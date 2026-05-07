package com.jarvis.core.application

import com.jarvis.core.adapters.ai.AiPredictEvent
import com.jarvis.core.adapters.ai.AiPredictRequest
import com.jarvis.core.adapters.ai.BehaviorPredictionClient
import com.jarvis.core.adapters.db.EventRepository
import com.jarvis.core.api.CommandRequest
import com.jarvis.core.command.AutomationCommand
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant

data class BehaviorLearningPrediction(
    val predictedAction: String,
    val confidence: Double,
    val reason: String,
    val context: Map<String, Any?> = emptyMap(),
)

enum class BehaviorLearningEnqueueReason {
    ENQUEUED,
    ENQUEUE_DISABLED,
    NO_EVENTS,
    NO_ACTION_PREDICTED,
    MALFORMED_PREDICTED_ACTION,
    UNSAFE_ACTION,
    LOW_CONFIDENCE,
}

data class BehaviorLearningEnqueueDetails(
    val enqueued: Boolean,
    val reason: BehaviorLearningEnqueueReason,
    val commandId: Long? = null,
    val commandAction: String? = null,
    val app: String? = null,
    val minConfidenceToEnqueue: Double,
)

data class BehaviorLearningResult(
    val eventCount: Int,
    val prediction: BehaviorLearningPrediction,
    val enqueue: BehaviorLearningEnqueueDetails,
)

interface BehaviorLearningCommandEnqueuer {
    fun enqueue(action: String, app: String, source: String): AutomationCommand
}

@Service
class CommandServiceBehaviorLearningCommandEnqueuer(
    private val commandService: CommandService,
) : BehaviorLearningCommandEnqueuer {

    override fun enqueue(action: String, app: String, source: String): AutomationCommand {
        return commandService.enqueue(
            CommandRequest(
                action = action,
                app = app,
                source = source,
            ),
        )
    }
}

