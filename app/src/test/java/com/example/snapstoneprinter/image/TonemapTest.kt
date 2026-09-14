package com.example.snapstoneprinter.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TonemapTest {

    @Test
    fun testToLuminance_usesRec601Weights() {
        val white = Tonemap.toLuminance(intArrayOf(0xFFFFFFFF.toInt()))
        val black = Tonemap.toLuminance(intArrayOf(0xFF000000.toInt()))
        assertEquals(255f, white[0], 0.01f)
        assertEquals(0f, black[0], 0.01f)

        val red = Tonemap.toLuminance(intArrayOf(0xFFFF0000.toInt()))
        assertEquals(0.299f * 255f, red[0], 0.01f)
    }

    @Test
    fun testAutoLevels_stretchesDarkImageToFullRange() {
        // A dark ramp: the sort of art_crop that used to dither into a black smear.
        val dark = FloatArray(100) { it * 0.3f } // 0.0 .. 29.7
        Tonemap.applyAutoLevels(dark)

        val min = dark.min()
        val max = dark.max()
        assertTrue("expected stretched max, got $max", max > 240f)
        assertTrue("expected stretched min, got $min", min < 15f)
    }

    @Test
    fun testAutoLevels_rejectsOutliers() {
        // 98 mid-grey samples plus one pure black and one pure white outlier.
        val values = FloatArray(100) { 120f }
        values[0] = 0f
        values[99] = 255f
        Tonemap.applyAutoLevels(values)

        // Outliers get clipped to the ends of the range.
        assertEquals(0f, values[0], 0.01f)
        assertEquals(255f, values[99], 0.01f)
    }

    @Test
    fun testAutoLevels_flatImageIsLeftUntouched() {
        val flat = FloatArray(64) { 136f }
        Tonemap.applyAutoLevels(flat)
        for (value in flat) {
            assertEquals(136f, value, 0.01f)
        }
    }

    @Test
    fun testAutoLevels_emptyBufferIsSafe() {
        val empty = FloatArray(0)
        Tonemap.applyAutoLevels(empty)
        assertEquals(0, empty.size.toLong().toInt())
    }

    @Test
    fun testContrastBrightness_defaultsAreNeutral() {
        val values = floatArrayOf(0f, 64f, 128f, 192f, 255f)
        val original = values.copyOf()
        Tonemap.applyContrastBrightness(
            values,
            Tonemap.DEFAULT_CONTRAST,
            Tonemap.DEFAULT_BRIGHTNESS
        )
        for (i in values.indices) {
            assertEquals(original[i], values[i], 0.001f)
        }
    }

    @Test
    fun testContrast_pushesValuesAwayFromMidGrey() {
        val values = floatArrayOf(100f, 128f, 156f)
        Tonemap.applyContrastBrightness(values, contrast = 2.0f, brightness = 0f)
        assertEquals(72f, values[0], 0.01f)
        assertEquals(128f, values[1], 0.01f)
        assertEquals(184f, values[2], 0.01f)
    }

    @Test
    fun testBrightness_offsetsAndClamps() {
        val values = floatArrayOf(0f, 128f, 250f)
        Tonemap.applyContrastBrightness(values, contrast = 1.0f, brightness = 20f)
        assertEquals(20f, values[0], 0.01f)
        assertEquals(148f, values[1], 0.01f)
        assertEquals(255f, values[2], 0.01f) // clamped
    }

    @Test
    fun testPrepareForDithering_runsAutoLevelsThenContrast() {
        val darkRamp = FloatArray(100) { it * 0.3f }
        val autoLevelsOnly = Tonemap.prepareForDithering(darkRamp.copyOf())
        val withContrast = Tonemap.prepareForDithering(darkRamp.copyOf(), contrast = 2.0f)

        // Auto-levels ran first in both cases, then contrast pushed values further apart.
        assertTrue(autoLevelsOnly.max() > 240f)
        assertTrue(withContrast[0] <= autoLevelsOnly[0])
        assertTrue(withContrast.max() >= autoLevelsOnly.max())
    }
}
