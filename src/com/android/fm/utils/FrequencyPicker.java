
package com.android.fm.utils;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.widget.FrameLayout;

import com.android.fm.R;
import android.widget.NumberPicker;
import android.widget.NumberPicker.OnChangedListener;

import java.text.NumberFormat;
import java.text.DecimalFormat;

/**
 * A view for selecting the frequency For a dialog using this view, see
 * {FrequencyPickerDialog}.
 */

public class FrequencyPicker extends FrameLayout {

    /* UI Components */
    private final NumberPicker mMHzPicker;

    private final NumberPicker mKHzPicker;

    /**
     * How we notify users the Frequency has changed.
     */
    private OnFrequencyChangedListener mOnFrequencyChangedListener;

    private int mFrequency;

    private int mMin;

    private int mMax;

    private int mStep;

    private int mMhz;

    private int mKhz;

    private static final NumberFormat sFrequencyFormat = NumberFormat.getInstance();

    /**
     * The callback used to indicate the user changes the Frequency.
     */
    public interface OnFrequencyChangedListener {

        /**
         * @param view The view associated with this listener.
         * @param frequency The Frequency that was set.
         */
        void onFrequencyChanged(FrequencyPicker view, int frequency);
    }

    static {
        if (sFrequencyFormat instanceof DecimalFormat) {
            ((DecimalFormat)sFrequencyFormat).setDecimalSeparatorAlwaysShown(true);
            ((DecimalFormat)sFrequencyFormat).setMinimumFractionDigits(1);
            ((DecimalFormat)sFrequencyFormat).setMaximumFractionDigits(1);
        }
    }

    public FrequencyPicker(Context context) {
        this(context, null);
    }

