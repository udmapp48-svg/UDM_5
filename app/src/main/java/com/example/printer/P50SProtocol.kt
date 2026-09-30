package com.example.printer

import java.io.ByteArrayOutputStream
import java.util.UUID

/**
 * Protocol implementation for Marklife P50S thermal receipt printer (BLE device P50S-496A-BLE).
 * Implements Protocol 0x1F with continuous paper support, 384-dot thermal bitmap rasterization,
 * Level 0 Zlib stored-block compression (RFC 1950, 1KB window), and credit-based flow control.
 */
object P50SProtocol {

    val SERVICE_UUID: UUID = UUID.fromString("0000ff00-0000-1000-8000-00805f9b34fb")
    val WRITE_CHAR_UUID: UUID = UUID.fromString("0000ff02-0000-1000-8000-00805f9b34fb")
    val FLOW_CONTROL_CHAR_UUID: UUID = UUID.fromString("0000ff03-0000-1000-8000-00805f9b34fb")
    val CCCD_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val TARGET_DEVICE_NAME_EXACT = "P50S-496A-BLE"
    const val PRINTER_WIDTH_DOTS = 384
    const val BYTES_PER_ROW = PRINTER_WIDTH_DOTS / 8 // 48 bytes
    const val BLE_CHUNK_SIZE = 90
    const val SLICE_MAX_HEIGHT = 200 // Slices avoid printer memory buffer overrun

    // Commands (Protocol 0x1F)
    val CMD_SET_BT_TYPE = byteArrayOf(0x1F.toByte(), 0xB2.toByte(), 0x00.toByte())
    val CMD_PAPER_TYPE_CONTINUOUS = byteArrayOf(0x1F.toByte(), 0x80.toByte(), 0x01.toByte(), 0x10.toByte()) // 0x10 = Continuous Receipt Paper
    val CMD_SET_DENSITY_4 = byteArrayOf(0x1F.toByte(), 0x70.toByte(), 0x01.toByte(), 0x04.toByte()) // Density 4
    val CMD_START_PRINT_JOB = byteArrayOf(0x1F.toByte(), 0xC0.toByte(), 0x01.toByte(), 0x00.toByte())
    val CMD_STOP_PRINT_JOB = byteArrayOf(0x1F.toByte(), 0xC0.toByte(), 0x01.toByte(), 0x01.toByte())
    val CMD_FEED_PAPER_80PX = byteArrayOf(0x1F.toByte(), 0x11.toByte(), 0x00.toByte(), 0x00.toByte(), 0x50.toByte()) // Feed 80px to tear bar
    val CMD_ALIGN_END = byteArrayOf(0x1F.toByte(), 0x11.toByte(), 0x50.toByte())

    /**
     * Constructs a full print payload for a 1-bit monochrome raster bitmap of width 384 dots.
     * Slices tall receipts into multiple continuous blocks to avoid buffer overflow on P50S.
     */
    fun buildPrintPayload(monochromeData: ByteArray, totalHeight: Int): ByteArray {
        val out = ByteArrayOutputStream()

        // 1. Configure for continuous receipt printing
        out.write(CMD_SET_BT_TYPE)
        out.write(CMD_PAPER_TYPE_CONTINUOUS)
        out.write(CMD_SET_DENSITY_4)
        out.write(CMD_START_PRINT_JOB)

        // 2. Transmit image in vertical slices
        var rowStart = 0
        while (rowStart < totalHeight) {
            val sliceHeight = minOf(SLICE_MAX_HEIGHT, totalHeight - rowStart)
            val sliceBytesCount = sliceHeight * BYTES_PER_ROW
            val sliceRaw = ByteArray(sliceBytesCount)
            System.arraycopy(monochromeData, rowStart * BYTES_PER_ROW, sliceRaw, 0, sliceBytesCount)

            val compressedSlice = compressZlib1KbLevel0(sliceRaw)
            val sliceCmd = buildImageCommand(sliceHeight, compressedSlice)
            out.write(sliceCmd)

            rowStart += sliceHeight
        }

        // 3. Stop print job and feed paper to tear bar
        out.write(CMD_STOP_PRINT_JOB)
        out.write(CMD_FEED_PAPER_80PX)
        out.write(CMD_ALIGN_END)

        return out.toByteArray()
    }

