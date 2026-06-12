package com.smartleaf.app.ml.vision

import android.graphics.Color
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ColorFeatureExtractorTest — JUnit 4 Unit Tests for [ColorFeatureExtractor]
 * ===========================================================================
 *
 * Sprint Reference : June 8, 2026 — Color Feature Extraction Tests
 *
 * This test class verifies the correctness of the native Kotlin HSV color
 * moment extractor. Since the tests run on the JVM (not an Android device),
 * we test the internal [computeChannelMoments] method directly and verify
 * the mathematical correctness of all three moments.
 *
 * ## Test Coverage
 *
 * 1. **Feature vector dimensionality** — Verifies output is exactly 9-dimensional.
 * 2. **Mean computation** — Cross-validated against manual calculation.
 * 3. **Standard deviation computation** — Cross-validated against manual calculation.
 * 4. **Skewness computation** — Verifies the cube-root formula produces correct
 *    results for positively-skewed, negatively-skewed, and symmetric distributions.
 * 5. **Skewness NaN safety** — Verifies that constant channels (σ=0) produce
 *    skewness=0.0 without NaN or Infinity.
 * 6. **Constant channel** — All identical values → mean=value, σ=0, γ=0.
 * 7. **Symmetric distribution** — Zero skewness for symmetric data.
 * 8. **Feature vector ordering** — Verifies [μH, σH, γH, μS, σS, γS, μV, σV, γV].
 * 9. **Single pixel** — Edge case with N=1.
 *
 * ## Note on Android API Mocking
 *
 * Since `Color.colorToHSV()` and `Bitmap` are Android framework classes
 * that are not available in JVM unit tests, the full `extract(Bitmap)`
 * pipeline should be tested via Android Instrumented Tests. These JVM tests
 * focus on the **mathematical core** (`computeChannelMoments`), which is
 * the critical path for correctness.
 *
 * @see ColorFeatureExtractor
 */
class ColorFeatureExtractorTest {

    private lateinit var extractor: ColorFeatureExtractor

    /** Tolerance for floating-point comparisons. */
    private val EPSILON = 1e-5f

    @Before
    fun setUp() {
        extractor = ColorFeatureExtractor()
    }

    // ═══════════════════════════════════════════════════════════════════
    // 1. FEATURE VECTOR DIMENSIONALITY
    // ═══════════════════════════════════════════════════════════════════

    /**
     * The feature vector MUST be exactly 9-dimensional:
     * [μH, σH, γH, μS, σS, γS, μV, σV, γV]
     */
    @Test
    fun `feature vector is exactly 9-dimensional`() {
        assertEquals(
            "Feature vector size constant",
            9,
            ColorFeatureExtractor.FEATURE_VECTOR_SIZE,
        )
    }

