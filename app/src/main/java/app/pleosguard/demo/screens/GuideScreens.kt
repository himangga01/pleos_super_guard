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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pleosguard.demo.DemoController
import app.pleosguard.demo.IncidentKind
import app.pleosguard.demo.Screen
import app.pleosguard.demo.logic.Guide
import app.pleosguard.demo.logic.GuideAction
import app.pleosguard.demo.logic.GuideLevel
import app.pleosguard.demo.logic.GuideStep
import app.pleosguard.demo.logic.StepMark
import app.pleosguard.demo.ui.*

/*
 * 대처 안내 화면(2026-11-14 사용자 요청).
 * 흐름: 홈 "도움이 필요해요" → 증상 고르기(최대 3개) → 오른쪽 아래 "대처 방법 보기" → 이 화면(증상 조합에 맞춘 확인 순서)
 *       → 해결됐어요(홈) 또는 해결 안 됐어요 → 증상 기록 → 정비소에 도움 요청(정비소 화면 위에 기록 띠 · QR).
 * 주행 중에는 한 줄 안전 안내와 음성만 보이고, 정차하면 같은 화면에 확인 순서가 나옵니다.
 */

/** 증상 이름 → 아이콘(상황 고르기 칸과 같은 그림). */
fun symptomIcon(name: String): Gi = when (name) {
    "차체 떨림" -> Gi.Activity
    "핸들 쏠림" -> Gi.Steering
    "이상한 소음" -> Gi.AudioLines
    "타는 냄새" -> Gi.Flame
    "페달 이상" -> Gi.Pedal
    "타이어 이상" -> Gi.Wheel
    "힘이 없어요" -> Gi.Gauge
    "경고등이 켜졌어요" -> Gi.Engine
    else -> Gi.CircleHelp
}

private fun markIcon(m: StepMark): Gi = when (m) {
    StepMark.STOP -> Gi.OctagonAlert
    StepMark.SMOKE -> Gi.Flame
    StepMark.HOOD -> Gi.CarFront
    StepMark.TIRE -> Gi.Tire
    StepMark.WHEEL -> Gi.Wheel
    StepMark.SPEED -> Gi.Gauge
    StepMark.BRAKE -> Gi.Brake
    StepMark.PEDAL, StepMark.MAT -> Gi.Pedal
    StepMark.ROAD -> Gi.Navigation
    StepMark.WARNING -> Gi.Engine
    StepMark.GEAR -> Gi.Park
    StepMark.RESTART -> Gi.Power
    StepMark.FUEL -> Gi.Droplets
    StepMark.LISTEN -> Gi.AudioLines
    StepMark.LOOK -> Gi.Eye
    StepMark.CARGO -> Gi.Luggage
    StepMark.PHONE -> Gi.Smartphone
    StepMark.SHOP -> Gi.Wrench
    StepMark.HAZARD -> Gi.TriangleAlert
}

private fun markTint(m: StepMark): Color = when (m) {
    StepMark.STOP, StepMark.SMOKE, StepMark.HOOD -> C.Red
    StepMark.WARNING, StepMark.HAZARD -> C.Amber
    else -> C.Mint
}

private fun actionIcon(a: GuideAction): Gi = when (a) {
    GuideAction.WARN_FIND -> Gi.Search
    GuideAction.PHONE_NOISE -> Gi.Mic
    GuideAction.PHONE_VIBRATION -> Gi.Vibrate
    GuideAction.PHONE_WARN_PHOTO -> Gi.Camera
}

private fun actionKind(a: GuideAction): String? = when (a) {
    GuideAction.PHONE_NOISE -> "noise"
    GuideAction.PHONE_VIBRATION -> "vibration"
    GuideAction.PHONE_WARN_PHOTO -> "warning_photo"
    GuideAction.WARN_FIND -> null
}

/** 급한 정도의 색 · 바탕 · 아이콘. 빨강은 바로 세우기, 노랑은 점검, 민트는 확인입니다(빨강 · 노랑은 차의 위험 · 주의에만 씁니다). */
private data class LevelLook(val fg: Color, val bg: Color, val icon: Gi)

