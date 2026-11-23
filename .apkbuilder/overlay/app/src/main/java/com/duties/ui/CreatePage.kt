package com.duties.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duties.R
import kotlin.math.abs
import kotlinx.coroutines.delay
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
 */
@Composable
fun CreatePage(dark: Boolean, cascade: Cascade, modifier: Modifier = Modifier) {
    // all the text here is Arabic, like the other detail screens
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val pagerState = rememberPagerState(pageCount = { STEPS.size })
        val scope = rememberCoroutineScope()
        val current = pagerState.currentPage
        val last = STEPS.lastIndex
        val firstCascadeUsed = remember { BooleanArray(1) }

        Column(modifier.fillMaxWidth()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth()
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

            // ---- dots + buttons
            Dots(current = current, count = STEPS.size, dark = dark)
            Spacer(Modifier.height(14.dp))
            val backFrac by animateFloatAsState(
                targetValue = if (current > 0) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
                label = "backPill"
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 108.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .width(112.dp * backFrac)
                        .height(54.dp)
                        .clipToBounds()
                        .graphicsLayer { alpha = backFrac }
                ) {
                    if (backFrac > 0.01f) {
                        GhostPill(
                            text = stringResource(R.string.create_back),
                            dark = dark,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (current > 0) scope.launch {
                                pagerState.animateScrollToPage(
                                    current - 1,
                                    animationSpec = spring(dampingRatio = 0.85f, stiffness = 220f)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(10.dp * backFrac))
                OrangePill(
                    text = stringResource(if (current == last) R.string.done else R.string.create_next),
                    modifier = Modifier.weight(1f)
                ) {
                    scope.launch {
                        pagerState.animateScrollToPage(
                            if (current == last) 0 else current + 1,
                            animationSpec = spring(dampingRatio = 0.85f, stiffness = 220f)
                        )
                    }
                }
            }
        }
    }
}

// =====================================================================
// One page
// =====================================================================
@Composable
private fun StepPage(index: Int, step: StepInfo, dark: Boolean, cascade: Cascade) {
    val textColor = if (dark) Color.White else Color(0xFF101114)
    val subColor = if (dark) Color(0xFFB0B3B8) else Color(0xFF55586A)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 6.dp, bottom = 8.dp)
    ) {
        // badge (Required / Optional / Guide) + step counter
        Reveal(delayMs = remember { cascade.take() }, modifier = Modifier.fillMaxWidth(), dy = 12.dp) {
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
        Reveal(delayMs = remember { cascade.take() }, modifier = Modifier.fillMaxWidth()) {
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

        Reveal(delayMs = remember { cascade.take() }, modifier = Modifier.fillMaxWidth()) {
            BasicText(
                stringResource(step.title),
                style = TextStyle(
                    color = textColor, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Start
                )
            )
        }
        Spacer(Modifier.height(8.dp))
        Reveal(delayMs = remember { cascade.take() }, modifier = Modifier.fillMaxWidth()) {
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
            Reveal(delayMs = remember { cascade.take() }, modifier = Modifier.fillMaxWidth(), dy = 12.dp) {
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
            Reveal(delayMs = remember { cascade.take() }, modifier = Modifier.fillMaxWidth(), dy = 12.dp) {
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
                Reveal(delayMs = remember { cascade.take() }, modifier = Modifier.fillMaxWidth(), dy = 11.dp) {
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

@Composable
private fun GhostPill(text: String, dark: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .bounceClick(onClick = onClick)
            .clip(CircleShape)
            .background(if (dark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.07f))
            .border(
                1.dp,
                if (dark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.10f),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text,
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
