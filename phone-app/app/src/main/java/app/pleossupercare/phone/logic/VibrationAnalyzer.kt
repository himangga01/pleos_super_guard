package app.pleossupercare.phone.logic

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 봉우리 하나: 주파수(Hz), 크기(g, RMS), 주변 중앙값보다 몇 배 큰지. */
data class Peak(val hz: Double, val g: Double, val ratio: Double)

/**
 * 떨림 기록 분석 결과(실험 기능). 값은 폰 센서 조사(docs/analysis/phone-sensing-research-2026-11-14.md) 7.2절을 따릅니다.
 * 크기는 중력을 뺀 가속도(g = 9.80665m/s²)이고 폰 기종과 놓는 자리에 따라 달라집니다. 위험 · 안전 판정을 하지 않습니다.
 */
data class VibrationResult(
    /** 타임스탬프 간격의 중앙값으로 잰 실제 표본 주기(Hz). */
    val sampleHz: Double,
    /** 고른 간격으로 바꾼 격자 주기(400 · 200 · 100 · 50Hz). */
    val gridHz: Int,
    val samples: Int,
    val durationMs: Long,
    val rmsG: Double,
    val peakG: Double,
    val dominantHz: Double,
    /** 1~5Hz · 5~20Hz · 20~80Hz RMS(g). 위 끝은 표본 주기의 0.4배 이하이고, band3은 격자 200Hz 이상일 때만. */
    val band1: Double, val band2: Double, val band3: Double?,
    val peaks: List<Peak>,
    /** 0~[spectrumMaxHz]를 64칸으로 나눈 칸별 크기(g, RMS). */
    val spectrum: List<Float>,
    val spectrumHz: List<Float>,
    val spectrumMaxHz: Double,
    /** 시간에 따른 크기(g, RMS), 200점 이하. */
    val series: List<Float>,
    val seriesStepMs: Int,
    /** 시작 1초의 중력 방향(g)과 화면이 위를 보는 정도에서 벗어난 각도. */
    val gravityX: Double, val gravityY: Double, val gravityZ: Double, val tiltDeg: Double,
    val windows: Int, val movedWindows: Int, val gappedWindows: Int,
    /** OK · LOW_RATE · MOVED · GAPPED · TOO_SHORT */
    val quality: String,
    val lowQuality: Boolean,
)

object VibrationAnalyzer {
    const val G0 = 9.80665
    const val WINDOW_SEC = 2.56
    const val BINS = 64
    const val SERIES_MAX = 200
    /** 이보다 느리면 기록 품질 낮음(CDD 최소 100Hz). */
    const val LOW_RATE_HZ = 100.0
    /** 시작 때보다 중력 방향이 이만큼 넘게 바뀐 창은 뺍니다(폰이 움직임). */
    const val MOVED_DEG = 5.0

    fun gridFor(sampleHz: Double) = when {
        sampleHz >= 380 -> 400
        sampleHz >= 190 -> 200
        sampleHz >= 95 -> 100
        else -> 50
    }

