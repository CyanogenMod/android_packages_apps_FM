
package com.android.fm.radio;

import java.util.ArrayList;
import java.util.List;

public class PresetList {
    private List<PresetStation> mPresetList = new ArrayList<PresetStation>();

    private int mCurrentStation = 0;

    private String mListName = "";

    public PresetList(String name) {
        mListName = name;
    }

    public String getName() {
        return mListName;
    }

    public String toString() {
        return mListName;
    }

    public synchronized int getStationCount() {
        return mPresetList.size();
    }

    public synchronized String getStationName(int stationNum) {
        String name = "";
        if (mPresetList.size() > stationNum) {
            name = mPresetList.get(stationNum).getName();
        }
        return name;
    }

    public synchronized int getStationFrequency(int stationNum) {
        int frequency = 102100;
        if (mPresetList.size() > stationNum) {
            frequency = mPresetList.get(stationNum).getFrequency();
        }
        return frequency;
    }

    public void setName(String name) {
        mListName = name;
    }

    public synchronized void setStationFrequency(int stationNum, int frequency) {
        PresetStation mStation = mPresetList.get(stationNum);
        mStation.setFrequency(frequency);
    }

    public synchronized void setStationName(int stationNum, String name) {
        PresetStation mStation = mPresetList.get(stationNum);
        mStation.setName(name);
    }

    public synchronized PresetStation getStationFromIndex(int index) {
        int totalPresets = mPresetList.size();
        PresetStation station = null;
        if (index < totalPresets) {
            station = mPresetList.get(index);
        }
        return station;
    }

    public synchronized PresetStation getStationFromFrequency(int frequency) {
        int totalPresets = mPresetList.size();
        for (int presetNum = 0; presetNum < totalPresets; presetNum++) {
            PresetStation station = mPresetList.get(presetNum);
            if (station != null) {
                if (frequency == station.getFrequency()) {
                    return station;
                }
            }
        }
        return null;
    }

    public synchronized PresetStation addStation(String name, int freq) {
        PresetStation addStation = new PresetStation(name, freq);
        if (addStation != null) {
            mPresetList.add(addStation);
        }
        return addStation;
    }

    public synchronized PresetStation addStation(PresetStation station) {
        PresetStation addStation = null;
        if (station != null) {
            addStation = new PresetStation(station);
            mPresetList.add(addStation);
        }
        return addStation;
    }

    public synchronized void removeStation(int index) {
        int totalPresets = mPresetList.size();
        if (index < totalPresets) {
            mPresetList.remove(index);
        }
    }

    public synchronized void removeStation(PresetStation station) {
        int index = mPresetList.indexOf(station);
        int totalPresets = mPresetList.size();
        if (index < totalPresets) {
            mPresetList.remove(index);
        }
    }

    public synchronized void clear() {
        mPresetList.clear();
    }

    /*
     * If a user selects a new station in this list, this routine will be called
     * to to update the list.
     */
    public synchronized boolean setSelectedStation(PresetStation selectStation) {
        int totalPresets = mPresetList.size();
        if (selectStation != null) {
            for (int presetNum = 0; presetNum < totalPresets; presetNum++) {
                PresetStation station = mPresetList.get(presetNum);
                if (station != null) {
                    if (selectStation.getFrequency() == station.getFrequency()) {
                        if (selectStation.getName().equalsIgnoreCase(station.getName())) {
                            mCurrentStation = presetNum;
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /*
     * Check if the same station already exists in a List to update the list.
     */
    public synchronized boolean sameStationExists(PresetStation compareStation) {
        int totalPresets = mPresetList.size();
        if (compareStation != null) {
            for (int presetNum = 0; presetNum < totalPresets; presetNum++) {
                PresetStation station = mPresetList.get(presetNum);
                if (station != null) {
                    if (compareStation.getFrequency() == station.getFrequency()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /*
     * If a user selects a new station in this list, this routine will be called
     * to to update the list.
     */
    public synchronized boolean setSelectedStation(int stationIndex) {
        boolean foundStation = false;
        int totalPresets = mPresetList.size();
        if (stationIndex < totalPresets) {
            mCurrentStation = stationIndex;
            foundStation = true;
        }
        return foundStation;
    }

    public synchronized PresetStation getSelectedStation() {
        int totalPresets = mPresetList.size();
        PresetStation station = null;
        if (mCurrentStation < totalPresets) {
            station = mPresetList.get(mCurrentStation);
        }
        return station;
    }

    public synchronized PresetStation selectNextStation() {
        int totalPresets = mPresetList.size();
        PresetStation station = null;
        if (totalPresets > 0) {
            mCurrentStation++;
            if ((mCurrentStation) >= totalPresets) {
                mCurrentStation = 0;
            }
            station = mPresetList.get(mCurrentStation);
        }
        return station;
    }

    public synchronized PresetStation selectPrevStation() {
        int totalPresets = mPresetList.size();
        PresetStation station = null;
        if (totalPresets > 0) {
            mCurrentStation--;
            if (mCurrentStation < 0) {
                mCurrentStation = totalPresets - 1;
            }
            station = mPresetList.get(mCurrentStation);
        }
        return station;
    }

    public synchronized void selectStation(PresetStation selectStation) {
        int totalPresets = mPresetList.size();
        if (selectStation != null) {
            for (int presetNum = 0; presetNum < totalPresets; presetNum++) {
                PresetStation station = mPresetList.get(presetNum);
                if (station != null) {
                    if (selectStation.getFrequency() == station.getFrequency()) {
                        mCurrentStation = presetNum;
                        return;
                    }
                }
            }
        }
    }

}
