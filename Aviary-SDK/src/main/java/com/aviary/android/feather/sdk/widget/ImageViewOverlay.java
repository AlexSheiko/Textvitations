package com.aviary.android.feather.sdk.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;

import com.aviary.android.feather.headless.moa.MoaPointParameter;
import com.aviary.android.feather.headless.moa.MoaRectParameter;
import com.aviary.android.feather.library.filters.OverlayFilter;

import it.sephiroth.android.library.imagezoom.ImageViewTouch;
import it.sephiroth.android.library.imagezoom.graphics.FastBitmapDrawable;

public class ImageViewOverlay extends ImageViewTouch {
    private static final int MAX_VIEWPORT_SIZE = 2048;
    protected Drawable mOverlayDrawable;
    protected Drawable mOverlayTempDrawable;
    protected Matrix   mDrawMatrix2;
    protected Matrix mBaseMatrix2       = new Matrix();
    protected Matrix mSuppMatrix2       = new Matrix();
    protected Matrix mDisplayMatrix2    = new Matrix();
    protected Matrix mMatrix2           = new Matrix();
    protected RectF  mOverlayBitmapRect = new RectF();
    RectF mCanvasClipRect = new RectF();
    private   RectF  mTempViewPort      = new RectF();
    private int mOverlayDrawableWidth, mOverlayDrawableHeight;
    private boolean mOverlayChanged;

