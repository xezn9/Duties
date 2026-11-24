package com.duties.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.drawBehind
import android.graphics.Bitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duties.R
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/*
 * ChatScreen.kt — the chat that opens when a channel row in Home is tapped.
 *
 * THE ANIMATION: the row itself grows into the chat.
 *   - [ChatMorph] draws a full-screen layer that is clipped to a rounded rectangle. At the start that rectangle is
 *     exactly the tapped row, then it grows until it covers the screen (and shrinks back into the row when going back).
 *   - The row is drawn again inside the rectangle (its text fades out while it grows) and its avatar FLIES to the
 *     avatar in the top bar. The top bar, the messages and the input bar fade in while the rectangle grows.
 *   - The real row in the list is hidden while the chat is open, so it looks like one thing that changes shape.
 *
 * THE LOOK: the top buttons, the name bar, the input bar and the send button are the same liquid glass as the rest
 * of the app (GlassSurface / liquidCard). They refract the messages and the background that move under them.
 *
 * THE DATA: Chat.kt (Realtime Database only, rules in database.rules.json).
 */

/** The channel that was tapped, where its row is (root coordinates) and a way to ask where the row is now. */
internal class ChatTarget(val channel: Channel, val from: Rect, val currentBounds: () -> Rect?)

private const val OPEN_MS = 620
private const val CLOSE_MS = 480
private val OPEN_EASE = CubicBezierEasing(0.25f, 0.85f, 0.25f, 1f)
private val CLOSE_EASE = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

private val HDR_AVATAR = 40.dp
private val BLUR_MAX = 9.dp
private val BAR_BUTTON = 48.dp

/** Where the circle starts: on the channel's avatar inside the tapped row (the row is right-to-left: avatar at the right end). */
private fun Density.avatarStart(from: Rect): Offset =
    Offset(from.right - ROW_PAD_H.toPx() - ROW_AVATAR.toPx() / 2f, from.center.y)

/** Radius that covers the whole screen from [c]. */
private fun coverRadius(c: Offset, w: Float, h: Float): Float {
    val dx = maxOf(c.x, w - c.x)
    val dy = maxOf(c.y, h - c.y)
    return kotlin.math.sqrt(dx * dx + dy * dy) + 2f
}

private class RoundedRectClip(val r: Rect, val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(r, CornerRadius(radius)))
}

/**
 * Hosts the chat. [target] = the tapped channel (null = nothing), [open] = true while it should be open: setting it
 * to false plays the closing animation, and [onDismissed] is called when the chat has shrunk back into its row.
 */
