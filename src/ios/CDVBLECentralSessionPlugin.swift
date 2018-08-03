import CoreBluetooth

@objc(CDVBLECentralSessionPlugin) class CDVBLECentralSessionPlugin : CDVPlugin {

    private var centralSession: CDVBLECentralSession!
    private var writeData: String!
    
    private func initSession(pluginCommand: CDVInvokedUrlCommand) {
        
        let data:NSDictionary = pluginCommand.arguments[0] as! NSDictionary
        
        centralSession = CDVBLECentralSession(pluginContext: self,
                                              pluginCommand: pluginCommand,
                                              peripheralName: data["name"] as? String ?? "",
                                              peripheralServiceUuid: data["serviceUuid"] as? String ?? "",
                                              peripheralCharacteristicUuid: data["characteristicUuid"] as? String ?? "")
        
        writeData = data["data"] as? String ?? ""
    }
    
    func write(_ command: CDVInvokedUrlCommand) {
        
        self.commandDelegate.run(inBackground: {
            
            self.initSession(pluginCommand: command)
            self.centralSession.write(self.writeData)
        })
    }
    
    func read(_ command: CDVInvokedUrlCommand) {
        
        self.commandDelegate.run(inBackground: {
            
            self.initSession(pluginCommand: command)
            self.centralSession.read()
        })
    }
    
    func request(_ command: CDVInvokedUrlCommand) {
        
        self.commandDelegate.run(inBackground: {
            
            self.initSession(pluginCommand: command)
            self.centralSession.request(self.writeData)
        })
    }
}
