package com.yichincho.skcheck.data

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Accept a copied Authorization value as well as a plain key. */
internal fun apiKey(key: String): String = key.trim().replace(Regex("^Bearer\\s+", RegexOption.IGNORE_CASE), "").trim()

/** Turns an error reply into a short readable message. */
internal fun apiCheck(who: String, code: Int, body: String) {
    if (code in 200..299) return
    val detail = try {
        val o = JSONObject(body)
        o.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() } ?: o.optString("detail").ifBlank { body }
    } catch (e: JSONException) {
        body
    }
    val hint = when (code) {
        400 -> "（這個模型可能不收圖片）"
        401 -> "（金鑰無效或已過期）"
        403 -> "（沒有模型權限，或服務受限）"
        402 -> "（餘額不足）"
        404, 410 -> "（這個模型不存在或已下架）"
        429 -> "（用太快或額度用完了）"
        else -> ""
    }
    throw NetException("$who 回報錯誤 $code$hint：${detail.take(160)}", code)
}

/** Google Gemini, used as the second choice for reading screenshots. */
object Gemini {
    const val DEFAULT_MODEL = "gemini-flash-latest"
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"
    private val UNWANTED = listOf("image", "tts", "live", "audio", "embedding", "robotics", "computer", "native", "learnlm", "gemma")

    fun listModels(key: String): List<String> {
        val req = Request.Builder().url("$BASE/models?pageSize=200").header("x-goog-api-key", apiKey(key)).build()
        val (code, body) = Http.call(req, "Gemini")
        apiCheck("Gemini", code, body)
        val models = JSONObject(body).optJSONArray("models") ?: JSONArray()
        return models.mapObjects { it }
            .filter { m -> (m.optJSONArray("supportedGenerationMethods") ?: JSONArray()).strings().contains("generateContent") }
            .map { it.optString("name").removePrefix("models/") }
    }

    /** Up to five models that read images: the rolling "flash-latest" first, then newest flash, then pro. */
    fun pick(all: List<String>): List<String> {
        val ok = all.filter { id -> id.startsWith("gemini") && UNWANTED.none { it in id } }
        val latest = ok.filter { it == "gemini-flash-latest" }
        val flash = ok.filter { "flash" in it && it !in latest && "lite" !in it }.sortedDescending()
        val pro = ok.filter { "pro" in it }.sortedDescending()
        val lite = ok.filter { "lite" in it }.sortedDescending()
        return (latest + flash + pro + lite).distinct().take(5)
    }

    private fun generate(key: String, model: String, parts: JSONArray, json: Boolean, maxTokens: Int): String {
        val config = JSONObject().put("temperature", 0.1).put("maxOutputTokens", maxTokens)
        if (json) config.put("responseMimeType", "application/json")
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", config)
        val req = Request.Builder().url("$BASE/models/$model:generateContent")
            .header("x-goog-api-key", apiKey(key))
            .post(body.toString().toRequestBody(Http.JSON))
            .build()
        val (code, text) = Http.call(req, "Gemini")
        apiCheck("Gemini", code, text)
        val out = JSONObject(text).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw NetException("Gemini 沒有回答")
        return (0 until out.length()).mapNotNull { out.optJSONObject(it)?.optString("text") }.joinToString("")
    }

    /** Checks the key with a one-word reply and returns the models to offer. */
    fun verify(key: String): List<String> {
        val models = pick(listModels(key)).ifEmpty { listOf(DEFAULT_MODEL) }
        generate(key, models.first(), JSONArray().put(JSONObject().put("text", "Reply with OK.")), json = false, maxTokens = 16)
        return models
    }

    fun readShot(key: String, model: String, jpegBase64: String): ShotResult {
        val parts = JSONArray()
            .put(JSONObject().put("text", Nvidia.PROMPT))
            .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", jpegBase64)))
        return Nvidia.parseShot(generate(key, model, parts, json = true, maxTokens = 4096)).copy(model = "Gemini · $model")
    }
}

/** DeepSeek official API; image capability comes from model metadata, not its name. */
object DeepSeek {
    const val DEFAULT_MODEL = "deepseek-flash"
    const val LEGACY_MODEL = "deepseek-v4-flash-vision-exp"
    private const val BASE = "https://api.deepseek.com"

