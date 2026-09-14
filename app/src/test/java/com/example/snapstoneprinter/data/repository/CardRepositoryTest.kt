package com.example.snapstoneprinter.data.repository

import com.example.snapstoneprinter.data.api.ScryfallApiService
import com.example.snapstoneprinter.data.model.ImageUris
import com.example.snapstoneprinter.data.model.ScryfallCard
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardRepositoryTest {

    /**
     * @param layouts layout value handed back per successive call; the last value repeats forever.
     */
    private class FakeScryfallApiService(
        private val layouts: List<String> = listOf("normal")
    ) : ScryfallApiService {
        var lastQuery: String? = "UNINITIALIZED"
        var callCount = 0

        override suspend fun getRandomCard(query: String?): ScryfallCard {
            lastQuery = query
            val layout = layouts[minOf(callCount, layouts.lastIndex)]
            callCount++
            return ScryfallCard(
                name = "Black Lotus",
                mana_cost = "{0}",
                type_line = "Artifact",
                oracle_text = "T, Sacrifice Black Lotus: Add three mana of any one color.",
                power = null,
                toughness = null,
                image_uris = ImageUris(artCrop = "https://example.com/art.jpg"),
                layout = layout
            )
        }
    }

    // ---------------------------------------------------------------- queries

    @Test
    fun testGetRandomCard_noToggle() = runTest {
        val fakeApi = FakeScryfallApiService()
        val repository = CardRepository(fakeApi)
        val card = repository.getRandomCard(isFunny = false)

        assertEquals("Black Lotus", card.name)
        assertEquals("-is:extra", fakeApi.lastQuery)
    }

    @Test
    fun testGetRandomCard_withFunnyToggle() = runTest {
        val fakeApi = FakeScryfallApiService()
        val repository = CardRepository(fakeApi)
        val card = repository.getRandomCard(isFunny = true)

        assertEquals("Black Lotus", card.name)
        assertEquals("is:funny -is:extra", fakeApi.lastQuery)
    }

    @Test
    fun testGetRandomNonLandCard_noToggle() = runTest {
        val fakeApi = FakeScryfallApiService()
        val repository = CardRepository(fakeApi)
        repository.getRandomNonLandCard(isFunny = false)

        assertEquals("-t:land -is:extra", fakeApi.lastQuery)
    }

    @Test
    fun testGetRandomNonLandCard_withFunnyToggle() = runTest {
        val fakeApi = FakeScryfallApiService()
        val repository = CardRepository(fakeApi)
        repository.getRandomNonLandCard(isFunny = true)

        assertEquals("-t:land is:funny -is:extra", fakeApi.lastQuery)
    }

    // ------------------------------------------------- junk layout re-rolling

    @Test
    fun testPlayableCard_isReturnedWithoutReroll() = runTest {
        val fakeApi = FakeScryfallApiService(listOf("normal"))
        val repository = CardRepository(fakeApi)
        repository.getRandomCard()

        assertEquals(1, fakeApi.callCount)
    }

    @Test
    fun testJunkLayouts_triggerReroll() = runTest {
        val fakeApi = FakeScryfallApiService(listOf("token", "emblem", "art_series", "normal"))
        val repository = CardRepository(fakeApi)
        val card = repository.getRandomCard()

        assertEquals(4, fakeApi.callCount)
        assertEquals("normal", card.layout)
    }

    @Test
    fun testDoubleFacedToken_triggersReroll() = runTest {
        val fakeApi = FakeScryfallApiService(listOf("double_faced_token", "transform"))
        val repository = CardRepository(fakeApi)
        val card = repository.getRandomCard()

        assertEquals(2, fakeApi.callCount)
        assertEquals("transform", card.layout)
    }

    @Test
    fun testRerollIsCapped() = runTest {
        val fakeApi = FakeScryfallApiService(listOf("token"))
        val repository = CardRepository(fakeApi)
        val card = repository.getRandomCard()

        assertEquals(CardRepository.MAX_REROLL_ATTEMPTS, fakeApi.callCount)
        // Best-effort fallback rather than an exception.
        assertEquals("token", card.layout)
    }

    @Test
    fun testMultiFacedLayoutsAreNotTreatedAsJunk() = runTest {
        for (layout in listOf("transform", "modal_dfc", "split", "flip", "adventure", "saga")) {
            val fakeApi = FakeScryfallApiService(listOf(layout))
            val repository = CardRepository(fakeApi)
            repository.getRandomCard()
            assertTrue("$layout should not be re-rolled", fakeApi.callCount == 1)
        }
    }
}