@Composable
internal fun ChatMorph(
    target: ChatTarget?,
    open: Boolean,
    dark: Boolean,
    uid: String,
    senderName: String,
    myPhoto: Bitmap?,
    onClose: () -> Unit,
    onDismissed: () -> Unit
) {
    val p = remember { Animatable(0f) }
    var shown by remember { mutableStateOf<ChatTarget?>(null) }
    var from by remember { mutableStateOf(Rect.Zero) }
    val density = LocalDensity.current

    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val close = {
        focus.clearFocus()
        keyboard?.hide()
        onClose()
    }
    BackHandler(enabled = open && target != null) { close() }

    LaunchedEffect(target, open) {
        if (open && target != null) {
            shown = target
            from = target.from
            p.snapTo(0f)
            p.animateTo(1f, tween(OPEN_MS, easing = OPEN_EASE))
        } else if (shown != null) {
            // the list may have moved while the chat was open: shrink into the row where it is NOW
            target?.currentBounds?.invoke()?.let { from = it }
            p.animateTo(0f, tween(CLOSE_MS, easing = CLOSE_EASE))
            shown = null
            onDismissed()
        }
    }

    val t = shown ?: return
    val screenW = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val screenH = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val textColor = if (dark) Color.White else Color(0xFF101114)
    val subColor = if (dark) Color(0xFFB0B3B8) else Color(0xFF55586A)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(Modifier.fillMaxSize()) {
            // ---- the growing layer: the channel's avatar grows as a circle until it covers the screen,
            // the chat shows up inside it. A light blur (Android 12+) while it moves.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val k = p.value
                        val c = avatarStart(from)
                        val rEnd = coverRadius(c, size.width, size.height)
                        val r = lerpF(ROW_AVATAR.toPx() / 2f, rEnd, k)
                        clip = true
                        shape = RoundedRectClip(Rect(c, r), r)
                        val blur = BLUR_MAX.toPx() * kotlin.math.sin(Math.PI * k).toFloat()
                        renderEffect = if (blur > 0.5f && k < 0.995f && android.os.Build.VERSION.SDK_INT >= 31)
                            BlurEffect(blur, blur, TileMode.Clamp) else null
                    }
                    .pointerInput(Unit) { detectTapGestures { } }          // covers what is below
            ) {
                ChatContent(
                    channel = t.channel,
                    dark = dark,
                    uid = uid,
                    senderName = senderName,
                    myPhoto = myPhoto,
                    reveal = { seg(p.value, 0.35f, 0.85f) },
                    onBack = close
                )

                // the avatar itself, scaled up from its place until it fills the circle; it fades into the chat
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Box(Modifier.fillMaxSize()) {
                        Box(
                            Modifier
                                .absoluteOffset {
                                    val c = avatarStart(from)
                                    IntOffset((c.x - ROW_AVATAR.toPx() / 2f).roundToInt(), (c.y - ROW_AVATAR.toPx() / 2f).roundToInt())
                                }
                                .size(ROW_AVATAR)
                                .graphicsLayer {
                                    val c = avatarStart(from)
                                    val rEnd = coverRadius(c, screenW, screenH)
                                    val r = lerpF(ROW_AVATAR.toPx() / 2f, rEnd, p.value)
                                    val sc = r / (ROW_AVATAR.toPx() / 2f)
                                    scaleX = sc
                                    scaleY = sc
                                    alpha = 1f - seg(p.value, 0.3f, 0.8f)
                                }
                        ) { Avatar(t.channel.photo, t.channel.name, ROW_AVATAR) }
                    }
                }
            }

            // ---- the thin glass rim of the circle (it fades away when the chat fills the screen)
            Canvas(Modifier.fillMaxSize()) {
                val k = p.value
                val a = seg(k, 0f, 0.12f) * (1f - seg(k, 0.5f, 0.9f))
                if (a <= 0f) return@Canvas
                val c = avatarStart(from)
                val r = lerpF(ROW_AVATAR.toPx() / 2f, coverRadius(c, size.width, size.height), k)
                drawCircle(
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(0.55f * a), Color.White.copy(0.08f * a)),
                        startY = c.y - r, endY = c.y + r
                    ),
                    radius = r,
                    center = c,
                    style = Stroke(0.9.dp.toPx())
                )
            }
        }
    }
}

// =====================================================================
// The chat itself
// =====================================================================

