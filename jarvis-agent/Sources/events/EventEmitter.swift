import Foundation

protocol EventEmitter {
    func emitAppSwitched(from: String, to: String)
}

final class HttpEventEmitter: EventEmitter {
    private let coreClient: CoreClient

    init(coreClient: CoreClient) {
        self.coreClient = coreClient
    }

    func emitAppSwitched(from: String, to: String) {
        let event = EventEnvelope(
            type: "APP_SWITCHED",
            payload: AppSwitchPayload(from: from, to: to),
            source: "jarvis-agent"
        )

        Task {
            await coreClient.post(event)
        }
    }
}
