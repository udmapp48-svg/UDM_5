package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.ui.screens.NewRepairScreen
import com.example.ui.theme.UDMRepairTheme
import com.example.ui.viewmodel.RepairViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("UDM Mobile Repair", appName)
    }

    @Test
    fun `test 4-digit job number formatting`() {
        val num = 14
        val formatted = String.format(Locale.US, "%04d", num)
        assertEquals("0014", formatted)

        val singleDigit = 1
        assertEquals("0001", String.format(Locale.US, "%04d", singleDigit))

        val fourDigit = 1250
        assertEquals("1250", String.format(Locale.US, "%04d", fourDigit))
    }

    @Test
    fun `test job number normalization from OCR`() {
        val rawOcr1 = "JOB NO: 0014"
        val digits1 = rawOcr1.replace(Regex("[^0-9]"), "")
        val normalized1 = String.format(Locale.US, "%04d", digits1.toInt())
        assertEquals("0014", normalized1)

        val rawOcr2 = "14"
        val digits2 = rawOcr2.replace(Regex("[^0-9]"), "")
        val normalized2 = String.format(Locale.US, "%04d", digits2.toInt())
        assertEquals("0014", normalized2)
    }

    @Test
    fun `test NewRepairScreen renders without layout crash and input fields accept text`() {
        val application = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = RepairViewModel(application)

        composeTestRule.setContent {
            UDMRepairTheme {
                NewRepairScreen(
                    viewModel = viewModel,
                    onNavigateBack = {},
                    onNavigateToCustomerHistory = {},
                    onRepairCreated = {}
                )
            }
        }

        // Verify key inputs and actions are displayed
        composeTestRule.onNodeWithTag("input_customer_phone").assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_customer_name").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_repair_button").assertIsDisplayed()

        // Test typing into input fields (simulating keyboard interaction)
        composeTestRule.onNodeWithTag("input_customer_phone").performTextInput("0771234567")
        composeTestRule.onNodeWithTag("input_customer_name").performTextInput("John Doe")
    }
}
