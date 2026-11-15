package app.pleosguard.demo.screens

import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pleosguard.demo.DemoController
import app.pleosguard.demo.R
import app.pleosguard.demo.RecordSnap
import app.pleosguard.demo.Screen
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.pleosguard.demo.logic.Quality
import app.pleosguard.demo.logic.WarnEntry
import app.pleosguard.demo.logic.WarnLevel
import app.pleosguard.demo.logic.WarningCatalog
import app.pleosguard.demo.car3d.*
import app.pleosguard.demo.ui.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.roundToInt

private fun kmh(mps: Float) = (mps * 3.6f).roundToInt()

/** 정차 중 머리 부분(머리줄 + 탭). */
@Composable
internal fun ColumnScope.ParkedHead(c: DemoController, tab: Tab?) {
    AppHeader { c.demoPanel = true }
    TabBar(tab) { t -> goTab(c, t) }
}

@Composable
fun DriveChip(c: DemoController) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            c.drivingUnknown -> StatusChip(Gi.CarFront, "주행으로 봄", C.Amber)
            c.driving -> StatusChip(Gi.CarFront, "주행 중", C.Mint)
            else -> StatusChip(Gi.Park, "정차 중", C.Mint)
        }
        if (c.drivingIsTest) { Spacer(Modifier.width(8.dp)); TestBadge() }
    }
}

@Composable
internal fun TitleRow(title: String, c: DemoController) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = ts(30.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
        DriveChip(c)
    }
}

// ---------------------------------------------------------------- 홈(정차 중)

/** 색 → 모양 → 켜짐을 아이콘 흐름으로(긴 안내 문장 대신). */
@Composable
fun FindFlow(modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 44.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        listOf(Gi.Steady to "색", Gi.Engine to "모양", Gi.Blink to "켜짐").forEachIndexed { i, (ic, t) ->
            if (i > 0) { Spacer(Modifier.width(10.dp)); GIcon(Gi.ChevronRight, C.Dim, 26.dp); Spacer(Modifier.width(10.dp)) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                GIcon(ic, C.Amber, size); Text(t, style = ts(22.sp, C.Mut, FontWeight.SemiBold))
            }
        }
    }
}

/** 차 점검 신호 이름 → 아이콘(홈 점검 카드 · 출발 전 점검이 같이 씁니다). */
fun checkIcon(name: String): Gi = when (name) {
    "엔진오일" -> Gi.Oil
    "브레이크액" -> Gi.Brake
    "워셔액" -> Gi.Washer
    else -> Gi.Gauge
}


/**
 * 케어 홈(정차 중, 세로): 3D 아반떼 + 케어 상태 카드 6개(다음 정비 · 리콜 · 짐 알림 · 구독 · 폰 연결 · 증상 기록).
 * 차의 부위를 고르면 카드 자리에 그 부위의 증상 단추와 "폰으로 기록"이 나옵니다.
 */
@Composable
fun HomeParked(c: DemoController) = ScreenFrame {
    ParkedHead(c, null)
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        DriveChip(c)
        Spacer(Modifier.width(12.dp))
        StatusChip(Gi.ShieldCheck, "안심 케어", C.Purple)
        Spacer(Modifier.weight(1f))
        GIcon(Gi.CarFront, C.Mut, 30.dp); Spacer(Modifier.width(8.dp)); Text("아반떼", style = ts(24.sp, C.Mut))
    }
    HomeCarCard(c, Modifier.padding(top = 16.dp).weight(1f), title = "내 차 케어")
    if (c.selectedPart != null) Panel(Modifier.padding(top = 16.dp)) { PartPanel(c, cols = 4) }
    else CareGrid(c, Modifier.padding(top = 16.dp).height(236.dp), cols = 3)
    HomeButtons(c)
}

/**
 * 홈 아래 버튼 2개(세로 · 가로 공통). 처음 쓰는 사람도 알 수 있게 이름 아래에 언제 · 무엇을 하는지 한 줄을 둡니다(2026-11-14 사용자 지적: "지금 기록"만으로는 뜻을 모름).
 * 왼쪽 = 이상할 때 그 순간의 차 상태를 남기는 증상 기록, 오른쪽 = 증상을 골라 대처 방법을 보는 도움 흐름.
 */
