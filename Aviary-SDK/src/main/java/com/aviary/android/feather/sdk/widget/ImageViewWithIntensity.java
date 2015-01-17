package com.aviary.android.feather.sdk.widget;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateDecelerateInterpolator;

import com.aviary.android.feather.common.log.LoggerFactory;
import com.aviary.android.feather.sdk.R;
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.ValueAnimator;

import it.sephiroth.android.library.imagezoom.ImageViewTouch;
import it.sephiroth.android.library.imagezoom.graphics.FastBitmapDrawable;

public class ImageViewWithIntensity extends ImageViewTouch {
    private static final float                RAD      = (float) Math.toRadians(10);
    private static final float                RAD_COS  = (float) Math.cos(RAD);
    private static final float                RAD_SIN  = (float) Math.sin(RAD);
    static               LoggerFactory.Logger logger   = LoggerFactory.getLogger(ImageViewWithIntensity.class.getSimpleName());
    private final        RectF                mTipRect = new RectF();
    private final        Rect                 tempRect = new Rect();
    private OnIntensityChange  mIntensityListener;
    private GestureDetector    mGestureDetector;
    private float              mIntensity;
    private FastBitmapDrawable mPreviewBitmapDrawable;
    private float              mTouchSlop;
    private boolean            mSwipeGestureEnabled;
    private PointF             mDownPoint;
    private PointF mCurrentPoint = new PointF();
    private boolean       mScrollVerticalStarted;
    private Paint         mTipPaint;
    private Paint         mTextPaint;
    private Paint         mTipArcPaint;
    private int           mTipSize;
    private int           mTipRadius;
    private float         mTipArcStrokeWeight;
    private int           mTipBackgroundColor;
    private int           mTipTextColor;
    private ValueAnimator mPlayDemoAnimator;
    private boolean mVaryTipStroke = true;
    private boolean mVaryTipHue    = true;
    private boolean mIntensityInitiated;
    private Path    mPath1, mPath2, mPath3;

