package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.PaymentPaid
import com.example.ui.theme.StatusCancelled
import com.example.ui.viewmodel.RepairViewModel
import com.example.util.ImageOcrPreprocessor
import com.example.util.JobNumberOcrParser
import com.example.util.OcrScanResult
import com.example.util.ScannerAudioHelper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun OcrScannerScreen(
    viewModel: RepairViewModel,
    onNavigateBack: () -> Unit,
    onRepairFound: (Long) -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val learnedRecords by viewModel.scannerLearningRecords.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var detectedJobNumber by remember { mutableStateOf("") }
    var isScanningActive by remember { mutableStateOf(true) }
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var showUnclearDialog by remember { mutableStateOf(false) }
    var notFoundDialogVisible by remember { mutableStateOf(false) }
    var isSearching by remember { mutableStateOf(false) }

    // Adaptive Learning Context Metadata (Small in-memory strings only, NO photo storage)
    var lastRawOcrText by remember { mutableStateOf("") }
    var lastDetectedProposedNumber by remember { mutableStateOf("") }
    var lastPreprocessingMethod by remember { mutableStateOf("RAW") }
    var lastConfidence by remember { mutableStateOf(0f) }
    var wasUnclearScan by remember { mutableStateOf(false) }

    val isProcessingFrame = remember { AtomicBoolean(false) }
    val audioHelper = remember { ScannerAudioHelper(context) }

    val textRecognizer = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            textRecognizer.close()
            audioHelper.release()
        }
    }

    fun restartScanning() {
        detectedJobNumber = ""
        isScanningActive = true
        showConfirmationDialog = false
        showUnclearDialog = false
        notFoundDialogVisible = false
        isSearching = false
        wasUnclearScan = false
        isProcessingFrame.set(false)
    }

    fun handleSuccessfulDetection(result: OcrScanResult, methodUsed: String) {
        if (!isScanningActive || showConfirmationDialog || showUnclearDialog || notFoundDialogVisible) return
        isScanningActive = false
        detectedJobNumber = result.jobNumber
        lastDetectedProposedNumber = result.jobNumber
        lastRawOcrText = result.rawMatchedText.ifEmpty { "+ ${result.jobNumber}" }
        lastPreprocessingMethod = methodUsed
        lastConfidence = result.confidence
        wasUnclearScan = false

        audioHelper.playConfirmationBeep()
        showConfirmationDialog = true
    }

    fun handleUnclearDetection(rawText: String = "", methodUsed: String = "MULTI_PASS") {
        if (!isScanningActive || showConfirmationDialog || showUnclearDialog || notFoundDialogVisible) return
        isScanningActive = false
        wasUnclearScan = true
        lastRawOcrText = rawText
        lastPreprocessingMethod = methodUsed
        lastConfidence = 0.2f
        showUnclearDialog = true
    }

    fun executeOpenJob(numberToOpen: String, isManualInput: Boolean = false) {
        val trimmed = numberToOpen.trim()
        if (trimmed.isBlank()) return

        // ADAPTIVE LEARNING: Save small learning record if user manually corrected or resolved an unclear/incorrect scan
        if (isManualInput || wasUnclearScan || (lastDetectedProposedNumber.isNotEmpty() && lastDetectedProposedNumber != trimmed)) {
            val rawToLearn = if (lastRawOcrText.isNotBlank()) lastRawOcrText else "+ $trimmed"
            viewModel.recordScannerCorrection(
                rawOcrText = rawToLearn,
                confirmedJobNumber = trimmed,
                detectedJobNumber = lastDetectedProposedNumber,
                confidence = lastConfidence,
                preprocessingMethod = lastPreprocessingMethod
            )
        }

        isSearching = true
        coroutineScope.launch {
            try {
                // Strictly preserves leading zeros in search
                val repair = viewModel.findRepairByJobNumber(trimmed)
                isSearching = false
                if (repair != null) {
                    showConfirmationDialog = false
                    notFoundDialogVisible = false
                    onRepairFound(repair.id)
                } else {
                    showConfirmationDialog = false
                    notFoundDialogVisible = true
                }
            } catch (_: Exception) {
                isSearching = false
                showConfirmationDialog = false
                notFoundDialogVisible = true
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090E1A))
    ) {
        // CAMERA PREVIEW
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val rotation = previewView.display?.rotation ?: android.view.Surface.ROTATION_0
                        val preview = Preview.Builder()
                            .setTargetRotation(rotation)
                            .build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                        @OptIn(ExperimentalGetImage::class)
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetRotation(rotation)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null && isScanningActive && !showConfirmationDialog && !showUnclearDialog && !notFoundDialogVisible && !isSearching) {
                                        if (isProcessingFrame.compareAndSet(false, true)) {
                                            val rotationDeg = imageProxy.imageInfo.rotationDegrees

                                            // PASS 1: Fast direct frame recognition
                                            val inputImage = InputImage.fromMediaImage(mediaImage, rotationDeg)
                                            textRecognizer.process(inputImage)
                                                .addOnSuccessListener { visionText ->
                                                    val pass1Result = JobNumberOcrParser.extractJobNumberFromVisionTextWithResult(
                                                        visionText,
                                                        learnedRecords
                                                    )

                                                    if (pass1Result != null && pass1Result.hasPlusAnchor && !pass1Result.isUnclear && pass1Result.jobNumber.isNotBlank() && pass1Result.confidence >= 0.90f) {
                                                        // Fast High-confidence '+' match found on Pass 1
                                                        handleSuccessfulDetection(pass1Result, "RAW_FRAME")
                                                    } else {
                                                        // PASS 2 & 3: Multi-Pass Preprocessing in memory (Crop, Enhance, Sharpen, Adaptive Threshold)
                                                        try {
                                                            val fullBitmap = ImageOcrPreprocessor.imageProxyToBitmap(imageProxy)
                                                            if (fullBitmap != null) {
                                                                val cropped = ImageOcrPreprocessor.cropToReticle(fullBitmap)
                                                                val upscaled = ImageOcrPreprocessor.upscale(cropped, 2.0f)
                                                                val contrastEnhanced = ImageOcrPreprocessor.enhanceGrayscaleAndContrast(upscaled, 1.8f, -15f)
                                                                val sharpened = ImageOcrPreprocessor.sharpen(contrastEnhanced)

                                                                val pass2Image = InputImage.fromBitmap(sharpened, 0)
                                                                textRecognizer.process(pass2Image)
                                                                    .addOnSuccessListener { p2VisionText ->
                                                                        val pass2Result = JobNumberOcrParser.extractJobNumberFromVisionTextWithResult(
                                                                            p2VisionText,
                                                                            learnedRecords
                                                                        )
                                                                        val best = JobNumberOcrParser.selectBestResult(pass1Result, pass2Result)

                                                                        if (best != null && !best.isUnclear && best.jobNumber.isNotBlank() && best.confidence >= 0.80f) {
                                                                            handleSuccessfulDetection(best, "CONTRAST_SHARPENED")
                                                                        } else {
                                                                            // PASS 3: Adaptive local threshold (ideal for low light, shadows, or reflective glare)
                                                                            val adaptiveBin = ImageOcrPreprocessor.adaptiveThreshold(sharpened, 32, 7)
                                                                            val pass3Image = InputImage.fromBitmap(adaptiveBin, 0)
                                                                            textRecognizer.process(pass3Image)
                                                                                .addOnSuccessListener { p3VisionText ->
                                                                                    val pass3Result = JobNumberOcrParser.extractJobNumberFromVisionTextWithResult(
                                                                                        p3VisionText,
                                                                                        learnedRecords
                                                                                    )
                                                                                    val finalBest = JobNumberOcrParser.selectBestResult(best, pass3Result)

                                                                                    if (finalBest != null) {
                                                                                        if (finalBest.isUnclear) {
                                                                                            handleUnclearDetection(finalBest.rawMatchedText, "ADAPTIVE_THRESHOLD")
                                                                                        } else if (finalBest.jobNumber.isNotBlank()) {
                                                                                            handleSuccessfulDetection(finalBest, "ADAPTIVE_THRESHOLD")
                                                                                        }
                                                                                    }
                                                                                }
                                                                        }
                                                                    }
                                                            } else if (pass1Result != null && !pass1Result.isUnclear && pass1Result.jobNumber.isNotBlank()) {
                                                                handleSuccessfulDetection(pass1Result, "RAW_FRAME")
                                                            }
                                                        } catch (e: Exception) {
                                                            if (pass1Result != null && !pass1Result.isUnclear && pass1Result.jobNumber.isNotBlank()) {
                                                                handleSuccessfulDetection(pass1Result, "RAW_FRAME")
                                                            }
                                                        }
                                                    }
                                                }
                                                .addOnCompleteListener {
                                                    isProcessingFrame.set(false)
                                                    imageProxy.close()
                                                }
                                        } else {
                                            imageProxy.close()
                                        }
                                    } else {
                                        imageProxy.close()
                                    }
                                }
                            }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalysis
                            )
                        } catch (e: Exception) {
                            Log.e("OcrScanner", "Camera bind error", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF090E1A)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(20.dp)) {
                    Icon(
                        Icons.Default.DocumentScanner,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        "Camera permission is required to scan '+' repair tags.",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                    ) {
                        Text("Grant Camera Permission", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // TOP CONTROLS
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp, start = 16.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.size(44.dp)
            ) {
                IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("ocr_back_btn")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "TAG SCANNER",
                        color = CyanAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    if (learnedRecords.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = PaymentPaid, modifier = Modifier.size(14.dp))
                    }
                }
            }

            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier.size(44.dp)
            ) {
                IconButton(onClick = { restartScanning() }, modifier = Modifier.testTag("ocr_scan_again_btn")) {
                    Icon(Icons.Default.Refresh, contentDescription = "Scan Again", tint = Color.White)
                }
            }
        }

        // SCANNING FRAME RETICLE WITH "+" ANCHOR PROMPT
        Box(
            modifier = Modifier
                .size(280.dp, 150.dp)
                .align(Alignment.Center)
                .border(2.dp, CyanAccent, RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "+",
                        color = CyanAccent,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "00125",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Aim at '+' tag marker\nPreserves leading zeros",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        // BOTTOM DETECTION & MANUAL CORRECTION PANEL
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "JOB NUMBER LOOKUP",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = CyanAccent,
                        letterSpacing = 1.sp
                    )
                    if (learnedRecords.isNotEmpty()) {
                        Text(
                            text = "${learnedRecords.size} learned patterns active",
                            fontSize = 10.sp,
                            color = PaymentPaid,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Text(
                    text = "Camera scans '+ 00125' tags. Or enter number manually below:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = detectedJobNumber,
                        onValueChange = { detectedJobNumber = it },
                        placeholder = { Text("00125", fontFamily = FontFamily.Monospace) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent,
                            letterSpacing = 3.sp
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ocr_detected_number_input")
                    )

                    Button(
                        onClick = { executeOpenJob(detectedJobNumber, isManualInput = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(56.dp)
                            .testTag("ocr_search_btn")
                    ) {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF090E1A), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF090E1A))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { restartScanning() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Resume Scanner", color = CyanAccent, fontSize = 13.sp)
                    }

                    Text(
                        text = "100% Offline • Preserves Zeros",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // 1. DIALOG: CONFIRMATION SCREEN ("Detected Job No: 00125")
    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = {
                showConfirmationDialog = false
                isScanningActive = true
            },
            icon = {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PaymentPaid, modifier = Modifier.size(36.dp))
            },
            title = {
                Text(
                    text = "Detected Job No: $detectedJobNumber",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Anchor symbol '+' verified. Leading zeros preserved.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Tag Reading: + $detectedJobNumber",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = CyanAccent,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = "Ready to search database for existing job.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        executeOpenJob(detectedJobNumber, isManualInput = false)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    modifier = Modifier.testTag("confirm_open_btn")
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF090E1A), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("OPEN", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        restartScanning()
                    },
                    modifier = Modifier.testTag("confirm_scan_again_btn")
                ) {
                    Text("SCAN AGAIN")
                }
            }
        )
    }

    // 2. DIALOG: UNCLEAR DETECTION ("Job number not clearly detected.")
    if (showUnclearDialog) {
        AlertDialog(
            onDismissRequest = {
                showUnclearDialog = false
                isScanningActive = true
            },
            icon = {
                Icon(Icons.Default.Warning, contentDescription = null, tint = StatusCancelled, modifier = Modifier.size(36.dp))
            },
            title = {
                Text("Job number not clearly detected.", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text(
                    text = "A repair tag was seen, but the digits following '+' were unclear. The scanner never silently guesses digits.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        restartScanning()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    modifier = Modifier.testTag("unclear_scan_again_btn")
                ) {
                    Text("SCAN AGAIN", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showUnclearDialog = false
                        // User proceeds to manual correction in the input field
                    },
                    modifier = Modifier.testTag("unclear_manual_correction_btn")
                ) {
                    Text("MANUAL CORRECTION")
                }
            }
        )
    }

    // 3. DIALOG: JOB NUMBER NOT FOUND
    if (notFoundDialogVisible) {
        AlertDialog(
            onDismissRequest = {
                notFoundDialogVisible = false
                isScanningActive = true
            },
            icon = {
                Icon(Icons.Default.Info, contentDescription = null, tint = StatusCancelled, modifier = Modifier.size(36.dp))
            },
            title = { Text("Job number not found.") },
            text = {
                Text("No repair job found with Job Number #$detectedJobNumber in the database. Scanned numbers are never automatically added as new repairs.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        restartScanning()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    modifier = Modifier.testTag("not_found_scan_again_btn")
                ) {
                    Text("SCAN AGAIN", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        notFoundDialogVisible = false
                    },
                    modifier = Modifier.testTag("not_found_manual_entry_btn")
                ) {
                    Text("MANUAL ENTRY")
                }
            }
        )
    }
}
