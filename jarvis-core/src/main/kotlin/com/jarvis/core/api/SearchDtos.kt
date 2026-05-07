package com.jarvis.core.api

import com.jarvis.core.application.FileSearchResponse
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import java.time.Instant

data class SearchRequest(
    @field:NotBlank
    val query: String,
    val roots: List<String> = emptyList(),
    @field:Min(1)
    @field:Max(50)
    val maxResults: Int = 10,
    val includeContent: Boolean = true,
    val referenceTime: Instant? = null,
)

data class SearchResultResponse(
    val path: String,
    val name: String,
    val extension: String,
    val sizeBytes: Long,
    val modifiedAt: Instant,
    val score: Double,
    val matchType: String,
    val snippet: String?,
    val reason: String,
)

data class SearchResponse(
    val query: String,
    val indexedCount: Int,
    val roots: List<String>,
    val results: List<SearchResultResponse> = emptyList(),
)

fun FileSearchResponse.toResponse(): SearchResponse = SearchResponse(
    query = query,
    indexedCount = indexedCount,
    roots = roots,
    results = results.map {
        SearchResultResponse(
            path = it.path,
            name = it.name,
            extension = it.extension,
            sizeBytes = it.sizeBytes,
            modifiedAt = it.modifiedAt,
            score = it.score,
            matchType = it.matchType,
            snippet = it.snippet,
            reason = it.reason,
        )
    },
)
