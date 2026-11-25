package com.duties.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duties.R
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.delay

/*
 * HomeSearch.kt — search for channels from the Home tab.
 *
 *   HomeSearchButton   the round liquid glass button at the right of the "Home" title (a X while the search is open)
 *   HomeSearchPanel    a glass search field + the channels found by name or @username. It covers the channel list
 *                      while it is open. The results are only SHOWN for now: tapping one does nothing yet.
 */

/** Round liquid glass button (same glass as the other buttons of the app). */
@Composable
internal fun HomeSearchButton(
    open: Boolean,
    dark: Boolean,
    backdrop: Backdrop,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fg = if (dark) Color.White else Color(0xFF101114)
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        surfaceColor = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.30f),
        modifier = modifier.size(48.dp)
    ) {
        Crossfade(targetState = open, animationSpec = tween(200), label = "searchIcon") { isOpen ->
            // requiredSize: the button is a 48dp circle, so the 16dp side padding of LiquidButton must not squeeze the icon
            GlyphIcon(
                if (isOpen) R.drawable.ic_close else R.drawable.ic_search,
                fg, 22.dp, Modifier.requiredSize(22.dp)
            )
        }
    }
}

/** The search page: it fades in over the channel list when [visible] and fades out when it is not. */
@Composable
internal fun HomeSearchPanel(
    visible: Boolean,
    dark: Boolean,
    backdrop: Backdrop,
    onSearch: suspend (String) -> List<Channel>,
    modifier: Modifier = Modifier
) {
    val p by animateFloatAsState(if (visible) 1f else 0f, tween(280, easing = FastOutSlowInEasing), label = "searchPanel")
    if (visible || p > 0.001f) {
        SearchContent(visible, p, dark, backdrop, onSearch, modifier)
    }
}

@Composable
private fun SearchContent(
    visible: Boolean,
    p: Float,
    dark: Boolean,
    backdrop: Backdrop,
    onSearch: suspend (String) -> List<Channel>,
    modifier: Modifier
) {
    val textColor = if (dark) Color.White else Color(0xFF101114)
    val subColor = if (dark) Color(0xFFB0B3B8) else Color(0xFF55586A)
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val focus = LocalFocusManager.current
    val fr = remember { FocusRequester() }

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Channel>?>(null) }   // null = nothing searched yet
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    // the keyboard comes up with the panel and goes away with it
    LaunchedEffect(visible) {
        if (visible) {
            delay(280)
            runCatching { fr.requestFocus() }
        } else {
            focus.clearFocus()
        }
    }
    // search while typing, a moment after the last letter (a new letter cancels the one that was waiting)
    LaunchedEffect(query) {
        val q = query.trim().removePrefix("@")
        if (q.length < 2) {
            results = null
            loading = false
            failed = false
            return@LaunchedEffect
        }
        loading = true
        delay(400)
        try {
            results = onSearch(q)
            failed = false
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            failed = true
        }
        loading = false
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = p
                    translationY = (1f - p) * 18.dp.toPx()
                }
                .pointerInput(Unit) { detectTapGestures { } }       // covers the list below
        ) {
            // ---- the glass search field
            val shape = RoundedCornerShape(26.dp)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .height(52.dp)
                    .liquidCard(backdrop, shape, dark)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlyphIcon(R.drawable.ic_search, subColor, 20.dp)
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        BasicText(
                            stringResource(R.string.search_hint),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = textColor.copy(alpha = 0.35f), fontSize = 17.sp)
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = textColor, fontSize = 17.sp),
                        cursorBrush = SolidColor(ACCENT),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(fr)
                    )
                }
            }

            // ---- the answer: a short message, or the channels that were found
            val list = results
            val message: Int? = when {
                failed -> R.string.search_failed
                list == null -> if (loading) R.string.search_loading else R.string.search_idle
                list.isEmpty() -> if (loading) R.string.search_loading else R.string.search_none
                else -> null
            }
            if (message == null && list != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 130.dp + navBottom)
                ) {
                    items(list, key = { "found-" + it.id }) { ch ->
                        // nothing happens when a result is tapped, for now
                        SearchResultRow(ch, textColor, subColor, Modifier.animateItem())
                    }
                }
            } else {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 50.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    BasicText(
                        stringResource(message ?: R.string.search_idle),
                        style = TextStyle(color = subColor, fontSize = 15.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
                    )
                }
            }
        }
    }
}

/** A channel that was found: photo, name, @username and how many people are inside (same look as a Home row). */
@Composable
private fun SearchResultRow(
    ch: Channel,
    textColor: Color,
    subColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ROW_PAD_H, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(ch.photo, ch.name, ROW_AVATAR)
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
                "@" + ch.username,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = subColor, fontSize = 14.sp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(subColor.copy(alpha = 0.14f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlyphIcon(R.drawable.ic_people, subColor, 16.dp)
            Spacer(Modifier.width(5.dp))
            BasicText(
                ch.memberCount.toString(),
                style = TextStyle(color = subColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            )
        }
    }
}
