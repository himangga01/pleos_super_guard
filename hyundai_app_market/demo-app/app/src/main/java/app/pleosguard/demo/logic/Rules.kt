package app.pleosguard.demo.logic

/**
 * 판정 규칙. 값은 계약 문서 docs/specs/vehicle-care/rules-lab.json 2.2.0과 같습니다.
 * 화면·SDK와 떼어 두어 JVM 단위 시험으로 확인합니다.
 */
object RulesLab {
    /** guidance.confirm_prompt_conditions.minimum_moving_speed_m_s */
    const val MIN_MOVING_SPEED_MPS = 1.0f
    /** guidance.confirm_prompt_conditions.brake_pressed_no_deceleration_observation_ms */
    const val BRAKE_OBSERVATION_MS = 1_000L
    /** guidance.confirm_prompt_conditions.minimum_speed_drop_m_s */
    const val MIN_SPEED_DROP_MPS = 0.5f
    /**
     * 확인 질문 관측을 시작할 최소 속도 15km/h. 정차 출발 직후·저속 구간은 차량 PMSA가 맡으므로 질문하지 않습니다(PRD 3.3.2).
     * 근거(docs/analysis/sudden-acceleration-2026-11-13.md): 페달 오조작 방지 장치의 대상 구간이 정지·크리프(UN R175 시험),
     * 개조형 장치는 "정차 또는 15km/h 이하"입니다. 값 자체는 데모 앱의 자체 결정이며 계약(rules-lab)에는 아직 넣지 않았습니다.
     * 브레이크를 밟은 채 속도가 이 값을 넘어 올라가면 그때부터 관측합니다(정차·저속에서 시작한 원치 않는 가속).
     */
    const val MIN_PROMPT_START_MPS = 15f / 3.6f
    /** guidance.confirm_prompt_conditions.unwanted_accel_suspect_m_s2 (급가속 후보, 기록만) */
    const val ACCEL_CANDIDATE_MPS2 = 2.5f
    /** guidance.confirm_prompt_conditions.unwanted_accel_hold_ms */
    const val ACCEL_CANDIDATE_HOLD_MS = 700L
    /** guidance.confirm_prompt_suppression.window_ms (같은 주행에서 5분에 1회) */
    const val PROMPT_SUPPRESSION_MS = 300_000L
    /** guidance.confirm_prompt_answers.voice_listen_window_ms, "말로 하기" 최대 듣기 시간 */
    const val VOICE_LISTEN_MS = 8_000L
    /** guidance.input_guard_ms_after_emergency_transition */
    const val INPUT_GUARD_MS = 500L
    /** guidance.emergency_visual.min_scene_hold_ms (1초에 한 장 이하) */
    const val MIN_SCENE_HOLD_MS = 1_000L
    /** guidance.emergency_visual.key_scenes_example_count */
    const val KEY_SCENES = 3
    /** 보호자 발송 확인(10초) */
    const val COUNTDOWN_S = 10
    /** notification.kakao_delivery_confirmation_deadline_ms (알림톡 전달 확인 기한) */
    const val KAKAO_DEADLINE_MS = 10_000L
    /** signal_validity.periodic.freshness_ms (차속) */
    const val SPEED_FRESHNESS_MS = 3_000L
}

/** 신호 품질. 시험 입력은 화면에 [시험 입력]을 붙이고, 섞인 사건은 모의 발송만 합니다. */
enum class Quality { OK, TEST_INPUT, STALE, MISSING }

/**
 * "차가 멈추지 않나요?" 확인 질문을 띄울지 정합니다(센서 조합은 질문만 띄우고 안내를 바로 시작하지 않습니다).
 * 조건: 움직이는 중(1.0m/s 이상) 브레이크가 눌린 뒤 1,000ms 동안 속도가 0.5m/s 이상 줄지 않음.
 * 같은 주행에서 5분에 1회까지입니다. 막힌 조건 충족은 [Decision.SUPPRESSED]로만 돌려줍니다.
 * 브레이크 값이 없음(null)은 false로 바꾸지 않고 관측을 멈춥니다.
 */
