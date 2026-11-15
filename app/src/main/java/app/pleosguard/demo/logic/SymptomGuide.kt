package app.pleosguard.demo.logic

/*
 * 대처 안내(2026-11-14 사용자 요청): "도움이 필요해요" → 증상 고르기(최대 3개) → 증상 조합에 맞춘 확인 순서 → 해결이 안 되면 증상 기록 → 정비소에 도움 요청.
 * 일반적인 자동차 관리 상식을 짧게 옮긴 안내 예시이며 진단이 아닙니다. 아반떼 취급설명서로 검토하기 전이라 화면에 "일반 안내 · 진단 아님"을 붙입니다.
 * 안전이 먼저입니다: 타는 냄새 · 페달 이상 · 떨림과 타이어 이상이 함께 있으면 '바로 세우기'로 올립니다.
 */

/** 안내의 급한 정도. 앞쪽일수록 급합니다. */
enum class GuideLevel(val label: String) { STOP("바로 세우세요"), SOON("점검이 필요해요"), CHECK("먼저 확인해요") }

/** 단계의 그림 뜻. 화면이 아이콘으로 바꿉니다. */
enum class StepMark { STOP, SMOKE, HOOD, TIRE, WHEEL, SPEED, BRAKE, PEDAL, MAT, ROAD, WARNING, GEAR, RESTART, FUEL, LISTEN, LOOK, CARGO, PHONE, SHOP, HAZARD }

/** 단계에서 바로 여는 기능. */
enum class GuideAction(val label: String) {
    WARN_FIND("경고등 찾기"),
    PHONE_NOISE("폰으로 소리 기록"),
    PHONE_VIBRATION("폰으로 진동 기록"),
    PHONE_WARN_PHOTO("폰으로 경고등 찍기"),
}

/**
 * 확인 단계 하나. [key]가 같은 단계는 한 번만 보이고(예: 여러 증상의 '경고등 확인'), 그 단계의 [from]에 증상 이름이 모입니다.
 * [from]은 짧은 증상 이름(떨림 · 타이어 …)입니다.
 */
data class GuideStep(
    val mark: StepMark,
    val title: String,
    val detail: String,
    val action: GuideAction? = null,
    val key: String = title,
    val from: List<String> = emptyList(),
)

data class Guide(
    /** 안내에 쓴 증상(알 수 없는 이름은 뺌). */
    val symptoms: List<String>,
    val level: GuideLevel,
    /** 맨 위 한 줄. */
    val headline: String,
    /** 맞은 조합(예: "떨림 + 타이어"). 없으면 null. */
    val combos: List<String>,
    /** 확인 순서(최대 [SymptomGuide.MAX_STEPS]). */
    val steps: List<GuideStep>,
    /** 자주 있는 원인(진단 아님, 최대 4). */
    val causes: List<String>,
    /** 주행 중에 읽을 한 줄. */
    val voice: String,
)

object SymptomGuide {
    const val MAX_STEPS = 7
    const val UNSURE = "잘 모르겠음"

    /** 상황 고르기의 증상 9개(화면과 같은 이름 · 같은 순서). */
    val SYMPTOMS = listOf("차체 떨림", "핸들 쏠림", "이상한 소음", "타는 냄새", "페달 이상", "타이어 이상", "힘이 없어요", "경고등이 켜졌어요", UNSURE)

    /** 단계 꼬리표에 쓰는 짧은 이름. */
    val SHORT = mapOf(
        "차체 떨림" to "떨림", "핸들 쏠림" to "쏠림", "이상한 소음" to "소음", "타는 냄새" to "냄새", "페달 이상" to "페달",
        "타이어 이상" to "타이어", "힘이 없어요" to "힘 없음", "경고등이 켜졌어요" to "경고등", UNSURE to "모름",
    )

    private data class Def(val level: GuideLevel, val headline: String, val steps: List<GuideStep>, val causes: List<String>)
    private data class Combo(val needs: Set<String>, val level: GuideLevel, val label: String, val headline: String, val steps: List<GuideStep>, val causes: List<String>)

