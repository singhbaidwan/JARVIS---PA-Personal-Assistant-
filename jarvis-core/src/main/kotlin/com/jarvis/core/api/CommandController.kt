package com.jarvis.core.api

import com.jarvis.core.application.CommandService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class CommandController(
    private val commandService: CommandService,
) {

    @PostMapping("/command")
    @ResponseStatus(HttpStatus.CREATED)
    fun enqueue(@Valid @RequestBody request: CommandRequest): CommandResponse {
        return commandService.enqueue(request).toResponse()
    }

    @GetMapping("/command")
    fun getRecentCommands(@RequestParam(defaultValue = "50") limit: Int): List<CommandResponse> {
        return commandService.recent(limit).map { it.toResponse() }
    }

    @PostMapping("/command/claim")
    fun claim(@Valid @RequestBody request: CommandClaimRequest): ResponseEntity<CommandResponse> {
        val claimed = commandService.claim(request.agentId) ?: return ResponseEntity.noContent().build()
        return ResponseEntity.ok(claimed.toResponse())
    }

    @PostMapping("/command/{id}/result")
    fun reportResult(
        @PathVariable id: Long,
        @Valid @RequestBody request: CommandResultRequest,
    ): CommandResultResponse {
        return commandService.complete(commandId = id, request = request).toResponse()
    }
}
