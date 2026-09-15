package dev.snapstonewielder.app.data.util

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-side mirror of the JVM `ManaCostFormatterTest`.
 *
 * ## Why this file exists
 *
 * The JVM suite is not enough. Android does not run `java.util.regex`'s own engine - it delegates
 * to **ICU**, which is stricter about pattern syntax. A pattern can compile happily on the desktop
 * JVM and be rejected outright on-device:
 *
 * ```
 * PatternSyntaxException: Syntax error in regexp pattern near index 11
 * \{([^{}]*)}          <- dangling `}` tolerated by java.util.regex, rejected by ICU
 * ```
 *
 * That exact divergence shipped: the pattern lived in an `object` initialiser, so `<clinit>` threw
 * and every single card render failed on a real device while the whole JVM suite stayed green.
 *
 * These tests are therefore intentionally duplicative of the JVM ones. Running the same assertions
 * through the ICU engine is the entire point - it is the only thing that would have caught it.
 * Keep both suites; this is additive, not a replacement.
 */
@RunWith(AndroidJUnit4::class)
class ManaCostFormatterTest {

    /**
     * The canary. Merely touching [ManaCostFormatter] runs its class initialiser, so if the pip
     * pattern is ever ICU-invalid again this fails immediately with the real
     * `ExceptionInInitializerError` cause attached, instead of the downstream
     * `NoClassDefFoundError` that every other test would report.
     */
    @Test
    fun testPipPatternCompilesUnderIcu() {
        assertEquals("2UU", ManaCostFormatter.normalizeManaCost("{2}{U}{U}"))
    }

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

    /**
     * Malformed braces must not throw on ICU either. Unbalanced and nested braces are the inputs
     * most likely to provoke an engine-level difference, and a real `mana_cost` is never trusted
     * input as far as the renderer is concerned.
     */
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
     * The regex-free fallback used when pattern compilation fails must be a genuine drop-in for
     * the regex path, otherwise the safety net silently changes what gets printed. Asserting they
     * agree on-device keeps the fallback honest.
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
}
