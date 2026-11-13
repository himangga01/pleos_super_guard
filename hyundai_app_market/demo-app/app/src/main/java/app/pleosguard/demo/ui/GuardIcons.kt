package app.pleosguard.demo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 아이콘 하나에 뜻 하나(UX 4.10절). 두 종류입니다.
 * - 직접 그린 기호: 시안 생성기 build_ui_v4.py의 100×100 좌표를 그대로 옮겼습니다. 경고등 기호 8개는
 *   일반 기호의 뜻만 살려 새로 그린 선화이며 제조사 원본 그림이 아닙니다.
 * - Lucide 선 아이콘(ISC): output/submission/assets/icons 폴더의 SVG를 변환한 [LucidePaths].
 */
enum class Gi {
    // 직접 그린 기호
    Engine, Oil, Brake, Battery, Tire, Belt, Airbag, Coolant, Home, Users, Lifebuoy, Rec, Steering, Pedal, Turn,
    Search, Lock, Door, Washer, Park, Steady, Blink, Crash, Seat, Wheel, Fan, Window, Wiper,
    // Lucide
    History, Wrench, Mic, Send, OctagonAlert, CircleHelp, CircleCheck, Volume, CarFront, Bluetooth, MapPin, MapPinOff,
    Navigation, CheckCheck, Message, Clock, ArrowLeft, AudioLines, Activity, Flame, Gauge, TriangleAlert, ShieldCheck, Phone,
}

private fun lucide(i: Gi): List<String>? = when (i) {
    Gi.History -> LucidePaths.history
    Gi.Wrench -> LucidePaths.wrench
    Gi.Mic -> LucidePaths.mic
    Gi.Send -> LucidePaths.send
    Gi.OctagonAlert -> LucidePaths.octagonAlert
    Gi.CircleHelp -> LucidePaths.circleHelp
    Gi.CircleCheck -> LucidePaths.circleCheck
    Gi.Volume -> LucidePaths.volume2
    Gi.CarFront -> LucidePaths.carFront
    Gi.Bluetooth -> LucidePaths.bluetooth
    Gi.MapPin, Gi.MapPinOff -> LucidePaths.mapPin
    Gi.Navigation -> LucidePaths.navigation
    Gi.CheckCheck -> LucidePaths.checkCheck
    Gi.Message -> LucidePaths.messageCircle
    Gi.Clock -> LucidePaths.clock3
    Gi.ArrowLeft -> LucidePaths.arrowLeft
    Gi.AudioLines -> LucidePaths.audioLines
    Gi.Activity -> LucidePaths.activity
    Gi.Flame -> LucidePaths.flame
    Gi.Gauge -> LucidePaths.circleGauge
    Gi.TriangleAlert -> LucidePaths.triangleAlert
    Gi.ShieldCheck -> LucidePaths.shieldCheck
    Gi.Phone -> LucidePaths.phoneCall
    else -> null
}

@Composable
fun GIcon(icon: Gi, tint: Color, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val paths = remember(icon) { lucide(icon)?.map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier.size(size)) {
        if (paths != null) {
            val k = this.size.minDimension / 24f
            withTransform({ scale(k, k, pivot = Offset.Zero) }) {
                val st = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                paths.forEach { drawPath(it, tint, style = st) }
                if (icon == Gi.MapPinOff) drawLine(tint, Offset(3f, 3f), Offset(21f, 21f), 2f, StrokeCap.Round)
            }
        } else {
            val k = this.size.minDimension / 100f
            withTransform({ scale(k, k, pivot = Offset.Zero) }) { G(this, tint).draw(icon) }
        }
    }
}

