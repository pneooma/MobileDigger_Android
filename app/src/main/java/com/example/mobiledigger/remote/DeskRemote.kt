package com.example.mobiledigger.remote

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.ParcelUuid
import com.example.mobiledigger.audio.MusicService
import com.example.mobiledigger.util.CrashLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * "Desk remote": lets a computer next to the phone control playback over Bluetooth Low Energy
 * (a Chrome/Edge page using Web Bluetooth). No network is involved.
 *
 * Security model:
 *  - Off unless the user switches it on; advertising stops when switched off.
 *  - A fresh 6-digit PIN is made each time it's switched on. A connection must send "pin:NNNNNN"
 *    before anything else; 5 wrong PINs drop the connection.
 *  - Only five commands exist (next, prev, play, like, dislike). They're handed to the app the
 *    same way the notification buttons are (package-only broadcasts), so the remote can do
 *    nothing the notification can't.
 *  - What goes back: "ok:<cmd>" acks and the title of the playing track (to show on the page).
 */
@SuppressLint("MissingPermission") // callers check BLUETOOTH_CONNECT / BLUETOOTH_ADVERTISE first
object DeskRemote {
    val SERVICE_UUID: UUID = UUID.fromString("8b7c0001-3f2a-4c1e-9d5b-6d6f62696c65")
    val CMD_UUID: UUID = UUID.fromString("8b7c0002-3f2a-4c1e-9d5b-6d6f62696c65")
    val STATE_UUID: UUID = UUID.fromString("8b7c0003-3f2a-4c1e-9d5b-6d6f62696c65")
    private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    data class State(
        val on: Boolean = false,
        val pin: String = "",
        /** Computers connected and unlocked with the PIN. */
        val unlocked: Int = 0,
        val error: String? = null,
        val lastCommand: String? = null
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var app: Context? = null
    private var server: BluetoothGattServer? = null
    private var stateChar: BluetoothGattCharacteristic? = null
    private val authed = ConcurrentHashMap.newKeySet<String>()
    private val subscribed = ConcurrentHashMap.newKeySet<String>()
    private val failures = ConcurrentHashMap<String, Int>()
    private val mtu = ConcurrentHashMap<String, Int>()
    private val devices = ConcurrentHashMap<String, BluetoothDevice>()
    @Volatile private var nowPlaying: String = ""

    /** Runtime permissions the remote needs (Android 12+). */
    val PERMISSIONS = arrayOf(
        android.Manifest.permission.BLUETOOTH_CONNECT,
        android.Manifest.permission.BLUETOOTH_ADVERTISE
    )

    fun start(context: Context): Boolean {
        if (_state.value.on) return true
        val ctx = context.applicationContext
        app = ctx
        val bm = ctx.getSystemService(BluetoothManager::class.java)
        val adapter = bm?.adapter
        if (adapter == null || !adapter.isEnabled) {
            _state.value = State(error = "Turn Bluetooth on first")
            return false
        }
        val advertiser = adapter.bluetoothLeAdvertiser ?: run {
            _state.value = State(error = "This phone can't advertise over Bluetooth LE")
            return false
        }
        return try {
            val gatt = bm.openGattServer(ctx, callback) ?: throw IllegalStateException("No GATT server")
            val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            val cmd = BluetoothGattCharacteristic(
                CMD_UUID,
                BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            val st = BluetoothGattCharacteristic(
                STATE_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                BluetoothGattCharacteristic.PERMISSION_READ
            ).apply {
                addDescriptor(BluetoothGattDescriptor(CCCD,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE))
            }
            service.addCharacteristic(cmd)
            service.addCharacteristic(st)
            gatt.addService(service)
            server = gatt
            stateChar = st

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)
                .setTimeout(0)
                .build()
            val data = AdvertiseData.Builder().addServiceUuid(ParcelUuid(SERVICE_UUID)).setIncludeDeviceName(false).build()
            val scan = AdvertiseData.Builder().setIncludeDeviceName(true).build()
            advertiser.startAdvertising(settings, data, scan, advertiseCallback)

            _state.value = State(on = true, pin = newPin())
            CrashLogger.log("DeskRemote", "Started")
            true
        } catch (e: Exception) {
            CrashLogger.log("DeskRemote", "Couldn't start", e)
            stop()
            _state.value = State(error = "Couldn't start: ${e.message}")
            false
        }
    }

    fun stop() {
        val ctx = app
        runCatching {
            ctx?.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        }
        runCatching { devices.values.forEach { server?.cancelConnection(it) } }
        runCatching { server?.close() }
        server = null
        stateChar = null
        authed.clear(); subscribed.clear(); failures.clear(); mtu.clear(); devices.clear()
        if (_state.value.on) CrashLogger.log("DeskRemote", "Stopped")
        _state.value = State()
    }

    /** Called by the app when the playing track changes; shown on the computer's page. */
    fun setNowPlaying(title: String) {
        if (title == nowPlaying) return
        nowPlaying = title
        broadcastState("np:$title")
    }

    private fun newPin(): String = "%06d".format(SecureRandom().nextInt(1_000_000))

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartFailure(errorCode: Int) {
            CrashLogger.log("DeskRemote", "Advertising failed: $errorCode")
            _state.value = _state.value.copy(error = "Bluetooth advertising failed (code $errorCode)")
        }
    }

