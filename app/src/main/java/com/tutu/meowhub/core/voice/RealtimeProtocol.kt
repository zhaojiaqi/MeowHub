package com.tutu.meowhub.core.voice

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 豆包实时语音对话二进制协议工具类。
 *
 * 协议格式（每个帧）：
 *   Byte 0: [version:4bit][headerSize:4bit]  headerSize 单位 = 4 bytes
 *   Byte 1: [messageType:4bit][messageFlags:4bit]
 *   Byte 2: [serializationMethod:4bit][compressionType:4bit]
 *   Byte 3: reserved
 *   Byte 4..headerSize*4-1: extension（可选）
 *   之后是 payload 区域，格式取决于 messageType 和 messageFlags
 */
object RealtimeProtocol {

    // ========================== 协议版本 ==========================
    const val PROTOCOL_VERSION = 0b0001

    // ========================== 消息类型（messageType） ==========================
    /** 客户端完整请求（JSON payload + event） */
    const val CLIENT_FULL_REQUEST = 0b0001
    /** 客户端纯音频请求 */
    const val CLIENT_AUDIO_ONLY_REQUEST = 0b0010
    /** 服务端完整响应（JSON payload + event） */
    const val SERVER_FULL_RESPONSE = 0b1001
    /** 服务端 ACK（通常携带 TTS 音频二进制数据） */
    const val SERVER_ACK = 0b1011
    /** 服务端错误响应 */
    const val SERVER_ERROR_RESPONSE = 0b1111

    // ========================== 消息标志（messageFlags） ==========================
    /** 携带事件 ID（4 字节 eventId 紧跟在 header 之后） */
    const val MSG_WITH_EVENT = 0b0100
    /** 携带负序列号（4 字节，位于 event 之前） */
    const val NEG_SEQUENCE = 0b0010

    // ========================== 序列化方式 ==========================
    const val JSON_SERIAL = 0b0001
    const val NO_SERIALIZATION = 0b0000

    // ========================== 压缩方式 ==========================
    const val GZIP = 0b0001
    const val NO_COMPRESSION = 0b0000

    // ========================== 客户端事件 ID ==========================
    /** 建立连接 */
    const val EVT_START_CONNECTION = 1
    /** 结束连接 */
    const val EVT_FINISH_CONNECTION = 2
    /** 开始会话（携带 model/speaker/audio_config 等配置） */
    const val EVT_START_SESSION = 100
    /** 结束会话 */
    const val EVT_FINISH_SESSION = 102
    /** 音频数据帧 */
    const val EVT_AUDIO_TASK = 200
    /** 开场白 */
    const val EVT_SAY_HELLO = 300

    // ========================== 服务端事件 ID ==========================
    /** 连接建立成功 */
    const val SVR_CONNECTION_STARTED = 50
    /** 连接建立失败 */
    const val SVR_CONNECTION_FAILED = 51
    /** 连接结束 */
    const val SVR_CONNECTION_FINISHED = 52
    /** 会话启动成功 */
    const val SVR_SESSION_STARTED = 150
    /** 会话结束 */
    const val SVR_SESSION_FINISHED = 152
    /** 会话启动失败 */
    const val SVR_SESSION_FAILED = 153
    /** 用量统计 */
    const val SVR_USAGE_RESPONSE = 154
    /** TTS 句子开始 */
    const val SVR_TTS_SENTENCE_START = 350
    /** TTS 句子结束 */
    const val SVR_TTS_SENTENCE_END = 351
    /** TTS 音频数据（通过 SERVER_ACK 携带） */
    const val SVR_TTS_RESPONSE = 352
    /** TTS 播放结束 */
    const val SVR_TTS_ENDED = 359
    /** 用户开始说话（ASR 检测到语音活动） */
    const val SVR_ASR_INFO = 450
    /** ASR 识别结果（含中间/最终文本） */
    const val SVR_ASR_RESPONSE = 451
    /** ASR 识别结束 */
    const val SVR_ASR_ENDED = 459
    /** AI 对话回复文本（增量） */
    const val SVR_CHAT_RESPONSE = 550
    /** AI 对话回复结束 */
    const val SVR_CHAT_ENDED = 559
    /** 对话过程通用错误 */
    const val SVR_DIALOG_ERROR = 599

    // ========================== 帧构造 ==========================

    /**
     * 生成二进制帧头部。
     *
     * @param version       协议版本（4bit）
     * @param messageType   消息类型（4bit）
     * @param messageFlags  消息标志（4bit）
     * @param serialMethod  序列化方式（4bit）
     * @param compression   压缩方式（4bit）
     * @param reserved      保留字节
     * @param extension     可选扩展数据（长度须为 4 的倍数）
     */
    fun generateHeader(
        version: Int,
        messageType: Int,
        messageFlags: Int,
        serialMethod: Int,
        compression: Int,
        reserved: Int = 0,
        extension: ByteArray? = null
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        val headerSize = (extension?.size ?: 0) / 4 + 1
        baos.write((version shl 4) or (headerSize and 0x0F))
        baos.write((messageType shl 4) or (messageFlags and 0x0F))
        baos.write((serialMethod shl 4) or (compression and 0x0F))
        baos.write(reserved and 0xFF)
        if (extension != null && extension.isNotEmpty()) baos.write(extension)
        return baos.toByteArray()
    }

