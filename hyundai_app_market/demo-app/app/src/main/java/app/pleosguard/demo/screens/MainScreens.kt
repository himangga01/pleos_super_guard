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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pleosguard.demo.DemoController
import app.pleosguard.demo.R
import app.pleosguard.demo.RecordSnap
import app.pleosguard.demo.Screen
import app.pleosguard.demo.logic.Quality
import app.pleosguard.demo.ui.*
import kotlin.math.abs
import kotlin.math.roundToInt

private fun kmh(mps: Float) = (mps * 3.6f).roundToInt()

/** 정차 중 머리 부분(머리줄 + 탭). */
@Composable
private fun ColumnScope.ParkedHead(c: DemoController, tab: Tab?) {
    AppHeader { c.demoPanel = true }
    TabBar(tab) { t ->
        when (t) {
            Tab.WARNING -> c.go(Screen.WARN_FIND)
            Tab.RECORD -> c.go(Screen.RECORD)
            Tab.GUARDIAN -> c.go(Screen.GUARDIAN)
            Tab.SERVICE -> c.go(Screen.SERVICE)
        }
    }
}

@Composable
fun DriveChip(c: DemoController) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            c.drivingUnknown -> StatusChip(Gi.CarFront, "주행 상태 확인 불가 · 주행 중으로 봄", C.Amber)
            c.driving -> StatusChip(Gi.CarFront, "주행 중", C.Mint)
            else -> StatusChip(Gi.Park, "정차 중", C.Mint)
        }
        if (c.drivingIsTest) { Spacer(Modifier.width(8.dp)); TestBadge() }
    }
}

@Composable
private fun TitleRow(title: String, c: DemoController) {
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = ts(30.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
        DriveChip(c)
    }
}

// ---------------------------------------------------------------- 홈(정차 중)
private val SPOTS = listOf(Triple(467f / 1600f, 496f / 899f, "앞부분"), Triple(1001f / 1600f, 220f / 899f, "실내"), Triple(1155f / 1600f, 759f / 899f, "앞 타이어"), Triple(1542f / 1600f, 427f / 899f, "뒤쪽"))

@Composable
fun HomeParked(c: DemoController) = ScreenFrame {
    ParkedHead(c, null)
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        DriveChip(c)
        Spacer(Modifier.width(12.dp))
        StatusChip(Gi.Users, "보호자 2명 준비됨", C.Purple)
        Spacer(Modifier.weight(1f))
        GIcon(Gi.CarFront, C.Mut, 30.dp); Spacer(Modifier.width(8.dp)); Text("아반떼", style = ts(24.sp, C.Mut))
    }
    Panel(Modifier.padding(top = 16.dp).weight(1f)) {
        Text("어디에서 이상을 느끼셨나요", style = ts(28.sp, weight = FontWeight.SemiBold))
        Text("점이나 이름을 누르세요", style = ts(22.sp, C.Mut))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val imgW = maxWidth; val imgH = imgW * (899f / 1600f)
            val top = (maxHeight - imgH) / 2
            Image(painterResource(R.drawable.avante_cutout), "디 올 뉴 아반떼", Modifier.width(imgW).height(imgH).offset(y = top), contentScale = ContentScale.Fit)
            SPOTS.forEach { (fx, fy, name) ->
                val on = c.selectedPart == name
                val x = imgW * fx; val y = top + imgH * fy
                Box(Modifier.offset(x = x - 24.dp, y = y - 24.dp).size(48.dp).clip(Pill).border(4.dp, C.Mint, Pill)
                    .background(if (on) C.Mint else C.Bg.copy(alpha = 0.4f)).clickable(role = Role.Button) { c.selectedPart = name },
                    contentAlignment = Alignment.Center) { Box(Modifier.size(16.dp).clip(Pill).background(if (on) C.Dark else C.Mint)) }
                Box(Modifier.offset(x = x - 50.dp, y = y - 76.dp).clip(R16).background(C.OnMint).clickable { c.selectedPart = name }
                    .padding(horizontal = 12.dp, vertical = 4.dp)) { Text(name, style = ts(24.sp, C.Mint, FontWeight.SemiBold)) }
            }
        }
    }
    Panel(Modifier.padding(top = 16.dp)) {
        val part = c.selectedPart
        if (part != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GIcon(Gi.Wheel, C.Mint, 44.dp); Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("고른 부위: $part", style = ts(26.sp, weight = FontWeight.SemiBold))
                    Text("소음 · 떨림 · 쏠림 · 잘 모르겠음 → 지금 기록으로 남기세요", style = ts(22.sp, C.Mut))
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GIcon(Gi.Washer, C.Amber, 52.dp); Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("차가 알려 준 점검 항목", style = ts(24.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f)); TestBadge()
                    }
                    Text("워셔액이 부족합니다", style = ts(28.sp, C.Amber, FontWeight.Bold))
                    Text("출발 전 점검에서 자세히 볼 수 있습니다", style = ts(22.sp, C.Mut))
                }
            }
        }
    }
    BottomButtons(Btn("지금 기록", Gi.Rec) { c.recordNow() }, Btn("도움이 필요해요", Gi.Lifebuoy) { c.go(Screen.SITUATION) }, RightKind.HELP_DARK)
}

