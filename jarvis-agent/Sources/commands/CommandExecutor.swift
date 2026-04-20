import AppKit
import Foundation

struct CommandExecutionOutcome {
    let status: CommandResultStatus
    let error: String?
}

final class CommandExecutor {
    @MainActor
    func execute(_ command: ClaimedCommand) async -> CommandExecutionOutcome {
        switch command.action.uppercased() {
        case "OPEN_APP":
            return await openApp(command)
        case "CLOSE_APP":
            return closeApp(command)
        default:
            return CommandExecutionOutcome(
                status: .failed,
                error: "Unsupported action: \(command.action)"
            )
        }
    }

    @MainActor
    private func openApp(_ command: ClaimedCommand) async -> CommandExecutionOutcome {
        let appURL: URL
        if let bundleId = command.params["bundleId"]?.stringValue, !bundleId.isEmpty {
            guard let resolvedURL = NSWorkspace.shared.urlForApplication(withBundleIdentifier: bundleId) else {
                return CommandExecutionOutcome(
                    status: .failed,
                    error: "Application not found for bundleId: \(bundleId)"
                )
            }
            appURL = resolvedURL
        } else if let appName = command.params["app"]?.stringValue, !appName.isEmpty {
            guard let appPath = NSWorkspace.shared.fullPath(forApplication: appName) else {
                return CommandExecutionOutcome(
                    status: .failed,
                    error: "Application not found: \(appName)"
                )
            }
            appURL = URL(fileURLWithPath: appPath)
        } else {
            return CommandExecutionOutcome(
                status: .failed,
                error: "OPEN_APP requires `app` or `bundleId`"
            )
        }

        do {
            _ = try await NSWorkspace.shared.openApplication(
                at: appURL,
                configuration: NSWorkspace.OpenConfiguration()
            )
            return CommandExecutionOutcome(status: .succeeded, error: nil)
        } catch {
            return CommandExecutionOutcome(
                status: .failed,
                error: "Launch failed: \(error.localizedDescription)"
            )
        }
    }

    @MainActor
    private func closeApp(_ command: ClaimedCommand) -> CommandExecutionOutcome {
        let appName = command.params["app"]?.stringValue?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let bundleId = command.params["bundleId"]?.stringValue?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""

        if appName.isEmpty && bundleId.isEmpty {
            return CommandExecutionOutcome(
                status: .failed,
                error: "CLOSE_APP requires `app` or `bundleId`"
            )
        }

        let runningApps = NSWorkspace.shared.runningApplications.filter { app in
            if !bundleId.isEmpty, app.bundleIdentifier == bundleId {
                return true
            }
            if !appName.isEmpty, let localizedName = app.localizedName {
                return localizedName.caseInsensitiveCompare(appName) == .orderedSame
            }
            return false
        }

        // Closing an app that is not running is treated as success for idempotency.
        guard !runningApps.isEmpty else {
            return CommandExecutionOutcome(status: .succeeded, error: nil)
        }

        for runningApp in runningApps {
            if runningApp.terminate() {
                continue
            }

            if !runningApp.forceTerminate() {
                let name = runningApp.localizedName ?? runningApp.bundleIdentifier ?? "unknown"
                return CommandExecutionOutcome(
                    status: .failed,
                    error: "Failed to terminate app: \(name)"
                )
            }
        }

        return CommandExecutionOutcome(status: .succeeded, error: nil)
    }
}
