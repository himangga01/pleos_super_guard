package app.pleossupercare.phone.screens

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.pleossupercare.phone.data.ReqCtx
import app.pleossupercare.phone.ui.Btn
import app.pleossupercare.phone.ui.BigButton
import app.pleossupercare.phone.ui.C
import app.pleossupercare.phone.ui.GIcon
import app.pleossupercare.phone.ui.Gi
import app.pleossupercare.phone.ui.Panel
import app.pleossupercare.phone.ui.SelChip
import app.pleossupercare.phone.ui.Small
import app.pleossupercare.phone.ui.ts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 차 앱 3D 부위 칩과 같은 이름(차가 기록을 부위에 붙일 때 씀). */
val PARTS = listOf(
    "앞부분" to Gi.CarFront, "앞 타이어" to Gi.Wheel, "문" to Gi.Door, "뒤 타이어" to Gi.Wheel,
    "뒤쪽" to Gi.CarRear, "실내" to Gi.Seat, "잘 모르겠음" to Gi.CircleHelp,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGroup(options: List<Pair<String, Gi?>>, selected: String?, accent: Color = C.Mint, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (label, icon) -> SelChip(label, label == selected, icon = icon, accent = accent) { onSelect(label) } }
    }
}

/** 차 요청에 답하는 기록이면 위에 띠를 보입니다. */
@Composable
fun RequestBanner(ctx: ReqCtx?) {
    if (ctx == null) return
    Panel(Modifier.padding(bottom = 10.dp), bg = C.OnMint, pad = 14.dp, border = C.Mint) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GIcon(Gi.CarFront, C.Mint, 28.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("차에서 요청한 기록", style = ts(16.sp, C.Mint, FontWeight.SemiBold))
                val line = listOfNotNull(ctx.partHint, ctx.note).joinToString(" · ")
                if (line.isNotBlank()) Text(line, style = ts(17.sp, C.Fg, FontWeight.Medium), maxLines = 2)
            }
        }
    }
}

/** 권한이 없을 때의 안내 + 다시 묻기 · 설정 열기. */
@Composable
fun PermissionNotice(text: String, onRetry: () -> Unit) {
    val context = LocalContext.current
    Panel(bg = C.OnAmber, pad = 14.dp, border = C.Amber) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GIcon(Gi.TriangleAlert, C.Amber, 26.dp); Spacer(Modifier.width(10.dp))
            Text(text, style = ts(16.sp, C.Fg, FontWeight.Medium), modifier = Modifier.weight(1f))
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BigButton(Btn("다시 묻기", Gi.Refresh, onRetry), Modifier.weight(1f), C.Surf2, C.Fg, C.Mint, height = 52.dp)
            BigButton(Btn("앱 설정", Gi.Settings, { openAppSettings(context) }), Modifier.weight(1f), C.Surf2, C.Fg, C.Mint, height = 52.dp)
        }
    }
}

fun openAppSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** 쓰는 순간에 권한을 묻는 도구. 돌려준 함수를 부르면 이미 있으면 바로 [onGranted], 없으면 묻고 답에 따라 부릅니다. */
@Composable
fun rememberPermission(permission: String): (onGranted: () -> Unit, onDenied: () -> Unit) -> Unit {
    val context = LocalContext.current
    var waiting by remember { mutableStateOf<Pair<() -> Unit, () -> Unit>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val w = waiting; waiting = null
        if (ok) w?.first?.invoke() else w?.second?.invoke()
    }
    return { granted, denied ->
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) granted()
        else { waiting = granted to denied; launcher.launch(permission) }
    }
}

fun hasPermission(context: Context, permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

fun ago(ms: Long, now: Long = System.currentTimeMillis()): String {
    val d = (now - ms) / 1000
    return when {
        d < 60 -> "방금"
        d < 3600 -> "${d / 60}분 전"
        d < 86400 -> "${d / 3600}시간 전"
        else -> SimpleDateFormat("M/d HH:mm", Locale.KOREA).format(Date(ms))
    }
}

fun clock(ms: Long): String = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(ms))

fun fmt(v: Double, digits: Int = 1): String = String.format(Locale.US, "%.${digits}f", v)

fun hzText(hz: Double): String = when {
    hz <= 0 -> "-"
    hz >= 1000 -> fmt(hz / 1000, 2) + "k"
    hz >= 100 -> fmt(hz, 0)
    else -> fmt(hz, 1)
}

/** 회색 점선 꼬리표 여러 개를 한 줄로. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Badges(vararg texts: String) {
    FlowRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        texts.forEach { app.pleossupercare.phone.ui.HonestyBadge(it) }
    }
}

@Composable
fun Note(text: String, color: Color = C.Mut) = Text(text, style = Small.copy(color = color), modifier = Modifier.padding(top = 4.dp))
