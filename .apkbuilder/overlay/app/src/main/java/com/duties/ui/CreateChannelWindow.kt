package com.duties.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duties.R
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * CreateChannelWindow.kt — the big "new channel" window of the Create tab.
 *
 * Opening: the "Create channel" button turns into a small liquid drop, the drop rises up the screen and
 * then grows (with a little spring) into a big window with round corners. Closing is the same thing backwards.
 * Everything is driven by ONE number p (0 = button, 1 = window); the geometry is read in the layout / draw
 * phase, so nothing recomposes while it moves.
 *
 * Inside: channel photo, name, username, bio (optional), add members (optional) and a close button.
 */

private val WIN_ORANGE = Color(0xFFDB7608)
private val WIN_ORANGE_SOFT = Color(0xFFFF8A1F)
private val WIN_VIOLET = Color(0xFF8A5CFF)
private val WIN_MINT = Color(0xFF00D4AA)

private val WIN_RING = listOf(
    Color(0xFFFF8A00), Color(0xFF2BD9A0), Color(0xFF00C2FF),
    Color(0xFF3D7BFF), Color(0xFF8A5CFF), Color(0xFFFF8A00)
)

private val BLOB_RADIUS = 38.dp

/** What the person typed / picked in the window (kept while the window is closed). */
internal class ChannelDraft {
    var name by mutableStateOf("")
    var username by mutableStateOf("")
    var bio by mutableStateOf("")
    var photo by mutableStateOf<Bitmap?>(null)
    val invitees = mutableStateListOf<FoundUser>()       // people to invite when the channel is created

    fun clear() {
        invitees.clear()
        name = ""
        username = ""
        bio = ""
        photo = null
    }
}

/**
 * Capsule while it is small (button / drop), big rounded corners when it is the window.
 * It is a real [RoundedCornerShape] (corner = half of the short side, at most [BLOB_RADIUS]), because the glass
 * "lens" effect of the backdrop library needs a corner based shape.
 */
private val BlobShape: Shape = RoundedCornerShape(
    object : CornerSize {
        override fun toPx(shapeSize: Size, density: Density): Float =
            minOf(shapeSize.minDimension / 2f, with(density) { BLOB_RADIUS.toPx() })
    }
)

private fun ease(x: Float): Float = FastOutSlowInEasing.transform(x.coerceIn(0f, 1f))

/**
 * [p]: 0 = the "Create channel" button, 1 = the open window (it may go a bit over 1 while the spring settles).
 * The place of the button and of the window is worked out from the size of the area, in the same way
 * [CreatePage] lays out its buttons (108dp above the navigation bar, 24dp from the sides).
 */
