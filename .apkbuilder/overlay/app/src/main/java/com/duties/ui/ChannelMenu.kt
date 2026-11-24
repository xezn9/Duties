package com.duties.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duties.R
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlin.math.roundToInt

/*
 * ChannelMenu.kt — what a long press on a channel (Home tab) opens.
 *
 *  - [ChannelActionOverlay]: a small glass menu that grows out of the pressed row (HyperSpring style: it springs
 *    open, sharpens from a blur and its items come one after another). The page behind blurs and dims.
 *      owner  : Archive, Mute, Delete channel, Leave channel
 *      member : Archive, Mute, Leave channel
 *    Archive and Mute are empty for now (they only close the menu).
 *  - [ConfirmOverlay]: "are you sure?" for the two that really change something (delete / leave).
 */

private val DANGER = Color(0xFFFF453A)

/** The channel that was pressed and where its row is on the screen (root coordinates). */
internal class MenuTarget(val channel: Channel, val rowBounds: Rect)

internal enum class ChannelAction { Archive, Mute, Delete, Leave }

/** Waiting for "yes" before [action] is done to [channel]. */
internal class ConfirmRequest(val channel: Channel, val action: ChannelAction)

// =====================================================================
// The menu
// =====================================================================
private val MENU_W = 236.dp
private val ITEM_H = 52.dp
private val MENU_PAD = 8.dp

/**
 * [p] is driven here (0 = closed, 1 = open) and is also read by the page behind, which blurs while it grows.
 * [target] null = closing; the menu stays on screen until its closing animation is over.
 */
@Composable
internal fun ChannelActionOverlay(
    target: MenuTarget?,
    p: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    dark: Boolean,
    backdrop: Backdrop,
    onAction: (Channel, ChannelAction) -> Unit,
    onDismiss: () -> Unit
) {
    // keep showing the last target while the menu folds away
    var shown by remember { mutableStateOf<MenuTarget?>(null) }
    LaunchedEffect(target) {
        if (target != null) {
            shown = target
            p.snapTo(0f)
            p.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 380f))
        } else if (shown != null) {
            p.animateTo(0f, tween(170))
            shown = null
        }
    }
    val t = shown ?: return
    BackHandler(enabled = target != null) { onDismiss() }

    val fg = if (dark) Color.White else Color(0xFF101114)
    val density = LocalDensity.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var origin by remember { mutableStateOf(Offset.Zero) }

    val actions = remember(t.channel.id, t.channel.isOwner) {
        buildList {
            add(ChannelAction.Archive)
            add(ChannelAction.Mute)
            if (t.channel.isOwner) add(ChannelAction.Delete)
            add(ChannelAction.Leave)
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
    ) {
        val w = with(density) { MENU_W.toPx() }
        val h = with(density) { (ITEM_H * actions.size + MENU_PAD * 2).toPx() }
        val margin = with(density) { 12.dp.toPx() }
        val overlap = with(density) { 6.dp.toPx() }
        val bottomSafe = with(density) { (navBottom + 112.dp).toPx() }
        val maxW = with(density) { maxWidth.toPx() }
        val maxH = with(density) { maxHeight.toPx() }

        // the row, in this overlay's own coordinates
        val row = t.rowBounds.translate(-origin)
        // under the row if there is room, otherwise above it
        val below = row.bottom - overlap + h <= maxH - bottomSafe
        val x = (row.right - with(density) { 20.dp.toPx() } - w).coerceIn(margin, (maxW - w - margin).coerceAtLeast(margin))
        val y = (if (below) row.bottom - overlap else row.top - h + overlap).coerceAtLeast(margin)

        // dark veil
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = p.value.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = if (dark) 0.42f else 0.22f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
        )
        // the pressed row stays lit
        Box(
            Modifier
                .offset { IntOffset(row.left.roundToInt(), row.top.roundToInt()) }
                .size(with(density) { row.width.toDp() }, with(density) { row.height.toDp() })
                .graphicsLayer { alpha = p.value.coerceIn(0f, 1f) }
                .clip(RoundedCornerShape(22.dp))
                .background(fg.copy(alpha = 0.10f))
        )

        val shape = RoundedCornerShape(26.dp)
        Column(
            Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .width(MENU_W)
                .graphicsLayer {
                    val k = p.value
                    val open = k.coerceIn(0f, 1f)
                    // it grows out of the corner that is next to the row, springing a little past its size
                    transformOrigin = TransformOrigin(1f, if (below) 0f else 1f)
                    val s = 0.55f + 0.45f * k
                    scaleX = s
                    scaleY = s
                    alpha = (open * 1.7f).coerceIn(0f, 1f)
                    translationY = (1f - open) * (if (below) -10.dp.toPx() else 10.dp.toPx())
                    renderEffect = blurEffect(14.dp.toPx() * (1f - open))
                }
                .liquidCard(backdrop, shape, dark)
                .background(
                    if (dark) Color(0xFF16161C).copy(alpha = 0.84f) else Color.White.copy(alpha = 0.88f),
                    shape
                )
                .pointerInput(Unit) { detectTapGestures { } }            // taps on the menu do not close it
                .padding(MENU_PAD)
        ) {
            actions.forEachIndexed { i, a ->
                if (a == ChannelAction.Delete || (a == ChannelAction.Leave && ChannelAction.Delete !in actions)) {
                    // a thin line before the two dangerous ones
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .height(1.dp)
                            .graphicsLayer { alpha = itemProgress(p.value, i) }
                            .background(fg.copy(alpha = 0.10f))
                    )
                }
                MenuItem(
                    icon = when (a) {
                        ChannelAction.Archive -> R.drawable.ic_archive
                        ChannelAction.Mute -> R.drawable.ic_mute
                        ChannelAction.Delete -> R.drawable.ic_delete
                        ChannelAction.Leave -> R.drawable.ic_logout
                    },
                    label = stringResource(
                        when (a) {
                            ChannelAction.Archive -> R.string.home_menu_archive
                            ChannelAction.Mute -> R.string.home_menu_mute
                            ChannelAction.Delete -> R.string.home_menu_delete
                            ChannelAction.Leave -> R.string.home_menu_leave
                        }
                    ),
                    color = if (a == ChannelAction.Delete || a == ChannelAction.Leave) DANGER else fg,
                    progress = { itemProgress(p.value, i) },
                    below = below,
                    onClick = { onAction(t.channel, a) }
                )
            }
        }
    }
}

