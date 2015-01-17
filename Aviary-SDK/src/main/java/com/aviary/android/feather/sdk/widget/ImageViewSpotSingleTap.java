package com.aviary.android.feather.sdk.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import com.aviary.android.feather.sdk.R;
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.AnimatorSet;
import com.nineoldandroids.animation.ObjectAnimator;

import it.sephiroth.android.library.imagezoom.ImageViewTouch;
import it.sephiroth.android.library.imagezoom.graphics.IBitmapDrawable;

public class ImageViewSpotSingleTap extends ImageViewTouch implements Animator.AnimatorListener {
    public static final float DEFAULT_TEXT_SIZE = 50f;
    public static final double BRUSH_SIZE_ANIMATION_SCALE = 1.3;
    protected float     mCurrentScale   = 1;
    protected Matrix    mInvertedMatrix = new Matrix();
    protected TouchMode mTouchMode      = TouchMode.DRAW;
    protected float     mX              = 0, mY = 0;
    protected float mStartX, mStartY;
    AnimatorSet mAnimator;
    boolean mDrawFadeCircle = true;
    boolean mCanceled       = false;
    RectF   mTextRect       = new RectF();
    Rect    mTextBounds     = new Rect();
    private float mBrushSize     = 10;
    private float radius         = 0;
    private Paint mShapePaint    = new Paint();
    private Paint mTextPaint     = new Paint();
    private Paint mTextRectPaint = new Paint();
    private OnTapListener mTapListener;
    private String mToolTip    = "";
    private float  mTextSize   = DEFAULT_TEXT_SIZE;
    private float  xTextOffset = 150;
    private float  yTextOffset = 150;
    private float  textPadding = 20;

    public ImageViewSpotSingleTap(Context context, AttributeSet set) {
        this(context, set, 0);
    }

    public ImageViewSpotSingleTap(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        onCreate(context);
    }

    private void onCreate(Context context) {
        mToolTip = context.getString(R.string.feather_blemish_tool_tip);
        mTextSize = context.getResources().getDimensionPixelSize(R.dimen.aviary_textSizeMedium);
        textPadding = mTextSize / 2;
        yTextOffset = mTextSize * 3;
        xTextOffset = yTextOffset;

        mAnimator = new AnimatorSet();
        mAnimator.addListener(this);

        mShapePaint.setAntiAlias(true);
        mShapePaint.setStyle(Paint.Style.STROKE);
        mShapePaint.setColor(Color.WHITE);
        mShapePaint.setStrokeWidth(6);

        mTextPaint.setColor(Color.WHITE);
        mTextPaint.setTextSize(mTextSize);
        mTextPaint.getTextBounds(mToolTip, 0, mToolTip.length(), mTextBounds);

        mTextRectPaint.setARGB(150, 0, 0, 0);

        setLongClickable(false);
    }

    public float getRadius() {
        return radius;
    }

    public void setRadius(float value) {
        this.radius = value;
        invalidate();
    }

    @Override
    public void onAnimationStart(final Animator animation) {
        invalidate();
    }

    @Override
    public void onAnimationEnd(final Animator animation) {
        invalidate();
    }

    @Override
    public void onAnimationCancel(final Animator animation) {

    }

    @Override
    public void onAnimationRepeat(final Animator animation) {
        invalidate();
    }