class ConfirmPromptDetector(
    private val minMoving: Float = RulesLab.MIN_MOVING_SPEED_MPS,
    private val observeMs: Long = RulesLab.BRAKE_OBSERVATION_MS,
    private val minDrop: Float = RulesLab.MIN_SPEED_DROP_MPS,
    private val windowMs: Long = RulesLab.PROMPT_SUPPRESSION_MS,
    /** 관측을 시작할 최소 속도. 정차 출발 직후·저속 구간(차량 PMSA 구간)은 질문하지 않습니다(PRD 3.3.2). */
    private val minStartSpeed: Float = RulesLab.MIN_MOVING_SPEED_MPS,
) {
    enum class Decision { NONE, PROMPT, SUPPRESSED, DECELERATING, LOW_SPEED }

    private var obsStart: Long? = null
    private var obsSpeed: Float = 0f
    private var finished = false
    private var lowSpeedReported = false
    private var lastBrake: Boolean? = null
    var lastPromptAt: Long? = null
        private set
    /** 마지막으로 끝난 관측에서 속도 변화(끝 - 시작, m/s). 양수면 브레이크를 밟는 동안 오히려 빨라진 것입니다. */
    var lastSpeedChange: Float? = null
        private set

    /** 새 주행이 시작되면 억제 창을 지웁니다. */
    fun resetTrip() { lastPromptAt = null; cancel() }

    private fun cancel() { obsStart = null; finished = false; lowSpeedReported = false }

    fun onSample(nowMs: Long, speedMps: Float?, speedQuality: Quality, brake: Boolean?): Decision {
        val speedUsable = speedMps != null && (speedQuality == Quality.OK || speedQuality == Quality.TEST_INPUT)
        val pressedEdge = brake == true && lastBrake != true
        lastBrake = brake
        if (brake != true || !speedUsable) { cancel(); return Decision.NONE }
        val v = speedMps!!
        if (pressedEdge || (obsStart == null && !finished)) {
            if (v < minMoving) return Decision.NONE
            if (v < minStartSpeed) {
                // 저속(PMSA) 구간: 질문하지 않습니다. 브레이크를 밟은 채 속도가 기준을 넘으면 다음 표본에서 관측을 시작합니다.
                if (lowSpeedReported) return Decision.NONE
                lowSpeedReported = true
                return Decision.LOW_SPEED
            }
            obsStart = nowMs; obsSpeed = v; finished = false
            return Decision.NONE
        }
        val start = obsStart ?: return Decision.NONE
        if (finished || nowMs - start < observeMs) return Decision.NONE
        finished = true
        lastSpeedChange = v - obsSpeed
        if (obsSpeed - v >= minDrop) return Decision.DECELERATING
        val last = lastPromptAt
        if (last != null && nowMs - last < windowMs) return Decision.SUPPRESSED
        lastPromptAt = nowMs
        return Decision.PROMPT
    }
}

/**
 * 급가속 후보: 이동 중(1.0m/s 이상) 가속도 2.5m/s² 이상이 700ms 이상 이어짐(rules-lab confirm_prompt_conditions).
 * 고속도로 합류 같은 정상 가속도 넘을 수 있어 화면·음성 없이 기록만 합니다(PRD 3.3.2, candidate_output_without_prompt = NONE).
 * 가속도는 [windowMs] 앞의 표본과 견줘 계산합니다(차속이 140ms 안팎 간격의 정수 km/h로 오는 경우를 고려).
 */
