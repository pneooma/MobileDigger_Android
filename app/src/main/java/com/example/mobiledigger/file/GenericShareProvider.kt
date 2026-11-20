package com.example.mobiledigger.file

import android.database.Cursor
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.mobiledigger.util.CrashLogger

/**
 * FileProvider that reports EVERY file with one generic, non-media MIME type.
 *
 * WhatsApp decides how to send an attachment from ContentResolver.getType(uri), not from
 * the Intent's type:
 *  - audio/…                  → treated as media (why tracks used to be zipped)
 *  - application/octet-stream → sent as a document, but WhatsApp maps that type to the
 *                               ".bin" extension and renames the file
 * A vendor type that Android's MimeTypeMap has NO extension for is still a plain
 * document, and WhatsApp keeps the real filename + extension from DISPLAY_NAME.
 */
class GenericShareProvider : FileProvider() {
    override fun getType(uri: Uri): String {
        CrashLogger.log("GenericShareProvider", "getType ${uri.lastPathSegment} by ${callingPackage ?: "?"} -> $GENERIC_MIME")
        return GENERIC_MIME
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor {
        CrashLogger.log("GenericShareProvider", "query ${uri.lastPathSegment} by ${callingPackage ?: "?"} projection=${projection?.joinToString()}")
        return super.query(uri, projection, selection, selectionArgs, sortOrder)
    }

    companion object {
        /** Deliberately unknown to MimeTypeMap → no extension substitution. */
        const val GENERIC_MIME = "application/vnd.mobiledigger.file"
        fun authority(packageName: String) = "$packageName.genericshare"
    }
}
