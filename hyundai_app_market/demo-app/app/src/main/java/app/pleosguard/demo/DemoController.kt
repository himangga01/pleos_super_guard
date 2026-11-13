package app.pleosguard.demo

import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.pleosguard.demo.logic.AccelCandidateDetector
import app.pleosguard.demo.logic.ConfirmPromptDetector
import app.pleosguard.demo.logic.Quality
import app.pleosguard.demo.logic.RulesLab
import app.pleosguard.demo.logic.SteppedScenes
import app.pleosguard.demo.logic.VoiceAnswer
import app.pleosguard.demo.logic.VoiceAnswerParser
import app.pleosguard.demo.logic.VoiceRoute
import app.pleosguard.demo.logic.VoiceRouter
import app.pleosguard.demo.sdk.VehicleSignals
import app.pleosguard.demo.sdk.Voice
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class Screen { HOME, WARN_FIND, WARN_RESULT, RECORD, GUARDIAN, SERVICE, SITUATION, CONFIRM, ACCEL_GUIDE, DECEL_GUIDE, RECIPIENTS, COUNTDOWN, SDK_CHECK }

/** 주행 상태를 어디서 얻는지. 실제(CarUxRestrictions)가 기본이고, 시연 모드에서 시험 입력으로 바꿀 수 있습니다. */
enum class DriveSource { REAL, TEST_DRIVING, TEST_PARKED }

/** 시간표 한 칸(100ms마다). 값이 없음(null)은 그대로 둡니다. */
data class Sample(val t: Long, val speed: Float?, val speedQ: Quality, val brake: Boolean?, val brakeTest: Boolean, val steering: Float?, val turn: TurnSignalValue?)

data class RecordSnap(val t0: Long, val wall: Long, val drivingAtRecord: Boolean, var symptom: String? = "소음", var memo: String? = null, var memoTest: Boolean = false)

enum class IncidentKind(val label: String) { AIRBAG("에어백 전개 신호(운전석)"), ACCIDENT("사고가 났어요(운전자 선택)"), HELP("도움 요청(운전자 선택)"), DECEL("감속 실패 대처 중 도움 요청") }

data class Incident(val kind: IncidentKind, val detectedWall: Long, val testInput: Boolean, val countdownFrom: Long?, var sentWall: Long? = null, var cancelled: Boolean = false)

/**
 * 데모 흐름의 상태와 판정. 실제 SDK 값(브레이크·조향·방향지시등·차속·주행 제한)과 시연 모드 시험 입력을 나눠 들고,
 * 시험 입력이 하나라도 섞인 사건은 모의 발송만 합니다. 서버·알림톡·문자는 이 앱에서 보내지 않습니다(모의).
 */
class DemoController(private val signals: VehicleSignals?, private val voice: Voice?, private val now: () -> Long = { SystemClock.elapsedRealtime() }, private val wallClock: () -> Long = { System.currentTimeMillis() }) {
    /**
     * 시연 모드 "대표 사례 시각". 문서의 대표 사례(21:00:03 에어백 신호, 21:00:13 발송 등)와 같은 시각을 화면에 보이려고
     * 표시용 시계를 옮깁니다. 0이면 실제 시각입니다. 화면 캡처를 문서 그림으로 쓸 때만 켭니다.
     */
    var clockOffsetMs by mutableStateOf(0L)
    private fun wallNow(): Long = wallClock() + clockOffsetMs

    /** [hms]("HH:mm:ss")가 지금 시각이 되도록 표시용 시계를 맞춥니다(Asia/Seoul, 오늘 날짜). */
    fun setCaseTime(hms: String) {
        val t = java.time.LocalTime.parse(hms)
        val today = java.time.LocalDate.now(SEOUL)
        val target = java.time.ZonedDateTime.of(today, t, SEOUL).toInstant().toEpochMilli()
        clockOffsetMs = target - wallClock()
    }

