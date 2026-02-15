/**
 * SOVEREIGN VANTAGE V5.5.93 "ARTHUR EDITION"
 * 76-LESSON INSTITUTIONAL TRADING PROGRAMME - MANAGER
 * 
 * Business logic for progress tracking, certificate management,
 * and curriculum navigation.
 * 
 * Copyright 2025-2026 MiWealth Pty Ltd
 */

package com.miwealth.sovereignvantage.education

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Main entry point for the Trading Programme.
 * Provides access to all 76 lessons across 7 modules with progress tracking.
 */
@Singleton
class TradingProgrammeManager @Inject constructor(
    private val progressRepository: TradingProgrammeRepository
) {
    
    // ========================================================================
    // CURRICULUM ACCESS
    // ========================================================================
    
    /** All 7 modules in order */
    val allModules: List<Module> = listOf(
        TradingProgrammeCurriculum.MODULE_1_FOUNDATION,
        TradingProgrammeCurriculum.MODULE_2_TECHNICAL,
        TradingProgrammeCurriculum.MODULE_3_FUNDAMENTAL,
        TradingProgrammeCurriculumPart2.MODULE_4_RISK,
        TradingProgrammeCurriculumPart2.MODULE_5_STRATEGIES,
        TradingProgrammeCurriculumPart2.MODULE_6_INSTITUTIONAL,
        TradingProgrammeCurriculumPart2.MODULE_7_MASTERY
    )
    
    /** All 76 lessons flattened */
    val allLessons: List<Lesson> = allModules.flatMap { it.lessons }
    
    /** Programme statistics */
    val stats = ProgrammeStats(
        totalLessons = allLessons.size,
        totalModules = allModules.size,
        totalHours = allModules.sumOf { it.estimatedHours },
        certifications = allModules.size,
        practicalExercises = allLessons.sumOf { it.practicalExercises.size },
        assessments = allLessons.size,
        lessonsByDifficulty = allLessons.groupingBy { it.difficulty }.eachCount()
    )
    
    // ========================================================================
    // LESSON LOOKUP (auto-merges content from LessonContentBank)
    // ========================================================================
    
    /**
     * Get a lesson by ID with content merged from LessonContentBank.
     * If the lesson already has content defined inline, it takes priority.
     * If the content bank has content and the lesson field is blank, bank content is merged.
     */
    fun getLessonById(lessonId: Int): Lesson? {
        val lesson = allLessons.find { it.id == lessonId } ?: return null
        return mergeContent(lesson)
    }
    
    fun getModuleById(moduleId: Int): Module? = allModules.find { it.id == moduleId }
    
    fun getLessonsForModule(moduleId: Int): List<Lesson> = 
        (getModuleById(moduleId)?.lessons ?: emptyList()).map { mergeContent(it) }

    /**
     * Merge content from LessonContentBank if the lesson's inline content is blank.
     * Inline content (in curriculum files) always takes priority over the bank.
     */
    private fun mergeContent(lesson: Lesson): Lesson {
        if (lesson.content.isNotBlank()) return lesson
        val bankContent = LessonContentBank.getContent(lesson.id) ?: return lesson
        return lesson.copy(content = bankContent)
    }
    
    /** Check how many lessons have content populated (inline or via bank). */
    val contentCoverage: Pair<Int, Int> get() {
        val populated = allLessons.count { 
            it.content.isNotBlank() || LessonContentBank.getContent(it.id) != null 
        }
        return populated to allLessons.size
    }
    
    fun getModuleForLesson(lessonId: Int): Module? {
        val lesson = getLessonById(lessonId) ?: return null
        return getModuleById(lesson.moduleId)
    }
    
    // ========================================================================
    // PREREQUISITE CHECKING
    // ========================================================================
    
    suspend fun checkPrerequisites(lessonId: Int): Boolean {
        val lesson = getLessonById(lessonId) ?: return false
        if (lesson.prerequisites.isEmpty()) return true
        val completed = progressRepository.getCompletedLessonIds()
        return lesson.prerequisites.all { it in completed }
    }
    
    suspend fun getMissingPrerequisites(lessonId: Int): List<Lesson> {
        val lesson = getLessonById(lessonId) ?: return emptyList()
        val completed = progressRepository.getCompletedLessonIds()
        return lesson.prerequisites
            .filter { it !in completed }
            .mapNotNull { getLessonById(it) }
    }
    
    suspend fun getAvailableLessons(): List<Lesson> {
        val completed = progressRepository.getCompletedLessonIds()
        return allLessons.filter { lesson ->
            lesson.id !in completed && lesson.prerequisites.all { it in completed }
        }
    }
    
    suspend fun getNextRecommendedLesson(): Lesson? {
        val available = getAvailableLessons()
        if (available.isEmpty()) return null
        
        // Prioritise: current module > lower difficulty > lower lesson ID
        return available.minWithOrNull(
            compareBy({ it.moduleId }, { it.difficulty.ordinal }, { it.id })
        )
    }
    
    // ========================================================================
    // PROGRESS TRACKING
    // ========================================================================
    
    suspend fun startLesson(lessonId: Int): Result<Unit> {
        if (!checkPrerequisites(lessonId)) {
            return Result.failure(PrerequisitesNotMetException(lessonId))
        }
        progressRepository.updateProgress(
            lessonId = lessonId,
            status = ProgressStatus.IN_PROGRESS,
            timeSpentMinutes = 0
        )
        return Result.success(Unit)
    }
    
    suspend fun updateLessonTime(lessonId: Int, additionalMinutes: Int) {
        val current = progressRepository.getProgress(lessonId)
        val newTime = (current?.timeSpentMinutes ?: 0) + additionalMinutes
        progressRepository.updateProgress(
            lessonId = lessonId,
            status = current?.status ?: ProgressStatus.IN_PROGRESS,
            timeSpentMinutes = newTime
        )
    }
    
    suspend fun completeLesson(lessonId: Int, score: Int): Result<LessonCompletionResult> {
        val lesson = getLessonById(lessonId) 
            ?: return Result.failure(LessonNotFoundException(lessonId))
        
        val passed = score >= lesson.passingScore
        val status = if (passed) ProgressStatus.COMPLETED else ProgressStatus.IN_PROGRESS
        
        val current = progressRepository.getProgress(lessonId)
        progressRepository.updateProgress(
            lessonId = lessonId,
            status = status,
            score = score,
            attempts = (current?.attempts ?: 0) + 1,
            completedAtMillis = if (passed) System.currentTimeMillis() else null,
            timeSpentMinutes = current?.timeSpentMinutes ?: 0
        )
        
        // Check for module completion
        var certificateEarned: String? = null
        if (passed) {
            val module = getModuleForLesson(lessonId)
            if (module != null && isModuleComplete(module.id)) {
                certificateEarned = module.certification
                progressRepository.saveCertificate(
                    Certificate(
                        moduleId = module.id,
                        certificateName = module.certification,
                        finalScore = calculateModuleAverageScore(module.id),
                        totalTimeMinutes = calculateModuleTotalTime(module.id)
                    )
                )
            }
        }
        
        val xpEarned = if (passed) lesson.calculateXpReward() else 0
        
        return Result.success(
            LessonCompletionResult(
                lessonId = lessonId,
                passed = passed,
                score = score,
                passingScore = lesson.passingScore,
                xpEarned = xpEarned,
                certificateEarned = certificateEarned
            )
        )
    }
    
    // ========================================================================
    // MODULE PROGRESS
    // ========================================================================
    
    suspend fun isModuleComplete(moduleId: Int): Boolean {
        val module = getModuleById(moduleId) ?: return false
        val completed = progressRepository.getCompletedLessonIds()
        return module.lessons.all { it.id in completed }
    }
    
    suspend fun getModuleProgress(moduleId: Int): Float {
        val module = getModuleById(moduleId) ?: return 0f
        val completed = progressRepository.getCompletedLessonIds()
        val moduleCompleted = module.lessons.count { it.id in completed }
        return moduleCompleted.toFloat() / module.lessons.size
    }
    
    private suspend fun calculateModuleAverageScore(moduleId: Int): Int {
        val module = getModuleById(moduleId) ?: return 0
        val scores = module.lessons.mapNotNull { 
            progressRepository.getProgress(it.id)?.score 
        }
        return if (scores.isNotEmpty()) scores.average().toInt() else 0
    }
    
    private suspend fun calculateModuleTotalTime(moduleId: Int): Int {
        val module = getModuleById(moduleId) ?: return 0
        return module.lessons.sumOf { 
            progressRepository.getProgress(it.id)?.timeSpentMinutes ?: 0 
        }
    }
    
    // ========================================================================
    // OVERALL PROGRESS
    // ========================================================================
    
    suspend fun getProgressSummary(): ProgressSummary {
        val completed = progressRepository.getCompletedLessonIds()
        val certificates = progressRepository.getAllCertificates()
        val allProgress = progressRepository.getAllProgress()
        
        val totalXp = completed.sumOf { lessonId ->
            getLessonById(lessonId)?.calculateXpReward() ?: 0
        }
        
        val totalTime = allProgress.sumOf { it.timeSpentMinutes }
        
        return ProgressSummary(
            completedLessons = completed.size,
            totalLessons = stats.totalLessons,
            completedModules = certificates.size,
            totalModules = stats.totalModules,
            totalXpEarned = totalXp,
            totalTimeSpentMinutes = totalTime,
            certificationsEarned = certificates.map { it.certificateName },
            currentStreak = calculateCurrentStreak(allProgress),
            longestStreak = calculateLongestStreak(allProgress)
        )
    }
    
    private fun calculateCurrentStreak(progress: List<StudentProgress>): Int {
        // Simplified: count consecutive days with activity
        val today = System.currentTimeMillis() / (24 * 60 * 60 * 1000)
        val activeDays = progress
            .filter { it.completedAtMillis != null }
            .map { it.completedAtMillis!! / (24 * 60 * 60 * 1000) }
            .distinct()
            .sortedDescending()
        
        if (activeDays.isEmpty() || activeDays.first() < today - 1) return 0
        
        var streak = 1
        for (i in 0 until activeDays.size - 1) {
            if (activeDays[i] - activeDays[i + 1] == 1L) streak++
            else break
        }
        return streak
    }
    
    private fun calculateLongestStreak(progress: List<StudentProgress>): Int {
        val activeDays = progress
            .filter { it.completedAtMillis != null }
            .map { it.completedAtMillis!! / (24 * 60 * 60 * 1000) }
            .distinct()
            .sorted()
        
        if (activeDays.isEmpty()) return 0
        
        var longest = 1
        var current = 1
        for (i in 1 until activeDays.size) {
            if (activeDays[i] - activeDays[i - 1] == 1L) {
                current++
                longest = maxOf(longest, current)
            } else {
                current = 1
            }
        }
        return longest
    }
    
    fun observeProgressSummary(): Flow<ProgressSummary> {
        return progressRepository.observeAllProgress().map { _ ->
            getProgressSummary()
        }
    }
    
    // ========================================================================
    // ESTIMATED COMPLETION
    // ========================================================================
    
    suspend fun getEstimatedCompletionHours(): Int {
        val completed = progressRepository.getCompletedLessonIds()
        val remaining = allLessons.filter { it.id !in completed }
        return remaining.sumOf { it.durationMinutes } / 60
    }
    
    // ========================================================================
    // CERTIFICATES
    // ========================================================================
    
    suspend fun getCertificates(): List<Certificate> = progressRepository.getAllCertificates()
    
    suspend fun hasCertificate(moduleId: Int): Boolean {
        return progressRepository.getAllCertificates().any { it.moduleId == moduleId }
    }
    
    suspend fun isMasterTrader(): Boolean {
        return progressRepository.getAllCertificates().size == stats.totalModules
    }
}

// ============================================================================
// RESULT TYPES
// ============================================================================

data class LessonCompletionResult(
    val lessonId: Int,
    val passed: Boolean,
    val score: Int,
    val passingScore: Int,
    val xpEarned: Int,
    val certificateEarned: String?
)

// ============================================================================
// EXCEPTIONS
// ============================================================================

class PrerequisitesNotMetException(lessonId: Int) : 
    Exception("Prerequisites not met for lesson $lessonId")

class LessonNotFoundException(lessonId: Int) : 
    Exception("Lesson $lessonId not found")
