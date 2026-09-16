package com.example.bookapp.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import java.io.File
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

data class TaziehImageItem(
    val id: Long,
    val filePath: String,
    val caption: String
)

/**
 * گالری تصاویر یک تعزیه: عکس‌های قدیمی نسخه‌های خطی، تعزیه‌خوانان معروف و ...
 * کاربر خودش از گالری گوشی عکس اضافه می‌کند؛ توضیح (caption) اختیاری است.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaziehGalleryScreen(
    taziehTitle: String,
    images: List<TaziehImageItem>,
    onAddImage: (android.net.Uri) -> Unit,
    onDeleteImage: (TaziehImageItem) -> Unit,
    onUpdateCaption: (TaziehImageItem, String) -> Unit,
    readOnly: Boolean = false,
    onBack: () -> Unit
) {
    var editingImage by remember { mutableStateOf<TaziehImageItem?>(null) }
    var captionText by remember { mutableStateOf("") }
    var previewImage by remember { mutableStateOf<TaziehImageItem?>(null) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(onAddImage) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تصاویر: $taziehTitle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!readOnly) ExtendedFloatingActionButton(
                text = { Text("افزودن عکس") },
                icon = { Icon(Icons.Filled.AddAPhoto, contentDescription = null) },
                onClick = { pickImageLauncher.launch("image/*") }
            )
        }
    ) { padding ->
        if (images.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(
                    if (readOnly) "هنوز عکسی برای این مجلس ثبت نشده است." else "برای همین مجلس از دکمه «افزودن عکس» در پایین صفحه عکس انتخاب کنید.\nمثلاً عکس نسخه‌ی خطی یا تعزیه‌خوانان.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                items(images, key = { it.id }) { image ->
                    Card(shape = RoundedCornerShape(12.dp)) {
                        Column {
                            AsyncImage(
                                model = File(image.filePath),
                                contentDescription = image.caption,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                    .clickable { previewImage = image }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text(
                                    image.caption.ifBlank { "بدون توضیح" },
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable(enabled = !readOnly) {
                                            editingImage = image
                                            captionText = image.caption
                                        }
                                )
                                if (!readOnly) IconButton(onClick = { onDeleteImage(image) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.Delete, contentDescription = "حذف عکس")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val preview = previewImage
    if (preview != null) {
        AlertDialog(
            onDismissRequest = { previewImage = null },
            confirmButton = {},
            dismissButton = {},
            title = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(preview.caption.ifBlank { "تصویر" }, modifier = Modifier.weight(1f))
                    IconButton(onClick = { previewImage = null }) {
                        Icon(Icons.Filled.Close, contentDescription = "بستن")
                    }
                }
            },
            text = {
                Box(Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 620.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    AsyncImage(
                        model = File(preview.filePath),
                        contentDescription = preview.caption,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }

    val current = editingImage
    if (current != null && !readOnly) {
        AlertDialog(
            onDismissRequest = { editingImage = null },
            title = { Text("توضیح عکس") },
            text = {
                OutlinedTextField(
                    value = captionText,
                    onValueChange = { captionText = it },
                    placeholder = { Text("مثلاً «نسخه خطی قرن ۱۳» یا «مرحوم فلانی در نقش شمر»") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onUpdateCaption(current, captionText.trim())
                    editingImage = null
                }) { Text("ذخیره") }
            },
            dismissButton = {
                TextButton(onClick = { editingImage = null }) { Text("انصراف") }
            }
        )
    }
}
