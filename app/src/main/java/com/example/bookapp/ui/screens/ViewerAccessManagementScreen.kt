package com.example.bookapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bookapp.data.ViewerAccessPolicy
import com.example.bookapp.data.ViewerAccessTransfer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.content.ClipData
import androidx.compose.material3.ExperimentalMaterial3Api


private data class PermissionGroup(
    val title: String,
    val keys: List<String>
)

private val accessPermissionGroups = listOf(
    PermissionGroup("مطالعه و جستجو", listOf("read", "search", "advancedSearch", "compare", "training")),
    PermissionGroup("رسانه و نمایش", listOf("audio", "tts", "gallery")),
    PermissionGroup("امکانات شخصی", listOf("notes", "bookmarks", "copy", "share")),
    PermissionGroup("امکانات پژوهشی", listOf("footnotes")),
    PermissionGroup("خروجی", listOf("pdf")),
    PermissionGroup("اطلاعات برنامه", listOf("appIntro"))
)

private fun permissionDescription(key: String): String = when (key) {
    "read" -> "مشاهده محتوای تعزیه و ورود به بخش‌های مطالعه"
    "search" -> "استفاده از جستجوی معمولی در محتوای Viewer"
    "advancedSearch" -> "استفاده از امکانات و فیلترهای پیشرفته جستجو"
    "compare" -> "دسترسی به مقایسه متون و بخش‌ها"
    "training" -> "استفاده از حالت تمرین و بازخوانی"
    "audio" -> "پخش فایل‌های صوتی مرتبط با محتوا"
    "tts" -> "خواندن متن با تبدیل متن به گفتار"
    "notes" -> "ایجاد و مدیریت یادداشت‌های شخصی"
    "bookmarks" -> "افزودن و مشاهده علاقه‌مندی‌ها"
    "gallery" -> "مشاهده و استفاده از گالری تصاویر"
    "copy" -> "کپی متن از محتوای Viewer"
    "share" -> "اشتراک‌گذاری محتوای مجاز"
    "pdf" -> "استفاده از خروجی PDF"
    "footnotes" -> "مشاهده و استفاده از پاورقی‌ها"
    "appIntro" -> "دسترسی به معرفی و اطلاعات برنامه"
    else -> ""
}

