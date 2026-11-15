package app.pleossupercare.phone.ui

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
 * 아이콘 하나에 뜻 하나. 차 앱(ui/GuardIcons.kt)과 같은 방식입니다.
 * - 직접 그린 기호(100×100 좌표): 경고등 기호 8개는 차 앱과 같은 선화이며 제조사 원본 그림이 아닙니다.
 * - Lucide 선 아이콘(ISC, assets/licenses/lucide-ISC.txt): 24×24 좌표, 선 굵기 2.
 */
enum class Gi {
    // 직접 그린 기호(차 앱과 같은 좌표)
    Engine, Oil, Brake, Battery, Tire, Belt, Airbag, Coolant, Steady, Blink, Smartphone, Wheel, Seat, Door, CarRear,
    Park, Info, Close, ChevronRight, Search, Home, Washer,
    // Lucide
    History, Wrench, Mic, Send, OctagonAlert, CircleHelp, CircleCheck, Volume, CarFront, MapPin, Clock, ArrowLeft,
    AudioLines, Activity, TriangleAlert, ShieldCheck, Camera, ScanLine, CircleStop, Image, Receipt, Settings, Refresh,
    Check, X, QrCode, Link, Trash, Bell, Vibrate, Droplet, Play, Package, WifiOff, Pencil,
}

