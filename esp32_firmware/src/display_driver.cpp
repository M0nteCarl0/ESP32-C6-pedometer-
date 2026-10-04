#include "display_driver.h"
#include <math.h>

DisplayDriver Display;

// Standard 5x7 ASCII font table (characters 32 ' ' to 126 '~')
static const uint8_t FONT5X7[][5] = {
    {0x00, 0x00, 0x00, 0x00, 0x00}, // 32 ' '
    {0x00, 0x00, 0x5F, 0x00, 0x00}, // 33 '!'
    {0x00, 0x07, 0x00, 0x07, 0x00}, // 34 '"'
    {0x14, 0x7F, 0x14, 0x7F, 0x14}, // 35 '#'
    {0x24, 0x2A, 0x7F, 0x2A, 0x12}, // 36 '$'
    {0x23, 0x13, 0x08, 0x64, 0x62}, // 37 '%'
    {0x36, 0x49, 0x55, 0x22, 0x50}, // 38 '&'
    {0x00, 0x05, 0x03, 0x00, 0x00}, // 39 '''
    {0x00, 0x1C, 0x22, 0x41, 0x00}, // 40 '('
    {0x00, 0x41, 0x22, 0x1C, 0x00}, // 41 ')'
    {0x08, 0x2A, 0x1C, 0x2A, 0x08}, // 42 '*'
    {0x08, 0x08, 0x3E, 0x08, 0x08}, // 43 '+'
    {0x00, 0x50, 0x30, 0x00, 0x00}, // 44 ','
    {0x08, 0x08, 0x08, 0x08, 0x08}, // 45 '-'
    {0x00, 0x60, 0x60, 0x00, 0x00}, // 46 '.'
    {0x20, 0x10, 0x08, 0x04, 0x02}, // 47 '/'
    {0x3E, 0x51, 0x49, 0x45, 0x3E}, // 48 '0'
    {0x00, 0x42, 0x7F, 0x40, 0x00}, // 49 '1'
    {0x42, 0x61, 0x51, 0x49, 0x46}, // 50 '2'
    {0x21, 0x41, 0x45, 0x4B, 0x31}, // 51 '3'
    {0x18, 0x14, 0x12, 0x7F, 0x10}, // 52 '4'
    {0x27, 0x45, 0x45, 0x45, 0x39}, // 53 '5'
    {0x3C, 0x4A, 0x49, 0x49, 0x30}, // 54 '6'
    {0x01, 0x71, 0x09, 0x05, 0x03}, // 55 '7'
    {0x36, 0x49, 0x49, 0x49, 0x36}, // 56 '8'
    {0x06, 0x49, 0x49, 0x29, 0x1E}, // 57 '9'
    {0x00, 0x36, 0x36, 0x00, 0x00}, // 58 ':'
    {0x00, 0x56, 0x36, 0x00, 0x00}, // 59 ';'
    {0x08, 0x14, 0x22, 0x41, 0x00}, // 60 '<'
    {0x14, 0x14, 0x14, 0x14, 0x14}, // 61 '='
    {0x00, 0x41, 0x22, 0x14, 0x08}, // 62 '>'
    {0x02, 0x01, 0x51, 0x09, 0x06}, // 63 '?'
    {0x32, 0x49, 0x79, 0x41, 0x3E}, // 64 '@'
    {0x7E, 0x11, 0x11, 0x11, 0x7E}, // 65 'A'
    {0x7F, 0x49, 0x49, 0x49, 0x36}, // 66 'B'
    {0x3E, 0x41, 0x41, 0x41, 0x22}, // 67 'C'
    {0x7F, 0x41, 0x41, 0x22, 0x1C}, // 68 'D'
    {0x7F, 0x49, 0x49, 0x49, 0x41}, // 69 'E'
    {0x7F, 0x09, 0x09, 0x09, 0x01}, // 70 'F'
    {0x3E, 0x41, 0x49, 0x49, 0x7A}, // 71 'G'
    {0x7F, 0x08, 0x08, 0x08, 0x7F}, // 72 'H'
    {0x00, 0x41, 0x7F, 0x41, 0x00}, // 73 'I'
    {0x20, 0x40, 0x41, 0x3F, 0x01}, // 74 'J'
    {0x7F, 0x08, 0x14, 0x22, 0x41}, // 75 'K'
    {0x7F, 0x40, 0x40, 0x40, 0x40}, // 76 'L'
    {0x7F, 0x02, 0x0C, 0x02, 0x7F}, // 77 'M'
    {0x7F, 0x04, 0x08, 0x10, 0x7F}, // 78 'N'
    {0x3E, 0x41, 0x41, 0x41, 0x3E}, // 79 'O'
    {0x7F, 0x09, 0x09, 0x09, 0x06}, // 80 'P'
    {0x3E, 0x41, 0x51, 0x21, 0x5E}, // 81 'Q'
    {0x7F, 0x09, 0x19, 0x29, 0x46}, // 82 'R'
    {0x46, 0x49, 0x49, 0x49, 0x31}, // 83 'S'
    {0x01, 0x01, 0x7F, 0x01, 0x01}, // 84 'T'
    {0x3F, 0x40, 0x40, 0x40, 0x3F}, // 85 'U'
    {0x1F, 0x20, 0x40, 0x20, 0x1F}, // 86 'V'
    {0x3F, 0x40, 0x38, 0x40, 0x3F}, // 87 'W'
    {0x63, 0x14, 0x08, 0x14, 0x63}, // 88 'X'
    {0x07, 0x08, 0x70, 0x08, 0x07}, // 89 'Y'
    {0x61, 0x51, 0x49, 0x45, 0x43}, // 90 'Z'
    {0x00, 0x7F, 0x41, 0x41, 0x00}, // 91 '['
    {0x02, 0x04, 0x08, 0x10, 0x20}, // 92 '\'
    {0x00, 0x41, 0x41, 0x7F, 0x00}, // 93 ']'
    {0x04, 0x02, 0x01, 0x02, 0x04}, // 94 '^'
    {0x40, 0x40, 0x40, 0x40, 0x40}, // 95 '_'
    {0x00, 0x01, 0x02, 0x04, 0x00}, // 96 '`'
    {0x20, 0x54, 0x54, 0x54, 0x78}, // 97 'a'
    {0x7F, 0x48, 0x44, 0x44, 0x38}, // 98 'b'
    {0x38, 0x44, 0x44, 0x44, 0x20}, // 99 'c'
    {0x38, 0x44, 0x44, 0x48, 0x7F}, // 100 'd'
    {0x38, 0x54, 0x54, 0x54, 0x18}, // 101 'e'
    {0x08, 0x7E, 0x09, 0x01, 0x02}, // 102 'f'
    {0x08, 0x54, 0x54, 0x54, 0x3C}, // 103 'g'
    {0x7F, 0x08, 0x04, 0x04, 0x78}, // 104 'h'
    {0x00, 0x44, 0x7D, 0x40, 0x00}, // 105 'i'
    {0x20, 0x40, 0x44, 0x3D, 0x00}, // 106 'j'
    {0x7F, 0x10, 0x28, 0x44, 0x00}, // 107 'k'
    {0x00, 0x41, 0x7F, 0x40, 0x00}, // 108 'l'
    {0x7C, 0x04, 0x18, 0x04, 0x78}, // 109 'm'
    {0x7C, 0x08, 0x04, 0x04, 0x78}, // 110 'n'
    {0x38, 0x44, 0x44, 0x44, 0x38}, // 111 'o'
    {0x7C, 0x14, 0x14, 0x14, 0x08}, // 112 'p'
    {0x08, 0x14, 0x14, 0x18, 0x7C}, // 113 'q'
    {0x7C, 0x08, 0x04, 0x04, 0x08}, // 114 'r'
    {0x48, 0x54, 0x54, 0x54, 0x20}, // 115 's'
    {0x04, 0x3F, 0x44, 0x40, 0x20}, // 116 't'
    {0x3C, 0x40, 0x40, 0x20, 0x7C}, // 117 'u'
    {0x1C, 0x20, 0x40, 0x20, 0x1C}, // 118 'v'
    {0x3C, 0x40, 0x30, 0x40, 0x3C}, // 119 'w'
    {0x44, 0x28, 0x10, 0x28, 0x44}, // 120 'x'
    {0x0C, 0x50, 0x50, 0x50, 0x3C}, // 121 'y'
    {0x44, 0x64, 0x54, 0x4C, 0x44}, // 122 'z'
    {0x00, 0x08, 0x36, 0x41, 0x00}, // 123 '{'
    {0x00, 0x00, 0x7F, 0x00, 0x00}, // 124 '|'
    {0x00, 0x41, 0x36, 0x08, 0x00}, // 125 '}'
    {0x08, 0x08, 0x2A, 0x1C, 0x08}  // 126 '~'
};

