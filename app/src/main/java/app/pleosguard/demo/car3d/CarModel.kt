package app.pleosguard.demo.car3d

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/*
 * 3D 아반떼 모델(assets/models/avante.glb)의 부위 · 카메라 계산. Android 없이 JVM에서 시험합니다.
 *
 * 모델은 demo-app/tools/car3d/avante_model.py(Blender 4.5)로 코드에서 만든 형상입니다.
 * glTF 좌표: x = 차 앞, y = 위, z = 차 오른쪽(운전석은 왼쪽 = z < 0). 단위 m, 바닥 y = 0.
 */

/** 고를 수 있는 부위. [label]은 증상 기록의 '부위' 값과 화면 글자로 그대로 씁니다. */
enum class CarPart(val label: String) {
    FRONT("앞부분"), FRONT_TIRE("앞 타이어"), DOOR("문"), REAR_TIRE("뒤 타이어"), REAR("뒤쪽"), CABIN("실내");

    companion object {
        /** glTF 노드 이름 → 부위. 이름 규칙은 모델 생성기 머리말과 같습니다. */
        fun ofNode(name: String?): CarPart? = when {
            name.isNullOrEmpty() -> null
            name.startsWith("Front_") -> FRONT
            name.startsWith("Wheel_F") || name.startsWith("Well_F") -> FRONT_TIRE
            name.startsWith("Wheel_R") || name.startsWith("Well_R") -> REAR_TIRE
            name.startsWith("Door") || name.startsWith("Mirror_") -> DOOR
            name.startsWith("Rear_") -> REAR
            name.startsWith("Canopy") || name.startsWith("Int_") || name.startsWith("Seat_") || name.startsWith("Belt_") ||
                name.startsWith("Occupant_") || name.startsWith("Airbag_") -> CABIN
            else -> null
        }

        fun ofLabel(label: String?): CarPart? = entries.firstOrNull { it.label == label }
    }
}

data class V3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Double) = V3(x * k, y * k, z * k)
    infix fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun len() = sqrt(this dot this)
    fun norm(): V3 { val l = len(); return if (l < 1e-12) this else this * (1.0 / l) }
}

/** 부위 표시점. 화면에 아이콘을 띄울 자리이며, [normal]이 카메라 쪽을 볼 때만 보입니다. */
data class Anchor(val part: CarPart, val pos: V3, val normal: V3)

object CarAnchors {
    val all: List<Anchor> = listOf(
        Anchor(CarPart.FRONT, V3(2.12, 0.86, 0.0), V3(1.0, 0.35, 0.0)),
        Anchor(CarPart.FRONT_TIRE, V3(1.375, 0.34, -0.93), V3(0.0, 0.1, -1.0)),
        Anchor(CarPart.FRONT_TIRE, V3(1.375, 0.34, 0.93), V3(0.0, 0.1, 1.0)),
        Anchor(CarPart.DOOR, V3(0.42, 0.64, -0.935), V3(0.0, 0.15, -1.0)),
        Anchor(CarPart.DOOR, V3(0.42, 0.64, 0.935), V3(0.0, 0.15, 1.0)),
        Anchor(CarPart.REAR_TIRE, V3(-1.375, 0.34, -0.93), V3(0.0, 0.1, -1.0)),
        Anchor(CarPart.REAR_TIRE, V3(-1.375, 0.34, 0.93), V3(0.0, 0.1, 1.0)),
        Anchor(CarPart.REAR, V3(-2.40, 0.80, 0.0), V3(-1.0, 0.25, 0.0)), // 5회 모델: 그 높이의 뒷면이 x −2.35(2026-11-14)
        Anchor(CarPart.CABIN, V3(-0.30, 1.43, 0.0), V3(0.0, 1.0, 0.0)), // 5회 모델: 지붕 높이 1.423m
        Anchor(CarPart.CABIN, V3(-0.15, 1.16, -0.74), V3(0.0, 0.35, -1.0)),
        Anchor(CarPart.CABIN, V3(-0.15, 1.16, 0.74), V3(0.0, 0.35, 1.0)),
    )

    /** 차 전체를 감싸는 상자(거울 포함). 카메라 거리 맞춤에 씁니다. */
    val bounds: List<V3> = buildList {
        for (x in listOf(-2.47, 2.37)) for (y in listOf(0.0, 1.47)) for (z in listOf(-1.0, 1.0)) add(V3(x, y, z))
    }
}

