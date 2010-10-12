package com.android.fm.radio;

interface IFMRadioServiceCallbacks
{
  void onEnabled();
  void onDisabled();

  void onTuneStatusChanged();
  void onProgramServiceChanged();
  void onRadioTextChanged();
  void onAlternateFrequencyChanged();
  void onSignalStrengthChanged();
  void onSearchComplete();
  void onSearchListComplete();
  void onMute(boolean bMuted);
  void onAudioUpdate(boolean bStereo);
  void onStationRDSSupported(boolean bRDSSupported);
}