private fun levelLook(l: GuideLevel) = when (l) {
    GuideLevel.STOP -> LevelLook(C.Red, C.ECard, Gi.OctagonAlert)
    GuideLevel.SOON -> LevelLook(C.Amber, C.OnAmber, Gi.TriangleAlert)
    GuideLevel.CHECK -> LevelLook(C.Mint, C.OnMint, Gi.CheckCheck)
}

@Composable
fun GuideScreen(c: DemoController) = ScreenFrame {
    c.bookVersion
    val g = c.guide()
    if (c.driving) { GuideDriving(c, g); return@ScreenFrame }
    AppHeader { c.demoPanel = true }
    GuideTitle(c, g)
    LevelBanner(g, Modifier.padding(top = 12.dp))
    // 넓은 창: 왼쪽 확인 순서(넘치면 그 칸만 밀어 봄), 오른쪽 해결이 안 되면 → 자주 있는 원인 → 긴급출동(내 보험사).
    if (LocalWide.current) Row(Modifier.padding(top = 16.dp).weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        StepsPanel(c, g, Modifier.weight(1.35f).fillMaxHeight(), scroll = true)
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            NextPanel(c, Modifier)
            CausesPanel(g)
            RoadsideCard(c)
        }
    } else Column(Modifier.padding(top = 16.dp).weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StepsPanel(c, g, Modifier, scroll = false)
        NextPanel(c, Modifier)
        CausesPanel(g)
        RoadsideCard(c)
    }
    // 오른쪽 아래 = 다음 할 일. 기록 전에는 "해결 안 됐어요 → 증상 기록", 기록 뒤에는 "정비소에 도움 요청".
    val recorded = c.book.byId(c.guideRecordId) != null
    BottomButtons(Btn("해결됐어요", Gi.CircleCheck, "안내 닫고 홈으로") { c.guideResolved() },
        if (!recorded) Btn("해결 안 됐어요", Gi.History, "증상 기록하기", trailing = Gi.ChevronRight) { c.recordFromGuide() }
        else Btn("정비소 도움 요청", Gi.Wrench, "기록 보여 주기 · 길 안내", trailing = Gi.ChevronRight) { c.requestShopHelp() },
        if (recorded) RightKind.AMBER else RightKind.MINT, guardKey = Screen.GUIDE, rightWeight = 1.25f)
}

@Composable
private fun GuideTitle(c: DemoController, g: Guide) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.height(56.dp).clip(Pill).background(C.Surf).border(1.5.dp, C.Line, Pill)
            .clickable(role = Role.Button) { c.go(Screen.SITUATION_PARKED) }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            GIcon(Gi.ArrowLeft, C.Fg, 28.dp); Spacer(Modifier.width(8.dp)); Text("증상 바꾸기", style = ts(22.sp, C.Fg, FontWeight.SemiBold), maxLines = 1)
        }
        Spacer(Modifier.width(16.dp))
        Text("대처 방법", style = ts(32.sp, weight = FontWeight.Bold), modifier = Modifier.weight(1f), maxLines = 1)
        InfoButton("대처 방법", listOf(
            "고른 증상과 그 조합에 맞춰 확인 순서를 고른 일반 안내입니다. 진단이 아닙니다",
            "안내 문장은 아반떼 취급설명서로 검토하기 전의 예시입니다. 설명서와 다르면 설명서를 따르세요",
            "위험하다고 느끼면 안내보다 안전이 먼저입니다: 안전한 곳에 세우고 긴급출동 · 119",
            "확인한 단계를 누르면 표시됩니다(저장하지 않음)",
        ))
        Spacer(Modifier.width(12.dp))
        DriveChip(c)
    }
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        g.symptoms.forEach { s -> StatusChip(symptomIcon(s), s, C.Mint) }
    }
}

