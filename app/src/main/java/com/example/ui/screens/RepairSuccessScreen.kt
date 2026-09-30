package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.core.content.ContextCompat
import com.example.data.entity.RepairEntity
import com.example.data.repository.SmsSendResult
import com.example.ui.components.PaymentBadge
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.PaymentPaid
import com.example.ui.theme.PaymentPartial
import com.example.ui.util.safeGetItemsAndPayments
import com.example.ui.viewmodel.RepairViewModel
import com.example.util.ReceiptHelper
import java.util.Locale

@Composable
fun RepairSuccessScreen(
    repair: RepairEntity,
    viewModel: RepairViewModel,
    onDone: () -> Unit,
    onEdit: (Long) -> Unit
) {
    BackHandler { onDone() }

    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val (items, payments) = safeGetItemsAndPayments(repair.id, viewModel)
    var isSendingSms by remember { mutableStateOf(false) }
    var isPrintingReceipt by remember { mutableStateOf(false) }
    val printStatusMsg by viewModel.printStatusMessage.collectAsState()

    // Auto Print Customer Receipt if enabled in Settings -> Printer
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (viewModel.isAutoPrintReceipt.value) {
            isPrintingReceipt = true
            viewModel.printCustomerReceiptDirect(repair) { result ->
                isPrintingReceipt = false
                when (result) {
                    is com.example.printer.PrintResult.Success -> {
                        Toast.makeText(context, "Receipt auto-printed on P50S", Toast.LENGTH_SHORT).show()
                    }
                    is com.example.printer.PrintResult.Error -> {
                        Toast.makeText(context, "Auto-print: ${result.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            isSendingSms = true
            viewModel.sendRegistrationSmsExplicit(repair) { result ->
                isSendingSms = false
                val msg = when (result) {
                    is SmsSendResult.SentOrQueued -> "Confirmation SMS sent to ${repair.customerPhone}"
                    is SmsSendResult.PermissionDenied -> "SMS permission not granted."
                    is SmsSendResult.Failed -> "SMS failed: ${result.error}"
                    is SmsSendResult.DisabledInSettings -> "SMS is disabled in Settings."
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "SMS permission denied in Android settings.", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Success Icon
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(PaymentPaid.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = PaymentPaid,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "JOB ADDED ✓",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = PaymentPaid,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // VERY LARGE JOB NUMBER
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, CyanAccent, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "JOB NO: ${repair.jobNumber}",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = CyanAccent,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = repair.jobNumber,
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = CyanAccent,
                    letterSpacing = 4.sp
                )
                Text(
                    text = "Write this 4-digit number on customer sticker",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // SUMMARY CARD
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                DetailRow(label = "Customer Name", value = repair.customerName)
                DetailRow(label = "Device", value = "${repair.brand} ${repair.model}")
                DetailRow(
                    label = "Total Price",
                    value = "${settings.currency} ${String.format(Locale.US, "%,.0f", repair.totalPrice)}"
                )
                DetailRow(
                    label = "Amount Paid",
                    value = "${settings.currency} ${String.format(Locale.US, "%,.0f", repair.amountPaid)}"
                )
                DetailRow(
                    label = "Balance Due",
                    value = "${settings.currency} ${String.format(Locale.US, "%,.0f", repair.balance)}",
                    valueColor = if (repair.balance > 0) PaymentPartial else CyanAccent
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Payment Status:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    PaymentBadge(paymentStatus = repair.paymentStatus)
                }
                DetailRow(label = "Date & Time", value = "${repair.receivedDate} at ${repair.receivedTime}")
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // SMS CONFIRMATION STATUS CARD
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CyanAccent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CyanAccent.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Message,
                            contentDescription = null,
                            tint = CyanAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (settings.autoRegistrationSms) "Customer Registration SMS" else "Customer SMS (Manual)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (settings.autoRegistrationSms) "Sent to ${repair.customerPhone}" else "Automatic SMS disabled in Settings",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                TextButton(
                    onClick = {
                        val hasSmsPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.SEND_SMS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (!hasSmsPermission) {
                            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                        } else {
                            isSendingSms = true
                            viewModel.sendRegistrationSmsExplicit(repair) { result ->
                                isSendingSms = false
                                val msg = when (result) {
                                    is SmsSendResult.SentOrQueued -> "Confirmation SMS sent to ${repair.customerPhone}"
                                    is SmsSendResult.PermissionDenied -> "SMS permission not granted."
                                    is SmsSendResult.Failed -> "SMS failed: ${result.error}"
                                    is SmsSendResult.DisabledInSettings -> "SMS is disabled in Settings."
                                }
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !isSendingSms,
                    modifier = Modifier.testTag("resend_registration_sms_btn")
                ) {
                    Text(
                        text = if (isSendingSms) "Sending..." else if (settings.autoRegistrationSms) "Resend SMS" else "Send SMS",
                        color = CyanAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // PRIMARY ACTION: PRINT CUSTOMER RECEIPT (Marklife P50S thermal receipt)
        Button(
            onClick = {
                isPrintingReceipt = true
                viewModel.printCustomerReceiptDirect(repair) { result ->
                    isPrintingReceipt = false
                    when (result) {
                        is com.example.printer.PrintResult.Success -> {
                            Toast.makeText(context, "Customer receipt printed successfully!", Toast.LENGTH_SHORT).show()
                        }
                        is com.example.printer.PrintResult.Error -> {
                            Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            },
            enabled = !isPrintingReceipt,
            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("print_customer_receipt_btn")
        ) {
            Icon(Icons.Default.Print, contentDescription = null, tint = Color(0xFF090E1A), modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isPrintingReceipt) {
                    val msg = printStatusMsg.uppercase()
                    if (msg.isNotBlank() && msg != "READY" && msg != "PRINT COMPLETE") msg else "PRINTING RECEIPT..."
                } else "PRINT CUSTOMER RECEIPT",
                color = Color(0xFF090E1A),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // PRIMARY ACTION: DONE
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("success_done_btn"),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("DONE", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // SECONDARY ACTIONS: Share & Edit Job
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { onEdit(repair.id) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("success_edit_btn"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Edit Job", fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = {
                    ReceiptHelper.shareReceipt(context, repair, items, payments, settings)
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("success_share_btn"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Share", color = CyanAccent, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        Text(text = value, fontWeight = FontWeight.SemiBold, color = valueColor, fontSize = 13.sp)
    }
}
