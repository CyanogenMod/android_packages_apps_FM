
package com.android.fm.radio;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.fmradio.FmConfig;
import android.hardware.fmradio.FmReceiver;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

public class FmSharedPreferences {
    public static final int REGIONAL_BAND_NORTH_AMERICA = 0;
    public static final int REGIONAL_BAND_EUROPE = 1;
    public static final int REGIONAL_BAND_JAPAN = 2;
    public static final int REGIONAL_BAND_JAPAN_WIDE = 3;
    public static final int REGIONAL_BAND_AUSTRALIA = 4;
    public static final int REGIONAL_BAND_AUSTRIA = 5;
    public static final int REGIONAL_BAND_BELGIUM = 6;
    public static final int REGIONAL_BAND_BRAZIL = 7;
    public static final int REGIONAL_BAND_CHINA = 8;
    public static final int REGIONAL_BAND_CZECH = 9;
    public static final int REGIONAL_BAND_DENMARK = 10;
    public static final int REGIONAL_BAND_FINLAND = 11;
    public static final int REGIONAL_BAND_FRANCE = 12;
    public static final int REGIONAL_BAND_GERMANY = 13;
    public static final int REGIONAL_BAND_GREECE = 14;
    public static final int REGIONAL_BAND_HONGKONG = 15;
    public static final int REGIONAL_BAND_INDIA = 16;
    public static final int REGIONAL_BAND_IRELAND = 17;
    public static final int REGIONAL_BAND_ITALY = 18;
    public static final int REGIONAL_BAND_KOREA = 19;
    public static final int REGIONAL_BAND_MEXICO = 20;
    public static final int REGIONAL_BAND_NETHERLANDS = 21;
    public static final int REGIONAL_BAND_NEWZEALAND = 22;
    public static final int REGIONAL_BAND_NORWAY = 23;
    public static final int REGIONAL_BAND_POLAND = 24;
    public static final int REGIONAL_BAND_PORTUGAL = 25;
    public static final int REGIONAL_BAND_RUSSIA = 26;
    public static final int REGIONAL_BAND_SINGAPORE = 27;
    public static final int REGIONAL_BAND_SLOVAKIA = 28;
    public static final int REGIONAL_BAND_SPAIN = 29;
    public static final int REGIONAL_BAND_SWITZERLAND = 30;
    public static final int REGIONAL_BAND_SWEDEN = 31;
    public static final int REGIONAL_BAND_TAIWAN = 32;
    public static final int REGIONAL_BAND_TURKEY = 33;
    public static final int REGIONAL_BAND_UNITEDKINGDOM = 34;
    public static final int REGIONAL_BAND_UNITED_STATES = 35;
    public static final int REGIONAL_BAND_DEFAULT = REGIONAL_BAND_NORTH_AMERICA;

    private static final String LOGTAG = FMRadio.LOGTAG;

    private static final String SHARED_PREFS = "fmradio_prefs";

    private static final String LIST_NUM = "list_number";

    private static final String LIST_NAME = "list_name";

    private static final String STATION_NAME = "station_name";

    private static final String STATION_FREQUENCY = "station_freq";

    private static final String STATION_ID = "station_id";

    private static final String STATION_PTY = "station_pty";

    private static final String STATION_RDS = "station_rds";

    private static final String STATION_NUM = "preset_number";

    private static final String FMCONFIG_COUNTRY = "fmconfig_country";

    private static final String FMSPEAKER = "fm_speaker";

    /* Storage key String */
    private static final String LAST_LIST_INDEX = "last_list_index";

    private static final String PREF_LAST_TUNED_FREQUENCY = "last_frequency";

    private static Map<String, String> mNameMap = new HashMap<String, String>();

    private static List<PresetList> mListOfPlists = new ArrayList<PresetList>();

    private static FmConfig mFMConfiguration;

    private static final String DEFAULT_NO_NAME = "";

    private static final int DEFAULT_NO_FREQUENCY = 97400;

    private static final int DEFAULT_NO_PTY = 0;

    private static final int DEFAULT_NO_STATIONID = 0;

    private static final int DEFAULT_NO_RDSSUP = 0;

    private static CharSequence[] mListEntries;

    private static CharSequence[] mListValues;

    private static int mListIndex;

    private Context mContext;

    private static int mTunedFrequency = 87600;

    private static int mFrequencyBand_Stepsize = 100;

