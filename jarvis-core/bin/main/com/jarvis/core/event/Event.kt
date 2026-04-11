package com.jarvis.core.event

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant

data class Event(
    val id: Long? = null,
    val type: String,
    val payload: JsonNode,
    val source: String? = null,
    val createdAt: Instant = Instant.now(),
)
