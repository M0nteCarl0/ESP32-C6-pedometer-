#pragma once

#include <Arduino.h>
#include <SPI.h>
#include "config.h"
#include "pedometer_engine.h"

// 16-bit RGB565 Color Definitions
#define COLOR_BLACK         0x0000
#define COLOR_WHITE         0xFFFF
#define COLOR_BG            0x0861  // Deep obsidian slate
#define COLOR_CARD_BG       0x18C3  // Dark navy container
#define COLOR_CARD_BORDER   0x2965  // Subtle border
#define COLOR_GREEN_NEON    0x07E5  // Neon green
#define COLOR_CYAN          0x067F  // Bright cyan
#define COLOR_ORANGE        0xFD20  // Vivid sunset orange
#define COLOR_PURPLE        0x9B1F  // Electric purple
#define COLOR_YELLOW        0xFFE0  // Golden yellow
#define COLOR_GRAY_DARK     0x39E7
#define COLOR_GRAY_LIGHT    0x9CD3
#define COLOR_BLE_BLUE      0x051D  // Bluetooth blue
#define COLOR_RED           0xF800

class DisplayDriver {
public:
    DisplayDriver();
    
    void begin();
    void setBrightness(uint8_t brightness); // 0 - 255
    
    // Core Drawing
    void clear(uint16_t color = COLOR_BG);
    void fillRect(int16_t x, int16_t y, int16_t w, int16_t h, uint16_t color);
    void drawRect(int16_t x, int16_t y, int16_t w, int16_t h, uint16_t color);
    void drawRoundRect(int16_t x, int16_t y, int16_t w, int16_t h, int16_t r, uint16_t color);
    void fillRoundRect(int16_t x, int16_t y, int16_t w, int16_t h, int16_t r, uint16_t color);
    void drawFastHLine(int16_t x, int16_t y, int16_t w, uint16_t color);
    void drawFastVLine(int16_t x, int16_t y, int16_t h, uint16_t color);
    void drawPixel(int16_t x, int16_t y, uint16_t color);
    
    // Text & Numbers
    void setTextColor(uint16_t color, uint16_t bg = COLOR_BG);
    void drawString(const char* text, int16_t x, int16_t y, uint8_t size = 1, uint16_t color = COLOR_WHITE, uint16_t bg = COLOR_BG);
    void drawLargeDigits(uint32_t number, int16_t y, uint16_t color = COLOR_GREEN_NEON);
    
    // UI Screen Renderers
    void renderStaticLayout();
    void renderPedometerDashboard(const StepMetrics& metrics, bool ble_connected, uint8_t battery_pct = 95);
    void renderStepPulseAnimation(uint8_t frame);
    void renderGoalCelebration();

private:
    SPIClass _spi;
    uint16_t _cached_steps;
    PedometerMode _cached_mode;
    bool _cached_ble;
    uint32_t _cached_active_sec;

    void _sendCommand(uint8_t cmd);
    void _sendData(uint8_t data);
    void _sendData16(uint16_t data);
    void _setAddrWindow(uint16_t x0, uint16_t y0, uint16_t x1, uint16_t y1);
    void _drawArcProgressBar(int16_t cx, int16_t cy, int16_t r, int16_t thickness, float progress, uint16_t color, uint16_t bg_color);
};

extern DisplayDriver Display;
