package com.duties.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duties.R

/*
 * CreateHero.kt — what fills the Create tab while the guide is folded (and the form is closed):
 * a floating, spinning picture, its name under it, and the band of moving words. The three are one thing:
 * the word that passes through the middle of the band is the name, and its picture is the one in the tile.
 */

private class HeroKind(val image: Int, val word: Int)

private val HERO_KINDS = listOf(
    HeroKind(R.drawable.hero_friends, R.string.create_hero_friends),
    HeroKind(R.drawable.hero_work, R.string.create_hero_work),
    HeroKind(R.drawable.hero_study, R.string.create_hero_study)
)

/** One piece of the hero: it comes in (fade + rise) at its own moment of [progress] (0..1) and goes out the same way. */
@Composable
private fun HeroPart(progress: () -> Float, from: Float, dy: Dp, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.graphicsLayer {
            val k = seg(progress(), from, from + 0.6f)
            alpha = k
            translationY = (1f - k) * dy.toPx()
        }
    ) { content() }
}

/**
 * [progress]: 0 = away, 1 = fully there. [marquee] is kept by the caller, so the band goes on from where it was.
 * It stands in the free part of the page: above the "read the rules again" button, centered.
 */
@Composable
fun CreateHeroPanel(
    marquee: MarqueeState,
    progress: () -> Float,
    dark: Boolean,
    modifier: Modifier = Modifier
) {
    val textColor = if (dark) Color(0xFFF5F5F7) else Color(0xFF101114)
    val words = HERO_KINDS.map { stringResource(it.word) }
    val current = marquee.current.coerceIn(0, HERO_KINDS.lastIndex)
    val tile = RoundedCornerShape(40.dp)             // like the squircle of the HyperSpring logo (27 % of the side)
    // the first name waits a moment (the picture comes first); the ones that follow start at once
    val firstName = remember { BooleanArray(1) { true } }

    // the buttons stand under it: leave their room (nav bar + 108dp + the raised small button) free
    Column(
        modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(bottom = 250.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // ---- the picture: floats, turns when tapped; the picture inside changes with the word
        HeroPart(progress, from = 0f, dy = 26.dp) {
            FloatingSpinImage(Modifier.size(148.dp)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(tile)
                        .background(if (dark) Color(0xFFF4F1EA) else Color.White)
                        .border(1.dp, if (dark) Color.Transparent else Color.Black.copy(alpha = 0.08f), tile),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = current,
                        transitionSpec = {
                            (fadeIn(tween(360)) + scaleIn(spring(dampingRatio = 0.55f, stiffness = 320f), initialScale = 0.5f)) togetherWith
                                (fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.8f))
                        },
                        contentAlignment = Alignment.Center,
                        label = "heroImage"
                    ) { i ->
                        Image(
                            painter = painterResource(HERO_KINDS[i].image),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().padding(24.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        // ---- the name under the picture: the word of the band, letters rising when it changes
        HeroPart(progress, from = 0.15f, dy = 22.dp, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            AnimatedContent(
                targetState = current,
                transitionSpec = { fadeIn(tween(0)) togetherWith fadeOut(tween(160)) },
                contentAlignment = Alignment.Center,
                label = "heroName"
            ) { i ->
                val wait = remember { if (firstName[0]) 260 else 0 }
                firstName[0] = false
                SplitTitle(
                    text = words[i],
                    delayMs = wait,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 46.sp,
                    color = textColor,
                    centered = true,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
        Spacer(Modifier.height(30.dp))

        // ---- the band of moving words (full width of the screen)
        HeroPart(progress, from = 0.3f, dy = 18.dp) {
            marquee.FollowTilt()      // gyroscope: tilt right -> the words slide right, left -> left
            Marquee(
                words = words,
                state = marquee,
                accent = ACCENT,
                fillColor = textColor,
                outlineColor = textColor.copy(alpha = 0.26f),
                outlined = false
            )
        }
    }
}
