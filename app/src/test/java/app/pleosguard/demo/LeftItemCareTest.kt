package app.pleosguard.demo

import app.pleosguard.demo.logic.LeftItemCare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 짐 알림 규칙(logic/LeftItemCare.kt, 조사 보고서 7.3 · 7.4절).
 * 0.5초 간격으로 신호를 넣는 작은 흉내 차로 시험합니다.
 */
class LeftItemCareTest {
    private class Sim(val care: LeftItemCare = LeftItemCare()) {
        var t = 0L
        var trunk: Int? = 0
        var door: Int? = 0
        var rear: List<Int?> = listOf(0, 0)
        val alerts = mutableListOf<LeftItemCare.Alert>()
        fun run(ms: Long, moving: Boolean) {
            val end = t + ms
            while (t < end) {
                care.update(t, 1_000_000L + t, moving, trunk, door, rear)?.let { alerts += it }
                t += 500
            }
        }
        fun openTrunk() { trunk = 1; run(1_000, false); trunk = 0; run(1_000, false) }
        fun openDoor() { door = 1; run(1_000, false) }
    }

    @Test fun trunkLoadedThenShortTripAlertsWhenDriverDoorOpens() {
        val s = Sim()
        s.openTrunk()
        s.run(60_000, moving = true)          // 1분 주행(짧은 주행)
        s.run(6_000, moving = false)          // 도착(정차 5초 넘음)
        assertTrue("짧은 주행은 문이 열리기 전에는 알리지 않습니다", s.alerts.isEmpty())
        s.openDoor()
        assertEquals(1, s.alerts.size)
        val a = s.alerts.single()
        assertEquals(setOf(LeftItemCare.Kind.TRUNK), a.kinds)
        assertTrue("문이 열릴 때 띄운 카드는 음성도 냅니다", a.voice)
    }

    @Test fun longTripShowsSilentCardOnArrivalAndSpeaksOnceWhenDoorOpens() {
        val s = Sim()
        s.openTrunk()
        s.run(4 * 60_000, moving = true)      // 4분 주행
        s.run(6_000, moving = false)
        assertEquals("3분 넘게 달렸으면 도착하자마자 띄웁니다", 1, s.alerts.size)
        assertFalse("도착 카드는 소리를 내지 않습니다", s.alerts.single().voice)
        assertFalse(s.care.takeSpeak())
        s.openDoor()
        assertTrue("운전석 문이 열리면 음성 한 번", s.care.takeSpeak())
        assertFalse(s.care.takeSpeak())
        assertEquals("한 주행에 한 번만", 1, s.alerts.size)
    }

    @Test fun openingTrunkAfterArrivalMeansUnloadedAndNoAlert() {
        val s = Sim()
        s.openTrunk()
        s.run(60_000, moving = true)
        s.run(6_000, moving = false)
        s.openTrunk()                         // 도착 뒤 트렁크를 엶 = 짐 내림
        assertFalse(s.care.stillNeeded(LeftItemCare.Kind.TRUNK))
        s.openDoor()
        assertTrue(s.alerts.isEmpty())
    }

    @Test fun loadingMoreThanTenMinutesBeforeDepartureIsNotCarried() {
        val s = Sim()
        s.openTrunk()
        s.run(11 * 60_000, moving = false)    // 11분 기다림
        s.run(60_000, moving = true)
        s.run(6_000, moving = false)
        s.openDoor()
        assertTrue(s.alerts.isEmpty())
        assertFalse(s.care.trunkCarried)
    }

    @Test fun noAlertWithoutLoadingOrWhileMoving() {
        val s = Sim()
        s.run(60_000, moving = true)
        s.run(6_000, moving = false)
        s.openDoor()
        assertTrue(s.alerts.isEmpty())
        val s2 = Sim()
        s2.trunk = 1; s2.run(10_000, moving = true); s2.trunk = 0  // 움직이는 중 트렁크 값은 무시
        s2.run(60_000, moving = true); s2.run(6_000, moving = false); s2.openDoor()
        assertTrue(s2.alerts.isEmpty())
    }

    @Test fun rearDoorBeforeTripAsksAboutRearSeat() {
        val s = Sim()
        s.rear = listOf(1, 0); s.run(1_000, false); s.rear = listOf(0, 0); s.run(1_000, false)
        s.run(60_000, moving = true); s.run(6_000, moving = false); s.openDoor()
        assertEquals(setOf(LeftItemCare.Kind.REAR_SEAT), s.alerts.single().kinds)
    }

    @Test fun unknownDriverDoorFallsBackAfterArrival() {
        val s = Sim()
        s.door = null
        s.openTrunk()
        s.run(60_000, moving = true)
        s.run(6_000, moving = false)
        assertTrue(s.alerts.isEmpty())
        s.run(11_000, moving = false)
        assertNotNull(s.alerts.singleOrNull())
    }

    @Test fun threeNoItemsAnswersAskToReduceAndReducedIgnoresShortOpenings() {
        val care = LeftItemCare()
        repeat(3) { care.dismiss(0L, noItems = true) }
        assertTrue(care.askReduce)
        care.answerReduce(true)
        assertFalse(care.askReduce)
        val s = Sim(care)
        s.openTrunk()                         // 1초만 엶: 줄인 상태에서는 세지 않음
        s.run(60_000, moving = true); s.run(6_000, moving = false); s.openDoor()
        assertTrue(s.alerts.isEmpty())
        val s2 = Sim(LeftItemCare().apply { reduced = true })
        s2.trunk = 1; s2.run(25_000, false); s2.trunk = 0; s2.run(1_000, false)  // 25초 엶: 셈
        s2.run(60_000, moving = true); s2.run(6_000, moving = false); s2.openDoor()
        assertEquals(1, s2.alerts.size)
    }

    @Test fun disabledNeverAlerts() {
        val s = Sim(LeftItemCare().apply { enabled = false })
        s.openTrunk(); s.run(60_000, true); s.run(6_000, false); s.openDoor()
        assertNull(s.alerts.firstOrNull())
    }
}
