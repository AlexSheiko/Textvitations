package com.aviary.android.feather.sdk.overlays;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import com.aviary.android.feather.sdk.R;
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.AnimatorSet;
import com.nineoldandroids.animation.ObjectAnimator;

import org.jetbrains.annotations.NotNull;

public class UndoRedoOverlay extends AviaryOverlay {
    private       Drawable         mRedoDrawable;
    private       Drawable         mUndoDrawable;
    private       Drawable         mTitleDrawable1;
    private       Drawable         mRedoTextDrawable;
    private       Drawable         mUndoTextDrawable;
    private       CharSequence     mTitleText;
    private       CharSequence     mRedoText;
    private       CharSequence     mUndoRext;
    private final Layout.Alignment mTextAlign;
    private final float            mTitleWidthFraction;

    public UndoRedoOverlay(Context context, int style) {
        super(context, "undo_redo", style, ID_UNDO_REDO);

        final Resources res = context.getResources();

        mRedoDrawable = res.getDrawable(R.drawable.aviary_overlay_undo_redo);
        mUndoDrawable = res.getDrawable(R.drawable.aviary_overlay_undo_undo);

        mTitleText = getTitleText(res);
        mRedoText = res.getString(R.string.feather_redo);
        mUndoRext = res.getString(R.string.feather_undo);

        mTitleWidthFraction = getTitleWidthFraction(res);

        mTextAlign = Layout.Alignment.ALIGN_CENTER;
    }

    protected float getTitleWidthFraction(final Resources res) {
        return 90;
    }

