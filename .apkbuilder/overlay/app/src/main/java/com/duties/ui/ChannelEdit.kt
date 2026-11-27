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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duties.R
import com.google.firebase.firestore.FirebaseFirestore
import com.kyant.backdrop.Backdrop
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
 * It is drawn inside the chat's sliding layer (see ChatScreen.kt), so it slides away together with the chat.
 */

private val EditOrange = Color(0xFFFF8A1F)
private val EditViolet = Color(0xFF8A5CFF)
private val EditMint = Color(0xFF00D4AA)
private val EditRed = Color(0xFFE5484D)
private val MEMBER_PHOTO = 46.dp

private const val EDIT_OPEN_MS = 430
private const val EDIT_CLOSE_MS = 330

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
// The page that grows out of the name bar
// =====================================================================

/**
 * Hosts the page. [open] = true shows it (it grows out of [from], the place of the name bar), false plays the way
 * back. [onOpenProfile] opens the profile of a member (ChatMorph hosts that page, above this one).
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
            p.animateTo(1f, tween(EDIT_OPEN_MS, easing = OPEN_EASE))
        } else if (visible) {
            p.animateTo(0f, tween(EDIT_CLOSE_MS, easing = CLOSE_EASE))
            visible = false
        }
    }
    if (!visible) return

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val k = p.value
                val s = 0.55f + 0.45f * k
                scaleX = s
                scaleY = s
                alpha = seg(k, 0f, 0.55f)
                transformOrigin = TransformOrigin(
                    if (size.width > 0f) (from.center.x / size.width).coerceIn(0f, 1f) else 0.5f,
                    if (size.height > 0f) (from.center.y / size.height).coerceIn(0f, 1f) else 0.1f
                )
                clip = true
                shape = RoundedCornerShape(((1f - k) * 44f).dp)
            }
            .pointerInput(Unit) { detectTapGestures { } }            // covers the chat below
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
        var saving by remember { mutableStateOf(false) }
        var saved by remember { mutableStateOf(false) }
        var usernameTaken by remember { mutableStateOf(false) }
        var cropping by remember { mutableStateOf<Pair<Bitmap, CropTransform?>?>(null) }
        // the member whose options panel is open (owner, or a supervisor that may kick / ban)
        var menuUid by remember { mutableStateOf<String?>(null) }
        BackHandler(enabled = menuUid != null) { menuUid = null }

        val nameOk = isValidName(name)
        val userOk = isValidUsername(username)
        val changed = name.trim() != channel.name || username != channel.username || bio.trim() != channel.bio || photoChanged
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
        fun kick(target: String) = act(true) { channels.kickMember(channel, target) }
        fun ban(target: String) = act(true) { channels.banMember(channel, target) }
        fun unban(target: String) = act(false) { channels.unbanMember(channel, target) }

        Box(Modifier.fillMaxSize()) {
            LiquidScene(dark, photo) { bd ->
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .imePadding(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // ---- top bar: close, title, save
                    item(key = "top") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            GlassSurface(
                                backdrop = bd,
                                container = if (dark) Color(0xFF121212).copy(alpha = 0.40f) else Color.White.copy(alpha = 0.50f),
                                modifier = Modifier.size(48.dp),
                                onClick = onClose
                            ) { GlyphIcon(R.drawable.ic_close, fg, 22.dp) }
                            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                BasicText(
                                    stringResource(if (canEdit) R.string.channel_edit_title else R.string.channel_info_title),
                                    style = TextStyle(color = fg, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                                )
                                BasicText(
                                    stringResource(if (canEdit) R.string.channel_edit_subtitle else R.string.channel_info_subtitle),
                                    style = TextStyle(color = fg.copy(alpha = 0.6f), fontSize = 12.sp)
                                )
                            }
                            if (canEdit) {
                                SaveButton(
                                    backdrop = bd,
                                    enabled = !saving && (!showErrors || (nameOk && userOk && !usernameTaken)),
                                    saving = saving,
                                    done = saved,
                                    onClick = ::trySave
                                )
                            }
                        }
                    }

                    // ---- the channel photo
                    item(key = "photo") {
                        PhotoBlock(
                            bd = bd,
                            fg = fg,
                            photo = photo,
                            name = name,
                            canEdit = canEdit,
                            canMove = photoSource != null,
                            onPick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onMove = { photoSource?.let { cropping = it to photoTf } }
                        )
                    }

                    // ---- name + @username
                    item(key = "name") {
                        LiquidField(
                            icon = R.drawable.ic_tab_account,
                            tint = EditOrange,
                            label = stringResource(R.string.create_field_name),
                            value = name,
                            maxLen = NAME_MAX,
                            backdrop = bd,
                            dark = dark,
                            onValueChange = { name = it.replace("\n", "").take(NAME_MAX) },
                            error = if (showErrors && !nameOk) stringResource(R.string.error_name_empty) else null,
                            keyboardType = KeyboardType.Text,
                            readOnly = !canEdit
                        )
                    }
                    item(key = "username") {
                        LiquidField(
                            icon = R.drawable.ic_at,
                            tint = EditViolet,
                            label = stringResource(R.string.create_field_username),
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
                            keyboardType = KeyboardType.Ascii,
                            readOnly = !canEdit
                        )
                    }

                    // ---- bio (a member only sees it when there is one)
                    if (canEdit || channel.bio.isNotBlank()) {
                        item(key = "bio") {
                            LiquidField(
                                icon = R.drawable.ic_chat,
                                tint = EditMint,
                                label = stringResource(R.string.create_win_bio_label),
                                value = bio,
                                maxLen = BIO_MAX,
                                backdrop = bd,
                                dark = dark,
                                hint = if (canEdit) stringResource(R.string.create_win_bio_hint) else null,
                                singleLine = false,
                                minLines = 2,
                                onValueChange = { bio = it.take(BIO_MAX) },
                                keyboardType = KeyboardType.Text,
                                readOnly = !canEdit
                            )
                        }
                    }

                    // ---- members
                    item(key = "membersHeader") {
                        BasicText(
                            stringResource(R.string.channel_members_header, ordered.size),
                            modifier = Modifier.padding(top = 10.dp, start = 8.dp, end = 8.dp),
                            style = TextStyle(color = fg.copy(alpha = 0.75f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        )
                    }
                    items(ordered, key = { "m_$it" }) { memberUid ->
                        MemberRow(
                            uid = memberUid,
                            isOwnerRow = memberUid == channel.ownerUid,
                            isAdmin = memberUid in channel.admins,
                            isMe = memberUid == uid,
                            // the options button: never on the owner's row or on my own. The owner has it on everybody;
                            // a supervisor only on ordinary members and only if he may kick or ban
                            showMenu = memberUid != channel.ownerUid && memberUid != uid &&
                                (isOwnerMe || (memberUid !in channel.admins && (canKick || canBan))),
                            onMenu = { menuUid = memberUid },
                            myName = myName,
                            myPhoto = myPhoto,
                            bd = bd,
                            dark = dark,
                            fg = fg,
                            onOpenProfile = onOpenProfile
                        )
                    }

                    // ---- the banned people (only for those who may lift a ban)
                    if (canBan && channel.banned.isNotEmpty()) {
                        item(key = "bannedHeader") {
                            BasicText(
                                stringResource(R.string.channel_banned_header, channel.banned.size),
                                modifier = Modifier.padding(top = 14.dp, start = 8.dp, end = 8.dp),
                                style = TextStyle(color = fg.copy(alpha = 0.75f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            )
                        }
                        items(channel.banned, key = { "b_$it" }) { bannedUid ->
                            BannedRow(
                                uid = bannedUid,
                                onUnban = { unban(bannedUid) },
                                bd = bd,
                                dark = dark,
                                fg = fg,
                                onOpenProfile = onOpenProfile
                            )
                        }
                    }
                    item(key = "bottomSpace") { Spacer(Modifier.height(24.dp)) }
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
                            onAdmin = { on -> setAdmin(menuTarget, on) },
                            onPerm = { perm, on -> setPerm(menuTarget, perm, on) },
                            onKick = { kick(menuTarget) },
                            onBan = { ban(menuTarget) },
                            onDismiss = { menuUid = null }
                        )
                    } else {
                        // the member is gone (kicked / left): close the panel
                        LaunchedEffect(menuTarget) { menuUid = null }
                    }
                }
            }

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

/** Big round channel photo with the orange pencil on its edge, and the "change" / "move" pills under it. */
@Composable
private fun PhotoBlock(
    bd: Backdrop,
    fg: Color,
    photo: Bitmap?,
    name: String,
    canEdit: Boolean,
    canMove: Boolean,
    onPick: () -> Unit,
    onMove: () -> Unit
) {
    val glass = Color(0xFF121212).copy(alpha = 0.40f)
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(124.dp)) {
            Avatar(
                photo = photo,
                name = name,
                size = 124.dp,
                modifier = Modifier
                    .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                    .then(if (canEdit) Modifier.clickable(onClick = onPick) else Modifier)
            )
            if (canEdit) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AppAccent.deep(Color(0xFFDB7608)))
                        .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                        .clickable(onClick = onPick),
                    contentAlignment = Alignment.Center
                ) { GlyphIcon(R.drawable.ic_edit, Color.White, 20.dp) }
            }
        }
        if (canEdit) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassSurface(
                    backdrop = bd,
                    container = glass,
                    modifier = Modifier.height(40.dp),
                    onClick = onPick
                ) { ChipLabel(stringResource(R.string.channel_photo_change), R.drawable.ic_image) }
                if (canMove) {
                    GlassSurface(
                        backdrop = bd,
                        container = glass,
                        modifier = Modifier.height(40.dp),
                        onClick = onMove
                    ) { ChipLabel(stringResource(R.string.channel_photo_move), null) }
                }
            }
        }
    }
}