// ---------------------------------------------------------------- 홈(주행 중): 큰 버튼 3개
@Composable
fun HomeDriving(c: DemoController, voiceListening: Boolean, partial: String) = ScreenFrame {
    AppHeader { c.demoPanel = true }
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        DriveChip(c); Spacer(Modifier.width(12.dp)); StatusChip(Gi.Users, "보호자 2명 준비됨", C.Purple)
    }
    Row(Modifier.fillMaxWidth().weight(1f).padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        TallBtn("지금 기록", "누른 순간의\n차 상태를 남깁니다", Gi.Rec, C.Mint, C.Surf2, Modifier.weight(1f)) { c.recordNow() }
        TallBtn("말로 하기", if (voiceListening) "듣는 중\n${partial.ifEmpty { "말씀하세요" }}" else "누른 뒤 8초 동안\n말을 듣습니다",
            Gi.Mic, if (voiceListening) C.Mint else C.Fg, if (voiceListening) C.OnMint else C.Surf2, Modifier.weight(1f)) { c.talk() }
        TallBtn("도움이\n필요해요", "급한 상황을\n고릅니다", Gi.Lifebuoy, C.Fg, C.Help, Modifier.weight(1f)) { c.go(Screen.SITUATION) }
    }
    SignalLine(c)
    Row(Modifier.fillMaxWidth().padding(top = 12.dp).clip(R16).background(C.Surf).padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.Lock, C.Mut, 30.dp); Spacer(Modifier.width(16.dp))
        Text("경고등 자세한 설명 · 점검 · 설정은 정차 후에 열립니다", style = ts(22.sp, C.Mut))
    }
}

@Composable
private fun TallBtn(label: String, sub: String, icon: Gi, tint: Color, bg: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.fillMaxHeight().clip(R24).background(bg).border(1.5.dp, C.Line, R24).clickable(role = Role.Button, onClick = onClick).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        GIcon(icon, tint, 76.dp)
        Spacer(Modifier.height(24.dp))
        Text(label, style = ts(36.sp, C.Fg, FontWeight.SemiBold, 46.sp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(sub, style = ts(22.sp, if (bg == C.Help) C.Fg else C.Mut, lineHeight = 30.sp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 3)
    }
}

/** 실제로 받은 SDK 값을 한 줄로 보여 줍니다(시연에서 실제 신호와 시험 입력을 구별하려고 둡니다). */
@Composable
fun SignalLine(c: DemoController) {
    val (v, q) = c.speedView
    val (b, bTest) = c.brakeView
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.Gauge, C.Mut, 26.dp); Spacer(Modifier.width(8.dp))
        Text(when { v == null -> "차속 확인 불가"; q == Quality.STALE -> "차속 ${kmh(v)}km/h(오래됨)"; else -> "차속 ${kmh(v)}km/h" }, style = ts(22.sp, C.Mut))
        if (q == Quality.TEST_INPUT) { Spacer(Modifier.width(8.dp)); TestBadge() }
        Spacer(Modifier.width(20.dp))
        GIcon(Gi.Pedal, C.Mut, 26.dp); Spacer(Modifier.width(8.dp))
        Text("브레이크 " + when (b) { true -> "밟음"; false -> "안 밟음"; null -> "확인 불가" } + if (!bTest) " · Vehicle SDK" else "", style = ts(22.sp, C.Mut))
        if (bTest) { Spacer(Modifier.width(8.dp)); TestBadge() }
    }
    // 화면·음성을 내지 않는 관측 기록(PRD 3.3.2). 시연에서 판정이 실제로 돌았는지 보이게 한 줄로 둡니다.
    val notes = listOfNotNull(
        c.accelCandidateWall?.let { "급가속 후보 ${DemoController.hms(it)} 기록(질문 없음)" },
        c.suppressedWall?.let { "질문 조건 맞음 ${DemoController.hms(it)} · 5분 안이라 묻지 않음" },
    )
    if (notes.isNotEmpty()) Text(notes.joinToString(" · "), style = ts(22.sp, C.Amber), modifier = Modifier.padding(top = 6.dp), maxLines = 2)
}

