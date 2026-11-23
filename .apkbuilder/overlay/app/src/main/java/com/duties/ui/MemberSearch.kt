package com.duties.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duties.R
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.delay

private val SEARCH_ORANGE = Color(0xFFDB7608)
private val SEARCH_VIOLET = Color(0xFF8A5CFF)
private const val MAX_INVITEES = 20

/**
 * Small search window on top of the "new channel" window: type an @username, tick the people you want,
 * press "Send invitation". The invitations are written together with the channel when it is created
 * (an invitation needs the channel to exist), so here the choice is kept in [draft].
 */
@Composable
internal fun MemberSearchPopup(
    dark: Boolean,
    backdrop: Backdrop,
    draft: ChannelDraft,
    onSearch: suspend (String) -> List<FoundUser>,
    onClose: () -> Unit
) {
    val fg = if (dark) Color.White else Color(0xFF101114)
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<FoundUser>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        failed = false
        if (query.trim().length < 2) {
            results = emptyList()
            loading = false
            searched = false
            return@LaunchedEffect
        }
        loading = true
        delay(350)                                   // wait until the typing stops
        try {
            results = onSearch(query)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            results = emptyList()
            failed = true
        }
        loading = false
        searched = true
    }

    val shape = RoundedCornerShape(32.dp)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (dark) 0.45f else 0.25f))
            .pointerInput(Unit) { detectTapGestures { onClose() } }      // tap outside = close
    ) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(start = 14.dp, end = 14.dp, top = 56.dp)
                .fillMaxWidth()
                .liquidCard(backdrop, shape, dark)
                .background(if (dark) Color(0xFF16161C).copy(alpha = 0.60f) else Color.White.copy(alpha = 0.62f), shape)
                .pointerInput(Unit) { detectTapGestures { } }              // taps on the card do not close it
                .padding(16.dp)
        ) {
            // ---- title + close
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    stringResource(R.string.create_win_members_title),
                    modifier = Modifier.weight(1f),
                    style = TextStyle(color = fg, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                )
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(fg.copy(alpha = 0.10f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
                    contentAlignment = Alignment.Center
                ) { GlyphIcon(R.drawable.ic_close, fg, 18.dp) }
            }
            Spacer(Modifier.height(12.dp))

            // ---- search field
            LiquidField(
                icon = R.drawable.ic_at,
                tint = SEARCH_VIOLET,
                label = stringResource(R.string.create_win_search_label),
                value = query,
                maxLen = USERNAME_MAX,
                backdrop = backdrop,
                dark = dark,
                hint = stringResource(R.string.create_win_search_hint),
                onValueChange = { v -> query = v.removePrefix("@").filter { it.isUsernameChar() }.take(USERNAME_MAX) },
                keyboardType = KeyboardType.Ascii
            )
            Spacer(Modifier.height(10.dp))

            // ---- results
            Box(Modifier.fillMaxWidth().heightIn(min = 56.dp, max = 190.dp)) {
                when {
                    loading -> Hint(stringResource(R.string.create_win_searching), fg)
                    failed -> Hint(stringResource(R.string.create_win_search_error), fg)
                    searched && results.isEmpty() -> Hint(stringResource(R.string.create_win_search_none), fg)
                    results.isEmpty() -> Hint(stringResource(R.string.create_win_search_tip), fg)
                    else -> Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        results.forEach { u ->
                            val selected = draft.invitees.any { it.uid == u.uid }
                            UserRow(u, selected, fg) {
                                if (selected) draft.invitees.removeAll { it.uid == u.uid }
                                else if (draft.invitees.size < MAX_INVITEES) draft.invitees.add(u)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))

            // ---- send
            val count = draft.invitees.size
            LiquidButton(
                onClick = { if (count > 0) onClose() },
                backdrop = backdrop,
                tint = SEARCH_ORANGE,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .graphicsLayer { alpha = if (count > 0) 1f else 0.45f }
            ) {
                BasicText(
                    stringResource(R.string.create_win_send_invite) + if (count > 0) "  ($count)" else "",
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                )
            }
            Spacer(Modifier.height(8.dp))
            BasicText(
                stringResource(R.string.create_win_invite_note),
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(color = fg.copy(alpha = 0.55f), fontSize = 12.sp, textAlign = TextAlign.Center)
            )
        }
    }
}

@Composable
private fun Hint(text: String, fg: Color) {
    Box(Modifier.fillMaxWidth().heightIn(min = 56.dp), contentAlignment = Alignment.Center) {
        BasicText(
            text,
            style = TextStyle(color = fg.copy(alpha = 0.6f), fontSize = 14.sp, textAlign = TextAlign.Center)
        )
    }
}

@Composable
private fun UserRow(u: FoundUser, selected: Boolean, fg: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Color(0xFF8A5CFF), Color(0xFF3D7BFF)))),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                u.name.trim().take(1).uppercase(),
                style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            BasicText(
                u.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            )
            BasicText(
                "@" + u.username,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = fg.copy(alpha = 0.55f), fontSize = 13.sp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (selected) SEARCH_ORANGE else Color.Transparent)
                .border(1.5.dp, if (selected) SEARCH_ORANGE else fg.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) { if (selected) GlyphIcon(R.drawable.ic_check, Color.White, 16.dp) }
    }
}
