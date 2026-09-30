package com.example

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun test_multiple_repair_items_total_calculation() {
        val repairItems = listOf(
            "Display Replacement" to 5500.0,
            "Charging Port" to 1500.0,
            "Battery Replacement" to 2500.0
        )
        val totalPrice = repairItems.sumOf { it.second }
        assertEquals(9500.0, totalPrice, 0.001)

        val advancePaid = 2000.0
        val balance = (totalPrice - advancePaid).coerceAtLeast(0.0)
        assertEquals(7500.0, balance, 0.001)

        val paymentStatus = when {
            totalPrice > 0 && advancePaid >= totalPrice -> "PAID"
            advancePaid > 0 -> "PARTIALLY PAID"
            else -> "UNPAID"
        }
        assertEquals("PARTIALLY PAID", paymentStatus)
    }

    @Test
    fun test_full_payment_sets_paid_and_delivered() {
        val repairItems = listOf(
            "Display Replacement" to 5500.0,
            "Battery Replacement" to 2500.0
        )
        val totalPrice = repairItems.sumOf { it.second }
        val totalPaid = 8000.0
        val balance = (totalPrice - totalPaid).coerceAtLeast(0.0)

        val paymentStatus = when {
            totalPrice > 0 && totalPaid >= totalPrice -> "PAID"
            totalPaid > 0 -> "PARTIALLY PAID"
            else -> "UNPAID"
        }
        val repairStatus = if (totalPrice > 0 && totalPaid >= totalPrice) "DELIVERED" else "RECEIVED"

        assertEquals(0.0, balance, 0.001)
        assertEquals("PAID", paymentStatus)
        assertEquals("DELIVERED", repairStatus)
    }

    @Test
    fun test_price_formatting_integer_and_decimals() {
        val price1 = 5500.0
        val formatted1 = if (price1 % 1.0 == 0.0) price1.toInt().toString() else String.format(Locale.US, "%.2f", price1)
        assertEquals("5500", formatted1)

        val price2 = 1250.50
        val formatted2 = if (price2 % 1.0 == 0.0) price2.toInt().toString() else String.format(Locale.US, "%.2f", price2)
        assertEquals("1250.50", formatted2)
    }
}
