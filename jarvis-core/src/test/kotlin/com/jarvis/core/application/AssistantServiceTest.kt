package com.jarvis.core.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jarvis.core.adapters.ai.AiAssistantActionSuggestion
import com.jarvis.core.adapters.ai.AiAssistantChatRequest
import com.jarvis.core.adapters.ai.AiAssistantChatResponse
import com.jarvis.core.adapters.ai.AssistantChatClient
import com.jarvis.core.adapters.db.EventRepository
import com.jarvis.core.event.Event
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class AssistantServiceTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `chat sends recent event context and approves safe suggestions`() {
        val eventRepository = FakeAssistantEventRepository(
            events = listOf(appEvent("2026-04-20T09:00:00Z", "VS Code")),
        )
        val assistantClient = FakeAssistantChatClient(
            nextResponse = AiAssistantChatResponse(
                response = "I can open VS Code if you confirm.",
                model = "llama3.2:latest",
                provider = "local",
                contextSummary = "Recent events include VS Code",
                suggestedActions = listOf(
                    AiAssistantActionSuggestion(
                        action = "OPEN_APP",
                        confidence = 0.81,
                        reason = "User asked for coding setup",
                        app = "VS Code",
                    ),
                ),
            ),
        )
        val service = AssistantService(
            eventRepository = eventRepository,
            assistantChatClient = assistantClient,
            defaultEventLimit = 20,
        )

        val result = service.chat(
            message = "Open my coding setup",
            provider = "local",
            eventLimit = 10,
        )

        assertEquals("local", assistantClient.lastRequest!!.provider)
        assertEquals(1, assistantClient.lastRequest!!.context.recentEvents.size)
        assertEquals("VS Code", assistantClient.lastRequest!!.context.recentEvents.first().payload.get("to").asText())
        assertTrue(result.suggestedActions.first().approved)
        assertEquals("OPEN_APP", result.suggestedActions.first().action)
        assertEquals("VS Code", result.suggestedActions.first().app)
    }

    @Test
    fun `chat rejects unsafe and incomplete suggestions`() {
        val eventRepository = FakeAssistantEventRepository(events = emptyList())
        val assistantClient = FakeAssistantChatClient(
            nextResponse = AiAssistantChatResponse(
                response = "I cannot run destructive commands.",
                model = "llama3.2:latest",
                provider = "local",
                contextSummary = "No events",
                suggestedActions = listOf(
                    AiAssistantActionSuggestion(
                        action = "DELETE_FILE",
                        confidence = 0.9,
                        reason = "Bad idea",
                        query = "/tmp/a",
                    ),
                    AiAssistantActionSuggestion(
                        action = "SEARCH_FILES",
                        confidence = 0.7,
                        reason = "Missing query",
                    ),
                ),
            ),
        )
        val service = AssistantService(
            eventRepository = eventRepository,
            assistantChatClient = assistantClient,
        )

        val result = service.chat(message = "Clean files", eventLimit = 0)

        assertNotNull(assistantClient.lastRequest)
        assertEquals(0, assistantClient.lastRequest!!.context.recentEvents.size)
        assertEquals(2, result.suggestedActions.size)
        assertFalse(result.suggestedActions[0].approved)
        assertEquals("Action is not allowed by core policy", result.suggestedActions[0].reason)
        assertFalse(result.suggestedActions[1].approved)
        assertEquals("SEARCH_FILES requires a query", result.suggestedActions[1].reason)
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

private class FakeAssistantChatClient(
    private val nextResponse: AiAssistantChatResponse,
) : AssistantChatClient {

    var lastRequest: AiAssistantChatRequest? = null

    override fun chat(request: AiAssistantChatRequest): AiAssistantChatResponse {
        lastRequest = request
        return nextResponse
    }
}

private class FakeAssistantEventRepository(
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