// ---------------------------------------------------------------- 경고등 찾기
private val COLORS = listOf("빨강" to Color(0xFFFF4D4D), "주황" to Color(0xFFFFA43A), "초록" to Color(0xFF3DDC84), "파랑" to Color(0xFF4C9BFF))
private val SHAPES = listOf("엔진 점검" to Gi.Engine, "오일 압력" to Gi.Oil, "브레이크" to Gi.Brake, "배터리" to Gi.Battery,
    "타이어 공기압" to Gi.Tire, "안전벨트" to Gi.Belt, "냉각수 온도" to Gi.Coolant, "에어백" to Gi.Airbag)

@Composable
fun WarningFind(c: DemoController) = ScreenFrame {
    ParkedHead(c, Tab.WARNING)
    TitleRow("경고등 찾기", c)
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
            listOf("계속 켜져 있어요" to Gi.Steady, "깜박여요" to Gi.Blink).forEach { (name, ic) ->
                SelChip(name, c.warnMode == name, Modifier.weight(1f), enabled = !locked, icon = ic) {
                    c.warnMode = name
                    if (c.warnColor != null && c.warnShape != null) c.go(Screen.WARN_RESULT)
                }
            }
        }
    }
    Spacer(Modifier.weight(1f))
    if (locked) Text("주행 중에는 3번 누르기 대신 \"말로 하기\"로 찾습니다", style = ts(22.sp, C.Amber))
    BottomButtons(Btn("문구로 찾기", Gi.Search) { c.showToast("문구로 찾기(키보드)는 이번 시연 범위 밖입니다") },
        Btn("말로 찾기", Gi.Mic) { c.talk() }, RightKind.AMBER)
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
    ParkedHead(c, Tab.WARNING)
    val match = c.warnColor == "주황" && c.warnShape == "타이어 공기압"
    Text(if (match) "후보 2개를 찾았습니다" else "이 시연 도감에서 찾지 못했습니다", style = ts(30.sp, weight = FontWeight.SemiBold), modifier = Modifier.padding(top = 20.dp))
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOfNotNull(c.warnColor, c.warnShape?.let { "$it 모양" }, c.warnMode?.removeSuffix(" 있어요")?.replace("계속 켜져", "계속 켜짐")).forEach {
            Box(Modifier.clip(Pill).background(C.Surf2).padding(horizontal = 16.dp, vertical = 8.dp)) { Text(it, style = ts(22.sp)) }
        }
        Spacer(Modifier.weight(1f))
        Text("다시 고르기", style = ts(24.sp, C.Mint, FontWeight.SemiBold), modifier = Modifier.clickable { c.warnColor = null; c.warnShape = null; c.warnMode = null; c.go(Screen.WARN_FIND) })
    }
    if (!match) {
        Panel(Modifier.weight(1f)) {
            Text("이 시연 도감에는 대표 사례(주황 · 타이어 공기압)만 들어 있습니다.", style = Body)
            Spacer(Modifier.height(12.dp))
            Text("실제 도감은 대상 차량 취급설명서의 기호와 대조해 확정합니다.", style = Small)
        }
    } else {
        val steady = c.warnMode == "계속 켜져 있어요"
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Candidate("타이어 공기압\n낮음", "계속 켜짐", if (steady) "고른 조건과 같습니다" else "켜짐 방식이 다릅니다", steady, Modifier.weight(1f))
            Candidate("공기압 장치\n점검", "깜박인 뒤 켜짐", if (!steady) "고른 조건과 같습니다" else "켜짐 방식이 다릅니다", !steady, Modifier.weight(1f))
        }
        Panel(Modifier.padding(top = 16.dp).weight(1f), pad = 16.dp) {
            Row(Modifier.fillMaxWidth().clip(R16).background(C.OnAmber).padding(horizontal = 20.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                GIcon(Gi.Wrench, C.Amber, 40.dp); Spacer(Modifier.width(16.dp))
                Text(if (steady) "가까운 정비소에 들르세요" else "정비소에서 점검을 받으세요", style = ts(30.sp, C.Amber, FontWeight.Bold), modifier = Modifier.weight(1f))
                Text("운행 판단", style = ts(22.sp, C.Mut))
            }
            Column(Modifier.padding(horizontal = 8.dp, vertical = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (steady) "타이어 공기압 낮음" else "공기압 장치 점검", style = ts(30.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f)); TestBadge("안내 예시 · 검토 전")
                }
                Spacer(Modifier.height(12.dp))
                Text("설명 요약(설명서 기준)", style = ts(22.sp, C.Amber, FontWeight.SemiBold))
                Text(if (steady) "타이어 공기압이 많이 낮을 때 켜집니다.\n안전한 곳에 세운 뒤 타이어 상태를 확인하세요.\n공기압을 맞춘 뒤에도 켜져 있으면 점검을 받으세요."
                    else "공기압 경고 장치에 이상이 있을 때 깜박인 뒤 켜집니다.\n가까운 정비소에서 점검을 받으세요.", style = ts(24.sp, lineHeight = 34.sp))
                Spacer(Modifier.weight(1f))
                Text("주행 중에는 위의 운행 판단 한 줄만 보입니다", style = Small)
                Spacer(Modifier.height(12.dp))
                Text("출처: 아반떼 취급설명서 해당 절 (확인 전)\n차량 계기판 안내와 다르면 차량 안내를 따르세요", style = ts(22.sp, C.Mut, lineHeight = 30.sp))
            }
        }
    }
    BottomButtons(Btn("정차 후 자세히", Gi.Park) { c.showToast("상세 설명은 정차 중에 엽니다(이번 시연 범위 밖)") },
        Btn("정비소 들르기", Gi.Wrench) { c.go(Screen.SERVICE) }, RightKind.AMBER)
}

