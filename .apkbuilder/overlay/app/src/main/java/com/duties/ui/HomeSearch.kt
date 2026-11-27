package com.duties.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duties.AnimationSpeed
import com.duties.R
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.liquid.morph.CARD_H
import com.liquid.morph.CARD_W
import com.liquid.morph.CLOSE_T
import com.liquid.morph.FINAL_BODY
import com.liquid.morph.OPEN_T
import com.liquid.morph.Phase
import com.liquid.morph.frameFor
import com.liquid.morph.gooEffect
import com.liquid.morph.smooth01
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * HomeSearch.kt — search for channels from the Home tab.
 *
 *   SearchMorph        the clock of the liquid morph animation (LiquidMenuMorph timeline, see LiquidMorphEngine.kt)
 *   HomeSearchButton   the round liquid glass button at the right of the "Home" title (a X while the search is open).
 *                      It is the "pill" of the animation: it is pressed (squash), and on close it overshoots and settles.
 *   HomeSearchPanel    a full-screen layer that plays the ORIGINAL LiquidMenuMorph animation, drawn exactly like the
 *                      original screen does (same frames: press, droplet, card with the same corner radius / overshoot,
 *                      blurred content that sharpens, gooey merge back into the button, glass rim). The card of the
 *                      original (750 x 1293 ref px) holds the search field and the channels that were found by name or
 *                      @username. A public channel that I am not in has a "Join" button: it adds me as a member.
 */

/** Shared state of the morph: the animation clock + where the button and the field area are (root coordinates). */
@Stable
internal class SearchMorph {
    var phase by mutableStateOf(Phase.Closed)
    val clock = Animatable(0f)
    var buttonBounds by mutableStateOf(Rect.Zero)
    var listBounds by mutableStateOf(Rect.Zero)

    suspend fun run(open: Boolean) {
        if (open) {
            if (phase == Phase.Open) return
            clock.snapTo(0f)                      // reset first, then switch the phase (no stale frame)
            phase = Phase.Opening
            playClock(OPEN_T)
            phase = Phase.Open
        } else {
            if (phase == Phase.Closed) return
            clock.snapTo(0f)
            phase = Phase.Closing
            playClock(CLOSE_T)
            phase = Phase.Closed
        }
    }

    private suspend fun playClock(total: Float) {
        val go = suspend { clock.animateTo(total, tween((total * 1000).toInt(), easing = LinearEasing)) }
        // AnimationSpeed (the app speeds every animation up 2x) must not touch this clock: the measured timeline
        // of the original plays in its real time, slowed down by MORPH_SLOWDOWN. (phone animations off = 0 = skip)
        if (AnimationSpeed.scaleFactor == 0f) go() else withContext(MorphTime) { go() }
    }
}

/** How much slower than the original video the morph plays (1 = exactly like the video). */
private const val MORPH_SLOWDOWN = 1.2f

private object MorphTime : MotionDurationScale {
    override val scaleFactor: Float = MORPH_SLOWDOWN
}

// clock values at which the real button is invisible: opening: faded out at 0.135, back (as the X) from 0.27;
// closing: faded out at 0.04, re-born as the bud at 0.11
private const val BTN_HIDDEN_FROM = 0.09f
private const val BTN_GONE_AT = 0.05f

// reference size of the pill of the animation (1080 px wide screen)
private const val REF_PILL_W = 301f
private const val REF_PILL_H = 132f
private const val REF_PILL_CY = 222f

/**
 * What the animation does to the button (the "pill" of the original): [scaleX, scaleY, translationX, translationY, alpha].
 * Opening: the pill is pressed (squash), then IT BECOMES the drop (the glass takes over at its place) and the button
 * comes back (as the X) when the drop has left. Closing: the drop melts back; the button is born again as a small bud
 * (same bud rect / alpha as the original), grows into the pill, overshoots and settles.
 */