DisplayDriver::DisplayDriver()
    : _spi(FSPI),
      _cached_steps(0xFFFF),
      _cached_mode((PedometerMode)0xFF),
      _cached_ble(false),
      _cached_active_sec(0xFFFFFFFF) {
}

void DisplayDriver::_sendCommand(uint8_t cmd) {
    digitalWrite(PIN_LCD_DC, LOW);
    digitalWrite(PIN_LCD_CS, LOW);
    _spi.transfer(cmd);
    digitalWrite(PIN_LCD_CS, HIGH);
}

void DisplayDriver::_sendData(uint8_t data) {
    digitalWrite(PIN_LCD_DC, HIGH);
    digitalWrite(PIN_LCD_CS, LOW);
    _spi.transfer(data);
    digitalWrite(PIN_LCD_CS, HIGH);
}

void DisplayDriver::_sendData16(uint16_t data) {
    digitalWrite(PIN_LCD_DC, HIGH);
    digitalWrite(PIN_LCD_CS, LOW);
    _spi.transfer16(data);
    digitalWrite(PIN_LCD_CS, HIGH);
}

void DisplayDriver::_setAddrWindow(uint16_t x0, uint16_t y0, uint16_t x1, uint16_t y1) {
    // Add hardware ST7789 column offset for 172x320 panel
    uint16_t xa0 = x0 + LCD_COL_OFFSET;
    uint16_t xa1 = x1 + LCD_COL_OFFSET;
    uint16_t ya0 = y0 + LCD_ROW_OFFSET;
    uint16_t ya1 = y1 + LCD_ROW_OFFSET;

    _sendCommand(0x2A); // CASET
    _sendData(xa0 >> 8);
    _sendData(xa0 & 0xFF);
    _sendData(xa1 >> 8);
    _sendData(xa1 & 0xFF);

    _sendCommand(0x2B); // RASET
    _sendData(ya0 >> 8);
    _sendData(ya0 & 0xFF);
    _sendData(ya1 >> 8);
    _sendData(ya1 & 0xFF);

    _sendCommand(0x2C); // RAMWR
}

