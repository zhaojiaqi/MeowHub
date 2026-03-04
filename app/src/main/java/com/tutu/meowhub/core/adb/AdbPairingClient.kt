package com.tutu.meowhub.core.adb

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.lang.reflect.Method
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.net.ssl.SSLSocket

private const val TAG = "AdbPairClient"

private const val kCurrentKeyHeaderVersion = 1.toByte()
private const val kMinSupportedKeyHeaderVersion = 1.toByte()
private const val kMaxSupportedKeyHeaderVersion = 1.toByte()
private const val kMaxPeerInfoSize = 8192
private const val kMaxPayloadSize = kMaxPeerInfoSize * 2

private const val kExportedKeyLabel = "adb-label\u0000"
private const val kExportedKeySize = 64

private const val kPairingPacketHeaderSize = 6

private class PeerInfo(
    val type: Byte,
    data: ByteArray
) {
    val data = ByteArray(kMaxPeerInfoSize - 1)

    init {
        data.copyInto(this.data, 0, 0, data.size.coerceAtMost(kMaxPeerInfoSize - 1))
    }

    enum class Type(val value: Byte) {
        ADB_RSA_PUB_KEY(0.toByte()),
        @Suppress("unused")
        ADB_DEVICE_GUID(0.toByte()),
    }

    fun writeTo(buffer: ByteBuffer) {
        buffer.put(type)
        buffer.put(data)
        Log.d(TAG, "write PeerInfo type=$type")
    }

    companion object {
        fun readFrom(buffer: ByteBuffer): PeerInfo {
            val type = buffer.get()
            val data = ByteArray(kMaxPeerInfoSize - 1)
            buffer.get(data)
            return PeerInfo(type, data)
        }
    }
}