@Composable
fun HomeButtons(c: DemoController) {
    BottomButtons(Btn("증상 기록하기", Gi.Rec, "이상할 때 차 상태 저장") { c.recordNow() },
        Btn("도움이 필요해요", Gi.Lifebuoy, "증상별 대처 방법 안내", trailing = Gi.ChevronRight) { c.openHelp() }, RightKind.HELP_DARK)
}

/**
 * 3D 아반떼 카드(세로 · 가로 공통). 끌어서 돌리고, 차의 부위나 아래 부위 칩을 누르면 고릅니다.
 * 고른 부위는 [PartOrCheck]가 증상 버튼으로 이어 받습니다. '실내'를 누르면 덮개가 반투명해져 안이 보입니다.
 * 주행 중에는 돌리기를 막습니다(정차 중 화면이지만 주행 신호가 들어와도 손짓을 받지 않게).
 */
@Composable
fun HomeCarCard(c: DemoController, modifier: Modifier, title: String? = null) {
    val view = rememberCarViewState(Orbit.HOME)
    var interior by remember { mutableStateOf(false) }
    val part = CarPart.ofLabel(c.selectedPart)
    fun choose(p: CarPart?, fly: Boolean) {
        c.selectedPart = p?.label
        if (!fly || p == null) return
        if (p == CarPart.CABIN) { interior = true; view.flyTo(Orbit.INTERIOR) }
        else { interior = false; view.flyTo(Orbit.focus(p)) }
    }
    Panel(modifier, pad = 16.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (title != null) Text(title, style = ts(28.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            else Spacer(Modifier.weight(1f))
            ViewToggle(Gi.Seat, "실내", interior) {
                interior = !interior
                view.flyTo(if (interior) Orbit.INTERIOR else Orbit.HOME)
            }
            Spacer(Modifier.width(10.dp))
            ViewToggle(Gi.Rotate, "처음", false) { interior = false; c.selectedPart = null; view.flyTo(Orbit.HOME) }
        }
        Car3D(view, Modifier.weight(1f).fillMaxWidth(), mode = if (interior) CarMode.INTERIOR else CarMode.EXTERIOR,
            selected = part, onSelect = { p -> choose(p, fly = false) }, interactive = !c.driving)
        PartChips(part, onPick = { p -> choose(p, fly = true) }, Modifier.padding(top = 12.dp))
    }
}

/** 고른 부위의 증상 단추 4개와 "폰으로 소리 · 진동 기록". 세로는 차 그림 아래, 가로는 오른쪽 패널에 둡니다. */
@Composable
fun ColumnScope.PartPanel(c: DemoController, cols: Int) {
    val part = c.selectedPart ?: return
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(CarPart.ofLabel(part)?.icon() ?: Gi.Wheel, C.Mint, 40.dp); Spacer(Modifier.width(12.dp))
        Text(part, style = ts(28.sp, weight = FontWeight.Bold), modifier = Modifier.weight(1f))
        Box(Modifier.size(56.dp).clip(Pill).background(C.Surf2).clickable(role = Role.Button) { c.selectedPart = null }
            .semantics { contentDescription = "부위 고르기 닫기" }, contentAlignment = Alignment.Center) { GIcon(Gi.Close, C.Mut, 26.dp) }
    }
    listOf("소음" to Gi.AudioLines, "떨림" to Gi.Activity, "쏠림" to Gi.Steering, "잘 모르겠음" to Gi.CircleHelp).chunked(cols).forEach { row ->
        Row(Modifier.padding(top = 10.dp).height(68.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { (name, ic) ->
                Box(Modifier.weight(1f).fillMaxHeight().clip(R16).background(C.Surf2).clickable { c.recordNow(symptom = name, part = part) }) {
                    TileContent(ic, C.Mint, name)
                }
            }
        }
    }
    Row(Modifier.padding(top = 10.dp).height(68.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Triple("noise", Gi.AudioLines, "폰으로 소리"), Triple("vibration", Gi.Vibrate, "폰으로 진동")).forEach { (kind, ic, label) ->
            val waiting = c.phoneWait?.kind == kind
            Box(Modifier.weight(1f).fillMaxHeight().clip(R16).background(if (waiting) C.OnMint else C.Surf2).border(1.5.dp, if (waiting) C.Mint else C.Line, R16)
                .clickable { if (waiting) c.cancelPhoneWait() else c.requestPhone(kind, null, part) }) {
                TileContent(ic, C.Mint, if (waiting) "폰에서 기록 중" else label, trailing = Gi.Smartphone)
            }
        }
    }
}

// ---------------------------------------------------------------- 홈(주행 중): 큰 버튼 3개
@Composable
fun HomeDriving(c: DemoController, voiceListening: Boolean, partial: String) = ScreenFrame {
    AppHeader { c.demoPanel = true }
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        DriveChip(c); Spacer(Modifier.width(12.dp)); StatusChip(Gi.Users, "보호자 2명", C.Purple)
    }
    Row(Modifier.fillMaxWidth().weight(1f).padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // 주행 중: 큰 아이콘 + 이름 + 한 줄(6자 안팎). 2026-11-14 아이콘 조사 규칙 6 · 11.
        TallBtn("증상 기록", "차 상태 저장", Gi.Rec, C.Mint, C.Surf2, Modifier.weight(1f)) { c.recordNow() }
        TallBtn("말로 하기", if (voiceListening) "듣는 중\n${partial.ifEmpty { "말씀하세요" }}" else "8초 듣기",
            Gi.Mic, if (voiceListening) C.Mint else C.Fg, if (voiceListening) C.OnMint else C.Surf2, Modifier.weight(1f)) { c.talk() }
        TallBtn("도움이\n필요해요", "대처 안내", Gi.Lifebuoy, C.Fg, C.Help, Modifier.weight(1f)) { c.go(Screen.SITUATION) }
    }
    SignalLine(c)
    Row(Modifier.fillMaxWidth().padding(top = 12.dp).clip(R16).background(C.Surf).padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.Lock, C.Mut, 30.dp); Spacer(Modifier.width(16.dp))
        Text("자세한 설명 · 점검은 정차 후", style = ts(22.sp, C.Mut))
    }
}