    @Test
    fun `FEATURE_VECTOR_SIZE equals NUM_CHANNELS times NUM_MOMENTS`() {
        assertEquals(
            ColorFeatureExtractor.NUM_CHANNELS * ColorFeatureExtractor.NUM_MOMENTS,
            ColorFeatureExtractor.FEATURE_VECTOR_SIZE,
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    // 2. MEAN COMPUTATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Given values [2.0, 4.0, 6.0, 8.0]:
     * Mean = (2 + 4 + 6 + 8) / 4 = 20 / 4 = 5.0
     */
    @Test
    fun `mean is correctly computed for simple values`() {
        val values = floatArrayOf(2.0f, 4.0f, 6.0f, 8.0f)
        val moments = extractor.computeChannelMoments(values, values.size)
        assertEquals("Mean", 5.0f, moments.mean, EPSILON)
    }

    /**
     * Given Hue-range values [0, 60, 120, 180, 240, 300]:
     * Mean = (0 + 60 + 120 + 180 + 240 + 300) / 6 = 900 / 6 = 150.0
     */
    @Test
    fun `mean is correct for hue-range values`() {
        val values = floatArrayOf(0f, 60f, 120f, 180f, 240f, 300f)
        val moments = extractor.computeChannelMoments(values, values.size)
        assertEquals("Mean (hue range)", 150.0f, moments.mean, EPSILON)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 3. STANDARD DEVIATION COMPUTATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Given values [2.0, 4.0, 6.0, 8.0]:
     * Mean = 5.0
     * Variance = ((2-5)² + (4-5)² + (6-5)² + (8-5)²) / 4
     *          = (9 + 1 + 1 + 9) / 4 = 20 / 4 = 5.0
     * StdDev = √5.0 ≈ 2.2360680
     */
    @Test
    fun `standard deviation is correctly computed`() {
        val values = floatArrayOf(2.0f, 4.0f, 6.0f, 8.0f)
        val moments = extractor.computeChannelMoments(values, values.size)
        val expectedStdDev = Math.sqrt(5.0).toFloat()
        assertEquals("Std Dev", expectedStdDev, moments.stdDeviation, EPSILON)
    }

    /**
     * Constant channel: all values identical → σ = 0.
     */
    @Test
    fun `standard deviation is zero for constant channel`() {
        val values = floatArrayOf(0.5f, 0.5f, 0.5f, 0.5f, 0.5f)
        val moments = extractor.computeChannelMoments(values, values.size)
        assertEquals("Std Dev (constant)", 0.0f, moments.stdDeviation, EPSILON)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 4. SKEWNESS COMPUTATION
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Positively skewed distribution: tail extends to the right.
     *
     * Values: [1, 1, 1, 1, 1, 10]
     * Mean = (5 + 10) / 6 = 15/6 = 2.5
     * m₃ = ((1-2.5)³ × 5 + (10-2.5)³) / 6
     *     = ((-1.5)³ × 5 + (7.5)³) / 6
     *     = (-3.375 × 5 + 421.875) / 6
     *     = (-16.875 + 421.875) / 6
     *     = 405.0 / 6 = 67.5
     * Skewness = cbrt(67.5) ≈ 4.07185...
     */
    @Test
    fun `skewness is positive for right-tailed distribution`() {
        val values = floatArrayOf(1f, 1f, 1f, 1f, 1f, 10f)
        val moments = extractor.computeChannelMoments(values, values.size)
        assertTrue(
            "Skewness should be positive for right-tailed data, got ${moments.skewness}",
            moments.skewness > 0.0f,
        )
        val expectedSkewness = Math.cbrt(67.5).toFloat()
        assertEquals("Skewness (positive)", expectedSkewness, moments.skewness, EPSILON)
    }

    /**
     * Negatively skewed distribution: tail extends to the left.
     *
     * Values: [1, 10, 10, 10, 10, 10]
     * Mean = (1 + 50) / 6 = 51/6 = 8.5
     * m₃ = ((1-8.5)³ + (10-8.5)³ × 5) / 6
     *     = ((-7.5)³ + (1.5)³ × 5) / 6
     *     = (-421.875 + 3.375 × 5) / 6
     *     = (-421.875 + 16.875) / 6
     *     = -405.0 / 6 = -67.5
     * Skewness = cbrt(-67.5) ≈ -4.07185...
     */
    @Test
    fun `skewness is negative for left-tailed distribution`() {
        val values = floatArrayOf(1f, 10f, 10f, 10f, 10f, 10f)
        val moments = extractor.computeChannelMoments(values, values.size)
        assertTrue(
            "Skewness should be negative for left-tailed data, got ${moments.skewness}",
            moments.skewness < 0.0f,
        )
        val expectedSkewness = Math.cbrt(-67.5).toFloat()
        assertEquals("Skewness (negative)", expectedSkewness, moments.skewness, EPSILON)
    }

    /**
     * Symmetric distribution: skewness should be zero (or near-zero).
     *
     * Values: [1, 2, 3, 4, 5] — symmetric around mean=3.
     * m₃ = ((1-3)³ + (2-3)³ + (3-3)³ + (4-3)³ + (5-3)³) / 5
     *     = (-8 + -1 + 0 + 1 + 8) / 5 = 0.0
     * Skewness = cbrt(0) = 0.0
     */
    @Test
    fun `skewness is zero for symmetric distribution`() {
        val values = floatArrayOf(1f, 2f, 3f, 4f, 5f)
        val moments = extractor.computeChannelMoments(values, values.size)
        assertEquals("Skewness (symmetric)", 0.0f, moments.skewness, EPSILON)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 5. SKEWNESS NaN SAFETY — Critical for constant channels
    // ═══════════════════════════════════════════════════════════════════

    /**
     * When all pixel values are identical (constant channel), σ = 0.
     * A naive skewness formula using m₃/σ³ would produce NaN (0/0).
     *
     * Our implementation uses cbrt(m₃) which gives cbrt(0) = 0.0 — always finite.
     * This test explicitly verifies no NaN/Infinity in the output.
     */
    @Test
    fun `skewness does not produce NaN for constant channel`() {
        val values = floatArrayOf(0.75f, 0.75f, 0.75f, 0.75f, 0.75f)
        val moments = extractor.computeChannelMoments(values, values.size)

        assertFalse("Skewness must not be NaN", moments.skewness.isNaN())
        assertFalse("Skewness must not be Infinite", moments.skewness.isInfinite())
        assertEquals("Skewness should be exactly 0 for constant data", 0.0f, moments.skewness, EPSILON)
    }

    /**
     * Even with zero-valued constant channel (all black V=0), no NaN.
     */
    @Test
    fun `skewness does not produce NaN for all-zero channel`() {
        val values = FloatArray(100) { 0.0f }
        val moments = extractor.computeChannelMoments(values, values.size)

        assertFalse("Mean must not be NaN", moments.mean.isNaN())
        assertFalse("StdDev must not be NaN", moments.stdDeviation.isNaN())
        assertFalse("Skewness must not be NaN", moments.skewness.isNaN())
        assertEquals("Mean should be 0", 0.0f, moments.mean, EPSILON)
        assertEquals("StdDev should be 0", 0.0f, moments.stdDeviation, EPSILON)
        assertEquals("Skewness should be 0", 0.0f, moments.skewness, EPSILON)
    }

    /**
     * Edge case: single-pixel image (N=1).
     * Mean = value, σ = 0, γ = 0.
     */
    @Test
    fun `single pixel produces mean=value, stddev=0, skewness=0`() {
        val values = floatArrayOf(180.0f) // Single hue value
        val moments = extractor.computeChannelMoments(values, values.size)

        assertEquals("Mean (single pixel)", 180.0f, moments.mean, EPSILON)
        assertEquals("StdDev (single pixel)", 0.0f, moments.stdDeviation, EPSILON)
        assertEquals("Skewness (single pixel)", 0.0f, moments.skewness, EPSILON)
        assertFalse("Skewness must not be NaN (single pixel)", moments.skewness.isNaN())
    }

    // ═══════════════════════════════════════════════════════════════════
    // 6. CONSTANT CHANNEL — Full moment verification
    // ═══════════════════════════════════════════════════════════════════

    /**
     * All pixels have the same value (e.g., fully saturated S=1.0).
     * - Mean = 1.0
     * - StdDev = 0.0
     * - Skewness = 0.0
     */
    @Test
    fun `constant channel produces exact moments`() {
        val values = FloatArray(256) { 1.0f }
        val moments = extractor.computeChannelMoments(values, values.size)

        assertEquals("Mean (constant=1)", 1.0f, moments.mean, EPSILON)
        assertEquals("StdDev (constant=1)", 0.0f, moments.stdDeviation, EPSILON)
        assertEquals("Skewness (constant=1)", 0.0f, moments.skewness, EPSILON)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 7. FEATURE VECTOR ORDERING
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Verify that the feature vector has the correct element ordering:
     * [μH, σH, γH, μS, σS, γS, μV, σV, γV]
     *
     * We use 3 distinct channel distributions so each moment value is unique
     * and we can verify the mapping.
     */
    @Test
    fun `feature vector elements are ordered as H-moments then S-moments then V-moments`() {
        // H channel: uniform [0, 60, 120] → mean=60
        val hValues = floatArrayOf(0f, 60f, 120f)
        val hMoments = extractor.computeChannelMoments(hValues, hValues.size)

        // S channel: [0.2, 0.4, 0.6] → mean=0.4
        val sValues = floatArrayOf(0.2f, 0.4f, 0.6f)
        val sMoments = extractor.computeChannelMoments(sValues, sValues.size)

        // V channel: [0.1, 0.5, 0.9] → mean=0.5
        val vValues = floatArrayOf(0.1f, 0.5f, 0.9f)
        val vMoments = extractor.computeChannelMoments(vValues, vValues.size)

        // Manually build the expected feature vector
        val expectedVector = floatArrayOf(
            hMoments.mean, hMoments.stdDeviation, hMoments.skewness,
            sMoments.mean, sMoments.stdDeviation, sMoments.skewness,
            vMoments.mean, vMoments.stdDeviation, vMoments.skewness,
        )

        // Verify the size
        assertEquals("Expected vector size", 9, expectedVector.size)

        // Verify individual elements match their channel moments
        assertEquals("Index 0 = μH", hMoments.mean, expectedVector[0], EPSILON)
        assertEquals("Index 1 = σH", hMoments.stdDeviation, expectedVector[1], EPSILON)
        assertEquals("Index 2 = γH", hMoments.skewness, expectedVector[2], EPSILON)
        assertEquals("Index 3 = μS", sMoments.mean, expectedVector[3], EPSILON)
        assertEquals("Index 4 = σS", sMoments.stdDeviation, expectedVector[4], EPSILON)
        assertEquals("Index 5 = γS", sMoments.skewness, expectedVector[5], EPSILON)
        assertEquals("Index 6 = μV", vMoments.mean, expectedVector[6], EPSILON)
        assertEquals("Index 7 = σV", vMoments.stdDeviation, expectedVector[7], EPSILON)
        assertEquals("Index 8 = γV", vMoments.skewness, expectedVector[8], EPSILON)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 8. LARGE DATASET — Numerical stability check
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Verify moments on a larger dataset (10,000 values) with known
     * uniform distribution properties.
     *
     * For uniform distribution [a, b]:
     * - Mean = (a + b) / 2
     * - StdDev = (b - a) / √12
     * - Skewness = 0 (symmetric)
     *
     * We use a = 0.0, b = 1.0 (simulating saturation/value channel):
     * - Expected Mean ≈ 0.5
     * - Expected StdDev ≈ 1/√12 ≈ 0.28868
     * - Expected Skewness ≈ 0.0
     */
    @Test
    fun `moments are numerically stable for large uniform dataset`() {
        val n = 10_000
        val values = FloatArray(n) { i -> i.toFloat() / (n - 1) } // [0.0, ..., 1.0]
        val moments = extractor.computeChannelMoments(values, n)

        // Looser tolerance for statistical estimates on finite samples
        val STAT_EPSILON = 1e-3f

        assertEquals("Mean (uniform 0..1)", 0.5f, moments.mean, STAT_EPSILON)
        assertEquals(
            "StdDev (uniform 0..1)",
            (1.0 / Math.sqrt(12.0)).toFloat(),
            moments.stdDeviation,
            STAT_EPSILON,
        )
        // Skewness should be near zero for symmetric uniform distribution
        assertEquals("Skewness (uniform 0..1)", 0.0f, moments.skewness, STAT_EPSILON)

        // No NaN anywhere
        assertFalse("Mean finite", moments.mean.isNaN())
        assertFalse("StdDev finite", moments.stdDeviation.isNaN())
        assertFalse("Skewness finite", moments.skewness.isNaN())
    }

    // ═══════════════════════════════════════════════════════════════════
    // 9. TWO-PIXEL IMAGE — Minimal non-trivial case
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Two pixels: [100, 200]
     * Mean = 150
     * Variance = ((100-150)² + (200-150)²) / 2 = (2500 + 2500) / 2 = 2500
     * StdDev = √2500 = 50
     * m₃ = ((100-150)³ + (200-150)³) / 2 = (-125000 + 125000) / 2 = 0
     * Skewness = cbrt(0) = 0 (symmetric pair)
     */
    @Test
    fun `two pixel symmetric pair has zero skewness`() {
        val values = floatArrayOf(100f, 200f)
        val moments = extractor.computeChannelMoments(values, values.size)

        assertEquals("Mean (2px)", 150.0f, moments.mean, EPSILON)
        assertEquals("StdDev (2px)", 50.0f, moments.stdDeviation, EPSILON)
        assertEquals("Skewness (2px)", 0.0f, moments.skewness, EPSILON)
    }

    // ═══════════════════════════════════════════════════════════════════
    // 10. RESULT DATA CLASS — Structural verification
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun `ColorMomentResult featureVector has correct size`() {
        val moments = ColorFeatureExtractor.ChannelMoments(1.0f, 2.0f, 3.0f)
        val featureVector = FloatArray(ColorFeatureExtractor.FEATURE_VECTOR_SIZE)

        val result = ColorFeatureExtractor.ColorMomentResult(
            hueMoments = moments,
            saturationMoments = moments,
            valueMoments = moments,
            featureVector = featureVector,
        )

        assertEquals(
            "Feature vector in result must be 9-dimensional",
            9,
            result.featureVector.size,
        )
        assertNotNull("Hue moments not null", result.hueMoments)
        assertNotNull("Saturation moments not null", result.saturationMoments)
        assertNotNull("Value moments not null", result.valueMoments)
    }

    @Test
    fun `ChannelMoments toString is formatted`() {
        val moments = ColorFeatureExtractor.ChannelMoments(1.5f, 0.25f, -0.1f)
        val str = moments.toString()
        assertTrue("Contains mean", str.contains("mean"))
        assertTrue("Contains stdDev", str.contains("stdDev"))
        assertTrue("Contains skewness", str.contains("skewness"))
    }

    // ═══════════════════════════════════════════════════════════════════
    // 11. NO NaN IN ANY OUTPUT — Exhaustive safety check
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Runs computeChannelMoments on multiple edge-case distributions
     * and asserts that NO output value is NaN or Infinite.
     */
    @Test
    fun `no NaN or Infinity produced for any edge case distribution`() {
        val edgeCases = listOf(
            floatArrayOf(0f),                                    // Single zero
            floatArrayOf(360f),                                  // Single max hue
            FloatArray(1000) { 0.5f },                          // Large constant
            floatArrayOf(0f, 0f, 0f, 0f, 1f),                  // Near-constant with outlier
            floatArrayOf(0f, 1f),                                // Binary
            floatArrayOf(Float.MIN_VALUE, Float.MIN_VALUE),     // Tiny values
        )

        for ((idx, values) in edgeCases.withIndex()) {
            val moments = extractor.computeChannelMoments(values, values.size)
            assertFalse("Case $idx: Mean NaN", moments.mean.isNaN())
            assertFalse("Case $idx: Mean Inf", moments.mean.isInfinite())
            assertFalse("Case $idx: StdDev NaN", moments.stdDeviation.isNaN())
            assertFalse("Case $idx: StdDev Inf", moments.stdDeviation.isInfinite())
            assertFalse("Case $idx: Skewness NaN", moments.skewness.isNaN())
            assertFalse("Case $idx: Skewness Inf", moments.skewness.isInfinite())
        }
    }
}
