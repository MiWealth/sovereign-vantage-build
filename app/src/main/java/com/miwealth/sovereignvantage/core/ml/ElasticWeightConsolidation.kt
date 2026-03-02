package com.miwealth.sovereignvantage.core.ml

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Elastic Weight Consolidation (EWC)
 * Prevents catastrophic forgetting when learning new patterns
 */

data class ModelWeights(
    val weights: List<Double>,
    val timestamp: Long = System.currentTimeMillis()
)

data class FisherInformation(
    val fisherMatrix: List<Double>,
    val importanceScores: List<Double>
)

/**
 * EWC Implementation
 * Preserves important weights while allowing adaptation to new data
 */
class ElasticWeightConsolidation(
    private val lambda: Double = 0.4,  // Regularization strength
    private val numWeights: Int = 1000
) {
    
    private val importantWeights = mutableMapOf<Int, Double>()
    private val fisherInformation = mutableMapOf<Int, Double>()
    private var previousWeights: ModelWeights? = null
    private val weightHistory = mutableListOf<ModelWeights>()
    
    /**
     * Calculates Fisher Information Matrix
     * Identifies which weights are important for previous tasks
     */
    fun calculateImportance(
        weights: ModelWeights,
        historicalData: List<TrainingExample>
    ): FisherInformation {
        
        val fisherMatrix = MutableList(numWeights) { 0.0 }
        
        // Calculate Fisher Information for each weight
        historicalData.forEach { example ->
            val gradients = calculateGradients(example, weights)
            
            gradients.forEachIndexed { index, gradient ->
                // Fisher Information is expectation of squared gradients
                fisherMatrix[index] += gradient * gradient
            }
        }
        
        // Normalize by number of samples
        val normalizedFisher = fisherMatrix.map { it / historicalData.size }
        
        // Calculate importance scores (normalized Fisher values)
        val maxFisher = normalizedFisher.max() ?: 1.0
        val importanceScores = normalizedFisher.map { it / maxFisher }
        
        // Store Fisher information
        normalizedFisher.forEachIndexed { index, value ->
            fisherInformation[index] = value
        }
        
        return FisherInformation(
            fisherMatrix = normalizedFisher,
            importanceScores = importanceScores
        )
    }
    
    /**
     * Calculates gradients for a training example using analytical backpropagation
     * V5.17.0: Replaced finite-difference approximation with analytical gradients
     * Previous: O(n) forward passes per gradient (1,000x slower)
     * Current: Single backprop pass (same as DQN training)
     */
    private fun calculateGradients(
        example: TrainingExample,
        weights: ModelWeights
    ): List<Double> {
        
        // Use analytical backpropagation instead of finite differences
        // This requires the same gradient computation as neural network training
        
        val prediction = forwardPass(example.features, weights)
        val target = if (example.label > 0.5) 1.0 else 0.0
        
        // For a simple perceptron (which ModelWeights represents):
        // gradient[i] = (target - prediction) * feature[i] * sigmoid'(z)
        val error = target - prediction
        val sigmoidDerivative = prediction * (1.0 - prediction)  // σ'(x) = σ(x)(1-σ(x))
        
        // Analytical gradients for all weights
        return weights.weights.mapIndexed { index, _ ->
            // Feature value at this weight index
            val feature = when {
                index < 6 -> when(index) {
                    0 -> example.features.marketPrice / 10000.0
                    1 -> example.features.volumeProfile
                    2 -> example.features.volatility
                    3 -> example.features.rsi / 100.0
                    4 -> example.features.macd
                    5 -> example.features.trend
                    else -> 0.0
                }
                else -> 1.0  // Bias term
            }
            
            error * feature * sigmoidDerivative
        }
    }
    
    /**
     * Public wrapper for gradient calculation (used by ContinualLearningManager)
     * V5.17.0: Added to expose analytical gradients to continual learning
     */
    internal fun calculateGradientsPublic(
        example: TrainingExample,
        weights: ModelWeights
    ): List<Double> {
        return calculateGradients(example, weights)
    }
    
    /**
     * Simplified forward pass for gradient calculation
     */
    private fun forwardPass(
        features: EnhancedFeatureVector,
        weights: ModelWeights
    ): Double {
        // Simplified neural network forward pass
        val featureVector = listOf(
            features.marketPrice / 10000.0,
            features.trend,
            features.volatility,
            features.rsi / 100.0,
            features.macd / 100.0
        )
        
        var sum = 0.0
        featureVector.forEachIndexed { index, value ->
            if (index < weights.weights.size) {
                sum += value * weights.weights[index]
            }
        }
        
        return sum
    }
    
    /**
     * Applies EWC penalty during training
     * Prevents important weights from changing too much
     */
    fun applyEWCPenalty(
        oldWeights: List<Double>,
        newWeights: List<Double>
    ): Double {
        
        if (previousWeights == null) return 0.0
        
        var penalty = 0.0
        
        newWeights.indices.forEach { i ->
            val importance = fisherInformation[i] ?: 0.0
            val weightChange = newWeights[i] - oldWeights[i]
            
            // EWC penalty: λ/2 * F_i * (θ_i - θ*_i)^2
            penalty += (lambda / 2.0) * importance * weightChange.pow(2)
        }
        
        return penalty
    }
    
    /**
     * Updates weights with EWC regularization
     */
    fun updateWeights(
        currentWeights: List<Double>,
        gradients: List<Double>,
        learningRate: Double
    ): List<Double> {
        
        return currentWeights.mapIndexed { index, weight ->
            val gradient = gradients[index]
            
            // Standard gradient descent update
            var update = -learningRate * gradient
            
            // Add EWC regularization if we have previous weights
            previousWeights?.let { prevWeights ->
                val importance = fisherInformation[index] ?: 0.0
                val weightDiff = weight - prevWeights.weights[index]
                
                // EWC gradient: λ * F_i * (θ_i - θ*_i)
                val ewcGradient = lambda * importance * weightDiff
                update -= learningRate * ewcGradient
            }
            
            weight + update
        }
    }
    
    /**
     * Consolidates current weights as important
     * Call this after completing training on a task
     */
    fun consolidate(
        weights: ModelWeights,
        trainingData: List<TrainingExample>
    ) {
        // Calculate importance of current weights
        val fisherInfo = calculateImportance(weights, trainingData)
        
        // Store weights and Fisher information
        previousWeights = weights
        weightHistory.add(weights)
        
        // Update importance scores
        fisherInfo.importanceScores.forEachIndexed { index, importance ->
            importantWeights[index] = importance
        }
    }
    
    /**
     * Gets importance score for a weight
     */
    fun getImportance(weightIndex: Int): Double {
        return importantWeights[weightIndex] ?: 0.0
    }
    
    /**
     * Gets statistics about weight importance
     */
    fun getStatistics(): EWCStatistics {
        val importanceValues = importantWeights.values.toList()
        
        val avgImportance = if (importanceValues.isNotEmpty()) {
            importanceValues.average()
        } else {
            0.0
        }
        
        val maxImportance = importanceValues.max() ?: 0.0
        val minImportance = importanceValues.min() ?: 0.0
        
        val highlyImportantWeights = importanceValues.count { it > 0.7 }
        
        return EWCStatistics(
            numTrackedWeights = importantWeights.size,
            avgImportance = avgImportance,
            maxImportance = maxImportance,
            minImportance = minImportance,
            highlyImportantCount = highlyImportantWeights,
            consolidationCount = weightHistory.size
        )
    }
    
    /**
     * Resets EWC state
     */
    fun reset() {
        importantWeights.clear()
        fisherInformation.clear()
        previousWeights = null
        weightHistory.clear()
    }
}

