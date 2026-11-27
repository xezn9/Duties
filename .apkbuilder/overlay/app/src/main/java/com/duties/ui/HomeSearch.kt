package com.duties.ui

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
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
import kotlin.math.min
import kotlin.math.roundToInt

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
private fun buttonMorph(morph: SearchMorph, btnWPx: Float, btnHPx: Float): FloatArray {
    val ph = morph.phase
    if (ph != Phase.Opening && ph != Phase.Closing) return floatArrayOf(1f, 1f, 0f, 0f, 1f)
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
        if (t < 0.16f) {                                      // it turns into the drop
            val a = 1f - smooth01((t - 0.075f) / 0.06f)
            return floatArrayOf(0.883f, 0.977f, 0f, 5.5f * ky, a)
        }
        val r = smooth01((t - 0.27f) / 0.10f)                 // the button comes back (as the X, inside the card)
        val sc = 0.6f + 0.4f * r
        return floatArrayOf(sc, sc, 0f, 0f, r)
    }
    if (p == null) {                                          // closing, before the bud: gone
        return floatArrayOf(1f, 1f, 0f, 0f, 1f - smooth01(t / 0.04f))
    }
    return floatArrayOf(
        (p[2] - p[0]) / REF_PILL_W, (p[3] - p[1]) / REF_PILL_H,
        ((p[0] + p[2]) / 2f - 366.5f) * kx, ((p[1] + p[3]) / 2f - REF_PILL_CY) * ky,
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
    delayMs: Int = 0
) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        enter.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 220f))
    }
    val t by animateFloatAsState(if (open) 1f else 0f, spring(dampingRatio = 0.62f, stiffness = 320f), label = "searchIconMorph")

    Box(
        modifier
            // measured BEFORE the layers below, so the squash never changes the size the morph starts from
            .onGloballyPositioned { morph.buttonBounds = it.boundsInRoot() }
            .graphicsLayer {
                val e = enter.value
                val sq = buttonMorph(morph, 48.dp.toPx(), 48.dp.toPx())
                scaleX = e * sq[0]
                scaleY = e * sq[1]
                translationX = sq[2]
                translationY = sq[3]
                alpha = (e * 2.2f).coerceIn(0f, 1f) * sq[4]
                rotationZ = (1f - e) * -70f
            }
    ) {
        WhiteLiquidButton(
            onClick = onClick,
            backdrop = backdrop,
            modifier = Modifier.size(48.dp),
            horizontalPadding = 0.dp
        ) {
            // both icons sit on the same spot
            Box(Modifier.requiredSize(22.dp)) {
                GlyphIcon(
                    R.drawable.ic_search, WhiteButtonInk, 22.dp,
                    Modifier.graphicsLayer {
                        val k = t.coerceIn(0f, 1f)
                        rotationZ = t * 90f
                        alpha = 1f - k
                        scaleX = 1f - 0.5f * k
                        scaleY = 1f - 0.5f * k
                    }
                )
                GlyphIcon(
                    R.drawable.ic_close, WhiteButtonInk, 22.dp,
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

/** Same as BoxShape of the original: a rounded rect in pixels of the layer. */
private class SearchClip(
    val l: Float, val t: Float, val r: Float, val b: Float, val radius: Float
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(l, t, r, b, CornerRadius(radius)))
}

private val LightCard = Color(0xFFF2F2F7)

/** Distance between the corner of the card and the button (the X) that sits inside it. */
private val CARD_INSET = 14.dp

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
 *  - the card grows OUT of the button: it covers it, and the button comes back as the X inside the card's top corner.
 * Only this composable reads the animation clock, so only it recomposes every frame.
 */
@Composable
private fun SearchMorphLayer(
    morph: SearchMorph,
    rest: Rect,
    u: Float,
    dark: Boolean,
    screenBottom: Float,
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
    val ax0 = rest.center.x - 366.5f * u * s0x
    val ay0 = rest.center.y - REF_PILL_CY * u * s0y
    val axF = rest.right + inset - FINAL_BODY[2] * u
    val ayF = rest.top - inset - FINAL_BODY[1] * u
    val k = ((b[3] - 288f) / (1449f - 288f)).coerceIn(0f, 1f)      // how far the card has grown out of the pill
    val ax = ax0 + (axF - ax0) * k
    val ay = ay0 + (ayF - ay0) * k
    val sx = u * (s0x + (1f - s0x) * k)
    val sy = u * (s0y + (1f - s0y) * k)
    val body = Rect(ax + b[0] * sx, ay + b[1] * sy, ax + b[2] * sx, ay + b[3] * sy)
    val bodyR = min(f.bodyR * u, min(body.width, body.height) / 2f)

    val g = f.bodyGray / 255f
    val bodyColor = if (dark) Color(g, g, g) else LightCard

    // 1) pill + card, merged by the gooey filter while closing
    val goo = remember(u) { if (Build.VERSION.SDK_INT >= 31) gooEffect(9f * u) else null }
    Canvas(
        Modifier.fillMaxSize().graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
            if (f.goo) renderEffect = goo
        }
    ) {
        if (phase == Phase.Closing && f.goo) {
            f.pill?.let { p ->
                val r = mapPill(p, rest)
                val c = if (dark) Color(31 / 255f, 31 / 255f, 31 / 255f, f.pillAlpha) else LightCard.copy(alpha = f.pillAlpha)
                drawRoundRect(c, r.topLeft, r.size, CornerRadius(min(r.width, r.height) / 2f))
            }
        }
        drawRoundRect(bodyColor, body.topLeft, body.size, CornerRadius(bodyR))
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
