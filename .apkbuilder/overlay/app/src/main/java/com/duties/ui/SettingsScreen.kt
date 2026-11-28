package com.duties.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duties.R
import com.duties.signOut
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidDialog
import kotlinx.coroutines.launch

private val LogoutRedDark = Color(0xFFFF453A)
private val LogoutRedLight = Color(0xFFE5342B)
private val ThemeTintDark = Color(0xFF64D2FF)
private val ThemeTintLight = Color(0xFF0A84FF)
private val NotifTintDark = Color(0xFFFFB340)
private val NotifTintLight = Color(0xFFFF9500)


/**
 * Settings page. It is opened with PushStack from the menu button of the Account tab, so it grows out of
 * that button and its parts come out of the same point (Emerge). It has the same black dotted background
 * as the Home tab (the dots light up under the finger, drift and ripple when the page opens).
 *
 * Two options: "App theme" (opens the glass page, which grows out of its row) and sign out (asks for
 * confirmation with the liquid glass dialog).
 */
@Composable
fun SettingsScreen(profile: ProfileState, dark: Boolean = true, onClose: () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val scope = rememberCoroutineScope()
        // a second PushStack inside the settings page: the "App theme" row grows into the theme page
        val push = remember { PushController(scope) }
        PushStack(
            controller = push,
            modifier = Modifier.fillMaxSize(),
            edgeLine = false,
            growFromSource = true,
            detail = { ThemeScreen(dark, onClose = { push.close() }) }
        ) {
            SettingsMain(dark, onClose = onClose, onTheme = { from -> push.open("theme", from) })
        }
    }
}

