package app.pleosguard.demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pleosguard.demo.logic.VoiceAnswer
import app.pleosguard.demo.sdk.VehicleSignals
import app.pleosguard.demo.sdk.Voice
import app.pleosguard.demo.ui.Pretendard

private const val CAR_SPEED = "android.car.permission.CAR_SPEED"

class MainActivity : ComponentActivity() {
    private lateinit var controller: DemoController
    private lateinit var signals: VehicleSignals
    private lateinit var voice: Voice
    private lateinit var check: SdkCheck
    private var receiver: BroadcastReceiver? = null

    private val askSpeed = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { signals.startSpeed(); if (controller.screen == Screen.SDK_CHECK) check.runSpeed() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var ref: DemoController? = null
        signals = VehicleSignals(applicationContext) { ref?.bump() }
        voice = Voice(applicationContext) { ref?.bump() }
        controller = DemoController(signals, voice).also { ref = it }
        check = SdkCheck(this)
        signals.start()
        voice.start()
        if (checkSelfPermission(CAR_SPEED) == PackageManager.PERMISSION_GRANTED) signals.startSpeed() else askSpeed.launch(CAR_SPEED)
        setContent {
            PleosGuardApp(controller, voice, signals) {
                SdkCheckScreen(
                    items = check.items,
                    onVehicle = { check.runVehicle() },
                    onSpeed = { if (checkSelfPermission(CAR_SPEED) == PackageManager.PERMISSION_GRANTED) check.runSpeed() else askSpeed.launch(CAR_SPEED) },
                    onTts = { check.runTts() },
                    onBack = { controller.go(Screen.HOME) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        controller.start()
        if (BuildConfig.DEBUG) registerTestReceiver()
    }

    override fun onStop() {
        controller.stop()
        receiver?.let { runCatching { unregisterReceiver(it) } }; receiver = null
        super.onStop()
    }

    override fun onDestroy() {
        signals.stop(); voice.release(); check.release()
        super.onDestroy()
    }

    /**
     * 디버그 빌드 전용 시험 입력 수신기. 시연 녹화 때 화면을 가리지 않고 adb로 시험 입력을 넣습니다.
     * 예) adb shell am broadcast -a app.pleosguard.demo.TEST --es cmd airbag
     * cmd: speed-on, speed-off, prompt, airbag, drive, park, real, answer(--es text "안 멈춰요"), home,
     *      case-time(--es at "21:00:03", 대표 사례 시각), real-time, tab(--es screen RECORD)
     * 여기서 넣은 값도 화면에 [시험 입력]으로 표시됩니다.
     */
    private fun registerTestReceiver() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val c = controller
                Log.i("PleosGuardTest", "test input: ${intent.getStringExtra("cmd")}")
                when (intent.getStringExtra("cmd")) {
                    "speed-on" -> { c.testSpeedKmh = intent.getStringExtra("kmh")?.toFloatOrNull() ?: 60f; c.detector.resetTrip() }
                    "speed-off" -> c.testSpeedKmh = null
                    "prompt" -> c.simulateSensorCombination()
                    "airbag" -> c.startCountdown(IncidentKind.AIRBAG, testInput = true)
                    "drive" -> c.driveSource = DriveSource.TEST_DRIVING
                    "park" -> c.driveSource = DriveSource.TEST_PARKED
                    "real" -> c.driveSource = DriveSource.REAL
                    "home" -> c.go(Screen.HOME)
                    "case-time" -> c.setCaseTime(intent.getStringExtra("at") ?: "21:00:03")
                    "real-time" -> c.clockOffsetMs = 0L
                    "tab" -> c.go(Screen.valueOf(intent.getStringExtra("screen") ?: "HOME"))
                    "answer" -> {
                        val text = intent.getStringExtra("text").orEmpty()
                        c.heardTest = true
                        if (voice.listening) voice.injectHeard(text) else if (c.screen == Screen.CONFIRM) {
                            c.promptAnswerHeard = "$text [시험 입력]"
                            c.answerPrompt(app.pleosguard.demo.logic.VoiceAnswerParser.parse(text).let { if (it == VoiceAnswer.UNKNOWN) VoiceAnswer.OK else it }, byVoice = true)
                        }
                    }
                }
            }
        }
        val filter = IntentFilter("app.pleosguard.demo.TEST")
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, filter, Context.RECEIVER_EXPORTED) else @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(r, filter)
        receiver = r
    }
}

private val Bg = Color(0xFF090E16)
private val Surf = Color(0xFF141C2A)
private val Mint = Color(0xFF5EEAD4)
private val Red = Color(0xFFFF5B6B)
private val Muted = Color(0xFFB8C5D8)

/** SDK 점검 화면: 로컬 Pleos 에뮬레이터에서 공개 SDK가 실제로 붙는지 확인합니다(시연 패널에서 엽니다). */
@Composable
fun SdkCheckScreen(items: List<CheckItem>, onVehicle: () -> Unit, onSpeed: () -> Unit, onTts: () -> Unit, onBack: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().background(Bg).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row {
            Text("플레오스 가드 · SDK 점검", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = Pretendard, modifier = Modifier.weight(1f))
            Text("돌아가기", color = Mint, fontSize = 26.sp, fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onBack).padding(8.dp))
        }
        Text("로컬 Pleos 에뮬레이터 2.1.0에서 공개 SDK가 실제로 붙는지 확인합니다", color = Muted, fontSize = 22.sp, fontFamily = Pretendard)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items) { it -> CheckRow(it) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BigButton("차량 SDK", Modifier.weight(1f), onVehicle)
            BigButton("차속", Modifier.weight(1f), onSpeed)
            BigButton("음성", Modifier.weight(1f), onTts)
        }
    }
}

@Composable
private fun CheckRow(item: CheckItem) {
    val (label, color) = when (item.state) {
        CheckItem.State.WAITING -> "대기" to Muted
        CheckItem.State.OK -> "성공" to Mint
        CheckItem.State.FAIL -> "실패" to Red
    }
    Row(Modifier.fillMaxWidth().background(Surf, RoundedCornerShape(16.dp)).padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = Pretendard, modifier = Modifier.width(72.dp))
        Column {
            Text(item.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, fontFamily = Pretendard)
            if (item.detail.isNotEmpty()) Text(item.detail, color = Muted, fontSize = 22.sp, fontFamily = Pretendard)
        }
    }
}

@Composable
private fun BigButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(88.dp), shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Surf, contentColor = Color.White)) {
        Text(text, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = Pretendard)
    }
}
