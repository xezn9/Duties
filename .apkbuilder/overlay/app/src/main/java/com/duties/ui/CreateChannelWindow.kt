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
 * CreateChannelWindow.kt — the "new channel" form of the Create tab.
 *
 * It is not a separate window any more: the photo, the fields, the cards and the buttons of the form live
 * in the page itself, under the "Create" title, exactly where the guide / the buttons were. When "Create channel"
 * is pressed (p: 0 -> 1) the elements simply move in one after the other (rise from below, grow, un-blur, fade in
 * with a small spring); when the close button is pressed (p: 1 -> 0) they leave in the opposite order.
 * The round "Create channel" button is replaced, in the very same place, by the submit button of the form.
 *
 * Everything is driven by ONE number p (read in the draw phase, so nothing recomposes while it moves).
 */

private val WIN_ORANGE = Color(0xFFDB7608)
private val WIN_ORANGE_SOFT = Color(0xFFFF8A1F)
private val WIN_VIOLET = Color(0xFF8A5CFF)
private val WIN_MINT = Color(0xFF00D4AA)

private val WIN_RING = listOf(
    Color(0xFFFF8A00), Color(0xFF2BD9A0), Color(0xFF00C2FF),
    Color(0xFF3D7BFF), Color(0xFF8A5CFF), Color(0xFFFF8A00)
)

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


// ---- motion of the form elements
private const val FORM_STAGGER = 0.065f      // each element starts a bit after the previous one (share of p)
private const val FORM_SPAN = 0.42f          // how much of p one element needs

/** Ease-out with a small overshoot (a little spring at the end). */
private fun backOut(x: Float): Float {
    val t = x.coerceIn(0f, 1f) - 1f
    return 1f + 2.25f * t * t * t + 1.25f * t * t
}

/**
 * One element of the form. It comes up from below (dy), grows from [startScale], loses its blur and fades in,
 * at its own moment ([index] places it in the cascade). The way out is the same thing backwards.
 */
@Composable
private fun FormItem(
    index: Int,
    p: () -> Float,
    modifier: Modifier = Modifier,
    dy: Dp = 64.dp,
    startScale: Float = 0.9f,
    content: @Composable () -> Unit
) {
    Box(
        modifier.graphicsLayer {
            val a = index * FORM_STAGGER
            val k = seg(p(), a, a + FORM_SPAN)
            val e = backOut(k)
            alpha = seg(k, 0f, 0.55f)
            translationY = (1f - e) * dy.toPx()
            val s = startScale + (1f - startScale) * e
            scaleX = s
            scaleY = s
            renderEffect = blurEffect(14.dp.toPx() * (1f - k))
        }
    ) { content() }
}

/**
 * [p]: 0 = the form is not there, 1 = the form is fully in the page (the motion is played by [FormItem]s).
 * The area is the whole body of the Create tab; the form keeps clear of the bottom tabs like the buttons did
 * (the submit button stands where the "Create channel" button was).
 */
@Composable
internal fun CreateChannelForm(
    p: () -> Float,
    dark: Boolean,
    backdrop: Backdrop,
    draft: ChannelDraft,
    onClose: () -> Unit,
    onSearch: suspend (String) -> List<FoundUser>,
    onSubmit: suspend (name: String, username: String, bio: String, photo: Bitmap?, invitees: List<FoundUser>) -> Unit
) {
    val show by remember { derivedStateOf { p() > 0.0005f } }
    if (!show) return

    FormContent(
        p = p,
        dark = dark,
        backdrop = backdrop,
        draft = draft,
        onClose = onClose,
        onSearch = onSearch,
        onSubmit = onSubmit
    )
}

