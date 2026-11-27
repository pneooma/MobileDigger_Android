package com.example.mobiledigger.ipod

import com.example.mobiledigger.util.CrashLogger
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Date

class TouchSyncException(message: String) : IOException(message)

/** A song to add, already prepared ([TouchMedia.prepare]). */
class TouchAdd(val audio: PreparedAudio)

/** A playlist member: a song already on the iPod (by its wire pid) or one being added now (index into the adds). */
sealed class Member {
    data class Existing(val syncId: Long) : Member()
    data class New(val addIndex: Int) : Member()
}

sealed class PlaylistChange {
    data class Create(val name: String, val members: List<Member>) : PlaylistChange()
    /** Rename / reorder / change members: done as delete + insert (both confirmed on iOS 9). */
    data class Replace(val pid: Long, val name: String, val members: List<Member>) : PlaylistChange()
    data class Delete(val pid: Long) : PlaylistChange()
}

data class SyncResult(val added: Int, val removed: Int, val playlistsChanged: Int)

/** New details for a song on the iPod (its wire pid = item_store.sync_id). */
data class TrackEdit(val syncId: Long, val title: String, val artist: String, val album: String, val albumArtist: String, val genre: String)

/**
 * Music sync to an iPod touch over ATC + AFC, ported from IpodDigger's TouchSync.writeBlocking
 * (proven on an iPod touch 5G / iOS 9.3.5). One call = one ATC session:
 *   clean staging → Ping … SyncAllowed → RequestingSync → ReadyForSync{anchor, Grappa}
 *   → stage signed Sync_00000000.plist(.cig) → FinishedSyncingMetadata
 *   → AssetManifest: upload each own track (cover → Airlock/Media/Artwork/<id>, audio → Airlock/Media/<id>)
 *   → SyncFinished.
 */
