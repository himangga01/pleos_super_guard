package app.pleosguard.demo.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pleosguard.demo.DemoController
import app.pleosguard.demo.R
import app.pleosguard.demo.Screen
import app.pleosguard.demo.logic.Pattern
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import app.pleosguard.demo.RecordSnap
import app.pleosguard.demo.car3d.CarPart
import app.pleosguard.demo.car3d.icon
import app.pleosguard.demo.logic.WarnEntry
import app.pleosguard.demo.logic.RulesLab
import app.pleosguard.demo.logic.WarningCatalog
import app.pleosguard.demo.ui.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.roundToInt

@Composable
private fun ColumnScope.Head(c: DemoController, tab: Tab?, title: String) = ScreenHead(c, tab, title)

@Composable
private fun Locked(text: String) = Panel(Modifier.padding(top = 8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) { GIcon(Gi.Lock, C.Mut, 30.dp); Spacer(Modifier.width(14.dp)); Text(text, style = ts(24.sp, C.Mut)) }
}

// ---------------------------------------------------------------- 기록 목록
/*
 * 넓은 창(가로 1100dp 이상, 도크 안)은 세로 화면을 늘리지 않고 두 칸으로 나눕니다(2026-11-14 사용자 요청 "넓은 화면을 낭비하지 말 것").
 * - 목록이 있는 화면: 왼쪽 목록 + 오른쪽 상세(Material 3 list-detail, AAOS 설정 앱의 1400dp 이상 2단과 같은 방식).
 * - 카드가 쌓인 화면: 2열.
 */
@Composable
fun RecordListScreen(c: DemoController) = ScreenFrame {
    c.bookVersion
    Head(c, Tab.RECORD, "증상 기록")
    val pattern = c.pattern()
    val list = c.book.records.sortedByDescending { it.wall }
    if (LocalWide.current && list.isNotEmpty()) {
        var sel by remember { mutableStateOf(list.first().id) }
        val r = list.firstOrNull { it.id == sel } ?: list.first()
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (pattern != null) { PatternBanner(c, pattern); Spacer(Modifier.height(14.dp)) }
                RecordRows(list, Modifier.weight(1f), selectedId = r.id) { sel = it.id }
            }
            RecordSummary(c, r, Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        if (pattern != null) { PatternBanner(c, pattern); Spacer(Modifier.height(14.dp)) }
        if (list.isEmpty()) Panel(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GIcon(Gi.Rec, C.Mut, 40.dp); Spacer(Modifier.width(14.dp))
                Text("아직 기록이 없습니다", style = Head, modifier = Modifier.weight(1f))
                InfoButton("증상 기록하기", listOf("홈의 \"증상 기록하기\"를 누르면 누른 순간 앞 20초와 뒤 10초의 차 상태를 남깁니다"), enabled = !c.driving)
            }
        } else RecordRows(list, Modifier.weight(1f), selectedId = null) { c.lastRecord = it; c.go(Screen.RECORD) }
    }
    BottomButtons(Btn("홈으로", Gi.Home) { c.go(Screen.HOME) }, Btn("증상 기록하기", Gi.Rec, "지금 차 상태 저장") { c.recordNow() }, RightKind.MINT)
}

@Composable
private fun PatternBanner(c: DemoController, pattern: Pattern) {
    Row(Modifier.fillMaxWidth().clip(R24).background(C.OnMint).border(2.dp, C.Mint, R24).clickable { c.go(Screen.PATTERN) }.padding(20.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.History, C.Mint, 40.dp); Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("재현 조건", style = ts(26.sp, C.Mint, FontWeight.Bold))
            Text("${pattern.part ?: ""} ${pattern.symptom} · ${pattern.records.size}번", style = ts(22.sp))
        }
        GIcon(Gi.ChevronRight, C.Mint, 32.dp)
    }
}

@Composable
private fun RecordRows(list: List<RecordSnap>, modifier: Modifier, selectedId: String?, onClick: (RecordSnap) -> Unit) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list, key = { it.id }) { r ->
            val on = r.id == selectedId
            Row(Modifier.fillMaxWidth().clip(R16).background(if (on) C.OnMint else C.Surf).then(if (on) Modifier.border(2.dp, C.Mint, R16) else Modifier)
                .clickable { onClick(r) }.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                GIcon(if (r.symptom == "소음") Gi.AudioLines else CarPart.ofLabel(r.part)?.icon() ?: Gi.Rec, C.Mint, 32.dp); Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(listOfNotNull(r.part, r.symptom ?: "증상 고르기 전").joinToString(" · "), style = ts(24.sp, weight = FontWeight.SemiBold), maxLines = 1)
                    Text(DemoController.dateTime(r.wall) + (r.memo?.let { " · $it" } ?: ""), style = ts(22.sp, C.Mut), maxLines = 1)
                }
                if (r.evidence.isNotEmpty()) { GIcon(Gi.Smartphone, C.Mint, 28.dp); Text("${r.evidence.size}", style = ts(22.sp, C.Mint, FontWeight.SemiBold)); Spacer(Modifier.width(10.dp)) }
                if (r.status != app.pleosguard.demo.logic.RecordStatus.OPEN.name) { StatusChip(Gi.CircleCheck, app.pleosguard.demo.logic.RecordStatus.valueOf(r.status).label, C.Mint); Spacer(Modifier.width(10.dp)) }
                if (r.example) TestBadge("예시 기록")
                if (selectedId != null) { Spacer(Modifier.width(8.dp)); GIcon(Gi.ChevronRight, if (on) C.Mint else C.Dim, 28.dp) }
            }
        }
    }
}

