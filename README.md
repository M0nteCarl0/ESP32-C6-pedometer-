# ESP32-C6 Pedometer Firmware for Waveshare ESP32-C6-LCD-1.47

<p align="center">
  <a href="https://github.com/M0nteCarl0/ESP32-C6-pedometer-/actions/workflows/build.yml"><img src="https://github.com/M0nteCarl0/ESP32-C6-pedometer-/actions/workflows/build.yml/badge.svg" alt="Build status"></a>
  <a href="https://github.com/M0nteCarl0/ESP32-C6-pedometer-/releases/latest"><img src="https://img.shields.io/github/v/release/M0nteCarl0/ESP32-C6-pedometer-?label=release" alt="Latest release"></a>
  <a href="https://github.com/M0nteCarl0/ESP32-C6-pedometer-/releases"><img src="https://img.shields.io/github/downloads/M0nteCarl0/ESP32-C6-pedometer-/total?label=downloads" alt="Release downloads"></a>
</p>

A complete smart step-counter / fitness-tracker firmware for the [**Waveshare ESP32-C6-LCD-1.47**](https://www.waveshare.com/esp32-c6-lcd-1.47.htm) board (1.47" color IPS display, ST7789 172x320), with an addressable WS2812 RGB LED, NVS flash persistence, a Bluetooth LE **Running Speed and Cadence (RSC)** profile, a browser-based companion panel, and a native Android app with GPS route emulation.

<p align="center">
  <a href="https://www.waveshare.com/esp32-c6-lcd-1.47.htm"><img src="https://www.waveshare.com/img/devkit/ESP32-C6-LCD-1.47/ESP32-C6-LCD-1.47-3.jpg" alt="Waveshare ESP32-C6-LCD-1.47 module" width="45%"></a>
  <a href="https://www.waveshare.com/esp32-c6-lcd-1.47.htm"><img src="https://www.waveshare.com/img/devkit/ESP32-C6-LCD-1.47/ESP32-C6-LCD-1.47-1.jpg" alt="Waveshare ESP32-C6-LCD-1.47 module with the LCD powered on" width="45%"></a>
</p>

<p align="center">Hardware: <a href="https://www.waveshare.com/esp32-c6-lcd-1.47.htm">Waveshare ESP32-C6-LCD-1.47 product page</a>. Module photos are hosted by Waveshare.</p>

---

## Features

### 1. Realistic walk / run simulation

- Modes: `PAUSE`, `WALK` (~100 spm, 4.5 km/h), `JOG` (~135 spm, 7.0 km/h), `RUN` (~165 spm, 10.5 km/h) and `CUSTOM` (30 to 240 spm).
- Natural step variation (interval micro-jitter of about 4%) so the output is not mechanically periodic.
- Distance (km), calories (MET formula), instantaneous speed (km/h) and time in motion.
- Hourly logging for 24 hours plus a 7-day history.
- Automatic NVS persistence on pause and every 50 steps, so nothing is lost on power loss.

### 2. On-screen UI (1.47" 172x320 IPS, ST7789)

- Large step counter with a daily-goal progress arc.
- Metric cards: distance (km), calories (kcal), cadence (spm), speed (km/h).
- Header: BLE status badge, GPS status badge, active mode pill, battery percentage.
- Footer: active time, or elapsed / remaining time when a movement-time target is set; the configured route name or the current GPS coordinates are shown below it.
- Display thermal protection: backlight defaults to a safe 47% duty cycle (PWM 120/255), following WaveShare's recommendation to limit panel heating.

### 3. Controls

- **BOOT button (GPIO 9)**
  - Single click: cycle modes (`WALK` -> `JOG` -> `RUN` -> `PAUSE`).
  - Double click: add `+500` steps instantly.
  - Hold longer than 1.2 s: pause / resume.
  - Hold longer than 5 s: reset daily statistics.
- **RGB LED (GPIO 8)**
  - Soft green pulse on every step.
  - Blue glow while a BLE client is connected.
  - Warm amber in pause mode.
  - Celebration animation when the daily goal (10,000 steps) is reached.

### 4. Android integration

- **Web Bluetooth companion (`android_web_companion/`)** - no APK required, runs in Chrome, Samsung Internet or Edge on Android:
  - live hourly activity chart for the last 24 hours;
  - CSV export of hourly and total statistics;
  - JSON export structured for Google Fit / Health Connect;
  - copy-summary-to-clipboard action;
  - remote control: cadence slider, `+500` / `+1000` / `+5000` step buttons, goal setting and clock sync.
- **Standard BLE RSC profile (`0x1814`)**: recognized as a running sensor / footpod by Strava, Wahoo Fitness, nRF Toolbox, Polar Beat, Zwift and Gadgetbridge.
- **Native Android Studio project (`android_native_app/`)**: Kotlin app with automatic BLE scan and parsing, plus direct upload to Google Fit through Health Connect.

### 5. GPS route emulation and map view

- **Route map screen** (`app/src/main/assets/map.html`, Leaflet + OpenStreetMap tiles): tap the map to place waypoints, the polyline is redrawn live with total distance, and the current "runner" position follows the emulated track.
- **Route manager** (`RouteManager.kt`): great-circle distance and bearing math, position interpolation along the polyline, seamless looping when the travelled distance exceeds the route length, three built-in preset loops (Gorky Park ~2.8 km, Luzhniki stadium ~1.4 km, Kremlin embankment ~3.6 km), and named routes saved in app storage.
- **GPS emulator** (`GpsEmulatorManager.kt`): registers a test GPS provider, injects mock locations at 1 Hz using the speed received from the device, and streams `GPS:<lat>,<lon>` plus `ROUTE:<name>` to the ESP32 so the display shows live position and route.
- **Targets from the app**: step goal and movement-time target can be set (with `5k` / `10k` / `15k` and `15m` / `30m` / `60m` shortcuts) or cleared; the device persists both.

---

## Pinout (Waveshare ESP32-C6-LCD-1.47)

| Peripheral | Signal | ESP32-C6 pin | Description |
| :--- | :--- | :--- | :--- |
| **ST7789 LCD** | MOSI | `GPIO 6` | SPI data |
| | SCLK | `GPIO 7` | SPI clock |
| | CS | `GPIO 14` | Display chip select |
| | DC | `GPIO 15` | Command / data |
| | RST | `GPIO 21` | Display hardware reset |
| | BL | `GPIO 22` | Backlight control (PWM) |
| **RGB LED** | DATA | `GPIO 8` | WS2812 addressable LED |
| **Button** | BOOT | `GPIO 9` | User button (active LOW) |
| **MicroSD (TF)** | MISO | `GPIO 5` | MicroSD SPI data |
| | MOSI | `GPIO 6` | MicroSD SPI data |
| | SCLK | `GPIO 7` | MicroSD SPI clock |
| | CS | `GPIO 4` | MicroSD chip select |

---

## Build and flash

Prebuilt binaries are attached to every release: `esp32-c6-pedometer-factory.bin` (a single image to flash at offset `0x0`, for example with the Espressif Flash Download Tool or `esptool`), `esp32-c6-pedometer-firmware.bin` (application image for OTA updates) and `esp32-pedometer-sync-debug.apk`. The `Build` GitHub Actions workflow produces them on every push and publishes them when a `v*` tag is pushed.

### Option A: PlatformIO (VS Code) - recommended

1. Install [Visual Studio Code](https://code.visualstudio.com/) with the **PlatformIO IDE** extension.
2. Open the `esp32_firmware/` folder: `File` -> `Open Folder...` -> select `esp32_firmware`.
3. Connect the board over USB Type-C.
4. Click **PlatformIO: Upload**, or run:
   ```bash
   pio run --target upload
   ```
5. Open the serial monitor:
   ```bash
   pio device monitor
   ```

The default environment is `esp32-c6-lcd-147`. `platformio.ini` pins `upload_port` / `monitor_port` to `COM13`; change those lines if your board enumerates on a different port.

### Option B: Arduino IDE 2.x

1. Install **Arduino IDE 2.x**.
2. In `File` -> `Preferences`, add the board manager URL:
   `https://espressif.github.io/arduino-esp32/package_esp32_index.json`
3. In `Tools` -> `Board` -> `Boards Manager`, install **esp32** by Espressif, version **3.0.0 or newer**.
4. Open `esp32_firmware/esp32_c6_pedometer/esp32_c6_pedometer.ino`.
5. In the `Tools` menu set:
   - **Board**: `ESP32C6 Dev Module`
   - **USB CDC On Boot**: `Enabled`
   - **Flash Size**: `4MB (32Mb)`
   - **Port**: the COM port of your board
6. Click **Upload**.

The `src/` and `esp32_c6_pedometer/` trees are kept in sync: the first one is built by PlatformIO, the second one is the ready-to-open Arduino sketch.

---

## Getting the data into Android

### Option 1: Web Bluetooth panel (no app install)

1. On Android, open **Google Chrome**, **Samsung Internet** or **Edge**.
2. Open `android_web_companion/index.html` (serve it locally or publish it to GitHub Pages / any host).
   > Quick local check on a PC, from the `android_web_companion` folder:
   > ```bash
   > npx serve .
   > # or: python -m http.server 8080
   > ```
3. Press the connect button (the panel UI is in Russian).
4. Pick **`ESP32-C6-Pedometer`** in the system device picker.
5. The panel then shows live steps, the hourly chart, CSV / JSON export buttons, a copy-summary button and the remote-control section.

### Option 2: Sports trackers (Strava, Wahoo, nRF Toolbox)

Because the firmware implements the official **Bluetooth SIG Running Speed and Cadence (0x1814)** service:

1. Enable Bluetooth on Android.
2. Open an app such as **Strava** or **Wahoo Fitness**.
3. Go to *Record activity* -> *Sensors* -> *Search for running / footpod sensors*.
4. Select **ESP32-C6-Pedometer**.
5. The app receives speed, cadence and distance in real time.

### Option 3: Native Android app (`android_native_app/`)

The Kotlin app reads live metrics and hourly history over BLE and uploads steps, distance and calories to **Google Fit** through **Health Connect** (the official Google Fit path on Android 14/15) with the **Google Fit History API** as a fallback.

1. Open the `android_native_app` project in **Android Studio**, or build the APK:
   ```bash
   cd android_native_app
   ./gradlew assembleDebug
   ```
   The project uses Gradle 8.9, Android Gradle Plugin 8.2.2, Kotlin 1.9.22, `compileSdk 34`, `minSdk 26`.
2. Install the APK:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
   On Windows you can also use `install_to_xiaomi.bat`, which calls `adb install` and launches the app. Edit the `ADB` and `APK` paths at the top of that file first.
3. Open **ESP32 Pedometer Sync**. The app mixes English labels with Russian section headers and status text; the ESP32 control block (`Walk` / `Jog` / `Pause` / `+1000`) is on the same screen.
4. Press **Scan & Connect**: the app negotiates a 512-byte MTU, subscribes to the live step stream and pulls the hourly history.
5. Press **Upload Steps to Google Fit**: on first run Health Connect asks for write permissions for steps, distance and calories; grant them and the activity intervals are written to Health Connect, where Google Fit picks them up.
6. **Share JSON** exports the structured JSON payload to any other app or cloud storage.

#### Using the GPS route emulation

1. Enable developer options on the phone and set the pedometer app as the **mock location app** (`Settings` -> `Developer options` -> `Select mock location app`); the app has a button that opens these settings directly.
2. Pick a preset route, or tap the map to draw your own and save it under a name.
3. Press the GPS toggle to start emulation. A test GPS provider is registered and mock locations are injected at 1 Hz along the route, looping when the route ends.
4. While connected, the app forwards each position to the ESP32, which displays the `GPS` badge, the coordinates or route name, and keeps speed in sync with the device mode.

---

## BLE control protocol

### 1. Step sync service (custom UUID base `6e400001-...`)

| Characteristic | UUID | Properties | Purpose |
| :--- | :--- | :--- | :--- |
| **Live Stats** | `6e400002-b5a3-f393-e0a9-e50e24dcca9e` | Read, Notify | JSON with current metrics |
| **History Sync** | `6e400003-b5a3-f393-e0a9-e50e24dcca9e` | Read, Notify | 24-hour and 7-day step arrays |
| **Command** | `6e400004-b5a3-f393-e0a9-e50e24dcca9e` | Write | Remote control commands |

Live stats payload example:

```json
{"steps":12345,"cadence":102,"speed":4.6,"dist":9.26,"kcal":421.3,"sec":7400,"goal":10000,"goal_sec":1800,"sess_sec":900,"mode":"WALK","lat":55.731456,"lon":37.603416,"gps":true,"route":"Gorky Park"}
```

Fields: `sec` is the total active time, `sess_sec` the time in motion of the current session, `goal_sec` the movement-time target in seconds (`0` means unlimited), and `lat` / `lon` / `gps` / `route` carry the position and route name pushed from the phone.

#### Supported commands (written to `6e400004-...`)

- `PAUSE` - stop generating steps.
- `WALK` / `RESUME` - walking mode (100 spm).
- `JOG` - jogging mode (135 spm).
- `RUN` - running mode (165 spm).
- `CYCLE` - switch to the next mode.
- `CAD:<num>` (alias `CADENCE:`) - set a custom cadence, e.g. `CAD:140`.
- `ADD:<num>` (alias `ADD_STEPS:`) - add N steps, e.g. `ADD:1000`.
- `SET:<num>` (alias `SET_STEPS:`) - set the exact step value, e.g. `SET:8500`.
- `GOAL:<num>` (aliases `SET_GOAL:`, `TARGET_STEPS:`) - set the step goal, e.g. `GOAL:12000`.
- `TARGET_TIME:<seconds>` (aliases `GOAL_TIME:`, `TIME_GOAL:`) - set the movement-time target.
- `CLEAR_GOAL` / `RESET_TARGETS` - clear both targets.
- `GPS:<lat>,<lon>` - update the displayed position.
- `ROUTE:<name>` - set the displayed route name.
- `RESET` - clear daily statistics.
- `REQ_HIST` (alias `GET_HISTORY`) - request the hourly history dump.
- `TIME_HR:<0-23>` - set the current local hour for correct hourly bucketing.
- `TIME:<unix_timestamp>` - synchronize the device clock.

### 2. Standard Bluetooth SIG services

- `0x1814` - Running Speed and Cadence Service (`0x2A53` RSC Measurement, `0x2A54` RSC Feature).
- `0x180F` - Battery Service (`0x2A19` Battery Level %).
- `0x180A` - Device Information Service (manufacturer, model, firmware revision `1.0.0`).