private fun buttonMorph(
    morph: SearchMorph, btnWPx: Float, btnHPx: Float, dropPx: Float, mirror: Float = 1f, inPlace: Boolean = false
): FloatArray {
    val ph = morph.phase
    // inPlace: the button IS the menu (like the original): it turns into the drop and stays gone while the card is open
    // (the card covers its place); it is born again as the bud when the card melts back
    // open: the X sits inside the card, which hangs below the title row, so it is [dropPx] lower than the rest place
    if (ph != Phase.Opening && ph != Phase.Closing) {
        return floatArrayOf(1f, 1f, 0f, if (ph == Phase.Open) dropPx else 0f, if (inPlace && ph == Phase.Open) 0f else 1f)
    }
    val t = morph.clock.value
    val f = frameFor(ph, t)
    val p = f.pill
    val kx = btnWPx / REF_PILL_W
    val ky = btnHPx / REF_PILL_H
    if (ph == Phase.Opening) {
        if (f.pillShape && p != null) {                       // pressed
            return floatArrayOf(
                (p[2] - p[0]) / REF_PILL_W, (p[3] - p[1]) / REF_PILL_H,
                0f, ((p[1] + p[3]) / 2f - REF_PILL_CY) * ky, 1f
            )
        }
        // from 0.084 the pressed button IS the drop (the layer draws it in the button's colour, no fading, no pop)
        val r = if (inPlace) 0f else smooth01((t - 0.27f) / 0.10f)   // the button comes back (as the X, inside the card)
        val sc = 0.6f + 0.4f * r
        return floatArrayOf(sc, sc, 0f, dropPx, r)
    }
    if (p == null) {                                          // closing, before the bud: gone
        return floatArrayOf(1f, 1f, 0f, dropPx, if (inPlace) 0f else 1f - smooth01(t / 0.04f))
    }
    return floatArrayOf(
        (p[2] - p[0]) / REF_PILL_W, (p[3] - p[1]) / REF_PILL_H,
        ((p[0] + p[2]) / 2f - 366.5f) * kx * mirror, ((p[1] + p[3]) / 2f - REF_PILL_CY) * ky,
        f.pillAlpha
    )
}

/** Pill rect of the animation (reference px) -> rect on this screen, relative to the button. */
private fun mapPill(p: FloatArray, rest: Rect): Rect = Rect(
    rest.left + (p[0] - 216f) / REF_PILL_W * rest.width,
    rest.top + (p[1] - 156f) / REF_PILL_H * rest.height,
    rest.left + (p[2] - 216f) / REF_PILL_W * rest.width,
    rest.top + (p[3] - 156f) / REF_PILL_H * rest.height
)

/**
 * Round liquid glass button (same glass as the other buttons of the app).
 * It pops in with a spring while it turns a quarter; when the search opens the magnifier turns and shrinks away while
 * a X turns in from the other side (and back when it closes). On top of that it plays the pill part of the morph
 * animation (press, becomes the drop, comes back), see [buttonMorph].
 */
