package com.jarvis.core.api

import com.jarvis.core.application.AssistantChatResult
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank

data class AssistantChatRequest(
    @field:NotBlank
    val message: String,
    val provider: String? = null,
    val model: String? = null,
    @field:Min(0)
    @field:Max(100)
    val eventLimit: Int = 20,
)

data class AssistantActionResponse(
    val action: String,
    val approved: Boolean,
    val reason: String,
    val confidence: Double,
    val app: String?,
    val query: String?,
)

data class AssistantChatResponse(
    val response: String,
    val model: String,
    val provider: String,
    val contextSummary: String,
    val suggestedActions: List<AssistantActionResponse> = emptyList(),
)

fun AssistantChatResult.toResponse(): AssistantChatResponse = AssistantChatResponse(
    response = response,
    model = model,
    provider = provider,
    contextSummary = contextSummary,
    suggestedActions = suggestedActions.map {
        AssistantActionResponse(
            action = it.action,
            approved = it.approved,
            reason = it.reason,
            confidence = it.confidence,
            app = it.app,
            query = it.query,
        )
    },
)
