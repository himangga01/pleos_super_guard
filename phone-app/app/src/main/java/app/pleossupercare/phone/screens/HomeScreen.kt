package app.pleossupercare.phone.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pleossupercare.phone.Controller
import app.pleossupercare.phone.Screen
import app.pleossupercare.phone.data.CarRequest
import app.pleossupercare.phone.data.Kind
import app.pleossupercare.phone.data.LocalCapture
import app.pleossupercare.phone.ui.BigButton
import app.pleossupercare.phone.ui.Btn
import app.pleossupercare.phone.ui.C
import app.pleossupercare.phone.ui.ConnChip
import app.pleossupercare.phone.ui.GIcon
import app.pleossupercare.phone.ui.Gi
import app.pleossupercare.phone.ui.HonestyBadge
import app.pleossupercare.phone.ui.PhoneFrame
import app.pleossupercare.phone.ui.Pill
import app.pleossupercare.phone.ui.R12
import app.pleossupercare.phone.ui.R16
import app.pleossupercare.phone.ui.R20
import app.pleossupercare.phone.ui.SectionLabel
import app.pleossupercare.phone.ui.Small
import app.pleossupercare.phone.ui.TileContent
import app.pleossupercare.phone.ui.TopBar
import app.pleossupercare.phone.ui.ts
import java.io.File

@Composable
fun HomeScreen(c: Controller) = PhoneFrame {
    TopBar("플레오스 슈퍼 케어 모바일") {
        Box(Modifier.size(48.dp).clip(Pill).clickable(role = Role.Button) { c.go(Screen.Settings) }.semantics { contentDescription = "설정" },
            contentAlignment = Alignment.Center) { GIcon(Gi.Settings, C.Mut, 24.dp) }
    }
    // 연결 상태는 앱 이름 아래 줄에(이름이 잘리지 않게)
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        ConnChip(c.serverOk, when (c.serverOk) { true -> "차와 연결됨"; false -> "서버 끊김"; null -> "확인 중" }) { c.go(Screen.Settings) }
    }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        if (c.pending.isNotEmpty()) {
            SectionLabel("차에서 요청한 기록") {
                Box(Modifier.clip(Pill).background(C.Mint).padding(horizontal = 10.dp, vertical = 2.dp)) {
                    Text("${c.pending.size}", style = ts(15.sp, C.Dark, FontWeight.Bold))
                }
            }
            c.pending.forEach { RequestCard(it) { c.openRequest(it) } }
            Spacer(Modifier.height(6.dp))
        }
        SectionLabel("지금 기록")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KindTile(Kind.NOISE, "가까이 · 10초") { c.go(Screen.Noise(null)) }
            KindTile(Kind.VIBRATION, "바닥에 두고", badge = "실험") { c.go(Screen.Vibration(null)) }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KindTile(Kind.WARNING, "색으로 후보") { c.go(Screen.Warning(null)) }
            KindTile(Kind.PHOTO, "손상 · 타이어") { c.go(Screen.Photo(null, false)) }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth().height(84.dp).clip(R20).background(C.Surf).border(1.dp, C.Line, R20)
                .clickable(role = Role.Button) { c.go(Screen.Photo(null, true)) }.semantics { contentDescription = Kind.RECEIPT.label },
        ) { TileContent(Kind.RECEIPT.icon, Kind.RECEIPT.color, Kind.RECEIPT.label, "찍어서 정비 이력에", row = true, trailing = Gi.ChevronRight) }

        SectionLabel("최근 기록")
        if (c.recent.isEmpty()) Text("아직 기록이 없습니다", style = Small, modifier = Modifier.padding(bottom = 8.dp))
        c.recent.take(8).forEach { RecentRow(c, it) }
        Spacer(Modifier.height(16.dp))
    }
}

/** 기록 종류 타일(정사각). 안의 아이콘 · 글자는 칸 크기에 비례합니다(ui/Tiles.kt). */
@Composable
private fun RowScope.KindTile(kind: Kind, sub: String, badge: String? = null, onClick: () -> Unit) {
    Box(
        Modifier.weight(1f).aspectRatio(1f).clip(R20).background(C.Surf).border(1.dp, C.Line, R20)
            .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = kind.label },
    ) {
        TileContent(kind.icon, kind.color, kind.label, sub)
        if (badge != null) HonestyBadge(badge, Modifier.align(Alignment.TopEnd).padding(10.dp))
    }
}

