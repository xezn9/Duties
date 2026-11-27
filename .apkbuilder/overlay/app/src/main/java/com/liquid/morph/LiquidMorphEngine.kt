@file:Suppress("MagicNumber")

package com.liquid.morph

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

/*
 * LiquidMorphEngine.kt
 * ------------------------------------------------------------------
 * The measured timeline of LiquidMenuMorph.kt (same numbers, copied 1:1): press squash, droplet, card bounds,
 * content blur / fade, gooey close, pill overshoot.  Only the demo screen (the dark menu card) was left out;
 * the app draws its own glass (search button -> search field) from these frames.
 */

// ---------------------------------------------------------------------------
// Timeline constants (seconds)
// ---------------------------------------------------------------------------
internal const val OPEN_T = 0.80f   // press -> fully settled
internal const val CLOSE_T = 0.60f  // close start -> pill settled

internal const val REF_W = 1080f
internal const val CARD_W = 750f
internal const val CARD_H = 1293f

private val BodyColor = Color(0xFF1F1F1F)
private val Yellow = Color(0xFFE2AD08)
private val ButtonColor = Color(0xFF1E1E1E)
private val RedColor = Color(0xFFFD3F42)
private val SubGray = Color(0xFF8E8E93)
private val DividerColor = Color(0xFF3C3C3C)

internal val REST_PILL = floatArrayOf(216f, 156f, 517f, 288f)
internal val FINAL_BODY = floatArrayOf(24f, 156f, 774f, 1449f)

// ---------------------------------------------------------------------------
// Measured keyframes.  Row layout is documented above each table.
// ---------------------------------------------------------------------------

/** OPEN card bounds: [t, left, top, right, bottom] */
private val OPEN_BOX = Track(
    arrayOf(
        // FIX: the pill itself morphs into the original droplet (208 x 237 ref px) – no separate cap.
        floatArrayOf(0.084f, 232f, 163f, 498f, 292f),   // = pressed pill (same rect as OPEN_PILL @0.084)
        floatArrayOf(0.118f, 212f, 312f, 421f, 549f),   // = original droplet, exact size (same time as the video)
        floatArrayOf(0.136f, 173f, 345f, 490f, 740f),
        floatArrayOf(0.163f, 139f, 368f, 552f, 924f),
        floatArrayOf(0.176f, 111f, 373f, 607f, 1088f),
        floatArrayOf(0.217f, 66f, 337f, 693f, 1331f),
        floatArrayOf(0.252f, 39f, 269f, 746f, 1459f),
        floatArrayOf(0.268f, 30f, 238f, 763f, 1490f),
        floatArrayOf(0.295f, 21f, 190f, 781f, 1511f),
        floatArrayOf(0.318f, 18f, 173f, 785f, 1510f),
        floatArrayOf(0.334f, 17f, 153f, 787f, 1497f),
        floatArrayOf(0.352f, 18f, 148f, 786f, 1489f),
        floatArrayOf(0.369f, 19f, 145f, 785f, 1481f),
        floatArrayOf(0.385f, 20f, 145f, 783f, 1473f),
        floatArrayOf(0.404f, 20f, 145f, 781f, 1467f),
        floatArrayOf(0.418f, 21f, 146f, 779f, 1462f),
        floatArrayOf(0.438f, 22f, 147f, 778f, 1457f),
        floatArrayOf(0.453f, 23f, 149f, 777f, 1454f),
        floatArrayOf(0.469f, 23f, 150f, 776f, 1451f),
        floatArrayOf(0.486f, 24f, 151f, 775f, 1450f),
        floatArrayOf(0.504f, 24f, 153f, 774f, 1449f),
        floatArrayOf(0.519f, 24f, 154f, 774f, 1448f),
        floatArrayOf(0.553f, 25f, 155f, 773f, 1448f),
        floatArrayOf(0.587f, 25f, 156f, 773f, 1448f),
        floatArrayOf(0.654f, 25f, 156f, 773f, 1449f),
        floatArrayOf(0.738f, 24f, 156f, 774f, 1449f),
        floatArrayOf(0.800f, 24f, 156f, 774f, 1449f),
    )
)

