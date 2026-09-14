package com.example.snapstoneprinter.data.api

import com.example.snapstoneprinter.data.model.ScryfallCard
import retrofit2.http.GET
import retrofit2.http.Query

interface ScryfallApiService {
    @GET("cards/random")
    suspend fun getRandomCard(
        @Query("q") query: String? = null
    ): ScryfallCard
}