@Composable
private fun TallBtn(label: String, sub: String, icon: Gi, tint: Color, bg: Color, modifier: Modifier, onClick: () -> Unit) {
    // 단추가 화면 높이를 채우므로 아이콘 · 글자도 단추 높이에 비례해 키웁니다(TallContent).
    Box(modifier.fillMaxHeight().clip(R24).background(bg).border(1.5.dp, C.Line, R24).clickable(role = Role.Button, onClick = onClick).padding(12.dp)) {
        TallContent(icon, tint, label, sub, if (bg == C.Help) C.Fg else C.Mut)
    }
}

/** 실제로 받은 SDK 값을 한 줄로 보여 줍니다(시연에서 실제 신호와 시험 입력을 구별하려고 둡니다). */
@Composable
fun SignalLine(c: DemoController) {
    val (v, q) = c.speedView
    val (b, bTest) = c.brakeView
    // 차속 · 브레이크를 아이콘 칩으로(무엇 = 아이콘, 값 = 짧은 글). 출처(AAOS · Vehicle SDK)는 '지금 상태'의 i 단추에 둡니다.
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatusChip(Gi.Gauge, when { v == null -> "확인 불가"; q == Quality.STALE -> "${kmh(v)}km/h 오래됨"; else -> "${kmh(v)}km/h" },
            if (v == null || q == Quality.STALE) C.Amber else C.Mint)
        StatusChip(Gi.Pedal, when (b) { true -> "밟음"; false -> "안 밟음"; null -> "확인 불가" }, if (b == null) C.Amber else C.Mint)
        if (q == Quality.TEST_INPUT || bTest) TestBadge()
    }
}

// ---------------------------------------------------------------- 경고등 찾기
// 색·모양 이름은 도감(logic/WarningCatalog.kt)과 같은 값을 씁니다. 화면 글자이면서 도감을 찾는 열쇠입니다.
private val COLORS = WarningCatalog.COLORS.zip(listOf(Color(0xFFFF4D4D), Color(0xFFFFA43A), Color(0xFF3DDC84), Color(0xFF4C9BFF)))
private val SHAPES = WarningCatalog.SHAPES.zip(listOf(Gi.Engine, Gi.Oil, Gi.Brake, Gi.Battery, Gi.Tire, Gi.Belt, Gi.Coolant, Gi.Airbag))

