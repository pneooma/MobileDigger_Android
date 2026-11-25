package com.example.mobiledigger.file.drives

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.FileInputStream
import java.io.InputStream

/**
 * A drive plugged into the phone (USB hard drive / stick, SD card), reached through the folder
 * access the user granted with the system picker ([tree] = that folder). Paths are relative to
 * it, like on a network drive, so browsing, playing and copy/move work the same way.
 */
class SafDriveClient(context: Context, private val tree: Uri) : DriveClient {
    private val cr = context.applicationContext.contentResolver
    private val rootId: String = DocumentsContract.getTreeDocumentId(tree)
    /** path -> document id, filled while listing (works for any storage provider). */
    private val ids = java.util.concurrent.ConcurrentHashMap<String, String>().apply { put("", rootId) }
    private val cols = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE)

    private fun docUri(id: String) = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    /** Move / Reject need write access; a drive granted read-only (or a read-only disk) can only be copied from. */
    override val canWrite: Boolean
        get() = cr.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }

    private fun children(parentId: String): List<Pair<String, RemoteEntry>> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val out = mutableListOf<Pair<String, RemoteEntry>>()
        val c = try { cr.query(uri, cols, null, null, null) } catch (e: Exception) { throw unreachable(e) }
        c ?: throw DriveException("The drive isn't connected (or access was removed)")
        c.use {
            while (it.moveToNext()) {
                val name = it.getString(1) ?: continue
                val isDir = it.getString(2) == Document.MIME_TYPE_DIR
                out += it.getString(0) to RemoteEntry(name, "", isDir, if (isDir) 0 else it.getLong(3))
            }
        }
        return out
    }

    private fun unreachable(e: Exception) =
        DriveException("The drive isn't connected (or access was removed). Plug it in and try again.", e)

    /** Document id for [path], walking down from the root by name when it isn't cached yet. */
    private fun idOf(path: String): String {
        val p = path.trim('/')
        ids[p]?.let { return it }
        val parent = p.substringBeforeLast('/', "")
        val name = p.substringAfterLast('/')
        val parentId = idOf(parent)
        children(parentId).forEach { (id, e) -> ids[joinPath(parent, e.name)] = id }
        return ids[p] ?: throw DriveException("“$name” isn't on the drive any more")
    }

    override fun list(path: String): List<RemoteEntry> {
        val p = path.trim('/')
        return children(idOf(p)).map { (id, e) ->
            val full = joinPath(p, e.name)
            ids[full] = id
            e.copy(path = full)
        }
    }

    override fun open(path: String, offset: Long): RemoteStream {
        val pfd = try { cr.openFileDescriptor(docUri(idOf(path)), "r") } catch (e: Exception) { throw unreachable(e) }
            ?: throw DriveException("Can't open ${path.substringAfterLast('/')}")
        val input = FileInputStream(pfd.fileDescriptor)
        if (offset > 0) input.channel.position(offset)
        return RemoteStream(input, pfd.statSize) { runCatching { pfd.close() } }
    }

    override fun delete(path: String) {
        val ok = try { DocumentsContract.deleteDocument(cr, docUri(idOf(path))) } catch (e: Exception) {
            throw DriveException("Couldn't delete ${path.substringAfterLast('/')}: ${e.message}", e)
        }
        if (!ok) throw DriveException("Couldn't delete ${path.substringAfterLast('/')}")
        ids.remove(path.trim('/'))
    }

    override fun deleteFolder(path: String) {
        delete(path)
        val prefix = path.trim('/') + "/"
        ids.keys.removeAll { it.startsWith(prefix) }
    }

    override fun mkdirs(path: String) {
        val p = path.trim('/')
        if (p.isEmpty() || runCatching { idOf(p) }.isSuccess) return
        val parent = p.substringBeforeLast('/', "")
        mkdirs(parent)
        val id = DocumentsContract.createDocument(cr, docUri(idOf(parent)), Document.MIME_TYPE_DIR, p.substringAfterLast('/'))
            ?.let { DocumentsContract.getDocumentId(it) } ?: throw DriveException("Can't create folder ${p.substringAfterLast('/')}")
        ids[p] = id
    }

    override fun write(path: String, input: InputStream, length: Long) {
        val p = path.trim('/')
        val parent = p.substringBeforeLast('/', "")
        mkdirs(parent)
        val target = runCatching { docUri(idOf(p)) }.getOrNull()
            ?: DocumentsContract.createDocument(cr, docUri(idOf(parent)), "application/octet-stream", p.substringAfterLast('/'))
                ?.also { ids[p] = DocumentsContract.getDocumentId(it) }
            ?: throw DriveException("Can't create ${p.substringAfterLast('/')}")
        (cr.openOutputStream(target, "wt") ?: throw DriveException("Can't write ${p.substringAfterLast('/')}")).use { input.copyTo(it, 256 * 1024) }
    }
}
