package com.aviary.android.feather.sdk.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

import it.sephiroth.android.library.imagezoom.ImageViewTouch;
import it.sephiroth.android.library.imagezoom.graphics.IBitmapDrawable;

public class ImageViewSpotDraw extends ImageViewTouch {
    protected static final float  TOUCH_TOLERANCE = 2;
    protected float mBrushSize = 30;
    protected Paint mPaint;
    protected float mCurrentScale = 1;
    protected Path  tmpPath       = new Path();
    protected Canvas mCanvas;
    protected TouchMode mTouchMode = TouchMode.DRAW;
    protected float mX, mY;
    protected float mStartX, mStartY;
    protected              Matrix mIdentityMatrix = new Matrix();
    protected              Matrix mInvertedMatrix = new Matrix();
    private OnDrawListener mDrawListener;
    private double mRestiction = 0;
    private boolean mMoved = false;
    public ImageViewSpotDraw(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ImageViewSpotDraw(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setOnDrawStartListener(OnDrawListener listener) {
        mDrawListener = listener;
    }

    @Override
    protected void init(Context context, AttributeSet attrs, int defStyle) {
        super.init(context, attrs, defStyle);
        tmpPath = new Path();
    }

    @Override
    protected void onLayoutChanged(int left, int top, int right, int bottom) {
        super.onLayoutChanged(left, top, right, bottom);

        if (null != getDrawable()) {
            onDrawModeChanged();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mTouchMode == TouchMode.DRAW && event.getPointerCount() == 1) {
            float x = event.getX();
            float y = event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    onTouchStart(x, y);
                    invalidate();
                    break;
                case MotionEvent.ACTION_MOVE:
                    onTouchMove(x, y);
                    invalidate();
                    break;
                case MotionEvent.ACTION_UP:
                    onTouchUp();
                    invalidate();
                    break;
                default:
                    break;
            }
            return true;
        } else {
            if (mTouchMode == TouchMode.IMAGE) {
                return super.onTouchEvent(event);
            } else {
                return false;
            }
        }
    }

    private void onTouchStart(float x, float y) {

        mMoved = false;

        tmpPath.reset();

        if (null != mPaint) {
            tmpPath.moveTo(x, y);
        }

        mX = x;
        mY = y;
        mStartX = x;
        mStartY = y;

        if (mDrawListener != null) {
            float[] mappedPoints = new float[2];
            mappedPoints[0] = x;
            mappedPoints[1] = y;
            mInvertedMatrix.mapPoints(mappedPoints);
            mDrawListener.onDrawStart(mappedPoints, mBrushSize / mCurrentScale);
        }
    }

    private void onTouchMove(float x, float y) {

        float dx = Math.abs(x - mX);
        float dy = Math.abs(y - mY);

        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {

            if (!mMoved && null != mPaint) {
                tmpPath.setLastPoint(mX, mY);
            }

            mMoved = true;

            if (mRestiction > 0) {
                double r = Math.sqrt(Math.pow(x - mStartX, 2) + Math.pow(y - mStartY, 2));
                double theta = Math.atan2(y - mStartY, x - mStartX);

                final float w = getWidth();
                final float h = getHeight();

                double scale = (mRestiction / mCurrentScale) / (double) (w + h) / (mBrushSize / mCurrentScale);
                double rNew = Math.log(r * scale + 1) / scale;

                x = (float) (mStartX + rNew * Math.cos(theta));
                y = (float) (mStartY + rNew * Math.sin(theta));
            }

            mX = x;
            mY = y;

            if (null != mPaint) {
                tmpPath.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2);
                // tmpPath.addCircle( mX, mY, 2, Direction.CW );
            }
        }

        if (mDrawListener != null) {
            float[] mappedPoints = new float[2];
            mappedPoints[0] = x;
            mappedPoints[1] = y;
            mInvertedMatrix.mapPoints(mappedPoints);
            mDrawListener.onDrawing(mappedPoints, mBrushSize / mCurrentScale);
        }
    }

    private void onTouchUp() {

        if (mDrawListener != null) {
            mDrawListener.onDrawEnd();
        }
    }

    public void setDrawLimit(double value) {
        mRestiction = value;
    }

    public void setBrushSize(float value) {
        mBrushSize = value;

        if (mPaint != null) {
            mPaint.setStrokeWidth(mBrushSize);
        }
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
            Log.i(TAG, "onDrawModeChanged");

            Matrix m1 = new Matrix(getImageMatrix());
            mInvertedMatrix.reset();

            float[] v1 = getMatrixValues(m1);
            m1.invert(m1);
            float[] v2 = getMatrixValues(m1);

            mInvertedMatrix.postTranslate(-v1[Matrix.MTRANS_X], -v1[Matrix.MTRANS_Y]);
            mInvertedMatrix.postScale(v2[Matrix.MSCALE_X], v2[Matrix.MSCALE_Y]);
            mCanvas.setMatrix(mInvertedMatrix);

            mCurrentScale = getScale() * getBaseScale();

            if (null != mPaint) {
                mPaint.setStrokeWidth(mBrushSize);
            }
        }
    }

    public static float[] getMatrixValues(Matrix m) {
        float[] values = new float[9];
        m.getValues(values);
        return values;
    }

    public Paint getPaint() {
        return mPaint;
    }

    public void setPaint(Paint paint) {
        mPaint.set(paint);
    }

    public void setPaintEnabled(boolean enabled) {
        if (enabled) {
            mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            mPaint.setFilterBitmap(false);
            mPaint.setDither(true);
            mPaint.setColor(0x66FFFFCC);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeCap(Paint.Cap.ROUND);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (null != mPaint) {
            canvas.drawPath(tmpPath, mPaint);
        }

    }

    public RectF getImageRect() {
        if (getDrawable() != null) {
            return new RectF(0, 0, getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight());
        } else {
            return null;
        }
    }

    @Override
    protected void onDrawableChanged(Drawable drawable) {
        super.onDrawableChanged(drawable);

        if (drawable != null && (drawable instanceof IBitmapDrawable)) {
            mCanvas = new Canvas();
            mCanvas.drawColor(0);
            onDrawModeChanged();
        }
    }

    public static enum TouchMode {
        // mode for pan and zoom
        IMAGE,
        // mode for drawing
        DRAW
    }

    public interface OnDrawListener {
        void onDrawStart(float[] points, float radius);

        void onDrawing(float[] points, float radius);

        void onDrawEnd();
    }
}
