package com.tutu.meowhub.core.repository

import com.tutu.meowhub.core.model.MeowSkillItem
import com.tutu.meowhub.core.model.MeowSkillListResponse
import com.tutu.meowhub.core.network.MeowHubApiClient

class MeowHubRepository(
    private val apiClient: MeowHubApiClient = MeowHubApiClient()
) {
    suspend fun getSkillList(
        category: String = "",
        sort: String = "featured",
        page: Int = 1,
        limit: Int = 12
    ): Result<MeowSkillListResponse> {
        return apiClient.fetchSkillList(category, sort, page, limit)
    }
}
