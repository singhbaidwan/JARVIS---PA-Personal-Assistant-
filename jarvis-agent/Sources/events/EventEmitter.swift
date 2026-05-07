import Foundation

protocol EventEmitter {
    func emitAppSwitched(from: String, to: String)
    func emitResourceSample(_ sample: ResourceSamplePayload)
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

    func emitResourceSample(_ sample: ResourceSamplePayload) {
        let event = EventEnvelope(
            type: "RESOURCE_SAMPLE",
            payload: sample,
            source: "jarvis-agent"
        )

        Task {
            await coreClient.post(event)
        }
    }
}