    fun visionModels(body: String): List<String> =
        (JSONObject(body).optJSONArray("data") ?: JSONArray()).mapObjects { it }
            .filter { m ->
                val modalities = m.optJSONArray("input_modalities")
                if (modalities != null) "image" in modalities.strings()
                else m.optString("id").let { it == DEFAULT_MODEL || "vision" in it || "-vl" in it }
            }.map { it.optString("id") }.filter { it.isNotBlank() }.distinct()
            .sortedWith(compareBy<String> { if (it == DEFAULT_MODEL) 0 else 1 }.thenByDescending { it })

    fun listModels(key: String): List<String> {
        val req = Request.Builder().url("$BASE/models").header("Authorization", "Bearer ${apiKey(key)}").build()
        val (code, body) = Http.call(req, "DeepSeek")
        apiCheck("DeepSeek", code, body)
        return visionModels(body)
    }

    private fun chat(key: String, model: String, content: Any, maxTokens: Int): String {
        val body = JSONObject()
            .put("model", if (model == LEGACY_MODEL) DEFAULT_MODEL else model)
            .put("thinking", JSONObject().put("type", "disabled"))
            .put("temperature", 0.1)
            .put("max_tokens", maxTokens)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val req = Request.Builder().url("$BASE/chat/completions")
            .header("Authorization", "Bearer ${apiKey(key)}")
            .post(body.toString().toRequestBody(Http.JSON))
            .build()
        val (code, text) = Http.call(req, "DeepSeek")
        apiCheck("DeepSeek", code, text)
        return JSONObject(text).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.takeIf { it.isNotBlank() && it != "null" }
            ?: throw NetException("DeepSeek 沒有回答")
    }

    /** Probe images, trying the currently advertised vision models in order. */
    fun verify(key: String): String {
        val models = listModels(key)
        if (models.isEmpty()) throw NetException("DeepSeek 尚未提供此帳號可用的圖片模型")
        val errors = mutableListOf<String>()
        for (model in models) {
            try {
                chat(key, model, Nvidia.visionProbe(), 1024)
                return model
            } catch (e: NetException) {
                if (e.httpCode in listOf(401, 402, 429)) throw e
                errors.add("$model：${e.message}")
            }
        }
        throw NetException("DeepSeek 識圖測試未通過：\n" + errors.joinToString("\n"))
    }

    fun readShot(key: String, model: String, jpegBase64: String): ShotResult {
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", Nvidia.PROMPT))
            .put(JSONObject().put("type", "image_url")
                .put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$jpegBase64")))
        return Nvidia.parseShot(chat(key, model, content, 3000)).copy(model = "DeepSeek · $model")
    }
}

/** Reads a screenshot with whichever providers have a key: NVIDIA first, then Gemini, then DeepSeek. */
object Vision {
    fun hasAnyKey(s: Settings) = s.nvidiaKey.isNotBlank() || s.geminiKey.isNotBlank() || s.deepseekKey.isNotBlank()

    fun readShot(s: Settings, jpegBase64: String, onTrying: (String) -> Unit = {}): ShotResult {
        val errors = mutableListOf<String>()
        fun attempt(name: String, block: () -> ShotResult): ShotResult? {
            onTrying(name)
            return try {
                val r = block()
                if (r.rows.isNotEmpty()) r else { errors.add("$name：沒有讀到股票"); null }
            } catch (e: Exception) {
                errors.add("$name：${e.message}")
                null
            }
        }
        if (s.nvidiaKey.isNotBlank()) {
            attempt("NVIDIA") { Nvidia.readShot(s.nvidiaKey, s.visionModel, s.visionModels, jpegBase64) }?.let { return it }
        }
        if (s.geminiKey.isNotBlank()) {
            val models = (listOf(s.geminiModel) + s.geminiModels).distinct().take(2)
            for (m in models) attempt("Gemini") { Gemini.readShot(s.geminiKey, m, jpegBase64) }?.let { return it }
        }
        if (s.deepseekKey.isNotBlank()) {
            attempt("DeepSeek") { DeepSeek.readShot(s.deepseekKey, s.deepseekModel, jpegBase64) }?.let { return it }
        }
        if (errors.isEmpty()) throw NetException("還沒有設定任何金鑰")
        throw NetException("都沒讀成功：\n" + errors.joinToString("\n"))
    }
}
