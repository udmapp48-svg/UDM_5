package com.example.util

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
import com.example.data.repository.BackupData
import org.json.JSONArray
import org.json.JSONObject

object BackupManager {

    fun exportToJson(data: BackupData): String {
        val root = JSONObject()
        root.put("app", "UDM MOBILE REPAIR")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        // Repairs
        val repairsArray = JSONArray()
        for (r in data.repairs) {
            val obj = JSONObject()
            obj.put("id", r.id)
            obj.put("jobNumber", r.jobNumber)
            obj.put("customerName", r.customerName)
            obj.put("customerPhone", r.customerPhone)
            obj.put("brand", r.brand)
            obj.put("model", r.model)
            obj.put("imei", r.imei)
            obj.put("deviceColour", r.deviceColour)
            obj.put("accessories", r.accessories)
            obj.put("fault", r.fault)
            obj.put("technicianNotes", r.technicianNotes)
            obj.put("remarks", r.remarks)
            obj.put("totalPrice", r.totalPrice)
            obj.put("amountPaid", r.amountPaid)
            obj.put("balance", r.balance)
            obj.put("paymentStatus", r.paymentStatus)
            obj.put("status", r.status)
            obj.put("receivedDate", r.receivedDate)
            obj.put("receivedTime", r.receivedTime)
            obj.put("receivedTimestamp", r.receivedTimestamp)
            obj.put("deliveredTimestamp", r.deliveredTimestamp ?: JSONObject.NULL)
            obj.put("readySmsSent", r.readySmsSent)
            repairsArray.put(obj)
        }
        root.put("repairs", repairsArray)

        // Repair Items
        val itemsArray = JSONArray()
        for (item in data.repairItems) {
            val obj = JSONObject()
            obj.put("id", item.id)
            obj.put("repairId", item.repairId)
            obj.put("repairType", item.repairType)
            obj.put("price", item.price)
            itemsArray.put(obj)
        }
        root.put("repairItems", itemsArray)

        // Payments
        val paymentsArray = JSONArray()
        for (p in data.payments) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("repairId", p.repairId)
            obj.put("paymentNumber", p.paymentNumber)
            obj.put("amount", p.amount)
            obj.put("date", p.date)
            obj.put("time", p.time)
            obj.put("timestamp", p.timestamp)
            paymentsArray.put(obj)
        }
        root.put("payments", paymentsArray)

        // Status History
        val historyArray = JSONArray()
        for (h in data.statusHistory) {
            val obj = JSONObject()
            obj.put("id", h.id)
            obj.put("repairId", h.repairId)
            obj.put("status", h.status)
            obj.put("date", h.date)
            obj.put("time", h.time)
            obj.put("timestamp", h.timestamp)
            obj.put("notes", h.notes)
            historyArray.put(obj)
        }
        root.put("statusHistory", historyArray)

        // Customers
        val customersArray = JSONArray()
        for (c in data.customers) {
            val obj = JSONObject()
            obj.put("phone", c.phone)
            obj.put("name", c.name)
            obj.put("lastVisitTimestamp", c.lastVisitTimestamp)
            customersArray.put(obj)
        }
        root.put("customers", customersArray)

        // Brands
        val brandsArray = JSONArray()
        for (b in data.brands) {
            val obj = JSONObject()
            obj.put("id", b.id)
            obj.put("name", b.name)
            brandsArray.put(obj)
        }
        root.put("brands", brandsArray)

        // Models
        val modelsArray = JSONArray()
        for (m in data.models) {
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("brandName", m.brandName)
            obj.put("modelName", m.modelName)
            modelsArray.put(obj)
        }
        root.put("models", modelsArray)

        // Faults
        val faultsArray = JSONArray()
        for (f in data.faults) {
            val obj = JSONObject()
            obj.put("id", f.id)
            obj.put("name", f.name)
            faultsArray.put(obj)
        }
        root.put("faults", faultsArray)

        // Repair Types
        val typesArray = JSONArray()
        for (rt in data.repairTypes) {
            val obj = JSONObject()
            obj.put("id", rt.id)
            obj.put("name", rt.name)
            typesArray.put(obj)
        }
        root.put("repairTypes", typesArray)

        // Prices
        val pricesArray = JSONArray()
        for (p in data.prices) {
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("brand", p.brand)
            obj.put("model", p.model)
            obj.put("repairType", p.repairType)
            obj.put("price", p.price)
            pricesArray.put(obj)
        }
        root.put("prices", pricesArray)

