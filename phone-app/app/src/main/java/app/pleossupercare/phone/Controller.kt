package app.pleossupercare.phone

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.pleossupercare.phone.data.Api
import app.pleossupercare.phone.data.CaptureStore
import app.pleossupercare.phone.data.CarRequest
import app.pleossupercare.phone.data.Kind
import app.pleossupercare.phone.data.LocalCapture
import app.pleossupercare.phone.data.Prefs
import app.pleossupercare.phone.data.ReqCtx
import app.pleossupercare.phone.data.optStringOrNull
import app.pleossupercare.phone.logic.PairLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.UUID

sealed interface Screen {
    data object Pair : Screen
    data object QrScan : Screen
    data object Home : Screen
    data object Settings : Screen
    data class Noise(val ctx: ReqCtx?) : Screen
    data class Vibration(val ctx: ReqCtx?) : Screen
    data class Warning(val ctx: ReqCtx?) : Screen
    data class Photo(val ctx: ReqCtx?, val receipt: Boolean) : Screen
}

/** 짝 맺기 화면 상태. Waiting = 번호를 넣었고 차 화면의 [연결]을 기다림. */
sealed interface PairUi {
    data object Idle : PairUi
    data object Busy : PairUi
    data class Waiting(val carId: String) : PairUi
    data class Error(val message: String) : PairUi
}

/** 앱 상태 하나(화면 · 연결 · 요청 · 기록). 앱 프로세스가 살아 있는 동안 유지됩니다(PhoneApp이 가짐). */
class Controller(private val app: Application) {
    val prefs = Prefs(app)
    val api = Api({ prefs.serverUrl }, { prefs.phoneId })
    val store = CaptureStore(File(app.filesDir, "care").apply { mkdirs() })
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var screen by mutableStateOf<Screen>(if (prefs.carId != null) Screen.Home else Screen.Pair)
        private set
    private val stack = mutableStateListOf<Screen>()
    val canGoBack: Boolean get() = stack.isNotEmpty()

    var pairUi by mutableStateOf<PairUi>(prefs.pendingCarId?.let { PairUi.Waiting(it) } ?: PairUi.Idle)
    var pending by mutableStateOf<List<CarRequest>>(emptyList())
        private set
    var reminders by mutableStateOf<List<CarRequest>>(emptyList())
        private set
    var serverOk by mutableStateOf<Boolean?>(null)
        private set
    var recent by mutableStateOf(store.list())
        private set
    var toast by mutableStateOf<String?>(null)
        private set
    /** 짝 맺기가 끝나면 알림 권한(Android 13+)을 묻도록 MainActivity에 알립니다. */
    var askNotifications by mutableStateOf(false)

    @Volatile var foreground = false
    private var polling: Job? = null
    private var tick = 0

    // ---------------------------------------------------------------- 화면 이동
    fun go(s: Screen) { stack.add(screen); screen = s }
    fun back(): Boolean { if (stack.isEmpty()) return false; screen = stack.removeAt(stack.lastIndex); return true }
    fun home() { stack.clear(); screen = if (prefs.carId != null) Screen.Home else Screen.Pair }

    fun showToast(text: String) {
        toast = text
        scope.launch { delay(2800); if (toast == text) toast = null }
    }

    fun openRequest(r: CarRequest) {
        val ctx = ReqCtx(r.id, r.recordId, r.partHint, r.note ?: r.text)
        when (r.captureKind) {
            Kind.NOISE -> go(Screen.Noise(ctx))
            Kind.VIBRATION -> go(Screen.Vibration(ctx))
            Kind.WARNING -> go(Screen.Warning(ctx))
            Kind.PHOTO -> go(Screen.Photo(ctx, false))
            Kind.RECEIPT -> go(Screen.Photo(ctx, true))
            null -> showToast("이 요청은 폰에서 기록할 수 없습니다")
        }
    }

    // ---------------------------------------------------------------- 짝 맺기
    /** QR · 링크(pleoscare://pair?code=…&server=…)나 번호로 연결을 청합니다. */
    fun onPairLink(p: PairLink.Parsed) {
        if (prefs.carId != null) { showToast("이미 차와 연결되어 있습니다 · 설정에서 끊은 뒤 다시"); return }
        home()
        claim(p.code, p.server)
    }

