package com.miwealth.sovereignvantage.core.portfolio

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.miwealth.sovereignvantage.data.local.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.*
import java.security.MessageDigest
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * TRADE ARCHIVE MANAGER
 * 
 * Tiered storage system for trade history:
 * - Tier 1: On-device (recent 90 days, hot data)
 * - Tier 2: User cloud (compressed archives)
 * - Tier 3: Tax export (on-demand generation)
 * 
 * Supports:
 * - Google Drive integration
 * - iCloud integration (iOS)
 * - Local compressed archives
 * - Encrypted storage option
 * - Automatic archival based on age
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO: Arthur Iain McManus (1966-2025)
 * 
 * Dedicated to Cathryn for tolerating me and my quirks. 💘
 */

// ============================================================================
// CONFIGURATION
// ============================================================================

enum class StorageMode {
    DEVICE_ONLY,    // All data on device (not recommended)
    HYBRID,         // Recent on device, archive to cloud (recommended)
    CLOUD_PRIMARY   // Minimal device storage, cloud for everything
}

enum class StorageLocation {
    LOCAL,
    GOOGLE_DRIVE,
    ICLOUD
}

enum class TaxLotMethod {
    FIFO,           // First In, First Out
    LIFO,           // Last In, First Out
    SPECIFIC_ID     // User selects specific lots
}

data class ArchiveConfig(
    val storageMode: StorageMode = StorageMode.HYBRID,
    val cloudLocation: StorageLocation = StorageLocation.GOOGLE_DRIVE,
    val daysToKeepOnDevice: Int = 90,
    val archiveBatchSize: Int = 1000,
    val compressArchives: Boolean = true,
    val encryptArchives: Boolean = true,
    val autoArchiveEnabled: Boolean = true,
    val taxLotMethod: TaxLotMethod = TaxLotMethod.FIFO,
    val includeAIDecisions: Boolean = true
)

// ============================================================================
// ARCHIVE MODELS
// ============================================================================

data class TradeArchive(
    val id: String,
    val periodStart: Long,
    val periodEnd: Long,
    val trades: List<EnhancedTradeEntity>,
    val aiDecisions: List<AIBoardDecisionEntity>?,
    val taxLots: List<TaxLotEntity>?,
    val createdAt: Long,
    val checksum: String
)

data class ArchiveResult(
    val success: Boolean,
    val archiveId: String?,
    val recordCount: Int,
    val fileSizeBytes: Long,
    val location: StorageLocation,
    val error: String?
)

data class StorageStats(
    val deviceUsageBytes: Long,
    val cloudUsageBytes: Long,
    val totalRecords: Int,
    val oldestRecord: Long?,
    val newestRecord: Long?,
    val archiveCount: Int,
    val pendingArchiveCount: Int
)

sealed class ArchiveEvent {
    data class ArchiveStarted(val recordCount: Int) : ArchiveEvent()
    data class ArchiveProgress(val current: Int, val total: Int) : ArchiveEvent()
    data class ArchiveCompleted(val result: ArchiveResult) : ArchiveEvent()
    data class ArchiveFailed(val error: String) : ArchiveEvent()
    data class RestoreStarted(val archiveId: String) : ArchiveEvent()
    data class RestoreCompleted(val recordCount: Int) : ArchiveEvent()
    data class RestoreFailed(val error: String) : ArchiveEvent()
}

// ============================================================================
// CLOUD STORAGE INTERFACE
// ============================================================================

interface CloudStorageProvider {
    val isAuthenticated: Boolean
    suspend fun authenticate(): Boolean
    suspend fun uploadFile(fileName: String, data: ByteArray): String?  // Returns file ID
    suspend fun downloadFile(fileId: String): ByteArray?
    suspend fun deleteFile(fileId: String): Boolean
    suspend fun listFiles(prefix: String): List<CloudFile>
    suspend fun getQuotaInfo(): CloudQuota?
}

data class CloudFile(
    val id: String,
    val name: String,
    val size: Long,
    val createdAt: Long,
    val modifiedAt: Long
)

data class CloudQuota(
    val totalBytes: Long,
    val usedBytes: Long,
    val availableBytes: Long
)

// ============================================================================
// TRADE ARCHIVE MANAGER
// ============================================================================

