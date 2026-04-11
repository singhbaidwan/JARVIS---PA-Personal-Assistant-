package com.jarvis.core.application

import com.jarvis.core.api.CommandResultStatus
import com.jarvis.core.command.AutomationCommand

interface CommandCompletionListener {
    fun onCommandCompletion(
        command: AutomationCommand,
        resultStatus: CommandResultStatus,
        retryScheduled: Boolean,
        rollbackQueued: Boolean,
    )
}
