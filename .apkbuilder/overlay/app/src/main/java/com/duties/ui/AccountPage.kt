package com.duties.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

private val RING_COLORS = listOf(
    Color(0xFFFF8A00), Color(0xFF2BD9A0), Color(0xFF00C2FF),
    Color(0xFF3D7BFF), Color(0xFF8A5CFF), Color(0xFFFF8A00)
)

/**
 * The Account tab, in the layout of the chat app's profile page: tall banner that melts into the
 * background, round photo on the left, name + @username + bio on the right.
 * No "add story" / "add status", no QR / friends / followers.
 *
 * A tap or a long press on the banner or on the photo calls [onViewImage] (full screen viewer, with save).
 */
@Composable
fun AccountPage(
    profile: ProfileState,
    dark: Boolean,
    cascade: Cascade,
    onEdit: (Rect) -> Unit,
    onSettings: () -> Unit,
    onViewImage: (ViewedImage) -> Unit = {}
) {
    // the layout is written with explicit left / right positions (like the design), so it is LTR
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        val backdrop = rememberLayerBackdrop()
        val textColor = if (dark) Color.White else Color(0xFF101114)
        val subColor = if (dark) Color(0xFFB0B3B8) else Color(0xFF6A6D7A)

        val top = if (dark) Color(0xFF111218) else Color(0xFFF4F5F9)
        val mid = if (dark) Color(0xFF292C37) else Color(0xFFE6E8F0)
        val bottom = if (dark) Color(0xFF474A5B) else Color(0xFFCFD3E2)
        val glassContent = if (dark) Color.White else Color(0xFF101114)
        val glassColor = if (dark) Color(0xFF121212).copy(alpha = 0.40f) else Color(0xFFFAFAFA).copy(alpha = 0.50f)

        var editBounds by remember { mutableStateOf<Rect?>(null) }
        val bannerBmp = profile.banner.cropped
        val avatarBmp = profile.avatar.cropped
        val bannerSlotNow by rememberUpdatedState(profile.banner)
        val avatarNow by rememberUpdatedState(avatarBmp)
        // the GIF stops while the edit page covers this page (nobody sees it, and it would cost battery)
        val covered = LocalPush.current?.request != null

        BoxWithConstraints(Modifier.fillMaxSize()) {
            // all sizes are a fraction of the screen width (design reference: width 1440)
            val u = maxWidth / 1440f
            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val bannerH = u * 620f + statusTop

            // Everything the glass buttons refract: page background + banner.
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0f to top, 0.4f to mid, 1f to bottom))
                )
                FadeIn(delayMs = remember { cascade.take() }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(bannerH)
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                // the banner melts into the page
                                drawRect(
                                    Brush.verticalGradient(
                                        0f to Color.Black,
                                        0.6f to Color.Black,
                                        1f to Color.Transparent
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                    ) {
                        if (bannerBmp != null) {
                            BannerPicture(
                                slot = profile.banner,
                                modifier = Modifier.fillMaxSize(),
                                playing = !covered
                            )
                        } else {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                if (dark) Color(0xFF3A3F55) else Color(0xFFB9C0DC),
                                                if (dark) Color(0xFF1B1D27) else Color(0xFFE2E5F2)
                                            )
                                        )
                                    )
                            )
                        }
                        if (dark) {
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(
                                        0f to Color.Black.copy(0.30f),
                                        0.5f to Color.Black.copy(0.05f),
                                        1f to Color.Black.copy(0.25f)
                                    )
                                )
                            )
                        }
                    }
                }
            }

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .heightIn(min = u * 900f)
                        .padding(bottom = 140.dp) // room for the bottom tab bar
                        .navigationBarsPadding()
                ) {
                    // tap or long press on the banner = open it full screen (only if there is one)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(u * 620f)
                            .align(Alignment.TopStart)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { openBanner(bannerSlotNow, onViewImage) },
                                    onLongPress = { openBanner(bannerSlotNow, onViewImage) }
                                )
                            }
                    )

                    // ---- top buttons (glass): menu on the left, pencil on the right
                    Row(
                        Modifier.fillMaxWidth().padding(start = u * 22f, end = u * 20f, top = u * 20f),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Reveal(delayMs = remember { cascade.take() }, dy = 10.dp) {
                            GlassSurface(
                                backdrop = backdrop,
                                container = glassColor,
                                modifier = Modifier.size(u * 150f),
                                onClick = onSettings
                            ) {
                                GlyphIcon(R.drawable.ic_menu, glassContent, u * 78f)
                            }
                        }
                        Reveal(delayMs = remember { cascade.take() }, dy = 10.dp) {
                            GlassSurface(
                                backdrop = backdrop,
                                container = glassColor,
                                modifier = Modifier
                                    .size(u * 150f)
                                    .onGloballyPositioned { editBounds = it.boundsInRoot() },
                                onClick = { editBounds?.let(onEdit) }
                            ) {
                                GlyphIcon(R.drawable.ic_edit, glassContent, u * 78f)
                            }
                        }
                    }

                    // ---- photo on the left (tap / long press = full screen)
                    Reveal(
                        delayMs = remember { cascade.take() },
                        modifier = Modifier.align(Alignment.TopStart).padding(start = u * 47f, top = u * 352f)
                    ) {
                        ProfileAvatar(
                            bitmap = avatarBmp,
                            size = u * 375f,
                            dark = dark,
                            showBadge = false,
                            modifier = Modifier
                                .clip(CircleShape)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { avatarNow?.let { onViewImage(ViewedImage(it)) } },
                                        onLongPress = { avatarNow?.let { onViewImage(ViewedImage(it)) } }
                                    )
                                }
                        )
                    }

                    // ---- name + @username on the right
                    Reveal(
                        delayMs = remember { cascade.take() },
                        modifier = Modifier.align(Alignment.TopEnd).padding(end = u * 52f, top = u * 445f)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.widthIn(max = u * 880f)
                        ) {
                            BasicText(
                                profile.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    color = textColor,
                                    fontSize = (u.value * 86f).sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End
                                )
                            )
                            if (profile.username.isNotBlank()) {
                                Spacer(Modifier.height(u * 8f))
                                BasicText(
                                    "@" + profile.username,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = TextStyle(
                                        color = subColor,
                                        fontSize = (u.value * 44f).sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }

                    // ---- bio
                    if (profile.bio.isNotBlank()) {
                        Reveal(
                            delayMs = remember { cascade.take() },
                            modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth()
                                .padding(top = u * 705f, end = u * 35f, start = u * 35f)
                        ) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                                BasicText(
                                    profile.bio,
                                    style = TextStyle(
                                        color = textColor,
                                        fontSize = (u.value * 52f).sp,
                                        textAlign = TextAlign.End
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Round photo with the colorful ring and (optionally) the orange badge. Empty = person icon. */
@Composable
fun ProfileAvatar(
    bitmap: android.graphics.Bitmap?,
    size: androidx.compose.ui.unit.Dp,
    dark: Boolean,
    modifier: Modifier = Modifier,
    badgeIcon: Int = R.drawable.ic_plus,
    onBadgeClick: (() -> Unit)? = null,
    showBadge: Boolean = true
) {
    Box(modifier.size(size)) {
        val ringW = size * 0.045f
        val gap = size * 0.03f
        Canvas(Modifier.fillMaxSize()) {
            val stroke = ringW.toPx()
            drawCircle(
                brush = Brush.sweepGradient(RING_COLORS, center = Offset(this.size.width / 2f, this.size.height / 2f)),
                radius = (this.size.minDimension - stroke) / 2f,
                style = Stroke(stroke)
            )
        }
        Box(
            Modifier
                .padding(ringW + gap)
                .fillMaxSize()
                .clip(CircleShape)
                .background(if (dark) Color(0xFF1A1B22) else Color(0xFFDDE0EC)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                val img = remember(bitmap) { bitmap.asImageBitmap() }
                Image(
                    bitmap = img,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                GlyphIcon(
                    R.drawable.ic_tab_account,
                    if (dark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.3f),
                    size * 0.5f
                )
            }
        }
        if (showBadge) {
            // orange badge, bottom corner
            val badgeMod = Modifier
                .align(Alignment.BottomEnd)
                .size(size * 0.3f)
                .let { if (onBadgeClick != null) it.bounceClick(onClick = onBadgeClick) else it }
                .clip(CircleShape)
                .background(Color(0xFFCC6A00))
                .border(2.5.dp, Color.White, CircleShape)
            Box(badgeMod, contentAlignment = Alignment.Center) {
                GlyphIcon(badgeIcon, Color.White, size * 0.16f)
            }
        }
    }
}

/** Opens the banner full screen (a GIF banner keeps playing there). */
private fun openBanner(slot: ImageSlot, onViewImage: (ViewedImage) -> Unit) {
    val bmp = slot.cropped ?: return
    onViewImage(ViewedImage(bmp, slot.gif, slot.tf))
}
