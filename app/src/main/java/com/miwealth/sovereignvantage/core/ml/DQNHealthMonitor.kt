package com.miwealth.sovereignvantage.core.ml

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * DQN Health Monitor - Production Safety Net
 * V5.17.0: Monitors neural network health and auto-recovers from failures
 *
 * Tracks:
 * - Gradient health (vanishing/exploding detection per layer)
 * - Weight health (NaN/Inf, dead neurons, magnitude drift)
 * - Loss trajectory (divergence, stagnation, NaN)
 * - Training performance (rolling win rate, reward trend)
 *
 * Auto-intervention:
 * - Checkpoints weights when health is HEALTHY
 * - Rolls back to last known good weights on CRITICAL status
 *
 * Architecture: 30 → 64 → 32 → 5 (matches SimpleNeuralNetwork v5.5.84)
 * Total parameters: 4,229 (1,984 + 2,080 + 165)
 *
 * Integration:
 *   val monitor = DQNHealthMonitor()
 *   // After each replay() batch:
 *   monitor.recordTrainingStep(network, gradients, tdError)
 *   val report = monitor.getHealthReport()
 *   if (report.status == HealthStatus.CRITICAL) {
 *       // Auto-rollback already applied — log the event
 *   }
 */

// ============================================================================
// Health Status
// ============================================================================

enum class HealthStatus {
    HEALTHY,        // All metrics nominal — checkpointing weights
    WARNING,        // Degraded but recoverable — increased monitoring
    CRITICAL        // Failure detected — auto-rollback triggered
}

enum class HealthIssue(val severity: HealthStatus, val description: String) {
    // Gradient issues
    VANISHING_GRADIENTS(HealthStatus.WARNING, "Gradient magnitudes near zero — learning stalled"),
    EXPLODING_GRADIENTS(HealthStatus.CRITICAL, "Gradient magnitudes excessive — training unstable"),
    GRADIENT_NAN(HealthStatus.CRITICAL, "NaN detected in gradients — immediate rollback"),

    // Weight issues
    WEIGHT_NAN(HealthStatus.CRITICAL, "NaN/Inf in network weights — corrupted model"),
    DEAD_NEURONS_HIGH(HealthStatus.WARNING, "Over 50% of neurons dead (stuck at zero)"),
    DEAD_NEURONS_CRITICAL(HealthStatus.CRITICAL, "Over 80% of neurons dead — network collapsed"),
    WEIGHT_EXPLOSION(HealthStatus.CRITICAL, "Weight magnitudes exceeding safe bounds"),

    // Loss issues
    LOSS_NAN(HealthStatus.CRITICAL, "NaN in training loss — immediate rollback"),
    LOSS_DIVERGING(HealthStatus.WARNING, "Loss increasing over sustained window"),
    LOSS_STAGNANT(HealthStatus.WARNING, "Loss plateau — learning may have stopped"),

    // Performance issues
    WIN_RATE_COLLAPSED(HealthStatus.WARNING, "Win rate dropped below random (50%)")
}

data class HealthReport(
    val status: HealthStatus,
    val issues: List<HealthIssue>,
    val gradientStats: GradientStats,
    val weightStats: WeightStats,
    val lossStats: LossStats,
    val performanceStats: PerformanceStats,
    val checkpointAge: Int,          // Steps since last checkpoint
    val rollbackCount: Int,          // Total rollbacks performed
    val stepCount: Int,              // Total training steps monitored
    val timestamp: Long = System.currentTimeMillis()
) {
    fun summary(): String {
        val issueText = if (issues.isEmpty()) "No issues" else issues.joinToString("; ") { it.description }
        return "[$status] Step $stepCount | Loss: ${"%.6f".format(lossStats.rollingMean)} | " +
               "Dead neurons: ${weightStats.deadNeuronPercent}% | Rollbacks: $rollbackCount | $issueText"
    }
}

data class GradientStats(
    val layer1Mean: Double,          // Input→Hidden1 gradient magnitude
    val layer2Mean: Double,          // Hidden1→Hidden2 gradient magnitude
    val layer3Mean: Double,          // Hidden2→Output gradient magnitude
    val globalNorm: Double,          // L2 norm of all gradients
    val maxAbsGradient: Double       // Largest single gradient value
)

data class WeightStats(
    val totalParameters: Int,
    val nanCount: Int,
    val infCount: Int,
    val deadNeuronCount: Int,        // Neurons with all-zero incoming weights
    val deadNeuronPercent: Double,
    val meanMagnitude: Double,
    val maxMagnitude: Double
)

