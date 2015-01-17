package com.aviary.android.feather.sdk.panels;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.aviary.android.feather.cds.AviaryCds.PackType;
import com.aviary.android.feather.cds.TrayColumns;
import com.aviary.android.feather.common.log.LoggerFactory;
import com.aviary.android.feather.common.utils.ApiHelper;
import com.aviary.android.feather.common.utils.SystemUtils;
import com.aviary.android.feather.headless.filters.INativeFilter;
import com.aviary.android.feather.headless.filters.NativeFilter;
import com.aviary.android.feather.headless.filters.NativeFilterProxy;
import com.aviary.android.feather.headless.filters.impl.EffectFilter;
import com.aviary.android.feather.headless.moa.MoaAction;
import com.aviary.android.feather.headless.moa.MoaActionFactory;
import com.aviary.android.feather.headless.moa.MoaActionList;
import com.aviary.android.feather.headless.moa.MoaResult;
import com.aviary.android.feather.library.content.ToolEntry;
import com.aviary.android.feather.library.filters.ToolLoaderFactory;
import com.aviary.android.feather.library.services.IAviaryController;
import com.aviary.android.feather.sdk.BuildConfig;
import com.aviary.android.feather.sdk.R;

import java.io.IOException;

import it.sephiroth.android.library.picasso.Picasso;
import it.sephiroth.android.library.picasso.Request;
import it.sephiroth.android.library.picasso.RequestHandler;

public class EffectsPanel extends BordersPanel {
    public static final double DEFAULT_THUMBNAIL_RESIZE_RATIO  = 1.4;
    public static final double THUMBNAIL_RESIZE_RATIO_SLOW_CPU = 2.0;
    /** thumbnail for effects */
    protected Bitmap         mThumbBitmap;
    private   int            mThumbPadding;
    private   int            mThumbRoundedCorners;
    private   int            mThumbStrokeColor;
    private   int            mThumbStrokeWidth;
    /* thumbnail resize factor */
    private   double         mFactor;
    private   RequestHandler mRequestHandler;

    public EffectsPanel(IAviaryController context, ToolEntry entry) {
        super(context, entry, PackType.EFFECT);
    }

    @Override
    protected boolean getIntensitySliderEnabled() {
        return ApiHelper.EFFECT_INTENSITY_AVAILABLE;
    }

    @Override
    protected boolean getIntensityIsManaged() {
        return false;
    }

    @Override
    public void onCreate(Bitmap bitmap, Bundle options) {
        super.onCreate(bitmap, options);

        mLogger.info("FastPreview enabled: " + mEnableFastPreview);

        mThumbPadding = mConfigService.getDimensionPixelSize(R.dimen.aviary_effect_thumb_padding);
        mThumbRoundedCorners = mConfigService.getDimensionPixelSize(R.dimen.aviary_effect_thumb_radius);
        mThumbStrokeWidth = mConfigService.getDimensionPixelSize(R.dimen.aviary_effect_thumb_stroke);
        mThumbStrokeColor = mConfigService.getColor(R.color.aviary_effect_thumb_stroke_color);

        mFactor = DEFAULT_THUMBNAIL_RESIZE_RATIO;

        int cpuSpeed = SystemUtils.CpuInfo.getCpuMhz();
        if (cpuSpeed > 0) {
            if (cpuSpeed < SystemUtils.CpuInfo.MHZ_CPU_FAST) {
                mFactor = THUMBNAIL_RESIZE_RATIO_SLOW_CPU;
            }
            mLogger.log("thumbnails scale factor: " + mFactor + " with cpu: " + cpuSpeed);
        }
    }

