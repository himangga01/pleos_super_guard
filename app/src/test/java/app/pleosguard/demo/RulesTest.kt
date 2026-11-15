package app.pleosguard.demo

import app.pleosguard.demo.logic.Quality
import app.pleosguard.demo.logic.VoiceRoute
import app.pleosguard.demo.logic.VoiceRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** rules-lab 2.2.0 값으로 판정 규칙을 확인합니다(화면·SDK 없이 JVM에서). */
class RulesTest {
    @Test fun voiceRouting() {
        assertEquals(VoiceRoute.HELP_REQUEST, VoiceRouter.route("도와줘"))
        assertEquals(VoiceRoute.WARNING_FIND, VoiceRouter.route("경고등이 켜졌어"))
        assertEquals(VoiceRoute.RECORD_MEMO, VoiceRouter.route("앞 타이어 쪽 끼익 소리"))
    }

}
