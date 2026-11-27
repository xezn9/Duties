package com.duties

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Crash report: when the app crashes, a .txt file is written to the Download folder with the reason of the
 * crash and the exact place in the code (file + line). Then the crash goes on as usual (the app closes).
 */
class DutiesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Realtime Database keeps what it already read on the phone (chats open from it before the server answers)
        runCatching { com.google.firebase.database.FirebaseDatabase.getInstance().setPersistenceEnabled(true) }
        // the most recent profiles (bio, @username, banner) are read from the phone's disk now, in the background,
        // so the first tap on a known person opens his profile with everything already there
        runCatching { com.duties.ui.UserProfiles.warmUp(this) }
        // the glass settings (transparency / blur / refraction) chosen in Settings > App theme
        runCatching { com.duties.ui.GlassTheme.load(this) }
        // the app color chosen in Settings > App theme
        runCatching { com.duties.ui.AppAccent.load(this) }
        // animation speed: phone on 1.0x -> the app's animations run at 0.5x; phone on 0.5x -> unchanged
        runCatching { AnimationSpeed.install(this) }
        // the notification channel of the chat pushes (FCM)
        runCatching { Push.createChannel(this) }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                saveCrashReport(this, thread, error)
            } catch (_: Throwable) {
                // writing the report must never hide the real crash
            }
            previous?.uncaughtException(thread, error)
        }
    }
}

private fun saveCrashReport(context: Context, thread: Thread, error: Throwable) {
    val now = Date()
    val name = "Duties_crash_" + SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(now) + ".txt"
    val text = buildReport(context, now, thread, error)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Android 10+: Download folder through MediaStore (no permission needed)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    } else {
        // Android 9 and older: straight into the Download folder (needs the storage permission),
        // otherwise into the app's own folder
        val dir = try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).also { it.mkdirs() }
        } catch (_: Throwable) {
            null
        }
        val target = try {
            File(dir, name).also { it.writeText(text) }
        } catch (_: Throwable) {
            File(context.getExternalFilesDir(null) ?: context.filesDir, name).also { it.writeText(text) }
        }
        target.exists()
    }
}

private fun buildReport(context: Context, time: Date, thread: Thread, error: Throwable): String {
    // the causes, from the outside to the deepest one
    val chain = generateSequence(error) { it.cause }.take(10).toList()
    val root = chain.last()

    // first line of OUR code (com.duties...) in the deepest cause, then in the others
    fun ourFrame(t: Throwable) = t.stackTrace.firstOrNull { it.className.startsWith("com.duties") }
    val where = ourFrame(root) ?: chain.asReversed().firstNotNullOfOrNull { ourFrame(it) }
    val top = root.stackTrace.firstOrNull()

    val version = try {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName ?: "?"
    } catch (_: Throwable) {
        "?"
    }

    val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()

    return buildString {
        appendLine("=== Duties crash report / تقرير الكراش ===")
        appendLine("Time / الوقت: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(time))
        appendLine("App version: $version")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} | Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Thread / الخيط: ${thread.name}")
        appendLine()
        appendLine("--- Reason / سبب الكراش ---")
        appendLine(root.javaClass.name + (root.message?.let { ": $it" } ?: ""))
        appendLine()
        appendLine("--- Where / مكان الكراش ---")
        if (where != null) {
            appendLine("In the app code / بكود التطبيق:")
            appendLine("  ${where.className}.${where.methodName}")
            appendLine("  File: ${where.fileName ?: "?"}   Line: ${where.lineNumber}")
        } else {
            appendLine("No line of the app code in the trace (the crash is inside a library).")
        }
        if (top != null) {
            appendLine("Exact line where it was thrown / السطر اللي انرمى منه:")
            appendLine("  ${top.className}.${top.methodName}  (${top.fileName ?: "?"}:${top.lineNumber})")
        }
        if (chain.size > 1) {
            appendLine()
            appendLine("--- Chain of causes / سلسلة الأسباب ---")
            chain.forEachIndexed { i, t -> appendLine("${i + 1}. ${t.javaClass.name}: ${t.message}") }
        }
        appendLine()
        appendLine("--- Full stack trace / التفاصيل الكاملة ---")
        append(trace)
    }
}
