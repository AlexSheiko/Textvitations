package com.aviary.android.feather.sdk.overlays;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;

import com.aviary.android.feather.library.filters.ToolLoaderFactory;
import com.aviary.android.feather.sdk.R;

public class OverlayOverlay extends AviaryOverlay {
    private       Drawable         mTopArrow;
    private       Drawable         mBottomArrow;
    private       Drawable         mTitleDrawable1;
    private       CharSequence     mTitleText;
    private final Layout.Alignment mTextAlign;
    private final float            mTitleWidthFraction;

    public OverlayOverlay(Context context, int style) {
        super(context, ToolLoaderFactory.getToolName(ToolLoaderFactory.Tools.OVERLAYS) + "_pinch", style, ID_OVERLAYS_PINCH);

        final Resources res = context.getResources();
        mTopArrow = res.getDrawable(R.drawable.aviary_overlay_arrow_top);
        mBottomArrow = res.getDrawable(R.drawable.aviary_overlay_arrow_top);

        mTitleText = getTitleText(res);

        mTitleWidthFraction = getTitleWidthFraction(res);

        mTextAlign = Layout.Alignment.ALIGN_CENTER;
        addCloseButton(ALIGN_PARENT_LEFT, ALIGN_PARENT_BOTTOM);
    }

    protected float getTitleWidthFraction(final Resources res) {
        return res.getFraction(R.fraction.aviary_overlay_overlay_text_width, 100, 100);
    }

    protected CharSequence getTitleText(final Resources res) {
        return res.getString(R.string.feather_pinch_to_zoom);
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

        // detail test
        int titleWidth = (int) ((metrics.widthPixels) * (mTitleWidthFraction / 100));

        mTitleDrawable1 = generateTitleDrawable(getContext(), mTitleText, titleWidth, mTextAlign);

        int width = mTitleDrawable1.getIntrinsicWidth();
        int height = mTitleDrawable1.getIntrinsicHeight();

        Rect textBounds = new Rect(getWidth() / 2 - width / 2,
                                   getHeight() / 2 - height / 2,
                                   getWidth() / 2 + width / 2,
                                   getHeight() / 2 + height / 2);

        Rect topArrowBounds = generateBounds(mTopArrow, textBounds, getTextMargins(), "top");
        Rect bottomArrowBounds = generateBounds(mTopArrow, textBounds, getTextMargins(), "bottom");

        mTopArrow.setBounds(topArrowBounds);
        mBottomArrow.setBounds(bottomArrowBounds);
        mTitleDrawable1.setBounds(textBounds);
        mTitleDrawable1.setAlpha(alpha);
    }

    private Rect generateBounds(Drawable drawable, Rect relativeTo, int margins, CharSequence relativePosition) {
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

    @Override
    protected void doShow() {
        logger.info("doShow");
        if (!isAttachedToParent()) {
            return;
        }
        fadeIn();
    }

    @Override
    public void setAlpha(final float alpha) {
        mTopArrow.setAlpha((int) (alpha * 255));
        mBottomArrow.setAlpha((int) (alpha * 255));
        mTitleDrawable1.setAlpha((int) (alpha * 255));
        super.setAlpha(alpha);
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

        int count = canvas.save();

        // top arrow
        Matrix matrix = new Matrix();
        Rect textBounds = mTitleDrawable1.getBounds();
        matrix.setRotate(45, textBounds.centerX(), textBounds.centerY());
        canvas.concat(matrix);
        mTopArrow.draw(canvas);
        canvas.restoreToCount(count);

        // bottom arrow
        count = canvas.save();
        matrix.reset();
        matrix.setScale(1, -1, 0, mBottomArrow.getBounds().centerY());
        matrix.postRotate(45, textBounds.centerX(), textBounds.centerY());
        canvas.concat(matrix);
        mBottomArrow.draw(canvas);
        canvas.restoreToCount(count);

        // title
        mTitleDrawable1.draw(canvas);

        super.dispatchDraw(canvas);
    }
}
