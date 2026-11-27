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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duties.R
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.liquid.morph.CLOSE_T
import com.liquid.morph.OPEN_T
import com.liquid.morph.Phase
import com.liquid.morph.frameFor
import com.liquid.morph.gooEffect
import com.liquid.morph.smooth01
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * HomeSearch.kt — search for channels from the Home tab.
 *
 *   SearchMorph        the clock of the liquid morph animation (LiquidMenuMorph timeline, see LiquidMorphEngine.kt)
 *   HomeSearchButton   the round liquid glass button at the right of the "Home" title (a X while the search is open).
 *                      It is the "pill" of the animation: it is pressed (squash), and on close it overshoots and settles.
 *   HomeSearchPanel    a full-screen layer: the search field is born from the button like a drop (the droplet falls and
 *                      grows into the field, the text un-blurs), and on close it shrinks back and melts into the button
 *                      (gooey). Under the field: the channels found by name or @username. A public channel that I am
 *                      not in has a "Join" button: it adds me as a member.
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
            clock.animateTo(OPEN_T, tween((OPEN_T * 1000).toInt(), easing = LinearEasing))
            phase = Phase.Open
        } else {
            if (phase == Phase.Closed) return
            clock.snapTo(0f)
            phase = Phase.Closing
            clock.animateTo(CLOSE_T, tween((CLOSE_T * 1000).toInt(), easing = LinearEasing))
            phase = Phase.Closed
        }
    }
}

// reference size of the pill of the animation (1080 px wide screen)
private const val REF_PILL_W = 301f
private const val REF_PILL_H = 132f
private const val REF_PILL_CY = 222f

/**
 * How the button is squeezed by the animation: [sx, sy, translationY in px].
 * Opening: the press (shrinks, a little lower) then it springs back. Closing: when the drop has merged into it, it
 * overshoots (a bit narrow + tall + higher) and settles. Same numbers as the pill of the original animation.
 */
private fun buttonSquash(morph: SearchMorph, btnHPx: Float): FloatArray {
    val ph = morph.phase
    if (ph != Phase.Opening && ph != Phase.Closing) return floatArrayOf(1f, 1f, 0f)
    val t = morph.clock.value
    val f = frameFor(ph, t)
    val p = f.pill
    var sx = 1f
    var sy = 1f
    var dy = 0f
    if (ph == Phase.Opening) {
        if (f.pillShape && p != null) {
            sx = (p[2] - p[0]) / REF_PILL_W
            sy = (p[3] - p[1]) / REF_PILL_H
            dy = (p[1] + p[3]) / 2f - REF_PILL_CY
        } else {
            val w = 1f - smooth01((t - 0.084f) / 0.09f)     // released: back to normal
            sx = 1f + (0.883f - 1f) * w
            sy = 1f + (0.977f - 1f) * w
            dy = 5.5f * w
        }
    } else if (p != null) {
        val w = smooth01((t - 0.20f) / 0.083f)
        sx = 1f + ((p[2] - p[0]) / REF_PILL_W - 1f) * w
        sy = 1f + ((p[3] - p[1]) / REF_PILL_H - 1f) * w
        dy = ((p[1] + p[3]) / 2f - REF_PILL_CY) * w
    }
    return floatArrayOf(sx, sy, dy * btnHPx / REF_PILL_H)
}

