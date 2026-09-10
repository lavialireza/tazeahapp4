package com.example.bookapp.data

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * خروجی PDF از تمام بخش‌های یک نقش (برای چاپ یا نگه‌داری آفلاین).
 * از StaticLayout برای چیدمان صحیح متن فارسی (راست‌به‌چپ) استفاده می‌شود.
 */
suspend fun exportRoleToPdf(context: Context, roleTitle: String, sections: List<SectionEntity>) {
    exportPdfInternal(context, roleTitle, listOf(roleTitle to sections))
}

/**
 * خروجی PDF از کل یک تعزیه: همه‌ی نقش‌ها پشت سر هم، هرکدام با عنوان نقش
 * به‌عنوان زیرعنوان، مناسب برای کارگردانی که می‌خواهد کل متن تعزیه را چاپ کند.
 */
suspend fun exportTaziehToPdf(context: Context, taziehTitle: String, roles: List<Pair<String, List<SectionEntity>>>) {
    exportPdfInternal(context, taziehTitle, roles)
}

/**
 * خروجی PDF از یک گفتگو: هر نوبت به‌عنوان یک بخش مستقل با نام نقش‌گوینده‌اش
 * به‌ترتیب چاپ می‌شود، شبیه یک نمایش‌نامه.
 */
suspend fun exportDialogueToPdf(context: Context, dialogueTitle: String, turns: List<Triple<String, String, String>>) {
    val roles = turns.map { (roleTitle, sectionTitle, content) ->
        roleTitle to listOf(SectionEntity(id = 0, roleId = 0, orderIndex = 0, title = sectionTitle, content = content))
    }
    exportPdfInternal(context, dialogueTitle, roles)
}

private suspend fun exportPdfInternal(
    context: Context,
    documentTitle: String,
    roles: List<Pair<String, List<SectionEntity>>>
) {
    val pageWidth = 595 // اندازه تقریبی A4 در نقطه (72dpi)
    val pageHeight = 842
    val margin = 40f
    val contentWidth = (pageWidth - margin * 2).toInt()

    val document = PdfDocument()
    val titlePaint = TextPaint().apply {
        isAntiAlias = true
        textSize = 18f
        textAlign = Paint.Align.RIGHT
    }
    val roleTitlePaint = TextPaint().apply {
        isAntiAlias = true
        textSize = 15f
        isFakeBoldText = true
    }
    val bodyPaint = TextPaint().apply {
        isAntiAlias = true
        textSize = 13f
    }

    var pageNumber = 1
    var page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
    var canvas: Canvas = page.canvas
    var y = margin

    fun newPage() {
        document.finishPage(page)
        pageNumber++
        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        canvas = page.canvas
        y = margin
    }

    /**
     * یک پاراگراف (که ممکن است چند خطی و طولانی‌تر از یک صفحه باشد) را خط‌به‌خط
     * رسم می‌کند و هرجا لازم شد صفحه‌ی تازه باز می‌کند، تا هیچ خطی خارج از
     * کادر صفحه/بریده نشود. StaticLayout کامل فقط برای محاسبه‌ی دقیق مرز هر خط
     * (که به‌خاطر ساختار bidi/فارسی به‌سادگی با شمردن کاراکتر قابل حدس نیست)
     * ساخته می‌شود؛ رسم واقعی خط‌به‌خط انجام می‌گیرد.
     */
    fun drawParagraphPaginated(
        text: String,
        paint: TextPaint,
        lineSpacingExtra: Float = 0f,
        spacingAfter: Float = 0f
    ) {
        if (text.isBlank()) return
        val fullLayout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_OPPOSITE) // راست‌چین
            .setLineSpacing(lineSpacingExtra, 1f)
            .build()

        for (i in 0 until fullLayout.lineCount) {
            val lineTop = fullLayout.getLineTop(i)
            val lineBottom = fullLayout.getLineBottom(i)
            val lineHeight = (lineBottom - lineTop).toFloat()

            // اگر حتی یک خط تنها هم از ارتفاع صفحه بیشتر باشد (تئوریک)، برای جلوگیری
            // از حلقه‌ی بی‌نهایت آن را همینجا -با هر ارتفاعی که هست- رسم می‌کنیم
            if (y + lineHeight > pageHeight - margin && y > margin) newPage()

            val lineStart = fullLayout.getLineStart(i)
            val lineEnd = fullLayout.getLineEnd(i)
            val lineLayout = StaticLayout.Builder
                .obtain(text, lineStart, lineEnd, paint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_OPPOSITE)
                .build()

            canvas.save()
            canvas.translate(margin, y)
            lineLayout.draw(canvas)
            canvas.restore()
            y += lineHeight
        }
        y += spacingAfter
    }

    // عنوان کلی سند (نام نقش یا نام کل تعزیه)
    canvas.drawText(documentTitle, pageWidth - margin, y + 20f, titlePaint)
    y += 40f

    val isMultiRole = roles.size > 1

    for ((roleTitle, sections) in roles) {
        if (isMultiRole) {
            drawParagraphPaginated(roleTitle, roleTitlePaint, spacingAfter = 14f)
        }

        for (section in sections) {
            drawParagraphPaginated(section.title, bodyPaint, spacingAfter = 8f)
            drawParagraphPaginated(section.content, bodyPaint, lineSpacingExtra = 6f, spacingAfter = 24f)
        }
    }

    document.finishPage(page)

    withContext(Dispatchers.IO) {
        val safeTitle = documentTitle.replace(Regex("[^\\p{L}\\p{N}]"), "_")
        val file = File(context.cacheDir, "$safeTitle.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()

        withContext(Dispatchers.Main) {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "ذخیره یا اشتراک‌گذاری PDF"))
        }
    }
}
