package com.snishaper.android.webview

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

internal object FileExporter {

    private const val AUTHORITY_SUFFIX = ".files"

    private val UNSAFE = Regex("[^A-Za-z0-9._-]")

    fun saveToCache(context: Context, name: String, bytes: ByteArray): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = name.replace(UNSAFE, "_").ifBlank { "snishaper-export.txt" }
        val file = File(dir, safeName)
        file.writeBytes(bytes)
        return file
    }

    fun share(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun sharePath(context: Context, path: String, mime: String): Boolean {
        val file = File(path)
        if (!file.isFile) return false
        return runCatching { share(context, file, mime) }.isSuccess
    }

    fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "json" -> "application/json"
        "zip" -> "application/zip"
        "txt", "log" -> "text/plain"
        "crt", "pem" -> "application/x-x509-ca-cert"
        else -> "application/octet-stream"
    }
}
