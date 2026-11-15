package app.pleossupercare.phone.screens

import android.Manifest
import android.os.Handler
import android.os.Looper
import android.util.Base64
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
import androidx.compose.runtime.mutableDoubleStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pleossupercare.phone.Controller
import app.pleossupercare.phone.data.Kind
import app.pleossupercare.phone.data.ReqCtx
import app.pleossupercare.phone.logic.NoiseAnalyzer
import app.pleossupercare.phone.logic.NoiseResult
import app.pleossupercare.phone.sensing.AacEncoder
import app.pleossupercare.phone.sensing.AudioRecorder
import app.pleossupercare.phone.ui.BarChart
import app.pleossupercare.phone.ui.BottomButtons
import app.pleossupercare.phone.ui.Btn
import app.pleossupercare.phone.ui.C
import app.pleossupercare.phone.ui.GIcon
import app.pleossupercare.phone.ui.Gi
import app.pleossupercare.phone.ui.GuideCard
import app.pleossupercare.phone.ui.LevelMeter
import app.pleossupercare.phone.ui.LineChart
import app.pleossupercare.phone.ui.MetricCell
import app.pleossupercare.phone.ui.Panel
import app.pleossupercare.phone.ui.PhoneFrame
import app.pleossupercare.phone.ui.Pill
import app.pleossupercare.phone.ui.R16
import app.pleossupercare.phone.ui.SectionLabel
import app.pleossupercare.phone.ui.Small
import app.pleossupercare.phone.ui.TitleL
import app.pleossupercare.phone.ui.TopBar
import app.pleossupercare.phone.ui.ts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

private enum class NoisePhase { READY, RECORDING, RESULT }

/** 앞 1초는 버리고(버튼 소리 · 손 떨림) 10초를 분석합니다(폰 센서 조사 7.3절). */
private const val SKIP_MS = 1_000L
private const val RECORD_MS = 10_000L

