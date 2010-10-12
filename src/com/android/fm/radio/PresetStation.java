
package com.android.fm.radio;

import android.hardware.fmradio.FmReceiver;
import android.text.TextUtils;

public class PresetStation {
    private String mName = "";

    private int mFrequency = 102100;

    private int mPty = 0;

    private int mPI = 0;

    private String mPtyStr = "";

    private String mPIStr = "";

    private boolean mRDSSupported = false;

    public PresetStation(String name, int frequency) {
        mName = name;
        /*
         * setFrequency set the name to "Frequency" String if Name is empty
         */
        setFrequency(frequency);
    }

    public PresetStation(PresetStation station) {
        Copy(station);
        /*
         * setFrequency set the name to "Frequency" String if Name is empty
         */
        setFrequency(station.getFrequency());
    }

    public void Copy(PresetStation station) {
        /*
         * Let copy just do a copy without any manipulation
         */
        mName = station.getName();
        mFrequency = station.getFrequency();
        mPI = station.getPI();
        mPty = station.getPty();
        mRDSSupported = station.getRDSSupported();

        mPtyStr = station.getPtyString();
        mPIStr = station.getPIString();
    }

    public boolean equals(PresetStation station) {
        boolean equal = false;
        if (mFrequency == station.getFrequency()) {
            if (mPty == (station.getPty())) {
                if (mPI == (station.getPI())) {
                    if (mRDSSupported == (station.getRDSSupported())) {
                        equal = true;
                    }
                }
            }
        }
        return equal;
    }

    public void setName(String name) {
        if (!TextUtils.isEmpty(name)) {
            mName = name;
        } else {
            mName = "" + mFrequency / 1000.0;
        }
    }

    public void setFrequency(int freq) {
        mFrequency = freq;
        /* If no name set it to the frequency */
        if (TextUtils.isEmpty(mName)) {
            mName = "" + mFrequency / 1000.0;
        }
        return;
    }

    public void setPty(int pty) {
        mPty = pty;
        mPtyStr = PresetStation.parsePTY(mPty);
    }

    public void setPI(int pi) {
        mPI = pi;
        mPIStr = PresetStation.parsePI(mPI);
    }

    public void setRDSSupported(boolean rds) {
        mRDSSupported = rds;
    }

    public String getName() {
        return mName;
    }

    public int getFrequency() {
        return mFrequency;
    }

    /**
     * Routine to get the Frequency in String from an integer
     *
     * @param frequency : Frequency to be converted (ex: 96500)
     * @return String : Frequency in String form (ex: 96.5)
     */
    public static String getFrequencyString(int frequency) {
        double frequencyDbl = frequency / 1000.0;
        String frequencyString = "" + frequencyDbl;
        return frequencyString;
    }

    public int getPty() {
        return mPty;
    }

    public String getPtyString() {
        return mPtyStr;
    }

    public int getPI() {
        return mPI;
    }

    public String getPIString() {
        return mPIStr;
    }

    public boolean getRDSSupported() {
        return mRDSSupported;
    }