    @Override
    @SuppressWarnings("checkstyle:magicnumber")
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mDrawFadeCircle) {
            if (radius > 0) {
                canvas.drawCircle(mX, mY, radius, mShapePaint);
            }
        }

        if (mCanceled) {
            mTextRect.set(mX - textPadding - xTextOffset,
                          mY - mTextBounds.height() * 1.25f - textPadding - yTextOffset,
                          mX + mTextBounds.width() + textPadding - xTextOffset,
                          mY + mTextBounds.height() * 0.5f + textPadding - yTextOffset);

            canvas.drawRoundRect(mTextRect, 10, 10, mTextRectPaint);
            canvas.drawText(mToolTip, mX - xTextOffset, mY - yTextOffset, mTextPaint);
        }

    }

    public void setOnTapListener(OnTapListener listener) {
        mTapListener = listener;
    }

    @Override
    protected void init(Context context, AttributeSet attrs, int defStyle) {
        super.init(context, attrs, defStyle);
    }

    @Override
    protected ScaleGestureDetector.OnScaleGestureListener getScaleListener() {
        return new TapScaleListener();
    }

    @Override
    protected void onLayoutChanged(int left, int top, int right, int bottom) {
        super.onLayoutChanged(left, top, right, bottom);

        if (null != getDrawable()) {
            onDrawModeChanged();
        }
    }

    @Override
    public boolean onSingleTapConfirmed(final MotionEvent e) {
        if (mTouchMode == TouchMode.DRAW) {
            mDrawFadeCircle = true;
            startAnimation();
            if (null != mTapListener) {
                float[] mappedPoints = new float[2];
                mappedPoints[0] = e.getX();
                mappedPoints[1] = e.getY();
                mInvertedMatrix.mapPoints(mappedPoints);
                mTapListener.onTap(mappedPoints, mBrushSize / mCurrentScale);
            }
            return true;
        }
        return super.onSingleTapConfirmed(e);
    }

    private void startAnimation() {

        radius = 0;
        mShapePaint.setAlpha(255);

        Animator set1 = ObjectAnimator.ofFloat(this, "radius", 0, mBrushSize);
        set1.setDuration(200);

        AnimatorSet set2 = new AnimatorSet();
        set2.setInterpolator(new DecelerateInterpolator(1f));
        set2.setDuration(200);
        set2.playTogether(ObjectAnimator.ofFloat(this, "radius", mBrushSize, (int) (mBrushSize * BRUSH_SIZE_ANIMATION_SCALE)),
                          ObjectAnimator.ofInt(mShapePaint, "alpha", 255, 0));

        mAnimator.playSequentially(set1, set2);

        mAnimator.setInterpolator(new AccelerateInterpolator(1f));
        mAnimator.start();
    }

    @Override
    public boolean onScroll(final MotionEvent e1, final MotionEvent e2, final float distanceX, final float distanceY) {
        if (mTouchMode == TouchMode.DRAW) {
            mX = e2.getX();
            mY = e2.getY();
            mCanceled = true;
            postInvalidate();
            return false;
        }

        return super.onScroll(e1, e2, distanceX, distanceY);
    }

    @Override
    public boolean onFling(final MotionEvent e1, final MotionEvent e2, final float velocityX, final float velocityY) {
        if (mTouchMode == TouchMode.DRAW) {
            return false;
        }

        return super.onFling(e1, e2, velocityX, velocityY);
    }

    @Override
    public boolean onDown(final MotionEvent e) {
        if (mTouchMode == TouchMode.DRAW) {
            mX = e.getX();
            mStartX = mX;
            mY = e.getY();
            mStartY = mY;
            mDrawFadeCircle = false;
        }

        return super.onDown(e);
    }

    @Override
    public boolean onUp(final MotionEvent e) {
        mCanceled = false;
        postInvalidate();

        return super.onUp(e);
    }

    @Override
    public boolean onSingleTapUp(final MotionEvent e) {
        if (mTouchMode == TouchMode.DRAW) {
            return false;
        }

        return super.onSingleTapUp(e);
    }

    public TouchMode getDrawMode() {
        return mTouchMode;
    }

    public void setDrawMode(TouchMode mode) {
        if (mode != mTouchMode) {
            mTouchMode = mode;
            onDrawModeChanged();
        }
    }

    protected void onDrawModeChanged() {
        if (mTouchMode == TouchMode.DRAW) {
            Matrix m1 = new Matrix(getImageMatrix());
            mInvertedMatrix.reset();

            float[] v1 = getMatrixValues(m1);
            m1.invert(m1);
            float[] v2 = getMatrixValues(m1);

            mInvertedMatrix.postTranslate(-v1[Matrix.MTRANS_X], -v1[Matrix.MTRANS_Y]);
            mInvertedMatrix.postScale(v2[Matrix.MSCALE_X], v2[Matrix.MSCALE_Y]);

            mCurrentScale = getScale() * getBaseScale();
        }

        setDoubleTapEnabled(mTouchMode == TouchMode.IMAGE);
        setScaleEnabled(mTouchMode == TouchMode.IMAGE);
    }

    public static float[] getMatrixValues(Matrix m) {
        float[] values = new float[9];
        m.getValues(values);
        return values;
    }

    @Override
    protected void onDrawableChanged(Drawable drawable) {
        super.onDrawableChanged(drawable);

        if (drawable != null && (drawable instanceof IBitmapDrawable)) {
            onDrawModeChanged();
        }
    }

    public RectF getImageRect() {
        if (getDrawable() != null) {
            return new RectF(0, 0, getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight());
        } else {
            return null;
        }
    }

    public void setBrushSize(float value) {
        mBrushSize = value;
    }

    public static enum TouchMode {
        // mode for pan and zoom
        IMAGE,
        // mode for drawing
        DRAW
    }

    public interface OnTapListener {
        void onTap(float[] points, float radius);
    }

    class TapScaleListener extends ScaleListener {
        @Override
        public boolean onScaleBegin(final ScaleGestureDetector detector) {
            if (mTouchMode == TouchMode.DRAW) {
                mX = detector.getFocusX();
                mY = detector.getFocusY();
                mStartX = mX;
                mStartY = mY;
                mCanceled = true;
                postInvalidate();
                return true;
            }
            return super.onScaleBegin(detector);
        }

        @Override
        public void onScaleEnd(final ScaleGestureDetector detector) {
            mCanceled = false;
            super.onScaleEnd(detector);
        }

        @Override
        public boolean onScale(final ScaleGestureDetector detector) {
            if (mTouchMode == TouchMode.DRAW) {
                mX = detector.getFocusX();
                mY = detector.getFocusY();
                postInvalidate();
                return true;
            }
            return super.onScale(detector);
        }
    }
}
