package com.jarvis.core.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

class WorkflowServiceTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `submit creates run and enqueues first step`() {
        val commandRepo = FakeWorkflowCommandRepository()
        val workflowRepo = FakeWorkflowRepository()
        val service = WorkflowService(workflowRepo, commandRepo, objectMapper)

        val details = service.submit(
            WorkflowRequest(
                name = "battery saver",
                condition = WorkflowConditionRequest(
                    leftKey = "battery",
                    operator = WorkflowConditionOperator.LT,
                    rightValue = 20,
                ),
                context = mapOf("battery" to 15),
                steps = listOf(
                    WorkflowStepRequest(action = "OPEN_APP", app = "Calculator"),
                    WorkflowStepRequest(action = "CLOSE_APP", app = "Calculator"),
                ),
            ),
        )

        assertEquals(WorkflowRunStatus.IN_PROGRESS, details.run.status)
        assertEquals(2, details.steps.size)
        assertEquals(WorkflowStepStatus.IN_PROGRESS, details.steps[0].status)
        assertNotNull(details.steps[0].commandId)
        assertEquals(WorkflowStepStatus.QUEUED, details.steps[1].status)
        assertEquals(1, commandRepo.store.size)
    }

    @Test
    fun `successful step completion advances pipeline`() {
        val commandRepo = FakeWorkflowCommandRepository()
        val workflowRepo = FakeWorkflowRepository()
        val service = WorkflowService(workflowRepo, commandRepo, objectMapper)

        val details = service.submit(
            WorkflowRequest(
                steps = listOf(
                    WorkflowStepRequest(action = "OPEN_APP", app = "Calculator"),
                    WorkflowStepRequest(action = "CLOSE_APP", app = "Calculator"),
                ),
            ),
        )
        val runId = details.run.id!!
        val firstStepCommand = commandRepo.store.values.first()

        service.onCommandCompletion(
            command = firstStepCommand.copy(status = CommandStatus.SUCCEEDED, lastError = null),
            resultStatus = CommandResultStatus.SUCCEEDED,
            retryScheduled = false,
            rollbackQueued = false,
        )
        val mid = service.get(runId)
        assertEquals(1, mid.run.completedSteps)
        assertEquals(WorkflowStepStatus.SUCCEEDED, mid.steps[0].status)
        assertEquals(WorkflowStepStatus.IN_PROGRESS, mid.steps[1].status)
        assertEquals(2, commandRepo.store.size)

        val secondCommand = commandRepo.store.values.maxBy { it.id ?: 0L }
        service.onCommandCompletion(
            command = secondCommand.copy(status = CommandStatus.SUCCEEDED, lastError = null),
            resultStatus = CommandResultStatus.SUCCEEDED,
            retryScheduled = false,
            rollbackQueued = false,
        )

        val completed = service.get(runId)
        assertEquals(WorkflowRunStatus.SUCCEEDED, completed.run.status)
        assertEquals(2, completed.run.completedSteps)
        assertEquals(WorkflowStepStatus.SUCCEEDED, completed.steps[1].status)
    }

    @Test
    fun `parallel roots are enqueued and dependent step waits for both`() {
        val commandRepo = FakeWorkflowCommandRepository()
        val workflowRepo = FakeWorkflowRepository()
        val service = WorkflowService(workflowRepo, commandRepo, objectMapper)

        val details = service.submit(
            WorkflowRequest(
                steps = listOf(
                    WorkflowStepRequest(action = "OPEN_APP", app = "VS Code", dependsOn = emptyList()),
                    WorkflowStepRequest(action = "OPEN_APP", app = "Chrome", dependsOn = emptyList()),
                    WorkflowStepRequest(action = "OPEN_APP", app = "Slack", dependsOn = listOf(1, 2)),
                ),
            ),
        )

        val runId = details.run.id!!
        assertEquals(2, commandRepo.store.size)
        val initial = service.get(runId)
        assertEquals(WorkflowStepStatus.IN_PROGRESS, initial.steps[0].status)
        assertEquals(WorkflowStepStatus.IN_PROGRESS, initial.steps[1].status)
        assertEquals(WorkflowStepStatus.QUEUED, initial.steps[2].status)

        val firstCommand = commandRepo.store.getValue(initial.steps[0].commandId!!)
        service.onCommandCompletion(
            command = firstCommand.copy(status = CommandStatus.SUCCEEDED, lastError = null),
            resultStatus = CommandResultStatus.SUCCEEDED,
            retryScheduled = false,
            rollbackQueued = false,
        )
        val afterFirst = service.get(runId)
        assertEquals(WorkflowStepStatus.SUCCEEDED, afterFirst.steps[0].status)
        assertEquals(WorkflowStepStatus.IN_PROGRESS, afterFirst.steps[1].status)
        assertEquals(WorkflowStepStatus.QUEUED, afterFirst.steps[2].status)
        assertEquals(2, commandRepo.store.size)

        val secondCommand = commandRepo.store.getValue(afterFirst.steps[1].commandId!!)
        service.onCommandCompletion(
            command = secondCommand.copy(status = CommandStatus.SUCCEEDED, lastError = null),
            resultStatus = CommandResultStatus.SUCCEEDED,
            retryScheduled = false,
            rollbackQueued = false,
        )

        val afterSecond = service.get(runId)
        assertEquals(WorkflowStepStatus.SUCCEEDED, afterSecond.steps[0].status)
        assertEquals(WorkflowStepStatus.SUCCEEDED, afterSecond.steps[1].status)
        assertEquals(WorkflowStepStatus.IN_PROGRESS, afterSecond.steps[2].status)
        assertEquals(3, commandRepo.store.size)
    }

    @Test
    fun `cyclic dependencies are rejected`() {
        val commandRepo = FakeWorkflowCommandRepository()
        val workflowRepo = FakeWorkflowRepository()
        val service = WorkflowService(workflowRepo, commandRepo, objectMapper)

        assertThrows(ResponseStatusException::class.java) {
            service.submit(
                WorkflowRequest(
                    steps = listOf(
                        WorkflowStepRequest(action = "OPEN_APP", app = "VS Code", dependsOn = listOf(2)),
                        WorkflowStepRequest(action = "OPEN_APP", app = "Chrome", dependsOn = listOf(1)),
                    ),
                ),
            )
        }
    }
}

