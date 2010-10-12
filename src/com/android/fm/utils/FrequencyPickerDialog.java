
package com.android.fm.utils;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.hardware.fmradio.FmConfig;
import android.hardware.fmradio.FmReceiver;

import com.android.fm.R;
import com.android.fm.radio.FmSharedPreferences;
import com.android.fm.utils.FrequencyPicker.OnFrequencyChangedListener;

/**
 * A simple dialog containing an FrequencyPicker.
 */
public class FrequencyPickerDialog extends AlertDialog implements OnClickListener,
        OnFrequencyChangedListener {

    private static final String FREQUENCY = "FREQUENCY";

    private static final String FREQ_MIN = "FREQ_MIN";

    private static final String FREQ_MAX = "FREQ_MAX";

    private static final String FREQ_STEP = "FREQ_STEP";

    private final FrequencyPicker mFrequencyPicker;

    private final OnFrequencySetListener mCallBack;

    private int mMinFrequency;

    private int mMaxFrequency;

    private int mChannelSpacing;

    /**
     * The callback used to indicate the user is done filling in the date.
     */
    public interface OnFrequencySetListener {

        void onFrequencySet(FrequencyPicker view, int frequency);
    }

    /**
     */
    public FrequencyPickerDialog(Context context, FmConfig fmConfig, int frequency,
            OnFrequencySetListener callback) {
        // this(context, android.R.style.Theme_Dialog, fmConfig, frequency,
        // callback);
        this(context, com.android.internal.R.style.Theme_Dialog_Alert, fmConfig, frequency,
                callback);
    }

    /**
     */
    public FrequencyPickerDialog(Context context, int theme, FmConfig fmConfig, int frequency,
            OnFrequencySetListener callback) {
        super(context, theme);
        mMinFrequency = fmConfig.getLowerLimit();
        mMaxFrequency = fmConfig.getUpperLimit();
        mChannelSpacing = 200;
        if (FmReceiver.FM_CHSPACE_200_KHZ == fmConfig.getChSpacing()) {
            mChannelSpacing = 200;
        } else if (FmReceiver.FM_CHSPACE_100_KHZ == fmConfig.getChSpacing()) {
            mChannelSpacing = 100;
        } else if (FmReceiver.FM_CHSPACE_50_KHZ == fmConfig.getChSpacing()) {
            mChannelSpacing = 50;
        }
        String frequencyString = getContext().getString(R.string.stat_notif_frequency,
                FrequencyPicker.formatFrequencyString(frequency));
        setTitle(frequencyString);
        mCallBack = callback;

        setButton(context.getString(R.string.alert_dialog_ok), this);
        setButton2(context.getString(R.string.alert_dialog_cancel), (OnClickListener) null);

        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.frequency_picker_dialog, null);
        setView(view);
        mFrequencyPicker = (FrequencyPicker) view.findViewById(R.id.frequencyPicker);
        if (mFrequencyPicker != null) {
            mFrequencyPicker.init(mMinFrequency, mMaxFrequency, mChannelSpacing, frequency, this);
        } else {
            Log.e("fmRadio", "Failed to find ID: R.id.frequencyPicker");
        }
    }

    public void UpdateFrequency(int frequency) {
        String frequencyString = getContext().getString(R.string.stat_notif_frequency,
                FrequencyPicker.formatFrequencyString(frequency));
        setTitle(frequencyString);
        mFrequencyPicker.updateFrequency(frequency);
    }

    public void onClick(DialogInterface dialog, int which) {
        if (mCallBack != null) {
            mFrequencyPicker.clearFocus();
            int frequency = mFrequencyPicker.getFrequency();
            mCallBack.onFrequencySet(mFrequencyPicker, frequency);
        }
    }

    public void onFrequencyChanged(FrequencyPicker view, int frequency) {
        UpdateFrequency(frequency);
    }

    @Override
    public Bundle onSaveInstanceState() {
        Bundle state = super.onSaveInstanceState();
        state.putInt(FREQUENCY, mFrequencyPicker.getFrequency());
        state.putInt(FREQ_MIN, mMinFrequency);
        state.putInt(FREQ_MAX, mMaxFrequency);
        state.putInt(FREQ_STEP, mChannelSpacing);
        return state;
    }

    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        int frequency = savedInstanceState.getInt(FREQUENCY);
        mMinFrequency = savedInstanceState.getInt(FREQ_MIN);
        mMaxFrequency = savedInstanceState.getInt(FREQ_MAX);
        mChannelSpacing = savedInstanceState.getInt(FREQ_STEP);
        mFrequencyPicker.init(mMinFrequency, mMaxFrequency, mChannelSpacing, frequency, this);
        String frequencyString = getContext().getString(R.string.stat_notif_frequency,
                FrequencyPicker.formatFrequencyString(frequency));
        setTitle(frequencyString);
    }
}
