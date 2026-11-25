package com.duties.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.hypot
import kotlin.math.max

/**
 * Switches between the app's big states (checking / login / main) with an animation.
 *
 * - When [waveOrigin] gives a point for the change (from -> to), the new page is born as a circle at that
 *   point and grows over the old one, with the same liquid-glass rim and "water push" of the tab wave
 *   (see TabRevealHost). Used for login -> main, starting at the Google button.
 * - When it gives null, the new page fades in softly (scale 0.96 -> 1).
 *
 * @param page content of a state; [viaWave] is true when this page is arriving with the wave, so it can
 *             start its own entrance (cascade) a bit later, when the circle reaches it.
 */
@Composable
fun AuthWaveHost(
    state: Int,
    waveOrigin: (from: Int, to: Int) -> Offset?,
    modifier: Modifier = Modifier,
    ringColor: Color = AppAccent.pick(Color(0xFF5AC8FA)),
    page: @Composable (state: Int, viaWave: Boolean) -> Unit
) {
    var current by remember { mutableIntStateOf(state) }
    var old by remember { mutableStateOf<Int?>(null) }
    var wave by remember { mutableStateOf(false) }
    var hostSize by remember { mutableStateOf(IntSize.Zero) }
    var center by remember { mutableStateOf(Offset.Zero) }
    val progress = remember { Animatable(0f) }
    val rim = remember { shaderOrNull(RIM_SHADER) }
    val push = remember { shaderOrNull(PUSH_SHADER) }

    LaunchedEffect(state) {
        if (state == current) return@LaunchedEffect
        val from = current
        val c = waveOrigin(from, state)
        progress.snapTo(0f)
        old = from
        current = state
        if (c != null && hostSize != IntSize.Zero) {
            center = c
            wave = true
            progress.animateTo(1f, tween(1050, easing = WAVE))
        } else {
            wave = false
            progress.animateTo(1f, tween(420, easing = SETTLE))
        }
        old = null
    }

    Box(modifier.onSizeChanged { hostSize = it }) {
        val animating = old != null
        for (idx in listOfNotNull(old, current)) {
            key(idx) {
                val isNew = idx == current
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (!animating) return@graphicsLayer
                            val t = progress.value

                            if (!wave) {
                                // Soft change: the new page fades in and settles; the old one stays below.
                                if (isNew) {
                                    alpha = t
                                    val s = 0.96f + 0.04f * t
                                    scaleX = s
                                    scaleY = s
                                }
                                return@graphicsLayer
                            }

                            val cx = center.x
                            val cy = center.y
                            val w = hostSize.width.toFloat().coerceAtLeast(1f)
                            val h = hostSize.height.toFloat().coerceAtLeast(1f)
                            val maxR = hypot(max(cx, w - cx), max(cy, h - cy)) + 40.dp.toPx()
                            val startR = 26.dp.toPx()
                            val r = startR + (maxR - startR) * t
                            val force = 1f - t * t * t
                            transformOrigin = TransformOrigin(cx / w, cy / h)

                            if (isNew) {
                                val s = 1.05f - 0.05f * t
                                scaleX = s
                                scaleY = s
                                clip = true
                                shape = CircleAt(Offset(cx, cy), r)
                                if (Build.VERSION.SDK_INT >= 33 && rim != null) {
                                    rim.setFloatUniform("center", cx, cy)
                                    rim.setFloatUniform("width", 34.dp.toPx())
                                    rim.setFloatUniform("radius", r)
                                    rim.setFloatUniform("amount", 26.dp.toPx() * force)
                                    renderEffect = android.graphics.RenderEffect
                                        .createRuntimeShaderEffect(rim, "content")
                                        .asComposeRenderEffect()
                                }
                            } else {
                                val k = 1f - 0.08f * t
                                scaleX = k
                                scaleY = k
                                alpha = 1f - 0.55f * t
                                if (Build.VERSION.SDK_INT >= 31) {
                                    var e: android.graphics.RenderEffect? = null
                                    val b = 10.dp.toPx() * t
                                    if (b >= 0.5f) {
                                        e = android.graphics.RenderEffect.createBlurEffect(
                                            b, b, android.graphics.Shader.TileMode.CLAMP
                                        )
                                    }
                                    if (Build.VERSION.SDK_INT >= 33 && push != null) {
                                        push.setFloatUniform("center", cx, cy)
                                        push.setFloatUniform("width", 40.dp.toPx())
                                        push.setFloatUniform("radius", r / k)
                                        push.setFloatUniform("amount", 48.dp.toPx() * force)
                                        val pe = android.graphics.RenderEffect
                                            .createRuntimeShaderEffect(push, "content")
                                        e = if (e == null) pe
                                        else android.graphics.RenderEffect.createChainEffect(e, pe)
                                    }
                                    renderEffect = e?.asComposeRenderEffect()
                                }
                            }
                        }
                ) {
                    page(idx, wave)
                }
            }
        }

        // Ring of light on the front of the wave.
        if (animating && wave) {
            Canvas(Modifier.fillMaxSize()) {
                val t = progress.value
                if (t >= 1f) return@Canvas
                val w = size.width
                val h = size.height
                val maxR = hypot(max(center.x, w - center.x), max(center.y, h - center.y)) + 40.dp.toPx()
                val startR = 26.dp.toPx()
                val r = startR + (maxR - startR) * t
                val fade = 1f - t
                rotate(180f * t, pivot = center) {
                    drawCircle(
                        ringColor.copy(alpha = 0.22f * fade), radius = r, center = center,
                        style = Stroke(16.dp.toPx())
                    )
                    drawCircle(
                        ringColor.copy(alpha = 0.78f * fade), radius = r, center = center,
                        style = Stroke(2.dp.toPx())
                    )
                }
            }
        }
    }
}
