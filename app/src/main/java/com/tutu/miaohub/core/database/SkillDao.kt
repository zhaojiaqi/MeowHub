package com.tutu.miaohub.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SkillDao {

    @Query("SELECT * FROM skills ORDER BY updatedAt DESC")
    fun getAllSkills(): Flow<List<LocalSkillEntity>>

    @Query("""
        SELECT * FROM skills 
        WHERE id IN (SELECT skillId FROM skill_tags WHERE tag = :tag) 
        ORDER BY updatedAt DESC
    """)
    fun getSkillsByTag(tag: String): Flow<List<LocalSkillEntity>>

    @Query("SELECT DISTINCT tag FROM skill_tags ORDER BY tag")
    fun getAllTags(): Flow<List<String>>

    @Query("SELECT * FROM skills WHERE slug = :slug LIMIT 1")
    suspend fun getBySlug(slug: String): LocalSkillEntity?

    @Query("SELECT * FROM skills WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): LocalSkillEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(skill: LocalSkillEntity): Long

    @Update
    suspend fun update(skill: LocalSkillEntity)

    @Delete
    suspend fun delete(skill: LocalSkillEntity)

    @Query("DELETE FROM skills WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTags(tags: List<SkillTagEntity>)

    @Query("DELETE FROM skill_tags WHERE skillId = :skillId")
    suspend fun deleteTagsBySkillId(skillId: Long)

    @Query("SELECT tag FROM skill_tags WHERE skillId = :skillId")
    suspend fun getTagsForSkill(skillId: Long): List<String>

    @Query("SELECT COUNT(*) FROM skills WHERE slug = :slug")
    suspend fun countBySlug(slug: String): Int

    @Query("SELECT * FROM skills WHERE displayName LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchSkills(query: String): Flow<List<LocalSkillEntity>>

    @Transaction
    suspend fun upsertWithTags(skill: LocalSkillEntity, tags: List<String>): Long {
        val skillId = insert(skill)
        deleteTagsBySkillId(skillId)
        if (tags.isNotEmpty()) {
            insertTags(tags.map { SkillTagEntity(skillId = skillId, tag = it) })
        }
        return skillId
    }
}