@Composable
private fun ChipLabel(text: String, icon: Int?) {
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            GlyphIcon(icon, Color.White, 18.dp)
            Spacer(Modifier.width(6.dp))
        }
        BasicText(text, style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
    }
}

/** One member: photo (tap = his profile), name, @username, and the round button at the far end (empty for now). */
@Composable
private fun MemberRow(
    uid: String,
    isOwnerRow: Boolean,
    isAdmin: Boolean,
    isMe: Boolean,
    showMenu: Boolean,
    onMenu: () -> Unit,
    myName: String,
    myPhoto: Bitmap?,
    bd: Backdrop,
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
    val name = info?.name ?: if (isMe) myName else ""
    val username = info?.username.orEmpty()
    val photo = (if (isMe) myPhoto else null) ?: UserPhotos.photos[uid]
    var bounds by remember { mutableStateOf(Rect.Zero) }

    Row(
        Modifier
            .fillMaxWidth()
            .liquidCard(bd, RoundedCornerShape(26.dp), dark)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // the photo: a tap opens the profile of this member (it grows out of the photo)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    name.ifBlank { "…" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                    style = TextStyle(color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                )
                // one small badge: owner, supervisor, or "you"
                val badge: Pair<Int, Color>? = when {
                    isOwnerRow -> R.string.channel_owner_badge to EditOrange
                    isAdmin -> R.string.channel_admin_badge to EditMint
                    isMe -> R.string.channel_you_badge to EditViolet
                    else -> null
                }
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    Badge(stringResource(badge.first), badge.second)
                }
            }
            if (username.isNotEmpty()) {
                BasicText(
                    "@$username",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = fg.copy(alpha = 0.6f), fontSize = 13.sp)
                )
            }
        }
        if (showMenu) {
            Spacer(Modifier.width(8.dp))
            // the options of this member (supervisor + permissions / kick / ban)
            GlassSurface(
                backdrop = bd,
                container = if (dark) Color(0xFF121212).copy(alpha = 0.40f) else Color.White.copy(alpha = 0.50f),
                modifier = Modifier.size(40.dp),
                onClick = onMenu
            ) { GlyphIcon(R.drawable.ic_settings, fg, 20.dp) }
        }
    }
}

