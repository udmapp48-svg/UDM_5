package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.repository.RepairRepository
import kotlinx.coroutines.flow.first
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ReadySmsTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var repository: RepairRepository

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getDatabase(app)
        repository = RepairRepository(db, app)
    }

    @Test
    fun `test REPAIRING to READY transition saves READY immediately and marks readySmsSent`() = runBlocking {
        // Create a repair job with REPAIRING status
        val repair = repository.createRepair(
            customerName = "Kasun Perera",
            customerPhone = "0771234567",
            brand = "Samsung",
            model = "Galaxy S21",
            imei = "123456789012345",
            deviceColour = "Black",
            accessories = "None",
            fault = "Display replacement",
            technicianNotes = "New screen tested",
            remarks = "Urgent",
            repairItems = listOf("Screen Replacement" to 15000.0),
            initialPaymentAmount = 5000.0
        )
        assertNotNull(repair)
        val repairId = repair.id

        // Set status to REPAIRING
        val repairingRepair = repository.updateStatus(repairId, "REPAIRING", "In progress")
        assertNotNull(repairingRepair)
        assertEquals("REPAIRING", repairingRepair!!.status)
        assertFalse(repairingRepair.readySmsSent)

        // Now transition REPAIRING -> READY
        val readyRepair = repository.updateStatus(repairId, "READY", "Repair completed")
        assertNotNull(readyRepair)

        // Requirement 1, 3, 8: READY status is confirmed and saved immediately in DB
        assertEquals("READY", readyRepair!!.status)
        assertTrue(readyRepair.readySmsSent)

        // Check direct DB record
        val fromDb = repository.getRepairById(repairId)
        assertNotNull(fromDb)
        assertEquals("READY", fromDb!!.status)
        assertTrue(fromDb.readySmsSent)
    }

    @Test
    fun `test already READY job does not trigger duplicate update or change`() = runBlocking {
        val repair = repository.createRepair(
            customerName = "Amal Silva",
            customerPhone = "0719876543",
            brand = "Apple",
            model = "iPhone 13",
            imei = "",
            deviceColour = "Blue",
            accessories = "",
            fault = "Battery change",
            technicianNotes = "",
            remarks = "",
            repairItems = listOf("Battery Replacement" to 12000.0),
            initialPaymentAmount = 0.0
        )
        val repairId = repair.id

        // Move to READY
        val ready1 = repository.updateStatus(repairId, "READY")
        assertNotNull(ready1)
        assertEquals("READY", ready1!!.status)

        val historyCountAfterFirst = repository.getHistoryForRepair(repairId).first().size

        // Call updateStatus with "READY" again (simulating recomposition, re-save, opening again)
        val ready2 = repository.updateStatus(repairId, "READY")
        assertEquals("READY", ready2!!.status)

        // History count should NOT increase because status was already READY (no redundant transition)
        val historyCountAfterSecond = repository.getHistoryForRepair(repairId).first().size
        assertEquals(historyCountAfterFirst, historyCountAfterSecond)
    }

    @Test
    fun `test READY to REPAIRING to READY allows genuine new transition`() = runBlocking {
        val repair = repository.createRepair(
            customerName = "Sunil Shantha",
            customerPhone = "0765554433",
            brand = "Xiaomi",
            model = "Redmi Note 10",
            imei = "",
            deviceColour = "Grey",
            accessories = "",
            fault = "Charging port",
            technicianNotes = "",
            remarks = "",
            repairItems = listOf("Port Replacement" to 4000.0),
            initialPaymentAmount = 0.0
        )
        val repairId = repair.id

        // 1. Move to READY
        val ready1 = repository.updateStatus(repairId, "READY")
        assertEquals("READY", ready1!!.status)
        assertTrue(ready1.readySmsSent)

        // 2. Move back to REPAIRING (job left READY)
        val repairingAgain = repository.updateStatus(repairId, "REPAIRING", "Re-checking issues")
        assertEquals("REPAIRING", repairingAgain!!.status)
        // readySmsSent is reset to false upon leaving READY
        assertFalse(repairingAgain.readySmsSent)

        // 3. Move back to READY (genuine new transition)
        val readyAgain = repository.updateStatus(repairId, "READY", "Fixed second issue")
        assertEquals("READY", readyAgain!!.status)
        assertTrue(readyAgain.readySmsSent)
    }

    @Test
    fun `test explicit manual Send SMS Again returns accurate status without crash`() = runBlocking {
        val repair = repository.createRepair(
            customerName = "Nimal Fernando",
            customerPhone = "0781112233",
            brand = "Huawei",
            model = "Y9",
            imei = "",
            deviceColour = "Gold",
            accessories = "",
            fault = "Mic replacement",
            technicianNotes = "",
            remarks = "",
            repairItems = listOf("Mic Repair" to 3500.0),
            initialPaymentAmount = 0.0
        )

        // Call explicit manual send
        val result = repository.sendReadySmsExplicit(repair)

        // In Robolectric without SEND_SMS permission or telephony service, it returns a safe result (PermissionDenied or Failed) without crashing
        assertNotNull(result)
        // Check requirement 9: does not falsely report delivered
        assertFalse(result.message.contains("delivered", ignoreCase = true))
    }
}
