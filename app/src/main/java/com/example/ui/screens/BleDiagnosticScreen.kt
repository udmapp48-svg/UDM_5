package com.example.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.printer.DiscoveredDevice
import com.example.printer.PrintResult
import com.example.printer.PrinterConnectionState
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.PaymentPaid
import com.example.ui.theme.StatusCancelled
import com.example.ui.viewmodel.RepairViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleDiagnosticScreen(
    viewModel: RepairViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val scannedDevices by viewModel.scannedPrinters.collectAsState()
    val isScanning by viewModel.printerIsScanning.collectAsState()
    val countdown by viewModel.printerScanCountdown.collectAsState()
    val lastScanError by viewModel.printerLastScanError.collectAsState()
    val totalPackets by viewModel.printerTotalPackets.collectAsState()
    val manufacturerPackets by viewModel.printerManufacturerPackets.collectAsState()
    val connectionState by viewModel.printerConnectionState.collectAsState()

    var showTips by remember { mutableStateOf(true) }
    var showSysDetails by remember { mutableStateOf(false) }

    val isBtEnabled = viewModel.printerBleManager.isBluetoothEnabled()
    val isBleSupported = viewModel.printerBleManager.isBleSupported()
    val isBtAdapterAvailable = viewModel.printerBleManager.isBluetoothAdapterAvailable()
    val isLocationServiceEnabled = viewModel.printerBleManager.isLocationServiceEnabled()
    val missingPermissions = viewModel.getMissingPrinterPermissions()
    val hasAllPermissions = missingPermissions.isEmpty()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            Toast.makeText(context, "Permissions granted! Ready to scan.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Some permissions were denied. BLE scan may fail.", Toast.LENGTH_LONG).show()
        }
    }

    fun requestPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
        permissionLauncher.launch(permissions)
    }

    fun triggerScan() {
        if (!isBtEnabled) {
            Toast.makeText(context, "Please turn ON Bluetooth first", Toast.LENGTH_SHORT).show()
            try {
                context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } catch (_: Exception) {}
            return
        }

        if (!hasAllPermissions) {
            requestPermissions()
            return
        }

        viewModel.startPrinterScan(durationSeconds = 15)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("BLE Diagnostic Scan", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(
                            "Find P50S-496A-BLE • Hardware Debugger",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("diagnostic_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.clearScannedPrinters() },
                        modifier = Modifier.testTag("diagnostic_clear_btn")
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear List")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            // CONNECTED PRINTER STATUS & TEST PRINT
            if (connectionState is PrinterConnectionState.Connected) {
                val connectedDevice = connectionState as PrinterConnectionState.Connected
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PaymentPaid.copy(alpha = 0.1f)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, PaymentPaid, RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.BluetoothConnected, contentDescription = null, tint = PaymentPaid)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(text = "CONNECTED: ${connectedDevice.deviceName}", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = PaymentPaid)
                                        Text(text = connectedDevice.address, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { viewModel.disconnectPrinter() },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Disconnect", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            var isTestingDiagPrint by remember { mutableStateOf(false) }
                            val isPrintingDiag = isTestingDiagPrint || connectionState is PrinterConnectionState.Printing

                            Button(
                                onClick = {
                                    if (isPrintingDiag) return@Button
                                    isTestingDiagPrint = true
                                    viewModel.printTestReceipt { result ->
                                        isTestingDiagPrint = false
                                        coroutineScope.launch {
                                            when (result) {
                                                is PrintResult.Success -> {
                                                    snackbarHostState.showSnackbar("PRINT SENT ✓")
                                                }
                                                is PrintResult.Error -> {
                                                    snackbarHostState.showSnackbar(result.message)
                                                }
                                            }
                                        }
                                    }
                                },
                                enabled = !isPrintingDiag,
                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isPrintingDiag) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF090E1A))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("PRINTING...", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.Print, contentDescription = null, tint = Color(0xFF090E1A), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("TEST PRINT (RUN CONSECUTIVE PRINT TEST)", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // 1. LIVE SCAN ACTION CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isScanning) CyanAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (isScanning) CyanAccent else MaterialTheme.colorScheme.outline,
                            RoundedCornerShape(14.dp)
                        )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isScanning) "ACTIVE BLE SCAN IN PROGRESS" else "BLE DIAGNOSTIC SCANNER",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = if (isScanning) CyanAccent else MaterialTheme.colorScheme.onSurface,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isScanning) "Scanning all raw BLE advertisements ($countdown s remaining)..." else "Unfiltered 15-second low-latency BLE scan",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp,
                                    color = CyanAccent
                                )
                            }
                        }

                        if (isScanning) {
                            Spacer(modifier = Modifier.height(10.dp))
                            val progress = (15 - countdown) / 15f
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = CyanAccent,
                                trackColor = MaterialTheme.colorScheme.surface
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (isScanning) {
                                Button(
                                    onClick = { viewModel.stopPrinterScan() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("diagnostic_stop_btn")
                                ) {
                                    Text("Stop Scan", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Button(
                                    onClick = { triggerScan() },
                                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("diagnostic_start_btn")
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.BluetoothSearching,
                                        contentDescription = null,
                                        tint = Color(0xFF090E1A),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Start 15s Diagnostic Scan",
                                        color = Color(0xFF090E1A),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. ERROR BANNER (if any)
            if (lastScanError != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Scan Error Detected",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = lastScanError ?: "",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }

            // 3. IMPORTANT P50S PHYSICAL CHECKS (Section 7 from brief)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showTips = !showTips },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "P50S PHYSICAL PRINTER CHECKLIST",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = CyanAccent,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Icon(
                                imageVector = if (showTips) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(visible = showTips) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                Text(
                                    text = "Why Marklife P50S may not advertise over BLE:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "• Official Marklife App Active: If the official Marklife app is open or running in background, it holds the BLE connection and the printer stops advertising. Force-close Marklife.",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "• Printer Power Cycle: Turn the P50S power OFF, wait 3 seconds, and turn ON so it restarts BLE advertising broadcast.",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "• Location & Bluetooth: Android requires phone Bluetooth and Location Services to be active for nearby BLE discovery.",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 4. SYSTEM & BLE DIAGNOSTIC INFO (Section 10 from brief)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSysDetails = !showSysDetails },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = PaymentPaid, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ENVIRONMENT & SCAN TELEMETRY",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = PaymentPaid,
                                    letterSpacing = 0.5.sp
                                )
                            }
                            Icon(
                                imageVector = if (showSysDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        AnimatedVisibility(visible = showSysDetails) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                DiagnosticRow("Android OS", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                                DiagnosticRow("Bluetooth Adapter", if (isBtAdapterAvailable) "Available" else "Not Found", ok = isBtAdapterAvailable)
                                DiagnosticRow("Bluetooth Enabled", if (isBtEnabled) "YES (ON)" else "NO (OFF)", ok = isBtEnabled)
                                DiagnosticRow("BLE Hardware", if (isBleSupported) "Supported" else "Unsupported", ok = isBleSupported)
                                DiagnosticRow("Location Services (GPS)", if (isLocationServiceEnabled) "ENABLED" else "DISABLED", ok = isLocationServiceEnabled)
                                DiagnosticRow(
                                    "Runtime Permissions",
                                    if (hasAllPermissions) "ALL GRANTED" else "MISSING: ${missingPermissions.map { it.substringAfterLast('.') }.joinToString()}",
                                    ok = hasAllPermissions
                                )
                                DiagnosticRow("Raw Packets Received", "$totalPackets packets")
                                DiagnosticRow("Manufacturer Packets", "$manufacturerPackets packets")
                                DiagnosticRow("Unique Devices", "${scannedDevices.size} discovered")
                                DiagnosticRow("P50S Target Matches", "${scannedDevices.count { it.isP50S }} found", ok = scannedDevices.any { it.isP50S })

                                if (!hasAllPermissions) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = { requestPermissions() },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Grant Required Permissions", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }

                                if (!isLocationServiceEnabled) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                            } catch (_: Exception) {}
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Open Location Settings (GPS)", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 5. LIVE DISCOVERED DEVICES HEADER
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DISCOVERED BLE DEVICES (${scannedDevices.size})",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )

                    if (isScanning) {
                        Text(
                            text = "Live updating...",
                            fontSize = 11.sp,
                            color = CyanAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 6. DEVICES LIST OR EMPTY STATE
            if (scannedDevices.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.BluetoothSearching,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (isScanning) "Actively listening for BLE advertisements..." else "No BLE devices discovered yet",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isScanning)
                                    "Packets received: $totalPackets. Make sure P50S is ON and Marklife app is closed."
                                else
                                    "Tap 'Start 15s Diagnostic Scan' above. All nearby BLE devices and raw packets will appear here.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    }
                }
            } else {
                items(scannedDevices) { device ->
                    DiagnosticDeviceCard(
                        device = device,
                        isConnected = (connectionState as? PrinterConnectionState.Connected)?.address == device.address,
                        onConnect = {
                            viewModel.connectPrinter(device.address, device.name)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Connecting to ${device.name} (${device.address})...")
                            }
                        },
                        onDisconnect = {
                            viewModel.disconnectPrinter()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DiagnosticRow(label: String, value: String, ok: Boolean? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = when (ok) {
                true -> PaymentPaid
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
fun DiagnosticDeviceCard(
    device: DiscoveredDevice,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (device.isP50S) CyanAccent.copy(alpha = 0.09f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (device.isP50S) 1.5.dp else 1.dp,
                color = if (isConnected) PaymentPaid else if (device.isP50S) CyanAccent else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp)
            )
            .testTag("diagnostic_device_${device.address}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Target P50S Badge
            if (device.isP50S) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "TARGET MATCH • MARKLIFE P50S PRINTER",
                        fontWeight = FontWeight.ExtraBold,
                        color = CyanAccent,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Primary Device Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Device Name (Show Unknown BLE Device if null/blank)
                    Text(
                        text = device.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (device.name == "Unknown BLE Device") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Address
                    Text(
                        text = device.address,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // RSSI and Device Type
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${device.rssi} dBm",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                device.rssi > -60 -> PaymentPaid
                                device.rssi > -80 -> CyanAccent
                                else -> MaterialTheme.colorScheme.error
                            }
                        )
                        Text(
                            text = " • Type: ${device.deviceType}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = " • ${device.packetCount} pkts",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    if (isConnected) {
                        OutlinedButton(
                            onClick = onDisconnect,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Disconnect", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = onConnect,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (device.isP50S) CyanAccent else MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Connect",
                                color = if (device.isP50S) Color(0xFF090E1A) else MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Raw Advertisement Details Toggle
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "Hide Raw Advertisement Data" else "View Raw BLE Advertisement Data",
                    fontSize = 11.sp,
                    color = CyanAccent,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = CyanAccent,
                    modifier = Modifier.size(16.dp)
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    if (device.serviceUuids.isNotEmpty()) {
                        Text("Advertised Service UUIDs:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        device.serviceUuids.forEach { uuid ->
                            Text(uuid, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (device.manufacturerDataHex.isNotEmpty()) {
                        Text("Manufacturer Specific Data:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(device.manufacturerDataHex, fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (device.rawBytesHex.isNotEmpty()) {
                        Text("Raw Advertisement Hex (${device.rawBytesHex.length / 2} bytes):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(device.rawBytesHex, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
