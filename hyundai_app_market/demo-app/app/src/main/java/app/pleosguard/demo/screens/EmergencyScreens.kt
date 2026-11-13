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
import app.pleosguard.demo.logic.RulesLab
import app.pleosguard.demo.logic.VoiceAnswer
import app.pleosguard.demo.ui.*

// ---------------------------------------------------------------- 상황 선택(주행 중 5개)
@Composable
fun SituationScreen(c: DemoController) = ScreenFrame {
    AppHeader { c.demoPanel = true }
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("어떤 상황인가요", style = ts(32.sp, weight = FontWeight.Bold), modifier = Modifier.weight(1f)); DriveChip(c)
    }
    Text("급한 상황은 누르면 바로 안내를 시작합니다", style = Small)
    Column(Modifier.weight(1f).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SitCard("안 멈춰요", "감속 실패 대처", Gi.OctagonAlert, true, Modifier.weight(1f)) { c.startGuide(Screen.DECEL_GUIDE) }
        SitCard("저절로 빨라져요", "원치 않는 가속 안내", Gi.TriangleAlert, true, Modifier.weight(1f)) { c.startGuide(Screen.ACCEL_GUIDE) }
        SitCard("사고가 났어요", "10초 뒤 보호자에게 알림", Gi.Crash, true, Modifier.weight(1f)) { c.startCountdown(IncidentKind.ACCIDENT, testInput = false) }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SitCard("차체 떨림", "기록만 합니다", Gi.Activity, false, Modifier.weight(1f).fillMaxHeight()) { c.recordNow(); c.go(Screen.HOME); c.showToast("차체 떨림을 기록했습니다. 보호자에게 자동으로 보내지 않습니다") }
            SitCard("타는 냄새", "기록만 합니다", Gi.Flame, false, Modifier.weight(1f).fillMaxHeight()) { c.recordNow(); c.go(Screen.HOME); c.showToast("타는 냄새를 기록했습니다. 보호자에게 자동으로 보내지 않습니다") }
        }
    }
    BottomButtons(Btn("고른 증상 기록", Gi.History) { c.go(Screen.HOME) }, Btn("지금 도움 요청", Gi.Send) { c.openRecipients(IncidentKind.HELP) }, RightKind.HELP_DARK, guardKey = Screen.SITUATION)
}

@Composable
private fun SitCard(title: String, sub: String, icon: Gi, urgent: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().clip(R24).background(if (urgent) C.ECard else C.Surf2).border(2.dp, if (urgent) C.Red.copy(alpha = 0.7f) else C.Line, R24)
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        GIcon(icon, if (urgent) C.Red else C.Fg, 52.dp); Spacer(Modifier.width(24.dp))
        Column { Text(title, style = ts(34.sp, weight = FontWeight.Bold)); Text(sub, style = ts(22.sp, if (urgent) C.EPink else C.Mut)) }
    }
}

