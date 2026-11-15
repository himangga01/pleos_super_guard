package app.pleosguard.demo.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pleosguard.demo.AccStep
import app.pleosguard.demo.DemoController
import app.pleosguard.demo.IncidentKind
import app.pleosguard.demo.Screen
import app.pleosguard.demo.SnapText
import app.pleosguard.demo.logic.AccidentAdvice
import app.pleosguard.demo.logic.BodyState
import app.pleosguard.demo.logic.Insurer
import app.pleosguard.demo.logic.Insurers
import app.pleosguard.demo.ui.*

/*
 * 사고 대처 안내(2026-11-14 사용자 요청). 보호자에게 알린 뒤 이어지는 긴급 화면입니다(도크 없음, 붉은 띠).
 * 1 사고가 맞나요?(사고 발생 시간 · 장소) → 2 2차 사고 주의 + 내 보험사 긴급 사고 접수 번호, "사고 접수 연락을 할까요?"
 * (보험사를 모르면 보험사별 번호 목록) → 3 연결(모의, 상담원에게 알려 줄 내용) → 4 접수 뒤 안내(몸 상태 · 2차 사고 · 현장 촬영 · 차 옮기기).
 * 답은 화면 단추 또는 질문 바로 뒤 8초 동안의 말로 합니다.
 */

@Composable
fun AccidentScreen(c: DemoController) = ScreenFrame(bg = C.EBg) {
    // 화면 전체를 0.1초마다 다시 그리지 않습니다(연결 시간만 [CallingStep]에서 초마다 바뀜).
    EmergencyBand("사고 대처 안내", "${c.accStep.ordinal + 1}/4 ${c.accStep.label}") { c.demoPanel = true }
    StepBar(c.accStep, Modifier.padding(top = 14.dp))
    when (c.accStep) {
        AccStep.CONFIRM -> ConfirmStep(c)
        AccStep.INSURER -> InsurerStep(c)
        AccStep.CALLING -> CallingStep(c)
        AccStep.AFTER -> AfterStep(c)
    }
}

/** 단계 막대: 지난 단계는 민트 체크, 지금 단계는 채운 칸, 남은 단계는 흐리게. */
@Composable
private fun StepBar(now: AccStep, modifier: Modifier) {
    val icons = mapOf(AccStep.CONFIRM to Gi.CircleHelp, AccStep.INSURER to Gi.Receipt, AccStep.CALLING to Gi.Phone, AccStep.AFTER to Gi.ShieldCheck)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AccStep.entries.forEach { s ->
            val done = s.ordinal < now.ordinal; val on = s == now
            Row(Modifier.weight(1f).height(56.dp).clip(Pill).background(if (on) C.EBand else C.ECard).border(1.5.dp, if (on) C.EPink else if (done) C.Mint.copy(alpha = 0.6f) else C.ENeu, Pill)
                .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                GIcon(if (done) Gi.CheckCheck else icons.getValue(s), if (done) C.Mint else if (on) C.Fg else C.Dim, 28.dp)
                Spacer(Modifier.width(8.dp))
                Text(s.label, style = ts(22.sp, if (on) C.Fg else if (done) C.Mint else C.Dim, FontWeight.SemiBold), maxLines = 1)
            }
        }
    }
}

