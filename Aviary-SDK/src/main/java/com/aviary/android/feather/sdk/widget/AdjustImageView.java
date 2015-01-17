package com.aviary.android.feather.sdk.widget;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import com.aviary.android.feather.common.utils.ReflectionException;
import com.aviary.android.feather.common.utils.ReflectionUtils;
import com.aviary.android.feather.library.graphics.Point2D;
import com.aviary.android.feather.library.graphics.animation.EasingType;
import com.aviary.android.feather.library.graphics.animation.ExpoInterpolator;
import com.aviary.android.feather.sdk.R;
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.AnimatorSet;
import com.nineoldandroids.animation.ObjectAnimator;
import com.nineoldandroids.animation.ValueAnimator;

public class AdjustImageView extends View {
    static final         String              LOG_TAG       = "rotate";
    // click tolerance around the knob
    private static final int                 HIT_TOLERANCE = 60;
    private static final Matrix.ScaleToFit[] SS2F_ARRAY    = {
        Matrix.ScaleToFit.FILL, Matrix.ScaleToFit.START, Matrix.ScaleToFit.CENTER, Matrix.ScaleToFit.END
    };
    protected final      float[]             mMatrixValues = new float[9];
    final                int                 gridRows      = 8;
    final                int                 gridCols      = 8;
    protected            Handler             mHandler      = new Handler();
    protected            double              mRotation     = 0;
    protected            float               mCurrentScale = 0;
    protected            boolean             mRunning      = false;
    protected            int                 mFlipType     = FlipType.FLIP_NONE.nativeInt;
    boolean isReset = false;
    long mResetAnimationDuration;
    long mAnimationDuration;
    Path  mClipPath       = new Path();
    Path  mInversePath    = new Path();
    Rect  mViewDrawRect   = new Rect();
    RectF mViewInvertRect = new RectF();
    Paint mOutlinePaint   = new Paint();
    Paint mOutlineFill    = new Paint();
    RectF mDrawRect;
    Path  mLinesPath  = new Path();
    Paint mLinesPaint = new Paint();
    Drawable mStraightenDrawable;
    int      handleWidth, handleHeight;
    int mOutlinePaintAlpha, mOutlineFillAlpha, mLinesAlpha;
    boolean straightenStarted       = false;
    double  previousStraightenAngle = 0;
    double  prevGrowth              = 1;
    boolean testStraighten          = true;
    float   currentGrowth           = 0;
    Matrix  mStraightenMatrix       = new Matrix();
    double  previousAngle           = 0;
    boolean portrait                = false;
    int     orientation             = 0; // the orientation of the screen, whether in landscape or
    int     mActivePointerId        = -1;
    int     mActivePointerIndex     = -1;
    RectF   imageCaptureRegion      = null;
    boolean initStraighten          = true;
    boolean     mFadeHandlerStarted;
    Animator    mFadeAnimator;
    AnimatorSet mFadeOutlinesAnimator;
    private Uri mUri;
    private int mResource = 0;
    private Matrix    mMatrix;
    private ScaleType mScaleType;
    private boolean mAdjustViewBounds = false;
    private int     mMaxWidth         = Integer.MAX_VALUE;
    private int     mMaxHeight        = Integer.MAX_VALUE;
    private ColorFilter mColorFilter;
    private int      mAlpha          = 255;
    private int      mViewAlphaScale = 256;
    private boolean  mColorMod       = false;
    private Drawable mDrawable       = null;
    private int[]    mState          = null;
    private boolean  mMergeState     = false;
    private int      mLevel          = 0;
    private int mDrawableWidth;
    private int mDrawableHeight;
    private Matrix  mDrawMatrix          = null;
    private Matrix  mTempMatrix          = new Matrix();
    private Matrix  mRotateMatrix        = new Matrix();
    private Matrix  mFlipMatrix          = new Matrix();
    private RectF   mTempSrc             = new RectF();
    private RectF   mTempDst             = new RectF();
    private int     mBaseline            = -1;
    private boolean mBaselineAlignBottom = false;
    private boolean         mHaveFrame;
    private PointF          mCenter;
    private boolean         mEnableFreeRotate;
    private OnResetListener mResetListener;
    private float           mLastTouchX;
    private float           mPosX;
    private boolean         mIsInStraighten;
    // portrait
    private boolean         mCameraEnabled;

    public AdjustImageView(Context context) {
        this(context, null);
    }

