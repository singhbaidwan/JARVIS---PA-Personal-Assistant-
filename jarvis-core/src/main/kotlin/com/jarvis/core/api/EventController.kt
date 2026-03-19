package com.jarvis.core.api

import com.jarvis.core.application.EventIngestionService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class EventController(
    private val eventIngestionService: EventIngestionService,
) {

    @PostMapping("/event")
    @ResponseStatus(HttpStatus.CREATED)
    fun postEvent(@Valid @RequestBody request: EventRequest): EventResponse {
        val event = eventIngestionService.ingest(request)
        return event.toResponse()
    }

    @GetMapping("/event")
    fun getRecentEvents(@RequestParam(defaultValue = "50") limit: Int): List<EventResponse> {
        return eventIngestionService.recent(limit).map { it.toResponse() }
    }
}
