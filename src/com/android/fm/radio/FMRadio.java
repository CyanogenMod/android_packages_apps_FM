
package com.android.fm.radio;

import com.android.fm.R;
import com.android.fm.utils.FrequencyPicker;
import com.android.fm.utils.FrequencyPickerDialog;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.hardware.fmradio.FmConfig;
import android.media.AudioSystem;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Locale;

public class FMRadio extends Activity {
    public static final String LOGTAG = "FMRadio";

    public static final boolean RECORDING_ENABLE = false;

    /* menu Identifiers */

    private static final int MENU_SETTINGS = Menu.FIRST + 8;

    private static final int MENU_WIRED_HEADSET = Menu.FIRST + 9;

    /* Dialog Identifiers */
    private static final int DIALOG_SEARCH = 1;

    private static final int DIALOG_SELECT_PRESET_LIST = 3;

    private static final int DIALOG_PRESETS_LIST = 4;

    private static final int DIALOG_PRESET_LIST_RENAME = 5;

    private static final int DIALOG_PRESET_LIST_DELETE = 6;

    private static final int DIALOG_PRESET_LIST_AUTO_SET = 7;

    private static final int DIALOG_PICK_FREQUENCY = 8;

    private static final int DIALOG_PRESET_OPTIONS = 10;

    private static final int DIALOG_PRESET_RENAME = 11;

    private static final int DIALOG_CMD_FAILED = 13;

    /* Activity Return ResultIdentifiers */
    private static final int ACTIVITY_RESULT_SETTINGS = 1;

    /* Activity Return ResultIdentifiers */
    private static final int MAX_PRESETS_PER_PAGE = 5;

    /* Station's Audio is Stereo */
    private static final int FMRADIO_UI_STATION_AUDIO_STEREO = 1;

    /* Station's Audio is Mono */
    private static final int FMRADIO_UI_STATION_AUDIO_MONO = 2;

    /*
     * The duration during which the "Sleep: xx:xx" string will be toggling
     */
    private static final int SLEEP_TOGGLE_SECONDS = 60;

    /*
     * The number of Preset Stations to create. The hardware supports a maximum
     * of 12.
     */
    private static final int NUM_AUTO_PRESETS_SEARCH = 12;

    /*
     * Command time out: For asynchonous operations, if no response is received
     * with int this duration, a timeout msg will be displayed.
     */
    private static final int CMD_TIMEOUT_DELAY_MS = 5000;

    private static final int MSG_CMD_TIMEOUT = 101;

    private static final int CMD_NONE = 0;

    private static final int CMD_TUNE = 1;

    private static final int CMD_FMON = 2;

    private static final int CMD_FMOFF = 3;

    private static final int CMD_FMCONFIGURE = 4;

    private static final int CMD_MUTE = 5;

    private static final int CMD_SEEK = 6;

    private static final int CMD_SCAN = 7;

    private static final int CMD_SEEKPI = 8;

    private static final int CMD_SEARCHLIST = 9;

    private static final int CMD_CANCELSEARCH = 10;

    private static final int CMD_SET_POWER_MODE = 11;

    private static final int CMD_SET_AUDIO_MODE = 12;

    private static final int CMD_SET_AUTOAF = 13;

    private static final int CMD_GET_INTERNALANTENNA_MODE = 14;

    private static final int PRESETS_OPTIONS_DELETE = 0;

    private static final int PRESETS_OPTIONS_SEARCHPI = 1;

    private static IFMRadioService mService = null;

    private static FmSharedPreferences mPrefs;

    /* Button Resources */
    private ImageButton mOnOffButton;

    /* Button switch speaker and headset */
    private ImageButton mSpeakerButton;

    /* 5 Preset Buttons */
    private Button[] mPresetButtons = {
            null, null, null, null, null
    };

    /* Middle row in the station info layout */
    private TextView mTuneStationFrequencyTV;

    /* Indicator of frequency */
    private FreqIndicator mFreqIndicator;

    private class SeekBarChangeListener implements SeekBar.OnSeekBarChangeListener{
        private int lowerLimit = 0;

        public void setMin(int min) {
            lowerLimit = min;
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            int stepSize = FmSharedPreferences.getBandStepSize();
            int frequency = ((lowerLimit + progress) / stepSize ) * stepSize;
            // change frequency
            tuneRadio(frequency);
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }

    };

    private SeekBarChangeListener mFrequencyIndicatorChangeListener;

    /* micro-steps tuner for frequency */
    private TunerView mTunerView;
    private TunerView.OnMoveListener mTunerViewMoveListener
                = new TunerView.OnMoveListener() {
        private int mSteps = 0;
        @Override
        public void onMove(TunerView tunerView, int step) {
            if (tunerView.isEnabled() && step != 0) {
                mSteps += step;
                int stepSize = FmSharedPreferences.getBandStepSize();
                if (mSteps > 5) {
                    mFreqIndicator.setProgress(((mFreqIndicator.getProgress() / stepSize) + 1) * stepSize);
                    mSteps = 0;
                } else if (mSteps < -5) {
                    mFreqIndicator.setProgress(((mFreqIndicator.getProgress() / stepSize) - 1) * stepSize);
                    mSteps = 0;
                }
            }
        }
    };

    private double mOutputFreq;

    private int mPresetPageNumber = 0;

    private int mStereo = -1;

    /* Current Status Indicators */
    private static boolean mRecording = false;

    private static boolean mIsScaning = false;

    private static boolean mIsSeeking = false;

    private static boolean mIsSearching = false;

    private static int mScanPty = 0;

    private static PresetStation mTunedStation = new PresetStation("", 102100);

    private PresetStation mPresetButtonStation = null;

    /* Radio Vars */
    final Handler mHandler = new Handler();

    /* Search Progress Dialog */
    private ProgressDialog mProgressDialog = null;

    /* Asynchronous command active */
    private static int mCommandActive = 0;

    /* Command that failed (Sycnhronous or Asynchronous) */
    private static int mCommandFailed = 0;

    private boolean mBluetoothEnabled = false;

