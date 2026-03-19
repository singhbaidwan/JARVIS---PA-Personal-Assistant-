import Foundation

final class CoreClient {
    private let endpoint: URL
    private let session: URLSession
    private let encoder = JSONEncoder()

    init(endpoint: URL, session: URLSession = .shared) {
        self.endpoint = endpoint
        self.session = session
    }

    func post<Payload: Codable>(_ event: EventEnvelope<Payload>) async {
        var request = URLRequest(url: endpoint)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        do {
            request.httpBody = try encoder.encode(event)
            let (_, response) = try await session.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                print("jarvis-agent: non-http response")
                return
            }

            guard (200...299).contains(httpResponse.statusCode) else {
                print("jarvis-agent: event rejected status=\(httpResponse.statusCode)")
                return
            }

            print("jarvis-agent: event sent status=\(httpResponse.statusCode)")
        } catch {
            print("jarvis-agent: failed to send event error=\(error.localizedDescription)")
        }
    }
}
