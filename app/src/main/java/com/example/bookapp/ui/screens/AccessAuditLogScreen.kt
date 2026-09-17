package com.example.bookapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bookapp.data.AccessAuditLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccessAuditLogScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var entries by remember { mutableStateOf(AccessAuditLog.getAll(context)) }
    var confirmClear by remember { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("سوابق تغییرات کاربران") },
            navigationIcon = { TextButton(onClick = onBack) { Text("بازگشت") } },
            actions = {
                IconButton(onClick = { entries = AccessAuditLog.getAll(context) }) { Icon(Icons.Filled.Refresh, "بازخوانی") }
                IconButton(enabled = entries.isNotEmpty(), onClick = { confirmClear = true }) { Icon(Icons.Filled.Delete, "پاک کردن سوابق") }
            }
        )
    }) { pad ->
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(pad).padding(20.dp)) { Text("هنوز سابقه‌ای ثبت نشده است.") }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("تعداد سوابق: ${entries.size}", style = MaterialTheme.typography.bodySmall) }
                items(entries) { e ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(e.action, style = MaterialTheme.typography.titleMedium)
                            Text("کاربر: ${e.displayName.ifBlank { "بدون نام" }}", style = MaterialTheme.typography.bodyMedium)
                            Text("شناسه: ${e.installationId}", style = MaterialTheme.typography.bodySmall)
                            Text(dateFormat.format(Date(e.timestamp)), style = MaterialTheme.typography.bodySmall)
                            if (e.details.isNotBlank()) Text(e.details, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("پاک کردن سوابق") },
        text = { Text("همه سوابق تغییرات کاربران خاص از این دستگاه حذف شود؟") },
        confirmButton = { TextButton(onClick = { AccessAuditLog.clear(context); entries = emptyList(); confirmClear = false }) { Text("پاک کردن") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("انصراف") } }
    )
}
