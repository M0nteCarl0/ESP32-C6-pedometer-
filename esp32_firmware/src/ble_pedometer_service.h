#pragma once

#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include "config.h"
#include "pedometer_engine.h"

class BlePedometerService : public BLEServerCallbacks, public BLECharacteristicCallbacks {
public:
    BlePedometerService();
    
    void begin();
    void update(const StepMetrics& metrics); // Called periodically
    
    bool isConnected() const { return _device_connected; }
    void notifyLiveStep(const StepMetrics& metrics);
    void sendHistory();

    // BLEServerCallbacks
    void onConnect(BLEServer* pServer) override;
    void onDisconnect(BLEServer* pServer) override;

    // BLECharacteristicCallbacks (Incoming commands from Android)
    void onWrite(BLECharacteristic* pCharacteristic) override;

private:
    BLEServer* _pServer;
    
    // Standard RSC Service
    BLEService* _pRscService;
    BLECharacteristic* _pRscMeasurementChar;
    BLECharacteristic* _pRscFeatureChar;
    
    // Custom Pedometer Sync Service
    BLEService* _pCustomService;
    BLECharacteristic* _pLiveChar;
    BLECharacteristic* _pHistoryChar;
    BLECharacteristic* _pCmdChar;

    // Battery Service
    BLEService* _pBatteryService;
    BLECharacteristic* _pBatteryChar;

    bool _device_connected;
    bool _old_device_connected;
    unsigned long _last_rsc_notify_ms;
    unsigned long _last_live_notify_ms;

    void _sendRscMeasurement(const StepMetrics& metrics);
    void _processCommand(const String& cmd);
};

extern BlePedometerService BleService;