/**
 * 궤도 카메라. yaw = 0이면 차 앞(+x)에서 보고, 양수로 갈수록 차 왼쪽(운전석, -z)으로 돕니다.
 * pitch는 위에서 내려다보는 각도, zoom은 맞춤 거리의 배수(작을수록 가까움).
 */
data class Orbit(val yawDeg: Double, val pitchDeg: Double, val zoom: Double = 1.0, val target: V3 = V3(-0.05, 0.62, 0.0)) {
    fun clamped() = copy(pitchDeg = pitchDeg.coerceIn(PITCH_MIN, PITCH_MAX), zoom = zoom.coerceIn(ZOOM_MIN, ZOOM_MAX))

    fun dir(): V3 {
        val y = Math.toRadians(yawDeg); val p = Math.toRadians(pitchDeg)
        return V3(cos(p) * cos(y), sin(p), -cos(p) * sin(y))
    }

    companion object {
        const val PITCH_MIN = 3.0
        const val PITCH_MAX = 80.0
        const val ZOOM_MIN = 0.55
        const val ZOOM_MAX = 1.5
        const val FOV_Y = 28.0

        /** 앱이 쓰던 공식 사진과 비슷한 앞 왼쪽 사선. */
        val HOME = Orbit(38.0, 16.0, 0.86)
        /** 실내 보기: 덮개를 반투명으로 하고 운전석 쪽(왼쪽) 앞 위에서. 좌석 앞면 · 안전벨트 · 운전대가 보입니다. */
        val INTERIOR = Orbit(46.0, 50.0, 0.74, V3(-0.25, 0.62, 0.0))
        /** 보호자 알림의 사고 순간 차 상태(실내 보기와 같은 자리라 미리 그린 그림 하나를 같이 씁니다). */
        val GUARDIAN = INTERIOR

        /** 부위 칩을 누르면 그 부위가 잘 보이는 쪽으로 돕니다. */
        fun focus(part: CarPart): Orbit = when (part) {
            CarPart.FRONT -> Orbit(22.0, 14.0, 0.86)
            CarPart.FRONT_TIRE -> Orbit(62.0, 9.0, 0.86)
            CarPart.DOOR -> Orbit(88.0, 10.0, 0.86)
            CarPart.REAR_TIRE -> Orbit(118.0, 9.0, 0.86)
            CarPart.REAR -> Orbit(158.0, 15.0, 0.86)
            CarPart.CABIN -> INTERIOR
        }
    }
}

/** 열 우선(column-major) 4×4 행렬 계산. Filament Camera와 같은 원근 · lookAt 정의입니다. */
object Mat {
    fun lookAt(eye: V3, target: V3, up: V3 = V3(0.0, 1.0, 0.0)): DoubleArray {
        val f = (target - eye).norm(); val s = (f cross up).norm(); val u = s cross f
        return doubleArrayOf(
            s.x, u.x, -f.x, 0.0,
            s.y, u.y, -f.y, 0.0,
            s.z, u.z, -f.z, 0.0,
            -(s dot eye), -(u dot eye), (f dot eye), 1.0,
        )
    }

    fun perspective(fovYDeg: Double, aspect: Double, near: Double, far: Double): DoubleArray {
        val f = 1.0 / tan(Math.toRadians(fovYDeg) / 2)
        return doubleArrayOf(
            f / aspect, 0.0, 0.0, 0.0,
            0.0, f, 0.0, 0.0,
            0.0, 0.0, (far + near) / (near - far), -1.0,
            0.0, 0.0, 2 * far * near / (near - far), 0.0,
        )
    }

    fun mul(a: DoubleArray, b: DoubleArray): DoubleArray {
        val r = DoubleArray(16)
        for (c in 0 until 4) for (rr in 0 until 4) {
            var s = 0.0
            for (k in 0 until 4) s += a[k * 4 + rr] * b[c * 4 + k]
            r[c * 4 + rr] = s
        }
        return r
    }

