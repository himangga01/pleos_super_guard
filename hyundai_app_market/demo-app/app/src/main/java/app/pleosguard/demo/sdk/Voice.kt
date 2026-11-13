package app.pleosguard.demo.sdk

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.listener.ResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.listener.EventListener
import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.pleosguard.demo.logic.RulesLab
import ai.pleos.playground.stt.constant.ErrorType as SttError
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnInitializeListener as SttInit
import ai.pleos.playground.tts.constant.ErrorType as TtsError
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.OnInitializeListener as TtsInit
import java.util.Locale

/**
 * 음성 안내(TTS)와 음성 인식(STT). 둘 다 기기 안 모드(ON_DEVICE)로 고정해 외부 서버로 소리를 보내지 않습니다.
 * - TTS: initialize 성공 뒤 EventListener 등록·언어 설정(SDK 문서 순서). 기본 용도 MEDIA 대신 안내 용도를 지정합니다.
 * - STT: 사용자가 누른 뒤나 확인 질문 직후에만 최대 8초 듣습니다(상시 듣기 없음). TTS가 말하는 동안은 듣지 않습니다.
 * - TTS가 실패해도 화면 흐름이 멈추지 않도록, 끝 알림이 오지 않으면 글 길이로 잡은 시간 뒤에 다음으로 넘어갑니다.
 */
class Voice(private val context: Context, private val onChange: () -> Unit) {
    var ttsState: String = "준비 전"; private set
    var sttState: String = "준비 전"; private set
    var speaking = false; private set
    var listening = false; private set
    var partial: String = ""; private set

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSpeak: Pair<String, (() -> Unit)?>? = null
    private var onSpoken: (() -> Unit)? = null
    private var speakSeq = 0
    private var stt: SpeechToText? = null
    private var sttReady = false
    private var onHeard: ((String) -> Unit)? = null
    private var onSilence: (() -> Unit)? = null
    private var listenSeq = 0

    private fun ui(block: () -> Unit) { main.post { block(); onChange() } }

    private val ttsEvents = object : EventListener {
        override fun onStart() = ui { speaking = true; ttsState = "말하는 중" }
        override fun onReady() {}
        override fun onDone() = ui { speaking = false; ttsState = "준비됨"; finishSpeak() }
        override fun onError(type: TtsError) = ui { speaking = false; ttsState = "오류: $type"; finishSpeak() }
        override fun onStop() = ui { speaking = false }
        override fun onUpdatedRms(rms: Double) {}
    }

    private val sttEvents = object : ResultListener {
        override fun onUpdated(stt: String, completed: Boolean) = ui {
            partial = stt
            if (completed) deliverHeard(stt)
        }
        override fun onUpdatedEpdData(start: Long, end: Long) {}
        override fun onStartedRecognition() = ui { sttState = "말소리 감지" }
        override fun onEndedRecognition() = ui { sttState = "말 끝 감지" }
        override fun onError(type: SttError) = ui { sttState = "오류: $type"; endListening(timeout = true) }
        override fun onReady() = ui { sttState = "듣는 중" }
        override fun onUpdatedRms(rms: Double) {}
    }

    fun start() {
        try {
            val t = TextToSpeech(context, TtsMode.ON_DEVICE).also { tts = it }
            t.initialize(object : TtsInit {
                override fun onSuccess() = ui {
                    try {
                        t.addEventListener(ttsEvents)
                        t.setLocale(Locale.KOREA)
                        t.setAudioAttributes(
                            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                        )
                        ttsReady = true; ttsState = "준비됨"
                        pendingSpeak?.let { (text, done) -> pendingSpeak = null; speak(text, done) }
                    } catch (e: Exception) { ttsState = "설정 실패: ${e.javaClass.simpleName}" }
                }
                override fun onFailure(type: TtsError) = ui { ttsState = "초기화 실패: $type" }
            })
        } catch (e: Throwable) { ttsState = "TTS 실패: ${e.javaClass.simpleName}" }
        try {
            val s = SpeechToText(context, SttMode.ON_DEVICE).also { stt = it }
            s.initialize(object : SttInit {
                override fun onSuccess() = ui {
                    try { s.addListener(sttEvents); s.setLocale(Locale.KOREA); sttReady = true; sttState = "준비됨" }
                    catch (e: Exception) { sttState = "설정 실패: ${e.javaClass.simpleName}" }
                }
                override fun onFailure(type: SttError) = ui { sttState = "초기화 실패: $type" }
            })
        } catch (e: Throwable) { sttState = "STT 실패: ${e.javaClass.simpleName}" }
    }

    /** 말합니다. 앞 안내는 멈추고 새 안내를 말합니다. [done]은 끝났을 때(또는 실패해도 예상 시간 뒤) 한 번 불립니다. */
    fun speak(text: String, done: (() -> Unit)? = null) {
        stopListening()
        val seq = ++speakSeq
        onSpoken = done
        // 끝 알림이 오지 않을 때를 대비한 예상 시간(한 글자 약 0.18초 + 1.5초)
        main.postDelayed({ if (seq == speakSeq && onSpoken != null) { speaking = false; finishSpeak(); onChange() } }, 1_500L + text.length * 180L)
        val t = tts
        if (t == null || !ttsReady) { pendingSpeak = text to done; return }
        try {
            if (speaking) t.stop()
            t.speak(text, true)
        } catch (e: Exception) {
            ttsState = "말하기 실패: ${e.javaClass.simpleName}"; Log.w(TAG, "speak", e)
        }
    }

    private fun finishSpeak() { val d = onSpoken; onSpoken = null; d?.invoke() }

    fun stopSpeaking() {
        speakSeq++; onSpoken = null; pendingSpeak = null
        runCatching { if (speaking) tts?.stop() }
        speaking = false
    }

    /** 최대 [windowMs] 동안 듣습니다. 완료 결과가 오면 [heard], 시간이 지나면 [silence]. */
    fun listen(windowMs: Long = RulesLab.VOICE_LISTEN_MS, heard: (String) -> Unit, silence: () -> Unit) {
        stopListening()
        val seq = ++listenSeq
        onHeard = heard; onSilence = silence; partial = ""; listening = true
        main.postDelayed({ if (seq == listenSeq && listening) { endListening(timeout = true); onChange() } }, windowMs)
        val s = stt
        if (s == null || !sttReady) { sttState = "준비 안 됨 · 화면 버튼으로 답하세요"; onChange(); return }
        try { s.request(); sttState = "듣는 중" } catch (e: Exception) { sttState = "듣기 실패: ${e.javaClass.simpleName}" }
        onChange()
    }

    /** 시연 모드 시험 입력: 마이크 대신 들은 말을 넣습니다(화면에 [시험 입력]을 붙입니다). */
    fun injectHeard(text: String) { if (listening) { partial = text; deliverHeard(text); onChange() } }

    private fun deliverHeard(text: String) {
        val h = onHeard ?: return
        endListening(timeout = false)
        h(text)
    }

    private fun endListening(timeout: Boolean) {
        val silence = onSilence
        listening = false; onHeard = null; onSilence = null; listenSeq++
        runCatching { stt?.stop() }
        if (timeout) silence?.invoke()
    }

    fun stopListening() {
        if (!listening) return
        listening = false; onHeard = null; onSilence = null; listenSeq++
        runCatching { stt?.stop() }
    }

    fun release() {
        stopSpeaking(); stopListening()
        runCatching { tts?.removeEventListener(ttsEvents); tts?.release() }
        runCatching { stt?.removeListener(sttEvents); stt?.release() }
        tts = null; stt = null
    }

    companion object { const val TAG = "PleosGuardVoice" }
}
