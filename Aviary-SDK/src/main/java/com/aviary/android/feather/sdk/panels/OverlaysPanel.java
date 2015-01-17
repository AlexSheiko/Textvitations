package com.aviary.android.feather.sdk.panels;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import com.aviary.android.feather.cds.AviaryCds;
import com.aviary.android.feather.cds.CdsUtils;
import com.aviary.android.feather.cds.TrayColumns;
import com.aviary.android.feather.library.content.ToolEntry;
import com.aviary.android.feather.library.filters.OverlayFilter;
import com.aviary.android.feather.library.filters.ToolLoaderFactory;
import com.aviary.android.feather.library.services.IAviaryController;
import com.aviary.android.feather.library.utils.DecodeUtils;
import com.aviary.android.feather.library.utils.ImageInfo;
import com.aviary.android.feather.library.vo.ToolActionVO;
import com.aviary.android.feather.sdk.R;
import com.aviary.android.feather.sdk.overlays.AviaryOverlay;
import com.aviary.android.feather.sdk.overlays.OverlayOverlay;
import com.aviary.android.feather.sdk.widget.ImageViewOverlay;

import java.util.HashMap;
import java.util.Locale;

public class OverlaysPanel extends BordersPanel {
    private OverlayFilter mCurrentFilter;

    public OverlaysPanel(IAviaryController context, ToolEntry entry) {
        super(context, entry, AviaryCds.PackType.OVERLAY);
    }

    @Override
    protected int getTutorialOverlayId() {
        return AviaryOverlay.ID_OVERLAYS;
    }

    @Override
    public void onCreate(final Bitmap bitmap, final Bundle options) {
        super.onCreate(bitmap, options);
    }

    @Override
    protected View generateContentView(final LayoutInflater inflater) {
        return inflater.inflate(R.layout.aviary_content_overlays, null);
    }

    @Override
    protected void onSetupImageView() {
        ((ImageViewOverlay) mImageView).setImageBitmap(mPreview, null);
    }

    @Override
    protected void renderEffect(TrayColumns.TrayCursorWrapper item, int position, float intensity) {
        mLogger.info("renderEffect. item: %s", item);

        if (null != item) {

            final String path = item.getPath() + "/" + AviaryCds.getPackItemFilename(item.getIdentifier(),
                                                                                     AviaryCds.PackType.STICKER,
                                                                                     AviaryCds.Size.Medium);

            int maxSize = Math.max(mPreview.getWidth(), mPreview.getHeight());
            mLogger.log("path: %s", path);
            mLogger.log("max_size: %d", maxSize);

            Bitmap overlayBitmap =
                DecodeUtils.decode(getContext().getBaseContext(), Uri.parse(path), maxSize, maxSize, new ImageInfo());

            if (null != ((ImageViewOverlay) mImageView).getOverlayDrawable()) {
                ((ImageViewOverlay) mImageView).updateImageOverlay(overlayBitmap);
            } else {
                ((ImageViewOverlay) mImageView).setImageBitmap(mPreview, overlayBitmap);
            }
            setIsChanged(true);

            // filling the edit result
            ToolActionVO<String> toolAction = new ToolActionVO<String>();
            toolAction.setPackIdentifier(item.getPackageName());
            toolAction.setContentIdentifier(item.getIdentifier());

            getEditToolResult().setToolAction(toolAction);

            final String packContentPath = CdsUtils.getPackContentPath(getContext().getBaseContext(), item.getPackId());
            mLogger.log("packId: %d, contentPath: %s", item.getId(), packContentPath);
            mCurrentFilter = (OverlayFilter) ToolLoaderFactory.get(getName());
            mCurrentFilter.setSourceDir(packContentPath);
            mCurrentFilter.setUrl(item.getIdentifier());

            HashMap<String, String> attrs = new HashMap<String, String>();
            attrs.put("pack", item.getPackageName());
            attrs.put("item", item.getIdentifier());
            getContext().getTracker()
                    .tagEventAttributes(getName().name().toLowerCase(Locale.US) + ": item_previewed", attrs);

            mTrackingAttributes.put("pack", item.getPackageName());
            mTrackingAttributes.put("item", item.getIdentifier());

            if (AviaryOverlay.shouldShow(getContext(), AviaryOverlay.ID_OVERLAYS_PINCH)) {
                OverlayOverlay overlay = new OverlayOverlay(getContext().getBaseContext(), R.style.AviaryWidget_Overlay_Overlay);
                if (overlay.show()) {
                    overlay.setOnCloseListener(new AviaryOverlay.OnCloseListener() {
                                                   @Override
                                                   public void onClose(final AviaryOverlay overlay) {
                                                       overlay.dismiss();
                                                   }
                                               });
                }
            }

        } else {
            ((ImageViewOverlay) mImageView).setImageBitmap(mPreview, null);
            getEditToolResult().reset();
            setIsChanged(false);
        }
    }

    @Override
    protected void onGenerateFinalBitmap() {
        if (null != mCurrentFilter) {
            Bitmap result = ((ImageViewOverlay) mImageView).generateResultBitmap(mCurrentFilter);
            getEditToolResult().setActionList(mCurrentFilter.getActions());
            onComplete(result);
        } else {
            getContext().cancel();
        }
    }
}