private object LucidePaths {
    val activity = listOf("M22 12h-2.48a2 2 0 0 0-1.93 1.46l-2.35 8.36a.25.25 0 0 1-.48 0L9.24 2.18a.25.25 0 0 0-.48 0l-2.35 8.36A2 2 0 0 1 4.49 12H2")
    val arrowLeft = listOf("m12 19-7-7 7-7", "M19 12H5")
    val audioLines = listOf("M2 10v3", "M6 6v11", "M10 3v18", "M14 8v7", "M18 5v13", "M22 10v3")
    val camera = listOf("M13.997 4a2 2 0 0 1 1.76 1.05l.486.9A2 2 0 0 0 18.003 7H20a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h1.997a2 2 0 0 0 1.759-1.048l.489-.904A2 2 0 0 1 10.004 4z", "M9.0 13.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0")
    val carFront = listOf("m21 8-2 2-1.5-3.7A2 2 0 0 0 15.646 5H8.4a2 2 0 0 0-1.903 1.257L5 10 3 8", "M7 14h.01", "M17 14h.01", "M5.0 10.0h14.0a2.0 2.0 0 0 1 2.0 2.0v4.0a2.0 2.0 0 0 1 -2.0 2.0h-14.0a2.0 2.0 0 0 1 -2.0 -2.0v-4.0a2.0 2.0 0 0 1 2.0 -2.0Z", "M5 18v2", "M19 18v2")
    val circleCheck = listOf("M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0", "m16 9-5.5 5.5L8 12")
    val circleHelp = listOf("M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0", "M9.1 9a3 3 0 0 1 5.8 1c0 2-3 2-3 4m.1 3h.01")
    val circleStop = listOf("M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0", "M10.0 9.0h4.0a1.0 1.0 0 0 1 1.0 1.0v4.0a1.0 1.0 0 0 1 -1.0 1.0h-4.0a1.0 1.0 0 0 1 -1.0 -1.0v-4.0a1.0 1.0 0 0 1 1.0 -1.0Z")
    val clock3 = listOf("M2.0 12.0a10.0 10.0 0 1 0 20.0 0a10.0 10.0 0 1 0 -20.0 0", "M12 6v6h4")
    val history = listOf("M3 11a9 9 0 1 1 2.8 7M3 4v7h7m2-4v6l4 2")
    val mapPin = listOf("M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0", "M9.0 10.0a3.0 3.0 0 1 0 6.0 0a3.0 3.0 0 1 0 -6.0 0")
    val mic = listOf("M12 19v3", "M19 10v2a7 7 0 0 1-14 0v-2", "M12.0 2.0h0.0a3.0 3.0 0 0 1 3.0 3.0v7.0a3.0 3.0 0 0 1 -3.0 3.0h-0.0a3.0 3.0 0 0 1 -3.0 -3.0v-7.0a3.0 3.0 0 0 1 3.0 -3.0Z")
    val octagonAlert = listOf("m8 2-6 6v8l6 6h8l6-6V8l-6-6Z", "M12 7v6m0 4h.01")
    val scanLine = listOf("M3 7V5a2 2 0 0 1 2-2h2", "M17 3h2a2 2 0 0 1 2 2v2", "M21 17v2a2 2 0 0 1-2 2h-2", "M7 21H5a2 2 0 0 1-2-2v-2", "M7 12h10")
    val send = listOf("M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z", "m21.854 2.147-10.94 10.939")
    val shieldCheck = listOf("M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z", "m9 12 2 2 4-4")
    val triangleAlert = listOf("M10.3 3.9 1.8 18.5A2 2 0 0 0 3.5 21h17a2 2 0 0 0 1.7-2.5L13.7 3.9a2 2 0 0 0-3.4 0Z", "M12 9v4m0 4h.01")
    val volume2 = listOf("M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z", "M16 9a5 5 0 0 1 0 6", "M19.364 18.364a9 9 0 0 0 0-12.728")
    val wrench = listOf("M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.106-3.105c.32-.322.863-.22.983.218a6 6 0 0 1-8.259 7.057l-7.91 7.91a1 1 0 0 1-2.999-3l7.91-7.91a6 6 0 0 1 7.057-8.259c.438.12.54.662.219.984z")
    // 폰 앱에서 더한 Lucide 선 아이콘
    val image = listOf("M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z", "M7 9a2 2 0 1 0 4 0a2 2 0 1 0-4 0", "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21")
    val receipt = listOf("M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1 2 1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1Z", "M14 8H8", "M16 12H8", "M13 16H8") // receipt-text
    val settings2 = listOf("M20 7h-9", "M14 17H5", "M14 17a3 3 0 1 0 6 0a3 3 0 1 0-6 0", "M4 7a3 3 0 1 0 6 0a3 3 0 1 0-6 0")
    val refreshCw = listOf("M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8", "M21 3v5h-5", "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16", "M8 16H3v5")
    val check = listOf("M20 6 9 17l-5-5")
    val x = listOf("M18 6 6 18", "m6 6 12 12")
    val qrCode = listOf("M4 3h3a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Z", "M17 3h3a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1h-3a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Z",
        "M4 16h3a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-3a1 1 0 0 1 1-1Z", "M21 16h-3a2 2 0 0 0-2 2v3", "M21 21v.01", "M12 7v3a2 2 0 0 1-2 2H7",
        "M3 12h.01", "M12 3h.01", "M12 16v.01", "M16 12h1", "M21 12v.01", "M12 21v-1")
    val link = listOf("M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71", "M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71")
    val trash2 = listOf("M3 6h18", "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6", "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2", "M10 11v6", "M14 11v6")
    val bell = listOf("M10.268 21a2 2 0 0 0 3.464 0", "M3.262 15.326A1 1 0 0 0 4 17h16a1 1 0 0 0 .74-1.673C19.41 13.956 18 12.499 18 8A6 6 0 0 0 6 8c0 4.499-1.411 5.956-2.738 7.326")
    val vibrate = listOf("m2 8 2 2-2 2 2 2-2 2", "m22 8-2 2 2 2-2 2 2 2", "M9 5h6a1 1 0 0 1 1 1v12a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1Z")
    val droplet = listOf("M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C6 11.1 5 13 5 15a7 7 0 0 0 7 7z")
    val play = listOf("M6 3l14 9-14 9V3z")
    val packageBox = listOf("M11 21.73a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73z",
        "M12 22V12", "m3.3 7 7.703 4.734a2 2 0 0 0 1.994 0L20.7 7", "m7.5 4.27 9 5.15")
    val wifiOff = listOf("M12 20h.01", "M8.5 16.429a5 5 0 0 1 7 0", "M5 12.859a10 10 0 0 1 5.17-2.69", "M19 12.859a10 10 0 0 0-2.007-1.523",
        "M2 8.82a15 15 0 0 1 4.177-2.643", "M22 8.82a15 15 0 0 0-11.288-3.764", "m2 2 20 20")
    val pencil = listOf("M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z", "m15 5 4 4")
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
    Gi.MapPin -> LucidePaths.mapPin
    Gi.Clock -> LucidePaths.clock3
    Gi.ArrowLeft -> LucidePaths.arrowLeft
    Gi.AudioLines -> LucidePaths.audioLines
    Gi.Activity -> LucidePaths.activity
    Gi.TriangleAlert -> LucidePaths.triangleAlert
    Gi.ShieldCheck -> LucidePaths.shieldCheck
    Gi.Camera -> LucidePaths.camera
    Gi.ScanLine -> LucidePaths.scanLine
    Gi.CircleStop -> LucidePaths.circleStop
    Gi.Image -> LucidePaths.image
    Gi.Receipt -> LucidePaths.receipt
    Gi.Settings -> LucidePaths.settings2
    Gi.Refresh -> LucidePaths.refreshCw
    Gi.Check -> LucidePaths.check
    Gi.X -> LucidePaths.x
    Gi.QrCode -> LucidePaths.qrCode
    Gi.Link -> LucidePaths.link
    Gi.Trash -> LucidePaths.trash2
    Gi.Bell -> LucidePaths.bell
    Gi.Vibrate -> LucidePaths.vibrate
    Gi.Droplet -> LucidePaths.droplet
    Gi.Play -> LucidePaths.play
    Gi.Package -> LucidePaths.packageBox
    Gi.WifiOff -> LucidePaths.wifiOff
    Gi.Pencil -> LucidePaths.pencil
    else -> null
}

