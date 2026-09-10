package com.example.bookapp.data

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * پخش صوت واقعی (ضبط‌شده) برای بخش‌هایی که آهنگ/لحن خاص دارند، به‌عنوان
 * جایگزینی برای صدای مصنوعی (TTS). آدرس صوت (audioUrl) می‌تواند:
 *   - یک URL کامل (http/https) باشد، یا
 *   - یک مسیر نسبی داخل assets باشد (مثلاً "audio/karbala_shahadat.mp3").
 * onStatus با مقادیر "started" / "done" / "error" فراخوانی می‌شود.
 *
 * توجه: setDataSource برای یک URL شبکه‌ای می‌تواند به‌صورت داخلی به شبکه وصل
 * شود؛ به همین دلیل کل آماده‌سازی پخش‌کننده روی Dispatchers.IO انجام می‌شود
 * تا Thread اصلی (UI) هرگز درگیر شبکه/دیسک نشود (جلوگیری از
 * NetworkOnMainThreadException و کندی رابط کاربری).
 */
class AudioPlayerHelper(private val context: Context, private val onStatus: (String) -> Unit = {}) {
    private var player: MediaPlayer? = null
    private var currentJob: Job? = null
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + job)

    fun play(audioUrl: String) {
        stop()
        currentJob = scope.launch {
            var mp: MediaPlayer? = null
            try {
                mp = withContext(Dispatchers.IO) {
                    val prepared = MediaPlayer()
                    if (audioUrl.startsWith("http://") || audioUrl.startsWith("https://")) {
                        prepared.setDataSource(audioUrl)
                    } else if (audioUrl.startsWith("/")) {
                        // مسیر مطلق فایل (مثلاً فایلی که کاربر از داخل اپ اضافه کرده و در
                        // حافظه‌ی اختصاصی اپ کپی شده)، نه یک asset داخل بسته‌ی نصب
                        prepared.setDataSource(audioUrl)
                    } else {
                        val afd = context.assets.openFd(audioUrl)
                        prepared.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        afd.close()
                    }
                    prepared
                }
            } catch (e: Exception) {
                onStatus("error")
                mp?.release()
                return@launch
            }

            // اگر در همین فاصله stop()/play() دوباره صدا زده شده (کوروتین کنسل شده،
            // مثلاً کاربر سریع صفحه را عوض کرده)، پخش‌کننده‌ی تازه‌ساخته را رها می‌کنیم
            if (!isActive) {
                mp.release()
                return@launch
            }

            mp.setOnPreparedListener {
                onStatus("started")
                it.start()
            }
            mp.setOnCompletionListener {
                onStatus("done")
            }
            mp.setOnErrorListener { _, _, _ ->
                onStatus("error")
                true
            }
            try {
                mp.prepareAsync()
                player = mp
            } catch (e: Exception) {
                onStatus("error")
            }
        }
    }

    fun stop() {
        currentJob?.cancel()
        currentJob = null
        try {
            player?.stop()
            player?.release()
        } catch (e: Exception) {
            // نادیده گرفته می‌شود؛ پخش‌کننده ممکن است در وضعیت نامعتبر بوده باشد
        }
        player = null
    }

    fun isPlaying(): Boolean = try { player?.isPlaying == true } catch (e: Exception) { false }

    /** باید هنگام از بین رفتن صفحه/کامپوننت صدا زده شود تا کوروتین‌های داخلی هم پاک شوند */
    fun dispose() {
        stop()
        job.cancel()
    }
}
