package com.duties.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duties.R
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private enum class CropTarget { Banner, Avatar }

private class CropRequest(val target: CropTarget, val source: Bitmap, val tf: CropTransform?, val gif: File? = null)

private val ERROR_RED = Color(0xFFFF5A5F)
private val AccentOrange = Color(0xFFFF8A1F)
private val AccentViolet = Color(0xFF8A5CFF)
private val AccentMint = Color(0xFF00D4AA)
private val DeleteRed = Color(0xFFE5484D)

/**
 * "Edit account" page, in the look of the chat app: liquid glass scene (banner melting at the top +
 * slowly moving color blobs), compact top bar, glass banner card with the round photo on its edge,
 * and glass fields with a colored icon and a character counter.
 * It is opened with PushStack, so it grows out of the pencil button and its parts come out of that
 * point one after another (Emerge). Nothing is saved until "Save" is pressed; the X throws the changes away.
 */
@Composable
fun EditProfileScreen(profile: ProfileState, dark: Boolean = true, onClose: () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val focus = LocalFocusManager.current
        val fg = if (dark) Color.White else Color(0xFF101114)

        var name by remember { mutableStateOf(profile.name) }
        var username by remember { mutableStateOf(profile.username) }
        var bio by remember { mutableStateOf(profile.bio) }
        var banner by remember { mutableStateOf(profile.banner) }
        var avatar by remember { mutableStateOf(profile.avatar) }

        var showErrors by remember { mutableStateOf(false) }
        var saving by remember { mutableStateOf(false) }
        var saved by remember { mutableStateOf(false) }
        var usernameTaken by remember { mutableStateOf(false) }
        var crop by remember { mutableStateOf<CropRequest?>(null) }
        var pickTarget by remember { mutableStateOf<CropTarget?>(null) }

        val nameOk = isValidName(name)
        val userOk = isValidUsername(username)
        val loadError = stringResource(R.string.error_image_load)
        val gifTooBig = stringResource(R.string.error_gif_too_big)
        val saveError = stringResource(R.string.error_save)
        val bannerUploadError = stringResource(R.string.error_banner_upload)

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val target = pickTarget
            if (uri != null && target != null) {
                scope.launch {
                    // the banner can be a GIF (max 16 MB): it is kept as it is, so it stays animated
                    if (target == CropTarget.Banner) {
                        when (val g = pickGif(context, uri)) {
                            is GifPick.Ok -> {
                                val first = decodeGifFirstFrame(g.file)
                                if (first != null) {
                                    crop = CropRequest(target, first, null, g.file)
                                } else {
                                    g.file.delete()
                                    Toast.makeText(context, loadError, Toast.LENGTH_SHORT).show()
                                }
                                return@launch
                            }
                            GifPick.TooBig -> {
                                Toast.makeText(context, gifTooBig, Toast.LENGTH_LONG).show()
                                return@launch
                            }
                            GifPick.Failed -> {
                                Toast.makeText(context, loadError, Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            GifPick.NotGif -> Unit
                        }
                    }
                    val bmp = decodePickedImage(context, uri)
                    if (bmp != null) crop = CropRequest(target, bmp, null)
                    else Toast.makeText(context, loadError, Toast.LENGTH_SHORT).show()
                }
            }
        }

        fun pick(target: CropTarget) {
            pickTarget = target
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        fun trySave() {
            showErrors = true
            if (!nameOk || !userOk || usernameTaken || saving) return
            saving = true
            focus.clearFocus()
            scope.launch {
                try {
                    profile.save(name.trim(), username, bio.trim(), banner, avatar)
                    // success animation (the check turns green) before the page closes
                    saved = true
                    delay(600)
                    onClose()
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    saving = false
                    saved = false
                    if (e.isUsernameTaken()) usernameTaken = true
                    else if (e.isBannerUpload()) Toast.makeText(context, bannerUploadError, Toast.LENGTH_LONG).show()
                    else Toast.makeText(context, saveError, Toast.LENGTH_LONG).show()
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            LiquidScene(dark, banner.cropped) { bd ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // ---- top bar: close, title, save
                    Emerge(delayMs = 40, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            GlassSurface(
                                backdrop = bd,
                                container = if (dark) Color(0xFF121212).copy(alpha = 0.40f) else Color.White.copy(alpha = 0.50f),
                                modifier = Modifier.size(48.dp),
                                onClick = onClose
                            ) { GlyphIcon(R.drawable.ic_close, fg, 22.dp) }
                            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                BasicText(
                                    stringResource(R.string.edit_account_title),
                                    style = TextStyle(color = fg, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                                )
                                BasicText(
                                    stringResource(R.string.edit_account_subtitle),
                                    style = TextStyle(color = fg.copy(alpha = 0.6f), fontSize = 12.sp)
                                )
                            }
                            SaveButton(
                                backdrop = bd,
                                enabled = !saving && (!showErrors || (nameOk && userOk && !usernameTaken)),
                                saving = saving,
                                done = saved,
                                onClick = ::trySave
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ---- banner card + photo on its edge
                    Emerge(delayMs = 100, modifier = Modifier.fillMaxWidth()) {
                        BannerAndAvatar(
                            bd = bd,
                            dark = dark,
                            fg = fg,
                            banner = banner,
                            avatar = avatar,
                            onChangeBanner = { pick(CropTarget.Banner) },
                            onMoveBanner = {
                                banner.source?.let { crop = CropRequest(CropTarget.Banner, it, banner.tf, banner.gif) }
                            },
                            onRemoveBanner = { banner = ImageSlot.Empty },
                            onChangeAvatar = { pick(CropTarget.Avatar) },
                            onMoveAvatar = {
                                avatar.source?.let { crop = CropRequest(CropTarget.Avatar, it, avatar.tf) }
                            },
                            onRemoveAvatar = { avatar = ImageSlot.Empty }
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // ---- fields
                    Emerge(delayMs = 170, modifier = Modifier.fillMaxWidth()) {
                        LiquidField(
                            icon = R.drawable.ic_tab_account,
                            tint = AccentOrange,
                            label = stringResource(R.string.field_name),
                            value = name,
                            maxLen = NAME_MAX,
                            backdrop = bd,
                            dark = dark,
                            onValueChange = { name = it.replace("\n", "").take(NAME_MAX) },
                            error = if (showErrors && !nameOk) stringResource(R.string.error_name_empty) else null,
                            keyboardType = KeyboardType.Text
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Emerge(delayMs = 220, modifier = Modifier.fillMaxWidth()) {
                        LiquidField(
                            icon = R.drawable.ic_at,
                            tint = AccentViolet,
                            label = stringResource(R.string.field_username),
                            value = username,
                            maxLen = USERNAME_MAX,
                            backdrop = bd,
                            dark = dark,
                            onValueChange = { v ->
                                username = v.filter { it.isUsernameChar() }.take(USERNAME_MAX)
                                usernameTaken = false
                            },
                            error = when {
                                usernameTaken -> stringResource(R.string.error_username_taken)
                                showErrors && !userOk -> stringResource(R.string.error_username_short)
                                else -> null
                            },
                            countBad = username.length < USERNAME_MIN,
                            keyboardType = KeyboardType.Ascii
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Emerge(delayMs = 270, modifier = Modifier.fillMaxWidth()) {
                        LiquidField(
                            icon = R.drawable.ic_chat,
                            tint = AccentMint,
                            label = stringResource(R.string.field_bio),
                            value = bio,
                            maxLen = BIO_MAX,
                            backdrop = bd,
                            dark = dark,
                            hint = stringResource(R.string.bio_hint),
                            singleLine = false,
                            minLines = 2,
                            onValueChange = { bio = it.take(BIO_MAX) },
                            keyboardType = KeyboardType.Text
                        )
                    }
                    Spacer(Modifier.height(28.dp))
                }
            }

            // ---- "move the picture" page on top
            crop?.let { req ->
                FadeScaleIn {
                    val isBanner = req.target == CropTarget.Banner
                    ImageCropScreen(
                        title = stringResource(if (isBanner) R.string.move_banner_title else R.string.move_avatar_title),
                        hint = stringResource(if (isBanner) R.string.move_banner_hint else R.string.move_avatar_hint),
                        source = req.source,
                        circle = !isBanner,
                        aspect = BANNER_ASPECT,
                        initial = req.tf,
                        onCancel = { crop = null },
                        onDone = { cropped, tf ->
                            val slot = ImageSlot(cropped, req.source, tf, if (isBanner) req.gif else null)
                            if (isBanner) banner = slot else avatar = slot
                            crop = null
                        }
                    )
                }
            }
        }
    }
}

// =====================================================================
// Pieces
// =====================================================================

@Composable
private fun FadeScaleIn(content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(260, easing = SETTLE)) }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = a.value
                val k = 0.96f + 0.04f * a.value
                scaleX = k
                scaleY = k
            }
    ) { content() }
}

/** Liquid glass card: blur + lens (edge refraction) + a shiny border. */
private fun Modifier.liquidCard(
    backdrop: Backdrop,
    shape: Shape,
    dark: Boolean,
    borderWidth: Dp = 0.9.dp,
    borderColor: Color? = null
): Modifier {
    val brush: Brush = if (borderColor != null) SolidColor(borderColor)
    else Brush.verticalGradient(listOf(Color.White.copy(0.55f), Color.White.copy(0.08f)))
    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(14f.dp.toPx())
                lens(10f.dp.toPx(), 20f.dp.toPx())
            },
            onDrawSurface = { drawRect(if (dark) Color.Black.copy(0.26f) else Color.White.copy(0.42f)) }
        )
        .border(borderWidth, brush, shape)
}

/** Page background the glass refracts: app gradient + the banner melting at the top + slow color blobs. */
@Composable
private fun LiquidScene(dark: Boolean, banner: Bitmap?, content: @Composable (Backdrop) -> Unit) {
    val bd = rememberLayerBackdrop()
    val transition = rememberInfiniteTransition(label = "scene")
    val p by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "blobs"
    )
    val base = if (dark) Color(0xFF07111C) else Color(0xFFEEF1F8)
    val blobAlpha = if (dark) 0.55f else 0.38f
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.matchParentSize().layerBackdrop(bd).background(base)) {
            if (banner != null) {
                val img = remember(banner) { banner.asImageBitmap() }
                Image(
                    bitmap = img,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(360.dp)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = 0.85f }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(0f to Color.Black, 0.5f to Color.Black, 1f to Color.Transparent),
                                blendMode = BlendMode.DstIn
                            )
                        }
                )
            }
            Canvas(Modifier.matchParentSize()) {
                val w = size.width
                val h = size.height
                fun blob(c: Color, cx: Float, cy: Float, r: Float) {
                    drawCircle(
                        Brush.radialGradient(listOf(c.copy(alpha = blobAlpha), Color.Transparent), center = Offset(cx, cy), radius = r),
                        r, Offset(cx, cy)
                    )
                }
                blob(AccentOrange, w * (0.15f + 0.5f * p), h * (0.30f + 0.10f * p), w * 0.65f)
                blob(AccentViolet, w * (0.85f - 0.45f * p), h * (0.58f - 0.10f * p), w * 0.60f)
                blob(AccentMint, w * (0.30f + 0.30f * p), h * (0.88f - 0.15f * p), w * 0.65f)
            }
        }
        content(bd)
    }
}

