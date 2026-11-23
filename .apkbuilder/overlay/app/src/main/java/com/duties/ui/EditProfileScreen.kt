package com.duties.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duties.R
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.launch

private enum class CropTarget { Banner, Avatar }

private class CropRequest(val target: CropTarget, val source: Bitmap, val tf: CropTransform?)

private val ERROR_RED = Color(0xFFFF6B6B)
private val LABEL_GREY = Color(0xFFB4B8C8)

/**
 * "Edit account" page. It is opened with PushStack, so it grows out of the pencil button and its
 * parts come out of that point one after another (Emerge).
 * Nothing is saved until the orange "Save" is pressed; the X throws the changes away.
 */
@Composable
fun EditProfileScreen(profile: ProfileState, onClose: () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val focus = LocalFocusManager.current

        var name by remember { mutableStateOf(profile.name) }
        var username by remember { mutableStateOf(profile.username) }
        var bio by remember { mutableStateOf(profile.bio) }
        var banner by remember { mutableStateOf(profile.banner) }
        var avatar by remember { mutableStateOf(profile.avatar) }

        var showErrors by remember { mutableStateOf(false) }
        var saving by remember { mutableStateOf(false) }
        var usernameTaken by remember { mutableStateOf(false) }
        var crop by remember { mutableStateOf<CropRequest?>(null) }
        var pickTarget by remember { mutableStateOf<CropTarget?>(null) }

        val nameOk = isValidName(name)
        val userOk = isValidUsername(username)
        val loadError = stringResource(R.string.error_image_load)
        val saveError = stringResource(R.string.error_save)

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            val target = pickTarget
            if (uri != null && target != null) {
                scope.launch {
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
                    onClose()
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    saving = false
                    if (e.isUsernameTaken()) usernameTaken = true
                    else Toast.makeText(context, saveError, Toast.LENGTH_LONG).show()
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            EditBackground()

            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(bottom = 28.dp)
            ) {
                // ---- header: close, title, save
                Emerge(delayMs = 40, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DarkCircleButton(R.drawable.ic_close, size = 58.dp, onClick = onClose)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText(
                                stringResource(R.string.edit_account_title),
                                style = TextStyle(
                                    color = Color.White,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Right
                                )
                            )
                            BasicText(
                                stringResource(R.string.edit_account_subtitle),
                                style = TextStyle(
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 18.sp,
                                    textAlign = TextAlign.Right
                                )
                            )
                        }
                        OrangePill(
                            text = stringResource(R.string.save),
                            icon = R.drawable.ic_check,
                            enabled = !saving && (!showErrors || (nameOk && userOk && !usernameTaken)),
                            onClick = ::trySave
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // ---- banner card + photo
                Emerge(delayMs = 100, modifier = Modifier.fillMaxWidth()) {
                    BannerAndAvatar(
                        banner = banner,
                        avatar = avatar,
                        onChangeBanner = { pick(CropTarget.Banner) },
                        onMoveBanner = {
                            banner.source?.let { crop = CropRequest(CropTarget.Banner, it, banner.tf) }
                        },
                        onRemoveBanner = { banner = ImageSlot.Empty },
                        onChangeAvatar = { pick(CropTarget.Avatar) },
                        onMoveAvatar = {
                            avatar.source?.let { crop = CropRequest(CropTarget.Avatar, it, avatar.tf) }
                        },
                        onRemoveAvatar = { avatar = ImageSlot.Empty }
                    )
                }

                Spacer(Modifier.height(10.dp))

                // ---- fields
                Emerge(delayMs = 170, modifier = Modifier.fillMaxWidth()) {
                    ProfileField(
                        icon = R.drawable.ic_tab_account,
                        accent = Color(0xFFFF8A1F),
                        tint = listOf(Color(0xFF6B3FA0), Color(0xFF3B3A9E)),
                        label = stringResource(R.string.field_name),
                        value = name,
                        max = NAME_MAX,
                        onValueChange = { name = it.replace("\n", "").take(NAME_MAX) },
                        error = if (showErrors && !nameOk) stringResource(R.string.error_name_empty) else null,
                        keyboardType = KeyboardType.Text
                    )
                }
                Spacer(Modifier.height(14.dp))
                Emerge(delayMs = 220, modifier = Modifier.fillMaxWidth()) {
                    ProfileField(
                        icon = R.drawable.ic_at,
                        accent = Color(0xFF9B8AFF),
                        tint = listOf(Color(0xFF2E45B8), Color(0xFF1D3B8A)),
                        label = stringResource(R.string.field_username),
                        value = username,
                        max = USERNAME_MAX,
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
                Spacer(Modifier.height(14.dp))
                Emerge(delayMs = 270, modifier = Modifier.fillMaxWidth()) {
                    ProfileField(
                        icon = R.drawable.ic_chat,
                        accent = Color(0xFF19D3A2),
                        tint = listOf(Color(0xFF0B6E8E), Color(0xFF14527F)),
                        label = stringResource(R.string.field_bio),
                        value = bio,
                        max = BIO_MAX,
                        hint = stringResource(R.string.bio_hint),
                        singleLine = false,
                        minHeight = 104.dp,
                        onValueChange = { bio = it.take(BIO_MAX) },
                        keyboardType = KeyboardType.Text
                    )
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
                            val slot = ImageSlot(cropped, req.source, tf)
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

@Composable
private fun EditBackground() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(Color(0xFF07111C))
        val m = size.maxDimension
        fun blob(x: Float, y: Float, r: Float, c: Color) {
            val center = Offset(size.width * x, size.height * y)
            drawCircle(
                brush = Brush.radialGradient(listOf(c, Color.Transparent), center = center, radius = m * r),
                radius = m * r,
                center = center
            )
        }
        blob(0.00f, 0.22f, 0.55f, Color(0xFFB45309).copy(alpha = 0.85f))
        blob(0.95f, 0.50f, 0.50f, Color(0xFF5B3FD4).copy(alpha = 0.70f))
        blob(0.25f, 0.62f, 0.40f, Color(0xFF1D4ED8).copy(alpha = 0.50f))
        blob(0.30f, 0.92f, 0.50f, Color(0xFF0F8F7A).copy(alpha = 0.85f))
    }
}

@Composable
private fun BannerAndAvatar(
    banner: ImageSlot,
    avatar: ImageSlot,
    onChangeBanner: () -> Unit,
    onMoveBanner: () -> Unit,
    onRemoveBanner: () -> Unit,
    onChangeAvatar: () -> Unit,
    onMoveAvatar: () -> Unit,
    onRemoveAvatar: () -> Unit
) {
    val bannerBackdrop = rememberLayerBackdrop()
    val shape = RoundedCornerShape(32.dp)
    val brown = Color(0xFF7A4A1E).copy(alpha = 0.55f)

    Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        Column {
            // banner card
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(BANNER_ASPECT)
                    .clip(shape)
                    .border(1.dp, Color.White.copy(alpha = 0.28f), shape)
            ) {
                Box(Modifier.fillMaxSize().layerBackdrop(bannerBackdrop)) {
                    val bmp = banner.cropped
                    if (bmp != null) {
                        val img = remember(bmp) { bmp.asImageBitmap() }
                        Image(
                            bitmap = img,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Brush.linearGradient(listOf(Color(0xFF2A2F45), Color(0xFF12141F))))
                        )
                    }
                }
                // buttons, top-left on screen: [change] [move] [delete]
                Row(
                    Modifier.align(Alignment.TopEnd).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasImage(banner)) {
                        Box(
                            Modifier
                                .size(46.dp)
                                .bounceClick(onClick = onRemoveBanner)
                                .clip(CircleShape)
                                .background(Color(0xFFD6333F)),
                            contentAlignment = Alignment.Center
                        ) { GlyphIcon(R.drawable.ic_delete, Color.White, 22.dp) }
                        if (banner.source != null) {
                            GlassSurface(
                                backdrop = bannerBackdrop,
                                container = brown,
                                modifier = Modifier.height(46.dp),
                                onClick = onMoveBanner
                            ) { PillLabel(stringResource(R.string.move), null) }
                        }
                    }
                    GlassSurface(
                        backdrop = bannerBackdrop,
                        container = brown,
                        modifier = Modifier.height(46.dp),
                        onClick = onChangeBanner
                    ) { PillLabel(stringResource(R.string.change), R.drawable.ic_image) }
                }
            }
            Spacer(Modifier.height(66.dp))
        }

        // photo, overlapping the bottom of the banner
        ProfileAvatar(
            bitmap = avatar.cropped,
            size = 108.dp,
            dark = true,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 10.dp),
            badgeIcon = R.drawable.ic_edit,
            onBadgeClick = onChangeAvatar
        )

        // remove / move the photo
        if (avatar.cropped != null) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SolidPill(stringResource(R.string.remove), Color(0xFFD6333F), onRemoveAvatar)
                if (avatar.source != null) {
                    SolidPill(stringResource(R.string.move), Color(0xFF3A1D05), onMoveAvatar, outlined = true)
                }
            }
        }
    }
}

