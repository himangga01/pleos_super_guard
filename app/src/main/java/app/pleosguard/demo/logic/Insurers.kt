package app.pleosguard.demo.logic

/**
 * 자동차 보험사의 사고 접수 · 긴급출동 대표번호(2026-11-14 사용자 요청: 사고 뒤 내 보험사 접수 번호 안내, 모르면 보험사별 목록).
 *
 * - 대상: 금융감독원 "2026년 상반기 자동차보험 사업실적"(2026-09-15)의 자동차보험 판매 보험사 11곳 가운데 신규 계약이 되는 10곳과
 *   한화손보에 합병된 캐롯의 전용 번호(2025-10 합병, 캐롯 상품 가입자용).
 * - 번호: 각 보험사 공식 누리집, 금융감독원 · 보험개발원 · 손해보험협회 공동 보도자료(2026-02-09) 붙임 연락처,
 *   손해보험협회 회원사 페이지가 모두 같았습니다(2026-11-14 확인, 실제로 걸어 보지는 않음).
 * - 뺀 곳: 예별손해보험(구 MG손해보험)은 2025-05부터 신규 계약이 막혔고 ARS에서 자동차 사고 접수 메뉴가 빠져 목록에서 뺐습니다.
 *   NH농협손해보험은 자동차보험을 팔지 않습니다.
 * - [hint]는 누리집에 적힌 ARS 안내입니다. 바뀔 수 있어 확인 날짜와 함께 보입니다.
 * 출처 URL은 docs/analysis/accident-guide-2026-11-14.md에 있습니다. 앱은 전화를 실제로 걸지 않습니다(모의 연결).
 */
data class Insurer(val id: String, val name: String, val phone: String, val hint: String)

object Insurers {
    /** 번호를 확인한 날(Asia/Seoul). */
    const val CHECKED = "2026-11-14"

    val ALL: List<Insurer> = listOf(
        Insurer("samsung", "삼성화재", "1588-5114", "연결 후 1번 · 24시간"),
        Insurer("hyundai", "현대해상", "1588-5656", "ARS 2번 사고접수 · 24시간"),
        Insurer("db", "DB손해보험", "1588-0100", "사고접수 · 현장출동 · 24시간"),
        Insurer("kb", "KB손해보험", "1544-0114", "대표 콜센터 · 24시간"),
        Insurer("meritz", "메리츠화재", "1566-7711", "ARS 1번 사고접수 · 24시간"),
        Insurer("hanwha", "한화손해보험", "1566-8000", "대표전화 · 24시간"),
        Insurer("lotte", "롯데손해보험", "1588-3344", "사고접수 상황실 · 24시간"),
        Insurer("heungkuk", "흥국화재", "1688-1688", "고객센터 · 24시간"),
        Insurer("axa", "AXA손해보험", "1566-1566", "ARS 3번 사고접수 · 24시간"),
        Insurer("hana", "하나손해보험", "1566-3000", "대표상담 · 24시간"),
        Insurer("carrot", "한화손보 캐롯", "1566-0300", "구 캐롯 가입자 · ARS 1번"),
    )

    fun byId(id: String?): Insurer? = ALL.firstOrNull { it.id == id }

    /** 공공 번호. */
    const val POLICE = "112"
    const val FIRE = "119"
}