/** 넓은 창의 오른쪽: 고른 기록 한 건의 요약(부위 · 증상 · 처리 흐름 · 자세히 · 폰 증거 또는 차 신호)과 시간표 열기. */
@Composable
private fun RecordSummary(c: DemoController, r: RecordSnap, modifier: Modifier) = Panel(modifier, pad = 24.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(CarPart.ofLabel(r.part)?.icon() ?: Gi.Rec, C.Mint, 56.dp); Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(listOfNotNull(r.part, r.symptom ?: "증상 고르기 전").joinToString(" · "), style = ts(30.sp, weight = FontWeight.Bold), maxLines = 1)
            Text(DemoController.dateTime(r.wall), style = ts(22.sp, C.Mut))
        }
        if (r.example) TestBadge("예시 기록")
    }
    // 처리 흐름: 누르면 그 단계로 바꿉니다(기록함 → 정비소에 보여 줌 → 정비 받음 → 해결).
    StatusFlow(r.status, Modifier.padding(top = 14.dp).fillMaxWidth()) { st -> c.updateRecord(r.id) { it.copy(status = st.name) } }
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusChip(if (r.drivingAtRecord) Gi.CarFront else Gi.Park, if (r.drivingAtRecord) "주행 중" else "정차 중", C.Mint)
        if (r.severity > 0) StatusChip(Gi.Gauge, app.pleosguard.demo.logic.RecordRules.SEVERITIES[r.severity], if (r.severity == 3) C.Amber else C.Mint)
        r.frequency?.let { StatusChip(Gi.Repeat, it, C.Mint) }
    }
    if (r.conditions.isNotEmpty()) Text(r.conditions.joinToString(" · "), style = ts(22.sp, C.Mint), modifier = Modifier.padding(top = 10.dp), maxLines = 1)
    r.memo?.let { m ->
        Row(Modifier.padding(top = 12.dp).fillMaxWidth().clip(R16).background(C.Surf2).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            GIcon(Gi.Mic, C.Mint, 30.dp); Spacer(Modifier.width(12.dp)); Text("\"$m\"", style = ts(24.sp), maxLines = 2)
        }
    }
    val ev = r.evidence.lastOrNull()
    if (ev != null) {
        EvidenceCard(c, ev, Modifier.padding(top = 14.dp).weight(1f))
        if (r.evidence.size > 1) Text("폰 기록 ${r.evidence.size}개 · 가장 최근 것", style = ts(22.sp, C.Mut), modifier = Modifier.padding(top = 6.dp))
    } else {
        // 기록 순간의 차 신호(시간표의 가운데 값). 아이콘 + 값만 크게.
        val (base, samples) = c.recordSamples(r)
        val at = samples.minByOrNull { kotlin.math.abs(it.t - base) }
        val sig = listOf(
            Triple(Gi.Gauge, "차속", at?.speed?.let { "${(it * 3.6f).roundToInt()}km/h" } ?: "확인 불가"),
            Triple(Gi.Pedal, "브레이크", when (at?.brake) { true -> "밟음"; false -> "안 밟음"; null -> "확인 불가" }),
            Triple(Gi.Steering, "바퀴 방향", at?.steering?.let { if (kotlin.math.abs(it) < 5) "직진" else if (it < 0) "왼쪽" else "오른쪽" } ?: "확인 불가"),
            Triple(Gi.Turn, "방향지시등", when (at?.turn?.name) { "LEFT" -> "왼쪽"; "RIGHT" -> "오른쪽"; "OFF" -> "꺼짐"; else -> "확인 불가" }),
        )
        Column(Modifier.padding(top = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            sig.chunked(2).forEach { row ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { (ic, k, v) ->
                        Box(Modifier.weight(1f).fillMaxHeight().clip(R16).background(C.Surf2)) {
                            TileContent(ic, C.Mint, k, v, titleColor = C.Mut)
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.weight(1f).fillMaxHeight().clip(R24).background(C.Surf2).border(2.dp, C.Mint, R24).clickable(role = Role.Button) { c.lastRecord = r; c.go(Screen.RECORD) }) {
            TileContent(Gi.Activity, C.Mint, "시간표 · 자세히", trailing = Gi.ChevronRight)
        }
        Box(Modifier.weight(1f).fillMaxHeight().clip(R24).background(C.Surf2).clickable(role = Role.Button) { c.requestPhone(if (r.symptom == "떨림") "vibration" else "noise", r.id, r.part) }) {
            TileContent(Gi.Smartphone, C.Mint, "폰으로 더 기록")
        }
    }
}

// ---------------------------------------------------------------- 재현 조건(N2) · 소리 용의자 지우기(H3) · 정비 노트(N6)
@Composable
fun PatternScreen(c: DemoController) = ScreenFrame {
    c.bookVersion
    Head(c, Tab.RECORD, "재현 조건")
    val p = c.pattern()
    if (p == null) {
        Panel(Modifier.weight(1f)) { Text("같은 증상이 3번 쌓이면 재현 조건 카드를 만듭니다", style = Head) }
        BottomButtons(Btn("기록 목록", Gi.History) { c.go(Screen.RECORD_LIST) }, Btn("홈으로", Gi.Home) { c.go(Screen.HOME) }, RightKind.MINT)
        return@ScreenFrame
    }
    PatternHeader(c, p)
    if (LocalWide.current) Row(Modifier.padding(top = 14.dp).weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        CondPanel(p, Modifier.weight(1.2f).fillMaxHeight(), scroll = true)
        Column(Modifier.weight(1f).fillMaxHeight()) {
            ExcludedPanel(p, Modifier)
            NoteCard(c, p, Modifier.padding(top = 14.dp).weight(1f))
        }
    } else {
        CondPanel(p, Modifier.padding(top = 14.dp), scroll = false)
        ExcludedPanel(p, Modifier.padding(top = 14.dp))
        NoteCard(c, p, Modifier.padding(top = 14.dp).weight(1f))
    }
    BottomButtons(Btn("기록 목록", Gi.History) { c.go(Screen.RECORD_LIST) }, Btn("정비소에 보여 주기", Gi.Send) { c.openShare(p) }, RightKind.MINT)
}

@Composable
private fun PatternHeader(c: DemoController, p: Pattern) = Panel(pad = 20.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(CarPart.ofLabel(p.part)?.icon() ?: Gi.Wheel, C.Mint, 44.dp); Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text("${p.part ?: "부위 미정"} · ${p.records.lastOrNull { it.memo != null }?.memo ?: p.symptom}", style = ts(28.sp, weight = FontWeight.Bold), maxLines = 1)
            Text("같은 증상 ${p.records.size}번 · " + p.records.joinToString(" · ") { DemoController.monthDay(it.wall) }, style = ts(22.sp, C.Mint))
        }
        if (p.records.any { it.example }) TestBadge("예시 기록 포함")
        Spacer(Modifier.width(10.dp))
        InfoButton("재현 조건", listOf("기록 순간의 차 신호를 평소 주행과 견준 자료입니다", "원인을 판정하지 않습니다"), enabled = !c.driving)
    }
}

@Composable
private fun CondPanel(p: Pattern, modifier: Modifier, scroll: Boolean) = Panel(modifier, pad = 20.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) { GIcon(Gi.Activity, C.Mint, 30.dp); Spacer(Modifier.width(10.dp)); Text("자주 겹친 조건", style = ts(24.sp, weight = FontWeight.SemiBold)) }
    Column(if (scroll) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier) {
        p.conds.forEach { cd ->
            Column(Modifier.padding(top = 14.dp).fillMaxWidth().clip(R16).background(C.Surf2).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(cd.label, style = ts(26.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                    if (cd.test) { TestBadge(); Spacer(Modifier.width(8.dp)) }
                    Text("${cd.total}번 중 ${cd.hits}번", style = ts(24.sp, C.Mint, FontWeight.Bold))
                }
                Bar(cd.hits.toFloat() / cd.total, C.Mint)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text("평소 ${cd.usualPct}%" + if (cd.usualExample) " (예시 값)" else "", style = ts(22.sp, C.Dim), modifier = Modifier.width(220.dp))
                    Box(Modifier.weight(1f)) { Bar(cd.usualPct / 100f, C.Dim) }
                }
            }
        }
    }
}