    private static int mCountry = 0;

    private static boolean mSpeaker = false;
    /*
     * true = Stereo and false = "force Mono" even if Station is transmitting a
     * Stereo signal
     */
    private static boolean mAudioOutputMode = true;

    private static boolean mAFAutoSwitch = true;

    private static boolean mHeadsetRemovalBehaviour = true;

    private static int mRecordDuration = 0;

    private static int mBluetoothExitBehaviour = 0;

    FmSharedPreferences(Context context) {
        mContext = context.getApplicationContext();
        mFMConfiguration = new FmConfig();
        Load();
    }

    public static void removeStation(int listIndex, int stationIndex) {
        if (listIndex < getNumList()) {
            mListOfPlists.get(listIndex).removeStation(stationIndex);
        }
    }

    public static void removeStation(int listIndex, PresetStation station) {
        if (listIndex < getNumList()) {
            mListOfPlists.get(listIndex).removeStation(station);
        }
    }

    public static void setListName(int listIndex, String name) {
        if (listIndex < getNumList()) {
            mListOfPlists.get(listIndex).setName(name);
        }
    }

    public static void setStationName(int listIndex, int stationIndex, String name) {
        if (listIndex < getNumList()) {
            mListOfPlists.get(listIndex).setStationName(stationIndex, name);
        }
    }

    public static String getListName(int listIndex) {
        String name = "";
        addListIfEmpty(listIndex);
        if (listIndex < getNumList()) {
            name = mListOfPlists.get(listIndex).getName();
        }
        return name;
    }

    public static String getStationName(int listIndex, int stationIndex) {
        String name = "";
        if (listIndex < getNumList()) {
            name = mListOfPlists.get(listIndex).getStationName(stationIndex);
        }
        return name;
    }

    public static double getStationFrequency(int listIndex, int stationIndex) {
        double frequency = 0;
        if (listIndex < getNumList()) {
            frequency = mListOfPlists.get(listIndex).getStationFrequency(stationIndex);
        }
        return frequency;
    }

    public static PresetList getStationList(int listIndex) {
        if (listIndex < getNumList()) {
            return mListOfPlists.get(listIndex);
        }
        return null;
    }

    public static PresetStation getselectedStation() {
        int listIndex = getCurrentListIndex();
        PresetStation station = null;
        if (listIndex < getNumList()) {
            station = mListOfPlists.get(listIndex).getSelectedStation();
        }
        return station;
    }

    public static PresetStation getStationInList(int index) {
        int listIndex = getCurrentListIndex();
        PresetStation station = null;
        if (listIndex < getNumList()) {
            station = mListOfPlists.get(listIndex).getStationFromIndex(index);
        }
        return station;
    }

    public static PresetStation getStationFromFrequency(int frequency) {
        int listIndex = getCurrentListIndex();
        PresetStation station = null;
        if (listIndex < getNumList()) {
            station = mListOfPlists.get(listIndex).getStationFromFrequency(frequency);
        }
        return station;
    }

    public static PresetStation selectNextStation() {
        int listIndex = getCurrentListIndex();
        PresetStation station = null;
        if (listIndex < getNumList()) {
            station = mListOfPlists.get(listIndex).selectNextStation();
        }
        return station;
    }

    public static PresetStation selectPrevStation() {
        int listIndex = getCurrentListIndex();
        PresetStation station = null;
        if (listIndex < getNumList()) {
            station = mListOfPlists.get(listIndex).selectPrevStation();
        }
        return station;
    }

    public static void selectStation(PresetStation station) {
        int listIndex = getCurrentListIndex();
        if (listIndex < getNumList()) {
            mListOfPlists.get(listIndex).selectStation(station);
        }
    }

    public static int getNumList() {
        return mListOfPlists.size();
    }

    public static int getCurrentListIndex() {
        return mListIndex;
    }

    public static void setListIndex(int index) {
        mListIndex = index;
    }

    public static Map<String, String> getNameMap() {
        return mNameMap;
    }

    private static void addListIfEmpty(int listIndex) {
        if ((listIndex < 1) && (getNumList() == 0)) {
            createPresetList("FM");
        }
    }

    public static void addStation(String name, int freq, int listIndex) {
        /*
         * If no lists exists and a new station is added, add a new Preset List
         * if "listIndex" requested was "0"
         */
        addListIfEmpty(listIndex);
        if (getNumList() > listIndex) {
            mListOfPlists.get(listIndex).addStation(name, freq);
        }
    }

