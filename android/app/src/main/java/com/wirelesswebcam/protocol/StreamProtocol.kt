package com.wirelesswebcam.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Ultra-Low Latency Binary Protocol Definition (Kotlin Implementation).
 *
 * Optimized for zero-allocation frame packing using a pre-allocated
 * thread-local header buffer to eliminate ByteBuffer.allocate() per frame.
 *
 * Wire Format (16 Bytes Header + NAL Payload):
 * [0..1]   Magic Bytes ("WC" -> 0x57, 0x43)
 * [2]      Protocol Version (0x01)
 * [3]      Frame Type Bitmask
 * [4..11]  Capture Timestamp in Microseconds (64-bit Long, Big-Endian)
 * [12..15] Payload Byte Count (32-bit Int, Big-Endian)
 * [16..N]  Raw H.264 Annex-B NAL Unit Data
 */
object StreamProtocol {
    val MAGIC: ByteArray = byteArrayOf('W'.code.toByte(), 'C'.code.toByte())
    const val VERSION: Byte = 0x01
    const val HEADER_SIZE: Int = 16

    object FrameType {
        const val UNKNOWN: Byte = 0x00
        const val SPS_PPS: Byte = 0x01
        const val KEYFRAME: Byte = 0x02
        const val P_FRAME: Byte = 0x04
    }

    // Pre-allocated header buffer per thread (avoids allocating 16-byte buffer 30-60x/sec)
    private val headerBuffer = ThreadLocal.withInitial {
        ByteBuffer.allocate(HEADER_SIZE).apply { order(ByteOrder.BIG_ENDIAN) }
    }

    /**
     * Serializes a frame payload into a binary wire-format packet.
     * Uses pre-allocated header buffer to minimize GC pressure.
     */
    fun packFrame(frameType: Byte, timestampUs: Long, payload: ByteArray): ByteArray {
        val header = headerBuffer.get()!!
        header.clear()
        header.put(MAGIC)
        header.put(VERSION)
        header.put(frameType)
        header.putLong(timestampUs)
        header.putInt(payload.size)

        val packet = ByteArray(HEADER_SIZE + payload.size)
        System.arraycopy(header.array(), 0, packet, 0, HEADER_SIZE)
        System.arraycopy(payload, 0, packet, HEADER_SIZE, payload.size)
        return packet
    }
}
