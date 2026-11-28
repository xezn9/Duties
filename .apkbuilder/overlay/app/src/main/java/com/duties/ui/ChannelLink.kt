package com.duties.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import com.duties.R
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidDialog

/*
 * ChannelLink.kt — what happens when a channel link (https://<worker>/c/<id>, see DeepLink.kt) is opened.
 * MainScreen reads the channel and decides:
 *   already inside            -> its chat opens
 *   public                    -> "Join this channel?"  (Join adds me and opens the chat)
 *   private                   -> a note: by invitation only
 *   I am banned from it       -> a note
 *   there is no such channel  -> a note
 */

internal const val LINK_JOIN = 0
internal const val LINK_PRIVATE = 1
internal const val LINK_BANNED = 2
internal const val LINK_MISSING = 3

/** The question / note that is shown for an opened link. [channel] is null for LINK_MISSING. */
internal class LinkRequest(val channel: Channel?, val kind: Int)

/**
 * The dialog of an opened link (the same LiquidDialog as the delete / leave question). Place it OUTSIDE the box that is
 * recorded into [backdrop] and over the whole screen, like [ChannelConfirmDialog].
 */
@Composable
internal fun ChannelLinkDialog(
    request: LinkRequest?,
    dark: Boolean,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onJoin: (Channel) -> Unit
) {
    // keep the last request so the text does not change while the dialog fades out
    var shown by remember { mutableStateOf<LinkRequest?>(null) }
    if (request != null) shown = request
    val r = shown
    val name = r?.channel?.name ?: ""
    val kind = r?.kind ?: LINK_MISSING

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        val title = stringResource(
            when (kind) {
                LINK_JOIN -> R.string.link_join_title
                LINK_PRIVATE -> R.string.link_private_title
                LINK_BANNED -> R.string.link_banned_title
                else -> R.string.link_missing_title
            }
        )
        val message = when (kind) {
            LINK_JOIN -> stringResource(R.string.link_join_msg, name)
            LINK_PRIVATE -> stringResource(R.string.link_private_msg, name)
            LINK_BANNED -> stringResource(R.string.link_banned_msg, name)
            else -> stringResource(R.string.link_missing_msg)
        }
        LiquidDialog(
            visible = request != null,
            onDismiss = onDismiss,
            backdrop = backdrop,
            dark = dark,
            title = title,
            message = message,
            confirmText = stringResource(if (kind == LINK_JOIN) R.string.link_join_yes else R.string.link_ok),
            dismissText = stringResource(R.string.home_confirm_cancel),
            onConfirm = {
                val ch = r?.channel
                if (kind == LINK_JOIN && ch != null) onJoin(ch) else onDismiss()
            }
        )
    }
}
