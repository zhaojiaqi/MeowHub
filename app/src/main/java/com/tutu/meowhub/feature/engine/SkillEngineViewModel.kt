package com.tutu.meowhub.feature.engine

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tutu.meowhub.MeowApp
import com.tutu.meowhub.core.engine.OverlayPromptManager
import com.tutu.meowhub.core.engine.SkillEngine
import com.tutu.meowhub.core.model.MeowSkillDetail
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SkillEngineViewModel : ViewModel() {

    private val app = MeowApp.instance
    val engine: SkillEngine = app.skillEngine

    private val promptManager = OverlayPromptManager(app)

    val engineState: StateFlow<SkillEngine.EngineState> = engine.state
    val currentSkill: StateFlow<MeowSkillDetail?> = engine.currentSkill
    val currentStepIndex: StateFlow<Int> = engine.currentStepIndex
    val currentStepLabel: StateFlow<String> = engine.currentStepLabel
    val stepStatuses: StateFlow<Map<Int, SkillEngine.StepStatus>> = engine.stepStatuses
    val runResult: StateFlow<SkillEngine.RunResult?> = engine.runResult

    private val _logs = MutableStateFlow<List<SkillEngine.EngineLog>>(emptyList())
    val logs: StateFlow<List<SkillEngine.EngineLog>> = _logs.asStateFlow()

    init {
        engine.setPromptHandler(promptManager)

        viewModelScope.launch {
            engine.logs.collect { log ->
                _logs.update { (it + log).takeLast(200) }
            }
        }
    }

    /**
     * 运行 Skill：先切后台再启动引擎。
     * @param activity 当前 Activity，用于 moveTaskToBack
     */
    fun runSkill(slug: String, activity: Activity? = null) {
        activity?.moveTaskToBack(true)
        viewModelScope.launch {
            engine.runSkill(slug)
        }
    }

    fun runLocalSkill(skill: MeowSkillDetail, activity: Activity? = null) {
        activity?.moveTaskToBack(true)
        viewModelScope.launch {
            engine.runLocalSkill(skill)
        }
    }

    fun pause() = engine.pause()
    fun resume() = engine.resume()
    fun stop() = engine.stop()

    fun clearLogs() {
        _logs.value = emptyList()
    }

    override fun onCleared() {
        super.onCleared()
        promptManager.destroy()
    }
}
