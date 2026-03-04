package com.tutu.miaohub.core.database

import com.tutu.miaohub.core.model.MiaoSkillDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    prettyPrint = true
    encodeDefaults = true
}

class LocalSkillRepository(private val dao: SkillDao) {

    fun getAllSkills(): Flow<List<LocalSkillEntity>> = dao.getAllSkills()

    fun getSkillsByTag(tag: String): Flow<List<LocalSkillEntity>> = dao.getSkillsByTag(tag)

    fun getAllTags(): Flow<List<String>> = dao.getAllTags()

    fun searchSkills(query: String): Flow<List<LocalSkillEntity>> = dao.searchSkills(query)

    suspend fun getBySlug(slug: String): LocalSkillEntity? = dao.getBySlug(slug)

    suspend fun getById(id: Long): LocalSkillEntity? = dao.getById(id)

    suspend fun existsBySlug(slug: String): Boolean = dao.countBySlug(slug) > 0

    suspend fun importSkill(
        detail: MiaoSkillDetail,
        source: String = LocalSkillEntity.SOURCE_LOCAL_IMPORT
    ): Long {
        val rawJson = json.encodeToString(detail)
        val stepsJson = json.encodeToString(detail.steps)

        val existing = dao.getBySlug(detail.slug)
        val now = System.currentTimeMillis()

        val entity = LocalSkillEntity(
            id = existing?.id ?: 0,
            remoteId = if (detail.id > 0) detail.id else null,
            slug = detail.slug,
            displayName = detail.displayName,
            displayNameEn = detail.displayNameEn,
            description = detail.description,
            icon = detail.icon,
            category = detail.category,
            version = detail.version,
            authorName = detail.authorName,
            estimatedTime = detail.estimatedTime,
            estimatedTokens = detail.estimatedTokens,
            stepsJson = stepsJson,
            rawJson = rawJson,
            source = source,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )

        return dao.upsertWithTags(entity, detail.tags)
    }

    suspend fun importFromMarket(detail: MiaoSkillDetail): Long {
        return importSkill(detail, LocalSkillEntity.SOURCE_MARKET)
    }

    suspend fun importFromClipboard(detail: MiaoSkillDetail): Long {
        return importSkill(detail, LocalSkillEntity.SOURCE_CLIPBOARD)
    }

    suspend fun duplicateSkill(id: Long): Long? {
        val original = dao.getById(id) ?: return null
        val tags = dao.getTagsForSkill(id)

        var newSlug = "${original.slug}-copy"
        var counter = 1
        while (dao.countBySlug(newSlug) > 0) {
            counter++
            newSlug = "${original.slug}-copy-$counter"
        }

        val copy = original.copy(
            id = 0,
            slug = newSlug,
            displayName = "${original.displayName} (副本)",
            source = LocalSkillEntity.SOURCE_LOCAL_IMPORT,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        return dao.upsertWithTags(copy, tags)
    }

    suspend fun updateSkillMetadata(
        id: Long,
        displayName: String,
        description: String,
        tags: List<String>
    ) {
        val existing = dao.getById(id) ?: return
        val updated = existing.copy(
            displayName = displayName,
            description = description,
            updatedAt = System.currentTimeMillis()
        )
        dao.update(updated)
        dao.deleteTagsBySkillId(id)
        if (tags.isNotEmpty()) {
            dao.insertTags(tags.map { SkillTagEntity(skillId = id, tag = it) })
        }
    }

    suspend fun deleteSkill(id: Long) {
        dao.deleteById(id)
    }

    fun toMiaoSkillDetail(entity: LocalSkillEntity): MiaoSkillDetail {
        return try {
            json.decodeFromString<MiaoSkillDetail>(entity.rawJson)
        } catch (_: Exception) {
            MiaoSkillDetail(
                id = entity.remoteId ?: 0,
                slug = entity.slug,
                displayName = entity.displayName,
                displayNameEn = entity.displayNameEn,
                description = entity.description,
                icon = entity.icon,
                category = entity.category,
                version = entity.version,
                authorName = entity.authorName,
                estimatedTime = entity.estimatedTime,
                estimatedTokens = entity.estimatedTokens
            )
        }
    }

    fun getExportJson(entity: LocalSkillEntity): String = entity.rawJson
}
