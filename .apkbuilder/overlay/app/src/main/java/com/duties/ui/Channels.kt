package com.duties.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/*
 * Channels.kt — channels and invitations of the signed-in account, kept on Firestore.
 *
 *   channels/{id}              = { name, username, bio, ownerUid, members[uid], photo (small JPEG, base64), createdAt }
 *   channelUsernames/{lower}   = { uid, channelId }                    (every channel @username is unique)
 *   invites/{channelId}_{toUid} = { channelId, channelName, channelUsername, channelPhoto, fromUid, fromName,
 *                                  fromUsername, toUid, status: pending | accepted | declined, createdAt }
 *
 * The Home tab shows the channels like a chat list, and the pending invites on top (accept / decline).
 */
class Channel(
    val id: String,
    val name: String,
    val username: String,
    val bio: String,
    val photo: Bitmap?,
    val createdAtMs: Long
)

/** A person found by the @username search. */
class FoundUser(val uid: String, val name: String, val username: String)

/** An invitation to join a channel, waiting for an answer. */
class Invite(
    val id: String,
    val channelId: String,
    val channelName: String,
    val channelUsername: String,
    val photo: Bitmap?,
    val fromName: String,
    val fromUsername: String,
    val createdAtMs: Long
)

class ChannelsState(private val uid: String) {
    /** Channels I own or I am a member of (newest first). */
    var channels by mutableStateOf<List<Channel>>(emptyList())
        private set

    /** Invitations that wait for my answer (newest first). */
    var invites by mutableStateOf<List<Invite>>(emptyList())
        private set

    private val registrations = mutableListOf<ListenerRegistration>()
    private var owned: List<Channel> = emptyList()
    private var joined: List<Channel> = emptyList()
    private val photoCache = HashMap<String, Pair<Int, Bitmap?>>()   // key -> (length of the text, decoded photo)