    /**
     * Routine parses the PI Code from Integer to Call Sign String Example:
     * 0x54A6 -> KZZY
     */
    public static String parsePI(int piCode) {
        String callSign = "";
        if ((piCode >> 8) == 0xAF) {// CALL LETTERS THAT MAP TO PI CODES = _ _ 0
                                    // 0.
            piCode = ((piCode & 0xFF) << 8);
        }
        /*
         * Run the second exception NOTE: For 9 special cases 1000,2000,..,9000
         * a double mapping occurs utilizing exceptions 1 and 2:
         * 1000->A100->AFA1;2000->A200->AFA2; ... ;
         * 8000->A800->AFA8;9000->A900->AFA9
         */
        if ((piCode >> 12) == 0xA) {// CALL LETTERS THAT MAP TO PI CODES = _ 0 _
                                    // _.
            piCode = ((piCode & 0xF00) << 4) + (piCode & 0xFF);
        }
        if ((piCode >= 0x1000) && (piCode <= 0x994E)) {
            String ShartChar;
            /* KAAA - KZZZ */
            if ((piCode >= 0x1000) && (piCode <= 0x54A7)) {
                piCode -= 0x1000;
                ShartChar = "K";
            } else { /* WAAA - WZZZ */
                piCode -= 0x54A8;
                ShartChar = "W";
            }
            int CharDiv = piCode / 26;
            int CharPos = piCode - (CharDiv * 26);
            char c3 = (char) ('A' + CharPos);

            piCode = CharDiv;
            CharDiv = piCode / 26;
            CharPos = piCode - (CharDiv * 26);
            char c2 = (char) ('A' + CharPos);

            piCode = CharDiv;
            CharDiv = piCode / 26;
            CharPos = piCode - (CharDiv * 26);
            char c1 = (char) ('A' + CharPos);
            callSign = ShartChar + c1 + c2 + c3;
        } else if ((piCode >= 0x9950) && (piCode <= 0x9EFF)) {// 3-LETTER-ONLY
                                                              // CALL LETTERS
            callSign = get3LetterCallSign(piCode);
        } else {// NATIONALLY-LINKED RADIO STATIONS CARRYING DIFFERENT CALL
                // LETTERS
            callSign = getOtherCallSign(piCode);
        }
        return callSign;
    }

    private static String getOtherCallSign(int piCode) {
        String callSign = "";
        if ((piCode >= 0xB001) && (piCode <= 0xBF01)) {
            callSign = "NPR";
        } else if ((piCode >= 0xB002) && (piCode <= 0xBF02)) {
            callSign = "CBC English";
        } else if ((piCode >= 0xB003) && (piCode <= 0xBF03)) {
            callSign = "CBC French";
        }
        return callSign;
    }