@Composable
fun WarningFind(c: DemoController) = ScreenFrame {
    ParkedHead(c, Tab.MANAGE)
    TitleRow("경고등 찾기", c)
    WarningFindBody(c)
    Spacer(Modifier.weight(1f))
    if (c.driving) Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.Mic, C.Amber, 28.dp); Spacer(Modifier.width(10.dp)); Text("주행 중에는 말로 찾기", style = ts(22.sp, C.Amber))
    }
    BottomButtons(Btn("문구로 찾기", Gi.Search) { c.openWarnSearch() }, Btn("말로 찾기", Gi.Mic) { c.findByVoiceButton() }, RightKind.AMBER)
}

/** 경고등 찾기 본문(색 → 모양 → 켜짐). 세로는 한 화면, 가로는 왼쪽 칸에 둡니다. */
@Composable
fun ColumnScope.WarningFindBody(c: DemoController) {
    val locked = c.driving
    Panel(pad = 20.dp) {
        SectionLabel(1, "무슨 색인가요", "색 → 모양 → 켜짐")
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            COLORS.forEach { (name, dot) ->
                SelChip(name, c.warnColor == name, Modifier.weight(1f), enabled = !locked, dot = dot) { c.warnColor = name }
            }
        }
    }
    Panel(Modifier.padding(top = 14.dp), pad = 20.dp) {
        SectionLabel(2, "어떤 모양인가요", "계기판과 같은 모양")
        SHAPES.chunked(4).forEach { row ->
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { (name, ic) ->
                    val on = c.warnShape == name
                    Column(Modifier.weight(1f).height(140.dp).clip(R24).background(if (on) C.OnAmber else C.Surf2)
                        .then(if (on) Modifier.border(2.dp, C.Amber, R24) else Modifier).clickable(enabled = !locked) { c.warnShape = name },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        GIcon(ic, if (on) C.Amber else C.Fg, 60.dp); Spacer(Modifier.height(12.dp))
                        Text(name, style = ts(22.sp, if (on) C.Amber else C.Fg, if (on) FontWeight.SemiBold else FontWeight.Normal))
                    }
                }
            }
        }
    }
    Panel(Modifier.padding(top = 14.dp), pad = 20.dp) {
        SectionLabel(3, "켜져 있나요, 깜박이나요")
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf(WarningCatalog.STEADY to Gi.Steady, WarningCatalog.BLINK to Gi.Blink).forEach { (name, ic) ->
                SelChip(name, c.warnMode == name, Modifier.weight(1f), enabled = !locked, icon = ic) {
                    c.warnMode = name
                    if (c.warnColor != null && c.warnShape != null) c.go(Screen.WARN_RESULT)
                }
            }
        }
    }
}

@Composable
private fun SelChip(label: String, on: Boolean, modifier: Modifier, enabled: Boolean = true, dot: Color? = null, icon: Gi? = null, onClick: () -> Unit) {
    Row(modifier.height(72.dp).clip(R16).background(if (on) C.OnAmber else C.Surf2).then(if (on) Modifier.border(2.dp, C.Amber, R16) else Modifier)
        .clickable(enabled = enabled, onClick = onClick), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) { Box(Modifier.size(26.dp).clip(Pill).background(dot)); Spacer(Modifier.width(12.dp)) }
        if (icon != null) { GIcon(icon, if (on) C.Amber else C.Fg, 30.dp); Spacer(Modifier.width(12.dp)) }
        Text(label, style = ts(24.sp, if (on) C.Amber else C.Fg, if (on) FontWeight.SemiBold else FontWeight.Normal))
    }
}

@Composable
fun WarningResult(c: DemoController) = ScreenFrame {
    ParkedHead(c, Tab.MANAGE)
    WarningResultBody(c, compact = false)
    WarningResultButtons(c)
}

@Composable
fun WarningResultButtons(c: DemoController) =
    BottomButtons(Btn("정차 후 자세히", Gi.Park) { c.openWarnDetail() }, Btn("정비소 들르기", Gi.Wrench) { c.openService() }, RightKind.AMBER)

/** 경고등 색 이름 → 점 색. */
fun warnDot(color: String): Color = COLORS.firstOrNull { it.first == color }?.second ?: C.Mut

/** 경고등 모양 이름 → 기호. */
fun shapeIcon(shape: String): Gi = SHAPES.firstOrNull { it.first == shape }?.second ?: Gi.Engine

