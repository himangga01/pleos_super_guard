package app.pleossupercare.phone.logic

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 소리 기록 분석 결과. 크기는 모두 dBFS(폰 마이크가 받을 수 있는 가장 큰 소리 = 0, 그래서 음수)인 상대 크기이며
 * 소음계의 dB SPL · dB(A)가 아닙니다. 지표 이름은 차 앱과 맞춘 것입니다(avgDb · peakDb · dominantHz · spectrumMaxHz).
 * 값은 폰 센서 조사(docs/analysis/phone-sensing-research-2026-11-14.md) 7.3절을 따릅니다.
 */
data class NoiseResult(
    val sampleRate: Int,
    val durationMs: Long,
    /** 기록 전체의 평균 크기(RMS, dBFS). */
    val avgDb: Double,
    /** 가장 큰 0.1초의 크기(RMS, dBFS). */
    val peakDb: Double,
    /** 표본 하나의 최댓값(dBFS). −1dBFS를 넘으면 "너무 가깝습니다". */
    val samplePeakDb: Double,
    /** 10%의 시간 동안 넘은 크기(L10)와 90%의 시간 동안 넘은 크기(바탕 소리, L90). */
    val l10Db: Double, val l90Db: Double,
    /** 가장 센 높이(8192점 평균 스펙트럼, 5.9Hz 칸에서 보간). */
    val dominantHz: Double,
    /** 센 봉우리 높이(강한 순, 최대 5개). */
    val peaksHz: List<Double>,
    /** 스펙트럼 무게중심(Hz). */
    val centroidHz: Double,
    /** 크기가 되풀이되는 간격(0.05~2초), 뚜렷하지 않으면 null. */
    val repeatSec: Double?,
    /** 250Hz 아래 · 250~2000Hz · 2000Hz 위의 에너지 몫(%). */
    val lowPct: Double, val midPct: Double, val highPct: Double,
    /** 0~[spectrumMaxHz]를 64칸으로 나눈 칸별 크기(dBFS, 2048점 창). */
    val spectrum: List<Float>,
    val spectrumHz: List<Float>,
    val spectrumMaxHz: Int,
    /** 1/3옥타브 크기(dBFS)와 가운데 주파수(25Hz~20kHz, 나이퀴스트 아래만). */
    val thirdOctave: List<Float>,
    val thirdOctaveHz: List<Float>,
    /** 0.1초마다 크기(dBFS), 200점 이하. */
    val series: List<Float>,
    val seriesStepMs: Int,
    val clipPct: Double,
    /** 소리가 거의 없음(마이크가 막혔거나 에뮬레이터에 PC 마이크가 꺼져 있음). */
    val silent: Boolean,
    /** 말소리로 보이는 구간이 있음(간단한 목소리 구간 검출). 그러면 소리 원본을 보내지 않습니다. */
    val speechLikely: Boolean,
    val speechPct: Double,
)

object NoiseAnalyzer {
    const val SPECTRUM_BINS = 64
    const val SPECTRUM_MAX_HZ = 8000
    const val FRAME_MS = 100
    const val SERIES_MAX = 200
    const val FLOOR_DB = -100.0
    /** 평균이 이보다 작으면 "소리가 거의 없음"으로 봅니다. */
    const val SILENT_DB = -85.0
    /** 표본 최댓값이 이보다 크면 "너무 가깝습니다". */
    const val TOO_CLOSE_DB = -1.0

    /** 1/3옥타브 가운데 주파수(표준 값, 25Hz~20kHz, 30개). */
    val THIRD_OCTAVE = doubleArrayOf(25.0, 31.5, 40.0, 50.0, 63.0, 80.0, 100.0, 125.0, 160.0, 200.0, 250.0, 315.0, 400.0, 500.0, 630.0,
        800.0, 1000.0, 1250.0, 1600.0, 2000.0, 2500.0, 3150.0, 4000.0, 5000.0, 6300.0, 8000.0, 10000.0, 12500.0, 16000.0, 20000.0)