    public ImageViewOverlay(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public ImageViewOverlay(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void init(final Context context, final AttributeSet attrs, final int defStyle) {
        super.init(context, attrs, defStyle);
    }

    @Override
    public DisplayType getDisplayType() {
        // force fit to screen
        return DisplayType.FIT_TO_SCREEN;
    }

    @Override
    protected void onViewPortChanged(final float left, final float top, final float right, final float bottom) {
        if (null == mOverlayDrawable) {
            super.onViewPortChanged(left, top, right, bottom);
        } else {
            super.onViewPortChanged((int) Math.ceil(mTempViewPort.left),
                                    (int) Math.ceil(mTempViewPort.top),
                                    (int) Math.floor(mTempViewPort.right),
                                    (int) Math.floor(mTempViewPort.bottom));
        }
        mCanvasClipRect.set(mViewPort);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        Log.i(TAG, "onLayout(" + left + ", " + top + ", " + right + ", " + bottom + ")");

        mTempViewPort.set(left, top, right, bottom);

        if (mOverlayChanged) {
            mOverlayDrawable = mOverlayTempDrawable;
            mOverlayTempDrawable = null;

            if (null != mOverlayDrawable) {
                mOverlayDrawableWidth = mOverlayDrawable.getIntrinsicWidth();
                mOverlayDrawableHeight = mOverlayDrawable.getIntrinsicHeight();
            } else {
                mOverlayDrawableWidth = 0;
                mOverlayDrawableHeight = 0;
            }
            mOverlayChanged = false;
        }

        if (changed || mBitmapChanged) {
            Drawable drawable = getDrawable();
            if (null != drawable && null != mOverlayDrawable) {

                Log.v(TAG, "bitmap size: " + drawable.getIntrinsicWidth() + "x" + drawable.getIntrinsicHeight());

                int dwidth = Math.min(right - left, Math.min(drawable.getIntrinsicWidth(), MAX_VIEWPORT_SIZE));
                int dheight = Math.min(bottom - top, Math.min(drawable.getIntrinsicHeight(), MAX_VIEWPORT_SIZE));

                if (mTempViewPort.width() > dwidth || mTempViewPort.height() > dheight) {
                    float widthScale, heightScale;

                    Matrix matrix = new Matrix();
                    widthScale = dwidth / mTempViewPort.width();
                    heightScale = dheight / mTempViewPort.height();
                    float scale = Math.max(widthScale, heightScale);
                    matrix.postScale(scale, scale, mTempViewPort.centerX(), mTempViewPort.centerY());
                    matrix.mapRect(mTempViewPort);
                }
            }
            changed = true;
        }

        if (null != mOverlayDrawable) {
            if (changed || mBitmapChanged) {
                mBaseMatrix2.reset();
                mSuppMatrix2.reset();

                getProperBaseMatrix2(mOverlayDrawable, mBaseMatrix2, mTempViewPort);
                setImageMatrix2(getImageViewMatrix2());

                mTempViewPort.set(getOverlayBitmapRect());
            }
        }

        super.onLayout(changed, left, top, right, bottom);
    }

    protected void getProperBaseMatrix2(Drawable drawable, Matrix matrix, RectF rect) {
        float w = drawable.getIntrinsicWidth();
        float h = drawable.getIntrinsicHeight();
        float widthScale, heightScale;

        matrix.reset();

        widthScale = rect.width() / w;
        heightScale = rect.height() / h;
        float scale = Math.min(widthScale, heightScale);
        matrix.postScale(scale, scale);
        matrix.postTranslate(rect.left, rect.top);

        float tw = (rect.width() - w * scale) / 2.0f;
        float th = (rect.height() - h * scale) / 2.0f;
        matrix.postTranslate(tw, th);
        printMatrix(matrix);
    }

    public void setImageMatrix2(Matrix matrix) {
        if (matrix != null && matrix.isIdentity()) {
            matrix = null;
        }

        // don't invalidate unless we're actually changing our matrix
        if (matrix == null && !mMatrix2.isIdentity() || matrix != null && !mMatrix2.equals(matrix)) {
            mMatrix2.set(matrix);
            configureBounds2();
            invalidate();
        }
    }

    public Matrix getImageViewMatrix2() {
        return getImageViewMatrix2(mSuppMatrix2);
    }

    public RectF getOverlayBitmapRect() {
        return getOverlayBitmapRect(mSuppMatrix2);
    }

    private void configureBounds2() {
        if (mOverlayDrawable == null) {
            return;
        }

        int dwidth = mOverlayDrawableWidth;
        int dheight = mOverlayDrawableHeight;

        int vwidth = getWidth();
        int vheight = getHeight();

        if (dwidth <= 0 || dheight <= 0) {
            mOverlayDrawable.setBounds(0, 0, vwidth, vheight);
            mDrawMatrix2 = null;
        } else {
            mOverlayDrawable.setBounds(0, 0, dwidth, dheight);

            if (mMatrix2.isIdentity()) {
                mDrawMatrix2 = null;
            } else {
                mDrawMatrix2 = mMatrix2;
            }
        }
    }

    public Matrix getImageViewMatrix2(Matrix supportMatrix) {
        mDisplayMatrix2.set(mBaseMatrix2);
        mDisplayMatrix2.postConcat(supportMatrix);
        return mDisplayMatrix2;
    }

    protected RectF getOverlayBitmapRect(Matrix supportMatrix) {
        final Drawable drawable = mOverlayDrawable;

        if (drawable == null) {
            return null;
        }
        Matrix m = getImageViewMatrix2(supportMatrix);
        mOverlayBitmapRect.set(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        m.mapRect(mOverlayBitmapRect);
        return mOverlayBitmapRect;
    }

    @Override
    protected float computeMinZoom() {
        if (null == mOverlayDrawable) {
            return super.computeMinZoom();
        }
        return 1;
    }

    @Override
    protected void getProperBaseMatrix(Drawable drawable, Matrix matrix, RectF rect) {
        if (null == mOverlayDrawable) {
            super.getProperBaseMatrix(drawable, matrix, rect);
            return;
        }

        float w = drawable.getIntrinsicWidth();
        float h = drawable.getIntrinsicHeight();
        float widthScale, heightScale;

        matrix.reset();

        widthScale = rect.width() / w;
        heightScale = rect.height() / h;
        float scale = Math.max(widthScale, heightScale);
        matrix.postScale(scale, scale);
        matrix.postTranslate(rect.left, rect.top);

        float tw = (rect.width() - w * scale) / 2.0f;
        float th = (rect.height() - h * scale) / 2.0f;
        matrix.postTranslate(tw, th);
        printMatrix(matrix);
    }

    public void setImageBitmap(final Bitmap bitmap, final Bitmap overlay) {
        if (null != overlay) {
            mOverlayTempDrawable = new FastBitmapDrawable(overlay);
        } else {
            mOverlayTempDrawable = null;
        }
        mOverlayChanged = true;
        super.setImageBitmap(bitmap, null, -1, -1);
    }

    public void updateImageOverlay(final Bitmap overlay) {
        if (mOverlayDrawable == null || null == overlay) {
            return;
        }

        if (mOverlayDrawable.getIntrinsicWidth() == overlay.getWidth()
            && mOverlayDrawable.getIntrinsicHeight() == overlay.getHeight()) {
            mOverlayDrawable = new FastBitmapDrawable(overlay);
            invalidate();
        } else {
            setImageDrawable(getDrawable(), overlay);
        }
    }

    public void setImageDrawable(final Drawable drawable, final Bitmap overlay) {
        if (null != overlay) {
            mOverlayTempDrawable = new FastBitmapDrawable(overlay);
        } else {
            mOverlayTempDrawable = null;
        }
        mOverlayChanged = true;
        super.setImageDrawable(drawable, null, -1, -1);
    }

    @SuppressWarnings ("unused")
    public Drawable getOverlayDrawable() {
        return mOverlayDrawable;
    }

    @SuppressLint ("WrongCall")
    public Bitmap generateResultBitmap(OverlayFilter filter) {
        if (null == mOverlayDrawable) {
            return null;
        }

        Drawable drawable = getDrawable();
        if (null == drawable) {
            return null;
        }

        RectF rect = getOverlayBitmapRect();

        Bitmap bitmap = Bitmap.createBitmap((int) rect.width(), (int) rect.height(), Bitmap.Config.ARGB_8888);

        filter.getActions()
            .get(0)
            .setValue("previewsize", new MoaPointParameter(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight()));
        filter.getActions().get(0).setValue("bitmaprect", new MoaRectParameter(getBitmapRect()));
        filter.getActions().get(0).setValue("overlayrect", new MoaRectParameter(getOverlayBitmapRect()));
        filter.getActions().get(0).setValue("overlaysize", new MoaPointParameter(mOverlayDrawableWidth, mOverlayDrawableHeight));

        Canvas canvas = new Canvas(bitmap);
        canvas.translate(-rect.left, -rect.top);
        onDraw(canvas);
        return bitmap;
    }

    @Override
    protected void onDraw(final Canvas canvas) {

        Matrix drawMatrix = getImageMatrix();
        Drawable drawable = getDrawable();
        int saveCount;

        if (null == drawable) {
            return;
        }

        saveCount = canvas.getSaveCount();
        canvas.save();

        canvas.clipRect(mCanvasClipRect);

        if (drawMatrix != null) {
            canvas.concat(drawMatrix);
        }
        drawable.draw(canvas);
        canvas.restoreToCount(saveCount);

        if (mOverlayDrawable == null) {
            return;
        }
        if (mOverlayDrawableWidth == 0 || mOverlayDrawableHeight == 0) {
            return;
        }

        if (mDrawMatrix2 != null) {
            saveCount = canvas.getSaveCount();
            canvas.save();
            canvas.concat(mDrawMatrix2);
            mOverlayDrawable.draw(canvas);
            canvas.restoreToCount(saveCount);
        }
    }
}
