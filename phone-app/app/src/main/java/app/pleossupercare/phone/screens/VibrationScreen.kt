package app.pleossupercare.phone.screens

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pleossupercare.phone.Controller
import app.pleossupercare.phone.data.Kind
import app.pleossupercare.phone.data.Prefs
import app.pleossupercare.phone.data.ReqCtx
import app.pleossupercare.phone.logic.VibrationAnalyzer
import app.pleossupercare.phone.logic.VibrationResult
import app.pleossupercare.phone.sensing.MotionData
import app.pleossupercare.phone.sensing.MotionRecorder
import app.pleossupercare.phone.sensing.VibrationReplay
import app.pleossupercare.phone.ui.BarChart
import app.pleossupercare.phone.ui.BottomButtons
import app.pleossupercare.phone.ui.Btn
import app.pleossupercare.phone.ui.C
import app.pleossupercare.phone.ui.GIcon
import app.pleossupercare.phone.ui.Gi
import app.pleossupercare.phone.ui.GuideCard
import app.pleossupercare.phone.ui.LineChart
import app.pleossupercare.phone.ui.MetricCell
import app.pleossupercare.phone.ui.PhoneFrame
import app.pleossupercare.phone.ui.Pill
import app.pleossupercare.phone.ui.R16
import app.pleossupercare.phone.ui.SectionLabel
import app.pleossupercare.phone.ui.Small
import app.pleossupercare.phone.ui.TitleL
import app.pleossupercare.phone.ui.TopBar
import app.pleossupercare.phone.ui.ts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

private enum class VibPhase { READY, COUNTDOWN, RECORDING, RESULT }

/** 폰 센서 조사 4.4절: 동승석 바닥(1순위) · 가운데 콘솔(2순위) · 뒷좌석 바닥(뒤쪽이 의심될 때). */
private val PLACES = listOf("조수석 바닥" to Gi.Seat, "가운데 콘솔" to Gi.Package, "뒷좌석 바닥" to Gi.CarRear)
private val DURATIONS = listOf("10초" to null, "20초" to null, "30초" to null)

