package app.pleosguard.demo

import app.pleosguard.demo.logic.ConfirmPromptDetector
import app.pleosguard.demo.logic.ConfirmPromptDetector.Decision
import app.pleosguard.demo.logic.Quality
import app.pleosguard.demo.logic.SteppedScenes
import app.pleosguard.demo.logic.VoiceAnswer
import app.pleosguard.demo.logic.VoiceAnswerParser
import app.pleosguard.demo.logic.VoiceRoute
import app.pleosguard.demo.logic.VoiceRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** rules-lab 2.2.0 값으로 판정 규칙을 확인합니다(화면·SDK 없이 JVM에서). */
class RulesTest {
    private fun run(d: ConfirmPromptDetector, from: Long, to: Long, speed: (Long) -> Float?, brake: (Long) -> Boolean?, q: Quality = Quality.TEST_INPUT): List<Pair<Long, Decision>> {
        val out = mutableListOf<Pair<Long, Decision>>()
        var t = from
        while (t <= to) { val r = d.onSample(t, speed(t), q, brake(t)); if (r != Decision.NONE) out += t to r; t += 100 }
        return out
    }

    @Test fun promptWhenBrakeDoesNotSlowCar() {
        val d = ConfirmPromptDetector()
        val r = run(d, 0, 3_000, { 16.7f }, { it >= 1_000 })
        assertEquals(listOf(2_000L to Decision.PROMPT), r)
    }

    @Test fun noPromptWhenCarDecelerates() {
        val d = ConfirmPromptDetector()
        val r = run(d, 0, 3_000, { t -> if (t < 1_000) 16.7f else 16.7f - (t - 1_000) / 1000f * 2f }, { it >= 1_000 })
        assertEquals(listOf(2_000L to Decision.DECELERATING), r)
    }

    @Test fun noPromptBelowMovingSpeed() {
        val d = ConfirmPromptDetector()
        assertTrue(run(d, 0, 3_000, { 0.5f }, { it >= 1_000 }).isEmpty())
    }

    @Test fun missingBrakeIsNotFalseAndStopsObservation() {
        val d = ConfirmPromptDetector()
        // 브레이크가 눌렸다가 값이 사라지면(null) 관측을 멈춥니다. false로 바꿔 판정하지 않습니다.
        assertTrue(run(d, 0, 3_000, { 16.7f }, { if (it < 1_000) false else if (it < 1_500) true else null }).isEmpty())
    }

    @Test fun staleSpeedIsNotUsed() {
        val d = ConfirmPromptDetector()
        assertTrue(run(d, 0, 3_000, { 16.7f }, { it >= 1_000 }, Quality.STALE).isEmpty())
    }

    @Test fun suppressedWithinFiveMinutes() {
        val d = ConfirmPromptDetector()
        val first = run(d, 0, 3_000, { 16.7f }, { it in 1_000..2_500 })
        assertEquals(Decision.PROMPT, first.single().second)
        val second = run(d, 3_100, 6_000, { 16.7f }, { it >= 4_000 })
        assertEquals(Decision.SUPPRESSED, second.single().second)
        val later = run(d, 302_100, 306_000, { 16.7f }, { it >= 303_000 })
        assertEquals(Decision.PROMPT, later.single().second)
    }

    @Test fun lowSpeedZoneSkipsThenObservesAboveThreshold() {
        val d = ConfirmPromptDetector(minStartSpeed = app.pleosguard.demo.logic.RulesLab.MIN_PROMPT_START_MPS)
        // 10km/h에서 브레이크: 저속(PMSA) 구간이라 묻지 않음
        val low = run(d, 0, 3_000, { 10f / 3.6f }, { it >= 1_000 })
        assertEquals(listOf(1_000L to Decision.LOW_SPEED), low)
        // 브레이크를 밟은 채 10 → 25km/h로 빨라지면, 15km/h를 넘은 뒤 1초 관측하고 묻습니다
        val d2 = ConfirmPromptDetector(minStartSpeed = app.pleosguard.demo.logic.RulesLab.MIN_PROMPT_START_MPS)
        val rising = run(d2, 0, 6_000, { t -> (10f + (t / 1000f) * 3f) / 3.6f }, { true })
        assertEquals(Decision.PROMPT, rising.last().second)
        assertTrue((d2.lastSpeedChange ?: 0f) >= 0.5f)
    }

    @Test fun accelCandidateNeedsHoldAndIsRecordOnly() {
        val a = app.pleosguard.demo.logic.AccelCandidateDetector()
        var fired: Long? = null
        var t = 0L
        while (t <= 5_000) { val v = if (t < 1_000) 10f else 10f + (t - 1_000) / 1000f * 3f; if (a.onSample(t, v, Quality.OK) && fired == null) fired = t; t += 100 }
        assertTrue("3m/s² 가속이 700ms 이어지면 후보", fired != null && fired!! in 1_700L..2_300L)
        val b = app.pleosguard.demo.logic.AccelCandidateDetector()
        var any = false; t = 0
        while (t <= 5_000) { val v = 10f + t / 1000f * 2f; if (b.onSample(t, v, Quality.OK)) any = true; t += 100 }
        assertFalse("2m/s²는 후보 아님", any)
    }

    @Test fun voiceAnswers() {
        assertEquals(VoiceAnswer.NOT_STOPPING, VoiceAnswerParser.parse("안 멈춰요"))
        assertEquals(VoiceAnswer.NOT_STOPPING, VoiceAnswerParser.parse("차가 멈추지 않아요!"))
        assertEquals(VoiceAnswer.OK, VoiceAnswerParser.parse("괜찮아요"))
        // 두 말이 섞이면 위험한 쪽(안 멈춰요)을 고릅니다.
        assertEquals(VoiceAnswer.NOT_STOPPING, VoiceAnswerParser.parse("괜찮… 아니 안 멈춰"))
        assertEquals(VoiceAnswer.UNKNOWN, VoiceAnswerParser.parse("음"))
    }

    @Test fun voiceRouting() {
        assertEquals(VoiceRoute.DECEL_GUIDE, VoiceRouter.route("차가 안 멈춰"))
        assertEquals(VoiceRoute.ACCEL_GUIDE, VoiceRouter.route("저절로 빨라져요"))
        assertEquals(VoiceRoute.HELP_REQUEST, VoiceRouter.route("도와줘"))
        assertEquals(VoiceRoute.WARNING_FIND, VoiceRouter.route("경고등이 켜졌어"))
        assertEquals(VoiceRoute.RECORD_MEMO, VoiceRouter.route("앞 타이어 쪽 끼익 소리"))
    }

    @Test fun steppedScenesHoldOneSecondAndDoNotLoop() {
        val s = SteppedScenes()
        s.start(0)
        assertFalse(s.advance(999))      // 1,000ms 전에는 바뀌지 않음
        assertTrue(s.advance(1_000)); assertEquals(1, s.index)
        assertFalse(s.advance(1_500))
        assertTrue(s.advance(2_000)); assertEquals(2, s.index)
        assertFalse(s.advance(10_000))   // 마지막 장면에서 멈춤(반복 없음)
        assertEquals(2, s.index)
    }
}