/** OPEN corner radius (clamped to half of the smaller side): [t, radius] */
private val OPEN_R = Track(
    arrayOf(
        floatArrayOf(0.084f, 66f),      // pill radius (half height) -> droplet
        floatArrayOf(0.118f, 400f),
        floatArrayOf(0.217f, 340f),
        floatArrayOf(0.252f, 310f),
        floatArrayOf(0.268f, 274f),
        floatArrayOf(0.295f, 202f),
        floatArrayOf(0.318f, 178f),
        floatArrayOf(0.334f, 138f),
        floatArrayOf(0.385f, 98f),
        floatArrayOf(0.453f, 90f),
        floatArrayOf(0.550f, 94f),
        floatArrayOf(0.800f, 94f),
    )
)

/** OPEN menu content: [t, blurSigma, alpha, scale] (scale is about the card centre) */
private val OPEN_CONTENT = Track(
    arrayOf(
        floatArrayOf(0.118f, 28f, 0.00f, 1.100f),
        floatArrayOf(0.136f, 26f, 0.25f, 1.100f),
        floatArrayOf(0.163f, 22f, 0.40f, 1.100f),
        floatArrayOf(0.176f, 20f, 0.45f, 1.090f),
        floatArrayOf(0.217f, 13f, 0.75f, 1.080f),
        floatArrayOf(0.252f, 8f, 0.90f, 1.080f),
        floatArrayOf(0.268f, 5.5f, 0.96f, 1.075f),
        floatArrayOf(0.295f, 3.5f, 1.00f, 1.070f),
        floatArrayOf(0.318f, 2.2f, 1.00f, 1.060f),
        floatArrayOf(0.334f, 1.0f, 1.00f, 1.035f),
        floatArrayOf(0.352f, 0f, 1.00f, 1.020f),
        floatArrayOf(0.369f, 0f, 1.00f, 1.012f),
        floatArrayOf(0.385f, 0f, 1.00f, 1.010f),
        floatArrayOf(0.418f, 0f, 1.00f, 1.007f),
        floatArrayOf(0.453f, 0f, 1.00f, 1.003f),
        floatArrayOf(0.500f, 0f, 1.00f, 1.000f),
        floatArrayOf(0.800f, 0f, 1.00f, 1.000f),
    )
)

/** OPEN pill (press + squash): [t, left, top, right, bottom, gray(0..255), contentAlpha, alpha] */
private val OPEN_PILL = Track(
    arrayOf(
        floatArrayOf(0.000f, 216f, 156f, 517f, 288f, 31f, 1.00f, 1f),
        floatArrayOf(0.025f, 216f, 156f, 517f, 288f, 43f, 1.00f, 1f),
        floatArrayOf(0.040f, 216f, 156f, 517f, 288f, 60f, 0.90f, 1f),
        floatArrayOf(0.066f, 216f, 156f, 517f, 288f, 87f, 0.75f, 1f),
        floatArrayOf(0.084f, 232f, 163f, 498f, 292f, 86f, 0.45f, 1f),
        floatArrayOf(0.098f, 232f, 163f, 498f, 292f, 86f, 0.00f, 1f),   // icons are gone, body takes over at 0.084
    )
)

