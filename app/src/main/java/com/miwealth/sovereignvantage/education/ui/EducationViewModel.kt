/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * 76-LESSON INSTITUTIONAL TRADING PROGRAMME - VIEWMODEL
 * 
 * State management for the education UI.
 * 
 * Copyright 2025-2026 MiWealth Pty Ltd
 */

package com.miwealth.sovereignvantage.education.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.miwealth.sovereignvantage.education.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ============================================================================
// UI STATE
// ============================================================================

data class EducationUiState(
    val isLoading: Boolean = true,
    val showModuleView: Boolean = true,
    val modules: List<ModuleWithProgress> = emptyList(),
    val availableLessons: List<Lesson> = emptyList(),
    val lessonProgress: Map<Int, StudentProgress> = emptyMap(),
    val progressSummary: ProgressSummary? = null,
    val nextLesson: Lesson? = null,
    val error: String? = null
)

data class ModuleWithProgress(
    val module: Module,
    val progress: Float,
    val isExpanded: Boolean = false,
    val isComplete: Boolean = false
)

// ============================================================================
// VIEWMODEL
// ============================================================================

@HiltViewModel
class EducationViewModel @Inject constructor(
    private val programmeManager: TradingProgrammeManager,
    private val repository: TradingProgrammeRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(EducationUiState())
    val uiState: StateFlow<EducationUiState> = _uiState.asStateFlow()
    
    private val expandedModules = MutableStateFlow<Set<Int>>(emptySet())
    
    init {
        loadData()
        observeProgress()
    }
    
    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val summary = programmeManager.getProgressSummary()
                val nextLesson = programmeManager.getNextRecommendedLesson()
                val availableLessons = programmeManager.getAvailableLessons()
                val allProgress = repository.getAllProgress().associateBy { it.lessonId }
                
                val modulesWithProgress = programmeManager.allModules.map { module ->
                    val progress = programmeManager.getModuleProgress(module.id)
                    ModuleWithProgress(
                        module = module,
                        progress = progress,
                        isExpanded = module.id in expandedModules.value,
                        isComplete = progress >= 1f
                    )
                }
                
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        modules = modulesWithProgress,
                        availableLessons = availableLessons,
                        lessonProgress = allProgress,
                        progressSummary = summary,
                        nextLesson = nextLesson,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load education data"
                    )
                }
            }
        }
    }
    
    private fun observeProgress() {
        viewModelScope.launch {
            repository.observeAllProgress().collect {
                loadData() // Refresh when progress changes
            }
        }
        
        viewModelScope.launch {
            expandedModules.collect { expanded ->
                _uiState.update { state ->
                    state.copy(
                        modules = state.modules.map { moduleWithProgress ->
                            moduleWithProgress.copy(
                                isExpanded = moduleWithProgress.module.id in expanded
                            )
                        }
                    )
                }
            }
        }
    }
    
    // ========================================================================
    // USER ACTIONS
    // ========================================================================
    
    fun toggleView() {
        _uiState.update { it.copy(showModuleView = !it.showModuleView) }
    }
    
    fun toggleModule(moduleId: Int) {
        expandedModules.update { current ->
            if (moduleId in current) current - moduleId else current + moduleId
        }
    }
    
    fun expandModule(moduleId: Int) {
        expandedModules.update { it + moduleId }
    }
    
    fun collapseAllModules() {
        expandedModules.update { emptySet() }
    }
    
    fun refresh() {
        loadData()
    }
    
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

// ============================================================================
// LESSON DETAIL VIEWMODEL
// ============================================================================