@Composable
private fun Candidate(name: String, mode: String, note: String, on: Boolean, modifier: Modifier) {
    Column(modifier.height(190.dp).clip(R24).background(if (on) C.OnAmber else C.Surf).border(2.dp, if (on) C.Amber else C.Line, R24).padding(20.dp)) {
        Row { GIcon(Gi.Tire, C.Amber, 60.dp); Spacer(Modifier.width(16.dp)); Text(name, style = ts(24.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f)); if (on) GIcon(Gi.CircleCheck, C.Amber, 32.dp) }
        Spacer(Modifier.weight(1f))
        Text(mode, style = ts(22.sp, if (on) C.Amber else C.Fg, FontWeight.SemiBold))
        Text(note, style = ts(22.sp, if (on) C.Mint else C.Mut))
    }
}

// ---------------------------------------------------------------- 지금 기록(차량 상태 시간표)
@Composable
fun RecordScreen(c: DemoController, partial: String, listening: Boolean) = ScreenFrame {
    ParkedHead(c, Tab.RECORD)
    val r = c.lastRecord
    TitleRow(if (r == null) "증상 기록" else "증상을 기록했습니다", c)
    if (r == null) {
        Panel(Modifier.weight(1f)) {
            Text("아직 기록이 없습니다", style = Head)
            Text("홈의 \"지금 기록\"을 누르면 누른 순간 앞 20초와 뒤 10초의 차 상태를 남깁니다", style = Small)
        }
        BottomButtons(Btn("홈으로", Gi.Home) { c.go(Screen.HOME) }, Btn("지금 기록", Gi.Rec) { c.recordNow() }, RightKind.MINT)
    } else RecordBody(c, r, partial, listening)
}

