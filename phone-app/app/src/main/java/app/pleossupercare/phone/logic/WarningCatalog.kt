package app.pleossupercare.phone.logic

// 차 앱 demo-app/app/src/main/java/app/pleosguard/demo/logic/WarningCatalog.kt를 그대로 옮긴 사본입니다(2026-11-14).
// 이름 · id는 화면 글자이면서 차 앱과 주고받는 열쇠이므로 차 앱 도감이 바뀌면 같이 고칩니다.

/**
 * 경고등 시연 도감. 색 → 모양 → 켜짐 찾기, 문구로 찾기, 말로 찾기, 정차 후 자세히가 모두 이 데이터를 씁니다.
 * - 모양 8개마다 계속 켜짐과 깜박임을 모두 다룹니다. 색 4개 × 모양 8개 × 켜짐 2가지(64개 조합) 모두 결과가 나옵니다([WarningCatalog.find]).
 * - 내용은 여러 차에 공통인 일반적인 뜻만 씁니다. 대상 차량(디 올 뉴 아반떼) 취급설명서와 대조하기 전이므로 화면에 "안내 예시 · 검토 전"을 붙입니다.
 *   제조사 고유 절차를 지어내지 않고, 차종마다 다른 부분은 "차종마다 다름"이라고 씁니다.
 * - 실제 경고등 상태는 PRIVATE_CAR_SAFETY(signature) 권한이 있어야 읽을 수 있어 앱이 읽지 못합니다. 그래서 운전자가 고릅니다.
 */
enum class WarnLevel(val label: String) {
    /** 빨강: 안전한 곳에 바로 세웁니다. */
    STOP("바로 세우기"),
    /** 빨강: 바로 조치하거나 가능한 한 빨리 점검합니다. */
    URGENT("바로 조치"),
    /** 주황: 가까운 정비소에서 점검합니다. */
    SERVICE("점검"),
    /** 초록·파랑: 기능이나 상태를 알리는 표시입니다. */
    INFO("상태 표시"),
}

data class WarnEntry(
    val id: String,
    val name: String,
    /** [WarningCatalog.SHAPES]의 하나. */
    val shape: String,
    /** 이 경고가 보통 켜지는 색. [WarningCatalog.COLORS]의 하나. */
    val color: String,
    /** true = 깜박일 때, false = 계속 켜질 때, null = 켜짐 방식과 상관없이 같은 경고. */
    val blink: Boolean?,
    val modeLabel: String,
    val level: WarnLevel,
    /** 운행 판단 한 줄(주행 중에는 이 줄과 음성만 냅니다). */
    val judgement: String,
    val meaning: String,
    val now: String,
    val next: String,
    /** 문구로 찾기·말로 찾기에 쓰는 낱말(띄어쓰기 없이 비교). */
    val keys: List<String>,
)

/**
 * 고른 색·모양·켜짐에 맞춘 결과. [primary]는 가장 가까운 후보, [others]는 같은 모양의 다른 후보입니다.
 * [colorNote]·[modeNote]는 고른 조건과 후보가 다를 때 붙이는 안내입니다.
 */
data class WarnResult(val primary: WarnEntry, val others: List<WarnEntry>, val colorNote: String?, val modeNote: String?) {
    val exact: Boolean get() = colorNote == null && modeNote == null
    val all: List<WarnEntry> get() = listOf(primary) + others
}

object WarningCatalog {
    const val STEADY = "계속 켜져 있어요"
    const val BLINK = "깜박여요"
    val COLORS = listOf("빨강", "주황", "초록", "파랑")
    val SHAPES = listOf("엔진 점검", "오일 압력", "브레이크", "배터리", "타이어 공기압", "안전벨트", "냉각수 온도", "에어백")
    val MODES = listOf(STEADY, BLINK)

