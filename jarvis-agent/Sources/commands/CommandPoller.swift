import Foundation

final class CommandPoller {
    private let coreClient: CoreClient
    private let commandExecutor: CommandExecutor
    private let agentId: String
    private let pollIntervalNanoseconds: UInt64
    private let workerCount: Int
    private let executionTimeoutNanoseconds: UInt64
    private var pollTasks: [Task<Void, Never>] = []

    init(
        coreClient: CoreClient,
        commandExecutor: CommandExecutor,
        agentId: String,
        pollIntervalSeconds: UInt64,
        workerCount: Int,
        executionTimeoutSeconds: UInt64
    ) {
        self.coreClient = coreClient
        self.commandExecutor = commandExecutor
        self.agentId = agentId
        self.pollIntervalNanoseconds = max(1, pollIntervalSeconds) * 1_000_000_000
        self.workerCount = max(1, workerCount)
        self.executionTimeoutNanoseconds = max(5, executionTimeoutSeconds) * 1_000_000_000
    }

    func start() {
        guard pollTasks.isEmpty else {
            return
        }

        print("jarvis-agent: command poller started (agentId=\(agentId), workers=\(workerCount))")
        for workerIndex in 1...workerCount {
            let workerTask = Task { [weak self] in
                guard let self else { return }
                while !Task.isCancelled {
                    await self.pollOnce(workerIndex: workerIndex)
                    try? await Task.sleep(nanoseconds: self.pollIntervalNanoseconds)
                }
            }
            pollTasks.append(workerTask)
        }
    }

    private func pollOnce(workerIndex: Int) async {
        guard let command = await coreClient.claimCommand(agentId: agentId) else {
            return
        }

        print("jarvis-agent: worker=\(workerIndex) claimed command id=\(command.id) action=\(command.action)")
        let outcome = await executeWithTimeout(command)
        let reported = await coreClient.reportCommandResult(
            commandId: command.id,
            status: outcome.status,
            error: outcome.error
        )
        if !reported {
            print("jarvis-agent: worker=\(workerIndex) result not reported for id=\(command.id)")
        }
    }

    private func executeWithTimeout(_ command: ClaimedCommand) async -> CommandExecutionOutcome {
        await withTaskGroup(of: CommandExecutionOutcome.self) { group in
            group.addTask { [commandExecutor] in
                await commandExecutor.execute(command)
            }
            group.addTask { [executionTimeoutNanoseconds] in
                try? await Task.sleep(nanoseconds: executionTimeoutNanoseconds)
                return CommandExecutionOutcome(
                    status: .failed,
                    error: "Command execution timed out after \(executionTimeoutNanoseconds / 1_000_000_000)s"
                )
            }

            let outcome = await group.next() ?? CommandExecutionOutcome(
                status: .failed,
                error: "Command execution failed unexpectedly"
            )
            group.cancelAll()
            return outcome
        }
    }

    deinit {
        pollTasks.forEach { $0.cancel() }
    }
}
