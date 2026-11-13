package app.pleosguard.demo

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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

private const val CAR_SPEED = "android.car.permission.CAR_SPEED"

class MainActivity : ComponentActivity() {
    private lateinit var check: SdkCheck
    private val askSpeed = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) check.runSpeed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check = SdkCheck(this)
        setContent {
            SdkCheckScreen(
                items = check.items,
                onVehicle = { check.runVehicle() },
                onSpeed = {
                    if (checkSelfPermission(CAR_SPEED) == PackageManager.PERMISSION_GRANTED) check.runSpeed() else askSpeed.launch(CAR_SPEED)
                },
                onTts = { check.runTts() },
            )
        }
    }

    override fun onDestroy() {
        check.release()
        super.onDestroy()
    }
}

private val Bg = Color(0xFF090E16)
private val Surf = Color(0xFF141C2A)
private val Mint = Color(0xFF5EEAD4)
private val Red = Color(0xFFFF5B6B)
private val Muted = Color(0xFFB8C5D8)

/** 데모 앱 뼈대의 첫 화면: SDK 점검. 실제 데모 흐름(홈 → 경고등 찾기 → ...)은 이 위에 만듭니다. */
@Composable
fun SdkCheckScreen(items: List<CheckItem>, onVehicle: () -> Unit, onSpeed: () -> Unit, onTts: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Bg).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("플레오스 가드 · SDK 점검", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("로컬 Pleos 에뮬레이터 2.1.0에서 공개 SDK가 실제로 붙는지 확인합니다", color = Muted, fontSize = 22.sp)
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
        Text(label, color = color, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(72.dp))
        Column {
            Text(item.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            if (item.detail.isNotEmpty()) Text(item.detail, color = Muted, fontSize = 22.sp)
        }
    }
}

@Composable
private fun BigButton(text: String, modifier: Modifier, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = modifier.height(88.dp), shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Surf, contentColor = Color.White)) {
        Text(text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}
