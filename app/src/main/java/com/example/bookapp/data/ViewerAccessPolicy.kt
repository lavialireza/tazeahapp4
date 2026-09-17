package com.example.bookapp.data

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/** مدیریت سیاست دسترسی Viewer؛ بدون وابستگی به شبکه. */
object ViewerAccessPolicy {
    const val PROFILE_PUBLIC = "public"
    const val PROFILE_TRAINING = "training"
    const val PROFILE_COLLABORATOR = "collaborator"
    const val PROFILE_CUSTOM = "custom"

    val permissionLabels = linkedMapOf(
        "read" to "مطالعه",
        "search" to "جستجو",
        "advancedSearch" to "جستجوی پیشرفته",
        "compare" to "مقایسه",
        "training" to "تمرین",
        "audio" to "صوت",
        "tts" to "تبدیل متن به گفتار",
        "notes" to "یادداشت",
        "bookmarks" to "علاقه‌مندی",
        "gallery" to "گالری تصاویر",
        "copy" to "کپی متن",
        "share" to "اشتراک‌گذاری",
        "pdf" to "PDF",
        "footnotes" to "پاورقی",
        "appIntro" to "معرفی برنامه"
    )

    data class SpecialUser(
        val installationId: String,
        val profile: String,
        val expiresAt: Long?,
        val permissions: Map<String, Boolean>
    )

    private const val PREFS = "viewer_access_policy"
    private const val KEY_PUBLIC = "public_permissions"
    private const val KEY_SPECIAL = "special_users"
    private const val KEY_POLICY_VERSION = "policy_version"
    private const val KEY_IMPORTED_PUBLIC_VERSION = "imported_public_version"
    private const val KEY_IMPORTED_PUBLIC = "imported_public_permissions"
    private const val SPECIAL_USERS_FILE = "viewer_access_special_users.json"

    fun defaultPermissions(): Map<String, Boolean> = permissionLabels.keys.associateWith { key ->
        key !in setOf("copy", "share", "pdf", "appIntro")
    }

    fun profileDefaults(profile: String): Map<String, Boolean> = when (profile) {
        PROFILE_TRAINING -> defaultPermissions() + mapOf("training" to true)
        PROFILE_COLLABORATOR -> defaultPermissions() + mapOf("copy" to true, "share" to false, "pdf" to false)
        else -> defaultPermissions()
    }


    /** دسترسی مؤثر همین Viewer: کاربر خاص در صورت وجود بر پروفایل عمومی اولویت دارد. */
    fun getEffectivePermissions(context: Context): Map<String, Boolean> {
        val id = installationId(context)
        val special = getSpecialUsers(context).firstOrNull { it.installationId == id }
        if (special != null) {
            val expiry = special.expiresAt
            if (expiry == null || expiry <= 0L || System.currentTimeMillis() <= expiry) return special.permissions
        }
        return getPublicPermissions(context)
    }

    fun hasPermission(context: Context, key: String): Boolean = getEffectivePermissions(context)[key] == true

    internal fun setImportedPublicPermissions(context: Context, permissions: Map<String, Boolean>, version: Int) {
        val obj = JSONObject(); permissionLabels.keys.forEach { obj.put(it, permissions[it] == true) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_IMPORTED_PUBLIC, obj.toString())
            .putInt(KEY_IMPORTED_PUBLIC_VERSION, version)
            .putString(KEY_PUBLIC, obj.toString())
            .apply()
    }

    internal fun setImportedSpecialPermissions(context: Context, permissions: Map<String, Boolean>, expiresAt: Long?, version: Int) {
        val id = installationId(context)
        upsertSpecialUser(context, SpecialUser(id, PROFILE_CUSTOM, expiresAt, permissions))
    }

