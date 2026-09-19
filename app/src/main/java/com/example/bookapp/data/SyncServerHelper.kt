package com.example.bookapp.data

import android.content.Context
import com.example.bookapp.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * اتصال Android به Sync Server مشترک Web.
 * این مرحله محتوای اصلی را همگام می‌کند: زمینه، تعزیه، نقش و بخش.
 * برنامه همچنان آفلاین-first است و فقط با فشردن دکمه همگام‌سازی به شبکه وصل می‌شود.
 */
object SyncServerHelper {
    private const val PREFS = "tazieh_sync"
    private const val KEY_URL = "server_url"
    private const val DEFAULT_TIMEOUT = 15000

    fun getServerUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_URL, BuildConfig.SYNC_SERVER_URL)?.trim()?.removeSuffix("/").orEmpty()

    fun setServerUrl(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_URL, value.trim().removeSuffix("/")).apply()
    }

    suspend fun check(context: Context): Result<String> = withContext(Dispatchers.IO) {
        request(context, "GET", "/api/health", null).map { it }
    }

    suspend fun syncContent(context: Context, db: AppDatabase): Result<SyncReport> = withContext(Dispatchers.IO) {
        val base = getServerUrl(context)
        if (base.isBlank()) return@withContext Result.failure(IllegalStateException("ابتدا آدرس Sync Server را وارد کنید."))
        if (BuildConfig.PUBLIC_VIEWER) {
            return@withContext Result.failure(IllegalStateException("نسخه عمومی فقط اجازه دریافت محتوای تأییدشده را دارد."))
        }
        try {
            val remoteText = request(context, "GET", "/api/state", null).getOrThrow()
            val remoteRoot = JSONObject(remoteText)
            val remoteData = remoteRoot.optJSONObject("data") ?: JSONObject()
            val remoteContent = remoteContentToJson(remoteData)

            var pulled = 0
            if (remoteContent.length() > 0) {
                val before = db.sectionDao().getAll().size
                mergeContentFromJson(db, remoteContent.toString(), ContentUid.source("sync-server"))
                val after = db.sectionDao().getAll().size
                pulled = (after - before).coerceAtLeast(0)
            }

            val localContent = contentState(db)
            val mergedData = JSONObject(remoteData.toString())
            mergedData.put("fields", localContent.getJSONArray("fields"))
            mergedData.put("taziehs", localContent.getJSONArray("taziehs"))
            mergedData.put("roles", localContent.getJSONArray("roles"))
            mergedData.put("sections", localContent.getJSONArray("sections"))
            mergedData.put("syncSource", "android-${if (BuildConfig.PUBLIC_VIEWER) "viewer" else "admin"}")
            mergedData.put("syncUpdatedAt", System.currentTimeMillis())

            val body = JSONObject().apply {
                put("deviceId", deviceId(context))
                put("data", mergedData)
            }
            request(context, "PUT", "/api/state", body.toString()).getOrThrow()
            Result.success(SyncReport(pulled, localContent.getJSONArray("sections").length(), System.currentTimeMillis()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString("device_id", null) ?: ("android-" + System.currentTimeMillis()).also {
            prefs.edit().putString("device_id", it).apply()
        }
    }

    private fun request(context: Context, method: String, path: String, body: String?): Result<String> {
        val base = getServerUrl(context)
        if (base.isBlank()) return Result.failure(IllegalStateException("آدرس Sync Server تنظیم نشده است."))
        return runCatching {
            val c = (URL(base + path).openConnection() as HttpURLConnection).apply {
                connectTimeout = DEFAULT_TIMEOUT
                readTimeout = DEFAULT_TIMEOUT
                requestMethod = method
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
            try {
                if (body != null) c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else c.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) error("Sync Server خطای HTTP $code${if (text.isBlank()) "" else ": $text"}")
                text
            } finally { c.disconnect() }
        }
    }

    private suspend fun contentState(db: AppDatabase): JSONObject {
        val fields = db.fieldDao().getAll()
        val taziehs = db.taziehDao().getAll()
        val roles = db.roleDao().getAllForSync()
        val sections = db.sectionDao().getAll()
        return JSONObject().apply {
            put("fields", JSONArray().also { a -> fields.forEach { f -> a.put(JSONObject().apply { put("id", f.id); put("uid", f.uid); put("title", f.title) }) } })
            put("taziehs", JSONArray().also { a -> taziehs.forEach { t -> a.put(JSONObject().apply { put("id", t.id); put("uid", t.uid); put("fieldId", t.fieldId); put("title", t.title); put("author", t.author ?: ""); put("authorEmail", t.authorEmail ?: "") }) } })
            put("roles", JSONArray().also { a -> roles.forEach { r -> a.put(JSONObject().apply { put("id", r.id); put("uid", r.uid); put("taziehId", r.taziehId); put("title", r.title); put("orderIndex", r.orderIndex) }) } })
            put("sections", JSONArray().also { a -> sections.forEach { s -> a.put(JSONObject().apply { put("id", s.id); put("uid", s.uid); put("roleId", s.roleId); put("title", s.title); put("content", s.content); put("audioUrl", s.audioUrl ?: ""); put("orderIndex", s.orderIndex) }) } })
        }
    }

    private fun remoteContentToJson(data: JSONObject): JSONArray {
        val fields = data.optJSONArray("fields") ?: return JSONArray()
        val taziehs = data.optJSONArray("taziehs") ?: JSONArray()
        val roles = data.optJSONArray("roles") ?: JSONArray()
        val sections = data.optJSONArray("sections") ?: JSONArray()
        val out = JSONArray()
        for (fi in 0 until fields.length()) {
            val f = fields.optJSONObject(fi) ?: continue
            val fUid = f.optString("uid")
            val fo = JSONObject().apply { put("title", f.optString("title")); if (fUid.isNotBlank()) put("uid", fUid) }
            val ta = JSONArray()
            for (ti in 0 until taziehs.length()) {
                val t = taziehs.optJSONObject(ti) ?: continue
                if (t.optString("fieldId") != f.optString("id")) continue
                val to = JSONObject().apply { put("title", t.optString("title")); if (t.optString("uid").isNotBlank()) put("uid", t.optString("uid")) }
                val ra = JSONArray()
                for (ri in 0 until roles.length()) {
                    val r = roles.optJSONObject(ri) ?: continue
                    if (r.optString("taziehId") != t.optString("id")) continue
                    val ro = JSONObject().apply { put("title", r.optString("title")); if (r.optString("uid").isNotBlank()) put("uid", r.optString("uid")) }
                    val sa = JSONArray()
                    for (si in 0 until sections.length()) {
                        val s = sections.optJSONObject(si) ?: continue
                        if (s.optString("roleId") != r.optString("id")) continue
                        val so = JSONObject().apply { put("title", s.optString("title")); put("content", s.optString("content")); if (s.optString("uid").isNotBlank()) put("uid", s.optString("uid")) }
                        sa.put(so)
                    }
                    ro.put("sections", sa); ra.put(ro)
                }
                to.put("roles", ra); ta.put(to)
            }
            fo.put("taziehs", ta); out.put(fo)
        }
        return out
    }

    data class SyncReport(val pulledNewSections: Int, val uploadedSections: Int, val timestamp: Long)
}
