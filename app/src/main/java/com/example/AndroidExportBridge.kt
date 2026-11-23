package com.example

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

class AndroidExportBridge(private val activity: Activity) {

    @JavascriptInterface
    fun compartirExcelBase64(base64Data: String, fileName: String) {
        try {
            val cleanBase64 = base64Data.substringAfter(",", base64Data).trim()
            if (cleanBase64.isEmpty()) {
                showExportError()
                return
            }

            val fileBytes = Base64.decode(cleanBase64, Base64.DEFAULT)
            val safeName = sanitizeFileName(fileName)

            val exportsDir = File(activity.cacheDir, EXPORTS_DIR_NAME).apply {
                if (!exists()) mkdirs()
            }
            val outputFile = File(exportsDir, safeName)
            outputFile.writeBytes(fileBytes)

            val authority = "${activity.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(activity, authority, outputFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = XLSX_MIME_TYPE
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, safeName)
                clipData = ClipData.newUri(activity.contentResolver, safeName, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooserIntent = Intent.createChooser(
                shareIntent,
                activity.getString(R.string.share_excel_chooser_title)
            )

            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) {
                    activity.startActivity(chooserIntent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing Excel file from Base64", e)
            showExportError()
        }
    }

    private fun sanitizeFileName(rawName: String): String {
        val baseName = File(rawName).name
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .ifBlank { DEFAULT_FILE_NAME }
        return if (baseName.endsWith(".xlsx", ignoreCase = true)) {
            baseName
        } else {
            "$baseName.xlsx"
        }
    }

    private fun showExportError() {
        activity.runOnUiThread {
            if (!activity.isFinishing && !activity.isDestroyed) {
                Toast.makeText(
                    activity,
                    R.string.error_export_excel,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    companion object {
        private const val TAG = "AndroidExportBridge"
        private const val EXPORTS_DIR_NAME = "exports"
        private const val DEFAULT_FILE_NAME = "BCP_Caja_y_Cierre.xlsx"
        private const val XLSX_MIME_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    }
}
