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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PriceChange
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.PriceEntity
import com.example.ui.components.SearchInputField
import com.example.ui.components.SmartAutocompleteField
import com.example.ui.theme.CyanAccent
import com.example.ui.viewmodel.RepairViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceManagerScreen(
    viewModel: RepairViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val prices by viewModel.allPrices.collectAsState()
    val allBrands by viewModel.allBrands.collectAsState()
    val allRepairTypes by viewModel.allRepairTypes.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showAddEditDialog by remember { mutableStateOf(false) }
    var priceToEdit by remember { mutableStateOf<PriceEntity?>(null) }
    var priceToDelete by remember { mutableStateOf<PriceEntity?>(null) }

    val filteredPrices = remember(prices, searchQuery) {
        if (searchQuery.isBlank()) prices
        else prices.filter {
            it.brand.contains(searchQuery, ignoreCase = true) ||
            it.model.contains(searchQuery, ignoreCase = true) ||
            it.repairType.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Price Manager", fontWeight = FontWeight.Bold) },
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
                onClick = {
                    priceToEdit = null
                    showAddEditDialog = true
                },
                containerColor = CyanAccent,
                modifier = Modifier.testTag("add_price_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Price", tint = Color(0xFF090E1A))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SearchInputField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholderText = "Search by Brand, Model, or Repair Type...",
                testTag = "price_search_input"
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "${filteredPrices.size} price records configured",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (filteredPrices.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No repair prices found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredPrices, key = { it.id }) { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${item.brand} ${item.model}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = item.repairType,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${settings.currency} ${String.format(Locale.US, "%,.2f", item.price)}",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        color = CyanAccent
                                    )
                                }

                                Row {
                                    IconButton(
                                        onClick = {
                                            priceToEdit = item
                                            showAddEditDialog = true
                                        }
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = CyanAccent, modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = { priceToDelete = item }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ADD / EDIT PRICE DIALOG
    if (showAddEditDialog) {
        var dBrand by remember { mutableStateOf(priceToEdit?.brand ?: "") }
        var dModel by remember { mutableStateOf(priceToEdit?.model ?: "") }
        var dRepairType by remember { mutableStateOf(priceToEdit?.repairType ?: "") }
        var dPriceStr by remember { mutableStateOf(priceToEdit?.price?.toInt()?.toString() ?: "") }
        var brandModels by remember { mutableStateOf<List<String>>(emptyList()) }

        LaunchedEffect(dBrand) {
            if (dBrand.isNotBlank()) {
                viewModel.getModelsForBrand(dBrand).collect { list ->
                    brandModels = list.map { it.modelName }
                }
            }
        }

        AlertDialog(
            onDismissRequest = { showAddEditDialog = false },
            title = { Text(if (priceToEdit == null) "Add Service Price" else "Edit Price") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmartAutocompleteField(
                        label = "Brand",
                        value = dBrand,
                        onValueChange = { dBrand = it },
                        suggestions = allBrands.map { it.name },
                        onSuggestionSelected = { dBrand = it },
                        onAddNew = { viewModel.addBrand(it) }
                    )

                    SmartAutocompleteField(
                        label = "Model",
                        value = dModel,
                        onValueChange = { dModel = it },
                        suggestions = brandModels,
                        onSuggestionSelected = { dModel = it },
                        onAddNew = {
                            if (dBrand.isNotBlank()) viewModel.addModel(dBrand, it)
                        }
                    )

                    SmartAutocompleteField(
                        label = "Repair Type",
                        value = dRepairType,
                        onValueChange = { dRepairType = it },
                        suggestions = allRepairTypes.map { it.name },
                        onSuggestionSelected = { dRepairType = it },
                        onAddNew = { viewModel.addRepairType(it) }
                    )

                    OutlinedTextField(
                        value = dPriceStr,
                        onValueChange = { dPriceStr = it },
                        label = { Text("Price (${settings.currency})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = dPriceStr.toDoubleOrNull() ?: 0.0
                        if (dBrand.isNotBlank() && dModel.isNotBlank() && dRepairType.isNotBlank() && p > 0) {
                            viewModel.savePrice(dBrand, dModel, dRepairType, p) {
                                showAddEditDialog = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
                ) {
                    Text("Save", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddEditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // DELETE CONFIRMATION DIALOG
    if (priceToDelete != null) {
        AlertDialog(
            onDismissRequest = { priceToDelete = null },
            title = { Text("Delete Price?") },
            text = {
                Text("Delete price for ${priceToDelete?.brand} ${priceToDelete?.model} - ${priceToDelete?.repairType}? Note: Existing completed/open repair records will NOT be modified.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        priceToDelete?.let { viewModel.deletePrice(it) }
                        priceToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { priceToDelete = null }) { Text("Cancel") }
            }
        )
    }
}
