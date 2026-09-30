package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import com.example.data.entity.RepairEntity
import com.example.ui.screens.BackupRestoreScreen
import com.example.ui.screens.BleDiagnosticScreen
import com.example.ui.screens.CustomerHistoryScreen
import com.example.ui.screens.CustomersScreen
import com.example.ui.screens.EditRepairScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.JobsScreen
import com.example.ui.screens.MasterDataScreen
import com.example.ui.screens.MoreScreen
import com.example.ui.screens.NewRepairScreen
import com.example.ui.screens.OcrScannerScreen
import com.example.ui.screens.PriceManagerScreen
import com.example.ui.screens.PrinterSettingsScreen
import com.example.ui.screens.RepairDetailsScreen
import com.example.ui.screens.RepairSuccessScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.NavyBorder
import com.example.ui.theme.NavySurface
import com.example.ui.theme.UDMRepairTheme
import com.example.ui.viewmodel.RepairViewModel

sealed class Screen {
    object Home : Screen()
    object Jobs : Screen()
    object Customers : Screen()
    object More : Screen()

    object NewRepair : Screen()
    data class RepairSuccess(val repair: RepairEntity) : Screen()
    data class RepairDetails(val repairId: Long) : Screen()
    data class EditRepair(val repairId: Long) : Screen()
    object OcrScanner : Screen()
    object PriceManager : Screen()
    object Reports : Screen()
    object MasterData : Screen()
    object BackupRestore : Screen()
    object Settings : Screen()
    object PrinterSettings : Screen()
    object BleDiagnostic : Screen()
    data class CustomerHistory(val phone: String) : Screen()
}

class MainActivity : ComponentActivity() {

    private val viewModel: RepairViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsState()
            UDMRepairTheme(darkTheme = settings.isDarkMode) {
                MainAppContainer(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppContainer(viewModel: RepairViewModel) {
    var currentTab by remember { mutableStateOf<Screen>(Screen.Home) }
    val screenStack = remember { mutableStateListOf<Screen>() }

    val currentScreen = screenStack.lastOrNull() ?: currentTab

    fun navigateTo(screen: Screen) {
        screenStack.add(screen)
    }

    fun navigateBack() {
        if (screenStack.isNotEmpty()) {
            screenStack.removeAt(screenStack.lastIndex)
        } else if (currentTab != Screen.Home) {
            currentTab = Screen.Home
        }
    }

    val isTopLevel = screenStack.isEmpty()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isTopLevel) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    val items = listOf(
                        Triple(Screen.Home, "HOME", Icons.Default.Home),
                        Triple(Screen.Jobs, "JOBS", Icons.Default.Build),
                        Triple(Screen.Customers, "CUSTOMERS", Icons.Default.People),
                        Triple(Screen.More, "MORE", Icons.Default.Menu)
                    )

                    items.forEach { (screen, label, icon) ->
                        val selected = currentTab == screen
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                currentTab = screen
                                screenStack.clear()
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color(0xFF090E1A),
                                selectedTextColor = CyanAccent,
                                indicatorColor = CyanAccent,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.testTag("nav_${label.lowercase()}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentScreen) {
                is Screen.Home -> {
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateToNewRepair = { navigateTo(Screen.NewRepair) },
                        onNavigateToOcrScanner = { navigateTo(Screen.OcrScanner) },
                        onNavigateToSettings = { navigateTo(Screen.Settings) },
                        onNavigateToSearch = {
                            currentTab = Screen.Jobs
                            screenStack.clear()
                        },
                        onNavigateToRepairDetails = { id -> navigateTo(Screen.RepairDetails(id)) },
                        onNavigateToJobsFilter = { filter ->
                            viewModel.setStatusFilter(filter)
                            currentTab = Screen.Jobs
                            screenStack.clear()
                        }
                    )
                }

                is Screen.Jobs -> {
                    JobsScreen(
                        viewModel = viewModel,
                        onNavigateToRepairDetails = { id -> navigateTo(Screen.RepairDetails(id)) }
                    )
                }

                is Screen.Customers -> {
                    CustomersScreen(
                        viewModel = viewModel,
                        onSelectCustomer = { phone -> navigateTo(Screen.CustomerHistory(phone)) }
                    )
                }

                is Screen.More -> {
                    MoreScreen(
                        viewModel = viewModel,
                        onNavigateToPriceManager = { navigateTo(Screen.PriceManager) },
                        onNavigateToReports = { navigateTo(Screen.Reports) },
                        onNavigateToMasterData = { navigateTo(Screen.MasterData) },
                        onNavigateToBackupRestore = { navigateTo(Screen.BackupRestore) },
                        onNavigateToSettings = { navigateTo(Screen.Settings) },
                        onNavigateToPrinterSettings = { navigateTo(Screen.PrinterSettings) }
                    )
                }

                is Screen.NewRepair -> {
                    NewRepairScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navigateBack() },
                        onNavigateToCustomerHistory = { phone -> navigateTo(Screen.CustomerHistory(phone)) },
                        onRepairCreated = { createdRepair ->
                            screenStack.removeAt(screenStack.lastIndex)
                            navigateTo(Screen.RepairSuccess(createdRepair))
                        }
                    )
                }

                is Screen.RepairSuccess -> {
                    RepairSuccessScreen(
                        repair = currentScreen.repair,
                        viewModel = viewModel,
                        onDone = {
                            screenStack.clear()
                            currentTab = Screen.Home
                        },
                        onEdit = { id ->
                            screenStack.removeAt(screenStack.lastIndex)
                            navigateTo(Screen.EditRepair(id))
                        }
                    )
                }

                is Screen.RepairDetails -> {
                    RepairDetailsScreen(
                        repairId = currentScreen.repairId,
                        viewModel = viewModel,
                        onNavigateBack = { navigateBack() },
                        onNavigateToEdit = { id -> navigateTo(Screen.EditRepair(id)) }
                    )
                }

                is Screen.EditRepair -> {
                    EditRepairScreen(
                        repairId = currentScreen.repairId,
                        viewModel = viewModel,
                        onNavigateBack = { navigateBack() }
                    )
                }

                is Screen.OcrScanner -> {
                    OcrScannerScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navigateBack() },
                        onRepairFound = { id ->
                            screenStack.removeAt(screenStack.lastIndex)
                            navigateTo(Screen.RepairDetails(id))
                        }
                    )
                }

                is Screen.PriceManager -> {
                    PriceManagerScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navigateBack() }
                    )
                }

                is Screen.Reports -> {
                    ReportsScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navigateBack() }
                    )
                }

                is Screen.MasterData -> {
                    MasterDataScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navigateBack() }
                    )
                }

                is Screen.BackupRestore -> {
                    BackupRestoreScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navigateBack() }
                    )
                }

                is Screen.Settings -> {
                    SettingsScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navigateBack() },
                        onNavigateToPrinter = { navigateTo(Screen.PrinterSettings) }
                    )
                }

                is Screen.PrinterSettings -> {
                    PrinterSettingsScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navigateBack() },
                        onNavigateToDiagnostic = { navigateTo(Screen.BleDiagnostic) }
                    )
                }

                is Screen.BleDiagnostic -> {
                    BleDiagnosticScreen(
                        viewModel = viewModel,
                        onNavigateBack = { navigateBack() }
                    )
                }

                is Screen.CustomerHistory -> {
                    CustomerHistoryScreen(
                        customerPhone = currentScreen.phone,
                        viewModel = viewModel,
                        onNavigateBack = { navigateBack() },
                        onNavigateToRepairDetails = { id -> navigateTo(Screen.RepairDetails(id)) }
                    )
                }
            }
        }
    }
}
