package br.com.editorstochy

import android.app.Activity
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MainActivity : ComponentActivity() {
    private lateinit var web: WebView
    private var pendingBytes: ByteArray = byteArrayOf()
    private var pendingMime = "text/plain"
    private var pendingName = "document.txt"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webChromeClient = WebChromeClient()
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(Bridge(), "Stochy")
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
    }

    inner class Bridge {
        @JavascriptInterface fun exportFile(name: String, html: String, format: String) {
            val plain = android.text.Html.fromHtml(html, android.text.Html.FROM_HTML_MODE_COMPACT).toString()
            when (format.lowercase()) {
                "pdf" -> { pendingBytes = makePdf(plain); pendingMime = "application/pdf"; pendingName = "$name.pdf" }
                "docx" -> { pendingBytes = makeDocx(plain); pendingMime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"; pendingName = "$name.docx" }
                else -> { pendingBytes = plain.toByteArray(Charsets.UTF_8); pendingMime = "text/plain"; pendingName = "$name.txt" }
            }
            runOnUiThread {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = pendingMime
                    putExtra(Intent.EXTRA_TITLE, pendingName)
                }
                startActivityForResult(intent, 41)
            }
        }
    }

    private fun makePdf(text: String): ByteArray {
        val pdf = PdfDocument()
        val paint = Paint().apply { textSize = 12f }
        val lines = text.split("\n")
        var pageNo = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
        var canvas: Canvas = page.canvas
        var y = 50f
        for (line in lines) {
            if (y > 800f) {
                pdf.finishPage(page); pageNo++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
                canvas = page.canvas; y = 50f
            }
            canvas.drawText(line.take(95), 40f, y, paint)
            y += 20f
        }
        pdf.finishPage(page)
        val out = ByteArrayOutputStream()
        pdf.writeTo(out); pdf.close()
        return out.toByteArray()
    }

    private fun makeDocx(text: String): ByteArray {
        val escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val paragraphs = escaped.split("\n").joinToString("") { "<w:p><w:r><w:t xml:space=\"preserve\">$it</w:t></w:r></w:p>" }
        val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$paragraphs<w:sectPr/></w:body></w:document>"""
        val types = """<?xml version="1.0" encoding="UTF-8"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""
        val rels = """<?xml version="1.0" encoding="UTF-8"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(path: String, value: String) { zip.putNextEntry(ZipEntry(path)); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            entry("[Content_Types].xml", types); entry("_rels/.rels", rels); entry("word/document.xml", document)
        }
        return out.toByteArray()
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 41 && resultCode == Activity.RESULT_OK) {
            val uri: Uri = data?.data ?: return
            contentResolver.openOutputStream(uri)?.use { it.write(pendingBytes) }
        }
    }

    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}