    private ProgressDialog mBluetoothStartingDialog;

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                mBluetoothEnabled = state == BluetoothAdapter.STATE_ON;
                if (!mBluetoothEnabled) {
                    // Bluetooth is disabled so we should turn off FM too.
                    if (isFmOn()) {
                        disableRadio();
                    }
                }
            }
        }
    };

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        mPrefs = new FmSharedPreferences(this);
        mPrefs.Load();
        mCommandActive = CMD_NONE;
        mCommandFailed = CMD_NONE;

        Log.d(LOGTAG, "onCreate - Height : " + getWindowManager().getDefaultDisplay().getHeight()
                + " - Width  : " + getWindowManager().getDefaultDisplay().getWidth());

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.fmradio);

        mOnOffButton = (ImageButton) findViewById(R.id.btn_onoff);
        mOnOffButton.setOnClickListener(mTurnOnOffClickListener);

        mSpeakerButton = (ImageButton) findViewById(R.id.btn_speaker);
        mSpeakerButton.setOnClickListener(mSpeakerSwitchClickListener);

        /* 5 Preset Buttons */
        mPresetButtons[0] = (Button) findViewById(R.id.presets_button_1);
        mPresetButtons[1] = (Button) findViewById(R.id.presets_button_2);
        mPresetButtons[2] = (Button) findViewById(R.id.presets_button_3);
        mPresetButtons[3] = (Button) findViewById(R.id.presets_button_4);
        mPresetButtons[4] = (Button) findViewById(R.id.presets_button_5);
        for (int nButton = 0; nButton < MAX_PRESETS_PER_PAGE; nButton++) {
            mPresetButtons[nButton].setOnClickListener(mPresetButtonClickListener);
            mPresetButtons[nButton].setOnLongClickListener(mPresetButtonOnLongClickListener);
        }

        mTuneStationFrequencyTV = (TextView) findViewById(R.id.prog_frequency_tv);
        if (mTuneStationFrequencyTV != null) {
            mTuneStationFrequencyTV.setOnClickListener(mFrequencyViewClickListener);
        }

        mFreqIndicator = (FreqIndicator) findViewById(R.id.freq_indicator_view);
        mFreqIndicator.setMax(FmSharedPreferences.getUpperLimit() - FmSharedPreferences.getLowerLimit());
        mFreqIndicator.setMinFrequency(FmSharedPreferences.getLowerLimit());
        mFrequencyIndicatorChangeListener = new SeekBarChangeListener();
        mFrequencyIndicatorChangeListener.setMin(mFreqIndicator.getMinFrequency());
        mFreqIndicator.setOnSeekBarChangeListener(mFrequencyIndicatorChangeListener);

        mTunerView = (TunerView) findViewById(R.id.fm_tuner_view);
        mTunerView.setOnMoveListener(mTunerViewMoveListener);

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mIntentReceiver, filter);

        enableRadioOnOffUI(false);

        if (false == bindToService(this, osc)) {
            Log.d(LOGTAG, "onCreate: Failed to Start Service");
        } else {
            Log.d(LOGTAG, "onCreate: Start Service completed successfully");
        }
    }

    @Override
    public void onRestart() {
        Log.d(LOGTAG, "FMRadio: onRestart");
        super.onRestart();
    }

    @Override
    public void onStop() {
        Log.d(LOGTAG, "FMRadio: onStop");
        super.onStop();
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(LOGTAG, "FMRadio: onStart");
        try {
            if (mService != null) {
                mService.registerCallbacks(mServiceCallbacks);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        FmSharedPreferences.setTunedFrequency(mTunedStation.getFrequency());
        mPrefs.Save();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(LOGTAG, "FMRadio: onResume");
        mPrefs.Load();
        PresetStation station = FmSharedPreferences.getStationFromFrequency(FmSharedPreferences
                .getTunedFrequency());
        if (station != null) {
            mTunedStation.Copy(station);
        }
        mHandler.post(mUpdateProgramService);
        mHandler.post(mUpdateRadioText);
        updateStationInfoToUI();

        enableRadioOnOffUI();
        setSpeakerUI(FmSharedPreferences.getSpeaker());
    }

    private void setSpeakerUI(boolean on) {
        if (on) {
            mSpeakerButton.setImageResource(R.drawable.button_loudspeaker_on);
        } else {
            mSpeakerButton.setImageResource(R.drawable.button_loudspeaker_off);
        }
    }

    private void setSpeakerFunc(boolean on) {
        if (on) {
            switchToSpeaker();
        } else {
            switchToHeadset();
        }
    }

    private void switchToSpeaker() {
        AudioSystem.setForceUse(AudioSystem.FOR_MEDIA, AudioSystem.FORCE_SPEAKER);
        AudioSystem.setDeviceConnectionState(AudioSystem.DEVICE_OUT_FM, AudioSystem.DEVICE_STATE_UNAVAILABLE, "");
        AudioSystem.setDeviceConnectionState(AudioSystem.DEVICE_OUT_FM, AudioSystem.DEVICE_STATE_AVAILABLE, "");
    }

    private void switchToHeadset() {
        AudioSystem.setForceUse(AudioSystem.FOR_MEDIA, AudioSystem.FORCE_NONE);
        AudioSystem.setDeviceConnectionState(AudioSystem.DEVICE_OUT_FM, AudioSystem.DEVICE_STATE_UNAVAILABLE, "");
        AudioSystem.setDeviceConnectionState(AudioSystem.DEVICE_OUT_FM, AudioSystem.DEVICE_STATE_AVAILABLE, "");
    }

    @Override
    public void onDestroy() {
        endSleepTimer();
        unbindFromService(this);
        mService = null;
        if (mIntentReceiver != null) {
            unregisterReceiver(mIntentReceiver);
            mIntentReceiver = null;
        }
        Log.d(LOGTAG, "onDestroy: unbindFromService completed");
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        super.onCreateOptionsMenu(menu);
        MenuItem item;
//        boolean radioOn = isFmOn();
//        boolean recording = isRecording();
//        boolean sleepActive = isSleepTimerActive();
//        boolean searchActive = isScanActive() || isSeekActive();
//
//        item = menu.add(0, MENU_SCAN_START, 0, R.string.menu_scan_start).setIcon(
//                R.drawable.ic_btn_search);
//        if (item != null) {
//            item.setVisible(!searchActive && radioOn);
//        }
//        item = menu.add(0, MENU_SCAN_STOP, 0, R.string.menu_scan_stop).setIcon(
//                R.drawable.ic_btn_search);
//        if (item != null) {
//            item.setVisible(searchActive && radioOn);
//        }
//
//        if (RECORDING_ENABLE) {
//            item = menu.add(0, MENU_RECORD_START, 0, R.string.menu_record_start).setIcon(
//                    R.drawable.ic_menu_record);
//            if (item != null) {
//                item.setVisible(!recording && radioOn);
//            }
//            item = menu.add(0, MENU_RECORD_STOP, 0, R.string.menu_record_stop).setIcon(
//                    R.drawable.ic_menu_record);
//            if (item != null) {
//                item.setVisible(recording && radioOn);
//            }
//        }
        /* Settings can be active */
        item = menu.add(0, MENU_SETTINGS, 0, R.string.settings_menu).setIcon(
                android.R.drawable.ic_menu_preferences);
//
//        item = menu.add(0, MENU_SLEEP, 0, R.string.menu_sleep).setTitle(R.string.menu_sleep);
//        if (item != null) {
//            item.setVisible(!sleepActive && radioOn);
//        }
//        item = menu.add(0, MENU_SLEEP_CANCEL, 0, R.string.menu_sleep_cancel).setTitle(
//                R.string.menu_sleep_cancel);
//        if (item != null) {
//            item.setVisible(sleepActive && radioOn);
//        }
//
//        if (isWiredHeadsetAvailable()) {
//            item = menu.add(0, MENU_WIRED_HEADSET, 0, R.string.menu_wired_headset).setIcon(
//                    R.drawable.ic_stereo);
//            if (item != null) {
//                item.setCheckable(true);
//                item.setChecked(false);
//                item.setVisible(radioOn);
//            }
//        }
//
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_SETTINGS:
                Intent launchPreferencesIntent = new Intent().setClass(this, Settings.class);
                launchPreferencesIntent.putExtra(Settings.RX_MODE, true);
                startActivityForResult(launchPreferencesIntent, ACTIVITY_RESULT_SETTINGS);
                // startActivity(launchPreferencesIntent);
                return true;

            case MENU_WIRED_HEADSET:
                DebugToasts("Route Audio over headset", Toast.LENGTH_SHORT);
                /* Call the mm interface to route the wired headset. */
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_PICK_FREQUENCY: {
                FmConfig fmConfig = FmSharedPreferences.getFMConfiguration();
                return new FrequencyPickerDialog(this, fmConfig, mTunedStation.getFrequency(),
                        mFrequencyChangeListener);
            }
            case DIALOG_PRESET_OPTIONS: {
                return createPresetOptionsDlg(id);
            }
            case DIALOG_CMD_FAILED: {
                return createCmdFailedDlg(id);
            }
            default:
                break;
        }
        return null;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        super.onPrepareDialog(id, dialog);
        int curListIndex = FmSharedPreferences.getCurrentListIndex();
        PresetList curList = FmSharedPreferences.getStationList(curListIndex);
        switch (id) {
            case DIALOG_PRESET_LIST_RENAME: {
                EditText et = (EditText) dialog.findViewById(R.id.list_edit);
                if (et != null) {
                    et.setText(curList.getName());
                }
                break;
            }
            case DIALOG_SELECT_PRESET_LIST: {
                AlertDialog alertDlg = ((AlertDialog) dialog);
                ListView lv = (ListView) alertDlg.findViewById(R.id.list);
                if (lv != null) {
                    updateSelectPresetListDlg(lv);
                }
                break;
            }
            case DIALOG_PRESETS_LIST: {
                AlertDialog alertDlg = ((AlertDialog) dialog);
                alertDlg.setTitle(curList.getName());
                break;
            }
            case DIALOG_PICK_FREQUENCY: {
                ((FrequencyPickerDialog) dialog).UpdateFrequency(mTunedStation.getFrequency());
                break;
            }
            case DIALOG_PRESET_RENAME: {
                EditText et = (EditText) dialog.findViewById(R.id.list_edit);
                if ((et != null) && (mPresetButtonStation != null)) {
                    et.setText(mPresetButtonStation.getName());
                }
                break;
            }
            case DIALOG_PRESET_OPTIONS: {
                AlertDialog alertDlg = ((AlertDialog) dialog);
                if ((alertDlg != null) && (mPresetButtonStation != null)) {
                    alertDlg.setTitle(mPresetButtonStation.getName());
                }
                break;
            }

            default:
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(LOGTAG, "onActivityResult : requestCode -> " + requestCode);
        Log.d(LOGTAG, "onActivityResult : resultCode -> " + resultCode);
        if (requestCode == ACTIVITY_RESULT_SETTINGS) {
            if (resultCode == RESULT_OK) {
                /* */
                if (data != null) {
                    String action = data.getAction();
                    if (action != null) {
                        if (action.equals(Settings.RESTORE_FACTORY_DEFAULT_ACTION)) {
                            RestoreDefaults();
                            enableRadioOnOffUI();
                        }
                    }
                }
            } // if ACTIVITY_RESULT_SETTINGS
        }// if (resultCode == RESULT_OK)
    }

    /**
     * @return true if a wired headset is connected.
     */
    boolean isWiredHeadsetAvailable() {
        boolean bAvailable = false;
        if (mService != null) {
            try {
                bAvailable = mService.isWiredHeadsetAvailable();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        Log.e(LOGTAG, "isWiredHeadsetAvailable: " + bAvailable);
        return bAvailable;
    }

    /**
     * @return true if a internal antenna is available.
     */
    boolean isAntennaAvailable() {
        boolean bAvailable = false;
        if (mService != null) {
            try {
                bAvailable = mService.isAntennaAvailable();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        Log.e(LOGTAG, "isAntennaAvailable: " + bAvailable);
        return bAvailable;
    }

    private Dialog createPresetOptionsDlg(int id) {
        if (mPresetButtonStation != null) {
            AlertDialog.Builder dlgBuilder = new AlertDialog.Builder(this);
            dlgBuilder.setTitle(mPresetButtonStation.getName());
            ArrayList<String> arrayList = new ArrayList<String>();
            // PRESETS_OPTIONS_DELETE=0
            arrayList.add(getResources().getString(R.string.preset_delete));
            String piString = mPresetButtonStation.getPIString();
            if (!TextUtils.isEmpty(piString)) {
                // PRESETS_OPTIONS_SEARCHPI=1
                arrayList.add(getResources().getString(R.string.preset_search, piString));
            }

            dlgBuilder.setCancelable(true);
            dlgBuilder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    mPresetButtonStation = null;
                    removeDialog(DIALOG_PRESET_OPTIONS);
                }
            });
            String[] items = new String[arrayList.size()];
            arrayList.toArray(items);
            dlgBuilder.setItems(items, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int item) {
                    if (mPresetButtonStation != null) {
                        switch (item) {
                            case PRESETS_OPTIONS_DELETE: {
                                // Delete
                                int curListIndex = FmSharedPreferences.getCurrentListIndex();
                                FmSharedPreferences.removeStation(curListIndex,
                                        mPresetButtonStation);
                                mPresetButtonStation = null;
                                setupPresetLayout();
                                mPrefs.Save();
                                break;
                            }
                            case PRESETS_OPTIONS_SEARCHPI: {
                                // SearchPI
                                String piString = mPresetButtonStation.getPIString();
                                int pi = mPresetButtonStation.getPI();
                                if ((!TextUtils.isEmpty(piString)) && (pi > 0)) {
                                    initiatePISearch(pi);
                                }
                                mPresetButtonStation = null;
                                break;
                            }
                            default: {
                                // Should not happen
                                mPresetButtonStation = null;
                                break;
                            }
                        }// switch item
                    }// if(mPresetButtonStation != null)
                    removeDialog(DIALOG_PRESET_OPTIONS);
                }// onClick
            });
            return dlgBuilder.create();
        }
        return null;
    }

    private void updateSelectPresetListDlg(ListView lv) {
    }

    private Dialog createCmdFailedDlg(int id) {
        AlertDialog.Builder dlgBuilder = new AlertDialog.Builder(this);
        dlgBuilder.setIcon(R.drawable.alert_dialog_icon).setTitle(R.string.fm_command_failed_title);
        dlgBuilder.setMessage(R.string.fm_cmd_failed_msg);

        dlgBuilder.setPositiveButton(R.string.alert_dialog_ok,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        removeDialog(DIALOG_CMD_FAILED);
                        mCommandFailed = CMD_NONE;
                    }
                });

        return (dlgBuilder.create());
    }

    private void RestoreDefaults() {
        FmSharedPreferences.SetDefaults();
        mPrefs.Save();
    }

    private View.OnClickListener mFrequencyViewClickListener = new View.OnClickListener() {
        public void onClick(View v) {
            showDialog(DIALOG_PICK_FREQUENCY);
        }
    };

    private View.OnClickListener mPresetListClickListener = new View.OnClickListener() {
        public void onClick(View v) {
            showDialog(DIALOG_SELECT_PRESET_LIST);
        }
    };

    private View.OnLongClickListener mPresetListButtonOnLongClickListener = new View.OnLongClickListener() {
        public boolean onLongClick(View view) {
            showDialog(DIALOG_PRESETS_LIST);
            return true;
        }
    };

    private View.OnClickListener mPresetButtonClickListener = new View.OnClickListener() {
        public void onClick(View view) {
            PresetStation station = (PresetStation) view.getTag();
            if (station != null) {
                Log.d(LOGTAG, "station - " + station.getName() + " (" + station.getFrequency()
                        + ")");
                mFreqIndicator.setFrequency(station.getFrequency());
            } else {
                addToPresets();
            }
        }
    };

    private View.OnLongClickListener mPresetButtonOnLongClickListener = new View.OnLongClickListener() {
        public boolean onLongClick(View view) {
            PresetStation station = (PresetStation) view.getTag();
            mPresetButtonStation = station;
            if (station != null) {
                showDialog(DIALOG_PRESET_OPTIONS);
            }
            return true;
        }
    };

    final FrequencyPickerDialog.OnFrequencySetListener mFrequencyChangeListener = new FrequencyPickerDialog.OnFrequencySetListener() {
        public void onFrequencySet(FrequencyPicker view, int frequency) {
            Log.d(LOGTAG, "mFrequencyChangeListener: onFrequencyChanged to : " + frequency);
            mFreqIndicator.setFrequency(frequency);
        }
    };

    private View.OnClickListener mTurnOnOffClickListener = new View.OnClickListener() {
        public void onClick(View v) {

            if (isFmOn()) {
                disableRadio();
            } else {
                asyncCheckAndEnableRadio();
            }
            setTurnOnOffButtonImage();
        }
    };

    private View.OnClickListener mSpeakerSwitchClickListener = new View.OnClickListener() {
        public void onClick(View v) {
            boolean speakerState = !FmSharedPreferences.getSpeaker();
            setSpeakerUI(speakerState);
            setSpeakerFunc(speakerState);
            FmSharedPreferences.setSpeaker(speakerState);
        }
    };

    private void setTurnOnOffButtonImage() {
        if (isFmOn() == true) {
            mOnOffButton.setImageResource(R.drawable.button_power_on);
        } else {
            /* Find a icon to indicate off */
            mOnOffButton.setImageResource(R.drawable.button_power_off);
        }
    }

    private void asyncCheckAndEnableRadio() {
        mBluetoothEnabled = BluetoothAdapter.getDefaultAdapter().isEnabled();
        if (mBluetoothEnabled) {
            enableRadio();
        } else {
            BluetoothAdapter.getDefaultAdapter().enable();

            AsyncTask<Void, Void, Boolean> task = new AsyncTask<Void, Void, Boolean>() {
                @Override
                protected void onPreExecute() {
                    if (mBluetoothStartingDialog != null) {
                        mBluetoothStartingDialog.dismiss();
                        mBluetoothStartingDialog = null;
                    }
                    mBluetoothStartingDialog = ProgressDialog.show(
                        FMRadio.this,
                        null,
                        getString(R.string.init_FM),
                        true,
                        false);
                    super.onPreExecute();
                }

                @Override
                protected Boolean doInBackground(Void... params) {
                    // 同步30秒
                    int n = 0;
                    try {
                        while (!mBluetoothEnabled && n < 30) {
                            Thread.sleep(1000);
                            ++n;
                        }
                    } catch (InterruptedException e) {
                    } finally {
                        return true;
                    }
                }

                @Override
                protected void onPostExecute(Boolean result) {
                    if (mBluetoothStartingDialog != null) {
                        mBluetoothStartingDialog.dismiss();
                        mBluetoothStartingDialog = null;
                    }
                    if (mBluetoothEnabled){
                        enableRadio();
                    } else {
                        Toast toast = Toast.makeText(FMRadio.this,
                                getString(R.string.need_bluetooth),
                                Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.TOP | Gravity.CENTER, 0, 240);
                        toast.show();
                    }
                    super.onPostExecute(result);
                }
            };
            task.execute();
        }
    }

    private void enableRadio() {
        mIsScaning = false;
        mIsSeeking = false;
        mIsSearching = false;
        boolean bStatus = false;

        if (mService != null) {
            try {
                // reset volume to avoid a bug that volume will be MAX
                int vol = AudioSystem.getStreamVolumeIndex(AudioSystem.STREAM_FM);
                AudioSystem.setStreamVolumeIndex(AudioSystem.STREAM_FM, vol);

                if (!isPhoneInCall()) {
                    mService.unMute();
                    bStatus = mService.fmOn();
                }

                if (bStatus) {
                    if (isAntennaAvailable()) {
                        mFreqIndicator.setFrequency(FmSharedPreferences.getTunedFrequency());
                        enableRadioOnOffUI();
                    } else {
                        Toast toast =Toast.makeText(this,
                                getString(R.string.need_headset_for_antenna),
                                Toast.LENGTH_LONG);
                        toast.setGravity(Gravity.TOP | Gravity.CENTER, 0, 240);
                        toast.show();
                        disableRadio();
                    }
                } else {
                    Log.e(LOGTAG, " mService.fmOn failed");
                    mCommandFailed = CMD_FMON;
                    showDialog(DIALOG_CMD_FAILED);
                }
            } catch (RemoteException e) {
                Log.e(LOGTAG, "RemoteException in enableRadio", e);
            }
        }
    }

    private void disableRadio() {
        boolean bStatus = false;
        cancelSearch();
        endSleepTimer();
        if (mService != null) {
            try {
                FmSharedPreferences.setTunedFrequency(mFreqIndicator.getFrequency());
                mService.mute();
                bStatus = mService.fmOff();
                enableRadioOnOffUI();
                if (bStatus == false) {
                    mCommandFailed = CMD_FMOFF;
                    Log.e(LOGTAG, " mService.fmOff failed");
                    // showDialog(DIALOG_CMD_FAILED);
                } else {
                    /* shut down force use */
                    AudioSystem.setForceUse(AudioSystem.FOR_MEDIA, AudioSystem.FORCE_NONE);
                }
                
                if (BluetoothAdapter.getDefaultAdapter().isEnabled()) {
                    if (mPrefs.getAlwaysDisableBt())
                        BluetoothAdapter.getDefaultAdapter().disable();
                    else if (mPrefs.getPromptDisableBt()) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setMessage(R.string.prompt_disable_bt)
                            .setPositiveButton(R.string.prompt_yes, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    BluetoothAdapter.getDefaultAdapter().disable();
                                }})
                            .setNegativeButton(R.string.prompt_no, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    // Do nothing
                                }})
                            .show();
                }
        }
            } catch (RemoteException e) {
                Log.e(LOGTAG, "RemoteException in disableRadio", e);
            }
        }
    }

    public static void fmConfigure() {
        boolean bStatus = false;
        if (mService != null) {
            try {
                bStatus = mService.fmReconfigure();
                if (bStatus == false) {
                    mCommandFailed = CMD_FMCONFIGURE;
                    Log.e(LOGTAG, " mService.fmReconfigure failed");
                    // showDialog(DIALOG_CMD_FAILED);
                }
            } catch (RemoteException e) {
                Log.e(LOGTAG, "RemoteException in fmConfigure", e);
            }
        }
    }

    public static void fmAutoAFSwitch() {
        boolean bStatus = false;
        if (mService != null) {
            try {
                bStatus = mService.enableAutoAF(FmSharedPreferences.getAutoAFSwitch());
                if (bStatus == false) {
                    mCommandFailed = CMD_SET_AUTOAF;
                    Log.e(LOGTAG, " mService.enableAutoAF failed");
                    // showDialog(DIALOG_CMD_FAILED);
                }
            } catch (RemoteException e) {
                Log.e(LOGTAG, "RemoteException in fmAutoAFSwitch", e);
            }
        }
    }

    public static void fmAudioOutputMode() {
        boolean bStatus = false;
        if (mService != null) {
            try {
                bStatus = mService.enableStereo(FmSharedPreferences.getAudioOutputMode());
                if (bStatus == false) {
                    mCommandFailed = CMD_SET_AUDIO_MODE;
                    Log.e(LOGTAG, " mService.enableStereo failed");
                    // showDialog(DIALOG_CMD_FAILED);
                }
            } catch (RemoteException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    private boolean startRecord() {
        mRecording = true;
        DebugToasts("Started Recording", Toast.LENGTH_SHORT);
        return mRecording;
    }

    private boolean isRecording() {
        return mRecording;
    }

    private boolean stopRecord() {
        mRecording = false;
        DebugToasts("Stopped Recording", Toast.LENGTH_SHORT);
        return mRecording;
    }

    private void addToPresets() {
        int currentList = FmSharedPreferences.getCurrentListIndex();
        String name = FrequencyPicker.formatFrequencyString(mTunedStation.getFrequency());
        FmSharedPreferences.addStation(name, mTunedStation.getFrequency(),
                currentList);
        setupPresetLayout();
    }

    private void enableRadioOnOffUI() {
        boolean bEnable = isFmOn();
        /* Disable if no antenna/headset is available */
        if (!isAntennaAvailable()) {
            bEnable = false;
        }
        enableRadioOnOffUI(bEnable);
    }

    private void enableRadioOnOffUI(boolean bEnable) {
        mTuneStationFrequencyTV.setVisibility(((bEnable == true) ? View.VISIBLE : View.INVISIBLE));

        setTurnOnOffButtonImage();

        for (int nButton = 0; nButton < MAX_PRESETS_PER_PAGE; nButton++) {
            mPresetButtons[nButton].setEnabled(bEnable);
        }

        mSpeakerButton.setEnabled(bEnable);
        mFreqIndicator.setEnabled(bEnable);
        mTunerView.setEnabled(bEnable);
    }

    private void updateSearchProgress() {
    }


    private void setupPresetLayout() {
        int numStations = FmSharedPreferences.getListStationCount();
        int addedStations = 0;

        /*
         * Validate mPresetPageNumber (Preset Page Number)
         */
        if (mPresetPageNumber > ((numStations) / MAX_PRESETS_PER_PAGE)) {
            mPresetPageNumber = 0;
        }

        /*
         * For every station, save the station as a tag and update the display
         * on the preset Button.
         */
        for (int buttonIndex = 0; (buttonIndex < MAX_PRESETS_PER_PAGE); buttonIndex++) {
            if (mPresetButtons[buttonIndex] != null) {
                int stationIdex = (mPresetPageNumber * MAX_PRESETS_PER_PAGE) + buttonIndex;
                PresetStation station = FmSharedPreferences.getStationInList(stationIdex);
                String display = "+";
                if (station != null) {
                    display = station.getName();
                    mPresetButtons[buttonIndex].setText(display);
                    mPresetButtons[buttonIndex].setTag(station);
                    addedStations++;
                } else {
                    mPresetButtons[buttonIndex].setText(display);
                    mPresetButtons[buttonIndex].setTag(station);
                }
            }
        }

    }

    private void updateStationInfoToUI() {
        mTuneStationFrequencyTV.setText(FrequencyPicker.formatFrequencyString(mTunedStation.getFrequency()));
        FmSharedPreferences.setTunedFrequency(mTunedStation.getFrequency());
        setupPresetLayout();
    }

    private boolean isFmOn() {
        boolean bOn = false;
        if (mService != null) {
            try {
                bOn = mService.isFmOn();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return (bOn);
    }

    private boolean isMuted() {
        boolean bMuted = false;
        if (mService != null) {
            try {
                bMuted = mService.isMuted();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return (bMuted);
    }

    private boolean isPhoneInCall() {
        int state = TelephonyManager.getDefault().getCallState();
        if (state == TelephonyManager.CALL_STATE_RINGING
          || state == TelephonyManager.CALL_STATE_OFFHOOK) {
            return true;
        } else {
            return false;
        }
    }

    private boolean isScanActive() {
        return (mIsScaning);
    }

    private boolean isSeekActive() {
        return (mIsSeeking);
    }

    private boolean isSearchActive() {
        return (mIsSearching);
    }

    public static PresetStation getCurrentTunedStation() {
        return mTunedStation;
    }

    private void SeekPreviousStation() {
        Log.d(LOGTAG, "SeekPreviousStation");
        if (mService != null) {
            try {
                if (!isSeekActive()) {
                    mIsSeeking = mService.seek(false);
                    if (mIsSeeking == false) {
                        mCommandFailed = CMD_SEEK;
                        Log.e(LOGTAG, " mService.seek failed");
                        showDialog(DIALOG_CMD_FAILED);
                    }

                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        updateSearchProgress();
    }

    private void SeekNextStation() {
        Log.d(LOGTAG, "SeekNextStation");
        if (mService != null) {
            try {
                if (!isSeekActive()) {
                    mIsSeeking = mService.seek(true);
                    if (mIsSeeking == false) {
                        mCommandFailed = CMD_SEEK;
                        Log.e(LOGTAG, " mService.seek failed");
                        showDialog(DIALOG_CMD_FAILED);
                    }
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        updateSearchProgress();
    }

    /** Scan related */
    private void initiateSearch(int pty) {
        synchronized (this) {
            mIsScaning = true;
            if (mService != null) {
                try {
                    mIsScaning = mService.scan(pty);
                    if (mIsScaning == false) {
                        mCommandFailed = CMD_SCAN;
                        Log.e(LOGTAG, " mService.scan failed");
                        showDialog(DIALOG_CMD_FAILED);
                    } else {
                        mScanPty = pty;
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                updateSearchProgress();
            }
        }
    }

    /** SEEK Station with the matching PI */
    private void initiatePISearch(int pi) {
        Log.d(LOGTAG, "initiatePISearch");
        if (mService != null) {
            try {
                if (!isSeekActive()) {
                    mIsSeeking = mService.seekPI(pi);
                    if (mIsSeeking == false) {
                        mCommandFailed = CMD_SEEKPI;
                        Log.e(LOGTAG, " mService.seekPI failed");
                        showDialog(DIALOG_CMD_FAILED);
                    }
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        updateSearchProgress();
    }

    private void cancelSearch() {
        synchronized (this) {
            if (mService != null) {
                try {
                    if ((mIsScaning == true) || (mIsSeeking == true) || (mIsSearching == true)) {
                        if (true == mService.cancelSearch()) {
                            mIsScaning = false;
                            mIsSeeking = false;
                            mIsSearching = false;
                        } else {
                            mCommandFailed = CMD_CANCELSEARCH;
                            Log.e(LOGTAG, " mService.cancelSearch failed");
                            showDialog(DIALOG_CMD_FAILED);
                        }
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
        updateSearchProgress();
    }

    /** get Strongest Stations */
    private void initiateSearchList() {
        synchronized (this) {
            mIsSearching = false;
            if (mService != null) {
                try {
                    mIsSearching = mService.searchStrongStationList(NUM_AUTO_PRESETS_SEARCH);
                    if (mIsSearching == false) {
                        mCommandFailed = CMD_SEARCHLIST;
                        Log.e(LOGTAG, " mService.searchStrongStationList failed");
                        showDialog(DIALOG_CMD_FAILED);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                updateSearchProgress();
            }
        }
    }

    private static final int UPDATE_PROGRESS_DLG = 1;

    private static final int END_PROGRESS_DLG = 2;

    private static final int TIMEOUT_PROGRESS_DLG = 3;

    private static final int SHOWBUSY_TIMEOUT = 300000;

    /** Sleep Handling: After the timer expires, the app needs to shut down */
    private static final int SLEEPTIMER_EXPIRED = 0x1001;

    private static final int SLEEPTIMER_UPDATE = 0x1002;

    private Thread mSleepUpdateHandlerThread = null;

    /*
     * Phone time when the App has to be shut down, calculated based on what the
     * user configured
     */
    private long mSleepAtPhoneTime = 0;

    private boolean mSleepCancelled = false;

    private void initiateSleepTimer(long seconds) {
        mSleepAtPhoneTime = (SystemClock.elapsedRealtime()) + (seconds * 1000);
        Log.d(LOGTAG, "Sleep in seconds : " + seconds);

        mSleepCancelled = false;
        if (mSleepUpdateHandlerThread == null) {
            mSleepUpdateHandlerThread = new Thread(null, doSleepProcessing, "SleepUpdateThread");
        }
        /* Launch he dummy thread to simulate the transfer progress */
        Log.d(LOGTAG, "Thread State: " + mSleepUpdateHandlerThread.getState());
        if (mSleepUpdateHandlerThread.getState() == Thread.State.TERMINATED) {
            mSleepUpdateHandlerThread = new Thread(null, doSleepProcessing, "SleepUpdateThread");
        }
        /* If the thread state is "new" then the thread has not yet started */
        if (mSleepUpdateHandlerThread.getState() == Thread.State.NEW) {
            mSleepUpdateHandlerThread.start();
        }
    }

    private void endSleepTimer() {
        mSleepAtPhoneTime = 0;
        mSleepCancelled = true;
        // Log.d(LOGTAG, "endSleepTimer");
    }

    private boolean hasSleepTimerExpired() {
        boolean expired = true;
        if (isSleepTimerActive()) {
            long timeNow = ((SystemClock.elapsedRealtime()));
            // Log.d(LOGTAG, "hasSleepTimerExpired - " + mSleepAtPhoneTime +
            // " now: "+ timeNow);
            if (timeNow < mSleepAtPhoneTime) {
                expired = false;
            }
        }
        return expired;
    }

    private boolean isSleepTimerActive() {
        boolean active = false;
        if (mSleepAtPhoneTime > 0) {
            active = true;
        }
        return active;
    }

    private void updateExpiredSleepTime() {
    }

    private Handler mUIUpdateHandlerHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SLEEPTIMER_EXPIRED: {
                    mSleepAtPhoneTime = 0;
                    if (mSleepCancelled != true) {
                        // Log.d(LOGTAG,
                        // "mUIUpdateHandlerHandler - SLEEPTIMER_EXPIRED");
                        DebugToasts("Turning Off FM Radio", Toast.LENGTH_SHORT);
                        disableRadio();
                    }
                    return;
                }
                case SLEEPTIMER_UPDATE: {
                    // Log.d(LOGTAG,
                    // "mUIUpdateHandlerHandler - SLEEPTIMER_UPDATE");
                    updateExpiredSleepTime();
                    break;
                }
                default:
                    break;
            }
            super.handleMessage(msg);
        }
    };

    /* Thread processing */
    private Runnable doSleepProcessing = new Runnable() {
        public void run() {
            boolean sleepTimerExpired = hasSleepTimerExpired();
            while (sleepTimerExpired == false) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                Message statusUpdate = new Message();
                statusUpdate.what = SLEEPTIMER_UPDATE;
                mUIUpdateHandlerHandler.sendMessage(statusUpdate);
                sleepTimerExpired = hasSleepTimerExpired();
            }
            Message finished = new Message();
            finished.what = SLEEPTIMER_EXPIRED;
            mUIUpdateHandlerHandler.sendMessage(finished);
        }
    };

    private static StringBuilder sFormatBuilder = new StringBuilder();

    private static Formatter sFormatter = new Formatter(sFormatBuilder, Locale.getDefault());

    private static final Object[] sTimeArgs = new Object[5];

    private String makeTimeString(long secs) {
        String durationformat = getString(R.string.durationformat);

        /*
         * Provide multiple arguments so the format can be changed easily by
         * modifying the xml.
         */
        sFormatBuilder.setLength(0);

        final Object[] timeArgs = sTimeArgs;
        timeArgs[0] = secs / 3600;
        timeArgs[1] = secs / 60;
        timeArgs[2] = (secs / 60) % 60;
        timeArgs[3] = secs;
        timeArgs[4] = secs % 60;

        return sFormatter.format(durationformat, timeArgs).toString();
    }

    private void tuneRadio(int frequency) {
        if ((mService != null)) {
            boolean bStatus = false;
            try {
                mTunedStation.setName("");
                mTunedStation.setPI(0);
                mTunedStation.setPty(0);
                mTunedStation.setFrequency(frequency);
                updateStationInfoToUI();
                bStatus = mService.tune(frequency);
                if (bStatus) {
                    mCommandActive = CMD_TUNE;
                } else {
                    mCommandFailed = CMD_TUNE;
                    Log.e(LOGTAG, " mService.tune failed");
                    showDialog(DIALOG_CMD_FAILED);
                }
            } catch (RemoteException e) {
                Log.e(LOGTAG, "RemoteException in tuneRadio", e);
            }
        }
    }

    private void resetFMStationInfoUI() {
        mTunedStation.setFrequency(FmSharedPreferences.getTunedFrequency());
        mTunedStation.setName("");
        mTunedStation.setPI(0);
        mTunedStation.setRDSSupported(false);
        // mTunedStation.setPI(20942);
        mTunedStation.setPty(0);
        updateStationInfoToUI();
    }

    final Runnable mRadioEnabled = new Runnable() {
        public void run() {
            /* Update UI to FM On State */
            enableRadioOnOffUI(true);
            /* Tune to the last tuned frequency */
            mFreqIndicator.setFrequency(FmSharedPreferences.getTunedFrequency());
        }
    };

    final Runnable mRadioDisabled = new Runnable() {
        public void run() {
            /* shut down force use */
            AudioSystem.setForceUse(AudioSystem.FOR_MEDIA, AudioSystem.FORCE_NONE);
            /* Update UI to FM Off State */
            enableRadioOnOffUI(false);
        }
    };

    final Runnable mUpdateStationInfo = new Runnable() {
        public void run() {
            PresetStation station = FmSharedPreferences.getStationFromFrequency(FmSharedPreferences
                    .getTunedFrequency());
            if (station != null) {
                mTunedStation.Copy(station);
            }

            updateSearchProgress();
            resetFMStationInfoUI();
        }
    };

    final Runnable mSearchComplete = new Runnable() {
        public void run() {
            Log.d(LOGTAG, "mSearchComplete: ");
            mScanPty = 0;
            mIsScaning = false;
            mIsSeeking = false;
            mIsSearching = false;
            updateSearchProgress();
            resetFMStationInfoUI();
        }
    };

    final Runnable mSearchListComplete = new Runnable() {
        public void run() {
            Log.d(LOGTAG, "mSearchListComplete: ");
            mIsSearching = false;

            /* Now get the list */
            if (mService != null) {
                try {
                    int[] searchList = mService.getSearchList();
                    if (searchList != null) {
                        /* Add the stations into the preset list */
                        int currentList = FmSharedPreferences.getCurrentListIndex();
                        for (int station = 0; (station < searchList.length)
                                && (station < NUM_AUTO_PRESETS_SEARCH); station++) {
                            int frequency = searchList[station];
                            Log.d(LOGTAG, "mSearchListComplete: [" + station + "] = " + frequency);
                            if ((frequency <= FmSharedPreferences.getUpperLimit())
                                    && (frequency >= FmSharedPreferences.getLowerLimit())) {
                                FmSharedPreferences
                                        .addStation("", searchList[station], currentList);
                            }

                            if (frequency == 0) {
                                break;
                            }
                        }
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            updateSearchProgress();
            resetFMStationInfoUI();
            setupPresetLayout();
        }
    };

    final Runnable mOnStereo = new Runnable() {
        public void run() {
            // do nothing, we can show stereo icon later here.
        }
    };

    final Runnable mUpdateRadioText = new Runnable() {
        public void run() {
            String str = "";
            if (mService != null) {
                try {
                    /* Get PTY and PI and update the display */
                    int tempInt = mService.getProgramType();
                    /* Save PTY */
                    mTunedStation.setPty(tempInt);
                    tempInt = mService.getProgramID();
                    if (tempInt != 0) {
                        mTunedStation.setPI(tempInt);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    /* Create runnable for posting */
    final Runnable mUpdateProgramService = new Runnable() {
        public void run() {
            String str = "";
            if (mService != null) {
                try {
                    /* Get PTY and PI and update the display */
                    int tempInt = mService.getProgramType();
                    /* Save PTY */
                    mTunedStation.setPty(tempInt);

                    tempInt = mService.getProgramID();
                    /* Save the program ID */
                    if (tempInt != 0) {
                        mTunedStation.setPI(tempInt);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    private void DebugToasts(String str, int duration) {
        Log.d(LOGTAG, "Debug:" + str);
    }

    /**
     * This Handler will scroll the text view. On startScroll, the scrolling
     * starts after SCROLLER_START_DELAY_MS The Text View is scrolled left one
     * character after every SCROLLER_UPDATE_DELAY_MS When the entire text is
     * scrolled, the scrolling will restart after SCROLLER_RESTART_DELAY_MS
     */
    private static final class ScrollerText extends Handler {

        private static final byte SCROLLER_STOPPED = 0x51;

        private static final byte SCROLLER_STARTING = 0x52;

        private static final byte SCROLLER_RUNNING = 0x53;

        private static final int SCROLLER_MSG_START = 0xF1;

        private static final int SCROLLER_MSG_TICK = 0xF2;

        private static final int SCROLLER_MSG_RESTART = 0xF3;

        private static final int SCROLLER_START_DELAY_MS = 1000;

        private static final int SCROLLER_RESTART_DELAY_MS = 3000;

        private static final int SCROLLER_UPDATE_DELAY_MS = 200;

        private final WeakReference<TextView> mView;

        private byte mStatus = SCROLLER_STOPPED;

        String mOriginalString;

        int mStringlength = 0;

        int mIteration = 0;

        ScrollerText(TextView v) {
            mView = new WeakReference<TextView>(v);
        }

        /**
         * Scrolling Message Handler
         */
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SCROLLER_MSG_START:
                    mStatus = SCROLLER_RUNNING;
                    updateText();
                    break;
                case SCROLLER_MSG_TICK:
                    updateText();
                    break;
                case SCROLLER_MSG_RESTART:
                    if (mStatus == SCROLLER_RUNNING) {
                        startScroll();
                    }
                    break;
            }
        }

        /**
         * Moves the text left by one character and posts a delayed message for
         * next update after SCROLLER_UPDATE_DELAY_MS. If the entire string is
         * scrolled, then it displays the entire string and waits for
         * SCROLLER_RESTART_DELAY_MS for scrolling restart
         */
        void updateText() {
            if (mStatus != SCROLLER_RUNNING) {
                return;
            }

            removeMessages(SCROLLER_MSG_TICK);

            final TextView textView = mView.get();
            if (textView != null) {
                String szStr2 = "";
                if (mStringlength > 0) {
                    mIteration++;
                    if (mIteration >= mStringlength) {
                        mIteration = 0;
                        sendEmptyMessageDelayed(SCROLLER_MSG_RESTART, SCROLLER_RESTART_DELAY_MS);
                    } else {
                        sendEmptyMessageDelayed(SCROLLER_MSG_TICK, SCROLLER_UPDATE_DELAY_MS);
                    }
                    // String szStr1 = mOriginalString.substring(0, mTick);
                    szStr2 = mOriginalString.substring(mIteration);
                }
                // textView.setText(szStr2+"     "+szStr1);
                textView.setText(szStr2);
            }
        }

        /**
         * Stops the scrolling The textView will be set to the original string.
         */
        void stopScroll() {
            mStatus = SCROLLER_STOPPED;
            removeMessages(SCROLLER_MSG_TICK);
            removeMessages(SCROLLER_MSG_RESTART);
            removeMessages(SCROLLER_MSG_START);
            resetScroll();
        }

        /**
         * Resets the scroll to display the original string.
         */
        private void resetScroll() {
            mIteration = 0;
            final TextView textView = mView.get();
            if (textView != null) {
                textView.setText(mOriginalString);
            }
        }

        /**
         * Starts the Scrolling of the TextView after a delay of
         * SCROLLER_START_DELAY_MS Starts only if Length > 0
         */
        void startScroll() {
            final TextView textView = mView.get();
            if (textView != null) {
                mOriginalString = (String) textView.getText();
                mStringlength = mOriginalString.length();
                if (mStringlength > 0) {
                    mStatus = SCROLLER_STARTING;
                    sendEmptyMessageDelayed(SCROLLER_MSG_START, SCROLLER_START_DELAY_MS);
                }
            }
        }
    }

    public static IFMRadioService sService = null;

    private static HashMap<Context, ServiceBinder> sConnectionMap = new HashMap<Context, ServiceBinder>();

    public static boolean bindToService(Context context) {
        Log.e(LOGTAG, "bindToService: Context");
        return bindToService(context, null);
    }

    public static boolean bindToService(Context context, ServiceConnection callback) {
        Log.e(LOGTAG, "bindToService: Context with serviceconnection callback");
        context.startService(new Intent(context, FMRadioService.class));
        ServiceBinder sb = new ServiceBinder(callback);
        sConnectionMap.put(context, sb);
        return context.bindService((new Intent()).setClass(context, FMRadioService.class), sb, 0);
    }

    public static void unbindFromService(Context context) {
        ServiceBinder sb = (ServiceBinder) sConnectionMap.remove(context);
        Log.e(LOGTAG, "unbindFromService: Context");
        if (sb == null) {
            Log.e(LOGTAG, "Trying to unbind for unknown Context");
            return;
        }
        context.unbindService(sb);
        if (sConnectionMap.isEmpty()) {
            // presumably there is nobody interested in the service at this
            // point,
            // so don't hang on to the ServiceConnection
            sService = null;
        }
    }

    private static class ServiceBinder implements ServiceConnection {
        ServiceConnection mCallback;

        ServiceBinder(ServiceConnection callback) {
            mCallback = callback;
        }

        public void onServiceConnected(ComponentName className, android.os.IBinder service) {
            sService = IFMRadioService.Stub.asInterface(service);
            if (mCallback != null) {
                Log.i(LOGTAG, "onServiceConnected: mCallback");
                mCallback.onServiceConnected(className, service);
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            if (mCallback != null) {
                mCallback.onServiceDisconnected(className);
            }
            sService = null;
        }
    }

    private ServiceConnection osc = new ServiceConnection() {
        public void onServiceConnected(ComponentName classname, IBinder obj) {
            mService = IFMRadioService.Stub.asInterface(obj);
            Log.e(LOGTAG, "ServiceConnection: onServiceConnected: ");
            if (mService != null) {
                try {
                    mService.registerCallbacks(mServiceCallbacks);

                    asyncCheckAndEnableRadio();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
                return;
            } else {
                Log.e(LOGTAG, "IFMRadioService onServiceConnected failed");
            }
            if (getIntent().getData() == null) {
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setClass(FMRadio.this, FMRadio.class);
                startActivity(intent);
            }
            finish();
        }

        public void onServiceDisconnected(ComponentName classname) {
        }
    };

    private IFMRadioServiceCallbacks.Stub mServiceCallbacks = new IFMRadioServiceCallbacks.Stub() {
        public void onEnabled() {
            Log.d(LOGTAG, "mServiceCallbacks.onEnabled :");
            mHandler.post(mRadioEnabled);
        }

        public void onDisabled() {
            Log.d(LOGTAG, "mServiceCallbacks.onDisabled :");
            mHandler.post(mRadioDisabled);
        }

        public void onTuneStatusChanged() {
            Log.d(LOGTAG, "mServiceCallbacks.onTuneStatusChanged :");
            mHandler.post(mUpdateStationInfo);
        }

        public void onProgramServiceChanged() {
            Log.d(LOGTAG, "mServiceCallbacks.onProgramServiceChanged :");
            mHandler.post(mUpdateProgramService);
        }

        public void onRadioTextChanged() {
            Log.d(LOGTAG, "mServiceCallbacks.onRadioTextChanged :");
            mHandler.post(mUpdateRadioText);
        }

        public void onAlternateFrequencyChanged() {
            Log.d(LOGTAG, "mServiceCallbacks.onAlternateFrequencyChanged :");
        }

        public void onSignalStrengthChanged() {
            Log.d(LOGTAG, "mServiceCallbacks.onSignalStrengthChanged :");
        }

        public void onSearchComplete() {
            Log.d(LOGTAG, "mServiceCallbacks.onSearchComplete :");
            mHandler.post(mSearchComplete);
        }

        public void onSearchListComplete() {
            Log.d(LOGTAG, "mServiceCallbacks.onSearchListComplete :");
            mHandler.post(mSearchListComplete);
        }

        public void onMute(boolean bMuted) {
            // do nothing when FM is muted
        }

        public void onAudioUpdate(boolean bStereo) {
            if ((bStereo) && (FmSharedPreferences.getAudioOutputMode())) {
                mStereo = FMRADIO_UI_STATION_AUDIO_STEREO;
            } else {
                mStereo = FMRADIO_UI_STATION_AUDIO_MONO;

            }
            Log.d(LOGTAG, "mServiceCallbacks.onAudioUpdate :" + mStereo);
            mHandler.post(mOnStereo);
        }

        public void onStationRDSSupported(boolean bRDSSupported) {
            Log.d(LOGTAG, "mServiceCallbacks.onStationRDSSupported :" + bRDSSupported);
            /*
             * Depending on the signal strength etc, RDS Lock Sync/Supported may
             * toggle, Since if a station Supports RDS, it will not change its
             * support intermittently just save the status and ignore any
             * "unsupported" state.
             */
            if (bRDSSupported) {
                mTunedStation.setRDSSupported(true);
            }
        }
    };
}
