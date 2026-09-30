package com.example.printer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.RepairEntity
import com.example.data.entity.RepairItemEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReceiptBitmapGenerator {

    const val BITMAP_WIDTH = 384 // 48 mm printable width at 203 DPI

    /**
     * Converts an ARGB_8888 Bitmap to a 1-bit monochrome byte array (48 bytes per row).
     * Pixel luminance threshold: <= 190 is converted to a black dot (bit 1, heater on),
     * > 190 is converted to white (bit 0, heater off).
     */
    fun convertTo1BitMonochrome(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val bytesPerRow = (width + 7) / 8 // 48 bytes for 384 px
        val out = ByteArray(bytesPerRow * height)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val threshold = 190

        for (y in 0 until height) {
            for (byteX in 0 until bytesPerRow) {
                var currentByte = 0
                for (bit in 0 until 8) {
                    val px = byteX * 8 + bit
                    if (px < width) {
                        val pixel = pixels[y * width + px]
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        val alpha = (pixel shr 24) and 0xFF

                        // Standard ITU-R BT.601 luminance
                        val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                        if (alpha > 50 && lum <= threshold) {
                            currentByte = currentByte or (128 shr bit)
                        }
                    }
                }
                out[y * bytesPerRow + byteX] = currentByte.toByte()
            }
        }
        return out
    }

    fun sanitizeText(input: String?): String {
        if (input.isNullOrBlank()) return ""
        val cleanWhitespace = input.replace("\r\n", " ")
            .replace("\r", " ")
            .replace("\n", " ")
            .replace("\t", " ")

        val sb = StringBuilder(cleanWhitespace.length)
        var i = 0
        while (i < cleanWhitespace.length) {
            val codePoint = cleanWhitespace.codePointAt(i)
            val charCount = Character.charCount(codePoint)

            when {
                // Control chars (0..31, 127..159)
                codePoint < 32 || (codePoint in 127..159) -> {
                    // Skip control chars
                }
                // Emojis / surrogate blocks (0x1F000..0x1FFFF, 0x2600..0x27BF, 0xFE00..0xFE0F)
                codePoint in 0x1F000..0x1FFFF || codePoint in 0x2600..0x27BF || codePoint in 0xFE00..0xFE0F -> {
                    sb.append(" ")
                }
                // Standard ASCII printable (32..126)
                codePoint in 32..126 -> {
                    sb.appendCodePoint(codePoint)
                }
                // Sinhala (0x0D80..0x0DFF)
                codePoint in 0x0D80..0x0DFF -> {
                    sb.appendCodePoint(codePoint)
                }
                // Tamil (0x0B80..0x0BFF)
                codePoint in 0x0B80..0x0BFF -> {
                    sb.appendCodePoint(codePoint)
                }
                // Latin-1 Supplement & Extended (0x00A0..0x024F)
                codePoint in 0x00A0..0x024F -> {
                    sb.appendCodePoint(codePoint)
                }
                // Standard Letter, Digit, Punctuation or Space
                Character.isLetterOrDigit(codePoint) || Character.isWhitespace(codePoint) -> {
                    sb.appendCodePoint(codePoint)
                }
                // Unassigned or Private Use or other surrogates -> fallback '?'
                else -> {
                    if (Character.isDefined(codePoint)) {
                        sb.appendCodePoint(codePoint)
                    } else {
                        sb.append("?")
                    }
                }
            }
            i += charCount
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    private fun formatCurrency(currency: String, amount: Double): String {
        val cur = currency.ifBlank { "Rs." }
        return if (amount % 1.0 == 0.0) {
            "$cur ${String.format(Locale.US, "%,d", amount.toLong())}"
        } else {
            "$cur ${String.format(Locale.US, "%,.2f", amount)}"
        }
    }

    private fun formatTimestamp(timestamp: Long, fallbackDate: String, fallbackTime: String): String {
        return try {
            if (timestamp > 0) {
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
                sdf.format(Date(timestamp))
            } else if (fallbackDate.isNotBlank()) {
                val parts = fallbackDate.trim().split("-")
                val formattedDate = if (parts.size == 3) "${parts[2]}/${parts[1]}/${parts[0]}" else fallbackDate.trim()
                val timeStr = fallbackTime.trim()
                if (timeStr.isNotBlank()) "$formattedDate $timeStr" else formattedDate
            } else {
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
                sdf.format(Date())
            }
        } catch (_: Exception) {
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US)
            sdf.format(Date())
        }
    }

    /**
     * Generates a Customer Handover Receipt Bitmap for Marklife P50S.
     * Compact 384-dot thermal receipt optimized for continuous 57mm paper.
     */
    fun generateCustomerReceipt(
        repair: RepairEntity,
        items: List<RepairItemEntity>,
        payments: List<PaymentEntity>,
        settings: AppSettingsEntity
    ): Bitmap {
        val lines = mutableListOf<ReceiptLine>()
        val cur = if (settings.currency.isNotBlank()) settings.currency.trim() else "Rs."
        val shopName = sanitizeText(settings.shopName).ifBlank { "UDM MOBILE REPAIR" }

        // 1. Header: UDM MOBILE REPAIR
        lines.add(ReceiptLine(text = shopName, style = LineStyle.HEADER_LARGE))
        lines.add(ReceiptLine(style = LineStyle.SPACER))

        // 2. Job No: 00125
        val rawJob = sanitizeText(repair.jobNumber)
        val formattedJobNo = if (rawJob.isNotBlank() && rawJob.length < 5 && rawJob.all { it.isDigit() }) {
            String.format(Locale.US, "%05d", rawJob.toIntOrNull() ?: 0)
        } else if (rawJob.isNotBlank()) {
            rawJob
        } else {
            "00001"
        }
        lines.add(ReceiptLine(text = "Job No: $formattedJobNo", style = LineStyle.BOLD_LEFT))

        // 3. Date: DD/MM/YYYY HH:MM
        val timestampStr = formatTimestamp(repair.receivedTimestamp, repair.receivedDate, repair.receivedTime)
        lines.add(ReceiptLine(text = "Date: $timestampStr", style = LineStyle.NORMAL_LEFT))
        lines.add(ReceiptLine(style = LineStyle.SPACER))

        // 4. Customer & Phone
        val customerName = sanitizeText(repair.customerName).ifBlank { "Valued Customer" }
        val customerPhone = sanitizeText(repair.customerPhone).ifBlank { "N/A" }
        lines.add(ReceiptLine(text = "Customer: $customerName", style = LineStyle.NORMAL_LEFT))
        lines.add(ReceiptLine(text = "Phone: $customerPhone", style = LineStyle.NORMAL_LEFT))
        lines.add(ReceiptLine(style = LineStyle.SPACER))

        // 5. Device & Model
        val brand = sanitizeText(repair.brand).ifBlank { "General Device" }
        val model = sanitizeText(repair.model).ifBlank { "Standard Model" }
        lines.add(ReceiptLine(text = "Device: $brand", style = LineStyle.NORMAL_LEFT))
        lines.add(ReceiptLine(text = "Model: $model", style = LineStyle.NORMAL_LEFT))
        lines.add(ReceiptLine(style = LineStyle.SPACER))

        // 6. Complaint:
        lines.add(ReceiptLine(text = "Complaint:", style = LineStyle.BOLD_LEFT))
        val rawFault = sanitizeText(repair.fault)
        val complaintDesc = rawFault.ifBlank { "None Specified" }
        lines.add(ReceiptLine(text = complaintDesc, style = LineStyle.NORMAL_LEFT))
        lines.add(ReceiptLine(style = LineStyle.SPACER))

        // 7. Repair:
        lines.add(ReceiptLine(text = "Repair:", style = LineStyle.BOLD_LEFT))
        if (items.isNotEmpty()) {
            for (item in items) {
                val itemTitle = sanitizeText(item.repairType).ifBlank { "Repair Service" }
                lines.add(ReceiptLine(text = itemTitle, style = LineStyle.NORMAL_LEFT))
            }
        } else {
            val singleItemTitle = if (rawFault.isNotBlank() && rawFault != "None Specified") {
                rawFault
            } else {
                "Display Replacement"
            }
            lines.add(ReceiptLine(text = singleItemTitle, style = LineStyle.NORMAL_LEFT))
        }
        lines.add(ReceiptLine(style = LineStyle.SPACER))

        // 8. Financials: Divider, Total, Paid, Balance
        lines.add(ReceiptLine(style = LineStyle.DIVIDER))
        lines.add(ReceiptLine(text = "Total:", rightText = formatCurrency(cur, repair.totalPrice), style = LineStyle.TWO_COLUMNS_NORMAL))
        lines.add(ReceiptLine(text = "Paid:", rightText = formatCurrency(cur, repair.amountPaid), style = LineStyle.TWO_COLUMNS_NORMAL))
        lines.add(ReceiptLine(text = "Balance:", rightText = formatCurrency(cur, repair.balance), style = LineStyle.TWO_COLUMNS_BOLD))
        lines.add(ReceiptLine(style = LineStyle.SPACER))

        // 9. Status: DELIVERED
        val safeStatus = sanitizeText(repair.status).ifBlank { "RECEIVED" }
        lines.add(ReceiptLine(text = "Status: $safeStatus", style = LineStyle.BOLD_LEFT))
        lines.add(ReceiptLine(style = LineStyle.SPACER))

        // 10. Thank you message
        lines.add(ReceiptLine(text = "Thank you for choosing", style = LineStyle.NORMAL_CENTER))
        lines.add(ReceiptLine(text = shopName, style = LineStyle.BOLD_CENTER))

        return renderLinesToBitmap(lines)
    }

    /**
     * Generates a Payment Receipt Bitmap for a specific payment transaction.
     */
    fun generatePaymentReceipt(
        repair: RepairEntity,
        payment: PaymentEntity,
        settings: AppSettingsEntity
    ): Bitmap {
        val lines = mutableListOf<ReceiptLine>()
        val cur = if (settings.currency.isNotBlank()) settings.currency else "Rs."
        val shopName = settings.shopName.ifBlank { "UDM MOBILE REPAIR" }

        // Header
        lines.add(ReceiptLine(text = shopName, style = LineStyle.HEADER_LARGE))
        lines.add(ReceiptLine(style = LineStyle.DOUBLE_DIVIDER))
        lines.add(ReceiptLine(style = LineStyle.SPACER))

        // JOB NO: 00125
        val formattedJobNo = if (repair.jobNumber.length < 5 && repair.jobNumber.all { it.isDigit() }) {
            String.format(Locale.US, "%05d", repair.jobNumber.toIntOrNull() ?: 0)
        } else {
            repair.jobNumber
        }
        lines.add(ReceiptLine(text = "JOB NO: $formattedJobNo", style = LineStyle.JOB_NO))
        lines.add(ReceiptLine(style = LineStyle.SPACER))

        // PAYMENT RECEIVED
        lines.add(ReceiptLine(text = "PAYMENT RECEIVED", style = LineStyle.HEADER_MEDIUM))
        lines.add(ReceiptLine(style = LineStyle.SPACER))

        // Payment amount
        lines.add(ReceiptLine(text = "Payment:", style = LineStyle.BOLD_LEFT))
        lines.add(ReceiptLine(text = formatCurrency(cur, payment.amount), style = LineStyle.JOB_NO))
        lines.add(ReceiptLine(style = LineStyle.SPACER))

        // Financials (Divider, Total, Paid, Balance)
        lines.add(ReceiptLine(style = LineStyle.DIVIDER))
        lines.add(ReceiptLine(text = "Total:", rightText = formatCurrency(cur, repair.totalPrice), style = LineStyle.TWO_COLUMNS_NORMAL))
        lines.add(ReceiptLine(text = "Paid:", rightText = formatCurrency(cur, repair.amountPaid), style = LineStyle.TWO_COLUMNS_NORMAL))
        lines.add(ReceiptLine(text = "Balance:", rightText = formatCurrency(cur, repair.balance), style = LineStyle.TWO_COLUMNS_BOLD))
        lines.add(ReceiptLine(style = LineStyle.SPACER))

        // Payment Status
        lines.add(ReceiptLine(text = "Payment Status: ${repair.paymentStatus}", style = LineStyle.BOLD_LEFT))
        lines.add(ReceiptLine(style = LineStyle.SPACER))

        // Date/Time
        lines.add(ReceiptLine(text = "Date:", style = LineStyle.BOLD_LEFT))
        lines.add(ReceiptLine(text = formatTimestamp(payment.timestamp, payment.date, payment.time), style = LineStyle.NORMAL_LEFT))
        lines.add(ReceiptLine(style = LineStyle.SPACER))

        // Footer
        lines.add(ReceiptLine(style = LineStyle.DOUBLE_DIVIDER))
        lines.add(ReceiptLine(text = "Thank you for choosing", style = LineStyle.NORMAL_CENTER))
        lines.add(ReceiptLine(text = shopName, style = LineStyle.BOLD_CENTER))

        return renderLinesToBitmap(lines)
    }

    /**
     * Generates a Test Print Receipt Bitmap for the Marklife P50S.
     * Exact format:
     * UDM MOBILE REPAIR
     * ====================
     * P50S TEST PRINT
     * --------------------
     * Printer: Marklife P50S
     * Device: P50S-496A-BLE
     * --------------------
     * PRINT TEST OK
     * ====================
     */
    fun generateTestReceipt(deviceName: String = "P50S-496A-BLE"): Bitmap {
        val lines = listOf(
            ReceiptLine(text = "UDM MOBILE REPAIR", style = LineStyle.HEADER_LARGE),
            ReceiptLine(style = LineStyle.DOUBLE_DIVIDER),
            ReceiptLine(text = "P50S TEST PRINT", style = LineStyle.HEADER_MEDIUM),
            ReceiptLine(style = LineStyle.DIVIDER),
            ReceiptLine(text = "Printer: Marklife P50S", style = LineStyle.NORMAL_LEFT),
            ReceiptLine(text = "Device: $deviceName", style = LineStyle.NORMAL_LEFT),
            ReceiptLine(style = LineStyle.DIVIDER),
            ReceiptLine(text = "PRINT TEST OK", style = LineStyle.JOB_NO),
            ReceiptLine(style = LineStyle.DOUBLE_DIVIDER)
        )
        return renderLinesToBitmap(lines)
    }

    /**
     * Minimal customer test receipt for step-by-step diagnostic verification:
     * UDM MOBILE REPAIR
     * JOB: 0001
     * TEST
     * TOTAL: Rs. 100
     * PAID: Rs. 100
     * BALANCE: Rs. 0
     */
    fun generateMinimalCustomerReceipt(settings: AppSettingsEntity = AppSettingsEntity()): Bitmap {
        val cur = if (settings.currency.isNotBlank()) settings.currency.trim() else "Rs."
        val shopName = sanitizeText(settings.shopName).ifBlank { "UDM MOBILE REPAIR" }
        val lines = listOf(
            ReceiptLine(text = shopName, style = LineStyle.HEADER_LARGE),
            ReceiptLine(style = LineStyle.DOUBLE_DIVIDER),
            ReceiptLine(style = LineStyle.SPACER),
            ReceiptLine(text = "JOB: 0001", style = LineStyle.JOB_NO),
            ReceiptLine(style = LineStyle.SPACER),
            ReceiptLine(text = "TEST", style = LineStyle.HEADER_MEDIUM),
            ReceiptLine(style = LineStyle.DIVIDER),
            ReceiptLine(text = "TOTAL:", rightText = "$cur 100", style = LineStyle.TWO_COLUMNS_NORMAL),
            ReceiptLine(text = "PAID:", rightText = "$cur 100", style = LineStyle.TWO_COLUMNS_NORMAL),
            ReceiptLine(text = "BALANCE:", rightText = "$cur 0", style = LineStyle.TWO_COLUMNS_BOLD),
            ReceiptLine(style = LineStyle.SPACER),
            ReceiptLine(style = LineStyle.DOUBLE_DIVIDER)
        )
        return renderLinesToBitmap(lines)
    }

    private enum class LineStyle {
        HEADER_LARGE,
        HEADER_MEDIUM,
        JOB_NO,
        BOLD_LEFT,
        NORMAL_LEFT,
        TWO_COLUMNS_NORMAL,
        TWO_COLUMNS_BOLD,
        NORMAL_CENTER,
        BOLD_CENTER,
        DOUBLE_DIVIDER,
        DIVIDER,
        SPACER
    }

    private data class ReceiptLine(
        val text: String = "",
        val rightText: String = "",
        val style: LineStyle
    )

    private fun renderLinesToBitmap(rawLines: List<ReceiptLine>): Bitmap {
        val leftMargin = 16f
        val rightMargin = BITMAP_WIDTH - 16f

        // Expand any long single-column lines with automatic word-wrapping
        val formattedLines = mutableListOf<ReceiptLine>()
        for (item in rawLines) {
            when (item.style) {
                LineStyle.DOUBLE_DIVIDER, LineStyle.DIVIDER, LineStyle.SPACER -> {
                    formattedLines.add(item)
                }
                LineStyle.TWO_COLUMNS_NORMAL, LineStyle.TWO_COLUMNS_BOLD -> {
                    // For two columns: dynamically calculate left column character budget based on right text
                    val rightLen = item.rightText.length
                    val maxLeftChars = if (rightLen > 0) (30 - rightLen).coerceIn(12, 20) else 26
                    val wrappedLeft = wrapText(item.text, maxLeftChars)
                    for ((index, w) in wrappedLeft.withIndex()) {
                        if (index == 0) {
                            formattedLines.add(ReceiptLine(text = w, rightText = item.rightText, style = item.style))
                        } else {
                            formattedLines.add(ReceiptLine(text = w, rightText = "", style = LineStyle.NORMAL_LEFT))
                        }
                    }
                }
                else -> {
                    val maxCharsPerLine = when (item.style) {
                        LineStyle.HEADER_LARGE -> 20
                        LineStyle.HEADER_MEDIUM, LineStyle.JOB_NO -> 22
                        else -> 28
                    }
                    val wrapped = wrapText(item.text, maxCharsPerLine)
                    for (w in wrapped) {
                        formattedLines.add(ReceiptLine(text = w, style = item.style))
                    }
                }
            }
        }

        // Compute total bitmap height dynamically with clean margins
        var totalHeight = 16 // Top margin
        for (item in formattedLines) {
            totalHeight += when (item.style) {
                LineStyle.HEADER_LARGE -> 32
                LineStyle.HEADER_MEDIUM -> 26
                LineStyle.JOB_NO -> 34
                LineStyle.BOLD_LEFT, LineStyle.BOLD_CENTER -> 24
                LineStyle.NORMAL_LEFT, LineStyle.NORMAL_CENTER -> 22
                LineStyle.TWO_COLUMNS_NORMAL, LineStyle.TWO_COLUMNS_BOLD -> 24
                LineStyle.DOUBLE_DIVIDER -> 20
                LineStyle.DIVIDER -> 16
                LineStyle.SPACER -> 8
            }
        }
        totalHeight += 24 // Bottom tear margin

        // Thermal printer hardware alignment: Ensure height is a multiple of 8
        // This guarantees all 200px vertical slices and the final slice have heights divisible by 8,
        // preventing microcontroller DMA unaligned read faults and odd-stride bitmap corruptions.
        val remainder = totalHeight % 8
        if (remainder != 0) {
            totalHeight += (8 - remainder)
        }

        val bitmap = Bitmap.createBitmap(BITMAP_WIDTH, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
        }

        var y = 16f

        for (item in formattedLines) {
            when (item.style) {
                LineStyle.HEADER_LARGE -> {
                    paint.textSize = 24f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textAlign = Paint.Align.CENTER
                    y += 24f
                    canvas.drawText(item.text, BITMAP_WIDTH / 2f, y, paint)
                    y += 8f
                }
                LineStyle.HEADER_MEDIUM -> {
                    paint.textSize = 20f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textAlign = Paint.Align.CENTER
                    y += 20f
                    canvas.drawText(item.text, BITMAP_WIDTH / 2f, y, paint)
                    y += 6f
                }
                LineStyle.JOB_NO -> {
                    paint.textSize = 28f
                    paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    paint.textAlign = Paint.Align.CENTER
                    y += 26f
                    canvas.drawText(item.text, BITMAP_WIDTH / 2f, y, paint)
                    y += 8f
                }
                LineStyle.BOLD_LEFT -> {
                    paint.textSize = 19f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textAlign = Paint.Align.LEFT
                    y += 19f
                    canvas.drawText(item.text, leftMargin, y, paint)
                    y += 5f
                }
                LineStyle.BOLD_CENTER -> {
                    paint.textSize = 19f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    paint.textAlign = Paint.Align.CENTER
                    y += 19f
                    canvas.drawText(item.text, BITMAP_WIDTH / 2f, y, paint)
                    y += 5f
                }
                LineStyle.NORMAL_LEFT -> {
                    paint.textSize = 18f
                    paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                    paint.textAlign = Paint.Align.LEFT
                    y += 18f
                    canvas.drawText(item.text, leftMargin, y, paint)
                    y += 4f
                }
                LineStyle.NORMAL_CENTER -> {
                    paint.textSize = 18f
                    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    paint.textAlign = Paint.Align.CENTER
                    y += 18f
                    canvas.drawText(item.text, BITMAP_WIDTH / 2f, y, paint)
                    y += 4f
                }
                LineStyle.TWO_COLUMNS_NORMAL -> {
                    paint.textSize = 18f
                    paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                    y += 19f
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(item.text, leftMargin, y, paint)
                    if (item.rightText.isNotEmpty()) {
                        paint.textAlign = Paint.Align.RIGHT
                        canvas.drawText(item.rightText, rightMargin, y, paint)
                    }
                    y += 5f
                }
                LineStyle.TWO_COLUMNS_BOLD -> {
                    paint.textSize = 19f
                    paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    y += 19f
                    paint.textAlign = Paint.Align.LEFT
                    canvas.drawText(item.text, leftMargin, y, paint)
                    if (item.rightText.isNotEmpty()) {
                        paint.textAlign = Paint.Align.RIGHT
                        canvas.drawText(item.rightText, rightMargin, y, paint)
                    }
                    y += 5f
                }
                LineStyle.DOUBLE_DIVIDER -> {
                    paint.textSize = 18f
                    paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    paint.textAlign = Paint.Align.CENTER
                    y += 14f
                    canvas.drawText("====================", BITMAP_WIDTH / 2f, y, paint)
                    y += 6f
                }
                LineStyle.DIVIDER -> {
                    paint.textSize = 16f
                    paint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
                    paint.textAlign = Paint.Align.CENTER
                    y += 12f
                    canvas.drawText("----------------------------", BITMAP_WIDTH / 2f, y, paint)
                    y += 4f
                }
                LineStyle.SPACER -> {
                    y += 8f
                }
            }
        }

        return bitmap
    }

    fun wrapText(rawText: String, maxChars: Int): List<String> {
        val safeMax = maxChars.coerceAtLeast(10)
        val clean = rawText.replace("\r\n", "\n").replace("\r", "\n").replace("\t", " ")
        val rawLines = clean.split("\n")
        val result = mutableListOf<String>()

        for (line in rawLines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.length <= safeMax) {
                result.add(trimmed)
                continue
            }

            val words = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
            var current = StringBuilder()

            for (word in words) {
                if (word.length > safeMax) {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current = StringBuilder()
                    }
                    var start = 0
                    while (start < word.length) {
                        val end = minOf(start + safeMax, word.length)
                        val sub = word.substring(start, end)
                        if (end == word.length) {
                            current = StringBuilder(sub)
                        } else {
                            result.add(sub)
                        }
                        start = end
                    }
                } else if (current.isEmpty()) {
                    current.append(word)
                } else if (current.length + 1 + word.length <= safeMax) {
                    current.append(" ").append(word)
                } else {
                    result.add(current.toString())
                    current = StringBuilder(word)
                }
            }
            if (current.isNotEmpty()) {
                result.add(current.toString())
            }
        }

        return if (result.isEmpty()) listOf(rawText.take(safeMax)) else result
    }
}