@Composable
private fun ExcludedPanel(p: Pattern, modifier: Modifier) = Panel(modifier, pad = 20.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.AudioLines, C.Mut, 30.dp); Spacer(Modifier.width(10.dp))
        Text("소리 원인에서 뺀 것", style = ts(24.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
        if (p.cabinTest) TestBadge()
    }
    Text(p.excluded.joinToString(" · ").ifEmpty { "함께 남긴 실내 상태가 없습니다" }, style = ts(24.sp), modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Bar(frac: Float, color: Color) {
    Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(14.dp).clip(Pill).background(C.Bg)) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().clip(Pill).background(color))
    }
}

@Composable
private fun NoteCard(c: DemoController, p: Pattern, modifier: Modifier) = Panel(modifier, pad = 20.dp) {
    val (odo, _) = c.odometer()
    val re = c.book.recheck(p.symptom, odo)
    val note = c.book.notes.lastOrNull { it.symptom == p.symptom }
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.Wrench, C.Mint, 30.dp); Spacer(Modifier.width(10.dp))
        Text("정비 노트", style = ts(24.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
        InfoButton("정비 노트", listOf("정비 받은 내용을 남기면 500km 동안 같은 증상이 다시 나는지 봅니다"), enabled = !c.driving)
    }
    if (note != null && re != null) {
        Text("${DemoController.monthDay(note.wall)} ${note.text}", style = ts(24.sp), modifier = Modifier.padding(top = 6.dp))
        Text("같은 증상 다시 기록 ${re.first}건" + (re.second?.let { " · 확인 남은 거리 ${it.roundToInt()}km" } ?: " · 주행거리 확인 불가"), style = ts(22.sp, if (re.first == 0) C.Mint else C.Amber))
    } else if (c.driving) {
        Text("정비 노트는 정차 중에 남깁니다", style = Small)
    } else {
        var text by remember { mutableStateOf("") }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(text, { text = it }, textStyle = ts(24.sp), cursorBrush = SolidColor(C.Mint), singleLine = true,
                modifier = Modifier.weight(1f).clip(R16).background(C.Surf2).padding(horizontal = 16.dp, vertical = 14.dp),
                decorationBox = { inner -> if (text.isEmpty()) Text("정비 내용(예: 앞 브레이크 패드 교체)", style = ts(22.sp, C.Dim)); inner() })
            Spacer(Modifier.width(12.dp))
            Box(Modifier.clip(R16).background(C.Mint).clickable { c.addServiceNote(p.symptom, text) }.padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text("정비 받았어요", style = ts(22.sp, C.Dark, FontWeight.SemiBold))
            }
        }
    }
}

