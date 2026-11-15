package app.pleosguard.demo.sdk

import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import app.pleosguard.demo.logic.CareData
import app.pleosguard.demo.logic.Evidence
import app.pleosguard.demo.logic.EvidenceJson
import app.pleosguard.demo.logic.Recall
import app.pleosguard.demo.logic.Review
import app.pleosguard.demo.logic.ShopInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * 차 앱 ↔ 모의 서버(demo-app/server/care_server.py) 연결. 규약은 docs/analysis/super-care-pivot-2026-11-14.md 5절입니다.
 * 에뮬레이터에서는 PC를 http://10.0.2.2:8766으로 부릅니다(이 PC의 8765는 다른 프로젝트가 씀, 2026-11-14).
 * 디버그 빌드는 시험 입력 `server --es url ...`로 바꿀 수 있고 앱 설정에 남습니다. 실제 서비스에서는 이 자리가 백엔드 서버입니다.
 * 네트워크는 뒤 스레드 하나에서 하고, 결과는 메인 스레드로 넘깁니다. 실패하면 화면이 앱 안 예시 자료로 돌아갑니다(오프라인).
 */
class CareLink(val base: String, private val evidenceDir: File) {
    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    @Volatile var lastError: String? = null; private set

    private fun <T> call(work: () -> T, done: (Result<T>) -> Unit) {
        exec.execute {
            val r = runCatching(work)
            // 서버가 꺼져 있으면 몇 초마다 실패하므로 한 줄만 남깁니다(전체 호출 기록이 로그를 덮어 다른 기록이 사라졌음, 2026-11-14).
            r.exceptionOrNull()?.let { lastError = it.javaClass.simpleName + ": " + (it.message ?: "").take(120); Log.w(TAG, "call failed: $lastError") }
            if (r.isSuccess) lastError = null
            main.post { done(r) }
        }
    }

    private fun http(method: String, path: String, body: JSONObject? = null, timeoutMs: Int = 3_000): String {
        val c = URL(base + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method; c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs
            c.setRequestProperty("Accept", "application/json")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IOException("HTTP $code ${text.take(120)}")
            return text
        } finally { c.disconnect() }
    }

    private fun q(v: String) = URLEncoder.encode(v, "UTF-8")

    /** 목록 응답: 배열 그대로이거나 {items: [...]} 같은 객체. */
    private fun array(text: String, vararg keys: String): JSONArray {
        val t = text.trim()
        if (t.startsWith("[")) return JSONArray(t)
        val o = JSONObject(t)
        for (k in keys.toList() + listOf("items", "data", "results")) o.optJSONArray(k)?.let { return it }
        return JSONArray()
    }

    fun health(done: (Boolean) -> Unit) = call({ http("GET", "/api/health", timeoutMs = 1_500); true }) { done(it.getOrDefault(false)) }

    /** 짝 맺기 시작: (6자리 코드, 만료 시각). */
    fun pairStart(carId: String, done: (Result<Pair<String, Long>>) -> Unit) = call({
        val o = JSONObject(http("POST", "/api/pair/start", JSONObject().put("carId", carId)))
        o.getString("code") to o.optLong("expiresAt", System.currentTimeMillis() + 600_000)
    }, done)

    /** 짝 상태. pending = 폰이 코드를 넣고 차 화면의 확인을 기다림(조사 권고: 차 화면 확인 단계). */
    data class PairState(val paired: Boolean, val pending: Boolean, val phoneName: String?)

    fun pairStatus(carId: String, done: (Result<PairState>) -> Unit) = call({
        val o = JSONObject(http("GET", "/api/pair/status?carId=${q(carId)}", timeoutMs = 2_000))
        PairState(o.optBoolean("paired"), o.optBoolean("pending"), o.optString("phoneName").ifBlank { null })
    }, done)

    /** 차 화면에서 연결을 허락 · 거절합니다. */
    fun pairConfirm(carId: String, accept: Boolean, done: (Result<Unit>) -> Unit) = call({
        http("POST", if (accept) "/api/pair/confirm" else "/api/pair/reject", JSONObject().put("carId", carId)); Unit
    }, done)

    /** 폰에 기록을 부탁합니다(kind: noise · vibration · warning_photo · photo, 알림만: trunk_reminder · rear_seat_reminder). 요청 id를 돌려줍니다. */
    fun request(carId: String, kind: String, recordId: String?, partHint: String?, text: String? = null, done: (Result<String>) -> Unit) = call({
        val b = JSONObject().put("carId", carId).put("kind", kind)
        recordId?.let { b.put("recordId", it) }; partHint?.let { b.put("partHint", it) }; text?.let { b.put("text", it) }
        val o = JSONObject(http("POST", "/api/requests", b))
        o.optString("id").ifBlank { o.optJSONObject("request")?.optString("id") ?: "" }
    }, done)

    /** 폰에서 온 기록. 사진 · 소리는 앱 폴더(evidence/)에 저장하고 받았음을 알립니다. (증거, 요청 id, 기록 id) */
    data class Incoming(val evidence: Evidence, val requestId: String?, val recordId: String?, val receivedAt: Long)

    fun captures(carId: String, since: Long, done: (Result<List<Incoming>>) -> Unit) = call({
        val a = array(http("GET", "/api/captures?carId=${q(carId)}&since=$since", timeoutMs = 4_000), "captures")
        (0 until a.length()).map { i -> parseCapture(a.getJSONObject(i)) }.also { list ->
            list.forEach { runCatching { http("POST", "/api/captures/${q(it.evidence.id)}/ack", JSONObject()) } }
        }
    }, done)

