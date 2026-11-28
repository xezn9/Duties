package com.duties.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.liquid.morph.FINAL_BODY
import com.liquid.morph.OPEN_T
import com.liquid.morph.Phase
import com.liquid.morph.frameFor
import com.liquid.morph.smooth01
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * StickyMorphGlass.kt — the card of the notifications button as STICKY LIQUID GLASS.
 *
 * Before, the card was a solid grey shape that the "goo" filter (blur + alpha threshold) melted into the button.
 * Now the card (and the button, while they are close) are ONE piece of liquid glass: the glass of the app
 * (blur + refraction + the same dark grey as tint) drawn with [stickyLens], so the drop / the card and the button
 * stay joined by a viscous neck that stretches and snaps - exactly like the StickyGlass demo.
 *
 * The shapes come from the SAME measured timeline as before (frameFor), so the motion of the card is unchanged;
 * only what is drawn at those places changed:
 *   - opening: the button the drop was born from stays behind as a glass blob (it drains into the card), the
 *              neck between the blob and the drop stretches, snaps, and the card grows over the place of the button.
 *   - closing: the card melts back into the bud of the button with the same sticky neck.
 */

/** How far (reference px, 1080 wide screen) two pieces of glass reach for each other. Bigger = stickier. */
internal const val STICKY_REACH_REF = 90f

/** Clock value at which the blob that stays at the button (while opening) is fully drained into the card. */
private const val BLOB_GONE_AT = 0.30f

/**
 * The grey of the card (31 on black = the old solid card) as a see-through tint, and its light-theme twin.
 * All the values of this glass (blur, lens, rainbow, tint) are FIXED: the sticky cards do NOT follow GlassTheme.
 */
private val GlassCardDark = Color(0xFF262626).copy(alpha = 0.82f)
private val GlassCardLight = Color(0xFFF2F2F7).copy(alpha = 0.82f)

/** One piece of the sticky glass: a rounded rect in pixels of the layer. */
internal class GlassBlob(val rect: Rect, val radius: Float)

/** The pieces of glass of this moment of the morph, in the frame the geometry of the animation is laid out in. */
internal fun Density.morphGlassShapes(
    morph: SearchMorph,
    rest: Rect,
    u: Float,
    inPlace: Boolean,
    phase: Phase,
    t: Float
): List<GlassBlob> {
    if (phase == Phase.Closed) return emptyList()
    val f = frameFor(phase, t)
    val b = f.body ?: return emptyList()

    // ---- the card / drop: the same placement as SearchMorphLayer (ONE geometry for the glass and for the content)
    val inset = CARD_INSET.toPx()
    val gap = CARD_GAP.toPx()
    val faithful = morph.faithful
    val gBody = when {
        !faithful -> 1f
        phase == Phase.Opening -> 1f - smooth01((t - 0.084f) / (0.118f - 0.084f))
        else -> 0f
    }
    val (bsx, bsy) = shapeScale(rest.width, rest.height, gBody)
    val ax0 = rest.center.x - 366.5f * bsx
    val ay0 = rest.center.y - REF_PILL_CY * bsy
    val axF = if (inPlace) rest.right - FINAL_BODY[2] * u else rest.right + inset - FINAL_BODY[2] * u
    val ayF = if (inPlace) rest.top - FINAL_BODY[1] * u else rest.bottom + gap - FINAL_BODY[1] * u
    val k = ((b[3] - 288f) / (1449f - 288f)).coerceIn(0f, 1f)
    val ax = ax0 + (axF - ax0) * k
    val ay = ay0 + (ayF - ay0) * k
    val sx = bsx + (u - bsx) * k
    val sy = bsy + (u - bsy) * k
    val body = Rect(ax + b[0] * sx, ay + b[1] * sy, ax + b[2] * sx, ay + b[3] * sy)
    val bodyR = min(f.bodyR * u, min(body.width, body.height) / 2f)

    val out = ArrayList<GlassBlob>(2)
    out.add(GlassBlob(body, bodyR))

    if (phase == Phase.Opening && t >= 0.084f) {
        // The pressed button (where the drop is born) stays behind as a blob and drains into the card:
        // this is what the drop pulls the sticky neck from.
        val s = 1f - smooth01((t - 0.084f) / (BLOB_GONE_AT - 0.084f))
        if (s > 0.02f) {
            val (px, py) = shapeScale(rest.width, rest.height, 1f)
            val cx = rest.center.x + ((232f + 498f) / 2f - 366.5f) * px
            val cy = rest.center.y + ((163f + 292f) / 2f - REF_PILL_CY) * py
            val hw = (498f - 232f) / 2f * px * s
            val hh = (292f - 163f) / 2f * py * s
            out.add(GlassBlob(Rect(cx - hw, cy - hh, cx + hw, cy + hh), min(hw, hh)))
        }
    }
    if (phase == Phase.Closing && f.pillAlpha > 0.01f) {
        // the bud of the button the card melts back into
        f.pill?.let { p ->
            val r = if (faithful) mapPillFaithful(p, rest) else mapPill(p, rest)
            out.add(GlassBlob(r, min(r.width, r.height) / 2f))
        }
    }
    return out
}