/** 운행 판단 줄의 바탕색 · 글자색 · 기호. 빨강 = 바로 세우기 · 바로 조치, 주황 = 점검, 민트 = 상태 표시. */
fun levelStyle(l: WarnLevel): Triple<Color, Color, Gi> = when (l) {
    WarnLevel.STOP -> Triple(C.ECard, C.Red, Gi.OctagonAlert)
    WarnLevel.URGENT -> Triple(C.ECard, C.Red, Gi.TriangleAlert)
    WarnLevel.SERVICE -> Triple(C.OnAmber, C.Amber, Gi.Wrench)
    WarnLevel.INFO -> Triple(C.OnMint, C.Mint, Gi.CircleCheck)
}

/** 후보 카드 아래 줄: 고른 조건과 같은지, 무엇이 다른지. */
private fun candidateNote(e: WarnEntry, color: String?, mode: String?): String = when {
    e.color != color -> "색이 다릅니다 · 보통 ${e.color}"
    e.blink != null && e.blink != (mode == WarningCatalog.BLINK) -> "켜짐 방식이 다릅니다"
    else -> "고른 조건과 같습니다"
}

/**
 * 후보 · 운행 판단 · 설명(경고등 시연 도감, logic/WarningCatalog.kt). 64개 조합 모두 결과가 나옵니다.
 * 고른 색이 그 모양의 보통 색과 다르면 가까운 후보와 "색을 다시 확인하세요" 안내를 냅니다.
 * 세로는 따로 한 화면, 가로는 찾기 오른쪽 칸([compact]: 후보를 위아래로 쌓음)에 둡니다.
 */
@Composable
fun ColumnScope.WarningResultBody(c: DemoController, compact: Boolean) {
    val r = c.warnResult()
    val n = r?.all?.size ?: 0
    Text(when { r == null -> "색 · 모양 · 켜짐을 모두 골라 주세요"; r.colorNote != null -> "가까운 후보 ${n}개"; else -> "후보 ${n}개를 찾았습니다" },
        style = ts(30.sp, weight = FontWeight.SemiBold), modifier = Modifier.padding(top = if (compact) 0.dp else 20.dp))
    Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOfNotNull(c.warnColor, c.warnShape?.let { "$it 모양" }, c.warnMode?.removeSuffix(" 있어요")?.replace("계속 켜져", "계속 켜짐")).forEach {
            Box(Modifier.clip(Pill).background(C.Surf2).padding(horizontal = 16.dp, vertical = 8.dp)) { Text(it, style = ts(22.sp), maxLines = 1) }
        }
        Spacer(Modifier.weight(1f))
        Text("다시 고르기", style = ts(24.sp, C.Mint, FontWeight.SemiBold), maxLines = 1, modifier = Modifier.clickable { c.warnColor = null; c.warnShape = null; c.warnMode = null; c.go(Screen.WARN_FIND) })
    }
    if (r == null) {
        Panel(Modifier.weight(1f)) { FindFlow(Modifier.fillMaxWidth().padding(top = 24.dp)) }
        return
    }
    listOfNotNull(r.colorNote, r.modeNote).forEach { note ->
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(R16).background(C.TestBg).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            GIcon(Gi.TriangleAlert, C.Amber, 28.dp); Spacer(Modifier.width(12.dp))
            Text(note, style = ts(22.sp, C.Amber, FontWeight.SemiBold, 30.sp), maxLines = 3)
        }
    }
    if (compact) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        r.all.forEach { e -> Candidate(e, candidateNote(e, c.warnColor, c.warnMode), e === r.primary, Modifier.fillMaxWidth(), compact = true) }
    } else Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        r.all.forEach { e -> Candidate(e, candidateNote(e, c.warnColor, c.warnMode), e === r.primary, Modifier.weight(1f)) }
    }
    val e = r.primary
    val (bg, fg, ic) = levelStyle(e.level)
    Panel(Modifier.padding(top = 16.dp).weight(1f), pad = 16.dp) {
        Row(Modifier.fillMaxWidth().clip(R16).background(bg).padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            GIcon(ic, fg, 40.dp); Spacer(Modifier.width(16.dp))
            Text(e.judgement, style = ts(30.sp, fg, FontWeight.Bold, 38.sp), modifier = Modifier.weight(1f), maxLines = 2)
            Text("운행 판단", style = ts(22.sp, C.Mut))
        }
        // 설명은 정차 중에 읽는 부분이라 길면 스크롤합니다(주행 중에는 운행 판단 한 줄만 봅니다).
        Column(Modifier.padding(horizontal = 8.dp).padding(top = 16.dp).weight(1f).verticalScroll(rememberScrollState())) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(e.name, style = ts(30.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 2); TestBadge("안내 예시 · 검토 전")
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GIcon(Gi.CircleHelp, C.Amber, 28.dp); Spacer(Modifier.width(10.dp)); Text("뜻", style = ts(22.sp, C.Amber, FontWeight.SemiBold))
            }
            Text(listOf(e.meaning, e.now, e.next).joinToString("\n"), style = ts(24.sp, lineHeight = 34.sp))
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                GIcon(Gi.CarFront, C.Mut, 26.dp); Spacer(Modifier.width(8.dp))
                Text("계기판 안내가 다르면 차량 안내를 따르세요", style = ts(22.sp, C.Mut), modifier = Modifier.weight(1f))
                InfoButton("출처", listOf("아반떼 취급설명서 해당 절(확인 전)", "주행 중에는 위의 운행 판단 한 줄만 보입니다"), enabled = !c.driving)
            }
        }
    }
}

