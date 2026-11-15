package app.pleosguard.demo

import app.pleosguard.demo.logic.Cabin
import app.pleosguard.demo.logic.DriveStats
import app.pleosguard.demo.logic.Record
import app.pleosguard.demo.logic.RecordBook
import app.pleosguard.demo.logic.RecordRules
import app.pleosguard.demo.logic.ServiceNote
import app.pleosguard.demo.logic.SnapPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 증상 기록 묶음(RecordBook): 재현 조건 카드(N2, 같은 증상 3번), 소리 용의자 지우기(H3), 정비 뒤 재발 확인(N6, 500km), 저장(JSON).
 * org.json은 Android 구현이 필요해 Robolectric으로 돌립니다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordBookTest {
    private val day = 24L * 3600 * 1000
    private val now = 1_794_571_203_000L

    /** 기록 순간(dt=0)의 차속과 브레이크만 다른 시간표. */
    private fun pts(kmh: Int?, brake: Boolean?) = listOf(SnapPoint(-1000, kmh?.plus(2), false, brake, 0f, "OFF"), SnapPoint(0, kmh, false, brake, 0f, "OFF"))

    private fun rec(daysAgo: Int, symptom: String?, kmh: Int? = 12, brake: Boolean? = true, cabin: Cabin? = Cabin(false, 0, true, true, false), part: String? = "앞 타이어") =
        Record(t0 = 0, wall = now - daysAgo * day, drivingAtRecord = true, symptom = symptom, part = part, points = pts(kmh, brake), frozen = true, cabin = cabin)

    @Test fun noPatternBelowThreeSameSymptoms() {
        val b = RecordBook()
        b.add(rec(3, "소음")); b.add(rec(2, "소음")); b.add(rec(1, "떨림")); b.add(rec(1, null))
        assertNull(b.pattern())
    }

    @Test fun patternCountsConditionsAtRecordMoment() {
        val b = RecordBook()
        b.add(rec(7, "소음", kmh = 13, brake = true)); b.add(rec(4, "소음", kmh = 15, brake = false)); b.add(rec(0, "소음", kmh = 34, brake = true))
        val p = b.pattern()!!
        assertEquals("소음", p.symptom)
        assertEquals("앞 타이어", p.part)
        assertEquals(RecordRules.SAME_SYMPTOM, p.records.size)
        val low = p.conds[0]; val brk = p.conds[1]
        assertEquals("시속 20km 아래", low.label)
        assertEquals(2, low.hits); assertEquals(3, low.total)
        assertEquals(2, brk.hits)
        // 평소 주행 표본이 600개보다 적으면 문서 예시 값(18%·12%)을 쓰고 "예시"를 붙입니다.
        assertTrue(low.usualExample); assertEquals(18, low.usualPct); assertEquals(12, brk.usualPct)
    }

    @Test fun usualRatiosComeFromDriveStatsWhenEnough() {
        val b = RecordBook()
        repeat(3) { b.add(rec(it, "소음")) }
        b.stats = DriveStats(samples = 1_000, lowSpeed = 250, braking = 90)
        val p = b.pattern()!!
        assertFalse(p.conds[0].usualExample)
        assertEquals(25, p.conds[0].usualPct); assertEquals(9, p.conds[1].usualPct)
    }

    @Test fun picksMostRecentGroupAndLastThree() {
        val b = RecordBook()
        listOf(10, 9, 8).forEach { b.add(rec(it, "떨림")) }
        listOf(6, 5, 4, 1).forEach { b.add(rec(it, "소음")) }
        val p = b.pattern()!!
        assertEquals("소음", p.symptom)
        assertEquals(listOf(now - 5 * day, now - 4 * day, now - 1 * day), p.records.map { it.wall })
    }

    @Test fun cabinStateExcludesSoundSuspects() {
        val b = RecordBook()
        b.add(rec(3, "소음", cabin = Cabin(false, 0, true, true, false)))
        b.add(rec(2, "소음", cabin = Cabin(true, 2, true, true, false)))
        b.add(rec(1, "소음", cabin = Cabin(false, 0, true, true, false)))
        val p = b.pattern()!!
        assertEquals(listOf("에어컨 1번만 켜짐", "창문 평소처럼 닫힘", "와이퍼 평소처럼 꺼짐"), p.excluded)
        assertFalse(p.cabinTest)
    }

    @Test fun recheckAfterServiceNote() {
        val b = RecordBook()
        assertNull(b.recheck("소음", 32_000f))
        b.add(rec(10, "소음"))
        b.notes.add(ServiceNote("소음", "앞 브레이크 패드 교체", now - 5 * day, 32_000f))
        assertEquals(0 to 380f, b.recheck("소음", 32_120f))
        b.add(rec(1, "소음"))
        assertEquals(1 to 0f, b.recheck("소음", 32_700f)) // 500km를 넘으면 0으로 멈춥니다
        assertEquals(1 to null, b.recheck("소음", null)) // 주행거리를 모르면 남은 거리는 없음
    }

    @Test fun jsonRoundTripKeepsFrozenAndExampleRecordsOnly() {
        val b = RecordBook()
        b.add(rec(2, "소음").copy(memo = "끼익 소리", memoTest = true))
        b.add(Record(t0 = 5, wall = now, drivingAtRecord = false)) // 아직 굳지 않은 기록은 저장하지 않습니다
        RecordBook.examples(now).forEach { b.add(it) }
        b.notes.add(ServiceNote("소음", "패드 교체", now - day, null))
        b.stats = DriveStats(700, 100, 50)
        val back = RecordBook().apply { load(b.toJson()) }
        assertEquals(3, back.records.size)
        assertEquals(2, back.records.count { it.example })
        val r = back.records.first { !it.example }
        assertEquals("끼익 소리", r.memo); assertTrue(r.memoTest); assertTrue(r.frozen)
        assertEquals(12, r.points.first { it.dt == 0 }.kmh)
        assertEquals(Cabin(false, 0, true, true, false), r.cabin)
        assertEquals(listOf(ServiceNote("소음", "패드 교체", now - day, null)), back.notes)
        assertEquals(DriveStats(700, 100, 50), back.stats)
    }

    @Test fun examplesAreTwoMarkedRecordsSevenAndFourDaysAgo() {
        val ex = RecordBook.examples(now)
        assertEquals(listOf(now - 7 * day, now - 4 * day), ex.map { it.wall })
        assertTrue(ex.all { it.example && it.symptom == "소음" && it.part == "앞 타이어" && it.cabin?.test == true })
    }

    @Test fun brokenJsonLeavesBookEmpty() {
        val b = RecordBook().apply { load("{not json") }
        assertTrue(b.records.isEmpty())
    }
}
