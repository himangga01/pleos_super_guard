package app.pleossupercare.phone.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pleossupercare.phone.R

val R20 = RoundedCornerShape(20.dp)
val R16 = RoundedCornerShape(16.dp)
val R12 = RoundedCornerShape(12.dp)
val Pill = RoundedCornerShape(50)

/** 화면 바탕: 어두운 바탕 위에 세로 한 줄. 폰은 폭 600dp까지만 쓰고 가운데에 둡니다(접는 폰 · 태블릿). */
@Composable
fun PhoneFrame(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(C.Bg), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 600.dp).fillMaxSize().padding(horizontal = 16.dp, vertical = 10.dp), content = content)
    }
}

/** 위 머리줄: 뒤로(또는 로고) · 이름 · 오른쪽 내용. 뒤로 단추는 56dp 터치 영역입니다. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Box(
                Modifier.size(56.dp).clip(Pill).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = "뒤로" },
                contentAlignment = Alignment.Center,
            ) { GIcon(Gi.ArrowLeft, C.Fg, 28.dp) }
            Spacer(Modifier.width(4.dp))
        } else {
            Image(painterResource(R.drawable.logo_symbol), contentDescription = "플레오스 슈퍼 케어 로고", modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
        }
        // 앱 이름처럼 긴 제목은 한 줄에 다 들어가게 조금 작게 씁니다(예: "플레오스 슈퍼 케어 모바일").
        Text(title, style = if (title.length > 11) Title.copy(fontSize = 19.sp) else Title, modifier = Modifier.weight(1f), maxLines = 1)
        trailing()
    }
}

/**
 * 정직성 꼬리표(상대값 · 실험 기준 · 안내 예시 · 검토 전 · 모의 · 예시). 차 앱과 같이 회색 점선 테두리로 그립니다.
 * 노랑 · 빨강은 차의 주의 · 위험에만 씁니다.
 */
@Composable
fun HonestyBadge(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.drawBehind {
            drawRoundRect(C.Mut, cornerRadius = CornerRadius(size.height / 2),
                style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
        }.padding(horizontal = 10.dp, vertical = 3.dp)
    ) { Text(text, style = ts(13.sp, C.Mut, FontWeight.SemiBold), maxLines = 1) }
}

@Composable
fun Panel(modifier: Modifier = Modifier, bg: Color = C.Surf, pad: Dp = 16.dp, border: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(R20).background(bg).then(if (border != null) Modifier.border(1.5.dp, border, R20) else Modifier).padding(pad),
        content = content,
    )
}

data class Btn(val label: String, val icon: Gi, val onClick: () -> Unit, val enabled: Boolean = true)

/** 아래 단추 두 개. 왼쪽 = 다시 · 취소(중립색), 오른쪽 = 그 화면의 주 행동(색). 높이 64dp. */
@Composable
fun BottomButtons(left: Btn?, right: Btn, rightColor: Color = C.Mint, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (left != null) BigButton(left, Modifier.weight(1f), C.Surf2, C.Fg, C.Mint)
        BigButton(right, Modifier.weight(if (left != null) 1.25f else 1f), rightColor, C.Dark, C.Dark)
    }
}

/** 큰 단추. 글자는 단추 폭에 맞춰 19sp에서 13sp까지 줄입니다(잘리지 않게, ui/Tiles.kt의 fitSp). */
@Composable
fun BigButton(b: Btn, modifier: Modifier, bg: Color, fg: Color, iconTint: Color = fg, height: Dp = 64.dp) {
    val alpha = if (b.enabled) 1f else 0.4f
    BoxWithConstraints(
        modifier.height(height).clip(R20).background(bg.copy(alpha = if (b.enabled) 1f else 0.5f))
            .clickable(enabled = b.enabled, role = Role.Button, onClick = b.onClick),
        contentAlignment = Alignment.Center,
    ) {
        val avail = (maxWidth.value - 32f - 26f - 10f).coerceAtLeast(40f)
        val size = fitSp(b.label, avail, 19f, 13f, 19f)
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            GIcon(b.icon, iconTint.copy(alpha = alpha), 26.dp)
            Spacer(Modifier.width(10.dp))
            Text(b.label, style = ts(size, fg.copy(alpha = alpha), FontWeight.SemiBold), maxLines = 1)
        }
    }
}

