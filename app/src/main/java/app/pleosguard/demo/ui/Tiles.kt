package app.pleosguard.demo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * 칸 크기에 비례하는 아이콘 · 글자(2026-11-14 사용자 요청: "버튼 여백이 많이 남는다, 아이콘 · 글자 크기를 비율에 맞춰라").
 * 넓은 창에서 칸이 커지면 안의 아이콘과 글자도 같은 비율로 커지고, 글자는 칸 폭을 넘지 않게 줄입니다.
 * 글자 배율 1(차량 화면 기본)에서 1sp ≈ 1dp로 봅니다.
 */

/** 글자열의 대략 폭(em). 한글 1.0, 공백 0.3, 그 밖(숫자 · 영문 · 기호) 0.62. */
fun emWidth(s: String): Float = s.sumOf { ch ->
    when {
        ch.code in 0xAC00..0xD7A3 || ch.code in 0x3130..0x318F -> 1.0
        ch == ' ' -> 0.3
        else -> 0.62
    }
}.toFloat().coerceAtLeast(1f)

/** 높이 비율로 원하는 크기([want])를 칸 폭([widthDp]) 안에 들어가게 줄인 뒤 [min]..[max]로 묶습니다. */
fun fitSp(text: String, widthDp: Float, want: Float, min: Float, max: Float): TextUnit {
    val longest = text.split('\n').maxOf { emWidth(it) }
    return minOf(want, widthDp / longest * 0.95f).coerceIn(min, max).sp
}

/**
 * 타일 내용: 아이콘 + 제목(+ 값). 칸 크기를 재서 크기를 정합니다.
 * - [row] = true: 아이콘 왼쪽 · 글자 오른쪽(낮고 넓은 칸). 아이콘 = min(높이 60%, 폭 25%), 제목 = 높이 22%, 값 = 높이 29%.
 * - [row] = false: 아이콘 위 · 글자 아래(정사각에 가까운 칸). 아이콘 = min(높이 38%, 폭 40%), 제목 = 높이 12%, 값 = 높이 15%.
 */
@Composable
fun TileContent(
    icon: Gi, tint: Color, title: String, value: String? = null, valueColor: Color = C.Fg,
    row: Boolean = true, titleColor: Color = C.Fg, trailing: Gi? = null,
    /** true면 제목이 주인공이고 값은 작은 도움말(값 = 제목의 80% 이하). 단추 이름 + 안내 한 줄에 씁니다. */
    hint: Boolean = false,
) {
    // 값에 줄바꿈이 있으면 두 줄까지 씁니다(가장 긴 줄로 크기를 맞춤).
    val valueLines = if (value != null && '\n' in value) 2 else 1
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight.value; val w = maxWidth.value
        val pad = (w * 0.05f).coerceIn(12f, 32f)
        if (row) {
            val ic = minOf(h * 0.60f, w * 0.25f).coerceIn(28f, 120f)
            val tr = if (trailing != null) (ic * 0.42f).coerceIn(22f, 48f) else 0f
            val gap = ic * 0.20f
            val textW = (w - pad * 2 - ic - gap - tr - (if (trailing != null) 8f else 0f)).coerceAtLeast(40f)
            val v0 = value?.let { fitSp(it, textW, h * (if (valueLines == 2) 0.22f else 0.29f), 20f, 56f) }
            // 이름만 있는 칸은 이름을 키우고(높이 34%), 값이 있으면 이름이 값보다 크지 않게(값의 85%) 합니다.
            // hint면 반대로 이름을 키우고(높이 30%) 값은 이름의 80% 이하로 둡니다.
            val t1 = when {
                v0 == null -> fitSp(title, textW, h * 0.34f, 20f, 44f)
                hint -> fitSp(title, textW, h * 0.30f, 20f, 44f)
                else -> minOf(fitSp(title, textW, h * 0.22f, 18f, 40f).value, maxOf(v0.value * 0.85f, 18f)).sp
            }
            val t2 = if (hint && v0 != null) minOf(v0.value, maxOf(t1.value * 0.8f, 20f)).sp else v0
            Row(Modifier.fillMaxSize().padding(horizontal = pad.dp), verticalAlignment = Alignment.CenterVertically) {
                GIcon(icon, tint, ic.dp)
                Spacer(Modifier.width(gap.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = ts(t1, titleColor, FontWeight.SemiBold), maxLines = 1)
                    if (value != null && t2 != null) Text(value, style = ts(t2, valueColor, if (hint) FontWeight.Medium else FontWeight.Bold, (t2.value * 1.25f).sp), maxLines = valueLines)
                }
                if (trailing != null) { Spacer(Modifier.width(8.dp)); GIcon(trailing, C.Dim, tr.dp) }
            }
        } else {
            val ic = minOf(h * 0.38f, w * 0.40f).coerceIn(30f, 150f)
            val textW = (w - pad * 2).coerceAtLeast(40f)
            val v0 = value?.let { fitSp(it, textW, h * 0.15f, 20f, 52f) }
            val t1 = when {
                v0 == null -> fitSp(title, textW, h * 0.15f, 20f, 46f)
                hint -> fitSp(title, textW, h * 0.14f, 20f, 46f)
                else -> minOf(fitSp(title, textW, h * 0.12f, 20f, 42f).value, maxOf(v0.value * 0.85f, 20f)).sp
            }
            val t2 = if (hint && v0 != null) minOf(v0.value, maxOf(t1.value * 0.8f, 20f)).sp else v0
            Column(Modifier.fillMaxSize().padding(horizontal = pad.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                GIcon(icon, tint, ic.dp)
                Spacer(Modifier.height((h * 0.05f).coerceIn(6f, 24f).dp))
                Text(title, style = ts(t1, titleColor, FontWeight.SemiBold), maxLines = 1, textAlign = TextAlign.Center)
                if (value != null && t2 != null) Text(value, style = ts(t2, valueColor, if (hint) FontWeight.Medium else FontWeight.Bold, (t2.value * 1.25f).sp), maxLines = valueLines, textAlign = TextAlign.Center)
            }
        }
    }
}

/**
 * 키 큰 단추 내용(주행 중 홈의 큰 단추 3개 등): 아이콘 · 이름(줄바꿈 가능) · 보조 글. 높이 비율로 키웁니다.
 * 아이콘 = min(높이 26%, 폭 45%), 이름 = 높이 7.5%, 보조 = 높이 4.2%.
 */
@Composable
fun TallContent(icon: Gi, tint: Color, label: String, sub: String?, subColor: Color = C.Mut) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight.value; val w = maxWidth.value
        val ic = minOf(h * 0.26f, w * 0.45f).coerceIn(56f, 180f)
        val t1 = fitSp(label, w * 0.86f, h * 0.075f, 28f, 56f)
        val t2 = sub?.let { fitSp(it, w * 0.86f, h * 0.042f, 20f, 34f) }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            GIcon(icon, tint, ic.dp)
            Spacer(Modifier.height((h * 0.05f).coerceIn(12f, 40f).dp))
            Text(label, style = ts(t1, C.Fg, FontWeight.SemiBold, (t1.value * 1.25f).sp), textAlign = TextAlign.Center)
            if (sub != null && t2 != null) {
                Spacer(Modifier.height((h * 0.025f).coerceIn(6f, 20f).dp))
                Text(sub, style = ts(t2, subColor, lineHeight = (t2.value * 1.3f).sp), textAlign = TextAlign.Center, maxLines = 3)
            }
        }
    }
}
