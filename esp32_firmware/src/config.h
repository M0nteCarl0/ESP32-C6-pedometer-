#pragma once

#include <Arduino.h>

// ============================================================================
// Waveshare ESP32-C6-LCD-1.47 Hardware Pin Definitions
// ============================================================================

// ST7789V2 SPI LCD (172 x 320 pixels)
#define PIN_LCD_MOSI        6
#define PIN_LCD_SCLK        7
#define PIN_LCD_CS          14
#define PIN_LCD_DC          15
#define PIN_LCD_RST         21
#define PIN_LCD_BL          22

// Screen Dimensions & ST7789 Offset
#define LCD_WIDTH           172
#define LCD_HEIGHT          320
#define LCD_COL_OFFSET      34   // (240 - 172) / 2
#define LCD_ROW_OFFSET      0

// Recommended safe brightness (0-255).
// Waveshare notes: keep brightness <= 50% for extended use to prevent panel heating.
#define LCD_DEFAULT_BRIGHTNESS 120 // ~47% duty cycle

// RGB LED (WS2812 / NeoPixel)
#define PIN_RGB_LED         8
#define RGB_NUM_LEDS        1

// User / Boot Button
#define PIN_BUTTON_BOOT     9

// MicroSD Card (TF Card)
#define PIN_SD_MISO         5
#define PIN_SD_MOSI         6
#define PIN_SD_SCLK         7
#define PIN_SD_CS           4

// ============================================================================
// BLE Configuration & GATT Service UUIDs
// ============================================================================

#define BLE_DEVICE_NAME             "ESP32-C6-Pedometer"
#define BLE_MANUFACTURER_NAME       "Waveshare"
#define BLE_MODEL_NUMBER            "ESP32-C6-LCD-1.47"
#define BLE_FIRMWARE_REV            "1.0.0"

// Standard BLE Running Speed & Cadence (RSC) Service (0x1814)
// Supported natively by Strava, Wahoo, nRF Toolbox, Polar Beat, Gadgetbridge
#define BLE_UUID_RSC_SERVICE        "00001814-0000-1000-8000-00805f9b34fb"
#define BLE_UUID_RSC_MEASUREMENT    "00002a53-0000-1000-8000-00805f9b34fb"
#define BLE_UUID_RSC_FEATURE        "00002a54-0000-1000-8000-00805f9b34fb"

// Standard Battery Service (0x180F)
#define BLE_UUID_BATTERY_SERVICE    "0000180f-0000-1000-8000-00805f9b34fb"
#define BLE_UUID_BATTERY_LEVEL      "00002a19-0000-1000-8000-00805f9b34fb"

// Custom Step Sync & Control GATT Service (Nordic UART Base)
#define BLE_UUID_CUSTOM_SERVICE     "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
#define BLE_UUID_CHAR_LIVE_DATA     "6e400002-b5a3-f393-e0a9-e50e24dcca9e" // Notify / Read: Real-time steps & stats
#define BLE_UUID_CHAR_HISTORY_SYNC  "6e400003-b5a3-f393-e0a9-e50e24dcca9e" // Notify / Read: 24h & 7d history dump
#define BLE_UUID_CHAR_COMMAND       "6e400004-b5a3-f393-e0a9-e50e24dcca9e" // Write: Remote config & actions

// ============================================================================
// Pedometer Simulation Defaults
// ============================================================================

#define DEFAULT_DAILY_GOAL          10000   // Steps
#define DEFAULT_STRIDE_LENGTH_M     0.75f   // Meters per step (average adult)
#define DEFAULT_USER_WEIGHT_KG      70.0f   // For calorie estimation

// Cadence Presets (Steps Per Minute)
#define CADENCE_WALK_MIN            80
#define CADENCE_WALK_DEFAULT        100     // ~4.5 km/h
#define CADENCE_JOG_DEFAULT         135     // ~7.0 km/h
#define CADENCE_RUN_DEFAULT         165     // ~10.5 km/h

// NVS Flash Save Interval (steps)
#define NVS_SAVE_STEP_INTERVAL      50
