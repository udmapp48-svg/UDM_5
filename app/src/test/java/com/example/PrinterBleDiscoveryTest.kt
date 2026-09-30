package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.printer.DiscoveredDevice
import com.example.printer.P50SProtocol
import com.example.printer.PrinterBleManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrinterBleDiscoveryTest {

    @Test
    fun `test P50S detection for physical printer name variations`() {
        // Physical name as stated by user: P50S-496A-BLE
        assertTrue(P50SProtocol.isPotentialP50SPrinter("P50S-496A-BLE"))
        assertTrue(P50SProtocol.isPotentialP50SPrinter("p50s-496a-ble"))
        assertTrue(P50SProtocol.isPotentialP50SPrinter("P50S-496A"))
        assertTrue(P50SProtocol.isPotentialP50SPrinter("P50S"))
        assertTrue(P50SProtocol.isPotentialP50SPrinter("Marklife P50S"))
        assertTrue(P50SProtocol.isPotentialP50SPrinter("Marklife_496A"))
        assertFalse(P50SProtocol.isPotentialP50SPrinter("Wireless_Earbuds"))
        assertFalse(P50SProtocol.isPotentialP50SPrinter("Unknown BLE Device"))
    }

    @Test
    fun `test DiscoveredDevice default parameters and types`() {
        val dev1 = DiscoveredDevice(
            name = "P50S-496A-BLE",
            address = "AA:BB:CC:DD:EE:FF",
            rssi = -55,
            isP50S = true
        )
        assertEquals("BLE", dev1.deviceType)
        assertEquals(1, dev1.packetCount)
        assertTrue(dev1.isP50S)

        val dev2 = DiscoveredDevice(
            name = "Unknown BLE Device",
            address = "11:22:33:44:55:66",
            rssi = -80,
            isP50S = false,
            deviceType = "BLE",
            serviceUuids = listOf("0000ff00-0000-1000-8000-00805f9b34fb"),
            manufacturerDataHex = "0x004C: 0215"
        )
        assertEquals("Unknown BLE Device", dev2.name)
        assertEquals("0x004C: 0215", dev2.manufacturerDataHex)
        assertEquals(1, dev2.serviceUuids.size)
    }

    @Test
    fun `test PrinterBleManager singleton and initial state`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = PrinterBleManager.getInstance(context)
        assertEquals(0, manager.scannedDevices.value.size)
        assertEquals(false, manager.isScanningFlow.value)
        assertEquals(false, manager.isPrinterConnected())
        // Default MTU 23 gives 20 bytes safe chunk size
        assertEquals(20, manager.getSafeChunkSize())
    }

    @Test
    fun `test printing when disconnected immediately returns clear error without hang`() = kotlinx.coroutines.test.runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = PrinterBleManager.getInstance(context)
        val result = manager.printTestReceipt()
        assertTrue(result is com.example.printer.PrintResult.Error)
        val errorMsg = (result as com.example.printer.PrintResult.Error).message
        assertTrue("Error must explain printer is disconnected or bluetooth needed: $errorMsg",
            errorMsg.contains("disconnected", ignoreCase = true) || errorMsg.contains("bluetooth", ignoreCase = true))
    }
}
