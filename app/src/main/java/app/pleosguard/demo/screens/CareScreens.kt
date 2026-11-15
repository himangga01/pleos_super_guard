package app.pleosguard.demo.screens

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pleosguard.demo.DemoController
import app.pleosguard.demo.RecordSnap
import app.pleosguard.demo.Screen
import app.pleosguard.demo.logic.CareData
import app.pleosguard.demo.logic.Consumable
import app.pleosguard.demo.logic.Evidence
import app.pleosguard.demo.logic.LeftItemCare
import app.pleosguard.demo.logic.Plan
import app.pleosguard.demo.logic.Recall
import app.pleosguard.demo.logic.RecordRules
import app.pleosguard.demo.logic.RecordStatus
import app.pleosguard.demo.logic.Review
import app.pleosguard.demo.logic.ShopInfo
import app.pleosguard.demo.logic.WarningCatalog
import app.pleosguard.demo.ui.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.roundToInt

/*
 * 플레오스 슈퍼 케어 화면(2026-11-14 컨셉 전환): 케어 홈 카드 · 차량 관리 · 리콜 · 구독 · 폰 연결 · 후기 쓰기 · 짐 알림 · 폰 증거.
 * 칸 안 아이콘 · 글자는 칸 크기에 비례합니다(ui/Tiles.kt). 넓은 창은 목록-상세 또는 2열입니다.
 * 예시 · 모의 자료에는 정직성 꼬리표를 붙입니다(HonestyBadge).
 */

// ---------------------------------------------------------------- 공통
/** 머리줄 + 탭 + 제목 줄. [trailing]은 제목 오른쪽(주행 상태 칩 앞)에 둡니다. */
@Composable
internal fun ColumnScope.ScreenHead(c: DemoController, tab: Tab?, title: String, trailing: (@Composable RowScope.() -> Unit)? = null) {
    AppHeader { c.demoPanel = true }
    TabBar(tab) { t -> goTab(c, t) }
    Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = ts(30.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1)
        trailing?.invoke(this)
        DriveChip(c)
    }
}

fun goTab(c: DemoController, t: Tab) = when (t) {
    Tab.MANAGE -> c.go(Screen.MANAGE)
    Tab.RECORD -> c.go(Screen.RECORD_LIST)
    Tab.SERVICE -> c.openService()
    Tab.SUBSCRIBE -> c.go(Screen.SUBSCRIBE)
}

/** 아이콘 이름(CareData의 문자열) → 아이콘. */
fun giOf(name: String, fallback: Gi = Gi.Wrench): Gi = runCatching { Gi.valueOf(name) }.getOrDefault(fallback)

/** 증거 종류 → 아이콘. */
fun evidenceIcon(kind: String): Gi = when (kind) { "noise" -> Gi.AudioLines; "vibration" -> Gi.Vibrate; "warning_photo" -> Gi.Camera; "receipt" -> Gi.Receipt; else -> Gi.Image }

/** 남은 거리 → 색. 1,500km 아래 주황, 지나면 빨강. */
private fun leftColor(left: Int) = when { left < 0 -> C.Red; left < 1_500 -> C.Amber; else -> C.Mint }

private fun km(v: Int) = "%,dkm".format(v)

@Composable
private fun ClickTile(modifier: Modifier, bg: Color = C.Surf, border: Color? = null, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.clip(R16).background(bg).then(if (border != null) Modifier.border(2.dp, border, R16) else Modifier).clickable(role = Role.Button, onClick = onClick), content = content)
}

/** 켜기 · 끄기 단추(알약 모양). */
@Composable
fun Toggle(on: Boolean, label: String, onClick: () -> Unit) {
    Box(Modifier.size(92.dp, 52.dp).clip(Pill).background(if (on) C.Mint else C.Surf2).border(2.dp, if (on) C.Mint else C.Line, Pill)
        .clickable(role = Role.Switch, onClick = onClick).semantics { contentDescription = "$label " + if (on) "켜짐" else "꺼짐" },
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.padding(5.dp).size(42.dp).clip(Pill).background(if (on) C.Dark else C.Mut))
    }
}

@Composable
private fun SelectChip(text: String, on: Boolean, modifier: Modifier = Modifier, icon: Gi? = null, color: Color = C.Mint, height: Dp = 60.dp, onClick: () -> Unit) {
    Row(modifier.height(height).clip(R16).background(if (on) color.copy(alpha = 0.18f) else C.Surf2).border(if (on) 2.dp else 1.5.dp, if (on) color else C.Line, R16)
        .clickable(role = Role.Checkbox, onClick = onClick).padding(horizontal = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { GIcon(icon, if (on) color else C.Mut, 26.dp); Spacer(Modifier.width(8.dp)) }
        Text(text, style = ts(22.sp, if (on) color else C.Fg, if (on) FontWeight.SemiBold else FontWeight.Normal), maxLines = 1)
    }
}

@Composable
private fun Progress(frac: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(10.dp).clip(Pill).background(C.Line)) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).fillMaxHeight().clip(Pill).background(color))
    }
}

/** 별 다섯 개(점수만큼 채움). */
@Composable
fun Stars(score: Float, size: Dp, color: Color = C.Amber) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) GIcon(if (score >= i - 0.25f) Gi.StarFill else Gi.StarLine, if (score >= i - 0.25f) color else C.Dim, size)
    }
}

@Composable
private fun LockedNote(text: String) = Panel(Modifier.padding(top = 8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) { GIcon(Gi.Lock, C.Mut, 30.dp); Spacer(Modifier.width(14.dp)); Text(text, style = ts(24.sp, C.Mut)) }
}

// ---------------------------------------------------------------- 케어 홈 카드
private data class CareCard(val icon: Gi, val tint: Color, val title: String, val value: String, val valueColor: Color, val onClick: () -> Unit, val border: Color? = null)

private fun careCards(c: DemoController): List<CareCard> {
    val (cons, left) = c.nextMaintenance()
    val recalls = c.openRecalls()
    val trunk = c.trunkNow().first
    val care = c.leftCare
    val plan = CareData.PLANS.firstOrNull { it.id in c.subscribed }
    val open = c.book.records.count { it.symptom != null && it.status != RecordStatus.DONE.name }
    val lc = leftColor(left)
    val confirmed = recalls.count { c.recallState(it) == "대상 맞음" }
    return listOf(
        CareCard(giOf(cons.icon), lc, cons.name, if (left < 0) "${km(-left)} 지남" else "${km(left)} 남음", if (lc == C.Mint) C.Fg else lc, { c.go(Screen.MANAGE) }),
        CareCard(Gi.Megaphone, if (recalls.isEmpty()) C.Mint else C.Amber, "리콜", when { recalls.isEmpty() -> "조치할 것 없음"; confirmed > 0 -> "조치 필요 ${confirmed}건"; else -> "확인 필요 ${recalls.size}건" },
            if (recalls.isEmpty()) C.Fg else C.Amber, { c.openRecall() }, if (recalls.isEmpty()) null else C.Amber),
        CareCard(Gi.Trunk, if (!care.enabled) C.Dim else C.Mint, "짐 알림", when {
            !care.enabled -> "꺼짐"; trunk != null && trunk > 0 -> "트렁크 열림"; care.trunkCarried -> "트렁크 짐 있음"; else -> "켜짐"
        }, C.Fg, { c.go(Screen.MANAGE) }),
        CareCard(Gi.Repeat, C.Purple, "구독", plan?.name ?: "둘러보기", C.Fg, { c.go(Screen.SUBSCRIBE) }),
        CareCard(Gi.Smartphone, if (c.pairedPhone != null) C.Mint else C.Mut, "폰 연결", if (c.pairedPhone != null) "연결됨" else "연결하기", C.Fg, { c.openPhone() }),
        CareCard(Gi.History, C.Mint, "증상 기록", if (open == 0) "열린 기록 없음" else "열린 기록 ${open}건", C.Fg, { c.go(Screen.RECORD_LIST) }),
    )
}

/** 케어 상태 카드 6개. [cols]열로 나누고, 칸 크기에 맞춰 아이콘 · 글자를 키웁니다. */
@Composable
fun CareGrid(c: DemoController, modifier: Modifier, cols: Int) {
    c.careVersion; c.leftVersion; c.bookVersion; c.tick
    val cards = careCards(c)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        cards.chunked(cols).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { k ->
                    ClickTile(Modifier.weight(1f).fillMaxHeight(), border = k.border, onClick = k.onClick) {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                            TileContent(k.icon, k.tint, k.title, k.value, k.valueColor, row = maxWidth > maxHeight * 1.7f, titleColor = C.Mut)
                        }
                    }
                }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// ---------------------------------------------------------------- 차량 관리
