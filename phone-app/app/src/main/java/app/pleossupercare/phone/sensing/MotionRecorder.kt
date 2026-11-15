package app.pleossupercare.phone.sensing

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.HandlerThread
import app.pleossupercare.phone.logic.VibrationAnalyzer
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sqrt

/** 떨림 표본(m/s², 중력 포함)과 타임스탬프(ns). 분석이 끝나면 버립니다(서버에 보내지 않음). */
class MotionData(
    val t: LongArray, val x: FloatArray, val y: FloatArray, val z: FloatArray, val n: Int,
    val gyroRmsDps: Double?, val startedAt: Long, val sensorName: String, val requestedUs: Int, val minDelayUs: Int,
    /** 시험 입력 재생으로 만든 표본이면 true. */
    val replay: Boolean,
)

/**
 * 가속도계 원값(TYPE_ACCELEROMETER) 기록(폰 센서 조사 7.2절). 400Hz(2,500µs)를 요청합니다.
 * Android 12+는 HIGH_SAMPLING_RATE_SENSORS 선언이 있어야 200Hz를 넘습니다(매니페스트에 선언). 실제 주기는 타임스탬프로 잽니다.
 */
class MotionRecorder(context: Context) {
    private val sm = context.getSystemService(SensorManager::class.java)
    val accel: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyro: Sensor? = sm?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    suspend fun record(durationMs: Long, stop: () -> Boolean, onLive: (elapsedMs: Long, magG: Double) -> Unit): MotionData {
        val sensor = accel ?: throw IllegalStateException("가속도 센서가 없습니다")
        val buf = Growable()
        val gyroBuf = Growable()
        val lock = Any()
        var live = 0.0
        var ex = Double.NaN; var ey = 0.0; var ez = 0.0
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                synchronized(lock) {
                    if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                        buf.add(e.timestamp, e.values[0], e.values[1], e.values[2])
                        if (ex.isNaN()) { ex = e.values[0].toDouble(); ey = e.values[1].toDouble(); ez = e.values[2].toDouble() }
                        ex += 0.02 * (e.values[0] - ex); ey += 0.02 * (e.values[1] - ey); ez += 0.02 * (e.values[2] - ez)
                        val dx = e.values[0] - ex; val dy = e.values[1] - ey; val dz = e.values[2] - ez
                        val mag = sqrt(dx * dx + dy * dy + dz * dz) / VibrationAnalyzer.G0
                        if (mag > live) live = mag
                    } else gyroBuf.add(e.timestamp, e.values[0], e.values[1], e.values[2])
                }
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
        }
        val thread = HandlerThread("motion").apply { start() }
        val handler = Handler(thread.looper)
        var requested = 2_500
        val ok = runCatching { sm!!.registerListener(listener, sensor, requested, handler) }.getOrElse {
            requested = 5_000 // 선언이 없는 Android 12+ 기기에서 SecurityException이 나면 200Hz로 낮춥니다.
            sm!!.registerListener(listener, sensor, requested, handler)
        }
        if (!ok) { thread.quitSafely(); throw IllegalStateException("가속도 센서를 켜지 못했습니다") }
        gyro?.let { runCatching { sm.registerListener(listener, it, 5_000, handler) } }
        val started = System.currentTimeMillis()
        try {
            while (!stop()) {
                val el = System.currentTimeMillis() - started
                if (el >= durationMs) break
                delay(100)
                val v = synchronized(lock) { val m = live; live = 0.0; m }
                onLive(minOf(durationMs, System.currentTimeMillis() - started), v)
            }
        } finally {
            sm.unregisterListener(listener)
            thread.quitSafely()
        }
        val gyroRms = synchronized(lock) { gyroBuf.rmsAroundMeanDps() }
        synchronized(lock) {
            return MotionData(buf.t, buf.x, buf.y, buf.z, buf.n, gyroRms, started, sensor.name ?: "accelerometer", requested, sensor.minDelay, false)
        }
    }

    /** 늘어나는 배열(표본을 받는 동안 다시 할당을 줄임). */
    private class Growable {
        var t = LongArray(8192); var x = FloatArray(8192); var y = FloatArray(8192); var z = FloatArray(8192); var n = 0
        fun add(ts: Long, a: Float, b: Float, c: Float) {
            if (n == t.size) { t = t.copyOf(n * 2); x = x.copyOf(n * 2); y = y.copyOf(n * 2); z = z.copyOf(n * 2) }
            t[n] = ts; x[n] = a; y[n] = b; z[n] = c; n++
        }
        fun rmsAroundMeanDps(): Double? {
            if (n < 10) return null
            val mx = (0 until n).sumOf { x[it].toDouble() } / n; val my = (0 until n).sumOf { y[it].toDouble() } / n; val mz = (0 until n).sumOf { z[it].toDouble() } / n
            val ms = (0 until n).sumOf { val a = x[it] - mx; val b = y[it] - my; val c = z[it] - mz; a * a + b * b + c * c } / n
            return Math.toDegrees(sqrt(abs(ms)))
        }
    }
}
