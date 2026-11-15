package app.pleosguard.demo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pleosguard.demo.R
import app.pleosguard.demo.logic.RulesLab
import kotlinx.coroutines.delay

val R24 = RoundedCornerShape(24.dp)
val R16 = RoundedCornerShape(16.dp)
val Pill = RoundedCornerShape(50)

/** 가로 화면(넓은 창)이면 true. 앱 뿌리에서 창 크기로 정합니다(UX 4.2: 세로 한 창 기본, 가로 확장). */
val LocalWide = compositionLocalOf { false }

/** 가로 화면에서 왼쪽 도크 틀 안에 그리면 true. 이때 머리줄과 위쪽 탭은 도크 틀이 대신 그립니다. */
val LocalDocked = compositionLocalOf { false }

/** 화면 바깥 틀. 세로 한 창(약 840dp)을 기준으로 그립니다. 가로 화면에서는 창 폭을 채웁니다(레터박스 없음). */
@Composable
fun ScreenFrame(bg: Color = C.Bg, content: @Composable ColumnScope.() -> Unit) {
    val wide = LocalWide.current
    Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = if (wide) 1600.dp else 960.dp).fillMaxHeight().padding(horizontal = 24.dp, vertical = 16.dp), content = content)
    }
}

/** 위 머리줄: 로고 · 앱 이름 · 오른쪽 "시연 모드" 표시(누르면 시연 패널). 도크 틀 안에서는 틀이 그리므로 생략합니다. */
@Composable
fun AppHeader(onDemo: () -> Unit) {
    if (LocalDocked.current) return
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.logo_symbol), contentDescription = "플레오스 슈퍼 케어 로고", modifier = Modifier.size(58.dp))
        Spacer(Modifier.width(20.dp))
        Text("플레오스 슈퍼 케어", style = ts(30.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
        DemoBadge(onDemo)
    }
}

@Composable
fun DemoBadge(onClick: () -> Unit, onEmergency: Boolean = false) {
    val fg = if (onEmergency) C.EDraft else C.Mut
    Box(
        Modifier.clip(Pill).border(1.5.dp, fg.copy(alpha = 0.7f), Pill).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) { Text("에뮬레이터 시연", style = ts(22.sp, fg, FontWeight.Medium)) }
}

/** 세로 한 창의 위쪽 탭(2026-11-14 케어 전환): 관리 · 기록 · 정비소 · 구독. 안심 케어(보호자 알림)는 관리 화면과 가로 도크에서 엽니다. */
enum class Tab(val label: String, val icon: Gi, val color: Color, val on: Color) {
    MANAGE("관리", Gi.Gauge, C.Mint, C.OnMint),
    RECORD("기록", Gi.History, C.Mint, C.OnMint),
    SERVICE("정비소", Gi.Wrench, C.Amber, C.OnAmber),
    SUBSCRIBE("구독", Gi.Repeat, C.Purple, C.OnPurple),
}

/** 세로 한 창의 위쪽 탭 4개. 가로 화면에서는 왼쪽 도크 6개가 대신합니다. */
@Composable
fun TabBar(selected: Tab?, onTab: (Tab) -> Unit) {
    if (LocalDocked.current) return
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tab.entries.forEach { t ->
            val on = t == selected
            Column(
                Modifier.weight(1f).height(96.dp).clip(R24)
                    .background(if (on) t.on else C.Surf)
                    .then(if (on) Modifier.border(2.dp, t.color, R24) else Modifier)
                    .clickable(role = Role.Tab) { onTab(t) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                GIcon(t.icon, if (on) t.color else C.Mut, 36.dp)
                Spacer(Modifier.height(8.dp))
                Text(t.label, style = ts(22.sp, if (on) t.color else C.Mut, if (on) FontWeight.SemiBold else FontWeight.Normal))
            }
        }
    }
}

@Composable
fun StatusChip(icon: Gi, text: String, tint: Color = C.Mint, bg: Color = C.Surf, fg: Color = C.Fg) {
    Row(
        Modifier.height(52.dp).clip(Pill).background(bg).border(1.5.dp, C.Line, Pill).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GIcon(icon, tint, 28.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, style = ts(22.sp, fg, FontWeight.SemiBold), maxLines = 1)
    }
}

/** [시험 입력] 배지. 시연 모드의 시험 입력 재생기가 넣은 값에만 붙입니다. */
@Composable
fun TestBadge(text: String = "시험 입력") = HonestyBadge(text)

@Composable
fun MockBadge(text: String = "모의") = HonestyBadge(text)

/**
 * 정직성 꼬리표(시험 입력 · 예시 기록 · 모의 발송 · 안내 예시 · 검토 전 · 설계 · 시연). 문구는 그대로 두고 카드마다 한 번만 씁니다.
 * 노랑 · 빨강은 차의 주의 · 위험에만 쓰므로(2026-11-14 아이콘 조사 규칙 4 · 5) 회색 점선 테두리로 그립니다.
 */
