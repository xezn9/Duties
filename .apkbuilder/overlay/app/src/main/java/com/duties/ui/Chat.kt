package com.duties.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/*
 * Chat.kt — the chat of every channel lives ONLY in the Realtime Database (see database.rules.json).
 *
 *   chats/{channelId}/owner               = uid of the channel owner
 *   chats/{channelId}/members/{uid}       = true   (who may read and write the chat)
 *   chats/{channelId}/invited/{uid}       = true   (invited at creation: lets him add himself when he accepts)
 *   chats/{channelId}/messages/{pushId}   = { uid, name, text, createdAt }
 *
 * Firestore is not touched by the chat. The channel list itself still comes from Firestore (Channels.kt) and only
 * tells this file which room to open and who is inside.
 */

/** One message of a chat. */
class ChatMessage(
    val id: String,
    val uid: String,
    val name: String,
    val text: String,
    val createdAtMs: Long
)

/**
 * The rooms (owner + members) in the Realtime Database. Every call is "fire and forget": a failure here must never
 * break creating / joining / leaving a channel (the owner's [sync] repairs a room later).
 */
internal object ChatRooms {
    private fun room(channelId: String) = FirebaseDatabase.getInstance().getReference("chats").child(channelId)

    /** New channel: the owner, himself as a member and the invited people (they can join when they accept). */
    fun create(channelId: String, ownerUid: String, invitedUids: List<String>, isPublic: Boolean = true) {
        runCatching {
            val m = HashMap<String, Any?>()
            m["owner"] = ownerUid
            m["public"] = isPublic
            m["members/$ownerUid"] = true
            for (u in invitedUids) m["invited/$u"] = true
            room(channelId).updateChildren(m)
        }
    }

    /** I accepted an invitation: I add myself to the members (and my invitation is used up). */
    fun join(channelId: String, uid: String) {
        runCatching {
            val m = HashMap<String, Any?>()
            m["members/$uid"] = true
            m["invited/$uid"] = null
            room(channelId).updateChildren(m)
        }
    }

    /**
     * I joined a PUBLIC channel (Join button): I add myself to the members of its room. Unlike [join] this waits for the
     * server (max 4 s), so the chat can be opened right after it and I can already read it. If it fails or is slow,
     * nothing breaks: the owner's [sync] adds every member of the channel to the room later.
     */
    suspend fun joinPublic(channelId: String, uid: String) {
        runCatching {
            withTimeoutOrNull(4000) { room(channelId).child("members").child(uid).setValue(true).await() }
        }
    }

    /**
     * Repair for a person that is in the channel (Firestore) but not in its room yet (a public channel that was joined
     * before the room had its "public" flag, or the rules were not published yet): he tries to add himself.
     * It only works when the room allows it (public room, or an invitation); [onDone] gets the answer.
     */
    fun addSelf(channelId: String, uid: String, onDone: (Boolean) -> Unit) {
        runCatching {
            room(channelId).child("members").child(uid).setValue(true)
                .addOnSuccessListener { onDone(true) }
                .addOnFailureListener { onDone(false) }
        }.onFailure { onDone(false) }
    }

    /** I leave. If I was the owner, the room goes to [newOwner] (the same person the channel goes to). */
    fun leave(channelId: String, uid: String, newOwner: String?) {
        runCatching {
            val m = HashMap<String, Any?>()
            m["members/$uid"] = null
            if (newOwner != null) m["owner"] = newOwner
            room(channelId).updateChildren(m)
        }
    }

    /** The channel was deleted: its whole chat goes (only the owner is allowed to do it). */
    fun delete(channelId: String) {
        runCatching { room(channelId).removeValue() }
    }

    private val synced = HashSet<String>()

    /**
     * Owner side, runs whenever the channel list changes: makes sure every channel I own has its room and that
     * everybody who is inside the channel (Firestore "members") is also inside the room. This also repairs the
     * channels that were made before the chat existed.
     */
    fun sync(uid: String, channels: List<Channel>) {
        for (ch in channels) {
            if (!ch.isOwner) continue
            val signature = ch.id + ":" + ch.isPublic + ":" + ch.members.sorted().joinToString(",")
            if (!synced.add(signature)) continue
            runCatching {
                val m = HashMap<String, Any?>()
                m["owner"] = uid
                m["public"] = ch.isPublic
                m["members/$uid"] = true
                for (u in ch.members) m["members/$u"] = true
                room(ch.id).updateChildren(m).addOnFailureListener { synced.remove(signature) }
            }.onFailure { synced.remove(signature) }
        }
    }
}