/** CLOSE card bounds: [t, left, top, right, bottom] */
private val CLOSE_BOX = Track(
    arrayOf(
        floatArrayOf(0.000f, 24f, 156f, 774f, 1449f),
        floatArrayOf(0.016f, 29f, 203f, 764f, 1460f),
        floatArrayOf(0.032f, 42f, 276f, 739f, 1442f),
        floatArrayOf(0.066f, 61f, 332f, 704f, 1375f),
        floatArrayOf(0.082f, 81f, 364f, 664f, 1271f),
        floatArrayOf(0.100f, 126f, 370f, 578f, 1016f),
        floatArrayOf(0.132f, 167f, 330f, 498f, 769f),
        floatArrayOf(0.150f, 185f, 300f, 463f, 663f),
        floatArrayOf(0.166f, 201f, 268f, 432f, 572f),
        floatArrayOf(0.184f, 216f, 236f, 405f, 495f),
        floatArrayOf(0.216f, 238f, 200f, 364f, 381f),
        floatArrayOf(0.234f, 248f, 175f, 345f, 340f),
        floatArrayOf(0.267f, 262f, 150f, 332f, 290f),
        floatArrayOf(0.283f, 281f, 150f, 313f, 262f),   // FIX: tail is fully inside the pill before it is removed (no pop)
    )
)

/** CLOSE corner radius: [t, radius] (clamped to half of the smaller side) */
private val CLOSE_R = Track(
    arrayOf(
        floatArrayOf(0.000f, 94f),
        floatArrayOf(0.016f, 110f),
        floatArrayOf(0.032f, 150f),
        floatArrayOf(0.066f, 230f),
        floatArrayOf(0.082f, 400f),
    )
)

/** CLOSE menu content: [t, blurSigma, alpha, scale] */
private val CLOSE_CONTENT = Track(
    arrayOf(
        floatArrayOf(0.000f, 0.0f, 1.00f, 1.000f),
        floatArrayOf(0.016f, 0.5f, 1.00f, 0.985f),
        floatArrayOf(0.032f, 4.0f, 0.92f, 0.970f),
        floatArrayOf(0.066f, 9.0f, 0.60f, 0.950f),
        floatArrayOf(0.082f, 13f, 0.35f, 0.940f),
        floatArrayOf(0.100f, 16f, 0.12f, 0.930f),
        floatArrayOf(0.120f, 18f, 0.00f, 0.920f),
    )
)

/**
 * CLOSE pill blob that re-forms + overshoots: [t, left, top, right, bottom, alpha]
 * FIX: rows up to 0.267 were re-fitted against the ORIGINAL video frame by frame (goo simulation,
 * IoU ~0.97): the bud is born as a ROUND bud of 70 x 78 ref px (not a flat 97 x 55 pill) and
 * grows into the pill the same way the original does.
 */
private val CLOSE_PILL = Track(
    arrayOf(
        floatArrayOf(0.110f, 323f, 230f, 363f, 270f, 0f),
        floatArrayOf(0.118f, 308f, 209f, 378f, 287f, 1f),   // original bud size, exact
        floatArrayOf(0.132f, 308f, 209f, 378f, 287f, 1f),
        floatArrayOf(0.150f, 277f, 178f, 414f, 306f, 1f),
        floatArrayOf(0.166f, 262f, 160f, 435f, 283f, 1f),
        floatArrayOf(0.184f, 250f, 150f, 454f, 269f, 1f),
        floatArrayOf(0.216f, 234f, 137f, 482f, 261f, 1f),
        floatArrayOf(0.234f, 229f, 134f, 490f, 260f, 1f),
        floatArrayOf(0.267f, 221f, 134f, 504f, 263f, 1f),
        floatArrayOf(0.283f, 221f, 136f, 510f, 273f, 1f),
        floatArrayOf(0.300f, 219f, 138f, 514f, 270f, 1f),
        floatArrayOf(0.316f, 217f, 140f, 516f, 272f, 1f),
        floatArrayOf(0.334f, 216f, 142f, 518f, 275f, 1f),
        floatArrayOf(0.350f, 215f, 144f, 520f, 277f, 1f),
        floatArrayOf(0.367f, 215f, 146f, 520f, 280f, 1f),
        floatArrayOf(0.384f, 215f, 147f, 521f, 281f, 1f),
        floatArrayOf(0.403f, 214f, 149f, 521f, 283f, 1f),
        floatArrayOf(0.417f, 214f, 150f, 521f, 284f, 1f),
        floatArrayOf(0.503f, 215f, 155f, 520f, 288f, 1f),
        floatArrayOf(0.570f, 216f, 156f, 517f, 288f, 1f),
    )
)

