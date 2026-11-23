package com.example.mobiledigger.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FilenameCleanerTest {
    private fun c(s: String) = FilenameCleaner.clean(s)

    @Test fun underscoresAndTrackNumber() =
        assertEquals("Alonso - Enjoy Club Tyla Push 2 Start Alonso Edit", c("005_Alonso_-_Enjoy_Club_Tyla_Push_2_Start_Alonso_Edit_★_Played_by"))

    @Test fun websiteInBrackets() =
        assertEquals("Samson, Peace Control, Nick Tew - Can't trust (Original Mix)",
            c("Samson, Peace Control, Nick Tew - Can't trust (Original Mix) [www.dj-promo.org]"))

    @Test fun websiteAfterDash() =
        assertEquals("Exclusives Only Week 31 (2026)", c("Exclusives Only Week 31 (2026) - djsoundtop.com"))

    @Test fun duplicateMixTag() =
        assertEquals("AEREA - Self-destruction (Extended Mix)", c("AEREA - Self-destruction (Extended Mix) (Extended Mix)"))

    @Test fun keepsLabelAndMix() =
        assertEquals("Giom - Things U Do [Supremus Records]", c("Giom - Things U Do [Supremus Records]"))

    @Test fun numberedTrack() = assertEquals("Ame - Rej [Innervisions, 2006]", c("03. Ame - Rej [Innervisions, 2006]"))

    @Test fun herbert() = assertEquals("Herbert - It's Only (DJ Koze Mix)", c("24 - Herbert - It's Only (DJ Koze Mix)"))

    @Test fun keepsHyphenatedWords() = assertEquals("Jay-Z - Song", c("Jay-Z - Song"))

    @Test fun dashSpacing() = assertEquals("Artist - Title", c("Artist -Title"))

    @Test fun freeDownloadAndCopy() = assertEquals("Artist - Title (Edit)", c("Artist - Title (Edit) [FREE DOWNLOAD] (1)"))

    @Test fun dottedName() = assertEquals("Artist - Title", c("Artist.-.Title"))

    @Test fun neverEmpty() = assertEquals("www.site.com", c("www.site.com"))
}
