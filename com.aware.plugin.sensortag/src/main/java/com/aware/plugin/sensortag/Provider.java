/**
 * @author: denzil
 */
package com.aware.plugin.sensortag;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

import com.aware.Aware;
import com.aware.utils.DatabaseHelper;

import java.util.HashMap;

public class Provider extends ContentProvider {

    /**
     * Authority of this content provider
     */
    public static String AUTHORITY = "com.aware.plugin.sensortag.provider.sensortag";

    /**
     * ContentProvider database version. Increment every time you modify the database structure
     */
    public static final int DATABASE_VERSION = 4;

    public static final class Sensor_Data implements BaseColumns {
        private Sensor_Data() {
        }

        /**
         * Your ContentProvider table content URI.<br/>
         * The last segment needs to match your database table name
         */
        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/plugin_sensortag");

        /**
         * How your data collection is identified internally in Android (vnd.android.cursor.dir). <br/>
         * It needs to be /vnd.aware.plugin.XXX where XXX is your plugin name (no spaces!).
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.plugin.sensortag";

        /**
         * How each row is identified individually internally in Android (vnd.android.cursor.item). <br/>
         * It needs to be /vnd.aware.plugin.XXX where XXX is your plugin name (no spaces!).
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.plugin.sensortag";

        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String UPDATE_PERIOD = "update_period";
        public static final String SENSOR = "sensor";
        public static final String VALUE = "value";
        public static final String UNIT = "unit";
        /*public static final String ELAPSED_DEVICE_ON = "double_elapsed_device_on";
        public static final String ELAPSED_DEVICE_OFF = "double_elapsed_device_off";*/
    }

    //ContentProvider query indexes
    private static final int DEVICE_USAGE = 1;
    private static final int DEVICE_USAGE_ID = 2;

    /**
     * Database stored in external folder: /AWARE/plugin_device_usage.db
     */
    public static final String DATABASE_NAME = "plugin_sensortag.db";

    /**
     * Database tables:<br/>
     * - plugin_phone_usage
     */
    public static final String[] DATABASE_TABLES = {"plugin_sensortag"};

    /**
     * Database table fields
     */
    public static final String[] TABLES_FIELDS = {
            Sensor_Data._ID + " integer primary key autoincrement," +
                    Sensor_Data.TIMESTAMP + " real default 0," +
                    Sensor_Data.DEVICE_ID + " text default ''," +
                    Sensor_Data.UPDATE_PERIOD + " text default ''," +
                    Sensor_Data.SENSOR + " text default ''," +
                    Sensor_Data.VALUE + " real default 0," +
                    Sensor_Data.UNIT + " text default '' "
    };

    private static UriMatcher sUriMatcher = null;
    private static HashMap<String, String> sensorDataHash = null;
    private DatabaseHelper dbHelper;
    private static SQLiteDatabase database;

    /**
     * Returns the provider authority that is dynamic
     * @return
     */
    public static String getAuthority(Context context) {
        Log.i("AUTHORITY", context.getPackageName());
        AUTHORITY = context.getPackageName() + ".provider.sensortag";
        return AUTHORITY;
    }

    private void initialiseDatabase() {
        if (dbHelper == null)
            dbHelper = new DatabaseHelper(getContext(), DATABASE_NAME, null, DATABASE_VERSION, DATABASE_TABLES, TABLES_FIELDS);
        if (database == null)
            database = dbHelper.getWritableDatabase();
    }

    @Override
    public synchronized int delete(Uri uri, String selection, String[] selectionArgs) {
        initialiseDatabase();

        database.beginTransaction();

        int count;
        switch (sUriMatcher.match(uri)) {
            case DEVICE_USAGE:
                count = database.delete(DATABASE_TABLES[0], selection, selectionArgs);
                break;
            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        database.setTransactionSuccessful();
        database.endTransaction();
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case DEVICE_USAGE:
                return Sensor_Data.CONTENT_TYPE;
            case DEVICE_USAGE_ID:
                return Sensor_Data.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public synchronized Uri insert(Uri uri, ContentValues new_values) {
        initialiseDatabase();

        ContentValues values = (new_values != null) ? new ContentValues(new_values) : new ContentValues();

        database.beginTransaction();

        switch (sUriMatcher.match(uri)) {
            case DEVICE_USAGE:
                long _id = database.insertWithOnConflict(DATABASE_TABLES[0],
                        Sensor_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (_id > 0) {
                    Uri dataUri = ContentUris.withAppendedId(
                            Sensor_Data.CONTENT_URI, _id);
                    getContext().getContentResolver().notifyChange(dataUri, null);
                    return dataUri;
                }
                database.endTransaction();
                throw new SQLException("Failed to insert row into " + uri);
            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public boolean onCreate() {
        Log.i("Package name", getContext().getPackageName().toString());
        AUTHORITY = getContext().getPackageName() + ".provider.sensortag";

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(AUTHORITY, DATABASE_TABLES[0], DEVICE_USAGE); //URI for all records
        sUriMatcher.addURI(AUTHORITY, DATABASE_TABLES[0] + "/#", DEVICE_USAGE_ID); //URI for a single record

        sensorDataHash = new HashMap<String, String>();
        sensorDataHash.put(Sensor_Data._ID, Sensor_Data._ID);
        sensorDataHash.put(Sensor_Data.TIMESTAMP, Sensor_Data.TIMESTAMP);
        sensorDataHash.put(Sensor_Data.DEVICE_ID, Sensor_Data.DEVICE_ID);
        sensorDataHash.put(Sensor_Data.UPDATE_PERIOD, Sensor_Data.UPDATE_PERIOD);
        sensorDataHash.put(Sensor_Data.SENSOR, Sensor_Data.SENSOR);
        sensorDataHash.put(Sensor_Data.VALUE, Sensor_Data.VALUE);
        sensorDataHash.put(Sensor_Data.UNIT, Sensor_Data.UNIT);

        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        initialiseDatabase();

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        switch (sUriMatcher.match(uri)) {
            case DEVICE_USAGE:
                qb.setTables(DATABASE_TABLES[0]);
                qb.setProjectionMap(sensorDataHash);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        try {
            Cursor c = qb.query(database, projection, selection, selectionArgs,
                    null, null, sortOrder);
            c.setNotificationUri(getContext().getContentResolver(), uri);
            return c;
        } catch (IllegalStateException e) {
            if (Aware.DEBUG)
                Log.e(Aware.TAG, e.getMessage());
            return null;
        }
    }

    @Override
    public synchronized int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        initialiseDatabase();

        database.beginTransaction();

        int count;
        switch (sUriMatcher.match(uri)) {
            case DEVICE_USAGE:
                count = database.update(DATABASE_TABLES[0], values, selection,
                        selectionArgs);
                break;
            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        database.setTransactionSuccessful();
        database.endTransaction();
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }
}
