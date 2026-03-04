package com.tutu.miaohub.core.database

import com.tutu.miaohub.core.model.MiaoSkillDetail
import kotlinx.serialization.json.Json

private val KNOWN_STEP_TYPES = setOf(
    "api", "wait", "wait_until_changed", "wait_for_event", "loop",
    "set_var", "condition", "ui_locate", "prompt_user",
    "ai_check", "ai_act", "ai_summary", "run_skill"
)

private val lenientJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
}

sealed class ValidationResult {
    data class Valid(val skill: MiaoSkillDetail) : ValidationResult()
    data class Invalid(val errors: List<String>) : ValidationResult()
}

object SkillJsonValidator {

    fun validate(jsonString: String): ValidationResult {
        val errors = mutableListOf<String>()
        val trimmed = jsonString.trim()

        if (trimmed.isEmpty()) {
            return ValidationResult.Invalid(listOf("JSON 内容为空"))
        }

        val skill: MiaoSkillDetail
        try {
            skill = lenientJson.decodeFromString<MiaoSkillDetail>(trimmed)
        } catch (e: Exception) {
            return ValidationResult.Invalid(listOf("JSON 解析失败: ${e.message?.take(200)}"))
        }

        if (skill.slug.isBlank()) errors.add("缺少必填字段: slug")
        if (skill.displayName.isBlank()) errors.add("缺少必填字段: display_name")
        if (skill.steps.isEmpty()) errors.add("steps 不能为空")

        val unknownTypes = skill.steps
            .map { it.type }
            .filter { it.isNotEmpty() && it !in KNOWN_STEP_TYPES }
            .distinct()
        if (unknownTypes.isNotEmpty()) {
            errors.add("包含未知步骤类型: ${unknownTypes.joinToString(", ")}")
        }

        return if (errors.isEmpty()) {
            ValidationResult.Valid(skill)
        } else {
            ValidationResult.Invalid(errors)
        }
    }
}