data class LossStats(
    val rollingMean: Double,         // Mean loss over recent window
    val rollingStdDev: Double,       // Loss volatility
    val trend: Double,               // Positive = diverging, negative = converging
    val isNaN: Boolean,
    val windowSize: Int
)

data class PerformanceStats(
    val rollingWinRate: Double,      // Win rate over recent trades
    val rollingReward: Double,       // Mean reward over recent window
    val rewardTrend: Double,         // Positive = improving
    val totalTrades: Int
)

// ============================================================================
// Health Monitor
// ============================================================================

class DQNHealthMonitor(
    private val lossWindowSize: Int = 100,          // Rolling window for loss stats
    private val performanceWindowSize: Int = 50,     // Rolling window for win rate
    private val checkpointInterval: Int = 50,        // Checkpoint every N healthy steps
    private val vanishingThreshold: Double = 1e-7,   // Gradient mean below this = vanishing
    private val explodingThreshold: Double = 100.0,  // Gradient mean above this = exploding
    private val maxWeightMagnitude: Double = 1000.0, // Weight abs above this = explosion
    private val deadNeuronWarning: Double = 0.50,    // 50% dead = WARNING
    private val deadNeuronCritical: Double = 0.80,   // 80% dead = CRITICAL
    private val lossDivergenceWindow: Int = 50,      // Sustained loss increase over N steps
    private val lossStagnationWindow: Int = 200,     // Loss plateau over N steps
    private val stagnationThreshold: Double = 1e-6,  // Loss change below this = stagnant

    // Network dimensions — defaults match SimpleNeuralNetwork v5.5.84 (30→64→32→5)
    // Override these when the DQN uses different dimensions (e.g. stateSize=6)
    private val inputSize: Int = 30,
    private val hidden1Size: Int = 64,
    private val hidden2Size: Int = 32,
    private val outputSize: Int = 5

) {
    // ── Tracking state ──────────────────────────────────────────────────────
    private val lossHistory = ArrayDeque<Double>(lossWindowSize * 2)
    private val rewardHistory = ArrayDeque<Double>(performanceWindowSize * 2)
    private val winHistory = ArrayDeque<Boolean>(performanceWindowSize * 2)

    private var lastGradientStats: GradientStats? = null
    private var lastWeightStats: WeightStats? = null

    // ── Checkpoint state ────────────────────────────────────────────────────
    private var checkpointWeights: List<Double>? = null
    private var stepsSinceCheckpoint = 0
    private var consecutiveHealthySteps = 0

    // ── Counters ────────────────────────────────────────────────────────────
    private var stepCount = 0
    private var rollbackCount = 0

    // Derived from constructor params
    private val totalNeurons = hidden1Size + hidden2Size

    // ========================================================================
    // Primary API
    // ========================================================================

    /**
     * Records a training step and checks health.
     * Call after each DQNTrader.replay() batch.
     *
     * @param network The policy network to monitor
     * @param gradients Flattened gradient vector from computeGradients() (optional)
     * @param tdError Mean TD-error (Bellman error) from the batch
     * @param reward Reward from most recent trade (optional, for performance tracking)
     * @param wasWin Whether the most recent trade was profitable (optional)
     * @return HealthReport with current status and any auto-interventions applied
     */
    fun recordTrainingStep(
        network: SimpleNeuralNetwork,
        gradients: List<Double>? = null,
        tdError: Double = 0.0,
        reward: Double? = null,
        wasWin: Boolean? = null
    ): HealthReport {
        stepCount++

        // ── Record metrics ──────────────────────────────────────────────
        recordLoss(tdError)
        reward?.let { recordReward(it) }
        wasWin?.let { recordWin(it) }

        // ── Analyse health ──────────────────────────────────────────────
        val gradStats = gradients?.let { analyseGradients(it) }
            ?: GradientStats(0.0, 0.0, 0.0, 0.0, 0.0)
        lastGradientStats = gradStats

        val weightStats = analyseWeights(network)
        lastWeightStats = weightStats

        val lossStats = analyseLoss()
        val perfStats = analysePerformance()

        // ── Detect issues ───────────────────────────────────────────────
        val issues = mutableListOf<HealthIssue>()

        // Gradient checks
        if (gradients != null) {
            if (gradients.any { it.isNaN() }) {
                issues.add(HealthIssue.GRADIENT_NAN)
            } else if (gradStats.globalNorm > explodingThreshold) {
                issues.add(HealthIssue.EXPLODING_GRADIENTS)
            } else if (gradStats.globalNorm < vanishingThreshold && stepCount > 10) {
                issues.add(HealthIssue.VANISHING_GRADIENTS)
            }
        }

        // Weight checks
        if (weightStats.nanCount > 0 || weightStats.infCount > 0) {
            issues.add(HealthIssue.WEIGHT_NAN)
        } else if (weightStats.maxMagnitude > maxWeightMagnitude) {
            issues.add(HealthIssue.WEIGHT_EXPLOSION)
        }
        if (weightStats.deadNeuronPercent >= deadNeuronCritical * 100) {
            issues.add(HealthIssue.DEAD_NEURONS_CRITICAL)
        } else if (weightStats.deadNeuronPercent >= deadNeuronWarning * 100) {
            issues.add(HealthIssue.DEAD_NEURONS_HIGH)
        }

        // Loss checks
        if (lossStats.isNaN) {
            issues.add(HealthIssue.LOSS_NAN)
        } else {
            if (lossStats.trend > 0 && lossHistory.size >= lossDivergenceWindow) {
                issues.add(HealthIssue.LOSS_DIVERGING)
            }
            if (lossStats.rollingStdDev < stagnationThreshold && lossHistory.size >= lossStagnationWindow) {
                issues.add(HealthIssue.LOSS_STAGNANT)
            }
        }

        // Performance checks
        if (perfStats.totalTrades >= 20 && perfStats.rollingWinRate < 0.40) {
            issues.add(HealthIssue.WIN_RATE_COLLAPSED)
        }

        // ── Determine overall status ────────────────────────────────────
        val status = when {
            issues.any { it.severity == HealthStatus.CRITICAL } -> HealthStatus.CRITICAL
            issues.any { it.severity == HealthStatus.WARNING } -> HealthStatus.WARNING
            else -> HealthStatus.HEALTHY
        }

        // ── Auto-intervention ───────────────────────────────────────────
        when (status) {
            HealthStatus.HEALTHY -> {
                consecutiveHealthySteps++
                stepsSinceCheckpoint++

                // Checkpoint weights periodically when healthy
                if (consecutiveHealthySteps >= checkpointInterval &&
                    stepsSinceCheckpoint >= checkpointInterval
                ) {
                    checkpoint(network)
                }
            }

            HealthStatus.WARNING -> {
                consecutiveHealthySteps = 0
                stepsSinceCheckpoint++
                // No auto-intervention — increased monitoring only
            }

            HealthStatus.CRITICAL -> {
                consecutiveHealthySteps = 0
                // Auto-rollback to last known good weights
                rollback(network)
            }
        }

        return HealthReport(
            status = status,
            issues = issues,
            gradientStats = gradStats,
            weightStats = weightStats,
            lossStats = lossStats,
            performanceStats = perfStats,
            checkpointAge = stepsSinceCheckpoint,
            rollbackCount = rollbackCount,
            stepCount = stepCount
        )
    }

    /**
     * Returns the most recent health report without recording a new step.
     * Useful for UI polling between training batches.
     */
    fun getHealthReport(): HealthReport? {
        val gradStats = lastGradientStats ?: return null
        val weightStats = lastWeightStats ?: return null
        return HealthReport(
            status = if (rollbackCount > 3) HealthStatus.WARNING else HealthStatus.HEALTHY,
            issues = emptyList(),
            gradientStats = gradStats,
            weightStats = weightStats,
            lossStats = analyseLoss(),
            performanceStats = analysePerformance(),
            checkpointAge = stepsSinceCheckpoint,
            rollbackCount = rollbackCount,
            stepCount = stepCount
        )
    }

    /**
     * Force a checkpoint of current weights (e.g. after successful pretraining).
     */
    fun forceCheckpoint(network: SimpleNeuralNetwork) {
        checkpoint(network)
    }

    /**
     * Returns true if a checkpoint exists for rollback.
     */
    fun hasCheckpoint(): Boolean = checkpointWeights != null

    // ========================================================================
    // Analysis
    // ========================================================================

    /**
     * Analyses gradient magnitudes per layer.
     * Gradient vector layout matches SimpleNeuralNetwork.getAllWeights():
     *   [inputHidden1 weights, hidden1 biases, hidden1Hidden2 weights, hidden2 biases, hidden2Output weights, output biases]
     */
    private fun analyseGradients(gradients: List<Double>): GradientStats {
        // Segment boundaries for the 2-layer architecture (matches getAllWeights() layout)
        // Layer 1: inputSize*hidden1Size weights + hidden1Size biases = 1,984
        // Layer 2: hidden1Size*hidden2Size weights + hidden2Size biases = 2,080
        // Layer 3: hidden2Size*outputSize weights + outputSize biases = 165
        // Total: 4,229

        val l1Size = inputSize * hidden1Size + hidden1Size     // 1,984
        val l2Size = hidden1Size * hidden2Size + hidden2Size   // 2,080
        // Layer 3 = remainder

        val layer1Grads = if (gradients.size >= l1Size) gradients.subList(0, l1Size) else gradients
        val layer2Grads = if (gradients.size >= l1Size + l2Size) {
            gradients.subList(l1Size, l1Size + l2Size)
        } else {
            emptyList()
        }
        val layer3Grads = if (gradients.size > l1Size + l2Size) {
            gradients.subList(l1Size + l2Size, gradients.size)
        } else {
            emptyList()
        }

        val l1Mean = meanAbsolute(layer1Grads)
        val l2Mean = meanAbsolute(layer2Grads)
        val l3Mean = meanAbsolute(layer3Grads)

        val globalNorm = sqrt(gradients.sumOf { it * it })
        val maxAbs = gradients.maxOfOrNull { abs(it) } ?: 0.0

        return GradientStats(
            layer1Mean = l1Mean,
            layer2Mean = l2Mean,
            layer3Mean = l3Mean,
            globalNorm = globalNorm,
            maxAbsGradient = maxAbs
        )
    }

    /**
     * Analyses weight health: NaN, dead neurons, magnitude.
     */
    private fun analyseWeights(network: SimpleNeuralNetwork): WeightStats {
        val weights = network.getAllWeights()

        var nanCount = 0
        var infCount = 0
        var sumMagnitude = 0.0
        var maxMag = 0.0

        for (w in weights) {
            when {
                w.isNaN() -> nanCount++
                w.isInfinite() -> infCount++
                else -> {
                    val mag = abs(w)
                    sumMagnitude += mag
                    if (mag > maxMag) maxMag = mag
                }
            }
        }

        // Dead neuron detection:
        // A neuron is "dead" if ALL its incoming weights are effectively zero
        // Check hidden1 neurons (incoming from input layer)
        val deadNeurons = countDeadNeurons(weights)

        val deadPercent = if (totalNeurons > 0) {
            (deadNeurons.toDouble() / totalNeurons.toDouble()) * 100.0
        } else {
            0.0
        }

        return WeightStats(
            totalParameters = weights.size,
            nanCount = nanCount,
            infCount = infCount,
            deadNeuronCount = deadNeurons,
            deadNeuronPercent = deadPercent,
            meanMagnitude = if (weights.isNotEmpty()) sumMagnitude / weights.size else 0.0,
            maxMagnitude = maxMag
        )
    }

    /**
     * Counts neurons with all incoming weights effectively zero.
     * A dead ReLU neuron receives zero signal → outputs zero → contributes nothing.
     */
    private fun countDeadNeurons(weights: List<Double>): Int {
        var deadCount = 0
        val threshold = 1e-8

        // Hidden1 neurons: each has inputSize incoming weights
        // Weights layout: first inputSize * hidden1Size values are weightsInputHidden1
        for (h in 0 until hidden1Size) {
            var allDead = true
            for (i in 0 until inputSize) {
                val idx = i * hidden1Size + h
                if (idx < weights.size && abs(weights[idx]) > threshold) {
                    allDead = false
                    break
                }
            }
            if (allDead) deadCount++
        }

        // Hidden2 neurons: each has hidden1Size incoming weights
        // Offset: after inputHidden1 weights + hidden1 biases
        val h2Offset = inputSize * hidden1Size + hidden1Size
        for (h in 0 until hidden2Size) {
            var allDead = true
            for (h1 in 0 until hidden1Size) {
                val idx = h2Offset + h1 * hidden2Size + h
                if (idx < weights.size && abs(weights[idx]) > threshold) {
                    allDead = false
                    break
                }
            }
            if (allDead) deadCount++
        }

        return deadCount
    }

    /**
     * Analyses loss trajectory from rolling window.
     */
    private fun analyseLoss(): LossStats {
        if (lossHistory.isEmpty()) {
            return LossStats(0.0, 0.0, 0.0, false, 0)
        }

        val hasNaN = lossHistory.any { it.isNaN() || it.isInfinite() }
        val validLosses = lossHistory.filter { !it.isNaN() && !it.isInfinite() }

        if (validLosses.isEmpty()) {
            return LossStats(0.0, 0.0, 0.0, true, lossHistory.size)
        }

        val mean = validLosses.average()
        val stdDev = if (validLosses.size > 1) {
            sqrt(validLosses.sumOf { (it - mean) * (it - mean) } / (validLosses.size - 1))
        } else {
            0.0
        }

        // Trend: compare first half mean to second half mean
        val trend = if (validLosses.size >= 20) {
            val halfPoint = validLosses.size / 2
            val firstHalf = validLosses.subList(0, halfPoint).average()
            val secondHalf = validLosses.subList(halfPoint, validLosses.size).average()
            secondHalf - firstHalf  // Positive = diverging
        } else {
            0.0
        }

        return LossStats(
            rollingMean = mean,
            rollingStdDev = stdDev,
            trend = trend,
            isNaN = hasNaN,
            windowSize = lossHistory.size
        )
    }

    /**
     * Analyses trading performance from rolling windows.
     */
    private fun analysePerformance(): PerformanceStats {
        val winRate = if (winHistory.isNotEmpty()) {
            winHistory.count { it }.toDouble() / winHistory.size.toDouble()
        } else {
            0.0
        }

        val meanReward = if (rewardHistory.isNotEmpty()) rewardHistory.average() else 0.0

        // Reward trend: first half vs second half
        val rewardTrend = if (rewardHistory.size >= 10) {
            val halfPoint = rewardHistory.size / 2
            val first = rewardHistory.toList().subList(0, halfPoint).average()
            val second = rewardHistory.toList().subList(halfPoint, rewardHistory.size).average()
            second - first  // Positive = improving
        } else {
            0.0
        }

        return PerformanceStats(
            rollingWinRate = winRate,
            rollingReward = meanReward,
            rewardTrend = rewardTrend,
            totalTrades = winHistory.size
        )
    }

    // ========================================================================
    // Checkpoint & Rollback
    // ========================================================================

    private fun checkpoint(network: SimpleNeuralNetwork) {
        checkpointWeights = network.getAllWeights().toList()
        stepsSinceCheckpoint = 0
        consecutiveHealthySteps = 0
    }

    /**
     * Rolls back network to last known good weights.
     * If no checkpoint exists, logs warning but cannot intervene.
     */
    private fun rollback(network: SimpleNeuralNetwork) {
        val saved = checkpointWeights
        if (saved != null) {
            network.setAllWeights(saved)
            rollbackCount++
            // Clear recent bad loss data so we don't immediately re-trigger
            val retainCount = (lossHistory.size / 2).coerceAtLeast(1)
            while (lossHistory.size > retainCount) {
                lossHistory.removeLast()
            }
        }
        // If no checkpoint exists, we can't intervene — the report will reflect this
        // via checkpointAge being high and rollbackCount staying at 0
    }

    // ========================================================================
    // History Management
    // ========================================================================

    private fun recordLoss(tdError: Double) {
        lossHistory.addLast(tdError)
        while (lossHistory.size > lossWindowSize) {
            lossHistory.removeFirst()
        }
    }

    private fun recordReward(reward: Double) {
        rewardHistory.addLast(reward)
        while (rewardHistory.size > performanceWindowSize) {
            rewardHistory.removeFirst()
        }
    }

    private fun recordWin(won: Boolean) {
        winHistory.addLast(won)
        while (winHistory.size > performanceWindowSize) {
            winHistory.removeFirst()
        }
    }

    // ========================================================================
    // Utility
    // ========================================================================

    private fun meanAbsolute(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        return values.sumOf { abs(it) } / values.size
    }

    /**
     * Resets all monitoring state. Use when starting a fresh training run.
     */
    fun reset() {
        lossHistory.clear()
        rewardHistory.clear()
        winHistory.clear()
        lastGradientStats = null
        lastWeightStats = null
        checkpointWeights = null
        stepsSinceCheckpoint = 0
        consecutiveHealthySteps = 0
        stepCount = 0
        rollbackCount = 0
    }
}
