package com.smartleaf.app.ml.vision

import android.graphics.Bitmap
import android.util.Log
import com.smartleaf.app.SmartLeafApplication
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.CLAHE
import org.opencv.imgproc.Imgproc

/**
 * TobaccoImagePreprocessor
 *
 * A robust, OpenCV-based image preprocessing pipeline designed
 * specifically for tobacco leaf maturity classification. This class
 * transforms a raw camera [Bitmap] into a normalized, noise-reduced,
 * contrast-enhanced, center-cropped image ready for TFLite inference.
 *
 * ## Pipeline Stages (in order)
 *
 * 1. **Bitmap → Mat conversion** — Converts Android Bitmap (ARGB_8888)
 *    to an OpenCV Mat in RGBA format via [Utils.bitmapToMat].
 *
 * 2. **Grayscale conversion** — Converts the RGBA Mat to single-channel
 *    grayscale using [Imgproc.cvtColor] with [Imgproc.COLOR_BGR2GRAY].
 *    This removes color information that is irrelevant for texture and
 *    structure analysis, reducing the data dimensionality by 3×.
 *
 * 3. **Gaussian Blur (5×5 kernel)** — Applies a Gaussian smoothing filter
 *    with kernel size 5×5 and sigma=0 (auto-computed from kernel size).
 *    This suppresses high-frequency noise from the camera sensor while
 *    preserving macro-level leaf texture features (veins, spots, edges).
 *
 * 4. **CLAHE (Contrast Limited Adaptive Histogram Equalization)** —
 *    Enhances local contrast using [Imgproc.createCLAHE] with a clip limit
 *    of 2.0 and a tile grid of 8×8. Unlike global histogram equalization,
 *    CLAHE adapts contrast per-region, which is critical for tobacco leaves
 *    that may have uneven illumination across their surface.
 *
 * 5. **Center-crop to 224×224** — Extracts the central 224×224 pixel region
 *    using [Rect]. Center-cropping (vs. stretching) preserves the aspect
 *    ratio and spatial relationships of leaf features, which is important
 *    for the CNN model's spatial invariance assumptions.
 *
 * 6. **Mat → Bitmap conversion** — Converts the processed grayscale Mat
 *    back to an Android Bitmap for downstream TFLite consumption.
 *
 * ## Thread Safety
 *
 * This class is **not** thread-safe. Each call to [preprocess] creates
 * its own intermediate [Mat] objects and releases them in a `finally`
 * block, so concurrent calls are safe as long as they do not share
 * the same input Bitmap. For CameraX pipelines, each frame analysis
 * callback runs on a single analysis thread by default.
 *
 * ## Error Handling
 *
 * - If OpenCV is not initialized ([SmartLeafApplication.isOpenCvInitialized]
 *   is `false`), the pipeline returns `null` and logs an error.
 * - If the input bitmap is smaller than [TARGET_SIZE]×[TARGET_SIZE],
 *   it is first upscaled to [TARGET_SIZE]×[TARGET_SIZE] before processing
 *   to avoid a negative-size Rect crash.
 * - All intermediate Mat objects are explicitly released to prevent
 *   native memory leaks.
 */
class TobaccoImagePreprocessor {

    companion object {
        private const val TAG = "TobaccoPreprocessor"

        /** The final output dimension expected by the TFLite model. */
        const val TARGET_SIZE = 224

        /** CLAHE clip limit — controls contrast amplification ceiling. */
        private const val CLAHE_CLIP_LIMIT = 2.0

        /** CLAHE tile grid size — number of tiles in each dimension. */
        private const val CLAHE_TILE_GRID = 8

        /** Gaussian blur kernel dimension (must be odd). */
        private const val BLUR_KERNEL_SIZE = 5.0
    }

