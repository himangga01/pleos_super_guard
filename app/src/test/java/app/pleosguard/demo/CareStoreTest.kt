package app.pleosguard.demo

import app.pleosguard.demo.logic.CareData
import app.pleosguard.demo.logic.CareSnapshot
import app.pleosguard.demo.logic.CareStore
import app.pleosguard.demo.logic.Evidence
import app.pleosguard.demo.logic.EvidenceJson
import app.pleosguard.demo.logic.Record
import app.pleosguard.demo.logic.RecordBook
import app.pleosguard.demo.logic.RecordStatus
import app.pleosguard.demo.logic.Review
import app.pleosguard.demo.logic.Visit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 케어 저장(care.json) · 기록 저장 version 2(폰 증거 · 심한 정도 · 나는 때 · 처리 흐름)와 version 1 파일 호환.
 * org.json은 Android 구현이 필요해 Robolectric으로 돌립니다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CareStoreTest {
    private val ev = Evidence("e1", "vibration", 1_794_571_203_000L, 8_000, "바닥에 둠", "앞 타이어", "진동 · 최대 0.38g · 주로 14Hz",
        mapOf("peakG" to 0.38, "dominantHz" to 14.0, "carKmh" to 98.0), (0 until 64).map { it.toFloat() }, listOf(0.1f, 0.3f, 0.2f), candidates = listOf("tpms-low"), note = "시험 입력")

    @Test fun careSnapshotRoundTrip() {
        val s = CareSnapshot(subscribed = setOf("parts"), recallDone = setOf("가상-001"), recallConfirmed = setOf("가상-002"),
            myReviews = listOf(Review("r1", "shop-a", "내 차", 5, mapOf("증상 해결" to 1), listOf("빠름"), "좋음", 10L, mine = true, example = false)),
            visits = listOf(Visit("v1", "shop-a", "정비소 A (예시)", 9L, "소음", reviewed = true)), inbox = listOf(ev), captureSince = 77L,
            leftEnabled = false, rearEnabled = true, carId = "car-1234", pairedPhone = "갤럭시(예시)", leftReduced = true)
        val back = CareStore.from(CareStore.toJson(s))
        assertEquals(s.subscribed, back.subscribed)
        assertEquals(s.recallDone, back.recallDone)
        assertEquals(s.recallConfirmed, back.recallConfirmed)
        assertEquals(s.myReviews, back.myReviews)
        assertEquals(s.visits, back.visits)
        assertEquals(s.inbox, back.inbox)
        assertEquals(77L, back.captureSince)
        assertEquals(false, back.leftEnabled)
        assertEquals("car-1234", back.carId)
        assertEquals("갤럭시(예시)", back.pairedPhone)
        assertEquals(true, back.leftReduced)
    }

    @Test fun brokenOrEmptyCareJsonGivesDefaults() {
        assertEquals(CareSnapshot(), CareStore.from(null))
        assertEquals(CareSnapshot(), CareStore.from("{oops"))
    }

    @Test fun recordVersion2KeepsNewFields() {
        val b = RecordBook()
        b.add(Record(t0 = 0, wall = 5L, drivingAtRecord = false, symptom = "떨림", part = "앞 타이어", frozen = true,
            severity = 3, frequency = "자주", conditions = listOf("고속", "방지턱 · 요철"), status = RecordStatus.SHOWN.name, evidence = listOf(ev)))
        val json = b.toJson()
        assertEquals(2, JSONObject(json).getInt("version"))
        val b2 = RecordBook(); b2.load(json)
        val r = b2.records.single()
        assertEquals(3, r.severity); assertEquals("자주", r.frequency)
        assertEquals(listOf("고속", "방지턱 · 요철"), r.conditions)
        assertEquals(RecordStatus.SHOWN.name, r.status)
        assertEquals(ev, r.evidence.single())
    }

    @Test fun recordVersion1FileStillLoads() {
        val v1 = JSONObject().put("version", 1).put("records", JSONArray().put(JSONObject().put("id", "old").put("wall", 3L).put("driving", true)
            .put("symptom", "소음").put("part", JSONObject.NULL).put("memo", "끼익").put("memoTest", false).put("example", false)
            .put("points", JSONArray()))).toString()
        val b = RecordBook(); b.load(v1)
        val r = b.records.single()
        assertEquals("old", r.id); assertEquals("소음", r.symptom); assertEquals("끼익", r.memo)
        assertEquals(0, r.severity); assertNull(r.frequency); assertTrue(r.conditions.isEmpty())
        assertEquals(RecordStatus.OPEN.name, r.status); assertTrue(r.evidence.isEmpty())
    }

    @Test fun serverCaptureFieldsBecomeEvidence() {
        val o = JSONObject().put("id", "c9").put("kind", "noise").put("startedAt", 42L).put("durationMs", 10_000)
            .put("metrics", JSONObject().put("peakDb", -14.0).put("dominantHz", 180.0)).put("spectrum", JSONArray((0 until 200).map { it.toDouble() }))
            .put("candidates", JSONArray().put(JSONObject().put("id", "engine-check")))
        val e = EvidenceJson.from(o)
        assertEquals(42L, e.wall)
        assertEquals(64, e.spectrum.size)                 // 그래프용으로 64칸까지 줄임(구간 최댓값)
        assertEquals(199f, e.spectrum.last())
        assertEquals(listOf("engine-check"), e.candidates)
        assertEquals("소리 · 최대 -14dBFS · 주로 180Hz", e.summary)  // 소리는 상대 값(dBFS)으로만 씀
        assertEquals("소리 · 최대 -14dBFS · 주로 180Hz", CareData.summarize("noise", e.metrics, emptyList()))
    }
}
