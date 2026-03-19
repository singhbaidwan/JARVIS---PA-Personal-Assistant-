import AppKit
import Foundation

final class AppMonitor {
    private let eventEmitter: EventEmitter
    private var previousApp: String?
    private var observer: NSObjectProtocol?

    init(eventEmitter: EventEmitter) {
        self.eventEmitter = eventEmitter
    }

    func start() {
        previousApp = NSWorkspace.shared.frontmostApplication?.localizedName

        observer = NSWorkspace.shared.notificationCenter.addObserver(
            forName: NSWorkspace.didActivateApplicationNotification,
            object: nil,
            queue: .main
        ) { [weak self] notification in
            self?.handleActivation(notification)
        }

        print("jarvis-agent: monitoring app switches (initial=\(previousApp ?? "unknown"))")
    }

    private func handleActivation(_ notification: Notification) {
        guard
            let app = notification.userInfo?[NSWorkspace.applicationUserInfoKey] as? NSRunningApplication,
            let currentApp = app.localizedName
        else {
            return
        }

        guard let fromApp = previousApp else {
            previousApp = currentApp
            return
        }

        guard fromApp != currentApp else {
            return
        }

        previousApp = currentApp
        print("jarvis-agent: APP_SWITCHED \(fromApp) -> \(currentApp)")
        eventEmitter.emitAppSwitched(from: fromApp, to: currentApp)
    }

    deinit {
        if let observer {
            NSWorkspace.shared.notificationCenter.removeObserver(observer)
        }
    }
}
