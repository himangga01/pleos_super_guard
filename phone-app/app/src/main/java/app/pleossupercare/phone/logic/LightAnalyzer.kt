package app.pleossupercare.phone.logic

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** 누른 자리 둘레에서 고른 불빛 색. [color]가 null이면 "색을 고르지 못했습니다. 직접 골라 주세요". */
data class ColorPick(
    val color: String?,
    /** 가장 많은 색 구간의 화소 비율(0..1). 차로 metric `confidence`로 보냅니다. 화면에는 숫자로 보이지 않습니다. */
    val confidence: Double,
    /** 켜진 불빛 화소 수(관심 영역을 한 변 256화소로 줄인 기준). */
    val lit: Int,
    val shares: Map<String, Double>,
    /** 관심 영역(사진 화소 좌표): x0, y0, x1, y1. */
    val roi: IntArray,
)

/** 밝고 진한 덩어리(누를 자리 힌트). 좌표는 사진 폭 · 높이에 대한 0..1 비율입니다. */
data class Blob(val cx: Float, val cy: Float, val r: Float, val size: Int)

/**
 * 경고등 사진에서 색을 고르는 계산(순수 Kotlin). 폰 센서 조사 6.2절의 시연 흐름을 따릅니다.
 * - 사용자가 사진 속 불빛을 누르면, 누른 점을 가운데로 사진 짧은 변의 15% 정사각형을 잘라 씁니다.
 * - 켜진 불빛 화소: 명도 V ≥ 0.55, 채도 S ≥ 0.40. 가운데가 하얗게 날아간 화소(S가 낮음)는 저절로 빠집니다.
 * - 색상 구간: 빨강 0~15° · 340~360°, 주황(노랑 포함) 16~65°, 초록 70~170°, 파랑 180~260°. 그 밖은 판정하지 않습니다.
 * - 가장 많은 구간의 비율이 0.6 밑이거나 켜진 화소가 40개 밑이면 색을 고르지 않고 사용자에게 묻습니다.
 * 모양은 자동으로 알아보지 않습니다(학습한 모양 분류기는 나중 단계). 운전자가 도감 후보에서 고릅니다.
 */
object LightAnalyzer {
    const val V_MIN = 0.55f
    const val S_MIN = 0.40f
    const val ROI_FRACTION = 0.15
    const val ROI_SIDE = 256
    const val MIN_SHARE = 0.6
    const val MIN_LIT = 40

