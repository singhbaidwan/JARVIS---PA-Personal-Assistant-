package com.jarvis.core.application

import com.jarvis.core.adapters.ai.AiSearchRequest
import com.jarvis.core.adapters.ai.SearchClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Path
import java.time.Instant

data class FileSearchResult(
    val path: String,
    val name: String,
    val extension: String,
    val sizeBytes: Long,
    val modifiedAt: Instant,
    val score: Double,
    val matchType: String,
    val snippet: String? = null,
    val reason: String,
)

data class FileSearchResponse(
    val query: String,
    val indexedCount: Int,
    val roots: List<String>,
    val results: List<FileSearchResult> = emptyList(),
)

@Service
class SearchService(
    private val searchClient: SearchClient,
    @Value("\${jarvis.search.default-roots:../}")
    private val defaultRoots: String = "../",
    @Value("\${jarvis.search.base-dir:../}")
    private val baseDir: String = "../",
) {

    private val logger = LoggerFactory.getLogger(SearchService::class.java)

    fun search(
        query: String,
        roots: List<String>,
        maxResults: Int,
        includeContent: Boolean,
        referenceTime: Instant? = null,
    ): FileSearchResponse {
        val normalizedQuery = query.trim()
        val resolvedRoots = roots.mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .ifEmpty { configuredDefaultRoots() }
            .map { resolveRoot(it) }

        val response = searchClient.search(
            AiSearchRequest(
                query = normalizedQuery,
                roots = resolvedRoots,
                maxResults = maxResults.coerceIn(1, 50),
                includeContent = includeContent,
                referenceTime = referenceTime,
            ),
        )

        logger.info(
            "SEARCH query={} roots={} indexed={} results={}",
            normalizedQuery,
            resolvedRoots,
            response.indexedCount,
            response.results.size,
        )

        return FileSearchResponse(
            query = response.query,
            indexedCount = response.indexedCount,
            roots = resolvedRoots,
            results = response.results.map {
                FileSearchResult(
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
    }

    private fun configuredDefaultRoots(): List<String> {
        return defaultRoots
            .split(",")
            .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
            .ifEmpty { listOf("../") }
    }

    private fun resolveRoot(root: String): String {
        val path = Path.of(root).expandHome()
        if (path.isAbsolute) {
            return path.normalize().toString()
        }

        return Path.of(baseDir).expandHome()
            .resolve(root)
            .toAbsolutePath()
            .normalize()
            .toString()
    }

    private fun Path.expandHome(): Path {
        val value = toString()
        if (value == "~") {
            return Path.of(System.getProperty("user.home"))
        }
        if (value.startsWith("~/")) {
            return Path.of(System.getProperty("user.home")).resolve(value.removePrefix("~/"))
        }
        return this
    }
}
