package com.duties.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.sp
import com.duties.Push
import com.duties.R
import com.google.firebase.auth.FirebaseAuth
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val TAB_TITLES = listOf(R.string.tab_home, R.string.tab_create, R.string.tab_account)
private val TAB_ICONS = listOf(R.drawable.ic_tab_home, R.drawable.ic_tab_create, R.drawable.ic_tab_account)

/** The 3 tabs (Home, Create, Account) with the liquid glass bar and the HyperSpring wave between tabs. */
@Composable
fun MainScreen(dark: Boolean, introDelayMs: Int = 200) {
    val backdrop = rememberLayerBackdrop()
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // the Create guide was read and folded into its small button (stays folded when you come back to the tab).
    // "Read" is saved on the phone (per account), so it is still read after the app is closed and opened again.
    val context = LocalContext.current
    val rulesPrefs = remember { context.getSharedPreferences("duties_state", Context.MODE_PRIVATE) }
    val rulesKey = remember { "rules_read_" + (FirebaseAuth.getInstance().currentUser?.uid ?: "local") }
    var rulesFolded by remember { mutableStateOf(rulesPrefs.getBoolean(rulesKey, false)) }
    // Center of each tab button: the wave starts from the tab you tapped.
    val centers = remember { mutableMapOf<Int, Offset>() }
    val contentColor = if (dark) Color.White else Color.Black

    // Opening animation: the tab bar rises from the bottom with a soft spring (the pages use the cascade).
    val intro = remember { introDelayMs }
    val barSlide = remember { Animatable(1f) }
    val barSlidePx = with(LocalDensity.current) { 150.dp.toPx() }
    LaunchedEffect(Unit) {
        delay((intro / 2).toLong())
        barSlide.animateTo(0f, spring(dampingRatio = 0.74f, stiffness = 170f))
    }

    // Profile (name, @username, bio, photos) and the edit page that opens from the pencil button.
    val user = remember { FirebaseAuth.getInstance().currentUser }
    val profile = rememberProfileState(user?.uid ?: "local", user?.displayName, user?.email)
    // channels of this account (Firestore): created in the Create tab, shown as a chat list in Home
    val channels = remember { ChannelsState(user?.uid ?: "local") }
    DisposableEffect(channels) {
        if (user != null) channels.start()
        onDispose { channels.stop() }
    }
    // delete / leave a channel: run here (not inside the Home page) so they finish even if the tab is left meanwhile
    val channelFailText = stringResource(R.string.error_save)
    val deleteChannel: (Channel) -> Unit = { ch ->
        scope.launch {
            runCatching { channels.deleteChannel(ch) }
                .onFailure { Toast.makeText(context, channelFailText, Toast.LENGTH_LONG).show() }
        }
    }
    val leaveChannel: (Channel) -> Unit = { ch ->
        scope.launch {
            runCatching { channels.leaveChannel(ch) }
                .onFailure { Toast.makeText(context, channelFailText, Toast.LENGTH_LONG).show() }
        }
    }
    // HyperSpring PushMorph: the page below dissolves, the pencil button grows into the edit page and
    // its parts come out of that point. edgeLine = false -> no thin light line around the growing page.
    val push = remember { PushController(scope) }
    // banner / profile photo shown full screen (null = closed); it sits above the tab bar
    var viewImage by remember { mutableStateOf<ViewedImage?>(null) }
    // delete / leave channel confirmation (LiquidDialog): lives here so its scrim and glass cover the whole screen
    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }
    // the chat that grows out of the tapped channel row (see ChatScreen.kt). chatOpen = false plays the way back,
    // and the chat is removed (chat = null) only when it has shrunk into its row again.
    var chat by remember { mutableStateOf<ChatTarget?>(null) }
    var chatOpen by remember { mutableStateOf(false) }
    // unread counters of the Home rows: one light listener per channel, stopped when the screen leaves
    val channelIds = channels.channels.map { it.id }
    DisposableEffect(channelIds) {
        if (user != null) ChatUnread.watch(context, user.uid, channelIds)
        onDispose { }
    }
    DisposableEffect(Unit) { onDispose { ChatUnread.stopAll() } }
    // the chat that is open counts as read (also the messages that arrive while it is open)
    LaunchedEffect(chat?.channel?.id, chatOpen) {
        ChatUnread.setOpen(if (chatOpen) chat?.channel?.id else null)
        // its push notification (if there is one) is not needed any more
        if (chatOpen) chat?.channel?.id?.let { Push.clear(context, it) }
    }
    // a tap on a push notification opens the chat of that channel (waits until the channel list is loaded)
    val wantedChannel = Push.openChannelId
    LaunchedEffect(wantedChannel, channels.channels) {
        val id = wantedChannel ?: return@LaunchedEffect
        val ch = channels.channels.firstOrNull { it.id == id } ?: return@LaunchedEffect
        if (chat == null) {
            chat = ChatTarget(ch, Rect.Zero) { null }
            chatOpen = true
        }
        Push.openChannelId = null
    }
    // the owner keeps the chat rooms (Realtime Database) in step with the channels (new members, old channels)
    LaunchedEffect(channels.channels) {
        if (user != null) ChatRooms.sync(user.uid, channels.channels)
        // the saved messages of every channel are loaded into memory: a chat opens with its messages already there
        ChatCache.preload(context, channels.channels.map { it.id })
    }
    // the channel was deleted (by its owner, from another phone) while its chat is open: close the chat
    LaunchedEffect(channels.channels, chat) {
        val c = chat
        if (c != null && channels.channels.none { it.id == c.channel.id }) chatOpen = false
    }

    PushStack(
        controller = push,
        // what shows through while the page below dissolves (settings / edit opening): the pages' own black / light
        // base, never the window background (a dark blue in the night theme)
        modifier = Modifier.fillMaxSize().background(if (dark) Color.Black else Color(0xFFF4F5F9)),
        edgeLine = false,
        growFromSource = true,
        detail = { page ->
            if (page == "settings") SettingsScreen(profile, dark, onClose = { push.close() })
            else EditProfileScreen(profile, dark, onClose = { push.close() })
        }
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                TabRevealHost(
                    selectedTab = tab,
                    tabCenter = { centers[it] },
                    modifier = Modifier.fillMaxSize(),
                    firstRevealBase = intro
                ) { index, revealBase ->
                    TabPage(
                        index, revealBase, dark, profile,
                        // the tab that was tapped = where the circle (and the dots wave) starts; 90 = a tab switch
                        waveFrom = if (revealBase == 90) centers[index] else null,
                        onEdit = { from -> push.open("edit", from) },
                        onSettings = { from -> push.open("settings", from, circular = true) },
                        onViewImage = { viewImage = it },
                        channels = channels,
                        onConfirmRequest = { confirm = it },
                        hiddenChannelId = null,
                        onOpenChat = { ch, from, now ->
                            if (chat == null) {
                                chat = ChatTarget(ch, from, now)
                                chatOpen = true
                            }
                        },
                        rulesFolded = rulesFolded,
                        onRulesFolded = {
                            rulesFolded = it
                            // only "read" is saved: opening the rules again to look at them does not make them unread
                            if (it) rulesPrefs.edit().putBoolean(rulesKey, true).apply()
                        }
                    )
                }
            }

            LiquidBottomTabs(
                selectedTabIndex = { tab },
                onTabSelected = { tab = it },
                backdrop = backdrop,
                tabsCount = 3,
                darkTheme = dark,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 28.dp, vertical = 14.dp)
                    .offset { IntOffset(0, (barSlide.value * barSlidePx).roundToInt()) }
            ) {
                repeat(3) { i ->
                    LiquidBottomTab(
                        onClick = { tab = i },
                        modifier = Modifier.onGloballyPositioned {
                            centers[i] = it.boundsInRoot().center
                        }
                    ) {
                        Image(
                            painter = painterResource(TAB_ICONS[i]),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            colorFilter = ColorFilter.tint(contentColor)
                        )
                        BasicText(
                            stringResource(TAB_TITLES[i]),
                            style = TextStyle(
                                color = contentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }

            // the chat covers the whole screen, the tab bar included
            ChatMorph(
                target = chat,
                open = chatOpen,
                dark = dark,
                uid = user?.uid ?: "local",
                senderName = profile.name,
                myPhoto = profile.avatar.cropped,
                onClose = { chatOpen = false },
                onDismissed = { chat = null }
            )

            // outside the box that is recorded into `backdrop` (as the LiquidDialog README says)
            ChannelConfirmDialog(
                request = confirm,
                dark = dark,
                backdrop = backdrop,
                onDismiss = { confirm = null },
                onConfirm = { req ->
                    confirm = null
                    when (req.action) {
                        ChannelAction.Delete -> deleteChannel(req.channel)
                        ChannelAction.Leave -> leaveChannel(req.channel)
                        else -> Unit
                    }
                }
            )

            ImageViewerHost(viewImage) { viewImage = null }
        }
    }
}

@Composable
private fun TabPage(
    index: Int,
    revealBase: Int,
    dark: Boolean,
    profile: ProfileState,
    waveFrom: Offset?,
    onEdit: (Rect) -> Unit,
    onSettings: (Rect) -> Unit,
    onViewImage: (ViewedImage) -> Unit,
    channels: ChannelsState,
    onConfirmRequest: (ConfirmRequest) -> Unit,
    hiddenChannelId: String?,
    onOpenChat: (Channel, Rect, () -> Rect?) -> Unit,
    rulesFolded: Boolean,
    onRulesFolded: (Boolean) -> Unit
) {
    val textColor = if (dark) Color.White else Color(0xFF101114)
    val cascade = remember { Cascade(revealBase) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val saveError = stringResource(R.string.error_save)
    val failToast = { Toast.makeText(context, saveError, Toast.LENGTH_LONG).show() }

    // Account tab = the profile page (no big title, it has its own banner).
    if (index == 2) {
        AccountPage(
            profile, dark, cascade,
            waveFrom = waveFrom,
            onEdit = onEdit,
            onSettings = onSettings,
            onViewImage = onViewImage
        )
        return
    }

    // what the glass buttons of the Create page refract: only the page background (like on the Account page)
    val glassBackdrop = rememberLayerBackdrop()
    // black dotted background; its dots light up under the finger, drift and ripple when the page opens
    val dots = rememberDotsState()
    DotsEntranceWave(dots, delayMs = cascade.base, from = waveFrom)
    // scroll state of the Create form (null while the form is closed): the title rises together with it
    var formScroll by remember { mutableStateOf<ScrollState?>(null) }
    // Home: the channel search is open (it covers the channel list; back closes it)
    var searching by remember { mutableStateOf(false) }
    // clock of the liquid morph (search button -> search field), shared by the button and the search layer
    val searchMorph = remember { SearchMorph() }
    BackHandler(enabled = searching) { searching = false }
    // The title is drawn ABOVE the search layer (the card of the morph stays under "Home"), so it is composed after the
    // layer. It takes its place in the cascade first, and the page leaves room for it with a spacer of its height.
    val titleDelay = remember { cascade.take() }
    var titleH by remember { mutableIntStateOf(0) }
    Box(Modifier.fillMaxSize().dotsTouch(dots)) {
        Box(Modifier.fillMaxSize().layerBackdrop(glassBackdrop)) {
            DotBackground(dots, dark)
        }
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 20.dp)
        ) {
            // room for the title (drawn further down, above the search layer)
            Spacer(Modifier.height(with(LocalDensity.current) { titleH.toDp() }))
            // Create = the swipeable pages that explain how to create a channel (continues the tab's cascade).
            if (index == 1) {
                Spacer(Modifier.height(14.dp))
                CreatePage(
                    dark = dark,
                    cascade = cascade,
                    backdrop = glassBackdrop,
                    modifier = Modifier.weight(1f),
                    startCollapsed = rulesFolded,
                    onCollapsedChange = onRulesFolded,
                    onFormScroll = { formScroll = it },
                    onSearchUsers = { q -> channels.searchUsers(q) },
                    onSubmitChannel = { name, username, bio, photo, invitees, isPublic ->
                        channels.create(name, username, bio, photo, invitees, profile.name, profile.username, isPublic)
                    }
                )
            }
            // Home = the channels as a chat list
            if (index == 0) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.weight(1f).onGloballyPositioned { searchMorph.listBounds = it.boundsInRoot() }) {
                    val listAlpha by animateFloatAsState(
                        if (searching) 0f else 1f, tween(240, easing = FastOutSlowInEasing), label = "homeListAlpha"
                    )
                    HomePage(
                        dark = dark,
                        backdrop = glassBackdrop,
                        channels = channels.channels,
                        invites = channels.invites,
                        cascade = cascade,
                        onAccept = { inv -> scope.launch { runCatching { channels.accept(inv) }.onFailure { failToast() } } },
                        onDecline = { inv -> scope.launch { runCatching { channels.decline(inv) }.onFailure { failToast() } } },
                        onConfirmRequest = onConfirmRequest,
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = listAlpha },
                        hiddenChannelId = hiddenChannelId,
                        onOpenChat = onOpenChat
                    )
                }
            }
        }
        if (index == 0) {
            // the search layer (the field is born from the button like a drop): over the page, UNDER the button
            HomeSearchPanel(
                visible = searching,
                morph = searchMorph,
                dark = dark,
                backdrop = glassBackdrop,
                onSearch = { q -> channels.searchChannels(q) },
                myChannelIds = channels.channels.map { it.id }.toSet(),
                onDismiss = { searching = false },
                onJoin = { ch ->
                    val ok = runCatching { channels.join(ch) }.onFailure { failToast() }.isSuccess
                    if (ok) {
                        // wait (max 4 s) until the channel shows up in my list, otherwise the chat would be
                        // closed at once ("the channel is gone"), then close the search and open the chat
                        val mine = withTimeoutOrNull(4000) {
                            snapshotFlow { channels.channels.firstOrNull { it.id == ch.id } }.filterNotNull().first()
                        }
                        if (mine != null) {
                            searching = false
                            onOpenChat(mine, Rect.Zero) { null }
                        }
                    }
                    ok
                }
            )
        }
        // the page title, above the search layer and under the search button
        Box(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 20.dp)) {
            SplitTitle(
                text = stringResource(TAB_TITLES[index]),
                delayMs = titleDelay,
                color = textColor,
                fontSize = 40.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .onSizeChanged { titleH = it.height }
                    .graphicsLayer {
                        // part of the form: it goes up with it (1:1) and melts away like the rest of the form does
                        val s = (formScroll?.value ?: 0).toFloat()
                        translationY = -s
                        val k = (s / 90.dp.toPx()).coerceIn(0f, 1f)
                        alpha = 1f - k
                        renderEffect = blurEffect(12.dp.toPx() * k)
                    }
            )
        }
        if (index == 0) {
            // the liquid glass search button, always on the RIGHT of the screen (the title is on the left).
            // On a phone in Arabic the layout is mirrored, so "end" would be the left: the side is chosen by hand.
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            HomeSearchButton(
                open = searching,
                dark = dark,
                backdrop = glassBackdrop,
                morph = searchMorph,
                onClick = { searching = !searching },
                delayMs = cascade.base + 120,
                modifier = Modifier
                    .align(if (rtl) Alignment.TopStart else Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 24.dp)
                    .absolutePadding(right = 20.dp)
            )
        }
    }
}
