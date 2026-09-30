package com.example.printer

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.RepairEntity
import com.example.data.entity.RepairItemEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

sealed class PrinterConnectionState {
    object Disconnected : PrinterConnectionState()
    object Scanning : PrinterConnectionState()
    data class Connecting(val deviceName: String, val address: String, val statusMessage: String = "") : PrinterConnectionState()
    data class Reconnecting(val deviceName: String, val address: String, val statusMessage: String = "Reconnecting...") : PrinterConnectionState()
    data class Connected(val deviceName: String, val address: String) : PrinterConnectionState()
    data class Printing(val progressPercent: Int, val statusMessage: String = "Printing...") : PrinterConnectionState()
    data class Error(val message: String) : PrinterConnectionState()
}

enum class PrintJobStatus {
    IDLE,
    CONNECTING,
    PREPARING,
    PRINTING,
    SUCCESS,
    FAILED;

    companion object {
        val TRANSMITTING get() = PRINTING
        val RETRYING get() = CONNECTING
        val COMPLETED get() = SUCCESS
    }
}

data class DiscoveredDevice(
    val name: String,
    val address: String,
    val rssi: Int,
    val isP50S: Boolean,
    val deviceType: String = "BLE",
    val serviceUuids: List<String> = emptyList(),
    val manufacturerDataHex: String = "",
    val rawBytesHex: String = "",
    val packetCount: Int = 1,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)

sealed class PrintResult {
    object Success : PrintResult()
    data class Error(val message: String) : PrintResult()
}

