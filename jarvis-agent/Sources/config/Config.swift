import Foundation

struct Config {
    let coreBaseURL: URL
    let agentId: String
    let commandPollIntervalSeconds: UInt64
    let commandWorkerCount: Int
    let commandExecutionTimeoutSeconds: UInt64

    var coreEventURL: URL {
        coreBaseURL.appendingPathComponent("event")
    }

    static func load() -> Config {
        let env = ProcessInfo.processInfo.environment
        let baseURLString: String
        if let configuredBase = env["JARVIS_CORE_BASE_URL"] {
            baseURLString = configuredBase
        } else if let configuredEvent = env["JARVIS_CORE_EVENT_URL"], let eventURL = URL(string: configuredEvent) {
            baseURLString = deriveBaseURL(fromLegacyEventURL: eventURL).absoluteString
        } else {
            baseURLString = "http://localhost:8080"
        }

        guard let baseURL = URL(string: baseURLString) else {
            fatalError("Invalid core URL: \(baseURLString)")
        }

        let pollIntervalSeconds = UInt64(env["JARVIS_COMMAND_POLL_INTERVAL_SECONDS"] ?? "3") ?? 3
        let workerCount = Int(env["JARVIS_COMMAND_WORKER_COUNT"] ?? "2") ?? 2
        let commandExecutionTimeoutSeconds = UInt64(env["JARVIS_COMMAND_EXECUTION_TIMEOUT_SECONDS"] ?? "15") ?? 15
        let agentId = env["JARVIS_AGENT_ID"] ?? "jarvis-agent"

        return Config(
            coreBaseURL: baseURL,
            agentId: agentId,
            commandPollIntervalSeconds: max(1, pollIntervalSeconds),
            commandWorkerCount: max(1, workerCount),
            commandExecutionTimeoutSeconds: max(5, commandExecutionTimeoutSeconds)
        )
    }

    private static func deriveBaseURL(fromLegacyEventURL eventURL: URL) -> URL {
        guard eventURL.path.hasSuffix("/event") else {
            return eventURL
        }

        var components = URLComponents(url: eventURL, resolvingAgainstBaseURL: false)
        let trimmedPath = String(eventURL.path.dropLast("/event".count))
        components?.path = trimmedPath.isEmpty ? "/" : trimmedPath
        return components?.url ?? eventURL
    }
}
