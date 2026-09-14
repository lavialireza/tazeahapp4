package com.example.bookapp.data

import android.content.Context
import androidx.core.content.FileProvider
import java.io.InputStream
import java.io.OutputStream
import org.json.JSONObject
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** انتقال امنِ سیاست دسترسی بین Admin و Viewer؛ بدون سرور. */
object ViewerAccessTransfer {
    private const val SCHEMA = 1
    private const val TARGET_PUBLIC = "*"
    // این راز فقط برای اعتبارسنجی فایل سیاست است؛ امنیت مطلق/DRM نیست.
    private const val SHARED_SECRET = "TaziehAccessPolicy-2026-v1"

    private fun sign(payload: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(SHARED_SECRET.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun buildPolicyJson(context: Context, targetInstallationId: String): String {
        val target = targetInstallationId.ifBlank { TARGET_PUBLIC }
        val permissions = if (target == TARGET_PUBLIC) {
            ViewerAccessPolicy.getPublicPermissions(context)
        } else {
            ViewerAccessPolicy.getSpecialUsers(context).firstOrNull { it.installationId == target }?.permissions
                ?: throw IllegalArgumentException("کاربر موردنظر پیدا نشد.")
        }
        val root = JSONObject()
            .put("schema", SCHEMA)
            .put("targetInstallationId", target)
            .put("policyVersion", ViewerAccessPolicy.getPolicyVersion(context))
            .put("issuedAt", System.currentTimeMillis())
            .put("expiresAt", if (target == TARGET_PUBLIC) JSONObject.NULL else ViewerAccessPolicy.getSpecialUsers(context).first { it.installationId == target }.expiresAt ?: JSONObject.NULL)
        val p = JSONObject(); ViewerAccessPolicy.permissionLabels.keys.forEach { p.put(it, permissions[it] == true) }
        root.put("permissions", p)
        val unsigned = root.toString()
        return JSONObject().put("schema", SCHEMA).put("payload", root).put("signature", sign(unsigned)).toString(2)
    }

    fun writePolicy(context: Context, targetInstallationId: String, output: OutputStream) {
        output.use { it.write(buildPolicyJson(context, targetInstallationId).toByteArray(Charsets.UTF_8)) }
    }

    /** فایل سیاست را در cache آماده می‌کند تا Admin بتواند آن را مستقیماً با Viewer به اشتراک بگذارد. */
    fun createShareUri(context: Context, targetInstallationId: String): android.net.Uri {
        val file = java.io.File(context.cacheDir, "viewer-access-share.json")
        file.outputStream().use { writePolicy(context, targetInstallationId, it) }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun importPolicy(context: Context, input: InputStream): Result<String> = runCatching {
        val text = input.use { it.readBytes().toString(Charsets.UTF_8) }
        val envelope = JSONObject(text)
        require(envelope.optInt("schema", -1) == SCHEMA) { "نسخه فایل سیاست پشتیبانی نمی‌شود." }
        val payload = envelope.getJSONObject("payload")
        val signature = envelope.optString("signature")
        require(signature == sign(payload.toString())) { "امضای سیاست معتبر نیست." }
        val target = payload.optString("targetInstallationId")
        val ownId = ViewerAccessPolicy.installationId(context)
        require(target == TARGET_PUBLIC || target == ownId) { "این سیاست برای این دستگاه صادر نشده است." }
        val expiresAt = if (payload.isNull("expiresAt")) null else payload.optLong("expiresAt")
        require(expiresAt == null || expiresAt <= 0L || System.currentTimeMillis() <= expiresAt) { "تاریخ اعتبار این سیاست گذشته است." }
        val p = payload.getJSONObject("permissions")
        val permissions = ViewerAccessPolicy.permissionLabels.keys.associateWith { p.optBoolean(it, false) }
        if (target == TARGET_PUBLIC) ViewerAccessPolicy.setImportedPublicPermissions(context, permissions, payload.optInt("policyVersion", 1))
        else ViewerAccessPolicy.setImportedSpecialPermissions(context, permissions, expiresAt, payload.optInt("policyVersion", 1))
        "سیاست دسترسی با موفقیت اعمال شد."
    }
}
