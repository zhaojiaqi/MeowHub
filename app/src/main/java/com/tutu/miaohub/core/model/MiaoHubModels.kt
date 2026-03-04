package com.tutu.miaohub.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// ── Skill 列表 ──

@Serializable
data class MiaoSkillListResponse(
    val success: Boolean,
    val list: List<MiaoSkillItem> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = 12,
    val message: String? = null
)

@Immutable
@Serializable
data class MiaoSkillItem(
    val id: Int,
    val slug: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("display_name_en") val displayNameEn: String = "",
    val description: String = "",
    val icon: String = "",
    val category: String = "",
    val version: String = "",
    val tags: List<String> = emptyList(),
    @SerialName("estimated_time") val estimatedTime: String = "",
    @SerialName("estimated_tokens") val estimatedTokens: Int = 0,
    val downloads: Int = 0,
    val stars: Int = 0,
    @SerialName("success_rate") val successRate: Double = 0.0,
    val featured: Int = 0,
    @SerialName("author_name") val authorName: String = ""
)

// ── Skill 详情 ──

@Serializable
data class MiaoSkillDetailResponse(
    val success: Boolean,
    val data: MiaoSkillDetail? = null,
    val message: String? = null
)

@Serializable
data class MiaoSkillDetail(
    val id: Int = 0,
    val slug: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("display_name_en") val displayNameEn: String = "",
    val description: String = "",
    val icon: String = "",
    val category: String = "",
    val version: String = "",
    val tags: List<String> = emptyList(),
    @SerialName("estimated_time") val estimatedTime: String = "",
    @SerialName("estimated_tokens") val estimatedTokens: Int = 0,
    val downloads: Int = 0,
    val stars: Int = 0,
    @SerialName("success_rate") val successRate: Double = 0.0,
    @SerialName("author_name") val authorName: String = "",
    val steps: List<SkillStep> = emptyList()
)

@Serializable
data class SkillStep(
    val id: String = "",
    val type: String = "",
    // api step fields
    val action: String = "",
    val params: JsonObject? = null,
    @SerialName("save_as") val saveAs: String = "",
    // wait
    val duration: Long = 0,
    // wait_until_changed / prompt_user
    val timeout: Long = 0,
    @SerialName("stable_ms") val stableMs: Long = 600,
    // set_var
    @SerialName("var") val varName: String = "",
    val op: String = "",
    val value: JsonElement? = null,
    // condition
    val expression: String = "",
    val goto: String = "",
    @SerialName("skip_to") val skipTo: String = "",
    // ai_check / ai_act / ai_summary
    val prompt: String = "",
    @SerialName("max_loops") val maxLoops: Int = 8,
    val branches: List<StepBranch> = emptyList(),
    @SerialName("branch_mode") val branchMode: String = "keyword",
    @SerialName("default_branch") val defaultBranch: StepBranch? = null,
    val output: String = "",
    val label: String = "",
    // ui_locate
    val text: String = "",
    @SerialName("text_exact") val textExact: String = "",
    @SerialName("resource_id") val resourceId: String = "",
    @SerialName("class_name") val className: String = "",
    @SerialName("description") val description: String = "",
    @SerialName("desc_exact") val descExact: String = "",
    @SerialName("exclude_text") val excludeText: String = "",
    @SerialName("exclude_desc") val excludeDesc: String = "",
    val index: Int = 0,
    // prompt_user
    val title: String = "",
    val fields: List<StepField> = emptyList(),
    @SerialName("timeout_action") val timeoutAction: String = "use_default",
    @SerialName("default_values") val defaultValues: JsonObject? = null,
    // on_fail
    @SerialName("on_fail") val onFail: JsonElement? = null,
    @SerialName("max_retries") val maxRetries: Int = 1,
    @SerialName("fail_message") val failMessage: String = "",
    // next_step routing
    @SerialName("next_step") val nextStep: String = "",
    // run_skill
    val skill: String = "",
    // wait_for_event
    @SerialName("event_types") val eventTypes: List<String> = emptyList(),
    // loop
    @SerialName("max_duration") val maxDuration: Long = 600000,
    @SerialName("max_iterations") val maxIterations: Int = 50,
    @SerialName("loop_steps") val loopSteps: List<SkillStep> = emptyList()
)

@Serializable
data class StepBranch(
    val match: String = "",
    val goto: String = "next",
    val label: String = ""
)

@Serializable
data class StepField(
    val key: String = "",
    val label: String = "",
    val type: String = "text",
    val placeholder: String = "",
    val default: String = "",
    val options: List<String> = emptyList()
)

// ── 枚举 ──

enum class SkillCategory(val key: String, val labelZh: String) {
    ALL("", "全部"),
    SOCIAL("social", "社交"),
    PRODUCTIVITY("productivity", "效率"),
    ENTERTAINMENT("entertainment", "娱乐"),
    SYSTEM("system", "系统"),
    SHOPPING("shopping", "购物"),
    TOOLS("tools", "工具"),
    DAILY("daily", "日常");
}

enum class SkillSortType(val key: String, val labelZh: String) {
    FEATURED("featured", "精选"),
    DOWNLOADS("downloads", "热门"),
    NEWEST("newest", "最新"),
    STARS("stars", "最多收藏");
}
