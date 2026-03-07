package com.tutu.meowhub.feature.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tutu.meowhub.MeowApp
import com.tutu.meowhub.core.engine.AiProvider
import com.tutu.meowhub.core.engine.SocketCommandBridge
import com.tutu.meowhub.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val app = MeowApp.instance
    private val chatDao = app.database.chatDao()
    private val bridge = SocketCommandBridge(app.tutuClient, app.deviceCache)

    private val chatAgent = ChatAgent(
        bridge = bridge,
        aiProviderFactory = { app.resolveAiProvider() },
        apiClient = app.apiClient,
        deviceCache = app.deviceCache,
        context = application
    )

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    val agentState = chatAgent.agentState
    val currentActionLabel = chatAgent.currentActionLabel

    private var conversationHistory = mutableListOf<AiProvider.AiMessage>()
    private var cachedSkills: List<ChatAgent.SkillInfo> = emptyList()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            loadOrCreateSession()
            fetchAvailableSkills()
        }
    }

    private suspend fun loadOrCreateSession() {
        val sessions = chatDao.getAllSessions()
        var latestSession: ChatSessionEntity? = null
        sessions.collect { list ->
            if (latestSession == null) {
                latestSession = list.firstOrNull()
                if (latestSession == null) {
                    val newSession = ChatSessionEntity()
                    chatDao.insertSession(newSession)
                    _currentSessionId.value = newSession.id
                } else {
                    _currentSessionId.value = latestSession!!.id
                    loadMessages(latestSession!!.id)
                }
            }
        }
    }

    private suspend fun loadMessages(sessionId: String) {
        val entities = chatDao.getMessagesList(sessionId)
        val msgs = entities.map { entity ->
            ChatMessage(
                id = entity.id,
                role = entity.role,
                content = entity.content,
                timestamp = entity.timestamp
            )
        }
        _messages.value = msgs
        conversationHistory = entities.filter { it.role != ChatMessageEntity.ROLE_SYSTEM }.map {
            AiProvider.AiMessage(it.role, it.content)
        }.toMutableList()
    }

    private suspend fun fetchAvailableSkills() {
        try {
            val result = app.apiClient.fetchSkillList(sort = "featured", limit = 20)
            val list = result.getOrNull()
            if (list != null) {
                cachedSkills = list.list.map {
                    ChatAgent.SkillInfo(it.slug, it.displayName, it.description)
                }
            }
        } catch (_: Exception) {}
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _isProcessing.value) return

        viewModelScope.launch(Dispatchers.IO) {
            _isProcessing.value = true
            val sessionId = _currentSessionId.value ?: return@launch

            val userMsg = ChatMessage(
                role = ChatMessageEntity.ROLE_USER,
                content = text
            )
            _messages.value = _messages.value + userMsg

            chatDao.insertMessage(
                ChatMessageEntity(
                    id = userMsg.id,
                    sessionId = sessionId,
                    role = ChatMessageEntity.ROLE_USER,
                    content = text
                )
            )

            if (_messages.value.count { it.role == ChatMessageEntity.ROLE_USER } == 1) {
                chatDao.updateSessionTitle(sessionId, text.take(30))
            }

            val aiMsgId = UUID.randomUUID().toString()
            val streamingMsg = ChatMessage(
                id = aiMsgId,
                role = ChatMessageEntity.ROLE_ASSISTANT,
                content = "",
                isStreaming = true
            )
            _messages.value = _messages.value + streamingMsg

            val tokenBuffer = StringBuilder()
            val actionSteps = mutableListOf<ActionStep>()

            chatAgent.chat(
                userMessage = text,
                conversationHistory = conversationHistory.toList(),
                availableSkills = cachedSkills,
                onToken = { token ->
                    tokenBuffer.append(token)
                    updateStreamingMessage(aiMsgId, tokenBuffer.toString(), actionSteps.toList())
                },
                onActionStep = { step ->
                    val idx = actionSteps.indexOfFirst { it.index == step.index }
                    if (idx >= 0) actionSteps[idx] = step else actionSteps.add(step)
                    updateStreamingMessage(aiMsgId, tokenBuffer.toString(), actionSteps.toList())
                },
                onComplete = { finalContent ->
                    val displayContent = extractDisplayText(finalContent)
                    val finalMsg = ChatMessage(
                        id = aiMsgId,
                        role = ChatMessageEntity.ROLE_ASSISTANT,
                        content = displayContent,
                        actionSteps = actionSteps.toList(),
                        isStreaming = false
                    )
                    _messages.value = _messages.value.map {
                        if (it.id == aiMsgId) finalMsg else it
                    }

                    viewModelScope.launch(Dispatchers.IO) {
                        chatDao.insertMessage(
                            ChatMessageEntity(
                                id = aiMsgId,
                                sessionId = sessionId,
                                role = ChatMessageEntity.ROLE_ASSISTANT,
                                content = displayContent,
                                actionData = if (actionSteps.isNotEmpty()) actionSteps.joinToString("\n") { "${it.actionType}: ${it.description}" } else null
                            )
                        )
                        chatDao.updateSession(
                            ChatSessionEntity(id = sessionId, title = text.take(30), updatedAt = System.currentTimeMillis())
                        )
                    }

                    conversationHistory.add(AiProvider.AiMessage("user", text))
                    conversationHistory.add(AiProvider.AiMessage("assistant", displayContent))
                    _isProcessing.value = false
                }
            )
        }
    }

    private fun updateStreamingMessage(msgId: String, content: String, steps: List<ActionStep>) {
        val displayContent = extractDisplayText(content)
        _messages.value = _messages.value.map {
            if (it.id == msgId) it.copy(content = displayContent, actionSteps = steps, isStreaming = true)
            else it
        }
    }

    private fun extractDisplayText(text: String): String {
        var result = text
        val thoughtMatch = Regex("Thought:\\s*(.+?)(?=\\s*Action:|$)", RegexOption.DOT_MATCHES_ALL).find(result)
        val actionMatch = Regex("Action:\\s*.+", RegexOption.DOT_MATCHES_ALL).find(result)

        if (actionMatch != null) {
            result = result.substring(0, actionMatch.range.first).trim()
        }
        result = result.replace(Regex("^Thought:\\s*", RegexOption.MULTILINE), "").trim()

        val finishedMatch = Regex("finished\\s*\\(\\s*content\\s*=\\s*'([^']*)'\\s*\\)").find(text)
        if (finishedMatch != null) {
            val finishedContent = finishedMatch.groupValues[1].trim()
            if (finishedContent.isNotEmpty()) return finishedContent
        }

        return result.ifEmpty { text }
    }

    fun startNewChat() {
        viewModelScope.launch(Dispatchers.IO) {
            chatAgent.stop()
            _isProcessing.value = false

            val newSession = ChatSessionEntity()
            chatDao.insertSession(newSession)
            _currentSessionId.value = newSession.id
            _messages.value = emptyList()
            conversationHistory.clear()
        }
    }

    fun stopAgent() {
        chatAgent.stop()
        _isProcessing.value = false
    }

    override fun onCleared() {
        super.onCleared()
        chatAgent.stop()
    }
}
