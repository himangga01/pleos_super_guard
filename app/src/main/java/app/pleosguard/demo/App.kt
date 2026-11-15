package app.pleosguard.demo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pleosguard.demo.logic.Quality
import app.pleosguard.demo.screens.*
import app.pleosguard.demo.sdk.VehicleSignals
import app.pleosguard.demo.sdk.Voice
import app.pleosguard.demo.ui.*
import kotlin.math.roundToInt

/** 앱 뿌리 화면. [voice]·[signals]가 없으면(화면 시험) 음성·신호 없이 그립니다. */
@Composable
fun PleosGuardApp(c: DemoController, voice: Voice?, signals: VehicleSignals?, sdkCheck: (@Composable () -> Unit)? = null) {
    c.sig // 음성 상태(듣는 중·들은 말)가 바뀌면 다시 그립니다. 차량 신호는 controller의 상태(speedView 등)로 읽습니다.
    BackHandler(enabled = c.screen != Screen.HOME || c.demoPanel) { if (c.demoPanel) c.demoPanel = false else c.back() }
    val listening = voice?.listening == true
    val partial = voice?.partial.orEmpty()
    BoxWithConstraints(Modifier.fillMaxSize().background(C.Bg)) {
        // 가로 화면: 창이 넓고 가로가 더 길면 왼쪽 도크와 두 칸 배치를 씁니다. 창 크기가 바뀌어도 고른 부위·진행 중 안내·남은 확인 시간은 controller에 남습니다.
        val wide = maxWidth >= WIDE_MIN_WIDTH && maxWidth > maxHeight
        CompositionLocalProvider(LocalWide provides wide) {
            if (wide && dockedScreen(c)) DockedFrame(c) { Screens(c, voice, listening, partial, sdkCheck, wide = true) }
            else Screens(c, voice, listening, partial, sdkCheck, wide)
        }
        c.shareQr?.let { QrOverlay(it) { c.shareQr = null } }
        c.qrSpec?.let { q -> QrOverlay(q.link, q.title, q.sub, q.badge) { c.qrSpec = null; q.onClose?.invoke() } }
        // 짐 알림(도착 뒤 내리려 할 때). 긴급 화면 위에는 띄우지 않습니다.
        if (c.screen !in DemoController.EMERGENCY) LeftItemOverlay(c)
        Toast(c.toast, Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp))
        // 내 보험사 고르기(처음 설정 · 안심 화면). 긴급 화면 위에는 띄우지 않습니다.
        if (c.insurerPicker && c.screen !in DemoController.EMERGENCY && !c.intro) InsurerPickerOverlay(c)
        if (c.demoPanel) DemoPanel(c, voice, signals)
        if (c.intro && c.screen !in DemoController.EMERGENCY) {
            IntroOverlay()
            // 첫 화면이 실제로 그려진 뒤부터 1.8초 보여 줍니다. 앱을 처음 그리는 데 시간이 걸리면 onCreate부터 세는 시간은 화면이 뜨기 전에 끝나 버립니다.
            LaunchedEffect(Unit) { withFrameNanos { }; delay(INTRO_MS); c.intro = false }
        }
    }
}

/** 가로 화면으로 보는 최소 창 폭. 내비 옆 세로 창(약 840dp)은 세로 배치를 씁니다. */
val WIDE_MIN_WIDTH = 1100.dp

@Composable
private fun Screens(c: DemoController, voice: Voice?, listening: Boolean, partial: String, sdkCheck: (@Composable () -> Unit)?, wide: Boolean) {
        when (c.screen) {
            Screen.HOME -> if (c.driving) HomeDriving(c, listening, partial) else if (wide) HomeParkedWide(c) else HomeParked(c)
            Screen.WARN_FIND -> if (wide) WarningWide(c) else WarningFind(c)
            Screen.WARN_RESULT -> if (wide) WarningWide(c) else WarningResult(c)
            Screen.RECORD -> RecordScreen(c, partial, listening)
            Screen.GUARDIAN -> GuardianScreen(c)
            Screen.SERVICE -> ServiceScreen(c)
            Screen.SITUATION -> if (c.driving) SituationScreen(c) else SituationParkedScreen(c)
            Screen.SITUATION_PARKED -> if (c.driving) SituationScreen(c) else SituationParkedScreen(c)
            Screen.GUIDE -> GuideScreen(c)
            Screen.ACCIDENT -> AccidentScreen(c)
            Screen.WARN_DETAIL -> WarningDetailScreen(c)
            Screen.WARN_SEARCH -> WarningSearchScreen(c)
            Screen.RECORD_LIST -> RecordListScreen(c)
            Screen.PATTERN -> PatternScreen(c)
            Screen.MANAGE -> ManageScreen(c)
            Screen.RECALL -> RecallScreen(c)
            Screen.SUBSCRIBE -> SubscribeScreen(c)
            Screen.PHONE -> PhoneScreen(c)
            Screen.REVIEW -> ReviewScreen(c)
            Screen.RECIPIENTS -> SendConfirmScreen(c, countdown = false)
            Screen.COUNTDOWN -> SendConfirmScreen(c, countdown = true)
            Screen.SDK_CHECK -> sdkCheck?.invoke() ?: Text("SDK 점검 없음", style = Body)
        }
}

