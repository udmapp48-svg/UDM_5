package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.SearchInputField
import com.example.ui.components.SmartAutocompleteField
import com.example.ui.theme.CyanAccent
import com.example.ui.viewmodel.RepairViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterDataScreen(
    viewModel: RepairViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Brands, 1: Models, 2: Faults, 3: Repair Types
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    val brands by viewModel.allBrands.collectAsState()
    val faults by viewModel.allFaults.collectAsState()
    val repairTypes by viewModel.allRepairTypes.collectAsState()

    val tabs = listOf("Brands", "Models", "Faults", "Repair Types")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Custom Data Master", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = CyanAccent
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color(0xFF090E1A))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = CyanAccent
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = {
                            selectedTab = index
                            searchQuery = ""
                        },
                        text = { Text(title, fontSize = 12.sp, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                SearchInputField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholderText = "Search ${tabs[selectedTab]}..."
                )

                Spacer(modifier = Modifier.height(10.dp))

                when (selectedTab) {
                    0 -> { // Brands
                        val filtered = remember(brands, searchQuery) {
                            if (searchQuery.isBlank()) brands
                            else brands.filter { it.name.contains(searchQuery, ignoreCase = true) }
                        }
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 96.dp)
                        ) {
                            items(filtered, key = { it.id }) { b ->
                                MasterDataCard(
                                    title = b.name,
                                    onDelete = { viewModel.deleteBrand(b) }
                                )
                            }
                        }
                    }
                    1 -> { // Models
                        var selectedBrandForModels by remember { mutableStateOf(brands.firstOrNull()?.name ?: "Samsung") }
                        var brandModels by remember { mutableStateOf<List<String>>(emptyList()) }

                        androidx.compose.runtime.LaunchedEffect(selectedBrandForModels) {
                            viewModel.getModelsForBrand(selectedBrandForModels).collect { list ->
                                brandModels = list.map { it.modelName }
                            }
                        }

                        SmartAutocompleteField(
                            label = "Select Brand to view Models",
                            value = selectedBrandForModels,
                            onValueChange = { selectedBrandForModels = it },
                            suggestions = brands.map { it.name },
                            onSuggestionSelected = { selectedBrandForModels = it },
                            onAddNew = { viewModel.addBrand(it) }
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val filteredModels = remember(brandModels, searchQuery) {
                            if (searchQuery.isBlank()) brandModels
                            else brandModels.filter { it.contains(searchQuery, ignoreCase = true) }
                        }

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 96.dp)
                        ) {
                            items(filteredModels) { m ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(m, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text(selectedBrandForModels, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    2 -> { // Faults
                        val filtered = remember(faults, searchQuery) {
                            if (searchQuery.isBlank()) faults
                            else faults.filter { it.name.contains(searchQuery, ignoreCase = true) }
                        }
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 96.dp)
                        ) {
                            items(filtered, key = { it.id }) { f ->
                                MasterDataCard(
                                    title = f.name,
                                    onDelete = { viewModel.deleteFault(f) }
                                )
                            }
                        }
                    }
                    3 -> { // Repair Types
                        val filtered = remember(repairTypes, searchQuery) {
                            if (searchQuery.isBlank()) repairTypes
                            else repairTypes.filter { it.name.contains(searchQuery, ignoreCase = true) }
                        }
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 96.dp)
                        ) {
                            items(filtered, key = { it.id }) { rt ->
                                MasterDataCard(
                                    title = rt.name,
                                    onDelete = { viewModel.deleteRepairType(rt) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ADD DIALOG
    if (showAddDialog) {
        var inputVal by remember { mutableStateOf("") }
        var brandForModel by remember { mutableStateOf(brands.firstOrNull()?.name ?: "Samsung") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add ${tabs[selectedTab]}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (selectedTab == 1) { // Adding model requires Brand
                        SmartAutocompleteField(
                            label = "Brand",
                            value = brandForModel,
                            onValueChange = { brandForModel = it },
                            suggestions = brands.map { it.name },
                            onSuggestionSelected = { brandForModel = it },
                            onAddNew = { viewModel.addBrand(it) }
                        )
                    }
                    OutlinedTextField(
                        value = inputVal,
                        onValueChange = { inputVal = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = inputVal.trim()
                        if (trimmed.isNotBlank()) {
                            when (selectedTab) {
                                0 -> viewModel.addBrand(trimmed)
                                1 -> if (brandForModel.isNotBlank()) viewModel.addModel(brandForModel, trimmed)
                                2 -> viewModel.addFault(trimmed)
                                3 -> viewModel.addRepairType(trimmed)
                            }
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                ) {
                    Text("Add", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun MasterDataCard(
    title: String,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            }
        }
    }
}