/** The orange "Save" pill. While saving it shows "...", when done the check turns green. */
@Composable
private fun SaveButton(backdrop: Backdrop, enabled: Boolean, saving: Boolean, done: Boolean, onClick: () -> Unit) {
    val container by animateColorAsState(
        if (done) Color(0xFF30D158).copy(alpha = 0.85f) else Color(0xFFCC6A00).copy(alpha = 0.85f),
        tween(250), label = "saveColor"
    )
    GlassSurface(
        backdrop = backdrop,
        container = container,
        modifier = Modifier
            .height(48.dp)
            .graphicsLayer { alpha = if (enabled || saving) 1f else 0.55f },
        onClick = { if (enabled && !saving) onClick() }
    ) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                if (saving && !done) "..." else stringResource(R.string.save),
                style = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.width(8.dp))
            GlyphIcon(R.drawable.ic_check, Color.White, 22.dp)
        }
    }
}

@Composable
private fun BannerAndAvatar(
    bd: Backdrop,
    dark: Boolean,
    fg: Color,
    banner: ImageSlot,
    avatar: ImageSlot,
    onChangeBanner: () -> Unit,
    onMoveBanner: () -> Unit,
    onRemoveBanner: () -> Unit,
    onChangeAvatar: () -> Unit,
    onMoveAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit
) {
    val bannerShape = RoundedCornerShape(32.dp)
    val glass = Color(0xFF121212).copy(alpha = 0.40f)

    Box(Modifier.fillMaxWidth()) {
        Column {
            // banner card
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(BANNER_ASPECT)
                    .liquidCard(bd, bannerShape, dark)
                    .clip(bannerShape)
                    .clickable(onClick = onChangeBanner),
                contentAlignment = Alignment.Center
            ) {
                if (banner.cropped != null) {
                    BannerPicture(slot = banner, modifier = Modifier.fillMaxSize())
                    Box(
                        Modifier.matchParentSize().background(
                            Brush.verticalGradient(0f to Color.Transparent, 0.6f to Color.Transparent, 1f to Color.Black.copy(0.35f))
                        )
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        GlyphIcon(R.drawable.ic_image, fg.copy(alpha = 0.7f), 34.dp)
                        Spacer(Modifier.height(6.dp))
                        BasicText(
                            stringResource(R.string.banner_pick_hint),
                            style = TextStyle(color = fg.copy(alpha = 0.75f), fontSize = 14.sp)
                        )
                    }
                }
                // buttons (the first one is on the right): [delete] [move] [change]
                Row(
                    Modifier.align(Alignment.TopEnd).padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasImage(banner)) {
                        GlassSurface(
                            backdrop = bd,
                            container = DeleteRed.copy(alpha = 0.92f),
                            modifier = Modifier.size(40.dp),
                            onClick = onRemoveBanner
                        ) { GlyphIcon(R.drawable.ic_delete, Color.White, 20.dp) }
                        if (banner.source != null) {
                            GlassSurface(
                                backdrop = bd,
                                container = glass,
                                modifier = Modifier.height(40.dp),
                                onClick = onMoveBanner
                            ) { PillLabel(stringResource(R.string.move), null) }
                        }
                    }
                    GlassSurface(
                        backdrop = bd,
                        container = glass,
                        modifier = Modifier.height(40.dp),
                        onClick = onChangeBanner
                    ) {
                        PillLabel(
                            stringResource(if (hasImage(banner)) R.string.change else R.string.add),
                            R.drawable.ic_image
                        )
                    }
                }
            }
            Spacer(Modifier.height(54.dp))
        }

        // photo, overlapping the bottom of the banner
        ProfileAvatar(
            bitmap = avatar.cropped,
            size = 108.dp,
            dark = true,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 22.dp),
            badgeIcon = R.drawable.ic_edit,
            onBadgeClick = onChangeAvatar
        )

        // move / remove the photo
        if (avatar.cropped != null) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (avatar.source != null) {
                    GlassSurface(
                        backdrop = bd,
                        container = glass,
                        modifier = Modifier.height(40.dp),
                        onClick = onMoveAvatar
                    ) { PillLabel(stringResource(R.string.move), null) }
                }
                GlassSurface(
                    backdrop = bd,
                    container = DeleteRed.copy(alpha = 0.92f),
                    modifier = Modifier.height(40.dp),
                    onClick = onRemoveAvatar
                ) { PillLabel(stringResource(R.string.remove), null) }
            }
        }
    }
}

