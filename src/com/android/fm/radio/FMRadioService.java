
package com.android.fm.radio;

import com.android.fm.R;
import com.android.fm.utils.FrequencyPicker;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.fmradio.FmConfig;
import android.hardware.fmradio.FmReceiver;
import android.hardware.fmradio.FmRxEvCallbacksAdaptor;
import android.hardware.fmradio.FmRxRdsData;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.AudioSystem;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.PowerManager.WakeLock;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.RemoteViews;

import java.lang.ref.WeakReference;

/**
 * Provides "background" FM Radio (that uses the hardware) capabilities,
 * allowing the user to switch between activities without stopping playback.
 */
public class FMRadioService extends Service {

    public static final String SERVICECMD = "com.android.fm.radio.fmservicecmd";
    public static final String CMDNAME = "command";
    public static final String CMDTOGGLEPAUSE = "togglepause";
    public static final String CMDNEXT = "next";
    public static final String CMDPREVIOUS = "previous";

    /**
     * Static translation for audio output going through a wired headset
     */
    public static final int RADIO_AUDIO_DEVICE_WIRED_HEADSET = 0;

    /**
     * Static translation for audio output going through speaker phone
     */
    public static final int RADIO_AUDIO_DEVICE_SPEAKER_PHONE = 1;

    /**
     * Points to the device node for the FM radio
     */
    private static final String FMRADIO_DEVICE_FD_STRING = "/dev/radio0";

    /**
     * ID identifying this service when launched in the foreground
     */
    private static final int FMRADIOSERVICE_STATUS = 101;

    /**
     * Code representing a msg in the notification bar
     */
    private static final int UPDATE_STAT_NOTIFICATION = 1001;

    /**
     * App name used in log messages
     */
    private static final String LOGTAG = "FMService";

    private AudioManager mAudioManager;

    /**
     * Manipulates the FM radio hardware
     */
    private FmReceiver mReceiver;

    /**
     * Receives Headset related Intents
     */
    private BroadcastReceiver mHeadsetReceiver = null;

    /**
     * Receives Screen-On/Off related Intents
     */
    private BroadcastReceiver mScreenOnOffReceiver = null;

    /**
     * Interface containing callback methods
     */
    private IFMRadioServiceCallbacks mCallbacks;

    /**
     * Handle to FM shared preferences
     */
    private static FmSharedPreferences mPrefs;

    /**
     * Defines whether a headset is currently plugged in
     */
    private boolean mHeadsetPlugged = false;

    /**
     * Defines whether an internal FM antenna is available
     */
    private boolean mInternalAntennaAvailable = false;

    /**
     * Allows access to keep the device awake
     */
    private WakeLock mWakeLock;

    /**
     * The service ID for the current running instance
     */
    private int mServiceStartId = -1;

    /**
     * Defines whether an instance of this service is currently running
     */
    private boolean mServiceInUse = false;

    /**
     * Defines if the audio is currently muted
     */
    private boolean mMuted = false;

    /**
     * Defines if the audio should be resumed after a call is completed
     */
    private boolean mResumeAfterCall = false;

    /**
     * Defines if the FM radio is currently enabled
     */
    private boolean mFMOn = false;

    /**
     * Generic Handler instance
     */
    final Handler mHandler = new Handler();

    /**
     * Resource to read Radio Data System (RDS) information embedded in a
     * radio stream if it exists
     */
    private FmRxRdsData mFMRxRDSData = null;

    /**
     * Used to modify UI view hierarchies
     */
    private RemoteViews statusBarViews = null;

    /**
     * Used to modify status bar notifications
     */
    private Notification statusBarNotification = null;

