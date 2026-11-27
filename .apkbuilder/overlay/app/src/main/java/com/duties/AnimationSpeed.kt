package com.duties

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.WindowRecomposerPolicy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import kotlin.math.abs

/**
 * سرعة الانيميشنات.
 *
 * يفحص "طول مدة الرسوم المتحركة" (Animator duration scale) مالت الجهاز:
 *  - إذا 1.0x  -> التطبيق يشغل كل انيميشنات Compose بسرعة 0.5x (أسرع وأسلس)
 *  - إذا 0.5x (أو أي قيمة ثانية غير 1.0x) -> يخليها مثل ما هيه
 *  - إذا الانيميشن مطفّي (0) -> يبقى مطفّي
 *
 * Compose يقرأ هذا الـ MotionDurationScale لكل Animatable / animate*AsState / AnimatedVisibility ... الخ،
 * فتغييره هنا يمشي على كل انيميشنات التطبيق مرة وحدة بدون ما نلمس أي انيميشن بروحه.
 */
object AnimationSpeed : MotionDurationScale {
    /** the speed the app wants when the phone is on 1.0x */
    private const val TARGET = 0.5f

    @Volatile
    private var systemScale = 1f

    // Compose reads this every time an animation starts
    override val scaleFactor: Float
        get() {
            val s = systemScale
            return if (abs(s - 1f) < 0.01f) TARGET else s
        }

    private fun read(app: Application) {
        systemScale = try {
            Settings.Global.getFloat(app.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (_: Throwable) {
            1f
        }
    }

    @OptIn(InternalComposeUiApi::class)
    fun install(app: Application) {
        read(app)

        // if the user changes the setting while the app is open, follow it
        runCatching {
            app.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) = read(app)
                }
            )
        }

        // every Compose window uses our scale instead of the raw system one
        WindowRecomposerPolicy.setFactory { rootView ->
            rootView.createLifecycleAwareWindowRecomposer(coroutineContext = AnimationSpeed)
        }
    }
}
