package com.jarvis.core.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.jarvis.core.adapters.db.EventRepository
import com.jarvis.core.api.EventRequest
import com.jarvis.core.event.Event
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class EventIngestionService(
    private val eventRepository: EventRepository,
    private val objectMapper: ObjectMapper,
) {

    private val logger = LoggerFactory.getLogger(EventIngestionService::class.java)

    fun ingest(request: EventRequest): Event {
        val event = Event(
            type = request.type.trim(),
            payload = objectMapper.valueToTree(request.payload),
            source = request.source,
            createdAt = Instant.now(),
        )

        val saved = eventRepository.save(event)
        logger.info("EVENT: {} -> {}", saved.type, saved.payload.toString())
        return saved
    }

    fun recent(limit: Int): List<Event> {
        val safeLimit = limit.coerceIn(1, 500)
        return eventRepository.findRecent(safeLimit)
    }
}
