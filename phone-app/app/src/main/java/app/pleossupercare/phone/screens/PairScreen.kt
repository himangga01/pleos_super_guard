package app.pleossupercare.phone.screens

import android.Manifest
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pleossupercare.phone.Controller
import app.pleossupercare.phone.PairUi
import app.pleossupercare.phone.Screen
import app.pleossupercare.phone.logic.PairLink
import app.pleossupercare.phone.ui.BigButton
import app.pleossupercare.phone.ui.BottomButtons
import app.pleossupercare.phone.ui.Btn
import app.pleossupercare.phone.ui.C
import app.pleossupercare.phone.ui.CameraHandle
import app.pleossupercare.phone.ui.CameraPreview
import app.pleossupercare.phone.ui.GIcon
import app.pleossupercare.phone.ui.Gi
import app.pleossupercare.phone.ui.GuideCard
import app.pleossupercare.phone.ui.Panel
import app.pleossupercare.phone.ui.PhoneFrame
import app.pleossupercare.phone.ui.Pill
import app.pleossupercare.phone.ui.QrAnalyzer
import app.pleossupercare.phone.ui.R16
import app.pleossupercare.phone.ui.R20
import app.pleossupercare.phone.ui.Small
import app.pleossupercare.phone.ui.TopBar
import app.pleossupercare.phone.ui.ts

/** 차와 연결: 차 화면의 6자리 번호(또는 QR) → 차 화면에서 [연결] 확인 기다림 → 홈. */
@Composable
fun PairScreen(c: Controller) = PhoneFrame {
    TopBar("차와 연결", onBack = if (c.canGoBack) ({ c.back() }) else null) {
        Box(Modifier.size(52.dp).clip(Pill).clickable(role = Role.Button) { c.go(Screen.Settings) }.semantics { contentDescription = "설정" },
            contentAlignment = Alignment.Center) { GIcon(Gi.Settings, C.Mut, 26.dp) }
    }
    val ui = c.pairUi
    if (ui is PairUi.Waiting) { WaitingForCar(c); return@PhoneFrame }

    var code by rememberSaveable { mutableStateOf("") }
    GuideCard(Gi.CarFront, C.Mint, "차 화면의 6자리 번호", "케어 홈 → 폰 연결에 보입니다")
    Spacer(Modifier.height(16.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(6) { i ->
            val ch = code.getOrNull(i)?.toString() ?: ""
            val focus = i == code.length
            Box(
                Modifier.weight(1f).height(64.dp).clip(R16).background(C.Surf)
                    .border(if (focus) 2.dp else 1.dp, if (focus) C.Mint else C.Line, R16),
                contentAlignment = Alignment.Center,
            ) { Text(ch, style = ts(30.sp, C.Fg, FontWeight.Bold)) }
        }
    }
    val status = when (ui) {
        PairUi.Busy -> "연결 중…" to C.Mut
        is PairUi.Error -> ui.message to C.Amber
        else -> "번호를 넣고 [연결]을 누르세요" to C.Dim
    }
    Text(status.first, style = ts(16.sp, status.second, FontWeight.Medium), modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = TextAlign.Center)
    Keypad(
        onDigit = { d -> if (code.length < 6) code += d; if (c.pairUi is PairUi.Error) c.pairUi = PairUi.Idle },
        onBack = { code = code.dropLast(1) },
        onClear = { code = "" },
    )
    Spacer(Modifier.weight(1f))
    BottomButtons(
        Btn("QR 찍기", Gi.QrCode, { c.go(Screen.QrScan) }),
        Btn("연결", Gi.Link, { c.claim(code) }, enabled = code.length == 6 && ui != PairUi.Busy),
    )
    Row(Modifier.fillMaxWidth().padding(top = 10.dp).clickable { c.go(Screen.Settings) }, verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.Link, C.Dim, 18.dp); Spacer(Modifier.width(6.dp))
        Text("서버 ${c.prefs.serverUrl}", style = Small, modifier = Modifier.weight(1f), maxLines = 1)
        Text("바꾸기", style = ts(15.sp, C.Mint, FontWeight.SemiBold))
    }
}