@Composable
private fun Candidate(e: WarnEntry, note: String, on: Boolean, modifier: Modifier, compact: Boolean = false) {
    val dot = warnDot(e.color)
    Column(modifier.height(if (compact) 120.dp else 190.dp).clip(R24).background(if (on) C.OnAmber else C.Surf).border(2.dp, if (on) C.Amber else C.Line, R24)
        .padding(horizontal = 20.dp, vertical = if (compact) 14.dp else 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GIcon(shapeIcon(e.shape), dot, if (compact) 44.dp else 60.dp); Spacer(Modifier.width(16.dp))
            Text(e.name, style = ts(24.sp, weight = FontWeight.SemiBold, lineHeight = 30.sp), modifier = Modifier.weight(1f), maxLines = 2)
            if (on) GIcon(Gi.CircleCheck, C.Amber, 32.dp)
        }
        Spacer(Modifier.weight(1f))
        val same = note == "고른 조건과 같습니다"
        if (compact) Text("${e.color} · ${e.modeLabel} · $note", style = ts(22.sp, if (on) C.Amber else C.Mut, FontWeight.SemiBold), maxLines = 1)
        else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(18.dp).clip(Pill).background(dot)); Spacer(Modifier.width(8.dp))
                Text("${e.color} · ${e.modeLabel}", style = ts(22.sp, if (on) C.Amber else C.Fg, FontWeight.SemiBold), maxLines = 1)
            }
            Text(note, style = ts(22.sp, if (same) C.Mint else C.Mut), maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- 증상 기록(차량 상태 시간표)
@Composable
fun RecordScreen(c: DemoController, partial: String, listening: Boolean) = ScreenFrame {
    ParkedHead(c, Tab.RECORD)
    val r = c.lastRecord
    TitleRow(if (r == null) "증상 기록" else "증상을 기록했습니다", c)
    if (r == null) {
        Panel(Modifier.weight(1f)) {
            Text("아직 기록이 없습니다", style = Head)
            Text("홈의 \"증상 기록하기\"를 누르면 누른 순간 앞 20초와 뒤 10초의 차 상태를 남깁니다", style = Small)
        }
        BottomButtons(Btn("홈으로", Gi.Home) { c.go(Screen.HOME) }, Btn("증상 기록하기", Gi.Rec, "지금 차 상태 저장") { c.recordNow() }, RightKind.MINT)
    } else RecordBody(c, r, partial, listening)
}

@Composable
private fun ColumnScope.RecordBody(c: DemoController, r: RecordSnap, partial: String, listening: Boolean) {
    c.tick // 기록 뒤 10초 동안 시간표를 채웁니다
    val ev = r.evidence.lastOrNull()
    if (LocalWide.current) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        // 넓은 창: 왼쪽 = 차량 상태 시간표 · 폰 증거, 오른쪽 = 증상 · 자세히(심한 정도 · 빈도 · 나는 때 · 폰으로 더 기록)
        Column(Modifier.weight(1.35f).fillMaxHeight()) {
            TimelinePanel(c, r, Modifier.weight(1f))
            if (ev != null) EvidenceCard(c, ev, Modifier.padding(top = 14.dp).weight(0.85f))
            else CabinPanel(Modifier.padding(top = 14.dp), vertical = false)
        }
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
            SymptomPanel(c, r, partial, listening, Modifier, cols = 3)
            RecordDetailPicker(c, r, Modifier.padding(top = 14.dp))
        }
    } else {
        TimelinePanel(c, r, Modifier.height(360.dp))
        Column(Modifier.padding(top = 14.dp).weight(1f).verticalScroll(rememberScrollState())) {
            SymptomPanel(c, r, partial, listening, Modifier, cols = 3)
            if (ev != null) EvidenceCard(c, ev, Modifier.padding(top = 14.dp).height(380.dp))
            RecordDetailPicker(c, r, Modifier.padding(top = 14.dp))
            CabinPanel(Modifier.padding(top = 14.dp), vertical = false)
        }
    }
    BottomButtons(Btn("기록 끝내기", Gi.CircleCheck) { c.go(Screen.HOME); c.showToast("기록을 저장했습니다") }, Btn("말로 메모", Gi.Mic) { c.memoByVoice() }, RightKind.MINT)
}