@Composable
private fun ColumnScope.RecordBody(c: DemoController, r: RecordSnap, partial: String, listening: Boolean) {
    c.tick // 기록 뒤 10초 동안 시간표를 채웁니다
    val samples = c.history.filter { it.t >= r.t0 - 20_000 && it.t <= r.t0 + 10_000 }
    val at = samples.minByOrNull { abs(it.t - r.t0) }
    val speedTest = samples.any { it.speedQ == Quality.TEST_INPUT }
    Panel(Modifier.weight(1f), pad = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("차량 상태 시간표", style = ts(26.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            if (speedTest) TestBadge("차속 · 시험 입력")
        }
        Text("${DemoController.hms(r.wall)} 기록 · 앞 20초와 뒤 10초", style = Small)
        Spacer(Modifier.height(12.dp))
        val rows = listOf(
            Triple(Gi.Gauge, "차속", at?.speed?.let { "시속 ${kmh(it)}km" } ?: "확인 불가"),
            Triple(Gi.Pedal, "브레이크", when (at?.brake) { true -> "기록 순간 밟음"; false -> "기록 순간 안 밟음"; null -> "확인 불가" }),
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
            Timeline(samples, r.t0, Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth().padding(start = 220.dp)) {
            Text("-20초", style = ts(22.sp, C.Mut)); Spacer(Modifier.weight(1f)); Text("-10초", style = ts(22.sp, C.Mut)); Spacer(Modifier.weight(1f))
            Text("기록 시점", style = ts(22.sp, C.Mint, FontWeight.SemiBold)); Spacer(Modifier.weight(0.6f)); Text("+10초", style = ts(22.sp, C.Mut))
        }
    }
    Panel(Modifier.padding(top = 14.dp), pad = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("함께 남긴 상태 · 공조 · 창문 · 와이퍼", style = ts(22.sp, C.Mut), modifier = Modifier.weight(1f)); TestBadge() }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            listOf(Gi.Fan to "에어컨 켜짐", Gi.Window to "창문 닫힘", Gi.Wiper to "와이퍼 꺼짐").forEach { (ic, t) ->
                Row(verticalAlignment = Alignment.CenterVertically) { GIcon(ic, C.Mint, 30.dp); Spacer(Modifier.width(10.dp)); Text(t, style = ts(22.sp)) }
            }
        }
    }
    Panel(Modifier.padding(top = 14.dp), pad = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("어떤 증상이었나요", style = ts(24.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            val memo = if (listening) "듣는 중: ${partial.ifEmpty { "말씀하세요" }}" else r.memo?.let { "말로 메모: $it" }
            if (memo != null) { GIcon(Gi.Mic, C.Mint, 28.dp); Spacer(Modifier.width(6.dp)); Text(memo, style = ts(22.sp, C.Mint), maxLines = 1) }
            if (r.memoTest && !listening) { Spacer(Modifier.width(8.dp)); TestBadge() }
        }
        listOf(listOf("떨림" to Gi.Activity, "소음" to Gi.AudioLines, "쏠림" to Gi.Steering), listOf("냄새" to Gi.Flame, "타이어" to Gi.Wheel, "잘 모르겠음" to Gi.CircleHelp)).forEach { row ->
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (name, ic) ->
                    val on = r.symptom == name
                    Row(Modifier.weight(1f).height(68.dp).clip(R16).background(if (on) C.OnMint else C.Surf2).then(if (on) Modifier.border(2.dp, C.Mint, R16) else Modifier)
                        .clickable { r.symptom = name; c.lastRecord = r.copy() }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        GIcon(ic, if (on) C.Mint else C.Fg, 30.dp); Spacer(Modifier.width(10.dp)); Text(name, style = ts(24.sp, if (on) C.Mint else C.Fg))
                    }
                }
            }
        }
    }
    BottomButtons(Btn("기록 끝내기", Gi.CircleCheck) { c.go(Screen.HOME); c.showToast("기록을 저장했습니다") }, Btn("말로 메모", Gi.Mic) { c.memoByVoice() }, RightKind.MINT)
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

