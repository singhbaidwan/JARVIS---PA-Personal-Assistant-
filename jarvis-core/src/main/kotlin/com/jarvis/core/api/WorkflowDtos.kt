package com.jarvis.core.api

import com.fasterxml.jackson.databind.JsonNode
import com.jarvis.core.workflow.AutomationWorkflowRun
import com.jarvis.core.workflow.AutomationWorkflowStep
import com.jarvis.core.workflow.WorkflowRunStatus
import com.jarvis.core.workflow.WorkflowStepStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.Instant

enum class WorkflowConditionOperator {
    LT,
    LTE,
    GT,
    GTE,
    EQ,
    NEQ,
}

data class WorkflowConditionRequest(
    @field:NotBlank
    val leftKey: String,
    val operator: WorkflowConditionOperator,
    val rightValue: Any?,
)

data class WorkflowStepRequest(
    @field:NotBlank
    val action: String,
    val app: String? = null,
    val params: Map<String, Any?> = emptyMap(),
    @field:Min(1)
    @field:Max(10)
    val priority: Int = 5,
    @field:Min(1)
    @field:Max(10)
    val maxAttempts: Int = 3,
    @field:Size(max = 24)
    val dependsOn: List<Int>? = null,
    val rollbackAction: String? = null,
    val rollbackParams: Map<String, Any?> = emptyMap(),
)

data class WorkflowRequest(
    val name: String? = null,
    val source: String? = "jarvis-core-api",
    @field:Valid
    val condition: WorkflowConditionRequest? = null,
    val context: Map<String, Any?> = emptyMap(),
    @field:Size(min = 1, max = 25)
    @field:Valid
    val steps: List<WorkflowStepRequest>,
)

data class WorkflowStepResponse(
    val id: Long?,
    val workflowRunId: Long,
    val stepOrder: Int,
    val action: String,
    val params: JsonNode,
    val priority: Int,
    val maxAttempts: Int,
    val dependsOn: List<Int>,
    val rollbackAction: String?,
    val rollbackParams: JsonNode?,
    val status: WorkflowStepStatus,
    val commandId: Long?,
    val lastError: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class WorkflowRunResponse(
    val id: Long?,
    val name: String?,
    val source: String?,
    val status: WorkflowRunStatus,
    val totalSteps: Int,
    val completedSteps: Int,
    val lastError: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val steps: List<WorkflowStepResponse>,
)

fun AutomationWorkflowStep.toResponse(): WorkflowStepResponse = WorkflowStepResponse(
    id = id,
    workflowRunId = workflowRunId,
    stepOrder = stepOrder,
    action = action,
    params = params,
    priority = priority,
    maxAttempts = maxAttempts,
    dependsOn = params.extractDependsOn(),
    rollbackAction = rollbackAction,
    rollbackParams = rollbackParams,
    status = status,
    commandId = commandId,
    lastError = lastError,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun JsonNode.extractDependsOn(): List<Int> {
    val dependencyNode = this.path("_dependsOn")
    if (!dependencyNode.isArray) {
        return emptyList()
    }
    return dependencyNode
        .mapNotNull { entry ->
            when {
                entry.isInt -> entry.asInt()
                entry.isLong -> entry.asLong().toInt()
                entry.isTextual -> entry.asText().toIntOrNull()
                else -> null
            }
        }
        .distinct()
        .sorted()
}

fun AutomationWorkflowRun.toResponse(steps: List<AutomationWorkflowStep>): WorkflowRunResponse = WorkflowRunResponse(
    id = id,
    name = name,
    source = source,
    status = status,
    totalSteps = totalSteps,
    completedSteps = completedSteps,
    lastError = lastError,
    createdAt = createdAt,
    updatedAt = updatedAt,
    steps = steps.map { it.toResponse() },
)
