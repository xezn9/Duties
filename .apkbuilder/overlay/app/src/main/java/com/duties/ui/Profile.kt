package com.duties.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.math.roundToInt

// ---------------------------------------------------------------------
// Rules (change them here only)
// ---------------------------------------------------------------------
const val NAME_MAX = 24
const val USERNAME_MIN = 3
const val USERNAME_MAX = 24
const val BIO_MAX = 150

/** Banner width / height. The profile page, the edit card and the "move banner" frame all use it. */
const val BANNER_ASPECT = 2.0f

fun isValidName(name: String) = name.trim().length in 1..NAME_MAX

fun isValidUsername(u: String) =
    u.length in USERNAME_MIN..USERNAME_MAX && u.all { it.isUsernameChar() }

/** English letters, digits, underscore and dot (like a normal @username). */
fun Char.isUsernameChar() = (this in 'a'..'z') || (this in 'A'..'Z') || (this in '0'..'9') || this == '_' || this == '.'

/** Somebody else already owns this @username. */
class UsernameTakenException : Exception("username taken")

/** The banner GIF could not be uploaded to the Realtime Database (offline, or the database is not set up). */
class BannerUploadException(cause: Throwable) : Exception("banner upload failed", cause)

fun Throwable.isBannerUpload(): Boolean {
    var t: Throwable? = this
    while (t != null) {
        if (t is BannerUploadException) return true
        t = t.cause
    }
    return false
}

/** True if this error (or the one inside it) is [UsernameTakenException]. */
fun Throwable.isUsernameTaken(): Boolean {
    var t: Throwable? = this
    while (t != null) {
        if (t is UsernameTakenException) return true
        t = t.cause
    }
    return false
}

// ---------------------------------------------------------------------
// Image slot = what the user picked + how it was cropped
// ---------------------------------------------------------------------

/** Where the image sits inside the frame: zoom (1 = just covers) and offset as a fraction of the frame size. */
class CropTransform(val zoom: Float, val ox: Float, val oy: Float)

/**
 * [gif] = the animated GIF file when the banner is a GIF ([cropped] / [source] are then its first frame,
 * used while the GIF loads, on old phones and in the blurred backgrounds).
 * [cropped] is what the app shows, [source] is the picked photo (kept so "move" can reopen it),
 * [tf] is the position/zoom the user chose.
 */
class ImageSlot(val cropped: Bitmap?, val source: Bitmap?, val tf: CropTransform?, val gif: File? = null) {
    companion object {
        val Empty = ImageSlot(null, null, null)
    }
}

