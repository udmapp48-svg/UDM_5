package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.RepairEntity
import com.example.ui.components.PaymentBadge
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BluePrimary
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavyCard
import com.example.ui.theme.NavyCardElevated
import com.example.ui.theme.PaymentPaid
import com.example.ui.theme.PaymentPartial
import com.example.ui.theme.StatusChecking
import com.example.ui.theme.StatusDelivered
import com.example.ui.theme.StatusReady
import com.example.ui.theme.StatusReceived
import com.example.ui.theme.StatusRepairing
import com.example.ui.viewmodel.RepairViewModel
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: RepairViewModel,
    onNavigateToNewRepair: () -> Unit,
    onNavigateToOcrScanner: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToRepairDetails: (Long) -> Unit,
    onNavigateToJobsFilter: (String) -> Unit
) {
    val stats by viewModel.dashboardStats.collectAsState()
    val allRepairs by viewModel.allRepairs.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val recentRepairs = allRepairs.take(5)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        // HEADER
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = settings.shopName,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        ),
                        color = CyanAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Professional Phone Repair Center",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onNavigateToSearch,
                        modifier = Modifier.testTag("header_search_btn")
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search Repairs",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = onNavigateToOcrScanner,
                        modifier = Modifier.testTag("header_ocr_btn")
                    ) {
                        Icon(
                            Icons.Default.DocumentScanner,
                            contentDescription = "Scan Job Number",
                            tint = CyanAccent
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("header_settings_btn")
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // PRIMARY ACTION: + NEW REPAIR (Largest and most prominent action)
        item {
            Button(
                onClick = onNavigateToNewRepair,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .testTag("new_repair_primary_button"),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(BluePrimary, CyanAccent)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.2f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "+ NEW REPAIR",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        // QUICK ACTIONS: Scan Sticker, Due Balance, Repairing
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Default.CameraAlt,
                    label = "Scan Sticker",
                    subtitle = "4-Digit OCR",
                    accentColor = CyanAccent,
                    modifier = Modifier.weight(1f),
                    testTag = "quick_action_scan_sticker",
                    onClick = onNavigateToOcrScanner
                )
                QuickActionButton(
                    icon = Icons.Default.Payments,
                    label = "Due Balance",
                    subtitle = "${stats.dueBalanceJobsCount} Jobs",
                    accentColor = PaymentPartial,
                    modifier = Modifier.weight(1f),
                    testTag = "quick_action_due_balance",
                    onClick = { onNavigateToJobsFilter("DUE_BALANCE") }
                )
                QuickActionButton(
                    icon = Icons.Default.Build,
                    label = "Repairing",
                    subtitle = "${stats.repairingCount} Active",
                    accentColor = StatusRepairing,
                    modifier = Modifier.weight(1f),
                    testTag = "quick_action_repairing",
                    onClick = { onNavigateToJobsFilter("REPAIRING") }
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // DASHBOARD SUMMARY: 2-column card layout
        item {
            Text(
                text = "DASHBOARD SUMMARY",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // Row 1: Today's Income & Outstanding Balance
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryMetricCard(
                    title = "Today's Income",
                    value = "${settings.currency} ${String.format(Locale.US, "%,.0f", stats.todayIncome)}",
                    icon = Icons.Default.MonetizationOn,
                    color = PaymentPaid,
                    modifier = Modifier.weight(1f)
                )
                SummaryMetricCard(
                    title = "Outstanding Balance",
                    value = "${settings.currency} ${String.format(Locale.US, "%,.0f", stats.outstandingBalance)}",
                    icon = Icons.Default.Payments,
                    color = PaymentPartial,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToJobsFilter("DUE_BALANCE") }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Pending Jobs & Checking
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryMetricCard(
                    title = "Pending Jobs",
                    value = "${stats.pendingJobsCount}",
                    icon = Icons.Default.HourglassTop,
                    color = StatusReceived,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToJobsFilter("ALL") }
                )
                SummaryMetricCard(
                    title = "Checking",
                    value = "${stats.checkingCount}",
                    icon = Icons.Default.Search,
                    color = StatusChecking,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToJobsFilter("CHECKING") }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            // Row 3: Repairing & Ready for Collection
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryMetricCard(
                    title = "Repairing",
                    value = "${stats.repairingCount}",
                    icon = Icons.Default.Build,
                    color = StatusRepairing,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToJobsFilter("REPAIRING") }
                )
                SummaryMetricCard(
                    title = "Ready for Collection",
                    value = "${stats.readyCount}",
                    icon = Icons.Default.CheckCircle,
                    color = StatusReady,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToJobsFilter("READY") }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            // Row 4: Due Balance Jobs & Delivered Today
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                SummaryMetricCard(
                    title = "Due Balance Jobs",
                    value = "${stats.dueBalanceJobsCount}",
                    icon = Icons.Default.ErrorOutline,
                    color = PaymentPartial,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToJobsFilter("DUE_BALANCE") }
                )
                SummaryMetricCard(
                    title = "Delivered Today",
                    value = "${stats.deliveredTodayCount}",
                    icon = Icons.Default.CheckCircle,
                    color = StatusDelivered,
                    modifier = Modifier.weight(1f),
                    onClick = { onNavigateToJobsFilter("DELIVERED") }
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // RECENT REPAIRS
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT REPAIRS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { onNavigateToJobsFilter("ALL") }) {
                    Text("See All (${allRepairs.size})", color = CyanAccent, fontSize = 13.sp)
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (recentRepairs.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Smartphone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No repair jobs recorded yet",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap + NEW REPAIR above to register the first customer phone",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            items(recentRepairs) { repair ->
                RecentRepairCard(
                    repair = repair,
                    currency = settings.currency,
                    onClick = { onNavigateToRepairDetails(repair.id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun QuickActionButton(
    icon: ImageVector,
    label: String,
    subtitle: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    testTag: String = "",
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
fun SummaryMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RecentRepairCard(
    repair: RepairEntity,
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
            .testTag("recent_repair_card_${repair.jobNumber}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = repair.customerName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                StatusBadge(status = repair.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${repair.brand} ${repair.model}",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$currency ${String.format(Locale.US, "%,.0f", repair.totalPrice)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            if (repair.balance > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Due Balance",
                        fontSize = 11.sp,
                        color = PaymentPartial
                    )
                    Text(
                        text = "$currency ${String.format(Locale.US, "%,.0f", repair.balance)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PaymentPartial
                    )
                }
            }
        }
    }
}
