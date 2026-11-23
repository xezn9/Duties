package com.duties.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/*
 * Channels.kt — channels of the signed-in account, kept on Firestore.
 *
 *   channels/{id}              = { name, username, bio, ownerUid, photo (small JPEG, base64), createdAt }
 *   channelUsernames/{lower}   = { uid, channelId }   (makes every channel @username unique)
 *
 * The Home tab shows them like a chat list. [ChannelsState] listens to the server (and to the local cache,
 * so the list is there at once and a new channel shows up immediately).
 */
class Channel(
    val id: String,
    val name: String,
    val username: String,
    val bio: String,
    val photo: Bitmap?,
    val createdAtMs: Long
)

class ChannelsState(private val uid: String) {
    var channels by mutableStateOf<List<Channel>>(emptyList())
        private set

    private var registration: ListenerRegistration? = null
    private val photoCache = HashMap<String, Pair<Int, Bitmap?>>()   // id -> (length of the text, decoded photo)

    /** Starts listening (call once; [stop] when the screen is gone). */
    fun start() {
        if (registration != null) return
        registration = FirebaseFirestore.getInstance()
            .collection("channels")
            .whereEqualTo("ownerUid", uid)
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                channels = snap.documents.mapNotNull { toChannel(it) }.sortedByDescending { it.createdAtMs }
            }
    }

    fun stop() {
        registration?.remove()
        registration = null
    }

    private fun toChannel(d: DocumentSnapshot): Channel? {
        val name = d.getString("name") ?: return null
        val username = d.getString("username") ?: return null
        val raw = d.getString("photo")
        val photo = if (raw.isNullOrEmpty()) null else {
            val cached = photoCache[d.id]
            if (cached != null && cached.first == raw.length) cached.second
            else decodePhoto(raw).also { photoCache[d.id] = raw.length to it }
        }
        val time = d.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: System.currentTimeMillis()
        return Channel(d.id, name, username, d.getString("bio") ?: "", photo, time)
    }

    /**
     * Creates the channel on the server: the unique @username and the channel in one transaction.
     * Throws [UsernameTakenException] if somebody already has that @username.
     */
    suspend fun create(name: String, username: String, bio: String, photo: Bitmap?) {
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
                "createdAt" to FieldValue.serverTimestamp()
            )
            if (encoded != null) data["photo"] = encoded
            tr.set(channelRef, data)
            null
        }.await()
    }
}

/** 256 px JPEG as base64 text (about 20-40 KB: small enough to live inside the channel document). */
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
