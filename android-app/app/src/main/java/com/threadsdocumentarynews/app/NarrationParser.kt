package com.threadsdocumentarynews.app

/** Parses the voiceover portion of a generated storyboard without reading headings or visual prompts. */
internal object NarrationParser {
    private val fullMarkers = listOf("التعليق الصوتي الكامل", "نص الراوي الكامل", "النص الكامل للراوي")
    private val sectionEnds = listOf("تقسيم المشاهد", "ملاحظات التحقق", "ملاحظات للتحقق")
    private val speechMarkers = listOf("نص الراوي", "نص التعليق", "نص المشهد")
    private val sectionHeaders = listOf(
        "عنوان الفيديو", "الفكرة", "تقسيم المشاهد", "المشاهد", "مشهد", "الوصف البصري",
        "وصف بصري", "ملاحظات التحقق", "ملاحظات للتحقق", "المدة", "مدة المشهد"
    )

    @JvmStatic
    fun extract(script: String): String {
        val fullStart = fullMarkers.mapNotNull { marker ->
            val index = script.indexOf(marker)
            if (index >= 0) index to marker else null
        }.minByOrNull { it.first }

        if (fullStart != null) {
            var start = fullStart.first + fullStart.second.length
            while (start < script.length && (script[start] == ':' || script[start] == '：' || script[start].isWhitespace())) start++
            val end = sectionEnds.mapNotNull { marker ->
                val index = script.indexOf(marker, start)
                if (index >= 0) index else null
            }.minOrNull() ?: script.length
            val narration = script.substring(start, end).trim()
            if (narration.isNotEmpty()) return narration
        }

        val lines = script.split(Regex("\\R"))
        val extracted = mutableListOf<String>()
        var index = 0
        while (index < lines.size) {
            val line = lines[index].trim()
            val marker = speechMarkers.firstOrNull { line.startsWith(it) }
            if (marker == null) {
                index++
                continue
            }
            val colonIndex = line.indexOfAny(charArrayOf(':', '：'), marker.length)
            if (colonIndex < 0) {
                index++
                continue
            }
            val part = StringBuilder(line.substring(colonIndex + 1).trim())
            index++
            while (index < lines.size) {
                val next = lines[index].trim()
                val normalized = next.lowercase()
                if (next.isEmpty() || speechMarkers.any { next.startsWith(it) } || sectionHeaders.any { normalized.startsWith(it.lowercase()) }) break
                if (part.isNotEmpty()) part.append(' ')
                part.append(next)
                index++
            }
            if (part.isNotEmpty()) extracted += part.toString()
        }
        if (extracted.isNotEmpty()) return extracted.joinToString("\n").trim()

        val looksLikeStoryboard = lines.any { line ->
            val trimmed = line.trim()
            trimmed.startsWith("مشهد") || trimmed.startsWith("الوصف البصري") || trimmed.startsWith("وصف بصري")
        }
        return if (looksLikeStoryboard) "" else script.trim()
    }
}
