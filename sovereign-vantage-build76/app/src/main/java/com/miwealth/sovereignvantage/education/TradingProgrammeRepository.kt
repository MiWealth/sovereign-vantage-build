/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * 76-LESSON INSTITUTIONAL TRADING PROGRAMME - REPOSITORY
 * 
 * Data persistence layer using Room database.
 * Supports offline-first operation and DHT sync.
 * 
 * Copyright 2025-2026 MiWealth Pty Ltd
 */


package com.miwealth.sovereignvantage.education

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

// ============================================================================
// ROOM DAO
// ============================================================================

@Dao
interface TradingProgrammeDao {
    
    // Progress operations
    @Query("SELECT * FROM student_progress WHERE lessonId = :lessonId")
    suspend fun getProgress(lessonId: Int): StudentProgress?
    
    @Query("SELECT * FROM student_progress")
    suspend fun getAllProgress(): List<StudentProgress>
    
    @Query("SELECT * FROM student_progress")
    fun observeAllProgress(): Flow<List<StudentProgress>>
    
    @Query("SELECT lessonId FROM student_progress WHERE status = 'COMPLETED' OR status = 'CERTIFIED'")
    suspend fun getCompletedLessonIds(): List<Int>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: StudentProgress)
    
    @Update
    suspend fun updateProgress(progress: StudentProgress)
    
    @Query("DELETE FROM student_progress")
    suspend fun clearAllProgress()
    
    // Certificate operations
    @Query("SELECT * FROM certificates")
    suspend fun getAllCertificates(): List<Certificate>
    
    @Query("SELECT * FROM certificates WHERE moduleId = :moduleId")
    suspend fun getCertificate(moduleId: Int): Certificate?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCertificate(certificate: Certificate)
    
    @Query("DELETE FROM certificates")
    suspend fun clearAllCertificates()
}

// ============================================================================
// TYPE CONVERTERS
// ============================================================================

class ProgressStatusConverter {
    @TypeConverter
    fun fromStatus(status: ProgressStatus): String = status.name
    
    @TypeConverter
    fun toStatus(value: String): ProgressStatus = ProgressStatus.valueOf(value)
}

// ============================================================================
// REPOSITORY INTERFACE
// ============================================================================

interface TradingProgrammeRepository {
    suspend fun getProgress(lessonId: Int): StudentProgress?
    suspend fun getAllProgress(): List<StudentProgress>
    fun observeAllProgress(): Flow<List<StudentProgress>>
    suspend fun getCompletedLessonIds(): List<Int>
    
    suspend fun updateProgress(
        lessonId: Int,
        status: ProgressStatus,
        score: Int = 0,
        attempts: Int = 0,
        completedAtMillis: Long? = null,
        timeSpentMinutes: Int = 0
    )
    
    suspend fun saveCertificate(certificate: Certificate)
    suspend fun getAllCertificates(): List<Certificate>
    suspend fun getCertificate(moduleId: Int): Certificate?
    
    suspend fun resetAllProgress()
    
    // DHT sync support
    suspend fun exportProgressForSync(): List<StudentProgress>
    suspend fun importProgressFromSync(progress: List<StudentProgress>)
}

// ============================================================================
// REPOSITORY IMPLEMENTATION
// ============================================================================

@Singleton
class TradingProgrammeRepositoryImpl @Inject constructor(
    private val dao: TradingProgrammeDao
) : TradingProgrammeRepository {
    
    override suspend fun getProgress(lessonId: Int): StudentProgress? {
        return dao.getProgress(lessonId)
    }
    
    override suspend fun getAllProgress(): List<StudentProgress> {
        return dao.getAllProgress()
    }
    
    override fun observeAllProgress(): Flow<List<StudentProgress>> {
        return dao.observeAllProgress()
    }
    
    override suspend fun getCompletedLessonIds(): List<Int> {
        return dao.getCompletedLessonIds()
    }
    
    override suspend fun updateProgress(
        lessonId: Int,
        status: ProgressStatus,
        score: Int,
        attempts: Int,
        completedAtMillis: Long?,
        timeSpentMinutes: Int
    ) {
        val existing = dao.getProgress(lessonId)
        val progress = StudentProgress(
            lessonId = lessonId,
            status = status,
            score = if (score > 0) score else existing?.score ?: 0,
            attempts = if (attempts > 0) attempts else existing?.attempts ?: 0,
            completedAtMillis = completedAtMillis ?: existing?.completedAtMillis,
            timeSpentMinutes = timeSpentMinutes,
            lastAccessedMillis = System.currentTimeMillis()
        )
        dao.insertProgress(progress)
    }
    
    override suspend fun saveCertificate(certificate: Certificate) {
        dao.insertCertificate(certificate)
    }
    
    override suspend fun getAllCertificates(): List<Certificate> {
        return dao.getAllCertificates()
    }
    
    override suspend fun getCertificate(moduleId: Int): Certificate? {
        return dao.getCertificate(moduleId)
    }
    
    override suspend fun resetAllProgress() {
        dao.clearAllProgress()
        dao.clearAllCertificates()
    }
    
    override suspend fun exportProgressForSync(): List<StudentProgress> {
        return dao.getAllProgress()
    }
    
    override suspend fun importProgressFromSync(progress: List<StudentProgress>) {
        // Merge strategy: keep higher scores and later completion times
        progress.forEach { incoming ->
            val existing = dao.getProgress(incoming.lessonId)
            if (existing == null) {
                dao.insertProgress(incoming)
            } else {
                // Keep the better result
                val merged = StudentProgress(
                    lessonId = incoming.lessonId,
                    status = maxOf(existing.status, incoming.status, compareBy { it.ordinal }),
                    score = maxOf(existing.score, incoming.score),
                    attempts = maxOf(existing.attempts, incoming.attempts),
                    completedAtMillis = listOfNotNull(
                        existing.completedAtMillis, 
                        incoming.completedAtMillis
                    ).minOrNull(),
                    timeSpentMinutes = maxOf(existing.timeSpentMinutes, incoming.timeSpentMinutes),
                    lastAccessedMillis = maxOf(existing.lastAccessedMillis, incoming.lastAccessedMillis)
                )
                dao.insertProgress(merged)
            }
        }
    }
}

// ============================================================================
// HILT MODULE
// ============================================================================

/* 
 * Add to your AppModule.kt or create EducationModule.kt:
 *
 * @Module
 * @InstallIn(SingletonComponent::class)
 * abstract class EducationModule {
 *     
 *     @Binds
 *     abstract fun bindRepository(
 *         impl: TradingProgrammeRepositoryImpl
 *     ): TradingProgrammeRepository
 * }
 *
 * @Module
 * @InstallIn(SingletonComponent::class)
 * object EducationDatabaseModule {
 *     
 *     @Provides
 *     fun provideTradingProgrammeDao(database: AppDatabase): TradingProgrammeDao {
 *         return database.tradingProgrammeDao()
 *     }
 * }
 */
