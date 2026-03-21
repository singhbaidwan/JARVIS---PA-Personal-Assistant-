package com.jarvis.core.adapters.db

import com.fasterxml.jackson.databind.ObjectMapper
import com.jarvis.core.event.Event
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.PreparedStatementCreator
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.jdbc.support.KeyHolder
import org.springframework.stereotype.Repository
import java.sql.Statement
import java.time.Instant

interface EventRepository {
    fun save(event: Event): Event
    fun findRecent(limit: Int): List<Event>
    fun findByTypesBetween(
        startInclusive: Instant,
        endExclusive: Instant,
        types: Set<String>,
    ): List<Event>
    fun findLatestByTypesBefore(
        before: Instant,
        types: Set<String>,
    ): Event?
}

@Repository
class SqliteEventRepository(
    private val jdbcTemplate: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) : EventRepository {

    private val logger = LoggerFactory.getLogger(SqliteEventRepository::class.java)

    override fun save(event: Event): Event {
        val sql = """
            INSERT INTO events(type, payload, source, created_at)
            VALUES (?, ?, ?, ?)
        """.trimIndent()

        val keyHolder: KeyHolder = GeneratedKeyHolder()
        val payloadJson = objectMapper.writeValueAsString(event.payload)

        val statementCreator = PreparedStatementCreator { connection ->
            connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).apply {
                setString(1, event.type)
                setString(2, payloadJson)
                setString(3, event.source)
                setString(4, event.createdAt.toString())
            }
        }

        jdbcTemplate.update(statementCreator, keyHolder)
        val generatedId = keyHolder.keys?.get("id") as? Number ?: keyHolder.key

        val saved = event.copy(id = generatedId?.toLong())
        logger.debug("Saved event id={} type={}", saved.id, saved.type)
        return saved
    }

    override fun findRecent(limit: Int): List<Event> {
        val sql = """
            SELECT id, type, payload, source, created_at
            FROM events
            ORDER BY datetime(created_at) DESC
            LIMIT ?
        """.trimIndent()

        return jdbcTemplate.query(sql, eventRowMapper(), limit)
    }

    override fun findByTypesBetween(
        startInclusive: Instant,
        endExclusive: Instant,
        types: Set<String>,
    ): List<Event> {
        if (types.isEmpty()) {
            return emptyList()
        }

        val placeholders = types.joinToString(",") { "?" }
        val sql = """
            SELECT id, type, payload, source, created_at
            FROM events
            WHERE type IN ($placeholders)
              AND julianday(created_at) >= julianday(?)
              AND julianday(created_at) < julianday(?)
            ORDER BY julianday(created_at) ASC
        """.trimIndent()

        val params = types.toList() + listOf(startInclusive.toString(), endExclusive.toString())
        return jdbcTemplate.query(sql, eventRowMapper(), *params.toTypedArray())
    }

    override fun findLatestByTypesBefore(
        before: Instant,
        types: Set<String>,
    ): Event? {
        if (types.isEmpty()) {
            return null
        }

        val placeholders = types.joinToString(",") { "?" }
        val sql = """
            SELECT id, type, payload, source, created_at
            FROM events
            WHERE type IN ($placeholders)
              AND julianday(created_at) < julianday(?)
            ORDER BY julianday(created_at) DESC
            LIMIT 1
        """.trimIndent()

        val params = types.toList() + before.toString()
        return jdbcTemplate.query(sql, eventRowMapper(), *params.toTypedArray()).firstOrNull()
    }

    private fun eventRowMapper() = { rs: java.sql.ResultSet, _: Int ->
        Event(
            id = rs.getLong("id"),
            type = rs.getString("type"),
            payload = objectMapper.readTree(rs.getString("payload")),
            source = rs.getString("source"),
            createdAt = Instant.parse(rs.getString("created_at")),
        )
    }
}
