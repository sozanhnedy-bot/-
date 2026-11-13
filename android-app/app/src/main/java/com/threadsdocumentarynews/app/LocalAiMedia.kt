package com.threadsdocumentarynews.app

import android.content.Context
import android.os.StatFs
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional local Arabic speech fallback. Script writing is handled by Gemini.
 */
object LocalAiMedia {
    private const val VOICE_SIZE = 82_810_819L
    private const val VOICE_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/nabra-82m-arabic-int8.tar.bz2"
    private const val VOICE_FOLDER = "nabra-82m-arabic-int8"
    fun interface ProgressListener {
        fun onProgress(percent: Int)
    }

    @JvmStatic
    fun voiceReady(context: Context): Boolean {
        val dir = File(context.filesDir, "voice/$VOICE_FOLDER")
        return File(dir, "model.int8.onnx").length() > 1_000_000L &&
            File(dir, "voices.bin").isFile && File(dir, "tokens.txt").isFile &&
            File(dir, "espeak-ng-data").isDirectory
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
    fun synthesizeArabic(context: Context, text: String): String {
        if (!voiceReady(context)) throw IllegalStateException("نزّل الصوت العربي الاحترافي أولًا.")
        if (text.isBlank()) throw IllegalArgumentException("اكتب نص التعليق الصوتي أولًا.")
        val dir = File(context.filesDir, "voice/$VOICE_FOLDER")
        val kokoro = OfflineTtsKokoroModelConfig(
            model = File(dir, "model.int8.onnx").absolutePath,
            voices = File(dir, "voices.bin").absolutePath,
            tokens = File(dir, "tokens.txt").absolutePath,
            dataDir = File(dir, "espeak-ng-data").absolutePath,
            lang = "ar",
        )
        val model = OfflineTtsModelConfig(kokoro = kokoro, numThreads = 2, debug = false)
        val config = OfflineTtsConfig(model = model, maxNumSentences = 64)
        val tts = OfflineTts(context.assets, config)
        try {
            val generation = GenerationConfig(sid = 0, speed = 1.0f, silenceScale = 0.2f)
            val audio = tts.generateWithConfig(text, generation)
            if (audio.samples.isEmpty()) throw IllegalStateException("لم ينتج Nabra صوتًا. جرّب نصًا أقصر وواضحًا.")
            val output = File(context.cacheDir, "nabra-arabic-voice.wav")
            if (!audio.save(output.absolutePath)) throw IllegalStateException("تعذر حفظ ملف صوت Nabra.")
            return output.absolutePath
        } finally {
            tts.release()
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

}