// ---------------------------------------------------------------- 보호자 알림 상태
@Composable
fun GuardianScreen(c: DemoController) = ScreenFrame {
    c.tick // 모의 상태(접수 → 전달 → 열람)가 시간에 따라 바뀝니다
    ParkedHead(c, Tab.GUARDIAN)
    TitleRow("보호자 알림 상태", c)
    val inc = c.incident
    val sent = inc?.sentWall
    Panel(pad = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("보호자별 상태", style = ts(24.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            if (sent != null) { Text("${DemoController.hms(sent)} 보냄", style = ts(22.sp, C.Mut)); Spacer(Modifier.width(10.dp)); MockBadge("모의 발송") }
        }
        val rows = if (sent != null) c.guardianRows() else listOf("보호자 1" to "준비됨 · 연습 알림 받음", "보호자 2" to "준비됨 · 연습 알림 받음")
        rows.forEachIndexed { i, (who, st) ->
            val done = sent != null && c.guardianDone(i)
            val col = if (sent == null) C.Mut else if (i == 0 && done) C.Mint else if (done) C.Amber else C.Fg
            Row(Modifier.padding(top = 12.dp).fillMaxWidth().clip(R16).background(C.Surf2).padding(horizontal = 20.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                GIcon(Gi.Users, C.Purple, 36.dp); Spacer(Modifier.width(14.dp))
                Text(who, style = ts(28.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                GIcon(if (i == 0) Gi.CheckCheck else Gi.Message, col, 30.dp); Spacer(Modifier.width(8.dp))
                Text(st, style = ts(22.sp, col, FontWeight.SemiBold))
            }
        }
        Text(if (sent != null) "접수 → 전달 → 열람 순서로 바뀝니다(서버 없이 앱 안에서 재생한 모의 상태)" else "보낸 알림이 없습니다",
            style = ts(22.sp, C.Mut), modifier = Modifier.padding(top = 10.dp))
    }
    if (inc != null && sent != null) {
        Panel(Modifier.padding(top = 14.dp), pad = 20.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GIcon(Gi.MapPin, C.Purple, 40.dp); Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("위치 측정 시각", style = ts(22.sp, C.Mut))
                    Text(DemoController.hms(inc.detectedWall - 2_000), style = ts(40.sp, weight = FontWeight.Bold))
                }
                TestBadge()
            }
            Text("주소: ○○시 ○○구 ○○대로 부근 (예시)", style = ts(24.sp), modifier = Modifier.padding(top = 10.dp))
            Text("지도는 앱에 없고 보호자 휴대폰 페이지에서 봅니다", style = Small)
            Text("보호자 2명에게 위치를 보내는 중", style = ts(22.sp, C.Purple, FontWeight.SemiBold))
        }
        Panel(Modifier.padding(top = 14.dp).weight(1f), pad = 20.dp) {
            Text("사고 순간 차 상태", style = ts(24.sp, weight = FontWeight.SemiBold))
            listOf(Gi.Seat to "탑승: 운전석 1명", Gi.Belt to "안전벨트: 운전석 착용", Gi.Airbag to "에어백: 운전석 전개", Gi.Door to "문: 모두 닫힘").forEach { (ic, t) ->
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    GIcon(ic, C.Purple, 30.dp); Spacer(Modifier.width(12.dp)); Text(t, style = ts(24.sp)); Spacer(Modifier.width(10.dp)); TestBadge()
                }
            }
        }
    } else Spacer(Modifier.weight(1f))
    BottomButtons(Btn("기록 보기", Gi.History) { c.go(Screen.RECORD) }, Btn("공유 중지", Gi.MapPinOff) { c.stopSharing() }, RightKind.PURPLE)
}

// ---------------------------------------------------------------- 정비소(이번 시연 범위 밖)
@Composable
fun ServiceScreen(c: DemoController) = ScreenFrame {
    ParkedHead(c, Tab.SERVICE)
    TitleRow("정비소 들르기", c)
    Panel(Modifier.weight(1f)) {
        Text("[설계] 이번 데모에는 없는 화면입니다", style = Head)
        Spacer(Modifier.height(12.dp))
        Text("설계: 정비소 후보 3곳을 목록으로 보여 주고, 지도는 그리지 않고 옆 내비에서 봅니다(requestNavigationScreen). \"경유지로 추가\"는 NaviHelper addWaypoint로 지금 경로에 넣습니다.", style = ts(24.sp, lineHeight = 34.sp))
    }
    BottomButtons(Btn("홈으로", Gi.Home) { c.go(Screen.HOME) }, Btn("경고등 찾기", Gi.Engine) { c.go(Screen.WARN_FIND) }, RightKind.AMBER)
}
