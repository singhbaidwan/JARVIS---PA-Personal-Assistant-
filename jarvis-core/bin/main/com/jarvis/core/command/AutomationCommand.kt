package com.jarvis.core.command

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant

enum class CommandStatus {
    QUEUED,
    IN_PROGRESS,
    SUCCEEDED,
    FAILED,
}

data class AutomationCommand(
    val id: Long? = null,
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
