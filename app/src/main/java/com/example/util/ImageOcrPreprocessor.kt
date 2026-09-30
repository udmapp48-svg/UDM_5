package com.example.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import androidx.camera.core.ImageProxy

object ImageOcrPreprocessor {

    /**
     * Converts CameraX ImageProxy to an upright Android Bitmap.
     */
    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val bitmap = imageProxy.toBitmap()
            val rotation = imageProxy.imageInfo.rotationDegrees
            if (rotation != 0) {
                val matrix = android.graphics.Matrix().apply {
                    postRotate(rotation.toFloat())
                }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Crops the center region of interest matching the on-screen scanner reticle.
     */
    fun cropToReticle(bitmap: Bitmap, widthRatio: Float = 0.75f, heightRatio: Float = 0.40f): Bitmap {
        val cropW = (bitmap.width * widthRatio).toInt().coerceIn(50, bitmap.width)
        val cropH = (bitmap.height * heightRatio).toInt().coerceIn(50, bitmap.height)
        val startX = ((bitmap.width - cropW) / 2).coerceIn(0, bitmap.width - cropW)
        val startY = ((bitmap.height - cropH) / 2).coerceIn(0, bitmap.height - cropH)

        return Bitmap.createBitmap(bitmap, startX, startY, cropW, cropH)
    }

    /**
     * Upscales bitmap by given factor so small printed numbers are resolved clearly by ML Kit.
     */
    fun upscale(source: Bitmap, factor: Float = 2.0f): Bitmap {
        if (factor <= 1.0f) return source
        val newW = (source.width * factor).toInt()
        val newH = (source.height * factor).toInt()
        return Bitmap.createScaledBitmap(source, newW, newH, true)
    }

    /**
     * Grayscale conversion with adjustable contrast enhancement and brightness compensation.
     */
    fun enhanceGrayscaleAndContrast(source: Bitmap, contrast: Float = 1.8f, brightness: Float = -15f): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val cm = ColorMatrix(floatArrayOf(
            0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, brightness,
            0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, brightness,
            0.299f * contrast, 0.587f * contrast, 0.114f * contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))

        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }

        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    /**
     * Legacy compatible enhance call.
     */
    fun enhanceForOcr(source: Bitmap): Bitmap {
        val upscaled = if (source.width < 700) upscale(source, 2.0f) else source
        return enhanceGrayscaleAndContrast(upscaled, 1.8f, -15f)
    }

    /**
     * 3x3 Sharpening filter to restore edge clarity for blurry or slightly out-of-focus camera frames.
     */
    fun sharpen(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val srcPixels = IntArray(width * height)
        val dstPixels = IntArray(width * height)
        source.getPixels(srcPixels, 0, width, 0, 0, width, height)

        // Center weight 5, orthogonal neighbors -1
        for (y in 1 until height - 1) {
            val yOffset = y * width
            for (x in 1 until width - 1) {
                val idx = yOffset + x

                val cCenter = srcPixels[idx]
                val cTop = srcPixels[idx - width]
                val cBottom = srcPixels[idx + width]
                val cLeft = srcPixels[idx - 1]
                val cRight = srcPixels[idx + 1]

                val r = (5 * ((cCenter shr 16) and 0xFF) -
                    ((cTop shr 16) and 0xFF) - ((cBottom shr 16) and 0xFF) -
                    ((cLeft shr 16) and 0xFF) - ((cRight shr 16) and 0xFF)).coerceIn(0, 255)

                val g = (5 * ((cCenter shr 8) and 0xFF) -
                    ((cTop shr 8) and 0xFF) - ((cBottom shr 8) and 0xFF) -
                    ((cLeft shr 8) and 0xFF) - ((cRight shr 8) and 0xFF)).coerceIn(0, 255)

                val b = (5 * (cCenter and 0xFF) -
                    (cTop and 0xFF) - (cBottom and 0xFF) -
                    (cLeft and 0xFF) - (cRight and 0xFF)).coerceIn(0, 255)

                dstPixels[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }

        // Copy boundary pixels
        for (x in 0 until width) {
            dstPixels[x] = srcPixels[x]
            dstPixels[(height - 1) * width + x] = srcPixels[(height - 1) * width + x]
        }
        for (y in 0 until height) {
            dstPixels[y * width] = srcPixels[y * width]
            dstPixels[y * width + (width - 1)] = srcPixels[y * width + (width - 1)]
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(dstPixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Noise reduction filter (3x3 mean blur) to smooth out camera sensor noise.
     */
    fun noiseReduction(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val srcPixels = IntArray(width * height)
        val dstPixels = IntArray(width * height)
        source.getPixels(srcPixels, 0, width, 0, 0, width, height)

        for (y in 1 until height - 1) {
            val yOffset = y * width
            for (x in 1 until width - 1) {
                var rSum = 0
                var gSum = 0
                var bSum = 0

                for (dy in -1..1) {
                    val row = (y + dy) * width
                    for (dx in -1..1) {
                        val p = srcPixels[row + (x + dx)]
                        rSum += (p shr 16) and 0xFF
                        gSum += (p shr 8) and 0xFF
                        bSum += p and 0xFF
                    }
                }

                dstPixels[yOffset + x] = (0xFF shl 24) or ((rSum / 9) shl 16) or ((gSum / 9) shl 8) or (bSum / 9)
            }
        }

        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        output.setPixels(dstPixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Adaptive threshold binarization:
     * Divides image into local grid tiles to handle uneven lighting, glare, and shadows on stickers.
     */
    fun adaptiveThreshold(source: Bitmap, tileSize: Int = 32, offset: Int = 7): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        // Convert to luminance array
        val lum = IntArray(width * height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            lum[i] = (r * 299 + g * 587 + b * 114) / 1000
        }

        val outPixels = IntArray(width * height)

        // Compute local block averages
        val blocksX = (width + tileSize - 1) / tileSize
        val blocksY = (height + tileSize - 1) / tileSize

        for (by in 0 until blocksY) {
            val startY = by * tileSize
            val endY = minOf(startY + tileSize, height)

            for (bx in 0 until blocksX) {
                val startX = bx * tileSize
                val endX = minOf(startX + tileSize, width)

                var sum = 0L
                var count = 0
                for (y in startY until endY) {
                    val yOffset = y * width
                    for (x in startX until endX) {
                        sum += lum[yOffset + x]
                        count++
                    }
                }

                val localMean = if (count > 0) (sum / count).toInt() else 128
                val localThreshold = (localMean - offset).coerceIn(30, 225)

                for (y in startY until endY) {
                    val yOffset = y * width
                    for (x in startX until endX) {
                        val idx = yOffset + x
                        outPixels[idx] = if (lum[idx] < localThreshold) Color.BLACK else Color.WHITE
                    }
                }
            }
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Fast global binarization to pure black and white.
     */
    fun binarize(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var sumLum = 0L
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            sumLum += (r * 299 + g * 587 + b * 114) / 1000
        }
        val avgLum = (sumLum / pixels.size).toInt()
        val threshold = (avgLum * 0.90f).toInt().coerceIn(40, 220)

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val l = (r * 299 + g * 587 + b * 114) / 1000
            pixels[i] = if (l < threshold) Color.BLACK else Color.WHITE
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Invert colors for inverted stickers (light text on dark background).
     */
    fun invert(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val p = pixels[i]
            val r = 255 - ((p shr 16) and 0xFF)
            val g = 255 - ((p shr 8) and 0xFF)
            val b = 255 - (p and 0xFF)
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }
}
