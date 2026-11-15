package app.pleossupercare.phone.ui

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
 * 칸 크기에 비례하는 아이콘 · 글자(차 앱 ui/Tiles.kt와 같은 규칙, 2026-11-14 사용자 요청).
 * 칸이 커지면 안의 아이콘과 글자도 같은 비율로 커지고, 글자는 칸 폭을 넘지 않게 줄입니다.
 * 폰은 차 화면보다 칸이 작으므로 최소 · 최대 크기만 폰에 맞게 낮췄습니다.
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
 * 타일 내용: 아이콘 + 이름(+ 보조 글). 칸 크기를 재서 크기를 정합니다.
 * - [row] = false: 아이콘 위 · 글자 아래(정사각에 가까운 칸). 아이콘 = min(높이 40%, 폭 42%), 이름 = 높이 13%, 보조 = 높이 8.5%.
 * - [row] = true: 아이콘 왼쪽 · 글자 오른쪽(낮고 넓은 칸). 아이콘 = min(높이 55%, 폭 22%), 이름 = 높이 24%, 보조 = 높이 17%.
 */
@Composable
fun TileContent(
    icon: Gi, tint: Color, title: String, sub: String? = null, row: Boolean = false,
    titleColor: Color = C.Fg, subColor: Color = C.Mut, trailing: Gi? = null,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight.value; val w = maxWidth.value
        val pad = (w * 0.06f).coerceIn(10f, 24f)
        if (row) {
            val ic = minOf(h * 0.55f, w * 0.22f).coerceIn(24f, 72f)
            val tr = if (trailing != null) (ic * 0.45f).coerceIn(18f, 32f) else 0f
            val gap = ic * 0.28f
            val textW = (w - pad * 2 - ic - gap - tr - (if (trailing != null) 8f else 0f)).coerceAtLeast(40f)
            val t1 = fitSp(title, textW, h * 0.24f, 16f, 28f)
            val t2 = sub?.let { fitSp(it, textW, h * 0.17f, 12f, 20f) }
            Row(Modifier.fillMaxSize().padding(horizontal = pad.dp), verticalAlignment = Alignment.CenterVertically) {
                GIcon(icon, tint, ic.dp)
                Spacer(Modifier.width(gap.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = ts(t1, titleColor, FontWeight.SemiBold), maxLines = 1)
                    if (sub != null && t2 != null) Text(sub, style = ts(t2, subColor, FontWeight.Medium), maxLines = 1)
                }
                if (trailing != null) { Spacer(Modifier.width(8.dp)); GIcon(trailing, C.Dim, tr.dp) }
            }
        } else {
            val ic = minOf(h * 0.40f, w * 0.42f).coerceIn(28f, 110f)
            val textW = (w - pad * 2).coerceAtLeast(40f)
            val t1 = fitSp(title, textW, h * 0.13f, 16f, 30f)
            val t2 = sub?.let { fitSp(it, textW, h * 0.085f, 12f, 20f) }
            Column(
                Modifier.fillMaxSize().padding(horizontal = pad.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                GIcon(icon, tint, ic.dp)
                Spacer(Modifier.height((h * 0.06f).coerceIn(6f, 18f).dp))
                Text(title, style = ts(t1, titleColor, FontWeight.SemiBold), maxLines = 1, textAlign = TextAlign.Center)
                if (sub != null && t2 != null) {
                    Spacer(Modifier.height((h * 0.015f).coerceIn(2f, 6f).dp))
                    Text(sub, style = ts(t2, subColor, FontWeight.Medium), maxLines = 1, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
