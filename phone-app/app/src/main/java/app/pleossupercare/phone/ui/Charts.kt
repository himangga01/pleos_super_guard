package app.pleossupercare.phone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 막대 그래프(소리 · 진동 스펙트럼). [values]는 칸마다 값, [lo]..[hi] 범위로 그립니다. [mark] 칸은 밝게 칠합니다. */
@Composable
fun BarChart(
    values: List<Float>, lo: Float, hi: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 120.dp,
    mark: Int? = null, leftLabel: String? = null, rightLabel: String? = null, desc: String = "그래프",
) {
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height).clip(R12).background(C.Surf2).semantics { contentDescription = desc }) {
            val n = values.size.coerceAtLeast(1)
            val gap = 1.5.dp.toPx()
            val bw = (size.width - gap * (n + 1)) / n
            // 가로 눈금 3줄
            for (k in 1..3) {
                val y = size.height * k / 4f
                drawLine(C.Line, Offset(0f, y), Offset(size.width, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            values.forEachIndexed { i, v ->
                val t = ((v - lo) / (hi - lo)).coerceIn(0f, 1f)
                val h = (size.height - 8f) * t
                val x = gap + i * (bw + gap)
                drawRoundRect(if (i == mark) color else color.copy(alpha = 0.55f), Offset(x, size.height - h), Size(bw, h), CornerRadius(2f, 2f))
            }
        }
        if (leftLabel != null || rightLabel != null) AxisLabels(leftLabel ?: "", rightLabel ?: "")
    }
}

/** 선 그래프(시간에 따른 소리 크기 · 진동 크기). */
@Composable
fun LineChart(
    values: List<Float>, lo: Float, hi: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 110.dp,
    leftLabel: String? = null, rightLabel: String? = null, fill: Boolean = true, desc: String = "그래프",
) {
    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(height).clip(R12).background(C.Surf2).semantics { contentDescription = desc }) {
            for (k in 1..3) {
                val y = size.height * k / 4f
                drawLine(C.Line, Offset(0f, y), Offset(size.width, y), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
            }
            if (values.size >= 2) {
                val p = Path()
                val stepX = size.width / (values.size - 1)
                values.forEachIndexed { i, v ->
                    val t = ((v - lo) / (hi - lo)).coerceIn(0f, 1f)
                    val x = i * stepX; val y = size.height - 4f - (size.height - 8f) * t
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                if (fill) {
                    val area = Path().apply { addPath(p); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                    drawPath(area, color.copy(alpha = 0.16f))
                }
                drawPath(p, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
        if (leftLabel != null || rightLabel != null) AxisLabels(leftLabel ?: "", rightLabel ?: "")
    }
}

@Composable
private fun AxisLabels(left: String, right: String) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(left, style = ts(12.sp, C.Dim), modifier = Modifier.weight(1f))
        Text(right, style = ts(12.sp, C.Dim))
    }
}

/** 지금 크기 막대(기록 중 화면). [level] 0..1. */
@Composable
fun LevelMeter(level: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(22.dp).clip(Pill).background(C.Surf2)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(level.coerceIn(0.02f, 1f)).clip(Pill).background(color))
    }
}
