package app.pleossupercare.phone.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class ApiException(val status: Int, val code: String, override val message: String) : Exception(message)

/**
 * 모의 서버(demo-app/server/care_server.py)와 JSON으로 주고받습니다. 규약은 전환 기록 5절.
 * 서버가 모든 응답에 넣는 serverTime으로 시계 차이(offset = serverTime − (보낸 시각 + 받은 시각) / 2)를 잽니다.
 */
class Api(private val baseUrl: () -> String, private val phoneId: () -> String?) {
    /** 마지막으로 잰 시계 차이(ms). 기록과 함께 clockOffsetMs로 보냅니다. */
    @Volatile var clockOffsetMs: Long? = null
        private set

    suspend fun get(path: String, base: String? = null): JSONObject = withContext(Dispatchers.IO) { call("GET", path, null, base) }

    suspend fun post(path: String, body: JSONObject?, base: String? = null): JSONObject =
        withContext(Dispatchers.IO) { call("POST", path, body, base) }

    private fun call(method: String, path: String, body: JSONObject?, base: String?): JSONObject {
        val url = URL((base ?: baseUrl()).trimEnd('/') + path)
        val c = url.openConnection() as HttpURLConnection
        val sent = System.currentTimeMillis()
        try {
            c.connectTimeout = 4000
            c.readTimeout = 20000
            c.requestMethod = method
            c.setRequestProperty("Accept", "application/json")
            phoneId()?.let { c.setRequestProperty("X-Phone-Id", it) }
            if (body != null) {
                val bytes = body.toString().toByteArray(Charsets.UTF_8)
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.setFixedLengthStreamingMode(bytes.size)
                c.outputStream.use { it.write(bytes) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val got = System.currentTimeMillis()
            val json = if (text.isBlank()) JSONObject() else runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (json.has("serverTime")) clockOffsetMs = json.optLong("serverTime") - (sent + got) / 2
            if (code !in 200..299) throw ApiException(code, json.optString("error", "http_$code"), json.optString("message", "서버 오류($code)"))
            return json
        } finally {
            c.disconnect()
        }
    }

    companion object {
        /** 사용자에게 보일 짧은 말. */
        fun describe(e: Throwable): String = when (e) {
            is ApiException -> when (e.code) {
                "code_not_found" -> "번호가 맞지 않습니다"
                "code_expired" -> "번호 시간이 지났습니다 · 차에서 새 번호를 받으세요"
                "too_many_attempts" -> "번호가 여러 번 틀려 잠겼습니다 · 차에서 새 번호를 받으세요"
                "pair_not_confirmed" -> "차 화면에서 연결을 눌러 주세요"
                else -> e.message
            }
            is IOException -> "서버에 닿지 않습니다 · 주소를 확인하세요"
            else -> e.message ?: "알 수 없는 오류"
        }
    }
}