// ---------------------------------------------------------------- 상황 선택(정차 후 12칸)
private val NINE = listOf("차체 떨림" to Gi.Activity, "핸들 쏠림" to Gi.Steering, "이상한 소음" to Gi.AudioLines, "타는 냄새" to Gi.Flame, "페달 이상" to Gi.Pedal,
    "타이어 이상" to Gi.Wheel, "힘이 없어요" to Gi.Gauge, "경고등이 켜졌어요" to Gi.Engine, "잘 모르겠음" to Gi.CircleHelp)

@Composable
fun SituationParkedScreen(c: DemoController) = ScreenFrame {
    AppHeader { c.demoPanel = true }
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("어떤 상황인가요", style = ts(32.sp, weight = FontWeight.Bold), modifier = Modifier.weight(1f)); DriveChip(c)
    }
    val urgent = listOf(Triple("사고가 났어요", Gi.Crash, Screen.COUNTDOWN))
    if (LocalWide.current) Row(Modifier.padding(top = 8.dp).weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        // 넓은 창: 왼쪽 = 급한 상황 3칸(세로로 크게), 오른쪽 = 증상 9칸
        Column(Modifier.weight(1f).fillMaxHeight()) {
            UrgentLabel()
            Column(Modifier.padding(top = 10.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                urgent.forEach { UrgentTile(c, it, Modifier.weight(1f).fillMaxWidth(), wide = true) }
            }
        }
        Column(Modifier.weight(2.1f).fillMaxHeight()) {
            SymptomLabel()
            SymptomGrid(c, Modifier.padding(top = 10.dp).weight(1f))
        }
    } else {
        UrgentLabel()
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { urgent.forEach { UrgentTile(c, it, Modifier.weight(1f).height(150.dp), wide = false) } }
        Spacer(Modifier.height(14.dp))
        SymptomLabel()
        SymptomGrid(c, Modifier.weight(1f).padding(top = 10.dp))
    }
    // 2026-11-14 사용자 요청: 증상을 고른 뒤 할 일을 오른쪽 아래 주 버튼("대처 방법 보기")으로 분명히 보입니다. 고르기 전에는 흐리게 그립니다.
    // 보호자에게 바로 알리는 "지금 도움 요청"은 왼쪽으로 옮겼습니다(기록은 대처 안내 뒤 "해결 안 됨"에서 남깁니다).
    val n = c.parkedChoices.size
    BottomButtons(Btn("지금 도움 요청", Gi.Send, "보호자에게 알림") { c.openRecipients(app.pleosguard.demo.IncidentKind.HELP) },
        Btn("대처 방법 보기", Gi.CheckCheck, if (n == 0) "증상을 먼저 고르세요" else "고른 증상 ${n}개", trailing = Gi.ChevronRight) { c.openGuide() },
        RightKind.MINT, guardKey = Screen.SITUATION_PARKED, rightDim = n == 0, leftTint = C.EPink, rightWeight = 1.25f)
}

@Composable
private fun UrgentLabel() = Row(verticalAlignment = Alignment.CenterVertically) {
    GIcon(Gi.OctagonAlert, C.Red, 26.dp); Spacer(Modifier.width(8.dp)); Text("바로 안내", style = ts(22.sp, C.Red, FontWeight.SemiBold))
}