    fun claim(code: String, server: String? = null) {
        if (pairUi == PairUi.Busy) return
        pairUi = PairUi.Busy
        scope.launch {
            val bases = listOfNotNull(server?.trimEnd('/'), prefs.serverUrl).distinct()
            var last: Throwable? = null
            for (b in bases) {
                try {
                    val r = api.post("/api/pair/claim", JSONObject().put("code", code).put("phoneName", prefs.phoneName), base = b)
                    prefs.serverUrl = b
                    prefs.phoneId = r.optString("phoneId")
                    val carId = r.optString("carId")
                    if (r.optBoolean("paired")) finishPairing(carId) else {
                        prefs.pendingCarId = carId
                        pairUi = PairUi.Waiting(carId)
                        startPolling()
                    }
                    return@launch
                } catch (e: IOException) {
                    last = e // 이 주소에 닿지 않으면 다음 주소(QR의 server → 설정의 주소)로
                } catch (e: Exception) {
                    last = e; break
                }
            }
            pairUi = PairUi.Error(Api.describe(last ?: IOException()))
        }
    }

    fun cancelPairing() {
        prefs.clearPairing()
        pairUi = PairUi.Idle
    }

    private fun finishPairing(carId: String) {
        prefs.carId = carId
        prefs.pendingCarId = null
        prefs.pairedAt = System.currentTimeMillis()
        pairUi = PairUi.Idle
        stack.clear()
        screen = Screen.Home
        askNotifications = true
        showToast("차와 연결했습니다")
        scope.launch { pollOnce(carId, true) }
    }

    private suspend fun pollPairing(carId: String) {
        try {
            val s = api.get("/api/pair/status?carId=" + enc(carId))
            serverOk = true
            val samePhone = s.optStringOrNull("phoneId") == prefs.phoneId
            when {
                s.optBoolean("paired") && samePhone -> finishPairing(carId)
                s.optBoolean("rejected") && samePhone -> { prefs.clearPairing(); pairUi = PairUi.Error("차 화면에서 거절했습니다") }
                !s.optBoolean("pending") || !samePhone -> { prefs.clearPairing(); pairUi = PairUi.Error("연결 요청이 사라졌습니다 · 번호를 다시 넣어 주세요") }
            }
        } catch (e: Exception) {
            serverOk = false
        }
    }

    fun unpair() {
        val car = prefs.carId
        scope.launch {
            if (car != null) runCatching { api.post("/api/pair/unpair", JSONObject().put("carId", car)) }
            prefs.clearPairing()
            pending = emptyList(); reminders = emptyList()
            pairUi = PairUi.Idle
            stack.clear(); screen = Screen.Pair
            showToast("연결을 끊었습니다")
        }
    }

    // ---------------------------------------------------------------- 요청 받기(앱이 떠 있는 동안)
    /** 앞에 있으면 2초, 뒤에 있으면 10초마다 서버를 봅니다(폰 센서 조사 8.2절: 기록 대기 화면에서 2초마다). */
    fun startPolling() {
        if (polling?.isActive == true) return
        polling = scope.launch {
            while (isActive) {
                val car = prefs.carId
                val waiting = prefs.pendingCarId
                when {
                    car != null -> pollOnce(car, tick++ % 4 == 0)
                    waiting != null -> pollPairing(waiting)
                }
                delay(if (foreground) 2_000 else 10_000)
            }
        }
    }

    private suspend fun pollOnce(car: String, slow: Boolean) {
        try {
            val r = api.get("/api/requests?carId=${enc(car)}&pending=1")
            serverOk = true
            val arr = r.optJSONArray("requests") ?: JSONArray()
            val list = (0 until arr.length()).map { CarRequest.from(arr.getJSONObject(it)) }
            pending = list.filter { !it.reminder }.sortedByDescending { it.createdAt }
            val rem = list.filter { it.reminder }.sortedByDescending { it.createdAt }
            reminders = rem
            val shown = prefs.notified
            val fresh = rem.filter { it.id !in shown }
            fresh.forEach { Notifier.reminder(app, it) }
            if (fresh.isNotEmpty()) prefs.notified = shown + fresh.map { it.id }
            if (slow) { refreshAcks(car); checkStillPaired(car) }
            retryFailed()
        } catch (e: Exception) {
            serverOk = false
        }
    }

    /** 차가 연결을 끊었거나 다른 폰과 연결했으면 이 폰의 연결을 지웁니다. */
    private suspend fun checkStillPaired(car: String) {
        val s = runCatching { api.get("/api/pair/status?carId=" + enc(car)) }.getOrNull() ?: return
        if (!s.optBoolean("paired") || s.optStringOrNull("phoneId") != prefs.phoneId) {
            prefs.clearPairing()
            pending = emptyList(); reminders = emptyList()
            stack.clear(); screen = Screen.Pair
            pairUi = PairUi.Error("차와 연결이 끊겼습니다 · 다시 연결해 주세요")
        }
    }

    /** 차가 받아 저장한 기록(ack)을 "차에서 확인"으로 바꿉니다. */
    private suspend fun refreshAcks(car: String) {
        val mine = store.list().filter { it.serverId != null && it.status == "sent" }
        if (mine.isEmpty()) return
        val r = runCatching { api.get("/api/captures?carId=${enc(car)}&lite=1") }.getOrNull() ?: return
        val arr = r.optJSONArray("captures") ?: return
        val acked = HashMap<String, Long>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (!o.isNull("ackedAt")) acked[o.optString("id")] = o.optLong("ackedAt")
        }
        var changed = false
        mine.forEach { c -> acked[c.serverId]?.let { t -> store.update(c.localId) { it.copy(status = "acked", ackedAt = t) }; changed = true } }
        if (changed) recent = store.list()
    }

    fun confirmReminder(r: CarRequest) {
        reminders = reminders.filter { it.id != r.id }
        Notifier.cancel(app, r.id)
        scope.launch { runCatching { api.post("/api/requests/${r.id}/done", JSONObject().put("seenBy", "phone")) } }
    }

    // ---------------------------------------------------------------- 기록 보내기
    /** 모든 기록에 공통인 필드(전환 기록 5절). [testInput]이면 note = "시험 입력"(차 앱이 시험 입력 꼬리표를 붙임). */
    fun basePayload(kind: Kind, startedAt: Long, durationMs: Long, placement: String?, partHint: String?, ctx: ReqCtx?, note: String?, testInput: Boolean): JSONObject =
        JSONObject()
            .put("carId", prefs.carId)
            .put("kind", kind.api)
            .put("requestId", ctx?.requestId ?: JSONObject.NULL)
            .put("recordId", ctx?.recordId ?: JSONObject.NULL)
            .put("startedAt", startedAt)
            .put("durationMs", durationMs)
            .put("placement", placement ?: JSONObject.NULL)
            .put("partHint", partHint ?: JSONObject.NULL)
            .put("note", if (testInput) TEST_INPUT else (note?.takeIf { it.isNotBlank() } ?: JSONObject.NULL))
            .put("phoneName", prefs.phoneName)
            .put("metrics", JSONObject())
            .put("spectrum", JSONArray())
            .put("series", JSONArray())
            .put("candidates", JSONArray())
            .apply { if (testInput && !note.isNullOrBlank()) put("userNote", note) }

    fun submit(kind: Kind, payload: JSONObject, summary: String, thumb: ByteArray?, ctx: ReqCtx?) {
        val localId = "lc-" + UUID.randomUUID().toString().take(8)
        val item = LocalCapture(
            localId, kind.api, payload.optLong("startedAt"), payload.optLong("durationMs"), summary, "sending", null, null,
            ctx?.requestId, if (thumb != null) "$localId.jpg" else null, payload.optString("note") == TEST_INPUT,
        )
        store.add(item, payload, thumb)
        recent = store.list()
        if (ctx != null) pending = pending.filter { it.id != ctx.requestId }
        home()
        scope.launch { trySend(localId) }
    }

    /** 서버가 받지 않은(망 오류가 아닌) 기록. 저절로 다시 보내지 않고 사용자가 [다시]를 누를 때만 보냅니다. */
    private val refused = HashSet<String>()

    private suspend fun trySend(localId: String, quiet: Boolean = false): Boolean {
        val payload = store.payload(localId) ?: return false
        payload.put("clockOffsetMs", api.clockOffsetMs ?: JSONObject.NULL)
        return try {
            val r = api.post("/api/captures", payload)
            store.update(localId) { it.copy(status = "sent", serverId = r.optString("id")) }
            store.dropPayload(localId)
            refused -= localId
            payload.optStringOrNull("requestId")?.let { req ->
                runCatching { api.post("/api/requests/$req/done", JSONObject().put("captureId", r.optString("id"))) }
            }
            recent = store.list()
            serverOk = true
            showToast("차로 보냈습니다")
            true
        } catch (e: Exception) {
            store.update(localId) { it.copy(status = "failed") }
            recent = store.list()
            if (e is IOException) serverOk = false else refused += localId
            if (!quiet) showToast("보내지 못했습니다 · " + Api.describe(e))
            false
        }
    }

    private var retrying = false
    private suspend fun retryFailed() {
        if (retrying) return
        retrying = true
        try {
            for (c in store.list().filter { it.status == "failed" && it.localId !in refused }) {
                if (!trySend(c.localId, quiet = true)) break
            }
        } finally {
            retrying = false
        }
    }

    fun retry(localId: String) = scope.launch { trySend(localId) }

    suspend fun health(url: String): String = try {
        val r = api.get("/api/health", base = url)
        "연결됨 · ${r.optString("name")} ${r.optString("version")}"
    } catch (e: Exception) {
        Api.describe(e)
    }

    companion object {
        const val TEST_INPUT = "시험 입력"
        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
    }
}
