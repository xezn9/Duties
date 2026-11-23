package com.duties.ui

/*
 * HyperSpringAnimations.kt — all HyperSpring page animations in one file (Jetpack Compose).
 * Ported from HyperSpring's Java sources, using the original curves, durations and AGSL shaders.
 *
 * What is inside
 *  - Motion helpers : ACCENT, SETTLE / EXPO_OUT / POWER4_OUT / WAVE easings, blurEffect()
 *  - Cascade + Reveal : staggered entrance of page elements (Motion.cascade / Motion.reveal)
 *  - SplitTitle      : big title that enters letter by letter
 *  - TabRevealHost   : tab change with the liquid-glass wave (TabReveal)
 *  - PushStack / PushController : open a sub-page (PushMorph), with Emerge and FadeIn for its elements
 *
 * Usage
 *  1) Tabs:
 *       TabRevealHost(selectedTab = tab, tabCenter = { i -> centerOfTabButton(i) }) { index, revealBase ->
 *           MyPage(index, revealBase)
 *       }
 *     Inside each page: val c = remember { Cascade(revealBase) }
 *       SplitTitle("Title", delayMs = remember { c.take() })
 *       Reveal(remember { c.take() }) { ...element... }
 *       (cards with rows: val d = remember { c.take(rows = n) }; Reveal(d){ rows: Reveal(d + 70 + j * 38, dy = 11.dp){} })
 *  2) Sub-pages:
 *       val push = remember { PushController(rememberCoroutineScope()) }   // create inside a composable
 *       PushStack(push, detail = { title -> MyDetail(title) }) { /* the tabs go here */ }
 *     A row opens it with  LocalPush.current?.open(title, rowBoundsInRoot)  and push.close() goes back.
 *     In the sub-page use Emerge(delayMs){...} for elements and FadeIn(delayMs){...} for rows inside cards.
 *
 * Needs Android 12+ for blur and Android 13+ for the glass shaders (older: circle + ring only).
 * Dependencies: compose ui, foundation, activity-compose (BackHandler).
 */

import android.graphics.RuntimeShader
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlin.math.hypot
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// =====================================================================
// Motion.kt
// =====================================================================
// Constants copied from HyperSpring's Motion.java / PushMorph.java / TabReveal.java

/** Accent color (Motion.ACCENT). */
val ACCENT = Color(0xFFFF5F45)

/** PathInterpolator(0.2, 0, 0, 1): "SETTLE" in PushMorph, "EASE" in Motion. */
val SETTLE = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Motion.EXPO_OUT */
val EXPO_OUT = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Motion.POWER4_OUT */
val POWER4_OUT = CubicBezierEasing(0.165f, 0.84f, 0.44f, 1f)

/** TabReveal.WAVE */
val WAVE = CubicBezierEasing(0.4f, 0.1f, 0.15f, 1f)

fun seg(t: Float, a: Float, b: Float): Float = ((t - a) / (b - a)).coerceIn(0f, 1f)

fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Blur of a layer (Android 12+). Below half a pixel it is removed (same rule as PushMorph.blur). */
fun blurEffect(r: Float, mode: TileMode = TileMode.Decal): RenderEffect? =
    if (Build.VERSION.SDK_INT < 31 || r < 0.5f) null else BlurEffect(r, r, mode)

/**
 * Motion.cascade: each child appears a little after the previous one.
 * Cards with [rows] rows reveal their rows right behind them.
 */
class Cascade(private val base: Int) {
    private var d = base

    /** Delay (ms) at which the next element should start. */
    fun take(rows: Int = 0): Int {
        val mine = d
        d += minOf(rows, 8) * 30
        d += 55
        if (d - base > 700) d = base + 700   // on long pages it does not drag on
        return mine
    }
}

/** Motion.reveal: fade in while rising a little (760 ms, power4.out). */
@Composable
fun Reveal(
    delayMs: Int,
    modifier: Modifier = Modifier,
    dy: Dp = 22.dp,
    content: @Composable () -> Unit
) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        a.animateTo(1f, tween(760, easing = POWER4_OUT))
    }
    Box(
        modifier.graphicsLayer {
            alpha = a.value
            translationY = (1f - a.value) * dy.toPx()
        }
    ) { content() }
}

// =====================================================================
// SplitTitle.kt
// =====================================================================
private const val CHAR_MS = 820
private const val STAGGER_MS = 26