@Composable
private fun SymptomLabel() = Row(verticalAlignment = Alignment.CenterVertically) {
    GIcon(Gi.CheckCheck, C.Mint, 26.dp); Spacer(Modifier.width(8.dp)); Text("증상 · 최대 3개", style = ts(22.sp, C.Mint, FontWeight.SemiBold), modifier = Modifier.weight(1f))
    GIcon(Gi.Lock, C.Mut, 24.dp); Spacer(Modifier.width(6.dp)); Text("보호자 자동 전송 없음", style = ts(22.sp, C.Mut))
}

@Composable
private fun UrgentTile(c: DemoController, t: Triple<String, Gi, Screen>, modifier: Modifier, wide: Boolean) {
    val (label, ic, sc) = t
    val go = { if (sc == Screen.COUNTDOWN) c.startCountdown(app.pleosguard.demo.IncidentKind.ACCIDENT, testInput = false) }
    // 칸 비율에 맞춥니다: 가로로 긴 칸(세로 화면)은 아이콘 옆에 이름, 세로로 긴 칸(넓은 창의 왼쪽 열)은 아이콘 위 · 이름 아래로 크게.
    BoxWithConstraints(modifier.clip(R24).background(C.ECard).border(2.dp, C.Red.copy(alpha = 0.7f), R24).clickable(role = Role.Button) { go() }) {
        TileContent(ic, C.Red, label, row = maxWidth > maxHeight)
    }
}

@Composable
private fun SymptomGrid(c: DemoController, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        NINE.chunked(3).forEach { row ->
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (t, ic) ->
                    val on = t in c.parkedChoices
                    Box(Modifier.weight(1f).fillMaxHeight().clip(R24).background(if (on) C.OnMint else C.Surf2).then(if (on) Modifier.border(2.dp, C.Mint, R24) else Modifier)
                        .clickable(role = Role.Checkbox) { c.toggleParkedChoice(t) }) {
                        TileContent(ic, if (on) C.Mint else C.Fg, t, row = false, titleColor = if (on) C.Mint else C.Fg)
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 정비소(N5·H2 + 2026-11-14 상세 · 후기 · 별점 · 방문)
@Composable
fun ServiceScreen(c: DemoController) = ScreenFrame {
    c.sig; c.tick; c.careVersion
    Head(c, Tab.SERVICE, "정비소")
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.Navigation, C.Mut, 26.dp); Spacer(Modifier.width(8.dp))
        Text("내비: ${c.navi?.state ?: "없음"}", style = ts(22.sp, C.Mut), modifier = Modifier.weight(1f), maxLines = 1)
        HonestyBadge(if (c.shopsFromServer) "모의 서버 · 예시 정비소" else "예시 정비소"); Spacer(Modifier.width(10.dp))
        InfoButton("정비소 목록", listOf(
            "지도는 앱에 그리지 않고 옆 내비에서 봅니다",
            "거리와 도착 시간은 NaviHelper requestTrip 결과입니다",
            "정비소 이름 · 정보 · 후기는 예시입니다(실제 상호 · 주소 아님)",
            "후기는 모의 서버에 올라가 다른 차에서도 보입니다(서버가 없으면 이 차에만 저장)",
        ) + listOfNotNull(c.navi?.lastError?.let { "마지막 내비 오류: $it" }), enabled = !c.driving)
    }
    HelpRecordBanner(c)
    if (LocalWide.current) Row(Modifier.padding(top = 12.dp).weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        ShopList(c, Modifier.weight(0.85f).fillMaxHeight())
        ShopCareDetail(c, Modifier.weight(1.15f).fillMaxHeight())
    } else {
        ShopList(c, Modifier.padding(top = 12.dp))
        ShopCareDetail(c, Modifier.padding(top = 12.dp).weight(1f))
    }
    BottomButtons(Btn("내비에서 보기", Gi.Navigation) { c.navi?.openSearch("자동차 정비소") ?: c.showToast("내비 연결 없음") },
        Btn("경유지로 추가", Gi.MapPin) { c.shops.getOrNull(c.selectedShop)?.let { s -> c.navi?.addWaypoint(s) ?: c.showToast("내비 연결 없음") } }, RightKind.AMBER)
}

@Composable
private fun ShopList(c: DemoController, modifier: Modifier) {
    val wide = LocalWide.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (wide) 12.dp else 10.dp)) {
        c.shops.forEachIndexed { i, s ->
            val on = i == c.selectedShop
            val info = c.shopInfos.getOrNull(i)
            Row(Modifier.fillMaxWidth().height(if (wide) 124.dp else 96.dp).clip(R24).background(if (on) C.OnAmber else C.Surf).then(if (on) Modifier.border(2.dp, C.Amber, R24) else Modifier)
                .clickable { c.selectShop(i) }.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                GIcon(Gi.Wrench, C.Amber, if (wide) 44.dp else 36.dp); Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = ts(if (wide) 28.sp else 26.sp, weight = FontWeight.SemiBold), maxLines = 1)
                    val d = s.distanceM; val e = s.etaS
                    Text(if (d != null && e != null) "%.1fkm · %d분".format(d / 1000f, (e + 59) / 60) else "도착 예상 계산 중", style = ts(22.sp, if (d != null) C.Fg else C.Mut), maxLines = 1)
                }
                if (info != null) Column(horizontalAlignment = Alignment.End) {
                    if (info.reviewCount >= app.pleosguard.demo.logic.CareData.MIN_REVIEWS_FOR_STARS) {
                        Stars(info.rating, 22.dp)
                        Text("%.1f · %d".format(info.rating, info.reviewCount), style = ts(22.sp, C.Mut))
                    } else Text("후기 ${info.reviewCount}개", style = ts(22.sp, C.Mut))
                    if (info.partner) Box(Modifier.padding(top = 4.dp)) { HonestyBadge("제휴") }
                }
                if (on) { Spacer(Modifier.width(10.dp)); GIcon(Gi.CircleCheck, C.Amber, 30.dp) }
            }
        }
        c.navi?.waypointMessage?.let { msg ->
            Row(verticalAlignment = Alignment.CenterVertically) { GIcon(Gi.MapPin, C.Amber, 26.dp); Spacer(Modifier.width(8.dp)); Text(msg, style = ts(22.sp, C.Amber), maxLines = 1) }
        }
    }
}