@Composable
fun ManageScreen(c: DemoController) = ScreenFrame {
    c.tick; c.careVersion; c.leftVersion
    ScreenHead(c, Tab.MANAGE, "차량 관리")
    if (c.driving) {
        LockedNote("차량 관리는 정차 중에 봅니다"); Spacer(Modifier.weight(1f))
        BottomButtons(Btn("홈으로", Gi.Home) { c.go(Screen.HOME) }, Btn("정비소 들르기", Gi.Wrench) { c.openService() }, RightKind.AMBER)
        return@ScreenFrame
    }
    if (LocalWide.current) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        // 넓은 창: 왼쪽 = 차가 보낸 점검 신호 · 소모품, 오른쪽 = 리콜 · 경고등 · 짐 알림 · 안심 케어
        Column(Modifier.weight(1.1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SignalsPanel(c, Modifier.weight(0.85f), tall = true)
            ConsumablesPanel(c, Modifier.weight(1.15f), tall = true)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            RecallCard(c, Modifier.weight(0.8f).fillMaxWidth())
            WarnTiles(c, Modifier.weight(0.8f).fillMaxWidth())
            LeftItemsCard(c, Modifier.weight(1.5f), tall = true)
            SafeCareCard(c, Modifier.weight(0.7f).fillMaxWidth())
        }
    } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SignalsPanel(c, Modifier, tall = false)
        RecallCard(c, Modifier.fillMaxWidth().height(108.dp))
        ConsumablesPanel(c, Modifier, tall = false)
        WarnTiles(c, Modifier.fillMaxWidth().height(116.dp))
        LeftItemsCard(c, Modifier, tall = false)
        SafeCareCard(c, Modifier.fillMaxWidth().height(100.dp))
    }
    BottomButtons(Btn("홈으로", Gi.Home) { c.go(Screen.HOME) }, Btn("정비소 들르기", Gi.Wrench) { c.openService() }, RightKind.AMBER)
}

@Composable
private fun SignalsPanel(c: DemoController, modifier: Modifier, tall: Boolean) = Panel(modifier, pad = 20.dp) {
    val items = c.checkItems()
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.Gauge, C.Mint, 32.dp); Spacer(Modifier.width(10.dp))
        Text("차 점검 신호", style = ts(24.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
        if (items.any { it.test }) { TestBadge(); Spacer(Modifier.width(10.dp)) }
        InfoButton("차 점검 신호", listOf(
            "경고등이 아니라 차가 보내는 점검 신호입니다(Vehicle SDK Safety)",
            "에뮬레이터는 엔진오일 · 브레이크액 · 워셔액 값을 주지 않아 시연에서는 시험 입력을 씁니다",
        ), enabled = !c.driving)
    }
    items.chunked(2).forEach { row ->
        Row(Modifier.padding(top = 12.dp).then(if (tall) Modifier.weight(1f) else Modifier), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            row.forEach { it2 ->
                val box = if (tall) Modifier.weight(1f).fillMaxHeight() else Modifier.weight(1f).height(96.dp)
                Box(box.clip(R16).background(C.Surf2)) {
                    TileContent(checkIcon(it2.name), if (it2.ok) C.Mint else C.Amber, it2.name, it2.value.substringBefore(" · "), if (it2.ok) C.Mint else C.Amber)
                }
            }
        }
    }
}

@Composable
private fun ConsumablesPanel(c: DemoController, modifier: Modifier, tall: Boolean) = Panel(modifier, pad = 20.dp) {
    val (odo, odoTest) = c.odometer()
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.Wrench, C.Mint, 32.dp); Spacer(Modifier.width(10.dp))
        Text("소모품 · 정기 점검", style = ts(24.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1)
        if (odoTest) { TestBadge(); Spacer(Modifier.width(8.dp)) }
        GIcon(Gi.Gauge, C.Mut, 26.dp); Spacer(Modifier.width(6.dp))
        Text(km(odo.roundToInt()), style = ts(24.sp, weight = FontWeight.SemiBold))
        Spacer(Modifier.width(10.dp))
        InfoButton("소모품 주기", listOf(
            "교체 주기와 마지막 교체 거리는 예시 값입니다(아반떼 정비 주기 확인 전)",
            "주행거리: Vehicle SDK 누적 주행거리(없으면 시험 입력 32,140km)",
            "구독 · 정비소 방문 기록과 이어 다음 정비 시기를 알려 줍니다",
        ), enabled = !c.driving)
    }
    val items = c.consumables().sortedBy { it.second }
    Column(Modifier.padding(top = 12.dp).then(if (tall) Modifier.weight(1f) else Modifier), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { (k, left) -> ConsumableRow(k, left, odo, if (tall) Modifier.weight(1f) else Modifier.height(72.dp)) }
    }
}

@Composable
private fun ConsumableRow(k: Consumable, left: Int, odo: Float, modifier: Modifier) {
    val col = leftColor(left)
    BoxWithConstraints(modifier.fillMaxWidth().clip(R16).background(C.Surf2)) {
        val h = maxHeight.value
        val ic = (h * 0.56f).coerceIn(26f, 60f)
        val t1 = (h * 0.30f).coerceIn(20f, 32f)
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            GIcon(giOf(k.icon), col, ic.dp); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(k.name, style = ts(t1.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1)
                    Text(if (left < 0) "${km(-left)} 지남" else "${km(left)} 남음", style = ts(t1.sp, if (col == C.Mint) C.Fg else col, FontWeight.Bold), maxLines = 1)
                }
                Spacer(Modifier.height((h * 0.08f).coerceIn(4f, 10f).dp))
                Progress(k.usedRatio(odo), col)
            }
        }
    }
}

@Composable
private fun RecallCard(c: DemoController, modifier: Modifier) {
    val open = c.openRecalls()
    ClickTile(modifier, bg = if (open.isEmpty()) C.Surf else C.OnAmber, border = if (open.isEmpty()) null else C.Amber, onClick = { c.openRecall() }) {
        TileContent(Gi.Megaphone, if (open.isEmpty()) C.Mint else C.Amber, "리콜", if (open.isEmpty()) "조치할 리콜 없음" else "확인 필요 ${open.size}건 · ${open.first().part}",
            if (open.isEmpty()) C.Mint else C.Amber, titleColor = C.Mut, trailing = Gi.ChevronRight)
    }
}

@Composable
private fun WarnTiles(c: DemoController, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ClickTile(Modifier.weight(1f).fillMaxHeight(), onClick = { c.go(Screen.WARN_FIND) }) {
            TileContent(Gi.Engine, C.Amber, "경고등 찾기", "색 · 모양 · 켜짐", titleColor = C.Fg, valueColor = C.Mut, hint = true)
        }
        ClickTile(Modifier.weight(1f).fillMaxHeight(), onClick = { c.requestPhone("warning_photo") }) {
            TileContent(Gi.Camera, C.Amber, "폰으로 찍기", if (c.phoneWait?.kind == "warning_photo") "폰에서 찍는 중" else "찍으면 바로 찾기",
                titleColor = C.Fg, valueColor = if (c.phoneWait?.kind == "warning_photo") C.Mint else C.Mut, hint = true)
        }
    }
}

