package com.duties

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Channel links.
 *
 *   https://<worker>/c/<channelId>
 *
 * Every channel has its own link (its id never changes, even when the channel is renamed). The link is copied from the edit card
 * of the channel (ChannelEdit.kt). Opened in the browser / Google / any app it brings the person to this app (Android App
 * Links: AndroidManifest.xml + /.well-known/assetlinks.json of the Worker, see push-worker/worker.js); MainActivity puts the id
 * here and MainScreen asks "join this channel?" (or opens it when the person is already inside).
 */
object DeepLink {
    private val ID_RE = Regex("^[A-Za-z0-9_-]{1,128}$")

    /** The link of a channel. */
    fun channelUrl(channelId: String): String = PushConfig.WORKER_URL.trimEnd('/') + "/c/" + channelId

    /** A channel id that arrived from a link and was not handled yet (MainScreen takes it and clears it). */
    var pendingChannelId: String? by mutableStateOf(null)

    /** Reads a VIEW intent: if it is a channel link its id is remembered. Returns true when the intent was a channel link. */
    fun handle(intent: Intent?): Boolean {
        if (intent?.action != Intent.ACTION_VIEW) return false
        val uri = intent.data ?: return false
        val worker = android.net.Uri.parse(PushConfig.WORKER_URL)
        if (!uri.host.equals(worker.host, ignoreCase = true)) return false
        val parts = uri.pathSegments
        if (parts.size < 2 || parts[0] != "c") return false
        val id = parts[1]
        intent.data = null                      // handled: a rotation / recreate must not open it again
        if (!ID_RE.matches(id)) return false
        pendingChannelId = id
        return true
    }
}