@Composable
fun HonestyBadge(text: String) {
    Box(
        Modifier.drawBehind {
            drawRoundRect(C.Mut, cornerRadius = CornerRadius(size.height / 2),
                style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 5.dp.toPx()))))
        }.padding(horizontal = 12.dp, vertical = 3.dp)
    ) { Text(text, style = ts(20.sp, C.Mut, FontWeight.SemiBold), maxLines = 1) }
}

/**
 * i 단추: 설명과 데이터 출처(Vehicle SDK · FusedLocation · NaviHelper 등)를 접어 둡니다. 누르면 작은 창이 열립니다.
 * 주행 중에는 그리지 않습니다([enabled] = 정차 중). 2026-11-14 아이콘 조사 규칙 2.
 */
@Composable
fun InfoButton(title: String, lines: List<String>, enabled: Boolean = true, size: Dp = 48.dp) {
    if (!enabled) return
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier.size(size).clip(Pill).border(2.dp, C.Line, Pill).clickable(role = Role.Button) { open = true }
                .semantics { contentDescription = "$title 설명" },
            contentAlignment = Alignment.Center,
        ) { GIcon(Gi.Info, C.Mut, size * 0.62f) }
        if (open) Popup(alignment = Alignment.TopEnd, onDismissRequest = { open = false }, properties = PopupProperties(focusable = true)) {
            Column(Modifier.widthIn(max = 620.dp).clip(R24).background(C.Surf2).border(2.dp, C.Line, R24).padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GIcon(Gi.Info, C.Mint, 34.dp); Spacer(Modifier.width(12.dp))
                    Text(title, style = ts(26.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                    Box(Modifier.size(56.dp).clip(Pill).clickable(role = Role.Button) { open = false }.semantics { contentDescription = "닫기" },
                        contentAlignment = Alignment.Center) { GIcon(Gi.Close, C.Mut, 26.dp) }
                }
                lines.forEach { Text("· $it", style = ts(22.sp, C.Fg, lineHeight = 31.sp), modifier = Modifier.padding(top = 8.dp)) }
            }
        }
    }
}

/**
 * 보호자 알림 단계: 접수(받은 편지함) → 전달(보내기) → 열람(눈). 지난 단계는 민트, 지금 단계는 채운 원, 남은 단계는 흐리게.
 * 2026-11-14 아이콘 조사 규칙 10.
 */
@Composable
fun StepIcons(step: Int, size: Dp = 34.dp, color: Color = C.Mint) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        listOf(Gi.Inbox to "접수", Gi.Send to "전달", Gi.Eye to "열람").forEachIndexed { i, (ic, label) ->
            val n = i + 1
            if (i > 0) Box(Modifier.width(14.dp).height(3.dp).clip(Pill).background(if (n <= step) color else C.Line))
            Box(
                Modifier.size(size + 12.dp).clip(Pill).background(if (n == step) color.copy(alpha = 0.22f) else Color.Transparent)
                    .border(2.dp, if (n <= step) color else C.Line, Pill).semantics { contentDescription = label + if (n <= step) " 됨" else " 전" },
                contentAlignment = Alignment.Center,
            ) { GIcon(ic, if (n <= step) color else C.Dim, size * 0.72f) }
        }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, bg: Color = C.Surf, pad: Dp = 24.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(R24).background(bg).padding(pad), content = content)
}

/** 아래 고정 버튼. 왼쪽 = 끝내기·취소·기록(중립색), 오른쪽 = 도움·보내기·감속 실패(빨강) 또는 그 화면의 주 행동 색(UX 4.4절). */
enum class RightKind { HELP_DARK, EMERGENCY, MINT, AMBER, PURPLE }

/** 아래 버튼 하나. [trailing]을 주면 글 뒤 오른쪽 끝에 "다음" 같은 표시를 그립니다. */
data class Btn(val label: String, val icon: Gi, val sub: String? = null, val trailing: Gi? = null, val onClick: () -> Unit)

/**
 * [rightDim]: 오른쪽 주 행동이 아직 준비되지 않았을 때(예: 증상을 고르기 전 "대처 방법 보기") 흐리게 그립니다. 눌리기는 하며 무엇이 필요한지 알립니다.
 * [leftTint]: 왼쪽 아이콘 색(기본 민트).
 */
