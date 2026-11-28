package com.duties.ui

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.derivedStateOf
import com.duties.R
import com.google.firebase.firestore.FirebaseFirestore
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidDialog
import com.kyant.backdrop.catalog.components.LiquidToggle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/*
 * ChannelEdit.kt — the channel page that opens when the name bar at the top of the chat is tapped.
 *
 *   - the OWNER, and every supervisor that was given the "edit" permission, can change the channel's photo (pick +
 *     move/zoom), name, @username and bio, then "Save" (Channels.kt -> ChannelsState.updateChannel). Everybody else sees
 *     the same page, read only: the channel information and the members, nothing can be changed.
 *   - under the fields: the members of the channel. Tap a member's photo = his profile opens (the same profile page
 *     that opens from a photo inside the chat, hosted by ChatMorph).
 *   - a round button on the member rows opens a small panel (see MemberMenu):
 *       owner      : Make supervisor (switch) and, for a supervisor, his permissions (Ban / Kick / Edit, one switch each),
 *                    Kick and Ban for everybody.
 *       supervisor : Kick and / or Ban, only the ones the owner allowed him, and only on ordinary members.
 *     Only the owner and the supervisors can write in the channel.
 *   - under the members: the BANNED people (only for those who may ban) with a button that lifts the ban.
 *
 * LOOK (v2): a cover made from the channel photo, the photo on its edge, name / @username / bio typed right on the page (no field
 * cards), three small chips, the members as two-in-a-row cards (a switch to the banned people when there are some), and a wide
 * Save button that slides up from the bottom only when something was changed. The page opens as a sheet that grows out of
 * the name bar (see ChannelEditMorph).
 *
 * It is drawn inside the chat's sliding layer (see ChatScreen.kt), so it slides away together with the chat.
 */

private val EditOrange = Color(0xFFFF8A1F)
private val EditViolet = Color(0xFF8A5CFF)
private val EditMint = Color(0xFF00D4AA)
private val EditRed = Color(0xFFE5484D)
private val MEMBER_PHOTO = 44.dp

/** The accent of the page: the app color the person chose (orange when none). Everything coloured here uses it. */
private val EditAccent: Color get() = AppAccent.pick(EditOrange)

/** The pieces of the page start rising this long after the page starts opening (the sheet is already growing). */
private const val INTRO_BASE_MS = 150L

private const val EDIT_OPEN_MS = 560
private const val EDIT_CLOSE_MS = 340

// =====================================================================
// Names of the members (users/{uid}); their small photos come from UserPhotos
// =====================================================================

internal class MemberInfo(val name: String, val username: String)

internal object MemberDirectory {
    /** uid -> name + @username. Reading it inside a composable makes that composable update when it arrives. */
    val info = mutableStateMapOf<String, MemberInfo>()

    private val asked = HashSet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun request(uid: String) {
        if (uid.isBlank()) return
        synchronized(asked) { if (!asked.add(uid)) return }
        scope.launch {
            try {
                val doc = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
                val username = doc.getString("username")
                if (doc.exists() && username != null) {
                    info[uid] = MemberInfo(doc.getString("name") ?: username, username)
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (_: Exception) {
                synchronized(asked) { asked.remove(uid) }        // offline: try again next time
            }
        }
    }
}

// =====================================================================
// The page that opens out of the name bar
// =====================================================================

/**
 * Hosts the page. [open] = true shows it, false plays the way back. [onOpenProfile] opens the profile of a member
 * (ChatMorph hosts that page, above this one).
 *
 * THE OPENING: the page is ONE sheet that starts exactly on the name bar ([from]) and grows to the whole screen:
 * first it gets wide (the width runs a little ahead), then it falls down to the bottom while its corners straighten.
 * The chat under it dims. The content is never squashed: it is only revealed by the growing sheet, it fades in and
 * settles from slightly bigger, and then the pieces (photo, numbers, fields, members) rise one after another.
 */
@Composable
internal fun ChannelEditMorph(
    open: Boolean,
    from: Rect,
    channel: Channel,
    channels: ChannelsState,
    dark: Boolean,
    uid: String,
    myName: String,
    myPhoto: Bitmap?,
    onClose: () -> Unit,
    onOpenProfile: (ProfileTarget) -> Unit
) {
    val p = remember { Animatable(0f) }
    var visible by remember { mutableStateOf(false) }

    BackHandler(enabled = open) { onClose() }

    LaunchedEffect(open) {
        if (open) {
            visible = true
            p.snapTo(0f)
            p.animateTo(1f, tween(EDIT_OPEN_MS, easing = CIRCLE_GROW))
        } else if (visible) {
            p.animateTo(0f, tween(EDIT_CLOSE_MS, easing = CLOSE_EASE))
            visible = false
        }
    }
    if (!visible) return

    val base = if (dark) Color.Black else Color(0xFFF4F5F9)

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.5f * p.value)) }       // the chat dims
            .pointerInput(Unit) { detectTapGestures { } }                            // covers the chat below
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val k = p.value
                    val w = size.width
                    val h = size.height
                    // the starting rectangle: the name bar (a bar at the top if its place is not known)
                    val bar = if (from.width > 1f && from.height > 1f) from
                    else Rect(w * 0.18f, 40.dp.toPx(), w * 0.82f, 96.dp.toPx())
                    val kx = FastOutSlowInEasing.transform(seg(k, 0f, 0.8f))        // the width runs a little ahead
                    val r = Rect(
                        lerpF(bar.left, 0f, kx),
                        lerpF(bar.top, 0f, k),
                        lerpF(bar.right, w, kx),
                        lerpF(bar.bottom, h, k)
                    )
                    shape = RoundedRectClip(r, lerpF(bar.height / 2f, 0f, k).coerceAtLeast(0f))
                    clip = true
                }
                .background(base)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val k = p.value
                        alpha = seg(k, 0.10f, 0.50f)
                        val s = 1.05f - 0.05f * k
                        scaleX = s
                        scaleY = s
                    }
            ) {
                ChannelEditPage(
                    channel = channel,
                    channels = channels,
                    dark = dark,
                    uid = uid,
                    myName = myName,
                    myPhoto = myPhoto,
                    onClose = onClose,
                    onOpenProfile = onOpenProfile
                )
            }
        }
    }
}