@Composable
private fun RequestCard(r: CarRequest, onClick: () -> Unit) {
    val kind = r.captureKind
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).heightIn(min = 76.dp).clip(R20).background(C.OnMint).border(2.dp, C.Mint, R20)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics { contentDescription = "${kind?.label ?: r.kind} 요청" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(Pill).background(C.Bg), contentAlignment = Alignment.Center) { GIcon(kind?.icon ?: Gi.CircleHelp, kind?.color ?: C.Mut, 30.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${kind?.label ?: r.kind} 요청", style = ts(18.sp, C.Fg, FontWeight.SemiBold))
            Text(listOfNotNull(r.partHint, r.note ?: r.text, ago(r.createdAt)).joinToString(" · "), style = Small, maxLines = 2)
        }
        GIcon(Gi.ChevronRight, C.Mint, 22.dp)
    }
}

@Composable
private fun RecentRow(c: Controller, item: LocalCapture) {
    val kind = Kind.of(item.kind)
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).heightIn(min = 68.dp).clip(R16).background(C.Surf).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val thumb = remember(item.thumb) { item.thumb?.let { File(c.store.thumbs, it) }?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } }
        if (thumb != null) Image(thumb.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(R12))
        else Box(Modifier.size(48.dp).clip(R12).background(C.Surf2), contentAlignment = Alignment.Center) { GIcon(kind?.icon ?: Gi.CircleHelp, kind?.color ?: C.Mut, 26.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(kind?.label ?: item.kind, style = ts(16.sp, C.Fg, FontWeight.SemiBold), maxLines = 1)
                Spacer(Modifier.width(8.dp))
                Text(ago(item.startedAt), style = ts(14.sp, C.Dim), maxLines = 1)
            }
            Text(item.summary, style = ts(14.sp, C.Mut), maxLines = 2)
            if (item.test) HonestyBadge("시험 입력", Modifier.padding(top = 4.dp))
        }
        Spacer(Modifier.width(8.dp))
        StatusTag(item.status) { c.retry(item.localId) }
    }
}

@Composable
private fun StatusTag(status: String, onRetry: () -> Unit) {
    val (label, color, icon) = when (status) {
        "acked" -> Triple("차에서 확인", C.Mint, Gi.Check)
        "sent" -> Triple("차로 보냄", C.Mint, Gi.Send)
        "sending" -> Triple("보내는 중", C.Dim, Gi.Clock)
        else -> Triple("다시 보내기", C.Amber, Gi.Refresh)
    }
    Row(
        Modifier.clip(Pill).border(1.dp, color.copy(alpha = 0.6f), Pill).then(if (status == "failed") Modifier.clickable(onClick = onRetry) else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GIcon(icon, color, 16.dp); Spacer(Modifier.width(4.dp))
        Text(label, style = ts(13.sp, color, FontWeight.SemiBold), maxLines = 1)
    }
}

/** 차에서 온 알림(트렁크 짐 · 뒷좌석). 화면 맨 위에 크게 띄우고 [확인했어요]를 누르면 차에 알립니다. */
@Composable
fun BoxScope.ReminderBanner(c: Controller) {
    val r = c.reminders.firstOrNull() ?: return
    val trunk = r.kind == "trunk_reminder"
    Column(
        Modifier.align(Alignment.TopCenter).padding(12.dp).widthIn(max = 600.dp).fillMaxWidth().clip(R20).background(C.OnAmber)
            .border(2.dp, C.Amber, R20).padding(16.dp).semantics { contentDescription = "차에서 온 알림" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(Pill).background(C.Amber.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                GIcon(if (trunk) Gi.Package else Gi.Seat, C.Amber, 32.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(if (trunk) "트렁크 확인" else "뒷좌석 확인", style = ts(16.sp, C.Amber, FontWeight.SemiBold))
                Text(r.text ?: "차에서 온 알림", style = ts(21.sp, C.Fg, FontWeight.Bold, 28.sp))
                Text("차에서 · ${ago(r.createdAt)}", style = Small)
            }
        }
        Spacer(Modifier.height(12.dp))
        BigButton(Btn("확인했어요", Gi.Check, { c.confirmReminder(r) }), Modifier.fillMaxWidth(), C.Amber, C.Dark, C.Dark)
    }
}
