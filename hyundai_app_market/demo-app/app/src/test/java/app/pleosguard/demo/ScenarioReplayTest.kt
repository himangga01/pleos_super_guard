package app.pleosguard.demo

import app.pleosguard.demo.logic.AccelCandidateDetector
import app.pleosguard.demo.logic.ConfirmPromptDetector
import app.pleosguard.demo.logic.ConfirmPromptDetector.Decision
import app.pleosguard.demo.logic.Quality
import app.pleosguard.demo.logic.RulesLab
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 급발진 조사로 정한 Virtual Console 시나리오(demo-app/scenarios/sua-scenarios.json)를
 * 재생기(scripts/vc_scenario.py)와 같은 물리식으로 0.1초 시간표로 만들고, 앱 판정 규칙에 그대로 넣어 기대 결과를 확인합니다.
 * 에뮬레이터에서는 차속이 정수 km/h로 오므로 여기서도 반올림한 값을 넣습니다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScenarioReplayTest {
    private data class Row(val t: Double, val gear: String, val acc: Double, val brk: Double, val kph: Double)

    private fun scenarios(): List<JSONObject> {
        val f = listOf(File("../scenarios/sua-scenarios.json"), File("scenarios/sua-scenarios.json")).first { it.exists() }
        val arr = JSONObject(f.readText(Charsets.UTF_8)).getJSONArray("scenarios")
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }

    /** scripts/vc_scenario.py simulate()와 같은 식입니다. */
    private fun simulate(sc: JSONObject): List<Row> {
        val steps = sc.getJSONArray("steps").let { a -> (0 until a.length()).map { a.getJSONObject(it) } }.sortedBy { it.getDouble("t") }
        val end = if (sc.has("duration_s")) sc.getDouble("duration_s") else steps.last().getDouble("t") + 3.0
        val replay = sc.optString("mode", "physics") == "replay"
        val keys = steps.filter { it.has("speed_kph") }.map { it.getDouble("t") to it.getDouble("speed_kph") }
        var gear = "Park"; var acc = 0.0; var brk = 0.0; var v = 0.0
        val out = mutableListOf<Row>(); var i = 0; var k = 0
        while (k * 0.1 <= end + 1e-9) {
            val t = Math.round(k * 0.1 * 10) / 10.0
            while (i < steps.size && steps[i].getDouble("t") <= t + 1e-9) {
                val s = steps[i]
                if (s.has("gear")) gear = s.getString("gear")
                if (s.has("accelerator")) acc = s.getDouble("accelerator")
                if (s.has("brake")) brk = s.getDouble("brake")
                if (s.has("speed_kph") && !replay) v = s.getDouble("speed_kph")
                i++
            }
            if (gear == "Park") v = 0.0
            else if (replay) v = keyframeSpeed(keys, t)
            else {
                val drive = gear == "Drive" || gear == "Reverse"
                val factor = if (gear == "Reverse") 0.45 else 1.0
                val driveAccel = if (drive) acc / 100.0 * 19.0 * factor else 0.0
                val brakeDecel = brk / 100.0 * 42.0
                val coast = if (v > 0) 1.2 + v * 0.035 else 0.0
                v = max(0.0, v + (driveAccel - brakeDecel - coast) * 0.1); if (v < 0.35 && acc == 0.0) v = 0.0
            }
            out += Row(t, gear, acc, brk, v)
            k++
        }
        return out
    }

    /** scripts/vc_scenario.py keyframe_speed()와 같습니다. */
    private fun keyframeSpeed(keys: List<Pair<Double, Double>>, t: Double): Double {
        if (t <= keys.first().first) return keys.first().second
        for ((a, b) in keys.zipWithNext()) if (t >= a.first && t <= b.first) return if (b.first > a.first) a.second + (b.second - a.second) * (t - a.first) / (b.first - a.first) else b.second
        return keys.last().second
    }

    @Test fun scenariosMatchExpectedAppBehaviour() {
        val list = scenarios()
        assertTrue("시나리오가 없습니다", list.isNotEmpty())
        for (sc in list) {
            val id = sc.getString("id")
            val expect = sc.getJSONObject("expect")
            val minStart = if (expect.has("min_prompt_start_kph")) (expect.getDouble("min_prompt_start_kph") / 3.6).toFloat() else RulesLab.MIN_PROMPT_START_MPS
            val prompt = ConfirmPromptDetector(minStartSpeed = minStart)
            val accel = AccelCandidateDetector()
            var prompted: Double? = null; var candidate: Double? = null; var lowSpeed = false; var change: Float? = null
            for (r in simulate(sc)) {
                val ms = (r.t * 1000).roundToInt().toLong()
                val v = (r.kph.roundToInt() / 3.6).toFloat()            // 콘솔은 정수 km/h로 보냅니다
                val brake = r.brk > 0                                    // 콘솔: 브레이크 위치에서 밟음 여부를 만듭니다
                if (accel.onSample(ms, v, Quality.OK) && candidate == null) candidate = r.t
                when (prompt.onSample(ms, v, Quality.OK, brake)) {
                    Decision.PROMPT -> if (prompted == null) { prompted = r.t; change = prompt.lastSpeedChange }
                    Decision.LOW_SPEED -> lowSpeed = true
                    else -> Unit
                }
            }
            assertEquals("$id: 확인 질문", expect.getBoolean("prompt"), prompted != null)
            assertEquals("$id: 급가속 후보", expect.getBoolean("accel_candidate"), candidate != null)
            if (expect.has("low_speed_skip")) assertEquals("$id: 저속 구간 건너뜀", expect.getBoolean("low_speed_skip"), lowSpeed)
            if (expect.has("prompt_reason") && prompted != null) {
                val rose = (change ?: 0f) >= RulesLab.MIN_SPEED_DROP_MPS
                assertEquals("$id: 질문 이유", expect.getString("prompt_reason"), if (rose) "rose" else "flat")
            }
            if (expect.has("prompt_by_s") && prompted != null) assertTrue("$id: ${prompted}초에 질문(기한 ${expect.getDouble("prompt_by_s")}초)", prompted!! <= expect.getDouble("prompt_by_s"))
            println("$id: 질문 ${prompted ?: "-"}초, 급가속 후보 ${candidate ?: "-"}초, 저속 건너뜀 $lowSpeed, 속도 변화 ${change ?: "-"} m/s")
        }
    }
}
