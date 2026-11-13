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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
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

/** 화면 바깥 틀. 세로 한 창(약 840dp)을 기준으로 그리고, 넓은 창에서는 가운데에 둡니다. */
@Composable
fun ScreenFrame(bg: Color = C.Bg, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(bg), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 960.dp).fillMaxHeight().padding(horizontal = 24.dp, vertical = 16.dp), content = content)
    }
}

/** 위 머리줄: 로고 · 앱 이름 · 오른쪽 "시연 모드" 표시(누르면 시연 패널). */
@Composable
fun AppHeader(onDemo: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.logo_symbol), contentDescription = "플레오스 가드 로고", modifier = Modifier.size(58.dp))
        Spacer(Modifier.width(20.dp))
        Text("플레오스 가드", style = ts(30.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
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

enum class Tab(val label: String, val icon: Gi, val color: Color, val on: Color) {
    WARNING("경고등 찾기", Gi.Engine, C.Amber, C.OnAmber),
    RECORD("증상 기록", Gi.History, C.Mint, C.OnMint),
    GUARDIAN("보호자 알림", Gi.Users, C.Purple, C.OnPurple),
    SERVICE("정비소", Gi.Wrench, C.Amber, C.OnAmber),
}

@Composable
fun TabBar(selected: Tab?, onTab: (Tab) -> Unit) {
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
fun TestBadge(text: String = "시험 입력") {
    Box(Modifier.clip(Pill).background(C.TestBg).border(1.5.dp, C.Amber, Pill).padding(horizontal = 12.dp, vertical = 3.dp)) {
        Text(text, style = ts(22.sp, C.Amber, FontWeight.SemiBold), maxLines = 1)
    }
}

@Composable
fun MockBadge(text: String = "모의") {
    Box(Modifier.clip(Pill).border(1.5.dp, C.Purple, Pill).padding(horizontal = 12.dp, vertical = 3.dp)) {
        Text(text, style = ts(22.sp, C.Purple, FontWeight.SemiBold), maxLines = 1)
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, bg: Color = C.Surf, pad: Dp = 24.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().clip(R24).background(bg).padding(pad), content = content)
}

/** 아래 고정 버튼. 왼쪽 = 끝내기·취소·기록(중립색), 오른쪽 = 도움·보내기·감속 실패(빨강) 또는 그 화면의 주 행동 색(UX 4.4절). */
enum class RightKind { HELP_DARK, EMERGENCY, MINT, AMBER, PURPLE }

data class Btn(val label: String, val icon: Gi, val sub: String? = null, val onClick: () -> Unit)

@Composable
fun BottomButtons(left: Btn, right: Btn, kind: RightKind, emergency: Boolean = false, tall: Boolean = false, guardKey: Any? = null) {
    // 화면이 바뀐 직후 0.5초는 터치를 받지 않습니다(rules-lab input_guard_ms_after_emergency_transition).
    var guarded by remember(guardKey) { mutableStateOf(guardKey != null) }
    if (guardKey != null) LaunchedEffect(guardKey) { delay(RulesLab.INPUT_GUARD_MS); guarded = false }
    val h = if (tall) 148.dp else 96.dp
    val (rbg, rfg) = when (kind) {
        RightKind.HELP_DARK -> C.Help to C.Fg
        RightKind.EMERGENCY -> C.Red to C.Dark
        RightKind.MINT -> C.Mint to C.Dark
        RightKind.AMBER -> C.Amber to C.Dark
        RightKind.PURPLE -> C.Purple to C.Dark
    }
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        BigBtn(left, Modifier.weight(1f).height(h), if (emergency) C.ENeu else C.Surf2, C.Fg,
            iconTint = if (emergency || left.icon == Gi.CircleCheck) C.Fg else C.Mint, enabled = !guarded)
        BigBtn(right, Modifier.weight(1f).height(h), rbg, rfg, iconTint = rfg, enabled = !guarded)
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
        Column {
            Text(b.label, style = ts(if (b.sub != null) 30.sp else 30.sp, fg, FontWeight.SemiBold), maxLines = 2)
            if (b.sub != null) Text(b.sub, style = ts(22.sp, fg, FontWeight.Medium), maxLines = 1)
        }
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