// ---------------------------------------------------------------------
// Saved profile (kept on the phone, one per signed-in account)
// ---------------------------------------------------------------------
class ProfileState(
    private val context: Context,
    private val uid: String,
    defaultName: String,
    defaultUsername: String
) {
    private val prefs = context.getSharedPreferences("profile_$uid", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "profile/$uid").apply { mkdirs() }

    var name by mutableStateOf(prefs.getString("name", null) ?: defaultName)
        private set
    var username by mutableStateOf(prefs.getString("username", null) ?: defaultUsername)
        private set
    var bio by mutableStateOf(prefs.getString("bio", "") ?: "")
        private set
    var banner by mutableStateOf(ImageSlot.Empty)
        private set
    var avatar by mutableStateOf(ImageSlot.Empty)
        private set

    /** Reads the saved pictures from the disk (called once, off the main thread). */
    suspend fun loadImages() = withContext(Dispatchers.IO) {
        val b = readSlot("banner")
        val a = readSlot("avatar")
        withContext(Dispatchers.Main) {
            banner = b
            avatar = a
        }
    }

    /**
     * Saves to Firestore first (name, username, bio + the unique @username), then on the phone.
     * Pictures stay on the phone only. Throws [UsernameTakenException] if the @username is taken.
     */
    suspend fun save(
        name: String,
        username: String,
        bio: String,
        banner: ImageSlot,
        avatar: ImageSlot
    ) {
        pushToServer(name, username, bio)
        if (banner !== this.banner) syncBannerGif(banner)
        var savedBanner = banner
        var savedAvatar = avatar
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString("name", name)
                .putString("username", username)
                .putString("bio", bio)
                .apply()
            if (banner !== this@ProfileState.banner) savedBanner = writeSlot("banner", banner)
            if (avatar !== this@ProfileState.avatar) savedAvatar = writeSlot("avatar", avatar)
        }
        this.name = name
        this.username = username
        this.bio = bio
        this.banner = savedBanner
        this.avatar = savedAvatar
    }

    /** users/{uid} + usernames/{lowercase username}, in one transaction. */
    private suspend fun pushToServer(name: String, username: String, bio: String) {
        val db = FirebaseFirestore.getInstance()
        val me = uid
        val lower = username.lowercase()
        val userRef = db.collection("users").document(me)
        val newRef = db.collection("usernames").document(lower)
        db.runTransaction { tr ->
            // all reads first, then the writes
            val newSnap = tr.get(newRef)
            if (newSnap.exists() && newSnap.getString("uid") != me) throw UsernameTakenException()
            val oldName = tr.get(userRef).getString("username")?.lowercase()
            val oldRef = if (oldName != null && oldName != lower) db.collection("usernames").document(oldName) else null
            val oldSnap = oldRef?.let { tr.get(it) }

            if (!newSnap.exists()) tr.set(newRef, mapOf("uid" to me))
            if (oldRef != null && oldSnap != null && oldSnap.exists() && oldSnap.getString("uid") == me) tr.delete(oldRef)
            tr.set(
                userRef,
                mapOf(
                    "name" to name,
                    "username" to username,
                    "bio" to bio,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            null
        }.await()
    }

    /** Takes the profile saved on Firestore (so it shows on a new phone too). Silent if offline. */
    suspend fun syncFromServer() {
        try {
            val doc = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
            if (!doc.exists()) return
            val n = doc.getString("name") ?: return
            val u = doc.getString("username") ?: return
            val b = doc.getString("bio") ?: ""
            withContext(Dispatchers.IO) {
                prefs.edit().putString("name", n).putString("username", u).putString("bio", b).apply()
            }
            name = n
            username = u
            bio = b
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            // offline or no permission: keep what is on the phone
        }
    }

    // -----------------------------------------------------------------
    // Banner GIF in the Realtime Database
    //   bannerGifs/{uid}/meta = { size, chunks, zoom, ox, oy, updatedAt }
    //   bannerGifs/{uid}/data/{0..chunks-1} = base64 text of each piece of the file
    // A GIF is up to 16 MB, but one database value can be 10 MB at most, so the file is cut in pieces.
    // The meta is written last, so a half uploaded GIF is never seen as complete.
    // -----------------------------------------------------------------
    private fun bannerGifRef() = FirebaseDatabase.getInstance().getReference("bannerGifs").child(uid)

    /** Puts the banner GIF in the database (or removes it when the banner is not a GIF any more). */
    private suspend fun syncBannerGif(slot: ImageSlot) {
        val g = slot.gif
        if (g == null) {
            // not a GIF now: remove the old one from the database (not important if this fails)
            try {
                val ref = bannerGifRef()
                ref.child("meta").removeValue().await()
                ref.child("data").removeValue().await()
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                // the database is not set up or offline: nothing to remove
            }
            return
        }
        try {
            val isSavedGif = g.parentFile?.absolutePath == dir.absolutePath
            val ref = bannerGifRef()
            // the same GIF that is already there: only the position may have changed
            val alreadyThere = isSavedGif && ref.child("meta").get().await().exists()
            if (alreadyThere) {
                ref.child("meta").updateChildren(
                    mapOf(
                        "zoom" to (slot.tf?.zoom ?: 1f).toDouble(),
                        "ox" to (slot.tf?.ox ?: 0f).toDouble(),
                        "oy" to (slot.tf?.oy ?: 0f).toDouble()
                    )
                ).await()
            } else {
                uploadBannerGif(ref, g, slot.tf)
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            throw BannerUploadException(e)
        }
    }

    private suspend fun uploadBannerGif(ref: com.google.firebase.database.DatabaseReference, file: File, tf: CropTransform?) {
        ref.child("meta").removeValue().await()
        ref.child("data").removeValue().await()
        var count = 0
        withContext(Dispatchers.IO) {
            file.inputStream().buffered().use { input ->
                val buf = ByteArray(GIF_CHUNK_BYTES)
                while (true) {
                    val n = readFully(input, buf)
                    if (n <= 0) break
                    val text = Base64.encodeToString(buf, 0, n, Base64.NO_WRAP)
                    ref.child("data").child(count.toString()).setValue(text).await()
                    count++
                }
            }
        }
        ref.child("meta").setValue(
            mapOf(
                "size" to file.length(),
                "chunks" to count,
                "zoom" to (tf?.zoom ?: 1f).toDouble(),
                "ox" to (tf?.ox ?: 0f).toDouble(),
                "oy" to (tf?.oy ?: 0f).toDouble(),
                "updatedAt" to ServerValue.TIMESTAMP
            )
        ).await()
    }

    /**
     * On a new phone (no banner yet) takes the banner GIF back from the database.
     * Silent if there is none, if it is offline or if the database is not set up.
     */
    suspend fun restoreBannerGif() {
        if (banner.cropped != null) return
        var temp: File? = null
        try {
            val ref = bannerGifRef()
            val meta = ref.child("meta").get().await()
            if (!meta.exists()) return
            val chunks = (meta.child("chunks").value as? Number)?.toInt() ?: return
            val tf = CropTransform(
                (meta.child("zoom").value as? Number)?.toFloat() ?: 1f,
                (meta.child("ox").value as? Number)?.toFloat() ?: 0f,
                (meta.child("oy").value as? Number)?.toFloat() ?: 0f
            )
            val out = File(context.cacheDir, "restore_banner_" + System.currentTimeMillis() + ".gif")
            temp = out
            withContext(Dispatchers.IO) {
                out.outputStream().buffered().use { output ->
                    for (i in 0 until chunks) {
                        val text = ref.child("data").child(i.toString()).get().await().getValue(String::class.java)
                            ?: throw IllegalStateException("missing piece $i")
                        output.write(Base64.decode(text, Base64.NO_WRAP))
                    }
                }
            }
            val first = decodeGifFirstFrame(out) ?: throw IllegalStateException("bad gif")
            val cropped = withContext(Dispatchers.Default) { cropToBannerFrame(first, tf) }
            if (banner.cropped != null) { out.delete(); return }       // the user picked a banner meanwhile
            val saved = withContext(Dispatchers.IO) { writeSlot("banner", ImageSlot(cropped, first, tf, out)) }
            banner = saved
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            temp?.delete()
            throw e
        } catch (e: Exception) {
            temp?.delete()
        }
    }

    private fun readSlot(key: String): ImageSlot {
        val cropped = decodeFile(File(dir, "$key.jpg"))
        val source = decodeFile(File(dir, "${key}_src.jpg"))
        val tf = if (prefs.contains("${key}_zoom")) CropTransform(
            prefs.getFloat("${key}_zoom", 1f),
            prefs.getFloat("${key}_ox", 0f),
            prefs.getFloat("${key}_oy", 0f)
        ) else null
        val gif = prefs.getString("${key}_gif", null)?.let { File(dir, it) }?.takeIf { it.exists() }
        return if (cropped == null) ImageSlot.Empty else ImageSlot(cropped, source, tf, gif)
    }

    /** Writes the slot to the phone and returns the slot as it is now stored (its GIF lives in the profile folder). */
    private fun writeSlot(key: String, slot: ImageSlot): ImageSlot {
        val cropFile = File(dir, "$key.jpg")
        val srcFile = File(dir, "${key}_src.jpg")
        val e = prefs.edit()
        val oldGif = prefs.getString("${key}_gif", null)?.let { File(dir, it) }
        var result = slot
        if (slot.cropped == null) {
            cropFile.delete()
            srcFile.delete()
            oldGif?.delete()
            e.remove("${key}_zoom").remove("${key}_ox").remove("${key}_oy").remove("${key}_gif")
        } else {
            cropFile.outputStream().use { slot.cropped.compress(Bitmap.CompressFormat.JPEG, 94, it) }
            if (slot.source != null) {
                srcFile.outputStream().use { slot.source.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            }
            slot.tf?.let { e.putFloat("${key}_zoom", it.zoom).putFloat("${key}_ox", it.ox).putFloat("${key}_oy", it.oy) }

            val g = slot.gif
            if (g == null) {
                // a normal picture now: the old GIF (if any) is not needed
                oldGif?.delete()
                e.remove("${key}_gif")
            } else if (g.parentFile?.absolutePath == dir.absolutePath) {
                e.putString("${key}_gif", g.name)               // same GIF as before (only moved / re-cropped)
            } else {
                // a new GIF: copy it from the cache under a new name (the old one may still be playing)
                val dest = File(dir, "${key}_${System.currentTimeMillis()}.gif")
                g.copyTo(dest, overwrite = true)
                g.delete()
                if (oldGif != null && oldGif.absolutePath != dest.absolutePath) oldGif.delete()
                e.putString("${key}_gif", dest.name)
                result = ImageSlot(slot.cropped, slot.source, slot.tf, dest)
            }
        }
        e.apply()
        return result
    }

    private fun decodeFile(f: File): Bitmap? =
        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
}

@Composable
fun rememberProfileState(uid: String, displayName: String?, email: String?): ProfileState {
    val context = LocalContext.current.applicationContext
    val state = remember(uid) {
        val defaultUser = email?.substringBefore('@').orEmpty()
            .filter { it.isUsernameChar() }
            .take(USERNAME_MAX)
            .let { if (it.length >= USERNAME_MIN) it else "user" + uid.filter { c -> c.isDigit() }.take(4).padEnd(4, '0') }
        ProfileState(context, uid, displayName.orEmpty().take(NAME_MAX), defaultUser)
    }
    LaunchedEffect(state) {
        state.loadImages()
        state.syncFromServer()
        state.restoreBannerGif()
    }
    return state
}

// ---------------------------------------------------------------------
// Picking a photo: decode (down-sampled, EXIF rotation fixed)
// ---------------------------------------------------------------------
suspend fun decodePickedImage(context: Context, uri: Uri, maxSide: Int = 2048): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return@withContext null

            val orientation = resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL

            val m = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            }
            if (m.isIdentity) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
        } catch (e: Exception) {
            null
        }
    }

