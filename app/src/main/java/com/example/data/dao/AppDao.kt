package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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
import kotlinx.coroutines.flow.Flow

@Dao
interface RepairDao {
    @Query("SELECT * FROM repairs ORDER BY receivedTimestamp DESC")
    fun getAllRepairsFlow(): Flow<List<RepairEntity>>

    @Query("SELECT * FROM repairs ORDER BY receivedTimestamp DESC")
    suspend fun getAllRepairsDirect(): List<RepairEntity>

    @Query("SELECT * FROM repairs WHERE id = :id LIMIT 1")
    suspend fun getRepairById(id: Long): RepairEntity?

    @Query("SELECT * FROM repairs WHERE jobNumber = :jobNumber LIMIT 1")
    suspend fun getRepairByJobNumber(jobNumber: String): RepairEntity?

    @Query("SELECT * FROM repairs WHERE customerPhone = :phone ORDER BY receivedTimestamp DESC")
    suspend fun getRepairsByCustomerPhone(phone: String): List<RepairEntity>

    @Query("SELECT * FROM repairs WHERE customerPhone = :phone ORDER BY receivedTimestamp DESC")
    fun getRepairsByCustomerPhoneFlow(phone: String): Flow<List<RepairEntity>>

    @Query("SELECT jobNumber FROM repairs ORDER BY id DESC LIMIT 1")
    suspend fun getLatestJobNumber(): String?

    @Query("SELECT jobNumber FROM repairs")
    suspend fun getAllJobNumbers(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRepair(repair: RepairEntity): Long

    @Update
    suspend fun updateRepair(repair: RepairEntity)

    @Delete
    suspend fun deleteRepair(repair: RepairEntity)

    @Query("DELETE FROM repairs WHERE id = :id")
    suspend fun deleteRepairById(id: Long)
}

@Dao
interface RepairItemDao {
    @Query("SELECT * FROM repair_items WHERE repairId = :repairId")
    fun getItemsForRepair(repairId: Long): Flow<List<RepairItemEntity>>

    @Query("SELECT * FROM repair_items WHERE repairId = :repairId")
    suspend fun getItemsForRepairDirect(repairId: Long): List<RepairItemEntity>

    @Query("SELECT * FROM repair_items")
    suspend fun getAllRepairItemsDirect(): List<RepairItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: RepairItemEntity): Long

    @Update
    suspend fun updateItem(item: RepairItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<RepairItemEntity>)

    @Delete
    suspend fun deleteItem(item: RepairItemEntity)

    @Query("DELETE FROM repair_items WHERE repairId = :repairId")
    suspend fun deleteItemsForRepair(repairId: Long)
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE repairId = :repairId ORDER BY paymentNumber ASC")
    fun getPaymentsForRepair(repairId: Long): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE repairId = :repairId ORDER BY paymentNumber ASC")
    suspend fun getPaymentsForRepairDirect(repairId: Long): List<PaymentEntity>

    @Query("SELECT * FROM payments ORDER BY timestamp DESC")
    fun getAllPaymentsFlow(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments ORDER BY timestamp DESC")
    suspend fun getAllPaymentsDirect(): List<PaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(payments: List<PaymentEntity>)

    @Update
    suspend fun updatePayment(payment: PaymentEntity)

    @Delete
    suspend fun deletePayment(payment: PaymentEntity)

    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun deletePaymentById(id: Long)

    @Query("DELETE FROM payments WHERE repairId = :repairId")
    suspend fun deletePaymentsForRepair(repairId: Long)
}

@Dao
interface StatusHistoryDao {
    @Query("SELECT * FROM status_history WHERE repairId = :repairId ORDER BY timestamp ASC")
    fun getHistoryForRepair(repairId: Long): Flow<List<StatusHistoryEntity>>

    @Query("SELECT * FROM status_history WHERE repairId = :repairId ORDER BY timestamp ASC")
    suspend fun getHistoryForRepairDirect(repairId: Long): List<StatusHistoryEntity>

    @Query("SELECT * FROM status_history")
    suspend fun getAllHistoryDirect(): List<StatusHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: StatusHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistories(histories: List<StatusHistoryEntity>)

