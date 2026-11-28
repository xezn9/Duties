package com.duties

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleNotificationIntent(intent)
        if (savedInstanceState == null) DeepLink.handle(intent)       // a channel link was opened
        setContent { DutiesApp() }
    }

    // the app is already open and a notification is tapped
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
        DeepLink.handle(intent)
    }

    override fun onStart() {
        super.onStart()
        Push.foreground = true
    }

    override fun onStop() {
        super.onStop()
        Push.foreground = false
    }

    /** A tap on a chat notification: remember which channel, MainScreen opens its chat. */
    private fun handleNotificationIntent(i: Intent?) {
        val channelId = i?.getStringExtra(Push.EXTRA_CHANNEL) ?: return
        Push.openChannelId = channelId
        i.removeExtra(Push.EXTRA_CHANNEL)
    }
}