    /** Add "station" into the Preset List indexed by "listIndex" */
    public static void addStation(int listIndex, PresetStation station) {
        /*
         * If no lists exists and a new station is added, add a new Preset List
         * if "listIndex" requested was "0"
         */
        addListIfEmpty(listIndex);
        if (getNumList() > listIndex) {
            mListOfPlists.get(listIndex).addStation(station);
        }
    }

    /** Does "station" already exist in the Preset List indexed by "listIndex" */
    public static boolean sameStationExists(int listIndex, PresetStation station) {
        boolean exists = false;
        if (getNumList() > listIndex) {
            exists = mListOfPlists.get(listIndex).sameStationExists(station);
        }
        return exists;
    }

    /** Does "station" already exist in the current Preset List */
    public static boolean sameStationExists(PresetStation station) {
        int listIndex = getCurrentListIndex();
        boolean exists = false;
        if (getNumList() > listIndex) {
            exists = mListOfPlists.get(listIndex).sameStationExists(station);
        }
        return exists;
    }

    /** Does "station" already exist in the current Preset List */
    public static int getListStationCount() {
        int listIndex = getCurrentListIndex();
        int numStations = 0;
        if (getNumList() > listIndex) {
            numStations = mListOfPlists.get(listIndex).getStationCount();
        }
        return numStations;
    }

    public static void renamePresetList(String newName, int listIndex) {
        PresetList curList = mListOfPlists.get(listIndex);
        if (curList != null) {
            String oldListName = curList.getName();
            curList.setName(newName);
            String index = mNameMap.get(oldListName);
            mNameMap.remove(oldListName);
            mNameMap.put((String) newName, index);
            repopulateEntryValueLists();
        }
    }

    /* Returns the index of the list just created */
    public static int createPresetList(String name) {
        int numLists = mListOfPlists.size();
        mListOfPlists.add(new PresetList(name));
        String index = String.valueOf(numLists);
        mNameMap.put(name, index);
        repopulateEntryValueLists();
        return numLists;
    }

    public static void createFirstPresetList(String name) {
        mListIndex = 0;
        createPresetList(name);
    }

    public static CharSequence[] repopulateEntryValueLists() {
        ListIterator<PresetList> presetIter;
        presetIter = mListOfPlists.listIterator();
        int numLists = mListOfPlists.size();

        mListEntries = new CharSequence[numLists];
        mListValues = new CharSequence[numLists];
        for (int i = 0; i < numLists; i++) {
            PresetList temp = presetIter.next();
            mListEntries[i] = temp.getName();
            mListValues[i] = temp.getName();
        }
        return mListEntries;
    }

    public static List<PresetList> getPresetLists() {
        return mListOfPlists;
    }

    public void Load() {
        Log.d(LOGTAG, "Load preferences ");
        if (mContext == null) {
            return;
        }
        SharedPreferences sp = mContext.getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        mTunedFrequency = sp.getInt(PREF_LAST_TUNED_FREQUENCY, DEFAULT_NO_FREQUENCY);
        /* Reset the Lists before reading the preferences */
        mListOfPlists.clear();

        int num_lists = sp.getInt(LIST_NUM, 1);
        for (int listIter = 0; listIter < num_lists; listIter++) {
            String listName = sp.getString(LIST_NAME + listIter, "FM - " + (listIter + 1));
            int numStations = sp.getInt(STATION_NUM + listIter, 1);
            if (listIter == 0) {
                createFirstPresetList(listName);
            } else {
                createPresetList(listName);
            }

            PresetList curList = mListOfPlists.get(listIter);
            for (int stationIter = 0; stationIter < numStations; stationIter++) {
                String stationName = sp.getString(STATION_NAME + listIter + "x" + stationIter,
                        DEFAULT_NO_NAME);
                int stationFreq = sp.getInt(STATION_FREQUENCY + listIter + "x" + stationIter,
                        DEFAULT_NO_FREQUENCY);
                PresetStation station = curList.addStation(stationName, stationFreq);

                int stationId = sp.getInt(STATION_ID + listIter + "x" + stationIter,
                        DEFAULT_NO_STATIONID);
                station.setPI(stationId);

                int pty = sp.getInt(STATION_PTY + listIter + "x" + stationIter, DEFAULT_NO_PTY);
                station.setPty(pty);

                int rdsSupported = sp.getInt(STATION_RDS + listIter + "x" + stationIter,
                        DEFAULT_NO_RDSSUP);
                if (rdsSupported != 0) {
                    station.setRDSSupported(true);
                } else {
                    station.setRDSSupported(false);
                }

            }
        }

        /* Load Configuration */
        setCountry(sp.getInt(FMCONFIG_COUNTRY, REGIONAL_BAND_DEFAULT));
        /* Load speaker state */
        setSpeaker(sp.getBoolean(FMSPEAKER, false));
        /* Last list the user was navigating */
        mListIndex = sp.getInt(LAST_LIST_INDEX, 0);
        if (mListIndex >= num_lists) {
            mListIndex = 0;
        }

        setBluetoothExitBehaviour(sp.getInt(Settings.BT_EXIT_BEHAVIOUR, 0));
        setHeadsetDcBehaviour(sp.getBoolean(Settings.HEADSET_DC_BEHAVIOUR, true));
    }