@Composable
internal fun HomeSearchButton(
    open: Boolean,
    dark: Boolean,
    backdrop: Backdrop,
    morph: SearchMorph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    delayMs: Int = 0,
    /** The icon of the closed button (the magnifier for the search, the bell for the notifications...). */
    icon: Int = R.drawable.ic_search,
    /** The button is on the LEFT of the screen: its panel opens to the right (see [MorphPanel]). */
    mirrored: Boolean = false,
    /**
     * true = like the ORIGINAL animation: the button itself turns into the menu (the card grows at its place and covers
     * it, the button does not come back as an X). A tap on its place while the card is open still calls [onClick].
     */
    inPlace: Boolean = false
) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        enter.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 220f))
    }
    // The icon follows the morph, not the tap: it stays a magnifier while the button is pressed / melts into the drop,
    // and turns into the X only while the button is hidden (the X is then born inside the card). Closing: the X is
    // gone at once and the magnifier is back before the button re-forms as the bud.
    val iconTarget by remember {
        derivedStateOf {
            if (inPlace) return@derivedStateOf 0f          // the button never turns into an X: it becomes the card
            val c = morph.clock.value
            when (morph.phase) {
                Phase.Closed -> 0f
                Phase.Opening -> if (c >= BTN_HIDDEN_FROM) 1f else 0f
                Phase.Open -> 1f
                Phase.Closing -> if (c < BTN_GONE_AT) 1f else 0f
            }
        }
    }
    val iconHidden by remember {
        derivedStateOf {
            val c = morph.clock.value
            (morph.phase == Phase.Opening && c >= BTN_HIDDEN_FROM && c < 0.27f) ||
                (morph.phase == Phase.Closing && c >= BTN_GONE_AT && c < 0.11f)
        }
    }
    val t by animateFloatAsState(
        iconTarget,
        if (iconHidden) snap() else spring(dampingRatio = 0.62f, stiffness = 320f),
        label = "searchIconMorph"
    )

    Box(
        modifier
            // measured BEFORE the layers below, so the squash never changes the size the morph starts from
            .onGloballyPositioned { morph.buttonBounds = it.boundsInRoot() }
            .graphicsLayer {
                val e = enter.value
                val sq = buttonMorph(
                    morph, 48.dp.toPx(), 48.dp.toPx(),
                    if (inPlace) 0f else 48.dp.toPx() + (CARD_GAP + CARD_INSET).toPx(),
                    if (mirrored) -1f else 1f,
                    inPlace
                )
                scaleX = e * sq[0]
                scaleY = e * sq[1]
                translationX = sq[2]
                translationY = sq[3]
                alpha = (e * 2.2f).coerceIn(0f, 1f) * sq[4]
                rotationZ = (1f - e) * -70f
            }
    ) {
        MenuGlassButton(
            onClick = onClick,
            backdrop = backdrop,
            dark = dark,
            modifier = Modifier.size(48.dp),
            horizontalPadding = 0.dp
        ) {
            // both icons sit on the same spot
            Box(Modifier.requiredSize(22.dp)) {
                GlyphIcon(
                    icon, menuInk(dark), 22.dp,
                    Modifier.graphicsLayer {
                        val k = t.coerceIn(0f, 1f)
                        rotationZ = t * 90f
                        alpha = 1f - k
                        scaleX = 1f - 0.5f * k
                        scaleY = 1f - 0.5f * k
                    }
                )
                GlyphIcon(
                    R.drawable.ic_close, menuInk(dark), 22.dp,
                    Modifier.graphicsLayer {
                        val k = t.coerceIn(0f, 1f)
                        rotationZ = (t - 1f) * 90f
                        alpha = k
                        scaleX = 0.5f + 0.5f * k
                        scaleY = 0.5f + 0.5f * k
                    }
                )
            }
        }
    }
}


/**
 * The search layer (put it over the whole page, UNDER the search button). Nothing is composed while the morph is
 * closed. [visible] = the search is open (true) or closing (false).
 */
@Composable
internal fun HomeSearchPanel(
    visible: Boolean,
    dark: Boolean,
    backdrop: Backdrop,
    morph: SearchMorph,
    onSearch: suspend (String) -> List<Channel>,
    myChannelIds: Set<String>,
    onJoin: suspend (Channel) -> Boolean,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    LaunchedEffect(visible) { morph.run(visible) }
    if (morph.phase != Phase.Closed) {
        SearchContent(visible, dark, backdrop, morph, onSearch, myChannelIds, onJoin, onDismiss, modifier)
    }
}