    val ENTRIES: List<WarnEntry> = listOf(
        WarnEntry("engine", "엔진 점검 경고", "엔진 점검", "주황", false, "계속 켜짐", WarnLevel.SERVICE,
            "가까운 정비소에서 점검을 받으세요",
            "엔진이나 배출가스 관련 장치에 이상이 있다는 뜻일 수 있습니다.",
            "급가속을 피하고 차가 평소와 다른지 살펴보세요.",
            "켜져 있는 동안의 증상을 기록해 두고 정비소에서 점검을 받으세요.",
            listOf("엔진", "엔진점검", "체크")),
        WarnEntry("engine-blink", "엔진 점검 경고(깜박임)", "엔진 점검", "주황", true, "깜박임", WarnLevel.URGENT,
            "속도를 줄이고 바로 점검을 받으세요",
            "엔진에 더 급한 이상이 있다는 뜻일 수 있습니다(차종마다 다름).",
            "속도를 줄이고 급가속을 하지 마세요. 차가 떨리거나 힘이 없으면 안전한 곳에 세우세요.",
            "가까운 정비소에 바로 연락하세요.",
            listOf("엔진", "엔진점검", "체크")),
        WarnEntry("oil-pressure", "엔진오일 압력 경고", "오일 압력", "빨강", null, "켜짐", WarnLevel.STOP,
            "안전한 곳에 바로 세우세요",
            "엔진오일 압력이 낮다는 뜻일 수 있습니다. 이대로 달리면 엔진이 크게 상할 수 있습니다.",
            "안전한 곳에 세우고 시동을 끄세요.",
            "엔진오일 양을 확인하세요. 경고가 꺼지지 않으면 운행하지 말고 정비소에 연락하세요.",
            listOf("오일", "기름", "엔진오일", "오일압력")),
        WarnEntry("oil-level", "엔진오일 부족", "오일 압력", "주황", null, "켜짐", WarnLevel.SERVICE,
            "엔진오일을 확인하세요",
            "엔진오일 양이 적다는 뜻일 수 있습니다(차종마다 다름).",
            "안전한 곳에 세운 뒤 엔진오일 양을 확인하세요.",
            "모자라면 보충하고, 자주 모자라면 정비소에서 점검을 받으세요.",
            listOf("오일", "기름", "엔진오일", "오일부족")),
        WarnEntry("brake", "브레이크 경고", "브레이크", "빨강", false, "계속 켜짐", WarnLevel.STOP,
            "안전한 곳에 세우고 확인하세요",
            "주차 브레이크가 걸려 있거나 브레이크액이 부족하다는 뜻일 수 있습니다.",
            "주차 브레이크가 풀렸는지 보세요. 풀었는데도 켜져 있으면 안전한 곳에 세우세요.",
            "브레이크액을 확인하고 정비소에 연락하세요. 브레이크가 평소와 다르면 운행하지 마세요.",
            listOf("브레이크", "제동", "주차브레이크", "사이드")),
        WarnEntry("brake-blink", "주차 브레이크 작동 이상", "브레이크", "빨강", true, "깜박임", WarnLevel.URGENT,
            "안전한 곳에 세우고 점검을 받으세요",
            "전자식 주차 브레이크가 제대로 걸리거나 풀리지 않았다는 뜻일 수 있습니다(차종마다 다름).",
            "평평하고 안전한 곳에 세우세요. 비탈길에 세워 두지 마세요.",
            "계속 깜박이면 정비소에 연락하세요.",
            listOf("브레이크", "주차브레이크", "사이드")),
        WarnEntry("brake-assist", "제동 보조 장치 점검(ABS 등)", "브레이크", "주황", null, "켜짐", WarnLevel.SERVICE,
            "가까운 정비소에서 점검을 받으세요",
            "ABS나 전자식 주차 브레이크 같은 제동 보조 장치에 이상이 있다는 뜻일 수 있습니다.",
            "앞차와 거리를 평소보다 더 두고 급제동을 피하세요.",
            "가까운 정비소에서 점검을 받으세요.",
            listOf("브레이크", "abs", "제동")),
        WarnEntry("charging", "충전 경고", "배터리", "빨강", null, "켜짐", WarnLevel.STOP,
            "안전한 곳에 세우세요",
            "배터리가 충전되지 않는다는 뜻일 수 있습니다. 배터리가 다 닳으면 차가 멈출 수 있습니다.",
            "에어컨이나 열선처럼 전기를 많이 쓰는 장치를 끄고 안전한 곳에 세우세요.",
            "정비소에 연락해 충전 장치 점검을 받으세요.",
            listOf("배터리", "충전", "발전")),
        WarnEntry("tpms-low", "타이어 공기압 낮음", "타이어 공기압", "주황", false, "계속 켜짐", WarnLevel.SERVICE,
            "가까운 정비소에 들르세요",
            "타이어 공기압이 많이 낮을 때 켜집니다.",
            "안전한 곳에 세운 뒤 타이어 상태를 확인하세요.",
            "공기압을 맞춘 뒤에도 켜져 있으면 점검을 받으세요.",
            listOf("타이어", "공기압", "바람")),
        WarnEntry("tpms-fault", "공기압 장치 점검", "타이어 공기압", "주황", true, "깜박인 뒤 켜짐", WarnLevel.SERVICE,
            "정비소에서 점검을 받으세요",
            "공기압 경고 장치에 이상이 있을 때 깜박인 뒤 켜집니다.",
            "가까운 정비소에서 점검을 받으세요.",
            "점검 전까지는 타이어 공기압을 직접 확인하세요.",
            listOf("타이어", "공기압", "공기압장치", "깜박")),
        WarnEntry("seatbelt", "안전벨트 미착용 경고", "안전벨트", "빨강", false, "계속 켜짐", WarnLevel.URGENT,
            "안전벨트를 매세요",
            "안전벨트를 매지 않은 좌석이 있다는 뜻입니다.",
            "운전자는 출발 전에 매고, 동승자에게도 매 달라고 말하세요.",
            "모두 맨 뒤에도 켜져 있으면 좌석 위의 짐이나 버클을 확인하세요.",
            listOf("벨트", "안전벨트")),
        WarnEntry("seatbelt-blink", "안전벨트 미착용 경고(주행 중)", "안전벨트", "빨강", true, "깜박임 · 경고음", WarnLevel.URGENT,
            "안전벨트를 매세요",
            "주행 중 안전벨트를 매지 않은 좌석이 있으면 깜박이며 경고음이 날 수 있습니다.",
            "동승자에게 안전벨트를 매 달라고 말하세요. 운전자는 안전한 곳에 세운 뒤 매세요.",
            "모두 맨 뒤에도 계속되면 좌석 위의 짐이나 버클을 확인하세요.",
            listOf("벨트", "안전벨트", "경고음")),
        WarnEntry("coolant-hot", "냉각수 온도 높음(과열)", "냉각수 온도", "빨강", null, "켜짐", WarnLevel.STOP,
            "안전한 곳에 바로 세우세요",
            "엔진이 지나치게 뜨거워졌다는 뜻일 수 있습니다.",
            "안전한 곳에 세우고 엔진을 식히세요.",
            "엔진이 뜨거울 때 냉각수 마개를 열지 마세요. 식은 뒤에도 켜지면 정비소에 연락하세요.",
            listOf("냉각수", "온도", "과열", "뜨거")),
        WarnEntry("coolant-cold", "냉각수 온도 낮음(엔진이 차가움)", "냉각수 온도", "파랑", null, "켜짐", WarnLevel.INFO,
            "상태 표시입니다. 운행할 수 있습니다",
            "엔진이 아직 차갑다는 상태 표시일 수 있습니다(차종마다 다름).",
            "따뜻해질 때까지 급가속을 피하세요.",
            "달리면 저절로 꺼집니다. 오래 켜져 있으면 점검을 받으세요.",
            listOf("냉각수", "온도", "차가")),
        WarnEntry("airbag", "에어백 경고", "에어백", "빨강", null, "켜짐", WarnLevel.URGENT,
            "가능한 한 빨리 점검을 받으세요",
            "에어백 장치에 이상이 있다는 뜻일 수 있습니다. 사고 때 에어백이 제대로 펴지지 않을 수 있습니다.",
            "조심해서 운전하세요.",
            "가까운 정비소에서 에어백 장치 점검을 받으세요.",
            listOf("에어백")),
    )

