package app.pleosguard.demo.car3d

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.ToneMapper
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** 3D 보기 방식. 실내는 덮개(유리 · 지붕 · 기둥)를 반투명 덮개로 바꿔 안을 보입니다(지붕이 열린 차로 그리지 않음). */
enum class CarMode { EXTERIOR, INTERIOR }

/**
 * 3D 아반떼를 그리는 Filament 엔진. 앱에 하나만 두고 화면이 바뀌면 TextureView만 바꿔 붙입니다.
 * 모든 호출은 메인 스레드에서 합니다. Robolectric(JVM 시험)처럼 그래픽이 없으면 [getOrNull]이 null을
 * 돌려주고, 화면은 같은 모델을 미리 그린 그림으로 대신합니다.
 */
class CarEngine private constructor(ctx: Context) {
    companion object {
        private const val TAG = "CarEngine"
        private var inst: CarEngine? = null
        private var failed = false

        fun available(): Boolean = !failed && !Build.FINGERPRINT.contains("robolectric", ignoreCase = true)

        fun getOrNull(ctx: Context): CarEngine? {
            if (!available()) return null
            inst?.let { return it }
            return try {
                CarEngine(ctx.applicationContext).also { inst = it }
            } catch (t: Throwable) {
                Log.w(TAG, "3D 엔진을 켜지 못해 미리 그린 그림을 씁니다", t)
                failed = true
                null
            }
        }

        // 강조 · 상태 색(선형 RGB). 화면 색(C.Mint · C.Amber · C.Purple · C.Red)에 맞췄습니다.
        private val HIGHLIGHT = floatArrayOf(0.03f, 0.42f, 0.36f)
        private val OCCUPIED = floatArrayOf(0.30f, 0.32f, 0.85f)
        private val BELT_ON = floatArrayOf(0.20f, 0.85f, 0.72f)
        private val BELT_OFF = floatArrayOf(1.0f, 0.62f, 0.12f)
        private val DOOR_OPEN = floatArrayOf(0.55f, 0.36f, 0.02f)
        private val AIRBAG = floatArrayOf(0.95f, 0.22f, 0.28f)
    }

    private val engine: Engine
    private val renderer: Renderer
    private val scene: Scene
    private val view: View
    private val camera: Camera
    private val asset: FilamentAsset
    private val rm get() = engine.renderableManager
    private val tm get() = engine.transformManager

    private val byName = HashMap<String, Int>()
    private val partOf = HashMap<Int, CarPart>()
    private val base = HashMap<Int, Array<MaterialInstance>>()
    private val hl = HashMap<Int, Array<MaterialInstance>>()
    private val tinted = HashMap<String, Array<MaterialInstance>>()  // "엔티티:색" → 칠한 재질
    private val doorBase = HashMap<Int, FloatArray>()

    private var ui: UiHelper? = null
    private var swapChain: SwapChain? = null
    private var dirty = 0
    private var pendingCompiles = 0
    /** 붙인 뒤 첫 그림을 다 그리면 한 번 부릅니다(그 전까지 화면은 미리 그린 그림을 보입니다). */
    var onFirstFrame: (() -> Unit)? = null
    private var framesSinceAttach = 0
    private val main = Handler(Looper.getMainLooper())

    var width = 0; private set
    var height = 0; private set
    var onSize: ((Int, Int) -> Unit)? = null

    private var mode: CarMode? = null
    private var selected: CarPart? = null
    private var state: CarState? = null

