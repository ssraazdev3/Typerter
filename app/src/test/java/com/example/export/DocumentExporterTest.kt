package com.example.export

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.ExportFormat
import com.example.data.model.Note
import com.example.data.model.TypewriterFont
import com.example.data.repository.NotesRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DocumentExporterTest {

    @Test
    fun exportMarkdownAndDocx_producesValidFormattedFiles(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val note = Note(
            id = "n1",
            title = "Midnight Study",
            content = "Line one.\nLine two with & <xml> symbols.",
            wordCount = 7L,
            charCount = 38L,
            wordGoal = 3000L,
            fontId = TypewriterFont.SPECIAL_ELITE.id
        )

        val mdFile = File(context.cacheDir, "test_out.md")
        val mdRes = DocumentExporter.exportToUri(context, note, ExportFormat.MARKDOWN, Uri.fromFile(mdFile))
        assertTrue(mdRes.isSuccess)
        val mdText = mdFile.readText()
        assertTrue(mdText.startsWith("# Midnight Study"))
        assertTrue(mdText.contains("Line two with & <xml> symbols."))

        val docxFile = File(context.cacheDir, "test_out.docx")
        val docxRes = DocumentExporter.exportToUri(context, note, ExportFormat.DOCX, Uri.fromFile(docxFile))
        assertTrue(docxRes.isSuccess)
        val entryNames = mutableListOf<String>()
        ZipInputStream(docxFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entryNames.add(entry.name)
                entry = zip.nextEntry
            }
        }
        assertTrue(entryNames.contains("word/document.xml"))
        assertTrue(entryNames.contains("[Content_Types].xml"))
        assertTrue(entryNames.contains("_rels/.rels"))
    }

    @Test
    fun countWords_countsWhitespaceDelimitedTokensAccurately() {
        assertEquals(0L, NotesRepository.countWords("   \n  "))
        assertEquals(6L, NotesRepository.countWords("Once upon a time   there\nwas"))
    }
}
