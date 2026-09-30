package com.example.ui.screens

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.printer.P50SProtocol
import com.example.printer.PrintResult
import com.example.printer.PrinterConnectionState
import com.example.printer.PrinterPreferences
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.PaymentPaid
import com.example.ui.theme.PaymentPartial
import com.example.ui.viewmodel.RepairViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrinterSettingsScreen(
    viewModel: RepairViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDiagnostic: () -> Unit = {}
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val connectionState by viewModel.printerConnectionState.collectAsState()
    val printStatusMsg by viewModel.printStatusMessage.collectAsState()
    val scannedDevices by viewModel.scannedPrinters.collectAsState()
    val isAutoPrint by viewModel.isAutoPrintReceipt.collectAsState()

    // Sync UI with actual BLE connection state whenever screen appears
    LaunchedEffect(Unit) {
        viewModel.syncPrinterConnectionState()
    }

    var isTestingPrint by remember { mutableStateOf(false) }

    val savedPrinterMac = remember(connectionState) {
        PrinterPreferences.getSavedPrinterMac(context)
    }
    val savedPrinterName = remember(connectionState) {
        PrinterPreferences.getSavedPrinterName(context)
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantedMap ->
        val allGranted = grantedMap.values.all { it }
        if (allGranted) {
            viewModel.startPrinterScan()
        } else {
            Toast.makeText(context, "Bluetooth and Location permissions are required to scan for BLE printers.", Toast.LENGTH_SHORT).show()
        }
    }

    fun requestPermissionsAndScan() {
        if (!viewModel.printerBleManager.isBluetoothEnabled()) {
            Toast.makeText(context, "Please turn on Bluetooth", Toast.LENGTH_SHORT).show()
            try {
                context.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } catch (_: Exception) {}
            return
        }

        if (!viewModel.printerBleManager.hasBluetoothPermissions()) {
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
            permissionsLauncher.launch(permissions)
        } else {
            viewModel.startPrinterScan()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Printer Settings", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        val subText = when (connectionState) {
                            is PrinterConnectionState.Connected -> "Marklife P50S (BLE) • Connected"
                            is PrinterConnectionState.Connecting -> "Marklife P50S (BLE) • Connecting..."
                            is PrinterConnectionState.Reconnecting -> "Marklife P50S (BLE) • Reconnecting..."
                            is PrinterConnectionState.Printing -> "Marklife P50S (BLE) • Printing..."
                            is PrinterConnectionState.Scanning -> "Marklife P50S (BLE) • Scanning..."
                            is PrinterConnectionState.Error -> "Marklife P50S (BLE) • Error"
                            is PrinterConnectionState.Disconnected -> if (savedPrinterMac != null) "Marklife P50S (BLE) • Ready (Tap Connect)" else "Marklife P50S (BLE) • Disconnected"
                        }
                        Text(
                            text = subText,
                            fontSize = 12.sp,
                            color = when (connectionState) {
                                is PrinterConnectionState.Connected -> PaymentPaid
                                is PrinterConnectionState.Connecting,
                                is PrinterConnectionState.Reconnecting,
                                is PrinterConnectionState.Printing -> CyanAccent
                                is PrinterConnectionState.Error -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("printer_settings_back_btn")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { requestPermissionsAndScan() },
                        modifier = Modifier.testTag("printer_refresh_btn")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh / Scan")
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
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // STATUS CARD
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = when (connectionState) {
                            is PrinterConnectionState.Connected -> PaymentPaid.copy(alpha = 0.1f)
                            is PrinterConnectionState.Connecting,
                            is PrinterConnectionState.Reconnecting,
                            is PrinterConnectionState.Printing -> CyanAccent.copy(alpha = 0.1f)
                            is PrinterConnectionState.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            when (connectionState) {
                                is PrinterConnectionState.Connected -> PaymentPaid
                                is PrinterConnectionState.Connecting,
                                is PrinterConnectionState.Reconnecting,
                                is PrinterConnectionState.Printing -> CyanAccent
                                is PrinterConnectionState.Error -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.outline
                            },
                            RoundedCornerShape(14.dp)
                        )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when (connectionState) {
                                                is PrinterConnectionState.Connected -> PaymentPaid.copy(alpha = 0.2f)
                                                is PrinterConnectionState.Connecting,
                                                is PrinterConnectionState.Reconnecting,
                                                is PrinterConnectionState.Printing -> CyanAccent.copy(alpha = 0.2f)
                                                is PrinterConnectionState.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                                                else -> Color.Gray.copy(alpha = 0.2f)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when (connectionState) {
                                            is PrinterConnectionState.Connected -> Icons.Default.BluetoothConnected
                                            is PrinterConnectionState.Scanning,
                                            is PrinterConnectionState.Reconnecting -> Icons.AutoMirrored.Filled.BluetoothSearching
                                            else -> Icons.Default.Bluetooth
                                        },
                                        contentDescription = null,
                                        tint = when (connectionState) {
                                            is PrinterConnectionState.Connected -> PaymentPaid
                                            is PrinterConnectionState.Connecting,
                                            is PrinterConnectionState.Reconnecting,
                                            is PrinterConnectionState.Printing -> CyanAccent
                                            is PrinterConnectionState.Error -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Printer Status",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = when (val s = connectionState) {
                                            is PrinterConnectionState.Connected -> "Connected"
                                            is PrinterConnectionState.Connecting -> if (s.statusMessage.isNotBlank()) s.statusMessage else "Connecting to ${s.deviceName}..."
                                            is PrinterConnectionState.Reconnecting -> if (s.statusMessage.isNotBlank()) s.statusMessage else "Reconnecting to ${s.deviceName}..."
                                            is PrinterConnectionState.Scanning -> "Scanning for printers..."
                                            is PrinterConnectionState.Printing -> if (s.statusMessage.isNotBlank()) s.statusMessage else "Printing (${s.progressPercent}%)..."
                                            is PrinterConnectionState.Error -> "Error: ${s.message}"
                                            is PrinterConnectionState.Disconnected -> "Disconnected"
                                        },
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (connectionState) {
                                            is PrinterConnectionState.Connected -> PaymentPaid
                                            is PrinterConnectionState.Connecting,
                                            is PrinterConnectionState.Reconnecting,
                                            is PrinterConnectionState.Printing -> CyanAccent
                                            is PrinterConnectionState.Error -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                }
                            }

                            if (connectionState is PrinterConnectionState.Scanning ||
                                connectionState is PrinterConnectionState.Connecting ||
                                connectionState is PrinterConnectionState.Reconnecting ||
                                connectionState is PrinterConnectionState.Printing
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.5.dp,
                                    color = CyanAccent
                                )
                            }
                        }

                        // Connected or Saved printer details
                        when (val state = connectionState) {
                            is PrinterConnectionState.Connected -> {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = state.deviceName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = state.address,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.disconnectPrinter() },
                                            modifier = Modifier.testTag("printer_disconnect_btn"),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text("Disconnect", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                            is PrinterConnectionState.Disconnected -> {
                                if (savedPrinterMac != null) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "Saved: ${savedPrinterName ?: "Marklife P50S"}",
                                                    fontWeight = FontWeight.SemiBold,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = savedPrinterMac,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Button(
                                                onClick = { viewModel.connectPrinter(savedPrinterMac, savedPrinterName) },
                                                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                                                modifier = Modifier.testTag("printer_reconnect_btn"),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text("Connect", color = Color(0xFF090E1A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {}
                        }
                    }
                }
            }

            // BLUETOOTH STATUS WARNING IF DISABLED
            if (!viewModel.printerBleManager.isBluetoothEnabled()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.BluetoothDisabled, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Please turn on Bluetooth to connect with your Marklife P50S printer.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // BLE DIAGNOSTIC SCAN (Settings -> Printer -> BLE Diagnostic Scan)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyanAccent.copy(alpha = 0.08f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, CyanAccent, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(CyanAccent.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.BluetoothSearching,
                                        contentDescription = null,
                                        tint = CyanAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "BLE DIAGNOSTIC SCAN",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = CyanAccent,
                                        letterSpacing = 1.sp
                                    )
                                    Text(
                                        text = "Hardware discovery debugger for P50S-496A-BLE",
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Find why P50S-496A-BLE is not appearing. Inspect raw BLE advertisements, device types, MAC addresses, signal strength, and verify permissions.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onNavigateToDiagnostic,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("printer_diagnostic_scan_btn")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null, tint = Color(0xFF090E1A), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Open BLE Diagnostic Scan",
                                color = Color(0xFF090E1A),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // AUTO PRINT OPTION
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto Print Customer Receipt",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Automatically print receipt once when a new repair is saved",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAutoPrint,
                            onCheckedChange = { viewModel.setAutoPrintReceipt(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = CyanAccent),
                            modifier = Modifier.testTag("printer_auto_print_switch")
                        )
                    }
                }
            }

            // TEST PRINT BUTTON
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "TEST PRINT",
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Prints a test receipt on 384-dot continuous thermal paper (UDM MOBILE REPAIR • P50S TEST PRINT • Test successful).",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        val isPrintingNow = isTestingPrint || connectionState is PrinterConnectionState.Printing
                        Button(
                            onClick = {
                                if (isPrintingNow) return@Button
                                isTestingPrint = true
                                viewModel.printTestReceipt { result ->
                                    isTestingPrint = false
                                    coroutineScope.launch {
                                        when (result) {
                                            is PrintResult.Success -> {
                                                snackbarHostState.showSnackbar("PRINT SUCCESSFUL")
                                            }
                                            is PrintResult.Error -> {
                                                snackbarHostState.showSnackbar("PRINT FAILED\nPlease check the printer connection.")
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !isPrintingNow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("printer_test_print_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            if (isPrintingNow) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Color(0xFF090E1A)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                val msg = printStatusMsg.uppercase()
                                Text(
                                    text = if (msg.isNotBlank() && msg != "READY" && msg != "PRINT COMPLETE") msg else "PRINTING...",
                                    color = Color(0xFF090E1A),
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(Icons.Default.Print, contentDescription = null, tint = Color(0xFF090E1A), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "TEST PRINT",
                                    color = Color(0xFF090E1A),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // SCAN / DISCOVER BUTTON
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AVAILABLE BLE PRINTERS",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )

                    Button(
                        onClick = { requestPermissionsAndScan() },
                        enabled = connectionState !is PrinterConnectionState.Scanning,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("printer_scan_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.BluetoothSearching, contentDescription = null, tint = Color(0xFF090E1A), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (connectionState is PrinterConnectionState.Scanning) "Scanning..." else "Scan Printers",
                            color = Color(0xFF090E1A),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // LIST OF SCANNED PRINTERS
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
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (connectionState is PrinterConnectionState.Scanning) "Searching for nearby Bluetooth printers..." else "No BLE printers found yet",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Turn on your Marklife P50S (P50S-496A-BLE) and tap 'Scan Printers'.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } else {
                items(scannedDevices) { device ->
                    DeviceItemCard(
                        device = device,
                        isConnected = (connectionState as? PrinterConnectionState.Connected)?.address == device.address,
                        onConnect = { viewModel.connectPrinter(device.address, device.name) },
                        onDisconnect = { viewModel.disconnectPrinter() }
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceItemCard(
    device: DiscoveredDevice,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (device.isP50S) CyanAccent.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (device.isP50S) 1.5.dp else 1.dp,
                color = if (isConnected) PaymentPaid else if (device.isP50S) CyanAccent else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp)
            )
            .testTag("device_card_${device.address}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                        text = "RECOMMENDED • MARKLIFE P50S",
                        fontWeight = FontWeight.ExtraBold,
                        color = CyanAccent,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = device.address,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Signal: ${device.rssi} dBm • Type: ${device.deviceType}",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isConnected) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("device_disconnect_${device.address}")
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
                        modifier = Modifier.testTag("device_connect_${device.address}")
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
    }
}
