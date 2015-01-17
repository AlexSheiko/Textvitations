package com.aviary.android.feather.sdk.widget;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.widget.CursorAdapter;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ViewAnimator;

import com.aviary.android.feather.cds.AviaryCds;
import com.aviary.android.feather.cds.AviaryCds.ContentType;
import com.aviary.android.feather.cds.AviaryCds.PackType;
import com.aviary.android.feather.cds.AviaryCdsDownloaderFactory;
import com.aviary.android.feather.cds.AviaryCdsValidatorFactory;
import com.aviary.android.feather.cds.AviaryCdsValidatorFactory.Validator;
import com.aviary.android.feather.cds.CdsUtils;
import com.aviary.android.feather.cds.CdsUtils.PackOption;
import com.aviary.android.feather.cds.IAPInstance;
import com.aviary.android.feather.cds.PacksColumns;
import com.aviary.android.feather.cds.PacksItemsColumns;
import com.aviary.android.feather.cds.billing.util.IabException;
import com.aviary.android.feather.cds.billing.util.IabHelper;
import com.aviary.android.feather.cds.billing.util.IabResult;
import com.aviary.android.feather.cds.billing.util.Inventory;
import com.aviary.android.feather.cds.billing.util.Purchase;
import com.aviary.android.feather.common.log.LoggerFactory;
import com.aviary.android.feather.common.log.LoggerFactory.Logger;
import com.aviary.android.feather.common.log.LoggerFactory.LoggerType;
import com.aviary.android.feather.common.utils.SystemUtils;
import com.aviary.android.feather.common.utils.os.AviaryAsyncTask;
import com.aviary.android.feather.sdk.R;
import com.aviary.android.feather.sdk.graphics.CdsPreviewTransformer;
import com.aviary.android.feather.sdk.utils.CdsUIUtils;
import com.aviary.android.feather.sdk.utils.PackIconStoreTransformation;
import com.nineoldandroids.view.ViewHelper;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import it.sephiroth.android.library.picasso.Callback;
import it.sephiroth.android.library.picasso.Picasso;
import it.sephiroth.android.library.picasso.RequestCreator;
import it.sephiroth.android.library.picasso.Transformation;
import it.sephiroth.android.library.widget.HListView;

public abstract class PackDetailLayout extends RelativeLayout implements OnClickListener, IabHelper.OnIabSetupFinishedListener {
    protected static Logger logger = LoggerFactory.getLogger("PackDetailLayout", LoggerType.ConsoleLoggerType);
    protected Picasso            mPicasso;
    protected int                mDelay;
    private   PreviewListAdapter mPreviewListAdapter;
    private   int                mPreviewHeight, mPreviewWidth, mMarginpx;
    private boolean                        mIsTablet;
    private boolean                        mAttached;
    private int                            mPreviewDefaultDivider;
    private long                           mPackId;
    private PacksColumns.PackCursorWrapper mPack;
    private boolean                        mDataIsAnimating;
    private ViewAnimator                   mDetailBanner;
    private AviaryTextView                 mTitle;
    private AviaryTextView                 mAuthor;
    private AviaryTextView                 mDescription;
    private ImageView                      mDetailImageView;
    private HListView                      mHListView;
    private AviaryTextView                 mDetailBannerText;
    private ImageView                      mDetailBannerIcon;
    private View                           mContent;
    private IAPBuyButton                   mBuyButton;
    private View                           mErrorView;
    private ProgressBar                    mPreviewProgress;
    private View                           mPreviewError;
    private View                           mHListViewContainer;
    private View                           mHeadView;
    // keep track if featured pack was clicked, we want to report its position if the pack is purchased
    private int mClickedFromPosition = -1;

    public PackDetailLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    protected void initialize(Context context) {
        mPicasso = Picasso.with(context);

        final Resources res = getResources();
        mIsTablet = res.getBoolean(R.bool.aviary_is_tablet);
        mMarginpx = res.getDimensionPixelSize(R.dimen.aviary_preview_margins);
        mPreviewHeight = res.getDimensionPixelSize(R.dimen.aviary_iap_previews_list_height);
        mDelay = res.getInteger(R.integer.aviary_iap_animator_time) + 100;
    }

    @Override
    protected void onFinishInflate() {
        logger.info("onFinishInflate");
        super.onFinishInflate();

        mDetailBanner = (ViewAnimator) findViewById(R.id.aviary_detail_switcher);
        mTitle = (AviaryTextView) findViewById(R.id.aviary_title);
        mAuthor = (AviaryTextView) findViewById(R.id.author);
        mDescription = (AviaryTextView) findViewById(R.id.aviary_description);
        mDetailImageView = (ImageView) findViewById(R.id.feature_image);
        mHListView = (HListView) findViewById(R.id.aviary_list);
        mDetailBannerText = (AviaryTextView) findViewById(R.id.aviary_detail_background_text);
        mDetailBannerIcon = (ImageView) findViewById(R.id.aviary_detail_background_icon);
        mContent = findViewById(R.id.aviary_content);
        mBuyButton = (IAPBuyButton) findViewById(R.id.aviary_buy_button);
        mErrorView = findViewById(R.id.aviary_error_message);
        mPreviewProgress = (ProgressBar) findViewById(R.id.aviary_progress2);
        mPreviewError = findViewById(R.id.aviary_error_previews);
        mHListViewContainer = findViewById(R.id.list_container);
        mHeadView = findViewById(R.id.aviary_head);
        mPreviewDefaultDivider = mHListView.getDividerWidth();
    }

