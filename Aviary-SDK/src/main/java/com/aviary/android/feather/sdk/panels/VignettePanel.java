package com.aviary.android.feather.sdk.panels;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.aviary.android.feather.headless.filters.NativeVignetteToolFilter;
import com.aviary.android.feather.library.content.ToolEntry;
import com.aviary.android.feather.library.services.IAviaryController;
import com.aviary.android.feather.library.utils.BitmapUtils;
import com.aviary.android.feather.library.utils.UIConfiguration;
import com.aviary.android.feather.library.vo.EditToolResultVO;
import com.aviary.android.feather.library.vo.ToolActionVO;
import com.aviary.android.feather.sdk.R;
import com.aviary.android.feather.sdk.widget.ImageViewVignette;

import it.sephiroth.android.library.imagezoom.ImageViewTouchBase;
import it.sephiroth.android.library.imagezoom.graphics.FastBitmapDrawable;

public class VignettePanel extends AbstractSliderContentPanel implements ImageViewVignette.OnVignetteChangeListener {
    public VignettePanel(
        final IAviaryController context, final ToolEntry entry) {
        super(context, entry);
    }

    @SuppressLint ("WrongViewCast")
    @Override
    public void onCreate(final Bitmap bitmap, final Bundle options) {
        super.onCreate(bitmap, options);

        mImageView = (it.sephiroth.android.library.imagezoom.ImageViewTouch) getContentView().findViewById(R.id.aviary_image);
        mImageView.setDisplayType(ImageViewTouchBase.DisplayType.FIT_IF_BIGGER);
        ImageViewVignette image = (ImageViewVignette) mImageView;

        setValue((image.getVignetteIntensity() + 100) / 2);
        ((NativeVignetteToolFilter) mFilter).createBitmap(mBitmap.getWidth(), mBitmap.getHeight(), 3);
    }

    @Override
    public void onActivate() {
        super.onActivate();

        Bitmap tempBitmap = ((NativeVignetteToolFilter) mFilter).getBitmap();

        ((ImageViewVignette) mImageView).setImageBitmap(mBitmap,
                                                        tempBitmap,
                                                        null,
                                                        ImageViewTouchBase.ZOOM_INVALID,
                                                        UIConfiguration.IMAGE_VIEW_MAX_ZOOM);
        ((ImageViewVignette) mImageView).setOnVignetteChangeListener(this);
        contentReady();
    }

    @Override
    public void onDeactivate() {
        ((ImageViewVignette) mImageView).setOnVignetteChangeListener(null);
        super.onDeactivate();
    }

    @Override
    protected void onDispose() {
        ((NativeVignetteToolFilter) mFilter).clearBitmap();
        ((NativeVignetteToolFilter) mFilter).dispose();
        super.onDispose();
    }

    @Override
    protected View generateContentView(final LayoutInflater inflater) {
        return inflater.inflate(R.layout.aviary_content_vignette, null);
    }

    @Override
    protected ViewGroup generateOptionView(final LayoutInflater inflater, final ViewGroup parent) {
        return (ViewGroup) inflater.inflate(R.layout.aviary_panel_wheel, parent, false);
    }

    @Override
    public boolean isRendering() {
        return false;
    }

    @Override
    protected void onSliderChanged(final int progress, final boolean fromUser) {
        if (fromUser) {
            int value = progress * 2 - 100;
            ((ImageViewVignette) mImageView).setVignetteIntensity(value);
        }
    }

    @Override
    protected void onSliderEnd(final int value) {}

    @Override
    protected void onSliderStart(final int value) {}

    @Override
    public void onVignetteChange(
        final ImageViewVignette imageView, final Bitmap vignetteBitmap, final RectF relativeRect, final int intensity,
        final float feather) {

        if (((NativeVignetteToolFilter) mFilter).renderPreview(relativeRect, intensity, feather)) {
            setIsChanged(true);
            mImageView.invalidate();
        }
    }

    @Override
    protected void onGenerateResult(final EditToolResultVO resultVO) {
        mPreview = BitmapUtils.copy(mBitmap, mBitmap.getConfig());
        ((ImageViewVignette) mImageView).generateBitmap(mPreview);

        Drawable drawable = mImageView.getDrawable();
        if (drawable instanceof FastBitmapDrawable) {
            ((FastBitmapDrawable) drawable).setBitmap(mPreview);
            ((NativeVignetteToolFilter) mFilter).clearBitmap();
            mImageView.invalidate();
        }

        mEditResult.setActionList(((NativeVignetteToolFilter) mFilter).getActions());
        mEditResult.setToolAction(new ToolActionVO<Integer>(0));
        onComplete(mPreview, resultVO);
    }
}
