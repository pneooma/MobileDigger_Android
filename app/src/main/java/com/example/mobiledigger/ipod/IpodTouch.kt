package com.example.mobiledigger.ipod

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.example.mobiledigger.util.CrashLogger
import java.io.File
import java.io.IOException

data class TouchTrack(
    val id: Long,            // item_pid on the device
    val syncId: Long,        // item_store.sync_id (0 = not put there by a computer sync)
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val fileSize: Long,
    val devicePath: String
)

/** [id] = container_pid (for playlists a computer synced, also their wire pid); [editable] = made by a computer sync. */
data class TouchPlaylist(val id: Long, val name: String, val trackIds: List<Long>, val editable: Boolean)

data class TouchLibrary(
    val deviceName: String,
    val tracks: List<TouchTrack>,
    val playlists: List<TouchPlaylist>,
    val anchor: Long,
    val dbPid: Long?,
    val primaryContainerPid: Long?,
    val maxArtworkToken: Long,
    val paths: Set<String>,
    /** Songs a sync announced but whose audio never arrived (interrupted transfer): no file on the iPod. */
    val unfinished: List<TouchTrack> = emptyList()
)

/**
 * One connected iPod touch: USB mux → lockdown (paired, TLS session) → services.
 * Everything here blocks on USB I/O; call it off the main thread.
 */
class IpodTouch private constructor(val mux: UsbMux, val lockdown: Lockdown, val appDir: File) {
    val udid get() = lockdown.udid
    var name: String = "iPod touch"; private set
    var productVersion: String = ""; private set

    companion object {
        const val DB_DIR = "/iTunes_Control/iTunes"
        const val DB_NAME = "MediaLibrary.sqlitedb"

        fun findDevice(usb: UsbManager): UsbDevice? =
            usb.deviceList.values.firstOrNull { UsbMux.isApple(it) && UsbMux.findMux(it) != null }

        fun connect(context: Context, usb: UsbManager, device: UsbDevice, onPrompt: (String) -> Unit): IpodTouch {
            val mux = UsbMux.open(usb, device)
            try {
                val ld = Lockdown(mux, context.filesDir)
                ld.ensurePaired(onPrompt)
                ld.startSession()
                val name = Plist.str(runCatching { ld.getValue("DeviceName") }.getOrNull()) ?: "iPod touch"
                val version = Plist.str(runCatching { ld.getValue("ProductVersion") }.getOrNull()) ?: ""
                runCatching { ld.close() }
                return IpodTouch(mux, ld, context.filesDir).also { t ->
                    t.name = name
                    t.productVersion = version
                    CrashLogger.log("IpodTouch", "Connected: ${t.name} iOS ${t.productVersion} ${t.udid}")
                }
            } catch (e: Exception) {
                mux.close(); throw e
            }
        }
    }

    /**
     * lockdownd drops idle connections, so each use gets a fresh session (as libimobiledevice does);
     * the pairing is already saved, so no prompt.
     */
    private fun <T> withLockdown(block: (Lockdown) -> T): T {
        val ld = Lockdown(mux, appDir)
        try {
            ld.ensurePaired({ })
            ld.startSession()
            return block(ld)
        } finally { runCatching { ld.close() } }
    }

    fun startService(name: String): DeviceStream = withLockdown { it.startService(name) }

    fun afc(): AfcClient = AfcClient(startService("com.apple.afc"))

    /** Free / total bytes of the media partition, if the device says. */
    fun freeSpace(): Pair<Long, Long>? = runCatching {
        val v = Plist.dict(withLockdown { it.getValue(domain = "com.apple.disk_usage") })
        val total = Plist.long(v["TotalDataCapacity"]) ?: Plist.long(v["TotalDiskCapacity"])
        val free = Plist.long(v["TotalDataAvailable"]) ?: Plist.long(v["AmountDataAvailable"])
        if (total != null && free != null) free to total else null
    }.getOrNull()

    /** Copies the media database (+ -wal/-shm) into [dir]. */
    fun pullDatabase(afc: AfcClient, dir: File): File {
        dir.mkdirs()
        if (afc.info("$DB_DIR/$DB_NAME") == null) throw IOException("No music database on the iPod")
        for (suffix in listOf("", "-wal", "-shm")) {
            val p = "$DB_DIR/$DB_NAME$suffix"
            val local = File(dir, DB_NAME + suffix)
            local.delete()
            if (afc.info(p) != null) afc.download(p, local)
        }
        return File(dir, DB_NAME)
    }

    fun readLibrary(afc: AfcClient, work: File): TouchLibrary {
        val db = pullDatabase(afc, work)
        return LibraryReader.read(db, name)
    }

    fun close() = mux.close()
}

