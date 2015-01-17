package com.aviary.android.feather.sdk.panels;

public class SimpleStatusMachine {
    public static final int INVALID_STATUS = -1;
    private             int currentStatus  = INVALID_STATUS;
    private             int previousStatus = INVALID_STATUS;
    private OnStatusChangeListener mStatusListener;

    public void setOnStatusChangeListener(OnStatusChangeListener listener) {
        mStatusListener = listener;
    }

    public void setStatus(int newStatus) {
        if (newStatus != currentStatus) {
            previousStatus = currentStatus;
            currentStatus = newStatus;

            if (null != mStatusListener) {
                mStatusListener.onStatusChanged(previousStatus, currentStatus);
            }
        } else {
            if (null != mStatusListener) {
                mStatusListener.onStatusUpdated(newStatus);
            }
        }
    }

    public int getCurrentStatus() {
        return currentStatus;
    }

    public int getPreviousStatus() {
        return previousStatus;
    }

    public interface OnStatusChangeListener {
        void onStatusChanged(int oldStatus, int newStatus);

        void onStatusUpdated(int status);
    }
}
