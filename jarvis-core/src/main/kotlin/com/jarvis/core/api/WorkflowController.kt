package com.jarvis.core.api

import com.jarvis.core.application.WorkflowService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class WorkflowController(
    private val workflowService: WorkflowService,
) {

    @PostMapping("/workflow")
    @ResponseStatus(HttpStatus.CREATED)
    fun submit(@Valid @RequestBody request: WorkflowRequest): WorkflowRunResponse {
        val details = workflowService.submit(request)
        return details.run.toResponse(details.steps)
    }

    @GetMapping("/workflow")
    fun recent(@RequestParam(defaultValue = "25") limit: Int): List<WorkflowRunResponse> {
        return workflowService.recent(limit).map { details ->
            details.run.toResponse(details.steps)
        }
    }

    @GetMapping("/workflow/{id}")
    fun get(@PathVariable id: Long): WorkflowRunResponse {
        val details = workflowService.get(id)
        return details.run.toResponse(details.steps)
    }
}
