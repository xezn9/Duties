package com.duties.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.coerceAtLeast
import com.duties.R
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/*
 * CreatePage.kt — the Create tab: a few swipeable pages that explain how to create a channel.
 *
 *   0  overview            (what you need)
 *   1  channel photo       (required)
 *   2  channel name        (required)
 *   3  channel username    (required)
 *   4  bio                 (optional)
 *   5  invite members      (optional)
 *
 * HyperSpring is used like on the other tabs:
 *  - the pager lives inside the tab, so the liquid-glass wave of TabRevealHost carries it in;
 *  - the elements of every page come with Reveal / Cascade (page 0 continues the tab's own cascade,
 *    the other pages start a new one each time they slide in);
 *  - the pages sink while you swipe (scale, fade, blur) like the old page in TabReveal;
 *  - Next / Back move with a spring.
 */

private val REQUIRED_COLOR = Color(0xFFFF8A00)
private val OPTIONAL_COLOR = Color(0xFF2BD9A0)
private val GUIDE_COLOR = Color(0xFF3D7BFF)
private val BUTTON_ORANGE = Color(0xFFDB7608)

private val RING = listOf(
    Color(0xFFFF8A00), Color(0xFF2BD9A0), Color(0xFF00C2FF),
    Color(0xFF3D7BFF), Color(0xFF8A5CFF), Color(0xFFFF8A00)
)

private enum class StepKind { Overview, Photo, Name, Username, Bio, Invite }

private class StepInfo(
    val kind: StepKind,
    val badge: Int,
    val color: Color,
    val title: Int,
    val body: Int,
    val tips: List<Int>
)

private val STEPS = listOf(
    StepInfo(
        StepKind.Overview, R.string.create_badge_guide, GUIDE_COLOR,
        R.string.create_s0_title, R.string.create_s0_body, emptyList()
    ),
    StepInfo(
        StepKind.Photo, R.string.create_badge_required, REQUIRED_COLOR,
        R.string.create_s1_title, R.string.create_s1_body,
        listOf(R.string.create_s1_t1, R.string.create_s1_t2, R.string.create_s1_t3)
    ),
    StepInfo(
        StepKind.Name, R.string.create_badge_required, REQUIRED_COLOR,
        R.string.create_s2_title, R.string.create_s2_body,
        listOf(R.string.create_s2_t1, R.string.create_s2_t2, R.string.create_s2_t3)
    ),
    StepInfo(
        StepKind.Username, R.string.create_badge_required, REQUIRED_COLOR,
        R.string.create_s3_title, R.string.create_s3_body,
        listOf(R.string.create_s3_t1, R.string.create_s3_t2, R.string.create_s3_t3)
    ),
    StepInfo(
        StepKind.Bio, R.string.create_badge_optional, OPTIONAL_COLOR,
        R.string.create_s4_title, R.string.create_s4_body,
        listOf(R.string.create_s4_t1, R.string.create_s4_t2)
    ),
    StepInfo(
        StepKind.Invite, R.string.create_badge_optional, OPTIONAL_COLOR,
        R.string.create_s5_title, R.string.create_s5_body,
        listOf(R.string.create_s5_t1, R.string.create_s5_t2)
    )
)

/**
 * Body of the Create tab (under the "Create" title). [cascade] is the tab's cascade: the first page
 * continues it, so its elements follow the title just like on the other tabs.
 *
 * When the last page's "Done" is pressed the whole guide is sucked into that button, which shrinks into a small
 * "read the rules again" button, which then rises like a droplet while "Create channel" drips out under it.
 * Pressing "Create channel" does NOT open a window: the form (photo, fields, cards, submit button) moves into the page
 * itself, element by element, while the two buttons go away (the pill sinks into the tabs, blurring; "Create channel" is
 * replaced by the submit button in its own place). The close button of the form does it backwards: the form leaves and
 * the two buttons come back (the pill like a droplet again).
 * Pressing "read the rules again" brings the guide back out and "Create channel" is pulled back in. [startCollapsed] / [onCollapsedChange]
 * let the caller remember that the guide was already read (so it stays folded when you come back to the tab).
 */
