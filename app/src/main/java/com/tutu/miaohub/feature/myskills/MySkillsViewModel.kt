package com.tutu.miaohub.feature.myskills

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tutu.miaohub.MiaoApp
import com.tutu.miaohub.core.database.LocalSkillEntity
import com.tutu.miaohub.core.database.LocalSkillRepository
import com.tutu.miaohub.core.database.SkillExporter
import com.tutu.miaohub.core.database.SkillJsonValidator
import com.tutu.miaohub.core.database.ValidationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MySkillsUiState(
    val skills: List<LocalSkillEntity> = emptyList(),
    val allTags: List<String> = emptyList(),
    val selectedTag: String? = null,
    val isLoading: Boolean = true,
    val toastMessage: String? = null
)

data class ImportState(
    val isValidating: Boolean = false,
    val validationResult: ValidationResult? = null,
    val isImporting: Boolean = false,
    val importSuccess: Boolean = false,
    val slugExists: Boolean = false
)

class MySkillsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo: LocalSkillRepository = MiaoApp.instance.skillRepository

    private val _selectedTag = MutableStateFlow<String?>(null)

    private val _importState = MutableStateFlow(ImportState())
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val skills = _selectedTag.flatMapLatest { tag ->
        if (tag == null) repo.getAllSkills() else repo.getSkillsByTag(tag)
    }

    private val allTags = repo.getAllTags()

    val uiState: StateFlow<MySkillsUiState> = combine(
        skills,
        allTags,
        _selectedTag,
        _toastMessage
    ) { skillList, tags, tag, toast ->
        MySkillsUiState(
            skills = skillList,
            allTags = tags,
            selectedTag = tag,
            isLoading = false,
            toastMessage = toast
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MySkillsUiState()
    )

    fun selectTag(tag: String?) {
        _selectedTag.value = tag
    }

    fun validateJson(jsonString: String) {
        viewModelScope.launch {
            _importState.value = ImportState(isValidating = true)
            val result = SkillJsonValidator.validate(jsonString)
            val slugExists = if (result is ValidationResult.Valid) {
                repo.existsBySlug(result.skill.slug)
            } else false
            _importState.value = ImportState(
                validationResult = result,
                slugExists = slugExists
            )
        }
    }

    fun importFromJson(jsonString: String, source: String = LocalSkillEntity.SOURCE_LOCAL_IMPORT) {
        viewModelScope.launch {
            _importState.value = _importState.value.copy(isImporting = true)
            val result = SkillJsonValidator.validate(jsonString)
            if (result is ValidationResult.Valid) {
                repo.importSkill(result.skill, source)
                _importState.value = ImportState(importSuccess = true)
                _toastMessage.value = "已导入: ${result.skill.displayName}"
            } else {
                _importState.value = ImportState(validationResult = result)
            }
        }
    }

    fun importFromFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _importState.value = ImportState(isValidating = true)
            try {
                val jsonString = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader()?.use { it.readText() }
                    ?: throw Exception("无法读取文件")
                val result = SkillJsonValidator.validate(jsonString)
                if (result is ValidationResult.Valid) {
                    val slugExists = repo.existsBySlug(result.skill.slug)
                    _importState.value = ImportState(
                        validationResult = result,
                        slugExists = slugExists
                    )
                } else {
                    _importState.value = ImportState(validationResult = result)
                }
            } catch (e: Exception) {
                _importState.value = ImportState(
                    validationResult = ValidationResult.Invalid(listOf("文件读取失败: ${e.message}"))
                )
            }
        }
    }

    fun confirmImport(source: String = LocalSkillEntity.SOURCE_LOCAL_IMPORT) {
        val result = _importState.value.validationResult
        if (result is ValidationResult.Valid) {
            viewModelScope.launch {
                _importState.value = _importState.value.copy(isImporting = true)
                repo.importSkill(result.skill, source)
                _importState.value = ImportState(importSuccess = true)
                _toastMessage.value = "已导入: ${result.skill.displayName}"
            }
        }
    }

    fun resetImportState() {
        _importState.value = ImportState()
    }

    fun deleteSkill(id: Long) {
        viewModelScope.launch {
            repo.deleteSkill(id)
            _toastMessage.value = "已删除"
        }
    }

    fun duplicateSkill(id: Long) {
        viewModelScope.launch {
            val newId = repo.duplicateSkill(id)
            _toastMessage.value = if (newId != null) "已复制" else "复制失败"
        }
    }

    fun shareSkill(context: Context, entity: LocalSkillEntity) {
        val rawJson = repo.getExportJson(entity)
        SkillExporter.shareSkill(context, entity, rawJson)
    }

    fun updateSkillMetadata(id: Long, name: String, description: String, tags: List<String>) {
        viewModelScope.launch {
            repo.updateSkillMetadata(id, name, description, tags)
            _toastMessage.value = "已更新"
        }
    }

    fun consumeToast() {
        _toastMessage.value = null
    }
}
