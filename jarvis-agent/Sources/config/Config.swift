import Foundation

struct Config {
    let coreEventURL: URL

    static func load() -> Config {
        let env = ProcessInfo.processInfo.environment
        let endpoint = env["JARVIS_CORE_EVENT_URL"] ?? "http://localhost:8080/event"

        guard let url = URL(string: endpoint) else {
            fatalError("Invalid JARVIS_CORE_EVENT_URL: \(endpoint)")
        }

        return Config(coreEventURL: url)
    }
}
