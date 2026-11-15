package app.pleosguard.demo.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pleosguard.demo.DemoController
import app.pleosguard.demo.IncidentKind
import app.pleosguard.demo.R
import app.pleosguard.demo.Screen
import app.pleosguard.demo.SnapText
import app.pleosguard.demo.logic.RulesLab
import app.pleosguard.demo.ui.*

// ---------------------------------------------------------------- 상황 선택(주행 중 5개)
@Composable
fun SituationScreen(c: DemoController) = ScreenFrame {
    AppHeader { c.demoPanel = true }
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("어떤 상황인가요", style = ts(32.sp, weight = FontWeight.Bold), modifier = Modifier.weight(1f)); DriveChip(c)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.OctagonAlert, C.Red, 26.dp); Spacer(Modifier.width(8.dp)); Text("누르면 바로 안내", style = ts(22.sp, C.Mut))
    }
    Column(Modifier.weight(1f).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SitCard("사고가 났어요", "10초 뒤 보호자에게 알림", Gi.Crash, true, Modifier.weight(1f)) { c.startCountdown(IncidentKind.ACCIDENT, testInput = false) }
        // 주행 중: 누르면 그 순간을 기록하고 한 줄 안전 안내를 소리로 읽습니다. 정차하면 같은 안내 화면에 확인 순서가 나옵니다.
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SitCard("차체 떨림", "기록 + 음성 안내", Gi.Activity, false, Modifier.weight(1f).fillMaxHeight()) { c.quickGuide("차체 떨림") }
            SitCard("타는 냄새", "기록 + 음성 안내", Gi.Flame, false, Modifier.weight(1f).fillMaxHeight()) { c.quickGuide("타는 냄새") }
        }
    }
    BottomButtons(Btn("홈으로", Gi.Home) { c.go(Screen.HOME) }, Btn("지금 도움 요청", Gi.Send, "보호자에게 알림") { c.openRecipients(IncidentKind.HELP) }, RightKind.HELP_DARK, guardKey = Screen.SITUATION)
}

@Composable
private fun SitCard(title: String, sub: String, icon: Gi, urgent: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clip(R24).background(if (urgent) C.ECard else C.Surf2).border(2.dp, if (urgent) C.Red.copy(alpha = 0.7f) else C.Line, R24)
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        GIcon(icon, if (urgent) C.Red else C.Fg, 52.dp); Spacer(Modifier.width(24.dp))
        Column { Text(title, style = ts(34.sp, weight = FontWeight.Bold)); Text(sub, style = ts(22.sp, if (urgent) C.EPink else C.Mut)) }
    }
}

// ---------------------------------------------------------------- 발송 확인(10초) · 받는 사람 확인
@Composable
fun SendConfirmScreen(c: DemoController, countdown: Boolean) = ScreenFrame(bg = C.EBg) {
    c.tick // 남은 초
    val inc = c.incident
    EmergencyBand("보호자 알림", if (countdown) "발송 확인(10초)" else "받는 사람 확인") { c.demoPanel = true }
    Row(Modifier.padding(top = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(when (inc?.kind) { IncidentKind.AIRBAG -> "에어백 전개 신호를 받았습니다"; IncidentKind.ACCIDENT -> "\"사고가 났어요\"를 골랐습니다"; else -> "보호자에게 도움을 요청합니다" },
            style = ts(30.sp, weight = FontWeight.SemiBold))
        if (inc?.kind == IncidentKind.AIRBAG) { Spacer(Modifier.width(12.dp)); TestBadge() }
    }
    if (countdown) {
        Row(Modifier.fillMaxWidth().height(180.dp).clip(R24).background(C.ECard).padding(horizontal = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            // 남은 초는 숫자만 바뀝니다. 주행 중 줄어드는 고리는 그리지 않습니다(ui-tokens countdown_ring_while_driving: false).
            Text("${c.countdownLeft()}", style = ts(120.sp, C.Fg, FontWeight.Bold), modifier = Modifier.width(220.dp))
            Text("초 뒤 보냅니다", style = ts(40.sp, C.Fg, FontWeight.Bold))
        }
    }
    Panel(Modifier.padding(top = 16.dp), bg = C.ECard, pad = 20.dp) {
        Text("받는 사람", style = ts(24.sp, weight = FontWeight.SemiBold))
        listOf("보호자 1", "보호자 2").forEach { who ->
            Row(Modifier.padding(top = 12.dp).fillMaxWidth().clip(R16).background(Color(0xFF3A1E28)).padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                GIcon(Gi.Users, C.Purple, 36.dp); Spacer(Modifier.width(14.dp))
                Text(who, style = ts(28.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                GIcon(Gi.Message, C.Mut, 26.dp); Spacer(Modifier.width(8.dp)); Text("알림톡 → 문자", style = ts(22.sp, C.Mut))
            }
        }
    }
    Panel(Modifier.padding(top = 16.dp), bg = C.ECard, pad = 20.dp) {
        Text("보낼 내용", style = ts(24.sp, weight = FontWeight.SemiBold))
        val det = inc?.detectedWall ?: 0L
        ContentRow("감지", "${inc?.kind?.label ?: "-"} · ${DemoController.hms(det)}", inc?.kind == IncidentKind.AIRBAG)
        ContentRow("위치", inc?.let { SnapText.locTime(it.snap, it.detectedWall) } ?: "-", false)
        ContentRow("차", inc?.let { SnapText.car(it.snap) } ?: "-", false)
    }
    Spacer(Modifier.weight(1f))
    // 119 안내는 안전에 필요한 문장이라 짧게 남기고, 모의 발송은 꼬리표로(2026-11-14 아이콘 조사 규칙 5 · 14).
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.Phone, C.Mut, 28.dp); Spacer(Modifier.width(10.dp))
        Text("119 자동 신고 없음 · 필요하면 직접 신고", style = ts(22.sp, C.Mut), modifier = Modifier.weight(1f))
        MockBadge("모의 발송 · 서버 없음")
    }
    if (countdown) BottomButtons(Btn("괜찮아요", Gi.CircleCheck, "보내지 않음") { c.cancelSend() }, Btn("지금 보내기", Gi.Send, "기다리지 않고 바로") { c.send() },
        RightKind.EMERGENCY, emergency = true, tall = true, guardKey = Screen.COUNTDOWN)
    else BottomButtons(Btn("보내지 않기", Gi.CircleCheck, "취소") { c.cancelSend() }, Btn("보내기", Gi.Send, "받는 사람 확인함") { c.send() },
        RightKind.EMERGENCY, emergency = true, tall = true, guardKey = Screen.RECIPIENTS)
}

@Composable
private fun ContentRow(k: String, v: String, test: Boolean) {
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(k, style = ts(24.sp, C.Mut), modifier = Modifier.width(72.dp))
        Text(v, style = ts(24.sp), modifier = Modifier.weight(1f), maxLines = 2)
        if (test) TestBadge()
    }
}