@Composable
internal fun CreateChannelWindow(
    p: () -> Float,
    dark: Boolean,
    backdrop: Backdrop,
    draft: ChannelDraft,
    onClose: () -> Unit,
    onSearch: suspend (String) -> List<FoundUser>,
    onSubmit: suspend (name: String, username: String, bio: String, photo: Bitmap?, invitees: List<FoundUser>) -> Unit
) {
    val show by remember { derivedStateOf { p() > 0.0005f } }
    val contentOn by remember { derivedStateOf { p() > 0.45f } }
    if (!show) return

    val density = LocalDensity.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // ---- geometry (dp)
        val btnW = maxWidth - 48.dp
        val btnH = 54.dp
        val btnCy = maxHeight - navBottom - 108.dp - btnH / 2
        val winW = maxWidth - 32.dp
        val winTop = 4.dp
        val rowTop = maxHeight - navBottom - 108.dp - btnH
        val winH = rowTop - 14.dp - winTop                 // the window ends above the place of the button
        val winCy = winTop + winH / 2
        val dropS = 52.dp
        // distance from the window's bottom edge to the bottom of the screen (for the keyboard)
        val windowBottomGap = navBottom + 108.dp + btnH + 14.dp

        // ---- geometry (px)
        val cw = with(density) { maxWidth.toPx() }
        val bw = with(density) { btnW.toPx() }
        val bh = with(density) { btnH.toPx() }
        val bcy = with(density) { btnCy.toPx() }
        val ww = with(density) { winW.toPx() }
        val wh = with(density) { winH.toPx() }
        val wcy = with(density) { winCy.toPx() }
        val ds = with(density) { dropS.toPx() }

        /** [width, height, centerY] of the blob for progress [pv]. */
        fun rect(pv: Float): FloatArray {
            val v = pv.coerceAtLeast(0f)
            val a = ease(seg(v, 0f, 0.22f))                    // button -> small drop
            val t = ease(seg(v, 0.10f, 0.62f))                 // the drop travels up
            val gg = ((v - 0.5f) / 0.5f).coerceAtLeast(0f)     // the drop grows into the window
            val g = ease(minOf(gg, 1f)) + (gg - 1f).coerceAtLeast(0f) * 0.6f   // a little overshoot is kept (the spring)
            val w = lerpF(lerpF(bw, ds, a), ww, g)
            val h = lerpF(lerpF(bh, ds, a), wh, g)
            return floatArrayOf(w.coerceAtLeast(1f), h.coerceAtLeast(1f), lerpF(bcy, wcy, t))
        }

        Box(
            Modifier
                .offset {
                    val r = rect(p())
                    IntOffset(((cw - r[0]) / 2f).roundToInt(), (r[2] - r[1] / 2f).roundToInt())
                }
                .layout { measurable, _ ->
                    val r = rect(p())
                    val w = r[0].roundToInt().coerceAtLeast(1)
                    val h = r[1].roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(w, h) { placeable.place(0, 0) }
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { BlobShape },
                    effects = {
                        // the same liquid glass as the dialogs of the app (stronger lens once it is the big window)
                        val big = seg(p(), 0.5f, 0.9f)
                        vibrancy()
                        blur(lerpF(10f, 14f, big).dp.toPx() * GlassTheme.blurScale)
                        lens(lerpF(12f, 16f, big).dp.toPx() * GlassTheme.lensScale, lerpF(24f, 32f, big).dp.toPx() * GlassTheme.lensScale)
                    },
                    layerBlock = {
                        // while it travels the drop is stretched tall (liquid), then it settles round
                        val v = p().coerceAtLeast(0f)
                        val travel = sin(PI.toFloat() * seg(v, 0.10f, 0.62f))
                        val small = 1f - seg(v, 0.45f, 0.65f)
                        scaleY = 1f + 0.28f * travel * small
                        scaleX = 1f - 0.14f * travel * small
                    },
                    onDrawSurface = {
                        val v = p()
                        val orange = 1f - seg(v, 0.25f, 0.8f)          // orange like the button, then dark / light glass
                        drawRect(
                            color = GlassTheme.fill(if (dark) Color(0xFF121212).copy(alpha = 0.45f) else Color(0xFFFAFAFA).copy(alpha = 0.45f)),
                            alpha = seg(v, 0.2f, 0.8f)
                        )
                        if (orange > 0.001f) {
                            drawRect(color = WIN_ORANGE, blendMode = BlendMode.Hue, alpha = orange)
                            drawRect(color = WIN_ORANGE.copy(alpha = 0.75f), alpha = orange)
                        }
                    }
                )
                .border(
                    0.9.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(0.55f), Color.White.copy(0.08f))),
                    BlobShape
                )
                .clip(BlobShape)
                .pointerInput(Unit) { detectTapGestures { } }       // nothing under the window can be touched
        ) {
            // the label of the button, while it still looks like the button
            Box(
                Modifier.fillMaxSize().graphicsLayer { alpha = 1f - seg(p(), 0f, 0.10f) },
                contentAlignment = Alignment.Center
            ) {
                Row(Modifier.wrapContentWidth(unbounded = true), verticalAlignment = Alignment.CenterVertically) {
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

            // the window itself: laid out at its final size and only revealed by the growing blob
            if (contentOn) {
                Box(
                    Modifier
                        .requiredSize(winW, winH)
                        .graphicsLayer { alpha = seg(p(), 0.72f, 0.98f) }
                ) {
                    ChannelWindowContent(
                        dark = dark,
                        draft = draft,
                        bottomGap = windowBottomGap,
                        onClose = onClose,
                        onSearch = onSearch,
                        onSubmit = onSubmit
                    )
                }
            }
        }
    }
}

