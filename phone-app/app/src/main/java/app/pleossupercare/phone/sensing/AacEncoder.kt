package app.pleossupercare.phone.sensing

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteOrder

/**
 * 소리 원본(사용자가 켰고 말소리가 없을 때만)을 AAC-LC 64kbps M4A로 만듭니다. 10초면 약 80KB입니다.
 * 녹음은 AudioRecord 하나로 하고, 같은 PCM을 MediaCodec으로 부호화합니다(마이크를 두 번 열지 않음).
 */
object AacEncoder {
    fun encode(pcm: ShortArray, from: Int, length: Int, sampleRate: Int, out: File, bitrate: Int = 64_000): Boolean {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16_384)
        }
        val codec = runCatching { MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC) }.getOrNull() ?: return false
        var muxer: MediaMuxer? = null
        var started = false
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(out.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var track = -1
            val info = MediaCodec.BufferInfo()
            var pos = 0
            var inputDone = false
            var guard = 0
            while (guard++ < 100_000) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)!!
                        buf.clear()
                        val n = minOf(buf.remaining() / 2, length - pos)
                        val pts = pos * 1_000_000L / sampleRate
                        if (n <= 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            buf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm, from + pos, n)
                            codec.queueInputBuffer(inIdx, 0, n * 2, pts, 0)
                            pos += n
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    started = true
                } else if (outIdx >= 0) {
                    val ob = codec.getOutputBuffer(outIdx)!!
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && started) {
                        ob.position(info.offset); ob.limit(info.offset + info.size)
                        muxer.writeSampleData(track, ob, info)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            return started && out.length() > 0
        } catch (e: Exception) {
            return false
        } finally {
            runCatching { codec.stop() }
            codec.release()
            runCatching { if (started) muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }
}