    var screen by mutableStateOf(Screen.HOME)
    var tick by mutableIntStateOf(0)
        private set
    /** SDK 콜백(신호·음성)이 올 때마다 늘어납니다. 화면이 실제 값을 다시 읽게 합니다. */
    var sig by mutableIntStateOf(0)
        private set
    fun bump() { sig++ }
    var demoPanel by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)
        private set

    // 시연 모드 시험 입력
    var driveSource by mutableStateOf(DriveSource.REAL)
    var testSpeedKmh by mutableStateOf<Float?>(null)
    var testBrakeUntil by mutableStateOf<Long?>(null)

    // 경고등 찾기
    var warnColor by mutableStateOf<String?>(null)
    var warnShape by mutableStateOf<String?>(null)
    var warnMode by mutableStateOf<String?>(null)

    // 기록
    val records = mutableListOf<RecordSnap>()
    var lastRecord by mutableStateOf<RecordSnap?>(null)
    var selectedPart by mutableStateOf<String?>(null)

    // 확인 질문·긴급 안내
    val detector = ConfirmPromptDetector(minStartSpeed = RulesLab.MIN_PROMPT_START_MPS)
    val accelDetector = AccelCandidateDetector()
    var promptReason by mutableStateOf("")
    var promptSpeedTest by mutableStateOf(false)
    var promptAnswerHeard by mutableStateOf<String?>(null)
    val scenes = SteppedScenes()
    var sceneIndex by mutableIntStateOf(0)
    var guideVoiceStep by mutableIntStateOf(-1)

    // 보호자 알림
    var incident by mutableStateOf<Incident?>(null)
    val history = ArrayDeque<Sample>()
    var heardTest by mutableStateOf(false)

    private val main = Handler(Looper.getMainLooper())
    private var running = false
    private var toastSeq = 0

    private val loop = object : Runnable {
        override fun run() { if (!running) return; step(); main.postDelayed(this, 100) }
    }

    fun start() { if (!running) { running = true; main.post(loop) } }
    fun stop() { running = false; main.removeCallbacks(loop) }

    // ---------------------------------------------------------------- 신호
    // SDK 값(일반 변수)을 100ms마다 Compose 상태로 옮깁니다. 화면은 이 상태만 읽어야 값이 바뀔 때 다시 그려집니다.
    // (2026-11-13 로컬 에뮬레이터에서 Pleos 계기판은 66km/h인데 앱은 0km/h로 남은 문제의 원인. strong skipping으로
    //  같은 controller 객체를 받는 화면은 건너뛰므로 일반 변수의 변화는 화면에 닿지 않았습니다.)
    var speedView by mutableStateOf<Pair<Float?, Quality>>(null to Quality.MISSING)
        private set
    var brakeView by mutableStateOf<Pair<Boolean?, Boolean>>(null to false)
        private set
    var restrictedView by mutableStateOf<Boolean?>(null)
        private set
    /** 급가속 후보(확인 질문 없이 기록만)의 마지막 시각(wall). */
    var accelCandidateWall by mutableStateOf<Long?>(null)
        private set
    /** 질문 조건은 맞았지만 5분 억제로 묻지 않은 마지막 시각(wall). */
    var suppressedWall by mutableStateOf<Long?>(null)
        private set

    val driving: Boolean
        get() = when (driveSource) {
            DriveSource.TEST_DRIVING -> true
            DriveSource.TEST_PARKED -> false
            DriveSource.REAL -> restrictedView ?: true // 알 수 없으면 주행 중으로 봅니다(D9)
        }
    val drivingIsTest get() = driveSource != DriveSource.REAL
    val drivingUnknown get() = driveSource == DriveSource.REAL && restrictedView == null

    fun speedNow(): Pair<Float?, Quality> {
        testSpeedKmh?.let { return (it / 3.6f) to Quality.TEST_INPUT }
        val v = signals?.speedMps ?: return null to Quality.MISSING
        val at = signals.speedAt ?: return null to Quality.MISSING
        return if (now() - at <= RulesLab.SPEED_FRESHNESS_MS) v to Quality.OK else v to Quality.STALE
    }

    fun brakeNow(): Pair<Boolean?, Boolean> {
        val until = testBrakeUntil
        if (until != null && now() < until) return true to true
        return signals?.brake to false
    }

    private fun step() {
        val t = now()
        val sv = speedNow(); val (v, q) = sv
        val bv = brakeNow(); val (b, bTest) = bv
        speedView = sv; brakeView = bv; restrictedView = signals?.restricted
        history.addLast(Sample(t, v, q, b, bTest, signals?.steeringDeg, signals?.turn))
        while (history.isNotEmpty() && t - history.first().t > 120_000) history.removeFirst() // 기록 화면을 늦게 열어도 앞 20초가 남도록 2분 보관

        if (accelDetector.onSample(t, v, q)) accelCandidateWall = wallNow() // 기록만(PRD 3.3.2)
        when (detector.onSample(t, v, q, b)) {
            ConfirmPromptDetector.Decision.PROMPT -> if (screen !in EMERGENCY) openConfirm(q == Quality.TEST_INPUT, bTest)
            ConfirmPromptDetector.Decision.SUPPRESSED -> suppressedWall = wallNow()
            else -> Unit
        }
        val inc = incident
        if (screen == Screen.COUNTDOWN && inc != null && inc.sentWall == null && !inc.cancelled && countdownLeft() <= 0) send()
        tick++
    }

    // ---------------------------------------------------------------- 이동
    fun go(s: Screen) { screen = s; demoPanel = false }

    fun back() {
        when (screen) {
            Screen.WARN_RESULT -> go(Screen.WARN_FIND)
            Screen.CONFIRM -> answerPrompt(VoiceAnswer.OK, byVoice = false)
            Screen.ACCEL_GUIDE, Screen.DECEL_GUIDE -> endGuide()
            Screen.COUNTDOWN -> cancelSend()
            else -> go(Screen.HOME)
        }
    }

    fun showToast(text: String) {
        val seq = ++toastSeq; toast = text
        main.postDelayed({ if (seq == toastSeq) toast = null }, 3_500)
    }

    // ---------------------------------------------------------------- 기록
    fun recordNow() {
        val snap = RecordSnap(now(), wallNow(), driving)
        records.add(snap); lastRecord = snap
        if (driving) {
            voice?.speak("기록했습니다.")
            showToast("기록했습니다 ${hms(snap.wall)} · 자세한 내용은 정차 후에 봅니다")
        } else {
            go(Screen.RECORD)
        }
    }

    fun memoByVoice() {
        val r = lastRecord ?: return
        voice?.listen(heard = { text -> r.memo = text; r.memoTest = heardTest; heardTest = false; lastRecord = r.copy() },
            silence = { showToast("말을 듣지 못했습니다") })
    }

    fun talk() {
        voice?.listen(heard = { text ->
            val test = heardTest; heardTest = false
            when (VoiceRouter.route(text)) {
                VoiceRoute.DECEL_GUIDE -> startGuide(Screen.DECEL_GUIDE)
                VoiceRoute.ACCEL_GUIDE -> startGuide(Screen.ACCEL_GUIDE)
                VoiceRoute.HELP_REQUEST -> openRecipients(IncidentKind.HELP)
                VoiceRoute.WARNING_FIND -> go(Screen.WARN_FIND)
                VoiceRoute.RECORD_MEMO -> {
                    val snap = RecordSnap(now(), wallNow(), driving, symptom = null, memo = text, memoTest = test)
                    records.add(snap); lastRecord = snap
                    voice?.speak("메모를 남겼습니다."); showToast("말로 메모: $text")
                }
            }
        }, silence = { showToast("말을 듣지 못했습니다") })
    }

    // ---------------------------------------------------------------- 확인 질문
    fun openConfirm(speedTest: Boolean, brakeTest: Boolean) {
        val rose = detector.lastSpeedChange?.let { it >= RulesLab.MIN_SPEED_DROP_MPS } == true
        promptReason = if (rose) "브레이크를 밟은 1초 동안\n속도가 오히려 빨라졌습니다" else "브레이크를 밟은 1초 동안\n속도가 줄지 않았습니다"
        promptSpeedTest = speedTest || brakeTest
        promptAnswerHeard = null
        go(Screen.CONFIRM)
        voice?.speak("차가 멈추지 않나요?") {
            if (screen == Screen.CONFIRM) voice?.listen(heard = { text ->
                promptAnswerHeard = text
                val a = VoiceAnswerParser.parse(text)
                if (a == VoiceAnswer.UNKNOWN) { showToast("\"$text\" · 화면 버튼으로 답해 주세요") } else answerPrompt(a, byVoice = true)
            }, silence = {
                if (screen == Screen.CONFIRM) { go(Screen.HOME); showToast("답이 없어 질문을 닫았습니다. 기록은 남깁니다") }
            })
        }
    }

    /** 시연 모드: 센서 조합을 시험 입력으로 만듭니다(차속 60km/h 유지 + 브레이크 1.5초). */
    fun simulateSensorCombination() {
        if (testSpeedKmh == null) testSpeedKmh = 60f
        detector.resetTrip()
        testBrakeUntil = now() + 1_500
        demoPanel = false
    }

    fun answerPrompt(a: VoiceAnswer, byVoice: Boolean) {
        voice?.stopListening()
        when (a) {
            VoiceAnswer.NOT_STOPPING -> startGuide(Screen.DECEL_GUIDE)
            else -> { voice?.stopSpeaking(); go(Screen.HOME); showToast(if (byVoice) "\"괜찮아요\"를 들었습니다. 질문을 닫았습니다" else "질문을 닫았습니다") }
        }
    }

    // ---------------------------------------------------------------- 긴급 안내(단계형 애니메이션 + 음성)
    private var guideSeq = 0

    fun startGuide(s: Screen) {
        val seq = ++guideSeq
        go(s)
        scenes.start(now()); sceneIndex = 0; guideVoiceStep = -1
        voice?.speak("앞을 보세요. 음성 안내를 따라 주세요.") { if (seq == guideSeq) speakGuideStep(seq, 0) }
            ?: speakGuideStep(seq, 0)
    }

    private fun procName() = if (screen == Screen.DECEL_GUIDE) "비상 제동 절차" else "비상 절차"

    fun guideSentence(step: Int = guideVoiceStep) =
        if (step < 0) "취급설명서 ${procName()}\n(검토 후 확정 문장)" else "취급설명서 ${procName()} ${step + 1}단계\n(검토 후 확정 문장)"

    private fun speakGuideStep(seq: Int, step: Int) {
        if (seq != guideSeq || screen !in GUIDES) return
        // 그림 장면은 음성 단계와 같이 바뀌되 앞 장면을 최소 1,000ms 보인 뒤에만 바뀝니다. 마지막 장면에서 멈춥니다.
        val wait = (scenes.earliestNextAt() - now()).coerceAtLeast(0)
        main.postDelayed({
            if (seq != guideSeq || screen !in GUIDES) return@postDelayed
            if (step > 0) { scenes.advance(now()); sceneIndex = scenes.index }
            guideVoiceStep = step
            // 실제 문장은 대상 차량 취급설명서의 검토된 비상 절차 문장만 씁니다. 지금은 문장 자리만 읽습니다.
            val text = "취급설명서 ${procName()} ${step + 1}단계 문장 자리입니다."
            val next = { if (step + 1 < RulesLab.KEY_SCENES) speakGuideStep(seq, step + 1) }
            voice?.speak(text) { next() } ?: main.postDelayed({ next() }, 2_500)
        }, if (step == 0) 0 else wait)
    }

    fun endGuide() {
        guideSeq++; voice?.stopSpeaking()
        val wasDecel = screen == Screen.DECEL_GUIDE
        go(Screen.HOME)
        showToast(if (wasDecel) "안내를 끝냈습니다. 정차 후 확인합니다" else "안내를 끝냈습니다. 기록은 남깁니다")
    }

    // ---------------------------------------------------------------- 보호자 알림(모의)
    fun openRecipients(kind: IncidentKind) {
        guideSeq++; voice?.stopSpeaking()
        incident = Incident(kind, wallNow(), testInput = drivingIsTest || testSpeedKmh != null, countdownFrom = null)
        go(Screen.RECIPIENTS)
    }

    /** 에어백 전개 신호(시험 입력) 또는 "사고가 났어요" → 발송 확인 10초. */
    fun startCountdown(kind: IncidentKind, testInput: Boolean) {
        guideSeq++
        incident = Incident(kind, wallNow(), testInput = testInput || drivingIsTest || testSpeedKmh != null, countdownFrom = now())
        demoPanel = false
        go(Screen.COUNTDOWN)
        voice?.speak(if (kind == IncidentKind.AIRBAG) "에어백 전개 신호를 받았습니다. 10초 뒤 보호자에게 알립니다." else "10초 뒤 보호자에게 알립니다.")
    }

    fun countdownLeft(): Int {
        val from = incident?.countdownFrom ?: return RulesLab.COUNTDOWN_S
        val left = RulesLab.COUNTDOWN_S - ((now() - from) / 1000).toInt()
        return left.coerceAtLeast(0)
    }

    fun send() {
        val inc = incident ?: return
        if (inc.sentWall != null) return
        inc.sentWall = wallNow()
        incident = inc.copy()
        go(Screen.GUARDIAN)
        voice?.speak("보호자에게 알렸습니다. 위치를 보내는 중입니다.")
    }

    fun cancelSend() {
        incident?.cancelled = true
        voice?.stopSpeaking()
        go(Screen.HOME)
        showToast("보내지 않았습니다")
    }

    fun stopSharing() {
        incident = null
        showToast("위치 공유를 멈췄습니다(모의)")
        go(Screen.HOME)
    }

    /** 모의 발송 뒤 보호자별 상태. 대표 사례의 순서(접수 → 전달 → 열람, 알림톡 전달 확인 기한 10초 뒤 문자)를 빠르게 재생합니다. */
    fun guardianRows(): List<Pair<String, String>> {
        val sent = incident?.sentWall ?: return emptyList()
        val el = wallNow() - sent
        val g1 = when {
            el < 2_000 -> "접수 · 알림톡"
            el < G1_OPEN_MS -> "전달 · 알림톡"
            else -> "전달 · 열람 ${hm(sent + G1_OPEN_MS)} · 전화할게요"
        }
        val g2 = if (el < RulesLab.KAKAO_DEADLINE_MS) "접수 · 알림톡 전달 확인 중" else "문자로 다시 보냄 ${hms(sent + RulesLab.KAKAO_DEADLINE_MS)}"
        return listOf("보호자 1" to g1, "보호자 2" to g2)
    }

    fun guardianDone(i: Int): Boolean {
        val sent = incident?.sentWall ?: return false
        val el = wallNow() - sent
        return if (i == 0) el >= G1_OPEN_MS else el >= RulesLab.KAKAO_DEADLINE_MS
    }

    // ---------------------------------------------------------------- 시연 패널
    fun cycleDrive() {
        driveSource = when (driveSource) { DriveSource.REAL -> DriveSource.TEST_DRIVING; DriveSource.TEST_DRIVING -> DriveSource.TEST_PARKED; DriveSource.TEST_PARKED -> DriveSource.REAL }
    }

    fun toggleTestSpeed() { testSpeedKmh = if (testSpeedKmh == null) 60f else null; detector.resetTrip() }

    companion object {
        val EMERGENCY = setOf(Screen.CONFIRM, Screen.ACCEL_GUIDE, Screen.DECEL_GUIDE, Screen.COUNTDOWN, Screen.RECIPIENTS)
        val GUIDES = setOf(Screen.ACCEL_GUIDE, Screen.DECEL_GUIDE)
        /** 모의 보호자 1 열람 시각: 대표 사례(21:00:13 보냄 → 21:01 열람)에 맞춰 보낸 뒤 47초. */
        const val G1_OPEN_MS = 47_000L
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        private val HMS = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(SEOUL)
        private val HM = DateTimeFormatter.ofPattern("HH:mm").withZone(SEOUL)
        fun hms(wall: Long): String = HMS.format(Instant.ofEpochMilli(wall))
        fun hm(wall: Long): String = HM.format(Instant.ofEpochMilli(wall))
    }
}