/**
 * The same liquid morph layer as the search ([HomeSearchPanel]) for any content: the card grows out of its button
 * ([HomeSearchButton] with the same [morph]) and [content] is drawn inside it (right-to-left, like the search card).
 * Keep the first row of [content] free at the corner where the X of the button sits (56dp wide, see [CARD_INSET]).
 *
 * [mirrored] = the button is on the LEFT of the screen. The geometry of the animation hangs the card to the left of a
 * button that sits on the right, so the whole layer is mirrored (and the content is turned back), the card then
 * opens to the right of the button.
 */
@Composable
internal fun MorphPanel(
    visible: Boolean,
    dark: Boolean,
    morph: SearchMorph,
    icon: Int,
    modifier: Modifier = Modifier,
    mirrored: Boolean = false,
    inPlace: Boolean = false,
    onDismiss: () -> Unit = {},
    content: @Composable () -> Unit
) {
    LaunchedEffect(visible) { morph.run(visible) }
    if (morph.phase == Phase.Closed) return
    var origin by remember { mutableStateOf<Offset?>(null) }
    var layerSize by remember { mutableStateOf(IntSize.Zero) }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    origin = it.positionInRoot()
                    layerSize = it.size
                }
                // covers the page below; a tap outside the card closes it
                .pointerInput(Unit) { detectTapGestures { if (morph.phase == Phase.Open) onDismiss() } }
        ) {
            val o = origin
            val btn = morph.buttonBounds
            if (o != null && layerSize.width > 0 && btn.width > 0f) {
                val w = layerSize.width.toFloat()
                var rest = Rect(btn.left - o.x, btn.top - o.y, btn.right - o.x, btn.bottom - o.y)
                if (mirrored) rest = Rect(w - rest.right, rest.top, w - rest.left, rest.bottom)
                Box(Modifier.fillMaxSize().graphicsLayer { if (mirrored) scaleX = -1f }) {
                    SearchMorphLayer(
                        morph = morph, rest = rest, u = w / REF_W_PX, dark = dark,
                        screenBottom = layerSize.height.toFloat(),
                        icon = icon, mirrored = mirrored, inPlace = inPlace
                    ) {
                        Box(Modifier.fillMaxSize().graphicsLayer { if (mirrored) scaleX = -1f }) { content() }
                    }
                }
            }
        }
    }
}

/** Same as BoxShape of the original: a rounded rect in pixels of the layer. */
private class SearchClip(
    val l: Float, val t: Float, val r: Float, val b: Float, val radius: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(l, t, r, b, CornerRadius(radius)))
}

private val LightCard = Color(0xFFF2F2F7)

/** Distance between the corner of the card and the button (the X) that sits inside it. */
internal val CARD_INSET = 14.dp

/** Blur of the metaball filter while closing (the original uses 9; a bit more = the card sticks to the button earlier). */
private const val CLOSE_GOO_BLUR = 13f

/** Extra (not in the original): a sticky metaball neck while the drop leaves the button. Off = exactly the original. */
private const val OPEN_NECK = true

/** Sticky neck while opening: blur of the metaball filter (bigger = a longer, thicker neck) and when it is gone (clock). */
private const val OPEN_NECK_BLUR = 12f
private const val OPEN_NECK_END = 0.26f

/** The neck snaps when the drop is this far (reference px) from the button; the bigger, the longer it stretches. */
private const val OPEN_NECK_REACH = 520f

/** Gap between the title row (the button's rest place) and the top of the card. */
private val CARD_GAP = 10.dp

