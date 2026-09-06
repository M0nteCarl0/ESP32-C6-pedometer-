#pragma once

#include <Arduino.h>
#include "config.h"

enum ButtonEvent {
    BTN_EVENT_NONE = 0,
    BTN_EVENT_CLICK,
    BTN_EVENT_DOUBLE_CLICK,
    BTN_EVENT_LONG_PRESS,
    BTN_EVENT_VERY_LONG_PRESS
};

typedef void (*ButtonCallback)(ButtonEvent event);

class ButtonHandler {
public:
    ButtonHandler();
    
    void begin(ButtonCallback cb = nullptr);
    void update(); // Call in loop
    
private:
    ButtonCallback _cb;
    bool _last_state;
    unsigned long _press_start_time;
    unsigned long _last_release_time;
    uint8_t _click_count;
    bool _long_press_triggered;
    bool _very_long_press_triggered;
};

extern ButtonHandler BootButton;
