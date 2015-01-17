package com.aviary.android.feather.sdk.widget;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import com.aviary.android.feather.common.log.LoggerFactory;
import com.aviary.android.feather.common.utils.ApiHelper;
import com.aviary.android.feather.library.graphics.animation.EasingType;
import com.aviary.android.feather.library.graphics.animation.ExpoInterpolator;
import com.aviary.android.feather.sdk.R;
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.ObjectAnimator;
import com.nineoldandroids.animation.ValueAnimator;

import it.sephiroth.android.library.imagezoom.ImageViewTouch;

public class ImageViewVignette extends ImageViewTouch {
    public static final float                STROKE_WIDTH_INCREASE_RATIO = 1.5f;
    /** when ripple must be shown, the current rect will be inset by this value in order to display the ripple effect */
    public static final float                CLOUD_INSET_RATIO           = 0.10f;
    /** minimum angle when to start zoom the vignette horizontally and vertically */
    public static final float                DIAGONAL_ANGLE_RATIO_MIN    = 0.50f;
    /** maximum angle when to start zoom the vignette horizontally and vertically */
    public static final float                DIAGONAL_ANGLE_RATIO_MAX    = 1.50f;
    /** minimum angle for vertical zooming */
    public static final double               VERTICAL_ANGLE_RATIO_MIN    = 0.75;
    private static      LoggerFactory.Logger logger                      = LoggerFactory.getLogger("ImageViewVignette");
    private static int mFadeoutTime;
    private static int   mTempBitmapScale = 6;
    final          RectF tempRect         = new RectF();
    private final  RectF pBitmapRect      = new RectF();
    private final  Paint mPaint           = new Paint();
    Animator mFadeInAnimator;
    Animator mFadeOutAnimator;
    float    mOuterRectOutset, mOuterRadiusAddition;
    int mRippleAnimationDuration;
    int mRippleAnimationDelay;
    boolean mShouldRipple = true;
    private float mFeather   = 1f;
    private int   mIntensity = 100;
    // styles
    private int   mStrokeColor1;
    private int   mStrokeColor2;
    private float mStrokeWidth1;
    private float mStrokeWidth2;
    private int mPaintAlpha = 255;
    private float                    mControlPointSize;
    private OnVignetteChangeListener mVignetteListener;
    private Bitmap                   mTempBitmap;
    private float mTempScaleX = 1;
    private float mTempScaleY = 1;
    private float           sControlPointTolerance;
    private GestureDetector mGestureDetector;
    private Paint           mVignettePaint;
    private Matrix mInvertedMatrix = new Matrix();
    private RectF      mVignetteRect;
    private TouchState mTouchState;
    private Rect mTempBitmapRect = new Rect();
    private PointCloud mPointCloud;
    private RectF mTempBitmapFinalRect = new RectF();