/** 짐 알림 카드: 켜기 · 끄기, 트렁크 · 짐 상태, 최근 기록. */
@Composable
private fun LeftItemsCard(c: DemoController, modifier: Modifier, tall: Boolean) = Panel(modifier, pad = 20.dp) {
    c.leftVersion; c.tick
    val care = c.leftCare
    val (trunk, test) = c.trunkNow()
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.Luggage, C.Mint, 34.dp); Spacer(Modifier.width(12.dp))
        Text("짐 알림", style = ts(26.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
        if (test) { TestBadge(); Spacer(Modifier.width(10.dp)) }
        InfoButton("짐 알림", listOf(
            "정차 중 트렁크를 열면 짐을 실은 것으로 봅니다",
            "짐을 싣고 10분 안에 출발하면 \"짐 실은 주행\"으로 봅니다",
            "도착(정차 5초) 뒤 3분 넘게 달렸으면 바로, 짧게 달렸으면 운전석 문이 열릴 때 \"트렁크에 짐이 남아 있지 않나요?\"라고 묻습니다",
            "트렁크를 열면 카드는 조용히 닫힙니다 · 한 주행에 한 번만 묻습니다",
            "뒷문을 열고 출발했으면 뒷좌석도 묻습니다",
            "트렁크 위치: Vehicle SDK 문 위치(DoorValue.REAR) · 주행: 차속 · 주행 제한",
            "짐이 실제로 있는지는 알 수 없어 묻기만 합니다. 폰이 연결돼 있으면 폰에도 알립니다",
        ), enabled = !c.driving)
        Spacer(Modifier.width(12.dp))
        Toggle(care.enabled, "짐 알림") { c.toggleLeftCare() }
    }
    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusChip(Gi.Trunk, when { trunk == null -> "트렁크 확인 불가"; trunk > 0 -> "트렁크 열림"; else -> "트렁크 닫힘" }, if (trunk == null) C.Amber else C.Mint)
        val loaded = care.trunkLoadedWall
        StatusChip(Gi.Luggage, when {
            care.trunkCarried -> "짐 싣고 주행"
            loaded != null -> "짐 실음 ${DemoController.hm(loaded)}"
            else -> "실은 짐 없음"
        }, C.Mint)
    }
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.Seat, C.Mut, 28.dp); Spacer(Modifier.width(10.dp))
        Text("뒷좌석도 묻기", style = ts(22.sp, C.Fg), modifier = Modifier.weight(1f))
        Toggle(care.rearEnabled, "뒷좌석 알림") { c.toggleRearCare() }
    }
    if (care.askReduce) {
        Row(Modifier.padding(top = 12.dp).fillMaxWidth().clip(R16).background(C.OnAmber).border(2.dp, C.Amber, R16).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            GIcon(Gi.Bell, C.Amber, 30.dp); Spacer(Modifier.width(10.dp))
            Text("세 번 연속 짐이 없었어요 · 알림을 줄일까요?", style = ts(22.sp, C.Amber, FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 2)
            SelectChip("줄이기", false, color = C.Amber, height = 56.dp) { c.answerReduce(true) }
            Spacer(Modifier.width(8.dp))
            SelectChip("그대로", false, height = 56.dp) { c.answerReduce(false) }
        }
    }
    if (care.reduced) Text("줄임: 20초 넘게 연 트렁크만 셉니다", style = ts(22.sp, C.Mut), modifier = Modifier.padding(top = 8.dp))
    val events = care.events.toList()
    if (events.isEmpty() && tall) {
        Row(Modifier.padding(top = 16.dp).fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf(Gi.Trunk to "트렁크 열기", Gi.CarFront to "10분 안 출발", Gi.Bell to "도착 때 묻기").forEachIndexed { i, (ic, t) ->
                if (i > 0) GIcon(Gi.ChevronRight, C.Dim, 28.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) { GIcon(ic, C.Mint, 56.dp); Spacer(Modifier.height(8.dp)); Text(t, style = ts(22.sp, C.Mut)) }
            }
        }
    }
    if (events.isNotEmpty()) {
        Text("최근 기록", style = ts(22.sp, C.Mut), modifier = Modifier.padding(top = 12.dp))
        Column(Modifier.padding(top = 6.dp).then(if (tall) Modifier.weight(1f) else Modifier), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            events.take(if (tall) 6 else 3).forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GIcon(giOf(e.icon, Gi.Clock), C.Mut, 24.dp); Spacer(Modifier.width(10.dp))
                    Text(DemoController.hm(e.wall), style = ts(22.sp, C.Mut)); Spacer(Modifier.width(12.dp))
                    Text(e.text, style = ts(22.sp), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SafeCareCard(c: DemoController, modifier: Modifier) {
    ClickTile(modifier, onClick = { c.go(Screen.GUARDIAN) }) {
        TileContent(Gi.ShieldCheck, C.Purple, "안심 케어", "사고 때 보호자 2명에게", titleColor = C.Fg, valueColor = C.Mut, trailing = Gi.ChevronRight, hint = true)
    }
}

// ---------------------------------------------------------------- 리콜
@Composable
fun RecallScreen(c: DemoController) = ScreenFrame {
    c.careVersion
    ScreenHead(c, Tab.MANAGE, "리콜 안내") {
        HonestyBadge(if (c.recallsFromServer) "모의 서버 · 가상 예시" else "가상 예시 · 실제 리콜 아님"); Spacer(Modifier.width(12.dp))
        InfoButton("리콜 정보", listOf(
            "국내에는 차대번호로 리콜을 묻는 공개 API가 없습니다. 그래서 차명 · 세대(${CareData.GEN}) · 제작연월로 \"대상일 수 있음\"까지만 보여 줍니다",
            "확인은 리콜센터(car.go.kr)에서 직접 합니다(아래 단추 QR). 확인한 결과를 \"내 차가 대상인가요\"에서 고릅니다",
            "제조사가 차 화면에서 이미 안내하는 리콜(무선 업데이트 통지)은 앱이 다시 알리지 않습니다",
            "리콜 안내는 늘 무료입니다. 주행 중에는 이 화면을 열지 않습니다",
            "데모의 리콜 2건은 형식을 보이려고 지어낸 가상 예시입니다(실제 리콜 아님)",
        ), enabled = !c.driving)
        Spacer(Modifier.width(12.dp))
    }
    val list = c.recalls
    val sel = list.firstOrNull { it.id == c.selectedRecall } ?: list.firstOrNull()
    if (LocalWide.current) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            CarRecallLine(c)
            RecallRows(c, list, sel?.id, Modifier.padding(top = 14.dp).weight(1f))
        }
        RecallDetail(c, sel, Modifier.weight(1.25f).fillMaxHeight())
    } else {
        CarRecallLine(c)
        RecallRows(c, list, sel?.id, Modifier.padding(top = 14.dp).heightIn(max = 260.dp))
        RecallDetail(c, sel, Modifier.padding(top = 14.dp).weight(1f))
    }
    BottomButtons(Btn("리콜센터 확인", Gi.QrCode) { c.openRecallCenter() },
        Btn("정비 예약", Gi.Calendar) { c.openService(); c.showToast("리콜 조치는 정비소에 예약합니다(모의)") }, RightKind.AMBER)
}

@Composable
private fun CarRecallLine(c: DemoController) {
    val open = c.openRecalls().size
    Row(Modifier.fillMaxWidth().clip(R16).background(C.Surf).padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.CarFront, C.Mut, 36.dp); Spacer(Modifier.width(14.dp))
        Text("아반떼 ${CareData.GEN} · ${CareData.PRODUCED} 제작", style = ts(24.sp, weight = FontWeight.SemiBold), maxLines = 1)
        Spacer(Modifier.width(10.dp)); HonestyBadge("예시"); Spacer(Modifier.weight(1f))
        Text(if (open == 0) "확인할 리콜 없음" else "확인 필요 ${open}건", style = ts(24.sp, if (open == 0) C.Mint else C.Amber, FontWeight.Bold), maxLines = 1)
    }
}

@Composable
private fun RecallRows(c: DemoController, list: List<Recall>, selId: String?, modifier: Modifier) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(list, key = { it.id }) { r ->
            val on = r.id == selId
            val done = c.recallIsDone(r)
            val state = c.recallState(r)
            Row(Modifier.fillMaxWidth().height(110.dp).clip(R16).background(if (on) C.OnAmber else C.Surf).then(if (on) Modifier.border(2.dp, C.Amber, R16) else Modifier)
                .clickable { c.selectedRecall = r.id }.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                GIcon(Gi.Megaphone, if (done) C.Mint else C.Amber, 40.dp); Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(r.title, style = ts(24.sp, weight = FontWeight.SemiBold), maxLines = 1)
                    Text(r.part + " · " + r.level, style = ts(22.sp, C.Mut), maxLines = 1)
                }
                StatusChip(if (done) Gi.CircleCheck else if (state == "대상 아님") Gi.Close else Gi.TriangleAlert, state, if (done || state == "대상 아님") C.Mint else C.Amber)
            }
        }
    }
}

/** 위험 표시 → 색 · 아이콘. "일반"은 주황 대신 정보색(민트)입니다(조사 5.5절). */
private fun levelLook(level: String): Pair<Color, Gi> = when (level) {
    "운행 자제" -> C.Red to Gi.OctagonAlert
    "실외 주차" -> C.Amber to Gi.Flame
    else -> C.Mint to Gi.Info
}

