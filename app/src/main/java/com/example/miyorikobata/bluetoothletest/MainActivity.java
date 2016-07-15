package com.example.miyorikobata.bluetoothletest;

import android.Manifest;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class MainActivity extends AppCompatActivity {

    private final static UUID SERVIE_UUID = UUID.fromString("5047b78a-33d8-49c4-abb5-6877abd6514d");
    private final static UUID CHARACTERISTIC_UUID = UUID.fromString("e3c9460b-33cf-4c1b-b097-5ba46633f585");
    private final static int REQUEST_CODE_BLUETOOTH = 100;
    private final static int REQUEST_CODE_COARSE_LOCATION = 200;

    private TextView mTextView;
    private EditText mEditText;
    private Button mAdvertiseButton;
    private Button mScanButton;

    private BluetoothAdapter mAdapter = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextView = (TextView)findViewById(R.id.textView1);
        mTextView.setText("");

        mEditText = (EditText)findViewById(R.id.editText);
        mEditText.setText("");

        mAdvertiseButton = (Button)findViewById(R.id.advertiseButton);
        mAdvertiseButton.setOnClickListener(new AdvertiseButtonListener());
        mAdvertiseButton.setEnabled(false);

        mScanButton = (Button)findViewById(R.id.scanButton);
        mScanButton.setOnClickListener(new ScanButtonListener());
        mScanButton.setEnabled(false);

        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(intent, REQUEST_CODE_BLUETOOTH);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("BLE", "activity result: " + requestCode);

        if(requestCode == REQUEST_CODE_BLUETOOTH) {
            SetupBluetoothLe();
        }
    }

    private void SetupBluetoothLe() {
        BluetoothManager manager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);

        mAdapter = manager.getAdapter();
        if(mAdapter != null) {
            Log.d("BLE", "getting bluetooth adapter succeeded");

            mScanButton.setEnabled(true);

            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if(mAdapter.isMultipleAdvertisementSupported()) {
                    mAdvertiser = mAdapter.getBluetoothLeAdvertiser();
                    if(mAdvertiser != null) {
                        Log.d("BLE", "getting bluetooth le advertiser succeeded");

                        setupGattServer(manager);

                        mAdvertiseButton.setEnabled(true);
                    }
                    else {
                        Log.e("BLE", "getting bluetooth le advertiser failed");
                    }
                }
                else {
                    Log.e("BLE", "advertising is not supported on this device");
                }

            } else {
                Log.e("BLE", "not supported on API level: " + Build.VERSION.SDK_INT);
            }

        } else {
            Log.e("BLE", "getting bluetooth adapter failed");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.d("BLE", "permission result: " + requestCode);

        if(requestCode == REQUEST_CODE_COARSE_LOCATION) {
            tryStartScan(false);
        }
    }

    private String hexToString(byte[] bytes) {
        StringBuffer buffer = new StringBuffer();
        for(byte b: bytes) {
            buffer.append(String.format("%02x", b & 0xff));
        }

        return buffer.toString();
    }


    // -- implementations for central --

    private HashSet<BluetoothDevice> mConnectedDevices = new HashSet<>();
    private boolean mIsScanning = false;

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d("BLE", "scan result: " + callbackType + ", " + result.toString());

            BluetoothDevice device = result.getDevice();
            if(device != null) {
                if(!mConnectedDevices.contains(device)) {
                    BluetoothGatt gatt = device.connectGatt(MainActivity.this, false, mGattCallback);
                    if(gatt != null) {
                        Log.d("BLE", "start connecting gatt succeeded");

                        mConnectedDevices.add(device);
                    } else {
                        Log.e("BLE", "start connecting gatt failed");
                    }
                }
            } else {
                Log.e("BLE", "bluetooth device is null");
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.d("BLE", "batch scan result: " + results.size());

            for(ScanResult result: results) {
                BluetoothDevice device = result.getDevice();
                if(device != null) {
                    if(device.connectGatt(MainActivity.this, false, mGattCallback) == null) {
                        Log.e("BLE", "connect gatt failed");
                    }
                } else {
                    Log.e("BLE", "bluetooth device is null");
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("BLE", "scan failed: " + errorCode);

            mScanButton.setText("Start scan");
            mTextView.setText("Scan failed");

            cleanupCentral();
        }
    };

    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.d("BLE", "connection state change: " + gatt.getDevice().getAddress() + ", " + status + ", " +newState);

            if(newState == BluetoothGatt.STATE_CONNECTED) { // status check is incomplete..
                if(status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("BLE", "connecting device succeededd");

                    if(gatt.discoverServices()) {
                        Log.d("BLE", "start discovering services succeeded");
                    } else {
                        Log.e("BLE", "start discovering services failed");
                    }
                } else {
                    Log.e("BLE", "connecting device failed");
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.d("BLE", "services discovered: " + status);

            if(status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVIE_UUID);
                if(service != null) {
                    BluetoothGattCharacteristic characteristic = service.getCharacteristic(CHARACTERISTIC_UUID);
                    if(characteristic != null) {
                        if(gatt.readCharacteristic(characteristic)) {
                            Log.d("BLE", "start reading characteristic succeeded");
                        } else {
                            Log.e("BLE", "start reading characteristic failed");
                        }
                    } else {
                        Log.e("BLE", "characteristic is null");
                    }
                } else {
                    Log.e("BLE", "service is null");
                }
            } else {
                Log.e("BLE", "discover services failed");
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d("BLE", "characteristic read: " + status + ", " + characteristic.toString());

            if(status == BluetoothGatt.GATT_SUCCESS) {
                final String value = characteristic.getStringValue(0);
                Log.d("BLE", "reading characteristic succeeded: " + value);

                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mTextView.setText(value);
                    }
                });
            } else {
                Log.e("BLE", "reading characteristic failed");
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.d("BLE", "characteristic write: " + status + ", " + characteristic.toString());
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.d("BLE", "characteristic changed: " + characteristic.toString());
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d("BLE", "descriptor read: " + status + ", " + descriptor.toString());
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.d("BLE", "descriptor write: " + status + ", " + descriptor.toString());
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            Log.d("BLE", "reliable write completed: " + status);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            Log.d("BLE", "read remote RSSI: " + status + ", " + rssi);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            Log.d("BLE", "MTU changed: " + status + ", " + mtu);
        }
    };

    private class ScanButtonListener implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            if(!mIsScanning) {
                mIsScanning = true;
                mScanButton.setText("Stop scan");
                mAdvertiseButton.setEnabled(false);
                mTextView.setText("Scanning...");

                tryStartScan(true);
            }
            else
            {
                Log.d("BLE", "stop scanning..");

                BluetoothLeScanner scanner = mAdapter.getBluetoothLeScanner();
                scanner.stopScan(mScanCallback);

                mScanButton.setText("Start scan");
                mTextView.setText("");

                cleanupCentral();
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void tryStartScan(boolean isRequestPermission) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // having no permission!

                if(isRequestPermission) {
                    Log.d("BLE", "requesting permission..");
                    requestPermissions(new String[] {Manifest.permission.ACCESS_COARSE_LOCATION},
                            REQUEST_CODE_COARSE_LOCATION);
                }
                else {
                    Log.e("BLE", "request permission denited");

                    mScanButton.setText("Start scan");
                    mTextView.setText("Bluetooth is disabled");

                    cleanupCentral();
                }

                return;
            }
        }

        Log.d("BLE", "start scanning..");

        List<ScanFilter> filters = Arrays.asList(new ScanFilter.Builder().setServiceUuid(new ParcelUuid(SERVIE_UUID)).build());
        ScanSettings settings = new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build();

        BluetoothLeScanner scanner = mAdapter.getBluetoothLeScanner();
        scanner.startScan(filters, settings, mScanCallback);
    }

    private void cleanupCentral() {
        mIsScanning = false;
        mConnectedDevices.clear();

        mAdvertiseButton.setEnabled(mAdvertiser != null);
    }


    // -- implementations for peripheral --

    private BluetoothGattCharacteristic mCharacteristic = null;
    private BluetoothLeAdvertiser mAdvertiser = null;
    private BluetoothGattServer mGattServer = null;
    private boolean mIsAdvertising = false;

    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.d("BLE", "advertise start success");
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.e("BLE", "advertise start failure: " + errorCode);

            mAdvertiseButton.setText("Start advertise");
            mTextView.setText("Advertise failed");

            cleanupPeripheral();
        }
    };

    BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            Log.d("BLE", "service added");
        }

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            Log.d("BLE", "connecion state change: " + device.getAddress() + ", " + status + ", " + newState);
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset,
                                                BluetoothGattCharacteristic characteristic) {
            Log.d("BLE", "characteristic read request: " + device.getAddress() + ", " + requestId + ", " + offset);

            if(characteristic.setValue(mEditText.getText().toString())) {
                Log.d("BLE", "setting characteristic's value succeeded");
            } else {
                Log.e("BLE", "setting characteristic's value failed");
            }

            if(mGattServer.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, characteristic.getValue()))
            {
                Log.d("BLE", "send response succeeded: " + hexToString(characteristic.getValue()));
            } else {
                Log.e("BLE", "send response faield");
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device,
                                                 int requestId,
                                                 BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite,
                                                 boolean responseNeeded,
                                                 int offset,
                                                 byte[] value) {
            Log.d("BLE", "characteristic write request: " + requestId + ", " + characteristic.toString() + ", " + preparedWrite + ", " + responseNeeded + ", " + offset + ", " + value.toString());
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device,
                                            int requestId,
                                            int offset,
                                            BluetoothGattDescriptor descriptor) {
            Log.d("BLE", "descriptor read request: " + requestId + ", " + offset + ", " + descriptor.toString());
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device,
                                             int requestId,
                                             BluetoothGattDescriptor descriptor,
                                             boolean preparedWrite,
                                             boolean responseNeeded,
                                             int offset,
                                             byte[] value) {
            Log.d("BLE", "descriptor write request: " + requestId + ", " + descriptor.toString() + ", " + preparedWrite + ", " + responseNeeded + ", " + offset + ", " + value.toString());
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            Log.d("BLE", "execute write: " + requestId + ", " + execute);
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            Log.d("BLE", "notification send: " + status);
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            Log.d("BLE", "MTU changed: " + mtu);
        }
    };

    private class AdvertiseButtonListener implements View.OnClickListener {
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        public void onClick(View view) {
            if(!mIsAdvertising) {
                Log.d("BLE", "start advertising..");

                mIsAdvertising = true;
                mScanButton.setEnabled(false);
                mAdvertiseButton.setText("Stop advertise");
                mTextView.setText("Advertising...");

                AdvertiseSettings.Builder settingsBuilder = new AdvertiseSettings.Builder();
                settingsBuilder.setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED);
                settingsBuilder.setConnectable(true);
                settingsBuilder.setTimeout(0);
                settingsBuilder.setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM);

                AdvertiseSettings settings = settingsBuilder.build();

                AdvertiseData advertiseData = new AdvertiseData.Builder().addServiceUuid(new ParcelUuid(SERVIE_UUID)).build();

                mAdvertiser.startAdvertising(settings, advertiseData, mAdvertiseCallback);
            }
            else {
                Log.d("BLE", "stop advertising..");

                mAdvertiseButton.setText("Start advertise");
                mTextView.setText("");

                mAdvertiser.stopAdvertising(mAdvertiseCallback);

                cleanupPeripheral();
            }
        }
    }

    private void setupGattServer(BluetoothManager manager) {
        mGattServer = manager.openGattServer(getApplicationContext(),
                mGattServerCallback);
        if(mGattServer != null) {
            Log.d("BLE", "opening gatt server succeeded");

            BluetoothGattService service = new BluetoothGattService(SERVIE_UUID,
                    BluetoothGattService.SERVICE_TYPE_PRIMARY);
            mCharacteristic = new BluetoothGattCharacteristic(
                    CHARACTERISTIC_UUID,
                    BluetoothGattCharacteristic.PROPERTY_READ,
                    BluetoothGattCharacteristic.PERMISSION_READ);

            if(service.addCharacteristic(mCharacteristic)) {
                Log.d("BLE", "adding characteristic succeeded");

                if (mGattServer.addService(service)) {
                    Log.d("BLE", "adding gatt server succeeded");
                } else {
                    Log.e("BLE", "adding gatt server failed");
                }
            } else {
                Log.e("BLE", "adding characteristic failed");
            }
        } else {
            Log.e("BLE", "opening gatt server failed");
        }
    }

    private void cleanupPeripheral() {
        mIsAdvertising = false;

        mScanButton.setEnabled(true);
    }
}
