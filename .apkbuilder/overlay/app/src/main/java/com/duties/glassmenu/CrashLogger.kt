package com.duties.glassmenu

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * When the app crashes this writes a .txt report to the phone's Download folder:
 * where it crashed (file + line + function), the cause, and the full stack trace.
 *
 * Install once:  CrashLogger.install(application)   (see GlassApp.kt)
 */
object CrashLogger {

    private const val PREFS = "crash_logger"
    private const val KEY_PENDING = "pending_file"

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val name = "crash_$stamp.txt"
                val saved = saveToDownloads(app, name, buildReport(app, thread, throwable))
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_PENDING, saved).commit()
            } catch (_: Throwable) {
                // never let the logger itself hide the real crash
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** call from the Activity: after a crash the next launch tells you where the report is */
    fun showPendingNotice(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs.getString(KEY_PENDING, null) ?: return
        prefs.edit().remove(KEY_PENDING).apply()
        Toast.makeText(context, "صار كراش وانحفظ التقرير:\n$saved", Toast.LENGTH_LONG).show()
    }

    // ───────────────────────── report ─────────────────────────
    private fun buildReport(app: Application, thread: Thread, error: Throwable): String {
        val chain = generateSequence(error) { it.cause }.take(10).toList()
        val root = chain.last()
        val pkg = app.packageName

        fun appFrame(t: Throwable): StackTraceElement? =
            t.stackTrace.firstOrNull { it.className.startsWith(pkg) }

        val appFrameFound = appFrame(root) ?: chain.firstNotNullOfOrNull { appFrame(it) }
        val topFrame = root.stackTrace.firstOrNull()

        val versionName = try {
            app.packageManager.getPackageInfo(pkg, 0).versionName
        } catch (_: Throwable) {
            "?"
        }

        return buildString {
            appendLine("===== تقرير كراش =====")
            appendLine("الوقت      : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}")
            appendLine("التطبيق    : $pkg ($versionName)")
            appendLine("الجهاز     : ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("الأندرويد  : ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("الـ Thread : ${thread.name}")
            appendLine()
            appendLine("--- مكان الكراش (أول سطر من كود تطبيقك) ---")
            if (appFrameFound != null) {
                appendLine("الملف  : ${appFrameFound.fileName}")
                appendLine("السطر  : ${appFrameFound.lineNumber}")
                appendLine("الدالة : ${appFrameFound.className}.${appFrameFound.methodName}")
            } else {
                appendLine("(ما لقيت سطر من كود تطبيقك بالـ stack، الكراش داخل مكتبة/النظام)")
            }
            appendLine()
            appendLine("--- مكان الانفجار الفعلي (أول سطر بالـ stack) ---")
            if (topFrame != null) {
                appendLine("${topFrame.className}.${topFrame.methodName} (${topFrame.fileName}:${topFrame.lineNumber})")
            }
            appendLine()
            appendLine("--- السبب ---")
            appendLine("${root.javaClass.name}: ${root.message}")
            appendLine()
            appendLine("--- سلسلة الأسباب ---")
            chain.forEachIndexed { i, t -> appendLine("${i + 1}. ${t.javaClass.name}: ${t.message}") }
            appendLine()
            appendLine("--- Stack trace كامل ---")
            appendLine(Log.getStackTraceString(error))
        }
    }

    // ───────────────────────── saving ─────────────────────────
    private fun saveToDownloads(ctx: Context, name: String, text: String): String {
        val bytes = text.toByteArray(Charsets.UTF_8)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+: MediaStore, no permission needed
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("MediaStore insert failed")
            ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IOException("openOutputStream failed")
            return "Download/$name"
        }

        // Android 9 and older: public Download folder (needs WRITE_EXTERNAL_STORAGE), else app folder
        return try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            val f = File(dir, name)
            f.writeBytes(bytes)
            f.absolutePath
        } catch (_: Throwable) {
            val dir = ctx.getExternalFilesDir(null) ?: ctx.filesDir
            val f = File(dir, name)
            f.writeBytes(bytes)
            f.absolutePath
        }
    }
}
