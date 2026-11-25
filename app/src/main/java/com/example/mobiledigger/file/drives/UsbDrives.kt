package com.example.mobiledigger.file.drives

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.DocumentsContract

/** A removable volume that is plugged in right now (USB drive, SD card). */
data class AttachedVolume(
    val id: String,            // volume UUID, e.g. "1A2B-3C4D" — the start of its document ids
    val label: String,         // "SanDisk USB drive", "WD Elements" …
    val freeBytes: Long?,
    val totalBytes: Long?,
    val volume: StorageVolume
)

object UsbDrives {
    /** Removable volumes currently mounted. */
    fun attached(context: Context): List<AttachedVolume> {
        val sm = context.getSystemService(StorageManager::class.java) ?: return emptyList()
        return sm.storageVolumes
            .filter { it.isRemovable && !it.isPrimary && it.state == Environment.MEDIA_MOUNTED && it.uuid != null }
            .map { v ->
                val stat = runCatching { v.directory?.let { StatFs(it.path) } }.getOrNull()
                AttachedVolume(
                    id = v.uuid!!,
                    label = v.getDescription(context).ifBlank { "USB drive" },
                    freeBytes = stat?.availableBytes,
                    totalBytes = stat?.totalBytes,
                    volume = v
                )
            }
    }

    /** System prompt that opens at the drive's top folder, so one tap grants the whole drive. */
    fun accessIntent(v: AttachedVolume): Intent = v.volume.createOpenDocumentTreeIntent()

    /** Volume id a granted folder lives on ("1A2B-3C4D" for "1A2B-3C4D:Music"), or null. */
    fun volumeOf(tree: Uri): String? = runCatching { DocumentsContract.getTreeDocumentId(tree).substringBefore(':') }.getOrNull()

    /** Folder inside the volume the grant covers ("" = the whole drive). */
    fun folderOf(tree: Uri): String = runCatching { DocumentsContract.getTreeDocumentId(tree).substringAfter(':', "") }.getOrDefault("")

    /** Whether the app still holds read access to [tree] (it survives unplugging / re-plugging). */
    fun hasAccess(context: Context, tree: String): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri.toString() == tree && it.isReadPermission }
}
