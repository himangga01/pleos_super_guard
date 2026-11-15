package app.pleossupercare.phone.screens

import android.Manifest
import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pleossupercare.phone.Controller
import app.pleossupercare.phone.data.Kind
import app.pleossupercare.phone.data.Prefs
import app.pleossupercare.phone.data.ReqCtx
import app.pleossupercare.phone.logic.Blob
import app.pleossupercare.phone.logic.ColorPick
import app.pleossupercare.phone.logic.LightAnalyzer
import app.pleossupercare.phone.logic.WarnLevel
import app.pleossupercare.phone.logic.WarnResult
import app.pleossupercare.phone.logic.WarningCatalog
import app.pleossupercare.phone.sensing.ImageUtil
import app.pleossupercare.phone.ui.BottomButtons
import app.pleossupercare.phone.ui.Btn
import app.pleossupercare.phone.ui.C
import app.pleossupercare.phone.ui.CameraHandle
import app.pleossupercare.phone.ui.CameraPreview
import app.pleossupercare.phone.ui.GIcon
import app.pleossupercare.phone.ui.Gi
import app.pleossupercare.phone.ui.Panel
import app.pleossupercare.phone.ui.PhoneFrame
import app.pleossupercare.phone.ui.R16
import app.pleossupercare.phone.ui.R20
import app.pleossupercare.phone.ui.SectionLabel
import app.pleossupercare.phone.ui.SelChip
import app.pleossupercare.phone.ui.Small
import app.pleossupercare.phone.ui.Title
import app.pleossupercare.phone.ui.TopBar
import app.pleossupercare.phone.ui.ts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

private enum class WarnPhase { CAMERA, TAP, PICK, RESULT }

/** 경고등 색 이름 → 점 색(차 앱 경고등 찾기와 같은 값). */
fun warnDot(color: String?): Color = when (color) {
    "빨강" -> C.WarnRed; "주황" -> C.WarnOrange; "초록" -> C.WarnGreen; "파랑" -> C.WarnBlue; else -> C.Mut
}

/** 경고등 모양 이름 → 기호(차 앱과 같은 짝). */
fun shapeIcon(shape: String): Gi = WarningCatalog.SHAPES.zip(listOf(Gi.Engine, Gi.Oil, Gi.Brake, Gi.Battery, Gi.Tire, Gi.Belt, Gi.Coolant, Gi.Airbag))
    .firstOrNull { it.first == shape }?.second ?: Gi.Engine

private fun levelStyle(l: WarnLevel): Triple<Color, Color, Gi> = when (l) {
    WarnLevel.STOP -> Triple(C.ECard, C.Red, Gi.OctagonAlert)
    WarnLevel.URGENT -> Triple(C.ECard, C.Red, Gi.TriangleAlert)
    WarnLevel.SERVICE -> Triple(C.OnAmber, C.Amber, Gi.Wrench)
    WarnLevel.INFO -> Triple(C.OnMint, C.Mint, Gi.CircleCheck)
}

/**
 * 경고등 찍기(폰 센서 조사 6.2절): 찍기 → 사진 속 불빛 누르기 → 색(폰이 고르고 확인) · 켜짐(묻기) · 모양(운전자가 고르기)
 * → 도감 찾기(WarningCatalog.find, 차 앱과 같은 15개) → 뜻 → 잘라 낸 사진과 후보를 차로 보내기.
 */
