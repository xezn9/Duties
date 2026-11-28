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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/*
 * Channels.kt — channels and invitations of the signed-in account, kept on Firestore.
 *
 *   channels/{id}              = { name, username, bio, ownerUid, members[uid], photo (small JPEG, base64), isPublic, createdAt,
 *                                  admins[uid], adminPerms{uid: ["ban","kick","edit"]}, adminTitles{uid: "custom name"}, banned[uid],
 *                                  restricted[uid] }
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
    val createdAtMs: Long,
    val ownerUid: String,
    /** How many people are in the channel right now (the owner counts). */
    val memberCount: Int,
    /** True when the signed-in account owns the channel (it may delete it). */
    val isOwner: Boolean,
    /** uids of everybody inside (used to prepare the chat room in the Realtime Database, see Chat.kt). */
    val members: List<String> = emptyList(),
    /** Public (true) or private (false), chosen when the channel was created. Old channels have none = public. */
    val isPublic: Boolean = true,
    /** uids of the supervisors (they can write in the channel, like the owner). Only the owner changes this list. */
    val admins: List<String> = emptyList(),
    /**
     * What each supervisor may do (the owner decides, supervisor by supervisor): uid -> some of [Perm.BAN], [Perm.KICK],
     * [Perm.EDIT]. A supervisor that is not in the map can only write messages.
     */
    val adminPerms: Map<String, Set<String>> = emptyMap(),
    /** The name the OWNER gave to each supervisor (shown next to him instead of "Supervisor"). No entry = the default word. */
    val adminTitles: Map<String, String> = emptyMap(),
    /** uids of the banned people: they are not members and can not join (or accept an invitation) until the ban is lifted. */
    val banned: List<String> = emptyList(),
    /** True when the signed-in account is banned from this channel (the search shows "banned" instead of Join). */
    val bannedMe: Boolean = false,
    /**
     * uids of the RESTRICTED members (only people who are still inside). A restricted member stays in the channel and can read
     * it, but the list of members is hidden from him. The owner, or a supervisor with the "ban" permission, restricts / frees him.
     */
    val restricted: List<String> = emptyList()
) {
    /** Only the owner and the supervisors can write in the channel; everybody else only reads. */
    fun canWrite(uid: String): Boolean = ownerUid == uid || uid in admins

    /** The owner can do everything; a supervisor only what the owner gave him; a member nothing. */
    fun can(uid: String, perm: String): Boolean =
        ownerUid == uid || (uid in admins && perm in (adminPerms[uid] ?: emptySet()))
}

/** Longest custom name the owner can give to a supervisor. */
const val ADMIN_TITLE_MAX = 14

/** The things the owner can allow a supervisor to do. The values are what is stored in Firestore / the Realtime Database. */
object Perm {
    const val BAN = "ban"
    const val KICK = "kick"
    const val EDIT = "edit"
    val ALL = listOf(BAN, KICK, EDIT)
}

