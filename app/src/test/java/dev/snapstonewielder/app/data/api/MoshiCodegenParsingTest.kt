package dev.snapstonewielder.app.data.api

import dev.snapstonewielder.app.data.model.ScryfallCard
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the removal of Moshi's reflective `KotlinJsonAdapterFactory`.
 *
 * That factory was redundant - every model is `@JsonClass(generateAdapter = true)` and KSP codegen
 * is active - but removing it is exactly the kind of change that fails SILENTLY: a bare
 * `Moshi.Builder().build()` with no generated adapter on the classpath does not refuse to compile,
 * it throws at the first parse, deep inside a coroutine, and the app just shows "Unknown error".
 *
 * These tests build Moshi the same way [RetrofitClient] does and parse real Scryfall payload
 * shapes, so a missing or unregistered codegen adapter fails here instead of on a device.
 */
class MoshiCodegenParsingTest {

    /** Built EXACTLY like the production stack: codegen adapters only, no reflective factory. */
    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(ScryfallCard::class.java)

    @Test
    fun parsesNormalCard_withSnakeCaseFields() {
        val card = adapter.fromJson(NORMAL_CARD_JSON)

        assertNotNull(card)
        requireNotNull(card)
        assertEquals("Colossal Dreadmaw", card.name)
        // @Json(name = "mana_cost") must still be honoured by the generated adapter.
        assertEquals("{4}{G}{G}", card.mana_cost)
        assertEquals("Creature — Dinosaur", card.type_line)
        assertEquals("Trample", card.oracle_text)
        assertEquals("6", card.power)
        assertEquals("6", card.toughness)
        assertEquals("normal", card.layout)
        assertEquals(
            "https://cards.scryfall.io/art_crop/front/a/b/dreadmaw.jpg",
            card.image_uris?.artCrop
        )
        assertEquals(
            "https://cards.scryfall.io/normal/front/a/b/dreadmaw.jpg",
            card.image_uris?.normal
        )
    }

    @Test
    fun parsesDoubleFacedCard_withNestedCardFaces() {
        val card = adapter.fromJson(TRANSFORM_CARD_JSON)

        assertNotNull(card)
        requireNotNull(card)
        assertEquals("transform", card.layout)
        // Nested @JsonClass types need their own generated adapters; this is where a half-applied
        // codegen setup falls over.
        assertEquals(2, card.card_faces?.size)
        assertEquals("Delver of Secrets", card.card_faces?.get(0)?.name)
        assertEquals("Insectile Aberration", card.card_faces?.get(1)?.name)
        assertEquals(
            "https://cards.scryfall.io/art_crop/front/d/e/delver.jpg",
            card.card_faces?.get(0)?.image_uris?.artCrop
        )
        assertEquals(
            "https://cards.scryfall.io/art_crop/back/d/e/delver.jpg",
            card.card_faces?.get(1)?.image_uris?.artCrop
        )
        // Top-level fields are genuinely absent on a transform card.
        assertNull(card.image_uris)
        assertNull(card.oracle_text)
        // ... and the effective* resolvers must paper over that.
        assertEquals(
            "https://cards.scryfall.io/art_crop/front/d/e/delver.jpg",
            card.effectiveImageUrl
        )
    }

    @Test
    fun ignoresUnknownFields() {
        // Scryfall sends ~60 fields; the model declares nine. Unknown keys must not throw.
        val card = adapter.fromJson(NORMAL_CARD_JSON_WITH_EXTRAS)
        assertNotNull(card)
        assertEquals("Colossal Dreadmaw", card?.name)
    }

    @Test
    fun absentOptionalFieldsBecomeNull() {
        val card = adapter.fromJson("""{"name":"Nameless Land"}""")
        requireNotNull(card)
        assertEquals("Nameless Land", card.name)
        assertNull(card.mana_cost)
        assertNull(card.card_faces)
        assertNull(card.image_uris)
        assertTrue(card.effectiveName.isNotEmpty())
    }

    private companion object {
        const val NORMAL_CARD_JSON = """
            {
              "name": "Colossal Dreadmaw",
              "mana_cost": "{4}{G}{G}",
              "type_line": "Creature — Dinosaur",
              "oracle_text": "Trample",
              "power": "6",
              "toughness": "6",
              "layout": "normal",
              "image_uris": {
                "art_crop": "https://cards.scryfall.io/art_crop/front/a/b/dreadmaw.jpg",
                "normal": "https://cards.scryfall.io/normal/front/a/b/dreadmaw.jpg",
                "large": "https://cards.scryfall.io/large/front/a/b/dreadmaw.jpg",
                "png": "https://cards.scryfall.io/png/front/a/b/dreadmaw.png"
              }
            }
        """

        const val NORMAL_CARD_JSON_WITH_EXTRAS = """
            {
              "object": "card",
              "id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
              "oracle_id": "ffffffff-1111-2222-3333-444444444444",
              "multiverse_ids": [1, 2, 3],
              "name": "Colossal Dreadmaw",
              "lang": "en",
              "released_at": "2017-09-29",
              "legalities": { "standard": "not_legal", "commander": "legal" },
              "prices": { "usd": "0.05", "eur": null },
              "layout": "normal"
            }
        """

        const val TRANSFORM_CARD_JSON = """
            {
              "name": "Delver of Secrets // Insectile Aberration",
              "layout": "transform",
              "card_faces": [
                {
                  "name": "Delver of Secrets",
                  "mana_cost": "{U}",
                  "type_line": "Creature — Human Wizard",
                  "oracle_text": "At the beginning of your upkeep, look at the top card.",
                  "power": "1",
                  "toughness": "1",
                  "image_uris": {
                    "art_crop": "https://cards.scryfall.io/art_crop/front/d/e/delver.jpg",
                    "normal": "https://cards.scryfall.io/normal/front/d/e/delver.jpg"
                  }
                },
                {
                  "name": "Insectile Aberration",
                  "mana_cost": "",
                  "type_line": "Creature — Human Insect",
                  "oracle_text": "Flying",
                  "power": "3",
                  "toughness": "2",
                  "image_uris": {
                    "art_crop": "https://cards.scryfall.io/art_crop/back/d/e/delver.jpg",
                    "normal": "https://cards.scryfall.io/normal/back/d/e/delver.jpg"
                  }
                }
              ]
            }
        """
    }
}