@Composable
private fun ChatContent(
    channel: Channel,
    dark: Boolean,
    uid: String,
    senderName: String,
    myPhoto: Bitmap?,
    reveal: () -> Float,
    onBack: () -> Unit
) {
    val fg = if (dark) Color.White else Color(0xFF101114)
    val sub = if (dark) Color(0xFFB0B3B8) else Color(0xFF55586A)
    val glassFill = if (dark) Color(0xFF121212).copy(alpha = 0.40f) else Color.White.copy(alpha = 0.50f)
    val density = LocalDensity.current
    val context = LocalContext.current

    val nameNow by rememberUpdatedState(senderName)
    val chat = remember(channel.id) { ChatState(context.applicationContext, channel.id, uid) { nameNow } }
    DisposableEffect(chat) {
        chat.start()
        onDispose { chat.stop() }
    }

    // records the background and the messages: the glass bars refract them
    val chatBd = rememberLayerBackdrop()
    var topPx by remember { mutableIntStateOf(0) }
    var bottomPx by remember { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf("") }
    val failText = stringResource(R.string.chat_send_failed)
    val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }

    Box(Modifier.fillMaxSize()) {
        // ---------------- background + messages
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(chatBd)
        ) {
            ChatBackground(dark, Modifier.fillMaxSize())
            val listState = rememberLazyListState()
            val msgs = chat.messages
            val reversed = remember(msgs) { msgs.asReversed() }
            val last = msgs.lastOrNull()
            // messages that were there when the chat opened do not animate; the ones that arrive later do
            val known = remember { HashSet<String>() }
            val heights = remember { HashMap<String, Int>() }
            var baselineDone by remember { mutableStateOf(false) }
            LaunchedEffect(chat.loaded, msgs) {
                if (!baselineDone) {
                    known.addAll(msgs.map { it.id })
                    if (chat.loaded) baselineDone = true
                }
            }
            // a new message: the list stays at the bottom (no scroll animation: the message grows in by itself)
            LaunchedEffect(last?.id) {
                if (last != null && (listState.firstVisibleItemIndex <= 2 || last.uid == uid)) {
                    listState.scrollToItem(0)
                }
            }
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .fillMaxSize()
                    // same insets as the writing bar: the list shrinks together with the keyboard, in the same frame
                    .imePadding()
                    .navigationBarsPadding()
                    .graphicsLayer { alpha = reveal() },
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = with(density) { topPx.toDp() } + 10.dp,
                    bottom = with(density) { bottomPx.toDp() } + 10.dp
                ),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                itemsIndexed(reversed, key = { _, m -> m.id }) { i, m ->
                    val olderSame = i + 1 < reversed.size && reversed[i + 1].uid == m.uid
                    val newerSame = i > 0 && reversed[i - 1].uid == m.uid
                    val isNew = remember(m.id) { baselineDone && m.id !in known }
                    LaunchedEffect(m.id) { known.add(m.id) }
                    // how far the photo has to fall: from the previous message of the same person down to this one
                    val dropPx = if (olderSame) (heights[reversed[i + 1].id] ?: 0) + with(density) { 3.dp.toPx() } else 0f
                    MessageBubble(
                        m = m,
                        mine = m.uid == uid,
                        showName = !olderSame && m.uid != uid,
                        topGap = !olderSame,
                        showAvatar = !newerSame,
                        myPhoto = myPhoto,
                        isNew = isNew,
                        dropPx = dropPx,
                        onHeight = { heights[m.id] = it },
                        fg = fg,
                        time = timeFormat.format(Date(m.createdAtMs))
                    )
                }
            }
            if (chat.failed || (chat.loaded && msgs.isEmpty())) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 48.dp)
                        .graphicsLayer { alpha = reveal() },
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        stringResource(if (chat.failed) R.string.chat_error else R.string.chat_empty),
                        style = TextStyle(color = sub, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
                    )
                }
            }
        }

        // ---------------- top: settings, name bar (photo + name), back
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { topPx = it.height }
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .graphicsLayer { alpha = seg(reveal(), 0.1f, 0.8f) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            // first child = the right end (the app is right-to-left): back
            GlassSurface(
                backdrop = chatBd,
                container = glassFill,
                modifier = Modifier.size(BAR_BUTTON),
                onClick = onBack
            ) { GlyphIcon(R.drawable.ic_back, fg, 24.dp) }
            Spacer(Modifier.width(10.dp))
            GlassSurface(
                backdrop = chatBd,
                container = glassFill,
                modifier = Modifier.weight(1f).height(BAR_BUTTON)
            ) {
                Row(
                    Modifier.fillMaxSize().padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(channel.photo, channel.name, HDR_AVATAR)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        BasicText(
                            channel.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        )
                        BasicText(
                            stringResource(R.string.chat_members, channel.memberCount),
                            maxLines = 1,
                            style = TextStyle(color = sub, fontSize = 12.sp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            // channel settings: empty for now
            GlassSurface(
                backdrop = chatBd,
                container = glassFill,
                modifier = Modifier.size(BAR_BUTTON),
                onClick = { }
            ) { GlyphIcon(R.drawable.ic_settings, fg, 22.dp) }
        }

        // ---------------- bottom: writing bar (opens the keyboard) + send
        val canSend = draft.isNotBlank()
        val sendFill by animateColorAsState(
            if (canSend) Color(0xFFDB7608).copy(alpha = 0.92f) else glassFill,
            tween(200), label = "sendFill"
        )
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .onSizeChanged { bottomPx = it.height }      // the bar alone, without keyboard / nav bar
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .graphicsLayer {
                    val a = seg(reveal(), 0.1f, 0.8f)
                    alpha = a
                    translationY = (1f - a) * 28.dp.toPx()
                },
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .liquidCard(chatBd, RoundedCornerShape(26.dp), dark)
                    .heightIn(min = BAR_BUTTON)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    maxLines = 5,
                    textStyle = TextStyle(
                        color = fg, fontSize = 16.sp, lineHeight = 22.sp,
                        textDirection = TextDirection.Content
                    ),
                    cursorBrush = SolidColor(Color(0xFFFF9A2E)),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (draft.isEmpty()) {
                                BasicText(
                                    stringResource(R.string.chat_hint),
                                    style = TextStyle(color = sub, fontSize = 16.sp, lineHeight = 22.sp)
                                )
                            }
                            inner()
                        }
                    }
                )
            }
            GlassSurface(
                backdrop = chatBd,
                container = sendFill,
                modifier = Modifier.size(BAR_BUTTON),
                onClick = {
                    if (canSend) {
                        val sent = chat.send(draft) {
                            Toast.makeText(context, failText, Toast.LENGTH_SHORT).show()
                        }
                        if (sent) draft = ""
                    }
                }
            ) {
                GlyphIcon(
                    R.drawable.ic_send,
                    if (canSend) Color.White else fg.copy(alpha = 0.45f),
                    22.dp,
                    // the arrow points to the left: the app is right-to-left
                    Modifier.graphicsLayer { scaleX = -1f }
                )
            }
        }
    }
}