    private static String get3LetterCallSign(int piCode) {
        String callSign = "";
        switch (piCode) {
            case 0x99A5: {
                callSign = "KBW";
                break;
            }
            case 0x9992: {
                callSign = "KOY";
                break;
            }
            case 0x9978: {
                callSign = "WHO";
                break;
            }
            case 0x99A6: {
                callSign = "KCY";
                break;
            }
            case 0x9993: {
                callSign = "KPQ";
                break;
            }
            case 0x999C: {
                callSign = "WHP";
                break;
            }
            case 0x9990: {
                callSign = "KDB";
                break;
            }
            case 0x9964: {
                callSign = "KQV";
                break;
            }
            case 0x999D: {
                callSign = "WIL";
                break;
            }
            case 0x99A7: {
                callSign = "KDF";
                break;
            }
            case 0x9994: {
                callSign = "KSD";
                break;
            }
            case 0x997A: {
                callSign = "WIP";
                break;
            }
            case 0x9950: {
                callSign = "KEX";
                break;
            }
            case 0x9965: {
                callSign = "KSL";
                break;
            }
            case 0x99B3: {
                callSign = "WIS";
                break;
            }
            case 0x9951: {
                callSign = "KFH";
                break;
            }
            case 0x9966: {
                callSign = "KUJ";
                break;
            }
            case 0x997B: {
                callSign = "WJR";
                break;
            }
            case 0x9952: {
                callSign = "KFI";
                break;
            }
            case 0x9995: {
                callSign = "KUT";
                break;
            }
            case 0x99B4: {
                callSign = "WJW";
                break;
            }
            case 0x9953: {
                callSign = "KGA";
                break;
            }
            case 0x9967: {
                callSign = "KVI";
                break;
            }
            case 0x99B5: {
                callSign = "WJZ";
                break;
            }
            case 0x9991: {
                callSign = "KGB";
                break;
            }
            case 0x9968: {
                callSign = "KWG";
                break;
            }
            case 0x997C: {
                callSign = "WKY";
                break;
            }
            case 0x9954: {
                callSign = "KGO";
                break;
            }
            case 0x9996: {
                callSign = "KXL";
                break;
            }
            case 0x997D: {
                callSign = "WLS";
                break;
            }
            case 0x9955: {
                callSign = "KGU";
                break;
            }
            case 0x9997: {
                callSign = "KXO";
                break;
            }
            case 0x997E: {
                callSign = "WLW";
                break;
            }
            case 0x9956: {
                callSign = "KGW";
                break;
            }
            case 0x996B: {
                callSign = "KYW";
                break;
            }
            case 0x999E: {
                callSign = "WMC";
                break;
            }
            case 0x9957: {
                callSign = "KGY";
                break;
            }
            case 0x9999: {
                callSign = "WBT";
                break;
            }
            case 0x999F: {
                callSign = "WMT";
                break;
            }
            case 0x99AA: {
                callSign = "KHQ";
                break;
            }
            case 0x996D: {
                callSign = "WBZ";
                break;
            }
            case 0x9981: {
                callSign = "WOC";
                break;
            }
            case 0x9958: {
                callSign = "KID";
                break;
            }
            case 0x996E: {
                callSign = "WDZ";
                break;
            }
            case 0x99A0: {
                callSign = "WOI";
                break;
            }
            case 0x9959: {
                callSign = "KIT";
                break;
            }
            case 0x996F: {
                callSign = "WEW";
                break;
            }
            case 0x9983: {
                callSign = "WOL";
                break;
            }
            case 0x995A: {
                callSign = "KJR";
                break;
            }
            case 0x999A: {
                callSign = "WGH";
                break;
            }
            case 0x9984: {
                callSign = "WOR";
                break;
            }
            case 0x995B: {
                callSign = "KLO";
                break;
            }
            case 0x9971: {
                callSign = "WGL";
                break;
            }
            case 0x99A1: {
                callSign = "WOW";
                break;
            }
            case 0x995C: {
                callSign = "KLZ";
                break;
            }
            case 0x9972: {
                callSign = "WGN";
                break;
            }
            case 0x99B9: {
                callSign = "WRC";
                break;
            }
            case 0x995D: {
                callSign = "KMA";
                break;
            }
            case 0x9973: {
                callSign = "WGR";
                break;
            }
            case 0x99A2: {
                callSign = "WRR";
                break;
            }
            case 0x995E: {
                callSign = "KMJ";
                break;
            }
            case 0x999B: {
                callSign = "WGY";
                break;
            }
            case 0x99A3: {
                callSign = "WSB";
                break;
            }
            case 0x995F: {
                callSign = "KNX";
                break;
            }
            case 0x9975: {
                callSign = "WHA";
                break;
            }
            case 0x99A4: {
                callSign = "WSM";
                break;
            }
            case 0x9960: {
                callSign = "KOA";
                break;
            }
            case 0x9976: {
                callSign = "WHB";
                break;
            }
            case 0x9988: {
                callSign = "WWJ";
                break;
            }
            case 0x99AB: {
                callSign = "KOB";
                break;
            }
            case 0x9977: {
                callSign = "WHK";
                break;
            }
            case 0x9989: {
                callSign = "WWL";
                break;
            }
        }
        return callSign;
    }

    /**
     * Get the Text String for the Program type Code
     */
    public static String parsePTY(int pty) {
        String ptyStr = "";
        int rdsStd = FmSharedPreferences.getFMConfiguration().getRdsStd();
        if (rdsStd == FmReceiver.FM_RDS_STD_RBDS) {
            ptyStr = getRBDSPtyString(pty);
        } else if (rdsStd == FmReceiver.FM_RDS_STD_RDS) {
            ptyStr = getRDSPtyString(pty);
        }
        return (ptyStr);
    }