/** 고르기 칩(놓은 곳 · 부위 · 종류). 높이 52dp, 고르면 민트 테두리. */
@Composable
fun SelChip(label: String, on: Boolean, modifier: Modifier = Modifier, icon: Gi? = null, dot: Color? = null, accent: Color = C.Mint, onClick: () -> Unit) {
    Row(
        modifier.heightIn(min = 52.dp).clip(R16).background(if (on) accent.copy(alpha = 0.16f) else C.Surf2)
            .border(if (on) 2.dp else 1.dp, if (on) accent else C.Line, R16)
            .clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (dot != null) { Box(Modifier.size(18.dp).clip(Pill).background(dot)); Spacer(Modifier.width(8.dp)) }
        if (icon != null) { GIcon(icon, if (on) accent else C.Mut, 22.dp); Spacer(Modifier.width(8.dp)) }
        Text(label, style = ts(16.sp, if (on) C.Fg else C.Mut, if (on) FontWeight.SemiBold else FontWeight.Medium), maxLines = 1)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = Head, modifier = Modifier.weight(1f))
        trailing()
    }
}

/** 값 칸: 이름(작게) + 값(크게) + 단위. 값과 단위가 칸 폭에 들어가게 글자 크기를 칸에 맞춥니다(24sp → 14sp). */
@Composable
fun MetricCell(label: String, value: String, modifier: Modifier = Modifier, unit: String? = null, color: Color = C.Fg) {
    BoxWithConstraints(modifier.clip(R16).background(C.Surf2).padding(horizontal = 12.dp, vertical = 10.dp)) {
        val w = maxWidth.value
        val unitEm = if (unit != null) emWidth(unit) * 0.58f + 0.25f else 0f
        val valueSize = (w / (emWidth(value) + unitEm) * 0.92f).coerceIn(14f, 24f)
        val unitSize = (valueSize * 0.58f).coerceAtLeast(11f)
        Column {
            Text(label, style = ts(fitSp(label, w, 14f, 11f, 14f), C.Mut, FontWeight.Medium), maxLines = 1)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = ts(valueSize.sp, color, FontWeight.Bold), maxLines = 1)
                if (unit != null) {
                    Spacer(Modifier.width(3.dp))
                    Text(unit, style = ts(unitSize.sp, C.Mut, FontWeight.Medium), maxLines = 1, modifier = Modifier.padding(bottom = (valueSize * 0.12f).dp))
                }
            }
        }
    }
}

/** 큰 안내 한 줄: 아이콘 + 짧은 문장. 기록 전 "어디에 두세요" 안내에 씁니다. */
@Composable
fun GuideCard(icon: Gi, tint: Color, text: String, sub: String? = null) {
    Panel(bg = C.Surf, pad = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(Pill).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) { GIcon(icon, tint, 36.dp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(text, style = ts(19.sp, C.Fg, FontWeight.SemiBold, 26.sp))
                if (sub != null) Text(sub, style = Small, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** 화면 아래쪽에 잠깐 뜨는 한 줄 알림. */
@Composable
fun BoxScope.ToastHost(text: String?) {
    if (text == null) return
    Box(
        Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp, start = 24.dp, end = 24.dp).clip(R16).background(C.Surf2)
            .border(1.5.dp, C.Mint, R16).padding(horizontal = 18.dp, vertical = 12.dp)
    ) { Text(text, style = ts(16.sp, C.Fg, FontWeight.SemiBold), textAlign = TextAlign.Center) }
}

/** 연결 상태 칩(머리줄 오른쪽). */
@Composable
fun ConnChip(ok: Boolean?, label: String, onClick: () -> Unit) {
    val color = when (ok) { true -> C.Mint; false -> C.Amber; null -> C.Dim }
    Row(
        Modifier.height(40.dp).clip(Pill).background(C.Surf).border(1.dp, C.Line, Pill).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(Pill).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, style = ts(14.sp, C.Fg, FontWeight.SemiBold), maxLines = 1)
    }
}
