package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.PaymentPaid
import com.example.ui.theme.PaymentPartial
import com.example.ui.theme.StatusReceived
import com.example.ui.viewmodel.RepairViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: RepairViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val allRepairs by viewModel.allRepairs.collectAsState()
    val allPayments by viewModel.allPayments.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var selectedPeriod by remember { mutableStateOf("ALL") } // TODAY, THIS_WEEK, THIS_MONTH, ALL

    // Date filtering
    val now = Calendar.getInstance()
    val todayDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    val filteredRepairs = remember(allRepairs, selectedPeriod) {
        when (selectedPeriod) {
            "TODAY" -> allRepairs.filter { it.receivedDate == todayDateStr }
            "THIS_WEEK" -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -7)
                val cutoff = cal.timeInMillis
                allRepairs.filter { it.receivedTimestamp >= cutoff }
            }
            "THIS_MONTH" -> {
                val currentYearMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
                allRepairs.filter { it.receivedDate.startsWith(currentYearMonth) }
            }
            else -> allRepairs
        }
    }

    val filteredPayments = remember(allPayments, selectedPeriod) {
        when (selectedPeriod) {
            "TODAY" -> allPayments.filter { it.date == todayDateStr }
            "THIS_WEEK" -> {
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -7)
                val cutoff = cal.timeInMillis
                allPayments.filter { it.timestamp >= cutoff }
            }
            "THIS_MONTH" -> {
                val currentYearMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
                allPayments.filter { it.date.startsWith(currentYearMonth) }
            }
            else -> allPayments
        }
    }

    val totalJobs = filteredRepairs.size
    val totalRepairValue = filteredRepairs.sumOf { it.totalPrice }
    val totalIncome = filteredPayments.sumOf { it.amount } // ONLY actual payments!
    val totalOutstanding = filteredRepairs.filter { it.status != "CANCELLED" }.sumOf { it.balance }

    val paidJobs = filteredRepairs.count { it.paymentStatus == "PAID" }
    val partialJobs = filteredRepairs.count { it.paymentStatus == "PARTIALLY PAID" }
    val unpaidJobs = filteredRepairs.count { it.paymentStatus == "UNPAID" }

    val deliveredJobs = filteredRepairs.count { it.status == "DELIVERED" }
    val pendingJobs = filteredRepairs.count { it.status in listOf("RECEIVED", "CHECKING", "REPAIRING", "READY") }
    val cancelledJobs = filteredRepairs.count { it.status == "CANCELLED" }

    // Analytics: Busy Days (sorted highest to lowest)
    val busyDays = remember(filteredRepairs) {
        filteredRepairs.groupBy { it.receivedDate }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
    }

    // Analytics: Busy Hours (sorted highest to lowest)
    val busyHours = remember(filteredRepairs) {
        filteredRepairs.groupBy {
            val time = it.receivedTime
            if (time.length >= 2) "${time.substring(0, 2)}:00" else "Unknown"
        }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
    }

    // Analytics: Most Repaired Models (sorted highest to lowest)
    val topModels = remember(filteredRepairs) {
        filteredRepairs.groupBy { "${it.brand} ${it.model}" }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(6)
    }

    // Analytics: Common Faults (sorted highest to lowest)
    val topFaults = remember(filteredRepairs) {
        filteredRepairs.groupBy { it.fault }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(6)
    }

    fun shareReport() {
        val cur = settings.currency
        val text = """
            ==================================
            ${settings.shopName} - REPAIR REPORT
            Period: $selectedPeriod
            ==================================
            Total Jobs: $totalJobs
            Total Repair Value: $cur ${String.format(Locale.US, "%,.2f", totalRepairValue)}
            Actual Income Collected: $cur ${String.format(Locale.US, "%,.2f", totalIncome)}
            Total Outstanding Balance: $cur ${String.format(Locale.US, "%,.2f", totalOutstanding)}
            ----------------------------------
            Status Breakdown:
             - Pending: $pendingJobs
             - Delivered: $deliveredJobs
             - Cancelled: $cancelledJobs
            ----------------------------------
            Payment Breakdown:
             - Paid: $paidJobs
             - Partial: $partialJobs
             - Unpaid: $unpaidJobs
            ==================================
        """.trimIndent()
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, text)
            type = "text/plain"
        }
        context.startActivity(Intent.createChooser(sendIntent, "Share Repair Report"))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports & Analytics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { shareReport() }) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = CyanAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 40.dp)
        ) {
            // Period Filter
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "TODAY" to "Today",
                        "THIS_WEEK" to "7 Days",
                        "THIS_MONTH" to "Month",
                        "ALL" to "All Time"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = selectedPeriod == key,
                            onClick = { selectedPeriod = key },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
                                selectedLabelColor = CyanAccent
                            ),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // FINANCIAL SUMMARY (Income is ONLY actual payments!)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "FINANCIAL SUMMARY",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = CyanAccent
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Jobs Processed", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$totalJobs", fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Repair Value", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${settings.currency} ${String.format(Locale.US, "%,.2f", totalRepairValue)}", fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Actual Income (Collected)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${settings.currency} ${String.format(Locale.US, "%,.2f", totalIncome)}",
                                fontWeight = FontWeight.ExtraBold,
                                color = PaymentPaid
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Outstanding Balance", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${settings.currency} ${String.format(Locale.US, "%,.2f", totalOutstanding)}",
                                fontWeight = FontWeight.Bold,
                                color = PaymentPartial
                            )
                        }
                    }
                }
            }

            // STATUS & PAYMENT BREAKDOWN
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("STATUS & PAYMENT BREAKDOWN", fontWeight = FontWeight.Bold, color = CyanAccent, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Pending Jobs:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$pendingJobs", fontWeight = FontWeight.Bold, color = StatusReceived)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Delivered Jobs:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$deliveredJobs", fontWeight = FontWeight.Bold, color = PaymentPaid)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Fully Paid:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$paidJobs", fontWeight = FontWeight.Bold, color = PaymentPaid)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Partially Paid:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$partialJobs", fontWeight = FontWeight.Bold, color = PaymentPartial)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Unpaid:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$unpaidJobs", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // BUSY DAYS & BUSY HOURS
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("BUSY DAYS (Highest to Lowest)", fontWeight = FontWeight.Bold, color = CyanAccent, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (busyDays.isEmpty()) {
                            Text("No data", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            busyDays.forEach { (day, count) ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(day, fontSize = 13.sp)
                                    Text("$count jobs", fontWeight = FontWeight.Bold, color = CyanAccent, fontSize = 13.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text("BUSY HOURS (Peak Shop Hours)", fontWeight = FontWeight.Bold, color = CyanAccent, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (busyHours.isEmpty()) {
                            Text("No data", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            busyHours.forEach { (hour, count) ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(hour, fontSize = 13.sp)
                                    Text("$count jobs", fontWeight = FontWeight.Bold, color = CyanAccent, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // MOST REPAIRED MODELS & COMMON FAULTS
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("MOST REPAIRED MODELS", fontWeight = FontWeight.Bold, color = CyanAccent, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (topModels.isEmpty()) {
                            Text("No data", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            topModels.forEach { (model, count) ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(model, fontSize = 13.sp)
                                    Text("$count repairs", fontWeight = FontWeight.Bold, color = CyanAccent, fontSize = 13.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text("COMMON FAULTS", fontWeight = FontWeight.Bold, color = CyanAccent, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (topFaults.isEmpty()) {
                            Text("No data", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            topFaults.forEach { (fault, count) ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(fault, fontSize = 13.sp)
                                    Text("$count complaints", fontWeight = FontWeight.Bold, color = CyanAccent, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