    /** 점 → NDC(x, y는 -1..1, 위가 +). 카메라 뒤면 null. */
    fun project(m: DoubleArray, p: V3): Pair<Double, Double>? {
        val x = m[0] * p.x + m[4] * p.y + m[8] * p.z + m[12]
        val y = m[1] * p.x + m[5] * p.y + m[9] * p.z + m[13]
        val w = m[3] * p.x + m[7] * p.y + m[11] * p.z + m[15]
        if (w <= 1e-6) return null
        return x / w to y / w
    }
}

/** 한 화면(가로 · 세로 비율)에서 궤도 카메라가 놓일 자리와 행렬. */
class CameraFrame(val orbit: Orbit, val aspect: Double) {
    val distance: Double = fitDistance(orbit, aspect) * orbit.zoom
    val eye: V3 = orbit.target + orbit.dir() * distance
    val viewProj: DoubleArray = Mat.mul(Mat.perspective(Orbit.FOV_Y, aspect, NEAR, FAR), Mat.lookAt(eye, orbit.target))

    /** 점 → 화면 비율 좌표(0..1, 왼쪽 위 기준). */
    fun toScreen(p: V3): Pair<Double, Double>? = Mat.project(viewProj, p)?.let { (x, y) -> (x + 1) / 2 to (1 - y) / 2 }

    /** 카메라를 향한 정도(-1..1). 0보다 작으면 뒤쪽이라 가립니다. */
    fun facing(a: Anchor): Double = (eye - a.pos).norm() dot a.normal.norm()

    /** 부위마다 카메라를 가장 잘 보는 표시점 하나. */
    fun visibleAnchors(): List<Pair<Anchor, Double>> =
        CarAnchors.all.groupBy { it.part }.map { (_, list) -> list.map { it to facing(it) }.maxBy { it.second } }

    companion object {
        const val NEAR = 0.1
        const val FAR = 60.0
        private const val MARGIN = 0.86

        /**
         * 차 전체가 화면에 들어가는 거리. 처음 돌릴 때 크기가 출렁이지 않도록 같은 높이(pitch)에서 한 바퀴(30도 간격)
         * 돌려 본 가장 먼 거리를 씁니다.
         */
        fun fitDistance(o: Orbit, aspect: Double): Double {
            var worst = 0.0
            for (k in 0 until 12) worst = max(worst, fitOne(o.copy(yawDeg = k * 30.0, zoom = 1.0), aspect))
            return max(worst, fitOne(o.copy(zoom = 1.0), aspect))
        }

        private fun fitOne(o: Orbit, aspect: Double): Double {
            var lo = 1.0; var hi = 40.0
            repeat(32) {
                val mid = (lo + hi) / 2
                val eye = o.target + o.dir() * mid
                val m = Mat.mul(Mat.perspective(Orbit.FOV_Y, aspect, NEAR, FAR), Mat.lookAt(eye, o.target))
                val fits = CarAnchors.bounds.all { p ->
                    val q = Mat.project(m, p)
                    q != null && abs(q.first) <= MARGIN && abs(q.second) <= MARGIN
                }
                if (fits) hi = mid else lo = mid
            }
            return hi
        }

        /** 가로 화각(도). 세로 창에서 차가 잘리는지 볼 때 씁니다. */
        fun fovX(aspect: Double) = Math.toDegrees(2 * atan(tan(Math.toRadians(Orbit.FOV_Y) / 2) * aspect))
    }
}

/**
 * 사고 순간 차 상태(보호자 알림). 좌석 · 벨트 · 문은 [IncidentSnap]과 같은 순서
 * (0 운전석 = 앞 왼쪽, 1 조수석, 2 뒤 왼쪽, 3 뒤 오른쪽). null = 확인 불가.
 */
data class CarState(
    val seats: List<Boolean?> = List(4) { null },
    val belts: List<Boolean?> = List(4) { null },
    val doors: List<Int?> = List(4) { null },
    val airbag: Boolean = false,
) {
    companion object {
        /** 좌석 순서 → 모델 이름 꼬리(Seat_FL 등). 가운데 뒷좌석(RC)은 신호가 없어 상태를 칠하지 않습니다. */
        val SEAT_KEYS = listOf("FL", "FR", "RL", "RR")
        /** 문 순서 → 열리는 방향(왼쪽 문은 -z쪽으로 열려 y축 기준 음의 각도). */
        fun doorAngle(i: Int, open: Boolean): Float = if (!open) 0f else if (i % 2 == 0) -55f else 55f
    }
}