private fun hasImage(slot: ImageSlot) = slot.cropped != null

@Composable
private fun PillLabel(text: String, icon: Int?) {
    Row(
        Modifier.padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            GlyphIcon(icon, Color.White, 22.dp)
            Spacer(Modifier.width(8.dp))
        }
        BasicText(
            text,
            style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
private fun SolidPill(text: String, color: Color, onClick: () -> Unit, outlined: Boolean = false) {
    Box(
        Modifier
            .bounceClick(onClick = onClick)
            .height(52.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = if (outlined) 0.85f else 0.95f))
            .then(if (outlined) Modifier.border(1.dp, Color(0xFFB8782E).copy(alpha = 0.6f), CircleShape) else Modifier)
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text,
            style = TextStyle(color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        )
    }
}

/** A glass-like card: round icon (start), label + text box, and the "3/24" counter (end). */
@Composable
private fun ProfileField(
    icon: Int,
    accent: Color,
    tint: List<Color>,
    label: String,
    value: String,
    max: Int,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minHeight: Dp = 80.dp,
    hint: String? = null,
    error: String? = null,
    countBad: Boolean = false
) {
    val shape = RoundedCornerShape(34.dp)
    val focusRequester = remember { FocusRequester() }
    Row(
        modifier
            .padding(horizontal = 18.dp)
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(shape)
            .background(Brush.linearGradient(tint.map { it.copy(alpha = 0.62f) }))
            .border(
                1.dp,
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.38f), Color.White.copy(alpha = 0.10f))),
                shape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { focusRequester.requestFocus() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.28f)),
            contentAlignment = Alignment.Center
        ) { GlyphIcon(icon, accent, 26.dp) }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            BasicText(
                label,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(color = LABEL_GREY, fontSize = 17.sp, textAlign = TextAlign.Right)
            )
            Box(Modifier.fillMaxWidth()) {
                if (value.isEmpty() && hint != null) {
                    BasicText(
                        hint,
                        modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(
                            color = Color.White.copy(alpha = 0.3f),
                            fontSize = 22.sp,
                            textAlign = TextAlign.Right
                        )
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = singleLine,
                    textStyle = TextStyle(color = Color.White, fontSize = 24.sp, textAlign = TextAlign.Right),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                )
            }
            if (error != null) {
                Spacer(Modifier.height(2.dp))
                BasicText(
                    error,
                    modifier = Modifier.fillMaxWidth(),
                    style = TextStyle(color = ERROR_RED, fontSize = 15.sp, textAlign = TextAlign.Right)
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        BasicText(
            "${value.length}/$max",
            style = TextStyle(
                color = if (countBad) ERROR_RED else Color.White.copy(alpha = 0.45f),
                fontSize = 18.sp
            )
        )
    }
}