/**
 * Round liquid glass button (same glass as the other buttons of the app).
 * It pops in with a spring while it turns a quarter; when the search opens the magnifier turns and shrinks away while
 * a X turns in from the other side (and back when it closes). On top of that it plays the pill part of the morph
 * animation (press squash / overshoot), see [buttonSquash].
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
                val sq = buttonSquash(morph, 48.dp.toPx())
                scaleX = e * sq[0]
                scaleY = e * sq[1]
                translationY = sq[2]
                alpha = (e * 2.2f).coerceIn(0f, 1f)
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
    modifier: Modifier = Modifier
) {
    LaunchedEffect(visible) { morph.run(visible) }
    if (morph.phase != Phase.Closed) {
        SearchContent(visible, dark, backdrop, morph, onSearch, myChannelIds, onJoin, modifier)
    }
}

/** Alpha of the results under the field: they come in after the drop has landed and go first when it closes. */
private fun resultsAlpha(morph: SearchMorph): Float {
    val t = morph.clock.value
    return when (morph.phase) {
        Phase.Opening -> smooth01((t - 0.20f) / 0.30f)
        Phase.Closing -> 1f - smooth01(t / 0.10f)
        Phase.Open -> 1f
        Phase.Closed -> 0f
    }
}

/**
 * Frame of the animation (reference px of a 1080 px screen) -> rect of the glass on this screen.
 * The reference card grows out of the pill, so every edge is a fraction between the rest rect (the button) and the
 * final rect (the field). While it travels the drop is stretched taller (like the falling droplet of the original).
 */
private fun mapBody(b: FloatArray, rest: Rect, fin: Rect, stretch: Float): Rect {
    val fl = ((b[0] - 216f) / (24f - 216f)).coerceIn(0f, 1.08f)
    val fr = ((b[2] - 517f) / (774f - 517f)).coerceIn(0f, 1.08f)
    val fb = ((b[3] - 288f) / (1449f - 288f)).coerceIn(0f, 1.06f)
    val left = rest.left + fl * (fin.left - rest.left)
    val right = rest.right + fr * (fin.right - rest.right)
    val k = fb.coerceIn(0f, 1f)
    val bump = 4f * k * (1f - k)
    val cy = rest.center.y + fb * (fin.center.y - rest.center.y)
    val h = (rest.height + fb * (fin.height - rest.height) + bump * stretch).coerceAtLeast(2f)
    return Rect(left, cy - h / 2f, right.coerceAtLeast(left + 2f), cy + h / 2f)
}

private class SearchClip(val l: Float, val t: Float, val r: Float, val b: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(l, t, r, b, CornerRadius(min(r - l, b - t) / 2f)))
}

