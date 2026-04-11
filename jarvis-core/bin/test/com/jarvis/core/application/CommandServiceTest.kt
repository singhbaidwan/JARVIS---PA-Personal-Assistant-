package com.jarvis.core.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jarvis.core.adapters.db.AutomationCommandRepository
import com.jarvis.core.api.CommandRequest
import com.jarvis.core.api.CommandResultRequest
import com.jarvis.core.api.CommandResultStatus
import com.jarvis.core.command.AutomationCommand
import com.jarvis.core.command.CommandStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class CommandServiceTest {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `enqueue maps app into params`() {
        val repository = FakeAutomationCommandRepository()
        val service = CommandService(repository, objectMapper)

        val command = service.enqueue(
            CommandRequest(
                action = "open_app",
                app = "Chrome",
            ),
        )

        assertEquals("OPEN_APP", command.action)
        assertEquals("Chrome", command.params.path("app").asText())
        assertEquals(CommandStatus.QUEUED, command.status)
    }

    @Test
    fun `failed command is requeued while attempts remain`() {
        val repository = FakeAutomationCommandRepository()
        val service = CommandService(repository, objectMapper)

        service.enqueue(
            CommandRequest(
                action = "OPEN_APP",
                app = "Chrome",
                maxAttempts = 3,
            ),
        )

        val claimed = service.claim("jarvis-agent")!!
        assertEquals(1, claimed.attempts)
        assertEquals(CommandStatus.IN_PROGRESS, claimed.status)

        val result = service.complete(
            commandId = claimed.id!!,
            request = CommandResultRequest(
                status = CommandResultStatus.FAILED,
                error = "App launch failed",
            ),
        )

        assertEquals(true, result.retryScheduled)
        assertEquals(CommandStatus.QUEUED, result.command.status)
        assertNull(result.rollbackCommand)
        assertEquals("App launch failed", result.command.lastError)
    }

    @Test
    fun `terminal failure enqueues rollback command`() {
        val repository = FakeAutomationCommandRepository()
        val service = CommandService(repository, objectMapper)

        val original = service.enqueue(
            CommandRequest(
                action = "OPEN_APP",
                app = "Chrome",
                maxAttempts = 1,
                rollbackAction = "CLOSE_APP",
                rollbackParams = mapOf("app" to "Chrome"),
            ),
        )
        service.claim("jarvis-agent")!!

        val result = service.complete(
            commandId = original.id!!,
            request = CommandResultRequest(
                status = CommandResultStatus.FAILED,
                error = "Permission denied",
            ),
        )

        assertEquals(false, result.retryScheduled)
        assertEquals(CommandStatus.FAILED, result.command.status)
        assertEquals("Permission denied", result.command.lastError)
        assertNotNull(result.rollbackCommand)
        assertEquals("CLOSE_APP", result.rollbackCommand!!.action)
        assertEquals(CommandStatus.QUEUED, result.rollbackCommand!!.status)
        assertEquals("Chrome", result.rollbackCommand!!.params.path("app").asText())
    }

    @Test
    fun `stale in-progress command is recovered and reclaimed`() {
        val repository = FakeAutomationCommandRepository()
        val service = CommandService(repository, objectMapper, commandClaimTimeoutSeconds = 5)

        val queued = service.enqueue(
            CommandRequest(
                action = "OPEN_APP",
                app = "Calculator",
                maxAttempts = 3,
            ),
        )
        val firstClaim = service.claim("worker-a")!!
        repository.override(
            id = firstClaim.id!!,
        ) { command ->
            command.copy(
                claimedAt = Instant.now().minusSeconds(120),
                updatedAt = Instant.now().minusSeconds(120),
            )
        }

        val reclaimed = service.claim("worker-b")

        assertNotNull(reclaimed)
        assertEquals(queued.id, reclaimed!!.id)
        assertEquals(CommandStatus.IN_PROGRESS, reclaimed.status)
        assertEquals(2, reclaimed.attempts)
        assertEquals("worker-b", reclaimed.claimedBy)
    }

    @Test
    fun `stale command past max attempts is failed`() {
        val repository = FakeAutomationCommandRepository()
        val service = CommandService(repository, objectMapper, commandClaimTimeoutSeconds = 5)

        val queued = service.enqueue(
            CommandRequest(
                action = "OPEN_APP",
                app = "Calculator",
                maxAttempts = 1,
            ),
        )
        val firstClaim = service.claim("worker-a")!!
        repository.override(
            id = firstClaim.id!!,
        ) { command ->
            command.copy(
                claimedAt = Instant.now().minusSeconds(120),
                updatedAt = Instant.now().minusSeconds(120),
            )
        }

        val nextClaim = service.claim("worker-b")
        val finalState = repository.findById(queued.id!!)

        assertNull(nextClaim)
        assertEquals(CommandStatus.FAILED, finalState!!.status)
        assertTrue(finalState.lastError!!.contains("lease expired", ignoreCase = true))
    }
}

