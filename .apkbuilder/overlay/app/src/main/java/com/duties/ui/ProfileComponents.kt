package com.duties.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule

/** Rubbery press: shrinks a little while pressed and springs back. */
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 420f),
        label = "bounce"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick
        )
}

@Composable
fun GlyphIcon(
    @DrawableRes id: Int,
    tint: Color,
    size: Dp = 24.dp,
    modifier: Modifier = Modifier
) {
    Image(
        painter = painterResource(id),
        contentDescription = null,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint)
    )
}

/** Liquid glass surface (a capsule; a square one is a circle) that refracts [backdrop]. */
@Composable
fun GlassSurface(
    backdrop: Backdrop,
    container: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    lensHeight: Dp = 8.dp,
    lensAmount: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "glassPress"
    )
    val clickMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            onClick = onClick
        )
    } else Modifier

    Box(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(6f.dp.toPx() * GlassTheme.blurScale)
                    glassLens(
                        lensHeight.toPx() * GlassTheme.lensScale,
                        lensAmount.toPx() * GlassTheme.lensScale,
                        chromaticAberration = GlassTheme.surfaceDispersion
                    )
                },
                layerBlock = {
                    scaleX = scale
                    scaleY = scale
                },
                onDrawSurface = { drawRect(GlassTheme.fill(container)) }
            )
            .then(clickMod),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** Dark round button (close, etc.) used on the colorful screens. */
@Composable
fun DarkCircleButton(@DrawableRes icon: Int, size: Dp = 52.dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .bounceClick(onClick = onClick)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.38f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(icon, Color.White, 24.dp)
    }
}

/** The orange pill (Save / Done). */
@Composable
fun OrangePill(
    text: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    height: Dp = 54.dp,
    onClick: () -> Unit
) {
    val shape = CircleShape
    Row(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.55f }
            .bounceClick(enabled = enabled, onClick = onClick)
            .height(height)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFFDB7608), Color(0xFFC25E00))))
            .border(1.dp, Color(0xFFF0A95A).copy(alpha = 0.55f), shape)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (icon != null) {
            GlyphIcon(icon, Color.White, 26.dp)
            Spacer(Modifier.width(10.dp))
        }
        BasicText(
            text,
            style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        )
    }
}
