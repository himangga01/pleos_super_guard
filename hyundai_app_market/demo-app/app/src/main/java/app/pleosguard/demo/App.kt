package app.pleosguard.demo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        when (c.screen) {
            Screen.HOME -> if (c.driving) HomeDriving(c, listening, partial) else HomeParked(c)
            Screen.WARN_FIND -> WarningFind(c)
            Screen.WARN_RESULT -> WarningResult(c)
            Screen.RECORD -> RecordScreen(c, partial, listening)
            Screen.GUARDIAN -> GuardianScreen(c)
            Screen.SERVICE -> ServiceScreen(c)
            Screen.SITUATION -> SituationScreen(c)
            Screen.CONFIRM -> ConfirmScreen(c, listening, partial, voice?.sttState ?: "음성 인식 없음")
            Screen.ACCEL_GUIDE -> GuideScreen(c, decel = false)
            Screen.DECEL_GUIDE -> GuideScreen(c, decel = true)
            Screen.RECIPIENTS -> SendConfirmScreen(c, countdown = false)
            Screen.COUNTDOWN -> SendConfirmScreen(c, countdown = true)
            Screen.SDK_CHECK -> sdkCheck?.invoke() ?: Text("SDK 점검 없음", style = Body)
        }
        Toast(c.toast, Modifier.align(Alignment.BottomCenter).padding(bottom = 140.dp))
        if (c.demoPanel) DemoPanel(c, voice, signals)
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
            PanelBtn("센서 조합 시험: 차속 60km 유지 + 브레이크 1.5초 → 확인 질문") { c.simulateSensorCombination() }
            PanelBtn("에어백 전개 신호(운전석) 시험 입력 → 발송 확인 10초") { c.startCountdown(IncidentKind.AIRBAG, testInput = true) }
            PanelBtn("시각: " + if (c.clockOffsetMs == 0L) "실제 시각 (누르면 대표 사례 21:00:03)" else "대표 사례 시각 사용 중 (누르면 실제 시각)") {
                if (c.clockOffsetMs == 0L) c.setCaseTime("21:00:03") else c.clockOffsetMs = 0L
            }
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
