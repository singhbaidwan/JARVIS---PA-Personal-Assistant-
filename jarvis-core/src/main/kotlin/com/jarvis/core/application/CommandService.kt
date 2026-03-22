package com.jarvis.core.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode
import com.jarvis.core.adapters.db.AutomationCommandRepository
import com.jarvis.core.api.CommandRequest
import com.jarvis.core.api.CommandResultRequest
import com.jarvis.core.api.CommandResultStatus
import com.jarvis.core.command.AutomationCommand
import com.jarvis.core.command.CommandStatus
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

data class CommandCompletionResult(
    val command: AutomationCommand,
    val retryScheduled: Boolean,
    val rollbackCommand: AutomationCommand?,
)

@Service
class CommandService(
    private val commandRepository: AutomationCommandRepository,
    private val objectMapper: ObjectMapper,
) {

    private val logger = LoggerFactory.getLogger(CommandService::class.java)

    fun enqueue(request: CommandRequest): AutomationCommand {
        val now = Instant.now()
        val action = request.action.trim().uppercase()
        val rollbackAction = request.rollbackAction?.trim()?.uppercase()?.takeIf { it.isNotEmpty() }
        val mergedParams = mergeParams(request)
        val source = request.source?.trim()?.takeIf { it.isNotEmpty() } ?: "jarvis-core-api"
        val scheduledAt = request.scheduledAt ?: now

        val command = AutomationCommand(
            action = action,
            params = objectMapper.valueToTree<JsonNode>(mergedParams),
            source = source,
            status = CommandStatus.QUEUED,
            priority = request.priority,
            attempts = 0,
            maxAttempts = request.maxAttempts,
            scheduledAt = scheduledAt,
            claimedBy = null,
            claimedAt = null,
            lastError = null,
            rollbackAction = rollbackAction,
            rollbackParams = if (rollbackAction == null) {
                null
            } else {
                objectMapper.valueToTree<JsonNode>(request.rollbackParams)
            },
            createdAt = now,
            updatedAt = now,
        )

        val saved = commandRepository.enqueue(command)
        logger.info(
            "COMMAND_ENQUEUED id={} action={} priority={} scheduledAt={}",
            saved.id,
            saved.action,
            saved.priority,
            saved.scheduledAt,
        )
        return saved
    }

    fun recent(limit: Int): List<AutomationCommand> {
        val safeLimit = limit.coerceIn(1, 500)
        return commandRepository.findRecent(safeLimit)
    }

    fun claim(agentId: String): AutomationCommand? {
        val normalizedAgentId = agentId.trim().ifEmpty {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "agentId must not be blank")
        }
        return commandRepository.claimNext(normalizedAgentId, Instant.now())
    }

    fun complete(commandId: Long, request: CommandResultRequest): CommandCompletionResult {
        val now = Instant.now()
        val command = commandRepository.findById(commandId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Command $commandId not found")

        if (command.status != CommandStatus.IN_PROGRESS) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Command $commandId is ${command.status} and cannot accept result",
            )
        }

        return when (request.status) {
            CommandResultStatus.SUCCEEDED -> {
                val updated = commandRepository.markSucceeded(commandId, now)
                    ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Command $commandId state changed")
                logger.info("COMMAND_SUCCEEDED id={} action={}", updated.id, updated.action)
                CommandCompletionResult(command = updated, retryScheduled = false, rollbackCommand = null)
            }

            CommandResultStatus.FAILED -> {
                val error = request.error?.trim()?.takeIf { it.isNotEmpty() }?.take(2000)
                if (command.attempts < command.maxAttempts) {
                    val retryAt = now.plusSeconds(computeBackoffSeconds(command.attempts))
                    val updated = commandRepository.requeueForRetry(
                        id = commandId,
                        error = error,
                        scheduledAt = retryAt,
                        updatedAt = now,
                    ) ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Command $commandId state changed")

                    logger.warn(
                        "COMMAND_RETRY id={} action={} attempt={}/{} retryAt={} error={}",
                        updated.id,
                        updated.action,
                        updated.attempts,
                        updated.maxAttempts,
                        updated.scheduledAt,
                        error ?: "unknown",
                    )
                    CommandCompletionResult(command = updated, retryScheduled = true, rollbackCommand = null)
                } else {
                    val failed = commandRepository.markFailed(commandId, error, now)
                        ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Command $commandId state changed")
                    val rollback = enqueueRollbackFor(failed, now)
                    logger.error(
                        "COMMAND_FAILED id={} action={} attempts={} error={} rollbackQueued={}",
                        failed.id,
                        failed.action,
                        failed.attempts,
                        failed.lastError ?: "unknown",
                        rollback?.id != null,
                    )
                    CommandCompletionResult(command = failed, retryScheduled = false, rollbackCommand = rollback)
                }
            }
        }
    }

    private fun enqueueRollbackFor(command: AutomationCommand, now: Instant): AutomationCommand? {
        val rollbackAction = command.rollbackAction?.takeIf { it.isNotBlank() } ?: return null
        val rollbackParams = command.rollbackParams ?: objectMapper.createObjectNode()

        return commandRepository.enqueue(
            AutomationCommand(
                action = rollbackAction,
                params = rollbackParams,
                source = "rollback:${command.id}",
                status = CommandStatus.QUEUED,
                priority = command.priority,
                attempts = 0,
                maxAttempts = 1,
                scheduledAt = now,
                claimedBy = null,
                claimedAt = null,
                lastError = null,
                rollbackAction = null,
                rollbackParams = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    private fun mergeParams(request: CommandRequest): Map<String, Any?> {
        val merged = linkedMapOf<String, Any?>()
        merged.putAll(request.params)

        val app = request.app?.trim()?.takeIf { it.isNotEmpty() }
        if (app != null && !merged.containsKey("app")) {
            merged["app"] = app
        }
        return merged
    }

    private fun computeBackoffSeconds(attempts: Int): Long {
        val exponent = attempts.coerceIn(1, 6)
        return (5L shl (exponent - 1)).coerceAtMost(300L)
    }
}
