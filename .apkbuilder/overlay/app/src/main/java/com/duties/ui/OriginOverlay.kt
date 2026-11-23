package com.duties.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp

/**
 * A full screen page that grows out of a point (a button) and shrinks back into it when it closes.
 * While it grows its corners go from very round (like the button) to square (full screen), and the
 * parts of the page ([Emerge]) come out of the same point.
 *
 * [origin] = center of the button in root coordinates (null = near the top-right corner).
 */
@Composable
fun OriginOverlay(
    visible: Boolean,
    origin: Offset?,
    startRadius: Float = 120f,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val px = remember(origin, view.width, view.height) {
        origin ?: Offset(view.width * 0.93f, view.height * 0.075f)
    }
    val tOrigin = remember(px, view.width, view.height) {
        if (view.width <= 0 || view.height <= 0) TransformOrigin(0.93f, 0.075f)
        else TransformOrigin((px.x / view.width).coerceIn(0f, 1f), (px.y / view.height).coerceIn(0f, 1f))
    }
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(spring(dampingRatio = 0.84f, stiffness = 300f), initialScale = 0.03f, transformOrigin = tOrigin) +
            fadeIn(tween(200)),
        exit = scaleOut(tween(300, easing = FastOutSlowInEasing), targetScale = 0.03f, transformOrigin = tOrigin) +
            fadeOut(tween(230, delayMillis = 40))
    ) {
        // corners: big and round at the start, square when the page fills the screen
        val edge by transition.animateFloat(
            transitionSpec = { tween(320, easing = FastOutSlowInEasing) },
            label = "overlayEdge"
        ) { if (it == EnterExitState.Visible) 0f else 1f }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    shape = RoundedCornerShape((edge * startRadius).dp)
                    clip = edge > 0.001f
                }
                .pointerInput(Unit) { detectTapGestures { } } // nothing below can be touched
        ) {
            CompositionLocalProvider(LocalPushOrigin provides px) { content() }
        }
    }
}
