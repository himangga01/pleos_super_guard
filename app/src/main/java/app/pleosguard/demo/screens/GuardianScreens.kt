package app.pleosguard.demo.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pleosguard.demo.DemoController
import app.pleosguard.demo.Incident
import app.pleosguard.demo.IncidentKind
import app.pleosguard.demo.IncidentSnap
import app.pleosguard.demo.Screen
import app.pleosguard.demo.SnapText
import app.pleosguard.demo.car3d.Car3D
import app.pleosguard.demo.car3d.CarMode
import app.pleosguard.demo.car3d.CarState
import app.pleosguard.demo.car3d.Orbit
import app.pleosguard.demo.car3d.rememberCarViewState
import app.pleosguard.demo.ui.*
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * 보호자 알림 상태(모의 발송). 2026-11-13 밤 사용자 요청으로 다시 그렸습니다.
 * - 글씨를 키우고 줄 사이를 띄웠습니다(넓은 창 본문 27sp, 핵심 값은 더 크게).
 * - 줄마다 뜻이 보이는 아이콘을 붙이고, 보호자 상태 글은 색과 아이콘이 함께 바뀝니다.
 * - 사고 순간 차 상태를 위에서 본 차 그림(차체 · 앞유리 · 바퀴 · 문 4개 · 좌석 4개)으로 보이고, 글 줄도 함께 둡니다.
 * - 주소는 내비 현재 위치 기준이고 좌표는 FusedLocation이라 출처가 다릅니다. 둘이 1km 넘게 떨어지면 그렇다고 알립니다.
 * 세로 한 창은 위아래로, 가로(도크 안)는 설계 시안 ui-guardian처럼 왼쪽(보호자 · 위치)과 오른쪽(차 상태) 두 칸으로 둡니다.
 */

/** 배치별 글자·아이콘 크기. 세로 한 창은 높이가 빠듯해 조금 작게 씁니다. */
private class GSize(val name: TextUnit, val body: TextUnit, val small: TextUnit, val time: TextUnit, val icon: Dp, val gap: Dp, val rowPad: Dp)
private val PORTRAIT = GSize(28.sp, 24.sp, 22.sp, 40.sp, 32.dp, 8.dp, 10.dp)
private val WIDE = GSize(30.sp, 27.sp, 24.sp, 50.sp, 38.dp, 16.dp, 18.dp)
private fun lh(t: TextUnit) = (t.value * 1.35f).sp

@Composable
fun GuardianScreen(c: DemoController) = ScreenFrame {
    c.tick // 모의 상태(접수 → 전달 → 열람)가 시간에 따라 바뀝니다
    val wide = LocalWide.current
    val z = if (wide) WIDE else PORTRAIT
    ParkedHead(c, null)
    TitleRow("안심 케어 · 보호자 알림", c)
    val inc = c.incident
    val sent = inc?.sentWall
    if (wide) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(Modifier.weight(1.12f).fillMaxHeight()) {
                GuardianPanel(c, z)
                InsurerRow(c, 16.dp)
                if (inc != null && sent != null) LocationPanel(c, inc, z, Modifier.padding(top = 16.dp))
            }
            if (inc != null && sent != null) CarStatePanel(inc, z, wide = true, interactive = !c.driving, modifier = Modifier.weight(1f).fillMaxHeight())
            else Panel(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GIcon(Gi.CarFront, C.Mut, z.icon); Spacer(Modifier.width(14.dp))
                    Text("보낸 뒤 나옵니다", style = ts(z.body, C.Mut, lineHeight = lh(z.body)))
                }
            }
        }
    } else {
        GuardianPanel(c, z)
        InsurerRow(c, 12.dp)
        if (inc != null && sent != null) {
            LocationPanel(c, inc, z, Modifier.padding(top = 12.dp))
            CarStatePanel(inc, z, wide = false, interactive = !c.driving, modifier = Modifier.padding(top = 12.dp).weight(1f))
        } else Spacer(Modifier.weight(1f))
    }
    BottomButtons(Btn("기록 보기", Gi.History) { c.go(Screen.RECORD) }, Btn("공유 중지", Gi.MapPinOff) { c.stopSharing() }, RightKind.PURPLE)
}

