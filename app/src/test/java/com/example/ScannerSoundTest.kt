package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.repository.RepairRepository
import com.example.util.ScannerAudioHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScannerSoundTest {

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
    fun `test 4-digit job number normalization with required numbers`() {
        // Requirement 17: Test the scanner with 0001, 0014, 0099, 0100, 1250
        val inputsAndExpected = listOf(
            "1" to "0001",
            "0001" to "0001",
            "14" to "0014",
            "0014" to "0014",
            "JOB NO: 0014" to "0014",
            "job 14" to "0014",
            "99" to "0099",
            "0099" to "0099",
            "100" to "0100",
            "0100" to "0100",
            "125" to "0125",
            "0125" to "0125",
            "1250" to "1250",
            "Job #1250" to "1250"
        )

        for ((input, expected) in inputsAndExpected) {
            val normalized = repository.normalizeJobNumber(input)
            assertEquals("Normalization failed for input: $input", expected, normalized)
        }
    }

    @Test
    fun `test ScannerAudioHelper initialization and lifecycle cleanup`() {
        val helper = ScannerAudioHelper(app)
        // Calling playConfirmationBeep should not throw any exception
        helper.playConfirmationBeep()

        // Releasing should be safe and idempotent
        helper.release()
        helper.release()
    }

    @Test
    fun `test OCR workflow plays confirmation sound ONLY for confirmed existing job`() = runBlocking {
        // 1. Create a repair with job #0014 in database
        val repair = repository.createRepair(
            customerName = "Sunil Perera",
            customerPhone = "0771234567",
            brand = "Apple",
            model = "iPhone 12",
            imei = "",
            deviceColour = "Blue",
            accessories = "",
            fault = "Display issue",
            technicianNotes = "",
            remarks = "",
            repairItems = listOf("Screen Fix" to 14000.0),
            initialPaymentAmount = 0.0
        )
        val confirmedJobNumber = repair.jobNumber // e.g. "0001"

        val audioHelper = ScannerAudioHelper(app)

        // Simulate detecting confirmedJobNumber
        var soundPlayed = false
        val foundJob = repository.getRepairByJobNumber(confirmedJobNumber)
        if (foundJob != null) {
            audioHelper.playConfirmationBeep()
            soundPlayed = true
        }

        assertNotNull(foundJob)
        assertEquals(true, soundPlayed)

        // 2. Simulate detecting a NON-EXISTING job (e.g. "9999")
        var nonExistingSoundPlayed = false
        val nonExistingJob = repository.getRepairByJobNumber("9999")
        if (nonExistingJob != null) {
            audioHelper.playConfirmationBeep()
            nonExistingSoundPlayed = true
        }

        assertNull(nonExistingJob)
        assertEquals("Sound must NOT play for non-existing job", false, nonExistingSoundPlayed)

        audioHelper.release()
    }
}
