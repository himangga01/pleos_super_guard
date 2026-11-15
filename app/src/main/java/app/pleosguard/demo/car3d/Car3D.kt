package app.pleosguard.demo.car3d

import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.pleosguard.demo.R
import app.pleosguard.demo.ui.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.hypot

/** 부위마다 쓰는 아이콘. 부위 칩 · 3D 위 표시 · 증상 패널이 같은 아이콘을 씁니다. */
fun CarPart.icon(): Gi = when (this) {
    CarPart.FRONT -> Gi.CarFront
    CarPart.FRONT_TIRE, CarPart.REAR_TIRE -> Gi.Wheel
    CarPart.DOOR -> Gi.Door
    CarPart.REAR -> Gi.CarRear
    CarPart.CABIN -> Gi.Seat
}

/** 3D 보기의 카메라 상태. 화면이 들고 있다가 부위 칩을 누르면 [flyTo]로 그 부위 쪽으로 돌립니다. */
class CarViewState(start: Orbit) {
    var orbit by mutableStateOf(start)
    internal var job: Job? = null
    internal var scope: kotlinx.coroutines.CoroutineScope? = null
    /** false면(주행 중 등 손짓을 막은 때) 카메라를 움직이는 대신 바로 옮깁니다. */
    internal var animate = true

    fun flyTo(to: Orbit) {
        val s = scope ?: run { orbit = to; return }
        if (!animate) { job?.cancel(); orbit = to; return }
        job?.cancel()
        val from = orbit
        job = s.launch {
            val a = Animatable(0f)
            a.animateTo(1f, tween(650, easing = FastOutSlowInEasing)) { orbit = lerp(from, to, value.toDouble()) }
        }
    }

    companion object {
        fun lerp(a: Orbit, b: Orbit, t: Double): Orbit {
            val dy = ((b.yawDeg - a.yawDeg + 540.0) % 360.0) - 180.0
            fun l(x: Double, y: Double) = x + (y - x) * t
            return Orbit(a.yawDeg + dy * t, l(a.pitchDeg, b.pitchDeg), l(a.zoom, b.zoom),
                V3(l(a.target.x, b.target.x), l(a.target.y, b.target.y), l(a.target.z, b.target.z)))
        }
    }
}

@Composable
fun rememberCarViewState(start: Orbit): CarViewState {
    val st = remember { CarViewState(start) }
    st.scope = rememberCoroutineScope()
    return st
}

/** 미리 그린 그림(같은 모델 · 같은 카메라). 그래픽이 없는 JVM 시험과 엔진을 켜는 첫 순간에 씁니다. */
private const val SNAP_ASPECT = 1200.0 / 960.0

/**
 * 돌리고 고를 수 있는 3D 아반떼.
 * - 끌면 돌고, 두 손가락으로 벌리면 커집니다([interactive]가 false면 고정 · 주행 중 등).
 * - 누르면 그 자리의 부위를 [onSelect]로 알립니다. 부위 위에는 아이콘 표시가 따라 다닙니다.
 * - [mode]가 실내면 덮개를 반투명으로 바꿔 좌석 · 운전대 · 화면이 보입니다.
 * - [state]가 있으면(보호자 알림) 탑승 · 안전벨트 · 문 · 에어백을 모델 위에 칠합니다.
 */