@Composable
private fun SearchContent(
    visible: Boolean,
    dark: Boolean,
    backdrop: Backdrop,
    morph: SearchMorph,
    onSearch: suspend (String) -> List<Channel>,
    myChannelIds: Set<String>,
    onJoin: suspend (Channel) -> Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var joining by remember { mutableStateOf<Set<String>>(emptySet()) }      // being joined right now
    var justJoined by remember { mutableStateOf<Set<String>>(emptySet()) }   // joined from this search (count + 1)
    val textColor = if (dark) Color.White else Color(0xFF101114)
    val subColor = if (dark) Color(0xFFB0B3B8) else Color(0xFF55586A)
    val focus = LocalFocusManager.current
    val fr = remember { FocusRequester() }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Channel>?>(null) }   // null = nothing searched yet
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    var origin by remember { mutableStateOf<Offset?>(null) }           // where this layer is (root coordinates)
    var layerSize by remember { mutableStateOf(IntSize.Zero) }
    val imeBottom = WindowInsets.ime.getBottom(density).toFloat()

    // the keyboard comes up when the card has landed and goes away with the close
    LaunchedEffect(visible) {
        if (visible) {
            delay(560)
            runCatching { fr.requestFocus() }
        } else {
            focus.clearFocus()
        }
    }
    // search while typing, a moment after the last letter (a new letter cancels the one that was waiting)
    LaunchedEffect(query) {
        val q = query.trim().removePrefix("@")
        if (q.length < 2) {
            results = null
            loading = false
            failed = false
            return@LaunchedEffect
        }
        loading = true
        delay(400)
        try {
            results = onSearch(q)
            failed = false
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        }
        loading = false
    }

    // The geometry of the animation is in absolute pixels: the whole layer is laid out LTR (the content of the
    // card is switched back to RTL inside).
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    origin = it.positionInRoot()
                    layerSize = it.size
                }
                // covers the page below; a tap outside the card closes it (like the original: tap outside = close)
                .pointerInput(Unit) { detectTapGestures { if (morph.phase == Phase.Open) onDismiss() } }
        ) {
            val o = origin
            val btn = morph.buttonBounds
            if (o != null && layerSize.width > 0 && btn.width > 0f) {
                val rest = Rect(btn.left - o.x, btn.top - o.y, btn.right - o.x, btn.bottom - o.y)
                val u = layerSize.width / REF_W_PX
                SearchMorphLayer(
                    morph = morph, rest = rest, u = u, dark = dark,
                    screenBottom = layerSize.height - imeBottom,
                ) { bottomInset ->
                    val list = results
                    val message: Int? = when {
                        failed -> R.string.search_failed
                        list == null -> if (loading) R.string.search_loading else R.string.search_idle
                        list.isEmpty() -> if (loading) R.string.search_loading else R.string.search_none
                        else -> null
                    }
                    SearchCard(
                        dark = dark, backdrop = backdrop,
                        query = query, onQuery = { query = it },
                        textColor = textColor, subColor = subColor,
                        onSearchKey = { focus.clearFocus() },
                        fr = fr,
                        list = list, message = message,
                        joinedIds = myChannelIds, justJoined = justJoined, joining = joining,
                        bottomInset = with(density) { bottomInset.toDp() },
                        onJoin = { ch ->
                            if (ch.id !in joining) {
                                joining = joining + ch.id
                                scope.launch {
                                    val ok = onJoin(ch)
                                    if (ok) justJoined = justJoined + ch.id
                                    joining = joining - ch.id
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

private const val REF_W_PX = 1080f

/**
 * Draws one frame of the ORIGINAL LiquidMenuMorph (same order of layers as LiquidMenuMorphScreen):
 *  1) pill + card body in ONE offscreen canvas; while closing the goo filter melts the card into the pill (metaball)
 *  2) content of the card (here: the search UI), clipped to the animated card, blurred / faded / scaled like the original
 *  3) glass rim
 * The frames come from frameFor() untouched. They are only placed on this screen:
 *  - the pill of the original = the search button (its rect is the "rest" rect);
 *  - the card grows OUT of the button and falls below the title row; the button comes back (lower, see buttonMorph)
 *    as the X inside the card's top corner.
 * Only this composable reads the animation clock, so only it recomposes every frame.
 */
@Composable
private fun SearchMorphLayer(
    morph: SearchMorph,
    rest: Rect,
    u: Float,
    dark: Boolean,
    screenBottom: Float,
    icon: Int = R.drawable.ic_search,
    mirrored: Boolean = false,
    inPlace: Boolean = false,
    content: @Composable (bottomInsetPx: Float) -> Unit
) {
    val density = LocalDensity.current
    val t = morph.clock.value
    val phase = morph.phase
    val f = frameFor(phase, t)
    val b = f.body ?: return

    // reference px -> screen px.  At the start / end of the morph (k = 0) the reference pill is laid on the button;
    // when the card is fully open (k = 1) it is at 1 : 1 scale, right edge on the button's right edge, under the button.
    val s0x = rest.width / (REF_PILL_W * u)
    val s0y = rest.height / (REF_PILL_H * u)
    val inset = with(density) { CARD_INSET.toPx() }      // the button (now the X) sits this far inside the card's corner
    val gap = with(density) { CARD_GAP.toPx() }
    val ax0 = rest.center.x - 366.5f * u * s0x
    val ay0 = rest.center.y - REF_PILL_CY * u * s0y
    // inPlace (the original): the settled card has its top and its outer edge ON the button (the button turned into the
    // card). Otherwise the card hangs below the button and the button comes back as the X inside it.
    val axF = if (inPlace) rest.right - FINAL_BODY[2] * u else rest.right + inset - FINAL_BODY[2] * u
    val ayF = if (inPlace) rest.top - FINAL_BODY[1] * u else rest.bottom + gap - FINAL_BODY[1] * u
    val k = ((b[3] - 288f) / (1449f - 288f)).coerceIn(0f, 1f)      // how far the card has grown out of the pill
    val ax = ax0 + (axF - ax0) * k
    val ay = ay0 + (ayF - ay0) * k
    val sx = u * (s0x + (1f - s0x) * k)
    val sy = u * (s0y + (1f - s0y) * k)
    val body = Rect(ax + b[0] * sx, ay + b[1] * sy, ax + b[2] * sx, ay + b[3] * sy)
    val bodyR = min(f.bodyR * u, min(body.width, body.height) / 2f)

    val cardBase = if (dark) Color(31 / 255f, 31 / 255f, 31 / 255f) else LightCard
    val buttonBase = if (dark) Color(0xFF2A2A2E) else Color(0xFFEDEDF0)     // the menu-colored glass button (drop colour)
    val bodyColor = when (phase) {
        Phase.Opening -> lerp(buttonBase, cardBase, smooth01((t - 0.084f) / (0.118f - 0.084f)))
        // closing: the card takes the colour of the button while it shrinks, so when it touches the bud they melt into
        // ONE shape (the original pill is dark like the card; this button is light, a dark neck would only stick on it)
        Phase.Closing -> lerp(cardBase, buttonBase, smooth01((t - 0.06f) / 0.16f))
        else -> cardBase
    }

    // 1) pill + card, merged by the gooey filter (metaball): while closing (as in the original) and, new, while the drop
    //    leaves the button: a rest blob of the button stays behind and fades, so a sticky neck stretches and snaps.
    val goo = remember(u) { if (Build.VERSION.SDK_INT >= 31) gooEffect(CLOSE_GOO_BLUR * u) else null }
    val gooOpen = remember(u) { if (Build.VERSION.SDK_INT >= 31) gooEffect(OPEN_NECK_BLUR * u) else null }
    val openNeck = OPEN_NECK && phase == Phase.Opening && t >= 0.084f && t <= OPEN_NECK_END
    val gooNow = if (openNeck) gooOpen else goo
    Canvas(
        Modifier.fillMaxSize().graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
            if (f.goo || openNeck) renderEffect = gooNow
        }
    ) {
        if (openNeck) {
            val a = 1f - smooth01((t - 0.084f) / (OPEN_NECK_END - 0.084f))
            val r = mapPill(floatArrayOf(232f, 163f, 498f, 292f), rest)      // the pressed button = where the drop is born
            drawRoundRect(bodyColor.copy(alpha = a), r.topLeft, r.size, CornerRadius(min(r.width, r.height) / 2f))

            // The sticky neck: a string of circles from the button to the drop, thin in the middle, thicker at both
            // ends. The goo filter melts them into one stretched bridge. It thins out as the drop moves away and
            // snaps at OPEN_NECK_REACH (the original drop leaves the button in ~3 frames, so this is what lets you SEE it).
            val sx0 = r.center.x
            val sy0 = r.center.y
            val ex = body.center.x
            val ey = body.top + body.height * 0.15f
            val dist = ey - sy0
            val reach = OPEN_NECK_REACH * u
            val strength = smooth01(1f - dist / reach) * (1f - smooth01((t - 0.15f) / (OPEN_NECK_END - 0.15f)))
            val r0 = min(r.width, r.height) * 0.36f * strength
            if (r0 > 0.5f && dist > 0f) {
                val n = (dist / (r0 * 0.7f)).toInt().coerceIn(4, 48)
                for (i in 1 until n) {
                    val q = i / n.toFloat()
                    val rad = r0 * (1f - 0.72f * sin(PI.toFloat() * q))
                    drawCircle(bodyColor, rad, Offset(sx0 + (ex - sx0) * q, sy0 + (ey - sy0) * q))
                }
            }
        }
        if (phase == Phase.Closing && f.goo) {
            f.pill?.let { p ->
                val r = mapPill(p, rest)
                drawRoundRect(buttonBase.copy(alpha = f.pillAlpha), r.topLeft, r.size, CornerRadius(min(r.width, r.height) / 2f))
            }
        }
        drawRoundRect(bodyColor, body.topLeft, body.size, CornerRadius(bodyR))
    }

    // the magnifier of the button fades out on the drop (the dots of the original pill: 0.45 -> 0 by t = 0.098)
    if (phase == Phase.Opening && f.pill != null && f.pillContent > 0.01f) {
        Box(
            Modifier
                .absoluteOffset { IntOffset((body.center.x - 11.dp.toPx()).roundToInt(), (body.center.y - 11.dp.toPx()).roundToInt()) }
                .graphicsLayer {
                    alpha = f.pillContent
                    if (mirrored) scaleX = -1f      // the whole layer is mirrored: the icon is turned back
                }
        ) { GlyphIcon(icon, menuInk(dark), 22.dp) }
    }

    // 2) content of the card, clipped to the animated card shape
    val menu = f.menu
    if (menu != null) {
        val cardW = CARD_W * u
        val cardH = CARD_H * u
        // part of the settled card the keyboard would cover: the list gets that much bottom padding
        val finalBottom = ayF + FINAL_BODY[3] * u
        val keyboardInset = (finalBottom - screenBottom).coerceIn(0f, cardH * 0.6f)
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                clip = true
                shape = SearchClip(body.left, body.top, body.right, body.bottom, bodyR)
            }
        ) {
            Box(
                Modifier
                    .absoluteOffset {
                        IntOffset((body.center.x - cardW / 2f).roundToInt(), (body.center.y - cardH / 2f).roundToInt())
                    }
                    .size(with(density) { cardW.toDp() }, with(density) { cardH.toDp() })
                    .graphicsLayer {
                        scaleX = menu[2]
                        scaleY = menu[2]
                        alpha = menu[1]
                        if (menu[0] > 0.3f && Build.VERSION.SDK_INT >= 31) {
                            renderEffect = BlurEffect(menu[0] * u, menu[0] * u, TileMode.Decal)
                        }
                    }
                    .pointerInput(Unit) { detectTapGestures { } }       // taps on the card never close it
            ) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    content(keyboardInset)
                }
            }
        }
    }

    // 3) glass rim (thin light edge) - same gradient as the original
    if (f.rim > 0.01f) {
        val rimColor = if (dark) Color.White else Color.Black
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                Brush.verticalGradient(
                    listOf(rimColor.copy(alpha = 0.20f * f.rim), rimColor.copy(alpha = 0.05f * f.rim)),
                    startY = body.top, endY = body.bottom,
                ),
                body.topLeft, body.size, CornerRadius(bodyR),
                style = Stroke(2.5f * u),
            )
        }
    }
}

