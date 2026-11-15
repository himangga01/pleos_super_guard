package app.pleossupercare.phone.sensing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/** 사진 풀기 · 돌리기 · 줄이기 · 자르기. JPEG로 다시 저장하면 EXIF(위치 등)는 남지 않습니다. */
object ImageUtil {
    fun decodeJpeg(bytes: ByteArray, rotation: Int, maxDim: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDim) }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        return scaled(rotate(bmp, rotation), maxDim)
    }

    fun decodeUri(context: Context, uri: Uri, maxDim: Int): Bitmap? {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // 크기만 읽는 decodeStream은 늘 null을 돌려주므로, 스트림이 열리는지만 따로 봅니다.
        val head = cr.openInputStream(uri) ?: return null
        head.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxDim) }
        val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val rotation = runCatching {
            cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
        return scaled(rotate(bmp, rotation), maxDim)
    }

    private fun sampleSize(w: Int, h: Int, maxDim: Int): Int {
        var s = 1
        while (max(w, h) / (s * 2) >= maxDim) s *= 2
        return s
    }

    fun rotate(bmp: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bmp
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    fun scaled(bmp: Bitmap, maxDim: Int): Bitmap {
        val big = max(bmp.width, bmp.height)
        if (big <= maxDim) return bmp
        val k = maxDim.toFloat() / big
        return Bitmap.createScaledBitmap(bmp, (bmp.width * k).roundToInt().coerceAtLeast(1), (bmp.height * k).roundToInt().coerceAtLeast(1), true)
    }

    fun crop(bmp: Bitmap, box: IntArray): Bitmap {
        val x0 = box[0].coerceIn(0, bmp.width - 1); val y0 = box[1].coerceIn(0, bmp.height - 1)
        val w = (box[2] - x0).coerceIn(1, bmp.width - x0); val h = (box[3] - y0).coerceIn(1, bmp.height - y0)
        return Bitmap.createBitmap(bmp, x0, y0, w, h)
    }

    fun jpeg(bmp: Bitmap, maxDim: Int, quality: Int = 80): ByteArray {
        val out = ByteArrayOutputStream()
        scaled(bmp, maxDim).compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    fun pixels(bmp: Bitmap): IntArray {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return px
    }
}
