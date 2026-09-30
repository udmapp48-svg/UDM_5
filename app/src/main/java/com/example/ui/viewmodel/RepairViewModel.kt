package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.BrandEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.FaultEntity
import com.example.data.entity.ModelEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.PriceEntity
import com.example.data.entity.RepairEntity
import com.example.data.entity.RepairItemEntity
import com.example.data.entity.RepairTypeEntity
import com.example.data.entity.ScannerLearningEntity
import com.example.data.entity.StatusHistoryEntity
import com.example.data.repository.RepairRepository
import com.example.data.repository.SmsEvent
import com.example.data.repository.SmsSendResult
import com.example.util.BackupManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DashboardStats(
    val totalRepairs: Int = 0,
    val todayNewRepairs: Int = 0,
    val receivedCount: Int = 0,
    val checkingCount: Int = 0,
    val repairingCount: Int = 0,
    val readyCount: Int = 0,
    val deliveredCount: Int = 0,
    val cancelledCount: Int = 0,
    val outstandingBalance: Double = 0.0,
    val dueBalanceJobsCount: Int = 0,
    val todayIncome: Double = 0.0,
    val deliveredTodayCount: Int = 0,
    val pendingJobsCount: Int = 0
)

class RepairViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val repository = RepairRepository(db, application)

    val allRepairs: StateFlow<List<RepairEntity>> = repository.allRepairs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPayments: StateFlow<List<PaymentEntity>> = repository.allPayments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCustomers: StateFlow<List<CustomerEntity>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBrands: StateFlow<List<BrandEntity>> = repository.allBrands
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFaults: StateFlow<List<FaultEntity>> = repository.allFaults
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRepairTypes: StateFlow<List<RepairTypeEntity>> = repository.allRepairTypes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPrices: StateFlow<List<PriceEntity>> = repository.allPrices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<AppSettingsEntity> = repository.settingsFlow
        .combine(MutableStateFlow(AppSettingsEntity())) { current, default ->
            current ?: default
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettingsEntity())

    val scannerLearningRecords: StateFlow<List<ScannerLearningEntity>> = repository.scannerLearningRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val scannerLearningCount: StateFlow<Int> = repository.scannerLearningCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val smsEvents: SharedFlow<SmsEvent> = repository.smsEvents

    // Filter states for Jobs Screen
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusFilter = MutableStateFlow("ALL")
    val statusFilter: StateFlow<String> = _statusFilter.asStateFlow()

    private val _brandFilter = MutableStateFlow("ALL")
    val brandFilter: StateFlow<String> = _brandFilter.asStateFlow()

    private val _paymentFilter = MutableStateFlow("ALL")
    val paymentFilter: StateFlow<String> = _paymentFilter.asStateFlow()

    // Filtered repairs
    val filteredRepairs: StateFlow<List<RepairEntity>> = combine(
        allRepairs,
        _searchQuery,
        _statusFilter,
        _brandFilter,
        _paymentFilter
    ) { repairs, query, status, brand, payment ->
        repairs.filter { repair ->
            val matchesQuery = query.isBlank() ||
                repair.jobNumber.contains(query, ignoreCase = true) ||
                repair.customerName.contains(query, ignoreCase = true) ||
                repair.customerPhone.contains(query, ignoreCase = true) ||
                repair.brand.contains(query, ignoreCase = true) ||
                repair.model.contains(query, ignoreCase = true) ||
                repair.imei.contains(query, ignoreCase = true) ||
                repair.fault.contains(query, ignoreCase = true)

            val matchesStatus = when (status) {
                "ALL" -> true
                "DUE_BALANCE" -> repair.balance > 0 && repair.status != "CANCELLED"
                else -> repair.status.equals(status, ignoreCase = true)
            }

            val matchesBrand = brand == "ALL" || repair.brand.equals(brand, ignoreCase = true)

            val matchesPayment = when (payment) {
                "ALL" -> true
                else -> repair.paymentStatus.equals(payment, ignoreCase = true)
            }

            matchesQuery && matchesStatus && matchesBrand && matchesPayment
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dashboard Statistics
    val dashboardStats: StateFlow<DashboardStats> = combine(
        allRepairs,
        allPayments
    ) { repairs, payments ->
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        var received = 0
        var checking = 0
        var repairing = 0
        var ready = 0
        var delivered = 0
        var cancelled = 0
        var todayNew = 0
        var outstandingBal = 0.0
        var dueBalanceJobs = 0
        var deliveredToday = 0

        for (r in repairs) {
            when (r.status.uppercase(Locale.getDefault())) {
                "RECEIVED" -> received++
                "CHECKING" -> checking++
                "REPAIRING" -> repairing++
                "READY" -> ready++
                "DELIVERED" -> delivered++
                "CANCELLED" -> cancelled++
            }
            if (r.receivedDate == todayStr) {
                todayNew++
            }
            if (r.status != "CANCELLED" && r.balance > 0) {
                outstandingBal += r.balance
                dueBalanceJobs++
            }
            if (r.status == "DELIVERED" && r.receivedDate == todayStr) {
                deliveredToday++
            }
        }

        // Today's Income ONLY from actual payments received today
        val todayIncome = payments
            .filter { it.date == todayStr }
            .sumOf { it.amount }

        DashboardStats(
            totalRepairs = repairs.size,
            todayNewRepairs = todayNew,
            receivedCount = received,
            checkingCount = checking,
            repairingCount = repairing,
            readyCount = ready,
            deliveredCount = delivered,
            cancelledCount = cancelled,
            outstandingBalance = outstandingBal,
            dueBalanceJobsCount = dueBalanceJobs,
            todayIncome = todayIncome,
            deliveredTodayCount = deliveredToday,
            pendingJobsCount = received + checking + repairing
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardStats())

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setStatusFilter(status: String) { _statusFilter.value = status }
    fun setBrandFilter(brand: String) { _brandFilter.value = brand }
    fun setPaymentFilter(payment: String) { _paymentFilter.value = payment }

    fun getModelsForBrand(brand: String): Flow<List<ModelEntity>> = repository.getModelsForBrand(brand)
    fun getItemsForRepair(repairId: Long): Flow<List<RepairItemEntity>> = repository.getItemsForRepair(repairId)
    fun getPaymentsForRepair(repairId: Long): Flow<List<PaymentEntity>> = repository.getPaymentsForRepair(repairId)
    fun getHistoryForRepair(repairId: Long): Flow<List<StatusHistoryEntity>> = repository.getHistoryForRepair(repairId)

    fun createRepair(
        customerName: String,
        customerPhone: String,
        brand: String,
        model: String,
        imei: String,
        deviceColour: String,
        accessories: String,
        fault: String,
        technicianNotes: String,
        remarks: String,
        repairItems: List<Pair<String, Double>>,
        initialPaymentAmount: Double,
        onSuccess: (RepairEntity) -> Unit
    ) {
        viewModelScope.launch {
            val repair = repository.createRepair(
                customerName = customerName,
                customerPhone = customerPhone,
                brand = brand,
                model = model,
                imei = imei,
                deviceColour = deviceColour,
                accessories = accessories,
                fault = fault,
                technicianNotes = technicianNotes,
                remarks = remarks,
                repairItems = repairItems,
                initialPaymentAmount = initialPaymentAmount
            )
            onSuccess(repair)
        }
    }

    fun addPayment(repairId: Long, amount: Double, onComplete: (RepairEntity?) -> Unit = {}) {
        viewModelScope.launch {
            val updated = repository.addPayment(repairId, amount)
            onComplete(updated)
        }
    }

    fun updatePayment(paymentId: Long, newAmount: Double, onComplete: (RepairEntity?) -> Unit = {}) {
        viewModelScope.launch {
            val updated = repository.updatePayment(paymentId, newAmount)
            onComplete(updated)
        }
    }

    fun deletePayment(paymentId: Long, onComplete: (RepairEntity?) -> Unit = {}) {
        viewModelScope.launch {
            val updated = repository.deletePayment(paymentId)
            onComplete(updated)
        }
    }

    fun addRepairItem(repairId: Long, repairType: String, price: Double, onComplete: (RepairEntity?) -> Unit = {}) {
        viewModelScope.launch {
            val updated = repository.addRepairItem(repairId, repairType, price)
            onComplete(updated)
        }
    }

    fun removeRepairItem(itemId: Long, repairId: Long, onComplete: (RepairEntity?) -> Unit = {}) {
        viewModelScope.launch {
            val updated = repository.removeRepairItem(itemId, repairId)
            onComplete(updated)
        }
    }

    fun updateRepairItem(itemId: Long, repairId: Long, newRepairType: String, newPrice: Double, onComplete: (RepairEntity?) -> Unit = {}) {
        viewModelScope.launch {
            val updated = repository.updateRepairItem(itemId, repairId, newRepairType, newPrice)
            onComplete(updated)
        }
    }

    fun updateStatus(repairId: Long, newStatus: String, notes: String = "", onComplete: (RepairEntity?) -> Unit = {}) {
        viewModelScope.launch {
            val updated = repository.updateStatus(repairId, newStatus, notes)
            onComplete(updated)
        }
    }

    fun sendReadySmsExplicit(repair: RepairEntity, onResult: (SmsSendResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.sendReadySmsExplicit(repair)
            onResult(result)
        }
    }

    fun sendRegistrationSmsExplicit(repair: RepairEntity, onResult: (SmsSendResult) -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.sendRegistrationSmsExplicit(repair)
            onResult(result)
        }
    }

    fun updateRepairDetails(
        repairId: Long,
        customerName: String,
        customerPhone: String,
        brand: String,
        model: String,
        imei: String,
        deviceColour: String,
        accessories: String,
        fault: String,
        technicianNotes: String,
        remarks: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            repository.updateRepairDetails(
                repairId, customerName, customerPhone, brand, model,
                imei, deviceColour, accessories, fault, technicianNotes, remarks
            )
            onComplete()
        }
    }

    fun deleteRepair(repairId: Long, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.deleteRepair(repairId)
            onComplete()
        }
    }

    fun searchCustomerHistory(phone: String, onResult: (CustomerEntity?, List<RepairEntity>) -> Unit) {
        viewModelScope.launch {
            val result = repository.getCustomerSummaryByPhone(phone)
            onResult(result.first, result.second)
        }
    }

    fun lookupPrice(brand: String, model: String, repairType: String, onResult: (Double?) -> Unit) {
        viewModelScope.launch {
            val price = repository.lookupPrice(brand, model, repairType)
            onResult(price)
        }
    }

    fun savePrice(brand: String, model: String, repairType: String, price: Double, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.savePrice(brand, model, repairType, price)
            onComplete()
        }
    }

    fun deletePrice(price: PriceEntity) {
        viewModelScope.launch { repository.deletePrice(price) }
    }

    fun addBrand(name: String) {
        viewModelScope.launch { repository.addBrandIfNew(name) }
    }

    fun addModel(brand: String, model: String) {
        viewModelScope.launch { repository.addModelIfNew(brand, model) }
    }

    fun addFault(name: String) {
        viewModelScope.launch { repository.addFaultIfNew(name) }
    }

    fun addRepairType(name: String) {
        viewModelScope.launch { repository.addRepairTypeIfNew(name) }
    }

    fun deleteBrand(brand: BrandEntity) {
        viewModelScope.launch { repository.deleteBrand(brand) }
    }

    fun deleteModel(model: ModelEntity) {
        viewModelScope.launch { repository.deleteModel(model) }
    }

    fun deleteFault(fault: FaultEntity) {
        viewModelScope.launch { repository.deleteFault(fault) }
    }

    fun deleteRepairType(type: RepairTypeEntity) {
        viewModelScope.launch { repository.deleteRepairType(type) }
    }

    fun updateSettings(settings: AppSettingsEntity) {
        viewModelScope.launch { repository.updateSettings(settings) }
    }

    fun exportBackup(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val data = repository.getAllDataForBackup()
            val json = com.example.util.BackupManager.exportToJson(data)
            onResult(json)
        }
    }

    fun restoreBackup(jsonString: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val data = com.example.util.BackupManager.parseFromJson(jsonString)
                repository.restoreData(data, clearExisting = true)
                onResult(true, "Restored ${data.repairs.size} repairs, ${data.payments.size} payments, and settings successfully!")
            } catch (e: Exception) {
                e.printStackTrace()
                onResult(false, e.localizedMessage ?: "Failed to restore backup")
            }
        }
    }

    fun createBackupFile(context: android.content.Context, onResult: (com.example.util.BackupFileHelper.BackupFileResult) -> Unit) {
        viewModelScope.launch {
            val data = repository.getAllDataForBackup()
            val json = com.example.util.BackupManager.exportToJson(data)
            val result = com.example.util.BackupFileHelper.saveBackupToFile(context, json)
            onResult(result)
        }
    }

    fun loadLocalBackupFiles(context: android.content.Context, onResult: (List<com.example.util.BackupFileHelper.BackupFileInfo>) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val list = com.example.util.BackupFileHelper.getAvailableBackupFiles(context)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                onResult(list)
            }
        }
    }

    fun restoreFromFile(file: java.io.File, onResult: (Boolean, String, com.example.util.BackupSummary?) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val content = file.readText(Charsets.UTF_8)
                val validation = com.example.util.BackupManager.validateJson(content)
                when (validation) {
                    is com.example.util.BackupValidationResult.Valid -> {
                        repository.restoreData(validation.data, clearExisting = true)
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            onResult(true, "Restored ${validation.summary.repairCount} repairs and shop settings successfully!", validation.summary)
                        }
                    }
                    is com.example.util.BackupValidationResult.Invalid -> {
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            onResult(false, validation.reason, null)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(false, e.localizedMessage ?: "Failed to read backup file", null)
                }
            }
        }
    }

    fun restoreFromUri(context: android.content.Context, uri: android.net.Uri, onResult: (Boolean, String, com.example.util.BackupSummary?) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val content = com.example.util.BackupFileHelper.readFromUri(context, uri)
                val validation = com.example.util.BackupManager.validateJson(content)
                when (validation) {
                    is com.example.util.BackupValidationResult.Valid -> {
                        repository.restoreData(validation.data, clearExisting = true)
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            onResult(true, "Restored ${validation.summary.repairCount} repairs and shop settings successfully!", validation.summary)
                        }
                    }
                    is com.example.util.BackupValidationResult.Invalid -> {
                        withContext(kotlinx.coroutines.Dispatchers.Main) {
                            onResult(false, validation.reason, null)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(false, e.localizedMessage ?: "Failed to read backup from selected file", null)
                }
            }
        }
    }

    fun deleteBackupFile(file: java.io.File, onComplete: () -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                file.delete()
            } catch (_: Exception) {}
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                onComplete()
            }
        }
    }

    suspend fun findRepairByJobNumber(jobNumber: String): RepairEntity? {
        return repository.getRepairByJobNumber(jobNumber)
    }

    fun recordScannerCorrection(
        rawOcrText: String,
        confirmedJobNumber: String,
        detectedJobNumber: String = "",
        confidence: Float = 0f,
        preprocessingMethod: String = ""
    ) {
        viewModelScope.launch {
            val cleanRaw = rawOcrText.trim()
            val cleanConfirmed = confirmedJobNumber.trim()
            if (cleanRaw.isBlank() || cleanConfirmed.isBlank()) return@launch

            // Build character substitution mapping string e.g. "O->0,I->1,Z->2"
            val substitutions = buildSubstitutionsSummary(cleanRaw, cleanConfirmed)

            repository.saveScannerLearning(
                rawOcrText = cleanRaw,
                confirmedJobNumber = cleanConfirmed,
                detectedJobNumber = detectedJobNumber.trim(),
                confidence = confidence,
                preprocessingMethod = preprocessingMethod,
                characterSubstitutions = substitutions
            )
        }
    }

    private fun buildSubstitutionsSummary(raw: String, confirmed: String): String {
        val pairs = mutableListOf<String>()
        val rawDigitsOrChars = raw.substringAfter("+").trim()
        if (rawDigitsOrChars.length == confirmed.length) {
            for (i in rawDigitsOrChars.indices) {
                val r = rawDigitsOrChars[i]
                val c = confirmed[i]
                if (r != c) {
                    pairs.add("$r->$c")
                }
            }
        }
        return pairs.joinToString(",")
    }

    fun resetScannerLearningData(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.resetScannerLearning()
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                onComplete()
            }
        }
    }

    suspend fun getScannerLearningRecordsDirect(): List<ScannerLearningEntity> {
        return repository.getAllScannerLearningDirect()
    }

    // ==========================================
    // MARKLIFE P50S THERMAL RECEIPT PRINTER
    // ==========================================
    val printerBleManager = com.example.printer.PrinterBleManager.getInstance(getApplication())
    val printerConnectionState = printerBleManager.connectionState
    val printerJobStatus = printerBleManager.printJobStatus
    val printStatusMessage = printerBleManager.printStatusMessage
    val scannedPrinters = printerBleManager.scannedDevices
    val printerIsScanning = printerBleManager.isScanningFlow
    val printerScanCountdown = printerBleManager.scanCountdown
    val printerLastScanError = printerBleManager.lastScanError
    val printerTotalPackets = printerBleManager.totalPacketsReceived
    val printerManufacturerPackets = printerBleManager.manufacturerPacketsCount

    private val _isAutoPrintReceipt = MutableStateFlow(com.example.printer.PrinterPreferences.isAutoPrintEnabled(getApplication()))
    val isAutoPrintReceipt: StateFlow<Boolean> = _isAutoPrintReceipt.asStateFlow()

    fun setAutoPrintReceipt(enabled: Boolean) {
        com.example.printer.PrinterPreferences.setAutoPrintEnabled(getApplication(), enabled)
        _isAutoPrintReceipt.value = enabled
    }

    fun startPrinterScan(durationSeconds: Int = 15) {
        printerBleManager.startScan(durationSeconds)
    }

    fun stopPrinterScan() {
        printerBleManager.stopScan()
    }

    fun clearScannedPrinters() {
        printerBleManager.clearScannedDevices()
    }

    fun getMissingPrinterPermissions(): List<String> {
        return printerBleManager.getMissingPermissions()
    }

    fun connectPrinter(address: String, name: String? = null) {
        printerBleManager.connect(address, name)
    }

    fun disconnectPrinter() {
        printerBleManager.disconnect()
    }

    fun syncPrinterConnectionState() {
        printerBleManager.syncConnectionState()
    }

    fun printTestReceipt(onResult: (com.example.printer.PrintResult) -> Unit) {
        viewModelScope.launch {
            val result = printerBleManager.printTestReceipt()
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    fun printCustomerReceipt(repairId: Long, onResult: (com.example.printer.PrintResult) -> Unit) {
        viewModelScope.launch {
            val rep = repository.getRepairById(repairId)
            if (rep == null) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(com.example.printer.PrintResult.Error("Repair job not found"))
                }
                return@launch
            }
            val items = repository.getItemsForRepairDirect(repairId)
            val payments = repository.getPaymentsForRepairDirect(repairId)
            val currentSettings = settings.value

            val result = printerBleManager.printCustomerReceipt(rep, items, payments, currentSettings)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    fun printCustomerReceiptDirect(repair: RepairEntity, onResult: (com.example.printer.PrintResult) -> Unit) {
        viewModelScope.launch {
            val items = repository.getItemsForRepairDirect(repair.id)
            val payments = repository.getPaymentsForRepairDirect(repair.id)
            val currentSettings = settings.value

            val result = printerBleManager.printCustomerReceipt(repair, items, payments, currentSettings)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    fun printPaymentReceipt(repairId: Long, payment: PaymentEntity, onResult: (com.example.printer.PrintResult) -> Unit) {
        viewModelScope.launch {
            val rep = repository.getRepairById(repairId)
            if (rep == null) {
                withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(com.example.printer.PrintResult.Error("Repair job not found"))
                }
                return@launch
            }
            val currentSettings = settings.value
            val result = printerBleManager.printPaymentReceipt(rep, payment, currentSettings)
            withContext(kotlinx.coroutines.Dispatchers.Main) {
                onResult(result)
            }
        }
    }
}

