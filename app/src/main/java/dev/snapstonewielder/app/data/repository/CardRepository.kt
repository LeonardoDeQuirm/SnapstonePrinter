package dev.snapstonewielder.app.data.repository

import dev.snapstonewielder.app.data.api.ScryfallApiService
import dev.snapstonewielder.app.data.api.ScryfallQueryBuilder
import dev.snapstonewielder.app.data.model.CardLayouts
import dev.snapstonewielder.app.data.model.ScryfallCard

class CardRepository(private val apiService: ScryfallApiService) {

    /** Always excludes lands - see [ScryfallQueryBuilder.build]. */
    suspend fun getRandomCard(isFunny: Boolean = false): ScryfallCard =
        fetchPlayableCard(ScryfallQueryBuilder.build(isFunny = isFunny))

    /** Momir Vig's ability: a random CREATURE card of the chosen CMC - see [ScryfallQueryBuilder.buildMomirVig]. */
    suspend fun getMomirVigCreature(cmc: Int, isFunny: Boolean = false): ScryfallCard =
        fetchPlayableCard(ScryfallQueryBuilder.buildMomirVig(cmc, isFunny))

    /**
     * Fetches an exact (fuzzy-matched) card by name.
     *
     * Deliberately skips [fetchPlayableCard]'s junk-layout reroll: the caller asked for THIS card
     * by name, so a token/emblem/art-series result is a valid answer, not a layout to reroll away
     * from. Also doubles as the way to force-generate a specific split/flip/adventure/transform
     * card for testing the renderer without waiting on `cards/random`.
     */
    suspend fun getCardByName(name: String): ScryfallCard = apiService.getCardByName(fuzzy = name)

    /**
     * Fetches a random card, re-rolling when Scryfall hands back a non-playable object
     * (token / emblem / art series / ...). Capped at [MAX_REROLL_ATTEMPTS] so a pathological
     * query can never spin forever; the last result is returned as a best effort.
     */
    private suspend fun fetchPlayableCard(query: String?): ScryfallCard {
        var lastCard: ScryfallCard? = null
        repeat(MAX_REROLL_ATTEMPTS) {
            val card = apiService.getRandomCard(query)
            lastCard = card
            if (CardLayouts.isPlayableLayout(card.layout)) {
                return card
            }
        }
        return lastCard ?: apiService.getRandomCard(query)
    }

    companion object {
        /** Maximum number of `cards/random` calls before giving up on avoiding junk layouts. */
        const val MAX_REROLL_ATTEMPTS = 5
    }
}
