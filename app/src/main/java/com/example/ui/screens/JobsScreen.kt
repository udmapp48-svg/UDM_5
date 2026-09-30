package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.RepairEntity
import com.example.ui.components.PaymentBadge
import com.example.ui.components.SearchInputField
import com.example.ui.components.StatusBadge
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.PaymentPartial
import com.example.ui.util.PhoneActions
import com.example.ui.viewmodel.RepairViewModel
import java.util.Locale

@Composable
fun JobsScreen(
    viewModel: RepairViewModel,
    onNavigateToRepairDetails: (Long) -> Unit
) {
    val context = LocalContext.current
    val repairs by viewModel.filteredRepairs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val statusFilter by viewModel.statusFilter.collectAsState()
    val brandFilter by viewModel.brandFilter.collectAsState()
    val paymentFilter by viewModel.paymentFilter.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val allBrands by viewModel.allBrands.collectAsState()

    var showBrandMenu by remember { mutableStateOf(false) }
    var showPaymentMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
    ) {
        // Search Input
        SearchInputField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            placeholderText = "Search Job #, customer, phone, IMEI, model...",
            testTag = "jobs_search_input"
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Status Filter Chips (Scrollable)
        val statusList = listOf(
            "ALL" to "All",
            "RECEIVED" to "Received",
            "CHECKING" to "Checking",
            "REPAIRING" to "Repairing",
            "READY" to "Ready",
            "DELIVERED" to "Delivered",
            "DUE_BALANCE" to "Due Balance",
            "CANCELLED" to "Cancelled"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            statusList.forEach { (key, label) ->
                FilterChip(
                    selected = statusFilter == key,
                    onClick = { viewModel.setStatusFilter(key) },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanAccent.copy(alpha = 0.2f),
                        selectedLabelColor = CyanAccent
                    )
                )
            }
        }

        // Secondary Filters: Brand & Payment Status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${repairs.size} jobs found",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Brand filter dropdown
                Box {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { showBrandMenu = true }
                    ) {
                        Text(
                            text = if (brandFilter == "ALL") "Brand: All" else brandFilter,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                    DropdownMenu(expanded = showBrandMenu, onDismissRequest = { showBrandMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("All Brands") },
                            onClick = {
                                viewModel.setBrandFilter("ALL")
                                showBrandMenu = false
                            }
                        )
                        allBrands.forEach { b ->
                            DropdownMenuItem(
                                text = { Text(b.name) },
                                onClick = {
                                    viewModel.setBrandFilter(b.name)
                                    showBrandMenu = false
                                }
                            )
                        }
                    }
                }

                // Payment filter dropdown
                Box {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { showPaymentMenu = true }
                    ) {
                        Text(
                            text = if (paymentFilter == "ALL") "Payment: All" else paymentFilter,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                    DropdownMenu(expanded = showPaymentMenu, onDismissRequest = { showPaymentMenu = false }) {
                        listOf("ALL", "UNPAID", "PARTIALLY PAID", "PAID").forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p) },
                                onClick = {
                                    viewModel.setPaymentFilter(p)
                                    showPaymentMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Repairs List
        if (repairs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Smartphone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No repair jobs match your filter",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(repairs, key = { it.id }) { repair ->
                    JobListItem(
                        repair = repair,
                        currency = settings.currency,
                        onClick = { onNavigateToRepairDetails(repair.id) },
                        onCall = { PhoneActions.callCustomer(context, repair.customerPhone) },
                        onWhatsApp = {
                            PhoneActions.openWhatsApp(
                                context,
                                repair.customerPhone,
                                "Hello ${repair.customerName}, regarding repair Job #${repair.jobNumber} (${repair.brand} ${repair.model}): Status is ${repair.status}. Balance: ${settings.currency} ${String.format(Locale.US, "%,.0f", repair.balance)}."
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun JobListItem(
    repair: RepairEntity,
    currency: String,
    onClick: () -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("job_item_${repair.jobNumber}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Job #, Customer Name, Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = CyanAccent.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "#${repair.jobNumber}",
                            color = CyanAccent,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = repair.customerName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                StatusBadge(status = repair.status)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Device & Fault
            Text(
                text = "${repair.brand} ${repair.model}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Fault: ${repair.fault}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Pricing & Dates Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total: $currency ${String.format(Locale.US, "%,.0f", repair.totalPrice)}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (repair.balance > 0) {
                        Text(
                            text = "Due: $currency ${String.format(Locale.US, "%,.0f", repair.balance)}",
                            fontSize = 12.sp,
                            color = PaymentPartial,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onCall,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Call", tint = CyanAccent, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = onWhatsApp,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "WhatsApp", tint = Color(0xFF25D366), modifier = Modifier.size(18.dp))
                    }
                }
            }

            Text(
                text = "Received: ${repair.receivedDate}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
