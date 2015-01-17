package com.aviary.android.feather.sdk;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;

import com.aviary.android.feather.common.log.LoggerFactory;
import com.aviary.android.feather.common.log.LoggerFactory.Logger;
import com.aviary.android.feather.common.log.LoggerFactory.LoggerType;
import com.aviary.android.feather.common.tracking.AviaryTracker;
import com.aviary.android.feather.common.utils.ApiHelper;
import com.aviary.android.feather.common.utils.PackageManagerUtils;
import com.aviary.android.feather.common.utils.SDKUtils;
import com.aviary.android.feather.headless.AviaryExecutionException;
import com.aviary.android.feather.library.content.ToolEntry;
import com.aviary.android.feather.library.services.BadgeService;
import com.aviary.android.feather.library.services.BaseContextService;
import com.aviary.android.feather.library.services.ConfigService;
import com.aviary.android.feather.library.services.DragControllerService;
import com.aviary.android.feather.library.services.HiResBackgroundService;
import com.aviary.android.feather.library.services.IAPService;
import com.aviary.android.feather.library.services.IAviaryController;
import com.aviary.android.feather.library.services.LocalDataService;
import com.aviary.android.feather.library.services.PreferenceService;
import com.aviary.android.feather.library.services.ServiceLoader;
import com.aviary.android.feather.library.services.SessionService;
import com.aviary.android.feather.library.services.ThreadPoolService;
import com.aviary.android.feather.library.services.drag.DragLayer;
import com.aviary.android.feather.library.utils.ImageInfo;
import com.aviary.android.feather.library.vo.EditToolResultVO;
import com.aviary.android.feather.sdk.overlays.AviaryOverlay;
import com.aviary.android.feather.sdk.overlays.UndoRedoOverlay;
import com.aviary.android.feather.sdk.panels.AbstractPanel;
import com.aviary.android.feather.sdk.panels.AbstractPanel.ContentPanel;
import com.aviary.android.feather.sdk.panels.AbstractPanel.OnApplyResultListener;
import com.aviary.android.feather.sdk.panels.AbstractPanel.OnContentReadyListener;
import com.aviary.android.feather.sdk.panels.AbstractPanel.OnErrorListener;
import com.aviary.android.feather.sdk.panels.AbstractPanel.OnPreviewListener;
import com.aviary.android.feather.sdk.panels.AbstractPanel.OnProgressListener;
import com.aviary.android.feather.sdk.panels.AbstractPanel.OptionPanel;
import com.aviary.android.feather.sdk.panels.AbstractPanelLoaderService;
import com.aviary.android.feather.sdk.widget.AviaryBottomBarViewFlipper;
import com.aviary.android.feather.sdk.widget.AviaryBottomBarViewFlipper.OnViewChangingStatusListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import it.sephiroth.android.library.imagezoom.ImageViewTouchBase;
import it.sephiroth.android.library.tooltip.TooltipManager;

/**
 * Main controller.<br />
 * It manages all the tools, notifies about new plugins installed, etc...
 *
 * @author alessandro
 */
