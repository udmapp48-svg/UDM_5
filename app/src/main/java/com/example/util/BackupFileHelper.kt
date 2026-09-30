package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupFileHelper {

    private const val TAG = "BackupFileHelper"
    const val BACKUP_SUBFOLDER = "UDM MOBILE REPAIR/Backups"
    const val FILE_EXTENSION = ".udmbackup"

    data class BackupFileInfo(
        val file: File,
        val name: String,
        val fullPath: String,
        val folderDisplayName: String,
        val sizeBytes: Long,
        val formattedSize: String,
        val lastModified: Long,
        val formattedDate: String,
        val summary: BackupSummary?
    )

    data class BackupFileResult(
        val success: Boolean,
        val file: File?,
        val fileName: String,
        val folderPath: String,
        val sizeBytes: Long,
        val formattedSize: String,
        val summary: BackupSummary?,
        val errorMessage: String? = null
    )

    fun generateBackupFileName(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)
        val dateStr = sdf.format(Date())
        return "UDM_Backup_${dateStr}$FILE_EXTENSION"
    }

    fun getPrimaryBackupDirectory(context: Context): File {
        // 1. Try public Documents/UDM MOBILE REPAIR/Backups/
        try {
            val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val dir = File(docs, BACKUP_SUBFOLDER)
            if (dir.exists() || dir.mkdirs()) {
                return dir
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not access Documents directory", e)
        }

        // 2. Try public Downloads/UDM MOBILE REPAIR/Backups/
        try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val dir = File(downloads, BACKUP_SUBFOLDER)
            if (dir.exists() || dir.mkdirs()) {
                return dir
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not access Downloads directory", e)
        }

        // 3. Fallback to app external files dir
        val appExt = File(context.getExternalFilesDir(null), BACKUP_SUBFOLDER)
        appExt.mkdirs()
        return appExt
    }

    fun getSearchDirectories(context: Context): List<File> {
        val dirs = mutableListOf<File>()

        try {
            val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            dirs.add(File(docs, BACKUP_SUBFOLDER))
        } catch (_: Exception) {}

        try {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dirs.add(File(downloads, BACKUP_SUBFOLDER))
        } catch (_: Exception) {}

        try {
            dirs.add(File(context.getExternalFilesDir(null), BACKUP_SUBFOLDER))
        } catch (_: Exception) {}

        return dirs
    }

    fun saveBackupToFile(context: Context, backupJson: String): BackupFileResult {
        return try {
            val baseFileName = generateBackupFileName()
            val primaryDir = getPrimaryBackupDirectory(context)
            if (!primaryDir.exists()) {
                primaryDir.mkdirs()
            }

            var targetFile = File(primaryDir, baseFileName)
            var counter = 1
            while (targetFile.exists()) {
                val nameWithoutExt = baseFileName.removeSuffix(FILE_EXTENSION)
                targetFile = File(primaryDir, "${nameWithoutExt}_$counter$FILE_EXTENSION")
                counter++
            }

            // Write the primary backup file
            targetFile.writeText(backupJson, Charsets.UTF_8)

            if (!targetFile.exists() || targetFile.length() == 0L) {
                throw IOException("Backup file write failed: file is missing or 0 bytes")
            }

            // Also save a safeguard mirror copy in app external files directory if primary was public
            try {
                val appExtDir = File(context.getExternalFilesDir(null), BACKUP_SUBFOLDER)
                if (appExtDir.absolutePath != primaryDir.absolutePath) {
                    appExtDir.mkdirs()
                    val mirrorFile = File(appExtDir, targetFile.name)
                    mirrorFile.writeText(backupJson, Charsets.UTF_8)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Mirror backup creation skipped", e)
            }

            // Force Android MediaScanner to index the file so file managers see it immediately
            notifyMediaScanner(context, targetFile)

            // Parse metadata summary for immediate feedback
            val root = try { JSONObject(backupJson) } catch (_: Exception) { JSONObject() }
            val data = BackupManager.parseFromJson(backupJson)
            val summary = BackupManager.generateSummary(root, data)
            val formattedSize = formatFileSize(targetFile.length())

            BackupFileResult(
                success = true,
                file = targetFile,
                fileName = targetFile.name,
                folderPath = primaryDir.absolutePath,
                sizeBytes = targetFile.length(),
                formattedSize = formattedSize,
                summary = summary
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save backup file", e)
            BackupFileResult(
                success = false,
                file = null,
                fileName = "",
                folderPath = "",
                sizeBytes = 0L,
                formattedSize = "0 B",
                summary = null,
                errorMessage = e.localizedMessage ?: "Failed to write backup file"
            )
        }
    }

    private fun notifyMediaScanner(context: Context, file: File) {
        try {
            MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(file.absolutePath),
                arrayOf("application/octet-stream")
            ) { path, uri ->
                Log.d(TAG, "MediaScanner indexed $path -> $uri")
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaScanner notification failed", e)
        }
    }

    fun getAvailableBackupFiles(context: Context): List<BackupFileInfo> {
        val list = mutableListOf<BackupFileInfo>()
        val seenNames = mutableSetOf<String>()

        val searchDirs = getSearchDirectories(context)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        for (dir in searchDirs) {
            if (dir.exists() && dir.isDirectory) {
                val files = dir.listFiles { _, name ->
                    name.endsWith(FILE_EXTENSION, ignoreCase = true) || name.endsWith(".json", ignoreCase = true)
                } ?: emptyArray()

                for (file in files) {
                    if (file.isFile && file.length() > 0 && !seenNames.contains(file.name)) {
                        seenNames.add(file.name)

                        var summary: BackupSummary? = null
                        try {
                            val content = file.readText(Charsets.UTF_8)
                            val validation = BackupManager.validateJson(content)
                            if (validation is BackupValidationResult.Valid) {
                                summary = validation.summary
                            }
                        } catch (_: Exception) {}

                        val folderDisplay = if (file.absolutePath.contains("Documents")) {
                            "Documents/UDM MOBILE REPAIR/Backups"
                        } else if (file.absolutePath.contains("Download")) {
                            "Downloads/UDM MOBILE REPAIR/Backups"
                        } else {
                            "UDM MOBILE REPAIR/Backups"
                        }

                        list.add(
                            BackupFileInfo(
                                file = file,
                                name = file.name,
                                fullPath = file.absolutePath,
                                folderDisplayName = folderDisplay,
                                sizeBytes = file.length(),
                                formattedSize = formatFileSize(file.length()),
                                lastModified = file.lastModified(),
                                formattedDate = dateFormat.format(Date(file.lastModified())),
                                summary = summary
                            )
                        )
                    }
                }
            }
        }

        // Sort descending by last modified
        return list.sortedByDescending { it.lastModified }
    }

    fun readFromUri(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } ?: throw IOException("Could not read backup file from storage")
    }

    fun writeToUri(context: Context, uri: Uri, content: String): Long {
        return context.contentResolver.openOutputStream(uri)?.use { stream ->
            val bytes = content.toByteArray(Charsets.UTF_8)
            stream.write(bytes)
            stream.flush()
            bytes.size.toLong()
        } ?: throw IOException("Could not write backup to selected destination")
    }

    fun shareBackupFile(context: Context, file: File) {
        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "UDM Mobile Repair Backup: ${file.name}")
                putExtra(Intent.EXTRA_TEXT, "UDM Mobile Repair backup file: ${file.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Backup File (.udmbackup)")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share backup file", e)
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.1f MB", mb)
    }
}