    fun analyze(t: LongArray, x: FloatArray, y: FloatArray, z: FloatArray, n: Int): VibrationResult {
        val count = minOf(n, t.size, x.size, y.size, z.size)
        if (count < 16) return empty(count, "TOO_SHORT")
        val dts = LongArray(count - 1) { t[it + 1] - t[it] }.filter { it > 0 }.sorted()
        if (dts.isEmpty()) return empty(count, "TOO_SHORT")
        val medDt = dts[dts.size / 2].toDouble()
        val sampleHz = 1e9 / medDt
        val fs = gridFor(sampleHz)
        val gapNs = max(10_000_000.0, 2.5 * medDt)

        // 고른 간격(fs) 격자로 선형 보간. 빈틈 구간에 든 격자 점을 표시합니다.
        val t0 = t[0]
        val span = (t[count - 1] - t0) / 1e9
        val m = (span * fs).toInt() + 1
        val gx = DoubleArray(m); val gy = DoubleArray(m); val gz = DoubleArray(m)
        val gap = BooleanArray(m)
        var i = 0
        for (j in 0 until m) {
            val tj = t0 + (j * 1e9 / fs).toLong()
            while (i < count - 2 && t[i + 1] < tj) i++
            val ta = t[i]; val tb = t[i + 1]
            val u = if (tb > ta) ((tj - ta).toDouble() / (tb - ta)).coerceIn(0.0, 1.0) else 0.0
            gx[j] = x[i] + (x[i + 1] - x[i]) * u
            gy[j] = y[i] + (y[i + 1] - y[i]) * u
            gz[j] = z[i] + (z[i + 1] - z[i]) * u
            gap[j] = (tb - ta) > gapNs
        }

        // 시작 1초의 중력 방향
        val head = minOf(m, fs)
        val g0x = gx.take(head).average(); val g0y = gy.take(head).average(); val g0z = gz.take(head).average()
        val gNorm = sqrt(g0x * g0x + g0y * g0y + g0z * g0z).coerceAtLeast(1e-9)
        val tilt = Math.toDegrees(acos((abs(g0z) / gNorm).coerceIn(0.0, 1.0)))

        // 2.56초 Hann 창, 50% 겹침. 창마다 평균(중력)을 빼고 세 축 스펙트럼을 더합니다.
        val len = Dsp.floorPow2((WINDOW_SEC * fs).roundToInt())
        val hop = len / 2
        val w = Dsp.hann(len)
        val sw2 = w.sumOf { it * it }
        val psd = DoubleArray(len / 2 + 1)
        val re = DoubleArray(len); val im = DoubleArray(len)
        var used = 0; var moved = 0; var gapped = 0; var windows = 0
        var refX = Double.NaN; var refY = 0.0; var refZ = 0.0
        val validSample = BooleanArray(m)
        var s = 0
        while (s + len <= m) {
            windows++
            if ((s until s + len).any { gap[it] }) { gapped++; s += hop; continue }
            var mx = 0.0; var my = 0.0; var mz = 0.0
            for (k in s until s + len) { mx += gx[k]; my += gy[k]; mz += gz[k] }
            mx /= len; my /= len; mz /= len
            if (refX.isNaN()) { refX = mx; refY = my; refZ = mz }
            if (angleDeg(refX, refY, refZ, mx, my, mz) > MOVED_DEG) { moved++; s += hop; continue }
            for ((axis, mean) in listOf(gx to mx, gy to my, gz to mz)) {
                for (k in 0 until len) { re[k] = (axis[s + k] - mean) * w[k]; im[k] = 0.0 }
                Dsp.fft(re, im)
                for (k in 0..len / 2) psd[k] += re[k] * re[k] + im[k] * im[k]
            }
            for (k in s until s + len) validSample[k] = true
            used++
            s += hop
        }
        val bin = fs.toDouble() / len
        if (used > 0) {
            val norm = 1.0 / (used * len * sw2 * G0 * G0)
            for (k in 0..len / 2) psd[k] *= norm * (if (k == 0 || k == len / 2) 1.0 else 2.0)
        }
        val top = 0.4 * fs
        fun bandRms(lo: Double, hi: Double): Double {
            var acc = 0.0
            for (k in psd.indices) { val f = k * bin; if (f >= lo && f < minOf(hi, top)) acc += psd[k] }
            return sqrt(acc)
        }
        val peaks = findPeaks(psd, bin, 1.0, top, 3)

        // 시간 영역 크기: 1초 이동 평균을 빼 중력 · 느린 기울기를 없앤 뒤 크기(g)
        val dyn = DoubleArray(m)
        val win = max(1, fs)
        val px = prefix(gx); val py = prefix(gy); val pz = prefix(gz)
        for (k in 0 until m) {
            val a = max(0, k - win / 2); val b = minOf(m, k + win / 2 + 1)
            val c = (b - a).toDouble()
            val dx = gx[k] - (px[b] - px[a]) / c; val dy = gy[k] - (py[b] - py[a]) / c; val dz = gz[k] - (pz[b] - pz[a]) / c
            dyn[k] = sqrt(dx * dx + dy * dy + dz * dz) / G0
        }
        val useIdx = (0 until m).filter { if (used > 0) validSample[it] else !gap[it] }
        val rms = if (useIdx.isNotEmpty()) sqrt(useIdx.sumOf { dyn[it] * dyn[it] } / useIdx.size) else 0.0
        val peak = useIdx.maxOfOrNull { dyn[it] } ?: 0.0

        val stepSec = max(0.1, span / SERIES_MAX)
        val per = max(1, (stepSec * fs).roundToInt())
        val series = (0 until m step per).map { a ->
            val b = minOf(m, a + per)
            sqrt((a until b).sumOf { dyn[it] * dyn[it] } / (b - a)).toFloat()
        }.take(SERIES_MAX)

        val maxHz = minOf(100.0, top)
        val (bands, centers) = Dsp.bandSum(psd, bin, maxHz, BINS)
        val quality = when {
            used == 0 && moved > 0 -> "MOVED"
            used == 0 -> "GAPPED"
            sampleHz < LOW_RATE_HZ -> "LOW_RATE"
            moved > 0 -> "MOVED"
            gapped > 0 -> "GAPPED"
            else -> "OK"
        }
        return VibrationResult(
            sampleHz = sampleHz, gridHz = fs, samples = count, durationMs = (span * 1000).toLong(),
            rmsG = rms, peakG = peak, dominantHz = peaks.firstOrNull()?.hz ?: 0.0,
            band1 = bandRms(1.0, 5.0), band2 = bandRms(5.0, 20.0), band3 = if (fs >= 200) bandRms(20.0, 80.0) else null,
            peaks = peaks,
            spectrum = bands.map { sqrt(it).toFloat() }, spectrumHz = centers.map { it.toFloat() }, spectrumMaxHz = maxHz,
            series = series, seriesStepMs = (per * 1000.0 / fs).roundToInt(),
            gravityX = g0x / G0, gravityY = g0y / G0, gravityZ = g0z / G0, tiltDeg = tilt,
            windows = windows, movedWindows = moved, gappedWindows = gapped,
            quality = quality, lowQuality = sampleHz < LOW_RATE_HZ,
        )
    }