@Composable
private fun ChannelEditPage(
    channel: Channel,
    channels: ChannelsState,
    dark: Boolean,
    uid: String,
    myName: String,
    myPhoto: Bitmap?,
    onClose: () -> Unit,
    onOpenProfile: (ProfileTarget) -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val focus = LocalFocusManager.current
        val fg = if (dark) Color.White else Color(0xFF101114)
        val accent = EditAccent
        // what the signed-in account may do here: the owner everything, a supervisor what the owner gave him
        val isOwnerMe = channel.isOwner
        val canEdit = channel.can(uid, Perm.EDIT)          // change name / @username / bio / photo
        val canKick = channel.can(uid, Perm.KICK)
        val canBan = channel.can(uid, Perm.BAN)

        // what is typed / picked here (nothing is saved until "Save"; the X throws it away)
        var name by remember(channel.id) { mutableStateOf(channel.name) }
        var username by remember(channel.id) { mutableStateOf(channel.username) }
        var bio by remember(channel.id) { mutableStateOf(channel.bio) }
        var photo by remember(channel.id) { mutableStateOf(channel.photo) }
        var photoSource by remember(channel.id) { mutableStateOf<Bitmap?>(null) }
        var photoTf by remember(channel.id) { mutableStateOf<CropTransform?>(null) }
        var photoChanged by remember(channel.id) { mutableStateOf(false) }

        var showErrors by remember { mutableStateOf(false) }
        var tab by remember { mutableStateOf(0) }            // 0 = members, 1 = banned
        var saving by remember { mutableStateOf(false) }
        var saved by remember { mutableStateOf(false) }
        var usernameTaken by remember { mutableStateOf(false) }
        var cropping by remember { mutableStateOf<Pair<Bitmap, CropTransform?>?>(null) }
        // the member whose options panel is open (owner, or a supervisor that may kick / ban)
        var menuUid by remember { mutableStateOf<String?>(null) }
        BackHandler(enabled = menuUid != null) { menuUid = null }
        // the "kick / ban this member?" question (a LiquidDialog). The three values stay while the dialog fades out
        var askShow by remember { mutableStateOf(false) }
        var askUid by remember { mutableStateOf("") }
        var askBan by remember { mutableStateOf(false) }

        val nameOk = isValidName(name)
        val userOk = isValidUsername(username)
        val changed = name.trim() != channel.name || username != channel.username || bio.trim() != channel.bio || photoChanged
        // the problems show as soon as something was changed (the Save button stays dim until everything is right)
        val showErr = showErrors || changed
        val loadError = stringResource(R.string.error_image_load)
        val saveError = stringResource(R.string.error_save)
        val actionError = stringResource(R.string.error_member_action)

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                scope.launch {
                    val bmp = decodePickedImage(context, uri)
                    if (bmp != null) cropping = bmp to null
                    else Toast.makeText(context, loadError, Toast.LENGTH_SHORT).show()
                }
            }
        }

        fun trySave() {
            showErrors = true
            if (!canEdit || !nameOk || !userOk || usernameTaken || saving) return
            if (!changed) {
                onClose()
                return
            }
            saving = true
            focus.clearFocus()
            scope.launch {
                try {
                    channels.updateChannel(channel, name.trim(), username, bio.trim(), photo, photoChanged)
                    saved = true                                   // the check turns green, then the page closes
                    delay(600)
                    onClose()
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    saving = false
                    saved = false
                    if (e.isUsernameTaken()) usernameTaken = true
                    else Toast.makeText(context, saveError, Toast.LENGTH_LONG).show()
                }
            }
        }

        // owner first, then the supervisors, then everybody else (the owner is shown even if an old channel has no "members" list)
        val ordered = remember(channel.members, channel.ownerUid, channel.admins) {
            val all = (listOf(channel.ownerUid) + channel.members).filter { it.isNotBlank() }.distinct()
            all.filter { it == channel.ownerUid } + all.filter { it != channel.ownerUid && it in channel.admins } +
                all.filter { it != channel.ownerUid && it !in channel.admins }
        }
        // runs one action of the options panel; a failure only shows the "could not do it" message
        fun act(closeMenu: Boolean, block: suspend () -> Unit) {
            if (closeMenu) menuUid = null
            scope.launch {
                try {
                    block()
                } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(context, actionError, Toast.LENGTH_LONG).show()
                }
            }
        }
        // owner only: supervisor on / off, and what each supervisor may do
        fun setAdmin(target: String, on: Boolean) = act(false) { channels.setAdmin(channel, target, on) }
        fun setPerm(target: String, perm: String, on: Boolean) = act(false) { channels.setAdminPerm(channel, target, perm, on) }
        fun setTitle(target: String, title: String) = act(false) { channels.setAdminTitle(channel, target, title) }
        fun kick(target: String) = act(true) { channels.kickMember(channel, target) }
        fun ban(target: String) = act(true) { channels.banMember(channel, target) }
        fun unban(target: String) = act(false) { channels.unbanMember(channel, target) }

        // Records the WHOLE page, so the dialog's glass refracts everything behind it.
        val screenBackdrop = rememberLayerBackdrop()
        // What the glass of the page refracts: only the dotted background (like Settings / Account).
        val glassBackdrop = rememberLayerBackdrop()
        val dots = rememberDotsState()

        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().layerBackdrop(screenBackdrop).dotsTouch(dots)) {
                Box(Modifier.fillMaxSize().layerBackdrop(glassBackdrop)) { DotBackground(dots, dark) }
                val bd: Backdrop = glassBackdrop
                val listState = rememberLazyListState()
                // the first moments: the pieces rise one after another; rows that scroll in later do not replay it
                var intro by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(1800)
                    intro = false
                }
                // the page uses the WHOLE screen: the list scrolls under the status bar and the navigation bar
                val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val fadeBase = if (dark) Color.Black else Color(0xFFF4F5F9)
                // the member cards sit on a soft fill (no glass: there can be many of them)
                val rowFill = if (dark) Color.White.copy(alpha = 0.07f) else Color.White.copy(alpha = 0.75f)
                val bannedList = if (canBan) channel.banned else emptyList()
                val tabNow = if (bannedList.isNotEmpty()) tab else 0
                val coverH = topInset + 250.dp
                val memberPairs = remember(ordered) { ordered.chunked(2) }
                val nameError = if (showErr && !nameOk) stringResource(R.string.error_name_empty) else null
                val userError = when {
                    usernameTaken -> stringResource(R.string.error_username_taken)
                    showErr && !userOk -> stringResource(R.string.error_username_short)
                    else -> null
                }
                val membersLabel = stringResource(R.string.channel_members_header, ordered.size)
                val bannedLabel = if (bannedList.isNotEmpty()) stringResource(R.string.channel_banned_header, bannedList.size) else null
                val density = LocalDensity.current
                // the title is white over the cover, and turns to the normal text colour when the list scrolls up
                val titleColor by remember(fg) {
                    derivedStateOf {
                        val off = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 4000f
                        val from = with(density) { 160.dp.toPx() }
                        val span = with(density) { 90.dp.toPx() }
                        lerpColor(Color.White, fg, ((off - from) / span).coerceIn(0f, 1f))
                    }
                }

                // no side padding here: the cover goes edge to edge, the other items add their own side padding
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().imePadding(),
                    contentPadding = PaddingValues(bottom = bottomInset + 120.dp)
                ) {
                    // ---- the top: blurred cover made from the photo, the photo on its edge, then name / @username / bio
                    //      typed right on the page (no field cards), and three small chips
                    item(key = "hero") {
                        ChannelHero(
                            listState = listState,
                            fg = fg,
                            accent = accent,
                            base = fadeBase,
                            coverHeight = coverH,
                            photo = photo,
                            name = name,
                            username = username,
                            bio = bio,
                            canEdit = canEdit,
                            canMove = photoSource != null,
                            memberCount = ordered.size,
                            adminCount = channel.admins.size,
                            isPublic = channel.isPublic,
                            nameError = nameError,
                            userError = userError,
                            usernameBad = username.length < USERNAME_MIN,
                            animate = intro,
                            onName = { name = it.replace("\n", "").take(NAME_MAX) },
                            onUsername = { v ->
                                username = v.filter { it.isUsernameChar() }.take(USERNAME_MAX)
                                usernameTaken = false
                            },
                            onBio = { bio = it.take(BIO_MAX) },
                            onPick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onMove = { photoSource?.let { cropping = it to photoTf } }
                        )
                    }

                    // ---- members / banned: a small switch when there are banned people, else just a title
                    item(key = "tabs") {
                        Rise(
                            delayMs = 420,
                            animate = intro,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 14.dp)
                        ) {
                            SectionTabs(tabNow, { tab = it }, membersLabel, bannedLabel, fg, accent)
                        }
                    }

                    if (tabNow == 0) {
                        // members: cards, two in a row
                        itemsIndexed(memberPairs, key = { _, pr -> "mp_" + pr.joinToString("_") }) { index, pr ->
                            Rise(
                                delayMs = 480 + minOf(index, 5) * 60,
                                animate = intro,
                                modifier = Modifier.animateItem().padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    pr.forEach { memberUid ->
                                        MemberCard(
                                            modifier = Modifier.weight(1f),
                                            uid = memberUid,
                                            isOwnerRow = memberUid == channel.ownerUid,
                                            isAdmin = memberUid in channel.admins,
                                            adminTitle = channel.adminTitles[memberUid].orEmpty(),
                                            isMe = memberUid == uid,
                                            // the options button: never on the owner's card or on my own. The owner has it on
                                            // everybody; a supervisor only on ordinary members and only if he may kick or ban
                                            showMenu = memberUid != channel.ownerUid && memberUid != uid &&
                                                (isOwnerMe || (memberUid !in channel.admins && (canKick || canBan))),
                                            onMenu = { menuUid = memberUid },
                                            myName = myName,
                                            myPhoto = myPhoto,
                                            fill = rowFill,
                                            fg = fg,
                                            onOpenProfile = onOpenProfile
                                        )
                                    }
                                    if (pr.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        // banned people (only for those who may lift a ban)
                        itemsIndexed(bannedList, key = { _, b -> "b_$b" }) { index, bannedUid ->
                            BannedRow(
                                modifier = Modifier.animateItem().padding(horizontal = 16.dp),
                                uid = bannedUid,
                                onUnban = { unban(bannedUid) },
                                first = index == 0,
                                last = index == bannedList.lastIndex,
                                dark = dark,
                                fg = fg,
                                onOpenProfile = onOpenProfile
                            )
                        }
                    }
                }

                // ---- the top bar: only the close button and the title (the list scrolls under it)
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(topInset + 90.dp)
                        .graphicsLayer {
                            val off = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 4000f
                            alpha = ((off - 160.dp.toPx()) / 90.dp.toPx()).coerceIn(0f, 1f)
                        }
                        .background(Brush.verticalGradient(listOf(fadeBase.copy(alpha = 0.95f), fadeBase.copy(alpha = 0f))))
                )
                Rise(
                    delayMs = 0,
                    animate = intro,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Box(Modifier.fillMaxWidth().height(48.dp)) {
                        GlassSurface(
                            backdrop = bd,
                            container = if (dark) Color(0xFF121212).copy(alpha = 0.40f) else Color.White.copy(alpha = 0.50f),
                            modifier = Modifier.align(Alignment.CenterStart).size(48.dp),
                            onClick = onClose
                        ) { GlyphIcon(R.drawable.ic_close, fg, 22.dp) }
                        BasicText(
                            stringResource(if (canEdit) R.string.channel_edit_title else R.string.channel_info_title),
                            modifier = Modifier.align(Alignment.Center),
                            style = TextStyle(color = titleColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }

                // ---- Save: a wide glass button that slides up from the bottom only when something was changed
                val showSave = canEdit && (changed || saving || saved)
                val saveA by animateFloatAsState(if (showSave) 1f else 0f, spring(0.8f, 380f), label = "saveSlide")
                if (saveA > 0.01f) {
                    val saveColor by animateColorAsState(
                        if (saved) Color(0xFF30D158).copy(alpha = 0.9f) else AppAccent.deep(Color(0xFFCC6A00)).copy(alpha = 0.9f),
                        tween(250), label = "saveColor"
                    )
                    val valid = nameOk && userOk && !usernameTaken
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                            .graphicsLayer {
                                translationY = 100.dp.toPx() * (1f - saveA)
                                alpha = saveA.coerceIn(0f, 1f)
                            }
                    ) {
                        GlassSurface(
                            backdrop = bd,
                            container = saveColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .graphicsLayer { alpha = if (valid || saving) 1f else 0.5f },
                            onClick = { if (valid && !saving) trySave() }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BasicText(
                                    if (saving && !saved) "..." else stringResource(R.string.save),
                                    style = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                )
                                Spacer(Modifier.width(8.dp))
                                GlyphIcon(R.drawable.ic_check, Color.White, 22.dp)
                            }
                        }
                    }
                }

                // ---- the options of one member (owner only)
                val menuTarget = menuUid
                if (menuTarget != null) {
                    if (menuTarget in ordered) {
                        MemberMenu(
                            uid = menuTarget,
                            isMe = menuTarget == uid,
                            myName = myName,
                            myPhoto = myPhoto,
                            isAdmin = menuTarget in channel.admins,
                            perms = channel.adminPerms[menuTarget].orEmpty(),
                            // the owner decides who is a supervisor and what each supervisor may do
                            isOwnerMe = isOwnerMe,
                            canKick = canKick,
                            canBan = canBan,
                            bd = bd,
                            dark = dark,
                            fg = fg,
                            adminTitle = channel.adminTitles[menuTarget].orEmpty(),
                            onAdmin = { on -> setAdmin(menuTarget, on) },
                            onPerm = { perm, on -> setPerm(menuTarget, perm, on) },
                            onTitle = { t -> setTitle(menuTarget, t) },
                            onAskKick = { askUid = menuTarget; askBan = false; askShow = true },
                            onAskBan = { askUid = menuTarget; askBan = true; askShow = true },
                            onDismiss = { menuUid = null }
                        )
                    } else {
                        // the member is gone (kicked / left): close the panel
                        LaunchedEffect(menuTarget) { menuUid = null }
                    }
                }
            }

            // ---- kick / ban: asks first (outside the recorded box, as the LiquidDialog README says)
            val askName = MemberDirectory.info[askUid]?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.member_this)
            LiquidDialog(
                visible = askShow,
                onDismiss = { askShow = false },
                backdrop = screenBackdrop,
                dark = dark,
                title = stringResource(if (askBan) R.string.member_ban_title else R.string.member_kick_title),
                message = stringResource(if (askBan) R.string.member_ban_msg else R.string.member_kick_msg, askName),
                confirmText = stringResource(if (askBan) R.string.member_ban_yes else R.string.member_kick_yes),
                dismissText = stringResource(R.string.cancel),
                confirmTint = EditRed,
                onConfirm = {
                    askShow = false
                    if (askBan) ban(askUid) else kick(askUid)
                }
            )

            // ---- "move the picture" page on top
            cropping?.let { (src, tf) ->
                CropFadeIn {
                    ImageCropScreen(
                        title = stringResource(R.string.channel_crop_title),
                        hint = stringResource(R.string.move_avatar_hint),
                        source = src,
                        circle = true,
                        aspect = 1f,
                        initial = tf,
                        onCancel = { cropping = null },
                        onDone = { cropped, t ->
                            photo = cropped
                            photoSource = src
                            photoTf = t
                            photoChanged = true
                            cropping = null
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
private fun CropFadeIn(content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, tween(240, easing = SETTLE)) }
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = a.value }) { content() }
}