    public void Save() {
        if (mContext == null) {
            return;
        }
        Log.d(LOGTAG, "Save preferences ");

        int numLists = mListOfPlists.size();
        SharedPreferences sp = mContext.getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();

        ed.putInt(PREF_LAST_TUNED_FREQUENCY, mTunedFrequency);

        ed.putInt(LIST_NUM, numLists);
        /* Last list the user was navigating */
        ed.putInt(LAST_LIST_INDEX, mListIndex);

        for (int listIter = 0; listIter < numLists; listIter++) {
            PresetList curList = mListOfPlists.get(listIter);
            ed.putString(LIST_NAME + listIter, curList.getName());
            int numStations = curList.getStationCount();
            ed.putInt(STATION_NUM + listIter, numStations);
            int numStation = 0;
            for (int stationIter = 0; stationIter < numStations; stationIter++) {
                PresetStation station = curList.getStationFromIndex(stationIter);
                if (station != null) {
                    ed.putString(STATION_NAME + listIter + "x" + numStation, station.getName());
                    ed.putInt(STATION_FREQUENCY + listIter + "x" + numStation, station
                            .getFrequency());
                    ed.putInt(STATION_ID + listIter + "x" + numStation, station.getPI());
                    ed.putInt(STATION_PTY + listIter + "x" + numStation, station.getPty());
                    ed.putInt(STATION_RDS + listIter + "x" + numStation,
                            (station.getRDSSupported() == true ? 1 : 0));
                    numStation++;
                }
            }
        }

        /* Save Configuration */
        ed.putInt(FMCONFIG_COUNTRY, mCountry);
        /* Save speaker state */
        ed.putBoolean(FMSPEAKER, mSpeaker);
        ed.putInt(Settings.BT_EXIT_BEHAVIOUR, mBluetoothExitBehaviour);
        ed.putBoolean(Settings.HEADSET_DC_BEHAVIOUR, mHeadsetRemovalBehaviour);
        ed.commit();
    }

    public static void SetDefaults() {
        mListIndex = 0;
        mListOfPlists.clear();
        setCountry(REGIONAL_BAND_DEFAULT);
        setRadioBand(0);
        setChSpacing(0);
        setEmphasis(0);
        setRdsStd(0);
        mFMConfiguration.setLowerLimit(87500);
        mFMConfiguration.setUpperLimit(107900);
    }

    public static void removeStationList(int listIndex) {
        mListIndex = listIndex;
        PresetList toRemove = mListOfPlists.get(mListIndex);

        mNameMap.remove(toRemove.getName());
        mListOfPlists.remove(mListIndex);
        int numLists = mListOfPlists.size();

        /* Remove for others */
        for (int i = mListIndex; i < numLists; i++) {
            PresetList curList = mListOfPlists.get(i);
            if (curList != null) {
                String listName = curList.getName();
                /* Removals */
                mNameMap.remove(listName);
                mNameMap.put(listName, String.valueOf(i));
            }
        }
        mListIndex = 0;
        repopulateEntryValueLists();
    }

    public static void setTunedFrequency(int frequency) {
        mTunedFrequency = frequency;
    }

    public static int getTunedFrequency() {
        return mTunedFrequency;
    }