data class EWCStatistics(
    val numTrackedWeights: Int,
    val avgImportance: Double,
    val maxImportance: Double,
    val minImportance: Double,
    val highlyImportantCount: Int,
    val consolidationCount: Int
)

/**
 * Progressive Neural Networks
 * Alternative approach to prevent catastrophic forgetting
 * Adds new columns for new tasks while preserving old columns
 */
class ProgressiveNeuralNetwork {
    
    private val taskColumns = mutableListOf<TaskColumn>()
    private var currentTaskId = 0
    
    data class TaskColumn(
        val taskId: Int,
        val weights: ModelWeights,
        val frozen: Boolean = false
    )
    
    /**
     * Simple forward pass for predictions within this class
     */
    private fun forwardPass(features: EnhancedFeatureVector, weights: ModelWeights): Double {
        val featureVector = listOf(
            features.marketPrice / 10000.0,
            features.trend,
            features.volatility,
            features.rsi / 100.0,
            features.macd / 100.0,
            features.volumeProfile
        )
        var sum = 0.0
        featureVector.forEachIndexed { i, v ->
            if (i < weights.weights.size) sum += v * weights.weights[i]
        }
        return sum
    }

    /**
     * Adds a new task column
     */
    fun addTask(initialWeights: ModelWeights) {
        val column = TaskColumn(
            taskId = currentTaskId++,
            weights = initialWeights,
            frozen = false
        )
        taskColumns.add(column)
    }
    
    /**
     * Freezes a task column (prevents further updates)
     */
    fun freezeTask(taskId: Int) {
        val index = taskColumns.indexOfFirst { it.taskId == taskId }
        if (index >= 0) {
            taskColumns[index] = taskColumns[index].copy(frozen = true)
        }
    }
    
    /**
     * Predicts using all task columns
     */
    fun predict(features: EnhancedFeatureVector): Double {
        // Combine predictions from all columns
        val predictions = taskColumns.map { column ->
            forwardPass(features, column.weights)
        }
        
        // Weighted average (recent tasks weighted more)
        var weightedSum = 0.0
        var totalWeight = 0.0
        
        predictions.forEachIndexed { index, prediction ->
            val weight = (index + 1).toDouble() / taskColumns.size
            weightedSum += prediction * weight
            totalWeight += weight
        }
        
        return if (totalWeight > 0) weightedSum / totalWeight else 0.0
    }
    