private class FakeWorkflowCommandRepository : AutomationCommandRepository {
    private var nextId = 1L
    val store = linkedMapOf<Long, AutomationCommand>()

    override fun enqueue(command: AutomationCommand): AutomationCommand {
        val saved = command.copy(id = nextId++)
        store[saved.id!!] = saved
        return saved
    }

    override fun findRecent(limit: Int): List<AutomationCommand> = store.values.take(limit)

    override fun findById(id: Long): AutomationCommand? = store[id]

    override fun claimNext(agentId: String, now: Instant): AutomationCommand? = null

    override fun recoverStaleClaims(staleBefore: Instant, now: Instant): Int = 0

    override fun markSucceeded(id: Long, finishedAt: Instant): AutomationCommand? = null

    override fun requeueForRetry(id: Long, error: String?, scheduledAt: Instant, updatedAt: Instant): AutomationCommand? = null

    override fun markFailed(id: Long, error: String?, updatedAt: Instant): AutomationCommand? = null
}

private class FakeWorkflowRepository : AutomationWorkflowRepository {
    private var nextRunId = 1L
    private var nextStepId = 1L
    private val runs = linkedMapOf<Long, AutomationWorkflowRun>()
    private val steps = linkedMapOf<Long, AutomationWorkflowStep>()

    override fun createRun(run: AutomationWorkflowRun): AutomationWorkflowRun {
        val saved = run.copy(id = nextRunId++)
        runs[saved.id!!] = saved
        return saved
    }

    override fun findRunById(runId: Long): AutomationWorkflowRun? = runs[runId]

    override fun findRecentRuns(limit: Int): List<AutomationWorkflowRun> = runs.values.take(limit)

    override fun updateRun(
        runId: Long,
        status: WorkflowRunStatus,
        completedSteps: Int,
        lastError: String?,
        updatedAt: Instant,
    ): AutomationWorkflowRun? {
        val run = runs[runId] ?: return null
        val updated = run.copy(
            status = status,
            completedSteps = completedSteps,
            lastError = lastError,
            updatedAt = updatedAt,
        )
        runs[runId] = updated
        return updated
    }

    override fun createStep(step: AutomationWorkflowStep): AutomationWorkflowStep {
        val saved = step.copy(id = nextStepId++)
        steps[saved.id!!] = saved
        return saved
    }

    override fun findStepsByRunId(runId: Long): List<AutomationWorkflowStep> {
        return steps.values.filter { it.workflowRunId == runId }.sortedBy { it.stepOrder }
    }

    override fun findStepByRunAndOrder(runId: Long, stepOrder: Int): AutomationWorkflowStep? {
        return steps.values.firstOrNull { it.workflowRunId == runId && it.stepOrder == stepOrder }
    }

    override fun findStepByCommandId(commandId: Long): AutomationWorkflowStep? {
        return steps.values.firstOrNull { it.commandId == commandId }
    }

    override fun findNextQueuedStep(runId: Long, afterStepOrder: Int): AutomationWorkflowStep? {
        return steps.values
            .filter { it.workflowRunId == runId && it.stepOrder > afterStepOrder && it.status == WorkflowStepStatus.QUEUED }
            .sortedBy { it.stepOrder }
            .firstOrNull()
    }

    override fun markStepInProgress(runId: Long, stepOrder: Int, commandId: Long, updatedAt: Instant): AutomationWorkflowStep? {
        val step = findStepByRunAndOrder(runId, stepOrder) ?: return null
        val updated = step.copy(
            status = WorkflowStepStatus.IN_PROGRESS,
            commandId = commandId,
            updatedAt = updatedAt,
        )
        steps[updated.id!!] = updated
        return updated
    }

    override fun markStepStatusByCommandId(
        commandId: Long,
        status: WorkflowStepStatus,
        lastError: String?,
        updatedAt: Instant,
    ): AutomationWorkflowStep? {
        val step = findStepByCommandId(commandId) ?: return null
        val updated = step.copy(
            status = status,
            lastError = lastError,
            updatedAt = updatedAt,
        )
        steps[updated.id!!] = updated
        return updated
    }

    override fun markStepStatus(
        runId: Long,
        stepOrder: Int,
        status: WorkflowStepStatus,
        lastError: String?,
        updatedAt: Instant,
    ): AutomationWorkflowStep? {
        val step = findStepByRunAndOrder(runId, stepOrder) ?: return null
        val updated = step.copy(
            status = status,
            lastError = lastError,
            updatedAt = updatedAt,
        )
        steps[updated.id!!] = updated
        return updated
    }
}
