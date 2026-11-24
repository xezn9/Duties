package com.duties.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duties.R
import kotlinx.coroutines.delay

/**
 * Home tab: the channels of the account as a chat list (photo, name, last line, number of members).
 * The newest channel is on top. Empty for now when there are no channels.
 *
 * Entering the tab: the rows come in one after another, right behind the title (HyperSpring cascade: each row
 * rises, sharpens from a blur and settles). Long press on a row: a small menu (see ChannelMenu.kt).
 */
@Composable
internal fun HomePage(
    dark: Boolean,
    backdrop: Backdrop,
    channels: List<Channel>,
    invites: List<Invite>,
    cascade: Cascade,
    onAccept: (Invite) -> Unit,
    onDecline: (Invite) -> Unit,
    onConfirmRequest: (ConfirmRequest) -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = if (dark) Color.White else Color(0xFF101114)
    val subColor = if (dark) Color(0xFFB0B3B8) else Color(0xFF55586A)
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // long press menu ("are you sure?" is a LiquidDialog drawn by MainScreen, over the whole screen)
    var menu by remember { mutableStateOf<MenuTarget?>(null) }
    val menuP = remember { Animatable(0f) }
    // rows that already came in: they do not come in again when they are scrolled back into view
    val played = remember { HashSet<String>() }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(modifier.fillMaxWidth()) {
            if (channels.isEmpty() && invites.isEmpty()) {
                Reveal(delayMs = remember { cascade.take() }, modifier = Modifier.fillMaxSize(), dy = 14.dp) {
                    Box(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 60.dp), contentAlignment = Alignment.TopCenter) {
                        BasicText(
                            stringResource(R.string.home_empty),
                            style = TextStyle(color = subColor, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
                        )
                    }
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            // while the menu is open the list behind it softly blurs
                            val v = menuP.value.coerceIn(0f, 1f)
                            renderEffect = blurEffect(5.dp.toPx() * v)
                        },
                    contentPadding = PaddingValues(top = 8.dp, bottom = 130.dp + navBottom)
                ) {
                    if (invites.isNotEmpty()) {
                        item(key = "invites-title") {
                            Entrance("invites-title", played, cascade, true, Modifier.animateItem()) {
                                BasicText(
                                    stringResource(R.string.home_invites_title) + "  (" + invites.size + ")",
                                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                                    style = TextStyle(color = subColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                        items(invites, key = { "inv-" + it.id }) { inv ->
                            Entrance("inv-" + inv.id, played, cascade, true, Modifier.animateItem()) {
                                InviteCard(
                                    inv, dark, backdrop, textColor, subColor,
                                    onAccept = { onAccept(inv) },
                                    onDecline = { onDecline(inv) }
                                )
                            }
                        }
                        if (channels.isNotEmpty()) {
                            item(key = "chats-title") {
                                Entrance("chats-title", played, cascade, true, Modifier.animateItem()) {
                                    BasicText(
                                        stringResource(R.string.home_channels_title),
                                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 2.dp),
                                        style = TextStyle(color = subColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    )
                                }
                            }
                        }
                    }
                    itemsIndexed(channels, key = { _, ch -> "ch-" + ch.id }) { index, ch ->
                        // only the rows that are on the screen when the tab opens come in one by one
                        Entrance("ch-" + ch.id, played, cascade, index < FIRST_SCREEN_ROWS, Modifier.animateItem()) {
                            ChannelRow(
                                ch, dark, textColor, subColor,
                                onLongPress = { bounds ->
                                    if (menu == null) menu = MenuTarget(ch, bounds)
                                }
                            )
                        }
                    }
                }
            }

            ChannelActionOverlay(
                target = menu,
                p = menuP,
                dark = dark,
                backdrop = backdrop,
                onDismiss = { menu = null },
                onAction = { ch, action ->
                    menu = null
                    when (action) {
                        // empty for now
                        ChannelAction.Archive, ChannelAction.Mute -> Unit
                        ChannelAction.Delete, ChannelAction.Leave -> onConfirmRequest(ConfirmRequest(ch, action))
                    }
                }
            )
        }
    }
}

private const val FIRST_SCREEN_ROWS = 9

/**
 * HyperSpring entrance of one list element: it waits for its turn in the tab's [cascade], then rises, sharpens
 * from a blur and settles. [animate] = false (rows far below the screen) shows it at once, and an element that
 * already came in once ([played]) is never animated again.
 */
@Composable
private fun Entrance(
    key: String,
    played: MutableSet<String>,
    cascade: Cascade,
    animate: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val skip = remember { !animate || key in played }
    val delayMs = remember { if (skip) 0 else cascade.take() }
    val a = remember { Animatable(if (skip) 1f else 0f) }
    LaunchedEffect(Unit) {
        played += key
        if (!skip) {
            delay(delayMs.toLong())
            a.animateTo(1f, tween(720, easing = SETTLE))
        }
    }
    Box(
        modifier.graphicsLayer {
            val t = a.value
            alpha = (t * 1.6f).coerceIn(0f, 1f)
            translationY = (1f - t) * 26.dp.toPx()
            val s = 0.94f + 0.06f * t
            scaleX = s
            scaleY = s
            renderEffect = blurEffect(10.dp.toPx() * (1f - t))
        }
    ) { content() }
}

/** An invitation: who invited me to which channel, with Accept / Decline. */
@Composable
private fun InviteCard(
    inv: Invite,
    dark: Boolean,
    backdrop: Backdrop,
    textColor: Color,
    subColor: Color,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .liquidCard(backdrop, shape, dark)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(inv.photo, inv.channelName, 54.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                BasicText(
                    stringResource(R.string.home_invite_label),
                    style = TextStyle(color = Color(0xFFFF9A2E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    inv.channelName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    stringResource(R.string.home_invite_from, inv.fromUsername.ifBlank { inv.fromName }),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = subColor, fontSize = 13.sp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LiquidButton(
                onClick = onAccept,
                backdrop = backdrop,
                tint = Color(0xFFDB7608),
                modifier = Modifier.weight(1f).height(46.dp)
            ) {
                BasicText(
                    stringResource(R.string.home_invite_accept),
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                )
            }
            LiquidButton(
                onClick = onDecline,
                backdrop = backdrop,
                surfaceColor = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.30f),
                modifier = Modifier.weight(1f).height(46.dp)
            ) {
                BasicText(
                    stringResource(R.string.home_invite_decline),
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(color = textColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

/** Channel photo, or the first letter on a colourful circle. */
@Composable
private fun Avatar(photo: android.graphics.Bitmap?, name: String, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFFFF8A00), Color(0xFFFF375F)))),
        contentAlignment = Alignment.Center
    ) {
        if (photo != null) {
            val img = remember(photo) { photo.asImageBitmap() }
            Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            BasicText(
                name.trim().take(1).uppercase(),
                style = TextStyle(color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun ChannelRow(
    ch: Channel,
    dark: Boolean,
    textColor: Color,
    subColor: Color,
    onLongPress: (androidx.compose.ui.geometry.Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var pressed by remember { mutableStateOf(false) }
    val press by animateFloatAsState(if (pressed) 0.965f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "rowPress")
    Row(
        modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords[0] = it }
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .pointerInput(ch.id) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onLongPress = {
                        val c = coords[0]
                        if (c != null && c.isAttached) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress(c.boundsInRoot())
                        }
                    }
                )
            }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(ch.photo, ch.name, 58.dp)
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
                if (ch.bio.isNotBlank()) ch.bio else "@" + ch.username,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = subColor, fontSize = 14.sp)
            )
        }
        Spacer(Modifier.width(10.dp))
        // how many people are in the channel right now
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
                ch.memberCount.toString(),
                style = TextStyle(color = subColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            )
        }
    }
}
