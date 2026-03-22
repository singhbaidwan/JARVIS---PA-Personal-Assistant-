import Foundation

final class CoreClient {
    private let baseURL: URL
    private let session: URLSession
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    init(baseURL: URL, session: URLSession = .shared) {
        self.baseURL = baseURL
        self.session = session
    }

    func post<Payload: Codable>(_ event: EventEnvelope<Payload>) async {
        var request = URLRequest(url: eventEndpoint())
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

    func claimCommand(agentId: String) async -> ClaimedCommand? {
        var request = URLRequest(url: commandClaimEndpoint())
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        do {
            request.httpBody = try encoder.encode(CommandClaimRequest(agentId: agentId))
            let (data, response) = try await session.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                print("jarvis-agent: non-http response for command claim")
                return nil
            }

            if httpResponse.statusCode == 204 {
                return nil
            }

            guard (200...299).contains(httpResponse.statusCode) else {
                print("jarvis-agent: claim rejected status=\(httpResponse.statusCode)")
                return nil
            }

            return try decoder.decode(ClaimedCommand.self, from: data)
        } catch {
            print("jarvis-agent: claim failed error=\(error.localizedDescription)")
            return nil
        }
    }

    func reportCommandResult(commandId: Int64, status: CommandResultStatus, error: String?) async {
        var request = URLRequest(url: commandResultEndpoint(commandId: commandId))
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        do {
            request.httpBody = try encoder.encode(CommandResultRequest(status: status, error: error))
            let (_, response) = try await session.data(for: request)

            guard let httpResponse = response as? HTTPURLResponse else {
                print("jarvis-agent: non-http response for command result id=\(commandId)")
                return
            }

            guard (200...299).contains(httpResponse.statusCode) else {
                print("jarvis-agent: result rejected id=\(commandId) status=\(httpResponse.statusCode)")
                return
            }

            print("jarvis-agent: command result accepted id=\(commandId)")
        } catch {
            print("jarvis-agent: failed to report command result id=\(commandId) error=\(error.localizedDescription)")
        }
    }

    private func eventEndpoint() -> URL {
        baseURL.appendingPathComponent("event")
    }

    private func commandClaimEndpoint() -> URL {
        baseURL.appendingPathComponent("command").appendingPathComponent("claim")
    }

    private func commandResultEndpoint(commandId: Int64) -> URL {
        baseURL
            .appendingPathComponent("command")
            .appendingPathComponent(String(commandId))
            .appendingPathComponent("result")
    }
}
