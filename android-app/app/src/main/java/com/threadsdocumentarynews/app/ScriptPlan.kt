package com.threadsdocumentarynews.app

internal data class ScriptPlan(
    val sceneCount: String,
    val wordTarget: String,
    val maxTokens: Int
)

internal object ScriptPlans {
    fun forDuration(duration: String): ScriptPlan = when {
        duration.contains("20 دقيقة") -> ScriptPlan("24 مشهدًا", "نحو 2,200 إلى 2,700 كلمة للراوي", 3600)
        duration.contains("6 دقائق") -> ScriptPlan("12 مشهدًا", "نحو 600 إلى 760 كلمة للراوي", 2200)
        duration.contains("4 دقائق") -> ScriptPlan("8 مشاهد", "نحو 380 إلى 500 كلمة للراوي", 1500)
        duration.contains("3 دقائق") -> ScriptPlan("6 مشاهد", "نحو 260 إلى 340 كلمة للراوي", 1200)
        duration.contains("60") -> ScriptPlan("6 مشاهد", "نحو 110 إلى 140 كلمة للراوي", 850)
        duration.contains("20 ثانية") -> ScriptPlan("4 مشاهد", "نحو 45 إلى 60 كلمة للراوي", 650)
        duration.contains("15 ثانية") -> ScriptPlan("3 مشاهد", "نحو 32 إلى 45 كلمة للراوي", 550)
        else -> ScriptPlan("5 مشاهد", "نحو 50 إلى 75 كلمة للراوي", 700)
    }
}
