package com.duties

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duties.ui.AppBackground
import com.duties.ui.AuthWaveHost
import com.duties.ui.LoginScreen
import com.duties.ui.MainScreen
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

@Composable
fun DutiesApp() {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val firebaseReady = remember { FirebaseApp.getApps(context).isNotEmpty() }

    if (firebaseReady) AuthGate(dark) else SetupScreen(dark)
}

private const val STATE_CHECKING = 0
private const val STATE_SIGNED_OUT = 1
private const val STATE_SIGNED_IN = 2

/**
 * Every time the app opens it checks whether somebody is signed in.
 *
 * The signed-in account is kept on the phone, so [FirebaseAuth.currentUser] already knows it when the app
 * starts: the tabs show at once (no waiting screen that hides them) and the check with the server runs
 * silently in the background (see the reload below). Only when nobody is saved on the phone we wait for the
 * first answer of the listener (a moment of background, then the login screen).
 */
@Composable
private fun AuthGate(dark: Boolean) {
    val auth = remember { FirebaseAuth.getInstance() }
    var user by remember { mutableStateOf(auth.currentUser) }
    var checked by remember { mutableStateOf(auth.currentUser != null) }

    DisposableEffect(auth) {
        val listener = FirebaseAuth.AuthStateListener { fa ->
            user = fa.currentUser
            checked = true
        }
        auth.addAuthStateListener(listener)
        onDispose { auth.removeAuthStateListener(listener) }
    }

    // Silent check with the server (the screen stays as it is): if the account was deleted/disabled
    // from Firebase, sign out instead of staying "logged in".
    LaunchedEffect(user?.uid) {
        val current = user ?: return@LaunchedEffect
        try {
            current.reload().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAuthInvalidUserException) {
            auth.signOut()
        } catch (e: Exception) {
            // offline or temporary error: keep the user signed in
        }
    }

    val state = when {
        !checked -> STATE_CHECKING
        user == null -> STATE_SIGNED_OUT
        else -> STATE_SIGNED_IN
    }

    // Where the Google button is: the login -> main wave starts from there.
    var buttonCenter by remember { mutableStateOf<Offset?>(null) }

    AuthWaveHost(
        state = state,
        waveOrigin = { from, to ->
            if (from == STATE_SIGNED_OUT && to == STATE_SIGNED_IN) buttonCenter else null
        },
        modifier = Modifier.fillMaxSize()
    ) { s, viaWave ->
        when (s) {
            STATE_CHECKING -> AppBackground(dark, variant = 0)
            STATE_SIGNED_OUT -> LoginScreen(dark, onButtonCenter = { buttonCenter = it })
            // After the wave starts the pages' own entrance waits until the circle reaches the top.
            else -> MainScreen(dark, introDelayMs = if (viaWave) 450 else 200)
        }
    }
}

@Composable
private fun SetupScreen(dark: Boolean) {
    val textColor = if (dark) Color.White else Color(0xFF101114)
    Box(Modifier.fillMaxSize()) {
        AppBackground(dark, variant = 0)
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
            verticalArrangement = Arrangement.Center
        ) {
            BasicText(
                stringResource(R.string.setup_title),
                style = TextStyle(color = textColor, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(Modifier.height(12.dp))
            BasicText(
                stringResource(R.string.setup_body),
                style = TextStyle(color = textColor.copy(alpha = 0.7f), fontSize = 16.sp)
            )
        }
    }
}