@Composable
private fun PermissionGroupEditor(
    permissions: Map<String, Boolean>,
    onChange: (String, Boolean) -> Unit,
    searchQuery: String,
    compact: Boolean = false
) {
    var expanded by remember { mutableStateOf(!compact) }
    val labels = ViewerAccessPolicy.permissionLabels
    val visible = accessPermissionGroups.flatMap { group ->
        val items = group.keys.mapNotNull { key -> labels[key]?.let { key to it } }
            .filter { (key, label) ->
                searchQuery.isBlank() || label.contains(searchQuery, ignoreCase = true) ||
                    key.contains(searchQuery, ignoreCase = true)
            }
        if (items.isEmpty()) null else group to items
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        visible.forEach { (group, items) ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { expanded = !expanded },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(group.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${items.count { permissions[it.first] == true }} از ${items.size} فعال",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(if (expanded) "▲" else "▼")
                    }
                    if (expanded) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TextButton(onClick = {
                                group.keys.forEach { key ->
                                    if (labels.containsKey(key)) onChange(key, true)
                                }
                            }) { Text("فعال کردن همه") }
                            TextButton(onClick = {
                                group.keys.forEach { key ->
                                    if (labels.containsKey(key)) onChange(key, false)
                                }
                            }) { Text("غیرفعال کردن همه") }
                        }
                        items.forEach { (key, label) ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(label)
                                    val desc = permissionDescription(key)
                                    if (desc.isNotBlank()) Text(desc, style = MaterialTheme.typography.bodySmall)
                                }
                                Switch(
                                    checked = permissions[key] == true,
                                    onCheckedChange = { value -> onChange(key, value) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ViewerAccessManagementScreen(
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var publicPermissions by remember { mutableStateOf(ViewerAccessPolicy.getPublicPermissions(context)) }
    var specialUsers by remember { mutableStateOf(ViewerAccessPolicy.getSpecialUsers(context)) }
    var selectedUser by remember { mutableStateOf<ViewerAccessPolicy.SpecialUser?>(null) }
    var installationId by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }
    var profile by remember { mutableStateOf(ViewerAccessPolicy.PROFILE_CUSTOM) }
    var expiryText by remember { mutableStateOf("") }
    var customPermissions by remember { mutableStateOf(ViewerAccessPolicy.profileDefaults(profile)) }
    var message by remember { mutableStateOf<String?>(null) }
    var accessTestMessage by remember { mutableStateOf<String?>(null) }
    var exportTarget by remember { mutableStateOf("*") }
    var permissionSearch by remember { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.openOutputStream(uri)?.let { ViewerAccessTransfer.writePolicy(context, exportTarget, it) } ?: error("فایل خروجی باز نشد.") }
                .onSuccess { message = "فایل سیاست دسترسی آماده شد؛ آن را به Viewer منتقل کنید." }
                .onFailure { message = "خروجی سیاست ناموفق بود: ${it.message ?: "خطای نامشخص"}" }
        }
    }
    fun sendDirectlyToViewer(target: String) {
        runCatching {
            val uri = ViewerAccessTransfer.createShareUri(context, target)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage("com.example.bookapp.viewer")
            }
            context.startActivity(intent)
        }.onFailure {
            // اگر Viewer نصب نیست، همان فایل را از طریق Share Sheet در اختیار کاربر می‌گذاریم.
            runCatching {
                val uri = ViewerAccessTransfer.createShareUri(context, target)
                val fallback = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(fallback, "ارسال سیاست دسترسی به Viewer"))
            }.onFailure { e -> message = "ارسال به Viewer ناموفق بود: ${e.message ?: "خطای نامشخص"}" }
        }
    }

    fun loadUser(user: ViewerAccessPolicy.SpecialUser) {
        selectedUser = user
        installationId = user.installationId
        displayName = user.displayName
        details = user.details
        enabled = user.enabled
        profile = user.profile
        expiryText = user.expiresAt?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it)) } ?: ""
        customPermissions = user.permissions
    }
    fun resetEditor() {
        selectedUser = null; installationId = ""; displayName = ""; details = ""; enabled = true; profile = ViewerAccessPolicy.PROFILE_CUSTOM; expiryText = ""; customPermissions = ViewerAccessPolicy.profileDefaults(ViewerAccessPolicy.PROFILE_CUSTOM)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("مدیریت دسترسی Viewer") }, navigationIcon = { TextButton(onClick = onBack) { Text("بازگشت") } }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("پروفایل عمومی", style = MaterialTheme.typography.titleMedium)
                        Text("این پروفایل برای همه کاربران عمومی است و تغییرات آن در انتشار بروزرسانی بعدی Viewer اعمال می‌شود.", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = permissionSearch,
                            onValueChange = { permissionSearch = it },
                            label = { Text("جستجوی دسترسی") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(6.dp))
                        PermissionGroupEditor(
                            permissions = publicPermissions,
                            onChange = { key, value -> publicPermissions = publicPermissions + (key to value) },
                            searchQuery = permissionSearch
                        )
                        Button(onClick = { ViewerAccessPolicy.setPublicPermissions(context, publicPermissions); message = "پروفایل عمومی ذخیره شد." }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Save, null); Spacer(Modifier.width(6.dp)); Text("ذخیره پروفایل عمومی") }
                        OutlinedButton(onClick = { exportTarget = "*"; exportLauncher.launch("viewer-access-public.json") }, modifier = Modifier.fillMaxWidth()) { Text("خروجی سیاست عمومی برای Viewer") }
                        Button(onClick = { sendDirectlyToViewer("*") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Send, null); Spacer(Modifier.width(6.dp)); Text("ارسال مستقیم به Viewer") }
                        Text("نسخه سیاست: ${ViewerAccessPolicy.getPolicyVersion(context)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(if (selectedUser == null) "افزودن کاربر خاص" else "ویرایش کاربر خاص", style = MaterialTheme.typography.titleMedium)
                        Text("برای هر Viewer یک پرونده مدیریتی مستقل نگه‌داری می‌شود. شناسه نصب شماره تلفن یا IMEI نیست.", style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(displayName, { displayName = it }, label = { Text("نام کاربر") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(details, { details = it }, label = { Text("مشخصات / توضیحات کاربر") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(if (enabled) "وضعیت: فعال" else "وضعیت: غیرفعال", style = MaterialTheme.typography.bodyMedium)
                                Text(if (enabled) "دسترسی اختصاصی این کاربر قابل اعمال است." else "دسترسی اختصاصی این کاربر فعلاً اعمال نمی‌شود.", style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = enabled, onCheckedChange = { enabled = it })
                        }
                        OutlinedTextField(installationId, { installationId = it.uppercase(Locale.US) }, label = { Text("شناسه نصب") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(ViewerAccessPolicy.PROFILE_PUBLIC to "عمومی", ViewerAccessPolicy.PROFILE_TRAINING to "تمرینی", ViewerAccessPolicy.PROFILE_COLLABORATOR to "همکار", ViewerAccessPolicy.PROFILE_CUSTOM to "سفارشی").forEach { (value, label) ->
                                FilterChip(selected = profile == value, onClick = { profile = value; customPermissions = ViewerAccessPolicy.profileDefaults(value) }, label = { Text(label) })
                            }
                        }
                        OutlinedTextField(expiryText, { expiryText = it }, label = { Text("انقضا (YYYY-MM-DD، خالی = بدون انقضا)") }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        if (profile == ViewerAccessPolicy.PROFILE_CUSTOM) {
                            Text("مجوزهای اختصاصی", style = MaterialTheme.typography.titleSmall)
                            PermissionGroupEditor(
                                permissions = customPermissions,
                                onChange = { key, value -> customPermissions = customPermissions + (key to value) },
                                searchQuery = permissionSearch,
                                compact = true
                            )
                        }
                        if (profile == ViewerAccessPolicy.PROFILE_CUSTOM) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "خلاصه مجوزها: ${customPermissions.count { it.value }} از ${ViewerAccessPolicy.permissionLabels.size} قابلیت فعال",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val normalizedId = installationId.trim().uppercase(Locale.US)
                                if (normalizedId.isBlank()) { message = "شناسه نصب را وارد کنید."; return@Button }
                                val expiry = expiryText.trim().takeIf { it.isNotBlank() }?.let { raw -> runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(raw)?.time }.getOrNull() }
                                if (expiryText.isNotBlank() && expiry == null) { message = "تاریخ انقضا معتبر نیست."; return@Button }
                                runCatching {
                                    ViewerAccessPolicy.upsertSpecialUser(
                                        context,
                                        ViewerAccessPolicy.SpecialUser(
                                            installationId = normalizedId,
                                            profile = profile,
                                            expiresAt = expiry,
                                            permissions = customPermissions,
                                            displayName = displayName.trim(),
                                            details = details.trim(),
                                            phone = selectedUser?.phone ?: "",
                                            address = selectedUser?.address ?: "",
                                            position = selectedUser?.position ?: "",
                                            userType = selectedUser?.userType ?: "",
                                            otherDetails = selectedUser?.otherDetails ?: "",
                                            enabled = enabled,
                                            createdAt = selectedUser?.createdAt ?: System.currentTimeMillis()
                                        )
                                    )
                                    val reloaded = ViewerAccessPolicy.getSpecialUsers(context)
                                    check(reloaded.any { it.installationId.equals(normalizedId, ignoreCase = true) }) { "شناسه پس از ذخیره پیدا نشد." }
                                    specialUsers = reloaded
                                    // فرم را پاک نمی‌کنیم تا کاربر بلافاصله اطلاعات ذخیره‌شده را ببیند
                                    // و بتواند در صورت نیاز همان رکورد را دوباره ویرایش کند.
                                    selectedUser = reloaded.firstOrNull { it.installationId.equals(normalizedId, ignoreCase = true) }
                                    installationId = normalizedId
                                    message = "کاربر خاص «$normalizedId» واقعاً در حافظه برنامه ذخیره و بازیابی شد."
                                }.onFailure { e ->
                                    message = "ذخیره کاربر خاص انجام نشد: ${e.message ?: "خطای نامشخص"}"
                                }
                            }, Modifier.weight(1f)) { Text("ذخیره و اعمال") }
                            OutlinedButton(onClick = { resetEditor(); accessTestMessage = null }, Modifier.weight(1f)) { Text("جدید") }
                        }
                        if (installationId.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    val id = installationId.trim().uppercase(Locale.US)
                                    val match = ViewerAccessPolicy.getSpecialUsers(context).firstOrNull { it.installationId == id }
                                    accessTestMessage = if (match == null) {
                                        "نتیجه آزمون: شناسه «$id» در فهرست کاربران خاص ذخیره نشده است."
                                    } else {
                                        val expired = match.expiresAt != null && match.expiresAt > 0L && System.currentTimeMillis() > match.expiresAt
                                        val active = match.permissions.count { it.value }
                                        if (!match.enabled) "نتیجه آزمون: «${match.displayName.ifBlank { id }}» غیرفعال است."
                                        else if (expired) "نتیجه آزمون: «${match.displayName.ifBlank { id }}» پیدا شد، اما دسترسی آن منقضی شده است."
                                        else "نتیجه آزمون: «${match.displayName.ifBlank { id }}» فعال است؛ $active قابلیت فعال دارد."
                                    }
                                }, Modifier.weight(1f)) { Text("آزمون دسترسی") }
                                OutlinedButton(onClick = {
                                    val id = installationId.trim().uppercase(Locale.US)
                                    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("شناسه نصب Viewer", id))
                                    message = "شناسه «$id» در کلیپ‌بورد کپی شد."
                                }, Modifier.weight(1f)) { Icon(Icons.Filled.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text("کپی شناسه") }
                            }
                            accessTestMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                        }
                        if (installationId.isNotBlank() && specialUsers.any { it.installationId.equals(installationId.trim(), ignoreCase = true) }) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { exportTarget = installationId.trim(); exportLauncher.launch("viewer-access-${installationId.trim()}.json") }, modifier = Modifier.fillMaxWidth()) { Text("خروجی سیاست این Viewer") }
                            Button(onClick = { sendDirectlyToViewer(installationId.trim()) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Send, null); Spacer(Modifier.width(6.dp)); Text("ارسال مستقیم به همین Viewer") }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("کاربران خاص (${specialUsers.size})", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = {
                        specialUsers = ViewerAccessPolicy.getSpecialUsers(context)
                        message = "فهرست کاربران خاص از حافظه پایدار دوباره بارگذاری شد."
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "بارگذاری مجدد")
                    }
                }
            }
            items(specialUsers, key = { it.installationId }) { user ->
                Card(Modifier.fillMaxWidth().clickable { loadUser(user) }) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(user.displayName.ifBlank { "بدون نام" }, style = MaterialTheme.typography.titleSmall)
                            Text("شناسه: ${user.installationId}", style = MaterialTheme.typography.bodySmall)
                            Text("پروفایل: ${profileTitle(user.profile)} | ${if (user.enabled) "فعال" else "غیرفعال"}", style = MaterialTheme.typography.bodySmall)
                            Text("انقضا: ${user.expiresAt?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it)) } ?: "بدون انقضا"}", style = MaterialTheme.typography.bodySmall)
                            if (user.details.isNotBlank()) Text(user.details, style = MaterialTheme.typography.bodySmall)
                            Text("مجوزهای فعال: ${user.permissions.count { it.value }} از ${ViewerAccessPolicy.permissionLabels.size}", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { loadUser(user) }) { Text("ویرایش") }
                        IconButton(onClick = { ViewerAccessPolicy.removeSpecialUser(context, user.installationId); specialUsers = ViewerAccessPolicy.getSpecialUsers(context) }) { Icon(Icons.Filled.Delete, null) }
                    }
                }
            }
            item { Text("شناسه نصب این دستگاه: ${ViewerAccessPolicy.installationId(context)}", style = MaterialTheme.typography.bodySmall) }
            if (message != null) item { Text(message!!, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

private fun profileTitle(profile: String) = when (profile) {
    ViewerAccessPolicy.PROFILE_PUBLIC -> "عمومی"
    ViewerAccessPolicy.PROFILE_TRAINING -> "تمرینی"
    ViewerAccessPolicy.PROFILE_COLLABORATOR -> "همکار"
    else -> "سفارشی"
}