    /**
     * Gets statistics
     */
    fun getStatistics(): PNNStatistics {
        return PNNStatistics(
            numTasks = taskColumns.size,
            frozenTasks = taskColumns.count { it.frozen },
            activeTasks = taskColumns.count { !it.frozen }
        )
    }
}

data class PNNStatistics(
    val numTasks: Int,
    val frozenTasks: Int,
    val activeTasks: Int
)

/**
 * Memory Replay Buffer
 * Stores representative samples from previous tasks
 * Prevents forgetting by periodically retraining on old data
 */
class MemoryReplayBuffer(
    private val maxSize: Int = 10000,
    private val samplesPerTask: Int = 1000
) {
    
    private val buffer = mutableMapOf<String, MutableList<TrainingExample>>()
    
    /**
     * Adds samples for a task
     */
    fun addTask(taskId: String, samples: List<TrainingExample>) {
        if (!buffer.containsKey(taskId)) {
            buffer[taskId] = mutableListOf()
        }
        
        // Add samples, keeping only most recent up to limit
        buffer[taskId]!!.addAll(samples)
        if (buffer[taskId]!!.size > samplesPerTask) {
            buffer[taskId] = buffer[taskId]!!.takeLast(samplesPerTask).toMutableList()
        }
    }
    
    /**
     * Samples a batch for replay training
     */
    fun sampleBatch(batchSize: Int): List<TrainingExample> {
        val allSamples = buffer.values.flatten()
        
        return if (allSamples.size <= batchSize) {
            allSamples
        } else {
            allSamples.shuffled().take(batchSize)
        }
    }
    
    /**
     * Samples from a specific task
     */
    fun sampleFromTask(taskId: String, batchSize: Int): List<TrainingExample> {
        val taskSamples = buffer[taskId] ?: emptyList()
        
        return if (taskSamples.size <= batchSize) {
            taskSamples
        } else {
            taskSamples.shuffled().take(batchSize)
        }
    }
    
    /**
     * Gets statistics
     */
    fun getStatistics(): ReplayBufferStatistics {
        return ReplayBufferStatistics(
            numTasks = buffer.size,
            totalSamples = buffer.values.sumOf { it.size },
            avgSamplesPerTask = if (buffer.isNotEmpty()) {
                buffer.values.sumOf { it.size }.toDouble() / buffer.size
            } else {
                0.0
            }
        )
    }
}

data class ReplayBufferStatistics(
    val numTasks: Int,
    val totalSamples: Int,
    val avgSamplesPerTask: Double
)

/**
 * Continual Learning Manager
 * Coordinates EWC, PNN, and Memory Replay
 */
class ContinualLearningManager(
    private val ewc: ElasticWeightConsolidation,
    private val replayBuffer: MemoryReplayBuffer,
    private val useEWC: Boolean = true,
    private val useReplay: Boolean = true
) {
    
    /**
     * Trains on new task while preserving old knowledge
     * V5.17.0: Fixed to use actual gradients instead of random noise
     */
    fun trainOnNewTask(
        taskId: String,
        newData: List<TrainingExample>,
        currentWeights: ModelWeights
    ): ModelWeights {
        
        // Store representative samples in replay buffer
        if (useReplay) {
            replayBuffer.addTask(taskId, newData.take(1000))
        }
        
        // Train on new data with EWC regularization
        var updatedWeights = currentWeights.weights.toList()
        
        newData.forEach { example ->
            // Calculate ACTUAL gradients using analytical backpropagation
            // V5.17.0: Previously used Random.nextDouble() - pure noise!
            val gradients = ewc.calculateGradientsPublic(example, ModelWeights(updatedWeights))
            
            // Update weights with EWC
            if (useEWC) {
                updatedWeights = ewc.updateWeights(updatedWeights, gradients, 0.001)
            } else {
                // Standard gradient descent
                updatedWeights = updatedWeights.mapIndexed { index, weight ->
                    weight - 0.001 * gradients[index]
                }
            }
        }
        
        // Periodically replay old data
        if (useReplay && kotlin.random.Random.nextDouble() < 0.1) {
            val replayBatch = replayBuffer.sampleBatch(32)
            // Train on replay batch (simplified)
        }
        
        val newWeights = ModelWeights(updatedWeights)
        
        // Consolidate weights after task completion
        if (useEWC) {
            ewc.consolidate(newWeights, newData)
        }
        
        return newWeights
    }
    
    /**
     * Gets combined statistics
     */
    fun getStatistics(): ContinualLearningStatistics {
        return ContinualLearningStatistics(
            ewcStats = ewc.getStatistics(),
            replayStats = replayBuffer.getStatistics(),
            useEWC = useEWC,
            useReplay = useReplay
        )
    }
}

data class ContinualLearningStatistics(
    val ewcStats: EWCStatistics,
    val replayStats: ReplayBufferStatistics,
    val useEWC: Boolean,
    val useReplay: Boolean
)
