package app.pleosguard.demo

import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.pleosguard.demo.logic.Cabin
import app.pleosguard.demo.logic.Pattern
import app.pleosguard.demo.logic.Record
import app.pleosguard.demo.logic.RecordBook
import app.pleosguard.demo.logic.ServiceNote
import app.pleosguard.demo.logic.SnapPoint
import app.pleosguard.demo.sdk.Location
import app.pleosguard.demo.sdk.Navi
import app.pleosguard.demo.sdk.Shop
import app.pleosguard.demo.logic.Quality
import app.pleosguard.demo.logic.RulesLab
import app.pleosguard.demo.logic.VoiceRoute
import app.pleosguard.demo.logic.VoiceRouter
import app.pleosguard.demo.logic.WarnResult
import app.pleosguard.demo.logic.WarningCatalog
import app.pleosguard.demo.sdk.VehicleSignals
import app.pleosguard.demo.sdk.Voice
import app.pleosguard.demo.sdk.CareLink
import app.pleosguard.demo.logic.CareData
import app.pleosguard.demo.logic.CareSnapshot
import app.pleosguard.demo.logic.CareStore
import app.pleosguard.demo.logic.Consumable
import app.pleosguard.demo.logic.Evidence
import app.pleosguard.demo.logic.LeftItemCare
import app.pleosguard.demo.logic.Recall
import app.pleosguard.demo.logic.Review
import app.pleosguard.demo.logic.ShopInfo
import app.pleosguard.demo.logic.Visit
import app.pleosguard.demo.logic.Guide
import app.pleosguard.demo.logic.GuideAction
import app.pleosguard.demo.logic.GuideLevel
import app.pleosguard.demo.logic.RecordStatus
import app.pleosguard.demo.logic.SymptomGuide
import app.pleosguard.demo.logic.BodyAnswer
import app.pleosguard.demo.logic.BodyState
import app.pleosguard.demo.logic.Insurer
import app.pleosguard.demo.logic.Insurers
import app.pleosguard.demo.logic.YesNo
import java.time.Instant
import kotlin.math.roundToInt
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 화면(2026-11-14 케어 전환). MANAGE = 차량 관리(점검 신호 · 소모품 · 리콜 요약 · 경고등 · 짐 알림 · 안심 케어),
 * RECALL = 리콜 안내, SUBSCRIBE = 구독 연계, PHONE = 폰 연결, REVIEW = 정비소 후기 쓰기. GUARDIAN = 안심 케어(보호자 알림).
 */
enum class Screen { HOME, WARN_FIND, WARN_RESULT, WARN_DETAIL, WARN_SEARCH, RECORD, RECORD_LIST, PATTERN, MANAGE, RECALL, SUBSCRIBE, PHONE, REVIEW, GUARDIAN, SERVICE, SITUATION, SITUATION_PARKED, GUIDE, RECIPIENTS, COUNTDOWN, ACCIDENT, SDK_CHECK }

/** 사고 대처 안내 단계: 사고 확인 → 보험 접수 질문(2차 사고 주의) → 연결(모의) → 접수 뒤 안내(몸 상태 · 2차 사고 · 현장 촬영 · 차 옮기기). */
enum class AccStep(val label: String) { CONFIRM("사고 확인"), INSURER("보험 접수"), CALLING("연결"), AFTER("안전 안내") }

/** 주행 상태를 어디서 얻는지. 실제(CarUxRestrictions)가 기본이고, 시연 모드에서 시험 입력으로 바꿀 수 있습니다. */
enum class DriveSource { REAL, TEST_DRIVING, TEST_PARKED }

/** 시간표 한 칸(100ms마다). 값이 없음(null)은 그대로 둡니다. */
data class Sample(val t: Long, val speed: Float?, val speedQ: Quality, val brake: Boolean?, val brakeTest: Boolean, val steering: Float?, val turn: TurnSignalValue?)

/** 이전 이름과 맞추려는 별칭: 기록 하나(logic/RecordBook.kt). */
typealias RecordSnap = app.pleosguard.demo.logic.Record

enum class IncidentKind(val label: String) { AIRBAG("에어백 전개 신호(운전석)"), ACCIDENT("사고가 났어요(운전자 선택)"), HELP("도움 요청(운전자 선택)"), DECEL("감속 실패 대처 중 도움 요청") }

data class Incident(val kind: IncidentKind, val detectedWall: Long, val testInput: Boolean, val countdownFrom: Long?, var sentWall: Long? = null, var cancelled: Boolean = false,
                    val snap: IncidentSnap = IncidentSnap())

/**
 * 사건 순간의 차 상태와 위치(PRD N4). Vehicle SDK·FusedLocation·NaviHelper에서 그때 받은 값을 그대로 담고, 없으면 null로 둡니다.
 * [locWall]은 위치 측정 시각(벽시계로 보이는 timestampMillis) 또는 앱이 받은 시각에 표시용 시계 차이를 더한 값입니다.
 */
data class IncidentSnap(
    val seats: List<Boolean?> = List(4) { null }, val belts: List<Boolean?> = List(4) { null }, val doors: List<Int?> = List(4) { null },
    val locWall: Long? = null, val locMeasured: Boolean = false, val lat: Double? = null, val lng: Double? = null, val confidence: Float? = null, val address: String? = null,
    /** 주소를 알려 준 내비의 현재 위치. 주소와 좌표의 출처가 달라 둘이 맞는지 비교할 때 씁니다. */
    val naviLat: Double? = null, val naviLng: Double? = null,
)

/** 사건 순간 차 상태를 한 줄 글로 바꿉니다. 값이 없으면 "확인 불가"로 쓰고, 없음을 닫힘·미착용으로 바꾸지 않습니다. */
object SnapText {
    private val NAMES = listOf("운전석", "조수석", "뒷좌석 왼쪽", "뒷좌석 오른쪽")
    private val SHORT = listOf("운전석", "조수석", "뒤 왼쪽", "뒤 오른쪽")
    private fun seatsOn(s: IncidentSnap) = s.seats.indices.filter { s.seats[it] == true }
    fun occupants(s: IncidentSnap): String {
        if (s.seats.all { it == null }) return "확인 불가"
        val on = seatsOn(s)
        return when {
            on.isEmpty() -> "탑승 감지 없음"
            on.size == 1 -> "${NAMES[on[0]]} 1명"
            on.size == s.seats.size -> "${on.size}명(모든 좌석)"
            else -> "${on.size}명(" + on.joinToString("·") { SHORT[it] } + ")"
        }
    }
    /** 탑승한 좌석의 안전벨트. 탑승을 모르면 운전석을 봅니다. */
    fun belts(s: IncidentSnap): String {
        val idx = seatsOn(s).ifEmpty { listOf(0) }
        val v = idx.map { s.belts.getOrNull(it) }
        if (v.all { it == null }) return "확인 불가"
        if (idx.size == 1) return NAMES[idx[0]] + " " + when (v[0]) { true -> "착용"; false -> "미착용"; null -> "확인 불가" }
        if (v.all { it == true }) return "모두 착용"
        if (v.all { it == false }) return "모두 미착용"
        val off = idx.filter { s.belts.getOrNull(it) == false }.map { SHORT[it] }
        val unknown = idx.filter { s.belts.getOrNull(it) == null }.map { SHORT[it] }
        return listOfNotNull(off.takeIf { it.isNotEmpty() }?.let { it.joinToString("·") + " 미착용" },
            unknown.takeIf { it.isNotEmpty() }?.let { it.joinToString("·") + " 확인 불가" }).joinToString(", ") + " · 나머지 착용"
    }
    fun doors(s: IncidentSnap): String {
        if (s.doors.all { it == null }) return "확인 불가"
        val open = s.doors.indices.filter { (s.doors[it] ?: 0) > 0 }
        return when { open.isNotEmpty() -> open.joinToString("·") { SHORT[it] } + " 열림"; s.doors.any { it == null } -> "닫힘(일부 확인 불가)"; else -> "모두 닫힘" }
    }
    fun car(s: IncidentSnap): String = "탑승 ${occupants(s)} · 안전벨트 ${belts(s)} · 문 ${doors(s)}"

    /** 탑승한 좌석 가운데 안전벨트를 안 맨 좌석이 있으면 true, 모두 맸으면 false, 알 수 없으면 null. */
    fun anyUnbuckled(s: IncidentSnap): Boolean? {
        val idx = s.seats.indices.filter { s.seats[it] == true }.ifEmpty { listOf(0) }
        val v = idx.map { s.belts.getOrNull(it) }
        return when { v.any { it == false } -> true; v.all { it == true } -> false; else -> null }
    }

    /**
     * 주소 출처(내비 현재 위치)와 좌표 출처(FusedLocation) 사이 거리(km). 둘 중 하나라도 없으면 null.
     * 에뮬레이터에서 둘이 9,000km 넘게 어긋난 일이 있어(2026-11-13) 화면에 알립니다.
     */
    fun sourceGapKm(s: IncidentSnap): Double? {
        val la1 = s.lat ?: return null; val lo1 = s.lng ?: return null; val la2 = s.naviLat ?: return null; val lo2 = s.naviLng ?: return null
        val r = 6371.0
        val dLa = Math.toRadians(la2 - la1); val dLo = Math.toRadians(lo2 - lo1)
        val a = Math.sin(dLa / 2).let { it * it } + Math.cos(Math.toRadians(la1)) * Math.cos(Math.toRadians(la2)) * Math.sin(dLo / 2).let { it * it }
        return 2 * r * Math.asin(Math.min(1.0, Math.sqrt(a)))
    }
    /** 위치 시각 한 줄. 사건보다 60초 넘게 앞선 위치는 오래된 위치라고 붙입니다(PRD 위치 나이 60초). */
    fun locTime(s: IncidentSnap, detectedWall: Long): String {
        val t = s.locWall ?: return "위치 확인 불가"
        val age = detectedWall - t
        return (if (s.locMeasured) "측정 시각 " else "받은 시각 ") + DemoController.hms(t) + if (age > 60_000) " · ${age / 60_000}분 전 위치(오래됨)" else ""
    }
}