// ---------------------------------------------------------------- 경고등 자세히 · 문구로 찾기
@Composable
private fun WarnHead(e: WarnEntry, badge: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(shapeIcon(e.shape), warnDot(e.color), 64.dp); Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, style = ts(30.sp, weight = FontWeight.Bold, lineHeight = 38.sp), maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically) {
                GIcon(if (WarningCatalog.modeOf(e) == WarningCatalog.STEADY) Gi.Steady else Gi.Blink, warnDot(e.color), 24.dp); Spacer(Modifier.width(8.dp))
                Text("${e.color} · ${e.modeLabel}", style = ts(22.sp, warnDot(e.color)))
            }
        }
        if (badge) TestBadge("안내 예시 · 검토 전")
    }
}

@Composable
private fun JudgeRow(e: WarnEntry) {
    val (bg, fg, ic) = levelStyle(e.level)
    Row(Modifier.padding(top = 16.dp).fillMaxWidth().clip(R16).background(bg).padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        GIcon(ic, fg, 34.dp); Spacer(Modifier.width(14.dp))
        Text(e.judgement, style = ts(26.sp, fg, FontWeight.Bold), modifier = Modifier.weight(1f), maxLines = 2); Text("운행 판단", style = ts(22.sp, C.Mut))
    }
}

/** 칸 이름 앞에 아이콘(뜻 = 물음표, 지금 = 주의, 다음 = 렌치). 내용 문장은 그대로 둡니다. */
@Composable
private fun WarnSections(e: WarnEntry, big: Boolean = false) {
    listOf(Triple(Gi.CircleHelp, "뜻", e.meaning), Triple(Gi.TriangleAlert, "지금 할 일", e.now), Triple(Gi.Wrench, "다음에 할 일", e.next)).forEach { (ic, k, v) ->
        Row(Modifier.padding(top = if (big) 22.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
            GIcon(ic, C.Amber, if (big) 36.dp else 28.dp); Spacer(Modifier.width(12.dp)); Text(k, style = ts(if (big) 26.sp else 22.sp, C.Amber, FontWeight.SemiBold))
        }
        Text(v, style = ts(if (big) 30.sp else 24.sp, lineHeight = if (big) 42.sp else 34.sp), modifier = Modifier.padding(start = if (big) 48.dp else 38.dp))
    }
}

@Composable
private fun WarnOthers(c: DemoController, e: WarnEntry) {
    val others = WarningCatalog.ENTRIES.filter { it.shape == e.shape && it.id != e.id }
    if (others.isEmpty()) return
    Text("같은 모양", style = ts(22.sp, C.Amber, FontWeight.SemiBold), modifier = Modifier.padding(top = 18.dp))
    others.forEach { o ->
        Row(Modifier.padding(top = 8.dp).fillMaxWidth().clip(R16).background(C.Surf2).clickable { c.openWarnDetail(o.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            GIcon(shapeIcon(o.shape), warnDot(o.color), 34.dp); Spacer(Modifier.width(12.dp))
            Text(o.name, style = ts(22.sp), modifier = Modifier.weight(1f), maxLines = 1)
            GIcon(Gi.ChevronRight, C.Mint, 28.dp)
        }
    }
}

@Composable
private fun WarnFooter(c: DemoController) {
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.CarFront, C.Mut, 26.dp); Spacer(Modifier.width(8.dp))
        Text("계기판 안내가 다르면 차량 안내를 따르세요", style = ts(22.sp, C.Mut), modifier = Modifier.weight(1f))
        InfoButton("출처", listOf("아반떼 취급설명서 해당 절(확인 전)", "도감은 일반적인 기호의 뜻만 담은 예시입니다"), enabled = !c.driving)
    }
}

/** 정차 후 자세히: 지금 고른 경고(없으면 타이어 공기압 낮음)의 뜻 · 지금 할 일 · 다음에 할 일과 같은 모양의 다른 경고. */
@Composable
fun WarningDetailScreen(c: DemoController) = ScreenFrame {
    val r = c.warnResult() ?: WarningCatalog.find("주황", "타이어 공기압", WarningCatalog.STEADY)
    val e = r.primary
    Head(c, Tab.MANAGE, "${e.name} · 자세히")
    if (c.driving) { Locked("자세한 설명은 정차 후에 열립니다. 주행 중에는 운행 판단 한 줄만 봅니다"); Spacer(Modifier.weight(1f)) }
    else if (LocalWide.current) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        // 넓은 창: 왼쪽 = 기호 · 운행 판단 · 같은 모양, 오른쪽 = 뜻 · 지금 할 일 · 다음에 할 일
        Panel(Modifier.weight(1f).fillMaxHeight(), pad = 24.dp) {
            WarnHead(e)
            JudgeRow(e)
            listOfNotNull(r.colorNote, r.modeNote).forEach { Text(it, style = ts(22.sp, C.Amber, FontWeight.SemiBold, 30.sp), modifier = Modifier.padding(top = 10.dp)) }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { WarnOthers(c, e) }
        }
        Panel(Modifier.weight(1.2f).fillMaxHeight(), pad = 24.dp) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { WarnSections(e, big = true) }
            WarnFooter(c)
        }
    } else Panel(Modifier.weight(1f), pad = 24.dp) {
        WarnHead(e)
        JudgeRow(e)
        listOfNotNull(r.colorNote, r.modeNote).forEach { Text(it, style = ts(22.sp, C.Amber, FontWeight.SemiBold, 30.sp), modifier = Modifier.padding(top = 10.dp)) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { WarnSections(e); WarnOthers(c, e) }
        WarnFooter(c)
    }
    BottomButtons(Btn("경고등 찾기", Gi.Engine) { c.go(Screen.WARN_FIND) }, Btn("정비소 들르기", Gi.Wrench) { c.openService() }, RightKind.AMBER)
}

