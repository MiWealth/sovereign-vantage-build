package com.miwealth.sovereignvantage.core.dflp

import android.content.Context

/**
 * DFLP Aggregator - Decentralized Federated Learning Protocol
 * 
 * Coordinates privacy-preserving machine learning across the DHT network.
 * Each node trains locally and shares only model gradients, not raw data.
 * 
 * © 2025 MiWealth Pty Ltd - Sovereign Vantage: Arthur Edition
 */

data class ModelGradient(
    val layerName: String,
    val gradients: FloatArray,
    val nodeId: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ModelGradient
        return layerName == other.layerName && gradients.contentEquals(other.gradients)
    }
    
    override fun hashCode(): Int {
        var result = layerName.hashCode()
        result = 31 * result + gradients.contentHashCode()
        return result
    }
}

data class AggregatedModel(
    val version: Int,
    val weights: Map<String, FloatArray>,
    val participantCount: Int,
    val aggregationTimestamp: Long
)

class DFLPAggregator(private val context: Context) {
    
    private val pendingGradients = mutableListOf<ModelGradient>()
    private var currentModelVersion = 0
    private var minParticipantsForAggregation = 3
    
    /**
     * Submit local gradients for aggregation
     */
    fun submitGradients(gradients: List<ModelGradient>) {
        pendingGradients.addAll(gradients)
        
        // Check if we have enough participants
        val uniqueNodes = pendingGradients.map { it.nodeId }.distinct().size
        if (uniqueNodes >= minParticipantsForAggregation) {
            performAggregation()
        }
    }
    
    /**
     * Federated averaging - aggregate gradients from multiple nodes
     */
    private fun performAggregation() {
        // Group gradients by layer
        val gradientsByLayer = pendingGradients.groupBy { it.layerName }
        
        val aggregatedWeights = mutableMapOf<String, FloatArray>()
        
        for ((layerName, layerGradients) in gradientsByLayer) {
            // Compute average gradient for this layer
            val numGradients = layerGradients.size
            val gradientSize = layerGradients.first().gradients.size
            val avgGradient = FloatArray(gradientSize)
            
            for (gradient in layerGradients) {
                for (i in gradient.gradients.indices) {
                    avgGradient[i] += gradient.gradients[i] / numGradients
                }
            }
            
            aggregatedWeights[layerName] = avgGradient
        }
        
        currentModelVersion++
        
        // Clear pending gradients after aggregation
        pendingGradients.clear()
        
        // Notify listeners of new aggregated model
        onModelAggregated?.invoke(
            AggregatedModel(
                version = currentModelVersion,
                weights = aggregatedWeights,
                participantCount = pendingGradients.map { it.nodeId }.distinct().size,
                aggregationTimestamp = System.currentTimeMillis()
            )
        )
    }
    
    /**
     * Get current model version
     */
    fun getCurrentModelVersion(): Int = currentModelVersion
    
    /**
     * Set minimum participants required for aggregation
     */
    fun setMinParticipants(count: Int) {
        minParticipantsForAggregation = count
    }
    
    /**
     * Callback for when model is aggregated
     */
    var onModelAggregated: ((AggregatedModel) -> Unit)? = null
    
    /**
     * Privacy-preserving gradient clipping
     * Ensures no single node's gradients dominate
     */
    fun clipGradients(gradients: FloatArray, maxNorm: Float = 1.0f): FloatArray {
        val norm = kotlin.math.sqrt(gradients.map { it * it }.sum())
        
        return if (norm > maxNorm) {
            val scale = maxNorm / norm
            gradients.map { it * scale }.toFloatArray()
        } else {
            gradients
        }
    }
    
    /**
     * Add differential privacy noise to gradients
     */
    fun addDifferentialPrivacyNoise(
        gradients: FloatArray,
        epsilon: Float = 1.0f,
        delta: Float = 1e-5f
    ): FloatArray {
        val sensitivity = 1.0f // Assuming clipped gradients
        val sigma = sensitivity * kotlin.math.sqrt(2 * kotlin.math.ln(1.25 / delta)) / epsilon
        
        val random = java.util.Random()
        return gradients.map { 
            it + (random.nextGaussian() * sigma).toFloat() 
        }.toFloatArray()
    }
}