/** CLOSE pill icons alpha (dots + share): [t, alpha] – measured from the original (fades in 0.17 -> 0.27) */
private val CLOSE_PILL_CONTENT = Track(
    arrayOf(
        floatArrayOf(0.000f, 0.00f),
        floatArrayOf(0.150f, 0.00f),
        floatArrayOf(0.166f, 0.20f),
        floatArrayOf(0.184f, 0.50f),
        floatArrayOf(0.216f, 0.86f),
        floatArrayOf(0.234f, 0.92f),
        floatArrayOf(0.267f, 1.00f),
        floatArrayOf(0.600f, 1.00f),
    )
)

// ---------------------------------------------------------------------------
// Frame evaluation
// ---------------------------------------------------------------------------

internal enum class Phase { Closed, Opening, Open, Closing }

internal class Frame(
    val body: FloatArray?,      // l, t, r, b (reference px)
    val bodyR: Float,
    val bodyGray: Float,        // body colour 0..255 (the pressed pill is brighter while it morphs into the droplet)
    val pill: FloatArray?,      // l, t, r, b  (also the anchor of the pill icons)
    val pillShape: Boolean,     // draw the pill rect itself? (false while it is morphing as the body)
    val pillGray: Float,
    val pillContent: Float,
    val pillAlpha: Float,
    val iconFixed: Boolean,     // closing: icons keep a FIXED x / size (like the original) and only follow the pill's y
    val menu: FloatArray?,      // sigma, alpha, scale
    val goo: Boolean,
    val rim: Float,
)

