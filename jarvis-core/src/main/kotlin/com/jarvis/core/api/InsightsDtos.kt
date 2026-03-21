package com.jarvis.core.api

import com.jarvis.core.application.DailyInsights
import java.time.LocalDate

data class DailyInsightsResponse(
    val date: LocalDate,
    val timezone: String,
    val totalTracked: String,
    val totalTrackedSeconds: Long,
    val apps: Map<String, String>,
    val appDurationsSeconds: Map<String, Long>,
)

fun DailyInsights.toResponse(): DailyInsightsResponse = DailyInsightsResponse(
    date = date,
    timezone = timezone,
    totalTracked = totalTracked,
    totalTrackedSeconds = totalTrackedSeconds,
    apps = apps,
    appDurationsSeconds = appDurationsSeconds,
)
