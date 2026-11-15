package app.pleosguard.demo.logic

/*
 * 플레오스 슈퍼 케어의 관리 · 정비 · 구독 자료(2026-11-14 컨셉 전환).
 * 실제 서비스에서는 서버 · 공공 자료에서 받습니다. 데모는 아래 예시 자료와 PC의 모의 서버(demo-app/server)를 씁니다.
 * 예시 자료는 화면에 정직성 꼬리표(예시 · 가상 예시)를 붙입니다.
 */

/**
 * 리콜 한 건. [example]이면 "가상 예시 · 실제 리콜 아님" 꼬리표를 붙입니다.
 * 형식은 조사 보고서 5.5절(국토부 보도자료 항목 + NHTSA parkIt · parkOutSide · overTheAirUpdate 구조)을 따릅니다.
 * [level] = 위험 표시(운행 자제 · 실외 주차 · 일반), [fix] = 입고 수리 · 무선 업데이트, [produced] = 대상 생산 기간,
 * [ota] = 제조사가 차 화면에서 이미 안내하는 리콜(앱은 다시 알리지 않음).
 */
data class Recall(
    val id: String, val title: String, val part: String, val risk: String, val action: String,
    val period: String, val done: Boolean, val example: Boolean = true, val source: String = "가상 예시",
    val level: String = "일반", val fix: String = "입고 수리", val gen: String = "CN8", val produced: String = "", val ota: Boolean = false, val checked: String = "",
)

/** 소모품 · 정기 점검. 주기와 마지막 교체 거리는 예시 값입니다(아반떼 정비 주기 확인 전). */
data class Consumable(val name: String, val icon: String, val intervalKm: Int, val lastKm: Int) {
    fun leftKm(odo: Float) = (lastKm + intervalKm - odo).toInt()
    fun usedRatio(odo: Float) = ((odo - lastKm) / intervalKm).coerceIn(0f, 1.2f)
}

/** 구독 상품(연계 아이디어). [demo] = 데모에서 가입 · 해지를 모의로 해 볼 수 있음, 아니면 설계만. */
data class Plan(
    val id: String, val name: String, val icon: String, val price: String, val partner: String,
    val benefits: List<String>, val uses: List<String>, val demo: Boolean,
)

/** 정비소 상세(모의 서버 또는 예시). [partner] = 제휴 정비소("제휴" 표시, 순서에는 영향 없음). */
data class ShopInfo(
    val id: String, val name: String, val services: List<String>, val hours: String, val ev: Boolean,
    val priceNote: String, val certs: List<String>, val rating: Float, val reviewCount: Int, val example: Boolean = true,
    val partner: Boolean = false,
)

/**
 * 정비소 후기. dims = 사실 질문 답([CareData.REVIEW_FACTS], 1 = 예, 0 = 아니요, 없으면 답 안 함).
 * [verified] = 방문 확인 후기(조사 6.4절: 방문이 확인된 후기만 받음). mine = 이 차에서 쓴 후기.
 */
data class Review(
    val id: String, val shopId: String, val author: String, val rating: Int, val dims: Map<String, Int>,
    val tags: List<String>, val text: String, val wall: Long, val mine: Boolean = false, val example: Boolean = true,
    val verified: Boolean = true,
)

/** 내 정비 방문 기록. */
data class Visit(val id: String, val shopId: String, val shopName: String, val wall: Long, val work: String, val reviewed: Boolean)

/** 폰에서 온 증거(소리 · 진동 · 경고등 사진 · 사진). spectrum/series는 그래프용으로 줄인 값입니다. */
data class Evidence(
    val id: String, val kind: String, val wall: Long, val durationMs: Long, val placement: String?, val partHint: String?,
    val summary: String, val metrics: Map<String, Double> = emptyMap(), val spectrum: List<Float> = emptyList(),
    val series: List<Float> = emptyList(), val imagePath: String? = null, val audioPath: String? = null,
    val candidates: List<String> = emptyList(), val confirmed: String? = null, val note: String? = null,
)

