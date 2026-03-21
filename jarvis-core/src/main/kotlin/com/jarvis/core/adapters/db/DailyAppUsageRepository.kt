package com.jarvis.core.adapters.db

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate

interface DailyAppUsageRepository {
    fun replaceForDay(day: LocalDate, usageByAppSeconds: Map<String, Long>)
}

@Repository
class SqliteDailyAppUsageRepository(
    private val jdbcTemplate: JdbcTemplate,
) : DailyAppUsageRepository {

    @Transactional
    override fun replaceForDay(day: LocalDate, usageByAppSeconds: Map<String, Long>) {
        val dayValue = day.toString()
        val now = Instant.now().toString()

        if (usageByAppSeconds.isEmpty()) {
            jdbcTemplate.update("DELETE FROM daily_app_usage WHERE day = ?", dayValue)
            return
        }

        val upsertSql = """
            INSERT INTO daily_app_usage(day, app_name, duration_seconds, updated_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(day, app_name)
            DO UPDATE SET
              duration_seconds = excluded.duration_seconds,
              updated_at = excluded.updated_at
        """.trimIndent()

        usageByAppSeconds.entries.forEach { (app, durationSeconds) ->
            jdbcTemplate.update(upsertSql, dayValue, app, durationSeconds, now)
        }

        val placeholders = usageByAppSeconds.keys.joinToString(",") { "?" }
        val deleteSql = """
            DELETE FROM daily_app_usage
            WHERE day = ?
              AND app_name NOT IN ($placeholders)
        """.trimIndent()
        val params = mutableListOf<Any>(dayValue)
        params.addAll(usageByAppSeconds.keys)
        jdbcTemplate.update(deleteSql, *params.toTypedArray())
    }
}
