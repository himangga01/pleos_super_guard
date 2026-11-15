package app.pleossupercare.phone.sensing

import android.content.Context
import app.pleossupercare.phone.logic.VibrationAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * "시험 입력 재생": 앱에 넣은 합성 떨림 파일(assets/samples/vibration_test_14hz.csv)을 실제 기록처럼 흘려보냅니다.
 * 에뮬레이터의 가상 센서는 진짜 진동을 만들지 못하므로(폰 센서 조사 7.5절) 시연은 이 재생을 씁니다.
 * 이렇게 만든 기록은 note를 "시험 입력"으로 보내고, 차 앱은 시험 입력 꼬리표를 붙입니다.
 */
object VibrationReplay {
    const val ASSET = "samples/vibration_test_14hz.csv"

    class Sample(val rateHz: Int, val x: FloatArray, val y: FloatArray, val z: FloatArray, val description: String)

    suspend fun load(context: Context): Sample = withContext(Dispatchers.IO) {
        var rate = 400
        var desc = "시험 입력"
        val xs = ArrayList<Float>(12_000); val ys = ArrayList<Float>(12_000); val zs = ArrayList<Float>(12_000)
        context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                when {
                    line.startsWith("# rate=") -> rate = line.substringAfter("=").trim().toIntOrNull() ?: rate
                    line.startsWith("# desc=") -> desc = line.substringAfter("=").trim()
                    line.startsWith("#") || line.isBlank() -> Unit
                    else -> {
                        val p = line.split(',')
                        if (p.size >= 3) { xs += p[0].toFloat(); ys += p[1].toFloat(); zs += p[2].toFloat() }
                    }
                }
            }
        }
        Sample(rate, xs.toFloatArray(), ys.toFloatArray(), zs.toFloatArray(), desc)
    }

    /** [durationMs] 동안 실제 시간에 맞춰 흘려보내며 [onLive]를 부르고, 같은 길이의 표본을 돌려줍니다. */
    suspend fun replay(sample: Sample, durationMs: Long, stop: () -> Boolean, onLive: (elapsedMs: Long, magG: Double) -> Unit): MotionData {
        val total = minOf(sample.x.size, (sample.rateHz * durationMs / 1000).toInt())
        val started = System.currentTimeMillis()
        val dtNs = 1_000_000_000L / sample.rateHz
        val t0 = System.nanoTime()
        var shown = 0
        while (!stop()) {
            val el = System.currentTimeMillis() - started
            val upto = minOf(total, (el * sample.rateHz / 1000).toInt())
            var mag = 0.0
            for (i in shown until upto) {
                val dz = sample.z[i] - VibrationAnalyzer.G0
                val m = sqrt((sample.x[i] * sample.x[i] + sample.y[i] * sample.y[i] + dz * dz).toDouble()) / VibrationAnalyzer.G0
                if (m > mag) mag = m
            }
            shown = upto
            onLive(minOf(el, durationMs), mag)
            if (upto >= total) break
            delay(100)
        }
        val n = shown
        val t = LongArray(n) { t0 + it * dtNs }
        return MotionData(t, sample.x.copyOf(n), sample.y.copyOf(n), sample.z.copyOf(n), n, null, started, "시험 입력 재생", 2_500, 2_500, true)
    }
}