/** The messages of one channel: live list + sending. Call [start] when the chat opens and [stop] when it is gone. */
class ChatState(
    private val context: Context,
    private val channelId: String,
    private val uid: String,
    private val channelName: String,
    private val senderName: () -> String
) {
    /** Oldest first. */
    var messages by mutableStateOf<List<ChatMessage>>(ChatCache.peek(channelId) ?: emptyList())
        private set

    /** True after the first answer of the server (even if it was an error). */
    var loaded by mutableStateOf(false)
        private set

    /** The server refused to show the chat (not a member yet, no internet for the first time, ...). */
    var failed by mutableStateOf(false)
        private set

    private var query: Query? = null
    private var listener: ValueEventListener? = null
    private var healed = false

    fun start() {
        if (listener != null) return
        // the messages saved on the phone show at once; the server's answer replaces them a moment later
        if (messages.isEmpty()) {
            val app = context.applicationContext
            ChatCache.background {
                val saved = ChatCache.load(app, channelId)
                if (!loaded && messages.isEmpty() && saved.isNotEmpty()) messages = saved
            }
        }
        val q = FirebaseDatabase.getInstance().getReference("chats").child(channelId).child("messages")
            .limitToLast(PAGE)
        val l = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                messages = snapshot.children.mapNotNull { toMessage(it) }
                    .sortedWith(compareBy<ChatMessage> { it.createdAtMs }.thenBy { it.id })
                failed = false
                loaded = true
                ChatCache.save(context, channelId, messages)
            }

            override fun onCancelled(error: DatabaseError) {
                // not in the room yet? add myself once and listen again
                if (!healed) {
                    healed = true
                    val self = this
                    ChatRooms.addSelf(channelId, uid) { ok ->
                        if (ok && listener === self) q.addValueEventListener(self)
                    }
                }
                // with saved messages on the screen the chat is still usable: no error text over them
                failed = messages.isEmpty()
                loaded = true
            }
        }
        q.addValueEventListener(l)
        query = q
        listener = l
    }

    fun stop() {
        val q = query
        val l = listener
        if (q != null && l != null) q.removeEventListener(l)
        query = null
        listener = null
    }

    /**
     * Sends [text] (it shows at once on this phone, the server confirms a moment later).
     * [onFailed] runs if the server refuses it (the message then disappears from the list).
     * Returns false when there is nothing to send.
     */
    fun send(text: String, onFailed: () -> Unit): Boolean {
        val t = text.trim().take(MAX_TEXT)
        if (t.isEmpty()) return false
        val name = senderName().trim().take(24).ifBlank { "?" }
        fun push(retry: Boolean) {
            val ref = FirebaseDatabase.getInstance().getReference("chats").child(channelId).child("messages").push()
            ref.setValue(
                mapOf(
                    "uid" to uid,
                    "name" to name,
                    "text" to t,
                    "createdAt" to ServerValue.TIMESTAMP
                )
            ).addOnSuccessListener {
                // the server has the message: the other members get a push notification (FCM, even if their app is closed)
                ref.key?.let { com.duties.Push.notifyServer(channelId, it, channelName) }
            }.addOnFailureListener {
                if (retry) {
                    // maybe I am not in the room yet: add myself, then try once more
                    ChatRooms.addSelf(channelId, uid) { ok -> if (ok) push(false) else onFailed() }
                } else onFailed()
            }
        }
        push(true)
        return true
    }

    private fun toMessage(s: DataSnapshot): ChatMessage? {
        val id = s.key ?: return null
        val sender = s.child("uid").value as? String ?: return null
        val text = s.child("text").value as? String ?: return null
        return ChatMessage(
            id = id,
            uid = sender,
            name = s.child("name").value as? String ?: "",
            text = text,
            createdAtMs = (s.child("createdAt").value as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    private companion object {
        const val PAGE = 200
        const val MAX_TEXT = 2000
    }
}
