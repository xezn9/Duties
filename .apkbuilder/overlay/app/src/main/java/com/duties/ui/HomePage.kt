package com.duties.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duties.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Home tab: the channels of the account as a chat list (photo, name, last line, time).
 * The newest channel is on top. Empty for now when there are no channels.
 */
@Composable
fun HomePage(
    dark: Boolean,
    backdrop: Backdrop,
    channels: List<Channel>,
    invites: List<Invite>,
    cascade: Cascade,
    onAccept: (Invite) -> Unit,
    onDecline: (Invite) -> Unit,
    modifier: Modifier = Modifier
) {
    val textColor = if (dark) Color.White else Color(0xFF101114)
    val subColor = if (dark) Color(0xFFB0B3B8) else Color(0xFF55586A)
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Reveal(delayMs = remember { cascade.take() }, modifier = modifier.fillMaxWidth(), dy = 14.dp) {
            if (channels.isEmpty() && invites.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 60.dp), contentAlignment = Alignment.TopCenter) {
                    BasicText(
                        stringResource(R.string.home_empty),
                        style = TextStyle(color = subColor, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 130.dp + navBottom)
                ) {
                    if (invites.isNotEmpty()) {
                        item(key = "invites-title") {
                            BasicText(
                                stringResource(R.string.home_invites_title) + "  (" + invites.size + ")",
                                modifier = Modifier.animateItem().padding(horizontal = 24.dp, vertical = 6.dp),
                                style = TextStyle(color = subColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            )
                        }
                        items(invites, key = { "inv-" + it.id }) { inv ->
                            InviteCard(
                                inv, dark, backdrop, textColor, subColor,
                                onAccept = { onAccept(inv) },
                                onDecline = { onDecline(inv) },
                                modifier = Modifier.animateItem()
                            )
                        }
                        if (channels.isNotEmpty()) {
                            item(key = "chats-title") {
                                BasicText(
                                    stringResource(R.string.home_channels_title),
                                    modifier = Modifier.animateItem().padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 2.dp),
                                    style = TextStyle(color = subColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                    items(channels, key = { "ch-" + it.id }) { ch ->
                        ChannelRow(ch, dark, textColor, subColor, Modifier.animateItem())
                    }
                }
            }
        }
    }
}

/** An invitation: who invited me to which channel, with Accept / Decline. */
@Composable
private fun InviteCard(
    inv: Invite,
    dark: Boolean,
    backdrop: Backdrop,
    textColor: Color,
    subColor: Color,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .liquidCard(backdrop, shape, dark)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(inv.photo, inv.channelName, 54.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                BasicText(
                    stringResource(R.string.home_invite_label),
                    style = TextStyle(color = Color(0xFFFF9A2E), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    inv.channelName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    stringResource(R.string.home_invite_from, inv.fromUsername.ifBlank { inv.fromName }),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = subColor, fontSize = 13.sp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LiquidButton(
                onClick = onAccept,
                backdrop = backdrop,
                tint = Color(0xFFDB7608),
                modifier = Modifier.weight(1f).height(46.dp)
            ) {
                BasicText(
                    stringResource(R.string.home_invite_accept),
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                )
            }
            LiquidButton(
                onClick = onDecline,
                backdrop = backdrop,
                surfaceColor = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.30f),
                modifier = Modifier.weight(1f).height(46.dp)
            ) {
                BasicText(
                    stringResource(R.string.home_invite_decline),
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(color = textColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                )
            }
        }
    }
}

/** Channel photo, or the first letter on a colourful circle. */
@Composable
private fun Avatar(photo: android.graphics.Bitmap?, name: String, size: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFFFF8A00), Color(0xFFFF375F)))),
        contentAlignment = Alignment.Center
    ) {
        if (photo != null) {
            val img = remember(photo) { photo.asImageBitmap() }
            Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            BasicText(
                name.trim().take(1).uppercase(),
                style = TextStyle(color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun ChannelRow(ch: Channel, dark: Boolean, textColor: Color, subColor: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(ch.photo, ch.name, 58.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                ch.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = textColor, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(3.dp))
            BasicText(
                if (ch.bio.isNotBlank()) ch.bio else "@" + ch.username,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = subColor, fontSize = 14.sp)
            )
        }
        Spacer(Modifier.width(10.dp))
        BasicText(
            formatTime(ch.createdAtMs),
            style = TextStyle(color = subColor, fontSize = 12.sp)
        )
    }
}

/** "14:05" for today, "23/11" for older days. */
private fun formatTime(ms: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ms }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    return SimpleDateFormat(if (sameDay) "HH:mm" else "d/M", Locale.US).format(Date(ms))
}
