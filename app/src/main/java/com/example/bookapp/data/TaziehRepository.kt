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
    suspend fun getAllTaziehs(): List<TaziehEntity> = db.taziehDao().getAll()
    suspend fun getTaziehById(taziehId: Long): TaziehEntity? = db.taziehDao().getById(taziehId)

    // ---- نقش‌ها ----
    suspend fun getRolesByTazieh(taziehId: Long): List<RoleEntity> = db.roleDao().getByTazieh(taziehId)
    suspend fun getRoleById(roleId: Long): RoleEntity = db.roleDao().getById(roleId)
    suspend fun getRoleByIdOrNull(roleId: Long): RoleEntity? = try { db.roleDao().getById(roleId) } catch (e: Exception) { null }
    suspend fun updateRoleTitle(roleId: Long, title: String) = db.roleDao().updateTitle(roleId, title)
    suspend fun updateRoleOrderIndex(roleId: Long, orderIndex: Int) = db.roleDao().updateOrderIndex(roleId, orderIndex)

    // ---- بخش‌ها ----
    suspend fun getSectionsByRole(roleId: Long): List<SectionEntity> = db.sectionDao().getByRole(roleId)
    suspend fun getSectionById(sectionId: Long): SectionEntity = db.sectionDao().getById(sectionId)
    suspend fun updateSectionAudioUrl(sectionId: Long, audioUrl: String?) = db.sectionDao().updateAudioUrl(sectionId, audioUrl)

    // ---- نقش «من» (ذخیره‌شده در SharedPreferences، نه دیتابیس) ----
    fun getMyRole(taziehId: Long): Long? = Prefs.getMyRole(appContext, taziehId)
    fun setMyRole(taziehId: Long, roleId: Long) = Prefs.setMyRole(appContext, taziehId, roleId)
    fun clearMyRole(taziehId: Long) = Prefs.clearMyRole(appContext, taziehId)
    fun getAllMyRoles(): Map<Long, Long> = Prefs.getAllMyRoles(appContext)

    // ---- بوکمارک‌ها ----
    fun getBookmarks(): Set<Long> = Prefs.getBookmarks(appContext)
    fun isBookmarked(sectionId: Long): Boolean = Prefs.isBookmarked(appContext, sectionId)
    /** برمی‌گرداند: وضعیت تازه‌ی بوکمارک بعد از تغییر */
    fun toggleBookmark(sectionId: Long): Boolean = Prefs.toggleBookmark(appContext, sectionId)

    // ---- اخیراً دیده‌شده‌ها / پیشرفت مطالعه ----
    fun getRecentIds(): List<Long> = Prefs.getRecent(appContext)
    fun addRecent(sectionId: Long) = Prefs.addRecent(appContext, sectionId)
    fun markSectionRead(sectionId: Long) = Prefs.markSectionRead(appContext, sectionId)
    fun getReadSectionsCount(): Int = Prefs.getReadSectionsCount(appContext)
    fun getStreakDays(): Int = Prefs.getStreakDays(appContext)
    fun getActiveDaysLast(days: Int): List<Boolean> = Prefs.getActiveDaysLast(appContext, days)

    // ---- جست‌وجو ----
    suspend fun search(query: String, fieldId: Long?, taziehId: Long?): List<SearchResult> = when {
        taziehId != null -> db.searchDao().searchInTazieh(query, taziehId)
        fieldId != null -> db.searchDao().searchInField(query, fieldId)
        else -> db.searchDao().search(query)
    }
    suspend fun searchDialogues(query: String): List<DialogueSearchResult> = db.searchDao().searchDialogues(query)
    suspend fun getResultsByIds(ids: List<Long>): List<SearchResult> = if (ids.isEmpty()) emptyList() else db.searchDao().getByIds(ids)
    suspend fun getRandomSection(): SearchResult? = db.searchDao().getRandomSection()
    suspend fun getRelatedSections(sectionTitle: String, excludeSectionId: Long): List<SearchResult> =
        db.searchDao().getRelatedByTitle(sectionTitle, excludeSectionId)

    // ---- شمارش برای صفحه‌ی «درباره» ----
    data class ContentCounts(val fields: Int, val taziehs: Int, val roles: Int, val sections: Int)
    suspend fun getContentCounts(): ContentCounts = ContentCounts(
        fields = db.searchDao().countFields(),
        taziehs = db.searchDao().countTaziehs(),
        roles = db.searchDao().countRoles(),
        sections = db.searchDao().countSections()
    )

    // ---- یادداشت‌ها ----
    suspend fun getAllNotes(): List<NoteEntity> = db.noteDao().getAll()
    suspend fun insertNote(title: String, content: String) = db.noteDao().insert(NoteEntity(title = title, content = content))
    suspend fun deleteNote(id: Long) = db.noteDao().delete(id)

    // ---- پاورقی‌ها ----
    suspend fun getFootnotesBySection(sectionId: Long): List<FootnoteEntity> = db.footnoteDao().getBySection(sectionId)
    suspend fun insertFootnote(sectionId: Long, term: String, explanation: String) =
        db.footnoteDao().insert(FootnoteEntity(sectionId = sectionId, term = term, explanation = explanation))
    suspend fun updateFootnote(footnote: FootnoteEntity, term: String, explanation: String) =
        db.footnoteDao().update(footnote.copy(term = term, explanation = explanation))
    suspend fun deleteFootnote(footnoteId: Long) = db.footnoteDao().delete(footnoteId)

    // ---- تصاویر یک تعزیه ----
    suspend fun getImagesByTazieh(taziehId: Long): List<TaziehImageEntity> = db.taziehImageDao().getByTazieh(taziehId)
    suspend fun addTaziehImage(context: Context, taziehId: Long, sourceUri: android.net.Uri): Boolean {
        val path = copyImageToAppStorage(context, sourceUri) ?: return false
        db.taziehImageDao().insert(TaziehImageEntity(taziehId = taziehId, filePath = path))
        return true
    }
    suspend fun deleteTaziehImage(imageId: Long, filePath: String) {
        db.taziehImageDao().delete(imageId)
        deleteImageFromAppStorage(filePath)
    }
    suspend fun updateTaziehImageCaption(imageId: Long, caption: String) = db.taziehImageDao().updateCaption(imageId, caption)

    /** همه‌ی تصاویر همه‌ی تعزیه‌ها، برای گالری عمومی صفحه‌ی اصلی */
    suspend fun getAllTaziehImagesWithTaziehTitle(): List<Triple<TaziehImageEntity, String, Long>> {
        val taziehs = db.taziehDao().getAll()
        return taziehs.flatMap { tazieh ->
            db.taziehImageDao().getByTazieh(tazieh.id).map { img -> Triple(img, tazieh.title, tazieh.id) }
        }
    }

    // ---- صدای بخش‌ها ----
    suspend fun attachAudioToSection(context: Context, sectionId: Long, sourceUri: android.net.Uri): String? {
        val path = copyAudioToAppStorage(context, sourceUri) ?: return null
        db.sectionDao().updateAudioUrl(sectionId, path)
        return path
    }
    suspend fun removeAudioFromSection(sectionId: Long, currentAudioUrl: String?) {
        currentAudioUrl?.let { deleteAudioFromAppStorage(it) }
        db.sectionDao().updateAudioUrl(sectionId, null)
    }

    // ---- گفتگوها ----
    suspend fun getDialoguesByTazieh(taziehId: Long): List<DialogueEntity> = db.dialogueDao().getByTazieh(taziehId)
    suspend fun getDialogueById(dialogueId: Long): DialogueEntity = db.dialogueDao().getById(dialogueId)
    suspend fun deleteDialogue(dialogueId: Long) = db.dialogueDao().delete(dialogueId)
    suspend fun getDialogueTurns(dialogueId: Long): List<DialogueTurnEntity> = db.dialogueTurnDao().getByDialogue(dialogueId)
    suspend fun createDialogue(taziehId: Long, title: String, orderedSectionIds: List<Long>): Long {
        val dialogueId = db.dialogueDao().insert(DialogueEntity(taziehId = taziehId, title = title))
        orderedSectionIds.forEachIndexed { index, sectionId ->
            db.dialogueTurnDao().insert(DialogueTurnEntity(dialogueId = dialogueId, sectionId = sectionId, orderIndex = index))
        }
        return dialogueId
    }
    suspend fun swapDialogueTurnOrder(turnA: DialogueTurnEntity, turnB: DialogueTurnEntity) {
        db.dialogueTurnDao().updateOrderIndex(turnA.id, turnB.orderIndex)
        db.dialogueTurnDao().updateOrderIndex(turnB.id, turnA.orderIndex)
    }
    suspend fun deleteDialogueTurn(turnId: Long) = db.dialogueTurnDao().deleteTurn(turnId)

    // ---- خروجی PDF ----
    // این‌ها هر بار یک Context جداگانه از فراخوان می‌گیرند (نه اینکه در سازنده ذخیره شوند)
    // چون برای نمایش Intent اشتراک‌گذاری به Context خودِ Activity نیاز دارند، نه
    // applicationContext؛ نگه‌داشتن Activity context در یک شیء طولانی‌عمر مثل Repository
    // نشتی حافظه ایجاد می‌کند.
    suspend fun exportRoleToPdf(context: Context, roleTitle: String, sections: List<SectionEntity>) =
        com.example.bookapp.data.exportRoleToPdf(context, roleTitle, sections)

    suspend fun exportTaziehToPdf(context: Context, taziehTitle: String, roles: List<Pair<String, List<SectionEntity>>>) =
        com.example.bookapp.data.exportTaziehToPdf(context, taziehTitle, roles)

    suspend fun exportDialogueToPdf(context: Context, dialogueTitle: String, turns: List<Triple<String, String, String>>) =
        com.example.bookapp.data.exportDialogueToPdf(context, dialogueTitle, turns)
}
