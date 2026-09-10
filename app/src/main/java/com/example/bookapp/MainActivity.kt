package com.example.bookapp

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.example.bookapp.data.Prefs
import com.example.bookapp.ui.AppNavigation
import com.example.bookapp.ui.theme.colorSchemeFor
import com.example.bookapp.ui.theme.typographyFor

class MainActivity : ComponentActivity() {

    // این دو مقدار به‌صورت Compose State نگه داشته می‌شوند (نه فقط local val داخل
    // onCreate) چون Activity با launchMode="singleTask" اجرا می‌شود: وقتی اپ از قبل
    // باز است و کاربر دوباره روی یک لینک اشتراک‌گذاری/میان‌بر می‌زند، اندروید به‌جای
    // ساختن یک نمونه‌ی تازه، همین Activity را نگه می‌دارد و فقط onNewIntent صدا زده
    // می‌شود؛ پس باید بتوانیم مقصد تازه را از همان‌جا هم به UI برسانیم.
    private val shortcutTargetState = mutableStateOf<String?>(null)
    private val deepLinkSectionIdState = mutableStateOf<Long?>(null)

    private fun applyIntent(intent: Intent?) {
        shortcutTargetState.value = intent?.getStringExtra("shortcut_target")
        deepLinkSectionIdState.value = intent?.data?.let { uri ->
            if (uri.scheme == "taziehapp" && uri.host == "section") uri.lastPathSegment?.toLongOrNull() else null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ثبت خودکار کرش‌ها: در صورت کرش برنامه، جزئیات خطا در یک فایل داخل
        // حافظه‌ی اپ ذخیره می‌شود تا بعداً از تنظیمات قابل مشاهده/ارسال باشد
        // (چون به سرویس آنالیتیکس بیرونی وصل نیستیم، این ساده‌ترین راه محلی است).
        val defaultCrashHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val logFile = java.io.File(filesDir, "last_crash.txt")
                logFile.writeText(
                    "زمان: ${java.util.Date()}\n\n" + android.util.Log.getStackTraceString(throwable)
                )
            } catch (e: Exception) {
                // اگر نوشتن لاگ هم شکست خورد، کاری نمی‌شود کرد
            }
            defaultCrashHandler?.uncaughtException(thread, throwable)
        }

        // اگر اپ از طریق میان‌بر فشار طولانی روی آیکون یا لینک taziehapp://section/{id}
        // باز شده، مقصد را از intent اولیه می‌خوانیم
        applyIntent(intent)

        setContent {
            val shortcutTarget by shortcutTargetState
            val deepLinkSectionId by deepLinkSectionIdState
            var autoDarkMode by remember { mutableStateOf(Prefs.getAutoDarkMode(this)) }
            var darkMode by remember {
                mutableStateOf(if (autoDarkMode) Prefs.isNightTimeNow() else Prefs.isDarkMode(this))
            }
            var fontScale by remember { mutableFloatStateOf(Prefs.getFontScale(this)) }
            var themeChoice by remember { mutableStateOf(Prefs.getThemeChoice(this)) }
            var fontChoice by remember { mutableStateOf(Prefs.getFontChoice(this)) }
            var keepScreenOn by remember { mutableStateOf(Prefs.getKeepScreenOn(this)) }

            // با تغییر تنظیم، بلافاصله روی پنجره اعمال می‌شود (هم می‌شود روشنش کرد هم خاموش)
            androidx.compose.runtime.LaunchedEffect(keepScreenOn) {
                if (keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            // در اندروید ۱۳ به بعد، نمایش اعلان نیاز به اجازه‌ی صریح کاربر دارد
            val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { /* نتیجه را نادیده می‌گیریم؛ اگر رد شود فقط اعلان نشان داده نمی‌شود */ }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            val colorScheme = colorSchemeFor(themeChoice, darkMode)
            val typography = typographyFor(fontChoice)
            val baseDensity = LocalDensity.current
            val scaledDensity = androidx.compose.ui.unit.Density(
                density = baseDensity.density,
                fontScale = baseDensity.fontScale * fontScale
            )

            MaterialTheme(colorScheme = colorScheme, typography = typography) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalDensity provides scaledDensity) {
                        AppNavigation(
                            darkMode = darkMode,
                            onDarkModeChange = {
                                darkMode = it
                                Prefs.setDarkMode(this, it)
                            },
                            autoDarkMode = autoDarkMode,
                            onAutoDarkModeChange = {
                                autoDarkMode = it
                                Prefs.setAutoDarkMode(this, it)
                                if (it) darkMode = Prefs.isNightTimeNow()
                            },
                            fontScale = fontScale,
                            onFontScaleChange = {
                                fontScale = it
                                Prefs.setFontScale(this, it)
                            },
                            themeChoice = themeChoice,
                            onThemeChoiceChange = {
                                themeChoice = it
                                Prefs.setThemeChoice(this, it)
                            },
                            fontChoice = fontChoice,
                            onFontChoiceChange = {
                                fontChoice = it
                                Prefs.setFontChoice(this, it)
                            },
                            keepScreenOn = keepScreenOn,
                            onKeepScreenOnChange = {
                                keepScreenOn = it
                                Prefs.setKeepScreenOn(this, it)
                            },
                            shortcutTarget = shortcutTarget,
                            deepLinkSectionId = deepLinkSectionId
                        )
                    }
                }
            }
        }
    }
}
