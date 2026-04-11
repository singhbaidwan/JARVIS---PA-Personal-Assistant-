package com.jarvis.core.adapters.db

import com.fasterxml.jackson.databind.ObjectMapper
import com.jarvis.core.workflow.AutomationWorkflowRun
import com.jarvis.core.workflow.AutomationWorkflowStep
import com.jarvis.core.workflow.WorkflowRunStatus
import com.jarvis.core.workflow.WorkflowStepStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.PreparedStatementCreator
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.jdbc.support.KeyHolder
import org.springframework.stereotype.Repository
import java.sql.Statement
import java.time.Instant

interface AutomationWorkflowRepository {
    fun createRun(run: AutomationWorkflowRun): AutomationWorkflowRun
    fun findRunById(runId: Long): AutomationWorkflowRun?
    fun findRecentRuns(limit: Int): List<AutomationWorkflowRun>
    fun updateRun(runId: Long, status: WorkflowRunStatus, completedSteps: Int, lastError: String?, updatedAt: Instant): AutomationWorkflowRun?
    fun createStep(step: AutomationWorkflowStep): AutomationWorkflowStep
    fun findStepsByRunId(runId: Long): List<AutomationWorkflowStep>
    fun findStepByRunAndOrder(runId: Long, stepOrder: Int): AutomationWorkflowStep?
    fun findStepByCommandId(commandId: Long): AutomationWorkflowStep?
    fun findNextQueuedStep(runId: Long, afterStepOrder: Int): AutomationWorkflowStep?
    fun markStepInProgress(runId: Long, stepOrder: Int, commandId: Long, updatedAt: Instant): AutomationWorkflowStep?
    fun markStepStatusByCommandId(commandId: Long, status: WorkflowStepStatus, lastError: String?, updatedAt: Instant): AutomationWorkflowStep?
    fun markStepStatus(runId: Long, stepOrder: Int, status: WorkflowStepStatus, lastError: String?, updatedAt: Instant): AutomationWorkflowStep?
}

@Repository
class SqliteAutomationWorkflowRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) : AutomationWorkflowRepository {