@Composable
fun NoiseScreen(c: Controller, ctx: ReqCtx?) = PhoneFrame {
    TopBar("소리 기록", onBack = { c.back() })
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ask = rememberPermission(Manifest.permission.RECORD_AUDIO)
    val main = remember { Handler(Looper.getMainLooper()) }
    val stop = remember { AtomicBoolean(false) }
    var phase by remember { mutableStateOf(NoisePhase.READY) }
    var part by remember { mutableStateOf(ctx?.partHint?.takeIf { h -> PARTS.any { it.first == h } }) }
    var place by remember { mutableStateOf("소리 가까이") }
    var keepAudio by remember { mutableStateOf(false) }
    var denied by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var level by remember { mutableDoubleStateOf(-100.0) }
    val live = remember { mutableStateListOf<Float>() }
    var rec by remember { mutableStateOf<AudioRecorder.Recording?>(null) }
    var result by remember { mutableStateOf<NoiseResult?>(null) }
    var sending by remember { mutableStateOf(false) }

    fun start() = ask({
        denied = false; error = null; live.clear(); stop.set(false); elapsed = 0; phase = NoisePhase.RECORDING
        scope.launch {
            try {
                val r = AudioRecorder(context).record(SKIP_MS + RECORD_MS, { stop.get() }) { el, db ->
                    main.post { elapsed = el; level = db; if (el > SKIP_MS) live += db.toFloat() }
                }
                val from = (r.sampleRate * SKIP_MS / 1000).toInt().coerceAtMost(r.length / 2)
                if (r.length - from < r.sampleRate * 2) { error = "너무 짧습니다 · 2초 넘게 기록해 주세요"; phase = NoisePhase.READY; return@launch }
                result = withContext(Dispatchers.Default) { NoiseAnalyzer.analyze(r.pcm, r.sampleRate, from, r.length - from) }
                rec = r
                phase = NoisePhase.RESULT
            } catch (e: Exception) {
                error = e.message ?: "마이크 오류"
                phase = NoisePhase.READY
            }
        }
    }, { denied = true })

    fun send() {
        val r = rec ?: return
        val res = result ?: return
        sending = true
        scope.launch {
            val from = (r.sampleRate * SKIP_MS / 1000).toInt().coerceAtMost(r.length / 2)
            val startedAt = r.startedAt + SKIP_MS
            val partHint = part ?: ctx?.partHint
            val payload = c.basePayload(Kind.NOISE, startedAt, res.durationMs, place, partHint, ctx, null, testInput = false)
            val m = payload.getJSONObject("metrics")
            m.put("avgDb", r1(res.avgDb)).put("peakDb", r1(res.peakDb)).put("dominantHz", r1(res.dominantHz)).put("spectrumMaxHz", res.spectrumMaxHz)
                .put("samplePeakDb", r1(res.samplePeakDb)).put("l10Db", r1(res.l10Db)).put("l90Db", r1(res.l90Db)).put("centroidHz", r1(res.centroidHz))
                .put("lowPct", r1(res.lowPct)).put("midPct", r1(res.midPct)).put("highPct", r1(res.highPct)).put("sampleRateHz", res.sampleRate)
                .put("clipPct", r1(res.clipPct)).put("speechPct", r1(res.speechPct)).put("silent", if (res.silent) 1 else 0)
                .put("tooClose", if (res.samplePeakDb > NoiseAnalyzer.TOO_CLOSE_DB) 1 else 0)
            res.peaksHz.take(3).forEachIndexed { i, hz -> m.put("peak${i + 1}Hz", r1(hz)) }
            res.repeatSec?.let { m.put("repeatSec", r2(it)) }
            payload.put("spectrum", JSONArray(res.spectrum.map { r1(it.toDouble()) })).put("spectrumHz", JSONArray(res.spectrumHz.map { r1(it.toDouble()) }))
                .put("spectrumUnit", "dBFS")
                .put("series", JSONArray(res.series.map { r1(it.toDouble()) })).put("seriesStepMs", res.seriesStepMs).put("seriesUnit", "dBFS")
                .put("thirdOctave", JSONArray(res.thirdOctave.map { r1(it.toDouble()) })).put("thirdOctaveHz", JSONArray(res.thirdOctaveHz.map { it.toDouble() }))
            payload.put("labels", JSONObject().put("audioSource", r.source).put("pitch", NoiseAnalyzer.pitchLabel(res.dominantHz))
                .put("reliableBand", if (r.source == "UNPROCESSED") "100-7000Hz" else "100-4000Hz").put("unit", "dBFS 상대 크기(실제 소음 크기 아님)"))
            val summary = "소리 · 평균 ${fmt(res.avgDb)} dBFS · 가장 센 높이 ${hzText(res.dominantHz)}Hz"
            payload.put("summary", summary)
            // 원본 소리: 사용자가 켰고, 말소리가 없고, 무음이 아닐 때만(기본 꺼짐).
            if (keepAudio && !res.speechLikely && !res.silent) {
                val f = File(context.cacheDir, "clip-${System.currentTimeMillis()}.m4a")
                val ok = withContext(Dispatchers.Default) { AacEncoder.encode(r.pcm, from, r.length - from, r.sampleRate, f) }
                if (ok) payload.put("audioM4aBase64", Base64.encodeToString(f.readBytes(), Base64.NO_WRAP))
                f.delete()
            }
            rec = null // 원본 표본은 보내지 않고 버립니다.
            c.submit(Kind.NOISE, payload, summary, null, ctx)
        }
    }

    when (phase) {
        NoisePhase.READY -> {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                RequestBanner(ctx)
                GuideCard(Gi.AudioLines, C.Mint, "소리 나는 곳에 폰을 가까이", "창문을 닫고 음악은 끕니다 · 주행 중에는 동승자가 기록합니다")
                SectionLabel("어디서 나나요")
                ChipGroup(PARTS, part) { part = it }
                SectionLabel("폰 위치")
                ChipGroup(listOf("소리 가까이" to Gi.Smartphone, "운전석" to Gi.Seat, "차 밖" to Gi.CarFront), place) { place = it }
                Spacer(Modifier.height(12.dp))
                ConsentRow(keepAudio) { keepAudio = !keepAudio }
                Badges("상대 크기(dBFS)", "실제 소음 크기 아님", "원인 판단 아님")
                if (denied) PermissionNotice("소리를 기록하려면 마이크 권한이 필요합니다") { start() }
                error?.let { Note(it, C.Amber) }
            }
            BottomButtons(null, Btn("기록 시작 · 10초", Gi.Mic, { start() }))
        }
        NoisePhase.RECORDING -> {
            Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                val t = ((level + 90) / 90).toFloat().coerceIn(0f, 1f)
                Box(Modifier.size((120 + 60 * t).dp).clip(Pill).background(C.Mint.copy(alpha = 0.10f + 0.25f * t)), contentAlignment = Alignment.Center) {
                    GIcon(Gi.Mic, C.Mint, 64.dp)
                }
                Spacer(Modifier.height(20.dp))
                val left = ((SKIP_MS + RECORD_MS - elapsed) / 1000.0).roundToInt().coerceAtLeast(0)
                Text(if (elapsed < SKIP_MS) "준비" else "${left}초", style = TitleL.copy(fontSize = 56.sp))
                Text(if (elapsed < SKIP_MS) "앞 1초는 버립니다" else "기록 중", style = Small)
                Spacer(Modifier.height(16.dp))
                LevelMeter(t, C.Mint)
                Text("${fmt(level, 0)} dBFS", style = ts(18.sp, C.Fg, FontWeight.SemiBold), modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(12.dp))
                LineChart(live.toList(), -90f, 0f, C.Mint, height = 90.dp, desc = "지금까지의 소리 크기")
            }
            BottomButtons(null, Btn("멈추기", Gi.CircleStop, { stop.set(true) }), rightColor = C.Amber)
        }
        NoisePhase.RESULT -> {
            val res = result!!
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                RequestBanner(ctx)
                NoiseResultBody(res, rec?.source ?: "")
                Spacer(Modifier.height(8.dp))
                Text(
                    when {
                        keepAudio && res.speechLikely -> "말소리가 섞여 원본 소리는 보내지 않습니다 · 숫자 결과만 보냅니다"
                        keepAudio && !res.silent -> "원본 소리 10초도 함께 보냅니다(약 80KB)"
                        else -> "숫자 결과만 보냅니다 · 원본 소리는 폰에서 지웁니다"
                    },
                    style = Small,
                )
            }
            BottomButtons(
                Btn("다시", Gi.Refresh, { result = null; rec = null; phase = NoisePhase.READY }),
                Btn("차로 보내기", Gi.Send, { send() }, enabled = !sending),
            )
        }
    }
}

