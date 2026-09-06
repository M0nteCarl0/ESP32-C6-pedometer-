#include "pedometer_engine.h"
#include <esp_random.h>

PedometerEngine Pedometer;

PedometerEngine::PedometerEngine() 
    : _callback(nullptr),
      _stride_length_m(DEFAULT_STRIDE_LENGTH_M),
      _user_weight_kg(DEFAULT_USER_WEIGHT_KG),
      _last_step_time_ms(0),
      _next_step_delay_ms(600),
      _last_second_tick_ms(0),
      _last_save_time_ms(0),
      _steps_since_last_save(0) {
    memset(&_metrics, 0, sizeof(StepMetrics));
    _metrics.target_goal = DEFAULT_DAILY_GOAL;
    _metrics.cadence_spm = CADENCE_WALK_DEFAULT;
    _metrics.mode = MODE_WALKING;
}

void PedometerEngine::begin(StepCallback cb) {
    _callback = cb;
    _loadFromNVS();
    _calculateDerivedMetrics();
    _last_step_time_ms = millis();
    _last_second_tick_ms = millis();
    _last_save_time_ms = millis();
    _next_step_delay_ms = _calculateNextStepDelay();
}

void PedometerEngine::update() {
    unsigned long now = millis();

    // 1-second background tick for active time, speed decay, and time tracking
    if (now - _last_second_tick_ms >= 1000) {
        _last_second_tick_ms = now;

        if (_metrics.mode != MODE_PAUSED) {
            _metrics.active_seconds++;
            
            // Increment hourly bucket
            if (_metrics.current_hour < 24) {
                // Hourly bucket tracks steps naturally
            }

            // Calorie burn accumulation based on MET
            float met = 3.5f;
            if (_metrics.mode == MODE_JOGGING) met = 7.0f;
            else if (_metrics.mode == MODE_RUNNING) met = 10.5f;
            else if (_metrics.mode == MODE_CUSTOM) {
                if (_metrics.cadence_spm < 110) met = 3.5f;
                else if (_metrics.cadence_spm < 150) met = 7.5f;
                else met = 11.0f;
            }

            // Calorie formula: (MET * 3.5 * weight_kg) / 200 = kcal / minute -> / 60 for per second
            float kcal_per_sec = (met * 3.5f * _user_weight_kg) / (200.0f * 60.0f);
            _metrics.calories_kcal += kcal_per_sec;
        }

        // Periodic NVS save (every 60s if modified)
        if (_steps_since_last_save >= NVS_SAVE_STEP_INTERVAL || (now - _last_save_time_ms > 60000 && _steps_since_last_save > 0)) {
            _saveToNVS();
        }
    }

    // Step Generation Simulation
    if (_metrics.mode != MODE_PAUSED && _metrics.cadence_spm > 0) {
        if (now - _last_step_time_ms >= _next_step_delay_ms) {
            _last_step_time_ms = now;
            _generateSingleStep();
            _next_step_delay_ms = _calculateNextStepDelay();
        }
    }
}

unsigned long PedometerEngine::_calculateNextStepDelay() {
    if (_metrics.cadence_spm == 0) return 1000;
    
    // Base interval in ms: (60,000 / cadence)
    float base_ms = 60000.0f / (float)_metrics.cadence_spm;
    
    // Add natural gait jitter (+/- 4% random human variation)
    int32_t jitter_pct = (int32_t)(esp_random() % 9) - 4; // -4 to +4
    float jitter_factor = 1.0f + ((float)jitter_pct / 100.0f);
    
    return (unsigned long)(base_ms * jitter_factor);
}

void PedometerEngine::_generateSingleStep() {
    _metrics.total_steps++;
    _metrics.session_steps++;
    _steps_since_last_save++;

    // Record into current hourly slot
    if (_metrics.current_hour < 24) {
        _metrics.hourly_steps[_metrics.current_hour]++;
    }

    // Dynamic stride based on cadence/mode
    float current_stride = _stride_length_m;
    if (_metrics.mode == MODE_JOGGING) current_stride *= 1.15f;
    else if (_metrics.mode == MODE_RUNNING) current_stride *= 1.35f;

    _metrics.distance_km += (current_stride / 1000.0f);

    // Goal reached detection
    if (_metrics.total_steps >= _metrics.target_goal && !_metrics.goal_reached_alert) {
        _metrics.goal_reached_alert = true;
    }

    _calculateDerivedMetrics();

    if (_callback) {
        _callback(_metrics);
    }
}

