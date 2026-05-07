package com.jarvis.core.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jarvis.core.adapters.ai.AiPredictRequest
import com.jarvis.core.adapters.ai.AiPredictResponse
import com.jarvis.core.adapters.ai.BehaviorPredictionClient
import com.jarvis.core.adapters.db.EventRepository
import com.jarvis.core.command.AutomationCommand
import com.jarvis.core.command.CommandStatus
import com.jarvis.core.event.Event
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class BehaviorLearningServiceTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `no events returns prediction without enqueue`() {
        val eventRepository = FakeBehaviorEventRepository(events = emptyList())
        val predictionClient = FakeBehaviorPredictionClient(
            nextResponse = AiPredictResponse(
                predictedAction = "NO_ACTION",
                confidence = 0.2,
                reason = "No activity",
                context = mapOf("event_count" to 0),
            ),
        )
        val commandEnqueuer = FakeBehaviorCommandEnqueuer(objectMapper)
        val service = BehaviorLearningService(
            eventRepository = eventRepository,
            behaviorPredictionClient = predictionClient,
            commandEnqueuer = commandEnqueuer,
            defaultEventLimit = 100,
            minConfidenceToEnqueue = 0.6,
            enqueueSource = "test-source",
        )

        val result = service.run(eventLimit = 25, enqueueIfSafe = true)

        assertEquals(0, result.eventCount)
        assertEquals("NO_ACTION", result.prediction.predictedAction)
        assertFalse(result.enqueue.enqueued)
        assertEquals(BehaviorLearningEnqueueReason.NO_EVENTS, result.enqueue.reason)
        assertNull(result.enqueue.commandId)
        assertEquals(0, commandEnqueuer.calls.size)
        assertNotNull(predictionClient.lastRequest)
        assertEquals(0, predictionClient.lastRequest!!.events.size)
    }

    @Test
    fun `malformed predicted action does not enqueue`() {
        val eventRepository = FakeBehaviorEventRepository(
            events = listOf(appEvent("2026-04-20T09:00:00Z", "Chrome")),
        )
        val predictionClient = FakeBehaviorPredictionClient(
            nextResponse = AiPredictResponse(
                predictedAction = "OPEN_APP",
                confidence = 0.95,
                reason = "Malformed action from model",
            ),
        )
        val commandEnqueuer = FakeBehaviorCommandEnqueuer(objectMapper)
        val service = BehaviorLearningService(
            eventRepository = eventRepository,
            behaviorPredictionClient = predictionClient,
            commandEnqueuer = commandEnqueuer,
            minConfidenceToEnqueue = 0.6,
        )

        val result = service.run(enqueueIfSafe = true)

        assertFalse(result.enqueue.enqueued)
        assertEquals(BehaviorLearningEnqueueReason.MALFORMED_PREDICTED_ACTION, result.enqueue.reason)
        assertEquals(0, commandEnqueuer.calls.size)
    }

    @Test
    fun `low confidence prediction does not enqueue`() {
        val eventRepository = FakeBehaviorEventRepository(
            events = listOf(appEvent("2026-04-20T09:15:00Z", "VS Code")),
        )
        val predictionClient = FakeBehaviorPredictionClient(
            nextResponse = AiPredictResponse(
                predictedAction = "OPEN_APP:VS Code",
                confidence = 0.35,
                reason = "Weak signal",
            ),
        )
        val commandEnqueuer = FakeBehaviorCommandEnqueuer(objectMapper)
        val service = BehaviorLearningService(
            eventRepository = eventRepository,
            behaviorPredictionClient = predictionClient,
            commandEnqueuer = commandEnqueuer,
            minConfidenceToEnqueue = 0.6,
        )

        val result = service.run(enqueueIfSafe = true)

        assertFalse(result.enqueue.enqueued)
        assertEquals(BehaviorLearningEnqueueReason.LOW_CONFIDENCE, result.enqueue.reason)
        assertEquals("OPEN_APP", result.enqueue.commandAction)
        assertEquals("VS Code", result.enqueue.app)
        assertEquals(0, commandEnqueuer.calls.size)
    }

    @Test
    fun `safe high confidence prediction enqueues command`() {
        val eventRepository = FakeBehaviorEventRepository(
            events = listOf(appEvent("2026-04-20T09:30:00Z", "Slack")),
        )
        val predictionClient = FakeBehaviorPredictionClient(
            nextResponse = AiPredictResponse(
                predictedAction = "CLOSE_APP:Slack",
                confidence = 0.88,
                reason = "Routine context",
            ),
        )
        val commandEnqueuer = FakeBehaviorCommandEnqueuer(objectMapper)
        val service = BehaviorLearningService(
            eventRepository = eventRepository,
            behaviorPredictionClient = predictionClient,
            commandEnqueuer = commandEnqueuer,
            minConfidenceToEnqueue = 0.6,
            enqueueSource = "behavior-test",
        )

        val result = service.run(enqueueIfSafe = true)

        assertEquals(BehaviorLearningEnqueueReason.ENQUEUED, result.enqueue.reason)
        assertEquals(true, result.enqueue.enqueued)
        assertEquals(1, commandEnqueuer.calls.size)
        assertEquals("CLOSE_APP", commandEnqueuer.calls.first().action)
        assertEquals("Slack", commandEnqueuer.calls.first().app)
        assertEquals("behavior-test", commandEnqueuer.calls.first().source)
        assertNotNull(result.enqueue.commandId)
        assertEquals("CLOSE_APP", result.enqueue.commandAction)
        assertEquals("Slack", result.enqueue.app)
    }

    private fun appEvent(timestamp: String, app: String): Event {
        return Event(
            type = "APP_SWITCHED",
            payload = objectMapper.valueToTree(mapOf("to" to app)),
            source = "test",
            createdAt = Instant.parse(timestamp),
        )
    }
}

