package com.yichincho.skcheck.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class NetException(message: String, val httpCode: Int = 0) : Exception(message)

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.MINUTES)
        .writeTimeout(2, TimeUnit.MINUTES)
        .build()

    val JSON = "application/json; charset=utf-8".toMediaType()

    /** Runs the request; returns code and body, or throws [NetException] when there is no connection. */
    fun call(request: Request, who: String): Pair<Int, String> = try {
        client.newCall(request).execute().use { it.code to (it.body?.string().orEmpty()) }
    } catch (e: IOException) {
        throw NetException("$who 連不上（${e.message ?: "網路問題"}）")
    }
}

data class Quote(
    val symbol: String,
    val name: String,
    val price: Double,
    val prevClose: Double,
    val time: Long,
)

/**
 * Yahoo Finance's public chart endpoint. It needs no key but is unofficial: it can change, and it answers
 * 429 when asked too often, so callers space their requests out.
 */
object Yahoo {
    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Mobile Safari/537.36"

    fun fetch(symbol: String): Quote {
        val req = Request.Builder()
            .url("https://query1.finance.yahoo.com/v8/finance/chart/$symbol?range=1d&interval=1d")
            .header("User-Agent", UA)
            .build()
        val (code, body) = Http.call(req, "Yahoo")
        if (code == 404) throw NetException("找不到 $symbol", 404)
        if (code == 429) throw NetException("Yahoo 說查太快了，稍後再試", 429)
        if (code !in 200..299) throw NetException("Yahoo 回報錯誤 $code", code)
        return parse(body, symbol)
    }

    fun parse(body: String, symbol: String): Quote {
        val root = try {
            JSONObject(body)
        } catch (e: JSONException) {
            throw NetException("Yahoo 回的資料看不懂")
        }
        val chart = root.optJSONObject("chart") ?: throw NetException("Yahoo 回的資料看不懂")
        val result = chart.optJSONArray("result")?.optJSONObject(0)
            ?: throw NetException("找不到 $symbol", 404)
        val meta = result.optJSONObject("meta") ?: throw NetException("Yahoo 沒有 $symbol 的報價")
        val price = meta.optDouble("regularMarketPrice", 0.0)
        if (price.isNaN() || price <= 0) throw NetException("Yahoo 沒有 $symbol 的報價")
        val prev = listOf("chartPreviousClose", "previousClose", "regularMarketPreviousClose")
            .map { meta.optDouble(it, 0.0) }.firstOrNull { !it.isNaN() && it > 0 } ?: 0.0
        val name = meta.optString("shortName").ifBlank { meta.optString("longName") }.ifBlank { symbol }
        return Quote(
            symbol = meta.optString("symbol").ifBlank { symbol },
            name = name,
            price = price,
            prevClose = prev,
            time = meta.optLong("regularMarketTime", 0),
        )
    }

    /** "2330" → tries 2330.TW (上市) then 2330.TWO (上櫃). A full symbol such as 2330.TW is used as it is. */
    fun resolve(input: String): Quote {
        val code = normalizeCode(input)
        if (code.isEmpty()) throw NetException("代號是空的")
        if ('.' in code) return fetch(code)
        if (!isTaiwanCode(code)) return fetch(code)
        return try {
            fetch("$code.TW")
        } catch (e: NetException) {
            if (e.httpCode != 404) throw e
            try {
                fetch("$code.TWO")
            } catch (e2: NetException) {
                if (e2.httpCode == 404) throw NetException("找不到 $code（上市、上櫃都查過）", 404) else throw e2
            }
        }
    }

    fun normalizeCode(input: String): String =
        input.trim().uppercase().replace("．", ".").replace(" ", "")

    fun isTaiwanCode(code: String) = Regex("""^\d{4,6}[A-Z]?$""").matches(code)
}