    /** [pcm]의 [from]부터 [length]개 표본을 분석합니다(앞 1초를 버릴 때 from = 표본 주기). */
    fun analyze(pcm: ShortArray, sampleRate: Int, from: Int = 0, length: Int = pcm.size - from): NoiseResult {
        val n = length.coerceIn(0, pcm.size - from)
        val x = DoubleArray(n) { pcm[from + it] / 32768.0 }
        val mean = if (n > 0) x.average() else 0.0
        for (i in 0 until n) x[i] -= mean
        var clip = 0
        var maxAbs = 0.0
        var sumSq = 0.0
        for (i in 0 until n) {
            val a = abs(x[i]); if (a > maxAbs) maxAbs = a
            if (abs(pcm[from + i].toInt()) >= 32700) clip++
            sumSq += x[i] * x[i]
        }
        val avg = Dsp.db(if (n > 0) sumSq / n else 0.0, FLOOR_DB)
        val silent = avg <= SILENT_DB

        // 0.1초마다 크기(시간 그래프)
        val frame = max(1, sampleRate * FRAME_MS / 1000)
        val frames = n / frame
        val frameMs = DoubleArray(frames) { f -> var s = 0.0; for (i in f * frame until (f + 1) * frame) s += x[i] * x[i]; s / frame }
        val frameDb = DoubleArray(frames) { Dsp.db(frameMs[it], FLOOR_DB) }
        val loudest = frameDb.indices.maxByOrNull { frameDb[it] } ?: 0
        val seriesMs = Dsp.shrink(frameMs, SERIES_MAX) { it.average() }
        val step = if (frames > 0) FRAME_MS * frames / seriesMs.size.coerceAtLeast(1) else FRAME_MS

        val nyq = sampleRate / 2.0
        // 짧은 창(2048점, 512 간격): 화면 · 차 앱 스펙트럼 64칸
        val psdShort = Dsp.welchPsd(x, 2048, 512)
        val binShort = sampleRate / 2048.0
        val maxHz = minOf(SPECTRUM_MAX_HZ.toDouble(), nyq)
        val (bands, centers) = Dsp.bandSum(psdShort, binShort, maxHz, SPECTRUM_BINS)
        // 긴 창(8192점): 또렷한 봉우리 · 1/3옥타브 · 무게중심
        val nLong = if (n >= 8192) 8192 else Dsp.floorPow2(max(n, 256))
        val psd = Dsp.welchPsd(x, nLong)
        val bin = sampleRate.toDouble() / nLong
        val hi = minOf(12000.0, nyq - 2 * bin)
        val dominant = Dsp.peakHz(psd, bin, 40.0, hi)
        val peaks = Dsp.peaks(psd, bin, 40.0, hi, 5)

        val third = mutableListOf<Float>(); val thirdHz = mutableListOf<Float>()
        val k = 2.0.pow(1.0 / 6)
        for (fc in THIRD_OCTAVE) {
            if (fc * k > nyq) break
            var s = 0.0
            for (i in psd.indices) { val f = i * bin; if (f >= fc / k && f < fc * k) s += psd[i] }
            third += Dsp.db(s, FLOOR_DB).toFloat(); thirdHz += fc.toFloat()
        }

        var low = 0.0; var mid = 0.0; var high = 0.0; var wsum = 0.0; var psum = 0.0
        for (i in psd.indices) {
            val f = i * bin
            if (f < 20) continue
            when { f < 250 -> low += psd[i]; f < 2000 -> mid += psd[i]; else -> high += psd[i] }
            wsum += f * psd[i]; psum += psd[i]
        }
        val tot = (low + mid + high).takeIf { it > 0 } ?: 1.0
        val (speech, speechPct) = if (silent) false to 0.0 else speechLikely(x, sampleRate)

        return NoiseResult(
            sampleRate = sampleRate,
            durationMs = n * 1000L / sampleRate.coerceAtLeast(1),
            avgDb = avg,
            peakDb = if (frames > 0) frameDb[loudest] else avg,
            samplePeakDb = if (maxAbs > 0) max(FLOOR_DB, 20 * log10(maxAbs)) else FLOOR_DB,
            l10Db = Dsp.percentile(frameDb, 90.0),
            l90Db = Dsp.percentile(frameDb, 10.0),
            dominantHz = if (silent) 0.0 else dominant,
            peaksHz = if (silent) emptyList() else peaks,
            centroidHz = if (silent || psum <= 0) 0.0 else wsum / psum,
            repeatSec = if (silent) null else repeatInterval(x, sampleRate),
            lowPct = 100 * low / tot, midPct = 100 * mid / tot, highPct = 100 * high / tot,
            spectrum = bands.map { Dsp.db(it, FLOOR_DB).toFloat() },
            spectrumHz = centers.map { it.toFloat() },
            spectrumMaxHz = maxHz.toInt(),
            thirdOctave = third, thirdOctaveHz = thirdHz,
            series = seriesMs.map { Dsp.db(it, FLOOR_DB).toFloat() },
            seriesStepMs = step,
            clipPct = if (n > 0) 100.0 * clip / n else 0.0,
            silent = silent,
            speechLikely = speech, speechPct = speechPct,
        )
    }