/** Thrown when a banned person tries to join the channel. */
class BannedException : Exception("banned from this channel")

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
        val sig = raw.length xor raw.hashCode()
        val cached = photoCache[key]
        if (cached != null && cached.first == sig) return cached.second
        return decodePhoto(raw).also { photoCache[key] = sig to it }
    }

    private fun timeOf(d: DocumentSnapshot): Long =
        d.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
            ?: System.currentTimeMillis()

    private fun toChannel(d: DocumentSnapshot): Channel? {
        val name = d.getString("name") ?: return null
        val username = d.getString("username") ?: return null
        val owner = d.getString("ownerUid") ?: ""
        val memberUids = (d.get("members") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
        val members = memberUids.size
        // only people who are still inside count as supervisors
        val adminUids = (d.get("admins") as? List<*>)?.filterIsInstance<String>()?.filter { it in memberUids } ?: emptyList()
        // what each supervisor may do: { uid: ["ban", "kick", "edit"] } (unknown words are ignored)
        val perms = HashMap<String, Set<String>>()
        (d.get("adminPerms") as? Map<*, *>)?.forEach { (k, v) ->
            if (k is String && k in adminUids) {
                perms[k] = (v as? List<*>)?.filterIsInstance<String>()?.filter { it in Perm.ALL }?.toSet() ?: emptySet()
            }
        }
        // the custom names the owner gave to the supervisors: { uid: "name" } (only for people who are still supervisors)
        val titles = HashMap<String, String>()
        (d.get("adminTitles") as? Map<*, *>)?.forEach { (k, v) ->
            if (k is String && k in adminUids && v is String && v.isNotBlank()) titles[k] = v.take(ADMIN_TITLE_MAX)
        }
        val bannedUids = (d.get("banned") as? List<*>)?.filterIsInstance<String>()?.distinct() ?: emptyList()
        // only people who are still inside count as restricted (a person who left, or was kicked / banned, is not)
        val restrictedUids = (d.get("restricted") as? List<*>)?.filterIsInstance<String>()
            ?.filter { it in memberUids && it != owner && it !in adminUids }?.distinct() ?: emptyList()
        return Channel(
            id = d.id,
            name = name,
            username = username,
            bio = d.getString("bio") ?: "",
            photo = photoOf("c" + d.id, d.getString("photo")),
            createdAtMs = timeOf(d),
            ownerUid = owner,
            memberCount = members.coerceAtLeast(1),
            isOwner = owner == uid,
            members = memberUids,
            isPublic = d.getBoolean("isPublic") ?: true,
            admins = adminUids,
            adminPerms = perms,
            adminTitles = titles,
            banned = bannedUids,
            bannedMe = uid in bannedUids,
            restricted = restrictedUids
        )
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
        fromUsername: String,
        isPublic: Boolean = true
    ) {
        val encoded = withContext(Dispatchers.Default) { photo?.let { encodePhoto(it) } }
        val db = FirebaseFirestore.getInstance()
        val lower = username.lowercase()
        val nameRef = db.collection("channelUsernames").document(lower)
        val channelRef = db.collection("channels").document()
        // the chat room (Realtime Database) is prepared first, so nobody else can claim this channel's chat
        ChatRooms.create(channelRef.id, uid, invitees.map { it.uid }, isPublic)
        try {
            runCreateTransaction(db, nameRef, channelRef, name, username, bio, encoded, invitees, fromName, fromUsername, isPublic)
        } catch (e: Exception) {
            ChatRooms.delete(channelRef.id)
            throw e
        }
    }

    private suspend fun runCreateTransaction(
        db: FirebaseFirestore,
        nameRef: com.google.firebase.firestore.DocumentReference,
        channelRef: com.google.firebase.firestore.DocumentReference,
        name: String,
        username: String,
        bio: String,
        encoded: String?,
        invitees: List<FoundUser>,
        fromName: String,
        fromUsername: String,
        isPublic: Boolean
    ) {
        db.runTransaction { tr ->
            if (tr.get(nameRef).exists()) throw UsernameTakenException()
            tr.set(nameRef, mapOf("uid" to uid, "channelId" to channelRef.id))
            val data = hashMapOf<String, Any>(
                "name" to name,
                "username" to username,
                "bio" to bio,
                "ownerUid" to uid,
                "members" to listOf(uid),
                "isPublic" to isPublic,
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

    /**
     * Edits the channel (the owner, or a supervisor with the "edit" permission): its name, its unique @username, its bio
     * and its photo, in one transaction.
     *  - the new @username is claimed in channelUsernames (it always belongs to the OWNER, also when a supervisor renames
     *    the channel) and the old one is released (UsernameTakenException if it is taken).
     *  - [photoChanged] = false keeps the old photo as it is; true writes [photo] (null removes it).
     * [isPublic] (null = unchanged) switches the channel between public and private; the room's "public" flag in the Realtime
     * Database follows at once (and ChatRooms.sync on the owner's phone repairs it if that failed).
     * Everybody's Home list and chat header update by themselves (their listeners see the new document).
     */
    suspend fun updateChannel(
        channel: Channel, name: String, username: String, bio: String, photo: Bitmap?, photoChanged: Boolean,
        isPublic: Boolean? = null
    ) {
        val encoded = if (photoChanged) withContext(Dispatchers.Default) { photo?.let { encodePhoto(it) } } else null
        val db = FirebaseFirestore.getInstance()
        val channelRef = db.collection("channels").document(channel.id)
        val oldLower = channel.username.lowercase()
        val newLower = username.lowercase()
        val renamed = oldLower != newLower
        val oldRef = db.collection("channelUsernames").document(oldLower)
        val newRef = db.collection("channelUsernames").document(newLower)
        db.runTransaction { tr ->
            // all reads first, then the writes
            val taken = if (renamed) tr.get(newRef).exists() else false
            val oldDoc = if (renamed) tr.get(oldRef) else null
            if (taken) throw UsernameTakenException()
            if (renamed) {
                tr.set(newRef, mapOf("uid" to channel.ownerUid, "channelId" to channel.id))
                // only the claim of THIS channel is released (it is the owner's, or it names this channel)
                if (oldDoc != null && oldDoc.exists() &&
                    (oldDoc.getString("uid") == channel.ownerUid || oldDoc.getString("channelId") == channel.id)
                ) tr.delete(oldRef)
            }
            val data = hashMapOf<String, Any>("name" to name, "username" to username, "bio" to bio)
            if (photoChanged) data["photo"] = encoded ?: FieldValue.delete()
            // public <-> private: the owner, or a supervisor with the "edit" permission (firestore.rules); null = leave it as it is
            if (isPublic != null) data["isPublic"] = isPublic
            tr.update(channelRef, data)
            null
        }.await()
        if (isPublic != null) ChatRooms.setPublic(channel.id, isPublic)
    }

    /**
     * Owner only: makes [targetUid] a supervisor ([admin] = true) or a normal member again.
     * A new supervisor starts with NO permissions (the owner gives them one by one, see [setAdminPerm]); a supervisor that
     * is taken back loses all of them. The Realtime Database room gets the same, because THERE the server decides who may
     * write, kick and ban.
     */
    suspend fun setAdmin(channel: Channel, targetUid: String, admin: Boolean) {
        if (targetUid == channel.ownerUid) return
        val ref = FirebaseFirestore.getInstance().collection("channels").document(channel.id)
        if (admin) {
            ref.update("admins", FieldValue.arrayUnion(targetUid)).await()
        } else {
            ref.update(
                "admins", FieldValue.arrayRemove(targetUid),
                FieldPath.of("adminPerms", targetUid), FieldValue.delete(),
                FieldPath.of("adminTitles", targetUid), FieldValue.delete()
            ).await()
        }
        ChatRooms.setAdmin(channel.id, targetUid, admin)
    }

    /** Owner only: allows ([on] = true) or forbids one thing ([Perm]) to one supervisor. */
    suspend fun setAdminPerm(channel: Channel, targetUid: String, perm: String, on: Boolean) {
        if (perm !in Perm.ALL || targetUid == channel.ownerUid || targetUid !in channel.admins) return
        val ref = FirebaseFirestore.getInstance().collection("channels").document(channel.id)
        // arrayUnion / arrayRemove: two quick taps on two switches can not overwrite each other
        ref.update(
            FieldPath.of("adminPerms", targetUid),
            if (on) FieldValue.arrayUnion(perm) else FieldValue.arrayRemove(perm)
        ).await()
        ChatRooms.setAdminPerm(channel.id, targetUid, perm, on)
    }

    /**
     * Owner only: the name that is shown next to a supervisor instead of "Supervisor". An empty [title] goes back to the
     * default word. It is only a label (nothing in the chat room depends on it), so it lives in Firestore only.
     */
    suspend fun setAdminTitle(channel: Channel, targetUid: String, title: String) {
        if (!channel.isOwner || targetUid == channel.ownerUid || targetUid !in channel.admins) return
        val clean = title.trim().take(ADMIN_TITLE_MAX)
        val ref = FirebaseFirestore.getInstance().collection("channels").document(channel.id)
        ref.update(
            FieldPath.of("adminTitles", targetUid),
            if (clean.isEmpty()) FieldValue.delete() else clean
        ).await()
    }

    /**
     * The owner, or a supervisor with the "kick" permission: removes a member from the channel (and from its chat room).
     * He can still join again if the channel is public. Nobody can kick the owner, and a supervisor can not kick another
     * supervisor (only the owner can).
     */
    suspend fun kickMember(channel: Channel, targetUid: String) {
        if (targetUid == channel.ownerUid || targetUid == uid) return
        val asOwner = channel.isOwner
        if (!asOwner && (!channel.can(uid, Perm.KICK) || targetUid in channel.admins)) return
        val ref = FirebaseFirestore.getInstance().collection("channels").document(channel.id)
        if (asOwner) {
            ref.update(
                "members", FieldValue.arrayRemove(targetUid),
                "admins", FieldValue.arrayRemove(targetUid),
                "restricted", FieldValue.arrayRemove(targetUid),
                FieldPath.of("adminPerms", targetUid), FieldValue.delete(),
                FieldPath.of("adminTitles", targetUid), FieldValue.delete()
            ).await()
        } else {
            ref.update("members", FieldValue.arrayRemove(targetUid)).await()
        }
        ChatRooms.kick(channel.id, targetUid, asOwner)
    }

    /**
     * The owner, or a supervisor with the "ban" permission: bans a member. He goes out of the channel (and its chat room)
     * and can NOT come back (no Join button, no invitation) until the ban is lifted with [unbanMember].
     * Nobody can ban the owner, and a supervisor can not ban another supervisor (only the owner can).
     *
     * The chat room is closed to him FIRST: if that fails nothing has changed and the exception goes to the caller.
     */
    suspend fun banMember(channel: Channel, targetUid: String) {
        if (targetUid == channel.ownerUid || targetUid == uid) return
        val asOwner = channel.isOwner
        if (!asOwner && (!channel.can(uid, Perm.BAN) || targetUid in channel.admins)) return
        ChatRooms.ban(channel.id, targetUid, asOwner)
        val ref = FirebaseFirestore.getInstance().collection("channels").document(channel.id)
        try {
            if (asOwner) {
                ref.update(
                    "members", FieldValue.arrayRemove(targetUid),
                    "banned", FieldValue.arrayUnion(targetUid),
                    "restricted", FieldValue.arrayRemove(targetUid),
                    "admins", FieldValue.arrayRemove(targetUid),
                    FieldPath.of("adminPerms", targetUid), FieldValue.delete(),
                    FieldPath.of("adminTitles", targetUid), FieldValue.delete()
                ).await()
            } else {
                ref.update(
                    "members", FieldValue.arrayRemove(targetUid),
                    "banned", FieldValue.arrayUnion(targetUid)
                ).await()
            }
        } catch (e: Exception) {
            // the channel was not changed: open the room's ban again (best effort; the room's "members" are repaired by the owner's sync)
            ChatRooms.unbanQuietly(channel.id, targetUid)
            throw e
        }
    }

    /** The owner, or a supervisor with the "ban" permission: lifts the ban, so the person can join / be invited again. */
    suspend fun unbanMember(channel: Channel, targetUid: String) {
        if (!channel.can(uid, Perm.BAN)) return
        // the room first (it must not keep blocking him), then the channel
        ChatRooms.unban(channel.id, targetUid)
        FirebaseFirestore.getInstance().collection("channels").document(channel.id)
            .update("banned", FieldValue.arrayRemove(targetUid))
            .await()
    }

    /**
     * The owner, or a supervisor with the "ban" permission: restricts ([on] = true) or frees ([on] = false) an ORDINARY member.
     * A restricted member stays in the channel and can read it, but the list of members is hidden from him.
     * Nobody can restrict the owner or a supervisor, or himself. It only changes the channel (Firestore): nothing in the
     * chat room depends on it.
     */
    suspend fun restrictMember(channel: Channel, targetUid: String, on: Boolean) {
        if (targetUid == channel.ownerUid || targetUid == uid) return
        if (!channel.can(uid, Perm.BAN) || targetUid in channel.admins) return
        FirebaseFirestore.getInstance().collection("channels").document(channel.id)
            .update("restricted", if (on) FieldValue.arrayUnion(targetUid) else FieldValue.arrayRemove(targetUid))
            .await()
    }

    /** Accept: the invite is marked accepted and I join the channel (both writes together). */
    suspend fun accept(invite: Invite) {
        val db = FirebaseFirestore.getInstance()
        db.batch()
            .update(db.collection("invites").document(invite.id), "status", "accepted")
            .update(db.collection("channels").document(invite.channelId), "members", FieldValue.arrayUnion(uid))
            .commit()
            .await()
        // I am inside the chat of this channel too
        ChatRooms.join(invite.channelId, uid)
    }

    /**
     * Join a PUBLIC channel found by the search (no invitation needed): my uid is added to the channel's members
     * and I am inside its chat room too. Then the channel shows up in my Home list by itself (my listener sees it).
     */
    suspend fun join(channel: Channel) {
        if (!channel.isPublic) return
        // a banned person can not come back until the ban is lifted (the server refuses it too, in both databases)
        if (channel.bannedMe) throw BannedException()
        FirebaseFirestore.getInstance().collection("channels").document(channel.id)
            .update("members", FieldValue.arrayUnion(uid))
            .await()
        ChatRooms.joinPublic(channel.id, uid)
    }

    /**
     * One channel by its id (a channel link was opened). null = there is no such channel. Throws when the server can not be
     * reached. Any signed-in account may read a channel (firestore.rules), a member or not.
     */
    suspend fun fetchChannel(id: String): Channel? {
        val ref = FirebaseFirestore.getInstance().collection("channels").document(id)
        var last: Throwable? = null
        // a link can be opened right when the app starts, before the connection to Firestore is up: try up to 3 times
        for (attempt in 1..3) {
            try {
                val d = ref.get().await()
                return if (d.exists()) toChannel(d) else null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                last = e
                // a refusal by the rules will not fix itself
                if (e is com.google.firebase.firestore.FirebaseFirestoreException &&
                    e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
                ) throw e
                if (attempt < 3) kotlinx.coroutines.delay(1500)
            }
        }
        throw last ?: IllegalStateException("fetchChannel failed")
    }

    /** Decline: the invite is marked declined (it disappears from Home). */
    suspend fun decline(invite: Invite) {
        FirebaseFirestore.getInstance().collection("invites").document(invite.id)
            .update("status", "declined")
            .await()
    }

    /**
     * Deletes the channel for everybody (owner only): the channel, its unique @username and the invitations
     * that were sent for it. The members' Home lists update by themselves (their listeners see it disappear).
     */
    suspend fun deleteChannel(channel: Channel) {
        val db = FirebaseFirestore.getInstance()
        // the invitations I sent for this channel (a query cannot run inside a transaction, so it goes first)
        val invites = db.collection("invites")
            .whereEqualTo("fromUid", uid)
            .whereEqualTo("channelId", channel.id)
            .get()
            .await()
        val channelRef = db.collection("channels").document(channel.id)
        val nameRef = db.collection("channelUsernames").document(channel.username.lowercase())
        db.runTransaction { tr ->
            val nameDoc = tr.get(nameRef)
            for (d in invites.documents) tr.delete(d.reference)
            if (nameDoc.exists() && nameDoc.getString("uid") == uid) tr.delete(nameRef)
            tr.delete(channelRef)
            null
        }.await()
        // the chat of the channel goes with it
        ChatRooms.delete(channel.id)
    }

    /**
     * Leaves the channel.
     *  - a member: just removes himself from "members".
     *  - the owner, with other people inside: the channel (and its @username) goes to the first other member.
     *  - the owner, alone: nobody is left to own it, so it is deleted.
     */
    suspend fun leaveChannel(channel: Channel) {
        val db = FirebaseFirestore.getInstance()
        val channelRef = db.collection("channels").document(channel.id)
        if (!channel.isOwner) {
            channelRef.update("members", FieldValue.arrayRemove(uid)).await()
            ChatRooms.leave(channel.id, uid, null)
            return
        }
        val nameRef = db.collection("channelUsernames").document(channel.username.lowercase())
        var deleteInstead = false
        var newOwner: String? = null
        db.runTransaction { tr ->
            deleteInstead = false                      // a transaction may run again: start clean
            newOwner = null
            val fresh = tr.get(channelRef)
            val nameDoc = tr.get(nameRef)
            val list = (fresh.get("members") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val next = list.firstOrNull { it != uid }
            if (next == null) {
                deleteInstead = true
            } else {
                newOwner = next
                tr.update(channelRef, mapOf("ownerUid" to next, "members" to list.filter { it != uid }))
                if (nameDoc.exists()) tr.update(nameRef, "uid", next)
            }
            null
        }.await()
        if (deleteInstead) deleteChannel(channel) else ChatRooms.leave(channel.id, uid, newOwner)
    }

    // ---------------------------------------------------------------- search for people

    /** (lowercase @username, uid) of the first accounts: used to find names that are SIMILAR, not only the same start. */
    private var usernamePool: List<Pair<String, String>> = emptyList()
    private var poolLoadedAtMs = 0L

    private suspend fun loadPool(db: FirebaseFirestore): List<Pair<String, String>> {
        val now = System.currentTimeMillis()
        if (usernamePool.isNotEmpty() && now - poolLoadedAtMs < POOL_MAX_AGE_MS) return usernamePool
        val snap = db.collection("usernames").limit(POOL_SIZE).get().await()
        usernamePool = snap.documents.mapNotNull { d -> d.getString("uid")?.let { d.id to it } }
        poolLoadedAtMs = now
        return usernamePool
    }

    /**
     * Looks for people whose @username is the same as, starts with, contains or is SIMILAR to [query]
     * (a typo or two does not matter), best match first, never me. Needs at least 2 letters.
     * usernames/{lowercase name} = { uid }, then users/{uid} gives the display name.
     */
    suspend fun searchUsers(query: String): List<FoundUser> {
        val q = query.trim().removePrefix("@").lowercase()
        if (q.length < 2) return emptyList()
        val db = FirebaseFirestore.getInstance()

        val names = LinkedHashMap<String, String>()          // lowercase username -> uid
        // 1) names that start with it (exact, asked from the server)
        val snap = db.collection("usernames")
            .whereGreaterThanOrEqualTo(FieldPath.documentId(), q)
            .whereLessThanOrEqualTo(FieldPath.documentId(), q + "\uf8ff")
            .limit(20)
            .get()
            .await()
        snap.documents.forEach { d -> d.getString("uid")?.let { names[d.id] = it } }
        // 2) similar names (checked here on the phone, over a small cached list)
        try {
            loadPool(db).forEach { (n, u) -> names.putIfAbsent(n, u) }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            // the prefix result is still good
        }

        val ranked = names.entries
            .filter { it.value != uid }
            .map { it to similarity(q, it.key) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(MAX_RESULTS)
            .map { it.first.value }
            .distinct()

        return coroutineScope {
            ranked.map { id ->
                async {
                    val doc = db.collection("users").document(id).get().await()
                    val username = doc.getString("username")
                    if (!doc.exists() || username == null) null
                    else FoundUser(id, doc.getString("name") ?: username, username)
                }
            }.map { it.await() }.filterNotNull()
        }
    }
    // ---------------------------------------------------------------- search for channels

    /** (lowercase channel @username, channelId) of the first channels: for names that are SIMILAR, not only the same start. */
    private var channelPool: List<Pair<String, String>> = emptyList()
    private var channelPoolLoadedAtMs = 0L

    private suspend fun loadChannelPool(db: FirebaseFirestore): List<Pair<String, String>> {
        val now = System.currentTimeMillis()
        if (channelPool.isNotEmpty() && now - channelPoolLoadedAtMs < POOL_MAX_AGE_MS) return channelPool
        val snap = db.collection("channelUsernames").limit(POOL_SIZE).get().await()
        channelPool = snap.documents.mapNotNull { d -> d.getString("channelId")?.let { d.id to it } }
        channelPoolLoadedAtMs = now
        return channelPool
    }

    /**
     * Looks for channels (any channel, not only mine) by @username or by name, best match first.
     *  - @username: the same as, starts with, contains or is SIMILAR to the typed text (a typo or two does not matter).
     *    channelUsernames/{lowercase name} = { uid, channelId }, then channels/{id} gives the channel itself.
     *  - name: the names that START with the typed text (typed as it is, in lowercase, or with a capital first letter).
     * Needs at least 2 letters. Throws if every part of the search failed (no connection, no permission).
     */
    suspend fun searchChannels(query: String): List<Channel> {
        val raw = query.trim().removePrefix("@")
        val q = raw.lowercase()
        if (q.length < 2) return emptyList()
        val db = FirebaseFirestore.getInstance()

        suspend fun part(block: suspend () -> List<Channel>): Result<List<Channel>> = try {
            Result.success(block())
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

        val parts = coroutineScope {
            val jobs = ArrayList<kotlinx.coroutines.Deferred<Result<List<Channel>>>>()
            // 1) the name starts with it
            for (v in linkedSetOf(raw, raw.lowercase(), raw.replaceFirstChar { it.uppercase() })) {
                jobs += async {
                    part {
                        db.collection("channels")
                            .whereGreaterThanOrEqualTo("name", v)
                            .whereLessThanOrEqualTo("name", v + "\uf8ff")
                            .limit(10)
                            .get()
                            .await()
                            .documents.mapNotNull { toChannel(it) }
                    }
                }
            }
            // 2) the @username is the same / starts with it / is similar
            jobs += async {
                part {
                    coroutineScope {
                        val ids = LinkedHashMap<String, String>()            // lowercase username -> channelId
                        val snap = db.collection("channelUsernames")
                            .whereGreaterThanOrEqualTo(FieldPath.documentId(), q)
                            .whereLessThanOrEqualTo(FieldPath.documentId(), q + "\uf8ff")
                            .limit(20)
                            .get()
                            .await()
                        snap.documents.forEach { d -> d.getString("channelId")?.let { ids[d.id] = it } }
                        try {
                            loadChannelPool(db).forEach { (n, id) -> ids.putIfAbsent(n, id) }
                        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            // the prefix result is still good
                        }
                        val best = ids.entries
                            .map { it to similarity(q, it.key) }
                            .filter { it.second > 0 }
                            .sortedByDescending { it.second }
                            .take(MAX_RESULTS)
                            .map { it.first.value }
                            .distinct()
                        best.map { id ->
                            // one channel that cannot be read must not break the whole search
                            async { runCatching { toChannel(db.collection("channels").document(id).get().await()) }.getOrNull() }
                        }.awaitAll().filterNotNull()
                    }
                }
            }
            jobs.awaitAll()
        }
        if (parts.all { it.isFailure }) throw parts.first().exceptionOrNull() ?: IllegalStateException("search failed")

        val found = LinkedHashMap<String, Channel>()
        parts.forEach { r -> r.getOrNull()?.forEach { c -> found.putIfAbsent(c.id, c) } }
        return found.values
            .map { c -> c to maxOf(similarity(q, c.username.lowercase()), similarity(q, c.name.lowercase())) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(MAX_RESULTS)
            .map { it.first }
    }
}

private const val POOL_SIZE = 600L
private const val POOL_MAX_AGE_MS = 5 * 60_000L
private const val MAX_RESULTS = 15

/** How much [name] looks like what was typed ([q]); 0 = not similar at all. Both are lowercase. */
private fun similarity(q: String, name: String): Int {
    if (name == q) return 1000
    if (name.startsWith(q)) return 900 - (name.length - q.length)
    val at = name.indexOf(q)
    if (at >= 0) return 700 - at * 5 - (name.length - q.length)
    if (q.length >= 3) {
        // a typo or two: compare with the whole name and with its beginning of the same length
        val d = minOf(editDistance(q, name), editDistance(q, name.take(q.length)))
        val allowed = when {
            q.length <= 4 -> 1
            q.length <= 7 -> 2
            else -> 3
        }
        if (d <= allowed) return 500 - d * 100
        // the typed letters appear in the same order inside the name (n-w-y in "noway")
        var i = 0
        for (c in name) if (i < q.length && c == q[i]) i++
        if (i == q.length) return 200
    }
    return 0
}

/** Levenshtein distance (number of letters to add / remove / change). */
private fun editDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var prev = IntArray(b.length + 1) { it }
    var cur = IntArray(b.length + 1)
    for (i in 1..a.length) {
        cur[0] = i
        for (j in 1..b.length) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
        }
        val t = prev; prev = cur; cur = t
    }
    return prev[b.length]
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
