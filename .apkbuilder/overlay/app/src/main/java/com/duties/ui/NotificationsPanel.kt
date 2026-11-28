package com.duties.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.duties.PushConfig
import com.duties.R

/*
 * NotificationsPanel.kt — the content of the card that grows out of the small bell button of the settings page
 * (same liquid morph as the search, see MorphPanel in HomeSearch.kt).
 *
 * Three buttons. Each one leads the user to the system settings where the thing is turned on, and shows a check
 * mark when it is already on (it is checked again every time the user comes back to the app):
 *   1. notifications allowed for the app
 *   2. battery optimization off for the app (so the system does not kill the push service)
 *   3. the app opens the links of the push Worker ("Open by default")
 */

/** What is on / off right now, and how to open the settings page of each one. */
internal object NotifAccess {
    /** The host of the Worker (the same host is declared in the manifest). */
    private val workerHost: String? get() = runCatching { Uri.parse(PushConfig.WORKER_URL).host }.getOrNull()

    fun notificationsOn(c: Context): Boolean = NotificationManagerCompat.from(c).areNotificationsEnabled()

    fun batteryUnrestricted(c: Context): Boolean =
        runCatching { c.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(c.packageName) == true }
            .getOrDefault(false)

    /** Android 12+: the link handling of the app is allowed AND the Worker domain is verified or chosen by the user. */
    fun linksOn(c: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return false
        val host = workerHost ?: return false
        return runCatching {
            val state = c.getSystemService(DomainVerificationManager::class.java)
                ?.getDomainVerificationUserState(c.packageName) ?: return@runCatching false
            val s = state.hostToStateMap[host]
            state.isLinkHandlingAllowed &&
                (s == DomainVerificationUserState.DOMAIN_STATE_VERIFIED || s == DomainVerificationUserState.DOMAIN_STATE_SELECTED)
        }.getOrDefault(false)
    }

    private fun start(c: Context, vararg tries: Intent) {
        for (i in tries) {
            if (c !is Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { c.startActivity(i) }.isSuccess) return
        }
    }

    private fun details(c: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}"))

    fun openNotificationSettings(c: Context) = start(
        c,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, c.packageName),
        details(c)
    )

    fun openBatterySettings(c: Context) = start(
        c,
        // the small system dialog "let the app run in the background?" (needs REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${c.packageName}")),
        // otherwise the list of apps of the battery optimization
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        details(c)
    )

    fun openLinkSettings(c: Context) = start(
        c,
        if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:${c.packageName}"))
        else details(c),
        details(c)
    )
}

/** Runs [onResume] every time the user comes back to the app (e.g. from the system settings). */
@Composable
private fun OnResume(onResume: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(context) {
        var c: Context? = context
        while (c is ContextWrapper && c !is LifecycleOwner) c = c.baseContext
        val lifecycle = (c as? LifecycleOwner)?.lifecycle
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) onResume() }
        lifecycle?.addObserver(observer)
        onDispose { lifecycle?.removeObserver(observer) }
    }
}

private val GreenDark = Color(0xFF30D158)
private val GreenLight = Color(0xFF34C759)

/** The three on / off states. Read when the owner is created and again every time the user comes back to the app. */
internal class NotifAccessState(private val context: Context) {
    var notif by mutableStateOf(NotifAccess.notificationsOn(context))
        private set
    var battery by mutableStateOf(NotifAccess.batteryUnrestricted(context))
        private set
    var links by mutableStateOf(NotifAccess.linksOn(context))
        private set

    fun refresh() {
        notif = NotifAccess.notificationsOn(context)
        battery = NotifAccess.batteryUnrestricted(context)
        links = NotifAccess.linksOn(context)
    }
}

/** Create it OUTSIDE the card (e.g. in the settings page), so reading the system state never costs a frame of the morph. */
@Composable
internal fun rememberNotifAccess(): NotifAccessState {
    val context = LocalContext.current
    val state = remember(context) { NotifAccessState(context) }
    OnResume { state.refresh() }
    return state
}

/** What is inside the card: a title (the X of the button sits at its end) and the three buttons. */
@Composable
internal fun NotificationsCard(dark: Boolean, access: NotifAccessState, onClose: () -> Unit) {
    val context = LocalContext.current
    val textColor = if (dark) Color.White else Color(0xFF101114)
    val subColor = if (dark) Color(0xFFB0B3B8) else Color(0xFF55586A)

    val notif = access.notif
    val battery = access.battery
    val links = access.links

    Column(Modifier.fillMaxSize()) {
        // top row: [title] ... [X]. The card grows exactly where the bell button was (the button turned into the
        // card), so the 48dp slot at the end of this row is the place of that button: the tap on it (the button is
        // invisible but still there) and the tap on the X below both close the card.
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(start = 18.dp)) {
                BasicText(
                    stringResource(R.string.notifications_title),
                    maxLines = 1,
                    style = TextStyle(color = textColor, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                )
                BasicText(
                    stringResource(R.string.notif_panel_sub),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = subColor, fontSize = 11.sp)
                )
            }
            Box(
                Modifier.size(48.dp).padding(6.dp).clip(CircleShape).bounceClick(onClick = onClose)
                    .background(textColor.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) { GlyphIcon(R.drawable.ic_close, textColor, 18.dp) }
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().padding(start = CARD_INSET, end = CARD_INSET)) {
        PermRow(
            icon = R.drawable.ic_notifications,
            tint = if (dark) Color(0xFFFFB340) else Color(0xFFFF9500),
            title = stringResource(R.string.notif_perm_title),
            hint = stringResource(R.string.notif_perm_hint),
            on = notif, dark = dark, textColor = textColor, subColor = subColor
        ) { NotifAccess.openNotificationSettings(context) }
        Spacer(Modifier.height(10.dp))
        PermRow(
            icon = R.drawable.ic_battery,
            tint = if (dark) GreenDark else GreenLight,
            title = stringResource(R.string.notif_battery_title),
            hint = stringResource(R.string.notif_battery_hint),
            on = battery, dark = dark, textColor = textColor, subColor = subColor
        ) { NotifAccess.openBatterySettings(context) }
        Spacer(Modifier.height(10.dp))
        PermRow(
            icon = R.drawable.ic_link,
            tint = if (dark) Color(0xFF64D2FF) else Color(0xFF0A84FF),
            title = stringResource(R.string.notif_links_title),
            hint = stringResource(R.string.notif_links_hint),
            on = links, dark = dark, textColor = textColor, subColor = subColor
        ) { NotifAccess.openLinkSettings(context) }
        }
    }
}

/** One button: colored icon, title + hint, and at the end a green check (already on) or a chevron (tap to turn it on). */
@Composable
private fun PermRow(
    icon: Int,
    tint: Color,
    title: String,
    hint: String,
    on: Boolean,
    dark: Boolean,
    textColor: Color,
    subColor: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .background(textColor.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { GlyphIcon(icon, tint, 20.dp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = textColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            )
            Spacer(Modifier.height(2.dp))
            BasicText(
                hint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = subColor, fontSize = 11.sp, lineHeight = 14.sp)
            )
        }
        Spacer(Modifier.width(8.dp))
        if (on) {
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(if (dark) GreenDark else GreenLight),
                contentAlignment = Alignment.Center
            ) { GlyphIcon(R.drawable.ic_check, Color.White, 15.dp) }
        } else {
            GlyphIcon(R.drawable.ic_chevron_left, textColor.copy(alpha = 0.45f), 22.dp)
        }
    }
}
