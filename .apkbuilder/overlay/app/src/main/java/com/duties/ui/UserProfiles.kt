package com.duties.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import androidx.compose.runtime.mutableStateMapOf
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/*
 * UserProfiles.kt — makes the profile of a person (name, @username, bio, photo, banner) show with NO waiting.
 *
 * Before: the data was only asked for AFTER the profile page opened, one request after the other
 * (Firestore, then the photos, then the GIF), so the bio / @username / banner came in late.
 *
 * Now:
 *  1) The profile of everyone who wrote in a chat is loaded in the background as soon as the chat shows
 *     (see ChatScreen.kt) - so it is already in memory when his photo is tapped.
 *  2) It is also saved on the phone (files/user_profiles), so even after restarting the app it shows at once,
 *     then the server's newer data replaces it silently.
 *  3) The three requests (Firestore text, photos + banner, GIF banner) run AT THE SAME TIME, not one after
 *     the other, and each one shows its part the moment it arrives.
 */

internal class UserProfileData(
    val name: String,
    val username: String,
    val bio: String,
    val avatar: Bitmap?,
    val banner: ImageSlot,
    val gifStamp: Long = 0L
)

internal class BannerGif(val file: File, val tf: CropTransform, val stamp: Long)

internal object UserProfiles {
    /** uid -> profile. Reading it inside a composable makes that composable update when something arrives. */
    val map = mutableStateMapOf<String, UserProfileData>()

    /** The profile that is open now (never dropped from memory). */
    @Volatile
    var pinned: String? = null

    private const val MAX_IN_MEMORY = 20
    private val EMPTY = UserProfileData("", "", "", null, ImageSlot.Empty)

    private val lock = Any()
    private val inFlight = HashSet<String>()
    private val fetched = HashSet<String>()
    private val order = ArrayList<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun safe(uid: String) = uid.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
    private fun dir(app: Context) = File(app.filesDir, "user_profiles").apply { mkdirs() }