    public static int getNextTuneFrequency(int frequency) {
        int nextFrequency = (frequency + mFrequencyBand_Stepsize);
        if (nextFrequency > getUpperLimit()) {
            nextFrequency = getLowerLimit();
        }
        return nextFrequency;
    }

    public static int getNextTuneFrequency() {
        int nextFrequency = (mTunedFrequency + mFrequencyBand_Stepsize);
        if (nextFrequency > getUpperLimit()) {
            nextFrequency = getLowerLimit();
        }
        mTunedFrequency = nextFrequency;
        return nextFrequency;
    }

    public static int getPrevTuneFrequency(int frequency) {
        int prevFrequency = (frequency - mFrequencyBand_Stepsize);
        if (prevFrequency < getLowerLimit()) {
            prevFrequency = getUpperLimit();
        }
        return prevFrequency;
    }

    public static int getPrevTuneFrequency() {
        int prevFrequency = (mTunedFrequency - mFrequencyBand_Stepsize);
        if (prevFrequency < getLowerLimit()) {
            prevFrequency = getUpperLimit();
        }
        mTunedFrequency = prevFrequency;
        return prevFrequency;
    }

    /**
     * @param mFMConfiguration the mFMConfiguration to set
     */
    public static void setFMConfiguration(FmConfig mFMConfig) {
        FmSharedPreferences.mFMConfiguration = mFMConfig;
    }

    /**
     * @return the mFMConfiguration
     */
    public static FmConfig getFMConfiguration() {
        return mFMConfiguration;
    }

    public static void setRadioBand(int band) {
        switch (band) {
            case FmReceiver.FM_JAPAN_WIDE_BAND: {
                mFrequencyBand_Stepsize = 50;
                mFMConfiguration.setLowerLimit(76000);
                mFMConfiguration.setUpperLimit(108000);
                break;
            }
            case FmReceiver.FM_JAPAN_STANDARD_BAND: {
                mFrequencyBand_Stepsize = 100;
                mFMConfiguration.setLowerLimit(76000);
                mFMConfiguration.setUpperLimit(90000);
                break;
            }
            case FmReceiver.FM_USER_DEFINED_BAND: {
                break;
            }
            case FmReceiver.FM_US_BAND:
            case FmReceiver.FM_EU_BAND:
            default: {
                band = FmReceiver.FM_US_BAND;
                mFMConfiguration.setLowerLimit(87500);
                mFMConfiguration.setUpperLimit(107900);
                mFrequencyBand_Stepsize = 200;
            }
        }
        mFMConfiguration.setRadioBand(band);
    }

    public static int getRadioBand() {
        return mFMConfiguration.getRadioBand();
    }

    public static void setChSpacing(int spacing) {
        if ((spacing >= FmReceiver.FM_CHSPACE_200_KHZ) && (spacing <= FmReceiver.FM_CHSPACE_50_KHZ)) {
            mFrequencyBand_Stepsize = 200;
            switch (spacing) {
                case FmReceiver.FM_CHSPACE_100_KHZ: {
                    mFrequencyBand_Stepsize = 100;
                    break;
                }
                case FmReceiver.FM_CHSPACE_50_KHZ: {
                    mFrequencyBand_Stepsize = 50;
                    break;
                }
            }
            mFMConfiguration.setChSpacing(spacing);
        }
    }

    public static int getBandStepSize() {
        switch (getChSpacing()) {
            case FmReceiver.FM_CHSPACE_100_KHZ:
                return 100;
            case FmReceiver.FM_CHSPACE_50_KHZ:
                return 50;
            default:
                return 200;
        }
    }

    public static int getChSpacing() {
        return mFMConfiguration.getChSpacing();
    }

    public static void setRdsStd(int std) {
        if ((std >= FmReceiver.FM_RDS_STD_RBDS) && (std <= FmReceiver.FM_RDS_STD_NONE)) {
            mFMConfiguration.setRdsStd(std);
        }
    }

    public static int getRdsStd() {
        return mFMConfiguration.getRdsStd();
    }

    /* North America */
    public static boolean isRDSStd() {
        return (FmReceiver.FM_RDS_STD_RDS == mFMConfiguration.getRdsStd());
    }

    public static boolean isRBDSStd() {
        return (FmReceiver.FM_RDS_STD_RBDS == mFMConfiguration.getRdsStd());
    }