    private fun send(device: BluetoothDevice, text: String) {
        val gatt = server ?: return
        val ch = stateChar ?: return
        if (device.address !in subscribed) return
        val max = (mtu[device.address] ?: 23) - 3
        val bytes = text.toByteArray(Charsets.UTF_8).let { if (it.size > max) it.copyOf(max) else it }
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) gatt.notifyCharacteristicChanged(device, ch, false, bytes)
            else {
                @Suppress("DEPRECATION")
                ch.value = bytes
                @Suppress("DEPRECATION")
                gatt.notifyCharacteristicChanged(device, ch, false)
            }
        }
    }

    private fun broadcastState(text: String) {
        devices.values.filter { it.address in authed }.forEach { send(it, text) }
    }

    private fun refreshCount() {
        _state.value = _state.value.copy(unlocked = authed.count { it in devices.keys })
    }

    private fun handle(device: BluetoothDevice, raw: String) {
        val text = raw.trim()
        val addr = device.address
        if (text.startsWith("pin:")) {
            if (text.removePrefix("pin:") == _state.value.pin && _state.value.pin.isNotEmpty()) {
                authed += addr; failures.remove(addr)
                refreshCount()
                send(device, "ok:pin")
                if (nowPlaying.isNotEmpty()) send(device, "np:$nowPlaying")
            } else {
                val n = (failures[addr] ?: 0) + 1
                failures[addr] = n
                send(device, "err:pin")
                CrashLogger.log("DeskRemote", "Wrong PIN ($n)")
                if (n >= 5) server?.cancelConnection(device)
            }
            return
        }
        if (addr !in authed) { send(device, "err:locked"); return }
        val action = when (text) {
            "next" -> MusicService.ACTION_NEXT
            "prev" -> MusicService.ACTION_PREVIOUS
            "play" -> MusicService.ACTION_PLAY_PAUSE
            "like" -> MusicService.ACTION_LIKE
            "dislike" -> MusicService.ACTION_DISLIKE
            else -> null
        }
        if (action == null) { send(device, "err:unknown"); return }
        app?.let { it.sendBroadcast(Intent(action).setPackage(it.packageName)) }
        _state.value = _state.value.copy(lastCommand = text)
        send(device, "ok:$text")
    }

    private val callback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) devices[device.address] = device
            else {
                devices.remove(device.address); authed.remove(device.address)
                subscribed.remove(device.address); mtu.remove(device.address)
            }
            refreshCount()
        }

        override fun onMtuChanged(device: BluetoothDevice, m: Int) { mtu[device.address] = m }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice, requestId: Int, characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?
        ) {
            val ok = characteristic.uuid == CMD_UUID && value != null && value.size <= 64
            if (responseNeeded) server?.sendResponse(device, requestId,
                if (ok) BluetoothGatt.GATT_SUCCESS else BluetoothGatt.GATT_FAILURE, offset, null)
            if (ok) handle(device, String(value!!, Charsets.UTF_8))
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, characteristic: BluetoothGattCharacteristic) {
            // Nothing readable without the PIN.
            val body = if (device.address in authed) "np:$nowPlaying" else "locked"
            val bytes = body.toByteArray(Charsets.UTF_8)
            val slice = if (offset < bytes.size) bytes.copyOfRange(offset, bytes.size) else ByteArray(0)
            server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, slice)
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice, requestId: Int, descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean, responseNeeded: Boolean, offset: Int, value: ByteArray?
        ) {
            if (descriptor.uuid == CCCD) {
                if (value != null && value.isNotEmpty() && value[0].toInt() != 0) subscribed += device.address
                else subscribed.remove(device.address)
            }
            if (responseNeeded) server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, null)
        }

        override fun onDescriptorReadRequest(device: BluetoothDevice, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor) {
            val v = if (device.address in subscribed) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                else BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, v)
        }
    }
}
