package app.pleossupercare.phone.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 이 폰의 기록 목록(captures.json)과 아직 못 보낸 기록(outbox/<id>.json), 사진 작은 그림(thumbs/).
 * 보내기에 실패하면 본문을 outbox에 남겨 두고 서버가 다시 닿을 때 보냅니다. 소리 · 진동 원본 표본은 저장하지 않습니다.
 */
class CaptureStore(private val dir: File) {
    private val listFile = File(dir, "captures.json")
    private val outbox = File(dir, "outbox").apply { mkdirs() }
    val thumbs = File(dir, "thumbs").apply { mkdirs() }
    private val lock = Any()

    fun list(): List<LocalCapture> = synchronized(lock) {
        if (!listFile.exists()) return emptyList()
        runCatching {
            val arr = JSONArray(listFile.readText())
            (0 until arr.length()).map { LocalCapture.from(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList()).sortedByDescending { it.startedAt }
    }

    private fun save(items: List<LocalCapture>) {
        val arr = JSONArray()
        items.sortedByDescending { it.startedAt }.take(60).forEach { arr.put(it.toJson()) }
        val tmp = File(dir, "captures.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(listFile).let { ok -> if (!ok) { listFile.delete(); tmp.renameTo(listFile) } }
    }

    fun add(item: LocalCapture, payload: JSONObject, thumbJpeg: ByteArray?) = synchronized(lock) {
        File(outbox, item.localId + ".json").writeText(payload.toString())
        if (thumbJpeg != null && item.thumb != null) File(thumbs, item.thumb).writeBytes(thumbJpeg)
        save(list() + item)
    }

    fun update(localId: String, change: (LocalCapture) -> LocalCapture) = synchronized(lock) {
        save(list().map { if (it.localId == localId) change(it) else it })
    }

    fun payload(localId: String): JSONObject? = synchronized(lock) {
        val f = File(outbox, "$localId.json")
        if (!f.exists()) null else runCatching { JSONObject(f.readText()) }.getOrNull()
    }

    fun dropPayload(localId: String) = synchronized(lock) { File(outbox, "$localId.json").delete() }

    fun clear() = synchronized(lock) {
        listFile.delete()
        outbox.listFiles()?.forEach { it.delete() }
        thumbs.listFiles()?.forEach { it.delete() }
    }
}
