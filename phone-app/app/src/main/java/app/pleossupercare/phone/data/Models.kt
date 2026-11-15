package app.pleossupercare.phone.data

import androidx.compose.ui.graphics.Color
import app.pleossupercare.phone.ui.C
import app.pleossupercare.phone.ui.Gi
import org.json.JSONObject

/** 폰 기록 종류(서버 kind와 같은 이름). */
enum class Kind(val api: String, val label: String, val icon: Gi, val color: Color) {
    NOISE("noise", "소리 기록", Gi.AudioLines, C.Mint),
    VIBRATION("vibration", "진동 기록", Gi.Vibrate, C.Purple),
    WARNING("warning_photo", "경고등 찍기", Gi.Engine, C.Amber),
    PHOTO("photo", "사진 기록", Gi.Camera, C.Mint),
    RECEIPT("receipt", "정비 영수증", Gi.Receipt, C.Mint);

    companion object {
        fun of(api: String?) = entries.firstOrNull { it.api == api }
    }
}

/** 차에서 온 요청. [reminder]면 기록 없이 알림만 띄웁니다(trunk_reminder · rear_seat_reminder). */
data class CarRequest(
    val id: String, val carId: String, val kind: String, val recordId: String?, val partHint: String?,
    val note: String?, val text: String?, val createdAt: Long,
) {
    val reminder: Boolean get() = kind == "trunk_reminder" || kind == "rear_seat_reminder"
    val captureKind: Kind? get() = Kind.of(kind)

    companion object {
        fun from(o: JSONObject) = CarRequest(
            o.optString("id"), o.optString("carId"), o.optString("kind"), o.optStringOrNull("recordId"), o.optStringOrNull("partHint"),
            o.optStringOrNull("note"), o.optStringOrNull("text"), o.optLong("createdAt"),
        )
    }
}

/** 캡처 화면이 차 요청에 답할 때 붙이는 맥락. */
data class ReqCtx(val requestId: String, val recordId: String?, val partHint: String?, val note: String?)

/** 이 폰에서 만든 기록(최근 기록 목록). 상태: sending · sent · acked · failed. */
data class LocalCapture(
    val localId: String, val kind: String, val startedAt: Long, val durationMs: Long, val summary: String,
    val status: String, val serverId: String?, val ackedAt: Long?, val requestId: String?, val thumb: String?, val test: Boolean,
) {
    fun toJson(): JSONObject = JSONObject().put("localId", localId).put("kind", kind).put("startedAt", startedAt).put("durationMs", durationMs)
        .put("summary", summary).put("status", status).put("serverId", serverId ?: JSONObject.NULL).put("ackedAt", ackedAt ?: JSONObject.NULL)
        .put("requestId", requestId ?: JSONObject.NULL).put("thumb", thumb ?: JSONObject.NULL).put("test", test)

    companion object {
        fun from(o: JSONObject) = LocalCapture(
            o.optString("localId"), o.optString("kind"), o.optLong("startedAt"), o.optLong("durationMs"), o.optString("summary"),
            o.optString("status", "failed"), o.optStringOrNull("serverId"), if (o.isNull("ackedAt")) null else o.optLong("ackedAt"),
            o.optStringOrNull("requestId"), o.optStringOrNull("thumb"), o.optBoolean("test"),
        )
    }
}

fun JSONObject.optStringOrNull(key: String): String? = if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }
