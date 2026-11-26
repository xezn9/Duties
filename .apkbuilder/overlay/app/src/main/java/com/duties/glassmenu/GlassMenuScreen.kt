package com.duties.glassmenu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

private val CardBrushes = listOf(
    Brush.linearGradient(listOf(Color(0xFFFF5F6D), Color(0xFFFFC371))),
    Brush.linearGradient(listOf(Color(0xFF36D1DC), Color(0xFF5B86E5))),
    Brush.linearGradient(listOf(Color(0xFF8E2DE2), Color(0xFFF093FB))),
    Brush.linearGradient(listOf(Color(0xFF11998E), Color(0xFF38EF7D))),
    Brush.linearGradient(listOf(Color(0xFFF7971E), Color(0xFFFFD200))),
    Brush.linearGradient(listOf(Color(0xFFEB3349), Color(0xFF4A00E0))),
)

@Composable
fun GlassMenuScreen() {
    // demo screen is designed LTR (button on the physical top-right) even on Arabic phones
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        GlassMenuScreenContent()
    }
}

@Composable
private fun GlassMenuScreenContent() {
    // everything that should be visible THROUGH the glass is drawn inside the layerBackdrop box
    val backdrop = rememberLayerBackdrop()
    var expanded by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(top = 12.dp, bottom = 24.dp),
            ) {
                BasicText(
                    text = "Listen Now",
                    modifier = Modifier.padding(start = 20.dp, top = 6.dp),
                    style = TextStyle(color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(Modifier.height(28.dp))
                Section("Recently Played", 0)
                Spacer(Modifier.height(28.dp))
                Section("New releases", 3)
                Spacer(Modifier.height(28.dp))
                Section("Made for you", 1)
                Spacer(Modifier.height(28.dp))
                Section("Top picks", 4)
            }
        }

        // tap outside = close
        if (expanded) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { expanded = false },
            )
        }

        GlassMorphMenu(
            backdrop = backdrop,
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 0.dp, end = 12.dp),
        ) {
            GlassMenuRow(Icons.Filled.AccountCircle, "Sign in", expanded) { expanded = false }
            GlassMenuRow(Icons.Filled.Refresh, "Refresh", expanded) { expanded = false }
            GlassMenuRow(Icons.Filled.Star, "Rate the app", expanded) { expanded = false }
            GlassMenuRow(Icons.Filled.Share, "Share", expanded) { expanded = false }
            GlassMenuRow(Icons.Filled.Favorite, "Support to keep the app free forever", expanded) {
                expanded = false
            }
        }
    }
}

@Composable
private fun Section(title: String, brushOffset: Int) {
    BasicText(
        text = title,
        modifier = Modifier.padding(start = 20.dp, bottom = 12.dp),
        style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold),
    )
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(5) { i ->
            Column {
                Box(
                    Modifier
                        .size(width = 150.dp, height = 110.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardBrushes[(brushOffset + i) % CardBrushes.size]),
                )
                Spacer(Modifier.height(6.dp))
                BasicText(
                    "Track ${brushOffset * 3 + i + 1}",
                    style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                )
                BasicText(
                    "Artist name",
                    style = TextStyle(color = Color(0xFF9A9AA0), fontSize = 12.sp),
                )
            }
        }
    }
}
