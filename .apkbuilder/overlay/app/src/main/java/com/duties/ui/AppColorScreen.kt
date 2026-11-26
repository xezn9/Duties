package com.duties.ui

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.duties.R
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/**
 * "App color" page. The 20 colors of [AppAccent] stand on a 3D shelf like VHS books (converted from the
 * HTML shelf): every book is ONE color and shows its name. The book in the middle is opened (its cover
 * faces you). Drag the books, tap a book to bring it to the middle, tap the middle cover (or the round
 * glass button on its corner) to apply the color to the whole app. Only the books react to the finger:
 * touching or dragging anywhere else on the screen does nothing.
 *
 * The back button and the apply button are the liquid glass of the app; the two arrows and the "original
 * color" button are white LiquidButtons. The glass refracts the dotted background AND the books, because
 * the books are drawn inside the same backdrop layer.
 *
 * Opening: every part comes out of the row that was tapped (Emerge). Going back is the opposite: the open
 * book closes, every part melts toward that row (Leave) and the page shrinks back into it.
 */
@Composable
fun AppColorScreen(dark: Boolean, onClose: () -> Unit) {
    // the shelf works with absolute left / right, so this page is laid out left-to-right
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        AppColorShelf(dark, onClose)
    }
}

@Composable
private fun AppColorShelf(dark: Boolean, onClose: () -> Unit) {
    val fg = if (dark) Color.White else Color(0xFF101114)
    val glassContainer = if (dark) Color(0xFF121212).copy(alpha = 0.40f) else Color.White.copy(alpha = 0.50f)
    val swatches = AppAccent.swatches
    val n = swatches.size
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val cutoutTop = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()

    // the white buttons (arrows, original color) need dark ink
    val whiteInk = WhiteButtonInk
    // 0..1 while the page is going back (the shelf closes, everything melts toward the row it came from)
    val leave = LocalPushLeave.current

    val sceneBackdrop = rememberLayerBackdrop()
    val dots = rememberDotsState()
    val origin = LocalPushOrigin.current
    LaunchedEffect(Unit) { dots.playWave(origin) }

    // s = where the shelf is now, t = where it is going (both in "books"); s follows t smoothly
    val startIndex = remember { if (AppAccent.active) AppAccent.index else 0 }
    var s by remember { mutableFloatStateOf(startIndex.toFloat()) }
    var t by remember { mutableFloatStateOf(startIndex.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val active by remember { derivedStateOf { Math.floorMod(s.roundToInt(), n) } }

    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            if (s != t) {
                val k = 1f - exp(-dt * (if (dragging) 18f else 5.2f))
                val next = s + (t - s) * k
                s = if (abs(t - next) < 1e-4f) t else next
            }
        }
    }

    val applyColor = {
        if (AppAccent.index != active) {
            AppAccent.index = active
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize().dotsTouch(dots)) {
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        // same size rule as the HTML on phones: --H = min(60vw, 40svh)
        val bookH = min(wPx * 0.60f, hPx * 0.40f)
        val geom = remember(bookH) { ShelfGeom(bookH) }
        val cy = hPx * 0.5f
        val slot = geom.ws + geom.gap

        // the two faces of every book are painted once (nearest books first), then only projected each frame
        val faces = remember(geom) { arrayOfNulls<BookFaces>(n) }
        var ver by remember(geom) { mutableIntStateOf(0) }
        LaunchedEffect(geom) {
            val order = (0 until n).sortedBy { abs(relOf(it, startIndex.toFloat(), n)) }
            for (i in order) {
                val f = withContext(Dispatchers.Default) {
                    buildFaces(swatches[i], geom.ws.roundToInt(), geom.d.roundToInt(), geom.H.roundToInt(), density.density)
                }
                faces[i] = f
                ver++
            }
        }
        val scratch = remember(n) { Scratch(n) }

        // ---- the scene: dotted background + books. The glass of the controls refracts all of it.
        Box(Modifier.fillMaxSize().layerBackdrop(sceneBackdrop)) {
            DotBackground(dots, dark)
            Emerge(delayMs = 120, modifier = Modifier.fillMaxSize()) {
                Leave(start = 0.2f, modifier = Modifier.fillMaxSize()) {
                    Canvas(Modifier.fillMaxSize()) {
                        if (ver < 0) return@Canvas      // reading it redraws the shelf when a new book is ready
                        // going back: the open book closes first (cover turns back into a spine)
                        val open = 1f - smooth(seg(leave(), 0f, 0.42f))
                        drawShelf(geom, s, n, faces, swatches, dark, cy, scratch, open)
                    }
                }
            }
        }

        // ---- touch: ONLY on the books (a band as tall as the books). Drag them (with a little momentum) or
        // tap one. Touching anywhere else on the screen does not change the color.
        val bandTop = cy - geom.H * 0.6f
        val bandH = geom.H * 1.2f
        Box(
            Modifier
                .offset { IntOffset(0, bandTop.roundToInt()) }
                .fillMaxWidth()
                .height(with(density) { bandH.toDp() })
                .pointerInput(slot) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        val first = awaitHorizontalTouchSlopOrCancellation(down.id) { change, over ->
                            change.consume()
                            t -= over / slot
                        }
                        if (first != null) {
                            dragging = true
                            horizontalDrag(first.id) { change ->
                                tracker.addPosition(change.uptimeMillis, change.position)
                                t -= change.positionChange().x / slot
                                change.consume()
                            }
                            dragging = false
                            val vx = tracker.calculateVelocity().x
                            val extra = (-vx * 0.12f / slot).coerceIn(-4f, 4f)
                            t = (t + extra).roundToInt().toFloat()
                        }
                    }
                }
                .pointerInput(geom, wPx) {
                    detectTapGestures { pos ->
                        val dx = pos.x - wPx / 2f
                        // which book is under the finger? (the open cover is wide, the others are spines)
                        val base = s.roundToInt()
                        var hit = Int.MIN_VALUE
                        var hitDist = Float.MAX_VALUE
                        var near = base
                        var nearDist = Float.MAX_VALUE
                        for (k in -6..6) {
                            val idx = base + k
                            val rel = idx - s
                            val cx = slotX(geom, rel)
                            val o = smooth(max(0f, 1f - abs(rel)))
                            val half = lerpF(geom.ws, geom.d, o) / 2f + geom.gap / 2f
                            val dist = abs(cx - dx)
                            if (dist <= half && dist < hitDist) {
                                hitDist = dist
                                hit = idx
                            }
                            if (dist < nearDist) {
                                nearDist = dist
                                near = idx
                            }
                        }
                        if (hit == Int.MIN_VALUE) hit = near                   // the thin gap between two books
                        if (hit == t.roundToInt() && abs(t - s) < 0.35f) {
                            applyColor()                                         // the open cover
                        } else {
                            t = hit.toFloat()                                    // bring that book to the middle
                        }
                    }
                }
        )

        // ---- back (liquid glass)
        Emerge(
            delayMs = 40,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = cutoutTop + 10.dp, end = 16.dp)
        ) {
            Leave(start = 0.12f) {
                GlassSurface(
                    backdrop = sceneBackdrop,
                    container = glassContainer,
                    modifier = Modifier.size(48.dp),
                    onClick = onClose
                ) { GlyphIcon(R.drawable.ic_back, fg, 22.dp) }
            }
        }

        // ---- title of the page, next to the back button. Its color follows the book that is in the middle.
        val titleAccent by animateColorAsState(
            targetValue = swatches[active].color,
            animationSpec = tween(450),
            label = "titleAccent"
        )
        Emerge(
            delayMs = 90,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = cutoutTop + 10.dp, start = 16.dp, end = 16.dp + 48.dp + 14.dp)
        ) {
            Leave(start = 0.12f) {
                Column(
                    Modifier.height(48.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.End
                ) {
                    BasicText(
                        stringResource(R.string.app_color_title),
                        maxLines = 1,
                        style = TextStyle(
                            brush = Brush.horizontalGradient(listOf(titleAccent, fg)),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = TextAlign.End
                        )
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            stringResource(R.string.app_color_screen_sub),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = fg.copy(alpha = 0.6f), fontSize = 12.sp, textAlign = TextAlign.End)
                        )
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.width(22.dp).height(4.dp).background(titleAccent, CircleShape))
                    }
                }
            }
        }

        // ---- apply button on the lower corner of the open cover (it grows when the book settles)
        val applyShown = AppAccent.index == active
        val applyTint = swatches[active].color
        val applyInk = if (applyTint.luminance() > 0.55f) Color(0xFF15161A) else Color.White
        GlassSurface(
            backdrop = sceneBackdrop,
            container = if (applyShown) applyTint.copy(alpha = 0.78f) else glassContainer,
            modifier = Modifier
                .offset {
                    IntOffset(
                        (wPx / 2f - geom.d / 2f - 6f - 26.dp.toPx()).roundToInt(),
                        (cy + geom.H * 0.36f - 26.dp.toPx()).roundToInt()
                    )
                }
                .graphicsLayer {
                    val k = smooth(max(0f, 1f - abs(t - s) * 3f)) * (1f - seg(leave(), 0f, 0.2f))
                    scaleX = k
                    scaleY = k
                }
                .size(52.dp),
            onClick = applyColor
        ) { GlyphIcon(R.drawable.ic_check, if (applyShown) applyInk else fg, 24.dp) }

        // ---- the name of the color (slides when the book changes)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, (cy + geom.H / 2f + 30.dp.toPx()).roundToInt()) }
                .fillMaxWidth(0.62f)
        ) {
            Emerge(delayMs = 200, modifier = Modifier.fillMaxWidth()) {
              Leave(start = 0.05f, modifier = Modifier.fillMaxWidth()) {
                AnimatedContent(
                    targetState = active,
                    transitionSpec = {
                        val dir = if (t >= s) 1 else -1
                        (slideInVertically(tween(420)) { it * dir / 2 } + fadeIn(tween(240)))
                            .togetherWith(slideOutVertically(tween(300)) { -it * dir / 2 } + fadeOut(tween(160)))
                    },
                    contentAlignment = Alignment.Center,
                    label = "colorName",
                    modifier = Modifier.fillMaxWidth()
                ) { idx ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicText(
                            swatches[idx].name,
                            maxLines = 1,
                            style = TextStyle(
                                color = fg,
                                fontSize = 30.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        )
                        if (AppAccent.index == idx) {
                            Spacer(Modifier.width(10.dp))
                            GlyphIcon(R.drawable.ic_check, swatches[idx].color, 24.dp)
                        }
                    }
                }
              }
            }
        }

        // ---- previous / next (white liquid buttons)
        val arrowY = (cy + geom.H / 2f + with(density) { 40.dp.toPx() }).roundToInt()
        Emerge(
            delayMs = 230,
            modifier = Modifier.align(Alignment.TopStart).offset { IntOffset(20.dp.roundToPx(), arrowY) }
        ) {
            Leave(start = 0f) {
                WhiteLiquidButton(
                    backdrop = sceneBackdrop,
                    modifier = Modifier.size(48.dp),
                    horizontalPadding = 0.dp,
                    onClick = { t = t.roundToInt() - 1f }
                ) { GlyphIcon(R.drawable.ic_chevron_left, whiteInk, 24.dp) }
            }
        }
        Emerge(
            delayMs = 230,
            modifier = Modifier.align(Alignment.TopEnd).offset { IntOffset(-20.dp.roundToPx(), arrowY) }
        ) {
            Leave(start = 0f) {
                WhiteLiquidButton(
                    backdrop = sceneBackdrop,
                    modifier = Modifier.size(48.dp),
                    horizontalPadding = 0.dp,
                    onClick = { t = t.roundToInt() + 1f }
                ) { GlyphIcon(R.drawable.ic_back, whiteInk, 24.dp) }
            }
        }

        // ---- back to the original color of the app (white liquid button)
        Emerge(
            delayMs = 280,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 22.dp)
        ) {
            Leave(start = 0f) {
                WhiteLiquidButton(
                    backdrop = sceneBackdrop,
                    modifier = Modifier.width(230.dp).height(48.dp),
                    onClick = {
                        if (AppAccent.active) {
                            AppAccent.index = -1
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                ) {
                    if (!AppAccent.active) GlyphIcon(R.drawable.ic_check, whiteInk, 18.dp)
                    BasicText(
                        stringResource(R.string.app_color_original),
                        style = TextStyle(color = whiteInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}

// =====================================================================================================
//  Shelf math (same numbers as the HTML: ws = H*142/840, d = H*474/900, gap = H*.03)
// =====================================================================================================

private class ShelfGeom(val H: Float) {
    val ws = H * 142f / 840f          // width of a spine
    val d = H * 474f / 900f           // width of a cover (= depth of the book)
    val gap = H * 0.03f
    val persp = H * 9.5f              // distance of the "camera" (perspective: 2200px for H = 232px in the HTML)
}

private fun smooth(x: Float) = x * x * (3f - 2f * x)

/** The open cover pushes its neighbours aside smoothly while it passes through the middle. */
private fun bend(r: Float): Float {
    val pi = PI.toFloat()
    return if (abs(r) >= 1f) sign(r) * 0.5f else (r + sin(pi * r) / pi) / 2f
}

/** [open] = 1: the open cover pushes its neighbours aside; 0: the shelf is closed (only spines, side by side). */
private fun slotX(g: ShelfGeom, r: Float, open: Float = 1f): Float =
    r * (g.ws + g.gap) + (g.d - g.ws + g.gap * 1.6f) * bend(r) * open

/** Signed distance (in books, wrapping around) from the middle of the shelf to book [i]. */
private fun relOf(i: Int, s: Float, n: Int): Float {
    var r = ((i - s) % n + n) % n
    if (r >= n / 2f) r -= n
    return r
}

private class Scratch(n: Int) {
    val rels = FloatArray(n)
    val order = IntArray(n)
    val src = FloatArray(8)
    val dst = FloatArray(8)
    val matrix = Matrix()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    val path = Path()
    val proj = Proj()
}

/** Turns a point of a book (x, y, z) into a point of the screen: rotateY, then rotateZ, then the perspective. */
private class Proj {
    private var g: ShelfGeom? = null
    private var ox = 0f
    private var oy = 0f
    private var tx = 0f
    private var ty = 0f
    private var tz = 0f
    private var cY = 1f
    private var sY = 0f
    private var cZ = 1f
    private var sZ = 0f

    fun begin(g: ShelfGeom, ox: Float, oy: Float) {
        this.g = g
        this.ox = ox
        this.oy = oy
    }

    fun place(x: Float, y: Float, z: Float, rotYDeg: Float, rotZDeg: Float) {
        tx = x
        ty = y
        tz = z
        val ry = Math.toRadians(rotYDeg.toDouble()).toFloat()
        val rz = Math.toRadians(rotZDeg.toDouble()).toFloat()
        cY = cos(ry)
        sY = sin(ry)
        cZ = cos(rz)
        sZ = sin(rz)
    }

    fun point(lx: Float, ly: Float, lz: Float, out: FloatArray, k: Int) {
        val x1 = lx * cY + lz * sY
        val z1 = -lx * sY + lz * cY
        val x2 = x1 * cZ - ly * sZ
        val y2 = x1 * sZ + ly * cZ
        val wz = z1 + tz
        val p = g!!.persp
        val sc = p / (p - wz)
        out[k * 2] = ox + (x2 + tx) * sc
        out[k * 2 + 1] = oy + (y2 + ty) * sc
    }
}

private fun quadArea(p: FloatArray): Float {
    var a = 0f
    for (k in 0..3) {
        val k2 = (k + 1) % 4
        a += p[k * 2] * p[k2 * 2 + 1] - p[k2 * 2] * p[k * 2 + 1]
    }
    return a / 2f
}

private fun DrawScope.drawShelf(
    g: ShelfGeom,
    s: Float,
    n: Int,
    faces: Array<BookFaces?>,
    swatches: List<AppAccent.Swatch>,
    dark: Boolean,
    cy: Float,
    sc: Scratch,
    open: Float = 1f
) {
    val w = size.width
    val ox = w / 2f

    // the books that are on the screen, far ones first
    var cnt = 0
    for (i in 0 until n) {
        val r = relOf(i, s, n)
        sc.rels[i] = r
        if (abs(slotX(g, r, open)) > w / 2f + g.d * 1.5f) continue
        sc.order[cnt++] = i
    }
    for (a in 1 until cnt) {
        val v = sc.order[a]
        var b = a - 1
        while (b >= 0 && abs(sc.rels[sc.order[b]]) < abs(sc.rels[v])) {
            sc.order[b + 1] = sc.order[b]
            b--
        }
        sc.order[b + 1] = v
    }

    // soft light of the color under the open book (a shadow in the light theme)
    for (k in 0 until cnt) {
        val i = sc.order[k]
        val r = sc.rels[i]
        val o = smooth(max(0f, 1f - abs(r))) * open
        if (o < 0.02f) continue
        val col = if (dark) swatches[i].color.copy(alpha = 0.42f * o) else Color.Black.copy(alpha = 0.22f * o)
        val c = Offset(ox + slotX(g, r, open), cy + g.H * 0.53f)
        val rad = g.d * 0.85f
        withTransform({ scale(1f, 0.2f, c) }) {
            drawCircle(
                brush = Brush.radialGradient(listOf(col, col.copy(alpha = 0f)), center = c, radius = rad),
                radius = rad,
                center = c
            )
        }
    }

    val proj = sc.proj
    proj.begin(g, ox, cy)
    val hw = g.ws / 2f
    val hh = g.H / 2f
    val hd = g.d / 2f
    for (k in 0 until cnt) {
        val i = sc.order[k]
        val f = faces[i] ?: continue
        val r = sc.rels[i]
        val o = smooth(max(0f, 1f - abs(r))) * open     // 0 = spine to the viewer ... 1 = cover to the viewer
        proj.place(slotX(g, r, open), -o * g.H * 0.025f, o * g.H * 0.2f, -90f * o, -4.5f * o)

        // spine
        proj.point(-hw, -hh, hd, sc.dst, 0)
        proj.point(hw, -hh, hd, sc.dst, 1)
        proj.point(hw, hh, hd, sc.dst, 2)
        proj.point(-hw, hh, hd, sc.dst, 3)
        drawFace(f.spine, sc, o * 0.55f)

        // cover (the right side of the box, it turns to face you)
        proj.point(hw, -hh, hd, sc.dst, 0)
        proj.point(hw, -hh, -hd, sc.dst, 1)
        proj.point(hw, hh, -hd, sc.dst, 2)
        proj.point(hw, hh, hd, sc.dst, 3)
        drawFace(f.cover, sc, (1f - o) * 0.5f)
    }
}

/** Draws [bmp] stretched onto the four projected corners in sc.dst (a real perspective), then darkens it. */
private fun DrawScope.drawFace(bmp: Bitmap, sc: Scratch, shade: Float) {
    val d = sc.dst
    if (quadArea(d) < 1f) return            // seen from behind or edge-on
    val w = bmp.width.toFloat()
    val h = bmp.height.toFloat()
    val src = sc.src
    src[0] = 0f; src[1] = 0f
    src[2] = w; src[3] = 0f
    src[4] = w; src[5] = h
    src[6] = 0f; src[7] = h
    sc.matrix.setPolyToPoly(src, 0, d, 0, 4)
    drawIntoNative { it.drawBitmap(bmp, sc.matrix, sc.paint) }
    if (shade > 0.004f) {
        val p = sc.path
        p.reset()
        p.moveTo(d[0], d[1])
        p.lineTo(d[2], d[3])
        p.lineTo(d[4], d[5])
        p.lineTo(d[6], d[7])
        p.close()
        drawPath(p, Color.Black.copy(alpha = shade.coerceAtMost(1f)))
    }
}

private inline fun DrawScope.drawIntoNative(block: (android.graphics.Canvas) -> Unit) {
    drawContext.canvas.nativeCanvas.let(block)
}

// =====================================================================================================
//  The two faces of a book, painted once with the color and its name
// =====================================================================================================

private class BookFaces(val spine: Bitmap, val cover: Bitmap)

private const val WHITE = -1
private const val BLACK = -16777216

private fun mix(a: Int, b: Int, t: Float) = ColorUtils.blendARGB(a, b, t)

private fun buildFaces(sw: AppAccent.Swatch, ws: Int, d: Int, h: Int, density: Float): BookFaces {
    val c = sw.color.toArgb()
    val ink = if (sw.color.luminance() > 0.55f) 0xFF15161A.toInt() else WHITE
    val hf = h.toFloat()

    // ---------------- spine ----------------
    val wf = ws.toFloat()
    val spine = Bitmap.createBitmap(ws, h, Bitmap.Config.ARGB_8888)
    run {
        val cv = android.graphics.Canvas(spine)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val clip = android.graphics.Path().apply {
            addRoundRect(RectF(0f, 0f, wf, hf), 3f * density, 3f * density, android.graphics.Path.Direction.CW)
        }
        cv.clipPath(clip)
        p.shader = LinearGradient(0f, 0f, 0f, hf, mix(c, WHITE, 0.14f), mix(c, BLACK, 0.30f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, wf, hf, p)
        // round sheen: dark edges, a light stripe
        p.shader = LinearGradient(
            0f, 0f, wf, 0f,
            intArrayOf(0x55000000, 0x00000000, 0x26FFFFFF, 0x00000000, 0x66000000),
            floatArrayOf(0f, 0.2f, 0.38f, 0.62f, 1f),
            Shader.TileMode.CLAMP
        )
        cv.drawRect(0f, 0f, wf, hf, p)
        p.shader = null

        // little dot on top and two lines at the bottom (like the label of a tape)
        p.color = ink
        p.alpha = 150
        cv.drawCircle(wf / 2f, wf * 0.32f, wf * 0.07f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.6f * density
        cv.drawLine(wf * 0.2f, hf - wf * 0.62f, wf * 0.8f, hf - wf * 0.62f, p)
        cv.drawLine(wf * 0.2f, hf - wf * 0.46f, wf * 0.8f, hf - wf * 0.46f, p)

        // the name, turned on its side
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = wf * 0.50f
        }
        val maxLen = hf * 0.56f
        val len = tp.measureText(sw.name)
        if (len > maxLen) tp.textSize = tp.textSize * maxLen / len
        cv.save()
        cv.translate(wf / 2f, hf * 0.50f)
        cv.rotate(90f)
        val fm = tp.fontMetrics
        cv.drawText(sw.name, 0f, -(fm.ascent + fm.descent) / 2f, tp)
        cv.restore()
    }

    // ---------------- cover ----------------
    val df = d.toFloat()
    val cover = Bitmap.createBitmap(d, h, Bitmap.Config.ARGB_8888)
    run {
        val cv = android.graphics.Canvas(cover)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val clip = android.graphics.Path().apply {
            addRoundRect(RectF(0f, 0f, df, hf), 3f * density, 3f * density, android.graphics.Path.Direction.CW)
        }
        cv.clipPath(clip)
        p.shader = LinearGradient(0f, 0f, df, hf, mix(c, WHITE, 0.22f), mix(c, BLACK, 0.34f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, df, hf, p)

        // a sun: soft light, a disc and a thin ring
        val sx = df / 2f
        val sy = hf * 0.36f
        p.shader = RadialGradient(
            sx, sy, df * 0.46f,
            intArrayOf(0x55FFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        cv.drawCircle(sx, sy, df * 0.46f, p)
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = mix(c, WHITE, 0.38f)
        p.alpha = 150
        cv.drawCircle(sx, sy, df * 0.20f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.6f * density
        p.color = ink
        p.alpha = 95
        cv.drawCircle(sx, sy, df * 0.29f, p)

        // gloss in the upper left corner
        p.style = Paint.Style.FILL
        p.color = 0x18FFFFFF
        val gloss = android.graphics.Path().apply {
            moveTo(0f, 0f); lineTo(df * 0.85f, 0f); lineTo(0f, hf * 0.5f); close()
        }
        cv.drawPath(gloss, p)

        // the edge next to the spine is a little darker
        p.shader = LinearGradient(
            0f, 0f, df * 0.07f, 0f, 0x40000000, 0x00000000, Shader.TileMode.CLAMP
        )
        cv.drawRect(0f, 0f, df * 0.07f, hf, p)
        p.shader = null

        // paper grain
        val rnd = java.util.Random(sw.name.hashCode().toLong())
        val gs = max(1f, 1.2f * density)
        for (k in 0 until 650) {
            val x = rnd.nextFloat() * df
            val y = rnd.nextFloat() * hf
            p.style = Paint.Style.FILL
            p.color = if (rnd.nextBoolean()) 0x16000000 else 0x16FFFFFF
            cv.drawRect(x, y, x + gs, y + gs, p)
        }

        // thin frame
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.4f * density
        p.color = ink
        p.alpha = 85
        val inset = 12f * density
        cv.drawRoundRect(RectF(inset, inset, df - inset, hf - inset), 2f * density, 2f * density, p)

        // name + a short line above it
        p.strokeWidth = 2f * density
        p.alpha = 150
        cv.drawLine(df * 0.41f, hf * 0.69f, df * 0.59f, hf * 0.69f, p)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            textSize = df * 0.17f
        }
        val maxLen = df * 0.74f
        val len = tp.measureText(sw.name)
        if (len > maxLen) tp.textSize = tp.textSize * maxLen / len
        val fm = tp.fontMetrics
        cv.drawText(sw.name, df / 2f, hf * 0.80f - (fm.ascent + fm.descent) / 2f, tp)
    }

    return BookFaces(spine, cover)
}
