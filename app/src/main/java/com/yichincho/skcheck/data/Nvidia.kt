package com.yichincho.skcheck.data

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class ShotResult(val title: String, val summary: String, val rows: List<ShotRow>, val model: String)

/** NVIDIA's OpenAI-compatible API (build.nvidia.com), used here only to read screenshots. */
object Nvidia {
    private const val BASE = "https://integrate.api.nvidia.com/v1"

    /** Used until the key is tested and the live model list is known. */
    val DEFAULT_MODELS = listOf(
        "meta/llama-4-maverick-17b-128e-instruct",
        "meta/llama-3.2-90b-vision-instruct",
        "microsoft/phi-4-multimodal-instruct",
        "meta/llama-3.2-11b-vision-instruct",
        "nvidia/llama-3.1-nemotron-nano-vl-8b-v1",
    )

    /** Families in the order they are preferred: newer and more widely used first. */
    private val PREFERRED = listOf(
        "qwen3-vl", "llama-4-maverick", "nemotron-nano-12b-v2-vl", "qwen3.5", "llama-4-scout", "qwen2.5-vl",
        "gemma-3", "llama-3.2-90b-vision", "nemotron-nano-vl", "phi-4-multimodal", "llama-3.2-11b-vision",
    )
    private val VISION_HINTS = listOf("vision", "-vl", "vl-", "multimodal", "maverick", "scout", "gemma-3")
    private val NOT_CHAT = listOf("embed", "rerank", "retriever", "guard", "safety", "clip", "parse", "ocdr", "paligemma", "deplot", "kosmos", "neva", "vila")

    /** Picks up to five vision models from NVIDIA's full list: preferred families first, then newest by name. */
    fun topVision(all: List<String>): List<String> {
        fun rank(id: String): Int = PREFERRED.indexOfFirst { it in id.lowercase() }.let { if (it < 0) 100 else it }
        return all.asSequence()
            .filter { id -> val l = id.lowercase(); VISION_HINTS.any { it in l } && NOT_CHAT.none { it in l } }
            .distinct()
            .sortedWith(compareBy<String> { rank(it) }.thenByDescending { it })
            .take(5)
            .toList()
    }

    private fun check(code: Int, body: String) {
        if (code in 200..299) return
        val detail = try {
            val o = JSONObject(body)
            o.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() } ?: o.optString("detail").ifBlank { body }
        } catch (e: JSONException) {
            body
        }
        val hint = when (code) {
            401, 403 -> "（金鑰不對）"
            404, 410 -> "（這個模型不存在或已下架）"
            429 -> "（用太快或額度用完了）"
            else -> ""
        }
        throw NetException("NVIDIA 回報錯誤 $code$hint：${detail.take(160)}", code)
    }

    fun listModels(key: String): List<String> {
        val req = Request.Builder().url("$BASE/models").header("Authorization", "Bearer $key").build()
        val (code, body) = Http.call(req, "NVIDIA")
        check(code, body)
        val data = JSONObject(body).optJSONArray("data") ?: JSONArray()
        return data.mapObjects { it.optString("id") }.filter { it.isNotBlank() }
    }

    private fun chat(key: String, model: String, content: Any, maxTokens: Int): String {
        val body = JSONObject()
            .put("model", model)
            .put("temperature", 0.1)
            .put("max_tokens", maxTokens)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val req = Request.Builder().url("$BASE/chat/completions")
            .header("Authorization", "Bearer $key")
            .header("Accept", "application/json")
            .post(body.toString().toRequestBody(Http.JSON))
            .build()
        val (code, text) = Http.call(req, "NVIDIA")
        check(code, text)
        return JSONObject(text).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            ?: throw NetException("NVIDIA 沒有回答")
    }

    /**
     * Proves the key works (NVIDIA's model list is public, so a tiny chat is the real test) and returns the
     * top five vision models it offers.
     */
    fun verify(key: String): List<String> {
        val top = topVision(listModels(key)).ifEmpty { DEFAULT_MODELS }
        var last: Exception? = null
        for (m in top) {
            try {
                chat(key, m, "Reply with OK.", 8)
                return top
            } catch (e: NetException) {
                if (e.httpCode == 401 || e.httpCode == 403) throw e
                last = e
            }
        }
        throw last ?: NetException("NVIDIA 沒有可用的識圖模型")
    }

    const val PROMPT = """這是一張台股相關的截圖（可能是選股清單、看盤軟體或新聞）。請把圖上每一檔股票抄出來。
只輸出 JSON，不要其他文字，格式：
{"title":"圖上的標題或日期區間，沒有就空字串","summary":"整張圖的其他重點，例如平均報酬、是否結算，沒有就空字串","rows":[{"code":"股票代號","name":"股票名稱","reason":"名稱後面的說明文字","eps":"EPS 那一段的原文","from":"起始價","to":"目前價（箭頭右邊的價格；只有一個價格就填這裡）","pct":"漲跌幅，含正負號"}]}
規則：只抄圖上看得到的字，看不到的欄位填空字串，不要猜也不要編。代號只填數字（和字母），不要加 .TW。不要輸出評分。"""

    /** Sends the picture (a JPEG, base64) to [preferred] first, then the other models until one answers. */
    fun readShot(key: String, preferred: String, models: List<String>, jpegBase64: String): ShotResult {
        val order = (listOf(preferred) + models).filter { it.isNotBlank() }.distinct()
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", PROMPT))
            .put(JSONObject().put("type", "image_url")
                .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$jpegBase64")))
        val errors = mutableListOf<String>()
        for (m in order) {
            try {
                val parsed = parseShot(chat(key, m, content, 3000))
                if (parsed.rows.isNotEmpty()) return parsed.copy(model = m)
                errors.add("${m.substringAfter('/')}：沒有讀到股票")
            } catch (e: NetException) {
                if (e.httpCode == 401 || e.httpCode == 403) throw e
                errors.add("${m.substringAfter('/')}：${e.message}")
            }
        }
        throw NetException("每個模型都沒讀成功。\n" + errors.joinToString("\n"))
    }

    fun parseShot(text: String): ShotResult {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return ShotResult("", "", emptyList(), "")
        val o = try {
            JSONObject(text.substring(start, end + 1))
        } catch (e: JSONException) {
            return ShotResult("", "", emptyList(), "")
        }
        val rows = (o.optJSONArray("rows") ?: JSONArray()).mapObjects(::shotRowFromJson)
            .map { it.copy(code = Yahoo.normalizeCode(it.code).substringBefore('.')) }
            .filter { it.code.isNotEmpty() }
        return ShotResult(o.optString("title").trim(), o.optString("summary").trim(), rows, "")
    }
}
