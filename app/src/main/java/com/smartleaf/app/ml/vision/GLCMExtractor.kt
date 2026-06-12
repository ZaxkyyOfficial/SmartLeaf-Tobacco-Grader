package com.smartleaf.app.ml.vision

/**
 * GLCMExtractor — Native Kotlin Gray Level Co-Occurrence Matrix Feature Extractor
 * ================================================================================
 *
 * Sprint Reference : June 7, 2026 — GLCM Feature Extraction Module
 * Package          : com.smartleaf.app.ml.vision
 *
 * A zero-dependency (no OpenCV needed), memory-efficient, pure-Kotlin implementation
 * of the Gray Level Co-Occurrence Matrix (GLCM) for texture feature extraction from
 * grayscale images. Designed for on-device use in the SmartLeaf Android app.
 *
 * ## What is GLCM?
 *
 * The GLCM is a statistical method that examines the spatial relationship of pixel
 * intensities. For a given offset (Δx, Δy), the matrix P[i, j] counts how many times
 * a pixel with intensity `i` is adjacent to a pixel with intensity `j` at that offset.
 * After normalization, P becomes a probability distribution from which we extract
 * second-order texture features.
 *
 * ## Supported Offsets (Angles)
 *
 * | Offset  | Angle | Description       |
 * |---------|-------|-------------------|
 * | (1, 0)  |   0°  | Horizontal        |
 * | (1, 1)  |  45°  | Diagonal ↗        |
 * | (0, 1)  |  90°  | Vertical          |
 * | (1, -1) | 135°  | Anti-diagonal ↙   |
 *
 * ## Extracted Features (per offset)
 *
 * 1. **Contrast** — Measures local intensity variation. High contrast = sharp edges.
 *    `Σ_ij (i - j)² · P[i, j]`
 *
 * 2. **Correlation** — Measures linear dependency of gray levels between neighbors.
 *    `Σ_ij ((i - μ_i)(j - μ_j) · P[i, j]) / (σ_i · σ_j)`
 *
 * 3. **Energy** — Also known as Angular Second Moment (ASM) squared. Measures uniformity.
 *    `sqrt(Σ_ij P[i, j]²)`
 *    Note: `Energy = sqrt(ASM)` following scikit-image convention.
 *
 * 4. **Homogeneity** — Measures closeness of distribution to the diagonal.
 *    `Σ_ij P[i, j] / (1 + |i - j|)`
 *
 * 5. **ASM (Angular Second Moment)** — Measures textural uniformity (sum of squared elements).
 *    `Σ_ij P[i, j]²`
 *
 * ## scikit-image Compatibility
 *
 * This implementation follows the **exact** mathematical definitions used by
 * `skimage.feature.graycomatrix` and `skimage.feature.graycoprops` to enable
 * cross-validation of outputs between the Kotlin and Python implementations.
 * Key compatibility details:
 * - The GLCM is made **symmetric** (P + P^T) before normalization, matching
 *   `skimage.feature.graycomatrix(symmetric=True)`.
 * - Normalization divides each element by the matrix sum so that Σ P[i,j] = 1.
 * - Correlation uses the marginal means μ_i, μ_j and standard deviations σ_i, σ_j
 *   of the normalized GLCM rows/columns, matching `skimage.feature.graycoprops`.
 *
 * ## Memory Efficiency
 *
 * - Uses a single flat [DoubleArray] of size `levels × levels` for each GLCM
 *   to avoid 2D array allocation overhead and improve cache locality.
 * - Intermediate matrices are computed per-offset and not retained.
 * - Input pixel data is read from an [IntArray] representation of the grayscale
 *   image (0–255 per pixel) to avoid holding Bitmap or Mat references.
 *
 * ## Thread Safety
 *
 * All public methods are pure functions with no shared mutable state.
 * This class is inherently **thread-safe**.
 *
 * @author SmartLeaf PKM-KC 2026 Team
 */
class GLCMExtractor {

    companion object {
        /** Default number of gray levels (quantization levels) for the GLCM. */
        const val DEFAULT_LEVELS = 256

        /** The four canonical GLCM offsets: 0°, 45°, 90°, 135°. */
        val DEFAULT_OFFSETS: List<Offset> = listOf(
            Offset(dx = 1, dy = 0),   //   0° — Horizontal
            Offset(dx = 1, dy = 1),   //  45° — Diagonal ↗
            Offset(dx = 0, dy = 1),   //  90° — Vertical
            Offset(dx = 1, dy = -1),  // 135° — Anti-diagonal ↙
        )
    }

