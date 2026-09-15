package dev.snapstonewielder.app.data.model

/**
 * Layout classification helpers for `cards/random` results.
 *
 * `cards/random` happily hands back non-playable objects (tokens, emblems, art series cards,
 * memorabilia, ...). Those are useless as printable proxies, so they get filtered out both in the
 * Scryfall query and again client side.
 */
object CardLayouts {

    /** Layouts that are not playable cards and must never be turned into a proxy. */
    val JUNK_LAYOUTS: Set<String> = setOf(
        "token",
        "double_faced_token",
        "art_series",
        "memorabilia",
        "emblem",
        "vanguard",
        "scheme",
        "planar"
    )

    /**
     * Returns true when [layout] identifies a non-playable object that should trigger a re-roll.
     * A null/blank layout is treated as playable (the field is simply missing).
     */
    fun isJunkLayout(layout: String?): Boolean {
        val normalized = layout?.trim()?.lowercase() ?: return false
        return normalized in JUNK_LAYOUTS
    }

    fun isPlayableLayout(layout: String?): Boolean = !isJunkLayout(layout)
}
