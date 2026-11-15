package app.pleosguard.demo.sdk

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.OilLevelState
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * 실제 차량 신호. 값과 함께 마지막으로 받은 시각(elapsedRealtime)을 둡니다.
 * - Vehicle SDK 값에는 측정 시각이 없습니다. 바뀔 때만 오는 신호는 마지막 수신 시각과 연결 상태로 봅니다.
 * - SDK 콜백은 메인 스레드가 아니므로 메인 스레드로 넘깁니다.
 * - 값이 없음(null)을 false·0으로 바꾸지 않습니다.
 */
class VehicleSignals(private val context: Context, private val onChange: () -> Unit) {
    var brake: Boolean? = null; private set
    var brakeAt: Long? = null; private set
    var steeringDeg: Float? = null; private set
    var turn: TurnSignalValue? = null; private set
    var speedMps: Float? = null; private set
    var speedAt: Long? = null; private set
    /** CarUxRestrictions.isRequiresDistractionOptimization. null = 알 수 없음(주행 중으로 봅니다). */
    var restricted: Boolean? = null; private set
    var activeRestrictions: Int? = null; private set
    var vehicleError: String? = null; private set
    var speedError: String? = null; private set
    var uxError: String? = null; private set

    // 느리게 바뀌는 신호: 2초마다 get으로 다시 읽습니다(refreshSlow). 값이 없으면 null 그대로 둡니다.
    /** 문 위치(0 = 닫힘). 운전석·조수석·뒷좌석 왼쪽·오른쪽 순서. */
    var doors: List<Int?> = listOf(null, null, null, null); private set
    /** 트렁크 위치(0 = 닫힘). Vehicle SDK DoorArea(DoorValue.REAR). 짐 알림에 씁니다(2026-11-14). */
    var trunk: Int? = null; private set
    /** 좌석 점유(true = 탑승, SDK 값을 뒤집은 것, refreshSlow 설명 참고). 운전석·조수석·뒷좌석 왼쪽·오른쪽 순서. */
    var seats: List<Boolean?> = listOf(null, null, null, null); private set
    /** 안전벨트 체결(true = 맴). 좌석과 같은 순서. */
    var belts: List<Boolean?> = listOf(null, null, null, null); private set
    var acOn: Boolean? = null; private set
    var fanSpeed: Int? = null; private set
    /** 창문 위치(0 = 닫힘). 운전석·조수석·뒷좌석 왼쪽·오른쪽 순서. */
    var windows: List<Int?> = listOf(null, null, null, null); private set
    var wiper: WiperState? = null; private set
    var odometerKm: Float? = null; private set
    var engineOil: OilLevelState? = null; private set
    var brakeFluid: OilLevelState? = null; private set
    var washerLow: Boolean? = null; private set
    var powerDown: Boolean? = null; private set

    private var hvacArea: SeatArea? = null
    private var hvacFanArea: SeatArea? = null
    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var car: Car? = null
    private var speedCb: CarPropertyManager.CarPropertyEventCallback? = null
    private var uxManager: CarUxRestrictionsManager? = null

