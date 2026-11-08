package com.threadsdocumentarynews.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiDirectXClientTest {
    @Test
    fun parsesEgyptTrendsAndRestrictsResultLinksToXSearch() {
        val items = ApiDirectXClient.parseEgyptTrendsJson(
            """{"location":"Egypt","as_of":"2026-11-08T00:00:00Z","trends":[{"name":"#Egypt","query":"%23Egypt","tweet_volume":42,"url":"http://twitter.com/search?q=%23Egypt"},{"name":"موضوع عام","query":"topic","tweet_volume":null,"url":"https://attacker.example/search?q=topic"}]}"""
        )

        assertEquals(2, items.size)
        assertEquals("#Egypt", items[0].name)
        assertEquals(42L, items[0].analysisData.count)
        assertTrue(items[0].sourceUrl.startsWith("https://twitter.com/search"))
        assertEquals("https://x.com/search?q=topic", items[1].sourceUrl)
        assertTrue(items[0].detail.contains("Egypt"))
    }

    @Test
    fun aggregatesHashtagsInMemoryAndDoesNotReturnPostTextOrAuthors() {
        val items = ApiDirectXClient.parseHashtagSearchJson(
            """{"posts":[{"title":"#Egypt is active #AI","snippet":"private snippet #AI","author":"private-author","url":"https://x.com/status/123"},{"title":"#Egypt update","snippet":"internal note #news","author":"another-user"}]}""",
            "Egypt",
            "now"
        )

        val byName = items.associateBy { it.name }
        assertEquals(2L, byName["#AI"]?.analysisData?.count)
        assertEquals(2L, byName["#Egypt"]?.analysisData?.count)
        assertTrue(items.all { it.sourceUrl.startsWith("https://x.com/search?") })
        assertFalse(items.any { it.detail.contains("private snippet") })
        assertFalse(items.any { it.detail.contains("private-author") })
        assertFalse(items.any { it.sourceUrl.contains("status/123") })
        assertTrue(items.all { it.analysisData.metricLabel.contains("عينة") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMultipleHashtagsForUndocumentedOrSyntax() {
        ApiDirectXClient.searchHashtags("not-used-in-this-test", listOf("AI", "Egypt"))
    }
}
