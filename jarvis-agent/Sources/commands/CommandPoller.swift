import Foundation

final class CommandPoller {
    private let coreClient: CoreClient
    private let commandExecutor: CommandExecutor
    private let agentId: String
    private let pollIntervalNanoseconds: UInt64
    private var pollTask: Task<Void, Never>?

    init(
        coreClient: CoreClient,
        commandExecutor: CommandExecutor,
        agentId: String,
        pollIntervalSeconds: UInt64
    ) {
        self.coreClient = coreClient
        self.commandExecutor = commandExecutor
        self.agentId = agentId
        self.pollIntervalNanoseconds = max(1, pollIntervalSeconds) * 1_000_000_000
    }

    func start() {
        guard pollTask == nil else {
            return
        }

        print("jarvis-agent: command poller started (agentId=\(agentId))")
        pollTask = Task { [weak self] in
            guard let self else { return }
            while !Task.isCancelled {
                await self.pollOnce()
                try? await Task.sleep(nanoseconds: self.pollIntervalNanoseconds)
            }
        }
    }

    private func pollOnce() async {
        guard let command = await coreClient.claimCommand(agentId: agentId) else {
            return
        }

        print("jarvis-agent: claimed command id=\(command.id) action=\(command.action)")
        let outcome = commandExecutor.execute(command)
        await coreClient.reportCommandResult(
            commandId: command.id,
            status: outcome.status,
            error: outcome.error
        )
    }

    deinit {
        pollTask?.cancel()
    }
}