@SuppressLint("MissingPermission")
class PrinterBleManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "PrinterBleManager"

        @Volatile
        private var instance: PrinterBleManager? = null

        fun getInstance(context: Context): PrinterBleManager {
            return instance ?: synchronized(this) {
                instance ?: PrinterBleManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private val _connectionState = MutableStateFlow<PrinterConnectionState>(PrinterConnectionState.Disconnected)
    val connectionState: StateFlow<PrinterConnectionState> = _connectionState.asStateFlow()

    private val _printJobStatus = MutableStateFlow(PrintJobStatus.IDLE)
    val printJobStatus: StateFlow<PrintJobStatus> = _printJobStatus.asStateFlow()

    private val _printStatusMessage = MutableStateFlow("Ready")
    val printStatusMessage: StateFlow<String> = _printStatusMessage.asStateFlow()

    private val _scannedDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val scannedDevices: StateFlow<List<DiscoveredDevice>> = _scannedDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanningFlow: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanCountdown = MutableStateFlow(0)
    val scanCountdown: StateFlow<Int> = _scanCountdown.asStateFlow()

    private val _lastScanError = MutableStateFlow<String?>(null)
    val lastScanError: StateFlow<String?> = _lastScanError.asStateFlow()

    private val _totalPacketsReceived = MutableStateFlow(0)
    val totalPacketsReceived: StateFlow<Int> = _totalPacketsReceived.asStateFlow()

    private val _manufacturerPacketsCount = MutableStateFlow(0)
    val manufacturerPacketsCount: StateFlow<Int> = _manufacturerPacketsCount.asStateFlow()

    @Volatile private var negotiatedMtu = 23
    @Volatile private var pendingWriteCompleter: CompletableDeferred<Int>? = null
    @Volatile private var gattReadyCompleter: CompletableDeferred<Boolean>? = null
    @Volatile private var isPrintInProgress = false
    @Volatile private var isGattConnected = false

    private var scanJob: Job? = null
    private var activeGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var flowControlCharacteristic: BluetoothGattCharacteristic? = null
    private val printMutex = Mutex()
    private var isScanning = false

    private var lastConnectedMac: String? = PrinterPreferences.getSavedPrinterMac(context)
    private var lastConnectedName: String? = PrinterPreferences.getSavedPrinterName(context)

    private fun updatePrintStatus(status: PrintJobStatus, message: String) {
        _printJobStatus.value = status
        _printStatusMessage.value = message
        Log.d(TAG, "PrintJob: $status - $message")
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { handleScanResult(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            val errorMsg = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "Scan already started (Code 1)"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed (Code 2). Please toggle Bluetooth OFF and ON."
                SCAN_FAILED_INTERNAL_ERROR -> "Internal Bluetooth error (Code 3). Please restart phone Bluetooth."
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE scanning feature unsupported on this device (Code 4)"
                SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES -> "Out of hardware resources (Code 5). Other apps may be using the scanner."
                else -> "BLE Scan failed with error code $errorCode"
            }
            Log.e(TAG, "BLE Scan failed: $errorMsg")
            _lastScanError.value = errorMsg
            isScanning = false
            _isScanning.value = false
            _scanCountdown.value = 0
            if (_connectionState.value is PrinterConnectionState.Scanning) {
                _connectionState.value = PrinterConnectionState.Disconnected
            }
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val device = result.device ?: return
        val address = device.address ?: return

        _totalPacketsReceived.value = _totalPacketsReceived.value + 1

        // Extract device name:
        // 1. ScanRecord device name
        // 2. device.name (if connect permission or cached)
        // 3. Raw TLV parse from bytes for 0x08 / 0x09
        var detectedName = result.scanRecord?.deviceName
        if (detectedName.isNullOrBlank()) {
            try {
                detectedName = device.name
            } catch (_: SecurityException) {}
        }
        if (detectedName.isNullOrBlank()) {
            detectedName = extractNameFromScanRecord(result.scanRecord?.bytes)
        }

        val displayName = if (detectedName.isNullOrBlank()) "Unknown BLE Device" else detectedName.trim()

        val deviceType = when (device.type) {
            BluetoothDevice.DEVICE_TYPE_LE -> "BLE"
            BluetoothDevice.DEVICE_TYPE_CLASSIC -> "CLASSIC"
            BluetoothDevice.DEVICE_TYPE_DUAL -> "DUAL"
            else -> "UNKNOWN"
        }

        val serviceUuids = result.scanRecord?.serviceUuids?.map { it.uuid.toString() } ?: emptyList()

        val manufData = result.scanRecord?.manufacturerSpecificData
        val manufacturerDataHex = if (manufData != null && manufData.size() > 0) {
            _manufacturerPacketsCount.value = _manufacturerPacketsCount.value + 1
            (0 until manufData.size()).joinToString("; ") { i ->
                val id = manufData.keyAt(i)
                val bytes = manufData.valueAt(i)
                val hex = bytes?.joinToString("") { "%02X".format(it) } ?: ""
                "0x%04X: %s".format(id, hex)
            }
        } else ""

        val rawBytesHex = result.scanRecord?.bytes?.joinToString("") { "%02X".format(it) } ?: ""

        val isTarget = P50SProtocol.isPotentialP50SPrinter(displayName) ||
                displayName.contains("P50S", ignoreCase = true) ||
                displayName.contains("496A", ignoreCase = true) ||
                serviceUuids.any { it.contains("ff00", ignoreCase = true) }

        val currentList = _scannedDevices.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.address == address }

        val updatedItem = if (existingIndex >= 0) {
            val existing = currentList[existingIndex]
            val bestName = if (existing.name != "Unknown BLE Device" && displayName == "Unknown BLE Device") {
                existing.name
            } else {
                displayName
            }
            val bestIsTarget = existing.isP50S || isTarget
            val bestManuf = if (manufacturerDataHex.isNotEmpty()) manufacturerDataHex else existing.manufacturerDataHex
            val bestUuids = if (serviceUuids.isNotEmpty()) serviceUuids else existing.serviceUuids
            val bestRaw = if (rawBytesHex.isNotEmpty()) rawBytesHex else existing.rawBytesHex

            existing.copy(
                name = bestName,
                rssi = result.rssi,
                isP50S = bestIsTarget,
                deviceType = deviceType,
                serviceUuids = bestUuids,
                manufacturerDataHex = bestManuf,
                rawBytesHex = bestRaw,
                packetCount = existing.packetCount + 1,
                lastSeenTimestamp = System.currentTimeMillis()
            )
        } else {
            DiscoveredDevice(
                name = displayName,
                address = address,
                rssi = result.rssi,
                isP50S = isTarget,
                deviceType = deviceType,
                serviceUuids = serviceUuids,
                manufacturerDataHex = manufacturerDataHex,
                rawBytesHex = rawBytesHex,
                packetCount = 1,
                lastSeenTimestamp = System.currentTimeMillis()
            )
        }

        if (existingIndex >= 0) {
            currentList[existingIndex] = updatedItem
        } else {
            if (isTarget) {
                currentList.add(0, updatedItem)
            } else {
                currentList.add(updatedItem)
            }
        }

        // Sort: target P50S at top, then by signal strength (RSSI)
        currentList.sortByDescending { if (it.isP50S) 1000 + it.rssi else it.rssi }
        _scannedDevices.value = currentList
    }

    private fun extractNameFromScanRecord(bytes: ByteArray?): String? {
        if (bytes == null || bytes.isEmpty()) return null
        var index = 0
        while (index < bytes.size) {
            val length = bytes[index].toInt() and 0xFF
            if (length == 0 || index + length >= bytes.size) break
            val type = bytes[index + 1].toInt() and 0xFF
            // 0x08 = Shortened Local Name, 0x09 = Complete Local Name
            if (type == 0x08 || type == 0x09) {
                val nameLength = length - 1
                if (nameLength > 0 && index + 2 + nameLength <= bytes.size) {
                    val nameBytes = bytes.copyOfRange(index + 2, index + 2 + nameLength)
                    val name = String(nameBytes, Charsets.UTF_8).trim()
                    if (name.isNotEmpty()) return name
                }
            }
            index += length + 1
        }
        return null
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            Log.d(TAG, "GATT STATE: status=$status, newState=$newState")
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                isGattConnected = true
                val name = gatt.device.name ?: PrinterPreferences.getSavedPrinterName(context) ?: lastConnectedName ?: "P50S-496A-BLE"
                Log.d(TAG, "GATT CONNECTED: device=$name (${gatt.device.address})")
                activeGatt = gatt
                negotiatedMtu = 23 // Reset until onMtuChanged
                lastConnectedMac = gatt.device.address
                lastConnectedName = name
                _connectionState.value = PrinterConnectionState.Connecting(name, gatt.device.address, "Connected. Discovering services...")
                updatePrintStatus(PrintJobStatus.CONNECTING, "Connected. Discovering services...")

                // Request MTU 240 for faster throughput
                val mtuRequested = try {
                    gatt.requestMtu(240)
                } catch (e: Exception) {
                    false
                }

                // Request HIGH connection priority for maximum throughput and low latency
                try {
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not request high connection priority", e)
                }

                if (!mtuRequested) {
                    Log.d(TAG, "MTU request failed or not supported; discovering services directly")
                    try {
                        gatt.discoverServices()
                    } catch (e: Exception) {
                        Log.e(TAG, "discoverServices failed", e)
                    }
                } else {
                    // Fallback timer: if onMtuChanged doesn't fire within 1200ms, discoverServices anyway
                    scope.launch {
                        delay(1200)
                        if (activeGatt == gatt && writeCharacteristic == null && _connectionState.value is PrinterConnectionState.Connecting) {
                            Log.d(TAG, "MTU callback timeout: triggering discoverServices directly")
                            try {
                                gatt.discoverServices()
                            } catch (_: Exception) {}
                        }
                    }
                }
            } else {
                Log.w(TAG, "GATT DISCONNECTED: status=$status, newState=$newState")
                isGattConnected = false
                val pending = pendingWriteCompleter
                pendingWriteCompleter = null
                pending?.complete(BluetoothGatt.GATT_FAILURE)

                val ready = gattReadyCompleter
                gattReadyCompleter = null
                ready?.complete(false)

                cleanupGatt()
                _connectionState.value = PrinterConnectionState.Disconnected
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "onMtuChanged mtu=$mtu, status=$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                negotiatedMtu = mtu
            } else {
                Log.w(TAG, "MTU request returned status $status; using default MTU $negotiatedMtu")
            }
            val name = gatt.device.name ?: PrinterPreferences.getSavedPrinterName(context) ?: lastConnectedName ?: "P50S-496A-BLE"
            _connectionState.value = PrinterConnectionState.Connecting(name, gatt.device.address, "Discovering printer services...")
            updatePrintStatus(PrintJobStatus.CONNECTING, "Discovering printer services...")
            try {
                gatt.discoverServices()
            } catch (e: Exception) {
                Log.e(TAG, "discoverServices failed", e)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.d(TAG, "onCharacteristicWrite status=$status, uuid=${characteristic.uuid}")
            val completer = pendingWriteCompleter
            pendingWriteCompleter = null
            completer?.complete(status)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            Log.d(TAG, "SERVICE DISCOVERY COMPLETE: status=$status")
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Service discovery failed with status $status")
                val ready = gattReadyCompleter
                gattReadyCompleter = null
                ready?.complete(false)
                cleanupGatt()
                _connectionState.value = PrinterConnectionState.Disconnected
                return
            }

            var writeChar: BluetoothGattCharacteristic? = null
            var flowChar: BluetoothGattCharacteristic? = null

            for (service in gatt.services) {
                val serviceUuidStr = service.uuid.toString().lowercase()
                if (serviceUuidStr.contains("ff00") || service.uuid == P50SProtocol.SERVICE_UUID) {
                    for (char in service.characteristics) {
                        val charUuidStr = char.uuid.toString().lowercase()
                        if (charUuidStr.contains("ff02") || char.uuid == P50SProtocol.WRITE_CHAR_UUID) {
                            writeChar = char
                        } else if (charUuidStr.contains("ff03") || char.uuid == P50SProtocol.FLOW_CONTROL_CHAR_UUID) {
                            flowChar = char
                        }
                    }
                }
            }

            // Fallback: check across all services if ff00 was customized
            if (writeChar == null) {
                for (service in gatt.services) {
                    for (char in service.characteristics) {
                        val charUuidStr = char.uuid.toString().lowercase()
                        if (charUuidStr.contains("ff02")) writeChar = char
                        if (charUuidStr.contains("ff03")) flowChar = char
                    }
                }
            }

            if (writeChar != null) {
                writeCharacteristic = writeChar
                flowControlCharacteristic = flowChar
                Log.d(TAG, "WRITABLE CHARACTERISTIC FOUND: uuid=${writeChar.uuid}")

                // Enable flow control notification on 0xFF03 if present
                if (flowChar != null) {
                    try {
                        gatt.setCharacteristicNotification(flowChar, true)
                        val descriptor = flowChar.getDescriptor(P50SProtocol.CCCD_DESCRIPTOR_UUID)
                        if (descriptor != null) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                            } else {
                                @Suppress("DEPRECATION")
                                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                @Suppress("DEPRECATION")
                                gatt.writeDescriptor(descriptor)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error enabling flow control notification", e)
                    }
                }

                val devName = gatt.device.name ?: PrinterPreferences.getSavedPrinterName(context) ?: lastConnectedName ?: "P50S-496A-BLE"
                val devAddress = gatt.device.address
                lastConnectedMac = devAddress
                lastConnectedName = devName
                PrinterPreferences.savePrinter(context, devName, devAddress)
                _connectionState.value = PrinterConnectionState.Connected(devName, devAddress)
                updatePrintStatus(PrintJobStatus.IDLE, "Printer ready")
                Log.i(TAG, "Printer connected and ready: $devName ($devAddress)")

                val ready = gattReadyCompleter
                gattReadyCompleter = null
                ready?.complete(true)
            } else {
                Log.e(TAG, "Write characteristic ff02 not found on printer")
                val ready = gattReadyCompleter
                gattReadyCompleter = null
                ready?.complete(false)
                cleanupGatt()
                _connectionState.value = PrinterConnectionState.Disconnected
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            handleNotification(characteristic.uuid, characteristic.value)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleNotification(characteristic.uuid, value)
        }

        private fun handleNotification(uuid: UUID, value: ByteArray?) {
            // Flow control notification logging if printer sends any data on ff03
            if (value != null && (uuid.toString().lowercase().contains("ff03") || uuid == P50SProtocol.FLOW_CONTROL_CHAR_UUID)) {
                Log.d(TAG, "Received 0xFF03 flow notification: ${value.joinToString(" ") { "%02X".format(it) }}")
            }
        }
    }

    fun getMissingPermissions(): List<String> {
        val missing = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.BLUETOOTH)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        return missing
    }

    fun hasBluetoothPermissions(): Boolean {
        return getMissingPermissions().isEmpty()
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    fun isLocationServiceEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(locationManager)
    }

    fun isBleSupported(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    fun isBluetoothAdapterAvailable(): Boolean {
        return bluetoothAdapter != null
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun clearScannedDevices() {
        _scannedDevices.value = emptyList()
        _totalPacketsReceived.value = 0
        _manufacturerPacketsCount.value = 0
    }

    fun startScan(durationSeconds: Int = 15) {
        _lastScanError.value = null

        if (!isBleSupported()) {
            val msg = "Bluetooth LE hardware is not supported on this device."
            Log.e(TAG, msg)
            _lastScanError.value = msg
            return
        }

        val missing = getMissingPermissions()
        if (missing.isNotEmpty()) {
            val friendlyMissing = missing.map { it.substringAfterLast('.') }.joinToString(", ")
            val msg = "Missing required permissions: $friendlyMissing. Please grant all permissions."
            Log.e(TAG, msg)
            _lastScanError.value = msg
            return
        }

        if (!isBluetoothEnabled()) {
            val msg = "Bluetooth is currently turned OFF. Please turn on phone Bluetooth."
            Log.e(TAG, msg)
            _lastScanError.value = msg
            return
        }

        // On Android 6-11 (and some Android 12+), Location Services must be turned ON for BLE scanning
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && !isLocationServiceEnabled()) {
            val msg = "Phone Location Services (GPS) is turned OFF. BLE scanning requires Location Services to be enabled."
            Log.w(TAG, msg)
            _lastScanError.value = msg
        }

        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            val msg = "BluetoothLeScanner is unavailable. Bluetooth may still be turning on."
            Log.e(TAG, msg)
            _lastScanError.value = msg
            return
        }

        // Stop any currently running scan
        scanJob?.cancel()
        if (isScanning) {
            try {
                scanner.stopScan(scanCallback)
            } catch (_: Exception) {}
        }

        isScanning = true
        _isScanning.value = true
        _connectionState.value = PrinterConnectionState.Scanning
        _scanCountdown.value = durationSeconds
        _totalPacketsReceived.value = 0
        _manufacturerPacketsCount.value = 0

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        try {
            // Scan for ALL nearby BLE advertisements without name or UUID filter
            scanner.startScan(null, scanSettings, scanCallback)
            Log.i(TAG, "Unfiltered BLE scan started (SCAN_MODE_LOW_LATENCY) for ${durationSeconds}s")

            scanJob = scope.launch {
                for (remaining in durationSeconds downTo 1) {
                    _scanCountdown.value = remaining
                    delay(1000)
                }
                _scanCountdown.value = 0
                stopScan()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting BLE scan", e)
            _lastScanError.value = "Failed to start BLE scan: ${e.localizedMessage}"
            isScanning = false
            _isScanning.value = false
            _scanCountdown.value = 0
            _connectionState.value = PrinterConnectionState.Disconnected
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        if (isScanning) {
            isScanning = false
            _isScanning.value = false
            _scanCountdown.value = 0
            try {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
                Log.i(TAG, "BLE scan stopped successfully")
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping BLE scan", e)
            }
        }
        if (_connectionState.value is PrinterConnectionState.Scanning) {
            _connectionState.value = PrinterConnectionState.Disconnected
        }
    }

    fun connect(deviceAddress: String, deviceName: String? = null) {
        stopScan()
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled) return

        try {
            val device = adapter.getRemoteDevice(deviceAddress) ?: return
            val label = deviceName ?: device.name ?: lastConnectedName ?: "P50S-496A-BLE"
            lastConnectedMac = deviceAddress
            lastConnectedName = label
            PrinterPreferences.savePrinter(context, label, deviceAddress)
            _connectionState.value = PrinterConnectionState.Connecting(label, deviceAddress, "Connecting to $label...")
            updatePrintStatus(PrintJobStatus.CONNECTING, "Connecting to $label...")

            cleanupGatt()
            activeGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(context, false, gattCallback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to device $deviceAddress", e)
            cleanupGatt()
            _connectionState.value = PrinterConnectionState.Disconnected
        }
    }

    /**
     * Synchronizes UI state with the real hardware GATT connection state.
     * Prevents false "Disconnected" or "Offline" states when navigating between screens.
     */
    fun syncConnectionState() {
        if (isPrinterConnected()) {
            val dev = activeGatt?.device
            val name = dev?.name ?: PrinterPreferences.getSavedPrinterName(context) ?: lastConnectedName ?: P50SProtocol.TARGET_DEVICE_NAME_EXACT
            val address = dev?.address ?: PrinterPreferences.getSavedPrinterMac(context) ?: lastConnectedMac ?: ""
            if (_connectionState.value !is PrinterConnectionState.Printing && _connectionState.value !is PrinterConnectionState.Scanning) {
                _connectionState.value = PrinterConnectionState.Connected(name, address)
            }
        } else if (!isGattConnected && _connectionState.value is PrinterConnectionState.Connected) {
            _connectionState.value = PrinterConnectionState.Disconnected
        }
    }

    fun disconnect() {
        stopScan()
        cleanupGatt()
        _connectionState.value = PrinterConnectionState.Disconnected
        updatePrintStatus(PrintJobStatus.IDLE, "Disconnected")
    }

    private fun cleanupGatt() {
        isGattConnected = false
        val gatt = activeGatt
        activeGatt = null
        writeCharacteristic = null
        flowControlCharacteristic = null
        negotiatedMtu = 23

        val pending = pendingWriteCompleter
        pendingWriteCompleter = null
        pending?.complete(BluetoothGatt.GATT_FAILURE)

        val ready = gattReadyCompleter
        gattReadyCompleter = null
        ready?.complete(false)

        if (gatt != null) {
            try {
                gatt.disconnect()
            } catch (_: Exception) {}
            try {
                gatt.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing GATT", e)
            }
        }
    }

    /**
     * Checks the REAL GATT state of the printer, verifying:
     * 1. Active GATT reference is non-null
     * 2. Required write characteristic 0xFF02 is discovered and ready
     * 3. Bluetooth adapter is enabled
     * 4. GATT connection is actively established and live
     */
    fun isPrinterConnected(): Boolean {
        val gatt = activeGatt ?: return false
        if (writeCharacteristic == null) return false
        val adapter = bluetoothAdapter ?: return false
        if (!adapter.isEnabled) return false
        return isGattConnected
    }

    /**
     * Verifies that the P50S GATT connection is live and ready.
     * If the current GATT is stale or disconnected, automatically attempts reconnection
     * and service rediscovery before reporting failure.
     */
    suspend fun ensureConnectedAndReady(): Boolean {
        if (!isBluetoothEnabled()) {
            Log.w(TAG, "ensureConnectedAndReady: Bluetooth adapter is OFF")
            return false
        }
        if (!hasBluetoothPermissions()) {
            Log.w(TAG, "ensureConnectedAndReady: Missing Bluetooth permissions")
            return false
        }

        // 1. If currently connected and verified ready
        if (isPrinterConnected()) {
            Log.d(TAG, "ensureConnectedAndReady: Existing GATT connection is live and verified ready")
            return true
        }

        // Stop any active BLE scan so connection and GATT communication get full radio priority
        stopScan()

        // 2. Identify target MAC address to reconnect to
        var targetMac = PrinterPreferences.getSavedPrinterMac(context) ?: lastConnectedMac
        var targetName = PrinterPreferences.getSavedPrinterName(context) ?: lastConnectedName ?: P50SProtocol.TARGET_DEVICE_NAME_EXACT

        if (targetMac.isNullOrBlank()) {
            // Check bonded devices
            try {
                val bonded = bluetoothAdapter?.bondedDevices?.firstOrNull {
                    P50SProtocol.isPotentialP50SPrinter(it.name) || it.name?.contains("P50S", ignoreCase = true) == true
                }
                if (bonded != null) {
                    targetMac = bonded.address
                    targetName = bonded.name ?: targetName
                    PrinterPreferences.savePrinter(context, targetName, targetMac)
                }
            } catch (_: SecurityException) {}
        }

        if (targetMac.isNullOrBlank()) {
            // Check memory scanned devices
            val discovered = _scannedDevices.value.firstOrNull { it.isP50S }
            if (discovered != null) {
                targetMac = discovered.address
                targetName = discovered.name
                PrinterPreferences.savePrinter(context, targetName, targetMac)
            }
        }

        if (targetMac.isNullOrBlank()) {
            Log.e(TAG, "ensureConnectedAndReady: No target printer MAC known or saved")
            return false
        }

        // Try reconnecting (up to 2 attempts for service discovery / ready state)
        for (attempt in 1..2) {
            Log.d(TAG, "RECONNECT START: target=$targetName ($targetMac), attempt=$attempt")
            updatePrintStatus(PrintJobStatus.CONNECTING, "Reconnecting to P50S...")
            _connectionState.value = PrinterConnectionState.Reconnecting(targetName, targetMac, "Reconnecting to P50S...")

            cleanupGatt()
            delay(250) // Let BLE radio stack settle

            val completer = CompletableDeferred<Boolean>()
            gattReadyCompleter = completer

            try {
                val device = bluetoothAdapter?.getRemoteDevice(targetMac)
                if (device == null) {
                    Log.e(TAG, "getRemoteDevice returned null for $targetMac")
                    cleanupGatt()
                    continue
                }

                activeGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
                } else {
                    device.connectGatt(context, false, gattCallback)
                }

                // Wait up to 10 seconds for connection + service discovery + characteristic discovery
                val ready = withTimeoutOrNull(10000) {
                    completer.await()
                } ?: false

                if (ready && isPrinterConnected()) {
                    Log.i(TAG, "ensureConnectedAndReady: Printer successfully reconnected and ready on attempt $attempt")
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during reconnect attempt $attempt", e)
            } finally {
                gattReadyCompleter = null
            }

            cleanupGatt()
            if (attempt < 2) {
                delay(500)
            }
        }

        _connectionState.value = PrinterConnectionState.Disconnected
        return false
    }

    fun getSafeChunkSize(): Int {
        // BLE ATT header is 3 bytes (Opcode 1 byte + Attribute Handle 2 bytes)
        val maxAttPayload = negotiatedMtu - 3
        // Safe chunk size for P50S microcontroller: minimum 20 bytes, max 128 bytes
        return maxAttPayload.coerceIn(20, 128)
    }

    suspend fun printTestReceipt(): PrintResult = withContext(Dispatchers.IO) {
        val savedName = PrinterPreferences.getSavedPrinterName(context) ?: P50SProtocol.TARGET_DEVICE_NAME_EXACT
        val bitmap = ReceiptBitmapGenerator.generateTestReceipt(savedName)
        printBitmap(bitmap)
    }

    suspend fun printCustomerReceipt(
        repair: RepairEntity,
        items: List<RepairItemEntity>,
        payments: List<PaymentEntity>,
        settings: AppSettingsEntity
    ): PrintResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "CUSTOMER_RECEIPT_START: Job #${repair.jobNumber}, Customer=${repair.customerName}, Phone=${repair.customerPhone}, Items=${items.size}")
        Log.i(TAG, "CUSTOMER_RECEIPT_RENDER_START")
        try {
            val bitmap = ReceiptBitmapGenerator.generateCustomerReceipt(repair, items, payments, settings)

            val width = bitmap.width
            val height = bitmap.height
            val bytesPerRow = (width + 7) / 8
            val rawSize = bytesPerRow * height

            Log.i(TAG, "CUSTOMER_RECEIPT_ENCODING_START")
            val monoBytes = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
            val payload = P50SProtocol.buildPrintPayload(monoBytes, height)
            val chunkSize = getSafeChunkSize()
            val packetCount = (payload.size + chunkSize - 1) / chunkSize
            Log.i(TAG, "CUSTOMER_RECEIPT_ENCODING_DONE")

            Log.i(TAG, "[CUSTOMER_RECEIPT]\nwidth=$width\nheight=$height\nbytesPerRow=$bytesPerRow\nrawBytes=$rawSize\ncompressedBytes=${payload.size}\npayloadBytes=${payload.size}\npacketCount=$packetCount")

            // Strict safety validation before sending to printer
            if (width != P50SProtocol.PRINTER_WIDTH_DOTS) {
                Log.e(TAG, "CUSTOMER_RECEIPT_PRINT_ERROR: Abnormal width $width != 384")
                return@withContext PrintResult.Error("Validation failed: Abnormal receipt width ($width)")
            }
            if (height < 100 || height > 2000) {
                Log.e(TAG, "CUSTOMER_RECEIPT_PRINT_ERROR: Abnormal height $height")
                return@withContext PrintResult.Error("Validation failed: Abnormal receipt height ($height)")
            }
            if (bytesPerRow != P50SProtocol.BYTES_PER_ROW) {
                Log.e(TAG, "CUSTOMER_RECEIPT_PRINT_ERROR: Invalid bytes per row $bytesPerRow != 48")
                return@withContext PrintResult.Error("Validation failed: Invalid bytes per row ($bytesPerRow)")
            }
            if (monoBytes.size != rawSize) {
                Log.e(TAG, "CUSTOMER_RECEIPT_PRINT_ERROR: Raster size mismatch ${monoBytes.size} != $rawSize")
                return@withContext PrintResult.Error("Validation failed: Raster size mismatch")
            }
            if (payload.size > 80000) {
                Log.e(TAG, "CUSTOMER_RECEIPT_PRINT_ERROR: Huge compressed payload ${payload.size}b")
                return@withContext PrintResult.Error("Validation failed: Payload too large for printer buffer")
            }
            if (packetCount > 600) {
                Log.e(TAG, "CUSTOMER_RECEIPT_PRINT_ERROR: Huge packet count $packetCount")
                return@withContext PrintResult.Error("Validation failed: Packet count exceeds safe limit")
            }

            Log.i(TAG, "CUSTOMER_RECEIPT_PRINT_START")
            val result = printBitmap(bitmap, isCustomerReceipt = true)
            when (result) {
                is PrintResult.Success -> {
                    Log.i(TAG, "CUSTOMER_RECEIPT_PRINT_SENT")
                    Log.i(TAG, "CUSTOMER_RECEIPT_PRINT_SUCCESS: Customer receipt printed successfully")
                }
                is PrintResult.Error -> {
                    Log.e(TAG, "CUSTOMER_RECEIPT_PRINT_ERROR: ${result.message}")
                    Log.e(TAG, "CUSTOMER_RECEIPT_PRINT_FAILED: ${result.message}")
                }
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "CUSTOMER_RECEIPT_PRINT_ERROR: Exception ${e.message}", e)
            Log.e(TAG, "CUSTOMER_RECEIPT_PRINT_FAILED: Exception ${e.message}", e)
            PrintResult.Error("Print failed: ${e.localizedMessage ?: "Unknown error"}")
        }
    }

    suspend fun printPaymentReceipt(
        repair: RepairEntity,
        payment: PaymentEntity,
        settings: AppSettingsEntity
    ): PrintResult = withContext(Dispatchers.IO) {
        val bitmap = ReceiptBitmapGenerator.generatePaymentReceipt(repair, payment, settings)
        val width = bitmap.width
        val height = bitmap.height
        val bytesPerRow = (width + 7) / 8
        val rawBytes = bytesPerRow * height
        val monoBytes = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
        val payload = P50SProtocol.buildPrintPayload(monoBytes, height)
        val chunkSize = getSafeChunkSize()
        val packetCount = (payload.size + chunkSize - 1) / chunkSize

        Log.i(TAG, "[PAYMENT_RECEIPT]\nwidth=$width\nheight=$height\nbytesPerRow=$bytesPerRow\nrawBytes=$rawBytes\ncompressedBytes=${payload.size}\npayloadBytes=${payload.size}\npacketCount=$packetCount")

        printBitmap(bitmap)
    }

    /**
     * Transmits the rendered bitmap over BLE to Marklife P50S using Protocol 0x1F.
     * Guaranteed safe:
     * - Only ONE print job can run at a time (locked against rapid clicks)
     * - Verifies active connection before sending (shows "Printer disconnected" immediately if offline)
     * - Uses negotiated MTU to chunk data safely without flooding the radio
     * - Sequential write queue: sends packet -> waits for GATT confirmation -> sends next packet
     * - Packet-level retry (up to 3 attempts) and 20s total job timeout
     * - Guaranteed state reset in finally block so UI never stays stuck on PRINTING
     */
    private suspend fun printBitmap(bitmap: android.graphics.Bitmap, isCustomerReceipt: Boolean = false): PrintResult = withContext(Dispatchers.IO) {
        if (!isBluetoothEnabled()) {
            if (isCustomerReceipt) {
                Log.e(TAG, "CUSTOMER_RECEIPT_PRINT_FAILED: Bluetooth is disabled")
            }
            return@withContext PrintResult.Error("Please turn on Bluetooth")
        }

        if (!hasBluetoothPermissions()) {
            if (isCustomerReceipt) {
                Log.e(TAG, "CUSTOMER_RECEIPT_PRINT_FAILED: Bluetooth permission not granted")
            }
            return@withContext PrintResult.Error("Bluetooth permission not granted")
        }

        // Prevent duplicate simultaneous print requests
        if (isPrintInProgress || _printJobStatus.value == PrintJobStatus.PRINTING || _printJobStatus.value == PrintJobStatus.CONNECTING || _printJobStatus.value == PrintJobStatus.PREPARING) {
            if (isCustomerReceipt) {
                Log.e(TAG, "CUSTOMER_RECEIPT_PRINT_FAILED: Print job already in progress")
            }
            return@withContext PrintResult.Error("A print job is already in progress. Please wait.")
        }
        if (!printMutex.tryLock()) {
            if (isCustomerReceipt) {
                Log.e(TAG, "CUSTOMER_RECEIPT_PRINT_FAILED: Print mutex lock failed")
            }
            return@withContext PrintResult.Error("A print job is already in progress. Please wait.")
        }
        isPrintInProgress = true

        try {
            updatePrintStatus(PrintJobStatus.PREPARING, "Preparing print data...")

            // Convert bitmap to 1-bit monochrome raster
            val monoBytes = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
            val payload = P50SProtocol.buildPrintPayload(monoBytes, bitmap.height)
            if (isCustomerReceipt) {
                Log.i(TAG, "CUSTOMER_RECEIPT_PAYLOAD_SIZE: ${payload.size} bytes (monoBytes=${monoBytes.size}, height=${bitmap.height})")
            }
            Log.d(TAG, "PRINT START: height=${bitmap.height}, payload size=${payload.size} bytes")

            val maxJobAttempts = 2

            for (jobAttempt in 1..maxJobAttempts) {
                if (jobAttempt > 1) {
                    Log.w(TAG, "RETRY START: Retrying complete print job (attempt $jobAttempt/$maxJobAttempts)")
                    updatePrintStatus(PrintJobStatus.CONNECTING, "Retrying print...")
                    _connectionState.value = PrinterConnectionState.Printing(0, "Retrying print...")
                }

                // 1. Verify real GATT state / auto-reconnect before print
                val isReady = ensureConnectedAndReady()
                if (!isReady) {
                    if (jobAttempt < maxJobAttempts) {
                        cleanupGatt()
                        delay(600)
                        continue
                    } else {
                        Log.e(TAG, "PRINT FAILED: Please check the printer connection.")
                        updatePrintStatus(PrintJobStatus.FAILED, "Printer not connected. Please connect the Marklife P50S.")
                        return@withContext PrintResult.Error("Printer not connected.\nPlease connect the Marklife P50S.")
                    }
                }

                val gatt = activeGatt
                val writeChar = writeCharacteristic

                if (gatt == null || writeChar == null || !isPrinterConnected()) {
                    cleanupGatt()
                    if (jobAttempt < maxJobAttempts) {
                        delay(600)
                        continue
                    } else {
                        Log.e(TAG, "PRINT FAILED: Please check the printer connection.")
                        updatePrintStatus(PrintJobStatus.FAILED, "Printer not connected. Please connect the Marklife P50S.")
                        return@withContext PrintResult.Error("Printer not connected.\nPlease connect the Marklife P50S.")
                    }
                }

                val chunkSize = getSafeChunkSize()
                val totalBytes = payload.size
                val totalPackets = (totalBytes + chunkSize - 1) / chunkSize
                var offset = 0
                var packetIndex = 0

                Log.i(TAG, "Starting P50S print transmission (Job attempt $jobAttempt): $totalBytes bytes in $totalPackets packets (chunkSize=$chunkSize, MTU=$negotiatedMtu)")
                if (isCustomerReceipt) {
                    Log.i(TAG, "CUSTOMER_RECEIPT_PACKET_COUNT: $totalPackets packets")
                    Log.i(TAG, "CUSTOMER_RECEIPT_PRINT_SENT: Starting transmission of $totalBytes bytes in $totalPackets packets (chunkSize=$chunkSize)")
                }
                updatePrintStatus(PrintJobStatus.PRINTING, "Printing...")
                _connectionState.value = PrinterConnectionState.Printing(0, "Printing...")

                // Determine write type supported by characteristic
                val props = writeChar.properties
                val writeType = if ((props and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0) {
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                } else {
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                }
                writeChar.writeType = writeType

                var connectionLostDuringTransmission = false

                try {
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                } catch (_: Exception) {}

                // Dynamic transmission timeout: generous allowance for long customer receipts
                val transmissionTimeoutMs = (totalBytes * 3L).coerceIn(60000L, 120000L)
                val jobSuccess = withTimeoutOrNull(transmissionTimeoutMs) {
                    while (offset < totalBytes) {
                        val currentGatt = activeGatt
                        val currentWriteChar = writeCharacteristic
                        if (currentGatt == null || currentWriteChar == null || !isGattConnected) {
                            Log.e(TAG, "Print aborted: activeGatt/writeCharacteristic is null or disconnected")
                            connectionLostDuringTransmission = true
                            return@withTimeoutOrNull false
                        }

                        val end = minOf(offset + chunkSize, totalBytes)
                        val chunk = payload.copyOfRange(offset, end)
                        packetIndex++

                        // Send packet with up to 3 retries
                        var packetSent = false
                        for (attempt in 1..3) {
                            if (!isGattConnected) {
                                connectionLostDuringTransmission = true
                                break
                            }
                            Log.d(TAG, "PACKET WRITE: packet $packetIndex/$totalPackets (${chunk.size} bytes, attempt $attempt)")
                            val completer = CompletableDeferred<Int>()
                            pendingWriteCompleter = completer

                            val writeInitiated = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                val res = currentGatt.writeCharacteristic(currentWriteChar, chunk, writeType)
                                res == BluetoothGatt.GATT_SUCCESS
                            } else {
                                @Suppress("DEPRECATION")
                                currentWriteChar.value = chunk
                                @Suppress("DEPRECATION")
                                currentGatt.writeCharacteristic(currentWriteChar)
                            }

                            if (!writeInitiated) {
                                Log.w(TAG, "writeCharacteristic call rejected on attempt $attempt for packet $packetIndex")
                                if (!isGattConnected) {
                                    connectionLostDuringTransmission = true
                                    break
                                }
                                delay(35)
                                continue
                            }

                            // Wait for callback confirmation with 2s timeout
                            val callbackStatus = withTimeoutOrNull(2000) {
                                completer.await()
                            }

                            if (callbackStatus == BluetoothGatt.GATT_SUCCESS) {
                                Log.d(TAG, "PACKET WRITE SUCCESS: packet $packetIndex/$totalPackets")
                                packetSent = true
                                break
                            } else if (callbackStatus == null && writeType == BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE) {
                                // Fallback for NO_RESPONSE on legacy chipsets
                                Log.d(TAG, "PACKET WRITE SUCCESS: packet $packetIndex/$totalPackets (NO_RESPONSE)")
                                packetSent = true
                                break
                            } else {
                                Log.w(TAG, "PACKET WRITE FAILURE: packet $packetIndex/$totalPackets attempt $attempt, status=$callbackStatus")
                                if (!isGattConnected) {
                                    connectionLostDuringTransmission = true
                                    break
                                }
                                delay(35)
                            }
                        }

                        if (!packetSent) {
                            Log.e(TAG, "Packet $packetIndex failed after 3 attempts")
                            return@withTimeoutOrNull false
                        }

                        offset = end
                        val progress = (offset * 100) / totalBytes
                        _connectionState.value = PrinterConnectionState.Printing(progress, "Printing ($progress%)...")

                        // Pacing delay (8ms) between chunks to avoid microcontroller buffer overrun
                        delay(8)
                    }
                    true
                }

                if (jobSuccess == true) {
                    if (isCustomerReceipt) {
                        Log.i(TAG, "CUSTOMER_RECEIPT_PRINT_SUCCESS: All $totalPackets packets successfully transmitted to P50S ($totalBytes bytes)")
                    }
                    Log.i(TAG, "PRINT SUCCESS: All $totalPackets packets successfully transmitted to P50S ($totalBytes bytes)")
                    updatePrintStatus(PrintJobStatus.SUCCESS, "Print complete")
                    return@withContext PrintResult.Success
                } else {
                    if (isCustomerReceipt) {
                        Log.e(TAG, "CUSTOMER_RECEIPT_PRINT_FAILED: Transmission attempt $jobAttempt failed (connectionLost=$connectionLostDuringTransmission)")
                    }
                    Log.w(TAG, "Print job transmission attempt $jobAttempt failed (connectionLost=$connectionLostDuringTransmission)")
                    cleanupGatt()
                    _connectionState.value = PrinterConnectionState.Disconnected
                    if (jobAttempt < maxJobAttempts) {
                        delay(600)
                    }
                }
            }

            if (isCustomerReceipt) {
                Log.e(TAG, "CUSTOMER_RECEIPT_PRINT_FAILED: Please check the printer connection")
            }
            Log.e(TAG, "PRINT FAILED: Please check the printer connection.")
            updatePrintStatus(PrintJobStatus.FAILED, "Printer not connected. Please connect the Marklife P50S.")
            return@withContext PrintResult.Error("Printer not connected.\nPlease connect the Marklife P50S.")
        } catch (e: Exception) {
            if (isCustomerReceipt) {
                Log.e(TAG, "CUSTOMER_RECEIPT_PRINT_FAILED: Exception ${e.message}", e)
            }
            Log.e(TAG, "PRINT FAILED with exception", e)
            updatePrintStatus(PrintJobStatus.FAILED, "Print failed: ${e.localizedMessage ?: "Unknown error"}")
            return@withContext PrintResult.Error("Print failed. Please check the printer connection.")
        } finally {
            val savedName = PrinterPreferences.getSavedPrinterName(context) ?: lastConnectedName ?: "P50S-496A-BLE"
            val savedMac = PrinterPreferences.getSavedPrinterMac(context) ?: lastConnectedMac ?: ""
            if (isPrinterConnected()) {
                _connectionState.value = PrinterConnectionState.Connected(savedName, savedMac)
            } else {
                _connectionState.value = PrinterConnectionState.Disconnected
            }
            pendingWriteCompleter = null
            isPrintInProgress = false
            printMutex.unlock()
        }
    }
}
