package com.aviary.android.feather.sdk.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.MotionEvent;
import android.view.animation.AccelerateDecelerateInterpolator;

import com.aviary.android.feather.library.graphics.Point2D;
import com.aviary.android.feather.library.graphics.drawable.EditableDrawable;
import com.aviary.android.feather.library.graphics.drawable.EditableDrawable.OnSizeChange;
import com.aviary.android.feather.library.graphics.drawable.FeatherDrawable;
import com.aviary.android.feather.sdk.R;
import com.aviary.android.feather.sdk.utils.UIUtils;
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.AnimatorSet;
import com.nineoldandroids.animation.ObjectAnimator;

import java.lang.ref.WeakReference;

import it.sephiroth.android.library.imagezoom.ImageViewTouch;

public class DrawableHighlightView implements OnSizeChange, Drawable.Callback {
    public static final  int   NONE             = 1 << 0; // 1
    public static final  int   GROW_LEFT_EDGE   = 1 << 1; // 2
    public static final  int   GROW_RIGHT_EDGE  = 1 << 2; // 4
    public static final  int   GROW_TOP_EDGE    = 1 << 3; // 8
    public static final  int   GROW_BOTTOM_EDGE = 1 << 4; // 16
    public static final  int   GROW             = GROW_TOP_EDGE | GROW_BOTTOM_EDGE | GROW_LEFT_EDGE | GROW_RIGHT_EDGE;
    public static final  int   ROTATE           = 1 << 5; // 32
    public static final  int   MOVE             = 1 << 6; // 64
    public static final  int   DELETE           = 1 << 7; // 128
    public static final  int   OPACITY          = 1 << 8; // 256
    static final String LOG_TAG = "drawable-view";
    static final int[]   STATE_SET_NONE             = new int[]{};
    static final int[]   STATE_SET_SELECTED         = new int[]{android.R.attr.state_selected};
    static final int[]   STATE_SET_SELECTED_PRESSED = new int[]{android.R.attr.state_selected, android.R.attr.state_pressed};
    static final int[]   STATE_SET_SELECTED_FOCUSED = new int[]{android.R.attr.state_focused};
    private static final int STATE_NONE     = 1 << 0;
    public static final float DEFAULT_MIN_SIZE = 20f;
    private              int     mState         = STATE_NONE;
    private static final int     STATE_SELECTED = 1 << 1;
    private static final int     STATE_FOCUSED  = 1 << 2;
    private static final float   HIT_TOLERANCE  = 40f;
    private final        RectF   mTempRect      = new RectF();
    private final        float[] fpoints        = new float[]{0, 0};
    RectF mInvalidateRectF = new RectF();
    Rect  mInvalidateRect  = new Rect();
    private OnDeleteClickListener                mDeleteClickListener;
    private OnOpacityChangeListener              mOpacityChangeListener;
    private WeakReference<OnContentFlipListener> mContentFlipListener;
    private boolean                              mHidden;
    private int                                  mMode;
    private RectF                                mDrawRect;
    private RectF                                mCropRect;
    private Matrix                               mMatrix;
    private FeatherDrawable                      mContent;
    private EditableDrawable                     mEditableContent;
    private Drawable                             mAnchorRotate;
    private Drawable                             mAnchorDelete;
    private Drawable                             mAnchorOpacity;
    private Drawable                             mBackgroundDrawable;
    private int                                  mAnchorRotateWidth;
    private int                                  mAnchorRotateHeight;
    private int                                  mAnchorDeleteHeight;
    private int                                  mAnchorDeleteWidth;
    private int                                  mAnchorOpacityWidth;
    private int                                  mAnchorOpacityHeight;
    private int                                  mResizeEdgeMode;
    private boolean                              mRotateEnabled;
    private boolean                              mScaleEnabled;
    private boolean                              mMoveEnabled;
    private float      mRotation          = 0;
    private float      mRatio             = 1f;
    private Matrix     mRotateMatrix      = new Matrix();
    private int        mPadding           = 0;
    private boolean    mShowAnchors       = true;
    private AlignModeV mAlignVerticalMode = AlignModeV.Center;
    private ImageViewTouch mContext;
    private boolean mShouldFlip       = true;
    private int     mOpacity          = 255;
    private int     mConfirmedOpacity = 255;