/** An element that rises into its place: from lower, a little smaller and blurred, to sharp. [animate] = false skips it. */
@Composable
private fun Rise(delayMs: Int, animate: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val a = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) {
            delay(INTRO_BASE_MS + delayMs.toLong())
            a.animateTo(1f, tween(600, easing = SETTLE))
        }
    }
    Box(
        modifier.graphicsLayer {
            val t = a.value
            alpha = (t * 1.6f).coerceIn(0f, 1f)
            translationY = 30.dp.toPx() * (1f - t)
            val sc = 0.94f + 0.06f * t
            scaleX = sc
            scaleY = sc
            renderEffect = if (t < 1f) blurEffect(8.dp.toPx() * (1f - t)) else null
        }
    ) { content() }
}

/** "2 members" in Arabic (one / two / 3-10 / more). */
private fun membersWord(n: Int): String = when {
    n == 1 -> "عضو واحد"
    n == 2 -> "عضوان"
    n in 3..10 -> "$n أعضاء"
    else -> "$n عضو"
}

private fun adminsWord(n: Int): String = when {
    n == 0 -> "بدون مشرفين"
    n == 1 -> "مشرف واحد"
    n == 2 -> "مشرفان"
    n in 3..10 -> "$n مشرفين"
    else -> "$n مشرف"
}

