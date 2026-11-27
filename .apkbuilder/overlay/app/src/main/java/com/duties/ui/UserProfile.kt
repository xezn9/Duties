package com.duties.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duties.R
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/*
 * UserProfile.kt — the profile of a person, opened by tapping his photo beside a message in a chat.
 *
 * It uses the SAME animation as the chat (ChatMorph): a circle that starts on the tapped photo, grows until it covers
 * the screen (light blur while it moves, thin glass rim), the photo flies up and fades into the page, and on the way
 * back it shrinks into the photo again. The back button (or the system back) closes only this page, not the chat.
 *
 * The data: users/{uid} on Firestore (name, username, bio), profilePics/{uid} in the Realtime Database (full photo and
 * banner, written by Profile.kt) and bannerGifs/{uid} (a GIF banner) - all readable by every signed-in user.
 * The look is the one of the Account tab (AccountPage.kt), without the edit / menu buttons.
 */

/** The person that was tapped, and where his photo is (root coordinates). */
internal class ProfileTarget(val uid: String, val name: String, val photo: Bitmap?, val from: Rect)

private class UserInfoView(val name: String, val username: String, val bio: String, val photo: Bitmap?)

private val PHOTO_START = 34.dp   // the photo beside a message (MSG_AVATAR in ChatScreen.kt)

@Composable
internal fun UserProfileMorph(
    target: ProfileTarget?,
    open: Boolean,
    dark: Boolean,
    onClose: () -> Unit,
    onDismissed: () -> Unit
) {
    val p = remember { Animatable(0f) }
    var shown by remember { mutableStateOf<ProfileTarget?>(null) }
    val density = LocalDensity.current
    // the photo / banner of this person shown full screen (null = closed)
    var viewImage by remember { mutableStateOf<ViewedImage?>(null) }

    BackHandler(enabled = open && target != null) { onClose() }

    LaunchedEffect(target, open) {
        if (open && target != null) {
            shown = target
            p.snapTo(0f)
            p.animateTo(1f, tween(OPEN_MS, easing = OPEN_EASE))
        } else if (shown != null) {
            viewImage = null
            p.animateTo(0f, tween(CLOSE_MS, easing = CLOSE_EASE))
            shown = null
            onDismissed()
        }
    }

    val t = shown ?: return
    val screenW = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val screenH = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    // the circle starts from the tapped photo itself (34dp beside a message, other sizes elsewhere)
    val start = t.from.width.takeIf { it > 8f } ?: with(density) { PHOTO_START.toPx() }
    val startDp = with(density) { start.toDp() }
    val c = Offset(t.from.center.x, t.from.center.y)

    // the page is written with explicit left / right positions (like the Account tab): left-to-right
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val k = p.value
                        val rEnd = coverRadius(c, size.width, size.height)
                        val r = lerpF(start / 2f, rEnd, k)
                        clip = true
                        shape = RoundedRectClip(Rect(c, r), r)
                        val blur = BLUR_MAX.toPx() * kotlin.math.sin(Math.PI * k).toFloat()
                        renderEffect = if (blur > 0.5f && k < 0.995f && android.os.Build.VERSION.SDK_INT >= 31)
                            BlurEffect(blur, blur, TileMode.Clamp) else null
                    }
                    .pointerInput(Unit) { detectTapGestures { } }          // covers what is below
            ) {
                ProfileContent(
                    target = t,
                    dark = dark,
                    reveal = { seg(p.value, 0.35f, 0.85f) },
                    onBack = onClose,
                    onViewImage = { viewImage = it }
                )

                // the photo itself, scaled up from its place until it fills the circle; it fades into the page
                Box(
                    Modifier
                        .absoluteOffset { IntOffset((c.x - start / 2f).roundToInt(), (c.y - start / 2f).roundToInt()) }
                        .size(startDp)
                        .graphicsLayer {
                            val rEnd = coverRadius(c, screenW, screenH)
                            val r = lerpF(start / 2f, rEnd, p.value)
                            val sc = r / (start / 2f)
                            scaleX = sc
                            scaleY = sc
                            alpha = 1f - seg(p.value, 0.3f, 0.8f)
                        }
                ) { Avatar(t.photo, t.name, startDp) }
            }

            // thin glass rim of the circle (fades away when the page fills the screen)
            Canvas(Modifier.fillMaxSize()) {
                val k = p.value
                val a = seg(k, 0f, 0.12f) * (1f - seg(k, 0.5f, 0.9f))
                if (a <= 0f) return@Canvas
                val r = lerpF(start / 2f, coverRadius(c, size.width, size.height), k)
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

            // the photo / banner full screen (zoom + save), above the whole profile page
            ImageViewerHost(viewImage) { viewImage = null }
        }
    }
}

