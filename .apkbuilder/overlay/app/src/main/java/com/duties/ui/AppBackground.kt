package com.duties.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private class Blob(val x: Float, val y: Float, val r: Float, val color: Color)

/**
 * Colorful background the liquid glass refracts. [variant] changes the colors so every tab page
 * looks different (that makes the tab-switch wave easy to see while the pages are still empty).
 */
@Composable
fun AppBackground(dark: Boolean, variant: Int = 0, modifier: Modifier = Modifier.fillMaxSize()) {
    val base = if (dark) listOf(Color(0xFF0B1020), Color(0xFF140F2B))
    else listOf(Color(0xFFEAF2FF), Color(0xFFF5ECFF))
    val alpha = if (dark) 0.55f else 0.45f

    val blobs = when (variant % 3) {
        0 -> listOf(
            Blob(0.15f, 0.10f, 0.75f, AppAccent.pick(Color(0xFF0A84FF))),
            Blob(0.95f, 0.45f, 0.70f, Color(0xFFBF5AF2)),
            Blob(0.20f, 0.90f, 0.80f, Color(0xFF5AC8FA))
        )
        1 -> listOf(
            Blob(0.85f, 0.10f, 0.75f, Color(0xFFBF5AF2)),
            Blob(0.05f, 0.50f, 0.70f, Color(0xFFFF375F)),
            Blob(0.80f, 0.92f, 0.80f, Color(0xFFFF9F0A))
        )
        else -> listOf(
            Blob(0.10f, 0.12f, 0.75f, Color(0xFF30D158)),
            Blob(0.95f, 0.50f, 0.70f, AppAccent.pick(Color(0xFF0A84FF))),
            Blob(0.25f, 0.92f, 0.80f, Color(0xFF64D2FF))
        )
    }

    Canvas(modifier) {
        drawRect(Brush.verticalGradient(base))
        for (b in blobs) {
            val center = Offset(size.width * b.x, size.height * b.y)
            val radius = size.maxDimension * b.r * 0.5f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(b.color.copy(alpha = alpha), Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
    }
}
