package app.pleossupercare.phone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import app.pleossupercare.phone.logic.PairLink
import app.pleossupercare.phone.screens.HomeScreen
import app.pleossupercare.phone.screens.NoiseScreen
import app.pleossupercare.phone.screens.PairScreen
import app.pleossupercare.phone.screens.PhotoScreen
import app.pleossupercare.phone.screens.QrScanScreen
import app.pleossupercare.phone.screens.ReminderBanner
import app.pleossupercare.phone.screens.SettingsScreen
import app.pleossupercare.phone.screens.VibrationScreen
import app.pleossupercare.phone.screens.WarningScreen
import app.pleossupercare.phone.ui.C
import app.pleossupercare.phone.ui.ToastHost

class MainActivity : ComponentActivity() {
    private val c: Controller get() = (application as PhoneApp).controller

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_PleosCarePhone)   // 진입 화면 테마(창 바탕)에서 보통 테마로
        super.onCreate(savedInstanceState)
        handleLink(intent)
        val intro = savedInstanceState == null
        setContent { AppRoot(c, intro) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLink(intent)
    }

    override fun onResume() {
        super.onResume()
        c.foreground = true
        c.startPolling()
    }

    override fun onPause() {
        c.foreground = false
        super.onPause()
    }

    /** 차 화면 QR을 폰 기본 카메라로 찍으면 pleoscare://pair?code=…&server=… 로 열립니다. */
    private fun handleLink(intent: Intent?) {
        val data = intent?.dataString ?: return
        PairLink.parse(data)?.let { c.onPairLink(it) }
        intent.data = null
    }
}

@Composable
fun AppRoot(c: Controller, intro: Boolean = false) {
    BackHandler(enabled = c.canGoBack) { c.back() }
    NotificationAsk(c)
    // 처음 열 때 1.2초 진입 화면("플레오스 슈퍼 케어 모바일")
    var showIntro by remember { mutableStateOf(intro) }
    if (showIntro) LaunchedEffect(Unit) { kotlinx.coroutines.delay(1_200); showIntro = false }
    Box(Modifier.fillMaxSize().background(C.Bg)) {
        when (val s = c.screen) {
            Screen.Pair -> PairScreen(c)
            Screen.QrScan -> QrScanScreen(c)
            Screen.Home -> HomeScreen(c)
            Screen.Settings -> SettingsScreen(c)
            is Screen.Noise -> NoiseScreen(c, s.ctx)
            is Screen.Vibration -> VibrationScreen(c, s.ctx)
            is Screen.Warning -> WarningScreen(c, s.ctx)
            is Screen.Photo -> PhotoScreen(c, s.ctx, s.receipt)
        }
        ReminderBanner(c)
        ToastHost(c.toast)
        if (showIntro) Box(Modifier.fillMaxSize().background(C.Bg), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.launch_mobile), contentDescription = "플레오스 슈퍼 케어 모바일", modifier = Modifier.fillMaxWidth(0.72f))
        }
    }
}

/** 짝 맺기가 끝나면 Android 13+에서 알림 권한을 한 번 묻습니다(트렁크 짐 알림). */
@Composable
private fun NotificationAsk(c: Controller) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(c.askNotifications) {
        if (!c.askNotifications) return@LaunchedEffect
        c.askNotifications = false
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
