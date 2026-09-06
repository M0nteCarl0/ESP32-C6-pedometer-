#include "button_handler.h"

ButtonHandler BootButton;

ButtonHandler::ButtonHandler()
    : _cb(nullptr),
      _last_state(HIGH),
      _press_start_time(0),
      _last_release_time(0),
      _click_count(0),
      _long_press_triggered(false),
      _very_long_press_triggered(false) {
}

void ButtonHandler::begin(ButtonCallback cb) {
    _cb = cb;
    pinMode(PIN_BUTTON_BOOT, INPUT_PULLUP);
    _last_state = digitalRead(PIN_BUTTON_BOOT);
}

void ButtonHandler::update() {
    bool current_state = digitalRead(PIN_BUTTON_BOOT);
    unsigned long now = millis();

    // Button Pressed (Active LOW)
    if (_last_state == HIGH && current_state == LOW) {
        _press_start_time = now;
        _long_press_triggered = false;
        _very_long_press_triggered = false;
    }
    // Button Held Down
    else if (current_state == LOW) {
        unsigned long duration = now - _press_start_time;

        if (duration >= 5000 && !_very_long_press_triggered) {
            _very_long_press_triggered = true;
            _click_count = 0;
            if (_cb) _cb(BTN_EVENT_VERY_LONG_PRESS);
        }
        else if (duration >= 1200 && !_long_press_triggered && !_very_long_press_triggered) {
            _long_press_triggered = true;
            _click_count = 0;
            if (_cb) _cb(BTN_EVENT_LONG_PRESS);
        }
    }
    // Button Released
    else if (_last_state == LOW && current_state == HIGH) {
        unsigned long duration = now - _press_start_time;
        
        // Debounce filter (ignore < 30ms noise)
        if (duration >= 30 && !_long_press_triggered && !_very_long_press_triggered) {
            _click_count++;
            _last_release_time = now;
        }
    }

    // Process single vs double clicks after short pause (250ms)
    if (_click_count > 0 && (now - _last_release_time > 250)) {
        if (_click_count == 1) {
            if (_cb) _cb(BTN_EVENT_CLICK);
        } else if (_click_count >= 2) {
            if (_cb) _cb(BTN_EVENT_DOUBLE_CLICK);
        }
        _click_count = 0;
    }

    _last_state = current_state;
}
