package com.watvision.mainapp;


import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WatBlueToothService {

    private static final String TAG = "WatBlueToothService";
    private String MY_UUID_STRING = "4fafc201-1fb5-459e-8fcc-c5c9c331914b";
    private boolean connected = false;

    public enum bluetoothStates {
        CONNECTED,
        DISCONNECTED,
        FAILED_TO_CONNECT
    }

    BluetoothGatt bluetoothGatt;

    Boolean btScanning = false;

    BluetoothLeScanner btScanner;

    private Handler mHandler = new Handler();
    private static final long SCAN_PERIOD = 5000;

    Context appContext;

    BluetoothDevice wearableDevice;

    BluetoothGattCharacteristic buzzCharacteristic;

    BluetoothGattCharacteristic vibrateCharac;

    Handler mainLoopHandler;

    private final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";

    public WatBlueToothService(BluetoothLeScanner inScanner, Context inContext, Handler inHandler) {
        btScanner = inScanner;
        appContext = inContext;
        mainLoopHandler = inHandler;
        wearableDevice = null;
        buzzCharacteristic = null;
    }

    // Device scan callback.
    private ScanCallback leScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            String deviceName = result.getDevice().getName();

            if (deviceName != null) {
                if (deviceName.equals("MyESP32")) {
                    wearableDevice = result.getDevice();
                }
            }
        }
    };

    // Used to start a connection to the device
    public void InitiateConnection() {
        startSearchForESP32();
    }

    // Device connect call back
    private final BluetoothGattCallback ESP32btleGattCallback = new BluetoothGattCallback() {

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // this will get called anytime you perform a read or write characteristic operation
            Log.d(TAG,"Device read or wrote to");
        }

        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            // this will get called when a device connects or disconnects
            System.out.println(newState);
            switch (newState) {
                case 0:
                    Log.d(TAG,"Device disconnected");
                    break;
                case 2:
                    Log.d(TAG,"Device connected");

                    // discover services and characteristics for this device
                    bluetoothGatt.discoverServices();

                    break;
                default:
                    Log.d(TAG,"Encountered unknown state. UH OH!");
                    break;
            }
        }

        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            // this will get called after the client initiates a 			BluetoothGatt.discoverServices() call
            Log.d(TAG,"Device services have been discovered");

            // Store services
            storeServices();
        }

        @Override
        // Result of a characteristic read operation
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }
    };

    private void storeServices() {
        //check mBluetoothGatt is available
        if (bluetoothGatt == null) {
            Log.e(TAG, "lost connection");
            return;
        }
        List<BluetoothGattService> serviceList = bluetoothGatt.getServices();
        BluetoothGattService Service = null;

        for (int i = 0; i < serviceList.size(); i++) {
            if (serviceList.get(i).getUuid().toString().equals(MY_UUID_STRING)) {
                Service = serviceList.get(i);
            }
        }

        if (Service == null) {
            Log.e(TAG, "service not found!");
            return;
        }
        BluetoothGattCharacteristic charac = Service
                .getCharacteristics().get(0);
        if (charac == null) {
            Log.e(TAG, "char not found!");
            return;
        }

        buzzCharacteristic = charac;
        byte[] value = new byte[1];
        value[0] = (byte) 'A';
        buzzCharacteristic.setValue(value);

        vibrateCharac = charac;
    }


    private void startSearchForESP32() {
        System.out.println("start scanning");
        btScanning = true;
        wearableDevice = null;
        buzzCharacteristic = null;
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                btScanner.startScan(leScanCallback);
            }
        });

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finalizeSearchForESP32();
                connectToWearable();
            }
        }, SCAN_PERIOD);
    }

    private void finalizeSearchForESP32() {
        btScanning = false;
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                btScanner.stopScan(leScanCallback);
            }
        });
    }

    private void connectToWearable() {
        Message msg = mainLoopHandler.obtainMessage();

        if (wearableDevice != null) {
            bluetoothGatt = wearableDevice.connectGatt(appContext, false, ESP32btleGattCallback);
            msg.arg1 = bluetoothStates.CONNECTED.ordinal();
            connected = true;
        } else {
            Log.d(TAG,"Tried to connect to a null device");
            msg.arg1 = bluetoothStates.FAILED_TO_CONNECT.ordinal();
            connected = false;
        }

        msg.arg2 = 0;
        mainLoopHandler.sendMessage(msg);
    }

    public void Buzz() {
        if (buzzCharacteristic != null) {
            boolean status = bluetoothGatt.writeCharacteristic(buzzCharacteristic);

            if (status) {
                Log.d(TAG,"Buzz Successful!");
            } else {
                Log.d(TAG,"Buzz Failure!");
            }
        } else {
            Log.d(TAG,"Buzz characteristic is null");
        }
    }

    public void vibrate(int i) {
        byte[] val = {'A', (byte)i};
        vibrateCharac.setValue(val);
        if (buzzCharacteristic != null) {
            boolean status = bluetoothGatt.writeCharacteristic(buzzCharacteristic);

            if (status) {
                Log.d(TAG,"Vibrate Successful!");
            } else {
                Log.d(TAG,"Vibrate Failure!");
            }
        } else {
            Log.d(TAG,"Vibrate characteristic is null");
        }
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {

        Log.d(TAG," Characteristic UUID: " + characteristic.getUuid().toString());
    }

    private void disconnectWearable() {
        Log.d(TAG,"Disconnecting from bluetooth");
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
        }
        connected = false;
        Message msg = mainLoopHandler.obtainMessage();
        msg.arg1 = bluetoothStates.DISCONNECTED.ordinal();
        msg.arg2 = 0;
        mainLoopHandler.sendMessage(msg);
    }

    public void destroy() {
        disconnectWearable();
    }

    public void pause() {
        disconnectWearable();
    }

    public void resume() {
        // In an ideal world we would reconnect to bluetooth when we resume the app
        //connectToWearable();
    }

    public boolean isConnected() { return connected; }

}
