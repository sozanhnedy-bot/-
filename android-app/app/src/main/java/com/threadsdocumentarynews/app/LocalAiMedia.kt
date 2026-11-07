package com.threadsdocumentarynews.app

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.github.csukuangfj.piper.phonemize.piperPhonemizeInitialize
import com.github.csukuangfj.piper.phonemize.piperPhonemizeText
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Paid-API-free local media generation. Models are not bundled in the APK;
 * the owner explicitly downloads them once and can then work offline.
 */
object LocalAiMedia {
    private const val QWEN_SIZE = 491_400_032L
    private const val QWEN_SHA256 = "74a4da8c9fdbcd15bd1f6d01d621410d31c6fc00986f5eb687824e7b93d7a9db"
    private const val QWEN_URL = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/9217f5db79a29953eb74d5343926648285ec7e67/qwen2.5-0.5b-instruct-q4_k_m.gguf"
    private const val VOICE_SIZE = 82_810_819L
    private const val VOICE_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/nabra-82m-arabic-int8.tar.bz2"
    private const val QWEN_FILE = "qwen2.5-0.5b-instruct-q4_k_m.gguf"
    private const val VOICE_FOLDER = "nabra-82m-arabic-int8"
    @Volatile private var phonemizerInitialized = false
    private val phonemizerLock = Any()

    fun interface ProgressListener {
        fun onProgress(percent: Int)
    }

    @JvmStatic
    fun scriptModelFile(context: Context): File = File(File(context.filesDir, "models"), QWEN_FILE)

    @JvmStatic
    fun voiceReady(context: Context): Boolean {
        val dir = File(context.filesDir, "voice/$VOICE_FOLDER")
        return File(dir, "model.int8.onnx").length() > 1_000_000L &&
            File(dir, "voices.bin").isFile && File(dir, "tokens.txt").isFile &&
            File(dir, "espeak-ng-data").isDirectory
    }

    @JvmStatic
    fun downloadScriptModel(context: Context, listener: ProgressListener) {
        val target = scriptModelFile(context)
        if (target.isFile && target.length() == QWEN_SIZE && sha256(target) == QWEN_SHA256) {
            listener.onProgress(100)
            return
        }
        ensureSpace(context.filesDir, QWEN_SIZE + 64_000_000L)
        target.parentFile?.mkdirs()
        val partial = File(target.parentFile, "$QWEN_FILE.part")
        try {
            download(QWEN_URL, partial, QWEN_SIZE, listener)
            if (sha256(partial) != QWEN_SHA256) throw IllegalStateException("تعذر التحقق من سلامة ملف نموذج الكتابة.")
            if (target.exists()) target.delete()
            if (!partial.renameTo(target)) throw IllegalStateException("تعذر حفظ نموذج الكتابة داخل التطبيق.")
        } catch (e: Exception) {
            partial.delete()
            throw e
        }
    }

    @JvmStatic
    fun downloadVoiceModel(context: Context, listener: ProgressListener) {
        if (voiceReady(context)) {
            listener.onProgress(100)
            return
        }
        ensureSpace(context.filesDir, VOICE_SIZE * 2)
        val voiceRoot = File(context.filesDir, "voice")
        voiceRoot.mkdirs()
        val archive = File(context.cacheDir, "nabra-82m-arabic-int8.tar.bz2.part")
        try {
            download(VOICE_URL, archive, VOICE_SIZE, listener)
            val rootCanonical = voiceRoot.canonicalFile
            BufferedInputStream(FileInputStream(archive)).use { buffered ->
                BZip2CompressorInputStream(buffered).use { bz ->
                    TarArchiveInputStream(bz).use { tar ->
                        var entry = tar.nextTarEntry
                        while (entry != null) {
                            if (entry.isSymbolicLink || entry.isLink || entry.isSparse) {
                                throw IllegalStateException("ملف الصوت يحتوي على مسار غير آمن.")
                            }
                            val out = File(rootCanonical, entry.name).canonicalFile
                            if (!out.path.startsWith(rootCanonical.path + File.separator)) {
                                throw IllegalStateException("ملف الصوت يحتوي على مسار غير آمن.")
                            }
                            if (entry.isDirectory) {
                                out.mkdirs()
                            } else {
                                out.parentFile?.mkdirs()
                                FileOutputStream(out).use { output -> tar.copyTo(output) }
                            }
                            entry = tar.nextTarEntry
                        }
                    }
                }
            }
            if (!voiceReady(context)) throw IllegalStateException("اكتمل تنزيل الصوت لكن ملفات النموذج غير مكتملة.")
            listener.onProgress(100)
        } catch (e: Exception) {
            File(voiceRoot, VOICE_FOLDER).deleteRecursively()
            throw e
        } finally {
            archive.delete()
        }
    }

