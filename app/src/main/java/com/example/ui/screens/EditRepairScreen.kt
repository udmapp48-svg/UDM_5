package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AccessoriesSelector
import com.example.ui.components.SmartAutocompleteField
import com.example.ui.theme.CyanAccent
import com.example.ui.viewmodel.RepairViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRepairScreen(
    repairId: Long,
    viewModel: RepairViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler { onNavigateBack() }

    val allRepairs by viewModel.allRepairs.collectAsState()
    val repair = allRepairs.find { it.id == repairId }
    val allBrands by viewModel.allBrands.collectAsState()
    val allFaults by viewModel.allFaults.collectAsState()

    if (repair == null) {
        onNavigateBack()
        return
    }

    var customerName by remember { mutableStateOf(repair.customerName) }
    var customerPhone by remember { mutableStateOf(repair.customerPhone) }
    var brand by remember { mutableStateOf(repair.brand) }
    var model by remember { mutableStateOf(repair.model) }
    var imei by remember { mutableStateOf(repair.imei) }
    var deviceColour by remember { mutableStateOf(repair.deviceColour) }
    var fault by remember { mutableStateOf(repair.fault) }
    var technicianNotes by remember { mutableStateOf(repair.technicianNotes) }
    var remarks by remember { mutableStateOf(repair.remarks) }

    val initialAcc = remember {
        repair.accessories.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }
    val selectedAccessories = remember { mutableStateListOf<String>().apply { addAll(initialAcc) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Job #${repair.jobNumber}", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SectionCard(title = "CUSTOMER & DEVICE") {
                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text("Customer Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it },
                        label = { Text("Phone Number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    SmartAutocompleteField(
                        label = "Brand",
                        value = brand,
                        onValueChange = { brand = it },
                        suggestions = allBrands.map { it.name },
                        onSuggestionSelected = { brand = it },
                        onAddNew = { viewModel.addBrand(it) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = imei,
                            onValueChange = { imei = it },
                            label = { Text("IMEI") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = deviceColour,
                            onValueChange = { deviceColour = it },
                            label = { Text("Colour") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    AccessoriesSelector(
                        selectedAccessories = selectedAccessories.toSet(),
                        onToggleAccessory = { item ->
                            if (selectedAccessories.contains(item)) selectedAccessories.remove(item)
                            else selectedAccessories.add(item)
                        }
                    )
                }
            }

            item {
                SectionCard(title = "FAULT & NOTES") {
                    SmartAutocompleteField(
                        label = "Fault / Complaint",
                        value = fault,
                        onValueChange = { fault = it },
                        suggestions = allFaults.map { it.name },
                        onSuggestionSelected = { fault = it },
                        onAddNew = { viewModel.addFault(it) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = technicianNotes,
                        onValueChange = { technicianNotes = it },
                        label = { Text("Technician Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = remarks,
                        onValueChange = { remarks = it },
                        label = { Text("Remarks") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        viewModel.updateRepairDetails(
                            repairId = repair.id,
                            customerName = customerName,
                            customerPhone = customerPhone,
                            brand = brand,
                            model = model,
                            imei = imei,
                            deviceColour = deviceColour,
                            accessories = selectedAccessories.joinToString(", "),
                            fault = fault,
                            technicianNotes = technicianNotes,
                            remarks = remarks,
                            onComplete = onNavigateBack
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("SAVE CHANGES", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
    }
}
