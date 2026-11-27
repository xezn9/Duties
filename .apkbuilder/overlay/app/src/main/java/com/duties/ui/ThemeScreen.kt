package com.duties.ui

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
    val scope = rememberCoroutineScope()
    // a second PushStack inside this page: the "App color" button grows into the colors page
    val push = remember { PushController(scope) }
    PushStack(
        controller = push,
        modifier = Modifier.fillMaxSize(),
        edgeLine = false,
        growFromSource = true,
        shrinkOnClose = true,
        detail = { AppColorScreen(dark, onClose = { push.close() }) }
    ) {
        ThemeMain(dark, onClose, onOpenColors = { from -> push.open("colors", from) })
    }
}

@Composable
private fun ThemeMain(dark: Boolean, onClose: () -> Unit, onOpenColors: (Rect?) -> Unit) {
    val context = LocalContext.current
    val fg = if (dark) Color.White else Color(0xFF101114)

    // full screen: the status bar is hidden while this page is open (a swipe from the top still shows it for a moment)
    val view = LocalView.current
    DisposableEffect(view) {
        val window = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val oldBehavior = controller?.systemBarsBehavior
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.statusBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.statusBars())
            if (controller != null && oldBehavior != null) controller.systemBarsBehavior = oldBehavior
        }
    }

    // fog at the top (copied from the Create form): what scrolls up gets blurred and melts away up to the top edge
    val density = LocalDensity.current
    val cutoutTop = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val topFog = cutoutTop + 90.dp
    val rampPx = with(density) { 80.dp.toPx() }   // how far the page has to scroll for the fog to be fully there
    val scroll = rememberScrollState()
    val fog = rememberLayerBackdrop()

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
        Box(Modifier.fillMaxSize().navigationBarsPadding()) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // top: what scrolls up melts away, all the way to the top edge of the screen
                    // (only once the page was scrolled, so everything is sharp at rest)
                    val th = topFog.toPx()
                    val kt = (scroll.value / rampPx).coerceIn(0f, 1f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 1f - kt),
                            0.12f to Color.Black.copy(alpha = 1f - kt),
                            1f to Color.Black,
                            startY = 0f,
                            endY = th
                        ),
                        topLeft = Offset.Zero,
                        size = Size(size.width, th),
                        blendMode = BlendMode.DstIn
                    )
                }
                .layerBackdrop(fog)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(cutoutTop + 10.dp))
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

            // ---- app color: ONE button that opens the colors page (the 20 colors are books on a shelf there)
            Emerge(delayMs = 250, modifier = Modifier.fillMaxWidth()) {
                val shape = RoundedCornerShape(28.dp)
                val colorRow = remember { arrayOfNulls<LayoutCoordinates>(1) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { colorRow[0] = it }
                        .bounceClick {
                            val c = colorRow[0]
                            onOpenColors(if (c != null && c.isAttached) c.boundsInRoot() else null)
                        }
                        .liquidCard(glassBackdrop, shape, dark)
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (AppAccent.active) Brush.linearGradient(listOf(AppAccent.color, AppAccent.color))
                                else Brush.sweepGradient(
                                    listOf(
                                        Color(0xFFFF5F45), Color(0xFFFFD60A), Color(0xFF30D158),
                                        Color(0xFF0A84FF), Color(0xFFBF5AF2), Color(0xFFFF5F45)
                                    )
                                )
                            )
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        BasicText(
                            stringResource(R.string.app_color_title),
                            style = TextStyle(color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        )
                        Spacer(Modifier.height(2.dp))
                        BasicText(
                            stringResource(R.string.app_color_hint),
                            style = TextStyle(color = fg.copy(alpha = 0.55f), fontSize = 12.sp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    BasicText(
                        if (AppAccent.active) AppAccent.swatches[AppAccent.index].name
                        else stringResource(R.string.app_color_original),
                        style = TextStyle(color = fg.copy(alpha = 0.7f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    )
                    Spacer(Modifier.width(6.dp))
                    GlyphIcon(R.drawable.ic_chevron_left, fg.copy(alpha = 0.45f), 22.dp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        // ---- fog at the top: two blurred copies of the content (soft over the whole length, strong near the top edge)
        val topStrength: () -> Float = { (scroll.value / rampPx).coerceIn(0f, 1f) }
        FogBand(
            backdrop = fog,
            blurRadius = 5.dp,
            strength = topStrength,
            stops = arrayOf(0f to Color.Transparent, 0.2f to Color.Black, 0.85f to Color.Black.copy(alpha = 0.25f), 1f to Color.Transparent),
            modifier = Modifier.align(Alignment.TopCenter).height(topFog)
        )
        FogBand(
            backdrop = fog,
            blurRadius = 14.dp,
            strength = topStrength,
            stops = arrayOf(0f to Color.Transparent, 0.3f to Color.Black, 1f to Color.Transparent),
            modifier = Modifier.align(Alignment.TopCenter).height(topFog * 0.55f)
        )
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