class AccelCandidateDetector(
    private val minMoving: Float = RulesLab.MIN_MOVING_SPEED_MPS,
    private val minAccel: Float = RulesLab.ACCEL_CANDIDATE_MPS2,
    private val holdMs: Long = RulesLab.ACCEL_CANDIDATE_HOLD_MS,
    private val windowMs: Long = 500L,
) {
    private val buf = ArrayDeque<Pair<Long, Float>>()
    private var aboveSince: Long? = null
    private var latched = false
    /** 마지막으로 계산한 가속도(m/s²). 계산할 수 없으면 null. */
    var lastAccel: Float? = null
        private set

    fun onSample(nowMs: Long, speedMps: Float?, speedQuality: Quality): Boolean {
        if (speedMps == null || !(speedQuality == Quality.OK || speedQuality == Quality.TEST_INPUT)) {
            buf.clear(); aboveSince = null; latched = false; lastAccel = null; return false
        }
        buf.addLast(nowMs to speedMps)
        while (buf.size > 1 && nowMs - buf[1].first >= windowMs) buf.removeFirst()
        val (t0, v0) = buf.first()
        if (nowMs - t0 < windowMs / 2) return false
        val a = (speedMps - v0) / ((nowMs - t0) / 1000f)
        lastAccel = a
        if (speedMps >= minMoving && a >= minAccel) {
            val since = aboveSince ?: nowMs.also { aboveSince = it }
            if (!latched && nowMs - since >= holdMs) { latched = true; return true }
        } else { aboveSince = null; latched = false }
        return false
    }
}

/** 확인 질문에 말로 한 답. rules-lab guidance.confirm_prompt_answers.voice_answers("안 멈춰", "괜찮아"). */
enum class VoiceAnswer { NOT_STOPPING, OK, UNKNOWN }

object VoiceAnswerParser {
    private fun norm(s: String) = s.replace(" ", "").replace(".", "").replace("?", "")

    fun parse(text: String): VoiceAnswer {
        val t = norm(text)
        if (t.isEmpty()) return VoiceAnswer.UNKNOWN
        // "안 멈춰"가 "괜찮아"보다 먼저입니다. 위험한 쪽 답을 놓치지 않기 위해서입니다.
        if (t.contains("안멈춰") || t.contains("멈추지않") || t.contains("안서") || t.contains("안멈춤")) return VoiceAnswer.NOT_STOPPING
        if (t.contains("괜찮")) return VoiceAnswer.OK
        return VoiceAnswer.UNKNOWN
    }
}

/** "말로 하기"로 들은 말을 나눕니다(H1). 긴급 낱말은 기록보다 먼저 봅니다. */
enum class VoiceRoute { DECEL_GUIDE, ACCEL_GUIDE, HELP_REQUEST, WARNING_FIND, RECORD_MEMO }

object VoiceRouter {
    fun route(text: String): VoiceRoute {
        val t = text.replace(" ", "")
        return when {
            t.contains("안멈춰") || t.contains("멈추지않") -> VoiceRoute.DECEL_GUIDE
            t.contains("빨라져") || t.contains("저절로") -> VoiceRoute.ACCEL_GUIDE
            t.contains("도와") || t.contains("도움") || t.contains("사고") -> VoiceRoute.HELP_REQUEST
            t.contains("경고등") || t.contains("불이들어") || t.contains("불이켜") -> VoiceRoute.WARNING_FIND
            else -> VoiceRoute.RECORD_MEMO
        }
    }
}

/**
 * 긴급 안내의 단계형 설명 애니메이션 시계입니다. 장면은 음성 단계와 함께 바뀌지만
 * 앞 장면이 최소 1,000ms 보인 뒤에만 바뀌고, 마지막 장면에서 멈춥니다(반복 없음).
 */
class SteppedScenes(private val count: Int = RulesLab.KEY_SCENES, private val minHoldMs: Long = RulesLab.MIN_SCENE_HOLD_MS) {
    var index = 0
        private set
    private var shownAt = 0L

    fun start(nowMs: Long) { index = 0; shownAt = nowMs }

    /** 다음 장면을 요청합니다. 바뀌면 true. 최소 유지 시간 전이거나 마지막 장면이면 false. */
    fun advance(nowMs: Long): Boolean {
        if (index >= count - 1) return false
        if (nowMs - shownAt < minHoldMs) return false
        index += 1; shownAt = nowMs
        return true
    }

    /** 다음 장면으로 넘어갈 수 있는 가장 이른 시각. */
    fun earliestNextAt(): Long = shownAt + minHoldMs
    val isLast get() = index >= count - 1
}