@Composable
private fun SettingsMain(dark: Boolean, onClose: () -> Unit, onTheme: (Rect?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fg = if (dark) Color.White else Color(0xFF101114)
    val red = if (dark) LogoutRedDark else LogoutRedLight
    val themeTint = AppAccent.pick(if (dark) ThemeTintDark else ThemeTintLight)

    // Records the WHOLE page, so the dialog's glass refracts everything behind it.
    val screenBackdrop = rememberLayerBackdrop()
    // What the glass of the page refracts: only the dotted background (like the Home tab).
    val glassBackdrop = rememberLayerBackdrop()
    val dots = rememberDotsState()
    val origin = LocalPushOrigin.current
    // the ring of lit dots opens from the button that opened the page
    LaunchedEffect(Unit) { dots.playWave(origin) }

    var showLogout by remember { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }
    // the notifications card (liquid morph of the bell button); back closes it
    var notifOpen by remember { mutableStateOf(false) }
    // faithful = the drop / bud keep the exact proportions of the reference video (the bell is a circle, not a pill)
    val notifMorph = remember { SearchMorph(faithful = true, slowdown = STICKY_MORPH_SLOWDOWN) }
    // the three on/off states are read here, while the page opens, NOT when the card is composed: those system calls
    // would otherwise land in the first (heaviest) frame of the morph and make it skip its first frames
    val notifAccess = rememberNotifAccess()
    BackHandler(enabled = notifOpen) { notifOpen = false }
    val themeRow = remember { arrayOfNulls<LayoutCoordinates>(1) }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(screenBackdrop).dotsTouch(dots)) {
            Box(Modifier.fillMaxSize().layerBackdrop(glassBackdrop)) {
                DotBackground(dots, dark)
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                // ---- top bar: close + title
                Emerge(delayMs = 40, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        MenuGlassButton(
                            backdrop = glassBackdrop,
                            dark = dark,
                            modifier = Modifier.size(48.dp),
                            horizontalPadding = 0.dp,
                            onClick = onClose
                        ) { GlyphIcon(R.drawable.ic_close, menuInk(dark), 22.dp) }
                        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                            BasicText(
                                stringResource(R.string.settings_title),
                                style = TextStyle(color = fg, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            )
                            BasicText(
                                stringResource(R.string.settings_subtitle),
                                style = TextStyle(color = fg.copy(alpha = 0.6f), fontSize = 12.sp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))

                // ---- App theme (opens the glass page)
                Emerge(delayMs = 110, modifier = Modifier.fillMaxWidth()) {
                    SettingsRow(
                        bd = glassBackdrop,
                        dark = dark,
                        icon = R.drawable.ic_palette,
                        tint = themeTint,
                        title = stringResource(R.string.theme_title),
                        subtitle = stringResource(R.string.theme_row_hint),
                        titleColor = fg,
                        subtitleColor = fg.copy(alpha = 0.6f),
                        chevron = true,
                        modifier = Modifier.onGloballyPositioned { themeRow[0] = it }
                    ) {
                        val c = themeRow[0]
                        onTheme(if (c != null && c.isAttached) c.boundsInRoot() else null)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ---- sign out (the notifications are now the small bell button of the top bar, see below)
                Emerge(delayMs = 200, modifier = Modifier.fillMaxWidth()) {
                    SettingsRow(
                        bd = glassBackdrop,
                        dark = dark,
                        icon = R.drawable.ic_logout,
                        tint = red,
                        title = stringResource(R.string.logout),
                        subtitle = null,
                        titleColor = red,
                        subtitleColor = Color.Unspecified,
                        chevron = false
                    ) { showLogout = true }
                }
            }
        }

        // ---- notifications: a small liquid glass button on the LEFT of the top bar. It plays the same morph as the
        // search button of Home: the card (permissions, battery, links) is born from it and the bell turns into the X.
        // The card grows at the place of the button (the button turns into it, like the original LiquidMenuMorph);
        // its outer edge and its top are the ones of the button, so it keeps the same margin as the page (16dp).
        MorphPanel(
            visible = notifOpen,
            dark = dark,
            morph = notifMorph,
            icon = R.drawable.ic_notifications,
            mirrored = true,
            inPlace = true,
            // the card is sticky liquid glass: it refracts the WHOLE page (the rows below it too), like the dialog
            glass = screenBackdrop,
            onDismiss = { notifOpen = false }
        ) { NotificationsCard(dark, notifAccess, onClose = { notifOpen = false }) }
        HomeSearchButton(
            open = notifOpen,
            dark = dark,
            backdrop = glassBackdrop,
            morph = notifMorph,
            onClick = { notifOpen = !notifOpen },
            delayMs = 160,
            icon = R.drawable.ic_notifications,
            mirrored = true,
            // like the original animation: the button itself turns into the card (it does not come back as an X)
            inPlace = true,
            // this page is laid out right-to-left, so "end" is the left side (the close button is at the start = right)
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 10.dp, end = 16.dp)
        )

        // The dialog is OUTSIDE the box that is recorded (as the LiquidDialog README says).
        LiquidDialog(
            visible = showLogout,
            onDismiss = { showLogout = false },
            backdrop = screenBackdrop,
            dark = dark,
            title = stringResource(R.string.logout_title),
            message = stringResource(R.string.logout_message),
            confirmText = stringResource(R.string.logout),
            dismissText = stringResource(R.string.cancel),
            confirmTint = Color(0xFFFF3B30),
            onConfirm = {
                if (!signingOut) {
                    signingOut = true
                    showLogout = false
                    // Firebase sign-out -> the auth listener in DutiesApp brings the login screen.
                    scope.launch { signOut(context.applicationContext) }
                }
            }
        )
    }
}

/** One option of the settings: colored icon, title (+ small line under it), optional chevron. */
@Composable
private fun SettingsRow(
    bd: Backdrop,
    dark: Boolean,
    icon: Int,
    tint: Color,
    title: String,
    subtitle: String?,
    titleColor: Color,
    subtitleColor: Color,
    chevron: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(26.dp)
    Row(
        modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick)
            .liquidCard(bd, shape, dark)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { GlyphIcon(icon, tint, 22.dp) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                title,
                style = TextStyle(color = titleColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            )
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                BasicText(subtitle, style = TextStyle(color = subtitleColor, fontSize = 12.sp))
            }
        }
        if (chevron) GlyphIcon(R.drawable.ic_chevron_left, titleColor.copy(alpha = 0.45f), 22.dp)
    }
}
