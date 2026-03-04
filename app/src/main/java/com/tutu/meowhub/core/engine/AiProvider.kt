package com.tutu.meowhub.core.engine

/**
 * AI 能力抽象接口 —— 引擎只依赖此接口，不关心具体实现。
 */
interface AiProvider {

    data class AiMessage(val role: String, val content: String)

    /**
     * @param prompt       系统/用户指令
     * @param screenshotBase64  压缩后的截图 base64（可空）
     * @param uiNodesJson       UI 节点树 JSON（可空）
     * @param history      对话历史
     * @param onToken      流式 token 回调（用于实时 UI）
     * @return 完整的 AI 回复文本
     */
    suspend fun analyze(
        prompt: String,
        screenshotBase64: String? = null,
        uiNodesJson: String? = null,
        history: List<AiMessage> = emptyList(),
        onToken: ((String) -> Unit)? = null
    ): String
}