/** What is inside the card: the search field on top, under it the answer (a message or the channels found). */
@Composable
private fun SearchCard(
    dark: Boolean,
    backdrop: Backdrop,
    query: String,
    onQuery: (String) -> Unit,
    textColor: Color,
    subColor: Color,
    onSearchKey: () -> Unit,
    fr: FocusRequester,
    list: List<Channel>?,
    message: Int?,
    joinedIds: Set<String>,
    justJoined: Set<String>,
    joining: Set<String>,
    bottomInset: Dp,
    onJoin: (Channel) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(start = CARD_INSET, end = CARD_INSET, top = CARD_INSET)) {
        // top row: [X button (drawn above the card by HomeSearchButton)] [the field]
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(48.dp + 8.dp))
        Row(
            Modifier
                .weight(1f)
                .height(46.dp)
                .clip(RoundedCornerShape(50))
                .background(textColor.copy(alpha = 0.08f))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlyphIcon(R.drawable.ic_search, subColor, 19.dp)
            Spacer(Modifier.width(9.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    BasicText(
                        stringResource(R.string.search_hint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = textColor.copy(alpha = 0.35f), fontSize = 14.sp)
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(color = textColor, fontSize = 16.sp),
                    cursorBrush = SolidColor(ACCENT),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearchKey() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(fr)
                )
            }
        }
        }
        // the answer
        if (message == null && list != null) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(top = 8.dp, bottom = bottomInset + 12.dp)
            ) {
                items(list, key = { "found-" + it.id }) { ch ->
                    SearchResultRow(
                        ch = ch,
                        joined = ch.id in joinedIds || ch.id in justJoined,
                        joining = ch.id in joining,
                        extraMember = ch.id in justJoined,
                        backdrop = backdrop,
                        textColor = textColor,
                        subColor = subColor,
                        onJoin = { onJoin(ch) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        } else {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 36.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                BasicText(
                    stringResource(message ?: R.string.search_idle),
                    style = TextStyle(color = subColor, fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
                )
            }
        }
    }
}

/** A channel that was found (compact: the card of the animation is narrow): photo, name, @username . members, Join. */
@Composable
private fun SearchResultRow(
    ch: Channel,
    joined: Boolean,
    joining: Boolean,
    extraMember: Boolean,
    backdrop: Backdrop,
    textColor: Color,
    subColor: Color,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(ch.photo, ch.name, 42.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                ch.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(2.dp))
            BasicText(
                "@" + ch.username + "  ·  " + (ch.memberCount + if (extraMember) 1 else 0),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = subColor, fontSize = 12.sp)
            )
        }
        // Join button: only for a public channel I am not in (a private one is by invitation only)
        if (joined) {
            Spacer(Modifier.width(8.dp))
            BasicText(
                stringResource(R.string.search_joined),
                maxLines = 1,
                softWrap = false,
                style = TextStyle(color = subColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            )
        } else if (ch.bannedMe) {
            // banned from this channel: no Join button until the ban is lifted
            Spacer(Modifier.width(8.dp))
            BasicText(
                stringResource(R.string.search_banned),
                maxLines = 1,
                softWrap = false,
                style = TextStyle(color = Color(0xFFE5484D), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            )
        } else if (ch.isPublic) {
            Spacer(Modifier.width(8.dp))
            LiquidButton(
                onClick = { if (!joining) onJoin() },
                backdrop = backdrop,
                tint = AppAccent.deep(Color(0xFFDB7608)),
                modifier = Modifier.width(72.dp).height(32.dp)
            ) {
                BasicText(
                    if (joining) "…" else stringResource(R.string.search_join),
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
