package com.threadsdocumentarynews.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrendScorerTest {
    @Test
    fun normalizesArabicAlefAndDiacriticsAndCountsAtMostOncePerTitle() {
        val results = TrendScorer.score(
            listOf(
                "إِيران تبحث ملف الطاقة في اجتماع إِيران",
                "ايران تعلن تطورات جديدة"
            )
        )

        val iran = results.single { it.label == "ايران" }
        assertEquals(2, iran.count)
        assertEquals("ايران تعلن تطورات جديدة", iran.example)
    }

    @Test
    fun removesArabicPunctuationAndStopWords() {
        val results = TrendScorer.score(listOf("الاقتصاد، السياسة: تحولات جديدة في المنطقة"))
        val labels = results.map { it.label }

        assertTrue("الاقتصاد" in labels)
        assertTrue("السياسة" in labels)
        assertTrue("تحولات" in labels)
        assertTrue("المنطقة" in labels)
        assertTrue("في" !in labels)
    }

    @Test
    fun sortsByFrequencyThenLabelAndLimitsResults() {
        val results = TrendScorer.score(
            listOf("الأردن ملفات في المنطقة", "الأردن ملفات تخص المنطقة", "الأردن أخبار مهمة"),
            limit = 3
        )

        assertEquals(listOf("الاردن", "المنطقة", "ملفات"), results.map { it.label })
        assertEquals(listOf(3, 2, 2), results.map { it.count })
    }

    @Test
    fun ignoresShortTitlesAndReturnsEmptyForNonPositiveLimit() {
        assertTrue(TrendScorer.score(listOf("خبر")).isEmpty())
        assertTrue(TrendScorer.score(listOf("عنوان خبري طويل ومفيد"), limit = 0).isEmpty())
    }

    @Test
    fun outputIsDeterministicAndKeepsTheShortestExampleTitle() {
        val titles = listOf("تطورات مهمة تخص المغرب اليوم", "المغرب يعلن عن تطورات جديدة")
        val first = TrendScorer.score(titles)
        val second = TrendScorer.score(titles)

        assertEquals(first, second)
        assertEquals("المغرب يعلن عن تطورات جديدة", first.single { it.label == "المغرب" }.example)
    }
}
