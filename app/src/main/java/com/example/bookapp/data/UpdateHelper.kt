package com.example.bookapp.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** بررسی دستی بروزرسانی و دریافت APK داخل خود برنامه؛ استفاده عادی برنامه آفلاین باقی می‌ماند. */
object UpdateHelper {
    private const val REPO = "lavialireza/taziehappv3"
    private const val RELEASES_API = "https://api.github.com/repos/$REPO/releases?per_page=20"
    private const val MANIFEST_ASSET = "update.json"

    data class UpdateInfo(
        val buildNumber: Int,
        val tagName: String,
        val downloadUrl: String,
        val isReleaseApk: Boolean,
        val versionName: String = tagName,
        val minSupportedVersion: Int = 0,
        val forceUpdate: Boolean = false,
        val releaseDate: String = "",
        val releaseNotes: List<String> = emptyList()
    )

    data class InstalledVersion(val buildNumber: Int, val versionName: String)

    /** نسخه واقعی نصب‌شده را از PackageManager می‌خواند؛ BuildConfig ممکن است
     * بعد از نصب یک APK جدید تا قبل از راه‌اندازی مجدد پردازش، مقدار قبلی باشد. */
    fun getInstalledVersion(context: Context): InstalledVersion {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt() else info.versionCode
        return InstalledVersion(code, info.versionName ?: "${code}")
    }

