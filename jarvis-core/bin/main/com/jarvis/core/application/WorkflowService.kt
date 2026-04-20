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
    private val dependencyKey = "_dependsOn"

    @Transactional
    fun submit(request: WorkflowRequest): WorkflowRunDetails {
        val now = Instant.now()
        val normalizedSource = request.source?.trim()?.takeIf { it.isNotEmpty() } ?: "jarvis-core-api"
        val normalizedName = request.name?.trim()?.takeIf { it.isNotEmpty() }
        val dependencyGraph = buildDependencyGraph(request.steps)
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
            val stepOrder = index + 1
            workflowRepository.createStep(
                AutomationWorkflowStep(
                    workflowRunId = runId,
                    stepOrder = stepOrder,
                    action = step.action.trim().uppercase(),
                    params = objectMapper.valueToTree<JsonNode>(
                        mergeStepParams(
                            step = step,
                            dependsOn = dependencyGraph.getValue(stepOrder),
                        ),
                    ),
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

        val readySteps = findReadyQueuedSteps(steps)
        if (readySteps.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Workflow has no executable root steps")
        }
        readySteps.forEach { readyStep ->
            enqueueStepCommand(
                runId = runId,
                step = readyStep,
                now = now,
            )
        }
        logger.info("WORKFLOW_ENQUEUED id={} steps={} roots={}", runId, steps.size, readySteps.size)

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
                val runId = run.id!!
                val refreshedSteps = workflowRepository.findStepsByRunId(runId)
                val completedSteps = refreshedSteps.count { it.status == WorkflowStepStatus.SUCCEEDED }
                val readySteps = findReadyQueuedSteps(refreshedSteps)
                val hasInProgress = refreshedSteps.any { it.status == WorkflowStepStatus.IN_PROGRESS }
                val hasQueued = refreshedSteps.any { it.status == WorkflowStepStatus.QUEUED }

                if (readySteps.isEmpty() && !hasInProgress && hasQueued) {
                    workflowRepository.updateRun(
                        runId = runId,
                        status = WorkflowRunStatus.FAILED,
                        completedSteps = completedSteps,
                        lastError = "Workflow deadlock: queued steps have unmet dependencies",
                        updatedAt = now,
                    )
                    logger.error("WORKFLOW_DEADLOCK id={} queuedSteps={}", runId, refreshedSteps.count { it.status == WorkflowStepStatus.QUEUED })
                    return
                }

                readySteps.forEach { nextStep ->
                    enqueueStepCommand(runId = runId, step = nextStep, now = now)
                }

                val allFinished = !hasQueued && !hasInProgress && readySteps.isEmpty()
                if (allFinished) {
                    workflowRepository.updateRun(
                        runId = runId,
                        status = WorkflowRunStatus.SUCCEEDED,
                        completedSteps = completedSteps,
                        lastError = null,
                        updatedAt = now,
                    )
                    logger.info("WORKFLOW_SUCCEEDED id={}", runId)
                } else {
                    workflowRepository.updateRun(
                        runId = runId,
                        status = WorkflowRunStatus.IN_PROGRESS,
                        completedSteps = completedSteps,
                        lastError = null,
                        updatedAt = now,
                    )
                    if (readySteps.isNotEmpty()) {
                        logger.info("WORKFLOW_PARALLEL_SCHEDULED id={} newlyScheduled={}", runId, readySteps.size)
                    }
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
                val runId = run.id!!
                markRemainingQueuedStepsSkipped(
                    runId = runId,
                    now = now,
                    reason = "Blocked by failed dependency at step ${step.stepOrder}",
                )
                val refreshedSteps = workflowRepository.findStepsByRunId(runId)
                val completedSteps = refreshedSteps.count { it.status == WorkflowStepStatus.SUCCEEDED }
                workflowRepository.updateRun(
                    runId = runId,
                    status = WorkflowRunStatus.FAILED,
                    completedSteps = completedSteps,
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

    private fun mergeStepParams(step: WorkflowStepRequest, dependsOn: List<Int>): Map<String, Any?> {
        val merged = linkedMapOf<String, Any?>()
        merged.putAll(step.params)
        val app = step.app?.trim()?.takeIf { it.isNotEmpty() }
        if (app != null && !merged.containsKey("app")) {
            merged["app"] = app
        }
        merged[dependencyKey] = dependsOn
        return merged
    }

    private fun buildDependencyGraph(steps: List<WorkflowStepRequest>): Map<Int, List<Int>> {
        if (steps.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Workflow requires at least one step")
        }
        val totalSteps = steps.size
        val validOrders = (1..totalSteps).toSet()
        val graph = linkedMapOf<Int, List<Int>>()

        steps.forEachIndexed { index, step ->
            val stepOrder = index + 1
            val resolved = (step.dependsOn ?: defaultDependencies(stepOrder))
                .distinct()
                .sorted()

            resolved.forEach { dependency ->
                if (!validOrders.contains(dependency)) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Step $stepOrder has invalid dependency $dependency",
                    )
                }
                if (dependency == stepOrder) {
                    throw ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Step $stepOrder cannot depend on itself",
                    )
                }
            }
            graph[stepOrder] = resolved
        }

        if (graph.values.none { it.isEmpty() }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Workflow must have at least one root step")
        }

        val state = mutableMapOf<Int, Int>()
        fun visit(node: Int) {
            when (state[node]) {
                1 -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Workflow dependencies contain a cycle")
                2 -> return
            }
            state[node] = 1
            graph[node].orEmpty().forEach { dependency -> visit(dependency) }
            state[node] = 2
        }
        validOrders.forEach { visit(it) }
        return graph
    }

    private fun defaultDependencies(stepOrder: Int): List<Int> {
        return if (stepOrder == 1) emptyList() else listOf(stepOrder - 1)
    }

    private fun findReadyQueuedSteps(steps: List<AutomationWorkflowStep>): List<AutomationWorkflowStep> {
        val stepByOrder = steps.associateBy { it.stepOrder }
        return steps
            .filter { it.status == WorkflowStepStatus.QUEUED }
            .filter { step ->
                dependenciesFor(step).all { dependencyOrder ->
                    stepByOrder[dependencyOrder]?.status == WorkflowStepStatus.SUCCEEDED
                }
            }
            .sortedBy { it.stepOrder }
    }

    private fun dependenciesFor(step: AutomationWorkflowStep): List<Int> {
        val node = step.params.path(dependencyKey)
        if (!node.isArray) {
            return defaultDependencies(step.stepOrder)
        }
        return node.mapNotNull { dependency ->
            when {
                dependency.isInt -> dependency.asInt()
                dependency.isLong -> dependency.asLong().toInt()
                dependency.isTextual -> dependency.asText().toIntOrNull()
                else -> null
            }
        }.distinct().sorted()
    }

    private fun markRemainingQueuedStepsSkipped(runId: Long, now: Instant, reason: String) {
        val currentSteps = workflowRepository.findStepsByRunId(runId)
        currentSteps
            .filter { it.status == WorkflowStepStatus.QUEUED }
            .forEach { queuedStep ->
                workflowRepository.markStepStatus(
                    runId = runId,
                    stepOrder = queuedStep.stepOrder,
                    status = WorkflowStepStatus.SKIPPED,
                    lastError = reason,
                    updatedAt = now,
                )
            }
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