/**
 * Port of SplitTitle.java: a big title that enters letter by letter. Each line is a "window"
 * and the letters rise from under it, rotating a little, one after another.
 */
@Composable
fun SplitTitle(
    text: String,
    delayMs: Int,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 40.sp,
    color: Color = Color.White
) {
    val measurer = rememberTextMeasurer()
    val style = remember(fontSize, color) {
        TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.025f).em
        )
    }
    val total = CHAR_MS + STAGGER_MS * text.length
    val t = remember { Animatable(-1f) }
    LaunchedEffect(text) {
        t.snapTo(-1f)
        delay(delayMs.toLong())
        t.snapTo(0f)
        t.animateTo(total.toFloat(), tween(total, easing = LinearEasing))
    }

    BoxWithConstraints(modifier) {
        val maxW = constraints.maxWidth
        val layout = remember(text, maxW, style) {
            measurer.measure(text, style, constraints = Constraints(maxWidth = maxW))
        }
        val glyphs = remember(text, style) { text.map { measurer.measure(it.toString(), style) } }
        val hDp = with(LocalDensity.current) { layout.size.height.toDp() }

        Canvas(Modifier.fillMaxWidth().height(hDp)) {
            val tt = t.value
            if (tt < 0f) return@Canvas                       // letters not out yet
            if (tt >= total + STAGGER_MS * layout.lineCount) {
                drawText(layout)                              // all arrived: whole title at once
                return@Canvas
            }
            var k = 0                                         // letter number in the whole title
            for (li in 0 until layout.lineCount) {
                val top = layout.getLineTop(li)
                val lineH = layout.getLineBottom(li) - top
                val start = layout.getLineStart(li)
                val end = layout.getLineEnd(li)
                // The line's "window": what is below it is not seen (letters come out of there).
                clipRect(0f, top - lineH * 0.2f, size.width, top + lineH) {
                    for (i in start until end) {
                        val kk = k++
                        val ch = text[i]
                        if (ch == ' ' || ch == '\n') continue
                        val p = ((tt - kk * STAGGER_MS) / CHAR_MS).coerceIn(0f, 1f)
                        if (p <= 0f) continue
                        val e = EXPO_OUT.transform(p)
                        val box = layout.getBoundingBox(i)
                        val dy = (1f - e) * lineH
                        rotate(14f * (1f - e), pivot = Offset(box.center.x, box.bottom + dy)) {
                            drawText(
                                glyphs[i],
                                topLeft = Offset(box.left, box.top + dy),
                                alpha = minOf(1f, p * 2.5f)
                            )
                        }
                    }
                }
                k++                                           // the gap between lines counts too
            }
        }
    }
}

// =====================================================================
// TabReveal.kt
// =====================================================================
/**
 * Port of HyperSpring's TabReveal.java: tab change with a liquid-glass wave.
 *
 * The new page is born as a circle from the tapped tab button and grows; its edge is like the
 * rim of thick glass (it bends the image and shines). Ahead of the wave the old page is pushed
 * outwards, like displaced water, and at the same time it sinks (shrinks, fades, blurs).
 *
 * The shaders below are the original AGSL sources (Android 13+); before that only the circle
 * and the ring of light are drawn.
 */

/** Glass rim of the new page (TabReveal.RIM_SHADER). */
internal const val RIM_SHADER = """
uniform shader content;
uniform float2 center;
uniform float radius;
uniform float width;
uniform float amount;
half4 main(float2 p) {
  float2 d = p - center;
  float dist = length(d);
  float e = radius - dist;
  if (e < 0.0 || e > width) return content.eval(p);
  float2 n = d / max(dist, 1.0);
  float k = 1.0 - e / width;
  half4 c = content.eval(p - n * amount * k * k);
  float shine = 0.22 * k * k * k;
  c.rgb = min(c.rgb + half3(shine) * c.a, c.aaa);
  return c;
}
"""

/** Push of the old page just ahead of the wave (TabReveal.PUSH_SHADER). */
internal const val PUSH_SHADER = """
uniform shader content;
uniform float2 center;
uniform float radius;
uniform float width;
uniform float amount;
half4 main(float2 p) {
  float2 d = p - center;
  float dist = length(d);
  float x = (dist - radius) / width;
  if (x < -1.0 || x > 5.0) return content.eval(p);
  float2 n = d / max(dist, 1.0);
  float push = amount * exp(-0.9 * max(x, 0.0));
  return content.eval(p - n * push);
}
"""