    suspend fun checkForUpdate(currentVersionCode: Int): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"; connectTimeout = 8000; readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Tazieh-Android-Updater")
            }
            try {
                if (connection.responseCode !in 200..299) throw IllegalStateException("خطای سرور: ${connection.responseCode}")
                val releases = JSONArray(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
                val viewer = com.example.bookapp.BuildConfig.PUBLIC_VIEWER
                val releaseAsset = if (viewer) "app-viewer-release.apk" else "app-admin-release.apk"
                val debugAsset = if (viewer) "app-viewer-debug.apk" else "app-admin-debug.apk"
                var best: UpdateInfo? = null
                for (i in 0 until releases.length()) {
                    val release = releases.getJSONObject(i)
                    if (release.optBoolean("draft", false)) continue
                    val tag = release.optString("tag_name")
                    val assets = release.optJSONArray("assets") ?: continue
                    var manifest: JSONObject? = null
                    var manifestUrl: String? = null
                    var apkUrl: String? = null
                    var isReleaseApk = false
                    for (j in 0 until assets.length()) {
                        val a = assets.getJSONObject(j); val name = a.optString("name")
                        val url = a.optString("browser_download_url").takeIf { it.isNotBlank() }
                        if (name == MANIFEST_ASSET) manifestUrl = url
                        if (name == releaseAsset) { apkUrl = url; isReleaseApk = url != null }
                        if (name == debugAsset && apkUrl == null) apkUrl = url
                    }
                    if (manifestUrl != null) {
                        runCatching {
                            val mc = (URL(manifestUrl).openConnection() as HttpURLConnection).apply {
                                connectTimeout = 6000; readTimeout = 8000; setRequestProperty("User-Agent", "Tazieh-Android-Updater")
                            }
                            try { if (mc.responseCode in 200..299) manifest = JSONObject(mc.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }) } finally { mc.disconnect() }
                        }
                    }
                    val buildNumber = manifest?.optInt("versionCode", 0)?.takeIf { it > 0 }
                        ?: Regex("^apk-build-(\\d+)$").find(tag)?.groupValues?.get(1)?.toIntOrNull()
                        ?: continue
                    if (buildNumber <= currentVersionCode || apkUrl == null) continue
                    // Manifest مشترک می‌تواند هر دو APK را معرفی کند؛ هر flavor فقط APK خودش را انتخاب می‌کند.
                    val selectedApkName = if (viewer) {
                        manifest?.optString("viewerApk").orEmpty()
                            .ifBlank { manifest?.optString("apkFile").orEmpty() }
                    } else {
                        manifest?.optString("adminApk").orEmpty()
                            .ifBlank { manifest?.optString("apkFile").orEmpty() }
                    }
                    if (selectedApkName.isNotBlank()) {
                        var exact: String? = null
                        for (j in 0 until assets.length()) {
                            val a = assets.getJSONObject(j)
                            if (a.optString("name") == selectedApkName) {
                                exact = a.optString("browser_download_url").takeIf { it.isNotBlank() }
                                if (exact != null) break
                            }
                        }
                        if (exact != null) apkUrl = exact
                    }
                    val notes = mutableListOf<String>()
                    manifest?.optJSONArray("releaseNotes")?.let { a -> for (n in 0 until a.length()) notes += a.optString(n) }
                    val info = UpdateInfo(buildNumber, tag, apkUrl!!, isReleaseApk, manifest?.optString("versionName").orEmpty().ifBlank { tag }, manifest?.optInt("minSupportedVersion", 0) ?: 0, manifest?.optBoolean("forceUpdate", false) ?: false, manifest?.optString("releaseDate").orEmpty(), notes)
                    if (best == null || info.buildNumber > best!!.buildNumber || (info.buildNumber == best!!.buildNumber && info.isReleaseApk && !best!!.isReleaseApk)) best = info
                }
                best
            } finally { connection.disconnect() }
        }
    }

    fun createUpdateManifest(context: Context, versionCode: Int, versionName: String, minSupportedVersion: Int, forceUpdate: Boolean, apkFile: String, releaseNotes: List<String>): File {
        val dir = File(context.filesDir, "updates").apply { mkdirs() }
        val file = File(dir, MANIFEST_ASSET)
        val json = JSONObject().apply {
            put("appName", if (com.example.bookapp.BuildConfig.PUBLIC_VIEWER) "Tazieh Viewer" else "Tazieh Admin")
            put("versionCode", versionCode); put("versionName", versionName); put("minSupportedVersion", minSupportedVersion)
            put("forceUpdate", forceUpdate); put("apkFile", apkFile); put("releaseDate", java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()))
            put("releaseNotes", JSONArray(releaseNotes))
        }
        file.writeText(json.toString(2), Charsets.UTF_8); return file
    }

    /**
     * APK را داخل cache خود برنامه دانلود می‌کند و پس از تکمیل، نصب سیستم را باز می‌کند.
     * این روش به مرورگر وابسته نیست و تا پایان دریافت صبر می‌کند.
     */
    suspend fun downloadAndInstall(
        context: Context,
        info: UpdateInfo,
        onProgress: (percent: Int) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // فایل کامل را در filesDir نگه می‌داریم تا اگر کاربر دوباره همان
            // بروزرسانی را درخواست کرد، APK دوباره از اینترنت دانلود نشود.
            val updateDir = File(context.filesDir, "updates").apply { mkdirs() }
            val apkFile = File(updateDir, "tazieh-update-${info.buildNumber}.apk")
            val partialFile = File(updateDir, "tazieh-update-${info.buildNumber}.apk.part")

            // فقط فایل نهایی را قابل نصب می‌دانیم؛ فایل .part ممکن است ناقص باشد.
            // اگر APK ذخیره‌شده دیگر از نسخه نصب‌شده جدیدتر نیست، آن را دوباره نصب نکن.
            if (apkFile.exists() && apkFile.length() > 0L) {
                val installed = getInstalledVersion(context)
                val archiveInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                val archiveCode = archiveInfo?.let {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode.toInt() else it.versionCode
                }
                val samePackage = archiveInfo?.packageName == context.packageName
                if (archiveCode != null && samePackage && archiveCode > installed.buildNumber) {
                    // ملاک نصب، نسخه واقعی داخل خود APK است؛ برچسب Release گیت‌هاب
                    // فقط برای پیدا کردن بروزرسانی استفاده می‌شود و ممکن است با
                    // versionCode داخلی APK قدیمی/متفاوت باشد.
                    onProgress(100)
                    withContext(Dispatchers.Main) { installApk(context, apkFile) }
                    return@runCatching
                }
                if (archiveCode != null && samePackage && archiveCode <= installed.buildNumber) {
                    apkFile.delete()
                    throw IllegalStateException(
                        "فایل APK موجود (نسخه $archiveCode) از نسخه نصب‌شده (${installed.buildNumber}) جدیدتر نیست؛ بروزرسانی متوقف شد."
                    )
                }
                apkFile.delete()
            }
            if (partialFile.exists()) partialFile.delete()

            val connection = (URL(info.downloadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("User-Agent", "Tazieh-Android-Updater")
            }
            try {
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("دریافت APK ناموفق بود: ${connection.responseCode}")
                }
                val total = connection.contentLengthLong
                var received = 0L
                var lastPercent = -1
                connection.inputStream.use { input ->
                    partialFile.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            received += count
                            if (total > 0) {
                                val percent = ((received * 100L) / total).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent)
                                }
                            }
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }

            if (!partialFile.exists() || partialFile.length() == 0L) {
                throw IllegalStateException("فایل APK کامل دریافت نشد")
            }
            if (apkFile.exists()) apkFile.delete()
            if (!partialFile.renameTo(apkFile)) {
                throw IllegalStateException("ذخیره فایل APK نهایی ناموفق بود")
            }

            // قبل از نصب، نسخه واقعی داخل APK را بررسی می‌کنیم.
            // مهم: شماره tag گیت‌هاب فقط برای پیدا کردن Release است و الزاماً
            // نباید با versionCode داخلی APK مقایسه شود. ملاک نصب فقط این است
            // که APK متعلق به همین package و جدیدتر از نسخه نصب‌شده باشد.
            val downloadedInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                ?: throw IllegalStateException("فایل دریافت‌شده یک APK معتبر نیست.")
            val downloadedCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                downloadedInfo.longVersionCode.toInt()
            } else {
                downloadedInfo.versionCode
            }
            if (downloadedInfo.packageName != context.packageName) {
                apkFile.delete()
                throw IllegalStateException("این APK مربوط به همین برنامه نیست؛ بروزرسانی متوقف شد.")
            }
            val installedAfterDownload = getInstalledVersion(context)
            if (downloadedCode <= installedAfterDownload.buildNumber) {
                apkFile.delete()
                throw IllegalStateException(
                    "نسخه APK دریافت‌شده (${downloadedCode}) از نسخه نصب‌شده (${installedAfterDownload.buildNumber}) جدیدتر نیست؛ بروزرسانی متوقف شد."
                )
            }

            onProgress(100)
            withContext(Dispatchers.Main) {
                installApk(context, apkFile)
            }
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            throw IllegalStateException("اجازه نصب برنامه از این منبع فعال نیست؛ پس از فعال‌سازی دوباره بروزرسانی را بزنید.")
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
