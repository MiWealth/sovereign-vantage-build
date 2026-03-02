/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * 76-LESSON INSTITUTIONAL TRADING PROGRAMME - DATA MODELS
 * 
 * Comprehensive curriculum covering foundation to mastery
 * Designed for HNWIs and institutional-grade traders
 * 
 * Copyright 2025-2026 MiWealth Pty Ltd
 * Creator: Mike Stahl
 * Co-Founder (In Memoriam): Arthur Iain McManus (1966-2025)
 * Dedicated to Cathryn 💘
 */


package com.miwealth.sovereignvantage.education

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import kotlinx.serialization.Serializable
import java.time.Instant

// ============================================================================
// ENUMS
// ============================================================================

@Serializable
enum class Difficulty(val displayName: String, val xpMultiplier: Double) {
    BEGINNER("Beginner", 1.0),
    INTERMEDIATE("Intermediate", 1.5),
    ADVANCED("Advanced", 2.0),
    EXPERT("Expert", 3.0)
}

@Serializable
enum class AssessmentType(val displayName: String) {
    QUIZ("Quiz"),
    PRACTICAL("Practical Exercise"),
    SIMULATION("Trading Simulation"),
    CERTIFICATION("Certification Exam")
}

@Serializable
enum class ProgressStatus(val displayName: String) {
    NOT_STARTED("Not Started"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    CERTIFIED("Certified")
}

// ============================================================================
// CORE DATA CLASSES
// ============================================================================

/**
 * Represents a single lesson in the trading programme.
 * Each lesson contains objectives, topics, exercises, and assessment criteria.
 */
@Serializable
data class Lesson(
    val id: Int,
    val moduleId: Int,
    val title: String,
    val description: String,
    val content: String = "",  // Full lesson text — populated incrementally per session
    val durationMinutes: Int,
    val difficulty: Difficulty,
    val prerequisites: List<Int>,
    val objectives: List<String>,
    val topics: List<String>,
    val practicalExercises: List<String>,
    val assessmentType: AssessmentType,
    val passingScore: Int
) {
    /**
     * Calculate XP reward for completing this lesson
     * Base XP = duration in minutes, modified by difficulty multiplier
     */
    fun calculateXpReward(): Int {
        return (durationMinutes * difficulty.xpMultiplier).toInt()
    }
    
    /**
     * Estimate reading time (assumes 150 words per topic/objective)
     */
    fun estimatedReadingMinutes(): Int {
        val totalItems = objectives.size + topics.size
        return (totalItems * 2) // ~2 minutes per topic/objective
    }
}

/**
 * Represents a module containing multiple related lessons.
 * Each module culminates in a certification upon completion.
 */
@Serializable
data class Module(
    val id: Int,
    val name: String,
    val description: String,
    val lessons: List<Lesson>,
    val certification: String,
    val estimatedHours: Int
) {
    /**
     * Get all lesson IDs in this module
     */
    fun lessonIds(): List<Int> = lessons.map { it.id }
    
    /**
     * Calculate total XP available in this module
     */
    fun totalXpAvailable(): Int = lessons.sumOf { it.calculateXpReward() }
    
    /**
     * Get the final assessment lesson (certification exam)
     */
    fun certificationLesson(): Lesson? = lessons.find { 
        it.assessmentType == AssessmentType.CERTIFICATION 
    }
}

/**
 * Tracks a student's progress on a specific lesson.
 * Stored in Room database for persistence.
 */
@Entity(tableName = "student_progress")
data class StudentProgress(
    @PrimaryKey
    val lessonId: Int,
    val status: ProgressStatus = ProgressStatus.NOT_STARTED,
    val score: Int = 0,
    val attempts: Int = 0,
    val completedAtMillis: Long? = null,
    val timeSpentMinutes: Int = 0,
    val lastAccessedMillis: Long = System.currentTimeMillis()
) {
    /**
     * Check if lesson is passed based on score
     */
    fun isPassed(passingScore: Int): Boolean = score >= passingScore
    
    /** Convenience: quiz score as nullable (null if never attempted) */
    val quizScore: Int? get() = if (attempts > 0) score else null
    
    /** Convenience: whether lesson is completed */
    val completed: Boolean get() = status == ProgressStatus.COMPLETED
    
    /** Convenience: whether lesson has been started */
    val started: Boolean get() = status != ProgressStatus.NOT_STARTED
    
    /**
     * Get completion timestamp as Instant (null if not completed)
     */
    fun completedAt(): Instant? = completedAtMillis?.let { Instant.ofEpochMilli(it) }
    
    /**
     * Get last accessed timestamp as Instant
     */
    fun lastAccessed(): Instant = Instant.ofEpochMilli(lastAccessedMillis)
}

/**
 * Aggregate statistics for the entire programme
 */
@Serializable
data class ProgrammeStats(
    val totalLessons: Int = 76,
    val totalModules: Int = 7,
    val totalHours: Int = 190,
    val certifications: Int = 7,
    val practicalExercises: Int = 228,
    val assessments: Int = 76,
    val lessonsByDifficulty: Map<Difficulty, Int> = mapOf(
        Difficulty.BEGINNER to 12,
        Difficulty.INTERMEDIATE to 24,
        Difficulty.ADVANCED to 28,
        Difficulty.EXPERT to 12
    )
)

/**
 * Summary of a student's overall progress
 */
data class ProgressSummary(
    val completedLessons: Int,
    val totalLessons: Int,
    val completedModules: Int,
    val totalModules: Int,
    val totalXpEarned: Int,
    val totalTimeSpentMinutes: Int,
    val certificationsEarned: List<String>,
    val currentStreak: Int,
    val longestStreak: Int
) {
    val overallProgressPercent: Float
        get() = if (totalLessons > 0) (completedLessons.toFloat() / totalLessons) * 100f else 0f
    
    val moduleProgressPercent: Float
        get() = if (totalModules > 0) (completedModules.toFloat() / totalModules) * 100f else 0f
    
    val estimatedRemainingHours: Int
        get() {
            val avgMinutesPerLesson = if (completedLessons > 0) {
                totalTimeSpentMinutes / completedLessons
            } else {
                120 // Default estimate: 2 hours per lesson
            }
            return ((totalLessons - completedLessons) * avgMinutesPerLesson) / 60
        }
}

/**
 * Represents a certificate earned by completing a module
 */
@Entity(tableName = "certificates")
data class Certificate(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val moduleId: Int,
    val certificateName: String,
    val earnedAtMillis: Long = System.currentTimeMillis(),
    val finalScore: Int,
    val totalTimeMinutes: Int
) {
    fun earnedAt(): Instant = Instant.ofEpochMilli(earnedAtMillis)
}

/**
 * Quiz question for lesson assessments
 */
@Serializable
data class QuizQuestion(
    val id: Int,
    val lessonId: Int,
    val question: String,
    val options: List<String>,
    val correctOptionIndex: Int,
    val explanation: String = "",  // Populated incrementally — empty = auto-reveal correct answer
    val difficulty: Difficulty = Difficulty.INTERMEDIATE  // Default for bank questions
)

/**
 * Result of a quiz attempt
 */
data class QuizResult(
    val lessonId: Int,
    val totalQuestions: Int,
    val correctAnswers: Int,
    val timeSpentSeconds: Int,
    val passed: Boolean,
    val xpEarned: Int
) {
    val scorePercent: Int
        get() = if (totalQuestions > 0) (correctAnswers * 100) / totalQuestions else 0
}
