package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.CustomerEntity
import com.example.data.entity.RepairEntity
import com.example.ui.components.SearchInputField
import com.example.ui.components.StatusBadge
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.PaymentPartial
import com.example.ui.util.PhoneActions
import com.example.ui.viewmodel.RepairViewModel
import java.util.Locale

@Composable
fun CustomersScreen(
    viewModel: RepairViewModel,
    onSelectCustomer: (String) -> Unit
) {
    val customers by viewModel.allCustomers.collectAsState()
    val allRepairs by viewModel.allRepairs.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val filteredCustomers = remember(customers, searchQuery) {
        if (searchQuery.isBlank()) customers
        else customers.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.phone.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
    ) {
        Text(
            text = "CUSTOMERS DIRECTORY",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = CyanAccent,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        SearchInputField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholderText = "Search by customer name or phone...",
            testTag = "customers_search_input"
        )

        Spacer(modifier = Modifier.height(14.dp))

        if (filteredCustomers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (customers.isEmpty()) "No customers recorded yet" else "No matching customers found",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredCustomers, key = { it.phone }) { customer ->
                    val customerRepairs = allRepairs.filter { it.customerPhone == customer.phone }
                    val totalSpent = customerRepairs.sumOf { it.amountPaid }
                    val totalDue = customerRepairs.sumOf { it.balance }

                    CustomerCard(
                        customer = customer,
                        jobsCount = customerRepairs.size,
                        totalSpent = totalSpent,
                        totalDue = totalDue,
                        currency = settings.currency,
                        onClick = { onSelectCustomer(customer.phone) }
                    )
                }
            }
        }
    }
}

@Composable
fun CustomerCard(
    customer: CustomerEntity,
    jobsCount: Int,
    totalSpent: Double,
    totalDue: Double,
    currency: String,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("customer_card_${customer.phone}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(CyanAccent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = customer.phone,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "$jobsCount Jobs",
                        fontSize = 12.sp,
                        color = CyanAccent,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (totalDue > 0) {
                        Text(
                            text = "Due: $currency ${String.format(Locale.US, "%,.0f", totalDue)}",
                            fontSize = 12.sp,
                            color = PaymentPartial,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerHistoryScreen(
    customerPhone: String,
    viewModel: RepairViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToRepairDetails: (Long) -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val allRepairs by viewModel.allRepairs.collectAsState()
    val allCustomers by viewModel.allCustomers.collectAsState()
    val settings by viewModel.settings.collectAsState()

    val customer = allCustomers.find { it.phone == customerPhone }
    val customerRepairs = allRepairs.filter { it.customerPhone == customerPhone }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(customer?.name ?: "Customer History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { PhoneActions.callCustomer(context, customerPhone) }) {
                        Icon(Icons.Default.Call, contentDescription = "Call", tint = CyanAccent)
                    }
                    IconButton(onClick = { PhoneActions.openWhatsApp(context, customerPhone) }) {
                        Icon(Icons.Default.Send, contentDescription = "WhatsApp", tint = Color(0xFF25D366))
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Customer Header Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = customer?.name ?: "Customer",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(text = customerPhone, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Jobs:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text("${customerRepairs.size}", fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Repair Value:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text("${settings.currency} ${String.format(Locale.US, "%,.0f", customerRepairs.sumOf { it.totalPrice })}", fontWeight = FontWeight.Bold)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Balance Due:", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            Text(
                                "${settings.currency} ${String.format(Locale.US, "%,.0f", customerRepairs.sumOf { it.balance })}",
                                fontWeight = FontWeight.Bold,
                                color = if (customerRepairs.sumOf { it.balance } > 0) PaymentPartial else CyanAccent
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "REPAIR HISTORY (${customerRepairs.size})",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = CyanAccent
                )
            }

            items(customerRepairs) { repair ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .clickable { onNavigateToRepairDetails(repair.id) }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            StatusBadge(status = repair.status)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${repair.brand} ${repair.model}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = repair.fault,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text = "Total: ${settings.currency} ${String.format(Locale.US, "%,.0f", repair.totalPrice)}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(text = "Paid: ${settings.currency} ${String.format(Locale.US, "%,.0f", repair.amountPaid)}", fontSize = 12.sp, color = com.example.ui.theme.PaymentPaid, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Balance: ${settings.currency} ${String.format(Locale.US, "%,.0f", repair.balance)}",
                                fontSize = 12.sp,
                                color = if (repair.balance > 0) PaymentPartial else CyanAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
