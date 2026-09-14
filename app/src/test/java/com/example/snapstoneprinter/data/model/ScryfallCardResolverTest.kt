package com.example.snapstoneprinter.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the double-faced card bug: transform / modal_dfc / split / flip layouts
 * return null top-level `image_uris` and `oracle_text`, with the real data in `card_faces[]`.
 */
class ScryfallCardResolverTest {

    private val transformCard = ScryfallCard(
        name = "Delver of Secrets // Insectile Aberration",
        mana_cost = null,
        type_line = null,
        oracle_text = null,
        power = null,
        toughness = null,
        image_uris = null,
        layout = "transform",
        card_faces = listOf(
            CardFace(
                name = "Delver of Secrets",
                mana_cost = "{U}",
                type_line = "Creature — Human Wizard",
                oracle_text = "At the beginning of your upkeep, look at the top card of your library.",
                power = "1",
                toughness = "1",
                image_uris = ImageUris(
                    artCrop = "https://img/front-art.jpg",
                    normal = "https://img/front-normal.jpg"
                )
            ),
            CardFace(
                name = "Insectile Aberration",
                mana_cost = "",
                type_line = "Creature — Human Insect",
                oracle_text = "Flying",
                power = "3",
                toughness = "2",
                image_uris = ImageUris(artCrop = "https://img/back-art.jpg")
            )
        )
    )

    private val singleFacedCard = ScryfallCard(
        name = "Colossal Dreadmaw",
        mana_cost = "{4}{G}{G}",
        type_line = "Creature — Dinosaur",
        oracle_text = "Trample",
        power = "6",
        toughness = "6",
        image_uris = ImageUris(artCrop = "https://img/dreadmaw-art.jpg", normal = "https://img/dreadmaw.jpg"),
        layout = "normal"
    )

    @Test
    fun testTransformCard_fallsBackToFirstFace() {
        assertEquals("https://img/front-art.jpg", transformCard.effectiveArtCropUrl)
        assertEquals("https://img/front-normal.jpg", transformCard.effectiveNormalUrl)
        assertEquals(
            "At the beginning of your upkeep, look at the top card of your library.",
            transformCard.effectiveOracleText
        )
        assertEquals("{U}", transformCard.effectiveManaCost)
        assertEquals("Creature — Human Wizard", transformCard.effectiveTypeLine)
        assertEquals("1", transformCard.effectivePower)
        assertEquals("1", transformCard.effectiveToughness)
    }

    @Test
    fun testTransformCard_keepsTopLevelCombinedName() {
        // Scryfall always supplies a combined top-level name; prefer it over the face name.
        assertEquals("Delver of Secrets // Insectile Aberration", transformCard.effectiveName)
        assertTrue(transformCard.isMultiFaced)
    }

    @Test
    fun testTransformCard_effectiveImageUrlPrefersArtCrop() {
        assertEquals("https://img/front-art.jpg", transformCard.effectiveImageUrl)
    }

    @Test
    fun testSingleFacedCard_usesTopLevelFields() {
        assertEquals("Colossal Dreadmaw", singleFacedCard.effectiveName)
        assertEquals("{4}{G}{G}", singleFacedCard.effectiveManaCost)
        assertEquals("Creature — Dinosaur", singleFacedCard.effectiveTypeLine)
        assertEquals("Trample", singleFacedCard.effectiveOracleText)
        assertEquals("6", singleFacedCard.effectivePower)
        assertEquals("6", singleFacedCard.effectiveToughness)
        assertEquals("https://img/dreadmaw-art.jpg", singleFacedCard.effectiveArtCropUrl)
        assertFalse(singleFacedCard.isMultiFaced)
    }

    @Test
    fun testBlankTopLevelFieldsAreTreatedAsAbsent() {
        val card = singleFacedCard.copy(
            oracle_text = "   ",
            card_faces = listOf(CardFace(oracle_text = "Flying"))
        )
        assertEquals("Flying", card.effectiveOracleText)
    }

    @Test
    fun testNoFacesAndNoTopLevelData_resolvesToNull() {
        val card = ScryfallCard(name = "Nameless", layout = "normal")
        assertNull(card.effectiveArtCropUrl)
        assertNull(card.effectiveNormalUrl)
        assertNull(card.effectiveImageUrl)
        assertNull(card.effectiveOracleText)
        assertNull(card.effectiveManaCost)
        assertNull(card.effectivePower)
        assertEquals("Nameless", card.effectiveName)
    }

    @Test
    fun testNormalUrlFallsBackToLarge() {
        val card = ScryfallCard(
            name = "Large Only",
            image_uris = ImageUris(large = "https://img/large.jpg")
        )
        assertEquals("https://img/large.jpg", card.effectiveNormalUrl)
        assertEquals("https://img/large.jpg", card.effectiveImageUrl)
    }
}