void PedometerEngine::_calculateDerivedMetrics() {
    if (_metrics.mode == MODE_PAUSED || _metrics.cadence_spm == 0) {
        _metrics.speed_kmh = 0.0f;
        return;
    }

    float current_stride = _stride_length_m;
    if (_metrics.mode == MODE_JOGGING) current_stride *= 1.15f;
    else if (_metrics.mode == MODE_RUNNING) current_stride *= 1.35f;

    // Speed (km/h) = (steps/min * stride_m * 60) / 1000
    _metrics.speed_kmh = ((float)_metrics.cadence_spm * current_stride * 60.0f) / 1000.0f;
}

void PedometerEngine::setMode(PedometerMode mode) {
    _metrics.mode = mode;
    switch (mode) {
        case MODE_PAUSED:
            _metrics.speed_kmh = 0.0f;
            break;
        case MODE_WALKING:
            _metrics.cadence_spm = CADENCE_WALK_DEFAULT;
            break;
        case MODE_JOGGING:
            _metrics.cadence_spm = CADENCE_JOG_DEFAULT;
            break;
        case MODE_RUNNING:
            _metrics.cadence_spm = CADENCE_RUN_DEFAULT;
            break;
        case MODE_CUSTOM:
            // Keeps current cadence
            break;
    }
    _calculateDerivedMetrics();
    _next_step_delay_ms = _calculateNextStepDelay();
    _saveToNVS();
    if (_callback) _callback(_metrics);
}

void PedometerEngine::cycleMode() {
    uint8_t next = ((uint8_t)_metrics.mode + 1) % 4; // Cycles through PAUSED, WALK, JOG, RUN
    setMode((PedometerMode)next);
}

void PedometerEngine::togglePause() {
    if (_metrics.mode == MODE_PAUSED) {
        setMode(MODE_WALKING);
    } else {
        setMode(MODE_PAUSED);
    }
}

void PedometerEngine::setCadence(uint16_t spm) {
    if (spm < 30) spm = 30;
    if (spm > 240) spm = 240;
    _metrics.cadence_spm = spm;
    _metrics.mode = MODE_CUSTOM;
    _calculateDerivedMetrics();
    _next_step_delay_ms = _calculateNextStepDelay();
    if (_callback) _callback(_metrics);
}

void PedometerEngine::addSteps(uint32_t count) {
    _metrics.total_steps += count;
    _metrics.session_steps += count;
    _steps_since_last_save += count;

    if (_metrics.current_hour < 24) {
        _metrics.hourly_steps[_metrics.current_hour] += count;
    }

    _metrics.distance_km += (count * _stride_length_m / 1000.0f);
    _metrics.calories_kcal += (count * 0.045f); // ~45 kcal per 1000 steps

    if (_metrics.total_steps >= _metrics.target_goal && !_metrics.goal_reached_alert) {
        _metrics.goal_reached_alert = true;
    }

    _saveToNVS();
    if (_callback) _callback(_metrics);
}

void PedometerEngine::setTotalSteps(uint32_t count) {
    _metrics.total_steps = count;
    _metrics.distance_km = (count * _stride_length_m / 1000.0f);
    _metrics.calories_kcal = (count * 0.045f);
    _metrics.goal_reached_alert = (_metrics.total_steps >= _metrics.target_goal);
    _saveToNVS();
    if (_callback) _callback(_metrics);
}

void PedometerEngine::setTargetGoal(uint32_t goal) {
    if (goal < 100) goal = 100;
    _metrics.target_goal = goal;
    _metrics.goal_reached_alert = (_metrics.total_steps >= _metrics.target_goal);
    _saveToNVS();
    if (_callback) _callback(_metrics);
}

void PedometerEngine::setStrideLength(float meters) {
    if (meters > 0.3f && meters < 2.0f) {
        _stride_length_m = meters;
        _calculateDerivedMetrics();
    }
}

void PedometerEngine::setUserWeight(float kg) {
    if (kg > 30.0f && kg < 250.0f) {
        _user_weight_kg = kg;
    }
}

void PedometerEngine::resetStats() {
    _metrics.total_steps = 0;
    _metrics.session_steps = 0;
    _metrics.distance_km = 0.0f;
    _metrics.calories_kcal = 0.0f;
    _metrics.active_seconds = 0;
    _metrics.goal_reached_alert = false;
    memset(_metrics.hourly_steps, 0, sizeof(_metrics.hourly_steps));
    _steps_since_last_save = 0;
    _saveToNVS();
    if (_callback) _callback(_metrics);
}

