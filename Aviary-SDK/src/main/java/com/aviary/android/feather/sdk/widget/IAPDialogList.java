package com.aviary.android.feather.sdk.widget;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MergeCursor;
import android.database.sqlite.SQLiteException;
import android.support.v4.app.NotificationCompat;
import android.support.v4.widget.CursorAdapter;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.aviary.android.feather.cds.AviaryCds;
import com.aviary.android.feather.cds.AviaryCds.PackType;
import com.aviary.android.feather.cds.CdsUtils;
import com.aviary.android.feather.cds.IAPInstance;
import com.aviary.android.feather.cds.PacksColumns;
import com.aviary.android.feather.cds.PacksContentColumns;
import com.aviary.android.feather.cds.RestoreAllHelper;
import com.aviary.android.feather.cds.billing.util.IabException;
import com.aviary.android.feather.cds.billing.util.IabHelper;
import com.aviary.android.feather.cds.billing.util.IabResult;
import com.aviary.android.feather.cds.billing.util.Inventory;
import com.aviary.android.feather.cds.billing.util.Purchase;
import com.aviary.android.feather.cds.billing.util.SkuDetails;
import com.aviary.android.feather.common.AviaryIntent;
import com.aviary.android.feather.common.log.LoggerFactory;
import com.aviary.android.feather.common.log.LoggerFactory.Logger;
import com.aviary.android.feather.common.tracking.AviaryTracker;
import com.aviary.android.feather.common.utils.ApiHelper;
import com.aviary.android.feather.common.utils.PackageManagerUtils;
import com.aviary.android.feather.common.utils.os.AviaryAsyncTask;
import com.aviary.android.feather.library.services.LocalDataService;
import com.aviary.android.feather.sdk.BuildConfig;
import com.aviary.android.feather.sdk.FeatherActivity;
import com.aviary.android.feather.sdk.R;
import com.aviary.android.feather.sdk.utils.CdsUIUtils;
import com.aviary.android.feather.sdk.utils.PackIconCallable;
import com.aviary.android.feather.sdk.widget.IAPDialogMain.IAPUpdater;
import com.nhaarman.listviewanimations.appearance.AnimationAdapter;
import com.nhaarman.listviewanimations.appearance.simple.AlphaInAnimationAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import it.sephiroth.android.library.picasso.Callback;
import it.sephiroth.android.library.picasso.Picasso;