/** 사고 발생 시간 · 장소(차의 현재 위치 기준 주소)와 보호자 알림 한 줄. [fill]이면 받은 높이를 세 줄이 나눠 갖고 글자 · 아이콘도 그 높이에 비례합니다. */
@Composable
private fun FactsCard(c: DemoController, modifier: Modifier = Modifier, big: Boolean = true, fill: Boolean = false) {
    val inc = c.incident
    val s = inc?.snap
    val place = listOfNotNull(if (s?.address != null) "내비 현재 위치 기준" else null, s?.lat?.let { la -> s.lng?.let { lo -> "%.4f, %.4f".format(la, lo) } }).joinToString(" · ").ifBlank { null }
    val sent = inc?.sentWall
    val rows = listOf(
        Triple(Gi.Clock, "사고 발생 시간", c.accTime() to null),
        Triple(Gi.MapPin, "사고 발생 장소", c.accPlace() to place),
        Triple(Gi.Users, "보호자 알림", (if (sent != null) "2명에게 보냄 · ${DemoController.hms(sent)}" else "보내지 않음") to "모의 발송"),
    )
    if (!fill) Panel(modifier, bg = C.ECard, pad = 22.dp) {
        val z = if (big) FactSize(40.dp, 24f, 32f, 190.dp) else FactSize(32.dp, 22f, 26f, 170.dp)
        rows.forEach { (ic, k, v) -> FactRow(ic, k, v.first, v.second, z, Modifier.padding(top = 12.dp)) }
    } else BoxWithConstraints(modifier.fillMaxWidth().clip(R24).background(C.ECard)) {
        val stacked = maxWidth < 1000.dp
        val rowH = (maxHeight - 48.dp) / 3
        // 값 글자: 줄 높이에 비례하되, 가장 긴 값(약 13글자 폭)이 한 줄에 들어가도록 폭으로도 제한합니다.
        val byHeight = rowH.value * (if (stacked) 0.24f else 0.30f)
        val key0 = (byHeight * 0.6f).coerceIn(22f, 32f)
        val textW = maxWidth.value - 48f - byHeight * 1.35f - 16f - (if (stacked) 0f else key0 * 7.6f)
        val value = minOf(byHeight, textW / 13f).coerceIn(28f, 54f)
        val key = (value * 0.6f).coerceIn(22f, 32f)
        val z = FactSize((value * 1.35f).dp, key, value, (key * 7.6f).dp, stacked)
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.SpaceEvenly) {
            rows.forEach { (ic, k, v) -> FactRow(ic, k, v.first, v.second, z, Modifier) }
        }
    }
}

private class FactSize(val icon: Dp, val key: Float, val value: Float, val keyWidth: Dp, val stacked: Boolean = false)