    private val WARN = GuideStep(StepMark.WARNING, "경고등이 켜졌는지 보세요", "켜졌으면 경고등 찾기에서 뜻을 확인", GuideAction.WARN_FIND, key = "warn")
    private val RESTART = GuideStep(StepMark.RESTART, "시동을 껐다 다시 켜 보세요", "안전한 곳에 세우고, 끄고 1분 뒤", key = "restart")
    private val TIRE_LOOK = GuideStep(StepMark.TIRE, "타이어를 눈으로 보세요", "납작함 · 찢김 · 박힌 못이 있는지", key = "tire_look")

    private val DEFS: Map<String, Def> = mapOf(
        "차체 떨림" to Def(GuideLevel.SOON, "속도를 줄이고, 떨리는 때와 타이어를 확인하세요", listOf(
            GuideStep(StepMark.SPEED, "언제 떨리는지 보세요", "출발할 때 · 일정 속도 · 브레이크 밟을 때", key = "when_shake"),
            TIRE_LOOK,
            GuideStep(StepMark.BRAKE, "브레이크 밟을 때만 떨리나요", "그렇다면 브레이크 점검이 필요할 수 있어요", key = "brake_only"),
            GuideStep(StepMark.PHONE, "폰으로 진동을 기록하세요", "떨리는 곳에 폰을 두면 정비소에 보여 줄 수 있어요", GuideAction.PHONE_VIBRATION, key = "phone_vib"),
        ), listOf("타이어 균형(휠 밸런스)", "타이어 손상", "브레이크 디스크", "엔진 받침(마운트)")),

        "핸들 쏠림" to Def(GuideLevel.SOON, "평평한 길에서 다시 보고, 타이어 공기압을 확인하세요", listOf(
            GuideStep(StepMark.ROAD, "평평하고 곧은 길에서 다시 보세요", "길이 기울어도 쏠릴 수 있어요", key = "road"),
            WARN.copy(title = "공기압 경고등을 보세요"),
            GuideStep(StepMark.TIRE, "좌우 타이어를 비교하세요", "한쪽만 납작하면 공기압이 낮아요", key = "tire_compare"),
            GuideStep(StepMark.BRAKE, "브레이크 밟을 때만 쏠리나요", "그렇다면 브레이크 점검이 필요해요", key = "brake_pull"),
        ), listOf("좌우 공기압 차이", "휠 정렬(얼라인먼트)", "브레이크 한쪽 끌림")),

        "이상한 소음" to Def(GuideLevel.SOON, "소리가 언제 · 어디서 나는지 확인하세요", listOf(
            GuideStep(StepMark.LISTEN, "언제 나는지 보세요", "시동 · 출발 · 회전 · 브레이크 · 요철", key = "when_noise"),
            GuideStep(StepMark.LOOK, "어디서 나는지 보세요", "앞 · 뒤 · 바퀴 쪽 · 엔진룸", key = "where_noise"),
            GuideStep(StepMark.CARGO, "실내 물건을 확인하세요", "트렁크 · 수납함 물건이 굴러도 소리가 나요", key = "cargo"),
            GuideStep(StepMark.PHONE, "폰으로 소리를 기록하세요", "소리 나는 곳 가까이 대고 10초", GuideAction.PHONE_NOISE, key = "phone_noise"),
        ), listOf("브레이크 패드 마모(쇳소리)", "벨트(끼익 소리)", "하체 부품(덜컥 소리)", "실내 물건")),

        "타는 냄새" to Def(GuideLevel.STOP, "안전한 곳에 세우고 시동을 끄세요", listOf(
            GuideStep(StepMark.SMOKE, "연기 · 불꽃이 보이면 내리세요", "차에서 멀리 떨어진 뒤 119에 신고", key = "smoke"),
            GuideStep(StepMark.HOOD, "연기가 나면 보닛을 열지 마세요", "공기가 들어가 불이 커질 수 있어요", key = "hood"),
            GuideStep(StepMark.BRAKE, "주차 브레이크를 확인하세요", "채운 채 달리면 타는 냄새가 나요", key = "parking_brake"),
            WARN.copy(title = "온도 · 경고등을 보세요", detail = "연기가 없을 때만, 계기판에서"),
            GuideStep(StepMark.SHOP, "다시 달리기 전에 점검하세요", "긴급출동이나 정비소에 연락", key = "call_help"),
        ), listOf("브레이크 과열", "냉각수 · 엔진오일 샘", "전기 배선")),

        "페달 이상" to Def(GuideLevel.STOP, "천천히 안전한 곳에 세우세요", listOf(
            GuideStep(StepMark.MAT, "바닥 매트를 확인하세요", "매트가 페달에 걸리지 않았는지", key = "mat"),
            WARN.copy(title = "브레이크 경고등을 보세요", detail = "빨간 경고등이면 운행을 멈추세요"),
            GuideStep(StepMark.PEDAL, "페달 느낌을 기억하세요", "깊이 들어감 · 딱딱함 · 떨림을 기록", key = "pedal_feel"),
            GuideStep(StepMark.SHOP, "이상하면 긴급출동을 부르세요", "브레이크는 직접 고치지 마세요", key = "call_help"),
        ), listOf("바닥 매트 걸림", "브레이크액 부족", "브레이크 보조 장치")),

        "타이어 이상" to Def(GuideLevel.SOON, "세운 뒤 타이어 상태와 공기압을 확인하세요", listOf(
            WARN.copy(title = "공기압 경고등을 보세요"),
            TIRE_LOOK,
            GuideStep(StepMark.STOP, "옆면이 찢기거나 부풀었으면 멈추세요", "달리지 말고 긴급출동을 부르세요", key = "tire_stop"),
            GuideStep(StepMark.TIRE, "공기압이 낮으면 공기를 넣으세요", "가까운 정비소 · 주유소에서", key = "tire_air"),
        ), listOf("공기압 부족", "펑크", "타이어 손상")),

        "힘이 없어요" to Def(GuideLevel.SOON, "경고등을 보고, 세운 뒤 시동을 다시 걸어 보세요", listOf(
            WARN,
            GuideStep(StepMark.GEAR, "기어 · 주행 모드를 보세요", "D 위치인지, 에코 모드인지", key = "gear"),
            RESTART,
            GuideStep(StepMark.FUEL, "연료 · 배터리 양을 보세요", "계기판의 남은 양", key = "fuel"),
        ), listOf("엔진 보호 모드(출력 제한)", "연료 · 공기 흡입 계통", "하이브리드 시스템")),

        "경고등이 켜졌어요" to Def(GuideLevel.CHECK, "경고등의 뜻부터 확인하세요", listOf(
            WARN.copy(title = "경고등 찾기로 뜻을 보세요", detail = "색 → 모양 → 켜짐을 고르면 돼요"),
            GuideStep(StepMark.STOP, "빨간 경고등이면 세우세요", "안전한 곳에 세우고 시동을 끄세요", key = "red_stop"),
            GuideStep(StepMark.PHONE, "폰으로 경고등을 찍어 두세요", "정비소에 그대로 보여 줄 수 있어요", GuideAction.PHONE_WARN_PHOTO, key = "phone_photo"),
            RESTART.copy(title = "다시 켜도 그대로인지 보세요", detail = "계속 켜지면 기록해 두세요"),
        ), listOf("경고등 종류마다 달라요")),

        UNSURE to Def(GuideLevel.CHECK, "안전한 곳에 세우고 하나씩 확인해 보세요", listOf(
            GuideStep(StepMark.HAZARD, "비상등을 켜고 세우세요", "갓길보다 휴게소 · 주차장이 안전해요", key = "hazard"),
            WARN.copy(title = "계기판 경고등을 보세요"),
            RESTART,
            GuideStep(StepMark.PHONE, "소리 · 떨림이면 폰으로 기록하세요", "정비소에 보여 줄 수 있어요", GuideAction.PHONE_NOISE, key = "phone_noise"),
        ), emptyList()),
    )

