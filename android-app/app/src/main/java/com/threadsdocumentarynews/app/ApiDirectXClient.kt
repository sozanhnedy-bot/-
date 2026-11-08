package com.threadsdocumentarynews.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TreeMap
import java.util.TimeZone
import java.util.regex.Pattern

/** A display-safe item plus aggregate-only data for Gemini. No post body or author is retained. */
data class XFeedItem(
    val name: String,
    val detail: String,
    val sourceUrl: String,
    val analysisData: XTrendData,
)

object ApiDirectXClient {
    private const val BASE_URL = "https://apidirect.io/v1/twitter"
    private const val EGYPT_WOEID = 23424802
    private const val MAX_RESPONSE_BYTES = 1_500_000
    private const val MAX_ITEMS = 50
    private const val MAX_POST_SCAN = 60
    private val hashtagPattern = Pattern.compile("#([\\p{L}\\p{N}_]{2,48})")

    @JvmStatic
    @Throws(Exception::class)
    fun loadEgyptTrends(apiKey: String): List<XFeedItem> =
        parseEgyptTrendsJson(getJson("$BASE_URL/trends?woeid=$EGYPT_WOEID", apiKey).toString())

    @JvmStatic
    fun parseEgyptTrendsJson(json: String): List<XFeedItem> {
        val root = JSONObject(json)
        val trends = root.optJSONArray("trends") ?: JSONArray()
        val observedAt = root.optString("as_of", "غير محدد").take(50)
        val location = root.optString("location", "مصر").take(60)
        val result = ArrayList<XFeedItem>()
        for (index in 0 until minOf(trends.length(), MAX_ITEMS)) {
            val item = trends.optJSONObject(index) ?: continue
            val name = item.optString("name", "").trim().take(100)
            if (name.isEmpty()) continue
            val query = item.optString("query", name).trim().take(140)
            val volumeValue = item.opt("tweet_volume")
            val volume = (volumeValue as? Number)?.toLong()
            val metric = if (volume != null) "حجم منشورات تقديري خلال 24 ساعة" else "حجم المنشورات غير متاح"
            val detail = buildString {
                append(location)
                if (volume != null) append(" · حجم تقديري: ").append(volume)
                append(" · وقت الالتقاط: ").append(observedAt)
            }
            val fallbackUrl = "https://x.com/search?q=" + enc(query)
            val safeUrl = safeXSearchUrl(item.optString("url", ""), fallbackUrl)
            val analysis = XTrendData(name, query, volume, metric, observedAt)
            result += XFeedItem(name, detail, safeUrl, analysis)
        }
        return result
    }

    @JvmStatic
    @Throws(Exception::class)
    fun searchHashtags(apiKey: String, hashtags: List<String>): List<XFeedItem> {
        require(hashtags.size == 1) { "ابحث عن هاشتاج واحد في كل مرة." }
        require(Pattern.matches("[\\p{L}\\p{N}_]{2,48}", hashtags.single())) { "صيغة الهاشتاج غير صالحة." }
        val query = "#${hashtags.single()}"
        val url = "$BASE_URL/posts?query=${enc(query)}&sort_by=most_recent"
        val json = getJson(url, apiKey).toString()
        val observedAt = SimpleDateFormat("yyyy-MM-dd HH:mm 'UTC'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())
        return parseHashtagSearchJson(json, hashtags.single(), observedAt)
    }

    /** Parses results into hashtag labels and counts only; raw snippets are never returned or persisted. */
    @JvmStatic
    fun parseHashtagSearchJson(json: String, requestedHashtag: String, observedAt: String): List<XFeedItem> {
        val root = JSONObject(json)
        val query = "#$requestedHashtag"
        val posts = root.optJSONArray("posts") ?: JSONArray()
        val counts = TreeMap<String, Int>(String.CASE_INSENSITIVE_ORDER)
        val scanCount = minOf(posts.length(), MAX_POST_SCAN)

        // Process returned snippets only in memory to count hashtag labels. Never retain or send post text.
        for (index in 0 until scanCount) {
            val post = posts.optJSONObject(index) ?: continue
            val localOnlyText = post.optString("title", "") + " " + post.optString("snippet", "")
            val matcher = hashtagPattern.matcher(localOnlyText)
            while (matcher.find()) {
                val tag = matcher.group(1)?.trim().orEmpty()
                if (tag.isNotEmpty()) counts[tag] = (counts[tag] ?: 0) + 1
            }
        }

        val result = ArrayList<XFeedItem>()
        val ranked = counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase(Locale.ROOT) })
        if (ranked.isEmpty()) {
            val name = "#$requestedHashtag"
            val metric = "عدد النتائج التي أعادها المزود للعينة، لا مقياس ترند رسمي"
            val analysis = XTrendData(name, query, posts.length().toLong(), metric, observedAt)
            result += XFeedItem(name, "أعاد المزود ${posts.length()} نتيجة؛ لم يظهر وسم قابل للعد في النصوص المختصرة.", searchUrl(name), analysis)
        } else {
            ranked.take(20).forEach { entry ->
                val name = "#${entry.key}"
                val metric = "تكرار الهاشتاج في عينة محلية من $scanCount نتيجة"
                val analysis = XTrendData(name, query, entry.value.toLong(), metric, observedAt)
                result += XFeedItem(name, "ظهر ${entry.value} مرة في عينة البحث؛ هذا ليس حجمًا رسميًا على X.", searchUrl(name), analysis)
            }
        }
        return result
    }

    private fun getJson(address: String, apiKey: String): JSONObject {
        val parsed = URI(address)
        require(parsed.scheme == "https" && parsed.host == "apidirect.io") { "رفض الاتصال بعنوان غير مسموح." }
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(address).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-API-Key", apiKey)
                setRequestProperty("User-Agent", "WorldNews-Android/1.6")
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { readBounded(it) }.orEmpty()
            if (code !in 200..299) {
                val providerMessage = runCatching { JSONObject(body).optString("error", "") }.getOrDefault("")
                    .replace(Regex("[\\r\\n\\t]+"), " ").take(140)
                val detail = if (providerMessage.isBlank()) "تحقق من المفتاح والحصة الشهرية." else providerMessage
                throw IllegalStateException("أعاد ApiDirect HTTP $code. $detail")
            }
            return JSONObject(body)
        } finally {
            connection?.disconnect()
        }
    }

    private fun readBounded(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > MAX_RESPONSE_BYTES) throw IllegalStateException("استجابة ApiDirect أكبر من الحد الآمن.")
            output.write(buffer, 0, count)
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    private fun searchUrl(query: String): String = "https://x.com/search?q=${enc(query)}"

    private fun safeXSearchUrl(candidate: String, fallback: String): String {
        return try {
            val uri = URI(candidate)
            val host = uri.host?.lowercase(Locale.ROOT)
            val trustedHost = host == "x.com" || host == "www.x.com" || host == "twitter.com" || host == "www.twitter.com"
            if (trustedHost && uri.userInfo == null && uri.path.orEmpty().startsWith("/search")) {
                URI("https", null, host, -1, uri.path, uri.query, null).toString()
            } else fallback
        } catch (_: Exception) {
            fallback
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}
