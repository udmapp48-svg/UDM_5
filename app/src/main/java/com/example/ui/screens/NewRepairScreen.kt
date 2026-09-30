package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.RepairEntity
import com.example.ui.components.AccessoriesSelector
import com.example.ui.components.SmartAutocompleteField
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.PaymentPartial
import com.example.ui.viewmodel.RepairViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

data class RepairItemInput(
    val id: String = UUID.randomUUID().toString(),
    val repairType: String,
    val price: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewRepairScreen(
    viewModel: RepairViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCustomerHistory: (String) -> Unit = {},
    onRepairCreated: (RepairEntity) -> Unit
) {
    BackHandler { onNavigateBack() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    var pendingSaveAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingSaveAction?.invoke()
        pendingSaveAction = null
    }

    // Focus Requesters for seamless keyboard navigation
    val phoneFocusRequester = remember { FocusRequester() }
    val nameFocusRequester = remember { FocusRequester() }
    val brandFocusRequester = remember { FocusRequester() }
    val modelFocusRequester = remember { FocusRequester() }
    val repairTypeFocusRequester = remember { FocusRequester() }
    val repairPriceFocusRequester = remember { FocusRequester() }
    val advancePaymentFocusRequester = remember { FocusRequester() }

    fun safeRequestFocus(requester: FocusRequester) {
        try {
            requester.requestFocus()
        } catch (_: Exception) {
            try {
                if (!focusManager.moveFocus(FocusDirection.Down)) {
                    focusManager.moveFocus(FocusDirection.Next)
                }
            } catch (_: Exception) {}
        }
    }

    // Master lists from ViewModel
    val allBrands by viewModel.allBrands.collectAsState()
    val allRepairTypes by viewModel.allRepairTypes.collectAsState()
    val settings by viewModel.settings.collectAsState()

    // 1. Customer Details (Phone number is first!)
    var customerPhone by remember { mutableStateOf("") }
    var customerName by remember { mutableStateOf("") }

    // Previous customer search state
    var previousCustomerName by remember { mutableStateOf("") }
    var previousRepairsCount by remember { mutableIntStateOf(0) }
    var hasPreviousHistory by remember { mutableStateOf(false) }

    LaunchedEffect(customerPhone) {
        val clean = customerPhone.filter { it.isDigit() }
        if (clean.length >= 3) {
            viewModel.searchCustomerHistory(customerPhone) { cust, repairs ->
                if (repairs.isNotEmpty()) {
                    hasPreviousHistory = true
                    previousRepairsCount = repairs.size
                    val foundName = cust?.name ?: repairs.firstOrNull()?.customerName ?: ""
                    previousCustomerName = foundName
                    if (customerName.isBlank() && foundName.isNotBlank()) {
                        customerName = foundName
                    }
                } else {
                    hasPreviousHistory = false
                    previousRepairsCount = 0
                }
            }
        } else {
            hasPreviousHistory = false
            previousRepairsCount = 0
        }
    }

    // 2. Primary Device Details
    var selectedBrand by remember { mutableStateOf("") }
    var selectedModel by remember { mutableStateOf("") }

    // Models for selected brand
    var brandModels by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(selectedBrand) {
        if (selectedBrand.isNotBlank()) {
            viewModel.getModelsForBrand(selectedBrand).collect { models ->
                brandModels = models.map { it.modelName }
            }
        } else {
            brandModels = emptyList()
        }
    }

    // Multiple Repair Items Support
    val repairItems = remember { mutableStateListOf<RepairItemInput>() }

    // Inputs for the first / current repair item
    var selectedRepairType by remember { mutableStateOf("") }
    var repairPriceStr by remember { mutableStateOf("") }
    var priceNotSetMessage by remember { mutableStateOf(false) }

    // Dialog state for "Add Another Repair"
    var showAddDialog by remember { mutableStateOf(false) }
    var dialogRepairType by remember { mutableStateOf("") }
    var dialogPriceStr by remember { mutableStateOf("") }
    var dialogPriceNotSet by remember { mutableStateOf(false) }

    // Dialog state for "Edit Repair Item"
    var itemToEdit by remember { mutableStateOf<RepairItemInput?>(null) }
    var editRepairType by remember { mutableStateOf("") }
    var editPriceStr by remember { mutableStateOf("") }
    var editPriceNotSet by remember { mutableStateOf(false) }

    // Smart Price Lookup for initial item: Brand + Model + Repair Type
    LaunchedEffect(selectedBrand, selectedModel, selectedRepairType) {
        val b = selectedBrand.trim()
        val m = selectedModel.trim()
        val r = selectedRepairType.trim()
        if (b.isNotBlank() && m.isNotBlank() && r.isNotBlank()) {
            viewModel.lookupPrice(b, m, r) { price ->
                if (price != null && price > 0) {
                    repairPriceStr = if (price % 1.0 == 0.0) price.toInt().toString() else String.format(Locale.US, "%.2f", price)
                    priceNotSetMessage = false
                } else {
                    repairPriceStr = "" // Instantly clear previous price!
                    priceNotSetMessage = true
                }
            }
        } else {
            priceNotSetMessage = false
            if (r.isBlank() || m.isBlank() || b.isBlank()) {
                repairPriceStr = ""
            }
        }
    }

    // Smart Price Lookup for Dialog: Brand + Model + dialogRepairType
    LaunchedEffect(selectedBrand, selectedModel, dialogRepairType) {
        val b = selectedBrand.trim()
        val m = selectedModel.trim()
        val r = dialogRepairType.trim()
        if (b.isNotBlank() && m.isNotBlank() && r.isNotBlank()) {
            viewModel.lookupPrice(b, m, r) { price ->
                if (price != null && price > 0) {
                    dialogPriceStr = if (price % 1.0 == 0.0) price.toInt().toString() else String.format(Locale.US, "%.2f", price)
                    dialogPriceNotSet = false
                } else {
                    dialogPriceStr = ""
                    dialogPriceNotSet = true
                }
            }
        } else {
            dialogPriceNotSet = false
        }
    }

    // Smart Price Lookup for Edit Dialog: Brand + Model + editRepairType
    LaunchedEffect(selectedBrand, selectedModel, editRepairType) {
        val b = selectedBrand.trim()
        val m = selectedModel.trim()
        val r = editRepairType.trim()
        if (b.isNotBlank() && m.isNotBlank() && r.isNotBlank() && itemToEdit != null) {
            viewModel.lookupPrice(b, m, r) { price ->
                if (price != null && price > 0) {
                    editPriceStr = if (price % 1.0 == 0.0) price.toInt().toString() else String.format(Locale.US, "%.2f", price)
                    editPriceNotSet = false
                } else {
                    editPriceNotSet = true
                }
            }
        }
    }

    // When Brand or Model changes: Re-validate prices for all existing repair items
    LaunchedEffect(selectedBrand, selectedModel) {
        val b = selectedBrand.trim()
        val m = selectedModel.trim()
        if (b.isNotBlank() && m.isNotBlank() && repairItems.isNotEmpty()) {
            repairItems.forEachIndexed { index, item ->
                viewModel.lookupPrice(b, m, item.repairType) { newPrice ->
                    if (newPrice != null && newPrice > 0) {
                        repairItems[index] = item.copy(price = newPrice)
                    }
                }
            }
        }
    }

    // Optional Additional Details (IMEI, Colour, Accessories, Remarks)
    var showMoreDetails by remember { mutableStateOf(false) }
    var imei by remember { mutableStateOf("") }
    var deviceColour by remember { mutableStateOf("") }
    val selectedAccessories = remember { mutableStateListOf<String>() }
    var technicianNotes by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }

    // 3. Payment input
    var amountPaidStr by remember { mutableStateOf("") }

    // Calculations
    val totalPrice = if (repairItems.isNotEmpty()) {
        repairItems.sumOf { it.price }
    } else {
        repairPriceStr.toDoubleOrNull() ?: 0.0
    }
    val amountPaid = amountPaidStr.toDoubleOrNull() ?: 0.0
    val balance = (totalPrice - amountPaid).coerceAtLeast(0.0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "New Repair Job",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val itemsCount = if (repairItems.isNotEmpty()) repairItems.size else if (selectedRepairType.isNotBlank()) 1 else 0
                            Text(
                                text = "Total: ${settings.currency} ${String.format(Locale.US, "%,.0f", totalPrice)}" + if (itemsCount > 1) " ($itemsCount items)" else "",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (amountPaid > 0) {
                                Text(
                                    text = "Balance: ${settings.currency} ${String.format(Locale.US, "%,.0f", balance)}",
                                    fontSize = 13.sp,
                                    color = if (balance > 0) PaymentPartial else CyanAccent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Button(
                            onClick = {
                                // Validation
                                when {
                                    customerPhone.isBlank() -> {
                                        coroutineScope.launch { snackbarHostState.showSnackbar("Please enter customer phone number") }
                                    }
                                    customerName.isBlank() -> {
                                        coroutineScope.launch { snackbarHostState.showSnackbar("Please enter customer name") }
                                    }
                                    selectedBrand.isBlank() -> {
                                        coroutineScope.launch { snackbarHostState.showSnackbar("Please select or enter device brand") }
                                    }
                                    selectedModel.isBlank() -> {
                                        coroutineScope.launch { snackbarHostState.showSnackbar("Please select or enter device model") }
                                    }
                                    repairItems.isEmpty() && selectedRepairType.isBlank() -> {
                                        coroutineScope.launch { snackbarHostState.showSnackbar("Please select or enter repair type") }
                                    }
                                    totalPrice <= 0 -> {
                                        coroutineScope.launch { snackbarHostState.showSnackbar("Please enter a valid repair price") }
                                    }
                                    else -> {
                                        val itemsList = if (repairItems.isNotEmpty()) {
                                            repairItems.map { it.repairType.trim() to it.price }
                                        } else {
                                            listOf(selectedRepairType.trim() to totalPrice)
                                        }
                                        val faultSummary = itemsList.joinToString(", ") { it.first }

                                        val doSave = {
                                            viewModel.createRepair(
                                                customerName = customerName,
                                                customerPhone = customerPhone,
                                                brand = selectedBrand,
                                                model = selectedModel,
                                                imei = imei,
                                                deviceColour = deviceColour,
                                                accessories = selectedAccessories.joinToString(", "),
                                                fault = faultSummary,
                                                technicianNotes = technicianNotes,
                                                remarks = remarks,
                                                repairItems = itemsList,
                                                initialPaymentAmount = amountPaid,
                                                onSuccess = { created ->
                                                    onRepairCreated(created)
                                                }
                                            )
                                        }

                                        val hasSmsPermission = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.SEND_SMS
                                        ) == PackageManager.PERMISSION_GRANTED

                                        if (settings.autoRegistrationSms && !hasSmsPermission) {
                                            pendingSaveAction = doSave
                                            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                                        } else {
                                            doSave()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(50.dp)
                                .testTag("save_repair_button")
                        ) {
                            Text(
                                "SAVE REPAIR",
                                color = Color(0xFF090E1A),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // 1. CUSTOMER DETAILS (Phone Number First!)
            item {
                SectionCard(title = "1. CUSTOMER DETAILS") {
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it },
                        label = { Text("Customer Phone Number *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { safeRequestFocus(nameFocusRequester) }
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(phoneFocusRequester)
                            .testTag("input_customer_phone"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    if (settings.autoRegistrationSms) {
                        Text(
                            text = "💬 Customer will receive confirmation SMS automatically on save",
                            fontSize = 11.sp,
                            color = CyanAccent,
                            modifier = Modifier.padding(top = 4.dp, start = 2.dp)
                        )
                    }

                    // VERY SMALL COMPACT ROW if previous customer history exists
                    if (hasPreviousHistory && previousRepairsCount > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = CyanAccent.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToCustomerHistory(customerPhone) }
                                .testTag("compact_previous_customer_row")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = CyanAccent,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${previousCustomerName.ifBlank { "Previous Customer" }} · $previousRepairsCount Previous ${if (previousRepairsCount == 1) "Repair" else "Repairs"}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = CyanAccent,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = "Open History",
                                    tint = CyanAccent,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = customerName,
                        onValueChange = { customerName = it },
                        label = { Text("Customer Name *") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { safeRequestFocus(brandFocusRequester) }
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(nameFocusRequester)
                            .testTag("input_customer_name"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            }

            // 2. DEVICE DETAILS
            item {
                SectionCard(title = "2. DEVICE DETAILS") {
                    // Brand Autocomplete
                    SmartAutocompleteField(
                        label = "Brand *",
                        value = selectedBrand,
                        onValueChange = { selectedBrand = it },
                        suggestions = allBrands.map { it.name },
                        onSuggestionSelected = { brand ->
                            selectedBrand = brand
                            safeRequestFocus(modelFocusRequester)
                        },
                        onAddNew = { newBrand ->
                            viewModel.addBrand(newBrand)
                            safeRequestFocus(modelFocusRequester)
                        },
                        focusRequester = brandFocusRequester,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { safeRequestFocus(modelFocusRequester) }
                        ),
                        placeholder = "e.g. Samsung, Apple, Xiaomi",
                        testTag = "input_brand_autocomplete"
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Model Autocomplete (Filtered by selected Brand)
                    SmartAutocompleteField(
                        label = "Model *",
                        value = selectedModel,
                        onValueChange = { selectedModel = it },
                        suggestions = brandModels,
                        onSuggestionSelected = { model ->
                            selectedModel = model
                            safeRequestFocus(repairTypeFocusRequester)
                        },
                        onAddNew = { newModel ->
                            if (selectedBrand.isNotBlank()) {
                                viewModel.addModel(selectedBrand, newModel)
                            }
                            safeRequestFocus(repairTypeFocusRequester)
                        },
                        focusRequester = modelFocusRequester,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { safeRequestFocus(repairTypeFocusRequester) }
                        ),
                        placeholder = if (selectedBrand.isBlank()) "Select Brand first" else "e.g. Galaxy M02, iPhone 11",
                        testTag = "input_model_autocomplete"
                    )
                }
            }

            // 3. REPAIR ITEMS (Supports Multiple Repair Types under ONE Job Number)
            item {
                SectionCard(title = "3. REPAIR ITEMS") {
                    if (repairItems.isEmpty()) {
                        // First repair item input flow
                        SmartAutocompleteField(
                            label = "Repair Type / Service *",
                            value = selectedRepairType,
                            onValueChange = { selectedRepairType = it },
                            suggestions = allRepairTypes.map { it.name },
                            onSuggestionSelected = { type ->
                                selectedRepairType = type
                                safeRequestFocus(repairPriceFocusRequester)
                            },
                            onAddNew = { newType ->
                                viewModel.addRepairType(newType)
                                safeRequestFocus(repairPriceFocusRequester)
                            },
                            focusRequester = repairTypeFocusRequester,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(
                                onNext = { safeRequestFocus(repairPriceFocusRequester) }
                            ),
                            placeholder = "e.g. Display Replacement, Charging Port",
                            testTag = "input_repair_type_autocomplete"
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Price Not Set Warning
                        if (priceNotSetMessage && selectedBrand.isNotBlank() && selectedModel.isNotBlank() && selectedRepairType.isNotBlank()) {
                            Surface(
                                color = PaymentPartial.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = PaymentPartial, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Price not set for this model and repair type.",
                                        color = PaymentPartial,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = repairPriceStr,
                            onValueChange = { repairPriceStr = it },
                            label = { Text("Repair Price (${settings.currency}) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(
                                onNext = { safeRequestFocus(advancePaymentFocusRequester) }
                            ),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(repairPriceFocusRequester)
                                .testTag("input_repair_price"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanAccent,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        // SAVE THIS PRICE button if price was not set
                        if (priceNotSetMessage && selectedBrand.isNotBlank() && selectedModel.isNotBlank() && selectedRepairType.isNotBlank()) {
                            val enteredPrice = repairPriceStr.toDoubleOrNull()
                            if (enteredPrice != null && enteredPrice > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        viewModel.savePrice(selectedBrand, selectedModel, selectedRepairType, enteredPrice) {
                                            priceNotSetMessage = false
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Price saved for future auto-fills!")
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("save_this_price_button")
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("SAVE THIS PRICE FOR FUTURE JOBS", fontSize = 12.sp, color = CyanAccent, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // + Add Another Repair button (commits first item and opens add dialog)
                        OutlinedButton(
                            onClick = {
                                val p = repairPriceStr.toDoubleOrNull() ?: 0.0
                                if (selectedRepairType.isBlank()) {
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Please select a repair type first") }
                                } else if (p <= 0) {
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Please enter a valid price for this repair") }
                                } else {
                                    repairItems.add(RepairItemInput(repairType = selectedRepairType.trim(), price = p))
                                    selectedRepairType = ""
                                    repairPriceStr = ""
                                    priceNotSetMessage = false
                                    dialogRepairType = ""
                                    dialogPriceStr = ""
                                    dialogPriceNotSet = false
                                    showAddDialog = true
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("add_another_repair_btn"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+ Add Another Repair", fontWeight = FontWeight.Bold, color = CyanAccent, fontSize = 13.sp)
                        }
                    } else {
                        // Displays all committed repair items with Edit / Delete
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            repairItems.forEachIndexed { index, item ->
                                Surface(
                                    color = MaterialTheme.colorScheme.background,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${index + 1}. ${item.repairType}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${settings.currency} ${String.format(Locale.US, "%,.0f", item.price)}",
                                                color = CyanAccent,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    itemToEdit = item
                                                    editRepairType = item.repairType
                                                    editPriceStr = if (item.price % 1.0 == 0.0) item.price.toInt().toString() else String.format(Locale.US, "%.2f", item.price)
                                                    editPriceNotSet = false
                                                },
                                                modifier = Modifier.size(32.dp).testTag("edit_item_${index}")
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Edit", tint = CyanAccent, modifier = Modifier.size(18.dp))
                                            }

                                            Spacer(modifier = Modifier.width(4.dp))

                                            IconButton(
                                                onClick = {
                                                    repairItems.removeAt(index)
                                                },
                                                modifier = Modifier.size(32.dp).testTag("delete_item_${index}")
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            OutlinedButton(
                                onClick = {
                                    dialogRepairType = ""
                                    dialogPriceStr = ""
                                    dialogPriceNotSet = false
                                    showAddDialog = true
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().testTag("add_another_repair_btn"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("+ Add Another Repair", fontWeight = FontWeight.Bold, color = CyanAccent, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // 4. OPTIONAL ADDITIONAL DETAILS (IMEI, Colour, Accessories, Remarks)
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        OutlinedButton(
                            onClick = { showMoreDetails = !showMoreDetails },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("toggle_more_details_btn"),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
                        ) {
                            Icon(
                                imageVector = if (showMoreDetails) Icons.Default.ExpandLess else Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = CyanAccent
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (showMoreDetails) "− Hide Additional Details" else "+ More Details (IMEI, Colour, Accessories, Remarks)",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = CyanAccent
                            )
                        }

                        AnimatedVisibility(visible = showMoreDetails) {
                            Column(modifier = Modifier.padding(top = 14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = imei,
                                        onValueChange = { imei = it },
                                        label = { Text("IMEI (Optional)") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("input_imei"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = CyanAccent,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                        )
                                    )
                                    OutlinedTextField(
                                        value = deviceColour,
                                        onValueChange = { deviceColour = it },
                                        label = { Text("Device Colour") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("input_device_colour"),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = CyanAccent,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Accessories Multi-select
                                AccessoriesSelector(
                                    selectedAccessories = selectedAccessories.toSet(),
                                    onToggleAccessory = { item ->
                                        if (selectedAccessories.contains(item)) {
                                            selectedAccessories.remove(item)
                                        } else {
                                            selectedAccessories.add(item)
                                        }
                                    }
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                OutlinedTextField(
                                    value = remarks,
                                    onValueChange = { remarks = it },
                                    label = { Text("Customer Remarks") },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_remarks"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CyanAccent,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = technicianNotes,
                                    onValueChange = { technicianNotes = it },
                                    label = { Text("Technician Notes (Internal)") },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_technician_notes"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CyanAccent,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // 5. PAYMENT DETAILS
            item {
                SectionCard(title = "4. PAYMENT DETAILS") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Repair Price:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${settings.currency} ${String.format(Locale.US, "%,.0f", totalPrice)}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = amountPaidStr,
                        onValueChange = { amountPaidStr = it },
                        label = { Text("Advance / Deposit Paid (${settings.currency})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(advancePaymentFocusRequester)
                            .testTag("input_advance_payment"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Remaining Balance:", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${settings.currency} ${String.format(Locale.US, "%,.0f", balance)}",
                            fontWeight = FontWeight.Bold,
                            color = if (balance > 0) PaymentPartial else CyanAccent,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    // DIALOG: ADD ANOTHER REPAIR
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    text = "Add Another Repair",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (selectedBrand.isNotBlank() && selectedModel.isNotBlank()) {
                        Text(
                            text = "Device: $selectedBrand $selectedModel",
                            fontSize = 12.sp,
                            color = CyanAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    SmartAutocompleteField(
                        label = "Repair Type / Service *",
                        value = dialogRepairType,
                        onValueChange = { dialogRepairType = it },
                        suggestions = allRepairTypes.map { it.name },
                        onSuggestionSelected = { dialogRepairType = it },
                        onAddNew = { viewModel.addRepairType(it) },
                        placeholder = "e.g. Battery, Charging Port",
                        testTag = "dialog_add_repair_type_input"
                    )

                    if (dialogPriceNotSet && selectedBrand.isNotBlank() && selectedModel.isNotBlank() && dialogRepairType.isNotBlank()) {
                        Surface(
                            color = PaymentPartial.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = PaymentPartial, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Price not set for this model and repair type.",
                                    color = PaymentPartial,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = dialogPriceStr,
                        onValueChange = { dialogPriceStr = it },
                        label = { Text("Price (${settings.currency}) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("dialog_add_price_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    if (dialogPriceNotSet && selectedBrand.isNotBlank() && selectedModel.isNotBlank() && dialogRepairType.isNotBlank()) {
                        val p = dialogPriceStr.toDoubleOrNull()
                        if (p != null && p > 0) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.savePrice(selectedBrand, selectedModel, dialogRepairType, p) {
                                        dialogPriceNotSet = false
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Price saved for future auto-fills!")
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("SAVE THIS PRICE", fontSize = 11.sp, color = CyanAccent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = dialogPriceStr.toDoubleOrNull() ?: 0.0
                        if (dialogRepairType.isNotBlank() && p > 0) {
                            repairItems.add(RepairItemInput(repairType = dialogRepairType.trim(), price = p))
                            showAddDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("dialog_confirm_add_repair")
                ) {
                    Text("Add Repair", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // DIALOG: EDIT REPAIR ITEM
    if (itemToEdit != null) {
        AlertDialog(
            onDismissRequest = { itemToEdit = null },
            title = {
                Text(
                    text = "Edit Repair Item",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SmartAutocompleteField(
                        label = "Repair Type / Service",
                        value = editRepairType,
                        onValueChange = { editRepairType = it },
                        suggestions = allRepairTypes.map { it.name },
                        onSuggestionSelected = { editRepairType = it },
                        onAddNew = { viewModel.addRepairType(it) },
                        testTag = "dialog_edit_repair_type_input"
                    )

                    if (editPriceNotSet && selectedBrand.isNotBlank() && selectedModel.isNotBlank() && editRepairType.isNotBlank()) {
                        Surface(
                            color = PaymentPartial.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = PaymentPartial, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Price not set for this model and repair type.",
                                    color = PaymentPartial,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editPriceStr,
                        onValueChange = { editPriceStr = it },
                        label = { Text("Price (${settings.currency})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("dialog_edit_price_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanAccent,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    if (editPriceNotSet && selectedBrand.isNotBlank() && selectedModel.isNotBlank() && editRepairType.isNotBlank()) {
                        val p = editPriceStr.toDoubleOrNull()
                        if (p != null && p > 0) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.savePrice(selectedBrand, selectedModel, editRepairType, p) {
                                        editPriceNotSet = false
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Price saved for future auto-fills!")
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("SAVE THIS PRICE", fontSize = 11.sp, color = CyanAccent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val p = editPriceStr.toDoubleOrNull() ?: 0.0
                        if (editRepairType.isNotBlank() && p > 0) {
                            val idx = repairItems.indexOfFirst { it.id == itemToEdit?.id }
                            if (idx != -1) {
                                repairItems[idx] = repairItems[idx].copy(repairType = editRepairType.trim(), price = p)
                            }
                            itemToEdit = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("dialog_confirm_edit_repair")
                ) {
                    Text("Save Changes", color = Color(0xFF090E1A), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                ),
                color = CyanAccent,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}
