package dev.snapstonewielder.app.image

import android.graphics.Bitmap

/**
 * One physical strip of thermal paper.
 *
 * A normal card produces exactly one slip. A true double-faced card (transform / modal_dfc /
 * reversible_card - i.e. every face carries its own `image_uris`) produces one slip per face so
 * that each face gets its own art instead of crowding a single slip.
 *
 * @param bitmap the composed, dithered slip. Always exactly [ImageProcessor.OUTPUT_WIDTH] px wide.
 * @param faceName the name printed on this slip (the face name for multi-slip cards, otherwise the
 *   card's effective name).
 * @param faceIndex zero-based index of this slip within [totalSlips].
 * @param totalSlips how many slips this card produced in total.
 * @param label the small layout-aware header line ("Transforms into: ...", "Other side: ..."), or
 *   null for single-slip cards.
 * @param fullImageUrl full-resolution Scryfall image for this face, used ONLY by the "full card"
 *   preview toggle. The printed output is always [bitmap].
 */
data class PrintSlip(
    val bitmap: Bitmap,
    val faceName: String,
    val faceIndex: Int,
    val totalSlips: Int,
    val label: String?,
    val fullImageUrl: String? = null
)
