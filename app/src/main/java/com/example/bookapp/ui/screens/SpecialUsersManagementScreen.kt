package com.example.bookapp.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import android.content.Intent
import com.example.bookapp.data.ViewerAccessTransfer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bookapp.data.ViewerAccessPolicy
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecialUsersManagementScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var users by remember { mutableStateOf(ViewerAccessPolicy.getSpecialUsers(context)) }
    var selected by remember { mutableStateOf<ViewerAccessPolicy.SpecialUser?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("all") }
    var sortMode by remember { mutableStateOf("name") }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }

    fun reload() { users = ViewerAccessPolicy.getSpecialUsers(context) }
    fun statusOf(user: ViewerAccessPolicy.SpecialUser): String = when {
        !user.enabled -> "disabled"
        user.expiresAt != null && user.expiresAt > 0L && System.currentTimeMillis() > user.expiresAt -> "expired"
        else -> "active"
    }
    val visibleUsers = remember(users, searchQuery, statusFilter, sortMode) {
        val q = searchQuery.trim().lowercase(Locale.getDefault())
        users.filter { user ->
            val matchesQuery = q.isBlank() || listOf(user.displayName, user.phone, user.position, user.userType, user.installationId, user.details)
                .any { it.lowercase(Locale.getDefault()).contains(q) }
            val matchesStatus = statusFilter == "all" || statusOf(user) == statusFilter
            matchesQuery && matchesStatus
        }.sortedWith(when (sortMode) {
            "created" -> compareByDescending<ViewerAccessPolicy.SpecialUser> { it.createdAt }
            "expiry" -> compareBy<ViewerAccessPolicy.SpecialUser> { it.expiresAt ?: Long.MAX_VALUE }
            else -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.displayName.ifBlank { it.installationId } }
        })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("مدیریت کاربران") },
                navigationIcon = { TextButton(onClick = onBack) { Text("بازگشت") } },
                actions = { IconButton(onClick = { reload() }) { Icon(Icons.Filled.Refresh, "بازخوانی") } }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(14.dp)) {
            Text("کاربران خاص", style = MaterialTheme.typography.headlineSmall)
            Text("برای مشاهده و ویرایش پرونده هر کاربر روی کارت او بزنید.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                label = { Text("جستجوی نام، همراه، سمت یا شناسه") }
            )
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("all" to "همه", "active" to "فعال", "disabled" to "غیرفعال", "expired" to "منقضی").forEach { (v, label) ->
                    FilterChip(selected = statusFilter == v, onClick = { statusFilter = v }, label = { Text(label) })
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("مرتب‌سازی:", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                listOf("name" to "نام", "created" to "جدیدترین ثبت", "expiry" to "نزدیک‌ترین انقضا").forEach { (v, label) ->
                    FilterChip(selected = sortMode == v, onClick = { sortMode = v }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("نمایش ${visibleUsers.size} نفر از ${users.size} کاربر", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            if (users.isEmpty()) {
                Card(Modifier.fillMaxWidth()) { Text("هنوز کاربر خاصی ثبت نشده است.", Modifier.padding(18.dp)) }
            } else if (visibleUsers.isEmpty()) {
                Card(Modifier.fillMaxWidth()) { Text("کاربری با این جستجو یا فیلتر پیدا نشد.", Modifier.padding(18.dp)) }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(visibleUsers, key = { it.installationId }) { user ->
                        val status = when {
                            !user.enabled -> "غیرفعال"
                            user.expiresAt != null && user.expiresAt > 0L && System.currentTimeMillis() > user.expiresAt -> "منقضی"
                            else -> "فعال"
                        }
                        Card(Modifier.fillMaxWidth().clickable { selected = user }) {
                            Column(Modifier.padding(14.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(user.displayName.ifBlank { "کاربر بدون نام" }, style = MaterialTheme.typography.titleMedium)
                                    Text(status, color = if (status == "فعال") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("شناسه: ${user.installationId}", style = MaterialTheme.typography.bodySmall)
                                if (user.phone.isNotBlank()) Text("همراه: ${user.phone}", style = MaterialTheme.typography.bodySmall)
                                if (user.position.isNotBlank()) Text("سمت: ${user.position}", style = MaterialTheme.typography.bodySmall)
                                Text("نوع کاربری: ${user.userType.ifBlank { user.profile }}", style = MaterialTheme.typography.bodySmall)
                                Text("ثبت: ${dateFormat.format(Date(user.createdAt))}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            message?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }

    selected?.let { user ->
        SpecialUserEditorDialog(
            user = user,
            onDismiss = { selected = null },
            onMessage = { message = it },
            onSaved = { updated ->
                ViewerAccessPolicy.upsertSpecialUser(context, updated)
                reload(); selected = null; message = "اطلاعات کاربر «${updated.displayName.ifBlank { updated.installationId }}» ذخیره شد."
            },
            onDeleted = {
                ViewerAccessPolicy.removeSpecialUser(context, user.installationId)
                reload(); selected = null; message = "کاربر حذف شد."
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpecialUserEditorDialog(
    user: ViewerAccessPolicy.SpecialUser,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
    onSaved: (ViewerAccessPolicy.SpecialUser) -> Unit,
    onDeleted: () -> Unit
) {
    var name by remember(user.installationId) { mutableStateOf(user.displayName) }
    var details by remember(user.installationId) { mutableStateOf(user.details) }
    var phone by remember(user.installationId) { mutableStateOf(user.phone) }
    var address by remember(user.installationId) { mutableStateOf(user.address) }
    var position by remember(user.installationId) { mutableStateOf(user.position) }
    var userType by remember(user.installationId) { mutableStateOf(user.userType) }
    var other by remember(user.installationId) { mutableStateOf(user.otherDetails) }
    var expiry by remember(user.installationId) { mutableStateOf(user.expiresAt?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it)) } ?: "") }
    var enabled by remember(user.installationId) { mutableStateOf(user.enabled) }
    var profile by remember(user.installationId) { mutableStateOf(user.profile) }
    var permissions by remember(user.installationId) { mutableStateOf(user.permissions) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("پرونده کاربر") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                item { Text("شناسه نصب: ${user.installationId}", style = MaterialTheme.typography.bodySmall) }
                item { OutlinedTextField(name, { name = it }, label = { Text("نام کاربری") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(phone, { phone = it }, label = { Text("شماره همراه") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(address, { address = it }, label = { Text("آدرس") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
                item { OutlinedTextField(position, { position = it }, label = { Text("سمت") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(userType, { userType = it }, label = { Text("نوع کاربری") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(details, { details = it }, label = { Text("مشخصات کاربر") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
                item { OutlinedTextField(other, { other = it }, label = { Text("سایر مشخصات") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
                item { OutlinedTextField(expiry, { expiry = it }, label = { Text("تاریخ انقضا (YYYY-MM-DD؛ خالی = بدون انقضا)") }, modifier = Modifier.fillMaxWidth()) }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (enabled) "کاربر فعال" else "کاربر غیرفعال")
                        Switch(enabled, { enabled = it })
                    }
                }
                item {
                    Text("پروفایل دسترسی", style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(ViewerAccessPolicy.PROFILE_PUBLIC to "عمومی", ViewerAccessPolicy.PROFILE_TRAINING to "تمرینی", ViewerAccessPolicy.PROFILE_COLLABORATOR to "همکار", ViewerAccessPolicy.PROFILE_CUSTOM to "سفارشی").forEach { (v,l) ->
                            FilterChip(profile == v, { profile = v; permissions = ViewerAccessPolicy.profileDefaults(v) }, label = { Text(l) })
                        }
                    }
                }
                if (profile == ViewerAccessPolicy.PROFILE_CUSTOM) {
                    item { Text("مجوزهای اختصاصی", style = MaterialTheme.typography.titleSmall) }
                    items(ViewerAccessPolicy.permissionLabels.toList()) { entry ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(entry.second); Switch(permissions[entry.first] == true, { permissions = permissions + (entry.first to it) })
                        }
                    }
                }
                error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error) } }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    runCatching {
                        val uri = ViewerAccessTransfer.createShareUri(context, user.installationId)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            setPackage("com.example.bookapp.viewer")
                        }
                        context.startActivity(intent)
                    }.onFailure {
                        onMessage("ارسال سیاست به Viewer ناموفق بود: ${it.message ?: "خطای نامشخص"}")
                    }
                }) { Icon(Icons.Filled.Send, null); Spacer(Modifier.width(3.dp)); Text("ارسال سیاست") }
                TextButton(onClick = {
                val exp = if (expiry.isBlank()) null else runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(expiry)?.time }.getOrNull()
                if (expiry.isNotBlank() && exp == null) { error = "تاریخ انقضا معتبر نیست."; return@TextButton }
                onSaved(user.copy(displayName = name.trim(), details = details.trim(), phone = phone.trim(), address = address.trim(), position = position.trim(), userType = userType.trim(), otherDetails = other.trim(), expiresAt = exp, enabled = enabled, profile = profile, permissions = permissions))
            }) { Icon(Icons.Filled.Save, null); Spacer(Modifier.width(4.dp)); Text("ذخیره") }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("انصراف") }
                TextButton(onClick = onDeleted, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Filled.Delete, null); Text("حذف") }
            }
        }
    )
}