    /**
     * get the Text String for the RBDS Program type Code
     */
    public static String getRBDSPtyString(int pty) {
        String ptyStr = "";
        switch (pty) {
            case 1: {
                ptyStr = "News";
                break;
            }
            case 2: {
                ptyStr = "Information";
                break;
            }
            case 3: {
                ptyStr = "Sports";
                break;
            }
            case 4: {
                ptyStr = "Talk";
                break;
            }
            case 5: {
                ptyStr = "Rock";
                break;
            }
            case 6: {
                ptyStr = "Classic Rock";
                break;
            }
            case 7: {
                ptyStr = "Adult Hits";
                break;
            }
            case 8: {
                ptyStr = "Soft Rock";
                break;
            }
            case 9: {
                ptyStr = "Top 40";
                break;
            }
            case 10: {
                ptyStr = "Country";
                break;
            }
            case 11: {
                ptyStr = "Oldies";
                break;
            }
            case 12: {
                ptyStr = "Soft";
                break;
            }
            case 13: {
                ptyStr = "Nostalgia";
                break;
            }
            case 14: {
                ptyStr = "Jazz";
                break;
            }
            case 15: {
                ptyStr = "Classical";
                break;
            }
            case 16: {
                ptyStr = "Rhythm and Blues";
                break;
            }
            case 17: {
                ptyStr = "Soft Rhythm and Blues";
                break;
            }
            case 18: {
                ptyStr = "Foreign Language";
                break;
            }
            case 19: {
                ptyStr = "Religious Music";
                break;
            }
            case 20: {
                ptyStr = "Religious Talk";
                break;
            }
            case 21: {
                ptyStr = "Personality";
                break;
            }
            case 22: {
                ptyStr = "Public";
                break;
            }
            case 23: {
                ptyStr = "College";
                break;
            }
            case 29: {
                ptyStr = "Weather";
                break;
            }
            case 30: {
                ptyStr = "Emergency Test";
                break;
            }
            case 31: {
                ptyStr = "Emergency";
                break;
            }
            default: {
                ptyStr = "";
                // Log.e(FMRadio.LOGTAG, "Unknown RBDS ProgramType [" + pty +
                // "]");
                break;
            }
        }
        return ptyStr;
    }

    /** get the Text String for the Program type Code */
    public static String getRDSPtyString(int pty) {
        String ptyStr = "";
        switch (pty) {
            case 1: {
                ptyStr = "News";
                break;
            }
            case 2: {
                ptyStr = "Current Affairs";
                break;
            }
            case 3: {
                ptyStr = "Information";
                break;
            }
            case 4: {
                ptyStr = "Sport";
                break;
            }
            case 5: {
                ptyStr = "Education";
                break;
            }
            case 6: {
                ptyStr = "Drama";
                break;
            }
            case 7: {
                ptyStr = "Culture";
                break;
            }
            case 8: {
                ptyStr = "Science";
                break;
            }
            case 9: {
                ptyStr = "Varied";
                break;
            }
            case 10: {
                ptyStr = "Pop Music";
                break;
            }
            case 11: {
                ptyStr = "Rock Music";
                break;
            }
            case 12: {
                ptyStr = "Easy Listening Music";
                break;
            }
            case 13: {
                ptyStr = "Light classical";
                break;
            }
            case 14: {
                ptyStr = "Serious classical";
                break;
            }
            case 15: {
                ptyStr = "Other Music";
                break;
            }
            case 16: {
                ptyStr = "Weather";
                break;
            }
            case 17: {
                ptyStr = "Finance";
                break;
            }
            case 18: {
                ptyStr = "Children programs";
                break;
            }
            case 19: {
                ptyStr = "Social Affairs";
                break;
            }
            case 20: {
                ptyStr = "Religion";
                break;
            }
            case 21: {
                ptyStr = "Phone In";
                break;
            }
            case 22: {
                ptyStr = "Travel";
                break;
            }
            case 23: {
                ptyStr = "Leisure";
                break;
            }
            case 24: {
                ptyStr = "Jazz Music";
                break;
            }
            case 25: {
                ptyStr = "Country Music";
                break;
            }
            case 26: {
                ptyStr = "National Music";
                break;
            }
            case 27: {
                ptyStr = "Oldies Music";
                break;
            }
            case 28: {
                ptyStr = "Folk Music";
                break;
            }
            case 29: {
                ptyStr = "Documentary";
                break;
            }
            case 30: {
                ptyStr = "Emergency Test";
                break;
            }
            case 31: {
                ptyStr = "Emergency";
                break;
            }
            default: {
                ptyStr = "";
                // Log.e(FMRadio.LOGTAG, "Unknown RDS ProgramType [" + pty +
                // "]");
                break;
            }
        }
        return ptyStr;
    }

}
