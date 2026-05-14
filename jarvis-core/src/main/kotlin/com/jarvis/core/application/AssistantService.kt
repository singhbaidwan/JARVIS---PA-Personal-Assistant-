package com.jarvis.core.application

import com.jarvis.core.adapters.ai.AiAssistantChatRequest
import com.jarvis.core.adapters.ai.AiAssistantContext
import com.jarvis.core.adapters.ai.AiAssistantEvent
import com.jarvis.core.adapters.ai.AssistantChatClient
import com.jarvis.core.adapters.db.EventRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

data class AssistantApprovedAction(
    val action: String,
    val approved: Boolean,
    val reason: String,
    val confidence: Double,
    val app: String? = null,
    val query: String? = null,
)

data class AssistantChatResult(
    val response: String,
    val model: String,
    val provider: String,
    val contextSummary: String,
    val suggestedActions: List<AssistantApprovedAction> = emptyList(),
)

@Service
class AssistantService(
    private val eventRepository: EventRepository,
    private val assistantChatClient: AssistantChatClient,
    @Value("\${jarvis.assistant.default-event-limit:20}")
    private val defaultEventLimit: Int = 20,
) {

    private val logger = LoggerFactory.getLogger(AssistantService::class.java)

    fun chat(
        message: String,
        provider: String? = null,
        model: String? = null,
        eventLimit: Int? = null,
    ): AssistantChatResult {
        val safeEventLimit = (eventLimit ?: defaultEventLimit).coerceIn(0, 100)
        val recentEvents = if (safeEventLimit == 0) {
            emptyList()
        } else {
            eventRepository.findRecent(safeEventLimit).sortedBy { it.createdAt }
        }

        val aiResponse = assistantChatClient.chat(
            AiAssistantChatRequest(
                message = message.trim(),
                provider = provider,
                model = model,
                context = AiAssistantContext(
                    recentEvents = recentEvents.map {
                        AiAssistantEvent(
                            type = it.type,
                            timestamp = it.createdAt,
                            payload = it.payload,
                            source = it.source,
                        )
                    },
                    facts = mapOf(
                        "policy" to "LLM suggests only; core decides and does not execute from chat.",
                        "recent_event_count" to recentEvents.size,
                    ),
                ),
            ),
        )

        val approvedActions = aiResponse.suggestedActions.map {
            approveAction(
                action = it.action,
                app = it.app,
                query = it.query,
                confidence = it.confidence,
                reason = it.reason,
            )
        }

        logger.info(
            "ASSISTANT_CHAT provider={} model={} suggestions={} approved={}",
            aiResponse.provider,
            aiResponse.model,
            approvedActions.size,
            approvedActions.count { it.approved },
        )

        return AssistantChatResult(
            response = aiResponse.response,
            model = aiResponse.model,
            provider = aiResponse.provider,
            contextSummary = aiResponse.contextSummary,
            suggestedActions = approvedActions,
        )
    }

    private fun approveAction(
        action: String,
        app: String?,
        query: String?,
        confidence: Double,
        reason: String,
    ): AssistantApprovedAction {
        val normalizedAction = action.trim().uppercase()
        val cleanApp = app?.trim()?.takeIf { it.isNotEmpty() }
        val cleanQuery = query?.trim()?.takeIf { it.isNotEmpty() }

        val rejectionReason = when {
            normalizedAction !in SAFE_SUGGESTED_ACTIONS -> "Action is not allowed by core policy"
            normalizedAction in APP_ACTIONS && cleanApp == null -> "Action requires an app"
            normalizedAction == "SEARCH_FILES" && cleanQuery == null -> "SEARCH_FILES requires a query"
            confidence < MIN_CONFIDENCE -> "Suggestion confidence is below ${MIN_CONFIDENCE}"
            else -> null
        }

        return AssistantApprovedAction(
            action = normalizedAction,
            app = cleanApp,
            query = cleanQuery,
            confidence = confidence.coerceIn(0.0, 1.0),
            reason = rejectionReason ?: reason,
            approved = rejectionReason == null,
        )
    }

    companion object {
        private const val MIN_CONFIDENCE = 0.5
        private val APP_ACTIONS = setOf("OPEN_APP", "CLOSE_APP")
        private val SAFE_SUGGESTED_ACTIONS = APP_ACTIONS + "SEARCH_FILES"
    }
}