@Composable
fun WarningScreen(c: Controller, ctx: ReqCtx?) = PhoneFrame {
    TopBar("경고등 찍기", onBack = { c.back() })
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val emulator = remember { Prefs.isEmulator() }
    val ask = rememberPermission(Manifest.permission.CAMERA)
    var granted by remember { mutableStateOf(hasPermission(context, Manifest.permission.CAMERA)) }
    var denied by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!granted) ask({ granted = true }, { denied = true }) }
    val handle = remember { CameraHandle() }

    var phase by remember { mutableStateOf(WarnPhase.CAMERA) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var pixels by remember { mutableStateOf<IntArray?>(null) }
    var blobs by remember { mutableStateOf<List<Blob>>(emptyList()) }
    var pick by remember { mutableStateOf<ColorPick?>(null) }
    var crop by remember { mutableStateOf<Bitmap?>(null) }
    var color by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<WarnResult?>(null) }
    var shape by remember { mutableStateOf<String?>(null) }
    var takenAt by remember { mutableLongStateOf(0L) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun accept(bmp: Bitmap?) {
        if (bmp == null) { error = "사진을 읽지 못했습니다"; busy = false; return }
        scope.launch {
            val px = withContext(Dispatchers.Default) { ImageUtil.pixels(bmp) }
            blobs = withContext(Dispatchers.Default) { LightAnalyzer.blobs(px, bmp.width, bmp.height) }
            photo = bmp; pixels = px; pick = null; crop = null; color = null; mode = null; result = null; shape = null
            takenAt = System.currentTimeMillis()
            busy = false; error = null
            phase = WarnPhase.TAP
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { busy = true; scope.launch { accept(withContext(Dispatchers.IO) { ImageUtil.decodeUri(context, uri, 1280) }) } }
    }
    fun onTap(x: Int, y: Int) {
        val bmp = photo ?: return; val px = pixels ?: return
        val p = LightAnalyzer.pickColor(px, bmp.width, bmp.height, x, y)
        pick = p; color = p.color; mode = null; result = null
        crop = ImageUtil.crop(bmp, LightAnalyzer.displayBox(bmp.width, bmp.height, x, y, blobs))
        phase = WarnPhase.PICK
    }
    fun send() {
        val res = result ?: return; val col = color ?: return; val md = mode ?: return; val sh = shape ?: return
        val cut = crop ?: return; val p = pick
        val jpeg = ImageUtil.jpeg(cut, 512, 85)
        val payload = c.basePayload(Kind.WARNING, takenAt, 0, "계기판", ctx?.partHint ?: "계기판", ctx, null, testInput = emulator)
        payload.getJSONObject("metrics").put("confidence", r2(p?.confidence ?: 0.0)).put("litPixels", p?.lit ?: 0)
            .put("colorAuto", if (p?.color != null && p.color == col) 1 else 0)
        payload.put("candidates", JSONArray(LightAnalyzer.candidates(col, sh, md)))
            .put("confirmed", res.primary.id).put("confirmedName", res.primary.name)
            .put("labels", JSONObject().put("detectedColor", p?.color ?: "고르지 못함").put("chosenColor", col).put("mode", md).put("shape", sh)
                .put("method", "색은 폰이 고르고 운전자가 확인 · 모양은 운전자가 고름"))
            .put("imageJpegBase64", Base64.encodeToString(jpeg, Base64.NO_WRAP))
        val summary = "경고등 사진 · $col · ${res.primary.name}(운전자가 고름)"
        payload.put("summary", summary)
        c.submit(Kind.WARNING, payload, summary, jpeg, ctx)
    }

    when (phase) {
        WarnPhase.CAMERA -> {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                RequestBanner(ctx)
                Text("계기판을 정면에서, 플래시 없이 찍으세요", style = ts(18.sp, C.Fg, FontWeight.SemiBold), modifier = Modifier.padding(bottom = 10.dp))
                if (granted) {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.8f).clip(R20).background(C.Surf)) {
                        CameraPreview(handle, Modifier.fillMaxSize())
                        Canvas(Modifier.fillMaxSize()) {
                            val w = size.width * 0.8f; val h = size.height * 0.45f
                            drawRoundRect(C.Amber, Offset((size.width - w) / 2, (size.height - h) / 2), Size(w, h),
                                androidx.compose.ui.geometry.CornerRadius(20f), style = Stroke(4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))))
                        }
                        handle.error?.let { Text(it, style = ts(16.sp, C.Amber), modifier = Modifier.align(Alignment.Center)) }
                    }
                } else if (denied) {
                    PermissionNotice("찍으려면 카메라 권한이 필요합니다 · 사진 불러오기는 권한 없이 됩니다") { ask({ granted = true; denied = false }, { denied = true }) }
                }
                Badges("색으로 후보를 좁힙니다", "모양은 확인해 주세요", "자동 인식 아님")
                error?.let { Note(it, C.Amber) }
            }
            BottomButtons(
                Btn("사진 불러오기", Gi.Image, { picker.launch("image/*") }, enabled = !busy),
                Btn(if (busy || handle.busy) "찍는 중" else "찍기", Gi.Camera, {
                    busy = true
                    handle.take(context, 1280) { bmp -> if (bmp == null) { busy = false; error = "찍지 못했습니다" } else accept(bmp) }
                }, enabled = granted && handle.ready && !busy),
                rightColor = C.Amber,
            )
        }
        WarnPhase.TAP -> {
            val bmp = photo!!
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text("사진에서 켜진 경고등을 눌러 주세요", style = ts(19.sp, C.Fg, FontWeight.SemiBold), modifier = Modifier.padding(bottom = 10.dp))
                TappablePhoto(bmp, blobs, null) { x, y -> onTap(x, y) }
                Note(if (blobs.isNotEmpty()) "밝고 진한 불빛 ${blobs.size}곳을 점선 동그라미로 표시했습니다 · 다른 곳을 눌러도 됩니다"
                    else "밝고 진한 불빛을 찾지 못했습니다 · 경고등 자리를 직접 눌러 주세요")
            }
            BottomButtons(null, Btn("다시 찍기", Gi.Camera, { phase = WarnPhase.CAMERA }), rightColor = C.Amber)
        }
        WarnPhase.PICK -> {
            val p = pick!!
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    crop?.let { Image(it.asImageBitmap(), contentDescription = "잘라 낸 불빛", contentScale = ContentScale.Crop,
                        modifier = Modifier.size(112.dp).clip(R16).border(2.dp, warnDot(color), R16)) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(LightAnalyzer.confidenceWord(p), style = ts(18.sp, C.Fg, FontWeight.SemiBold, 25.sp))
                        Text(if (p.color != null) "사진에서 ${p.color} 불빛을 찾았습니다. 아래 후보에서 같은 모양을 골라 주세요" else "색 · 켜짐 · 모양을 차례로 골라 주세요",
                            style = Small, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                SectionLabel("1  무슨 색인가요")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WarningCatalog.COLORS.forEach { col ->
                        SelChip(col, color == col, Modifier.weight(1f), dot = warnDot(col), accent = C.Amber) { color = col }
                    }
                }
                SectionLabel("2  켜져 있나요, 깜박이나요")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(WarningCatalog.STEADY to Gi.Steady, WarningCatalog.BLINK to Gi.Blink).forEach { (m, ic) ->
                        SelChip(m, mode == m, Modifier.weight(1f), icon = ic, accent = C.Amber) { mode = m }
                    }
                }
                val (shapes, narrowed) = LightAnalyzer.shapesFor(color)
                SectionLabel("3  어떤 모양인가요") {
                    if (color != null) Text(if (narrowed) "${color} 후보 ${shapes.size}개" else "모든 모양", style = Small)
                }
                if (color == "초록" || (color != null && !narrowed)) Note("${color} 표시등은 도감에 없습니다 · 기능이 켜졌다는 상태 표시일 수 있습니다", C.Mut)
                val ready = color != null && mode != null
                if (!ready) Note("색과 켜짐 방식을 먼저 골라 주세요", C.Amber)
                shapes.chunked(3).forEach { row ->
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { sh ->
                            Column(
                                Modifier.weight(1f).heightIn(min = 104.dp).clip(R16).background(C.Surf2)
                                    .border(1.dp, C.Line, R16).clickable(enabled = ready, role = Role.Button) {
                                        shape = sh; result = WarningCatalog.find(color!!, sh, mode!!); phase = WarnPhase.RESULT
                                    }.padding(8.dp).semantics { contentDescription = sh },
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                            ) {
                                GIcon(shapeIcon(sh), if (ready) warnDot(color) else C.Dim, 48.dp)
                                Spacer(Modifier.height(6.dp))
                                Text(sh, style = ts(15.sp, if (ready) C.Fg else C.Dim, FontWeight.Medium), textAlign = TextAlign.Center, maxLines = 2)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Badges("모양은 운전자가 고릅니다", "자동 인식 아님")
            }
            BottomButtons(null, Btn("다른 자리 누르기", Gi.Image, { phase = WarnPhase.TAP }), rightColor = C.Amber)
        }
        WarnPhase.RESULT -> {
            val res = result!!
            val e = res.primary
            val (bg, fg, ic) = levelStyle(e.level)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    crop?.let { Image(it.asImageBitmap(), contentDescription = "잘라 낸 불빛", contentScale = ContentScale.Crop,
                        modifier = Modifier.size(88.dp).clip(R16).border(2.dp, warnDot(color), R16)) }
                    Spacer(Modifier.width(12.dp))
                    GIcon(shapeIcon(e.shape), warnDot(e.color), 52.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.name, style = Title)
                        Text("${e.color} · ${e.modeLabel}", style = Small)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp).clip(R16).background(bg).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GIcon(ic, fg, 30.dp); Spacer(Modifier.width(12.dp))
                    Column {
                        Text(e.level.label, style = ts(15.sp, fg, FontWeight.SemiBold))
                        Text(e.judgement, style = ts(19.sp, C.Fg, FontWeight.Bold, 26.sp))
                    }
                }
                res.colorNote?.let { Warn(it) }
                res.modeNote?.let { Warn(it) }
                Panel(Modifier.padding(top = 12.dp)) {
                    Labeled("뜻", e.meaning); Labeled("지금 할 일", e.now); Labeled("다음에 할 일", e.next)
                }
                if (res.others.isNotEmpty()) Note("다른 후보: " + res.others.joinToString(" · ") { it.name })
                Badges("안내 예시 · 검토 전", "운전자가 고른 기호", "자동 인식 아님")
                Note("잘라 낸 사진과 후보를 차로 보냅니다 · 차 화면의 경고등 찾기에서 함께 봅니다")
            }
            BottomButtons(
                Btn("다른 모양", Gi.ArrowLeft, { phase = WarnPhase.PICK }),
                Btn("차로 보내기", Gi.Send, { send() }),
                rightColor = C.Amber,
            )
        }
    }
}

