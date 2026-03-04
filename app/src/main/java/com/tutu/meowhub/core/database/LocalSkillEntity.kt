package com.tutu.meowhub.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "skills")
data class LocalSkillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val remoteId: Int? = null,
    val slug: String,
    val displayName: String,
    val displayNameEn: String = "",
    val description: String = "",
    val icon: String = "",
    val category: String = "",
    val version: String = "",
    val authorName: String = "",
    val estimatedTime: String = "",
    val estimatedTokens: Int = 0,
    val stepsJson: String = "[]",
    val rawJson: String = "{}",
    val source: String = SOURCE_LOCAL_IMPORT,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SOURCE_LOCAL_IMPORT = "local_import"
        const val SOURCE_CLIPBOARD = "clipboard"
        const val SOURCE_MARKET = "market"
    }
}

@Entity(
    tableName = "skill_tags",
    foreignKeys = [
        ForeignKey(
            entity = LocalSkillEntity::class,
            parentColumns = ["id"],
            childColumns = ["skillId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("skillId"), Index("tag")]
)
data class SkillTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val skillId: Long,
    val tag: String
)
