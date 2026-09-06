package com.pedometer.companion

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.util.*

@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    companion object {
        const val TAG = "BleManager"
        val UUID_CUSTOM_SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val UUID_CHAR_LIVE: UUID      = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        val UUID_CHAR_HISTORY: UUID   = UUID.fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e")
        val UUID_CHAR_CMD: UUID       = UUID.fromString("6e400004-b5a3-f393-e0a9-e50e24dcca9e")
        val UUID_CCCD: UUID           = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    interface BleEventListener {
        fun onConnectionStateChange(connected: Boolean)
        fun onLiveMetricsReceived(metrics: StepMetrics)
        fun onHistoryReceived(history: HourlyHistory)
        fun onLogMessage(message: String)
    }

    var listener: BleEventListener? = null

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        manager?.adapter
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var cmdCharacteristic: BluetoothGattCharacteristic? = null
    private var isScanning = false
    private val mainHandler = Handler(Looper.getMainLooper())

    // Queue for writing CCCD descriptors sequentially
    private val descriptorQueue: Queue<BluetoothGattDescriptor> = LinkedList()

    fun isConnected(): Boolean {
        return bluetoothGatt != null && cmdCharacteristic != null
    }

    fun startScan() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            listener?.onLogMessage("Bluetooth is disabled or not available")
            return
        }

        // 1. Check bonded devices first
        val bonded = adapter.bondedDevices ?: emptySet()
        for (dev in bonded) {
            val name = dev.name ?: ""
            if (name.contains("ESP32", ignoreCase = true) ||
                name.contains("Pedometer", ignoreCase = true) ||
                dev.address.equals("CC:BA:97:F3:C1:FA", ignoreCase = true)) {
                listener?.onLogMessage("Found paired pedometer: ${dev.name ?: "ESP32"} [${dev.address}]. Connecting...")
                connectToDevice(dev)
                return
            }
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            listener?.onLogMessage("BLE Scanner not ready")
            return
        }

        if (isScanning) return
        isScanning = true
        listener?.onLogMessage("Scanning for 'ESP32-C6-Pedometer'...")

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filters = listOf(
            android.bluetooth.le.ScanFilter.Builder().setDeviceName("ESP32-C6-Pedometer").build(),
            android.bluetooth.le.ScanFilter.Builder().setServiceUuid(android.os.ParcelUuid(UUID_CUSTOM_SERVICE)).build(),
            android.bluetooth.le.ScanFilter.Builder().setServiceUuid(android.os.ParcelUuid(UUID.fromString("00001814-0000-1000-8000-00805f9b34fb"))).build()
        )

        try {
            scanner.startScan(filters, settings, scanCallback)
        } catch (e: Exception) {
            try {
                scanner.startScan(null, settings, scanCallback)
            } catch (e2: Exception) {
                isScanning = false
                listener?.onLogMessage("Start scan error: ${e2.message}")
                return
            }
        }

        // Stop scanning after 8 seconds timeout, then fallback to direct connection
        mainHandler.postDelayed({
            if (isScanning) {
                stopScan()
                listener?.onLogMessage("Scan timeout. Connecting directly to CC:BA:97:F3:C1:FA...")
                try {
                    val dev = adapter.getRemoteDevice("CC:BA:97:F3:C1:FA")
                    connectToDevice(dev)
                } catch (e: Exception) {
                    listener?.onLogMessage("Direct connect failed: ${e.message}")
                }
            }
        }, 8000)
    }

    fun stopScan() {
        if (!isScanning) return
        isScanning = false
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan", e)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val record = result.scanRecord
            val name = record?.deviceName ?: result.device.name
            val hasService = record?.serviceUuids?.any { it.uuid == UUID_CUSTOM_SERVICE } == true

            if (name?.contains("ESP32-C6-Pedometer", ignoreCase = true) == true ||
                name?.contains("Pedometer", ignoreCase = true) == true ||
                hasService) {
                listener?.onLogMessage("Found target: ${name ?: "ESP32"} [${result.device.address}]")
                stopScan()
                connectToDevice(result.device)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            listener?.onLogMessage("BLE scan failed with error code: $errorCode")
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        listener?.onLogMessage("Connecting to GATT at ${device.address}...")
        bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(context, false, gattCallback)
        }
    }

    fun disconnect() {
        descriptorQueue.clear()
        bluetoothGatt?.let { gatt ->
            gatt.disconnect()
            gatt.close()
        }
        bluetoothGatt = null
        cmdCharacteristic = null
        listener?.onConnectionStateChange(false)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mainHandler.post {
                    listener?.onLogMessage("GATT Connected! Negotiating MTU 512...")
                    listener?.onConnectionStateChange(true)
                }
                // Request MTU 512 for full JSON packets
                val mtuSuccess = gatt.requestMtu(512)
                if (!mtuSuccess) {
                    mainHandler.post {
                        listener?.onLogMessage("requestMtu failed, falling back to discoverServices")
                    }
                    gatt.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mainHandler.post {
                    listener?.onLogMessage("GATT Disconnected (status: $status)")
                    listener?.onConnectionStateChange(false)
                }
                descriptorQueue.clear()
                cmdCharacteristic = null
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            mainHandler.post {
                listener?.onLogMessage("BLE MTU negotiated: $mtu bytes. Discovering services...")
            }
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(UUID_CUSTOM_SERVICE)
                if (service != null) {
                    cmdCharacteristic = service.getCharacteristic(UUID_CHAR_CMD)

                    descriptorQueue.clear()

                    // 1. Enable Live Notifications
                    val liveChar = service.getCharacteristic(UUID_CHAR_LIVE)
                    if (liveChar != null) {
                        gatt.setCharacteristicNotification(liveChar, true)
                        liveChar.getDescriptor(UUID_CCCD)?.let { descriptorQueue.add(it) }
                    }

                    // 2. Enable History Notifications
                    val histChar = service.getCharacteristic(UUID_CHAR_HISTORY)
                    if (histChar != null) {
                        gatt.setCharacteristicNotification(histChar, true)
                        histChar.getDescriptor(UUID_CCCD)?.let { descriptorQueue.add(it) }
                    }

                    // Start descriptor writing sequentially
                    processNextDescriptor(gatt)
                } else {
                    mainHandler.post {
                        listener?.onLogMessage("Custom Pedometer service not found on device!")
                    }
                }
            } else {
                mainHandler.post {
                    listener?.onLogMessage("Service discovery failed with status: $status")
                }
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (descriptorQueue.isNotEmpty()) {
                processNextDescriptor(gatt)
            } else {
                mainHandler.post {
                    listener?.onLogMessage("All notifications subscribed! Syncing time and history...")
                    // Sync local time to align hourly step buckets
                    val cal = Calendar.getInstance()
                    val currentHour = cal.get(Calendar.HOUR_OF_DAY)
                    val unixSec = (System.currentTimeMillis() / 1000).toLong()
                    sendCommand("TIME_HR:$currentHour")
                    sendCommand("TIME:$unixSec")
                    sendCommand("REQ_HIST")
                }
            }
        }

        // Android 13+ (API 33+)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleCharacteristicPayload(characteristic.uuid, value)
        }

        // Older Android versions
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            @Suppress("DEPRECATION")
            val data = characteristic.value ?: return
            handleCharacteristicPayload(characteristic.uuid, data)
        }
    }

    private fun processNextDescriptor(gatt: BluetoothGatt) {
        val desc = descriptorQueue.poll() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(desc)
        }
    }

    private fun handleCharacteristicPayload(uuid: UUID, data: ByteArray) {
        val jsonStr = String(data, Charsets.UTF_8)
        when (uuid) {
            UUID_CHAR_LIVE -> {
                try {
                    val json = JSONObject(jsonStr)
                    val metrics = StepMetrics(
                        steps = json.optLong("steps", 0),
                        cadence = json.optInt("cadence", 0),
                        speed = json.optDouble("speed", 0.0).toFloat(),
                        distanceKm = json.optDouble("dist", 0.0).toFloat(),
                        caloriesKcal = json.optDouble("kcal", 0.0).toFloat(),
                        activeSeconds = json.optLong("sec", 0),
                        targetGoal = json.optLong("goal", 10000),
                        mode = json.optString("mode", "PAUSED")
                    )
                    mainHandler.post { listener?.onLiveMetricsReceived(metrics) }
                } catch (e: Exception) {
                    Log.e(TAG, "Live JSON parse error: $jsonStr", e)
                }
            }
            UUID_CHAR_HISTORY -> {
                try {
                    val json = JSONObject(jsonStr)
                    val hourlyArr = json.optJSONArray("hourly")
                    val hourlyList = mutableListOf<Int>()
                    if (hourlyArr != null) {
                        for (i in 0 until hourlyArr.length()) {
                            hourlyList.add(hourlyArr.getInt(i))
                        }
                    }
                    val dailyArr = json.optJSONArray("daily")
                    val dailyList = mutableListOf<Long>()
                    if (dailyArr != null) {
                        for (i in 0 until dailyArr.length()) {
                            dailyList.add(dailyArr.getLong(i))
                        }
                    }
                    val history = HourlyHistory(
                        hourly = hourlyList,
                        daily = dailyList,
                        total = json.optLong("total", 0)
                    )
                    mainHandler.post {
                        listener?.onHistoryReceived(history)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "History JSON parse error: $jsonStr", e)
                }
            }
        }
    }

    fun sendCommand(cmd: String) {
        val gatt = bluetoothGatt ?: return
        val char = cmdCharacteristic ?: return
        val bytes = cmd.toByteArray(Charsets.UTF_8)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            char.value = bytes
            @Suppress("DEPRECATION")
            char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(char)
        }
        listener?.onLogMessage("[TX] $cmd")
    }

    fun requestHistory() = sendCommand("REQ_HIST")
    fun setMode(mode: String) = sendCommand(mode.uppercase())
    fun addSteps(count: Int) = sendCommand("ADD:$count")
    fun setGoal(goal: Int) = sendCommand("GOAL:$goal")
    fun resetStats() = sendCommand("RESET")
    fun syncTime() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val epoch = System.currentTimeMillis() / 1000
        sendCommand("TIME_HR:$hour")
        sendCommand("TIME:$epoch")
    }
}
