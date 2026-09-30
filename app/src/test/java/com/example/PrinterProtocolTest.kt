package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.RepairEntity
import com.example.data.entity.RepairItemEntity
import com.example.printer.P50SProtocol
import com.example.printer.PrinterPreferences
import com.example.printer.ReceiptBitmapGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrinterProtocolTest {

    @Test
    fun `test P50S target device name and matching`() {
        assertEquals("P50S-496A-BLE", P50SProtocol.TARGET_DEVICE_NAME_EXACT)
        assertTrue(P50SProtocol.isPotentialP50SPrinter("P50S-496A-BLE"))
        assertTrue(P50SProtocol.isPotentialP50SPrinter("p50s-496a-ble"))
        assertTrue(P50SProtocol.isPotentialP50SPrinter("P50S"))
        assertTrue(P50SProtocol.isPotentialP50SPrinter("Marklife P50"))
        assertTrue(P50SProtocol.isPotentialP50SPrinter("PRINTER_BLE"))
        assertFalse(P50SProtocol.isPotentialP50SPrinter("Headphones_BT"))
        assertFalse(P50SProtocol.isPotentialP50SPrinter(null))
        assertFalse(P50SProtocol.isPotentialP50SPrinter(""))
    }

    @Test
    fun `test flow control credit parsing`() {
        val notify4 = byteArrayOf(0x01, 0x04)
        assertEquals(4, P50SProtocol.parseFlowControlNotification(notify4))

        val notify1 = byteArrayOf(0x01, 0x01)
        assertEquals(1, P50SProtocol.parseFlowControlNotification(notify1))

        val invalid = byteArrayOf(0x02, 0x01)
        assertEquals(null, P50SProtocol.parseFlowControlNotification(invalid))
    }

    @Test
    fun `test Zlib Level 0 stored block compression produces RFC 1950 header`() {
        val sampleData = ByteArray(100) { it.toByte() }
        val compressed = P50SProtocol.compressZlib1KbLevel0(sampleData)

        // RFC 1950 header: CMF = 0x28, FLG = 0x15
        assertEquals(0x28.toByte(), compressed[0])
        assertEquals(0x15.toByte(), compressed[1])

        // Total size should be 2 (zlib header) + 5 (deflate stored block header) + 100 (data) + 4 (adler32) = 111
        assertEquals(111, compressed.size)
    }

    @Test
    fun `test image command building`() {
        val compressedData = byteArrayOf(1, 2, 3, 4, 5)
        val cmd = P50SProtocol.buildImageCommand(heightPixels = 10, compressedData = compressedData)

        assertEquals(15, cmd.size) // 10 byte header + 5 byte data
        assertEquals(0x1F.toByte(), cmd[0])
        assertEquals(0x10.toByte(), cmd[1])
        assertEquals(0x00.toByte(), cmd[2])
        assertEquals(48.toByte(), cmd[3]) // 48 bytes per row
        assertEquals(0x00.toByte(), cmd[4])
        assertEquals(10.toByte(), cmd[5]) // height = 10
    }

    @Test
    fun `test buildPrintPayload incorporates required P50S commands`() {
        val dummyData = ByteArray(48 * 10)
        val payload = P50SProtocol.buildPrintPayload(dummyData, totalHeight = 10)

        // Must start with CMD_SET_BT_TYPE [0x1F, 0xB2, 0x00]
        assertEquals(0x1F.toByte(), payload[0])
        assertEquals(0xB2.toByte(), payload[1])
        assertEquals(0x00.toByte(), payload[2])

        // Must contain continuous paper command [0x1F, 0x80, 0x01, 0x10]
        val payloadHex = payload.joinToString(" ") { String.format("%02X", it) }
        assertTrue("Must specify continuous receipt paper (0x10)", payloadHex.contains("1F 80 01 10"))

        // Must contain start print job [0x1F, 0xC0, 0x01, 0x00]
        assertTrue("Must start print job", payloadHex.contains("1F C0 01 00"))

        // Must end with paper feed [0x1F, 0x11, 0x00, 0x00, 0x50] and align [0x1F, 0x11, 0x50]
        assertTrue("Must contain paper feed to tear bar", payloadHex.contains("1F 11 00 00 50"))
    }

    @Test
    fun `test test receipt generation and monochrome conversion`() {
        val bitmap = ReceiptBitmapGenerator.generateTestReceipt("P50S-496A-BLE")
        assertNotNull(bitmap)
        assertEquals(P50SProtocol.PRINTER_WIDTH_DOTS, bitmap.width)
        assertTrue(bitmap.height > 100)

        val mono = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
        assertEquals(bitmap.height * 48, mono.size)
    }

    @Test
    fun `test customer receipt generation with dynamic values`() {
        val repair = RepairEntity(
            id = 1L,
            jobNumber = "00125",
            customerName = "Kasun",
            customerPhone = "0771234567",
            brand = "Samsung",
            model = "Galaxy M02",
            fault = "Display broken",
            totalPrice = 3500.0,
            amountPaid = 2000.0,
            balance = 1500.0,
            status = "REPAIRING",
            paymentStatus = "PARTIAL",
            receivedDate = "28/09/2026",
            receivedTime = "12:05 PM"
        )
        val items = listOf(
            RepairItemEntity(repairId = 1L, repairType = "Display Replacement", price = 3500.0)
        )
        val settings = AppSettingsEntity(
            shopName = "UDM MOBILE REPAIR",
            currency = "Rs."
        )

        val bitmap = ReceiptBitmapGenerator.generateCustomerReceipt(repair, items, emptyList(), settings)
        assertNotNull(bitmap)
        assertEquals(P50SProtocol.PRINTER_WIDTH_DOTS, bitmap.width)
        assertTrue(bitmap.height > 100)
        assertEquals(0, bitmap.height % 8) // Height must be multiple of 8 for thermal printer alignment

        val mono = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
        assertEquals(bitmap.height * 48, mono.size)

        val payload = P50SProtocol.buildPrintPayload(mono, bitmap.height)
        assertTrue(payload.isNotEmpty())
    }

    @Test
    fun `test customer receipt generation with long customer, model, and fault text without crashing`() {
        val repair = RepairEntity(
            id = 2L,
            jobNumber = "00999",
            customerName = "Alexander Bartholomew Montgomery The Third From Long Valley",
            customerPhone = "+94 77 123 4567 / 071 987 6543",
            brand = "Samsung Electronics International",
            model = "Galaxy S24 Ultra 5G Snapdragon Edition 1TB Dual Physical SIM Titanium Black",
            fault = "Front AMOLED screen glass severely cracked, touch display unresponsive in top left quadrant, rear camera lens glass shattered, and battery draining abnormally within 30 minutes\nSecond line with remarks\r\nThird line notes",
            totalPrice = 45850.0,
            amountPaid = 20000.0,
            balance = 25850.0,
            status = "IN_PROGRESS",
            paymentStatus = "PARTIAL",
            receivedDate = "29/09/2026",
            receivedTime = "02:30 PM"
        )
        val items = listOf(
            RepairItemEntity(repairId = 2L, repairType = "Original Super AMOLED 120Hz Display Replacement", price = 32000.0),
            RepairItemEntity(repairId = 2L, repairType = "Rear Quad Camera Module Glass Replacement", price = 4850.0),
            RepairItemEntity(repairId = 2L, repairType = "High Capacity 5000mAh Lithium Ion Battery Replacement", price = 9000.0)
        )
        val settings = AppSettingsEntity(
            shopName = "UDM MOBILE REPAIR & SERVICE CENTER",
            currency = "Rs."
        )

        val bitmap = ReceiptBitmapGenerator.generateCustomerReceipt(repair, items, emptyList(), settings)
        assertNotNull(bitmap)
        assertEquals(384, bitmap.width)
        assertTrue(bitmap.height > 200)
        assertEquals(0, bitmap.height % 8)

        val mono = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
        assertEquals(bitmap.height * 48, mono.size)

        val payload = P50SProtocol.buildPrintPayload(mono, bitmap.height)
        assertTrue(payload.isNotEmpty())
        assertTrue("Payload should not be abnormally large", payload.size < 100000)
    }

    @Test
    fun `test customer receipt with empty items and blank fields handles safely`() {
        val repair = RepairEntity(
            id = 3L,
            jobNumber = "",
            customerName = "",
            customerPhone = "",
            brand = "",
            model = "",
            fault = "",
            totalPrice = 1500.0,
            amountPaid = 0.0,
            balance = 1500.0,
            status = "",
            paymentStatus = "UNPAID",
            receivedDate = "",
            receivedTime = ""
        )
        val settings = AppSettingsEntity(shopName = "", currency = "")

        val bitmap = ReceiptBitmapGenerator.generateCustomerReceipt(repair, emptyList(), emptyList(), settings)
        assertNotNull(bitmap)
        assertEquals(384, bitmap.width)
        assertTrue(bitmap.height > 100)
        assertEquals(0, bitmap.height % 8)

        val mono = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
        val payload = P50SProtocol.buildPrintPayload(mono, bitmap.height)
        assertTrue(payload.isNotEmpty())
    }

    @Test
    fun `test payment receipt generation with dynamic values`() {
        val repair = RepairEntity(
            id = 1L,
            jobNumber = "00125",
            customerName = "Kasun",
            customerPhone = "0771234567",
            brand = "Samsung",
            model = "Galaxy M02",
            fault = "Display broken",
            totalPrice = 3500.0,
            amountPaid = 3500.0,
            balance = 0.0,
            status = "REPAIRING",
            paymentStatus = "PAID",
            receivedDate = "28/09/2026",
            receivedTime = "12:05 PM"
        )
        val payment = PaymentEntity(
            repairId = 1L,
            paymentNumber = 1,
            amount = 2000.0,
            date = "28/09/2026",
            time = "12:05 PM"
        )
        val settings = AppSettingsEntity(shopName = "UDM MOBILE REPAIR", currency = "Rs.")

        val bitmap = ReceiptBitmapGenerator.generatePaymentReceipt(repair, payment, settings)
        assertNotNull(bitmap)
        assertEquals(P50SProtocol.PRINTER_WIDTH_DOTS, bitmap.width)
        assertTrue(bitmap.height > 100)
    }

    @Test
    fun `test short receipt generates compact dynamic height without wasting paper`() {
        val repair = RepairEntity(
            id = 4L,
            jobNumber = "00125",
            customerName = "Kasun",
            customerPhone = "0771234567",
            brand = "Samsung",
            model = "Galaxy M02",
            fault = "Display broken",
            totalPrice = 2500.0,
            amountPaid = 2500.0,
            balance = 0.0,
            status = "DELIVERED",
            paymentStatus = "PAID",
            receivedDate = "29/09/2026",
            receivedTime = "10:15"
        )
        val items = listOf(
            RepairItemEntity(repairId = 4L, repairType = "Display Replacement", price = 2500.0)
        )
        val settings = AppSettingsEntity(
            shopName = "UDM MOBILE REPAIR",
            currency = "Rs."
        )

        val bitmap = ReceiptBitmapGenerator.generateCustomerReceipt(repair, items, emptyList(), settings)
        assertNotNull(bitmap)
        assertEquals(384, bitmap.width)
        // Verify compact dynamic height for 57mm thermal continuous paper
        assertTrue("Short receipt should be compact (between 300 and 650 px)", bitmap.height in 300..650)
        assertEquals(0, bitmap.height % 8)

        val mono = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
        assertEquals(bitmap.height * 48, mono.size)

        val payload = P50SProtocol.buildPrintPayload(mono, bitmap.height)
        assertTrue(payload.isNotEmpty())
        assertTrue("Payload for short receipt should be compact", payload.size < 50000)
    }

    @Test
    fun `test customer receipt and payment receipt share identical 384 dot width and encoding pipeline`() {
        val repair = RepairEntity(
            id = 5L,
            jobNumber = "00125",
            customerName = "Kasun",
            customerPhone = "0771234567",
            brand = "Samsung",
            model = "Galaxy M02",
            fault = "Display broken",
            totalPrice = 2500.0,
            amountPaid = 2500.0,
            balance = 0.0,
            status = "DELIVERED",
            paymentStatus = "PAID",
            receivedDate = "29/09/2026",
            receivedTime = "10:15"
        )
        val payment = PaymentEntity(
            repairId = 5L,
            paymentNumber = 1,
            amount = 2500.0,
            date = "29/09/2026",
            time = "10:15"
        )
        val settings = AppSettingsEntity(shopName = "UDM MOBILE REPAIR", currency = "Rs.")

        val customerBitmap = ReceiptBitmapGenerator.generateCustomerReceipt(repair, emptyList(), listOf(payment), settings)
        val paymentBitmap = ReceiptBitmapGenerator.generatePaymentReceipt(repair, payment, settings)

        // Both bitmaps MUST be 384 dots width
        assertEquals(384, customerBitmap.width)
        assertEquals(384, paymentBitmap.width)

        val customerMono = ReceiptBitmapGenerator.convertTo1BitMonochrome(customerBitmap)
        val paymentMono = ReceiptBitmapGenerator.convertTo1BitMonochrome(paymentBitmap)

        assertEquals(customerBitmap.height * 48, customerMono.size)
        assertEquals(paymentBitmap.height * 48, paymentMono.size)

        val customerPayload = P50SProtocol.buildPrintPayload(customerMono, customerBitmap.height)
        val paymentPayload = P50SProtocol.buildPrintPayload(paymentMono, paymentBitmap.height)

        // Both payloads must start with CMD_SET_BT_TYPE and CMD_PAPER_TYPE_CONTINUOUS
        assertEquals(0x1F.toByte(), customerPayload[0])
        assertEquals(0x1F.toByte(), paymentPayload[0])
        assertEquals(0xB2.toByte(), customerPayload[1])
        assertEquals(0xB2.toByte(), paymentPayload[1])
    }

    @Test
    fun `test minimal customer receipt generation and verification`() {
        val settings = AppSettingsEntity(shopName = "UDM MOBILE REPAIR", currency = "Rs.")
        val bitmap = ReceiptBitmapGenerator.generateMinimalCustomerReceipt(settings)

        assertNotNull(bitmap)
        assertEquals(384, bitmap.width)
        assertTrue(bitmap.height > 100)
        assertEquals(0, bitmap.height % 8)

        val mono = ReceiptBitmapGenerator.convertTo1BitMonochrome(bitmap)
        val bytesPerRow = (bitmap.width + 7) / 8
        assertEquals(48, bytesPerRow)
        assertEquals(bitmap.height * 48, mono.size)

        val payload = P50SProtocol.buildPrintPayload(mono, bitmap.height)
        assertTrue(payload.isNotEmpty())
        assertTrue("Minimal receipt should produce compact payload", payload.size < 30000)
    }

    @Test
    fun `test progressive addition of fields to customer receipt preserves safety thresholds`() {
        val baseSettings = AppSettingsEntity(shopName = "UDM MOBILE REPAIR", currency = "Rs.")

        // Step 1: Base minimal
        var r = RepairEntity(
            jobNumber = "0001",
            customerName = "",
            customerPhone = "",
            brand = "",
            model = "",
            fault = "",
            totalPrice = 100.0,
            amountPaid = 100.0,
            balance = 0.0,
            status = "DELIVERED",
            paymentStatus = "PAID",
            receivedDate = "29/09/2026",
            receivedTime = "10:15"
        )
        var bmp = ReceiptBitmapGenerator.generateCustomerReceipt(r, emptyList(), emptyList(), baseSettings)
        assertEquals(384, bmp.width)
        assertEquals(0, bmp.height % 8)

        // Step 2: Add customer name
        r = r.copy(customerName = "Kasun Perera")
        bmp = ReceiptBitmapGenerator.generateCustomerReceipt(r, emptyList(), emptyList(), baseSettings)
        assertEquals(384, bmp.width)
        assertEquals(0, bmp.height % 8)

        // Step 3: Add phone
        r = r.copy(customerPhone = "0771234567")
        bmp = ReceiptBitmapGenerator.generateCustomerReceipt(r, emptyList(), emptyList(), baseSettings)
        assertEquals(384, bmp.width)
        assertEquals(0, bmp.height % 8)

        // Step 4: Add device (brand + model)
        r = r.copy(brand = "Samsung", model = "Galaxy M02")
        bmp = ReceiptBitmapGenerator.generateCustomerReceipt(r, emptyList(), emptyList(), baseSettings)
        assertEquals(384, bmp.width)
        assertEquals(0, bmp.height % 8)

        // Step 5: Add complaint
        r = r.copy(fault = "Display broken / touch not working")
        bmp = ReceiptBitmapGenerator.generateCustomerReceipt(r, emptyList(), emptyList(), baseSettings)
        assertEquals(384, bmp.width)
        assertEquals(0, bmp.height % 8)

        // Step 6: Add repair items and prices
        val items = listOf(
            RepairItemEntity(repairId = 1L, repairType = "Display Replacement", price = 2500.0)
        )
        r = r.copy(totalPrice = 2500.0, amountPaid = 2500.0, balance = 0.0)
        bmp = ReceiptBitmapGenerator.generateCustomerReceipt(r, items, emptyList(), baseSettings)
        assertEquals(384, bmp.width)
        assertEquals(0, bmp.height % 8)

        val mono = ReceiptBitmapGenerator.convertTo1BitMonochrome(bmp)
        assertEquals(bmp.height * 48, mono.size)
        val payload = P50SProtocol.buildPrintPayload(mono, bmp.height)
        assertTrue(payload.isNotEmpty())
        assertTrue("Final customer receipt payload must remain safe and under 60KB", payload.size < 60000)
    }

    @Test
    fun `test printer preferences persistence`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Default auto print is false
        PrinterPreferences.setAutoPrintEnabled(context, false)
        assertFalse(PrinterPreferences.isAutoPrintEnabled(context))

        // Enable auto print
        PrinterPreferences.setAutoPrintEnabled(context, true)
        assertTrue(PrinterPreferences.isAutoPrintEnabled(context))

        // Save printer
        PrinterPreferences.savePrinter(context, "P50S-496A-BLE", "AA:BB:CC:DD:EE:FF")
        assertEquals("P50S-496A-BLE", PrinterPreferences.getSavedPrinterName(context))
        assertEquals("AA:BB:CC:DD:EE:FF", PrinterPreferences.getSavedPrinterMac(context))

        // Clear printer
        PrinterPreferences.clearSavedPrinter(context)
        assertEquals(null, PrinterPreferences.getSavedPrinterName(context))
        assertEquals(null, PrinterPreferences.getSavedPrinterMac(context))
    }
}