    /**
     * Represents a spatial offset (Δx, Δy) for GLCM computation.
     *
     * @property dx Column offset (positive = right).
     * @property dy Row offset (positive = down).
     */
    data class Offset(val dx: Int, val dy: Int) {
        /** Human-readable angle label for this offset. */
        val angleLabel: String
            get() = when {
                dx == 1 && dy == 0  -> "0°"
                dx == 1 && dy == 1  -> "45°"
                dx == 0 && dy == 1  -> "90°"
                dx == 1 && dy == -1 -> "135°"
                else -> "${dx},${dy}"
            }
    }

    /**
     * Data class holding the 5 GLCM texture features for a single offset.
     *
     * All values are [Double] for floating-point precision.
     */
    data class GLCMFeatures(
        val contrast: Double,
        val correlation: Double,
        val energy: Double,
        val homogeneity: Double,
        val asm: Double,
    ) {
        override fun toString(): String =
            "GLCMFeatures(contrast=%.6f, correlation=%.6f, energy=%.6f, homogeneity=%.6f, asm=%.6f)"
                .format(contrast, correlation, energy, homogeneity, asm)
    }

    /**
     * Complete GLCM result containing features for all computed offsets.
     *
     * @property featuresByOffset Map from [Offset] to its [GLCMFeatures].
     * @property averageFeatures The mean feature values across all offsets.
     */
    data class GLCMResult(
        val featuresByOffset: Map<Offset, GLCMFeatures>,
        val averageFeatures: GLCMFeatures,
    )

    // ═══════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Extracts GLCM texture features from a grayscale image.
     *
     * @param pixels   1D array of grayscale pixel values (row-major order).
     *                 Each value must be in [0, levels - 1]. For standard 8-bit
     *                 grayscale images, values are in [0, 255].
     * @param width    Image width in pixels.
     * @param height   Image height in pixels.
     * @param offsets  List of spatial offsets to compute. Defaults to the 4
     *                 canonical angles: 0°, 45°, 90°, 135°.
     * @param levels   Number of gray levels (quantization levels). Defaults to 256.
     * @return A [GLCMResult] containing per-offset and averaged features.
     * @throws IllegalArgumentException if [pixels].size != [width] × [height],
     *         or if any pixel value is outside [0, levels - 1].
     */
    fun extract(
        pixels: IntArray,
        width: Int,
        height: Int,
        offsets: List<Offset> = DEFAULT_OFFSETS,
        levels: Int = DEFAULT_LEVELS,
    ): GLCMResult {
        require(pixels.size == width * height) {
            "Pixel array size (${pixels.size}) does not match dimensions ($width × $height = ${width * height})"
        }

        val featuresByOffset = LinkedHashMap<Offset, GLCMFeatures>(offsets.size)

        for (offset in offsets) {
            // Step 1: Build raw co-occurrence matrix
            val glcm = buildGLCM(pixels, width, height, offset, levels)

            // Step 2: Make symmetric (P + P^T) to match skimage symmetric=True
            makeSymmetric(glcm, levels)

            // Step 3: Normalize so that Σ P[i,j] = 1.0
            normalize(glcm)

            // Step 4: Extract the 5 features from the normalized GLCM
            featuresByOffset[offset] = computeFeatures(glcm, levels)
        }

        // Compute average features across all offsets
        val avgFeatures = averageFeatures(featuresByOffset.values)

        return GLCMResult(
            featuresByOffset = featuresByOffset,
            averageFeatures = avgFeatures,
        )
    }

    /**
     * Convenience overload that accepts an Android Bitmap.
     *
     * The bitmap is converted to grayscale by extracting the luminance channel
     * using the standard NTSC/Rec.601 formula:
     * `Y = 0.299·R + 0.587·G + 0.114·B`
     *
     * @param bitmap   Source bitmap (any config — ARGB_8888 recommended).
     * @param offsets  List of spatial offsets. Defaults to 4 canonical angles.
     * @param levels   Number of gray levels. Defaults to 256.
     * @return A [GLCMResult] containing per-offset and averaged features.
     */
    fun extract(
        bitmap: android.graphics.Bitmap,
        offsets: List<Offset> = DEFAULT_OFFSETS,
        levels: Int = DEFAULT_LEVELS,
    ): GLCMResult {
        val width = bitmap.width
        val height = bitmap.height
        val argbPixels = IntArray(width * height)
        bitmap.getPixels(argbPixels, 0, width, 0, 0, width, height)

        // Convert ARGB to grayscale using Rec.601 luminance
        val grayPixels = IntArray(argbPixels.size) { idx ->
            val color = argbPixels[idx]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            // Standard NTSC luminance formula
            (0.299 * r + 0.587 * g + 0.114 * b + 0.5).toInt().coerceIn(0, 255)
        }

        return extract(grayPixels, width, height, offsets, levels)
    }