    init {
        Filament.init()
        Gltfio.init()
        engine = Engine.create()
        renderer = engine.createRenderer()
        scene = engine.createScene()
        view = engine.createView()
        camera = engine.createCamera(EntityManager.get().create())
        view.setScene(scene)
        view.setCamera(camera)
        view.setBlendMode(View.BlendMode.TRANSLUCENT)
        view.setMultiSampleAntiAliasingOptions(View.MultiSampleAntiAliasingOptions().apply { enabled = true; sampleCount = 4 })
        renderer.setClearOptions(Renderer.ClearOptions().apply { clear = true; clearColor = doubleArrayOf(0.0, 0.0, 0.0, 0.0) })
        camera.setExposure(16f, 1f / 125f, 100f)
        // 제품 사진처럼 칠 색을 그대로 보이는 톤 매핑(Khronos PBR Neutral)과 틈 · 바퀴집의 그늘(SSAO)
        view.setColorGrading(ColorGrading.Builder().toneMapper(ToneMapper.PBRNeutralToneMapper()).build(engine))
        view.setAmbientOcclusionOptions(View.AmbientOcclusionOptions().apply {
            enabled = true; radius = 0.35f; intensity = 1.0f; quality = View.QualityLevel.MEDIUM
        })

        // 빛: 앞 왼쪽 위의 해 + 스튜디오 조명 같은 반사 환경(내려받은 HDR 없이 코드로 만듦)
        val sun = EntityManager.get().create()
        LightManager.Builder(LightManager.Type.SUN)
            .color(1.0f, 0.98f, 0.95f).intensity(125_000f).direction(-0.45f, -1.0f, 0.38f)
            .sunAngularRadius(2.0f).castShadows(false) // 실시간 그림자는 처음 몇 프레임에 차 전체를 어둡게 해 끕니다(바닥 그림자는 모델에 구워 둠)
            .build(engine, sun)
        scene.addEntity(sun)
        val env = studioCubemap(engine, 64)
        scene.setIndirectLight(IndirectLight.Builder().reflections(env).irradiance(1, floatArrayOf(0.55f, 0.58f, 0.62f)).intensity(46_000f).build(engine))

        // 모델
        val provider = UbershaderProvider(engine)
        val loader = AssetLoader(engine, provider, EntityManager.get())
        val bytes = ctx.assets.open("models/avante.glb").use { it.readBytes() }
        val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)
        buf.rewind()
        asset = loader.createAsset(buf) ?: error("avante.glb를 읽지 못했습니다")
        ResourceLoader(engine, true).loadResources(asset)
        asset.releaseSourceData()
        scene.addEntities(asset.entities)
        // 셰이더를 미리 컴파일합니다. Filament는 컴파일이 끝나지 않은 물체를 건너뛰고 그리므로,
        // 다 끝날 때까지는 미리 그린 그림을 보이고 계속 그립니다.
        val mats = HashMap<Long, Material>()
        for (e in asset.renderableEntities) {
            val ri = rm.getInstance(e)
            for (i in 0 until rm.getPrimitiveCount(ri)) rm.getMaterialInstanceAt(ri, i).material.let { mats[it.nativeObject] = it }
        }
        pendingCompiles = mats.size
        val t0 = System.currentTimeMillis()
        for (m in mats.values) m.compile(Material.CompilerPriorityQueue.HIGH, 0x07, main) {
            if (--pendingCompiles == 0) {
                Log.i(TAG, "셰이더 ${mats.size}개 준비 ${System.currentTimeMillis() - t0}ms")
                invalidate(12)
            }
        }

