package app.pleosguard.demo

import app.pleosguard.demo.car3d.CameraFrame
import app.pleosguard.demo.car3d.CarAnchors
import app.pleosguard.demo.car3d.CarPart
import app.pleosguard.demo.car3d.CarState
import app.pleosguard.demo.car3d.CarViewState
import app.pleosguard.demo.car3d.Mat
import app.pleosguard.demo.car3d.Orbit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** 3D 아반떼의 부위 판정(물체 이름 → 부위)과 궤도 카메라 계산. 그래픽 없이 JVM에서 봅니다. */
class Car3dModelTest {

    @Test
    fun nodeNamesMapToParts() {
        val cases = mapOf(
            "Front_Hood" to CarPart.FRONT, "Front_LampBar" to CarPart.FRONT, "Front_Grille" to CarPart.FRONT,
            "Wheel_FL" to CarPart.FRONT_TIRE, "Well_FR" to CarPart.FRONT_TIRE,
            "Wheel_RL" to CarPart.REAR_TIRE, "Well_RR" to CarPart.REAR_TIRE,
            "Door_FL" to CarPart.DOOR, "Door_RR_Handle" to CarPart.DOOR, "DoorTrim_FR" to CarPart.DOOR, "Mirror_L" to CarPart.DOOR,
            "Rear_Trunk" to CarPart.REAR, "Rear_Badge" to CarPart.REAR,
            "Canopy" to CarPart.CABIN, "CanopyGhost" to CarPart.CABIN, "Int_Steering" to CarPart.CABIN, "Seat_RC" to CarPart.CABIN,
            "Belt_FL" to CarPart.CABIN, "Occupant_FR" to CarPart.CABIN, "Airbag_FL" to CarPart.CABIN,
        )
        cases.forEach { (name, part) -> assertEquals(name, part, CarPart.ofNode(name)) }
        listOf("Shadow", "Underbody", "Avante", "", null).forEach { assertNull("$it", CarPart.ofNode(it)) }
    }

    @Test
    fun labelsRoundTripAndKeepRecordValues() {
        CarPart.entries.forEach { assertEquals(it, CarPart.ofLabel(it.label)) }
        // 기존 기록 · 예시 기록의 부위 글자와 같아야 합니다.
        assertEquals(CarPart.FRONT_TIRE, CarPart.ofLabel("앞 타이어"))
        assertEquals(setOf("앞부분", "앞 타이어", "문", "뒤 타이어", "뒤쪽", "실내"), CarPart.entries.map { it.label }.toSet())
    }

    @Test
    fun wholeCarFitsInEveryRotationAndAspect() {
        for (aspect in listOf(0.75, 1.0, 1.25, 1.6, 2.2)) for (pitch in listOf(Orbit.PITCH_MIN, 16.0, 50.0, Orbit.PITCH_MAX)) {
            for (yaw in 0 until 360 step 15) {
                val f = CameraFrame(Orbit(yaw.toDouble(), pitch, 1.0), aspect)
                CarAnchors.bounds.forEach { p ->
                    val q = Mat.project(f.viewProj, p)
                    assertNotNull(q)
                    assertTrue("aspect=$aspect pitch=$pitch yaw=$yaw $p → $q", abs(q!!.first) <= 0.87 && abs(q.second) <= 0.87)
                }
            }
        }
    }

    @Test
    fun homeViewShowsFrontAndDriverSideNotRear() {
        val f = CameraFrame(Orbit.HOME, 1.25)
        val vis = f.visibleAnchors().associate { (a, facing) -> a.part to facing }
        assertTrue("앞부분은 보여야 함", vis.getValue(CarPart.FRONT) > 0.2)
        assertTrue("앞 타이어(운전석 쪽)는 보여야 함", vis.getValue(CarPart.FRONT_TIRE) > 0.2)
        assertTrue("뒤쪽은 가려져야 함", vis.getValue(CarPart.REAR) < 0.0)
        // 운전석 쪽(z < 0) 바퀴 표시점을 고릅니다(앞 왼쪽 사선).
        val tire = f.visibleAnchors().first { it.first.part == CarPart.FRONT_TIRE }.first
        assertTrue(tire.pos.z < 0)
        // 화면 안(0..1)에 들어옵니다.
        val (sx, sy) = f.toScreen(tire.pos)!!
        assertTrue(sx in 0.0..1.0 && sy in 0.0..1.0)
    }

    @Test
    fun cameraTurnsTheShortWayAndClamps() {
        val mid = CarViewState.lerp(Orbit(350.0, 10.0), Orbit(10.0, 10.0), 0.5)
        assertTrue("짧은 쪽(360도 근처)으로 돌아야 함: ${mid.yawDeg}", abs(((mid.yawDeg % 360) + 360) % 360 - 0.0) < 1e-6 || abs(mid.yawDeg - 360.0) < 1e-6)
        val c = Orbit(0.0, 120.0, 9.0).clamped()
        assertEquals(Orbit.PITCH_MAX, c.pitchDeg, 1e-9)
        assertEquals(Orbit.ZOOM_MAX, c.zoom, 1e-9)
    }

    @Test
    fun doorsOpenOutward() {
        // 왼쪽 문(0 운전석, 2 뒤 왼쪽)은 -z(왼쪽)으로, 오른쪽 문은 +z로 열립니다.
        assertTrue(CarState.doorAngle(0, true) < 0 && CarState.doorAngle(2, true) < 0)
        assertTrue(CarState.doorAngle(1, true) > 0 && CarState.doorAngle(3, true) > 0)
        assertEquals(0f, CarState.doorAngle(0, false))
        assertEquals(listOf("FL", "FR", "RL", "RR"), CarState.SEAT_KEYS)
    }
}
