package com.example.engine.streaming

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

/**
 * Standard AMF0 and RTMP Protocol utilities for YouTube Live, Twitch, and custom RTMP/RTMPS ingests.
 */
object RtmpProtocol {

    const val DEFAULT_CHUNK_SIZE = 128
    const val TARGET_CHUNK_SIZE = 4096

    const val MSG_SET_CHUNK_SIZE = 1
    const val MSG_ABORT = 2
    const val MSG_ACK = 3
    const val MSG_USER_CONTROL = 4
    const val MSG_WINDOW_ACK_SIZE = 5
    const val MSG_SET_PEER_BANDWIDTH = 6
    const val MSG_AUDIO = 8
    const val MSG_VIDEO = 9
    const val MSG_DATA_AMF0 = 18
    const val MSG_COMMAND_AMF0 = 20

    const val CSID_PROTOCOL = 2
    const val CSID_COMMAND = 3
    const val CSID_VIDEO = 4
    const val CSID_AUDIO = 5
    const val CSID_DATA = 6

    /**
     * Performs standard RTMP C0/C1/C2 and S0/S1/S2 handshake with the server.
     */
    fun performHandshake(inputStream: InputStream, outputStream: OutputStream): Boolean {
        // Send C0 (1 byte: 0x03) + C1 (1536 bytes)
        val c0c1 = ByteArray(1537)
        c0c1[0] = 0x03.toByte() // C0: RTMP version 3

        // C1: 4 bytes timestamp (0), 4 bytes zero, 1528 bytes random
        c0c1[1] = 0; c0c1[2] = 0; c0c1[3] = 0; c0c1[4] = 0
        c0c1[5] = 0; c0c1[6] = 0; c0c1[7] = 0; c0c1[8] = 0
        val randomBytes = ByteArray(1528)
        Random.nextBytes(randomBytes)
        System.arraycopy(randomBytes, 0, c0c1, 9, 1528)

        outputStream.write(c0c1)
        outputStream.flush()

        // Read S0 (1 byte)
        val s0 = inputStream.read()
        if (s0 != 0x03) {
            throw java.io.IOException("Invalid RTMP S0 version received: $s0 (expected 3)")
        }

        // Read S1 (1536 bytes)
        val s1 = ByteArray(1536)
        readFully(inputStream, s1)

        // Send C2 (1536 bytes: echo of S1)
        outputStream.write(s1)
        outputStream.flush()

        // Read S2 (1536 bytes: echo of C1)
        val s2 = ByteArray(1536)
        readFully(inputStream, s2)

        return true
    }

    private fun readFully(inputStream: InputStream, buffer: ByteArray) {
        var totalRead = 0
        while (totalRead < buffer.size) {
            val read = inputStream.read(buffer, totalRead, buffer.size - totalRead)
            if (read == -1) {
                throw java.io.EOFException("Premature EOF reading RTMP handshake buffer (read $totalRead of ${buffer.size})")
            }
            totalRead += read
        }
    }

    /**
     * Writes an RTMP chunk packet to the output stream, breaking into sub-chunks of [chunkSize] if needed.
     */
    fun writeChunk(
        outputStream: OutputStream,
        csid: Int,
        timestamp: Int,
        messageType: Int,
        messageStreamId: Int,
        payload: ByteArray,
        chunkSize: Int = TARGET_CHUNK_SIZE
    ) {
        val payloadLen = payload.size
        var offset = 0

        // Type 0 Chunk Header (11 bytes header + basic header)
        val basicHeader = csid and 0x3F // Assuming CSID < 64 (2..6)
        outputStream.write(basicHeader)

        // Timestamp (3 bytes BE)
        val tsClamped = if (timestamp >= 0xFFFFFF) 0xFFFFFF else timestamp
        outputStream.write((tsClamped shr 16) and 0xFF)
        outputStream.write((tsClamped shr 8) and 0xFF)
        outputStream.write(tsClamped and 0xFF)

        // Message Length (3 bytes BE)
        outputStream.write((payloadLen shr 16) and 0xFF)
        outputStream.write((payloadLen shr 8) and 0xFF)
        outputStream.write(payloadLen and 0xFF)

        // Message Type (1 byte)
        outputStream.write(messageType and 0xFF)

        // Message Stream ID (4 bytes LE)
        outputStream.write(messageStreamId and 0xFF)
        outputStream.write((messageStreamId shr 8) and 0xFF)
        outputStream.write((messageStreamId shr 16) and 0xFF)
        outputStream.write((messageStreamId shr 24) and 0xFF)

        // Extended timestamp if needed
        if (tsClamped == 0xFFFFFF) {
            outputStream.write((timestamp shr 24) and 0xFF)
            outputStream.write((timestamp shr 16) and 0xFF)
            outputStream.write((timestamp shr 8) and 0xFF)
            outputStream.write(timestamp and 0xFF)
        }

        // First chunk payload
        val firstChunkSize = minOf(payloadLen, chunkSize)
        outputStream.write(payload, 0, firstChunkSize)
        offset += firstChunkSize

        // Subsequent chunks: Type 3 Chunk Header (1 byte: 0xC0 or csid)
        val type3Header = 0xC0 or (csid and 0x3F)
        while (offset < payloadLen) {
            outputStream.write(type3Header)
            if (tsClamped == 0xFFFFFF) {
                outputStream.write((timestamp shr 24) and 0xFF)
                outputStream.write((timestamp shr 16) and 0xFF)
                outputStream.write((timestamp shr 8) and 0xFF)
                outputStream.write(timestamp and 0xFF)
            }
            val thisChunkSize = minOf(payloadLen - offset, chunkSize)
            outputStream.write(payload, offset, thisChunkSize)
            offset += thisChunkSize
        }
        outputStream.flush()
    }

