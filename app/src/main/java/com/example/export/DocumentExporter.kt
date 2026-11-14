package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.model.ExportFormat
import com.example.data.model.Note
import com.example.data.model.TypewriterFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DocumentExporter {

    fun suggestedFileName(note: Note, format: ExportFormat): String {
        val base = note.title
            .ifBlank { "Untitled_Typewriter_Note" }
            .replace(Regex("[^a-zA-Z0-9._\\- ]"), "")
            .trim()
            .replace(Regex("\\s+"), "_")
            .take(48)
            .ifBlank { "Typerter_Note" }
        return "$base.${format.extension}"
    }

    suspend fun exportToUri(
        context: Context,
        note: Note,
        format: ExportFormat,
        uri: Uri
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                writeFormatted(context, note, format, out)
            } ?: error("Could not open destination output stream.")
        }
    }

    suspend fun createShareIntent(
        context: Context,
        note: Note,
        format: ExportFormat
    ): Result<Intent> = withContext(Dispatchers.IO) {
        runCatching {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportDir, suggestedFileName(note, format))
            file.outputStream().use { out ->
                writeFormatted(context, note, format, out)
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            Intent(Intent.ACTION_SEND).apply {
                type = format.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, note.title.ifBlank { "Typerter Note" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }

    private fun writeFormatted(
        context: Context,
        note: Note,
        format: ExportFormat,
        out: OutputStream
    ) {
        when (format) {
            ExportFormat.MARKDOWN -> writeMarkdown(note, out)
            ExportFormat.TXT -> writePlainText(note, out)
            ExportFormat.PDF -> writePdf(context, note, out)
            ExportFormat.DOCX -> writeDocx(note, out)
        }
    }

    private fun writeMarkdown(note: Note, out: OutputStream) {
        val title = note.title.ifBlank { "Untitled Note" }
        val md = buildString {
            append("# ").append(title).append("\n\n")
            append(note.content)
            if (!note.content.endsWith("\n")) append("\n")
        }
        out.write(md.toByteArray(StandardCharsets.UTF_8))
        out.flush()
    }

    private fun writePlainText(note: Note, out: OutputStream) {
        val text = buildString {
            if (note.title.isNotBlank()) {
                append(note.title).append("\n")
                append("=".repeat(note.title.length.coerceAtMost(40))).append("\n\n")
            }
            append(note.content)
        }
        out.write(text.toByteArray(StandardCharsets.UTF_8))
        out.flush()
    }

    private fun writePdf(context: Context, note: Note, out: OutputStream) {
        val pageWidth = 595 // A4 points (72 dpi)
        val pageHeight = 842
        val marginX = 56
        val marginTop = 64
        val marginBottom = 56
        val contentWidth = pageWidth - marginX * 2
        val maxBodyHeight = pageHeight - marginTop - marginBottom

        val typeface = resolveAndroidTypeface(context, TypewriterFont.fromId(note.fontId))

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(24, 22, 20)
            textSize = 18f
            this.typeface = Typeface.create(typeface, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(34, 32, 30)
            textSize = 11.5f
            this.typeface = typeface
        }
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(125, 120, 112)
            textSize = 8.5f
            this.typeface = Typeface.MONOSPACE
        }

        val titleText = note.title.ifBlank { "Untitled Note" }
        val titleLayout = StaticLayout.Builder
            .obtain(titleText, 0, titleText.length, titlePaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(4f, 1.15f)
            .build()

        val bodyText = note.content.ifEmpty { " " }
        val bodyLayout = StaticLayout.Builder
            .obtain(bodyText, 0, bodyText.length, bodyPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(3f, 1.4f)
            .build()

        val document = PdfDocument()
        var currentLine = 0
        var pageNum = 1

        while (currentLine < bodyLayout.lineCount || pageNum == 1) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas
            canvas.drawColor(Color.rgb(252, 250, 246))

            var yCursor = marginTop.toFloat()
            if (pageNum == 1) {
                canvas.save()
                canvas.translate(marginX.toFloat(), yCursor)
                titleLayout.draw(canvas)
                canvas.restore()
                yCursor += titleLayout.height + 16f

                val rulePaint = Paint().apply {
                    color = Color.rgb(210, 204, 194)
                    strokeWidth = 1f
                }
                canvas.drawLine(
                    marginX.toFloat(),
                    yCursor,
                    (pageWidth - marginX).toFloat(),
                    yCursor,
                    rulePaint
                )
                yCursor += 18f
            }

            val availableHeight = (pageHeight - marginBottom - yCursor).toInt().coerceAtLeast(40)
            val startLineTop = if (currentLine < bodyLayout.lineCount) bodyLayout.getLineTop(currentLine) else 0
            var endLine = currentLine
            while (endLine < bodyLayout.lineCount) {
                val lineBottom = bodyLayout.getLineBottom(endLine) - startLineTop
                if (lineBottom > availableHeight && endLine > currentLine) break
                endLine++
            }

            if (currentLine < endLine) {
                canvas.save()
                canvas.clipRect(
                    marginX.toFloat(),
                    yCursor,
                    (pageWidth - marginX).toFloat(),
                    (pageHeight - marginBottom).toFloat()
                )
                canvas.translate(marginX.toFloat(), yCursor - startLineTop)
                bodyLayout.draw(canvas)
                canvas.restore()
            }

            val footerText = "$titleText  ·  Page $pageNum  ·  ${note.wordCount} words"
            canvas.drawText(footerText, marginX.toFloat(), (pageHeight - 28).toFloat(), footerPaint)

            document.finishPage(page)
            currentLine = endLine
            pageNum++
            if (currentLine >= bodyLayout.lineCount) break
        }

        document.writeTo(out)
        document.close()
        out.flush()
    }

    private fun writeDocx(note: Note, out: OutputStream) {
        val title = escapeXml(note.title.ifBlank { "Untitled Note" })
        val lines = note.content.split("\n")

        val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
  <Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>
</Types>"""

        val relsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        val stylesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
    <w:rPr>
      <w:rFonts w:ascii="Courier New" w:hAnsi="Courier New"/>
      <w:sz w:val="22"/>
    </w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading1">
    <w:name w:val="heading 1"/>
    <w:basedOn w:val="Normal"/>
    <w:pPr><w:spacing w:after="240"/></w:pPr>
    <w:rPr>
      <w:rFonts w:ascii="Courier New" w:hAnsi="Courier New"/>
      <w:b/>
      <w:sz w:val="36"/>
    </w:rPr>
  </w:style>
</w:styles>"""

        val documentXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
            append("<w:body>")
            append("""<w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t xml:space="preserve">$title</w:t></w:r></w:p>""")
            for (line in lines) {
                val escaped = escapeXml(line)
                append("""<w:p><w:r><w:t xml:space="preserve">$escaped</w:t></w:r></w:p>""")
            }
            append("""<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr>""")
            append("</w:body></w:document>")
        }

        ZipOutputStream(out).use { zip ->
            writeZipEntry(zip, "[Content_Types].xml", contentTypesXml)
            writeZipEntry(zip, "_rels/.rels", relsXml)
            writeZipEntry(zip, "word/styles.xml", stylesXml)
            writeZipEntry(zip, "word/document.xml", documentXml)
        }
    }

    private fun writeZipEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun escapeXml(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                '\t' -> sb.append("    ")
                else -> if (ch.code >= 0x20 || ch == '\n' || ch == '\r') sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun resolveAndroidTypeface(context: Context, font: TypewriterFont): Typeface {
        val resId = when (font) {
            TypewriterFont.SPECIAL_ELITE -> R.font.special_elite
            TypewriterFont.COURIER_PRIME -> R.font.courier_prime
            TypewriterFont.IBM_PLEX_MONO -> R.font.ibm_plex_mono
            TypewriterFont.SPACE_MONO -> R.font.space_mono
            TypewriterFont.NEWSREADER -> R.font.newsreader
            TypewriterFont.INTER -> R.font.inter
        }
        return try {
            ResourcesCompat.getFont(context, resId) ?: Typeface.MONOSPACE
        } catch (_: Exception) {
            Typeface.MONOSPACE
        }
    }
}