@Composable
fun VibrationScreen(c: Controller, ctx: ReqCtx?) = PhoneFrame {
    TopBar("진동 기록", onBack = { c.back() }) { app.pleossupercare.phone.ui.HonestyBadge("실험 기능") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val main = remember { Handler(Looper.getMainLooper()) }
    val stop = remember { AtomicBoolean(false) }
    val emulator = remember { Prefs.isEmulator() }
    var phase by remember { mutableStateOf(VibPhase.READY) }
    var place by remember { mutableStateOf(PLACES[0].first) }
    var part by remember { mutableStateOf(ctx?.partHint?.takeIf { h -> PARTS.any { it.first == h } }) }
    var duration by remember { mutableStateOf("20초") }
    var replay by remember { mutableStateOf(emulator) }
    var countdown by remember { mutableIntStateOf(3) }
    var elapsed by remember { mutableLongStateOf(0L) }
    val live = remember { mutableStateListOf<Float>() }
    var data by remember { mutableStateOf<MotionData?>(null) }
    var result by remember { mutableStateOf<VibrationResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    val totalMs = duration.removeSuffix("초").toLong() * 1000

    fun start() {
        error = null; live.clear(); stop.set(false); elapsed = 0
        scope.launch {
            try {
                if (!replay) {
                    phase = VibPhase.COUNTDOWN
                    for (k in 3 downTo 1) { countdown = k; delay(1000); if (stop.get()) { phase = VibPhase.READY; return@launch } }
                }
                phase = VibPhase.RECORDING
                val onLive: (Long, Double) -> Unit = { el, g -> main.post { elapsed = el; live += g.toFloat() } }
                val d = if (replay) VibrationReplay.replay(VibrationReplay.load(context), totalMs, { stop.get() }, onLive)
                else MotionRecorder(context).record(totalMs, { stop.get() }, onLive)
                if (d.n < 64) { error = "표본이 너무 적습니다 · 센서를 확인하세요"; phase = VibPhase.READY; return@launch }
                result = withContext(Dispatchers.Default) { VibrationAnalyzer.analyze(d.t, d.x, d.y, d.z, d.n) }
                data = d
                phase = VibPhase.RESULT
            } catch (e: Exception) {
                error = e.message ?: "센서 오류"
                phase = VibPhase.READY
            }
        }
    }

    fun send() {
        val d = data ?: return
        val res = result ?: return
        sending = true
        // 에뮬레이터의 가상 센서는 진짜 진동을 만들지 못하므로, 재생이 아니어도 에뮬레이터 기록은 시험 입력으로 보냅니다.
        val test = d.replay || emulator
        val payload = c.basePayload(Kind.VIBRATION, d.startedAt, res.durationMs, place, part ?: ctx?.partHint, ctx, null, testInput = test)
        val m = payload.getJSONObject("metrics")
        m.put("rmsG", r4(res.rmsG)).put("peakG", r4(res.peakG)).put("dominantHz", r2(res.dominantHz)).put("spectrumMaxHz", r1(res.spectrumMaxHz))
            .put("sampleHz", r1(res.sampleHz)).put("lowQuality", if (res.lowQuality) 1 else 0).put("gridHz", res.gridHz)
            .put("band1", r4(res.band1)).put("band2", r4(res.band2))
            .put("samples", res.samples).put("windows", res.windows).put("movedWindows", res.movedWindows).put("gappedWindows", res.gappedWindows)
            .put("gravityXG", r2(res.gravityX)).put("gravityYG", r2(res.gravityY)).put("gravityZG", r2(res.gravityZ)).put("tiltDeg", r1(res.tiltDeg))
        res.band3?.let { m.put("band3", r4(it)) }
        res.peaks.forEachIndexed { i, p -> m.put("peak${i + 1}Hz", r2(p.hz)).put("peak${i + 1}G", r4(p.g)).put("peak${i + 1}Ratio", r1(p.ratio)) }
        d.gyroRmsDps?.let { m.put("gyroRmsDps", r2(it)) }
        payload.put("spectrum", JSONArray(res.spectrum.map { r4(it.toDouble()) })).put("spectrumHz", JSONArray(res.spectrumHz.map { r2(it.toDouble()) }))
            .put("spectrumUnit", "g")
            .put("series", JSONArray(res.series.map { r4(it.toDouble()) })).put("seriesStepMs", res.seriesStepMs).put("seriesUnit", "g")
        payload.put("labels", JSONObject().put("quality", if (test) "TEST_INPUT" else res.quality).put("analysisQuality", res.quality)
            .put("sensor", d.sensorName).put("feature", "실험").put("bands", "band1 1-5Hz, band2 5-20Hz, band3 20-80Hz (RMS, g)"))
        val summary = "진동(실험) · ${hzText(res.dominantHz)}Hz · ${fmt(res.rmsG, 3)}g · $place"
        payload.put("summary", summary)
        data = null // 원본 표본은 보내지 않고 버립니다.
        c.submit(Kind.VIBRATION, payload, summary, null, ctx)
    }

    when (phase) {
        VibPhase.READY -> {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                RequestBanner(ctx)
                GuideCard(Gi.Vibrate, C.Purple, "조수석 바닥이나 가운데 콘솔에 폰을 올려 두세요", "운전석 발밑 · 핸들 · 송풍구 · 유리 거치대는 피하세요")
                Note("출발 전에 놓고 손대지 않습니다 · 일정한 속도로 달리는 동안 10~30초")
                SectionLabel("놓은 곳")
                ChipGroup(PLACES, place, C.Purple) { place = it }
                SectionLabel("재는 시간")
                ChipGroup(DURATIONS, duration, C.Purple) { duration = it }
                SectionLabel("어디서 떨리나요")
                ChipGroup(PARTS, part, C.Purple) { part = it }
                Spacer(Modifier.height(12.dp))
                ReplayRow(replay, emulator) { replay = !replay }
                Badges("실험 기능", "폰 기종 · 놓는 자리에 따라 달라짐", "원인 판단 아님")
                error?.let { Note(it, C.Amber) }
            }
            BottomButtons(null, Btn(if (replay) "시험 입력 재생" else "기록 시작", Gi.Play, { start() }), rightColor = C.Purple)
        }
        VibPhase.COUNTDOWN -> {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("$countdown", style = TitleL.copy(fontSize = 96.sp, color = C.Purple))
                Text("폰을 내려놓고 손을 떼세요", style = ts(20.sp, C.Fg, FontWeight.SemiBold))
                Text(place, style = Small)
            }
            BottomButtons(null, Btn("취소", Gi.X, { stop.set(true) }), rightColor = C.Amber)
        }
        VibPhase.RECORDING -> {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(120.dp).clip(Pill).background(C.Purple.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { GIcon(Gi.Vibrate, C.Purple, 64.dp) }
                Spacer(Modifier.height(16.dp))
                val left = ((totalMs - elapsed + 999) / 1000).coerceAtLeast(0)
                Text("${left}초", style = TitleL.copy(fontSize = 56.sp))
                Text(if (replay) "시험 입력 재생 중" else "재는 중 · 폰을 만지지 마세요", style = Small)
                Spacer(Modifier.height(16.dp))
                val hi = ((live.maxOrNull() ?: 0.05f) * 1.2f).coerceAtLeast(0.02f)
                LineChart(live.toList(), 0f, hi, C.Purple, height = 110.dp, desc = "지금까지의 떨림 크기")
                if (replay) app.pleossupercare.phone.ui.HonestyBadge("시험 입력", Modifier.padding(top = 10.dp))
            }
            BottomButtons(null, Btn("멈추기", Gi.CircleStop, { stop.set(true) }), rightColor = C.Amber)
        }
        VibPhase.RESULT -> {
            val res = result!!
            val test = (data?.replay ?: false) || emulator
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                RequestBanner(ctx)
                VibResultBody(res, test)
            }
            BottomButtons(
                Btn("다시", Gi.Refresh, { result = null; data = null; phase = VibPhase.READY }),
                Btn("차로 보내기", Gi.Send, { send() }, enabled = !sending), rightColor = C.Purple,
            )
        }
    }
}