    /**
     * Creates Set Chunk Size protocol packet payload.
     */
    fun createSetChunkSizePayload(newChunkSize: Int): ByteArray {
        val buf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN)
        buf.putInt(newChunkSize and 0x7FFFFFFF)
        return buf.array()
    }

    private var currentInChunkSize = DEFAULT_CHUNK_SIZE
    private val channelHeaders = mutableMapOf<Int, RtmpHeaderState>()

    data class RtmpHeaderState(
        var timestamp: Int = 0,
        var length: Int = 0,
        var typeId: Int = 0,
        var streamId: Int = 0
    )

    fun resetProtocolState() {
        currentInChunkSize = DEFAULT_CHUNK_SIZE
        channelHeaders.clear()
    }

    /**
     * Reads a single complete RTMP message from the stream, tracking inbound chunk size and channel state.
     */
    fun readMessage(inputStream: InputStream): RtmpMessage? {
        val firstByte = inputStream.read()
        if (firstByte == -1) return null

        val fmt = (firstByte shr 6) and 0x03
        var csid = firstByte and 0x3F

        if (csid == 0) {
            val b = inputStream.read()
            if (b == -1) return null
            csid = 64 + b
        } else if (csid == 1) {
            val b1 = inputStream.read()
            val b2 = inputStream.read()
            if (b1 == -1 || b2 == -1) return null
            csid = 64 + b1 + (b2 shl 8)
        }

        val state = channelHeaders.getOrPut(csid) { RtmpHeaderState() }

        when (fmt) {
            0 -> {
                val b0 = inputStream.read(); val b1 = inputStream.read(); val b2 = inputStream.read()
                if (b0 == -1 || b1 == -1 || b2 == -1) return null
                state.timestamp = (b0 shl 16) or (b1 shl 8) or b2

                val l0 = inputStream.read(); val l1 = inputStream.read(); val l2 = inputStream.read()
                if (l0 == -1 || l1 == -1 || l2 == -1) return null
                state.length = (l0 shl 16) or (l1 shl 8) or l2

                val t = inputStream.read()
                if (t == -1) return null
                state.typeId = t

                val s0 = inputStream.read(); val s1 = inputStream.read(); val s2 = inputStream.read(); val s3 = inputStream.read()
                if (s0 == -1 || s1 == -1 || s2 == -1 || s3 == -1) return null
                state.streamId = (s3 shl 24) or (s2 shl 16) or (s1 shl 8) or s0
            }
            1 -> {
                val b0 = inputStream.read(); val b1 = inputStream.read(); val b2 = inputStream.read()
                if (b0 == -1 || b1 == -1 || b2 == -1) return null
                state.timestamp = (b0 shl 16) or (b1 shl 8) or b2

                val l0 = inputStream.read(); val l1 = inputStream.read(); val l2 = inputStream.read()
                if (l0 == -1 || l1 == -1 || l2 == -1) return null
                state.length = (l0 shl 16) or (l1 shl 8) or l2

                val t = inputStream.read()
                if (t == -1) return null
                state.typeId = t
            }
            2 -> {
                val b0 = inputStream.read(); val b1 = inputStream.read(); val b2 = inputStream.read()
                if (b0 == -1 || b1 == -1 || b2 == -1) return null
                state.timestamp = (b0 shl 16) or (b1 shl 8) or b2
            }
            3 -> {
                // Continue with previous header state
            }
        }

        if (state.length <= 0) return null

        val payload = ByteArray(state.length)
        var offset = 0
        while (offset < state.length) {
            val remainingInChunk = minOf(state.length - offset, currentInChunkSize)
            readFully(inputStream, payload, offset, remainingInChunk)
            offset += remainingInChunk
            if (offset < state.length) {
                // Consume continuation chunk header (fmt = 3)
                val contHeader = inputStream.read()
                if (contHeader == -1) break
                val contCsid = contHeader and 0x3F
                if (contCsid == 0) inputStream.read()
                else if (contCsid == 1) { inputStream.read(); inputStream.read() }
            }
        }

        // Handle Set Chunk Size message from server internally
        if (state.typeId == MSG_SET_CHUNK_SIZE && payload.size >= 4) {
            val newSize = ((payload[0].toInt() and 0x7F) shl 24) or
                    ((payload[1].toInt() and 0xFF) shl 16) or
                    ((payload[2].toInt() and 0xFF) shl 8) or
                    (payload[3].toInt() and 0xFF)
            if (newSize > 0) {
                currentInChunkSize = newSize
            }
        }

        return RtmpMessage(
            csid = csid,
            messageType = state.typeId,
            streamId = state.streamId,
            timestamp = state.timestamp,
            payload = payload
        )
    }

    private fun readFully(inputStream: InputStream, buffer: ByteArray, offset: Int, length: Int) {
        var totalRead = 0
        while (totalRead < length) {
            val read = inputStream.read(buffer, offset + totalRead, length - totalRead)
            if (read == -1) throw java.io.EOFException("Premature EOF reading chunk data (got $totalRead of $length)")
            totalRead += read
        }
    }
}