/**
 * The top of the page. A cover made from the channel photo (enlarged and blurred, fading into the page) with the photo
 * itself sitting on its lower edge (a small button on it picks a new one; the photo is tappable too). Under it the NAME,
 * the @USERNAME and the BIO are typed right on the page - tap the text and write, there are no field cards. Then three
 * small chips: type, members, supervisors. The cover moves slower than the list when it scrolls.
 */
@Composable
private fun ChannelHero(
    listState: LazyListState,
    fg: Color,
    accent: Color,
    base: Color,
    coverHeight: Dp,
    photo: Bitmap?,
    name: String,
    username: String,
    bio: String,
    canEdit: Boolean,
    canMove: Boolean,
    memberCount: Int,
    adminCount: Int,
    isPublic: Boolean,
    nameError: String?,
    userError: String?,
    usernameBad: Boolean,
    animate: Boolean,
    onName: (String) -> Unit,
    onUsername: (String) -> Unit,
    onBio: (String) -> Unit,
    onPick: () -> Unit,
    onMove: () -> Unit
) {
    val appear = remember { Animatable(if (animate) 0f else 1f) }
    val pop = remember { Animatable(if (animate) 0.5f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) {
            delay(INTRO_BASE_MS + 40L)
            launch { appear.animateTo(1f, tween(500)) }
            pop.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 280f))
        }
    }
    val cover = remember(photo) { photo?.asImageBitmap() }

    Box(Modifier.fillMaxWidth()) {
        // ---- the cover
        Box(
            Modifier
                .fillMaxWidth()
                .height(coverHeight)
                .graphicsLayer {
                    val off = if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 4000f
                    translationY = off * 0.5f
                    alpha = appear.value
                }
                .clipToBounds()
        ) {
            if (cover != null) {
                Image(
                    bitmap = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.3f
                            scaleY = 1.3f
                            renderEffect = blurEffect(28.dp.toPx())
                        }
                )
            } else {
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.12f)))))
            }
            // dark at the top (the buttons stay readable), melting into the page at the bottom
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.45f to Color.Black.copy(alpha = 0.25f),
                        1f to base
                    )
                )
            )
        }

        // ---- the photo on the cover's edge, and everything under it
        Column(
            Modifier.fillMaxWidth().padding(top = coverHeight - 58.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(116.dp)
                    .graphicsLayer {
                        scaleX = pop.value
                        scaleY = pop.value
                        alpha = appear.value
                    }
            ) {
                Box(Modifier.fillMaxSize().clip(CircleShape).background(base), contentAlignment = Alignment.Center) {
                    Avatar(
                        photo = photo,
                        name = name,
                        size = 106.dp,
                        modifier = if (canEdit) {
                            Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onPick)
                        } else Modifier
                    )
                }
                if (canEdit) {
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(base)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(AppAccent.deep(Color(0xFFDB7608)))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onPick),
                        contentAlignment = Alignment.Center
                    ) { GlyphIcon(R.drawable.ic_image, Color.White, 17.dp) }
                }
            }

            Spacer(Modifier.height(12.dp))
            Rise(delayMs = 120, animate = animate) {
                InlineField(
                    value = name,
                    onValueChange = onName,
                    maxLen = NAME_MAX,
                    fg = fg,
                    accent = accent,
                    textStyle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                    keyboardType = KeyboardType.Text,
                    pencil = true,
                    readOnly = !canEdit,
                    error = nameError
                )
            }
            Spacer(Modifier.height(6.dp))
            Rise(delayMs = 160, animate = animate) {
                InlineField(
                    value = username,
                    onValueChange = onUsername,
                    maxLen = USERNAME_MAX,
                    fg = fg,
                    accent = accent,
                    textStyle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    keyboardType = KeyboardType.Ascii,
                    prefix = "@",
                    readOnly = !canEdit,
                    error = userError,
                    countBad = usernameBad
                )
            }
            if (canEdit || bio.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Rise(delayMs = 200, animate = animate) {
                    InlineField(
                        value = bio,
                        onValueChange = onBio,
                        maxLen = BIO_MAX,
                        fg = fg,
                        accent = accent,
                        textStyle = TextStyle(fontSize = 15.sp),
                        keyboardType = KeyboardType.Text,
                        hint = if (canEdit) stringResource(R.string.create_win_bio_hint) else null,
                        wide = true,
                        readOnly = !canEdit
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Rise(delayMs = 250, animate = animate) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    InfoChip(
                        if (isPublic) R.drawable.ic_globe else R.drawable.ic_lock,
                        stringResource(if (isPublic) R.string.channel_stat_public else R.string.channel_stat_private),
                        fg, accent
                    )
                    InfoChip(R.drawable.ic_people, membersWord(memberCount), fg, accent)
                    InfoChip(R.drawable.ic_tab_account, adminsWord(adminCount), fg, accent)
                }
            }
            if (canEdit && canMove) {
                Spacer(Modifier.height(6.dp))
                TextAction(stringResource(R.string.channel_photo_move), null, accent, onMove)
            }
        }
    }
}