@Composable
private fun RecallDetail(c: DemoController, r: Recall?, modifier: Modifier) = Panel(modifier, pad = 24.dp) {
    if (r == null) { Text("리콜 정보가 없습니다", style = ts(26.sp, C.Mut)); return@Panel }
    val state = c.recallState(r)
    val (lc, li) = levelLook(r.level)
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.Megaphone, lc, 56.dp); Spacer(Modifier.width(18.dp))
        Text(r.title, style = ts(30.sp, weight = FontWeight.Bold, lineHeight = 38.sp), modifier = Modifier.weight(1f), maxLines = 2)
        InfoButton("출처 · 확인일", listOf("출처: ${r.source}", "확인일: ${r.checked.ifBlank { "-" }}", "대상: ${r.gen} · ${r.produced.ifBlank { "생산 기간 확인 전" }}", "시정: ${r.period}"), enabled = !c.driving)
    }
    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusChip(li, r.level, lc)
        StatusChip(if (r.fix == "무선 업데이트") Gi.Radio else Gi.Wrench, r.fix + " · 무상", C.Mint)
        if (r.ota) StatusChip(Gi.CarFront, "차에서 안내 중", C.Mut)
        if (r.example) HonestyBadge(r.source)
    }
    Column(Modifier.padding(top = 16.dp).weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(Triple(Gi.CircleHelp, "무엇이 문제인가요", r.risk), Triple(Gi.Wrench, "어떻게 고치나요", r.action)).forEach { (ic, k, v) ->
            Row(Modifier.fillMaxWidth().clip(R16).background(C.Surf2).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                GIcon(ic, C.Mint, 36.dp); Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(k, style = ts(22.sp, C.Mut))
                    Text(v, style = ts(26.sp, weight = FontWeight.SemiBold, lineHeight = 34.sp))
                }
            }
        }
        // 내 차가 대상인가요: 리콜센터에서 확인한 뒤 고릅니다.
        Column(Modifier.fillMaxWidth().clip(R16).background(C.Surf2).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GIcon(Gi.CarFront, C.Mint, 36.dp); Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("내 차가 대상인가요", style = ts(22.sp, C.Mut))
                    Text(state, style = ts(26.sp, if (state == "대상일 수 있음" || state == "대상 맞음") C.Amber else C.Mint, FontWeight.SemiBold))
                }
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("대상 맞음", "대상 아님", "조치 완료").forEach { st -> SelectChip(st, state == st, Modifier.weight(1f)) { c.setRecallState(r.id, st) } }
            }
            Text("제작 기간이 겹쳐 \"대상일 수 있음\"으로 보였습니다 · 확인은 리콜센터에서", style = ts(20.sp, C.Mut), modifier = Modifier.padding(top = 8.dp))
        }
        Row(Modifier.fillMaxWidth().clip(R16).background(C.Surf2).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            QrImage(CareData.RECALL_CENTER, 132.dp); Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { GIcon(Gi.Smartphone, C.Mint, 30.dp); Spacer(Modifier.width(8.dp)); Text("폰으로 찍어 리콜센터 열기", style = ts(24.sp, weight = FontWeight.SemiBold)) }
                Text("등록번호나 차대번호로 직접 조회합니다 · 주소에 개인정보를 넣지 않습니다", style = ts(22.sp, C.Mut, lineHeight = 30.sp), modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- 구독 연계
/** 연결되는 내 자료 이름 → 아이콘. */
private fun useIcon(u: String): Gi = when {
    "타이어" in u -> Gi.Wheel; "증상" in u -> Gi.History; "주행거리" in u -> Gi.Gauge; "리콜" in u -> Gi.Megaphone
    "위치" in u -> Gi.MapPin; "안심" in u -> Gi.ShieldCheck; "정비" in u -> Gi.Receipt; else -> Gi.Link
}

@Composable
fun SubscribeScreen(c: DemoController) = ScreenFrame {
    c.careVersion
    ScreenHead(c, Tab.SUBSCRIBE, "구독 연계") {
        HonestyBadge("모의 · 결제 없음"); Spacer(Modifier.width(12.dp))
        InfoButton("구독 연계 원칙", listOf(
            "차가 재는 주행거리 · 증상 기록 · 리콜과 이어지는 구독만 둡니다(위 3개가 조사의 추천 상위 3개)",
            "안전 정보(리콜 · 짐 알림 · 안심 케어)는 무료입니다",
            "결제는 폰에서 합니다. 차 화면은 QR만 보입니다(Pleos 결제 기능은 확인 전, 앱 안 결제는 향후)",
            "주행 중에는 제안하지 않습니다 · 제휴 여부를 숨기지 않습니다 · 보낼 자료는 건별 동의",
            "해지는 가입만큼 쉽게 합니다 · 가격 · 제휴사는 예시입니다",
        ), enabled = !c.driving)
        Spacer(Modifier.width(12.dp))
    }
    val plans = CareData.PLANS
    val sel = plans.firstOrNull { it.id == c.selectedPlan } ?: plans.first()
    if (LocalWide.current) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        PlanList(c, plans, sel.id, Modifier.weight(1f).fillMaxHeight())
        PlanDetail(c, sel, Modifier.weight(1.25f).fillMaxHeight())
    } else {
        PlanList(c, plans, sel.id, Modifier.heightIn(max = 400.dp))
        PlanDetail(c, sel, Modifier.padding(top = 14.dp).weight(1f))
    }
    val on = sel.id in c.subscribed
    BottomButtons(Btn("홈으로", Gi.Home) { c.go(Screen.HOME) },
        Btn(if (on) "해지(모의)" else "폰에서 가입", if (on) Gi.Close else Gi.QrCode) { c.toggleSubscribe(sel.id) }, RightKind.PURPLE)
}

@Composable
private fun PlanList(c: DemoController, plans: List<Plan>, selId: String, modifier: Modifier) {
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(plans, key = { it.id }) { p ->
            val on = p.id == selId
            val joined = p.id in c.subscribed
            Row(Modifier.fillMaxWidth().height(104.dp).clip(R16).background(if (on) C.OnPurple else C.Surf).then(if (on) Modifier.border(2.dp, C.Purple, R16) else Modifier)
                .clickable { c.selectedPlan = p.id }.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                GIcon(giOf(p.icon), C.Purple, 44.dp); Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = ts(26.sp, weight = FontWeight.SemiBold), maxLines = 1)
                    Text(p.price, style = ts(22.sp, C.Mut), maxLines = 1)
                }
                when {
                    joined -> StatusChip(Gi.CircleCheck, "이용 중", C.Mint)
                    !p.demo -> HonestyBadge("설계")
                }
            }
        }
    }
}

@Composable
private fun PlanDetail(c: DemoController, p: Plan, modifier: Modifier) = Panel(modifier, pad = 24.dp) {
    val joined = p.id in c.subscribed
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(giOf(p.icon), C.Purple, 64.dp); Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = ts(32.sp, weight = FontWeight.Bold), maxLines = 1)
            Text(p.price, style = ts(24.sp, C.Mut), maxLines = 1)
        }
        if (joined) StatusChip(Gi.CircleCheck, "이용 중", C.Mint) else if (!p.demo) HonestyBadge("설계 · 가입 시연 없음")
    }
    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.Users, C.Mut, 26.dp); Spacer(Modifier.width(8.dp)); Text(p.partner, style = ts(22.sp, C.Mut)); Spacer(Modifier.width(10.dp)); HonestyBadge("예시")
    }
    Column(Modifier.padding(top = 18.dp).weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("혜택", style = ts(22.sp, C.Mut))
        p.benefits.forEach { b ->
            Row(Modifier.fillMaxWidth().clip(R16).background(C.Surf2).padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                GIcon(Gi.CircleCheck, C.Mint, 32.dp); Spacer(Modifier.width(14.dp)); Text(b, style = ts(24.sp, weight = FontWeight.SemiBold), maxLines = 2)
            }
        }
        Text("내 차 기록과 연결", style = ts(22.sp, C.Mut), modifier = Modifier.padding(top = 6.dp))
        p.uses.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { u -> StatusChip(useIcon(u), u, C.Purple) } }
        }
        planNow(c, p)?.let { line ->
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(R16).background(C.OnMint).border(2.dp, C.Mint, R16).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                GIcon(Gi.Bell, C.Mint, 32.dp); Spacer(Modifier.width(14.dp)); Text(line, style = ts(24.sp, C.Mint, FontWeight.SemiBold, 32.sp), modifier = Modifier.weight(1f), maxLines = 2)
            }
        }
        if (p.demo) Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(R16).background(C.Surf2).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            QrImage("https://pleoscare.example/plan/" + p.id, 120.dp); Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) { GIcon(Gi.Smartphone, C.Purple, 30.dp); Spacer(Modifier.width(8.dp)); Text(if (joined) "폰에서 관리" else "폰에서 가입 · 결제", style = ts(24.sp, weight = FontWeight.SemiBold)) }
                Text("차 화면은 QR만 보입니다 · 모의 링크", style = ts(22.sp, C.Mut), modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** 가입한 구독이 지금 내 차에 해 줄 일(예시). 가입 전이면 "가입하면" 문장. */
private fun planNow(c: DemoController, p: Plan): String? {
    val joined = p.id in c.subscribed
    val (cons, left) = c.nextMaintenance()
    val open = c.book.records.count { it.symptom != null && it.status == RecordStatus.OPEN.name }
    val odo = c.odometer().first.roundToInt()
    val line = when (p.id) {
        "parts" -> "${cons.name} ${if (left < 0) "교체 시기 지남" else "${km(left)} 뒤"} · 패키지 3개 비교"
        "quote" -> if (open > 0) "열린 기록 ${open}건으로 견적 받기(모의 견적)" else "증상을 기록하면 견적을 받을 수 있어요"
        "warranty" -> "출고 ${CareData.DELIVERED} · 할인 기한 2026-12-19 · 가입 기한 2028-09-20 또는 40,000km(남은 ${km((40_000 - odo).coerceAtLeast(0))})"
        "wash" -> "마지막 세차 9일 전(예시) · 가까운 지점 경유"
        "tire" -> "타이어 위치 교환 ${km(c.consumables().firstOrNull { it.first.icon == "Wheel" }?.second ?: 0)} 뒤 · 마모는 차가 재지 않음"
        "roadside" -> "현대 긴급 출동 무상 기간: 출고 뒤 6년(예시)"
        else -> null
    } ?: return null
    return if (joined) line else "가입하면: $line"
}

// ---------------------------------------------------------------- 폰 연결
@Composable
fun PhoneScreen(c: DemoController) = ScreenFrame {
    c.careVersion; c.tick
    ScreenHead(c, null, "폰 연결") {
        val ok = if (c.link == null) false else c.serverOk
        StatusChip(if (ok == false) Gi.MapPinOff else Gi.Radio, when (ok) { true -> "모의 서버 연결"; false -> "서버 없음"; null -> "서버 확인 중" }, when (ok) { true -> C.Mint; false -> C.Amber; null -> C.Mut })
        Spacer(Modifier.width(12.dp))
    }
    if (LocalWide.current) Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        PairPanel(c, Modifier.weight(0.9f).fillMaxHeight())
        Column(Modifier.weight(1.1f).fillMaxHeight()) {
            CaptureTiles(c, Modifier.weight(1f))
            InboxPanel(c, Modifier.padding(top = 14.dp).weight(1.1f))
        }
    } else {
        PairPanel(c, Modifier.height(if (c.pairedPhone != null) 170.dp else 430.dp))
        CaptureTiles(c, Modifier.padding(top = 14.dp).height(250.dp))
        InboxPanel(c, Modifier.padding(top = 14.dp).weight(1f))
    }
    BottomButtons(Btn("홈으로", Gi.Home) { c.go(Screen.HOME) }, Btn("증상 기록", Gi.History) { c.go(Screen.RECORD_LIST) }, RightKind.MINT)
}

