package com.threadsdocumentarynews.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiNewsAnalyzerTest {
    @Test
    fun promptContainsOnlyBoundedPublicHeadlineFieldsAndAnalysisLimits() {
        val prompt = GeminiNewsAnalyzer.buildPrompt(
            listOf(NewsHeadline("عنوان اقتصادي حديث", "مصدر عام", "اقتصاد", "2026-11-07")),
            "الاقتصاد"
        )

        assertTrue(prompt.contains("عنوان اقتصادي حديث"))
        assertTrue(prompt.contains("مصدر عام"))
        assertTrue(prompt.contains("الاقتصاد"))
        assertTrue(prompt.contains("لا تثبت ترندًا رسميًا"))
        assertTrue(prompt.contains("ليست قياسًا مباشرًا لترندات منصات التواصل"))
        assertTrue(prompt.contains("لا تضف أحداثًا أو أرقامًا"))
    }

    @Test
    fun promptLimitsHeadlineCountAndTruncatesLongUntrustedText() {
        val titles = (1..(GeminiNewsAnalyzer.MAX_HEADLINES + 1)).map { number ->
            NewsHeadline("عنوان رقم $number " + "س".repeat(400), "مصدر\nمحرف", "عام", "اليوم")
        }
        val prompt = GeminiNewsAnalyzer.buildPrompt(titles, "موضوع\nيتم تنظيفه")

        assertTrue(prompt.contains("عناوين العينة (40)"))
        assertTrue(prompt.contains("عنوان رقم 40"))
        assertFalse(prompt.contains("عنوان رقم 41"))
        assertFalse(prompt.contains("مصدر\nمحرف"))
        assertFalse(prompt.contains("موضوع\nيتم تنظيفه"))
    }
}