internal fun smooth01(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

private fun clampR(r: Float, b: FloatArray) = min(r, min(b[2] - b[0], b[3] - b[1]) / 2f)

internal fun frameFor(phase: Phase, t: Float): Frame = when (phase) {
    Phase.Closed -> Frame(null, 0f, 31f, REST_PILL, true, 31f, 1f, 1f, false, null, false, 1f)

    Phase.Open -> Frame(FINAL_BODY, 94f, 31f, null, false, 31f, 0f, 0f, false, floatArrayOf(0f, 1f, 1f), false, 1f)

    Phase.Opening -> {
        val p = OPEN_PILL.eval(t)
        // FIX: from t = 0.084 the pill IS the body (it morphs pill -> droplet), so there is no cap on top.
        val morph = t >= 0.084f
        val body = if (morph) OPEN_BOX.eval(t) else null
        Frame(
            body = body,
            bodyR = body?.let { clampR(OPEN_R.eval(t)[0], it) } ?: 0f,
            bodyGray = if (morph) 86f + (31f - 86f) * smooth01((t - 0.084f) / (0.118f - 0.084f)) else 31f,
            pill = when {
                !morph -> floatArrayOf(p[0], p[1], p[2], p[3])
                t < 0.098f -> body            // only anchors the fading icons
                else -> null
            },
            pillShape = !morph,
            pillGray = p[4],
            pillContent = p[5],
            pillAlpha = p[6],
            iconFixed = false,
            menu = if (t >= 0.118f) OPEN_CONTENT.eval(t) else null,
            goo = false,
            rim = 1f,
        )
    }

    Phase.Closing -> {
        // FIX: the tail is removed only once it is fully inside the pill (0.283) – no pop.
        val body = if (t < 0.283f) CLOSE_BOX.eval(t) else null
        val pillV = if (t >= 0.110f) CLOSE_PILL.eval(t) else null
        val goo = t in 0.09f..0.30f
        // FIX: the glass rim fades out before the merge and fades back in after it (it used to pop on/off).
        val rim = if (t < 0.283f) (1f - (t - 0.07f) / 0.03f).coerceIn(0f, 1f)
        else smooth01((t - 0.283f) / 0.12f)
        Frame(
            body = body,
            bodyR = body?.let { clampR(CLOSE_R.eval(t)[0], it) } ?: 0f,
            bodyGray = 31f,
            pill = pillV?.let { floatArrayOf(it[0], it[1], it[2], it[3]) },
            pillShape = true,
            pillGray = 31f,
            pillContent = CLOSE_PILL_CONTENT.eval(t)[0],
            pillAlpha = pillV?.get(4) ?: 0f,
            iconFixed = true,
            menu = if (t < 0.12f) CLOSE_CONTENT.eval(t) else null,
            goo = goo,
            rim = rim,
        )
    }
}

/**
 * Monotone cubic Hermite (Fritsch-Carlson / PCHIP) over irregular time stamps.
 * Row = [t, v0, v1, ...]. Keeps the measured overshoot without adding new ones.
 */
private class Track(rows: Array<FloatArray>) {
    private val n = rows.size
    private val ch = rows[0].size - 1
    private val ts = FloatArray(n) { rows[it][0] }
    private val vs = Array(ch) { c -> FloatArray(n) { rows[it][c + 1] } }
    private val ms = Array(ch) { c -> tangents(vs[c]) }

    private fun tangents(v: FloatArray): FloatArray {
        if (n == 1) return FloatArray(1)
        val d = FloatArray(n - 1) { (v[it + 1] - v[it]) / (ts[it + 1] - ts[it]) }
        val s = FloatArray(n)
        s[0] = d[0]
        s[n - 1] = d[n - 2]
        for (i in 1 until n - 1) {
            s[i] = if (d[i - 1] * d[i] <= 0f) {
                0f
            } else {
                val h0 = ts[i] - ts[i - 1]
                val h1 = ts[i + 1] - ts[i]
                val w1 = 2f * h1 + h0
                val w2 = h1 + 2f * h0
                (w1 + w2) / (w1 / d[i - 1] + w2 / d[i])
            }
        }
        return s
    }

    fun eval(t: Float): FloatArray {
        val out = FloatArray(ch)
        if (t <= ts[0]) {
            for (c in 0 until ch) out[c] = vs[c][0]
            return out
        }
        if (t >= ts[n - 1]) {
            for (c in 0 until ch) out[c] = vs[c][n - 1]
            return out
        }
        var i = 0
        while (t > ts[i + 1]) i++
        val h = ts[i + 1] - ts[i]
        val x = (t - ts[i]) / h
        val x2 = x * x
        val x3 = x2 * x
        val h00 = 2f * x3 - 3f * x2 + 1f
        val h10 = x3 - 2f * x2 + x
        val h01 = -2f * x3 + 3f * x2
        val h11 = x3 - x2
        for (c in 0 until ch) {
            out[c] = h00 * vs[c][i] + h10 * h * ms[c][i] + h01 * vs[c][i + 1] + h11 * h * ms[c][i + 1]
        }
        return out
    }
}

private class BoxShape(
    private val l: Float, private val t: Float, private val r: Float, private val b: Float,
    private val radius: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(l, t, r, b, CornerRadius(radius)))
}

/** Blur -> alpha threshold = metaball ("gooey") merge between the card and the pill. */
@RequiresApi(Build.VERSION_CODES.S)
internal fun gooEffect(blurPx: Float): RenderEffect {
    val blur = android.graphics.RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.DECAL)
    val m = ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            // threshold = 0.5 -> an isolated shape keeps its exact size (the old 9/22 threshold swelled it by ~2px
            // and it "jumped" back when the filter was switched off = the little shake of the button)
            0f, 0f, 0f, 22f, -11f * 255f,
        )
    )
    return android.graphics.RenderEffect
        .createColorFilterEffect(ColorMatrixColorFilter(m), blur)
        .asComposeRenderEffect()
}

