#include "ble_pedometer_service.h"
#include "rgb_led.h"

BlePedometerService BleService;

BlePedometerService::BlePedometerService()
    : _pServer(nullptr),
      _pRscService(nullptr),
      _pRscMeasurementChar(nullptr),
      _pRscFeatureChar(nullptr),
      _pCustomService(nullptr),
      _pLiveChar(nullptr),
      _pHistoryChar(nullptr),
      _pCmdChar(nullptr),
      _pBatteryService(nullptr),
      _pBatteryChar(nullptr),
      _device_connected(false),
      _old_device_connected(false),
      _last_rsc_notify_ms(0),
      _last_live_notify_ms(0) {
}

void BlePedometerService::begin() {
    // 1. Initialize BLE Device
    BLEDevice::init(BLE_DEVICE_NAME);
    
    // Set MTU to 512 for fast batch JSON history transfer
    BLEDevice::setMTU(512);

    // 2. Create BLE Server
    _pServer = BLEDevice::createServer();
    _pServer->setCallbacks(this);

    // 3. Create Standard RSC (Running Speed and Cadence) Service (0x1814)
    _pRscService = _pServer->createService(BLEUUID((uint16_t)0x1814));

    // RSC Measurement Characteristic (0x2A53, Notify)
    _pRscMeasurementChar = _pRscService->createCharacteristic(
        BLEUUID((uint16_t)0x2A53),
        BLECharacteristic::PROPERTY_NOTIFY
    );

    // RSC Feature Characteristic (0x2A54, Read: Stride Length, Distance, Walk/Run supported)
    _pRscFeatureChar = _pRscService->createCharacteristic(
        BLEUUID((uint16_t)0x2A54),
        BLECharacteristic::PROPERTY_READ
    );
    uint16_t rsc_features = 0x0007; // bit 0 (Stride), bit 1 (Distance), bit 2 (Walk/Run status)
    _pRscFeatureChar->setValue((uint8_t*)&rsc_features, 2);

    _pRscService->start();

    // 4. Create Custom Pedometer Sync Service (Nordic UART Base)
    _pCustomService = _pServer->createService(BLEUUID(BLE_UUID_CUSTOM_SERVICE));

    // Live Stats Characteristic
    _pLiveChar = _pCustomService->createCharacteristic(
        BLEUUID(BLE_UUID_CHAR_LIVE_DATA),
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
    );

    // History Dump Characteristic
    _pHistoryChar = _pCustomService->createCharacteristic(
        BLEUUID(BLE_UUID_CHAR_HISTORY_SYNC),
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
    );

    // Remote Command Characteristic (Write from Android)
    _pCmdChar = _pCustomService->createCharacteristic(
        BLEUUID(BLE_UUID_CHAR_COMMAND),
        BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_WRITE_NR
    );
    _pCmdChar->setCallbacks(this);

    _pCustomService->start();

    // 5. Create Battery Service (0x180F)
    _pBatteryService = _pServer->createService(BLEUUID((uint16_t)0x180F));
    _pBatteryChar = _pBatteryService->createCharacteristic(
        BLEUUID((uint16_t)0x2A19),
        BLECharacteristic::PROPERTY_READ | BLECharacteristic::PROPERTY_NOTIFY
    );
    uint8_t initial_battery = 98;
    _pBatteryChar->setValue(&initial_battery, 1);
    _pBatteryService->start();

    // 6. Device Information Service (0x180A)
    BLEService* pDevInfo = _pServer->createService(BLEUUID((uint16_t)0x180A));
    BLECharacteristic* pMfg = pDevInfo->createCharacteristic(BLEUUID((uint16_t)0x2A29), BLECharacteristic::PROPERTY_READ);
    pMfg->setValue(BLE_MANUFACTURER_NAME);
    BLECharacteristic* pMdl = pDevInfo->createCharacteristic(BLEUUID((uint16_t)0x2A24), BLECharacteristic::PROPERTY_READ);
    pMdl->setValue(BLE_MODEL_NUMBER);
    BLECharacteristic* pFw = pDevInfo->createCharacteristic(BLEUUID((uint16_t)0x2A26), BLECharacteristic::PROPERTY_READ);
    pFw->setValue(BLE_FIRMWARE_REV);
    pDevInfo->start();

    // 7. Start Advertising
    BLEAdvertising* pAdvertising = BLEDevice::getAdvertising();
    pAdvertising->addServiceUUID(BLEUUID((uint16_t)0x1814));
    pAdvertising->addServiceUUID(BLEUUID(BLE_UUID_CUSTOM_SERVICE));
    pAdvertising->setScanResponse(true);
    pAdvertising->setMinPreferred(0x06);
    pAdvertising->setMinPreferred(0x12);
    BLEDevice::startAdvertising();
}

void BlePedometerService::onConnect(BLEServer* pServer) {
    _device_connected = true;
    RgbLed.setBleConnected(true);
}

void BlePedometerService::onDisconnect(BLEServer* pServer) {
    _device_connected = false;
    RgbLed.setBleConnected(false);
    // Restart advertising to allow re-connection
    BLEDevice::startAdvertising();
}

void BlePedometerService::onWrite(BLECharacteristic* pCharacteristic) {
    if (pCharacteristic == _pCmdChar) {
        String val = pCharacteristic->getValue().c_str();
        val.trim();
        if (val.length() > 0) {
            _processCommand(val);
        }
    }
}

