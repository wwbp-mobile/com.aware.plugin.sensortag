package com.aware.plugin.fitbit;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class DevicePicker extends AppCompatActivity {

    private ListView mDevicesListView;
    private ArrayList<String> mDeviceList = new ArrayList<>();
    private BluetoothAdapter mBluetoothAdapter;
    private int REQUEST_ENABLE_BT = 1;
    private Handler mHandler;
    private static final long SCAN_PERIOD = 10000;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private BluetoothGatt mGatt;
    private BluetoothGattService mMovService;
    private BluetoothGattCharacteristic mRead, mEnable, mPeriod;
    private LinkedList<Measurement> mRecording;
    private Calendar previousRead;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_picker);

        mHandler = new Handler();
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "Low Energy Bluetooth service not supported", Toast.LENGTH_SHORT)
                    .show();
            finish();
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(
                Context.BLUETOOTH_SERVICE);
        mDevicesListView = (ListView) findViewById(R.id.devices_available_to_connect);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBluetoothIntent = new Intent((mBluetoothAdapter.ACTION_REQUEST_ENABLE));
            startActivityForResult(enableBluetoothIntent, REQUEST_ENABLE_BT);
        } else {
            if (Build.VERSION.SDK_INT >= 21) {
                mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
                settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                filters = new ArrayList<ScanFilter>();
            }

            scanLeDevice(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            scanLeDevice(false);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                finish();
                return;
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void scanLeDevice(boolean enable) {
        if (enable) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if(Build.VERSION.SDK_INT < 21) {
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    } else {
                        mLEScanner.stopScan(mScanCallback);
                    }
                }
            }, SCAN_PERIOD);

            if (Build.VERSION.SDK_INT < 21) {
                mBluetoothAdapter.startLeScan(mLeScanCallback);
            } else {
                mLEScanner.startScan(filters, settings, mScanCallback);
            }
        } else {
            if (Build.VERSION.SDK_INT < 21) {
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            } else {
                mLEScanner.stopScan(mScanCallback);
            }
        }


    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice bleDevice = result.getDevice();
            connectToDevice(bleDevice);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result: results) {
                Log.i("ScanResult - Results", result.toString());
            }
        }

        public void onScanFailed(int errorCode) {
            Log.e("Scan failed", "Error Code: " + errorCode);
        }
    };

    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.i("onLeScan", bluetoothDevice.toString());
                            connectToDevice(bluetoothDevice);
                        }
                    });
                }
            };

    // TODO CHANGE TO RETRIEVE LIST OF DEVICES AND CONNECT TO THE ONE WITH THE RIGHT MAC ADDRESS
    public void connectToDevice(BluetoothDevice bluetoothDevice) {
        if(mGatt == null && bluetoothDevice.getAddress().equals("A0:E6:F8:AE:36:02")) {
            Log.i("MAC ADDRESS", bluetoothDevice.getAddress());
            mGatt = bluetoothDevice.connectGatt(this, false, gattCallback);
            scanLeDevice(false);
        }

    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        double result [];
        Calendar currentTime;
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i("onConnectionStateChange", "Status: " + status);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    gatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    break;
                default:
                    Log.e("gattCallback", "STATE_OTHER");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            mMovService = mGatt.getService(UUID.fromString("F000AA80-0451-4000-B000-000000000000"));
            mEnable = mMovService.getCharacteristic(UUID.fromString("F000AA82-0451-4000-B000-000000000000"));
            mEnable.setValue(0b1000111000, BluetoothGattCharacteristic.FORMAT_UINT16, 0);
            mGatt.writeCharacteristic(mEnable);
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            if (characteristic == mEnable) {
                mPeriod = mMovService.getCharacteristic(UUID.fromString("F000AA83-0451-4000-B000-000000000000"));
                mPeriod.setValue(0x0A, BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                mGatt.writeCharacteristic(mPeriod);
            } else if (characteristic == mPeriod) {
                mRead = mMovService.getCharacteristic(UUID.fromString("F000AA81-0451-4000-B000-000000000000"));
                previousRead = Calendar.getInstance();
                mGatt.readCharacteristic(mRead);
                deviceConnected();
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            result = Util.convertAccel(characteristic.getValue());
            Log.i("X", String.valueOf(result[0]));
            Log.i("Y", String.valueOf(result[1]));
            Log.d("Z", String.valueOf(result[2]));
            currentTime = Calendar.getInstance();
            long diff = currentTime.getTimeInMillis() - previousRead.getTimeInMillis();
            if (diff < 100) {
                try {
                    Thread.sleep(100-diff);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            previousRead = Calendar.getInstance();
            mGatt.readCharacteristic(mRead);

        }


    };

    private void deviceConnected() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean hasConnection = true;
                while (hasConnection) {
                    long timeDiff = Calendar.getInstance().getTimeInMillis() - previousRead.getTimeInMillis();
                    if (timeDiff > 2000) {
                        hasConnection = false;
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }).start();
    }



    /*private void displayGattServices(List<BluetoothGattService> services) {
        if (services == null) {
            return;
        }

        String uuid = null;

        ArrayList<HashMap<String, String>> gattServiceData
                = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics
                = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        for ()
    }*


    @Override
    protected void onDestroy() {
        if (mGatt == null) {
            return;
        }

        mGatt.close();
        mGatt = null;
        super.onDestroy();
    }



    /*private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(BluetoothDevice.ACTION_FOUND.equals(action)) {

                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                mDeviceList.add(device.getName() + "\n" + device.getAddress());

                mDevicesListView.setAdapter(new ArrayAdapter<>(context,
                        android.R.layout.simple_list_item_1, mDeviceList));

            }
        }
    };*/
}