@Composable
private fun PairPanel(c: DemoController, modifier: Modifier) = Panel(modifier, pad = 24.dp) {
    val phone = c.pairedPhone
    val code = c.pairCode
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
        val small = maxHeight < 260.dp
        val pending = c.pairPending
        when {
            pending != null && phone == null -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                GIcon(Gi.Smartphone, C.Mint, if (small) 64.dp else 110.dp)
                Text("$pending 연결할까요?", style = ts(32.sp, weight = FontWeight.Bold), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
                Text("이 차의 증상 기록을 폰과 함께 씁니다", style = ts(22.sp, C.Mut), textAlign = TextAlign.Center)
                Row(Modifier.padding(top = 18.dp).fillMaxWidth().height(84.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    BigBtn(Btn("거절", Gi.Close) { c.confirmPair(false) }, Modifier.weight(1f).fillMaxHeight(), C.Surf2, C.Fg, iconTint = C.Fg)
                    BigBtn(Btn("연결", Gi.Link) { c.confirmPair(true) }, Modifier.weight(1f).fillMaxHeight(), C.Mint, C.Dark)
                }
            }
            phone != null -> Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GIcon(Gi.Smartphone, C.Mint, if (small) 56.dp else 72.dp); Spacer(Modifier.width(18.dp))
                    Column(Modifier.weight(1f)) {
                        Text("연결됨", style = ts(if (small) 28.sp else 34.sp, C.Mint, FontWeight.Bold))
                        Text(phone, style = ts(24.sp, weight = FontWeight.SemiBold), maxLines = 1)
                    }
                    Text("연결 끊기", style = ts(22.sp, C.Mut, FontWeight.SemiBold), modifier = Modifier.clip(Pill).clickable(role = Role.Button) { c.forgetPhone() }.padding(12.dp))
                }
                if (!small) {
                    // 기록하는 법(폰 센서 조사: 진동은 조수석 바닥 · 콘솔, 운전석 발밑 · 핸들 · 거치대는 안 됨)
                    Column(Modifier.padding(top = 18.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf(Triple(Gi.AudioLines, "소리", "소리 나는 곳 가까이\n10초 녹음"), Triple(Gi.Vibrate, "진동", "조수석 바닥이나 콘솔에\n일정한 속도로 10~30초"),
                            Triple(Gi.Camera, "경고등", "켜진 기호를 찍고\n눌러서 고르기")).forEach { (ic, k, v) ->
                            Box(Modifier.weight(1f).fillMaxWidth().clip(R16).background(C.Surf2)) { TileContent(ic, C.Mint, k, v, valueColor = C.Mut, hint = true) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GIcon(Gi.Lock, C.Mut, 24.dp); Spacer(Modifier.width(8.dp))
                            Text("운전석 발밑 · 핸들 · 거치대에 두지 않습니다 · 주행 중 폰 조작 금지", style = ts(20.sp, C.Mut), maxLines = 2)
                        }
                    }
                }
            }
            code != null -> {
                val qr = minOf(maxWidth * 0.62f, maxHeight * 0.58f)
                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    QrImage("pleoscare://pair?code=$code&server=${c.link?.base ?: ""}", qr)
                    Spacer(Modifier.height(16.dp))
                    Text(code.chunked(3).joinToString(" "), style = ts(56.sp, C.Fg, FontWeight.Bold))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GIcon(Gi.Smartphone, C.Mut, 26.dp); Spacer(Modifier.width(8.dp))
                        Text("폰 앱에서 QR을 찍거나 코드를 넣으세요", style = ts(22.sp, C.Mut))
                    }
                }
            }
            else -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                GIcon(if (c.serverOk == false) Gi.MapPinOff else Gi.Radio, C.Amber, if (small) 64.dp else 96.dp)
                Spacer(Modifier.height(14.dp))
                Text(if (c.serverOk == false) "모의 서버에 연결하지 못했습니다" else "연결 코드를 받는 중", style = ts(26.sp, weight = FontWeight.SemiBold), textAlign = TextAlign.Center)
                if (c.serverOk == false) Text("PC에서 demo-app/server/care_server.py를 켠 뒤 다시 누르세요", style = ts(22.sp, C.Mut), textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.height(64.dp).clip(Pill).background(C.Surf2).border(2.dp, C.Mint, Pill).clickable(role = Role.Button) { c.startPair() }.padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    GIcon(Gi.Rotate, C.Mint, 28.dp); Spacer(Modifier.width(10.dp)); Text("다시 연결", style = ts(24.sp, C.Mint, FontWeight.SemiBold))
                }
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        HonestyBadge("모의 서버 · 데모"); Spacer(Modifier.weight(1f))
        InfoButton("폰 연결", listOf(
            "폰 앱(플레오스 슈퍼 케어 모바일)과 이 차를 짝 지어 증상을 함께 기록합니다",
            "소리: 폰을 소리 나는 곳 가까이 · 진동: 폰을 떨리는 곳에 올려 둠 · 경고등: 폰 카메라로 찍기",
            "데모는 PC의 모의 서버(${c.link?.base ?: "연결 없음"})를 거칩니다. 실제 서비스는 HTTPS 백엔드 서버입니다",
        ), enabled = !c.driving)
    }
}

/** QR 그림(zxing). */
@Composable
fun QrImage(content: String, size: Dp) {
    val bits = remember(content) { runCatching { QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0) }.getOrNull() }
    Canvas(Modifier.size(size).clip(R16).background(Color.White)) {
        val b = bits ?: return@Canvas
        val n = b.width; val cell = this.size.minDimension / (n + 6)
        for (y in 0 until n) for (x in 0 until n) if (b.get(x, y)) drawRect(Color.Black, Offset((x + 3) * cell, (y + 3) * cell), Size(cell + 0.5f, cell + 0.5f))
    }
}

/** 폰으로 기록하기 4칸(소리 · 진동 · 경고등 · 사진). */
@Composable
private fun CaptureTiles(c: DemoController, modifier: Modifier, recordId: String? = null) {
    val tiles = listOf(
        Triple("noise", Gi.AudioLines, "소리 기록"), Triple("vibration", Gi.Vibrate, "진동 기록"),
        Triple("warning_photo", Gi.Camera, "경고등 찍기"), Triple("photo", Gi.Image, "사진"),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (kind, ic, label) ->
                    val waiting = c.phoneWait?.kind == kind
                    ClickTile(Modifier.weight(1f).fillMaxHeight(), bg = if (waiting) C.OnMint else C.Surf, border = if (waiting) C.Mint else null,
                        onClick = { if (waiting) c.cancelPhoneWait() else c.requestPhone(kind, recordId) }) {
                        TileContent(ic, C.Mint, label, if (waiting) "폰에서 기록 중 · 누르면 취소" else shortGuide(kind),
                            valueColor = if (waiting) C.Mint else C.Mut, hint = true)
                    }
                }
            }
        }
    }
}

private fun shortGuide(kind: String) = when (kind) { "noise" -> "폰을 소리 가까이"; "vibration" -> "폰을 떨리는 곳에"; "warning_photo" -> "찍으면 바로 찾기"; else -> "부위 · 영수증" }

@Composable
private fun InboxPanel(c: DemoController, modifier: Modifier) = Panel(modifier, pad = 20.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.Inbox, C.Mint, 30.dp); Spacer(Modifier.width(10.dp))
        Text("폰에서 온 기록", style = ts(24.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
        Text("${c.inbox.size}건", style = ts(22.sp, C.Mut))
    }
    if (c.inbox.isEmpty()) {
        Spacer(Modifier.weight(1f))
        Text("아직 없습니다 · 위 칸을 누르면 폰이 안내합니다", style = ts(22.sp, C.Mut), modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.weight(1f))
        return@Panel
    }
    LazyColumn(Modifier.padding(top = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(c.inbox, key = { it.id }) { e ->
            EvidenceRow(e, Modifier.fillMaxWidth()) {
                c.book.records.firstOrNull { r -> r.evidence.any { it.id == e.id } }?.let { r -> c.lastRecord = r; c.go(Screen.RECORD) }
            }
        }
    }
}

// ---------------------------------------------------------------- 폰 증거(소리 · 진동 · 사진)
@Composable
fun EvidenceRow(e: Evidence, modifier: Modifier, onClick: (() -> Unit)? = null) {
    Row(modifier.height(92.dp).clip(R16).background(C.Surf2).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GIcon(evidenceIcon(e.kind), C.Mint, 36.dp); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(e.summary, style = ts(23.sp, weight = FontWeight.SemiBold), maxLines = 1)
            Text(listOfNotNull(DemoController.hm(e.wall), if (e.durationMs > 0) "${(e.durationMs + 500) / 1000}초" else null, e.placement, e.partHint).joinToString(" · "),
                style = ts(22.sp, C.Mut), maxLines = 1)
        }
        when {
            e.imagePath != null -> PhotoThumb(e.imagePath, Modifier.size(120.dp, 72.dp))
            e.spectrum.isNotEmpty() -> SpectrumChart(e.spectrum, Modifier.size(140.dp, 64.dp), labels = false)
            e.series.isNotEmpty() -> SeriesChart(e.series, Modifier.size(140.dp, 64.dp))
        }
        if (e.note == "시험 입력") { Spacer(Modifier.width(10.dp)); TestBadge() }
    }
}

