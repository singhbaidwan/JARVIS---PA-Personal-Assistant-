package com.jarvis.core.api

import com.jarvis.core.application.SearchService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class SearchController(
    private val searchService: SearchService,
) {

    @PostMapping("/search")
    fun search(@Valid @RequestBody request: SearchRequest): SearchResponse {
        return searchService.search(
            query = request.query,
            roots = request.roots,
            maxResults = request.maxResults,
            includeContent = request.includeContent,
            referenceTime = request.referenceTime,
        ).toResponse()
    }
}
