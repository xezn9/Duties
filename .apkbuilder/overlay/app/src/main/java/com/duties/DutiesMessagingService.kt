package com.duties

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** Receives the FCM pushes — also when the app is closed (Android starts the app's process for it). */
class DutiesMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // new token (first run, or Google refreshed it): save it under the signed-in account
        Push.registerTokenInBackground()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data.isNotEmpty()) Push.show(applicationContext, data)
    }
}
