package com.example.bookapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bookapp.data.ViewerAccessPolicy
import com.example.bookapp.data.ViewerAccessTransfer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.ExperimentalMaterial3Api

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
    var profile by remember { mutableStateOf(ViewerAccessPolicy.PROFILE_CUSTOM) }
    var expiryText by remember { mutableStateOf("") }
    var customPermissions by remember { mutableStateOf(ViewerAccessPolicy.profileDefaults(profile)) }
    var message by remember { mutableStateOf<String?>(null) }
    var exportTarget by remember { mutableStateOf("*") }
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
        selectedUser = user; installationId = user.installationId; profile = user.profile
        expiryText = user.expiresAt?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it)) } ?: ""
        customPermissions = user.permissions
    }
    fun resetEditor() {
        selectedUser = null; installationId = ""; profile = ViewerAccessPolicy.PROFILE_CUSTOM; expiryText = ""; customPermissions = ViewerAccessPolicy.profileDefaults(ViewerAccessPolicy.PROFILE_CUSTOM)
    }

    Scaffold(topBar = { TopAppBar(title = { Text("مدیریت دسترسی Viewer") }, navigationIcon = { TextButton(onClick = onBack) { Text("بازگشت") } }) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("پروفایل عمومی", style = MaterialTheme.typography.titleMedium)
                        Text("این پروفایل برای همه کاربران عمومی است و تغییرات آن در انتشار بروزرسانی بعدی Viewer اعمال می‌شود.", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        ViewerAccessPolicy.permissionLabels.forEach { (key, label) ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label); Switch(checked = publicPermissions[key] == true, onCheckedChange = { v -> publicPermissions = publicPermissions + (key to v) })
                            }
                        }
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
                        Text("کاربر خاص / همکار", style = MaterialTheme.typography.titleMedium)
                        Text("شناسه نصب Viewer را وارد کنید؛ این شناسه شماره تلفن یا IMEI نیست.", style = MaterialTheme.typography.bodySmall)
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
                            ViewerAccessPolicy.permissionLabels.forEach { (key, label) ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Switch(checked = customPermissions[key] == true, onCheckedChange = { v -> customPermissions = customPermissions + (key to v) }) }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                val normalizedId = installationId.trim().uppercase(Locale.US)
                                if (normalizedId.isBlank()) { message = "شناسه نصب را وارد کنید."; return@Button }
                                val expiry = expiryText.trim().takeIf { it.isNotBlank() }?.let { runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it)?.time }.getOrNull() }
                                if (expiryText.isNotBlank() && expiry == null) { message = "تاریخ انقضا معتبر نیست."; return@Button }
                                runCatching {
                                    ViewerAccessPolicy.upsertSpecialUser(context, ViewerAccessPolicy.SpecialUser(normalizedId, profile, expiry, customPermissions))
                                    val reloaded = ViewerAccessPolicy.getSpecialUsers(context)
                                    check(reloaded.any { it.installationId.equals(normalizedId, ignoreCase = true) }) { "شناسه پس از ذخیره پیدا نشد." }
                                    specialUsers = reloaded
                                    resetEditor()
                                    message = "کاربر خاص «$normalizedId» با موفقیت ذخیره شد."
                                }.onFailure { e ->
                                    message = "ذخیره کاربر خاص انجام نشد: ${e.message ?: "خطای نامشخص"}"
                                }
                            }, Modifier.weight(1f)) { Text("ذخیره و اعمال") }
                            OutlinedButton(onClick = { resetEditor() }, Modifier.weight(1f)) { Text("جدید") }
                        }
                        if (installationId.isNotBlank() && specialUsers.any { it.installationId.equals(installationId.trim(), ignoreCase = true) }) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { exportTarget = installationId.trim(); exportLauncher.launch("viewer-access-${installationId.trim()}.json") }, modifier = Modifier.fillMaxWidth()) { Text("خروجی سیاست این Viewer") }
                            Button(onClick = { sendDirectlyToViewer(installationId.trim()) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Send, null); Spacer(Modifier.width(6.dp)); Text("ارسال مستقیم به همین Viewer") }
                        }
                    }
                }
            }
            item { Text("کاربران خاص (${specialUsers.size})", style = MaterialTheme.typography.titleMedium) }
            items(specialUsers, key = { it.installationId }) { user ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(user.installationId); Text("پروفایل: ${profileTitle(user.profile)}", style = MaterialTheme.typography.bodySmall)
                            Text("انقضا: ${user.expiresAt?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it)) } ?: "بدون انقضا"}", style = MaterialTheme.typography.bodySmall)
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
