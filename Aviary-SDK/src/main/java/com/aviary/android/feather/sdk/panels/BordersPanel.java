package com.aviary.android.feather.sdk.panels;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.content.Loader.OnLoadCompleteListener;
import android.support.v4.util.SparseArrayCompat;
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;

import com.aviary.android.feather.cds.AviaryCds.PackType;
import com.aviary.android.feather.cds.PacksItemsColumns;
import com.aviary.android.feather.cds.TrayColumns;
import com.aviary.android.feather.common.tracking.AviaryTracker;
import com.aviary.android.feather.common.utils.ApiHelper;
import com.aviary.android.feather.common.utils.IOUtils;
import com.aviary.android.feather.common.utils.PackageManagerUtils;
import com.aviary.android.feather.common.utils.SystemUtils;
import com.aviary.android.feather.common.utils.os.AviaryAsyncTask;
import com.aviary.android.feather.headless.filters.IntensityNativeFilter;
import com.aviary.android.feather.headless.filters.NativeFilter;
import com.aviary.android.feather.headless.moa.MoaResult;
import com.aviary.android.feather.library.Constants;
import com.aviary.android.feather.library.content.ToolEntry;
import com.aviary.android.feather.library.filters.BorderFilter;
import com.aviary.android.feather.library.filters.ToolLoaderFactory;
import com.aviary.android.feather.library.services.ConfigService;
import com.aviary.android.feather.library.services.IAviaryController;
import com.aviary.android.feather.library.services.PreferenceService;
import com.aviary.android.feather.library.utils.BitmapUtils;
import com.aviary.android.feather.library.vo.ToolActionVO;
import com.aviary.android.feather.sdk.AviaryMainController.FeatherContext;
import com.aviary.android.feather.sdk.R;
import com.aviary.android.feather.sdk.graphics.PluginDividerDrawable;
import com.aviary.android.feather.sdk.overlays.AviaryOverlay;
import com.aviary.android.feather.sdk.overlays.StickersOverlay;
import com.aviary.android.feather.sdk.utils.PackIconCallable;
import com.aviary.android.feather.sdk.widget.EffectThumbLayout;
import com.aviary.android.feather.sdk.widget.IAPDialogDetail;
import com.aviary.android.feather.sdk.widget.IAPDialogMain;
import com.aviary.android.feather.sdk.widget.IAPDialogMain.IAPUpdater;
import com.aviary.android.feather.sdk.widget.IAPDialogMain.OnCloseListener;
import com.aviary.android.feather.sdk.widget.ImageViewWithIntensity;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import it.sephiroth.android.library.imagezoom.ImageViewTouch;
import it.sephiroth.android.library.imagezoom.ImageViewTouchBase.DisplayType;
import it.sephiroth.android.library.picasso.LruCache;
import it.sephiroth.android.library.picasso.Picasso;
import it.sephiroth.android.library.picasso.RequestCreator;
import it.sephiroth.android.library.tooltip.TooltipManager;
import it.sephiroth.android.library.widget.AdapterView.OnItemClickListener;
import it.sephiroth.android.library.widget.AdapterView.OnItemSelectedListener;
import it.sephiroth.android.library.widget.HListView;

