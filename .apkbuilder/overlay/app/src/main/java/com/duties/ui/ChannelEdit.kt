package com.duties.ui

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.platform.LocalView
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
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
import androidx.compose.ui.text.style.TextDirection
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
import com.duties.DeepLink
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
 *   - under the members, three buttons (each opens a glass panel):
 *       Supervisors' permissions (owner only): every supervisor with his Ban / Kick / Edit switches.
 *       Banned      (those who may ban): the banned people, each with a button that lifts the ban.
 *       Restricted  (those who may ban): the restricted people, each with a button that lifts the restriction.
 *     A RESTRICTED member stays in the channel and can read it, but the list of members is hidden from him.
 *   - the "Owner" badge on the owner's card is a button: a tap opens the owner's account (his profile page).
 *
 * EDITING: name / @username / bio (and public <-> private, owner only) are changed in a CARD that the pencil button next to the
 * name turns into (MorphPanel + HomeSearchButton of HomeSearch.kt: the very same liquid morph as the bell button of the settings
 * page, nothing of the animation is rewritten). The card writes into the same fields as before; "Save" below saves them.
 *
 * LOOK (v3): a soft ROUND glow made from the channel photo behind the round photo, the photo, name / @username / bio shown right on
 * the page (no field cards), three small chips, the members as two-in-a-row cards, the three buttons above, and a wide Save button that slides
 * up from the bottom only when something was changed.
 *
 * OPENING: the page is opened with the HyperSpring push (PushStack in ChatScreen.kt, the same animation as the edit-account
 * page): the chat below dissolves, the name bar grows into the page and its parts come out of that point one after another.
 *
 * It is drawn inside the chat's sliding layer (see ChatScreen.kt), so it slides away together with the chat.
 */

private val EditOrange = Color(0xFFFF8A1F)
private val EditViolet = Color(0xFF8A5CFF)
private val EditMint = Color(0xFF00D4AA)
private val EditRed = Color(0xFFE5484D)
private val MEMBER_PHOTO = 44.dp

/** The round glow behind the channel photo: the visible disc, how much it is blurred, and the (bigger) box it is drawn in. */
private val GLOW_DISC = 230.dp
private val GLOW_BLUR = 26.dp
private val GLOW_BOX = 400.dp

/** The accent of the page: the app color the person chose (orange when none). Everything coloured here uses it. */
private val EditAccent: Color get() = AppAccent.pick(EditOrange)

/** The pieces of the page start rising this long after the page starts opening (the sheet is already growing). */
private const val INTRO_BASE_MS = 150L

private const val PANEL_NONE = 0
private const val PANEL_ADMINS = 1
private const val PANEL_BANNED = 2
private const val PANEL_RESTRICTED = 3

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
 * The channel page. It is the DETAIL page of a HyperSpring PushStack (see ChatMorph in ChatScreen.kt): the stack makes the
 * page grow out of the name bar and dissolves the chat below, and the parts of the page come out of that point (see [Rise]).
 * [onOpenProfile] opens the profile of a person (ChatMorph hosts that page, above this one).
 */
