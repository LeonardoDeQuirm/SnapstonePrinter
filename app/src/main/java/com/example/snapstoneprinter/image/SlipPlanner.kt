package com.example.snapstoneprinter.image

import com.example.snapstoneprinter.data.model.CardFace
import com.example.snapstoneprinter.data.model.ScryfallCard

/**
 * The resolved, renderer-agnostic content of a single slip.
 *
 * Deliberately free of any `android.*` type so the whole slip-planning decision (how many slips,
 * which art, which label) is covered by plain JVM unit tests.
 */
data class SlipContent(
    val faceIndex: Int,
    val totalSlips: Int,
    val name: String,
    val manaCost: String?,
    val typeLine: String?,
    val oracleText: String?,
    val power: String?,
    val toughness: String?,
    /** Art URL for THIS face - never the top-level one when the card is multi-slip. */
    val artUrl: String?,
    /** Layout-aware header line, or null for single-slip cards. */
    val label: String?,
    /**
     * Full-resolution card image for THIS face (`normal`, then `large`). Never dithered - this is
     * only what the "full card" preview toggle shows next to the thermal composite.
     */
    val fullImageUrl: String? = null
)

/**
 * Decides whether a fetched card prints as one slip or several, and what each slip contains.
 *
 * ## The critical distinction
 *
 * `card_faces[]` alone does NOT mean "double-faced". Both of these groups populate `card_faces[]`:
 *
 * - **Two slips** - `transform`, `modal_dfc`, `reversible_card`: physically two printed sides, so
 *   every face carries its **own** `image_uris`.
 * - **One slip** - `split`, `flip`, `adventure`: physically one card with one piece of art, so
 *   there is a single shared **top-level** `image_uris` and the faces have none.
 *
 * The trigger is therefore the *structural* test in [hasPerFaceArt]: `card_faces` is non-empty AND
 * every face has its own non-null, usable `image_uris`. Keying off `card_faces` presence alone
 * would make Fire // Ice print twice with duplicate art.
 *
 * Layout strings are only consulted for the *wording* of the slip label, never to decide the slip
 * count. `meld` cards arrive from `/cards/random` as individual cards and so naturally fall into
 * the single-slip path.
 */
object SlipPlanner {

    const val LAYOUT_TRANSFORM = "transform"
    const val LAYOUT_MODAL_DFC = "modal_dfc"
    const val LAYOUT_REVERSIBLE_CARD = "reversible_card"

    /**
     * The structural two-slip test: at least two faces, and every one of them owns a usable
     * `image_uris`. This - not the layout string - is what decides the slip count.
     */
    fun hasPerFaceArt(card: ScryfallCard): Boolean {
        val faces = card.card_faces
        if (faces == null || faces.size < 2) return false
        return faces.all { !it.bestArtUrl().isNullOrBlank() }
    }

    /** Number of slips [card] will print as. 1 for everything that is not truly double-faced. */
    fun slipCount(card: ScryfallCard): Int =
        if (hasPerFaceArt(card)) card.card_faces!!.size else 1

    /**
     * The small header line for the slip at [faceIndex].
     *
     * - `transform` front: "Transforms into: {back face name}"
     * - `transform` back:  "Transforms from: {front face name}"
     * - `modal_dfc` / `reversible_card`: "Other side: {other face name}"
     * - anything else that still passes the structural test: "Other side: {other face name}"
     * - single-slip cards: null
     */
    fun labelFor(card: ScryfallCard, faceIndex: Int): String? {
        if (!hasPerFaceArt(card)) return null
        val faces = card.card_faces ?: return null
        if (faceIndex !in faces.indices) return null

        val otherName = faces[(faceIndex + 1) % faces.size].name.nullIfBlank() ?: return null

        return when (card.layout.normalizedLayout()) {
            LAYOUT_TRANSFORM ->
                if (faceIndex == 0) "Transforms into: $otherName" else "Transforms from: $otherName"

            LAYOUT_MODAL_DFC, LAYOUT_REVERSIBLE_CARD -> "Other side: $otherName"

            // Unknown / unexpected multi-face layout that still has per-face art.
            else -> "Other side: $otherName"
        }
    }

    /**
     * Resolves [card] into the ordered list of slips to compose. Size is always [slipCount].
     */
    fun plan(card: ScryfallCard): List<SlipContent> {
        val faces = card.card_faces
        if (faces == null || !hasPerFaceArt(card)) return listOf(singleSlipContent(card))

        val total = faces.size
        return faces.mapIndexed { index, face ->
            SlipContent(
                faceIndex = index,
                totalSlips = total,
                name = face.name.nullIfBlank() ?: card.name,
                manaCost = face.mana_cost.nullIfBlank(),
                typeLine = face.type_line.nullIfBlank(),
                oracleText = face.oracle_text.nullIfBlank(),
                power = face.power.nullIfBlank(),
                toughness = face.toughness.nullIfBlank(),
                artUrl = face.bestArtUrl(),
                label = labelFor(card, index),
                fullImageUrl = face.bestFullUrl()
            )
        }
    }

    /**
     * The legacy single-slip content: everything read through the `effective*` resolvers, no label.
     * Behaviour here is intentionally identical to the pre-multi-slip renderer.
     */
    fun singleSlipContent(card: ScryfallCard): SlipContent = SlipContent(
        faceIndex = 0,
        totalSlips = 1,
        name = card.effectiveName,
        manaCost = card.effectiveManaCost,
        typeLine = card.effectiveTypeLine,
        oracleText = card.effectiveOracleText,
        power = card.effectivePower,
        toughness = card.effectiveToughness,
        artUrl = card.effectiveImageUrl,
        label = null,
        fullImageUrl = card.effectiveNormalUrl
    )

    /** Ordered art URLs, one per slip. Convenience for the download step in the ViewModel. */
    fun artUrls(card: ScryfallCard): List<String?> = plan(card).map { it.artUrl }

    /** art_crop first, then the full card image, exactly like the top-level resolvers. */
    private fun CardFace.bestArtUrl(): String? {
        val uris = image_uris ?: return null
        return uris.artCrop.nullIfBlank() ?: uris.normal.nullIfBlank() ?: uris.large.nullIfBlank()
    }

    /** Full-card image for this face, mirroring [ScryfallCard.effectiveNormalUrl]. */
    private fun CardFace.bestFullUrl(): String? {
        val uris = image_uris ?: return null
        return uris.normal.nullIfBlank() ?: uris.large.nullIfBlank()
    }

    private fun String?.normalizedLayout(): String? = this?.trim()?.lowercase().nullIfBlank()

    private fun String?.nullIfBlank(): String? = if (this.isNullOrBlank()) null else this
}
