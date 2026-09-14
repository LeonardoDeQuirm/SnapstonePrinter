package com.example.snapstoneprinter.data.util

/**
 * Pure, side-effect-free helpers for turning Scryfall's brace-delimited mana notation into plain
 * readable text suitable for a monochrome thermal printout.
 *
 * No mana pip symbols are ever drawn - this is deliberately plain text only.
 */
object ManaCostFormatter {

    /**
     * Matches one `{...}` pip and captures its contents.
     *
     * The trailing `\}` MUST stay escaped.
     *
     * A dangling, unescaped `}` is a **JVM-only** courtesy: `java.util.regex` on the desktop JVM
     * silently treats it as a literal, so `"""\{([^{}]*)}"""` compiles and every JVM unit test
     * passes. Android does not use `java.util.regex`'s own engine - it delegates to **ICU**, which
     * is stricter and rejects the dangling brace outright:
     *
     * ```
     * java.util.regex.PatternSyntaxException: Syntax error in regexp pattern near index 11
     * \{([^{}]*)}
     *     at com.android.icu.util.regex.PatternNative.compileImpl(Native Method)
     * ```
     *
     * Because this is a top-level `val` on an `object`, that threw from `<clinit>` and made EVERY
     * card render fail on-device regardless of input. Do not "simplify" the escape away.
     */
    private const val PIP_PATTERN = """\{([^{}]*)\}"""

    /**
     * Compiled defensively.
     *
     * An exception escaping `<clinit>` is catastrophic and near-undebuggable: the first caller sees
     * `ExceptionInInitializerError`, and from then on the class is permanently marked erroneous so
     * every subsequent touch throws a `NoClassDefFoundError` that no longer mentions the real
     * cause. Worse, both are `Error`s rather than `Exception`s, so ordinary `catch (e: Exception)`
     * handlers upstream do not stop them.
     *
     * So we never let pattern compilation escape the initialiser. If it ever fails again on some
     * future engine, this is null and [normalizeManaCost] transparently uses [stripBracesManually],
     * which produces identical output with no regex engine involved at all.
     */
    private val PIP_REGEX: Regex? = runCatching { Regex(PIP_PATTERN) }.getOrNull()

    /**
     * Strips the braces from a Scryfall `mana_cost` string.
     *
     * ```
     * "{2}{U}{U}"      -> "2UU"
     * "{W/U}"          -> "W/U"
     * "{2/W}"          -> "2/W"
     * "{U/P}"          -> "U/P"
     * "{X}"            -> "X"
     * "{1}{G} // {3}{R}" -> "1G // 3R"
     * null / ""        -> ""
     * ```
     *
     * Text outside the braces (notably the ` // ` separator on split cards) is preserved verbatim.
     *
     * Never throws: any engine-level failure degrades to the brace-free fallback scanner.
     */
    fun normalizeManaCost(raw: String?): String {
        if (raw.isNullOrBlank()) return ""

        val regex = PIP_REGEX
        if (regex != null) {
            val replaced = runCatching {
                regex.replace(raw) { match -> match.groupValues[1].trim() }
            }.getOrNull()
            if (replaced != null) return replaced.trim()
        }

        return stripBracesManually(raw).trim()
    }

    /**
     * Regex-free equivalent of replacing [PIP_PATTERN] with its trimmed capture group.
     *
     * Faithfully reproduces the pattern's semantics, including the `[^{}]*` restriction: a `{` is
     * only treated as the start of a pip when the next brace-ish character is its own closing `}`.
     * An unmatched or nested `{` is emitted verbatim, exactly as the regex would leave it.
     *
     * Internal rather than private so the test suites can assert both paths agree.
     */
    internal fun stripBracesManually(raw: String): String {
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '{') {
                val close = raw.indexOf('}', i + 1)
                val nextOpen = raw.indexOf('{', i + 1)
                val wellFormed = close > i && (nextOpen == -1 || nextOpen > close)
                if (wellFormed) {
                    out.append(raw.substring(i + 1, close).trim())
                    i = close + 1
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }
}

/** Convenience top-level alias for [ManaCostFormatter.normalizeManaCost]. */
fun normalizeManaCost(raw: String?): String = ManaCostFormatter.normalizeManaCost(raw)
