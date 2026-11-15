package app.pleosguard.demo

import app.pleosguard.demo.logic.BodyAnswer
import app.pleosguard.demo.logic.BodyState
import app.pleosguard.demo.logic.CareSnapshot
import app.pleosguard.demo.logic.CareStore
import app.pleosguard.demo.logic.GuideAction
import app.pleosguard.demo.logic.Insurers
import app.pleosguard.demo.logic.YesNo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 사고 대처 안내(보호자 알림 뒤 → 사고 확인 → 보험 접수 → 연결 → 접수 뒤 안내)와 말 답 · 보험사 저장. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccidentFlowTest {
    private var clock = 100_000L
    private val wall = 1_794_571_203_000L // 2026-11-13 21:00:03 KST
    private fun controller() = DemoController(null, null, now = { clock }, wallClock = { wall }).apply { driveSource = DriveSource.TEST_PARKED }

    @Test fun yesNoWords() {
        listOf("예", "네", "네 맞아요", "응", "연결해 줘", "전화해 주세요").forEach { assertEquals(it, true, YesNo.parse(it)) }
        listOf("아니요", "아니예요", "아뇨", "괜찮아요", "나중에", "연결하지 마").forEach { assertEquals(it, false, YesNo.parse(it)) }
        assertNull(YesNo.parse("음"))
        assertNull(YesNo.parse(""))
    }

    @Test fun bodyWords() {
        assertEquals(BodyState.OK, BodyAnswer.parse("괜찮아요"))
        assertEquals(BodyState.OK, BodyAnswer.parse("안 아파요"))
        assertEquals(BodyState.MINOR, BodyAnswer.parse("조금 아파요"))
        assertEquals(BodyState.MINOR, BodyAnswer.parse("목이 좀 결려요"))
        assertEquals(BodyState.SEVERE, BodyAnswer.parse("많이 다쳤어요"))
        assertEquals(BodyState.SEVERE, BodyAnswer.parse("다쳤어요"))
        assertEquals(BodyState.SEVERE, BodyAnswer.parse("피가 나요"))
        assertNull(BodyAnswer.parse("글쎄"))
    }

    @Test fun insurerIsSavedInCareJson() {
        val s = CareStore.from(CareStore.toJson(CareSnapshot(insurerId = "samsung", insurerAsked = true)))
        assertEquals("samsung", s.insurerId)
        assertTrue(s.insurerAsked)
        val old = CareStore.from("""{"version":1}""")
        assertNull(old.insurerId)
        assertFalse(old.insurerAsked)
    }

    @Test fun everyInsurerHasANumberAndUniqueId() {
        assertTrue(Insurers.ALL.isNotEmpty())
        assertEquals(Insurers.ALL.size, Insurers.ALL.map { it.id }.distinct().size)
        Insurers.ALL.forEach { assertTrue(it.name, Regex("""\d{4}-\d{4}""").matches(it.phone)) }
    }

    @Test fun accidentAfterSendingGoesToConfirm() {
        val c = controller()
        c.startCountdown(IncidentKind.ACCIDENT, testInput = true)
        c.send()
        assertEquals(Screen.ACCIDENT, c.screen)
        assertEquals(AccStep.CONFIRM, c.accStep)
        assertEquals("2026-11-13 21:00", c.accTime())
    }

    @Test fun airbagAlsoOpensTheGuideButHelpDoesNot() {
        val a = controller(); a.startCountdown(IncidentKind.AIRBAG, testInput = true); a.send()
        assertEquals(Screen.ACCIDENT, a.screen)
        val h = controller(); h.openRecipients(IncidentKind.HELP); h.send()
        assertEquals(Screen.GUARDIAN, h.screen)
    }

    @Test fun noMeansNotAnAccident() {
        val c = controller(); c.startCountdown(IncidentKind.ACCIDENT, testInput = true); c.send()
        c.accidentNo()
        assertEquals(Screen.GUARDIAN, c.screen)
    }

    @Test fun knownInsurerCallReportAndBody() {
        val c = controller(); c.saveInsurer("hyundai")
        c.startCountdown(IncidentKind.ACCIDENT, testInput = true); c.send()
        c.accidentYes()
        assertEquals(AccStep.INSURER, c.accStep)
        c.callInsurer(c.insurer()!!)
        assertEquals(AccStep.CALLING, c.accStep)
        assertEquals("현대해상", c.accInsurer?.name)
        c.reportDone()
        assertEquals(AccStep.AFTER, c.accStep)
        assertTrue(c.accReported)
        c.setBody(BodyState.MINOR)
        assertEquals(BodyState.MINOR, c.bodyState)
        assertTrue("body" in c.accDone)
        c.finishAccident()
        assertEquals(Screen.HOME, c.screen)
    }

    @Test fun skippingTheReportStillShowsSafetyGuide() {
        val c = controller()
        c.startCountdown(IncidentKind.ACCIDENT, testInput = true); c.send(); c.accidentYes()
        assertNull(c.insurer())
        c.skipReport()
        assertEquals(AccStep.AFTER, c.accStep)
        assertFalse(c.accReported)
    }

    @Test fun answersFromAnOldStepAreIgnored() {
        val c = controller()
        c.startCountdown(IncidentKind.ACCIDENT, testInput = true); c.send(); c.accidentYes()
        c.accidentNo() // 이미 다음 단계라 무시
        assertEquals(Screen.ACCIDENT, c.screen)
        assertEquals(AccStep.INSURER, c.accStep)
    }

    @Test fun spokenPhoneReadsDigits() {
        assertEquals("1 5 8 8, 5 1 1 4", DemoController.spokenPhone("1588-5114"))
    }

    /** 폰이 연결돼 있지 않으면 사고 안내를 떠나지 않고, 빈 사고 기록도 만들지 않습니다. */
    @Test fun photoWithoutPhoneStaysInTheAccidentGuide() {
        val c = controller()
        c.startCountdown(IncidentKind.ACCIDENT, testInput = true); c.send(); c.accidentYes(); c.skipReport()
        c.accidentPhoto()
        assertEquals(Screen.ACCIDENT, c.screen)
        assertEquals(AccStep.AFTER, c.accStep)
        assertNull(c.accRecordId)
        assertEquals(0, c.accPhotoCount())
    }

    /** 대처 방법에서 폰 기록을 눌렀는데 폰이 없으면 폰 연결 화면으로 가고, 뒤로 가면 대처 방법으로 돌아옵니다. */
    @Test fun longViewScreensCloseWhenDrivingStarts() {
        val c = controller()
        for (s in listOf(Screen.RECALL, Screen.SUBSCRIBE, Screen.REVIEW)) {
            c.driveSource = DriveSource.TEST_PARKED; c.go(s); c.closeParkedOnlyScreens(); assertEquals(s, c.screen)
            c.driveSource = DriveSource.TEST_DRIVING; c.closeParkedOnlyScreens(); assertEquals(Screen.HOME, c.screen)
        }
        c.go(Screen.SERVICE); c.closeParkedOnlyScreens(); assertEquals(Screen.SERVICE, c.screen)
    }
    @Test fun phoneStepFromTheGuideComesBackToTheGuide() {
        val c = controller()
        c.openHelp(); c.toggleParkedChoice("이상한 소음"); c.openGuide()
        assertEquals(Screen.GUIDE, c.screen)
        c.guideAction(GuideAction.PHONE_NOISE)
        assertEquals(Screen.PHONE, c.screen)
        c.back()
        assertEquals(Screen.GUIDE, c.screen)
        c.go(Screen.HOME); c.go(Screen.PHONE); c.back()
        assertEquals(Screen.HOME, c.screen)
    }
}
