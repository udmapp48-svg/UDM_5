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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.PaymentPaid
import com.example.ui.theme.PaymentPartial
import com.example.ui.theme.StatusChecking
import com.example.ui.theme.StatusRepairing
import com.example.ui.viewmodel.RepairViewModel

@Composable
fun MoreScreen(
    viewModel: RepairViewModel,
    onNavigateToPriceManager: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToMasterData: () -> Unit,
    onNavigateToBackupRestore: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPrinterSettings: () -> Unit = {}
) {
    val settings by viewModel.settings.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "MANAGEMENT & TOOLS",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                ),
                color = CyanAccent,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        item {
            MoreNavigationCard(
                title = "Price Manager",
                subtitle = "Manage Brand -> Model -> Repair Type price directory",
                icon = Icons.Default.MonetizationOn,
                accentColor = CyanAccent,
                onClick = onNavigateToPriceManager,
                testTag = "more_price_manager_btn"
            )
        }

        item {
            MoreNavigationCard(
                title = "Reports & Analytics",
                subtitle = "Daily, monthly income, busy days, busy hours & fault trends",
                icon = Icons.Default.Analytics,
                accentColor = PaymentPaid,
                onClick = onNavigateToReports,
                testTag = "more_reports_btn"
            )
        }

        item {
            MoreNavigationCard(
                title = "Custom Data Master",
                subtitle = "Manage Brands, Models, Faults and Repair Types list",
                icon = Icons.Default.Category,
                accentColor = StatusChecking,
                onClick = onNavigateToMasterData,
                testTag = "more_master_data_btn"
            )
        }

        item {
            MoreNavigationCard(
                title = "Backup & Restore",
                subtitle = "Export or restore complete shop database via offline JSON",
                icon = Icons.Default.Backup,
                accentColor = PaymentPartial,
                onClick = onNavigateToBackupRestore,
                testTag = "more_backup_restore_btn"
            )
        }

        item {
            MoreNavigationCard(
                title = "Printer Settings",
                subtitle = "Marklife P50S thermal receipt printer BLE setup & test print",
                icon = Icons.Default.Print,
                accentColor = CyanAccent,
                onClick = onNavigateToPrinterSettings,
                testTag = "more_printer_btn"
            )
        }

        item {
            MoreNavigationCard(
                title = "Shop Settings & SMS",
                subtitle = "Configure shop profile, starting job number, SMS templates",
                icon = Icons.Default.Settings,
                accentColor = StatusRepairing,
                onClick = onNavigateToSettings,
                testTag = "more_settings_btn"
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = settings.shopName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(text = "Phone: ${settings.shopPhone}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "Address: ${settings.shopAddress}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "100% Offline-first local database. No cloud dependencies.",
                        fontSize = 11.sp,
                        color = CyanAccent
                    )
                }
            }
        }
    }
}

@Composable
fun MoreNavigationCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String = ""
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
