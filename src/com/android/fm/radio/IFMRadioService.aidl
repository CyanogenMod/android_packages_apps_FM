package com.android.fm.radio;

import com.android.fm.radio.IFMRadioServiceCallbacks;

interface IFMRadioService
{
    boolean fmOn();
    boolean fmOff();
    boolean isFmOn();
    boolean fmReconfigure();
    void registerCallbacks(IFMRadioServiceCallbacks cb);
    void unregisterCallbacks();
    boolean mute();
    boolean routeAudio(int device);
    boolean unMute();
    boolean isMuted();
    boolean tune(int frequency);
    boolean seek(boolean up);
    boolean scan(int pty);
    boolean seekPI(int piCode);
    boolean searchStrongStationList(int numStations);
    int[]   getSearchList();
    boolean cancelSearch();
    String getProgramService();
    String getRadioText();
    int getProgramType();
    int getProgramID();
    boolean setLowPowerMode(boolean bLowPower);
    int getPowerMode();
    boolean enableAutoAF(boolean bEnable);
    boolean enableStereo(boolean bEnable);
    boolean isAntennaAvailable();
    boolean isWiredHeadsetAvailable();
}

