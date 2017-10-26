AWARE Plugin: SensorTag
==========================

This plugin receives data from the TI SensorTag.

# Settings
Parameters adjustable on the dashboard and client:
- **status_plugin_collection_frequency**: (integer) Data Collection Frequency (Default 10Hz)

# Broadcasts
**ACTION_AWARE_PLUGIN_SENSORTAG**

Broadcast as sessions toggle between usage-not usage, with the following extras:
- **Sensor Name**: The sensor data was received from.
- **Update Period**: The frequency.
- **Unit**: The unit of the reading.
- **Value**: The value obtained.

# Providers
##  Device Usage Data
> content://com.aware.plugin.sensortag.provider.sensortag/sensortag

Field | Type | Description
----- | ---- | -----------
_id | INTEGER | primary key auto-incremented
timestamp | REAL | unix timestamp in milliseconds of sample
device_id | TEXT | AWARE device ID
update_period | TEXT | Frequency at which sensor collects data
sensor| TEXT | One of Accelerometer, Gyro, Magnetometer, Humidity, Light or Pressure
value | REAL | Value from the sensor
unit | TEXT | Unit of the reading
