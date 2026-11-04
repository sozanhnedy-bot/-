package com.threadsdocumentarynews.app

internal data class ScriptPlan(
    val sceneCount: String,
    val wordTarget: String,
    val maxTokens: Int
)

internal object ScriptPlans {
    fun forDuration(duration: String): ScriptPlan = when {
        duration.contains("3 دقائق") -> ScriptPlan("8 مشاهد", "نحو 300 إلى 380 كلمة للراوي", 1200)
        duration.contains("60") -> ScriptPlan("6 مشاهد", "نحو 110 إلى 140 كلمة للراوي", 850)
        else -> ScriptPlan("4 مشاهد", "نحو 50 إلى 75 كلمة للراوي", 600)
    }
}
