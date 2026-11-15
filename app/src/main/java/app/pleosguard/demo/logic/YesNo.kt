package app.pleosguard.demo.logic

/**
 * 사고 대처 안내의 말 답(2026-11-14 사용자 요청: "예(화면 터치 또는 음성입력)").
 * 질문 바로 뒤 8초 동안만 듣고, 알아듣지 못하면 화면 단추로 답하게 합니다. 띄어쓰기는 무시합니다.
 */
object YesNo {
    // "아니"가 먼저입니다: "아니예요"에는 "예"도 들어 있습니다.
    private val NO = listOf("아니", "아뇨", "아냐", "괜찮", "취소", "하지마", "싫어", "나중에", "됐어", "그만")
    private val YES = listOf("예", "네", "응", "맞아", "맞습니다", "맞아요", "그래", "좋아", "연결", "해줘", "해주세요", "부탁", "걸어", "전화")

    /** true = 예, false = 아니요, null = 알아듣지 못함. */
    fun parse(text: String): Boolean? {
        val t = text.replace(" ", "")
        if (t.isEmpty()) return null
        return when {
            NO.any { t.contains(it) } -> false
            YES.any { t.contains(it) } -> true
            else -> null
        }
    }
}

/** 몸 상태(사고 접수 뒤). */
enum class BodyState(val label: String) { OK("괜찮아요"), MINOR("조금 아파요"), SEVERE("많이 다쳤어요") }

object BodyAnswer {
    private val NONE = listOf("안아파", "다친데없", "다친곳없", "안다쳤")
    private val SEVERE = listOf("많이", "심하", "피가", "피나", "움직일수없", "못움직", "119", "구급", "응급", "의식")
    private val MILD = listOf("조금", "약간", "살짝")
    private val PAIN = listOf("아파", "아프", "결려", "쑤셔")
    private val OK = listOf("괜찮", "멀쩡", "이상없", "없어")

    /**
     * 말에서 몸 상태를 고릅니다. 순서: "안 아파요"(괜찮음) → 심한 말(많이 · 피 · 119 …) → "조금 · 약간"(조금 아픔)
     * → 그냥 "다쳤어요"(모르면 더 급하게 봐서 심함) → 아픔(조금 아픔) → 괜찮음.
     */
    fun parse(text: String): BodyState? {
        val t = text.replace(" ", "")
        if (t.isEmpty()) return null
        return when {
            NONE.any { t.contains(it) } -> BodyState.OK
            SEVERE.any { t.contains(it) } -> BodyState.SEVERE
            MILD.any { t.contains(it) } -> BodyState.MINOR
            t.contains("다쳤") -> BodyState.SEVERE
            PAIN.any { t.contains(it) } -> BodyState.MINOR
            OK.any { t.contains(it) } -> BodyState.OK
            else -> null
        }
    }
}