object CareData {
    const val MODEL = "AVANTE"
    const val YEAR = 2026
    /** 대상 차: 디 올 뉴 아반떼(8세대, 세대 코드 CN8, 2026-06 공개). 조사 보고서 2건이 확인했습니다. */
    const val GEN = "CN8"
    /** 제작연월(자동차등록증 "형식 및 제작연월" 칸, 사용자 입력). 데모 값은 예시입니다. */
    const val PRODUCED = "2026-09"
    /** 출고일(사용자 입력, 보증 기한 계산). 데모 값은 예시입니다. */
    const val DELIVERED = "2026-09-20"
    /** 리콜 확인 주소(자동차리콜센터 첫 화면). 차대번호 · 등록번호를 주소에 넣지 않습니다(개인정보). */
    const val RECALL_CENTER = "https://www.car.go.kr/home/main.do"

    /** 리콜 예시 2건(가상, 실제 리콜 아님). 조사 5.5절의 "가상 예" 형식입니다. */
    val RECALLS = listOf(
        Recall("가상-001", "계기판 표시 소프트웨어 점검", "계기판", "계기판 표시가 늦게 켜질 수 있어요(가상)", "무선 업데이트 또는 정비소 업데이트(약 30분, 무상)",
            "2026-12-01 시정 시작(가상)", done = false, level = "일반", fix = "무선 업데이트", produced = "2026-08 ~ 2026-09 생산(가상)", ota = true, checked = "2026-11-14"),
        Recall("가상-002", "하이브리드 제어기 소프트웨어 점검", "하이브리드 제어기", "드물게 과열될 수 있어 실외 주차를 권해요(가상)", "정비소에서 제어기 소프트웨어 업데이트(약 40분, 무상)",
            "2026-11-20 시정 시작(가상)", done = false, level = "실외 주차", fix = "입고 수리", produced = "2026-08 ~ 2026-10 생산 · 하이브리드(가상)", checked = "2026-11-14"),
    )

    /** 아이콘 이름은 화면에서 [app.pleosguard.demo.ui.Gi]로 바꿉니다. */
    val CONSUMABLES = listOf(
        Consumable("엔진오일", "Oil", 10_000, 22_900),
        Consumable("에어컨 필터", "Fan", 15_000, 19_000),
        Consumable("와이퍼", "Wiper", 20_000, 15_500),
        Consumable("타이어 위치 교환", "Wheel", 10_000, 28_000),
        Consumable("브레이크 패드", "Brake", 40_000, 0),
        Consumable("배터리 점검", "Battery", 50_000, 0),
    )

    /**
     * 구독 연계 아이디어(조사 보고서 4.3 · 4.4절, 상위 3개가 앞). 가격 · 제휴사는 예시입니다.
     * 공통 원칙(4.5절): 안전 정보는 무료, 결제는 폰(차 화면은 QR만), 주행 중 제안 없음, 제휴 표시, 건별 동의, 해지는 가입만큼 쉽게.
     */
    var PLANS: List<Plan> = listOf(
        Plan("parts", "소모품 패키지", "Oil", "패키지마다 가격 공개(예시)", "제휴 정비 예약(예시)",
            listOf("다음 교체까지 남은 거리 알림", "가격 공개 패키지 3개 비교", "폰에서 QR로 예약 · 결제"), listOf("주행거리", "정비 기록"), demo = true),
        Plan("quote", "견적 · 예약 받기", "Receipt", "운전자 무료 · 정비소 건당 수수료(예시)", "1 · 2급 공업사 · 블루핸즈(예시)",
            listOf("증상 기록 · 재현 조건을 건별 동의로 보내기", "모의 견적 3곳 비교", "방문 → 후기 → 500km 재발 확인"), listOf("증상 기록", "주행거리"), demo = true),
        Plan("warranty", "보증 연장 관리", "ShieldCheck", "현대 워런티 플러스(예시)", "현대(판매처) · 블루핸즈",
            listOf("출고 90일 안 가입 15% 할인 기한", "출고 2년 · 4만km 안 가입 기한", "보증 끝나기 전 고칠 기록 목록"), listOf("주행거리", "증상 기록", "리콜"), demo = true),
        Plan("wash", "세차 구독", "Droplets", "월정액(예시)", "세차 구독 업체(예시)",
            listOf("마지막 세차 뒤 일수 · 거리", "가까운 지점을 경유지로"), listOf("주행거리", "위치"), demo = true),
        Plan("tire", "타이어 렌탈 · 교체 계획", "Wheel", "렌탈료 · 구매(예시)", "타이어 렌탈(예시)",
            listOf("교체 예정 시점(주행거리 기준)", "렌탈과 구매 비교 · 마모는 차가 재지 않음"), listOf("주행거리", "타이어 증상 기록"), demo = true),
        Plan("roadside", "고장 출동 모아 보기", "Lifebuoy", "대부분 이미 가입한 서비스", "보험 특약 · 현대 고객센터",
            listOf("쓸 수 있는 출동 서비스와 무상 기간", "전화할 때 읽을 정보 정리"), listOf("위치", "증상 기록"), demo = false),
    )