    @Override
    protected void onAttachedToWindow() {
        logger.info("onAttachedToWindow (packid: %d)", mPackId);
        super.onAttachedToWindow();
        mAttached = true;

        mPreviewListAdapter = new PreviewListAdapter(getContext(), null, null);
        mHListView.setAdapter(mPreviewListAdapter);

        mBuyButton.setOnClickListener(this);

        if (null != mErrorView) {
            mErrorView.setOnClickListener(this);
        }

        if (null != mPreviewError) {
            mPreviewError.setOnClickListener(this);
        }

        if (null == mPack && mPackId > 0 && isValidContext()) {
            logger.verbose("ok, attached");
            resetView();
            setPackContent(CdsUtils.getPackFullInfoById(getContext(), mPackId));
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        logger.info("onDetachedFromWindow");

        mAttached = false;
        mBuyButton.setOnClickListener(null);
        mHListView.setTag(null);
        mPreviewListAdapter.changeCursor(null, null);
        mHListView.setAdapter(null);

        if (null != mPreviewError) {
            mPreviewError.setOnClickListener(null);
        }

        super.onDetachedFromWindow();
    }

    public abstract boolean isValidContext();

    private void resetView() {
        logger.info("resetView");

        setPackOption(new CdsUtils.PackOptionWithPrice(PackOption.PACK_OPTION_BEING_DETERMINED), -1);
        mTitle.setText("");
        mDescription.setText("");
        mAuthor.setText("");
        mPreviewListAdapter.changeCursor(null, null);
        mHListView.setTag(null);

        mDetailBanner.setInAnimation(null);
        mDetailBanner.setOutAnimation(null);
        mDetailBanner.setDisplayedChild(0);
        mDetailImageView.setTag(null);
        mDetailImageView.setImageBitmap(null);
        mDetailBannerIcon.setImageBitmap(null);
        mDetailBannerText.setText("");
        mPicasso.cancelRequest(mDetailImageView);
    }

    /**
     * Display the informations of the new pack
     *
     * @param pack
     */
    private void setPackContent(PacksColumns.PackCursorWrapper pack) {
        logger.info("setPackContent: %s", pack);

        if (!isValidContext() || getPackId() < 0) {
            return;
        }

        if (null == pack || null == pack.getContent()) {
            logger.error("pack or pack.content are null!");
            onPackMissingError();
            return;
        }

        mErrorView.setVisibility(View.GONE);
        mContent.setVisibility(View.VISIBLE);
        mPreviewError.setVisibility(View.INVISIBLE);

        mPack = (PacksColumns.PackCursorWrapper) pack.clone();
        mPackId = pack.getId();

        mTitle.setText(mPack.getContent().getDisplayName());
        mTitle.setSelected(true);
        mDescription.setText(
            TextUtils.isEmpty(mPack.getContent().getDisplayDescription()) ? "" : mPack.getContent().getDisplayDescription());

        mAuthor.setText(TextUtils.isEmpty(mPack.getContent().getAuthor()) ? "" : mPack.getContent().getAuthor());

        // start loading the previews
        loadPreviews(mPack);

        // check if the detail image exists locally
        if (!loadDetailImageIfLocal(mPack)) {
            loadTempDetailImage(mPack);
        }

        // set the previews background color
        String hexColor = mPack.getContent().getShopBackgroundColor();
        if (!TextUtils.isEmpty(hexColor)) {
            mHListViewContainer.setBackgroundColor(Color.parseColor(hexColor));
        } else {
            mHListViewContainer.setBackgroundDrawable(null);
        }

        // update workspace
        mPreviewListAdapter.setFileExt(AviaryCds.getPreviewItemExt(mPack.getPackType()));
        mPreviewListAdapter.setBaseDir(null);

        if (null != mHeadView) {
            mHeadView.requestFocus();
            mHeadView.requestFocusFromTouch();
        }

        // start the iap-setup for this content
        AviaryStoreWrapperAbstract wrapper = getStoreWrapper();
        if (null != wrapper && wrapper.isActive()) {
            if (wrapper.isSetupDone()) {
                onIabSetupFinished(null);
            } else {
                wrapper.startSetup(true, this);
            }
        }
        onSetPackContentCompleted(mPack);
    }

    /**
     * Update the buy button status
     *
     * @param option
     * @param packId
     */
    void setPackOption(CdsUtils.PackOptionWithPrice option, long packId) {
        logger.info("setPackOption: %s", option);
        if (null != mBuyButton) {
            mBuyButton.setPackOption(option, packId);
        }
    }

    public final long getPackId() {
        return mPackId;
    }

    /**
     * Error downloading plugin informations
     */
    private void onPackMissingError() {
        logger.info("onPackMissingError");

        mContent.setVisibility(View.INVISIBLE);
        mErrorView.setVisibility(View.VISIBLE);

        mPreviewListAdapter.changeCursor(null, null);
        mHListView.setTag(null);
    }

    private void loadPreviews(PacksColumns.PackCursorWrapper pack) {
        if (pack.getIdentifier().equals(mHListView.getTag())) {
            logger.warn("ok, don't reload the workspace, same tag found");
            downloadDetailImage(pack);
            return;
        }

        LoadPreviewsAsyncTask task = new LoadPreviewsAsyncTask(this, pack, mDataIsAnimating ? mDelay + 100 : 0);
        task.executeInParallel(getContext());
    }

    private boolean loadDetailImageIfLocal(PacksColumns.PackCursorWrapper pack) {
        logger.info("loadDetailImageIfLocal");
        String detailImagePath = pack.getContent().getDetailImageLocalPath();
        logger.verbose("detailImagePath: %s", detailImagePath);
        if (!TextUtils.isEmpty(detailImagePath)) {
            File file = new File(detailImagePath);
            if (file.exists()) {
                // ok, load the image
                RequestCreator request = mPicasso.load(detailImagePath);
                request.noFade();

                if (mDataIsAnimating) {
                    request.withDelay(mDelay + 100);
                }

                mDetailImageView.setTag(pack.getIdentifier());

                request.config(Bitmap.Config.RGB_565).fit(true).skipMemoryCache().into(mDetailImageView, new Callback() {
                    @Override
                    public void onSuccess() {
                        if (null != getContext()) {
                            if (mDetailBanner.getDisplayedChild() == 0) {
                                logger.verbose("detail image loaded " + "from local path");
                                mDetailBanner.setInAnimation(getContext(), R.anim.aviary_detail_banner_fade_in);
                                mDetailBanner.setOutAnimation(getContext(), R.anim.aviary_detail_banner_fade_out);
                                mDetailBanner.setDisplayedChild(2);
                            }
                        }
                    }

                    @Override
                    public void onError() {
                        logger.error("failed to load local detail " + "image");
                    }
                });

                return true;
            }
        }
        return false;
    }

    private void loadTempDetailImage(PacksColumns.PackCursorWrapper pack) {
        logger.info("loadTempDetailImage");
        if (!isValidContext()) {
            return;
        }

        PackType packType = PackType.fromString(pack.getPackType());
        String packTypeString = CdsUIUtils.getPackTypeString(getContext(), packType);
        mDetailBannerText.setText(pack.getContent().getDisplayName() + " " + packTypeString);

        RequestCreator request = mPicasso.load(pack.getContent().getIconPath());
        request.noFade();
        request.skipMemoryCache();
        request.fit(true);

        try {
            Transformation transformation = new PackIconStoreTransformation.Builder().withPackType(pack.getPackType())
                .withIdentifier(pack.getIdentifier())
                .withResources(getContext().getResources())
                .build();
            request.transform(transformation);

        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }

        request.into(mDetailBannerIcon, new Callback() {
            @Override
            public void onSuccess() {
                if (mDetailBanner.getDisplayedChild() == 0) {
                    logger.verbose("loadTempDetailImage:onSuccess");
                    mDetailBanner.setInAnimation(null);
                    mDetailBanner.setOutAnimation(null);
                    mDetailBanner.setDisplayedChild(1);
                }
            }

            @Override
            public void onError() {

            }
        });

    }

    protected abstract AviaryStoreWrapperAbstract getStoreWrapper();

    @Override
    public void onIabSetupFinished(final IabResult result) {
        logger.info("onIabSetupFinished: %s", result);

        if (isValidContext() && null != getPack()) {
            determinePackOption(getPackId(), getInventory());
        }
    }

    // --------------------------
    // Store Wrapper methods
    // --------------------------

    protected abstract void onSetPackContentCompleted(PacksColumns.PackCursorWrapper pack);

    /**
     * Download the remote detail image
     *
     * @param pack
     */
    void downloadDetailImage(final PacksColumns.PackCursorWrapper pack) {
        logger.info("downloadDetailImage: %s", pack.getContent().getDetailImageURL());

        if (null != pack && null != pack.getContent() && isValidContext() && pack.getId() == getPackId()) {
            if (mDetailBanner.getDisplayedChild() == 2 || pack.getIdentifier().equals(mDetailImageView.getTag())) {
                logger.warn("detail image already loaded! skipping..");
                return;
            }

            mDetailImageView.setTag(pack.getIdentifier());
            long delay = mDetailBanner.getDisplayedChild() == 0 ? 100 : 1000;
            new DetailImageDownloadAsyncTask(pack.getId(), delay).executeInParallel(getContext());
        }
    }

    protected final PacksColumns.PackCursorWrapper getPack() {
        return mPack;
    }

    /**
     * This task must be executed on the single executor
     *
     * @param packId
     * @param inventory
     */
    private void determinePackOption(final long packId, Inventory inventory) {
        new DeterminePackOptionAsyncTask(packId).execute(inventory);
    }

    public abstract Inventory getInventory();

    public abstract void setInventory(final Inventory inventory);

    public boolean isAttached() {
        return mAttached;
    }

    protected abstract boolean isChildVisible(final PackDetailLayout packDetailLayout);

    protected void update(long packId, boolean isAnimating, Bundle extras) {

        int clickedFromPosition = -1;
        if (extras != null) {
            clickedFromPosition = extras.getInt(IAPDialogDetail.EXTRA_CLICK_FROM_POSITION, -1);
        }

        logger.info("(update: %d, %b)", packId, isAnimating);
        mDataIsAnimating = isAnimating;
        mPackId = packId;
        mPack = null;
        mClickedFromPosition = clickedFromPosition;

        if (isValidContext()) {
            logger.verbose("ok attached!");
            resetView();
            setPackContent(CdsUtils.getPackFullInfoById(getContext(), mPackId));
        }
    }

    public void onDownloadStatusChanged(long packId, String packType, int status) {
        if (isValidContext() && null != getPack()) {
            logger.info("onDownloadStatusChanged: %d, %s, %d", packId, packType, status);
            if (packId == getPackId()) {
                determinePackOption(getPackId(), getInventory());
            }
        }
    }

    public void onPackInstalled(final long packId, final String packType, final int purchased) {
        if (isValidContext() && null != getPack() && packId == getPackId()) {
            logger.info("onPackInstalled: %d, %s, %d", packId, packType, purchased);
            determinePackOption(getPackId(), getInventory());
        }
    }

    public void onPurchaseSuccess(final long packId, final String packType, final Purchase purchase) {
        if (isValidContext() && null != getPack() && packId == getPackId()) {
            logger.info("onPurchaseSuccess: %d - %s", packId, packType);
            determinePackOption(getPackId(), getInventory());
        }
    }

    public void onSubscriptionPurchased(final String identifier, final int purchased) {
        if (isValidContext() && null != getPack()) {
            logger.info("onSubscriptionPurchased: %s, %d", identifier, purchased);
            determinePackOption(getPackId(), getInventory());
        }
    }

    public void onServiceFinished() {
        if (!isValidContext() || getPack() == null) {
            return;
        }
        logger.info("onServiceFinished");
    }

    /**
     * Completed downloading the preview images
     */
    private void onDownloadPreviewCompleted() {
        logger.info("onDownloadPreviewCompleted");
        mPreviewProgress.setVisibility(View.INVISIBLE);
        mPreviewError.setVisibility(View.INVISIBLE);
    }

    public void onLoadPreviewsCompleted(
        final int errorCode, final String previewPath, final PacksColumns.PackCursorWrapper pack) {
        logger.info("onLoadPreviewsCompleted(%d, %s)", errorCode, previewPath);

        if (!isValidContext() || null == getPack() || null == pack || getPackId() != pack.getId()) {
            return;
        }

        if (null != previewPath) {
            initWorkspace(pack, previewPath);
            downloadDetailImage(pack);
        } else {
            if (errorCode == LoadPreviewsAsyncTask.STATUS_ERROR) {
                onDownloadPreviewError();
                downloadDetailImage(pack);
            } else {
                new PreviewDownloadAsyncTask(pack).executeInParallel(getContext());
            }
        }
    }

    private void initWorkspace(PacksColumns.PackCursorWrapper pack, final String previewPath) {
        if (null != pack && isValidContext()) {

            if (pack.getIdentifier().equals(mHListView.getTag())) {
                logger.warn("ok, don't reload the workspace, same tag found");
                return;
            }

            MeasurePreviewTask task = new MeasurePreviewTask(pack, previewPath);
            task.executeInParallel(getContext());
        } else {
            logger.error("invalid plugin");
            mPreviewListAdapter.changeCursor(null, null);
            mHListView.setTag(null);
        }
    }

    /**
     * Failed to download the pack's previews
     */
    private void onDownloadPreviewError() {
        logger.info("onDownloadPreviewError");
        mPreviewProgress.setVisibility(View.INVISIBLE);
        mPreviewError.setVisibility(View.VISIBLE);
        mPreviewListAdapter.changeCursor(null, null);
        mHListView.setTag(null);
    }

    public void onLoadPreviewsStarted() {
        onDownloadPreviewStarted(false);
    }

    // ---------------
    // CALLBACKS
    // ---------------

    /**
     * Started downloading the preview images
     */
    private void onDownloadPreviewStarted(boolean showProgress) {
        logger.info("onDownloadPreviewStarted");
        if (showProgress) {
            mPreviewProgress.setVisibility(View.VISIBLE);
        }
        mPreviewError.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onClick(View v) {

        final int id = v.getId();

        if (id == mErrorView.getId()) {
            onForceUpdate();
        } else if (id == mPreviewError.getId()) {
            new PreviewDownloadAsyncTask(getPack()).executeInParallel(getContext());
        } else if (id == mBuyButton.getId()) {

            IAPBuyButton button = (IAPBuyButton) v;

            CdsUtils.PackOptionWithPrice option = button.getPackOption();
            if (null == option) {
                return;
            }

            switch (option.option) {

                case PURCHASE:
                    Log.d("PackDetails", "clicked from position: " + mClickedFromPosition);
                    getStoreWrapper().purchase(mPack.getId(),
                                               mPack.getIdentifier(),
                                               mPack.getPackType(),
                                               "shop_detail",
                                               option.price,
                                               mClickedFromPosition);
                    break;

                case INSTALL:
                case FREE:
                case RESTORE:
                case DOWNLOAD_ERROR:
                    final boolean isFree = option.option == CdsUtils.PackOption.FREE;
                    final boolean isRestore = option.option == CdsUtils.PackOption.RESTORE;
                    final boolean isError = option.option == CdsUtils.PackOption.DOWNLOAD_ERROR;
                    final boolean isSubscription = option.option == PackOption.INSTALL;

                    getStoreWrapper().restore(mPack.getId(),
                                              mPack.getIdentifier(),
                                              mPack.getPackType(),
                                              "shop_detail",
                                              isRestore,
                                              isFree,
                                              isError,
                                              isSubscription);
                    break;

                case ERROR:
                    setPackOption(new CdsUtils.PackOptionWithPrice(CdsUtils.PackOption.PACK_OPTION_BEING_DETERMINED), getPackId());
                    getStoreWrapper().startSetup(true, this);
                    break;

                case OWNED:
                case PACK_OPTION_BEING_DETERMINED:
                case DOWNLOADING:
                case DOWNLOAD_COMPLETE:
                    logger.log("Do nothing here");
                    break;

                default:
                    /* invalid case */
                    break;
            }
        }
    }

    protected abstract void onForceUpdate();

    /**
     * Base Adapter for the pack's previews
     */
    static class PreviewListAdapter extends CursorAdapter {
        LayoutInflater mLayoutInflater;
        String         mBaseDir;
        String         mFileExt;
        int            mTargetDensity;
        int            columnIndexDisplayName;
        int            columnIndexIdentifier;
        private int mPreviewWidth  = 100;
        private int mPreviewHeight = 100;
        private int mMarginpx      = 0;
        private Picasso picasso;
        private String  packType;

        public PreviewListAdapter(Context context, String baseDir, Cursor cursor) {
            super(context, cursor, false);
            picasso = Picasso.with(context);
            mLayoutInflater = LayoutInflater.from(context);
            mBaseDir = baseDir;
            mTargetDensity = context.getResources().getDisplayMetrics().densityDpi;
            initCursor(cursor);
        }

        private void initCursor(Cursor cursor) {
            logger.info("initCursor");
            if (null != cursor) {
                columnIndexDisplayName = cursor.getColumnIndex(PacksItemsColumns.DISPLAY_NAME);
                columnIndexIdentifier = cursor.getColumnIndex(PacksItemsColumns.IDENTIFIER);
            }
        }

        public void setFileExt(String fileExt) {
            mFileExt = fileExt;
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            ImageView imageView = new ImageView(context);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(mPreviewWidth, mPreviewHeight);
            imageView.setLayoutParams(params);

            if (!AviaryCds.PACKTYPE_EFFECT.equals(packType)) {
                imageView.setPadding(mMarginpx, 0, mMarginpx, 0);
            } else {
                imageView.setPadding(0, 0, 0, 0);
            }

            imageView.setTag(null);
            return imageView;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ImageView imageView = (ImageView) view;
            loadImage(cursor.getPosition(), imageView);
        }

        @Override
        public Cursor swapCursor(Cursor newCursor) {
            initCursor(newCursor);
            recycleBitmaps();
            return super.swapCursor(newCursor);
        }

        private void recycleBitmaps() {
            logger.info("recycleBitmaps. Not implemented");
        }

        public void loadImage(int position, final ImageView imageView) {
            Cursor cursor = (Cursor) getItem(position);

            if (null != cursor && !cursor.isAfterLast() && null != imageView && columnIndexDisplayName > -1
                && columnIndexIdentifier > -1) {
                String identifier = cursor.getString(columnIndexIdentifier);
                String displayName = cursor.getString(columnIndexDisplayName);
                String type = PackType.fromString(packType).toCdsString();

                File file = new File(getBaseDir(), identifier + (mFileExt));
                final String path = file.getAbsolutePath();
                final int imageTag = path.hashCode();

                final Integer tag = (Integer) imageView.getTag();
                final boolean same = (tag != null && tag.equals(imageTag));

                if (!same) {
                    if (imageView.getDrawable() != null) {
                        imageView.setImageBitmap(null);
                    }
                    picasso.cancelRequest(imageView);

                    RequestCreator request = picasso.load(path);

                    request.fit()
                        .error(R.drawable.aviary_store_placeholder)
                        .transform(new CdsPreviewTransformer(path, displayName, type))
                        .into(imageView, new Callback() {
                            @Override
                            public void onSuccess() {
                                imageView.setTag(Integer.valueOf(imageTag));
                            }

                            public void onError() {}
                        });
                }
            }
        }

        public String getBaseDir() {
            return mBaseDir;
        }

        public void setBaseDir(String dir) {
            mBaseDir = dir;
        }

        public void setPreviewSize(final int width, final int height) {
            mPreviewWidth = width;
            mPreviewHeight = height;
        }

        public void setPreviewMargin(final int marginpx) {
            mMarginpx = marginpx;
        }

        public void changeCursor(final Cursor o, final String packType) {
            this.packType = packType;
            super.changeCursor(o);
        }
    }

    /**
     * Tries to load the previews locally
     */
    static class LoadPreviewsAsyncTask extends AviaryAsyncTask<Context, Void, String> {
        /** no error */
        public static final int STATUS_OK              = 0;
        /** must re-download the previews */
        public static final int STATUS_REMOTE_DOWNLOAD = 1;
        /** error occurred */
        public static final int STATUS_ERROR           = 2;
        private final long                           packId;
        private final PacksColumns.PackCursorWrapper pack;
        private final long                           delay;
        private final PackDetailLayout               callback;
        private       int                            status;

        LoadPreviewsAsyncTask(PackDetailLayout callback, PacksColumns.PackCursorWrapper pack, long delay) {
            this.delay = delay;
            this.pack = pack;
            this.packId = pack.getId();
            this.callback = callback;
        }

        @Override
        protected void doPostExecute(final String previewPath) {
            logger.info("LoadPreviewsAsyncTask::doPostExecute: %s, %d", previewPath, status);

            if (null != callback) {
                callback.onLoadPreviewsCompleted(status, previewPath, pack);
            }
        }

        @Override
        protected String doInBackground(final Context... params) {
            logger.info("LoadPreviewsAsyncTask::doInBackground");

            final Context context = params[0];

            if (!callback.isValidContext() || null == context || packId != callback.getPackId()) {
                return null;
            }

            if (delay > 0) {
                SystemUtils.trySleep(delay);
            }

            final PacksColumns.PackCursorWrapper newPack = CdsUtils.getPackFullInfoById(context, packId);

            if (null == newPack || null == newPack.getContent()) {
                status = STATUS_ERROR;
                return null;
            }

            final String previewPath = newPack.getContent().getPreviewPath();

            if (!TextUtils.isEmpty(previewPath)) {
                File file = new File(previewPath);
                Validator validator =
                    AviaryCdsValidatorFactory.create(ContentType.PREVIEW, PackType.fromString(newPack.getPackType()));

                try {
                    validator.validate(context, newPack.getContent().getId(), file, false);
                    return newPack.getContent().getPreviewPath();
                } catch (Throwable e) {
                    status = STATUS_REMOTE_DOWNLOAD;
                }
            } else {
                status = STATUS_REMOTE_DOWNLOAD;
            }
            return null;
        }

        @Override
        protected void doPreExecute() {
            if (null != callback) {
                callback.onLoadPreviewsStarted();
            }
        }

    }

    /**
     * Download the detail image in background and save in the local cache
     */
    class DetailImageDownloadAsyncTask extends AviaryAsyncTask<Context, Void, String> {
        private final long packId;
        private final long delay;

        DetailImageDownloadAsyncTask(long packId, long delay) {
            this.delay = delay;
            this.packId = packId;
        }

        @Override
        protected void doPostExecute(final String localPath) {
            logger.info("DetailImageDownloadAsyncTask::doPostExecute");

            if (!isValidContext() || null == getPack() || null == mPicasso || packId != getPackId()) {
                logger.warn("isValidContext: %b", isValidContext());
                logger.warn("pack != null: %b", getPack() != null);
                logger.warn("picasso != null: %b", mPicasso != null);
                logger.warn("packId != mPackId: %d/%d", packId, getPackId());
                return;
            }

            RequestCreator request = mPicasso.load(localPath);
            request.noFade();

            request.config(Bitmap.Config.RGB_565).fit(true).skipMemoryCache().into(mDetailImageView, new Callback() {
                @Override
                public void onSuccess() {
                    if (isValidContext() && packId == getPackId()) {
                        if (mDetailBanner.getDisplayedChild() == 1) {
                            if (isChildVisible(PackDetailLayout.this)) {
                                mDetailBanner.setInAnimation(getContext(), R.anim.aviary_detail_banner_fade_in);
                                mDetailBanner.setOutAnimation(getContext(), R.anim.aviary_detail_banner_fade_out);
                            } else {
                                logger.warn("downloadDetailImage, skip animation..");
                                mDetailBanner.setInAnimation(null);
                                mDetailBanner.setOutAnimation(null);
                            }
                            mDetailBanner.setDisplayedChild(2);
                        }
                    }
                }

                @Override
                public void onError() {
                    logger.warn("onError");
                }
            });
        }

        @Override
        protected void doPreExecute() {
        }

        @Override
        protected String doInBackground(final Context... params) {
            logger.info("DetailImageDownloadAsyncTask::doInBackground");

            final Context context = params[0];

            if (!isValidContext() || null == context || null == getPack()) {
                return null;
            }
            if (packId != getPackId()) {
                return null;
            }

            if (delay > 0) {
                SystemUtils.trySleep(delay);
            }

            AviaryCdsDownloaderFactory.Downloader downloader = AviaryCdsDownloaderFactory.create(ContentType.DETAIL_IMAGE);
            try {
                return downloader.download(context, packId);
            } catch (Throwable e) {
                e.printStackTrace();
                return null;
            }
        }
    }

    /**
     * Download the pack previews
     */
    class PreviewDownloadAsyncTask extends AviaryAsyncTask<Context, Void, String> {
        private final long                           packId;
        private final PacksColumns.PackCursorWrapper pack;
        private       Throwable                      error;

        PreviewDownloadAsyncTask(final PacksColumns.PackCursorWrapper pack) {
            this.pack = pack;
            this.packId = pack.getId();
        }

        @Override
        protected String doInBackground(Context... params) {

            logger.log("PreviewDownloadAsyncTask::doInBackground");

            final Context context = params[0];
            if (!isValidContext() || null == getPack() || null == context || packId != getPackId()) {
                return null;
            }

            AviaryCdsDownloaderFactory.Downloader downloader = AviaryCdsDownloaderFactory.create(ContentType.PREVIEW);
            try {
                return downloader.download(context, packId);
            } catch (Throwable e) {
                e.printStackTrace();
                error = e;
                return null;
            }
        }

        @Override
        protected void doPostExecute(String previewsPath) {
            logger.log("PreviewDownloadAsyncTask::doPostExecute: %s", previewsPath);

            if (isCancelled() || !isValidContext() || null == getPack() || packId != getPackId()) {
                return;
            }

            if (null != previewsPath) {
                initWorkspace(pack, previewsPath);
            }

            if (null != error) {
                onDownloadPreviewError();
            }

            // ok, previews downloaded, now download (if necessary) the detail
            // image
            downloadDetailImage(pack);
        }

        @Override
        protected void doPreExecute() {
            onDownloadPreviewStarted(true);
        }
    }

    /**
     * Measure the preview images in order to setup correctly the List adapter
     */
    private class MeasurePreviewTask extends AviaryAsyncTask<Context, Void, Cursor> {
        private final PacksColumns.PackCursorWrapper pack;
        private       String                         previewPath;
        private       double                         previewAspectRatio;

        MeasurePreviewTask(PacksColumns.PackCursorWrapper pack, String previewPath) {
            this.pack = pack;
            this.previewPath = previewPath;
            this.previewAspectRatio = 1;
        }

        @Override
        protected Cursor doInBackground(Context... params) {
            final Context context = params[0];
            if (context == null) {
                return null;
            }

            double[] out = new double[]{1};

            Cursor cursor = CdsUIUtils.computePreviewAspectRatio(context, pack.getId(), pack.getPackType(), previewPath, out);
            if (cursor != null && cursor.moveToFirst()) {
                previewAspectRatio = out[0];
            }

            return cursor;
        }

        @Override
        protected void doPostExecute(Cursor cursor) {

            if (null != cursor && getContext() != null) {
                mPreviewWidth = (int) (mPreviewHeight * previewAspectRatio);

                // restore default translationX
                ViewHelper.setTranslationX(mHListView, 0);
                mHListView.setDividerWidth(mPreviewDefaultDivider);

                // recalculate the dividerWidth for the HListView

                final DisplayMetrics metrics = getResources().getDisplayMetrics();
                if (!mIsTablet && null != cursor && cursor.getCount() > 0) {
                    int width = mHListView.getWidth();
                    logger.info("hlistview.getWidth: %d, screen.size: %d", width, metrics.widthPixels);
                    if (width > 0) {
                        final int cursorCount = cursor.getCount();
                        int dividerWidth = mHListView.getDividerWidth();
                        logger.info("preview.width: %d, divider.width: %d", mPreviewWidth, dividerWidth);
                        int totalWidth = (((mPreviewWidth * cursorCount) + (dividerWidth * cursorCount - 1)));
                        if (totalWidth < width) {
                            int newDividerWidth =
                                (metrics.widthPixels - (mPreviewWidth * cursorCount)) / Math.max(1, cursorCount + 1);

                            mHListView.setDividerWidth(newDividerWidth);

                            float translationX = Math.abs(newDividerWidth - dividerWidth);

                            logger.log("new dividerWidth: %d", newDividerWidth);
                            logger.log("translationX: %f", translationX);

                            ViewHelper.setTranslationX(mHListView, translationX);

                        }
                    }
                }

                mPreviewListAdapter.setPreviewSize(mPreviewWidth, mPreviewHeight);
                mPreviewListAdapter.setPreviewMargin(mMarginpx);
                mPreviewListAdapter.setBaseDir(previewPath);
                mPreviewListAdapter.changeCursor(cursor, pack.getPackType());

                mHListView.setTag(pack.getIdentifier());
                mHListView.setSelection(0);

                onDownloadPreviewCompleted();
            }
        }

        @Override
        protected void doPreExecute() {
        }
    }

    /**
     * Determine the pack price/options
     */
    class DeterminePackOptionAsyncTask extends AviaryAsyncTask<Inventory, Void, CdsUtils.PackOptionWithPrice> {
        long      packId;
        Inventory inventory;

        DeterminePackOptionAsyncTask(long packId) {
            this.packId = packId;
        }

        Inventory getInventory(final String identifier, IAPInstance store) throws IabException {
            List<String> array = Arrays.asList(new String[]{identifier});
            if (store.isAvailable()) {
                return store.queryInventory(true, array, null);
            }
            return null;
        }

        @Override
        protected void doPreExecute() {
        }

        @Override
        protected CdsUtils.PackOptionWithPrice doInBackground(final Inventory... params) {
            logger.info("DeterminePackOptionAsyncTask.doInBackground");

            if (!isValidContext()) {
                return null;
            }

            final Context context = getContext();
            inventory = params[0];

            AviaryStoreWrapperAbstract wrapper = getStoreWrapper();
            if (null == wrapper) {
                return null;
            }

            IAPInstance instance = wrapper.getIAPInstance();

            final PacksColumns.PackCursorWrapper pack = CdsUtils.getPackFullInfoById(context, packId);

            if (pack == null) {
                return null;
            }

            CdsUtils.PackOptionWithPrice downloadStatus = getPackDownloadStatus(context, pack);
            CdsUtils.PackOptionWithPrice optionStatus =
                new CdsUtils.PackOptionWithPrice(CdsUtils.getPackOption(context, pack), null);

            logger.log("downloadStatus: %s", downloadStatus);
            logger.log("optionsStatus: %s", optionStatus);

            if (null != downloadStatus) {
                // special case, download completed and pack is owned ( this means it's installed )
                if (downloadStatus.option == PackOption.DOWNLOAD_COMPLETE && PackOption.isInstalled(optionStatus.option)) {
                    return optionStatus;
                }
                return downloadStatus;
            }

            if (PackOption.isOwned(optionStatus.option) || PackOption.isFree(optionStatus.option)) {
                return optionStatus;
            }

            if (null != instance && instance.isSetupDone()) {
                if (null == inventory) {
                    try {
                        inventory = getInventory(pack.getIdentifier(), instance);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            if (null != inventory) {
                return wrapper.getPackOptionFromInventory(pack.getIdentifier(), inventory);
            }

            return null;
        }

        @Override
        protected void doPostExecute(CdsUtils.PackOptionWithPrice result) {
            logger.log("DeterminePackOptionAsyncTask::doPostExecute: %s", result);

            if (!isValidContext() || isCancelled()) {
                return;
            }
            if (null == getPack()) {
                return;
            }
            if (null == getStoreWrapper()) {
                return;
            }
            if (!getStoreWrapper().isActive()) {
                return;
            }
            if (getPackId() != packId) {
                return;
            }

            setInventory(this.inventory);

            if (null == result) {
                result = new CdsUtils.PackOptionWithPrice(PackOption.ERROR);
            }
            setPackOption(result, getPackId());
        }

        /**
         * Returns the pack download status<br />
         * Do not call this in the main thread!
         *
         * @param pack
         */
        private CdsUtils.PackOptionWithPrice getPackDownloadStatus(Context context, PacksColumns.PackCursorWrapper pack) {

            if (null == context) {
                return null;
            }

            CdsUtils.PackOptionWithPrice result = null;

            Pair<PackOption, String> pair = CdsUtils.getPackOptionDownloadStatus(context, pack.getId());
            if (null != pair) {
                result = new CdsUtils.PackOptionWithPrice(pair.first, null);
            }
            return result;
        }
    }
}
