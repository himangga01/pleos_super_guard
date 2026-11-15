package app.pleossupercare.phone.screens

import android.Manifest
import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pleossupercare.phone.Controller
import app.pleossupercare.phone.data.Kind
import app.pleossupercare.phone.data.Prefs
import app.pleossupercare.phone.data.ReqCtx
import app.pleossupercare.phone.sensing.ImageUtil
import app.pleossupercare.phone.ui.BottomButtons
import app.pleossupercare.phone.ui.Btn
import app.pleossupercare.phone.ui.C
import app.pleossupercare.phone.ui.CameraHandle
import app.pleossupercare.phone.ui.CameraPreview
import app.pleossupercare.phone.ui.Gi
import app.pleossupercare.phone.ui.PhoneFrame
import app.pleossupercare.phone.ui.R16
import app.pleossupercare.phone.ui.R20
import app.pleossupercare.phone.ui.SectionLabel
import app.pleossupercare.phone.ui.TopBar
import app.pleossupercare.phone.ui.ts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 사진 종류. 타이어는 "상태" 사진이며 홈 깊이를 재지 않습니다(폰 센서 조사 9절: 마모 깊이 측정은 넣지 않음). */
private val CATEGORIES = listOf("차체 손상" to Gi.CarFront, "타이어 상태" to Gi.Wheel, "액체 · 누유" to Gi.Droplet, "기타" to Gi.Image)

/** 사진 기록(손상 · 타이어 · 누유) 또는 정비 영수증. [receipt]면 kind = receipt. */
@Composable
fun PhotoScreen(c: Controller, ctx: ReqCtx?, receipt: Boolean) = PhoneFrame {
    TopBar(if (receipt) "정비 영수증" else "사진 기록", onBack = { c.back() })
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val emulator = remember { Prefs.isEmulator() }
    val ask = rememberPermission(Manifest.permission.CAMERA)
    var granted by remember { mutableStateOf(hasPermission(context, Manifest.permission.CAMERA)) }
    var denied by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!granted) ask({ granted = true }, { denied = true }) }
    val handle = remember { CameraHandle() }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var takenAt by remember { mutableLongStateOf(0L) }
    var category by remember { mutableStateOf(if (receipt) "정비 영수증" else CATEGORIES[0].first) }
    var part by remember { mutableStateOf(ctx?.partHint?.takeIf { h -> PARTS.any { it.first == h } }) }
    var note by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var mileage by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            photo = withContext(Dispatchers.IO) { ImageUtil.decodeUri(context, uri, 1600) }
            takenAt = System.currentTimeMillis(); busy = false
            if (photo == null) error = "사진을 읽지 못했습니다"
        }
    }

    fun send() {
        val bmp = photo ?: return
        val jpeg = ImageUtil.jpeg(bmp, 1280, 80) // 다시 저장하므로 EXIF(위치 등)는 남지 않습니다.
        val kind = if (receipt) Kind.RECEIPT else Kind.PHOTO
        val payload = c.basePayload(kind, takenAt, 0, if (receipt) "영수증" else category, part ?: ctx?.partHint, ctx, note, testInput = emulator)
        val m = payload.getJSONObject("metrics")
        amount.filter { it.isDigit() }.toLongOrNull()?.let { m.put("amountWon", it) }
        mileage.filter { it.isDigit() }.toLongOrNull()?.let { m.put("mileageKm", it) }
        m.put("imageWidth", bmp.width).put("imageHeight", bmp.height)
        payload.put("labels", JSONObject().put("category", category))
            .put("imageJpegBase64", Base64.encodeToString(jpeg, Base64.NO_WRAP))
        val summary = buildString {
            append(if (receipt) "정비 영수증" else "사진 · $category")
            part?.let { append(" · $it") }
            amount.filter { it.isDigit() }.toLongOrNull()?.let { append(" · ${"%,d".format(it)}원") }
        }
        payload.put("summary", summary)
        c.submit(kind, payload, summary, ImageUtil.jpeg(bmp, 256, 70), ctx)
    }

    val bmp = photo
    if (bmp == null) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            RequestBanner(ctx)
            if (!receipt) {
                ChipGroup(CATEGORIES, category) { category = it }
                Spacer(Modifier.padding(top = 10.dp))
            } else Text("영수증 · 정비 명세서를 평평하게 놓고 찍으세요", style = ts(18.sp, C.Fg, FontWeight.SemiBold), modifier = Modifier.padding(bottom = 10.dp))
            if (granted) {
                Box(Modifier.fillMaxWidth().aspectRatio(0.8f).clip(R20).background(C.Surf)) {
                    CameraPreview(handle, Modifier.fillMaxSize())
                    handle.error?.let { Text(it, style = ts(16.sp, C.Amber), modifier = Modifier.align(Alignment.Center)) }
                }
            } else if (denied) {
                PermissionNotice("찍으려면 카메라 권한이 필요합니다 · 사진 불러오기는 권한 없이 됩니다") { ask({ granted = true; denied = false }, { denied = true }) }
            }
            if (receipt) Badges("글자 읽기(OCR)는 다음 단계 · 금액은 직접 적기") else Badges("사진만 남깁니다 · 원인 판단 아님")
            error?.let { Note(it, C.Amber) }
        }
        BottomButtons(
            Btn("사진 불러오기", Gi.Image, { picker.launch("image/*") }, enabled = !busy),
            Btn(if (busy || handle.busy) "찍는 중" else "찍기", Gi.Camera, {
                busy = true
                handle.take(context, 1600) { bmp ->
                    busy = false
                    if (bmp == null) error = "찍지 못했습니다" else { photo = bmp; takenAt = System.currentTimeMillis() }
                }
            }, enabled = granted && handle.ready && !busy),
        )
    } else {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            RequestBanner(ctx)
            Image(bmp.asImageBitmap(), contentDescription = "찍은 사진", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(R16).background(C.Surf))
            if (!receipt) {
                SectionLabel("부위")
                ChipGroup(PARTS, part) { part = it }
            } else {
                SectionLabel("영수증 내용(선택)")
                Row {
                    Field(amount, { amount = it }, "금액(원)", Modifier.weight(1f), KeyboardType.Number)
                    Spacer(Modifier.width(8.dp))
                    Field(mileage, { mileage = it }, "주행거리(km)", Modifier.weight(1f), KeyboardType.Number)
                }
            }
            SectionLabel("메모(선택)")
            Field(note, { note = it }, if (receipt) "정비소 · 항목" else "예: 앞 범퍼 왼쪽 긁힘", Modifier.fillMaxWidth(), KeyboardType.Text)
            if (emulator) Badges("에뮬레이터 사진 · 시험 입력")
        }
        BottomButtons(Btn("다시 찍기", Gi.Camera, { photo = null }), Btn("차로 보내기", Gi.Send, { send() }))
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier, type: KeyboardType) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier, singleLine = true,
        label = { Text(label, style = ts(14.sp, C.Mut)) },
        textStyle = ts(17.sp, C.Fg),
        keyboardOptions = KeyboardOptions(keyboardType = type),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = C.Mint, unfocusedBorderColor = C.Line, cursorColor = C.Mint,
            focusedContainerColor = C.Surf, unfocusedContainerColor = C.Surf,
        ),
    )
}