@Composable
private fun LevelBanner(g: Guide, modifier: Modifier) {
    val look = levelLook(g.level)
    Row(modifier.fillMaxWidth().clip(R24).background(look.bg).border(2.dp, look.fg.copy(alpha = 0.7f), R24).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GIcon(look.icon, look.fg, 60.dp)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(g.level.label, style = ts(22.sp, look.fg, FontWeight.SemiBold))
            Text(g.headline, style = ts(30.sp, C.Fg, FontWeight.Bold, 40.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (g.combos.isNotEmpty()) Text("함께 본 조합 · " + g.combos.joinToString(", "), style = ts(20.sp, C.Mut), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun StepsPanel(c: DemoController, g: Guide, modifier: Modifier, scroll: Boolean) {
    Panel(modifier, pad = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GIcon(Gi.CheckCheck, C.Mint, 30.dp); Spacer(Modifier.width(10.dp))
            Text("먼저 확인해 보세요", style = ts(26.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1)
            Text("${c.guideChecked.count { it < g.steps.size }}/${g.steps.size} 확인", style = ts(22.sp, C.Mut))
            Spacer(Modifier.width(12.dp)); HonestyBadge("일반 안내 · 진단 아님")
        }
        Column((if (scroll) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            g.steps.forEachIndexed { i, st -> StepRow(c, i, st) }
        }
    }
}

@Composable
private fun StepRow(c: DemoController, i: Int, st: GuideStep) {
    val done = i in c.guideChecked
    Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).clip(R16).background(if (done) C.OnMint else C.Surf2).border(1.5.dp, if (done) C.Mint else C.Line, R16)
        .clickable(role = Role.Checkbox) { c.toggleGuideStep(i) }.semantics { contentDescription = "${i + 1}단계 ${st.title}" + if (done) " 확인함" else "" }
        .padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(Pill).background(if (done) C.Mint else C.Surf).border(2.dp, if (done) C.Mint else C.Line, Pill), contentAlignment = Alignment.Center) {
            if (done) GIcon(Gi.CheckCheck, C.Dark, 28.dp) else Text("${i + 1}", style = ts(22.sp, C.Fg, FontWeight.Bold))
        }
        Spacer(Modifier.width(14.dp))
        GIcon(markIcon(st.mark), markTint(st.mark), 42.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(st.title, style = ts(25.sp, C.Fg, FontWeight.SemiBold, 32.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(st.detail, style = ts(21.sp, C.Mut), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (st.from.isNotEmpty()) { Spacer(Modifier.width(10.dp)); TagPill(st.from.joinToString(" · ")) }
            }
        }
        st.action?.let { a ->
            Spacer(Modifier.width(12.dp))
            val waiting = actionKind(a)?.let { k -> c.phoneWait?.kind == k } == true
            ActionPill(actionIcon(a), if (waiting) "폰에서 기록 중" else a.label, waiting) { if (waiting) c.cancelPhoneWait() else c.guideAction(a) }
        }
    }
}

/** 이 단계가 어느 증상에서 왔는지(떨림 · 타이어 …). */
@Composable
private fun TagPill(text: String) {
    Box(Modifier.clip(Pill).border(1.dp, C.Line, Pill).padding(horizontal = 10.dp, vertical = 2.dp)) {
        Text(text, style = ts(18.sp, C.Dim, FontWeight.Medium), maxLines = 1)
    }
}

@Composable
private fun ActionPill(icon: Gi, label: String, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.height(60.dp).clip(Pill).background(if (on) C.OnMint else C.Surf).border(1.5.dp, C.Mint, Pill)
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        GIcon(icon, C.Mint, 30.dp); Spacer(Modifier.width(8.dp)); Text(label, style = ts(22.sp, C.Mint, FontWeight.SemiBold), maxLines = 1)
    }
}

@Composable
private fun CausesPanel(g: Guide) {
    if (g.causes.isEmpty()) return
    Panel(pad = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GIcon(Gi.Wrench, C.Amber, 30.dp); Spacer(Modifier.width(10.dp))
            Text("자주 있는 원인", style = ts(26.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            Text("진단 아님 · 정비소가 확인", style = ts(20.sp, C.Mut), maxLines = 1)
        }
        g.causes.chunked(2).forEach { row ->
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { cause ->
                    Row(Modifier.weight(1f).height(56.dp).clip(R16).background(C.Surf2).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(Pill).background(C.Amber)); Spacer(Modifier.width(10.dp))
                        Text(cause, style = ts(22.sp, C.Fg, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 해결이 안 되면: ① 증상 기록 ② 정비소에 도움 요청. 지금 할 단계를 민트 테두리로, 끝난 단계는 확인 표시로 보입니다. */
@Composable
private fun NextPanel(c: DemoController, modifier: Modifier) {
    val rec = c.book.byId(c.guideRecordId)
    Panel(modifier, pad = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GIcon(Gi.CircleHelp, C.Mint, 30.dp); Spacer(Modifier.width(10.dp))
            Text("해결이 안 되면", style = ts(26.sp, weight = FontWeight.SemiBold))
        }
        Text("증상을 기록해 두고, 정비소에 그 기록을 보여 주며 도움을 요청하세요", style = ts(21.sp, C.Mut, lineHeight = 29.sp), modifier = Modifier.padding(top = 6.dp))
        val evidence = rec?.evidence?.size ?: 0
        NextStep(1, Gi.History, "증상 기록하기",
            if (rec != null) "기록함 · ${DemoController.hms(rec.wall)}" + (if (evidence > 0) " · 폰 기록 ${evidence}개" else "") else "고른 증상과 지금 차 상태를 남겨요",
            done = rec != null, now = rec == null) { c.recordFromGuide() }
        NextStep(2, Gi.Wrench, "정비소에 도움 요청", "기록을 QR로 보여 주고 길 안내", done = false, now = rec != null) { c.requestShopHelp() }
    }
}

@Composable
private fun NextStep(n: Int, icon: Gi, title: String, sub: String, done: Boolean, now: Boolean, onClick: () -> Unit) {
    val edge = when { done -> C.Mint.copy(alpha = 0.5f); now -> C.Mint; else -> C.Line }
    Row(Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 84.dp).clip(R16).background(if (now) C.OnMint else C.Surf2).border(if (now) 2.dp else 1.5.dp, edge, R16)
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(Pill).background(if (done) C.Mint else C.Surf).border(2.dp, if (done || now) C.Mint else C.Line, Pill), contentAlignment = Alignment.Center) {
            if (done) GIcon(Gi.CheckCheck, C.Dark, 28.dp) else Text("$n", style = ts(22.sp, C.Fg, FontWeight.Bold))
        }
        Spacer(Modifier.width(14.dp))
        GIcon(icon, if (now || done) C.Mint else C.Mut, 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = ts(25.sp, C.Fg, FontWeight.SemiBold), maxLines = 1)
            Text(sub, style = ts(21.sp, if (done) C.Mint else C.Mut), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (now) GIcon(Gi.ChevronRight, C.Mint, 36.dp)
    }
}

/** 주행 중: 한 줄 안전 안내 + 음성. 확인 순서는 정차하면 나옵니다. */
@Composable
private fun ColumnScope.GuideDriving(c: DemoController, g: Guide) {
    AppHeader { c.demoPanel = true }
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("대처 안내", style = ts(32.sp, weight = FontWeight.Bold), modifier = Modifier.weight(1f)); DriveChip(c)
    }
    val look = levelLook(g.level)
    Column(Modifier.padding(top = 20.dp).weight(1f).fillMaxWidth().clip(R24).background(look.bg).border(2.dp, look.fg.copy(alpha = 0.7f), R24).padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        GIcon(look.icon, look.fg, 150.dp)
        Spacer(Modifier.height(20.dp))
        Text(g.level.label, style = ts(32.sp, look.fg, FontWeight.SemiBold))
        Spacer(Modifier.height(14.dp))
        Text(g.headline, style = ts(42.sp, C.Fg, FontWeight.Bold, 58.sp), textAlign = TextAlign.Center)
        if (c.book.byId(c.guideRecordId) != null) {
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GIcon(Gi.CircleCheck, C.Mint, 32.dp); Spacer(Modifier.width(10.dp)); Text("이 순간을 기록했습니다", style = ts(26.sp, C.Mint, FontWeight.SemiBold))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp).clip(R16).background(C.Surf).padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.Lock, C.Mut, 30.dp); Spacer(Modifier.width(16.dp))
        Text("확인 순서는 정차하면 이 화면에 나옵니다", style = ts(22.sp, C.Mut))
    }
    BottomButtons(Btn("다시 듣기", Gi.Volume) { c.speakGuide() }, Btn("지금 도움 요청", Gi.Send, "보호자에게 알림") { c.openRecipients(IncidentKind.HELP) },
        RightKind.HELP_DARK, guardKey = Screen.GUIDE)
}

/** 정비소 화면 위: 대처 안내에서 남긴 "도움 요청할 기록"과 QR로 보여 주기. */
@Composable
fun HelpRecordBanner(c: DemoController) {
    val r = c.book.byId(c.helpRecordId) ?: return
    Row(Modifier.fillMaxWidth().padding(top = 12.dp).clip(R16).background(C.OnAmber).border(1.5.dp, C.Amber, R16).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.History, C.Amber, 36.dp); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("도움 요청할 기록 · ${app.pleosguard.demo.logic.RecordStatus.entries.firstOrNull { it.name == r.status }?.label ?: "기록함"}",
                style = ts(20.sp, C.Amber, FontWeight.SemiBold), maxLines = 1)
            Text("${r.symptom ?: "증상"} · ${DemoController.hms(r.wall)}", style = ts(24.sp, C.Fg, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Row(Modifier.height(60.dp).clip(Pill).background(C.Amber).clickable(role = Role.Button) { c.shareRecord(r.id) }.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            GIcon(Gi.QrCode, C.Dark, 30.dp); Spacer(Modifier.width(8.dp)); Text("QR로 보여 주기", style = ts(22.sp, C.Dark, FontWeight.SemiBold), maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.size(56.dp).clip(Pill).clickable(role = Role.Button) { c.helpRecordId = null }.semantics { contentDescription = "도움 요청할 기록 닫기" },
            contentAlignment = Alignment.Center) { GIcon(Gi.Close, C.Mut, 26.dp) }
    }
}

/** 긴급출동 · 사고 접수: 내 보험사 번호(모의 연결). 보험사를 정하지 않았으면 정하기 창을 엽니다. */
@Composable
private fun RoadsideCard(c: DemoController) {
    val ins = c.insurer()
    Panel(pad = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GIcon(Gi.Phone, C.Amber, 30.dp); Spacer(Modifier.width(10.dp))
            Text("긴급출동 · 사고 접수", style = ts(26.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1)
            Text("위험하면 112 · 119", style = ts(20.sp, C.Mut), maxLines = 1)
        }
        Row(Modifier.padding(top = 12.dp).fillMaxWidth().heightIn(min = 84.dp).clip(R16).background(C.Surf2).border(1.5.dp, if (ins != null) C.Amber else C.Line, R16)
            .clickable(role = Role.Button) { if (ins != null) c.mockCall(ins) else c.openInsurerPicker() }.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            GIcon(Gi.Receipt, C.Purple, 40.dp); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(ins?.name ?: "내 보험사를 정하세요", style = ts(25.sp, C.Fg, FontWeight.SemiBold), maxLines = 1)
                Text(ins?.phone ?: "사고 · 고장 때 접수 번호를 바로 알려 드려요", style = ts(if (ins != null) 26.sp else 21.sp, if (ins != null) C.Amber else C.Mut, FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(if (ins != null) "연결" else "정하기", style = ts(22.sp, if (ins != null) C.Amber else C.Purple, FontWeight.SemiBold))
            Spacer(Modifier.width(6.dp)); GIcon(Gi.ChevronRight, if (ins != null) C.Amber else C.Purple, 32.dp)
        }
    }
}
