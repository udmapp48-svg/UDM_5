package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "repairs",
    indices = [
        Index(value = ["jobNumber"], unique = true),
        Index(value = ["customerPhone"]),
        Index(value = ["status"]),
        Index(value = ["receivedTimestamp"])
    ]
)
data class RepairEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobNumber: String, // Always 4 digits: "0001", "0014"
    val customerName: String,
    val customerPhone: String,
    val brand: String,
    val model: String,
    val imei: String = "",
    val deviceColour: String = "",
    val accessories: String = "", // e.g. "SIM Tray, Battery, Charger"
    val fault: String,
    val technicianNotes: String = "",
    val remarks: String = "",
    val totalPrice: Double = 0.0,
    val amountPaid: Double = 0.0,
    val balance: Double = 0.0,
    val paymentStatus: String = "UNPAID", // UNPAID, PARTIALLY PAID, PAID
    val status: String = "RECEIVED", // RECEIVED, CHECKING, REPAIRING, READY, DELIVERED, CANCELLED
    val receivedDate: String, // YYYY-MM-DD
    val receivedTime: String, // HH:mm
    val receivedTimestamp: Long = System.currentTimeMillis(),
    val deliveredTimestamp: Long? = null,
    val readySmsSent: Boolean = false
)

@Entity(
    tableName = "repair_items",
    indices = [Index(value = ["repairId"])]
)
data class RepairItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val repairId: Long,
    val repairType: String,
    val price: Double
)

@Entity(
    tableName = "payments",
    indices = [Index(value = ["repairId"])]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val repairId: Long,
    val paymentNumber: Int,
    val amount: Double,
    val date: String,
    val time: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "status_history",
    indices = [Index(value = ["repairId"])]
)
data class StatusHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val repairId: Long,
    val status: String,
    val date: String,
    val time: String,
    val timestamp: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val phone: String,
    val name: String,
    val lastVisitTimestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "brands",
    indices = [Index(value = ["name"], unique = true)]
)
data class BrandEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "models",
    indices = [Index(value = ["brandName", "modelName"], unique = true)]
)
data class ModelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val brandName: String,
    val modelName: String
)

@Entity(
    tableName = "faults",
    indices = [Index(value = ["name"], unique = true)]
)
data class FaultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "repair_types",
    indices = [Index(value = ["name"], unique = true)]
)
data class RepairTypeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)

@Entity(
    tableName = "prices",
    indices = [Index(value = ["brand", "model", "repairType"], unique = true)]
)
data class PriceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val brand: String,
    val model: String,
    val repairType: String,
    val price: Double
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val shopName: String = "UDM MOBILE REPAIR",
    val shopPhone: String = "+94 77 123 4567",
    val shopAddress: String = "No. 48, Main Street, Colombo",
    val whatsappNumber: String = "+94771234567",
    val receiptFooter: String = "Thank you for choosing UDM Mobile Repair! 30-day service warranty on parts.",
    val currency: String = "Rs.",
    val startingJobNumber: Int = 1,
    val autoReadySms: Boolean = true,
    val smsTemplate: String = "UDM Mobile Repair\n\nDear {customer_name}, your phone repair Job #{job_number} is ready for collection. Total repair price: Rs. {total_price}. Balance: Rs. {balance}. Thank you.",
    val autoRegistrationSms: Boolean = true,
    val registrationSmsTemplate: String = "UDM MOBILE REPAIR\nJob No: {JOB_NO}\n\nWe have received your phone for repair.\nYour repair job has been successfully registered.\n\nTotal: Rs. {TOTAL}\nPaid: Rs. {PAID}\nBalance: Rs. {BALANCE}\n\nWe will contact you when your phone is ready.\nThank you for choosing UDM Mobile Repair.",
    val isDarkMode: Boolean = true
)

@Entity(
    tableName = "scanner_learning_records",
    indices = [
        Index(value = ["rawOcrText"]),
        Index(value = ["confirmedJobNumber"])
    ]
)
data class ScannerLearningEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rawOcrText: String,
    val confirmedJobNumber: String,
    val detectedJobNumber: String = "",
    val confidence: Float = 0f,
    val preprocessingMethod: String = "",
    val characterSubstitutions: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val timesEncountered: Int = 1
)

