module.exports = {

    BAD_PARAMETERS: "BAD_PARAMETERS",
    OPERATION_TIMED_OUT: "OPERATION_TIMED_OUT",
    BLUETOOTH_OFF_OR_UNSUPPORTED: "BLUETOOTH_OFF_OR_UNSUPPORTED",
    DISCONNECTED_BEFORE_TERM: "DISCONNECTED_BEFORE_TERM",
    WRITE_NOT_EXPECTED: "WRITE_NOT_EXPECTED",
    READ_NOT_EXPECTED: "READ_NOT_EXPECTED",
    PERIPHERAL_NOT_FOUND: "PERIPHERAL_NOT_FOUND",
    SERVICE_NOT_FOUND: "SERVICE_NOT_FOUND",
    CHARACTERISTIC_NOT_FOUND: "CHARACTERISTIC_NOT_FOUND",

    write: function (successCallback, errorCallback, data) {
        cordova.exec(successCallback, errorCallback, "BLECentralSessionPlugin", "write", [data]);
    },

    read: function (successCallback, errorCallback, data) {
        cordova.exec(successCallback, errorCallback, "BLECentralSessionPlugin", "read", [data]);
    },

    request: function (successCallback, errorCallback, data) {
        cordova.exec(successCallback, errorCallback, "BLECentralSessionPlugin", "request", [data]);
    }
};