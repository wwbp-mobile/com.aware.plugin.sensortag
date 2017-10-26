/*
* Created by: Aayush Chadha
* Last Updated: 26th October 2017
* Adapted from:
* 1) https://github.com/denzilferreira/aware-plugin-template
* 2) https://github.com/denzilferreira/com.aware.plugin.device_usage
* 3) https://github.com/denzilferreira/com.aware.plugin.fitbit
*
* */
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
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;
import com.aware.plugin.sensortag.Provider.Sensor_Data;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public class DevicePicker extends AppCompatActivity {

    private HashSet<BluetoothDevice> mDeviceList = new HashSet<>();
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
    private BluetoothDevice selectedDevice;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_picker);
        getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);


        mHandler = new Handler();
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "Low Energy Bluetooth service not supported", Toast.LENGTH_SHORT)
                    .show();
            finish();
        }

        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(
                Context.BLUETOOTH_SERVICE);

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

    /* Newer Android versions support this method for scanning for BLE Devices */
    private ScanCallback mScanCallback = new ScanCallback() {
        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        @Override
        /*
        onScanResult is called each time we see a device brodcasting. In order to prevent over
        populating the radio group view, we first check if the device is a BLE DEvice, and then
        check our hash set to see if we haven't already added it to the view and then finally check
        if the device is indeed a SensorTag.
         */
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice bleDevice = result.getDevice();
            if (bleDevice.getType() == BluetoothDevice.DEVICE_TYPE_LE
                &&
                !(mDeviceList.contains(bleDevice))
                && (bleDevice.getName().contains("SensorTag"))) {
                // Add to HashSet
                mDeviceList.add(bleDevice);
                // Call function to create new Radio Button View
                addToView(bleDevice);
            }

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

    /* Each new device obtained from the scan results is added to the Radio Group view here. We set
       a listener to look for changed radio button values. But changing the selected device doesn't
       initiate the connectToDevice() function, for that, we set a listener to the
       SELECT DEVICE BUTTON.
     */
    private void addToView(BluetoothDevice bluetoothDevice) {
        RadioGroup singleChoice = new RadioGroup(this);

        RadioButton rDevice = new RadioButton(this);
        rDevice.setText(bluetoothDevice.getName());
        rDevice.setTag(bluetoothDevice.getAddress());
        singleChoice.addView(rDevice);

        LinearLayout device_container = findViewById(R.id.device_picker);
        device_container.addView(singleChoice);

        singleChoice.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup radioGroup, int checkedId) {
                RadioButton selectedRadio = (RadioButton) radioGroup.findViewById(checkedId);
                for (BluetoothDevice bt: mDeviceList) {
                    if (bt.getAddress() == selectedRadio.getTag().toString()) {
                        selectedDevice = bt;
                    }
                }
            }
        });

        Button saveDevice = (Button) findViewById(R.id.select_device);
        saveDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                connectToDevice(selectedDevice);
                finish();
            }
        });
    }

    // Method used to find BLE Devices in old versions of Android
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {
                @Override
                public void onLeScan(final BluetoothDevice bluetoothDevice, int i, byte[] bytes) {
                    Log.i("Bluetooth connection", "Success");
                    //mDeviceList.add(bluetoothDevice);
                   addToView(bluetoothDevice);
                }
            };

    // Use GATT to connect to selected device from the Radio View
    public void connectToDevice(BluetoothDevice bluetoothDevice) {

        Log.i("MAC ADDRESS", bluetoothDevice.getAddress());
        mGatt = bluetoothDevice.connectGatt(this, false, gattCallback);
        scanLeDevice(false);
        Toast.makeText(getApplicationContext(), "Connected to" + bluetoothDevice.getName(), Toast.LENGTH_SHORT)
                    .show();


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

        /* Crux of the App. Before we can start receiving data from the sensor, we must send NOTIFY
        * requests to each of the sensors we are interested in. This will ensure we receive data
        * continuously. Post this request, we have to actually ENABLE the sensors to get them
        * started on transmitting the values. The catch however lies in the fact that Android
        * silently ignores subsequent GATT requests if the previous one doesn't complete. To avoid
        * that and to get all sensors working, we put the thread to sleep for a short time period
        * which allows the previously issued request to complete.
        * */

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

                            enableNotifications(service, SensorTagGatt.UUID_BAR_DATA);
                            safeSleep();
                            enableService(service, SensorTagGatt.UUID_BAR_CONF);
                            safeSleep();
                        }
                    }

                }
            });
            worker.start();
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.i("Characteristic written:", characteristic.getUuid().toString());
        }

        /* We receive each of the sensor values here as a byte stream. These must be converted into
        * human readable values. */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            convertData(characteristic);
        }

    };

    // Convert characteristic data into byte stream and then insert into database for further operations
    private void convertData(BluetoothGattCharacteristic characteristic) {
        String characteristicUUID = characteristic.getUuid().toString();
        byte [] value = characteristic.getValue();
        double lastRead = System.currentTimeMillis();
        if (characteristicUUID.compareTo(SensorTagGatt.UUID_MOV_DATA.toString()) == 0) {

            data = SensorConversion.MOVEMENT_ACC.convert(value);

            insertIntoDatabase(lastRead, 10.0, "Accelerometer X", data.getX(), "G");
            insertIntoDatabase(lastRead, 10.0, "Accelerometer Y", data.getY(), "G");
            insertIntoDatabase(lastRead, 10.0, "Accelerometer Z", data.getZ(), "G");

            data = SensorConversion.MOVEMENT_GYRO.convert(value);

            insertIntoDatabase(lastRead, 10.0, "Gyro X", data.getX(), "degrees");
            insertIntoDatabase(lastRead, 10.0, "Gyro Y", data.getY(), "degrees");
            insertIntoDatabase(lastRead, 10.0, "Gyro Z", data.getZ(), "degrees");

            data = SensorConversion.MOVEMENT_MAG.convert(value);

            insertIntoDatabase(lastRead, 10.0, "Magnetometer X", data.getX(), "uT");
            insertIntoDatabase(lastRead, 10.0, "Magnetometer Y", data.getY(), "uT");
            insertIntoDatabase(lastRead, 10.0, "Magnetometer Z", data.getZ(), "uT");

        }

        else if (characteristicUUID.compareTo(SensorTagGatt.UUID_HUM_DATA.toString()) == 0) {

            data = SensorConversion.HUMIDITY2.convert(value);
            insertIntoDatabase(lastRead, 10.0, "Humidity", data.getX(), "rg");


        }

        else if (characteristicUUID.compareTo(SensorTagGatt.UUID_IRT_DATA.toString()) == 0) {

            data = SensorConversion.IR_TEMPERATURE.convert(value);
            insertIntoDatabase(lastRead, 10.0, "Ambient Temperature", data.getX(), "Celsius");
            insertIntoDatabase(lastRead, 10.0, "Infrared Temperature", data.getZ(), "Celsius");


        }

        else if (characteristicUUID.compareTo(SensorTagGatt.UUID_OPT_DATA.toString()) == 0) {

            data = SensorConversion.LUXOMETER.convert(value);
            insertIntoDatabase(lastRead, 10.0, "Light Intensity", data.getX(), "Lux");


        } else if (characteristicUUID.compareTo(SensorTagGatt.UUID_BAR_DATA.toString()) == 0) {

            data = SensorConversion.BAROMETER.convert(value);
            // TODO CHECK IF INSERT SHOULD HAVE A DIVIDE BY 100
            insertIntoDatabase(lastRead, 10.0, "Pressure X", data.getX(), "mBar");
            Log.i("Pressure Data:", String.format("%.1f mBar", (data.getX() / 100)));

        }

    }

    /* We need to broadcast the values for them to be accessible by the plugin class. Trying a direct
    * insert from here throws a Permission Denied error. */
    private void insertIntoDatabase(Double time, Double period, String sensorname, Double reading, String unit) {

        ContentValues row = new ContentValues();
        row.put(Sensor_Data.TIMESTAMP, time);
        row.put(Sensor_Data.DEVICE_ID, "Foo");
        row.put(Sensor_Data.UPDATE_PERIOD, period);
        row.put(Sensor_Data.SENSOR, sensorname);
        row.put(Sensor_Data.VALUE, reading);
        row.put(Sensor_Data.UNIT, unit);
        Intent sharedContext = new Intent("SENSOR_DATA");

        sharedContext.putExtra("Update_Period", period);
        sharedContext.putExtra("Sensor", sensorname);
        sharedContext.putExtra("Value", reading);
        sharedContext.putExtra("Unit", unit);

        sendBroadcast(sharedContext);

    }


    /* Motion (Accelerometer/Gyro/Mag) service has a different structure to it and requires a
       separate function to enable */
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

    /* Issue notify requests */
    private void enableNotifications(BluetoothGattService service, UUID uuidData) {
        BluetoothGattCharacteristic dataCharacteristic = service.getCharacteristic(uuidData);
        mGatt.setCharacteristicNotification(dataCharacteristic, true);

        BluetoothGattDescriptor config = dataCharacteristic.getDescriptor(SensorTagGatt.UUID_NOTIFICATIONS);
        config.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        mGatt.writeDescriptor(config);
    }

    /* Put thread to sleep */
    private void safeSleep() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /* Enable service request */
    private void enableService(BluetoothGattService service, UUID uuidConf) {
        BluetoothGattCharacteristic config = service.getCharacteristic(uuidConf);
        config.setValue(new byte[] {1});
        mGatt.writeCharacteristic(config);

    }
}
