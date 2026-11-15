package app.pleosguard.demo.logic

import org.json.JSONArray
import org.json.JSONObject

/**
 * 케어 상태 저장(앱 폴더의 care.json). 기록(records.json)과 따로 둡니다.
 * 구독(모의) · 사용자가 조치 완료로 표시한 리콜 · 내 후기 · 정비 방문 · 폰에서 받은 증거 목록 · 짐 알림 설정.
 */
data class CareSnapshot(
    val subscribed: Set<String> = emptySet(),
    val recallDone: Set<String> = emptySet(),
    val myReviews: List<Review> = emptyList(),
    val visits: List<Visit> = emptyList(),
    val inbox: List<Evidence> = emptyList(),
    val captureSince: Long = 0L,
    val leftEnabled: Boolean = true,
    val rearEnabled: Boolean = true,
    val carId: String? = null,
    /** 연결해 둔 폰 이름(다시 열어도 연결 유지, 서버가 짝을 잊으면 다시 연결). */
    val pairedPhone: String? = null,
    val recallConfirmed: Set<String> = emptySet(),
    val recallNotMine: Set<String> = emptySet(),
    val leftReduced: Boolean = false,
    /** 내 자동차 보험사([Insurers] id). 처음 설치할 때 묻고 안심 화면에서 바꿉니다(2026-11-14 사용자 요청). */
    val insurerId: String? = null,
    /** 처음 설정에서 보험사를 물었는지(고르지 않고 '나중에'를 눌러도 true). */
    val insurerAsked: Boolean = false,
)

object CareStore {
    fun toJson(s: CareSnapshot): String = JSONObject().apply {
        put("version", 1)
        put("subscribed", JSONArray(s.subscribed.toList()))
        put("recallDone", JSONArray(s.recallDone.toList()))
        put("myReviews", JSONArray().apply { s.myReviews.forEach { put(reviewJson(it)) } })
        put("visits", JSONArray().apply {
            s.visits.forEach { v -> put(JSONObject().put("id", v.id).put("shopId", v.shopId).put("shopName", v.shopName).put("wall", v.wall).put("work", v.work).put("reviewed", v.reviewed)) }
        })
        put("inbox", JSONArray().apply { s.inbox.forEach { put(EvidenceJson.toJson(it)) } })
        put("captureSince", s.captureSince)
        put("leftEnabled", s.leftEnabled); put("rearEnabled", s.rearEnabled)
        put("carId", s.carId ?: JSONObject.NULL)
        put("pairedPhone", s.pairedPhone ?: JSONObject.NULL)
        put("recallConfirmed", JSONArray(s.recallConfirmed.toList())); put("recallNotMine", JSONArray(s.recallNotMine.toList()))
        put("leftReduced", s.leftReduced)
        put("insurerId", s.insurerId ?: JSONObject.NULL); put("insurerAsked", s.insurerAsked)
    }.toString()

    fun reviewJson(r: Review): JSONObject = JSONObject().put("id", r.id).put("shopId", r.shopId).put("author", r.author).put("rating", r.rating)
        .put("dims", JSONObject(r.dims)).put("tags", JSONArray(r.tags)).put("text", r.text).put("wall", r.wall).put("mine", r.mine).put("example", r.example).put("verified", r.verified)

    fun reviewFrom(o: JSONObject): Review {
        val d = o.optJSONObject("dims")
        val dims = if (d == null) emptyMap() else CareData.REVIEW_FACTS.filter { d.has(it) }.associateWith { d.optInt(it) }
        val tags = o.optJSONArray("tags")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        return Review(o.getString("id"), o.getString("shopId"), o.optString("author", "나"), o.optInt("rating"), dims, tags, o.optString("text"),
            o.optLong("wall"), o.optBoolean("mine", true), o.optBoolean("example", false), o.optBoolean("verified", true))
    }

    private fun strings(a: JSONArray?): List<String> = if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }

    fun from(json: String?): CareSnapshot {
        if (json.isNullOrBlank()) return CareSnapshot()
        return runCatching {
            val o = JSONObject(json)
            CareSnapshot(
                subscribed = strings(o.optJSONArray("subscribed")).toSet(),
                recallDone = strings(o.optJSONArray("recallDone")).toSet(),
                myReviews = o.optJSONArray("myReviews")?.let { a -> (0 until a.length()).mapNotNull { runCatching { reviewFrom(a.getJSONObject(it)) }.getOrNull() } } ?: emptyList(),
                visits = o.optJSONArray("visits")?.let { a ->
                    (0 until a.length()).map { i -> a.getJSONObject(i).let { v -> Visit(v.getString("id"), v.getString("shopId"), v.optString("shopName"), v.optLong("wall"), v.optString("work"), v.optBoolean("reviewed")) } }
                } ?: emptyList(),
                inbox = o.optJSONArray("inbox")?.let { a -> (0 until a.length()).mapNotNull { runCatching { EvidenceJson.from(a.getJSONObject(it)) }.getOrNull() } } ?: emptyList(),
                captureSince = o.optLong("captureSince", 0L),
                leftEnabled = o.optBoolean("leftEnabled", true),
                rearEnabled = o.optBoolean("rearEnabled", true),
                carId = if (!o.has("carId") || o.isNull("carId")) null else o.getString("carId"),
                pairedPhone = if (!o.has("pairedPhone") || o.isNull("pairedPhone")) null else o.getString("pairedPhone"),
                recallConfirmed = strings(o.optJSONArray("recallConfirmed")).toSet(),
                recallNotMine = strings(o.optJSONArray("recallNotMine")).toSet(),
                leftReduced = o.optBoolean("leftReduced", false),
                insurerId = if (!o.has("insurerId") || o.isNull("insurerId")) null else o.getString("insurerId"),
                insurerAsked = o.optBoolean("insurerAsked", false),
            )
        }.getOrDefault(CareSnapshot())
    }
}
