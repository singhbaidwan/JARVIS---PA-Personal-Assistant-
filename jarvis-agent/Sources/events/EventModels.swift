import Foundation

struct AppSwitchPayload: Codable {
    let from: String
    let to: String
}

struct EventEnvelope<Payload: Codable>: Codable {
    let type: String
    let payload: Payload
    let source: String
}