    fun byId(id: String?): WarnEntry? = ENTRIES.firstOrNull { it.id == id }

    /** 이 모양의 경고가 보통 켜지는 색들(도감 순서). */
    fun usualColors(shape: String): List<String> = ENTRIES.filter { it.shape == shape }.map { it.color }.distinct()

    /** 이 경고를 고를 때의 켜짐 값. */
    fun modeOf(e: WarnEntry): String = if (e.blink == true) BLINK else STEADY

    /**
     * 색·모양·켜짐으로 후보를 고릅니다. 순서: 색이 같은 후보 → 켜짐이 맞는 후보 → 더 급한 후보.
     * 모양마다 후보가 하나 이상 있으므로 64개 조합 모두 결과가 나옵니다. 고른 색이 이 모양의 보통 색과 다르면
     * "찾지 못했습니다" 대신 가장 가까운 후보와 색을 다시 확인하라는 안내를 냅니다.
     */
    fun find(color: String, shape: String, mode: String): WarnResult {
        val blink = mode == BLINK
        val same = ENTRIES.filter { it.shape == shape }
        require(same.isNotEmpty()) { "도감에 없는 모양: $shape" }
        fun modeOk(e: WarnEntry) = e.blink == null || e.blink == blink
        val ranked = same.sortedWith(compareBy<WarnEntry>({ if (it.color == color) 0 else 1 }, { if (modeOk(it)) 0 else 1 }, { it.level.ordinal }))
        val primary = ranked.first()
        val colorNote = if (primary.color == color) null else buildString {
            if (color == "초록" || color == "파랑") append("초록·파랑 표시등은 보통 기능이 켜져 있다는 상태 표시입니다. ")
            append("이 모양은 보통 ${usualColors(shape).joinToString("·")}입니다. 색을 다시 확인하세요")
        }
        val modeNote = when {
            !modeOk(primary) -> "고른 켜짐 방식과 다릅니다. 켜져 있는지, 깜박이는지 다시 확인하세요"
            primary.blink == null && blink -> "깜박일 때의 뜻은 차종마다 다를 수 있습니다. 차량 설명서를 확인하세요"
            else -> null
        }
        return WarnResult(primary, ranked.drop(1).take(1), colorNote, modeNote)
    }