    fun getPublicPermissions(context: Context): Map<String, Boolean> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PUBLIC, null)
        if (raw.isNullOrBlank()) return defaultPermissions()
        return runCatching {
            val obj = JSONObject(raw)
            permissionLabels.keys.associateWith { key -> obj.optBoolean(key, defaultPermissions()[key] == true) }
        }.getOrElse { defaultPermissions() }
    }

    fun setPublicPermissions(context: Context, permissions: Map<String, Boolean>) {
        val obj = JSONObject()
        permissionLabels.keys.forEach { obj.put(it, permissions[it] == true) }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = prefs.getInt(KEY_POLICY_VERSION, 1) + 1
        prefs.edit().putString(KEY_PUBLIC, obj.toString()).putInt(KEY_POLICY_VERSION, next).apply()
    }

    fun getPolicyVersion(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_POLICY_VERSION, 1)

    fun getSpecialUsers(context: Context): List<SpecialUser> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val prefRaw = prefs.getString(KEY_SPECIAL, null)
        val file = File(context.filesDir, SPECIAL_USERS_FILE)
        // فایل داخلی به عنوان پشتیبان پایدار نگه داشته می‌شود. اگر SharedPreferences
        // خالی/خراب باشد، رکوردهای ذخیره‌شده از فایل بازیابی می‌شوند.
        val raw = when {
            !prefRaw.isNullOrBlank() -> prefRaw
            file.exists() -> runCatching { file.readText(Charsets.UTF_8) }.getOrNull()
            else -> null
        } ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val id = o.optString("installationId").trim().uppercase(java.util.Locale.US)
                if (id.isBlank()) return@mapNotNull null
                val p = o.optJSONObject("permissions")
                val perms = permissionLabels.keys.associateWith { p?.optBoolean(it, false) ?: false }
                SpecialUser(id, o.optString("profile", PROFILE_CUSTOM),
                    if (o.isNull("expiresAt")) null else o.optLong("expiresAt"), perms)
            }
        }.getOrElse { emptyList() }
    }

    fun saveSpecialUsers(context: Context, users: List<SpecialUser>) {
        val arr = JSONArray()
        users.forEach { user ->
            val o = JSONObject().put("installationId", user.installationId).put("profile", user.profile)
            if (user.expiresAt == null) o.put("expiresAt", JSONObject.NULL) else o.put("expiresAt", user.expiresAt)
            val p = JSONObject(); permissionLabels.keys.forEach { p.put(it, user.permissions[it] == true) }
            o.put("permissions", p); arr.put(o)
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val next = prefs.getInt(KEY_POLICY_VERSION, 1) + 1
        // commit() is intentional here: the Admin screen immediately reloads the list
        // and the policy may be exported to Viewer in the same UI action.
        val json = arr.toString()
        val saved = prefs.edit().putString(KEY_SPECIAL, json).putInt(KEY_POLICY_VERSION, next).commit()
        check(saved) { "ذخیره کاربران خاص در حافظه برنامه انجام نشد." }
        // یک نسخه مستقل در حافظه داخلی برنامه هم نگه می‌داریم تا رکورد کاربر خاص
        // با بازشدن دوباره صفحه/فرآیند برنامه قابل بازیابی باشد.
        val file = File(context.filesDir, SPECIAL_USERS_FILE)
        val tmp = File(context.filesDir, "$SPECIAL_USERS_FILE.tmp")
        runCatching {
            tmp.writeText(json, Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.writeText(json, Charsets.UTF_8)
                tmp.delete()
            }
        }.getOrElse { throw IllegalStateException("فایل پایدار کاربران خاص ذخیره نشد: ${it.message ?: "خطای نامشخص"}", it) }
        check(file.exists() && file.readText(Charsets.UTF_8) == json) { "تأیید ذخیره کاربران خاص انجام نشد." }
    }

    fun upsertSpecialUser(context: Context, user: SpecialUser) {
        val normalizedId = user.installationId.trim().uppercase(java.util.Locale.US)
        require(normalizedId.isNotBlank()) { "شناسه نصب خالی است." }
        val normalized = user.copy(installationId = normalizedId)
        val users = getSpecialUsers(context).toMutableList()
        val index = users.indexOfFirst { it.installationId.trim().equals(normalizedId, ignoreCase = true) }
        if (index >= 0) users[index] = normalized else users.add(normalized)
        saveSpecialUsers(context, users)
        check(getSpecialUsers(context).any { it.installationId == normalizedId }) { "کاربر خاص پس از ذخیره قابل بازیابی نیست." }
    }

    fun removeSpecialUser(context: Context, installationId: String) {
        val normalizedId = installationId.trim().uppercase(java.util.Locale.US)
        saveSpecialUsers(context, getSpecialUsers(context).filterNot { it.installationId.trim().uppercase(java.util.Locale.US) == normalizedId })
        check(getSpecialUsers(context).none { it.installationId.trim().uppercase(java.util.Locale.US) == normalizedId }) {
            "حذف کاربر خاص انجام نشد."
        }
    }

    fun installationId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString("installation_id", null)?.let { return it }
        val bytes = ByteArray(6); SecureRandom().nextBytes(bytes)
        val id = "VWR-" + bytes.joinToString("") { "%02X".format(it) }.chunked(4).joinToString("-")
        prefs.edit().putString("installation_id", id).commit().also { ok -> check(ok) { "شناسه نصب ذخیره نشد." } }
        return id
    }

    fun toJson(context: Context): String {
        val root = JSONObject().put("schema", 1).put("policyVersion", getPolicyVersion(context))
        val pub = JSONObject(); getPublicPermissions(context).forEach { (k,v) -> pub.put(k,v) }; root.put("public", pub)
        val arr = JSONArray(); getSpecialUsers(context).forEach { u ->
            val o = JSONObject().put("installationId", u.installationId).put("profile", u.profile)
            if (u.expiresAt == null) o.put("expiresAt", JSONObject.NULL) else o.put("expiresAt", u.expiresAt)
            val p = JSONObject(); u.permissions.forEach { (k,v) -> p.put(k,v) }; o.put("permissions",p); arr.put(o)
        }; root.put("specialUsers", arr)
        return root.toString(2)
    }
}
