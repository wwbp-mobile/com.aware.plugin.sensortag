package com.aware.plugin.sensortag;

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
import android.content.ContentValues;
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

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.plugin.sensortag.Provider.Sensor_Data;
import com.aware.plugin.sensortag.Plugin;
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
    private Measurement data;


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

            Thread worker = new Thread(new Runnable() {
                @Override
                public void run() {

                    for (int serviceNum = 0; serviceNum < mServiceList.size(); serviceNum++) {
                        BluetoothGattService service = mServiceList.get(serviceNum);
                        // Get characteristics
                        List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                        String serviceUUID = service.getUuid().toString();
                        Log.i("Service UUID", serviceUUID);
                        // Check if the retrieved service is the move service
                        if (serviceUUID.compareTo(SensorTagGatt.UUID_MOV_SERV.toString()) == 0) {
                            Log.i("Notify MOV", "true");
                            enableNotifications(service, SensorTagGatt.UUID_MOV_DATA);
                            safeSleep();
                            // Write to mEnable
                            enableMotionService(service, SensorTagGatt.UUID_MOV_CONF, true);
                            safeSleep();
                        }

                        else if (serviceUUID.compareTo(SensorTagGatt.UUID_HUM_SERV.toString()) == 0) {
                            Log.i("Notify HUM", "true");
                            enableNotifications(service, SensorTagGatt.UUID_HUM_DATA);
                            safeSleep();
                            enableService(service, SensorTagGatt.UUID_HUM_CONF);
                            safeSleep();

                        }

                        else if (serviceUUID.compareTo(SensorTagGatt.UUID_IRT_SERV.toString()) == 0) {
                            Log.i("Notify IRT", "true");
                            enableNotifications(service, SensorTagGatt.UUID_IRT_DATA);
                            safeSleep();
                            enableService(service, SensorTagGatt.UUID_IRT_CONF);
                            safeSleep();

                        }

                        else if (serviceUUID.compareTo(SensorTagGatt.UUID_OPT_SERV.toString()) == 0) {
                            Log.i("Notify OPT", "true");
                            enableNotifications(service, SensorTagGatt.UUID_OPT_DATA);
                            safeSleep();
                            enableService(service, SensorTagGatt.UUID_OPT_CONF);
                            safeSleep();

                        } else if (serviceUUID.compareTo(SensorTagGatt.UUID_BAR_SERV.toString()) == 0) {
                            Log.i("Notify BAR", "true");
                            enableNotifications(service, SensorTagGatt.UUID_BAR_DATA);
                            safeSleep();
                            enableService(service, SensorTagGatt.UUID_BAR_CONF);
                            safeSleep();
                        }
                    }

                }
            });
            worker.start();
            // BUILD CHARACTERISTIC LIST


        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.i("Characteristic written:", characteristic.getUuid().toString());
        }



        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            convertData(characteristic);
        }


    };

    private void convertData(BluetoothGattCharacteristic characteristic) {
        String characteristicUUID = characteristic.getUuid().toString();
        Log.i("Converting", characteristicUUID);
        byte [] value = characteristic.getValue();
        double lastRead = System.currentTimeMillis();
        if (characteristicUUID.compareTo(SensorTagGatt.UUID_MOV_DATA.toString()) == 0) {

            data = SensorConversion.MOVEMENT_ACC.convert(value);

            insertIntoDatabase(lastRead, 10.0, "Accelerometer X", data.getX(), "G");
            insertIntoDatabase(lastRead, 10.0, "Accelerometer Y", data.getY(), "G");
            insertIntoDatabase(lastRead, 10.0, "Accelerometer Z", data.getZ(), "G");

            Log.i("Acceleration X", String.format("%.2fG", data.getX()));
            Log.i("Acceleration Y", String.format("%.2fG", data.getY()));
            Log.i("Acceleration Z", String.format("%.2fG", data.getZ()));

            data = SensorConversion.MOVEMENT_GYRO.convert(value);

            insertIntoDatabase(lastRead, 10.0, "Gyro X", data.getX(), "degrees");
            insertIntoDatabase(lastRead, 10.0, "Gyro Y", data.getY(), "degrees");
            insertIntoDatabase(lastRead, 10.0, "Gyro Z", data.getZ(), "degrees");

            /*Log.i("GYRO X", String.format("%.2f degrees", data.getX()));
            Log.i("GYRO Y", String.format("%.2f degrees", data.getY()));
            Log.i("GYRO Z", String.format("%.2f degrees", data.getZ()));*/

            data = SensorConversion.MOVEMENT_MAG.convert(value);

            insertIntoDatabase(lastRead, 10.0, "Magnetometer X", data.getX(), "uT");
            insertIntoDatabase(lastRead, 10.0, "Magnetometer Y", data.getY(), "uT");
            insertIntoDatabase(lastRead, 10.0, "Magnetometer Z", data.getZ(), "uT");

            /*Log.i("Mag X", String.format("%.2fuT", data.getX()));
            Log.i("Mag Y", String.format("%.2fuT", data.getY()));
            Log.i("Mag Z", String.format("%.2fuT", data.getZ()));*/


        }

        else if (characteristicUUID.compareTo(SensorTagGatt.UUID_HUM_DATA.toString()) == 0) {

            data = SensorConversion.HUMIDITY2.convert(value);
            insertIntoDatabase(lastRead, 10.0, "Humidity", data.getX(), "rg");
            Log.i("HUMIDITY DATA:", String.format("Humidity: %.1f %%rH", data.getX()));


        }

        else if (characteristicUUID.compareTo(SensorTagGatt.UUID_IRT_DATA.toString()) == 0) {

            data = SensorConversion.IR_TEMPERATURE.convert(value);
            insertIntoDatabase(lastRead, 10.0, "Ambient Temperature", data.getX(), "Celsius");
            insertIntoDatabase(lastRead, 10.0, "Infrared Temperature", data.getZ(), "Celsius");
            Log.i("Ambient Temperature:", String.format("%.1f Celsius", data.getX()));
            Log.i("IR Temperature:", String.format("%.1f Celsius", data.getZ()));


        }

        else if (characteristicUUID.compareTo(SensorTagGatt.UUID_OPT_DATA.toString()) == 0) {

            data = SensorConversion.LUXOMETER.convert(value);
            insertIntoDatabase(lastRead, 10.0, "Light Intensity", data.getX(), "Lux");
            Log.i("Light Intensity", String.format("%.2f Lux", data.getX()));


        } else if (characteristicUUID.compareTo(SensorTagGatt.UUID_BAR_DATA.toString()) == 0) {

            data = SensorConversion.BAROMETER.convert(value);
            insertIntoDatabase(lastRead, 10.0, "Pressure X", data.getX(), "mBar");
            Log.i("Pressure Data:", String.format("%.1f mBar", (data.getX() / 100)));

        }

    }

    private void insertIntoDatabase(Double time, Double period, String sensorname, Double reading, String unit) {
        Log.i("CONTENT URI", Sensor_Data.CONTENT_URI.toString());

        ContentValues row = new ContentValues();
        row.put(Sensor_Data.TIMESTAMP, time);
        row.put(Sensor_Data.DEVICE_ID, "Foo");
        row.put(Sensor_Data.UPDATE_PERIOD, period);
        row.put(Sensor_Data.SENSOR, sensorname);
        row.put(Sensor_Data.VALUE, reading);
        row.put(Sensor_Data.UNIT, unit);
        Intent sharedContext = new Intent("SENSOR_DATA");
        //sharedContext.putExtra("Timestamp", time);
        //sharedContext.putExtra("Device_ID", "foo");
        sharedContext.putExtra("Update_Period", period);
        sharedContext.putExtra("Sensor", sensorname);
        sharedContext.putExtra("Value", reading);
        sharedContext.putExtra("Unit", unit);

        sendBroadcast(sharedContext);

        Log.d("DATA", row.toString());
        //getContentResolver().insert(Provider.Sensor_Data.CONTENT_URI, row);

        Log.i("db edited", "true");

    }

    private void enableMotionService(BluetoothGattService service, UUID uuidMovConf, boolean bool) {
        byte b[] = new byte[] {0x7F,0x00};
        // 0x7F (hexadecimal) = 127 (decimal)
        // 127 = 2^0 + 2^1 ... 2^6
        // Enables all bits from 0-6

        if (bool) {
            b[0] = (byte)0xFF;  // Enables bit 7
        }

        // Get Configuration Characteristic
        BluetoothGattCharacteristic config = service.getCharacteristic(uuidMovConf);
        config.setValue(b);
        mGatt.writeCharacteristic(config);
    }

    private void enableNotifications(BluetoothGattService service, UUID uuidData) {
        BluetoothGattCharacteristic dataCharacteristic = service.getCharacteristic(uuidData);
        mGatt.setCharacteristicNotification(dataCharacteristic, true);

        BluetoothGattDescriptor config = dataCharacteristic.getDescriptor(SensorTagGatt.UUID_NOTIFICATIONS);
        config.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        Log.i("Enabling", service.getUuid().toString());
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

    }

    /*private void deviceConnected() {

    }*/



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