@Composable
private fun ProfileContent(
    target: ProfileTarget,
    dark: Boolean,
    reveal: () -> Float,
    onBack: () -> Unit,
    onViewImage: (ViewedImage) -> Unit
) {
    // name / username / bio / photo / banner come from UserProfiles: already in memory (loaded when the chat showed,
    // or saved on the phone), so they are in the very first frame. The server's newer data replaces them silently.
    val context = LocalContext.current.applicationContext
    val data = UserProfiles.map[target.uid]
    val info = UserInfoView(
        name = data?.name?.ifBlank { null } ?: target.name,
        username = data?.username ?: "",
        bio = data?.bio ?: "",
        photo = data?.avatar ?: target.photo
    )
    val banner = data?.banner ?: ImageSlot.Empty
    LaunchedEffect(target.uid) { UserProfiles.request(context, target.uid, force = true) }
    DisposableEffect(target.uid) {
        UserProfiles.pinned = target.uid
        onDispose { if (UserProfiles.pinned == target.uid) UserProfiles.pinned = null }
    }

    val photoNow by rememberUpdatedState(info.photo)
    val bannerNow by rememberUpdatedState(banner)

    val backdrop = rememberLayerBackdrop()
    val textColor = if (dark) Color.White else Color(0xFF101114)
    val subColor = if (dark) Color(0xFFB0B3B8) else Color(0xFF6A6D7A)
    val top = if (dark) Color(0xFF111218) else Color(0xFFF4F5F9)
    val mid = if (dark) Color(0xFF292C37) else Color(0xFFE6E8F0)
    val bottom = if (dark) Color(0xFF474A5B) else Color(0xFFCFD3E2)
    val glassContent = if (dark) Color.White else Color(0xFF101114)
    val glassColor = if (dark) Color(0xFF121212).copy(alpha = 0.40f) else Color(0xFFFAFAFA).copy(alpha = 0.50f)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val u = maxWidth / 1440f      // all sizes are a fraction of the screen width (same as the Account tab)
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bannerH = u * 620f + statusTop

        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to top, 0.4f to mid, 1f to bottom)))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(bannerH)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        alpha = reveal()
                    }
                    .drawWithContent {
                        drawContent()
                        // the banner melts into the page
                        drawRect(
                            Brush.verticalGradient(0f to Color.Black, 0.6f to Color.Black, 1f to Color.Transparent),
                            blendMode = BlendMode.DstIn
                        )
                    }
            ) {
                if (banner.cropped != null) {
                    BannerPicture(slot = banner, modifier = Modifier.fillMaxSize())
                } else {
                    Box(
                        Modifier.fillMaxSize().background(
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
                                0f to Color.Black.copy(0.30f), 0.5f to Color.Black.copy(0.05f), 1f to Color.Black.copy(0.25f)
                            )
                        )
                    )
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .graphicsLayer { alpha = reveal() }
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(bottom = 40.dp)
            ) {
                // tap or long press on the banner = open it full screen (only if there is one)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(u * 620f)
                        .align(Alignment.TopStart)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { openUserBanner(bannerNow, onViewImage) },
                                onLongPress = { openUserBanner(bannerNow, onViewImage) }
                            )
                        }
                )

                // ---- back button (right end: the app is right-to-left, like the back button of the chat)
                Row(
                    Modifier.fillMaxWidth().padding(start = u * 22f, end = u * 20f, top = u * 20f),
                    horizontalArrangement = Arrangement.End
                ) {
                    GlassSurface(
                        backdrop = backdrop,
                        container = glassColor,
                        modifier = Modifier.size(u * 150f),
                        onClick = onBack
                    ) { GlyphIcon(R.drawable.ic_back, glassContent, u * 78f) }
                }

                // ---- photo on the left
                ProfileAvatar(
                    bitmap = info.photo,
                    size = u * 375f,
                    dark = dark,
                    showBadge = false,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = u * 47f, top = u * 352f)
                        .clip(CircleShape)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { photoNow?.let { onViewImage(ViewedImage(it)) } },
                                onLongPress = { photoNow?.let { onViewImage(ViewedImage(it)) } }
                            )
                        }
                )

                // ---- name + @username on the right
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = u * 52f, top = u * 445f)
                        .widthIn(max = u * 880f)
                ) {
                    BasicText(
                        info.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            color = textColor,
                            fontSize = (u.value * 86f).sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End
                        )
                    )
                    if (info.username.isNotBlank()) {
                        Spacer(Modifier.height(u * 8f))
                        BasicText(
                            "@" + info.username,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = subColor, fontSize = (u.value * 44f).sp, fontWeight = FontWeight.Medium)
                        )
                    }
                }

                // ---- bio
                if (info.bio.isNotBlank()) {
                    Column(
                        Modifier
                            .align(Alignment.TopEnd)
                            .fillMaxWidth()
                            .padding(top = u * 705f, end = u * 35f, start = u * 35f),
                        horizontalAlignment = Alignment.End
                    ) {
                        BasicText(
                            info.bio,
                            style = TextStyle(color = textColor, fontSize = (u.value * 52f).sp, textAlign = TextAlign.End)
                        )
                    }
                }
            }
        }
    }
}

/** Opens the banner full screen (a GIF banner keeps playing there). */
private fun openUserBanner(slot: ImageSlot, onViewImage: (ViewedImage) -> Unit) {
    val bmp = slot.cropped ?: return
    onViewImage(ViewedImage(bmp, slot.gif, slot.tf))
}