        // Settings
        val s = data.settings
        val settingsObj = JSONObject()
        settingsObj.put("shopName", s.shopName)
        settingsObj.put("shopPhone", s.shopPhone)
        settingsObj.put("shopAddress", s.shopAddress)
        settingsObj.put("whatsappNumber", s.whatsappNumber)
        settingsObj.put("receiptFooter", s.receiptFooter)
        settingsObj.put("currency", s.currency)
        settingsObj.put("startingJobNumber", s.startingJobNumber)
        settingsObj.put("autoReadySms", s.autoReadySms)
        settingsObj.put("smsTemplate", s.smsTemplate)
        settingsObj.put("autoRegistrationSms", s.autoRegistrationSms)
        settingsObj.put("registrationSmsTemplate", s.registrationSmsTemplate)
        settingsObj.put("isDarkMode", s.isDarkMode)
        root.put("settings", settingsObj)

        // Scanner Learning Records (Storage-Safe: lightweight metadata only, NO photos)
        val learningArray = JSONArray()
        for (item in data.scannerLearning) {
            val obj = JSONObject()
            obj.put("rawOcrText", item.rawOcrText)
            obj.put("confirmedJobNumber", item.confirmedJobNumber)
            obj.put("detectedJobNumber", item.detectedJobNumber)
            obj.put("confidence", item.confidence.toDouble())
            obj.put("preprocessingMethod", item.preprocessingMethod)
            obj.put("characterSubstitutions", item.characterSubstitutions)
            obj.put("timestamp", item.timestamp)
            obj.put("timesEncountered", item.timesEncountered)
            learningArray.put(obj)
        }
        root.put("scannerLearning", learningArray)

