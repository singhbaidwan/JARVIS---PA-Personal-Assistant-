package com.jarvis.core.application

import com.jarvis.core.adapters.ai.AiSearchRequest
import com.jarvis.core.adapters.ai.AiSearchResponse
import com.jarvis.core.adapters.ai.AiSearchResult
import com.jarvis.core.adapters.ai.SearchClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.time.Instant

class SearchServiceTest {

    @Test
    fun `search uses provided roots and maps ai results`() {
        val searchClient = FakeSearchClient(
            nextResponse = AiSearchResponse(
                query = "Find python file I edited yesterday",
                indexedCount = 12,
                results = listOf(
                    AiSearchResult(
                        path = "/tmp/report.py",
                        name = "report.py",
                        extension = ".py",
                        sizeBytes = 128,
                        modifiedAt = Instant.parse("2026-04-11T10:00:00Z"),
                        score = 82.5,
                        matchType = "metadata",
                        snippet = "def report(): pass",
                        reason = "extension matched .py; modified time matched",
                    ),
                ),
            ),
        )
        val service = SearchService(
            searchClient = searchClient,
            defaultRoots = "/Users/test",
            baseDir = "/repo",
        )

        val result = service.search(
            query = " Find python file I edited yesterday ",
            roots = listOf("/workspace"),
            maxResults = 5,
            includeContent = true,
            referenceTime = Instant.parse("2026-04-12T10:00:00Z"),
        )

        assertEquals("Find python file I edited yesterday", searchClient.lastRequest!!.query)
        assertEquals(listOf("/workspace"), searchClient.lastRequest!!.roots)
        assertEquals(5, searchClient.lastRequest!!.maxResults)
        assertEquals(12, result.indexedCount)
        assertEquals("report.py", result.results.first().name)
        assertEquals("metadata", result.results.first().matchType)
    }

    @Test
    fun `search falls back to configured default roots`() {
        val searchClient = FakeSearchClient(
            nextResponse = AiSearchResponse(
                query = "notes",
                indexedCount = 0,
                results = emptyList(),
            ),
        )
        val service = SearchService(
            searchClient = searchClient,
            defaultRoots = "/one, /two",
            baseDir = "/repo",
        )

        service.search(
            query = "notes",
            roots = emptyList(),
            maxResults = 100,
            includeContent = false,
        )

        assertNotNull(searchClient.lastRequest)
        assertEquals(listOf("/one", "/two"), searchClient.lastRequest!!.roots)
        assertEquals(50, searchClient.lastRequest!!.maxResults)
        assertEquals(false, searchClient.lastRequest!!.includeContent)
    }

    @Test
    fun `search resolves relative roots against configured base directory`() {
        val searchClient = FakeSearchClient(
            nextResponse = AiSearchResponse(
                query = "python",
                indexedCount = 0,
                results = emptyList(),
            ),
        )
        val service = SearchService(
            searchClient = searchClient,
            defaultRoots = "../",
            baseDir = "/repo",
        )

        service.search(
            query = "python",
            roots = listOf("./jarvis-ai"),
            maxResults = 10,
            includeContent = true,
        )

        assertEquals(listOf("/repo/jarvis-ai"), searchClient.lastRequest!!.roots)
    }
}

private class FakeSearchClient(
    private val nextResponse: AiSearchResponse,
) : SearchClient {

    var lastRequest: AiSearchRequest? = null

    override fun search(request: AiSearchRequest): AiSearchResponse {
        lastRequest = request
        return nextResponse
    }
}
