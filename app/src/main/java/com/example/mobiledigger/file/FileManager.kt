package com.example.mobiledigger.file

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.mobiledigger.model.MusicFile
import com.example.mobiledigger.model.SortAction
import com.example.mobiledigger.util.CrashLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.media.MediaMetadataRetriever
import android.util.Log
import android.provider.DocumentsContract
import android.content.ContentResolver

class FileManager(private val context: Context) {
    
    private var selectedFolder: DocumentFile? = null
    private var destinationFolder: DocumentFile? = null
    private var selectedFolderPath: String? = null  // For direct file access
    private val musicExtensions = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "aif", "aiff")
    private val contentResolver: ContentResolver = context.contentResolver

    
    fun setSelectedFolder(uri: Uri) {
        if (uri.scheme == "file") {
            // Direct file system access
            selectedFolderPath = uri.path
            selectedFolder = null
            com.example.mobiledigger.util.CrashLogger.log("FileManager", "Selected folder (direct): $selectedFolderPath")
        } else {
            // Content URI access (SAF)
            selectedFolderPath = null
            // Persist access to the tree for future sessions
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                com.example.mobiledigger.util.CrashLogger.log("FileManager", "Cannot persist URI permission: ${e.message}")
            }
            
            try {
                selectedFolder = DocumentFile.fromTreeUri(context, uri)
                com.example.mobiledigger.util.CrashLogger.log("FileManager", "Selected folder (SAF): ${selectedFolder?.name ?: "null"}")
            } catch (e: Exception) {
                com.example.mobiledigger.util.CrashLogger.log("FileManager", "Failed to create DocumentFile from URI", e)
                selectedFolder = null
            }
        }
    }

    
    
    
    
    
    fun getSelectedFolder(): DocumentFile? = selectedFolder
    
    fun setDestinationFolder(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            CrashLogger.log("FileManager", "Persistent permissions granted for destination: $uri")
        } catch (e: SecurityException) { 
            CrashLogger.log("FileManager", "Failed to take persistent permissions for destination", e)
        }
        
        destinationFolder = DocumentFile.fromTreeUri(context, uri)
        destinationFolder?.let { folder ->
            if (!folder.exists()) {
                CrashLogger.log("FileManager", "WARNING: Destination folder doesn't exist: $uri")
            } else if (!folder.canWrite()) {
                CrashLogger.log("FileManager", "WARNING: No write permission to destination folder: $uri")
            } else {
                CrashLogger.log("FileManager", "Destination folder set successfully: $uri")
            }
        }
    }
    
    fun getDestinationFolder(): DocumentFile? = destinationFolder
    
    suspend fun ensureSubfolders() = withContext(Dispatchers.IO) {
        destinationFolder?.let { root ->
            ensureDir(root, "Liked")
            ensureDir(root, "Rejected")
            // Backward compatibility: support legacy names
            // (do not remove old folders if present)
        }
    }

    private fun ensureDir(root: DocumentFile, name: String): DocumentFile? {
        val existing = root.findFile(name)
        if (existing != null && existing.isDirectory) return existing
        root.listFiles().firstOrNull { it.isDirectory && (it.name ?: "").equals(name, ignoreCase = true) }?.let { return it }
        return root.createDirectory(name)
    }
    
    suspend fun loadMusicFiles(): List<MusicFile> = withContext(Dispatchers.IO) {
        val musicFiles = mutableListOf<MusicFile>()
        
        try {
            CrashLogger.log("FileManager", "Starting loadMusicFiles")
            
            if (selectedFolderPath != null) {
                // Direct file system access
                val folder = File(selectedFolderPath!!)
                CrashLogger.log("FileManager", "Direct folder access: $selectedFolderPath, exists: ${folder.exists()}, canRead: ${folder.canRead()}")
                if (folder.exists() && folder.canRead()) {
                    val scanned = scanForMusicFilesDirect(folder)
                    musicFiles.addAll(scanned)
                    com.example.mobiledigger.util.CrashLogger.log("FileManager", "Scanned ${musicFiles.size} files from direct path: $selectedFolderPath")
                } else {
                    com.example.mobiledigger.util.CrashLogger.log("FileManager", "Cannot access direct folder: $selectedFolderPath")
                }
            } else {
                // SAF access
                selectedFolder?.let { folder ->
                    CrashLogger.log("FileManager", "SAF folder exists: ${folder.exists()}, canRead: ${folder.canRead()}")
                    if (folder.exists() && folder.canRead()) {
                        val scanned = scanForMusicFiles(folder.uri, true)
                        musicFiles.addAll(scanned)
                        com.example.mobiledigger.util.CrashLogger.log("FileManager", "Scanned ${musicFiles.size} files from SAF folder: ${folder.name}")
                    } else {
                        com.example.mobiledigger.util.CrashLogger.log("FileManager", "Cannot access SAF folder: ${folder.name}")
                    }
                } ?: run {
                    CrashLogger.log("FileManager", "No folder selected (neither direct nor SAF)")
                }
            }
        } catch (e: Exception) {
            com.example.mobiledigger.util.CrashLogger.log("FileManager", "Error loading music files", e)
        }
        
        CrashLogger.log("FileManager", "loadMusicFiles completed with ${musicFiles.size} files")
        musicFiles
    }

    // Read-only listing of a user-picked cloud/local folder without changing current source
    suspend fun listMusicFilesInFolder(uri: Uri): List<MusicFile> = withContext(Dispatchers.IO) {
        val out = mutableListOf<MusicFile>()
        try {
            val root = DocumentFile.fromTreeUri(context, uri)
            if (root != null && root.exists() && root.canRead()) {
                val scanned = scanForMusicFiles(root.uri, false)
                out.addAll(scanned)
            }
        } catch (_: Exception) {}
        out
    }
    
    private suspend fun scanForMusicFiles(
        directoryUri: Uri,
        isSource: Boolean
    ): List<MusicFile> {
        val musicFiles = mutableListOf<MusicFile>()
        Log.d("FileManager", "Starting scanForMusicFiles with extensions: $musicExtensions")
        try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                directoryUri,
                DocumentsContract.getDocumentId(directoryUri)
            )
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE
            )

            contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val displayName = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME))
                    val docId = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                    val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, docId)

                    if (DocumentsContract.Document.MIME_TYPE_DIR == mimeType) {
                        // Recurse into subdirectories
                        try {
                            musicFiles.addAll(scanForMusicFiles(childUri, isSource))
                        } catch (e: Exception) {
                            CrashLogger.log("FileManager", "Failed to scan subfolder $displayName", e)
                        }
                        continue
                    }

                    // Check both file extension and MIME type for audio files
                    val extension = displayName.substringAfterLast('.', "").lowercase()
                    val isAudioByExtension = extension in musicExtensions
                    val isAudioByMimeType = mimeType?.startsWith("audio/") == true
                    val isAiffMimeType = mimeType == "audio/aiff" || mimeType == "audio/x-aiff" || mimeType?.contains("aiff") == true
                    
                    // Reduced logging to prevent performance issues
                    // Only log AIFF files for debugging
                    if ((extension == "aif" || extension == "aiff" || isAiffMimeType) && musicFiles.size % 50 == 0) {
                        CrashLogger.log("FileManager", "Found AIFF file: $displayName")
                    }
                    
                    if (isAudioByExtension || isAudioByMimeType || isAiffMimeType) {
                        try {
                            // Skip duration extraction for now to prevent freezing
                            // Duration will be extracted when the file is actually played
                            val isAiffFile = extension == "aif" || extension == "aiff" || mimeType?.contains("aiff") == true
                            
                            // Get file size efficiently
                            val fileSize = try {
                                val sizeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                                if (sizeColumn >= 0) {
                                    cursor.getLong(sizeColumn)
                                } else {
                                    0L
                                }
                            } catch (e: Exception) { 0L }
                            
                            val basicMusicFile = MusicFile(
                                uri = childUri,
                                name = displayName,
                                duration = 0L, // Will be extracted when played
                                size = fileSize
                            )
                            musicFiles.add(basicMusicFile)
                            
                            // Reduced logging to prevent performance issues
                            if (musicFiles.size % 10 == 0) {
                                CrashLogger.log("FileManager", "Loaded ${musicFiles.size} files so far...")
                            }
                        } catch (e: Exception) {
                            CrashLogger.log("FileManager", "Failed to create MusicFile for $displayName", e)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            CrashLogger.log("FileManager", "Error scanning source folder", e)
        }
        return musicFiles
    }
    
    private suspend fun scanForMusicFilesDirect(directory: File): List<MusicFile> = withContext(Dispatchers.IO) {
        val musicFiles = mutableListOf<MusicFile>()
        
        try {
            if (!directory.exists() || !directory.canRead()) {
                CrashLogger.log("FileManager", "Cannot access directory: ${directory.absolutePath}")
                return@withContext musicFiles
            }
            
            CrashLogger.log("FileManager", "Scanning direct directory: ${directory.absolutePath}")
            
            // Recursively scan the directory
            scanDirectoryRecursive(directory, musicFiles)
            
        } catch (e: Exception) {
            CrashLogger.log("FileManager", "Error scanning direct directory", e)
        }
        
        musicFiles
    }
    
    private fun scanDirectoryRecursive(directory: File, musicFiles: MutableList<MusicFile>) {
        try {
            val files = directory.listFiles()
            if (files == null) {
                CrashLogger.log("FileManager", "Cannot list files in directory: ${directory.absolutePath}")
                return
            }
            
            for (file in files) {
                if (file.isDirectory) {
                    // Recursively scan subdirectories
                    scanDirectoryRecursive(file, musicFiles)
                } else if (file.isFile && isMusicFile(file.name)) {
                    try {
                        val extension = file.name.substringAfterLast('.', "").lowercase()
                        val isAiffFile = extension == "aif" || extension == "aiff"
                        
                        // Create file:// URI for direct access
                        val fileUri = Uri.fromFile(file)
                        
                        val musicFile = MusicFile(
                            uri = fileUri,
                            name = file.name,
                            duration = 0L, // Will be extracted when played
                            size = file.length()
                        )
                        musicFiles.add(musicFile)
                        
                        // Log AIFF files for debugging
                        if (isAiffFile && musicFiles.size % 10 == 0) {
                            CrashLogger.log("FileManager", "Found AIFF file (direct): ${file.name}")
                        }
                        
                    } catch (e: Exception) {
                        CrashLogger.log("FileManager", "Failed to create MusicFile for ${file.name}", e)
                    }
                }
            }
        } catch (e: Exception) {
            CrashLogger.log("FileManager", "Error scanning directory: ${directory.absolutePath}", e)
        }
    }

    private fun isMusicFile(fileName: String?): Boolean {
        if (fileName == null) return false
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return musicExtensions.contains(extension)
    }

    private fun isHiddenOrTrashed(name: String?): Boolean {
        if (name == null) return true
        val lowered = name.lowercase()
        return lowered.contains(".trashed") || lowered.startsWith('.') || lowered.startsWith("._") || lowered.contains("/.trash")
    }
    
    suspend fun sortFile(musicFile: MusicFile, action: SortAction): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val sourceFile = DocumentFile.fromSingleUri(context, musicFile.uri)
                if (sourceFile == null || !sourceFile.exists()) {
                    CrashLogger.log("FileManager", "Source file not found: ${musicFile.uri}")
                    return@withContext false
                }

                val destination = destinationFolder ?: run {
                    CrashLogger.log("FileManager", "Destination folder is not set.")
                    return@withContext false
                }

                if (!destination.exists()) {
                    CrashLogger.log("FileManager", "Destination folder doesn't exist: ${destination.uri}")
                    return@withContext false
                }

                if (!destination.canWrite()) {
                    CrashLogger.log("FileManager", "No write permission to destination folder: ${destination.uri}")
                    return@withContext false
                }

                val subFolderName = if (action == SortAction.LIKE) "Liked" else "Rejected"
                var targetSubFolder = destination.findFile(subFolderName)
                if (targetSubFolder == null) {
                    targetSubFolder = destination.createDirectory(subFolderName)
                    if (targetSubFolder == null) {
                        CrashLogger.log("FileManager", "Failed to create subfolder: $subFolderName in ${destination.uri}")
                        return@withContext false
                    }
                }
                if (!targetSubFolder.canWrite()) {
                    CrashLogger.log("FileManager", "No write permission to subfolder: ${targetSubFolder.uri}")
                    return@withContext false
                }

                val desiredName = sourceFile.name ?: musicFile.name
                val safeName = resolveUniqueName(targetSubFolder, desiredName)
                val target = targetSubFolder.createFile(sourceFile.type ?: "audio/*", safeName)
                    ?: run {
                        CrashLogger.log("FileManager", "Failed to create target file: $safeName")
                        return@withContext false
                    }

                var bytesCopied = 0L
                contentResolver.openInputStream(sourceFile.uri)?.use { input ->
                    contentResolver.openOutputStream(target.uri)?.use { output ->
                        bytesCopied = input.copyTo(output)
                        output.flush()
                    } ?: run {
                        CrashLogger.log("FileManager", "Unable to open output stream for ${target.uri}")
                        target.delete()
                        return@withContext false
                    }
                } ?: run {
                    CrashLogger.log("FileManager", "Unable to open input stream for ${sourceFile.uri}")
                    target.delete()
                    return@withContext false
                }

                if (!sourceFile.delete()) {
                    CrashLogger.log("FileManager", "Copied but failed to delete source: ${sourceFile.uri}")
                }

                CrashLogger.log("FileManager", "Successfully sorted file: ${musicFile.name} to $subFolderName ($bytesCopied bytes)")
                return@withContext true
            } catch (e: Exception) {
                CrashLogger.log("FileManager", "Error sorting file: ${musicFile.name}", e)
                return@withContext false
            }
        }
    }

    private fun resolveUniqueName(parent: DocumentFile, desiredName: String): String {
        // Keep extension, append (1), (2), ... if needed
        val dot = desiredName.lastIndexOf('.')
        val base = if (dot > 0) desiredName.substring(0, dot) else desiredName
        val ext = if (dot > 0) desiredName.substring(dot) else ""
        if (parent.findFile(desiredName) == null) return desiredName
        var index = 1
        while (true) {
            val candidate = "$base ($index)$ext"
            if (parent.findFile(candidate) == null) return candidate
            index += 1
        }
    }

    private fun ensureSubfolder(name: String, root: DocumentFile? = selectedFolder): DocumentFile? {
        val rootFolder = root ?: return null
        val direct = rootFolder.findFile(name)
        if (direct != null && direct.isDirectory) return direct
        // case-insensitive search
        rootFolder.listFiles().firstOrNull { it.isDirectory && (it.name ?: "").equals(name, ignoreCase = true) }?.let { return it }
        return rootFolder.createDirectory(name)
    }

    private fun createOrReplaceFile(parent: DocumentFile, mime: String, displayName: String): DocumentFile? {
        // Find exact or case-insensitive match
        val existing = parent.findFile(displayName)
            ?: parent.listFiles().firstOrNull { it.isFile && (it.name ?: "").equals(displayName, ignoreCase = true) }
        if (existing != null) {
            try { existing.delete() } catch (_: Exception) {}
        }
        return parent.createFile(mime, displayName)
    }

    // Direct move via DocumentsContract.moveDocument is not used here due to inconsistent
    // provider support; copy + delete achieves the same user-visible result reliably.
    
    fun getFolderPath(): String {
        return selectedFolder?.uri?.path ?: "No folder selected"
    }
    
    fun getDestinationPath(): String {
        return destinationFolder?.uri?.path ?: "Not set"
    }

    fun isFolderSelected(): Boolean = selectedFolder != null
    fun isDestinationSelected(): Boolean = destinationFolder != null

    fun getFolderUriString(): String = selectedFolder?.uri?.toString() ?: ""
    fun getDestinationUriString(): String = destinationFolder?.uri?.toString() ?: ""

    fun isDestinationInsideSource(): Boolean {
        val src = getFolderUriString()
        val dst = getDestinationUriString()
        if (src.isEmpty() || dst.isEmpty()) return false
        // Simple containment check; works for SAF tree URIs
        return dst.contains(src)
    }

    suspend fun undoSort(result: com.example.mobiledigger.model.SortResult): MusicFile? = withContext(Dispatchers.IO) {
        try {
            val root = destinationFolder ?: selectedFolder ?: return@withContext null
            val sourceFolderName = when (result.action) {
                com.example.mobiledigger.model.SortAction.LIKE -> "Liked"
                com.example.mobiledigger.model.SortAction.DISLIKE -> "Rejected"
                com.example.mobiledigger.model.SortAction.SKIP -> return@withContext null
            }
            val sourceFolder = root.findFile(sourceFolderName) ?: return@withContext null
            val movedName = result.musicFile.name
            val sourceFile = sourceFolder.findFile(movedName) ?: return@withContext null
            val destRoot = selectedFolder ?: return@withContext null

            // Resolve name collision
            val finalName = if (destRoot.findFile(movedName) == null) movedName else "$movedName (restored)"
            val targetFile = destRoot.createFile(sourceFile.type ?: "audio/*", finalName) ?: return@withContext null

            contentResolver.openInputStream(sourceFile.uri)?.use { input ->
                contentResolver.openOutputStream(targetFile.uri)?.use { output ->
                    input.copyTo(output)
                }
            }
            sourceFile.delete()

            val fileSize = try {
                context.contentResolver.openAssetFileDescriptor(targetFile.uri, "r")?.use { it.length } ?: 0L
            } catch (e: Exception) { 0L }
            
            MusicFile(
                uri = targetFile.uri,
                name = targetFile.name ?: finalName,
                duration = getDuration(targetFile.uri),
                size = fileSize,
                
            )
        } catch (e: Exception) {
            CrashLogger.log("FileManager", "Error undoing sort", e)
            null
        }
    }
    
    private fun getDuration(uri: Uri): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            val durationMs = duration?.toLong() ?: 0L
            if (durationMs == 0L) {
                CrashLogger.log("FileManager", "Could not extract duration for $uri - this might be an unsupported format")
            }
            durationMs
        } catch (e: Exception) {
            CrashLogger.log("FileManager", "Error getting duration for $uri", e)
            0L
        }
    }
    
    // Delete all files from "Rejected" folder
    suspend fun deleteRejectedFiles(): Boolean = withContext(Dispatchers.IO) {
        try {
            val destination = destinationFolder ?: return@withContext false
            val rejectedFolder = destination.findFile("Rejected")
                ?: destination.findFile("I Don't Dig")
                ?: return@withContext false
            if (!rejectedFolder.isDirectory) return@withContext false
            val files = rejectedFolder.listFiles().filter { it.isFile }
            var deletedCount = 0
            for (file in files) {
                try { if (file.delete()) deletedCount++ } catch (e: Exception) {
                    CrashLogger.log("FileManager", "Failed to delete file: ${file.name}", e)
                }
            }
            CrashLogger.log("FileManager", "Deleted $deletedCount rejected files")
            true
        } catch (e: Exception) {
            CrashLogger.log("FileManager", "Error deleting rejected files", e)
            false
        }
    }
    
    // Create liked files text file in cache for sharing
    suspend fun createLikedFilesTxt(): java.io.File? = withContext(Dispatchers.IO) {
        try {
            val destination = destinationFolder ?: return@withContext null
            val likedFolder = destination.findFile("Liked") ?: destination.findFile("I DIG") ?: return@withContext null
            
            if (!likedFolder.isDirectory) return@withContext null
            
            val files = likedFolder.listFiles().filter { it.isFile }
            if (files.isEmpty()) return@withContext null
            
            // Create text file in cache directory
            val cacheDir = java.io.File(context.cacheDir, "shared_txt")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            
            val dateStr = java.text.SimpleDateFormat("dd-MM-yy", java.util.Locale.getDefault()).format(java.util.Date())
            val txtFile = java.io.File(cacheDir, "Liked_Songs_${dateStr}.txt")
            
            java.io.FileOutputStream(txtFile).use { output ->
                val writer = output.bufferedWriter()
                writer.write("Liked Songs List - ${java.time.LocalDateTime.now()}\n")
                writer.write("=".repeat(50) + "\n\n")
                
                files.forEachIndexed { index, file ->
                    writer.write("${index + 1}. ${file.name}\n")
                }
                
                writer.write("\nTotal: ${files.size} songs\n")
                writer.write("Generated by MobileDigger\n")
                writer.flush()
            }
            
            CrashLogger.log("FileManager", "Created TXT file with ${files.size} liked files: ${txtFile.absolutePath}")
            txtFile
        } catch (e: Exception) {
            CrashLogger.log("FileManager", "Error creating liked files TXT", e)
            null
        }
    }

    suspend fun zipIDigFolder(context: Context): File? = withContext(Dispatchers.IO) {
        try {
            val destination = destinationFolder ?: return@withContext null
            val likedFolder = destination.findFile("Liked") ?: destination.findFile("I DIG") ?: return@withContext null
            if (!likedFolder.isDirectory) return@withContext null

            val cacheRoot = File(context.cacheDir, "idig_zip")
            if (!cacheRoot.exists()) cacheRoot.mkdirs()
            val outZip = File(cacheRoot, "LIKED_${System.currentTimeMillis()}.zip")

            java.util.zip.ZipOutputStream(java.io.FileOutputStream(outZip)).use { zipOut ->
                likedFolder.listFiles().filter { it.isFile }.forEach { doc ->
                    val entryName = doc.name ?: "track"
                    zipOut.putNextEntry(java.util.zip.ZipEntry(entryName))
                    context.contentResolver.openInputStream(doc.uri)?.use { input ->
                        input.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                }
            }
            outZip
        } catch (e: Exception) {
            CrashLogger.log("FileManager", "zipIDigFolder failed", e)
            null
        }
    }
    
    fun getLikedFolderUri(): Uri? {
        return destinationFolder?.findFile("Liked")?.uri
    }
    
    fun getRejectedFolderUri(): Uri? {
        return destinationFolder?.findFile("Rejected")?.uri
    }
}