@Composable
private fun SearchContent(
    visible: Boolean,
    dark: Boolean,
    backdrop: Backdrop,
    morph: SearchMorph,
    onSearch: suspend (String) -> List<Channel>,
    myChannelIds: Set<String>,
    onJoin: suspend (Channel) -> Boolean,
    modifier: Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var joining by remember { mutableStateOf<Set<String>>(emptySet()) }      // being joined right now
    var justJoined by remember { mutableStateOf<Set<String>>(emptySet()) }   // joined from this search (count + 1)
    val textColor = if (dark) Color.White else Color(0xFF101114)
    val subColor = if (dark) Color(0xFFB0B3B8) else Color(0xFF55586A)
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val focus = LocalFocusManager.current
    val fr = remember { FocusRequester() }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Channel>?>(null) }   // null = nothing searched yet
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    var origin by remember { mutableStateOf<Offset?>(null) }           // where this layer is (root coordinates)
    var layerSize by remember { mutableStateOf(IntSize.Zero) }

    // the keyboard comes up when the drop has landed and goes away with the close
    LaunchedEffect(visible) {
        if (visible) {
            delay(280)
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

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier
                .fillMaxSize()
                .onGloballyPositioned {
                    origin = it.positionInRoot()
                    layerSize = it.size
                }
                .pointerInput(Unit) { detectTapGestures { } }       // covers the page below
        ) {
            val o = origin
            val btn = morph.buttonBounds
            val area = morph.listBounds
            if (o != null && layerSize.width > 0 && btn.width > 0f && area.width > 0f) {
                val padH = with(density) { 16.dp.toPx() }
                val fieldH = with(density) { 52.dp.toPx() }
                val rest = Rect(btn.left - o.x, btn.top - o.y, btn.right - o.x, btn.bottom - o.y)
                val fTop = area.top - o.y + with(density) { 6.dp.toPx() }
                val fin = Rect(padH, fTop, layerSize.width - padH, fTop + fieldH)

                // ---- the morphing glass: drop -> field (and back), with the text blurred in / out
                SearchMorphLayer(
                    morph = morph, rest = rest, fin = fin, u = layerSize.width / 1080f,
                    dark = dark, backdrop = backdrop,
                    query = query, onQuery = { query = it },
                    textColor = textColor, subColor = subColor,
                    onSearchKey = { focus.clearFocus() },
                    fr = fr
                )

                // ---- the answer: a short message, or the channels that were found
                val topPad = with(density) { (fin.bottom + 2.dp.toPx()).toDp() }
                val list = results
                val message: Int? = when {
                    failed -> R.string.search_failed
                    list == null -> if (loading) R.string.search_loading else R.string.search_idle
                    list.isEmpty() -> if (loading) R.string.search_loading else R.string.search_none
                    else -> null
                }
                if (message == null && list != null) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topPad)
                            .graphicsLayer {
                                val p = resultsAlpha(morph)
                                alpha = p
                                translationY = (1f - p) * 18.dp.toPx()
                            },
                        contentPadding = PaddingValues(top = 8.dp, bottom = 130.dp + navBottom)
                    ) {
                        items(list, key = { "found-" + it.id }) { ch ->
                            SearchResultRow(
                                ch = ch,
                                joined = ch.id in myChannelIds || ch.id in justJoined,
                                joining = ch.id in joining,
                                extraMember = ch.id in justJoined,
                                dark = dark,
                                backdrop = backdrop,
                                textColor = textColor,
                                subColor = subColor,
                                onJoin = {
                                    if (ch.id !in joining) {
                                        joining = joining + ch.id
                                        scope.launch {
                                            val ok = onJoin(ch)
                                            if (ok) justJoined = justJoined + ch.id
                                            joining = joining - ch.id
                                        }
                                    }
                                },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = topPad)
                            .padding(horizontal = 40.dp, vertical = 50.dp)
                            .graphicsLayer {
                                val p = resultsAlpha(morph)
                                alpha = p
                                translationY = (1f - p) * 18.dp.toPx()
                            },
                        contentAlignment = Alignment.TopCenter
                    ) {
                        BasicText(
                            stringResource(message ?: R.string.search_idle),
                            style = TextStyle(color = subColor, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Draws one frame of the morph. Only this composable reads the animation clock, so only it recomposes every frame.
 *  1. (closing) a gooey bridge between the shrinking drop and the button = the metaball merge of the original
 *  2. the glass of the drop / field
 *  3. the text field, clipped to the glass, blurred + scaled like the menu content of the original
 */
@Composable
private fun SearchMorphLayer(
    morph: SearchMorph,
    rest: Rect,
    fin: Rect,
    u: Float,
    dark: Boolean,
    backdrop: Backdrop,
    query: String,
    onQuery: (String) -> Unit,
    textColor: Color,
    subColor: Color,
    onSearchKey: () -> Unit,
    fr: FocusRequester
) {
    val density = LocalDensity.current
    val t = morph.clock.value
    val phase = morph.phase
    val f = frameFor(phase, t)
    val b = f.body ?: return
    val body = mapBody(b, rest, fin, rest.height * 0.85f)

    // 1) gooey bridge (API 31+): blur -> alpha threshold of two white shapes = they melt into each other
    if (f.goo && Build.VERSION.SDK_INT >= 31) {
        val goo = remember(u) { gooEffect(9f * u) }
        val gw = 0.28f * smooth01((t - 0.09f) / 0.03f) * (1f - smooth01((t - 0.27f) / 0.03f))
        Canvas(
            Modifier.fillMaxSize().graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
                renderEffect = goo
                alpha = gw
            }
        ) {
            drawRoundRect(Color.White, rest.topLeft, rest.size, CornerRadius(min(rest.width, rest.height) / 2f))
            drawRoundRect(Color.White, body.topLeft, body.size, CornerRadius(min(body.width, body.height) / 2f))
        }
    }

    // 2) the glass
    val bodyAlpha = when (phase) {
        Phase.Opening -> smooth01((t - 0.084f) / 0.03f)
        Phase.Closing -> 1f - smooth01((t - 0.235f) / 0.048f)
        else -> 1f
    }
    Box(
        Modifier
            .absoluteOffset { IntOffset(body.left.roundToInt(), body.top.roundToInt()) }
            .size(with(density) { body.width.toDp() }, with(density) { body.height.toDp() })
            .graphicsLayer { alpha = bodyAlpha }
            .liquidCard(backdrop, RoundedCornerShape(50), dark)
    )

    // 3) the field content (search icon + text), always at its final size, centred in the glass, clipped to it
    val menu = f.menu
    if (menu != null) {
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                clip = true
                shape = SearchClip(body.left, body.top, body.right, body.bottom)
            }
        ) {
            Box(
                Modifier
                    .absoluteOffset {
                        IntOffset((body.center.x - fin.width / 2f).roundToInt(), (body.center.y - fin.height / 2f).roundToInt())
                    }
                    .size(with(density) { fin.width.toDp() }, with(density) { fin.height.toDp() })
                    .graphicsLayer {
                        scaleX = menu[2]
                        scaleY = menu[2]
                        alpha = menu[1]
                        if (menu[0] > 0.3f && Build.VERSION.SDK_INT >= 31) {
                            renderEffect = BlurEffect(menu[0] * u, menu[0] * u, TileMode.Decal)
                        }
                    }
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GlyphIcon(R.drawable.ic_search, subColor, 20.dp)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            BasicText(
                                stringResource(R.string.search_hint),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(color = textColor.copy(alpha = 0.35f), fontSize = 17.sp)
                            )
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQuery,
                            singleLine = true,
                            textStyle = TextStyle(color = textColor, fontSize = 17.sp),
                            cursorBrush = SolidColor(ACCENT),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearchKey() }),
                            modifier = Modifier.fillMaxWidth().focusRequester(fr)
                        )
                    }
                }
            }
        }
    }
}