/** 기록 상세의 증거 카드: 그래프 · 사진 · 소리 듣기 · 경고등 후보. */
@Composable
fun EvidenceCard(c: DemoController, e: Evidence, modifier: Modifier) = Panel(modifier, bg = C.Surf2, pad = 18.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(evidenceIcon(e.kind), C.Mint, 40.dp); Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("폰 · " + CareData.kindLabel(e.kind), style = ts(24.sp, weight = FontWeight.SemiBold), maxLines = 1)
            Text(listOfNotNull(DemoController.hm(e.wall), if (e.durationMs > 0) "${(e.durationMs + 500) / 1000}초" else null, e.placement).joinToString(" · "), style = ts(22.sp, C.Mut), maxLines = 1)
        }
        if (e.kind == "vibration") { HonestyBadge("실험"); Spacer(Modifier.width(8.dp)) }
        if (e.note == "시험 입력") TestBadge()
        e.audioPath?.let { path -> Spacer(Modifier.width(10.dp)); PlayButton(path) }
    }
    // 그 시각 차속과 바퀴 회전 빠르기(참고). 바퀴 지름 0.63m 가정, 원인 판정이 아닙니다(조사: 폰 센서 보고서).
    e.metrics["carKmh"]?.takeIf { it >= 5 }?.let { kmh ->
        val wheelHz = kmh / 3.6 / (Math.PI * 0.63)
        val dom = e.metrics["dominantHz"]
        val near = dom != null && listOf(1.0, 2.0).any { k -> kotlin.math.abs(dom - wheelHz * k) <= wheelHz * k * 0.12 }
        Row(Modifier.padding(top = 10.dp).fillMaxWidth().clip(R16).background(if (near) C.OnMint else C.Surf).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            GIcon(Gi.Wheel, if (near) C.Mint else C.Mut, 28.dp); Spacer(Modifier.width(10.dp))
            Text("차속 ${kmh.roundToInt()}km/h · 바퀴 회전 약 ${"%.1f".format(wheelHz)}Hz" + if (near) " · 주된 떨림과 비슷(참고)" else "",
                style = ts(22.sp, if (near) C.Mint else C.Mut, FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1)
        }
    }
    // 주요 값 칸(소리: 최대 · 주된 높이, 진동: 최대 · 평균 · 주된 떨림)
    val metrics = when (e.kind) {
        "noise" -> listOfNotNull(e.metrics["peakDb"]?.let { Triple(Gi.Volume, "최대 · 상대", "${it.roundToInt()}dBFS") }, e.metrics["avgDb"]?.let { Triple(Gi.AudioLines, "평균 · 상대", "${it.roundToInt()}dBFS") },
            e.metrics["dominantHz"]?.let { Triple(Gi.Activity, "주된 높이", "${it.roundToInt()}Hz") })
        "vibration" -> listOfNotNull(e.metrics["peakG"]?.let { Triple(Gi.Vibrate, "최대", "%.2fg".format(it)) }, e.metrics["rmsG"]?.let { Triple(Gi.Activity, "평균", "%.2fg".format(it)) },
            e.metrics["dominantHz"]?.let { Triple(Gi.Gauge, "주된 떨림", "${it.roundToInt()}Hz") })
        "warning_photo" -> listOfNotNull(e.metrics["confidence"]?.let { Triple(Gi.Scan, "색 비율", "${(it * 100).roundToInt()}%") })
        else -> emptyList()
    }
    if (metrics.isNotEmpty()) Row(Modifier.padding(top = 12.dp).height(84.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        metrics.forEach { (ic, k, v) -> Box(Modifier.weight(1f).fillMaxHeight().clip(R16).background(C.Surf)) { TileContent(ic, C.Mint, k, v, titleColor = C.Mut) } }
    }
    when {
        e.imagePath != null -> PhotoThumb(e.imagePath, Modifier.padding(top = 12.dp).fillMaxWidth().weight(1f))
        e.spectrum.isNotEmpty() -> {
            SpectrumChart(e.spectrum, Modifier.padding(top = 12.dp).fillMaxWidth().weight(1f), labels = true, maxHz = e.metrics["spectrumMaxHz"])
        }
        e.series.isNotEmpty() -> SeriesChart(e.series, Modifier.padding(top = 12.dp).fillMaxWidth().weight(1f))
        else -> Spacer(Modifier.weight(1f))
    }
    if (e.kind == "warning_photo" && e.candidates.isNotEmpty()) {
        Text("폰 사진으로 찾은 후보", style = ts(22.sp, C.Mut), modifier = Modifier.padding(top = 10.dp))
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            e.candidates.take(3).forEach { id ->
                val entry = WarningCatalog.byId(id) ?: WarningCatalog.ENTRIES.firstOrNull { it.name == id }
                SelectChip(entry?.name ?: id, false, icon = entry?.let { shapeIcon(it.shape) } ?: Gi.Engine, color = C.Amber) { entry?.let { c.openWarnDetail(it.id) } }
            }
        }
    }
}

@Composable
private fun PlayButton(path: String) {
    Box(Modifier.size(56.dp).clip(Pill).background(C.Mint).clickable(role = Role.Button) {
        runCatching { MediaPlayer().apply { setDataSource(path); setOnCompletionListener { it.release() }; prepare(); start() } }
    }.semantics { contentDescription = "소리 듣기" }, contentAlignment = Alignment.Center) { GIcon(Gi.Play, C.Dark, 28.dp) }
}

@Composable
fun PhotoThumb(path: String, modifier: Modifier) {
    val img = remember(path) {
        runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }; BitmapFactory.decodeFile(path, o)
            var s = 1; while (o.outWidth / s > 1200) s *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
        }.getOrNull()
    }
    Box(modifier.clip(R16).background(C.Surf), contentAlignment = Alignment.Center) {
        if (img != null) Image(img, "폰 사진", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else GIcon(Gi.Image, C.Dim, 40.dp)
    }
}

/** 소리 · 진동 스펙트럼(막대). 가장 큰 막대를 민트로 칠합니다. */
@Composable
fun SpectrumChart(bins: List<Float>, modifier: Modifier, labels: Boolean, maxHz: Double? = null) {
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            if (bins.isEmpty()) return@Canvas
            val lo = bins.min(); val hi = bins.max(); val span = (hi - lo).takeIf { it > 1e-3f } ?: 1f
            val w = size.width / bins.size; val peak = bins.indexOf(hi)
            drawRoundRect(C.Surf, cornerRadius = CornerRadius(12f))
            bins.forEachIndexed { i, v ->
                val h = ((v - lo) / span).coerceIn(0.03f, 1f) * size.height * 0.92f
                drawRect(if (i == peak) C.Mint else C.Mint.copy(alpha = 0.45f), Offset(i * w + w * 0.15f, size.height - h), Size(w * 0.7f, h))
            }
        }
        if (labels && maxHz != null) Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("0", style = ts(20.sp, C.Mut)); Spacer(Modifier.weight(1f))
            Text(hzLabel(maxHz / 2), style = ts(20.sp, C.Mut)); Spacer(Modifier.weight(1f))
            Text(hzLabel(maxHz), style = ts(20.sp, C.Mut))
        }
    }
}

private fun hzLabel(hz: Double) = if (hz >= 1000) "%.1fkHz".format(hz / 1000) else "${hz.roundToInt()}Hz"

/** 시간에 따른 값(진동 크기 · 소리 크기) 선 그래프. */
@Composable
fun SeriesChart(values: List<Float>, modifier: Modifier) {
    Canvas(modifier) {
        drawRoundRect(C.Surf, cornerRadius = CornerRadius(12f))
        if (values.size < 2) return@Canvas
        val hi = values.max().takeIf { it > 1e-4f } ?: 1f
        val p = Path()
        values.forEachIndexed { i, v ->
            val x = i * size.width / (values.size - 1); val y = size.height - (v / hi).coerceIn(0f, 1f) * size.height * 0.9f
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        drawPath(p, C.Mint, style = Stroke(width = 3.dp.toPx()))
    }
}

// ---------------------------------------------------------------- 기록 자세히(심한 정도 · 빈도 · 나는 때 · 처리 흐름 · 폰 기록)
/** 기록 처리 흐름 4단계 아이콘. */
@Composable
fun StatusFlow(status: String, modifier: Modifier = Modifier, onPick: ((RecordStatus) -> Unit)? = null) {
    val cur = RecordStatus.entries.indexOfFirst { it.name == status }.coerceAtLeast(0)
    val icons = listOf(Gi.Rec, Gi.Eye, Gi.Wrench, Gi.CircleCheck)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        RecordStatus.entries.forEachIndexed { i, st ->
            if (i > 0) Box(Modifier.weight(0.25f).height(3.dp).clip(Pill).background(if (i <= cur) C.Mint else C.Line))
            Column(Modifier.weight(1f).clip(R16).then(if (onPick != null) Modifier.clickable(role = Role.Button) { onPick(st) } else Modifier).padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(48.dp).clip(Pill).background(if (i == cur) C.Mint.copy(alpha = 0.22f) else Color.Transparent).border(2.dp, if (i <= cur) C.Mint else C.Line, Pill),
                    contentAlignment = Alignment.Center) { GIcon(icons[i], if (i <= cur) C.Mint else C.Dim, 26.dp) }
                Text(st.label, style = ts(20.sp, if (i == cur) C.Mint else C.Mut, if (i == cur) FontWeight.SemiBold else FontWeight.Normal), maxLines = 1)
            }
        }
    }
}

