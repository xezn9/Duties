package com.duties.ui

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.ComposeShader
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** The coral of HyperSpring. Change it here to recolor all the dots animations. */
val DOTS_ACCENT: Color get() = AppAccent.pick(Color(0xFFFF5F45))

private val DOTS_POWER3_OUT = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

/**
 * Black page with a grid of faint dots (ported from HyperSpring's LiveBackground) and three
 * "coloring" animations that light the dots up:
 *  1. finger  : the dots under your finger light up in a circle that fades outwards, and go out slowly
 *               when you lift it (needs [dotsTouch] on the parent).
 *  2. ambient : two soft lights drift over the grid by themselves and slowly breathe.
 *  3. wave    : a ring of lit dots opens from a point and crosses the page ([DotsState.playWave]).
 */
@Stable
class DotsState(private val scope: CoroutineScope) {
    internal var touchX by mutableFloatStateOf(0f)
    internal var touchY by mutableFloatStateOf(0f)
    internal val glow = Animatable(0f)
    private var down = false

    internal var waveX by mutableFloatStateOf(0f)
    internal var waveY by mutableFloatStateOf(0f)
    internal val wave = Animatable(1f)               // 0..1, 1 = no wave
    /** true = the ring follows the tab-switch circle exactly (same start radius, same easing, same end). */
    internal var waveMatch by mutableStateOf(false)

    internal fun press(p: Offset) {
        touchX = p.x
        touchY = p.y
        if (!down) {
            down = true
            scope.launch { glow.animateTo(1f, tween(220, easing = DOTS_POWER3_OUT)) }
        }
    }

    internal fun release() {
        if (down) {
            down = false
            scope.launch { glow.animateTo(0f, tween(700, easing = DOTS_POWER3_OUT)) }
        }
    }

    /**
     * Ring of lit dots opening from [origin] (in px, in the coordinates of the page).
     * [fromTab] = true when the page is revealed by the tab circle: the ring then rides right behind
     * the edge of that circle (same 900 ms and same easing), so it is seen at once and not after it.
     */
    fun playWave(origin: Offset, fromTab: Boolean = false) {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        waveX = origin.x
        waveY = origin.y
        waveMatch = fromTab
        scope.launch {
            wave.snapTo(0f)
            if (fromTab) wave.animateTo(1f, tween(900, easing = WAVE))
            else wave.animateTo(1f, tween(1050, easing = LinearEasing))
        }
    }
}

@Composable
fun rememberDotsState(): DotsState {
    val scope = rememberCoroutineScope()
    return remember { DotsState(scope) }
}

/**
 * Put this on the parent that holds the page (the background and the content): it watches the
 * finger without consuming anything, so scrolling and buttons keep working.
 */
fun Modifier.dotsTouch(state: DotsState): Modifier = pointerInput(state) {
    awaitPointerEventScope {
        while (true) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = ev.changes.firstOrNull { it.pressed }
            if (pressed != null && ValueAnimator.areAnimatorsEnabled()) state.press(pressed.position)
            else state.release()
        }
    }
}

/**
 * Plays the opening wave once. [from] = the tab button that was tapped (root px): the wave starts at that
 * very moment, together with the tab circle. Without it (first opening of the app) it starts [delayMs] later,
 * from a point near the title.
 */
@Composable
fun DotsEntranceWave(
    state: DotsState,
    delayMs: Int,
    from: Offset? = null,
    originX: Dp = 110.dp,
    originY: Dp = 70.dp
) {
    val density = LocalDensity.current
    val statusTop = WindowInsets.statusBars.getTop(density)
    LaunchedEffect(Unit) {
        if (from != null) {
            state.playWave(from, fromTab = true)
            return@LaunchedEffect
        }
        delay(delayMs.toLong())
        state.playWave(
            Offset(
                with(density) { originX.toPx() },
                statusTop + with(density) { originY.toPx() }
            )
        )
    }
}

private class DotPaints(density: Float, dark: Boolean, accent: Color) {
    val base = if (dark) 0xFF000000.toInt() else 0xFFF4F5F9.toInt()
    val dots = Paint()
    val litDots: BitmapShader
    val lit = Paint()
    val amb = Paint()
    val wave = Paint()

    init {
        val cell = max(8, (22 * density).roundToInt())
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val tile = Bitmap.createBitmap(cell, cell, Bitmap.Config.ARGB_8888)
        val dim = if (dark) 0x2A else 0x38
        p.color = (dim shl 24) or (if (dark) 0xFFFFFF else 0x000000)
        android.graphics.Canvas(tile).drawCircle(cell / 2f, cell / 2f, max(1f, 0.9f * density), p)
        dots.shader = BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)

        val l = Bitmap.createBitmap(cell, cell, Bitmap.Config.ARGB_8888)
        p.color = accent.toArgb()
        android.graphics.Canvas(l).drawCircle(cell / 2f, cell / 2f, max(1.5f, 1.7f * density), p)
        litDots = BitmapShader(l, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }
}