// ---------------------------------------------------------------------
// GIF banner: max size, check, copy to the cache, first frame
// ---------------------------------------------------------------------

/** Biggest GIF accepted for the banner (16 MB). It is stored in the Realtime Database, see [ProfileState]. */
const val GIF_MAX_BYTES = 16L * 1024 * 1024

/** The GIF goes to the database in pieces of 384 KB (base64 = 512 KB each; one value may not pass 10 MB). */
private const val GIF_CHUNK_BYTES = 384 * 1024

sealed class GifPick {
    /** It is a normal picture, not a GIF. */
    object NotGif : GifPick()

    /** A GIF, but bigger than [GIF_MAX_BYTES] (16 MB). */
    object TooBig : GifPick()

    object Failed : GifPick()

    /** A GIF within the limit, copied to [file] (in the cache until the profile is saved). */
    class Ok(val file: File) : GifPick()
}

/** Looks at the first bytes of the picked file ("GIF87a" / "GIF89a"); if it is a GIF it is copied to the cache. */
suspend fun pickGif(context: Context, uri: Uri): GifPick = withContext(Dispatchers.IO) {
    var temp: File? = null
    try {
        val resolver = context.contentResolver
        val head = ByteArray(6)
        var got = 0
        resolver.openInputStream(uri)?.use { input ->
            while (got < head.size) {
                val n = input.read(head, got, head.size - got)
                if (n < 0) break
                got += n
            }
        } ?: return@withContext GifPick.Failed
        val magic = String(head, 0, got, Charsets.ISO_8859_1)
        if (magic != "GIF87a" && magic != "GIF89a") return@withContext GifPick.NotGif

        val out = File(context.cacheDir, "pick_banner_" + System.currentTimeMillis() + ".gif")
        temp = out
        var total = 0L
        resolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > GIF_MAX_BYTES) return@withContext GifPick.TooBig.also { out.delete() }
                    output.write(buf, 0, n)
                }
            }
        } ?: return@withContext GifPick.Failed
        GifPick.Ok(out)
    } catch (e: Exception) {
        temp?.delete()
        GifPick.Failed
    }
}

