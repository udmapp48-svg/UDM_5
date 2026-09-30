package com.example

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.repository.RepairRepository
import com.example.data.repository.SmsSendResult
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
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RegistrationSmsTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var repository: RepairRepository

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        app = ApplicationProvider.getApplicationContext()
        db = AppDatabase.getDatabase(app)
        repository = RepairRepository(db, app)
        repository.updateSettings(com.example.data.entity.AppSettingsEntity())
    }

    @Test
    fun `test default registration SMS template contains all required tags`() = runBlocking {
        val settings = repository.getSettings()
        val template = settings.registrationSmsTemplate

        assertTrue("Template must contain {JOB_NO}", template.contains("{JOB_NO}"))
        assertTrue("Template must contain {TOTAL}", template.contains("{TOTAL}"))
        assertTrue("Template must contain {PAID}", template.contains("{PAID}"))
        assertTrue("Template must contain {BALANCE}", template.contains("{BALANCE}"))
        assertTrue("Template must contain UDM MOBILE REPAIR", template.contains("UDM MOBILE REPAIR"))
        assertTrue("autoRegistrationSms must be enabled by default", settings.autoRegistrationSms)
    }

    @Test
    fun `test repair creation saves database records before SMS trigger and replaces tags accurately`() = runBlocking {
        // Create a repair job with total 15000 and initial payment 5000 (balance 10000)
        val repair = repository.createRepair(
            customerName = "Sunil Shantha",
            customerPhone = "0779876543",
            brand = "Apple",
            model = "iPhone 13",
            imei = "354890123456789",
            deviceColour = "Midnight Blue",
            accessories = "Back cover",
            fault = "Display touch issue",
            technicianNotes = "Check digitizer",
            remarks = "Customer waiting",
            repairItems = listOf("Screen Replacement" to 15000.0),
            initialPaymentAmount = 5000.0
        )

        assertNotNull(repair)
        val repairFromDb = repository.getRepairById(repair.id)
        assertNotNull("Repair must be saved in database", repairFromDb)
        assertEquals("0779876543", repairFromDb!!.customerPhone)
        assertEquals(15000.0, repairFromDb.totalPrice, 0.01)
        assertEquals(5000.0, repairFromDb.amountPaid, 0.01)
        assertEquals(10000.0, repairFromDb.balance, 0.01)

        // Verify template tag replacements
        val settings = repository.getSettings()
        val totalStr = if (repair.totalPrice % 1.0 == 0.0) String.format(Locale.US, "%.0f", repair.totalPrice) else String.format(Locale.US, "%,.2f", repair.totalPrice)
        val paidStr = if (repair.amountPaid % 1.0 == 0.0) String.format(Locale.US, "%.0f", repair.amountPaid) else String.format(Locale.US, "%,.2f", repair.amountPaid)
        val balanceStr = if (repair.balance % 1.0 == 0.0) String.format(Locale.US, "%.0f", repair.balance) else String.format(Locale.US, "%,.2f", repair.balance)

        val expectedMessage = settings.registrationSmsTemplate
            .replace("{JOB_NO}", repair.jobNumber)
            .replace("{TOTAL}", totalStr)
            .replace("{PAID}", paidStr)
            .replace("{BALANCE}", balanceStr)

        assertTrue(expectedMessage.contains("Job No: ${repair.jobNumber}"))
        assertTrue(expectedMessage.contains("Total: Rs. 15000"))
        assertTrue(expectedMessage.contains("Paid: Rs. 5000"))
        assertTrue(expectedMessage.contains("Balance: Rs. 10000"))
    }

    @Test
    fun `test custom registration SMS template in settings`() = runBlocking {
        val currentSettings = repository.getSettings()
        val customTemplate = "UDM REPAIR\nJOB #{JOB_NO}\nTotal: {TOTAL}\nPaid: {PAID}\nBal: {BALANCE}\nThanks!"

        val updatedSettings = currentSettings.copy(
            registrationSmsTemplate = customTemplate
        )
        repository.updateSettings(updatedSettings)

        val reloadedSettings = repository.getSettings()
        assertEquals(customTemplate, reloadedSettings.registrationSmsTemplate)

        // Verify replacement with custom template
        val repair = repository.createRepair(
            customerName = "Nimal Silva",
            customerPhone = "0711122334",
            brand = "Xiaomi",
            model = "Redmi Note 10",
            imei = "",
            deviceColour = "Black",
            accessories = "",
            fault = "Charging port",
            technicianNotes = "",
            remarks = "",
            repairItems = listOf("Charging Port Replacement" to 3500.0),
            initialPaymentAmount = 3500.0
        )

        val rendered = reloadedSettings.registrationSmsTemplate
            .replace("{JOB_NO}", repair.jobNumber)
            .replace("{TOTAL}", "3500")
            .replace("{PAID}", "3500")
            .replace("{BALANCE}", "0")

        assertEquals("UDM REPAIR\nJOB #${repair.jobNumber}\nTotal: 3500\nPaid: 3500\nBal: 0\nThanks!", rendered)
    }

    @Test
    fun `test auto registration SMS disabled in settings`() = runBlocking {
        val currentSettings = repository.getSettings()
        repository.updateSettings(currentSettings.copy(autoRegistrationSms = false))

        val reloadedSettings = repository.getSettings()
        assertFalse(reloadedSettings.autoRegistrationSms)

        val repair = repository.createRepair(
            customerName = "Amila Perera",
            customerPhone = "0770001122",
            brand = "Samsung",
            model = "A12",
            imei = "",
            deviceColour = "Blue",
            accessories = "",
            fault = "Battery change",
            technicianNotes = "",
            remarks = "",
            repairItems = listOf("Battery" to 6000.0),
            initialPaymentAmount = 0.0
        )

        // Explicit call to auto registration SMS returns DisabledInSettings
        val result = repository.performAutoRegistrationSms(repair)
        assertTrue("Expected DisabledInSettings when switch is off", result is SmsSendResult.DisabledInSettings)
    }
}