    /** 증상 조합(앞쪽이 먼저). 조합 단계는 증상 단계보다 앞에 둡니다. */
    private val COMBOS: List<Combo> = listOf(
        Combo(setOf("차체 떨림", "타이어 이상"), GuideLevel.STOP, "떨림 + 타이어", "속도를 줄이고 세운 뒤 타이어부터 확인하세요", listOf(
            GuideStep(StepMark.TIRE, "모든 타이어를 확인하세요", "부풂 · 찢김 · 납작함이 있으면 달리지 마세요", key = "tire_look"),
            GuideStep(StepMark.WHEEL, "휠 볼트 · 이물질을 보세요", "휠에 진흙 · 돌이 끼어도 떨려요", key = "wheel_bolt"),
        ), listOf("타이어 손상", "휠 균형", "휠 볼트 풀림")),
        Combo(setOf("핸들 쏠림", "타이어 이상"), GuideLevel.SOON, "쏠림 + 타이어", "쏠리는 쪽 타이어의 공기압부터 확인하세요", listOf(
            GuideStep(StepMark.TIRE, "쏠리는 쪽 타이어를 보세요", "그쪽 공기압이 낮을 때가 많아요", key = "tire_compare"),
        ), listOf("한쪽 공기압 부족", "펑크")),
        Combo(setOf("차체 떨림", "핸들 쏠림"), GuideLevel.SOON, "떨림 + 쏠림", "타이어와 휠 정렬을 점검받으세요", listOf(
            GuideStep(StepMark.SPEED, "빠를수록 심해지는지 보세요", "그렇다면 휠 균형 · 정렬 점검", key = "when_shake"),
        ), listOf("휠 균형 · 정렬", "타이어 고르지 않은 마모")),
        Combo(setOf("이상한 소음", "타이어 이상"), GuideLevel.SOON, "소음 + 타이어", "바퀴 쪽 이물질과 손상을 확인하세요", listOf(
            GuideStep(StepMark.TIRE, "타이어에 박힌 것을 보세요", "못 · 돌이 박히면 소리가 나요", key = "tire_look"),
        ), listOf("박힌 못 · 돌", "타이어 손상")),
        Combo(setOf("이상한 소음", "차체 떨림"), GuideLevel.SOON, "소음 + 떨림", "소리와 떨림을 함께 기록해 정비소에 보여 주세요", listOf(
            GuideStep(StepMark.PHONE, "소리와 진동을 함께 기록하세요", "폰으로 소리 10초, 진동 10초", GuideAction.PHONE_NOISE, key = "phone_noise"),
        ), listOf("하체 부품", "브레이크", "엔진 받침")),
        Combo(setOf("힘이 없어요", "경고등이 켜졌어요"), GuideLevel.SOON, "힘 없음 + 경고등", "경고등의 뜻을 보고, 세운 뒤 시동을 다시 걸어 보세요", listOf(
            WARN.copy(title = "경고등부터 확인하세요", detail = "엔진 · 하이브리드 경고면 점검이 필요해요"),
        ), listOf("엔진 보호 모드", "하이브리드 시스템")),
        Combo(setOf("차체 떨림", "힘이 없어요"), GuideLevel.SOON, "떨림 + 힘 없음", "엔진 쪽 문제일 수 있어요. 경고등을 확인하세요", listOf(
            WARN.copy(title = "엔진 경고등을 보세요", detail = "엔진 경고등이 깜박이면 바로 세우세요"),
        ), listOf("점화 계통(불발)", "엔진 받침")),
        Combo(setOf("경고등이 켜졌어요", "타이어 이상"), GuideLevel.SOON, "경고등 + 타이어", "타이어 공기압 경고등일 수 있어요", listOf(
            WARN.copy(title = "경고등이 타이어 모양인지 보세요", detail = "타이어 모양이면 공기압 경고등이에요"),
        ), listOf("공기압 부족")),
    )