@Composable
fun CreatePage(
    dark: Boolean,
    cascade: Cascade,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    startCollapsed: Boolean = false,
    onCollapsedChange: (Boolean) -> Unit = {},
    onSearchUsers: suspend (String) -> List<FoundUser> = { emptyList() },
    onSubmitChannel: suspend (name: String, username: String, bio: String, photo: Bitmap?, invitees: List<FoundUser>) -> Unit = { _, _, _, _, _ -> }
) {
    // all the text here is Arabic, like the other detail screens
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val initiallyFolded = remember { startCollapsed }
        // every time the guide is opened again it starts from page 0 with a fresh pager (and fresh reveals)
        var round by remember { mutableIntStateOf(0) }
        val pagerState = key(round) { rememberPagerState(pageCount = { STEPS.size }) }
        val scope = rememberCoroutineScope()
        val focus = LocalFocusManager.current
        val current = pagerState.currentPage
        val last = STEPS.lastIndex
        val firstCascadeUsed = remember { BooleanArray(1).also { it[0] = initiallyFolded } }
        // The tab opens with the guide already folded: the two buttons ("read the rules again" and "Create channel")
        // come in with the page animation of the other tabs (Reveal: fade in while rising), one after the other
        // right behind the title. Null = nothing extra (guide open: the buttons are shown as before).
        val entryDelays = remember { if (initiallyFolded) intArrayOf(cascade.take(), cascade.take()) else null }

        // fold: 0 = guide open, 1 = guide sucked into the button
        val fold = remember { Animatable(if (initiallyFolded) 1f else 0f) }
        var collapsed by remember { mutableStateOf(initiallyFolded) }
        val showPages by remember { derivedStateOf { fold.value < 0.999f } }
        val foldProvider = remember { { fold.value } }
        // centre of the button (root coordinates): where the elements are pulled into / come out of
        var suckTarget by remember { mutableStateOf(Offset.Unspecified) }

        // ---- the "new channel" form moves into the page (win: 0 = not there, 1 = fully in; every element has its own moment)
        var windowOpen by remember { mutableStateOf(false) }
        val win = remember { Animatable(0f) }
        val draft = remember { ChannelDraft() }
        // after a channel was created the form is emptied, but only once the window has melted away
        var clearDraft by remember { mutableStateOf(false) }
        LaunchedEffect(windowOpen, clearDraft) {
            if (clearDraft && !windowOpen) {
                snapshotFlow { win.value }.first { it < 0.002f }
                draft.clear()
                clearDraft = false
            }
        }

        fun openWindow() {
            if (windowOpen) return
            windowOpen = true
            scope.launch { win.animateTo(1f, tween(1800, easing = LinearEasing)) }
        }

        fun closeWindow() {
            if (!windowOpen) return
            windowOpen = false
            focus.clearFocus()
            // the same thing backwards: the elements leave in the opposite order, then the buttons come back
            scope.launch { win.animateTo(0f, tween(950, easing = LinearEasing)) }
        }

        BackHandler(enabled = windowOpen) { closeWindow() }

        fun collapse() {
            scope.launch {
                collapsed = true
                onCollapsedChange(true)
                // linear here: every element eases in by itself (see Suck), nearest ones first
                fold.animateTo(1f, tween(820, easing = LinearEasing))
            }
        }

        fun reopen() {
            scope.launch {
                round++
                collapsed = false
                onCollapsedChange(false)
                fold.animateTo(0f, tween(900, easing = LinearEasing))
            }
        }

        Box(modifier.fillMaxWidth()) {
            // the pages go edge to edge (sides and bottom of the screen), nothing clips them early
            if (showPages) {
                key(round) {
                    CompositionLocalProvider(
                        LocalFold provides foldProvider,
                        LocalSuckTarget provides suckTarget
                    ) {
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = !collapsed,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        // page 0 uses the tab's cascade the first time; every later entrance starts a new one
                        val pageCascade = remember {
                            if (page == 0 && !firstCascadeUsed[0]) {
                                firstCascadeUsed[0] = true
                                cascade
                            } else {
                                Cascade(40)
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    // the page sinks while it leaves (same idea as the old page in TabReveal)
                                    val off = abs((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                                        .coerceIn(0f, 1f)
                                    val k = 1f - 0.08f * off
                                    scaleX = k
                                    scaleY = k
                                    alpha = 1f - 0.55f * off
                                    renderEffect = blurEffect(10.dp.toPx() * off)
                                }
                        ) {
                            StepPage(index = page, step = STEPS[page], dark = dark, cascade = pageCascade)
                        }
                    }
                    }
                }
            }

            // ---- dots + buttons: float over the bottom of the pages
            val controlsModifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
            val controls: @Composable (Modifier) -> Unit = { m ->
                Controls(
                    modifier = m,
                    fold = { fold.value },
                    current = current,
                    last = last,
                    collapsed = collapsed,
                    dark = dark,
                    backdrop = backdrop,
                    entryDelays = entryDelays,
                    onTarget = { suckTarget = it },
                    windowP = { win.value },
                    windowOpen = windowOpen,
                    onCreate = { openWindow() },
                    onBack = {
                        if (current > 0 && !collapsed) scope.launch {
                            pagerState.animateScrollToPage(
                                current - 1,
                                animationSpec = spring(dampingRatio = 0.85f, stiffness = 220f)
                            )
                        }
                    },
                    onPrimary = {
                        if (!fold.isRunning) {
                            when {
                                // the form is open: this button is gone (it comes back when the form is closed)
                                windowOpen -> Unit
                                collapsed -> reopen()
                                current == last -> collapse()
                                else -> scope.launch {
                                    pagerState.animateScrollToPage(
                                        current + 1,
                                        animationSpec = spring(dampingRatio = 0.85f, stiffness = 220f)
                                    )
                                }
                            }
                        }
                    }
                )
            }
            // The buttons are composed from the very first frame of the tab (the tab's circular wave reveals them).
            // When the tab opens with the guide folded, the two buttons additionally play Reveal (see entryDelays).
            controls(controlsModifier)

            // ---- the form of the new channel: part of this page, above the (hidden) buttons
            CreateChannelForm(
                p = { win.value },
                dark = dark,
                backdrop = backdrop,
                draft = draft,
                onClose = ::closeWindow,
                onSearch = onSearchUsers,
                onSubmit = { name, username, bio, photo, invitees ->
                    onSubmitChannel(name, username, bio, photo, invitees)
                    clearDraft = true
                }
            )
        }
    }
}

