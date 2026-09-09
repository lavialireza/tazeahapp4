package com.example.bookapp.ui.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.bookapp.data.TaziehRepository

/**
 * چون همه‌ی ViewModelهای این پروژه فقط یک وابستگی (TaziehRepository) در سازنده‌شان
 * دارند، به‌جای نوشتن یک ViewModelProvider.Factory جداگانه برای هرکدام، از این
 * تابع کمکی استفاده می‌کنیم.
 *
 * ViewModel ساخته‌شده به NavBackStackEntry جاری (مقصد فعلی در NavHost) وابسته
 * است: یعنی اگر کاربر مثلاً به دو «نقش» مختلف برود، هرکدام یک نمونه‌ی جداگانه از
 * RolesViewModel می‌گیرند و با خروج از آن مقصد، ViewModel و State داخلش هم پاک
 * می‌شود - دقیقاً همان رفتاری که با remember/LaunchedEffect در نسخه‌ی قبلی وجود
 * داشت، با این تفاوت که حالا در برابر recomposition (نه فقط navigation) هم
 * پایدار است.
 */
@Composable
inline fun <reified VM : ViewModel> rememberRepositoryViewModel(
    repository: TaziehRepository,
    crossinline create: (TaziehRepository) -> VM
): VM {
    val factory = remember(repository) {
        viewModelFactory {
            initializer { create(repository) }
        }
    }
    return viewModel(factory = factory)
}
