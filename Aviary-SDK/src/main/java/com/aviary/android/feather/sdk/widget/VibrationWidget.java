package com.aviary.android.feather.sdk.widget;

public interface VibrationWidget {
    /**
     * Get the vibration feedback enabled status
     *
     * @return
     */
    boolean getVibrationEnabled();

    /**
     * Enable the vibration feedback
     *
     * @param value
     */
    void setVibrationEnabled(boolean value);
}