/**
 * Text typed right on the page: centred, no box. A thin line under it grows and takes the accent colour while it is
 * focused, a small counter shows then, and a tiny pencil tells that the text can be edited. [wide] = a text of several
 * lines (the bio), else it is as wide as its text.
 */
@Composable
private fun InlineField(
    value: String,
    onValueChange: (String) -> Unit,
    maxLen: Int,
    fg: Color,
    accent: Color,
    textStyle: TextStyle,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    hint: String? = null,
    wide: Boolean = false,
    pencil: Boolean = false,
    readOnly: Boolean = false,
    error: String? = null,
    countBad: Boolean = false
) {
    var focused by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    val glow by animateFloatAsState(if (focused) 1f else 0f, tween(240), label = "inlineGlow")
    val line by animateColorAsState(if (focused) accent else fg.copy(alpha = 0.12f), tween(240), label = "inlineLine")
    val centered = textStyle.copy(textAlign = TextAlign.Center)

    Column(
        modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                if (!readOnly) fr.requestFocus()
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (prefix != null) BasicText(prefix, style = textStyle.copy(color = fg.copy(alpha = 0.5f)))
            Box(
                if (wide) Modifier.fillMaxWidth() else Modifier.widthIn(min = 56.dp, max = 260.dp),
                contentAlignment = Alignment.Center
            ) {
                if (value.isEmpty() && hint != null) {
                    BasicText(hint, style = centered.copy(color = fg.copy(alpha = 0.3f)))
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    readOnly = readOnly,
                    singleLine = !wide,
                    maxLines = if (wide) 5 else 1,
                    textStyle = centered.copy(color = fg),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = (if (wide) Modifier.fillMaxWidth() else Modifier)
                        .focusRequester(fr)
                        .onFocusChanged { focused = it.isFocused }
                )
            }
            if (pencil && !readOnly && !focused) {
                Spacer(Modifier.width(8.dp))
                GlyphIcon(R.drawable.ic_edit, fg.copy(alpha = 0.35f), 15.dp)
            }
        }
        if (!readOnly) {
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.25f + 0.55f * glow)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(line)
            )
            if (focused) {
                Spacer(Modifier.height(4.dp))
                BasicText(
                    "${value.length}/$maxLen",
                    style = TextStyle(color = if (countBad) EditRed else fg.copy(alpha = 0.5f), fontSize = 11.sp)
                )
            }
        }
        if (error != null) {
            Spacer(Modifier.height(4.dp))
            BasicText(error, style = TextStyle(color = EditRed, fontSize = 12.sp, textAlign = TextAlign.Center))
        }
    }
}