/**
 * 문구로 찾기: 도감 전체(이름 · 모양 · 낱말)에서 찾습니다.
 * 세로: 색 묶음 목록, 누르면 결과 화면. 넓은 창: 왼쪽 기호 격자(2열) + 오른쪽 고른 경고의 설명(목록 · 상세).
 */
@Composable
fun WarningSearchScreen(c: DemoController) = ScreenFrame {
    Head(c, Tab.MANAGE, "문구로 찾기")
    if (c.driving) { Locked("키보드 입력은 정차 중에만 씁니다"); Spacer(Modifier.weight(1f)) } else {
        val hits = WarningCatalog.search(c.warnQuery)
        val open: (WarnEntry) -> Unit = { e -> c.warnColor = e.color; c.warnShape = e.shape; c.warnMode = WarningCatalog.modeOf(e); c.go(Screen.WARN_RESULT) }
        if (LocalWide.current) {
            var pick by remember { mutableStateOf<String?>(null) }
            val sel = hits.firstOrNull { it.id == pick } ?: hits.firstOrNull()
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Column(Modifier.weight(1.25f).fillMaxHeight()) {
                    SearchField(c)
                    SearchCount(c, hits.size)
                    if (hits.isEmpty()) Text("맞는 경고가 없습니다. 색 → 모양 → 켜짐으로 찾아 보세요", style = ts(24.sp, C.Amber), modifier = Modifier.padding(top = 12.dp))
                    LazyVerticalGrid(GridCells.Fixed(2), Modifier.weight(1f).padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        WarningCatalog.COLORS.forEach { color ->
                            val group = hits.filter { it.color == color }
                            if (group.isEmpty()) return@forEach
                            item(key = "h-$color", span = { GridItemSpan(maxLineSpan) }) { ColorHeader(color) }
                            gridItems(group, key = { it.id }) { e -> SearchTile(e, on = e.id == sel?.id) { pick = e.id } }
                        }
                    }
                }
                if (sel != null) Panel(Modifier.weight(1f).fillMaxHeight(), pad = 24.dp) {
                    WarnHead(sel, badge = false)
                    JudgeRow(sel)
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { WarnSections(sel, big = true) }
                    Row(Modifier.padding(top = 12.dp).fillMaxWidth().height(80.dp).clip(R24).background(C.Surf2).border(2.dp, C.Amber, R24)
                        .clickable(role = Role.Button) { open(sel) }.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                        GIcon(Gi.Engine, C.Amber, 34.dp); Spacer(Modifier.width(12.dp))
                        Text("이 경고로 보기", style = ts(26.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                        GIcon(Gi.ChevronRight, C.Amber, 32.dp)
                    }
                } else Spacer(Modifier.weight(1f))
            }
        } else {
            SearchField(c)
            SearchCount(c, hits.size)
            if (hits.isEmpty()) Text("맞는 경고가 없습니다. 색 → 모양 → 켜짐으로 찾아 보세요", style = ts(24.sp, C.Amber), modifier = Modifier.padding(top = 12.dp))
            LazyColumn(Modifier.weight(1f).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                WarningCatalog.COLORS.forEach { color ->
                    val group = hits.filter { it.color == color }
                    if (group.isEmpty()) return@forEach
                    item(key = "h-$color") { ColorHeader(color) }
                    items(group, key = { it.id }) { e -> SearchTile(e, on = false) { open(e) } }
                }
            }
        }
    }
    BottomButtons(Btn("색으로 찾기", Gi.Engine) { c.go(Screen.WARN_FIND) }, Btn("말로 찾기", Gi.Mic) { c.findByVoiceButton() }, RightKind.AMBER)
}

@Composable
private fun SearchField(c: DemoController) {
    BasicTextField(c.warnQuery, { c.warnQuery = it }, textStyle = ts(28.sp), cursorBrush = SolidColor(C.Amber), singleLine = true,
        modifier = Modifier.fillMaxWidth().clip(R16).background(C.Surf2).border(2.dp, C.Amber, R16).padding(horizontal = 20.dp, vertical = 18.dp),
        decorationBox = { inner ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                GIcon(Gi.Search, C.Amber, 30.dp); Spacer(Modifier.width(12.dp))
                Box { if (c.warnQuery.isEmpty()) Text("타이어, 오일, 브레이크, 과열", style = ts(26.sp, C.Dim)); inner() }
            }
        })
}

