package com.jarvis.core.application

import com.jarvis.core.adapters.ai.AiAnomalyEvent
import com.jarvis.core.adapters.ai.AiAnomalyRequest
import com.jarvis.core.adapters.ai.AnomalyDetectionClient
import com.jarvis.core.adapters.db.EventRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant

data class GuardianAnomaly(
    val anomalyDetected: Boolean,
    val reason: String,
    val severity: String,
    val score: Double,
    val signals: List<String> = emptyList(),
)

data class GuardianAnomalyResult(
    val eventCount: Int,
    val anomaly: GuardianAnomaly,
)

@Service
class GuardianAnomalyService(
    private val eventRepository: EventRepository,
    private val anomalyDetectionClient: AnomalyDetectionClient,
    @Value("\${jarvis.guardian.default-event-limit:120}")
    private val defaultEventLimit: Int = 120,
) {

    private val logger = LoggerFactory.getLogger(GuardianAnomalyService::class.java)

    fun scan(eventLimit: Int? = null): GuardianAnomalyResult {
        val safeLimit = (eventLimit ?: defaultEventLimit).coerceIn(1, 500)
        val recentEvents = eventRepository.findRecent(safeLimit).sortedBy { it.createdAt }
        val anomalyResponse = anomalyDetectionClient.detect(
            AiAnomalyRequest(
                events = recentEvents.map { event ->
                    AiAnomalyEvent(
                        type = event.type,
                        timestamp = event.createdAt,
                        payload = event.payload,
                        source = event.source,
                    )
                },
                referenceTime = Instant.now(),
            ),
        )

        val anomaly = GuardianAnomaly(
            anomalyDetected = anomalyResponse.anomalyDetected,
            reason = anomalyResponse.reason,
            severity = anomalyResponse.severity,
            score = anomalyResponse.score,
            signals = anomalyResponse.signals,
        )

        logger.info(
            "GUARDIAN_ANOMALY detected={} severity={} score={} eventCount={} reason={}",
            anomaly.anomalyDetected,
            anomaly.severity,
            anomaly.score,
            recentEvents.size,
            anomaly.reason,
        )

        return GuardianAnomalyResult(
            eventCount = recentEvents.size,
            anomaly = anomaly,
        )
    }
}
