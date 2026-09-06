#include "rgb_led.h"

RgbLedController RgbLed;

RgbLedController::RgbLedController()
    : _r(0), _g(0), _b(0),
      _pulse_end_time(0),
      _is_pulsing(false),
      _ble_connected(false),
      _is_paused(false),
      _rainbow_hue(0) {
}

void RgbLedController::begin() {
    pinMode(PIN_RGB_LED, OUTPUT);
    off();
}

void RgbLedController::_writeRgb(uint8_t r, uint8_t g, uint8_t b) {
    // Built-in ESP32 rgbLedWrite (RMT driver on ESP32-C6)
    rgbLedWrite(PIN_RGB_LED, r, g, b);
}

void RgbLedController::setColor(uint8_t r, uint8_t g, uint8_t b) {
    _r = r;
    _g = g;
    _b = b;
    _writeRgb(r, g, b);
}

void RgbLedController::triggerStepPulse() {
    _is_pulsing = true;
    _pulse_end_time = millis() + 60; // 60ms subtle pulse
    // Mild green pulse
    _writeRgb(0, 30, 10);
}

void RgbLedController::setBleConnected(bool connected) {
    _ble_connected = connected;
    if (connected) {
        // Quick blue blink
        _writeRgb(0, 15, 45);
        delay(80);
        _writeRgb(0, 0, 0);
    }
}

void RgbLedController::setPaused(bool paused) {
    _is_paused = paused;
    if (paused) {
        _writeRgb(25, 10, 0); // Gentle warm amber
    } else {
        _writeRgb(0, 0, 0);
    }
}

void RgbLedController::triggerGoalCelebration() {
    for (int j = 0; j < 3; j++) {
        _writeRgb(0, 50, 0);
        delay(100);
        _writeRgb(0, 0, 0);
        delay(100);
    }
}

void RgbLedController::off() {
    _writeRgb(0, 0, 0);
}

void RgbLedController::update() {
    if (_is_pulsing) {
        if (millis() >= _pulse_end_time) {
            _is_pulsing = false;
            if (_is_paused) {
                _writeRgb(25, 10, 0);
            } else if (_ble_connected) {
                _writeRgb(0, 2, 8); // Dim blue glow when BLE linked
            } else {
                _writeRgb(0, 0, 0);
            }
        }
    }
}
