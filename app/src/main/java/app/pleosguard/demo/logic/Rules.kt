package app.pleosguard.demo.logic

/**
 * 판정 규칙. 값은 계약 문서 docs/specs/vehicle-care/rules-lab.json 2.2.0과 같습니다.
 * 화면·SDK와 떼어 두어 JVM 단위 시험으로 확인합니다.
 */
object RulesLab {
    /** "말로 하기" 최대 듣기 시간 */
    const val VOICE_LISTEN_MS = 8_000L
    /** 긴급 화면(보호자 발송 확인)으로 바뀐 직후 터치를 받지 않는 시간 */
    const val INPUT_GUARD_MS = 500L
    /** 보호자 발송 확인(10초) */
    const val COUNTDOWN_S = 10
    /** notification.kakao_delivery_confirmation_deadline_ms (알림톡 전달 확인 기한) */
    const val KAKAO_DEADLINE_MS = 10_000L
    /** signal_validity.periodic.freshness_ms (차속) */
    const val SPEED_FRESHNESS_MS = 3_000L
}

/** 신호 품질. 시험 입력은 화면에 [시험 입력]을 붙이고, 섞인 사건은 모의 발송만 합니다. */
enum class Quality { OK, TEST_INPUT, STALE, MISSING }


/** "말로 하기"로 들은 말을 나눕니다(H1). 도움 요청 낱말을 기록보다 먼저 봅니다. */
enum class VoiceRoute { HELP_REQUEST, WARNING_FIND, RECORD_MEMO }

object VoiceRouter {
    fun route(text: String): VoiceRoute {
        val t = text.replace(" ", "")
        return when {
            t.contains("도와") || t.contains("도움") || t.contains("사고") -> VoiceRoute.HELP_REQUEST
            t.contains("경고등") || t.contains("불이들어") || t.contains("불이켜") -> VoiceRoute.WARNING_FIND
            else -> VoiceRoute.RECORD_MEMO
        }
    }
}

