package com.duties

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Push notifications (FCM) — works even when the app is closed.
 *
 * How it works:
 *  1. every phone saves its FCM token in the Realtime Database:  fcmTokens/{uid}/{token} = true
 *  2. when somebody sends a message, his app tells the free Cloudflare Worker (push-worker/worker.js)
 *     "message X in channel Y" (with his Firebase login token)
 *  3. the Worker checks the message really exists, finds the channel members' tokens and sends the FCM push
 *  4. [DutiesMessagingService] receives it (even if the app is dead) and shows the notification
 */
object PushConfig {
    /** The address of your Worker after you deploy it (see push-worker/README.md). Example: https://duties-push.hassanain.workers.dev */
    const val WORKER_URL = "https://autumn-mouse-f769.hesenein12345678.workers.dev"
}

object Push {
    const val CHANNEL_ID = "messages"
    const val EXTRA_CHANNEL = "duties_open_channel"

    /** True while the app is on the screen: the chat is live there, so no notification is needed. */
    @Volatile
    var foreground = false

    /** A tap on a notification puts the channel id here; MainScreen opens that chat and clears it. */
    var openChannelId: String? by mutableStateOf(null)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val ch = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.push_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        )
        nm.createNotificationChannel(ch)
    }

    // ---------------------------------------------------------------- token

    /** Saves this phone's FCM token under my account (fire and forget: it is also kept while offline). */
    suspend fun registerToken() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        runCatching {
            val token = FirebaseMessaging.getInstance().token.await()
            FirebaseDatabase.getInstance().getReference("fcmTokens").child(uid).child(token).setValue(true)
        }
    }

    fun registerTokenInBackground() {
        scope.launch { registerToken() }
    }

    /** Sign out: this phone must stop receiving the notifications of the account. Call BEFORE the Firebase sign out. */
    suspend fun unregisterToken() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        runCatching {
            withTimeoutOrNull(4000) {
                val token = FirebaseMessaging.getInstance().token.await()
                FirebaseDatabase.getInstance().getReference("fcmTokens").child(uid).child(token).removeValue().await()
                FirebaseMessaging.getInstance().deleteToken().await()
            }
        }
    }

    // ---------------------------------------------------------------- sending

    /** A message of mine reached the server: ask the Worker to push it to the other members. */
    fun notifyServer(channelId: String, messageId: String, channelName: String) {
        if (PushConfig.WORKER_URL.contains("YOUR-NAME")) return // Worker not set up yet
        val user = FirebaseAuth.getInstance().currentUser ?: return
        scope.launch {
            runCatching {
                val idToken = user.getIdToken(false).await().token ?: return@launch
                val body = JSONObject()
                    .put("channelId", channelId)
                    .put("messageId", messageId)
                    .put("channelName", channelName)
                    .toString()
                val c = URL(PushConfig.WORKER_URL.trimEnd('/') + "/notify").openConnection() as HttpURLConnection
                try {
                    c.requestMethod = "POST"
                    c.connectTimeout = 8000
                    c.readTimeout = 8000
                    c.doOutput = true
                    c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    c.setRequestProperty("Authorization", "Bearer $idToken")
                    c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    c.responseCode // sends the request; the answer is not needed
                } finally {
                    c.disconnect()
                }
            }
        }
    }

    // ---------------------------------------------------------------- showing

    /** Shows (or adds to) the notification of one channel. Messages of the same channel stack in one notification. */
    @SuppressLint("MissingPermission")
    fun show(context: Context, data: Map<String, String>) {
        if (foreground) return
        val channelId = data["channelId"] ?: return
        val text = data["text"] ?: return
        val channelName = data["channelName"].orEmpty().ifBlank { context.getString(R.string.app_name) }
        val sender = data["senderName"].orEmpty().ifBlank { "?" }
        val sentAt = data["ts"]?.toLongOrNull() ?: System.currentTimeMillis()

        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        createChannel(context)

        val id = channelId.hashCode()
        val manager = context.getSystemService(NotificationManager::class.java)
        val existing = runCatching { manager?.activeNotifications?.firstOrNull { it.id == id }?.notification }.getOrNull()
        val style = existing?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
            ?: NotificationCompat.MessagingStyle(Person.Builder().setName(context.getString(R.string.app_name)).build())
        style.setGroupConversation(true)
        style.setConversationTitle(channelName)
        style.addMessage(text, sentAt, Person.Builder().setName(sender).build())

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_CHANNEL, channelId)
        }
        val pending = PendingIntent.getActivity(
            context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_chat)
            .setStyle(style)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setWhen(sentAt)
            .build()
        runCatching { nm.notify(id, notification) }
    }

    /** The chat of this channel is open: its notification (if any) is not needed any more. */
    fun clear(context: Context, channelId: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(channelId.hashCode()) }
    }
}