@Service
class BehaviorLearningService(
    private val eventRepository: EventRepository,
    private val behaviorPredictionClient: BehaviorPredictionClient,
    private val commandEnqueuer: BehaviorLearningCommandEnqueuer,
    @Value("\${jarvis.behavior-learning.default-event-limit:120}")
    private val defaultEventLimit: Int = 120,
    @Value("\${jarvis.behavior-learning.min-confidence-to-enqueue:0.6}")
    minConfidenceToEnqueue: Double = 0.6,
    @Value("\${jarvis.behavior-learning.enqueue-source:jarvis-core-behavior-learning}")
    private val enqueueSource: String = "jarvis-core-behavior-learning",
) {

    private val logger = LoggerFactory.getLogger(BehaviorLearningService::class.java)
    private val confidenceThreshold = minConfidenceToEnqueue.coerceIn(0.0, 1.0)

    fun run(
        eventLimit: Int? = null,
        enqueueIfSafe: Boolean = true,
    ): BehaviorLearningResult {
        val safeLimit = (eventLimit ?: defaultEventLimit).coerceIn(1, 500)
        val recentEvents = eventRepository.findRecent(safeLimit).sortedBy { it.createdAt }
        val predictionResponse = behaviorPredictionClient.predict(
            AiPredictRequest(
                events = recentEvents.map { event ->
                    AiPredictEvent(
                        type = event.type,
                        timestamp = event.createdAt,
                        payload = event.payload,
                        source = event.source,
                    )
                },
                referenceTime = Instant.now(),
            ),
        )

        val prediction = BehaviorLearningPrediction(
            predictedAction = predictionResponse.predictedAction,
            confidence = predictionResponse.confidence,
            reason = predictionResponse.reason,
            context = predictionResponse.context,
        )
        val enqueueResult = maybeEnqueue(
            prediction = prediction,
            eventCount = recentEvents.size,
            enqueueIfSafe = enqueueIfSafe,
        )

        logger.info(
            "BEHAVIOR_LEARNING prediction={} confidence={} eventCount={} enqueued={} reason={}",
            prediction.predictedAction,
            prediction.confidence,
            recentEvents.size,
            enqueueResult.enqueued,
            enqueueResult.reason,
        )

        return BehaviorLearningResult(
            eventCount = recentEvents.size,
            prediction = prediction,
            enqueue = enqueueResult,
        )
    }

    private fun maybeEnqueue(
        prediction: BehaviorLearningPrediction,
        eventCount: Int,
        enqueueIfSafe: Boolean,
    ): BehaviorLearningEnqueueDetails {
        if (!enqueueIfSafe) {
            return BehaviorLearningEnqueueDetails(
                enqueued = false,
                reason = BehaviorLearningEnqueueReason.ENQUEUE_DISABLED,
                minConfidenceToEnqueue = confidenceThreshold,
            )
        }

        if (eventCount == 0) {
            return BehaviorLearningEnqueueDetails(
                enqueued = false,
                reason = BehaviorLearningEnqueueReason.NO_EVENTS,
                minConfidenceToEnqueue = confidenceThreshold,
            )
        }

        when (val parseResult = parsePredictionAction(prediction.predictedAction)) {
            is ParsedPredictionAction.NoAction -> {
                return BehaviorLearningEnqueueDetails(
                    enqueued = false,
                    reason = BehaviorLearningEnqueueReason.NO_ACTION_PREDICTED,
                    minConfidenceToEnqueue = confidenceThreshold,
                )
            }

            is ParsedPredictionAction.Malformed -> {
                return BehaviorLearningEnqueueDetails(
                    enqueued = false,
                    reason = BehaviorLearningEnqueueReason.MALFORMED_PREDICTED_ACTION,
                    minConfidenceToEnqueue = confidenceThreshold,
                )
            }

            is ParsedPredictionAction.Unsafe -> {
                return BehaviorLearningEnqueueDetails(
                    enqueued = false,
                    reason = BehaviorLearningEnqueueReason.UNSAFE_ACTION,
                    minConfidenceToEnqueue = confidenceThreshold,
                )
            }

            is ParsedPredictionAction.Safe -> {
                if (prediction.confidence < confidenceThreshold) {
                    return BehaviorLearningEnqueueDetails(
                        enqueued = false,
                        reason = BehaviorLearningEnqueueReason.LOW_CONFIDENCE,
                        commandAction = parseResult.action,
                        app = parseResult.app,
                        minConfidenceToEnqueue = confidenceThreshold,
                    )
                }

                val command = commandEnqueuer.enqueue(
                    action = parseResult.action,
                    app = parseResult.app,
                    source = enqueueSource,
                )
                return BehaviorLearningEnqueueDetails(
                    enqueued = true,
                    reason = BehaviorLearningEnqueueReason.ENQUEUED,
                    commandId = command.id,
                    commandAction = command.action,
                    app = parseResult.app,
                    minConfidenceToEnqueue = confidenceThreshold,
                )
            }
        }
    }

    private fun parsePredictionAction(predictedAction: String): ParsedPredictionAction {
        val normalized = predictedAction.trim()
        if (normalized.isEmpty() || normalized.equals("NO_ACTION", ignoreCase = true)) {
            return ParsedPredictionAction.NoAction
        }

        val parts = normalized.split(":", limit = 2)
        if (parts.size != 2) {
            return ParsedPredictionAction.Malformed
        }

        val action = parts[0].trim().uppercase()
        val app = parts[1].trim()
        if (app.isEmpty()) {
            return ParsedPredictionAction.Malformed
        }

        return if (action in SAFE_ACTIONS) {
            ParsedPredictionAction.Safe(action = action, app = app)
        } else {
            ParsedPredictionAction.Unsafe(action)
        }
    }

    private sealed interface ParsedPredictionAction {
        data object NoAction : ParsedPredictionAction
        data object Malformed : ParsedPredictionAction
        data class Unsafe(val action: String) : ParsedPredictionAction
        data class Safe(val action: String, val app: String) : ParsedPredictionAction
    }

    companion object {
        private val SAFE_ACTIONS = setOf("OPEN_APP", "CLOSE_APP")
    }
}