    @Query("DELETE FROM status_history WHERE repairId = :repairId")
    suspend fun deleteHistoryForRepair(repairId: Long)
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY lastVisitTimestamp DESC")
    fun getAllCustomersFlow(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers ORDER BY lastVisitTimestamp DESC")
    suspend fun getAllCustomersDirect(): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE phone = :phone LIMIT 1")
    suspend fun getCustomerByPhone(phone: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(customers: List<CustomerEntity>)
}

@Dao
interface BrandDao {
    @Query("SELECT * FROM brands ORDER BY name ASC")
    fun getAllBrandsFlow(): Flow<List<BrandEntity>>

    @Query("SELECT * FROM brands ORDER BY name ASC")
    suspend fun getAllBrandsDirect(): List<BrandEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBrand(brand: BrandEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBrands(brands: List<BrandEntity>)

    @Update
    suspend fun updateBrand(brand: BrandEntity)

    @Delete
    suspend fun deleteBrand(brand: BrandEntity)
}

@Dao
interface ModelDao {
    @Query("SELECT * FROM models WHERE LOWER(brandName) = LOWER(:brandName) ORDER BY modelName ASC")
    fun getModelsForBrand(brandName: String): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models WHERE LOWER(brandName) = LOWER(:brandName) ORDER BY modelName ASC")
    suspend fun getModelsForBrandDirect(brandName: String): List<ModelEntity>

    @Query("SELECT * FROM models ORDER BY modelName ASC")
    fun getAllModelsFlow(): Flow<List<ModelEntity>>

    @Query("SELECT * FROM models ORDER BY modelName ASC")
    suspend fun getAllModelsDirect(): List<ModelEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertModel(model: ModelEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertModels(models: List<ModelEntity>)

    @Delete
    suspend fun deleteModel(model: ModelEntity)
}

@Dao
interface FaultDao {
    @Query("SELECT * FROM faults ORDER BY name ASC")
    fun getAllFaultsFlow(): Flow<List<FaultEntity>>

    @Query("SELECT * FROM faults ORDER BY name ASC")
    suspend fun getAllFaultsDirect(): List<FaultEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFault(fault: FaultEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFaults(faults: List<FaultEntity>)

    @Delete
    suspend fun deleteFault(fault: FaultEntity)
}

@Dao
interface RepairTypeDao {
    @Query("SELECT * FROM repair_types ORDER BY name ASC")
    fun getAllRepairTypesFlow(): Flow<List<RepairTypeEntity>>

    @Query("SELECT * FROM repair_types ORDER BY name ASC")
    suspend fun getAllRepairTypesDirect(): List<RepairTypeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRepairType(repairType: RepairTypeEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRepairTypes(types: List<RepairTypeEntity>)

    @Delete
    suspend fun deleteRepairType(repairType: RepairTypeEntity)
}

@Dao
interface PriceDao {
    @Query("SELECT * FROM prices ORDER BY brand ASC, model ASC")
    fun getAllPricesFlow(): Flow<List<PriceEntity>>

    @Query("SELECT * FROM prices ORDER BY brand ASC, model ASC")
    suspend fun getAllPricesDirect(): List<PriceEntity>

    @Query("SELECT * FROM prices WHERE LOWER(brand) = LOWER(:brand) AND LOWER(model) = LOWER(:model) AND LOWER(repairType) = LOWER(:repairType) LIMIT 1")
    suspend fun findPrice(brand: String, model: String, repairType: String): PriceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrice(price: PriceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrices(prices: List<PriceEntity>)

    @Update
    suspend fun updatePrice(price: PriceEntity)

    @Delete
    suspend fun deletePrice(price: PriceEntity)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun getSettingsFlow(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettings(): AppSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: AppSettingsEntity)
}

@Dao
interface ScannerLearningDao {
    @Query("SELECT * FROM scanner_learning_records ORDER BY timesEncountered DESC, timestamp DESC")
    fun getAllRecordsFlow(): Flow<List<ScannerLearningEntity>>

    @Query("SELECT * FROM scanner_learning_records ORDER BY timesEncountered DESC, timestamp DESC")
    suspend fun getAllRecordsDirect(): List<ScannerLearningEntity>

    @Query("SELECT COUNT(*) FROM scanner_learning_records")
    fun getRecordCountFlow(): Flow<Int>

    @Query("SELECT COUNT(*) FROM scanner_learning_records")
    suspend fun getRecordCount(): Int

    @Query("SELECT * FROM scanner_learning_records WHERE rawOcrText = :rawText AND confirmedJobNumber = :confirmed LIMIT 1")
    suspend fun findRecord(rawText: String, confirmed: String): ScannerLearningEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: ScannerLearningEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<ScannerLearningEntity>)

    @Update
    suspend fun updateRecord(record: ScannerLearningEntity)

    @Query("DELETE FROM scanner_learning_records WHERE id NOT IN (SELECT id FROM scanner_learning_records ORDER BY timesEncountered DESC, timestamp DESC LIMIT :keepCount)")
    suspend fun pruneOldRecords(keepCount: Int = 100)

    @Query("DELETE FROM scanner_learning_records")
    suspend fun deleteAllRecords()
}