@Composable
private fun ColumnScope.VibResultBody(res: VibrationResult, test: Boolean) {
    if (test) Badges("실험 기능", "시험 입력", "원인 판단 아님") else Badges("실험 기능", "폰 측정 · 참고용", "원인 판단 아님")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCell("흔들림(RMS)", fmt(res.rmsG, 3), Modifier.weight(1f), "g")
        MetricCell("가장 큰 값", fmt(res.peakG, 3), Modifier.weight(1f), "g")
        MetricCell("가장 센 떨림", hzText(res.dominantHz), Modifier.weight(1f), "Hz")
    }
    if (res.dominantHz > 0) Text("${hzText(res.dominantHz)}Hz 떨림이 가장 컸습니다", style = ts(17.sp, C.Fg, FontWeight.Medium), modifier = Modifier.padding(top = 8.dp))
    when (res.quality) {
        "LOW_RATE" -> Warn("기록 품질 낮음 · 표본이 1초에 ${fmt(res.sampleHz, 0)}개뿐입니다(100개 밑)")
        "MOVED" -> Warn("폰이 움직인 구간 ${res.movedWindows}개를 뺐습니다 · 단단한 바닥에 두고 다시 재면 좋습니다")
        "GAPPED" -> Warn("센서 값이 끊긴 구간 ${res.gappedWindows}개를 뺐습니다")
    }
    SectionLabel("박자별 크기")
    val hi = ((res.spectrum.maxOrNull() ?: 0.01f) * 1.15f).coerceAtLeast(0.001f)
    val mark = (res.dominantHz / res.spectrumMaxHz * res.spectrum.size).toInt().takeIf { res.dominantHz > 0 && it in res.spectrum.indices }
    BarChart(res.spectrum, 0f, hi, C.Purple, mark = mark, leftLabel = "0Hz", rightLabel = "${fmt(res.spectrumMaxHz, 0)}Hz", desc = "떨림 스펙트럼")
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCell("1~5Hz", fmt(res.band1, 3), Modifier.weight(1f), "g")
        MetricCell("5~20Hz", fmt(res.band2, 3), Modifier.weight(1f), "g")
        MetricCell("20~80Hz", res.band3?.let { fmt(it, 3) } ?: "-", Modifier.weight(1f), "g")
    }
    if (res.peaks.isNotEmpty()) Note("센 박자: " + res.peaks.joinToString(" · ") { "${hzText(it.hz)}Hz" })
    SectionLabel("시간에 따른 크기")
    val shi = ((res.series.maxOrNull() ?: 0.01f) * 1.2f).coerceAtLeast(0.001f)
    LineChart(res.series, 0f, shi, C.Purple, leftLabel = "0초", rightLabel = "${(res.durationMs + 500) / 1000}초", desc = "시간에 따른 떨림 크기")
    Note("표본 ${fmt(res.sampleHz, 0)}Hz(분석 격자 ${res.gridHz}Hz) · 2.56초 창 ${res.windows - res.movedWindows - res.gappedWindows}개 · 기울기 ${fmt(res.tiltDeg, 0)}°")
}

@Composable
private fun ReplayRow(on: Boolean, emulator: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(R16).background(C.Surf).border(1.dp, if (on) C.Purple else C.Line, R16)
            .clickable(role = Role.Switch, onClick = onToggle).padding(14.dp).semantics { contentDescription = "시험 입력 재생" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("시험 입력 재생", style = ts(17.sp, C.Fg, FontWeight.SemiBold))
            Text(if (emulator) "에뮬레이터는 진짜 진동을 만들지 못합니다 · 넣어 둔 14Hz 합성 파일을 흘려보냅니다"
                else "넣어 둔 14Hz 합성 파일을 흘려보냅니다 · 끄면 실제 센서로 잽니다", style = Small)
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(56.dp).height(32.dp).clip(Pill).background(if (on) C.Purple else C.Surf2), contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.padding(4.dp).size(24.dp).clip(Pill).background(if (on) C.Dark else C.Mut))
        }
    }
}
