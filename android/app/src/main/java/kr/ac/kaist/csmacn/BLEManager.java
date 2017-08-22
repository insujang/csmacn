package kr.ac.kaist.csmacn;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.UUID;

/**
 * Created by Insu Jang on 2016-04-30.
 */
public class BLEManager {

    private static final UUID CSMA_CN_SERVICE_UUID =
            UUID.fromString("c95457be-e279-4e27-9bb9-4a34c4ecfeb4");

    private static final UUID DATA_UUID =
        UUID.fromString("fc3c1d76-2ff3-4592-94f3-a68d046fc8cb");


    private static final String TAG = "cs546.ble";
    private final BluetoothManager bluetoothManager;

    private final BluetoothAdapter bluetoothAdapter;

    // following three instances are for setting GATT server.
    private BluetoothGattServer bluetoothGattServer;
    private final BluetoothGattServerCallback bluetoothGattServerCallback;
    private BluetoothDevice bluetoothDevice = null;

    // following three instances are for starting advertisement.
    private final AdvertiseSettings advertiseSettings;
    private final AdvertiseCallback advertiseCallback;
    private AdvertiseData advertiseData;

    private final BluetoothLeAdvertiser advertiser;

    private Thread broadcastTimeupThread;
    private final MainActivity activity;
    private final TransmissionManager transmissionManager;
    private final Handler handler;
    private boolean isBLEConnected = false;

    public BLEManager(final MainActivity activity, final TransmissionManager transmissionManager) {
        this.activity = activity;
        this.transmissionManager = transmissionManager;
        handler = new Handler();
        bluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        advertiser = bluetoothAdapter.getBluetoothLeAdvertiser();

        // GATT server definition
        bluetoothGattServerCallback = new BluetoothGattServerCallback() {

            private static final int BLE_CONNECTION_ACK = 0;
            private static final int BLE_DATA_COLLISION = 5;

            @Override
            public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
                super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);

                // Log.v(TAG, "received data: " + value[0]);
                //byte[] buffer = new byte[1]; buffer[0] = '0';
                //bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, buffer);

                switch(value[0]){
                    // For first-time BLE connection establishment.
                    case BLE_CONNECTION_ACK:
                        // advertiser.stopAdvertising(advertiseCallback);
                        byte[] buffer = new byte[1]; buffer[0] = BLE_CONNECTION_ACK;
                        bluetoothGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, buffer);
                        if(broadcastTimeupThread.isAlive()) broadcastTimeupThread.interrupt();
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                activity.onSuccessToConnect();
                                isBLEConnected = true;
                            }
                        });
                        break;
                    case BLE_DATA_COLLISION:
                        Log.i(TAG, "Collision Notification received via BLE");
                        try {
                            transmissionManager.collisionNotified();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        break;
                }
            }

            @Override
            public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
                super.onConnectionStateChange(device, status, newState);
                if(status == BluetoothGatt.GATT_SUCCESS){
                    if(newState == BluetoothGatt.STATE_CONNECTED){
                        bluetoothDevice = device;
                        Log.v(TAG, "Connected to device: " + device.getAddress());
                    }
                    else if(newState == BluetoothGatt.STATE_DISCONNECTED){
                        advertiser.stopAdvertising(advertiseCallback);
                        bluetoothGattServer.clearServices();
                        bluetoothGattServer.close();
                        bluetoothGattServer = null;
                        bluetoothDevice = null;
                        isBLEConnected = false;
                        Log.v(TAG, "Disconnected from device");
                    }
                }
                else Log.w(TAG, "GattConnectionStateChange error status : " + status);

            }
        };

        // Advertisement definition
        advertiseSettings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW)
                .setConnectable(true)
                .build();
        advertiseCallback = new AdvertiseCallback() {
            @Override
            public void onStartSuccess(AdvertiseSettings settingsInEffect) {
                super.onStartSuccess(settingsInEffect);
                Log.i(TAG, "BLE broadcasting");
            }

            @Override
            public void onStartFailure(int errorCode) {
                super.onStartFailure(errorCode);
                String error;
                switch (errorCode) {
                    case ADVERTISE_FAILED_ALREADY_STARTED:
                        error = "App was already advertising";
                        break;
                    case ADVERTISE_FAILED_DATA_TOO_LARGE:
                        error = "Advertisement is larger than 31 bytes.";
                        break;
                    case ADVERTISE_FAILED_FEATURE_UNSUPPORTED:
                        error = "Advertising not supported.";
                        break;
                    case ADVERTISE_FAILED_INTERNAL_ERROR:
                        error = "Internal error occrued.";
                        break;
                    case ADVERTISE_FAILED_TOO_MANY_ADVERTISERS:
                        error = "No advertising instance is available";
                        break;
                    default:
                        error = "Unhandled error: " + errorCode;
                }
                Log.i(TAG, "fail to initiate BLE advertising." + error);
            }
        };
        advertiseData = new AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .setIncludeTxPowerLevel(false)
                .addServiceUuid(new ParcelUuid(CSMA_CN_SERVICE_UUID))
                .addServiceData(new ParcelUuid(CSMA_CN_SERVICE_UUID), "csmacn".getBytes())
                .build();
    }

    public boolean isBluetoothOn() {
        return (bluetoothAdapter != null && bluetoothAdapter.isEnabled());
    }

    public void startBleAdvertising (){
        bluetoothGattServer = bluetoothManager.openGattServer(activity, bluetoothGattServerCallback);
        // Service definition
        BluetoothGattService csmacnService = new BluetoothGattService(CSMA_CN_SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY);
        // Characteristics definition
        BluetoothGattCharacteristic dataCharacteristic = new BluetoothGattCharacteristic(DATA_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE,
                BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        csmacnService.addCharacteristic(dataCharacteristic);

        bluetoothGattServer.addService(csmacnService);

        advertiser.startAdvertising(advertiseSettings, advertiseData, advertiseCallback);
        broadcastTimeupThread = new Thread(){
            @Override
            public void run() {
                Log.i(TAG, "broadcast timeup thread start");
                try {
                    Thread.sleep(10000);
                    Log.w(TAG, "broadcasting timeup. stop advertising");
                    advertiser.stopAdvertising(advertiseCallback);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            activity.onFailToConnect();
                        }
                    });
                } catch (InterruptedException e) {
                    Log.i(TAG, "broadcast timeup thread interrupted as BLE connection established.");
                }
            }
        };
        broadcastTimeupThread.start();
    }

}