/** A small pill with an icon and a word (type / members / supervisors of the channel). */
@Composable
private fun InfoChip(icon: Int, text: String, fg: Color, accent: Color) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(fg.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlyphIcon(icon, accent, 14.dp)
        Spacer(Modifier.width(5.dp))
        BasicText(text, maxLines = 1, softWrap = false, style = TextStyle(color = fg.copy(alpha = 0.85f), fontSize = 12.5.sp, fontWeight = FontWeight.Medium))
    }
}

/** A quiet text button (no box): small icon + a word in the accent colour. */
@Composable
private fun TextAction(text: String, icon: Int?, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            GlyphIcon(icon, accent, 17.dp)
            Spacer(Modifier.width(6.dp))
        }
        BasicText(text, style = TextStyle(color = accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
    }
}

/** Members / banned switch. With no banned people it is only the title of the list. */
@Composable
private fun SectionTabs(tab: Int, onTab: (Int) -> Unit, membersLabel: String, bannedLabel: String?, fg: Color, accent: Color) {
    if (bannedLabel == null) {
        BasicText(
            membersLabel,
            modifier = Modifier.padding(horizontal = 4.dp),
            style = TextStyle(color = fg.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        )
    } else {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(fg.copy(alpha = 0.07f))
                .padding(4.dp)
        ) {
            TabPill(Modifier.weight(1f), membersLabel, tab == 0, accent, fg) { onTab(0) }
            TabPill(Modifier.weight(1f), bannedLabel, tab == 1, EditRed, fg) { onTab(1) }
        }
    }
}

@Composable
private fun TabPill(modifier: Modifier, text: String, selected: Boolean, tint: Color, fg: Color, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) tint.copy(alpha = 0.20f) else Color.Transparent, tween(200), label = "tabBg")
    val c by animateColorAsState(if (selected) tint else fg.copy(alpha = 0.6f), tween(200), label = "tabText")
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        BasicText(text, maxLines = 1, style = TextStyle(color = c, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
    }
}

