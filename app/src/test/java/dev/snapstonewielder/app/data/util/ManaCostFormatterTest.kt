package dev.snapstonewielder.app.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManaCostFormatterTest {

    @Test
    fun testGenericAndColoredPips() {
        assertEquals("2UU", ManaCostFormatter.normalizeManaCost("{2}{U}{U}"))
        assertEquals("4GG", ManaCostFormatter.normalizeManaCost("{4}{G}{G}"))
        assertEquals("0", ManaCostFormatter.normalizeManaCost("{0}"))
        assertEquals("W", ManaCostFormatter.normalizeManaCost("{W}"))
    }

    @Test
    fun testHybridPips() {
        assertEquals("W/U", ManaCostFormatter.normalizeManaCost("{W/U}"))
        assertEquals("2/W", ManaCostFormatter.normalizeManaCost("{2/W}"))
        assertEquals("B/GB/G", ManaCostFormatter.normalizeManaCost("{B/G}{B/G}"))
    }

    @Test
    fun testPhyrexianPips() {
        assertEquals("U/P", ManaCostFormatter.normalizeManaCost("{U/P}"))
        assertEquals("1U/P", ManaCostFormatter.normalizeManaCost("{1}{U/P}"))
    }

    @Test
    fun testVariableAndSymbolPips() {
        assertEquals("X", ManaCostFormatter.normalizeManaCost("{X}"))
        assertEquals("XXR", ManaCostFormatter.normalizeManaCost("{X}{X}{R}"))
        assertEquals("C", ManaCostFormatter.normalizeManaCost("{C}"))
        assertEquals("T", ManaCostFormatter.normalizeManaCost("{T}"))
    }

    @Test
    fun testSplitCardSeparatorIsPreserved() {
        assertEquals("1G // 3R", ManaCostFormatter.normalizeManaCost("{1}{G} // {3}{R}"))
    }

    @Test
    fun testEmptyAndNullInputs() {
        assertEquals("", ManaCostFormatter.normalizeManaCost(null))
        assertEquals("", ManaCostFormatter.normalizeManaCost(""))
        assertEquals("", ManaCostFormatter.normalizeManaCost("   "))
    }

    @Test
    fun testUnbracedInputIsPassedThrough() {
        assertEquals("2UU", ManaCostFormatter.normalizeManaCost("2UU"))
    }

    @Test
    fun testTopLevelAliasMatchesObject() {
        assertEquals("2UU", normalizeManaCost("{2}{U}{U}"))
    }

    @Test
    fun testMalformedBracesDegradeQuietly() {
        assertEquals("{2", ManaCostFormatter.normalizeManaCost("{2"))
        assertEquals("2}", ManaCostFormatter.normalizeManaCost("2}"))
        // Only the INNER `{U}` is a valid pip - `[^{}]*` forbids a nested brace - so the match
        // starts at index 1 and the stray leading `{` survives verbatim.
        assertEquals("{U", ManaCostFormatter.normalizeManaCost("{{U}"))
        assertEquals("}{", ManaCostFormatter.normalizeManaCost("}{"))
        assertEquals("", ManaCostFormatter.normalizeManaCost("{}"))
    }

    /**
     * The regex-free fallback only runs if pattern compilation ever fails again, so nothing else
     * exercises it. Pinning it against the regex path keeps the safety net from silently drifting
     * into printing something different from the real implementation.
     */
    @Test
    fun testManualFallbackMatchesRegexPath() {
        val inputs = listOf(
            "{2}{U}{U}", "{4}{G}{G}", "{0}", "{W}", "{W/U}", "{2/W}", "{B/G}{B/G}",
            "{U/P}", "{1}{U/P}", "{X}", "{X}{X}{R}", "{C}", "{T}",
            "{1}{G} // {3}{R}", "2UU", "{2", "2}", "{{U}", "}{", "{}"
        )
        for (input in inputs) {
            assertEquals(
                "regex and manual fallback must agree for \"$input\"",
                ManaCostFormatter.normalizeManaCost(input),
                ManaCostFormatter.stripBracesManually(input).trim()
            )
        }
    }

    /**
     * Documents the exact divergence that caused the outage. `java.util.regex` on the JVM tolerates
     * a dangling `}`; Android's ICU engine rejects it. This test therefore CANNOT fail here - it
     * exists to record that fact and to point at the androidTest suite, which is the only place the
     * real constraint can be enforced.
     */
    @Test
    fun testDanglingBraceIsToleratedOnJvmButNotOnDevice() {
        // Compiles fine on the JVM. On-device this same pattern throws PatternSyntaxException.
        val jvmOnlyPattern = runCatching { Regex("""\{([^{}]*)}""") }
        assertTrue(
            "java.util.regex is expected to tolerate the dangling brace - if this ever fails, " +
                "the JVM engine changed and the ICU gap may have closed",
            jvmOnlyPattern.isSuccess
        )
        // The shipped pattern must be the escaped one, which is valid on BOTH engines.
        assertEquals("2UU", ManaCostFormatter.normalizeManaCost("{2}{U}{U}"))
    }
}
