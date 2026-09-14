package com.example.snapstoneprinter.data.api

/**
 * Builds the `q` parameter for `cards/random`.
 *
 * Junk-pull filtering is done here first (server side) so that most requests never even return a
 * token/emblem/art-series object; [com.example.snapstoneprinter.data.model.CardLayouts] then acts
 * as a second, authoritative client-side guard.
 */
object ScryfallQueryBuilder {

    /** Scryfall's catch-all negation for tokens, emblems, art series, memorabilia, planes, ... */
    const val EXCLUDE_EXTRAS = "-is:extra"

    const val NON_LAND = "-t:land"

    const val FUNNY = "is:funny"

    /**
     * @param isFunny include silver-bordered / acorn cards.
     * @param nonLandOnly exclude lands.
     */
    fun build(isFunny: Boolean = false, nonLandOnly: Boolean = false): String {
        val terms = mutableListOf<String>()
        if (nonLandOnly) terms += NON_LAND
        if (isFunny) terms += FUNNY
        terms += EXCLUDE_EXTRAS
        return terms.joinToString(" ")
    }
}