@Composable
fun DotBackground(
    state: DotsState,
    dark: Boolean,
    modifier: Modifier = Modifier.fillMaxSize(),
    accent: Color = DOTS_ACCENT,
    ambient: Boolean = true
) {
    val density = LocalDensity.current.density
    val paints = remember(density, dark, accent) { DotPaints(density, dark, accent) }

    // seconds since the page appeared: drives the two drifting lights (only read while drawing)
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(ambient) {
        if (!ambient || !ValueAnimator.areAnimatorsEnabled()) return@LaunchedEffect
        val t0 = androidx.compose.runtime.withFrameNanos { it }
        while (true) androidx.compose.runtime.withFrameNanos { clock.floatValue = (it - t0) / 1e9f }
    }

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val dp = density
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            nc.drawColor(paints.base)
            nc.drawRect(0f, 0f, w, h, paints.dots)

            fun light(x: Float, y: Float, r: Float, a: Float, paint: Paint) {
                val mask = RadialGradient(
                    x, y, r,
                    intArrayOf(0xFF000000.toInt(), 0x66000000, 0x00000000),
                    floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP
                )
                paint.shader = ComposeShader(mask, paints.litDots, PorterDuff.Mode.SRC_IN)
                paint.alpha = (255 * a).roundToInt().coerceIn(0, 255)
                nc.drawCircle(x, y, r, paint)
            }

            // 2. two lights that drift by themselves (Lissajous curves, 31 s and 23 s)
            if (ambient && w > 0f) {
                val t = clock.floatValue
                val p1 = 2 * PI * t / 31.0
                val p2 = 2 * PI * t / 23.0
                val x1 = w * (0.5f + 0.38f * sin(p1).toFloat())
                val y1 = h * (0.22f + 0.12f * sin(2 * p1 + 0.7).toFloat())
                val x2 = w * (0.5f + 0.42f * cos(p2 * 1.3).toFloat())
                val y2 = h * (0.68f + 0.16f * sin(p2).toFloat())
                val a1 = 0.30f + 0.10f * sin(2 * PI * t / 7.0).toFloat()
                val a2 = 0.20f + 0.08f * sin(2 * PI * t / 9.0 + 1.3).toFloat()
                val enter = min(1f, t / 1.6f)
                // third light low on the page: the glass tab bar always has lit dots to blur and bend
                val x3 = w * (0.5f + 0.40f * sin(2 * PI * t / 27.0 + 2.1).toFloat())
                val y3 = h * 0.93f
                val a3 = 0.24f + 0.08f * sin(2 * PI * t / 11.0).toFloat()
                light(x1, y1, 190f * dp, a1 * enter, paints.amb)
                light(x2, y2, 150f * dp, a2 * enter, paints.amb)
                light(x3, y3, 150f * dp, a3 * enter, paints.amb)
            }

            // 1. circle of lit dots under the finger
            val g = state.glow.value
            if (g > 0.01f) {
                val r = 120f * dp
                val mask = RadialGradient(
                    state.touchX, state.touchY, r,
                    0xFF000000.toInt(), 0x00000000, Shader.TileMode.CLAMP
                )
                paints.lit.shader = ComposeShader(mask, paints.litDots, PorterDuff.Mode.SRC_IN)
                paints.lit.alpha = (255 * g).roundToInt().coerceIn(0, 255)
                nc.drawCircle(state.touchX, state.touchY, r, paints.lit)
            }

            // 3. opening wave: strong coral at the front, a fading trail behind it
            val p = state.wave.value
            if (p < 1f) {
                val ox = state.waveX
                val oy = state.waveY
                val match = state.waveMatch
                val far = hypot(max(ox, w - ox), max(oy, h - oy))
                val radius = if (match) {
                    // same radius as the tab circle: 26 dp at the start, far corner + 40 dp at the end
                    val startR = 26f * dp
                    startR + (far + 40f * dp - startR) * p
                } else {
                    (far + 90f * dp) * (1f - (1f - p) * (1f - p))   // fast at first, then slows
                }
                val seg = ((p - (if (match) 0.7f else 0.55f)) / (if (match) 0.3f else 0.45f)).coerceIn(0f, 1f)
                val alpha = 0.9f * (1f - seg)
                if (alpha > 0.01f && radius > 1f) {
                    val band = 90f * dp
                    val outer = radius + 6f * dp
                    val inner = max(0f, (radius - band) / outer)
                    val mid = radius / outer
                    val ring = RadialGradient(
                        ox, oy, outer,
                        intArrayOf(0x00000000, 0x00000000, 0xFF000000.toInt(), 0x00000000),
                        floatArrayOf(0f, inner, mid, 1f), Shader.TileMode.CLAMP
                    )
                    paints.wave.shader = ComposeShader(ring, paints.litDots, PorterDuff.Mode.SRC_IN)
                    paints.wave.alpha = (255 * min(1f, alpha)).roundToInt()
                    nc.drawCircle(ox, oy, outer, paints.wave)
                }
            }
        }
    }
}
