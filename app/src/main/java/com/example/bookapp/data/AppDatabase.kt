package com.example.bookapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

@Database(
    entities = [
        FieldEntity::class,
        TaziehEntity::class,
        RoleEntity::class,
        SectionEntity::class,
        NoteEntity::class,
        FootnoteEntity::class,
        DialogueEntity::class,
        DialogueTurnEntity::class,
        TaziehImageEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fieldDao(): FieldDao
    abstract fun taziehDao(): TaziehDao
    abstract fun roleDao(): RoleDao
    abstract fun sectionDao(): SectionDao
    abstract fun searchDao(): SearchDao
    abstract fun noteDao(): NoteDao
    abstract fun footnoteDao(): FootnoteDao
    abstract fun dialogueDao(): DialogueDao
    abstract fun dialogueTurnDao(): DialogueTurnDao
    abstract fun taziehImageDao(): TaziehImageDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /**
         * از نسخه ۵ به ۶: فقط دو جدول تازه (گفتگو و نوبت‌های گفتگو) اضافه می‌شود؛
         * هیچ جدول موجودی تغییر نمی‌کند. چون این تغییر را خودمان نوشتیم و ساختار
         * دقیقش را مطمئنیم، برخلاف گذارهای قبلی، اینجا از Migration واقعی استفاده
         * می‌کنیم تا داده‌ی کاربر (بوکمارک، یادداشت، پاورقی، نقش من) پاک نشود.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `dialogues` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `taziehId` INTEGER NOT NULL,
                        `title` TEXT NOT NULL,
                        FOREIGN KEY(`taziehId`) REFERENCES `taziehs`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dialogues_taziehId` ON `dialogues` (`taziehId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `dialogue_turns` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `dialogueId` INTEGER NOT NULL,
                        `sectionId` INTEGER NOT NULL,
                        `orderIndex` INTEGER NOT NULL,
                        FOREIGN KEY(`dialogueId`) REFERENCES `dialogues`(`id`) ON DELETE CASCADE,
                        FOREIGN KEY(`sectionId`) REFERENCES `sections`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dialogue_turns_dialogueId` ON `dialogue_turns` (`dialogueId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_dialogue_turns_sectionId` ON `dialogue_turns` (`sectionId`)")
            }
        }

        /** از نسخه ۶ به ۷: فقط جدول تصاویر تعزیه اضافه می‌شود. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `tazieh_images` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `taziehId` INTEGER NOT NULL,
                        `filePath` TEXT NOT NULL,
                        `caption` TEXT NOT NULL DEFAULT '',
                        FOREIGN KEY(`taziehId`) REFERENCES `taziehs`(`id`) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tazieh_images_taziehId` ON `tazieh_images` (`taziehId`)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "bookapp.db"
                )
                    .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
                    // برای گذارهای قبل از نسخه ۵ که مطمئن نیستیم schema دقیقشان چه بوده،
                    // همچنان بازسازی خودکار انجام می‌شود.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/**
 * فایل‌های JSON پوشه‌ی assets/content را که هنوز پردازش نشده‌اند (بر اساس نام فایل)
 * پیدا کرده و محتوای هرکدام را به دیتابیس اضافه می‌کند، بدون پاک‌کردن محتوای قبلی.
 * به این ترتیب برای افزودن یک مجلس تعزیه‌ی جدید، کافی است یک فایل JSON تازه در
 * assets/content قرار بگیرد؛ بقیه‌ی محتوا دست‌نخورده باقی می‌ماند و فقط فایل جدید
 * به ترتیب (بر اساس نام فایل) به انتهای نقش‌های موجود اضافه یا نقش/تعزیه/زمینه‌ی
 * تازه ساخته می‌شود.
 */
/**
 * فایل‌های JSON پوشه‌ی assets/content را که هنوز پردازش نشده‌اند (بر اساس نام فایل)
 * به دیتابیس اضافه می‌کند و شمار فایل‌های تازه‌ی اضافه‌شده را برمی‌گرداند (۰ یعنی
 * چیز تازه‌ای نبود). اگر این اولین اجرای برنامه نباشد (یعنی قبلاً محتوایی داشته)
 * و چیزی واقعاً تازه اضافه شده باشد، یک اعلان محلی هم نشان داده می‌شود.
 */
suspend fun syncLocalContentFiles(context: Context, db: AppDatabase): Int {
    val allFiles = context.assets.list("content")?.filter { it.endsWith(".json") }?.sorted() ?: emptyList()
    var processed = Prefs.getProcessedContentFiles(context)
    val hadContentBefore = db.fieldDao().getAll().isNotEmpty()

    // اگر دیتابیس به هر دلیلی (مثلاً migration مخرب بین نسخه‌ها) خالی شده باشد
    // ولی Prefs هنوز فایل‌ها را «پردازش‌شده» می‌داند، باید همه چیز دوباره بارگذاری شود.
    if (!hadContentBefore && processed.isNotEmpty()) {
        processed = emptySet()
    }

    val newFiles = allFiles.filter { it !in processed }
    if (newFiles.isEmpty()) return 0

    for (fileName in newFiles) {
        val jsonText = context.assets.open("content/$fileName")
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        mergeContentFromJson(db, jsonText)
    }
    Prefs.addProcessedContentFiles(context, allFiles) // کل فهرست فعلی را به‌عنوان پردازش‌شده ثبت می‌کنیم (نه فقط newFiles، چون ممکن است processed از صفر بازنشانی شده باشد)

    // فقط وقتی اعلان نشان بده که این اولین نصب نبوده (کاربر قبلاً محتوا داشته)
    // تا کاربرِ تازه‌نصب‌کرده با یک اعلان اضافه و بی‌مورد مواجه نشود
    if (hadContentBefore) {
        showNewContentNotification(context, newFiles.size)
    }

    return newFiles.size
}

/**
 * یک فایل JSON که از اینترنت دریافت شده را می‌خواند و - دقیقاً مثل
 * mergeContentFromJson برای فایل‌های محلی assets/content - به‌صورت افزودنی/تدریجی
 * با محتوای فعلی دیتابیس ادغام می‌کند: هیچ‌چیز پاک نمی‌شود؛ زمینه/تعزیه/نقشی که با
 * همان عنوان از قبل وجود دارد دوباره ساخته نمی‌شود و فقط بخش‌های تازه به انتهای
 * همان نقش اضافه می‌شوند. برای «بروزرسانی محتوا بدون نیاز به ساخت نسخه جدید اپ»
 * استفاده می‌شود. آدرس پیش‌فرض: فایل sample_data.json روی گیت‌هاب (شاخه main).
 */
suspend fun syncRemoteContent(
    db: AppDatabase,
    url: String = "https://raw.githubusercontent.com/lavialireza/tazeahappv-1/main/app/src/main/assets/content/001_sample.json"
): Result<Unit> {
    return try {
        val jsonText = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            withHttpGet(url)
        }
        mergeContentFromJson(db, jsonText)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}

private fun withHttpGet(urlString: String): String {
    val connection = URL(urlString).openConnection() as HttpURLConnection
    connection.connectTimeout = 15000
    connection.readTimeout = 15000
    connection.requestMethod = "GET"
    return try {
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    } finally {
        connection.disconnect()
    }
}

internal suspend fun mergeContentFromJson(db: AppDatabase, jsonText: String) {
    val fields = JSONArray(jsonText)
    db.withTransaction {
        for (fi in 0 until fields.length()) {
            val fieldObj = fields.getJSONObject(fi)
            val fieldTitle = fieldObj.getString("title")
            val fieldId = db.fieldDao().getByTitle(fieldTitle)?.id
                ?: db.fieldDao().insert(FieldEntity(title = fieldTitle))

            val taziehs = fieldObj.getJSONArray("taziehs")
            for (ti in 0 until taziehs.length()) {
                val taziehObj = taziehs.getJSONObject(ti)
                val taziehTitle = taziehObj.getString("title")
                val taziehId = db.taziehDao().getByTitle(fieldId, taziehTitle)?.id
                    ?: db.taziehDao().insert(TaziehEntity(fieldId = fieldId, title = taziehTitle))

                val roles = taziehObj.getJSONArray("roles")
                for (ri in 0 until roles.length()) {
                    val roleObj = roles.getJSONObject(ri)
                    val roleTitle = roleObj.getString("title")
                    val roleId = db.roleDao().getByTitle(taziehId, roleTitle)?.id
                        ?: run {
                            val nextRoleOrder = db.roleDao().getMaxOrderIndex(taziehId) + 1
                            db.roleDao().insert(RoleEntity(taziehId = taziehId, title = roleTitle, orderIndex = nextRoleOrder))
                        }

                    var nextOrderIndex = db.sectionDao().getMaxOrderIndex(roleId) + 1
                    val sections = roleObj.getJSONArray("sections")
                    for (si in 0 until sections.length()) {
                        val secObj = sections.getJSONObject(si)
                        db.sectionDao().insert(
                            SectionEntity(
                                roleId = roleId,
                                orderIndex = nextOrderIndex,
                                title = secObj.getString("title"),
                                content = secObj.getString("content"),
                                audioUrl = secObj.optString("audio", "").ifBlank { null }
                            )
                        )
                        nextOrderIndex++
                    }
                }
            }
        }
    }
}