@Composable
fun Car3D(
    view: CarViewState,
    modifier: Modifier = Modifier,
    mode: CarMode = CarMode.EXTERIOR,
    selected: CarPart? = null,
    onSelect: ((CarPart?) -> Unit)? = null,
    state: CarState? = null,
    interactive: Boolean = true,
    markers: Boolean = true,
    markerSize: Dp = 52.dp,
) {
    val ctx = LocalContext.current
    view.animate = interactive
    var eng by remember { mutableStateOf<CarEngine?>(null) }
    val live = remember { CarEngine.available() }
    if (live) LaunchedEffect(Unit) { eng = CarEngine.getOrNull(ctx) }
    val e = eng
    BoxWithConstraints(modifier.clip(R24)) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }; val hPx = with(density) { maxHeight.toPx() }
        if (wPx <= 0f || hPx <= 0f) return@BoxWithConstraints
        // 그림 영역: 엔진이 첫 그림을 그렸으면 상자 전체, 아니면 미리 그린 그림이 맞춰 들어간 자리
        var drawn by remember(e) { mutableStateOf(false) }
        val snap = e == null || !drawn
        val (rx, ry, rw, rh) = if (!snap) listOf(0f, 0f, wPx, hPx) else {
            val k = minOf(wPx / SNAP_ASPECT.toFloat(), hPx)
            val w = k * SNAP_ASPECT.toFloat(); listOf((wPx - w) / 2, (hPx - k) / 2, w, k)
        }
        val orbit = if (snap) (if (mode == CarMode.INTERIOR) Orbit.INTERIOR else Orbit.HOME) else view.orbit
        val frame = remember(orbit, rw, rh) { CameraFrame(orbit, (rw / rh).toDouble()) }

        val liveFrame = remember(view.orbit, wPx, hPx) { CameraFrame(view.orbit, (wPx / hPx).toDouble()) }
        if (e != null) {
            SideEffect {
                e.setMode(mode); e.setSelected(selected); e.setState(state); e.setCamera(liveFrame)
            }
            AndroidView(
                factory = { c -> TextureView(c).also { tv -> e.attach(tv) { drawn = true } } },
                modifier = Modifier.fillMaxSize(),
                onRelease = { tv -> e.detach(tv) },
            )
        }
        if (snap) {
            Image(
                painterResource(if (mode == CarMode.INTERIOR) R.drawable.car3d_interior else R.drawable.car3d_home),
                contentDescription = "디 올 뉴 아반떼 3D 모델",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }
        // 손짓: 끌기 = 돌리기, 벌리기 = 크게, 누르기 = 부위 고르기
        // 손짓 하나로 처리합니다: 손가락이 조금(터치 슬롭) 넘게 움직이면 돌리기, 두 손가락은 크게 · 작게,
        // 거의 움직이지 않고 떼면 누르기(부위 고르기). 돌리는 동안에는 부위가 골라지지 않습니다.
        // 카메라가 바뀔 때마다 손짓 처리기가 다시 시작되면 끌기가 끊기므로, 바뀌는 값은 최신값으로 읽습니다.
        val canTurnS = rememberUpdatedState(interactive && !snap)
        val frameS = rememberUpdatedState(frame)
        val rectS = rememberUpdatedState(floatArrayOf(rx, ry, rw, rh))
        val engS = rememberUpdatedState(if (snap) null else e)
        val selectS = rememberUpdatedState(onSelect)
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var total = Offset.Zero
                var dragging = false
                while (true) {
                    val ev = awaitPointerEvent()
                    if (ev.changes.none { it.pressed }) break
                    if (!canTurnS.value) continue
                    if (ev.changes.count { it.pressed } >= 2) {
                        dragging = true
                        val z = ev.calculateZoom()
                        if (z != 1f) { view.job?.cancel(); view.orbit = view.orbit.copy(zoom = view.orbit.zoom / z).clamped() }
                        ev.changes.forEach { it.consume() }
                        continue
                    }
                    val ch = ev.changes.first()
                    val d = ch.positionChange()
                    total += d
                    if (!dragging && total.getDistance() > viewConfiguration.touchSlop) dragging = true
                    if (dragging) {
                        view.job?.cancel()
                        val o = view.orbit
                        view.orbit = o.copy(yawDeg = o.yawDeg - d.x * 0.32, pitchDeg = o.pitchDeg + d.y * 0.24).clamped()
                        ch.consume()
                    }
                }
                val sel = selectS.value
                if (!dragging && sel != null) {
                    val p = down.position
                    val live = engS.value
                    if (live != null) live.pick(p.x, p.y) { part -> sel(part) }
                    else {
                        // 미리 그린 그림: 가장 가까운 표시점(손가락 한 마디 안)
                        val f = frameS.value; val r = rectS.value
                        val near = f.visibleAnchors().filter { it.second > 0.05 }.mapNotNull { (a, _) ->
                            f.toScreen(a.pos)?.let { (sx, sy) -> a.part to hypot(r[0] + sx.toFloat() * r[2] - p.x, r[1] + sy.toFloat() * r[3] - p.y) }
                        }.minByOrNull { it.second }
                        if (near != null && near.second < 90f * density.density) sel(near.first)
                    }
                }
            }
        })
        if (markers && onSelect != null) {
            frame.visibleAnchors().forEach { (a, facing) ->
                if (facing < -0.02) return@forEach
                val sp = frame.toScreen(a.pos) ?: return@forEach
                val x = rx + sp.first.toFloat() * rw; val y = ry + sp.second.toFloat() * rh
                if (x < 0 || y < 0 || x > wPx || y > hPx) return@forEach
                val alpha = ((facing + 0.02) / 0.30).coerceIn(0.0, 1.0).toFloat()
                PartMarker(a.part, a.part == selected, markerSize, alpha,
                    Modifier.offset(with(density) { x.toDp() } - markerSize / 2, with(density) { y.toDp() } - markerSize / 2)) { onSelect(a.part) }
            }
        }
    }
}

