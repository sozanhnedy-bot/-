package com.threadsdocumentarynews.app

import java.util.Locale

/** Scores repeated words in public news titles; this is not an official social-network trend feed. */
object TrendScorer {
    data class Trend(val label: String, val count: Int, val example: String)

    private val stopWords = setOf(
        "من", "في", "على", "الى", "عن", "مع", "هذا", "هذه", "ذلك", "التي", "الذي",
        "بعد", "قبل", "بين", "امام", "خلال", "حول", "اخبار", "خبر", "اليوم", "غدا",
        "قال", "وقال", "the", "and", "for", "with"
    )
    private val punctuation = Regex("[\\p{P}\\p{S}]")
    private val diacritics = Regex("[\\u064B-\\u065F\\u0670]")

    @JvmStatic
    fun score(titles: List<String>, limit: Int = 12): List<Trend> {
        if (limit <= 0) return emptyList()
        val counts = linkedMapOf<String, Int>()
        val examples = mutableMapOf<String, String>()

        titles.forEach { rawTitle ->
            val title = rawTitle.trim()
            if (title.length < 8) return@forEach
            val tokens = normalize(title)
                .replace(punctuation, " ")
                .split(Regex("\\s+"))
                .filter { it.length >= 4 && it !in stopWords }
                .toSet()
            tokens.forEach { token ->
                counts[token] = (counts[token] ?: 0) + 1
                val current = examples[token]
                if (current == null || title.length < current.length) examples[token] = title
            }
        }

        return counts.entries
            .map { (label, count) -> Trend(label, count, examples[label].orEmpty()) }
            .sortedWith(compareByDescending<Trend> { it.count }.thenBy { it.label })
            .take(limit)
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(diacritics, "")
        .replace(Regex("[أإآ]"), "ا")
        .replace('ى', 'ي')
}
