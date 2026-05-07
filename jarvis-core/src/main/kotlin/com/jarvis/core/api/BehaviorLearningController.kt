package com.jarvis.core.api

import com.jarvis.core.application.BehaviorLearningService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class BehaviorLearningController(
    private val behaviorLearningService: BehaviorLearningService,
) {

    @PostMapping("/behavior-learning/predict")
    fun triggerPrediction(
        @Valid @RequestBody(required = false) request: BehaviorLearningRequest?,
    ): BehaviorLearningResponse {
        val resolvedRequest = request ?: BehaviorLearningRequest()
        return behaviorLearningService.run(
            eventLimit = resolvedRequest.eventLimit,
            enqueueIfSafe = resolvedRequest.enqueueIfSafe,
        ).toResponse()
    }
}