    override fun createRun(run: AutomationWorkflowRun): AutomationWorkflowRun {
        val sql = """
            INSERT INTO automation_workflow_runs(
                name, source, status, condition_payload, context_payload,
                total_steps, completed_steps, last_error, created_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val keyHolder: KeyHolder = GeneratedKeyHolder()
        val statementCreator = PreparedStatementCreator { connection ->
            connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).apply {
                setString(1, run.name)
                setString(2, run.source)
                setString(3, run.status.name)
                setString(4, run.conditionPayload?.let(objectMapper::writeValueAsString))
                setString(5, run.contextPayload?.let(objectMapper::writeValueAsString))
                setInt(6, run.totalSteps)
                setInt(7, run.completedSteps)
                setString(8, run.lastError)
                setString(9, run.createdAt.toString())
                setString(10, run.updatedAt.toString())
            }
        }

        jdbcTemplate.update(statementCreator, keyHolder)
        val generatedId = keyHolder.keys?.get("id") as? Number ?: keyHolder.key
        return run.copy(id = generatedId?.toLong())
    }

    override fun findRunById(runId: Long): AutomationWorkflowRun? {
        val sql = """
            SELECT id, name, source, status, condition_payload, context_payload,
                   total_steps, completed_steps, last_error, created_at, updated_at
            FROM automation_workflow_runs
            WHERE id = ?
            LIMIT 1
        """.trimIndent()
        return jdbcTemplate.query(sql, runRowMapper(), runId).firstOrNull()
    }

    override fun findRecentRuns(limit: Int): List<AutomationWorkflowRun> {
        val sql = """
            SELECT id, name, source, status, condition_payload, context_payload,
                   total_steps, completed_steps, last_error, created_at, updated_at
            FROM automation_workflow_runs
            ORDER BY julianday(created_at) DESC
            LIMIT ?
        """.trimIndent()
        return jdbcTemplate.query(sql, runRowMapper(), limit)
    }

    override fun updateRun(
        runId: Long,
        status: WorkflowRunStatus,
        completedSteps: Int,
        lastError: String?,
        updatedAt: Instant,
    ): AutomationWorkflowRun? {
        val rows = jdbcTemplate.update(
            """
                UPDATE automation_workflow_runs
                SET status = ?,
                    completed_steps = ?,
                    last_error = ?,
                    updated_at = ?
                WHERE id = ?
            """.trimIndent(),
            status.name,
            completedSteps,
            lastError,
            updatedAt.toString(),
            runId,
        )
        if (rows == 0) {
            return null
        }
        return findRunById(runId)
    }

    override fun createStep(step: AutomationWorkflowStep): AutomationWorkflowStep {
        val sql = """
            INSERT INTO automation_workflow_steps(
                workflow_run_id, step_order, action, params, priority, max_attempts,
                rollback_action, rollback_params, status, command_id, last_error,
                created_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val keyHolder: KeyHolder = GeneratedKeyHolder()
        val statementCreator = PreparedStatementCreator { connection ->
            connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).apply {
                setLong(1, step.workflowRunId)
                setInt(2, step.stepOrder)
                setString(3, step.action)
                setString(4, objectMapper.writeValueAsString(step.params))
                setInt(5, step.priority)
                setInt(6, step.maxAttempts)
                setString(7, step.rollbackAction)
                setString(8, step.rollbackParams?.let(objectMapper::writeValueAsString))
                setString(9, step.status.name)
                setObject(10, step.commandId)
                setString(11, step.lastError)
                setString(12, step.createdAt.toString())
                setString(13, step.updatedAt.toString())
            }
        }

        jdbcTemplate.update(statementCreator, keyHolder)
        val generatedId = keyHolder.keys?.get("id") as? Number ?: keyHolder.key
        return step.copy(id = generatedId?.toLong())
    }

    override fun findStepsByRunId(runId: Long): List<AutomationWorkflowStep> {
        val sql = """
            SELECT id, workflow_run_id, step_order, action, params, priority, max_attempts,
                   rollback_action, rollback_params, status, command_id, last_error, created_at, updated_at
            FROM automation_workflow_steps
            WHERE workflow_run_id = ?
            ORDER BY step_order ASC
        """.trimIndent()
        return jdbcTemplate.query(sql, stepRowMapper(), runId)
    }

    override fun findStepByRunAndOrder(runId: Long, stepOrder: Int): AutomationWorkflowStep? {
        val sql = """
            SELECT id, workflow_run_id, step_order, action, params, priority, max_attempts,
                   rollback_action, rollback_params, status, command_id, last_error, created_at, updated_at
            FROM automation_workflow_steps
            WHERE workflow_run_id = ?
              AND step_order = ?
            LIMIT 1
        """.trimIndent()
        return jdbcTemplate.query(sql, stepRowMapper(), runId, stepOrder).firstOrNull()
    }

    override fun findStepByCommandId(commandId: Long): AutomationWorkflowStep? {
        val sql = """
            SELECT id, workflow_run_id, step_order, action, params, priority, max_attempts,
                   rollback_action, rollback_params, status, command_id, last_error, created_at, updated_at
            FROM automation_workflow_steps
            WHERE command_id = ?
            LIMIT 1
        """.trimIndent()
        return jdbcTemplate.query(sql, stepRowMapper(), commandId).firstOrNull()
    }

    override fun findNextQueuedStep(runId: Long, afterStepOrder: Int): AutomationWorkflowStep? {
        val sql = """
            SELECT id, workflow_run_id, step_order, action, params, priority, max_attempts,
                   rollback_action, rollback_params, status, command_id, last_error, created_at, updated_at
            FROM automation_workflow_steps
            WHERE workflow_run_id = ?
              AND step_order > ?
              AND status = ?
            ORDER BY step_order ASC
            LIMIT 1
        """.trimIndent()
        return jdbcTemplate.query(sql, stepRowMapper(), runId, afterStepOrder, WorkflowStepStatus.QUEUED.name).firstOrNull()
    }