internal class CircleAt(val c: Offset, val r: Float) : Shape {
    override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(Path().apply { addOval(Rect(c, r)) })
}

internal fun shaderOrNull(src: String): RuntimeShader? =
    if (Build.VERSION.SDK_INT >= 33) runCatching { RuntimeShader(src) }.getOrNull() else null

/**
 * Shows page [selectedTab]. When it changes, the wave plays from [tabCenter] (the center of the
 * tapped tab button, in this host's coordinates).
 *
 * @param page content of a tab; [revealBase] is the delay (ms) at which its cascade should start
 */
@Composable
fun TabRevealHost(
    selectedTab: Int,
    tabCenter: (Int) -> Offset?,
    modifier: Modifier = Modifier,
    firstRevealBase: Int = 200,
    page: @Composable (index: Int, revealBase: Int) -> Unit
) {
    var current by remember { mutableIntStateOf(selectedTab) }
    var old by remember { mutableStateOf<Int?>(null) }
    var switched by remember { mutableStateOf(false) }
    var hostSize by remember { mutableStateOf(IntSize.Zero) }
    var center by remember { mutableStateOf(Offset.Zero) }
    val progress = remember { Animatable(0f) }
    val rim = remember { shaderOrNull(RIM_SHADER) }
    val push = remember { shaderOrNull(PUSH_SHADER) }

    LaunchedEffect(selectedTab) {
        if (selectedTab == current) return@LaunchedEffect
        val c = tabCenter(selectedTab)
        progress.snapTo(0f)
        switched = true
        if (c == null || hostSize == IntSize.Zero) {          // no wave: plain switch
            old = null
            current = selectedTab
            return@LaunchedEffect
        }
        center = c
        old = current                                      // an unfinished change ends right here
        current = selectedTab
        progress.animateTo(1f, tween(900, easing = WAVE))  // WAVE, 900 ms
        old = null
    }

    Box(modifier.onSizeChanged { hostSize = it }) {
        val animating = old != null
        // New page last so it is drawn on top; keys keep each page's state when the order changes.
        for (idx in listOfNotNull(old, current)) {
            key(idx) {
                val isNew = idx == current
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (!animating) return@graphicsLayer
                            val t = progress.value
                            val cx = center.x
                            val cy = center.y
                            val w = hostSize.width.toFloat().coerceAtLeast(1f)
                            val h = hostSize.height.toFloat().coerceAtLeast(1f)
                            val maxR = hypot(max(cx, w - cx), max(cy, h - cy)) + 40.dp.toPx()
                            val startR = 26.dp.toPx()
                            val r = startR + (maxR - startR) * t
                            val force = 1f - t * t * t   // full for most of the way, calms at the end
                            transformOrigin = TransformOrigin(cx / w, cy / h)

                            if (isNew) {
                                // The new page only shows inside the circle.
                                val s = 1.04f - 0.04f * t           // it arrives a bit close
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
                                        // Wave radius in the old page's coordinates (which shrinks).
                                        push.setFloatUniform("center", cx, cy)
                                        push.setFloatUniform("width", 40.dp.toPx())
                                        push.setFloatUniform("radius", r / k)
                                        push.setFloatUniform("amount", 48.dp.toPx() * force)
                                        val pe = android.graphics.RenderEffect
                                            .createRuntimeShaderEffect(push, "content")
                                        // push first, then blur
                                        e = if (e == null) pe
                                        else android.graphics.RenderEffect.createChainEffect(e, pe)
                                    }
                                    renderEffect = e?.asComposeRenderEffect()
                                }
                            }
                        }
                ) {
                    page(idx, if (!switched) firstRevealBase else 90)
                }
            }
        }

        // The ring of light on the wave front: a wide soft halo and a thin line on top.
        if (animating) {
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
                        ACCENT.copy(alpha = 55f / 255f * fade), radius = r, center = center,
                        style = Stroke(16.dp.toPx())
                    )
                    drawCircle(
                        ACCENT.copy(alpha = 200f / 255f * fade), radius = r, center = center,
                        style = Stroke(2.dp.toPx())
                    )
                }
            }
        }
    }
}

