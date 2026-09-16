package com.example.bookapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

data class GalleryImageItem(
    val id: Long,
    val filePath: String,
    val caption: String,
    val taziehTitle: String
)

/**
 * گالری تجمیعی همه‌ی عکس‌های همه‌ی تعزیه‌ها، از منوی اصلی در دسترس.
 * برخلاف گالری داخل هر تعزیه، این صفحه کاملاً فقط-نمایشی است: کاربر عادی
 * فقط عکس‌ها را می‌بیند و نمی‌تواند اضافه یا حذف کند. افزودن/حذف عکس همچنان
 * فقط از همان صفحه‌ی «تصاویر» داخل هر تعزیه (که قبلاً ساخته شده) ممکن است.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllImagesGalleryScreen(
    images: List<GalleryImageItem>,
    onBack: () -> Unit
) {
    val previewImageState = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<GalleryImageItem?>(null) }
    val previewImage = previewImageState.value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("گالری تصاویر") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        if (images.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text(
                    "هنوز عکسی اضافه نشده.",
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
                                model = image.filePath,
                                contentDescription = image.caption,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                    .clickable { previewImageState.value = image }
                            )
                            Column(Modifier.padding(8.dp)) {
                                Text(
                                    image.taziehTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    image.caption.ifBlank { "بدون توضیح" },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val preview = previewImage
    if (preview != null) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { previewImageState.value = null },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Card(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(12.dp)) {
                    Box(Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = preview.filePath,
                            contentDescription = preview.caption,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                        TextButton(
                            onClick = { previewImageState.value = null },
                            modifier = Modifier.align(androidx.compose.ui.Alignment.TopEnd)
                        ) { Text("بستن") }
                    }
                }
            }
        }
    }
}
