package com.jarvis.core.api

import com.jarvis.core.application.GuardianAnomalyResult
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

data class GuardianAnomalyRequest(
    @field:Min(1)
    @field:Max(500)
    val eventLimit: Int = 120,
)

data class GuardianAnomalyResponse(
    val eventCount: Int,
    val anomalyDetected: Boolean,
    val reason: String,
    val severity: String,
    val score: Double,
    val signals: List<String> = emptyList(),
)

fun GuardianAnomalyResult.toResponse(): GuardianAnomalyResponse = GuardianAnomalyResponse(
    eventCount = eventCount,
    anomalyDetected = anomaly.anomalyDetected,
    reason = anomaly.reason,
    severity = anomaly.severity,
    score = anomaly.score,
    signals = anomaly.signals,
)
