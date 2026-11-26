package com.duties.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.duties.R
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A picture for the full screen viewer. [gif] (with [tf], the chosen position) is set when it is an animated
 * GIF banner: the viewer plays it, and "Save" saves the GIF itself. [bitmap] is the still picture.
 */
class ViewedImage(val bitmap: Bitmap, val gif: File? = null, val tf: CropTransform? = null)

/**
 * Full screen picture viewer (banner / profile photo): pinch to zoom, double tap to zoom in/out,
 * and a button that saves the picture to the phone's gallery. [image] = the picture (null = hidden).
 * Put it last inside the screen's root Box so it covers the bottom tab bar too.
 */
@Composable
fun ImageViewerHost(image: ViewedImage?, onDismiss: () -> Unit) {
    // remember the last picture so the closing animation can finish before it disappears
    var last by remember { mutableStateOf(image) }
    LaunchedEffect(image) { if (image != null) last = image }
    AnimatedVisibility(
        visible = image != null,
        enter = fadeIn(tween(200)) + scaleIn(spring(0.85f, 400f), initialScale = 0.9f),
        exit = fadeOut(tween(170)) + scaleOut(tween(190), targetScale = 0.94f)
    ) {
        last?.let { ImageViewerScreen(it, onDismiss) }
    }
}

@Composable
private fun ImageViewerScreen(viewed: ViewedImage, onDismiss: () -> Unit) {
    val bitmap = viewed.bitmap
    val slot = remember(viewed) { ImageSlot(viewed.bitmap, null, viewed.tf, viewed.gif) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val backdrop = rememberLayerBackdrop()
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var saving by remember { mutableStateOf(false) }

    val savedMsg = stringResource(R.string.image_saved)
    val failedMsg = stringResource(R.string.image_save_failed)
    val permMsg = stringResource(R.string.image_need_permission)

    fun doSave() {
        if (saving) return
        saving = true
        scope.launch {
            val name = "Duties_" + System.currentTimeMillis()
            val ok = if (viewed.gif != null) saveGifToGallery(ctx, viewed.gif, name)
            else saveBitmapToGallery(ctx, bitmap, name)
            saving = false
            Toast.makeText(ctx, if (ok) savedMsg else failedMsg, Toast.LENGTH_SHORT).show()
        }
    }

    // Android 9 and older need the write permission to save into the gallery
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) doSave() else Toast.makeText(ctx, permMsg, Toast.LENGTH_LONG).show()
    }
    fun save() {
        if (Build.VERSION.SDK_INT < 29 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) permLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else doSave()
    }

    BackHandler(onBack = onDismiss)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        // the empty tap handler stops touches from reaching the page below
        Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { } }) {
            Box(
                Modifier.fillMaxSize().layerBackdrop(backdrop).onSizeChanged { box = it },
                contentAlignment = Alignment.Center
            ) {
                BannerPicture(
                    slot = slot,
                    fit = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = {
                                if (scale > 1.05f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                            })
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                offset = if (scale <= 1.01f) {
                                    Offset.Zero
                                } else {
                                    // do not drag the picture out of the screen
                                    val maxX = box.width * (scale - 1f) / 2f
                                    val maxY = box.height * (scale - 1f) / 2f
                                    val o = offset + pan * scale
                                    Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
                                }
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                )
            }

            val glass = Color(0xFF121212).copy(alpha = 0.45f)
            Row(
                Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WhiteLiquidButton(
                    backdrop = backdrop,
                    modifier = Modifier.size(48.dp),
                    horizontalPadding = 0.dp,
                    onClick = onDismiss
                ) { GlyphIcon(R.drawable.ic_close, WhiteButtonInk, 24.dp) }
            }
            Row(
                Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp).align(Alignment.TopEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassSurface(
                    backdrop = backdrop,
                    container = AppAccent.pick(Color(0xFF0A84FF)).copy(alpha = 0.55f),
                    modifier = Modifier.height(48.dp),
                    onClick = { if (!saving) save() }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(18.dp))
                        GlyphIcon(R.drawable.ic_download, Color.White, 22.dp)
                        Spacer(Modifier.width(8.dp))
                        BasicText(
                            if (saving) "..." else stringResource(R.string.save),
                            style = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        )
                        Spacer(Modifier.width(20.dp))
                    }
                }
            }
        }
    }
}

/** Saves [bmp] into the gallery (Pictures/Duties). Returns false if anything went wrong. */
private suspend fun saveBitmapToGallery(ctx: Context, bmp: Bitmap, name: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = ctx.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$name.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Duties")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri == null) {
                    false
                } else {
                    val written = resolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 98, it) } ?: false
                    if (written) {
                        val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                        resolver.update(uri, done, null, null)
                    } else {
                        resolver.delete(uri, null, null)
                    }
                    written
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Duties")
                dir.mkdirs()
                val file = File(dir, "$name.jpg")
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 98, it) }
                MediaScannerConnection.scanFile(ctx, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

/** Saves the GIF file itself (still animated) into the gallery (Pictures/Duties). */
private suspend fun saveGifToGallery(ctx: Context, file: File, name: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val resolver = ctx.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "$name.gif")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/gif")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Duties")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri == null) {
                    false
                } else {
                    val written = try {
                        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } != null
                    } catch (e: Exception) {
                        false
                    }
                    if (written) {
                        val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
                        resolver.update(uri, done, null, null)
                    } else {
                        resolver.delete(uri, null, null)
                    }
                    written
                }
            } else {
                @Suppress("DEPRECATION")
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Duties")
                dir.mkdirs()
                val dest = File(dir, "$name.gif")
                file.copyTo(dest, overwrite = true)
                MediaScannerConnection.scanFile(ctx, arrayOf(dest.absolutePath), arrayOf("image/gif"), null)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