    public DrawableHighlightView(ImageViewTouch context, int styleId, FeatherDrawable content) {
        mContent = content;
        mContext = context;

        mContent.setCallback(this);

        if (content instanceof EditableDrawable) {
            mEditableContent = (EditableDrawable) content;
            mEditableContent.setOnSizeChangeListener(this);
        } else {
            mEditableContent = null;
        }

        float minSize = -1f;

        if (styleId > 0) {
            TypedArray array = context.getContext().obtainStyledAttributes(styleId, R.styleable.AviaryDrawableHighlightView);

            mAnchorRotate = array.getDrawable(R.styleable.AviaryDrawableHighlightView_aviary_rotateDrawable);
            mAnchorDelete = array.getDrawable(R.styleable.AviaryDrawableHighlightView_aviary_deleteDrawable);
            mAnchorOpacity = array.getDrawable(R.styleable.AviaryDrawableHighlightView_aviary_opacityDrawable);
            mBackgroundDrawable = array.getDrawable(R.styleable.AviaryDrawableHighlightView_android_background);
            mPadding = array.getDimensionPixelSize(R.styleable.AviaryDrawableHighlightView_android_padding, 10);
            mResizeEdgeMode = array.getInteger(R.styleable.AviaryDrawableHighlightView_aviary_resizeEdgeMode, 0);

            mMoveEnabled = array.getBoolean(R.styleable.AviaryDrawableHighlightView_aviary_moveEnabled, true);
            mRotateEnabled = array.getBoolean(R.styleable.AviaryDrawableHighlightView_aviary_rotateEnabled, true);
            mScaleEnabled = array.getBoolean(R.styleable.AviaryDrawableHighlightView_aviary_resizeEnabled, true);

            minSize = array.getDimension(R.styleable.AviaryDrawableHighlightView_aviary_minSize, DEFAULT_MIN_SIZE);

            array.recycle();
        }
        if (null != mAnchorRotate) {
            mAnchorRotateWidth = mAnchorRotate.getIntrinsicWidth() / 3;
            mAnchorRotateHeight = mAnchorRotate.getIntrinsicHeight() / 3;
        }

        if (null != mAnchorDelete) {
            mAnchorDeleteWidth = mAnchorDelete.getIntrinsicWidth() / 3;
            mAnchorDeleteHeight = mAnchorDelete.getIntrinsicHeight() / 3;
        }

        if (null != mAnchorOpacity) {
            mAnchorOpacityWidth = mAnchorOpacity.getIntrinsicWidth() / 3;
            mAnchorOpacityHeight = mAnchorOpacity.getIntrinsicHeight() / 3;
        }

        updateRatio();

        if (minSize > 0) {
            setMinSize(minSize);
        }
    }

    private void updateRatio() {
        final float w = mContent.getCurrentWidth();
        final float h = mContent.getCurrentHeight();
        mRatio = w / h;
    }

    public void setMinSize(final float size) {
        if (mRatio >= 1) {
            mContent.setMinSize(size, size / mRatio);
        } else {
            mContent.setMinSize(size * mRatio, size);
        }
    }

    public void setAlignModeV(AlignModeV mode) {
        mAlignVerticalMode = mode;
    }

    public void dispose() {
        mDeleteClickListener = null;
        mOpacityChangeListener = null;
        mContentFlipListener = null;
        mContext = null;
        mContent = null;
        mEditableContent = null;
    }

    public int getOpacity() {
        return mOpacity;
    }

    public void setOpacity(int alpha) {
        getContent().setAlpha(alpha);
        mOpacity = alpha;
    }

    public FeatherDrawable getContent() {
        return mContent;
    }

    public int getConfirmedOpacity() {
        return mConfirmedOpacity;
    }

    public void setConfirmedOpacity(int alpha) {
        mConfirmedOpacity = alpha;
    }

