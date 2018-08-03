package com.slkerndnme.cordova.blecentralsession;

import android.util.Log;

import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class BLECentralSessionPlugin extends CordovaPlugin {

    private final static String TAG = BLECentralSessionPlugin.class.getSimpleName();

    private BLECentralSession mBLECentralSession;

    private final static String BAD_PARAMETERS = "BAD_PARAMETERS";

    @Override
    public boolean execute(final String action, final JSONArray data, final CallbackContext callbackContext) {

        cordova.getThreadPool().execute(new Runnable() {

            @Override
            public void run() {

                String writeData = "";

                try {

                    JSONObject object = data.getJSONObject(0);

                    mBLECentralSession = new BLECentralSession(cordova.getActivity(),
                            callbackContext,
                            object.getString("address"),
                            object.getString("serviceUuid"),
                            object.getString("characteristicUuid"));

                    if (object.has("data"))
                        writeData = object.getString("data");

                } catch(Exception e) {

                    Log.d(TAG, "Error while parsing input data");
                    e.printStackTrace();

                    callbackContext.error(BAD_PARAMETERS);

                    return;
                }

                if (action.equals("write") && !writeData.equals(""))
                    write(writeData);
                else if (action.equals("read"))
                    read();
                else if (action.equals("request") && !writeData.equals(""))
                    request(writeData);
            }
        });

        return true;
    }

    private void write(String data) {

        mBLECentralSession.write(data);
    }

    private void read() {

        mBLECentralSession.read();
    }

    private void request(String data) {

        mBLECentralSession.request(data);
    }
}