        return root.toString(2)
    }

    fun parseFromJson(jsonString: String): BackupData {
        val root = JSONObject(jsonString)
        if (!root.has("repairs")) {
            throw IllegalArgumentException("Invalid backup format: Missing repairs field")
        }

        val repairs = mutableListOf<RepairEntity>()
        val repairsArray = root.optJSONArray("repairs") ?: JSONArray()
        for (i in 0 until repairsArray.length()) {
            val obj = repairsArray.getJSONObject(i)
            repairs.add(
                RepairEntity(
                    id = obj.optLong("id", 0),
                    jobNumber = obj.getString("jobNumber"),
                    customerName = obj.optString("customerName", "Customer"),
                    customerPhone = obj.optString("customerPhone", ""),
                    brand = obj.optString("brand", ""),
                    model = obj.optString("model", ""),
                    imei = obj.optString("imei", ""),
                    deviceColour = obj.optString("deviceColour", ""),
                    accessories = obj.optString("accessories", ""),
                    fault = obj.optString("fault", ""),
                    technicianNotes = obj.optString("technicianNotes", ""),
                    remarks = obj.optString("remarks", ""),
                    totalPrice = obj.optDouble("totalPrice", 0.0),
                    amountPaid = obj.optDouble("amountPaid", 0.0),
                    balance = obj.optDouble("balance", 0.0),
                    paymentStatus = obj.optString("paymentStatus", "UNPAID"),
                    status = obj.optString("status", "RECEIVED"),
                    receivedDate = obj.optString("receivedDate", ""),
                    receivedTime = obj.optString("receivedTime", ""),
                    receivedTimestamp = obj.optLong("receivedTimestamp", System.currentTimeMillis()),
                    deliveredTimestamp = if (obj.has("deliveredTimestamp") && !obj.isNull("deliveredTimestamp")) obj.optLong("deliveredTimestamp") else null,
                    readySmsSent = obj.optBoolean("readySmsSent", false)
                )
            )
        }

        val repairItems = mutableListOf<RepairItemEntity>()
        val itemsArray = root.optJSONArray("repairItems") ?: JSONArray()
        for (i in 0 until itemsArray.length()) {
            val obj = itemsArray.getJSONObject(i)
            repairItems.add(
                RepairItemEntity(
                    id = obj.optLong("id", 0),
                    repairId = obj.getLong("repairId"),
                    repairType = obj.getString("repairType"),
                    price = obj.getDouble("price")
                )
            )
        }

        val payments = mutableListOf<PaymentEntity>()
        val paymentsArray = root.optJSONArray("payments") ?: JSONArray()
        for (i in 0 until paymentsArray.length()) {
            val obj = paymentsArray.getJSONObject(i)
            payments.add(
                PaymentEntity(
                    id = obj.optLong("id", 0),
                    repairId = obj.getLong("repairId"),
                    paymentNumber = obj.optInt("paymentNumber", i + 1),
                    amount = obj.getDouble("amount"),
                    date = obj.optString("date", ""),
                    time = obj.optString("time", ""),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            )
        }

        val statusHistory = mutableListOf<StatusHistoryEntity>()
        val historyArray = root.optJSONArray("statusHistory") ?: JSONArray()
        for (i in 0 until historyArray.length()) {
            val obj = historyArray.getJSONObject(i)
            statusHistory.add(
                StatusHistoryEntity(
                    id = obj.optLong("id", 0),
                    repairId = obj.getLong("repairId"),
                    status = obj.getString("status"),
                    date = obj.optString("date", ""),
                    time = obj.optString("time", ""),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    notes = obj.optString("notes", "")
                )
            )
        }

        val customers = mutableListOf<CustomerEntity>()
        val customersArray = root.optJSONArray("customers") ?: JSONArray()
        for (i in 0 until customersArray.length()) {
            val obj = customersArray.getJSONObject(i)
            customers.add(
                CustomerEntity(
                    phone = obj.getString("phone"),
                    name = obj.getString("name"),
                    lastVisitTimestamp = obj.optLong("lastVisitTimestamp", System.currentTimeMillis())
                )
            )
        }

        val brands = mutableListOf<BrandEntity>()
        val brandsArray = root.optJSONArray("brands") ?: JSONArray()
        for (i in 0 until brandsArray.length()) {
            val obj = brandsArray.getJSONObject(i)
            brands.add(BrandEntity(id = obj.optLong("id", 0), name = obj.getString("name")))
        }

        val models = mutableListOf<ModelEntity>()
        val modelsArray = root.optJSONArray("models") ?: JSONArray()
        for (i in 0 until modelsArray.length()) {
            val obj = modelsArray.getJSONObject(i)
            models.add(
                ModelEntity(
                    id = obj.optLong("id", 0),
                    brandName = obj.getString("brandName"),
                    modelName = obj.getString("modelName")
                )
            )
        }

        val faults = mutableListOf<FaultEntity>()
        val faultsArray = root.optJSONArray("faults") ?: JSONArray()
        for (i in 0 until faultsArray.length()) {
            val obj = faultsArray.getJSONObject(i)
            faults.add(FaultEntity(id = obj.optLong("id", 0), name = obj.getString("name")))
        }

        val repairTypes = mutableListOf<RepairTypeEntity>()
        val typesArray = root.optJSONArray("repairTypes") ?: JSONArray()
        for (i in 0 until typesArray.length()) {
            val obj = typesArray.getJSONObject(i)
            repairTypes.add(RepairTypeEntity(id = obj.optLong("id", 0), name = obj.getString("name")))
        }

        val prices = mutableListOf<PriceEntity>()
        val pricesArray = root.optJSONArray("prices") ?: JSONArray()
        for (i in 0 until pricesArray.length()) {
            val obj = pricesArray.getJSONObject(i)
            prices.add(
                PriceEntity(
                    id = obj.optLong("id", 0),
                    brand = obj.getString("brand"),
                    model = obj.getString("model"),
                    repairType = obj.getString("repairType"),
                    price = obj.getDouble("price")
                )
            )
        }

        val sObj = root.optJSONObject("settings")
        val settings = if (sObj != null) {
            AppSettingsEntity(
                shopName = sObj.optString("shopName", "UDM MOBILE REPAIR"),
                shopPhone = sObj.optString("shopPhone", "+94 77 123 4567"),
                shopAddress = sObj.optString("shopAddress", "No. 48, Main Street, Colombo"),
                whatsappNumber = sObj.optString("whatsappNumber", "+94771234567"),
                receiptFooter = sObj.optString("receiptFooter", "Thank you for choosing UDM Mobile Repair!"),
                currency = sObj.optString("currency", "Rs."),
                startingJobNumber = sObj.optInt("startingJobNumber", 1),
                autoReadySms = sObj.optBoolean("autoReadySms", true),
                smsTemplate = sObj.optString("smsTemplate", "UDM Mobile Repair\n\nDear {customer_name}, your phone repair Job #{job_number} is ready for collection. Total repair price: Rs. {total_price}. Balance: Rs. {balance}. Thank you."),
                autoRegistrationSms = sObj.optBoolean("autoRegistrationSms", true),
                registrationSmsTemplate = sObj.optString("registrationSmsTemplate", "UDM MOBILE REPAIR\nJob No: {JOB_NO}\n\nWe have received your phone for repair.\nYour repair job has been successfully registered.\n\nTotal: Rs. {TOTAL}\nPaid: Rs. {PAID}\nBalance: Rs. {BALANCE}\n\nWe will contact you when your phone is ready.\nThank you for choosing UDM Mobile Repair."),
                isDarkMode = sObj.optBoolean("isDarkMode", true)
            )
        } else AppSettingsEntity()

        val scannerLearning = mutableListOf<ScannerLearningEntity>()
        val learningArray = root.optJSONArray("scannerLearning") ?: JSONArray()
        for (i in 0 until learningArray.length()) {
            val obj = learningArray.getJSONObject(i)
            scannerLearning.add(
                ScannerLearningEntity(
                    rawOcrText = obj.getString("rawOcrText"),
                    confirmedJobNumber = obj.getString("confirmedJobNumber"),
                    detectedJobNumber = obj.optString("detectedJobNumber", ""),
                    confidence = obj.optDouble("confidence", 0.0).toFloat(),
                    preprocessingMethod = obj.optString("preprocessingMethod", ""),
                    characterSubstitutions = obj.optString("characterSubstitutions", ""),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    timesEncountered = obj.optInt("timesEncountered", 1)
                )
            )
        }

        return BackupData(
            repairs = repairs,
            repairItems = repairItems,
            payments = payments,
            statusHistory = statusHistory,
            customers = customers,
            brands = brands,
            models = models,
            faults = faults,
            repairTypes = repairTypes,
            prices = prices,
            settings = settings,
            scannerLearning = scannerLearning
        )

    }

    fun validateJson(jsonString: String): BackupValidationResult {
        if (jsonString.isBlank()) {
            return BackupValidationResult.Invalid("The backup file is empty.")
        }
        return try {
            val root = JSONObject(jsonString)
            val app = root.optString("app", "")
            if (!app.contains("UDM MOBILE REPAIR", ignoreCase = true) && !root.has("repairs")) {
                return BackupValidationResult.Invalid("Selected file is not a valid UDM Mobile Repair backup.")
            }
            if (!root.has("repairs")) {
                return BackupValidationResult.Invalid("Backup file is missing required repair data.")
            }

            val data = parseFromJson(jsonString)
            val summary = generateSummary(root, data)
            BackupValidationResult.Valid(data, summary)
        } catch (e: Exception) {
            BackupValidationResult.Invalid("Corrupted or invalid backup file: ${e.localizedMessage ?: "JSON parse error"}")
        }
    }

    fun generateSummary(root: JSONObject, data: BackupData): BackupSummary {
        val exportedAt = root.optLong("exportedAt", System.currentTimeMillis())
        val dateFmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
        val formattedDate = dateFmt.format(java.util.Date(exportedAt))

        return BackupSummary(
            app = root.optString("app", "UDM MOBILE REPAIR"),
            version = root.optInt("version", 1),
            exportedAt = exportedAt,
            formattedExportDate = formattedDate,
            repairCount = data.repairs.size,
            customerCount = data.customers.size,
            paymentCount = data.payments.size,
            priceCount = data.prices.size,
            brandCount = data.brands.size,
            modelCount = data.models.size,
            faultCount = data.faults.size,
            repairTypeCount = data.repairTypes.size,
            startingJobNumber = data.settings.startingJobNumber,
            shopName = data.settings.shopName
        )
    }
}

data class BackupSummary(
    val app: String,
    val version: Int,
    val exportedAt: Long,
    val formattedExportDate: String,
    val repairCount: Int,
    val customerCount: Int,
    val paymentCount: Int,
    val priceCount: Int,
    val brandCount: Int,
    val modelCount: Int,
    val faultCount: Int,
    val repairTypeCount: Int,
    val startingJobNumber: Int,
    val shopName: String
)

sealed class BackupValidationResult {
    data class Valid(
        val data: BackupData,
        val summary: BackupSummary
    ) : BackupValidationResult()

    data class Invalid(
        val reason: String
    ) : BackupValidationResult()
}

