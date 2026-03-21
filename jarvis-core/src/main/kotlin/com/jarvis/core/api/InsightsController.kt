package com.jarvis.core.api

import com.jarvis.core.application.DailyInsightsService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.ZoneId

@RestController
class InsightsController(
    private val dailyInsightsService: DailyInsightsService,
) {

    @GetMapping("/insights/daily")
    fun getDailyInsights(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        date: LocalDate?,
    ): DailyInsightsResponse {
        val targetDate = date ?: LocalDate.now(ZoneId.systemDefault())
        return dailyInsightsService.getDailyInsights(targetDate).toResponse()
    }
}