@Composable
fun BottomButtons(left: Btn, right: Btn, kind: RightKind, emergency: Boolean = false, tall: Boolean = false, guardKey: Any? = null,
                  rightDim: Boolean = false, leftTint: Color? = null, rightWeight: Float = 1f) {
    // 화면이 바뀐 직후 0.5초는 터치를 받지 않습니다(rules-lab input_guard_ms_after_emergency_transition).
    var guarded by remember(guardKey) { mutableStateOf(guardKey != null) }
    if (guardKey != null) LaunchedEffect(guardKey) { delay(RulesLab.INPUT_GUARD_MS); guarded = false }
    val h = if (tall) 148.dp else 96.dp
    val (rbg, rfg) = if (rightDim) C.Surf2 to C.Mut else when (kind) {
        RightKind.HELP_DARK -> C.Help to C.Fg
        RightKind.EMERGENCY -> C.Red to C.Dark
        RightKind.MINT -> C.Mint to C.Dark
        RightKind.AMBER -> C.Amber to C.Dark
        RightKind.PURPLE -> C.Purple to C.Dark
    }
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        BigBtn(left, Modifier.weight(1f).height(h), if (emergency) C.ENeu else C.Surf2, C.Fg,
            iconTint = leftTint ?: if (emergency || left.icon == Gi.CircleCheck) C.Fg else C.Mint, enabled = !guarded)
        BigBtn(right, Modifier.weight(rightWeight).height(h), rbg, rfg, iconTint = rfg, enabled = !guarded)
    }
}

@Composable
fun BigBtn(b: Btn, modifier: Modifier, bg: Color, fg: Color, iconTint: Color = fg, enabled: Boolean = true) {
    Row(
        modifier.clip(R24).background(bg).border(1.5.dp, Color.White.copy(alpha = 0.06f), R24)
            .clickable(enabled = enabled, role = Role.Button, onClick = b.onClick).padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GIcon(b.icon, iconTint, 40.dp)
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(b.label, style = ts(30.sp, fg, FontWeight.SemiBold), maxLines = if (b.sub != null) 1 else 2, overflow = TextOverflow.Ellipsis)
            if (b.sub != null) Text(b.sub, style = ts(22.sp, fg.copy(alpha = 0.86f), FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (b.trailing != null) { Spacer(Modifier.width(12.dp)); GIcon(b.trailing, iconTint, 44.dp) }
    }
}

/** 긴급 화면 위 띠. 일반 탭을 숨기고 붉은 띠로 일반 메뉴와 구별합니다. */
@Composable
fun EmergencyBand(title: String, sub: String, onDemo: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(112.dp).clip(R24).background(C.EBand).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GIcon(Gi.OctagonAlert, C.Fg, 52.dp)
        Spacer(Modifier.width(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = ts(32.sp, C.Fg, FontWeight.Bold))
            Text(sub, style = ts(24.sp, C.Fg, FontWeight.Medium))
        }
        DemoBadge(onDemo, onEmergency = true)
    }
}

/** 점선 테두리의 "문장 자리". 검토된 취급설명서 문장이 들어갈 자리이며 지어낸 조작 문장을 넣지 않습니다(F5). */
@Composable
fun SentenceSlot(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().clip(R24).background(C.ECard).padding(12.dp)) {
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(C.EPink, style = Stroke(3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f))),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f))
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GIcon(Gi.Volume, C.EPink, 28.dp)
                Spacer(Modifier.width(10.dp))
                Text("지금 나오는 음성 · 화면 문장", style = ts(22.sp, C.EPink, FontWeight.Medium), modifier = Modifier.weight(1f))
                Box(Modifier.clip(Pill).border(1.5.dp, C.EPink, Pill).padding(horizontal = 12.dp, vertical = 2.dp)) {
                    Text("문장 자리", style = ts(22.sp, C.EPink, FontWeight.Medium))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(text, style = ts(28.sp, C.Fg, FontWeight.SemiBold, 38.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** 화면 아래쪽에 잠깐 뜨는 한 줄 알림(전체 화면 팝업을 쓰지 않습니다). */
@Composable
fun Toast(text: String?, modifier: Modifier = Modifier) {
    if (text == null) return
    Box(modifier.clip(R16).background(C.Surf2).border(1.5.dp, C.Mint, R16).padding(horizontal = 24.dp, vertical = 14.dp)) {
        Text(text, style = ts(24.sp, C.Fg, FontWeight.SemiBold))
    }
}

@Composable
fun SectionLabel(num: Int?, text: String, hint: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (num != null) {
            Box(Modifier.size(40.dp).clip(Pill).background(C.Amber), contentAlignment = Alignment.Center) {
                Text("$num", style = ts(22.sp, C.Dark, FontWeight.Bold))
            }
            Spacer(Modifier.width(12.dp))
        }
        Text(text, style = ts(26.sp, C.Fg, FontWeight.SemiBold), modifier = Modifier.weight(1f))
        if (hint != null) Text(hint, style = ts(22.sp, C.Mut))
    }
}
