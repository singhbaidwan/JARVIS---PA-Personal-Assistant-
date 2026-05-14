package com.jarvis.core.api

import com.jarvis.core.application.AssistantService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class AssistantController(
    private val assistantService: AssistantService,
) {

    @PostMapping("/assistant/chat")
    fun chat(@Valid @RequestBody request: AssistantChatRequest): AssistantChatResponse {
        return assistantService.chat(
            message = request.message,
            provider = request.provider,
            model = request.model,
            eventLimit = request.eventLimit,
        ).toResponse()
    }
}