private class FakeAutomationCommandRepository : AutomationCommandRepository {
    private var nextId = 1L
    private val store = linkedMapOf<Long, AutomationCommand>()

    override fun enqueue(command: AutomationCommand): AutomationCommand {
        val assigned = command.copy(id = nextId++)
        store[assigned.id!!] = assigned
        return assigned
    }

    override fun findRecent(limit: Int): List<AutomationCommand> {
        return store.values
            .sortedByDescending { it.createdAt }
            .take(limit)
    }

    override fun findById(id: Long): AutomationCommand? = store[id]

    override fun claimNext(agentId: String, now: Instant): AutomationCommand? {
        val next = store.values
            .filter { it.status == CommandStatus.QUEUED && !it.scheduledAt.isAfter(now) }
            .sortedWith(
                compareBy<AutomationCommand> { it.priority }
                    .thenBy { it.scheduledAt }
                    .thenBy { it.createdAt },
            )
            .firstOrNull() ?: return null

        val updated = next.copy(
            status = CommandStatus.IN_PROGRESS,
            claimedBy = agentId,
            claimedAt = now,
            attempts = next.attempts + 1,
            updatedAt = now,
        )
        store[updated.id!!] = updated
        return updated
    }

    override fun recoverStaleClaims(staleBefore: Instant, now: Instant): Int {
        var updatedCount = 0
        val staleError = "Command lease expired before completion was reported"

        val updates = store.values.map { command ->
            val claimedAt = command.claimedAt
            if (command.status != CommandStatus.IN_PROGRESS || claimedAt == null || claimedAt.isAfter(staleBefore)) {
                return@map command
            }

            updatedCount += 1
            if (command.attempts < command.maxAttempts) {
                command.copy(
                    status = CommandStatus.QUEUED,
                    scheduledAt = now,
                    claimedBy = null,
                    claimedAt = null,
                    lastError = staleError,
                    updatedAt = now,
                )
            } else {
                command.copy(
                    status = CommandStatus.FAILED,
                    claimedBy = null,
                    claimedAt = null,
                    lastError = staleError,
                    updatedAt = now,
                )
            }
        }

        store.clear()
        updates.forEach { store[it.id!!] = it }
        return updatedCount
    }

    override fun markSucceeded(id: Long, finishedAt: Instant): AutomationCommand? {
        val current = store[id] ?: return null
        if (current.status != CommandStatus.IN_PROGRESS) {
            return null
        }

        val updated = current.copy(
            status = CommandStatus.SUCCEEDED,
            lastError = null,
            updatedAt = finishedAt,
        )
        store[id] = updated
        return updated
    }

    override fun requeueForRetry(
        id: Long,
        error: String?,
        scheduledAt: Instant,
        updatedAt: Instant,
    ): AutomationCommand? {
        val current = store[id] ?: return null
        if (current.status != CommandStatus.IN_PROGRESS) {
            return null
        }

        val updated = current.copy(
            status = CommandStatus.QUEUED,
            scheduledAt = scheduledAt,
            claimedBy = null,
            claimedAt = null,
            lastError = error,
            updatedAt = updatedAt,
        )
        store[id] = updated
        return updated
    }

    override fun markFailed(id: Long, error: String?, updatedAt: Instant): AutomationCommand? {
        val current = store[id] ?: return null
        if (current.status != CommandStatus.IN_PROGRESS) {
            return null
        }

        val updated = current.copy(
            status = CommandStatus.FAILED,
            lastError = error,
            updatedAt = updatedAt,
        )
        store[id] = updated
        return updated
    }

    fun override(id: Long, update: (AutomationCommand) -> AutomationCommand) {
        val current = store[id] ?: return
        store[id] = update(current)
    }
}
