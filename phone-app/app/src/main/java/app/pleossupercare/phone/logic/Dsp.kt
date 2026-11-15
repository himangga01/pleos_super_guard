package app.pleossupercare.phone.logic

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin

/** 소리 · 진동 분석에 쓰는 신호 처리 도구(순수 Kotlin, 기기 없이 시험 가능). */
object Dsp {
    fun isPow2(n: Int) = n > 0 && (n and (n - 1)) == 0

    /** n 이하의 가장 큰 2의 거듭제곱. */
    fun floorPow2(n: Int): Int {
        var p = 1
        while (p * 2 <= n) p *= 2
        return p
    }

    /** 제자리 radix-2 FFT. [re] · [im] 길이는 같은 2의 거듭제곱입니다. */
    fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        require(n == im.size && isPow2(n)) { "FFT 길이는 2의 거듭제곱: $n" }
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang); val wi = sin(ang)
            val half = len / 2
            var i = 0
            while (i < n) {
                var cr = 1.0; var ci = 0.0
                for (k in 0 until half) {
                    val a = i + k; val b = a + half
                    val tr = re[b] * cr - im[b] * ci
                    val ti = re[b] * ci + im[b] * cr
                    re[b] = re[a] - tr; im[b] = im[a] - ti
                    re[a] += tr; im[a] += ti
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr; cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }

    /** 주기형 Hann 창. */
    fun hann(n: Int) = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / n) }

    /**
     * Welch 평균 파워 스펙트럼(한쪽, 칸 [n]/2+1개). 결과[k] = 그 칸의 평균 제곱(mean-square) 몫입니다.
     * 모든 칸을 더하면 신호의 평균 제곱이 됩니다(파스발). 진폭 A인 사인은 근처 칸의 합이 A²/2입니다.
     * 신호가 [n]보다 짧으면 0을 덧대어 한 조각으로 계산합니다.
     */
    fun welchPsd(x: DoubleArray, n: Int, hop: Int = n / 2): DoubleArray {
        require(isPow2(n))
        val w = hann(n)
        val sw2 = w.sumOf { it * it }
        val out = DoubleArray(n / 2 + 1)
        val re = DoubleArray(n); val im = DoubleArray(n)
        var frames = 0
        var start = 0
        while (frames == 0 || start + n <= x.size) {
            for (i in 0 until n) {
                val v = if (start + i < x.size) x[start + i] else 0.0
                re[i] = v * w[i]; im[i] = 0.0
            }
            fft(re, im)
            for (k in 0..n / 2) out[k] += re[k] * re[k] + im[k] * im[k]
            frames++
            start += hop
            if (x.size < n) break
        }
        val norm = 1.0 / (frames * n * sw2)
        for (k in 0..n / 2) out[k] *= norm * (if (k == 0 || k == n / 2) 1.0 else 2.0)
        return out
    }

    /** 가장 센 칸(주파수 [loHz]..[hiHz])을 찾아 이웃 칸으로 포물선 보간한 주파수(Hz). 신호가 없으면 0. */
    fun peakHz(psd: DoubleArray, binHz: Double, loHz: Double, hiHz: Double): Double {
        val k0 = max(1, (loHz / binHz).toInt())
        val k1 = minOf(psd.size - 2, (hiHz / binHz).toInt())
        if (k1 <= k0) return 0.0
        var best = k0
        for (k in k0..k1) if (psd[k] > psd[best]) best = k
        if (psd[best] <= 1e-20) return 0.0
        val a = ln(psd[best - 1] + 1e-30); val b = ln(psd[best] + 1e-30); val c = ln(psd[best + 1] + 1e-30)
        val den = a - 2 * b + c
        val delta = if (den != 0.0) (0.5 * (a - c) / den).coerceIn(-0.5, 0.5) else 0.0
        return (best + delta) * binHz
    }

    /**
     * 센 봉우리 주파수 몇 개(강한 순). 서로 [minRatio]배(기본 1/6옥타브 ≈ 1.12배)보다 가까운 봉우리는 하나로 봅니다.
     */
    fun peaks(psd: DoubleArray, binHz: Double, loHz: Double, hiHz: Double, count: Int, minRatio: Double = 1.12): List<Double> {
        val k0 = max(1, (loHz / binHz).toInt())
        val k1 = minOf(psd.size - 2, (hiHz / binHz).toInt())
        val local = (k0..k1).filter { psd[it] > 1e-20 && psd[it] >= psd[it - 1] && psd[it] >= psd[it + 1] }.sortedByDescending { psd[it] }
        val out = mutableListOf<Double>()
        for (k in local) {
            val f = k * binHz
            if (out.none { o -> f / o < minRatio && o / f < minRatio }) out += f
            if (out.size >= count) break
        }
        return out
    }

    /** 0..[maxHz]를 [bins]칸으로 나눠 칸마다 PSD를 더합니다(칸의 평균 제곱). 칸 중심 주파수도 줍니다. */
    fun bandSum(psd: DoubleArray, binHz: Double, maxHz: Double, bins: Int): Pair<DoubleArray, DoubleArray> {
        val out = DoubleArray(bins)
        val width = maxHz / bins
        for (k in psd.indices) {
            val f = k * binHz
            if (f >= maxHz) break
            out[(f / width).toInt().coerceIn(0, bins - 1)] += psd[k]
        }
        return out to DoubleArray(bins) { (it + 0.5) * width }
    }

    fun db(meanSquare: Double, floor: Double = -120.0) = if (meanSquare <= 0.0) floor else max(floor, 10 * log10(meanSquare))

    /** [values]를 [limit]칸 이하로 묶습니다([reduce]로 칸 값 계산). */
    fun shrink(values: DoubleArray, limit: Int, reduce: (DoubleArray) -> Double): DoubleArray {
        if (values.size <= limit) return values
        return DoubleArray(limit) { i ->
            val a = i * values.size / limit
            val b = max((i + 1) * values.size / limit, a + 1)
            reduce(values.copyOfRange(a, b))
        }
    }

    /** 정렬한 값에서 백분위([p] 0..100). */
    fun percentile(values: DoubleArray, p: Double): Double {
        if (values.isEmpty()) return 0.0
        val s = values.sortedArray()
        val pos = (p / 100.0) * (s.size - 1)
        val lo = pos.toInt(); val hi = minOf(lo + 1, s.size - 1)
        return s[lo] + (s[hi] - s[lo]) * (pos - lo)
    }
}