// =====================================================================
// PushMorph.kt
// =====================================================================
/**
 * Port of HyperSpring's PushMorph.java: opening a sub-page like the HyperOS setup wizard.
 *
 *  1. What was there dissolves in place: it blurs, shrinks a little and fades out.
 *  2. The tapped row grows until it fills the screen, as if it became the new page; if it is
 *     not known what was tapped, a circle of light is born instead.
 *  3. The elements of the new page come out of that point, small and blurry, and slide to their
 *     place while they sharpen, one after another.
 */

/** Card color (the row that grows). */
private val SURFACE = Color(0xFF22222B)

class PushRequest(val title: String, val from: Rect?)

class PushController(private val scope: CoroutineScope) {
    var request by mutableStateOf<PushRequest?>(null)
        private set

    /** 0..1: the page below dissolves (blur 22dp, scale 0.96, alpha 0). 380 ms SETTLE. */
    val under = Animatable(0f)

    /** 0..1: the growing row / circle of light. 640 ms EXPO_OUT (700 ms SETTLE for the circle). */
    val bloom = Animatable(0f)

    /** 0..1: the page slides out to the right when going back (pop). */
    val pop = Animatable(0f)
    private var closing = false

    fun open(title: String, from: Rect?) {
        if (request != null) return
        request = PushRequest(title, from)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            pop.snapTo(0f)
            under.snapTo(0f)
            under.animateTo(1f, tween(380, easing = SETTLE))
        }
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            bloom.snapTo(0f)
            bloom.animateTo(
                1f,
                tween(if (from != null) 640 else 700, easing = if (from != null) EXPO_OUT else SETTLE)
            )
        }
    }

    /** Back: the page slides out (320 ms) while the one below comes back. */
    fun close() {
        if (request == null || closing) return
        closing = true
        scope.launch {
            launch { bloom.snapTo(1f) }                   // an unfinished opening is completed
            coroutineScope {
                launch { under.animateTo(0f, tween(320, easing = SETTLE)) }
                launch { pop.animateTo(1f, tween(320, easing = SETTLE)) }
            }
            request = null
            pop.snapTo(0f)
            closing = false
        }
    }
}

private class GrowRect(val r: Rect, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(r, CornerRadius(radius)))
}

val LocalPush = staticCompositionLocalOf<PushController?> { null }

/** Where everything comes out of (the center of what was tapped), in root coordinates. */
val LocalPushOrigin = compositionLocalOf { Offset.Zero }

/**
 * [growFromSource] = true makes the whole detail page (background and content) grow out of the tapped
 * row / button: it is clipped to a rectangle that goes from the button's bounds to the full screen.
 * [edgeLine] = false removes the thin light line (outline of the growing row / ring of the circle)
 * from the opening, for pages that should not have it. The default keeps the original look.
 */
@Composable
fun PushStack(
    controller: PushController,
    modifier: Modifier = Modifier,
    edgeLine: Boolean = true,
    growFromSource: Boolean = false,
    detail: @Composable (title: String) -> Unit,
    under: @Composable () -> Unit
) {
    BackHandler(enabled = controller.request != null) { controller.close() }
    Box(modifier) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val t = controller.under.value
                    val k = 1f - 0.04f * t
                    scaleX = k
                    scaleY = k
                    alpha = 1f - t
                    renderEffect = blurEffect(22.dp.toPx() * t)
                }
        ) {
            CompositionLocalProvider(LocalPush provides controller) { under() }
        }
        controller.request?.let { r -> DetailLayer(r, controller, edgeLine, growFromSource) { detail(r.title) } }
    }
}

