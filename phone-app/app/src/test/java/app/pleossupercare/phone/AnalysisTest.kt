package app.pleossupercare.phone

import app.pleossupercare.phone.logic.Dsp
import app.pleossupercare.phone.logic.LightAnalyzer
import app.pleossupercare.phone.logic.NoiseAnalyzer
import app.pleossupercare.phone.logic.PairLink
import app.pleossupercare.phone.logic.QrDecoder
import app.pleossupercare.phone.logic.VibrationAnalyzer
import app.pleossupercare.phone.logic.WarningCatalog
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

class DspTest {
    @Test fun fftFindsSineBin() {
        val n = 1024; val re = DoubleArray(n) { sin(2 * PI * 50 * it / n) }; val im = DoubleArray(n)
        Dsp.fft(re, im)
        val mags = (0 until n / 2).map { re[it] * re[it] + im[it] * im[it] }
        assertEquals(50, mags.indices.maxByOrNull { mags[it] })
    }

    @Test fun welchKeepsPower() {
        val r = Random(1)
        val x = DoubleArray(48000) { 0.3 * sin(2 * PI * 440 * it / 48000.0) + 0.05 * r.nextGaussian() }
        val ms = x.sumOf { it * it } / x.size
        val psd = Dsp.welchPsd(x, 2048, 512)
        assertEquals(ms, psd.sum(), ms * 0.03)
    }
}

class NoiseAnalyzerTest {
    private val fs = 48000

    @Test fun toneLevelAndPitch() {
        // 1kHz, 진폭 0.5(= −6dBFS 봉우리) → RMS −9.03dBFS
        val pcm = ShortArray(fs * 10) { (0.5 * 32767 * sin(2 * PI * 1000 * it / fs)).toInt().toShort() }
        val r = NoiseAnalyzer.analyze(pcm, fs)
        assertEquals(-9.03, r.avgDb, 0.2)
        assertTrue(r.avgDb < 0 && r.peakDb < 0)
        assertEquals(1000.0, r.dominantHz, 3.0)
        assertEquals(64, r.spectrum.size)
        assertEquals(8000, r.spectrumMaxHz)
        assertEquals((1000.0 / 8000 * 64).toInt(), r.spectrum.indices.maxByOrNull { r.spectrum[it] })
        assertEquals(1000f, r.thirdOctaveHz[r.thirdOctave.indices.maxByOrNull { r.thirdOctave[it] }!!])
        assertEquals(100, r.series.size) // 0.1초마다, 10초
        assertFalse(r.silent)
        assertFalse(r.speechLikely) // 한 번 길게 이어지는 소리는 말소리로 보지 않음
    }

    @Test fun skipsFirstSecond() {
        val pcm = ShortArray(fs * 11) { i -> if (i < fs) 30000 else (0.1 * 32767 * sin(2 * PI * 300 * i / fs)).toInt().toShort() }
        val r = NoiseAnalyzer.analyze(pcm, fs, from = fs)
        assertEquals(10_000L, r.durationMs)
        assertEquals(20 * log10(0.1 / sqrt(2.0)), r.avgDb, 0.3)
    }

    @Test fun silenceIsSilent() {
        val r = NoiseAnalyzer.analyze(ShortArray(fs * 3), fs)
        assertTrue(r.silent)
        assertEquals(0.0, r.dominantHz, 0.0)
        assertEquals(NoiseAnalyzer.FLOOR_DB, r.avgDb, 0.0)
    }

    @Test fun clickTrainRepeats() {
        val rnd = Random(3)
        val pcm = ShortArray(fs * 10) { i ->
            val inClick = (i % (fs / 4)) < fs / 100 // 0.25초마다 10ms 딸깍
            ((if (inClick) 0.6 else 0.01) * 32767 * rnd.nextGaussian().coerceIn(-1.0, 1.0)).toInt().toShort()
        }
        val r = NoiseAnalyzer.analyze(pcm, fs)
        assertNotNull(r.repeatSec)
        assertEquals(0.25, r.repeatSec!!, 0.02)
    }

    @Test fun syllableLikeBurstsLookLikeSpeech() {
        // 150Hz 배음(말소리 흉내)을 0.3초 켜고 0.25초 끄기를 되풀이 → 조심하는 쪽으로 말소리로 봄(원본을 보내지 않음)
        val pcm = ShortArray(fs * 10) { i ->
            val on = (i % (fs * 55 / 100)) < fs * 30 / 100
            val t = i.toDouble() / fs
            val v = if (on) (1..12).sumOf { h -> sin(2 * PI * 150 * h * t) / h } * 0.15 else 0.0
            (v * 32767).toInt().coerceIn(-32768, 32767).toShort()
        }
        assertTrue(NoiseAnalyzer.analyze(pcm, fs).speechLikely)
    }