    public ImageViewWithIntensity(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ImageViewWithIntensity(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        mGestureDetector = new GestureDetector(context, getGestureListener());
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        Resources.Theme theme = context.getTheme();
        TypedArray array = theme.obtainStyledAttributes(attrs, R.styleable.AviaryImageViewIntensity, defStyle, 0);

        mTipSize = array.getDimensionPixelSize(R.styleable.AviaryImageViewIntensity_aviary_tooltipSize, 200);
        mTipArcStrokeWeight = array.getDimension(R.styleable.AviaryImageViewIntensity_aviary_strokeWidth, 14);
        mTipBackgroundColor = array.getColor(R.styleable.AviaryImageViewIntensity_aviary_strokeColor, Color.BLACK);
        mTipTextColor = array.getColor(R.styleable.AviaryImageViewIntensity_android_textColor, Color.BLACK);
        mTipRadius = mTipSize / 2;

        array.recycle();

        mPath1 = new Path();
        mPath2 = new Path();
        mPath3 = new Path();

        mTipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTipPaint.setColor(mTipBackgroundColor);

        mTipArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTipArcPaint.setStyle(Paint.Style.FILL);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setTextSize(mTipSize / 3);
        mTextPaint.setColor(mTipTextColor);
        mTextPaint.setAntiAlias(true);
        mTextPaint.setLinearText(true);

    }

    @Override
    protected GestureDetector.OnGestureListener getGestureListener() {
        return new MyGestureListener();
    }

    protected ScaleGestureDetector.OnScaleGestureListener getScaleListener() {
        return new MyScaleListener();
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        if (null == getBitmapRect() || getBitmapRect().isEmpty()) {
            return false;
        }

        mScaleDetector.onTouchEvent(event);

        if (!mScaleDetector.isInProgress()) {
            mGestureDetector.onTouchEvent(event);
        }

        final int action = event.getAction();
        switch (action & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_UP:
                return onUp(event);
            default:
                break;
        }
        return true;
    }

    @Override
    public boolean onScroll(final MotionEvent e1, final MotionEvent e2, final float distanceX, final float distanceY) {
        if (mDownPoint != null && getBitmapRect() != null && mSwipeGestureEnabled) {
            if (mScaleDetector.isInProgress()) {
                return false;
            }
            return onScroll(e2.getX(), e2.getY(), distanceX, distanceY, true);
        }
        return false;
    }

    @Override
    public boolean onDown(final MotionEvent e) {
        if (mSwipeGestureEnabled) {
            return onDown(e.getX(), e.getY(), true);
        }
        return super.onDown(e);
    }

    @Override
    public boolean onUp(final MotionEvent e) {
        mDownPoint = null;
        mScrollVerticalStarted = false;

        if (null != mIntensityListener && mSwipeGestureEnabled) {
            mIntensityListener.onIntensitySwipeChanged(mIntensity);
        }

        postInvalidate();
        return true;
    }

    public void finishIntensityChanging() {
        onUp(null);
    }

    public void setVaryTipStroke(boolean vary) {
        mVaryTipStroke = vary;
    }

    public void setVaryTipHue(boolean vary) {
        mVaryTipHue = vary;
    }

    public void setOnIntensityChangeListener(OnIntensityChange listener) {
        mIntensityListener = listener;
    }

    public void setPreviewBitmap(Bitmap bitmap, float intensity) {
        if (null == bitmap) {
            mPreviewBitmapDrawable = null;
        } else {
            if (getDrawable() != null) {
                if (getDrawable().getIntrinsicWidth() == bitmap.getWidth()
                    && getDrawable().getIntrinsicHeight() == bitmap.getHeight()) {
                    mPreviewBitmapDrawable = new FastBitmapDrawable(bitmap);
                }
            }
        }
        mIntensity = intensity;
        postInvalidate();
    }

    public void generateBitmap(Bitmap outBitmap, final float intensity) {
        logger.info("generateBitmap, intensity: %f", intensity);
        Canvas canvas = new Canvas(outBitmap);
        if (null != mPreviewBitmapDrawable) {
            mPreviewBitmapDrawable.setAlpha((int) intensity);
            mPreviewBitmapDrawable.draw(canvas);
        }
    }

    public int getTooltipSize() {
        return mTipSize;
    }

    @Override
    @SuppressWarnings ({ "checkstyle:cyclomaticcomplexity", "checkstyle:magicnumber" })
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);

