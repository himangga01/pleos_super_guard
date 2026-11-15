package app.pleosguard.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 사건 순간 차 상태 문장(SnapText). 값이 없으면 "확인 불가"이고, 없음을 닫힘·미착용으로 바꾸지 않는지 봅니다. */
class SnapTextTest {
    private val wall = 1_794_571_203_000L // 2026-11-13 21:00:03 KST

    private fun snap(seats: List<Boolean?> = List(4) { null }, belts: List<Boolean?> = List(4) { null }, doors: List<Int?> = List(4) { null },
                     locWall: Long? = null, measured: Boolean = true) =
        IncidentSnap(seats = seats, belts = belts, doors = doors, locWall = locWall, locMeasured = measured)

    @Test fun unknownValuesStayUnknown() {
        val s = snap()
        assertEquals("확인 불가", SnapText.occupants(s))
        assertEquals("확인 불가", SnapText.belts(s))
        assertEquals("확인 불가", SnapText.doors(s))
        assertEquals("위치 확인 불가", SnapText.locTime(s, wall))
    }

    @Test fun occupants() {
        assertEquals("운전석 1명", SnapText.occupants(snap(seats = listOf(true, false, false, false))))
        assertEquals("2명(운전석·조수석)", SnapText.occupants(snap(seats = listOf(true, true, false, false))))
        assertEquals("4명(모든 좌석)", SnapText.occupants(snap(seats = listOf(true, true, true, true))))
        assertEquals("탑승 감지 없음", SnapText.occupants(snap(seats = listOf(false, false, false, false))))
    }

    @Test fun beltsFollowOccupiedSeats() {
        // 탑승을 모르면 운전석을 봅니다.
        assertEquals("운전석 착용", SnapText.belts(snap(belts = listOf(true, null, null, null))))
        assertEquals("운전석 미착용", SnapText.belts(snap(seats = listOf(true, false, false, false), belts = listOf(false, false, false, false))))
        assertEquals("모두 착용", SnapText.belts(snap(seats = listOf(true, true, false, false), belts = listOf(true, true, false, false))))
        assertEquals("모두 미착용", SnapText.belts(snap(seats = listOf(true, true, false, false), belts = listOf(false, false, true, true))))
        assertEquals("조수석 미착용 · 나머지 착용", SnapText.belts(snap(seats = listOf(true, true, false, false), belts = listOf(true, false, null, null))))
        assertEquals("확인 불가", SnapText.belts(snap(seats = listOf(true, true, false, false), belts = listOf(null, null, true, true))))
    }

    @Test fun doors() {
        assertEquals("모두 닫힘", SnapText.doors(snap(doors = listOf(0, 0, 0, 0))))
        assertEquals("뒤 왼쪽 열림", SnapText.doors(snap(doors = listOf(0, 0, 1, 0))))
        assertEquals("닫힘(일부 확인 불가)", SnapText.doors(snap(doors = listOf(0, null, 0, 0))))
    }

    @Test fun carLineJoinsThreeParts() {
        val s = snap(seats = listOf(true, false, false, false), belts = listOf(true, false, false, false), doors = listOf(0, 0, 0, 0))
        assertEquals("탑승 운전석 1명 · 안전벨트 운전석 착용 · 문 모두 닫힘", SnapText.car(s))
    }

    @Test fun unbuckledAmongOccupiedSeats() {
        assertEquals(true, SnapText.anyUnbuckled(snap(seats = listOf(true, true, false, false), belts = listOf(true, false, null, null))))
        assertEquals(false, SnapText.anyUnbuckled(snap(seats = listOf(true, false, false, false), belts = listOf(true, false, false, false))))
        assertEquals(null, SnapText.anyUnbuckled(snap()))
    }

    @Test fun addressAndCoordinateSourceGap() {
        val pangyo = IncidentSnap(lat = 37.4127, lng = 127.0947, naviLat = 37.4127, naviLng = 127.0947)
        assertEquals(0.0, SnapText.sourceGapKm(pangyo)!!, 0.001)
        // 에뮬레이터에서 본 어긋남: FusedLocation 기본 좌표(미국)와 내비 위치(판교)
        val gap = SnapText.sourceGapKm(pangyo.copy(lat = 37.4220, lng = -122.0840))!!
        assertTrue("$gap", gap in 9_000.0..10_000.0)
        assertEquals(null, SnapText.sourceGapKm(pangyo.copy(naviLat = null)))
    }

    @Test fun locationTimeAndAge() {
        assertEquals("측정 시각 21:00:01", SnapText.locTime(snap(locWall = wall - 2_000), wall))
        assertEquals("받은 시각 21:00:01", SnapText.locTime(snap(locWall = wall - 2_000, measured = false), wall))
        // 사건보다 60초 넘게 앞선 위치는 오래된 위치라고 붙입니다(PRD 위치 나이 60초). 딱 60초는 아직 오래되지 않은 것으로 봅니다.
        assertEquals("측정 시각 20:59:03", SnapText.locTime(snap(locWall = wall - 60_000), wall))
        assertEquals("측정 시각 20:59:02 · 1분 전 위치(오래됨)", SnapText.locTime(snap(locWall = wall - 61_000), wall))
        val old = SnapText.locTime(snap(locWall = wall - 89 * 60_000L), wall)
        assertTrue(old, old.endsWith("89분 전 위치(오래됨)"))
    }
}