void PedometerEngine::resetSession() {
    _metrics.session_steps = 0;
    _metrics.active_seconds = 0;
    if (_callback) _callback(_metrics);
}

void PedometerEngine::setTime(uint8_t hour, uint8_t minute, uint8_t second) {
    if (hour < 24) {
        _metrics.current_hour = hour;
    }
}

void PedometerEngine::setUnixTime(uint32_t unix_timestamp) {
    // Basic hour extraction from unix timestamp (UTC/Local)
    uint32_t seconds_in_day = unix_timestamp % 86400;
    uint8_t hour = (seconds_in_day / 3600);
    setTime(hour, 0, 0);
}

const char* PedometerEngine::getModeString() const {
    switch (_metrics.mode) {
        case MODE_PAUSED:  return "PAUSED";
        case MODE_WALKING: return "WALK";
        case MODE_JOGGING: return "JOG";
        case MODE_RUNNING: return "RUN";
        case MODE_CUSTOM:  return "CUSTOM";
        default:           return "IDLE";
    }
}

String PedometerEngine::getLiveJson() const {
    char buf[256];
    snprintf(buf, sizeof(buf),
        "{\"steps\":%u,\"cadence\":%u,\"speed\":%.2f,\"dist\":%.2f,\"kcal\":%.1f,\"sec\":%u,\"goal\":%u,\"mode\":\"%s\"}",
        _metrics.total_steps,
        _metrics.cadence_spm,
        _metrics.speed_kmh,
        _metrics.distance_km,
        _metrics.calories_kcal,
        _metrics.active_seconds,
        _metrics.target_goal,
        getModeString()
    );
    return String(buf);
}

String PedometerEngine::getHistoryJson() const {
    String json = "{\"hourly\":[";
    for (int i = 0; i < 24; i++) {
        json += String(_metrics.hourly_steps[i]);
        if (i < 23) json += ",";
    }
    json += "],\"daily\":[";
    for (int i = 0; i < 7; i++) {
        json += String(_metrics.daily_history[i]);
        if (i < 6) json += ",";
    }
    json += "],\"total\":" + String(_metrics.total_steps) + "}";
    return json;
}

String PedometerEngine::getCsvExport() const {
    String csv = "Hour,Steps,Est_Dist_km,Est_Kcal\n";
    for (int i = 0; i < 24; i++) {
        float h_dist = (_metrics.hourly_steps[i] * _stride_length_m) / 1000.0f;
        float h_kcal = _metrics.hourly_steps[i] * 0.045f;
        csv += String(i) + ":00," + String(_metrics.hourly_steps[i]) + "," + String(h_dist, 3) + "," + String(h_kcal, 1) + "\n";
    }
    return csv;
}

void PedometerEngine::_saveToNVS() {
    _prefs.begin("pedometer", false);
    _prefs.putUInt("steps", _metrics.total_steps);
    _prefs.putFloat("dist", _metrics.distance_km);
    _prefs.putFloat("kcal", _metrics.calories_kcal);
    _prefs.putUInt("sec", _metrics.active_seconds);
    _prefs.putUInt("goal", _metrics.target_goal);
    _prefs.putUChar("mode", (uint8_t)_metrics.mode);
    _prefs.putUShort("cadence", _metrics.cadence_spm);
    _prefs.putBytes("hourly", _metrics.hourly_steps, sizeof(_metrics.hourly_steps));
    _prefs.putBytes("daily", _metrics.daily_history, sizeof(_metrics.daily_history));
    _prefs.end();

    _steps_since_last_save = 0;
    _last_save_time_ms = millis();
}

void PedometerEngine::_loadFromNVS() {
    if (_prefs.begin("pedometer", true)) {
        _metrics.total_steps = _prefs.getUInt("steps", 0);
        _metrics.distance_km = _prefs.getFloat("dist", 0.0f);
        _metrics.calories_kcal = _prefs.getFloat("kcal", 0.0f);
        _metrics.active_seconds = _prefs.getUInt("sec", 0);
        _metrics.target_goal = _prefs.getUInt("goal", DEFAULT_DAILY_GOAL);
        _metrics.mode = (PedometerMode)_prefs.getUChar("mode", (uint8_t)MODE_WALKING);
        _metrics.cadence_spm = _prefs.getUShort("cadence", CADENCE_WALK_DEFAULT);
        _prefs.getBytes("hourly", _metrics.hourly_steps, sizeof(_metrics.hourly_steps));
        _prefs.getBytes("daily", _metrics.daily_history, sizeof(_metrics.daily_history));
        _prefs.end();
    }
}