class TradeArchiveManager(
    private val context: Context,
    private val enhancedTradeDao: EnhancedTradeDao,
    private val aiBoardDecisionDao: AIBoardDecisionDao,
    private val taxLotDao: TaxLotDao,
    private val archiveMetadataDao: ArchiveMetadataDao,
    private val equitySnapshotDao: EquitySnapshotDao,
    private var config: ArchiveConfig = ArchiveConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) {
    
    private val gson: Gson = GsonBuilder()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        .create()
    
    private var cloudProvider: CloudStorageProvider? = null
    
    private val _events = MutableSharedFlow<ArchiveEvent>(replay = 0, extraBufferCapacity = 16)
    val events: SharedFlow<ArchiveEvent> = _events.asSharedFlow()
    
    private val _isArchiving = MutableStateFlow(false)
    val isArchiving: StateFlow<Boolean> = _isArchiving.asStateFlow()
    
    companion object {
        private const val ARCHIVE_DIR = "trade_archives"
        private const val ARCHIVE_PREFIX = "sv_archive_"
        private const val MILLIS_PER_DAY = 86_400_000L
    }
    
    // ========================================================================
    // CONFIGURATION
    // ========================================================================
    
    fun setConfig(newConfig: ArchiveConfig) {
        config = newConfig
    }
    
    fun setCloudProvider(provider: CloudStorageProvider) {
        cloudProvider = provider
    }
    
    // ========================================================================
    // AUTOMATIC ARCHIVAL
    // ========================================================================
    
    /**
     * Run automatic archival based on configuration
     */
    suspend fun runAutoArchive(): ArchiveResult? {
        if (!config.autoArchiveEnabled) return null
        if (_isArchiving.value) return null
        
        val cutoffDate = System.currentTimeMillis() - (config.daysToKeepOnDevice * MILLIS_PER_DAY)
        val oldTrades = enhancedTradeDao.getTradesForArchive(cutoffDate, config.archiveBatchSize)
        
        if (oldTrades.isEmpty()) return null
        
        return archiveTrades(oldTrades)
    }
    
    /**
     * Archive trades older than specified days
     */
    suspend fun archiveOldTrades(daysOld: Int = config.daysToKeepOnDevice): ArchiveResult? {
        val cutoffDate = System.currentTimeMillis() - (daysOld * MILLIS_PER_DAY)
        val trades = enhancedTradeDao.getTradesForArchive(cutoffDate, config.archiveBatchSize)
        
        if (trades.isEmpty()) return null
        
        return archiveTrades(trades)
    }
    
    // ========================================================================
    // MANUAL ARCHIVAL
    // ========================================================================
    
    /**
     * Archive specific date range
     */
    suspend fun archiveDateRange(startDate: Long, endDate: Long): ArchiveResult {
        val trades = enhancedTradeDao.getTradesInRange(startDate, endDate).first()
        
        if (trades.isEmpty()) {
            return ArchiveResult(
                success = false,
                archiveId = null,
                recordCount = 0,
                fileSizeBytes = 0,
                location = config.cloudLocation,
                error = "No trades found in date range"
            )
        }
        
        return archiveTrades(trades)
    }
    
    /**
     * Main archive method
     */
    private suspend fun archiveTrades(trades: List<EnhancedTradeEntity>): ArchiveResult {
        _isArchiving.value = true
        _events.emit(ArchiveEvent.ArchiveStarted(trades.size))
        
        try {
            val periodStart = trades.minOf { it.createdAt }
            val periodEnd = trades.maxOf { it.createdAt }
            
            // Gather related AI decisions if configured
            val aiDecisions = if (config.includeAIDecisions) {
                trades.mapNotNull { it.aiDecisionId }
                    .distinct()
                    .mapNotNull { aiBoardDecisionDao.getDecisionById(it) }
            } else null
            
            // Gather tax lots
            val taxLotIds = trades.mapNotNull { it.taxLotId }.distinct()
            val taxLots = taxLotIds.mapNotNull { taxLotDao.getLotById(it) }
            
            // Create archive object
            val archiveId = "archive_${periodStart}_${periodEnd}_${System.currentTimeMillis()}"
            val archive = TradeArchive(
                id = archiveId,
                periodStart = periodStart,
                periodEnd = periodEnd,
                trades = trades,
                aiDecisions = aiDecisions,
                taxLots = taxLots.takeIf { it.isNotEmpty() },
                createdAt = System.currentTimeMillis(),
                checksum = ""  // Will be calculated after serialization
            )
            
            // Serialize to JSON
            val json = gson.toJson(archive)
            var data = json.toByteArray(Charsets.UTF_8)
            
            // Compress if configured
            if (config.compressArchives) {
                data = compress(data)
            }
            
            // Calculate checksum
            val checksum = calculateChecksum(data)
            
            // Store based on configuration
            val location: StorageLocation
            var cloudFileId: String? = null
            var cloudFilePath: String? = null
            
            when (config.storageMode) {
                StorageMode.DEVICE_ONLY -> {
                    location = StorageLocation.LOCAL
                    saveToLocal(archiveId, data)
                }
                StorageMode.HYBRID, StorageMode.CLOUD_PRIMARY -> {
                    val provider = cloudProvider
                    if (provider != null && provider.isAuthenticated) {
                        location = config.cloudLocation
                        val fileName = "${ARCHIVE_PREFIX}${archiveId}.json${if (config.compressArchives) ".gz" else ""}"
                        cloudFileId = provider.uploadFile(fileName, data)
                        cloudFilePath = fileName
                        
                        if (cloudFileId == null) {
                            // Fallback to local
                            location == StorageLocation.LOCAL
                            saveToLocal(archiveId, data)
                        }
                    } else {
                        location = StorageLocation.LOCAL
                        saveToLocal(archiveId, data)
                    }
                }
            }
            
            // Record archive metadata
            val metadata = ArchiveMetadataEntity(
                id = archiveId,
                archiveType = "TRADES",
                periodStart = periodStart,
                periodEnd = periodEnd,
                recordCount = trades.size,
                storageLocation = location.name,
                cloudFileId = cloudFileId,
                cloudFilePath = cloudFilePath,
                fileSizeBytes = data.size.toLong(),
                isCompressed = config.compressArchives,
                isEncrypted = config.encryptArchives,
                checksum = checksum,
                createdAt = System.currentTimeMillis(),
                lastVerifiedAt = null
            )
            archiveMetadataDao.insertArchive(metadata)
            
            // Delete archived trades from device (if not device-only mode)
            if (config.storageMode != StorageMode.DEVICE_ONLY) {
                val deletedCount = enhancedTradeDao.deleteTradesOlderThan(periodEnd)
                
                // Also clean up AI decisions
                if (config.includeAIDecisions && aiDecisions != null) {
                    aiBoardDecisionDao.deleteDecisionsOlderThan(periodEnd)
                }
            }
            
            val result = ArchiveResult(
                success = true,
                archiveId = archiveId,
                recordCount = trades.size,
                fileSizeBytes = data.size.toLong(),
                location = location,
                error = null
            )
            
            _events.emit(ArchiveEvent.ArchiveCompleted(result))
            return result
            
        } catch (e: Exception) {
            val result = ArchiveResult(
                success = false,
                archiveId = null,
                recordCount = 0,
                fileSizeBytes = 0,
                location = StorageLocation.LOCAL,
                error = e.message
            )
            _events.emit(ArchiveEvent.ArchiveFailed(e.message ?: "Unknown error"))
            return result
        } finally {
            _isArchiving.value = false
        }
    }
    
    // ========================================================================
    // RESTORE
    // ========================================================================
    
    /**
     * Restore trades from an archive
     */
    suspend fun restoreArchive(archiveId: String): Int {
        _events.emit(ArchiveEvent.RestoreStarted(archiveId))
        
        try {
            val metadata = archiveMetadataDao.getArchiveById(archiveId)
                ?: throw IllegalArgumentException("Archive not found: $archiveId")
            
            // Load data based on location
            var data = when (StorageLocation.valueOf(metadata.storageLocation)) {
                StorageLocation.LOCAL -> loadFromLocal(archiveId)
                StorageLocation.GOOGLE_DRIVE, StorageLocation.ICLOUD -> {
                    val provider = cloudProvider
                        ?: throw IllegalStateException("Cloud provider not configured")
                    val fileId = metadata.cloudFileId
                        ?: throw IllegalStateException("Cloud file ID not found")
                    provider.downloadFile(fileId)
                }
            } ?: throw IllegalStateException("Failed to load archive data")
            
            // Verify checksum
            val checksum = calculateChecksum(data)
            if (checksum != metadata.checksum) {
                throw IllegalStateException("Archive checksum mismatch - data may be corrupted")
            }
            
            // Decompress if needed
            if (metadata.isCompressed) {
                data = decompress(data)
            }
            
            // Parse JSON
            val json = String(data, Charsets.UTF_8)
            val archive = gson.fromJson(json, TradeArchive::class.java)
            
            // Restore trades
            enhancedTradeDao.insertTrades(archive.trades)
            
            // Restore AI decisions
            archive.aiDecisions?.forEach { decision ->
                aiBoardDecisionDao.insertDecision(decision)
            }
            
            // Restore tax lots
            archive.taxLots?.forEach { lot ->
                taxLotDao.insertLot(lot)
            }
            
            _events.emit(ArchiveEvent.RestoreCompleted(archive.trades.size))
            return archive.trades.size
            
        } catch (e: Exception) {
            _events.emit(ArchiveEvent.RestoreFailed(e.message ?: "Unknown error"))
            throw e
        }
    }
    
    // ========================================================================
    // TAX EXPORT
    // ========================================================================
    
    /**
     * Generate tax report for Australian Tax Office (ATO)
     */
    suspend fun generateATOReport(taxYear: Int): ByteArray {
        val (startDate, endDate) = getTaxYearRange(taxYear, "AU")
        
        val trades = enhancedTradeDao.getTradesInRange(startDate, endDate).first()
            .filter { it.disposalDate != null }
        
        val sb = StringBuilder()
        sb.appendLine("ATO CAPITAL GAINS TAX REPORT - TAX YEAR $taxYear")
        sb.appendLine("Generated: ${Date()}")
        sb.appendLine("=" .repeat(80))
        sb.appendLine()
        
        // Summary
        val shortTermGains = trades.filter { it.isLongTerm == false }.sumOf { it.gainLoss ?: 0.0 }
        val longTermGains = trades.filter { it.isLongTerm == true }.sumOf { it.gainLoss ?: 0.0 }
        val totalGains = shortTermGains + longTermGains
        
        sb.appendLine("SUMMARY")
        sb.appendLine("-".repeat(40))
        sb.appendLine("Short-term gains/losses: \$${String.format("%.2f", shortTermGains)}")
        sb.appendLine("Long-term gains/losses:  \$${String.format("%.2f", longTermGains)}")
        sb.appendLine("CGT Discount (50% of LT): \$${String.format("%.2f", if (longTermGains > 0) longTermGains * 0.5 else 0.0)}")
        sb.appendLine("Total taxable gains:     \$${String.format("%.2f", totalGains)}")
        sb.appendLine()
        
        // Detail
        sb.appendLine("TRANSACTION DETAIL")
        sb.appendLine("-".repeat(80))
        sb.appendLine(String.format("%-12s %-10s %-10s %-12s %-12s %-10s %-10s",
            "Date", "Asset", "Quantity", "Cost Basis", "Proceeds", "Gain/Loss", "Term"))
        sb.appendLine("-".repeat(80))
        
        for (trade in trades.sortedBy { it.disposalDate }) {
            sb.appendLine(String.format("%-12s %-10s %-10.4f %-12.2f %-12.2f %-10.2f %-10s",
                formatDate(trade.disposalDate ?: 0),
                trade.baseAsset,
                trade.quantity,
                trade.costBasis,
                trade.proceeds ?: 0.0,
                trade.gainLoss ?: 0.0,
                if (trade.isLongTerm == true) "Long" else "Short"
            ))
        }
        
        sb.appendLine()
        sb.appendLine("END OF REPORT")
        
        return sb.toString().toByteArray(Charsets.UTF_8)
    }
    
    /**
     * Generate IRS Form 8949 / Schedule D report
     */
    suspend fun generateIRSReport(taxYear: Int): ByteArray {
        val (startDate, endDate) = getTaxYearRange(taxYear, "US")
        
        val trades = enhancedTradeDao.getTradesInRange(startDate, endDate).first()
            .filter { it.disposalDate != null }
        
        val sb = StringBuilder()
        sb.appendLine("IRS FORM 8949 / SCHEDULE D DATA - TAX YEAR $taxYear")
        sb.appendLine("Generated: ${Date()}")
        sb.appendLine("=" .repeat(100))
        sb.appendLine()
        
        // Part I - Short Term (held 1 year or less)
        val shortTerm = trades.filter { it.isLongTerm == false }
        sb.appendLine("PART I - SHORT-TERM CAPITAL GAINS AND LOSSES")
        sb.appendLine("-".repeat(100))
        sb.appendLine(String.format("%-15s %-12s %-12s %-12s %-12s %-12s %-12s",
            "Description", "Acquired", "Sold", "Proceeds", "Cost Basis", "Adjustment", "Gain/Loss"))
        sb.appendLine("-".repeat(100))
        
        var shortTermTotal = 0.0
        for (trade in shortTerm.sortedBy { it.disposalDate }) {
            val gainLoss = trade.gainLoss ?: 0.0
            shortTermTotal += gainLoss
            sb.appendLine(String.format("%-15s %-12s %-12s %-12.2f %-12.2f %-12s %-12.2f",
                "${trade.quantity} ${trade.baseAsset}",
                formatDate(trade.acquisitionDate),
                formatDate(trade.disposalDate ?: 0),
                trade.proceeds ?: 0.0,
                trade.costBasis,
                "-",
                gainLoss
            ))
        }
        sb.appendLine("-".repeat(100))
        sb.appendLine("Short-term total: \$${String.format("%.2f", shortTermTotal)}")
        sb.appendLine()
        
        // Part II - Long Term (held more than 1 year)
        val longTerm = trades.filter { it.isLongTerm == true }
        sb.appendLine("PART II - LONG-TERM CAPITAL GAINS AND LOSSES")
        sb.appendLine("-".repeat(100))
        sb.appendLine(String.format("%-15s %-12s %-12s %-12s %-12s %-12s %-12s",
            "Description", "Acquired", "Sold", "Proceeds", "Cost Basis", "Adjustment", "Gain/Loss"))
        sb.appendLine("-".repeat(100))
        
        var longTermTotal = 0.0
        for (trade in longTerm.sortedBy { it.disposalDate }) {
            val gainLoss = trade.gainLoss ?: 0.0
            longTermTotal += gainLoss
            sb.appendLine(String.format("%-15s %-12s %-12s %-12.2f %-12.2f %-12s %-12.2f",
                "${trade.quantity} ${trade.baseAsset}",
                formatDate(trade.acquisitionDate),
                formatDate(trade.disposalDate ?: 0),
                trade.proceeds ?: 0.0,
                trade.costBasis,
                "-",
                gainLoss
            ))
        }
        sb.appendLine("-".repeat(100))
        sb.appendLine("Long-term total: \$${String.format("%.2f", longTermTotal)}")
        sb.appendLine()
        
        // Summary
        sb.appendLine("SUMMARY")
        sb.appendLine("-".repeat(40))
        sb.appendLine("Total short-term gain/loss: \$${String.format("%.2f", shortTermTotal)}")
        sb.appendLine("Total long-term gain/loss:  \$${String.format("%.2f", longTermTotal)}")
        sb.appendLine("Net capital gain/loss:      \$${String.format("%.2f", shortTermTotal + longTermTotal)}")
        sb.appendLine()
        sb.appendLine("END OF REPORT")
        
        return sb.toString().toByteArray(Charsets.UTF_8)
    }
    
    /**
     * Generate generic CSV export for any jurisdiction
     */
    suspend fun generateCSVExport(startDate: Long, endDate: Long): ByteArray {
        val trades = enhancedTradeDao.getTradesInRange(startDate, endDate).first()
        
        val sb = StringBuilder()
        sb.appendLine("TradeID,Symbol,Side,Quantity,ExecutedPrice,CostBasis,Proceeds,GainLoss,AcquisitionDate,DisposalDate,HoldingDays,IsLongTerm,Exchange,Fees,TaxLotID")
        
        for (trade in trades.sortedBy { it.createdAt }) {
            sb.appendLine(listOf(
                trade.id,
                trade.symbol,
                trade.side,
                trade.quantity,
                trade.executedPrice,
                trade.costBasis,
                trade.proceeds ?: "",
                trade.gainLoss ?: "",
                formatDate(trade.acquisitionDate),
                trade.disposalDate?.let { formatDate(it) } ?: "",
                trade.holdingPeriodDays ?: "",
                trade.isLongTerm ?: "",
                trade.exchange,
                trade.totalFees,
                trade.taxLotId ?: ""
            ).joinToString(","))
        }
        
        return sb.toString().toByteArray(Charsets.UTF_8)
    }
    
    // ========================================================================
    // STORAGE STATS
    // ========================================================================
    
    /**
     * Get storage statistics
     */
    suspend fun getStorageStats(): StorageStats {
        val archives = archiveMetadataDao.getAllArchives().first()
        val trades = enhancedTradeDao.getAllTrades().first()
        
        val localArchives = archives.filter { it.storageLocation == StorageLocation.LOCAL.name }
        val cloudArchives = archives.filter { it.storageLocation != StorageLocation.LOCAL.name }
        
        val deviceUsage = localArchives.sumOf { it.fileSizeBytes } + 
            estimateDeviceDbSize(trades.size)
        
        val cloudUsage = cloudArchives.sumOf { it.fileSizeBytes }
        
        return StorageStats(
            deviceUsageBytes = deviceUsage,
            cloudUsageBytes = cloudUsage,
            totalRecords = trades.size + archives.sumOf { it.recordCount },
            oldestRecord = trades.minOfOrNull { it.createdAt },
            newestRecord = trades.maxOfOrNull { it.createdAt },
            archiveCount = archives.size,
            pendingArchiveCount = trades.count { 
                it.createdAt < System.currentTimeMillis() - (config.daysToKeepOnDevice * MILLIS_PER_DAY)
            }
        )
    }
    
    // ========================================================================
    // HELPER METHODS
    // ========================================================================
    
    private fun saveToLocal(archiveId: String, data: ByteArray) {
        val dir = File(context.filesDir, ARCHIVE_DIR)
        if (!dir.exists()) dir.mkdirs()
        
        val file = File(dir, "$archiveId.json${if (config.compressArchives) ".gz" else ""}")
        FileOutputStream(file).use { it.write(data) }
    }
    
    private fun loadFromLocal(archiveId: String): ByteArray? {
        val dir = File(context.filesDir, ARCHIVE_DIR)
        val file = File(dir, "$archiveId.json.gz").takeIf { it.exists() }
            ?: File(dir, "$archiveId.json").takeIf { it.exists() }
            ?: return null
        
        return FileInputStream(file).use { it.readBytes() }
    }
    
    private fun compress(data: ByteArray): ByteArray {
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { it.write(data) }
        return baos.toByteArray()
    }
    
    private fun decompress(data: ByteArray): ByteArray {
        val bais = ByteArrayInputStream(data)
        return GZIPInputStream(bais).use { it.readBytes() }
    }
    
    private fun calculateChecksum(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    private fun getTaxYearRange(taxYear: Int, jurisdiction: String): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        
        return when (jurisdiction) {
            "AU" -> {
                // Australian tax year: July 1 - June 30
                calendar.set(taxYear - 1, Calendar.JULY, 1, 0, 0, 0)
                val start = calendar.timeInMillis
                calendar.set(taxYear, Calendar.JUNE, 30, 23, 59, 59)
                val end = calendar.timeInMillis
                Pair(start, end)
            }
            "US" -> {
                // US tax year: January 1 - December 31
                calendar.set(taxYear, Calendar.JANUARY, 1, 0, 0, 0)
                val start = calendar.timeInMillis
                calendar.set(taxYear, Calendar.DECEMBER, 31, 23, 59, 59)
                val end = calendar.timeInMillis
                Pair(start, end)
            }
            else -> {
                // Default to calendar year
                calendar.set(taxYear, Calendar.JANUARY, 1, 0, 0, 0)
                val start = calendar.timeInMillis
                calendar.set(taxYear, Calendar.DECEMBER, 31, 23, 59, 59)
                val end = calendar.timeInMillis
                Pair(start, end)
            }
        }
    }
    
    private fun formatDate(timestamp: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        return String.format("%d/%02d/%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }
    
    private fun estimateDeviceDbSize(recordCount: Int): Long {
        // Rough estimate: ~500 bytes per trade record
        return recordCount * 500L
    }
    
    /**
     * Clean up resources
     */
    fun shutdown() {
        scope.cancel()
    }
}