@Composable
private fun Labeled(label: String, text: String) {
    Text(label, style = ts(14.sp, C.Mut, FontWeight.SemiBold), modifier = Modifier.padding(top = 6.dp))
    Text(text, style = ts(17.sp, C.Fg, lineHeight = 25.sp))
}

/** 사진을 보여 주고 누른 자리를 사진 화소 좌표로 알려 줍니다. 불빛 힌트를 점선 동그라미로 그립니다. */
@Composable
private fun TappablePhoto(bmp: Bitmap, blobs: List<Blob>, roi: IntArray?, onTap: (Int, Int) -> Unit) {
    val ratio = bmp.width.toFloat() / bmp.height
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val w = minOf(maxWidth, 440.dp * ratio)
        Box(
            Modifier.width(w).aspectRatio(ratio).clip(R16).background(C.Surf)
                .pointerInput(bmp) { detectTapGestures { off -> onTap((off.x / size.width * bmp.width).toInt(), (off.y / size.height * bmp.height).toInt()) } }
                .semantics { contentDescription = "찍은 사진(경고등 자리를 누르세요)" },
        ) {
            Image(bmp.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
            Canvas(Modifier.fillMaxSize()) {
                val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                blobs.forEach { b ->
                    val r = max(b.r * max(size.width, size.height), 22.dp.toPx())
                    drawCircle(Color.White, r, Offset(b.cx * size.width, b.cy * size.height), style = Stroke(3.dp.toPx(), pathEffect = dash))
                }
                roi?.let {
                    val sx = size.width / bmp.width; val sy = size.height / bmp.height
                    drawRect(C.Amber, Offset(it[0] * sx, it[1] * sy), Size((it[2] - it[0]) * sx, (it[3] - it[1]) * sy), style = Stroke(3.dp.toPx()))
                }
            }
        }
    }
}
