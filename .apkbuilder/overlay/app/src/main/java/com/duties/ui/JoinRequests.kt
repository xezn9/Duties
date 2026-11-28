package com.duties.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.duties.R
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/*
 * JoinRequests.kt — "ask to join" a PRIVATE channel.
 *
 *   chats/{channelId}/requests/{uid} = { name, username, createdAt }     (Realtime Database, rules in database.rules.json)
 *
 *  - A person that taps Join on a private channel (Home search, or an opened channel link) writes his own request.
 *  - Only the OWNER and the SUPERVISORS of the channel can read the requests (the rules refuse everybody else). They show
 *    inside the chat of the channel as a card with the person's name and two buttons.
 *  - Only the OWNER decides: Accept puts the person in the channel (Firestore "members" + the chat room) and removes the
 *    request; Reject just removes it. A supervisor sees the card but not the buttons.
 *  - The person can read / remove his own request only (so he can not send it twice).
 */

/** One request to join the channel. */
class JoinRequest(
    val uid: String,
    val name: String,
    val username: String,
    val createdAtMs: Long
)

enum class JoinRequestResult { SENT, ALREADY }

internal object JoinRequests {
    private fun room(channelId: String): DatabaseReference =
        FirebaseDatabase.getInstance().getReference("chats").child(channelId)

    /**
     * I ask to join the private channel [channelId]. Returns [JoinRequestResult.ALREADY] when my request is already waiting.
     * Throws when the server did not answer / refused (for example: the channel room does not exist yet, or I am banned).
     */
    suspend fun send(channelId: String, uid: String, name: String, username: String): JoinRequestResult {
        val ref = room(channelId).child("requests").child(uid)
        val existing = withTimeoutOrNull(6000) { ref.get().await() }
            ?: throw IllegalStateException("the server did not answer")
        if (existing.exists()) return JoinRequestResult.ALREADY
        var done = false
        withTimeoutOrNull(6000) {
            ref.setValue(
                mapOf(
                    "name" to name.trim().take(24).ifBlank { "?" },
                    "username" to username.trim().take(24),
                    "createdAt" to ServerValue.TIMESTAMP
                )
            ).await()
            done = true
        }
        if (!done) {
            // slow / offline: do not leave the write queued, it would arrive later without the person knowing
            runCatching { ref.removeValue() }
            throw IllegalStateException("the request did not reach the server")
        }
        return JoinRequestResult.SENT
    }

    /** Owner: accept. The person joins the channel (Firestore) and the chat room, then his request is removed. */
    suspend fun accept(channel: Channel, req: JoinRequest) {
        check(channel.isOwner) { "only the owner can accept a request" }
        FirebaseFirestore.getInstance().collection("channels").document(channel.id)
            .update("members", FieldValue.arrayUnion(req.uid))
            .await()
        val m = HashMap<String, Any?>()
        m["members/${req.uid}"] = true
        m["requests/${req.uid}"] = null
        var done = false
        withTimeoutOrNull(6000) {
            room(channel.id).updateChildren(m).await()
            done = true
        }
        // (if only the room write failed the request stays on the card: pressing Accept again finishes it)
        if (!done) throw IllegalStateException("the chat room did not answer")
    }

    /** Owner: reject. The request is removed. */
    suspend fun reject(channelId: String, req: JoinRequest) {
        var done = false
        withTimeoutOrNull(6000) {
            room(channelId).child("requests").child(req.uid).removeValue().await()
            done = true
        }
        if (!done) throw IllegalStateException("the chat room did not answer")
    }
}