void DisplayDriver::begin() {
    pinMode(PIN_LCD_CS, OUTPUT);
    pinMode(PIN_LCD_DC, OUTPUT);
    pinMode(PIN_LCD_RST, OUTPUT);
    pinMode(PIN_LCD_BL, OUTPUT);

    digitalWrite(PIN_LCD_CS, HIGH);
    digitalWrite(PIN_LCD_DC, HIGH);

    // Hardware Reset
    digitalWrite(PIN_LCD_RST, HIGH);
    delay(20);
    digitalWrite(PIN_LCD_RST, LOW);
    delay(50);
    digitalWrite(PIN_LCD_RST, HIGH);
    delay(120);

    // Init SPI Bus (40 MHz clock)
    _spi.begin(PIN_LCD_SCLK, -1, PIN_LCD_MOSI, PIN_LCD_CS);
    _spi.beginTransaction(SPISettings(40000000, MSBFIRST, SPI_MODE0));

    // ST7789 Initialization Commands
    _sendCommand(0x01); // Software Reset
    delay(150);

    _sendCommand(0x11); // Sleep Out
    delay(120);

    _sendCommand(0x3A); // Interface Pixel Format (16-bit RGB565)
    _sendData(0x55);

    _sendCommand(0x36); // Memory Data Access Control (Portrait Mode)
    _sendData(0x00);

    _sendCommand(0x21); // Display Inversion ON (IPS panel)
    delay(10);

    _sendCommand(0x13); // Normal Display Mode ON
    delay(10);

    _sendCommand(0x29); // Display ON
    delay(50);

    // Backlight PWM Initialization (LEDC)
    // ESP32 Arduino Core 3.x ledcAttach
    #if ESP_ARDUINO_VERSION >= ESP_ARDUINO_VERSION_VAL(3, 0, 0)
        ledcAttach(PIN_LCD_BL, 5000, 8);
        ledcWrite(PIN_LCD_BL, LCD_DEFAULT_BRIGHTNESS);
    #else
        ledcSetup(0, 5000, 8);
        ledcAttachPin(PIN_LCD_BL, 0);
        ledcWrite(0, LCD_DEFAULT_BRIGHTNESS);
    #endif

    clear(COLOR_BG);
}