/** First frame of a GIF as a normal bitmap (down-sampled), used for the crop screen and as a still picture. */
suspend fun decodeGifFirstFrame(file: File, maxSide: Int = 2048): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (e: Exception) {
        null
    }
}

/** Reads until [buf] is full or the stream ends. Returns how many bytes were read. */
private fun readFully(input: InputStream, buf: ByteArray): Int {
    var off = 0
    while (off < buf.size) {
        val n = input.read(buf, off, buf.size - off)
        if (n < 0) break
        off += n
    }
    return off
}

/**
 * The part of [source] that the banner frame shows for the chosen position / zoom [tf], cut out as a
 * 2:1 picture (same maths as the "move the banner" page).
 */
fun cropToBannerFrame(source: Bitmap, tf: CropTransform?, outW: Int = 1080): Bitmap {
    val fw = 1000f
    val fh = fw / BANNER_ASPECT
    val iw = source.width.toFloat()
    val ih = source.height.toFloat()
    val base = maxOf(fw / iw, fh / ih)
    val zoom = (tf?.zoom ?: 1f).coerceIn(1f, 8f)
    val sc = base * zoom
    val dw = iw * sc
    val dh = ih * sc
    val limX = maxOf(0f, (dw - fw) / 2f)
    val limY = maxOf(0f, (dh - fh) / 2f)
    val ox = ((tf?.ox ?: 0f) * fw).coerceIn(-limX, limX)
    val oy = ((tf?.oy ?: 0f) * fh).coerceIn(-limY, limY)
    val srcL = (dw / 2f - fw / 2f - ox) / sc
    val srcT = (dh / 2f - fh / 2f - oy) / sc
    val srcW = fw / sc
    val srcH = fh / sc
    val outH = (outW / BANNER_ASPECT).roundToInt()
    val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val l = srcL.roundToInt().coerceIn(0, source.width - 1)
    val t = srcT.roundToInt().coerceIn(0, source.height - 1)
    val r = (srcL + srcW).roundToInt().coerceIn(l + 1, source.width)
    val b = (srcT + srcH).roundToInt().coerceIn(t + 1, source.height)
    android.graphics.Canvas(out).drawBitmap(
        source,
        android.graphics.Rect(l, t, r, b),
        android.graphics.Rect(0, 0, outW, outH),
        android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
    )
    return out
}
