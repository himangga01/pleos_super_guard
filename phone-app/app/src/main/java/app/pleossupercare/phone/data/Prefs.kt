package app.pleossupercare.phone.data

import android.content.Context
import android.os.Build

/** 폰 앱 설정: 서버 주소, 짝 맺은 차, 폰 이름. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("pleos_care_phone", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = sp.getString("serverUrl", null) ?: defaultServer()
        set(v) = sp.edit().putString("serverUrl", v.trim().trimEnd('/')).apply()

    /** 확인까지 끝난 차(차 화면에서 [연결]을 누름). */
    var carId: String?
        get() = sp.getString("carId", null)
        set(v) = sp.edit().putString("carId", v).apply()

    /** 번호를 넣었고 차 화면 확인을 기다리는 차. */
    var pendingCarId: String?
        get() = sp.getString("pendingCarId", null)
        set(v) = sp.edit().putString("pendingCarId", v).apply()

    var phoneId: String?
        get() = sp.getString("phoneId", null)
        set(v) = sp.edit().putString("phoneId", v).apply()

    var pairedAt: Long
        get() = sp.getLong("pairedAt", 0L)
        set(v) = sp.edit().putLong("pairedAt", v).apply()

    var phoneName: String
        get() = sp.getString("phoneName", null) ?: defaultPhoneName()
        set(v) = sp.edit().putString("phoneName", v).apply()

    /** 이미 알림을 띄운 알림 요청 id(같은 알림을 두 번 띄우지 않음). */
    var notified: Set<String>
        get() = sp.getStringSet("notified", emptySet()) ?: emptySet()
        set(v) = sp.edit().putStringSet("notified", v.toList().takeLast(100).toSet()).apply()

    fun clearPairing() {
        sp.edit().remove("carId").remove("pendingCarId").remove("phoneId").remove("pairedAt").apply()
    }

    companion object {
        /** 에뮬레이터면 PC를 가리키는 10.0.2.2, 실제 폰이면 adb reverse 뒤의 127.0.0.1. */
        fun defaultServer() = if (isEmulator()) "http://10.0.2.2:8766" else "http://127.0.0.1:8766"

        fun isEmulator(): Boolean =
            Build.FINGERPRINT.contains("generic") || Build.FINGERPRINT.contains("emulator") || Build.HARDWARE.contains("ranchu") ||
                Build.HARDWARE.contains("goldfish") || Build.PRODUCT.contains("sdk") || Build.MODEL.contains("Emulator")

        fun defaultPhoneName(): String {
            val model = Build.MODEL ?: "폰"
            return if (isEmulator()) "폰 에뮬레이터($model)" else model
        }
    }
}
