package com.jarvis.core.adapters.ai

import com.fasterxml.jackson.annotation.JsonProperty
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

data class AiSearchRequest(
    val query: String,
    val roots: List<String> = emptyList(),
    @field:JsonProperty("max_results")
    val maxResults: Int = 10,
    @field:JsonProperty("include_content")
    val includeContent: Boolean = true,
    @field:JsonProperty("reference_time")
    val referenceTime: Instant? = null,
)

data class AiSearchResult(
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

data class AiSearchResponse(
    val query: String,
    @field:JsonProperty("indexed_count")
    val indexedCount: Int,
    val results: List<AiSearchResult> = emptyList(),
)

interface SearchClient {
    fun search(request: AiSearchRequest): AiSearchResponse
}

@Component
class JarvisAiSearchClient(
    @Value("\${jarvis.ai.base-url:http://127.0.0.1:8000}")
    jarvisAiBaseUrl: String,
    @Value("\${jarvis.ai.search-path:/search}")
    private val searchPath: String,
) : SearchClient {

    private val logger = LoggerFactory.getLogger(JarvisAiSearchClient::class.java)
    private val restTemplate = RestTemplate()
    private val searchUrl = jarvisAiBaseUrl.trimEnd('/') + "/" + searchPath.trimStart('/')

    override fun search(request: AiSearchRequest): AiSearchResponse {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val entity = HttpEntity(request, headers)

        return try {
            restTemplate.postForObject(
                searchUrl,
                entity,
                AiSearchResponse::class.java,
            )
                ?: throw ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "jarvis-ai /search returned an empty response body",
                )
        } catch (error: ResponseStatusException) {
            throw error
        } catch (error: org.springframework.web.client.HttpStatusCodeException) {
            logger.warn(
                "AI_SEARCH_HTTP_ERROR status={} response={}",
                error.statusCode.value(),
                error.responseBodyAsString.take(300),
            )
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "jarvis-ai /search returned ${error.statusCode.value()}",
                error,
            )
        } catch (error: RestClientException) {
            logger.error("AI_SEARCH_REQUEST_FAILED error={}", error.message)
            throw ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Failed to call jarvis-ai /search",
                error,
            )
        }
    }
}
