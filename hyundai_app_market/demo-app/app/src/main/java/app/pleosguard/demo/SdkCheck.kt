package app.pleosguard.demo

import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import java.util.Locale

/** 점검 한 줄. 상태는 대기·성공·실패 셋입니다. */
data class CheckItem(val key: String, val title: String, val state: State = State.WAITING, val detail: String = "") {
    enum class State { WAITING, OK, FAIL }
}

/**
 * 로컬 Pleos 에뮬레이터에서 공개 SDK가 실제로 붙는지 먼저 확인하는 점검기입니다(데모 앱 뼈대).
 * - SDK 콜백은 메인 스레드가 아니므로 화면 갱신 전에 메인 스레드로 넘깁니다.
 * - 서비스 연결 전 호출은 시간 제한 없이 기다리므로 앱이 5초 제한을 둡니다.
 * - 값이 없다는 사실(null)을 false나 0으로 바꾸지 않습니다.
 */
class SdkCheck(private val context: Context) {
    val items = mutableStateListOf(
        CheckItem("vehicle", "Vehicle SDK 연결 · 브레이크 값 읽기"),
        CheckItem("brake_sub", "브레이크 변화 구독(Virtual Console에서 바꿔 보기)"),
        CheckItem("speed", "AAOS 차속 PERF_VEHICLE_SPEED(CAR_SPEED 허락 필요)"),
        CheckItem("tts", "TextToSpeech 기기 안 모드 한국어 합성"),
    )
    private val main = Handler(Looper.getMainLooper())
    private var vehicle: Vehicle? = null
    private var tts: TextToSpeech? = null
    private var car: Car? = null
    private var speedCallback: CarPropertyManager.CarPropertyEventCallback? = null
    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) =
            set("brake_sub", CheckItem.State.OK, "받은 값: ${status ?: "확인 불가(null)"}")
        override fun onFailed(e: Exception) =
            set("brake_sub", CheckItem.State.FAIL, "${e.javaClass.simpleName}: ${e.message}")
    }

    private fun set(key: String, state: CheckItem.State, detail: String) {
        main.post {
            val i = items.indexOfFirst { it.key == key }
            if (i >= 0) items[i] = items[i].copy(state = state, detail = detail)
        }
    }

    private fun timeout(key: String, ms: Long = 5_000) { main.postDelayed({
        val i = items.indexOfFirst { it.key == key }
        if (i >= 0 && items[i].state == CheckItem.State.WAITING) items[i] = items[i].copy(state = CheckItem.State.FAIL, detail = "${ms}ms 안에 응답 없음")
    }, ms) }

    fun runVehicle() {
        try {
            val v = Vehicle(context).also { vehicle = it }
            v.initialize()
            timeout("vehicle")
            v.getBrake().getCurrentBrakePedalStatus(
                { status -> set("vehicle", CheckItem.State.OK, "브레이크: ${status ?: "확인 불가(null)"}") },
                { e -> set("vehicle", CheckItem.State.FAIL, "${e.javaClass.simpleName}: ${e.message}") },
            )
            try {
                v.getBrake().registerBrakePedalStatus(brakeListener)
                set("brake_sub", CheckItem.State.WAITING, "구독함. Virtual Console에서 브레이크를 바꿔 보세요")
            } catch (e: SecurityException) {
                set("brake_sub", CheckItem.State.FAIL, "권한 없음: ${e.message}")
            }
        } catch (e: Exception) {
            set("vehicle", CheckItem.State.FAIL, "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    /** CAR_SPEED는 dangerous 권한입니다. 허락을 받은 뒤 부릅니다. */
    fun runSpeed() {
        try {
            val c = Car.createCar(context).also { car = it }
            val cpm = c.getCarManager(Car.PROPERTY_SERVICE) as CarPropertyManager
            val cb = object : CarPropertyManager.CarPropertyEventCallback {
                override fun onChangeEvent(value: CarPropertyValue<*>) {
                    val mps = (value.value as? Float)
                    set("speed", CheckItem.State.OK, if (mps == null) "값 없음" else "%.1f m/s (%.0f km/h)".format(mps, mps * 3.6f))
                }
                override fun onErrorEvent(propertyId: Int, zone: Int) = set("speed", CheckItem.State.FAIL, "속성 오류")
            }
            speedCallback = cb
            val ok = cpm.registerCallback(cb, VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_NORMAL)
            if (!ok) set("speed", CheckItem.State.FAIL, "구독 실패") else timeout("speed")
        } catch (e: Exception) {
            set("speed", CheckItem.State.FAIL, "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private val ttsEvents = object : EventListener {
        override fun onStart() = set("tts", CheckItem.State.WAITING, "재생 시작")
        override fun onReady() {}
        override fun onDone() = set("tts", CheckItem.State.OK, "재생 끝")
        override fun onError(type: ErrorType) = set("tts", CheckItem.State.FAIL, "재생 오류: $type")
        override fun onStop() {}
        override fun onUpdatedRms(rms: Double) {}
    }
    private var ttsReady = false

    /**
     * SDK 문서 순서: initialize 성공 → EventListener 등록 → 언어 설정 → speak.
     * 2026-11-13 로컬 에뮬레이터에서 initialize 전에 addEventListener를 부르면
     * "lateinit property tts has not been initialized"로 실패하는 것을 확인했습니다.
     */
    fun runTts(text: String = "플레오스 가드 음성 안내 시험입니다.") {
        try {
            val existing = tts
            timeout("tts", 10_000)
            if (existing != null && ttsReady) { existing.speak(text, true); return }
            val t = TextToSpeech(context, Mode.ON_DEVICE).also { tts = it }
            t.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    try {
                        t.addEventListener(ttsEvents)
                        t.setLocale(Locale.KOREA)
                        ttsReady = true
                        t.speak(text, true)
                    } catch (e: Exception) { set("tts", CheckItem.State.FAIL, "${e.javaClass.simpleName}: ${e.message}") }
                }
                override fun onFailure(type: ErrorType) = set("tts", CheckItem.State.FAIL, "초기화 실패: $type")
            })
        } catch (e: Exception) {
            set("tts", CheckItem.State.FAIL, "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun release() {
        runCatching { vehicle?.getBrake()?.unregisterBrakePedalStatus(brakeListener) }
        runCatching { vehicle?.release() }
        runCatching { tts?.removeEventListener(ttsEvents); tts?.release() }
        runCatching {
            val cb = speedCallback
            val cpm = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
            if (cb != null && cpm != null) cpm.unregisterCallback(cb)
            car?.disconnect()
        }
    }
}