void DisplayDriver::setBrightness(uint8_t brightness) {
    // Keep max limit at 180 (70%) to avoid overheating according to Waveshare hardware guidelines
    if (brightness > 180) brightness = 180;
    #if ESP_ARDUINO_VERSION >= ESP_ARDUINO_VERSION_VAL(3, 0, 0)
        ledcWrite(PIN_LCD_BL, brightness);
    #else
        ledcWrite(0, brightness);
    #endif
}

void DisplayDriver::clear(uint16_t color) {
    fillRect(0, 0, LCD_WIDTH, LCD_HEIGHT, color);
}

void DisplayDriver::drawPixel(int16_t x, int16_t y, uint16_t color) {
    if (x < 0 || x >= LCD_WIDTH || y < 0 || y >= LCD_HEIGHT) return;
    _setAddrWindow(x, y, x, y);
    digitalWrite(PIN_LCD_DC, HIGH);
    digitalWrite(PIN_LCD_CS, LOW);
    _spi.transfer16(color);
    digitalWrite(PIN_LCD_CS, HIGH);
}

void DisplayDriver::drawFastHLine(int16_t x, int16_t y, int16_t w, uint16_t color) {
    if (y < 0 || y >= LCD_HEIGHT || x >= LCD_WIDTH || w <= 0) return;
    if (x < 0) { w += x; x = 0; }
    if (x + w > LCD_WIDTH) w = LCD_WIDTH - x;
    if (w <= 0) return;

    _setAddrWindow(x, y, x + w - 1, y);
    digitalWrite(PIN_LCD_DC, HIGH);
    digitalWrite(PIN_LCD_CS, LOW);
    for (int16_t i = 0; i < w; i++) {
        _spi.transfer16(color);
    }
    digitalWrite(PIN_LCD_CS, HIGH);
}

void DisplayDriver::drawFastVLine(int16_t x, int16_t y, int16_t h, uint16_t color) {
    if (x < 0 || x >= LCD_WIDTH || y >= LCD_HEIGHT || h <= 0) return;
    if (y < 0) { h += y; y = 0; }
    if (y + h > LCD_HEIGHT) h = LCD_HEIGHT - y;
    if (h <= 0) return;

    _setAddrWindow(x, y, x, y + h - 1);
    digitalWrite(PIN_LCD_DC, HIGH);
    digitalWrite(PIN_LCD_CS, LOW);
    for (int16_t i = 0; i < h; i++) {
        _spi.transfer16(color);
    }
    digitalWrite(PIN_LCD_CS, HIGH);
}

void DisplayDriver::fillRect(int16_t x, int16_t y, int16_t w, int16_t h, uint16_t color) {
    if (x >= LCD_WIDTH || y >= LCD_HEIGHT || w <= 0 || h <= 0) return;
    if (x < 0) { w += x; x = 0; }
    if (y < 0) { h += y; y = 0; }
    if (x + w > LCD_WIDTH) w = LCD_WIDTH - x;
    if (y + h > LCD_HEIGHT) h = LCD_HEIGHT - y;
    if (w <= 0 || h <= 0) return;

    _setAddrWindow(x, y, x + w - 1, y + h - 1);
    digitalWrite(PIN_LCD_DC, HIGH);
    digitalWrite(PIN_LCD_CS, LOW);
    
    // Batch transfer
    uint32_t total_pixels = w * h;
    for (uint32_t i = 0; i < total_pixels; i++) {
        _spi.transfer16(color);
    }
    digitalWrite(PIN_LCD_CS, HIGH);
}

void DisplayDriver::drawRect(int16_t x, int16_t y, int16_t w, int16_t h, uint16_t color) {
    drawFastHLine(x, y, w, color);
    drawFastHLine(x, y + h - 1, w, color);
    drawFastVLine(x, y, h, color);
    drawFastVLine(x + w - 1, y, h, color);
}

void DisplayDriver::drawRoundRect(int16_t x, int16_t y, int16_t w, int16_t h, int16_t r, uint16_t color) {
    drawFastHLine(x + r, y, w - 2 * r, color);
    drawFastHLine(x + r, y + h - 1, w - 2 * r, color);
    drawFastVLine(x, y + r, h - 2 * r, color);
    drawFastVLine(x + w - 1, y + r, h - 2 * r, color);
}