    public FrequencyPicker(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FrequencyPicker(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.frequency_picker, this, true);

        mMHzPicker = (NumberPicker) findViewById(R.id.mhz);
        mMHzPicker.setSpeed(100);

        mMHzPicker.setOnChangeListener(new OnChangedListener() {
            public void onChanged(NumberPicker picker, int oldVal, int newVal) {
                mMhz = newVal;
                mFrequency = (mMhz * 1000) + (getFrequencyKHz(mKhz, mMin, mStep));
                validateFrequencyRange();
                if (mOnFrequencyChangedListener != null) {
                    mOnFrequencyChangedListener
                            .onFrequencyChanged(FrequencyPicker.this, mFrequency);
                }
            }
        });
        mKHzPicker = (NumberPicker) findViewById(R.id.khz);
        mKHzPicker.setSpeed(100);
        mKHzPicker.setOnChangeListener(new OnChangedListener() {
            public void onChanged(NumberPicker picker, int oldVal, int newVal) {
                mKhz = newVal;
                mFrequency = (mMhz * 1000) + (getFrequencyKHz(mKhz, mMin, mStep));

                validateFrequencyRange();

                if (mOnFrequencyChangedListener != null) {
                    mOnFrequencyChangedListener
                            .onFrequencyChanged(FrequencyPicker.this, mFrequency);
                }
            }
        });

        updateSpinnerRange();

        if (!isEnabled()) {
            setEnabled(false);
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        mMHzPicker.setEnabled(enabled);
        mKHzPicker.setEnabled(enabled);
    }

    public void updateFrequency(int frequency) {
        mFrequency = frequency;
        updateSpinners();
    }

    private static class SavedState extends BaseSavedState {

        private final int mMHZ;

        private final int mKHZ;

        /**
         * Constructor called from {@link FrequencyPicker#onSaveInstanceState()}
         */
        private SavedState(Parcelable superState, int mhz, int khz) {
            super(superState);
            mMHZ = mhz;
            mKHZ = khz;
        }

        /**
         * Constructor called from {@link #CREATOR}
         */
        private SavedState(Parcel in) {
            super(in);
            mMHZ = in.readInt();
            mKHZ = in.readInt();
        }

        public int getMHz() {
            return mMHZ;
        }

        public int getKHz() {
            return mKHZ;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(mMHZ);
            dest.writeInt(mKHZ);
        }

        public static final Parcelable.Creator<SavedState> CREATOR = new Creator<SavedState>() {

            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    /**
     * Override so we are in complete control of save / restore for this widget.
     */
    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        dispatchThawSelfOnly(container);
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();

        return new SavedState(superState, mMhz, mKhz);
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        mMhz = ss.getMHz();
        mKhz = ss.getKHz();
    }

    private String[] getKHzStrings(int min, int stepSize) {
        if (stepSize == 100) {
            return (get100KHzStrings());
        } else if (stepSize == 50) {
            return (get50KHzStrings());
        }
        return (get200KHzStrings(min));
    }

    private int getKHzCount(int stepSize) {
        if (stepSize == 100) {
            return (10);
        } else if (stepSize == 50) {
            return (20);
        }
        return (5);
    }

    private int getCurrentKHz(int frequency, int min, int stepSize) {
        if (stepSize == 100) {
            return (getCurrent100KHz(frequency));
        } else if (stepSize == 50) {
            return (getCurrent50KHz(frequency));
        }
        return (getCurrent200KHz(frequency, min));
    }

    private int getFrequencyKHz(int kHz, int min, int stepSize) {
        if (stepSize == 100) {
            return (getFrequency100KHz(kHz));
        } else if (stepSize == 50) {
            return (getFrequency50KHz(kHz));
        }
        return (getFrequency200KHz(kHz, min));
    }

    private int getFrequency100KHz(int kHz) {
        int frequencykhz = ((kHz - 1) * 100);
        // Log.d("FMRadio", "FP: getCurrent100KHz: " + frequencykhz);
        return (frequencykhz);
    }

    private int getFrequency50KHz(int kHz) {
        int frequencykhz = ((kHz - 1) * 50);
        // Log.d("FMRadio", "FP: getCurrent100KHz: " + frequencykhz);
        return (frequencykhz);
    }

    private int getFrequency200KHz(int kHz, int min) {
        int frequencykhz = ((kHz - 1) * 200);
        if (min % 200 != 0) {
            frequencykhz = ((kHz - 1) * 200) + 100;
        }
        // Log.d("FMRadio", "FP: getCurrent200KHz: " + frequencykhz);
        return (frequencykhz);
    }

    private int getCurrent100KHz(int frequency) {
        int khz = ((frequency % 1000) / 100);
        // Log.d("FMRadio", "FP: getCurrent100KHz: " + khz);
        return (khz + 1);
    }

    private int getCurrent50KHz(int frequency) {
        int khz = ((frequency % 1000) / 50);
        // Log.d("FMRadio", "FP: getCurrent50KHz: " + khz);
        return (khz + 1);
    }

    private int getCurrent200KHz(int frequency, int min) {
        int khz = ((frequency % 1000) / 200);
        // Log.d("FMRadio", "FP: getCurrent200KHz: " + khz);
        return (khz + 1);
    }

    private String[] get50KHzStrings() {
        String[] khzStrings = {
                "00", "05", "10", "15", "20", "25", "30", "35", "40", "45", "50", "55", "60", "65",
                "70", "75", "80", "85", "90", "95"
        };
        // Log.d("FMRadio", "FP: get50KHzStrings");
        return khzStrings;
    }

    private String[] get100KHzStrings() {
        String[] khzStrings = {
                "0", "1", "2", "3", "4", "5", "6", "7", "8", "9"
        };
        // Log.d("FMRadio", "FP: get100KHzStrings");
        return khzStrings;
    }

    private String[] get200KHzStrings(int min) {
        if (min % 200 == 0) {
            return (getEven200KHzStrings());
        }
        return (getOdd200KHzStrings());
    }

    private String[] getEven200KHzStrings() {
        String[] khzStrings = {
                "0", "2", "4", "6", "8"
        };
        // Log.d("FMRadio", "FP: getEven200KHzStrings");
        return khzStrings;
    }

    private String[] getOdd200KHzStrings() {
        String[] khzStrings = {
                "1", "3", "5", "7", "9"
        };
        // Log.d("FMRadio", "FP: getOdd200KHzStrings");
        return khzStrings;
    }

    /**
     * Initialize the state.
     *
     * @param year The initial year.
     * @param monthOfYear The initial month.
     * @param dayOfMonth The initial day of the month.
     * @param onDateChangedListener How user is notified date is changed by
     *            user, can be null.
     */
    public void init(int min, int max, int step, int frequency,
            OnFrequencyChangedListener onFrequencyChangedListener) {

        mMin = min;
        mMax = max;
        mStep = step;
        mFrequency = frequency;
        mOnFrequencyChangedListener = onFrequencyChangedListener;

        updateSpinners();
    }

    private void updateSpinnerRange() {
        String[] khzStrings = getKHzStrings(mMin, mStep);
        int khzNumSteps = getKHzCount(mStep);

        mMHzPicker.setRange(mMin / 1000, mMax / 1000);
        mKHzPicker.setRange(1, khzNumSteps, khzStrings);
    }

    private void updateSpinners() {
        int khzNumSteps = getKHzCount(mStep);
        updateSpinnerRange();
        mMhz = (int) (mFrequency / 1000);
        mKhz = getCurrentKHz(mFrequency, mMin, mStep);
        if ((mMin / 1000 <= mMhz) && (mMax / 1000 >= mMhz)) {
            mMHzPicker.setCurrent(mMhz);
        }
        if (mKhz <= khzNumSteps) {
            mKHzPicker.setCurrent(mKhz);
        }
    }

    private void validateFrequencyRange() {
        boolean bUpdateSpinner = false;
        if (mFrequency < mMin) {
            mFrequency = mMin;
            bUpdateSpinner = true;
        }
        if (mFrequency > mMax) {
            mFrequency = mMax;
            bUpdateSpinner = true;
        }
        if (bUpdateSpinner == true) {
            updateSpinners();
        }
    }

    public int getFrequency() {
        return (mFrequency);
    }

    public static String formatFrequencyString(int frequency) {
        return sFrequencyFormat.format(frequency / 1000.0);
    }
}
