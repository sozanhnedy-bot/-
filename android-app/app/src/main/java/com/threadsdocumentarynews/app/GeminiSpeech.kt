package com.threadsdocumentarynews.app

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Gemini TTS with an optional, on-device Nabra fallback managed by LocalAiMedia. */
object GeminiSpeech {
    const val MODEL_NAME = "gemini-3.1-flash-tts-preview"
    private const val SAMPLE_RATE = 24_000

    @OptIn(PublicPreviewAPI::class)
    suspend fun generateWav(context: Context, text: String, voiceName: String): String =
        withContext(Dispatchers.IO) {
            if (!FirebaseAiSetup.initialize(context.applicationContext)) {
                throw IllegalStateException("Firebase غير مهيأ لهذا البناء؛ تعذر تشغيل صوت Gemini.")
            }
            val safeText = text.trim().take(3_500)
            if (safeText.isEmpty()) throw IllegalArgumentException("لا يوجد نص للراوي لإنشاء الصوت.")
            val generation = GenerationConfig.builder()
                .setResponseModalities(listOf(ResponseModality.AUDIO))
                .setSpeechConfig(SpeechConfig(Voice(voiceName), "ar-XA"))
                .build()
            val model = Firebase.ai(backend = GenerativeBackend.googleAI())
                .generativeModel(MODEL_NAME, generationConfig = generation)
            val response = model.generateContent("اقرأ النص العربي التالي كما هو دون إضافة أو حذف، بصوت وثائقي واضح وطبيعي: $safeText")
            val part = response.candidates
                .asSequence()
                .flatMap { it.content.parts.asSequence() }
                .filterIsInstance<InlineDataPart>()
                .firstOrNull()
                ?: throw IllegalStateException("لم يُرجع Gemini بيانات صوتية. قد تكون الحصة المجانية انتهت أو الخدمة غير متاحة.")
            val mime = part.mimeType.lowercase()
            if (!mime.contains("pcm") && !mime.contains("audio")) {
                throw IllegalStateException("صيغة صوت Gemini غير متوقعة: $mime")
            }
            if (part.inlineData.isEmpty()) throw IllegalStateException("أعاد Gemini ملفًا صوتيًا فارغًا.")
            val output = File(context.cacheDir, "gemini-voice-${System.currentTimeMillis()}.wav")
            output.writeBytes(pcmToWav(part.inlineData))
            output.absolutePath
        }

    @JvmStatic
    fun generateWavBlocking(context: Context, text: String, voiceName: String): String =
        runBlocking { generateWav(context, text, voiceName) }

    internal fun pcmToWav(pcm: ByteArray): ByteArray {
        require(pcm.size % 2 == 0) { "بيانات PCM غير مكتملة لعينة 16-bit." }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + pcm.size)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1)
        header.putShort(1)
        header.putInt(SAMPLE_RATE)
        header.putInt(SAMPLE_RATE * 2)
        header.putShort(2)
        header.putShort(16)
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(pcm.size)
        return header.array() + pcm
    }
}