    @Test fun engineHumAndRoadNoiseAreNotSpeech() {
        val rnd = Random(5)
        val pcm = ShortArray(fs * 10) { i ->
            val t = i.toDouble() / fs
            val v = 0.2 * sin(2 * PI * 45 * t) + 0.1 * sin(2 * PI * 90 * t) + 0.05 * rnd.nextGaussian()
            (v * 32767).toInt().coerceIn(-32768, 32767).toShort()
        }
        val r = NoiseAnalyzer.analyze(pcm, fs)
        assertFalse(r.speechLikely)
        assertEquals(45.0, r.dominantHz, 3.0)
    }
}

class VibrationAnalyzerTest {
    private fun synth(rate: Double, sec: Double, hz: Double, amp: Double, noise: Double = 0.02, jitter: Boolean = false, tiltAt: Double? = null): Array<Any> {
        val n = (rate * sec).toInt(); val r = Random(7)
        val t = LongArray(n); val x = FloatArray(n); val y = FloatArray(n); val z = FloatArray(n)
        var ts = 1_000_000_000L
        for (i in 0 until n) {
            val tt = i / rate
            t[i] = ts; ts += (1e9 / rate * (if (jitter) 0.9 + 0.2 * r.nextDouble() else 1.0)).toLong()
            val g = VibrationAnalyzer.G0
            val tilted = tiltAt != null && tt > tiltAt
            val gx = if (tilted) g * sin(0.3) else 0.0; val gz = if (tilted) g * cos(0.3) else g
            x[i] = (gx + noise * r.nextGaussian()).toFloat()
            y[i] = (noise * r.nextGaussian()).toFloat()
            z[i] = (gz + amp * sin(2 * PI * hz * tt) + noise * r.nextGaussian()).toFloat()
        }
        return arrayOf(t, x, y, z, n)
    }

    private fun run(a: Array<Any>) = VibrationAnalyzer.analyze(a[0] as LongArray, a[1] as FloatArray, a[2] as FloatArray, a[3] as FloatArray, a[4] as Int)

    @Test fun findsFourteenHertz() {
        val r = run(synth(400.0, 20.0, 14.0, 0.3, jitter = true))
        assertEquals(400.0, r.sampleHz, 30.0)
        assertEquals(400, r.gridHz)
        assertEquals(14.0, r.dominantHz, 0.3)
        val expectG = 0.3 / sqrt(2.0) / VibrationAnalyzer.G0
        assertEquals(expectG, r.band2, expectG * 0.12)
        assertTrue(r.band1 < r.band2 / 3 && r.band3!! < r.band2 / 3)
        assertEquals(expectG, r.rmsG, expectG * 0.25)
        assertEquals(64, r.spectrum.size)
        assertTrue(r.series.size in 1..200)
        assertEquals("OK", r.quality)
        assertFalse(r.lowQuality)
        assertEquals(14.0, r.peaks.first().hz, 0.3)
        assertTrue(r.peaks.first().ratio > 5)
        assertEquals(0.0, r.tiltDeg, 2.0)
    }

    @Test fun lowRateIsFlagged() {
        val r = run(synth(60.0, 20.0, 8.0, 0.3))
        assertTrue(r.lowQuality)
        assertEquals("LOW_RATE", r.quality)
        assertNull(r.band3) // 20~80Hz는 격자 200Hz 이상일 때만
        assertEquals(8.0, r.dominantHz, 0.4)
    }

    @Test fun movedPhoneWindowsAreDropped() {
        val r = run(synth(200.0, 20.0, 12.0, 0.3, tiltAt = 10.0))
        assertTrue(r.movedWindows > 0)
        assertEquals("MOVED", r.quality)
        assertEquals(12.0, r.dominantHz, 0.4) // 남은 창으로도 박자를 찾음
    }

    @Test fun bundledTestInputIsFourteenHertz() {
        val f = listOf(File("src/main/assets/samples/vibration_test_14hz.csv"), File("app/src/main/assets/samples/vibration_test_14hz.csv")).first { it.exists() }
        val rows = f.readLines().filter { !it.startsWith("#") && it.isNotBlank() }.map { l -> l.split(',').map { it.toFloat() } }
        val n = rows.size
        val t = LongArray(n) { it * 2_500_000L }
        val r = VibrationAnalyzer.analyze(t, FloatArray(n) { rows[it][0] }, FloatArray(n) { rows[it][1] }, FloatArray(n) { rows[it][2] }, n)
        assertEquals(12_000, n)
        assertEquals(14.0, r.dominantHz, 0.3)
        assertEquals(400.0, r.sampleHz, 1.0)
    }
}

class LightAnalyzerTest {
    private fun image(w: Int, h: Int, cx: Int, cy: Int, rad: Int, rgb: Int): IntArray = IntArray(w * h) { i ->
        val x = i % w; val y = i / w
        val d = sqrt(((x - cx) * (x - cx) + (y - cy) * (y - cy)).toDouble())
        if (d <= rad && d >= rad - 5) (0xFF shl 24) or rgb else (0xFF shl 24) or 0x101418 // 고리 모양 불빛 + 어두운 계기판
    }

