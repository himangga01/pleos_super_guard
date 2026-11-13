package app.pleosguard.demo.sdk

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
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
