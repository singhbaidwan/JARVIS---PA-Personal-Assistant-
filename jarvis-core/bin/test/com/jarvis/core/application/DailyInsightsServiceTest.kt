package com.jarvis.core.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jarvis.core.adapters.db.DailyAppUsageRepository
import com.jarvis.core.adapters.db.EventRepository
import com.jarvis.core.event.Event
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DailyInsightsServiceTest {

    private val objectMapper: ObjectMapper = jacksonObjectMapper()

    @Test
    fun `aggregates usage and stores summary for past day`() {
        val eventRepository = FakeEventRepository(
            events = listOf(
                switchEvent("2026-03-20T23:00:00Z", from = "Chrome", to = "VS Code"),
                switchEvent("2026-03-20T23:30:00Z", from = "VS Code", to = "Chrome"),
            ),
        )
        val summaryRepository = FakeDailyAppUsageRepository()
        val service = DailyInsightsService(eventRepository, summaryRepository)

        val insights = service.getDailyInsights(
            date = LocalDate.parse("2026-03-20"),
            zoneId = ZoneId.of("UTC"),
            now = Instant.parse("2026-03-21T10:00:00Z"),
        )

        assertEquals("1h", insights.totalTracked)
        assertEquals(3600, insights.totalTrackedSeconds)
        assertEquals(mapOf("Chrome" to 1800L, "VS Code" to 1800L), insights.appDurationsSeconds)
        assertEquals(mapOf("Chrome" to "30m", "VS Code" to "30m"), insights.apps)
        assertEquals(LocalDate.parse("2026-03-20"), summaryRepository.lastDay)
        assertEquals(mapOf("Chrome" to 1800L, "VS Code" to 1800L), summaryRepository.lastUsage)
    }

    @Test
    fun `clamps current day to now instead of midnight`() {
        val eventRepository = FakeEventRepository(
            events = listOf(
                switchEvent("2026-03-21T09:00:00Z", from = "Chrome", to = "VS Code"),
                switchEvent("2026-03-21T09:30:00Z", from = "VS Code", to = "Chrome"),
                switchEvent("2026-03-21T12:00:00Z", from = "Chrome", to = "Slack"),
            ),
        )
        val summaryRepository = FakeDailyAppUsageRepository()
        val service = DailyInsightsService(eventRepository, summaryRepository)

        val insights = service.getDailyInsights(
            date = LocalDate.parse("2026-03-21"),
            zoneId = ZoneId.of("UTC"),
            now = Instant.parse("2026-03-21T10:00:00Z"),
        )

        assertEquals(3600, insights.totalTrackedSeconds)
        assertEquals(mapOf("Chrome" to 1800L, "VS Code" to 1800L), insights.appDurationsSeconds)
    }

    @Test
    fun `returns empty insights for future date`() {
        val eventRepository = FakeEventRepository(events = emptyList())
        val summaryRepository = FakeDailyAppUsageRepository()
        val service = DailyInsightsService(eventRepository, summaryRepository)

        val insights = service.getDailyInsights(
            date = LocalDate.parse("2026-03-22"),
            zoneId = ZoneId.of("UTC"),
            now = Instant.parse("2026-03-21T10:00:00Z"),
        )

        assertEquals(0, insights.totalTrackedSeconds)
        assertTrue(insights.appDurationsSeconds.isEmpty())
        assertEquals(LocalDate.parse("2026-03-22"), summaryRepository.lastDay)
        assertTrue(summaryRepository.lastUsage.isEmpty())
    }

    private fun switchEvent(timestamp: String, from: String, to: String): Event {
        return Event(
            id = null,
            type = "APP_SWITCHED",
            payload = objectMapper.valueToTree(mapOf("from" to from, "to" to to)),
            source = "test",
            createdAt = Instant.parse(timestamp),
        )
    }
}

private class FakeEventRepository(
    private val events: List<Event>,
) : EventRepository {

    override fun save(event: Event): Event = event

    override fun findRecent(limit: Int): List<Event> {
        return events.sortedByDescending { it.createdAt }.take(limit)
    }

    override fun findByTypesBetween(
        startInclusive: Instant,
        endExclusive: Instant,
        types: Set<String>,
    ): List<Event> {
        return events
            .filter { it.type in types }
            .filter { !it.createdAt.isBefore(startInclusive) && it.createdAt.isBefore(endExclusive) }
            .sortedBy { it.createdAt }
    }

    override fun findLatestByTypesBefore(before: Instant, types: Set<String>): Event? {
        return events
            .filter { it.type in types }
            .filter { it.createdAt.isBefore(before) }
            .maxByOrNull { it.createdAt }
    }
}

private class FakeDailyAppUsageRepository : DailyAppUsageRepository {
    var lastDay: LocalDate? = null
    var lastUsage: Map<String, Long> = emptyMap()

    override fun replaceForDay(day: LocalDate, usageByAppSeconds: Map<String, Long>) {
        lastDay = day
        lastUsage = usageByAppSeconds
    }
}