    /**
     * Executes the full preprocessing pipeline on the given [bitmap].
     *
     * @param bitmap Input image from camera capture (any size, ARGB_8888).
     * @return A 224×224 grayscale [Bitmap] ready for inference, or `null`
     *         if OpenCV is not initialized or an error occurs.
     */
    fun preprocess(bitmap: Bitmap): Bitmap? {
        if (!SmartLeafApplication.isOpenCvInitialized) {
            Log.e(TAG, "OpenCV not initialized — cannot preprocess image.")
            return null
        }

        // Intermediate Mat references for cleanup
        var rgbaMat: Mat? = null
        var bgrMat: Mat? = null
        var grayMat: Mat? = null
        var blurredMat: Mat? = null
        var claheMat: Mat? = null
        var croppedMat: Mat? = null
        var claheInstance: CLAHE? = null

        try {
            // ─────────────────────────────────────────────────────
            // Stage 1: Bitmap → Mat (RGBA)
            // ─────────────────────────────────────────────────────
            rgbaMat = Mat()
            Utils.bitmapToMat(bitmap, rgbaMat)

            // ─────────────────────────────────────────────────────
            // Stage 1b: RGBA → BGR (OpenCV's native channel order)
            // Android Bitmaps are ARGB_8888 which maps to RGBA in
            // OpenCV. We convert to BGR for correct cvtColor behavior.
            // ─────────────────────────────────────────────────────
            bgrMat = Mat()
            Imgproc.cvtColor(rgbaMat, bgrMat, Imgproc.COLOR_RGBA2BGR)

            // ─────────────────────────────────────────────────────
            // Stage 2: Grayscale Conversion
            // ─────────────────────────────────────────────────────
            grayMat = Mat()
            Imgproc.cvtColor(bgrMat, grayMat, Imgproc.COLOR_BGR2GRAY)
            Log.d(TAG, "Grayscale conversion complete: ${grayMat.cols()}x${grayMat.rows()}")

            // ─────────────────────────────────────────────────────
            // Stage 3: Gaussian Blur (5×5 kernel)
            // Sigma = 0 → auto-calculated from kernel size as
            // sigma = 0.3 * ((ksize-1)*0.5 - 1) + 0.8
            // ─────────────────────────────────────────────────────
            blurredMat = Mat()
            Imgproc.GaussianBlur(
                grayMat,
                blurredMat,
                Size(BLUR_KERNEL_SIZE, BLUR_KERNEL_SIZE),
                0.0 // sigmaX — auto from kernel size
            )
            Log.d(TAG, "Gaussian blur (5x5) applied.")

            // ─────────────────────────────────────────────────────
            // Stage 4: CLAHE — Contrast Limited Adaptive Histogram
            //          Equalization
            // clipLimit=2.0 prevents over-amplification of noise
            // tileGridSize=8x8 provides fine-grained local adaptation
            // ─────────────────────────────────────────────────────
            claheMat = Mat()
            claheInstance = Imgproc.createCLAHE(
                CLAHE_CLIP_LIMIT,
                Size(CLAHE_TILE_GRID.toDouble(), CLAHE_TILE_GRID.toDouble())
            )
            claheInstance.apply(blurredMat, claheMat)
            Log.d(TAG, "CLAHE applied (clip=$CLAHE_CLIP_LIMIT, grid=${CLAHE_TILE_GRID}x$CLAHE_TILE_GRID).")

            // ─────────────────────────────────────────────────────
            // Stage 5: Center-crop to 224×224
            // If the image is smaller than 224×224 in either
            // dimension, we resize it first to prevent a crash.
            // ─────────────────────────────────────────────────────
            val matToCrop = ensureMinimumSize(claheMat)
            croppedMat = centerCrop(matToCrop, TARGET_SIZE, TARGET_SIZE)
            // If ensureMinimumSize created a new mat, release it
            if (matToCrop !== claheMat) {
                matToCrop.release()
            }
            Log.d(TAG, "Center-cropped to ${croppedMat.cols()}x${croppedMat.rows()}.")

            // ─────────────────────────────────────────────────────
            // Stage 6: Mat → Bitmap
            // ─────────────────────────────────────────────────────
            val resultBitmap = matToBitmap(croppedMat)
            Log.i(TAG, "Preprocessing complete — output: ${resultBitmap?.width}x${resultBitmap?.height}")
            return resultBitmap

        } catch (e: Exception) {
            Log.e(TAG, "Preprocessing pipeline failed: ${e.message}", e)
            return null
        } finally {
            // Release all native Mat memory to prevent leaks
            rgbaMat?.release()
            bgrMat?.release()
            grayMat?.release()
            blurredMat?.release()
            claheMat?.release()
            croppedMat?.release()
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Helper: Center-crop a Mat to the specified dimensions
    // ═══════════════════════════════════════════════════════════

    /**
     * Extracts the center [width]×[height] region from [src].
     *
     * The returned Mat is a **new allocation** (via `.clone()`) so the
     * caller is responsible for releasing it. The submat reference is
     * released internally.
     *
     * @param src   Source Mat (must be >= width×height).
     * @param width Target crop width in pixels.
     * @param height Target crop height in pixels.
     * @return A new Mat containing the center-cropped region.
     */
    private fun centerCrop(src: Mat, width: Int, height: Int): Mat {
        val srcWidth = src.cols()
        val srcHeight = src.rows()

        // Calculate the top-left corner of the center crop rectangle
        val x = (srcWidth - width) / 2
        val y = (srcHeight - height) / 2

        // Define the ROI rectangle
        val roi = Rect(x, y, width, height)

        // Extract the sub-matrix and clone it so it owns its own data
        val subMat = src.submat(roi)
        val cropped = subMat.clone()
        subMat.release()

        return cropped
    }

    // ═══════════════════════════════════════════════════════════
    // Helper: Ensure Mat is at least TARGET_SIZE × TARGET_SIZE
    // ═══════════════════════════════════════════════════════════

    /**
     * If [src] is smaller than [TARGET_SIZE] in either dimension,
     * resizes it up to [TARGET_SIZE]×[TARGET_SIZE] using cubic
     * interpolation. Otherwise returns [src] unchanged.
     */
    private fun ensureMinimumSize(src: Mat): Mat {
        return if (src.cols() < TARGET_SIZE || src.rows() < TARGET_SIZE) {
            val resized = Mat()
            Imgproc.resize(
                src,
                resized,
                Size(TARGET_SIZE.toDouble(), TARGET_SIZE.toDouble()),
                0.0,
                0.0,
                Imgproc.INTER_CUBIC
            )
            Log.w(TAG, "Input too small (${src.cols()}x${src.rows()}) — upscaled to ${TARGET_SIZE}x$TARGET_SIZE.")
            resized
        } else {
            src
        }
    }

    // ═══════════════════════════════════════════════════════════
    // Conversion Helpers: Bitmap ↔ Mat
    // ═══════════════════════════════════════════════════════════

    /**
     * Converts an Android [Bitmap] (ARGB_8888) to an OpenCV [Mat] (RGBA).
     *
     * This is a convenience wrapper around [Utils.bitmapToMat] for
     * external callers who need a Mat for custom processing.
     *
     * @param bitmap Source bitmap (must be ARGB_8888 or compatible).
     * @return A new Mat in RGBA format. Caller must release.
     */
    fun bitmapToMat(bitmap: Bitmap): Mat {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        return mat
    }

    /**
     * Converts an OpenCV [Mat] back to an Android [Bitmap].
     *
     * Handles both single-channel (grayscale) and multi-channel Mats:
     * - **1-channel (CV_8UC1):** Converted to RGBA via `GRAY2RGBA` before
     *   creating the Bitmap, since Android requires ARGB_8888.
     * - **3-channel (CV_8UC3):** Treated as BGR and converted to RGBA.
     * - **4-channel (CV_8UC4):** Used directly as RGBA.
     *
     * @param mat Source Mat. Not modified.
     * @return A new Bitmap in ARGB_8888 config, or `null` on failure.
     */
    fun matToBitmap(mat: Mat): Bitmap? {
        return try {
            val outputMat: Mat
            val needsRelease: Boolean

            when (mat.channels()) {
                1 -> {
                    // Grayscale → RGBA for Bitmap compatibility
                    outputMat = Mat()
                    Imgproc.cvtColor(mat, outputMat, Imgproc.COLOR_GRAY2RGBA)
                    needsRelease = true
                }
                3 -> {
                    // BGR → RGBA
                    outputMat = Mat()
                    Imgproc.cvtColor(mat, outputMat, Imgproc.COLOR_BGR2RGBA)
                    needsRelease = true
                }
                4 -> {
                    // Already RGBA
                    outputMat = mat
                    needsRelease = false
                }
                else -> {
                    Log.e(TAG, "Unsupported channel count: ${mat.channels()}")
                    return null
                }
            }

            val bitmap = Bitmap.createBitmap(
                outputMat.cols(),
                outputMat.rows(),
                Bitmap.Config.ARGB_8888
            )
            Utils.matToBitmap(outputMat, bitmap)

            if (needsRelease) {
                outputMat.release()
            }

            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Mat → Bitmap conversion failed: ${e.message}", e)
            null
        }
    }
}