void DisplayDriver::fillRoundRect(int16_t x, int16_t y, int16_t w, int16_t h, int16_t r, uint16_t color) {
    fillRect(x + r, y, w - 2 * r, h, color);
    fillRect(x, y + r, r, h - 2 * r, color);
    fillRect(x + w - r, y + r, r, h - 2 * r, color);
}

void DisplayDriver::drawString(const char* text, int16_t x, int16_t y, uint8_t size, uint16_t color, uint16_t bg) {
    int16_t cursor_x = x;
    int16_t cursor_y = y;

    while (*text) {
        char c = *text++;
        if (c < 32 || c > 126) c = '?';
        uint8_t char_idx = c - 32;

        for (int8_t i = 0; i < 5; i++) {
            uint8_t line = FONT5X7[char_idx][i];
            for (int8_t j = 0; j < 8; j++) {
                uint16_t pixel_color = (line & 0x01) ? color : bg;
                if (pixel_color != bg || bg != COLOR_BG) {
                    if (size == 1) {
                        drawPixel(cursor_x + i, cursor_y + j, pixel_color);
                    } else {
                        fillRect(cursor_x + i * size, cursor_y + j * size, size, size, pixel_color);
                    }
                }
                line >>= 1;
            }
        }
        cursor_x += 6 * size;
    }
}

void DisplayDriver::drawLargeDigits(uint32_t number, int16_t y, uint16_t color) {
    char buf[16];
    snprintf(buf, sizeof(buf), "%u", number);
    uint8_t len = strlen(buf);
    
    // Large font scale = 4 (each char ~24px wide)
    uint8_t size = 4;
    int16_t total_w = len * (6 * size);
    int16_t start_x = (LCD_WIDTH - total_w) / 2;
    if (start_x < 4) {
        size = 3;
        total_w = len * (6 * size);
        start_x = (LCD_WIDTH - total_w) / 2;
    }

    // Clear digit background area to avoid ghosting
    fillRect(0, y, LCD_WIDTH, 8 * size + 4, COLOR_BG);
    drawString(buf, start_x, y, size, color, COLOR_BG);
}

void DisplayDriver::_drawArcProgressBar(int16_t cx, int16_t cy, int16_t r, int16_t thickness, float progress, uint16_t color, uint16_t bg_color) {
    if (progress < 0.0f) progress = 0.0f;
    if (progress > 1.0f) progress = 1.0f;

    // Draw horizontal rounded progress bar for sharp performance & aesthetics
    int16_t bar_x = 16;
    int16_t bar_y = cy;
    int16_t bar_w = LCD_WIDTH - 32;
    int16_t bar_h = thickness;

    fillRoundRect(bar_x, bar_y, bar_w, bar_h, bar_h / 2, bg_color);
    int16_t fill_w = (int16_t)(bar_w * progress);
    if (fill_w > 4) {
        fillRoundRect(bar_x, bar_y, fill_w, bar_h, bar_h / 2, color);
    }
}

void DisplayDriver::renderStaticLayout() {
    clear(COLOR_BG);

    // Top Status Header line
    drawFastHLine(0, 26, LCD_WIDTH, COLOR_CARD_BORDER);

    // Main Step Container card
    drawRoundRect(8, 32, LCD_WIDTH - 16, 116, 8, COLOR_CARD_BORDER);

    // 2x2 Metrics Grid cards
    // Card 1: Distance (Top-Left)
    fillRoundRect(8, 154, 74, 52, 6, COLOR_CARD_BG);
    drawRoundRect(8, 154, 74, 52, 6, COLOR_CARD_BORDER);
    drawString("DIST", 14, 160, 1, COLOR_GRAY_LIGHT, COLOR_CARD_BG);
    drawString("km", 56, 188, 1, COLOR_GRAY_LIGHT, COLOR_CARD_BG);

    // Card 2: Calories (Top-Right)
    fillRoundRect(90, 154, 74, 52, 6, COLOR_CARD_BG);
    drawRoundRect(90, 154, 74, 52, 6, COLOR_CARD_BORDER);
    drawString("CAL", 96, 160, 1, COLOR_GRAY_LIGHT, COLOR_CARD_BG);
    drawString("kcal", 132, 188, 1, COLOR_GRAY_LIGHT, COLOR_CARD_BG);

    // Card 3: Cadence (Bottom-Left)
    fillRoundRect(8, 212, 74, 52, 6, COLOR_CARD_BG);
    drawRoundRect(8, 212, 74, 52, 6, COLOR_CARD_BORDER);
    drawString("CADENCE", 14, 218, 1, COLOR_GRAY_LIGHT, COLOR_CARD_BG);
    drawString("spm", 52, 246, 1, COLOR_GRAY_LIGHT, COLOR_CARD_BG);

    // Card 4: Speed (Bottom-Right)
    fillRoundRect(90, 212, 74, 52, 6, COLOR_CARD_BG);
    drawRoundRect(90, 212, 74, 52, 6, COLOR_CARD_BORDER);
    drawString("SPEED", 96, 218, 1, COLOR_GRAY_LIGHT, COLOR_CARD_BG);
    drawString("km/h", 130, 246, 1, COLOR_GRAY_LIGHT, COLOR_CARD_BG);

    // Bottom Help & Active Time Divider
    drawFastHLine(0, 270, LCD_WIDTH, COLOR_CARD_BORDER);
}

