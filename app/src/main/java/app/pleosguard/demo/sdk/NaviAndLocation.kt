package app.pleosguard.demo.sdk

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.WaypointIndex
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.NavigationScreenRequest
import ai.pleos.playground.navi.data.RequestWaypointInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.TripRequest
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/** 정비소 후보. 좌표는 앱이 정한 예시 정비소입니다(실제 상호·주소 아님). */
data class Shop(val name: String, val lat: Double, val lng: Double, var distanceM: Int? = null, var etaS: Int? = null)

/**
 * NaviHelper 연결: 도착 예상 비교(requestTrip), 경유지 추가(addWaypoint), 옆 내비 화면 열기(requestNavigationScreen),
 * 현재 위치 주소(getCurrentLocationInfo). 앱은 지도를 그리지 않고 옆 내비에 맡깁니다(B11·H2).
 * requestTrip 결과에는 요청 구분 값이 없으므로 후보를 한 번에 하나씩 묻습니다(SDK 노트 4절).
 */
class Navi(private val context: Context, private val onChange: () -> Unit) {
    var state: String = "연결 전"; private set
    var lastError: String? = null; private set
    var address: String? = null; private set
    /** 내비가 알려 준 현재 위치(getCurrentLocationInfo). 측정 시각은 주지 않습니다. */
    var lat: Double? = null; private set
    var lng: Double? = null; private set
    var waypointMessage: String? = null; private set

    /** 내비 현재 위치. 값이 없거나 (0, 0)이면 null입니다. */
    fun latLng(): Pair<Double, Double>? {
        val a = lat ?: return null; val b = lng ?: return null
        return if (a == 0.0 && b == 0.0) null else a to b
    }
    private val main = Handler(Looper.getMainLooper())
    private var helper: NaviHelper? = null
    private var tripQueue = ArrayDeque<Shop>()
    private var tripTarget: Shop? = null

    private fun ui(block: () -> Unit) { main.post { block(); onChange() } }

    private val events = object : NaviHelperEventListener {
        override fun onTripInfo(info: TripInfo) = ui {
            tripTarget?.let { it.distanceM = info.distance; it.etaS = info.eta }
            tripTarget = null; nextTrip()
        }
        override fun onError(code: NaviErrorCode) = ui {
            lastError = code.toString()
            if (tripTarget != null) { tripTarget = null; nextTrip() }
            if (waypointMessage == "경유지 추가 요청함") waypointMessage = "경유지 추가 실패: $code"
        }
        override fun onCurrentLocationInfo(info: CurrentLocationInfo) = ui { address = info.address; lat = info.latitude; lng = info.longitude }
        override fun onWaypointChanged(info: WaypointChangedInfo) = ui { waypointMessage = "경유지로 추가했습니다" }
    }

    fun start() {
        try {
            val h = NaviHelper(context).also { helper = it }
            h.initialize({ ui { state = "연결됨"; runCatching { h.addListener(events); h.getCurrentLocationInfo() }.onFailure { lastError = it.javaClass.simpleName } } },
                { e -> ui { state = "연결 실패: ${e.javaClass.simpleName}" } })
        } catch (e: Throwable) { state = "NaviHelper 실패: ${e.javaClass.simpleName}" }
    }

    fun refreshAddress() { runCatching { helper?.getCurrentLocationInfo() } }

    /** 후보들의 거리·도착까지 시간을 하나씩 묻습니다. */
    fun requestTrips(shops: List<Shop>) {
        tripQueue = ArrayDeque(shops); tripTarget = null; nextTrip()
    }

    private fun nextTrip() {
        if (tripTarget != null) return
        val s = tripQueue.removeFirstOrNull() ?: return
        tripTarget = s
        try { helper?.requestTrip(TripRequest(s.lat, s.lng, "", s.name, "")) ?: run { tripTarget = null } }
        catch (e: Throwable) { lastError = "도착 예상 실패: ${e.javaClass.simpleName}"; tripTarget = null; main.post { nextTrip() } }
        // 결과가 오지 않으면 5초 뒤 다음 후보로 넘어갑니다.
        main.postDelayed({ if (tripTarget === s) { tripTarget = null; nextTrip(); onChange() } }, 5_000)
    }