    /** Starts listening (call once; [stop] when the screen is gone). */
    fun start() {
        if (registrations.isNotEmpty()) return
        val db = FirebaseFirestore.getInstance()
        // channels I created (also finds the ones made before "members" existed) ...
        registrations += db.collection("channels").whereEqualTo("ownerUid", uid)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                owned = snap.documents.mapNotNull { toChannel(it) }
                merge()
            }
        // ... and the ones I joined
        registrations += db.collection("channels").whereArrayContains("members", uid)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                joined = snap.documents.mapNotNull { toChannel(it) }
                merge()
            }
        registrations += db.collection("invites").whereEqualTo("toUid", uid).whereEqualTo("status", "pending")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                invites = snap.documents.mapNotNull { toInvite(it) }.sortedByDescending { it.createdAtMs }
            }
    }

    fun stop() {
        registrations.forEach { it.remove() }
        registrations.clear()
    }

    private fun merge() {
        channels = (owned + joined).distinctBy { it.id }.sortedByDescending { it.createdAtMs }
    }

    private fun photoOf(key: String, raw: String?): Bitmap? {
        if (raw.isNullOrEmpty()) return null
        val cached = photoCache[key]
        if (cached != null && cached.first == raw.length) return cached.second
        return decodePhoto(raw).also { photoCache[key] = raw.length to it }
    }

    private fun timeOf(d: DocumentSnapshot): Long =
        d.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: System.currentTimeMillis()

    private fun toChannel(d: DocumentSnapshot): Channel? {
        val name = d.getString("name") ?: return null
        val username = d.getString("username") ?: return null
        return Channel(d.id, name, username, d.getString("bio") ?: "", photoOf("c" + d.id, d.getString("photo")), timeOf(d))
    }

    private fun toInvite(d: DocumentSnapshot): Invite? {
        val channelId = d.getString("channelId") ?: return null
        val name = d.getString("channelName") ?: return null
        return Invite(
            id = d.id,
            channelId = channelId,
            channelName = name,
            channelUsername = d.getString("channelUsername") ?: "",
            photo = photoOf("i" + d.id, d.getString("channelPhoto")),
            fromName = d.getString("fromName") ?: "",
            fromUsername = d.getString("fromUsername") ?: "",
            createdAtMs = timeOf(d)
        )
    }

    /**
     * Creates the channel on the server: the unique @username, the channel and the invitations, in one transaction.
     * Throws [UsernameTakenException] if somebody already has that @username.
     */
    suspend fun create(
        name: String,
        username: String,
        bio: String,
        photo: Bitmap?,
        invitees: List<FoundUser>,
        fromName: String,
        fromUsername: String
    ) {
        val encoded = withContext(Dispatchers.Default) { photo?.let { encodePhoto(it) } }
        val db = FirebaseFirestore.getInstance()
        val lower = username.lowercase()
        val nameRef = db.collection("channelUsernames").document(lower)
        val channelRef = db.collection("channels").document()
        db.runTransaction { tr ->
            if (tr.get(nameRef).exists()) throw UsernameTakenException()
            tr.set(nameRef, mapOf("uid" to uid, "channelId" to channelRef.id))
            val data = hashMapOf<String, Any>(
                "name" to name,
                "username" to username,
                "bio" to bio,
                "ownerUid" to uid,
                "members" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (encoded != null) data["photo"] = encoded
            tr.set(channelRef, data)
            for (u in invitees) {
                val invite = hashMapOf<String, Any>(
                    "channelId" to channelRef.id,
                    "channelName" to name,
                    "channelUsername" to username,
                    "fromUid" to uid,
                    "fromName" to fromName,
                    "fromUsername" to fromUsername,
                    "toUid" to u.uid,
                    "status" to "pending",
                    "createdAt" to FieldValue.serverTimestamp()
                )
                if (encoded != null) invite["channelPhoto"] = encoded
                tr.set(db.collection("invites").document(channelRef.id + "_" + u.uid), invite)
            }
            null
        }.await()
    }

    /** Accept: the invite is marked accepted and I join the channel (both writes together). */
    suspend fun accept(invite: Invite) {
        val db = FirebaseFirestore.getInstance()
        db.batch()
            .update(db.collection("invites").document(invite.id), "status", "accepted")
            .update(db.collection("channels").document(invite.channelId), "members", FieldValue.arrayUnion(uid))
            .commit()
            .await()
    }

    /** Decline: the invite is marked declined (it disappears from Home). */
    suspend fun decline(invite: Invite) {
        FirebaseFirestore.getInstance().collection("invites").document(invite.id)
            .update("status", "declined")
            .await()
    }

    /**
     * Looks for people whose @username starts with [query] (not me). Needs at least 2 letters.
     * usernames/{lowercase name} = { uid }, then users/{uid} gives the display name.
     */
    suspend fun searchUsers(query: String): List<FoundUser> {
        val q = query.trim().removePrefix("@").lowercase()
        if (q.length < 2) return emptyList()
        val db = FirebaseFirestore.getInstance()
        val snap = db.collection("usernames")
            .whereGreaterThanOrEqualTo(FieldPath.documentId(), q)
            .whereLessThanOrEqualTo(FieldPath.documentId(), q + "")
            .limit(12)
            .get()
            .await()
        val uids = snap.documents.mapNotNull { it.getString("uid") }.filter { it != uid }.distinct()
        return coroutineScope {
            uids.map { id ->
                async {
                    val doc = db.collection("users").document(id).get().await()
                    val username = doc.getString("username")
                    if (!doc.exists() || username == null) null
                    else FoundUser(id, doc.getString("name") ?: username, username)
                }
            }.mapNotNull { it.await() }
        }
    }
}

/** 256 px JPEG as base64 text (about 20-40 KB: small enough to live inside a document). */
private fun encodePhoto(src: Bitmap): String {
    val side = 256
    val scaled = if (src.width > side || src.height > side) Bitmap.createScaledBitmap(src, side, side, true) else src
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

private fun decodePhoto(text: String): Bitmap? = try {
    val bytes = Base64.decode(text, Base64.NO_WRAP)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
} catch (_: Exception) {
    null
}
