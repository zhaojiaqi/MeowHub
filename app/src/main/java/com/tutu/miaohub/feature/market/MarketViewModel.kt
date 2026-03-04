package com.tutu.miaohub.feature.market

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tutu.miaohub.MiaoApp
import com.tutu.miaohub.core.model.MiaoSkillItem
import com.tutu.miaohub.core.model.SkillCategory
import com.tutu.miaohub.core.model.SkillSortType
import com.tutu.miaohub.core.repository.MiaoHubRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class MarketUiState(
    val skills: List<MiaoSkillItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val currentCategory: SkillCategory = SkillCategory.ALL,
    val currentSort: SkillSortType = SkillSortType.FEATURED,
    val currentPage: Int = 1,
    val total: Int = 0,
    val hasMore: Boolean = false
)

class MarketViewModel : ViewModel() {

    private val repository = MiaoHubRepository()
    private val _uiState = MutableStateFlow(MarketUiState())
    val uiState: StateFlow<MarketUiState> = _uiState.asStateFlow()

    companion object {
        private const val PAGE_SIZE = 12
    }

    init {
        loadSkills()
    }

    fun setCategory(category: SkillCategory) {
        if (category == _uiState.value.currentCategory) return
        _uiState.update { it.copy(currentCategory = category) }
        loadSkills()
    }

    fun setSort(sort: SkillSortType) {
        if (sort == _uiState.value.currentSort) return
        _uiState.update { it.copy(currentSort = sort) }
        loadSkills()
    }

    fun loadSkills() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, currentPage = 1) }

            val state = _uiState.value
            val result = repository.getSkillList(
                category = state.currentCategory.key,
                sort = state.currentSort.key,
                page = 1,
                limit = PAGE_SIZE
            )

            result.fold(
                onSuccess = { response ->
                    _uiState.update {
                        it.copy(
                            skills = response.list,
                            total = response.total,
                            currentPage = 1,
                            hasMore = response.list.size < response.total,
                            isLoading = false
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "加载失败"
                        )
                    }
                }
            )
        }
    }

    private val _saveMessage = MutableStateFlow<String?>(null)
    val saveMessage: StateFlow<String?> = _saveMessage.asStateFlow()

    fun saveToLocal(slug: String) {
        viewModelScope.launch {
            try {
                val app = MiaoApp.instance
                val result = app.apiClient.fetchSkillDetail(slug)
                val detail = result.getOrThrow()
                app.skillRepository.importFromMarket(detail)
                _saveMessage.value = "已收藏: ${detail.displayName}"
            } catch (e: Exception) {
                _saveMessage.value = "收藏失败: ${e.message}"
            }
        }
    }

    fun consumeSaveMessage() {
        _saveMessage.value = null
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return

        viewModelScope.launch {
            val nextPage = state.currentPage + 1
            _uiState.update { it.copy(isLoadingMore = true) }

            val result = repository.getSkillList(
                category = state.currentCategory.key,
                sort = state.currentSort.key,
                page = nextPage,
                limit = PAGE_SIZE
            )

            result.fold(
                onSuccess = { response ->
                    _uiState.update {
                        val allSkills = it.skills + response.list
                        it.copy(
                            skills = allSkills,
                            total = response.total,
                            currentPage = nextPage,
                            hasMore = allSkills.size < response.total,
                            isLoadingMore = false
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoadingMore = false,
                            error = e.message ?: "加载更多失败"
                        )
                    }
                }
            )
        }
    }
}
