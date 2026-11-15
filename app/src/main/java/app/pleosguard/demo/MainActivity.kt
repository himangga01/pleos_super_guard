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
import app.pleosguard.demo.sdk.CareLink
import app.pleosguard.demo.sdk.Location
import app.pleosguard.demo.sdk.Navi
import app.pleosguard.demo.sdk.VehicleSignals
import app.pleosguard.demo.sdk.Voice
import app.pleosguard.demo.ui.Pretendard

private const val CAR_SPEED = "android.car.permission.CAR_SPEED"
private const val FUSED_LOCATION = "pleos.car.permission.FUSED_LOCATION"

class MainActivity : ComponentActivity() {
    private lateinit var controller: DemoController
    private lateinit var signals: VehicleSignals
    private lateinit var voice: Voice
    private lateinit var check: SdkCheck
    private lateinit var navi: Navi
    private lateinit var location: Location
    private var receiver: BroadcastReceiver? = null
    private var link: CareLink? = null

    private val askSpeed = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { signals.startSpeed(); if (controller.screen == Screen.SDK_CHECK) check.runSpeed() }
    }

    /** 처음 열 때 위험 권한 두 개(차속 CAR_SPEED, 위치 FUSED_LOCATION)를 한 번에 묻습니다. 허락받은 것만 시작합니다. */
    private val askStartup = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[CAR_SPEED] == true) signals.startSpeed()
        if (res[FUSED_LOCATION] == true) location.start()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var ref: DemoController? = null
        signals = VehicleSignals(applicationContext) { ref?.bump() }
        voice = Voice(applicationContext) { ref?.bump() }
        navi = Navi(applicationContext) { ref?.bump() }
        location = Location(applicationContext) { ref?.bump() }
        controller = DemoController(signals, voice, navi = navi, location = location).also { ref = it }
        // 기록 저장(앱 전용 폴더의 JSON). 앱을 다시 열어도 기록·정비 노트·평소 주행 통계가 남습니다.
        val file = java.io.File(filesDir, "records.json")
        controller.loadBook(runCatching { file.readText() }.getOrNull())
        controller.store = { json -> runCatching { file.writeText(json) } }
        // 케어 상태(구독 · 리콜 표시 · 후기 · 방문 · 폰 증거 · 짐 알림 설정)와 모의 서버 연결(PC의 demo-app/server, 에뮬레이터에서 10.0.2.2:8766)
        val careFile = java.io.File(filesDir, "care.json")
        controller.loadCare(runCatching { careFile.readText() }.getOrNull())
        if (controller.carId == "car-demo") controller.carId = "car-" + java.util.UUID.randomUUID().toString().take(8)
        controller.careStore = { json -> runCatching { careFile.writeText(json) } }
        connectServer(getSharedPreferences("care", MODE_PRIVATE).getString("server_url", null) ?: CareLink.EMULATOR_BASE)
        // 진입 화면: 처음 열 때 잠깐. 긴급 안내 중에는 앱 뿌리에서 건너뜁니다.
        if (savedInstanceState == null) controller.intro = true // 닫는 시간은 App()이 첫 화면을 그린 뒤부터 셉니다
        // 처음 설치(보험사를 아직 묻지 않음): 진입 화면 뒤에 내 자동차 보험사를 묻습니다(2026-11-14 사용자 요청).
        if (!controller.insurerAsked) controller.openInsurerPicker()
        navi.start()
        check = SdkCheck(this)
        signals.start()
        voice.start()
        // 위치 API는 권한이 없으면 onError가 아니라 SecurityException을 바로 던집니다(SDK 노트). 허락을 받은 뒤에 시작합니다.
        val need = listOf(CAR_SPEED, FUSED_LOCATION).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (CAR_SPEED !in need) signals.startSpeed()
        if (FUSED_LOCATION !in need) location.start()
        if (need.isNotEmpty()) askStartup.launch(need.toTypedArray())
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

    /** 모의 서버 주소를 바꿉니다(시험 입력 `server`). 앱 설정에 남겨 다시 열어도 씁니다. */
    private fun connectServer(url: String) {
        link?.release()
        link = CareLink(url.trimEnd('/'), java.io.File(filesDir, "evidence")).also { controller.link = it }
        getSharedPreferences("care", MODE_PRIVATE).edit().putString("server_url", url.trimEnd('/')).apply()
        controller.serverOk = null; controller.pairCode = null
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
        controller.persist()
        signals.stop(); voice.release(); check.release(); navi.release(); location.release(); link?.release()
        super.onDestroy()
    }

    /**
     * 디버그 빌드 전용 시험 입력 수신기. 시연 녹화 때 화면을 가리지 않고 adb로 시험 입력을 넣습니다.
     * 예) adb shell am broadcast -a app.pleosguard.demo.TEST --es cmd airbag
     * cmd: speed-on, speed-off, airbag, drive, park, real, answer(--es text "메모할 말", 듣는 중에만), home,
     *      case-time(--es at "21:00:03", 대표 사례 시각), real-time, tab(--es screen RECORD),
     *      trunk-open, trunk-close, trunk-real, door-open, door-close, door-real(짐 알림 시험 입력),
     *      left-alert(짐 알림 바로 보기), phone-noise, phone-vibration(폰 증거 시험 입력), phone(폰 연결 화면),
     *      server(--es url http://10.0.2.2:8766, 모의 서버 주소 바꾸기),
     *      help-pick / guide(--es picks "0,5,6", 도움 흐름의 증상 고르기 / 대처 방법까지),
     *      accident, send, insurer(--es id samsung|none), insurer-setup(사고 대처 안내 · 보험사)
     * 여기서 넣은 값도 화면에 [시험 입력]으로 표시됩니다.
     */
    private fun registerTestReceiver() {
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val c = controller
                Log.i("PleosGuardTest", "test input: ${intent.getStringExtra("cmd")}")
                when (intent.getStringExtra("cmd")) {
                    "speed-on" -> c.testSpeedKmh = intent.getStringExtra("kmh")?.toFloatOrNull() ?: 60f
                    "speed-off" -> c.testSpeedKmh = null
                    "airbag" -> c.startCountdown(IncidentKind.AIRBAG, testInput = true)
                    "drive" -> c.driveSource = DriveSource.TEST_DRIVING
                    "park" -> c.driveSource = DriveSource.TEST_PARKED
                    "real" -> c.driveSource = DriveSource.REAL
                    "home" -> c.go(Screen.HOME)
                    "seed-records" -> c.seedExamples()
                    "case-time" -> c.setCaseTime(intent.getStringExtra("at") ?: "21:00:03")
                    "real-time" -> c.clockOffsetMs = 0L
                    "tab" -> c.go(Screen.valueOf(intent.getStringExtra("screen") ?: "HOME"))
                    "trunk-open" -> c.testTrunk = 1
                    "trunk-close" -> c.testTrunk = 0
                    "trunk-real" -> c.testTrunk = null
                    "door-open" -> c.testDriverDoor = 1
                    "door-close" -> c.testDriverDoor = 0
                    "door-real" -> c.testDriverDoor = null
                    "left-alert" -> c.showTestLeftAlert()
                    "phone-noise" -> c.injectTestEvidence("noise")
                    "phone-vibration" -> c.injectTestEvidence("vibration")
                    "phone" -> c.openPhone()
                    // 도움 흐름 시험: --es picks "0,5,6"(상황 고르기 증상 번호, SymptomGuide.SYMPTOMS 순서). guide는 고른 뒤 대처 방법까지 엽니다.
                    "help-pick", "guide" -> {
                        val picks = intent.getStringExtra("picks").orEmpty().split(",").mapNotNull { it.trim().toIntOrNull() }
                            .mapNotNull { app.pleosguard.demo.logic.SymptomGuide.SYMPTOMS.getOrNull(it) }.distinct().take(3)
                        c.openHelp(); picks.forEach { c.toggleParkedChoice(it) }
                        if (intent.getStringExtra("cmd") == "guide") c.openGuide()
                    }
                    // 사고 대처 안내 시험: accident(사고가 났어요 → 10초 확인), send(바로 보내기 → 사고 대처 안내), insurer(--es id samsung 또는 none), insurer-setup(처음 설정 창)
                    "accident" -> c.startCountdown(IncidentKind.ACCIDENT, testInput = true)
                    "send" -> c.send()
                    "insurer" -> intent.getStringExtra("id").let { id -> c.saveInsurer(if (id == null || id == "none") null else id) }
                    "insurer-setup" -> { c.insurerAsked = false; c.openInsurerPicker() }
                    "server" -> { connectServer(intent.getStringExtra("url") ?: CareLink.EMULATOR_BASE); c.showToast("모의 서버: ${link?.base}") }
                    "answer" -> {
                        val text = intent.getStringExtra("text").orEmpty()
                        c.heardTest = true
                        if (voice.listening) voice.injectHeard(text)
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
            Text("플레오스 슈퍼 케어 · SDK 점검", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = Pretendard, modifier = Modifier.weight(1f))
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