public final class AviaryMainController
    implements IAviaryController, OnPreviewListener, OnApplyResultListener, OnErrorListener, OnContentReadyListener,
               OnProgressListener, HiResBackgroundService.OnHiresListener {
    /**
     * The Interface FeatherContext.<br />
     * The activity caller must implement this interface
     */
    public interface FeatherContext {
        /**
         * Gets the Activity main image view.
         *
         * @return the main image
         */
        ImageViewTouchBase getMainImage();

        /**
         * Gets the Activity bottom bar view.
         *
         * @return the bottom bar
         */
        AviaryBottomBarViewFlipper getBottomBar();

        /**
         * Gets the Activity options panel container view.
         *
         * @return the options panel container
         */
        ViewGroup getOptionsPanelContainer();

        /**
         * Gets the Activity drawing image container view.
         *
         * @return the drawing image container
         */
        ViewGroup getDrawingImageContainer();

        /**
         * There's a special container drawn on top of all views, which can be used to add
         * custom dialogs/popups.
         * This is invisible by default and must be activated in order to be used
         *
         * @return
         */
        ViewGroup activatePopupContainer();

        /**
         * When the there's no need to use the popup container anymore, you must
         * deactivate it
         */
        void deactivatePopupContainer();

        /**
         * Show tool progress.
         */
        void showToolProgress();

        /**
         * Hide tool progress.
         */
        void hideToolProgress();

        /**
         * Show a modal progress
         */
        void showModalProgress();

        /**
         * Hide the modal progress
         */
        void hideModalProgress();

        AviaryTracker getTracker();

        /**
         * Returns the current actionbar size in pixels
         *
         * @return
         */
        int getActionBarSize();
    }

    public interface OnBitmapChangeListener {
        void onBitmapChange(Bitmap bitmap, boolean update, Matrix matrix);

        void onPreviewChange(Bitmap bitmap, boolean reset);

        void onPreviewChange(Drawable drawable, boolean reset);

        void onInvalidateBitmap();
    }

    public static String stateToString(int state) {
        switch (state) {
            case CONTROLLER_STATE_CLOSED_CANCEL:
                return "closed_cancel";
            case CONTROLLER_STATE_CLOSED_CONFIRMED:
                return "closed_confirmed";
            case CONTROLLER_STATE_CLOSING:
                return "closing";
            case CONTROLLER_STATE_OPENED:
                return "opened";
            case CONTROLLER_STATE_DISABLED:
                return "disabled";
            case CONTROLLER_STATE_OPENING:
                return "opening";
            default:
                return null;
        }
    }

    /** state: controller is disabled */
    public static final int CONTROLLER_STATE_DISABLED         = 0;
    /** state: controller panel is opening */
    public static final int CONTROLLER_STATE_OPENING          = 1;
    /** state: controller panel is opened */
    public static final int CONTROLLER_STATE_OPENED           = 2;
    /** state: controller panel is closing */
    public static final int CONTROLLER_STATE_CLOSING          = 3;
    /** state: controller panel has been closed after an apply */
    public static final int CONTROLLER_STATE_CLOSED_CONFIRMED = 4;
    /** state: controller panel has been closed after a cancel */
    public static final int CONTROLLER_STATE_CLOSED_CANCEL    = 5;
    public static final int PANEL_STATE_CONTENT_READY         = 5;
    public static final int PANEL_STATE_READY                 = 6;
    /** controller state is changed */
    public static final int CONTROLLER_STATE_CHANGED          = 10;
    public static final int TOOLBAR_TITLE                     = 100;
    public static final int TOOLBAR_TITLE_INT                 = 101;
    public static final int TOOLBAR_APPLY_VISIBILITY          = 102;
    /** The current bitmap. */
    private       Bitmap                                       mBitmap;
    /** The base context. This is the main activity */
    private       FeatherContext                               mContext;
    /** The current active effect. */
    private       AbstractPanel                                mCurrentEffect;
    /** The current active effect entry. */
    private       ToolEntry                                    mCurrentEntry;
    /** The list of enabled tools, if it is null, we assume all tools are enabled */
    private       List<String>                                 mToolList;
    /** The current panel state. */
    private       int                                          mCurrentState;
    /** bitmap change listener */
    private       OnBitmapChangeListener                       mBitmapChangeListener;
    private final Handler                                      mHandler;
    private final ServiceLoader<BaseContextService>            mServiceLoader;
    private       AbstractPanelLoaderService                   mPanelCreatorService;
    private       Logger                                       logger;
    /** The changed state. If the original image has been modified. */
    private       boolean                                      mChanged;
    private       Configuration                                mConfiguration;
    private       List<HiResBackgroundService.OnHiresListener> mHiresListeners;
    private       DragLayer                                    mDragLayer;
    /** true when the app has been updated from the google play store */
    private       Boolean                                      mAppIsUpdated;
    private       int                                          mToolCompleteCount;

    /**
     * Instantiates a new filter manager.
     *
     * @param context the context
     * @param handler the handler
     */
    public AviaryMainController(final FeatherContext context, final Handler handler) {
        logger = LoggerFactory.getLogger("AviaryMainController", LoggerType.ConsoleLoggerType);
        mContext = context;
        mHandler = handler;
        mToolCompleteCount = 0;
        mHiresListeners = new ArrayList<HiResBackgroundService.OnHiresListener>(0);

        mServiceLoader = new ServiceLoader<BaseContextService>(this);
        mConfiguration = new Configuration(((Context) context).getResources().getConfiguration());

        initServices(context);
        setCurrentState(CONTROLLER_STATE_DISABLED);
        mChanged = false;
    }

    public void addOnHiresListener(HiResBackgroundService.OnHiresListener listener) {
        mHiresListeners.add(listener);
    }

    public boolean removeOnHiresListener(HiResBackgroundService.OnHiresListener listener) {
        return mHiresListeners.remove(listener);
    }

    @Override
    public AviaryTracker getTracker() {
        return mContext.getTracker();
    }

    /**
     * Returns the total count of tools that
     * have been applied during the session
     *
     * @return
     */
    public int getToolCompleteCount() {
        return mToolCompleteCount;
    }

    protected void setToolList(List<String> toolList) {
        mToolList = toolList;
    }

    protected List<String> getToolList() {
        return mToolList;
    }

    /**
     * Returns true if the app has been updated from an older version
     * and this is the first time it's running
     *
     * @return
     */
    public boolean getAppIsUpdated() {
        if (null == mAppIsUpdated) {
            PreferenceService service = getService(PreferenceService.class);
            if (null != service) {

                int versionCode;

                PackageInfo info = PackageManagerUtils.getPackageInfo(getBaseContext());

                if (null != info) {
                    versionCode = info.versionCode;
                } else {
                    versionCode = SDKUtils.SDK_VERSION_CODE;
                }

                int registeredVersion = service.getInt("aviary-package-version", 0);

                logger.info("registered version: " + registeredVersion + ", my version: " + versionCode);

                if (registeredVersion != versionCode) {
                    mAppIsUpdated = true;
                    service.putInt("aviary-package-version", versionCode);
                } else {
                    mAppIsUpdated = false;
                }
            } else {
                logger.error("can't open preferenceService");
                mAppIsUpdated = false;
            }
        }

        return mAppIsUpdated.booleanValue();
    }

    public void setDragLayer(DragLayer view) {
        mDragLayer = view;
    }

    private synchronized void initServices(final FeatherContext context) {
        logger.info("initServices");

        mServiceLoader.register(SessionService.class);
        mServiceLoader.register(LocalDataService.class);
        mServiceLoader.register(ThreadPoolService.class);
        mServiceLoader.register(ConfigService.class);
        mServiceLoader.register(IAPService.class);
        mServiceLoader.register(BadgeService.class);
        mServiceLoader.register(HiResBackgroundService.class);
        mServiceLoader.register(DragControllerService.class);
        mServiceLoader.register(PreferenceService.class);
        mServiceLoader.register(AbstractPanelLoaderService.class);
    }

    private void initHiResService(final Bitmap bitmap, final ImageInfo imageInfo) {
        logger.info("initHiResService");
        LocalDataService dataService = getService(LocalDataService.class);
        HiResBackgroundService service = getService(HiResBackgroundService.class);

        if (null != bitmap && null != imageInfo) {
            if (null != imageInfo.getOriginalSize() && imageInfo.getOriginalSize().length == 2) {
                logger.log("original size: %dx%d", imageInfo.getOriginalSize()[0], imageInfo.getOriginalSize()[1]);
                logger.log("bitmap size: %dx%d", bitmap.getWidth(), bitmap.getHeight());
            }
        }

        if (!service.isRunning()) {
            service.setOnHiresListener(this);
        }

        SessionService sessionService = getService(SessionService.class);
        if (!sessionService.isRunning()) {
            sessionService.start();
        }
        sessionService.load(bitmap, dataService.getRequestedMegaPixels(), imageInfo);
    }

    public void activateTool(final ToolEntry tag) {
        activateTool(tag, null);
    }

    /**
     * This is the entry point of every aviary tool
     *
     * @param tag     indicates which tool to activate
     * @param options an optional Bundle to be passed to the created {@link AbstractPanel}
     */
    public void activateTool(final ToolEntry tag, Bundle options) {
        if (!getEnabled() || !isClosed() || mBitmap == null) {
            return;
        }

        // check if the tool is enabled. if mToolList is null, just assume enabled:
        if (mToolList != null) {
            if (mToolList.indexOf(tag.name.name()) < 0) {
                // throw new IllegalStateException( "Selected entry is not valid" );
                return;
            }
        }

        if (mCurrentEffect != null) {
            throw new IllegalStateException("There is already an active effect. Cannot activate new");
        }
        if (mPanelCreatorService == null) {
            mPanelCreatorService = getService(AbstractPanelLoaderService.class);
        }

        final AbstractPanel effect = mPanelCreatorService.createNew(tag);

        if (effect != null) {
            mCurrentEffect = effect;
            mCurrentEntry = tag;

            // mark tool as read
            BadgeService badge = getService(BadgeService.class);
            badge.markAsRead(tag.name);

            setCurrentState(CONTROLLER_STATE_OPENING);
            prepareToolPanel(effect, tag, options);

            getTracker().tagEvent(mCurrentEntry.name.name().toLowerCase(Locale.US) + ": opened");

            mContext.getBottomBar().setOnViewChangingStatusListener(new OnViewChangingStatusListener() {
                @Override
                public void onOpenStart() {
                    mCurrentEffect.onOpening();
                }

                @Override
                public void onOpenEnd() {
                    setCurrentState(CONTROLLER_STATE_OPENED);
                    mContext.getBottomBar().setOnViewChangingStatusListener(null);
                }

                @Override
                public void onCloseStart() {}

                @Override
                public void onCloseEnd() {}
            });

            mContext.getBottomBar().open();
        }
    }

    public void dispose() {
        if (mCurrentEffect != null) {
            logger.log("Deactivate and destroy current panel");
            mCurrentEffect.onDeactivate();
            mCurrentEffect.onDestroy();
            mCurrentEffect = null;
        }

        HiResBackgroundService hiresService = getService(HiResBackgroundService.class);
        if (null != hiresService) {
            hiresService.setOnHiresListener(null);
        }

        mServiceLoader.dispose();
        mContext = null;
        mBitmapChangeListener = null;

        System.gc();
    }

    @Override
    public Context getBaseContext() {
        return (Context) mContext;
    }

    @Override
    public Activity getBaseActivity() {
        return (Activity) mContext;
    }

    /**
     * Return the current bitmap.
     *
     * @return the bitmap
     */
    public Bitmap getBitmap() {
        return mBitmap;
    }

    /**
     * Return true if the main image has been modified by any of the feather tools.
     *
     * @return the bitmap is changed
     */
    public boolean getBitmapIsChanged() {
        return mChanged;
    }

    /**
     * Returns true if the main image has been modified or there's an
     * active panel which is modifying it
     *
     * @return
     */
    public boolean getBitmapIsChangedOrChanging() {
        if (mChanged) {
            return true;
        }
        if (null != mCurrentEffect && mCurrentEffect.isActive()) {
            if (mCurrentEffect.getIsChanged()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if there's an active tool which has
     * an active render task
     *
     * @return
     */
    public boolean getPanelIsRendering() {
        if (null != mCurrentEffect && mCurrentEffect.isActive()) {
            return mCurrentEffect.isRendering();
        }
        return false;
    }

    /**
     * Returns the active tool, null if there is not active tool.
     *
     * @return
     */
    @Override
    public ToolEntry getActiveTool() {
        return mCurrentEntry;
    }

    /**
     * Return the current panel associated with the active tool. Null if there's no active
     * tool
     *
     * @return the current panel
     */
    public AbstractPanel getActiveToolPanel() {
        return mCurrentEffect;
    }

    /**
     * Return the current image transformation matrix. this is useful for those tools
     * which implement ContentPanel and want to
     * display the preview bitmap with the same zoom level of the main image
     *
     * @return the current image view matrix
     * @see ContentPanel
     */
    @Override
    public Matrix getCurrentImageViewMatrix() {
        return mContext.getMainImage().getDisplayMatrix();
    }

    /**
     * Return true if enabled.
     *
     * @return the enabled
     */
    public boolean getEnabled() {
        return mCurrentState != CONTROLLER_STATE_DISABLED;
    }

    /**
     * Return the service, if previously registered using ServiceLoader.
     *
     * @param <T> the generic type
     * @param cls the cls
     * @return the service
     */
    @SuppressWarnings ("unchecked")
    @Override
    public <T> T getService(Class<T> cls) {
        try {
            return (T) mServiceLoader.getService((Class<BaseContextService>) cls);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void registerService(Class<? extends BaseContextService> cls) {
        mServiceLoader.register(cls);
    }

    /**
     * Remove any running instance of the {@link BaseContextService} and delete it from
     * the registered services
     *
     * @param cls
     */
    public void removeService(Class<? extends BaseContextService> cls) {
        mServiceLoader.remove(cls);
    }

    /**
     * Return true if there's no active tool.
     *
     * @return true, if is closed
     */
    public boolean isClosed() {
        return (mCurrentState == CONTROLLER_STATE_CLOSED_CANCEL) || (mCurrentState == CONTROLLER_STATE_CLOSED_CONFIRMED);
    }

    /**
     * return true if there's one active tool.
     *
     * @return true, if is opened
     */
    public boolean isOpened() {
        return mCurrentState == CONTROLLER_STATE_OPENED;
    }

    /**
     * On activate.
     *
     * @param bitmap    the bitmap
     * @param imageInfo the info about the loaded image
     */
    public void onActivate(final Bitmap bitmap, ImageInfo imageInfo) {
        if (mCurrentState != CONTROLLER_STATE_DISABLED) {
            throw new IllegalStateException("Cannot activate. Already active!");
        }

        if ((mBitmap != null) && !mBitmap.isRecycled()) {
            mBitmap = null;
        }

        mBitmap = bitmap;

        LocalDataService dataService = getService(LocalDataService.class);
        dataService.setImageInfo(imageInfo);

        mChanged = false;
        setCurrentState(CONTROLLER_STATE_CLOSED_CONFIRMED);
        initHiResService(bitmap, imageInfo);
    }

    /**
     * @param requestCode
     * @param resultCode
     * @param data
     * @return true if the action has been handled
     */
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        IAPService service = getService(IAPService.class);
        if (null != service && requestCode == IAPService.PURCHASE_FLOW_REQUEST_CODE) {
            try {
                return service.handleActivityResult(requestCode, resultCode, data);
            } catch (IllegalStateException e) {
                logger.error("handled exception");
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * Current activity is asking to apply the current tool.
     */
    public void onApply() {
        logger.info("FilterManager::onapply");
        if (!getEnabled() || !isOpened()) {
            return;
        }

        if (mCurrentEffect == null) {
            throw new IllegalStateException("there is no current effect active in the context");
        }

        if (!mCurrentEffect.isEnabled()) {
            return;
        }

        if (mCurrentEffect.getIsChanged()) {
            mCurrentEffect.onSave();
            mChanged = true;
        } else {
            onCancel();
        }
    }

    /**
     * Parent activity just received a onBackPressed event. If there's one active tool, it
     * will be asked to manage the onBackPressed
     * event. If the active tool onBackPressed method return a false then try to close it.
     *
     * @return true, if successful
     */
    public boolean onBackPressed() {
        if (isClosed()) {
            return false;
        }
        if (mCurrentState != CONTROLLER_STATE_DISABLED) {
            if (isOpened()) {
                if (!mCurrentEffect.onBackPressed()) {
                    onCancel();
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Main activity asked to cancel the current operation.
     */
    public void onCancel() {
        if (!getEnabled() || !isOpened()) {
            return;
        }
        if (mCurrentEffect == null) {
            throw new IllegalStateException("there is no current effect active in the context");
        }
        if (!mCurrentEffect.onCancel()) {
            cancel();
        }
    }

    @Override
    public void cancel() {

        logger.info("FilterManager::cancel");

        if (!getEnabled() || !isOpened()) {
            return;
        }
        if (mCurrentEffect == null) {
            throw new IllegalStateException("there is no current effect active in the context");
        }

        mContext.getTracker().tagEvent(mCurrentEntry.name.name().toLowerCase(Locale.US) + ": cancelled");

        // send the cancel event to the effect
        mCurrentEffect.onCancelled();

        // check changed image
        if (mCurrentEffect.getIsChanged()) {
            // panel is changed, restore the original bitmap
            setNextBitmap(mBitmap, true);
        } else {
            // panel is not changed
            setNextBitmap(mBitmap, true);
        }
        onClose(false);
    }

    /**
     * On close.
     *
     * @param isConfirmed the is confirmed
     */
    private void onClose(final boolean isConfirmed) {

        logger.info("onClose");

        setCurrentState(CONTROLLER_STATE_CLOSING);

        mContext.getBottomBar().setOnViewChangingStatusListener(new OnViewChangingStatusListener() {
            @Override
            public void onOpenStart() {}

            @Override
            public void onOpenEnd() {}

            @Override
            public void onCloseStart() {
                mCurrentEffect.onClosing();
            }

            @Override
            public void onCloseEnd() {
                setCurrentState(isConfirmed ? CONTROLLER_STATE_CLOSED_CONFIRMED : CONTROLLER_STATE_CLOSED_CANCEL);
                mContext.getBottomBar().setOnViewChangingStatusListener(null);
            }
        });

        mContext.getBottomBar().close();
    }

    @Override
    public void onComplete(final Bitmap result, EditToolResultVO editResultVO) {
        mToolCompleteCount += 1;

        if (result != null) {
            setNextBitmap(result, true);
        } else {
            logger.error("Error: returned bitmap is null!");
            setNextBitmap(mBitmap, true);
        }

        onClose(true);

        if (null != editResultVO) {

            // validate the result object ( this will throw an AssertionError )
            if (!editResultVO.valid()) {
                logger.error("editResult is not valid!");
            }

            // add the recipe to the data service
            LocalDataService dataService = getService(LocalDataService.class);
            if (null != dataService) {
                dataService.addRecipe(editResultVO.getEditTool());
            }

            if (null != editResultVO.getActionList() && editResultVO.getActionList().size() > 0) {
                SessionService sessionService = getService(SessionService.class);
                sessionService.push(result, editResultVO);

                // show undo/redo overlay
                if (ApiHelper.isUndoRedoAvailable() && AviaryOverlay.shouldShow(this, AviaryOverlay.ID_UNDO_REDO)) {
                    AviaryOverlay overlay = new UndoRedoOverlay(getBaseContext(), R.style.AviaryWidget_Overlay_UndoRedo);
                    overlay.showDelayed(400);
                }

            } else {
                logger.error("actionlist is missing!");
            }
        } else {
            logger.error("Something was wrong, edit result is null!");
        }
    }

    /**
     * Sets the next bitmap.
     *
     * @param bitmap the new next bitmap
     */
    void setNextBitmap(Bitmap bitmap) {
        setNextBitmap(bitmap, true);
    }

    /**
     * Sets the next bitmap.
     *
     * @param bitmap the bitmap
     * @param update the update
     */
    void setNextBitmap(Bitmap bitmap, boolean update) {
        setNextBitmap(bitmap, update, null);
    }

    /**
     * Sets the next bitmap.
     *
     * @param bitmap the bitmap
     * @param update the update
     * @param matrix the matrix
     */
    void setNextBitmap(Bitmap bitmap, boolean update, Matrix matrix) {
        logger.log("setNextBitmap", bitmap, update, matrix);

        if (null != mBitmapChangeListener) {
            mBitmapChangeListener.onBitmapChange(bitmap, update, matrix);
        }

        if (!mBitmap.equals(bitmap) && !mBitmap.isRecycled()) {
            logger.warn("[recycle] original Bitmap: " + mBitmap);
            mBitmap.recycle();
            mBitmap = null;
        }
        mBitmap = bitmap;
    }

    @Override
    public void onError(CharSequence message, int yesLabel, OnClickListener yesListener) {
        new AlertDialog.Builder((Activity) mContext).setTitle(R.string.feather_generic_error_title)
            .setMessage(message)
            .setPositiveButton(yesLabel, yesListener)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }

    @Override
    public void onError(CharSequence message, int yesLabel, OnClickListener yesListener, int noLabel, OnClickListener noListener) {
        new AlertDialog.Builder((Activity) mContext).setTitle(R.string.feather_generic_error_title)
            .setMessage(message)
            .setPositiveButton(yesLabel, yesListener)
            .setNegativeButton(noLabel, noListener)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show();
    }

    @Override
    public void onMessage(CharSequence title, CharSequence message, int yesLabel, OnClickListener yesListener) {
        new AlertDialog.Builder((Activity) mContext).setTitle(title)
            .setMessage(message)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton(yesLabel, yesListener)
            .show();
    }

    @Override
    public void onMessage(
        CharSequence title, CharSequence message, int yesLabel, OnClickListener yesListener, int noLabel,
        OnClickListener noListener) {
        new AlertDialog.Builder((Activity) mContext).setTitle(title)
            .setMessage(message)
            .setPositiveButton(yesLabel, yesListener)
            .setNegativeButton(noLabel, noListener)
            .setIcon(android.R.drawable.ic_dialog_info)
            .show();
    }

    @Override
    public void onPreviewChange(final Bitmap result, boolean reset) {
        if (!getEnabled() || !isOpened()) {
            return;
        }
        if (null != mBitmapChangeListener) {
            mBitmapChangeListener.onPreviewChange(result, reset);
        }
    }

    @Override
    public void onPreviewChange(final Drawable drawable, final boolean reset) {
        if (!getEnabled() || !isOpened()) {
            return;
        }
        if (null != mBitmapChangeListener) {
            mBitmapChangeListener.onPreviewChange(drawable, reset);
        }
    }

    @Override
    public void onPreviewUpdated() {
        if (!getEnabled() || !isOpened()) {
            return;
        }
        if (null != mBitmapChangeListener) {
            mBitmapChangeListener.onInvalidateBitmap();
        }
    }

    @Override
    public void onReady(final AbstractPanel panel) {
        mHandler.sendEmptyMessage(PANEL_STATE_CONTENT_READY);
        mHandler.sendEmptyMessage(PANEL_STATE_READY);
    }

    public void onUndo() {
        logger.info("onUndo");
        if (!getEnabled() || mCurrentEffect != null) {
            return;
        }
        SessionService sessionService = getService(SessionService.class);
        if (sessionService.canUndo()) {
            Bitmap bitmap = sessionService.undo();
            if (null != bitmap) {
                setNextBitmap(bitmap, true);
                showUndoRedoToolTip(R.string.feather_undo);
                getTracker().tagEvent("editor: undo");
            }
        } else {
            if (sessionService.canRedo()) {
                showUndoRedoToolTip(R.string.feather_cant_undo_anymore);
            }
        }
    }

    public void onRedo() {
        logger.info("onRedo");
        if (!getEnabled() || mCurrentEffect != null) {
            return;
        }
        SessionService sessionService = getService(SessionService.class);
        if (sessionService.canRedo()) {
            Bitmap bitmap = sessionService.redo();
            if (null != bitmap) {
                setNextBitmap(bitmap, true);
                showUndoRedoToolTip(R.string.feather_redo);
                getTracker().tagEvent("editor: redo");
            }
        }
    }

    /**
     * On save.
     */
    public void onSave() {
        if (!getEnabled() || !isClosed()) {
            return;
        }
    }

    private void showUndoRedoToolTip(int textId) {
        DisplayMetrics metrics = getBaseActivity().getResources().getDisplayMetrics();
        TooltipManager.getInstance(getBaseActivity()).remove(0);
        TooltipManager.getInstance(getBaseActivity())
            .create(0)
            .withCustomView(R.layout.aviary_default_tooltip, false)
            .closePolicy(TooltipManager.ClosePolicy.None, 1000)
            .text(getBaseContext().getResources(), textId)
            .withStyleId(R.style.AviaryUndoTooltip)
            .actionBarSize(mContext.getActionBarSize())
            .toggleArrow(false)
            .anchor(new Point(metrics.widthPixels / 2, 90), TooltipManager.Gravity.BOTTOM)
            .show();
    }

    /**
     * Prepare tool panel.
     *
     * @param effect
     * @param entry
     */
    private void prepareToolPanel(final AbstractPanel effect, final ToolEntry entry, Bundle options) {
        View optionChild;
        View drawingChild;

        if (effect instanceof OptionPanel) {
            optionChild =
                ((OptionPanel) effect).getOptionView(LayoutInflater.from((Context) mContext), mContext.getOptionsPanelContainer());
            mContext.getOptionsPanelContainer().addView(optionChild);
        }

        if (effect instanceof ContentPanel) {
            drawingChild = ((ContentPanel) effect).getContentView(LayoutInflater.from((Context) mContext));
            drawingChild.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            mContext.getDrawingImageContainer().addView(drawingChild);
        }

        effect.onCreate(mBitmap, options);
    }

    /**
     * Run a Runnable on the main UI thread.
     *
     * @param action the action
     */
    @Override
    public void runOnUiThread(final Runnable action) {
        if (mContext != null) {
            ((Activity) mContext).runOnUiThread(action);
        }
    }

    /**
     * Sets the current state.
     *
     * @param newState the new current state
     */
    private void setCurrentState(final int newState) {
        if (newState != mCurrentState) {
            logger.info("setcurrentState: %s >> %s", mCurrentState, newState);
            final int previousState = mCurrentState;
            mCurrentState = newState;

            switch (newState) {
                case CONTROLLER_STATE_OPENING:

                    mCurrentEffect.setOnPreviewListener(this);
                    mCurrentEffect.setOnApplyResultListener(this);
                    mCurrentEffect.setOnErrorListener(this);
                    mCurrentEffect.setOnProgressListener(this);

                    if (mCurrentEffect instanceof ContentPanel) {
                        ((ContentPanel) mCurrentEffect).setOnReadyListener(this);
                    }
                    mHandler.obtainMessage(CONTROLLER_STATE_CHANGED, newState, previousState).sendToTarget();
                    break;

                case CONTROLLER_STATE_OPENED:
                    mCurrentEffect.onActivate();
                    mHandler.obtainMessage(CONTROLLER_STATE_CHANGED, newState, previousState).sendToTarget();

                    if (!(mCurrentEffect instanceof ContentPanel)) {
                        mHandler.sendEmptyMessage(PANEL_STATE_READY);
                    }

                    break;

                case CONTROLLER_STATE_CLOSING:
                    mHandler.obtainMessage(CONTROLLER_STATE_CHANGED, newState, previousState).sendToTarget();

                    mCurrentEffect.onDeactivate();
                    if (mCurrentEffect instanceof ContentPanel) {
                        ((ContentPanel) mCurrentEffect).setOnReadyListener(null);
                    }

                    // TODO: use a delay?
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mContext.getDrawingImageContainer().removeAllViews();
                            mContext.deactivatePopupContainer();
                        }
                    });
                    break;

                case CONTROLLER_STATE_CLOSED_CANCEL:
                case CONTROLLER_STATE_CLOSED_CONFIRMED:

                    mContext.getOptionsPanelContainer().removeAllViews();

                    if (previousState != CONTROLLER_STATE_DISABLED) {
                        mCurrentEffect.onDestroy();
                        mCurrentEffect.setOnPreviewListener(null);
                        mCurrentEffect.setOnApplyResultListener(null);
                        mCurrentEffect.setOnErrorListener(null);
                        mCurrentEffect.setOnProgressListener(null);
                        mCurrentEffect = null;
                        mCurrentEntry = null;
                    }
                    mHandler.obtainMessage(CONTROLLER_STATE_CHANGED, newState, previousState).sendToTarget();
                    System.gc();
                    break;

                case CONTROLLER_STATE_DISABLED:
                    mHandler.obtainMessage(CONTROLLER_STATE_CHANGED, newState, previousState).sendToTarget();
                    break;

                default:
                    logger.error("Invalid state");
                    break;
            }
        }
    }

    /**
     * Sets the enabled.
     *
     * @param value the new enabled
     */
    public void setEnabled(final boolean value) {
        if (!value) {
            if (isClosed()) {
                setCurrentState(CONTROLLER_STATE_DISABLED);
            } else {
                logger.warn("FilterManager must be closed to change state");
            }
        }
    }

    public void setOnBitmapChangeListener(final OnBitmapChangeListener listener) {
        mBitmapChangeListener = listener;
    }

    /**
     * Main Activity configuration changed We want to dispatch the configuration event
     * also to the opened panel.
     *
     * @param newConfig the new config
     * @return true if the event has been handled
     */
    public boolean onConfigurationChanged(Configuration newConfig) {

        boolean result = false;
        logger.info("onConfigurationChanged: " + newConfig.orientation + ", " + mConfiguration.orientation);

        if (mCurrentEffect != null) {
            if (mCurrentEffect.isCreated()) {
                logger.info("onConfigurationChanged, sending event to ", mCurrentEffect);
                mCurrentEffect.onConfigurationChanged(newConfig, mConfiguration);
                result = true;
            }
        }

        mConfiguration = new Configuration(newConfig);
        return result;
    }

    @Override
    public void onProgressStart() {
        mContext.showToolProgress();
    }

    @Override
    public void onProgressEnd() {
        mContext.hideToolProgress();
    }

    @Override
    public void onProgressModalStart() {
        mContext.showModalProgress();
    }

    @Override
    public void onProgressModalEnd() {
        mContext.hideModalProgress();
    }

    @Override
    public void setToolbarTitle(int resId) {
        final Message message = mHandler.obtainMessage(TOOLBAR_TITLE_INT, resId, 0);
        mHandler.sendMessage(message);
    }

    @Override
    public void setToolbarTitle(CharSequence value) {
        final Message message = mHandler.obtainMessage(TOOLBAR_TITLE, value);
        mHandler.sendMessage(message);
    }

    @Override
    public void restoreToolbarTitle() {

        if (null != mCurrentEntry) {
            final Message message = mHandler.obtainMessage(TOOLBAR_TITLE_INT, mCurrentEntry.labelResourceId, 0);
            mHandler.sendMessage(message);
        }
    }

    @Override
    public void setPanelApplyStatusEnabled(boolean enabled) {
        final Message message = mHandler.obtainMessage(TOOLBAR_APPLY_VISIBILITY, enabled ? 1 : 0, 0);
        mHandler.sendMessage(message);
    }

    @Override
    public DragLayer getDragLayer() {
        return mDragLayer;
    }

    // ----------------------
    // HIRES listener methods
    // ----------------------

    @Override
    public void onHiresError(final AviaryExecutionException ex) {
        logger.info("onHiresError: " + ex);

        for (HiResBackgroundService.OnHiresListener listener : mHiresListeners) {
            listener.onHiresError(ex);
        }
    }

    @Override
    public void onHiresProgress(final int index, final int total) {
        logger.info("onHiresProgress: %d of %d", index, total);

        for (HiResBackgroundService.OnHiresListener listener : mHiresListeners) {
            listener.onHiresProgress(index, total);
        }
    }

    @Override
    public void onHiresComplete() {
        logger.info("onHiresComplete");

        for (HiResBackgroundService.OnHiresListener listener : mHiresListeners) {
            listener.onHiresComplete();
        }
    }
}
