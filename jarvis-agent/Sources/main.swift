import Foundation

let config = Config.load()
let coreClient = CoreClient(baseURL: config.coreBaseURL)
let eventEmitter = HttpEventEmitter(coreClient: coreClient)
let appMonitor = AppMonitor(eventEmitter: eventEmitter)
let commandExecutor = CommandExecutor()
let commandPoller = CommandPoller(
    coreClient: coreClient,
    commandExecutor: commandExecutor,
    agentId: config.agentId,
    pollIntervalSeconds: config.commandPollIntervalSeconds
)

appMonitor.start()
commandPoller.start()
RunLoop.main.run()
