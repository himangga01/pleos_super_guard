package app.pleossupercare.phone.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pleossupercare.phone.BuildConfig
import app.pleossupercare.phone.Controller
import app.pleossupercare.phone.Screen
import app.pleossupercare.phone.data.Prefs
import app.pleossupercare.phone.ui.BigButton
import app.pleossupercare.phone.ui.Btn
import app.pleossupercare.phone.ui.C
import app.pleossupercare.phone.ui.Gi
import app.pleossupercare.phone.ui.Head
import app.pleossupercare.phone.ui.Panel
import app.pleossupercare.phone.ui.PhoneFrame
import app.pleossupercare.phone.ui.Small
import app.pleossupercare.phone.ui.TopBar
import app.pleossupercare.phone.ui.ts
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(c: Controller) = PhoneFrame {
    TopBar("설정", onBack = { c.back() })
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(c.prefs.serverUrl) }
    var status by remember { mutableStateOf<String?>(null) }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel {
            Text("연결된 차", style = Head)
            val car = c.prefs.carId
            if (car != null) {
                Text(car, style = ts(20.sp, C.Fg, FontWeight.Bold), modifier = Modifier.padding(top = 6.dp))
                Text("이 폰: ${c.prefs.phoneName}", style = Small)
                if (c.prefs.pairedAt > 0) Text("연결: " + SimpleDateFormat("M/d HH:mm", Locale.KOREA).format(Date(c.prefs.pairedAt)), style = Small)
                Spacer(Modifier.height(10.dp))
                BigButton(Btn("연결 끊기", Gi.X, { c.unpair() }), Modifier.fillMaxWidth(), C.Surf2, C.Amber, C.Amber, height = 56.dp)
            } else {
                Text("연결 안 됨", style = ts(18.sp, C.Mut), modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(10.dp))
                BigButton(Btn("차와 연결", Gi.Link, { c.home(); if (c.screen != Screen.Pair) c.go(Screen.Pair) }), Modifier.fillMaxWidth(), C.Mint, C.Dark, C.Dark, height = 56.dp)
            }
        }
        Panel {
            Text("서버 주소", style = Head)
            OutlinedTextField(
                value = url, onValueChange = { url = it.trim() }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                textStyle = ts(17.sp, C.Fg), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.Mint, unfocusedBorderColor = C.Line, cursorColor = C.Mint,
                    focusedContainerColor = C.Surf2, unfocusedContainerColor = C.Surf2),
            )
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton(Btn("기본값", Gi.Refresh, { url = Prefs.defaultServer() }), Modifier.weight(1f), C.Surf2, C.Fg, C.Mint, height = 52.dp)
                BigButton(Btn("저장 · 확인", Gi.Check, {
                    c.prefs.serverUrl = url
                    status = "확인 중…"
                    scope.launch { status = c.health(url) }
                }), Modifier.weight(1.3f), C.Mint, C.Dark, C.Dark, height = 52.dp)
            }
            status?.let { Text(it, style = ts(16.sp, if (it.startsWith("연결됨")) C.Mint else C.Amber, FontWeight.Medium), modifier = Modifier.padding(top = 8.dp)) }
            Text("에뮬레이터: http://10.0.2.2:8766\n실제 폰: PC에서 adb reverse tcp:8766 tcp:8766 뒤 http://127.0.0.1:8766", style = Small, modifier = Modifier.padding(top = 8.dp))
        }
        Panel {
            Text("이 앱", style = Head)
            Text("플레오스 슈퍼 케어 모바일 ${BuildConfig.VERSION_NAME}", style = ts(17.sp, C.Fg), modifier = Modifier.padding(top = 6.dp))
            Text("데모용입니다. 소리는 상대 크기(dBFS), 진동은 실험 기능이며 원인을 판단하지 않습니다. 실제 서비스에서는 PC 모의 서버 자리에 백엔드 서버가 옵니다.", style = Small, modifier = Modifier.padding(top = 4.dp))
            Text("글꼴 Pretendard(OFL) · 아이콘 Lucide(ISC) · QR 읽기 ZXing · 카메라 CameraX(Apache-2.0). 라이선스 글은 앱 안 assets/licenses에 있습니다.", style = Small, modifier = Modifier.padding(top = 6.dp))
        }
    }
}
