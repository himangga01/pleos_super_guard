package app.pleosguard.demo

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 에뮬레이터 없이 실제 앱 화면을 PNG로 그리는 시험입니다(./gradlew :app:recordRoborazziDebug). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w840dp-h1212dp-mdpi")
class SdkCheckScreenShotTest {
    @get:Rule val rule = createComposeRule()

    @Test fun sdkCheckScreen() {
        val items = listOf(
            CheckItem("vehicle", "Vehicle SDK 연결 · 브레이크 값 읽기", CheckItem.State.OK, "브레이크: false"),
            CheckItem("brake_sub", "브레이크 변화 구독(Virtual Console에서 바꿔 보기)", CheckItem.State.WAITING, "구독함"),
            CheckItem("speed", "AAOS 차속 PERF_VEHICLE_SPEED(CAR_SPEED 허락 필요)", CheckItem.State.FAIL, "SecurityException"),
            CheckItem("tts", "TextToSpeech 기기 안 모드 한국어 합성"),
        )
        rule.setContent { SdkCheckScreen(items, {}, {}, {}) }
        rule.onRoot().captureRoboImage("build/shots/sdk-check.png")
    }
}