/** 기록 자세히 고르기(정차 중). 넓은 창 오른쪽 칸 · 세로 아래쪽에 둡니다. */
@Composable
fun RecordDetailPicker(c: DemoController, r: RecordSnap, modifier: Modifier) = Panel(modifier, pad = 20.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.Pencil, C.Mint, 28.dp); Spacer(Modifier.width(10.dp))
        Text("자세히", style = ts(24.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
        InfoButton("자세히 남기기", listOf("정비사가 증상을 다시 내 볼 때 묻는 질문입니다", "고르지 않아도 기록은 남습니다"), enabled = !c.driving)
    }
    Text("심한 정도", style = ts(22.sp, C.Mut), modifier = Modifier.padding(top = 10.dp))
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RecordRules.SEVERITIES.forEachIndexed { i, s ->
            SelectChip(s, r.severity == i, Modifier.weight(1f), color = if (i == 3) C.Amber else C.Mint) { c.updateRecord(r.id) { it.copy(severity = i) } }
        }
    }
    Text("얼마나 자주", style = ts(22.sp, C.Mut), modifier = Modifier.padding(top = 12.dp))
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        RecordRules.FREQUENCIES.forEach { f -> SelectChip(f, r.frequency == f, Modifier.weight(1f)) { c.updateRecord(r.id) { it.copy(frequency = if (it.frequency == f) null else f) } } }
    }
    Text("언제 나나요 · 여러 개", style = ts(22.sp, C.Mut), modifier = Modifier.padding(top = 12.dp))
    RecordRules.CONDITIONS.chunked(3).forEach { row ->
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { k ->
                val on = k in r.conditions
                SelectChip(k, on, Modifier.weight(1f)) { c.updateRecord(r.id) { it.copy(conditions = if (on) it.conditions - k else it.conditions + k) } }
            }
        }
    }
    Text("폰으로 더 기록", style = ts(22.sp, C.Mut), modifier = Modifier.padding(top = 12.dp))
    Row(Modifier.padding(top = 6.dp).height(72.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Triple("noise", Gi.AudioLines, "소리"), Triple("vibration", Gi.Vibrate, "진동"), Triple("photo", Gi.Image, "사진")).forEach { (kind, ic, label) ->
            val waiting = c.phoneWait?.kind == kind && c.phoneWait?.recordId == r.id
            ClickTile(Modifier.weight(1f).fillMaxHeight(), bg = if (waiting) C.OnMint else C.Surf2, border = if (waiting) C.Mint else null,
                onClick = { if (waiting) c.cancelPhoneWait() else c.requestPhone(kind, r.id, r.part) }) {
                TileContent(ic, C.Mint, if (waiting) "기록 중" else label, titleColor = if (waiting) C.Mint else C.Fg)
            }
        }
    }
}

// ---------------------------------------------------------------- 정비소 상세 · 후기 · 방문
/** 정비소 상세: 거리 · 시간, 정보, 별점 요약, 다른 사용자 후기, 내 방문. */
@Composable
fun ShopCareDetail(c: DemoController, modifier: Modifier) = Panel(modifier, pad = 22.dp) {
    c.careVersion
    val s = c.shops.getOrNull(c.selectedShop)
    val info = c.selectedShopInfo()
    if (s == null || info == null) { Text("정비소를 고르세요", style = ts(26.sp, C.Mut)); return@Panel }
    val reviews = c.shopReviews(info.id)
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.Wrench, C.Amber, 48.dp); Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(info.name, style = ts(30.sp, weight = FontWeight.Bold), maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 확인된 후기 5개 미만이면 별점을 숨깁니다(조사 6.4절).
                if (info.reviewCount >= CareData.MIN_REVIEWS_FOR_STARS) {
                    Stars(info.rating, 26.dp); Spacer(Modifier.width(10.dp))
                    Text("%.1f · 방문 확인 후기 %d개".format(info.rating, info.reviewCount), style = ts(22.sp, C.Mut))
                } else Text("후기 ${info.reviewCount}개 · 5개부터 별점 공개", style = ts(22.sp, C.Mut))
            }
        }
        if (info.partner) { HonestyBadge("제휴"); Spacer(Modifier.width(8.dp)) }
        if (info.example) HonestyBadge("예시")
    }
    val d = s.distanceM; val e = s.etaS
    Row(Modifier.padding(top = 14.dp).height(92.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(Triple(Gi.Navigation, "거리", d?.let { "%.1fkm".format(it / 1000f) } ?: "-"), Triple(Gi.Clock, "걸리는 시간", e?.let { "${(it + 59) / 60}분" } ?: "-"),
            Triple(Gi.MapPin, "도착", e?.let { DemoController.hm(c.wallNowPublic() + it * 1000L) } ?: "-")).forEach { (ic, k, v) ->
            Box(Modifier.weight(1f).fillMaxHeight().clip(R16).background(C.Surf2)) { TileContent(ic, C.Amber, k, v, titleColor = C.Mut) }
        }
    }
    if (c.driving) {
        // 주행 중: 별점 숫자와 후기 수만(조사 6.5절)
        Spacer(Modifier.weight(1f))
        Text("후기는 정차 후에 봅니다", style = ts(24.sp, C.Mut), modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.weight(1f))
        return@Panel
    }
    Column(Modifier.padding(top = 14.dp).weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 정보
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            StatusChip(Gi.Clock, info.hours.substringBefore(" ·"), C.Mint)
            if (info.ev) StatusChip(Gi.Battery, "전기차 정비", C.Mint)
        }
        Text(info.services.joinToString(" · "), style = ts(22.sp, C.Fg), maxLines = 2)
        info.certs.forEach { cert -> Row(verticalAlignment = Alignment.CenterVertically) { GIcon(Gi.ShieldCheck, C.Mint, 24.dp); Spacer(Modifier.width(8.dp)); Text(cert, style = ts(22.sp, C.Mut)) } }
        Row(verticalAlignment = Alignment.CenterVertically) { GIcon(Gi.Receipt, C.Mut, 24.dp); Spacer(Modifier.width(8.dp)); Text(info.priceNote, style = ts(22.sp, C.Mut)) }
        // 사실 질문의 "예" 비율(조사 6.4절)
        if (reviews.isNotEmpty()) {
            Text("방문한 사람들의 답", style = ts(22.sp, C.Mut), modifier = Modifier.padding(top = 6.dp))
            CareData.REVIEW_FACTS.forEach { fact ->
                val vals = reviews.mapNotNull { it.dims[fact] }
                if (vals.isNotEmpty()) {
                    val yes = vals.count { it >= 1 }.toFloat() / vals.size
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(fact, style = ts(22.sp), modifier = Modifier.width(170.dp), maxLines = 1)
                        Progress(yes, C.Amber, Modifier.weight(1f)); Spacer(Modifier.width(12.dp))
                        Text("${(yes * 100).roundToInt()}%", style = ts(22.sp, C.Amber, FontWeight.SemiBold))
                    }
                }
            }
        }
        // 내 방문
        val visits = c.visitsAt(info.id)
        if (visits.isNotEmpty()) {
            Text("내 방문", style = ts(22.sp, C.Mut), modifier = Modifier.padding(top = 6.dp))
            visits.take(3).forEach { v ->
                Row(Modifier.fillMaxWidth().clip(R16).background(C.Surf2).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    GIcon(Gi.Calendar, C.Mint, 28.dp); Spacer(Modifier.width(12.dp))
                    Text(DemoController.monthDay(v.wall) + " · " + v.work, style = ts(22.sp), modifier = Modifier.weight(1f), maxLines = 1)
                    if (v.reviewed) StatusChip(Gi.CircleCheck, "후기 씀", C.Mint) else Text("후기 쓰기", style = ts(22.sp, C.Amber, FontWeight.SemiBold), modifier = Modifier.clickable { c.openReview(info.id) })
                }
            }
        }
        // 후기
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("후기 ${reviews.size}개", style = ts(22.sp, C.Mut), modifier = Modifier.weight(1f))
            if (!c.shopsFromServer) HonestyBadge("예시 후기")
        }
        reviews.forEach { r -> ReviewItem(r) { c.reportReview(r.id) } }
    }
    val can = c.canReview(info.id)
    Row(Modifier.padding(top = 12.dp).height(76.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ClickTile(Modifier.weight(1f).fillMaxHeight(), bg = C.Surf2, border = if (can) null else C.Mint, onClick = { c.addVisit() }) { TileContent(Gi.Calendar, C.Mint, "방문 기록") }
        ClickTile(Modifier.weight(1f).fillMaxHeight(), bg = C.Surf2, border = if (can) C.Amber else null, onClick = { c.openReview(info.id) }) {
            TileContent(Gi.Pencil, if (can) C.Amber else C.Dim, "후기 쓰기", if (can) null else "방문 기록 뒤", titleColor = if (can) C.Fg else C.Mut, valueColor = C.Mut)
        }
    }
}

