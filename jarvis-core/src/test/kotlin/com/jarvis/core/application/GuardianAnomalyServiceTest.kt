package com.jarvis.core.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jarvis.core.adapters.ai.AiAnomalyRequest
import com.jarvis.core.adapters.ai.AiAnomalyResponse
import com.jarvis.core.adapters.ai.AnomalyDetectionClient
import com.jarvis.core.adapters.db.EventRepository
import com.jarvis.core.event.Event
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class GuardianAnomalyServiceTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `scan sends recent events to ai anomaly detector`() {
        val eventRepository = FakeGuardianEventRepository(
            events = listOf(
                resourceEvent("2026-04-20T09:00:00Z", "Chrome", 91.0),
                resourceEvent("2026-04-20T09:01:00Z", "Slack", 12.0),
            ),
        )
        val anomalyClient = FakeAnomalyDetectionClient(
            nextResponse = AiAnomalyResponse(
                anomalyDetected = true,
                reason = "Chrome using 91% CPU",
                severity = "MEDIUM",
                score = 30.0,
                signals = listOf("Chrome using 91% CPU"),
            ),
        )
        val service = GuardianAnomalyService(
            eventRepository = eventRepository,
            anomalyDetectionClient = anomalyClient,
            defaultEventLimit = 100,
        )

        val result = service.scan(eventLimit = 50)

        assertEquals(2, result.eventCount)
        assertTrue(result.anomaly.anomalyDetected)
        assertEquals("Chrome using 91% CPU", result.anomaly.reason)
        assertEquals("MEDIUM", result.anomaly.severity)
        assertEquals(listOf("Chrome using 91% CPU"), result.anomaly.signals)
        assertNotNull(anomalyClient.lastRequest)
        assertEquals(2, anomalyClient.lastRequest!!.events.size)
        assertEquals("Chrome", anomalyClient.lastRequest!!.events.first().payload.get("process").asText())
    }

    private fun resourceEvent(timestamp: String, process: String, cpuPercent: Double): Event {
        return Event(
            type = "RESOURCE_SAMPLE",
            payload = objectMapper.valueToTree(
                mapOf(
                    "process" to process,
                    "cpu_percent" to cpuPercent,
                ),
            ),
            source = "test",
            createdAt = Instant.parse(timestamp),
        )
    }
}

private class FakeAnomalyDetectionClient(
    private val nextResponse: AiAnomalyResponse,
) : AnomalyDetectionClient {

    var lastRequest: AiAnomalyRequest? = null

    override fun detect(request: AiAnomalyRequest): AiAnomalyResponse {
        lastRequest = request
        return nextResponse
    }
}

private class FakeGuardianEventRepository(
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
