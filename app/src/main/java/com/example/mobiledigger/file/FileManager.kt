package com.example.mobiledigger.file

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.mobiledigger.model.MusicFile
import com.example.mobiledigger.model.SortAction
import com.example.mobiledigger.util.CrashLogger
import com.example.mobiledigger.util.ResourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import android.util.Log
import android.provider.DocumentsContract
import android.content.ContentResolver

class FileManager(private val context: Context) {
    init { com.example.mobiledigger.util.SortHistory.init(context) }

    
    private var selectedFolder: DocumentFile? = null
    private var destinationFolder: DocumentFile? = null
    private var selectedFolderPath: String? = null  // For direct file access
    private val musicExtensions = setOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "aif", "aiff")
    private val contentResolver: ContentResolver = context.contentResolver
    private val preferencesManager = PreferencesManager(context)

    
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

    
    
    
    
    
    fun clearSelectedFolder() {
        selectedFolder = null
        selectedFolderPath = null
        CrashLogger.log("FileManager", "Cleared selected folder")
    }
    
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
        synchronized(scanArchives) { scanArchives.clear() }
        
        try {
            val mode = preferencesManager.getSourceMode()
            CrashLogger.log("FileManager", "Starting loadMusicFiles (load from: $mode)")
            
            if (mode == "NETWORK") {
                CrashLogger.log("FileManager", "Phone folder skipped: loading the network drive only")
            } else if (selectedFolderPath != null) {
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

            // Active network drive (SMB / WebDAV / MobileDigger server) — additive with local files
            if (mode != "PHONE") try {
                musicFiles.addAll(loadActiveDriveFiles())
            } catch (e: Exception) {
                CrashLogger.log("FileManager", "Network drive load failed", e)
                lastDriveError = e.message
            }
        } catch (e: Exception) {
            com.example.mobiledigger.util.CrashLogger.log("FileManager", "Error loading music files", e)
        }
        
        lastArchives = synchronized(scanArchives) { scanArchives.toList() }
        CrashLogger.log("FileManager", "loadMusicFiles completed with ${musicFiles.size} files, ${lastArchives.size} archives")
        musicFiles
    }

    /** Archives (.zip/.rar/.7z) found by the last loadMusicFiles(). */
    @Volatile var lastArchives: List<com.example.mobiledigger.file.archives.ArchiveItem> = emptyList()
    private val scanArchives = mutableListOf<com.example.mobiledigger.file.archives.ArchiveItem>()

    /** Set by the ViewModel: plain-language notes about network-drive actions (e.g. a failed delete). */
    @Volatile var onDriveNotice: ((String) -> Unit)? = null

    /** Last network-drive error, for the UI (null when the last load worked). */
    @Volatile var lastDriveError: String? = null

    /**
     * Tracks (and nested archives) inside ONE freshly unpacked phone folder, for a partial
     * To Do update instead of a full rescan. [subfolderPath] = its path relative to the source root.
     */
    suspend fun scanSingleFolder(
        folderDocUri: Uri, subfolderPath: String
    ): Pair<List<MusicFile>, List<com.example.mobiledigger.file.archives.ArchiveItem>> = withContext(Dispatchers.IO) {
        val before = synchronized(scanArchives) { scanArchives.size }
        val tracks = scanForMusicFiles(folderDocUri, true, subfolderPath)
        val nested = synchronized(scanArchives) {
            val added = scanArchives.subList(before, scanArchives.size).toList()
            repeat(added.size) { scanArchives.removeAt(scanArchives.size - 1) }
            added
        }
        tracks to nested
    }

    /** Same as [scanSingleFolder] for a folder on a saved network drive. */
    fun listDriveFolder(
        driveId: String, path: String
    ): Pair<List<MusicFile>, List<com.example.mobiledigger.file.archives.ArchiveItem>> {
        val drive = com.example.mobiledigger.file.drives.DriveStore(context).get(driveId) ?: return emptyList<MusicFile>() to emptyList()
        com.example.mobiledigger.file.drives.DriveProxy.ensureStarted(context)
        val client = com.example.mobiledigger.file.drives.DriveClients.create(drive)
        try {
            val archives = mutableListOf<Pair<String, com.example.mobiledigger.file.drives.RemoteEntry>>()
            val found = com.example.mobiledigger.file.drives.DriveClients.listAudioRecursive(client, path, archives = archives)
            val tracks = found.map { (folder, e) ->
                MusicFile(
                    uri = com.example.mobiledigger.file.drives.DriveProxy.uriFor(drive.id, e.path),
                    name = e.name, size = e.size, duration = 0L, subfolder = folder.takeIf { it.isNotEmpty() }
                )
            }
            val nested = archives.map { (folder, e) ->
                com.example.mobiledigger.file.archives.ArchiveItem(
                    uri = com.example.mobiledigger.file.drives.DriveProxy.uriFor(drive.id, e.path),
                    name = e.name, size = e.size, folder = folder.takeIf { it.isNotEmpty() },
                    driveId = drive.id, drivePath = e.path, driveFolder = folder
                )
            }
            return tracks to nested
        } finally {
            runCatching { client.close() }
        }
    }

    /** Lists the active drive's folder (recursively) as MusicFiles served by the local DriveProxy. */
    fun loadActiveDriveFiles(): List<MusicFile> {
        lastDriveError = null
        val store = com.example.mobiledigger.file.drives.DriveStore(context)
        val drive = store.active() ?: return emptyList()
        com.example.mobiledigger.file.drives.DriveProxy.ensureStarted(context)
        com.example.mobiledigger.file.drives.DriveProxy.invalidate(drive.id)
        val client = com.example.mobiledigger.file.drives.DriveClients.create(drive)
        try {
            val archives = mutableListOf<Pair<String, com.example.mobiledigger.file.drives.RemoteEntry>>()
            val found = com.example.mobiledigger.file.drives.DriveClients.listAudioRecursive(client, drive.folder, archives = archives)
            synchronized(scanArchives) {
                archives.forEach { (folder, e) ->
                    scanArchives.add(
                        com.example.mobiledigger.file.archives.ArchiveItem(
                            uri = com.example.mobiledigger.file.drives.DriveProxy.uriFor(drive.id, e.path),
                            name = e.name, size = e.size, folder = folder.takeIf { it.isNotEmpty() },
                            driveId = drive.id, drivePath = e.path, driveFolder = folder
                        )
                    )
                }
            }
            CrashLogger.log("FileManager", "Loaded ${found.size} files from drive '${drive.name}' (${drive.type})")
            return found.map { (folder, entry) ->
                MusicFile(
                    uri = com.example.mobiledigger.file.drives.DriveProxy.uriFor(drive.id, entry.path),
                    name = entry.name,
                    size = entry.size,
                    duration = 0L,
                    subfolder = folder.takeIf { it.isNotEmpty() }
                )
            }
        } catch (e: Exception) {
            lastDriveError = e.message
            throw e
        } finally {
            runCatching { client.close() }
        }
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
        isSource: Boolean,
        subfolderPath: String? = null
    ): List<MusicFile> {
        val musicFiles = mutableListOf<MusicFile>()
        Log.d("FileManager", "Starting scanForMusicFiles with extensions: $musicExtensions")
        
        // Memory safety: Check available memory before scanning
        val runtime = Runtime.getRuntime()
        val availableMemoryMB = (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / (1024 * 1024)
        if (availableMemoryMB < 50) {
            CrashLogger.log("FileManager", "Low memory detected ($availableMemoryMB MB), skipping file scan")
            return emptyList()
        }
        
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
                    if (isHiddenOrTrashed(displayName)) continue
                    val docId = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                    val mimeType = cursor.getString(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE))
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(directoryUri, docId)

                    if (DocumentsContract.Document.MIME_TYPE_DIR == mimeType) {
                        // Recurse into subdirectories with memory limit
                        if (musicFiles.size < 1000) { // Limit total files to prevent memory issues
                            try {
                                // Build subfolder path for nested directories
                                val newSubfolderPath = if (subfolderPath != null) {
                                    "$subfolderPath/$displayName"
                                } else {
                                    displayName
                                }
                                musicFiles.addAll(scanForMusicFiles(childUri, isSource, newSubfolderPath))
                            } catch (e: Exception) {
                                CrashLogger.log("FileManager", "Failed to scan subfolder $displayName", e)
                            }
                        } else {
                            CrashLogger.log("FileManager", "File limit reached (1000), skipping remaining directories")
                        }
                        continue
                    }

                    // Check both file extension and MIME type for audio files
                    val extension = displayName.substringAfterLast('.', "").lowercase()
                    if (isSource && extension in com.example.mobiledigger.file.drives.DriveClients.ARCHIVE_EXTENSIONS) {
                        val size = runCatching {
                            cursor.getLong(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE))
                        }.getOrDefault(0L)
                        synchronized(scanArchives) {
                            scanArchives.add(
                                com.example.mobiledigger.file.archives.ArchiveItem(
                                    uri = childUri, name = displayName, size = size,
                                    folder = subfolderPath, parentDocUri = directoryUri
                                )
                            )
                        }
                        continue
                    }
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
                            
                            // Liked / Rejected are ordered newest-sorted first: the time it was
                            // liked/rejected (sort history), else the file's modified date. (A sort is
                            // a rename, which keeps the file's old date.)
                            val lastModified = runCatching {
                                cursor.getLong(cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED))
                            }.getOrDefault(0L)
                            val addedAt = (if (!isSource) com.example.mobiledigger.util.SortHistory.sortedAt(displayName, fileSize) else null)
                                ?: lastModified
                            val basicMusicFile = MusicFile(
                                uri = childUri,
                                name = displayName,
                                duration = 0L, // Will be extracted when played
                                size = fileSize,
                                dateAdded = addedAt,
                                subfolder = subfolderPath
                            )
                            musicFiles.add(basicMusicFile)
                            
                            // Log files with subfolder for debugging
                            if (subfolderPath != null && musicFiles.size % 10 == 0) {
                                CrashLogger.log("FileManager", "📁 SAF: Found file in '$subfolderPath': $displayName")
                            }
                            
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
            scanDirectoryRecursive(directory, directory, musicFiles)
            
        } catch (e: Exception) {
            CrashLogger.log("FileManager", "Error scanning direct directory", e)
        }
        
        musicFiles
    }
    
    private fun scanDirectoryRecursive(rootFolder: File, directory: File, musicFiles: MutableList<MusicFile>) {
        try {
            val files = directory.listFiles()
            if (files == null) {
                CrashLogger.log("FileManager", "Cannot list files in directory: ${directory.absolutePath}")
                return
            }
            
            for (file in files) {
                if (isHiddenOrTrashed(file.name)) continue
                if (file.isDirectory) {
                    // Recursively scan subdirectories
                    scanDirectoryRecursive(rootFolder, file, musicFiles)
                } else if (file.isFile && isMusicFile(file.name)) {
                    try {
                        val extension = file.name.substringAfterLast('.', "").lowercase()
                        val isAiffFile = extension == "aif" || extension == "aiff"
                        
                        // Create file:// URI for direct access
                        val fileUri = Uri.fromFile(file)
                        
                        // Extract subfolder name (relative to root folder)
                        val subfolder = if (directory != rootFolder) {
                            val relativePath = directory.absolutePath.removePrefix(rootFolder.absolutePath).removePrefix("/")
                            if (relativePath.isNotEmpty()) relativePath else null
                        } else null
                        
                        val musicFile = MusicFile(
                            uri = fileUri,
                            name = file.name,
                            duration = 0L, // Will be extracted when played
                            size = file.length(),
                            subfolder = subfolder
                        )
                        musicFiles.add(musicFile)
                        
                        // Log files with subfolder
                        if (subfolder != null) {
                            CrashLogger.log("FileManager", "📁 Found file in subfolder '$subfolder': ${file.name}")
                        }
                        
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
    
    /**
     * Rejecting a track that lives on a network drive deletes it from the drive.
     * If the drive refuses (read-only Pi server, permissions, offline) the user is told and the
     * file stays on the drive; it still leaves the To Do list.
     */
    private fun deleteFromDrive(musicFile: MusicFile, driveId: String, path: String) {
        val drive = com.example.mobiledigger.file.drives.DriveStore(context).get(driveId)
        if (drive == null) {
            onDriveNotice?.invoke("${musicFile.name} wasn't deleted: its network drive is no longer saved.")
            return
        }
        val client = com.example.mobiledigger.file.drives.DriveClients.create(drive)
        try {
            client.delete(path)
            CrashLogger.log("FileManager", "Deleted ${musicFile.name} from drive '${drive.name}'")
            onDriveNotice?.invoke("Deleted ${musicFile.name} from ${drive.name}")
        } catch (e: Exception) {
            CrashLogger.log("FileManager", "Could not delete ${musicFile.name} from '${drive.name}'", e)
            onDriveNotice?.invoke("Couldn't delete ${musicFile.name} from ${drive.name}. ${e.message ?: ""} It's still on the drive.".trim())
        } finally {
            runCatching { client.close() }
        }
    }

    /**
     * Permanently deletes one track (phone file via SAF, or on its network drive). Quiet: the
     * caller reports totals. Returns true if it's gone.
     */
    suspend fun deletePermanently(musicFile: MusicFile): Boolean = withContext(Dispatchers.IO) {
        com.example.mobiledigger.file.drives.DriveProxy.parse(musicFile.uri)?.let { (driveId, path) ->
            val drive = com.example.mobiledigger.file.drives.DriveStore(context).get(driveId) ?: return@withContext false
            val client = com.example.mobiledigger.file.drives.DriveClients.create(drive)
            return@withContext try { client.delete(path); true } catch (e: Exception) {
                CrashLogger.log("FileManager", "Delete on drive failed: ${musicFile.name}", e); false
            } finally { runCatching { client.close() } }
        }
        try {
            if (android.provider.DocumentsContract.isDocumentUri(context, musicFile.uri))
                android.provider.DocumentsContract.deleteDocument(context.contentResolver, musicFile.uri)
            else androidx.documentfile.provider.DocumentFile.fromSingleUri(context, musicFile.uri)?.delete() == true
        } catch (e: Exception) {
            CrashLogger.log("FileManager", "Delete failed: ${musicFile.name}", e); false
        }
    }

    suspend fun sortFile(musicFile: MusicFile, action: SortAction): Boolean =
        sortFileInner(musicFile, action).also { ok ->
            if (ok) com.example.mobiledigger.util.SortHistory.record(musicFile, action == SortAction.LIKE, resultsPath(if (action == SortAction.LIKE) "Liked" else "Rejected"))
        }

    private suspend fun sortFileInner(musicFile: MusicFile, action: SortAction): Boolean {
        return withContext(Dispatchers.IO) {
            // Remote (Pi) files live on the server — no filesystem move is possible.
            // Report success so the UI playlists still update.
            val scheme = musicFile.uri.scheme
            com.example.mobiledigger.file.drives.DriveProxy.parse(musicFile.uri)?.let { (driveId, path) ->
                if (action == SortAction.DISLIKE) deleteFromDrive(musicFile, driveId, path)
                // Liking a network track keeps it where it is on the drive.
                return@withContext true
            }
            if (scheme == "http" || scheme == "https") {
                CrashLogger.log("FileManager", "Skipping filesystem sort for remote file: ${musicFile.name}")
                return@withContext true
            }
            try {
                CrashLogger.log("FileManager", "🔍 Starting sort operation for: ${musicFile.name} with action: $action")
                CrashLogger.log("FileManager", "🔍 File URI: ${musicFile.uri}")
                CrashLogger.log("FileManager", "🔍 File size: ${musicFile.size} bytes")
                
                // Handle temporary files differently
                val sourceFile = if (musicFile.uri.scheme == "file" && musicFile.uri.path?.contains("temp_audio") == true) {
                    // This is a temporary file, handle it as a regular File
                    val file = File(musicFile.uri.path!!)
                    if (!file.exists()) {
                        CrashLogger.log("FileManager", "Temporary source file not found: ${musicFile.uri}")
                        return@withContext false
                    }
                    CrashLogger.log("FileManager", "Temporary source file found: ${file.name}, exists: ${file.exists()}, canRead: ${file.canRead()}")
                    null // We'll handle this differently below
                } else {
                    // Regular DocumentFile handling
                    val docFile = DocumentFile.fromSingleUri(context, musicFile.uri)
                    if (docFile == null || !docFile.exists()) {
                        CrashLogger.log("FileManager", "Source file not found: ${musicFile.uri}")
                        return@withContext false
                    }
                    CrashLogger.log("FileManager", "Source file found: ${docFile.name}, exists: ${docFile.exists()}, canRead: ${docFile.canRead()}")
                    docFile
                }

                val destination = destinationFolder ?: run {
                    CrashLogger.log("FileManager", "Destination folder is not set.")
                    return@withContext false
                }
                CrashLogger.log("FileManager", "Destination folder: ${destination.uri}")

                if (!destination.exists()) {
                    CrashLogger.log("FileManager", "Destination folder doesn't exist: ${destination.uri}")
                    return@withContext false
                }

                if (!destination.canWrite()) {
                    CrashLogger.log("FileManager", "No write permission to destination folder: ${destination.uri}")
                    return@withContext false
                }
                CrashLogger.log("FileManager", "Destination folder permissions OK")

                val subFolderName = if (action == SortAction.LIKE) "Liked" else "Rejected"
                CrashLogger.log("FileManager", "Looking for subfolder: $subFolderName")
                var targetSubFolder = destination.findFile(subFolderName)
                if (targetSubFolder == null) {
                    CrashLogger.log("FileManager", "Subfolder not found, creating: $subFolderName")
                    targetSubFolder = destination.createDirectory(subFolderName)
                    if (targetSubFolder == null) {
                        CrashLogger.log("FileManager", "Failed to create subfolder: $subFolderName in ${destination.uri}")
                        return@withContext false
                    }
                    CrashLogger.log("FileManager", "Successfully created subfolder: $subFolderName")
                } else {
                    CrashLogger.log("FileManager", "Found existing subfolder: $subFolderName")
                }
                
                if (!targetSubFolder.canWrite()) {
                    CrashLogger.log("FileManager", "No write permission to subfolder: ${targetSubFolder.uri}")
                    return@withContext false
                }
                CrashLogger.log("FileManager", "Subfolder permissions OK: ${targetSubFolder.uri}")

                // Handle file copying based on source type
                val isTemporaryFile = sourceFile == null
                val desiredName = if (isTemporaryFile) {
                    musicFile.name
                } else {
                    sourceFile!!.name ?: musicFile.name
                }
                val safeName = resolveUniqueName(targetSubFolder, desiredName)
                // Same storage: an instant move (rename) instead of copying the whole file —
                // big back-to-back copies starved playback of the next track (audible hiccups).
                if (!isTemporaryFile && safeName == desiredName && fastMove(sourceFile!!.uri, targetSubFolder.uri)) {
                    CrashLogger.log("FileManager", "Moved (rename) ${musicFile.name} to $subFolderName")
                    return@withContext true
                }
                val target = targetSubFolder.createFile("audio/*", safeName)
                    ?: run {
                        CrashLogger.log("FileManager", "Failed to create target file: $safeName")
                        return@withContext false
                    }

                var bytesCopied = 0L
                if (isTemporaryFile) {
                    // Handle temporary file copying
                    val tempFile = File(musicFile.uri.path!!)
                    tempFile.inputStream().use { input ->
                        contentResolver.openOutputStream(target.uri)?.use { output ->
                            bytesCopied = input.copyTo(output)
                            output.flush()
                        } ?: run {
                            CrashLogger.log("FileManager", "Unable to open output stream for ${target.uri}")
                            target.delete()
                            return@withContext false
                        }
                    }
                    // Delete temporary file after copying
                    if (!tempFile.delete()) {
                        CrashLogger.log("FileManager", "Could not delete temporary file: ${tempFile.absolutePath}")
                    }
                } else {
                    // Handle regular DocumentFile copying
                    contentResolver.openInputStream(sourceFile!!.uri)?.use { input ->
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
                        // Copy succeeded but the original could not be removed. Returning
                        // success here would DUPLICATE the file (it now exists in both the
                        // source and destination) while the UI reports a clean move and
                        // drops the row. Roll back the copy and report failure so the
                        // library stays consistent.
                        CrashLogger.log("FileManager", "Could not delete source file after copy; rolling back move: ${sourceFile.uri}")
                        target.delete()
                        return@withContext false
                    }
                }

                CrashLogger.log("FileManager", "Successfully sorted file: ${musicFile.name} to $subFolderName ($bytesCopied bytes)")
                return@withContext true
            } catch (e: Exception) {
                CrashLogger.log("FileManager", "Error sorting file: ${musicFile.name}", e)
                return@withContext false
            }
        }
    }

    /**
     * Moves a document into [targetParent] without copying its bytes (a rename when both are on
     * the same storage). Returns false when the provider can't do it, so the caller copies instead.
     */
    private fun fastMove(src: Uri, targetParent: Uri): Boolean = try {
        if (src.authority != targetParent.authority || !DocumentsContract.isDocumentUri(context, src)) false
        else {
            val docId = DocumentsContract.getDocumentId(src)
            val slash = docId.lastIndexOf('/')
            if (slash <= 0) false else {
                val parentId = docId.substring(0, slash)
                val parentUri = if (DocumentsContract.isTreeUri(src)) DocumentsContract.buildDocumentUriUsingTree(src, parentId)
                    else DocumentsContract.buildDocumentUri(src.authority, parentId)
                DocumentsContract.moveDocument(contentResolver, src, parentUri, targetParent) != null
            }
        }
    } catch (e: Exception) {
        CrashLogger.log("FileManager", "Fast move not possible, copying instead: ${e.message}")
        false
    }

    private fun resolveUniqueName(parent: DocumentFile, desiredName: String): String {
        // Keep extension, append (1), (2), ... if needed
        val dot = desiredName.lastIndexOf('.')
        val base = if (dot > 0) desiredName.substring(0, dot) else desiredName
        val ext = if (dot > 0) desiredName.substring(dot) else ""
        // Phone storage ignores letter case ("BeckhäUser" == "Beckhäuser"), so compare that way;
        // a case-only clash used to slip through and end up as "name.flac (1)".
        val taken = parent.listFiles().mapNotNull { it.name?.lowercase() }.toHashSet()
        if (desiredName.lowercase() !in taken) return desiredName
        var index = 1
        while (true) {
            val candidate = "$base ($index)$ext"
            if (candidate.lowercase() !in taken) return candidate
            index += 1
        }
    }

    // Direct move via DocumentsContract.moveDocument is not used here due to inconsistent
    // provider support; copy + delete achieves the same user-visible result reliably.
    
    fun getDestinationPath(): String {
        return destinationFolder?.uri?.path ?: "Not set"
    }

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
            val original = result.musicFile
            val movedName = original.name
            val sourceFile = sourceFolder.findFile(movedName) ?: return@withContext null

            // Put the track back in the folder it came from (it may be several subfolders deep),
            // not the top of the source folder.
            val originalParent = originalParentUri(original.uri)
            val parentUri = originalParent ?: selectedFolder?.uri ?: return@withContext null
            val parentName = originalParent?.let { DocumentsContract.getDocumentId(it).substringAfterLast('/') }
            CrashLogger.log("FileManager", "Undo: restoring $movedName to ${parentName ?: "source root"}")

            // Same storage: instant move, keeping the original name when it is free.
            val nameFree = childExists(parentUri, movedName).not()
            if (nameFree) {
                val moved = try {
                    DocumentsContract.moveDocument(contentResolver, sourceFile.uri, sourceFolder.uri, parentUri)
                } catch (e: Exception) {
                    CrashLogger.log("FileManager", "Undo fast move not possible, copying instead: ${e.message}")
                    null
                }
                if (moved != null) {
                    CrashLogger.log("FileManager", "Undo: moved (rename) $movedName back")
                    // moveDocument hands back a URI under the Liked/Rejected tree, which can't
                    // open a file outside it — rebuild it under the source folder's tree.
                    val usable = DocumentsContract.buildDocumentUriUsingTree(parentUri, DocumentsContract.getDocumentId(moved))
                    return@withContext original.copy(uri = usable)
                }
            }

            // Fallback: copy + delete into the original folder.
            val dot = movedName.lastIndexOf('.')
            val finalName = if (nameFree) movedName
                else if (dot > 0) movedName.substring(0, dot) + " (restored)" + movedName.substring(dot)
                else "$movedName (restored)"
            val targetUri = DocumentsContract.createDocument(contentResolver, parentUri, sourceFile.type ?: "audio/*", finalName)
                ?: return@withContext null
            contentResolver.openInputStream(sourceFile.uri)?.use { input ->
                contentResolver.openOutputStream(targetUri)?.use { output -> input.copyTo(output) }
            }
            sourceFile.delete()
            CrashLogger.log("FileManager", "Undo: copied $movedName back as $finalName")
            original.copy(uri = targetUri, name = finalName)
        } catch (e: Exception) {
            CrashLogger.log("FileManager", "Error undoing sort", e)
            null
        }
    }

    /** Parent folder of a SAF tree document (same tree), or null if it can't be derived/doesn't exist. */
    private fun originalParentUri(uri: Uri): Uri? = try {
        if (!DocumentsContract.isTreeUri(uri) || !DocumentsContract.isDocumentUri(context, uri)) null
        else {
            val docId = DocumentsContract.getDocumentId(uri)
            val slash = docId.lastIndexOf('/')
            if (slash <= 0) null else {
                val parent = DocumentsContract.buildDocumentUriUsingTree(uri, docId.substring(0, slash))
                if (DocumentFile.fromSingleUri(context, parent)?.exists() == true) parent else null
            }
        }
    } catch (_: Exception) { null }

    /** Whether [parentDoc] (a tree document URI) already has a child named [name], ignoring case. */
    private fun childExists(parentDoc: Uri, name: String): Boolean = try {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(parentDoc, DocumentsContract.getDocumentId(parentDoc))
        contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            var found = false
            while (c.moveToNext()) if (c.getString(0)?.equals(name, ignoreCase = true) == true) { found = true; break }
            found
        } ?: false
    } catch (_: Exception) { false }

    private suspend fun getDuration(uri: Uri): Long {
        return try {
            val duration = ResourceManager.extractDurationWithFallback(context, uri)
            if (duration == 0L) {
                CrashLogger.log("FileManager", "Could not extract duration for $uri - this might be an unsupported format")
            }
            duration
        } catch (e: Exception) {
            CrashLogger.log("FileManager", "Error getting duration for $uri", e)
            0L
        }
    }
    
    // Delete all files from "Rejected" folder
    suspend fun deleteRejectedFiles(): Boolean = withContext(Dispatchers.IO) {
        return@withContext deleteRejectedFilesWithProgress { _ -> }
    }
    
    // Delete all files from "Rejected" folder with progress callback
    suspend fun deleteRejectedFilesWithProgress(
        onProgress: (deletedCount: Int) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val destination = destinationFolder ?: return@withContext false
            val rejectedFolder = destination.findFile("Rejected")
                ?: destination.findFile("I Don't Dig")
                ?: return@withContext false
            if (!rejectedFolder.isDirectory) return@withContext false
            
            val files = rejectedFolder.listFiles().filter { it.isFile }
            var deletedCount = 0
            
            CrashLogger.log("FileManager", "Starting deletion of ${files.size} rejected files")
            
            for ((index, file) in files.withIndex()) {
                try { 
                    if (file.delete()) {
                        deletedCount++
                        onProgress(deletedCount)
                        CrashLogger.log("FileManager", "Deleted file ${index + 1}/${files.size}: ${file.name}")
                    }
                } catch (e: Exception) {
                    CrashLogger.log("FileManager", "Failed to delete file: ${file.name}", e)
                }
                
                // Small delay to prevent overwhelming the system
                if (index % 10 == 0) {
                    delay(50)
                }
            }
            
            CrashLogger.log("FileManager", "Deletion completed: $deletedCount out of ${files.size} files deleted")
            true
        } catch (e: Exception) {
            CrashLogger.log("FileManager", "Error deleting rejected files", e)
            false
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
    
    fun getRejectedFolderUri(): Uri? {
        return destinationFolder?.findFile("Rejected")?.uri
    }

    fun listSubfolders(parentUri: Uri): List<Uri> {
        val subfolders = mutableListOf<Uri>()
        try {
            val parentDocument = DocumentFile.fromTreeUri(context, parentUri)
            parentDocument?.listFiles()?.forEach { file ->
                if (file.isDirectory && !isHiddenOrTrashed(file.name)) {
                    subfolders.add(file.uri)
                }
            }
            CrashLogger.log("FileManager", "Found ${subfolders.size} subfolders in ${parentUri}")
        } catch (e: Exception) {
            CrashLogger.log("FileManager", "Error listing subfolders for ${parentUri}", e)
        }
        return subfolders
    }
    
    suspend fun loadMusicFilesFromSubfolder(subfolderUri: Uri): List<MusicFile> = withContext(Dispatchers.IO) {
        val musicFiles = mutableListOf<MusicFile>()
        try {
            val subfolderDocument = DocumentFile.fromTreeUri(context, subfolderUri) ?: DocumentFile.fromSingleUri(context, subfolderUri)
            subfolderDocument?.let { folder ->
                if (folder.exists() && folder.canRead()) {
                    val scanned = scanForMusicFiles(folder.uri, true)
                    musicFiles.addAll(scanned)
                    CrashLogger.log("FileManager", "Scanned ${musicFiles.size} files from subfolder: ${folder.name}")
                } else {
                    CrashLogger.log("FileManager", "Cannot access subfolder: ${folder.name}")
                }
            } ?: run {
                CrashLogger.log("FileManager", "Subfolder document not found for URI: $subfolderUri")
            }
        } catch (e: Exception) {
            CrashLogger.log("FileManager", "Error loading music files from subfolder", e)
        }
        musicFiles
    }
    
    fun getFileName(uri: Uri): String? {
        val documentFile = DocumentFile.fromSingleUri(context, uri)
        return documentFile?.name
    }

    suspend fun moveFilesToSubfolder(files: List<MusicFile>, subfolderName: String): Boolean =
        moveFilesToSubfolderInner(files, subfolderName).also { ok ->
            if (ok && (subfolderName == "Liked" || subfolderName == "Rejected"))
                files.forEach { com.example.mobiledigger.util.SortHistory.record(it, subfolderName == "Liked", resultsPath(subfolderName)) }
        }

    /** "Results october › Liked" — where a sorted track went (for Settings → History). */
    private fun resultsPath(sub: String): String {
        val dest = destinationFolder
        val loc = dest?.uri?.let { u ->
            runCatching { DocumentsContract.getTreeDocumentId(u) }.getOrNull()?.let { id ->
                val vol = id.substringBefore(':')
                val path = id.substringAfter(':', "").trim('/').split('/').filter { it.isNotBlank() }
                (listOf(if (vol.equals("primary", true)) "Phone" else "SD card / USB ($vol)") + path).joinToString(" › ")
            }
        } ?: dest?.name ?: "Results"
        return "$loc › $sub"
    }

    private suspend fun moveFilesToSubfolderInner(files: List<MusicFile>, subfolderName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val destination = destinationFolder ?: return@withContext false
            var targetSubFolder = destination.findFile(subfolderName)
            if (targetSubFolder == null) {
                targetSubFolder = destination.createDirectory(subfolderName)
            }
            targetSubFolder ?: return@withContext false

            var allSucceeded = true
            files.forEach { musicFile ->
                // Remote (Pi) files can't be moved on the local filesystem — skip.
                if (musicFile.uri.scheme == "http" || musicFile.uri.scheme == "https") {
                    CrashLogger.log("FileManager", "Skipping subfolder move for remote file: ${musicFile.name}")
                    return@forEach
                }
                val sourceFile = DocumentFile.fromSingleUri(context, musicFile.uri)
                if (sourceFile == null || !sourceFile.exists()) {
                    allSucceeded = false
                    return@forEach
                }
                val desiredName = sourceFile.name ?: musicFile.name
                val safeName = resolveUniqueName(targetSubFolder, desiredName)
                if (safeName == desiredName && fastMove(sourceFile.uri, targetSubFolder.uri)) return@forEach
                val target = targetSubFolder.createFile(sourceFile.type ?: "audio/*", safeName)
                if (target == null) {
                    allSucceeded = false
                    return@forEach
                }

                // Verify the copy actually happened BEFORE deleting the original.
                // Previously the source was deleted unconditionally — if either stream
                // was null the file was lost without ever being copied (data loss).
                var copied = false
                try {
                    contentResolver.openInputStream(sourceFile.uri)?.use { input ->
                        contentResolver.openOutputStream(target.uri)?.use { output ->
                            input.copyTo(output)
                            output.flush()
                            copied = true
                        }
                    }
                } catch (e: Exception) {
                    CrashLogger.log("FileManager", "Error copying ${musicFile.name} to subfolder", e)
                }

                if (copied) {
                    if (!sourceFile.delete()) {
                        // Copy ok but original couldn't be removed: drop the copy to
                        // avoid a duplicate and report failure for this file.
                        CrashLogger.log("FileManager", "Could not delete source after copy; rolling back: ${sourceFile.uri}")
                        target.delete()
                        allSucceeded = false
                    }
                } else {
                    // Copy failed: remove the empty/partial target, keep the original.
                    CrashLogger.log("FileManager", "Copy failed for ${musicFile.name}; original kept")
                    target.delete()
                    allSucceeded = false
                }
            }
            allSucceeded
        } catch (e: Exception) {
            CrashLogger.log("FileManager", "Error moving files to subfolder", e)
            false
        }
    }

    suspend fun renameFile(musicFile: MusicFile, newBaseName: String): MusicFile? = withContext(Dispatchers.IO) {
        try {
            val oldName = musicFile.name
            val dot = oldName.lastIndexOf('.')
            val ext = if (dot > 0) oldName.substring(dot) else ""
            val newName = if (ext.isNotEmpty()) "$newBaseName$ext" else newBaseName

            // Remote (Pi) files can't be renamed from the app.
            if (musicFile.uri.scheme == "http" || musicFile.uri.scheme == "https") {
                CrashLogger.log("FileManager", "Skipping rename for remote file: ${musicFile.name}")
                return@withContext null
            }

            return@withContext if (musicFile.uri.scheme == "file") {
                val oldFile = java.io.File(musicFile.uri.path ?: return@withContext null)
                val target = java.io.File(oldFile.parentFile, newName)
                if (oldFile.renameTo(target)) {
                    musicFile.copy(uri = Uri.fromFile(target), name = target.name)
                } else null
            } else {
                // Try DocumentFile.renameTo first (attempt two-step rename if provider appends (1))
                val df = DocumentFile.fromSingleUri(context, musicFile.uri)
                if (df != null && df.exists()) {
                    val ok = try { df.renameTo(newName) } catch (_: Exception) { false }
                    if (ok) {
                        val updated = DocumentFile.fromSingleUri(context, musicFile.uri)
                        var finalName = updated?.name ?: newName
                        // If provider still appended (1), try two-step rename (temp -> target) to avoid case-insensitive conflicts
                        if (finalName != newName && finalName.startsWith(newBaseName) && finalName.contains("(1)")) {
                            try {
                                val tempName = "${newBaseName}_temp_${System.currentTimeMillis()}$ext"
                                (updated ?: df).renameTo(tempName)
                                (updated ?: df).renameTo(newName)
                                finalName = (DocumentFile.fromSingleUri(context, musicFile.uri)?.name ?: newName)
                            } catch (_: Exception) {}
                        }
                        return@withContext musicFile.copy(uri = musicFile.uri, name = finalName)
                    }
                }
                // Fallback to DocumentsContract.renameDocument, then attempt second pass if (1) was appended
                val renamedUri = try { DocumentsContract.renameDocument(contentResolver, musicFile.uri, newName) } catch (_: Exception) { null }
                if (renamedUri != null) {
                    val updated = DocumentFile.fromSingleUri(context, renamedUri)
                    var finalName = updated?.name ?: newName
                    if (finalName != newName && finalName.startsWith(newBaseName) && finalName.contains("(1)")) {
                        try {
                            // Two-step rename via contract
                            val tempName = "${newBaseName}_temp_${System.currentTimeMillis()}$ext"
                            val tempUri = DocumentsContract.renameDocument(contentResolver, renamedUri, tempName) ?: renamedUri
                            DocumentsContract.renameDocument(contentResolver, tempUri, newName)?.let { retryUri ->
                                finalName = DocumentFile.fromSingleUri(context, retryUri)?.name ?: newName
                                return@withContext musicFile.copy(uri = retryUri, name = finalName)
                            }
                        } catch (_: Exception) {}
                    }
                    musicFile.copy(uri = renamedUri, name = finalName)
                } else null
            }
        } catch (e: Exception) {
            CrashLogger.log("FileManager", "Error renaming file ${musicFile.name}", e)
            null
        }
    }
}
