package com.tutu.meowhub.core.adb

import android.util.Log
import com.tutu.meowhub.core.adb.AdbProtocol.ADB_AUTH_RSAPUBLICKEY
import com.tutu.meowhub.core.adb.AdbProtocol.ADB_AUTH_SIGNATURE
import com.tutu.meowhub.core.adb.AdbProtocol.ADB_AUTH_TOKEN
import com.tutu.meowhub.core.adb.AdbProtocol.A_AUTH
import com.tutu.meowhub.core.adb.AdbProtocol.A_CLSE
import com.tutu.meowhub.core.adb.AdbProtocol.A_CNXN
import com.tutu.meowhub.core.adb.AdbProtocol.A_MAXDATA
import com.tutu.meowhub.core.adb.AdbProtocol.A_OKAY
import com.tutu.meowhub.core.adb.AdbProtocol.A_OPEN
import com.tutu.meowhub.core.adb.AdbProtocol.A_STLS
import com.tutu.meowhub.core.adb.AdbProtocol.A_STLS_VERSION
import com.tutu.meowhub.core.adb.AdbProtocol.A_VERSION
import com.tutu.meowhub.core.adb.AdbProtocol.A_WRTE
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.net.ssl.SSLSocket

private const val TAG = "AdbClient"

class AdbClient(
    private val host: String,
    private val port: Int,
    private val key: AdbKey
) : Closeable {

    private lateinit var socket: Socket
    private lateinit var plainInputStream: DataInputStream
    private lateinit var plainOutputStream: DataOutputStream

    private var useTls = false

    private lateinit var tlsSocket: SSLSocket
    private lateinit var tlsInputStream: DataInputStream
    private lateinit var tlsOutputStream: DataOutputStream

    private val inputStream get() = if (useTls) tlsInputStream else plainInputStream
    private val outputStream get() = if (useTls) tlsOutputStream else plainOutputStream

    private var nextLocalId = 1
    private var remoteMaxData = A_MAXDATA

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val SO_TIMEOUT_MS = 15_000
    }

    fun connect() {
        socket = Socket()
        socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
        socket.tcpNoDelay = true
        socket.soTimeout = SO_TIMEOUT_MS
        plainInputStream = DataInputStream(socket.getInputStream())
        plainOutputStream = DataOutputStream(socket.getOutputStream())

        write(A_CNXN, A_VERSION, A_MAXDATA, "host::")

        var message = read()
        if (message.command == A_STLS) {
            write(A_STLS, A_STLS_VERSION, 0)

            val sslContext = key.sslContext
            tlsSocket = sslContext.socketFactory.createSocket(socket, host, port, true) as SSLSocket
            tlsSocket.soTimeout = SO_TIMEOUT_MS
            tlsSocket.startHandshake()
            Log.d(TAG, "TLS handshake succeeded.")

            tlsInputStream = DataInputStream(tlsSocket.inputStream)
            tlsOutputStream = DataOutputStream(tlsSocket.outputStream)
            useTls = true

            message = read()
        } else if (message.command == A_AUTH) {
            if (message.arg0 != ADB_AUTH_TOKEN) error("not A_AUTH ADB_AUTH_TOKEN")
            write(A_AUTH, ADB_AUTH_SIGNATURE, 0, key.sign(message.data))

            message = read()
            if (message.command != A_CNXN) {
                write(A_AUTH, ADB_AUTH_RSAPUBLICKEY, 0, key.adbPublicKey)
                message = read()
            }
        }

        if (message.command != A_CNXN) error("not A_CNXN")
        remoteMaxData = message.arg1
        Log.d(TAG, "Remote maxdata=$remoteMaxData")
    }

    fun shellCommand(
        command: String,
        timeoutMs: Int = 0,
        listener: ((ByteArray) -> Unit)?
    ) {
        val localId = nextLocalId++
        val s = if (useTls) tlsSocket else socket
        val prevTimeout = s.soTimeout
        s.soTimeout = if (timeoutMs > 0) timeoutMs else 0

        write(A_OPEN, localId, 0, "shell:$command")

        try {
            var message = read()
            when (message.command) {
                A_OKAY -> {
                    while (true) {
                        message = read()
                        val remoteId = message.arg0
                        if (message.command == A_WRTE) {
                            if (message.data_length > 0) {
                                listener?.invoke(message.data!!)
                            }
                            write(A_OKAY, localId, remoteId)
                        } else if (message.command == A_CLSE) {
                            write(A_CLSE, localId, remoteId)
                            break
                        } else {
                            error("not A_WRTE or A_CLSE")
                        }
                    }
                }
                A_CLSE -> {
                    val remoteId = message.arg0
                    write(A_CLSE, localId, remoteId)
                }
                else -> error("not A_OKAY or A_CLSE")
            }
        } finally {
            s.soTimeout = prevTimeout
        }
    }

    private fun write(command: Int, arg0: Int, arg1: Int, data: ByteArray? = null) =
        write(AdbMessage(command, arg0, arg1, data))

    private fun write(command: Int, arg0: Int, arg1: Int, data: String) =
        write(AdbMessage(command, arg0, arg1, data))

    private fun write(message: AdbMessage) {
        outputStream.write(message.toByteArray())
        outputStream.flush()
        Log.d(TAG, "write ${message.toStringShort()}")
    }

    private fun read(): AdbMessage {
        val buffer = ByteBuffer.allocate(AdbMessage.HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN)

        inputStream.readFully(buffer.array(), 0, 24)

        val command = buffer.int
        val arg0 = buffer.int
        val arg1 = buffer.int
        val dataLength = buffer.int
        @Suppress("UNUSED_VARIABLE")
        val checksum = buffer.int
        @Suppress("UNUSED_VARIABLE")
        val magic = buffer.int
        val data: ByteArray? = if (dataLength > 0) {
            ByteArray(dataLength).also { inputStream.readFully(it, 0, dataLength) }
        } else {
            null
        }
        val message = AdbMessage(command, arg0, arg1, dataLength, checksum, magic, data)
        message.validateOrThrow()
        Log.d(TAG, "read ${message.toStringShort()}")
        return message
    }

    override fun close() {
        runCatching { plainInputStream.close() }
        runCatching { plainOutputStream.close() }
        runCatching { socket.close() }

        if (useTls) {
            runCatching { tlsInputStream.close() }
            runCatching { tlsOutputStream.close() }
            runCatching { tlsSocket.close() }
        }
    }
}
