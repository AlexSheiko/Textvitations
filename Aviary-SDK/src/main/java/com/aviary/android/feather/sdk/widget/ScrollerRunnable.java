package com.aviary.android.feather.sdk.widget;

import android.annotation.TargetApi;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.OverScroller;
import android.widget.Scroller;

public class ScrollerRunnable implements Runnable {
    protected int            mLastFlingX;
    protected boolean        mShouldStopFling;
    protected ScrollableView mParent;
    protected int            mAnimationDuration;
    protected int            mPreviousX;
    protected boolean        mHasMore;
    private   OverScroller   mScroller;
    private   int            mOverscrollDistance;
    /**
     * ScrollerRunnable acts like a {@link Scroller} adding some help methods
     *
     * @param parent             the parent view
     * @param animationDuration  the default animation duration
     * @param overscrollDistance the overscroller distance
     * @param interpolator       the default interpolator, can be null
     */
    public ScrollerRunnable(ScrollableView parent, int animationDuration, int overscrollDistance, Interpolator interpolator) {
        if (null == interpolator) {
            mScroller = new OverScroller(((View) parent).getContext());
        } else {
            mScroller = new OverScroller(((View) parent).getContext(), interpolator);
        }

        mOverscrollDistance = overscrollDistance;
        mParent = parent;
        mAnimationDuration = animationDuration;
    }

    public int getLastFlingX() {
        return mLastFlingX;
    }

    public void stop(boolean scrollIntoSlots) {
        mParent.removeCallbacks(this);
        endFling(scrollIntoSlots);
    }

    public void endFling(boolean scrollIntoSlots) {
        abortAnimation();
        mLastFlingX = 0;
        mHasMore = false;

        if (scrollIntoSlots) {
            mParent.scrollIntoSlots();
        }
    }

    protected void abortAnimation() {
        mScroller.abortAnimation();
    }

    public void startUsingDistance(int initialX, int distance) {
        if (distance == 0) {
            return;
        }
        startCommon();
        mLastFlingX = initialX;
        mScroller.startScroll(initialX, 0, distance, 0, mAnimationDuration);
        mParent.post(this);
    }

    protected void startCommon() {
        mParent.removeCallbacks(this);
    }

    public void startUsingVelocity(int initialX, int initialVelocity) {
        if (initialVelocity == 0) {
            return;
        }
        startCommon();
        mLastFlingX = initialX;
        mScroller.fling(initialX,
                        0,
                        initialVelocity,
                        0,
                        mParent.getMinX(),
                        mParent.getMaxX(),
                        0,
                        Integer.MAX_VALUE,
                        mOverscrollDistance,
                        0);
        mParent.post(this);
    }

    public int getPreviousX() {
        return mPreviousX;
    }

    public boolean hasMore() {
        return mHasMore;
    }

    @TargetApi (14)
    public float getCurrVelocity() {
        return mScroller.getCurrVelocity();
    }

    public boolean isFinished() {
        return mScroller.isFinished();
    }

    public boolean springBack(int startX, int startY, int minX, int maxX, int minY, int maxY) {
        return mScroller.springBack(startX, startY, minX, maxX, minY, maxY);
    }

    @Override
    public void run() {
        mShouldStopFling = false;
        mPreviousX = getCurrX();
        mHasMore = computeScrollOffset();
        int x = getCurrX();
        mParent.trackMotionScroll(x);

        if (mHasMore && !mShouldStopFling) {
            mLastFlingX = x;
            mParent.post(this);
        } else {
            endFling(true);
        }
    }

    public int getCurrX() {
        return mScroller.getCurrX();
    }

    public boolean computeScrollOffset() {
        return mScroller.computeScrollOffset();
    }

    public interface ScrollableView {
        boolean removeCallbacks(Runnable action);

        boolean post(Runnable action);

        void scrollIntoSlots();

        void trackMotionScroll(int newX);

        int getMinX();

        int getMaxX();
    }

}