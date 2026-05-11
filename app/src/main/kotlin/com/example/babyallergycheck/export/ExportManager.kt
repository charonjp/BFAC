package com.example.babyallergycheck.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.babyallergycheck.data.BabyMasters
import com.example.babyallergycheck.data.ChildEntity
import com.example.babyallergycheck.data.ExportFoodRow
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object ExportManager {
    private val exportDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    fun exportCsv(
        context: Context,
        child: ChildEntity,
        rows: List<ExportFoodRow>,
    ): Uri {
        val exportDate = LocalDate.now().format(exportDateFormatter)
        val fileName = "baby_allergy_${sanitize(child.name)}_$exportDate.csv"
        val body = buildString {
            append("\uFEFF")
            appendLine("子供番号,${csv(child.number)}")
            appendLine("子供名,${csv(child.name)}")
            appendLine("出力日,${csv(exportDate)}")
            appendLine()
            appendLine("期,分類,食材コード,食材,状況,1回目日付,1回目反応,1回目メモ,2回目日付,2回目反応,2回目メモ")
            rows.forEach { row ->
                appendLine(
                    listOf(
                        BabyMasters.phaseLabel(row.phaseCode),
                        BabyMasters.categoryLabel(row.categoryCode),
                        row.code,
                        row.name,
                        statusLabel(row.firstDate, row.secondDate),
                        row.firstDate.orEmpty(),
                        reactionLabel(row.firstReaction),
                        row.firstMemo.orEmpty(),
                        row.secondDate.orEmpty(),
                        reactionLabel(row.secondReaction),
                        row.secondMemo.orEmpty(),
                    ).joinToString(",") { csv(it) },
                )
            }
        }

        return context.writeToDownloads(fileName, "text/csv") { stream ->
            stream.write(body.toByteArray(Charsets.UTF_8))
        }
    }

    fun exportPdf(
        context: Context,
        child: ChildEntity,
        rows: List<ExportFoodRow>,
    ): Uri {
        val exportDate = LocalDate.now().format(exportDateFormatter)
        val fileName = "baby_allergy_${sanitize(child.name)}_$exportDate.pdf"
        val document = PdfDocument()
        val state = PdfState(document)

        try {
            state.startPage()
            state.drawTitle("離乳食アレルギーチェック")
            state.drawText("子供番号: ${child.number}    子供名: ${child.name}    出力日: $exportDate", 12f, bold = false)
            state.addGap(10f)

            BabyMasters.phases.forEach { phase ->
                val phaseRows = rows.filter { it.phaseCode == phase.code }
                if (phaseRows.isEmpty()) return@forEach
                state.ensureSpace(52f)
                state.drawText("${phase.ageLabel}（${phase.label}）", 14f, bold = true)
                state.drawHeader()
                phaseRows.forEach { row ->
                    state.ensureSpace(26f)
                    state.drawRow(row)
                }
                state.addGap(10f)
            }

            state.finish()
            return context.writeToDownloads(fileName, "application/pdf") { stream ->
                document.writeTo(stream)
            }
        } finally {
            document.close()
        }
    }

    private fun csv(value: String): String =
        "\"" + value.replace("\"", "\"\"").replace("\n", " ") + "\""

    private fun reactionLabel(value: Boolean?): String =
        if (value == true) "反応あり" else ""

    private fun statusLabel(firstDate: String?, secondDate: String?): String =
        when {
            firstDate.isNullOrBlank() && secondDate.isNullOrBlank() -> "未実施"
            !firstDate.isNullOrBlank() && secondDate.isNullOrBlank() -> "1回目済"
            firstDate.isNullOrBlank() && !secondDate.isNullOrBlank() -> "2回目済"
            else -> "完了"
        }

    private fun sanitize(value: String): String =
        value.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "child" }
}

private class PdfState(
    private val document: PdfDocument,
) {
    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 32f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(33, 33, 33)
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    private var pageNumber = 0
    private var currentPage: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var y = margin

    fun startPage() {
        pageNumber += 1
        currentPage = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        canvas = currentPage!!.canvas
        y = margin
    }

    fun finish() {
        currentPage?.let { document.finishPage(it) }
        currentPage = null
        canvas = null
    }

    fun ensureSpace(height: Float) {
        if (y + height <= pageHeight - margin) return
        finish()
        startPage()
    }

    fun addGap(height: Float) {
        y += height
    }

    fun drawTitle(text: String) {
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        canvas!!.drawText(text, margin, y, paint)
        y += 30f
    }

    fun drawText(text: String, size: Float, bold: Boolean) {
        paint.textSize = size
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        canvas!!.drawText(text, margin, y, paint)
        y += size + 10f
    }

    fun drawHeader() {
        val labels = listOf("分類", "食材", "1回目", "1回目メモ", "2回目", "2回目メモ")
        val xs = listOf(margin, 88f, 188f, 258f, 382f, 452f)
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        paint.color = Color.rgb(80, 80, 80)
        labels.forEachIndexed { index, label -> canvas!!.drawText(label, xs[index], y, paint) }
        paint.color = Color.rgb(33, 33, 33)
        y += 16f
    }

    fun drawRow(row: ExportFoodRow) {
        val xs = listOf(margin, 88f, 188f, 258f, 382f, 452f)
        val values = listOf(
            BabyMasters.categoryLabel(row.categoryCode),
            row.name,
            row.firstDate.orEmpty(),
            memoText(row.firstMemo, row.firstReaction),
            row.secondDate.orEmpty(),
            memoText(row.secondMemo, row.secondReaction),
        )
        paint.textSize = 8.5f
        paint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        values.forEachIndexed { index, value ->
            canvas!!.drawText(value.ellipsize(maxChars(index)), xs[index], y, paint)
        }
        y += 20f
    }

    private fun maxChars(index: Int): Int =
        when (index) {
            0 -> 7
            1 -> 14
            2, 4 -> 10
            else -> 18
        }

    private fun memoText(memo: String?, reaction: Boolean?): String {
        val cleanMemo = memo.orEmpty().replace("\n", " ")
        return when {
            reaction == true && cleanMemo.isNotBlank() -> "反応あり: $cleanMemo"
            reaction == true -> "反応あり"
            else -> cleanMemo
        }
    }

    private fun String.ellipsize(maxChars: Int): String =
        if (length <= maxChars) this else take(maxChars - 1) + "…"
}

private fun Context.writeToDownloads(
    fileName: String,
    mimeType: String,
    writer: (OutputStream) -> Unit,
): Uri {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("保存先を作成できませんでした")
        contentResolver.openOutputStream(uri)?.use(writer) ?: error("保存先を開けませんでした")
        return uri
    }

    val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, fileName)
    FileOutputStream(file).use(writer)
    return Uri.fromFile(file)
}