    public void draw(final Canvas canvas) {
        if (mHidden) {
            return;
        }

        copyBounds(mTempRect);

        final int saveCount = canvas.save();
        canvas.concat(mRotateMatrix);

        if (null != mBackgroundDrawable) {
            mBackgroundDrawable.setBounds((int) mTempRect.left, (int) mTempRect.top, (int) mTempRect.right, (int) mTempRect.bottom);
            mBackgroundDrawable.draw(canvas);
        }

        boolean isSelected = isSelected();
        boolean isFocused = isFocused();

        if (mEditableContent != null) {
            mEditableContent.setBounds(mDrawRect.left, mDrawRect.top, mDrawRect.right, mDrawRect.bottom);
        } else {
            mContent.setBounds((int) mDrawRect.left, (int) mDrawRect.top, (int) mDrawRect.right, (int) mDrawRect.bottom);
        }

        mContent.draw(canvas);

        if (isSelected || isFocused) {

            if (mShowAnchors) {
                final int left = (int) (mTempRect.left);
                final int right = (int) (mTempRect.right);
                final int top = (int) (mTempRect.top);
                final int bottom = (int) (mTempRect.bottom);

                if (mAnchorRotate != null) {
                    mAnchorRotate.setBounds(right - mAnchorRotateWidth,
                                            bottom - mAnchorRotateHeight,
                                            right + mAnchorRotateWidth,
                                            bottom + mAnchorRotateHeight);
                    mAnchorRotate.draw(canvas);
                }

                if (mAnchorDelete != null) {
                    mAnchorDelete.setBounds(left - mAnchorDeleteWidth,
                                            top - mAnchorDeleteHeight,
                                            left + mAnchorDeleteWidth,
                                            top + mAnchorDeleteHeight);
                    mAnchorDelete.draw(canvas);
                }

                if (mAnchorOpacity != null) {
                    mAnchorOpacity.setBounds(right - mAnchorOpacityWidth,
                                             top - mAnchorOpacityHeight,
                                             right + mAnchorOpacityWidth,
                                             top + mAnchorOpacityHeight);
                    mAnchorOpacity.draw(canvas);
                }
            }
        }

        canvas.restoreToCount(saveCount);
    }

    public void copyBounds(RectF outRect) {
        outRect.set(mDrawRect);
        outRect.inset(-mPadding, -mPadding);
    }

    public boolean isSelected() {
        return (mState & STATE_SELECTED) == STATE_SELECTED;
    }

    public void setSelected(final boolean selected) {
        if (!selected) {
            mShouldFlip = false;
            if (mOpacityChangeListener != null) {
                mOpacityChangeListener.onLockOpacity();
            }
        }

        boolean isSelected = isSelected();
        if (isSelected != selected) {
            mState ^= STATE_SELECTED;
            updateDrawableState();
        }
    }

    protected void updateDrawableState() {
        boolean isSelected = isSelected();
        boolean isFocused = isFocused();

        final int[] state;
        int[] state2 = STATE_SET_NONE;
        int[] state3 = STATE_SET_NONE;
        int[] state4 = STATE_SET_NONE;

        if (isSelected) {
            if (mMode == NONE) {
                if (isFocused) {
                    state = STATE_SET_SELECTED_FOCUSED;
                } else {
                    state = STATE_SET_SELECTED;
                }
            } else {
                if ((mMode & DELETE) == DELETE) {
                    state3 = STATE_SET_SELECTED_PRESSED;
                }

                state = STATE_SET_SELECTED_PRESSED;

                if ((mMode & ROTATE) == ROTATE) {
                    state2 = STATE_SET_SELECTED_PRESSED;
                }

                if ((mMode & OPACITY) == OPACITY) {
                    state4 = STATE_SET_SELECTED_PRESSED;
                }
            }
        } else {
            // normal state
            state = STATE_SET_NONE;
        }

        if (null != mBackgroundDrawable) {
            mBackgroundDrawable.setState(state);
        }
        if (null != mAnchorRotate) {
            mAnchorRotate.setState(state2);
        }
        if (null != mAnchorDelete) {
            mAnchorDelete.setState(state3);
        }
        if (null != mAnchorOpacity) {
            mAnchorOpacity.setState(state4);
        }
    }