/** Asks to join a private channel and tells the person what happened (a short message). Never throws. */
internal suspend fun requestToJoinWithToast(
    context: Context,
    channels: ChannelsState,
    channel: Channel,
    name: String,
    username: String
) {
    val text = try {
        when (channels.requestJoin(channel, name, username)) {
            JoinRequestResult.SENT -> context.getString(R.string.join_req_sent)
            JoinRequestResult.ALREADY -> context.getString(R.string.join_req_already)
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: BannedException) {
        context.getString(R.string.error_banned_join)
    } catch (e: Throwable) {
        context.getString(R.string.join_req_failed)
    }
    Toast.makeText(context, text, Toast.LENGTH_LONG).show()
}

/**
 * The live list of the requests of one channel (oldest first). Only the owner and the supervisors can read them, so with
 * [enabled] = false nothing is listened to. Call [start] when the chat opens and [stop] when it is gone.
 */
class JoinRequestsState(private val channelId: String, private val enabled: Boolean) {
    var requests by mutableStateOf<List<JoinRequest>>(emptyList())
        private set

    private var ref: DatabaseReference? = null
    private var listener: ValueEventListener? = null

    fun start() {
        if (!enabled || listener != null) return
        val r = FirebaseDatabase.getInstance().getReference("chats").child(channelId).child("requests")
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                requests = snapshot.children.mapNotNull { toRequest(it) }.sortedBy { it.createdAtMs }
            }

            override fun onCancelled(error: DatabaseError) {
                requests = emptyList()
            }
        }
        r.addValueEventListener(l)
        ref = r
        listener = l
    }

    fun stop() {
        val r = ref
        val l = listener
        if (r != null && l != null) r.removeEventListener(l)
        ref = null
        listener = null
    }

    private fun toRequest(s: DataSnapshot): JoinRequest? {
        val uid = s.key ?: return null
        return JoinRequest(
            uid = uid,
            name = s.child("name").value as? String ?: "?",
            username = s.child("username").value as? String ?: "",
            createdAtMs = (s.child("createdAt").value as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }
}

/**
 * The card of one request, inside the chat (same white round look as a message). The owner sees Accept / Reject, a
 * supervisor only sees that the owner has to decide.
 */
@Composable
internal fun JoinRequestCard(
    req: JoinRequest,
    canDecide: Boolean,
    busy: Boolean,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    LaunchedEffect(req.uid) { UserPhotos.request(context, req.uid) }
    val photo = UserPhotos.photos[req.uid]
    val ink = Color(0xFF101114)
    val sub = Color(0xFF6B6F7B)

    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .background(Color.White, RoundedCornerShape(22.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(photo, req.name, 42.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                BasicText(
                    stringResource(R.string.join_req_title),
                    style = TextStyle(color = AppAccent.deep(Color(0xFFDB7608)), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                )
                BasicText(
                    req.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(
                        color = ink, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        textDirection = TextDirection.Content
                    )
                )
                if (req.username.isNotBlank()) {
                    BasicText(
                        "@" + req.username,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(color = sub, fontSize = 13.sp, textDirection = TextDirection.Ltr)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        BasicText(
            stringResource(R.string.join_req_body),
            style = TextStyle(color = ink, fontSize = 14.sp, lineHeight = 21.sp)
        )
        Spacer(Modifier.height(10.dp))
        if (canDecide) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // first child = the right end (the app is right-to-left): Accept, then Reject
                RequestButton(
                    text = if (busy) "…" else stringResource(R.string.join_req_accept),
                    fill = AppAccent.deep(Color(0xFFDB7608)),
                    textColor = Color.White,
                    enabled = !busy,
                    onClick = onAccept,
                    modifier = Modifier.weight(1f)
                )
                RequestButton(
                    text = stringResource(R.string.join_req_reject),
                    fill = Color(0xFFEDEEF1),
                    textColor = ink,
                    enabled = !busy,
                    onClick = onReject,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            BasicText(
                stringResource(R.string.join_req_wait_owner),
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(color = sub, fontSize = 13.sp, textAlign = TextAlign.Center)
            )
        }
    }
}

@Composable
private fun RequestButton(
    text: String,
    fill: Color,
    textColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(fill)
            .pointerInput(enabled) {
                if (enabled) detectTapGestures { onClick() }
            },
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text,
            maxLines = 1,
            style = TextStyle(color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        )
    }
}