@Composable
private fun SearchCount(c: DemoController, n: Int) {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("도감 $n/${WarningCatalog.ENTRIES.size}", style = ts(22.sp, C.Mut), modifier = Modifier.weight(1f))
        HonestyBadge("안내 예시 · 검토 전"); Spacer(Modifier.width(10.dp))
        InfoButton("시연 도감", listOf("모양 ${WarningCatalog.SHAPES.size}개의 일반적인 뜻만 담은 예시입니다", "아반떼 취급설명서로 검토하기 전입니다"), enabled = !c.driving)
    }
}

@Composable
private fun ColorHeader(color: String) {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(16.dp).clip(Pill).background(warnDot(color))); Spacer(Modifier.width(10.dp))
        Text(color, style = ts(22.sp, warnDot(color), FontWeight.SemiBold))
    }
}

/** 경고 한 칸: 그 색으로 칠한 기호 + 이름 + 켜짐 방식 · 운행 판단 아이콘. */
@Composable
private fun SearchTile(e: WarnEntry, on: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(R16).background(if (on) C.OnAmber else C.Surf).then(if (on) Modifier.border(2.dp, C.Amber, R16) else Modifier)
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        GIcon(shapeIcon(e.shape), warnDot(e.color), 44.dp); Spacer(Modifier.width(14.dp))
        Text(e.name, style = ts(26.sp, weight = FontWeight.SemiBold), maxLines = 2, lineHeight = 32.sp, modifier = Modifier.weight(1f))
        GIcon(if (WarningCatalog.modeOf(e) == WarningCatalog.STEADY) Gi.Steady else Gi.Blink, warnDot(e.color), 28.dp)
        Spacer(Modifier.width(10.dp))
        val (_, fg, lic) = levelStyle(e.level)
        GIcon(lic, fg, 30.dp)
    }
}

// ---------------------------------------------------------------- 진입 화면 · QR
@Composable
fun IntroOverlay() {
    Box(Modifier.fillMaxSize().background(C.Bg), contentAlignment = Alignment.Center) {
        Image(painterResource(R.drawable.launch_outline), "플레오스 슈퍼 케어 진입 화면", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
fun QrOverlay(link: String, title: String = "정비소에 보여 주기", sub: String = "정비사가 휴대폰으로 찍으면 기록 요약을 봅니다(24시간 읽기 전용)",
              badge: String = "모의 링크 · 공유 서버는 설계", onClose: () -> Unit) {
    val bits = remember(link) { QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 0, 0) }
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
        Column(Modifier.clip(R24).background(C.Surf).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = ts(30.sp, weight = FontWeight.Bold))
            Text(sub, style = Small)
            Canvas(Modifier.padding(20.dp).size(360.dp).background(Color.White)) {
                val n = bits.width; val cell = size.minDimension / (n + 8)
                for (y in 0 until n) for (x in 0 until n) if (bits.get(x, y)) drawRect(Color.Black, Offset((x + 4) * cell, (y + 4) * cell), Size(cell, cell))
            }
            MockBadge(badge)
            Text(link, style = ts(22.sp, C.Mut), modifier = Modifier.padding(top = 8.dp))
            Text("닫기", style = ts(26.sp, C.Mint, FontWeight.SemiBold), modifier = Modifier.padding(top = 14.dp).clickable(onClick = onClose))
        }
    }
}