    private fun post(block: () -> Unit) { main.post { block(); onChange() } }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) = post { brake = status; brakeAt = SystemClock.elapsedRealtime() }
        override fun onFailed(e: Exception) = post { vehicleError = "브레이크 구독 실패: ${e.javaClass.simpleName}" }
    }
    private val steeringListener = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) = post { steeringDeg = angle }
        override fun onFailed(e: Exception) = post { Log.w(TAG, "steering", e) }
    }
    private val turnListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(value: TurnSignalValue?) = post { turn = value }
        override fun onFailed(e: Exception) = post { Log.w(TAG, "turn", e) }
    }

    fun start() {
        startVehicle()
        startCar()
    }

    private fun startVehicle() {
        try {
            val v = Vehicle(context).also { vehicle = it }
            v.initialize()
            v.getBrake().getCurrentBrakePedalStatus({ s -> post { brake = s; brakeAt = SystemClock.elapsedRealtime() } }, { e -> post { vehicleError = "브레이크 읽기 실패: ${e.javaClass.simpleName}" } })
            v.getBrake().registerBrakePedalStatus(brakeListener)
            runCatching {
                v.getSteeringWheel().getCurrentSteeringWheelAngle({ a -> post { steeringDeg = a } }, { e -> Log.w(TAG, "steering get", e) })
                v.getSteeringWheel().registerSteeringWheelAngle(steeringListener)
            }.onFailure { Log.w(TAG, "steering", it) }
            runCatching {
                v.getTurnSignal().getCurrentTurnSignalState({ t -> post { turn = t } }, { e -> Log.w(TAG, "turn get", e) })
                v.getTurnSignal().registerTurnSignalState(turnListener)
            }.onFailure { Log.w(TAG, "turn", it) }
        } catch (e: SecurityException) {
            vehicleError = "권한 없음: ${e.message}"
        } catch (e: Throwable) {
            vehicleError = "Vehicle SDK 실패: ${e.javaClass.simpleName}"
        }
    }

    private fun startCar() {
        try {
            val c = Car.createCar(context).also { car = it }
            val ux = c.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            uxManager = ux
            applyUx(ux.currentCarUxRestrictions)
            ux.registerListener { r -> applyUx(r); onChange() }
        } catch (e: Throwable) {
            uxError = "주행 제한 상태 확인 불가: ${e.javaClass.simpleName}"
        }
    }

    private fun applyUx(r: CarUxRestrictions?) {
        restricted = r?.isRequiresDistractionOptimization
        activeRestrictions = r?.activeRestrictions
    }

    /**
     * 문·좌석·공조·창문·와이퍼·주행거리·점검 항목을 한 번 읽습니다. 컨트롤러가 2초마다 부릅니다.
     * 기능 미지원·권한 없음은 null로 남기고 화면에서 [시험 입력]이나 "확인 불가"로 보입니다.
     */
    fun refreshSlow() {
        val v = vehicle ?: return
        fun <T> safe(block: () -> Unit) = runCatching(block).onFailure { Log.w(TAG, "slow", it) }
        val fourDoors = listOf(DoorValue.ROW_1_LEFT, DoorValue.ROW_1_RIGHT, DoorValue.ROW_2_LEFT, DoorValue.ROW_2_RIGHT)
        fourDoors.forEachIndexed { i, d ->
            safe<Unit> { v.getDoor().getCurrentDoorPosition(DoorArea(d), { p -> post { doors = doors.toMutableList().also { it[i] = p } } }, { e -> Log.w(TAG, "slow get failed: " + e.javaClass.simpleName + " " + e.message) }) }
        }
        // 트렁크: Door API의 REAR 영역(Vehicle 2.3.2 바이트코드 확인). Virtual Console의 Trunk Position과 짝입니다.
        safe<Unit> { v.getDoor().getCurrentDoorPosition(DoorArea(DoorValue.REAR), { p -> post { trunk = p } }, { e -> Log.w(TAG, "trunk get failed: " + e.javaClass.simpleName + " " + e.message) }) }
        val fourSeats = listOf(SeatValue.ROW_1_LEFT, SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT)
        fourSeats.forEachIndexed { i, s ->
            // getCurrentSeatOccupancy는 서비스 값이 1일 때만 true입니다(SDK 노트). AAOS VehicleSeatOccupancyState에서 1은 VACANT(빈 좌석)이고,
            // 에뮬레이터 Virtual Console에서 Occupied → false, Empty → true를 확인했습니다(2026-11-13). 그래서 뒤집어 "탑승"으로 씁니다.
            // 한계: false가 OCCUPIED(2)인지 UNKNOWN(0)인지 구별할 수 없습니다. 실차에서 다시 확인해야 합니다.
            safe<Unit> { v.getSeat().getCurrentSeatOccupancy(SeatArea(s), { o -> post { seats = seats.toMutableList().also { it[i] = o?.let { vacant -> !vacant } } } }, { e -> Log.w(TAG, "slow get failed: " + e.javaClass.simpleName + " " + e.message) }) }
            safe<Unit> { v.getSeat().getCurrentSeatBeltBuckledState(SeatArea(s), { b -> post { belts = belts.toMutableList().also { it[i] = b } } }, { e -> Log.w(TAG, "slow get failed: belt " + e.javaClass.simpleName + " " + e.message) }) }
        }
        val fourWindows = listOf(WindowValue.ROW_1_LEFT, WindowValue.ROW_1_RIGHT, WindowValue.ROW_2_LEFT, WindowValue.ROW_2_RIGHT)
        fourWindows.forEachIndexed { i, w ->
            safe<Unit> { v.getWindow().getCurrentWindowPosition(WindowArea(w), { p -> post { windows = windows.toMutableList().also { it[i] = p } } }, { e -> Log.w(TAG, "slow get failed: " + e.javaClass.simpleName + " " + e.message) }) }
        }
        // 공조 영역은 차마다 다릅니다(에뮬레이터는 운전석 단독 0x1을 지원하지 않음). 지원 영역을 한 번 물어 운전석이 든 영역을 씁니다.
        val area = hvacArea
        if (area == null) {
            safe<Unit> { v.getHVAC().checkHvacAcCapabilities({ areas: List<SeatArea> -> post { hvacArea = areas.firstOrNull { SeatValue.ROW_1_LEFT in it.values } ?: areas.firstOrNull() } },
                { e -> Log.w(TAG, "hvac capability: " + e.message) }) }
        } else {
            safe<Unit> { v.getHVAC().getHvacAcState(area, { a -> post { acOn = a } }, { e -> Log.w(TAG, "slow get failed: " + e.message) }) }
            val fanArea = hvacFanArea
            if (fanArea == null) safe<Unit> { v.getHVAC().checkHvacFanSpeedCapabilities({ bounds: Map<SeatArea, ai.pleos.playground.vehicle.api.model.capability.AreaBounds<Int>> -> post { hvacFanArea = bounds.keys.firstOrNull { SeatValue.ROW_1_LEFT in it.values } ?: bounds.keys.firstOrNull() } },
                { e -> Log.w(TAG, "fan capability: " + e.message) }) }
            else safe<Unit> { v.getHVAC().getHvacFanSpeed(fanArea, { f -> post { fanSpeed = f } }, { e -> Log.w(TAG, "slow get failed: " + e.message) }) }
        }
        safe<Unit> { v.getWiper().getCurrentWiperState(WindowArea(WindowValue.FRONT_WINDSHIELD), { s -> post { wiper = s } }, { e -> Log.w(TAG, "slow get failed: " + e.javaClass.simpleName + " " + e.message) }) }
        safe<Unit> { v.getOdometer().getCurrentDistanceDriven({ km -> post { odometerKm = km } }, { e -> Log.w(TAG, "slow get failed: " + e.javaClass.simpleName + " " + e.message) }) }
        safe<Unit> { v.getSafety().getEngineOilLevelState({ s -> post { engineOil = s } }, { e -> Log.w(TAG, "slow get failed: " + e.javaClass.simpleName + " " + e.message) }) }
        safe<Unit> { v.getSafety().getBrakeFluidLevelState({ s -> post { brakeFluid = s } }, { e -> Log.w(TAG, "slow get failed: " + e.javaClass.simpleName + " " + e.message) }) }
        safe<Unit> { v.getSafety().hasLowWasherFluidWarning({ b -> post { washerLow = b } }, { e -> Log.w(TAG, "slow get failed: " + e.javaClass.simpleName + " " + e.message) }) }
        safe<Unit> { v.getSafety().hasPowerDownWarningIndicator({ b -> post { powerDown = b } }, { e -> Log.w(TAG, "slow get failed: " + e.javaClass.simpleName + " " + e.message) }) }
    }

    /** CAR_SPEED(dangerous) 허락 뒤에 부릅니다. */
    fun startSpeed() {
        if (speedCb != null) return
        try {
            val c = car ?: Car.createCar(context).also { car = it }
            val cpm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            val cb = object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) = post {
                    speedMps = value.value as? Float; speedAt = SystemClock.elapsedRealtime()
                }
                override fun onErrorEvent(propertyId: Int, zone: Int) = post { speedError = "차속 속성 오류" }
            }
            speedCb = cb
            if (!cpm.registerCallback(cb, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)) speedError = "차속 구독 실패"
        } catch (e: SecurityException) {
            speedError = "CAR_SPEED 권한 없음"
        } catch (e: Throwable) {
            speedError = "차속 확인 불가: ${e.javaClass.simpleName}"
        }
        onChange()
    }

    fun stop() {
        runCatching { vehicle?.getBrake()?.unregisterBrakePedalStatus(brakeListener) }
        runCatching { vehicle?.getSteeringWheel()?.unregisterSteeringWheelAngle(steeringListener) }
        runCatching { vehicle?.getTurnSignal()?.unregisterTurnSignalState(turnListener) }
        runCatching { vehicle?.release() }
        runCatching { uxManager?.unregisterListener() }
        runCatching {
            val cb = speedCb; val cpm = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            if (cb != null && cpm != null) cpm.unregisterCallback(cb)
        }
        runCatching { car?.disconnect() }
        vehicle = null; car = null; speedCb = null; uxManager = null
    }

    companion object { const val TAG = "PleosGuardSignals" }
}
