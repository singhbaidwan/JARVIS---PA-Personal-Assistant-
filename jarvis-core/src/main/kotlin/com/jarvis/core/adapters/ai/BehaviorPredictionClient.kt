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

data class AiPredictEvent(
    val type: String,
    val timestamp: Instant?,
    val payload: JsonNode,
    val source: String?,
)

data class AiPredictRequest(
    val events: List<AiPredictEvent>,
    @field:JsonProperty("reference_time")
    val referenceTime: Instant? = null,
)

data class AiPredictResponse(
    @JsonProperty("predicted_action")
    val predictedAction: String,
    val confidence: Double,
    val reason: String,
    val context: Map<String, Any?> = emptyMap(),
)

interface BehaviorPredictionClient {
    fun predict(request: AiPredictRequest): AiPredictResponse
}

@Component
class JarvisAiPredictionClient(
    @Value("\${jarvis.ai.base-url:http://127.0.0.1:8000}")
    jarvisAiBaseUrl: String,
    @Value("\${jarvis.ai.predict-path:/predict}")
    private val predictPath: String,
) : BehaviorPredictionClient {

    private val logger = LoggerFactory.getLogger(JarvisAiPredictionClient::class.java)
    private val restTemplate = RestTemplate()
    private val predictUrl = jarvisAiBaseUrl.trimEnd('/') + "/" + predictPath.trimStart('/')

    override fun predict(request: AiPredictRequest): AiPredictResponse {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val entity = HttpEntity(request, headers)

        return try {
            restTemplate.postForObject(
                predictUrl,
                entity,
                AiPredictResponse::class.java,
            )
                ?: throw ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "jarvis-ai /predict returned an empty response body",
                )
        } catch (error: ResponseStatusException) {
            throw error
        } catch (error: org.springframework.web.client.HttpStatusCodeException) {
            logger.warn(
                "AI_PREDICT_HTTP_ERROR status={} response={}",
                error.statusCode.value(),
                error.responseBodyAsString.take(300),
            )
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "jarvis-ai /predict returned ${error.statusCode.value()}",
                error,
            )
        } catch (error: RestClientException) {
            logger.error("AI_PREDICT_REQUEST_FAILED error={}", error.message)
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Failed to call jarvis-ai /predict",
                error,
            )
        }
    }
}
