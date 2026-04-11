package com.jarvis.core.api

import com.fasterxml.jackson.databind.JsonNode
import com.jarvis.core.event.Event
import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class EventRequest(
    @field:NotBlank
    val type: String,
    val payload: Map<String, Any?> = emptyMap(),
    val source: String? = "jarvis-agent",
)

data class EventResponse(
    val id: Long?,
    val type: String,
    val payload: JsonNode,
    val source: String?,
    val createdAt: Instant,
)

fun Event.toResponse(): EventResponse = EventResponse(
    id = id,
    type = type,
    payload = payload,
    source = source,
    createdAt = createdAt,
)
