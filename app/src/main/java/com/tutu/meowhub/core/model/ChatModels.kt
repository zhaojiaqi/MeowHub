package com.tutu.meowhub.core.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val role: String,
    val content: String,
    val actionData: String? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        const val ROLE_SYSTEM = "system"
    }
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val actionSteps: List<ActionStep> = emptyList(),
    val isStreaming: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class ActionStep(
    val index: Int,
    val actionType: String,
    val description: String,
    val status: ActionStepStatus = ActionStepStatus.PENDING,
    val detail: String = ""
)

enum class ActionStepStatus {
    PENDING, RUNNING, DONE, ERROR
}