private class PairingPacketHeader(
    val version: Byte,
    val type: Byte,
    val payload: Int
) {
    enum class Type(val value: Byte) {
        SPAKE2_MSG(0.toByte()),
        PEER_INFO(1.toByte())
    }

    fun writeTo(buffer: ByteBuffer) {
        buffer.put(version)
        buffer.put(type)
        buffer.putInt(payload)
        Log.d(TAG, "write PairingPacketHeader version=$version, type=$type, payload=$payload")
    }

    companion object {
        fun readFrom(buffer: ByteBuffer): PairingPacketHeader? {
            val version = buffer.get()
            val type = buffer.get()
            val payload = buffer.int

            if (version < kMinSupportedKeyHeaderVersion || version > kMaxSupportedKeyHeaderVersion) {
                Log.e(TAG, "PairingPacketHeader version mismatch (us=$kCurrentKeyHeaderVersion them=$version)")
                return null
            }
            if (type != Type.SPAKE2_MSG.value && type != Type.PEER_INFO.value) {
                Log.e(TAG, "Unknown PairingPacket type=$type")
                return null
            }
            if (payload <= 0 || payload > kMaxPayloadSize) {
                Log.e(TAG, "header payload not within a safe payload size (size=$payload)")
                return null
            }

            Log.d(TAG, "read PairingPacketHeader version=$version, type=$type, payload=$payload")
            return PairingPacketHeader(version, type, payload)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.R)
class AdbPairingClient(
    private val host: String,
    private val port: Int,
    private val pairCode: String,
    private val key: AdbKey
) : Closeable {

    private enum class State { Ready, ExchangingMsgs, ExchangingPeerInfo, Stopped }

    private lateinit var socket: Socket
    private lateinit var inputStream: DataInputStream
    private lateinit var outputStream: DataOutputStream

    private val peerInfo = PeerInfo(PeerInfo.Type.ADB_RSA_PUB_KEY.value, key.adbPublicKey)
    private lateinit var pairingContext: PairingContext
    private var state = State.Ready

    fun start(): Boolean {
        setupTlsConnection()

        state = State.ExchangingMsgs
        if (!doExchangeMsgs()) {
            state = State.Stopped
            return false
        }

        state = State.ExchangingPeerInfo
        if (!doExchangePeerInfo()) {
            state = State.Stopped
            return false
        }

        state = State.Stopped
        return true
    }

    private fun setupTlsConnection() {
        socket = Socket(host, port)
        socket.tcpNoDelay = true

        val sslContext = key.sslContext
        val sslSocket = sslContext.socketFactory.createSocket(socket, host, port, true) as SSLSocket
        sslSocket.startHandshake()
        Log.d(TAG, "TLS handshake succeeded.")

        inputStream = DataInputStream(sslSocket.inputStream)
        outputStream = DataOutputStream(sslSocket.outputStream)

        val pairCodeBytes = pairCode.toByteArray()
        val keyMaterial = exportKeyingMaterial(sslSocket, kExportedKeyLabel, null, kExportedKeySize)
        val passwordBytes = ByteArray(pairCode.length + keyMaterial.size)
        pairCodeBytes.copyInto(passwordBytes)
        keyMaterial.copyInto(passwordBytes, pairCodeBytes.size)

        val ctx = PairingContext.create(passwordBytes)
            ?: error("Unable to create PairingContext.")
        this.pairingContext = ctx
    }

    private fun createHeader(type: PairingPacketHeader.Type, payloadSize: Int) =
        PairingPacketHeader(kCurrentKeyHeaderVersion, type.value, payloadSize)

    private fun readHeader(): PairingPacketHeader? {
        val bytes = ByteArray(kPairingPacketHeaderSize)
        inputStream.readFully(bytes)
        return PairingPacketHeader.readFrom(ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN))
    }

    private fun writeHeader(header: PairingPacketHeader, payload: ByteArray) {
        val buffer = ByteBuffer.allocate(kPairingPacketHeaderSize).order(ByteOrder.BIG_ENDIAN)
        header.writeTo(buffer)
        outputStream.write(buffer.array())
        outputStream.write(payload)
    }

    private fun doExchangeMsgs(): Boolean {
        val msg = pairingContext.msg
        writeHeader(createHeader(PairingPacketHeader.Type.SPAKE2_MSG, msg.size), msg)

        val theirHeader = readHeader() ?: return false
        if (theirHeader.type != PairingPacketHeader.Type.SPAKE2_MSG.value) return false

        val theirMessage = ByteArray(theirHeader.payload)
        inputStream.readFully(theirMessage)

        return pairingContext.initCipher(theirMessage)
    }

    private fun doExchangePeerInfo(): Boolean {
        val buf = ByteBuffer.allocate(kMaxPeerInfoSize).order(ByteOrder.BIG_ENDIAN)
        peerInfo.writeTo(buf)

        val outbuf = pairingContext.encrypt(buf.array()) ?: return false

        writeHeader(createHeader(PairingPacketHeader.Type.PEER_INFO, outbuf.size), outbuf)

        val theirHeader = readHeader() ?: return false
        if (theirHeader.type != PairingPacketHeader.Type.PEER_INFO.value) return false

        val theirMessage = ByteArray(theirHeader.payload)
        inputStream.readFully(theirMessage)

        val decrypted = pairingContext.decrypt(theirMessage)
            ?: throw AdbInvalidPairingCodeException()
        if (decrypted.size != kMaxPeerInfoSize) {
            Log.e(TAG, "Got size=${decrypted.size} PeerInfo.size=$kMaxPeerInfoSize")
            return false
        }
        val theirPeerInfo = PeerInfo.readFrom(ByteBuffer.wrap(decrypted))
        Log.d(TAG, "Peer info type=${theirPeerInfo.type}")
        return true
    }

    override fun close() {
        runCatching { inputStream.close() }
        runCatching { outputStream.close() }
        runCatching { socket.close() }
        if (state != State.Ready) pairingContext.destroy()
    }

    companion object {
        init {
            System.loadLibrary("adb")
        }

        fun available(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

        /**
         * Call com.android.org.conscrypt.Conscrypt.exportKeyingMaterial() via reflection
         * since it's a hidden/internal Android API.
         */
        private var exportMethod: Method? = null

        private fun exportKeyingMaterial(
            sslSocket: SSLSocket,
            label: String,
            context: ByteArray?,
            length: Int
        ): ByteArray {
            if (exportMethod == null) {
                val conscryptClass = Class.forName("com.android.org.conscrypt.Conscrypt")
                exportMethod = conscryptClass.getMethod(
                    "exportKeyingMaterial",
                    SSLSocket::class.java,
                    String::class.java,
                    ByteArray::class.java,
                    Int::class.javaPrimitiveType
                )
            }
            return exportMethod!!.invoke(null, sslSocket, label, context, length) as ByteArray
        }
    }
}