    /** ARGB → [H(0..360), S(0..1), V(0..1)]. android.graphics.Color.RGBToHSV와 같은 정의입니다. */
    fun hsv(argb: Int, out: FloatArray) {
        val r = (argb shr 16 and 0xFF) / 255f; val g = (argb shr 8 and 0xFF) / 255f; val b = (argb and 0xFF) / 255f
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b)); val d = mx - mn
        var h = when {
            d == 0f -> 0f
            mx == r -> 60f * (((g - b) / d) % 6f)
            mx == g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }
        if (h < 0) h += 360f
        out[0] = h; out[1] = if (mx == 0f) 0f else d / mx; out[2] = mx
    }

    /** 색상 → 도감 색 이름(빨강 · 주황 · 초록 · 파랑) 또는 판정하지 않음(null). */
    fun hueClass(h: Float): String? = when {
        h <= 15f || h >= 340f -> "빨강"
        h >= 16f && h <= 65f -> "주황"
        h >= 70f && h <= 170f -> "초록"
        h >= 180f && h <= 260f -> "파랑"
        else -> null
    }

    fun roi(w: Int, h: Int, tapX: Int, tapY: Int): IntArray {
        val side = max(8, (min(w, h) * ROI_FRACTION).roundToInt())
        val x0 = (tapX - side / 2).coerceIn(0, max(0, w - side)); val y0 = (tapY - side / 2).coerceIn(0, max(0, h - side))
        return intArrayOf(x0, y0, min(w, x0 + side), min(h, y0 + side))
    }

    /** 누른 점([tapX], [tapY], 사진 화소) 둘레에서 불빛 색을 고릅니다. */
    fun pickColor(argb: IntArray, w: Int, h: Int, tapX: Int, tapY: Int): ColorPick {
        val box = roi(w, h, tapX, tapY)
        val bw = box[2] - box[0]; val bh = box[3] - box[1]
        val counts = LinkedHashMap<String, Int>().apply { WarningCatalog.COLORS.forEach { put(it, 0) } }
        var lit = 0
        val hsv = FloatArray(3)
        // 관심 영역을 한 변 256화소로 줄여 봅니다(가까운 화소 고르기).
        for (j in 0 until ROI_SIDE) for (i in 0 until ROI_SIDE) {
            val px = box[0] + (i * bw / ROI_SIDE).coerceAtMost(bw - 1)
            val py = box[1] + (j * bh / ROI_SIDE).coerceAtMost(bh - 1)
            hsv(argb[py * w + px], hsv)
            if (hsv[2] < V_MIN || hsv[1] < S_MIN) continue
            lit++
            val c = hueClass(hsv[0]) ?: continue
            counts[c] = counts.getValue(c) + 1
        }
        val shares = counts.mapValues { if (lit > 0) it.value.toDouble() / lit else 0.0 }
        val best = shares.maxByOrNull { it.value }
        val conf = best?.value ?: 0.0
        val color = if (best != null && lit >= MIN_LIT && conf >= MIN_SHARE) best.key else null
        return ColorPick(color, conf, lit, shares, box)
    }

    /** 사진 전체에서 밝고 진한(도감 색 구간의) 덩어리를 큰 순서로 [maxCount]개 찾습니다. 누를 자리 힌트로만 씁니다. */
    fun blobs(argb: IntArray, w: Int, h: Int, maxCount: Int = 5): List<Blob> {
        val scale = max(1, max(w, h) / 200)
        val gw = w / scale; val gh = h / scale
        if (gw < 4 || gh < 4) return emptyList()
        val on = BooleanArray(gw * gh)
        val hsv = FloatArray(3)
        for (j in 0 until gh) for (i in 0 until gw) {
            hsv(argb[(j * scale) * w + i * scale], hsv)
            on[j * gw + i] = hsv[2] >= V_MIN && hsv[1] >= S_MIN && hueClass(hsv[0]) != null
        }
        val seen = BooleanArray(gw * gh)
        val out = mutableListOf<Blob>()
        val stack = IntArray(gw * gh)
        for (start in on.indices) {
            if (!on[start] || seen[start]) continue
            var sp = 0; stack[sp++] = start; seen[start] = true
            var n = 0; var sx = 0L; var sy = 0L; var x0 = gw; var y0 = gh; var x1 = 0; var y1 = 0
            while (sp > 0) {
                val p = stack[--sp]
                val px = p % gw; val py = p / gw
                n++; sx += px; sy += py
                x0 = min(x0, px); y0 = min(y0, py); x1 = max(x1, px); y1 = max(y1, py)
                for (dy in -1..1) for (dx in -1..1) {
                    val qx = px + dx; val qy = py + dy
                    if (qx < 0 || qy < 0 || qx >= gw || qy >= gh) continue
                    val q = qy * gw + qx
                    if (on[q] && !seen[q]) { seen[q] = true; stack[sp++] = q }
                }
            }
            if (n >= 6) {
                val r = max(x1 - x0, y1 - y0) / 2f + 2f
                out += Blob((sx.toFloat() / n + 0.5f) / gw, (sy.toFloat() / n + 0.5f) / gh, r / max(gw, gh), n)
            }
        }
        return out.sortedByDescending { it.size }.take(maxCount)
    }

    /**
     * 화면에 보이고 차로 보낼 잘라 낸 영역(사진 화소 좌표 x0, y0, x1, y1, 정사각).
     * 색 판정은 [roi](짧은 변의 15%)로 하지만, 운전자가 후보 아이콘과 견주려면 기호 전체가 보여야 하므로
     * 누른 점 가까이에 불빛 덩어리가 있으면 그 덩어리를 통째로(여유 30%), 없으면 관심 영역의 2.2배를 씁니다.
     */
    fun displayBox(w: Int, h: Int, tapX: Int, tapY: Int, blobs: List<Blob>): IntArray {
        val big = max(w, h).toFloat()
        val tx = tapX.toFloat(); val ty = tapY.toFloat()
        val near = blobs.minByOrNull { dist(it.cx * w, it.cy * h, tx, ty) }
            ?.takeIf { dist(it.cx * w, it.cy * h, tx, ty) <= max(it.r * big * 1.5f, min(w, h) * 0.1f) }
        val roiHalf = min(w, h) * ROI_FRACTION.toFloat() / 2
        val cx = near?.let { it.cx * w } ?: tx
        val cy = near?.let { it.cy * h } ?: ty
        val half = if (near != null) max(near.r * big * 1.3f, roiHalf) else roiHalf * 2.2f
        val side = (half * 2).roundToInt().coerceIn(16, min(w, h))
        val x0 = (cx - side / 2f).roundToInt().coerceIn(0, w - side)
        val y0 = (cy - side / 2f).roundToInt().coerceIn(0, h - side)
        return intArrayOf(x0, y0, x0 + side, y0 + side)
    }

    /** 고른 색으로 남는 모양(도감 순서). 남는 모양이 없으면(초록 등) 모든 모양을 돌려주고 [narrowed] = false. */
    fun shapesFor(color: String?): Pair<List<String>, Boolean> {
        if (color == null) return WarningCatalog.SHAPES to false
        val left = WarningCatalog.SHAPES.filter { color in WarningCatalog.usualColors(it) }
        return if (left.isEmpty()) WarningCatalog.SHAPES to false else left to true
    }

    /**
     * 차로 보낼 후보 id(좋은 순서): 도감 찾기 결과(첫째 · 둘째) → 같은 모양의 나머지 → 같은 색의 나머지. 최대 8개.
     */
    fun candidates(color: String, shape: String, mode: String): List<String> {
        val res = WarningCatalog.find(color, shape, mode)
        val ids = LinkedHashSet<String>()
        res.all.forEach { ids += it.id }
        WarningCatalog.ENTRIES.filter { it.shape == shape }.forEach { ids += it.id }
        WarningCatalog.ENTRIES.filter { it.color == color }.forEach { ids += it.id }
        return ids.take(8)
    }

    /** 화면에 보일 말(정확도 숫자 없이). */
    fun confidenceWord(p: ColorPick): String = when {
        p.color == null -> "색을 고르지 못했습니다. 직접 골라 주세요"
        p.confidence >= 0.85 -> "${p.color} 불빛이 뚜렷합니다"
        else -> "${p.color}으로 보입니다. 맞는지 확인해 주세요"
    }

    fun dist(ax: Float, ay: Float, bx: Float, by: Float) = sqrt((ax - bx) * (ax - bx) + (ay - by) * (ay - by))
}