    /** 将 Int 转为 4 字节大端序字节数组 */
    fun intToBytes(value: Int): ByteArray = byteArrayOf(
        ((value ushr 24) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    /** 从字节数组指定偏移读取 4 字节大端序 Int */
    fun bytesToInt(b: ByteArray, offset: Int): Int =
        ((b[offset].toInt() and 0xFF) shl 24) or
        ((b[offset + 1].toInt() and 0xFF) shl 16) or
        ((b[offset + 2].toInt() and 0xFF) shl 8) or
        (b[offset + 3].toInt() and 0xFF)

    /** GZIP 压缩 */
    fun gzipCompress(data: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(data) }
        return baos.toByteArray()
    }

    /** GZIP 解压缩 */
    fun gzipDecompress(data: ByteArray): ByteArray {
        GZIPInputStream(ByteArrayInputStream(data)).use { gis ->
            val baos = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var len: Int
            while (gis.read(buffer).also { len = it } != -1) {
                baos.write(buffer, 0, len)
            }
            return baos.toByteArray()
        }
    }

    // ========================== 响应解析 ==========================

    data class ParsedResponse(
        val messageType: Int,
        val messageFlags: Int,
        val serializationMethod: Int,
        val compressionType: Int,
        val event: Int = 0,
        val errorCode: Int = 0,
        val responseSessionId: String? = null,
        val payloadBytes: ByteArray? = null,
        val payloadJson: String? = null
    )

    /**
     * 解析服务端返回的二进制帧。
     *
     * @return 解析结果，数据不足时返回 null
     */
    fun parseResponse(res: ByteArray): ParsedResponse? {
        if (res.size < 4) return null

        val headerSize = res[0].toInt() and 0x0F
        val messageType = (res[1].toInt() shr 4) and 0x0F
        val messageFlags = res[1].toInt() and 0x0F
        val serialMethod = (res[2].toInt() shr 4) and 0x0F
        val compression = res[2].toInt() and 0x0F
        val payloadStart = headerSize * 4
        var offset = payloadStart

        when (messageType) {
            SERVER_FULL_RESPONSE, SERVER_ACK -> {
                var event = 0
                var sessionId: String? = null

                if (messageFlags and NEG_SEQUENCE > 0) {
                    offset += 4
                }
                if (messageFlags and MSG_WITH_EVENT > 0) {
                    event = bytesToInt(res, offset)
                    offset += 4
                }

                val sidLen = bytesToInt(res, offset)
                offset += 4
                if (sidLen > 0 && offset + sidLen <= res.size) {
                    sessionId = String(res, offset, sidLen)
                }
                offset += maxOf(0, sidLen)

                val plLen = bytesToInt(res, offset)
                offset += 4
                var pl = ByteArray(maxOf(0, minOf(plLen, res.size - offset)))
                if (pl.isNotEmpty()) {
                    System.arraycopy(res, offset, pl, 0, pl.size)
                }
                if (compression == GZIP && pl.isNotEmpty()) {
                    pl = gzipDecompress(pl)
                }

                return if (serialMethod == JSON_SERIAL) {
                    ParsedResponse(messageType, messageFlags, serialMethod, compression,
                        event = event, responseSessionId = sessionId, payloadJson = String(pl))
                } else {
                    ParsedResponse(messageType, messageFlags, serialMethod, compression,
                        event = event, responseSessionId = sessionId, payloadBytes = pl)
                }
            }

            SERVER_ERROR_RESPONSE -> {
                val code = bytesToInt(res, offset)
                offset += 4
                val plLen = bytesToInt(res, offset)
                offset += 4
                var pl = ByteArray(maxOf(0, minOf(plLen, res.size - offset)))
                if (pl.isNotEmpty()) {
                    System.arraycopy(res, offset, pl, 0, pl.size)
                }
                if (compression == GZIP && pl.isNotEmpty()) {
                    pl = gzipDecompress(pl)
                }
                return ParsedResponse(messageType, messageFlags, serialMethod, compression,
                    errorCode = code, payloadJson = String(pl))
            }

            else -> {
                return ParsedResponse(messageType, messageFlags, serialMethod, compression)
            }
        }
    }

    /** 事件 ID 可读名称映射（调试用） */
    fun eventName(event: Int): String = when (event) {
        SVR_CONNECTION_STARTED -> "ConnectionStarted"
        SVR_CONNECTION_FAILED -> "ConnectionFailed"
        SVR_CONNECTION_FINISHED -> "ConnectionFinished"
        SVR_SESSION_STARTED -> "SessionStarted"
        SVR_SESSION_FINISHED -> "SessionFinished"
        SVR_SESSION_FAILED -> "SessionFailed"
        SVR_USAGE_RESPONSE -> "UsageResponse"
        SVR_TTS_SENTENCE_START -> "TTSSentenceStart"
        SVR_TTS_SENTENCE_END -> "TTSSentenceEnd"
        SVR_TTS_RESPONSE -> "TTSResponse"
        SVR_TTS_ENDED -> "TTSEnded"
        SVR_ASR_INFO -> "ASRInfo"
        SVR_ASR_RESPONSE -> "ASRResponse"
        SVR_ASR_ENDED -> "ASREnded"
        SVR_CHAT_RESPONSE -> "ChatResponse"
        SVR_CHAT_ENDED -> "ChatEnded"
        SVR_DIALOG_ERROR -> "DialogCommonError"
        else -> "Unknown($event)"
    }
}
