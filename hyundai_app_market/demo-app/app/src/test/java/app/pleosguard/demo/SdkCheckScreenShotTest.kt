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

/**
 * 에뮬레이터 없이 실제 앱 화면을 PNG로 그리는 시험입니다(./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true).
 * 창 크기는 로컬 Pleos 에뮬레이터 2.1.0의 세로 한 창(841×1210dp, 160dpi)입니다. SDK 값은 없으므로 시험 입력 상태로 그립니다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w841dp-h1210dp-mdpi")
class SdkCheckScreenShotTest {
    @get:Rule val rule = createComposeRule()

    private var clock = 100_000L
    private var wall = 1_794_571_203_000L // 2026-11-13 21:00:03 KST (대표 사례 시각)

    private fun controller() = DemoController(null, null, now = { clock }, wallClock = { wall })

    private fun shot(name: String, c: DemoController) {
        rule.setContent { PleosGuardApp(c, null, null) }
        rule.onRoot().captureRoboImage("build/shots/$name.png")
    }

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

    @Test fun homeParked() = shot("01-home-parked", controller().apply { driveSource = DriveSource.TEST_PARKED })

    @Test fun homeDriving() = shot("02-home-driving", controller().apply { driveSource = DriveSource.TEST_DRIVING; testSpeedKmh = 60f })

    @Test fun warningFind() = shot("03-warning-find", controller().apply {
        driveSource = DriveSource.TEST_PARKED; warnColor = "주황"; warnShape = "타이어 공기압"; warnMode = "계속 켜져 있어요"; screen = Screen.WARN_FIND
    })

    @Test fun warningResult() = shot("04-warning-result", controller().apply {
        driveSource = DriveSource.TEST_PARKED; warnColor = "주황"; warnShape = "타이어 공기압"; warnMode = "계속 켜져 있어요"; screen = Screen.WARN_RESULT
    })

    @Test fun record() {
        val c = controller().apply { driveSource = DriveSource.TEST_PARKED; testSpeedKmh = 14f }
        // 시간표용 표본 30초(100ms 간격): 기록 2초 전부터 브레이크
        for (i in 0 until 300) {
            val t = clock - 20_000 + i * 100L
            c.history.addLast(Sample(t, (20f - i * 0.02f) / 3.6f, app.pleosguard.demo.logic.Quality.TEST_INPUT, if (t in (clock - 2_000)..(clock + 3_000)) true else false, false, 2f, null))
        }
        c.lastRecord = RecordSnap(clock, wall - 45_000_000, false, memo = "앞 타이어 쪽 끼익 소리")
        c.screen = Screen.RECORD
        shot("05-record", c)
    }

    @Test fun situation() = shot("06-situation", controller().apply { driveSource = DriveSource.TEST_DRIVING; screen = Screen.SITUATION })

    @Test fun confirm() = shot("07-confirm", controller().apply {
        driveSource = DriveSource.TEST_DRIVING; promptReason = "브레이크를 밟은 1초 동안\n속도가 줄지 않았습니다"; promptSpeedTest = true; screen = Screen.CONFIRM
    })

    @Test fun decelGuide() = shot("08-decel-guide", controller().apply { driveSource = DriveSource.TEST_DRIVING; screen = Screen.DECEL_GUIDE; sceneIndex = 0; guideVoiceStep = 0 })

    @Test fun decelGuideScene3() = shot("09-decel-guide-scene3", controller().apply { driveSource = DriveSource.TEST_DRIVING; screen = Screen.DECEL_GUIDE; sceneIndex = 2; guideVoiceStep = 2 })

    @Test fun countdown() = shot("10-countdown", controller().apply {
        driveSource = DriveSource.TEST_DRIVING
        incident = Incident(IncidentKind.AIRBAG, wall, testInput = true, countdownFrom = clock)
        screen = Screen.COUNTDOWN
    })

    @Test fun guardian() {
        val c = controller().apply {
            driveSource = DriveSource.TEST_PARKED
            incident = Incident(IncidentKind.AIRBAG, wall, testInput = true, countdownFrom = clock, sentWall = wall + 10_000)
            screen = Screen.GUARDIAN
        }
        wall += 70_000 // 보낸 뒤 1분: 보호자 1 열람, 보호자 2 문자 재발송
        shot("11-guardian", c)
    }
}
