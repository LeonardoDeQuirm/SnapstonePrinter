package com.example.snapstoneprinter.data.api

import com.example.snapstoneprinter.data.model.ScryfallCard
import retrofit2.http.GET
import retrofit2.http.Query

interface ScryfallApiService {
    @GET("cards/random")
    suspend fun getRandomCard(
        @Query("q") query: String? = null
    ): ScryfallCard

    /** Fuzzy name lookup - tolerates typos/partial names the way the Scryfall search bar does. */
    @GET("cards/named")
    suspend fun getCardByName(
        @Query("fuzzy") fuzzy: String
    ): ScryfallCard
}
