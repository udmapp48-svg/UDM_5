package com.example.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale

sealed class SmsSendResult {
    abstract val isSuccess: Boolean
    abstract val message: String

    data class SentOrQueued(
        val recipient: String,
        override val message: String = "Ready SMS initiated and queued for $recipient"
    ) : SmsSendResult() {
        override val isSuccess: Boolean = true
    }

    data class PermissionDenied(
        override val message: String = "SMS permission not granted. Please allow SMS permission in Android settings."
    ) : SmsSendResult() {
        override val isSuccess: Boolean = false
    }

    data class Failed(
        val error: String,
        override val message: String = "Failed to send SMS: $error"
    ) : SmsSendResult() {
        override val isSuccess: Boolean = false
    }

    data class DisabledInSettings(
        override val message: String = "Automatic Ready SMS is disabled in Settings."
    ) : SmsSendResult() {
        override val isSuccess: Boolean = false
    }
}

data class SmsEvent(
    val repairId: Long,
    val jobNumber: String,
    val customerPhone: String,
    val result: SmsSendResult,
    val isAuto: Boolean,
    val message: String
)

class RepairRepository(private val db: AppDatabase, private val context: Context) {

    private val repairDao = db.repairDao()
    private val repairItemDao = db.repairItemDao()
    private val paymentDao = db.paymentDao()
    private val statusHistoryDao = db.statusHistoryDao()
    private val customerDao = db.customerDao()
    private val brandDao = db.brandDao()
    private val modelDao = db.modelDao()
    private val faultDao = db.faultDao()
    private val repairTypeDao = db.repairTypeDao()
    private val priceDao = db.priceDao()
    private val settingsDao = db.settingsDao()
    private val scannerLearningDao = db.scannerLearningDao()

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processedReadyTransitions = Collections.synchronizedSet(mutableSetOf<String>())
    private val prefs by lazy {
        context.getSharedPreferences("udm_ready_sms_transitions", Context.MODE_PRIVATE)
    }

    private val _smsEvents = MutableSharedFlow<SmsEvent>(extraBufferCapacity = 64)
    val smsEvents: SharedFlow<SmsEvent> = _smsEvents.asSharedFlow()

    private fun markTransitionProcessed(marker: String): Boolean {
        synchronized(processedReadyTransitions) {
            if (processedReadyTransitions.contains(marker) || prefs.getBoolean(marker, false)) {
                return false
            }
            processedReadyTransitions.add(marker)
            try {
                prefs.edit().putBoolean(marker, true).apply()
            } catch (e: Exception) {
                // Ignore SharedPreferences failure, in-memory set handles this session
            }
            return true
        }
    }

    val allRepairs: Flow<List<RepairEntity>> = repairDao.getAllRepairsFlow()
    val allPayments: Flow<List<PaymentEntity>> = paymentDao.getAllPaymentsFlow()
    val allCustomers: Flow<List<CustomerEntity>> = customerDao.getAllCustomersFlow()
    val allBrands: Flow<List<BrandEntity>> = brandDao.getAllBrandsFlow()
    val allFaults: Flow<List<FaultEntity>> = faultDao.getAllFaultsFlow()
    val allRepairTypes: Flow<List<RepairTypeEntity>> = repairTypeDao.getAllRepairTypesFlow()
    val allPrices: Flow<List<PriceEntity>> = priceDao.getAllPricesFlow()
    val settingsFlow: Flow<AppSettingsEntity?> = settingsDao.getSettingsFlow()
    val scannerLearningRecords: Flow<List<ScannerLearningEntity>> = scannerLearningDao.getAllRecordsFlow()
    val scannerLearningCount: Flow<Int> = scannerLearningDao.getRecordCountFlow()

    fun getModelsForBrand(brand: String): Flow<List<ModelEntity>> = modelDao.getModelsForBrand(brand)

    fun getItemsForRepair(repairId: Long): Flow<List<RepairItemEntity>> = repairItemDao.getItemsForRepair(repairId)
    suspend fun getItemsForRepairDirect(repairId: Long): List<RepairItemEntity> = repairItemDao.getItemsForRepairDirect(repairId)

    fun getPaymentsForRepair(repairId: Long): Flow<List<PaymentEntity>> = paymentDao.getPaymentsForRepair(repairId)
    suspend fun getPaymentsForRepairDirect(repairId: Long): List<PaymentEntity> = paymentDao.getPaymentsForRepairDirect(repairId)

