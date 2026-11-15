package app.pleosguard.demo.logic

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 시간표 한 점(기록 시점 기준 상대 시각 ms). 값이 없으면 null 그대로 둡니다. */
data class SnapPoint(val dt: Int, val kmh: Int?, val speedTest: Boolean, val brake: Boolean?, val steering: Float?, val turn: String?)

/** 기록 때 함께 남긴 실내 상태(H3 소리 용의자 지우기). test=true면 [시험 입력]. */
data class Cabin(val acOn: Boolean?, val fan: Int?, val windowsClosed: Boolean?, val wiperOff: Boolean?, val test: Boolean)

/** 정비 노트(N6): 정비 받은 날·주행거리. 같은 증상이 다시 기록되는지 500km 동안 봅니다. */
data class ServiceNote(val symptom: String, val text: String, val wall: Long, val odometerKm: Float?)

/** 두 조건의 겹침(N2): 이 증상 기록에서 맞은 수와 평소 주행에서 맞은 비율. */
data class Cond(val label: String, val hits: Int, val total: Int, val usualPct: Int, val usualExample: Boolean, val test: Boolean)

data class Pattern(val symptom: String, val part: String?, val records: List<Record>, val conds: List<Cond>, val excluded: List<String>, val cabinTest: Boolean)

/** 기록 하나. t0는 앱이 켜진 동안만 쓰는 elapsedRealtime이고, 저장·목록에는 wall과 상대 시각 점(points)을 씁니다. */
data class Record(
    val id: String = UUID.randomUUID().toString(),
    val t0: Long,
    val wall: Long,
    val drivingAtRecord: Boolean,
    val symptom: String? = null,
    val part: String? = null,
    val memo: String? = null,
    val memoTest: Boolean = false,
    val points: List<SnapPoint> = emptyList(),
    val frozen: Boolean = false,
    val cabin: Cabin? = null,
    val example: Boolean = false,
    /** 심한 정도: 0 모름, 1 약함, 2 보통, 3 심함(2026-11-14 기록 강화). */
    val severity: Int = 0,
    /** 얼마나 자주: null 모름, [RecordRules.FREQUENCIES] 가운데 하나. */
    val frequency: String? = null,
    /** 사용자가 고른 나는 때([RecordRules.CONDITIONS]). 차 신호로 찾는 재현 조건(Pattern)과 따로 둡니다. */
    val conditions: List<String> = emptyList(),
    /** 처리 흐름([RecordStatus] 이름). */
    val status: String = RecordStatus.OPEN.name,
    /** 폰에서 온 증거(소리 · 진동 · 경고등 사진 · 사진). */
    val evidence: List<Evidence> = emptyList(),
)

/** 기록 처리 흐름: 기록함 → 정비소에 보여 줌 → 정비 받음 → 해결. 정비 뒤 재발 확인(500km)은 정비 노트가 맡습니다. */
enum class RecordStatus(val label: String) { OPEN("기록함"), SHOWN("정비소에 보여 줌"), FIXED("정비 받음"), DONE("해결") }

/** 평소 주행 통계(재현 조건 비교용): 주행 중 표본 수와 조건별 맞은 수. */
data class DriveStats(val samples: Long = 0, val lowSpeed: Long = 0, val braking: Long = 0)

object RecordRules {
    /** rules-lab 재현 조건: 같은 증상 3회. */
    const val SAME_SYMPTOM = 3
    /** 저속 조건(시속 20km 아래). 대표 사례 "시속 20km 아래에서 브레이크를 밟을 때". */
    const val LOW_KMH = 20
    /** 재발 확인 거리(rules-lab 정비 재발 확인 500km). */
    const val RECHECK_KM = 500f
    /** 평소 주행 표본이 이보다 적으면 평소 비율은 문서 예시 값(18%·12%)으로 보이고 "예시"를 붙입니다. */
    const val MIN_USUAL_SAMPLES = 600L
    /** 나는 때(사용자가 고름). 정비사가 증상을 다시 내 볼 때 묻는 질문을 칩으로 바꾼 것입니다. */
    val CONDITIONS = listOf("출발할 때", "저속", "고속", "브레이크 밟을 때", "핸들 돌릴 때", "방지턱 · 요철", "추울 때", "비 온 뒤", "에어컨 켤 때")
    val FREQUENCIES = listOf("처음", "가끔", "자주", "늘")
    val SEVERITIES = listOf("모름", "약함", "보통", "심함")
}

