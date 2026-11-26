package com.duties.glassmenu

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Button -> window morph animation (measured from the reference video), drawn with
 * Liquid Glass from `io.github.kyant0:backdrop:2.0.0`.
 *
 * The glass body is a RoundedRectangle (the `lens` effect needs a rounded-rect SDF, so a
 * free-form path can't be used), a second small glass Capsule stays at the button position
 * and shrinks away = the "droplet" you see while the button stretches into the window.
 */
object MorphSpec {
    val PanelWidth = 214.dp
    val PanelHeight = 305.dp
    val PanelCorner = 34.dp
    val ButtonSize = 53.dp

    /** button centre relative to the panel's top-right corner */
    val BtnFromRight = 23.dp
    val BtnFromTop = 22.dp

    /** body sags down + pulls in from the right while travelling */
    val Sag = 20.dp
    val RightPull = 14.dp

    /** room around the panel so spring overshoot / button are never clipped */
    val PadTop = 8.dp
    val PadEnd = 8.dp
    val PadStart = 16.dp
    val PadBottom = 16.dp

    // open spring
    const val OpenDamping = 0.68f
    const val OpenStiffness = 520f

    // close tween
    const val CloseMillis = 250
    val CloseEasing = CubicBezierEasing(0.45f, 0f, 0.85f, 0.55f)

    // glass look
    val GlassTint = Color(0xFF1C1C1E)
}

private val PI_F = PI.toFloat()
private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

/** never let NaN / Infinity reach layout */
private fun Float.safe(default: Float = 0f) = if (isNaN() || isInfinite()) default else this

/** all values in dp, in the container's coordinate space */
private class MorphGeometry(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val corner: Float,
    val dropletCx: Float,
    val dropletCy: Float,
    val dropletR: Float,
)

private fun morphGeometry(p: Float): MorphGeometry {
    val s = MorphSpec
    val panelL = s.PadStart.value
    val panelT = s.PadTop.value
    val panelR = panelL + s.PanelWidth.value
    val panelB = panelT + s.PanelHeight.value
    val br = s.ButtonSize.value / 2f
    val cx = panelR - s.BtnFromRight.value
    val cy = panelT + s.BtnFromTop.value

    val pc = p.coerceAtLeast(0f)
    val p01 = pc.coerceIn(0f, 1f)
    // sin(PI_F) is slightly NEGATIVE in Float -> negative.pow(1.5f) = NaN -> roundToInt() crash
    val bump = sin(PI_F * p01).coerceAtLeast(0f).pow(1.5f)
    val lead = pc + 0.5f * p01 * (1f - p01) // bottom edge leads: the drop falls first

    val left = lerpF(cx - br, panelL, pc)
    val top = lerpF(cy - br, panelT, pc) + s.Sag.value * bump
    val right = lerpF(cx + br, panelR, pc) - s.RightPull.value * bump
    val bottom = lerpF(cy + br, panelB, lead)

    val w = (right - left).safe(1f)
    val h = (bottom - top).safe(1f)
    val corner = min(lerpF(br, s.PanelCorner.value, p01), min(w, h) / 2f).safe(br).coerceAtLeast(1f)

    val rd = if (p01 < 0.08f || pc >= 0.92f) 0f else (br * (1f - pc)).safe(0f)
    return MorphGeometry(
        left.safe(cx - br), top.safe(cy - br), right.safe(cx + br), bottom.safe(cy + br),
        corner, cx, cy, rd,
    )
}

@Composable
fun GlassMorphMenu(
    backdrop: Backdrop,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    buttonContent: @Composable () -> Unit = { DefaultAvatarIcon() },
    menuContent: @Composable ColumnScope.() -> Unit,
) {
    val outerDirection = LocalLayoutDirection.current
    val spec = MorphSpec
    val anim = remember { Animatable(0f) }
    LaunchedEffect(expanded) {
        if (expanded) {
            anim.animateTo(1f, spring(dampingRatio = spec.OpenDamping, stiffness = spec.OpenStiffness))
        } else {
            anim.animateTo(0f, tween(spec.CloseMillis, easing = spec.CloseEasing))
        }
    }
    // read lazily (layout / draw phase) so the animation doesn't recompose anything
    val progress: () -> Float = { anim.value }

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressAlpha by animateFloatAsState(if (pressed) 0.14f else 0f, tween(80), label = "press")

    // the morph geometry is absolute (button on the physical right) -> always lay it out LTR,
    // so it behaves the same on Arabic / RTL phones
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Box(
        modifier = modifier.size(
            width = spec.PadStart + spec.PanelWidth + spec.PadEnd,
            height = spec.PadTop + spec.PanelHeight + spec.PadBottom,
        ),
    ) {
        // ── 1) glass body (button == window, same box morphing) ──
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val g = morphGeometry(progress())
                    val w = ((g.right - g.left) * density).roundToInt().coerceAtLeast(1)
                    val h = ((g.bottom - g.top) * density).roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeable.place((g.left * density).roundToInt(), (g.top * density).roundToInt())
                    }
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(morphGeometry(progress()).corner.dp) },
                    effects = {
                        vibrancy()
                        blur(6f.dp.toPx())
                        lens(12f.dp.toPx(), 24f.dp.toPx())
                    },
                    onDrawSurface = {
                        val t = progress().coerceIn(0f, 1f)
                        drawRect(spec.GlassTint.copy(alpha = lerpF(0.22f, 0.5f, t)))
                    },
                ),
        ) {
            // ── 2) menu content: fades in / un-blurs / scales, clipped by the body ──
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        shape = RoundedRectangle(morphGeometry(progress()).corner.dp)
                        clip = true
                    },
                contentAlignment = Alignment.TopEnd,
            ) {
                Column(
                    Modifier
                        .requiredSize(spec.PanelWidth, spec.PanelHeight)
                        .graphicsLayer {
                            val p = progress()
                            val a = ((p - 0.5f) / 0.35f).coerceIn(0f, 1f)
                            alpha = a
                            val sc = lerpF(0.85f, 1f, p.coerceIn(0f, 1f))
                            scaleX = sc
                            scaleY = sc
                            transformOrigin = TransformOrigin(1f, 0f)
                            val blurPx = (1f - a) * 14f * density
                            renderEffect = if (blurPx > 0.5f) BlurEffect(blurPx, blurPx, TileMode.Decal) else null
                        }
                        .padding(vertical = 12.dp),
                ) {
                    // menu rows follow the app's real layout direction (Arabic rows stay RTL)
                    CompositionLocalProvider(LocalLayoutDirection provides outerDirection) { menuContent() }
                }
            }
        }

        // ── 3) the droplet that stays behind at the button and shrinks away ──
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val g = morphGeometry(progress())
                    val d = (g.dropletR * 2f * density).roundToInt()
                    if (d < 2) {
                        layout(constraints.maxWidth, constraints.maxHeight) {}
                    } else {
                        val placeable = measurable.measure(Constraints.fixed(d, d))
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place(
                                ((g.dropletCx - g.dropletR) * density).roundToInt(),
                                ((g.dropletCy - g.dropletR) * density).roundToInt(),
                            )
                        }
                    }
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(4f.dp.toPx())
                        lens(8f.dp.toPx(), 16f.dp.toPx())
                    },
                    onDrawSurface = { drawRect(spec.GlassTint.copy(alpha = 0.3f)) },
                ),
        )

        // ── 4) the round button (icon + press highlight), clickable only while collapsed ──
        Box(
            modifier = Modifier
                .offset(
                    x = spec.PadStart + spec.PanelWidth - spec.BtnFromRight - spec.ButtonSize / 2,
                    y = spec.PadTop + spec.BtnFromTop - spec.ButtonSize / 2,
                )
                .size(spec.ButtonSize)
                .clip(CircleShape)
                .drawBehind { drawCircle(Color.White.copy(alpha = pressAlpha)) }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    enabled = !expanded,
                ) { onExpandedChange(true) },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.graphicsLayer { alpha = 1f - (progress() * 5f).coerceIn(0f, 1f) }) {
                buttonContent()
            }
        }
    }
    }
}

@Composable
fun DefaultAvatarIcon() {
    Image(
        imageVector = Icons.Filled.Person,
        contentDescription = null,
        colorFilter = ColorFilter.tint(Color(0xFFD0D0D5)),
        modifier = Modifier.size(28.dp),
    )
}

@Composable
fun GlassMenuRow(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 49.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 30.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            imageVector = icon,
            contentDescription = null,
            colorFilter = ColorFilter.tint(Color.White),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(13.dp))
        BasicText(
            text = label,
            style = TextStyle(color = Color.White, fontSize = 15.sp, lineHeight = 20.sp),
        )
    }
}
