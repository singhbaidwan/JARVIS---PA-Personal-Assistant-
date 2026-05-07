package com.jarvis.core.api

import com.jarvis.core.application.GuardianAnomalyService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class GuardianAnomalyController(
    private val guardianAnomalyService: GuardianAnomalyService,
) {

    @PostMapping("/guardian/anomaly")
    fun scanForAnomalies(
        @Valid @RequestBody(required = false) request: GuardianAnomalyRequest?,
    ): GuardianAnomalyResponse {
        val resolvedRequest = request ?: GuardianAnomalyRequest()
        return guardianAnomalyService.scan(
            eventLimit = resolvedRequest.eventLimit,
        ).toResponse()
    }
}