        for (e in asset.entities) {
            val n = asset.getName(e) ?: continue
            byName[n] = e
            CarPart.ofNode(n)?.let { partOf[e] = it }
        }
        for (e in asset.renderableEntities) {
            val ri = rm.getInstance(e)
            val n = asset.getName(e) ?: ""
            val glass = n == "Canopy" || n == "CanopyGhost" || n == "Shadow"
            rm.setCastShadows(ri, !glass && !n.startsWith("Underbody"))
            rm.setReceiveShadows(ri, n != "Shadow")
            val count = rm.getPrimitiveCount(ri)
            val orig = Array(count) { rm.getMaterialInstanceAt(ri, it) }
            base[e] = orig
            if (partOf[e] != null) hl[e] = Array(count) { i -> MaterialInstance.duplicate(orig[i], "hl").also { glow(it, HIGHLIGHT, 1f) } }
        }
        for (key in listOf("Door_FL", "Door_FR", "Door_RL", "Door_RR")) byName[key]?.let { e ->
            doorBase[e] = tm.getTransform(tm.getInstance(e), FloatArray(16))
        }
        setMode(CarMode.EXTERIOR)
        applyState(null)
    }

    // ------------------------------------------------------------------ 붙이기 · 그리기
    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(t: Long) {
            val u = ui ?: return
            Choreographer.getInstance().postFrameCallback(this)
            val sc = swapChain ?: return
            if ((dirty <= 0 && pendingCompiles == 0) || !u.isReadyToRender) return
            if (renderer.beginFrame(sc, t)) {
                renderer.render(view)
                renderer.endFrame()
                if (dirty > 0) dirty--
                if (pendingCompiles == 0 && ++framesSinceAttach == 3) {
                    Log.i(TAG, "첫 그림 완료(mode=$mode, state=${state != null})")
                    onFirstFrame?.let { cb -> main.post { cb() } }
                }
            }
        }
    }

    private var attached: TextureView? = null

    /** 화면이 바뀌면 새 화면의 TextureView를 붙입니다. 앞 화면을 떼는 일이 나중에 와도 새 화면은 그대로 둡니다. */
    fun attach(tv: TextureView, onFirst: (() -> Unit)? = null) {
        detach()
        attached = tv
        onFirstFrame = onFirst
        val u = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
        u.isOpaque = false
        u.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface, u.swapChainFlags)
                invalidate(8)
            }

            override fun onDetachedFromSurface() {
                swapChain?.let { engine.destroySwapChain(it); engine.flushAndWait() }
                swapChain = null
            }

            override fun onResized(w: Int, h: Int) {
                width = w; height = h
                view.setViewport(Viewport(0, 0, w, h))
                onSize?.invoke(w, h)
                invalidate(8)
            }
        }
        framesSinceAttach = 0
        Log.i(TAG, "붙임")
        invalidate(8) // 붙인 뒤 몇 장은 꼭 그려 미리 그린 그림을 걷어 냅니다
        u.attachTo(tv)
        ui = u
        Choreographer.getInstance().postFrameCallback(frame)
    }

    /** [tv]가 지금 붙어 있는 화면일 때만 뗍니다(화면 전환 때 앞 화면의 해제가 늦게 와도 안전). */
    fun detach(tv: TextureView) { if (attached === tv) detach() }

    private fun detach() {
        attached = null
        Choreographer.getInstance().removeFrameCallback(frame)
        ui?.detach()
        ui = null
        onSize = null
        onFirstFrame = null
    }

    fun invalidate(frames: Int = 3) { dirty = max(dirty, frames) }

    // ------------------------------------------------------------------ 카메라
    fun setCamera(f: CameraFrame) {
        camera.setProjection(Orbit.FOV_Y, f.aspect, CameraFrame.NEAR, CameraFrame.FAR, Camera.Fov.VERTICAL)
        val e = f.eye; val t = f.orbit.target
        camera.lookAt(e.x, e.y, e.z, t.x, t.y, t.z, 0.0, 1.0, 0.0)
        invalidate()
    }

    /** 화면 좌표(왼쪽 위 기준 px)의 부위. 결과는 다음 그림을 그린 뒤 메인 스레드로 옵니다. */
    fun pick(x: Float, y: Float, onResult: (CarPart?) -> Unit) {
        if (width == 0 || height == 0) { onResult(null); return }
        val px = x.toInt().coerceIn(0, width - 1); val py = (height - 1 - y.toInt()).coerceIn(0, height - 1)
        view.pick(px, py, main) { r ->
            val name = asset.getName(r.renderable)
            onResult(CarPart.ofNode(name))
        }
        invalidate(3)
    }

    // ------------------------------------------------------------------ 상태
    fun setMode(m: CarMode) {
        if (mode == m) return
        mode = m
        show("Canopy", m == CarMode.EXTERIOR)
        show("Canopy_Fin", m == CarMode.EXTERIOR)
        show("CanopyGhost", m == CarMode.INTERIOR)
        invalidate()
    }

    fun setSelected(p: CarPart?) {
        if (selected == p) return
        selected = p
        restoreAll()
        applyState(state)
        invalidate()
    }

    fun setState(s: CarState?) {
        if (state == s) return
        Log.i(TAG, "상태: $s")
        state = s
        restoreAll()
        applyState(s)
        invalidate()
    }

    private fun restoreAll() {
        for ((e, mis) in base) { val ri = rm.getInstance(e); mis.forEachIndexed { i, mi -> rm.setMaterialInstanceAt(ri, i, mi) } }
        for ((e, m) in doorBase) tm.setTransform(tm.getInstance(e), m)
    }

    private fun applyState(s: CarState?) {
        // 고른 부위 강조
        selected?.let { p -> for ((e, part) in partOf) if (part == p) hl[e]?.let { setMis(e, it) } }
        // 사고 순간 상태: 탑승 · 벨트 · 문 · 에어백. 상태가 없으면(홈) 사람 · 벨트 · 에어백을 숨깁니다.
        for ((i, k) in CarState.SEAT_KEYS.withIndex()) {
            val occ = s?.seats?.getOrNull(i)
            show("Occupant_$k", occ == true)
            val seat = byName["Seat_$k"]
            val belt = s?.belts?.getOrNull(i)
            // 탑승 좌석은 보라, 탑승했는데 벨트를 안 맸으면 주황. 맨 벨트만 초록 띠로 보입니다.
            if (seat != null && occ == true) setMis(seat, if (belt == false) tint(seat, "occWarn", BELT_OFF) else tint(seat, "occ", OCCUPIED))
            val beltE = byName["Belt_$k"]
            show("Belt_$k", s != null && occ == true && belt == true)
            if (beltE != null && belt == true) setMis(beltE, tint(beltE, "on", BELT_ON))
            val door = s?.doors?.getOrNull(i)
            val doorKey = listOf("Door_FL", "Door_FR", "Door_RL", "Door_RR")[i]
            byName[doorKey]?.let { e ->
                val open = (door ?: 0) > 0
                if (open) {
                    val m = doorBase[e]!!.copyOf()
                    android.opengl.Matrix.rotateM(m, 0, CarState.doorAngle(i, true), 0f, 1f, 0f)
                    tm.setTransform(tm.getInstance(e), m)
                    setMis(e, tint(e, "open", DOOR_OPEN))
                }
            }
        }
        show("Occupant_RC", false)
        show("Belt_RC", false)
        show("Airbag_FL", s?.airbag == true)
        byName["Airbag_FL"]?.let { if (s?.airbag == true) setMis(it, tint(it, "red", AIRBAG, base = true)) }
    }

    private fun setMis(e: Int, mis: Array<MaterialInstance>) {
        val ri = rm.getInstance(e)
        mis.forEachIndexed { i, mi -> rm.setMaterialInstanceAt(ri, i, mi) }
    }

    private fun tint(e: Int, key: String, rgb: FloatArray, base: Boolean = false): Array<MaterialInstance> =
        tinted.getOrPut("$e:$key") {
            this.base[e]!!.map { orig ->
                MaterialInstance.duplicate(orig, key).also { mi ->
                    if (base && mi.material.hasParameter("baseColorFactor")) mi.setParameter("baseColorFactor", Colors.RgbaType.LINEAR, rgb[0], rgb[1], rgb[2], 1f)
                    glow(mi, rgb, if (base) 0.25f else 0.55f)
                    if (!base && mi.material.hasParameter("baseColorFactor")) mi.setParameter("baseColorFactor", Colors.RgbaType.LINEAR, rgb[0] * 0.6f, rgb[1] * 0.6f, rgb[2] * 0.6f, 1f)
                }
            }.toTypedArray()
        }

    /** 빛나는 색을 더합니다(고른 부위의 민트 강조, 상태 색). 어두운 타이어 · 유리에서도 보입니다. */
    private fun glow(mi: MaterialInstance, rgb: FloatArray, k: Float) {
        if (mi.material.hasParameter("emissiveFactor")) mi.setParameter("emissiveFactor", Colors.RgbType.LINEAR, rgb[0] * k, rgb[1] * k, rgb[2] * k)
    }

    private fun show(name: String, on: Boolean) {
        val e = byName[name] ?: return
        if (on && !scene.hasEntity(e)) scene.addEntity(e)
        if (!on && scene.hasEntity(e)) scene.removeEntity(e)
    }

    // ------------------------------------------------------------------ 반사 환경(코드로 만든 스튜디오)
    private fun studioCubemap(engine: Engine, size: Int): Texture {
        var levels = 1; while ((1 shl (levels - 1)) < size) levels++
        val tex = Texture.Builder().width(size).height(size).levels(levels)
            .sampler(Texture.Sampler.SAMPLER_CUBEMAP).format(Texture.InternalFormat.R11F_G11F_B10F).build(engine)
        val faceBytes = size * size * 3 * 4
        val buf = ByteBuffer.allocateDirect(faceBytes * 6).order(ByteOrder.nativeOrder())
        val fb = buf.asFloatBuffer()
        for (face in 0 until 6) for (yy in 0 until size) for (xx in 0 until size) {
            val u = (xx + 0.5f) / size * 2f - 1f; val v = (yy + 0.5f) / size * 2f - 1f
            val d = when (face) {
                0 -> floatArrayOf(1f, -v, -u); 1 -> floatArrayOf(-1f, -v, u)
                2 -> floatArrayOf(u, 1f, v); 3 -> floatArrayOf(u, -1f, -v)
                4 -> floatArrayOf(u, -v, 1f); else -> floatArrayOf(-u, -v, -1f)
            }
            val l = sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]); val x = d[0] / l; val y = d[1] / l; val z = d[2] / l
            val c = studio(x, y, z)
            fb.put(c * 0.96f); fb.put(c * 0.99f); fb.put(c * 1.04f)
        }
        buf.rewind()
        tex.generatePrefilterMipmap(engine, Texture.PixelBufferDescriptor(buf, Texture.Format.RGB, Texture.Type.FLOAT),
            IntArray(6) { it * faceBytes }, Texture.PrefilterOptions())
        return tex
    }

    /**
     * 스튜디오 반사 환경: 어두운 벽, 수평선의 밝은 띠, 머리 위 큰 조명, 앞 왼쪽 · 뒤 오른쪽의 세로 조명, 어두운 바닥.
     * 차체 옆면에 가로 반사선, 보닛 · 지붕에 밝은 반사가 생겨 칠 색과 꺾임선이 보입니다.
     */
    private fun studio(x: Float, y: Float, z: Float): Float {
        var c = if (y >= 0f) 0.16f + 0.95f * exp(-y * 11f) else 0.035f + 0.40f * exp(y * 18f)
        if (y > 0.86f) c += 1.7f * min(1f, (y - 0.86f) / 0.05f)                  // 머리 위 큰 조명
        val band = max(0f, 1f - kotlin.math.abs(y - 0.18f) * 3.2f)                // 세로 조명의 높이 범위
        val a = kotlin.math.atan2(-z, x)                                          // 0 = 차 앞, +π/2 = 왼쪽
        fun lobe(center: Float, width: Float): Float {
            var d = kotlin.math.abs(a - center); if (d > Math.PI.toFloat()) d = (2 * Math.PI).toFloat() - d
            return max(0f, 1f - d / width)
        }
        c += 2.4f * band * lobe(0.75f, 0.32f)                                     // 앞 왼쪽 세로 조명
        c += 1.6f * band * lobe(-2.3f, 0.30f)                                     // 뒤 오른쪽 세로 조명
        return c
    }
}