@Composable
private fun ReviewItem(r: Review, onReport: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(R16).background(if (r.mine) C.OnMint else C.Surf2).then(if (r.mine) Modifier.border(2.dp, C.Mint, R16) else Modifier).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Stars(r.rating.toFloat(), 22.dp); Spacer(Modifier.width(10.dp))
            Text(r.author, style = ts(22.sp, if (r.mine) C.Mint else C.Fg, FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1)
            if (r.verified) { StatusChip(Gi.CheckCheck, "방문 확인", C.Mint); Spacer(Modifier.width(8.dp)) }
            Text(DemoController.monthDay(r.wall), style = ts(20.sp, C.Mut))
        }
        if (r.tags.isNotEmpty()) Text(r.tags.joinToString(" · ") { "#$it" }, style = ts(20.sp, C.Amber), modifier = Modifier.padding(top = 4.dp), maxLines = 1)
        if (r.text.isNotBlank()) Text(r.text, style = ts(22.sp, lineHeight = 30.sp), modifier = Modifier.padding(top = 4.dp), maxLines = 3)
        if (!r.mine) Text("신고", style = ts(20.sp, C.Mut, FontWeight.SemiBold), modifier = Modifier.align(Alignment.End).clip(Pill).clickable(role = Role.Button, onClick = onReport).padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

// ---------------------------------------------------------------- 후기 쓰기
private val RATING_WORDS = listOf("별을 눌러 주세요", "아쉬워요", "그저 그래요", "괜찮아요", "좋아요", "아주 좋아요")

@Composable
fun ReviewScreen(c: DemoController) = ScreenFrame {
    val shop = c.shopInfos.firstOrNull { it.id == c.reviewShopId }
    ScreenHead(c, Tab.SERVICE, "후기 쓰기") {
        HonestyBadge(if (c.link != null) "모의 서버에 올림" else "이 차에만 저장"); Spacer(Modifier.width(12.dp))
    }
    if (shop == null || c.driving) {
        LockedNote(if (c.driving) "후기는 정차 중에 씁니다" else "정비소를 먼저 고르세요"); Spacer(Modifier.weight(1f))
        BottomButtons(Btn("정비소로", Gi.Wrench) { c.go(Screen.SERVICE) }, Btn("홈으로", Gi.Home) { c.go(Screen.HOME) }, RightKind.AMBER)
        return@ScreenFrame
    }
    Row(Modifier.fillMaxWidth().clip(R16).background(C.Surf).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.Wrench, C.Amber, 36.dp); Spacer(Modifier.width(14.dp))
        Text(shop.name, style = ts(26.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f), maxLines = 1)
        c.visitsAt(shop.id).firstOrNull()?.let { v -> Text("방문 " + DemoController.monthDay(v.wall), style = ts(22.sp, C.Mut)) }
    }
    if (LocalWide.current) Row(Modifier.padding(top = 14.dp).weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BigStars(c, Modifier.weight(0.9f))
            DimsPanel(c, Modifier.weight(1.1f))
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TagsPanel(c, Modifier)
            MemoPanel(c, Modifier.weight(1f))
        }
    } else Column(Modifier.padding(top = 14.dp).weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        BigStars(c, Modifier.height(220.dp))
        DimsPanel(c, Modifier.height(380.dp))
        TagsPanel(c, Modifier)
        MemoPanel(c, Modifier.height(220.dp))
    }
    BottomButtons(Btn("취소", Gi.Close) { c.go(Screen.SERVICE) }, Btn("후기 남기기", Gi.Send) { c.submitReview() }, RightKind.AMBER)
}

@Composable
private fun BigStars(c: DemoController, modifier: Modifier) = Panel(modifier, pad = 20.dp) {
    Text("별점", style = ts(24.sp, weight = FontWeight.SemiBold))
    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        val star = minOf(maxWidth / 6.2f, maxHeight * 0.62f).coerceIn(48.dp, 120.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(star * 0.12f)) {
                for (i in 1..5) Box(Modifier.size(star).clip(Pill).clickable(role = Role.Button) { c.draftRating = i }.semantics { contentDescription = "별 ${i}개" }, contentAlignment = Alignment.Center) {
                    GIcon(if (c.draftRating >= i) Gi.StarFill else Gi.StarLine, if (c.draftRating >= i) C.Amber else C.Dim, star * 0.9f)
                }
            }
            Text(RATING_WORDS[c.draftRating], style = ts(26.sp, if (c.draftRating == 0) C.Mut else C.Amber, FontWeight.SemiBold), modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun DimsPanel(c: DemoController, modifier: Modifier) = Panel(modifier, pad = 20.dp) {
    Text("사실 질문 · 고르지 않아도 됩니다", style = ts(22.sp, C.Mut))
    Column(Modifier.padding(top = 8.dp).weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
        CareData.REVIEW_FACTS.forEach { fact ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(fact, style = ts(24.sp), modifier = Modifier.weight(1f), maxLines = 1)
                val v = c.draftDims[fact]
                listOf(1 to "예", 0 to "아니요").forEach { (n, label) ->
                    SelectChip(label, v == n, Modifier.padding(start = 10.dp).width(120.dp), color = if (n == 1) C.Mint else C.Amber, height = 64.dp) {
                        c.draftDims = if (v == n) c.draftDims - fact else c.draftDims + (fact to n)
                    }
                }
            }
        }
    }
}

@Composable
private fun TagsPanel(c: DemoController, modifier: Modifier) = Panel(modifier, pad = 20.dp) {
    Text("한 줄 표시", style = ts(22.sp, C.Mut))
    CareData.REVIEW_TAGS.chunked(2).forEach { row ->
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            row.forEach { t ->
                val on = t in c.draftTags
                SelectChip(t, on, Modifier.weight(1f), color = C.Amber) { c.draftTags = if (on) c.draftTags - t else c.draftTags + t }
            }
        }
    }
}

@Composable
private fun MemoPanel(c: DemoController, modifier: Modifier) = Panel(modifier, pad = 20.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("메모", style = ts(22.sp, C.Mut), modifier = Modifier.weight(1f))
        Row(Modifier.height(56.dp).clip(Pill).background(C.Surf2).border(1.5.dp, C.Line, Pill).clickable(role = Role.Button) { c.reviewByVoice() }.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically) {
            GIcon(Gi.Mic, C.Mint, 26.dp); Spacer(Modifier.width(8.dp)); Text("말로", style = ts(22.sp, C.Mint, FontWeight.SemiBold))
        }
    }
    Box(Modifier.padding(top = 10.dp).fillMaxWidth().weight(1f).clip(R16).background(C.Surf2).padding(16.dp)) {
        if (c.draftText.isEmpty()) Text("말로 남기거나 눌러서 쓰세요(정차 중)", style = ts(22.sp, C.Dim))
        BasicTextField(c.draftText, { c.draftText = it.take(300) }, Modifier.fillMaxSize(), textStyle = ts(24.sp, lineHeight = 32.sp), cursorBrush = SolidColor(C.Mint))
    }
}

// ---------------------------------------------------------------- 짐 알림(겹침)
/** 도착 뒤 내리려 할 때 뜨는 짐 알림. 주행 중에는 그리지 않습니다. 트렁크를 열면 저절로 닫힙니다. */
@Composable
fun LeftItemOverlay(c: DemoController) {
    val a = c.leftAlert ?: return
    if (c.driving) return
    Box(Modifier.fillMaxSize().background(Color(0xD9000000)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 980.dp).fillMaxWidth(0.88f).clip(R24).background(C.Surf).border(3.dp, C.Mint, R24).padding(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                if (LeftItemCare.Kind.TRUNK in a.kinds) GIcon(Gi.Trunk, C.Mint, 140.dp)
                if (LeftItemCare.Kind.REAR_SEAT in a.kinds) GIcon(Gi.Seat, C.Mint, 120.dp)
                GIcon(Gi.Luggage, C.Amber, 110.dp)
            }
            Text(c.leftText(a), style = ts(42.sp, weight = FontWeight.Bold, lineHeight = 54.sp), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 24.dp))
            Text(listOfNotNull(a.loadedWall?.let { (if (LeftItemCare.Kind.TRUNK in a.kinds) "트렁크 연 시각 " else "뒷문 연 시각 ") + DemoController.hm(it) },
                a.arrivedWall?.let { "도착 " + DemoController.hm(it) }).joinToString(" · "), style = ts(26.sp, C.Mut), modifier = Modifier.padding(top = 10.dp))
            if (c.pairedPhone != null) Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                GIcon(Gi.Smartphone, C.Mut, 28.dp); Spacer(Modifier.width(8.dp)); Text("폰에도 알렸습니다", style = ts(22.sp, C.Mut))
            }
            if (a.test) Box(Modifier.padding(top = 10.dp)) { TestBadge() }
            Row(Modifier.padding(top = 28.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                BigBtn(Btn("짐 없어요", Gi.Close) { c.answerLeftAlert(noItems = true) }, Modifier.weight(1f).height(110.dp), C.Surf2, C.Fg, iconTint = C.Fg)
                BigBtn(Btn("확인했어요", Gi.CircleCheck) { c.answerLeftAlert(noItems = false) }, Modifier.weight(1f).height(110.dp), C.Mint, C.Dark)
            }
        }
    }
}