void BlePedometerService::_processCommand(const String& cmd) {
    String upper = cmd;
    upper.toUpperCase();

    if (upper == "PAUSE") {
        Pedometer.setMode(MODE_PAUSED);
    } 
    else if (upper == "WALK" || upper == "RESUME") {
        Pedometer.setMode(MODE_WALKING);
    } 
    else if (upper == "JOG") {
        Pedometer.setMode(MODE_JOGGING);
    } 
    else if (upper == "RUN") {
        Pedometer.setMode(MODE_RUNNING);
    } 
    else if (upper == "CYCLE") {
        Pedometer.cycleMode();
    } 
    else if (upper == "RESET") {
        Pedometer.resetStats();
    } 
    else if (upper == "REQ_HIST" || upper == "GET_HISTORY") {
        sendHistory();
    } 
    else if (upper.startsWith("CAD:") || upper.startsWith("CADENCE:")) {
        int idx = upper.indexOf(':');
        uint16_t spm = upper.substring(idx + 1).toInt();
        if (spm > 0) Pedometer.setCadence(spm);
    } 
    else if (upper.startsWith("ADD:") || upper.startsWith("ADD_STEPS:")) {
        int idx = upper.indexOf(':');
        uint32_t steps = upper.substring(idx + 1).toInt();
        if (steps > 0) Pedometer.addSteps(steps);
    } 
    else if (upper.startsWith("SET:") || upper.startsWith("SET_STEPS:")) {
        int idx = upper.indexOf(':');
        uint32_t steps = upper.substring(idx + 1).toInt();
        Pedometer.setTotalSteps(steps);
    } 
    else if (upper.startsWith("GOAL:") || upper.startsWith("SET_GOAL:")) {
        int idx = upper.indexOf(':');
        uint32_t goal = upper.substring(idx + 1).toInt();
        if (goal > 0) Pedometer.setTargetGoal(goal);
    } 
    else if (upper.startsWith("TIME_HR:")) {
        int idx = upper.indexOf(':');
        uint8_t hour = upper.substring(idx + 1).toInt();
        if (hour < 24) Pedometer.setTime(hour, 0, 0);
    }
    else if (upper.startsWith("TIME:")) {
        int idx = upper.indexOf(':');
        uint32_t timestamp = upper.substring(idx + 1).toInt();
        if (timestamp > 0) Pedometer.setUnixTime(timestamp);
    }
}

void BlePedometerService::notifyLiveStep(const StepMetrics& metrics) {
    if (!_device_connected) return;

    // Send Live JSON payload
    String json = Pedometer.getLiveJson();
    _pLiveChar->setValue(json.c_str());
    _pLiveChar->notify();

    // Send RSC Measurement
    _sendRscMeasurement(metrics);
}

void BlePedometerService::_sendRscMeasurement(const StepMetrics& metrics) {
    if (!_device_connected || !_pRscMeasurementChar) return;

    // Bluetooth SIG RSC Measurement Packet Format:
    // [0] Flags:
    //     bit 0: Instantaneous Stride Length Present (1)
    //     bit 1: Total Distance Present (1)
    //     bit 2: Walking or Running Status (0: Walking, 1: Running)
    // [1..2] Instantaneous Speed (uint16_t, 1/256 m/s)
    // [3] Instantaneous Cadence (uint8_t, 1/min)
    // [4..5] Instantaneous Stride Length (uint16_t, cm)
    // [6..9] Total Distance (uint32_t, 1/10 m = decimeters)

    uint8_t flags = 0x03; // Stride & Distance present
    if (metrics.mode == MODE_RUNNING || metrics.mode == MODE_JOGGING) {
        flags |= 0x04; // Running
    }

    // Speed: km/h to m/s -> * 256
    float speed_mps = (metrics.speed_kmh / 3.6f);
    uint16_t speed_raw = (uint16_t)(speed_mps * 256.0f);

    uint8_t cadence_raw = (uint8_t)(metrics.cadence_spm > 255 ? 255 : metrics.cadence_spm);
    
    // Stride in cm
    float stride_m = (metrics.mode == MODE_RUNNING) ? 1.05f : ((metrics.mode == MODE_JOGGING) ? 0.85f : 0.75f);
    uint16_t stride_cm = (uint16_t)(stride_m * 100.0f);

    // Total distance in 1/10 meters (decimeters)
    uint32_t total_dist_dm = (uint32_t)(metrics.distance_km * 10000.0f);

    uint8_t packet[10];
    packet[0] = flags;
    packet[1] = speed_raw & 0xFF;
    packet[2] = (speed_raw >> 8) & 0xFF;
    packet[3] = cadence_raw;
    packet[4] = stride_cm & 0xFF;
    packet[5] = (stride_cm >> 8) & 0xFF;
    packet[6] = total_dist_dm & 0xFF;
    packet[7] = (total_dist_dm >> 8) & 0xFF;
    packet[8] = (total_dist_dm >> 16) & 0xFF;
    packet[9] = (total_dist_dm >> 24) & 0xFF;

    _pRscMeasurementChar->setValue(packet, 10);
    _pRscMeasurementChar->notify();
}

void BlePedometerService::sendHistory() {
    if (!_device_connected || !_pHistoryChar) return;
    String hist_json = Pedometer.getHistoryJson();
    _pHistoryChar->setValue(hist_json.c_str());
    _pHistoryChar->notify();
}

void BlePedometerService::update(const StepMetrics& metrics) {
    unsigned long now = millis();

    // Periodic live notification every 1 second when connected
    if (_device_connected) {
        if (now - _last_live_notify_ms >= 1000) {
            _last_live_notify_ms = now;
            notifyLiveStep(metrics);
        }
    }
}
