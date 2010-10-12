package com.android.fm.radio;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.SeekBar;

public class FreqIndicator extends SeekBar {

    private int lowerLimit = 0;

    public FreqIndicator(Context context) {
        super(context);
    }

    public FreqIndicator(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
    *
    * @param frequency something like 87500(87.5MHz), 103900(103.9MHz)
    */
   public void setFrequency(int frequency) {
       setProgress(frequency - lowerLimit);
   }

   /**
   *
   * @param frequency something like 87500(87.5MHz), 103900(103.9MHz)
   */
  public int getFrequency() {
      return getProgress() + lowerLimit;
  }

  /**
    * @param minFrequency something like 87500(87.5MHz)
    */
   public void setMinFrequency(int minFrequency) {
       lowerLimit = minFrequency;
   }

   /**
    * @return something like 87500(87.5MHz)
    */
   public int getMinFrequency() {
       return lowerLimit;
   }

}