void DisplayDriver::renderPedometerDashboard(const StepMetrics& metrics, bool ble_connected, uint8_t battery_pct) {
    // 1. TOP HEADER (BLE, GPS, Mode Badge, Battery)
    // BLE indicator
    if (ble_connected) {
        fillRoundRect(6, 4, 30, 18, 4, COLOR_BLE_BLUE);
        drawString("BLE", 11, 9, 1, COLOR_WHITE, COLOR_BLE_BLUE);
    } else {
        fillRoundRect(6, 4, 30, 18, 4, COLOR_GRAY_DARK);
        drawString("BLE", 11, 9, 1, COLOR_GRAY_LIGHT, COLOR_GRAY_DARK);
    }

    // GPS indicator
    if (metrics.has_gps) {
        fillRoundRect(38, 4, 28, 18, 4, COLOR_CYAN);
        drawString("GPS", 43, 9, 1, COLOR_BLACK, COLOR_CYAN);
    } else {
        fillRoundRect(38, 4, 28, 18, 4, COLOR_GRAY_DARK);
        drawString("GPS", 43, 9, 1, COLOR_GRAY_LIGHT, COLOR_GRAY_DARK);
    }

    // Mode Pill Badge
    uint16_t mode_color = COLOR_GREEN_NEON;
    if (metrics.mode == MODE_PAUSED) mode_color = COLOR_GRAY_LIGHT;
    else if (metrics.mode == MODE_JOGGING) mode_color = COLOR_YELLOW;
    else if (metrics.mode == MODE_RUNNING) mode_color = COLOR_ORANGE;

    fillRoundRect(68, 4, 52, 18, 4, COLOR_CARD_BG);
    drawRoundRect(68, 4, 52, 18, 4, mode_color);
    const char* mode_str = Pedometer.getModeString();
    int16_t mode_x = 94 - (strlen(mode_str) * 3);
    drawString(mode_str, mode_x, 9, 1, mode_color, COLOR_CARD_BG);

    // Battery / Status (Right)
    char bat_buf[8];
    snprintf(bat_buf, sizeof(bat_buf), "%u%%", battery_pct);
    drawString(bat_buf, 126, 9, 1, COLOR_WHITE, COLOR_BG);

    // 2. MAIN STEP WIDGET
    // Large Digits
    drawLargeDigits(metrics.total_steps, 46, COLOR_GREEN_NEON);

    // Goal Subtext & Progress Bar
    float progress = (float)metrics.total_steps / (float)metrics.target_goal;
    if (progress > 1.0f) progress = 1.0f;
    _drawArcProgressBar(86, 92, 0, 6, progress, COLOR_GREEN_NEON, COLOR_GRAY_DARK);

    char goal_buf[36];
    uint16_t pct = (uint16_t)((float)metrics.total_steps * 100.0f / (float)metrics.target_goal);
    if (metrics.target_duration_sec > 0) {
        uint32_t tgt_min = metrics.target_duration_sec / 60;
        snprintf(goal_buf, sizeof(goal_buf), "Goal: %u | Tgt: %um", metrics.target_goal, tgt_min);
    } else {
        snprintf(goal_buf, sizeof(goal_buf), "Goal: %u (%u%%)", metrics.target_goal, pct);
    }
    int16_t goal_x = (LCD_WIDTH - (strlen(goal_buf) * 6)) / 2;
    if (goal_x < 2) goal_x = 2;
    fillRect(2, 114, LCD_WIDTH - 4, 12, COLOR_BG);
    drawString(goal_buf, goal_x, 114, 1, COLOR_GRAY_LIGHT, COLOR_BG);

    // 3. METRIC VALUES UPDATE (Dirty region clearing)
    // Distance
    char dist_buf[10];
    snprintf(dist_buf, sizeof(dist_buf), "%.2f", metrics.distance_km);
    fillRect(12, 174, 50, 16, COLOR_CARD_BG);
    drawString(dist_buf, 14, 174, 2, COLOR_CYAN, COLOR_CARD_BG);

    // Calories
    char cal_buf[10];
    snprintf(cal_buf, sizeof(cal_buf), "%u", (uint32_t)metrics.calories_kcal);
    fillRect(94, 174, 45, 16, COLOR_CARD_BG);
    drawString(cal_buf, 96, 174, 2, COLOR_ORANGE, COLOR_CARD_BG);

    // Cadence
    char cad_buf[10];
    snprintf(cad_buf, sizeof(cad_buf), "%u", metrics.cadence_spm);
    fillRect(12, 232, 45, 16, COLOR_CARD_BG);
    drawString(cad_buf, 14, 232, 2, COLOR_PURPLE, COLOR_CARD_BG);

    // Speed
    char spd_buf[10];
    snprintf(spd_buf, sizeof(spd_buf), "%.1f", metrics.speed_kmh);
    fillRect(94, 232, 45, 16, COLOR_CARD_BG);
    drawString(spd_buf, 96, 232, 2, COLOR_YELLOW, COLOR_CARD_BG);

    // 4. FOOTER: Active Time & Target / GPS status
    char time_buf[28];
    if (metrics.target_duration_sec > 0) {
        uint32_t rem = Pedometer.getRemainingDuration();
        uint32_t act_min = metrics.session_active_sec / 60;
        uint32_t act_sec = metrics.session_active_sec % 60;
        uint32_t rem_min = rem / 60;
        uint32_t rem_sec = rem % 60;
        snprintf(time_buf, sizeof(time_buf), "%02u:%02u / Rem %02u:%02u", act_min, act_sec, rem_min, rem_sec);
    } else {
        uint32_t hrs = metrics.active_seconds / 3600;
        uint32_t mins = (metrics.active_seconds % 3600) / 60;
        uint32_t secs = metrics.active_seconds % 60;
        snprintf(time_buf, sizeof(time_buf), "Active %02u:%02u:%02u", hrs, mins, secs);
    }
    fillRect(6, 278, LCD_WIDTH - 12, 12, COLOR_BG);
    int16_t time_x = (LCD_WIDTH - (strlen(time_buf) * 6)) / 2;
    if (time_x < 4) time_x = 4;
    drawString(time_buf, time_x, 278, 1, COLOR_WHITE, COLOR_BG);

    // Hint text or GPS info
    char hint_buf[32];
    if (metrics.has_gps) {
        if (metrics.route_name[0] != '\0') {
            snprintf(hint_buf, sizeof(hint_buf), "%.18s", metrics.route_name);
        } else {
            snprintf(hint_buf, sizeof(hint_buf), "GPS: %.3f, %.3f", metrics.current_lat, metrics.current_lon);
        }
    } else {
        snprintf(hint_buf, sizeof(hint_buf), "BOOT: Mode | Hold: P/R");
    }
    fillRect(4, 298, LCD_WIDTH - 8, 12, COLOR_BG);
    int16_t hint_x = (LCD_WIDTH - (strlen(hint_buf) * 6)) / 2;
    if (hint_x < 4) hint_x = 4;
    drawString(hint_buf, hint_x, 298, 1, metrics.has_gps ? COLOR_CYAN : COLOR_GRAY_LIGHT, COLOR_BG);
}

void DisplayDriver::renderStepPulseAnimation(uint8_t frame) {
    // Subtle pulsating green dot in bottom corner on each step
    uint16_t color = (frame % 2 == 0) ? COLOR_GREEN_NEON : COLOR_BG;
    fillRoundRect(156, 280, 8, 8, 4, color);
}

void DisplayDriver::renderGoalCelebration() {
    fillRoundRect(16, 80, LCD_WIDTH - 32, 80, 8, COLOR_GREEN_NEON);
    drawString("GOAL REACHED!", 32, 100, 1, COLOR_BLACK, COLOR_GREEN_NEON);
    drawString("10,000 STEPS", 36, 120, 1, COLOR_BLACK, COLOR_GREEN_NEON);
    delay(2000);
    renderStaticLayout();
}
