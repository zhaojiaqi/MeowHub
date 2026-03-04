package com.tutu.miaohub.core.adb

/**
 * JNI bridge for SPAKE2-based ADB pairing.
 * Native implementation in jni/adb_pairing.cpp
 */
internal class PairingContext private constructor(private val nativePtr: Long) {

    val msg: ByteArray = nativeMsg(nativePtr)

    fun initCipher(theirMsg: ByteArray): Boolean = nativeInitCipher(nativePtr, theirMsg)
    fun encrypt(`in`: ByteArray): ByteArray? = nativeEncrypt(nativePtr, `in`)
    fun decrypt(`in`: ByteArray): ByteArray? = nativeDecrypt(nativePtr, `in`)
    fun destroy() = nativeDestroy(nativePtr)

    private external fun nativeMsg(nativePtr: Long): ByteArray
    private external fun nativeInitCipher(nativePtr: Long, theirMsg: ByteArray): Boolean
    private external fun nativeEncrypt(nativePtr: Long, inbuf: ByteArray): ByteArray?
    private external fun nativeDecrypt(nativePtr: Long, inbuf: ByteArray): ByteArray?
    private external fun nativeDestroy(nativePtr: Long)

    companion object {
        fun create(password: ByteArray): PairingContext? {
            val nativePtr = nativeConstructor(true, password)
            return if (nativePtr != 0L) PairingContext(nativePtr) else null
        }

        @JvmStatic
        private external fun nativeConstructor(isClient: Boolean, password: ByteArray): Long
    }
}
