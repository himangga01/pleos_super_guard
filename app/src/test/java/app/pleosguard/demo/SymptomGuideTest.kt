package app.pleosguard.demo

import app.pleosguard.demo.logic.GuideAction
import app.pleosguard.demo.logic.GuideLevel
import app.pleosguard.demo.logic.SymptomGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 대처 안내 규칙: 증상 조합 → 급한 정도 · 확인 순서 · 원인. */
class SymptomGuideTest {
    @Test fun everySymptomAloneHasAGuide() {
        SymptomGuide.SYMPTOMS.forEach { s ->
            val g = SymptomGuide.forChoices(listOf(s))
            assertEquals(listOf(s), g.symptoms)
            assertTrue("$s 단계", g.steps.size in 3..SymptomGuide.MAX_STEPS)
            assertTrue("$s 첫 줄", g.headline.isNotBlank())
            assertEquals("$s 단계 중복", g.steps.size, g.steps.map { it.key }.distinct().size)
        }
    }

    @Test fun burningSmellIsStopAndPutsSafetyFirst() {
        val g = SymptomGuide.forChoices(listOf("핸들 쏠림", "타는 냄새"))
        assertEquals(GuideLevel.STOP, g.level)
        assertEquals("안전한 곳에 세우고 시동을 끄세요", g.headline)
        assertEquals("smoke", g.steps.first().key)
        assertEquals(listOf("냄새"), g.steps.first().from)
    }

    @Test fun pedalIsStop() {
        assertEquals(GuideLevel.STOP, SymptomGuide.forChoices(listOf("페달 이상")).level)
    }

    @Test fun shakeWithTireBecomesStopAndChecksTiresFirst() {
        // 사용자가 화면에서 고른 조합(차체 떨림 · 타이어 이상 · 힘이 없어요)
        val g = SymptomGuide.forChoices(listOf("차체 떨림", "타이어 이상", "힘이 없어요"))
        assertEquals(GuideLevel.STOP, g.level)
        assertEquals("속도를 줄이고 세운 뒤 타이어부터 확인하세요", g.headline)
        assertEquals(listOf("떨림 + 타이어", "떨림 + 힘 없음"), g.combos)
        assertEquals("tire_look", g.steps[0].key)
        assertEquals(listOf("떨림", "타이어"), g.steps[0].from)
        assertTrue(g.steps.size <= SymptomGuide.MAX_STEPS)
        // 증상마다 적어도 한 단계가 들어갑니다.
        listOf("떨림", "타이어", "힘 없음").forEach { t -> assertTrue(t, g.steps.any { t in it.from }) }
        assertTrue(g.causes.first() == "타이어 손상")
        // 엔진 경고등(떨림 + 힘 없음)과 공기압 경고등(타이어)이 합쳐지면 한쪽 이름이 다른 증상을 덮지 않게 일반 이름을 씁니다.
        val warn = g.steps.first { it.key == "warn" }
        assertEquals("경고등이 켜졌는지 보세요", warn.title)
        assertTrue(warn.from.containsAll(listOf("타이어", "힘 없음")))
    }

    @Test fun singleWarnStepKeepsItsSpecificName() {
        assertEquals("공기압 경고등을 보세요", SymptomGuide.forChoices(listOf("타이어 이상")).steps.first { it.key == "warn" }.title)
    }

    @Test fun sameActionAppearsOnce() {
        val g = SymptomGuide.forChoices(listOf("힘이 없어요", "경고등이 켜졌어요", "타이어 이상"))
        assertEquals(1, g.steps.count { it.action == GuideAction.WARN_FIND })
        val warn = g.steps.first { it.key == "warn" }
        assertTrue(warn.from.containsAll(listOf("힘 없음", "경고등")))
    }

    @Test fun phoneActionsMatchTheSymptom() {
        assertTrue(SymptomGuide.forChoices(listOf("이상한 소음")).steps.any { it.action == GuideAction.PHONE_NOISE })
        assertTrue(SymptomGuide.forChoices(listOf("차체 떨림")).steps.any { it.action == GuideAction.PHONE_VIBRATION })
        assertTrue(SymptomGuide.forChoices(listOf("경고등이 켜졌어요")).steps.any { it.action == GuideAction.PHONE_WARN_PHOTO })
    }

    @Test fun unsureIsDroppedWhenOtherSymptomsArePicked() {
        val g = SymptomGuide.forChoices(listOf("잘 모르겠음", "핸들 쏠림"))
        assertEquals(listOf("핸들 쏠림"), g.symptoms)
        assertFalse(g.steps.any { "모름" in it.from })
    }

    @Test fun emptyOrUnknownFallsBackToUnsure() {
        assertEquals(listOf(SymptomGuide.UNSURE), SymptomGuide.forChoices(emptyList()).symptoms)
        val g = SymptomGuide.forChoices(listOf("없는 증상"))
        assertEquals(GuideLevel.CHECK, g.level)
        assertEquals("hazard", g.steps.first().key)
    }

    @Test fun voiceLineHasNoMiddleDots() {
        val g = SymptomGuide.forChoices(listOf("이상한 소음"))
        assertFalse(g.voice.contains("·"))
        assertTrue(g.voice.startsWith(g.headline.replace(" · ", ", ")))
    }
}
