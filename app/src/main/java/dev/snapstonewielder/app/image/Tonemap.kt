package dev.snapstonewielder.app.image

/**
 * Pure (Android-free) luminance tone-mapping stage of the print pipeline.
 *
 * Pipeline order is fixed and must not change:
 *   RGB -> luminance -> auto-levels -> contrast/brightness -> Floyd-Steinberg dithering
 *
 * Running auto-levels BEFORE dithering is what stops dark `art_crop` images from collapsing into
 * a solid black smear that is illegible, slow to print and wastes paper.
 */
object Tonemap {

    /** Neutral contrast multiplier: 1.0 means "leave the auto-levelled buffer alone". */
    const val DEFAULT_CONTRAST = 1.0f

    /** Neutral brightness offset in luminance units: 0.0 means "leave the buffer alone". */
    const val DEFAULT_BRIGHTNESS = 0.0f

    /** Low percentile clipped by auto-levels (rejects dark outliers). */
    const val AUTO_LEVELS_LOW_PERCENTILE = 0.02f

    /** High percentile clipped by auto-levels (rejects bright outliers). */
    const val AUTO_LEVELS_HIGH_PERCENTILE = 0.98f

    private const val MID_POINT = 128f

    /** Converts packed ARGB pixels to a Rec.601 luminance buffer in the 0..255 range. */
    fun toLuminance(pixels: IntArray): FloatArray {
        val out = FloatArray(pixels.size)
        for (i in pixels.indices) {
            val color = pixels[i]
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            out[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }
        return out
    }

    /**
     * Histogram contrast stretch. Maps the [lowPercentile]..[highPercentile] luminance range onto
     * the full 0..255 range, in place.
     *
     * Degenerate inputs (empty buffer, flat image, inverted percentile window) are left untouched
     * rather than being blown out.
     */
    fun applyAutoLevels(
        luminance: FloatArray,
        lowPercentile: Float = AUTO_LEVELS_LOW_PERCENTILE,
        highPercentile: Float = AUTO_LEVELS_HIGH_PERCENTILE
    ): FloatArray {
        if (luminance.isEmpty()) return luminance

        val histogram = IntArray(256)
        for (value in luminance) {
            histogram[value.coerceIn(0f, 255f).toInt().coerceIn(0, 255)]++
        }

        val total = luminance.size
        val lowTarget = (total * lowPercentile).toInt()
        val highTarget = (total * highPercentile).toInt().coerceAtMost(total)

        var cumulative = 0
        var low = 0
        for (i in 0..255) {
            cumulative += histogram[i]
            if (cumulative > lowTarget) {
                low = i
                break
            }
        }

        cumulative = 0
        var high = 255
        for (i in 0..255) {
            cumulative += histogram[i]
            if (cumulative >= highTarget) {
                high = i
                break
            }
        }

        if (high <= low) return luminance

        val scale = 255f / (high - low).toFloat()
        for (i in luminance.indices) {
            luminance[i] = ((luminance[i] - low) * scale).coerceIn(0f, 255f)
        }
        return luminance
    }

    /**
     * Applies a contrast multiplier around the mid grey point plus a brightness offset, in place.
     * With the neutral defaults this is a no-op, so default behaviour == auto-levels only.
     */
    fun applyContrastBrightness(
        luminance: FloatArray,
        contrast: Float = DEFAULT_CONTRAST,
        brightness: Float = DEFAULT_BRIGHTNESS
    ): FloatArray {
        if (contrast == DEFAULT_CONTRAST && brightness == DEFAULT_BRIGHTNESS) return luminance
        for (i in luminance.indices) {
            luminance[i] =
                ((luminance[i] - MID_POINT) * contrast + MID_POINT + brightness).coerceIn(0f, 255f)
        }
        return luminance
    }

    /**
     * Full pre-dither tone-mapping stage: auto-levels first, then contrast/brightness.
     * Never call this after dithering.
     */
    fun prepareForDithering(
        luminance: FloatArray,
        contrast: Float = DEFAULT_CONTRAST,
        brightness: Float = DEFAULT_BRIGHTNESS
    ): FloatArray {
        applyAutoLevels(luminance)
        applyContrastBrightness(luminance, contrast, brightness)
        return luminance
    }
}
