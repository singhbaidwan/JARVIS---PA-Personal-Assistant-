import Foundation

struct ProcessResourceSnapshot {
    let process: String
    let pid: Int
    let cpuPercent: Double
    let memoryPercent: Double
    let command: String
}

final class ResourceMonitor {
    private let eventEmitter: EventEmitter
    private let intervalSeconds: TimeInterval
    private var timer: Timer?

    init(eventEmitter: EventEmitter, intervalSeconds: UInt64) {
        self.eventEmitter = eventEmitter
        self.intervalSeconds = TimeInterval(max(5, intervalSeconds))
    }

    func start() {
        emitSample()
        timer = Timer.scheduledTimer(withTimeInterval: intervalSeconds, repeats: true) { [weak self] _ in
            self?.emitSample()
        }

        print("jarvis-agent: monitoring resource samples every \(Int(intervalSeconds))s")
    }

    private func emitSample() {
        guard let sample = sampleTopCpuProcess() else {
            return
        }

        print(
            "jarvis-agent: RESOURCE_SAMPLE process=\(sample.process) cpu=\(sample.cpuPercent) memory=\(sample.memoryPercent)"
        )
        eventEmitter.emitResourceSample(
            ResourceSamplePayload(
                process: sample.process,
                pid: sample.pid,
                cpuPercent: sample.cpuPercent,
                memoryPercent: sample.memoryPercent,
                command: sample.command
            )
        )
    }

    private func sampleTopCpuProcess() -> ProcessResourceSnapshot? {
        let process = Process()
        let pipe = Pipe()
        process.executableURL = URL(fileURLWithPath: "/bin/ps")
        process.arguments = ["-axo", "pid=,pcpu=,pmem=,comm="]
        process.standardOutput = pipe
        process.standardError = Pipe()

        do {
            try process.run()
        } catch {
            print("jarvis-agent: failed to run ps error=\(error.localizedDescription)")
            return nil
        }

        process.waitUntilExit()
        guard process.terminationStatus == 0 else {
            print("jarvis-agent: ps exited status=\(process.terminationStatus)")
            return nil
        }

        let data = pipe.fileHandleForReading.readDataToEndOfFile()
        guard let output = String(data: data, encoding: .utf8) else {
            return nil
        }

        return output
            .split(separator: "\n")
            .compactMap(parseProcessLine)
            .max { left, right in left.cpuPercent < right.cpuPercent }
    }

    private func parseProcessLine(_ line: Substring) -> ProcessResourceSnapshot? {
        let parts = line
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .split(maxSplits: 3, omittingEmptySubsequences: true) { $0.isWhitespace }

        guard parts.count == 4,
              let pid = Int(parts[0]),
              let cpuPercent = Double(parts[1]),
              let memoryPercent = Double(parts[2])
        else {
            return nil
        }

        let command = String(parts[3]).trimmingCharacters(in: .whitespacesAndNewlines)
        guard !command.isEmpty else {
            return nil
        }

        return ProcessResourceSnapshot(
            process: displayName(for: command),
            pid: pid,
            cpuPercent: cpuPercent,
            memoryPercent: memoryPercent,
            command: command
        )
    }

    private func displayName(for command: String) -> String {
        let pathComponent = URL(fileURLWithPath: command).lastPathComponent
        return pathComponent.replacingOccurrences(of: ".app", with: "")
    }

    deinit {
        timer?.invalidate()
    }
}
