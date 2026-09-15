package dev.snapstonewielder.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardLayoutsTest {

    @Test
    fun testAllJunkLayoutsAreRejected() {
        val expected = listOf(
            "token",
            "double_faced_token",
            "art_series",
            "memorabilia",
            "emblem",
            "vanguard",
            "scheme",
            "planar"
        )
        assertEquals(expected.size.toLong(), CardLayouts.JUNK_LAYOUTS.size.toLong())
        for (layout in expected) {
            assertTrue("$layout should be junk", CardLayouts.isJunkLayout(layout))
            assertFalse("$layout should not be playable", CardLayouts.isPlayableLayout(layout))
        }
    }

    @Test
    fun testPlayableLayoutsAreAccepted() {
        val playable = listOf(
            "normal", "split", "flip", "transform", "modal_dfc", "meld",
            "leveler", "class", "saga", "adventure", "mutate", "battle", "prototype"
        )
        for (layout in playable) {
            assertFalse("$layout should not be junk", CardLayouts.isJunkLayout(layout))
            assertTrue("$layout should be playable", CardLayouts.isPlayableLayout(layout))
        }
    }

    @Test
    fun testNullOrBlankLayoutIsTreatedAsPlayable() {
        assertFalse(CardLayouts.isJunkLayout(null))
        assertFalse(CardLayouts.isJunkLayout(""))
        assertFalse(CardLayouts.isJunkLayout("   "))
    }

    @Test
    fun testMatchingIsCaseAndWhitespaceInsensitive() {
        assertTrue(CardLayouts.isJunkLayout("TOKEN"))
        assertTrue(CardLayouts.isJunkLayout("  Double_Faced_Token  "))
    }
}