/** 3D 위 부위 표시: 둥근 아이콘. 고르면 민트로 채워지고 이름표가 붙습니다. */
@Composable
private fun PartMarker(part: CarPart, on: Boolean, size: Dp, alpha: Float, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.alpha(alpha)) {
        Box(
            Modifier.size(size).clip(Pill).background(if (on) C.Mint else C.Bg.copy(alpha = 0.72f)).border(3.dp, C.Mint, Pill)
                .clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = part.label },
            contentAlignment = Alignment.Center,
        ) { GIcon(part.icon(), if (on) C.Dark else C.Mint, size * 0.56f) }
        if (on) Box(
            Modifier.offset(x = size + 6.dp, y = (size - 40.dp) / 2).clip(R16).background(C.Mint).padding(horizontal = 12.dp, vertical = 4.dp),
        ) { Text(part.label, style = ts(24.sp, C.Dark, FontWeight.Bold), maxLines = 1) }
    }
}

/** 부위 칩 한 줄: 아이콘 + 짧은 이름. 누르면 그 부위를 고르고 3D가 그쪽으로 돕니다. */
@Composable
fun PartChips(selected: CarPart?, onPick: (CarPart) -> Unit, modifier: Modifier = Modifier, height: Dp = 80.dp, iconSize: Dp = 34.dp) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CarPart.entries.forEach { p ->
            val on = p == selected
            Column(
                Modifier.weight(1f).height(height).clip(R16).background(if (on) C.OnMint else C.Surf2)
                    .then(if (on) Modifier.border(2.dp, C.Mint, R16) else Modifier)
                    .clickable(role = Role.Button) { onPick(p) },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                GIcon(p.icon(), if (on) C.Mint else C.Mut, iconSize)
                Text(p.label, style = ts(22.sp, if (on) C.Mint else C.Fg, if (on) FontWeight.Bold else FontWeight.Medium), maxLines = 1)
            }
        }
    }
}

/** 3D 보기 위 작은 둥근 단추(실내 보기 · 처음 자리). */
@Composable
fun ViewToggle(icon: Gi, label: String, on: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(52.dp).clip(Pill).background(if (on) C.OnMint else C.Surf2).border(2.dp, if (on) C.Mint else C.Line, Pill)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GIcon(icon, if (on) C.Mint else C.Mut, 28.dp); Spacer(Modifier.width(8.dp))
        Text(label, style = ts(22.sp, if (on) C.Mint else C.Fg, FontWeight.SemiBold), maxLines = 1)
    }
}