    @JvmStatic
    fun generateScript(context: Context, topic: String, duration: String, style: String, platform: String, references: String): String {
        val modelFile = scriptModelFile(context)
        if (!modelFile.isFile || modelFile.length() != QWEN_SIZE) {
            throw IllegalStateException("نزّل نموذج الكتابة المحلي أولًا.")
        }
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memory)
        if (memory.availMem < 850L * 1024L * 1024L) {
            throw IllegalStateException("المساحة الحرة في الذاكرة قليلة لتشغيل نموذج الكتابة. أغلق بعض التطبيقات وحاول مجددًا.")
        }
        val threadCount = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)
        val config = LlamaConfig(
            contextSize = 3072,
            threads = threadCount,
            gpuLayers = 0,
            temperature = 0.65f,
            topP = 0.9f,
            topK = 40,
            seed = -1,
        )
        return runBlocking {
            val model = Llama.loadModel(modelFile.absolutePath, config)
            try {
                val systemPrompt = """
                    أنت كاتب مساعد لإعداد مسودات فيديو وثائقي باللغة العربية الفصحى الواضحة.
                    لا تخترع أرقامًا أو أحداثًا أو اقتباسات؛ إذا احتاج الموضوع حقائق حديثة، ضع تنبيهًا واضحًا للتحقق قبل النشر.
                    أخرج مسودة قابلة للتحرير بعناوين ثابتة بهذا الترتيب: «عنوان الفيديو:»، «الفكرة:»، «التعليق الصوتي الكامل:» ثم نص الراوي وحده دون عناوين أو وصف مشاهد، ثم «تقسيم المشاهد:»، ثم المشاهد المرقمة ولكل مشهد زمن تقريبي و«الوصف البصري:» ونص المشهد المقتبس من التعليق الصوتي، ثم «ملاحظات التحقق:».
                    التزم بعدد المشاهد المطلوب للمدة. لا تكرر عناوين المشاهد في نص التعليق الصوتي ولا تضف وصفًا بصريًا إلى النص المخصص للقراءة.
                    اجعل العربية سليمة ومناسبة للمدة المحددة، وابتعد عن التكرار والمبالغة. لا تدّع أن النص تقرير موثق.
                """.trimIndent()
                val plan = ScriptPlans.forDuration(duration)
                val context = references.trim().take(3_000).ifBlank { "لا توجد مراجع أو معلومات إضافية مقدمة." }
                val prompt = "الموضوع: $topic\nالمدة: $duration\nالمنصة/نسبة العرض: $platform\nالنبرة: $style\nعدد المشاهد: ${plan.sceneCount}\nحجم النص: ${plan.wordTarget}\nمعلومات أو مصادر قدمها المالك: $context\nاستخدم المعلومات المقدمة بحذر. لا تدّع مراجعة الإنترنت أو التحقق من الوقائع، وضع أي نقطة تحتاج تحققًا في قسم الملاحظات."
                val response = Llama.complete(model, prompt, systemPrompt, plan.maxTokens)
                response.text.trim().ifBlank { throw IllegalStateException("لم يُنتج النموذج نصًا. أعد المحاولة بصياغة أقصر.") }
            } finally {
                Llama.releaseModel(model)
            }
        }
    }

    data class NewsArticleMaterial(val title: String, val sourceName: String, val sourceUrl: String, val imageUrl: String, val articleText: String)

    @JvmStatic
    fun fetchArticleMaterial(articleUrl: String): NewsArticleMaterial {
        var current = URL(articleUrl.trim())
        if (current.protocol != "https" || current.host.isBlank()) throw IllegalArgumentException("استخدم رابط خبر يبدأ بـ HTTPS.")
        var html = ""
        var finalUrl = current
        for (attempt in 0 until 4) {
            val addresses = java.net.InetAddress.getAllByName(current.host)
            if (addresses.isEmpty() || addresses.any { it.isAnyLocalAddress || it.isLoopbackAddress || it.isLinkLocalAddress || it.isSiteLocalAddress || it.isMulticastAddress }) throw IllegalArgumentException("عنوان الموقع غير مسموح لأسباب أمنية.")
            val conn = (current.openConnection() as HttpURLConnection).apply { instanceFollowRedirects = false; connectTimeout = 12000; readTimeout = 15000; requestMethod = "GET"; setRequestProperty("User-Agent", "WorldNewsAndroid/1.0"); setRequestProperty("Accept", "text/html,application/xhtml+xml") }
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: throw IllegalStateException("تحويل غير مكتمل من المصدر.")
                    val next = URL(current, location)
                    if (next.protocol != "https" || !next.host.equals(current.host, true)) throw IllegalStateException("تم رفض تحويل إلى نطاق مختلف حفاظًا على الأمان.")
                    current = next
                } else {
                    if (code !in 200..299) throw IllegalStateException("تعذر فتح صفحة الخبر (HTTP " + code + ").")
                    if (conn.contentLengthLong > 2500000L) throw IllegalStateException("صفحة الخبر أكبر من الحد المسموح.")
                    val out = java.io.ByteArrayOutputStream()
                    conn.inputStream.use { input -> val buffer = ByteArray(8192); var total = 0; while (true) { val n = input.read(buffer); if (n < 0) break; total += n; if (total > 2500000) throw IllegalStateException("صفحة الخبر أكبر من الحد المسموح."); out.write(buffer, 0, n) } }
                    html = out.toString("UTF-8")
                    finalUrl = current
                    break
                }
            } finally { conn.disconnect() }
        }
        if (html.isBlank()) throw IllegalStateException("لم نتمكن من قراءة صفحة الخبر.")
        fun meta(key: String): String {
            val tags = Regex("<meta\\b[^>]*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            for (match in tags.findAll(html)) {
                val tag = match.value
                val name = Regex("""(?:property|name)\s*=\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1).orEmpty()
                if (name.equals(key, true)) return Regex("""content\s*=\s*['"]([^'"]*)['"]""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1).orEmpty()
            }
            return ""
        }
        val rawTitle = meta("og:title").ifBlank { Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.groupValues?.get(1).orEmpty() }
        val title = android.text.Html.fromHtml(rawTitle, android.text.Html.FROM_HTML_MODE_COMPACT).toString().replace(Regex("\\s+"), " ").trim()
        val rawImage = meta("og:image").ifBlank { meta("twitter:image") }
        val image = if (rawImage.isBlank()) "" else try { URL(finalUrl, rawImage).let { if (it.protocol == "https") it.toString() else "" } } catch (_: Exception) { "" }
        var body = Regex("<article\\b[^>]*>(.*?)</article>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.groupValues?.get(1)
        if (body.isNullOrBlank()) body = Regex("<body\\b[^>]*>(.*?)</body>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(html)?.groupValues?.get(1).orEmpty()
        body = body.orEmpty().replace(Regex("<(script|style|nav|footer|header|aside|svg|form)\\b[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
        val articleText = android.text.Html.fromHtml(body.orEmpty(), android.text.Html.FROM_HTML_MODE_COMPACT).toString().replace(Regex("[ \t]+"), " ").replace(Regex("\\n{3,}"), "\n\n").trim().take(7000)
        if (articleText.length < 300) throw IllegalStateException("المصدر لم يعرض نصًا كافيًا داخل الصفحة؛ لن نصنع تقريرًا من العنوان وحده.")
        return NewsArticleMaterial(title.ifBlank { "خبر من " + finalUrl.host }, finalUrl.host.removePrefix("www."), finalUrl.toString(), image, articleText)
    }

    @JvmStatic
    fun generateNewsReport(context: Context, material: NewsArticleMaterial): String {
        val modelFile = scriptModelFile(context)
        if (!modelFile.isFile || modelFile.length() != QWEN_SIZE) throw IllegalStateException("نزّل نموذج Qwen المحلي أولًا.")
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memory)
        if (memory.availMem < 850L * 1024L * 1024L) throw IllegalStateException("الذاكرة الحرة قليلة لتشغيل Qwen؛ أغلق بعض التطبيقات وحاول مجددًا.")
        val config = LlamaConfig(contextSize = 4096, threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 4), gpuLayers = 0, temperature = 0.35f, topP = 0.85f, topK = 30, seed = -1)
        return runBlocking {
            val model = Llama.loadModel(modelFile.absolutePath, config)
            try {
                val systemPrompt = "أنت محرر أخبار عربي. اكتب تقريرًا صحفيًا اعتمادًا حصريًا على نص المصدر. ممنوع اختراع أسماء أو أرقام أو تواريخ أو تصريحات أو أسباب. ميّز بين المؤكد والمنسوب للمصدر والاحتمال. اكتب عنوانًا دقيقًا ومقدمة وثلاثة عناوين فرعية وفقرات، ثم قسمًا بعنوان ما الذي نعرفه يوضح حدود المعلومات. لا تنسخ فقرات المصدر حرفيًا ولا تضف معرفة من ذاكرتك. اختم بتنبيه أن التقرير مسودة مولدة محليًا وتحتاج مراجعة بشرية قبل النشر."
                val prompt = "عنوان المصدر: " + material.title + "\nالناشر: " + material.sourceName + "\nرابط المصدر: " + material.sourceUrl + "\n\nنص الصفحة المتاح:\n" + material.articleText
                val response = Llama.complete(model, prompt, systemPrompt, 1200)
                response.text.trim().ifBlank { throw IllegalStateException("لم ينتج Qwen تقريرًا؛ جرّب مصدرًا آخر.") }
            } finally { Llama.releaseModel(model) }
        }
    }
    @JvmStatic
    fun synthesizeArabic(context: Context, text: String): String {
        if (!voiceReady(context)) throw IllegalStateException("نزّل الصوت العربي الاحترافي أولًا.")
        if (text.isBlank()) throw IllegalArgumentException("اكتب نص التعليق الصوتي أولًا.")
        ensurePhonemizerInitialized(context)
        val dir = File(context.filesDir, "voice/$VOICE_FOLDER")
        val kokoro = OfflineTtsKokoroModelConfig(
            model = File(dir, "model.int8.onnx").absolutePath,
            voices = File(dir, "voices.bin").absolutePath,
            tokens = File(dir, "tokens.txt").absolutePath,
            dataDir = "",
            lang = "ar",
        )
        val model = OfflineTtsModelConfig(kokoro = kokoro, numThreads = 2, debug = false)
        val config = OfflineTtsConfig(model = model, maxNumSentences = 64)
        val tts = OfflineTts(context.assets, config)
        try {
            val language = tts.lang().ifBlank { "ar" }
            val phonemes = piperPhonemizeText(text, language)
                ?: throw IllegalStateException("تعذر تجهيز النطق العربي للنص.")
            try {
                val codepoints = Array(phonemes.numSentences) { index -> phonemes.getPhonemes(index) }
                if (codepoints.isEmpty() || codepoints.all { it.isEmpty() }) {
                    throw IllegalStateException("لم ينتج تجهيز النص العربي رموزًا صوتية؛ جرّب نصًا أقصر.")
                }
                val generation = GenerationConfig(sid = 0, speed = 1.0f, silenceScale = 0.2f)
                generation.phonemeCodepoints = codepoints
                val audio = tts.generateWithConfig("", generation)
                if (audio.samples.isEmpty()) throw IllegalStateException("لم ينتج Nabra صوتًا. جرّب نصًا عربيًا أقصر وواضحًا.")
                val output = File(context.cacheDir, "arabic-voice-preview.wav")
                if (!audio.save(output.absolutePath)) throw IllegalStateException("تعذر حفظ معاينة الصوت.")
                return output.absolutePath
            } finally {
                phonemes.close()
            }
        } finally {
            tts.release()
        }
    }

    private fun ensurePhonemizerInitialized(context: Context) {
        if (phonemizerInitialized) return
        synchronized(phonemizerLock) {
            if (!phonemizerInitialized) {
                piperPhonemizeInitialize(context.applicationContext)
                phonemizerInitialized = true
            }
        }
    }

    private fun ensureSpace(directory: File, needed: Long) {
        val stats = StatFs(directory.absolutePath)
        if (stats.availableBytes < needed) {
            val approxMb = (needed / (1024 * 1024)).toInt()
            throw IllegalStateException("يلزم توفير مساحة فارغة تقارب $approxMb ميغابايت على الهاتف.")
        }
    }

    private fun download(url: String, target: File, expectedSize: Long, listener: ProgressListener) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "WorldNews-Android")
            }
            val status = connection.responseCode
            if (status !in 200..299) throw IllegalStateException("فشل تنزيل النموذج (HTTP $status). تحقّق من الإنترنت ثم أعد المحاولة.")
            val declared = connection.contentLengthLong
            if (declared > 0 && declared != expectedSize) throw IllegalStateException("حجم ملف النموذج غير متوقع؛ أوقف التنزيل للحماية.")
            var downloaded = 0L
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(1024 * 256)
                    var lastPercent = -1
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        if (downloaded > expectedSize) throw IllegalStateException("تجاوز تنزيل النموذج الحجم المتوقع.")
                        val percent = ((downloaded * 100) / expectedSize).toInt().coerceAtMost(99)
                        if (percent != lastPercent) {
                            listener.onProgress(percent)
                            lastPercent = percent
                        }
                    }
                    output.fd.sync()
                }
            }
            if (downloaded != expectedSize) throw IllegalStateException("اكتمل التنزيل بحجم غير صحيح؛ احذف الملف الجزئي وحاول مجددًا.")
            listener.onProgress(100)
        } finally {
            connection?.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(1024 * 256)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
