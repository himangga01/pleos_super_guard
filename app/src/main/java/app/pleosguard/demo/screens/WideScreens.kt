package app.pleosguard.demo.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pleosguard.demo.DemoController
import app.pleosguard.demo.IncidentKind
import app.pleosguard.demo.R
import app.pleosguard.demo.Screen
import app.pleosguard.demo.ui.*

/*
 * 가로 화면(UX 4.2 기능 패리티). 세로 한 창의 위쪽 탭 4개 대신 왼쪽 도크 6개(홈 · 관리 · 기록 · 정비소 · 구독 · 안심)를 둡니다(2026-11-14 케어 전환).
 * 버튼 이름 · 버튼 수 · 왼쪽/오른쪽의 뜻 · 들어가는 화면은 세로와 같습니다.
 * 도크는 정차 중 화면에만 둡니다. 주행 중 홈 · 주행 중 상황 선택 · 긴급 화면에는 도크가 없습니다(세로와 같음).
 * 두 칸으로 바뀌는 화면: 홈(차 그림 + 오른쪽 맥락 패널), 경고등(왼쪽 찾기 + 오른쪽 후보 · 설명), 긴급 안내(왼쪽 문장 + 오른쪽 시각 영역).
 * 나머지 화면은 세로 화면을 도크 오른쪽에 창 폭대로 그립니다.
 */

/** 도크 틀에 넣을 화면인지 정합니다. */
fun dockedScreen(c: DemoController): Boolean = when (c.screen) {
    Screen.SDK_CHECK -> false
    Screen.HOME, Screen.SITUATION, Screen.SITUATION_PARKED, Screen.GUIDE -> !c.driving
    else -> c.screen !in DemoController.EMERGENCY
}

/** 머리줄(로고 · 앱 이름 · 시연 표시) + 왼쪽 도크 + 오른쪽 화면. */
@Composable
fun DockedFrame(c: DemoController, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(C.Bg).padding(start = 16.dp, top = 12.dp)) {
        Row(Modifier.fillMaxWidth().height(72.dp).padding(end = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.logo_symbol), contentDescription = "플레오스 슈퍼 케어 로고", modifier = Modifier.size(58.dp))
            Spacer(Modifier.width(20.dp))
            Text("플레오스 슈퍼 케어", style = ts(30.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
            DemoBadge({ c.demoPanel = true })
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            LeftDock(c)
            Box(Modifier.weight(1f).fillMaxHeight()) {
                CompositionLocalProvider(LocalDocked provides true) { content() }
            }
        }
    }
}