private fun hasImage(slot: ImageSlot) = slot.cropped != null

@Composable
private fun PillLabel(text: String, icon: Int?) {
    Row(
        Modifier.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            GlyphIcon(icon, Color.White, 18.dp)
            Spacer(Modifier.width(6.dp))
        }
        BasicText(
            text,
            style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        )
    }
}

/** Glass field: colored round icon, small label, text, and the "3/24" counter. It glows when focused. */
@Composable
private fun LiquidField(
    icon: Int,
    tint: Color,
    label: String,
    value: String,
    maxLen: Int,
    backdrop: Backdrop,
    dark: Boolean,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    hint: String? = null,
    error: String? = null,
    countBad: Boolean = false
) {
    val fg = if (dark) Color.White else Color(0xFF101114)
    val shape = RoundedCornerShape(26.dp)
    var focused by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    val glow by animateFloatAsState(if (focused) 1f else 0f, spring(0.8f, 400f), label = "fieldGlow")
    val pop by animateFloatAsState(if (focused) 1.015f else 1f, spring(0.6f, 450f), label = "fieldPop")
    val labelColor by animateColorAsState(if (focused) tint else fg.copy(alpha = 0.6f), tween(200), label = "fieldLabel")

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = pop; scaleY = pop }
                .liquidCard(
                    backdrop, shape, dark,
                    borderWidth = (0.9f + 1.1f * glow).dp,
                    borderColor = if (glow > 0.01f) lerp(Color.White.copy(alpha = 0.30f), tint, glow) else null
                )
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { fr.requestFocus() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(tint.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) { GlyphIcon(icon, tint, 22.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                BasicText(label, style = TextStyle(color = labelColor, fontSize = 12.sp, fontWeight = FontWeight.Medium))
                Spacer(Modifier.height(3.dp))
                Box(Modifier.fillMaxWidth()) {
                    if (value.isEmpty() && hint != null) {
                        BasicText(hint, style = TextStyle(color = fg.copy(alpha = 0.3f), fontSize = 17.sp))
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = singleLine,
                        minLines = minLines,
                        maxLines = if (singleLine) 1 else 4,
                        textStyle = TextStyle(color = fg, fontSize = 17.sp),
                        cursorBrush = SolidColor(tint),
                        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(fr)
                            .onFocusChanged { focused = it.isFocused }
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            BasicText(
                "${value.length}/$maxLen",
                style = TextStyle(
                    color = if (countBad) ERROR_RED else fg.copy(alpha = if (focused) 0.7f else 0.4f),
                    fontSize = 11.sp
                )
            )
        }
        if (error != null) {
            BasicText(
                error,
                modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp),
                style = TextStyle(color = ERROR_RED, fontSize = 12.sp)
            )
        }
    }
}
