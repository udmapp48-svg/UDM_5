package com.example

import com.example.data.entity.ScannerLearningEntity
import com.example.util.JobNumberOcrParser
import com.example.util.OcrScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JobNumberOcrParserTest {

    @Test
    fun `test all requested plus marker job number test cases`() {
        val testCases = listOf(
            "+ 00001" to "00001",
            "+ 00012" to "00012",
            "+ 00125" to "00125",
            "+ 00126" to "00126",
            "+ 00150" to "00150",
            "+ 01005" to "01005",
            "+ 09999" to "09999",
            "+ 10000" to "10000"
        )

        for ((input, expected) in testCases) {
            val result = JobNumberOcrParser.extractJobNumberResult(input)
            assertNotNull("Failed to detect $input", result)
            assertTrue("Result must have plus anchor for $input", result!!.hasPlusAnchor)
            assertFalse("Result must not be marked unclear for $input", result.isUnclear)
            assertEquals("Detection mismatch for $input", expected, result.jobNumber)

            // Confirm '+' is NOT part of the job number
            assertFalse("Job number must not contain '+'", result.jobNumber.contains("+"))

            // Leading zero integrity: must not be stripped to int
            assertEquals(expected.length, result.jobNumber.length)

            // Backward-compatible method
            val simple = JobNumberOcrParser.extractJobNumberFromText(input)
            assertEquals(expected, simple)
        }
    }

    @Test
    fun `test plus marker formats with attached digits and spaces`() {
        assertEquals("00125", JobNumberOcrParser.extractJobNumberFromText("+00125"))
        assertEquals("00125", JobNumberOcrParser.extractJobNumberFromText("+ 00125"))
        assertEquals("00125", JobNumberOcrParser.extractJobNumberFromText("+  00125"))
        assertEquals("00125", JobNumberOcrParser.extractJobNumberFromText("+ 0 0 1 2 5"))
        assertEquals("00001", JobNumberOcrParser.extractJobNumberFromText("+00001"))
        assertEquals("01005", JobNumberOcrParser.extractJobNumberFromText("+ 01005"))
        assertEquals("10000", JobNumberOcrParser.extractJobNumberFromText("+10000"))
    }

    @Test
    fun `test ignores surrounding sticker noise like phone, price, date, IMEI`() {
        val stickerText = """
            UDM MOBILE REPAIR
            Date: 2026-09-27
            Customer Phone: 0771234567
            IMEI: 354921098765432
            + 00125
            Price: Rs. 5,500.00
            Display Replacement
        """.trimIndent()

        val result = JobNumberOcrParser.extractJobNumberResult(stickerText)
        assertNotNull(result)
        assertEquals("00125", result!!.jobNumber)
        assertTrue(result.hasPlusAnchor)
        assertFalse(result.isUnclear)
    }

    @Test
    fun `test common OCR lookalikes after plus marker`() {
        // O instead of 0
        val resO = JobNumberOcrParser.extractJobNumberResult("+ O0125")
        assertNotNull(resO)
        assertEquals("00125", resO!!.jobNumber)

        // S instead of 5
        val resS = JobNumberOcrParser.extractJobNumberResult("+ 0012S")
        assertNotNull(resS)
        assertEquals("00125", resS!!.jobNumber)

        // l/I instead of 1
        val resI = JobNumberOcrParser.extractJobNumberResult("+ 00I25")
        assertNotNull(resI)
        assertEquals("00125", resI!!.jobNumber)

        // Z instead of 2
        val resZ = JobNumberOcrParser.extractJobNumberResult("+ 001Z5")
        assertNotNull(resZ)
        assertEquals("00125", resZ!!.jobNumber)

        // G instead of 6
        val resG = JobNumberOcrParser.extractJobNumberResult("+ 0012G")
        assertNotNull(resG)
        assertEquals("00126", resG!!.jobNumber)

        // B instead of 8
        val resB = JobNumberOcrParser.extractJobNumberResult("+ 0000B")
        assertNotNull(resB)
        assertEquals("00008", resB!!.jobNumber)
    }

    @Test
    fun `test adaptive learning from confirmed user correction`() {
        // Case: OCR heavily confused multiple characters on shop sticker "+ OOIZS"
        // Without learned records, 4 substitutions exceeds standard limit of 2, so marked unclear
        val rawScan = "+ OOIZS"
        val unlearned = JobNumberOcrParser.extractJobNumberResult(rawScan)
        assertNotNull(unlearned)
        assertTrue("Without learning, heavy confusion must not silently guess", unlearned!!.isUnclear)

        // Now user confirmed that this sticker was Job Number 00125
        val learnedRecord = ScannerLearningEntity(
            rawOcrText = "+ OOIZS",
            confirmedJobNumber = "00125",
            detectedJobNumber = "",
            confidence = 0.98f,
            characterSubstitutions = "O->0,I->1,Z->2,S->5",
            timesEncountered = 2
        )

        // When scanned again with learned record present:
        val learnedResult = JobNumberOcrParser.extractJobNumberResult(rawScan, listOf(learnedRecord))
        assertNotNull(learnedResult)
        assertFalse("Must resolve clearly using learned pattern", learnedResult!!.isUnclear)
        assertEquals("00125", learnedResult.jobNumber)
        assertTrue("Learned pattern flag must be true", learnedResult.learnedPatternApplied)
    }

    @Test
    fun `test safety against blind replacements on unrelated text`() {
        // Blind replacement safety: "OO" on unrelated text must NOT guess a random number
        val unrelated = "PHONE MODEL: SAMS-OOS"
        val result = JobNumberOcrParser.extractJobNumberResult(unrelated)
        // Must either be null or not match as a valid job number
        assertTrue(result == null || result.isUnclear || result.jobNumber != "005")
    }

    @Test
    fun `test unclear characters after plus do not silently guess and are marked unclear`() {
        // Completely non-digit characters
        val resUnclear = JobNumberOcrParser.extractJobNumberResult("+ ABCXYZ")
        assertNotNull(resUnclear)
        assertTrue("Must be marked unclear", resUnclear!!.isUnclear)
        assertTrue("Job number must be empty when unclear", resUnclear.jobNumber.isEmpty())

        // Ambiguous symbol sequence
        val resSymbols = JobNumberOcrParser.extractJobNumberResult("+ ??##")
        assertNotNull(resSymbols)
        assertTrue("Must be marked unclear", resSymbols!!.isUnclear)
    }

    @Test
    fun `test fallback mode for legacy tags without plus marker`() {
        val legacyCases = listOf(
            "0001" to "0001",
            "0002" to "0002",
            "0006" to "0006",
            "0008" to "0008",
            "0014" to "0014",
            "0025" to "0025",
            "0105" to "0105",
            "1005" to "1005",
            "9999" to "9999"
        )

        for ((input, expected) in legacyCases) {
            val detected = JobNumberOcrParser.extractJobNumberFromText(input)
            assertNotNull("Failed legacy detect $input", detected)
            assertEquals("Detection mismatch for $input", expected, detected)
        }

        assertEquals("0006", JobNumberOcrParser.extractJobNumberFromText("JOB NO: 0006"))
        assertEquals("0008", JobNumberOcrParser.extractJobNumberFromText("JOB # 0008"))
        assertEquals("0014", JobNumberOcrParser.extractJobNumberFromText("JOB NO. 0014"))
        assertEquals("0025", JobNumberOcrParser.extractJobNumberFromText("No: 0025"))
        assertEquals("0006", JobNumberOcrParser.extractJobNumberFromText("0 0 0 6"))
    }

    @Test
    fun `test selectBestResult prefers plus anchor and clear over unclear`() {
        val fallback = OcrScanResult(jobNumber = "0014", hasPlusAnchor = false, confidence = 0.8f)
        val plusMatch = OcrScanResult(jobNumber = "00125", hasPlusAnchor = true, confidence = 1.0f)
        val unclear = OcrScanResult(jobNumber = "", hasPlusAnchor = true, confidence = 0.2f, isUnclear = true)

        val best1 = JobNumberOcrParser.selectBestResult(fallback, plusMatch)
        assertEquals("00125", best1?.jobNumber)

        val best2 = JobNumberOcrParser.selectBestResult(plusMatch, unclear)
        assertEquals("00125", best2?.jobNumber)
    }
}