private val MSG_AVATAR = 34.dp

private val NAME_COLORS = listOf(
    Color(0xFFFF9F0A), Color(0xFF64D2FF), Color(0xFFBF5AF2), Color(0xFF30D158), Color(0xFFFF375F)
)

/** One message: a round white bubble with black text (like Telegram), the sender's photo beside the last message of a group. */
@Composable
private fun MessageBubble(
    m: ChatMessage,
    mine: Boolean,
    showName: Boolean,
    topGap: Boolean,
    showAvatar: Boolean,
    myPhoto: Bitmap?,
    isNew: Boolean,
    dropPx: Float,
    onHeight: (Int) -> Unit,
    fg: Color,
    time: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(m.uid) { UserPhotos.request(context, m.uid) }
    val photo = (if (mine) myPhoto else null) ?: UserPhotos.photos[m.uid]

    // a new message pops up from the bottom (where the writing bar is) with a soft spring
    val enter = remember { Animatable(if (isNew) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (isNew) enter.animateTo(1f, tween(320, easing = CubicBezierEasing(0.2f, 0.9f, 0.25f, 1f)))
    }

    // the tail corner (small) is on the photo's side and only on the last message of a group
    val tail = if (showAvatar) 6.dp else 20.dp
    val shape = RoundedCornerShape(
        topStart = 20.dp, topEnd = 20.dp,
        bottomStart = if (mine) tail else 20.dp,
        bottomEnd = if (mine) 20.dp else tail
    )
    // the item's height grows from 0 to its real height: the older messages slide up smoothly and the new bubble
    // comes out from under them (from the writing bar). It is the only motion: nothing else scrolls or moves.
    Box(
        modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val h = (placeable.height * enter.value.coerceIn(0f, 1f)).roundToInt()
            layout(placeable.width, h) { placeable.place(0, 0) }
        }
    ) {
    Box(
        Modifier
            .fillMaxWidth()
            .onSizeChanged { onHeight(it.height) }
            .padding(top = if (topGap) 6.dp else 0.dp),
        contentAlignment = if (mine) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        // right-to-left row: mine = [photo][bubble] on the right, the others = [bubble][photo] on the left.
        Row(verticalAlignment = Alignment.Bottom) {
            if (mine) {
                MsgAvatarSlot(showAvatar, isNew, dropPx, photo, m.name)
                Spacer(Modifier.width(6.dp))
            }
            Column(
                Modifier
                    .widthIn(max = 260.dp)
                    .graphicsLayer {
                        val e = enter.value.coerceIn(0f, 1f)
                        alpha = e
                        val sc = 0.88f + 0.12f * e
                        scaleX = sc
                        scaleY = sc
                        transformOrigin = TransformOrigin(if (mine) 1f else 0f, 0f)
                    }
            ) {
                if (showName && m.name.isNotBlank()) {
                    BasicText(
                        m.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 2.dp),
                        style = TextStyle(
                            color = NAME_COLORS[(m.uid.hashCode() and 0x7fffffff) % NAME_COLORS.size],
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Column(
                    Modifier
                        .background(Color.White, shape)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    BasicText(
                        m.text,
                        style = TextStyle(
                            color = Color(0xFF101114), fontSize = 16.sp, lineHeight = 22.sp,
                            textDirection = TextDirection.Content
                        )
                    )
                    BasicText(
                        time,
                        modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                        style = TextStyle(color = Color(0xFF8A8F98), fontSize = 11.sp)
                    )
                }
            }
            if (!mine) {
                Spacer(Modifier.width(6.dp))
                MsgAvatarSlot(showAvatar, isNew, dropPx, photo, m.name)
            }
        }
    }
    }
}

/**
 * The place of the sender's photo beside a message. When a new message of the same person arrives, the photo
 * of the old last message is gone and the one of the new message falls from the old message down to its place.
 * (A new person's first message: the photo pops in.)
 */
@Composable
private fun MsgAvatarSlot(show: Boolean, isNew: Boolean, dropPx: Float, photo: Bitmap?, name: String) {
    val fall = remember { Animatable(if (show && isNew && dropPx > 0f) -dropPx else 0f) }
    val pop = remember { Animatable(if (show && isNew && dropPx <= 0f) 0f else 1f) }
    LaunchedEffect(show) {
        if (show) {
            if (fall.value != 0f) fall.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 200f))
            if (pop.value != 1f) pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 380f))
        }
    }
    Box(Modifier.size(MSG_AVATAR)) {
        if (show) {
            Box(
                Modifier.graphicsLayer {
                    translationY = fall.value
                    val sc = pop.value
                    scaleX = sc
                    scaleY = sc
                }
            ) { Avatar(photo, name, MSG_AVATAR) }
        }
    }
}

/** Calm background for the chats: one soft colour, no bright spots (the glass bars refract it). */
@Composable
private fun ChatBackground(dark: Boolean, modifier: Modifier = Modifier) {
    val base = if (dark) listOf(Color(0xFF2C4468), Color(0xFF283F5F), Color(0xFF233851))
    else listOf(Color(0xFFD3DFEE), Color(0xFFCBD9EB), Color(0xFFC4D3E8))
    val glow = if (dark) Color(0xFF5B83B3).copy(alpha = 0.35f) else Color(0xFFE6EFFA).copy(alpha = 0.60f)
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(base))
        val c = Offset(size.width * 0.5f, size.height * 0.08f)
        val r = size.maxDimension * 0.55f
        drawCircle(Brush.radialGradient(listOf(glow, Color.Transparent), center = c, radius = r), radius = r, center = c)
    }
}
