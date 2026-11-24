package com.duties.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.runtime.mutableStateMapOf
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/*
 * ChatCache.kt — two small caches that make the chat feel instant:
 *
 *  1) [ChatCache]  : the last messages of every channel are kept on the phone (memory + a file per channel).
 *                    When a chat opens it shows them at once, then the Realtime Database brings anything new.
 *  2) [UserPhotos] : the small profile photo of every person who wrote in a chat (users/{uid}.photo on Firestore),
 *                    kept on the phone too, so the photo shows next to the message without waiting.
 */

object ChatCache {
    private val memory = ConcurrentHashMap<String, List<ChatMessage>>()
    private val io = Executors.newSingleThreadExecutor()

    private fun file(context: Context, channelId: String): File {
        val dir = File(context.applicationContext.filesDir, "chat_cache").apply { mkdirs() }
        return File(dir, channelId.filter { it.isLetterOrDigit() || it == '_' || it == '-' } + ".json")
    }

    /** What is already in memory (instant), or null. */
    fun peek(channelId: String): List<ChatMessage>? = memory[channelId]

    /** Reads the file of a channel into memory (call off the main thread). */
    fun load(context: Context, channelId: String): List<ChatMessage> {
        memory[channelId]?.let { return it }
        val list = try {
            val f = file(context, channelId)
            if (!f.exists()) emptyList() else {
                val arr = JSONArray(f.readText())
                (0 until arr.length()).mapNotNull { i ->
                    val o = arr.optJSONObject(i) ?: return@mapNotNull null
                    ChatMessage(
                        id = o.optString("id"),
                        uid = o.optString("uid"),
                        name = o.optString("name"),
                        text = o.optString("text"),
                        createdAtMs = o.optLong("t")
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
        if (list.isNotEmpty()) memory.putIfAbsent(channelId, list)
        return memory[channelId] ?: list
    }

    /** Warms the memory for these channels (so their chats open with the messages already there). */
    fun preload(context: Context, channelIds: List<String>) {
        val app = context.applicationContext
        io.execute { channelIds.forEach { if (memory[it] == null) load(app, it) } }
    }

    /** Remembers the messages (memory now, file in the background). */
    fun save(context: Context, channelId: String, list: List<ChatMessage>) {
        memory[channelId] = list
        val app = context.applicationContext
        io.execute {
            try {
                val arr = JSONArray()
                for (m in list) {
                    arr.put(
                        JSONObject()
                            .put("id", m.id).put("uid", m.uid).put("name", m.name)
                            .put("text", m.text).put("t", m.createdAtMs)
                    )
                }
                val f = file(app, channelId)
                val tmp = File(f.parentFile, f.name + ".tmp")
                tmp.writeText(arr.toString())
                if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
            } catch (_: Exception) {
                // the cache is only a speed-up
            }
        }
    }

    /** Runs [block] on the cache's own background thread. */
    internal fun background(block: () -> Unit) = io.execute(block)
}

object UserPhotos {
    /** uid -> photo. Reading it inside a composable makes that composable update when the photo arrives. */
    val photos = mutableStateMapOf<String, Bitmap>()

    private val asked = HashSet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun file(context: Context, uid: String): File {
        val dir = File(context.filesDir, "user_photos").apply { mkdirs() }
        return File(dir, uid.filter { it.isLetterOrDigit() || it == '_' || it == '-' } + ".txt")
    }

    /** Asks for the photo of [uid]: the saved copy shows first, then the server's one (once per app start). */
    fun request(context: Context, uid: String) {
        if (uid.isBlank()) return
        val app = context.applicationContext
        synchronized(asked) { if (!asked.add(uid)) return }
        scope.launch {
            val f = file(app, uid)
            if (!photos.containsKey(uid) && f.exists()) {
                runCatching { decodeUserPhoto(f.readText()) }.getOrNull()?.let { photos[uid] = it }
            }
            try {
                val doc = FirebaseFirestore.getInstance().collection("users").document(uid).get().await()
                val raw = doc.getString("photo")
                if (raw.isNullOrEmpty()) {
                    f.delete()
                    photos.remove(uid)
                } else {
                    decodeUserPhoto(raw)?.let {
                        photos[uid] = it
                        runCatching { f.writeText(raw) }
                    }
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (_: Exception) {
                synchronized(asked) { asked.remove(uid) }       // offline: try again next time
            }
        }
    }
}

/** 128 px square JPEG as base64 text (about 6-12 KB): the profile photo that other people see in chats. */
fun encodeUserPhoto(src: Bitmap): String {
    val side = minOf(src.width, src.height)
    val square = if (src.width == src.height) src
    else Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    val scaled = if (square.width > 128) Bitmap.createScaledBitmap(square, 128, 128, true) else square
    val out = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}

fun decodeUserPhoto(text: String): Bitmap? = try {
    val bytes = Base64.decode(text, Base64.NO_WRAP)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
} catch (_: Exception) {
    null
}