/** build_ui_v4.py의 G 클래스(100×100 좌표, 기본 선 굵기 7.5)를 옮긴 것입니다. */
private class G(val d: DrawScope, val c: Color) {
    fun L(vararg pts: Pair<Number, Number>, w: Float = 7.5f, close: Boolean = false) = L(pts.toList(), w, close)
    fun L(pts: List<Pair<Number, Number>>, w: Float = 7.5f, close: Boolean = false) {
        val p = Path()
        pts.forEachIndexed { i, (x, y) -> if (i == 0) p.moveTo(x.toFloat(), y.toFloat()) else p.lineTo(x.toFloat(), y.toFloat()) }
        if (close) p.close()
        d.drawPath(p, c, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    fun E(x0: Number, y0: Number, x1: Number, y1: Number, fill: Boolean = false, w: Float = 7.5f) {
        val a = x0.toFloat(); val b = y0.toFloat(); val cc = x1.toFloat(); val dd = y1.toFloat()
        if (fill) d.drawOval(c, Offset(a, b), Size(cc - a, dd - b))
        else d.drawOval(c, Offset(a + w / 2, b + w / 2), Size(cc - a - w, dd - b - w), style = Stroke(w))
    }

    fun A(x0: Number, y0: Number, x1: Number, y1: Number, a0: Float, a1: Float, w: Float = 7.5f) {
        val a = x0.toFloat(); val b = y0.toFloat(); val cc = x1.toFloat(); val dd = y1.toFloat()
        d.drawArc(c, a0, a1 - a0, false, Offset(a + w / 2, b + w / 2), Size(cc - a - w, dd - b - w), style = Stroke(w, cap = StrokeCap.Round))
    }

    fun RR(x0: Number, y0: Number, x1: Number, y1: Number, r: Float, w: Float = 7.5f) {
        val a = x0.toFloat(); val b = y0.toFloat(); val cc = x1.toFloat(); val dd = y1.toFloat()
        d.drawRoundRect(c, Offset(a + w / 2, b + w / 2), Size(cc - a - w, dd - b - w), CornerRadius(r), style = Stroke(w))
    }

    fun R(x0: Number, y0: Number, x1: Number, y1: Number) {
        d.drawRect(c, Offset(x0.toFloat(), y0.toFloat()), Size(x1.toFloat() - x0.toFloat(), y1.toFloat() - y0.toFloat()))
    }

    fun DOT(x: Number, y: Number, r: Number) = d.drawCircle(c, r.toFloat(), Offset(x.toFloat(), y.toFloat()))

    fun tri(vararg pts: Pair<Number, Number>) {
        val p = Path(); pts.forEachIndexed { i, (x, y) -> if (i == 0) p.moveTo(x.toFloat(), y.toFloat()) else p.lineTo(x.toFloat(), y.toFloat()) }
        p.close(); d.drawPath(p, c)
    }

    private fun rad(a: Double) = Math.toRadians(a)

    fun person(x: Int = 0) {
        E(30 + x, 8, 48 + x, 26, fill = true)
        L(listOf(36 + x to 34, 52 + x to 34, 58 + x to 62, 40 + x to 62), close = true)
        L(48 + x to 62, 74 + x to 62, 80 + x to 88)
    }

    fun draw(i: Gi) {
        when (i) {
            Gi.Engine -> {
                L(listOf(16 to 42, 24 to 42, 24 to 34, 34 to 34, 34 to 26, 64 to 26, 64 to 34, 72 to 34, 80 to 42, 88 to 42,
                    88 to 68, 80 to 68, 72 to 76, 30 to 76, 24 to 68, 16 to 68), close = true)
                L(8 to 46, 8 to 64); L(8 to 55, 16 to 55); L(42 to 18, 56 to 18); L(49 to 18, 49 to 26)
            }
            Gi.Oil -> {
                L(listOf(22 to 48, 56 to 48, 84 to 36, 84 to 42, 62 to 72, 22 to 72), close = true)
                L(22 to 54, 10 to 50, 10 to 62, 22 to 66); L(30 to 40, 48 to 40); L(39 to 40, 39 to 48)
                DOT(88, 60, 5); tri(88 to 50, 83.6 to 59, 92.4 to 59)
            }
            Gi.Brake -> { E(28, 28, 72, 72); A(8, 20, 44, 80, 125f, 235f); A(56, 20, 92, 80, -55f, 55f); L(50 to 38, 50 to 52); DOT(50, 61, 4.6) }
            Gi.Battery -> { RR(12, 32, 88, 78, 6f); R(24, 22, 38, 32); R(62, 22, 76, 32); L(24 to 55, 40 to 55); L(60 to 55, 76 to 55); L(68 to 47, 68 to 63) }
            Gi.Tire -> {
                L(28 to 12, 19 to 28, 15 to 45, 18 to 61, 27 to 74, 73 to 74, 82 to 61, 85 to 45, 81 to 28, 72 to 12)
                for (x in listOf(30, 43, 57, 70)) L(x to 84, x to 92, w = 6.5f)
                L(50 to 26, 50 to 46); DOT(50, 58, 4.6)
            }
            Gi.Belt -> { person(); L(64 to 30, 34 to 62, w = 6.5f) }
            Gi.Airbag -> { person(-14); E(56, 8, 92, 44) }
            Gi.Coolant -> {
                L(44 to 10, 44 to 54); E(35, 50, 53, 68, fill = true)
                for (y in listOf(20, 32, 44)) L(52 to y, 64 to y, w = 6f)
                for (yy in listOf(80, 92)) L((0..20).map { (10 + it * 4) to (yy + 3 * sin(it * 0.9)) }, w = 6f)
            }
            Gi.Home -> { L(12 to 50, 50 to 16, 88 to 50); L(22 to 42, 22 to 86, 78 to 86, 78 to 42); L(42 to 86, 42 to 62, 58 to 62, 58 to 86) }
            Gi.Users -> { E(22, 14, 48, 40); L(8 to 86, 8 to 74, 18 to 60, 52 to 60, 62 to 74, 62 to 86); A(56, 12, 80, 36, -110f, 110f); L(70 to 58, 84 to 58, 92 to 70, 92 to 86) }
            Gi.Lifebuoy -> {
                E(8, 8, 92, 92); E(32, 32, 68, 68)
                for (a in listOf(45.0, 135.0, 225.0, 315.0)) { val co = cos(rad(a)); val s = sin(rad(a)); L((50 + 20 * co) to (50 + 20 * s), (50 + 40 * co) to (50 + 40 * s)) }
            }
            Gi.Rec -> { E(10, 10, 90, 90); E(32, 32, 68, 68, fill = true) }
            Gi.Steering -> { E(8, 8, 92, 92); E(40, 40, 60, 60); L(10 to 50, 40 to 50); L(60 to 50, 90 to 50); L(50 to 60, 50 to 90) }
            Gi.Pedal -> { RR(26, 8, 74, 74, 10f); for (y in listOf(24, 40, 56)) L(38 to y, 62 to y, w = 6.5f); L(50 to 74, 50 to 92) }
            Gi.Turn -> {
                L(listOf(6 to 50, 26 to 30, 26 to 42, 44 to 42, 44 to 58, 26 to 58, 26 to 70), w = 6.5f, close = true)
                L(listOf(94 to 50, 74 to 30, 74 to 42, 56 to 42, 56 to 58, 74 to 58, 74 to 70), w = 6.5f, close = true)
            }
            Gi.Search -> { E(12, 12, 64, 64); L(57 to 57, 88 to 88) }
            Gi.Lock -> { A(30, 10, 70, 50, 180f, 360f); L(30 to 30, 30 to 44); L(70 to 30, 70 to 44); RR(18, 44, 82, 90, 8f); DOT(50, 64, 5); L(50 to 64, 50 to 76) }
            Gi.Door -> { L(listOf(18 to 46, 42 to 12, 84 to 12, 84 to 88, 18 to 88), close = true); L(18 to 46, 84 to 46, w = 6f); L(62 to 60, 74 to 60) }
            Gi.Washer -> { L(listOf(8 to 14, 92 to 14, 82 to 58, 18 to 58), close = true); L(48 to 54, 28 to 26, w = 6f); tri(76 to 62, 67.5 to 80, 84.5 to 80); DOT(76, 82, 8.5) }
            Gi.Park -> { RR(8, 8, 92, 92, 18f); L(38 to 74, 38 to 28, 54 to 28); A(36, 28, 68, 56, -90f, 90f, w = 7.5f); L(38 to 52, 54 to 52) }
            Gi.Steady -> { E(26, 26, 74, 74, fill = true); E(8, 8, 92, 92, w = 5f) }
            Gi.Blink -> {
                E(32, 32, 68, 68, fill = true)
                for (a in 0 until 360 step 45) { val co = cos(rad(a.toDouble())); val s = sin(rad(a.toDouble())); L((50 + 34 * co) to (50 + 34 * s), (50 + 46 * co) to (50 + 46 * s), w = 6f) }
            }
            Gi.Crash -> {
                val pts = (0 until 16).map { k -> val r = if (k % 2 == 0) 44.0 else 22.0; val a = rad(k * 22.5 - 90); (50 + r * cos(a)) to (50 + r * sin(a)) }
                L(pts, w = 6.5f, close = true)
            }
            Gi.Seat -> { L(listOf(28 to 10, 42 to 10, 48 to 58, 34 to 58), w = 6.5f, close = true); L(listOf(34 to 58, 80 to 58, 80 to 72, 36 to 72), w = 6.5f, close = true); L(60 to 72, 60 to 90); L(44 to 90, 76 to 90) }
            Gi.Wheel -> {
                E(8, 8, 92, 92, w = 10f); E(24, 24, 76, 76, w = 5f); DOT(50, 50, 6)
                for (a in listOf(-90.0, -18.0, 54.0, 126.0, 198.0)) { val co = cos(rad(a)); val s = sin(rad(a)); L((50 + 8 * co) to (50 + 8 * s), (50 + 24 * co) to (50 + 24 * s), w = 5f) }
            }
            Gi.Fan -> {
                DOT(50, 50, 8)
                for (a0 in listOf(0.0, 120.0, 240.0)) L((0..8).map { k -> val t = k / 8.0; val r = 12 + 30 * t; val a = rad(a0 + 70 * t); (50 + r * cos(a)) to (50 + r * sin(a)) }, w = 8f)
            }
            Gi.Window -> { L(listOf(10 to 84, 10 to 44, 34 to 16, 90 to 16, 90 to 84), w = 7f, close = true); L(14 to 56, 86 to 56, w = 6f) }
            Gi.Wiper -> { A(6, 24, 94, 112, 200f, 340f, w = 7f); L(50 to 90, 22 to 44, w = 7f); DOT(50, 90, 7) }
            else -> Unit
        }
    }
}
