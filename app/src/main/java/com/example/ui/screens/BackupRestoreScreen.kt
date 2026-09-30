package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.PaymentPaid
import com.example.ui.theme.StatusCancelled
import com.example.ui.viewmodel.RepairViewModel
import com.example.util.BackupFileHelper
import com.example.util.BackupManager
import com.example.util.BackupSummary
import com.example.util.BackupValidationResult
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: RepairViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isCreatingBackup by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    val localBackupFiles = remember { mutableStateListOf<BackupFileHelper.BackupFileInfo>() }

    // Dialog state for newly created backup
    var createdBackupResult by remember { mutableStateOf<BackupFileHelper.BackupFileResult?>(null) }

    // Dialog state for confirming restore from File or Uri
    var pendingRestoreFile by remember { mutableStateOf<File?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var pendingRestoreSummary by remember { mutableStateOf<BackupSummary?>(null) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }

    fun refreshFileList() {
        viewModel.loadLocalBackupFiles(context) { list ->
            localBackupFiles.clear()
            localBackupFiles.addAll(list)
        }
    }

    LaunchedEffect(Unit) {
        refreshFileList()
    }

    // SAF File Picker for selecting a .udmbackup file from File Manager
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val content = BackupFileHelper.readFromUri(context, uri)
                    when (val validation = BackupManager.validateJson(content)) {
                        is BackupValidationResult.Valid -> {
                            pendingRestoreUri = uri
                            pendingRestoreFile = null
                            pendingRestoreSummary = validation.summary
                        }
                        is BackupValidationResult.Invalid -> {
                            snackbarHostState.showSnackbar("Invalid backup: ${validation.reason}")
                        }
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Failed to read file: ${e.localizedMessage}")
                }
            }
        }
    }

    // SAF Document Creator for saving backup to a user-chosen folder or SD card
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    viewModel.exportBackup { json ->
                        try {
                            BackupFileHelper.writeToUri(context, uri, json)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Backup saved to chosen storage location successfully!")
                            }
                            refreshFileList()
                        } catch (e: Exception) {
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Failed to write to selected file: ${e.localizedMessage}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("Backup creation failed: ${e.localizedMessage}")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Restore Files", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refreshFileList() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Backup Files", tint = CyanAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
            contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // FOLDER LOCATION INFO BANNER
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "DEDICATED BACKUP STORAGE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent
                            )
                            Text(
                                text = "UDM MOBILE REPAIR/Backups/",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Accessible in phone File Manager under Documents. Offline & safe.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 1. CREATE BACKUP SECTION
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "CREATE BACKUP FILE",
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Generates a complete offline .udmbackup file containing all repairs, customers, payments, prices, and shop settings inside UDM MOBILE REPAIR/Backups/.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                if (!isCreatingBackup) {
                                    isCreatingBackup = true
                                    viewModel.createBackupFile(context) { result ->
                                        isCreatingBackup = false
                                        if (result.success) {
                                            createdBackupResult = result
                                            refreshFileList()
                                        } else {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Backup failed: ${result.errorMessage}")
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !isCreatingBackup && !isRestoring,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("generate_backup_btn")
                        ) {
                            if (isCreatingBackup) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color(0xFF090E1A),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("CREATING BACKUP...", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.FileUpload, contentDescription = null, tint = Color(0xFF090E1A))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("CREATE BACKUP FILE", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                val suggestedName = BackupFileHelper.generateBackupFileName()
                                createDocumentLauncher.launch(suggestedName)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.SaveAlt, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save Backup to Custom Folder / SD Card...", color = CyanAccent, fontSize = 12.sp)
                        }
                    }
                }
            }

            // 2. RESTORE BACKUP FROM FILE MANAGER
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "RESTORE BACKUP FILE",
                            fontWeight = FontWeight.Bold,
                            color = PaymentPaid,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Select a .udmbackup file from your phone's File Manager (or another phone) to restore all repair jobs, payments, customers, and settings.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                openDocumentLauncher.launch(arrayOf("*/*", "application/*"))
                            },
                            enabled = !isRestoring && !isCreatingBackup,
                            colors = ButtonDefaults.buttonColors(containerColor = PaymentPaid),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("restore_file_manager_btn")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("SELECT FILE FROM FILE MANAGER", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3. LOCAL BACKUP FILES IN UDM MOBILE REPAIR/Backups
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BACKUP FILES IN STORAGE (${localBackupFiles.size})",
                        fontWeight = FontWeight.Bold,
                        color = CyanAccent,
                        fontSize = 12.sp
                    )
                    TextButton(onClick = { refreshFileList() }) {
                        Text("Refresh", fontSize = 12.sp, color = CyanAccent)
                    }
                }
            }

            if (localBackupFiles.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
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
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No backup files found yet",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Tap 'CREATE BACKUP FILE' above to generate your first backup in UDM MOBILE REPAIR/Backups/.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            } else {
                items(localBackupFiles) { backupInfo ->
                    BackupFileCard(
                        info = backupInfo,
                        onRestore = {
                            // Validate and open confirmation dialog
                            try {
                                val content = backupInfo.file.readText(Charsets.UTF_8)
                                when (val validation = BackupManager.validateJson(content)) {
                                    is BackupValidationResult.Valid -> {
                                        pendingRestoreFile = backupInfo.file
                                        pendingRestoreUri = null
                                        pendingRestoreSummary = validation.summary
                                    }
                                    is BackupValidationResult.Invalid -> {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Invalid file: ${validation.reason}")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Failed to read file: ${e.localizedMessage}")
                                }
                            }
                        },
                        onShare = {
                            BackupFileHelper.shareBackupFile(context, backupInfo.file)
                        },
                        onDelete = {
                            fileToDelete = backupInfo.file
                        }
                    )
                }
            }
        }
    }

    // DIALOG: Backup Created Successfully
    createdBackupResult?.let { result ->
        AlertDialog(
            onDismissRequest = { createdBackupResult = null },
            icon = {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PaymentPaid, modifier = Modifier.size(36.dp))
            },
            title = { Text("Backup File Created!", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "File successfully generated and saved to your phone's storage:",
                        fontSize = 13.sp
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = result.fileName,
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Folder: ${result.folderPath}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Size: ${result.formattedSize}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            result.summary?.let { s ->
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                Text(
                                    text = "Contains: ${s.repairCount} Repairs, ${s.customerCount} Customers, ${s.paymentCount} Payments, ${s.priceCount} Prices, Shop Settings",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = PaymentPaid
                                )
                            }
                        }
                    }

                    Text(
                        text = "You can view this file in your phone's File Manager or share it to another device for safekeeping.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        result.file?.let { BackupFileHelper.shareBackupFile(context, it) }
                        createdBackupResult = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFF090E1A), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share / Send File", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { createdBackupResult = null }) {
                    Text("Done")
                }
            }
        )
    }

    // DIALOG: Confirm Restore
    if (pendingRestoreSummary != null && (pendingRestoreFile != null || pendingRestoreUri != null)) {
        val summary = pendingRestoreSummary!!
        val fileName = pendingRestoreFile?.name ?: "Selected Backup File"

        AlertDialog(
            onDismissRequest = {
                pendingRestoreFile = null
                pendingRestoreUri = null
                pendingRestoreSummary = null
            },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(36.dp))
            },
            title = { Text("Confirm Database Restore", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Are you sure you want to restore data from this backup? Current database records will be replaced with this backup data.",
                        fontSize = 13.sp
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = fileName,
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "Exported: ${summary.formattedExportDate}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Text("• Repairs / Jobs: ${summary.repairCount}", fontSize = 11.sp)
                            Text("• Customers: ${summary.customerCount}", fontSize = 11.sp)
                            Text("• Payments: ${summary.paymentCount}", fontSize = 11.sp)
                            Text("• Model Prices: ${summary.priceCount}", fontSize = 11.sp)
                            Text("• Brands / Models: ${summary.brandCount} brands, ${summary.modelCount} models", fontSize = 11.sp)
                            Text("• Settings & SMS Templates: Included", fontSize = 11.sp)
                        }
                    }

                    Text(
                        text = "✓ 4-digit job numbers with leading zeros (e.g. 00125, 0001) will be preserved exactly.",
                        fontSize = 11.sp,
                        color = PaymentPaid,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val file = pendingRestoreFile
                        val uri = pendingRestoreUri
                        pendingRestoreFile = null
                        pendingRestoreUri = null
                        pendingRestoreSummary = null

                        isRestoring = true
                        if (file != null) {
                            viewModel.restoreFromFile(file) { success, msg, _ ->
                                isRestoring = false
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(msg)
                                    refreshFileList()
                                }
                            }
                        } else if (uri != null) {
                            viewModel.restoreFromUri(context, uri) { success, msg, _ ->
                                isRestoring = false
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(msg)
                                    refreshFileList()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PaymentPaid),
                    modifier = Modifier.testTag("confirm_restore_btn")
                ) {
                    Text("CONFIRM RESTORE", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingRestoreFile = null
                    pendingRestoreUri = null
                    pendingRestoreSummary = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // DIALOG: Confirm Delete Backup File
    fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text("Delete Backup File?") },
            text = {
                Text("Are you sure you want to delete ${file.name}? This file will be removed from your phone's storage.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteBackupFile(file) {
                            fileToDelete = null
                            refreshFileList()
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Backup file deleted.")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusCancelled)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun BackupFileCard(
    info: BackupFileHelper.BackupFileInfo,
    onRestore: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .testTag("backup_file_item")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = info.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = info.formattedSize,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = CyanAccent
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Modified: ${info.formattedDate} • ${info.folderDisplayName}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            info.summary?.let { s ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${s.repairCount} Jobs • ${s.customerCount} Customers • ${s.paymentCount} Payments • ${s.priceCount} Prices",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = PaymentPaid
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRestore,
                    colors = ButtonDefaults.buttonColors(containerColor = PaymentPaid),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(38.dp).testTag("restore_btn")
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restore", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onShare,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(38.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", color = CyanAccent, fontSize = 12.sp)
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Backup File",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