    public static void setEmphasis(int emph) {
        if ((emph >= FmReceiver.FM_DE_EMP75) && (emph <= FmReceiver.FM_DE_EMP50)) {
            mFMConfiguration.setEmphasis(emph);
        }
    }

    public static int getEmphasis() {
        return mFMConfiguration.getEmphasis();
    }

    public static int getUpperLimit() {
        return mFMConfiguration.getUpperLimit();
    }

    public static int getLowerLimit() {
        return mFMConfiguration.getLowerLimit();
    }

    public static void setLowerLimit(int lowLimit) {
        mFMConfiguration.setLowerLimit(lowLimit);
    }

    public static void setUpperLimit(int upLimit) {
        mFMConfiguration.setUpperLimit(upLimit);
    }

    public static void setCountry(int nCountryCode) {

        // Default: 87500 TO 10800 IN 100 KHZ STEPS
        mFMConfiguration.setRadioBand(FmReceiver.FM_USER_DEFINED_BAND);
        mFMConfiguration.setChSpacing(FmReceiver.FM_CHSPACE_100_KHZ);
        mFMConfiguration.setEmphasis(FmReceiver.FM_DE_EMP50);
        mFMConfiguration.setRdsStd(FmReceiver.FM_RDS_STD_RDS);
        mFMConfiguration.setLowerLimit(87500);
        mFMConfiguration.setUpperLimit(108000);

        switch (nCountryCode) {
            case REGIONAL_BAND_NORTH_AMERICA: {
                // NORTH_AMERICA 87500 TO 108000 IN 200 KHZ STEPS
                mFMConfiguration.setRadioBand(FmReceiver.FM_US_BAND);
                mFMConfiguration.setChSpacing(FmReceiver.FM_CHSPACE_200_KHZ);
                mFMConfiguration.setEmphasis(FmReceiver.FM_DE_EMP75);
                mFMConfiguration.setRdsStd(FmReceiver.FM_RDS_STD_RBDS);
                /*
                 * Since the step size if 200K starting at 87500, 107900 is the
                 * maximum
                 */
                mFMConfiguration.setUpperLimit(107900);
                break;
            }
            case REGIONAL_BAND_EUROPE: {
                // EUROPE/Default: 87500 TO 10800 IN 100 KHZ STEPS
                mFMConfiguration.setRadioBand(FmReceiver.FM_EU_BAND);
                break;
            }

            case REGIONAL_BAND_JAPAN: {// - JAPAN 76000 TO 090000 IN 100 KHZ
                                       // STEPS

                mFMConfiguration.setChSpacing(FmReceiver.FM_CHSPACE_100_KHZ);
                mFMConfiguration.setEmphasis(FmReceiver.FM_DE_EMP75);
                mFMConfiguration.setLowerLimit(76000);
                mFMConfiguration.setUpperLimit(90000);
                break;
            }
            case REGIONAL_BAND_JAPAN_WIDE: {// - JAPAN_WB 090000 TO 108000 IN 50
                                            // KHZ STEPS
                mFMConfiguration.setChSpacing(FmReceiver.FM_CHSPACE_50_KHZ);
                mFMConfiguration.setEmphasis(FmReceiver.FM_DE_EMP75);
                mFMConfiguration.setLowerLimit(90000);
                mFMConfiguration.setUpperLimit(108000);
                break;
            }

                /* Country specific */
            case REGIONAL_BAND_AUSTRALIA: {
                // - AUSTRALIA 87700 TO 108000 IN 100 KHZ STEPS
                mFMConfiguration.setLowerLimit(87700);
                break;
            }
            case REGIONAL_BAND_AUSTRIA: {// - AUSTRIA 87500 TO 108000 IN 50 KHZ
                                         // STEPS
                mFMConfiguration.setEmphasis(FmReceiver.FM_DE_EMP50);
                break;
            }
            case REGIONAL_BAND_BELGIUM: {// - BELGIUM 87500 TO 108000 IN 100 KHZ
                                         // STEPS
                break;
            }
            case REGIONAL_BAND_BRAZIL: {// - BRAZIL 87800 TO 108000 IN 200 KHZ
                                        // STEP
                mFMConfiguration.setChSpacing(FmReceiver.FM_CHSPACE_200_KHZ);
                mFMConfiguration.setLowerLimit(87800);
                break;
            }
            case REGIONAL_BAND_CHINA: {// - CHINA 87000 TO 108000 IN 100 KHZ
                                       // STEPS
                mFMConfiguration.setLowerLimit(87000);
                break;
            }
            case REGIONAL_BAND_CZECH: {// - CZECH 87500 TO 108000 IN 100 KHZ
                                       // STEPS
                break;
            }
            case REGIONAL_BAND_DENMARK: {// - DENMARK 87500 TO 108000 IN 50 KHZ
                                         // STEPS
                mFMConfiguration.setChSpacing(FmReceiver.FM_CHSPACE_50_KHZ);
                break;
            }
            case REGIONAL_BAND_FINLAND: {// - FINLAND 87500 TO 108000 IN 100 KHZ
                                         // STEPS
                break;
            }
            case REGIONAL_BAND_FRANCE:
                // - FRANCE 87500 TO 108000 IN 50 KHZ STEPS
            case REGIONAL_BAND_GERMANY:
                // - GERMANY 87500 TO 108000 IN 50 KHZ STEPS
            case REGIONAL_BAND_GREECE:
                // - GREECE 87500 TO 108000 IN 50 KHZ STEPS
            {
                mFMConfiguration.setChSpacing(FmReceiver.FM_CHSPACE_50_KHZ);
                break;
            }
            case REGIONAL_BAND_HONGKONG: {// - HONG KONG 87500 TO 108000 IN 100
                                          // KHZ STEPS
                break;
            }
            case REGIONAL_BAND_INDIA: {// - INDIA 91000 TO 106400 IN 100 KHZ
                                       // STEPS
                mFMConfiguration.setLowerLimit(91000);
                mFMConfiguration.setUpperLimit(106400);
                break;
            }
            case REGIONAL_BAND_IRELAND: {// - IRELAND 87500 TO 108000 IN 50 KHZ
                                         // STEPS
                mFMConfiguration.setChSpacing(FmReceiver.FM_CHSPACE_50_KHZ);
                break;
            }
            case REGIONAL_BAND_ITALY: {// - ITALY 87500 TO 108000 IN 50 KHZ
                                       // STEPS
                mFMConfiguration.setChSpacing(FmReceiver.FM_CHSPACE_50_KHZ);
                break;
            }
            case REGIONAL_BAND_KOREA: {// - KOREA 87500 TO 108000 IN 200 KHZ
                                       // STEPS
                mFMConfiguration.setChSpacing(FmReceiver.FM_CHSPACE_200_KHZ);
                /*
                 * Since the step size if 200K starting at 87500, 107900 is the
                 * maximum
                 */
                mFMConfiguration.setUpperLimit(107900);
                break;
            }
            case REGIONAL_BAND_MEXICO: {// - MEXICO 88100 TO 107900 IN 200 KHZ
                                        // STEPS
                mFMConfiguration.setChSpacing(FmReceiver.FM_CHSPACE_200_KHZ);
                mFMConfiguration.setEmphasis(FmReceiver.FM_DE_EMP75);
                mFMConfiguration.setRdsStd(FmReceiver.FM_RDS_STD_RBDS);
                mFMConfiguration.setLowerLimit(88100);
                mFMConfiguration.setUpperLimit(107900);
                break;
            }
            case REGIONAL_BAND_NETHERLANDS: {// - NETHERLANDS 87500 TO 108000 IN
                                             // 100 KHZ STEPS

                break;
            }
            case REGIONAL_BAND_NEWZEALAND: {// - NEW ZEALAND 88000 TO 107000 IN
                                            // 100 KHZ STEPS
                mFMConfiguration.setLowerLimit(88000);
                mFMConfiguration.setUpperLimit(107000);
                break;
            }
            case REGIONAL_BAND_NORWAY: {// - NORWAY 87500 TO 108000 IN 100 KHZ
                                        // STEPS

                break;
            }
            case REGIONAL_BAND_POLAND: {// - POLAND 88000 TO 108000 IN 50 KHZ
                                        // STEPS
                mFMConfiguration.setChSpacing(FmReceiver.FM_CHSPACE_50_KHZ);
                mFMConfiguration.setLowerLimit(88000);
                break;
            }
            case REGIONAL_BAND_PORTUGAL: {// - PORTUGAL 87500 TO 108000 IN 50
                                          // KHZ STEPS
                mFMConfiguration.setChSpacing(FmReceiver.FM_CHSPACE_50_KHZ);
                break;
            }
            case REGIONAL_BAND_RUSSIA: {// - RUSSIA 87500 TO 108000 IN 100 KHZ
                                        // STEPS

                break;
            }
            case REGIONAL_BAND_SINGAPORE: {// - SINGAPORE 88000 TO 108000 IN 100
                                           // KHZ STEPS
                mFMConfiguration.setLowerLimit(88000);
                break;
            }
            case REGIONAL_BAND_SLOVAKIA: {// - SLOVAKIA 87500 TO 108000 IN 100
                                          // KHZ STEPS
                break;
            }
            case REGIONAL_BAND_SPAIN: {// - SPAIN 87500 TO 108000 IN 100 KHZ
                                       // STEPS

                break;
            }
            case REGIONAL_BAND_SWITZERLAND: {// - SWITZERLAND 87500 TO 108000 IN
                                             // 100 KHZ STEPS

                break;
            }
            case REGIONAL_BAND_SWEDEN: {// - SWEDEN 87500 TO 108000 IN 100 KHZ
                                        // STEPS

                break;
            }
            case REGIONAL_BAND_TAIWAN: {// - TAIWAN 87500 TO 108000 IN 100 KHZ
                                        // STEPS

                break;
            }
            case REGIONAL_BAND_TURKEY: {// - TURKEY 87500 TO 108000 IN 100 KHZ
                                        // STEPS

                break;
            }
            case REGIONAL_BAND_UNITEDKINGDOM: {// - UNITED KINGDOM 87500 TO
                                               // 108000 IN 100 KHZ STEPS
                break;
            }
            case REGIONAL_BAND_UNITED_STATES: {
                // - UNITED STATES 88100 TO 107900 IN 200 KHZ STEPS
                mFMConfiguration.setChSpacing(FmReceiver.FM_CHSPACE_200_KHZ);
                mFMConfiguration.setEmphasis(FmReceiver.FM_DE_EMP75);
                mFMConfiguration.setRdsStd(FmReceiver.FM_RDS_STD_RBDS);
                mFMConfiguration.setLowerLimit(88100);
                mFMConfiguration.setUpperLimit(107900);
                break;
            }
            default: {
                Log.d(LOGTAG, "Invalid: countryCode: " + nCountryCode);
                nCountryCode = 0;
            }
        }
        mCountry = nCountryCode;
        Log.d(LOGTAG, "=====================================================");
        Log.d(LOGTAG, "Country     :" + nCountryCode);
        Log.d(LOGTAG, "RadioBand   :" + mFMConfiguration.getRadioBand());
        Log.d(LOGTAG, "Emphasis    :" + mFMConfiguration.getEmphasis());
        Log.d(LOGTAG, "ChSpacing   :" + mFMConfiguration.getChSpacing());
        Log.d(LOGTAG, "RdsStd      :" + mFMConfiguration.getRdsStd());
        Log.d(LOGTAG, "LowerLimit  :" + mFMConfiguration.getLowerLimit());
        Log.d(LOGTAG, "UpperLimit  :" + mFMConfiguration.getUpperLimit());
        Log.d(LOGTAG, "=====================================================");
    }

