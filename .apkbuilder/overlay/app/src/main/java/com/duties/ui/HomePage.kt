package com.duties.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
    channels: List<Channel>,
    cascade: Cascade,
    modifier: Modifier = Modifier
) {
    val textColor = if (dark) Color.White else Color(0xFF101114)
    val subColor = if (dark) Color(0xFFB0B3B8) else Color(0xFF55586A)
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Reveal(delayMs = remember { cascade.take() }, modifier = modifier.fillMaxWidth(), dy = 14.dp) {
            if (channels.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 60.dp), contentAlignment = Alignment.TopCenter) {
                    BasicText(
                        stringResource(R.string.home_empty),
                        style = TextStyle(color = subColor, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = 8.dp, bottom = 130.dp + navBottom
                    )
                ) {
                    items(channels, key = { it.id }) { ch ->
                        ChannelRow(ch, dark, textColor, subColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(ch: Channel, dark: Boolean, textColor: Color, subColor: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // photo (or the first letter on a colourful circle)
        Box(
            Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFFFF8A00), Color(0xFFFF375F)))),
            contentAlignment = Alignment.Center
        ) {
            if (ch.photo != null) {
                val img = remember(ch.photo) { ch.photo.asImageBitmap() }
                Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                BasicText(
                    ch.name.trim().take(1).uppercase(),
                    style = TextStyle(color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                )
            }
        }
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
