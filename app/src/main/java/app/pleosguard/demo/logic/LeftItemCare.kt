package app.pleosguard.demo.logic

/**
 * 짐 알림(트렁크 · 뒷좌석). 2026-11-14 사용자 요청: "트렁크에 짐을 넣어 두고 까먹고 내리는 경우가 많다".
 * 차 신호만 보는 순수 논리입니다(JVM 시험: LeftItemCareTest). 신호는 Vehicle SDK 문 위치(트렁크 = DoorValue.REAR)와 주행 상태입니다.
 * 규칙은 조사 보고서(docs/analysis/care-pivot-research-2026-11-14.md 7.3 · 7.4절, 제조사의 "문 열림 기록" 방식)를 따릅니다.
 *
 * 규칙
 * 1. 정차 중 트렁크가 열리면 "짐을 실었다"고 기억합니다. 뒷문도 같습니다(뒷좌석 물건 · 사람).
 *    알림을 줄인 상태에서는 [reducedMinOpenMs] 넘게 열려 있던 경우만 셉니다.
 * 2. 짐을 실은 뒤 [loadWindowMs](10분) 안에 움직임이 [moveStartMs] 넘게 이어지면 "짐 실은 주행"입니다.
 * 3. 정차가 [arriveMs](5초) 넘게 이어지면 도착입니다. 도착 뒤 처음 트렁크를 열면 "내렸다"고 보고 알림 대상에서 뺍니다(카드는 조용히 닫힘).
 * 4. 알림 시점: 도착했고, 주행이 [longTripMs](3분) 넘었으면 도착하자마자 소리 없는 카드를 띄웁니다.
 *    짧은 주행이면 운전석 문이 열릴 때 띄웁니다. 운전석 문 값을 모르면 도착 [noDoorFallbackMs] 뒤에 띄웁니다.
 *    카드가 떠 있는 동안 운전석 문이 열리면 음성을 한 번 냅니다([takeSpeak]).
 * 5. 움직이는 동안에는 알리지 않습니다. 한 주행에 한 번만 알립니다.
 * 6. "짐 없어요"를 세 번 이어서 고르면 알림을 줄일지 묻습니다([askReduce]).
 *
 * 한계: 트렁크를 열었다고 짐을 실었는지는 알 수 없습니다(무게 · 카메라 신호 없음). 그래서 "남아 있지 않나요?"라고 묻기만 합니다.
 * 일반 앱은 헤드업 알림과 시동 꺼짐 신호를 받지 못하므로(조사 7.2절) 앱 화면 카드로 알립니다.
 */