data class LessonDetailUiState(
    val isLoading: Boolean = true,
    val lesson: Lesson? = null,
    val module: Module? = null,
    val progress: StudentProgress? = null,
    val prerequisitesMet: Boolean = false,
    val missingPrerequisites: List<Lesson> = emptyList(),
    val isQuizActive: Boolean = false,
    val quizScore: Int? = null,
    val error: String? = null,
    // Quiz question state
    val quizQuestions: List<QuizQuestion> = emptyList(),
    val currentQuestionIndex: Int = 0,
    val selectedAnswers: Map<Int, Int> = emptyMap(), // questionId → optionIndex
    val revealedQuestions: Set<Int> = emptySet(),     // questionIds shown answer
    val quizComplete: Boolean = false
) {
    val currentQuestion: QuizQuestion? get() =
        quizQuestions.getOrNull(currentQuestionIndex)
    val isLastQuestion: Boolean get() =
        currentQuestionIndex >= quizQuestions.size - 1
    val answeredCount: Int get() = selectedAnswers.size
    val correctCount: Int get() = selectedAnswers.count { (qId, selected) ->
        quizQuestions.find { it.id == qId }?.correctOptionIndex == selected
    }
}

@HiltViewModel
class LessonDetailViewModel @Inject constructor(
    private val programmeManager: TradingProgrammeManager,
    private val repository: TradingProgrammeRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(LessonDetailUiState())
    val uiState: StateFlow<LessonDetailUiState> = _uiState.asStateFlow()
    
    private var lessonId: Int = 0
    
    fun loadLesson(id: Int) {
        lessonId = id
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            try {
                val lesson = programmeManager.getLessonById(id)
                val module = lesson?.let { programmeManager.getModuleForLesson(it.id) }
                val progress = repository.getProgress(id)
                val prerequisitesMet = programmeManager.checkPrerequisites(id)
                val missing = programmeManager.getMissingPrerequisites(id)
                
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        lesson = lesson,
                        module = module,
                        progress = progress,
                        prerequisitesMet = prerequisitesMet,
                        missingPrerequisites = missing,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            }
        }
    }
    
    fun startLesson() {
        viewModelScope.launch {
            programmeManager.startLesson(lessonId)
            loadLesson(lessonId)
        }
    }
    
    fun updateTimeSpent(additionalMinutes: Int) {
        viewModelScope.launch {
            programmeManager.updateLessonTime(lessonId, additionalMinutes)
        }
    }
    
    fun startQuiz() {
        val questions = QuizQuestionBank.getQuestionsForLesson(lessonId, shuffle = true)
        _uiState.update {
            it.copy(
                isQuizActive = true,
                quizQuestions = questions,
                currentQuestionIndex = 0,
                selectedAnswers = emptyMap(),
                revealedQuestions = emptySet(),
                quizComplete = false,
                quizScore = null
            )
        }
    }

    fun selectAnswer(questionId: Int, optionIndex: Int) {
        val state = _uiState.value
        // Only allow selection if not already revealed
        if (questionId in state.revealedQuestions) return
        _uiState.update {
            it.copy(
                selectedAnswers = it.selectedAnswers + (questionId to optionIndex),
                revealedQuestions = it.revealedQuestions + questionId
            )
        }
    }

    fun nextQuestion() {
        _uiState.update {
            if (it.currentQuestionIndex < it.quizQuestions.size - 1) {
                it.copy(currentQuestionIndex = it.currentQuestionIndex + 1)
            } else it
        }
    }

    fun previousQuestion() {
        _uiState.update {
            if (it.currentQuestionIndex > 0) {
                it.copy(currentQuestionIndex = it.currentQuestionIndex - 1)
            } else it
        }
    }

    fun finishQuiz() {
        val state = _uiState.value
        val totalQ = state.quizQuestions.size
        val correct = state.correctCount
        val score = if (totalQ > 0) (correct * 100) / totalQ else 0
        _uiState.update { it.copy(quizComplete = true) }
        submitQuiz(score)
    }
    
    fun submitQuiz(score: Int) {
        viewModelScope.launch {
            val result = programmeManager.completeLesson(lessonId, score)
            result.onSuccess { completion ->
                _uiState.update {
                    it.copy(
                        isQuizActive = false,
                        quizScore = score
                    )
                }
                loadLesson(lessonId) // Refresh to show updated progress
            }
            result.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isQuizActive = false,
                        error = error.message
                    )
                }
            }
        }
    }
    
    fun cancelQuiz() {
        _uiState.update { it.copy(isQuizActive = false) }
    }
}
