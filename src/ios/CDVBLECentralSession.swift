import Foundation
import CoreBluetooth

class CDVBLECentralSession : NSObject, CBCentralManagerDelegate, CBPeripheralDelegate {

    private var context: CDVBLECentralSessionPlugin!
    private var command: CDVInvokedUrlCommand!
    private var name: String!
    private var serviceUuid: CBUUID!
    private var characteristicUuid: CBUUID!
    private var peripheral: CBPeripheral!

    private var centralManager: CBCentralManager!

    private var sessionType: Int!
    private var writeValue: Data!
    private var over: Bool! = false

    private var sessionTask: DispatchWorkItem!

    private static var TYPE_WRITE: Int! = 1
    private static var TYPE_READ: Int! = 2
    private static var TYPE_REQUEST: Int! = 3

    private static var OPERATION_MAX_TIME: Double! = 10

    private static var OPERATION_TIMED_OUT: String! = "OPERATION_TIMED_OUT"
    private static var BLUETOOTH_OFF_OR_UNSUPPORTED: String! = "BLUETOOTH_OFF_OR_UNSUPPORTED"
    private static var DISCONNECTED_BEFORE_TERM: String! = "DISCONNECTED_BEFORE_TERM"
    private static var WRITE_NOT_EXPECTED: String! = "WRITE_NOT_EXPECTED"
    private static var READ_NOT_EXPECTED: String! = "READ_NOT_EXPECTED"
    private static var SERVICE_NOT_FOUND: String! = "SERVICE_NOT_FOUND"
    private static var CHARACTERISTIC_NOT_FOUND: String! = "CHARACTERISTIC_NOT_FOUND"

    convenience init(pluginContext: CDVBLECentralSessionPlugin,
                     pluginCommand: CDVInvokedUrlCommand,
                     peripheralName: String,
                     peripheralServiceUuid: String,
                     peripheralCharacteristicUuid: String)
    {
        self.init()

        context = pluginContext
        command = pluginCommand
        name = peripheralName
        serviceUuid = CBUUID(string: peripheralServiceUuid)
        characteristicUuid = CBUUID(string: peripheralCharacteristicUuid)

        sessionTask = DispatchWorkItem {

            self.releaseResources()
            self.sendError(CDVBLECentralSession.OPERATION_TIMED_OUT)
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + CDVBLECentralSession.OPERATION_MAX_TIME, execute: sessionTask)
    }

    func instantiateManager() {

        centralManager = CBCentralManager(delegate: self, queue: DispatchQueue(label: "com.cordova.cdvblecentralsession.centralQueue", attributes: .concurrent))
    }

    func write(_ data: String) {

        sessionType = CDVBLECentralSession.TYPE_WRITE
        writeValue = data.data(using: .utf8)

        instantiateManager()
    }

    func read() {

        sessionType = CDVBLECentralSession.TYPE_READ

        instantiateManager()
    }

    func request(_ data: String) {

        sessionType = CDVBLECentralSession.TYPE_REQUEST
        writeValue = data.data(using: .utf8)

        instantiateManager()
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {

        switch central.state {

        case .unknown, .resetting, .unsupported, .unauthorized, .poweredOff:

            print("Bluetooth status is off or unsupported")

            releaseResources()
            sendError(CDVBLECentralSession.BLUETOOTH_OFF_OR_UNSUPPORTED)

        case .poweredOn:

            print("Bluetooth status is powered on")

            centralManager.scanForPeripherals(withServices: [serviceUuid])
        }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String : Any], rssi RSSI: NSNumber) {

        if (peripheral.name != name) {
            return
        }

        self.peripheral = peripheral;
        self.peripheral.delegate = self

        centralManager.stopScan()
        centralManager.connect(peripheral)
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {

        self.peripheral.discoverServices([serviceUuid])
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {

        if (over == false) {
            sendError(CDVBLECentralSession.DISCONNECTED_BEFORE_TERM)
        }

        releaseResources()
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {

        for service in peripheral.services! {

            if (service.uuid == serviceUuid) {

                print("Service found");

                peripheral.discoverCharacteristics(nil, for: service)

                return;
            }
        }

        releaseResources()
        sendError(CDVBLECentralSession.SERVICE_NOT_FOUND)
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {

        for characteristic in service.characteristics! {

            if (characteristic.uuid == characteristicUuid) {

                print("Characteristic found");

                if (sessionType == CDVBLECentralSession.TYPE_WRITE || sessionType == CDVBLECentralSession.TYPE_REQUEST) {
                    peripheral.writeValue(writeValue, for: characteristic, type: CBCharacteristicWriteType.withResponse)
                }
                else {
                    peripheral.readValue(for: characteristic)
                }

                return
            }
        }

        releaseResources()
        sendError(CDVBLECentralSession.CHARACTERISTIC_NOT_FOUND)
    }

    //==============
    //WRITE CALLBACK
    //==============

    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {

        if (characteristic.uuid == characteristicUuid) {

            if (sessionType == CDVBLECentralSession.TYPE_REQUEST) {

                print("Write request success, reading...")

                peripheral.readValue(for: characteristic)
            }
            else if (sessionType == CDVBLECentralSession.TYPE_WRITE) {

                print("Write success")

                releaseResources()
                sendSuccess()
            }
            else {

                releaseResources()
                sendError(CDVBLECentralSession.WRITE_NOT_EXPECTED)
            }
        }
    }

    //=============
    //READ CALLBACK
    //=============

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {

        releaseResources()

        if (characteristic.uuid == characteristicUuid) {

            let data = characteristic.value
            let string = String(data: data!, encoding: String.Encoding.utf8) as String?

            if (sessionType == CDVBLECentralSession.TYPE_READ || sessionType == CDVBLECentralSession.TYPE_REQUEST) {

                print("Read success: " + string!)

                sendSuccess(string!)
            }
            else {
                sendError(CDVBLECentralSession.READ_NOT_EXPECTED)
            }
        }
    }

    func releaseResources() {

        over = true;

        if (sessionTask.isCancelled != true) {
            sessionTask.cancel()
        }

        if (centralManager != nil && peripheral != nil) {
            centralManager.cancelPeripheralConnection(peripheral)
        }
    }

    func sendError(_ message: String) {

        context.commandDelegate!.send(CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: message), callbackId: command.callbackId)
    }

    func sendSuccess(_ message: String) {

        context.commandDelegate!.send(CDVPluginResult(status: CDVCommandStatus_OK, messageAs: message), callbackId: command.callbackId)
    }

    func sendSuccess() {

        context.commandDelegate!.send(CDVPluginResult(status: CDVCommandStatus_OK), callbackId: command.callbackId)
    }
}