    // ═══════════════════════════════════════════════════════════════════
    // GLCM CONSTRUCTION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Builds the raw (non-symmetric, unnormalized) co-occurrence matrix for
     * a single offset.
     *
     * Uses a flat [DoubleArray] of size [levels]² for cache-friendly access.
     * Element at row `i`, column `j` is stored at index `i * levels + j`.
     *
     * For each pixel (x, y), if the neighbor pixel at (x + dx, y + dy) is
     * within bounds, the pair (pixel[y,x], pixel[y+dy, x+dx]) increments
     * the corresponding GLCM cell.
     *
     * @param pixels Row-major grayscale pixel array.
     * @param width  Image width.
     * @param height Image height.
     * @param offset Spatial offset (Δx, Δy).
     * @param levels Number of gray levels.
     * @return Flat GLCM array of size [levels]².
     */
    internal fun buildGLCM(
        pixels: IntArray,
        width: Int,
        height: Int,
        offset: Offset,
        levels: Int,
    ): DoubleArray {
        val size = levels * levels
        val glcm = DoubleArray(size)

        val dx = offset.dx
        val dy = offset.dy

        // Determine valid iteration bounds based on the offset direction.
        // This avoids per-pixel bounds checking inside the hot loop.
        val yStart = maxOf(0, -dy)
        val yEnd = minOf(height, height - dy)
        val xStart = maxOf(0, -dx)
        val xEnd = minOf(width, width - dx)

        for (y in yStart until yEnd) {
            val rowBase = y * width
            val neighborRowBase = (y + dy) * width

            for (x in xStart until xEnd) {
                val i = pixels[rowBase + x]
                val j = pixels[neighborRowBase + x + dx]
                glcm[i * levels + j] += 1.0
            }
        }

        return glcm
    }

    /**
     * Makes the GLCM symmetric in-place: P[i,j] = P[i,j] + P[j,i].
     *
     * This matches `skimage.feature.graycomatrix(symmetric=True)`, which
     * considers both (i→j) and (j→i) co-occurrences for each pixel pair.
     *
     * We iterate only over the upper triangle (i < j) and add the transposed
     * value, then double the diagonal. This is done in a single pass.
     *
     * @param glcm   Flat GLCM array (modified in-place).
     * @param levels Number of gray levels.
     */
    internal fun makeSymmetric(glcm: DoubleArray, levels: Int) {
        for (i in 0 until levels) {
            for (j in i + 1 until levels) {
                val ij = i * levels + j
                val ji = j * levels + i
                val sum = glcm[ij] + glcm[ji]
                glcm[ij] = sum
                glcm[ji] = sum
            }
        }
    }