/** 시연 모드 패널: 시험 입력을 넣고 실제 SDK 신호 상태를 확인합니다. 여기서 넣은 값은 화면에 [시험 입력]으로 표시됩니다. */
@Composable
fun DemoPanel(c: DemoController, voice: Voice?, signals: VehicleSignals?) {
    c.tick // 실제 신호 값을 계속 보여 줍니다
    Box(Modifier.fillMaxSize().background(Color(0xB3000000)).clickable { c.demoPanel = false }, contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.widthIn(max = 900.dp).fillMaxWidth().padding(16.dp).clip(R24).background(C.Surf).border(2.dp, C.Amber, R24)
            .clickable(enabled = false) {}.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("시연 모드 · 시험 입력", style = ts(30.sp, C.Amber, FontWeight.Bold), modifier = Modifier.weight(1f))
                Text("닫기", style = ts(26.sp, C.Mint, FontWeight.SemiBold), modifier = Modifier.clickable { c.demoPanel = false }.padding(8.dp))
            }
            Text("Virtual Console에 카드가 없는 값(차속, 에어백 전개 신호)은 여기서 넣습니다. 이 값이 섞인 사건은 모의 발송만 합니다.", style = Small)
            PanelBtn("주행 상태: " + when (c.driveSource) { DriveSource.REAL -> "실제 신호(CarUxRestrictions)"; DriveSource.TEST_DRIVING -> "주행 중 [시험 입력]"; DriveSource.TEST_PARKED -> "정차 중 [시험 입력]" }) { c.cycleDrive() }
            PanelBtn("차속 시험 입력: " + (c.testSpeedKmh?.let { "시속 ${it.roundToInt()}km 유지 (끄려면 누르기)" } ?: "끔 (누르면 시속 60km)")) { c.toggleTestSpeed() }
            PanelBtn("에어백 전개 신호(운전석) 시험 입력 → 발송 확인 10초") { c.startCountdown(IncidentKind.AIRBAG, testInput = true) }
            PanelBtn("시각: " + if (c.clockOffsetMs == 0L) "실제 시각 (누르면 대표 사례 21:00:03)" else "대표 사례 시각 사용 중 (누르면 실제 시각)") {
                if (c.clockOffsetMs == 0L) c.setCaseTime("21:00:03") else c.clockOffsetMs = 0L
            }
            PanelBtn("예시 기록 2건 넣기(재현 조건 카드 시연)") { c.seedExamples(); c.demoPanel = false }
            // 짐 알림: Virtual Console의 Trunk Position · 문 카드 대신 쓰는 시험 입력
            PanelBtn("트렁크: " + when (c.testTrunk) { null -> "실제 신호 (${c.signals?.trunk?.let { if (it > 0) "열림" else "닫힘" } ?: "확인 불가"})"; 0 -> "닫힘 [시험 입력]"; else -> "열림 [시험 입력]" }) {
                c.testTrunk = when (c.testTrunk) { null -> 1; 1 -> 0; else -> null }
            }
            PanelBtn("운전석 문: " + when (c.testDriverDoor) { null -> "실제 신호"; 0 -> "닫힘 [시험 입력]"; else -> "열림 [시험 입력]" }) {
                c.testDriverDoor = when (c.testDriverDoor) { null -> 1; 1 -> 0; else -> null }
            }
            PanelBtn("짐 알림 바로 보기 [시험 입력]") { c.showTestLeftAlert(); c.demoPanel = false }
            PanelBtn("폰 기록 넣기 [시험 입력]: 소리 · 진동") { c.injectTestEvidence("noise"); c.injectTestEvidence("vibration"); c.demoPanel = false }
            PanelBtn("SDK 점검 화면 열기") { c.go(Screen.SDK_CHECK) }
            val (v, q) = c.speedNow()
            val sigText = buildString {
                append("실제 신호  ")
                append("브레이크: ${signals?.brake?.let { if (it) "밟음" else "안 밟음" } ?: "확인 불가"}")
                append(" · 차속(AAOS): ${signals?.speedMps?.let { "${(it * 3.6f).roundToInt()}km/h" } ?: "없음"}")
                append(" · 조향: ${signals?.steeringDeg?.roundToInt()?.let { "${it}도" } ?: "없음"}")
                append(" · 주행 제한: ${when (signals?.restricted) { true -> "켜짐"; false -> "꺼짐"; null -> "확인 불가" }}")
                append("\n지금 쓰는 차속: ${v?.let { "${(it * 3.6f).roundToInt()}km/h" } ?: "없음"} (${when (q) { Quality.OK -> "실제"; Quality.TEST_INPUT -> "시험 입력"; Quality.STALE -> "오래됨"; Quality.MISSING -> "없음" }})")
                append("\n음성 안내: ${voice?.ttsState ?: "없음"} · 음성 인식: ${voice?.sttState ?: "없음"}")
                append("\n내비: ${c.navi?.state ?: "없음"}" + (c.navi?.address?.let { " · $it" } ?: "") + (c.navi?.latLng()?.let { " (%.4f, %.4f)".format(it.first, it.second) } ?: ""))
                append("\n위치(FusedLocation): ${c.location?.state ?: "없음"}" + (c.location?.last?.let { " · %.4f, %.4f · 측정 %s".format(it.latitude, it.longitude, DemoController.hms(it.timestampMillis)) } ?: ""))
                signals?.let { sg -> append("\n문: ${sg.doors} · 좌석: ${sg.seats} · 벨트: ${sg.belts} · 에어컨: ${sg.acOn} · 창문: ${sg.windows} · 와이퍼: ${sg.wiper} · 주행거리: ${sg.odometerKm}") }
                listOfNotNull(signals?.vehicleError, signals?.speedError, signals?.uxError).forEach { append("\n$it") }
            }
            Text(sigText, style = ts(22.sp, C.Mut, lineHeight = 30.sp))
        }
    }
}

@Composable
private fun PanelBtn(text: String, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(R16).background(C.Surf2).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text(text, style = ts(24.sp, C.Fg, FontWeight.SemiBold))
    }
}

/** 진입(로딩) 화면을 보여 주는 시간(첫 화면을 그린 뒤부터). */
const val INTRO_MS = 1_800L