@Composable
private fun DetailLayer(
    r: PushRequest,
    c: PushController,
    edgeLine: Boolean,
    growFromSource: Boolean,
    content: @Composable () -> Unit
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val origin = r.from?.center ?: Offset(size.width / 2f, size.height * 0.45f)
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .graphicsLayer {
                val bt = c.bloom.value
                val src = r.from
                val grow = growFromSource && src != null && c.pop.value == 0f && bt < 1f
                alpha = if (grow || c.pop.value > 0f) 1f else minOf(1f, c.under.value * 1.4f)
                translationX = c.pop.value * size.width
                if (grow && src != null) {
                    // the page is only visible inside a rectangle that grows from the button to the screen
                    val l = lerpF(src.left, 0f, bt)
                    val tp = lerpF(src.top, 0f, bt)
                    val rt = lerpF(src.right, size.width.toFloat(), bt)
                    val bm = lerpF(src.bottom, size.height.toFloat(), bt)
                    val rad = lerpF(minOf(src.width, src.height) / 2f, 0f, bt)
                    clip = true
                    shape = GrowRect(Rect(l, tp, rt, bm), rad)
                }
            }
            .background(Brush.verticalGradient(listOf(Color(0xFF14141B), Color(0xFF0B0B10))))
            .pointerInput(Unit) { detectTapGestures { } }     // covers what is below
    ) {
        CompositionLocalProvider(LocalPushOrigin provides origin) { content() }

        // The row that grows (Grow) or the circle of light (Bloom), over the new page.
        Canvas(Modifier.fillMaxSize()) {
            val t = c.bloom.value
            if (t >= 1f) return@Canvas
            val dp = density
            val from = r.from
            if (from != null) {
                if (growFromSource) return@Canvas              // the page itself is growing
                val a = 1f - seg(t, 0.3f, 0.85f)
                if (a <= 0f) return@Canvas
                val left = lerpF(from.left, 0f, t)
                val top = lerpF(from.top, 0f, t)
                val right = lerpF(from.right, this.size.width, t)
                val bottom = lerpF(from.bottom, this.size.height, t)
                val rad = CornerRadius(lerpF(18f, 34f, t) * dp)   // like the screen corners
                drawRoundRect(
                    SURFACE.copy(alpha = 235f / 255f * a),
                    topLeft = Offset(left, top), size = Size(right - left, bottom - top), cornerRadius = rad
                )
                if (edgeLine) {
                    drawRoundRect(
                        ACCENT.copy(alpha = 140f / 255f * a),
                        topLeft = Offset(left, top), size = Size(right - left, bottom - top), cornerRadius = rad,
                        style = Stroke(maxOf(1f, dp))
                    )
                }
            } else {
                val ox = this.size.width / 2f
                val oy = this.size.height * 0.45f
                val rr = (24f + 190f * t) * dp
                val fade = 1f - t
                val core = ACCENT.copy(alpha = 70f / 255f * fade)
                drawCircle(
                    Brush.radialGradient(
                        0.2f to core, 1f to core.copy(alpha = 0f),
                        center = Offset(ox, oy), radius = rr
                    ),
                    radius = rr, center = Offset(ox, oy)
                )
                if (edgeLine) {
                    drawCircle(
                        Color.White.copy(alpha = 90f / 255f * fade * fade),
                        radius = rr * 0.82f, center = Offset(ox, oy), style = Stroke(1.5f * dp)
                    )
                }
            }
        }
    }
}

/**
 * An element comes out of the circle: from small, blurry and shifted towards it, to sharp in
 * its place (PushMorph.emerge: 680 ms SETTLE, scale 0.84, blur 16dp).
 */
@Composable
fun Emerge(
    delayMs: Int,
    modifier: Modifier = Modifier,
    sharpen: Boolean = true,
    content: @Composable () -> Unit
) {
    val origin = LocalPushOrigin.current
    val a = remember { Animatable(0f) }
    var center by remember { mutableStateOf(Offset.Unspecified) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        a.animateTo(1f, tween(680, easing = SETTLE))
    }
    Box(
        modifier
            .onGloballyPositioned { if (center == Offset.Unspecified) center = it.boundsInRoot().center }
            .graphicsLayer {
                val t = a.value
                val c = if (center == Offset.Unspecified) origin else center
                val maxDy = 180.dp.toPx()
                val dx = (origin.x - c.x) * 0.25f
                val dy = ((origin.y - c.y) * 0.45f).coerceIn(-maxDy, maxDy)
                alpha = minOf(1f, t * 1.8f)
                translationX = dx * (1f - t)
                translationY = dy * (1f - t)
                val s = 0.84f + 0.16f * t
                scaleX = s
                scaleY = s
                renderEffect = if (sharpen) blurEffect(16.dp.toPx() * (1f - t)) else null
            }
    ) { content() }
}

/** A row inside a card: it appears sharpening, without moving (the card already moves). */
@Composable
fun FadeIn(delayMs: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        a.animateTo(1f, tween(520, easing = SETTLE))
    }
    Box(
        modifier.graphicsLayer {
            alpha = a.value
            renderEffect = blurEffect(10.dp.toPx() * (1f - a.value))
        }
    ) { content() }
}