class RecordBook {
    val records = mutableListOf<Record>()
    val notes = mutableListOf<ServiceNote>()
    var stats = DriveStats()

    fun add(r: Record) { records.add(r) }
    fun replace(r: Record) { val i = records.indexOfFirst { it.id == r.id }; if (i >= 0) records[i] = r }
    fun byId(id: String?) = records.firstOrNull { it.id == id }

    /** 같은 증상이 3번 쌓인 묶음 하나(가장 최근 증상 우선)로 재현 조건 카드를 만듭니다. */
    fun pattern(): Pattern? {
        val groups = records.filter { it.symptom != null }.groupBy { it.symptom!! }
        val sym = groups.entries.filter { it.value.size >= RecordRules.SAME_SYMPTOM }.maxByOrNull { e -> e.value.maxOf { it.wall } }?.key ?: return null
        val recs = groups.getValue(sym).sortedBy { it.wall }.takeLast(RecordRules.SAME_SYMPTOM)
        val at = recs.map { r -> r.points.minByOrNull { kotlin.math.abs(it.dt) } }
        val low = at.count { it?.kmh != null && it.kmh < RecordRules.LOW_KMH }
        val brk = at.count { it?.brake == true }
        val speedTest = at.any { it?.speedTest == true }
        val enough = stats.samples >= RecordRules.MIN_USUAL_SAMPLES
        val usualLow = if (enough) (stats.lowSpeed * 100 / stats.samples).toInt() else 18
        val usualBrake = if (enough) (stats.braking * 100 / stats.samples).toInt() else 12
        val conds = listOf(
            Cond("시속 ${RecordRules.LOW_KMH}km 아래", low, recs.size, usualLow, !enough, speedTest),
            Cond("브레이크 밟는 중", brk, recs.size, usualBrake, !enough, false),
        )
        val cabins = recs.mapNotNull { it.cabin }
        val excluded = buildList {
            val ac = cabins.count { it.acOn == true }
            if (cabins.isNotEmpty()) add(if (ac == 0) "에어컨 꺼짐" else "에어컨 ${ac}번만 켜짐")
            if (cabins.isNotEmpty() && cabins.all { it.windowsClosed == true }) add("창문 평소처럼 닫힘")
            if (cabins.isNotEmpty() && cabins.all { it.wiperOff == true }) add("와이퍼 평소처럼 꺼짐")
        }
        return Pattern(sym, recs.lastOrNull { it.part != null }?.part, recs, conds, excluded, cabins.any { it.test })
    }

    /** 정비 노트 뒤 같은 증상 기록 수와 남은 확인 거리. */
    fun recheck(symptom: String, odometerKm: Float?): Pair<Int, Float?>? {
        val n = notes.lastOrNull { it.symptom == symptom } ?: return null
        val again = records.count { it.symptom == symptom && it.wall > n.wall }
        val left = if (n.odometerKm != null && odometerKm != null) (RecordRules.RECHECK_KM - (odometerKm - n.odometerKm)).coerceAtLeast(0f) else null
        return again to left
    }

    // ---------------------------------------------------------------- 저장(JSON)
    fun toJson(): String = JSONObject().apply {
        put("version", 2)
        put("records", JSONArray().apply { records.filter { it.frozen || it.example }.forEach { put(recordJson(it)) } })
        put("notes", JSONArray().apply { notes.forEach { put(JSONObject().put("symptom", it.symptom).put("text", it.text).put("wall", it.wall).put("odometerKm", it.odometerKm?.toDouble() ?: JSONObject.NULL)) } })
        put("stats", JSONObject().put("samples", stats.samples).put("lowSpeed", stats.lowSpeed).put("braking", stats.braking))
    }.toString()

    fun load(json: String?) {
        if (json.isNullOrBlank()) return
        runCatching {
            val o = JSONObject(json)
            records.clear(); notes.clear()
            o.optJSONArray("records")?.let { a -> for (i in 0 until a.length()) records.add(recordFrom(a.getJSONObject(i))) }
            o.optJSONArray("notes")?.let { a ->
                for (i in 0 until a.length()) a.getJSONObject(i).let { n ->
                    notes.add(ServiceNote(n.getString("symptom"), n.getString("text"), n.getLong("wall"), if (n.isNull("odometerKm")) null else n.getDouble("odometerKm").toFloat()))
                }
            }
            o.optJSONObject("stats")?.let { s -> stats = DriveStats(s.optLong("samples"), s.optLong("lowSpeed"), s.optLong("braking")) }
        }
    }