    public AdjustImageView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.aviaryAdjustImageViewStyle);
    }

    public AdjustImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initImageView(context, attrs, defStyle);
    }

    private void initImageView(Context context, AttributeSet attrs, int defStyle) {

        mMatrix = new Matrix();
        mScaleType = ScaleType.FIT_CENTER;

        // obtain the image style
        Theme theme = context.getTheme();
        TypedArray array = theme.obtainStyledAttributes(attrs, R.styleable.AviaryAdjustImageView, defStyle, 0);

        mStraightenDrawable = array.getDrawable(R.styleable.AviaryAdjustImageView_aviary_handle);

        int strokeColor1 = array.getColor(R.styleable.AviaryAdjustImageView_aviary_strokeColor, 0);
        int strokeColor2 = array.getColor(R.styleable.AviaryAdjustImageView_aviary_strokeColor2, 0);

        int strokeWidth1 = array.getDimensionPixelSize(R.styleable.AviaryAdjustImageView_aviary_strokeWidth, 2);
        int strokeWidth2 = array.getDimensionPixelSize(R.styleable.AviaryAdjustImageView_aviary_strokeWidth2, 1);

        int fillColor = array.getColor(R.styleable.AviaryAdjustImageView_aviary_color1, 0);

        mAnimationDuration = array.getInteger(R.styleable.AviaryAdjustImageView_aviary_animationDuration, 400);
        mResetAnimationDuration = array.getInteger(R.styleable.AviaryAdjustImageView_aviary_animationDuration2, 200);

        boolean cameraEnabled = array.getBoolean(R.styleable.AviaryAdjustImageView_aviary_enable3d, false);
        boolean freeRotate = array.getBoolean(R.styleable.AviaryAdjustImageView_aviary_freeRotate, true);

        array.recycle();

        setCameraEnabled(cameraEnabled);
        setEnableFreeRotate(freeRotate);

        double w = mStraightenDrawable.getIntrinsicWidth();
        double h = mStraightenDrawable.getIntrinsicHeight();
        handleWidth = (int) Math.ceil(w / 2);
        handleHeight = (int) Math.ceil(h / 2);

        mOutlinePaint.setStrokeWidth(strokeWidth1);
        mOutlinePaint.setStyle(Paint.Style.STROKE);
        mOutlinePaint.setAntiAlias(true);
        mOutlinePaint.setColor(strokeColor1);

        mOutlineFill.setStyle(Paint.Style.FILL);
        mOutlineFill.setAntiAlias(false);
        mOutlineFill.setColor(fillColor);
        mOutlineFill.setDither(false);

        try {
            ReflectionUtils.invokeMethod(mOutlineFill, "setHinting", new Class<?>[]{int.class}, 0);
        } catch (ReflectionException e) {
        }

        mLinesPaint.setStrokeWidth(strokeWidth2);
        mLinesPaint.setAntiAlias(false);
        mLinesPaint.setDither(false);
        mLinesPaint.setStyle(Paint.Style.STROKE);
        mLinesPaint.setColor(strokeColor2);
        try {
            ReflectionUtils.invokeMethod(mLinesPaint, "setHinting", new Class<?>[]{int.class}, 0);
        } catch (ReflectionException e) {
        }

        mOutlineFillAlpha = mOutlineFill.getAlpha();
        mOutlinePaintAlpha = mOutlinePaint.getAlpha();
        mLinesAlpha = mLinesPaint.getAlpha();

        mOutlinePaint.setAlpha(0);
        mOutlineFill.setAlpha(0);
        mLinesPaint.setAlpha(0);
    }

    public void setCameraEnabled(final boolean value) {
        mCameraEnabled = android.os.Build.VERSION.SDK_INT >= 14 && value;
    }

    public void setEnableFreeRotate(boolean value) {
        mEnableFreeRotate = value;
    }

    public boolean isFreeRotateEnabled() {
        return mEnableFreeRotate;
    }

    public void setOnResetListener(OnResetListener listener) {
        mResetListener = listener;
    }

    private RectF getViewRect() {
        final int vwidth = getWidth() - getPaddingLeft() - getPaddingRight();
        final int vheight = getHeight() - getPaddingTop() - getPaddingBottom();
        return new RectF(0, 0, vwidth, vheight);
    }

    private void setImageRotation(double angle, boolean invert) {
        PointF center = getCenter();

        Matrix tempMatrix = new Matrix(mDrawMatrix);
        RectF src = getImageRect();
        RectF dst = getViewRect();

        tempMatrix.setRotate((float) angle, center.x, center.y);
        tempMatrix.mapRect(src);
        tempMatrix.setRectToRect(src, dst, scaleTypeToScaleToFit(mScaleType));

        float[] scale = getMatrixScale(tempMatrix);
        float fScale = Math.min(scale[0], scale[1]);

        if (invert) {
            mRotateMatrix.setRotate((float) angle, center.x, center.y);
            mRotateMatrix.postScale(fScale, fScale, center.x, center.y);
        } else {
            mRotateMatrix.setScale(fScale, fScale, center.x, center.y);
            mRotateMatrix.postRotate((float) angle, center.x, center.y);
        }

    }

    public double getGrowthFactor() {
        return prevGrowth;
    }

    public double getStraightenAngle() {
        return previousStraightenAngle;
    }

    /**
     * The top level call for the straightening of the image
     *
     * @param newPosition - the destination angle for the image
     * @param durationMs  - animation time
     */
    public void straightenBy(final double newPosition, final int newx, final long durationMs) {
        if (mRunning) {
            Log.w(LOG_TAG, "still running!..");
            return;
        }

        mRunning = true;
        straightenStarted = true;

        final long startTime = System.currentTimeMillis();

        final int srcx = mStraightenDrawable.getBounds().centerX();
        final float deltax = newx - srcx;

        final double destRotation = getStraightenAngle() + newPosition;
        final double srcRotation = getStraightenAngle();
        invalidate();

        ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
        animator.setDuration(durationMs);
        animator.setInterpolator(new ExpoInterpolator(EasingType.Type.INOUT));
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(final ValueAnimator animation) {
                float value = (Float) animation.getAnimatedValue();

                mStraightenDrawable.setBounds((int) (srcx + (deltax * value) - handleWidth),
                                              (int) (imageCaptureRegion.bottom - handleHeight),
                                              (int) (srcx + (deltax * value) + handleWidth),
                                              (int) (imageCaptureRegion.bottom + handleHeight));

                setStraightenRotation(srcRotation + (newPosition * value));
                invalidate();
            }
        });
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(final Animator animation) {

            }

            @Override
            public void onAnimationEnd(final Animator animation) {
                mStraightenDrawable.setBounds(newx - handleWidth,
                                              (int) (imageCaptureRegion.bottom - handleHeight),
                                              newx + handleWidth,
                                              (int) (imageCaptureRegion.bottom + handleHeight));
                setStraightenRotation(destRotation);
                mRunning = false;
                invalidate();

                if (isReset) {
                    straightenStarted = false;
                    onReset();
                }
            }

            @Override
            public void onAnimationCancel(final Animator animation) {

            }

            @Override
            public void onAnimationRepeat(final Animator animation) {

            }
        });
        animator.start();
    }

    private double getRotationFromMatrix(Matrix matrix) {
        float[] pts = {0, 0, 0, -100};
        matrix.mapPoints(pts);
        double angle = Point2D.angleBetweenPoints(pts[0], pts[1], pts[2], pts[3], 0);
        return -angle;
    }

    /**
     * Set this to true if you want the ImageView to adjust its bounds to preserve the
     * aspect ratio of its drawable.
     *
     * @param adjustViewBounds Whether to adjust the bounds of this view to presrve the original aspect
     *                         ratio of the drawable
     * @attr ref android.R.styleable#ImageView_adjustViewBounds
     */
    public void setAdjustViewBounds(boolean adjustViewBounds) {
        mAdjustViewBounds = adjustViewBounds;
        if (adjustViewBounds) {
            setScaleType(ScaleType.FIT_CENTER);
        }
    }

    public void setMaxWidth(int maxWidth) {
        mMaxWidth = maxWidth;
    }

    public void setMaxHeight(int maxHeight) {
        mMaxHeight = maxHeight;
    }

    /**
     * Return the view's drawable, or null if no drawable has been assigned.
     *
     * @return the drawable
     */
    public Drawable getDrawable() {
        return mDrawable;
    }

    /**
     * Sets a drawable as the content of this ImageView.
     * <p class="note">
     * This does Bitmap reading and decoding on the UI thread, which can cause a latency
     * hiccup. If that's a concern, consider using
     *
     * @param resId the resource identifier of the the drawable
     *              {@link #setImageDrawable(android.graphics.drawable.Drawable)} or
     *              {@link #setImageBitmap(android.graphics.Bitmap)} and
     *              {@link android.graphics.BitmapFactory} instead.
     *              </p>
     * @attr ref android.R.styleable#ImageView_src
     */
    public void setImageResource(int resId) {
        if (mUri != null || mResource != resId) {
            updateDrawable(null);
            mResource = resId;
            mUri = null;
            resolveUri();
            requestLayout();
            invalidate();
        }
    }

    /**
     * Sets the content of this ImageView to the specified Uri.
     * <p class="note">
     * This does Bitmap reading and decoding on the UI thread, which can cause a latency
     * hiccup. If that's a concern, consider using
     *
     * @param uri The Uri of an image
     *            {@link #setImageDrawable(android.graphics.drawable.Drawable)} or
     *            {@link #setImageBitmap(android.graphics.Bitmap)} and
     *            {@link android.graphics.BitmapFactory} instead.
     *            </p>
     */
    public void setImageURI(Uri uri) {
        if (mResource != 0 || (mUri != uri && (uri == null || mUri == null || !uri.equals(mUri)))) {
            updateDrawable(null);
            mResource = 0;
            mUri = uri;
            resolveUri();
            requestLayout();
            invalidate();
        }
    }

    /**
     * Sets a Bitmap as the content of this ImageView.
     *
     * @param bm The bitmap to set
     */
    public void setImageBitmap(Bitmap bm) {
        // if this is used frequently, may handle bitmaps explicitly
        // to reduce the intermediate drawable object
        setImageDrawable(new BitmapDrawable(getContext().getResources(), bm));
    }

    /**
     * Sets a drawable as the content of this ImageView.
     *
     * @param drawable The drawable to set
     */
    public void setImageDrawable(Drawable drawable) {
        if (mDrawable != drawable) {
            mResource = 0;
            mUri = null;

            int oldWidth = mDrawableWidth;
            int oldHeight = mDrawableHeight;

            updateDrawable(drawable);

            if (oldWidth != mDrawableWidth || oldHeight != mDrawableHeight) {
                requestLayout();
            }
            invalidate();
        }
    }

    private void updateDrawable(Drawable d) {
        if (mDrawable != null) {
            mDrawable.setCallback(null);
            unscheduleDrawable(mDrawable);
        }
        mDrawable = d;
        if (d != null) {
            d.setCallback(this);
            if (d.isStateful()) {
                d.setState(getDrawableState());
            }
            d.setLevel(mLevel);
            mDrawableWidth = d.getIntrinsicWidth();
            mDrawableHeight = d.getIntrinsicHeight();
            applyColorMod();
            configureBounds();
        } else {
            mDrawableWidth = -1;
            mDrawableHeight = -1;
        }
    }

    @SuppressWarnings("checkstyle:cyclomaticcomplexity")
    private void configureBounds() {
        if (mDrawable == null || !mHaveFrame) {
            return;
        }

        int dwidth = mDrawableWidth;
        int dheight = mDrawableHeight;

        int vwidth = getWidth() - getPaddingLeft() - getPaddingRight();
        int vheight = getHeight() - getPaddingTop() - getPaddingBottom();

        boolean fits = (dwidth < 0 || vwidth == dwidth) && (dheight < 0 || vheight == dheight);

        if (dwidth <= 0 || dheight <= 0 || ScaleType.FIT_XY == mScaleType) {
            /*
            * If the drawable has no intrinsic size, or we're told to scaletofit, then we
            * just fill our entire view.
            */
            mDrawable.setBounds(0, 0, vwidth, vheight);
            mDrawMatrix = null;
        } else {
            // We need to do the scaling ourself, so have the drawable
            // use its native size.
            mDrawable.setBounds(0, 0, dwidth, dheight);

            if (ScaleType.MATRIX == mScaleType) {
                // Use the specified matrix as-is.
                if (mMatrix.isIdentity()) {
                    mDrawMatrix = null;
                } else {
                    mDrawMatrix = mMatrix;
                }
            } else if (fits) {
                // The bitmap fits exactly, no transform needed.
                mDrawMatrix = null;
            } else if (ScaleType.CENTER == mScaleType) {
                // Center bitmap in view, no scaling.
                mDrawMatrix = mMatrix;
                mDrawMatrix.setTranslate((int) ((vwidth - dwidth) * 0.5f + 0.5f), (int) ((vheight - dheight) * 0.5f + 0.5f));
            } else if (ScaleType.CENTER_CROP == mScaleType) {
                mDrawMatrix = mMatrix;

                float scale;
                float dx = 0, dy = 0;

                if (dwidth * vheight > vwidth * dheight) {
                    scale = (float) vheight / (float) dheight;
                    dx = (vwidth - dwidth * scale) * 0.5f;
                } else {
                    scale = (float) vwidth / (float) dwidth;
                    dy = (vheight - dheight * scale) * 0.5f;
                }

                mDrawMatrix.setScale(scale, scale);
                mDrawMatrix.postTranslate((int) (dx + 0.5f), (int) (dy + 0.5f));
            } else if (ScaleType.CENTER_INSIDE == mScaleType) {
                mDrawMatrix = mMatrix;
                float scale;
                float dx;
                float dy;

                if (dwidth <= vwidth && dheight <= vheight) {
                    scale = 1.0f;
                } else {
                    scale = Math.min((float) vwidth / (float) dwidth, (float) vheight / (float) dheight);
                }

                dx = (int) ((vwidth - dwidth * scale) * 0.5f + 0.5f);
                dy = (int) ((vheight - dheight * scale) * 0.5f + 0.5f);

                mDrawMatrix.setScale(scale, scale);
                mDrawMatrix.postTranslate(dx, dy);
            } else {
                // Generate the required transform.
                mTempSrc.set(0, 0, dwidth, dheight);
                mTempDst.set(0, 0, vwidth, vheight);

                mDrawMatrix = mMatrix;
                mDrawMatrix.setRectToRect(mTempSrc, mTempDst, scaleTypeToScaleToFit(mScaleType));

                mCurrentScale = getMatrixScale(mDrawMatrix)[0];

                Matrix tempMatrix = new Matrix(mMatrix);
                RectF src = new RectF();
                RectF dst = new RectF();
                src.set(0, 0, dheight, dwidth);
                dst.set(0, 0, vwidth, vheight);
                tempMatrix.setRectToRect(src, dst, scaleTypeToScaleToFit(mScaleType));

                tempMatrix = new Matrix(mDrawMatrix);
                tempMatrix.invert(tempMatrix);

                float invertScale = getMatrixScale(tempMatrix)[0];

                mDrawMatrix.postScale(invertScale, invertScale, vwidth / 2, vheight / 2);

                mRotateMatrix.reset();
                mStraightenMatrix.reset();
                mFlipMatrix.reset();
                mFlipType = FlipType.FLIP_NONE.nativeInt;
                mRotation = 0;
                mRotateMatrix.postScale(mCurrentScale, mCurrentScale, vwidth / 2, vheight / 2);
                mDrawRect = getImageRect();
                getCenter();
            }
        }
    }

    private static Matrix.ScaleToFit scaleTypeToScaleToFit(ScaleType st) {
        // ScaleToFit enum to their corresponding Matrix.ScaleToFit values
        return SS2F_ARRAY[st.nativeInt - 1];
    }

    protected float[] getMatrixScale(Matrix matrix) {
        float[] result = new float[2];
        result[0] = getValue(matrix, Matrix.MSCALE_X);
        result[1] = getValue(matrix, Matrix.MSCALE_Y);
        return result;
    }

    private RectF getImageRect() {
        return new RectF(0, 0, mDrawableWidth, mDrawableHeight);
    }

    protected float getValue(Matrix matrix, int whichValue) {
        matrix.getValues(mMatrixValues);
        return mMatrixValues[whichValue];
    }

    public void setImageState(int[] state, boolean merge) {
        mState = state;
        mMergeState = merge;
        if (mDrawable != null) {
            refreshDrawableState();
            resizeFromDrawable();
        }
    }

    private void resizeFromDrawable() {
        Drawable d = mDrawable;
        if (d != null) {
            int w = d.getIntrinsicWidth();
            if (w < 0) {
                w = mDrawableWidth;
            }
            int h = d.getIntrinsicHeight();
            if (h < 0) {
                h = mDrawableHeight;
            }
            if (w != mDrawableWidth || h != mDrawableHeight) {
                mDrawableWidth = w;
                mDrawableHeight = h;
                requestLayout();
            }
        }
    }

    public void setImageLevel(int level) {
        mLevel = level;
        if (mDrawable != null) {
            mDrawable.setLevel(level);
            resizeFromDrawable();
        }
    }

    public ScaleType getScaleType() {
        return mScaleType;
    }

    public void setScaleType(ScaleType scaleType) {
        if (scaleType == null) {
            throw new NullPointerException();
        }

        if (mScaleType != scaleType) {
            mScaleType = scaleType;

            setWillNotCacheDrawing(mScaleType == ScaleType.CENTER);

            requestLayout();
            invalidate();
        }
    }

    /**
     * Return the view's optional matrix. This is applied to the view's drawable when it
     * is drawn. If there is not matrix, this
     * method will return null. Do not change this matrix in place. If you want a
     * different matrix applied to the drawable, be sure
     * to call setImageMatrix().
     *
     * @return the image matrix
     */
    public Matrix getImageMatrix() {
        return mMatrix;
    }

    /**
     * Sets the image matrix.
     *
     * @param matrix the new image matrix
     */
    public void setImageMatrix(Matrix matrix) {
        // collaps null and identity to just null
        if (matrix != null && matrix.isIdentity()) {
            matrix = null;
        }

        // don't invalidate unless we're actually changing our matrix
        if (matrix == null && !mMatrix.isIdentity() || matrix != null && !mMatrix.equals(matrix)) {
            mMatrix.set(matrix);
            configureBounds();
            invalidate();
        }
    }

    public int getGridAlpha() {
        return mLinesPaint.getAlpha();
    }

    public void setGridAlpha(int value) {
        mLinesPaint.setAlpha(value);
        postInvalidate();
    }

    public int getOutlineFillAlpha() {
        return mOutlineFill.getAlpha();
    }

    public void setOutlineFillAlpha(int value) {
        mOutlineFill.setAlpha(value);
        postInvalidate();
    }

    public int getOutlinePaintAlpha() {
        return mOutlinePaint.getAlpha();
    }

    public void setOutlinePaintAlpha(int value) {
        mOutlinePaint.setAlpha(value);
        postInvalidate();
    }

    public int getLinesAlpha() {
        return mLinesPaint.getAlpha();
    }

    public void setLinesAlpha(int value) {
        mLinesPaint.setAlpha(value);
        postInvalidate();
    }

    protected void fadeoutOutlines(final int durationMs) {
        if (null != mFadeOutlinesAnimator) {
            mFadeOutlinesAnimator.cancel();
        }

        mFadeOutlinesAnimator = new AnimatorSet();
        mFadeOutlinesAnimator.playTogether(ObjectAnimator.ofInt(this, "outlineFillAlpha", mOutlineFill.getAlpha(), 0),
                                           ObjectAnimator.ofInt(this, "outlinePaintAlpha", 0, mOutlinePaint.getAlpha(), 0),
                                           ObjectAnimator.ofInt(this, "linesAlpha", 0, mLinesPaint.getAlpha()));

        mFadeOutlinesAnimator.setDuration(durationMs);
        mFadeOutlinesAnimator.start();
    }

    protected void hideOutlines() {
        mFadeHandlerStarted = false;
        mOutlineFill.setAlpha(0);
        mOutlinePaint.setAlpha(0);
        mLinesPaint.setAlpha(0);
        invalidate();
    }

    public boolean getBaselineAlignBottom() {
        return mBaselineAlignBottom;
    }

    public void setBaselineAlignBottom(boolean aligned) {
        if (mBaselineAlignBottom != aligned) {
            mBaselineAlignBottom = aligned;
            requestLayout();
        }
    }

    public final void setColorFilter(int color) {
        setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
    }

    public final void setColorFilter(int color, PorterDuff.Mode mode) {
        setColorFilter(new PorterDuffColorFilter(color, mode));
    }

    public void setColorFilter(ColorFilter cf) {
        if (mColorFilter != cf) {
            mColorFilter = cf;
            mColorMod = true;
            applyColorMod();
            invalidate();
        }
    }

    public final void clearColorFilter() {
        setColorFilter(null);
    }

    public void setAlpha(int alpha) {
        alpha &= 0xFF; // keep it legal
        if (mAlpha != alpha) {
            mAlpha = alpha;
            mColorMod = true;
            applyColorMod();
            invalidate();
        }
    }

    public void rotate90(boolean cw) {
        rotate90(cw, mAnimationDuration);
    }

    public void rotate90(boolean cw, long durationMs) {
        final double destRotation = (cw ? 90 : -90);
        rotateBy(destRotation, durationMs);
        hideOutlines();
        portrait = !portrait;
    }

    public boolean getStraightenStarted() {
        return straightenStarted;
    }

    protected void rotateBy(final double deltaRotation, final long durationMs) {
        if (mRunning) {
            Log.w(LOG_TAG, "still running!..");
            return;
        }

        mRunning = true;

        final double destRotation = mRotation + deltaRotation;
        final double srcRotation = mRotation;

        setImageRotation(mRotation, false);
        invalidate();

        ValueAnimator animator = ValueAnimator.ofFloat((float) srcRotation, (float) destRotation);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(final ValueAnimator animation) {
                float value = (Float) animation.getAnimatedValue();
                mRotation = Point2D.angle360(value);
                setImageRotation(mRotation, false);
                initStraighten = true;
                invalidate();
            }
        });
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(final Animator animation) {

            }

            @Override
            public void onAnimationEnd(final Animator animation) {
                mRotation = Point2D.angle360(destRotation);
                setImageRotation(mRotation, true);
                initStraighten = true;
                mRunning = false;

                invalidate();
                printDetails();

                if (isReset) {
                    onReset();
                }
            }

            @Override
            public void onAnimationCancel(final Animator animation) {

            }

            @Override
            public void onAnimationRepeat(final Animator animation) {

            }
        });
        animator.setInterpolator(new ExpoInterpolator(EasingType.Type.INOUT));
        animator.setDuration(durationMs);
        animator.start();

        if (straightenStarted && !isReset) {
            initStraighten = true;
            resetStraighten();
            invalidate();
        }
    }

    public void printDetails() {
        Log.i(LOG_TAG, "details:");
        Log.d(LOG_TAG,
              " flip horizontal: " + ((mFlipType & FlipType.FLIP_HORIZONTAL.nativeInt) == FlipType.FLIP_HORIZONTAL.nativeInt));
        Log.d(LOG_TAG, " flip vertical: " + ((mFlipType & FlipType.FLIP_VERTICAL.nativeInt) == FlipType.FLIP_VERTICAL.nativeInt));
        Log.d(LOG_TAG, " rotation: " + mRotation);
        Log.d(LOG_TAG, "--------");
    }

    public void flip(boolean horizontal) {
        flip(horizontal, mAnimationDuration);
    }

    protected void flip(boolean horizontal, long durationMs) {
        flipTo(horizontal, durationMs);
        hideOutlines();
    }

    protected void flipTo(final boolean horizontal, final long durationMs) {
        if (mRunning) {
            Log.w(LOG_TAG, "still running!..");
            return;
        }

        mRunning = true;

        final int vwidth = getWidth() - getPaddingLeft() - getPaddingRight();
        final int vheight = getHeight() - getPaddingTop() - getPaddingBottom();
        final float centerx = vwidth / 2;
        final float centery = vheight / 2;

        final Camera camera = new Camera();

        ValueAnimator animator = ValueAnimator.ofFloat(mCameraEnabled ? 0 : 1, mCameraEnabled ? 180 : -1);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(final ValueAnimator animation) {
                float value = (Float) animation.getAnimatedValue();

                if (mCameraEnabled) {

                    camera.save();
                    if (horizontal) {
                        camera.rotateY(value);
                    } else {
                        camera.rotateX(value);
                    }
                    camera.getMatrix(mFlipMatrix);
                    camera.restore();
                    mFlipMatrix.preTranslate(-centerx, -centery);
                    mFlipMatrix.postTranslate(centerx, centery);
                } else {
                    if (horizontal) {
                        mFlipMatrix.setScale(value, 1, centerx, centery);
                    } else {
                        mFlipMatrix.setScale(1, value, centerx, centery);
                    }
                }
                invalidate();
            }
        });
        animator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(final Animator animation) {

            }

            @Override
            public void onAnimationEnd(final Animator animation) {
                if (horizontal) {
                    mFlipType ^= FlipType.FLIP_HORIZONTAL.nativeInt;
                    mDrawMatrix.postScale(-1, 1, centerx, centery);
                } else {
                    mFlipType ^= FlipType.FLIP_VERTICAL.nativeInt;
                    mDrawMatrix.postScale(1, -1, centerx, centery);
                }

                // Problem is HERE!
                mRotateMatrix.postRotate((float) (-mRotation * 2), centerx, centery);
                mRotation = Point2D.angle360(getRotationFromMatrix(mRotateMatrix));
                mFlipMatrix.reset();

                invalidate();
                printDetails();

                mRunning = false;

                if (isReset) {
                    onReset();
                }
            }

            @Override
            public void onAnimationCancel(final Animator animation) {

            }

            @Override
            public void onAnimationRepeat(final Animator animation) {

            }
        });
        animator.setInterpolator(new ExpoInterpolator(EasingType.Type.INOUT));
        animator.setDuration(durationMs);
        animator.start();

        if (straightenStarted && !isReset) {
            initStraighten = true;
            resetStraighten();
            invalidate();
        }
    }

    private void flip(boolean horizontal, boolean vertical) {

        invalidate();
        PointF center = getCenter();

        if (horizontal) {
            mFlipType ^= FlipType.FLIP_HORIZONTAL.nativeInt;
            mDrawMatrix.postScale(-1, 1, center.x, center.y);
        }

        if (vertical) {
            mFlipType ^= FlipType.FLIP_VERTICAL.nativeInt;
            mDrawMatrix.postScale(1, -1, center.x, center.y);
        }

        mRotateMatrix.postRotate((float) (-mRotation * 2), center.x, center.y);
        mRotation = Point2D.angle360(getRotationFromMatrix(mRotateMatrix));
        mFlipMatrix.reset();
    }

    public double getCurrentRotation() {
        return mRotation;
    }

    public boolean getHorizontalFlip() {
        if (mFlipType != FlipType.FLIP_NONE.nativeInt) {
            return (mFlipType & FlipType.FLIP_HORIZONTAL.nativeInt) == FlipType.FLIP_HORIZONTAL.nativeInt;
        }
        return false;
    }

    public boolean getVerticalFlip() {
        if (mFlipType != FlipType.FLIP_NONE.nativeInt) {
            return (mFlipType & FlipType.FLIP_VERTICAL.nativeInt) == FlipType.FLIP_VERTICAL.nativeInt;
        }
        return false;
    }

    public int getFlipType() {
        return mFlipType;
    }

    public boolean isRunning() {
        return mRunning;
    }

    public void reset() {
        isReset = true;
        onReset();
    }

    private void onReset() {
        if (isReset) {
            double rotation = (double) getRotation();
            double straightenRotation = getStraightenAngle();
            boolean resetStraighten = getStraightenStarted();
            straightenStarted = false;

            rotation = rotation % 360;
            if (rotation > 180) {
                rotation = rotation - 360;
            }

            final boolean hflip = getHorizontalFlip();
            final boolean vflip = getVerticalFlip();
            boolean handled = false;
            initStraighten = false;
            invalidate();

            if (rotation != 0 || resetStraighten) {
                if (resetStraighten) {
                    straightenBy(-straightenRotation, (int) getCenter().x, mResetAnimationDuration);
                } else {
                    rotateBy(-rotation, mResetAnimationDuration);
                }
                handled = true;
            }

            if (hflip) {
                flip(true, mResetAnimationDuration);
                handled = true;
            }

            if (vflip) {
                flip(false, mResetAnimationDuration);
                handled = true;
            }

            if (!handled) {
                fireOnResetComplete();
            }
        }
    }

    private void fireOnResetComplete() {
        if (mResetListener != null) {
            mResetListener.onResetComplete();
        }
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        // During straighten, we must bring it to the start if orientation is changed
        orientation = getResources().getConfiguration().orientation;
        initStraighten = true;
        mCenter = null;
        invalidate();
        if (straightenStarted) {
            initStraighten = true;
            resetStraighten();
            invalidate();
        }
    }

    @Override
    @SuppressWarnings("checkstyle:cyclomaticcomplexity")
    public boolean onTouchEvent(MotionEvent ev) {

        if (!mEnableFreeRotate) {
            return true;
        }

        final int action = ev.getAction();

        if (initStraighten) {
            resetStraighten();
        }

        float x, y;

        switch (action & MotionEvent.ACTION_MASK) {

            case MotionEvent.ACTION_POINTER_UP:
                int index = ev.findPointerIndex(mActivePointerId);
                if (index < 0) {
                    // released the original pointer
                    Log.d(LOG_TAG, "released original pointer");
                    onTouchUp();
                    mActivePointerId = -1;
                    mActivePointerIndex = -1;
                }

                Log.d(LOG_TAG, "pointerId: " + mActivePointerId + ", activePointerId: " + mActivePointerId);

                break;

            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_DOWN:

                // Log.i( LOG_TAG, "ACTION_DOWN" );

                if (mActivePointerId != -1) {
                    Log.w(LOG_TAG, "We already have a valid pointer");
                    return true;
                }

                for (int i = 0; i < ev.getPointerCount(); i++) {

                    x = ev.getX(i);
                    y = ev.getY(i);

                    if (null != imageCaptureRegion) {
                        RectF copy = new RectF(imageCaptureRegion.left - HIT_TOLERANCE,
                                               imageCaptureRegion.bottom - HIT_TOLERANCE,
                                               imageCaptureRegion.right + HIT_TOLERANCE,
                                               imageCaptureRegion.bottom + HIT_TOLERANCE);
                        copy.offset(getPaddingLeft(), getPaddingTop());
                        mIsInStraighten = copy.contains(x, y);
                    }

                    if (mIsInStraighten) {
                        mLastTouchX = x;
                        mPosX = mStraightenDrawable.getBounds().centerX();

                        mActivePointerIndex = i;
                        mActivePointerId = ev.getPointerId(mActivePointerIndex);

                        // Log.d( LOG_TAG, "active pointer index: " + mActivePointerIndex
                        // );
                        // Log.d( LOG_TAG, "active pointer id: " + mActivePointerId );

                        onTouchStart();
                        return true;
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                // Log.i( LOG_TAG, "ACTION_MOVE" );

                int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex < 0) {
                    Log.w(LOG_TAG, "could not find the original pointerId");
                    return false;
                }

                x = ev.getX(pointerIndex);
                y = ev.getY(pointerIndex);

                // Calculate the distance moved
                final float dx = x - mLastTouchX;

                // Move the object
                mPosX += dx;

                // Remember this touch position for the next move event
                mLastTouchX = x;

                if (mIsInStraighten) {
                    // if the move is within the straighten tool touch bounds
                    if (mPosX > imageCaptureRegion.right) {
                        mPosX = imageCaptureRegion.right;
                    }
                    if (mPosX < imageCaptureRegion.left) {
                        mPosX = imageCaptureRegion.left;
                    }

                    // now get the angle from the distance
                    double midPoint = getCenter().x;
                    double maxAngle = (45 * imageCaptureRegion.right) / midPoint - 45;
                    double tempAngle = (45 * mPosX) / midPoint - 45;
                    double angle = (45 * tempAngle) / maxAngle;

                    straighten(-angle / 2, mPosX);
                }

                // Invalidate to request a redraw
                invalidate();
                break;

            case MotionEvent.ACTION_UP:
                // Log.i( LOG_TAG, "ACTION_UP" );

                if (mActivePointerId != -1) {
                    onTouchUp();
                }

                mActivePointerId = -1;
                mActivePointerIndex = -1;

                mIsInStraighten = false;
                mLastTouchX = 0;
                break;

            case MotionEvent.ACTION_CANCEL:
                // Log.i( LOG_TAG, "ACTION_CANCEL" );

                mActivePointerId = -1;
                mActivePointerIndex = -1;

                mIsInStraighten = false;
                mLastTouchX = 0;
                break;

            default:
                /* invalid case */
                break;
        }

        return true;
    }

    private void resetStraighten() {
        mStraightenMatrix.reset();
        straightenStarted = false;
        previousStraightenAngle = 0;
        prevGrowth = 1;
        testStraighten = true;
        currentGrowth = 0;
        previousAngle = 0;
    }

    private void onTouchUp() {
        invalidate();
        fadeoutGrid(200);
    }

    private void onTouchStart() {
        if (mFadeHandlerStarted) {
            fadeinGrid(100);
        } else {
            fadeinOutlines(200);
        }
    }

    private PointF getCenter() {
        if (null == mCenter) {
            final int vwidth = getWidth() - getPaddingLeft() - getPaddingRight();
            final int vheight = getHeight() - getPaddingTop() - getPaddingBottom();
            mCenter = new PointF((float) vwidth / 2, (float) vheight / 2);
        }
        return mCenter;
    }

    public void straighten(final double newPosition, final float posx) {

        if (mRunning) {
            return;
        }

        straightenStarted = true;

        mStraightenDrawable.setBounds((int) (posx - handleWidth),
                                      (int) (imageCaptureRegion.bottom - handleHeight),
                                      (int) (posx + handleWidth),
                                      (int) (imageCaptureRegion.bottom + handleHeight));
        setStraightenRotation(newPosition);
        mPosX = posx;
        invalidate();
    }

    protected void fadeoutGrid(final int durationMs) {
        if (null != mFadeAnimator) {
            mFadeAnimator.cancel();
        }
        mFadeAnimator = ObjectAnimator.ofInt(this, "gridAlpha", mLinesPaint.getAlpha(), 0);
        mFadeAnimator.setDuration(durationMs);
        mFadeAnimator.start();
    }

    protected void fadeinGrid(final int durationMs) {
        if (null != mFadeAnimator) {
            mFadeAnimator.cancel();
        }
        mFadeAnimator = ObjectAnimator.ofInt(this, "gridAlpha", mLinesPaint.getAlpha(), mLinesAlpha);
        mFadeAnimator.setDuration(durationMs);
        mFadeAnimator.start();
    }

    protected void fadeinOutlines(final int durationMs) {
        if (mFadeHandlerStarted) {
            return;
        }
        mFadeHandlerStarted = true;

        if (null != mFadeOutlinesAnimator) {
            mFadeOutlinesAnimator.cancel();
        }

        mFadeOutlinesAnimator = new AnimatorSet();
        mFadeOutlinesAnimator.playTogether(ObjectAnimator.ofInt(this, "outlineFillAlpha", 0, mOutlineFillAlpha),
                                           ObjectAnimator.ofInt(this, "outlinePaintAlpha", 0, mOutlinePaintAlpha),
                                           ObjectAnimator.ofInt(this, "linesAlpha", 0, mLinesAlpha));

        mFadeOutlinesAnimator.setDuration(durationMs);
        mFadeOutlinesAnimator.start();
    }

    /**
     * Calculates the new angle and size of of the image through matrix and geometric
     * operations
     *
     * @param newPosition - the new destination angle
     */
    private void setStraightenRotation(final double newPosition) {

        // angle here is the difference between previous angle and new angle
        // you need to take advantage of the third parameter, newPosition
        double growthFactor;

        // newPosition = newPosition / 2;

        PointF center = getCenter();

        mStraightenMatrix.postRotate((float) -previousStraightenAngle, center.x, center.y);

        mStraightenMatrix.postRotate((float) newPosition, center.x, center.y);
        previousStraightenAngle = newPosition;

        double divideGrowth = 1 / prevGrowth;

        divideGrowth = isNumber(divideGrowth, 1);

        mStraightenMatrix.postScale((float) divideGrowth, (float) divideGrowth, center.x, center.y);

        final int paddingLeft = getPaddingLeft();
        final int paddingRight = getPaddingRight();
        final int paddingTop = getPaddingTop();
        final int paddingBottom = getPaddingBottom();

        if (portrait) {
            // this algorithm works slightly differently between landscape and portrait
            // images because of the proportions

            final double sinRad = Math.sin(Math.toRadians(newPosition));
            final double cosRad = Math.cos(Math.toRadians(newPosition));

            float[] testPoint = {
                (float) (imageCaptureRegion.left + sinRad * paddingLeft + cosRad * paddingLeft),
                (float) (imageCaptureRegion.top - sinRad * paddingTop + cosRad * paddingLeft),
                (float) (imageCaptureRegion.right + sinRad * paddingRight + cosRad * paddingRight),
                (float) (imageCaptureRegion.top - sinRad * paddingTop + cosRad * paddingLeft),
                (float) (imageCaptureRegion.left + sinRad * paddingLeft + cosRad * paddingLeft),
                (float) (imageCaptureRegion.bottom - sinRad * paddingBottom + cosRad * paddingBottom),
                (float) (imageCaptureRegion.right + sinRad * paddingRight + cosRad * paddingRight),
                (float) (imageCaptureRegion.bottom - sinRad * paddingBottom + cosRad * paddingBottom)
            };

            mStraightenMatrix.mapPoints(testPoint);

            float x1 = (float) (imageCaptureRegion.right + sinRad * paddingRight + cosRad * paddingRight);
            float y1 = (float) (imageCaptureRegion.top - sinRad * paddingTop + cosRad * paddingTop);
            float x2 = (float) (imageCaptureRegion.right + sinRad * paddingRight + cosRad * paddingRight);
            float y2 = (float) (imageCaptureRegion.bottom - sinRad * paddingBottom + cosRad * paddingBottom);
            float x3 = testPoint[2];
            float y3 = testPoint[3];
            float x4 = testPoint[6];
            float y4 = testPoint[7];

            double numerator2 = (x1 * y2 - y1 * x2) * (y3 - y4) - (y1 - y2) * (x3 * y4 - y3 * x4);
            double denominator2 = ((x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4));

            double aPx = imageCaptureRegion.right + paddingRight;
            double aPy = (numerator2) / (denominator2) + paddingBottom;

            orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE && newPosition > 0) {
                aPy = (numerator2) / (denominator2) + sinRad * paddingBottom;
            }

            double dx = aPx - x2;
            double dy = aPy - y2;

            if (newPosition < 0) {
                dx = aPx - x1;
                dy = aPy - y1;
            }

            double distance = Math.sqrt(dx * dx + dy * dy);
            double amountNeededToGrow = (2 * distance * (Math.sin(Math.toRadians(Math.abs(newPosition)))));
            distance = Math.sqrt((testPoint[0] - testPoint[2]) * (testPoint[0] - testPoint[2]));

            if (newPosition != 0) {
                growthFactor = (distance + amountNeededToGrow) / distance;
                growthFactor = isNumber(growthFactor, 1);
                mStraightenMatrix.postScale((float) growthFactor, (float) growthFactor, center.x, center.y);

            } else {
                growthFactor = 1;
            }
        } else {

            final double sinRad = Math.sin(Math.toRadians(newPosition));
            final double cosRad = Math.cos(Math.toRadians(newPosition));

            float[] testPoint = {
                (float) (imageCaptureRegion.left + sinRad * paddingLeft + cosRad * paddingLeft),
                (float) (imageCaptureRegion.top - sinRad * paddingTop + cosRad * paddingLeft),
                (float) (imageCaptureRegion.right + sinRad * paddingRight + cosRad * paddingRight),
                (float) (imageCaptureRegion.top - sinRad * paddingTop + cosRad * paddingLeft),
                (float) (imageCaptureRegion.left + sinRad * paddingLeft + cosRad * paddingLeft),
                (float) (imageCaptureRegion.bottom - sinRad * paddingBottom + cosRad * paddingBottom),
                (float) (imageCaptureRegion.right + sinRad * paddingRight + cosRad * paddingRight),
                (float) (imageCaptureRegion.bottom - sinRad * paddingBottom + cosRad * paddingBottom)
            };

            mStraightenMatrix.mapPoints(testPoint);

            float x1 = (float) (imageCaptureRegion.left + sinRad * paddingLeft + cosRad * paddingLeft);
            float y1 = (float) (imageCaptureRegion.bottom - sinRad * paddingBottom + cosRad * paddingBottom);
            float x2 = (float) (imageCaptureRegion.right + sinRad * paddingRight + cosRad * paddingRight);
            float y2 = (float) (imageCaptureRegion.bottom - sinRad * paddingBottom + cosRad * paddingBottom);
            float x3 = testPoint[4];
            float y3 = testPoint[5];
            float x4 = testPoint[6];
            float y4 = testPoint[7];

            double numerator1 = (x1 * y2 - y1 * x2) * (x3 - x4) - (x1 - x2) * (x3 * y4 - y3 * x4);
            double denominator1 = ((x1 - x2) * (y3 - y4) - (y1 - y2) * (x3 - x4));

            double aPx = (numerator1) / (denominator1) + paddingLeft;
            double aPy = imageCaptureRegion.bottom + paddingBottom;
            double dx = aPx - x1;
            double dy = aPy - y1;

            if (newPosition < 0) {
                dx = aPx - x2;
                dy = aPy - y2;
            }

            double distance = Math.sqrt(dx * dx + dy * dy);
            double amountNeededToGrow = (2 * distance * (Math.sin(Math.toRadians(Math.abs(newPosition)))));
            distance = Math.sqrt((testPoint[5] - testPoint[1]) * (testPoint[5] - testPoint[1]));

            if (newPosition != 0) {
                growthFactor = (distance + amountNeededToGrow) / distance;
                growthFactor = isNumber(growthFactor, 1);

                mStraightenMatrix.postScale((float) growthFactor, (float) growthFactor, center.x, center.y);
            } else {
                growthFactor = 1;
            }
        }
        // now the resize-grow stuff
        prevGrowth = growthFactor;
    }

    protected double isNumber(double number, double defaultValue) {
        if (Double.isInfinite(number) || Double.isNaN(number)) {
            Log.e(LOG_TAG, "number is NaN or Infinite");
            return defaultValue;
        }
        return number;
    }

    public float getRotation() {
        return (float) mRotation;
    }

    @Override
    @SuppressWarnings("checkstyle:cyclomaticcomplexity")
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (mDrawable == null) {
            Log.e(LOG_TAG, "Drawable is null");
            return;
        }

        if (mDrawableWidth == 0 || mDrawableHeight == 0) {
            Log.e(LOG_TAG, "drawable width or height is 0");
            return;
        }

        final int mPaddingTop = getPaddingTop();
        final int mPaddingLeft = getPaddingLeft();
        // final int mPaddingBottom = getPaddingBottom();
        // final int mPaddingRight = getPaddingRight();

        if (mDrawMatrix == null) {
            Log.e(LOG_TAG, "mDrawMatrix is null");
            mDrawable.draw(canvas);
        } else {

            // save 0
            int saveCount = canvas.save();

            canvas.translate(mPaddingLeft, mPaddingTop);

            if (mFlipMatrix != null) {
                canvas.concat(mFlipMatrix);
            }
            if (mRotateMatrix != null) {
                canvas.concat(mRotateMatrix);
            }
            if (mStraightenMatrix != null) {
                canvas.concat(mStraightenMatrix);
            }
            if (mDrawMatrix != null) {
                canvas.concat(mDrawMatrix);
            }

            mDrawable.draw(canvas);

            // restore 0
            canvas.restoreToCount(saveCount);

            if (mEnableFreeRotate) {

                mDrawRect = getImageRect();

                getDrawingRect(mViewDrawRect);

                mClipPath.reset();
                mInversePath.reset();
                mLinesPath.reset();

                float[] points = new float[]{
                    mDrawRect.left, mDrawRect.top, mDrawRect.right, mDrawRect.top, mDrawRect.right, mDrawRect.bottom,
                    mDrawRect.left, mDrawRect.bottom
                };

                mTempMatrix.set(mDrawMatrix);
                mTempMatrix.postConcat(mRotateMatrix);
                mTempMatrix.postConcat(mStraightenMatrix);
                mTempMatrix.mapPoints(points);

                mViewInvertRect.set(mViewDrawRect);
                mViewInvertRect.top -= mPaddingLeft;
                mViewInvertRect.left -= mPaddingTop;

                mInversePath.addRect(mViewInvertRect, Path.Direction.CW);

                double sx = Point2D.distance(points[2], points[3], points[0], points[1]);
                double sy = Point2D.distance(points[6], points[7], points[0], points[1]);
                double angle = getAngle90(mRotation);
                RectF rect;

                if (initStraighten) {

                    if (angle < 45) {
                        rect = crop((float) sx, (float) sy, angle, mDrawableWidth, mDrawableHeight, getCenter(), null);
                    } else {
                        rect = crop((float) sx, (float) sy, angle, mDrawableHeight, mDrawableWidth, getCenter(), null);
                    }

                    float colStep = rect.height() / gridCols;
                    float rowStep = rect.width() / gridRows;

                    for (int i = 1; i < gridCols; i++) {
                        mLinesPath.moveTo((int) rect.left, (int) (rect.top + colStep * i));
                        mLinesPath.lineTo((int) rect.right, (int) (rect.top + colStep * i));
                    }

                    for (int i = 1; i < gridRows; i++) {
                        mLinesPath.moveTo((int) (rect.left + rowStep * i), (int) rect.top);
                        mLinesPath.lineTo((int) (rect.left + rowStep * i), (int) rect.bottom);
                    }
                    imageCaptureRegion = rect;

                    PointF center = getCenter();
                    mStraightenDrawable.setBounds((int) (center.x - handleWidth),
                                                  (int) (imageCaptureRegion.bottom - handleHeight),
                                                  (int) (center.x + handleWidth),
                                                  (int) (imageCaptureRegion.bottom + handleHeight));
                    mPosX = center.x;
                    initStraighten = false;
                } else {
                    rect = imageCaptureRegion;
                    float colStep = rect.height() / gridCols;
                    float rowStep = rect.width() / gridRows;

                    for (int i = 1; i < gridCols; i++) {
                        mLinesPath.moveTo((int) rect.left, (int) (rect.top + colStep * i));
                        mLinesPath.lineTo((int) rect.right, (int) (rect.top + colStep * i));
                    }

                    for (int i = 1; i < gridRows; i++) {
                        mLinesPath.moveTo((int) (rect.left + rowStep * i), (int) rect.top);
                        mLinesPath.lineTo((int) (rect.left + rowStep * i), (int) rect.bottom);
                    }

                }

                mClipPath.addRect(rect, Path.Direction.CW);

                mInversePath.addRect(rect, Path.Direction.CCW);

                // save 1
                saveCount = canvas.save();

                canvas.translate(mPaddingLeft, mPaddingTop);

                canvas.drawPath(mInversePath, mOutlineFill);
                canvas.drawPath(mLinesPath, mLinesPaint);
                canvas.drawPath(mClipPath, mOutlinePaint);

                if (!mRunning) {
                    mStraightenDrawable.draw(canvas);
                }

                // restore 1
                canvas.restoreToCount(saveCount);

            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (changed) {
            mHaveFrame = true;

            double oldRotation = mRotation;
            boolean flipH = getHorizontalFlip();
            boolean flipV = getVerticalFlip();

            configureBounds();

            if (flipH || flipV) {
                flip(flipH, flipV);
            }

            if (oldRotation != 0) {
                setImageRotation(oldRotation, false);
                mRotation = oldRotation;
            }
            invalidate();
        }
    }

    @Override
    public void invalidateDrawable(Drawable dr) {
        if (dr == mDrawable) {
            invalidate();
        } else {
            super.invalidateDrawable(dr);
        }
    }

    @Override
    protected boolean verifyDrawable(Drawable dr) {
        return mDrawable == dr || super.verifyDrawable(dr);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        Drawable d = mDrawable;
        if (d != null && d.isStateful()) {
            d.setState(getDrawableState());
        }
    }

    @Override
    public int[] onCreateDrawableState(int extraSpace) {
        if (mState == null) {
            return super.onCreateDrawableState(extraSpace);
        } else if (!mMergeState) {
            return mState;
        } else {
            return mergeDrawableStates(super.onCreateDrawableState(extraSpace + mState.length), mState);
        }
    }

    @Override
    public void setSelected(boolean selected) {
        super.setSelected(selected);
        resizeFromDrawable();
    }

    @Override
    public int getBaseline() {
        if (mBaselineAlignBottom) {
            return getMeasuredHeight();
        } else {
            return mBaseline;
        }
    }

    @Override
    @SuppressWarnings("checkstyle:cyclomaticcomplexity")
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        resolveUri();
        int w;
        int h;

        // Desired aspect ratio of the view's contents (not including padding)
        float desiredAspect = 0.0f;

        // We are allowed to change the view's width
        boolean resizeWidth = false;

        // We are allowed to change the view's height
        boolean resizeHeight = false;

        final int widthSpecMode = MeasureSpec.getMode(widthMeasureSpec);
        final int heightSpecMode = MeasureSpec.getMode(heightMeasureSpec);

        if (mDrawable == null) {
            // If no drawable, its intrinsic size is 0.
            mDrawableWidth = -1;
            mDrawableHeight = -1;
            w = 0;
            h = 0;
        } else {
            w = mDrawableWidth;
            h = mDrawableHeight;

            if (w <= 0) {
                w = 1;
            }
            if (h <= 0) {
                h = 1;
            }

            if (mDrawableHeight > mDrawableWidth) {
                portrait = true;
            }

            orientation = getResources().getConfiguration().orientation;

            // We are supposed to adjust view bounds to match the aspect
            // ratio of our drawable. See if that is possible.
            if (mAdjustViewBounds) {
                resizeWidth = widthSpecMode != MeasureSpec.EXACTLY;
                resizeHeight = heightSpecMode != MeasureSpec.EXACTLY;

                desiredAspect = (float) w / (float) h;
            }
        }

        int pleft = getPaddingLeft();
        int pright = getPaddingRight();
        int ptop = getPaddingTop();
        int pbottom = getPaddingBottom();

        int widthSize;
        int heightSize;

        if (resizeWidth || resizeHeight) {
            /*
            * If we get here, it means we want to resize to match the drawables aspect ratio,
            * and we have the freedom to change at least one dimension.
            */

            // Get the max possible width given our constraints
            widthSize = resolveAdjustedSize(w + pleft + pright, mMaxWidth, widthMeasureSpec);

            // Get the max possible height given our constraints
            heightSize = resolveAdjustedSize(h + ptop + pbottom, mMaxHeight, heightMeasureSpec);

            if (desiredAspect != 0.0f) {
                // See what our actual aspect ratio is
                float actualAspect = (float) (widthSize - pleft - pright) / (heightSize - ptop - pbottom);

                //CHECKSTYLE.OFF: MagicNumber
                if (Math.abs(actualAspect - desiredAspect) > 0.0000001) {
                //CHECKSTYLE.ON: MagicNumber

                    boolean done = false;

                    // Try adjusting width to be proportional to height
                    if (resizeWidth) {
                        int newWidth = (int) (desiredAspect * (heightSize - ptop - pbottom)) + pleft + pright;
                        if (newWidth <= widthSize) {
                            widthSize = newWidth;
                            done = true;
                        }
                    }

                    // Try adjusting height to be proportional to width
                    if (!done && resizeHeight) {
                        int newHeight = (int) ((widthSize - pleft - pright) / desiredAspect) + ptop + pbottom;
                        if (newHeight <= heightSize) {
                            heightSize = newHeight;
                        }
                    }
                }
            }
        } else {
            /*
            * We are either don't want to preserve the drawables aspect ratio, or we are not allowed to change view dimensions.
            * Just measure in the normal way.
            */
            w += pleft + pright;
            h += ptop + pbottom;

            w = Math.max(w, getSuggestedMinimumWidth());
            h = Math.max(h, getSuggestedMinimumHeight());

            widthSize = resolveSize(w, widthMeasureSpec);
            heightSize = resolveSize(h, heightMeasureSpec);
        }

        setMeasuredDimension(widthSize, heightSize);

        // drawResource();
    }

    @Override
    protected boolean onSetAlpha(int alpha) {
        if (getBackground() == null) {
            int scale = alpha + (alpha >> 7);
            if (mViewAlphaScale != scale) {
                mViewAlphaScale = scale;
                mColorMod = true;
                applyColorMod();
            }
            return true;
        }
        return false;
    }

    private void applyColorMod() {
        // Only mutate and apply when modifications have occurred. This should
        // not reset the mColorMod flag, since these filters need to be
        // re-applied if the Drawable is changed.
        if (mDrawable != null && mColorMod) {
            mDrawable = mDrawable.mutate();
            mDrawable.setColorFilter(mColorFilter);
            mDrawable.setAlpha(mAlpha * mViewAlphaScale >> 8);
        }
    }

    private void resolveUri() {
        if (mDrawable != null) {
            return;
        }

        Resources rsrc = getResources();
        if (rsrc == null) {
            return;
        }

        Drawable d = null;

        if (mResource != 0) {
            try {
                d = rsrc.getDrawable(mResource);
            } catch (Exception e) {
                Log.w(LOG_TAG, "Unable to find resource: " + mResource, e);
                // Don't try again.
                mUri = null;
            }
        } else if (mUri != null) {
            String scheme = mUri.getScheme();
            if (ContentResolver.SCHEME_ANDROID_RESOURCE.equals(scheme)) {
                // empty block
            } else if (ContentResolver.SCHEME_CONTENT.equals(scheme) || ContentResolver.SCHEME_FILE.equals(scheme)) {
                try {
                    d = Drawable.createFromStream(getContext().getContentResolver().openInputStream(mUri), null);
                } catch (Exception e) {
                    Log.w(LOG_TAG, "Unable to open content: " + mUri, e);
                }
            } else {
                d = Drawable.createFromPath(mUri.toString());
            }

            if (d == null) {
                System.out.println("resolveUri failed on bad bitmap uri: " + mUri);
                // Don't try again.
                mUri = null;
            }
        } else {
            return;
        }

        updateDrawable(d);
    }

    private int resolveAdjustedSize(int desiredSize, int maxSize, int measureSpec) {
        int result = desiredSize;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);
        switch (specMode) {
            case MeasureSpec.UNSPECIFIED:
                // Parent says we can be as big as we want. Just don't be larger than max size imposed on ourselves.
                result = Math.min(desiredSize, maxSize);
                break;
            case MeasureSpec.AT_MOST:
                // Parent says we can be as big as we want, up to specSize.
                // Don't be larger than specSize, and don't be larger than
                // the max size imposed on ourselves.
                result = Math.min(Math.min(desiredSize, specSize), maxSize);
                break;
            case MeasureSpec.EXACTLY:
                // No choice. Do what we are told.
                result = specSize;
                break;
            default:
                break;
        }
        return result;
    }

    public void setBaseline(int baseline) {
        if (mBaseline != baseline) {
            mBaseline = baseline;
            requestLayout();
        }
    }

    static double getAngle90(double value) {

        double rotation = Point2D.angle360(value);
        double angle = rotation;

        if (rotation >= 270) {
            angle = 360 - rotation;
        } else if (rotation >= 180) {
            angle = rotation - 180;
        } else if (rotation > 90) {
            angle = 180 - rotation;
        }
        return angle;
    }

    RectF crop(
        float originalWidth, float originalHeight, double angle, float targetWidth, float targetHeight, PointF center,
        Canvas canvas) {
        double radians = Point2D.radians(angle);

        PointF[] original = new PointF[]{
            new PointF(0, 0), new PointF(originalWidth, 0), new PointF(originalWidth, originalHeight), new PointF(0, originalHeight)
        };

        Point2D.translate(original, -originalWidth / 2, -originalHeight / 2);

        PointF[] rotated = new PointF[original.length];
        System.arraycopy(original, 0, rotated, 0, original.length);
        Point2D.rotate(rotated, radians);

        if (angle >= 0) {
            PointF[] ray = new PointF[]{new PointF(0, 0), new PointF(-targetWidth / 2, -targetHeight / 2)};
            PointF[] bound = new PointF[]{rotated[0], rotated[3]};

            // Top Left intersection.
            PointF intersectTL = Point2D.intersection(ray, bound);

            PointF[] ray2 = new PointF[]{new PointF(0, 0), new PointF(targetWidth / 2, -targetHeight / 2)};
            PointF[] bound2 = new PointF[]{rotated[0], rotated[1]};

            // Top Right intersection.
            PointF intersectTR = Point2D.intersection(ray2, bound2);

            // Pick the intersection closest to the origin
            PointF intersect = new PointF(Math.max(intersectTL.x, -intersectTR.x), Math.max(intersectTL.y, intersectTR.y));

            RectF newRect = new RectF(intersect.x, intersect.y, -intersect.x, -intersect.y);
            newRect.offset(center.x, center.y);

            if (canvas != null) { // debug

                Point2D.translate(rotated, center.x, center.y);
                Point2D.translate(ray, center.x, center.y);
                Point2D.translate(ray2, center.x, center.y);

                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setColor(0x66FFFF00);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2);
                // draw rotated
                drawRect(rotated, canvas, paint);

                paint.setColor(Color.GREEN);
                drawLine(ray, canvas, paint);

                paint.setColor(Color.BLUE);
                drawLine(ray2, canvas, paint);

                paint.setColor(Color.CYAN);
                drawLine(bound, canvas, paint);

                paint.setColor(Color.WHITE);
                drawLine(bound2, canvas, paint);

                paint.setColor(Color.GRAY);
                canvas.drawRect(newRect, paint);
            }
            return newRect;

        } else {
            throw new IllegalArgumentException("angle cannot be < 0");
        }
    }

    void drawRect(PointF[] rect, Canvas canvas, Paint paint) {
        // draw rotated
        Path path = new Path();
        path.moveTo(rect[0].x, rect[0].y);
        path.lineTo(rect[1].x, rect[1].y);
        path.lineTo(rect[2].x, rect[2].y);
        path.lineTo(rect[3].x, rect[3].y);
        path.lineTo(rect[0].x, rect[0].y);
        canvas.drawPath(path, paint);
    }

    void drawLine(PointF[] line, Canvas canvas, Paint paint) {
        canvas.drawLine(line[0].x, line[0].y, line[1].x, line[1].y, paint);
    }

    public enum ScaleType {
        /**
         * Scale using the image matrix when drawing. The image matrix can be set using
         * {@link ImageView#setImageMatrix(Matrix)}. From
         * XML, use this syntax: <code>android:scaleType="matrix"</code>.
         */
        MATRIX(0),
        /**
         * Scale the image using {@link Matrix.ScaleToFit#FILL}. From XML, use this
         * syntax: <code>android:scaleType="fitXY"</code>.
         */
        FIT_XY(1),
        /**
         * Scale the image using {@link Matrix.ScaleToFit#START}. From XML, use this
         * syntax: <code>android:scaleType="fitStart"</code> .
         */
        FIT_START(2),
        /**
         * Scale the image using {@link Matrix.ScaleToFit#CENTER}. From XML, use this
         * syntax: <code>android:scaleType="fitCenter"</code>.
         */
        FIT_CENTER(3),
        /**
         * Scale the image using {@link Matrix.ScaleToFit#END}. From XML, use this syntax:
         * <code>android:scaleType="fitEnd"</code>.
         */
        FIT_END(4),
        /**
         * Center the image in the view, but perform no scaling. From XML, use this
         * syntax: <code>android:scaleType="center"</code>.
         */
        CENTER(5),
        /**
         * Scale the image uniformly (maintain the image's aspect ratio) so that both
         * dimensions (width and height) of the image will
         * be equal to or larger than the corresponding dimension of the view (minus
         * padding). The image is then centered in the view.
         * From XML, use this syntax: <code>android:scaleType="centerCrop"</code>.
         */
        CENTER_CROP(6),
        /**
         * Scale the image uniformly (maintain the image's aspect ratio) so that both
         * dimensions (width and height) of the image will
         * be equal to or less than the corresponding dimension of the view (minus
         * padding). The image is then centered in the view.
         * From XML, use this syntax: <code>android:scaleType="centerInside"</code>.
         */
        CENTER_INSIDE(7);
        /** The native int. */
        final int nativeInt;

        /**
         * Instantiates a new scale type.
         *
         * @param ni the ni
         */
        ScaleType(int ni) {
            nativeInt = ni;
        }
    }

    public enum FlipType {

        FLIP_NONE(1 << 0), FLIP_HORIZONTAL(1 << 1), FLIP_VERTICAL(1 << 2);
        public final int nativeInt;

        FlipType(int ni) {
            nativeInt = ni;
        }
    }

    public interface OnResetListener {
        void onResetComplete();
    }
}