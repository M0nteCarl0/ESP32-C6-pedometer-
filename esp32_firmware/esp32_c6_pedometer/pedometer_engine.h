#pragma once

#include <Arduino.h>
#include <Preferences.h>
#include "config.h"

enum PedometerMode {
    MODE_PAUSED = 0,
    MODE_WALKING,
    MODE_JOGGING,
    MODE_RUNNING,
    MODE_CUSTOM
};

struct StepMetrics {
    uint32_t total_steps;
    uint32_t session_steps;
    uint32_t target_goal;
    uint16_t cadence_spm;        // Current steps per minute
    float speed_kmh;            // Current speed in km/h
    float distance_km;          // Total distance in kilometers
    float calories_kcal;        // Total calories burned
    uint32_t active_seconds;    // Total active movement time
    PedometerMode mode;
    uint8_t current_hour;       // 0 - 23
    uint16_t hourly_steps[24];  // Steps for each hour of the day
    uint32_t daily_history[7];  // Steps for the last 7 days
    bool goal_reached_alert;    // Flag triggered once when goal is met
};

typedef void (*StepCallback)(const StepMetrics& metrics);

class PedometerEngine {
public:
    PedometerEngine();
    
    void begin(StepCallback cb = nullptr);
    void update(); // Called in main loop or FreeRTOS task
    
    // Controls
    void setMode(PedometerMode mode);
    void cycleMode();
    void togglePause();
    void setCadence(uint16_t spm);
    void addSteps(uint32_t count);
    void setTotalSteps(uint32_t count);
    void setTargetGoal(uint32_t goal);
    void setStrideLength(float meters);
    void setUserWeight(float kg);
    void resetStats();
    void resetSession();
    
    // Time & History
    void setTime(uint8_t hour, uint8_t minute, uint8_t second);
    void setUnixTime(uint32_t unix_timestamp);
    
    // Getters
    const StepMetrics& getMetrics() const { return _metrics; }
    PedometerMode getMode() const { return _metrics.mode; }
    uint32_t getTotalSteps() const { return _metrics.total_steps; }
    uint16_t getCadence() const { return _metrics.cadence_spm; }
    bool isPaused() const { return _metrics.mode == MODE_PAUSED; }
    const char* getModeString() const;

    // Serialization for Android BLE Sync
    String getLiveJson() const;
    String getHistoryJson() const;
    String getCsvExport() const;

private:
    StepMetrics _metrics;
    StepCallback _callback;
    Preferences _prefs;

    float _stride_length_m;
    float _user_weight_kg;

    unsigned long _last_step_time_ms;
    unsigned long _next_step_delay_ms;
    unsigned long _last_second_tick_ms;
    unsigned long _last_save_time_ms;
    uint32_t _steps_since_last_save;

    void _calculateDerivedMetrics();
    void _generateSingleStep();
    void _saveToNVS();
    void _loadFromNVS();
    unsigned long _calculateNextStepDelay();
};

extern PedometerEngine Pedometer;
