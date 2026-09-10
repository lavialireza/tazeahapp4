package com.example.bookapp.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * تصویر انتخاب‌شده از گالری گوشی را داخل حافظه‌ی اختصاصی خود اپ کپی می‌کند
 * (پوشه‌ی files/tazieh_images) تا حتی اگر کاربر بعداً عکس اصلی را از گالری
 * پاک کند یا گوشی ری‌استارت شود، تصویر داخل اپ باقی بماند.
 * مسیر فایل کپی‌شده را برمی‌گرداند (برای ذخیره در دیتابیس).
 *
 * چون کپی فایل ممکن است حجیم/کند باشد، این تابع suspend است و کار واقعی روی
 * Dispatchers.IO انجام می‌شود تا Thread اصلی (UI) قفل نشود.
 */
suspend fun copyImageToAppStorage(context: Context, sourceUri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        val dir = File(context.filesDir, "tazieh_images").apply { mkdirs() }
        val destFile = File(dir, "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        destFile.absolutePath
    } catch (e: Exception) {
        null
    }
}

suspend fun deleteImageFromAppStorage(filePath: String) = withContext(Dispatchers.IO) {
    try {
        File(filePath).delete()
    } catch (e: Exception) {
        // نادیده گرفتن؛ حذف رکورد دیتابیس مهم‌تر از حذف فایل باقی‌مانده است
    }
    Unit
}

/**
 * فایل صوتی انتخاب‌شده از گوشی را برای یک بخش خاص در حافظه داخلی برنامه کپی
 * می‌کند (مشابه copyImageToAppStorage) تا صدای واقعی/ضبط‌شده به‌جای صدای
 * مصنوعی (TTS) پخش شود. روی Dispatchers.IO اجرا می‌شود.
 */
suspend fun copyAudioToAppStorage(context: Context, sourceUri: Uri): String? = withContext(Dispatchers.IO) {
    try {
        val dir = File(context.filesDir, "tazieh_audio").apply { mkdirs() }
        val destFile = File(dir, "${UUID.randomUUID()}.mp3")
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output -> input.copyTo(output) }
        }
        destFile.absolutePath
    } catch (e: Exception) {
        null
    }
}

suspend fun deleteAudioFromAppStorage(filePath: String) = withContext(Dispatchers.IO) {
    try {
        File(filePath).delete()
    } catch (e: Exception) {
        // نادیده گرفتن
    }
    Unit
}
