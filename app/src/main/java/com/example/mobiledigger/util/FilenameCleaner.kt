package com.example.mobiledigger.util

/**
 * Tidies a track file name (without extension) for the rename dialog:
 *  - underscores, %20 and dots used as spaces → spaces
 *  - website tags ("[www.dj-promo.org]", "- djsoundtop.com", "(beatport.com)") removed
 *  - leading track numbers ("005_", "03. ", "24 - ", "A1 ") removed
 *  - promo junk ("Free Download", "[FREE DL]", "320kbps", "Played by", "(1)" copy suffix) removed
 *  - the same bracketed tag twice in a row ("(Extended Mix) (Extended Mix)") kept once
 *  - stray symbols (★ ✳ * ~ |) removed, "Artist-Title" style dashes spaced, spaces collapsed
 * Mix names and label tags like "(Original Mix)" / "[Toolroom]" are kept: DJs rely on them.
 * Pure Kotlin, unit-tested.
 */
object FilenameCleaner {

    private val SITE = Regex("""(?i)\b(?:https?://)?(?:www\.)?[a-z0-9][a-z0-9-]*\.(?:com|org|net|ru|info|biz|to|cc|me|io|co|fm|xyz|top|club|eu|ro|de|uk|pl)\b(?:/\S*)?""")
    private val JUNK = listOf(
        """free\s*download""", """free\s*dl""", """\bfree\b""", """out\s*now""", """premiere""",
        """\b(?:320|256|192|128)\s*kbps\b""", """\b320\b""", """\bhq\b""", """played\s*by""", """\bpromo\b"""
    ).map { Regex("(?i)$it") }

    fun clean(input: String): String {
        var s = input.trim()

        // 1) separators used instead of spaces
        s = s.replace("%20", " ").replace('_', ' ')
        // dots between words ("Artist.Name.-.Title") — only when the name has no spaces at all
        if (!s.contains(' ') && s.count { it == '.' } >= 2 && !SITE.matches(s)) s = s.replace('.', ' ')

        // 2) bracketed groups that are only a website or promo junk → drop the whole group
        s = Regex("""[\[(\{]([^\])\}]*)[\])\}]""").replace(s) { m ->
            val inner = m.groupValues[1]
            val stripped = JUNK.fold(SITE.replace(inner, "")) { acc, r -> r.replace(acc, "") }
            if (stripped.isBlank() || stripped.trim().matches(Regex("""[\s\-–—.,:;|]*"""))) "" else m.value
        }
        // websites and promo words outside brackets
        s = SITE.replace(s, " ")
        JUNK.forEach { s = it.replace(s, " ") }

        // 3) copy suffixes "(1)", " - Copy"
        s = s.replace(Regex("""\s*\(\d{1,2}\)\s*$"""), "").replace(Regex("""(?i)\s*-\s*copy\s*$"""), "")

        // 4) leading track numbers: "005 ", "03. ", "24 - ", "1-", "A1 " (vinyl sides)
        s = s.trim().replace(Regex("""^(?:[A-D]?\d{1,3})(?:\s*[.\-–)]\s*|\s+)(?=\S)"""), "")

        // 5) stray symbols
        s = s.replace(Regex("""[★☆✳✴✱✲*~|•·#]+"""), " ")

        // 6) the same bracketed tag repeated back to back → once
        s = Regex("""(\([^)]*\)|\[[^\]]*\])(\s*\1)+""", RegexOption.IGNORE_CASE).replace(s) { it.groupValues[1] }

        // 7) dashes: "Artist-Title" / "Artist -Title" → "Artist - Title" (not inside words like "Jay-Z")
        s = s.replace(Regex("""\s+[-–—]+\s*|\s*[-–—]+\s+"""), " - ")
        s = s.replace(Regex("""(\s-\s){2,}"""), " - ")

        // 8) tidy brackets and spaces
        s = s.replace(Regex("""\(\s+"""), "(").replace(Regex("""\s+\)"""), ")")
            .replace(Regex("""\[\s+"""), "[").replace(Regex("""\s+]"""), "]")
            .replace(Regex("""\(\s*\)|\[\s*]"""), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
            .trim('-', '–', '—', '.', ',', ' ')
        return s.ifBlank { input.trim() }
    }
}