    /**
     * 간단한 목소리 구간 검출(조심하는 쪽으로 기울임: 애매하면 말소리로 봅니다).
     * 약 21ms 창마다 250~4,000Hz 몫(0.3 넘게), 그 대역의 스펙트럼 평탄도(말소리는 배음이 있어 낮음, 0.35 밑), 바탕보다 9dB 큰지를 봅니다.
     * 이런 창이 0.1~1.2초 이어진 덩어리가 3개 이상이면 말소리로 봅니다(한 번 길게 이어지는 기계음 · 윙 소리와 구별).
     * 끊겼다 이어지는 끽 소리도 말소리로 볼 수 있습니다. 그때는 원본을 보내지 않을 뿐 숫자 결과는 그대로 보냅니다.
     */
    fun speechLikely(x: DoubleArray, sampleRate: Int): Pair<Boolean, Double> {
        val n = Dsp.floorPow2(max(128, sampleRate / 40)) // 약 21ms(48kHz · 44.1kHz에서 1024점)
        val hop = n / 2
        val frames = if (x.size >= n) (x.size - n) / hop + 1 else 0
        if (frames < 10) return false to 0.0
        val w = Dsp.hann(n)
        val re = DoubleArray(n); val im = DoubleArray(n)
        val energy = DoubleArray(frames); val ratio = DoubleArray(frames); val flat = DoubleArray(frames)
        val bin = sampleRate.toDouble() / n
        val k0 = max(1, (250 / bin).toInt()); val k1 = minOf(n / 2 - 1, (4000 / bin).toInt())
        for (f in 0 until frames) {
            var e = 0.0
            for (i in 0 until n) { val v = x[f * hop + i]; re[i] = v * w[i]; im[i] = 0.0; e += v * v }
            Dsp.fft(re, im)
            var tot = 0.0; var band = 0.0; var logSum = 0.0
            for (k in 1 until n / 2) {
                val p = re[k] * re[k] + im[k] * im[k]
                tot += p
                if (k in k0..k1) { band += p; logSum += ln(p + 1e-20) }
            }
            val cnt = (k1 - k0 + 1).coerceAtLeast(1)
            energy[f] = Dsp.db(e / n)
            ratio[f] = if (tot > 0) band / tot else 0.0
            flat[f] = if (band > 0) exp(logSum / cnt) / (band / cnt) else 1.0
        }
        val floor = Dsp.percentile(energy, 20.0)
        val voiced = BooleanArray(frames) { energy[it] > floor + 9 && ratio[it] > 0.3 && flat[it] < 0.35 }
        val frameSec = hop.toDouble() / sampleRate
        var runs = 0; var len = 0
        for (f in 0..frames) {
            if (f < frames && voiced[f]) len++ else {
                val sec = len * frameSec
                if (sec in 0.1..1.2) runs++
                len = 0
            }
        }
        val pct = 100.0 * voiced.count { it } / frames
        return (runs >= 3) to pct
    }

    /** 크기 흐름(10ms)의 자기상관으로 0.05~2초 되풀이 간격을 찾습니다. 상관이 0.5 밑이면 null. */
    fun repeatInterval(x: DoubleArray, sampleRate: Int): Double? {
        val hop = max(1, sampleRate / 100)
        val m = x.size / hop
        if (m < 60) return null
        val env = DoubleArray(m) { j -> var s = 0.0; for (i in j * hop until (j + 1) * hop) s += x[i] * x[i]; sqrt(s / hop) }
        val mu = env.average()
        for (j in env.indices) env[j] -= mu
        val r0 = env.sumOf { it * it }
        if (r0 <= 1e-12) return null
        val maxLag = minOf(200, m / 2)
        val r = DoubleArray(maxLag + 1) { lag ->
            var s = 0.0
            for (j in 0 until m - lag) s += env[j] * env[j + lag]
            s / r0 * m / (m - lag)
        }
        // 천천히 변하는 크기(점점 커짐 등)를 되풀이로 보지 않도록, 상관이 한 번 0.2 밑으로 내려간 뒤의 봉우리만 봅니다.
        val start = (1..maxLag).firstOrNull { r[it] < 0.2 } ?: return null
        val peaks = (maxOf(start, 5) until maxLag).filter { r[it] >= r[it - 1] && r[it] >= r[it + 1] }
        val best = peaks.maxOfOrNull { r[it] } ?: return null
        if (best < 0.5) return null
        // 간격의 배수(2배 · 3배 …)에서도 상관이 비슷하게 높으므로, 가장 높은 값의 90% 안에 드는 첫 봉우리(기본 간격)를 고릅니다.
        val lag = peaks.first { r[it] >= 0.9 * best }
        return lag / 100.0
    }

    /** 소리 높낮이 말(화면 참고용). 원인을 뜻하지 않습니다. */
    fun pitchLabel(hz: Double): String = when {
        hz <= 0 -> "알 수 없음"
        hz < 150 -> "낮은 웅웅"
        hz < 500 -> "낮은 소리"
        hz < 2000 -> "중간 높이"
        hz < 5000 -> "높은 소리"
        else -> "아주 높은 소리"
    }

    /** 표본 묶음의 크기(dBFS). 기록 중 화면의 막대에 씁니다. */
    fun levelDb(buf: ShortArray, count: Int): Double {
        if (count <= 0) return FLOOR_DB
        var s = 0.0
        for (i in 0 until count) { val v = buf[i] / 32768.0; s += v * v }
        return Dsp.db(s / count, FLOOR_DB)
    }
}