/**
 * The glass layer. Put it UNDER the (mirrored) layer of [SearchMorphLayer] and over the page; it fills the whole layer
 * (the shapes are inside it) and is transparent where there is no glass. [rest] is the rest rect of the button in the
 * frame of the geometry; [mirrored] = that frame is the mirror image of the screen (the button is on the left).
 */
@Composable
internal fun StickyMorphGlass(
    morph: SearchMorph,
    backdrop: Backdrop,
    rest: Rect,
    layerW: Float,
    u: Float,
    dark: Boolean,
    mirrored: Boolean,
    inPlace: Boolean
) {
    val density = LocalDensity.current

    // Settled card: the glass only needs the area of the card (a full-screen blur on every frame would be wasted work).
    val settled = morph.phase == Phase.Open
    val tight: Rect? = if (settled) {
        with(density) {
            val body = morphGlassShapes(morph, rest, u, inPlace, Phase.Open, OPEN_T).firstOrNull()?.rect
            body?.let {
                val m = 24.dp.toPx()
                val r = if (mirrored) Rect(layerW - it.right, it.top, layerW - it.left, it.bottom) else it
                Rect(r.left - m, r.top - m, r.right + m, r.bottom + m)
            }
        }
    } else null
    val ox = if (tight != null) tight.left.roundToInt().toFloat() else 0f
    val oy = if (tight != null) tight.top.roundToInt().toFloat() else 0f

    val place = if (tight != null) {
        Modifier
            .absoluteOffset { IntOffset(ox.roundToInt(), oy.roundToInt()) }
            .requiredSize(with(density) { tight.width.toDp() }, with(density) { tight.height.toDp() })
    } else Modifier.fillMaxSize()

    Box(
        place.drawBackdrop(
            backdrop = backdrop,
            shape = { RectangleShape },
            highlight = null,
            shadow = null,
            effects = {
                // read inside the effect: the glass follows the clock of the morph every frame without recomposing
                val blobs = morphGlassShapes(morph, rest, u, inPlace, morph.phase, morph.clock.value)
                val shapes = blobs.map {
                    val r = if (mirrored) Rect(layerW - it.rect.right, it.rect.top, layerW - it.rect.left, it.rect.bottom) else it.rect
                    val hw = r.width / 2f
                    val hh = r.height / 2f
                    StickyRoundRect(r.center.x - ox, r.center.y - oy, hw, hh, min(it.radius, min(hw, hh)))
                }
                vibrancy()
                blur(12f.dp.toPx())
                stickyLens(
                    shapes = shapes,
                    smoothness = STICKY_REACH_REF * u,
                    refractionHeight = 14f.dp.toPx(),
                    refractionAmount = 28f.dp.toPx(),
                    chromaticAberration = 0.15f,
                    tint = if (dark) GlassCardDark else GlassCardLight,
                    rimWidth = 1.5f.dp.toPx(),
                    rimAlpha = if (dark) 0.45f else 0.30f,
                    lightAngle = 45f
                )
            }
        )
    ) {}
}
