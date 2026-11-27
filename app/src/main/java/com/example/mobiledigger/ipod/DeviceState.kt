package com.example.mobiledigger.ipod

import org.json.JSONObject
import java.io.File
import java.security.SecureRandom

/**
 * This phone's record of an iPod touch library it syncs (same JSON as IpodDigger's
 * ~/Music/IpodDigger/Devices/<udid>.json, so a Mac's file can be imported and both stay compatible).
 * The device binds its library to [libraryId] / [dbPid]; a host with a different identity would
 * be "another library", so this must never be regenerated for a device that already has ours.
 */
data class DeviceState(
    var libraryId: String,
    var dbPid: Long,
    var primaryContainerPid: Long,
    var initialized: Boolean,
    var lastAnchor: Long,
    var nextArtworkCacheId: Long,
    val albumPids: MutableMap<String, Long>,
    val artistPids: MutableMap<String, Long>,
    val tracks: MutableMap<String, TrackRecord>,
    var staged: MutableMap<String, String>,
    var migratedFrom: String?
) {
    data class TrackRecord(val title: String, val path: String, val artworkCacheId: Long?, val anchor: Long)

    fun toJson(): JSONObject = JSONObject().apply {
        put("libraryID", libraryId); put("dbPID", dbPid); put("primaryContainerPID", primaryContainerPid)
        put("initialized", initialized); put("lastAnchor", lastAnchor); put("nextArtworkCacheID", nextArtworkCacheId)
        put("albumPIDs", JSONObject(albumPids as Map<*, *>)); put("artistPIDs", JSONObject(artistPids as Map<*, *>))
        put("tracks", JSONObject().apply {
            tracks.forEach { (k, t) ->
                put(k, JSONObject().put("title", t.title).put("path", t.path).put("anchor", t.anchor)
                    .apply { t.artworkCacheId?.let { put("artworkCacheID", it) } })
            }
        })
        put("staged", JSONObject(staged as Map<*, *>))
        migratedFrom?.let { put("migratedFrom", it) }
    }

    companion object {
        private val rnd = SecureRandom()

        /** Random signed 64-bit PID with |pid| ≥ 1000 (same rule as IpodDigger). */
        fun randomPid(): Long {
            while (true) { val v = rnd.nextLong(); if (v >= 1000 || v < -1000) return v }
        }

        fun fresh() = DeviceState(
            "%016X".format(rnd.nextLong().let { if (it == 0L) 1L else it }), randomPid(), randomPid(), false, 0, 100,
            HashMap(), HashMap(), HashMap(), HashMap(), null
        )

        fun fromJson(o: JSONObject): DeviceState {
            fun longMap(j: JSONObject?) = HashMap<String, Long>().apply { j?.keys()?.forEach { put(it, j.getLong(it)) } }
            val tracks = HashMap<String, TrackRecord>()
            o.optJSONObject("tracks")?.let { j ->
                j.keys().forEach { k ->
                    val t = j.getJSONObject(k)
                    tracks[k] = TrackRecord(t.optString("title"), t.optString("path"),
                        if (t.has("artworkCacheID")) t.getLong("artworkCacheID") else null, t.optLong("anchor"))
                }
            }
            val staged = HashMap<String, String>()
            o.optJSONObject("staged")?.let { j -> j.keys().forEach { staged[it] = j.getString(it) } }
            return DeviceState(
                o.getString("libraryID"), o.getLong("dbPID"), o.getLong("primaryContainerPID"),
                o.optBoolean("initialized"), o.optLong("lastAnchor"), o.optLong("nextArtworkCacheID", 100),
                longMap(o.optJSONObject("albumPIDs")), longMap(o.optJSONObject("artistPIDs")), tracks, staged,
                o.optString("migratedFrom").takeIf { it.isNotEmpty() }
            )
        }

        fun file(appDir: File, udid: String) = File(appDir, "ipod/$udid.json")

        fun load(appDir: File, udid: String): DeviceState? = runCatching {
            val f = file(appDir, udid)
            if (f.exists()) fromJson(JSONObject(f.readText())) else null
        }.getOrNull()

        fun save(appDir: File, udid: String, st: DeviceState) {
            val f = file(appDir, udid)
            f.parentFile?.mkdirs()
            val tmp = File(f.path + ".tmp")
            tmp.writeText(st.toJson().toString(2))
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }
}
