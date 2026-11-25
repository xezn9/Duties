package com.duties.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duties.R
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.delay

private val SEARCH_ORANGE: Color get() = AppAccent.deep(Color(0xFFDB7608))
private val SEARCH_VIOLET = Color(0xFF8A5CFF)
private const val MAX_INVITEES = 20

/**
 * "Add members" window, on top of the "new channel" window.
 *
 *  - type an @username: the people whose username is the same, starts the same, contains it or is just SIMILAR show up;
 *  - tap as many people as you want (multiple choice): every one you picked is pinned in the "picked" strip at the top
 *    (tap him there to take him out) and stays lit in the list;
 *  - "Send invitation" keeps the choice. The invitations are written together with the channel when it is created
 *    (an invitation needs the channel to exist), so the choice is kept in [draft].
 *
 * The card is nearly opaque, so the fields of the window under it never show through the text.
 * [bottomInset] = the part of the keyboard that covers this window: the card ends above it.
 */
@Composable
internal fun MemberSearchPopup(
    dark: Boolean,
    backdrop: Backdrop,
    draft: ChannelDraft,
    onSearch: suspend (String) -> List<FoundUser>,
    onClose: () -> Unit,
    bottomInset: Dp = 0.dp
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
        delay(300)                                   // wait until the typing stops
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

    fun toggle(u: FoundUser) {
        val at = draft.invitees.indexOfFirst { it.uid == u.uid }
        if (at >= 0) draft.invitees.removeAt(at)
        else if (draft.invitees.size < MAX_INVITEES) draft.invitees.add(u)
    }

    // the keyboard covers part of the window: the card is short, so everything that is not needed is hidden
    // (section titles, the note, the big button: its job is done by the small tick in the title row) and the
    // list of people gets all the room that is left.
    val compact = bottomInset > 0.dp
    val shape = RoundedCornerShape(32.dp)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (dark) 0.55f else 0.30f))
            .pointerInput(Unit) { detectTapGestures { onClose() } }      // tap outside = close
    ) {
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp + bottomInset)
                .fillMaxWidth()
                .heightIn(max = (maxHeight - 24.dp - bottomInset).coerceAtLeast(160.dp))
                .liquidCard(backdrop, shape, dark)
                .background(if (dark) Color(0xFF14141A).copy(alpha = 0.95f) else Color.White.copy(alpha = 0.96f), shape)
                .pointerInput(Unit) { detectTapGestures { } }              // taps on the card do not close it
                .padding(if (compact) 12.dp else 16.dp)
        ) {
            // ---- title + counter + close
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    stringResource(R.string.create_win_members_title),
                    style = TextStyle(color = fg, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.width(10.dp))
                if (draft.invitees.isNotEmpty()) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(SEARCH_ORANGE.copy(alpha = 0.20f))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        BasicText(
                            "${draft.invitees.size}/$MAX_INVITEES",
                            style = TextStyle(color = SEARCH_ORANGE, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (compact && draft.invitees.isNotEmpty()) {
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(SEARCH_ORANGE)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlyphIcon(R.drawable.ic_check, Color.White, 16.dp)
                        Spacer(Modifier.width(4.dp))
                        BasicText(
                            stringResource(R.string.create_win_send_invite),
                            maxLines = 1,
                            softWrap = false,
                            style = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(fg.copy(alpha = 0.10f))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
                    contentAlignment = Alignment.Center
                ) { GlyphIcon(R.drawable.ic_close, fg, 18.dp) }
            }
            Spacer(Modifier.height(if (compact) 8.dp else 12.dp))

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

            // ---- the people picked so far: pinned here whatever you search
            AnimatedVisibility(
                visible = draft.invitees.isNotEmpty(),
                enter = fadeIn(spring(stiffness = 500f)) + expandVertically(spring(dampingRatio = 0.8f, stiffness = 400f)),
                exit = fadeOut(spring(stiffness = 500f)) + shrinkVertically(spring(stiffness = 500f))
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
                    if (!compact) {
                        SectionTitle(stringResource(R.string.create_win_selected_title) + "  (" + draft.invitees.size + ")", fg)
                        Spacer(Modifier.height(8.dp))
                    }
                    LazyRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(draft.invitees.toList(), key = { it.uid }) { u ->
                            PickedChip(u, fg, Modifier.animateItem()) { toggle(u) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(if (compact) 8.dp else 14.dp))

            // ---- results (similar names)
            when {
                loading && results.isEmpty() -> Hint(stringResource(R.string.create_win_searching), fg)
                failed -> Hint(stringResource(R.string.create_win_search_error), fg)
                searched && results.isEmpty() -> Hint(stringResource(R.string.create_win_search_none), fg)
                results.isEmpty() -> Hint(stringResource(R.string.create_win_search_tip), fg)
                else -> {
                    if (!compact) {
                        SectionTitle(stringResource(R.string.create_win_results_title), fg)
                        Spacer(Modifier.height(4.dp))
                    }
                    LazyColumn(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = compact)
                            .graphicsLayer { alpha = if (loading) 0.5f else 1f }
                    ) {
                        items(results, key = { it.uid }) { u ->
                            val selected = draft.invitees.any { it.uid == u.uid }
                            UserRow(u, selected, fg, Modifier.animateItem()) { toggle(u) }
                        }
                    }
                }
            }
            if (!compact) {
            Spacer(Modifier.height(12.dp))

            // ---- confirm the choice
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
}

@Composable
private fun SectionTitle(text: String, fg: Color) {
    BasicText(text, style = TextStyle(color = fg.copy(alpha = 0.60f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
}

@Composable
private fun Hint(text: String, fg: Color) {
    Box(Modifier.fillMaxWidth().heightIn(min = 64.dp), contentAlignment = Alignment.Center) {
        BasicText(
            text,
            style = TextStyle(color = fg.copy(alpha = 0.6f), fontSize = 14.sp, textAlign = TextAlign.Center)
        )
    }
}

@Composable
private fun Avatar(name: String, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Color(0xFF8A5CFF), Color(0xFF3D7BFF)))),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            name.trim().take(1).uppercase(),
            style = TextStyle(color = Color.White, fontSize = (size.value * 0.43f).sp, fontWeight = FontWeight.Bold)
        )
    }
}

/** A person that was picked: small pill with his name and an "x"; tap = take him out. */
@Composable
private fun PickedChip(u: FoundUser, fg: Color, modifier: Modifier, onRemove: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(SEARCH_ORANGE.copy(alpha = 0.18f))
            .border(1.dp, SEARCH_ORANGE.copy(alpha = 0.70f), RoundedCornerShape(50))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onRemove)
            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(u.name, 28.dp)
        Spacer(Modifier.width(8.dp))
        Column {
            BasicText(
                u.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 110.dp),
                style = TextStyle(color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            )
            BasicText(
                "@" + u.username,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 110.dp),
                style = TextStyle(color = fg.copy(alpha = 0.55f), fontSize = 11.sp)
            )
        }
        Spacer(Modifier.width(8.dp))
        GlyphIcon(R.drawable.ic_close, fg.copy(alpha = 0.7f), 14.dp)
    }
}

@Composable
private fun UserRow(u: FoundUser, selected: Boolean, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    val tick by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "pickTick")
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(shape)
            .background(SEARCH_ORANGE.copy(alpha = 0.14f * tick.coerceIn(0f, 1f)))
            .border(1.dp, if (selected) SEARCH_ORANGE.copy(alpha = 0.65f) else Color.Transparent, shape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(u.name, 42.dp)
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
                .graphicsLayer {
                    val sc = 1f + 0.12f * tick.coerceIn(0f, 1.5f)
                    scaleX = sc
                    scaleY = sc
                }
                .clip(CircleShape)
                .background(if (selected) SEARCH_ORANGE else Color.Transparent)
                .border(1.5.dp, if (selected) SEARCH_ORANGE else fg.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) { if (selected) GlyphIcon(R.drawable.ic_check, Color.White, 16.dp) }
    }
}