    /** 센 봉우리 [count]개(강한 순). 포물선 보간한 주파수, 주봉우리 칸 3개의 RMS(g), 주변 ±5Hz 중앙값 대비 배수. */
    fun findPeaks(psd: DoubleArray, bin: Double, loHz: Double, hiHz: Double, count: Int): List<Peak> {
        val k0 = max(2, (loHz / bin).toInt()); val k1 = minOf(psd.size - 3, (hiHz / bin).toInt())
        if (k1 <= k0) return emptyList()
        val local = (k0..k1).filter { psd[it] > 1e-24 && psd[it] >= psd[it - 1] && psd[it] > psd[it + 1] }.sortedByDescending { psd[it] }
        val out = mutableListOf<Peak>()
        val span = max(3, (5.0 / bin).toInt())
        for (k in local) {
            val a = ln(psd[k - 1] + 1e-30); val b = ln(psd[k] + 1e-30); val c = ln(psd[k + 1] + 1e-30)
            val den = a - 2 * b + c
            val f = (k + if (den != 0.0) (0.5 * (a - c) / den).coerceIn(-0.5, 0.5) else 0.0) * bin
            if (out.any { abs(it.hz - f) < max(2 * bin, 0.12 * minOf(it.hz, f)) }) continue
            val around = ((k - span)..(k + span)).filter { it in psd.indices && abs(it - k) > 2 }.map { psd[it] }.sorted()
            val med = if (around.isNotEmpty()) around[around.size / 2] else 0.0
            val amp = sqrt(psd[k - 1] + psd[k] + psd[k + 1])
            out += Peak(f, amp, if (med > 0) sqrt(psd[k] / med) else 0.0)
            if (out.size >= count) break
        }
        return out
    }

    private fun angleDeg(ax: Double, ay: Double, az: Double, bx: Double, by: Double, bz: Double): Double {
        val na = sqrt(ax * ax + ay * ay + az * az); val nb = sqrt(bx * bx + by * by + bz * bz)
        if (na < 1e-9 || nb < 1e-9) return 0.0
        return acos(((ax * bx + ay * by + az * bz) / (na * nb)).coerceIn(-1.0, 1.0)) * 180.0 / PI
    }

    private fun prefix(v: DoubleArray): DoubleArray {
        val p = DoubleArray(v.size + 1)
        for (k in v.indices) p[k + 1] = p[k] + v[k]
        return p
    }

    private fun empty(n: Int, quality: String) = VibrationResult(
        0.0, 0, n, 0, 0.0, 0.0, 0.0, 0.0, 0.0, null, emptyList(), List(BINS) { 0f }, List(BINS) { 0f }, 0.0,
        emptyList(), 100, 0.0, 0.0, 0.0, 0.0, 0, 0, 0, quality, true,
    )
}