/** The corners of a row inside a grouped list: only the first row is round on top, only the last one at the bottom. */
private fun groupShape(first: Boolean, last: Boolean): RoundedCornerShape {
    val top = if (first) 26.dp else 0.dp
    val bottom = if (last) 26.dp else 0.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/**
 * One member as a card (two cards in a row): big photo (tap = his profile), name, @username and a small badge (owner /
 * supervisor / you). The round options button sits in the corner of the card.
 */
@Composable
private fun MemberCard(
    modifier: Modifier,
    uid: String,
    isOwnerRow: Boolean,
    isAdmin: Boolean,
    adminTitle: String,
    isMe: Boolean,
    showMenu: Boolean,
    onMenu: () -> Unit,
    myName: String,
    myPhoto: Bitmap?,
    fill: Color,
    fg: Color,
    onOpenProfile: (ProfileTarget) -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(uid) {
        MemberDirectory.request(uid)
        UserPhotos.request(context, uid)
    }
    val info = MemberDirectory.info[uid]
    val name = info?.name ?: if (isMe) myName else ""
    val username = info?.username.orEmpty()
    val photo = (if (isMe) myPhoto else null) ?: UserPhotos.photos[uid]
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val ownerWord = stringResource(R.string.channel_owner_badge)
    val adminWord = stringResource(R.string.channel_admin_badge)
    val youWord = stringResource(R.string.channel_you_badge)
    val badge: Pair<String, Color>? = when {
        isOwnerRow -> ownerWord to EditOrange
        isAdmin -> (if (adminTitle.isBlank()) adminWord else adminTitle) to EditMint
        isMe -> youWord to EditViolet
        else -> null
    }

    Box(modifier.clip(RoundedCornerShape(24.dp)).background(fill)) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // the photo: a tap opens the profile of this member (it grows out of the photo)
            Box(
                Modifier
                    .size(64.dp)
                    .onGloballyPositioned { bounds = it.boundsInRoot() }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        onOpenProfile(ProfileTarget(uid, name, photo, bounds))
                    }
            ) { Avatar(photo, name, 64.dp) }
            Spacer(Modifier.height(10.dp))
            BasicText(
                name.ifBlank { "…" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(color = fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            )
            BasicText(
                if (username.isNotEmpty()) "@$username" else " ",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(color = fg.copy(alpha = 0.55f), fontSize = 12.sp, textAlign = TextAlign.Center)
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.height(22.dp), contentAlignment = Alignment.Center) {
                if (badge != null) Badge(badge.first, badge.second)
            }
        }
        if (showMenu) {
            // the options of this member (supervisor + permissions / kick / ban)
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(fg.copy(alpha = 0.08f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onMenu),
                contentAlignment = Alignment.Center
            ) { GlyphIcon(R.drawable.ic_settings, fg.copy(alpha = 0.8f), 17.dp) }
        }
    }
}

/**
 * The options of one member: a glass panel over the page. A tap outside the panel (or Back) closes it.
 *  - the OWNER sees "Make supervisor" (a switch). When the member is a supervisor, the owner also sees what that supervisor
 *    may do: Ban members / Kick members / Edit the channel information, one switch each (all off for a new supervisor).
 *  - the OWNER can also type the name that is shown next to that supervisor (instead of "Supervisor").
 *  - Kick and Ban are buttons; each one asks the question in a LiquidDialog (the page shows it: [onAskKick] / [onAskBan]).
 *    The owner has both; a supervisor only the ones the owner allowed him ([canKick], [canBan]).
 * The panel scrolls when it is taller than the screen.
 */
