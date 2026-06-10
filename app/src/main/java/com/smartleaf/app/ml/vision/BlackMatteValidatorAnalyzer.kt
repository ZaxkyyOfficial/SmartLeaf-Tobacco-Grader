package com.smartleaf.app.ml.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.graphics.BitmapFactory
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * BlackMatteValidatorAnalyzer
 *
 * Analyzes live camera frames from CameraX [ImageAnalysis] to validate
 * that the tobacco leaf is placed on a valid black matte surface.
 *
 * The algorithm samples pixels in the outer border region of the frame,
 * converts each pixel to HSV color space, and checks whether the HSV
 * Value (brightness) component is below 0.2. If a sufficient percentage
 * (>= 60%) of border pixels are "black matte", the surface is considered valid.
 *
 * @param onValidationResult Callback invoked on every analyzed frame.
 *        Returns `true` if the surface is valid, `false` otherwise.
 */
class BlackMatteValidatorAnalyzer(
    private val onValidationResult: (isValid: Boolean) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        /**
         * HSV Value threshold below which a pixel is considered "black matte".
         * Standard black matte surfaces reflect almost no light, so a Value < 0.2
         * is a conservative threshold.
         */
        private const val BLACK_MATTE_VALUE_THRESHOLD = 0.2f

        /**
         * Minimum percentage of border pixels that must be black matte
         * for the surface to be considered valid.
         */
        private const val VALID_SURFACE_PERCENTAGE = 0.60f

        /**
         * The fraction of the frame width/height that constitutes the "border" region.
         * A border inset of 15% means we sample the outer 15% on each side.
         */
        private const val BORDER_INSET_FRACTION = 0.15f

        /**
         * Step size for pixel sampling to keep analysis performant on each frame.
         * We sample every Nth pixel in both X and Y directions.
         */
        private const val SAMPLE_STEP = 8
    }

    /**
     * Analyze a single camera frame for black matte validation.
     *
     * The [imageProxy] is always closed in a `finally` block to prevent
     * stalling the CameraX analysis pipeline.
     */
    override fun analyze(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxyToBitmap(imageProxy) ?: run {
                onValidationResult(false)
                return
            }

            val isValid = validateBlackMatteSurface(bitmap)
            bitmap.recycle()
            onValidationResult(isValid)
        } finally {
            imageProxy.close()
        }
    }

    /**
     * Converts a YUV_420_888 [ImageProxy] to an ARGB_8888 [Bitmap].
     *
     * Uses NV21 conversion via [YuvImage] which is the most reliable
     * approach available without OpenCV dependencies.
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        if (imageProxy.format != ImageFormat.YUV_420_888) return null

        val yBuffer: ByteBuffer = imageProxy.planes[0].buffer
        val uBuffer: ByteBuffer = imageProxy.planes[1].buffer
        val vBuffer: ByteBuffer = imageProxy.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        // NV21 format: Y plane followed by interleaved VU
        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(
            nv21,
            ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )

        val outputStream = ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            Rect(0, 0, imageProxy.width, imageProxy.height),
            80,
            outputStream
        )

        val jpegBytes = outputStream.toByteArray()
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    /**
     * Validates that the outer border region of the bitmap contains
     * a sufficiently dark (black matte) surface.
     *
     * Border region definition:
     * - Top strip:    y in [0, insetY)
     * - Bottom strip: y in [height - insetY, height)
     * - Left strip:   x in [0, insetX), y in [insetY, height - insetY)
     * - Right strip:  x in [width - insetX, width), y in [insetY, height - insetY)
     *
     * This avoids double-counting corner pixels and ensures we only
     * sample the border "frame" around the central leaf region.
     */
    private fun validateBlackMatteSurface(bitmap: Bitmap): Boolean {
        val width = bitmap.width
        val height = bitmap.height

        val insetX = (width * BORDER_INSET_FRACTION).toInt().coerceAtLeast(1)
        val insetY = (height * BORDER_INSET_FRACTION).toInt().coerceAtLeast(1)

        var totalSampled = 0
        var blackMatteCount = 0
        val hsv = FloatArray(3)

        // --- Top strip: full width, y in [0, insetY) ---
        var y = 0
        while (y < insetY) {
            var x = 0
            while (x < width) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                totalSampled++
                if (hsv[2] < BLACK_MATTE_VALUE_THRESHOLD) {
                    blackMatteCount++
                }
                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }

        // --- Bottom strip: full width, y in [height - insetY, height) ---
        y = height - insetY
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                totalSampled++
                if (hsv[2] < BLACK_MATTE_VALUE_THRESHOLD) {
                    blackMatteCount++
                }
                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }

        // --- Left strip: x in [0, insetX), y in [insetY, height - insetY) ---
        y = insetY
        while (y < height - insetY) {
            var x = 0
            while (x < insetX) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                totalSampled++
                if (hsv[2] < BLACK_MATTE_VALUE_THRESHOLD) {
                    blackMatteCount++
                }
                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }

        // --- Right strip: x in [width - insetX, width), y in [insetY, height - insetY) ---
        y = insetY
        while (y < height - insetY) {
            var x = width - insetX
            while (x < width) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                totalSampled++
                if (hsv[2] < BLACK_MATTE_VALUE_THRESHOLD) {
                    blackMatteCount++
                }
                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }

        if (totalSampled == 0) return false

        val blackPercentage = blackMatteCount.toFloat() / totalSampled.toFloat()
        return blackPercentage >= VALID_SURFACE_PERCENTAGE
    }
}
