package io.github.shoalapps.swordfish.data

import io.github.shoalapps.swordfish.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/**
 * Video titles in your language. YouTube already sends the creator's own translation when there
 * is one (NewPipe asks in your language); everything else goes through Google Translate's free
 * endpoint, one title at a time, remembered for the session. Titles already in your language
 * come back unchanged and are remembered too, so they're never sent twice.
 */
object TitleTranslator {
    data class Result(val text: String, val from: String?)

    private val cache = object : LinkedHashMap<String, Result>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Result>?) = size > 3000
    }
    private val gate = Semaphore(4)

    fun cached(title: String, to: String): Result? = synchronized(cache) { cache["$to\u0000$title"] }

    suspend fun translate(title: String, to: String): Result? {
        if (title.isBlank() || to.isBlank()) return null
        cached(title, to)?.let { return it }
        return gate.withPermit {
            cached(title, to) ?: withContext(Dispatchers.IO) {
                runCatching {
                    val url = "https://translate.googleapis.com/translate_a/single".toHttpUrl().newBuilder()
                        .addQueryParameter("client", "gtx").addQueryParameter("sl", "auto")
                        .addQueryParameter("tl", to).addQueryParameter("dt", "t").addQueryParameter("q", title).build()
                    val body = AppGraph.http.newCall(Request.Builder().url(url).build()).execute().use {
                        if (!it.isSuccessful) return@runCatching null
                        it.body?.string().orEmpty()
                    }
                    parse(body, title, to)
                }.getOrNull()?.also { r -> synchronized(cache) { cache["$to\u0000$title"] = r } }
            }
        }
    }

    /**
     * Many short lines in as few requests as fit (about 1,200 characters each), in order. A batch
     * that comes back with a different number of lines falls back to one line per request.
     * [onBatch] gets each finished batch's start index and lines, so captions can fill in as they go.
     */
    suspend fun lines(lines: List<String>, to: String, onBatch: suspend (start: Int, out: List<String>) -> Unit) {
        var start = 0
        while (start < lines.size) {
            var end = start
            var chars = 0
            while (end < lines.size && (end == start || chars + lines[end].length < 1200)) { chars += lines[end].length + 1; end++ }
            val batch = lines.subList(start, end).map { it.replace('\n', ' ') }
            val joined = translate(batch.joinToString("\n"), to)?.text?.split('\n')
            val out = if (joined != null && joined.size == batch.size) joined
                else batch.map { translate(it, to)?.text ?: it }
            onBatch(start, out.map { it.trim() })
            start = end
        }
    }

    /** `[[["Bonjour","Hello",…],…],null,"en",…]`: the pieces joined, and the detected language. */
    fun parse(body: String, original: String, to: String): Result? {
        val root = runCatching { Json.parseToJsonElement(body).jsonArray }.getOrNull() ?: return null
        val pieces = (root.getOrNull(0) as? JsonArray) ?: return null
        val text = pieces.mapNotNull { ((it as? JsonArray)?.getOrNull(0) as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.joinToString("")
        val from = (root.getOrNull(2) as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (text.isBlank()) return null
        // Already in your language: keep the creator's exact wording.
        val same = from != null && from.substringBefore('-').equals(to.substringBefore('-'), ignoreCase = true)
        return Result(if (same) original else text, from)
    }
}
