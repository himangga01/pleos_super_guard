package app.pleossupercare.phone.logic

import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.DecodeHintType
import com.google.zxing.FormatException
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.net.URLDecoder

/** 차 화면의 짝 맺기 QR · 번호 읽기. 차 앱 QR 모양: pleoscare://pair?code=123456&server=http://10.0.2.2:8766 */
object PairLink {
    data class Parsed(val code: String, val server: String?)

    fun parse(text: String?): Parsed? {
        val t = text?.trim() ?: return null
        if (t.startsWith("pleoscare:", ignoreCase = true)) {
            val query = t.substringAfter('?', "")
            val params = query.split('&').mapNotNull { kv ->
                val i = kv.indexOf('=')
                if (i <= 0) null else kv.substring(0, i) to runCatching { URLDecoder.decode(kv.substring(i + 1), "UTF-8") }.getOrDefault(kv.substring(i + 1))
            }.toMap()
            val code = params["code"]?.filter { it.isDigit() }
            if (code == null || code.length != 6) return null
            val server = params["server"]?.trim()?.trimEnd('/')?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            return Parsed(code, server)
        }
        val m = Regex("(?<!\\d)(\\d{6})(?!\\d)").find(t) ?: return null
        return Parsed(m.groupValues[1], null)
    }
}

/** 밝기(Y) 화소로 QR을 읽습니다(ZXing core, Play 서비스 없이). */
object QrDecoder {
    private val hints = mapOf(DecodeHintType.TRY_HARDER to true)

    fun decode(luminance: ByteArray, width: Int, height: Int): String? {
        val source = PlanarYUVLuminanceSource(luminance, width, height, 0, 0, width, height, false)
        return try {
            QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        } catch (e: NotFoundException) {
            null
        } catch (e: ChecksumException) {
            null
        } catch (e: FormatException) {
            null
        }
    }
}
