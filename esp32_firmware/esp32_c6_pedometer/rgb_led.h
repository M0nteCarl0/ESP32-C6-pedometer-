#pragma once

#include <Arduino.h>
#include "config.h"

enum LedEffect {
    LED_EFFECT_OFF = 0,
    LED_EFFECT_STEP_PULSE,
    LED_EFFECT_BLE_CONNECTED,
    LED_EFFECT_BLE_ADVERTISING,
    LED_EFFECT_PAUSED,
    LED_EFFECT_GOAL
};

class RgbLedController {
public:
    RgbLedController();
    
    void begin();
    void update(); // Called in loop
    
    void setColor(uint8_t r, uint8_t g, uint8_t b);
    void triggerStepPulse();
    void setBleConnected(bool connected);
    void setPaused(bool paused);
    void triggerGoalCelebration();
    void off();

private:
    uint8_t _r, _g, _b;
    unsigned long _pulse_end_time;
    bool _is_pulsing;
    bool _ble_connected;
    bool _is_paused;
    uint8_t _rainbow_hue;
    
    void _writeRgb(uint8_t r, uint8_t g, uint8_t b);
};

extern RgbLedController RgbLed;