@Composable
fun LeftDock(c: DemoController) {
    val items = listOf(
        Triple("홈", Gi.Home, Screen.HOME), Triple("관리", Gi.Gauge, Screen.MANAGE), Triple("기록", Gi.History, Screen.RECORD_LIST),
        Triple("정비소", Gi.Wrench, Screen.SERVICE), Triple("구독", Gi.Repeat, Screen.SUBSCRIBE), Triple("안심", Gi.ShieldCheck, Screen.GUARDIAN),
    )
    val cur = when (c.screen) {
        Screen.WARN_FIND, Screen.WARN_RESULT, Screen.WARN_DETAIL, Screen.WARN_SEARCH, Screen.MANAGE, Screen.RECALL -> Screen.MANAGE
        Screen.RECORD, Screen.RECORD_LIST, Screen.PATTERN -> Screen.RECORD_LIST
        Screen.REVIEW -> Screen.SERVICE
        Screen.SITUATION, Screen.SITUATION_PARKED, Screen.GUIDE -> Screen.HOME // 도움 흐름은 홈의 "도움이 필요해요"에서 시작합니다
        else -> c.screen
    }
    // 도크 높이를 6칸이 나눠 갖고(84~128dp), 아이콘 · 글자도 칸 높이에 비례합니다.
    BoxWithConstraints(Modifier.padding(top = 16.dp, bottom = 16.dp).width(140.dp).fillMaxHeight()) {
        val itemH = ((maxHeight - 24.dp - 8.dp * 5) / 6).coerceIn(84.dp, 128.dp)
        val ic = itemH * 0.36f
        val label = (itemH.value * 0.2f).coerceIn(20f, 26f)
        Column(Modifier.fillMaxWidth().clip(R24).background(C.Surf).padding(vertical = 12.dp, horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { (name, icon, sc) ->
                val on = sc == cur
                Column(Modifier.fillMaxWidth().height(itemH).clip(R16).background(if (on) C.OnMint else C.Surf).then(if (on) Modifier.border(2.dp, C.Mint, R16) else Modifier)
                    .clickable(role = Role.Tab) { if (sc == Screen.SERVICE) c.openService() else c.go(sc) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    GIcon(icon, if (on) C.Mint else C.Mut, ic)
                    Spacer(Modifier.height(itemH * 0.06f))
                    Text(name, style = ts(label.sp, if (on) C.Mint else C.Mut, if (on) FontWeight.SemiBold else FontWeight.Normal), textAlign = TextAlign.Center, maxLines = 1)
                }
            }
        }
    }
}

/** 머리줄 오른쪽의 작은 동작 칩(가로 경고등 화면의 문구로 찾기 · 말로 찾기). */
@Composable
private fun ActionChip(icon: Gi, text: String, onClick: () -> Unit) {
    Row(Modifier.height(56.dp).clip(Pill).background(C.Surf).border(1.5.dp, C.Line, Pill).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically) {
        GIcon(icon, C.Amber, 28.dp); Spacer(Modifier.width(10.dp)); Text(text, style = ts(22.sp, C.Fg, FontWeight.SemiBold), maxLines = 1)
    }
}

/** 가로 케어 홈(정차 중): 왼쪽 3D 아반떼, 오른쪽 지금 상태 + 케어 카드 6개(부위를 고르면 그 부위의 증상 단추), 아래 버튼 2개. */
@Composable
fun HomeParkedWide(c: DemoController) = ScreenFrame {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("내 차 케어", style = ts(30.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
        GIcon(Gi.CarFront, C.Mut, 30.dp); Spacer(Modifier.width(8.dp)); Text("아반떼", style = ts(24.sp, C.Mut))
    }
    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        HomeCarCard(c, Modifier.weight(1.6f).fillMaxHeight())
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("지금 상태", style = ts(26.sp, weight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
                    InfoButton("지금 상태 출처", listOf(
                        "주행 상태: CarUxRestrictions(알 수 없으면 주행 중으로 봅니다)",
                        "차속: Android Automotive PERF_VEHICLE_SPEED",
                        "브레이크: Vehicle SDK의 밟음 여부(Boolean)",
                        "안심 케어: 사고 · 에어백 때 보호자 2명(시연용 연락처, 실제 사람 아님)에게 알림",
                        "케어 카드: 정비 주기 · 리콜 · 구독은 예시 자료입니다",
                    ), enabled = !c.driving)
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DriveChip(c); StatusChip(Gi.ShieldCheck, "안심 케어", C.Purple)
                }
                SignalLine(c)
            }
            if (c.selectedPart != null) Panel(Modifier.weight(1f)) { PartPanel(c, cols = 2) }
            else CareGrid(c, Modifier.weight(1f), cols = 2)
        }
    }
    HomeButtons(c)
}

/**
 * 가로 경고등: 한 화면의 왼쪽(색 → 모양 → 켜짐)과 오른쪽(후보 · 운행 판단 · 설명).
 * 세 가지를 다 고르기 전에는 아래 버튼이 세로 찾기 화면과 같고(문구로 찾기 · 말로 찾기),
 * 다 고르면 세로 결과 화면과 같은 버튼(정차 후 자세히 · 정비소 들르기)이 되고 찾기 버튼은 머리줄로 올라갑니다.
 */
@Composable
fun WarningWide(c: DemoController) = ScreenFrame {
    val done = c.warnColor != null && c.warnShape != null && c.warnMode != null
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("경고등 찾기", style = ts(30.sp, weight = FontWeight.SemiBold))
        if (done) {
            Spacer(Modifier.width(24.dp)); ActionChip(Gi.Search, "문구로 찾기") { c.openWarnSearch() }
            Spacer(Modifier.width(12.dp)); ActionChip(Gi.Mic, "말로 찾기") { c.findByVoiceButton() }
        }
        Spacer(Modifier.weight(1f))
        DriveChip(c)
    }
    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(1.1f).fillMaxHeight()) {
            WarningFindBody(c)
            Spacer(Modifier.weight(1f))
            if (c.driving) Row(verticalAlignment = Alignment.CenterVertically) {
                GIcon(Gi.Mic, C.Amber, 28.dp); Spacer(Modifier.width(10.dp)); Text("주행 중에는 말로 찾기", style = ts(22.sp, C.Amber))
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            if (done) WarningResultBody(c, compact = true)
            else Panel(Modifier.fillMaxSize()) {
                Spacer(Modifier.weight(1f))
                GIcon(Gi.Engine, C.Amber, 80.dp, Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(20.dp))
                FindFlow(Modifier.fillMaxWidth(), size = 56.dp)
                Spacer(Modifier.weight(1f))
            }
        }
    }
    if (done) WarningResultButtons(c)
    else BottomButtons(Btn("문구로 찾기", Gi.Search) { c.openWarnSearch() }, Btn("말로 찾기", Gi.Mic) { c.findByVoiceButton() }, RightKind.AMBER)
}