@Composable
private fun Keypad(onDigit: (String) -> Unit, onBack: () -> Unit, onClear: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("지움", "0", "←"))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    val desc = when (k) { "←" -> "한 칸 지우기"; "지움" -> "모두 지우기"; else -> k }
                    Box(
                        Modifier.weight(1f).height(60.dp).clip(R16).background(C.Surf2)
                            .clickable(role = Role.Button) { when (k) { "←" -> onBack(); "지움" -> onClear(); else -> onDigit(k) } }
                            .semantics { contentDescription = desc },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (k) {
                            "←" -> GIcon(Gi.ArrowLeft, C.Fg, 28.dp)
                            "지움" -> GIcon(Gi.X, C.Mut, 26.dp)
                            else -> Text(k, style = ts(28.sp, C.Fg, FontWeight.SemiBold))
                        }
                    }
                }
            }
        }
    }
}

/** 번호를 넣은 뒤: 차 화면의 확인을 기다립니다(서버 GET /api/pair/status를 2초마다). */
@Composable
private fun ColumnScope.WaitingForCar(c: Controller) {
    val pulse by rememberInfiniteTransition(label = "wait").animateFloat(0.35f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    Spacer(Modifier.height(24.dp))
    Panel(pad = 24.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(120.dp).clip(Pill).background(C.Mint.copy(alpha = 0.12f * pulse + 0.05f)), contentAlignment = Alignment.Center) {
                GIcon(Gi.CarFront, C.Mint, 64.dp)
            }
            Spacer(Modifier.height(18.dp))
            Text("차 화면에서\n[연결]을 눌러 주세요", style = ts(24.sp, C.Fg, FontWeight.Bold, 32.sp), textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Text("이 폰: ${c.prefs.phoneName}", style = Small, textAlign = TextAlign.Center)
            Text("2초마다 확인합니다", style = Small.copy(color = C.Dim), textAlign = TextAlign.Center)
        }
    }
    Spacer(Modifier.weight(1f))
    BigButton(Btn("취소", Gi.X, { c.cancelPairing() }), Modifier.fillMaxWidth().padding(top = 12.dp), C.Surf2, C.Fg, C.Mint)
}

/** 차 화면의 QR 읽기(CameraX + ZXing). 에뮬레이터는 가상 장면이라 실제 QR을 비추기 어렵습니다(번호 넣기를 씀). */
@Composable
fun QrScanScreen(c: Controller) = PhoneFrame {
    TopBar("QR 찍기", onBack = { c.back() })
    val context = LocalContext.current
    val ask = rememberPermission(Manifest.permission.CAMERA)
    var granted by remember { mutableStateOf(hasPermission(context, Manifest.permission.CAMERA)) }
    var denied by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!granted) ask({ granted = true }, { denied = true }) }
    var found by remember { mutableStateOf<String?>(null) }
    val handle = remember { CameraHandle() }
    val analyzer = remember {
        QrAnalyzer { text ->
            val p = PairLink.parse(text)
            if (p == null) { c.showToast("차 화면의 연결 QR이 아닙니다") } else {
                found = p.code
                c.back()
                c.claim(p.code, p.server)
            }
        }
    }
    if (!granted) {
        if (denied) PermissionNotice("QR을 읽으려면 카메라 권한이 필요합니다") { ask({ granted = true; denied = false }, { denied = true }) }
    } else {
        Box(Modifier.fillMaxWidth().aspectRatio(0.8f).clip(R20).background(C.Surf)) {
            CameraPreview(handle, Modifier.fillMaxSize(), withCapture = false, analyzer = analyzer)
            Canvas(Modifier.fillMaxSize()) {
                val side = size.minDimension * 0.66f
                drawRoundRect(C.Mint, Offset((size.width - side) / 2, (size.height - side) / 2), Size(side, side), CornerRadius(24f), style = Stroke(5f))
            }
            handle.error?.let { Text(it, style = ts(16.sp, C.Amber), modifier = Modifier.align(Alignment.Center)) }
        }
        Text(found?.let { "읽었습니다 · 연결 중" } ?: "차 화면의 QR을 네모 안에 맞추세요", style = ts(17.sp, C.Fg, FontWeight.Medium),
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp), textAlign = TextAlign.Center)
    }
    Spacer(Modifier.weight(1f))
    BottomButtons(null, Btn("번호로 넣기", Gi.Pencil, { c.back() }))
}
