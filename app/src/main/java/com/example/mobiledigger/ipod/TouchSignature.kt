package com.example.mobiledigger.ipod

import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Signs ATC operation plists (Sync_XXXXXXXX.plist) with the 21-byte ".cig" the iPod verifies
 * ("GrappaVerify"). Ported from IpodDigger's TouchSignature.swift (itself from go-tunes cig.cpp, MIT).
 * R = device Grappa (83 bytes, from ReadyForSync), P = plist bytes:
 *  1. D = AES-128-CBC-decrypt(R[19..83], key 0x95×16, iv R[2..18]) → 64 bytes
 *  2. K = AES-128-ECB-encrypt(D[1..17] ⊕ D[39..55] ⊕ keyMask, derivKey)
 *  3. cig = 0x01 ‖ (HMAC-SHA1(K, P) ⊕ D[18..38] ⊕ macMask)
 */
object TouchSignature {
    /** Fixed 84-byte host Grappa announced in RequestingSync → HostInfo.Grappa. */
    val hostGrappa: ByteArray = intArrayOf(
        0x01, 0x01, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x11,
        0x11, 0x11, 0x04, 0x40, 0xbc, 0x27, 0x85, 0xe0, 0xdb, 0xf1, 0x66, 0x36, 0x1e, 0x07, 0x98, 0x0a,
        0x5e, 0xa4, 0x8d, 0xba, 0x95, 0xb3, 0xb8, 0xea, 0x26, 0x5d, 0x62, 0xae, 0xfe, 0xa5, 0x1b, 0xb7,
        0xb1, 0x90, 0xe0, 0xb7, 0x71, 0x26, 0x29, 0x0a, 0xd3, 0x9b, 0xb1, 0x3f, 0xec, 0xc0, 0x8c, 0x25,
        0xa9, 0x56, 0x1c, 0x51, 0x7a, 0xc1, 0x1e, 0x64, 0x90, 0x5d, 0xa0, 0x29, 0xe6, 0x1b, 0xdf, 0xd0,
        0xba, 0x22, 0xc3, 0x13
    ).map { it.toByte() }.toByteArray()

    private val wrapKey = ByteArray(16) { 0x95.toByte() }
    private val derivKey = intArrayOf(0x77, 0x76, 0xeb, 0x98, 0xab, 0x6c, 0xe6, 0x02,
        0xe8, 0xe8, 0x92, 0xe4, 0x1b, 0xa7, 0xe9, 0x15).map { it.toByte() }.toByteArray()
    private val keyMask = intArrayOf(0xc6, 0xc7, 0xc4, 0xc5, 0xca, 0xcb, 0xc8, 0xc9,
        0xce, 0xcf, 0xcc, 0xcd, 0xb2, 0xb3, 0xb0, 0xb1).map { it.toByte() }.toByteArray()
    private val macMask = intArrayOf(0xf9, 0xfa, 0xfb, 0xfc, 0xfd, 0xfe, 0xff, 0xf0, 0xf1, 0xf2,
        0xf3, 0xf4, 0xf5, 0xf6, 0xf7, 0xe8, 0xe9, 0xea, 0xeb, 0xec).map { it.toByte() }.toByteArray()

    fun cig(plist: ByteArray, deviceGrappa: ByteArray): ByteArray? {
        if (deviceGrappa.size < 83) return null
        val dec = Cipher.getInstance("AES/CBC/NoPadding")
        dec.init(Cipher.DECRYPT_MODE, SecretKeySpec(wrapKey, "AES"), IvParameterSpec(deviceGrappa.copyOfRange(2, 18)))
        val d = dec.doFinal(deviceGrappa.copyOfRange(19, 83))
        if (d.size != 64) return null
        val block = ByteArray(16) { i -> (d[1 + i].toInt() xor d[39 + i].toInt() xor keyMask[i].toInt()).toByte() }
        val ecb = Cipher.getInstance("AES/ECB/NoPadding")
        ecb.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derivKey, "AES"))
        val k = ecb.doFinal(block)
        val mac = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(k, "HmacSHA1")) }.doFinal(plist)
        val out = ByteArray(21)
        out[0] = 1
        for (i in 0 until 20) out[1 + i] = (mac[i].toInt() xor d[18 + i].toInt() xor macMask[i].toInt()).toByte()
        return out
    }
}