@Composable
private fun TimelinePanel(c: DemoController, r: RecordSnap, modifier: Modifier) = Panel(modifier, pad = 20.dp) {
    val (base, samples) = c.recordSamples(r)
    val at = samples.minByOrNull { abs(it.t - base) }
    val speedTest = samples.any { it.speedQ == Quality.TEST_INPUT }
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.Activity, C.Mint, 30.dp); Spacer(Modifier.width(10.dp))
        Text("차량 상태 시간표", style = ts(26.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
        if (speedTest) TestBadge("차속 · 시험 입력")
    }
    Text("${DemoController.hms(r.wall)} 기록 · 앞 20초와 뒤 10초", style = Small)
    Spacer(Modifier.height(12.dp))
    val rows = listOf(
        Triple(Gi.Gauge, "차속", at?.speed?.let { "시속 ${kmh(it)}km" } ?: "확인 불가"),
        Triple(Gi.Pedal, "브레이크", when (at?.brake) { true -> "밟음"; false -> "안 밟음"; null -> "확인 불가" }),
        Triple(Gi.Steering, "바퀴 방향", at?.steering?.let { if (abs(it) < 5) "거의 직진" else if (it < 0) "왼쪽 ${abs(it).roundToInt()}도" else "오른쪽 ${it.roundToInt()}도" } ?: "확인 불가"),
        Triple(Gi.Turn, "방향지시등", when (at?.turn) { TurnSignalValue.LEFT -> "왼쪽"; TurnSignalValue.RIGHT -> "오른쪽"; TurnSignalValue.OFF -> "꺼짐"; null -> "확인 불가" }),
    )
    Row(Modifier.weight(1f)) {
        Column(Modifier.width(220.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
            rows.forEachIndexed { i, (ic, name, v) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GIcon(ic, listOf(C.Mint, C.Amber, C.Purple, C.Fg)[i], 34.dp); Spacer(Modifier.width(12.dp))
                    Column { Text(name, style = ts(24.sp, weight = FontWeight.SemiBold)); Text(v, style = ts(22.sp, C.Mut)) }
                }
            }
        }
        Timeline(samples, base, Modifier.weight(1f).fillMaxHeight())
    }
    Row(Modifier.fillMaxWidth().padding(start = 220.dp)) {
        Text("-20초", style = ts(22.sp, C.Mut)); Spacer(Modifier.weight(1f)); Text("-10초", style = ts(22.sp, C.Mut)); Spacer(Modifier.weight(1f))
        Text("기록 시점", style = ts(22.sp, C.Mint, FontWeight.SemiBold)); Spacer(Modifier.weight(0.6f)); Text("+10초", style = ts(22.sp, C.Mut))
    }
}

/** 함께 남긴 상태(공조 · 창문 · 와이퍼). 넓은 창 오른쪽 칸에서는 위아래로 둡니다. */
@Composable
private fun CabinPanel(modifier: Modifier, vertical: Boolean) = Panel(modifier, pad = 20.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) { Text("함께 남긴 상태", style = ts(22.sp, C.Mut), modifier = Modifier.weight(1f)); TestBadge() }
    val items = listOf(Gi.Fan to "에어컨 켜짐", Gi.Window to "창문 닫힘", Gi.Wiper to "와이퍼 꺼짐")
    if (vertical) Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (ic, t) -> Column(Modifier.weight(1f).clip(R16).background(C.Surf2).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            GIcon(ic, C.Mint, 34.dp); Spacer(Modifier.height(6.dp)); Text(t, style = ts(22.sp), maxLines = 1)
        } }
    } else Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
        items.forEach { (ic, t) -> Row(verticalAlignment = Alignment.CenterVertically) { GIcon(ic, C.Mint, 30.dp); Spacer(Modifier.width(10.dp)); Text(t, style = ts(22.sp)) } }
    }
}

