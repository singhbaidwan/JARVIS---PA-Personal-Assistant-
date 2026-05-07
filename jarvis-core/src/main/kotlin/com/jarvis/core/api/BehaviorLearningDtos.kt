package com.jarvis.core.api

import com.jarvis.core.application.BehaviorLearningResult
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class BehaviorLearningRequest(
    @field:Min(1)
    @field:Max(500)
    val eventLimit: Int = 120,
    val enqueueIfSafe: Boolean = true,
)

data class BehaviorLearningPredictionResponse(
    val predictedAction: String,
    val confidence: Double,
    val reason: String,
    val context: Map<String, Any?> = emptyMap(),
)

data class BehaviorLearningEnqueueResponse(
    val enqueued: Boolean,
    val reason: String,
    val commandId: Long?,
    val commandAction: String?,
    val app: String?,
    val minConfidenceToEnqueue: Double,
)

data class BehaviorLearningResponse(
    val eventCount: Int,
    val prediction: BehaviorLearningPredictionResponse,
    val enqueue: BehaviorLearningEnqueueResponse,
)

fun BehaviorLearningResult.toResponse(): BehaviorLearningResponse = BehaviorLearningResponse(
    eventCount = eventCount,
    prediction = BehaviorLearningPredictionResponse(
        predictedAction = prediction.predictedAction,
        confidence = prediction.confidence,
        reason = prediction.reason,
        context = prediction.context,
    ),
    enqueue = BehaviorLearningEnqueueResponse(
        enqueued = enqueue.enqueued,
        reason = enqueue.reason.name,
        commandId = enqueue.commandId,
        commandAction = enqueue.commandAction,
        app = enqueue.app,
        minConfidenceToEnqueue = enqueue.minConfidenceToEnqueue,
    ),
)