    /**
     * Builds the 10-byte header + compressed image data command for Protocol 0x1F:
     * Header: [0x1F, 0x10, widthBytesHigh, widthBytesLow, heightHigh, heightLow, len3, len2, len1, len0]
     */
    fun buildImageCommand(heightPixels: Int, compressedData: ByteArray): ByteArray {
        val widthBytes = BYTES_PER_ROW // 48
        val cmd = ByteArray(10 + compressedData.size)
        cmd[0] = 0x1F.toByte() // 31
        cmd[1] = 0x10.toByte() // 16 (Raster Image Sub-command)
        cmd[2] = ((widthBytes shr 8) and 0xFF).toByte()
        cmd[3] = (widthBytes and 0xFF).toByte()
        cmd[4] = ((heightPixels shr 8) and 0xFF).toByte()
        cmd[5] = (heightPixels and 0xFF).toByte()
        cmd[6] = ((compressedData.size shr 24) and 0xFF).toByte()
        cmd[7] = ((compressedData.size shr 16) and 0xFF).toByte()
        cmd[8] = ((compressedData.size shr 8) and 0xFF).toByte()
        cmd[9] = (compressedData.size and 0xFF).toByte()

        System.arraycopy(compressedData, 0, cmd, 10, compressedData.size)
        return cmd
    }

    /**
     * Compresses byte array using RFC 1950 Zlib with 1KB window (CMF=0x28, FLG=0x15)
     * and Stored (Level 0) Deflate blocks.
     * This ensures 100% compatibility with the Marklife P50S microcontroller decompression routine
     * without CPU overhead or window-distance buffer overflows.
     */
    fun compressZlib1KbLevel0(raw: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()

        // RFC 1950 header for 1KB window:
        // CMF = 0x28 (window size 1024), FLG = 0x15 (check bit ensures (0x2800 + 0x15) % 31 == 0)
        out.write(0x28)
        out.write(0x15)

        // Calculate Adler-32
        var s1 = 1L
        var s2 = 0L
        for (b in raw) {
            val unsignedVal = b.toInt() and 0xFF
            s1 = (s1 + unsignedVal) % 65521L
            s2 = (s2 + s1) % 65521L
        }
        val adler32 = ((s2 shl 16) or s1) and 0xFFFFFFFFL

        // Split into stored Deflate blocks (max 32768 bytes per block)
        var offset = 0
        while (offset < raw.size) {
            val chunkLen = minOf(raw.size - offset, 32768)
            val isFinal = (offset + chunkLen >= raw.size)

            val bfinalAndType = if (isFinal) 0x01 else 0x00 // BFINAL=1/0, BTYPE=00 (Stored)
            out.write(bfinalAndType)

            // LEN in little endian (2 bytes)
            out.write(chunkLen and 0xFF)
            out.write((chunkLen shr 8) and 0xFF)

            // NLEN (one's complement of LEN) in little endian (2 bytes)
            val nlen = chunkLen.inv() and 0xFFFF
            out.write(nlen and 0xFF)
            out.write((nlen shr 8) and 0xFF)

            // Raw bytes
            out.write(raw, offset, chunkLen)
            offset += chunkLen
        }

        // Adler-32 checksum in big endian (4 bytes)
        out.write(((adler32 shr 24) and 0xFF).toInt())
        out.write(((adler32 shr 16) and 0xFF).toInt())
        out.write(((adler32 shr 8) and 0xFF).toInt())
        out.write((adler32 and 0xFF).toInt())

        return out.toByteArray()
    }

    /**
     * Parses flow control credit notification from characteristic 0xFF03.
     * Android SDK specification:
     * - if data.size >= 2 and data[0] == 0x01:
     *   if data[1] == 0x04 -> credits = 4
     *   else -> credits += data[1]
     */
    fun parseFlowControlNotification(data: ByteArray): Int? {
        if (data.size >= 2 && data[0] == 0x01.toByte()) {
            val creditVal = data[1].toInt() and 0xFF
            return if (creditVal == 0x04) 4 else creditVal
        }
        return null
    }

    /**
     * Determines whether a discovered BLE device is a likely Marklife / P50S printer.
     */
    fun isPotentialP50SPrinter(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val upper = name.uppercase()
        return upper == TARGET_DEVICE_NAME_EXACT ||
                upper.contains("P50S") ||
                upper.contains("P50") ||
                upper.contains("MARKLIFE") ||
                upper.contains("PRINTER") ||
                upper.startsWith("ML-") ||
                upper.startsWith("QP") ||
                upper.contains("496A")
    }
}
