import AppKit
import Foundation

struct CommandExecutionOutcome {
    let status: CommandResultStatus
    let error: String?
}

final class CommandExecutor {
    func execute(_ command: ClaimedCommand) -> CommandExecutionOutcome {
        switch command.action.uppercased() {
        case "OPEN_APP":
            return openApp(command)
        default:
            return CommandExecutionOutcome(
                status: .failed,
                error: "Unsupported action: \(command.action)"
            )
        }
    }

    private func openApp(_ command: ClaimedCommand) -> CommandExecutionOutcome {
        guard let appName = command.params["app"]?.stringValue, !appName.isEmpty else {
            return CommandExecutionOutcome(
                status: .failed,
                error: "OPEN_APP requires string param `app`"
            )
        }

        guard let appPath = NSWorkspace.shared.fullPath(forApplication: appName) else {
            return CommandExecutionOutcome(
                status: .failed,
                error: "Application not found: \(appName)"
            )
        }

        let semaphore = DispatchSemaphore(value: 0)
        var launchError: Error?

        NSWorkspace.shared.openApplication(
            at: URL(fileURLWithPath: appPath),
            configuration: NSWorkspace.OpenConfiguration()
        ) { _, error in
            launchError = error
            semaphore.signal()
        }

        let waitResult = semaphore.wait(timeout: .now() + .seconds(10))
        if waitResult == .timedOut {
            return CommandExecutionOutcome(
                status: .failed,
                error: "Timed out launching app: \(appName)"
            )
        }

        if let launchError {
            return CommandExecutionOutcome(
                status: .failed,
                error: "Launch failed: \(launchError.localizedDescription)"
            )
        }

        return CommandExecutionOutcome(status: .succeeded, error: nil)
    }
}