// =====================================================================
// Inside of the window
// =====================================================================
@Composable
private fun ChannelWindowContent(
    dark: Boolean,
    draft: ChannelDraft,
    bottomGap: Dp,
    onClose: () -> Unit,
    onSearch: suspend (String) -> List<FoundUser>,
    onSubmit: suspend (name: String, username: String, bio: String, photo: Bitmap?, invitees: List<FoundUser>) -> Unit
) {
    val fg = if (dark) Color.White else Color(0xFF101114)
    val inner = rememberLayerBackdrop()          // what the glass fields inside the window refract
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val loadError = stringResource(R.string.error_image_load)

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = decodePickedImage(context, uri)
                if (bmp != null) draft.photo = squareCrop(bmp)
                else Toast.makeText(context, loadError, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---- create the channel for real
    val focus = LocalFocusManager.current
    var showErrors by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var created by remember { mutableStateOf(false) }
    var taken by remember { mutableStateOf(false) }
    val saveError = stringResource(R.string.error_save)
    val nameOk = isValidName(draft.name)
    val userOk = isValidUsername(draft.username)
    val photoOk = draft.photo != null

    fun submit() {
        showErrors = true
        if (busy || created || !nameOk || !userOk || !photoOk) return
        busy = true
        focus.clearFocus()
        scope.launch {
            try {
                onSubmit(draft.name.trim(), draft.username, draft.bio.trim(), draft.photo, draft.invitees.toList())
                created = true
                delay(650)
                onClose()
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                busy = false
                if (e.isUsernameTaken()) taken = true
                else Toast.makeText(context, saveError, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ---- the small "add members" search window
    var searching by remember { mutableStateOf(false) }
    BackHandler(enabled = searching) { searching = false }

    // keyboard: only the part that really covers the window pushes the content up
    val density = LocalDensity.current
    val ime = WindowInsets.ime.getBottom(density)
    val gapPx = with(density) { bottomGap.roundToPx() }
    val imeExtra = with(density) { (ime - gapPx).coerceAtLeast(0).toDp() }

    Box(Modifier.fillMaxSize()) {
        // soft colour blobs behind the glass, so the fields have something to refract
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(inner)
                .drawBehind {
                    fun blob(c: Color, cx: Float, cy: Float, r: Float) = drawCircle(
                        brush = Brush.radialGradient(
                            listOf(c.copy(alpha = if (dark) 0.22f else 0.16f), Color.Transparent),
                            center = Offset(size.width * cx, size.height * cy),
                            radius = size.minDimension * r
                        ),
                        radius = size.minDimension * r,
                        center = Offset(size.width * cx, size.height * cy)
                    )
                    blob(WIN_ORANGE_SOFT, 0.15f, 0.10f, 0.75f)
                    blob(WIN_VIOLET, 0.95f, 0.45f, 0.70f)
                    blob(WIN_MINT, 0.20f, 0.95f, 0.65f)
                }
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp + imeExtra)
        ) {
            // ---- top bar: close + title
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GlassSurface(
                    backdrop = inner,
                    container = if (dark) Color(0xFF121212).copy(alpha = 0.40f) else Color.White.copy(alpha = 0.50f),
                    modifier = Modifier.size(48.dp),
                    onClick = onClose
                ) { GlyphIcon(R.drawable.ic_close, fg, 22.dp) }
                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                    BasicText(
                        stringResource(R.string.create_win_title),
                        style = TextStyle(color = fg, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                    )
                    BasicText(
                        stringResource(R.string.create_win_sub),
                        style = TextStyle(color = fg.copy(alpha = 0.6f), fontSize = 12.sp)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ---- channel photo
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                ChannelPhoto(
                    photo = draft.photo,
                    dark = dark,
                    onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                )
                Spacer(Modifier.height(8.dp))
                BasicText(
                    stringResource(if (draft.photo == null) R.string.create_win_photo_add else R.string.create_win_photo_change),
                    style = TextStyle(
                        color = if (showErrors && !photoOk) Color(0xFFFF5A5F) else fg.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                if (showErrors && !photoOk) {
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        stringResource(R.string.create_win_error_photo),
                        style = TextStyle(color = Color(0xFFFF5A5F), fontSize = 12.sp)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ---- name
            LiquidField(
                icon = R.drawable.ic_tab_account,
                tint = WIN_ORANGE_SOFT,
                label = stringResource(R.string.create_field_name),
                value = draft.name,
                maxLen = NAME_MAX,
                backdrop = inner,
                dark = dark,
                onValueChange = { draft.name = it.replace("\n", "").take(NAME_MAX) },
                error = if (showErrors && !nameOk) stringResource(R.string.error_name_empty) else null,
                keyboardType = KeyboardType.Text
            )
            Spacer(Modifier.height(12.dp))
            // ---- username
            LiquidField(
                icon = R.drawable.ic_at,
                tint = WIN_VIOLET,
                label = stringResource(R.string.create_field_username),
                value = draft.username,
                maxLen = USERNAME_MAX,
                backdrop = inner,
                dark = dark,
                onValueChange = { v ->
                    draft.username = v.filter { it.isUsernameChar() }.take(USERNAME_MAX)
                    taken = false
                },
                error = when {
                    taken -> stringResource(R.string.error_username_taken)
                    showErrors && !userOk -> stringResource(R.string.error_username_short)
                    else -> null
                },
                countBad = draft.username.isNotEmpty() && draft.username.length < USERNAME_MIN,
                keyboardType = KeyboardType.Ascii
            )
            Spacer(Modifier.height(12.dp))
            // ---- bio (optional)
            LiquidField(
                icon = R.drawable.ic_chat,
                tint = WIN_MINT,
                label = stringResource(R.string.create_win_bio_label),
                value = draft.bio,
                maxLen = BIO_MAX,
                backdrop = inner,
                dark = dark,
                hint = stringResource(R.string.create_win_bio_hint),
                singleLine = false,
                minLines = 2,
                onValueChange = { draft.bio = it.take(BIO_MAX) },
                keyboardType = KeyboardType.Text
            )
            Spacer(Modifier.height(12.dp))
            // ---- channel type: public / private (empty for now, it does nothing yet)
            ChannelTypeCard(bd = inner, dark = dark, fg = fg)
            Spacer(Modifier.height(12.dp))
            // ---- members (optional)
            MembersCard(bd = inner, dark = dark, fg = fg, count = draft.invitees.size, onAdd = { searching = true })

            Spacer(Modifier.height(20.dp))
            // ---- create
            LiquidButton(
                onClick = ::submit,
                backdrop = inner,
                tint = WIN_ORANGE,
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                BasicText(
                    stringResource(
                        when {
                            created -> R.string.create_win_done
                            busy -> R.string.create_win_creating
                            else -> R.string.create_win_submit
                        }
                    ),
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                )
            }
        }

        // search window (small, on top of everything in the window)
        AnimatedVisibility(
            visible = searching,
            enter = fadeIn(tween(180)) + scaleIn(spring(dampingRatio = 0.75f, stiffness = 420f), initialScale = 0.88f),
            exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.92f)
        ) {
            MemberSearchPopup(
                dark = dark,
                backdrop = inner,
                draft = draft,
                onSearch = onSearch,
                onClose = { searching = false },
                bottomInset = imeExtra
            )
        }
    }
}

/** Round photo with a slowly turning colourful ring and a breathing "+" badge. Tap = pick a photo. */
@Composable
private fun ChannelPhoto(photo: Bitmap?, dark: Boolean, onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "chPhoto")
    val rot by t.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "rot"
    )
    val pulse by t.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(850, easing = SETTLE), RepeatMode.Reverse),
        label = "pulse"
    )
    Box(
        Modifier
            .size(128.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            rotate(rot, pivot = center) {
                drawCircle(
                    brush = Brush.sweepGradient(WIN_RING, center = center),
                    radius = (size.minDimension - stroke) / 2f,
                    style = Stroke(stroke)
                )
            }
        }
        Box(
            Modifier
                .padding(12.dp)
                .fillMaxSize()
                .clip(CircleShape)
                .background(if (dark) Color(0xFF1A1B22) else Color(0xFFDDE0EC)),
            contentAlignment = Alignment.Center
        ) {
            if (photo != null) {
                val img = remember(photo) { photo.asImageBitmap() }
                Image(bitmap = img, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                GlyphIcon(
                    R.drawable.ic_image,
                    if (dark) Color.White.copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.35f),
                    44.dp
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(40.dp)
                .graphicsLayer { scaleX = pulse; scaleY = pulse }
                .clip(CircleShape)
                .background(Color(0xFFCC6A00))
                .border(2.5.dp, Color.White, CircleShape),
            contentAlignment = Alignment.Center
        ) { GlyphIcon(if (photo == null) R.drawable.ic_plus else R.drawable.ic_edit, Color.White, 22.dp) }
    }
}

/** "Channel (public / private)" row: only the place for it, the choice is added later. */
@Composable
private fun ChannelTypeCard(bd: Backdrop, dark: Boolean, fg: Color) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .liquidCard(bd, shape, dark)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(WIN_VIOLET.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) { GlyphIcon(R.drawable.ic_people, WIN_VIOLET, 22.dp) }
        Spacer(Modifier.width(12.dp))
        BasicText(
            stringResource(R.string.create_win_type_title),
            modifier = Modifier.weight(1f),
            style = TextStyle(color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        )
    }
}

/** "Add members" row (optional). The button does nothing yet. */
@Composable
private fun MembersCard(bd: Backdrop, dark: Boolean, fg: Color, count: Int, onAdd: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .liquidCard(bd, shape, dark)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(WIN_MINT.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) { GlyphIcon(R.drawable.ic_link, WIN_MINT, 22.dp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                stringResource(R.string.create_win_members_title),
                style = TextStyle(color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            )
            Spacer(Modifier.height(2.dp))
            BasicText(
                if (count > 0) stringResource(R.string.create_win_members_selected, count)
                else stringResource(R.string.create_win_members_sub),
                style = TextStyle(
                    color = if (count > 0) WIN_MINT else fg.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
            )
        }
        Spacer(Modifier.width(8.dp))
        LiquidButton(
            onClick = onAdd,
            backdrop = bd,
            tint = WIN_ORANGE,
            modifier = Modifier.height(40.dp)
        ) {
            GlyphIcon(R.drawable.ic_plus, Color.White, 16.dp)
            BasicText(
                stringResource(R.string.create_win_members_add),
                maxLines = 1,
                softWrap = false,
                style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

/** Middle square of the picked picture, at most 640 px. */
private fun squareCrop(src: Bitmap, side: Int = 640): Bitmap {
    val s = minOf(src.width, src.height)
    val sq = Bitmap.createBitmap(src, (src.width - s) / 2, (src.height - s) / 2, s, s)
    return if (s > side) Bitmap.createScaledBitmap(sq, side, side, true) else sq
}