    public ImageViewVignette(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ImageViewVignette(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        initialize(context, attrs, defStyle);
    }

    private void initialize(Context context, final AttributeSet attrs, final int defStyle) {
        logger.info("initialize");

        Resources.Theme theme = context.getTheme();
        TypedArray array = theme.obtainStyledAttributes(attrs, R.styleable.ImageViewVignette, defStyle, 0);

        mStrokeWidth1 = array.getDimension(R.styleable.ImageViewVignette_aviary_vignette_strokeSize, 1f);
        mControlPointSize = array.getDimension(R.styleable.ImageViewVignette_aviary_vignette_controlPointSize, 1f);
        mStrokeWidth2 = mStrokeWidth1 * STROKE_WIDTH_INCREASE_RATIO;
        mStrokeColor1 = array.getColor(R.styleable.ImageViewVignette_aviary_vignette_strokeColor1, Color.WHITE);
        mStrokeColor2 = array.getColor(R.styleable.ImageViewVignette_aviary_vignette_strokeColor2, Color.BLACK);
        mIntensity = array.getInteger(R.styleable.ImageViewVignette_aviary_vignette_intensity, 100);
        mFeather = array.getFloat(R.styleable.ImageViewVignette_aviary_vignette_feather, 1);
        Drawable pointDrawable = array.getDrawable(R.styleable.ImageViewVignette_aviary_vignette_aviaryWave_pointDrawable);
        mOuterRectOutset = context.getResources().getDimension(R.dimen.aviary_vignette_outer_rect_outset);
        mOuterRadiusAddition = context.getResources().getDimension(R.dimen.aviary_vignette_outer_radius_addition);
        mRippleAnimationDuration = array.getInt(R.styleable.ImageViewVignette_aviary_vignette_rippleAnimationDuration, 1800);
        mRippleAnimationDelay = array.getInt(R.styleable.ImageViewVignette_aviary_vignette_animationDelay, 400);

        mFadeoutTime = array.getInteger(R.styleable.ImageViewVignette_aviary_vignette_fadeout_time, 1000);

        mInvertedMatrix.reset();

        mPaintAlpha = 255;

        array.recycle();

        mGestureDetector = new GestureDetector(context, getGestureListener());

        if (ApiHelper.AT_LEAST_19) {
            mScaleDetector.setQuickScaleEnabled(true);
        }

        mVignettePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mVignettePaint.setStyle(Paint.Style.STROKE);

        mVignetteRect = new RectF();

        mTouchState = TouchState.None;

        sControlPointTolerance = mControlPointSize * 2;

        logger.log("control point size: %f", mControlPointSize);

        setHardwareAccelerated(true);

        mFadeInAnimator = ObjectAnimator.ofFloat(this, "paintAlpha", 0, 255);
        mFadeOutAnimator = ObjectAnimator.ofFloat(this, "paintAlpha", 255, 0);
        mFadeOutAnimator.setStartDelay(mFadeoutTime);

        mPointCloud = new PointCloud(pointDrawable);

    }

    @Override
    protected GestureDetector.OnGestureListener getGestureListener() {
        return new MyGestureListener();
    }

    public void setHardwareAccelerated(boolean accelerated) {
        if (accelerated) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                if (isHardwareAccelerated()) {
                    Paint hardwarePaint = new Paint();
                    hardwarePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.OVERLAY));
                    setLayerType(LAYER_TYPE_HARDWARE, hardwarePaint);
                } else {
                    setLayerType(LAYER_TYPE_SOFTWARE, null);
                }
            } else {
                setDrawingCacheEnabled(true);
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                setLayerType(LAYER_TYPE_SOFTWARE, null);
            } else {
                setDrawingCacheEnabled(true);
            }
        }
    }

    protected ScaleGestureDetector.OnScaleGestureListener getScaleListener() {
        return new MyScaleListener();
    }

    @Override
    protected void onLayoutChanged(final int left, final int top, final int right, final int bottom) {
        super.onLayoutChanged(left, top, right, bottom);
        updateBitmapRect();
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {

        if (pBitmapRect.isEmpty()) {
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
        if (mVignetteRect.isEmpty()) {
            return false;
        }

        tempRect.set(mVignetteRect);

        float max;

        switch (mTouchState) {
            case None:
                break;

            case Center:
                if (pBitmapRect.contains(tempRect.centerX() - distanceX, tempRect.centerY() - distanceY)) {
                    tempRect.offset(-distanceX, -distanceY);
                }
                break;

            case Scale:
                tempRect.inset(distanceX, distanceY);
                break;
            default:
                break;
        }

        if (tempRect.width() > sControlPointTolerance && tempRect.height() > sControlPointTolerance) {
            mVignetteRect.set(tempRect);
        }

        dispatchVignetteChangeListener(mVignetteRect);
        invalidate();
        return true;
    }

    @Override
    public boolean onDown(final MotionEvent e) {
        mFadeOutAnimator.cancel();

        if (getPaintAlpha() != 255) {
            mFadeInAnimator.start();
        }

        if (mVignetteRect.isEmpty()) {
            return false;
        }
        setTouchState(TouchState.Center);
        return true;
    }

    public float getPaintAlpha() {
        return mPaintAlpha;
    }

    @SuppressWarnings ("unused")
    public void setPaintAlpha(float value) {
        mPaintAlpha = (int) value;
        mVignettePaint.setAlpha(mPaintAlpha);
        invalidate();
    }

    @Override
    public boolean onUp(final MotionEvent e) {
        setTouchState(TouchState.None);
        mFadeOutAnimator.start();
        return true;
    }

    private void setTouchState(TouchState newState) {
        if (newState != mTouchState) {
            logger.info("setTouchState: %s", newState);
            mTouchState = newState;
            invalidate();
        }
    }

    private void updateBitmapRect() {
        logger.log("updateBitmapRect");

        mTouchState = TouchState.None;

        if (null == getDrawable()) {
            mVignetteRect.setEmpty();
            pBitmapRect.setEmpty();
            return;
        }

        RectF rect = getBitmapRect();
        final boolean rectChanged = !pBitmapRect.equals(rect);

        logger.log("rect_changed: %b", rectChanged);

        if (null != rect) {
            if (rectChanged) {
                if (!pBitmapRect.isEmpty()) {

                    float oldLeft = pBitmapRect.left;
                    float oldTop = pBitmapRect.top;
                    float oldWidth = pBitmapRect.width();
                    float oldHeight = pBitmapRect.height();

                    mVignetteRect.inset(-(rect.width() - oldWidth) / 2, -(rect.height() - oldHeight) / 2);
                    mVignetteRect.offset(rect.left - oldLeft, rect.top - oldTop);
                    mVignetteRect.offset((rect.width() - oldWidth) / 2, (rect.height() - oldHeight) / 2);
                } else {
                    mVignetteRect.set(rect);
                    mVignetteRect.inset(sControlPointTolerance, sControlPointTolerance);
                }
            }
            pBitmapRect.set(rect);
        } else {
            // rect is null
            pBitmapRect.setEmpty();
            mVignetteRect.setEmpty();
        }

        if (mShouldRipple) {
            mVignetteRect.inset(CLOUD_INSET_RATIO * mVignetteRect.width(), CLOUD_INSET_RATIO * mVignetteRect.height());
        }

        mTempScaleX = mTempBitmapRect.width() / pBitmapRect.width();
        mTempScaleY = mTempBitmapRect.height() / pBitmapRect.height();

        Matrix matrix = new Matrix(getImageMatrix());
        matrix.postScale(mTempBitmapScale, mTempBitmapScale);
        matrix.postTranslate(-pBitmapRect.left * (mTempBitmapScale - 1), -pBitmapRect.top * (mTempBitmapScale - 1));
        matrix.invert(mInvertedMatrix);

        dispatchVignetteChangeListener(mVignetteRect);

        //setPaintAlpha(255);
        mFadeOutAnimator.start();

        if (mShouldRipple) {
            setupRipple();
            mShouldRipple = false;
        }

    }

    void setupRipple() {

        RectF outerRect = new RectF(mVignetteRect);
        outerRect.inset(-1 * mOuterRectOutset, -1 * mOuterRectOutset);

        mPointCloud.waveManager.setRadius(Math.max(mVignetteRect.width(), mVignetteRect.height()));
        mPointCloud.waveManager.setAlpha(0.0f);

        mPointCloud.setEllipseOffset(mVignetteRect.left, mVignetteRect.top);
        mPointCloud.makeEllipseCloud(mVignetteRect, outerRect);
        final boolean mPointCloudEnabled = true;

        // make sure the 200 works for different configs
        final ValueAnimator mAnimator = ObjectAnimator.ofFloat(mPointCloud.waveManager,
                                                               "radius",
                                                               Math.max(mVignetteRect.width() / 2, mVignetteRect.height() / 2),
                                                               Math.max(outerRect.width() / 2, outerRect.height() / 2)
                                                                   + mOuterRadiusAddition);
        mAnimator.setDuration(mRippleAnimationDuration);
        mAnimator.setStartDelay(mRippleAnimationDelay);
        mAnimator.setInterpolator(new ExpoInterpolator(EasingType.Type.OUT));
        mAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(final ValueAnimator animation) {
                if (mPointCloudEnabled) {
                    invalidate();
                }
            }
        });
        mAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(final Animator animation) {
                if (mPointCloudEnabled) {
                    mPointCloud.waveManager.setRadius((Float) mAnimator.getAnimatedValue());
                    mPointCloud.waveManager.setAlpha(1);
                    invalidate();
                }
            }

            @Override
            public void onAnimationEnd(final Animator animation) {
                if (mPointCloudEnabled) {
                    mPointCloud.waveManager.setRadius(0.0f);
                    mPointCloud.waveManager.setAlpha(0);
                    invalidate();
                }
            }

            @Override
            public void onAnimationCancel(final Animator animation) {
                if (mPointCloudEnabled) {
                    mPointCloud.waveManager.setAlpha(0);
                    invalidate();
                }
            }

            @Override
            public void onAnimationRepeat(final Animator animation) {}
        });

        if (null != mPointCloud && mPointCloudEnabled) {
            mPointCloud.waveManager.setType(PointCloud.WaveType.Ellipse);
            mPointCloud.waveManager.setAlpha(0.0f);

            if (mPointCloudEnabled) {
                if (null != mAnimator) {
                    mAnimator.cancel();
                }
                //mAnimator.setFloatValues( mVignetteRect.width() / 2, outerRect.width() / 2 );
                mAnimator.start();
            }
        }

    }

    public void setOnVignetteChangeListener(OnVignetteChangeListener listener) {
        mVignetteListener = listener;
    }

    public float getVignetteFeather() {
        return mFeather;
    }

    public void setVignetteFeather(float value) {
        mFeather = value;
        dispatchVignetteChangeListener(mVignetteRect);
    }

    private void dispatchVignetteChangeListener(RectF rect) {
        if (null != mVignetteListener && null != mTempBitmap && !mTempBitmap.isRecycled() && !rect.isEmpty()) {
            mInvertedMatrix.mapRect(mTempBitmapFinalRect, rect);
            mVignetteListener.onVignetteChange(this, mTempBitmap, mTempBitmapFinalRect, mIntensity, mFeather);
        }
    }

    public int getVignetteIntensity() {
        return mIntensity;
    }

    public void setVignetteIntensity(int value) {
        mIntensity = value;
        dispatchVignetteChangeListener(mVignetteRect);
    }

    public void generateBitmap(Bitmap outBitmap) {
        if (mVignetteRect.isEmpty() || pBitmapRect.isEmpty()) {
            return;
        }
        if (mTempBitmap == null || mTempBitmap.isRecycled()) {
            return;
        }

        Canvas canvas = new Canvas(outBitmap);
        canvas.drawBitmap(mTempBitmap, mTempBitmapRect, new Rect(0, 0, outBitmap.getWidth(), outBitmap.getHeight()), mPaint);
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);

        if (mVignetteRect.isEmpty() || pBitmapRect.isEmpty()) {
            return;
        }

        if (mTempBitmap == null || mTempBitmap.isRecycled()) {
            return;
        }
        canvas.drawBitmap(mTempBitmap, mTempBitmapRect, pBitmapRect, mPaint);

        // ------------------
        // rest of the UI
        // ------------------
        // main ellipse
        if (mPaintAlpha > 0) {
            mVignettePaint.setStrokeWidth(mStrokeWidth2);
            mVignettePaint.setColor(mStrokeColor2);
            mVignettePaint.setAlpha(mPaintAlpha);
            canvas.drawOval(mVignetteRect, mVignettePaint);

            mVignettePaint.setStrokeWidth(mStrokeWidth1);
            mVignettePaint.setColor(mStrokeColor1);
            mVignettePaint.setAlpha(mPaintAlpha);
            canvas.drawOval(mVignetteRect, mVignettePaint);
        }

        if (mPointCloud != null) {
            mPointCloud.draw(canvas);
        }

    }

    public void setImageBitmap(
        final Bitmap bitmap, final Bitmap vignetteBitmap, final Matrix matrix, final float minZoom, final float maxZoom) {

        mTempBitmap = vignetteBitmap;
        if (null != vignetteBitmap) {
            mTempBitmapRect.set(0, 0, mTempBitmap.getWidth(), mTempBitmap.getHeight());
            mTempBitmapScale = bitmap.getWidth() / vignetteBitmap.getWidth();
        } else {
            mTempBitmapRect.setEmpty();
        }
        setImageBitmap(bitmap, matrix, minZoom, maxZoom);
    }

    static enum TouchState {
        None,
        Center,
        Scale,
    }

    public interface OnVignetteChangeListener {
        void onVignetteChange(
            ImageViewVignette imageView, Bitmap vignetteBitmap, RectF relativeRect, int intensity, float feather);
    }

    private class MyGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapUp(final MotionEvent e) {
            return false;
        }

        @Override
        public void onLongPress(final MotionEvent e) { }

        @Override
        public boolean onScroll(final MotionEvent e1, final MotionEvent e2, final float distanceX, final float distanceY) {
            if (mScaleDetector.isInProgress()) {
                return false;
            }
            if (e2.getPointerCount() > 1 || e1.getPointerCount() > 1) {
                return false;
            }

            return ImageViewVignette.this.onScroll(e1, e2, distanceX, distanceY);
        }

        @Override
        public boolean onFling(
            final MotionEvent e1, final MotionEvent e2, final float velocityX, final float velocityY) {
            return false;
        }

        @Override
        public boolean onDown(final MotionEvent e) {
            return ImageViewVignette.this.onDown(e);
        }

        @Override
        public boolean onDoubleTap(final MotionEvent e) {
            return false;
        }
    }

    public class MyScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        private float mRatio;

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            if (mTouchState != TouchState.Scale) {
                return true;
            }

            if (!ApiHelper.AT_LEAST_11) {
                return true;
            }

            float distanceX = detector.getPreviousSpanX() - detector.getCurrentSpanX();
            float distanceY = detector.getPreviousSpanY() - detector.getCurrentSpanY();
            float distance = detector.getPreviousSpan() - detector.getCurrentSpan();

            if (mRatio > DIAGONAL_ANGLE_RATIO_MIN && mRatio < DIAGONAL_ANGLE_RATIO_MAX) {
                // diagonal
                float ratio = mVignetteRect.width() / mVignetteRect.height();

                if (ratio >= 1.0) {
                    distanceX = distance;
                    distanceY = distance / ratio;
                } else {
                    distanceX = distance * ratio;
                    distanceY = distance;
                }
                ImageViewVignette.this.onScroll(null, null, distanceX, distanceY);
            } else if (mRatio <= VERTICAL_ANGLE_RATIO_MIN) {
                // vertical
                ImageViewVignette.this.onScroll(null, null, 0, distanceY);
            } else {
                // horizontal
                ImageViewVignette.this.onScroll(null, null, distanceX, 0);
            }

            return true;
        }

        @Override
        public boolean onScaleBegin(final ScaleGestureDetector detector) {
            if (ApiHelper.AT_LEAST_11) {
                mRatio = detector.getCurrentSpanX() / detector.getCurrentSpanY();
                setTouchState(TouchState.Scale);
            }
            return super.onScaleBegin(detector);
        }

        @Override
        public void onScaleEnd(final ScaleGestureDetector detector) {
            super.onScaleEnd(detector);
        }
    }
}