    fun getHistoryForRepair(repairId: Long): Flow<List<StatusHistoryEntity>> = statusHistoryDao.getHistoryForRepair(repairId)

    suspend fun getRepairById(id: Long): RepairEntity? = repairDao.getRepairById(id)

    suspend fun getRepairByJobNumber(jobNumber: String): RepairEntity? = withContext(Dispatchers.IO) {
        val trimmed = jobNumber.trim()
        if (trimmed.isEmpty()) return@withContext null

        // 1. Direct search preserving exact string and leading zeros (e.g. "00125")
        val direct = repairDao.getRepairByJobNumber(trimmed)
        if (direct != null) return@withContext direct

        // 2. Normalized 4-digit search fallback
        val normalized = normalizeJobNumber(trimmed)
        if (normalized.isNotEmpty() && normalized != trimmed) {
            val normMatch = repairDao.getRepairByJobNumber(normalized)
            if (normMatch != null) return@withContext normMatch
        }

        // 3. Fallback: match by numerical value across all existing repairs
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isNotEmpty()) {
            val all = repairDao.getAllRepairsDirect()
            return@withContext all.firstOrNull {
                it.jobNumber == trimmed ||
                it.jobNumber == digits ||
                it.jobNumber.trimStart('0') == digits.trimStart('0')
            }
        }
        null
    }

    suspend fun getRepairsForCustomer(phone: String): List<RepairEntity> = repairDao.getRepairsByCustomerPhone(phone)

    suspend fun getSettings(): AppSettingsEntity {
        return settingsDao.getSettings() ?: AppSettingsEntity()
    }

    suspend fun updateSettings(settings: AppSettingsEntity) {
        settingsDao.insertOrUpdate(settings)
    }

    // 4-Digit Job Number Generator
    suspend fun generateNextJobNumber(): String = withContext(Dispatchers.IO) {
        val settings = getSettings()
        val allNumbers = repairDao.getAllJobNumbers()
        var maxNum = settings.startingJobNumber - 1

        for (numStr in allNumbers) {
            val num = numStr.toIntOrNull()
            if (num != null && num > maxNum) {
                maxNum = num
            }
        }
        val next = maxNum + 1
        String.format(Locale.US, "%04d", next)
    }

    fun normalizeJobNumber(raw: String): String {
        val digitsOnly = raw.replace(Regex("[^0-9]"), "")
        if (digitsOnly.isEmpty()) return ""
        val num = digitsOnly.toIntOrNull() ?: 0
        return String.format(Locale.US, "%04d", num)
    }

    // Customer History Lookup by Phone
    suspend fun getCustomerSummaryByPhone(phone: String): Pair<CustomerEntity?, List<RepairEntity>> = withContext(Dispatchers.IO) {
        val clean = phone.filter { it.isDigit() }
        if (clean.length < 3) return@withContext null to emptyList()
        val allRepairs = repairDao.getAllRepairsDirect()
        val matchingRepairs = allRepairs.filter { repair ->
            val repClean = repair.customerPhone.filter { it.isDigit() }
            repClean.isNotEmpty() && (repClean == clean || repClean.endsWith(clean) || clean.endsWith(repClean))
        }
        val allCustomers = customerDao.getAllCustomersDirect()
        val matchingCustomer = allCustomers.find { customer ->
            val custClean = customer.phone.filter { it.isDigit() }
            custClean.isNotEmpty() && (custClean == clean || custClean.endsWith(clean) || clean.endsWith(custClean))
        }
        matchingCustomer to matchingRepairs
    }

    // Price Lookup
    suspend fun lookupPrice(brand: String, model: String, repairType: String): Double? = withContext(Dispatchers.IO) {
        val entity = priceDao.findPrice(brand.trim(), model.trim(), repairType.trim())
        entity?.price
    }

    suspend fun savePrice(brand: String, model: String, repairType: String, price: Double) = withContext(Dispatchers.IO) {
        priceDao.insertPrice(
            PriceEntity(
                brand = brand.trim(),
                model = model.trim(),
                repairType = repairType.trim(),
                price = price
            )
        )
    }

    suspend fun deletePrice(price: PriceEntity) = withContext(Dispatchers.IO) {
        priceDao.deletePrice(price)
    }

    // Autocomplete Master Data
    suspend fun addBrandIfNew(name: String): Boolean = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext false
        val existing = brandDao.getAllBrandsDirect()
        if (existing.none { it.name.equals(trimmed, ignoreCase = true) }) {
            brandDao.insertBrand(BrandEntity(name = trimmed))
            true
        } else false
    }

    suspend fun addModelIfNew(brand: String, modelName: String): Boolean = withContext(Dispatchers.IO) {
        val bTrimmed = brand.trim()
        val mTrimmed = modelName.trim()
        if (bTrimmed.isEmpty() || mTrimmed.isEmpty()) return@withContext false
        val existing = modelDao.getModelsForBrandDirect(bTrimmed)
        if (existing.none { it.modelName.equals(mTrimmed, ignoreCase = true) }) {
            modelDao.insertModel(ModelEntity(brandName = bTrimmed, modelName = mTrimmed))
            true
        } else false
    }

    suspend fun addFaultIfNew(name: String): Boolean = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext false
        val existing = faultDao.getAllFaultsDirect()
        if (existing.none { it.name.equals(trimmed, ignoreCase = true) }) {
            faultDao.insertFault(FaultEntity(name = trimmed))
            true
        } else false
    }

    suspend fun addRepairTypeIfNew(name: String): Boolean = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext false
        val existing = repairTypeDao.getAllRepairTypesDirect()
        if (existing.none { it.name.equals(trimmed, ignoreCase = true) }) {
            repairTypeDao.insertRepairType(RepairTypeEntity(name = trimmed))
            true
        } else false
    }

    // Creating a New Repair
    suspend fun createRepair(
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
        initialPaymentAmount: Double
    ): RepairEntity = withContext(Dispatchers.IO) {
        val jobNumber = generateNextJobNumber()
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date(now))
        val timeStr = timeFormat.format(Date(now))

        val totalPrice = repairItems.sumOf { it.second }
        val amountPaid = initialPaymentAmount
        val balance = (totalPrice - amountPaid).coerceAtLeast(0.0)

        val paymentStatus = when {
            totalPrice > 0 && amountPaid >= totalPrice -> "PAID"
            amountPaid > 0 -> "PARTIALLY PAID"
            else -> "UNPAID"
        }

        // Automatic delivery if fully paid
        val initialStatus = if (totalPrice > 0 && amountPaid >= totalPrice) "DELIVERED" else "RECEIVED"
        val deliveredTimestamp = if (initialStatus == "DELIVERED") now else null

        val repairEntity = RepairEntity(
            jobNumber = jobNumber,
            customerName = customerName.trim(),
            customerPhone = customerPhone.trim(),
            brand = brand.trim(),
            model = model.trim(),
            imei = imei.trim(),
            deviceColour = deviceColour.trim(),
            accessories = accessories.trim(),
            fault = fault.trim(),
            technicianNotes = technicianNotes.trim(),
            remarks = remarks.trim(),
            totalPrice = totalPrice,
            amountPaid = amountPaid,
            balance = balance,
            paymentStatus = paymentStatus,
            status = initialStatus,
            receivedDate = dateStr,
            receivedTime = timeStr,
            receivedTimestamp = now,
            deliveredTimestamp = deliveredTimestamp,
            readySmsSent = false
        )

        val repairId = repairDao.insertRepair(repairEntity)
        val createdRepair = repairEntity.copy(id = repairId)

        // Insert repair items
        val itemEntities = repairItems.map {
            RepairItemEntity(repairId = repairId, repairType = it.first.trim(), price = it.second)
        }
        repairItemDao.insertItems(itemEntities)

        // Insert initial payment if any
        if (initialPaymentAmount > 0) {
            paymentDao.insertPayment(
                PaymentEntity(
                    repairId = repairId,
                    paymentNumber = 1,
                    amount = initialPaymentAmount,
                    date = dateStr,
                    time = timeStr,
                    timestamp = now
                )
            )
        }

        // Insert status history
        statusHistoryDao.insertHistory(
            StatusHistoryEntity(
                repairId = repairId,
                status = initialStatus,
                date = dateStr,
                time = timeStr,
                timestamp = now,
                notes = if (initialStatus == "DELIVERED") "Delivered on receipt (Paid in full)" else "Job registered"
            )
        )

        // Save / update customer
        customerDao.insertCustomer(
            CustomerEntity(
                phone = customerPhone.trim(),
                name = customerName.trim(),
                lastVisitTimestamp = now
            )
        )

        // Save any custom brand/model/fault/repairType into master data
        addBrandIfNew(brand)
        addModelIfNew(brand, model)
        addFaultIfNew(fault)
        repairItems.forEach { addRepairTypeIfNew(it.first) }

        // Trigger automatic customer registration SMS (phone received) AFTER repair is successfully saved
        val currentSettings = getSettings()
        if (currentSettings.autoRegistrationSms && createdRepair.customerPhone.isNotBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                performAutoRegistrationSms(createdRepair)
            }
        }

        createdRepair
    }

    // Payment Operations
    suspend fun addPayment(repairId: Long, amount: Double): RepairEntity? = withContext(Dispatchers.IO) {
        val repair = repairDao.getRepairById(repairId) ?: return@withContext null
        if (amount <= 0) return@withContext repair

        val existingPayments = paymentDao.getPaymentsForRepairDirect(repairId)
        val nextNumber = existingPayments.size + 1
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date(now))
        val timeStr = timeFormat.format(Date(now))

        paymentDao.insertPayment(
            PaymentEntity(
                repairId = repairId,
                paymentNumber = nextNumber,
                amount = amount,
                date = dateStr,
                time = timeStr,
                timestamp = now
            )
        )

        recalculateAndSaveRepair(repairId)
    }

    suspend fun updatePayment(paymentId: Long, newAmount: Double): RepairEntity? = withContext(Dispatchers.IO) {
        val allPayments = paymentDao.getAllPaymentsDirect()
        val payment = allPayments.find { it.id == paymentId } ?: return@withContext null
        if (newAmount <= 0) return@withContext null

        paymentDao.updatePayment(payment.copy(amount = newAmount))
        recalculateAndSaveRepair(payment.repairId)
    }

    suspend fun deletePayment(paymentId: Long): RepairEntity? = withContext(Dispatchers.IO) {
        val allPayments = paymentDao.getAllPaymentsDirect()
        val payment = allPayments.find { it.id == paymentId } ?: return@withContext null
        val repairId = payment.repairId

        paymentDao.deletePaymentById(paymentId)
        recalculateAndSaveRepair(repairId)
    }

    // Add / Remove Repair Items
    suspend fun addRepairItem(repairId: Long, repairType: String, price: Double): RepairEntity? = withContext(Dispatchers.IO) {
        repairItemDao.insertItem(
            RepairItemEntity(repairId = repairId, repairType = repairType.trim(), price = price)
        )
        addRepairTypeIfNew(repairType)
        recalculateAndSaveRepair(repairId)
    }

    suspend fun removeRepairItem(itemId: Long, repairId: Long): RepairEntity? = withContext(Dispatchers.IO) {
        val items = repairItemDao.getItemsForRepairDirect(repairId)
        val item = items.find { it.id == itemId } ?: return@withContext null
        repairItemDao.deleteItem(item)
        recalculateAndSaveRepair(repairId)
    }

    suspend fun updateRepairItem(itemId: Long, repairId: Long, newRepairType: String, newPrice: Double): RepairEntity? = withContext(Dispatchers.IO) {
        val items = repairItemDao.getItemsForRepairDirect(repairId)
        val item = items.find { it.id == itemId } ?: return@withContext null
        repairItemDao.updateItem(item.copy(repairType = newRepairType.trim(), price = newPrice))
        addRepairTypeIfNew(newRepairType)
        recalculateAndSaveRepair(repairId)
    }

    // Recalculates payments, balance, payment status, and automatic delivery status
    private suspend fun recalculateAndSaveRepair(repairId: Long): RepairEntity? {
        val repair = repairDao.getRepairById(repairId) ?: return null
        val items = repairItemDao.getItemsForRepairDirect(repairId)
        val payments = paymentDao.getPaymentsForRepairDirect(repairId)

        val totalPrice = items.sumOf { it.price }
        val totalPaid = payments.sumOf { it.amount }
        val balance = (totalPrice - totalPaid).coerceAtLeast(0.0)

        val paymentStatus = when {
            totalPrice > 0 && totalPaid >= totalPrice -> "PAID"
            totalPaid > 0 -> "PARTIALLY PAID"
            else -> "UNPAID"
        }

        var newStatus = repair.status
        var deliveredTimestamp = repair.deliveredTimestamp
        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        // Automation Rule: If Total Paid >= Total Repair Price -> DELIVERED
        if (totalPrice > 0 && totalPaid >= totalPrice) {
            if (repair.status != "DELIVERED") {
                newStatus = "DELIVERED"
                deliveredTimestamp = now
                statusHistoryDao.insertHistory(
                    StatusHistoryEntity(
                        repairId = repairId,
                        status = "DELIVERED",
                        date = dateFormat.format(Date(now)),
                        time = timeFormat.format(Date(now)),
                        timestamp = now,
                        notes = "Auto-marked DELIVERED: Full payment received"
                    )
                )
            }
        } else {
            // If it was DELIVERED purely because of full payment, and now underpaid (e.g. price increased or payment removed)
            if (repair.status == "DELIVERED" && totalPrice > totalPaid) {
                // Return to appropriate status (READY if was ready, or REPAIRING)
                newStatus = "READY"
                statusHistoryDao.insertHistory(
                    StatusHistoryEntity(
                        repairId = repairId,
                        status = "READY",
                        date = dateFormat.format(Date(now)),
                        time = timeFormat.format(Date(now)),
                        timestamp = now,
                        notes = "Status updated to READY due to pending balance"
                    )
                )
            }
        }

        val updated = repair.copy(
            totalPrice = totalPrice,
            amountPaid = totalPaid,
            balance = balance,
            paymentStatus = paymentStatus,
            status = newStatus,
            deliveredTimestamp = deliveredTimestamp
        )

        repairDao.updateRepair(updated)
        return updated
    }

    // Status Changes & Automatic SMS
    suspend fun updateStatus(repairId: Long, newStatus: String, notes: String = ""): RepairEntity? = withContext(Dispatchers.IO) {
        val repair = repairDao.getRepairById(repairId) ?: return@withContext null
        if (repair.status == newStatus) return@withContext repair

        val now = System.currentTimeMillis()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateStr = dateFormat.format(Date(now))
        val timeStr = timeFormat.format(Date(now))

        val isTransitionToReady = (newStatus == "READY")
        val deliveredTimestamp = if (newStatus == "DELIVERED") now else repair.deliveredTimestamp

        // When transitioning to READY, mark readySmsSent = true. When leaving READY, reset to false so a future transition back to READY can send SMS again.
        val updated = repair.copy(
            status = newStatus,
            deliveredTimestamp = deliveredTimestamp,
            readySmsSent = if (isTransitionToReady) true else false
        )

        // 1. SAVE READY STATUS IMMEDIATELY to local database
        repairDao.updateRepair(updated)
        val historyId = statusHistoryDao.insertHistory(
            StatusHistoryEntity(
                repairId = repairId,
                status = newStatus,
                date = dateStr,
                time = timeStr,
                timestamp = now,
                notes = notes.ifEmpty { "Status changed to $newStatus" }
            )
        )

        // 2. Immediately initiate Ready SMS asynchronously if transitioning to READY
        if (isTransitionToReady) {
            val transitionMarker = "${repairId}_${historyId}"
            if (markTransitionProcessed(transitionMarker)) {
                repositoryScope.launch(Dispatchers.IO) {
                    performAutoReadySms(updated, transitionMarker)
                }
            }
        }

        updated
    }

    private suspend fun performAutoReadySms(repair: RepairEntity, transitionMarker: String) {
        val settings = getSettings()
        if (!settings.autoReadySms) {
            return
        }

        val phone = repair.customerPhone.trim()
        if (phone.isBlank()) {
            _smsEvents.emit(
                SmsEvent(
                    repairId = repair.id,
                    jobNumber = repair.jobNumber,
                    customerPhone = phone,
                    result = SmsSendResult.Failed("Customer phone number is blank"),
                    isAuto = true,
                    message = "Status marked READY. SMS not sent: Customer phone number is blank."
                )
            )
            return
        }

        val result = sendSmsInternal(repair, settings)
        val displayMessage = when (result) {
            is SmsSendResult.SentOrQueued -> "Ready SMS initiated and queued for $phone"
            is SmsSendResult.PermissionDenied -> "Status marked READY. SMS not sent: SMS permission not granted."
            is SmsSendResult.Failed -> "Status marked READY. SMS failed: ${result.error}"
            is SmsSendResult.DisabledInSettings -> "Automatic Ready SMS is disabled in Settings."
        }

        _smsEvents.emit(
            SmsEvent(
                repairId = repair.id,
                jobNumber = repair.jobNumber,
                customerPhone = phone,
                result = result,
                isAuto = true,
                message = displayMessage
            )
        )
    }

    // Send SMS (Manual "SEND SMS AGAIN")
    suspend fun sendReadySmsExplicit(repair: RepairEntity): SmsSendResult = withContext(Dispatchers.IO) {
        val settings = getSettings()
        sendSmsInternal(repair, settings)
    }

    private fun sendSmsInternal(repair: RepairEntity, settings: AppSettingsEntity): SmsSendResult {
        val phone = repair.customerPhone.trim()
        if (phone.isBlank()) {
            return SmsSendResult.Failed("Customer phone number is blank")
        }

        val hasPermission = try {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }

        if (!hasPermission) {
            return SmsSendResult.PermissionDenied("SMS permission not granted. Please allow SMS permission in Android settings.")
        }

        return try {
            val balanceStr = if (repair.balance <= 0) "0" else String.format(Locale.US, "%,.0f", repair.balance)
            val totalStr = String.format(Locale.US, "%,.0f", repair.totalPrice)

            val text = settings.smsTemplate
                .replace("{customer_name}", repair.customerName)
                .replace("{job_number}", repair.jobNumber)
                .replace("{total_price}", totalStr)
                .replace("{balance}", balanceStr)
                .replace("{model}", "${repair.brand} ${repair.model}")
                .replace("{shop_name}", settings.shopName)
                .replace("{shop_phone}", settings.shopPhone)

            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(text)
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)

            SmsSendResult.SentOrQueued(
                recipient = phone,
                message = "Ready SMS initiated and queued for $phone"
            )
        } catch (se: SecurityException) {
            SmsSendResult.PermissionDenied(se.localizedMessage ?: "SMS permission denied")
        } catch (e: Exception) {
            SmsSendResult.Failed(e.localizedMessage ?: "Failed to initiate SMS sending")
        }
    }

    // Automatic Customer SMS on Job Registration (Phone Received)
    suspend fun performAutoRegistrationSms(repair: RepairEntity): SmsSendResult = withContext(Dispatchers.IO) {
        val settings = getSettings()
        if (!settings.autoRegistrationSms) {
            return@withContext SmsSendResult.DisabledInSettings("Automatic Registration SMS is disabled in Settings.")
        }

        val phone = repair.customerPhone.trim()
        if (phone.isBlank()) {
            val res = SmsSendResult.Failed("Customer phone number is blank")
            _smsEvents.emit(
                SmsEvent(
                    repairId = repair.id,
                    jobNumber = repair.jobNumber,
                    customerPhone = phone,
                    result = res,
                    isAuto = true,
                    message = "Job #${repair.jobNumber} registered. SMS not sent: Customer phone number is blank."
                )
            )
            return@withContext res
        }

        val result = sendRegistrationSmsInternal(repair, settings)
        val displayMessage = when (result) {
            is SmsSendResult.SentOrQueued -> "Registration confirmation SMS sent to $phone"
            is SmsSendResult.PermissionDenied -> "Job #${repair.jobNumber} saved. SMS not sent: SMS permission not granted."
            is SmsSendResult.Failed -> "Job #${repair.jobNumber} saved. SMS failed: ${result.error}"
            is SmsSendResult.DisabledInSettings -> "Automatic Registration SMS is disabled in Settings."
        }

        _smsEvents.emit(
            SmsEvent(
                repairId = repair.id,
                jobNumber = repair.jobNumber,
                customerPhone = phone,
                result = result,
                isAuto = true,
                message = displayMessage
            )
        )
        result
    }

    // Explicit Registration SMS Sending (Manual Trigger / Resend)
    suspend fun sendRegistrationSmsExplicit(repair: RepairEntity): SmsSendResult = withContext(Dispatchers.IO) {
        val settings = getSettings()
        sendRegistrationSmsInternal(repair, settings)
    }

    private fun sendRegistrationSmsInternal(repair: RepairEntity, settings: AppSettingsEntity): SmsSendResult {
        val phone = repair.customerPhone.trim()
        if (phone.isBlank()) {
            return SmsSendResult.Failed("Customer phone number is blank")
        }

        val hasPermission = try {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.SEND_SMS
            ) == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }

        if (!hasPermission) {
            return SmsSendResult.PermissionDenied("SMS permission not granted. Please allow SMS permission in Android settings.")
        }

        return try {
            val totalStr = if (repair.totalPrice % 1.0 == 0.0) {
                String.format(Locale.US, "%.0f", repair.totalPrice)
            } else {
                String.format(Locale.US, "%,.2f", repair.totalPrice)
            }

            val paidStr = if (repair.amountPaid % 1.0 == 0.0) {
                String.format(Locale.US, "%.0f", repair.amountPaid)
            } else {
                String.format(Locale.US, "%,.2f", repair.amountPaid)
            }

            val balanceStr = if (repair.balance % 1.0 == 0.0) {
                String.format(Locale.US, "%.0f", repair.balance)
            } else {
                String.format(Locale.US, "%,.2f", repair.balance)
            }

            var text = settings.registrationSmsTemplate
            // Automatically replace JOB_NO, TOTAL, PAID and BALANCE with actual job values (case-insensitive)
            text = text.replace("{JOB_NO}", repair.jobNumber, ignoreCase = true)
                .replace("{JOB_NUMBER}", repair.jobNumber, ignoreCase = true)
                .replace("{TOTAL}", totalStr, ignoreCase = true)
                .replace("{TOTAL_PRICE}", totalStr, ignoreCase = true)
                .replace("{PAID}", paidStr, ignoreCase = true)
                .replace("{AMOUNT_PAID}", paidStr, ignoreCase = true)
                .replace("{BALANCE}", balanceStr, ignoreCase = true)
                .replace("{CUSTOMER_NAME}", repair.customerName, ignoreCase = true)
                .replace("{MODEL}", "${repair.brand} ${repair.model}", ignoreCase = true)
                .replace("{SHOP_NAME}", settings.shopName, ignoreCase = true)
                .replace("{SHOP_PHONE}", settings.shopPhone, ignoreCase = true)

            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(text)
            smsManager.sendMultipartTextMessage(phone, null, parts, null, null)

            SmsSendResult.SentOrQueued(
                recipient = phone,
                message = "Registration SMS initiated and queued for $phone"
            )
        } catch (se: SecurityException) {
            SmsSendResult.PermissionDenied(se.localizedMessage ?: "SMS permission denied")
        } catch (e: Exception) {
            SmsSendResult.Failed(e.localizedMessage ?: "Failed to initiate SMS sending")
        }
    }

    // Full Repair Details Updating
    suspend fun updateRepairDetails(
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
        remarks: String
    ) = withContext(Dispatchers.IO) {
        val repair = repairDao.getRepairById(repairId) ?: return@withContext
        val updated = repair.copy(
            customerName = customerName.trim(),
            customerPhone = customerPhone.trim(),
            brand = brand.trim(),
            model = model.trim(),
            imei = imei.trim(),
            deviceColour = deviceColour.trim(),
            accessories = accessories.trim(),
            fault = fault.trim(),
            technicianNotes = technicianNotes.trim(),
            remarks = remarks.trim()
        )
        repairDao.updateRepair(updated)
        customerDao.insertCustomer(
            CustomerEntity(
                phone = customerPhone.trim(),
                name = customerName.trim(),
                lastVisitTimestamp = System.currentTimeMillis()
            )
        )
        addBrandIfNew(brand)
        addModelIfNew(brand, model)
        addFaultIfNew(fault)
    }

    suspend fun deleteRepair(repairId: Long) = withContext(Dispatchers.IO) {
        repairItemDao.deleteItemsForRepair(repairId)
        paymentDao.deletePaymentsForRepair(repairId)
        statusHistoryDao.deleteHistoryForRepair(repairId)
        repairDao.deleteRepairById(repairId)
    }

    // Master Data Deletion/Editing
    suspend fun deleteBrand(brand: BrandEntity) = withContext(Dispatchers.IO) { brandDao.deleteBrand(brand) }
    suspend fun deleteModel(model: ModelEntity) = withContext(Dispatchers.IO) { modelDao.deleteModel(model) }
    suspend fun deleteFault(fault: FaultEntity) = withContext(Dispatchers.IO) { faultDao.deleteFault(fault) }
    suspend fun deleteRepairType(type: RepairTypeEntity) = withContext(Dispatchers.IO) { repairTypeDao.deleteRepairType(type) }

    // Scanner Adaptive Learning (Storage-Safe)
    suspend fun saveScannerLearning(
        rawOcrText: String,
        confirmedJobNumber: String,
        detectedJobNumber: String = "",
        confidence: Float = 0f,
        preprocessingMethod: String = "",
        characterSubstitutions: String = ""
    ) = withContext(Dispatchers.IO) {
        val cleanRaw = rawOcrText.trim()
        val cleanConfirmed = confirmedJobNumber.trim()
        if (cleanRaw.isBlank() || cleanConfirmed.isBlank()) return@withContext

        val existing = scannerLearningDao.findRecord(cleanRaw, cleanConfirmed)
        if (existing != null) {
            val updated = existing.copy(
                timesEncountered = existing.timesEncountered + 1,
                timestamp = System.currentTimeMillis(),
                confidence = if (confidence > 0) confidence else existing.confidence,
                preprocessingMethod = preprocessingMethod.ifEmpty { existing.preprocessingMethod },
                characterSubstitutions = characterSubstitutions.ifEmpty { existing.characterSubstitutions }
            )
            scannerLearningDao.updateRecord(updated)
        } else {
            scannerLearningDao.insertRecord(
                ScannerLearningEntity(
                    rawOcrText = cleanRaw,
                    confirmedJobNumber = cleanConfirmed,
                    detectedJobNumber = detectedJobNumber.trim(),
                    confidence = confidence,
                    preprocessingMethod = preprocessingMethod,
                    characterSubstitutions = characterSubstitutions,
                    timestamp = System.currentTimeMillis(),
                    timesEncountered = 1
                )
            )
        }
        // Never fill phone storage: automatically prune oldest/least frequent records beyond 100
        scannerLearningDao.pruneOldRecords(100)
    }

    suspend fun resetScannerLearning() = withContext(Dispatchers.IO) {
        scannerLearningDao.deleteAllRecords()
    }

    suspend fun getAllScannerLearningDirect(): List<ScannerLearningEntity> = withContext(Dispatchers.IO) {
        scannerLearningDao.getAllRecordsDirect()
    }

    // Direct access for backup & restore
    suspend fun getAllDataForBackup() = withContext(Dispatchers.IO) {
        BackupData(
            repairs = repairDao.getAllRepairsDirect(),
            repairItems = repairItemDao.getAllRepairItemsDirect(),
            payments = paymentDao.getAllPaymentsDirect(),
            statusHistory = statusHistoryDao.getAllHistoryDirect(),
            customers = customerDao.getAllCustomersDirect(),
            brands = brandDao.getAllBrandsDirect(),
            models = modelDao.getAllModelsDirect(),
            faults = faultDao.getAllFaultsDirect(),
            repairTypes = repairTypeDao.getAllRepairTypesDirect(),
            prices = priceDao.getAllPricesDirect(),
            settings = settingsDao.getSettings() ?: AppSettingsEntity(),
            scannerLearning = scannerLearningDao.getAllRecordsDirect()
        )
    }

    suspend fun restoreData(backup: BackupData, clearExisting: Boolean = true) = withContext(Dispatchers.IO) {
        if (clearExisting) {
            db.clearAllTables()
        }
        if (backup.brands.isNotEmpty()) {
            for (b in backup.brands) brandDao.insertBrand(b)
        } else {
            brandDao.insertBrands(com.example.data.DefaultData.BRANDS)
        }
        if (backup.models.isNotEmpty()) {
            for (m in backup.models) modelDao.insertModel(m)
        } else {
            modelDao.insertModels(com.example.data.DefaultData.getModelsList())
        }
        if (backup.faults.isNotEmpty()) {
            for (f in backup.faults) faultDao.insertFault(f)
        } else {
            faultDao.insertFaults(com.example.data.DefaultData.FAULTS)
        }
        if (backup.repairTypes.isNotEmpty()) {
            for (rt in backup.repairTypes) repairTypeDao.insertRepairType(rt)
        } else {
            repairTypeDao.insertRepairTypes(com.example.data.DefaultData.REPAIR_TYPES)
        }
        for (p in backup.prices) priceDao.insertPrice(p)
        for (c in backup.customers) customerDao.insertCustomer(c)
        for (r in backup.repairs) repairDao.insertRepair(r)
        for (ri in backup.repairItems) repairItemDao.insertItem(ri)
        for (pm in backup.payments) paymentDao.insertPayment(pm)
        for (sh in backup.statusHistory) statusHistoryDao.insertHistory(sh)
        settingsDao.insertOrUpdate(backup.settings)
        if (backup.scannerLearning.isNotEmpty()) {
            scannerLearningDao.insertRecords(backup.scannerLearning)
        }
    }
}

data class BackupData(
    val repairs: List<RepairEntity>,
    val repairItems: List<RepairItemEntity>,
    val payments: List<PaymentEntity>,
    val statusHistory: List<StatusHistoryEntity>,
    val customers: List<CustomerEntity>,
    val brands: List<BrandEntity>,
    val models: List<ModelEntity>,
    val faults: List<FaultEntity>,
    val repairTypes: List<RepairTypeEntity>,
    val prices: List<PriceEntity>,
    val settings: AppSettingsEntity,
    val scannerLearning: List<ScannerLearningEntity> = emptyList()
)

