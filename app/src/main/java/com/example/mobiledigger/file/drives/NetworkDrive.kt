package com.example.mobiledigger.file.drives

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** How the app talks to a network drive. */
enum class DriveType(val label: String, val hint: String) {
    SMB("Shared folder (SMB)", "Windows / Mac sharing, most NAS boxes, router USB drives"),
    WEBDAV("WebDAV", "Synology, QNAP, TrueNAS, Nextcloud, ownCloud"),
    MOBILEDIGGER("MobileDigger server", "A Raspberry Pi or PC running the MobileDigger library server"),
    /** A drive plugged into the phone: address = granted folder (SAF tree URI), share = volume id. */
    USB("USB drive", "A hard drive, SSD or stick plugged into the phone");

    val isNetwork: Boolean get() = this != USB
}

/**
 * A saved network drive.
 *  - SMB:          address = host or IP (optional ":port"), share = share name
 *  - WEBDAV:       address = base URL, e.g. http://nas.local:5005/music
 *  - MOBILEDIGGER: address = host:port, password sent as X-Dashboard-Password
 * [folder] is the folder inside the drive that gets loaded ("" = everything).
 */
data class NetworkDrive(
    val id: String = UUID.randomUUID().toString().take(8),
    val name: String,
    val type: DriveType,
    val address: String,
    val share: String = "",
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    val folder: String = "",
    /**
     * MobileDigger server only: an SMB share on the same machine that holds the same library.
     * When set, deleting (Reject) and unpacking archives go through SMB; reading still uses the server.
     */
    val writeShare: String = "",
    val writeUser: String = "",
    val writePassword: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("type", type.name).put("address", address)
        .put("share", share).put("username", username).put("password", password)
        .put("domain", domain).put("folder", folder)
        .put("writeShare", writeShare).put("writeUser", writeUser).put("writePassword", writePassword)

    /** Name of the drive's top level as a folder: the granted folder on a USB drive, else the drive name. */
    val topName: String
        get() = if (type == DriveType.USB)
            UsbDrives.folderOf(android.net.Uri.parse(address)).substringAfterLast('/').ifBlank { name }
        else name

    /** Short "where is it" line for the UI (never shows the password). */
    val displayAddress: String
        get() = when (type) {
            DriveType.SMB -> "smb://${address.trim()}/${share.trim()}"
            DriveType.USB -> "Plugged into the phone"
            else -> address.trim()
        }

    companion object {
        fun fromJson(o: JSONObject) = NetworkDrive(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString().take(8) },
            name = o.optString("name"),
            type = runCatching { DriveType.valueOf(o.optString("type")) }.getOrDefault(DriveType.MOBILEDIGGER),
            address = o.optString("address"),
            share = o.optString("share"),
            username = o.optString("username"),
            password = o.optString("password"),
            domain = o.optString("domain"),
            folder = o.optString("folder"),
            writeShare = o.optString("writeShare"),
            writeUser = o.optString("writeUser"),
            writePassword = o.optString("writePassword")
        )
    }
}

/** Saved drives + which one is the active music source. Stored in the app's private prefs. */
class DriveStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mobiledigger_prefs", Context.MODE_PRIVATE)

    init { migrateLegacyPi() }

    fun all(): List<NetworkDrive> {
        val raw = prefs.getString(KEY_DRIVES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { NetworkDrive.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun get(id: String?): NetworkDrive? = id?.let { i -> all().firstOrNull { it.id == i } }

    fun save(drive: NetworkDrive) {
        val list = all().toMutableList()
        val idx = list.indexOfFirst { it.id == drive.id }
        if (idx >= 0) list[idx] = drive else list.add(drive)
        write(list)
    }

    fun delete(id: String) {
        write(all().filterNot { it.id == id })
        if (activeId() == id) setActive(null)
    }

    fun activeId(): String? = prefs.getString(KEY_ACTIVE, null)
    fun active(): NetworkDrive? = get(activeId())
    fun setActive(id: String?) { prefs.edit().putString(KEY_ACTIVE, id).apply() }

    private fun write(list: List<NetworkDrive>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_DRIVES, arr.toString()).apply()
    }

    /** One-time move of the old single "Pi" settings into a saved drive. */
    private fun migrateLegacyPi() {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        val host = prefs.getString("pi_host", null)
        if (!host.isNullOrBlank() && all().isEmpty()) {
            val pi = NetworkDrive(
                name = "Pi",
                type = DriveType.MOBILEDIGGER,
                address = host,
                password = prefs.getString("pi_password", null) ?: "",
                folder = prefs.getString("pi_path", null) ?: ""
            )
            write(listOf(pi))
            setActive(pi.id)
        }
        prefs.edit().remove("pi_host").remove("pi_password").remove("pi_path")
            .putBoolean(KEY_MIGRATED, true).apply()
    }

    companion object {
        private const val KEY_DRIVES = "network_drives"
        private const val KEY_ACTIVE = "network_drive_active"
        private const val KEY_MIGRATED = "network_drives_migrated_v1"
    }
}