    /**
     * Normalizes the GLCM in-place so that all elements sum to 1.0.
     *
     * After normalization, P[i,j] represents the probability of the
     * co-occurrence pair (i, j).
     *
     * @param glcm Flat GLCM array (modified in-place).
     */
    internal fun normalize(glcm: DoubleArray) {
        var total = 0.0
        for (v in glcm) total += v

        if (total > 0.0) {
            val invTotal = 1.0 / total
            for (idx in glcm.indices) {
                glcm[idx] *= invTotal
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // FEATURE COMPUTATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Computes the 5 GLCM texture features from a normalized, symmetric GLCM.
     *
     * Mathematical definitions (matching `skimage.feature.graycoprops`):
     *
     * - **Contrast:**       Σ_ij (i - j)² · P[i,j]
     * - **Correlation:**    Σ_ij ((i - μ_i)(j - μ_j) · P[i,j]) / (σ_i · σ_j)
     *                       where μ_i = Σ_i (i · Σ_j P[i,j]) and
     *                       σ_i² = Σ_i ((i - μ_i)² · Σ_j P[i,j])
     * - **Energy:**         sqrt(Σ_ij P[i,j]²)        (= sqrt(ASM))
     * - **Homogeneity:**    Σ_ij P[i,j] / (1 + |i - j|)
     * - **ASM:**            Σ_ij P[i,j]²
     *
     * For Correlation, if either σ_i or σ_j is zero (uniform row/column
     * marginals), the correlation is defined as 1.0 (perfectly correlated
     * with itself), consistent with the limit behavior and skimage's output
     * for constant images.
     *
     * @param glcm   Normalized, symmetric flat GLCM array.
     * @param levels Number of gray levels.
     * @return [GLCMFeatures] with all 5 features.
     */
    internal fun computeFeatures(glcm: DoubleArray, levels: Int): GLCMFeatures {
        // ── Pre-compute marginal distributions ──────────────────────────
        // p_i[i] = Σ_j P[i,j]  (row marginal)
        // p_j[j] = Σ_i P[i,j]  (column marginal)
        val pI = DoubleArray(levels)
        val pJ = DoubleArray(levels)

        for (i in 0 until levels) {
            val rowBase = i * levels
            for (j in 0 until levels) {
                val pij = glcm[rowBase + j]
                pI[i] += pij
                pJ[j] += pij
            }
        }

        // ── Marginal means ──────────────────────────────────────────────
        // μ_i = Σ_i (i · p_i[i])
        var muI = 0.0
        var muJ = 0.0
        for (k in 0 until levels) {
            muI += k.toDouble() * pI[k]
            muJ += k.toDouble() * pJ[k]
        }

        // ── Marginal variances ──────────────────────────────────────────
        // σ_i² = Σ_i ((i - μ_i)² · p_i[i])
        var varI = 0.0
        var varJ = 0.0
        for (k in 0 until levels) {
            val diffI = k.toDouble() - muI
            val diffJ = k.toDouble() - muJ
            varI += diffI * diffI * pI[k]
            varJ += diffJ * diffJ * pJ[k]
        }
        val sigmaI = Math.sqrt(varI)
        val sigmaJ = Math.sqrt(varJ)

        // ── Compute all 5 features in a single pass over the GLCM ──────
        var contrast = 0.0
        var correlationNumerator = 0.0
        var asmValue = 0.0
        var homogeneity = 0.0

        for (i in 0 until levels) {
            val rowBase = i * levels
            val iDouble = i.toDouble()
            val iMinusMuI = iDouble - muI

            for (j in 0 until levels) {
                val pij = glcm[rowBase + j]

                // Skip zero entries for performance (sparse GLCM optimization)
                if (pij == 0.0) continue

                val jDouble = j.toDouble()
                val diff = iDouble - jDouble

                // Contrast: Σ (i - j)² · P[i,j]
                contrast += diff * diff * pij

                // Correlation numerator: Σ (i - μ_i)(j - μ_j) · P[i,j]
                correlationNumerator += iMinusMuI * (jDouble - muJ) * pij

                // ASM: Σ P[i,j]²
                asmValue += pij * pij

                // Homogeneity: Σ P[i,j] / (1 + |i - j|)
                homogeneity += pij / (1.0 + Math.abs(diff))
            }
        }

        // ── Correlation ─────────────────────────────────────────────────
        // If sigma is zero (constant image), correlation is defined as 1.0
        val correlation = if (sigmaI > 0.0 && sigmaJ > 0.0) {
            correlationNumerator / (sigmaI * sigmaJ)
        } else {
            1.0
        }

        // ── Energy = sqrt(ASM) ──────────────────────────────────────────
        val energy = Math.sqrt(asmValue)

        return GLCMFeatures(
            contrast = contrast,
            correlation = correlation,
            energy = energy,
            homogeneity = homogeneity,
            asm = asmValue,
        )
    }

    /**
     * Computes the arithmetic mean of a collection of [GLCMFeatures].
     *
     * This is useful for producing a single feature vector that summarizes
     * the texture across all 4 angular offsets.
     *
     * @param features Collection of [GLCMFeatures] to average.
     * @return A single [GLCMFeatures] containing the mean of each feature.
     */
    internal fun averageFeatures(features: Collection<GLCMFeatures>): GLCMFeatures {
        val n = features.size.toDouble()
        return GLCMFeatures(
            contrast = features.sumOf { it.contrast } / n,
            correlation = features.sumOf { it.correlation } / n,
            energy = features.sumOf { it.energy } / n,
            homogeneity = features.sumOf { it.homogeneity } / n,
            asm = features.sumOf { it.asm } / n,
        )
    }
}
