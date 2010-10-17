
package com.android.fm.radio;

import com.android.fm.R;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.Preference.OnPreferenceClickListener;
import android.util.Log;

public class Settings extends PreferenceActivity implements OnSharedPreferenceChangeListener,
        OnPreferenceClickListener {
    public static final String RX_MODE = "rx_mode";

    public static final String REGIONAL_BAND_KEY = "regional_band";

    public static final String AUDIO_OUTPUT_KEY = "audio_output_mode";

    public static final String RECORD_DURATION_KEY = "record_duration";

    public static final String AUTO_AF = "af_checkbox_preference";
    
    public static final String BT_EXIT_BEHAVIOUR = "bt_exit_behaviour";

    public static final String RESTORE_FACTORY_DEFAULT = "revert_to_fac";

    private static final String ABOUT_KEY = "about";

    public static final int RESTORE_FACTORY_DEFAULT_INT = 1;

    public static final String RESTORE_FACTORY_DEFAULT_ACTION = "com.android.fm.radio.settings.revert_to_defaults";

    private static final String LOGTAG = FMRadio.LOGTAG;

    private ListPreference mBandPreference;

    private ListPreference mAudioPreference;

    private ListPreference mRecordDurPreference;

    private CheckBoxPreference mAfPref;
    
    private ListPreference mBluetoothBehaviour;

    private Preference mRestoreDefaultPreference;

    private Preference mAboutPreference;

    private FmSharedPreferences mPrefs = null;

    private boolean mRxMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        if (intent != null) {
            mRxMode = intent.getBooleanExtra(RX_MODE, false);
        }
        mPrefs = new FmSharedPreferences(this);
        if (mPrefs != null) {
            setPreferenceScreen(createPreferenceHierarchy());
        }
    }

    private PreferenceScreen createPreferenceHierarchy() {
        int index = 0;
        if (mPrefs == null) {
            return null;
        }
        // Root
        PreferenceScreen root = getPreferenceManager().createPreferenceScreen(this);

        // Band/Country
        String[] summaryBandItems = getResources().getStringArray(R.array.regional_band_summary);
        mBandPreference = new ListPreference(this);
        mBandPreference.setEntries(R.array.regional_band_entries);
        mBandPreference.setEntryValues(R.array.regional_band_values);
        mBandPreference.setDialogTitle(R.string.sel_band_menu);
        mBandPreference.setKey(REGIONAL_BAND_KEY);
        mBandPreference.setTitle(R.string.regional_band);
        index = FmSharedPreferences.getCountry();
        Log.d(LOGTAG, "createPreferenceHierarchy: Country: " + index);
        // Get the preference and list the value.
        if ((index < 0) || (index >= summaryBandItems.length)) {
            index = 0;
        }
        Log.d(LOGTAG, "createPreferenceHierarchy: CountrySummary: " + summaryBandItems[index]);
        mBandPreference.setSummary(summaryBandItems[index]);
        mBandPreference.setValueIndex(index);
        root.addPreference(mBandPreference);

        if (mRxMode) {
            // Audio Output (Stereo or Mono)
            String[] summaryAudioModeItems = getResources()
                    .getStringArray(R.array.ster_mon_entries);
            mAudioPreference = new ListPreference(this);
            mAudioPreference.setEntries(R.array.ster_mon_entries);
            mAudioPreference.setEntryValues(R.array.ster_mon_values);
            mAudioPreference.setDialogTitle(R.string.sel_audio_output);
            mAudioPreference.setKey(AUDIO_OUTPUT_KEY);
            mAudioPreference.setTitle(R.string.aud_output_mode);
            boolean audiomode = FmSharedPreferences.getAudioOutputMode();
            if (audiomode) {
                index = 0;
            } else {
                index = 1;
            }
            Log.d(LOGTAG, "createPreferenceHierarchy: audiomode: " + audiomode);
            mAudioPreference.setSummary(summaryAudioModeItems[index]);
            mAudioPreference.setValueIndex(index);
            root.addPreference(mAudioPreference);

            // AF Auto Enable (Checkbox)
            mAfPref = new CheckBoxPreference(this);
            mAfPref.setKey(AUTO_AF);
            mAfPref.setTitle(R.string.auto_select_af);
            mAfPref.setSummaryOn(R.string.auto_select_af_enabled);
            mAfPref.setSummaryOff(R.string.auto_select_af_disabled);
            boolean bAFAutoSwitch = FmSharedPreferences.getAutoAFSwitch();
            Log.d(LOGTAG, "createPreferenceHierarchy: bAFAutoSwitch: " + bAFAutoSwitch);
            mAfPref.setChecked(bAFAutoSwitch);
            root.addPreference(mAfPref);

            if (FMRadio.RECORDING_ENABLE) {
                String[] summaryRecordItems = getResources().getStringArray(
                        R.array.record_durations_entries);
                mRecordDurPreference = new ListPreference(this);
                mRecordDurPreference.setEntries(R.array.record_durations_entries);
                mRecordDurPreference.setEntryValues(R.array.record_duration_values);
                mRecordDurPreference.setDialogTitle(R.string.sel_rec_dur);
                mRecordDurPreference.setKey(RECORD_DURATION_KEY);
                mRecordDurPreference.setTitle(R.string.record_dur);
                index = FmSharedPreferences.getRecordDuration();
                Log.d(LOGTAG, "createPreferenceHierarchy: recordDuration: " + index);
                // Get the preference and list the value.
                if ((index < 0) || (index >= summaryRecordItems.length)) {
                    index = 0;
                }
                Log.d(LOGTAG, "createPreferenceHierarchy: recordDurationSummary: "
                        + summaryRecordItems[index]);
                mRecordDurPreference.setSummary(summaryRecordItems[index]);
                mRecordDurPreference.setValueIndex(index);
                root.addPreference(mRecordDurPreference);
            }
        }
        
        mBluetoothBehaviour = new ListPreference(this);
        mBluetoothBehaviour.setEntries(R.array.bt_exit_behaviour_entries);
        mBluetoothBehaviour.setEntryValues(R.array.bt_exit_behaviour_values);
        mBluetoothBehaviour.setDialogTitle(R.string.pref_bt_behaviour_on_exit_dialog_title);
        mBluetoothBehaviour.setKey(BT_EXIT_BEHAVIOUR);
        mBluetoothBehaviour.setTitle(R.string.pref_bt_behaviour_on_exit_title);
        mBluetoothBehaviour.setSummary(R.string.pref_bt_behaviour_on_exit_summary);
        root.addPreference(mBluetoothBehaviour);        

        mRestoreDefaultPreference = new Preference(this);
        mRestoreDefaultPreference.setTitle(R.string.settings_revert_defaults_title);
        mRestoreDefaultPreference.setKey(RESTORE_FACTORY_DEFAULT);
        mRestoreDefaultPreference.setSummary(R.string.settings_revert_defaults_summary);
        mRestoreDefaultPreference.setOnPreferenceClickListener(this);
        root.addPreference(mRestoreDefaultPreference);

        // Add a new category
        PreferenceCategory prefCat = new PreferenceCategory(this);
        prefCat.setTitle(R.string.about_title);
        root.addPreference(prefCat);

        mAboutPreference = new Preference(this);
        mAboutPreference.setTitle(R.string.about_title);
        mAboutPreference.setKey(ABOUT_KEY);
        mAboutPreference.setSummary(R.string.about_summary);
        mAboutPreference.setOnPreferenceClickListener(this);
        root.addPreference(mAboutPreference);

        return root;
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(REGIONAL_BAND_KEY)) {
            String[] summaryBandItems = getResources()
                    .getStringArray(R.array.regional_band_summary);
            String valueStr = sharedPreferences.getString(key, "");
            int index = 0;
            if (valueStr != null) {
                index = mBandPreference.findIndexOfValue(valueStr);
            }
            if ((index < 0) || (index >= summaryBandItems.length)) {
                index = 0;
                mBandPreference.setValueIndex(0);
            }
            Log.d(LOGTAG, "onSharedPreferenceChanged: Country Change: " + index);
            mBandPreference.setSummary(summaryBandItems[index]);
            FmSharedPreferences.setCountry(index);
            FMRadio.fmConfigure();
        } else {
            if (mRxMode) {
                if (key.equals(AUTO_AF)) {
                    boolean bAFAutoSwitch = mAfPref.isChecked();
                    Log.d(LOGTAG, "onSharedPreferenceChanged: Auto AF Enable: " + bAFAutoSwitch);
                    FmSharedPreferences.setAutoAFSwitch(bAFAutoSwitch);
                    FMRadio.fmAutoAFSwitch();
                } else if (key.equals(RECORD_DURATION_KEY)) {
                    if (FMRadio.RECORDING_ENABLE) {
                        String[] recordItems = getResources().getStringArray(
                                R.array.record_durations_entries);
                        String valueStr = mRecordDurPreference.getValue();
                        int index = 0;
                        if (valueStr != null) {
                            index = mRecordDurPreference.findIndexOfValue(valueStr);
                        }
                        if ((index < 0) || (index >= recordItems.length)) {
                            index = 0;
                            mRecordDurPreference.setValueIndex(index);
                        }
                        Log
                                .d(LOGTAG, "onSharedPreferenceChanged: recorddur: "
                                        + recordItems[index]);
                        mRecordDurPreference.setSummary(recordItems[index]);
                        FmSharedPreferences.setRecordDuration(index);
                    }
                } else if (key.equals(AUDIO_OUTPUT_KEY)) {
                    String[] bandItems = getResources().getStringArray(R.array.ster_mon_entries);
                    String valueStr = mAudioPreference.getValue();
                    int index = 0;
                    if (valueStr != null) {
                        index = mAudioPreference.findIndexOfValue(valueStr);
                    }
                    if (index != 1) {
                        if (index != 0) {
                            index = 0;
                            /* It shud be 0(Stereo) or 1(Mono) */
                            mAudioPreference.setValueIndex(index);
                        }
                    }
                    Log.d(LOGTAG, "onSharedPreferenceChanged: audiomode: " + bandItems[index]);
                    mAudioPreference.setSummary(bandItems[index]);
                    if (index == 0) {
                        // Stereo
                        FmSharedPreferences.setAudioOutputMode(true);
                    } else {
                        // Mono
                        FmSharedPreferences.setAudioOutputMode(false);
                    }
                    FMRadio.fmAudioOutputMode();
                }
                else if (key.equals(BT_EXIT_BEHAVIOUR)) {
                    String[] btChoices = getResources().getStringArray(R.array.bt_exit_behaviour_entries);
                    String valueStr = mBluetoothBehaviour.getValue();
                    Log.d(LOGTAG, "onSharedPreferenceChanged: BT behaviour: " + btChoices[mBluetoothBehaviour.findIndexOfValue(valueStr)]);
                    FmSharedPreferences.setBluetoothExitBehaviour(Integer.parseInt(valueStr));
                }
            }
        }
        if (mPrefs != null) {
            mPrefs.Save();
        }
    }

    public boolean onPreferenceClick(Preference preference) {
        boolean handled = false;
        if (preference == mRestoreDefaultPreference) {
            showDialog(RESTORE_FACTORY_DEFAULT_INT);
        } else if (preference == mAboutPreference) {
            Intent viewIntent = new Intent("android.intent.action.VIEW", Uri.parse("http://www.miui.com"));
            startActivity(viewIntent);
            handled = true;
        }

        return handled;
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case RESTORE_FACTORY_DEFAULT_INT:
                return new AlertDialog.Builder(this).setIcon(R.drawable.alert_dialog_icon)
                        .setTitle(R.string.settings_revert_confirm_title).setMessage(
                                R.string.settings_revert_confirm_msg).setPositiveButton(
                                R.string.alert_dialog_ok, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        Intent data = new Intent(RESTORE_FACTORY_DEFAULT_ACTION);
                                        setResult(RESULT_OK, data);
                                        restoreSettingsDefault();
                                        finish();
                                    }

                                }).setNegativeButton(R.string.alert_dialog_cancel,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                    }
                                }).create();
            default:
                break;
        }
        return null;
    }

    private void restoreSettingsDefault() {
        if (mPrefs != null) {
            mBandPreference.setValueIndex(0);
            if (mRxMode) {
                mAudioPreference.setValueIndex(0);
                if (FMRadio.RECORDING_ENABLE) {
                    mRecordDurPreference.setValueIndex(0);
                }
                mAfPref.setChecked(false);
                FmSharedPreferences.SetDefaults();
            } else {
                FmSharedPreferences.setCountry(FmSharedPreferences.REGIONAL_BAND_NORTH_AMERICA);
            }
            mPrefs.Save();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(
                this);
    }

}