    private inline fun <T> attempt(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun update(uid: String, change: (UserProfileData) -> UserProfileData) {
        synchronized(lock) {
            map[uid] = change(map[uid] ?: EMPTY)
            order.remove(uid)
            order.add(uid)
            while (order.size > MAX_IN_MEMORY) {
                val victim = order.firstOrNull { it != pinned } ?: break
                order.remove(victim)
                map.remove(victim)
            }
        }
    }

    /**
     * Gets the profile of [uid]: the copy saved on the phone shows first, then the server's one.
     * [force] = ask the server again even if it was already asked in this session (used when a profile opens,
     * so a changed bio shows up). Without it, each person is asked once per app start.
     */
    fun request(context: Context, uid: String, force: Boolean = false) {
        if (uid.isBlank()) return
        val app = context.applicationContext
        synchronized(lock) {
            if (uid in inFlight) return
            if (!force && uid in fetched) return
            inFlight.add(uid)
        }
        scope.launch {
            var ok = false
            try {
                if (!map.containsKey(uid)) loadDisk(app, uid)
                ok = fetch(app, uid)
            } finally {
                synchronized(lock) {
                    inFlight.remove(uid)
                    if (ok) fetched.add(uid)
                }
            }
        }
    }

    // ------------------------------------------------------------------ the copy on the phone

    private fun loadDisk(app: Context, uid: String) {
        try {
            val d = dir(app)
            val s = safe(uid)
            val jf = File(d, "$s.json")
            val avF = File(d, "${s}_avatar.txt")
            val bnF = File(d, "${s}_banner.txt")
            if (!jf.exists() && !avF.exists() && !bnF.exists()) return
            val o = if (jf.exists()) JSONObject(jf.readText()) else JSONObject()
            val av = if (avF.exists()) decodeUserPhoto(avF.readText()) else null
            val bn = if (bnF.exists()) decodeUserPhoto(bnF.readText()) else null
            val stamp = o.optLong("gifStamp", 0L)
            val gifFile = if (stamp > 0L) File(File(app.cacheDir, "user_banners"), "${s}_$stamp.gif").takeIf { it.exists() } else null
            val tf = CropTransform(
                o.optDouble("gifZoom", 1.0).toFloat(),
                o.optDouble("gifOx", 0.0).toFloat(),
                o.optDouble("gifOy", 0.0).toFloat()
            )
            val banner = when {
                bn == null -> ImageSlot.Empty
                gifFile != null -> ImageSlot(bn, null, tf, gifFile)
                else -> ImageSlot(bn, null, null)
            }
            if (map.containsKey(uid)) return      // the server already answered
            update(uid) {
                UserProfileData(o.optString("name"), o.optString("username"), o.optString("bio"), av, banner, if (gifFile != null) stamp else 0L)
            }
        } catch (_: Exception) {
            // the copy is only a speed-up
        }
    }

    private fun saveText(app: Context, uid: String) {
        val p = map[uid] ?: return
        try {
            val o = JSONObject()
                .put("name", p.name).put("username", p.username).put("bio", p.bio)
                .put("gifStamp", p.gifStamp)
            p.banner.tf?.let { o.put("gifZoom", it.zoom.toDouble()).put("gifOx", it.ox.toDouble()).put("gifOy", it.oy.toDouble()) }
            val f = File(dir(app), safe(uid) + ".json")
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(o.toString())
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        } catch (_: Exception) {
        }
    }

    private fun saveRaw(app: Context, uid: String, kind: String, raw: String?) {
        try {
            val f = File(dir(app), "${safe(uid)}_$kind.txt")
            if (raw.isNullOrEmpty()) f.delete() else f.writeText(raw)
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------ the server (all at the same time)

    private suspend fun fetch(app: Context, uid: String): Boolean = supervisorScope {
        // 1) name, @username, bio (Firestore users/{uid})
        val textJob = async {
            attempt {
                val doc = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
                if (doc.exists()) {
                    val raw = doc.getString("photo")
                    val small = if (raw.isNullOrEmpty()) null else decodeUserPhoto(raw)
                    val name = doc.getString("name")
                    val username = doc.getString("username") ?: ""
                    val bio = doc.getString("bio") ?: ""
                    update(uid) {
                        UserProfileData(name?.ifBlank { null } ?: it.name, username, bio, it.avatar ?: small, it.banner, it.gifStamp)
                    }
                    saveText(app, uid)
                }
                Unit
            }
        }
        // 2) the full photo and the still banner (Realtime Database profilePics/{uid})
        val picsJob = async {
            attempt {
                val pics = FirebaseDatabase.getInstance().getReference("profilePics").child(uid).get().await()
                val avRaw = pics.child("avatar").getValue(String::class.java)
                val bnRaw = pics.child("banner").getValue(String::class.java)
                val av = avRaw?.let { withContext(Dispatchers.Default) { decodeUserPhoto(it) } }
                val bn = bnRaw?.let { withContext(Dispatchers.Default) { decodeUserPhoto(it) } }
                update(uid) { cur ->
                    // the GIF of the saved copy stays until the server's GIF answer comes
                    val keepGif = bn != null && cur.banner.gif != null
                    UserProfileData(
                        cur.name, cur.username, cur.bio, av ?: cur.avatar,
                        if (bn == null) ImageSlot.Empty
                        else ImageSlot(bn, null, if (keepGif) cur.banner.tf else null, if (keepGif) cur.banner.gif else null),
                        if (keepGif) cur.gifStamp else 0L
                    )
                }
                saveRaw(app, uid, "avatar", avRaw)
                saveRaw(app, uid, "banner", bnRaw)
                saveText(app, uid)
                bn != null
            }
        }
        // 3) the GIF banner (starts now too, shows when it has been downloaded)
        val gifJob = async { attempt { loadBannerGif(app, uid) } }

        val text = textJob.await()
        val pics = picsJob.await()
        val gif = gifJob.await()

        if (pics.getOrNull() == true && gif.isSuccess) {
            val g = gif.getOrNull()
            update(uid) { cur ->
                val still = cur.banner.cropped
                if (still == null) cur
                else UserProfileData(cur.name, cur.username, cur.bio, cur.avatar, ImageSlot(still, null, g?.tf, g?.file), g?.stamp ?: 0L)
            }
            saveText(app, uid)
        }
        text.isSuccess && pics.isSuccess && gif.isSuccess
    }
}

/**
 * The banner GIF of [uid] from bannerGifs/{uid} (see Profile.kt), downloaded into the cache folder (kept until the
 * person changes it). Returns null if there is none; throws if the network fails.
 */
internal suspend fun loadBannerGif(context: Context, uid: String): BannerGif? {
    val ref = FirebaseDatabase.getInstance().getReference("bannerGifs").child(uid)
    val meta = ref.child("meta").get().await()
    if (!meta.exists()) return null
    val chunks = (meta.child("chunks").value as? Number)?.toInt() ?: return null
    val stamp = (meta.child("updatedAt").value as? Number)?.toLong() ?: 0L
    val tf = CropTransform(
        (meta.child("zoom").value as? Number)?.toFloat() ?: 1f,
        (meta.child("ox").value as? Number)?.toFloat() ?: 0f,
        (meta.child("oy").value as? Number)?.toFloat() ?: 0f
    )
    val safe = uid.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
    val dir = File(context.cacheDir, "user_banners").apply { mkdirs() }
    val file = File(dir, "${safe}_$stamp.gif")
    if (!file.exists()) {
        withContext(Dispatchers.IO) {
            dir.listFiles()?.forEach { if (it.name.startsWith(safe + "_")) it.delete() }   // the old ones of this person
        }
        val part = File(dir, "${safe}_$stamp.part")
        try {
            withContext(Dispatchers.IO) {
                part.outputStream().buffered().use { out ->
                    for (i in 0 until chunks) {
                        val text = ref.child("data").child(i.toString()).get().await().getValue(String::class.java)
                            ?: throw IllegalStateException("missing piece $i")
                        out.write(Base64.decode(text, Base64.NO_WRAP))
                    }
                }
                part.renameTo(file)
            }
        } catch (e: Exception) {
            part.delete()
            throw e
        }
    }
    return BannerGif(file, tf, stamp)
}