private class FakeBehaviorPredictionClient(
    private val nextResponse: AiPredictResponse,
) : BehaviorPredictionClient {

    var lastRequest: AiPredictRequest? = null

    override fun predict(request: AiPredictRequest): AiPredictResponse {
        lastRequest = request
        return nextResponse
    }
}

private class FakeBehaviorEventRepository(
    private val events: List<Event>,
) : EventRepository {

    override fun save(event: Event): Event = event

    override fun findRecent(limit: Int): List<Event> {
        return events.sortedByDescending { it.createdAt }.take(limit)
    }

    override fun findByTypesBetween(
        startInclusive: Instant,
        endExclusive: Instant,
        types: Set<String>,
    ): List<Event> {
        return events
            .filter { it.type in types }
            .filter { !it.createdAt.isBefore(startInclusive) && it.createdAt.isBefore(endExclusive) }
            .sortedBy { it.createdAt }
    }

    override fun findLatestByTypesBefore(before: Instant, types: Set<String>): Event? {
        return events
            .filter { it.type in types }
            .filter { it.createdAt.isBefore(before) }
            .maxByOrNull { it.createdAt }
    }
}

private class FakeBehaviorCommandEnqueuer(
    private val objectMapper: com.fasterxml.jackson.databind.ObjectMapper,
) : BehaviorLearningCommandEnqueuer {

    data class EnqueueCall(
        val action: String,
        val app: String,
        val source: String,
    )

    private var nextId = 1L
    val calls = mutableListOf<EnqueueCall>()

    override fun enqueue(action: String, app: String, source: String): AutomationCommand {
        calls += EnqueueCall(action = action, app = app, source = source)
        val now = Instant.now()
        return AutomationCommand(
            id = nextId++,
            action = action,
            params = objectMapper.valueToTree(mapOf("app" to app)),
            source = source,
            status = CommandStatus.QUEUED,
            priority = 5,
            attempts = 0,
            maxAttempts = 3,
            scheduledAt = now,
            claimedBy = null,
            claimedAt = null,
            lastError = null,
            rollbackAction = null,
            rollbackParams = null,
            createdAt = now,
            updatedAt = now,
        )
    }
}
