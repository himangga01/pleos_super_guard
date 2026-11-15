package app.pleosguard.demo

import app.pleosguard.demo.logic.WarnLevel
import app.pleosguard.demo.logic.WarningCatalog
import app.pleosguard.demo.logic.WarningCatalog.BLINK
import app.pleosguard.demo.logic.WarningCatalog.STEADY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 경고등 시연 도감: 색 4 × 모양 8 × 켜짐 2 = 64개 조합이 모두 결과를 내는지, 대표 조합이 맞는 후보를 고르는지 봅니다. */
class WarningCatalogTest {
    private val cat = WarningCatalog

    @Test fun every64CombinationGivesAResultForThatShape() {
        var n = 0
        for (color in cat.COLORS) for (shape in cat.SHAPES) for (mode in cat.MODES) {
            val r = cat.find(color, shape, mode)
            assertEquals("$color/$shape/$mode", shape, r.primary.shape)
            assertTrue(r.all.all { it.shape == shape })
            // 고른 색의 후보가 도감에 있으면 그 색을 고르고 색 안내가 없습니다. 없으면 가까운 후보와 색 안내를 냅니다.
            val hasColor = cat.ENTRIES.any { it.shape == shape && it.color == color }
            if (hasColor) { assertEquals(color, r.primary.color); assertNull(r.colorNote) }
            else { assertNotNull("$color/$shape/$mode", r.colorNote); assertTrue(r.colorNote!!.contains("보통")) }
            n++
        }
        assertEquals(64, n)
    }

    @Test fun catalogIsWellFormed() {
        assertEquals(cat.ENTRIES.size, cat.ENTRIES.map { it.id }.toSet().size)
        cat.ENTRIES.forEach { e ->
            assertTrue(e.id, e.shape in cat.SHAPES); assertTrue(e.id, e.color in cat.COLORS)
            assertTrue(e.id, listOf(e.name, e.judgement, e.meaning, e.now, e.next, e.modeLabel).all { it.isNotBlank() })
            assertTrue(e.id, e.keys.isNotEmpty())
            // 색과 급한 정도가 맞아야 합니다: 빨강은 세우기·바로 조치, 주황은 점검·바로 조치, 초록·파랑은 상태 표시.
            when (e.color) {
                "빨강" -> assertTrue(e.id, e.level == WarnLevel.STOP || e.level == WarnLevel.URGENT)
                "주황" -> assertTrue(e.id, e.level == WarnLevel.SERVICE || e.level == WarnLevel.URGENT)
                else -> assertEquals(e.id, WarnLevel.INFO, e.level)
            }
        }
        // 모양마다 계속 켜짐과 깜박임을 모두 다룹니다(켜짐 방식과 상관없는 항목은 둘 다 맡음).
        cat.SHAPES.forEach { sh ->
            val es = cat.ENTRIES.filter { it.shape == sh }
            assertTrue(sh, es.isNotEmpty())
            assertTrue(sh, es.any { it.blink != true }); assertTrue(sh, es.any { it.blink != false })
        }
    }

    @Test fun representativeCases() {
        assertEquals("tpms-low", cat.find("주황", "타이어 공기압", STEADY).primary.id)
        assertTrue(cat.find("주황", "타이어 공기압", STEADY).exact)
        assertEquals("tpms-fault", cat.find("주황", "타이어 공기압", BLINK).primary.id)
        // 사용자가 본 경우: 빨강 + 브레이크 + 계속 켜짐 → 브레이크 경고(찾지 못함이 아님)
        val brake = cat.find("빨강", "브레이크", STEADY)
        assertEquals("brake", brake.primary.id); assertTrue(brake.exact); assertEquals(WarnLevel.STOP, brake.primary.level)
        assertEquals(listOf("brake-blink"), brake.others.map { it.id })
        assertEquals("brake-blink", cat.find("빨강", "브레이크", BLINK).primary.id)
        assertEquals("brake-assist", cat.find("주황", "브레이크", STEADY).primary.id)
        assertEquals("coolant-cold", cat.find("파랑", "냉각수 온도", STEADY).primary.id)
        assertEquals("oil-level", cat.find("주황", "오일 압력", STEADY).primary.id)
        assertEquals("engine-blink", cat.find("주황", "엔진 점검", BLINK).primary.id)
    }

    @Test fun mismatchNotes() {
        val green = cat.find("초록", "엔진 점검", STEADY)
        assertEquals("engine", green.primary.id)
        assertTrue(green.colorNote!!.startsWith("초록·파랑 표시등은 보통 기능이 켜져 있다는 상태 표시입니다"))
        assertTrue(green.colorNote!!.contains("이 모양은 보통 주황입니다"))
        val redTire = cat.find("빨강", "타이어 공기압", STEADY)
        assertEquals("이 모양은 보통 주황입니다. 색을 다시 확인하세요", redTire.colorNote)
        // 켜짐 방식과 상관없는 경고를 깜박임으로 고르면 차종마다 다르다고 안내합니다.
        val oilBlink = cat.find("빨강", "오일 압력", BLINK)
        assertEquals("oil-pressure", oilBlink.primary.id); assertNotNull(oilBlink.modeNote); assertFalse(oilBlink.exact)
        assertNull(cat.find("빨강", "오일 압력", STEADY).modeNote)
    }

    @Test fun searchAndSpokenParsing() {
        assertEquals(cat.ENTRIES, cat.search(""))
        assertEquals(listOf("coolant-hot"), cat.search("과열").map { it.id })
        assertTrue(cat.search("ABS").any { it.id == "brake-assist" })
        assertTrue(cat.search("사이드 브레이크").all { it.shape == "브레이크" })
        assertTrue(cat.search("없는말").isEmpty())
        assertEquals(Triple("주황", "타이어 공기압", BLINK), cat.parseSpoken("주황색 타이어 경고등이 깜박여요"))
        assertEquals(Triple(null, "오일 압력", STEADY), cat.parseSpoken("엔진오일 불이 들어왔어"))
        assertEquals("엔진 점검", cat.parseSpoken("노란 엔진 모양").second)
        assertNull(cat.parseSpoken("뭔가 켜졌어").second)
    }
}
