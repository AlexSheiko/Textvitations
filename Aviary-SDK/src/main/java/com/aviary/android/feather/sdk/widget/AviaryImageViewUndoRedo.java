package com.aviary.android.feather.sdk.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.aviary.android.feather.common.log.LoggerFactory;
import com.aviary.android.feather.sdk.BuildConfig;

import it.sephiroth.android.library.imagezoom.ImageViewTouch;

public class AviaryImageViewUndoRedo extends ImageViewTouch {
    static  LoggerFactory.Logger logger        = LoggerFactory.getLogger("AviaryImageViewUndoRedo");
    private int                  mUndoMinFling = 500;
    private int               mMinFling;
    private int               mMaxFling;
    private OnHistoryListener mHistoryListener;

    public AviaryImageViewUndoRedo(final Context context) {
        this(context, null);
    }

    public AviaryImageViewUndoRedo(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AviaryImageViewUndoRedo(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setOnHistoryListener(OnHistoryListener listener) {
        mHistoryListener = listener;
    }

    @Override
    protected void init(final Context context, final AttributeSet attrs, final int defStyle) {
        super.init(context, attrs, defStyle);

        mMinFling = ViewConfiguration.get(context).getScaledMinimumFlingVelocity();
        mMaxFling = ViewConfiguration.get(context).getScaledMaximumFlingVelocity();
        mUndoMinFling = (int) ((double) mMinFling * 2);

        logger.verbose("minFling: %d, maxFling: %d, undoFling: %d", mMinFling, mMaxFling, mUndoMinFling);
    }

    @Override
    public boolean onFling(final MotionEvent e1, final MotionEvent e2, final float velocityX, final float velocityY) {

        if (null == getDrawable()) {
            return false;
        }
        if (!isShown()) {
            return false;
        }

        logger.info("onFling: %f", velocityX);
        if (null != mHistoryListener && isValidScale() && Math.abs(velocityX) > mUndoMinFling && Math.abs(velocityX) > Math.abs(
            velocityY)) {
            if (velocityX < 0) {
                mHistoryListener.onRedo();
            } else {
                mHistoryListener.onUndo();
            }
            return true;
        }

        return super.onFling(e1, e2, velocityX, velocityY);
    }

    @SuppressWarnings("checkstyle:magicnumber")
    private boolean isValidScale() {
        if (BuildConfig.SDK_DEBUG) {
            logger.verbose("isValidScale: %f == %f", getScale(), getMinScale());
        }
        return Math.abs(getScale() - getMinScale()) < 0.1;
    }

    public interface OnHistoryListener {
        void onRedo();

        void onUndo();
    }
}