    val SHOPS = listOf(
        ShopInfo("shop-a", "정비소 A (예시)", listOf("엔진오일", "타이어", "브레이크", "전기 장치"), "평일 09:00~18:00 · 토 09:00~13:00", ev = true,
            "엔진오일 교체 공임 2만 원대(예시)", listOf("제조사 협력 정비(예시)", "전기차 정비 교육 이수(예시)"), 4.6f, 8, partner = true),
        ShopInfo("shop-b", "정비소 B (예시)", listOf("타이어", "휠 얼라인먼트", "에어컨"), "매일 08:30~20:00", ev = false,
            "얼라인먼트 5만 원대(예시)", listOf("타이어 전문(예시)"), 4.2f, 6),
        ShopInfo("shop-c", "정비소 C (예시)", listOf("판금 · 도장", "유리", "실내"), "평일 09:00~19:00", ev = true,
            "견적 후 진행(예시)", listOf("사고 수리 전문(예시)"), 3.9f, 3),
    )

    /** 서버가 없을 때 보이는 예시 후기(모의 서버의 씨앗 자료와 같은 성격). */
    fun localReviews(shopId: String, nowWall: Long): List<Review> {
        val day = 86_400_000L
        val base = listOf(
            Review("lr1-$shopId", shopId, "예시 사용자 1", 5, mapOf("견적대로 결제" to 1, "설명을 들음" to 1, "약속 시간에 끝남" to 1, "증상 해결" to 1), listOf("설명이 자세함", "기록을 보고 바로 찾음"),
                "앱 기록을 보여 줬더니 소리 원인을 빨리 찾았습니다.", nowWall - 3 * day),
            Review("lr2-$shopId", shopId, "예시 사용자 2", 4, mapOf("견적대로 결제" to 1, "설명을 들음" to 1, "약속 시간에 끝남" to 1, "증상 해결" to 1), listOf("빠름"),
                "예약 시간에 맞춰 바로 봐 줬습니다.", nowWall - 9 * day),
            Review("lr3-$shopId", shopId, "예시 사용자 3", 3, mapOf("견적대로 결제" to 0, "설명을 들음" to 1, "약속 시간에 끝남" to 0, "증상 해결" to 1), listOf("가격 확인 필요"),
                "수리는 잘 됐지만 부품값 설명이 짧았습니다.", nowWall - 20 * day),
        )
        return base
    }

    /** 후기 사실 질문 4개(조사 6.4절: 별점과 함께 사실을 묻고, 확인된 후기 5개 미만이면 별점을 숨김). */
    val REVIEW_FACTS = listOf("견적대로 결제", "설명을 들음", "약속 시간에 끝남", "증상 해결")
    @Deprecated("사실 질문으로 바뀜", ReplaceWith("REVIEW_FACTS"))
    val REVIEW_DIMS get() = REVIEW_FACTS
    /** 확인된 후기가 이보다 적으면 별점 숫자를 숨깁니다. */
    const val MIN_REVIEWS_FOR_STARS = 5
    val REVIEW_TAGS = listOf("설명이 자세함", "기록을 보고 바로 찾음", "빠름", "가격이 투명함", "재방문 의사", "가격 확인 필요")

    /** 증거 종류 → 기록 증상 이름(폰에서 온 기록이 새 기록이 될 때). */
    fun symptomOf(kind: String): String? = when (kind) { "noise" -> "소음"; "vibration" -> "떨림"; "warning_photo" -> "경고등"; else -> null }

    /** 증거 종류 이름(화면). */
    fun kindLabel(kind: String): String = when (kind) {
        "noise" -> "소리"; "vibration" -> "진동"; "warning_photo" -> "경고등 사진"; "photo" -> "사진"; "receipt" -> "영수증"; else -> kind
    }