// =====================================================================
// Inside of the form
// =====================================================================
@Composable
private fun FormContent(
    p: () -> Float,
    dark: Boolean,
    backdrop: Backdrop,
    draft: ChannelDraft,
    onClose: () -> Unit,
    onSearch: suspend (String) -> List<FoundUser>,
    onSubmit: suspend (name: String, username: String, bio: String, photo: Bitmap?, invitees: List<FoundUser>) -> Unit
) {
    val fg = if (dark) Color.White else Color(0xFF101114)
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

    // ---- places: the submit button stands where the "Create channel" button was (108dp above the navigation bar)
    val density = LocalDensity.current
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBase = navBottom + 108.dp
    val ime = WindowInsets.ime.getBottom(density)
    val imeDp = with(density) { ime.toDp() }
    // keyboard: only the part that goes over that place lifts the button and the list
    val liftPx = (ime - with(density) { bottomBase.roundToPx() }).coerceAtLeast(0)
    val lift = with(density) { liftPx.toDp() }
    val submitH = 54.dp

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            // ---- top bar: close + title (stays in its place while the rest scrolls)
            FormItem(0, p, Modifier.fillMaxWidth().padding(horizontal = 24.dp), dy = 36.dp, startScale = 0.94f) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    GlassSurface(
                        backdrop = backdrop,
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
            }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(Modifier.height(16.dp))

                // ---- channel photo
                FormItem(2, p, Modifier.fillMaxWidth()) {
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
                }

                Spacer(Modifier.height(18.dp))

                // ---- name
                FormItem(3, p, Modifier.fillMaxWidth()) {
                    LiquidField(
                        icon = R.drawable.ic_tab_account,
                        tint = WIN_ORANGE_SOFT,
                        label = stringResource(R.string.create_field_name),
                        value = draft.name,
                        maxLen = NAME_MAX,
                        backdrop = backdrop,
                        dark = dark,
                        onValueChange = { draft.name = it.replace("\n", "").take(NAME_MAX) },
                        error = if (showErrors && !nameOk) stringResource(R.string.error_name_empty) else null,
                        keyboardType = KeyboardType.Text
                    )
                }
                Spacer(Modifier.height(12.dp))
                // ---- username
                FormItem(4, p, Modifier.fillMaxWidth()) {
                    LiquidField(
                        icon = R.drawable.ic_at,
                        tint = WIN_VIOLET,
                        label = stringResource(R.string.create_field_username),
                        value = draft.username,
                        maxLen = USERNAME_MAX,
                        backdrop = backdrop,
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
                }
                Spacer(Modifier.height(12.dp))
                // ---- bio (optional)
                FormItem(5, p, Modifier.fillMaxWidth()) {
                    LiquidField(
                        icon = R.drawable.ic_chat,
                        tint = WIN_MINT,
                        label = stringResource(R.string.create_win_bio_label),
                        value = draft.bio,
                        maxLen = BIO_MAX,
                        backdrop = backdrop,
                        dark = dark,
                        hint = stringResource(R.string.create_win_bio_hint),
                        singleLine = false,
                        minLines = 2,
                        onValueChange = { draft.bio = it.take(BIO_MAX) },
                        keyboardType = KeyboardType.Text
                    )
                }
                Spacer(Modifier.height(12.dp))
                // ---- channel type: public / private (empty for now, it does nothing yet)
                FormItem(6, p, Modifier.fillMaxWidth()) {
                    ChannelTypeCard(bd = backdrop, dark = dark, fg = fg)
                }
                Spacer(Modifier.height(12.dp))
                // ---- members (optional)
                FormItem(7, p, Modifier.fillMaxWidth()) {
                    MembersCard(bd = backdrop, dark = dark, fg = fg, count = draft.invitees.size, onAdd = { searching = true })
                }
                Spacer(Modifier.height(16.dp))
            }

            // room for the submit button (it floats over the bottom of the page, in the place of "Create channel")
            Spacer(Modifier.height(bottomBase + submitH + 14.dp + lift))
        }

        // ---- create (takes the place of the "Create channel" button: the same spot, it just fades in over it)
        FormItem(
            1, p,
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = bottomBase + lift),
            dy = 0.dp,
            startScale = 0.96f
        ) {
            LiquidButton(
                onClick = ::submit,
                backdrop = backdrop,
                tint = WIN_ORANGE,
                modifier = Modifier.fillMaxWidth().height(submitH)
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

        // search window (small, on top of everything in the form; it stops above the bottom tabs when there is no keyboard)
        AnimatedVisibility(
            visible = searching,
            enter = fadeIn(tween(180)) + scaleIn(spring(dampingRatio = 0.75f, stiffness = 420f), initialScale = 0.88f),
            exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.92f)
        ) {
            Box(Modifier.fillMaxSize().padding(bottom = if (imeDp > 0.dp) 0.dp else bottomBase)) {
                MemberSearchPopup(
                    dark = dark,
                    backdrop = backdrop,
                    draft = draft,
                    onSearch = onSearch,
                    onClose = { searching = false },
                    bottomInset = imeDp
                )
            }
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
