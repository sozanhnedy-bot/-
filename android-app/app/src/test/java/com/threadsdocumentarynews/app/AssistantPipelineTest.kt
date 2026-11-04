package com.threadsdocumentarynews.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantPipelineTest {
    @Test
    fun extractsFullNarrationWithoutSceneDirections() {
        val script = """
            عنوان الفيديو: قصة النهر
            الفكرة: رحلة قصيرة
            التعليق الصوتي الكامل:
            بدأ النهر رحلته من الجبال، ثم عبر السهول ووصل إلى البحر.
            تقسيم المشاهد:
            مشهد 1 — 00:00 إلى 00:08
            الوصف البصري: لقطة جوية لجبال بعيدة.
            نص المشهد: بدأ النهر رحلته من الجبال.
            ملاحظات التحقق: راجع المصادر قبل النشر.
        """.trimIndent()
        assertEquals("بدأ النهر رحلته من الجبال، ثم عبر السهول ووصل إلى البحر.", NarrationParser.extract(script))
    }

    @Test
    fun combinesNarratorLinesAndSkipsVisualPrompts() {
        val script = """
            مشهد 1 — 00:00
            الوصف البصري: لقطة للبحر عند الشروق.
            نص الراوي: يبدأ المشهد على الساحل.
            مشهد 2 — 00:10
            الوصف البصري: لقطة قريبة للأمواج.
            نص التعليق: ترتفع الأمواج مع هبوب الرياح.
        """.trimIndent()
        assertEquals("يبدأ المشهد على الساحل.\nترتفع الأمواج مع هبوب الرياح.", NarrationParser.extract(script))
    }

    @Test
    fun returnsPlainNarrationWhenNoStoryboardMarkersExist() {
        assertEquals("هذا نص قصير للراوي.", NarrationParser.extract("هذا نص قصير للراوي."))
    }

    @Test
    fun doesNotReadStoryboardWhenNarrationIsMissing() {
        val script = "مشهد 1\nالوصف البصري: لقطة للمدينة."
        assertTrue(NarrationParser.extract(script).isEmpty())
    }

    @Test
    fun mapsThirtySecondsToShortPlanNotThreeMinutes() {
        assertEquals("4 مشاهد", ScriptPlans.forDuration("30 ثانية").sceneCount)
        assertEquals(600, ScriptPlans.forDuration("30 ثانية").maxTokens)
    }

    @Test
    fun mapsSixtySecondsAndThreeMinutesToLongerPlans() {
        assertEquals("6 مشاهد", ScriptPlans.forDuration("60 ثانية").sceneCount)
        assertEquals(850, ScriptPlans.forDuration("60 ثانية").maxTokens)
        assertEquals("8 مشاهد", ScriptPlans.forDuration("3 دقائق").sceneCount)
        assertEquals(1200, ScriptPlans.forDuration("3 دقائق").maxTokens)
    }
}