    /** 증거 한 줄 요약(주요 값만). 값 이름은 폰 앱과 맞춘 이름입니다(super-care-pivot 5절). */
    fun summarize(kind: String, m: Map<String, Double>, candidates: List<String>): String {
        fun f(v: Double?, unit: String, digits: Int = 0) = v?.let { if (digits == 0) "${it.toInt()}$unit" else "%.${digits}f$unit".format(it) }
        return when (kind) {
            "noise" -> listOfNotNull("소리", f(m["peakDb"], "dBFS")?.let { "최대 $it" }, f(m["dominantHz"], "Hz")?.let { "주로 $it" }).joinToString(" · ")
            "vibration" -> listOfNotNull("진동", f(m["peakG"], "g", 2)?.let { "최대 $it" }, f(m["dominantHz"], "Hz")?.let { "주로 $it" }).joinToString(" · ")
            "warning_photo" -> "경고등 사진 · 후보 ${candidates.size}개"
            else -> kindLabel(kind)
        }
    }
}

/** 증거 저장(JSON). 기록 파일(records.json, version 2) 안에 들어갑니다. */
object EvidenceJson {
    fun toJson(e: Evidence): org.json.JSONObject = org.json.JSONObject().apply {
        put("id", e.id); put("kind", e.kind); put("wall", e.wall); put("durationMs", e.durationMs)
        put("placement", e.placement ?: org.json.JSONObject.NULL); put("partHint", e.partHint ?: org.json.JSONObject.NULL)
        put("summary", e.summary)
        put("metrics", org.json.JSONObject().apply { e.metrics.forEach { (k, v) -> put(k, v) } })
        put("spectrum", org.json.JSONArray().apply { e.spectrum.forEach { put(it.toDouble()) } })
        put("series", org.json.JSONArray().apply { e.series.forEach { put(it.toDouble()) } })
        put("imagePath", e.imagePath ?: org.json.JSONObject.NULL); put("audioPath", e.audioPath ?: org.json.JSONObject.NULL)
        put("candidates", org.json.JSONArray(e.candidates)); put("confirmed", e.confirmed ?: org.json.JSONObject.NULL); put("note", e.note ?: org.json.JSONObject.NULL)
    }

    private fun org.json.JSONObject.str(k: String): String? = if (!has(k) || isNull(k)) null else optString(k).ifBlank { null }

    fun floats(a: org.json.JSONArray?, max: Int): List<Float> {
        if (a == null || a.length() == 0) return emptyList()
        val n = a.length()
        if (n <= max) return (0 until n).map { a.optDouble(it, 0.0).toFloat() }
        // 길면 구간 최댓값으로 줄입니다(그래프용).
        return (0 until max).map { b -> val from = b * n / max; val to = maxOf(from + 1, (b + 1) * n / max); (from until to).maxOf { a.optDouble(it, 0.0) }.toFloat() }
    }

    fun metrics(o: org.json.JSONObject?): Map<String, Double> {
        if (o == null) return emptyMap()
        val out = linkedMapOf<String, Double>()
        o.keys().forEach { k -> val v = o.opt(k); if (v is Number) out[k] = v.toDouble() }
        return out
    }

    fun strings(a: org.json.JSONArray?): List<String> =
        if (a == null) emptyList() else (0 until a.length()).mapNotNull { i ->
            when (val v = a.opt(i)) { is String -> v; is org.json.JSONObject -> v.optString("id").ifBlank { v.optString("name") }.ifBlank { null }; else -> null }
        }

    fun from(o: org.json.JSONObject): Evidence {
        val kind = o.optString("kind", "photo")
        val m = metrics(o.optJSONObject("metrics"))
        val cands = strings(o.optJSONArray("candidates"))
        return Evidence(
            id = o.getString("id"), kind = kind, wall = o.optLong("wall", o.optLong("startedAt", 0L)), durationMs = o.optLong("durationMs", 0L),
            placement = o.str("placement"), partHint = o.str("partHint"), summary = o.str("summary") ?: CareData.summarize(kind, m, cands),
            metrics = m, spectrum = floats(o.optJSONArray("spectrum"), 64), series = floats(o.optJSONArray("series"), 200),
            imagePath = o.str("imagePath"), audioPath = o.str("audioPath"), candidates = cands, confirmed = o.str("confirmed"), note = o.str("note"),
        )
    }
}
