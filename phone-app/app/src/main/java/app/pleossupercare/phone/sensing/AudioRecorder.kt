package app.pleossupercare.phone.sensing

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import app.pleossupercare.phone.logic.NoiseAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * 마이크 녹음(폰 센서 조사 7.3절): 소스는 UNPROCESSED(기기가 지원한다고 알릴 때) → 아니면 VOICE_RECOGNITION.
 * 48,000Hz를 먼저 시도하고 안 되면 44,100Hz, 모노 16비트 PCM. 원본 표본은 메모리에서 분석한 뒤 버립니다.
 */
class AudioRecorder(private val context: Context) {
    class Recording(val pcm: ShortArray, val length: Int, val sampleRate: Int, val source: String, val startedAt: Long)

    @SuppressLint("MissingPermission") // 부르기 전에 RECORD_AUDIO를 묻습니다(쓰는 순간에 묻기).
    suspend fun record(totalMs: Long, stop: () -> Boolean, onLive: (elapsedMs: Long, levelDb: Double) -> Unit): Recording =
        withContext(Dispatchers.IO) {
            val am = context.getSystemService(AudioManager::class.java)
            val unprocessed = am?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
            val sources = buildList {
                if (unprocessed) add(MediaRecorder.AudioSource.UNPROCESSED to "UNPROCESSED")
                add(MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION")
            }
            var rec: AudioRecord? = null
            var name = ""
            outer@ for ((src, label) in sources) for (rate in listOf(48000, 44100)) {
                val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                if (minBuf <= 0) continue
                val ar = runCatching { AudioRecord(src, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(minBuf, rate / 5 * 2)) }.getOrNull()
                if (ar != null && ar.state == AudioRecord.STATE_INITIALIZED) { rec = ar; name = label; break@outer }
                ar?.release()
            }
            val ar = rec ?: throw IllegalStateException("마이크를 열지 못했습니다")
            val rate = ar.sampleRate
            val total = (rate.toLong() * totalMs / 1000).toInt()
            val pcm = ShortArray(total)
            val chunk = ShortArray(rate / 10)
            var got = 0
            try {
                ar.startRecording()
                val started = System.currentTimeMillis()
                while (got < total && isActive && !stop()) {
                    val r = ar.read(chunk, 0, min(chunk.size, total - got))
                    if (r < 0) throw IllegalStateException("마이크 읽기 오류($r)")
                    System.arraycopy(chunk, 0, pcm, got, r)
                    got += r
                    onLive(got * 1000L / rate, NoiseAnalyzer.levelDb(chunk, r))
                }
                Recording(pcm, got, rate, name, started)
            } finally {
                runCatching { ar.stop() }
                ar.release()
            }
        }
}