/**
 * Dots + Back + Next/Done, all liquid glass buttons ([LiquidButton] over [backdrop]). [fold] (0..1) morphs the big pill into the small "read the rules again" pill:
 * the dots and Back fade away, the pill shrinks to the size of its new text and gulps once when the guide arrives.
 */
@Composable
private fun Controls(
    modifier: Modifier,
    fold: () -> Float,
    current: Int,
    last: Int,
    collapsed: Boolean,
    dark: Boolean,
    backdrop: Backdrop,
    entryDelays: IntArray?,
    onTarget: (Offset) -> Unit,
    windowP: () -> Float,
    windowOpen: Boolean,
    onCreate: () -> Unit,
    onBack: () -> Unit,
    onPrimary: () -> Unit
) {
    val e = fold()                     // read here, so only this small part recomposes while it animates
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val reopenText = stringResource(R.string.create_reopen)
    val reopenStyle = remember { TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
    // width of the small pill = icon + gap + text + LiquidButton's 16dp side padding (+ a little room)
    val smallW = remember(reopenText) {
        with(density) { measurer.measure(reopenText, reopenStyle).size.width.toDp() } + 20.dp + 8.dp + 36.dp
    }
    val backFrac by animateFloatAsState(
        targetValue = if (current > 0 && !collapsed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "backPill"
    )

    // ---- after the guide was swallowed: the small pill rises like a droplet and "Create channel" drips out under it
    val settled = collapsed && e >= 0.999f
    // the pill stays up; while the form is open it only goes away (pillShow) and comes back when the form is closed
    val rise by animateFloatAsState(
        targetValue = if (settled) 1f else 0f,
        animationSpec = if (settled) spring(dampingRatio = 0.5f, stiffness = 140f)    // bouncy, like a drop
        else tween(420, easing = FastOutSlowInEasing),
        label = "rise"
    )
    // 1 = shown, 0 = gone. Opening the form: the pill sinks into the tabs (blur + shrink + fade) and "Create channel" fades
    // out in its place (the submit button of the form takes that place). Closing: when the form is almost gone the
    // buttons come back with a little spring (the pill pops up like a droplet).
    val pillShow = remember { Animatable(1f) }
    val createShow = remember { Animatable(1f) }
    LaunchedEffect(windowOpen) {
        if (windowOpen) {
            pillShow.animateTo(0f, tween(700, easing = FastOutSlowInEasing))
        } else {
            snapshotFlow { windowP() }.first { it < 0.35f }
            pillShow.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 90f))
        }
    }
    LaunchedEffect(windowOpen) {
        if (windowOpen) {
            createShow.animateTo(0f, tween(480, easing = FastOutSlowInEasing))
        } else {
            snapshotFlow { windowP() }.first { it < 0.25f }
            createShow.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 110f))
        }
    }
    val create = remember { Animatable(if (settled) 1f else 0f) }
    LaunchedEffect(settled) {
        if (settled) {
            delay(260)                                                                     // after the drop started rising
            create.animateTo(1f, spring(dampingRatio = 0.58f, stiffness = 170f))
        } else {
            create.animateTo(0f, tween(340, easing = FastOutSlowInEasing))                 // pulled back into the pill
        }
    }
    val riseDp = 74.dp

    // page animation (Reveal) of the two buttons when the tab is entered with the guide folded
    val pillIn = remember { Animatable(if (entryDelays != null) 0f else 1f) }
    val createIn = remember { Animatable(if (entryDelays != null) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (entryDelays != null) {
            launch {
                delay(entryDelays[0].toLong())
                pillIn.animateTo(1f, tween(760, easing = POWER4_OUT))
            }
            launch {
                delay(entryDelays[1].toLong())
                createIn.animateTo(1f, tween(760, easing = POWER4_OUT))
            }
        }
    }
    val revealDy = 22.dp

    Column(modifier) {
        Box(Modifier.fillMaxWidth().graphicsLayer { alpha = 1f - seg(e, 0f, 0.35f) }) {
            Dots(current = current, count = STEPS.size, dark = dark)
        }
        Spacer(Modifier.height(14.dp))
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 108.dp)
                // centre of the row = where the pill ends up (the guide is pulled into this point)
                .onGloballyPositioned { onTarget(it.boundsInRoot().center) }
        ) {
            val backW = 112.dp * backFrac
            val gap = 10.dp * backFrac
            val m = SETTLE.transform(seg(e, 0.45f, 1f))      // the pill shrinks while the guide arrives in it
            val pillW = lerp(maxWidth - backW - gap, smallW, m)
            val pillH = lerp(54.dp, 46.dp, m)

            // ---- Create channel (appears only while the rules are folded; empty action for now)
            val cf = create.value
            if (cf > 0.01f) {
                val smallBtn = 46.dp
                val bw = lerp(smallBtn, maxWidth, cf.coerceIn(0f, 1f))
                val bh = lerp(smallBtn, 54.dp, cf.coerceIn(0f, 1f))
                Box(Modifier.fillMaxWidth().height(54.dp), contentAlignment = Alignment.Center) {
                    LiquidButton(
                        onClick = { if (create.value > 0.9f && windowP() < 0.002f && !windowOpen) onCreate() },
                        backdrop = backdrop,
                        tint = BUTTON_ORANGE,
                        modifier = Modifier
                            .width(bw.coerceAtLeast(smallBtn))
                            .height(bh)
                            .graphicsLayer {
                                // it falls out of the droplet: starts up at the pill, drops to its place
                                val cs = createShow.value
                                translationY = -riseDp.toPx() * (1f - cf) + (1f - createIn.value) * revealDy.toPx()
                                // while the form is open this button is gone: it shrinks, blurs and fades in its place
                                alpha = seg(cf, 0f, 0.2f) * cs.coerceIn(0f, 1f) * createIn.value
                                val sc = 0.8f + 0.2f * cs
                                scaleX = sc
                                scaleY = sc
                                renderEffect = blurEffect(
                                    10.dp.toPx() * (1f - seg(cf, 0f, 0.7f)) + 12.dp.toPx() * (1f - cs.coerceIn(0f, 1f))
                                )
                            }
                    ) {
                        Box(Modifier.wrapContentWidth(unbounded = true), contentAlignment = Alignment.Center) {
                            Row(
                                Modifier.graphicsLayer { alpha = seg(cf, 0.45f, 0.85f) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlyphIcon(R.drawable.ic_plus, Color.White, 22.dp)
                                Spacer(Modifier.width(8.dp))
                                BasicText(
                                    stringResource(R.string.create_channel),
                                    maxLines = 1,
                                    softWrap = false,
                                    style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().height(54.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ---- Back (clear glass)
                Box(
                    Modifier
                        .width(backW)
                        .height(54.dp)
                        .then(if (backFrac < 0.99f) Modifier.clipToBounds() else Modifier)
                ) {
                    if (backFrac > 0.01f) {
                        LiquidButton(
                            onClick = onBack,
                            backdrop = backdrop,
                            surfaceColor = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.30f),
                            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = backFrac }
                        ) {
                            Box(Modifier.wrapContentWidth(unbounded = true)) {
                                BasicText(
                                    stringResource(R.string.create_back),
                                    maxLines = 1,
                                    softWrap = false,
                                    style = TextStyle(
                                        color = if (dark) Color.White else Color(0xFF101114),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(gap))
                // ---- Next / Done  ->  small "read the rules again" (orange glass)
                LiquidButton(
                    onClick = onPrimary,
                    backdrop = backdrop,
                    tint = BUTTON_ORANGE,
                    modifier = Modifier
                        .width(pillW)
                        .height(pillH)
                        .graphicsLayer {
                            // a small "gulp" when the guide reaches the button
                            val g = 1f + 0.07f * sin(PI.toFloat() * seg(e, 0.75f, 1f))
                            // droplet: it lifts off (spring overshoot makes it wobble), stretched tall while moving
                            val r = rise
                            val travel = sin(PI.toFloat() * r.coerceIn(0f, 1f))
                            // form open: it sinks down, shrinks, blurs and fades; it comes back with a spring (pop)
                            val ps = pillShow.value
                            val gone = 1f - ps.coerceIn(0f, 1f)
                            val hs = 0.4f + 0.6f * ps
                            translationY = -riseDp.toPx() * r + (1f - pillIn.value) * revealDy.toPx() + (1f - ps) * 70.dp.toPx()
                            alpha = pillIn.value * ps.coerceIn(0f, 1f)
                            scaleX = g * (1f - 0.14f * travel) * hs
                            scaleY = g * (1f + 0.22f * travel) * hs
                            renderEffect = blurEffect(20.dp.toPx() * gone)
                        }
                ) {
                    // both labels sit on top of each other and cross-fade; unbounded so they never wrap while it shrinks
                    Box(Modifier.wrapContentWidth(unbounded = true), contentAlignment = Alignment.Center) {
                        // Next / Done fades out while the pill shrinks ...
                        BasicText(
                            stringResource(if (current == last) R.string.done else R.string.create_next),
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.graphicsLayer { alpha = 1f - seg(m, 0f, 0.45f) },
                            style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        )
                        // ... and "read the rules again" fades in
                        Row(
                            Modifier.graphicsLayer { alpha = seg(m, 0.55f, 1f) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            GlyphIcon(R.drawable.ic_refresh, Color.White, 20.dp)
                            Spacer(Modifier.width(8.dp))
                            BasicText(reopenText, maxLines = 1, softWrap = false, style = reopenStyle)
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================
// Suction (HyperSpring Emerge, run backwards)
// =====================================================================
/** 0 = guide open, 1 = guide sucked into the button. */
private val LocalFold = staticCompositionLocalOf<() -> Float> { { 0f } }

/** Centre of the button, in root coordinates (Unspecified until the button was laid out). */
private val LocalSuckTarget = compositionLocalOf { Offset.Unspecified }

/**
 * The look of HyperSpring's Emerge (small, blurry, shifted towards a point  ->  sharp in its place), driven
 * by the fold: while the guide folds, each element is pulled to the centre of the button, shrinking, blurring
 * and fading; when it opens again it comes back out of the same point. The elements nearest to the button
 * go first and the far ones follow, so it feels like being sucked in.
 */
@Composable
private fun Suck(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val fold = LocalFold.current
    val target = LocalSuckTarget.current
    var center by remember { mutableStateOf(Offset.Unspecified) }
    Box(
        modifier
            .onGloballyPositioned { center = it.boundsInRoot().center }
            .graphicsLayer {
                val f = fold()
                if (f <= 0f) return@graphicsLayer
                val c = center
                if (c == Offset.Unspecified || target == Offset.Unspecified) {
                    alpha = 0f                      // not placed yet: do not flash for a frame
                    return@graphicsLayer
                }
                val far = ((c - target).getDistance() / 700.dp.toPx()).coerceIn(0f, 1f)
                val s = ((f - 0.32f * far) / 0.68f).coerceIn(0f, 1f)   // this element's own progress
                val e = s * s                                          // accelerates: pulled in
                translationX = (target.x - c.x) * e
                translationY = (target.y - c.y) * e
                val k = 1f - 0.88f * e
                scaleX = k
                scaleY = k
                alpha = 1f - seg(s, 0.5f, 1f)
                renderEffect = blurEffect(16.dp.toPx() * s)
            }
    ) { content() }
}

/** One element of a page: it can be sucked into the button, and enters with Reveal (the tab's cascade). */
@Composable
private fun Element(
    cascade: Cascade,
    instant: Boolean,
    modifier: Modifier = Modifier,
    dy: Dp = 22.dp,
    content: @Composable () -> Unit
) {
    val delay = remember { cascade.take() }
    Suck(modifier.fillMaxWidth()) {
        if (instant) Box(Modifier.fillMaxWidth()) { content() }
        else Reveal(delayMs = delay, modifier = Modifier.fillMaxWidth(), dy = dy, content = content)
    }
}

// =====================================================================
// One page
// =====================================================================
@Composable
private fun StepPage(index: Int, step: StepInfo, dark: Boolean, cascade: Cascade) {
    val textColor = if (dark) Color.White else Color(0xFF101114)
    val subColor = if (dark) Color(0xFFB0B3B8) else Color(0xFF55586A)
    // a page born while the guide comes out of the button does not fade in by itself: its elements already
    // come out of the button (Suck), so Reveal would hide them a second time
    val foldNow = LocalFold.current
    val instant = remember { foldNow() > 0.001f }

    // room under the content so the last item can scroll above the dots + buttons
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 6.dp, bottom = 200.dp + navBottom)
    ) {
        // badge (Required / Optional / Guide) + step counter
        Element(cascade, instant, dy = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepBadge(stringResource(step.badge), step.color, textColor)
                if (index > 0) {
                    Spacer(Modifier.width(10.dp))
                    BasicText(
                        "$index / ${STEPS.size - 1}",
                        style = TextStyle(color = subColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))

        // illustration
        Element(cascade, instant) {
            HeroCard(dark) {
                when (step.kind) {
                    StepKind.Overview -> OverviewHero()
                    StepKind.Photo -> PhotoHero(dark)
                    StepKind.Name -> NameHero(dark)
                    StepKind.Username -> UsernameHero(dark)
                    StepKind.Bio -> BioHero(dark)
                    StepKind.Invite -> InviteHero(dark)
                }
            }
        }
        Spacer(Modifier.height(18.dp))

        Element(cascade, instant) {
            BasicText(
                stringResource(step.title),
                style = TextStyle(
                    color = textColor, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start
                )
            )
        }
        Spacer(Modifier.height(8.dp))
        Element(cascade, instant) {
            BasicText(
                stringResource(step.body),
                style = TextStyle(
                    color = subColor, fontSize = 15.sp, lineHeight = 22.sp,
                    textAlign = TextAlign.Start
                )
            )
        }
        Spacer(Modifier.height(14.dp))

        if (step.kind == StepKind.Overview) {
            Element(cascade, instant, dy = 12.dp) {
                ChipsGroup(
                    label = stringResource(R.string.create_badge_required),
                    color = REQUIRED_COLOR,
                    items = listOf(
                        stringResource(R.string.create_chip_photo),
                        stringResource(R.string.create_chip_name),
                        stringResource(R.string.create_chip_username)
                    ),
                    textColor = textColor,
                    subColor = subColor
                )
            }
            Spacer(Modifier.height(12.dp))
            Element(cascade, instant, dy = 12.dp) {
                ChipsGroup(
                    label = stringResource(R.string.create_badge_optional),
                    color = OPTIONAL_COLOR,
                    items = listOf(
                        stringResource(R.string.create_chip_bio),
                        stringResource(R.string.create_chip_invite)
                    ),
                    textColor = textColor,
                    subColor = subColor
                )
            }
        } else {
            step.tips.forEach { tip ->
                Element(cascade, instant, dy = 11.dp) {
                    TipRow(stringResource(tip), step.color, subColor)
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun StepBadge(text: String, color: Color, textColor: Color) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.45f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(7.dp))
        BasicText(text, style = TextStyle(color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun TipRow(text: String, color: Color, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center
        ) { GlyphIcon(R.drawable.ic_check, Color.White, 14.dp) }
        Spacer(Modifier.width(10.dp))
        BasicText(
            text,
            style = TextStyle(color = textColor, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Start)
        )
    }
}

@Composable
private fun ChipsGroup(
    label: String,
    color: Color,
    items: List<String>,
    textColor: Color,
    subColor: Color
) {
    Column(Modifier.fillMaxWidth()) {
        BasicText(
            label,
            style = TextStyle(color = subColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        )
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items.forEach { item ->
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(color.copy(alpha = 0.16f))
                        .border(1.dp, color.copy(alpha = 0.40f), RoundedCornerShape(16.dp))
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        item,
                        maxLines = 1,
                        style = TextStyle(color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

// =====================================================================
// Controls
// =====================================================================
@Composable
private fun Dots(current: Int, count: Int, dark: Boolean) {
    val inactive = if (dark) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.18f)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            val selected = i == current
            val w by animateDpAsState(
                targetValue = if (selected) 26.dp else 8.dp,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 380f),
                label = "dot"
            )
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .height(8.dp)
                    .width(w)
                    .clip(CircleShape)
                    .background(if (selected) BUTTON_ORANGE else inactive)
            )
        }
    }
}

// =====================================================================
// Illustrations
// =====================================================================
@Composable
private fun HeroCard(dark: Boolean, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(32.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    if (dark) listOf(Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.04f))
                    else listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.45f))
                )
            )
            .border(1.dp, if (dark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.9f), shape),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** Overview: waves going out of a channel icon. */
@Composable
private fun OverviewHero() {
    val t = rememberInfiniteTransition(label = "waves")
    val p = t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing)),
        label = "p"
    )
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(170.dp)) {
            val maxR = size.minDimension / 2f
            for (i in 0..2) {
                val ph = (p.value + i / 3f) % 1f
                drawCircle(
                    color = RING[i].copy(alpha = (1f - ph) * 0.6f),
                    radius = maxR * (0.35f + 0.65f * ph),
                    center = center,
                    style = Stroke(3.dp.toPx())
                )
            }
        }
        Box(
            Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFFFF8A00), Color(0xFFFF375F)))),
            contentAlignment = Alignment.Center
        ) { GlyphIcon(R.drawable.ic_chat, Color.White, 38.dp) }
    }
}

/** Photo: the colorful ring turns slowly, the "+" badge breathes. */
@Composable
private fun PhotoHero(dark: Boolean) {
    val t = rememberInfiniteTransition(label = "photo")
    val rot = t.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "rot"
    )
    val pulse = t.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(tween(850, easing = SETTLE), RepeatMode.Reverse),
        label = "pulse"
    )
    Box(Modifier.size(140.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 7.dp.toPx()
            rotate(rot.value, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(RING, center = center),
                    radius = (size.minDimension - stroke) / 2f,
                    style = Stroke(stroke)
                )
            }
        }
        Box(
            Modifier
                .padding(14.dp)
                .fillMaxSize()
                .clip(CircleShape)
                .background(if (dark) Color(0xFF1A1B22) else Color(0xFFDDE0EC)),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(
                R.drawable.ic_image,
                if (dark) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.35f),
                50.dp
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(44.dp)
                .graphicsLayer {
                    scaleX = pulse.value
                    scaleY = pulse.value
                }
                .clip(CircleShape)
                .background(Color(0xFFCC6A00))
                .border(2.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) { GlyphIcon(R.drawable.ic_plus, Color.White, 24.dp) }
    }
}

/** Name: a text field where a sample channel name is typed again and again. */
@Composable
private fun NameHero(dark: Boolean) {
    val full = stringResource(R.string.create_sample_name)
    val n = typedCount(full)
    MockField(label = stringResource(R.string.create_field_name), dark = dark) {
        BasicText(
            full.take(n),
            maxLines = 1,
            style = TextStyle(
                color = if (dark) Color.White else Color(0xFF101114),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
        Caret()
    }
}

/** Username: @ + a sample username being typed, then the "available" chip pops in. */
@Composable
private fun UsernameHero(dark: Boolean) {
    val full = "duties_channel"
    val n = typedCount(full)
    val done = n == full.length
    val chip by animateFloatAsState(
        targetValue = if (done) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 320f),
        label = "ok"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        MockField(label = stringResource(R.string.create_field_username), dark = dark, ltr = true) {
            GlyphIcon(R.drawable.ic_at, REQUIRED_COLOR, 22.dp)
            Spacer(Modifier.width(8.dp))
            BasicText(
                full.take(n),
                maxLines = 1,
                style = TextStyle(
                    color = if (dark) Color.White else Color(0xFF101114),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
            Caret()
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .graphicsLayer {
                    alpha = chip.coerceIn(0f, 1f)
                    scaleX = 0.6f + 0.4f * chip
                    scaleY = 0.6f + 0.4f * chip
                }
                .clip(CircleShape)
                .background(OPTIONAL_COLOR.copy(alpha = 0.18f))
                .border(1.dp, OPTIONAL_COLOR.copy(alpha = 0.5f), CircleShape)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlyphIcon(R.drawable.ic_check, OPTIONAL_COLOR, 16.dp)
            Spacer(Modifier.width(6.dp))
            BasicText(
                stringResource(R.string.create_username_ok),
                style = TextStyle(
                    color = if (dark) Color.White else Color(0xFF101114),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

/** Bio: a mini channel header with the bio line being typed. */
@Composable
private fun BioHero(dark: Boolean) {
    val full = stringResource(R.string.create_sample_bio)
    val n = typedCount(full)
    val skeleton = if (dark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f)
    Column(Modifier.fillMaxWidth(0.86f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFFF8A00), Color(0xFFFF375F))))
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Box(Modifier.width(110.dp).height(10.dp).clip(CircleShape).background(skeleton))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.width(70.dp).height(8.dp).clip(CircleShape).background(skeleton))
            }
        }
        Spacer(Modifier.height(12.dp))
        MockField(
            label = stringResource(R.string.field_bio),
            dark = dark,
            focusColor = OPTIONAL_COLOR,
            fieldHeight = 78.dp,
            fullWidth = true
        ) {
            BasicText(
                full.take(n),
                maxLines = 2,
                style = TextStyle(
                    color = if (dark) Color.White else Color(0xFF101114),
                    fontSize = 16.sp,
                    lineHeight = 22.sp
                )
            )
            Caret(color = OPTIONAL_COLOR)
        }
    }
}

/** Invite: members pop in one after another, then the invite link. */
@Composable
private fun InviteHero(dark: Boolean) {
    val colors = listOf(
        Color(0xFFFF8A00), Color(0xFF2BD9A0), Color(0xFF00C2FF), Color(0xFF8A5CFF), Color(0xFFFF375F)
    )
    val ringColor = if (dark) Color(0xFF1E2029) else Color(0xFFF2F3F8)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
            colors.forEachIndexed { i, c -> PopAvatar(i, c, ringColor, last = i == colors.lastIndex) }
        }
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier
                .clip(CircleShape)
                .background(OPTIONAL_COLOR.copy(alpha = 0.16f))
                .border(1.dp, OPTIONAL_COLOR.copy(alpha = 0.45f), CircleShape)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlyphIcon(R.drawable.ic_link, OPTIONAL_COLOR, 20.dp)
            Spacer(Modifier.width(8.dp))
            BasicText(
                stringResource(R.string.create_invite_link),
                style = TextStyle(
                    color = if (dark) Color.White else Color(0xFF101114),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
private fun PopAvatar(index: Int, color: Color, ring: Color, last: Boolean) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(250L + index * 90L)
        a.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
    }
    Box(
        Modifier
            .graphicsLayer {
                scaleX = a.value
                scaleY = a.value
                alpha = a.value.coerceIn(0f, 1f)
            }
            .size(54.dp)
            .clip(CircleShape)
            .background(ring)
            .padding(3.dp)
            .clip(CircleShape)
            .background(
                if (last) Brush.linearGradient(listOf(Color(0xFF2BD9A0), Color(0xFF00C2FF)))
                else Brush.linearGradient(listOf(color, color.copy(alpha = 0.65f)))
            ),
        contentAlignment = Alignment.Center
    ) {
        if (last) GlyphIcon(R.drawable.ic_plus, Color.White, 26.dp)
        else GlyphIcon(R.drawable.ic_tab_account, Color.White, 26.dp)
    }
}

// =====================================================================
// Small helpers for the illustrations
// =====================================================================

/** A fake text field: small label on top, the box under it. [content] sits inside the box. */
@Composable
private fun MockField(
    label: String,
    dark: Boolean,
    modifier: Modifier = Modifier,
    focusColor: Color = REQUIRED_COLOR,
    fieldHeight: Dp = 58.dp,
    ltr: Boolean = false,
    fullWidth: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.then(if (fullWidth) Modifier.fillMaxWidth() else Modifier.fillMaxWidth(0.86f))
    ) {
        BasicText(
            label,
            style = TextStyle(
                color = if (dark) Color(0xFFB0B3B8) else Color(0xFF55586A),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(Modifier.height(6.dp))
        val direction = if (ltr) LayoutDirection.Ltr else LocalLayoutDirection.current
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(fieldHeight)
                    .clip(shape)
                    .background(if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.85f))
                    .border(1.5.dp, focusColor.copy(alpha = 0.75f), shape)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

/** Blinking text cursor. */
@Composable
private fun Caret(color: Color = REQUIRED_COLOR) {
    val t = rememberInfiniteTransition(label = "caret")
    val a = t.animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(480, easing = LinearEasing), RepeatMode.Reverse),
        label = "a"
    )
    Spacer(Modifier.width(3.dp))
    Box(
        Modifier
            .width(2.dp)
            .height(22.dp)
            .graphicsLayer { alpha = a.value }
            .background(color)
    )
}

/** How many letters of [full] are typed right now (it types, waits, clears and starts again). */
@Composable
private fun typedCount(full: String): Int {
    var n by remember { mutableIntStateOf(0) }
    LaunchedEffect(full) {
        delay(700)
        while (true) {
            for (i in 1..full.length) {
                n = i
                delay(95)
            }
            delay(1900)
            n = 0
            delay(500)
        }
    }
    return n
}
