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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/*
 * UserProfiles.kt — makes the profile of a person (name, @username, bio, photo, banner) show with NO waiting.
 *
 * What was slow before, and what changed:
 *
 *  1) A profile that was pushed out of memory (more than 20 people) was still marked "already fetched", so it was
 *     never loaded again until you tapped it -> the page opened empty and filled in 1-2 seconds later.
 *     Now "fetched" is forgotten when a profile leaves memory, and a profile is only skipped if it is really in memory.
 *
 *  2) Right after the app starts, nothing was in memory. Now the most recent profiles are read from the phone's
 *     disk in the background at app start (UserProfiles.warmUp, called from DutiesApplication).
 *
 *  3) Tapping a photo forced a full re-download of the profile pictures (up to ~1 MB of base64) and then replaced
 *     the banner bitmap while the page was opening. Now only the tiny profilePics/{uid}/updatedAt value is read;
 *     the pictures are downloaded only if it changed.
 *
 *  4) The avatar and the (big) banner were one single request, so the small avatar waited for the big banner.
 *     Now avatar, banner and text are three separate requests that run at the same time and show on arrival.
 *
 *  5) The GIF banner (up to 16 MB, downloaded piece by piece) used to run at the same time as everything else and
 *     took the bandwidth of the text/pictures of the other people. Now it starts AFTER the text and pictures of that
 *     person are done, and at most 3 GIFs download at once.
 */

