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

data class AiAssistantEvent(
    val type: String,
    val timestamp: Instant?,
    val payload: JsonNode,
    val source: String?,
)

data class AiAssistantSearchResult(
    val path: String,
    val name: String,
    val extension: String,
    @field:JsonProperty("size_bytes")
    val sizeBytes: Long,
    @field:JsonProperty("modified_at")
    val modifiedAt: Instant,
    val score: Double,
    @field:JsonProperty("match_type")
    val matchType: String,
    val snippet: String? = null,
    val reason: String,
)

data class AiAssistantContext(
    @field:JsonProperty("recent_events")
    val recentEvents: List<AiAssistantEvent> = emptyList(),
    @field:JsonProperty("search_results")
    val searchResults: List<AiAssistantSearchResult> = emptyList(),
    val facts: Map<String, Any?> = emptyMap(),
)

data class AiAssistantChatRequest(
    val message: String,
    val provider: String? = null,
    val model: String? = null,
    val temperature: Double = 0.3,
    @field:JsonProperty("max_tokens")
    val maxTokens: Int = 500,
    val context: AiAssistantContext = AiAssistantContext(),
)

data class AiAssistantActionSuggestion(
    val action: String,
    val confidence: Double,
    val reason: String,
    val app: String? = null,
    val query: String? = null,
)

data class AiAssistantChatResponse(
    val response: String,
    val model: String,
    val provider: String,
    @field:JsonProperty("suggested_actions")
    val suggestedActions: List<AiAssistantActionSuggestion> = emptyList(),
    @field:JsonProperty("context_summary")
    val contextSummary: String,
    val metadata: Map<String, Any?> = emptyMap(),
)

interface AssistantChatClient {
    fun chat(request: AiAssistantChatRequest): AiAssistantChatResponse
}

@Component
class JarvisAiAssistantChatClient(
    @Value("\${jarvis.ai.base-url:http://127.0.0.1:8000}")
    jarvisAiBaseUrl: String,
    @Value("\${jarvis.ai.assistant-chat-path:/assistant/chat}")
    private val assistantChatPath: String,
) : AssistantChatClient {

    private val logger = LoggerFactory.getLogger(JarvisAiAssistantChatClient::class.java)
    private val restTemplate = RestTemplate()
    private val assistantChatUrl = jarvisAiBaseUrl.trimEnd('/') + "/" + assistantChatPath.trimStart('/')

    override fun chat(request: AiAssistantChatRequest): AiAssistantChatResponse {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val entity = HttpEntity(request, headers)

        return try {
            restTemplate.postForObject(
                assistantChatUrl,
                entity,
                AiAssistantChatResponse::class.java,
            )
                ?: throw ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "jarvis-ai /assistant/chat returned an empty response body",
                )
        } catch (error: ResponseStatusException) {
            throw error
        } catch (error: org.springframework.web.client.HttpStatusCodeException) {
            logger.warn(
                "AI_ASSISTANT_CHAT_HTTP_ERROR status={} response={}",
                error.statusCode.value(),
                error.responseBodyAsString.take(300),
            )
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "jarvis-ai /assistant/chat returned ${error.statusCode.value()}",
                error,
            )
        } catch (error: RestClientException) {
            logger.error("AI_ASSISTANT_CHAT_REQUEST_FAILED error={}", error.message)
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Failed to call jarvis-ai /assistant/chat",
                error,
            )
        }
    }
}
