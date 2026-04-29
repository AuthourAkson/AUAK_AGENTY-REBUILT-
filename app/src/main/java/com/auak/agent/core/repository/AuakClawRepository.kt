package com.auak.agent.core.repository

import com.auak.agent.core.model.AuakClawSkillListResponse
import com.auak.agent.core.network.auak agentyApiClient

class auak agentyRepository(
    private val apiClient: auak agentyApiClient = auak agentyApiClient()
) {
    suspend fun getSkillList(
        category: String = "",
        sort: String = "featured",
        page: Int = 1,
        limit: Int = 12
    ): Result<AuakClawSkillListResponse> {
        return apiClient.fetchSkillList(category, sort, page, limit)
    }
}