    /**
     * Handler for Notifications
     */
    private final Handler mNotificationHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_STAT_NOTIFICATION:
                    if (isFmOn()) {
                        statusBarViews.setTextViewText(R.id.frequency, getTunedFrequencyString());
                    } else {
                        statusBarViews.setTextViewText(R.id.frequency, getString(R.string.stat_no_fm));
                    }
                    startForeground(FMRADIOSERVICE_STATUS, statusBarNotification);
                    break;
                default:
                    super.handleMessage(msg);
                    break;
            }
        }
    };

    /**
     * Defines the interval after which we stop the service when idle
     */
    private static final int IDLE_DELAY = 60000;

    /**
     * Default java constructor
     */
    public FMRadioService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Grab a handle to the AudioManager
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // Register our MediaButtonEventReceiver so it receives the lockscreen events
        mAudioManager.registerMediaButtonEventReceiver(new ComponentName(getPackageName(),
                FMMediaButtonIntentReceiver.class.getName()));

        // Instantiate a handle to our shared preferences
        // TODO: These should be stored in a properties file and read from via a ResourceBundle
        mPrefs = new FmSharedPreferences(this);

        // Since this is the onCreate(), we set Callbacks to null
        mCallbacks = null;

        // Handle to telephone resources
        TelephonyManager tmgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        // Listen for phone call state
        tmgr.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

        // Handle for device power states
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

        // Resource to keep the device on a partial wake lock
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.getClass().getName());
        mWakeLock.setReferenceCounted(false);

        // Register for Screen On/off broadcast notifications
        registerScreenOnOffListener();

        // Register for headset plug/unplug intents
        registerHeadsetListener();

        /* If the service was idle, but got killed before it stopped itself, the
           system will relaunch it. Make sure it gets stopped again in that
           case.
         */
        Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);

        statusBarViews = new RemoteViews(getPackageName(), R.layout.statusbar);
        statusBarViews.setImageViewResource(R.id.icon, R.drawable.stat_notify_fm);

        statusBarNotification = new Notification();
        statusBarNotification.contentView = statusBarViews;
        statusBarNotification.flags |= Notification.FLAG_ONGOING_EVENT;
        statusBarNotification.icon = R.drawable.stat_notify_fm;
        statusBarNotification.contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, FMRadio.class), 0);
    }

    @Override
    public void onDestroy() {
        Log.d(LOGTAG, "onDestroy");
        if (isFmOn()) {
            Log.e(LOGTAG, "Service being destroyed while still playing.");
        }

        // make sure there aren't any other messages coming
        mDelayedStopHandler.removeCallbacksAndMessages(null);

        /* Remove the Screen On/off listener */
        if (mScreenOnOffReceiver != null) {
            unregisterReceiver(mScreenOnOffReceiver);
            mScreenOnOffReceiver = null;
        }
        /* Unregister the headset Broadcase receiver */
        if (mHeadsetReceiver != null) {
            unregisterReceiver(mHeadsetReceiver);
            mHeadsetReceiver = null;
        }

        Log.d(LOGTAG, "onDestroy: Giving up audio focus");

        // Give up Audio focus so other music apps can utilize it
        mAudioManager.abandonAudioFocus(mAudioFocusListener);

        /* Since the service is closing, disable the receiver */
        fmOff();

        TelephonyManager tmgr = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        tmgr.listen(mPhoneStateListener, 0);

        Log.d(LOGTAG, "onDestroy: unbindFromService completed");

        // unregisterReceiver(mIntentReceiver);
        mWakeLock.release();

        super.onDestroy();

        // Unregister as a receiver for media button events
        mAudioManager.unregisterMediaButtonEventReceiver(new ComponentName(getPackageName(),
                FMMediaButtonIntentReceiver.class.getName()));
    }

    private OnAudioFocusChangeListener mAudioFocusListener = new OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_LOSS:
                    Log.v(LOGTAG, "AudioFocus: received AUDIOFOCUS_LOSS, turning FM off");

                    if(isFmOn()) {
                        fmOff();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    Log.v(LOGTAG, "AudioFocus: received AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");

                    if (isFmOn()) {
                        Log.d(LOGTAG, "AudioFocus: FM is on, turning off");
                        mute();
                        stopFM();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    Log.v(LOGTAG, "AudioFocus: received AUDIOFOCUS_LOSS_TRANSIENT");

                    if(isFmOn()) {
                        Log.d(LOGTAG, "AudioFocus: FM is on, turning off");
                        mute();
                        stopFM();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_GAIN:
                    Log.v(LOGTAG, "AudioFocus: received AUDIOFOCUS_GAIN");

                    if(isFmOn()) {
                        Log.d(LOGTAG, "AudioFocus: FM is off, turning back on");
                        startFM();
                    }
                    break;
                default:
                    Log.e(LOGTAG, "Unknown audio focus change code " + focusChange);
            }
        }
    };

    /**
     * Registers an intent to listen for ACTION_HEADSET_PLUG notifications. This
     * intent is called to know if the headset was plugged in/out
     */
    public void registerHeadsetListener() {
        if (mHeadsetReceiver == null) {
            mHeadsetReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_HEADSET_PLUG)) {
                        Log.d(LOGTAG, "ACTION_HEADSET_PLUG Intent received");
                        // Listen for ACTION_HEADSET_PLUG broadcasts.
                        Log.d(LOGTAG, "mReceiver: ACTION_HEADSET_PLUG");
                        Log.d(LOGTAG, "==> intent: " + intent);
                        Log.d(LOGTAG, "    state: " + intent.getIntExtra("state", 0));
                        Log.d(LOGTAG, "    name: " + intent.getStringExtra("name"));
                        mHeadsetPlugged = (intent.getIntExtra("state", 0) == 1);
                        mHandler.post(mHeadsetPluginHandler);
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_HEADSET_PLUG);
            registerReceiver(mHeadsetReceiver, iFilter);
        }
    }

    final Runnable mHeadsetPluginHandler = new Runnable() {
        public void run() {
            /* Update the UI based on the state change of the headset/antenna */
            if (!isAntennaAvailable()) {
                /* Disable FM and let the UI know */
                if (isFmOn()) {
                    fmOff();
                    try {
                        /*
                         * Notify the UI/Activity, only if the service is "bound" by
                         * an activity and if Callbacks are registered
                         */
                        if ((mServiceInUse) && (mCallbacks != null)) {
                            mCallbacks.onDisabled();
                        }
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mServiceInUse = true;
        /* Application/UI is attached, so get out of lower power mode */
        setLowPowerMode(false);
        Log.d(LOGTAG, "onBind");
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mServiceInUse = true;
        /* Application/UI is attached, so get out of lower power mode */
        setLowPowerMode(false);
        Log.d(LOGTAG, "onRebind");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mServiceStartId = startId;
        mDelayedStopHandler.removeCallbacksAndMessages(null);

        Log.d(LOGTAG, "StartId: "+startId);

        if (intent != null) {
            Log.d(LOGTAG, "onStartCommand: Intent received -> "+intent.toString());
            // We don't act on Action for Lockscreen controls as the actual command is stored
            // as an extra value in the Intent
            String cmd = intent.getStringExtra(FMRadioService.CMDNAME);

            if (FMRadioService.CMDTOGGLEPAUSE.equals(cmd)) {
                Log.d(LOGTAG, "Play/Pause Intent received");

                // This handles the PLAY action
                if(isFmOn() && isMuted()) {
                    Log.d(LOGTAG, "Unpausing FM radio playback");
                    unMute();
                    startFM();

                    try {
                        if (mCallbacks != null) {
                            mCallbacks.onMute(false);
                        }
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
                // This handles the PAUSE action
                else if(isFmOn() && !isMuted()) {
                    Log.d(LOGTAG, "Pausing FM radio playback");
                    mute();
                    stopFM();

                    try {
                        if (mCallbacks != null) {
                            mCallbacks.onMute(true);
                        }
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
            else if(FMRadioService.CMDNEXT.equals(cmd)) {
                // Verify the FM radio is turned on
                if(isFmOn()) {
                    Log.d(LOGTAG, "Moving up in frequency");
                    nextFrequency(true);

                    try {
                        /*
                         * Notify the UI/Activity, only if the service is "bound" by
                         * an activity and if Callbacks are registered
                         */
                        if ((mServiceInUse) && (mCallbacks != null)) {
                            mCallbacks.onTuneStatusChanged();
                        }
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
            else if(FMRadioService.CMDPREVIOUS.equals(cmd)) {
                // Verify the FM radio is turned on
                if(isFmOn()) {
                    Log.d(LOGTAG, "Moving down in frequency");
                    nextFrequency(false);

                    try {
                        /*
                         * Notify the UI/Activity, only if the service is "bound" by
                         * an activity and if Callbacks are registered
                         */
                        if ((mServiceInUse) && (mCallbacks != null)) {
                            mCallbacks.onTuneStatusChanged();
                        }
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // make sure the service will shut down on its own if it was
        // just started but not bound to and nothing is playing
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
        return START_STICKY;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mServiceInUse = false;
        Log.d(LOGTAG, "onUnbind");

        /* Application/UI is not attached, so go into lower power mode */
        unregisterCallbacks();
        setLowPowerMode(true);
        if (isFmOn()) {
            // something is currently playing, or will be playing once
            // an in-progress call ends, so don't stop the service now.
            return true;
        }

        stopSelf(mServiceStartId);
        return true;
    }

    private void startFM(){
        Log.d(LOGTAG, "In startFM");
        AudioSystem.setDeviceConnectionState(AudioSystem.DEVICE_OUT_FM, AudioSystem.DEVICE_STATE_AVAILABLE, "");
    }

    private void stopFM(){
        Log.d(LOGTAG, "In stopFM");
        AudioSystem.setDeviceConnectionState(AudioSystem.DEVICE_OUT_FM, AudioSystem.DEVICE_STATE_UNAVAILABLE, "");
    }

    /* Handle Phone Call + FM Concurrency */
    private PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            Log.d(LOGTAG, "onCallStateChanged: State - " + state);

            //AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

            if (state == TelephonyManager.CALL_STATE_RINGING) {
                int ringvolume = mAudioManager.getStreamVolume(AudioManager.STREAM_RING);
                if (ringvolume > 0) {
                    mute();
                    stopFM();
                    mResumeAfterCall = true;
                    try {
                        if (mCallbacks != null) {
                            mCallbacks.onMute(true);
                        }
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            } // ringing
            else if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                // pause the music while a conversation is in progress
                mute();
                stopFM();
                mResumeAfterCall = true;
                try {
                    if (mCallbacks != null) {
                        mCallbacks.onMute(true);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            } // offhook
            else if (state == TelephonyManager.CALL_STATE_IDLE) {
                // start playing again
                if (mResumeAfterCall) {
                    Log.d(LOGTAG, "onCallStateChanged: Re-enabling FM now that phone call has ended");

                    // resume playback only if FM Radio was playing
                    // when the call was answered
                    // unMute-FM
                    unMute();
                    startFM();

                    // Re-enable audio focus as the phone call stole it away
                    mAudioManager.requestAudioFocus(mAudioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

                    // Register our MediaButtonEventReceiver so it receives the lockscreen events and volume control events
                    mAudioManager.registerMediaButtonEventReceiver(new ComponentName(getPackageName(),
                            FMMediaButtonIntentReceiver.class.getName()));

                    mResumeAfterCall = false;
                    try {
                        if (mCallbacks != null) {
                            mCallbacks.onMute(false);
                        }
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }// idle
        }
    };

    private Handler mDelayedStopHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // Check again to make sure nothing is playing right now
            if (isFmOn() || mServiceInUse) {
                return;
            }
            Log.d(LOGTAG, "mDelayedStopHandler: stopSelf");
            stopSelf(mServiceStartId);
        }
    };

    /**
     * Registers an intent to listen for ACTION_SCREEN_ON/ACTION_SCREEN_OFF
     * notifications. This intent is called to know iwhen the screen is turned
     * on/off.
     */
    public void registerScreenOnOffListener() {
        if (mScreenOnOffReceiver == null) {
            mScreenOnOffReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_SCREEN_ON)) {
                        Log.d(LOGTAG, "ACTION_SCREEN_ON Intent received");
                        // Screen turned on, set FM module into normal power
                        // mode
                        mHandler.post(mScreenOnHandler);
                    } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                        Log.d(LOGTAG, "ACTION_SCREEN_OFF Intent received");
                        // Screen turned on, set FM module into low power mode
                        mHandler.post(mScreenOffHandler);
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_SCREEN_ON);
            iFilter.addAction(Intent.ACTION_SCREEN_OFF);
            registerReceiver(mScreenOnOffReceiver, iFilter);
        }
    }

    /*
     * Handle all the Screen On actions: Set FM Power mode to Normal
     */
    final Runnable mScreenOnHandler = new Runnable() {
        public void run() {
            setLowPowerMode(false);
        }
    };

    /*
     * Handle all the Screen Off actions: Set FM Power mode to Low Power This
     * will reduce all the interrupts coming up from the SoC, saving power
     */
    final Runnable mScreenOffHandler = new Runnable() {
        public void run() {
            setLowPowerMode(true);
        }
    };

    private void stop() {
        gotoIdleState();
        mFMOn = false;
    }

    private void gotoIdleState() {
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
        // NotificationManager nm =
        // (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // nm.cancel(FMRADIOSERVICE_STATUS);
        // setForeground(false);
        stopForeground(true);
    }

    /* Show the FM Notification */
    private void updateNotification() {
        mNotificationHandler.removeMessages(UPDATE_STAT_NOTIFICATION);
        // delay 1000ms so that we won't send notification too frequently during move
        mNotificationHandler.sendEmptyMessageDelayed(UPDATE_STAT_NOTIFICATION, 1000);
    }

    /*
     * Read the Tuned Frequency from the FM module.
     */
    private String getTunedFrequencyString() {
        String frequencyString = getString(R.string.stat_notif_frequency,
                FrequencyPicker.formatFrequencyString(FmSharedPreferences.getTunedFrequency()));
        return frequencyString;
    }


    /**
     * Read's the internal Antenna available state from the FM Device.
     */
    public void readInternalAntennaAvailable() {
        mInternalAntennaAvailable = false;
        if (mReceiver != null) {
            mInternalAntennaAvailable = mReceiver.getInternalAntenna();
            Log.d(LOGTAG, "getInternalAntenna: " + mInternalAntennaAvailable);
        }
    }

    /*
     * By making this a static class with a WeakReference to the Service, we
     * ensure that the Service can be GCd even when the system process still has
     * a remote reference to the stub.
     */
    static class ServiceStub extends IFMRadioService.Stub {
        WeakReference<FMRadioService> mService;

        ServiceStub(FMRadioService service) {
            mService = new WeakReference<FMRadioService>(service);
        }

        public boolean fmOn() throws RemoteException {
            return (mService.get().fmOn());
        }

        public boolean fmOff() throws RemoteException {
            return (mService.get().fmOff());
        }

        public boolean isFmOn() {
            return (mService.get().isFmOn());
        }

        public boolean fmReconfigure() {
            return (mService.get().fmReconfigure());
        }

        public void registerCallbacks(IFMRadioServiceCallbacks cb) throws RemoteException {
            mService.get().registerCallbacks(cb);
        }

        public void unregisterCallbacks() throws RemoteException {
            mService.get().unregisterCallbacks();
        }

        public boolean routeAudio(int device) {
            return (mService.get().routeAudio(device));
        }

        public boolean mute() {
            return (mService.get().mute());
        }

        public boolean unMute() {
            return (mService.get().unMute());
        }

        public boolean isMuted() {
            return (mService.get().isMuted());
        }

        public boolean tune(int frequency) {
            return (mService.get().tune(frequency));
        }

        public boolean seek(boolean up) {
            return (mService.get().seek(up));
        }

        public boolean scan(int pty) {
            return (mService.get().scan(pty));
        }

        public boolean seekPI(int piCode) {
            return (mService.get().seekPI(piCode));
        }

        public boolean searchStrongStationList(int numStations) {
            return (mService.get().searchStrongStationList(numStations));
        }

        public boolean cancelSearch() {
            return (mService.get().cancelSearch());
        }

        public int getFreq() {
            return (mService.get().getFreq());
        }

        public String getProgramService() {
            return (mService.get().getProgramService());
        }

        public String getRadioText() {
            return (mService.get().getRadioText());
        }

        public int getProgramType() {
            return (mService.get().getProgramType());
        }

        public int getProgramID() {
            return (mService.get().getProgramID());
        }

        public int[] getSearchList() {
            return (mService.get().getSearchList());
        }

        public boolean setLowPowerMode(boolean enable) {
            return (mService.get().setLowPowerMode(enable));
        }

        public int getPowerMode() {
            return (mService.get().getPowerMode());
        }

        public boolean enableAutoAF(boolean bEnable) {
            return (mService.get().enableAutoAF(bEnable));
        }

        public boolean enableStereo(boolean bEnable) {
            return (mService.get().enableStereo(bEnable));
        }

        public boolean isAntennaAvailable() {
            return (mService.get().isAntennaAvailable());
        }

        public boolean isWiredHeadsetAvailable() {
            return (mService.get().isWiredHeadsetAvailable());
        }
    }

    private final IBinder mBinder = new ServiceStub(this);

    /*
     * Turn ON FM: Powers up FM hardware, and initializes the FM module .
     * @return true if fm Enable api was invoked successfully, false if the api
     * failed.
     */
    private boolean fmOn() {
        boolean bStatus = false;
        Log.d(LOGTAG, "fmOn");
        mAudioManager.registerMediaButtonEventReceiver(new ComponentName(getPackageName(), FMMediaButtonIntentReceiver.class.getName()));
        mAudioManager.requestAudioFocus(mAudioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        if (mReceiver == null) {
            mReceiver = new FmReceiver(FMRADIO_DEVICE_FD_STRING, null);
            if (mReceiver == null) {
                throw new RuntimeException("FmReceiver service not available!");
            }
        }

        if (mReceiver != null) {
            if (isFmOn()) {
                /* FM Is already on, */
                bStatus = true;
                Log.d(LOGTAG, "mReceiver.already enabled");
            } else {
                // This sets up the FM radio device
                FmConfig config = FmSharedPreferences.getFMConfiguration();
                Log.d(LOGTAG, "fmOn: RadioBand   :" + config.getRadioBand());
                Log.d(LOGTAG, "fmOn: Emphasis    :" + config.getEmphasis());
                Log.d(LOGTAG, "fmOn: ChSpacing   :" + config.getChSpacing());
                Log.d(LOGTAG, "fmOn: RdsStd      :" + config.getRdsStd());
                Log.d(LOGTAG, "fmOn: LowerLimit  :" + config.getLowerLimit());
                Log.d(LOGTAG, "fmOn: UpperLimit  :" + config.getUpperLimit());
                bStatus = mReceiver.enable(FmSharedPreferences.getFMConfiguration());
                Log.d(LOGTAG, "mReceiver.enable done, Status :" + bStatus);
            }

            if (bStatus == true) {
                startFM();
                bStatus = mReceiver.registerRdsGroupProcessing(FmReceiver.FM_RX_RDS_GRP_RT_EBL
                        | FmReceiver.FM_RX_RDS_GRP_PS_EBL | FmReceiver.FM_RX_RDS_GRP_AF_EBL
                        | FmReceiver.FM_RX_RDS_GRP_PS_SIMPLE_EBL);
                Log.d(LOGTAG, "registerRdsGroupProcessing done, Status :" + bStatus);
                bStatus = enableAutoAF(FmSharedPreferences.getAutoAFSwitch());
                Log.d(LOGTAG, "enableAutoAF done, Status :" + bStatus);
                /* Put the hardware into normal mode */
                bStatus = setLowPowerMode(false);
                Log.d(LOGTAG, "setLowPowerMode done, Status :" + bStatus);

                /* There is no internal Antenna */
                bStatus = mReceiver.setInternalAntenna(false);
                Log.d(LOGTAG, "setInternalAntenna done, Status :" + bStatus);

                /* Read back to verify the internal Antenna mode */
                readInternalAntennaAvailable();

                mFMOn = true;
                bStatus = true;
            } else {
                stop();
            }
        }
        return (bStatus);
    }

    /*
     * Turn OFF FM: Disable the FM Host and hardware . .
     * @return true if fm Disable api was invoked successfully, false if the api
     * failed.
     */
    private boolean fmOff() {
        boolean bStatus = false;
        Log.d(LOGTAG, "fmOff");
        mAudioManager.abandonAudioFocus(mAudioFocusListener);
        stopFM();
        // This will disable the FM radio device
        if (mReceiver != null) {
            bStatus = mReceiver.disable();
            mReceiver = null;
        }
        stop();
        return (bStatus);
    }

    /*
     * Returns whether FM hardware is ON.
     * @return true if FM was tuned, searching. (at the end of the search FM
     * goes back to tuned).
     */
    public boolean isFmOn() {
        return mFMOn;
    }

    /*
     * ReConfigure the FM Setup parameters - Band - Channel Spacing (50/100/200
     * KHz) - Emphasis (50/75) - Frequency limits - RDS/RBDS standard
     * @return true if configure api was invoked successfully, false if the api
     * failed.
     */
    public boolean fmReconfigure() {
        boolean bStatus = false;
        Log.d(LOGTAG, "fmReconfigure");
        if (mReceiver != null) {
            // This sets up the FM radio device
            FmConfig config = FmSharedPreferences.getFMConfiguration();
            Log.d(LOGTAG, "RadioBand   :" + config.getRadioBand());
            Log.d(LOGTAG, "Emphasis    :" + config.getEmphasis());
            Log.d(LOGTAG, "ChSpacing   :" + config.getChSpacing());
            Log.d(LOGTAG, "RdsStd      :" + config.getRdsStd());
            Log.d(LOGTAG, "LowerLimit  :" + config.getLowerLimit());
            Log.d(LOGTAG, "UpperLimit  :" + config.getUpperLimit());
            bStatus = mReceiver.configure(config);
        }
        return (bStatus);
    }

    /*
     * Register UI/Activity Callbacks
     */
    public void registerCallbacks(IFMRadioServiceCallbacks cb) {
        mCallbacks = cb;
    }

    /*
     * unRegister UI/Activity Callbacks
     */
    public void unregisterCallbacks() {
        mCallbacks = null;
    }

    /*
     * Route Audio to headset or speaker phone
     * @return true if routeAudio call succeeded, false if the route call
     * failed.
     */
    public boolean routeAudio(int audioDevice) {
        boolean bStatus = false;
        Log.d(LOGTAG, "routeAudio:");
        if (mReceiver != null) {
            if ((audioDevice == RADIO_AUDIO_DEVICE_WIRED_HEADSET)
                    || (audioDevice == RADIO_AUDIO_DEVICE_SPEAKER_PHONE)) {
                // Add API call when audio routing is supported
            } else {
                Log.e(LOGTAG, "routeAudio: Unsupported Audio Device: " + audioDevice);
            }
        }
        return bStatus;
    }

    /*
     * Mute FM Hardware (SoC)
     * @return true if set mute mode api was invoked successfully, false if the
     * api failed.
     */
    public boolean mute() {
        boolean bCommandSent = false;
        Log.d(LOGTAG, "mute:");
        if (mReceiver != null) {
            mMuted = true;
            bCommandSent = mReceiver.setMuteMode(FmReceiver.FM_RX_MUTE);
        }
        return bCommandSent;
    }

    /*
     * UnMute FM Hardware (SoC)
     * @return true if set mute mode api was invoked successfully, false if the
     * api failed.
     */
    public boolean unMute() {
        boolean bCommandSent = false;
        Log.d(LOGTAG, "unMute:");
        if (mReceiver != null) {
            mMuted = false;
            bCommandSent = mReceiver.setMuteMode(FmReceiver.FM_RX_UNMUTE);
        }
        return bCommandSent;
    }

    /*
     * Returns whether FM Hardware(Soc) Audio is Muted.
     * @return true if FM Audio is muted, false if not muted.
     */
    public boolean isMuted() {
        return mMuted;
    }

    /*
     * Tunes to the specified frequency
     * @return true if Tune command was invoked successfully, false if not
     * muted. Note: Callback FmRxEvRadioTuneStatus will be called when the tune
     * is complete
     */
    public boolean tune(int frequency) {
        boolean bCommandSent = false;
        double doubleFrequency = frequency / 1000.00;

        Log.d(LOGTAG, "tuneRadio:  " + doubleFrequency);
        if (mReceiver != null) {
            mReceiver.setStation(frequency);
            bCommandSent = true;
            updateNotification();
        }
        return bCommandSent;
    }

    /**
     * Get the current tuned frequency from the radio module
     * @return freq value currently tuned
     */
    public int getFreq() {
	return mReceiver.getTunedFrequency();
    }

   /**
     * Changes frequencies either higher or lower depending on {code boolean}
     * passed in.
     *
     * @param up increments higher if {code true}, lower if {code false}
     * @return result of command being issued
     */
    public boolean nextFrequency(boolean up) {
        boolean bCommandSent = false;
        int intNewFreq = 0;

        // Verify we don't have a null handle to FmReceiver
        if(mReceiver != null) {
            /* We have to grab the current freq from FmSharedPreferences rather than mReceiver
             * since any changes to freq get saved to FmSharedPreferences and NOT mReceiver
             */
            int intCurrFreq = FmSharedPreferences.getTunedFrequency();
            Log.d(LOGTAG, "Current Tuned frequency: "+intCurrFreq);

            if(up) {
                intNewFreq = intCurrFreq + 100;
            }
            else {
                intNewFreq = intCurrFreq - 100;
            }
            Log.d(LOGTAG, "New Frequency: "+intNewFreq);

            // Update the new frequency in FmSharedPreferences
            FmSharedPreferences.setTunedFrequency(intNewFreq);

            // Call tune() to process the changes in FmReceiver
            bCommandSent = tune(intNewFreq);
        }

        return bCommandSent;
    }

    /*
     * Seeks (Search for strong station) to the station in the direction
     * specified relative to the tuned station. boolean up: true - Search in the
     * forward direction. false - Search in the backward direction.
     * @return true if Seek command was invoked successfully, false if not
     * muted. Note: 1. Callback FmRxEvSearchComplete will be called when the
     * Search is complete 2. Callback FmRxEvRadioTuneStatus will also be called
     * when tuned to a station at the end of the Search or if the seach was
     * cancelled.
     */
    public boolean seek(boolean up) {
        boolean bCommandSent = false;
        if (mReceiver != null) {
            if (up == true) {
                Log.d(LOGTAG, "seek:  Up");
                mReceiver.searchStations(FmReceiver.FM_RX_SRCH_MODE_SEEK,
                        FmReceiver.FM_RX_DWELL_PERIOD_1S, FmReceiver.FM_RX_SEARCHDIR_UP);
            } else {
                Log.d(LOGTAG, "seek:  Down");
                mReceiver.searchStations(FmReceiver.FM_RX_SRCH_MODE_SEEK,
                        FmReceiver.FM_RX_DWELL_PERIOD_1S, FmReceiver.FM_RX_SEARCHDIR_DOWN);
            }
            bCommandSent = true;
        }
        return bCommandSent;
    }

    /*
     * Scan (Search for station with a "preview" of "n" seconds) FM Stations. It
     * always scans in the forward direction relative to the current tuned
     * station. int pty: 0 or a reserved PTY value- Perform a "strong" station
     * search of all stations. Valid/Known PTY - perform RDS Scan for that pty.
     * @return true if Scan command was invoked successfully, false if not
     * muted. Note: 1. Callback FmRxEvRadioTuneStatus will be called when tuned
     * to various stations during the Scan. 2. Callback FmRxEvSearchComplete
     * will be called when the Search is complete 3. Callback
     * FmRxEvRadioTuneStatus will also be called when tuned to a station at the
     * end of the Search or if the seach was cancelled.
     */
    public boolean scan(int pty) {
        boolean bCommandSent = false;
        if (mReceiver != null) {
            Log.d(LOGTAG, "scan:  PTY: " + pty);
            if (FmSharedPreferences.isRBDSStd()) {
                /* RBDS : Validate PTY value?? */
                if (((pty > 0) && (pty <= 23)) || ((pty >= 29) && (pty <= 31))) {
                    bCommandSent = mReceiver
                            .searchStations(FmReceiver.FM_RX_SRCHRDS_MODE_SCAN_PTY,
                                    FmReceiver.FM_RX_DWELL_PERIOD_7S,
                                    FmReceiver.FM_RX_SEARCHDIR_UP, pty, 0);
                } else {
                    bCommandSent = mReceiver.searchStations(FmReceiver.FM_RX_SRCH_MODE_SCAN,
                            FmReceiver.FM_RX_DWELL_PERIOD_7S, FmReceiver.FM_RX_SEARCHDIR_UP);
                }
            } else {
                /* RDS : Validate PTY value?? */
                if ((pty > 0) && (pty <= 31)) {
                    bCommandSent = mReceiver
                            .searchStations(FmReceiver.FM_RX_SRCHRDS_MODE_SCAN_PTY,
                                    FmReceiver.FM_RX_DWELL_PERIOD_7S,
                                    FmReceiver.FM_RX_SEARCHDIR_UP, pty, 0);
                } else {
                    bCommandSent = mReceiver.searchStations(FmReceiver.FM_RX_SRCH_MODE_SCAN,
                            FmReceiver.FM_RX_DWELL_PERIOD_7S, FmReceiver.FM_RX_SEARCHDIR_UP);
                }
            }
        }
        return bCommandSent;
    }

    /*
     * Search for the 'numStations' number of strong FM Stations. It searches in
     * the forward direction relative to the current tuned station. int
     * numStations: maximum number of stations to search.
     * @return true if Search command was invoked successfully, false if not
     * muted. Note: 1. Callback FmRxEvSearchListComplete will be called when the
     * Search is complete 2. Callback FmRxEvRadioTuneStatus will also be called
     * when tuned to the previously tuned station.
     */
    public boolean searchStrongStationList(int numStations) {
        boolean bCommandSent = false;
        if (mReceiver != null) {
            Log.d(LOGTAG, "searchStrongStationList:  numStations: " + numStations);
            bCommandSent = mReceiver.searchStationList(FmReceiver.FM_RX_SRCHLIST_MODE_STRONG,
                    FmReceiver.FM_RX_SEARCHDIR_UP, numStations, 0);
        }
        return bCommandSent;
    }

    /*
     * Search for the FM Station that matches the RDS PI (Program Identifier)
     * code. It always scans in the forward direction relative to the current
     * tuned station. int piCode: PI Code of the station to search.
     * @return true if Search command was invoked successfully, false if not
     * muted. Note: 1. Callback FmRxEvSearchComplete will be called when the
     * Search is complete 2. Callback FmRxEvRadioTuneStatus will also be called
     * when tuned to a station at the end of the Search or if the seach was
     * cancelled.
     */
    public boolean seekPI(int piCode) {
        boolean bCommandSent = false;
        if (mReceiver != null) {
            Log.d(LOGTAG, "seekPI:  piCode: " + piCode);
            bCommandSent = mReceiver.searchStations(FmReceiver.FM_RX_SRCHRDS_MODE_SEEK_PI,
                    FmReceiver.FM_RX_DWELL_PERIOD_1S, FmReceiver.FM_RX_SEARCHDIR_UP, 0, piCode);
        }
        return bCommandSent;
    }

    /*
     * Cancel any ongoing Search (Seek/Scan/SearchStationList).
     * @return true if Search command was invoked successfully, false if not
     * muted. Note: 1. Callback FmRxEvSearchComplete will be called when the
     * Search is complete/cancelled. 2. Callback FmRxEvRadioTuneStatus will also
     * be called when tuned to a station at the end of the Search or if the
     * seach was cancelled.
     */
    public boolean cancelSearch() {
        boolean bCommandSent = false;
        if (mReceiver != null) {
            Log.d(LOGTAG, "cancelSearch");
            bCommandSent = mReceiver.cancelSearch();
        }
        return bCommandSent;
    }

    /*
     * Retrieves the RDS Program Service (PS) String.
     * @return String - RDS PS String. Note: 1. This is a synchronous call that
     * should typically called when Callback FmRxEvRdsPsInfo is invoked. 2.
     * Since PS contains multiple fields, this Service reads all the fields and
     * "caches" the values and provides this helper routine for the Activity to
     * get only the information it needs. 3. The "cached" data fields are always
     * "cleared" when the tune status changes.
     */
    public String getProgramService() {
        String str = "";
        if (mFMRxRDSData != null) {
            str = mFMRxRDSData.getPrgmServices();
            if (str == null) {
                str = "";
            }
        }
        Log.d(LOGTAG, "Program Service: [" + str + "]");
        return str;
    }

    /*
     * Retrieves the RDS Radio Text (RT) String.
     * @return String - RDS RT String. Note: 1. This is a synchronous call that
     * should typically called when Callback FmRxEvRdsRtInfo is invoked. 2.
     * Since RT contains multiple fields, this Service reads all the fields and
     * "caches" the values and provides this helper routine for the Activity to
     * get only the information it needs. 3. The "cached" data fields are always
     * "cleared" when the tune status changes.
     */
    public String getRadioText() {
        String str = "";
        if (mFMRxRDSData != null) {
            str = mFMRxRDSData.getRadioText();
            if (str == null) {
                str = "";
            }
        }
        Log.d(LOGTAG, "Radio Text: [" + str + "]");
        return str;
    }

    /*
     * Retrieves the RDS Program Type (PTY) code.
     * @return int - RDS PTY code. Note: 1. This is a synchronous call that
     * should typically called when Callback FmRxEvRdsRtInfo and or
     * FmRxEvRdsPsInfo is invoked. 2. Since RT/PS contains multiple fields, this
     * Service reads all the fields and "caches" the values and provides this
     * helper routine for the Activity to get only the information it needs. 3.
     * The "cached" data fields are always "cleared" when the tune status
     * changes.
     */
    public int getProgramType() {
        int pty = -1;
        if (mFMRxRDSData != null) {
            pty = mFMRxRDSData.getPrgmType();
        }
        Log.d(LOGTAG, "PTY: [" + pty + "]");
        return pty;
    }

    /*
     * Retrieves the RDS Program Identifier (PI).
     * @return int - RDS PI code. Note: 1. This is a synchronous call that
     * should typically called when Callback FmRxEvRdsRtInfo and or
     * FmRxEvRdsPsInfo is invoked. 2. Since RT/PS contains multiple fields, this
     * Service reads all the fields and "caches" the values and provides this
     * helper routine for the Activity to get only the information it needs. 3.
     * The "cached" data fields are always "cleared" when the tune status
     * changes.
     */
    public int getProgramID() {
        int pi = -1;
        if (mFMRxRDSData != null) {
            pi = mFMRxRDSData.getPrgmId();
        }
        Log.d(LOGTAG, "PI: [" + pi + "]");
        return pi;
    }

    /*
     * Retrieves the station list from the SearchStationlist.
     * @return Array of integers that represents the station frequencies. Note:
     * 1. This is a synchronous call that should typically called when Callback
     * onSearchListComplete.
     */
    public int[] getSearchList() {
        int[] frequencyList = null;
        if (mReceiver != null) {
            Log.d(LOGTAG, "getSearchList: ");
            frequencyList = mReceiver.getStationList();
        }
        return frequencyList;
    }

    /*
     * Set the FM Power Mode on the FM hardware SoC. Typically used when
     * UI/Activity is in the background, so the Host is interrupted less often.
     * boolean bLowPower: true: Enable Low Power mode on FM hardware. false:
     * Disable Low Power mode on FM hardware. (Put into normal power mode)
     * @return true if set power mode api was invoked successfully, false if the
     * api failed.
     */
    public boolean setLowPowerMode(boolean bLowPower) {
        boolean bCommandSent = false;
        if (mReceiver != null) {
            Log.d(LOGTAG, "setLowPowerMode: " + bLowPower);
            if (bLowPower) {
                bCommandSent = mReceiver.setPowerMode(FmReceiver.FM_RX_LOW_POWER_MODE);
            } else {
                bCommandSent = mReceiver.setPowerMode(FmReceiver.FM_RX_NORMAL_POWER_MODE);
            }
        }
        return bCommandSent;
    }

    /*
     * Get the FM Power Mode on the FM hardware SoC.
     * @return the device power mode.
     */
    public int getPowerMode() {
        int powerMode = FmReceiver.FM_RX_NORMAL_POWER_MODE;
        if (mReceiver != null) {
            powerMode = mReceiver.getPowerMode();
            Log.d(LOGTAG, "getLowPowerMode: " + powerMode);
        }
        return powerMode;
    }

    /*
     * Set the FM module to auto switch to an Alternate Frequency for the
     * station if one the signal strength of that frequency is stronger than the
     * current tuned frequency. boolean bEnable: true: Auto switch to stronger
     * alternate frequency. false: Do not switch to alternate frequency.
     * @return true if set Auto AF mode api was invoked successfully, false if
     * the api failed. Note: Callback FmRxEvRadioTuneStatus will be called when
     * tune is complete to a different frequency.
     */
    public boolean enableAutoAF(boolean bEnable) {
        boolean bCommandSent = false;
        if (mReceiver != null) {
            Log.d(LOGTAG, "enableAutoAF: " + bEnable);
            bCommandSent = mReceiver.enableAFjump(bEnable);
        }
        return bCommandSent;
    }

    /*
     * Set the FM module to Stereo Mode or always force it to Mono Mode. Note:
     * The stereo mode will be available only when the station is broadcasting
     * in Stereo mode. boolean bEnable: true: Enable Stereo Mode. false: Always
     * stay in Mono Mode.
     * @return true if set Stereo mode api was invoked successfully, false if
     * the api failed.
     */
    public boolean enableStereo(boolean bEnable) {
        boolean bCommandSent = false;
        if (mReceiver != null) {
            Log.d(LOGTAG, "enableStereo: " + bEnable);
            bCommandSent = mReceiver.setStereoMode(bEnable);
        }
        return bCommandSent;
    }

    /**
     * Determines if an internal Antenna is available. Returns the cached value
     * initialized on FMOn.
     *
     * @return true if internal antenna is available or wired headset is plugged
     *         in, false if internal antenna is not available and wired headset
     *         is not plugged in.
     */
    public boolean isAntennaAvailable() {
        boolean bAvailable = false;
        if ((mInternalAntennaAvailable) || (mHeadsetPlugged)) {
            bAvailable = true;
        }
        return bAvailable;
    }

    /**
     * Determines if a Wired headset is plugged in. Returns the cached value
     * initialized on broadcast receiver initialization.
     *
     * @return true if wired headset is plugged in, false if wired headset is
     *         not plugged in.
     */
    public boolean isWiredHeadsetAvailable() {
        return (mHeadsetPlugged);
    }

    /* Receiver callbacks back from the FM Stack */
    FmRxEvCallbacksAdaptor fmCallbacks = new FmRxEvCallbacksAdaptor() {
        public void FmRxEvEnableReceiver() {
            Log.d(LOGTAG, "FmRxEvEnableReceiver");
        }

        public void FmRxEvDisableReceiver() {
            Log.d(LOGTAG, "FmRxEvEnableReceiver");
        }

        public void FmRxEvConfigReceiver() {
            Log.d(LOGTAG, "FmRxEvConfigReceiver");
        }

        public void FmRxEvMuteModeSet() {
            Log.d(LOGTAG, "FmRxEvMuteModeSet");
        }

        public void FmRxEvStereoModeSet() {
            Log.d(LOGTAG, "FmRxEvStereoModeSet");
        }

        public void FmRxEvRadioStationSet() {
            Log.d(LOGTAG, "FmRxEvRadioStationSet");
        }

        public void FmRxEvPowerModeSet() {
            Log.d(LOGTAG, "FmRxEvPowerModeSet");
        }

        public void FmRxEvSetSignalThreshold() {
            Log.d(LOGTAG, "FmRxEvSetSignalThreshold");
        }

        public void FmRxEvRadioTuneStatus(int frequency) {
            Log.d(LOGTAG, "FmRxEvRadioTuneStatus: Tuned Frequency: " + frequency);
            try {
                FmSharedPreferences.setTunedFrequency(frequency);
                // Log.d(LOGTAG, "Call mCallbacks.onTuneStatusChanged");
                /* Since the Tuned Status changed, clear out the RDSData cached */
                mFMRxRDSData = null;
                if (mCallbacks != null) {
                    mCallbacks.onTuneStatusChanged();
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        public void FmRxEvStationParameters() {
            Log.d(LOGTAG, "FmRxEvStationParameters");
        }

        public void FmRxEvRdsLockStatus(boolean bRDSSupported) {
            Log.d(LOGTAG, "FmRxEvRdsLockStatus: " + bRDSSupported);
            try {
                if (mCallbacks != null) {
                    mCallbacks.onStationRDSSupported(bRDSSupported);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        public void FmRxEvStereoStatus(boolean stereo) {
            Log.d(LOGTAG, "FmRxEvStereoStatus: " + stereo);
            try {
                if (mCallbacks != null) {
                    mCallbacks.onAudioUpdate(stereo);
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        public void FmRxEvServiceAvailable() {
            Log.d(LOGTAG, "FmRxEvServiceAvailable");
        }

        public void FmRxEvGetSignalThreshold() {
            Log.d(LOGTAG, "FmRxEvGetSignalThreshold");
        }

        public void FmRxEvSearchInProgress() {
            Log.d(LOGTAG, "FmRxEvSearchInProgress");
        }

        public void FmRxEvSearchRdsInProgress() {
            Log.d(LOGTAG, "FmRxEvSearchRdsInProgress");
        }

        public void FmRxEvSearchListInProgress() {
            Log.d(LOGTAG, "FmRxEvSearchListInProgress");
        }

        public void FmRxEvSearchComplete(int frequency) {
            Log.d(LOGTAG, "FmRxEvSearchComplete: Tuned Frequency: " + frequency);
            try {
                FmSharedPreferences.setTunedFrequency(frequency);
                // Log.d(LOGTAG, "Call mCallbacks.onSearchComplete");
                /* Since the Tuned Status changed, clear out the RDSData cached */
                mFMRxRDSData = null;
                if (mCallbacks != null) {
                    mCallbacks.onSearchComplete();
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        public void FmRxEvSearchRdsComplete() {
            Log.d(LOGTAG, "FmRxEvSearchRdsComplete");
        }

        public void FmRxEvSearchListComplete() {
            Log.d(LOGTAG, "FmRxEvSearchListComplete");
            try {
                if (mCallbacks != null) {
                    mCallbacks.onSearchListComplete();
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        public void FmRxEvSearchCancelled() {
            Log.d(LOGTAG, "FmRxEvSearchCancelled");
        }

        public void FmRxEvRdsGroupData() {
            Log.d(LOGTAG, "FmRxEvRdsGroupData");
        }

        public void FmRxEvRdsPsInfo() {
            Log.d(LOGTAG, "FmRxEvRdsPsInfo: ");
            try {
                if (mReceiver != null) {
                    mFMRxRDSData = mReceiver.getPSInfo();
                    if (mFMRxRDSData != null) {
                        Log.d(LOGTAG, "PI: [" + mFMRxRDSData.getPrgmId() + "]");
                        Log.d(LOGTAG, "PTY: [" + mFMRxRDSData.getPrgmType() + "]");
                        Log.d(LOGTAG, "PS: [" + mFMRxRDSData.getPrgmServices() + "]");
                    }
                    if (mCallbacks != null) {
                        mCallbacks.onProgramServiceChanged();
                    }
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }

        public void FmRxEvRdsRtInfo() {
            Log.d(LOGTAG, "FmRxEvRdsRtInfo");
            try {
                // Log.d(LOGTAG, "Call mCallbacks.onRadioTextChanged");
                if (mReceiver != null) {
                    mFMRxRDSData = mReceiver.getRTInfo();
                    if (mFMRxRDSData != null) {
                        Log.d(LOGTAG, "PI: [" + mFMRxRDSData.getPrgmId() + "]");
                        Log.d(LOGTAG, "PTY: [" + mFMRxRDSData.getPrgmType() + "]");
                        Log.d(LOGTAG, "RT: [" + mFMRxRDSData.getRadioText() + "]");
                    }
                    if (mCallbacks != null) {
                        mCallbacks.onRadioTextChanged();
                    }
                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }

        }

        public void FmRxEvRdsAfInfo() {
            Log.d(LOGTAG, "FmRxEvRdsAfInfo");
        }

        public void FmRxEvRdsPiMatchAvailable() {
            Log.d(LOGTAG, "FmRxEvRdsPiMatchAvailable");
        }

        public void FmRxEvRdsGroupOptionsSet() {
            Log.d(LOGTAG, "FmRxEvRdsGroupOptionsSet");
        }

        public void FmRxEvRdsProcRegDone() {
            Log.d(LOGTAG, "FmRxEvRdsProcRegDone");
        }

        public void FmRxEvRdsPiMatchRegDone() {
            Log.d(LOGTAG, "FmRxEvRdsPiMatchRegDone");
        }
    };

}