/**
 * 데모 흐름의 상태와 판정. 실제 SDK 값(브레이크·조향·방향지시등·차속·주행 제한)과 시연 모드 시험 입력을 나눠 들고,
 * 시험 입력이 하나라도 섞인 사건은 모의 발송만 합니다. 서버·알림톡·문자는 이 앱에서 보내지 않습니다(모의).
 */
class DemoController(val signals: VehicleSignals?, private val voice: Voice?, private val now: () -> Long = { SystemClock.elapsedRealtime() }, private val wallClock: () -> Long = { System.currentTimeMillis() },
                     val navi: Navi? = null, val location: Location? = null) {
    /**
     * 시연 모드 "대표 사례 시각". 문서의 대표 사례(21:00:03 에어백 신호, 21:00:13 발송 등)와 같은 시각을 화면에 보이려고
     * 표시용 시계를 옮깁니다. 0이면 실제 시각입니다. 화면 캡처를 문서 그림으로 쓸 때만 켭니다.
     */
    var clockOffsetMs by mutableStateOf(0L)
    private fun wallNow(): Long = wallClock() + clockOffsetMs
    fun wallNowPublic(): Long = wallNow()

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
    val book = RecordBook()
    /** 기록 목록이 바뀔 때마다 늘어납니다(목록·재현 조건 화면 다시 그리기). */
    var bookVersion by mutableIntStateOf(0)
        private set
    /** 열어 둔 기록(지금 기록 화면). */
    var lastRecord by mutableStateOf<RecordSnap?>(null)
    var selectedPart by mutableStateOf<String?>(null)
    /** 저장소(JSON). 앱에서는 파일, 시험에서는 없음. */
    var store: ((String) -> Unit)? = null
    /** 정차 후 상황 선택(최대 3개). */
    var parkedChoices by mutableStateOf<List<String>>(emptyList())
    /** 경고등 문구로 찾기 검색어. */
    var warnQuery by mutableStateOf("")
    /** 진입 화면(앱을 처음 열 때 잠깐). 긴급 안내 중이면 건너뜁니다. */
    var intro by mutableStateOf(false)
    /** 정비소 후보(앱이 정한 예시 정비소). 거리·시간은 NaviHelper requestTrip 결과. */
    var shops by mutableStateOf<List<Shop>>(emptyList())
    var selectedShop by mutableIntStateOf(0)
    /** 정비소에 보여 주기(모의 공유 링크 QR). */
    var shareQr by mutableStateOf<String?>(null)
    /** 폰으로 넘기는 QR(리콜센터 확인 · 구독 가입 결제 등). 닫으면 [QrSpec.onClose]를 부릅니다. */
    data class QrSpec(val link: String, val title: String, val sub: String, val badge: String, val onClose: (() -> Unit)? = null)
    var qrSpec by mutableStateOf<QrSpec?>(null)

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
        closeParkedOnlyScreens()
        history.addLast(Sample(t, v, q, b, bTest, signals?.steeringDeg, signals?.turn))
        while (history.isNotEmpty() && t - history.first().t > 120_000) history.removeFirst() // 기록 화면을 늦게 열어도 앞 20초가 남도록 2분 보관

        val inc = incident
        if (screen == Screen.COUNTDOWN && inc != null && inc.sentWall == null && !inc.cancelled && countdownLeft() <= 0) send()
        // 평소 주행 통계(재현 조건 비교): 주행 중(1m/s 이상) 표본만 셉니다.
        if (v != null && v > 1f && (q == Quality.OK || q == Quality.TEST_INPUT)) {
            val st = book.stats
            book.stats = st.copy(samples = st.samples + 1, lowSpeed = st.lowSpeed + if (v * 3.6f < 20f) 1 else 0, braking = st.braking + if (b == true) 1 else 0)
        }
        freezeRecords(t)
        if (tick % 20 == 0) signals?.refreshSlow()
        if (tick % 5 == 0) stepLeftItems(t)
        stepLink()
        if (tick % 300 == 0) persist()
        tick++
    }

    /** 주행이 시작되면 오래 보는 화면(리콜 · 구독 · 후기 쓰기)을 닫고 주행 홈으로 돌아갑니다. 들어가는 버튼은 주행 중에 이미 잠겨 있습니다. */
    internal fun closeParkedOnlyScreens() { if (driving && screen in PARKED_ONLY) go(Screen.HOME) }

    // ---------------------------------------------------------------- 이동
    fun go(s: Screen) {
        // 정비소 목록이 아직 없으면(시험 입력 tab 등으로 바로 열 때) 목록과 도착 예상부터 만듭니다.
        if (s == Screen.SERVICE && shops.isEmpty() && shopInfos.isNotEmpty()) { openService(); return }
        // 대처 방법에서 잠깐 다녀오는 화면(경고등 찾기 · 폰 연결)이 아니면 "대처 방법으로 돌아가기"를 지웁니다.
        if (s !in GUIDE_SIDE_TRIPS) returnToGuide = false
        screen = s; demoPanel = false
    }

    fun back() {
        when (screen) {
            Screen.GUIDE -> go(Screen.SITUATION_PARKED)
            Screen.WARN_FIND -> go(if (returnToGuide && guideChoices.isNotEmpty()) Screen.GUIDE else Screen.HOME)
            Screen.WARN_RESULT -> go(Screen.WARN_FIND)
            Screen.WARN_DETAIL -> go(Screen.WARN_RESULT)
            Screen.WARN_SEARCH -> go(Screen.WARN_FIND)
            Screen.PHONE -> go(if (returnToGuide && guideChoices.isNotEmpty()) Screen.GUIDE else Screen.HOME)
            Screen.RECORD, Screen.PATTERN -> go(Screen.RECORD_LIST)
            Screen.REVIEW -> go(Screen.SERVICE)
            Screen.RECALL -> go(Screen.MANAGE)
            Screen.COUNTDOWN -> cancelSend()
            Screen.ACCIDENT -> go(Screen.GUARDIAN)
            else -> go(Screen.HOME)
        }
    }

    fun showToast(text: String) {
        val seq = ++toastSeq; toast = text
        main.postDelayed({ if (seq == toastSeq) toast = null }, 3_500)
    }

    // ---------------------------------------------------------------- 기록
    fun recordNow(symptom: String? = null, part: String? = null) {
        val snap = Record(t0 = now(), wall = wallNow(), drivingAtRecord = driving, symptom = symptom, part = part ?: selectedPart, cabin = cabinNow())
        book.add(snap); lastRecord = snap; bookVersion++
        if (driving) {
            voice?.speak("기록했습니다.")
            showToast("기록했습니다 ${hms(snap.wall)} · 자세한 내용은 정차 후에 봅니다")
        } else {
            go(Screen.RECORD)
        }
    }

    fun memoByVoice() {
        val r = lastRecord ?: return
        voice?.listen(heard = { text -> updateRecord(r.id) { it.copy(memo = text, memoTest = heardTest) }; heardTest = false },
            silence = { showToast("말을 듣지 못했습니다") })
    }

    fun talk() {
        voice?.listen(heard = { text ->
            val test = heardTest; heardTest = false
            when (VoiceRouter.route(text)) {
                VoiceRoute.HELP_REQUEST -> openRecipients(IncidentKind.HELP)
                VoiceRoute.WARNING_FIND -> findWarningByVoice(text)
                VoiceRoute.RECORD_MEMO -> {
                    val snap = Record(t0 = now(), wall = wallNow(), drivingAtRecord = driving, memo = text, memoTest = test, cabin = cabinNow())
                    book.add(snap); lastRecord = snap; bookVersion++
                    voice?.speak("메모를 남겼습니다."); showToast("말로 메모: $text")
                }
            }
        }, silence = { showToast("말을 듣지 못했습니다") })
    }

    // ---------------------------------------------------------------- 기록 책(목록·저장·재현 조건·정비 노트)
    /** 기록 때 함께 남길 실내 상태(H3). SDK 값이 하나도 없으면 문서 대표 사례 값을 [시험 입력]으로 씁니다. */
    fun cabinNow(): Cabin {
        val sg = signals
        val ac = sg?.acOn; val fan = sg?.fanSpeed
        val win = sg?.windows?.let { w -> if (w.any { it == null }) null else w.all { it == 0 } }
        val wip = sg?.wiper?.let { it == ai.pleos.playground.vehicle.constant.control.WiperState.OFF }
        return if (ac == null && win == null && wip == null) Cabin(true, 2, true, true, test = true) else Cabin(ac, fan, win, wip, test = false)
    }

    fun updateRecord(id: String, f: (Record) -> Record) {
        val r = book.byId(id) ?: return
        val n = f(r); book.replace(n)
        if (lastRecord?.id == id) lastRecord = n
        bookVersion++; persist()
    }

    /** 기록 뒤 10초가 지나면 앞 20초·뒤 10초 시간표를 0.5초 간격 점으로 굳혀 저장합니다. */
    private fun freezeRecords(t: Long) {
        book.records.filter { !it.frozen && !it.example && t - it.t0 >= 10_000 }.forEach { r ->
            val pts = (-40..20).mapNotNull { k ->
                val at = r.t0 + k * 500L
                history.minByOrNull { kotlin.math.abs(it.t - at) }?.takeIf { kotlin.math.abs(it.t - at) <= 300 }?.let { smp ->
                    SnapPoint(k * 500, smp.speed?.let { sp -> (sp * 3.6f).roundToInt() }, smp.speedQ == Quality.TEST_INPUT, smp.brake, smp.steering, smp.turn?.name)
                }
            }
            updateRecord(r.id) { it.copy(points = pts, frozen = true) }
        }
    }

    fun persist() { store?.invoke(book.toJson()); careStore?.invoke(CareStore.toJson(careSnapshot())) }
    fun loadBook(json: String?) { book.load(json); bookVersion++ }

    /** 시연 모드: 재현 조건 카드를 보이려고 예시 기록 2건(문서 대표 사례 11월 6일·9일)을 넣습니다. 화면에 "예시 기록"을 붙입니다. */
    fun seedExamples() {
        if (book.records.any { it.example }) { showToast("예시 기록이 이미 있습니다"); return }
        RecordBook.examples(wallNow()).forEach { book.add(it) }
        bookVersion++; persist(); showToast("예시 기록 2건을 넣었습니다(소음 · 앞 타이어)")
    }

    /** 기록 화면 시간표: 굳힌 기록은 저장된 점, 아직이면 지금 보관 중인 표본. (기준 시각, 표본) */
    fun recordSamples(r: Record): Pair<Long, List<Sample>> =
        if (r.frozen || r.example) 0L to r.points.map { pt -> Sample(pt.dt.toLong(), pt.kmh?.let { it / 3.6f }, if (pt.speedTest) Quality.TEST_INPUT else Quality.OK, pt.brake, false, pt.steering,
            pt.turn?.let { name -> runCatching { TurnSignalValue.valueOf(name) }.getOrNull() }) }
        else r.t0 to history.filter { it.t >= r.t0 - 20_000 && it.t <= r.t0 + 10_000 }

    fun pattern(): Pattern? = book.pattern()

    fun addServiceNote(symptom: String, text: String) {
        book.notes.add(ServiceNote(symptom, text.ifBlank { "정비 받음" }, wallNow(), signals?.odometerKm))
        bookVersion++; persist(); showToast("정비 노트를 남겼습니다. 500km 동안 같은 증상이 다시 나는지 봅니다")
    }

    fun openShare(pt: Pattern) {
        // 정비 자료 공유 링크는 [설계](서버 필요). 데모는 모의 링크를 QR로 보여 줍니다.
        shareQr = "https://pleossupercare.example/share/" + pt.records.last().id.take(8)
    }

    // ---------------------------------------------------------------- 출발 전 점검(N1·N7)
    data class CheckItem(val name: String, val value: String, val ok: Boolean, val test: Boolean)

    fun checkItems(): List<CheckItem> {
        val sg = signals
        fun oil(st: ai.pleos.playground.vehicle.constant.control.OilLevelState?) = when (st?.name) { null -> null; "NORMAL", "HIGH" -> "정상" to true; else -> "부족 · 보충 필요" to false }
        val e = oil(sg?.engineOil); val bf = oil(sg?.brakeFluid)
        val w = sg?.washerLow?.let { if (it) "부족 · 보충 필요" to false else "정상" to true }
        val pd = sg?.powerDown?.let { if (it) "표시 있음" to false else "표시 없음" to true }
        return listOf(
            CheckItem("엔진오일", e?.first ?: "정상", e?.second ?: true, e == null),
            CheckItem("브레이크액", bf?.first ?: "정상", bf?.second ?: true, bf == null),
            CheckItem("워셔액", w?.first ?: "부족 · 보충 필요", w?.second ?: false, w == null),
            CheckItem("출력 저하", pd?.first ?: "표시 없음", pd?.second ?: true, pd == null),
        )
    }

    /** 누적 주행거리. SDK 값이 없으면 대표 사례(32,140km)를 [시험 입력]으로. 다음 정기 점검은 35,000km(예시). */
    /**
     * 1,000km 아래(에뮬레이터 처음 값 0km)는 예시 정비 기록(마지막 교체 거리)과 맞지 않아 시험 입력 32,140km를 씁니다.
     * Virtual Console에서 주행거리를 넣으면(PUT /vehicle/odometer) 그 값을 실제로 씁니다.
     */
    fun odometer(): Pair<Float, Boolean> = signals?.odometerKm?.takeIf { it >= 1_000f }?.let { it to false } ?: (32_140f to true)

    // ---------------------------------------------------------------- 정비소 들르기(N5·H2)
    fun openService() {
        // 도착 예상은 내비가 자기 현재 위치에서 계산하므로 내비 위치를 먼저 씁니다. 없으면 FusedLocation, 그것도 없으면 판교 예시 좌표입니다.
        val base = navi?.latLng() ?: location?.last?.let { it.latitude to it.longitude } ?: (37.4127 to 127.0947)
        val (la, lo) = base
        shops = shopInfos.mapIndexed { i, info -> val (dla, dlo) = SHOP_OFFSETS[i % SHOP_OFFSETS.size]; Shop(info.name, la + dla, lo + dlo) }
        if (selectedShop !in shops.indices) selectedShop = 0
        navi?.requestTrips(shops)
        go(Screen.SERVICE)
        loadShopData()
    }

    // ---------------------------------------------------------------- 정차 후 상황 선택(12칸, 최대 3개)
    fun toggleParkedChoice(name: String) {
        parkedChoices = when {
            name in parkedChoices -> parkedChoices - name
            parkedChoices.size < 3 -> parkedChoices + name
            else -> { showToast("최대 3개까지 고릅니다"); parkedChoices }
        }
    }

    // ---------------------------------------------------------------- 대처 안내(2026-11-14 사용자 요청)
    // 도움이 필요해요 → 증상 고르기 → 오른쪽 아래 "대처 방법 보기" → 증상 조합에 맞춘 확인 순서 → 해결 안 됨: 증상 기록 → 정비소에 도움 요청.
    /** 대처 안내에 쓰는 증상(상황 고르기에서 넘겨받음). */
    var guideChoices by mutableStateOf<List<String>>(emptyList())
        private set
    /** 안내 화면에서 남긴 기록(해결 안 됨 → 증상 기록, 주행 중 상황 고르기, 폰 기록). */
    var guideRecordId by mutableStateOf<String?>(null)
        private set
    /** 확인했다고 누른 단계 번호. */
    var guideChecked by mutableStateOf<Set<Int>>(emptySet())
        private set
    /** 정비소 화면 위에 띄울 "도움 요청할 기록". */
    var helpRecordId by mutableStateOf<String?>(null)
    /** 안내 단계에서 경고등 찾기를 열었으면 뒤로 가기가 안내로 돌아옵니다. */
    private var returnToGuide = false

    fun guide(): Guide = SymptomGuide.forChoices(guideChoices)

    /** 홈의 "도움이 필요해요": 지난번에 고른 증상을 비우고 상황 고르기를 엽니다. */
    fun openHelp() {
        parkedChoices = emptyList()
        go(Screen.SITUATION_PARKED)
    }

    /** 상황 고르기의 "대처 방법 보기". */
    fun openGuide() {
        if (parkedChoices.isEmpty()) { showToast("증상을 1개 이상 고르세요"); return }
        if (parkedChoices != guideChoices) { guideRecordId = null; guideChecked = emptySet() }
        guideChoices = parkedChoices
        go(Screen.GUIDE)
        if (driving) voice?.speak(guide().voice)
    }

    /** 주행 중 상황 고르기: 그 순간을 기록하고, 한 줄 안전 안내를 읽은 뒤 안내 화면을 엽니다(주행 중에는 한 줄만, 정차하면 확인 순서). */
    fun quickGuide(symptom: String) {
        val r = Record(t0 = now(), wall = wallNow(), drivingAtRecord = driving, symptom = symptom, cabin = cabinNow())
        book.add(r); lastRecord = r; bookVersion++; persist()
        parkedChoices = listOf(symptom); guideChoices = parkedChoices; guideRecordId = r.id; guideChecked = emptySet()
        go(Screen.GUIDE)
        voice?.speak("기록했습니다. " + guide().voice)
    }

    fun speakGuide() { voice?.speak(guide().voice) }

    fun toggleGuideStep(i: Int) { guideChecked = if (i in guideChecked) guideChecked - i else guideChecked + i }

    /** 단계의 단추. 폰 기록은 안내의 기록에 붙도록 기록부터 남깁니다. */
    fun guideAction(a: GuideAction) {
        when (a) {
            GuideAction.WARN_FIND -> { returnToGuide = true; go(Screen.WARN_FIND) }
            GuideAction.PHONE_NOISE -> { ensureGuideRecord(); requestPhone("noise", guideRecordId, null) }
            GuideAction.PHONE_VIBRATION -> { ensureGuideRecord(); requestPhone("vibration", guideRecordId, null) }
            GuideAction.PHONE_WARN_PHOTO -> { ensureGuideRecord(); requestPhone("warning_photo", guideRecordId, null) }
        }
    }

    private fun ensureGuideRecord() {
        if (guideRecordId != null && book.byId(guideRecordId) != null) return
        val g = guide()
        val r = Record(t0 = now(), wall = wallNow(), drivingAtRecord = driving, symptom = g.symptoms.joinToString(" · "), cabin = cabinNow(),
            severity = if (g.level == GuideLevel.STOP) 3 else 0)
        book.add(r); lastRecord = r; bookVersion++; persist()
        guideRecordId = r.id
    }

    /** 해결 안 됨 → 고른 증상을 지금 차 상태와 함께 기록합니다. 안내 화면에 머물고 다음 단계(정비소에 도움 요청)를 보입니다. */
    fun recordFromGuide() {
        val had = guideRecordId != null && book.byId(guideRecordId) != null
        ensureGuideRecord()
        showToast(if (had) "이미 기록했습니다 · 정비소에 보여 줄 수 있어요" else "증상을 기록했습니다 · 정비소에 보여 줄 수 있어요")
    }

    /** 정비소에 도움 요청: 기록이 없으면 먼저 남기고, 정비소 화면 위에 그 기록을 보여 줄 띠를 띄웁니다. */
    fun requestShopHelp() {
        ensureGuideRecord()
        helpRecordId = guideRecordId
        openService()
    }

    /** 정비소에 보여 주기(모의 공유 링크 QR). 보여 준 기록은 처리 흐름이 '정비소에 보여 줌'으로 바뀝니다. */
    fun shareRecord(id: String) {
        val r = book.byId(id) ?: return
        shareQr = "https://pleossupercare.example/share/" + r.id.take(8)
        if (r.status == RecordStatus.OPEN.name) updateRecord(r.id) { it.copy(status = RecordStatus.SHOWN.name) }
    }

    /** 해결됐어요: 안내에서 남긴 기록이 있으면 '해결'로 바꾸고 홈으로 갑니다. */
    fun guideResolved() {
        guideRecordId?.let { id -> if (book.byId(id) != null) updateRecord(id) { it.copy(status = RecordStatus.DONE.name) } }
        guideChoices = emptyList(); parkedChoices = emptyList(); guideRecordId = null; guideChecked = emptySet()
        go(Screen.HOME)
        showToast("해결돼서 다행입니다 · 다시 생기면 '증상 기록하기'를 누르세요")
    }

    // ---------------------------------------------------------------- 보험사(처음 설정 · 안심 화면에서 바꾸기)
    /** 내 자동차 보험사([Insurers] id). */
    var insurerId by mutableStateOf<String?>(null)
    /** 처음 설정에서 보험사를 물었는지. */
    var insurerAsked by mutableStateOf(false)
    /** 보험사 고르기 창. */
    var insurerPicker by mutableStateOf(false)
    /** 고르기 창에서 고른 값(저장 전). */
    var insurerDraft by mutableStateOf<String?>(null)

    fun insurer(): Insurer? = Insurers.byId(insurerId)

    fun openInsurerPicker() { insurerDraft = insurerId; insurerPicker = true }

    fun saveInsurer(id: String?) {
        insurerId = id; insurerAsked = true; insurerPicker = false; careChanged()
        showToast(Insurers.byId(id)?.let { "내 보험사를 저장했습니다 · ${it.name} ${it.phone}" } ?: "보험사를 비웠습니다 · 사고 때 보험사별 번호를 보여 드려요")
    }

    /** 보험사 연결(모의). 실제 전화는 걸지 않습니다. */
    fun mockCall(ins: Insurer) {
        voice?.speak("${ins.name}에 연결합니다. 모의 연결이라 실제 전화는 걸지 않습니다.")
        showToast("모의 연결 · ${ins.name} ${ins.phone} · 실제 전화는 걸지 않습니다")
    }

    fun skipInsurerSetup() {
        insurerAsked = true; insurerPicker = false; careChanged()
        showToast("나중에 안심 화면에서 보험사를 정할 수 있어요")
    }

    // ---------------------------------------------------------------- 사고 대처 안내(2026-11-14 사용자 요청)
    // 보호자에게 알린 뒤: 사고가 맞나요?(시간 · 장소) → 2차 사고 주의 + 내 보험사 사고 접수 번호, 연결할까요? → 연결(모의) → 접수 뒤 안내.
    // 답은 화면 단추 또는 질문 바로 뒤 8초 동안의 말("예" · "아니요")로 합니다. 전화는 실제로 걸지 않습니다(모의 연결).
    var accStep by mutableStateOf(AccStep.CONFIRM)
        private set
    /** 이번에 연결한 보험사(내 보험사 또는 목록에서 고른 보험사). */
    var accInsurer by mutableStateOf<Insurer?>(null)
        private set
    var accCallFrom by mutableStateOf<Long?>(null)
        private set
    var accReported by mutableStateOf(false)
        private set
    var bodyState by mutableStateOf<BodyState?>(null)
        private set
    /** 사고 안내 질문 뒤 듣는 중. */
    var accListening by mutableStateOf(false)
        private set
    /** 마지막으로 들은 말(알아듣지 못했을 때 화면에 보입니다). */
    var accHeard by mutableStateOf<String?>(null)
        private set
    /** 접수 뒤 안내에서 확인했다고 누른 카드. */
    var accDone by mutableStateOf<Set<String>>(emptySet())
        private set
    /** 현장 사진(폰)을 붙일 사고 기록. */
    var accRecordId by mutableStateOf<String?>(null)
        internal set // 화면 시험이 "사진 받음" 상태를 만들 때만 씁니다

    fun accTime(): String = incident?.let { ymdhm(it.detectedWall) } ?: ymdhm(wallNow())

    /** 사고 발생 장소: 내비 주소(차의 현재 위치 기준). 없으면 좌표, 그것도 없으면 확인 중. */
    fun accPlace(): String {
        val s = incident?.snap
        return s?.address?.takeIf { it.isNotBlank() } ?: s?.lat?.let { la -> s.lng?.let { lo -> "좌표 %.4f, %.4f".format(la, lo) } } ?: "위치 확인 중"
    }

    fun startAccidentGuide() {
        accStep = AccStep.CONFIRM; accInsurer = null; accCallFrom = null; accReported = false; bodyState = null; accDone = emptySet(); accHeard = null; accRecordId = null
        go(Screen.ACCIDENT)
        ask(AccStep.CONFIRM, "보호자에게 알렸습니다. 사고가 맞나요? 예 또는 아니요로 답해 주세요.") { yes -> if (yes) accidentYes() else accidentNo() }
    }

    fun accidentYes() {
        if (accStep != AccStep.CONFIRM) return
        accStep = AccStep.INSURER
        val ins = insurer()
        val caution = "도로라면 2차 사고가 날 수 있으니 조심하세요. "
        if (ins != null) ask(AccStep.INSURER, caution + "${ins.name} 긴급 사고 접수 번호는 ${spokenPhone(ins.phone)}입니다. 사고 접수 연락을 할까요?") { yes -> if (yes) callInsurer(ins) else skipReport() }
        else voice?.speak(caution + "보험사를 고르면 사고 접수를 연결합니다.")
    }

    fun accidentNo() {
        if (accStep != AccStep.CONFIRM) return
        voice?.stopSpeaking()
        go(Screen.GUARDIAN)
        showToast("사고가 아니라고 답했습니다 · 보호자 알림 상태는 이 화면에 있어요")
    }

    fun callInsurer(ins: Insurer) {
        accInsurer = ins; accCallFrom = wallNow(); accStep = AccStep.CALLING; accListening = false
        voice?.speak("${ins.name}에 연결합니다. 모의 연결이라 실제 전화는 걸지 않습니다. 상담원에게 사고 시간과 장소를 알려 주세요.")
    }

    fun hangUp() {
        accCallFrom = null; accStep = AccStep.INSURER
        voice?.stopSpeaking(); showToast("연결을 끊었습니다(모의)")
    }

    fun reportDone() { accReported = true; startAfter("접수를 마쳤습니다. ") }

    fun skipReport() { startAfter("") }

    private fun startAfter(prefix: String) {
        accStep = AccStep.AFTER; accListening = false
        askBody(prefix + "몸 상태를 확인할게요. 다친 곳이 있나요? 괜찮아요, 조금 아파요, 많이 다쳤어요 가운데 하나로 답해 주세요.")
    }

    fun setBody(b: BodyState) {
        bodyState = b; accDone = accDone + "body"
        voice?.speak(when (b) {
            BodyState.SEVERE -> "움직이지 말고 119에 연락하세요. 화면의 119 연결을 누르면 연결합니다."
            BodyState.MINOR -> "사고 뒤 늦게 아플 수 있어요. 병원 진료를 받아 두세요."
            BodyState.OK -> "다행입니다. 비상등을 켜고, 차가 움직이면 갓길 같은 안전한 곳으로 먼저 옮긴 뒤 사진을 찍으세요."
        })
    }

    /** 119 연결(모의). 실제 전화는 걸지 않습니다. */
    fun call119() {
        voice?.speak("119에 연결합니다. 모의 연결이라 실제 전화는 걸지 않습니다.")
        showToast("119 연결(모의) · 실제 전화는 걸지 않습니다")
    }

    fun toggleAccCard(key: String) { accDone = if (key in accDone) accDone - key else accDone + key }

    /** 현장 사진: 폰 카메라로 찍어 사고 기록에 붙입니다(폰 연결 필요). 폰 요청 카드에는 부위 대신 "사고 현장 사진"이 보입니다. */
    fun accidentPhoto() {
        if (link == null || pairedPhone == null) { requestPhone("photo"); return }
        if (book.byId(accRecordId) == null) {
            val r = Record(t0 = now(), wall = incident?.detectedWall ?: wallNow(), drivingAtRecord = false, symptom = "사고", cabin = cabinNow(), severity = 3)
            book.add(r); lastRecord = r; bookVersion++; persist()
            accRecordId = r.id
        }
        requestPhone("photo", accRecordId, null, text = ACC_PHOTO_TEXT, usePart = false)
    }

    /** 사고 기록에 붙은 폰 사진 수(사고 현장 촬영 카드에 보입니다). */
    fun accPhotoCount(): Int = book.byId(accRecordId)?.evidence?.count { it.kind == "photo" } ?: 0

    fun finishAccident() {
        voice?.stopSpeaking(); accListening = false
        go(Screen.HOME)
        showToast("사고 대처 안내를 마쳤습니다 · 보호자 알림 상태는 안심 화면에 있어요")
    }

    /** 질문을 읽고 바로 뒤 8초 동안 "예" · "아니요"를 듣습니다. 그사이 화면이 바뀌면 답을 버립니다. */
    private fun ask(step: AccStep, question: String, onAnswer: (Boolean) -> Unit) {
        val v = voice ?: return
        accHeard = null
        v.speak(question) {
            if (screen != Screen.ACCIDENT || accStep != step) return@speak
            accListening = true
            v.listen(heard = { text ->
                accListening = false; accHeard = text
                if (screen == Screen.ACCIDENT && accStep == step) {
                    val a = YesNo.parse(text)
                    if (a != null) onAnswer(a) else showToast("'예' 또는 '아니요'로 답하거나 화면을 눌러 주세요")
                }
            }, silence = { accListening = false })
        }
    }

    private fun askBody(question: String) {
        val v = voice ?: return
        accHeard = null
        v.speak(question) {
            if (screen != Screen.ACCIDENT || accStep != AccStep.AFTER || bodyState != null) return@speak
            accListening = true
            v.listen(heard = { text ->
                accListening = false; accHeard = text
                if (screen == Screen.ACCIDENT && accStep == AccStep.AFTER && bodyState == null) {
                    val b = BodyAnswer.parse(text)
                    if (b != null) setBody(b) else showToast("화면에서 몸 상태를 골라 주세요")
                }
            }, silence = { accListening = false })
        }
    }

    // ---------------------------------------------------------------- 경고등: 말로 찾기
    /** 경고등 찾기의 "말로 찾기" 버튼: 8초 듣고 색·모양을 고릅니다. */
    fun findByVoiceButton() {
        voice?.listen(heard = { text -> heardTest = false; findWarningByVoice(text) }, silence = { showToast("말을 듣지 못했습니다") })
    }

    /** 문구로 찾기(키보드)는 정차 중에만 엽니다. */
    fun openWarnSearch() {
        if (driving) showToast("문구로 찾기(키보드)는 정차 중에만 씁니다") else go(Screen.WARN_SEARCH)
    }

    /**
     * "주황색 타이어 모양"처럼 색·모양 낱말을 고릅니다. 색을 말하지 않으면 그 모양의 보통 색으로 봅니다.
     * 주행 중에는 운행 판단 한 줄과 음성만 냅니다.
     */
    fun findWarningByVoice(text: String) {
        val (color, shape, mode) = WarningCatalog.parseSpoken(text)
        if (shape == null) {
            val line = "경고등 모양을 알아듣지 못했습니다"
            if (driving) { voice?.speak(line); showToast("말로 찾기: $line · 정차 후 화면에서 고르세요") } else { showToast("$line · 화면에서 골라 주세요"); go(Screen.WARN_FIND) }
            return
        }
        warnShape = shape
        warnColor = color ?: WarningCatalog.usualColors(shape).first()
        warnMode = mode
        val r = warnResult()
        if (driving) {
            val line = r?.primary?.judgement ?: "정차 후 경고등 찾기에서 확인하세요"
            voice?.speak(line); showToast("말로 찾기: ${r?.primary?.name ?: shape} · $line")
        } else go(Screen.WARN_RESULT)
    }

    /** 지금 고른 색·모양·켜짐의 도감 결과. 셋 중 하나라도 고르지 않았으면 null입니다. */
    fun warnResult(): WarnResult? {
        val col = warnColor; val sh = warnShape; val m = warnMode
        return if (col != null && sh != null && m != null) WarningCatalog.find(col, sh, m) else null
    }

    /** 정차 후 자세히. [id]를 주면 그 경고를 고른 것으로 하고 엽니다(출발 전 점검의 "이번 주 경고등 익히기"). */
    fun openWarnDetail(id: String? = null) {
        WarningCatalog.byId(id)?.let { e -> warnColor = e.color; warnShape = e.shape; warnMode = WarningCatalog.modeOf(e) }
        if (driving) showToast("자세한 설명은 정차 후에 열립니다") else go(Screen.WARN_DETAIL)
    }




    // ---------------------------------------------------------------- 보호자 알림(모의)
    /** 지금 위치: FusedLocation 마지막 위치와 NaviHelper 주소. timestampMillis가 벽시계로 보이면(2020년 이후) 측정 시각으로, 아니면 앱이 받은 시각을 씁니다. */
    private fun locPart(): IncidentSnap {
        val loc = location?.last
        val measured = loc?.timestampMillis?.takeIf { it > 1_577_836_800_000L }
        val base = measured ?: location?.receivedWall
        val nav = navi?.latLng()
        return IncidentSnap(locWall = base?.let { it + clockOffsetMs }, locMeasured = measured != null, lat = loc?.latitude, lng = loc?.longitude,
            confidence = loc?.confidence, address = navi?.address, naviLat = nav?.first, naviLng = nav?.second)
    }

    /** 사건 순간의 차 상태(좌석 · 안전벨트 · 문)와 위치를 담습니다. 위치와 주소는 한 번 더 물어 보낼 때 새 값이 있으면 바꿉니다. */
    private fun snapNow(): IncidentSnap {
        location?.refresh(); navi?.refreshAddress()
        val s = signals
        return locPart().copy(seats = s?.seats ?: List(4) { null }, belts = s?.belts ?: List(4) { null }, doors = s?.doors ?: List(4) { null })
    }

    fun openRecipients(kind: IncidentKind) {
        voice?.stopSpeaking()
        incident = Incident(kind, wallNow(), testInput = drivingIsTest || testSpeedKmh != null, countdownFrom = null, snap = snapNow())
        go(Screen.RECIPIENTS)
    }

    /** 에어백 전개 신호(시험 입력) 또는 "사고가 났어요" → 발송 확인 10초. */
    fun startCountdown(kind: IncidentKind, testInput: Boolean) {
        incident = Incident(kind, wallNow(), testInput = testInput || drivingIsTest || testSpeedKmh != null, countdownFrom = now(), snap = snapNow())
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
        // 보낼 때 더 새 위치를 받았으면 그 위치를 보냅니다. 주소는 늦게 오면 채웁니다.
        val lp = locPart(); val old = inc.snap
        val snap = if (lp.locWall != null && (old.locWall == null || lp.locWall > old.locWall))
            old.copy(locWall = lp.locWall, locMeasured = lp.locMeasured, lat = lp.lat, lng = lp.lng, confidence = lp.confidence, address = lp.address ?: old.address,
                naviLat = lp.naviLat ?: old.naviLat, naviLng = lp.naviLng ?: old.naviLng)
        else old.copy(address = old.address ?: lp.address, naviLat = old.naviLat ?: lp.naviLat, naviLng = old.naviLng ?: lp.naviLng)
        incident = inc.copy(snap = snap)
        // 사고 · 에어백: 보호자에게 알린 뒤 사고 대처 안내로 이어 갑니다(2026-11-14 사용자 요청). 도움 요청은 보호자 알림 상태만 봅니다.
        if (inc.kind == IncidentKind.ACCIDENT || inc.kind == IncidentKind.AIRBAG) startAccidentGuide()
        else { go(Screen.GUARDIAN); voice?.speak("보호자에게 알렸습니다. 위치를 보내는 중입니다.") }
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

    /**
     * 보호자 알림 단계(1 접수 · 2 전달 · 3 열람)와 짧은 이름(6자 안팎). [guardianRows]와 같은 시간표입니다.
     * 보호자 2는 알림톡 전달 확인이 늦어 문자로 다시 보낸 경우 2단계 '문자로 다시'로 둡니다.
     */
    fun guardianStep(i: Int): Pair<Int, String> {
        val sent = incident?.sentWall ?: return 0 to "준비됨"
        val el = wallNow() - sent
        return if (i == 0) when {
            el < 2_000 -> 1 to "접수"
            el < G1_OPEN_MS -> 2 to "전달"
            else -> 3 to "열람 · 전화할게요"
        } else if (el < RulesLab.KAKAO_DEADLINE_MS) 1 to "전달 확인 중" else 2 to "문자로 다시 보냄"
    }

    fun guardianDone(i: Int): Boolean {
        val sent = incident?.sentWall ?: return false
        val el = wallNow() - sent
        return if (i == 0) el >= G1_OPEN_MS else el >= RulesLab.KAKAO_DEADLINE_MS
    }


    // ================================================================ 케어(2026-11-14 컨셉 전환: 관리 · 정비 · 구독 · 리콜 · 짐 알림 · 폰 연결)
    /** 케어 상태 저장소(앱에서는 care.json, 시험에서는 없음). */
    var careStore: ((String) -> Unit)? = null
    /** 모의 서버 연결(앱에서 넣습니다). 없으면 앱 안 예시 자료만 씁니다. */
    var link: CareLink? = null
    /** 이 차의 id(서버 짝 맺기 · 기록 요청). 앱에서 처음 한 번 만들어 저장합니다. */
    var carId: String = "car-demo"

    fun loadCare(json: String?) {
        val s = CareStore.from(json)
        subscribed = s.subscribed; recallDone = s.recallDone; myReviews = s.myReviews; visits = s.visits
        inbox = s.inbox; captureSince = s.captureSince
        leftCare.enabled = s.leftEnabled; leftCare.rearEnabled = s.rearEnabled
        s.carId?.let { carId = it }
        pairedPhone = s.pairedPhone
        recallConfirmed = s.recallConfirmed; recallNotMine = s.recallNotMine; leftCare.reduced = s.leftReduced
        insurerId = s.insurerId; insurerAsked = s.insurerAsked
        careVersion++
    }

    fun careSnapshot() = CareSnapshot(subscribed, recallDone, myReviews, visits, inbox.take(12), captureSince, leftCare.enabled, leftCare.rearEnabled, carId, pairedPhone,
        recallConfirmed, recallNotMine, leftCare.reduced, insurerId, insurerAsked)

    /** 케어 자료가 바뀔 때마다 늘어납니다(화면 다시 그리기). */
    var careVersion by mutableIntStateOf(0)
        private set
    private fun careChanged() { careVersion++; persist() }

    // ---------------------------------------------------------------- 소모품 · 정기 점검
    /** 소모품과 남은 거리(km). 주기는 예시 값입니다. */
    fun consumables(): List<Pair<Consumable, Int>> { val odo = odometer().first; return CareData.CONSUMABLES.map { it to it.leftKm(odo) } }
    /** 가장 먼저 할 정비. */
    fun nextMaintenance(): Pair<Consumable, Int> = consumables().minBy { it.second }

    // ---------------------------------------------------------------- 리콜
    var recalls by mutableStateOf(CareData.RECALLS)
    var recallsFromServer by mutableStateOf(false)
    var recallDone by mutableStateOf<Set<String>>(emptySet())
    var selectedRecall by mutableStateOf<String?>(null)

    var recallConfirmed by mutableStateOf<Set<String>>(emptySet())
    var recallNotMine by mutableStateOf<Set<String>>(emptySet())

    fun recallIsDone(r: Recall) = r.done || r.id in recallDone
    /** 내 차 상태(조사 5.4절): 대상일 수 있음 → 대상 맞음 · 대상 아님 · 조치 완료(사용자가 리콜센터에서 확인하고 고름). */
    fun recallState(r: Recall): String = when { recallIsDone(r) -> "조치 완료"; r.id in recallNotMine -> "대상 아님"; r.id in recallConfirmed -> "대상 맞음"; else -> "대상일 수 있음" }
    fun openRecalls(): List<Recall> = recalls.filter { !recallIsDone(it) && it.id !in recallNotMine }

    fun setRecallState(id: String, state: String) {
        recallDone = if (state == "조치 완료") recallDone + id else recallDone - id
        recallNotMine = if (state == "대상 아님") recallNotMine + id else recallNotMine - id
        recallConfirmed = if (state == "대상 맞음") recallConfirmed + id else recallConfirmed - id
        careChanged(); showToast("내 차 상태: $state")
    }

    /** 리콜센터에서 직접 확인(폰으로 QR). 주소에는 차대번호 · 등록번호를 넣지 않습니다. */
    fun openRecallCenter() {
        qrSpec = QrSpec(CareData.RECALL_CENTER, "리콜센터에서 확인", "폰으로 찍어 자동차리콜센터를 열고 등록번호나 차대번호로 직접 조회하세요", "공식 사이트 · 개인정보는 주소에 넣지 않음")
    }

    fun openRecall(id: String? = null) {
        selectedRecall = id ?: selectedRecall ?: openRecalls().firstOrNull()?.id ?: recalls.firstOrNull()?.id
        go(Screen.RECALL)
        link?.recalls(CareData.MODEL, CareData.YEAR) { r -> r.onSuccess { list -> if (list.isNotEmpty()) { recalls = list; recallsFromServer = true; if (recalls.none { it.id == selectedRecall }) selectedRecall = openRecalls().firstOrNull()?.id ?: list.first().id } } }
    }

    fun markRecallDone(id: String) = setRecallState(id, "조치 완료")

    // ---------------------------------------------------------------- 구독(모의)
    var subscribed by mutableStateOf<Set<String>>(emptySet())
    var selectedPlan by mutableStateOf<String?>(null)

    fun toggleSubscribe(id: String) {
        val plan = CareData.PLANS.firstOrNull { it.id == id } ?: return
        if (!plan.demo) { showToast("설계 단계 상품입니다 · 데모에서는 가입하지 않습니다"); return }
        if (id in subscribed) { subscribed = subscribed - id; showToast("${plan.name} 해지(모의) · 해지는 가입만큼 쉽게"); careChanged(); return }
        // 결제는 폰에서 합니다(조사 4.5절). 차 화면은 QR만 보이고, 닫으면 모의 가입으로 표시합니다.
        qrSpec = QrSpec("https://pleoscare.example/plan/" + plan.id, "폰에서 가입", "${plan.name} · 결제는 폰에서 합니다", "모의 링크 · 결제 없음") {
            subscribed = subscribed + id; careChanged(); showToast("${plan.name} 가입(모의 · 결제 없음)")
        }
    }

    // ---------------------------------------------------------------- 정비소 상세 · 후기 · 방문
    var shopInfos by mutableStateOf(CareData.SHOPS)
    var shopsFromServer by mutableStateOf(false)
    /** 서버에서 받은 다른 사용자 후기(정비소 id → 목록). 없으면 예시 후기를 씁니다. */
    var serverReviews by mutableStateOf<Map<String, List<Review>>>(emptyMap())
    var myReviews by mutableStateOf<List<Review>>(emptyList())
    var visits by mutableStateOf<List<Visit>>(emptyList())

    fun selectedShopInfo(): ShopInfo? = shopInfos.getOrNull(selectedShop)

    fun selectShop(i: Int) { selectedShop = i; shopInfos.getOrNull(i)?.let { loadReviews(it.id) } }

    private fun loadShopData() {
        val lk = link ?: return
        lk.shops { r ->
            r.onSuccess { list ->
                if (list.isNotEmpty() && list.map { it.id } != shopInfos.map { it.id }) {
                    shopInfos = list; shopsFromServer = true
                    val base = navi?.latLng() ?: location?.last?.let { it.latitude to it.longitude } ?: (37.4127 to 127.0947)
                    shops = list.mapIndexed { i, info -> val (dla, dlo) = SHOP_OFFSETS[i % SHOP_OFFSETS.size]; Shop(info.name, base.first + dla, base.second + dlo) }
                    if (selectedShop !in shops.indices) selectedShop = 0
                    navi?.requestTrips(shops)
                } else if (list.isNotEmpty()) { shopInfos = list; shopsFromServer = true }
                selectedShopInfo()?.let { loadReviews(it.id) }
            }
        }
    }

    private fun loadReviews(shopId: String) {
        link?.reviews(shopId, carId) { r -> r.onSuccess { list -> serverReviews = serverReviews + (shopId to list) } }
    }

    /** 정비소 후기: 다른 사용자 후기(서버, 없으면 예시) + 이 차에서 쓴 후기. 새것이 앞. */
    fun shopReviews(shopId: String): List<Review> {
        val others = serverReviews[shopId] ?: CareData.localReviews(shopId, wallNow())
        val mine = myReviews.filter { it.shopId == shopId }
        return (mine + others.filter { o -> mine.none { it.id == o.id } }).filter { it.id !in hiddenReviews }.sortedByDescending { it.wall }
    }

    fun visitsAt(shopId: String) = visits.filter { it.shopId == shopId }.sortedByDescending { it.wall }

    /** 방문 기록 남기기. 할 일은 열린 증상 기록 · 다음 정비에서 고릅니다. */
    fun addVisit() {
        val info = selectedShopInfo() ?: return
        val work = book.records.filter { it.symptom != null && it.status == app.pleosguard.demo.logic.RecordStatus.OPEN.name }.mapNotNull { it.symptom }.distinct().take(2)
            .ifEmpty { listOf(nextMaintenance().first.name) }.joinToString(" · ")
        val local = Visit(java.util.UUID.randomUUID().toString(), info.id, info.name, wallNow(), work, reviewed = false)
        visits = visits + local
        careChanged(); showToast("방문 기록을 남겼습니다 · ${info.name}")
        // 서버에도 남겨 방문 확인 후기의 근거로 씁니다. 서버 id를 받으면 바꿔 둡니다.
        link?.postVisit(carId, info.id, work) { r -> r.onSuccess { id -> if (id.isNotBlank()) { visits = visits.map { if (it.id == local.id) it.copy(id = id) else it }; careChanged() } } }
    }

    // 후기 쓰기
    var reviewShopId by mutableStateOf<String?>(null)
    var draftRating by mutableIntStateOf(0)
    var draftDims by mutableStateOf<Map<String, Int>>(emptyMap())
    var draftTags by mutableStateOf<Set<String>>(emptySet())
    var draftText by mutableStateOf("")

    /** 후기를 쓸 수 있는 방문(아직 후기 없는 방문 기록)이 있는지. 방문 확인 후기만 받습니다(조사 6.4절). */
    fun canReview(shopId: String) = visits.any { it.shopId == shopId && !it.reviewed }

    /** 신고해 가린 후기(모의: 검토 30일 동안 가림). */
    var hiddenReviews by mutableStateOf<Set<String>>(emptySet())
    fun reportReview(id: String) { hiddenReviews = hiddenReviews + id; showToast("신고했습니다(모의) · 검토하는 동안 가립니다") }

    fun openReview(shopId: String? = selectedShopInfo()?.id) {
        if (driving) { showToast("후기는 정차 중에 씁니다"); return }
        if (shopId == null) return
        if (!canReview(shopId)) { showToast("방문을 기록한 뒤 후기를 씁니다 · 방문 확인 후기만 받습니다"); return }
        reviewShopId = shopId
        draftRating = 0; draftDims = emptyMap(); draftTags = emptySet(); draftText = ""
        go(Screen.REVIEW)
    }

    fun reviewByVoice() {
        voice?.listen(heard = { text -> draftText = text; heardTest = false }, silence = { showToast("말을 듣지 못했습니다") })
    }

    fun submitReview() {
        val shopId = reviewShopId ?: return
        if (draftRating == 0) { showToast("별점을 먼저 골라 주세요"); return }
        val r = Review(java.util.UUID.randomUUID().toString(), shopId, "내 차 · 아반떼", draftRating, draftDims, draftTags.toList(), draftText, wallNow(), mine = true, example = false)
        myReviews = myReviews + r
        // 이 정비소의 아직 후기 없는 방문 하나에 표시
        visits.lastOrNull { it.shopId == shopId && !it.reviewed }?.let { v -> visits = visits.map { if (it.id == v.id) it.copy(reviewed = true) else it } }
        shopInfos = shopInfos.map { s -> if (s.id == shopId) s.copy(rating = (s.rating * s.reviewCount + draftRating) / (s.reviewCount + 1), reviewCount = s.reviewCount + 1) else s }
        careChanged()
        val lk = link
        if (lk != null) lk.postReview(r, carId, visits.lastOrNull { it.shopId == shopId }?.id) { res ->
            res.onSuccess { showToast("후기를 남겼습니다 · 다른 사용자도 볼 수 있습니다(모의 서버)") }
            res.onFailure { showToast("후기를 이 차에 남겼습니다 · 서버 연결 없음") }
        } else showToast("후기를 이 차에 남겼습니다 · 서버 연결 없음")
        go(Screen.SERVICE)
    }

    // ---------------------------------------------------------------- 짐 알림(트렁크 · 뒷좌석)
    val leftCare = LeftItemCare()
    var leftAlert by mutableStateOf<LeftItemCare.Alert?>(null)
    var leftVersion by mutableIntStateOf(0)
        private set
    /** 시연 모드 시험 입력(Virtual Console 대신). null이면 실제 신호를 씁니다. */
    var testTrunk by mutableStateOf<Int?>(null)
    var testDriverDoor by mutableStateOf<Int?>(null)

    /** 트렁크 위치와 시험 입력 여부. */
    fun trunkNow(): Pair<Int?, Boolean> = testTrunk?.let { it to true } ?: (signals?.trunk to false)
    fun driverDoorNow(): Pair<Int?, Boolean> = testDriverDoor?.let { it to true } ?: (signals?.doors?.getOrNull(0) to false)

    private fun stepLeftItems(t: Long) {
        val (v, q) = speedView
        val kmhNow = if (v != null && (q == Quality.OK || q == Quality.TEST_INPUT)) v * 3.6f else 0f
        val moving = kmhNow >= 5f || (driving && !drivingUnknown)
        val rear = signals?.doors?.let { listOf(it.getOrNull(2), it.getOrNull(3)) } ?: listOf(null, null)
        val before = leftCare.events.firstOrNull()
        val a = leftCare.update(t, wallNow(), moving, trunkNow().first, driverDoorNow().first, rear)
        if (a != null) raiseLeftAlert(a)
        if (leftCare.takeSpeak()) leftAlert?.let { voice?.speak(leftText(it)) }
        leftAlert?.let { cur ->
            // 트렁크를 열어 확인했으면 카드는 조용히 닫습니다(조사 7.3절).
            if (moving || (!cur.test && cur.kinds.none { leftCare.stillNeeded(it) })) { leftAlert = null; leftCare.closed() }
        }
        if (leftCare.events.firstOrNull() !== before) leftVersion++
    }

    fun leftText(a: LeftItemCare.Alert): String = when {
        LeftItemCare.Kind.TRUNK in a.kinds && LeftItemCare.Kind.REAR_SEAT in a.kinds -> "트렁크와 뒷좌석에 짐이 남아 있지 않나요?"
        LeftItemCare.Kind.TRUNK in a.kinds -> "트렁크에 짐이 남아 있지 않나요?"
        else -> "뒷좌석에 두고 내리는 것이 없나요?"
    }

    private fun raiseLeftAlert(a: LeftItemCare.Alert) {
        leftAlert = a
        val text = leftText(a)
        if (a.voice) voice?.speak(text)
        if (pairedPhone != null) link?.request(carId, if (LeftItemCare.Kind.TRUNK in a.kinds) "trunk_reminder" else "rear_seat_reminder", null, null, text) { }
    }

    /** 시연 패널: 짐 알림을 바로 띄웁니다(주행 · 도착 순서 없이 화면만 보이기). [시험 입력]이 붙습니다. */
    fun showTestLeftAlert() {
        leftAlert = LeftItemCare.Alert(setOf(LeftItemCare.Kind.TRUNK), wallNow() - 42 * 60_000L, wallNow() - 20_000L, test = true)
    }

    fun answerLeftAlert(noItems: Boolean) {
        leftCare.dismiss(wallNow(), noItems); leftAlert = null; leftVersion++
        showToast(when { leftCare.askReduce -> "세 번 연속 짐이 없었습니다 · 관리 화면에서 알림을 줄일 수 있습니다"; noItems -> "짐 없음으로 닫았습니다"; else -> "확인했습니다" })
    }

    fun answerReduce(reduce: Boolean) { leftCare.answerReduce(reduce); leftVersion++; careChanged(); showToast(if (reduce) "짧게 연 트렁크는 세지 않습니다" else "그대로 둡니다") }

    fun toggleLeftCare() { leftCare.enabled = !leftCare.enabled; leftVersion++; careChanged() }
    fun toggleRearCare() { leftCare.rearEnabled = !leftCare.rearEnabled; leftVersion++; careChanged() }

    // ---------------------------------------------------------------- 폰 연결 · 폰 증거
    var serverOk by mutableStateOf<Boolean?>(null)
    var pairCode by mutableStateOf<String?>(null)
    var pairExpires by mutableStateOf(0L)
    var pairedPhone by mutableStateOf<String?>(null)
    /** 폰이 코드를 넣고 차 화면의 허락을 기다리는 중(폰 이름). */
    var pairPending by mutableStateOf<String?>(null)
    /** 폰 기록을 기다리는 중(요청 종류 · 붙일 기록 · 요청 id). */
    data class PhoneWait(val kind: String, val recordId: String?, val requestId: String?, val sinceWall: Long)
    var phoneWait by mutableStateOf<PhoneWait?>(null)
    /** 폰에서 받은 증거(새것이 앞, 최근 12개). */
    var inbox by mutableStateOf<List<Evidence>>(emptyList())
    var captureSince = 0L
    private var linkBusy = false
    private var pairBusy = false

    /** 폰 연결 화면을 엽니다. 아직 연결 전이면 서버에 짝 맺기를 시작합니다. */
    fun openPhone() {
        go(Screen.PHONE)
        if (pairedPhone == null) startPair()
    }

    /** 차 화면에서 폰 연결을 허락하거나 거절합니다. */
    fun confirmPair(accept: Boolean) {
        val lk = link ?: return
        val name = pairPending
        lk.pairConfirm(carId, accept) { r ->
            r.onSuccess {
                pairPending = null
                if (accept) { pairedPhone = name ?: "폰"; careChanged(); showToast("폰을 연결했습니다 · ${pairedPhone}") }
                else { showToast("연결하지 않았습니다"); startPair() }
            }
            r.onFailure { showToast("서버에 연결하지 못했습니다") }
        }
    }

    fun forgetPhone() { pairedPhone = null; pairPending = null; careChanged(); startPair() }

    fun startPair() {
        val lk = link
        if (lk == null) { serverOk = false; return }
        lk.pairStart(carId) { r ->
            r.onSuccess { (code, exp) -> pairCode = code; pairExpires = exp; serverOk = true }
            r.onFailure { pairCode = null; serverOk = false }
        }
    }

    /** 0.1초마다 불립니다. 폰 연결 화면이거나 폰이 연결돼 있을 때만 서버에 묻습니다. */
    private fun stepLink() {
        val lk = link ?: return
        val onPhone = screen == Screen.PHONE
        if (onPhone && pairedPhone == null && pairCode != null && tick % 20 == 0 && !pairBusy) {
            pairBusy = true
            lk.pairStatus(carId) { r ->
                pairBusy = false
                r.onSuccess { st ->
                    when {
                        st.paired && pairedPhone == null -> { pairedPhone = st.phoneName ?: "폰"; pairPending = null; careChanged(); showToast("폰이 연결됐습니다 · ${pairedPhone}") }
                        st.pending -> pairPending = st.phoneName ?: "폰"
                    }
                }
            }
        }
        val every = if (phoneWait != null || onPhone) 20 else 50
        if (pairedPhone != null && tick % every == 0 && !linkBusy) {
            linkBusy = true
            lk.captures(carId, captureSince) { r ->
                linkBusy = false
                r.onSuccess { list -> serverOk = true; list.forEach { receive(it) } }
                r.onFailure { serverOk = false }
            }
        }
        if (onPhone && tick % 100 == 0) lk.health { ok -> serverOk = ok }
    }

    /** 기록 종류별 폰 안내(폰 화면과 같은 뜻). */
    fun phoneGuide(kind: String): String = when (kind) {
        "noise" -> "폰을 소리 나는 곳 가까이 가져가 녹음하세요"
        "vibration" -> "폰을 떨리는 곳에 올려 두세요"
        "warning_photo" -> "폰 카메라로 계기판 경고등을 찍으세요"
        else -> "폰으로 사진을 찍으세요"
    }

    /**
     * 폰에 기록을 부탁합니다. [recordId]를 주면 그 기록에 붙습니다. [text]는 폰 요청 카드에 보이는 한 줄이고,
     * [usePart]가 false면 홈에서 고른 부위를 붙이지 않습니다(사고 현장 사진).
     */
    fun requestPhone(kind: String, recordId: String? = null, partHint: String? = null, text: String? = null, usePart: Boolean = true) {
        val lk = link
        if (lk == null || pairedPhone == null) {
            if (screen == Screen.ACCIDENT) {
                // 사고 안내 중에는 폰 연결 화면으로 넘기지 않습니다(뒤로 가면 홈이라 안내를 잃습니다).
                showToast("폰이 연결돼 있지 않습니다 · 폰 카메라로 직접 찍어 두세요")
                voice?.speak("폰이 연결돼 있지 않습니다. 폰 카메라로 직접 찍어 두세요.")
                return
            }
            if (screen == Screen.GUIDE) returnToGuide = true
            showToast("폰을 먼저 연결하세요"); openPhone(); return
        }
        lk.request(carId, kind, recordId, if (usePart) partHint ?: selectedPart else partHint, text) { r ->
            r.onSuccess { id ->
                phoneWait = PhoneWait(kind, recordId, id.ifBlank { null }, wallNow())
                val guide = phoneGuide(kind)
                showToast("폰에 보냈습니다 · $guide")
                if (!driving) voice?.speak(guide)
            }
            r.onFailure { showToast("서버에 연결하지 못했습니다") }
        }
    }

    fun cancelPhoneWait() { phoneWait = null }

    /** 폰에서 온 증거 하나를 기록에 붙이거나 새 기록으로 만듭니다. */
    private fun receive(inc: CareLink.Incoming) {
        val e0 = inc.evidence
        captureSince = maxOf(captureSince, inc.receivedAt)
        val at = now() - (wallNow() - e0.wall).coerceAtLeast(0L) + e0.durationMs / 2
        val kmhAt = history.minByOrNull { kotlin.math.abs(it.t - at) }?.takeIf { kotlin.math.abs(it.t - at) <= 1_000 }?.speed?.let { (it * 3.6f).toDouble() }
        val e = if (kmhAt != null && "carKmh" !in e0.metrics) e0.copy(metrics = e0.metrics + ("carKmh" to kmhAt)) else e0
        if (inbox.any { it.id == e.id }) return
        inbox = (listOf(e) + inbox).take(12)
        val wait = phoneWait
        val matched = wait != null && (wait.requestId == null || wait.requestId == inc.requestId || wait.kind == e.kind)
        val targetId = inc.recordId ?: if (matched) wait?.recordId else null
        val target = book.byId(targetId)
        if (target != null) {
            updateRecord(target.id) { it.copy(evidence = it.evidence + e) }
            lastRecord = book.byId(target.id)
            // 사고 현장 사진이 사고 기록에 붙으면 "사고 현장 촬영" 카드를 한 일로 표시합니다(더 찍을 수는 있음).
            if (target.id == accRecordId && e.kind == "photo") accDone = accDone + "photo"
        } else {
            // 새 기록: 폰 기록 시작 시각 앞뒤의 차 신호를 붙이려고 t0를 그 시각으로 맞춥니다(2분 보관 안이면 10초 뒤 시간표가 굳습니다).
            val age = (wallNow() - e.wall).coerceAtLeast(0L)
            val inHistory = age < 100_000L
            val r = Record(t0 = now() - age, wall = e.wall, drivingAtRecord = driving, symptom = CareData.symptomOf(e.kind), part = e.partHint ?: selectedPart,
                memo = e.note?.takeIf { it != "시험 입력" }, cabin = cabinNow(), frozen = !inHistory, evidence = listOf(e))
            book.add(r); lastRecord = r; bookVersion++
        }
        if (matched) phoneWait = null
        showToast("폰 기록이 도착했습니다 · ${e.summary}")
        if (e.kind == "warning_photo" && e.candidates.isNotEmpty() && matched && !driving) {
            val id = e.candidates.first()
            val entry = app.pleosguard.demo.logic.WarningCatalog.byId(id) ?: app.pleosguard.demo.logic.WarningCatalog.ENTRIES.firstOrNull { it.name == id }
            if (entry != null) openWarnDetail(entry.id)
        }
        careChanged()
    }

    /** 시연 패널: 서버 없이 폰 증거 한 건을 넣어 봅니다(소리 · 진동). 화면에 [시험 입력]을 붙이려고 note에 표시합니다. */
    fun injectTestEvidence(kind: String) {
        val spectrum = (0 until 64).map { i -> val f = i * 4000f / 64; (-60f + 34f * kotlin.math.exp(-((f - 180f) / 90f).let { it * it }) + 12f * kotlin.math.exp(-((f - 1200f) / 300f).let { it * it })) }
        val series = (0 until 120).map { i -> (0.08f + 0.30f * kotlin.math.abs(kotlin.math.sin(i * 0.5f)) * (if (i in 40..80) 1f else 0.35f)) }
        val m = if (kind == "noise") mapOf("peakDb" to -14.0, "avgDb" to -27.0, "dominantHz" to 180.0, "spectrumMaxHz" to 4000.0)
                else mapOf("rmsG" to 0.12, "peakG" to 0.38, "dominantHz" to 14.0, "spectrumMaxHz" to 50.0)
        val e = Evidence(java.util.UUID.randomUUID().toString(), kind, wallNow() - 5_000, 8_000, if (kind == "noise") "소리 가까이" else "바닥에 둠", selectedPart,
            CareData.summarize(kind, m, emptyList()), m, spectrum, series, note = "시험 입력")
        receive(CareLink.Incoming(e, phoneWait?.requestId, phoneWait?.recordId, wallNow()))
    }

    // ---------------------------------------------------------------- 시연 패널
    fun cycleDrive() {
        driveSource = when (driveSource) { DriveSource.REAL -> DriveSource.TEST_DRIVING; DriveSource.TEST_DRIVING -> DriveSource.TEST_PARKED; DriveSource.TEST_PARKED -> DriveSource.REAL }
    }

    fun toggleTestSpeed() { testSpeedKmh = if (testSpeedKmh == null) 60f else null }

    companion object {
        val EMERGENCY = setOf(Screen.COUNTDOWN, Screen.RECIPIENTS, Screen.ACCIDENT)
        /** 대처 방법에서 잠깐 다녀오는 화면. 뒤로 가면 대처 방법으로 돌아갑니다. */
        val PARKED_ONLY = setOf(Screen.RECALL, Screen.SUBSCRIBE, Screen.REVIEW)
        val GUIDE_SIDE_TRIPS = setOf(Screen.WARN_FIND, Screen.WARN_RESULT, Screen.WARN_DETAIL, Screen.WARN_SEARCH, Screen.PHONE)
        /** 사고 현장 사진 요청 때 폰 요청 카드에 보이는 한 줄. */
        const val ACC_PHOTO_TEXT = "사고 현장 사진 · 안전한 곳에서 찍으세요"
        /** 예시 정비소 좌표(기준 위치에서 떨어진 정도). 지도 대신 내비 도착 예상만 씁니다. */
        val SHOP_OFFSETS = listOf(0.030 to 0.012, -0.046 to 0.031, 0.062 to -0.052, -0.021 to -0.040, 0.044 to 0.055)
        /** 모의 보호자 1 열람 시각: 대표 사례(21:00:13 보냄 → 21:01 열람)에 맞춰 보낸 뒤 47초. */
        const val G1_OPEN_MS = 47_000L
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        private val HMS = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(SEOUL)
        private val HM = DateTimeFormatter.ofPattern("HH:mm").withZone(SEOUL)
        fun hms(wall: Long): String = HMS.format(Instant.ofEpochMilli(wall))
        private val MD = DateTimeFormatter.ofPattern("M월 d일").withZone(SEOUL)
        private val DT = DateTimeFormatter.ofPattern("M월 d일 HH:mm").withZone(SEOUL)
        fun monthDay(wall: Long): String = MD.format(Instant.ofEpochMilli(wall))
        fun dateTime(wall: Long): String = DT.format(Instant.ofEpochMilli(wall))
        fun hm(wall: Long): String = HM.format(Instant.ofEpochMilli(wall))
        private val YMDHM = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(SEOUL)
        /** 사고 발생 시간 표시(연도-월-일 시:분, Asia/Seoul). */
        fun ymdhm(wall: Long): String = YMDHM.format(Instant.ofEpochMilli(wall))
        /** 전화번호를 음성으로 읽기 좋게: "1588-5114" → "1 5 8 8, 5 1 1 4". */
        fun spokenPhone(phone: String): String = phone.split("-").joinToString(", ") { part -> part.toList().joinToString(" ") }
    }
}
