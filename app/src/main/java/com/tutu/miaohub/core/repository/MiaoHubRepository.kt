package com.tutu.miaohub.core.repository

import com.tutu.miaohub.core.model.MiaoSkillItem
import com.tutu.miaohub.core.model.MiaoSkillListResponse
import com.tutu.miaohub.core.network.MiaoHubApiClient

class MiaoHubRepository(
    private val apiClient: MiaoHubApiClient = MiaoHubApiClient()
) {
    suspend fun getSkillList(
        category: String = "",
        sort: String = "featured",
        page: Int = 1,
        limit: Int = 12
    ): Result<MiaoSkillListResponse> {
        return apiClient.fetchSkillList(category, sort, page, limit)
    }
}
