package com.jarvis.core.application

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.jarvis.core.adapters.db.AutomationCommandRepository
import com.jarvis.core.adapters.db.AutomationWorkflowRepository
import com.jarvis.core.api.CommandResultStatus
import com.jarvis.core.api.WorkflowConditionOperator
import com.jarvis.core.api.WorkflowConditionRequest
import com.jarvis.core.api.WorkflowRequest
import com.jarvis.core.api.WorkflowStepRequest
import com.jarvis.core.command.AutomationCommand
import com.jarvis.core.command.CommandStatus
import com.jarvis.core.workflow.AutomationWorkflowRun
import com.jarvis.core.workflow.AutomationWorkflowStep
import com.jarvis.core.workflow.WorkflowRunStatus
import com.jarvis.core.workflow.WorkflowStepStatus
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

data class WorkflowRunDetails(
    val run: AutomationWorkflowRun,
    val steps: List<AutomationWorkflowStep>,
)

@Service
class WorkflowService(
    private val workflowRepository: AutomationWorkflowRepository,
    private val commandRepository: AutomationCommandRepository,
    private val objectMapper: ObjectMapper,
) : CommandCompletionListener {

    private val logger = LoggerFactory.getLogger(WorkflowService::class.java)

    @Transactional
    fun submit(request: WorkflowRequest): WorkflowRunDetails {
        val now = Instant.now()
        val normalizedSource = request.source?.trim()?.takeIf { it.isNotEmpty() } ?: "jarvis-core-api"
        val normalizedName = request.name?.trim()?.takeIf { it.isNotEmpty() }
        val conditionPayload = request.condition?.let { objectMapper.valueToTree<JsonNode>(it) }
        val contextPayload = objectMapper.valueToTree<JsonNode>(request.context)
        val conditionMatched = evaluateCondition(request.condition, request.context)
        val initialStatus = if (conditionMatched) WorkflowRunStatus.IN_PROGRESS else WorkflowRunStatus.SKIPPED
        val initialCompletedSteps = if (conditionMatched) 0 else request.steps.size

        val run = workflowRepository.createRun(
            AutomationWorkflowRun(
                name = normalizedName,
                source = normalizedSource,
                status = initialStatus,
                conditionPayload = conditionPayload,
                contextPayload = contextPayload,
                totalSteps = request.steps.size,
                completedSteps = initialCompletedSteps,
                lastError = null,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val runId = run.id ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create workflow run")

        val steps = request.steps.mapIndexed { index, step ->
            workflowRepository.createStep(
                AutomationWorkflowStep(
                    workflowRunId = runId,
                    stepOrder = index + 1,
                    action = step.action.trim().uppercase(),
                    params = objectMapper.valueToTree<JsonNode>(mergeStepParams(step)),
                    priority = step.priority,
                    maxAttempts = step.maxAttempts,
                    rollbackAction = step.rollbackAction?.trim()?.uppercase()?.takeIf { it.isNotEmpty() },
                    rollbackParams = if (step.rollbackAction.isNullOrBlank()) {
                        null
                    } else {
                        objectMapper.valueToTree<JsonNode>(step.rollbackParams)
                    },
                    status = if (conditionMatched) WorkflowStepStatus.QUEUED else WorkflowStepStatus.SKIPPED,
                    commandId = null,
                    lastError = if (conditionMatched) null else "Workflow condition evaluated to false",
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }

        if (!conditionMatched) {
            logger.info("WORKFLOW_SKIPPED id={} source={} reason=condition_false", runId, normalizedSource)
            return WorkflowRunDetails(
                run = workflowRepository.findRunById(runId) ?: run,
                steps = workflowRepository.findStepsByRunId(runId),
            )
        }

        val firstStep = steps.firstOrNull()
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Workflow requires at least one step")
        enqueueStepCommand(
            runId = runId,
            step = firstStep,
            now = now,
        )
        logger.info("WORKFLOW_ENQUEUED id={} steps={}", runId, steps.size)

        return WorkflowRunDetails(
            run = workflowRepository.findRunById(runId) ?: run,
            steps = workflowRepository.findStepsByRunId(runId),
        )
    }

    fun recent(limit: Int): List<WorkflowRunDetails> {
        val safeLimit = limit.coerceIn(1, 200)
        return workflowRepository.findRecentRuns(safeLimit).map { run ->
            WorkflowRunDetails(
                run = run,
                steps = workflowRepository.findStepsByRunId(run.id!!),
            )
        }
    }

    fun get(runId: Long): WorkflowRunDetails {
        val run = workflowRepository.findRunById(runId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow $runId not found")
        val steps = workflowRepository.findStepsByRunId(runId)
        return WorkflowRunDetails(run = run, steps = steps)
    }

    @Transactional
    override fun onCommandCompletion(
        command: AutomationCommand,
        resultStatus: CommandResultStatus,
        retryScheduled: Boolean,
        rollbackQueued: Boolean,
    ) {
        val commandId = command.id ?: return
        val step = workflowRepository.findStepByCommandId(commandId) ?: return
        val run = workflowRepository.findRunById(step.workflowRunId) ?: return
        if (run.status == WorkflowRunStatus.FAILED || run.status == WorkflowRunStatus.SUCCEEDED || run.status == WorkflowRunStatus.SKIPPED) {
            return
        }

        val now = Instant.now()
        when (resultStatus) {
            CommandResultStatus.SUCCEEDED -> {
                workflowRepository.markStepStatusByCommandId(
                    commandId = commandId,
                    status = WorkflowStepStatus.SUCCEEDED,
                    lastError = null,
                    updatedAt = now,
                )
                val completedSteps = (run.completedSteps + 1).coerceAtMost(run.totalSteps)
                val nextStep = workflowRepository.findNextQueuedStep(run.id!!, step.stepOrder)
                if (nextStep == null) {
                    workflowRepository.updateRun(
                        runId = run.id,
                        status = WorkflowRunStatus.SUCCEEDED,
                        completedSteps = run.totalSteps,
                        lastError = null,
                        updatedAt = now,
                    )
                    logger.info("WORKFLOW_SUCCEEDED id={}", run.id)
                } else {
                    workflowRepository.updateRun(
                        runId = run.id,
                        status = WorkflowRunStatus.IN_PROGRESS,
                        completedSteps = completedSteps,
                        lastError = null,
                        updatedAt = now,
                    )
                    enqueueStepCommand(runId = run.id, step = nextStep, now = now)
                }
            }

            CommandResultStatus.FAILED -> {
                if (retryScheduled) {
                    logger.warn(
                        "WORKFLOW_STEP_RETRY workflowId={} stepOrder={} commandId={}",
                        run.id,
                        step.stepOrder,
                        commandId,
                    )
                    return
                }

                workflowRepository.markStepStatusByCommandId(
                    commandId = commandId,
                    status = WorkflowStepStatus.FAILED,
                    lastError = command.lastError,
                    updatedAt = now,
                )
                workflowRepository.updateRun(
                    runId = run.id!!,
                    status = WorkflowRunStatus.FAILED,
                    completedSteps = run.completedSteps,
                    lastError = command.lastError ?: "Command step failed",
                    updatedAt = now,
                )
                logger.error(
                    "WORKFLOW_FAILED id={} stepOrder={} commandId={} rollbackQueued={} error={}",
                    run.id,
                    step.stepOrder,
                    commandId,
                    rollbackQueued,
                    command.lastError ?: "unknown",
                )
            }
        }
    }

    private fun enqueueStepCommand(runId: Long, step: AutomationWorkflowStep, now: Instant) {
        val enrichedParams = step.params.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().apply {
            put("_workflowRunId", runId)
            put("_workflowStepOrder", step.stepOrder)
        }

        val command = commandRepository.enqueue(
            AutomationCommand(
                action = step.action,
                params = enrichedParams,
                source = "workflow:$runId",
                status = CommandStatus.QUEUED,
                priority = step.priority,
                attempts = 0,
                maxAttempts = step.maxAttempts,
                scheduledAt = now,
                claimedBy = null,
                claimedAt = null,
                lastError = null,
                rollbackAction = step.rollbackAction,
                rollbackParams = step.rollbackParams,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val commandId = command.id ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to enqueue step command")
        workflowRepository.markStepInProgress(
            runId = runId,
            stepOrder = step.stepOrder,
            commandId = commandId,
            updatedAt = now,
        )
    }

    private fun mergeStepParams(step: WorkflowStepRequest): Map<String, Any?> {
        val merged = linkedMapOf<String, Any?>()
        merged.putAll(step.params)
        val app = step.app?.trim()?.takeIf { it.isNotEmpty() }
        if (app != null && !merged.containsKey("app")) {
            merged["app"] = app
        }
        return merged
    }

    private fun evaluateCondition(condition: WorkflowConditionRequest?, context: Map<String, Any?>): Boolean {
        if (condition == null) {
            return true
        }

        val leftValue = context[condition.leftKey] ?: return false
        val rightValue = condition.rightValue
        return when (condition.operator) {
            WorkflowConditionOperator.EQ -> leftValue == rightValue
            WorkflowConditionOperator.NEQ -> leftValue != rightValue
            WorkflowConditionOperator.LT -> compareNumbers(leftValue, rightValue) < 0
            WorkflowConditionOperator.LTE -> compareNumbers(leftValue, rightValue) <= 0
            WorkflowConditionOperator.GT -> compareNumbers(leftValue, rightValue) > 0
            WorkflowConditionOperator.GTE -> compareNumbers(leftValue, rightValue) >= 0
        }
    }

    private fun compareNumbers(left: Any?, right: Any?): Int {
        val leftNumber = left.toString().toDoubleOrNull()
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Condition left value is not numeric")
        val rightNumber = right?.toString()?.toDoubleOrNull()
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Condition right value is not numeric")
        return leftNumber.compareTo(rightNumber)
    }
}