    private fun parseCapture(o: JSONObject): Incoming {
        val id = o.getString("id")
        evidenceDir.mkdirs()
        fun save(field: String, ext: String): String? {
            // JSON null은 optString이 "null" 글자를 돌려주므로 먼저 거릅니다.
            if (!o.has(field) || o.isNull(field)) return null
            val b64 = o.optString(field).ifBlank { return null }
            val f = File(evidenceDir, "$id.$ext")
            f.writeBytes(Base64.decode(b64, Base64.DEFAULT))
            return f.absolutePath
        }
        val copy = JSONObject(o.toString()).apply {
            put("wall", o.optLong("startedAt", System.currentTimeMillis()))
            save("imageJpegBase64", "jpg")?.let { put("imagePath", it) }
            save("audioM4aBase64", "m4a")?.let { put("audioPath", it) }
            remove("imageJpegBase64"); remove("audioM4aBase64")
        }
        fun s(k: String) = if (!o.has(k) || o.isNull(k)) null else o.optString(k).ifBlank { null }
        return Incoming(EvidenceJson.from(copy), s("requestId"), s("recordId"), o.optLong("receivedAt", System.currentTimeMillis()))
    }

    // ---------------------------------------------------------------- 정비소 · 후기 · 리콜
    fun shops(done: (Result<List<ShopInfo>>) -> Unit) = call({
        val a = array(http("GET", "/api/shops"), "shops")
        (0 until a.length()).map { i -> shopFrom(a.getJSONObject(i)) }
    }, done)

    fun reviews(shopId: String, carId: String, done: (Result<List<Review>>) -> Unit) = call({
        val a = array(http("GET", "/api/shops/${q(shopId)}/reviews?carId=${q(carId)}"), "reviews")
        (0 until a.length()).map { i -> reviewFrom(a.getJSONObject(i), shopId) }
    }, done)

    /** 방문 기록을 서버에 남깁니다(방문 확인 후기의 근거). 서버 방문 id를 돌려줍니다. */
    fun postVisit(carId: String, shopId: String, work: String, done: (Result<String>) -> Unit) = call({
        val o = JSONObject(http("POST", "/api/visits", JSONObject().put("carId", carId).put("shopId", shopId).put("work", work)))
        o.optString("id").ifBlank { o.optJSONObject("visit")?.optString("id") ?: "" }
    }, done)

    fun postReview(r: Review, carId: String, visitId: String?, done: (Result<Review>) -> Unit) = call({
        val b = JSONObject().put("author", r.author).put("rating", r.rating).put("dims", JSONObject(r.dims))
            .put("tags", JSONArray(r.tags)).put("text", r.text).put("carId", carId)
        visitId?.let { b.put("visitId", it) }
        val o = JSONObject(http("POST", "/api/shops/${q(r.shopId)}/reviews", b))
        reviewFrom(o.optJSONObject("review") ?: o, r.shopId).copy(mine = true)
    }, done)

    fun recalls(model: String, year: Int, done: (Result<List<Recall>>) -> Unit) = call({
        val a = array(http("GET", "/api/recalls?model=${q(model)}&year=$year"), "recalls")
        (0 until a.length()).map { i -> recallFrom(a.getJSONObject(i)) }
    }, done)

    fun release() { exec.shutdownNow() }

    companion object {
        const val TAG = "CareLink"
        const val EMULATOR_BASE = "http://10.0.2.2:8766"

        private fun JSONObject.str(k: String, def: String = ""): String = if (!has(k) || isNull(k)) def else optString(k, def)
        private fun JSONObject.strs(k: String): List<String> = optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()

        fun shopFrom(o: JSONObject) = ShopInfo(
            id = o.getString("id"), name = o.str("name", "정비소"), services = o.strs("services"), hours = o.str("hours"),
            ev = o.optBoolean("ev"), priceNote = o.str("priceNote"), certs = o.strs("certs"),
            rating = o.optDouble("rating", 0.0).toFloat(), reviewCount = o.optInt("reviewCount", 0), example = o.optBoolean("example", true),
            partner = o.optBoolean("partner", false),
        )

        fun reviewFrom(o: JSONObject, shopId: String): Review {
            val dims = o.optJSONObject("dims")?.let { d -> CareData.REVIEW_FACTS.filter { d.has(it) }.associateWith { d.optInt(it) } } ?: emptyMap()
            return Review(
                id = o.str("id", java.util.UUID.randomUUID().toString()), shopId = o.str("shopId", shopId), author = o.str("author", "사용자"),
                rating = o.optInt("rating", 0).coerceIn(0, 5), dims = dims, tags = o.strs("tags"), text = o.str("text"),
                wall = o.optLong("createdAt", o.optLong("wall", System.currentTimeMillis())), mine = o.optBoolean("mine"), example = o.optBoolean("example", false),
            )
        }

        fun recallFrom(o: JSONObject) = Recall(
            id = o.getString("id"), title = o.str("title"), part = o.str("part"), risk = o.str("risk"), action = o.str("action"),
            period = o.str("period"), done = o.optBoolean("done"), example = o.optBoolean("example", true), source = o.str("source", "가상 예시"),
            level = o.str("level", "일반"), fix = o.str("fix", "입고 수리"), gen = o.str("gen", "CN8"), produced = o.str("produced"), ota = o.optBoolean("ota"), checked = o.str("checked"),
        )
    }
}