/** Read-only parser for the iOS MediaLibrary.sqlitedb (ported from IpodDigger's TouchLibraryReader). */
object LibraryReader {
    fun read(dbFile: File, deviceName: String): TouchLibrary {
        // A private copy is opened read-write so SQLite can replay the WAL; nothing is written back.
        val db = SQLiteDatabase.openDatabase(dbFile.path, null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
        try {
            val tables = HashSet<String>()
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND sql NOT LIKE 'CREATE VIRTUAL%'", null).use { c ->
                while (c.moveToNext()) tables += c.getString(0)
            }
            val bases = HashMap<Long, String>()
            db.rawQuery("SELECT base_location_id, path FROM base_location", null).use { c ->
                while (c.moveToNext()) bases[c.getLong(0)] = c.getString(1) ?: ""
            }
            val syncIds = HashMap<Long, Long>()
            if ("item_store" in tables) db.rawQuery("SELECT item_pid, sync_id FROM item_store", null).use { c ->
                while (c.moveToNext()) syncIds[c.getLong(0)] = c.getLong(1)
            }
            val tracks = ArrayList<TouchTrack>()
            val paths = HashSet<String>()
            db.rawQuery("""
                SELECT i.item_pid, e.title, ia.item_artist, aa.album_artist, al.album, e.total_time_ms,
                       i.base_location_id, e.location, e.file_size
                FROM item i JOIN item_extra e ON e.item_pid = i.item_pid
                LEFT JOIN item_artist ia ON ia.item_artist_pid = i.item_artist_pid
                LEFT JOIN album_artist aa ON aa.album_artist_pid = i.album_artist_pid
                LEFT JOIN album al ON al.album_pid = i.album_pid
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) {
                    val file = c.getString(7) ?: ""
                    val base = bases[c.getLong(6)] ?: continue
                    if (file.isEmpty()) continue
                    val folder = base.trim('/')
                    val rel = if (folder.isEmpty()) file else "$folder/$file"
                    paths += rel.lowercase()
                    val id = c.getLong(0)
                    val artist = c.getString(2).orEmpty().ifEmpty { c.getString(3).orEmpty() }
                    tracks += TouchTrack(
                        id, syncIds[id] ?: 0L,
                        c.getString(1).orEmpty().ifEmpty { file.substringBeforeLast('.') },
                        artist, c.getString(4).orEmpty(), c.getDouble(5).toLong(), c.getLong(8), "/$rel"
                    )
                }
            }
            val containers = ArrayList<Triple<Long, String, Boolean>>()
            db.rawQuery("""
                SELECT container_pid, name, is_src_remote, length(smart_criteria) FROM container
                WHERE distinguished_kind = 0 AND is_hidden = 0 AND smart_is_folder = 0
                ORDER BY name_order, name COLLATE NOCASE
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) containers += Triple(c.getLong(0), c.getString(1).orEmpty(), c.getInt(2) == 1 && c.isNull(3))
            }
            val known = tracks.map { it.id }.toHashSet()
            val members = HashMap<Long, List<Long>>()
            if ("container_items" in tables) {
                db.rawQuery("SELECT container_pid, item_pid_data FROM container_items", null).use { c ->
                    while (c.moveToNext()) members[c.getLong(0)] = decodePids(c.getBlob(1) ?: ByteArray(0), known)
                }
            } else if ("container_item" in tables) {
                val m = HashMap<Long, MutableList<Long>>()
                db.rawQuery("SELECT container_pid, item_pid FROM container_item ORDER BY container_pid, position", null).use { c ->
                    while (c.moveToNext()) m.getOrPut(c.getLong(0)) { ArrayList() } += c.getLong(1)
                }
                members.putAll(m)
            }
            val playlists = containers.map { (pid, n, editable) -> TouchPlaylist(pid, n, (members[pid] ?: emptyList()).filter { it in known }, editable) }

            var anchor = 0L
            db.rawQuery("SELECT value FROM _MLDatabaseProperties WHERE key = 'MLSyncClientSyncAnchor'", null).use { c ->
                if (c.moveToNext()) anchor = c.getString(0)?.toLongOrNull() ?: c.getLong(0)
            }
            var dbPid: Long? = null; var pcPid: Long? = null
            if ("db_info" in tables) db.rawQuery("SELECT db_pid, primary_container_pid FROM db_info LIMIT 1", null).use { c ->
                if (c.moveToNext()) { dbPid = c.getLong(0).takeIf { it != 0L }; pcPid = c.getLong(1) }
            }
            var maxToken = 0L
            if ("artwork_token" in tables) db.rawQuery("SELECT artwork_token FROM artwork_token", null).use { c ->
                while (c.moveToNext()) c.getString(0)?.toLongOrNull()?.let { maxToken = maxOf(maxToken, it) }
            }
            val unfinished = ArrayList<TouchTrack>()
            db.rawQuery("""
                SELECT i.item_pid, e.title, e.location FROM item i JOIN item_extra e ON e.item_pid = i.item_pid
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0); val sync = syncIds[id] ?: 0L
                    if (sync != 0L && c.getString(2).isNullOrEmpty())
                        unfinished += TouchTrack(id, sync, c.getString(1).orEmpty(), "", "", 0, 0, "")
                }
            }
            return TouchLibrary(deviceName, tracks, playlists, anchor, dbPid, pcPid, maxToken, paths, unfinished)
        } finally { db.close() }
    }

    /** container_items.item_pid_data: 8-byte item PIDs in play order (little-endian, big-endian fallback). */
    fun decodePids(blob: ByteArray, known: Set<Long>): List<Long> {
        if (blob.size < 8 || blob.size % 8 != 0) return emptyList()
        fun dec(big: Boolean) = (blob.indices step 8).map { o ->
            var v = 0L
            for (i in 0 until 8) v = v or ((blob[o + i].toLong() and 0xFF) shl (if (big) 56 - 8 * i else 8 * i))
            v
        }
        val le = dec(false)
        if (known.isEmpty() || le.any { it in known }) return le
        val be = dec(true)
        return if (be.any { it in known }) be else le
    }
}