@Composable
internal fun ChannelEditPage(
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
        // public / private (the owner, and a supervisor with the "edit" permission, see firestore.rules)
        var isPublic by remember(channel.id) { mutableStateOf(channel.isPublic) }
        // the edit card (the pencil button turns into it, the same liquid morph as the bell button of the settings page)
        var editOpen by remember { mutableStateOf(false) }
        // opening at the speed of the video (1x), closing a bit slower so the sticky neck can be seen
        val editMorph = remember { SearchMorph(faithful = true, slowdown = STICKY_MORPH_SLOWDOWN, openSlowdown = 1f) }
        BackHandler(enabled = editOpen) {
            editOpen = false
            focus.clearFocus()
        }

        var showErrors by remember { mutableStateOf(false) }
        // the glass panel that is open: supervisors' permissions / banned / restricted (PANEL_NONE = none)
        var panel by remember { mutableStateOf(PANEL_NONE) }
        var saving by remember { mutableStateOf(false) }
        var saved by remember { mutableStateOf(false) }
        var usernameTaken by remember { mutableStateOf(false) }
        var cropping by remember { mutableStateOf<Pair<Bitmap, CropTransform?>?>(null) }
        // the member whose options panel is open (owner, or a supervisor that may kick / ban)
        var menuUid by remember { mutableStateOf<String?>(null) }
        BackHandler(enabled = menuUid != null) { menuUid = null }
        BackHandler(enabled = panel != PANEL_NONE) { panel = PANEL_NONE }
        // the "kick / ban this member?" question (a LiquidDialog). The three values stay while the dialog fades out
        var askShow by remember { mutableStateOf(false) }
        var askUid by remember { mutableStateOf("") }
        var askBan by remember { mutableStateOf(false) }

        // the pencil sits OUTSIDE the recorded box (above the page, like the bell of the settings), so it fades out while one of
        // the glass panels of the page is open (they dim everything that is under them)
        val pencilA by animateFloatAsState(
            if (menuUid != null || panel != PANEL_NONE) 0f else 1f, tween(160), label = "pencilFade"
        )

        val nameOk = isValidName(name)
        val userOk = isValidUsername(username)
        val typeChanged = canEdit && isPublic != channel.isPublic
        val changed = name.trim() != channel.name || username != channel.username || bio.trim() != channel.bio || photoChanged || typeChanged
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
                    channels.updateChannel(channel, name.trim(), username, bio.trim(), photo, photoChanged, if (typeChanged) isPublic else null)
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
        // owner, or a supervisor that may ban: restricts / un-restricts an ordinary member
        fun restrict(target: String, on: Boolean, closeMenu: Boolean) = act(closeMenu) { channels.restrictMember(channel, target, on) }

        val nameError = if (showErr && !nameOk) stringResource(R.string.error_name_empty) else null
        val userError = when {
            usernameTaken -> stringResource(R.string.error_username_taken)
            showErr && !userOk -> stringResource(R.string.error_username_short)
            else -> null
        }

        // Records the WHOLE page, so the dialog's glass refracts everything behind it.
        val screenBackdrop = rememberLayerBackdrop()
        // What the glass of the page refracts: only the dotted background (like Settings / Account).
        val glassBackdrop = rememberLayerBackdrop()
        val dots = rememberDotsState()

        Box(Modifier.fillMaxSize().background(if (dark) Color.Black else Color(0xFFF4F5F9))) {
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
                val restrictedList = if (canBan) channel.restricted else emptyList()
                // a restricted member only reads: the list of members is hidden from him (the owner / supervisors are never restricted)
                val restrictedMe = uid in channel.restricted && !isOwnerMe && uid !in channel.admins
                val coverH = topInset + 250.dp
                val memberPairs = remember(ordered) { ordered.chunked(2) }
                val membersLabel = stringResource(R.string.channel_members_header, ordered.size)
                val adminPermsTitle = stringResource(R.string.manage_admin_perms)
                val bannedTitle = stringResource(R.string.manage_banned)
                val restrictedTitle = stringResource(R.string.manage_restricted)
                val noneWord = stringResource(R.string.manage_none)
                // no side padding here: the cover goes edge to edge, the other items add their own side padding
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = bottomInset + 120.dp)
                ) {
                    // ---- the top: blurred cover made from the photo, the photo on its edge, then name / @username / bio
                    //      typed right on the page (no field cards), and three small chips
                    item(key = "hero") {
                        ChannelHero(
                            fg = fg,
                            accent = accent,
                            base = fadeBase,
                            coverHeight = coverH,
                            topInset = topInset,
                            title = stringResource(if (canEdit) R.string.channel_edit_title else R.string.channel_info_title),
                            photo = photo,
                            name = name,
                            username = username,
                            bio = bio,
                            canEdit = canEdit,
                            canMove = photoSource != null,
                            memberCount = ordered.size,
                            adminCount = channel.admins.size,
                            isPublic = isPublic,
                            nameError = nameError,
                            userError = userError,
                            animate = intro,
                            onPick = {
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            onMove = { photoSource?.let { cropping = it to photoTf } }
                        )
                    }

                    // ---- the members: a title, then cards two in a row
                    item(key = "tabs") {
                        Rise(
                            delayMs = 420,
                            animate = intro,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 14.dp)
                        ) {
                            BasicText(
                                membersLabel,
                                modifier = Modifier.padding(horizontal = 4.dp),
                                style = TextStyle(color = fg.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }

                    if (restrictedMe) {
                        // restricted: no list of members, only a note
                        item(key = "restrictedNote") {
                            Rise(delayMs = 480, animate = intro, modifier = Modifier.padding(horizontal = 16.dp)) {
                                RestrictedNote(rowFill, fg)
                            }
                        }
                    } else {
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
                                            isRestricted = memberUid in channel.restricted,
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

                        // ---- the three buttons under the members: supervisors' permissions / banned / restricted
                        if (isOwnerMe || canBan) {
                            item(key = "manage") {
                                val rows = buildList<ManageItem> {
                                    if (isOwnerMe) {
                                        add(ManageItem(PANEL_ADMINS, R.drawable.ic_settings, EditMint, adminPermsTitle, adminsWord(channel.admins.size)))
                                    }
                                    if (canBan) {
                                        add(ManageItem(PANEL_BANNED, R.drawable.ic_lock, EditRed, bannedTitle, if (bannedList.isEmpty()) noneWord else bannedList.size.toString()))
                                        add(ManageItem(PANEL_RESTRICTED, R.drawable.ic_mute, EditOrange, restrictedTitle, if (restrictedList.isEmpty()) noneWord else restrictedList.size.toString()))
                                    }
                                }
                                Rise(
                                    delayMs = 560 + minOf(memberPairs.size, 5) * 60,
                                    animate = intro,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
                                ) {
                                    ManageGroup(rows, rowFill, fg) { panel = it }
                                }
                            }
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
                            isRestricted = menuTarget in channel.restricted,
                            onRestrict = { restrict(menuTarget, menuTarget !in channel.restricted, true) },
                            onAskKick = { askUid = menuTarget; askBan = false; askShow = true },
                            onAskBan = { askUid = menuTarget; askBan = true; askShow = true },
                            onDismiss = { menuUid = null }
                        )
                    } else {
                        // the member is gone (kicked / left): close the panel
                        LaunchedEffect(menuTarget) { menuUid = null }
                    }
                }

                // ---- the glass panels of the three buttons under the members
                when (panel) {
                    PANEL_ADMINS -> if (isOwnerMe) {
                        ManagePanel(
                            title = adminPermsTitle,
                            hint = stringResource(R.string.manage_admins_hint),
                            bd = bd, dark = dark, fg = fg,
                            onDismiss = { panel = PANEL_NONE }
                        ) {
                            val sups = channel.admins.filter { it != channel.ownerUid }
                            if (sups.isEmpty()) {
                                EmptyNote(stringResource(R.string.manage_admins_empty), fg)
                            } else {
                                sups.forEachIndexed { i, a ->
                                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(fg.copy(alpha = 0.10f)))
                                    AdminPermsBlock(
                                        uid = a,
                                        perms = channel.adminPerms[a].orEmpty(),
                                        bd = bd, dark = dark, fg = fg,
                                        onPerm = { perm, on -> setPerm(a, perm, on) }
                                    )
                                }
                            }
                        }
                    }
                    PANEL_BANNED -> if (canBan) {
                        ManagePanel(
                            title = bannedTitle,
                            hint = stringResource(R.string.manage_banned_hint),
                            bd = bd, dark = dark, fg = fg,
                            onDismiss = { panel = PANEL_NONE }
                        ) {
                            if (bannedList.isEmpty()) {
                                EmptyNote(stringResource(R.string.manage_banned_empty), fg)
                            } else {
                                bannedList.forEachIndexed { index, bannedUid ->
                                    BannedRow(
                                        modifier = Modifier.padding(bottom = 2.dp),
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
                    }
                    PANEL_RESTRICTED -> if (canBan) {
                        ManagePanel(
                            title = restrictedTitle,
                            hint = stringResource(R.string.manage_restricted_hint),
                            bd = bd, dark = dark, fg = fg,
                            onDismiss = { panel = PANEL_NONE }
                        ) {
                            if (restrictedList.isEmpty()) {
                                EmptyNote(stringResource(R.string.manage_restricted_empty), fg)
                            } else {
                                restrictedList.forEachIndexed { index, rUid ->
                                    BannedRow(
                                        modifier = Modifier.padding(bottom = 2.dp),
                                        uid = rUid,
                                        onUnban = { restrict(rUid, false, false) },
                                        first = index == 0,
                                        last = index == restrictedList.lastIndex,
                                        dark = dark,
                                        fg = fg,
                                        onOpenProfile = onOpenProfile,
                                        tint = EditOrange,
                                        actionText = stringResource(R.string.channel_unrestrict)
                                    )
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }

            // ---- the edit card: the pencil button of the top turns into it (the same MorphPanel + HomeSearchButton as the
            //      bell button of the settings page, only the content is different). Outside the recorded box, like the dialog
            if (canEdit) {
                MorphPanel(
                    visible = editOpen,
                    dark = dark,
                    morph = editMorph,
                    icon = R.drawable.ic_edit,
                    mirrored = true,
                    inPlace = true,
                    // the card is sticky liquid glass: it refracts the WHOLE page
                    glass = screenBackdrop,
                    onDismiss = {
                        focus.clearFocus()
                        editOpen = false
                    }
                ) {
                    ChannelEditCard(
                        dark = dark,
                        fg = fg,
                        accent = accent,
                        bd = glassBackdrop,
                        name = name,
                        username = username,
                        bio = bio,
                        isPublic = isPublic,
                        link = DeepLink.channelUrl(channel.id),
                        nameError = nameError,
                        userError = userError,
                        usernameBad = username.length < USERNAME_MIN,
                        onName = { name = it.replace("\n", "").take(NAME_MAX) },
                        onUsername = { v ->
                            username = v.filter { it.isUsernameChar() }.take(USERNAME_MAX)
                            usernameTaken = false
                        },
                        onBio = { bio = it.take(BIO_MAX) },
                        onPublic = { isPublic = it },
                        onClose = {
                            focus.clearFocus()
                            editOpen = false
                        }
                    )
                }
            }

            // ---- the pencil: a fixed glass button in the top bar, at the opposite corner from the X (exactly where the bell of the
            //      settings page is). It is NOT inside the list any more, so nothing clips it, it does not scroll / move with the
            //      keyboard while the card opens or closes, and the morph always starts from / returns to the same place.
            if (canEdit) {
                HomeSearchButton(
                    open = editOpen,
                    dark = dark,
                    backdrop = glassBackdrop,
                    morph = editMorph,
                    onClick = {
                        if (pencilA > 0.5f) {
                            if (editOpen) focus.clearFocus()
                            editOpen = !editOpen
                        }
                    },
                    delayMs = 160,
                    icon = R.drawable.ic_edit,
                    // the button is on the LEFT side of the page: the card opens to the right of it
                    mirrored = true,
                    // like the bell of the settings: the button itself turns into the card
                    inPlace = true,
                    // this page is laid out right-to-left, so "end" is the left side (the X is at the start = right)
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 10.dp, end = 16.dp)
                        .graphicsLayer { alpha = pencilA }
                )
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
// The edit card: the pencil button turns into it
// =====================================================================

/**
 * What is inside the card that grows out of the pencil button (drawn by [MorphPanel], the same liquid morph as the notifications
 * card of the settings page, see NotificationsCard). Same layout as that card: a title (the X sits at its end, exactly where the
 * button was), then the fields: name, @username, bio, the public / private switch and the link of the channel (copy).
 * The fields change the state of the page directly; the page's "Save" button saves them.
 * When the keyboard (or the screen's edge) would cover the bottom of the card, the list inside it gets shorter and scrolls.
 */
@Composable
private fun ChannelEditCard(
    dark: Boolean,
    fg: Color,
    accent: Color,
    bd: Backdrop,
    name: String,
    username: String,
    bio: String,
    isPublic: Boolean,
    link: String,
    nameError: String?,
    userError: String?,
    usernameBad: Boolean,
    onName: (String) -> Unit,
    onUsername: (String) -> Unit,
    onBio: (String) -> Unit,
    onPublic: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val subColor = if (dark) Color(0xFFB0B3B8) else Color(0xFF55586A)
    val density = LocalDensity.current
    val view = LocalView.current
    // how much of the bottom of the list is under the keyboard / the navigation bar (the card has a fixed place and size)
    val imeBottom = WindowInsets.ime.getBottom(density)
    val navBottom = WindowInsets.navigationBars.getBottom(density)
    val listBox = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val listBottom = listBox[0]?.takeIf { it.isAttached }?.boundsInRoot()?.bottom ?: 0f
    val overlapPx = (listBottom - (view.rootView.height - maxOf(imeBottom, navBottom))).coerceAtLeast(0f)

    Column(Modifier.fillMaxSize()) {
        // top row: [title] ... [X]. The X is at the place of the pencil button (the button turned into the card)
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 18.dp)) {
                BasicText(
                    stringResource(R.string.channel_edit_title),
                    maxLines = 1,
                    style = TextStyle(color = fg, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                )
                BasicText(
                    stringResource(R.string.channel_edit_card_sub),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = subColor, fontSize = 11.sp)
                )
            }
            Box(
                Modifier.size(48.dp).padding(6.dp).clip(CircleShape).bounceClick(onClick = onClose)
                    .background(fg.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) { GlyphIcon(R.drawable.ic_close, fg, 18.dp) }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { listBox[0] = it }
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(bottom = with(density) { overlapPx.toDp() })
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = CARD_INSET)
            ) {
                CardField(
                    label = stringResource(R.string.create_field_name),
                    value = name,
                    onValueChange = onName,
                    maxLen = NAME_MAX,
                    fg = fg,
                    accent = accent,
                    keyboardType = KeyboardType.Text,
                    error = nameError
                )
                Spacer(Modifier.height(12.dp))
                CardField(
                    label = stringResource(R.string.create_field_username),
                    value = username,
                    onValueChange = onUsername,
                    maxLen = USERNAME_MAX,
                    fg = fg,
                    accent = accent,
                    keyboardType = KeyboardType.Ascii,
                    prefix = "@",
                    error = userError,
                    countBad = usernameBad
                )
                Spacer(Modifier.height(12.dp))
                CardField(
                    label = stringResource(R.string.create_win_bio_label),
                    value = bio,
                    onValueChange = onBio,
                    maxLen = BIO_MAX,
                    fg = fg,
                    accent = accent,
                    keyboardType = KeyboardType.Text,
                    hint = stringResource(R.string.create_win_bio_hint),
                    multiline = true,
                    imeAction = ImeAction.Default
                )
                // public <-> private (the owner, or a supervisor with the "edit" permission: the card only opens for them)
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(fg.copy(alpha = 0.08f))
                        .padding(horizontal = 14.dp)
                ) {
                    OptionSwitch(
                        title = stringResource(R.string.channel_type_toggle_title),
                        sub = stringResource(if (isPublic) R.string.channel_type_public_hint else R.string.channel_type_private_hint),
                        selected = isPublic,
                        onSelect = onPublic,
                        bd = bd, dark = dark, fg = fg
                    )
                }
                // the link of this channel (every channel has its own): tap = copy
                Spacer(Modifier.height(12.dp))
                ChannelLinkField(link = link, isPublic = isPublic, fg = fg, accent = accent)
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

/**
 * The link of the channel: a small label, then a soft rounded box with the link (always left-to-right) and a "copy" pill. A tap
 * anywhere on the box copies it. Anybody who opens the link on a phone with the app is asked to join the channel (a PUBLIC
 * channel); a private one only tells him that it is by invitation (see MainScreen).
 */
@Composable
private fun ChannelLinkField(link: String, isPublic: Boolean, fg: Color, accent: Color) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1600)
            copied = false
        }
    }
    val shape = RoundedCornerShape(23.dp)
    val pillColor by animateColorAsState(if (copied) Color(0xFF30D158) else accent, tween(200), label = "linkPill")
    Column(Modifier.fillMaxWidth()) {
        BasicText(
            stringResource(R.string.channel_link_label),
            modifier = Modifier.padding(horizontal = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = fg.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        )
        Spacer(Modifier.height(5.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(shape)
                .background(fg.copy(alpha = 0.08f))
                .border(1.dp, fg.copy(alpha = 0.14f), shape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    runCatching {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("channel link", link))
                    }
                    copied = true
                }
                .padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlyphIcon(R.drawable.ic_link, fg.copy(alpha = 0.6f), 18.dp)
            Spacer(Modifier.width(8.dp))
            BasicText(
                link,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(
                    color = fg.copy(alpha = 0.8f), fontSize = 12.sp,
                    textAlign = TextAlign.Left, textDirection = TextDirection.Ltr
                )
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(pillColor).padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    stringResource(if (copied) R.string.channel_link_copied else R.string.channel_link_copy),
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        BasicText(
            stringResource(if (isPublic) R.string.channel_link_public_hint else R.string.channel_link_private_hint),
            modifier = Modifier.padding(horizontal = 6.dp),
            style = TextStyle(color = fg.copy(alpha = 0.55f), fontSize = 11.5.sp, lineHeight = 16.sp)
        )
    }
}

/**
 * One field of the edit card: a small label (with the letter counter while it is typed on), then the soft rounded box with
 * the text. The line around the box takes the accent colour while the field is focused, and turns red when there is an error.
 */
@Composable
private fun CardField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    maxLen: Int,
    fg: Color,
    accent: Color,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    hint: String? = null,
    multiline: Boolean = false,
    error: String? = null,
    countBad: Boolean = false,
    imeAction: ImeAction = ImeAction.Next
) {
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(
        when {
            error != null -> EditRed.copy(alpha = 0.85f)
            focused -> accent
            else -> fg.copy(alpha = 0.14f)
        },
        tween(200), label = "cardFieldBorder"
    )
    val shape = RoundedCornerShape(if (multiline) 20.dp else 23.dp)

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                label,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = fg.copy(alpha = 0.75f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            )
            if (focused) {
                BasicText(
                    "${value.length}/$maxLen",
                    style = TextStyle(color = if (countBad) EditRed else fg.copy(alpha = 0.5f), fontSize = 11.sp)
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        // a field with a prefix (the @ of the username) is laid out left-to-right: the @ stays in front of the letters
        CompositionLocalProvider(
            LocalLayoutDirection provides if (prefix != null) LayoutDirection.Ltr else LocalLayoutDirection.current
        ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (multiline) 84.dp else 46.dp)
                .clip(shape)
                .background(fg.copy(alpha = 0.08f))
                .border(1.dp, border, shape)
                .padding(horizontal = 16.dp, vertical = if (multiline) 12.dp else 0.dp),
            verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically
        ) {
            if (prefix != null) {
                BasicText(prefix, style = TextStyle(color = fg.copy(alpha = 0.5f), fontSize = 15.sp))
                Spacer(Modifier.width(4.dp))
            }
            Box(Modifier.weight(1f)) {
                if (value.isEmpty() && hint != null) {
                    BasicText(hint, style = TextStyle(color = fg.copy(alpha = 0.35f), fontSize = 15.sp))
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = !multiline,
                    maxLines = if (multiline) 5 else 1,
                    textStyle = TextStyle(
                        color = fg,
                        fontSize = 15.sp,
                        fontWeight = if (multiline) FontWeight.Normal else FontWeight.SemiBold
                    ),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
                )
            }
        }
        }
        if (error != null) {
            Spacer(Modifier.height(3.dp))
            BasicText(
                error,
                modifier = Modifier.padding(horizontal = 6.dp),
                style = TextStyle(color = EditRed, fontSize = 12.sp)
            )
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

/**
 * An element that comes out of the point that was tapped (the name bar), like the parts of the edit-account page (the
 * HyperSpring Emerge): from small, blurred and shifted toward that point, to sharp in its place. [animate] = false shows it
 * at once, so rows that scroll in later do not replay it.
 */
@Composable
private fun Rise(delayMs: Int, animate: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val origin = LocalPushOrigin.current
    val a = remember { Animatable(if (animate) 0f else 1f) }
    var center by remember { mutableStateOf(Offset.Unspecified) }
    LaunchedEffect(Unit) {
        if (animate) {
            delay(delayMs.toLong() + 40L)
            a.animateTo(1f, tween(680, easing = SETTLE))
        }
    }
    Box(
        modifier
            .onGloballyPositioned { if (center == Offset.Unspecified) center = it.boundsInRoot().center }
            .graphicsLayer {
                val t = a.value
                val c = if (center == Offset.Unspecified) origin else center
                val maxDy = 180.dp.toPx()
                translationX = (origin.x - c.x) * 0.25f * (1f - t)
                translationY = ((origin.y - c.y) * 0.45f).coerceIn(-maxDy, maxDy) * (1f - t)
                alpha = minOf(1f, t * 1.8f)
                val sc = 0.84f + 0.16f * t
                scaleX = sc
                scaleY = sc
                renderEffect = if (t < 1f) blurEffect(16.dp.toPx() * (1f - t)) else null
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
 * The top of the page. The TITLE of the page sits at the very top and scrolls away together with everything else. Behind the
 * round channel photo sits a soft ROUND glow made from the same photo (blurred, its edge melts into the page). The glow belongs
 * to the photo: it moves with it, at the same speed. The photo has a small button on it that picks a new one (the photo is
 * tappable too). Under it the NAME, the @USERNAME and the BIO, each one centred on the page (they are changed in the edit card,
 * see [ChannelEditCard]; the pencil that opens it is a fixed button of the top bar). Then three small chips: type, members,
 * supervisors.
 */
@Composable
private fun ChannelHero(
    fg: Color,
    accent: Color,
    base: Color,
    coverHeight: Dp,
    topInset: Dp,
    title: String,
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
    animate: Boolean,
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
        // ---- the glow: the channel photo as a soft round blur behind the photo (a circle, not a square cover).
        //      The disc is blurred with a Decal edge, so its rim melts into the page; the box is bigger than the disc
        //      so the blur has room. It is centred on the photo and has NO scroll effect of its own: it rises with the photo.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .offset(y = coverHeight - GLOW_BOX / 2)
                .requiredSize(GLOW_BOX)
                .graphicsLayer {
                    alpha = appear.value
                    scaleX = pop.value
                    scaleY = pop.value
                    renderEffect = blurEffect(GLOW_BLUR.toPx())
                },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(GLOW_DISC).clip(CircleShape)) {
                if (cover != null) {
                    Image(
                        bitmap = cover,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(accent.copy(alpha = 0.45f)))
                }
            }
        }

        // ---- the title of the page: part of the page (it scrolls up with it), on the same row as the X and the pencil
        Rise(
            delayMs = 0,
            animate = animate,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = topInset + 10.dp, start = 72.dp, end = 72.dp)
                .height(48.dp)
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BasicText(
                    title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                )
            }
        }

        // ---- the photo, and everything under it
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
            // the name: alone on its row, so it is exactly in the middle of the page
            Rise(delayMs = 120, animate = animate, modifier = Modifier.fillMaxWidth()) {
                HeroText(
                    text = name,
                    fg = fg,
                    style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    error = nameError
                )
            }
            Spacer(Modifier.height(6.dp))
            // the @username: always drawn left-to-right, so the @ stays in front of the letters
            Rise(delayMs = 160, animate = animate, modifier = Modifier.fillMaxWidth()) {
                HeroText(
                    text = "@$username",
                    fg = fg,
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    ltr = true,
                    error = userError
                )
            }
            if (canEdit || bio.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Rise(delayMs = 200, animate = animate, modifier = Modifier.fillMaxWidth()) {
                    HeroText(
                        text = bio,
                        fg = fg,
                        style = TextStyle(fontSize = 15.sp),
                        maxLines = 5,
                        hint = if (canEdit) stringResource(R.string.create_win_bio_hint) else null
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
 * Text shown right on the page: centred in the full width, no box (it is changed in the edit card). [ltr] = a text that is
 * always left-to-right (the @username: in a right-to-left page the @ would otherwise jump to the wrong end). An empty text shows
 * the [hint], if there is one. An [error] is shown under it, in red.
 */
@Composable
private fun HeroText(
    text: String,
    fg: Color,
    style: TextStyle,
    maxLines: Int,
    modifier: Modifier = Modifier,
    ltr: Boolean = false,
    hint: String? = null,
    error: String? = null
) {
    val centered = style.copy(
        textAlign = TextAlign.Center,
        textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content
    )
    Column(
        modifier.fillMaxWidth().animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (text.isEmpty() && hint != null) {
            BasicText(hint, modifier = Modifier.fillMaxWidth(), style = centered.copy(color = fg.copy(alpha = 0.3f)))
        } else {
            BasicText(
                text,
                modifier = Modifier.fillMaxWidth(),
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
                style = centered.copy(color = if (ltr) fg.copy(alpha = 0.6f) else fg)
            )
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
    isRestricted: Boolean,
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
    val restrictedWord = stringResource(R.string.channel_restricted_badge)
    val badge: Pair<String, Color>? = when {
        isOwnerRow -> ownerWord to EditOrange
        isAdmin -> (if (adminTitle.isBlank()) adminWord else adminTitle) to EditMint
        isRestricted -> restrictedWord to EditRed
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
                style = TextStyle(
                    color = fg.copy(alpha = 0.55f), fontSize = 12.sp,
                    textAlign = TextAlign.Center, textDirection = TextDirection.Ltr
                )
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.height(28.dp), contentAlignment = Alignment.Center) {
                if (badge != null) {
                    // the OWNER badge is a button: a tap opens the owner's account (it grows out of the badge)
                    val ownerTap: ((Rect) -> Unit)? =
                        if (isOwnerRow) ({ r: Rect -> onOpenProfile(ProfileTarget(uid, name, photo, r)) }) else null
                    Badge(badge.first, badge.second, onClick = ownerTap)
                }
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
    isRestricted: Boolean,
    onRestrict: () -> Unit,
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
                            modifier = Modifier.fillMaxWidth(),
                            style = TextStyle(
                                color = fg.copy(alpha = 0.6f), fontSize = 13.sp,
                                textAlign = TextAlign.Right, textDirection = TextDirection.Ltr
                            )
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

            // ---- restrict / lift the restriction (ordinary members only; whoever may ban). No question: it can be undone
            if (canBan && !isAdmin) {
                DangerButton(
                    text = stringResource(if (isRestricted) R.string.member_menu_unrestrict else R.string.member_menu_restrict),
                    tint = EditOrange,
                    onClick = onRestrict
                )
                Spacer(Modifier.height(8.dp))
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

/** A soft coloured button (red = kick / ban, which open the question dialog; orange = restrict, which acts at once). */
@Composable
private fun DangerButton(text: String, tint: Color = EditRed, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(tint.copy(alpha = 0.18f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text,
            style = TextStyle(color = tint, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
    onOpenProfile: (ProfileTarget) -> Unit,
    tint: Color = EditRed,
    actionText: String = ""
) {
    val context = LocalContext.current
    LaunchedEffect(uid) {
        MemberDirectory.request(uid)
        UserPhotos.request(context, uid)
    }
    val actionLabel = actionText.ifEmpty { stringResource(R.string.channel_unban) }
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
            .background(tint.copy(alpha = if (dark) 0.12f else 0.10f))
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
                        modifier = Modifier.fillMaxWidth(),
                            style = TextStyle(
                                color = fg.copy(alpha = 0.55f), fontSize = 13.sp,
                                textAlign = TextAlign.Right, textDirection = TextDirection.Ltr
                            )
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
                    actionLabel,
                    style = TextStyle(color = fg.copy(alpha = if (busy) 0.5f else 1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                )
            }
        }
        if (!last) {
            Box(Modifier.fillMaxWidth().padding(start = 72.dp).height(0.7.dp).background(tint.copy(alpha = 0.18f)))
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

/** A small pill. With [onClick] it is a button (it shrinks a little while pressed); the callback gets the pill's bounds. */
@Composable
private fun Badge(text: String, tint: Color, onClick: ((Rect) -> Unit)? = null) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "badgePress")
    Box(
        Modifier
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .onGloballyPositioned { bounds = it.boundsInRoot() }
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = if (onClick != null) 0.30f else 0.22f))
            .then(
                if (onClick != null) Modifier.clickable(interactionSource = source, indication = null) { onClick(bounds) }
                else Modifier
            )
            .padding(horizontal = if (onClick != null) 12.dp else 8.dp, vertical = if (onClick != null) 4.dp else 2.dp)
    ) {
        BasicText(text, maxLines = 1, style = TextStyle(color = tint, fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
    }
}

// =====================================================================
// The three buttons under the members + their glass panels
// =====================================================================

private class ManageItem(val panel: Int, val icon: Int, val tint: Color, val title: String, val sub: String)

/** The buttons in one group: only the first is round on top, only the last at the bottom (like the other grouped lists). */
@Composable
private fun ManageGroup(items: List<ManageItem>, fill: Color, fg: Color, onOpen: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        items.forEachIndexed { i, item ->
            ManageRow(item, fill, fg, first = i == 0, last = i == items.lastIndex) { onOpen(item.panel) }
            if (i != items.lastIndex) Spacer(Modifier.height(2.dp))
        }
    }
}

/** One button: coloured icon, title, a short line (a number or a word) and the arrow at the far end. */
@Composable
private fun ManageRow(item: ManageItem, fill: Color, fg: Color, first: Boolean, last: Boolean, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "manageRowPress")
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = press
                scaleY = press
            }
            .clip(groupShape(first, last))
            .background(fill)
            .clickable(interactionSource = source, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(item.tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { GlyphIcon(item.icon, item.tint, 20.dp) }
        Spacer(Modifier.width(12.dp))
        BasicText(
            item.title,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        )
        BasicText(
            item.sub,
            maxLines = 1,
            style = TextStyle(color = fg.copy(alpha = 0.5f), fontSize = 13.sp)
        )
        Spacer(Modifier.width(6.dp))
        GlyphIcon(R.drawable.ic_chevron_left, fg.copy(alpha = 0.4f), 18.dp)
    }
}

/**
 * A glass panel over the page (same look as the member options): a title, one line that explains it, then [content].
 * A tap outside the panel (or Back) closes it. It scrolls when it is taller than the screen.
 */
@Composable
private fun ManagePanel(
    title: String,
    hint: String,
    bd: Backdrop,
    dark: Boolean,
    fg: Color,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { a.animateTo(1f, spring(dampingRatio = 0.78f, stiffness = 420f)) }
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
            BasicText(title, style = TextStyle(color = fg, fontSize = 19.sp, fontWeight = FontWeight.Bold))
            Spacer(Modifier.height(3.dp))
            BasicText(hint, style = TextStyle(color = fg.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 17.sp))
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(fg.copy(alpha = 0.12f)))
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/** One supervisor inside the "supervisors' permissions" panel: who he is and his three switches (owner only). */
@Composable
private fun AdminPermsBlock(
    uid: String,
    perms: Set<String>,
    bd: Backdrop,
    dark: Boolean,
    fg: Color,
    onPerm: (String, Boolean) -> Unit
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

    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(photo, name, 40.dp)
            Spacer(Modifier.width(10.dp))
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
                        modifier = Modifier.fillMaxWidth(),
                            style = TextStyle(
                                color = fg.copy(alpha = 0.55f), fontSize = 12.sp,
                                textAlign = TextAlign.Right, textDirection = TextDirection.Ltr
                            )
                    )
                }
            }
        }
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
}

/** Shown to a RESTRICTED member instead of the members: he can read the channel, nothing more. */
@Composable
private fun RestrictedNote(fill: Color, fg: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(fill)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlyphIcon(R.drawable.ic_lock, EditOrange, 22.dp)
        Spacer(Modifier.width(12.dp))
        BasicText(
            stringResource(R.string.restricted_me_note),
            modifier = Modifier.weight(1f),
            style = TextStyle(color = fg.copy(alpha = 0.8f), fontSize = 14.sp, lineHeight = 20.sp)
        )
    }
}

/** A quiet line inside a panel that has nothing to show. */
@Composable
private fun EmptyNote(text: String, fg: Color) {
    BasicText(
        text,
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
        style = TextStyle(color = fg.copy(alpha = 0.55f), fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
    )
}