class LeftItemCare(
    private val moveStartMs: Long = 5_000,
    private val arriveMs: Long = 5_000,
    private val loadWindowMs: Long = 10 * 60_000,
    private val longTripMs: Long = 3 * 60_000,
    private val noDoorFallbackMs: Long = 10_000,
    private val reducedMinOpenMs: Long = 20_000,
) {
    enum class Kind(val label: String) { TRUNK("트렁크"), REAR_SEAT("뒷좌석") }

    /** 알림 한 건. [loadedWall] = 짐을 실은(트렁크 · 뒷문을 연) 시각, [arrivedWall] = 도착 시각, [voice] = 띄울 때 음성을 낼지. */
    data class Alert(val kinds: Set<Kind>, val loadedWall: Long?, val arrivedWall: Long?, val test: Boolean = false, val voice: Boolean = true)

    data class Event(val wall: Long, val icon: String, val text: String)

    var enabled = true
    var rearEnabled = true
    /** 알림 줄이기(짧게 연 트렁크는 세지 않음). */
    var reduced = false

    var trunkLoadedWall: Long? = null; private set
    var rearLoadedWall: Long? = null; private set
    var tripActive = false; private set
    var arrivedWall: Long? = null; private set
    /** 이번 주행에 실린 짐(알림 대상). */
    var trunkCarried = false; private set
    var rearCarried = false; private set
    /** "짐 없어요"를 이어서 고른 횟수. */
    var noItemsStreak = 0; private set
    val askReduce: Boolean get() = noItemsStreak >= 3 && !reduced

    private var arrivedAt: Long? = null
    private var tripStartAt: Long? = null
    private var tripMs = 0L
    private var movingSince: Long? = null
    private var stillSince: Long? = null
    private var lastTrunkOpen: Boolean? = null
    private var lastDriverOpen: Boolean? = null
    private var lastRearOpen: List<Boolean?> = listOf(null, null)
    private var trunkOpenSince: Long? = null
    private var trunkActivityAt: Long? = null
    private var rearActivityAt: Long? = null
    private var alerted = false
    private var alertShowing = false
    private var speak = false
    private var driverDoorKnown = false

    /** 최근 기록(관리 화면 "짐 알림 기록"). 새것이 앞. */
    val events = ArrayDeque<Event>()

    private fun log(wall: Long, icon: String, text: String) {
        events.addFirst(Event(wall, icon, text))
        while (events.size > 8) events.removeLast()
    }

    /** 카드가 떠 있는 동안 운전석 문이 열렸으면 한 번 true(음성 한 번). */
    fun takeSpeak(): Boolean = speak.also { speak = false }

    private fun loadTrunk(t: Long, wall: Long) {
        trunkLoadedWall = wall; trunkActivityAt = t
        log(wall, "Trunk", "트렁크 열림 · 짐 실음으로 봄")
    }

    /**
     * 신호 한 번 반영. 0.5초쯤마다 부릅니다. 새로 알릴 일이 생긴 순간에만 [Alert]를 돌려줍니다.
     * [trunk] · [driverDoor] · [rearDoors]: 문 위치(0 = 닫힘, 0보다 크면 열림, null = 모름).
     */
    fun update(t: Long, wall: Long, moving: Boolean, trunk: Int?, driverDoor: Int?, rearDoors: List<Int?>): Alert? {
        val trunkOpen = trunk?.let { it > 0 }
        val driverOpen = driverDoor?.let { it > 0 }
        val rearOpen = rearDoors.map { d -> d?.let { it > 0 } }
        if (driverDoor != null) driverDoorKnown = true
        val trunkOpened = trunkOpen == true && lastTrunkOpen != true
        val trunkClosed = trunkOpen == false && lastTrunkOpen == true
        val driverOpened = driverOpen == true && lastDriverOpen != true
        val rearOpened = rearOpen.indices.any { i -> rearOpen[i] == true && lastRearOpen.getOrNull(i) != true }
        lastTrunkOpen = trunkOpen; lastDriverOpen = driverOpen; lastRearOpen = rearOpen

        // 움직임 · 도착
        if (moving) {
            stillSince = null
            val since = movingSince ?: t.also { movingSince = it }
            if (!tripActive && t - since >= moveStartMs) {
                tripActive = true; tripStartAt = since; arrivedAt = null; arrivedWall = null; alerted = false; alertShowing = false
                trunkCarried = trunkLoadedWall != null && (trunkOpen == true || since - (trunkActivityAt ?: since) <= loadWindowMs)
                rearCarried = rearLoadedWall != null && since - (rearActivityAt ?: since) <= loadWindowMs
                if (!trunkCarried) trunkLoadedWall = null
                if (!rearCarried) rearLoadedWall = null
                log(wall, "CarFront", "출발" + when {
                    trunkCarried && rearCarried -> " · 트렁크 · 뒷좌석 짐"
                    trunkCarried -> " · 트렁크 짐"
                    rearCarried -> " · 뒷좌석 짐"
                    else -> ""
                })
            }
        } else {
            movingSince = null
            val since = stillSince ?: t.also { stillSince = it }
            if (tripActive && t - since >= arriveMs) {
                tripActive = false; arrivedAt = t; arrivedWall = wall; tripMs = since - (tripStartAt ?: since)
                log(wall, "Park", "도착")
            }
        }

        // 트렁크: 정차 중 연 것만 셉니다(움직이는 중 값은 무시).
        if (!moving) {
            if (trunkOpened) {
                trunkOpenSince = t
                if (arrivedAt != null && trunkCarried) {
                    trunkCarried = false; trunkLoadedWall = null
                    log(wall, "Trunk", "트렁크 열림 · 짐 내림으로 봄")
                } else if (!reduced) loadTrunk(t, wall)
            }
            if (trunkClosed) {
                val opened = trunkOpenSince
                if (reduced && opened != null && t - opened >= reducedMinOpenMs && !(arrivedAt != null && trunkCarried) && trunkLoadedWall == null) loadTrunk(t, wall)
                if (trunkLoadedWall != null) trunkActivityAt = t
                trunkOpenSince = null
            }
            if (rearOpened) {
                if (arrivedAt != null && rearCarried) {
                    rearCarried = false; rearLoadedWall = null
                    log(wall, "Door", "뒷문 열림 · 뒷좌석 확인으로 봄")
                } else {
                    rearLoadedWall = wall; rearActivityAt = t
                    log(wall, "Door", "뒷문 열림 · 뒷좌석에 둔 것으로 봄")
                }
            }
        }

        // 카드가 떠 있는데 운전석 문이 열림: 음성 한 번
        if (alertShowing && driverOpened && !moving) speak = true

        val at = arrivedAt
        if (enabled && at != null && !moving && !alerted) {
            val kinds = buildSet {
                if (trunkCarried) add(Kind.TRUNK)
                if (rearEnabled && rearCarried) add(Kind.REAR_SEAT)
            }
            val longTrip = tripMs >= longTripMs
            val fallback = !driverDoorKnown && t - at >= noDoorFallbackMs
            if (kinds.isNotEmpty() && (longTrip || driverOpened || fallback)) {
                alerted = true; alertShowing = true
                log(wall, "Bell", kinds.joinToString(" · ") { it.label } + " 짐 알림")
                return Alert(kinds, if (Kind.TRUNK in kinds) trunkLoadedWall else rearLoadedWall, arrivedWall, voice = driverOpened || fallback)
            }
        }
        return null
    }

    /** 알림의 종류가 아직 유효한지(그사이 트렁크 · 뒷문을 열어 확인했으면 false). */
    fun stillNeeded(kind: Kind): Boolean = when (kind) { Kind.TRUNK -> trunkCarried; Kind.REAR_SEAT -> rearCarried }

    /** 카드가 닫힘(확인 · 짐 없음 · 트렁크를 열어 저절로). */
    fun closed() { alertShowing = false }

    /** 사용자가 알림에 답함("확인했어요" · "짐 없어요"). 이번 짐을 지웁니다. */
    fun dismiss(wall: Long, noItems: Boolean) {
        trunkCarried = false; rearCarried = false; trunkLoadedWall = null; rearLoadedWall = null; alertShowing = false
        noItemsStreak = if (noItems) noItemsStreak + 1 else 0
        log(wall, "CircleCheck", if (noItems) "짐 없음으로 닫음" else "확인함으로 닫음")
    }

    /** 알림 줄이기에 답함. */
    fun answerReduce(reduce: Boolean) { reduced = reduce; noItemsStreak = 0 }
}
