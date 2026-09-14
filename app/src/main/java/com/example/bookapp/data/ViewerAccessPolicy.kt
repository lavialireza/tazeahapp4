package com.example.bookapp.data

import android.content.Context
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
        "pdf" to "PDF"
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

    fun defaultPermissions(): Map<String, Boolean> = permissionLabels.keys.associateWith { key ->
        key !in setOf("copy", "share", "pdf")
    }

    fun profileDefaults(profile: String): Map<String, Boolean> = when (profile) {
        PROFILE_TRAINING -> defaultPermissions() + mapOf("training" to true)
        PROFILE_COLLABORATOR -> defaultPermissions() + mapOf("copy" to true, "share" to false, "pdf" to false)
        else -> defaultPermissions()
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
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_SPECIAL, "[]") ?: "[]"
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val p = o.optJSONObject("permissions")
                val perms = permissionLabels.keys.associateWith { p?.optBoolean(it, false) ?: false }
                SpecialUser(o.optString("installationId"), o.optString("profile", PROFILE_CUSTOM),
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
        prefs.edit().putString(KEY_SPECIAL, arr.toString()).putInt(KEY_POLICY_VERSION, next).apply()
    }

    fun upsertSpecialUser(context: Context, user: SpecialUser) {
        val users = getSpecialUsers(context).toMutableList()
        val index = users.indexOfFirst { it.installationId == user.installationId }
        if (index >= 0) users[index] = user else users.add(user)
        saveSpecialUsers(context, users)
    }

    fun removeSpecialUser(context: Context, installationId: String) {
        saveSpecialUsers(context, getSpecialUsers(context).filterNot { it.installationId == installationId })
    }

    fun installationId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString("installation_id", null)?.let { return it }
        val bytes = ByteArray(6); SecureRandom().nextBytes(bytes)
        val id = "VWR-" + bytes.joinToString("") { "%02X".format(it) }.chunked(4).joinToString("-")
        prefs.edit().putString("installation_id", id).apply()
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