/**
 * The options of one member: a glass panel over the page. A tap outside the panel (or Back) closes it.
 *  - the OWNER sees "Make supervisor" (a switch). When the member is a supervisor, the owner also sees what that supervisor
 *    may do: Ban members / Kick members / Edit the channel information, one switch each (all off for a new supervisor).
 *  - Kick and Ban are buttons that ask for a second tap. The owner has both; a supervisor only the ones the owner allowed
 *    him ([canKick], [canBan]).
 * The panel scrolls when it is taller than the screen.
 */
@Composable
private fun MemberMenu(
    uid: String,
    isMe: Boolean,
    myName: String,
    myPhoto: Bitmap?,
    isAdmin: Boolean,
    perms: Set<String>,
    isOwnerMe: Boolean,
    canKick: Boolean,
    canBan: Boolean,
    bd: Backdrop,
    dark: Boolean,
    fg: Color,
    onAdmin: (Boolean) -> Unit,
    onPerm: (String, Boolean) -> Unit,
    onKick: () -> Unit,
    onBan: () -> Unit,
    onDismiss: () -> Unit
) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 420f)) }
    // which red button waits for its second tap: 0 = none, 1 = kick, 2 = ban
    var confirm by remember { mutableStateOf(0) }

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

            // ---- kick / ban: the first tap asks, the second one does it
            if (canKick) {
                DangerButton(
                    text = stringResource(R.string.member_menu_kick),
                    confirmText = stringResource(R.string.member_menu_kick_confirm),
                    confirming = confirm == 1,
                    onClick = { if (confirm == 1) onKick() else confirm = 1 }
                )
            }
            if (canKick && canBan) Spacer(Modifier.height(8.dp))
            if (canBan) {
                DangerButton(
                    text = stringResource(R.string.member_menu_ban),
                    confirmText = stringResource(R.string.member_menu_ban_confirm),
                    confirming = confirm == 2,
                    onClick = { if (confirm == 2) onBan() else confirm = 2 }
                )
            }
        }
    }
}

/** A red button that asks for a second tap: the first tap turns it solid red and shows [confirmText]. */
@Composable
private fun DangerButton(text: String, confirmText: String, confirming: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(EditRed.copy(alpha = if (confirming) 0.95f else 0.18f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            if (confirming) confirmText else text,
            style = TextStyle(
                color = if (confirming) Color.White else EditRed,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

/** One banned person: photo (tap = his profile), name, @username and the button that lifts the ban. */
@Composable
private fun BannedRow(
    uid: String,
    onUnban: () -> Unit,
    bd: Backdrop,
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

    Row(
        Modifier
            .fillMaxWidth()
            .liquidCard(bd, RoundedCornerShape(26.dp), dark)
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
                    style = TextStyle(color = fg.copy(alpha = 0.6f), fontSize = 13.sp)
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        GlassSurface(
            backdrop = bd,
            container = Color(0xFF121212).copy(alpha = if (busy) 0.25f else 0.40f),
            modifier = Modifier.height(40.dp),
            onClick = {
                if (!busy) {
                    busy = true
                    onUnban()
                }
            }
        ) { ChipLabel(stringResource(R.string.channel_unban), null) }
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
