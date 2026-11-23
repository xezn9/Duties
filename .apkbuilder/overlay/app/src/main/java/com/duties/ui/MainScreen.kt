package com.duties.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duties.R
import com.google.firebase.auth.FirebaseAuth
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val TAB_TITLES = listOf(R.string.tab_home, R.string.tab_create, R.string.tab_account)
private val TAB_ICONS = listOf(R.drawable.ic_tab_home, R.drawable.ic_tab_create, R.drawable.ic_tab_account)

/** The 3 tabs (Home, Create, Account) with the liquid glass bar and the HyperSpring wave between tabs. */
@Composable
fun MainScreen(dark: Boolean, introDelayMs: Int = 200) {
    val backdrop = rememberLayerBackdrop()
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
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
    // HyperSpring PushMorph: the page below dissolves, the pencil button grows into the edit page and
    // its parts come out of that point. edgeLine = false -> no thin light line around the growing page.
    val push = remember { PushController(scope) }
    // banner / profile photo shown full screen (null = closed); it sits above the tab bar
    var viewImage by remember { mutableStateOf<ViewedImage?>(null) }

    PushStack(
        controller = push,
        modifier = Modifier.fillMaxSize(),
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
                        onEdit = { from -> push.open("edit", from) },
                        onSettings = { from -> push.open("settings", from, circular = true) },
                        onViewImage = { viewImage = it }
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
    onEdit: (Rect) -> Unit,
    onSettings: (Rect) -> Unit,
    onViewImage: (ViewedImage) -> Unit
) {
    val textColor = if (dark) Color.White else Color(0xFF101114)
    val cascade = remember { Cascade(revealBase) }

    // Account tab = the profile page (no big title, it has its own banner).
    if (index == 2) {
        AccountPage(
            profile, dark, cascade,
            onEdit = onEdit,
            onSettings = onSettings,
            onViewImage = onViewImage
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        AppBackground(dark, variant = index)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            SplitTitle(
                text = stringResource(TAB_TITLES[index]),
                delayMs = remember { cascade.take() },
                color = textColor,
                fontSize = 40.sp,
                modifier = Modifier.fillMaxWidth()
            )
            // Home and Create stay empty for now.
        }
    }
}