public class BordersPanel extends AbstractContentPanel
    implements OnItemSelectedListener, OnItemClickListener, OnLoadCompleteListener<Cursor>,
               ImageViewWithIntensity.OnIntensityChange {
    public static final double ANCHOR_X_OFFSET        = 1.85;
    public static final double ANCHOR_MAX_WIDTH_RATIO = 2.5;
    private final PackType  mPackType;
    protected     HListView mHList;
    protected     View      mLoader;
    protected volatile Boolean mIsRendering = false;
    private volatile boolean           mIsAnimating;
    private          RenderTask        mCurrentTask;
    protected        ConfigService     mConfigService;
    protected        PreferenceService mPreferenceService;
    /** default width of each effect thumbnail */
    private int mCellWidth = 80;
    protected int      mThumbSize;
    protected Picasso  mPicassoLibrary;
    private   LruCache mCache;
    /* the first valid position of the list */
    protected int     mListFirstValidPosition = 0;
    private   boolean mFirstTime              = true;
    /** options used to decode cached images */
    private static BitmapFactory.Options mThumbnailOptions;
    protected       boolean mEnableFastPreview     = false;
    protected final float   mInitialIntensityValue = 255f;
    protected TrayColumns.TrayCursorWrapper mRenderedEffect;
    protected CursorAdapter                 mAdapter;
    protected CursorLoader                  mCursorLoader;
    protected ContentObserver               mContentObserver;
    protected IAPDialogMain                 mIapDialog;
    protected TooltipManager                mTooltipManager;
    /** tutorial overlay */
    StickersOverlay mOverlay;
    /** current filter */
    protected IntensityNativeFilter mFilter;
    private final        List<Long> mInstalledPacks    = new ArrayList<Long>();
    private static final int        MAX_MEM_CACHE_SIZE = 6 * IOUtils.MEGABYTE; // 6MB

    /**
     * Enable/disable intensity slider thing
     * <p/>
     * we are disabling frames intensity for now
     *
     * @return
     */
    protected boolean getIntensitySliderEnabled() {
        //return ApiHelper.AT_LEAST_14;
        return false;
    }

    /**
     * That means that the bitmap modification will be
     * managed by the panel and not by the ImageView widget
     *
     * @return
     */
    protected boolean getIntensityIsManaged() {
        return true;
    }

    /**
     * Content resolver has loaded
     */
    @Override
    @SuppressWarnings ("checkstyle:cyclomaticcomplexity")
    public void onLoadComplete(Loader<Cursor> loader, Cursor cursor) {
        mLogger.info("onLoadComplete");

        long iapDialogFeaturedId = -1;
        int lastInstalledPackIndex = -1;
        int firstValidIndex = -1;
        int index = 0;
        long optionsPackId = -1;
        long optionsContentId = -1;
        boolean smoothSelection = false;
        boolean forceUpdate = false;

        boolean checkFromIap = (!mFirstTime && null != mIapDialog && mIapDialog.isValid() && null != mIapDialog.getParent()
            && null != mIapDialog.getData());
        boolean checkFromOptions = false;
        boolean skipTutorial = false;
        boolean applySelected = false;

        // check if a pack has been installed from the IAP dialog
        if (checkFromIap) {
            IAPUpdater data = mIapDialog.getData();
            if (data.getFeaturedPackId() == data.getPackId() && data.getFeaturedPackId() > -1) {
                iapDialogFeaturedId = data.getFeaturedPackId();
            }
        }
        checkFromIap = iapDialogFeaturedId > -1;

        if (hasOptions() && mFirstTime && !checkFromIap) {
            final Bundle options = getOptions();
            optionsPackId = options.getLong(Constants.QuickLaunch.CONTENT_PACK_ID, -1);
            optionsContentId = options.getLong(Constants.QuickLaunch.CONTENT_ITEM_ID, -1);

            checkFromOptions = optionsPackId > -1 && optionsContentId > -1;

            // remove the extra from the option bundle, since it's a one time shot
            options.remove(Constants.QuickLaunch.CONTENT_PACK_ID);
            options.remove(Constants.QuickLaunch.CONTENT_ITEM_ID);
        }

        List<Long> tmpList = new ArrayList<Long>();

        if (null != cursor) {
            index = cursor.getPosition();
            while (cursor.moveToNext()) {
                int type = cursor.getInt(TrayColumns.TYPE_COLUMN_INDEX);

                if (type == TrayColumns.TYPE_PACK_INTERNAL) {
                    long packId = cursor.getLong(TrayColumns.ID_COLUMN_INDEX);
                    String identifier = cursor.getString(TrayColumns.IDENTIFIER_COLUMN_INDEX);

                    tmpList.add(packId);

                    if (!mFirstTime) {
                        if (!mInstalledPacks.contains(packId)) {
                            mLogger.log("adding %d (%s) to new packs", packId, identifier);
                            mLogger.log("iapDialogFeaturedId: %d, pack_id: %d", iapDialogFeaturedId, packId);

                            if (checkFromIap && iapDialogFeaturedId == packId) {
                                mLogger.log("setting new position based on featured: %d", packId);
                                lastInstalledPackIndex = cursor.getPosition();
                                smoothSelection = true;
                            }
                        }
                    }

                    if (firstValidIndex == -1) {
                        firstValidIndex = cursor.getPosition();
                    }
                    // break;

                } else if (type == TrayColumns.TYPE_CONTENT && checkFromOptions) {
                    long itemId = cursor.getLong(TrayColumns.ID_COLUMN_INDEX);
                    if (optionsContentId == itemId) {
                        lastInstalledPackIndex = cursor.getPosition();
                        checkFromOptions = false;
                        skipTutorial = true;
                        applySelected = true;
                        optionsPackId = -1;
                    }
                }
            }
            cursor.moveToPosition(index);
        }

        mInstalledPacks.clear();
        mInstalledPacks.addAll(tmpList);

        // update the adapter cursor
        mAdapter.changeCursor(cursor);

        mLogger.log("lastInstalledPackIndex: %d", lastInstalledPackIndex);

        if (lastInstalledPackIndex >= 0) {
            forceUpdate = true;
            firstValidIndex = lastInstalledPackIndex;
            removeIapDialog();
        }

        onEffectListUpdated(cursor, firstValidIndex, forceUpdate, smoothSelection, applySelected);

        // check optional messaging
        if (openStorePanelIfRequired(optionsPackId)) {
            return;
        }

        // skip tutorial if quick launch was enabled
        if (skipTutorial) {
            return;
        }

        if (isContentTutorialNeeded()) {
            createTutorialOverlayIfNecessary(firstValidIndex);
        }
    }

    public BordersPanel(IAviaryController context, ToolEntry entry) {
        this(context, entry, PackType.FRAME);
    }

    protected BordersPanel(IAviaryController context, ToolEntry entry, PackType type) {
        super(context, entry);
        mPackType = type;
    }

    private boolean openStorePanelIfRequired(long id) {
        mLogger.info("openStorePanelIfRequired: %d", id);
        // check optional messaging
        long iapPackageId = -1;
        if (hasOption(Constants.QuickLaunch.CONTENT_PACK_ID) || id > -1) {
            if (id > -1) {
                iapPackageId = id;
            } else if (hasOption(Constants.QuickLaunch.CONTENT_PACK_ID)) {
                Bundle options = getOptions();
                iapPackageId = options.getLong(Constants.QuickLaunch.CONTENT_PACK_ID);
                options.remove(Constants.QuickLaunch.CONTENT_PACK_ID);
            }

            mLogger.log("iapPackageId: %d", id);

            // display the iap dialog
            if (iapPackageId > -1) {
                IAPUpdater iapData = new IAPUpdater.Builder().setPackId(iapPackageId)
                    .setFeaturedPackId(iapPackageId)
                    .setEvent("shop_details: opened")
                    .setPackType(mPackType)
                    .addEventAttributes("pack", String.valueOf(iapPackageId))
                    .addEventAttributes("from", "message")
                    .build();

                displayIAPDialog(iapData);
                return true;
            }
        }
        return false;
    }

    @SuppressLint ("WrongViewCast")
    @Override
    public void onCreate(Bitmap bitmap, Bundle options) {
        super.onCreate(bitmap, options);

        mPicassoLibrary = Picasso.with(getContext().getBaseContext());
        mInstalledPacks.clear();

        double[] mem = new double[3];
        SystemUtils.MemoryInfo.getRuntimeMemory(mem);

        final double total = Math.max(mem[0], 2); // at least 2MB
        int maxSize = (int) (IOUtils.MEGABYTE * total);
        mLogger.log("max size for cache: " + maxSize);

        maxSize = Math.min(maxSize, MAX_MEM_CACHE_SIZE);
        mCache = new LruCache(maxSize);

        mThumbnailOptions = new Options();
        mThumbnailOptions.inPreferredConfig = Config.RGB_565;

        mConfigService = getContext().getService(ConfigService.class);
        mPreferenceService = getContext().getService(PreferenceService.class);
        mTooltipManager = TooltipManager.getInstance(getContext().getBaseActivity());

        mEnableFastPreview = ApiHelper.fastPreviewEnabled();

        mHList = (HListView) getOptionView().findViewById(R.id.aviary_list);
        mLoader = getOptionView().findViewById(R.id.aviary_loader);

        mCellWidth = mConfigService.getDimensionPixelSize(R.dimen.aviary_frame_item_width);
        mThumbSize = mConfigService.getDimensionPixelSize(R.dimen.aviary_frame_item_image_width);

        mPreview = BitmapUtils.copy(mBitmap, Bitmap.Config.ARGB_8888);

        mImageView = (ImageViewTouch) getContentView().findViewById(R.id.aviary_image);
        mImageView.setDisplayType(DisplayType.FIT_IF_BIGGER);
        if (getIntensitySliderEnabled()) {
            ((ImageViewWithIntensity) mImageView).setVaryTipStroke(false);
            ((ImageViewWithIntensity) mImageView).setVaryTipHue(true);
        }

        if (null != mPreferenceService && !mPreferenceService.containsSingleTimeKey(((Object) this).getClass(),
                                                                                    "intensity.slider.tooltip",
                                                                                    false)) {
            mSliderIntensityTooltip = 0;
        } else {
            mSliderIntensityTooltip = 1;
        }
    }

    @Override
    public void onBitmapReplaced(Bitmap bitmap) {
        super.onBitmapReplaced(bitmap);

        if (isActive()) {
            mLogger.error("TODO: BordersPanel check this");
            mHList.setSelection(mListFirstValidPosition);
        }
    }

    @Override
    public void onActivate() {
        super.onActivate();
        onSetupImageView();
        onAddCustomRequestHandlers();
        mHList.setOnItemClickListener(this);
        onPostActivate();
        contentReady();
    }

    /** register custom request handlers to the picasso library */
    protected void onAddCustomRequestHandlers() { }

    /** unregister custom request handlers */
    protected void onRemoveCustomRequestHandlers() { }

    protected void onSetupImageView() {
        if (getIntensitySliderEnabled()) {
            ((ImageViewWithIntensity) mImageView).setOnIntensityChangeListener(this);

            if (getIntensityIsManaged()) {
                mImageView.setImageBitmap(mPreview, null, 1, 1);
                ((ImageViewWithIntensity) mImageView).setIntensity(mInitialIntensityValue);
            } else {
                mImageView.setImageBitmap(mBitmap, null, 1, 1);
                ((ImageViewWithIntensity) mImageView).setPreviewBitmap(mPreview, mInitialIntensityValue);
            }
        } else {
            ((ImageViewWithIntensity) mImageView).setSwipeGestureEnabled(false);
            mImageView.setImageBitmap(mPreview, null, 1, 1);
        }

        mHList.setOnItemClickListener(this);
        onPostActivate();
        contentReady();
    }

    @Override
    public boolean isRendering() {
        return mIsRendering;
    }

    protected final PackType getPluginType() {
        return mPackType;
    }

    protected void onPostActivate() {
        updateInstalledPacks(true);
    }

    @Override
    public void onDestroy() {
        mConfigService = null;

        if (getIntensitySliderEnabled()) {
            ((ImageViewWithIntensity) mImageView).setPreviewBitmap(null, mInitialIntensityValue);
        }

        try {
            mCache.clear();
        } catch (Exception e) {
        }

        super.onDestroy();
    }

    @Override
    public void onDeactivate() {
        if (mPackType != PackType.FRAME) {
            onProgressEnd();
        }
        mHList.setOnItemClickListener(null);
        mHList.setAdapter(null);
        mHList.setOnScrollListener(null);

        removeIapDialog();

        if (null != mOverlay) {
            mOverlay.dismiss();
            mOverlay = null;
        }

        if (getIntensitySliderEnabled()) {
            ((ImageViewWithIntensity) mImageView).setOnIntensityChangeListener(null);
        }

        Context context = getContext().getBaseContext();
        context.getContentResolver().unregisterContentObserver(mContentObserver);

        if (null != mCursorLoader) {
            mCursorLoader.unregisterListener(this);
            mCursorLoader.stopLoading();
            mCursorLoader.abandon();
            mCursorLoader.reset();
        }

        if (null != mAdapter) {
            Cursor cursor = mAdapter.getCursor();
            IOUtils.closeSilently(cursor);
        }

        mAdapter = null;
        mCursorLoader = null;
        super.onDeactivate();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig, Configuration oldConfig) {
        if (mIapDialog != null) {
            mIapDialog.onConfigurationChanged(newConfig);
        }

        if (getIntensitySliderEnabled()) ((ImageViewWithIntensity) mImageView).finishIntensityChanging();

        super.onConfigurationChanged(newConfig, oldConfig);
    }

    @Override
    protected void onDispose() {
        mHList.setAdapter(null);
        onRemoveCustomRequestHandlers();
        super.onDispose();
    }

    @Override
    protected void onGenerateResult() {
        mLogger.info("onGenerateResult. isRendering: " + mIsRendering);
        if (mIsRendering) {
            GenerateResultTask task = new GenerateResultTask();
            task.execute();
        } else {
            onGenerateFinalBitmap();
        }
    }

    protected void onGenerateFinalBitmap() {
        if (getIntensitySliderEnabled() && !getIntensityIsManaged()) {
            // intensity enabled and not managed, ask the ImageView the result bitmap
            float intensity = ((ImageViewWithIntensity) mImageView).getIntensity();

            if (null != mFilter) {
                if (intensity < 255) {
                    mFilter.setIntensity(intensity / 255);
                } else {
                    mFilter.setIntensity(1);
                }
            }

            Bitmap resultBitmap;

            if (intensity == 255) {
                resultBitmap = mPreview;
            } else if (intensity == 0) {
                resultBitmap = mBitmap;
            } else {
                resultBitmap = mBitmap;
                if (!resultBitmap.isMutable()) {
                    resultBitmap = mBitmap.copy(mBitmap.getConfig(), true);
                }
                ((ImageViewWithIntensity) mImageView).generateBitmap(resultBitmap, intensity);

            }
            onComplete(resultBitmap);
        } else {
            onComplete(mPreview);
        }
    }

    @Override
    public boolean onBackPressed() {
        if (backHandled()) {
            return true;
        }
        return super.onBackPressed();
    }

    @Override
    public void onCancelled() {
        killCurrentTask();
        mIsRendering = false;
        super.onCancelled();
    }

    @Override
    public boolean getIsChanged() {
        return super.getIsChanged() || mIsRendering;
    }

    @Override
    protected ViewGroup generateOptionView(LayoutInflater inflater, ViewGroup parent) {
        return (ViewGroup) inflater.inflate(R.layout.aviary_panel_frames, parent, false);
    }

    @Override
    protected View generateContentView(final LayoutInflater inflater) {
        return inflater.inflate(R.layout.aviary_content_frames, null);
    }

    /**
     * Update the installed plugins
     */
    protected void updateInstalledPacks(boolean firstTime) {

        mLoader.setVisibility(View.VISIBLE);
        mHList.setVisibility(View.INVISIBLE);

        mAdapter = createListAdapter(getContext().getBaseContext(), null);
        mHList.setAdapter(mAdapter);

        Context context = getContext().getBaseContext();

        if (null == mCursorLoader) {

            final String uri = String.format(Locale.US, "packTray/%d/%d/%d/%s", 3, 0, 1, mPackType.toCdsString());

            Uri baseUri = PackageManagerUtils.getCDSProviderContentUri(context, uri);
            mCursorLoader = new CursorLoader(context, baseUri, null, null, null, null);
            mCursorLoader.registerListener(1, this);

            mContentObserver = new ContentObserver(new Handler()) {
                @Override
                public void onChange(boolean selfChange) {
                    mLogger.info("mContentObserver::onChange");
                    super.onChange(selfChange);

                    if (isActive() && null != mCursorLoader && mCursorLoader.isStarted()) {
                        mCursorLoader.onContentChanged();
                    }
                }
            };
            context.getContentResolver()
                .registerContentObserver(PackageManagerUtils.getCDSProviderContentUri(context,
                                                                                      "packTray/" + mPackType.toCdsString()),
                                         false,
                                         mContentObserver);
        }

        mCursorLoader.startLoading();
    }

    /**
     * Creates and returns the default adapter for the frames listview
     *
     * @param context
     * @param
     * @return
     */
    protected CursorAdapter createListAdapter(Context context, Cursor cursor) {

        return new ListAdapter(context,
                               R.layout.aviary_frame_item,
                               R.layout.aviary_effect_item_more,
                               R.layout.aviary_effect_item_external,
                               R.layout.aviary_frame_item_divider,
                               cursor);
    }

    private void onEffectListUpdated(
        Cursor cursor, int firstValidIndex, boolean forceSelection, boolean smoothSelection, boolean applySelected) {

        int mListFirstValidPosition = firstValidIndex > 0 ? firstValidIndex : 0;

        if (mFirstTime) {
            mLoader.setVisibility(View.INVISIBLE);
            mHList.setVisibility(View.VISIBLE);
        }

        if (mFirstTime || forceSelection) {
            if (mListFirstValidPosition > 0) {

                if (applySelected) {
                    applyEffect(mListFirstValidPosition, 500);
                }

                if (smoothSelection) {
                    mHList.smoothScrollToPositionFromLeft(mListFirstValidPosition - 1, mCellWidth / 2, 500);
                } else {
                    mHList.setSelectionFromLeft(mListFirstValidPosition - 1, mCellWidth / 2);
                }
            }
        }

        if (mFirstTime) {
            Animation animation = new AlphaAnimation(0, 1);
            animation.setFillAfter(true);
            animation.setDuration(getContext().getBaseContext().getResources().getInteger(android.R.integer.config_longAnimTime));
            mHList.startAnimation(animation);
        }

        mFirstTime = false;
    }

    /**
     * Returns true if the default content tutorial
     * is needed for the first time user
     *
     * @return
     */
    protected boolean isContentTutorialNeeded() {
        return true;
    }

    private void createTutorialOverlayIfNecessary(final int firstValidIndex) {
        mLogger.info("createTutorialOverlayIfNecessary: %d", firstValidIndex);

        if (!isActive()) {
            return;
        }
        if (null == getHandler()) {
            return;
        }

        getHandler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (firstValidIndex < 0) {
                    createTutorialOverlayIfNecessaryDelayed(firstValidIndex);
                }
            }
        }, 200);
    }

    @SuppressWarnings ("checkstyle:cyclomaticcomplexity")
    private boolean createTutorialOverlayIfNecessaryDelayed(final int firstValidIndex) {
        mLogger.info("createTutorialOverlayIfNecessaryDelayed: %d", firstValidIndex);

        if (!isActive()) {
            return false;
        }

        boolean shouldProceed = true;

        int count = mHList.getChildCount();
        int validIndex = -1;
        View validView = null;
        boolean free = false;

        for (int i = 0; i < count; i++) {
            View view = mHList.getChildAt(i);
            if (null != view) {
                Object tag = view.getTag();
                if (null != tag && tag instanceof ViewHolder) {
                    ViewHolder holder = (ViewHolder) tag;

                    if (holder.type == ListAdapter.TYPE_NORMAL) {
                        shouldProceed = false;
                        break;
                    }

                    if (holder.type == ListAdapter.TYPE_EXTERNAL) {
                        ViewHolderExternal holderExt = (ViewHolderExternal) holder;
                        free = holderExt.free == 1;
                        if (free) {
                            validIndex = i;
                            validView = holderExt.image;
                        }
                    }
                }
            }
        }

        if (!free || !(validIndex > -1) || null == validView) {
            shouldProceed = false;
        }

        if (!shouldProceed) {
            if (null != mOverlay) {
                mOverlay.hide();
            }
            return false;
        }

        mLogger.log("free item index: %d", validIndex);

        if (null == mOverlay) {
            if (AviaryOverlay.shouldShow(getContext(), getTutorialOverlayId())) {
                mOverlay = createTutorialOverlay(validView);
                return mOverlay.show();
            }
        } else {
            mOverlay.update(validView);
        }
        return false;
    }

    protected int getTutorialOverlayId() {
        return AviaryOverlay.ID_FRAMES;
    }

    protected StickersOverlay createTutorialOverlay(@NotNull View validView) {
        StickersOverlay result = new StickersOverlay(getContext().getBaseActivity(),
                                                     R.style.AviaryWidget_Overlay_Frames,
                                                     validView,
                                                     getName(),
                                                     getTutorialOverlayId());
        result.setTitle(AbstractPanelLoaderService.getToolDisplayName(getName()));
        return result;
    }

    // ///////////////
    // IAP - Dialog //
    // ///////////////

    private void displayIAPDialog(IAPUpdater data) {
        if (null != mIapDialog) {
            if (mIapDialog.isValid()) {
                mIapDialog.update(data);
                setApplyEnabled(false);
                return;
            } else {
                mIapDialog.dismiss(false);
                mIapDialog = null;
            }
        }

        IAPDialogMain dialog = IAPDialogMain.create((FeatherContext) getContext().getBaseContext(), data);
        if (dialog != null) {
            dialog.setOnCloseListener(new OnCloseListener() {
                @Override
                public void onClose() {
                    removeIapDialog();
                }
            });
        }
        mIapDialog = dialog;
        setApplyEnabled(false);

        // TODO: add "Store: Opened" tracking event
    }

    private boolean removeIapDialog() {
        setApplyEnabled(true);
        if (null != mIapDialog) {
            mIapDialog.dismiss(true);
            mIapDialog = null;
            return true;
        }
        return false;
    }

    private void applyEffect(final int position, long delay) {
        if (!isActive() || null == getHandler()) {
            return;
        }
        getHandler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isActive() || null == mAdapter || null == mHList) {
                    return;
                }
                mHList.clearChoices();

                if (position >= mHList.getFirstVisiblePosition() && position < mHList.getLastVisiblePosition()
                    && position < mAdapter.getCount()) {
                    View view = mHList.getChildAt(position - mHList.getFirstVisiblePosition());
                    if (null != view && view instanceof EffectThumbLayout) {
                        mHList.performItemClick(view, position, mAdapter.getItemId(position));
                    }
                }
            }
        }, delay);
    }

    protected void renderEffect(int position, float intensity) {
        if (null == mAdapter) {
            return;
        }
        if (position < 0 || position >= mAdapter.getCount()) {
            return;
        }

        Cursor cursor = (Cursor) mAdapter.getItem(position);
        if (null != cursor) {
            TrayColumns.TrayCursorWrapper item = TrayColumns.TrayCursorWrapper.create(cursor);
            if (null != item) {
                renderEffect(item, position, intensity);
            }
        }
    }

    protected void renderEffect(TrayColumns.TrayCursorWrapper item, int position, float intensity) {
        killCurrentTask();
        mCurrentTask = createRenderTask(position, intensity);
        mCurrentTask.execute(item);
    }

    protected RenderTask createRenderTask(int position, float intensity) {
        return new RenderTask(position, intensity);
    }

    boolean killCurrentTask() {
        if (mCurrentTask != null) {
            if (mPackType != PackType.FRAME) {
                onProgressEnd();
            }
            return mCurrentTask.cancel(true);
        }
        return false;
    }

    protected NativeFilter loadNativeFilter(
        final TrayColumns.TrayCursorWrapper item, int position, boolean hires, float intensity) throws JSONException {

        if (null != item && position > -1) {
            BorderFilter filter = (BorderFilter) ToolLoaderFactory.get(ToolLoaderFactory.Tools.FRAMES);
            Cursor cursor = getContext().getBaseContext()
                .getContentResolver()
                .query(PackageManagerUtils.getCDSProviderContentUri(getContext().getBaseContext(),
                                                                    "pack/content/item/" + item.getId()), null, null, null, null);
            double frameWidth = 0;
            try {
                if (null != cursor) {
                    if (cursor.moveToFirst()) {
                        byte[] options = cursor.getBlob(cursor.getColumnIndex(PacksItemsColumns.OPTIONS));
                        JSONObject object = new JSONObject(new String(options));
                        frameWidth = object.getDouble("width");
                    }
                }
            } finally {
                IOUtils.closeSilently(cursor);
            }

            filter.setHiRes(hires);
            filter.setSize(frameWidth);
            filter.setIdentifier(item.getIdentifier());
            filter.setSourceDir(item.getPath());
            filter.setIntensity(intensity / 255);
            return filter;
        }
        return null;
    }

    boolean backHandled() {
        if (mIsAnimating) {
            return true;
        }
        if (null != mIapDialog) {
            if (mIapDialog.onBackPressed()) {
                return true;
            }
            removeIapDialog();
            return true;
        }

        if (null != mOverlay) {
            if (mOverlay.onBackPressed()) {
                return true;
            }
        }

        killCurrentTask();
        return false;
    }

    // -----------------------
    // Intensity listeners
    // -----------------------

    @Override
    public void onIntensitySwipeStarted(final float intensity) {
    }

    @Override
    public void onIntensitySwipeChanging(final float intensity) {
        if (getIntensityIsManaged()) {
            onIntensitySwipeChanged(intensity);
        }
    }

    @Override
    public void onIntensitySwipeChanged(final float intensity) {
        if (mPackType == PackType.FRAME) {
            SparseArrayCompat<Boolean> positions = mHList.getCheckedItemPositions();
            if (null != positions && positions.size() > 0) {
                for (int i = 0; i < positions.size(); i++) {
                    int key = positions.keyAt(i);
                    if (positions.get(key)) {
                        renderEffect(key, intensity);
                    }
                }
            }
        }
    }

    @Override
    public void onIntensityInit() {
        if (mRenderedEffect != null) {
            AviaryTracker.getInstance(getContext().getBaseContext())
                .tagEvent(getName().name().toLowerCase(Locale.US) + ": intensity_initiated",
                          "pack",
                          mRenderedEffect.getPackageName(),
                          "item",
                          mRenderedEffect.getIdentifier());
        }
    }

    static class ViewHolder {
        protected TextView  text;
        protected ImageView image;
        protected int       type;
        protected long      id;
        protected String    identifier;
        protected Object    obj;
        protected boolean   isNew;
    }

    static class ViewHolderExternal extends ViewHolder {
        protected ImageView externalIcon;
        protected int       free;
    }

    class ListAdapter extends CursorAdapter {
        static final int TYPE_INVALID       = -1;
        static final int TYPE_LEFT_GETMORE  = TrayColumns.TYPE_LEFT_GETMORE;
        static final int TYPE_RIGHT_GETMORE = TrayColumns.TYPE_RIGHT_GETMORE;
        static final int TYPE_NORMAL        = TrayColumns.TYPE_CONTENT;
        static final int TYPE_EXTERNAL      = TrayColumns.TYPE_PACK_EXTERNAL;
        static final int TYPE_DIVIDER       = TrayColumns.TYPE_PACK_INTERNAL;
        static final int TYPE_LEFT_DIVIDER  = TrayColumns.TYPE_LEFT_DIVIDER;
        static final int TYPE_RIGHT_DIVIDER = TrayColumns.TYPE_RIGHT_DIVIDER;
        LayoutInflater mInflater;
        int            mDefaultResId;
        int            mMoreResId;
        int            mExternalResId;
        int            mDividerResId;
        int mCount = -1;
        int mIdColumnIndex;
        int mPackageNameColumnIndex;
        int mIdentifierColumnIndex;
        int mTypeColumnIndex;
        int mDisplayNameColumnIndex;
        int mPathColumnIndex;
        int mIsFreeColumnIndex;

        public ListAdapter(Context context, int defaultResId, int moreResId, int externalResId, int dividerResId, Cursor cursor) {
            super(context, cursor, 0);
            initColumns(cursor);

            mInflater = LayoutInflater.from(context);

            mDefaultResId = defaultResId;
            mMoreResId = moreResId;
            mExternalResId = externalResId;
            mDividerResId = dividerResId;
        }

        private void initColumns(Cursor cursor) {
            if (null != cursor) {
                mIdColumnIndex = cursor.getColumnIndex(TrayColumns._ID);
                mPackageNameColumnIndex = cursor.getColumnIndex(TrayColumns.PACKAGE_NAME);
                mIdentifierColumnIndex = cursor.getColumnIndex(TrayColumns.IDENTIFIER);
                mTypeColumnIndex = cursor.getColumnIndex(TrayColumns.TYPE);
                mDisplayNameColumnIndex = cursor.getColumnIndex(TrayColumns.DISPLAY_NAME);
                mPathColumnIndex = cursor.getColumnIndex(TrayColumns.PATH);
                mIsFreeColumnIndex = cursor.getColumnIndex(TrayColumns.IS_FREE);
            }
        }

        @Override
        public Cursor swapCursor(Cursor newCursor) {
            initColumns(newCursor);
            return super.swapCursor(newCursor);
        }

        @Override
        protected void onContentChanged() {
            super.onContentChanged();
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public int getViewTypeCount() {
            return 7;
        }

        @Override
        public int getItemViewType(int position) {
            Cursor cursor = (Cursor) getItem(position);
            if (null != cursor) {
                return cursor.getInt(mTypeColumnIndex);
            }
            return TYPE_INVALID;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (!mDataValid) {
                throw new IllegalStateException("this should only be called when the cursor is valid");
            }

            View v;
            if (convertView == null) {
                v = newView(mContext, mCursor, parent, position);
            } else {
                v = convertView;
            }
            bindView(v, mContext, mCursor, position);
            return v;
        }

        @SuppressWarnings ("checkstyle:cyclomaticcomplexity")
        private View newView(Context context, Cursor cursor, ViewGroup parent, int position) {

            final int type = getItemViewType(position);

            View view;
            int layoutWidth;
            ViewHolder holder;

            switch (type) {
                case TYPE_LEFT_GETMORE:
                    view = mInflater.inflate(mMoreResId, parent, false);
                    ((ImageView) view.findViewById(R.id.aviary_image)).setImageResource(
                        mPackType == PackType.EFFECT ? R.drawable.aviary_effect_item_getmore
                            : R.drawable.aviary_frame_item_getmore);
                    layoutWidth = mCellWidth;
                    break;

                case TYPE_RIGHT_GETMORE:
                    view = mInflater.inflate(mMoreResId, parent, false);
                    ((ImageView) view.findViewById(R.id.aviary_image)).setImageResource(
                        mPackType == PackType.EFFECT ? R.drawable.aviary_effect_item_getmore
                            : R.drawable.aviary_frame_item_getmore);
                    layoutWidth = mCellWidth;

                    if (parent.getChildCount() > 0 && mHList.getFirstVisiblePosition() == 0) {
                        View lastView = parent.getChildAt(parent.getChildCount() - 1);

                        if (lastView.getRight() < parent.getWidth()) {
                            view.setVisibility(View.INVISIBLE);
                            layoutWidth = 1;
                        }
                    }

                    break;

                case TYPE_DIVIDER:
                    view = mInflater.inflate(mDividerResId, parent, false);
                    layoutWidth = LayoutParams.WRAP_CONTENT;
                    break;

                case TYPE_EXTERNAL:
                    view = mInflater.inflate(mExternalResId, parent, false);
                    layoutWidth = mCellWidth;
                    break;

                case TYPE_LEFT_DIVIDER:
                    view = mInflater.inflate(R.layout.aviary_thumb_divider_right, parent, false);
                    layoutWidth = LayoutParams.WRAP_CONTENT;
                    break;

                case TYPE_RIGHT_DIVIDER:
                    view = mInflater.inflate(R.layout.aviary_thumb_divider_left, parent, false);
                    layoutWidth = LayoutParams.WRAP_CONTENT;

                    if (parent.getChildCount() > 0 && mHList.getFirstVisiblePosition() == 0) {
                        View lastView = parent.getChildAt(parent.getChildCount() - 1);

                        if (lastView.getRight() < parent.getWidth()) {
                            view.setVisibility(View.INVISIBLE);
                            layoutWidth = 1;
                        }
                    }
                    break;

                case TYPE_NORMAL:
                default:
                    view = mInflater.inflate(mDefaultResId, parent, false);
                    layoutWidth = mCellWidth;
                    break;
            }

            view.setLayoutParams(new LayoutParams(layoutWidth, LayoutParams.MATCH_PARENT));

            if (type == TYPE_EXTERNAL) {
                holder = new ViewHolderExternal();
            } else {
                holder = new ViewHolder();
            }

            holder.type = type;
            holder.image = (ImageView) view.findViewById(R.id.aviary_image);
            holder.text = (TextView) view.findViewById(R.id.aviary_text);

            if (type != TYPE_DIVIDER && holder.image != null) {
                LayoutParams params = holder.image.getLayoutParams();
                params.height = mThumbSize;
                params.width = mThumbSize;
                holder.image.setLayoutParams(params);
            }

            view.setTag(holder);
            return view;
        }

        void bindView(View view, Context context, Cursor cursor, int position) {
            final ViewHolder holder = (ViewHolder) view.getTag();
            String displayName;
            String identifier;
            String path;
            long id = -1;

            if (!cursor.isAfterLast() && !cursor.isBeforeFirst()) {
                id = cursor.getLong(mIdColumnIndex);
            }

            boolean isChecked = mHList.getCheckedItemPositions().get(position, false);

            if (holder.type == TYPE_NORMAL) {
                displayName = cursor.getString(mDisplayNameColumnIndex);
                identifier = cursor.getString(mIdentifierColumnIndex);
                path = cursor.getString(mPathColumnIndex);

                holder.text.setText(displayName);
                holder.identifier = identifier;

                if (holder.id != id) {
                    final String file;

                    holder.image.setImageBitmap(null);

                    if (mPackType == PackType.EFFECT) {
                        file = EffectsPanel.EffectsRequestHandler.FILTER_SCHEME + "://" + path + "/" + identifier + ".json";
                    } else {
                        if (!path.startsWith(ContentResolver.SCHEME_FILE + "://")) {
                            path = ContentResolver.SCHEME_FILE + "://" + path;
                        }
                        file = path + "/" + identifier + "-small.png";
                    }

                    RequestCreator request =
                        mPicassoLibrary.load(Uri.parse(file)).fade(200).error(R.drawable.aviary_ic_na).withCache(mCache);

                    request.into(holder.image);
                }

                EffectThumbLayout effectThumbLayout = (EffectThumbLayout) view;
                effectThumbLayout.setIsOpened(isChecked);

            } else if (holder.type == TYPE_EXTERNAL) {
                ViewHolderExternal holderExternal = (ViewHolderExternal) holder;

                identifier = cursor.getString(mIdentifierColumnIndex);
                displayName = cursor.getString(mDisplayNameColumnIndex);
                String icon = cursor.getString(mPathColumnIndex);
                int free = cursor.getInt(mIsFreeColumnIndex);

                holder.text.setText(displayName);
                holder.identifier = identifier;
                holderExternal.free = free;

                if (holder.id != id) {
                    mPicassoLibrary.load(icon)
                        .transform(new PackIconCallable.Builder().withResources(getContext().getBaseContext().getResources())
                                       .withPackType(mPackType)
                                       .withPath(icon)
                                       .build())
                        .error(R.drawable.aviary_ic_na)
                        .into(holder.image);
                }

            } else if (holder.type == TYPE_DIVIDER) {
                Drawable drawable = holder.image.getDrawable();
                displayName = cursor.getString(mDisplayNameColumnIndex);

                if (drawable instanceof PluginDividerDrawable) {
                    ((PluginDividerDrawable) drawable).setTitle(displayName);
                } else {
                    PluginDividerDrawable d = new PluginDividerDrawable(getContext().getBaseContext(),
                                                                        R.attr.aviaryEffectThumbDividerTextStyle,
                                                                        displayName);
                    holder.image.setImageDrawable(d);
                }
            }

            holder.id = id;
        }

        @Override
        public View newView(Context arg0, Cursor arg1, ViewGroup arg2) {
            return null;
        }

        @Override
        public void bindView(View arg0, Context arg1, Cursor arg2) {}

    }

    // ////////////////////////
    // OnItemClickedListener //
    // ////////////////////////

    @Override
    @SuppressWarnings ("checkstyle:cyclomaticcomplexity")
    public void onItemClick(it.sephiroth.android.library.widget.AdapterView<?> parent, View view, int position, long id) {
        mLogger.info("onItemClick: " + position);

        if (null != mOverlay) {
            mOverlay.hide();
        }

        int checkedItemsCount = mHList.getCheckedItemCount();

        // get the current selection and remove the current position
        SparseArrayCompat<Boolean> checked = mHList.getCheckedItemPositions().clone();
        checked.remove(position);

        if (isActive()) {
            ViewHolder holder = (ViewHolder) view.getTag();

            if (null != holder) {

                final boolean validPosition = holder.type == ListAdapter.TYPE_NORMAL;

                if (holder.type == ListAdapter.TYPE_LEFT_GETMORE || holder.type == ListAdapter.TYPE_RIGHT_GETMORE) {

                    String side = holder.type == ListAdapter.TYPE_RIGHT_GETMORE ? "right" : "left";

                    displayIAPDialog(new IAPUpdater.Builder().setPackType(mPackType)
                                         .setEvent("shop_list: opened")
                                         .setFeaturedPackId(-1)
                                         .addEventAttributes("from", getName().name().toLowerCase(Locale.US))
                                         .addEventAttributes("side", side)
                                         .build());

                } else if (holder.type == ListAdapter.TYPE_EXTERNAL) {

                    Bundle extras = new Bundle();
                    extras.putInt(IAPDialogDetail.EXTRA_CLICK_FROM_POSITION, position);

                    displayIAPDialog(new IAPUpdater.Builder().setPackId(holder.id)
                                         .setPackType(mPackType)
                                         .setFeaturedPackId(holder.id)
                                         .setEvent("shop_details: opened")
                                         .addEventAttributes("pack", holder.identifier)
                                         .addEventAttributes("from", "featured")
                                         .setExtras(extras)
                                         .build());

                } else if (holder.type == ListAdapter.TYPE_NORMAL) {
                    removeIapDialog();

                    mLogger.log("checkedItemsCount: %d", checkedItemsCount);

                    if (checkedItemsCount > 0) {
                        renderEffect(position, mInitialIntensityValue);
                    } else {
                        renderEffect(null, -1, mInitialIntensityValue);
                    }
                }

                if (validPosition) {
                    EffectThumbLayout layout = (EffectThumbLayout) view;

                    if (layout.isChecked()) {
                        layout.open();
                    } else {
                        layout.close();
                    }
                } else {
                    mHList.setItemChecked(position, false);
                }

                if (checked.size() > 0 && validPosition) {
                    mHList.setItemChecked(checked.keyAt(0), false);
                }

            }
        }

        // mHList.setItemChecked( position, true );
    }

    // /////////////////////////
    // OnItemSelectedListener //
    // /////////////////////////

    @Override
    public void onItemSelected(it.sephiroth.android.library.widget.AdapterView<?> arg0, View arg1, int arg2, long arg3) {
    }

    @Override
    public void onNothingSelected(it.sephiroth.android.library.widget.AdapterView<?> parent) {
    }

    protected CharSequence[] getOptionalEffectsLabels() {
        if (null != mConfigService) {
            return new CharSequence[]{mConfigService.getString(R.string.feather_original)};
        } else {
            return new CharSequence[]{"Original"};
        }
    }

    /**
     * Render the selected effect
     */
    protected int mSliderIntensityTooltip = 0;

    protected class RenderTask extends AviaryAsyncTask<TrayColumns.TrayCursorWrapper, Bitmap, Bitmap> implements OnCancelListener {
        int                           mPosition;
        String                        mError;
        MoaResult                     mMoaMainExecutor;
        TrayColumns.TrayCursorWrapper currentEffect;
        IntensityNativeFilter         filter;
        float                         intensity;

        /**
         * Instantiates a new render task.
         *
         * @param
         */
        public RenderTask(final int position, float intensity) {
            this.mPosition = position;
            this.intensity = intensity;
        }

        @Override
        protected void doPreExecute() {
            if (mPackType != PackType.FRAME) {
                onProgressStart();
            }
            mIsAnimating = true;

            ((ImageViewWithIntensity) mImageView).setSwipeGestureEnabled(getIntensityIsManaged() && getIntensitySliderEnabled());
        }

        private IntensityNativeFilter initFilter(TrayColumns.TrayCursorWrapper item, int position, float intensity) {
            final IntensityNativeFilter tempFilter;

            try {
                tempFilter = (IntensityNativeFilter) loadNativeFilter(item, position, true, intensity);
            } catch (Throwable t) {
                t.printStackTrace();
                return null;
            }

            if (null == tempFilter) {
                return null;
            }

            if (tempFilter instanceof BorderFilter) {
                ((BorderFilter) tempFilter).setHiRes(false);
            }

            try {
                mMoaMainExecutor = tempFilter.prepare(mBitmap, mPreview, 1, 1);
            } catch (JSONException e) {
                e.printStackTrace();
                mMoaMainExecutor = null;
                return null;
            }
            return tempFilter;
        }

        @Override
        public Bitmap doInBackground(final TrayColumns.TrayCursorWrapper... params) {

            if (isCancelled()) {
                return null;
            }

            final TrayColumns.TrayCursorWrapper item = params[0];
            currentEffect = item;

            filter = initFilter(item, mPosition, intensity);

            if (null == filter) {
                currentEffect = null;
                return null;
            }

            mIsRendering = true;

            if (isCancelled()) {
                return null;
            }

            // rendering the full preview
            try {
                mMoaMainExecutor.execute();
            } catch (Exception exception) {
                mError = exception.getMessage();
                exception.printStackTrace();
                return null;
            }

            if (!isCancelled()) {
                return mMoaMainExecutor.outputBitmap;
            } else {
                return null;
            }
        }

        @Override
        public void doPostExecute(final Bitmap result) {

            mFilter = filter;
            mIsAnimating = false;

            if (!isActive()) {
                return;
            }

            mPreview = result;

            mRenderedEffect = currentEffect;

            if (result == null || mMoaMainExecutor == null || mMoaMainExecutor.active == 0) {
                onRestoreOriginalBitmap();

                if (mError != null) {
                    onGenericError(mError, android.R.string.ok, null);
                }
                setIsChanged(false);
            } else {
                onApplyNewBitmap(result);

                if (null != mRenderedEffect) {
                    HashMap<String, String> attrs = new HashMap<String, String>();
                    attrs.put("pack", mRenderedEffect.getPackageName());
                    attrs.put("item", mRenderedEffect.getIdentifier());
                    attrs.put("intensity_adjusted", String.valueOf(mFilter.getIntensity()));
                    getContext().getTracker()
                        .tagEventAttributes(getName().name().toLowerCase(Locale.US) + ": item_previewed", attrs);

                    // filling the edit result
                    ToolActionVO<String> toolAction = new ToolActionVO<String>();
                    toolAction.setPackIdentifier(mRenderedEffect.getPackageName());
                    toolAction.setContentIdentifier(mRenderedEffect.getIdentifier());

                    mEditResult.setActionList(mFilter.getActions());
                    mEditResult.setToolAction(toolAction);

                    mTrackingAttributes.put("item", mRenderedEffect.getIdentifier());
                    mTrackingAttributes.put("pack", mRenderedEffect.getPackageName());
                } else {
                    mEditResult.setToolAction(null);
                    mEditResult.setActionList(null);

                    mTrackingAttributes.remove("item");
                    mTrackingAttributes.remove("pack");
                }
            }

            if (mPackType != PackType.FRAME) {
                onProgressEnd();
            }

            mIsRendering = false;
            mCurrentTask = null;
        }

        protected void onApplyNewBitmap(final Bitmap result) {
            if (getIntensityIsManaged() || !getIntensitySliderEnabled()) {
                mImageView.postInvalidate();
            } else {
                ((ImageViewWithIntensity) mImageView).setPreviewBitmap(result, mInitialIntensityValue);
            }

            ((ImageViewWithIntensity) mImageView).setSwipeGestureEnabled(getIntensitySliderEnabled());

            setIsChanged(mRenderedEffect != null);

            if (mSliderIntensityTooltip++ == 0 && getIntensitySliderEnabled()) {

                final ImageViewWithIntensity image = ((ImageViewWithIntensity) mImageView);
                PointF pointf = image.playDemo();

                mTooltipManager.create(0)
                    .actionBarSize(((FeatherContext) getContext().getBaseContext()).getActionBarSize())
                    .anchor(new Point((int) (pointf.x - image.getTooltipSize() * ANCHOR_X_OFFSET),
                                      (int) pointf.y + (image.getTooltipSize() / 2)), TooltipManager.Gravity.LEFT)
                    .text(getContext().getBaseContext().getResources(), R.string.feather_effect_intensity_tooltip)
                    .closePolicy(TooltipManager.ClosePolicy.None, 2500)
                    .maxWidth((int) (image.getWidth() / ANCHOR_MAX_WIDTH_RATIO))
                    .showDelay(100)
                    .withStyleId(R.style.AviaryPanelsTooltip)
                    .toggleArrow(false)
                    .withCustomView(R.layout.aviary_effect_intensity_tooltip, false)
                    .show();

                if (null != mPreferenceService) {
                    mPreferenceService.containsSingleTimeKey(((Object) BordersPanel.this).getClass(),
                                                             "intensity.slider.tooltip",
                                                             true);
                }
            }
        }

        protected void onRestoreOriginalBitmap() {
            // restore the original bitmap...
            mLogger.info("onRestoreOriginalBitmap");

            mPreview = BitmapUtils.copy(mBitmap, Config.ARGB_8888);

            if (getIntensitySliderEnabled()) {
                if (getIntensityIsManaged()) {
                    mImageView.setImageBitmap(mPreview, null, 1, 1);
                } else {
                    ((ImageViewWithIntensity) mImageView).setPreviewBitmap(mPreview, mInitialIntensityValue);
                }
                ((ImageViewWithIntensity) mImageView).setIntensity(mInitialIntensityValue);
                ((ImageViewWithIntensity) mImageView).setSwipeGestureEnabled(false);
            } else {
                mImageView.setImageBitmap(mPreview, null, 1, 1);
            }

            // onPreviewChanged( mBitmap, true, true );
            setIsChanged(false);
        }

        @Override
        public void onCancelled() {
            super.onCancelled();

            if (mMoaMainExecutor != null) {
                mMoaMainExecutor.cancel();
            }
            mIsRendering = false;
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            cancel(true);
        }
    }

    /**
     * Used to generate the Bitmap result. If user clicks on the "Apply" button when an
     * effect is still rendering, then starts this
     * task.
     */
    class GenerateResultTask extends AviaryAsyncTask<Void, Void, Void> {
        ProgressDialog mProgress = new ProgressDialog(getContext().getBaseContext());

        @Override
        protected void doPreExecute() {
            mProgress.setTitle(getContext().getBaseContext().getString(R.string.feather_loading_title));
            mProgress.setMessage(getContext().getBaseContext().getString(R.string.feather_effect_loading_message));
            mProgress.setIndeterminate(true);
            mProgress.setCancelable(false);
            mProgress.show();
        }

        @Override
        protected Void doInBackground(Void... params) {

            mLogger.info("GenerateResultTask::doInBackground", mIsRendering);

            while (mIsRendering) {
                mLogger.log("waiting....");
            }

            return null;
        }

        @Override
        protected void doPostExecute(Void result) {
            if (getContext().getBaseActivity().isFinishing()) {
                return;
            }
            if (mProgress.isShowing()) {
                mProgress.dismiss();
            }
            onGenerateFinalBitmap();
        }
    }

}
