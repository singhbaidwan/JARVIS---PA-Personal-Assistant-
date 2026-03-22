package com.jarvis.core.adapters.db

import com.fasterxml.jackson.databind.ObjectMapper
import com.jarvis.core.command.AutomationCommand
import com.jarvis.core.command.CommandStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.PreparedStatementCreator
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.jdbc.support.KeyHolder
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.Statement
import java.time.Instant

interface AutomationCommandRepository {
    fun enqueue(command: AutomationCommand): AutomationCommand
    fun findRecent(limit: Int): List<AutomationCommand>
    fun findById(id: Long): AutomationCommand?
    fun claimNext(agentId: String, now: Instant): AutomationCommand?
    fun markSucceeded(id: Long, finishedAt: Instant): AutomationCommand?
    fun requeueForRetry(id: Long, error: String?, scheduledAt: Instant, updatedAt: Instant): AutomationCommand?
    fun markFailed(id: Long, error: String?, updatedAt: Instant): AutomationCommand?
}

@Repository
class SqliteAutomationCommandRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) : AutomationCommandRepository {

    override fun enqueue(command: AutomationCommand): AutomationCommand {
        val sql = """
            INSERT INTO automation_commands(
                action, params, source, status, priority, attempts, max_attempts,
                scheduled_at, claimed_by, claimed_at, last_error, rollback_action,
                rollback_params, created_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        val keyHolder: KeyHolder = GeneratedKeyHolder()
        val paramsJson = objectMapper.writeValueAsString(command.params)
        val rollbackParamsJson = command.rollbackParams?.let(objectMapper::writeValueAsString)

        val statementCreator = PreparedStatementCreator { connection ->
            connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).apply {
                setString(1, command.action)
                setString(2, paramsJson)
                setString(3, command.source)
                setString(4, command.status.name)
                setInt(5, command.priority)
                setInt(6, command.attempts)
                setInt(7, command.maxAttempts)
                setString(8, command.scheduledAt.toString())
                setString(9, command.claimedBy)
                setString(10, command.claimedAt?.toString())
                setString(11, command.lastError)
                setString(12, command.rollbackAction)
                setString(13, rollbackParamsJson)
                setString(14, command.createdAt.toString())
                setString(15, command.updatedAt.toString())
            }
        }

        jdbcTemplate.update(statementCreator, keyHolder)
        val generatedId = keyHolder.keys?.get("id") as? Number ?: keyHolder.key
        return command.copy(id = generatedId?.toLong())
    }

    override fun findRecent(limit: Int): List<AutomationCommand> {
        val sql = """
            SELECT id, action, params, source, status, priority, attempts, max_attempts,
                   scheduled_at, claimed_by, claimed_at, last_error, rollback_action,
                   rollback_params, created_at, updated_at
            FROM automation_commands
            ORDER BY julianday(created_at) DESC
            LIMIT ?
        """.trimIndent()

        return jdbcTemplate.query(sql, commandRowMapper(), limit)
    }

    override fun findById(id: Long): AutomationCommand? {
        val sql = """
            SELECT id, action, params, source, status, priority, attempts, max_attempts,
                   scheduled_at, claimed_by, claimed_at, last_error, rollback_action,
                   rollback_params, created_at, updated_at
            FROM automation_commands
            WHERE id = ?
            LIMIT 1
        """.trimIndent()

        return jdbcTemplate.query(sql, commandRowMapper(), id).firstOrNull()
    }

    @Transactional
    override fun claimNext(agentId: String, now: Instant): AutomationCommand? {
        repeat(5) {
            val nextId = findClaimableCommandId(now) ?: return null
            val nowValue = now.toString()
            val rows = jdbcTemplate.update(
                """
                    UPDATE automation_commands
                    SET status = ?,
                        claimed_by = ?,
                        claimed_at = ?,
                        attempts = attempts + 1,
                        updated_at = ?
                    WHERE id = ?
                      AND status = ?
                      AND julianday(scheduled_at) <= julianday(?)
                """.trimIndent(),
                CommandStatus.IN_PROGRESS.name,
                agentId,
                nowValue,
                nowValue,
                nextId,
                CommandStatus.QUEUED.name,
                nowValue,
            )

            if (rows > 0) {
                return findById(nextId)
            }
        }

        return null
    }

    override fun markSucceeded(id: Long, finishedAt: Instant): AutomationCommand? {
        val nowValue = finishedAt.toString()
        val rows = jdbcTemplate.update(
            """
                UPDATE automation_commands
                SET status = ?,
                    last_error = NULL,
                    updated_at = ?
                WHERE id = ?
                  AND status = ?
            """.trimIndent(),
            CommandStatus.SUCCEEDED.name,
            nowValue,
            id,
            CommandStatus.IN_PROGRESS.name,
        )

        if (rows == 0) {
            return null
        }

        return findById(id)
    }

    override fun requeueForRetry(
        id: Long,
        error: String?,
        scheduledAt: Instant,
        updatedAt: Instant,
    ): AutomationCommand? {
        val rows = jdbcTemplate.update(
            """
                UPDATE automation_commands
                SET status = ?,
                    scheduled_at = ?,
                    last_error = ?,
                    claimed_by = NULL,
                    claimed_at = NULL,
                    updated_at = ?
                WHERE id = ?
                  AND status = ?
            """.trimIndent(),
            CommandStatus.QUEUED.name,
            scheduledAt.toString(),
            error,
            updatedAt.toString(),
            id,
            CommandStatus.IN_PROGRESS.name,
        )

        if (rows == 0) {
            return null
        }

        return findById(id)
    }

    override fun markFailed(id: Long, error: String?, updatedAt: Instant): AutomationCommand? {
        val rows = jdbcTemplate.update(
            """
                UPDATE automation_commands
                SET status = ?,
                    last_error = ?,
                    updated_at = ?
                WHERE id = ?
                  AND status = ?
            """.trimIndent(),
            CommandStatus.FAILED.name,
            error,
            updatedAt.toString(),
            id,
            CommandStatus.IN_PROGRESS.name,
        )

        if (rows == 0) {
            return null
        }

        return findById(id)
    }

    private fun findClaimableCommandId(now: Instant): Long? {
        val sql = """
            SELECT id
            FROM automation_commands
            WHERE status = ?
              AND julianday(scheduled_at) <= julianday(?)
            ORDER BY priority ASC, julianday(scheduled_at) ASC, julianday(created_at) ASC
            LIMIT 1
        """.trimIndent()

        return jdbcTemplate.query(sql, { rs, _ -> rs.getLong("id") }, CommandStatus.QUEUED.name, now.toString()).firstOrNull()
    }

    private fun commandRowMapper() = { rs: java.sql.ResultSet, _: Int ->
        AutomationCommand(
            id = rs.getLong("id"),
            action = rs.getString("action"),
            params = objectMapper.readTree(rs.getString("params")),
            source = rs.getString("source"),
            status = CommandStatus.valueOf(rs.getString("status")),
            priority = rs.getInt("priority"),
            attempts = rs.getInt("attempts"),
            maxAttempts = rs.getInt("max_attempts"),
            scheduledAt = Instant.parse(rs.getString("scheduled_at")),
            claimedBy = rs.getString("claimed_by"),
            claimedAt = rs.getString("claimed_at")?.let(Instant::parse),
            lastError = rs.getString("last_error"),
            rollbackAction = rs.getString("rollback_action"),
            rollbackParams = rs.getString("rollback_params")?.let(objectMapper::readTree),
            createdAt = Instant.parse(rs.getString("created_at")),
            updatedAt = Instant.parse(rs.getString("updated_at")),
        )
    }
}