@Composable
private fun MemberMenu(
    uid: String,
    isMe: Boolean,
    myName: String,
    myPhoto: Bitmap?,
    isAdmin: Boolean,
    adminTitle: String,
    perms: Set<String>,
    isOwnerMe: Boolean,
    canKick: Boolean,
    canBan: Boolean,
    bd: Backdrop,
    dark: Boolean,
    fg: Color,
    onAdmin: (Boolean) -> Unit,
    onPerm: (String, Boolean) -> Unit,
    onTitle: (String) -> Unit,
    onAskKick: () -> Unit,
    onAskBan: () -> Unit,
    onDismiss: () -> Unit
) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 420f)) }

    val info = MemberDirectory.info[uid]
    val name = info?.name ?: if (isMe) myName else ""
    val username = info?.username.orEmpty()
    val photo = (if (isMe) myPhoto else null) ?: UserPhotos.photos[uid]
    val shape = RoundedCornerShape(32.dp)

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 0.45f * a.value.coerceIn(0f, 1f))) }
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(horizontal = 22.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val k = a.value
                    val sc = 0.88f + 0.12f * k
                    scaleX = sc
                    scaleY = sc
                    alpha = k.coerceIn(0f, 1f)
                }
                .pointerInput(Unit) { detectTapGestures { } }           // a tap on the panel does not close it
                .liquidCard(bd, shape, dark)
                .background(if (dark) Color(0xFF14161B).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.45f), shape)
                .padding(horizontal = 18.dp, vertical = 18.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // who
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(photo, name, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    BasicText(
                        name.ifBlank { "…" },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    )
                    if (username.isNotEmpty()) {
                        BasicText(
                            "@$username",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = fg.copy(alpha = 0.6f), fontSize = 13.sp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(fg.copy(alpha = 0.12f)))
            Spacer(Modifier.height(6.dp))

            // ---- owner only: supervisor + what the supervisor may do
            if (isOwnerMe) {
                OptionSwitch(
                    title = stringResource(R.string.member_menu_admin),
                    sub = stringResource(R.string.member_menu_admin_sub),
                    selected = isAdmin,
                    onSelect = onAdmin,
                    bd = bd, dark = dark, fg = fg
                )
                if (isAdmin) {
                    AdminTitleField(current = adminTitle, bd = bd, fg = fg, onSave = onTitle)
                    Spacer(Modifier.height(4.dp))
                    BasicText(
                        stringResource(R.string.member_menu_perms_title),
                        modifier = Modifier.padding(top = 4.dp),
                        style = TextStyle(color = fg.copy(alpha = 0.75f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    )
                    OptionSwitch(
                        title = stringResource(R.string.perm_ban),
                        sub = stringResource(R.string.perm_ban_sub),
                        selected = Perm.BAN in perms,
                        onSelect = { on -> onPerm(Perm.BAN, on) },
                        bd = bd, dark = dark, fg = fg
                    )
                    OptionSwitch(
                        title = stringResource(R.string.perm_kick),
                        sub = stringResource(R.string.perm_kick_sub),
                        selected = Perm.KICK in perms,
                        onSelect = { on -> onPerm(Perm.KICK, on) },
                        bd = bd, dark = dark, fg = fg
                    )
                    OptionSwitch(
                        title = stringResource(R.string.perm_edit),
                        sub = stringResource(R.string.perm_edit_sub),
                        selected = Perm.EDIT in perms,
                        onSelect = { on -> onPerm(Perm.EDIT, on) },
                        bd = bd, dark = dark, fg = fg
                    )
                }
                Spacer(Modifier.height(10.dp))
            }

            // ---- kick / ban: a tap opens the question (LiquidDialog), the dialog's red button does it
            if (canKick) {
                DangerButton(text = stringResource(R.string.member_menu_kick), onClick = onAskKick)
            }
            if (canKick && canBan) Spacer(Modifier.height(8.dp))
            if (canBan) {
                DangerButton(text = stringResource(R.string.member_menu_ban), onClick = onAskBan)
            }
        }
    }
}

/** A soft red button (kick / ban). It does not act by itself: it opens the question dialog. */
@Composable
private fun DangerButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(EditRed.copy(alpha = 0.18f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text,
            style = TextStyle(color = EditRed, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        )
    }
}

/**
 * Owner only, for a supervisor: the name that is shown next to him instead of "Supervisor" (at most [ADMIN_TITLE_MAX]
 * letters). The check button appears when the text changed (the keyboard's Done does the same); empty = the default word.
 */
@Composable
private fun AdminTitleField(current: String, bd: Backdrop, fg: Color, onSave: (String) -> Unit) {
    var text by remember(current) { mutableStateOf(current) }
    val focus = LocalFocusManager.current
    val dirty = text.trim() != current
    val fieldShape = RoundedCornerShape(23.dp)
    fun commit() {
        if (dirty) onSave(text.trim())
        focus.clearFocus()
    }

    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 6.dp)) {
        BasicText(
            stringResource(R.string.admin_title_label),
            style = TextStyle(color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        )
        Spacer(Modifier.height(2.dp))
        BasicText(
            stringResource(R.string.admin_title_sub),
            style = TextStyle(color = fg.copy(alpha = 0.6f), fontSize = 12.sp)
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .height(46.dp)
                    .clip(fieldShape)
                    .background(fg.copy(alpha = 0.08f))
                    .border(1.dp, fg.copy(alpha = 0.14f), fieldShape)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isEmpty()) {
                    BasicText(
                        stringResource(R.string.channel_admin_badge),
                        style = TextStyle(color = fg.copy(alpha = 0.35f), fontSize = 15.sp)
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.replace("\n", "").take(ADMIN_TITLE_MAX) },
                    singleLine = true,
                    textStyle = TextStyle(color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(EditMint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commit() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (dirty) {
                Spacer(Modifier.width(8.dp))
                GlassSurface(
                    backdrop = bd,
                    container = EditMint.copy(alpha = 0.85f),
                    modifier = Modifier.size(46.dp),
                    onClick = { commit() }
                ) { GlyphIcon(R.drawable.ic_check, Color.White, 22.dp) }
            }
        }
    }
}

/**
 * One banned person: photo (tap = his profile), name, @username and a small button that lifts the ban.
 * The rows sit on ONE soft red sheet (same shape rule as the members).
 */
@Composable
private fun BannedRow(
    modifier: Modifier,
    uid: String,
    onUnban: () -> Unit,
    first: Boolean,
    last: Boolean,
    dark: Boolean,
    fg: Color,
    onOpenProfile: (ProfileTarget) -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(uid) {
        MemberDirectory.request(uid)
        UserPhotos.request(context, uid)
    }
    val info = MemberDirectory.info[uid]
    val name = info?.name ?: ""
    val username = info?.username.orEmpty()
    val photo = UserPhotos.photos[uid]
    var bounds by remember { mutableStateOf(Rect.Zero) }
    // one tap is enough: the button waits a few seconds (a second tap would only ask the server for the same thing twice)
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(busy) {
        if (busy) {
            delay(6000)
            busy = false
        }
    }

    Column(
        modifier
            .fillMaxWidth()
            .clip(groupShape(first, last))
            .background(EditRed.copy(alpha = if (dark) 0.12f else 0.10f))
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(MEMBER_PHOTO)
                    .onGloballyPositioned { bounds = it.boundsInRoot() }
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        onOpenProfile(ProfileTarget(uid, name, photo, bounds))
                    }
            ) { Avatar(photo, name, MEMBER_PHOTO) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                BasicText(
                    name.ifBlank { "…" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                )
                if (username.isNotEmpty()) {
                    BasicText(
                        "@$username",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = fg.copy(alpha = 0.55f), fontSize = 13.sp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(fg.copy(alpha = if (busy) 0.05f else 0.11f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (!busy) {
                            busy = true
                            onUnban()
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    stringResource(R.string.channel_unban),
                    style = TextStyle(color = fg.copy(alpha = if (busy) 0.5f else 1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                )
            }
        }
        if (!last) {
            Box(Modifier.fillMaxWidth().padding(start = 72.dp).height(0.7.dp).background(EditRed.copy(alpha = 0.18f)))
        }
    }
}

/** A line of the member panel: title + small explanation, and the liquid switch at the far end. */
@Composable
private fun OptionSwitch(
    title: String,
    sub: String,
    selected: Boolean,
    onSelect: (Boolean) -> Unit,
    bd: Backdrop,
    dark: Boolean,
    fg: Color
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(title, style = TextStyle(color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(2.dp))
            BasicText(sub, style = TextStyle(color = fg.copy(alpha = 0.6f), fontSize = 12.sp))
        }
        Spacer(Modifier.width(12.dp))
        LiquidToggle(
            selected = { selected },
            onSelect = onSelect,
            backdrop = bd,
            dark = dark
        )
    }
}

@Composable
private fun Badge(text: String, tint: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.22f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        BasicText(text, maxLines = 1, style = TextStyle(color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
    }
}
