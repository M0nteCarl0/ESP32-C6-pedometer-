#include <Arduino.h>
#include "config.h"
#include "pedometer_engine.h"
#include "display_driver.h"
#include "rgb_led.h"
#include "button_handler.h"
#include "ble_pedometer_service.h"

// Forward declarations
void onStepEvent(const StepMetrics& metrics);
void onButtonEvent(ButtonEvent event);

static unsigned long last_ui_refresh_ms = 0;
static bool ui_needs_redraw = true;

void setup() {
    Serial.begin(115200);
    delay(500);
    Serial.println("\n==========================================");
    Serial.println(" Waveshare ESP32-C6-LCD-1.47 Pedometer");
    Serial.println(" Firmware v1.0.0 Initializing...");
    Serial.println("==========================================");

    // 1. Initialize RGB WS2812 LED (GPIO 8)
    RgbLed.begin();
    RgbLed.setColor(0, 10, 30); // Boot pulse

    // 2. Initialize ST7789 Display (172x320)
    Display.begin();
    Display.renderStaticLayout();
    Serial.println("[OK] ST7789 Display initialized (Safe 47% brightness)");

    // 3. Initialize Pedometer Simulation Engine
    Pedometer.begin(onStepEvent);
    Serial.printf("[OK] Pedometer Engine ready. Restored %u steps\n", Pedometer.getTotalSteps());

    // 4. Initialize BOOT Button (GPIO 9)
    BootButton.begin(onButtonEvent);
    Serial.println("[OK] BOOT Button (GPIO9) active");

    // 5. Initialize Bluetooth Low Energy Stack
    BleService.begin();
    Serial.println("[OK] BLE Services advertising as 'ESP32-C6-Pedometer'");
    Serial.println("     - Standard RSC Footpod Service (0x1814)");
    Serial.println("     - Custom Step Sync Service (Nordic UART UUID)");
    Serial.println("     - Battery Service (0x180F)");

    RgbLed.off();
    ui_needs_redraw = true;
}

void loop() {
    // 1. Process Core Subsystems
    Pedometer.update();
    BootButton.update();
    RgbLed.update();
    BleService.update(Pedometer.getMetrics());

    // 2. Periodic UI Dashboard Refresh (~4 FPS / 250ms interval)
    unsigned long now = millis();
    if (now - last_ui_refresh_ms >= 250 || ui_needs_redraw) {
        last_ui_refresh_ms = now;
        ui_needs_redraw = false;
        
        Display.renderPedometerDashboard(
            Pedometer.getMetrics(),
            BleService.isConnected(),
            98 // Battery percentage
        );
    }

    // Yield to FreeRTOS scheduler
    delay(5);
}

// Callback triggered whenever a step is simulated or added
void onStepEvent(const StepMetrics& metrics) {
    // 1. Subtle RGB LED pulse
    RgbLed.triggerStepPulse();

    // 2. Transmit step via BLE to connected Android phone / Web App
    BleService.notifyLiveStep(metrics);

    // 3. Request immediate UI repaint
    ui_needs_redraw = true;

    // 4. Goal Reached or Time Target Celebration
    if (metrics.goal_reached_alert || metrics.time_reached_alert) {
        RgbLed.triggerGoalCelebration();
    }
}

// Callback triggered on physical BOOT button interaction (GPIO 9)
void onButtonEvent(ButtonEvent event) {
    switch (event) {
        case BTN_EVENT_CLICK:
            Serial.println("[BTN] Single Click -> Cycle Mode");
            Pedometer.cycleMode();
            ui_needs_redraw = true;
            break;

        case BTN_EVENT_DOUBLE_CLICK:
            Serial.println("[BTN] Double Click -> Add +500 Steps");
            Pedometer.addSteps(500);
            ui_needs_redraw = true;
            break;

        case BTN_EVENT_LONG_PRESS:
            Serial.println("[BTN] Long Press (>1.2s) -> Toggle Pause/Resume");
            Pedometer.togglePause();
            RgbLed.setPaused(Pedometer.isPaused());
            ui_needs_redraw = true;
            break;

        case BTN_EVENT_VERY_LONG_PRESS:
            Serial.println("[BTN] Very Long Press (>5s) -> Reset Daily Stats");
            Pedometer.resetStats();
            Display.clear();
            Display.drawString("STATS RESET!", 36, 150, 1, COLOR_ORANGE, COLOR_BG);
            delay(1000);
            Display.renderStaticLayout();
            ui_needs_redraw = true;
            break;

        default:
            break;
    }
}
