package app.pleossupercare.phone.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.pleossupercare.phone.logic.QrDecoder
import app.pleossupercare.phone.sensing.ImageUtil
import java.util.concurrent.Executors

/** 카메라 미리보기와 찍기를 잇는 손잡이. */
class CameraHandle {
    internal var capture: ImageCapture? = null
    internal var view: PreviewView? = null
    var ready by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
    /** 마지막 사진이 미리보기 화면에서 가져온 것이면 true(정지 사진이 제때 오지 않았음). */
    var fromPreview = false
        private set

    /**
     * 사진 한 장(최대 [maxDim]px, 돌림 맞춤). 실패하면 null. 콜백은 메인 스레드에서 한 번만 부릅니다.
     * 정지 사진(ImageCapture)이 2.5초 안에 오지 않으면 지금 미리보기 화면을 씁니다.
     * 2026-11-14 폰 에뮬레이터(가상 장면 카메라)에서 정지 사진 요청이 30초 넘게 돌아오지 않아 넣은 대비책입니다.
     */
    fun take(context: Context, maxDim: Int, onResult: (Bitmap?) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        var done = false
        fun finish(b: Bitmap?, preview: Boolean) {
            if (done) return
            done = true; busy = false; fromPreview = preview
            onResult(b)
        }
        val fallback = Runnable { finish(view?.bitmap?.let { ImageUtil.scaled(it, maxDim) }, true) }
        busy = true
        val c = capture
        if (c == null) { fallback.run(); return }
        main.postDelayed(fallback, 2_500)
        c.takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bytes = try {
                    val buf = image.planes[0].buffer
                    ByteArray(buf.remaining()).also { buf.get(it) }
                } catch (e: Exception) { null }
                val rot = image.imageInfo.rotationDegrees
                image.close()
                if (done) return
                main.removeCallbacks(fallback)
                Thread {
                    val bmp = bytes?.let { ImageUtil.decodeJpeg(it, rot, maxDim) }
                    main.post { finish(bmp, false) }
                }.start()
            }

            override fun onError(exception: ImageCaptureException) {
                main.removeCallbacks(fallback)
                fallback.run()
            }
        })
    }
}

/**
 * CameraX 뒤 카메라 미리보기. [withCapture]면 사진 찍기, [analyzer]가 있으면 프레임 분석(QR 읽기)을 붙입니다.
 * 화면을 떠나면 카메라를 놓습니다.
 */
@Composable
fun CameraPreview(handle: CameraHandle, modifier: Modifier = Modifier, withCapture: Boolean = true, analyzer: ImageAnalysis.Analyzer? = null) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    handle.view = view
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(owner, analyzer, withCapture) {
        val future = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val p = future.get(); provider = p
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                val cases = mutableListOf<UseCase>(preview)
                if (withCapture) {
                    val cap = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                    handle.capture = cap; cases += cap
                }
                if (analyzer != null) {
                    cases += ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                        .also { it.setAnalyzer(executor, analyzer) }
                }
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, *cases.toTypedArray())
                handle.ready = true
                handle.error = null
            } catch (e: Exception) {
                handle.ready = false
                handle.error = "카메라를 열지 못했습니다"
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { provider?.unbindAll() }
            handle.capture = null
            handle.ready = false
        }
    }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }
    AndroidView(factory = { view }, modifier = modifier)
}

/** 밝기(Y) 평면으로 QR을 찾습니다. 찾으면 한 번만 메인 스레드에서 [onText]를 부릅니다. */
class QrAnalyzer(private val onText: (String) -> Unit) : ImageAnalysis.Analyzer {
    @Volatile var done = false
    private val main = Handler(Looper.getMainLooper())

    override fun analyze(image: ImageProxy) {
        try {
            if (done) return
            val plane = image.planes[0]
            val w = image.width; val h = image.height
            val buf = plane.buffer
            val stride = plane.rowStride
            val data = ByteArray(w * h)
            if (stride == w) buf.get(data, 0, minOf(data.size, buf.remaining()))
            else for (row in 0 until h) { buf.position(row * stride); buf.get(data, row * w, w) }
            val text = QrDecoder.decode(data, w, h)
            if (text != null && !done) { done = true; main.post { onText(text) } }
        } catch (_: Exception) {
        } finally {
            image.close()
        }
    }
}