    protected CharSequence getTitleText(final Resources res) {
        return res.getString(R.string.feather_overlay_undo_title);
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {

        if (!isVisible() || !isActive()) {
            return true;
        }

        if (null != mCloseListener) {
            trackTutorialClosed(TAG_CLOSE_FROM_BACKGROUND);
            mCloseListener.onClose(this);
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            hide(TAG_CLOSE_FROM_BACKGROUND);
            return true;
        }
        return true;
    }

    @Override
    public void onClick(final View view) {
        logger.info("onClick: " + view);

        if (view == getCloseButton() && null != mCloseListener) {
            trackTutorialClosed(TAG_CLOSE_FROM_BUTTON);
            mCloseListener.onClose(this);
            return;
        }

        super.onClick(view);
    }

    @Override
    protected void calculatePositions() {
        logger.info("calculatePositions");
        calculateTextLayouts();
    }

    private void calculateTextLayouts() {
        if (!isAttachedToParent()) {
            return;
        }

        int alpha = 0;
        if (mOrientationChanged) alpha = 255;

        final DisplayMetrics metrics = getDisplayMetrics();

        int width = mRedoDrawable.getIntrinsicWidth();
        int height = mRedoDrawable.getIntrinsicHeight();

        // undo arrow
        int x = getWidth() / 2;
        int y = getHeight() / 2;
        final Rect bounds1 = new Rect(x - width / 2, y - height / 2, x + width / 2, y + height / 2);
        mUndoDrawable.setBounds(bounds1);
        mUndoDrawable.setAlpha(alpha);

        // undo text
        mUndoTextDrawable = generateTextDrawable(getContext(), mUndoRext, bounds1.width(), mTextAlign);
        Rect undoBounds = relativeBounds(mUndoTextDrawable, bounds1, 0, Gravity.BOTTOM);
        mUndoTextDrawable.setBounds(undoBounds);
        mUndoTextDrawable.setAlpha(alpha);

        // undo drawable
        y = (int) (undoBounds.bottom + dp2px(metrics, 60));
        final Rect bounds2 = new Rect(x - width / 2, y - height / 2, x + width / 2, y + height / 2);
        mRedoDrawable.setBounds(bounds2);
        mRedoDrawable.setAlpha(alpha);

        // Undo Text
        mRedoTextDrawable = generateTextDrawable(getContext(), mRedoText, bounds2.width(), mTextAlign);
        Rect redoBounds = relativeBounds(mRedoTextDrawable, bounds2, 0, Gravity.BOTTOM);
        mRedoTextDrawable.setBounds(redoBounds);
        mRedoTextDrawable.setAlpha(alpha);

        // Title text
        x = getWidth() / 2;
        final Rect bounds3 = new Rect(x, bounds1.top, x, bounds1.bottom);
        int titleWidth = (int) ((metrics.widthPixels) * (mTitleWidthFraction / 100));
        mTitleDrawable1 = generateTitleDrawable(getContext(), mTitleText, titleWidth, mTextAlign);
        Rect textBounds = generateBounds(mTitleDrawable1, bounds3, getTextMargins(), "top");
        mTitleDrawable1.setBounds(textBounds);
        mTitleDrawable1.setAlpha(alpha);
    }

    static float dp2px(DisplayMetrics metrics, float size) {
        return size * metrics.density;
    }

    private Rect generateBounds(
            @NotNull Drawable drawable, @NotNull Rect relativeTo, int margins, @NotNull CharSequence relativePosition) {
        final DisplayMetrics metrics = getDisplayMetrics();
        final int drawableWidth = drawable.getIntrinsicWidth();
        final int drawableHeight = drawable.getIntrinsicHeight();

        Rect textBounds = new Rect(0, 0, drawableWidth, drawableHeight);

        if ("top".equals(relativePosition)) {
            textBounds.offsetTo((metrics.widthPixels - drawableWidth) / 2, relativeTo.top - drawableHeight - margins);
        } else {
            textBounds.offsetTo((metrics.widthPixels - drawableWidth) / 2, relativeTo.bottom + margins);
        }
        return textBounds;
    }

    private Rect relativeBounds(
            @NotNull Drawable drawable, @NotNull Rect relativeTo, int margins, @NotNull int relativePosition) {
        final int drawableWidth = drawable.getIntrinsicWidth();
        final int drawableHeight = drawable.getIntrinsicHeight();

        Rect textBounds = new Rect(0, 0, drawableWidth, drawableHeight);
        textBounds.offsetTo((relativeTo.centerX() - drawableWidth / 2), 0);

        if (relativePosition == Gravity.TOP) {
            textBounds.offset(0, relativeTo.top - drawableHeight - margins);
        } else {
            textBounds.offset(0, relativeTo.bottom + margins);
        }
        return textBounds;
    }

    @Override
    protected void doShow() {
        logger.info("doShow");
        if (!isAttachedToParent()) {
            return;
        }
        fadeIn();
    }

    @Override
    protected Animator generateInAnimation() {

        AnimatorSet set = new AnimatorSet();

        Animator anim0 = ObjectAnimator.ofFloat(this, "alpha", 0, 1);
        anim0.setDuration(getAnimationDuration());

        Animator anim1 = ObjectAnimator.ofInt(this, "alpha1", 0, 255);
        anim1.setDuration(getAnimationDuration());
        anim1.setStartDelay(100);

        Animator anim2 = ObjectAnimator.ofInt(this, "alpha2", 0, 255);
        anim2.setDuration(getAnimationDuration());
        anim2.setStartDelay(200);

        Animator anim3 = ObjectAnimator.ofInt(this, "alpha3", 0, 255);
        anim3.setDuration(getAnimationDuration());
        anim3.setStartDelay(600);

        set.playSequentially(anim0, anim1, anim2, anim3);

        return set;
    }

    @Override
    public void setAlpha(final float alpha) {
        logger.info("setAlpha: " + alpha);
        super.setAlpha(alpha);
    }

    @SuppressWarnings ("unused")
    public void setAlpha1(int value) {
        mTitleDrawable1.setAlpha(value);
        postInvalidate();
    }

    public int getAlpha1() {
        return mTitleDrawable1.getAlpha();
    }

    @SuppressWarnings ("unused")
    public void setAlpha2(int value) {
        mUndoDrawable.setAlpha(value);
        mUndoTextDrawable.setAlpha(value);
        postInvalidate();
    }

    public int getAlpha2() {
        return mUndoDrawable.getAlpha();
    }

    @SuppressWarnings ("unused")
    public void setAlpha3(int value) {
        mRedoDrawable.setAlpha(value);
        mRedoTextDrawable.setAlpha(value);
        postInvalidate();
    }

    public int getAlpha3() {
        return mRedoDrawable.getAlpha();
    }

    @Override
    protected void inAnimationCompleted() {
        if (null != getCloseButton()) {
            getCloseButton().setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void dispatchDraw(final Canvas canvas) {
        if (getVisibility() != View.VISIBLE || !isAttachedToParent()) {
            return;
        }

        canvas.drawColor(getBackgroundColor());

        mRedoDrawable.draw(canvas);

        mUndoDrawable.draw(canvas);

        mTitleDrawable1.draw(canvas);

        mRedoTextDrawable.draw(canvas);

        mUndoTextDrawable.draw(canvas);

        super.dispatchDraw(canvas);
    }
}