@Composable
private fun SymptomPanel(c: DemoController, r: RecordSnap, partial: String, listening: Boolean, modifier: Modifier, cols: Int) = Panel(modifier, pad = 20.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("증상", style = ts(24.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
        val memo = if (listening) "듣는 중: ${partial.ifEmpty { "말씀하세요" }}" else r.memo?.let { "\"$it\"" }
        if (memo != null) { GIcon(Gi.Mic, C.Mint, 28.dp); Spacer(Modifier.width(6.dp)); Text(memo, style = ts(22.sp, C.Mint), maxLines = 1) }
        if (r.memoTest && !listening) { Spacer(Modifier.width(8.dp)); TestBadge() }
    }
    listOf("떨림" to Gi.Activity, "소음" to Gi.AudioLines, "쏠림" to Gi.Steering, "냄새" to Gi.Flame, "타이어" to Gi.Wheel, "잘 모르겠음" to Gi.CircleHelp).chunked(cols).forEach { row ->
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { (name, ic) ->
                val on = r.symptom == name
                Row(Modifier.weight(1f).height(if (cols == 2) 76.dp else 68.dp).clip(R16).background(if (on) C.OnMint else C.Surf2).then(if (on) Modifier.border(2.dp, C.Mint, R16) else Modifier)
                    .clickable { c.updateRecord(r.id) { it.copy(symptom = name) } }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    GIcon(ic, if (on) C.Mint else C.Fg, 30.dp); Spacer(Modifier.width(10.dp)); Text(name, style = ts(24.sp, if (on) C.Mint else C.Fg))
                }
            }
        }
    }
}

/** 시간표: 차속 막대, 브레이크 구간, 바퀴 방향 점, 방향지시등 구간. 값이 없는 구간은 비워 둡니다. */
@Composable
private fun Timeline(samples: List<app.pleosguard.demo.Sample>, t0: Long, modifier: Modifier) {
    Canvas(modifier) {
        val rowH = size.height / 4f; val pad = 10f
        val x = { t: Long -> ((t - (t0 - 20_000)).toFloat() / 30_000f) * size.width }
        for (i in 0 until 4) drawRoundRect(C.Surf2, Offset(0f, i * rowH + pad), Size(size.width, rowH - 2 * pad), CornerRadius(16f))
        val maxV = (samples.mapNotNull { it.speed }.maxOrNull() ?: 1f).coerceAtLeast(5f)
        samples.filterIndexed { i, _ -> i % 5 == 0 }.forEach { s ->
            val v = s.speed ?: return@forEach
            val h = (v / maxV) * (rowH - 4 * pad)
            drawRect(if (s.speedQ == Quality.TEST_INPUT) C.Mint.copy(alpha = 0.75f) else C.Mint, Offset(x(s.t), rowH - pad - 8f - h), Size(size.width / 70f, h))
        }
        fun segments(row: Int, color: Color, on: (app.pleosguard.demo.Sample) -> Boolean) {
            var start: Long? = null
            samples.forEachIndexed { i, s ->
                val o = on(s)
                if (o && start == null) start = s.t
                if ((!o || i == samples.lastIndex) && start != null) {
                    val st = start!!; drawRoundRect(color, Offset(x(st), row * rowH + rowH * 0.3f), Size((x(s.t) - x(st)).coerceAtLeast(8f), rowH * 0.4f), CornerRadius(12f)); start = null
                }
            }
        }
        segments(1, C.Amber) { it.brake == true }
        samples.filterIndexed { i, _ -> i % 8 == 0 }.forEach { s ->
            val a = s.steering ?: return@forEach
            drawCircle(C.Purple, 7f, Offset(x(s.t), 2 * rowH + rowH / 2 + (a / 540f) * (rowH * 0.35f)))
        }
        segments(3, C.Fg) { it.turn == TurnSignalValue.LEFT || it.turn == TurnSignalValue.RIGHT }
        val xr = x(t0)
        drawLine(C.Mint, Offset(xr, 0f), Offset(xr, size.height), 5f)
    }
}