public class IAPDialogList extends LinearLayout
    implements OnClickListener, OnItemClickListener, IabHelper.OnIabSetupFinishedListener {
    static Logger logger = LoggerFactory.getLogger("IAPDialogList");
    /** returns if dialog is currently attached to parent */
    private boolean    mAttached;
    /** current data provider */
    private IAPUpdater mData;
    BroadcastReceiver downloadMissingPacksCompletedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            logger.info("downloadMissingPacksCompletedReceiver");

            if (null != context && null != intent) {
                final String packType = intent.getStringExtra("packType");
                final int error = intent.getIntExtra("error", 0);

                if (!isValidContext()) {
                    return;
                }

                if (null != packType
                    && null != mData
                    && null != mData.getPackType()
                    && packType.equals(mData.getPackType().toCdsString())) {
                    // there's no need to requery the inventory etc..
                    runInventoryAsyncTask(error, mParent.mInventory);
                }
            }

        }
    };
    private CursorAdapter          mAdapter;
    private AnimationAdapter       mAnimationAdapter;
    private IAPDialogMain          mParent;
    private ListView               mList;
    private Button                 mRestoreAllButton;
    private View                   mListProgress;
    private View                   mErrorView;
    private View                   mRestoreAllView;
    private OnPackSelectedListener mPackSelectedListener;
    private Picasso                mPicasso;
    private LocalDataService       mDataService;
    private int                    mMissingPackRetryCount;

    public IAPDialogList(Context context, AttributeSet attrs) {
        super(context, attrs);
        mMissingPackRetryCount = 5;
    }

    @Override
    protected void onAttachedToWindow() {
        logger.info("onAttachedToWindow");

        super.onAttachedToWindow();

        if (isInEditMode()) {
            return;
        }

        final FeatherActivity activity = (FeatherActivity) getContext();

        mMissingPackRetryCount = 5;
        mAttached = true;

        mList = (ListView) findViewById(android.R.id.list);
        mRestoreAllButton = (Button) findViewById(R.id.aviary_restore_all_button);
        mRestoreAllButton.setOnClickListener(this);
        mRestoreAllView = findViewById(R.id.restore_all_container);
        mListProgress = findViewById(R.id.aviary_iap_list_progress);
        mErrorView = findViewById(R.id.aviary_error_message);

        // hide the "restore-all" container if it's an amazon device
        mRestoreAllView.setVisibility("amazon".equals(BuildConfig.SDK_FLAVOR) ? View.GONE : View.VISIBLE);

        mPicasso = Picasso.with(getContext());

        mDataService = activity.getMainController().getService(LocalDataService.class);

        activity.registerReceiver(downloadMissingPacksCompletedReceiver,
                                  new IntentFilter(
                                      activity.getPackageName() + CdsUtils.BROADCAST_DOWNLOAD_MISSING_PACKS_COMPLETED));

        mList.setOnItemClickListener(this);
        mList.setItemsCanFocus(true);

        mAdapter = createAdapter();

        if (ApiHelper.listAnimationsAvailable()) {
            mAnimationAdapter = new AlphaInAnimationAdapter(mAdapter);
            mAnimationAdapter.setAbsListView(mList);
            mList.setAdapter(mAnimationAdapter);
        } else {
            mList.setAdapter(mAdapter);
        }

        mErrorView.setOnClickListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        logger.info("onDetachedFromWindow");

        onVisibilityChanged(null, View.GONE);

        if (null != mAdapter) {
            mAdapter.changeCursor(null);
        }

        mList.setAdapter(null);

        if (null != mAnimationAdapter) {
            mAnimationAdapter.setAbsListView(null);
        }

        getContext().unregisterReceiver(downloadMissingPacksCompletedReceiver);

        mAttached = false;

        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        logger.info("onVisibilityChanged: " + visibility);
        super.onVisibilityChanged(changedView, visibility);

        if (visibility == View.VISIBLE) {

            getHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (null != mList) {
                        mList.clearFocus();
                        mList.clearChoices();
                        mList.invalidateViews();
                    }
                }
            }, 100);
        }
    }

    protected CursorAdapter createAdapter() {
        return new MyCursorAdapter(getContext(), null);
    }

    @Override
    @SuppressWarnings ("checkstyle:cyclomaticcomplexity")
    public void onClick(View v) {
        if (null == v) {
            return;
        }

        final int id = v.getId();

        if (id == R.id.aviary_restore_all_button) {
            // restore all
            onRestoreAll();

        } else if (id == mErrorView.getId()) {
            // try again
            mListProgress.setVisibility(View.VISIBLE);
            mErrorView.setVisibility(View.GONE);
            update(mData, mParent);

        } else if (id == R.id.aviary_buy_button) {
            // buy

            if (v instanceof IAPBuyButton) {
                IAPBuyButton button = (IAPBuyButton) v;
                ViewGroup parent = (ViewGroup) v.getParent();
                CdsUtils.PackOptionWithPrice packOption = button.getPackOption();

                if (null == packOption || null == parent) {
                    return;
                }

                parent = (ViewGroup) parent.getParent();
                if (null == parent) {
                    return;
                }

                Object tag = parent.getTag();

                if (!(tag instanceof MyCursorAdapter.ViewHolder)) {
                    return;
                }

                MyCursorAdapter.ViewHolder holder = (MyCursorAdapter.ViewHolder) tag;

                if (null == holder || holder.packid < 0 || null == holder.identifier) {
                    return;
                }

                switch (packOption.option) {

                    case PURCHASE:
                        // start the purchase flow
                        mParent.getStoreWrapper()
                            .purchase(holder.packid,
                                      holder.identifier,
                                      mData.getPackType().toCdsString(),
                                      "shop_list",
                                      packOption.price);
                        break;

                    case UNINSTALL:
                        break;

                    case FREE:
                    case RESTORE:
                    case INSTALL:
                    case DOWNLOAD_ERROR:
                        // track the purchase
                        // download it

                        final boolean isFree = packOption.option == CdsUtils.PackOption.FREE;
                        final boolean isRestore = packOption.option == CdsUtils.PackOption.RESTORE;
                        final boolean isError = packOption.option == CdsUtils.PackOption.DOWNLOAD_ERROR;
                        final boolean isFromSubscription = packOption.option == CdsUtils.PackOption.INSTALL;
                        mParent.getStoreWrapper()
                            .restore(holder.packid,
                                     holder.identifier,
                                     mData.getPackType().toCdsString(),
                                     "top_store",
                                     isRestore,
                                     isFree,
                                     isError,
                                     isFromSubscription);
                        break;

                    case ERROR:
                        // remove the pack id from the cache map
                        mParent.mCacheMap.put(holder.packid, CdsUtils.PackOption.PACK_OPTION_BEING_DETERMINED);
                        button.setPackOption(new CdsUtils.PackOptionWithPrice(CdsUtils.PackOption.PACK_OPTION_BEING_DETERMINED),
                                             holder.packid);
                        // query again the billing service
                        mParent.getStoreWrapper().startSetup(true, this);
                        break;

                    case OWNED:
                    case PACK_OPTION_BEING_DETERMINED:
                    case DOWNLOADING:
                    case DOWNLOAD_COMPLETE:
                        // do nothing
                        break;

                    default:
                        /* invalid case */
                        break;
                }

            }
        }
    }

    private void onRestoreAll() {
        logger.info("onRestoreAll");

        Toast.makeText(getContext(), R.string.feather_restore_all_request_sent, Toast.LENGTH_SHORT).show();

        final String secret = mDataService.getIntentExtra(AviaryIntent.EXTRA_API_KEY_SECRET, "");
        final String billingPublicKey = mDataService.getIntentExtra(AviaryIntent.EXTRA_BILLING_PUBLIC_KEY, "");

        Intent intent =
            AviaryIntent.createCdsRestoreAllIntent(getContext(), mData.getPackType().toCdsString(), secret, billingPublicKey);

        if (mParent.getContext().startService(intent) != null) {
            displayProgressNotification();
        }

        AviaryTracker.getInstance(getContext()).tagEvent("shop_list: restore_all_initiated");
    }

    public void update(final IAPUpdater updater, final IAPDialogMain parent) {
        logger.info("update: %s", updater);

        mParent = parent;
        mData = (IAPUpdater) updater.clone();

        if (isValidContext()) {

            if (mParent.getStoreWrapper().isActive()) {

                if (mParent.getStoreWrapper().isSetupDone()) {
                    logger.log("valid iabResult");
                    onUpdate();
                } else {
                    logger.log("setup not yet started");
                    mParent.getStoreWrapper().startSetup(true, this);
                }
            }
        }
    }

    /**
     * Restore all request has been sent, in the meantime let's send also
     * a system notification to show that....
     */
    private void displayProgressNotification() {
        final Context context = getContext();

        if (null != context) {
            NotificationCompat.Builder notification = RestoreAllHelper.createNotification(context);
            notification.setProgress(100, 0, true);
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(RestoreAllHelper.NOTIFICATION_ONGOING_ID, notification.build());
        }
    }

    /**
     * Returns true if the current context is valid and
     * the view is currently attached
     *
     * @return
     */
    boolean isValidContext() {
        return null != getContext() && mAttached;
    }

    private void onUpdate() {
        logger.info("onUpdate");

        if (!isValidContext() || null == getHandler()) {
            return;
        }

        mErrorView.setVisibility(View.GONE);

        int delayTime = getResources().getInteger(android.R.integer.config_mediumAnimTime) + 300;

        getHandler().postDelayed(new Runnable() {
            @Override
            public void run() {
                runInventoryAsyncTask(0, null);
            }
        }, delayTime);

    }

    void runInventoryAsyncTask(int error, Inventory inventory) {
        logger.info("runInventoryAsyncTask");
        new QueryInventoryAsyncTask(error, inventory).execute(mData.getPackType());
    }

    // -----------------------
    // StoreWrapper methods
    // -----------------------

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        logger.info("onItemClick: position: %d, id: %d", position, id);

        if (null != mPackSelectedListener && id > -1) {

            Object tag = view.getTag();
            if (tag instanceof MyCursorAdapter.ViewHolder) {
                String identifier = ((MyCursorAdapter.ViewHolder) tag).identifier;
                mPackSelectedListener.onPackSelected(id, mData.getPackType(), identifier);
            }
        }
    }

    /**
     * Returns the cursor used to display the list of avialable items for this view
     *
     * @param packType
     * @return
     */
    protected Cursor createCursorForAvailablePacks(AviaryCds.PackType packType) throws SQLiteException {

        logger.info("createCursorForAvailablePacks(%s)", packType);

        if (!isValidContext()) {
            return null;
        }

        final String query1 = "pack/type/" + packType.toCdsString() + "/content/available/list";

        Cursor cursor1 = getContext().getContentResolver()
            .query(PackageManagerUtils.getCDSProviderContentUri(getContext(), query1), new String[]{
                PacksColumns._ID + " as _id", PacksColumns._ID, PacksColumns.PACK_TYPE, PacksColumns.IDENTIFIER,
                PacksContentColumns._ID, PacksContentColumns.CONTENT_PATH, PacksContentColumns.CONTENT_URL,
                PacksContentColumns.DISPLAY_NAME, PacksContentColumns.ICON_PATH, PacksContentColumns.ICON_URL,
                PacksContentColumns.IS_FREE_PURCHASE, PacksContentColumns.PURCHASED, PacksContentColumns.PACK_ID,
                PacksContentColumns.ITEMS_COUNT
            }, null, null, PacksContentColumns.PURCHASED + " ASC, " + PacksColumns.DISPLAY_ORDER + " ASC");
        return cursor1;
    }

    /**
     * Returns the cursor used to display the list of avialable items for this view
     *
     * @param packType
     * @return
     */
    protected Cursor createCursorForHiddenPacks(AviaryCds.PackType packType, String selectionOpt) throws SQLiteException {

        logger.info("createCursorForHiddenPacks(%s)", packType);

        if (!isValidContext()) {
            return null;
        }

        if (null == selectionOpt) {
            throw new IllegalArgumentException("selection cannot be null");
        }

        final String query2 = "pack/type/" + packType.toCdsString() + "/content/hidden/list";
        return getContext().getContentResolver()
            .query(PackageManagerUtils.getCDSProviderContentUri(getContext(), query2), new String[]{
                PacksColumns._ID + " as _id", PacksColumns._ID, PacksColumns.PACK_TYPE, PacksColumns.IDENTIFIER,
                PacksContentColumns._ID, PacksContentColumns.CONTENT_PATH, PacksContentColumns.CONTENT_URL,
                PacksContentColumns.DISPLAY_NAME, PacksContentColumns.ICON_PATH, PacksContentColumns.ICON_URL,
                PacksContentColumns.IS_FREE_PURCHASE, PacksContentColumns.PURCHASED, PacksContentColumns.PACK_ID,
                PacksContentColumns.ITEMS_COUNT
            }, selectionOpt, null, PacksContentColumns.PURCHASED + " ASC, " + PacksColumns.DISPLAY_ORDER + " ASC");

    }

    @Override
    public void onIabSetupFinished(final IabResult result) {
        logger.info("onIabSetupFinished: %s", result);

        if (null != mData && isValidContext()) {
            if (null != result) {
                logger.log("mData not null and result is success");
                onUpdate();
            } else {
                onError();
            }
        }
    }

    private void onError() {
        logger.info("onError");

        mListProgress.setVisibility(View.GONE);
        mErrorView.setVisibility(View.VISIBLE);
        mRestoreAllView.setVisibility(View.INVISIBLE);
    }

    void onDownloadStatusChanged(long packId, String packType, int status) {
        if (null != mAdapter && null != mData && isValidContext()) {
            if (null != packType && packType.equals(mData.getPackType().toCdsString())) {
                logger.info("onDownloadStatusChanged: id: %d, type: %s, status: %d", packId, packType, status);
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    void onPackInstalled(final long packId, final String packType, final int purchased) {
        if (null != mAdapter && isValidContext() && null != mData) {
            if (null != packType && packType.equals(mData.getPackType().toCdsString())) {
                logger.info("onPackInstalled: id: %d, type: %s, purchased: %d", packId, packType, purchased);
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    void onPurchaseSuccess(long packId, String packType, Purchase purchase) {

        if (null != mAdapter && isValidContext() && null != mData) {
            if (null != packType && packType.equals(mData.getPackType().toCdsString())) {
                logger.info("onPurchaseSuccess( %d, %s )", packId, packType);
                mAdapter.notifyDataSetChanged();
            }
        }
    }

    void onSubscriptionPurchased(final String identifier, final int purchased) {
        logger.info("onSubscriptionPurchased: identifier: %s, purchased: %d", identifier, purchased);

        if (null != mAdapter && isValidContext() && null != mData) {
            runInventoryAsyncTask(0, mParent.mInventory);
        }
    }

    void onServiceFinished() {
        logger.info("onServiceFinished");

        if (isValidContext() && null != mData) {
            runInventoryAsyncTask(0, null);
        }
    }

    public IAPUpdater getData() {
        return mData;
    }

    public void setOnPackSelectedListener(OnPackSelectedListener listener) {
        mPackSelectedListener = listener;
    }

    interface OnPackSelectedListener {
        void onPackSelected(long packid, PackType packType, String identifier);
    }

    // -------------------
    // List Adapter
    // -------------------

    private class MyCursorAdapter extends android.support.v4.widget.CursorAdapter {
        static final int TYPE_ITEM   = 0;
        static final int TYPE_LOADER = 1;
        int idColumnIndex;
        int displayNameColumnIndex;
        int iconPackColumnIndex;
        int identifierColumnIndex;
        int itemsCountColumnIndex;
        int mMaxImageSize;

        public MyCursorAdapter(Context context, Cursor c) {
            super(context, c, false);
            mMaxImageSize = context.getResources().getDimensionPixelSize(R.dimen.aviary_store_list_icon_size);
            initCursor(c);
        }

        protected void initCursor(Cursor cursor) {
            if (null != cursor) {
                idColumnIndex = cursor.getColumnIndex(PacksColumns._ID);
                displayNameColumnIndex = cursor.getColumnIndex(PacksContentColumns.DISPLAY_NAME);
                iconPackColumnIndex = cursor.getColumnIndex(PacksContentColumns.ICON_PATH);
                identifierColumnIndex = cursor.getColumnIndex(PacksColumns.IDENTIFIER);
                itemsCountColumnIndex = cursor.getColumnIndex(PacksContentColumns.ITEMS_COUNT);
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {

            final int type = getItemViewType(cursor.getPosition());
            final View view;
            ViewHolder holder = new ViewHolder();

            if (type == TYPE_ITEM) {
                view = LayoutInflater.from(context).inflate(R.layout.aviary_iap_list_item, parent, false);
                IAPBuyButton buttonContainer = (IAPBuyButton) view.findViewById(R.id.aviary_buy_button);
                TextView textView1 = (TextView) view.findViewById(R.id.aviary_title);
                TextView textView2 = (TextView) view.findViewById(R.id.aviary_text);
                ImageView imageView = (ImageView) view.findViewById(R.id.aviary_image);

                holder.title = textView1;
                holder.text = textView2;
                holder.icon = imageView;
                holder.buttonContainer = buttonContainer;
                holder.buttonContainer.setOnClickListener(IAPDialogList.this);

            } else {
                view = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, parent, false);
                holder.title = (TextView) view.findViewById(android.R.id.text1);
            }

            view.setTag(holder);
            return view;
        }

        @Override
        @SuppressWarnings ("checkstyle:cyclomaticcomplexity")
        public void bindView(View view, Context context, Cursor cursor) {

            final int type = getItemViewType(cursor.getPosition());
            final ViewHolder holder = (ViewHolder) view.getTag();

            if (null == holder) {
                return;
            }

            if (type == TYPE_ITEM) {
                long packid = cursor.getLong(idColumnIndex);
                String title = cursor.getString(displayNameColumnIndex);
                final String iconPath = cursor.getString(iconPackColumnIndex);
                String identifier = cursor.getString(identifierColumnIndex);
                int itemsCount = cursor.getInt(itemsCountColumnIndex);

                boolean process = true;

                if (null != iconPath) {

                    Object tag = holder.icon.getTag();
                    int hashCode = iconPath.hashCode();
                    if (tag instanceof Integer) {
                        if (((Integer) tag).intValue() == hashCode) {
                            process = false;
                        }
                    }

                    if (process) {
                        mPicasso.cancelRequest(holder.icon);
                        mPicasso.load(iconPath)
                            .resize(mMaxImageSize, mMaxImageSize, true)
                            .noFade()
                            .transform(new PackIconCallable.Builder().withResources(getResources())
                                           .withPath(iconPath)
                                           .withPackType(mData.getPackType())
                                           .noBackground()
                                           .roundedCorners()
                                           .build())
                            .into(holder.icon, new Callback() {
                                @Override
                                public void onSuccess() {
                                    holder.icon.setTag(iconPath.hashCode());
                                }

                                @Override
                                public void onError() {
                                    holder.icon.setTag(null);
                                }
                            });
                        holder.title.setText(title);
                        holder.setItemCount(itemsCount, mData.getPackType());
                    }
                } else {
                    holder.icon.setImageBitmap(null);
                    holder.icon.setTag(null);
                }

                holder.packid = packid;
                holder.identifier = identifier;

                if (null != mParent && null != mParent.getStoreWrapper()) {
                    CdsUtils.PackOption option = null;
                    CdsUtils.PackOptionWithPrice finalOption = null;

                    option = mParent.mDownloadMap.get(holder.packid);

                    if (null == option && mParent.mCacheMap.containsKey(holder.packid)) {
                        option = mParent.mCacheMap.get(holder.packid);
                    }

                    if (null == option) {
                        if (null != mParent.mInventory) {
                            if (mParent.mInventory.hasPurchase(holder.identifier)) {
                                finalOption = new CdsUtils.PackOptionWithPrice(CdsUtils.PackOption.RESTORE);
                            } else {
                                if (mParent.mInventory.hasDetails(holder.identifier)) {
                                    SkuDetails details = mParent.mInventory.getSkuDetails(holder.identifier);
                                    finalOption =
                                        new CdsUtils.PackOptionWithPrice(CdsUtils.PackOption.PURCHASE, details.getPrice());
                                }
                            }
                        }
                    } else {
                        finalOption = new CdsUtils.PackOptionWithPrice(option);
                    }

                    if (null == finalOption) {
                        logger.error("%d, option is null", holder.packid);
                        finalOption = new CdsUtils.PackOptionWithPrice(CdsUtils.PackOption.ERROR);
                    }
                    holder.buttonContainer.setPackOption(finalOption, holder.packid);

                }
            } else {
                // TYPE_LOADER
                holder.title.setText(R.string.feather_store_checking_additional_packs);
            }
        }

        @Override
        public Cursor swapCursor(Cursor newCursor) {
            initCursor(newCursor);
            return super.swapCursor(newCursor);
        }

        @Override
        public int getItemViewType(final int position) {
            return getItemId(position) > -1 ? TYPE_ITEM : TYPE_LOADER;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        class ViewHolder {
            long               packid;
            String             identifier;
            TextView           title;
            TextView           text;
            ImageView          icon;
            IAPBuyButton       buttonContainer;
            AviaryCds.PackType packType;
            int itemsCount = 0;

            public void setItemCount(final int value, AviaryCds.PackType packType) {
                this.packType = packType;
                this.itemsCount = value;
                updateText();
            }

            public void updateText() {
                StringBuilder builder = new StringBuilder();
                final String packTypeText = CdsUIUtils.getPackTypeString(getContext(), packType);
                if (itemsCount > 0) {
                    builder.append(itemsCount + " " + packTypeText);
                } else {
                    builder.append("");
                }
                text.setText(builder.toString());
            }
        }
    }

    /**
     * Startup async task.
     * Gets the list of available stuff from the provider and checks their availability/price
     * on the play store
     */
    class QueryInventoryAsyncTask extends AviaryAsyncTask<PackType, Void, HashMap<Long, CdsUtils.PackOption>> {
        Cursor       cursor1;
        Cursor       cursor2;
        Cursor       finalCursor;
        List<String> ownedPacks;
        int          error;
        PackType     packType;
        Inventory    inventory;

        public QueryInventoryAsyncTask(int error, Inventory inventory) {
            this.error = error;
            this.inventory = inventory;
        }

        @Override
        @SuppressWarnings ("checkstyle:cyclomaticcomplexity")
        protected HashMap<Long, CdsUtils.PackOption> doInBackground(AviaryCds.PackType... params) {
            logger.info("QueryInventoryAsyncTask::doInBackground");

            // this will be the new cached map
            HashMap<Long, CdsUtils.PackOption> map = new HashMap<Long, CdsUtils.PackOption>();

            packType = params[0];
            final AviaryStoreWrapper storeWrapper = mParent.getStoreWrapper();
            final IabResult iabResult = storeWrapper.getIAPInstance().getResult();

            if (null == mParent || !isValidContext()) {
                return map;
            }

            try {
                cursor1 = createCursorForAvailablePacks(packType);
            } catch (SQLiteException e) {
                e.printStackTrace();
            }

            if (null != cursor1 && null != iabResult && iabResult.isSuccess() && null == inventory) {
                try {
                    inventory = getInventory(cursor1, storeWrapper.getIAPInstance());
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    cursor1.moveToPosition(-1);
                }
            }

            if (null == mParent || !isValidContext()) {
                return map;
            }

            String selectionOpt = null;

            if (null != inventory) {
                ownedPacks = getPurchasedPacksByType(inventory, params[0]);
                if (null != ownedPacks) {
                    selectionOpt = PacksColumns.IDENTIFIER + " IN (" + CdsUtils.toSQLArray(ownedPacks) + ")";
                }
            }

            if (null != selectionOpt) {
                try {
                    cursor2 = createCursorForHiddenPacks(params[0], selectionOpt);
                } catch (SQLiteException e) {
                    e.printStackTrace();
                }
            }

            if (null == cursor1) {
                return map;
            }

            if (null != cursor2) {
                finalCursor = new MergeCursor(new Cursor[]{cursor1, cursor2});
            } else {
                finalCursor = cursor1;
            }

            logger.log("final cursor size: %d", finalCursor.getCount());

            try {
                while (finalCursor.moveToNext()) {
                    PacksColumns.PackCursorWrapper pack = PacksColumns.PackCursorWrapper.create(finalCursor);
                    PacksContentColumns.ContentCursorWrapper content = PacksContentColumns.ContentCursorWrapper.create(finalCursor);
                    pack.setContent(content);

                    CdsUtils.PackOption result = CdsUtils.getPackOption(getContext(), pack);
                    logger.log("result: %s", result);

                    switch (result) {
                        case PACK_OPTION_BEING_DETERMINED:
                            break;

                        default:
                            logger.verbose("option: %s, identifier: %s", result, pack.getIdentifier());
                            map.put(pack.getId(), result);
                            break;
                    }

                }
            } finally {
                /* do nothing */
            }

            return map;
        }

        Inventory getInventory(Cursor cursor, IAPInstance store) throws IabException {
            if (null != cursor) {
                List<String> array = new ArrayList<String>();
                while (cursor.moveToNext()) {
                    PacksColumns.PackCursorWrapper wrapper = PacksColumns.PackCursorWrapper.create(cursor);
                    array.add(wrapper.getIdentifier());
                }

                if (store.isAvailable()) {
                    return store.queryInventory(true, array, null);
                }
            }
            return null;
        }

        List<String> getPurchasedPacksByType(Inventory inventory, AviaryCds.PackType packType) {
            List<String> ownedPacks = null;

            if (null != inventory) {
                ownedPacks = inventory.getAllOwnedSkus(IabHelper.ITEM_TYPE_INAPP);
                if (null != ownedPacks) {
                    CdsUtils.filterSkuByPackType(ownedPacks, packType.toCdsString());
                }
            }
            return ownedPacks;
        }

        @Override
        protected void doPostExecute(HashMap<Long, CdsUtils.PackOption> result) {
            logger.info("QueryInventoryAsyncTask::doPostExecute");
            logger.log("result: %s", result);

            if (!isValidContext() || null == mParent) {
                return;
            }

            final Context context = getContext();
            AviaryStoreWrapper wrapper = mParent.getStoreWrapper();

            final IabResult iabResult = wrapper.getIAPInstance().getResult();

            if (null != iabResult && iabResult.isFailure()) {
                logger.warn(iabResult.getMessage());
                if (iabResult.getResponse() != IabHelper.IABHELPER_MISSING_SIGNATURE) {
                    if (!"amazon".equals(BuildConfig.SDK_FLAVOR)) {
                        Toast.makeText(context, iabResult.getMessage(), Toast.LENGTH_SHORT).show();
                    } else {
                        logger.warn("failure: %s", iabResult.getMessage());
                    }
                }
            }

            if (null != finalCursor && null != ownedPacks) {
                finalCursor.moveToPosition(-1);
                removeFromCursor(ownedPacks, finalCursor);

                if (ownedPacks.size() > 0 && error == 0) {
                    mMissingPackRetryCount--;

                    // don't retry forever
                    if (mMissingPackRetryCount > 0) {
                        logger.log("need to download missing packs...");
                        // add the loading view at the bottom of the current ListView..
                        MatrixCursor cursor = new MatrixCursor(finalCursor.getColumnNames());
                        cursor.addRow(new Object[]{-1, -1, "", "", -1, "", "", "", "", "", 0, 0, -1, 0});
                        finalCursor = new MergeCursor(new Cursor[]{finalCursor, cursor});

                        // invoke the service to restore the missing packs..
                        final String secret = mDataService.getIntentExtra(AviaryIntent.EXTRA_API_KEY_SECRET, "");
                        final String billingKey = mDataService.getIntentExtra(AviaryIntent.EXTRA_BILLING_PUBLIC_KEY, "");

                        final Intent intent =
                            AviaryIntent.createCdsRestoreOwnedPacks(context, packType.toCdsString(), secret, billingKey);

                        context.startService(intent);
                    }
                }
            }

            mParent.mCacheMap.clear();
            mParent.mCacheMap.putAll(result);
            mParent.mInventory = this.inventory;
            mAdapter.changeCursor(finalCursor);
            mListProgress.setVisibility(View.GONE);
            mErrorView.setVisibility(View.GONE);
            mRestoreAllView.setVisibility("amazon".equals(BuildConfig.SDK_FLAVOR) ? View.GONE : View.VISIBLE);
        }

        @Override
        protected void doPreExecute() {}

        private void removeFromCursor(List<String> owned, Cursor cursor) {
            logger.info("removeFromCursor");
            while (cursor.moveToNext()) {
                PacksColumns.PackCursorWrapper wrapper = PacksColumns.PackCursorWrapper.create(cursor);
                if (owned.contains(wrapper.getIdentifier())) {
                    owned.remove(wrapper.getIdentifier());
                }
            }
        }
    }

}