    @Test fun amberRingIsAmber() {
        val px = image(400, 300, 200, 150, 22, 0xFFAA00)
        val p = LightAnalyzer.pickColor(px, 400, 300, 200, 150)
        assertEquals("주황", p.color)
        assertTrue(p.confidence > 0.9)
    }

    @Test fun redAndBlue() {
        assertEquals("빨강", LightAnalyzer.pickColor(image(400, 300, 100, 100, 20, 0xFF2020), 400, 300, 100, 100).color)
        assertEquals("파랑", LightAnalyzer.pickColor(image(400, 300, 300, 200, 20, 0x2060FF), 400, 300, 300, 200).color)
        assertEquals("초록", LightAnalyzer.pickColor(image(400, 300, 300, 200, 20, 0x20E040), 400, 300, 300, 200).color)
    }

    @Test fun darkAreaAsksUser() {
        val px = image(400, 300, 200, 150, 22, 0xFFAA00)
        val p = LightAnalyzer.pickColor(px, 400, 300, 40, 40)
        assertNull(p.color)
    }

    @Test fun blobsPointAtLights() {
        val px = image(400, 300, 300, 80, 24, 0xFF2020)
        val b = LightAnalyzer.blobs(px, 400, 300)
        assertTrue(b.isNotEmpty())
        assertEquals(0.75f, b.first().cx, 0.05f)
        assertEquals(80f / 300, b.first().cy, 0.05f)
    }

    @Test fun displayBoxShowsWholeSymbol() {
        // 고리 반지름 60px: 색 판정 영역(짧은 변 300의 15% = 45px)보다 크지만, 보여 줄 영역은 고리를 통째로 담아야 함
        val px = image(400, 300, 200, 150, 60, 0xFFAA00)
        val box = LightAnalyzer.displayBox(400, 300, 200, 150, LightAnalyzer.blobs(px, 400, 300))
        assertTrue(box[0] <= 140 && box[1] <= 90 && box[2] >= 260 && box[3] >= 210)
        assertEquals(box[2] - box[0], box[3] - box[1])
        assertEquals("주황", LightAnalyzer.pickColor(px, 400, 300, 200, 150 - 57).color) // 고리 위를 누르면 색은 그대로
    }

    @Test fun narrowingMatchesResearchTable() {
        assertEquals(6, LightAnalyzer.shapesFor("빨강").first.size)
        assertEquals(4, LightAnalyzer.shapesFor("주황").first.size)
        assertEquals(1, LightAnalyzer.shapesFor("파랑").first.size)
        val green = LightAnalyzer.shapesFor("초록")
        assertEquals(8, green.first.size); assertFalse(green.second)
    }

    @Test fun candidatesStartWithCatalogResult() {
        val ids = LightAnalyzer.candidates("빨강", "브레이크", WarningCatalog.BLINK)
        assertEquals("brake-blink", ids.first())
        assertTrue(ids.all { id -> WarningCatalog.byId(id) != null })
        assertTrue(ids.size <= 8)
    }

    @Test fun hsvMatchesDefinition() {
        val out = FloatArray(3)
        LightAnalyzer.hsv(0xFFFF0000.toInt(), out); assertEquals(0f, out[0], 0.01f); assertEquals(1f, out[1], 0.01f)
        LightAnalyzer.hsv(0xFF0000FF.toInt(), out); assertEquals(240f, out[0], 0.01f)
        LightAnalyzer.hsv(0xFFFF8000.toInt(), out); assertEquals(30f, out[0], 0.5f)
        assertTrue(abs(out[2] - 1f) < 0.01f)
    }
}

class PairLinkTest {
    @Test fun parsesCarQr() {
        val p = PairLink.parse("pleoscare://pair?code=123456&server=http://10.0.2.2:8766")!!
        assertEquals("123456", p.code); assertEquals("http://10.0.2.2:8766", p.server)
    }

    @Test fun parsesPlainCode() {
        assertEquals("654321", PairLink.parse(" 654321 ")!!.code)
        assertNull(PairLink.parse("abc"))
        assertNull(PairLink.parse("pleoscare://pair?code=12345"))
        assertNull(PairLink.parse("1234567"))
    }

    @Test fun decodesGeneratedQr() {
        val text = "pleoscare://pair?code=482913&server=http://10.0.2.2:8766"
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 300, 300)
        val lum = ByteArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) 0 else 255.toByte() }
        assertEquals(text, QrDecoder.decode(lum, m.width, m.height))
        assertEquals("482913", PairLink.parse(QrDecoder.decode(lum, m.width, m.height))!!.code)
    }
}
