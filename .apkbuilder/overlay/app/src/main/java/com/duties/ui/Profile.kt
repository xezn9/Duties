package com.duties.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

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
 * [cropped] is what the app shows, [source] is the picked photo (kept so "move" can reopen it),
 * [tf] is the position/zoom the user chose.
 */
class ImageSlot(val cropped: Bitmap?, val source: Bitmap?, val tf: CropTransform?) {
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
        withContext(Dispatchers.IO) {
            prefs.edit()
                .putString("name", name)
                .putString("username", username)
                .putString("bio", bio)
                .apply()
            if (banner !== this@ProfileState.banner) writeSlot("banner", banner)
            if (avatar !== this@ProfileState.avatar) writeSlot("avatar", avatar)
        }
        this.name = name
        this.username = username
        this.bio = bio
        this.banner = banner
        this.avatar = avatar
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

    private fun readSlot(key: String): ImageSlot {
        val cropped = decodeFile(File(dir, "$key.jpg"))
        val source = decodeFile(File(dir, "${key}_src.jpg"))
        val tf = if (prefs.contains("${key}_zoom")) CropTransform(
            prefs.getFloat("${key}_zoom", 1f),
            prefs.getFloat("${key}_ox", 0f),
            prefs.getFloat("${key}_oy", 0f)
        ) else null
        return if (cropped == null) ImageSlot.Empty else ImageSlot(cropped, source, tf)
    }

    private fun writeSlot(key: String, slot: ImageSlot) {
        val cropFile = File(dir, "$key.jpg")
        val srcFile = File(dir, "${key}_src.jpg")
        val e = prefs.edit()
        if (slot.cropped == null) {
            cropFile.delete()
            srcFile.delete()
            e.remove("${key}_zoom").remove("${key}_ox").remove("${key}_oy")
        } else {
            cropFile.outputStream().use { slot.cropped.compress(Bitmap.CompressFormat.JPEG, 94, it) }
            if (slot.source != null) {
                srcFile.outputStream().use { slot.source.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            }
            slot.tf?.let { e.putFloat("${key}_zoom", it.zoom).putFloat("${key}_ox", it.ox).putFloat("${key}_oy", it.oy) }
        }
        e.apply()
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