    public boolean isFocused() {
        return (mState & STATE_FOCUSED) == STATE_FOCUSED;
    }

    public void setFocused(final boolean value) {
        boolean isFocused = isFocused();
        if (isFocused != value) {
            mState ^= STATE_FOCUSED;

            if (null != mEditableContent) {
                if (value) {
                    mEditableContent.beginEdit();
                } else {
                    mEditableContent.endEdit();
                }
            }
            updateDrawableState();
        }
    }

    public void showAnchors(boolean value) {
        mShowAnchors = value;
    }

    public void playHorizontalFlipDemo() {
        if (null != mContent && mContent.getHorizontalFlipEnabled()) {
            final boolean flipped = mContent.getHorizontalFlip();

            AnimatorSet set = new AnimatorSet();

            ObjectAnimator anim1 = ObjectAnimator.ofFloat(mContent, "scaleX", flipped ? -1 : 1, flipped ? 1 : -1);
            anim1.setDuration(200);
            anim1.setInterpolator(new AccelerateDecelerateInterpolator());

            ObjectAnimator anim2 = ObjectAnimator.ofFloat(mContent, "scaleX", flipped ? 1 : -1, flipped ? -1 : 1);
            anim2.setDuration(200);
            anim2.setStartDelay(50);
            anim2.setInterpolator(new AccelerateDecelerateInterpolator());

            set.playSequentially(anim1, anim2);
            set.setStartDelay(150);
            set.start();

            set.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(final Animator animation) {}

                @Override
                public void onAnimationEnd(final Animator animation) {
                    if (null != mContent) {
                        mContent.setHorizontalFlip(false);
                    }
                }

                @Override
                public void onAnimationCancel(final Animator animation) {}

                @Override
                public void onAnimationRepeat(final Animator animation) {}
            });
        }
    }

    public boolean getFlipHorizontal() {
        if (null != mContent) {
            return mContent.getHorizontalFlip();
        }
        return false;
    }

    @Override
    public void invalidateDrawable(final Drawable drawable) {
        mContext.invalidate();
    }

    @Override
    public void scheduleDrawable(final Drawable drawable, final Runnable runnable, final long l) {

    }

    @Override
    public void unscheduleDrawable(final Drawable drawable, final Runnable runnable) {

    }

    public void draw(final Canvas canvas, final Matrix source) {

        final Matrix matrix = new Matrix(source);
        matrix.invert(matrix);

        final int saveCount = canvas.save();
        canvas.concat(matrix);
        canvas.concat(mRotateMatrix);

        mContent.setBounds((int) mDrawRect.left, (int) mDrawRect.top, (int) mDrawRect.right, (int) mDrawRect.bottom);
        mContent.draw(canvas);

        canvas.restoreToCount(saveCount);
    }

    public Rect getCropRect() {
        return new Rect((int) mCropRect.left, (int) mCropRect.top, (int) mCropRect.right, (int) mCropRect.bottom);
    }

    public RectF getCropRectF() {
        return mCropRect;
    }

    public Matrix getCropRotationMatrix() {
        final Matrix m = new Matrix();
        m.postTranslate(-mCropRect.centerX(), -mCropRect.centerY());
        m.postRotate(mRotation);
        m.postTranslate(mCropRect.centerX(), mCropRect.centerY());
        return m;
    }

    public RectF getDisplayRectF() {
        final RectF r = new RectF(mDrawRect);
        mRotateMatrix.mapRect(r);
        return r;
    }

    public RectF getDrawRect() {
        return mDrawRect;
    }

    @SuppressWarnings("checkstyle:cyclomaticcomplexity")
    public int getHit(float x, float y) {
        final RectF rect = new RectF(mDrawRect);
        rect.inset(-mPadding, -mPadding);

        final float[] pts = new float[]{x, y};

        final Matrix rotateMatrix = new Matrix();
        rotateMatrix.postTranslate(-rect.centerX(), -rect.centerY());
        rotateMatrix.postRotate(-mRotation);
        rotateMatrix.postTranslate(rect.centerX(), rect.centerY());
        rotateMatrix.mapPoints(pts);

        x = pts[0];
        y = pts[1];

        int retval = NONE;
        final boolean verticalCheck = (y >= (rect.top - HIT_TOLERANCE)) && (y < (rect.bottom + HIT_TOLERANCE));
        final boolean horizCheck = (x >= (rect.left - HIT_TOLERANCE)) && (x < (rect.right + HIT_TOLERANCE));

        // if horizontal and vertical checks are good then
        // at least the move edge is selected
        if (verticalCheck && horizCheck) {
            retval = MOVE;
        }

        if (mScaleEnabled) {
            if ((Math.abs(rect.left - x) < HIT_TOLERANCE) && verticalCheck && UIUtils.checkBits(mResizeEdgeMode, GROW_LEFT_EDGE)) {
                retval |= GROW_LEFT_EDGE;
            }
            if ((Math.abs(rect.right - x) < HIT_TOLERANCE) && verticalCheck && UIUtils.checkBits(mResizeEdgeMode,
                                                                                                 GROW_RIGHT_EDGE)) {
                retval |= GROW_RIGHT_EDGE;
            }
            if ((Math.abs(rect.top - y) < HIT_TOLERANCE) && horizCheck && UIUtils.checkBits(mResizeEdgeMode, GROW_TOP_EDGE)) {
                retval |= GROW_TOP_EDGE;
            }
            if ((Math.abs(rect.bottom - y) < HIT_TOLERANCE) && horizCheck && UIUtils.checkBits(mResizeEdgeMode, GROW_BOTTOM_EDGE)) {
                retval |= GROW_BOTTOM_EDGE;
            }
        }

        if ((mRotateEnabled || mScaleEnabled) && (Math.abs(rect.right - x) < HIT_TOLERANCE) && (Math.abs(rect.bottom - y)
            < HIT_TOLERANCE) && verticalCheck && horizCheck) {
            retval = ROTATE;
        }

        if (mMoveEnabled && (retval == NONE) && rect.contains((int) x, (int) y)) {
            retval = MOVE;
        }

        if (null != mAnchorDelete) {
            if ((Math.abs(rect.left - x) < HIT_TOLERANCE) && (Math.abs(rect.top - y) < HIT_TOLERANCE) && verticalCheck
                && horizCheck) {
                retval = DELETE;
            }
        }

        if (null != mAnchorOpacity) {
            if ((Math.abs(rect.right - x) < HIT_TOLERANCE) && (Math.abs(rect.top - y) < HIT_TOLERANCE) && verticalCheck
                && horizCheck) {
                retval = OPACITY;
            }
        }

        return retval;
    }

    @SuppressWarnings("checkstyle:cyclomaticcomplexity")
    public void onSingleTapConfirmed(float x, float y) {
        final RectF rect = new RectF(mDrawRect);
        rect.inset(-mPadding, -mPadding);

        final float[] pts = new float[]{x, y};

        final Matrix rotateMatrix = new Matrix();
        rotateMatrix.postTranslate(-rect.centerX(), -rect.centerY());
        rotateMatrix.postRotate(-mRotation);
        rotateMatrix.postTranslate(rect.centerX(), rect.centerY());
        rotateMatrix.mapPoints(pts);

        x = pts[0];
        y = pts[1];

        final boolean verticalCheck = (y >= (rect.top - HIT_TOLERANCE)) && (y < (rect.bottom + HIT_TOLERANCE));
        final boolean horizCheck = (x >= (rect.left - HIT_TOLERANCE)) && (x < (rect.right + HIT_TOLERANCE));

        if (mAnchorDelete != null) {
            if ((Math.abs(rect.left - x) < HIT_TOLERANCE) && (Math.abs(rect.top - y) < HIT_TOLERANCE) && verticalCheck
                && horizCheck) {
                if (mDeleteClickListener != null) {
                    mDeleteClickListener.onDeleteClick();
                    return;
                }
            }
        }

        if (mAnchorRotate != null) {
            if ((Math.abs(rect.right - x) < HIT_TOLERANCE) && (Math.abs(rect.bottom - y) < HIT_TOLERANCE) && verticalCheck
                && horizCheck) {
                return;
            }
        }

        if (mAnchorOpacity != null) {
            if ((Math.abs(rect.right - x) < HIT_TOLERANCE) && (Math.abs(rect.top - y) < HIT_TOLERANCE) && verticalCheck
                && horizCheck) {
                Log.i(LOG_TAG, "sticker opacity on singleTapConfirmed");
                if (mOpacityChangeListener != null) {
                    mOpacityChangeListener.onChangeOpacity();
                    return;
                }
            }
        }

        if (mContent.getHorizontalFlipEnabled() && isSelected()) {
            if (mShouldFlip) {
                flipHorizontal();
            } else {
                mShouldFlip = true;
            }
        }
    }

    public void flipHorizontal() {
        if (null != mContent && mContent.getHorizontalFlipEnabled()) {
            final boolean flipped = mContent.getHorizontalFlip();
            ObjectAnimator anim = ObjectAnimator.ofFloat(mContent, "scaleX", flipped ? -1 : 1, flipped ? 1 : -1);
            anim.setDuration(150);
            anim.setInterpolator(new AccelerateDecelerateInterpolator());
            anim.start();
            anim.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(final Animator animation) {}

                @Override
                public void onAnimationEnd(final Animator animation) {
                    if (null != mContent) {
                        mContent.setHorizontalFlip(!flipped);
                    }
                }

                @Override
                public void onAnimationCancel(final Animator animation) {}

                @Override
                public void onAnimationRepeat(final Animator animation) {}
            });

            if (null != mContentFlipListener) {
                OnContentFlipListener listener = mContentFlipListener.get();
                if (null != listener) {
                    listener.onContentFlip(this);
                }
            }
        }
    }

    public Rect getInvalidationRect() {
        mInvalidateRectF.set(mDrawRect);
        mInvalidateRectF.inset(-mPadding, -mPadding);
        mRotateMatrix.mapRect(mInvalidateRectF);

        mInvalidateRect.set((int) mInvalidateRectF.left,
                            (int) mInvalidateRectF.top,
                            (int) mInvalidateRectF.right,
                            (int) mInvalidateRectF.bottom);

        int w = Math.max(mAnchorRotateWidth, mAnchorDeleteWidth);
        int h = Math.max(mAnchorRotateHeight, mAnchorDeleteHeight);

        mInvalidateRect.inset(-w * 2, -h * 2);
        return mInvalidateRect;
    }

    public Matrix getMatrix() {
        return mMatrix;
    }

    public int getMode() {
        return mMode;
    }

    public void setMode(final int mode) {
        if (mode != mMode) {
            mMode = mode;
            updateDrawableState();
        }
    }

    public float getRotation() {
        return mRotation;
    }

    public Matrix getRotationMatrix() {
        return mRotateMatrix;
    }

    protected void growBy(final float dx) {
        growBy(dx, dx / mRatio, true);
    }

    protected void growBy(final float dx, final float dy, boolean checkMinSize) {
        if (!mScaleEnabled) {
            return;
        }

        final RectF r = new RectF(mCropRect);

        if (mAlignVerticalMode == AlignModeV.Center) {
            r.inset(-dx, -dy);
        } else if (mAlignVerticalMode == AlignModeV.Top) {
            r.inset(-dx, 0);
            r.bottom += dy * 2;
        } else {
            r.inset(-dx, 0);
            r.top -= dy * 2;
        }

        RectF testRect = getDisplayRect(mMatrix, r);

        if (!mContent.validateSize(testRect) && checkMinSize) {
            return;
        }

        mCropRect.set(r);
        invalidate();
    }

    public void onMouseMove(int edge, MotionEvent event2, float dx, float dy) {
        if (edge == NONE) {
            return;
        }

        fpoints[0] = dx;
        fpoints[1] = dy;

        float xDelta;
        float yDelta;

        mShouldFlip = true;

        if (edge == MOVE) {
            moveBy(dx * (mCropRect.width() / mDrawRect.width()), dy * (mCropRect.height() / mDrawRect.height()));
        } else if (edge == ROTATE) {
            dx = fpoints[0];
            dy = fpoints[1];
            xDelta = dx * (mCropRect.width() / mDrawRect.width());
            yDelta = dy * (mCropRect.height() / mDrawRect.height());
            rotateBy(event2.getX(), event2.getY(), dx, dy);

            invalidate();
            // mContext.invalidate( getInvalidationRect() );
        } else {

            Matrix rotateMatrix = new Matrix();
            rotateMatrix.postRotate(-mRotation);
            rotateMatrix.mapPoints(fpoints);
            dx = fpoints[0];
            dy = fpoints[1];

            if (((GROW_LEFT_EDGE | GROW_RIGHT_EDGE) & edge) == 0) {
                dx = 0;
            }
            if (((GROW_TOP_EDGE | GROW_BOTTOM_EDGE) & edge) == 0) {
                dy = 0;
            }

            xDelta = dx * (mCropRect.width() / mDrawRect.width());
            yDelta = dy * (mCropRect.height() / mDrawRect.height());

            boolean isLeft = UIUtils.checkBits(edge, GROW_LEFT_EDGE);
            boolean isTop = UIUtils.checkBits(edge, GROW_TOP_EDGE);

            float delta;

            if (Math.abs(xDelta) >= Math.abs(yDelta)) {
                delta = xDelta;
                if (isLeft) {
                    delta *= -1;
                }
            } else {
                delta = yDelta;
                if (isTop) {
                    delta *= -1;
                }
            }

            growBy(delta);

            invalidate();
            // mContext.invalidate( getInvalidationRect() );
        }
    }

    void onMove(float dx, float dy) {
        moveBy(dx * (mCropRect.width() / mDrawRect.width()), dy * (mCropRect.height() / mDrawRect.height()));
    }

    void moveBy(final float dx, final float dy) {
        if (mMoveEnabled) {
            mCropRect.offset(dx, dy);
            invalidate();
        }
    }

    public void invalidate() {
        mDrawRect = computeLayout(); // true
        mRotateMatrix.reset();
        mRotateMatrix.postTranslate(-mDrawRect.centerX(), -mDrawRect.centerY());
        mRotateMatrix.postRotate(mRotation);
        mRotateMatrix.postTranslate(mDrawRect.centerX(), mDrawRect.centerY());
    }

    protected RectF computeLayout() {
        return getDisplayRect(mMatrix, mCropRect);
    }

    public RectF getDisplayRect(final Matrix m, final RectF supportRect) {
        final RectF r = new RectF(supportRect);
        m.mapRect(r);
        return r;
    }

    void rotateBy(final float dx, final float dy, float diffx, float diffy) {

        if (!mRotateEnabled && !mScaleEnabled) {
            return;
        }

        final float[] pt1 = new float[]{mDrawRect.centerX(), mDrawRect.centerY()};
        final float[] pt2 = new float[]{mDrawRect.right, mDrawRect.bottom};
        final float[] pt3 = new float[]{dx, dy};

        final double angle1 = Point2D.angleBetweenPoints(pt2, pt1);
        final double angle2 = Point2D.angleBetweenPoints(pt3, pt1);

        if (mRotateEnabled) {
            mRotation = -(float) (angle2 - angle1);
        }

        if (mScaleEnabled) {

            final Matrix rotateMatrix = new Matrix();
            rotateMatrix.postRotate(-mRotation);

            final float[] points = new float[]{diffx, diffy};
            rotateMatrix.mapPoints(points);

            diffx = points[0];
            diffy = points[1];

            final float xDelta = diffx * (mCropRect.width() / mDrawRect.width());
            final float yDelta = diffy * (mCropRect.height() / mDrawRect.height());

            final float[] pt4 = new float[]{mDrawRect.right + xDelta, mDrawRect.bottom + yDelta};
            final double distance1 = Point2D.distance(pt1, pt2);
            final double distance2 = Point2D.distance(pt1, pt4);
            final float distance = (float) (distance2 - distance1);
            growBy(distance);
        }

    }

    void onRotateAndGrow(double angle, float scaleFactor) {

        if (!mRotateEnabled) {
            mRotation -= (float) (angle);
        }

        if (mRotateEnabled) {
            mRotation -= (float) (angle);
            growBy(scaleFactor * (mCropRect.width() / mDrawRect.width()));
        }

        invalidate();
    }

    public void setHidden(final boolean hidden) {
        mHidden = hidden;
    }

    public boolean isPressed() {
        return isSelected() && mMode != NONE;
    }

    public void setOnDeleteClickListener(final OnDeleteClickListener listener) {
        mDeleteClickListener = listener;
    }

    public void setOnChangeOpacityListener(final OnOpacityChangeListener listener) {
        mOpacityChangeListener = listener;
    }

    public void setOnContentFlipListener(final OnContentFlipListener listener) {
        if (null != listener) {
            mContentFlipListener = new WeakReference<OnContentFlipListener>(listener);
        } else {
            mContentFlipListener = null;
        }
    }

    public void setup(
        final Context context, final Matrix m, final Rect imageRect, final RectF cropRect, final boolean maintainAspectRatio) {
        mMatrix = new Matrix(m);
        mRotation = 0;
        mRotateMatrix = new Matrix();
        mCropRect = cropRect;
        setMode(NONE);
        invalidate();
    }

    public void update(final Matrix imageMatrix, final Rect imageRect) {
        setMode(NONE);
        mMatrix = new Matrix(imageMatrix);
        mRotation = 0;
        mRotateMatrix = new Matrix();
        invalidate();
    }

    public boolean forceUpdate() {
        RectF cropRect = getCropRectF();
        RectF drawRect = getDrawRect();

        if (mEditableContent != null) {

            final float textWidth = mContent.getCurrentWidth();
            final float textHeight = mContent.getCurrentHeight();

            updateRatio();

            RectF textRect = new RectF(cropRect);
            getMatrix().mapRect(textRect);

            float dx = textWidth - textRect.width();
            float dy = textHeight - textRect.height();

            float[] fpoints = new float[]{dx, dy};

            Matrix rotateMatrix = new Matrix();
            rotateMatrix.postRotate(-mRotation);

            dx = fpoints[0];
            dy = fpoints[1];

            float xDelta = dx * (cropRect.width() / drawRect.width());
            float yDelta = dy * (cropRect.height() / drawRect.height());

            if (xDelta != 0 || yDelta != 0) {
                growBy(xDelta / 2, yDelta / 2, false);
            }

            invalidate();
            return true;
        }
        return false;
    }

    @Deprecated
    public void setPadding(int value) {
        mPadding = value;
    }

    @Override
    public void onSizeChanged(EditableDrawable content, float left, float top, float right, float bottom) {
        if (content.equals(mEditableContent) && null != mContext) {

            /*
            * final Matrix mImageMatrix = mContext.getImageViewMatrix();
            * final Matrix matrix = new Matrix( mImageMatrix );
            * matrix.invert( matrix );
            * final float[] pts = new float[] { left, top, right, bottom };
            * MatrixUtils.mapPoints( matrix, pts );
            * final RectF cropRect = new RectF( pts[0], pts[1], pts[2], pts[3] );
            * mCropRect.set( cropRect );
            */

            // RectF rect = new RectF( mDrawRect );
            // Matrix matrix = new Matrix( mMatrix );
            // matrix.invert( matrix );
            // matrix.mapRect( rect );
            // mCropRect.set( rect );

            if (mDrawRect.left != left || mDrawRect.top != top || mDrawRect.right != right || mDrawRect.bottom != bottom) {
                if (forceUpdate()) {
                    mContext.invalidate(getInvalidationRect());
                } else {
                    mContext.postInvalidate();
                }
            }
        }
    }

    public static enum AlignModeV {
        Top, Bottom, Center
    }

    public interface OnDeleteClickListener {
        void onDeleteClick();
    }

    public interface OnOpacityChangeListener {
        /** Opacity can be changed */
        void onChangeOpacity();

        /** Opacity can no longer be changed */
        void onLockOpacity();
    }

    public interface OnContentFlipListener {
        void onContentFlip(DrawableHighlightView content);
    }
}
