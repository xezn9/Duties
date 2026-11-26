package com.duties.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duties.R
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidSlider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

/**
 * "App theme" page. It grows out of its row in the settings (PushStack) and its parts come out of the
 * same point (Emerge). It has one option: the real control of the liquid glass of the WHOLE app, with
 * four sliders (transparency, blur, refraction, rainbow colors). They change [GlassTheme], which every glass of the app
 * reads while drawing, so the bottom bar, the cards, the dialogs and the buttons change live while the
 * slider moves. The preview on top shows the same glass over bright shapes so the change is easy to see.
 */
@Composable
fun ThemeScreen(dark: Boolean, onClose: () -> Unit) {
    val context = LocalContext.current
    val fg = if (dark) Color.White else Color(0xFF101114)

    val glassBackdrop = rememberLayerBackdrop()
    val dots = rememberDotsState()
    val origin = LocalPushOrigin.current
    LaunchedEffect(Unit) { dots.playWave(origin) }

    // the values are saved on the phone shortly after the last move of a slider
    LaunchedEffect(Unit) {
        snapshotFlow {
            listOf(
                GlassTheme.transparency, GlassTheme.blur, GlassTheme.refraction, GlassTheme.dispersion,
                AppAccent.index.toFloat()
            )
        }
            .collectLatest {
                delay(250)
                GlassTheme.save(context)
                AppAccent.save(context)
            }
    }

    Box(Modifier.fillMaxSize().dotsTouch(dots)) {
        Box(Modifier.fillMaxSize().layerBackdrop(glassBackdrop)) {
            DotBackground(dots, dark)
        }
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            // ---- top bar: close + title
            Emerge(delayMs = 40, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    GlassSurface(
                        backdrop = glassBackdrop,
                        container = if (dark) Color(0xFF121212).copy(alpha = 0.40f) else Color.White.copy(alpha = 0.50f),
                        modifier = Modifier.size(48.dp),
                        onClick = onClose
                    ) { GlyphIcon(R.drawable.ic_close, fg, 22.dp) }
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        BasicText(
                            stringResource(R.string.theme_title),
                            style = TextStyle(color = fg, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                        )
                        BasicText(
                            stringResource(R.string.theme_subtitle),
                            style = TextStyle(color = fg.copy(alpha = 0.6f), fontSize = 12.sp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // ---- live preview of the glass
            Emerge(delayMs = 110, modifier = Modifier.fillMaxWidth()) {
                GlassPreview(dark)
            }

            Spacer(Modifier.height(16.dp))

            // ---- the only option: liquid glass control
            Emerge(delayMs = 180, modifier = Modifier.fillMaxWidth()) {
                val shape = RoundedCornerShape(28.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .liquidCard(glassBackdrop, shape, dark)
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    BasicText(
                        stringResource(R.string.glass_title),
                        style = TextStyle(color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(14.dp))
                    GlassSliderRow(
                        title = stringResource(R.string.glass_transparency),
                        hint = stringResource(R.string.glass_transparency_hint),
                        get = { GlassTheme.transparency },
                        set = { GlassTheme.transparency = it },
                        backdrop = glassBackdrop, dark = dark, fg = fg
                    )
                    Spacer(Modifier.height(10.dp))
                    GlassSliderRow(
                        title = stringResource(R.string.glass_blur),
                        hint = stringResource(R.string.glass_blur_hint),
                        get = { GlassTheme.blur },
                        set = { GlassTheme.blur = it },
                        backdrop = glassBackdrop, dark = dark, fg = fg
                    )
                    Spacer(Modifier.height(10.dp))
                    GlassSliderRow(
                        title = stringResource(R.string.glass_refraction),
                        hint = stringResource(R.string.glass_refraction_hint),
                        get = { GlassTheme.refraction },
                        set = { GlassTheme.refraction = it },
                        backdrop = glassBackdrop, dark = dark, fg = fg
                    )
                    Spacer(Modifier.height(10.dp))
                    GlassSliderRow(
                        title = stringResource(R.string.glass_dispersion),
                        hint = stringResource(R.string.glass_dispersion_hint),
                        get = { GlassTheme.dispersion },
                        set = { GlassTheme.dispersion = it },
                        backdrop = glassBackdrop, dark = dark, fg = fg
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // ---- app color: 20 colors that recolor the whole app
            Emerge(delayMs = 250, modifier = Modifier.fillMaxWidth()) {
                val shape = RoundedCornerShape(28.dp)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .liquidCard(glassBackdrop, shape, dark)
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            stringResource(R.string.app_color_title),
                            modifier = Modifier.weight(1f),
                            style = TextStyle(color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        )
                        BasicText(
                            if (AppAccent.active) AppAccent.swatches[AppAccent.index].name
                            else stringResource(R.string.app_color_original),
                            style = TextStyle(color = fg.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        )
                    }
                    BasicText(
                        stringResource(R.string.app_color_hint),
                        style = TextStyle(color = fg.copy(alpha = 0.55f), fontSize = 12.sp)
                    )
                    Spacer(Modifier.height(14.dp))
                    AppColorWheel(dark = dark, fg = fg)
                    Spacer(Modifier.height(6.dp))
                    BasicText(
                        stringResource(R.string.app_color_wheel_hint),
                        modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(color = fg.copy(alpha = 0.55f), fontSize = 12.sp, textAlign = TextAlign.Center)
                    )
                    Spacer(Modifier.height(16.dp))
                    GlassSurface(
                        backdrop = glassBackdrop,
                        container = if (dark) Color(0xFF121212).copy(alpha = 0.40f) else Color.White.copy(alpha = 0.50f),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        onClick = { AppAccent.index = -1 }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!AppAccent.active) {
                                GlyphIcon(R.drawable.ic_check, fg, 18.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            BasicText(
                                stringResource(R.string.app_color_original),
                                style = TextStyle(color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Title + percent on one line, a small hint under it, and the liquid slider. */
@Composable
private fun GlassSliderRow(
    title: String,
    hint: String,
    get: () -> Float,
    set: (Float) -> Unit,
    backdrop: Backdrop,
    dark: Boolean,
    fg: Color
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                title,
                modifier = Modifier.weight(1f),
                style = TextStyle(color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            )
            // read here, so only this text redraws while the slider moves
            BasicText(
                "${(get() * 100f).roundToInt()}%",
                style = TextStyle(color = fg.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
            )
        }
        BasicText(hint, style = TextStyle(color = fg.copy(alpha = 0.55f), fontSize = 12.sp))
        Spacer(Modifier.height(6.dp))
        LiquidSlider(
            value = get,
            onValueChange = set,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            backdrop = backdrop,
            dark = dark
        )
    }
}

/**
 * Bright shapes and thin stripes with a glass card over them: blur softens the stripes, refraction bends
 * them at the edge of the card and transparency changes how much of the color shows through.
 */
@Composable
private fun GlassPreview(dark: Boolean) {
    val pv = rememberLayerBackdrop()
    val shape = RoundedCornerShape(30.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(170.dp)
            .clip(shape)
    ) {
        Canvas(Modifier.matchParentSize().layerBackdrop(pv)) {
            drawRect(
                Brush.linearGradient(
                    listOf(Color(0xFF1B2A6B), Color(0xFF7A2C9E), Color(0xFFFF5F45)),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )
            drawCircle(Color(0xFFFFD60A), radius = size.height * 0.28f, center = Offset(size.width * 0.20f, size.height * 0.30f))
            drawCircle(Color(0xFF30D158), radius = size.height * 0.22f, center = Offset(size.width * 0.86f, size.height * 0.74f))
            drawCircle(Color(0xFF64D2FF), radius = size.height * 0.16f, center = Offset(size.width * 0.62f, size.height * 0.18f))
            var x = 0f
            val step = 22.dp.toPx()
            val w = 2.dp.toPx()
            while (x < size.width) {
                drawRect(Color.White.copy(alpha = 0.30f), Offset(x, 0f), Size(w, size.height))
                x += step
            }
        }
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.64f)
                .height(78.dp)
                .liquidCard(pv, RoundedCornerShape(26.dp), dark),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                stringResource(R.string.glass_title),
                style = TextStyle(color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            )
        }
    }
}