class TouchSync(private val t: IpodTouch, private val appDir: File, private val work: File,
                private val log: (String) -> Unit, private val progress: (String, Float?) -> Unit,
                /** Index (into the adds) of the song being uploaded / just completed. */
                private val onSending: (Int) -> Unit = {}, private val onSent: (Int) -> Unit = {}) {

    companion object {
        private const val STAGING = "/iTunes_Control/Sync/Media"
        private const val HOST_VERSION = "12.6.0.100"   // accepted by iOS 9.3.5
        private const val RECV_TIMEOUT = 30_000L
        private const val IMPORT_TIMEOUT = 180_000L
        private const val KEEP_BACKUPS = 5
    }

    // ------------------------------------------------------------------ ATC framing (u32 LE length + binary plist)

    private inner class Atc(val s: DeviceStream) {
        fun send(obj: Map<String, Any?>) {
            val body = Plist.toBinary(obj)
            s.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(body.size).array() + body)
            log(">>> ${obj["Command"]}")
        }
        fun receive(timeout: Long, stage: String): Map<String, Any?> {
            val hdr = try { s.readFully(4, timeout) } catch (e: IOException) { throw TouchSyncException("The iPod stopped responding ($stage). Unplug it, plug it back in and try again.") }
            val n = ByteBuffer.wrap(hdr).order(ByteOrder.LITTLE_ENDIAN).int
            if (n <= 0 || n > 32 shl 20) throw TouchSyncException("Unexpected reply from the iPod (bad frame length $n)")
            val m = Plist.dict(Plist.parse(s.readFully(n, maxOf(timeout, 10_000))))
            log("<<< ${m["Command"]} ${brief(m)}")
            return m
        }
        fun pong(m: Map<String, Any?>, session: Long) =
            send(mapOf("Command" to "Pong", "Session" to (Plist.long(m["Session"]) ?: session)))
        fun drain(ms: Long) {
            val end = System.currentTimeMillis() + ms
            while (System.currentTimeMillis() < end) {
                val m = runCatching { receive(maxOf(end - System.currentTimeMillis(), 50), "drain") }.getOrNull() ?: return
                if (m["Command"] == "Ping") runCatching { pong(m, 2) }
            }
        }
        private fun brief(m: Map<String, Any?>): String {
            val p = Plist.dict(m["Params"])
            return "session ${m["Session"]} [${p.keys.sorted().take(8).joinToString(",")}]" +
                (p["DataclassAnchors"]?.let { " anchors $it" } ?: "") + (p["AssetID"]?.let { " asset $it" } ?: "")
        }
    }

    private fun params(m: Map<String, Any?>) = Plist.dict(m["Params"])
    private fun reason(m: Map<String, Any?>): String {
        val p = params(m)
        return Plist.str(p["ErrorDescription"]) ?: if (p.isEmpty()) "no reason given" else p.entries.joinToString { "${it.key}=${it.value}" }
    }

    private fun checkAllowed(m: Map<String, Any?>) {
        val p = params(m)
        if (Plist.bool(p["DataProtected"]) == true) throw TouchSyncException("The iPod is locked. Unlock it and keep it unlocked while syncing.")
        if ((p["DisabledAssetTypes"] as? List<*>)?.contains("Music") == true) throw TouchSyncException("Music syncing is turned off on the iPod.")
        if (Plist.bool(p["ManualSync"]) == false) throw TouchSyncException("Manual syncing is turned off on the iPod.")
    }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    // ------------------------------------------------------------------ backup of the device database

    private fun backup(afc: AfcClient) {
        val root = File(appDir, "ipod/backups/${t.udid}")
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH.mm.ss", java.util.Locale.US).format(Date())
        val dest = File(root, stamp); dest.mkdirs()
        for (name in afc.list(IpodTouch.DB_DIR)) {
            val p = "${IpodTouch.DB_DIR}/$name"
            val info = afc.info(p) ?: continue
            if (info["st_ifmt"] != "S_IFREG") continue
            if ((info["st_size"]?.toLongOrNull() ?: 0) > 256L shl 20) continue
            afc.download(p, File(dest, name))
        }
        root.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.name }?.drop(KEEP_BACKUPS)?.forEach { it.deleteRecursively() }
        log("backup ${dest.path}")
    }

    /** iOS 9 leaves consumed Sync_*.plist(.cig) behind: remove ours (hash match) or already-applied ones; refuse anything else. */
    private fun cleanStaging(afc: AfcClient, state: DeviceState, deviceAnchor: Long) {
        afc.makeDirectory(STAGING)
        val names = afc.list(STAGING)
        if (names.isEmpty()) return
        val revisions = HashMap<String, Long>()
        for (n in names.filter { it.endsWith(".plist") }) runCatching {
            Plist.long(Plist.dict(Plist.parse(afc.read("$STAGING/$n")))["revision"])?.let { revisions[n] = it }
        }
        for (n in names) {
            val p = "$STAGING/$n"
            val data = runCatching { afc.read(p) }.getOrDefault(ByteArray(0))
            val stem = if (n.endsWith(".cig")) n.dropLast(4) else n
            val ours = state.staged[n] == sha256(data)
            val consumed = (revisions[stem] ?: Long.MAX_VALUE) <= deviceAnchor
            if (!ours && !consumed) throw TouchSyncException("The iPod's sync folder holds files MobileDigger didn't create ($n). Another sync may be in progress.")
            afc.remove(p)
            log("removed leftover staged file $n")
        }
        state.staged = HashMap()
    }

    // ------------------------------------------------------------------ track operation

    private fun groupKey(album: String, albumArtist: String) = album.lowercase() + "\u0001" + albumArtist.lowercase()

    private fun numberPair(s: String?): Pair<Int, Int> {
        val parts = (s ?: "").split('/').map { it.trim().toIntOrNull() ?: 0 }
        return (parts.getOrNull(0) ?: 0) to (parts.getOrNull(1) ?: 0)
    }

    private fun randomAssetPath(ext: String, taken: MutableSet<String>): String {
        val letters = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        while (true) {
            val name = (0 until 4).map { letters.random() }.joinToString("")
            val path = "iTunes_Control/Music/F%02d/".format((0 until 50).random()) + name + "." + ext
            if (taken.add(path.lowercase())) return path
        }
    }

    private class Prepared(val pid: Long, val a: PreparedAudio, val assetPath: String, val op: Map<String, Any?>, val cacheId: Long?)

    private fun buildTrack(a: PreparedAudio, pid: Long, assetPath: String, state: DeviceState, nextCache: () -> Long): Prepared {
        val (kind, format) = when (a.codec) {
            "aac" -> "AAC audio file" to 502
            "alac" -> "Apple Lossless audio file" to 601
            "mp3" -> "MPEG audio file" to 301
            else -> throw TouchSyncException("Can't send ${a.title} (${a.codec})")
        }
        val tg = a.tags
        val totalMs = (a.samples * 1000 / a.sampleRate).toLong()
        val artist = tg["artist"].orEmpty(); val albumArtist = tg["album_artist"].orEmpty()
        val album = tg["album"].orEmpty()
        val (trackNo, trackCount) = numberPair(tg["track"]); val (discNo, _) = numberPair(tg["disc"])
        val year = tg["year"]?.take(4)?.toIntOrNull() ?: 0
        val now = Date((System.currentTimeMillis() / 1000) * 1000)
        val item = linkedMapOf<String, Any?>(
            "title" to a.title, "sort_name" to a.title, "genre" to tg["genre"].orEmpty(),
            "disc_number" to (if (discNo > 0) discNo else 1), "total_time_ms" to totalMs,
            "is_song" to true, "remember_bookmark" to false, "date_created" to now, "date_modified" to now
        )
        if (artist.isNotEmpty()) { item["artist"] = artist; item["sort_artist"] = artist }
        if (albumArtist.isNotEmpty()) { item["album_artist"] = albumArtist; item["sort_album_artist"] = albumArtist }
        val groupArtist = albumArtist.ifEmpty { artist }
        if (album.isNotEmpty()) {
            item["album"] = album; item["sort_album"] = album
            val key = groupKey(album, groupArtist)
            item["album_pid"] = state.albumPids.getOrPut(key) { DeviceState.randomPid() }
        }
        if (groupArtist.isNotEmpty()) item["artist_pid"] = state.artistPids.getOrPut(groupArtist.lowercase()) { DeviceState.randomPid() }
        tg["composer"]?.let { item["composer"] = it; item["sort_composer"] = it }
        if (trackNo > 0) item["track_number"] = trackNo
        if (trackCount > 0) item["track_count"] = trackCount
        if (year > 0) item["year"] = year
        if (tg["compilation"] == "1" || tg["compilation"]?.lowercase() == "true") item["is_compilation"] = true
        var cacheId: Long? = null
        val cover = a.cover
        if (cover != null && cover.length() > 0) {
            cacheId = nextCache()
            item["artwork_cache_id"] = cacheId
            item["artwork_checksum"] = cover.length() + (if (a.ext == "mp3") 1 else 17)   // picture + its container
        } else item["artwork_checksum"] = 0
        val av = linkedMapOf<String, Any?>(
            "audio_format" to format, "sample_rate" to a.sampleRate, "bit_rate" to a.bitRateKbps,
            "channels" to a.channels, "duration" to a.samples
        )
        if (a.ext != "mp3") {
            av["gapless_encoding_delay"] = a.delay; av["gapless_encoding_drain"] = a.drain
            av["gapless_heuristic_info"] = 1; av["gapless_last_frame_resynch"] = 0
            if (a.codec == "alac") av["analysis_inhibit_flags"] = 2
        }
        val op = linkedMapOf<String, Any?>(
            "operation" to "insert_track", "pid" to pid, "item" to item,
            "item_stats" to linkedMapOf("play_count_user" to 0, "skip_count_user" to 0, "play_count_recent" to 0,
                "skip_count_recent" to 0, "has_been_played" to false),
            "avformat_info" to av, "location" to mapOf("kind" to kind)
        )
        return Prepared(pid, a, assetPath, op, cacheId)
    }

    private fun playlistOp(pid: Long, name: String, members: List<Long>): Map<String, Any?> {
        val now = Date((System.currentTimeMillis() / 1000) * 1000)
        return linkedMapOf(
            "operation" to "insert_playlist", "pid" to pid,
            "container" to linkedMapOf("name" to name, "date_created" to now, "date_modified" to now),
            "container_ui" to linkedMapOf("play_order" to 1, "album_field_order" to 0, "repeat_mode" to 0,
                "is_reversed" to false, "shuffle_items" to false, "has_been_shuffled" to false),
            "item_to_container" to members
        )
    }

    // ------------------------------------------------------------------ the session

    private fun editOp(e: TrackEdit, state: DeviceState): Map<String, Any?> {
        val now = Date((System.currentTimeMillis() / 1000) * 1000)
        val item = linkedMapOf<String, Any?>("title" to e.title, "sort_name" to e.title, "genre" to e.genre, "date_modified" to now)
        if (e.artist.isNotEmpty()) { item["artist"] = e.artist; item["sort_artist"] = e.artist }
        if (e.albumArtist.isNotEmpty()) { item["album_artist"] = e.albumArtist; item["sort_album_artist"] = e.albumArtist }
        val groupArtist = e.albumArtist.ifEmpty { e.artist }
        if (e.album.isNotEmpty()) {
            item["album"] = e.album; item["sort_album"] = e.album
            item["album_pid"] = state.albumPids.getOrPut(groupKey(e.album, groupArtist)) { DeviceState.randomPid() }
        }
        if (groupArtist.isNotEmpty()) item["artist_pid"] = state.artistPids.getOrPut(groupArtist.lowercase()) { DeviceState.randomPid() }
        return linkedMapOf("operation" to "update_track", "pid" to e.syncId, "item" to item)
    }

    fun run(adds: List<TouchAdd>, removeSyncIds: List<Long>, playlists: List<PlaylistChange>,
            edits: List<TrackEdit> = emptyList()): SyncResult {
        progress("Backing up the iPod database…", null)
        val afc = t.afc()
        try {
            backup(afc)
            progress("Reading the iPod library…", null)
            val dev = t.readLibrary(afc, File(work, "db"))
            log("device db: anchor ${dev.anchor} db_pid ${dev.dbPid} items ${dev.tracks.size}")

            // Library identity: continue the library this iPod belongs to, never start another.
            val st: DeviceState
            var firstSync = false
            val known = DeviceState.load(appDir, t.udid)
            if (known != null) {
                st = known
                if (dev.dbPid != null) {
                    if (dev.dbPid != st.dbPid) throw TouchSyncException(
                        "This iPod touch belongs to another music library (iTunes/Finder or another computer). MobileDigger won't take it over, because that could erase its music.")
                    st.initialized = true
                } else st.initialized = false
            } else {
                if (dev.dbPid != null) throw TouchSyncException(
                    "This iPod touch is synced from a computer library MobileDigger doesn't know yet. Import that library's identity (IpodDigger's device file) first.")
                st = DeviceState.fresh()
            }
            if (!st.initialized) firstSync = true
            dev.primaryContainerPid?.takeIf { it != 0L }?.let { st.primaryContainerPid = it }
            DeviceState.save(appDir, t.udid, st)

            // Inserts
            val taken = HashSet(dev.paths)
            val usedPids = dev.tracks.map { it.syncId }.toHashSet()
            var nextCache = maxOf(st.nextArtworkCacheId, dev.maxArtworkToken + 1, 100)
            val prepared = adds.map { add ->
                var pid = DeviceState.randomPid(); while (pid in usedPids) pid = DeviceState.randomPid(); usedPids += pid
                buildTrack(add.audio, pid, randomAssetPath(add.audio.ext, taken), st) { nextCache++ }
            }
            val deletePids = removeSyncIds.filter { it != 0L && (dev.tracks.any { t -> t.syncId == it } || dev.unfinished.any { t -> t.syncId == it }) }.distinct()

            // Playlists: delete before (re)insert; a Replace gets a fresh pid.
            val existingPlaylistPids = dev.playlists.map { it.id }.toSet()
            val playlistOps = ArrayList<Map<String, Any?>>()
            fun resolve(m: List<Member>) = m.mapNotNull {
                when (it) { is Member.Existing -> it.syncId; is Member.New -> prepared.getOrNull(it.addIndex)?.pid }
            }.filter { it != 0L && it !in deletePids }
            for (c in playlists) when (c) {
                is PlaylistChange.Delete -> if (c.pid in existingPlaylistPids) playlistOps += mapOf("operation" to "delete_playlist", "pid" to c.pid)
                is PlaylistChange.Replace -> {
                    if (c.pid in existingPlaylistPids) playlistOps += mapOf("operation" to "delete_playlist", "pid" to c.pid)
                    playlistOps += playlistOp(DeviceState.randomPid(), c.name, resolve(c.members))
                }
                is PlaylistChange.Create -> playlistOps += playlistOp(DeviceState.randomPid(), c.name, resolve(c.members))
            }
            val editOps = edits.filter { e -> e.syncId != 0L && dev.tracks.any { it.syncId == e.syncId } }.map { editOp(it, st) }
            if (prepared.isEmpty() && deletePids.isEmpty() && playlistOps.isEmpty() && editOps.isEmpty() && !firstSync) return SyncResult(0, 0, 0)

            val totalBytes = prepared.sumOf { it.a.file.length() + (it.a.cover?.length() ?: 0) }
            t.freeSpace()?.let { (free, _) ->
                if (totalBytes > 0 && free < totalBytes + (50L shl 20))
                    throw TouchSyncException("Not enough space on the iPod: needs ${totalBytes shr 20} MB, ${free shr 20} MB free.")
            }

            val ops = ArrayList<Map<String, Any?>>()
            ops += linkedMapOf("operation" to "update_db_info", "pid" to st.dbPid,
                "db_info" to mapOf("primary_container_pid" to st.primaryContainerPid))
            if (firstSync) {
                val now = Date((System.currentTimeMillis() / 1000) * 1000)
                ops += linkedMapOf("operation" to "insert_playlist", "pid" to st.primaryContainerPid,
                    "container" to linkedMapOf("name" to t.name, "is_hidden" to true, "date_created" to now, "date_modified" to now),
                    "container_ui" to linkedMapOf("play_order" to 1, "album_field_order" to 0, "repeat_mode" to 0,
                        "is_reversed" to false, "shuffle_items" to false, "has_been_shuffled" to false),
                    "item_to_container" to emptyList<Long>())
            }
            ops += prepared.map { it.op }
            ops += editOps
            ops += deletePids.map { mapOf("operation" to "delete_track", "pid" to it) }
            ops += playlistOps

            progress("Connecting to the iPod…", null)
            cleanStaging(afc, st, dev.anchor)
            DeviceState.save(appDir, t.udid, st)
            val atc = Atc(t.startService("com.apple.atc"))
            try {
                atc.send(mapOf("Command" to "Ping", "Session" to 1L))
                while (true) {
                    val m = atc.receive(RECV_TIMEOUT, "waiting for the iPod to allow syncing")
                    when (m["Command"]) {
                        "SyncAllowed" -> { checkAllowed(m); break }
                        "Ping" -> atc.pong(m, 1)
                        "SyncFailed" -> throw TouchSyncException("The iPod rejected the sync: ${reason(m)}")
                    }
                }
                atc.drain(700)
                var session = 2L
                atc.send(linkedMapOf("Command" to "RequestingSync", "Session" to session, "Params" to linkedMapOf(
                    "Dataclasses" to listOf("Media", "Book", "Keybag"),
                    "DataclassAnchors" to mapOf("Media" to dev.anchor.toString(), "VoiceMemo" to "1"),
                    "HostInfo" to linkedMapOf(
                        "Type" to "iTunes", "Version" to HOST_VERSION, "WindowsOSVersion" to "10.0",
                        "SyncHostName" to "MobileDigger", "LibraryID" to st.libraryId,
                        "SyncedDataclasses" to listOf("Media"), "SyncedAssetTypes" to listOf("Music"), "Wakeable" to 0,
                        "Grappa" to TouchSignature.hostGrappa
                    )
                )))
                var ready: Map<String, Any?>? = null
                while (ready == null) {
                    val m = atc.receive(RECV_TIMEOUT, "waiting for the iPod to get ready")
                    when (m["Command"]) {
                        "ReadyForSync" -> ready = m
                        "SyncFailed" -> throw TouchSyncException("The iPod rejected the sync: ${reason(m)}")
                        "SyncAllowed" -> checkAllowed(m)
                        "Ping" -> atc.pong(m, session)
                    }
                }
                session = Plist.long(ready["Session"]) ?: session
                val rp = params(ready)
                val anchor = Plist.long(Plist.dict(rp["DataclassAnchors"])["Media"]) ?: dev.anchor
                val grappa = Plist.dict(rp["DeviceInfo"])["Grappa"] as? ByteArray
                    ?: throw TouchSyncException("Unexpected reply from the iPod (no Grappa)")
                val newAnchor = anchor + 1
                log("ready: session $session anchor $anchor grappa ${grappa.size} bytes")

                val payload = Plist.toBinary(linkedMapOf("revision" to newAnchor, "timestamp" to Date(), "operations" to ops))
                val cig = TouchSignature.cig(payload, grappa) ?: throw TouchSyncException("Unexpected signature blob from the iPod")
                log("operations: ${ops.size} ops, ${payload.size} bytes, revision $newAnchor")
                afc.makeDirectory(STAGING)
                val plistPath = "$STAGING/Sync_00000000.plist"; val cigPath = "$plistPath.cig"
                st.staged = hashMapOf("Sync_00000000.plist" to sha256(payload), "Sync_00000000.plist.cig" to sha256(cig))
                DeviceState.save(appDir, t.udid, st)
                afc.write(payload, plistPath); afc.write(cig, cigPath)
                if (!afc.read(plistPath).contentEquals(payload) || !afc.read(cigPath).contentEquals(cig))
                    throw TouchSyncException("The sync file didn't read back identically from the iPod")

                atc.send(linkedMapOf("Command" to "FinishedSyncingMetadata", "Session" to session, "Params" to linkedMapOf(
                    "SyncTypes" to mapOf("Media" to 1, "Book" to 0, "Keybag" to 1),
                    "DataclassAnchors" to mapOf("Media" to newAnchor.toString(), "VoiceMemo" to "1")
                )))

                val own = prepared.associateBy { it.pid.toString() }
                val completed = HashSet<String>()
                var sent = 0L
                progress(if (prepared.isEmpty()) "Updating the iPod library…" else "Sending music…", if (prepared.isEmpty()) null else 0f)
                var finished = false
                while (!finished) {
                    val m = atc.receive(IMPORT_TIMEOUT, "syncing")
                    when (m["Command"]) {
                        "Ping" -> atc.pong(m, session)
                        "SyncFinished" -> finished = true
                        "SyncFailed" -> throw TouchSyncException("The iPod rejected the sync: ${reason(m)}")
                        "AssetManifest" -> {
                            val manifest = (Plist.dict(params(m)["AssetManifest"])["Media"] as? List<*>) ?: emptyList<Any?>()
                            for (e in manifest) {
                                val entry = Plist.dict(e)
                                val aid = Plist.str(entry["AssetID"]) ?: ""
                                val parts = Plist.long(Plist.dict(entry["Variant"])["AssetParts"])?.toInt() ?: 1
                                val p = own[aid]
                                if (p == null || parts and 3 == 0 || aid in completed) {
                                    log("declining $aid parts $parts")
                                    atc.send(linkedMapOf("Command" to "FileError", "Session" to session, "Params" to linkedMapOf(
                                        "AssetID" to aid, "Dataclass" to "Media", "ErrorCode" to 16, "ErrorIsVisible" to false)))
                                    continue
                                }
                                val cover = p.a.cover
                                if (parts and 2 != 0 && cover != null && p.cacheId != null) {
                                    afc.makeDirectory("/Airlock/Media/Artwork")
                                    val bytes = cover.readBytes()
                                    afc.write(bytes, "/Airlock/Media/Artwork/$aid")
                                    sent += bytes.size
                                }
                                val addIndex = prepared.indexOf(p)
                                onSending(addIndex)
                                if (parts and 1 != 0) {
                                    afc.makeDirectory("/Airlock/Media")
                                    val tmp = "/Airlock/Media/$aid.${p.a.ext}"
                                    val base = sent
                                    afc.upload(p.a.file, tmp) { n ->
                                        progress("Sending “${p.a.title}”…", ((base + n).toFloat() / maxOf(totalBytes, 1)).coerceIn(0f, 1f))
                                    }
                                    val size = afc.info(tmp)?.get("st_size")?.toLongOrNull() ?: -1
                                    if (size != p.a.file.length()) throw TouchSyncException("“${p.a.title}” arrived with $size of ${p.a.file.length()} bytes")
                                    runCatching { afc.remove("/Airlock/Media/$aid") }
                                    afc.rename(tmp, "/Airlock/Media/$aid")
                                    sent += p.a.file.length()
                                    atc.send(linkedMapOf("Command" to "FileComplete", "Session" to session, "Params" to linkedMapOf(
                                        "AssetID" to aid, "Dataclass" to "Media", "AssetPath" to p.assetPath)))
                                } else {
                                    atc.send(linkedMapOf("Command" to "FileComplete", "Session" to session, "Params" to linkedMapOf(
                                        "AssetID" to aid, "Dataclass" to "Media", "AssetPath" to "")))
                                }
                                completed += aid
                                onSent(addIndex)
                            }
                        }
                        "Progress" -> if (prepared.isEmpty()) Plist.dict(params(m))["OverallProgress"]?.let {
                            (it as? Number)?.toFloat()?.let { f -> progress("Updating the iPod library…", f.coerceIn(0f, 1f)) }
                        }
                    }
                }
                atc.drain(1500)
                log("session finished, anchor $anchor → $newAnchor")

                st.initialized = true
                st.lastAnchor = newAnchor
                st.nextArtworkCacheId = nextCache
                for (p in prepared) if (p.pid.toString() in completed)
                    st.tracks[p.pid.toString()] = DeviceState.TrackRecord(p.a.title, p.assetPath, p.cacheId, newAnchor)
                deletePids.forEach { st.tracks.remove(it.toString()) }
                DeviceState.save(appDir, t.udid, st)
                for (n in listOf("Sync_00000000.plist", "Sync_00000000.plist.cig")) runCatching {
                    val d = afc.read("$STAGING/$n")
                    if (sha256(d) == st.staged[n]) afc.remove("$STAGING/$n")
                }
                val missing = prepared.filter { it.pid.toString() !in completed }
                if (missing.isNotEmpty()) throw TouchSyncException(
                    "The sync finished but the iPod never asked for " + missing.joinToString { "“${it.a.title}”" })
                return SyncResult(prepared.size, deletePids.size, playlists.size)
            } finally { runCatching { atc.s.close() } }
        } finally {
            runCatching { afc.close() }
        }
    }
}
