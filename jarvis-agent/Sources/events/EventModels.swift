import Foundation

struct AppSwitchPayload: Codable {
    let from: String
    let to: String
}

struct ResourceSamplePayload: Codable {
    let process: String
    let pid: Int
    let cpuPercent: Double
    let memoryPercent: Double
    let command: String

    enum CodingKeys: String, CodingKey {
        case process
        case pid
        case cpuPercent = "cpu_percent"
        case memoryPercent = "memory_percent"
        case command
    }
}

struct EventEnvelope<Payload: Codable>: Codable {
    let type: String
    let payload: Payload
    let source: String
}
