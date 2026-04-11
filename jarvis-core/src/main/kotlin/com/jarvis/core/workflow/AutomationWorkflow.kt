package com.jarvis.core.workflow

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant

enum class WorkflowRunStatus {
    QUEUED,
    IN_PROGRESS,
    SUCCEEDED,
    FAILED,
    SKIPPED,
}

enum class WorkflowStepStatus {
    QUEUED,
    IN_PROGRESS,
    SUCCEEDED,
    FAILED,
    SKIPPED,
}

data class AutomationWorkflowRun(
    val id: Long? = null,
    val name: String?,
    val source: String?,
    val status: WorkflowRunStatus,
    val conditionPayload: JsonNode?,
    val contextPayload: JsonNode?,
    val totalSteps: Int,
    val completedSteps: Int,
    val lastError: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class AutomationWorkflowStep(
    val id: Long? = null,
    val workflowRunId: Long,
    val stepOrder: Int,
    val action: String,
    val params: JsonNode,
    val priority: Int,
    val maxAttempts: Int,
    val rollbackAction: String?,
    val rollbackParams: JsonNode?,
    val status: WorkflowStepStatus,
    val commandId: Long?,
    val lastError: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)
