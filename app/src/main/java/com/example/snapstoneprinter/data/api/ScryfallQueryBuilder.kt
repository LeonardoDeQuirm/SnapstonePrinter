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
     * Lands are never a valid pull: the app exists to proxy a card for Snapstone Wielder, which
     * only ever targets a nonland card, so every random query excludes them unconditionally.
     *
     * @param isFunny include silver-bordered / acorn cards.
     */
    fun build(isFunny: Boolean = false): String {
        val terms = mutableListOf(NON_LAND)
        if (isFunny) terms += FUNNY
        terms += EXCLUDE_EXTRAS
        return terms.joinToString(" ")
    }
}
