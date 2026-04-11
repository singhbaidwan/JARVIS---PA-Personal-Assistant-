package com.jarvis.core.application

import com.jarvis.core.adapters.db.DailyAppUsageRepository
import com.jarvis.core.adapters.db.EventRepository
import com.jarvis.core.event.Event
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DailyInsights(
    val date: LocalDate,
    val timezone: String,
    val totalTracked: String,
    val totalTrackedSeconds: Long,
    val apps: Map<String, String>,
    val appDurationsSeconds: Map<String, Long>,
)

@Service
class DailyInsightsService(
    private val eventRepository: EventRepository,
    private val dailyAppUsageRepository: DailyAppUsageRepository,
) {

    private val trackedTypes = setOf("APP_SWITCHED", "APP_OPENED")

    fun getDailyInsights(
        date: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now(),
    ): DailyInsights {
        val dayStart = date.atStartOfDay(zoneId).toInstant()
        val dayEnd = resolveDayEnd(date = date, zoneId = zoneId, now = now)

        val usageByApp = if (dayEnd.isAfter(dayStart)) {
            aggregateDailyUsage(dayStart, dayEnd)
        } else {
            emptyMap()
        }
        dailyAppUsageRepository.replaceForDay(date, usageByApp)

        val totalSeconds = usageByApp.values.sum()
        val formattedApps = usageByApp.mapValues { (_, seconds) -> formatDuration(seconds) }

        return DailyInsights(
            date = date,
            timezone = zoneId.id,
            totalTracked = formatDuration(totalSeconds),
            totalTrackedSeconds = totalSeconds,
            apps = formattedApps,
            appDurationsSeconds = usageByApp,
        )
    }

    private fun resolveDayEnd(date: LocalDate, zoneId: ZoneId, now: Instant): Instant {
        val nextDayStart = date.plusDays(1).atStartOfDay(zoneId).toInstant()
        val today = LocalDate.ofInstant(now, zoneId)

        return when {
            date.isAfter(today) -> date.atStartOfDay(zoneId).toInstant()
            date.isEqual(today) && now.isBefore(nextDayStart) -> now
            else -> nextDayStart
        }
    }

    private fun aggregateDailyUsage(
        dayStart: Instant,
        dayEnd: Instant,
    ): Map<String, Long> {
        val usageByApp = mutableMapOf<String, Long>()
        val initialApp = eventRepository.findLatestByTypesBefore(dayStart, trackedTypes)?.toActiveApp()
        val events = eventRepository.findByTypesBetween(dayStart, dayEnd, trackedTypes)

        var currentApp = initialApp
        var cursor = dayStart

        events.forEach { event ->
            val switchTime = clamp(event.createdAt, dayStart, dayEnd)
            if (currentApp != null && switchTime.isAfter(cursor)) {
                usageByApp.merge(currentApp!!, Duration.between(cursor, switchTime).seconds, Long::plus)
            }

            currentApp = event.toActiveApp()
            cursor = switchTime
        }

        if (currentApp != null && dayEnd.isAfter(cursor)) {
            usageByApp.merge(currentApp!!, Duration.between(cursor, dayEnd).seconds, Long::plus)
        }

        return usageByApp
            .filterValues { it > 0 }
            .toList()
            .sortedWith(compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first.lowercase() })
            .toMap(LinkedHashMap())
    }

    private fun Event.toActiveApp(): String? {
        if (type.equals("APP_SWITCHED", ignoreCase = true)) {
            return payload.path("to").takeIf { !it.isMissingNode && !it.isNull }?.asText()?.trim()?.takeIf { it.isNotEmpty() }
        }

        if (type.equals("APP_OPENED", ignoreCase = true)) {
            return payload.path("app").takeIf { !it.isMissingNode && !it.isNull }?.asText()?.trim()?.takeIf { it.isNotEmpty() }
        }

        return null
    }

    private fun clamp(value: Instant, min: Instant, max: Instant): Instant {
        if (value.isBefore(min)) {
            return min
        }
        if (value.isAfter(max)) {
            return max
        }
        return value
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) {
            return "0m"
        }

        val totalMinutes = seconds / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        if (hours > 0 && minutes > 0) {
            return "${hours}h ${minutes}m"
        }
        if (hours > 0) {
            return "${hours}h"
        }
        if (totalMinutes > 0) {
            return "${totalMinutes}m"
        }
        return "${seconds}s"
    }
}