    @Override
    protected void onAddCustomRequestHandlers() {
        super.onAddCustomRequestHandlers();

        mThumbBitmap = generateThumbnail(mBitmap, mThumbSize, mThumbSize);
        mRequestHandler = new EffectsRequestHandler(mThumbBitmap,
                                                    mFactor,
                                                    mThumbSize,
                                                    mThumbPadding,
                                                    mThumbRoundedCorners,
                                                    mThumbStrokeColor,
                                                    mThumbStrokeWidth);
        try {
            mPicassoLibrary.addRequestHandler(mRequestHandler);
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onRemoveCustomRequestHandlers() {
        super.onRemoveCustomRequestHandlers();

        if (null != mRequestHandler) {
            mPicassoLibrary.removeRequestHandler(mRequestHandler);
        }
        mRequestHandler = null;
    }

    @Override
    protected void onPostActivate() {
        super.onPostActivate();
    }

    @Override
    protected void onDispose() {
        super.onDispose();
        if (mThumbBitmap != null && !mThumbBitmap.isRecycled()) {
            mThumbBitmap.recycle();
        }
        mThumbBitmap = null;
    }

    @Override
    protected ListAdapter createListAdapter(Context context, Cursor cursor) {
        return new EffectsListAdapter(context,
                                      R.layout.aviary_frame_item,
                                      R.layout.aviary_effect_item_more,
                                      R.layout.aviary_effect_item_external,
                                      R.layout.aviary_frame_item_divider,
                                      cursor);
    }

    @Override
    protected boolean isContentTutorialNeeded() {
        return false;
    }

    @Override
    protected RenderTask createRenderTask(int position, float intensity) {
        return new EffectsRenderTask(position, intensity);
    }

    @Override
    protected NativeFilter loadNativeFilter(
        final TrayColumns.TrayCursorWrapper item, int position, boolean hires, float intensity) {
        if (null != item && position > -1) {
            EffectFilter filter = (EffectFilter) ToolLoaderFactory.get(ToolLoaderFactory.Tools.EFFECTS);
            filter.setMoaLiteEffect(item.getPath() + "/" + item.getIdentifier() + ".json");
            filter.setPreviewSize(mPreview.getWidth(), mPreview.getHeight());
            filter.setIntensity(intensity);
            return filter;
        }
        return null;
    }

    @Override
    protected CharSequence[] getOptionalEffectsLabels() {
        return super.getOptionalEffectsLabels();
    }

    private Bitmap generateThumbnail(Bitmap input, int width, int height) {
        return ThumbnailUtils.extractThumbnail(input, (int) ((double) width / mFactor), (int) ((double) height / mFactor));
    }

    @Override
    protected void onProgressStart() {
        if (!mEnableFastPreview) {
            super.onProgressModalStart();
        } else {
            super.onProgressStart();
        }
    }

    @Override
    protected void onProgressEnd() {
        if (!mEnableFastPreview) {
            super.onProgressModalEnd();
        } else {
            super.onProgressEnd();
        }
    }

    /**
     * Custom request handler for effect's thumbnail
     */
    static class EffectsRequestHandler extends RequestHandler {
        /** scheme used to generate effects */
        static final String FILTER_SCHEME = "aviary_effect";
        private final double mFactor;
        private final int    mThumbSize;
        private final int    mThumbPadding;
        private final int    mThumbRoundedCorners;
        private final int    mThumbStrokeColor;
        private final int    mThumbStrokeWidth;
        private       Bitmap srcBitmap;

        public EffectsRequestHandler(
            Bitmap bitmap, final double mFactor, final int mThumbSize, final int mThumbPadding, final int mThumbRoundedCorners,
            final int mThumbStrokeColor, final int mThumbStrokeWidth) {
            srcBitmap = bitmap;
            this.mFactor = mFactor;
            this.mThumbSize = mThumbSize;
            this.mThumbPadding = mThumbPadding;
            this.mThumbRoundedCorners = mThumbRoundedCorners;
            this.mThumbStrokeColor = mThumbStrokeColor;
            this.mThumbStrokeWidth = mThumbStrokeWidth;
        }

        @Override
        public boolean canHandleRequest(final Request request) {
            if (null != request.uri) {
                final String scheme = request.uri.getScheme();
                return null != scheme && FILTER_SCHEME.equals(scheme);
            }
            return false;
        }

        @Override
        public Result load(final Request request) throws IOException {
            if (null != request.uri) {
                Bitmap bitmap = decode(request.uri);
                return new Result(bitmap, Picasso.LoadedFrom.NETWORK);
            }
            return null;
        }

        public Bitmap decode(Uri uri) throws IOException {
            try {
                return call(uri.getPath());
            } catch (Throwable t) {
                t.printStackTrace();
                throw new IOException(t);
            }
        }

        public Bitmap call(String filename) throws Exception {
            boolean isValid = true;
            INativeFilter filter = null;
            try {
                filter = loadFilter(filename);
            } catch (Throwable t) {
                t.printStackTrace();
                isValid = false;
            }

            MoaActionList actionList = actionsForRoundedThumbnail(isValid, filter);
            MoaResult moaresult = NativeFilterProxy.prepareActions(actionList, srcBitmap, null, 1, 1);
            moaresult.execute();
            Bitmap result = moaresult.outputBitmap;
            return result;
        }

        private INativeFilter loadFilter(CharSequence effectFileName) {
            EffectFilter filter = (EffectFilter) ToolLoaderFactory.get(ToolLoaderFactory.Tools.EFFECTS);
            filter.setMoaLiteEffect((String) effectFileName);
            filter.setPreviewSize(srcBitmap.getWidth(), srcBitmap.getHeight());

            if (BuildConfig.DEBUG) {
                Log.d(LoggerFactory.BASE_LOG_TAG, "loadFilter: " + effectFileName);
            }

            return filter;
        }

        MoaActionList actionsForRoundedThumbnail(final boolean isValid, INativeFilter filter) {
            MoaActionList actions = MoaActionFactory.actionList();
            MoaAction action;

            if (null != filter) {
                actions.addAll(filter.getActions());
            }

            if (mFactor != 1) {
                action = MoaActionFactory.action("resize");
                action.setValue("size", mThumbSize);
                action.setValue("force", true);
                actions.add(action);
            }

            action = MoaActionFactory.action("ext-roundedborders");
            action.setValue("padding", mThumbPadding);
            action.setValue("roundPx", mThumbRoundedCorners);
            action.setValue("strokeColor", mThumbStrokeColor);
            action.setValue("strokeWeight", mThumbStrokeWidth);

            if (!isValid) {
                action.setValue("overlaycolor", 0x99000000);
            }
            actions.add(action);

            return actions;
        }
    }

    protected class EffectsRenderTask extends RenderTask {
        public EffectsRenderTask(int position, float intensity) {
            super(position, intensity);
        }
    }

    class EffectsListAdapter extends ListAdapter {
        public EffectsListAdapter(
            Context context, int mainResId, int moreResId, int externalResId, int dividerResId, Cursor cursor) {
            super(context, mainResId, moreResId, externalResId, dividerResId, cursor);
        }
    }
}
