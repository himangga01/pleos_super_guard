package app.pleossupercare.phone

import android.app.Application

/** 앱 상태([Controller])를 프로세스 하나에 하나만 둡니다. 화면이 돌아가도 기록 · 연결 상태가 유지됩니다. */
class PhoneApp : Application() {
    lateinit var controller: Controller
        private set

    override fun onCreate() {
        super.onCreate()
        controller = Controller(this)
        Notifier.ensureChannel(this)
        controller.startPolling()
    }
}