/** A channel that was found: photo, name, @username and how many people are inside (same look as a Home row). */
@Composable
private fun SearchResultRow(
    ch: Channel,
    joined: Boolean,
    joining: Boolean,
    extraMember: Boolean,
    dark: Boolean,
    backdrop: Backdrop,
    textColor: Color,
    subColor: Color,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ROW_PAD_H, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(ch.photo, ch.name, ROW_AVATAR)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                ch.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = textColor, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(3.dp))
            BasicText(
                "@" + ch.username,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = subColor, fontSize = 14.sp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(subColor.copy(alpha = 0.14f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlyphIcon(R.drawable.ic_people, subColor, 16.dp)
            Spacer(Modifier.width(5.dp))
            BasicText(
                (ch.memberCount + if (extraMember) 1 else 0).toString(),
                style = TextStyle(color = subColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            )
        }
        // Join button: only for a public channel I am not in (a private one is by invitation only)
        if (joined) {
            Spacer(Modifier.width(10.dp))
            BasicText(
                stringResource(R.string.search_joined),
                maxLines = 1,
                softWrap = false,
                style = TextStyle(color = subColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            )
        } else if (ch.isPublic) {
            Spacer(Modifier.width(10.dp))
            LiquidButton(
                onClick = { if (!joining) onJoin() },
                backdrop = backdrop,
                tint = AppAccent.deep(Color(0xFFDB7608)),
                modifier = Modifier.width(96.dp).height(38.dp)
            ) {
                BasicText(
                    if (joining) "…" else stringResource(R.string.search_join),
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