    /** 지금 경로에 경유지로 넣습니다(목적지 유지). 안내 중이 아니면 내비가 오류를 돌려줍니다. */
    fun addWaypoint(s: Shop) {
        waypointMessage = "경유지 추가 요청함"
        try { helper?.addWaypoint(RequestWaypointInfo(s.lat, s.lng, s.name, WaypointIndex.FIRST, "", "", "", "")) }
        catch (e: Throwable) { waypointMessage = "경유지 추가 실패: ${e.javaClass.simpleName}" }
        onChange()
    }

    /** 옆 내비에서 장소 검색 화면을 엽니다(앱 안 지도 없음). */
    fun openSearch(keyword: String) {
        try { helper?.requestNavigationScreen(NavigationScreenRequest.SearchPlace(keyword)) }
        catch (e: Throwable) { lastError = "내비 화면 열기 실패: ${e.javaClass.simpleName}" }
        onChange()
    }

    fun release() { runCatching { helper?.removeListener(events); helper?.release() }; helper = null }

    companion object { const val TAG = "PleosGuardNavi" }
}

/** FusedLocation: 마지막 위치와 측정 시각. 오차 반경(m)은 SDK가 주지 않으므로 표시하지 않고 confidence 원값만 둡니다. */
class Location(private val context: Context, private val onChange: () -> Unit) {
    var last: LocationData? = null; private set
    /** 앱이 마지막 위치를 받은 시각(System.currentTimeMillis). 측정 시각(timestampMillis)의 기준 시계가 공개되지 않아 따로 둡니다. */
    var receivedWall: Long? = null; private set
    var state: String = "연결 전"; private set
    private val main = Handler(Looper.getMainLooper())
    private var fused: FusedLocation? = null
    /** 구독(registerFusedLocationCallback)으로 마지막 위치가 온 시각. 한 번 묻기(getLastKnownLocation)와 나눠 셉니다. */
    private var pushedWall = 0L
    private var subscribedWall = 0L

    private fun take(data: LocationData?, pushed: Boolean) {
        main.post {
            if (data != null) {
                val t = System.currentTimeMillis()
                last = data; receivedWall = t
                if (pushed) pushedWall = t
                if (pushed || pushedWall == 0L) state = if (pushed) "받는 중" else "받음(마지막 위치)"
            }
            onChange()
        }
    }
    private val listener = object : FusedLocationListener {
        override fun onChanged(data: LocationData?) { take(data, pushed = true) }
        override fun onError(e: Exception) { main.post { state = "오류: ${e.javaClass.simpleName}"; onChange() } }
    }
    private val once = object : FusedLocationListener {
        override fun onChanged(data: LocationData?) { take(data, pushed = false) }
        override fun onError(e: Exception) { main.post { state = "오류: ${e.javaClass.simpleName}"; onChange() } }
    }

    /** 마지막 위치를 한 번 더 묻습니다(사건이 생긴 순간). 권한이 없으면 SecurityException을 삼키고 상태에 남깁니다. */
    fun refresh() {
        val f = fused ?: return
        runCatching { f.getLastKnownLocation(once) }.onFailure { state = "실패: ${it.javaClass.simpleName}"; onChange() }
    }

    fun start() {
        if (fused != null) return
        try {
            val f = FusedLocation(context).also { fused = it }
            f.initialize()
            // initialize()에는 연결 결과 콜백이 없어(SDK 노트) 1초 뒤에 묻고 구독합니다.
            main.postDelayed({
                runCatching { f.getLastKnownLocation(once); f.registerFusedLocationCallback(listener); subscribedWall = System.currentTimeMillis() }
                    .onFailure { state = "실패: ${it.javaClass.simpleName}"; Log.w("PleosGuardLoc", "loc", it); onChange() }
            }, 1_000)
            // 구독 뒤 8초 동안 새 위치가 한 번도 오지 않으면 한 번 다시 구독합니다.
            // 앱을 다시 설치한 직후 위치 서비스가 GPS를 다시 켜지 않은 일이 에뮬레이터에서 있었습니다(2026-11-13).
            main.postDelayed({
                if (fused === f && pushedWall < subscribedWall) runCatching { f.unregisterFusedLocationCallback(listener); f.registerFusedLocationCallback(listener) }
                    .onSuccess { Log.i("PleosGuardLoc", "resubscribed: no location push in 8 s") }
            }, 9_000)
        } catch (e: Throwable) { state = "FusedLocation 실패: ${e.javaClass.simpleName}" }
    }

    fun release() { runCatching { fused?.unregisterFusedLocationCallback(listener); fused?.release() }; fused = null }
}