@Composable
fun GIcon(icon: Gi, tint: Color, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    val paths = remember(icon) { lucide(icon)?.map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier.size(size)) {
        if (paths != null) {
            val k = this.size.minDimension / 24f
            withTransform({ scale(k, k, pivot = Offset.Zero) }) {
                val st = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                paths.forEach { drawPath(it, tint, style = st) }
            }
        } else {
            val k = this.size.minDimension / 100f
            withTransform({ scale(k, k, pivot = Offset.Zero) }) { G(this, tint).draw(icon) }
        }
    }
}

/** 차 앱 GuardIcons.kt의 G 클래스(시안 생성기 build_ui_v4.py의 100×100 좌표, 기본 선 굵기 7.5)를 옮긴 것입니다. */
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
            Gi.Steady -> { E(26, 26, 74, 74, fill = true); E(8, 8, 92, 92, w = 5f) }
            Gi.Blink -> {
                E(32, 32, 68, 68, fill = true)
                for (a in 0 until 360 step 45) { val co = cos(rad(a.toDouble())); val s = sin(rad(a.toDouble())); L((50 + 34 * co) to (50 + 34 * s), (50 + 46 * co) to (50 + 46 * s), w = 6f) }
            }
            Gi.Smartphone -> { RR(24, 4, 76, 96, 12f); L(42 to 82, 58 to 82) }
            Gi.Wheel -> {
                E(8, 8, 92, 92, w = 10f); E(24, 24, 76, 76, w = 5f); DOT(50, 50, 6)
                for (a in listOf(-90.0, -18.0, 54.0, 126.0, 198.0)) { val co = cos(rad(a)); val s = sin(rad(a)); L((50 + 8 * co) to (50 + 8 * s), (50 + 24 * co) to (50 + 24 * s), w = 5f) }
            }
            Gi.Seat -> { L(listOf(28 to 10, 42 to 10, 48 to 58, 34 to 58), w = 6.5f, close = true); L(listOf(34 to 58, 80 to 58, 80 to 72, 36 to 72), w = 6.5f, close = true); L(60 to 72, 60 to 90); L(44 to 90, 76 to 90) }
            Gi.Door -> { L(listOf(18 to 46, 42 to 12, 84 to 12, 84 to 88, 18 to 88), close = true); L(18 to 46, 84 to 46, w = 6f); L(62 to 60, 74 to 60) }
            Gi.CarRear -> {
                L(listOf(10 to 72, 10 to 52, 20 to 44, 28 to 22, 72 to 22, 80 to 44, 90 to 52, 90 to 72), close = true)
                L(listOf(33 to 30, 67 to 30, 71 to 42, 29 to 42), w = 6f, close = true)
                L(20 to 58, 38 to 58, w = 6f); L(62 to 58, 80 to 58, w = 6f)
                L(18 to 74, 18 to 88, 30 to 88, 30 to 74, w = 6.5f); L(70 to 74, 70 to 88, 82 to 88, 82 to 74, w = 6.5f)
            }
            Gi.Park -> { RR(8, 8, 92, 92, 18f); L(38 to 74, 38 to 28, 54 to 28); A(36, 28, 68, 56, -90f, 90f, w = 7.5f); L(38 to 52, 54 to 52) }
            Gi.Info -> { E(8, 8, 92, 92); L(50 to 44, 50 to 72, w = 8f); DOT(50, 29, 6) }
            Gi.Close -> { L(22 to 22, 78 to 78, w = 9f); L(78 to 22, 22 to 78, w = 9f) }
            Gi.ChevronRight -> L(36 to 16, 70 to 50, 36 to 84, w = 9f)
            Gi.Search -> { E(12, 12, 64, 64); L(57 to 57, 88 to 88) }
            Gi.Home -> { L(12 to 50, 50 to 16, 88 to 50); L(22 to 42, 22 to 86, 78 to 86, 78 to 42); L(42 to 86, 42 to 62, 58 to 62, 58 to 86) }
            Gi.Washer -> { L(listOf(8 to 14, 92 to 14, 82 to 58, 18 to 58), close = true); L(48 to 54, 28 to 26, w = 6f); tri(76 to 62, 67.5 to 80, 84.5 to 80); DOT(76, 82, 8.5) }
            else -> Unit
        }
    }
}