    /** 문구로 찾기: 이름·모양·낱말에 검색어가 들어 있거나, 검색어에 낱말이 들어 있는 항목. 빈 검색어는 전체. */
    fun search(query: String): List<WarnEntry> {
        val q = query.replace(" ", "").lowercase()
        if (q.isEmpty()) return ENTRIES
        return ENTRIES.filter { e ->
            e.name.replace(" ", "").lowercase().contains(q) || e.shape.replace(" ", "").contains(q) || e.keys.any { k -> q.contains(k) || k.contains(q) }
        }
    }

    /** 말로 찾기: 말에서 색·모양·켜짐 낱말을 고릅니다. 모양을 못 알아들으면 null입니다. */
    fun parseSpoken(text: String): Triple<String?, String?, String> {
        val t = text.replace(" ", "")
        val color = listOf("빨강" to listOf("빨간", "빨강", "붉은"), "주황" to listOf("주황", "노란", "노랑", "오렌지"), "초록" to listOf("초록", "녹색"), "파랑" to listOf("파란", "파랑"))
            .firstOrNull { (_, ks) -> ks.any { it in t } }?.first
        // "엔진오일"이 엔진 점검으로 잡히지 않도록 오일을 엔진보다 먼저 봅니다.
        val shape = listOf("타이어 공기압" to listOf("타이어", "공기압"), "오일 압력" to listOf("오일", "기름"), "엔진 점검" to listOf("엔진"), "브레이크" to listOf("브레이크", "사이드"),
            "배터리" to listOf("배터리", "충전"), "안전벨트" to listOf("벨트"), "냉각수 온도" to listOf("냉각수", "온도", "과열"), "에어백" to listOf("에어백"))
            .firstOrNull { (_, ks) -> ks.any { it in t } }?.first
        val mode = if ("깜박" in t || "깜빡" in t) BLINK else STEADY
        return Triple(color, shape, mode)
    }
}
