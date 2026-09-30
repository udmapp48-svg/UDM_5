package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.entity.AppSettingsEntity
import com.example.data.repository.RepairRepository
import com.example.util.BackupFileHelper
import com.example.util.BackupManager
import com.example.util.BackupValidationResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupRestoreFileTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var repository: RepairRepository

    @Before
    fun setUp() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getDatabase(app)
        repository = RepairRepository(db, app)
        repository.updateSettings(AppSettingsEntity())
    }

    @Test
    fun `test backup file name matches format UDM_Backup_date_time_udmbackup`() {
        val fileName = BackupFileHelper.generateBackupFileName()
        assertTrue("Filename must start with UDM_Backup_", fileName.startsWith("UDM_Backup_"))
        assertTrue("Filename must end with .udmbackup", fileName.endsWith(".udmbackup"))
        // Check date pattern UDM_Backup_yyyy-MM-dd_HH-mm.udmbackup
        val regex = Regex("^UDM_Backup_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}(_\\d+)?\\.udmbackup$")
        assertTrue("Filename $fileName must match format regex", regex.matches(fileName))
    }

    @Test
    fun `test backup file generation creates real physical non-zero file in UDM MOBILE REPAIR Backups folder`() = runBlocking {
        // Create sample repair with leading-zero job number
        val repair = repository.createRepair(
            customerName = "Kavinda Fernando",
            customerPhone = "0771234567",
            brand = "Apple",
            model = "iPhone 12",
            imei = "354123098765432",
            deviceColour = "Pacific Blue",
            accessories = "Charger",
            fault = "Display Broken",
            technicianNotes = "Replace OLED",
            remarks = "Urgent",
            repairItems = listOf("Display Replacement" to 22000.0),
            initialPaymentAmount = 10000.0
        )

        assertNotNull(repair)
        val data = repository.getAllDataForBackup()
        val json = BackupManager.exportToJson(data)

        val result = BackupFileHelper.saveBackupToFile(app, json)
        assertTrue("BackupFileHelper result must be success", result.success)
        assertNotNull("Generated file must not be null", result.file)

        val file = result.file!!
        assertTrue("Physical backup file must exist", file.exists())
        assertTrue("Physical backup file must be greater than 0 bytes (was ${file.length()})", file.length() > 0)
        assertTrue("File path must contain UDM MOBILE REPAIR/Backups", file.absolutePath.contains("UDM MOBILE REPAIR/Backups"))
        assertTrue("File name must end with .udmbackup", file.name.endsWith(".udmbackup"))

        // Validate summary returned
        assertNotNull(result.summary)
        assertTrue("Summary repair count must be >= 1", result.summary!!.repairCount >= 1)
        assertEquals("UDM MOBILE REPAIR", result.summary!!.app)
    }

    @Test
    fun `test backup data includes all required tables and settings`() = runBlocking {
        val customSettings = AppSettingsEntity(
            shopName = "UDM Mobile Repair Test Shop",
            shopPhone = "+94 77 999 8888",
            startingJobNumber = 100,
            autoReadySms = true,
            autoRegistrationSms = true,
            registrationSmsTemplate = "Custom Registration Template for Job {JOB_NO}"
        )
        repository.updateSettings(customSettings)

        val repair = repository.createRepair(
            customerName = "Ruwan Kumara",
            customerPhone = "0715554433",
            brand = "Samsung",
            model = "Galaxy M02",
            imei = "",
            deviceColour = "Black",
            accessories = "Cover",
            fault = "Charging Pin",
            technicianNotes = "Clean port first",
            remarks = "Collected in morning",
            repairItems = listOf("Charging Pin" to 1500.0),
            initialPaymentAmount = 1500.0
        )

        val data = repository.getAllDataForBackup()
        val json = BackupManager.exportToJson(data)

        // Verify root metadata
        assertTrue(json.contains("\"app\": \"UDM MOBILE REPAIR\""))
        assertTrue(json.contains("\"version\":"))
        assertTrue(json.contains("\"repairs\":"))
        assertTrue(json.contains("\"repairItems\":"))
        assertTrue(json.contains("\"payments\":"))
        assertTrue(json.contains("\"statusHistory\":"))
        assertTrue(json.contains("\"customers\":"))
        assertTrue(json.contains("\"brands\":"))
        assertTrue(json.contains("\"models\":"))
        assertTrue(json.contains("\"faults\":"))
        assertTrue(json.contains("\"repairTypes\":"))
        assertTrue(json.contains("\"prices\":"))
        assertTrue(json.contains("\"settings\":"))
        assertTrue(json.contains("Custom Registration Template for Job {JOB_NO}"))
        assertTrue(json.contains(repair.jobNumber))
    }

    @Test
    fun `test restore from backup preserves leading zero job numbers and all records`() = runBlocking {
        // Create 2 repairs with explicit formatting
        val repair1 = repository.createRepair(
            customerName = "Amila Perera",
            customerPhone = "0770011223",
            brand = "Apple",
            model = "iPhone 11",
            imei = "123456789012345",
            deviceColour = "White",
            accessories = "None",
            fault = "Battery",
            technicianNotes = "Original battery",
            remarks = "Test remarks 1",
            repairItems = listOf("Battery" to 7500.0),
            initialPaymentAmount = 2500.0
        )

        val backupData = repository.getAllDataForBackup()
        val exportedJson = BackupManager.exportToJson(backupData)

        // Validate json before restore
        val validation = BackupManager.validateJson(exportedJson)
        assertTrue("Validation must succeed", validation is BackupValidationResult.Valid)
        val validResult = validation as BackupValidationResult.Valid
        assertTrue("Summary contains repair", validResult.summary.repairCount >= 1)

        // Wipe / clear current database on IO dispatcher
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            db.clearAllTables()
            assertEquals(0, db.repairDao().getAllRepairsDirect().size)
        }

        // Restore from backup (which also safely clears and restores on IO)
        repository.restoreData(validResult.data, clearExisting = true)

        // Verify restored records
        val restoredRepairs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            db.repairDao().getAllRepairsDirect()
        }
        assertTrue("Restored repairs must not be empty", restoredRepairs.isNotEmpty())

        val restoredRepair = repository.getRepairByJobNumber(repair1.jobNumber)
        assertNotNull("Repair with original job number must exist", restoredRepair)
        assertEquals("Job number format must be preserved exactly", repair1.jobNumber, restoredRepair!!.jobNumber)
        assertEquals("Amila Perera", restoredRepair.customerName)
        assertEquals("0770011223", restoredRepair.customerPhone)
        assertEquals(7500.0, restoredRepair.totalPrice, 0.01)
        assertEquals(2500.0, restoredRepair.amountPaid, 0.01)
        assertEquals(5000.0, restoredRepair.balance, 0.01)
    }

    @Test
    fun `test backup validation rejects invalid and empty files`() {
        // Empty string
        val emptyResult = BackupManager.validateJson("")
        assertTrue(emptyResult is BackupValidationResult.Invalid)

        // Random non-JSON string
        val invalidTextResult = BackupManager.validateJson("This is just plain text, not a backup")
        assertTrue(invalidTextResult is BackupValidationResult.Invalid)

        // JSON without UDM header or repairs
        val wrongJsonResult = BackupManager.validateJson("{\"something\": \"else\"}")
        assertTrue(wrongJsonResult is BackupValidationResult.Invalid)
    }

    @Test
    fun `test getAvailableBackupFiles discovers saved backup files`() = runBlocking {
        val data = repository.getAllDataForBackup()
        val json = BackupManager.exportToJson(data)

        val saveResult = BackupFileHelper.saveBackupToFile(app, json)
        assertTrue(saveResult.success)

        val availableFiles = BackupFileHelper.getAvailableBackupFiles(app)
        assertTrue("Available backup files list must not be empty", availableFiles.isNotEmpty())

        val found = availableFiles.find { it.name == saveResult.fileName }
        assertNotNull("Saved file must be found in available backup files", found)
        assertTrue("Found file must have positive size", found!!.sizeBytes > 0)
    }

    @Test
    fun `test scanner learning records are included in backup and restored`() = runBlocking {
        repository.saveScannerLearning(
            rawOcrText = "+ OOIZS",
            confirmedJobNumber = "00125",
            detectedJobNumber = "",
            confidence = 0.95f,
            preprocessingMethod = "CONTRAST_SHARPEN",
            characterSubstitutions = "O->0,I->1,Z->2,S->5"
        )

        val beforeBackup = repository.getAllScannerLearningDirect()
        assertEquals(1, beforeBackup.size)

        val data = repository.getAllDataForBackup()
        val json = BackupManager.exportToJson(data)
        assertTrue(json.contains("scannerLearning"))
        assertTrue(json.contains("00125"))

        // Reset learning data
        repository.resetScannerLearning()
        assertEquals(0, repository.getAllScannerLearningDirect().size)

        // Restore backup
        val validation = BackupManager.validateJson(json) as BackupValidationResult.Valid
        repository.restoreData(validation.data, clearExisting = false)

        val afterRestore = repository.getAllScannerLearningDirect()
        assertEquals(1, afterRestore.size)
        assertEquals("00125", afterRestore[0].confirmedJobNumber)
        assertEquals("+ OOIZS", afterRestore[0].rawOcrText)
    }
}

