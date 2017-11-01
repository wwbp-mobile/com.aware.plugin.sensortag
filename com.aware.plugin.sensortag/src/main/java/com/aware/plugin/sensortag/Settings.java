package com.aware.plugin.sensortag;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;

import com.aware.Aware;

public class Settings extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener {

    /**
     * State of this plugin
     */
    public static final String PLUGIN_COLLECTION_FREQUENCY = "status_plugin_collection_frequency";
    public static final String STATUS_PLUGIN_SENSORTAG = "status_plugin_sensortag";

    private ListPreference frequency;
    private CheckBoxPreference status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        frequency = (ListPreference) findPreference(PLUGIN_COLLECTION_FREQUENCY);
        status = (CheckBoxPreference) findPreference(STATUS_PLUGIN_SENSORTAG);
        if (Aware.getSetting(this, STATUS_PLUGIN_SENSORTAG).length() == 0) {
            Aware.setSetting(this, STATUS_PLUGIN_SENSORTAG, true); //by default, the setting is true on install
        }
        status.setChecked(Aware.getSetting(getApplicationContext(), STATUS_PLUGIN_SENSORTAG).equals("true"));
        if (Aware.getSetting(this, PLUGIN_COLLECTION_FREQUENCY).length() == 0) {
            Log.i("Called again", "true");
            Aware.setSetting(this, PLUGIN_COLLECTION_FREQUENCY, "20");
        }
        //frequency.setSummary(Aware.getSetting(this, PLUGIN_COLLECTION_FREQUENCY));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference preference = (Preference) findPreference(key);
        Log.i("Preference key", preference.getKey());
        if (preference.getKey().equals(PLUGIN_COLLECTION_FREQUENCY)) {
            Aware.setSetting(this, key, sharedPreferences.getString(key, "30"));
            preference.setSummary(Aware.getSetting(this, PLUGIN_COLLECTION_FREQUENCY));
        }
        if (preference.getKey().equals(STATUS_PLUGIN_SENSORTAG)) {
            Aware.setSetting(this, key, sharedPreferences.getBoolean(key, false));
            status.setChecked(sharedPreferences.getBoolean(key, false));
        }

        if (Aware.getSetting(this, PLUGIN_COLLECTION_FREQUENCY).contains("0")) {
            Log.i("Starting again", "true");
            Aware.startPlugin(getApplicationContext(), "com.aware.plugin.sensortag");
        } else {
            Aware.stopPlugin(getApplicationContext(), "com.aware.plugin.sensortag");
        }
    }
}