@Composable
private fun ColumnScope.NoiseResultBody(res: NoiseResult, source: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCell("평균", fmt(res.avgDb), Modifier.weight(1f), "dBFS")
        MetricCell("가장 큰 순간", fmt(res.peakDb), Modifier.weight(1f), "dBFS")
        MetricCell("가장 센 높이", hzText(res.dominantHz), Modifier.weight(1f), "Hz")
    }
    Badges("상대 크기(dBFS)", "실제 소음 크기 아님", "원인 판단 아님")
    if (res.silent) Warn("소리가 거의 들어오지 않았습니다 · 에뮬레이터는 PC 마이크를 켜야 합니다")
    if (res.samplePeakDb > NoiseAnalyzer.TOO_CLOSE_DB) Warn("너무 가깝습니다(−1dBFS 넘음) · 조금 떼고 다시 기록하세요")
    if (res.speechLikely) Warn("말소리가 섞인 것 같습니다 · 조용할 때 다시 기록하면 좋습니다")
    if (!res.silent) {
        Text("가장 센 높이는 ${hzText(res.dominantHz)}Hz(${NoiseAnalyzer.pitchLabel(res.dominantHz)}) 쪽입니다", style = ts(17.sp, C.Fg, FontWeight.Medium), modifier = Modifier.padding(top = 6.dp))
        res.repeatSec?.let { Text("소리가 약 ${fmt(it, 2)}초마다 되풀이됩니다", style = ts(16.sp, C.Mut), modifier = Modifier.padding(top = 2.dp)) }
    }
    SectionLabel("높이별 크기")
    val hi = (res.spectrum.maxOrNull() ?: -40f) + 3f
    val mark = (res.dominantHz / res.spectrumMaxHz * res.spectrum.size).toInt().takeIf { res.dominantHz > 0 && it in res.spectrum.indices }
    BarChart(res.spectrum, hi - 60f, hi, C.Mint, mark = mark, leftLabel = "0Hz", rightLabel = "${res.spectrumMaxHz / 1000}kHz", desc = "소리 스펙트럼")
    Note("믿을 만한 높이: ${if (source == "UNPROCESSED") "100~7,000Hz" else "100~4,000Hz"} · 그 밖은 있음 · 없음만 참고")
    SectionLabel("시간에 따른 크기")
    val shi = (res.series.maxOrNull() ?: -40f) + 3f
    LineChart(res.series, shi - 50f, shi, C.Mint, leftLabel = "0초", rightLabel = "${(res.durationMs + 500) / 1000}초", desc = "시간에 따른 소리 크기")
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricCell("바탕 소리(L90)", fmt(res.l90Db), Modifier.weight(1f), "dBFS")
        MetricCell("큰 소리(L10)", fmt(res.l10Db), Modifier.weight(1f), "dBFS")
    }
}

@Composable
private fun ConsentRow(on: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(R16).background(C.Surf).border(1.dp, if (on) C.Mint else C.Line, R16)
            .clickable(role = Role.Switch, onClick = onToggle).padding(14.dp).semantics { contentDescription = "원본 소리도 보내기" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("원본 소리 10초도 보내기", style = ts(17.sp, C.Fg, FontWeight.SemiBold))
            Text("기본은 꺼짐 · 말소리가 섞이면 보내지 않습니다", style = Small)
        }
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(56.dp).height(32.dp).clip(Pill).background(if (on) C.Mint else C.Surf2), contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.padding(4.dp).size(24.dp).clip(Pill).background(if (on) C.Dark else C.Mut))
        }
    }
}

@Composable
fun Warn(text: String) {
    Panel(Modifier.padding(top = 8.dp), bg = C.OnAmber, pad = 12.dp, border = C.Amber.copy(alpha = 0.7f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GIcon(Gi.TriangleAlert, C.Amber, 22.dp); Spacer(Modifier.width(10.dp))
            Text(text, style = ts(15.sp, C.Fg, FontWeight.Medium), textAlign = TextAlign.Start)
        }
    }
}

private fun finite(v: Double) = if (v.isNaN() || v.isInfinite()) 0.0 else v
fun r1(v: Double): Double = Math.round(finite(v) * 10) / 10.0
fun r2(v: Double): Double = Math.round(finite(v) * 100) / 100.0
fun r4(v: Double): Double = Math.round(finite(v) * 10000) / 10000.0
