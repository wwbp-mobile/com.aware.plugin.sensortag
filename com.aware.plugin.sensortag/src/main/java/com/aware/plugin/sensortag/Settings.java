package com.aware.plugin.sensortag;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import com.aware.Aware;

public class Settings extends PreferenceActivity implements OnSharedPreferenceChangeListener {

    /**
     * State of this plugin
     */
    public static final String STATUS_PLUGIN_COLLECTION_FREQUENCY = "status_plugin_collection_frequency";

    private ListPreference frequency;

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
        frequency = (ListPreference) findPreference(STATUS_PLUGIN_COLLECTION_FREQUENCY);
        if (Aware.getSetting(this, STATUS_PLUGIN_COLLECTION_FREQUENCY).length() == 0) {
            Aware.setSetting(this, STATUS_PLUGIN_COLLECTION_FREQUENCY, "30");
        }
        //frequency.setSummary(Aware.getSetting(this, STATUS_PLUGIN_COLLECTION_FREQUENCY));
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference preference = (Preference) findPreference(key);
        if (preference.getKey().equals(STATUS_PLUGIN_COLLECTION_FREQUENCY)) {
            Aware.setSetting(this, key, sharedPreferences.getString(key, "30"));
            preference.setSummary(Aware.getSetting(this, STATUS_PLUGIN_COLLECTION_FREQUENCY));
        }
        if (Aware.getSetting(this, STATUS_PLUGIN_COLLECTION_FREQUENCY).equals("30")) {
            Aware.startPlugin(getApplicationContext(), "com.aware.plugin.sensortag");
        } else {
            Aware.stopPlugin(getApplicationContext(), "com.aware.plugin.sensortag");
        }
    }
}