    private fun recordJson(r: Record) = JSONObject().apply {
        put("id", r.id); put("wall", r.wall); put("driving", r.drivingAtRecord)
        put("symptom", r.symptom ?: JSONObject.NULL); put("part", r.part ?: JSONObject.NULL)
        put("memo", r.memo ?: JSONObject.NULL); put("memoTest", r.memoTest); put("example", r.example)
        r.cabin?.let { c -> put("cabin", JSONObject().put("ac", c.acOn ?: JSONObject.NULL).put("fan", c.fan ?: JSONObject.NULL).put("windowsClosed", c.windowsClosed ?: JSONObject.NULL).put("wiperOff", c.wiperOff ?: JSONObject.NULL).put("test", c.test)) }
        put("points", JSONArray().apply {
            r.points.forEach { p -> put(JSONObject().put("dt", p.dt).put("kmh", p.kmh ?: JSONObject.NULL).put("st", p.speedTest).put("b", p.brake ?: JSONObject.NULL).put("s", p.steering?.toDouble() ?: JSONObject.NULL).put("t", p.turn ?: JSONObject.NULL)) }
        })
        put("severity", r.severity); put("frequency", r.frequency ?: JSONObject.NULL); put("status", r.status)
        put("conditions", JSONArray(r.conditions))
        put("evidence", JSONArray().apply { r.evidence.forEach { put(EvidenceJson.toJson(it)) } })
    }

    private fun JSONObject.optBool(k: String): Boolean? = if (isNull(k) || !has(k)) null else getBoolean(k)

    private fun recordFrom(o: JSONObject): Record {
        val pts = o.optJSONArray("points")?.let { a ->
            (0 until a.length()).map { i -> a.getJSONObject(i).let { p ->
                SnapPoint(p.getInt("dt"), if (p.isNull("kmh")) null else p.getInt("kmh"), p.optBoolean("st"), p.optBool("b"),
                    if (p.isNull("s")) null else p.getDouble("s").toFloat(), if (p.isNull("t")) null else p.getString("t"))
            } }
        } ?: emptyList()
        val cabin = o.optJSONObject("cabin")?.let { c -> Cabin(c.optBool("ac"), if (c.isNull("fan")) null else c.getInt("fan"), c.optBool("windowsClosed"), c.optBool("wiperOff"), c.optBoolean("test")) }
        val conds = o.optJSONArray("conditions")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList()
        val ev = o.optJSONArray("evidence")?.let { a -> (0 until a.length()).mapNotNull { i -> runCatching { EvidenceJson.from(a.getJSONObject(i)) }.getOrNull() } } ?: emptyList()
        return Record(o.getString("id"), 0L, o.getLong("wall"), o.optBoolean("driving"),
            if (o.isNull("symptom")) null else o.getString("symptom"), if (o.isNull("part")) null else o.getString("part"),
            if (o.isNull("memo")) null else o.getString("memo"), o.optBoolean("memoTest"), pts, true, cabin, o.optBoolean("example"),
            severity = o.optInt("severity", 0), frequency = if (!o.has("frequency") || o.isNull("frequency")) null else o.getString("frequency"),
            conditions = conds, status = o.optString("status", RecordStatus.OPEN.name).ifBlank { RecordStatus.OPEN.name }, evidence = ev)
    }

    companion object {
        /** 재현 조건 카드를 시연하려고 넣는 예시 기록 2건(문서 대표 사례 11월 6일·9일). 화면에 "예시 기록"을 붙입니다. */
        fun examples(nowWall: Long): List<Record> {
            val day = 24L * 3600 * 1000
            fun pts(kmhAt: Int) = (-20..10).map { s ->
                val k = if (s < -6) kmhAt + 8 - (s + 20) / 3 else if (s <= 2) kmhAt else kmhAt - 2
                SnapPoint(s * 1000, k.coerceAtLeast(0), true, s in -5..2, 1f, "OFF")
            }
            return listOf(
                Record(t0 = 0, wall = nowWall - 7 * day, drivingAtRecord = true, symptom = "소음", part = "앞 타이어", memo = "앞 타이어 쪽 끼익 소리", points = pts(13), frozen = true,
                    cabin = Cabin(true, 2, true, true, true), example = true),
                Record(t0 = 0, wall = nowWall - 4 * day, drivingAtRecord = true, symptom = "소음", part = "앞 타이어", memo = "끼익 소리 다시", points = pts(15), frozen = true,
                    cabin = Cabin(false, 0, true, true, true), example = true),
            )
        }
    }
}