// ---------------------------------------------------------------- 확인 질문
@Composable
fun ConfirmScreen(c: DemoController, listening: Boolean, partial: String, sttState: String) = ScreenFrame {
    AppHeader { c.demoPanel = true }
    Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        DriveChip(c); Spacer(Modifier.width(12.dp)); StatusChip(Gi.Volume, "음성으로도 묻습니다", C.Fg)
    }
    Column(Modifier.padding(top = 24.dp).fillMaxWidth().weight(1f).clip(R24).background(C.ECard).border(3.dp, C.Red, R24).padding(32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { GIcon(Gi.CircleHelp, C.Red, 48.dp); Spacer(Modifier.width(16.dp)); Text("확인 질문", style = ts(26.sp, C.EPink, FontWeight.SemiBold)) }
        Spacer(Modifier.height(20.dp))
        Text("차가 멈추지\n않나요?", style = ts(66.sp, C.Fg, FontWeight.Bold, 84.sp))
        HorizontalDivider(Modifier.padding(vertical = 28.dp), color = C.EPink.copy(alpha = 0.25f))
        Row(verticalAlignment = Alignment.Top) {
            GIcon(Gi.Pedal, C.Amber, 40.dp); Spacer(Modifier.width(16.dp))
            Column {
                Text(c.promptReason, style = ts(28.sp, weight = FontWeight.SemiBold, lineHeight = 38.sp))
                if (c.promptSpeedTest) { Spacer(Modifier.height(8.dp)); TestBadge("차속 · 시험 입력") }
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.Top) {
            GIcon(Gi.Mic, if (listening) C.Mint else C.EPink, 40.dp); Spacer(Modifier.width(16.dp))
            Column {
                Text("버튼 없이 말로 답해도 됩니다\n\"안 멈춰요\" · \"괜찮아요\" (8초 듣기)", style = ts(28.sp, weight = FontWeight.SemiBold, lineHeight = 38.sp))
                val heard = c.promptAnswerHeard
                val line = when { listening -> if (partial.isEmpty()) "듣는 중 · 말씀하세요" else "듣는 중 · $partial"; heard != null -> "들은 말: $heard"; else -> null }
                if (line != null) Text(line, style = ts(24.sp, C.Mint, FontWeight.SemiBold), modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
    Text("\"안 멈춰요\"라고 답하면 감속 실패 대처를 바로 시작합니다.\n답이 없으면 8초 뒤 질문을 닫고 기록만 남깁니다", style = ts(22.sp, C.Mut, lineHeight = 30.sp), modifier = Modifier.padding(top = 20.dp))
    BottomButtons(Btn("괜찮아요", Gi.CircleCheck, "질문 닫기") { c.answerPrompt(VoiceAnswer.OK, byVoice = false) },
        Btn("안 멈춰요", Gi.OctagonAlert, "감속 실패 대처") { c.answerPrompt(VoiceAnswer.NOT_STOPPING, byVoice = false) },
        RightKind.EMERGENCY, tall = true, guardKey = Screen.CONFIRM)
}

// ---------------------------------------------------------------- 감속 실패 대처 · 원치 않는 가속 안내
@Composable
fun GuideScreen(c: DemoController, decel: Boolean) = ScreenFrame(bg = C.EBg) {
    EmergencyBand("긴급 안내", if (decel) "감속 실패 대처" else "원치 않는 가속 안내") { c.demoPanel = true }
    Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        DriveChip(c); Spacer(Modifier.width(12.dp)); StatusChip(Gi.Volume, "음성 안내 중", C.Fg, bg = C.ECard)
    }
    Text("앞을 보세요\n음성 안내를 따라 주세요", style = ts(42.sp, C.Fg, FontWeight.Bold, 56.sp), modifier = Modifier.padding(vertical = 20.dp))
    SentenceSlot(c.guideSentence())
    Row(Modifier.weight(1f).padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Image(painterResource(R.drawable.avante_rear), "디 올 뉴 아반떼 뒷모습", Modifier.weight(1f).fillMaxHeight().clip(R24), contentScale = ContentScale.Crop,
            alignment = androidx.compose.ui.BiasAlignment(0.28f, 0f)) // 차(가로 440~1040px)가 가운데 오도록 자릅니다
        PedalPanel(c.sceneIndex, Modifier.weight(1f).fillMaxHeight())
    }
    if (decel) BottomButtons(Btn("차가 멈췄어요", Gi.CircleCheck, "안내 끝내기") { c.endGuide() }, Btn("도움 요청", Gi.Send, "받는 사람 확인") { c.openRecipients(IncidentKind.DECEL) },
        RightKind.EMERGENCY, emergency = true, tall = true, guardKey = Screen.DECEL_GUIDE)
    else BottomButtons(Btn("괜찮아요", Gi.CircleCheck, "안내 끝내기") { c.endGuide() }, Btn("안 멈춰요", Gi.OctagonAlert, "감속 실패 대처") { c.startGuide(Screen.DECEL_GUIDE) },
        RightKind.EMERGENCY, emergency = true, tall = true, guardKey = Screen.ACCEL_GUIDE)
}

/**
 * 페달 그림·설명 애니메이션(긴급 안전 예외, 사용자 결정 2026-11-13).
 * 장면 3개가 음성 단계와 함께 바뀌는 단계형입니다(장면마다 최소 1,000ms, 부드러운 전환·반복 없음).
 * 그림은 AI 설명 그림 시안이며, 동작 내용은 검토된 취급설명서 문장을 따라 확정합니다.
 */
@Composable
fun PedalPanel(scene: Int, modifier: Modifier) {
    val img: ImageBitmap = ImageBitmap.imageResource(R.drawable.pedal_detail)
    Box(modifier.clip(R24)) {
        Canvas(Modifier.fillMaxSize()) {
            // 원본 1280×720에서 가운데(cx≈739)를 패널 비율로 자릅니다(시안 생성기 pedal_panel과 같은 방식).
            val sh = img.height.toFloat(); val sw = minOf(img.width.toFloat(), sh * size.width / size.height)
            val left = (739f - sw / 2).coerceIn(0f, img.width - sw)
            val k = size.height / sh
            drawImage(img, IntOffset(left.toInt(), 0), IntSize(sw.toInt(), sh.toInt()), dstSize = IntSize(size.width.toInt(), size.height.toInt()))
            val brake = Offset((601f - left) * k, 262f * k)
            when (scene) {
                0 -> { // 1단계: 브레이크 페달 위치를 비춥니다
                    val r = 120f * k
                    val dim = Path().apply {
                        fillType = PathFillType.EvenOdd
                        addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
                        addOval(androidx.compose.ui.geometry.Rect(brake, r))
                    }
                    drawPath(dim, Color.Black.copy(alpha = 0.62f))
                    drawCircle(C.EPink, r + 4f, brake, style = Stroke(6f))
                }
                1 -> Unit // 2단계: 그림 그대로(발을 옮기는 화살표)
                else -> { // 3단계: 페달을 누르는 방향 표시
                    for (i in 0 until 3) {
                        val y = brake.y + (40f + i * 46f) * k
                        val p = Path().apply { moveTo(brake.x - 60f * k, y); lineTo(brake.x, y + 34f * k); lineTo(brake.x + 60f * k, y) }
                        drawPath(p, C.EPink.copy(alpha = 1f - i * 0.25f), style = Stroke(12f * k + 4f, cap = StrokeCap.Round))
                    }
                }
            }
        }
        Box(Modifier.padding(12.dp).clip(Pill).background(Color(0xCC14090E)).padding(horizontal = 12.dp, vertical = 2.dp).align(Alignment.TopStart)) {
            Text("AI 설명 그림", style = ts(22.sp, C.EDraft, FontWeight.Medium))
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(12.dp).fillMaxWidth().clip(R16).background(Color(0xFF14090E)).border(2.dp, C.EPink, R16)
            .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(20.dp)) { drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2); lineTo(0f, size.height); close() }, C.EPink) }
            Spacer(Modifier.width(10.dp))
            Text("설명 애니메이션 ${scene + 1}/${RulesLab.KEY_SCENES}", style = ts(22.sp, C.Fg, FontWeight.SemiBold), modifier = Modifier.weight(1f))
            for (j in 0 until RulesLab.KEY_SCENES) { Box(Modifier.padding(start = 8.dp).size(12.dp).clip(Pill).background(if (j == scene) C.EPink else Color(0xFF5A3A44))) }
        }
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
                Text("알림톡, 안 되면 문자", style = ts(22.sp, C.Mut))
            }
        }
    }
    Panel(Modifier.padding(top = 16.dp), bg = C.ECard, pad = 20.dp) {
        Text("보낼 내용", style = ts(24.sp, weight = FontWeight.SemiBold))
        val det = inc?.detectedWall ?: 0L
        ContentRow("감지", "${inc?.kind?.label ?: "-"} · ${DemoController.hms(det)}", inc?.kind == IncidentKind.AIRBAG)
        ContentRow("위치", "측정 시각 ${DemoController.hms(det - 2_000)}", true)
        ContentRow("차", "탑승 1명(운전석) · 안전벨트 착용 · 문 닫힘", true)
    }
    Spacer(Modifier.weight(1f))
    Text("119에 자동 신고하지 않습니다. 필요하면 119에 직접 신고하세요", style = ts(22.sp, C.Mut))
    Text("이 데모는 서버 없이 모의 발송만 합니다. 시험 입력이 섞인 사건은 실제로 보내지 않습니다", style = ts(22.sp, C.EPink))
    if (countdown) BottomButtons(Btn("괜찮아요", Gi.CircleCheck, "보내지 않음") { c.cancelSend() }, Btn("지금 보내기", Gi.Send, "기다리지 않고 바로") { c.send() },
        RightKind.EMERGENCY, emergency = true, tall = true, guardKey = Screen.COUNTDOWN)
    else BottomButtons(Btn("보내지 않기", Gi.CircleCheck, "취소") { c.cancelSend() }, Btn("보내기", Gi.Send, "받는 사람 확인함") { c.send() },
        RightKind.EMERGENCY, emergency = true, tall = true, guardKey = Screen.RECIPIENTS)
}

@Composable
private fun ContentRow(k: String, v: String, test: Boolean) {
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(k, style = ts(24.sp, C.Mut), modifier = Modifier.width(72.dp))
        Text(v, style = ts(24.sp), modifier = Modifier.weight(1f), maxLines = 1)
        if (test) TestBadge()
    }
}