    /**
     * 고른 증상으로 안내를 만듭니다.
     * - '잘 모르겠음'은 다른 증상과 함께 고르면 뺍니다.
     * - 급한 정도는 증상과 맞은 조합 가운데 가장 급한 것입니다.
     * - 단계 순서: 바로 세워야 하는 증상의 단계 → 맞은 조합의 단계 → 나머지 증상의 단계를 증상마다 하나씩 돌아가며. 같은 [GuideStep.key]는 한 번만.
     */
    fun forChoices(choices: List<String>): Guide {
        val known = choices.filter { it in DEFS }.distinct()
        val core = (if (known.size > 1) known - UNSURE else known).ifEmpty { listOf(UNSURE) }
        val defs = core.associateWith { DEFS.getValue(it) }
        val combos = COMBOS.filter { core.containsAll(it.needs) }
        val level = (defs.values.map { it.level } + combos.map { it.level }).minBy { it.ordinal }

        val stopSym = core.firstOrNull { defs.getValue(it).level == GuideLevel.STOP }
        val stopCombo = combos.firstOrNull { it.level == GuideLevel.STOP }
        val headline = when {
            stopSym != null -> defs.getValue(stopSym).headline
            stopCombo != null -> stopCombo.headline
            combos.isNotEmpty() -> combos.first().headline
            else -> defs.getValue(core.first()).headline
        }

        val tag = { s: String -> SHORT[s] ?: s }
        val stopSteps = core.filter { defs.getValue(it).level == GuideLevel.STOP }.flatMap { s -> defs.getValue(s).steps.map { it.copy(from = listOf(tag(s))) } }
        val comboSteps = combos.flatMap { cb -> cb.steps.map { it.copy(from = cb.needs.sortedBy { n -> SYMPTOMS.indexOf(n) }.map(tag)) } }
        val others = core.filter { defs.getValue(it).level != GuideLevel.STOP }.sortedBy { defs.getValue(it).level.ordinal }
        val rest = others.map { s -> defs.getValue(s).steps.map { it.copy(from = listOf(tag(s))) } }
        val roundRobin = interleave(rest)

        val merged = LinkedHashMap<String, GuideStep>()
        for (st in stopSteps + comboSteps + roundRobin) {
            val had = merged[st.key]
            merged[st.key] = when {
                had == null -> st
                // 이름이 다른 경고등 단계(예: 엔진 경고등 · 공기압 경고등)가 합쳐지면 어느 한쪽 이름이 다른 증상까지 덮지 않게 일반 이름으로 바꿉니다.
                had.key == WARN.key && had.title != st.title -> had.copy(title = WARN.title, from = (had.from + st.from).distinct())
                else -> had.copy(from = (had.from + st.from).distinct())
            }
        }
        // 같은 기능(예: 경고등 찾기)은 한 번만 단추로 둡니다.
        val seenAction = mutableSetOf<GuideAction>()
        val steps = merged.values.map { st -> if (st.action != null && !seenAction.add(st.action)) st.copy(action = null) else st }.take(MAX_STEPS)

        // 원인: 맞은 조합(가장 구체적) → 바로 세워야 하는 증상 → 나머지 증상을 하나씩 돌아가며.
        val causes = (combos.flatMap { it.causes } + core.filter { defs.getValue(it).level == GuideLevel.STOP }.flatMap { defs.getValue(it).causes } +
            interleave(others.map { defs.getValue(it).causes })).distinct().take(4)
        val voice = ("$headline. " + (steps.firstOrNull()?.title ?: "") + ".").replace(" · ", ", ")
        return Guide(core, level, headline, combos.map { it.label }, steps, causes, voice)
    }

    /** 목록들에서 하나씩 돌아가며 꺼냅니다([[a1, a2], [b1]] → [a1, b1, a2]). */
    private fun <T> interleave(lists: List<List<T>>): List<T> = buildList {
        for (i in 0 until (lists.maxOfOrNull { it.size } ?: 0)) lists.forEach { l -> l.getOrNull(i)?.let(::add) }
    }
}
