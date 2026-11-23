package com.duties.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.duties.MissingWebClientIdException
import com.duties.R
import com.duties.signInWithGoogle
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(dark: Boolean, onButtonCenter: (Offset) -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val textColor = if (dark) Color.White else Color(0xFF101114)
    val cascade = remember { Cascade(250) }
    val notReadyMessage = stringResource(R.string.google_not_ready)

    Box(Modifier.fillMaxSize()) {
        // The glass button refracts this layer.
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            AppBackground(dark, variant = 0)
        }

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp)
        ) {
            Spacer(Modifier.weight(1f))

            SplitTitle(
                text = stringResource(R.string.app_name),
                delayMs = remember { cascade.take() },
                color = textColor,
                fontSize = 56.sp,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Reveal(delayMs = remember { cascade.take() }) {
                BasicText(
                    stringResource(R.string.login_subtitle),
                    style = TextStyle(color = textColor.copy(alpha = 0.65f), fontSize = 17.sp)
                )
            }

            Spacer(Modifier.height(40.dp))

            Reveal(delayMs = remember { cascade.take() }, modifier = Modifier.fillMaxWidth()) {
                GlassGoogleButton(
                    backdrop = backdrop,
                    dark = dark,
                    loading = loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { onButtonCenter(it.boundsInRoot().center) }
                ) {
                    if (!loading) {
                        loading = true
                        error = null
                        scope.launch {
                            try {
                                signInWithGoogle(context)
                            } catch (e: GetCredentialCancellationException) {
                                // user closed the account picker
                            } catch (e: MissingWebClientIdException) {
                                error = notReadyMessage
                            } catch (e: Exception) {
                                error = e.localizedMessage ?: e.toString()
                            } finally {
                                loading = false
                            }
                        }
                    }
                }
            }

            error?.let {
                Spacer(Modifier.height(14.dp))
                BasicText(
                    it,
                    style = TextStyle(color = Color(0xFFFF453A), fontSize = 14.sp)
                )
            }
        }
    }
}

@Composable
private fun GlassGoogleButton(
    backdrop: Backdrop,
    dark: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val container =
        if (dark) Color(0xFF121212).copy(0.4f) else Color(0xFFFAFAFA).copy(0.5f)
    val contentColor = if (dark) Color.White else Color(0xFF101114)

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "press"
    )

    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    lens(16f.dp.toPx(), 32f.dp.toPx())
                },
                layerBlock = {
                    scaleX = scale
                    scaleY = scale
                },
                onDrawSurface = { drawRect(container) }
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = !loading,
                role = Role.Button,
                onClick = onClick
            )
            .height(58.dp)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(R.drawable.ic_google),
            contentDescription = null,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        BasicText(
            text = stringResource(if (loading) R.string.signing_in else R.string.continue_google),
            style = TextStyle(color = contentColor, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        )
    }
}
