package com.jarvis.core.api

import com.fasterxml.jackson.databind.JsonNode
import com.jarvis.core.application.CommandCompletionResult
import com.jarvis.core.command.AutomationCommand
import com.jarvis.core.command.CommandStatus
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class CommandRequest(
    @field:NotBlank
    val action: String,
    val app: String? = null,
    val params: Map<String, Any?> = emptyMap(),
    val source: String? = "jarvis-core-api",
    @field:Min(1)
    @field:Max(10)
    val priority: Int = 5,
    @field:Min(1)
    @field:Max(10)
    val maxAttempts: Int = 3,
    val scheduledAt: Instant? = null,
    val rollbackAction: String? = null,
    val rollbackParams: Map<String, Any?> = emptyMap(),
)

data class CommandClaimRequest(
    @field:NotBlank
    val agentId: String,
)

enum class CommandResultStatus {
    SUCCEEDED,
    FAILED,
}

data class CommandResultRequest(
    val status: CommandResultStatus,
    val error: String? = null,
)

data class CommandResponse(
    val id: Long?,
    val action: String,
    val params: JsonNode,
    val source: String?,
    val status: CommandStatus,
    val priority: Int,
    val attempts: Int,
    val maxAttempts: Int,
    val scheduledAt: Instant,
    val claimedBy: String?,
    val claimedAt: Instant?,
    val lastError: String?,
    val rollbackAction: String?,
    val rollbackParams: JsonNode?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class CommandResultResponse(
    val command: CommandResponse,
    val retryScheduled: Boolean,
    val rollbackCommand: CommandResponse?,
)

fun AutomationCommand.toResponse(): CommandResponse = CommandResponse(
    id = id,
    action = action,
    params = params,
    source = source,
    status = status,
    priority = priority,
    attempts = attempts,
    maxAttempts = maxAttempts,
    scheduledAt = scheduledAt,
    claimedBy = claimedBy,
    claimedAt = claimedAt,
    lastError = lastError,
    rollbackAction = rollbackAction,
    rollbackParams = rollbackParams,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun CommandCompletionResult.toResponse(): CommandResultResponse = CommandResultResponse(
    command = command.toResponse(),
    retryScheduled = retryScheduled,
    rollbackCommand = rollbackCommand?.toResponse(),
)
