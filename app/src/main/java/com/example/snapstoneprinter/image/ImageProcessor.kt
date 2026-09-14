package com.example.snapstoneprinter.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import com.example.snapstoneprinter.data.model.ScryfallCard
import com.example.snapstoneprinter.data.util.ManaCostFormatter

object ImageProcessor {

    private const val TAG = "ImageProcessor"

    /** Thermal printer head width. Immutable spec - never change. */
    const val OUTPUT_WIDTH = 384

    /**
     * Blank white rows appended below the composed proxy so a tear-off at the printer's cutter
     * never clips the last line of text.
     */
    const val TRAILING_FEED_WHITESPACE_PX = 48

    const val DEFAULT_CONTRAST = Tonemap.DEFAULT_CONTRAST
    const val DEFAULT_BRIGHTNESS = Tonemap.DEFAULT_BRIGHTNESS

    private const val PADDING = 12

    /**
     * Converts a Bitmap to monochrome.
     *
     * Order of operations (fixed):
     *  1. RGB -> luminance
     *  2. auto-levels (2nd..98th percentile histogram stretch)
     *  3. contrast / brightness
     *  4. Floyd-Steinberg error diffusion (7/3/5/1 over 16)
     *
     * @param contrast multiplier around mid grey; [DEFAULT_CONTRAST] is neutral.
     * @param brightness offset in luminance units; [DEFAULT_BRIGHTNESS] is neutral.
     */
    @JvmOverloads
    fun applyFloydSteinbergDithering(
        src: Bitmap,
        contrast: Float = DEFAULT_CONTRAST,
        brightness: Float = DEFAULT_BRIGHTNESS
    ): Bitmap {
        val width = src.width
        val height = src.height
        val pixels = IntArray(width * height)
        src.getPixels(pixels, 0, width, 0, 0, width, height)

        // 1-3: luminance, then auto-levels, then contrast/brightness. Always BEFORE dithering.
        val gray = Tonemap.prepareForDithering(
            luminance = Tonemap.toLuminance(pixels),
            contrast = contrast,
            brightness = brightness
        )

        // 4: Floyd-Steinberg error diffusion. Kernel is immutable spec.
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val oldPixel = gray[idx]
                val newPixel = if (oldPixel >= 128f) 255f else 0f
                gray[idx] = newPixel

                val error = oldPixel - newPixel

                if (x + 1 < width) {
                    gray[idx + 1] += error * 7f / 16f
                }
                if (y + 1 < height) {
                    if (x - 1 >= 0) {
                        gray[(y + 1) * width + x - 1] += error * 3f / 16f
                    }
                    gray[(y + 1) * width + x] += error * 5f / 16f
                    if (x + 1 < width) {
                        gray[(y + 1) * width + x + 1] += error * 1f / 16f
                    }
                }
            }
        }

        val outPixels = IntArray(width * height)
        for (i in outPixels.indices) {
            val g = gray[i].coerceIn(0f, 255f).toInt()
            outPixels[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }

        val dest = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        dest.setPixels(outPixels, 0, width, 0, 0, width, height)
        return dest
    }

    /**
     * Composition entry point.
     *
     * Turns a fetched card into the ordered list of slips to print. A normal card yields exactly
     * one slip; a true double-faced card (see [SlipPlanner.hasPerFaceArt]) yields one slip per
     * face, each composed from THAT face's own art / mana cost / type line / oracle text / P-T.
     *
     * Every slip is independently [OUTPUT_WIDTH] px wide and gets its own
     * [TRAILING_FEED_WHITESPACE_PX] tear-off band.
     *
     * @param ditheredArt already-dithered art, indexed by slip. `getOrNull` is used, so passing an
     *   empty list simply renders every slip text-only.
     */
    @JvmOverloads
    fun composePrintSlips(
        card: ScryfallCard,
        ditheredArt: List<Bitmap?> = emptyList()
    ): List<PrintSlip> = composeSlips(SlipPlanner.plan(card), ditheredArt)

    /**
     * Renders a pre-resolved [plan] (see [SlipPlanner.plan]). Split out from [composePrintSlips] so
     * callers can download the per-face art referenced by the plan before composing.
     */
    fun composeSlips(
        plan: List<SlipContent>,
        ditheredArt: List<Bitmap?> = emptyList()
    ): List<PrintSlip> = plan.map { content ->
        PrintSlip(
            bitmap = renderSlip(content, ditheredArt.getOrNull(content.faceIndex)),
            faceName = content.name,
            faceIndex = content.faceIndex,
            totalSlips = content.totalSlips,
            label = content.label,
            fullImageUrl = content.fullImageUrl
        )
    }

    /**
     * Composites card text and the dithered art_crop image into a single Bitmap whose width is
     * always exactly [OUTPUT_WIDTH] pixels.
     *
     * No card frames are drawn: the text is rendered natively on Canvas below the dithered art in
     * high-contrast sans-serif on pure white. Mana costs are plain text - no pip symbols.
     *
     * All card data is read through the `effective*` resolvers so that layouts which carry null
     * top-level fields (transform / modal_dfc / split / flip) still render. This is the legacy
     * single-slip path, kept for callers that only ever want one bitmap.
     */
    fun compositeCardProxy(card: ScryfallCard, ditheredArt: Bitmap?): Bitmap =
        renderSlip(SlipPlanner.singleSlipContent(card), ditheredArt)

    /**
     * Draws one slip: optional small header label, name + plain-text mana cost, type line, dithered
     * art, oracle text, power/toughness, then the trailing blank feed band.
     */
    private fun renderSlip(content: SlipContent, ditheredArt: Bitmap?): Bitmap {
        val canvasWidth = OUTPUT_WIDTH
        val padding = PADDING
        val textWidth = canvasWidth - (padding * 2)

        // Setup high-contrast sans-serif paints
        val labelPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }

        val titlePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }

        val typePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 18f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }

        val oraclePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 16f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            isAntiAlias = true
        }

        val ptPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            isAntiAlias = true
        }

        // Measure heights dynamically
        var currentY = padding.toFloat()

        val labelText = content.label
        var labelLayout: StaticLayout? = null
        if (!labelText.isNullOrBlank()) {
            labelLayout = StaticLayout.Builder
                .obtain(labelText, 0, labelText.length, labelPaint, textWidth)
                .build()
            currentY += labelLayout.height + 6f
        }

        val manaCost = safeManaCost(content.manaCost)
        val titleText = content.name + (if (manaCost.isNotEmpty()) "  $manaCost" else "")
        val titleLayout = StaticLayout.Builder.obtain(titleText, 0, titleText.length, titlePaint, textWidth).build()
        currentY += titleLayout.height + 8f

        val typeText = content.typeLine ?: ""
        val typeLayout = StaticLayout.Builder.obtain(typeText, 0, typeText.length, typePaint, textWidth).build()
        currentY += typeLayout.height + 12f

        var imgHeight = 0
        if (ditheredArt != null && ditheredArt.width > 0) {
            imgHeight = (ditheredArt.height * textWidth) / ditheredArt.width
            currentY += imgHeight + 12f
        }

        val oracleText = content.oracleText ?: ""
        val oracleLayout = StaticLayout.Builder.obtain(oracleText, 0, oracleText.length, oraclePaint, textWidth).build()
        currentY += oracleLayout.height

        val power = content.power
        val toughness = content.toughness
        var ptLayout: StaticLayout? = null
        if (!power.isNullOrEmpty() && !toughness.isNullOrEmpty()) {
            currentY += 8f
            val ptText = "$power/$toughness"
            ptLayout = StaticLayout.Builder.obtain(ptText, 0, ptText.length, ptPaint, textWidth)
                .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
                .build()
            currentY += ptLayout.height
        }

        currentY += padding.toFloat()
        // Trailing blank feed so the tear-off never clips the last line. Per slip.
        currentY += TRAILING_FEED_WHITESPACE_PX
        val totalHeight = currentY.toInt()

        // Create bitmap and canvas
        val resultBitmap = Bitmap.createBitmap(canvasWidth, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        canvas.drawColor(Color.WHITE)

        // Draw components
        var drawY = padding.toFloat()

        if (labelLayout != null) {
            canvas.save()
            canvas.translate(padding.toFloat(), drawY)
            labelLayout.draw(canvas)
            canvas.restore()
            drawY += labelLayout.height + 6f
        }

        canvas.save()
        canvas.translate(padding.toFloat(), drawY)
        titleLayout.draw(canvas)
        canvas.restore()
        drawY += titleLayout.height + 8f

        canvas.save()
        canvas.translate(padding.toFloat(), drawY)
        typeLayout.draw(canvas)
        canvas.restore()
        drawY += typeLayout.height + 12f

        if (ditheredArt != null && imgHeight > 0) {
            val srcRect = Rect(0, 0, ditheredArt.width, ditheredArt.height)
            val destRect = Rect(padding, drawY.toInt(), padding + textWidth, drawY.toInt() + imgHeight)
            canvas.drawBitmap(ditheredArt, srcRect, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
            drawY += imgHeight + 12f
        }

        canvas.save()
        canvas.translate(padding.toFloat(), drawY)
        oracleLayout.draw(canvas)
        canvas.restore()
        drawY += oracleLayout.height

        if (ptLayout != null) {
            drawY += 8f
            canvas.save()
            canvas.translate(padding.toFloat(), drawY)
            ptLayout.draw(canvas)
            canvas.restore()
        }

        return resultBitmap
    }

    /**
     * Formats a mana cost for the title line, degrading gracefully instead of failing the render.
     *
     * Mana-cost formatting is purely cosmetic: it strips the braces off `{2}{U}{U}`. A problem
     * there must never be able to take down the whole bitmap composition, so any failure falls
     * back to the raw Scryfall string. Printing "{2}{U}{U}" is a blemish; printing nothing at all
     * because the app crashed is a P0.
     *
     * [Throwable] is caught deliberately and not narrowed to [Exception]. The real outage this
     * guards against was an invalid regex thrown from a static initialiser, which surfaces as
     * `ExceptionInInitializerError` and thereafter `NoClassDefFoundError` - both `Error`s, so a
     * `catch (e: Exception)` would have sailed straight past them and crashed the process anyway.
     */
    private fun safeManaCost(raw: String?): String = try {
        ManaCostFormatter.normalizeManaCost(raw)
    } catch (t: Throwable) {
        Log.w(TAG, "Mana cost formatting failed; falling back to the raw value", t)
        raw?.trim().orEmpty()
    }
}
