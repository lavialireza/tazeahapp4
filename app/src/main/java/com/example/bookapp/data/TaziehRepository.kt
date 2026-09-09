package com.example.bookapp.data

import android.content.Context

/**
 * لایه‌ی واسط بین ViewModelها و منبع داده‌ی واقعی (Room + SharedPreferences).
 *
 * چرا این لایه لازم است: پیش از این، هر Composable داخل AppNavigation.kt مستقیماً
 * db.xxxDao() و Prefs.xxx(context, ...) را صدا می‌زد. این کار باعث می‌شد منطق
 * دیتابیس در دل UI پخش شود و تست‌کردنش بدون اجرای کامل Compose ممکن نباشد.
 * حالا ViewModelها فقط با این Repository کار می‌کنند؛ اگر روزی منبع داده عوض شود
 * (مثلاً caching اضافه شود یا Room با چیز دیگری جایگزین شود)، فقط همین فایل
 * تغییر می‌کند و ViewModelها دست‌نخورده می‌مانند.
 *
 * نکته درباره‌ی exportRoleToPdf: برخلاف بقیه‌ی توابع، این یکی هر بار یک Context
 * جداگانه از فراخوان می‌گیرد (نه اینکه در سازنده ذخیره شود) چون برای نمایش
 * Intent اشتراک‌گذاری به Context خودِ Activity نیاز دارد، نه applicationContext؛
 * نگه‌داشتن Activity context در یک شیء طولانی‌عمر مثل Repository نشتی حافظه
 * ایجاد می‌کند.
 */
class TaziehRepository(
    context: Context,
    private val db: AppDatabase
) {
    private val appContext = context.applicationContext

    // ---- زمینه‌ها ----
    suspend fun getFields(): List<FieldEntity> = db.fieldDao().getAll()

    // ---- تعزیه‌ها ----
    suspend fun getTaziehsByField(fieldId: Long): List<TaziehEntity> = db.taziehDao().getByField(fieldId)

    // ---- نقش‌ها ----
    suspend fun getRolesByTazieh(taziehId: Long): List<RoleEntity> = db.roleDao().getByTazieh(taziehId)

    // ---- بخش‌ها ----
    suspend fun getSectionsByRole(roleId: Long): List<SectionEntity> = db.sectionDao().getByRole(roleId)

    // ---- نقش «من» (ذخیره‌شده در SharedPreferences، نه دیتابیس) ----
    fun getMyRole(taziehId: Long): Long? = Prefs.getMyRole(appContext, taziehId)
    fun setMyRole(taziehId: Long, roleId: Long) = Prefs.setMyRole(appContext, taziehId, roleId)

    // ---- خروجی PDF ----
    suspend fun exportRoleToPdf(context: Context, roleTitle: String, sections: List<SectionEntity>) =
        com.example.bookapp.data.exportRoleToPdf(context, roleTitle, sections)
}
