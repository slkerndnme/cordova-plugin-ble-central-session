package com.slkerndnme.cordova.blecentralsession;

import java.util.List;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.apache.cordova.CallbackContext;

public class BLECentralSession {

    private final static String TAG = BLECentralSession.class.getSimpleName();

    private Context mContext;
    private CallbackContext mCordovaCallback;
    private String mBluetoothDeviceAddress;
    private String mBluetoothDeviceServiceUuid;
    private String mBluetoothDeviceCharacteristicUuid;

    private Runnable mCancelSessionTimeoutRunnable;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mGatt;
    private BluetoothGattCharacteristic mCharacteristic;
    private Runnable mOnCharacteristicIsolatedCallbackRunnable;
    private Boolean mOver = false;

    private int mSessionType;

    private final static int TYPE_READ = 1;
    private final static int TYPE_WRITE = 2;
    private final static int TYPE_REQUEST = 3;

    private final static int OPERATION_MAX_TIME = 10000;

    private final static String OPERATION_TIMED_OUT = "OPERATION_TIMED_OUT";
    private final static String BLUETOOTH_OFF_OR_UNSUPPORTED = "BLUETOOTH_OFF_OR_UNSUPPORTED";
    private final static String DISCONNECTED_BEFORE_TERM = "DISCONNECTED_BEFORE_TERM";
    private final static String WRITE_NOT_EXPECTED = "WRITE_NOT_EXPECTED";
    private final static String READ_NOT_EXPECTED = "READ_NOT_EXPECTED";
    private final static String PERIPHERAL_NOT_FOUND = "PERIPHERAL_NOT_FOUND";
    private final static String SERVICE_NOT_FOUND = "SERVICE_NOT_FOUND";
    private final static String CHARACTERISTIC_NOT_FOUND = "CHARACTERISTIC_NOT_FOUND";

    BLECentralSession(Context context, CallbackContext callback, String address, String serviceUuid, String characteristicUuid) {

        mContext = context;
        mCordovaCallback = callback;
        mBluetoothDeviceAddress = address;
        mBluetoothDeviceServiceUuid = serviceUuid;
        mBluetoothDeviceCharacteristicUuid = characteristicUuid;

        mCancelSessionTimeoutRunnable = setTimeout(new Runnable() {
            @Override
            public void run() {
                mGatt.disconnect();
                mGatt.close();
                sendError(OPERATION_TIMED_OUT);
            }
        });

        if (!this.initialize())
            sendError(BLUETOOTH_OFF_OR_UNSUPPORTED);
    }

    private Runnable setTimeout(final Runnable callback) {

        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(callback, OPERATION_MAX_TIME);

        return new Runnable() {
            @Override
            public void run() {
                handler.removeCallbacks(callback);
            }
        };
    }

    private boolean initialize() {

        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

    private void setSessionType(int type) {

        mSessionType = type;
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {

            if (!mOver) {

                if (newState == BluetoothProfile.STATE_CONNECTED)
                    mGatt.discoverServices();
                else {
                    mCordovaCallback.error(DISCONNECTED_BEFORE_TERM);
                    releaseResources();
                }
            }

            if (newState == BluetoothProfile.STATE_DISCONNECTED)
                releaseResources();
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {

            isolateCharacteristic();
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            disconnect();

            if ((mSessionType == TYPE_READ || mSessionType == TYPE_REQUEST) && status == BluetoothGatt.GATT_SUCCESS)
                sendSuccess(characteristic.getStringValue(0));
            else
                sendError(READ_NOT_EXPECTED);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {

            if (mSessionType == TYPE_REQUEST)
                mGatt.readCharacteristic(mCharacteristic);
            else if (mSessionType == TYPE_WRITE) {
                disconnect();
                sendSuccess();
            }
            else {
                disconnect();
                sendError(WRITE_NOT_EXPECTED);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {}
    };

    private void isolateCharacteristic() {

        List<BluetoothGattService> services = mGatt.getServices();

        for (int i = 0; i < services.size(); i++) {

            if (services.get(i).getUuid().toString().equals(mBluetoothDeviceServiceUuid)) {

                BluetoothGattService mService = services.get(i);

                for (int j = 0; j < mService.getCharacteristics().size(); j++) {

                    if (mService.getCharacteristics().get(j).getUuid().toString().equals(mBluetoothDeviceCharacteristicUuid)) {

                        mCharacteristic = mService.getCharacteristics().get(j);

                        mOnCharacteristicIsolatedCallbackRunnable.run();

                        return;
                    }
                }

                mCordovaCallback.error(CHARACTERISTIC_NOT_FOUND);
                disconnect();
            }
        }

        mCordovaCallback.error(SERVICE_NOT_FOUND);
        disconnect();
    }

    public boolean connect() {

        if (mBluetoothAdapter == null) {
            disconnect();
            return false;
        }

        if (mBluetoothDeviceAddress != null && mGatt != null) {

            Log.d(TAG, "Trying to use an existing mGatt for connection.");

            if (mGatt.connect())
                return true;
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mBluetoothDeviceAddress);

        if (device == null) {

            mCordovaCallback.error(PERIPHERAL_NOT_FOUND);
            releaseResources();

            return false;
        }

        Log.d(TAG, "Trying to create a new connection.");

        mGatt = device.connectGatt(mContext, false, mGattCallback);

        return true;
    }

    public void write(final String data) {

        setSessionType(TYPE_WRITE);

        mOnCharacteristicIsolatedCallbackRunnable = new Runnable() {

            @Override
            public void run() {

                mCharacteristic.setValue(data);

                mGatt.writeCharacteristic(mCharacteristic);
            }
        };

        connect();
    }

    public void read() {

        setSessionType(TYPE_READ);

        mOnCharacteristicIsolatedCallbackRunnable = new Runnable() {

            @Override
            public void run() {

                mGatt.readCharacteristic(mCharacteristic);
            }
        };

        connect();
    }

    public void request(final String data) {

        setSessionType(TYPE_REQUEST);

        mOnCharacteristicIsolatedCallbackRunnable = new Runnable() {

            @Override
            public void run() {

                mCharacteristic.setValue(data);

                mGatt.writeCharacteristic(mCharacteristic);
            }
        };

        connect();
    }

    private void disconnect() {

        mOver = true;

        if (mGatt == null)
            return;

        mGatt.disconnect();
    }

    private void releaseResources() {

        mOver = true;

        mCancelSessionTimeoutRunnable.run();

        if (mGatt == null)
            return;

        mGatt.close();
        mGatt = null;
    }

    private void sendError(String errorCode) {

        mCordovaCallback.error(errorCode);
    }

    private void sendSuccess(String... message) {

        if (message.length > 0)
            mCordovaCallback.success(message[0]);
        else
            mCordovaCallback.success();
    }
}