    override fun markStepInProgress(runId: Long, stepOrder: Int, commandId: Long, updatedAt: Instant): AutomationWorkflowStep? {
        val rows = jdbcTemplate.update(
            """
                UPDATE automation_workflow_steps
                SET status = ?,
                    command_id = ?,
                    updated_at = ?
                WHERE workflow_run_id = ?
                  AND step_order = ?
            """.trimIndent(),
            WorkflowStepStatus.IN_PROGRESS.name,
            commandId,
            updatedAt.toString(),
            runId,
            stepOrder,
        )
        if (rows == 0) {
            return null
        }
        return findStepByRunAndOrder(runId, stepOrder)
    }

    override fun markStepStatusByCommandId(
        commandId: Long,
        status: WorkflowStepStatus,
        lastError: String?,
        updatedAt: Instant,
    ): AutomationWorkflowStep? {
        val rows = jdbcTemplate.update(
            """
                UPDATE automation_workflow_steps
                SET status = ?,
                    last_error = ?,
                    updated_at = ?
                WHERE command_id = ?
            """.trimIndent(),
            status.name,
            lastError,
            updatedAt.toString(),
            commandId,
        )
        if (rows == 0) {
            return null
        }
        return findStepByCommandId(commandId)
    }

    override fun markStepStatus(
        runId: Long,
        stepOrder: Int,
        status: WorkflowStepStatus,
        lastError: String?,
        updatedAt: Instant,
    ): AutomationWorkflowStep? {
        val rows = jdbcTemplate.update(
            """
                UPDATE automation_workflow_steps
                SET status = ?,
                    last_error = ?,
                    updated_at = ?
                WHERE workflow_run_id = ?
                  AND step_order = ?
            """.trimIndent(),
            status.name,
            lastError,
            updatedAt.toString(),
            runId,
            stepOrder,
        )
        if (rows == 0) {
            return null
        }
        return findStepByRunAndOrder(runId, stepOrder)
    }

    private fun runRowMapper() = { rs: java.sql.ResultSet, _: Int ->
        AutomationWorkflowRun(
            id = rs.getLong("id"),
            name = rs.getString("name"),
            source = rs.getString("source"),
            status = WorkflowRunStatus.valueOf(rs.getString("status")),
            conditionPayload = rs.getString("condition_payload")?.let(objectMapper::readTree),
            contextPayload = rs.getString("context_payload")?.let(objectMapper::readTree),
            totalSteps = rs.getInt("total_steps"),
            completedSteps = rs.getInt("completed_steps"),
            lastError = rs.getString("last_error"),
            createdAt = Instant.parse(rs.getString("created_at")),
            updatedAt = Instant.parse(rs.getString("updated_at")),
        )
    }

    private fun stepRowMapper() = { rs: java.sql.ResultSet, _: Int ->
        AutomationWorkflowStep(
            id = rs.getLong("id"),
            workflowRunId = rs.getLong("workflow_run_id"),
            stepOrder = rs.getInt("step_order"),
            action = rs.getString("action"),
            params = objectMapper.readTree(rs.getString("params")),
            priority = rs.getInt("priority"),
            maxAttempts = rs.getInt("max_attempts"),
            rollbackAction = rs.getString("rollback_action"),
            rollbackParams = rs.getString("rollback_params")?.let(objectMapper::readTree),
            status = WorkflowStepStatus.valueOf(rs.getString("status")),
            commandId = rs.getObject("command_id")?.toString()?.toLongOrNull(),
            lastError = rs.getString("last_error"),
            createdAt = Instant.parse(rs.getString("created_at")),
            updatedAt = Instant.parse(rs.getString("updated_at")),
        )
    }
}