data class RtmpMessage(
    val csid: Int,
    val messageType: Int,
    val streamId: Int,
    val timestamp: Int,
    val payload: ByteArray
)

/**
 * AMF0 Writer for RTMP command & metadata serialization.
 */
class Amf0Writer {
    private val baos = ByteArrayOutputStream()
    private val dos = DataOutputStream(baos)

    fun writeNumber(value: Double): Amf0Writer {
        dos.writeByte(0x00) // Type: Number
        dos.writeDouble(value)
        return this
    }

    fun writeBoolean(value: Boolean): Amf0Writer {
        dos.writeByte(0x01) // Type: Boolean
        dos.writeByte(if (value) 1 else 0)
        return this
    }

    fun writeString(value: String): Amf0Writer {
        val bytes = value.toByteArray(Charsets.UTF_8)
        if (bytes.size < 65536) {
            dos.writeByte(0x02) // Type: String
            dos.writeShort(bytes.size)
            dos.write(bytes)
        } else {
            dos.writeByte(0x0C) // Type: Long string
            dos.writeInt(bytes.size)
            dos.write(bytes)
        }
        return this
    }

    fun writeNull(): Amf0Writer {
        dos.writeByte(0x05) // Type: Null
        return this
    }

    fun writeObject(properties: Map<String, Any?>): Amf0Writer {
        dos.writeByte(0x03) // Type: Object
        for ((key, value) in properties) {
            writeKey(key)
            writeValue(value)
        }
        // Object end marker: empty key (0 length) + 0x09
        dos.writeShort(0)
        dos.writeByte(0x09)
        return this
    }

    fun writeEcmaArray(properties: Map<String, Any?>): Amf0Writer {
        dos.writeByte(0x08) // Type: ECMA Array
        dos.writeInt(properties.size)
        for ((key, value) in properties) {
            writeKey(key)
            writeValue(value)
        }
        // Array end marker: 0x00, 0x00, 0x09
        dos.writeShort(0)
        dos.writeByte(0x09)
        return this
    }

    private fun writeKey(key: String) {
        val bytes = key.toByteArray(Charsets.UTF_8)
        dos.writeShort(bytes.size)
        dos.write(bytes)
    }

    private fun writeValue(value: Any?) {
        when (value) {
            null -> writeNull()
            is Double -> writeNumber(value)
            is Float -> writeNumber(value.toDouble())
            is Int -> writeNumber(value.toDouble())
            is Long -> writeNumber(value.toDouble())
            is Boolean -> writeBoolean(value)
            is String -> writeString(value)
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                writeObject(value as Map<String, Any?>)
            }
            else -> writeString(value.toString())
        }
    }

    fun toByteArray(): ByteArray {
        dos.flush()
        return baos.toByteArray()
    }
}

/**
 * AMF0 Reader for parsing responses from YouTube/RTMP servers.
 */
class Amf0Reader(private val bytes: ByteArray) {
    private val buffer = ByteBuffer.wrap(bytes)

    fun readNext(): Any? {
        if (!buffer.hasRemaining()) return null
        val type = buffer.get().toInt() and 0xFF
        return when (type) {
            0x00 -> buffer.double
            0x01 -> buffer.get().toInt() != 0
            0x02 -> readStringUtf8()
            0x03 -> readObjectProperties()
            0x05 -> null // Null
            0x08 -> {
                buffer.int // Skip count
                readObjectProperties()
            }
            0x0C -> readLongStringUtf8()
            else -> null
        }
    }

    private fun readStringUtf8(): String {
        val len = buffer.short.toInt() and 0xFFFF
        val strBytes = ByteArray(len)
        buffer.get(strBytes)
        return String(strBytes, Charsets.UTF_8)
    }

    private fun readLongStringUtf8(): String {
        val len = buffer.int
        val strBytes = ByteArray(len)
        buffer.get(strBytes)
        return String(strBytes, Charsets.UTF_8)
    }

    private fun readObjectProperties(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        while (buffer.hasRemaining()) {
            val keyLen = buffer.short.toInt() and 0xFFFF
            if (keyLen == 0) {
                val endMarker = buffer.get().toInt() and 0xFF
                if (endMarker == 0x09) break
            }
            val keyBytes = ByteArray(keyLen)
            buffer.get(keyBytes)
            val key = String(keyBytes, Charsets.UTF_8)
            val value = readNext()
            map[key] = value
        }
        return map
    }
}
