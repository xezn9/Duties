package com.duties.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duties.R
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

private val RING_COLORS = listOf(
    Color(0xFFFF8A00), Color(0xFF2BD9A0), Color(0xFF00C2FF),
    Color(0xFF3D7BFF), Color(0xFF8A5CFF), Color(0xFFFF8A00)
)

/**
 * The Account tab: banner, round photo with the colorful ring, name, @username and bio.
 * Top buttons: settings (empty for now) and edit profile (the edit page grows out of it).
 * The screen is drawn right-to-left like the design.
 */
@Composable
fun AccountPage(
    profile: ProfileState,
    dark: Boolean,
    cascade: Cascade,
    onEdit: (Rect) -> Unit,
    onSettings: () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val backdrop = rememberLayerBackdrop()
        val textColor = if (dark) Color.White else Color(0xFF101114)
        val subColor = if (dark) Color(0xFF9A9CA8) else Color(0xFF6A6D7A)

        val top = if (dark) Color(0xFF111218) else Color(0xFFF4F5F9)
        val mid = if (dark) Color(0xFF292C37) else Color(0xFFE6E8F0)
        val bottom = if (dark) Color(0xFF474A5B) else Color(0xFFCFD3E2)
        val glassContent = if (dark) Color.White else Color(0xFF101114)
        val glassColor = if (dark) Color(0xFF121212).copy(alpha = 0.40f) else Color(0xFFFAFAFA).copy(alpha = 0.50f)

        var editBounds by remember { mutableStateOf<Rect?>(null) }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val bannerH = maxWidth / BANNER_ASPECT
            val nameMaxWidth = maxWidth - 36.dp - 106.dp - 14.dp

            // Everything the glass buttons refract: page background + banner.
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(0f to top, 0.4f to mid, 1f to bottom))
                )
                FadeIn(delayMs = remember { cascade.take() }) {
                    Box(Modifier.fillMaxWidth().height(bannerH)) {
                        val bmp = profile.banner.cropped
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
                        // soft edge into the page
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(bannerH * 0.3f)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, lerp(top, mid, 0.5f))
                                    )
                                )
                        )
                    }
                }
            }

            // ---- top buttons (glass)
            Box(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Reveal(delayMs = remember { cascade.take() }, modifier = Modifier.align(Alignment.TopStart), dy = 10.dp) {
                    GlassSurface(
                        backdrop = backdrop,
                        container = glassColor,
                        modifier = Modifier
                            .size(46.dp)
                            .onGloballyPositioned { editBounds = it.boundsInRoot() },
                        onClick = { editBounds?.let(onEdit) }
                    ) {
                        GlyphIcon(R.drawable.ic_edit, glassContent, 22.dp)
                    }
                }
                Reveal(delayMs = remember { cascade.take() }, modifier = Modifier.align(Alignment.TopEnd), dy = 10.dp) {
                    GlassSurface(
                        backdrop = backdrop,
                        container = glassColor,
                        modifier = Modifier.size(46.dp),
                        onClick = onSettings
                    ) {
                        GlyphIcon(R.drawable.ic_menu, glassContent, 22.dp)
                    }
                }
            }

            // ---- photo, name, username, bio
            Column(
                Modifier
                    .padding(top = bannerH - 16.dp)
                    .padding(horizontal = 18.dp)
                    .fillMaxWidth()
            ) {
                Box(Modifier.fillMaxWidth()) {
                    Reveal(
                        delayMs = remember { cascade.take() },
                        modifier = Modifier.align(Alignment.TopStart).padding(top = 40.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier.widthIn(max = nameMaxWidth)
                        ) {
                            BasicText(
                                profile.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(color = textColor, fontSize = 32.sp, fontWeight = FontWeight.Medium)
                            )
                            Spacer(Modifier.height(6.dp))
                            BasicText(
                                "@" + profile.username,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(color = subColor, fontSize = 19.sp)
                            )
                        }
                    }

                    Reveal(
                        delayMs = remember { cascade.take() },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Box {
                            ProfileAvatar(
                                bitmap = profile.avatar.cropped,
                                size = 106.dp,
                                dark = dark,
                                modifier = Modifier.padding(top = 28.dp)
                            )
                            GlassSurface(
                                backdrop = backdrop,
                                container = glassColor,
                                modifier = Modifier.align(Alignment.TopCenter).height(34.dp)
                            ) {
                                BasicText(
                                    stringResource(R.string.add_status),
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    style = TextStyle(
                                        color = glassContent,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            }
                        }
                    }
                }

                if (profile.bio.isNotBlank()) {
                    Spacer(Modifier.height(18.dp))
                    Reveal(delayMs = remember { cascade.take() }) {
                        BasicText(
                            profile.bio,
                            style = TextStyle(color = textColor.copy(alpha = 0.85f), fontSize = 17.sp, lineHeight = 24.sp)
                        )
                    }
                }
            }
        }
    }
}

/** Round photo with the colorful ring and the orange "+" badge. Empty = person icon. */
@Composable
fun ProfileAvatar(
    bitmap: android.graphics.Bitmap?,
    size: androidx.compose.ui.unit.Dp,
    dark: Boolean,
    modifier: Modifier = Modifier,
    badgeIcon: Int = R.drawable.ic_plus,
    onBadgeClick: (() -> Unit)? = null
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
