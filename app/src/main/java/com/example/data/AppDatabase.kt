package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.BrandDao
import com.example.data.dao.CustomerDao
import com.example.data.dao.FaultDao
import com.example.data.dao.ModelDao
import com.example.data.dao.PaymentDao
import com.example.data.dao.PriceDao
import com.example.data.dao.RepairDao
import com.example.data.dao.RepairItemDao
import com.example.data.dao.RepairTypeDao
import com.example.data.dao.ScannerLearningDao
import com.example.data.dao.SettingsDao
import com.example.data.dao.StatusHistoryDao
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
import kotlinx.coroutines.launch

@Database(
    entities = [
        RepairEntity::class,
        RepairItemEntity::class,
        PaymentEntity::class,
        StatusHistoryEntity::class,
        CustomerEntity::class,
        BrandEntity::class,
        ModelEntity::class,
        FaultEntity::class,
        RepairTypeEntity::class,
        PriceEntity::class,
        AppSettingsEntity::class,
        ScannerLearningEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun repairDao(): RepairDao
    abstract fun repairItemDao(): RepairItemDao
    abstract fun paymentDao(): PaymentDao
    abstract fun statusHistoryDao(): StatusHistoryDao
    abstract fun customerDao(): CustomerDao
    abstract fun brandDao(): BrandDao
    abstract fun modelDao(): ModelDao
    abstract fun faultDao(): FaultDao
    abstract fun repairTypeDao(): RepairTypeDao
    abstract fun priceDao(): PriceDao
    abstract fun settingsDao(): SettingsDao
    abstract fun scannerLearningDao(): ScannerLearningDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE app_settings ADD COLUMN autoRegistrationSms INTEGER NOT NULL DEFAULT 1")
                } catch (_: Exception) {}
                try {
                    db.execSQL("ALTER TABLE app_settings ADD COLUMN registrationSmsTemplate TEXT NOT NULL DEFAULT 'UDM MOBILE REPAIR\nJob No: {JOB_NO}\n\nWe have received your phone for repair.\nYour repair job has been successfully registered.\n\nTotal: Rs. {TOTAL}\nPaid: Rs. {PAID}\nBalance: Rs. {BALANCE}\n\nWe will contact you when your phone is ready.\nThank you for choosing UDM Mobile Repair.'")
                } catch (_: Exception) {}
            }
        }

        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `scanner_learning_records` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `rawOcrText` TEXT NOT NULL,
                            `confirmedJobNumber` TEXT NOT NULL,
                            `detectedJobNumber` TEXT NOT NULL,
                            `confidence` REAL NOT NULL,
                            `preprocessingMethod` TEXT NOT NULL,
                            `characterSubstitutions` TEXT NOT NULL,
                            `timestamp` INTEGER NOT NULL,
                            `timesEncountered` INTEGER NOT NULL
                        )
                    """.trimIndent())
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_scanner_learning_records_rawOcrText` ON `scanner_learning_records` (`rawOcrText`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_scanner_learning_records_confirmedJobNumber` ON `scanner_learning_records` (`confirmedJobNumber`)")
                } catch (_: Exception) {}
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "udm_repair_shop.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .fallbackToDestructiveMigration(false)

                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Seed default data in background
                            CoroutineScope(Dispatchers.IO).launch {
                                INSTANCE?.let { database ->
                                    populateInitialData(database)
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                // Also verify default data populated (e.g. if DB exists or opened)
                CoroutineScope(Dispatchers.IO).launch {
                    populateInitialDataIfNeeded(instance)
                }
                instance
            }
        }

        private suspend fun populateInitialData(db: AppDatabase) {
            db.brandDao().insertBrands(DefaultData.BRANDS)
            db.modelDao().insertModels(DefaultData.getModelsList())
            db.faultDao().insertFaults(DefaultData.FAULTS)
            db.repairTypeDao().insertRepairTypes(DefaultData.REPAIR_TYPES)
            db.priceDao().insertPrices(DefaultData.SAMPLE_PRICES)
            db.settingsDao().insertOrUpdate(AppSettingsEntity())
        }

        private suspend fun populateInitialDataIfNeeded(db: AppDatabase) {
            try {
                if (db.brandDao().getAllBrandsDirect().isEmpty()) {
                    populateInitialData(db)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