@Composable
private fun FactRow(icon: Gi, k: String, v: String, sub: String?, z: FactSize, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        GIcon(icon, C.EPink, z.icon); Spacer(Modifier.width(16.dp))
        if (!z.stacked) Text(k, style = ts(z.key.sp, C.EPink, FontWeight.Medium), modifier = Modifier.width(z.keyWidth))
        Column(Modifier.weight(1f)) {
            if (z.stacked) Text(k, style = ts(z.key.sp, C.EPink, FontWeight.Medium), maxLines = 1)
            Text(v, style = ts(z.value.sp, C.Fg, FontWeight.Bold, (z.value * 1.25f).sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = ts((z.key * 0.86f).coerceAtLeast(20f).sp, C.EDraft), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 말로 답하기 표시: 듣는 중이면 민트, 아니면 회색 안내. 알아듣지 못한 말은 그대로 보입니다. */
@Composable
private fun VoiceLine(c: DemoController, hint: String) {
    Row(Modifier.padding(top = 14.dp).fillMaxWidth().clip(R16).background(if (c.accListening) C.OnMint else C.ECard).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.Mic, if (c.accListening) C.Mint else C.EPink, 32.dp); Spacer(Modifier.width(14.dp))
        Text(if (c.accListening) "듣는 중 · $hint" else "말로 답할 수 있어요 · $hint · 화면을 눌러도 돼요",
            style = ts(22.sp, if (c.accListening) C.Mint else C.EDraft, FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        c.accHeard?.let { Text("들은 말: $it", style = ts(20.sp, C.EDraft), maxLines = 1) }
    }
}

// ---------------------------------------------------------------- 1 사고가 맞나요?
@Composable
private fun ColumnScope.ConfirmStep(c: DemoController) {
    Text("사고가 맞나요?", style = ts(52.sp, C.Fg, FontWeight.Bold), modifier = Modifier.padding(top = 22.dp))
    FactsCard(c, Modifier.padding(top = 16.dp).weight(1f), fill = true)
    VoiceLine(c, "'예' 또는 '아니요'")
    BottomButtons(Btn("아니요", Gi.CircleCheck, "사고 아님") { c.accidentNo() }, Btn("예, 사고예요", Gi.OctagonAlert, "다음: 보험 접수") { c.accidentYes() },
        RightKind.EMERGENCY, emergency = true, tall = true, guardKey = AccStep.CONFIRM)
}

// ---------------------------------------------------------------- 2 2차 사고 주의 + 보험 접수
@Composable
private fun ColumnScope.InsurerStep(c: DemoController) {
    CautionBanner(Modifier.padding(top = 16.dp))
    val ins = c.insurer()
    if (ins != null) {
        Panel(Modifier.padding(top = 16.dp), bg = C.ECard, pad = 24.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("운전자분의 보험사", style = ts(24.sp, C.EPink, FontWeight.Medium))
                    Text(ins.name, style = ts(40.sp, C.Fg, FontWeight.Bold))
                }
                Column(Modifier.weight(1.2f)) {
                    Text("긴급 사고 접수", style = ts(24.sp, C.EPink, FontWeight.Medium))
                    Text(ins.phone, style = ts(52.sp, C.Mint, FontWeight.Bold))
                }
            }
            Text("${ins.hint} · 번호 확인 ${Insurers.CHECKED}", style = ts(21.sp, C.EDraft), modifier = Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("사고 접수 연락을 할까요?", style = ts(36.sp, C.Fg, FontWeight.Bold), modifier = Modifier.padding(top = 14.dp))
        }
        FactsCard(c, Modifier.padding(top = 14.dp).weight(1f), fill = true)
        VoiceLine(c, "'예' 또는 '아니요'")
        BottomButtons(Btn("나중에", Gi.Clock, "접수 건너뛰기") { c.skipReport() }, Btn("예, 연결해 주세요", Gi.Phone, "${ins.name} ${ins.phone}") { c.callInsurer(ins) },
            RightKind.EMERGENCY, emergency = true, tall = true, guardKey = AccStep.INSURER)
    } else {
        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            GIcon(Gi.Receipt, C.EPink, 36.dp); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("보험사를 고르면 사고 접수를 연결해요", style = ts(30.sp, C.Fg, FontWeight.Bold))
                Text("내 보험사가 저장돼 있지 않아요 · 보험사별 긴급 사고 접수 번호", style = ts(22.sp, C.EDraft))
            }
        }
        InsurerGrid(c.insurerId, cols = if (LocalWide.current) 4 else 3, Modifier.padding(top = 12.dp).weight(1f), fill = true) { c.callInsurer(it) }
        PublicNumbers(Modifier.padding(top = 12.dp))
        BottomButtons(Btn("보호자 알림 보기", Gi.Users) { c.go(Screen.GUARDIAN) }, Btn("다음: 안전 안내", Gi.ShieldCheck, "접수는 나중에") { c.skipReport() },
            RightKind.EMERGENCY, emergency = true, guardKey = AccStep.INSURER)
    }
}

/** 2차 사고 주의 띠(사용자 요청 문구: "도로의 경우 2차 사고가 발생할 수 있으니 조심하세요"). */
@Composable
private fun CautionBanner(modifier: Modifier) {
    Row(modifier.fillMaxWidth().clip(R24).background(C.OnAmber).border(2.dp, C.Amber, R24).padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.TriangleAlert, C.Amber, 52.dp); Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text("도로라면 2차 사고가 날 수 있어요. 조심하세요", style = ts(30.sp, C.Fg, FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(AccidentAdvice.CAUTION_SUB, style = ts(22.sp, C.Amber, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * 보험사 칸(이름 + 긴급 사고 접수 번호). [selected]는 민트 테두리.
 * [fill]이면 받은 높이를 줄들이 나눠 갖고(96~170dp) 글자 · 아이콘도 칸 높이에 비례합니다. 아니면 96dp 칸을 밀어 봅니다.
 */
@Composable
fun InsurerGrid(selected: String?, cols: Int, modifier: Modifier, fill: Boolean = false, onPick: (Insurer) -> Unit) {
    val rows = Insurers.ALL.chunked(cols)
    BoxWithConstraints(modifier) {
        val rowH = if (fill) ((maxHeight - 10.dp * (rows.size - 1)) / rows.size).coerceIn(96.dp, 170.dp) else 96.dp
        val tileW = (maxWidth.value - 10f * (cols - 1)) / cols
        // 번호 "1588-5114" 9글자(숫자 폭 약 0.58em) + 아이콘(0.9em) + 여백 40dp가 칸 폭에 들어가야 합니다.
        val phone = minOf(rowH.value * 0.25f, (tileW - 44f) / 6.9f).coerceIn(22f, 40f)
        val name = minOf(rowH.value * 0.21f, (tileW - 32f) / 6.4f).coerceIn(20f, 32f)
        Column(if (fill) Modifier else Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { ins ->
                        val on = ins.id == selected
                        Column(Modifier.weight(1f).height(rowH).clip(R16).background(if (on) C.OnMint else C.Surf2).border(if (on) 2.dp else 1.5.dp, if (on) C.Mint else C.Line, R16)
                            .clickable(role = Role.Button) { onPick(ins) }.padding(horizontal = 16.dp).semantics { contentDescription = "${ins.name} ${ins.phone}" },
                            verticalArrangement = Arrangement.Center) {
                            Text(ins.name, style = ts(name.sp, C.Fg, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GIcon(Gi.Phone, C.Mint, (phone * 0.9f).dp); Spacer(Modifier.width(8.dp))
                                Text(ins.phone, style = ts(phone.sp, C.Mint, FontWeight.Bold), maxLines = 1)
                            }
                        }
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun PublicNumbers(modifier: Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AccidentAdvice.PUBLIC.forEach { (label, num) ->
            Row(Modifier.weight(1f).height(76.dp).clip(R16).background(C.ECard).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                GIcon(Gi.Phone, C.EPink, 28.dp); Spacer(Modifier.width(10.dp))
                Column {
                    Text(num, style = ts(26.sp, C.Fg, FontWeight.Bold), maxLines = 1)
                    Text(label, style = ts(19.sp, C.EDraft), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 3 연결(모의)
@Composable
private fun ColumnScope.CallingStep(c: DemoController) {
    val ins = c.accInsurer ?: return
    c.tick // 연결 시간(초) 표시
    val from = c.accCallFrom
    val el = if (from != null) ((c.wallNowPublic() - from) / 1000).coerceAtLeast(0) else 0
    Row(Modifier.padding(top = 18.dp).fillMaxWidth().clip(R24).background(C.ECard).padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(88.dp).clip(Pill).background(C.Mint.copy(alpha = 0.18f)).border(2.dp, C.Mint, Pill), contentAlignment = Alignment.Center) { GIcon(Gi.Phone, C.Mint, 48.dp) }
        Spacer(Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            Text("${ins.name} 연결 중", style = ts(34.sp, C.Fg, FontWeight.Bold))
            Text(ins.phone, style = ts(44.sp, C.Mint, FontWeight.Bold))
            Text(ins.hint, style = ts(21.sp, C.EDraft), maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("%d:%02d".format(el / 60, el % 60), style = ts(36.sp, C.Fg, FontWeight.Bold))
            MockBadge("모의 연결 · 실제 전화 안 함")
        }
    }
    Text("상담원에게 알려 줄 내용", style = ts(28.sp, C.Fg, FontWeight.SemiBold), modifier = Modifier.padding(top = 18.dp))
    FactsCard(c, Modifier.padding(top = 10.dp).weight(1f), fill = true)
    c.incident?.let { inc ->
        Panel(Modifier.padding(top = 12.dp), bg = C.ECard, pad = 22.dp) {
            val z = FactSize(36.dp, 23f, 28f, 180.dp)
            FactRow(Gi.CarFront, "차", "아반떼(CN8)" + if (inc.kind == IncidentKind.AIRBAG) " · 에어백 전개 신호" else "", null, z, Modifier)
            FactRow(Gi.Seat, "탑승 · 벨트", "${SnapText.occupants(inc.snap)} · ${SnapText.belts(inc.snap)}", null, z, Modifier.padding(top = 12.dp))
        }
    }
    BottomButtons(Btn("연결 끊기", Gi.Close, "접수 못 함") { c.hangUp() }, Btn("접수 완료", Gi.CircleCheck, "다음: 몸 상태 · 안전 안내") { c.reportDone() },
        RightKind.EMERGENCY, emergency = true, tall = true, guardKey = AccStep.CALLING)
}

// ---------------------------------------------------------------- 4 접수 뒤 안내
@Composable
private fun ColumnScope.AfterStep(c: DemoController) {
    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        DoneChip(Gi.Users, "보호자 알림", c.incident?.sentWall != null)
        DoneChip(Gi.Receipt, c.accInsurer?.takeIf { c.accReported }?.let { "보험 접수 · ${it.name}" } ?: "보험 접수 안 함", c.accReported)
        DoneChip(Gi.Activity, c.bodyState?.let { "몸 상태 · ${it.label}" } ?: "몸 상태 확인 전", c.bodyState != null)
    }
    if (LocalWide.current) {
        Row(Modifier.padding(top = 14.dp).weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                BodyCard(c, Modifier.weight(1f)); SecondaryCard(c, Modifier.weight(1f))
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                MoveCard(c, Modifier.weight(1f)); PhotoCard(c, Modifier.weight(1f))
            }
        }
    } else Column(Modifier.padding(top = 14.dp).weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BodyCard(c, Modifier); SecondaryCard(c, Modifier); MoveCard(c, Modifier); PhotoCard(c, Modifier)
    }
    BottomButtons(Btn("보호자 알림 보기", Gi.Users) { c.go(Screen.GUARDIAN) }, Btn("안내 끝내기", Gi.Home, "홈으로") { c.finishAccident() },
        RightKind.EMERGENCY, emergency = true, guardKey = AccStep.AFTER)
}

@Composable
private fun DoneChip(icon: Gi, text: String, done: Boolean) {
    Row(Modifier.height(52.dp).clip(Pill).background(C.ECard).border(1.5.dp, if (done) C.Mint.copy(alpha = 0.7f) else C.ENeu, Pill).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GIcon(if (done) Gi.CheckCheck else icon, if (done) C.Mint else C.EDraft, 28.dp); Spacer(Modifier.width(8.dp))
        Text(text, style = ts(22.sp, if (done) C.Mint else C.EDraft, FontWeight.SemiBold), maxLines = 1)
    }
}

/** 안내 카드 틀: 머리(아이콘 · 제목 · 확인 표시) + 내용. 머리를 누르면 확인함으로 표시됩니다. */
@Composable
private fun AdviceCard(c: DemoController, key: String, icon: Gi, tint: Color, title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val done = key in c.accDone
    Column(modifier.fillMaxWidth().clip(R24).background(C.ECard).border(1.5.dp, if (done) C.Mint.copy(alpha = 0.6f) else C.ENeu, R24).padding(20.dp)) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { c.toggleAccCard(key) }, verticalAlignment = Alignment.CenterVertically) {
            GIcon(icon, tint, 40.dp); Spacer(Modifier.width(14.dp))
            Text(title, style = ts(28.sp, C.Fg, FontWeight.Bold), modifier = Modifier.weight(1f), maxLines = 1)
            Box(Modifier.size(44.dp).clip(Pill).background(if (done) C.Mint else Color.Transparent).border(2.dp, if (done) C.Mint else C.ENeu, Pill), contentAlignment = Alignment.Center) {
                if (done) GIcon(Gi.CheckCheck, C.Dark, 26.dp)
            }
        }
        content()
    }
}

@Composable
private fun Bullet(text: String, size: Int = 23) {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 11.dp).size(9.dp).clip(Pill).background(C.EPink)); Spacer(Modifier.width(12.dp))
        Text(text, style = ts(size.sp, C.Fg, lineHeight = (size * 1.4f).sp))
    }
}

@Composable
private fun BodyCard(c: DemoController, modifier: Modifier) {
    val b = c.bodyState
    val tint = when (b) { BodyState.SEVERE -> C.Red; BodyState.MINOR -> C.Amber; BodyState.OK -> C.Mint; null -> C.EPink }
    AdviceCard(c, "body", Gi.Activity, tint, "몸 상태 확인", modifier) {
        Text("다친 곳이 있나요?", style = ts(26.sp, C.Fg, FontWeight.SemiBold), modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BodyState.entries.forEach { s ->
                val on = b == s
                val col = when (s) { BodyState.SEVERE -> C.Red; BodyState.MINOR -> C.Amber; BodyState.OK -> C.Mint }
                Box(Modifier.weight(1f).height(64.dp).clip(R16).background(if (on) col.copy(alpha = 0.22f) else C.ENeu).border(if (on) 2.dp else 1.dp, if (on) col else C.ENeu, R16)
                    .clickable(role = Role.RadioButton) { c.setBody(s) }, contentAlignment = Alignment.Center) {
                    Text(s.label, style = ts(23.sp, if (on) col else C.Fg, FontWeight.SemiBold), maxLines = 1)
                }
            }
        }
        val occ = c.incident?.snap?.let { SnapText.occupants(it) }
        when (b) {
            BodyState.SEVERE -> {
                Bullet("움직이지 말고 119를 부르세요. 피가 나면 깨끗한 천으로 눌러 주세요")
                Row(Modifier.padding(top = 10.dp).fillMaxWidth().height(64.dp).clip(R16).background(C.Red).clickable(role = Role.Button) { c.call119() }.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    GIcon(Gi.Phone, C.Dark, 32.dp); Spacer(Modifier.width(10.dp)); Text("119 연결", style = ts(26.sp, C.Dark, FontWeight.Bold), modifier = Modifier.weight(1f))
                    Text("모의 연결", style = ts(20.sp, C.Dark, FontWeight.Medium))
                }
            }
            BodyState.MINOR -> Bullet("사고 뒤 늦게 아플 수 있어요. 병원 진료를 받아 두세요")
            BodyState.OK -> Bullet("지금 괜찮아도 며칠 동안 몸 상태를 살피세요")
            null -> Row(Modifier.padding(top = 10.dp).fillMaxWidth().clip(R16).background(if (c.accListening) C.OnMint else C.ENeu).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                GIcon(Gi.Mic, if (c.accListening) C.Mint else C.EPink, 28.dp); Spacer(Modifier.width(10.dp))
                Text((if (c.accListening) "듣는 중 · " else "말로 답해도 돼요 · ") + "'괜찮아요' · '조금 아파요' · '많이 다쳤어요'",
                    style = ts(21.sp, if (c.accListening) C.Mint else C.EDraft, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (occ != null && occ != "확인 불가") Bullet("함께 탄 사람도 확인하세요 · 탑승 $occ", 21)
    }
}

/** 2차 사고 조심: 비상등 · 트렁크 · 밖으로 대피 · 스마트폰 신고(한국도로공사 2차 사고 예방 행동요령). */
@Composable
private fun SecondaryCard(c: DemoController, modifier: Modifier) {
    AdviceCard(c, "secondary", Gi.TriangleAlert, C.Amber, "2차 사고 조심", modifier) {
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccidentAdvice.SECONDARY.forEach { (title, sub) ->
                val ic = when (title) { AccidentAdvice.SECONDARY[0].first -> Gi.TriangleAlert; AccidentAdvice.SECONDARY[1].first -> Gi.Trunk; AccidentAdvice.SECONDARY[2].first -> Gi.Navigation; else -> Gi.Phone }
                Column(Modifier.weight(1f).heightIn(min = 120.dp).clip(R16).background(C.ENeu).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    GIcon(ic, C.Amber, 40.dp); Spacer(Modifier.height(6.dp))
                    Text(title, style = ts(22.sp, C.Fg, FontWeight.Bold), textAlign = TextAlign.Center, maxLines = 1)
                    Text(sub, style = ts(18.sp, C.EDraft), textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
        Bullet(AccidentAdvice.SECONDARY_NOTE, 21)
    }
}

@Composable
private fun PhotoCard(c: DemoController, modifier: Modifier) {
    AdviceCard(c, "photo", Gi.Camera, C.Mint, "사고 현장 촬영", modifier) {
        AccidentAdvice.PHOTO.forEach { Bullet(it, 22) }
        c.bookVersion
        val waiting = c.phoneWait?.kind == "photo"
        val noPhone = c.pairedPhone == null
        val got = c.accPhotoCount()
        val col = if (noPhone) C.EDraft else C.Mint
        Row(Modifier.padding(top = 12.dp).fillMaxWidth().height(60.dp).clip(Pill).background(if (waiting) C.OnMint else C.ENeu).border(1.5.dp, col, Pill)
            .clickable(role = Role.Button) { if (waiting) c.cancelPhoneWait() else c.accidentPhoto() }.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            GIcon(Gi.Smartphone, col, 28.dp); Spacer(Modifier.width(10.dp))
            Text(when {
                waiting -> "폰에서 촬영 중 · 누르면 취소"
                noPhone -> "폰 연결 전 · 폰 카메라로 직접 찍으세요"
                got > 0 -> "폰으로 한 장 더 찍기"
                else -> "폰으로 사진 찍기 · 사고 기록에 붙임"
            }, style = ts(22.sp, col, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            // 받은 사진 수는 단추 안 오른쪽에 둡니다(넓은 창 2×2 카드는 높이가 같아 줄을 늘리면 잘립니다).
            if (got > 0) {
                Spacer(Modifier.width(10.dp))
                GIcon(Gi.CircleCheck, C.Mint, 24.dp); Spacer(Modifier.width(6.dp))
                Text("받은 사진 ${got}장", style = ts(21.sp, C.Mint, FontWeight.SemiBold), maxLines = 1)
            }
        }
    }
}

@Composable
private fun MoveCard(c: DemoController, modifier: Modifier) {
    AdviceCard(c, "move", Gi.CarFront, C.Mint, "차를 안전한 곳으로", modifier) {
        AccidentAdvice.MOVE.forEach { Bullet(it, 22) }
    }
}

/** 보험사 고르기 창(처음 설정 · 안심 화면). */
@Composable
fun InsurerPickerOverlay(c: DemoController) {
    val first = !c.insurerAsked
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth(if (LocalWide.current) 0.72f else 0.92f).fillMaxHeight(0.86f).clip(R24).background(C.Surf).border(2.dp, C.Line, R24).padding(28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GIcon(Gi.ShieldCheck, C.Purple, 44.dp); Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    if (first) Text("처음 설정", style = ts(22.sp, C.Purple, FontWeight.SemiBold))
                    Text("내 자동차 보험사", style = ts(34.sp, C.Fg, FontWeight.Bold))
                }
            }
            Text("사고가 나면 이 보험사의 긴급 사고 접수 번호를 바로 알려 드리고, 연결할지 묻습니다. 이 차에만 저장됩니다.",
                style = ts(22.sp, C.Mut, lineHeight = 31.sp), modifier = Modifier.padding(top = 12.dp))
            InsurerGrid(c.insurerDraft, cols = if (LocalWide.current) 4 else 2, Modifier.padding(top = 16.dp).weight(1f)) { c.insurerDraft = it.id }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                GIcon(Gi.Info, C.Dim, 24.dp); Spacer(Modifier.width(8.dp))
                Text("번호 확인 ${Insurers.CHECKED} · 바뀔 수 있으니 보험 증권의 번호를 함께 확인하세요", style = ts(20.sp, C.Dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BigBtn(Btn(if (first) "나중에 하기" else "닫기", Gi.Clock) { if (first) c.skipInsurerSetup() else c.insurerPicker = false },
                    Modifier.weight(1f).height(84.dp), C.Surf2, C.Fg, iconTint = C.Mut)
                val ok = c.insurerDraft != null
                BigBtn(Btn("저장", Gi.CircleCheck, Insurers.byId(c.insurerDraft)?.name ?: "보험사를 고르세요") { if (ok) c.saveInsurer(c.insurerDraft) else c.showToast("보험사를 먼저 고르세요") },
                    Modifier.weight(1f).height(84.dp), if (ok) C.Mint else C.Surf2, if (ok) C.Dark else C.Mut)
            }
        }
    }
}

/** 안심 화면의 "내 보험사" 줄. */
@Composable
fun InsurerRow(c: DemoController, top: Dp) {
    val ins = c.insurer()
    Row(Modifier.padding(top = top).fillMaxWidth().clip(R16).background(C.Surf).border(1.5.dp, C.Line, R16).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.Receipt, C.Purple, 34.dp); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("내 보험사 · 사고 때 접수 번호 안내", style = ts(20.sp, C.Mut, FontWeight.Medium), maxLines = 1)
            Text(ins?.let { "${it.name} · ${it.phone}" } ?: "정하지 않음 · 사고 때 보험사별 번호 목록", style = ts(24.sp, C.Fg, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.height(56.dp).clip(Pill).background(C.Surf2).border(1.5.dp, C.Purple, Pill).clickable(role = Role.Button) { c.openInsurerPicker() }.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            GIcon(Gi.Pencil, C.Purple, 26.dp); Spacer(Modifier.width(8.dp)); Text(if (ins == null) "정하기" else "바꾸기", style = ts(22.sp, C.Purple, FontWeight.SemiBold))
        }
    }
}