/** Each item has its own progress, one after another (like HyperSpring's cascade). */
private fun itemProgress(k: Float, index: Int): Float = ((k - 0.22f - index * 0.09f) / 0.45f).coerceIn(0f, 1f)

@Composable
private fun MenuItem(
    icon: Int,
    label: String,
    color: Color,
    progress: () -> Float,
    below: Boolean,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val bg by animateFloatAsState(if (pressed) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 600f), label = "menuItemBg")
    Row(
        Modifier
            .fillMaxWidth()
            .height(ITEM_H)
            .graphicsLayer {
                val v = progress()
                alpha = v
                translationY = (1f - v) * (if (below) -8.dp.toPx() else 8.dp.toPx())
                val sc = 0.94f + 0.06f * v
                scaleX = sc
                scaleY = sc
            }
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = 0.14f * bg))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onClick() }
                )
            }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) { GlyphIcon(icon, color, 20.dp) }
        Spacer(Modifier.width(12.dp))
        BasicText(
            label,
            maxLines = 1,
            style = TextStyle(color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        )
    }
}

// =====================================================================
// "Are you sure?"
// =====================================================================
@Composable
internal fun ConfirmOverlay(
    request: ConfirmRequest?,
    dark: Boolean,
    backdrop: Backdrop,
    onConfirm: (ConfirmRequest) -> Unit,
    onDismiss: () -> Unit
) {
    var shown by remember { mutableStateOf<ConfirmRequest?>(null) }
    val p = remember { Animatable(0f) }
    LaunchedEffect(request) {
        if (request != null) {
            shown = request
            p.snapTo(0f)
            p.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 330f))
        } else if (shown != null) {
            p.animateTo(0f, tween(160))
            shown = null
        }
    }
    val r = shown ?: return
    BackHandler(enabled = request != null) { onDismiss() }

    val fg = if (dark) Color.White else Color(0xFF101114)
    val isDelete = r.action == ChannelAction.Delete
    val ch = r.channel
    val title = stringResource(if (isDelete) R.string.home_confirm_delete_title else R.string.home_confirm_leave_title)
    val message = when {
        isDelete -> stringResource(R.string.home_confirm_delete_msg, ch.name)
        ch.isOwner && ch.memberCount > 1 -> stringResource(R.string.home_confirm_leave_owner_msg, ch.name)
        ch.isOwner -> stringResource(R.string.home_confirm_leave_alone_msg, ch.name)
        else -> stringResource(R.string.home_confirm_leave_msg, ch.name)
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = p.value.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = if (dark) 0.55f else 0.30f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
        )
        val shape = RoundedCornerShape(30.dp)
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val k = p.value
                    val open = k.coerceIn(0f, 1f)
                    val s = 0.80f + 0.20f * k
                    scaleX = s
                    scaleY = s
                    alpha = (open * 1.6f).coerceIn(0f, 1f)
                    renderEffect = blurEffect(12.dp.toPx() * (1f - open))
                }
                .liquidCard(backdrop, shape, dark)
                .background(
                    if (dark) Color(0xFF16161C).copy(alpha = 0.90f) else Color.White.copy(alpha = 0.92f),
                    shape
                )
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(DANGER.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) { GlyphIcon(if (isDelete) R.drawable.ic_delete else R.drawable.ic_logout, DANGER, 28.dp) }
            Spacer(Modifier.height(14.dp))
            BasicText(
                title,
                style = TextStyle(color = fg, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            )
            Spacer(Modifier.height(8.dp))
            BasicText(
                message,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(color = fg.copy(alpha = 0.70f), fontSize = 14.sp, lineHeight = 21.sp, textAlign = TextAlign.Center)
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiquidButton(
                    onClick = { onConfirm(r) },
                    backdrop = backdrop,
                    tint = DANGER,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    BasicText(
                        stringResource(if (isDelete) R.string.home_confirm_delete_yes else R.string.home_confirm_leave_yes),
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    )
                }
                LiquidButton(
                    onClick = onDismiss,
                    backdrop = backdrop,
                    surfaceColor = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.30f),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    BasicText(
                        stringResource(R.string.home_confirm_cancel),
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(color = fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}
