import Foundation

let config = Config.load()
let coreClient = CoreClient(endpoint: config.coreEventURL)
let eventEmitter = HttpEventEmitter(coreClient: coreClient)
let appMonitor = AppMonitor(eventEmitter: eventEmitter)

appMonitor.start()
RunLoop.main.run()
