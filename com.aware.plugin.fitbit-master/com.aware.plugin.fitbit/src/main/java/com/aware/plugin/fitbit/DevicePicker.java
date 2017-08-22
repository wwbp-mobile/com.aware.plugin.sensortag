package com.aware.plugin.fitbit;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
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
    private List<BluetoothGattService> mServiceList;
    private BluetoothGattCharacteristic mRead, mEnable, mPeriod;
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
        byte result [];
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
            // GET ALL AVAILABLE SERVICES
            mServiceList = mGatt.getServices();


            // BUILD CHARACTERISTIC LIST
            for (int serviceNum = 0; serviceNum < mServiceList.size(); serviceNum++) {
                BluetoothGattService service = mServiceList.get(serviceNum);
                // Get characteristics
                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                String serviceUUID = service.getUuid().toString();

                if (serviceUUID.compareTo(SensorTagGatt.UUID_IRT_SERV.toString()) == 0) {
                    enableNotifications(service, SensorTagGatt.UUID_IRT_DATA);
                    safeSleep();
                    enableService(service, SensorTagGatt.UUID_IRT_CONF);
                    safeSleep();
                }

                else if (serviceUUID.compareTo(SensorTagGatt.UUID_HUM_SERV.toString()) == 0) {
                    enableNotifications(service, SensorTagGatt.UUID_HUM_DATA);
                    safeSleep();
                    enableService(service, SensorTagGatt.UUID_HUM_CONF);
                    safeSleep();
                }

                else if (serviceUUID.compareTo(SensorTagGatt.UUID_OPT_SERV.toString()) == 0) {
                    enableNotifications(service, SensorTagGatt.UUID_OPT_DATA);
                    safeSleep();
                    enableService(service, SensorTagGatt.UUID_OPT_CONF);
                    safeSleep();
                } else if (serviceUUID.compareTo(SensorTagGatt.UUID_BAR_SERV.toString()) == 0) {
                    enableNotifications(service, SensorTagGatt.UUID_BAR_DATA);
                    safeSleep();
                    enableService(service, SensorTagGatt.UUID_BAR_CONF);
                } else if (serviceUUID.compareTo(SensorTagGatt.UUID_MOV_SERV.toString()) == 0) {
                    enableNotifications(service, SensorTagGatt.UUID_MOV_DATA);
                    safeSleep();
                    enableMotionService(service, SensorTagGatt.UUID_MOV_CONF, true);
                    safeSleep();
                }
            }

        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            gatt.readCharacteristic(characteristic);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.i("Characteristic UUID:", characteristic.getUuid().toString());



            /*currentTime = Calendar.getInstance();
            long diff = currentTime.getTimeInMillis() - previousRead.getTimeInMillis();
            if (diff < 100) {
                try {
                    Thread.sleep(100-diff);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            previousRead = Calendar.getInstance();
            mGatt.readCharacteristic(mRead);*/

        }


    };

    private void enableMotionService(BluetoothGattService service, UUID uuidMovConf, boolean bool) {
        byte b[] = new byte[] {0x7F,0x00};
        // 0x7F (hexadecimal) = 127 (decimal)
        // 127 = 2^0 + 2^1 ... 2^6
        // Enables all bits from 0-6

        if (bool) {
            b[0] = (byte)0xFF;  // Enables bit 7
        }

        BluetoothGattCharacteristic config = service.getCharacteristic(uuidMovConf);
        config.setValue(b);
        mGatt.writeCharacteristic(config);
    }

    private void enableNotifications(BluetoothGattService service, UUID uuidData) {
        BluetoothGattCharacteristic dataCharacteristic = service.getCharacteristic(uuidData);
        mGatt.setCharacteristicNotification(dataCharacteristic, true);

        BluetoothGattDescriptor config = dataCharacteristic.getDescriptor(SensorTagGatt.UUID_NOTIFICATIONS);
        config.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mGatt.writeDescriptor(config);
    }

    private void safeSleep() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void enableService(BluetoothGattService service, UUID uuidConf) {
        Log.i("Enabling:", uuidConf.toString());
        BluetoothGattCharacteristic config = service.getCharacteristic(uuidConf);
        config.setValue(new byte[] {1});
        mGatt.writeCharacteristic(config);
        Log.i("Enabled", uuidConf.toString());
    }

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