    public static int getCountry() {
        return mCountry;
    }

    public static void setSpeaker(boolean speakerOn) {
        mSpeaker = speakerOn;
    }

    public static boolean getSpeaker() {
        return mSpeaker;
    }

    public static void setAudioOutputMode(boolean bStereo) {
        mAudioOutputMode = bStereo;
    }

    public static boolean getAudioOutputMode() {
        return mAudioOutputMode;
    }

    public static void setRecordDuration(int duration) {
        mRecordDuration = duration;
    }

    public static int getRecordDuration() {
        return mRecordDuration;
    }

    public static void setAutoAFSwitch(boolean bAFAutoSwitch) {
        mAFAutoSwitch = bAFAutoSwitch;
    }

    public static boolean getAutoAFSwitch() {
        return mAFAutoSwitch;
    }

    public static void setBluetoothExitBehaviour(int behaviour) {
        mBluetoothExitBehaviour = behaviour;
    }

    public static int getBluetoothExitBehaviour() {
        return mBluetoothExitBehaviour;
    }

    public static void setHeadsetDcBehaviour(boolean behaviour) {
        mHeadsetRemovalBehaviour = behaviour;
    }

    public static boolean getHeadsetDcBehaviour() {
        return mHeadsetRemovalBehaviour;
    }

}
