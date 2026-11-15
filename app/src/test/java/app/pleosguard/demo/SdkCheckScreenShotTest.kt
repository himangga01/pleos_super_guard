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
        c.lastRecord = RecordSnap(t0 = clock, wall = wall - 45_000_000, drivingAtRecord = false, symptom = "소음", memo = "앞 타이어 쪽 끼익 소리")
        c.screen = Screen.RECORD
        shot("05-record", c)
    }

    @Test fun situation() = shot("06-situation", controller().apply { driveSource = DriveSource.TEST_DRIVING; screen = Screen.SITUATION })

    /** 사건 순간 차 상태와 위치(시험 값): 운전석 1명 · 안전벨트 착용 · 문 닫힘, 사건 2초 전 측정 위치(판교, 에뮬레이터 내비 주소). */
    private fun snap() = IncidentSnap(seats = listOf(true, false, false, false), belts = listOf(true, false, false, false), doors = listOf(0, 0, 0, 0),
        locWall = wall - 2_000, locMeasured = true, lat = 37.4127, lng = 127.0947, confidence = 5f, address = "수정구 시흥동", naviLat = 37.4127, naviLng = 127.0947)

    /** 어긋난 경우(에뮬레이터에서 본 값): FusedLocation은 미국 기본 좌표, 내비는 판교. 조수석 탑승·벨트 안 맴, 조수석 문 열림. */
    private fun oddSnap() = snap().copy(seats = listOf(true, true, false, false), belts = listOf(true, false, false, false), doors = listOf(0, 1, 0, 0),
        locWall = wall - 89 * 60_000L, lat = 37.4220, lng = -122.0840)

    private fun countdownController() = controller().apply {
        driveSource = DriveSource.TEST_DRIVING
        incident = Incident(IncidentKind.AIRBAG, wall, testInput = true, countdownFrom = clock, snap = snap())
        screen = Screen.COUNTDOWN
    }

    private fun guardianController(): DemoController {
        val c = controller().apply {
            driveSource = DriveSource.TEST_PARKED
            incident = Incident(IncidentKind.AIRBAG, wall, testInput = true, countdownFrom = clock, sentWall = wall + 10_000, snap = snap())
            screen = Screen.GUARDIAN
        }
        wall += 70_000 // 보낸 뒤 1분: 보호자 1 열람, 보호자 2 문자 재발송
        return c
    }

    @Test fun countdown() = shot("10-countdown", countdownController())

    @Test fun guardian() = shot("11-guardian", guardianController())

    private fun oddGuardianController(): DemoController {
        val c = controller().apply {
            driveSource = DriveSource.TEST_PARKED
            incident = Incident(IncidentKind.ACCIDENT, wall, testInput = false, countdownFrom = clock, sentWall = wall + 10_000, snap = oddSnap())
            screen = Screen.GUARDIAN
        }
        wall += 20_000
        return c
    }

    @Test fun guardianOdd() = shot("20-guardian-mismatch", oddGuardianController())

    // ---------------------------------------------------------------- 경고등 도감(64개 조합): 대표 조합
    private fun warn(color: String, shape: String, mode: String, s: Screen = Screen.WARN_RESULT) = controller().apply {
        driveSource = DriveSource.TEST_PARKED; warnColor = color; warnShape = shape; warnMode = mode; screen = s
    }

    @Test fun warningResultBrake() = shot("04b-warning-result-brake", warn("빨강", "브레이크", app.pleosguard.demo.logic.WarningCatalog.STEADY))

    @Test fun warningResultMismatch() = shot("04c-warning-result-mismatch", warn("초록", "엔진 점검", app.pleosguard.demo.logic.WarningCatalog.STEADY))

    @Test fun warningDetail() = shot("18-warning-detail", warn("빨강", "브레이크", app.pleosguard.demo.logic.WarningCatalog.STEADY, Screen.WARN_DETAIL))

    @Test fun warningSearch() = shot("19-warning-search", controller().apply { driveSource = DriveSource.TEST_PARKED; warnQuery = "브레이크"; screen = Screen.WARN_SEARCH })

    /** 같은 증상 3번: 예시 기록 2건(11월 6일·9일) + 오늘 기록 1건. */
    private fun withRecords(c: DemoController) = c.apply {
        app.pleosguard.demo.logic.RecordBook.examples(wall).forEach { book.add(it) }
        book.add(RecordSnap(t0 = 0, wall = wall - 60_000, drivingAtRecord = true, symptom = "소음", part = "앞 타이어", memo = "끼익 소리 또",
            points = listOf(app.pleosguard.demo.logic.SnapPoint(0, 14, false, true, 1f, "OFF")), frozen = true,
            cabin = app.pleosguard.demo.logic.Cabin(false, 0, true, true, false)))
    }

    @Test fun manage() = shot("12-manage", withRecords(controller().apply { driveSource = DriveSource.TEST_PARKED; screen = Screen.MANAGE }))

    // ---------------------------------------------------------------- 케어 화면(2026-11-14 컨셉 전환)
    private fun careController() = controller().apply { driveSource = DriveSource.TEST_PARKED }

    /** 폰이 연결된 상태 + 폰에서 온 소리 · 진동 기록 2건(시험 입력). */
    private fun phoneController() = careController().apply {
        pairedPhone = "갤럭시 S24 (예시)"
        injectTestEvidence("noise"); injectTestEvidence("vibration")
    }

    private fun serviceController() = careController().apply {
        shops = listOf(app.pleosguard.demo.sdk.Shop("정비소 A (예시)", 37.44, 127.11, 4_000, 660), app.pleosguard.demo.sdk.Shop("정비소 B (예시)", 37.37, 127.13, 7_200, 1_080),
            app.pleosguard.demo.sdk.Shop("정비소 C (예시)", 37.47, 127.04, 11_500, 1_200))
        visits = listOf(app.pleosguard.demo.logic.Visit("v1", "shop-a", "정비소 A (예시)", wall - 86_400_000L, "소음", reviewed = false))
        screen = Screen.SERVICE
    }

    @Test fun recall() = shot("21-recall", careController().apply { screen = Screen.RECALL })

    @Test fun subscribe() = shot("22-subscribe", careController().apply { subscribed = setOf("parts"); selectedPlan = "warranty"; screen = Screen.SUBSCRIBE })

    @Test fun phoneOffline() = shot("23-phone-offline", careController().apply { serverOk = false; screen = Screen.PHONE })

    @Test fun phonePaired() = shot("23b-phone-paired", phoneController().apply { screen = Screen.PHONE })

    @Test fun phonePending() = shot("23c-phone-confirm", careController().apply { pairPending = "갤럭시 S24 (예시)"; screen = Screen.PHONE })

    @Test fun review() = shot("24-review", serviceController().apply {
        reviewShopId = "shop-a"; draftRating = 4; draftDims = mapOf("견적대로 결제" to 1, "설명을 들음" to 1); draftTags = setOf("빠름"); draftText = "앱 기록을 보여 줬더니 빨리 찾았습니다"
        screen = Screen.REVIEW
    })

    @Test fun leftAlert() = shot("25-left-alert", careController().apply { showTestLeftAlert() })

    @Test fun recordWithEvidence() = shot("26-record-evidence", phoneController().apply { lastRecord = book.records.last(); screen = Screen.RECORD })

    @Test fun homeWithPart() = shot("01b-home-part", careController().apply { selectedPart = "앞 타이어" })

    @Test fun recordList() = shot("13-record-list", withRecords(controller().apply { driveSource = DriveSource.TEST_PARKED; screen = Screen.RECORD_LIST }))

    @Test fun pattern() = shot("14-pattern", withRecords(controller().apply { driveSource = DriveSource.TEST_PARKED; screen = Screen.PATTERN }))

    @Test fun situationParked() = shot("15-situation-parked", controller().apply {
        driveSource = DriveSource.TEST_PARKED; parkedChoices = listOf("이상한 소음", "타이어 이상"); screen = Screen.SITUATION_PARKED
    })

    @Test fun service() = shot("17-service", serviceController())

    // ---------------------------------------------------------------- 도움 흐름(2026-11-14): 증상 고르기 → 대처 방법 → 기록 → 정비소
    private fun guideController(vararg picks: String) = controller().apply {
        driveSource = DriveSource.TEST_PARKED; openHelp(); picks.forEach { toggleParkedChoice(it) }; openGuide()
    }

    @Test fun situationParkedEmpty() = shot("15a-situation-parked-empty", controller().apply { driveSource = DriveSource.TEST_PARKED; openHelp() })

    @Test fun guide() = shot("16-guide", guideController("차체 떨림", "타이어 이상", "힘이 없어요"))

    @Test fun guideRecorded() = shot("16b-guide-recorded", guideController("차체 떨림", "타이어 이상", "힘이 없어요").apply { toggleGuideStep(0); recordFromGuide() })

    @Test fun guideSmell() = shot("16c-guide-smell", guideController("타는 냄새"))

    @Test fun guideDriving() = shot("16d-guide-driving", controller().apply { driveSource = DriveSource.TEST_DRIVING; quickGuide("차체 떨림") })

    // ---------------------------------------------------------------- 사고 대처 안내(2026-11-14): 보호자 알림 뒤 → 사고 확인 → 보험 접수 → 연결 → 접수 뒤 안내
    private fun accidentController(insurer: String? = "samsung") = controller().apply {
        driveSource = DriveSource.TEST_PARKED
        insurerId = insurer; insurerAsked = true // saveInsurer는 알림 글을 띄워 그림에 남으므로 값만 넣습니다
        incident = Incident(IncidentKind.ACCIDENT, wall, testInput = true, countdownFrom = clock, snap = snap())
        send()
    }

    @Test fun accidentConfirm() = shot("27-accident-confirm", accidentController())

    @Test fun accidentInsurer() = shot("27b-accident-insurer", accidentController().apply { accidentYes() })

    @Test fun accidentInsurerList() = shot("27c-accident-insurer-list", accidentController(null).apply { accidentYes() })

    @Test fun accidentCalling() = shot("27d-accident-calling", accidentController().apply { accidentYes(); callInsurer(insurer()!!) })

    @Test fun accidentAfter() = shot("27e-accident-after", accidentController().apply {
        accidentYes(); callInsurer(insurer()!!); reportDone(); setBody(app.pleosguard.demo.logic.BodyState.MINOR); toggleAccCard("secondary")
    })

    @Test fun insurerSetup() = shot("28-insurer-setup", controller().apply { driveSource = DriveSource.TEST_PARKED; openInsurerPicker(); insurerDraft = "kb" })

    @Test fun serviceHelp() = shot("17b-service-help", serviceController().apply {
        driveSource = DriveSource.TEST_PARKED; openHelp(); toggleParkedChoice("이상한 소음"); openGuide(); requestShopHelp()
    })

    // ---------------------------------------------------------------- 가로 화면(넓은 창, 에뮬레이터 앱 영역 전체 1682×1210dp)
    @Test @Config(qualifiers = WIDE) fun wideHomeParked() = shot("w01-home-parked", controller().apply { driveSource = DriveSource.TEST_PARKED })

    @Test @Config(qualifiers = WIDE) fun wideHomeDriving() = shot("w02-home-driving", controller().apply { driveSource = DriveSource.TEST_DRIVING; testSpeedKmh = 60f })

    @Test @Config(qualifiers = WIDE) fun wideWarningFind() = shot("w03-warning-find", controller().apply { driveSource = DriveSource.TEST_PARKED; screen = Screen.WARN_FIND })

    @Test @Config(qualifiers = WIDE) fun wideWarningResult() = shot("w04-warning-result", controller().apply {
        driveSource = DriveSource.TEST_PARKED; warnColor = "주황"; warnShape = "타이어 공기압"; warnMode = "계속 켜져 있어요"; screen = Screen.WARN_RESULT
    })

    @Test @Config(qualifiers = WIDE) fun wideSituationParked() = shot("w06-situation-parked", controller().apply {
        driveSource = DriveSource.TEST_PARKED; parkedChoices = listOf("이상한 소음", "타이어 이상"); screen = Screen.SITUATION_PARKED
    })

    @Test @Config(qualifiers = WIDE) fun wideCountdown() = shot("w07-countdown", countdownController())

    @Test @Config(qualifiers = WIDE) fun wideGuardian() = shot("w08-guardian", guardianController())

    @Test @Config(qualifiers = WIDE) fun widePattern() = shot("w09-pattern", withRecords(controller().apply { driveSource = DriveSource.TEST_PARKED; screen = Screen.PATTERN }))

    /** 세로 한 창 시안 크기가 아닌 1280×720dp(가로 시안 크기)에서는 긴급 안내 그림을 나란히 둡니다. */
    @Test @Config(qualifiers = WIDE) fun wideWarningBrake() = shot("w11-warning-brake", warn("빨강", "브레이크", app.pleosguard.demo.logic.WarningCatalog.STEADY))

    @Test @Config(qualifiers = WIDE) fun wideGuardianOdd() = shot("w12-guardian-mismatch", oddGuardianController())

    @Test @Config(qualifiers = WIDE) fun wideManage() = shot("w13-manage", withRecords(careController().apply { screen = Screen.MANAGE }))

    @Test @Config(qualifiers = WIDE) fun wideRecall() = shot("w14-recall", careController().apply { screen = Screen.RECALL })

    @Test @Config(qualifiers = WIDE) fun wideSubscribe() = shot("w15-subscribe", careController().apply { subscribed = setOf("parts"); screen = Screen.SUBSCRIBE })

    @Test @Config(qualifiers = WIDE) fun widePhone() = shot("w16-phone-paired", phoneController().apply { screen = Screen.PHONE })

    @Test @Config(qualifiers = WIDE) fun wideService() = shot("w17-service", serviceController())

    @Test @Config(qualifiers = WIDE) fun wideRecordList() = shot("w18-record-list", phoneController().apply { screen = Screen.RECORD_LIST })

    @Test @Config(qualifiers = WIDE) fun wideLeftAlert() = shot("w19-left-alert", careController().apply { showTestLeftAlert() })

    @Test @Config(qualifiers = WIDE) fun wideReview() = shot("w20-review", serviceController().apply { reviewShopId = "shop-a"; draftRating = 5; screen = Screen.REVIEW })

    @Test @Config(qualifiers = WIDE) fun wideRecordEvidence() = shot("w21-record-evidence", phoneController().apply { lastRecord = book.records.last(); screen = Screen.RECORD })

    @Test @Config(qualifiers = WIDE) fun wideHomePart() = shot("w22-home-part", careController().apply { selectedPart = "앞 타이어" })

    @Test @Config(qualifiers = WIDE) fun wideGuide() = shot("w23-guide", guideController("차체 떨림", "타이어 이상", "힘이 없어요"))

    @Test @Config(qualifiers = WIDE) fun wideGuideRecorded() = shot("w24-guide-recorded", guideController("이상한 소음", "핸들 쏠림").apply { recordFromGuide() })

    @Test @Config(qualifiers = WIDE) fun wideSituationEmpty() = shot("w06a-situation-parked-empty", controller().apply { driveSource = DriveSource.TEST_PARKED; openHelp() })

    @Test @Config(qualifiers = WIDE) fun wideAccidentConfirm() = shot("w25-accident-confirm", accidentController())

    @Test @Config(qualifiers = WIDE) fun wideAccidentInsurer() = shot("w26-accident-insurer", accidentController().apply { accidentYes() })

    @Test @Config(qualifiers = WIDE) fun wideAccidentInsurerList() = shot("w27-accident-insurer-list", accidentController(null).apply { accidentYes() })

    @Test @Config(qualifiers = WIDE) fun wideAccidentCalling() = shot("w28-accident-calling", accidentController().apply { accidentYes(); callInsurer(insurer()!!) })

    @Test @Config(qualifiers = WIDE) fun wideAccidentAfter() = shot("w29-accident-after", accidentController().apply {
        accidentYes(); callInsurer(insurer()!!); reportDone(); setBody(app.pleosguard.demo.logic.BodyState.SEVERE)
    })

    @Test @Config(qualifiers = WIDE) fun wideInsurerSetup() = shot("w30-insurer-setup", controller().apply { driveSource = DriveSource.TEST_PARKED; openInsurerPicker() })

    /** 폰으로 찍은 현장 사진 1장이 사고 기록에 붙은 뒤: 받은 사진 수가 단추 안에 보이고 카드 밖으로 넘치지 않아야 합니다. */
    @Test @Config(qualifiers = WIDE) fun wideAccidentPhotoReceived() = shot("w31-accident-photo-received", accidentController().apply {
        pairedPhone = "갤럭시 S24 (예시)"
        accidentYes(); skipReport(); setBody(app.pleosguard.demo.logic.BodyState.OK)
        val e = app.pleosguard.demo.logic.Evidence("ev-acc-1", "photo", wall + 60_000, 0, null, "앞부분", "사진 · 차체 손상 · 앞부분")
        val r = app.pleosguard.demo.logic.Record(t0 = clock, wall = wall, drivingAtRecord = false, symptom = "사고", severity = 3, evidence = listOf(e))
        book.add(r); accRecordId = r.id
    })

    companion object { const val WIDE = "w1682dp-h1210dp-land-mdpi" }
}
