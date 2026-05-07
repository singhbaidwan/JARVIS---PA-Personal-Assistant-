package com.jarvis.core.adapters.ai

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

data class AiAnomalyEvent(
    val type: String,
    val timestamp: Instant?,
    val payload: JsonNode,
    val source: String?,
)

data class AiAnomalyRequest(
    val events: List<AiAnomalyEvent>,
    @field:JsonProperty("reference_time")
    val referenceTime: Instant? = null,
)

data class AiAnomalyResponse(
    @JsonProperty("anomaly_detected")
    val anomalyDetected: Boolean,
    val reason: String,
    val severity: String,
    val score: Double,
    val signals: List<String> = emptyList(),
)

interface AnomalyDetectionClient {
    fun detect(request: AiAnomalyRequest): AiAnomalyResponse
}

@Component
class JarvisAiAnomalyDetectionClient(
    @Value("\${jarvis.ai.base-url:http://127.0.0.1:8000}")
    jarvisAiBaseUrl: String,
    @Value("\${jarvis.ai.anomaly-path:/anomaly}")
    private val anomalyPath: String,
) : AnomalyDetectionClient {

    private val logger = LoggerFactory.getLogger(JarvisAiAnomalyDetectionClient::class.java)
    private val restTemplate = RestTemplate()
    private val anomalyUrl = jarvisAiBaseUrl.trimEnd('/') + "/" + anomalyPath.trimStart('/')

    override fun detect(request: AiAnomalyRequest): AiAnomalyResponse {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val entity = HttpEntity(request, headers)

        return try {
            restTemplate.postForObject(
                anomalyUrl,
                entity,
                AiAnomalyResponse::class.java,
            )
                ?: throw ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "jarvis-ai /anomaly returned an empty response body",
                )
        } catch (error: ResponseStatusException) {
            throw error
        } catch (error: org.springframework.web.client.HttpStatusCodeException) {
            logger.warn(
                "AI_ANOMALY_HTTP_ERROR status={} response={}",
                error.statusCode.value(),
                error.responseBodyAsString.take(300),
            )
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "jarvis-ai /anomaly returned ${error.statusCode.value()}",
                error,
            )
        } catch (error: RestClientException) {
            logger.error("AI_ANOMALY_REQUEST_FAILED error={}", error.message)
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Failed to call jarvis-ai /anomaly",
                error,
            )
        }
    }
}