        if (null != getDrawable()) {
            if (null != mPreviewBitmapDrawable) {
                mPreviewBitmapDrawable.setAlpha((int) mIntensity);

                canvas.save();
                canvas.concat(getImageMatrix());
                mPreviewBitmapDrawable.draw(canvas);
                canvas.restore();
            }

            if (mScrollVerticalStarted) {
                int saveCount = canvas.save();

                int intensityPerc = (int) (mIntensity / 2.55);
                String intensityString = String.valueOf(intensityPerc);

                float x = mCurrentPoint.x - (mTipSize * 1.2f);
                float y = mCurrentPoint.y;

                boolean flip = false;
                int startAngle = 10;

                if ((x - mTipRadius - mTipArcStrokeWeight * 2) < 0) {
                    x = mCurrentPoint.x + mTipSize * 1.2f;
                    startAngle += 180;
                    flip = true;
                }

                mTipRect.set(x - mTipRadius, y - mTipRadius, x + mTipRadius, y + mTipRadius);

                float radiusX = mTipRect.width() / 2 * RAD_COS;
                float radiusY = mTipRect.height() / 2 * RAD_SIN;

                mPath1.reset();
                mPath1.addArc(mTipRect, startAngle, 340);
                mPath1.moveTo(x + (flip ? -radiusX : radiusX), flip ? y + radiusY : y - radiusY);
                mPath1.lineTo(x + (flip ? -radiusX - mTipRadius / 4 : radiusX + mTipRadius / 4), y);
                mPath1.lineTo(x + (flip ? -radiusX : radiusX), flip ? y - radiusY : y + radiusY);

                canvas.drawPath(mPath1, mTipPaint);

                // text
                mTextPaint.getTextBounds(intensityString, 0, intensityString.length(), tempRect);
                float x2 = (mTipSize - tempRect.width()) / 2;
                canvas.drawText(intensityString, (x - mTipRadius) + x2, y + (tempRect.height() / 2), mTextPaint);

                // intensity slider background
                final float sliderOffsetTop = 0.65f;
                final float sliderOffsetBottom = 0.35f;

                mTipArcPaint.setColor(mTipBackgroundColor);
                mTipRect.inset(-2, -2);

                mPath2.reset();
                mPath2.moveTo(mTipRect.centerX(), mTipRect.bottom);
                mPath2.arcTo(mTipRect, 90, flip ? -180 : 180);

                if (mVaryTipStroke) {
                    mTipRect.inset(-mTipArcStrokeWeight, -mTipArcStrokeWeight);
                    mTipRect.offset(0, -(mTipArcStrokeWeight * sliderOffsetTop));
                } else {
                    mTipRect.inset(-(mTipArcStrokeWeight / 2) - 1, -(mTipArcStrokeWeight / 2) - 1);
                }

                mPath2.lineTo(mTipRect.centerX(), mTipRect.top);
                mPath2.arcTo(mTipRect, -90, flip ? 180 : -180);
                mPath2.lineTo(mTipRect.centerX(), mTipRect.bottom - (mTipArcStrokeWeight * sliderOffsetBottom));

                canvas.drawPath(mPath2, mTipArcPaint);

                // intensity slider foreground
                mTipRect.set(x - mTipRadius, y - mTipRadius, x + mTipRadius, y + mTipRadius);
                mTipRect.inset(-2, -2);

                // change the color based on the intensity if mVaryTipHue is enabled
                int color;
                if (mVaryTipHue) {
                    color = Color.HSVToColor(255, new float[]{intensityPerc * 2, 1, 1});
                } else {
                    color = Color.HSVToColor(255, new float[]{200, 1, 1});
                }
                mTipArcPaint.setColor(color);

                if (intensityPerc > 0) {
                    final float perc = (float) intensityPerc / 100f;
                    final float perc180 = perc * 180f;

                    mPath3.reset();

                    if (mVaryTipStroke) {
                        mPath3.moveTo(mTipRect.centerX(), mTipRect.bottom);
                        mPath3.arcTo(mTipRect, 90, flip ? -perc180 : perc180);
                        mTipRect.inset(-mTipArcStrokeWeight, -mTipArcStrokeWeight);
                        mTipRect.offset(0, -(mTipArcStrokeWeight * sliderOffsetTop));
                        float additional = (float) Math.sin(Math.toRadians((perc180)));
                        float angle = (float) (perc180 - ((mTipArcStrokeWeight * 1.05f) / Math.PI * additional));
                        mPath3.arcTo(mTipRect, 90 + (flip ? -angle : angle), flip ? angle : -angle);
                        mPath3.lineTo(mTipRect.centerX(), mTipRect.bottom - (mTipArcStrokeWeight * sliderOffsetBottom));
                    } else {
                        mPath3.arcTo(mTipRect, 90, flip ? -perc180 : perc180);
                        mTipRect.inset(-(mTipArcStrokeWeight / 2) - 1, -(mTipArcStrokeWeight / 2) - 1);
                        mPath3.arcTo(mTipRect, 90 + (flip ? -perc180 : perc180), flip ? perc180 : -perc180);
                        mPath3.lineTo(mTipRect.centerX(), mTipRect.bottom - (mTipArcStrokeWeight * sliderOffsetBottom));
                    }
                    canvas.drawPath(mPath3, mTipArcPaint);
                }

                canvas.restoreToCount(saveCount);
            }
        }
    }

    public PointF playDemo() {
        final PointF downPoint = new PointF(getWidth() - (mTipRadius / 2), getHeight() / 4);

        mPlayDemoAnimator = ValueAnimator.ofInt(0, getHeight() / 4, 0);
        mPlayDemoAnimator.setDuration(3000);
        mPlayDemoAnimator.setStartDelay(300);
        mPlayDemoAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        mPlayDemoAnimator.start();
        mPlayDemoAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(final ValueAnimator animation) {
                if (null == mDownPoint) {
                    return;
                }
                Integer value = (Integer) animation.getAnimatedValue();
                onScroll(mDownPoint.x, mDownPoint.y + value, 0, value, false);

            }
        });
        mPlayDemoAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(final Animator animation) {
                onDown(downPoint.x, downPoint.y, false);
            }

            @Override
            public void onAnimationEnd(final Animator animation) {
                onUp(null);
            }

            @Override
            public void onAnimationCancel(final Animator animation) {
            }

            @Override
            public void onAnimationRepeat(final Animator animation) {

            }
        });

        return downPoint;
    }

    private boolean onScroll(float x, float y, float distanceX, float distanceY, boolean fromUser) {
        if (!mSwipeGestureEnabled || null == mDownPoint) {
            return true;
        }
        float totalDistanceX = x - mDownPoint.x;
        float totalDistanceY = y - mDownPoint.y;
        if (!mScrollVerticalStarted) {

            if (Math.abs(totalDistanceX) > mTouchSlop || Math.abs(totalDistanceY) > mTouchSlop) {
                if (Math.abs(totalDistanceX) > Math.abs(totalDistanceY)) {
                    mScrollVerticalStarted = false;
                    mDownPoint = null;
                    return false;
                }
                logger.warn("ok, started!!!!");
                mScrollVerticalStarted = true;

                if (mIntensityListener != null && !mIntensityInitiated) {
                    mIntensityListener.onIntensityInit();
                    mIntensityInitiated = true;
                }

                if (null != mIntensityListener) {
                    mIntensityListener.onIntensitySwipeStarted(mIntensity);
                }

            }
            return true;
        }

        distanceY = y - mCurrentPoint.y;
        mCurrentPoint.set(x, y);

        float realDistance = (distanceY / ((float) getHeight() / 2f)) * 255;
        float intensity = mIntensity - realDistance;
        intensity = Math.min(255, Math.max(0, intensity));
        setIntensity(intensity);

        if (null != mIntensityListener) {
            mIntensityListener.onIntensitySwipeChanging(mIntensity);
        }

        return true;
    }

    private boolean onDown(float x, float y, boolean fromUser) {
        if (!mSwipeGestureEnabled) {
            return true;
        }
        if (fromUser) {
            if (null != mPlayDemoAnimator && mPlayDemoAnimator.isStarted()) {
                mPlayDemoAnimator.cancel();
                mPlayDemoAnimator = null;
            }
        }
        mDownPoint = new PointF(x, y);
        mCurrentPoint.set(mDownPoint);
        mScrollVerticalStarted = false;
        return true;
    }

    public void setSwipeGestureEnabled(boolean enabled) {
        mSwipeGestureEnabled = enabled;
    }

    public float getIntensity() {
        return mIntensity;
    }

    public void setIntensity(float value) {
        mIntensity = value;
        invalidate();
    }

    public interface OnIntensityChange {
        void onIntensitySwipeStarted(float intensity);

        void onIntensitySwipeChanging(float intensity);

        void onIntensitySwipeChanged(float intensity);

        void onIntensityInit();
    }

    private class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapUp(final MotionEvent e) {
            return false;
        }

        @Override
        public void onLongPress(final MotionEvent e) {}

        @Override
        public boolean onScroll(final MotionEvent e1, final MotionEvent e2, final float distanceX, final float distanceY) {
            return ImageViewWithIntensity.this.onScroll(e1, e2, distanceX, distanceY);
        }

        @Override
        public boolean onFling(
            final MotionEvent e1, final MotionEvent e2, final float velocityX, final float velocityY) {
            return false;
        }

        @Override
        public boolean onDown(final MotionEvent e) {
            return ImageViewWithIntensity.this.onDown(e);
        }

        @Override
        public boolean onDoubleTap(final MotionEvent e) {
            return false;
        }
    }

    public class MyScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            mDownPoint = null;
            mScrollVerticalStarted = false;
            return true;
        }

    }
}