/** 보호자별 상태. 상태 글의 색과 아이콘이 함께 바뀝니다(열람 = 민트 두 체크, 문자 재발송 = 주황 말풍선, 보내는 중 = 흰 보내기). */
@Composable
private fun GuardianPanel(c: DemoController, z: GSize) = Panel(pad = 20.dp) {
    val sent = c.incident?.sentWall
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.Users, C.Purple, z.icon); Spacer(Modifier.width(12.dp))
        Text("보호자", style = ts(z.body, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
        if (sent != null) {
            GIcon(Gi.Send, C.Mut, 26.dp); Spacer(Modifier.width(6.dp))
            Text(DemoController.hms(sent), style = ts(z.small, C.Mut)); Spacer(Modifier.width(10.dp)); MockBadge("모의 발송"); Spacer(Modifier.width(10.dp))
        }
        InfoButton("보호자 알림", listOf(
            "접수 → 전달 → 열람 순서로 바뀝니다(받은 편지함 → 보내기 → 눈 아이콘)",
            "서버 없이 앱 안에서 재생한 모의 상태입니다",
            "알림톡 전달이 늦으면 문자로 다시 보냅니다",
        ), enabled = !c.driving)
    }
    // 보호자마다 단계 아이콘(접수 · 전달 · 열람)과 짧은 이름. 2026-11-14 아이콘 조사 규칙 10.
    for (i in 0 until 2) {
        val (step, label) = c.guardianStep(i)
        val sms = i == 1 && step == 2
        Row(Modifier.padding(top = z.gap).fillMaxWidth().clip(R16).background(C.Surf2).padding(horizontal = 20.dp, vertical = z.rowPad),
            verticalAlignment = Alignment.CenterVertically) {
            GIcon(Gi.Users, C.Purple, z.icon); Spacer(Modifier.width(14.dp))
            Text("보호자 ${i + 1}", style = ts(z.name, weight = FontWeight.SemiBold), maxLines = 1)
            Spacer(Modifier.weight(1f))
            if (sent == null) Text("준비됨", style = ts(z.body, C.Mut, FontWeight.SemiBold))
            else Column(horizontalAlignment = Alignment.End) {
                StepIcons(step, color = if (sms) C.Amber else C.Mint)
                Text(label, style = ts(z.small, if (sms) C.Amber else C.Fg, FontWeight.SemiBold), maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** 아이콘 + 내용 한 줄. 줄 사이는 [GSize.gap]만큼 띄웁니다. */
@Composable
private fun IconLine(icon: Gi, tint: Color, z: GSize, top: Dp = z.gap, content: @Composable RowScope.() -> Unit) {
    Row(Modifier.padding(top = top).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(z.icon + 6.dp), contentAlignment = Alignment.Center) { GIcon(icon, tint, z.icon) }
        Spacer(Modifier.width(14.dp))
        content()
    }
}

/** 위치: 측정 시각(시계) · 주소(위치 핀, 내비 현재 위치 기준) · 좌표(조준점, FusedLocation) · 지도 없음(휴대폰) · 보내는 중(보내기). */
@Composable
private fun LocationPanel(c: DemoController, inc: Incident, z: GSize, modifier: Modifier) = Panel(modifier, pad = 20.dp) {
    val s = inc.snap
    IconLine(Gi.Clock, C.Purple, z, top = 0.dp) {
        Text(s.locWall?.let { DemoController.hms(it) } ?: "확인 불가", style = ts(z.time, weight = FontWeight.Bold, lineHeight = (z.time.value * 1.15f).sp),
            modifier = Modifier.weight(1f))
        val age = s.locWall?.let { inc.detectedWall - it }
        if (age != null && age > 60_000) Box(Modifier.clip(Pill).background(C.TestBg).padding(horizontal = 14.dp, vertical = 6.dp)) {
            Text("${age / 60_000}분 전 위치", style = ts(z.small, C.Amber, FontWeight.SemiBold))
        }
        Spacer(Modifier.width(10.dp))
        // 출처와 좌표는 i 뒤로(2026-11-14 아이콘 조사 규칙 2). 숫자 · 시각 · 주소는 글로 남깁니다(규칙 9).
        InfoButton("위치 출처", buildList {
            add(if (s.locWall == null) "위치 시각: 받지 못함" else if (s.locMeasured) "시각: FusedLocation이 위치를 잰 시각" else "시각: 앱이 위치를 받은 시각")
            if (s.lat != null && s.lng != null) add("좌표 %.4f, %.4f · FusedLocation · confidence 원값 %s".format(s.lat, s.lng, s.confidence?.toString() ?: "없음"))
            add("주소: 내비 현재 위치 기준(NaviHelper)")
            add("지도는 앱에 없고 보호자 휴대폰 페이지에서 봅니다")
        }, enabled = !c.driving)
    }
    IconLine(Gi.MapPin, C.Purple, z) {
        Text(s.address ?: c.navi?.address ?: "주소 확인 불가", style = ts(z.body, lineHeight = lh(z.body)), maxLines = 2, modifier = Modifier.weight(1f))
    }
    SnapText.sourceGapKm(s)?.takeIf { it > 1.0 }?.let { km ->
        IconLine(Gi.TriangleAlert, C.Amber, z) {
            Text("주소와 좌표가 약 ${"%,d".format(km.roundToInt())}km 차이", style = ts(z.small, C.Amber, FontWeight.SemiBold, lh(z.small)), modifier = Modifier.weight(1f), maxLines = 2)
        }
    }
    IconLine(Gi.Send, C.Purple, z) {
        Text("2명에게 공유 중", style = ts(z.body, C.Purple, FontWeight.SemiBold), modifier = Modifier.weight(1f))
        GIcon(Gi.Smartphone, C.Mut, 26.dp); Spacer(Modifier.width(6.dp))
        Text("지도는 보호자 폰", style = ts(z.small, C.Mut), maxLines = 1)
    }
}

private data class CarLine(val icon: Gi, val text: String, val color: Color, val test: Boolean)

/** 사고 순간 상태를 아이콘 칩 4개로(무엇 = 아이콘, 급한 정도 = 색, 상태 = 짧은 글). 자세한 좌석 이름은 i 뒤에 둡니다. */
private fun carLines(s: IncidentSnap, airbag: Boolean): List<CarLine> {
    val belt = SnapText.anyUnbuckled(s)
    val open = s.doors.count { (it ?: 0) > 0 }
    val n = s.seats.count { it == true }
    return buildList {
        add(CarLine(Gi.Seat, when { s.seats.all { it == null } -> "탑승 확인 불가"; n == 0 -> "탑승 없음"; else -> "탑승 ${n}명" },
            if (s.seats.all { it == null }) C.Mut else C.Fg, false))
        add(CarLine(Gi.Belt, when (belt) { true -> "벨트 안 맴"; false -> "벨트 맴"; null -> "벨트 확인 불가" }, when (belt) { true -> C.Amber; false -> C.Mint; null -> C.Mut }, false))
        if (airbag) add(CarLine(Gi.Airbag, "에어백 전개", C.Red, true))
        add(CarLine(Gi.Door, when { s.doors.all { it == null } -> "문 확인 불가"; open > 0 -> "문 ${open}개 열림"; else -> "문 닫힘" },
            when { s.doors.all { it == null } -> C.Mut; open > 0 -> C.Amber; else -> C.Fg }, false))
    }
}

/**
 * 사고 순간 차 상태: 3D 아반떼 실내(덮개 반투명) + 글 줄. 탑승한 좌석에는 사람 모양, 안전벨트는 초록(맴) · 주황(안 맴),
 * 열린 문은 주황으로 열린 채, 에어백 전개 신호는 운전석 앞 빨간 에어백으로 보입니다. 끌어서 돌려 볼 수 있습니다.
 * 가로는 그림 위 · 글 아래, 세로는 그림 왼쪽 · 글 오른쪽.
 */
@Composable
private fun CarStatePanel(inc: Incident, z: GSize, wide: Boolean, interactive: Boolean, modifier: Modifier) = Panel(modifier, pad = 20.dp) {
    val s = inc.snap
    val airbag = inc.kind == IncidentKind.AIRBAG
    val car = remember(s, airbag) { CarState(s.seats, s.belts, s.doors, airbag) }
    val view = rememberCarViewState(Orbit.GUARDIAN)
    Row(verticalAlignment = Alignment.CenterVertically) {
        GIcon(Gi.CarFront, C.Purple, z.icon); Spacer(Modifier.width(12.dp))
        Text("사고 순간", style = ts(z.body, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
        GIcon(Gi.Clock, C.Mut, 26.dp); Spacer(Modifier.width(6.dp))
        Text(DemoController.hms(inc.detectedWall), style = ts(z.small, C.Mut)); Spacer(Modifier.width(10.dp))
        InfoButton("차 상태 출처", listOf(
            "탑승: " + SnapText.occupants(s) + " · 안전벨트: " + SnapText.belts(s) + " · 문: " + SnapText.doors(s),
            "문 · 좌석 · 안전벨트는 Vehicle SDK 값입니다. 에뮬레이터에서는 Virtual Console 값입니다",
            "3D 그림: 사람 = 탑승, 초록 띠 = 벨트 맴, 주황 좌석 = 벨트 안 맴, 주황 문 = 열림, 빨간 공 = 에어백",
            "에어백 전개 신호는 시연의 시험 입력입니다",
        ), enabled = interactive)
    }
    val lines = carLines(s, airbag)
    if (wide) Box(Modifier.padding(top = 12.dp).weight(1f).fillMaxWidth()) {
        // 넓은 창: 3D 실내 위에 상태 칩을 얹습니다(글 줄 · 범례 · 출처 문장은 칩과 i 단추로).
        Car3D(view, Modifier.fillMaxSize(), mode = CarMode.INTERIOR, state = car, markers = false, interactive = interactive)
        Column(Modifier.align(Alignment.TopStart), verticalArrangement = Arrangement.spacedBy(8.dp)) { lines.forEach { CarChip(it) } }
    } else Row(Modifier.padding(top = 4.dp).weight(1f).fillMaxWidth()) {
        Car3D(view, Modifier.width(300.dp).fillMaxHeight(), mode = CarMode.INTERIOR, state = car, markers = false, interactive = interactive)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) { lines.forEach { CarChip(it) } }
    }
}

@Composable
private fun CarChip(l: CarLine) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StatusChip(l.icon, l.text, l.color, fg = l.color)
        if (l.test) { Spacer(Modifier.width(8.dp)); TestBadge() }
    }
}