internal data class UserProfileData(
    val name: String,
    val username: String,
    val bio: String,
    val avatar: Bitmap?,
    val banner: ImageSlot,
    val gifStamp: Long = 0L,
    /** profilePics/{uid}/updatedAt of the pictures we hold; 0 = unknown (then they are downloaded again). */
    val picsStamp: Long = 0L
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
    private val gifBusy = HashSet<String>()
    private val fetched = HashSet<String>()
    private val order = ArrayList<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gifGate = Semaphore(3)

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
            val cur = map[uid]
            val next = change(cur ?: EMPTY)
            if (cur == null && next === EMPTY) return      // nothing to store (e.g. the profile was pushed out meanwhile)
            map[uid] = next
            order.remove(uid)
            order.add(uid)
            while (order.size > MAX_IN_MEMORY) {
                val victim = order.firstOrNull { it != pinned } ?: break
                order.remove(victim)
                map.remove(victim)
                fetched.remove(victim)                     // it must be loaded again next time it is needed
            }
        }
    }

    /**
     * Gets the profile of [uid]: the copy saved on the phone shows first, then the server's one.
     * [force] = ask the server again even if it was already asked in this session (used when a profile opens,
     * so a changed bio shows up). Without it, each person is asked once per app start (as long as he stays in memory).
     */
    fun request(context: Context, uid: String, force: Boolean = false) {
        if (uid.isBlank()) return
        val app = context.applicationContext
        synchronized(lock) {
            if (uid in inFlight) return
            if (!force && uid in fetched && map.containsKey(uid)) return
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

    /**
     * Reads the most recently saved profiles from the phone into memory (no network). Called once at app start,
     * so the first tap on a known person is instant even before the chat has loaded.
     */
    fun warmUp(context: Context, limit: Int = 8) {
        val app = context.applicationContext
        scope.launch {
            try {
                val files = dir(app).listFiles { f -> f.name.endsWith(".json") } ?: return@launch
                files.sortedByDescending { it.lastModified() }
                    .take(limit)
                    .forEach { f ->
                        val uid = f.name.removeSuffix(".json")
                        if (!map.containsKey(uid)) loadDisk(app, uid)
                    }
            } catch (_: Exception) {
                // only a speed-up
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
            // the pictures stamp is only trusted if the pictures are really on the disk too
            val picsStamp = if (av != null || bn != null) o.optLong("picsStamp", 0L) else 0L
            if (map.containsKey(uid)) return      // the server already answered
            update(uid) {
                UserProfileData(
                    o.optString("name"), o.optString("username"), o.optString("bio"), av, banner,
                    if (gifFile != null) stamp else 0L, picsStamp
                )
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
                .put("gifStamp", p.gifStamp).put("picsStamp", p.picsStamp)
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

    // ------------------------------------------------------------------ the server

    /** Text + pictures at the same time; the GIF banner starts afterwards on its own (it can be big). */
    private suspend fun fetch(app: Context, uid: String): Boolean = supervisorScope {
        val textJob = async { attempt { fetchText(app, uid) } }
        val picsJob = async { attempt { fetchPics(app, uid) } }
        val text = textJob.await()
        val pics = picsJob.await()
        scope.launch { fetchGif(app, uid) }
        text.isSuccess && pics.isSuccess
    }

    /** name, @username, bio (Firestore users/{uid}) */
    private suspend fun fetchText(app: Context, uid: String) {
        val doc = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
        if (!doc.exists()) return
        val raw = doc.getString("photo")
        val small = if (raw.isNullOrEmpty()) null else withContext(Dispatchers.Default) { decodeUserPhoto(raw) }
        val name = doc.getString("name")
        val username = doc.getString("username") ?: ""
        val bio = doc.getString("bio") ?: ""
        update(uid) {
            it.copy(
                name = name?.ifBlank { null } ?: it.name,
                username = username,
                bio = bio,
                avatar = it.avatar ?: small
            )
        }
        saveText(app, uid)
    }

    /**
     * The full photo and the still banner (Realtime Database profilePics/{uid}).
     * If we already hold pictures, only the tiny updatedAt value is read: same value = nothing to download.
     * Otherwise avatar and banner are two separate requests, so the small avatar never waits for the big banner.
     */
    private suspend fun fetchPics(app: Context, uid: String) {
        supervisorScope {
            val ref = FirebaseDatabase.getInstance().getReference("profilePics").child(uid)
            suspend fun stampOnServer(): Long = (ref.child("updatedAt").get().await().value as? Number)?.toLong() ?: 0L

            val have = map[uid]
            if (have != null && have.picsStamp != 0L && stampOnServer() == have.picsStamp) {
                return@supervisorScope      // unchanged: what is on the screen is already right
            }

            val stampJob = async { attempt { stampOnServer() } }
            val avJob = async {
                attempt {
                    val raw = ref.child("avatar").get().await().getValue(String::class.java)
                    val bmp = raw?.let { withContext(Dispatchers.Default) { decodeUserPhoto(it) } }
                    update(uid) { it.copy(avatar = bmp ?: it.avatar) }
                    saveRaw(app, uid, "avatar", raw)
                    saveText(app, uid)
                }
            }
            val bnJob = async {
                attempt {
                    val raw = ref.child("banner").get().await().getValue(String::class.java)
                    val bmp = raw?.let { withContext(Dispatchers.Default) { decodeUserPhoto(it) } }
                    update(uid) { cur ->
                        // the GIF of the saved copy stays until the server's GIF answer comes
                        val keepGif = bmp != null && cur.banner.gif != null
                        cur.copy(
                            banner = if (bmp == null) ImageSlot.Empty
                            else ImageSlot(bmp, null, if (keepGif) cur.banner.tf else null, if (keepGif) cur.banner.gif else null),
                            gifStamp = if (keepGif) cur.gifStamp else 0L
                        )
                    }
                    saveRaw(app, uid, "banner", raw)
                    saveText(app, uid)
                }
            }
            val st = stampJob.await()
            val av = avJob.await()
            val bn = bnJob.await()
            av.getOrThrow()
            bn.getOrThrow()
            // remember the stamp only when both pictures really arrived
            update(uid) { it.copy(picsStamp = st.getOrNull() ?: 0L) }
            saveText(app, uid)
            Unit
        }
    }

    /** The GIF banner: after the text and pictures, at most 3 at a time, one download per person. */
    private suspend fun fetchGif(app: Context, uid: String) {
        synchronized(lock) { if (!gifBusy.add(uid)) return }
        try {
            val r = gifGate.withPermit { attempt { loadBannerGif(app, uid) } }
            if (r.isSuccess) {
                val g = r.getOrNull()
                update(uid) { cur ->
                    val still = cur.banner.cropped
                    if (still == null) cur
                    else cur.copy(banner = ImageSlot(still, null, g?.tf, g?.file), gifStamp = g?.stamp ?: 0L)
                }
                saveText(app, uid)
            }
        } finally {
            synchronized(lock) { gifBusy.remove(uid) }
        }
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
