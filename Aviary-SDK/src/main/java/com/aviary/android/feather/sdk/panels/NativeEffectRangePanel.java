package com.aviary.android.feather.sdk.panels;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.os.Bundle;

import com.aviary.android.feather.common.utils.ApiHelper;
import com.aviary.android.feather.common.utils.os.AviaryAsyncTask;
import com.aviary.android.feather.headless.filters.IFilter;
import com.aviary.android.feather.headless.filters.INativeRangeFilter;
import com.aviary.android.feather.headless.moa.MoaActionList;
import com.aviary.android.feather.headless.moa.MoaResult;
import com.aviary.android.feather.library.Constants;
import com.aviary.android.feather.library.content.ToolEntry;
import com.aviary.android.feather.library.filters.ToolLoaderFactory;
import com.aviary.android.feather.library.graphics.drawable.FakeBitmapDrawable;
import com.aviary.android.feather.library.services.IAviaryController;
import com.aviary.android.feather.library.utils.BitmapUtils;
import com.aviary.android.feather.library.vo.EditToolResultVO;
import com.aviary.android.feather.library.vo.ToolActionVO;
import com.aviary.android.feather.sdk.R;

public class NativeEffectRangePanel extends SliderEffectPanel {
    static final int PREVIEW_FAKE_RATIO = 3;
    private ApplyFilterTask mCurrentTask;
    private boolean enableFastPreview = ApiHelper.AT_LEAST_14;
    private       MoaActionList       mActions;
    private final ToolActionVO<Float> mToolAction;
    volatile boolean mIsRendering = false;
    /* temporary drawable */
    private FakeBitmapDrawable mPreviewSmallDrawable;
    /* temporary bitmap */
    private Bitmap             mPreviewSmallBitmap;

    public NativeEffectRangePanel(
        IAviaryController context, ToolEntry entry, ToolLoaderFactory.Tools type, String resourcesBaseName) {
        super(context, entry, type, resourcesBaseName);
        mFilter = ToolLoaderFactory.get(type);
        mToolAction = new ToolActionVO<Float>();
    }

    @Override
    public void onCreate(Bitmap bitmap, Bundle options) {
        super.onCreate(bitmap, options);
    }

    @Override
    public void onBitmapReplaced(Bitmap bitmap) {
        super.onBitmapReplaced(bitmap);

        if (isActive()) {
            applyFilter(0, false, false);
            setValue(50);
        }
    }

    @Override
    public void onActivate() {
        super.onActivate();

        mPreview = BitmapUtils.copy(mBitmap, Bitmap.Config.ARGB_8888);

        if (enableFastPreview) {
            mPreviewSmallBitmap = acquireBitmap(PREVIEW_FAKE_RATIO);
        }

        mPreviewSmallDrawable = new FakeBitmapDrawable(mPreview, mPreview.getWidth(), mPreview.getHeight());
        onPreviewChanged(mPreviewSmallDrawable, false, true);
        setIsChanged(false);

        if (hasOptions()) {
            final Bundle options = getOptions();
            if (options.containsKey(Constants.QuickLaunch.NUMERIC_VALUE)) {
                int value = options.getInt(Constants.QuickLaunch.NUMERIC_VALUE, 0);
                setValue(value);
            }
        }
    }

    @Override
    public boolean isRendering() {
        return mIsRendering;
    }

    @Override
    protected void onSliderStart(int value) {
        if (enableFastPreview) {
            onProgressStart();
        }
    }

    @Override
    protected void onSliderEnd(int value) {
        mLogger.info("onSliderEnd: " + value);

        if (enableFastPreview) {
            killCurrentTask(false);
            onProgressEnd();
        }

        value = (value - 50) * 2;
        applyFilter(value, !enableFastPreview, false);
    }

    @Override
    protected void onSliderChanged(int value, boolean fromUser) {
        mLogger.info("onSliderChanged: " + value + ", fromUser: " + fromUser);

        if (enableFastPreview || !fromUser) {
            value = (value - 50) * 2;
            if (null == mCurrentTask) {
                applyFilter(value, !fromUser, true);
            }
        }
    }

    @Override
    public void onDeactivate() {
        onProgressEnd();
        super.onDeactivate();
    }

    @Override
    protected void onDispose() {
        super.onDispose();

        if (null != mPreviewSmallBitmap && !mPreviewSmallBitmap.isRecycled()) {
            mPreviewSmallBitmap.recycle();
            mPreviewSmallBitmap = null;
        }
    }

    @Override
    protected void onGenerateResult() {
        mLogger.info("onGenerateResult: " + mIsRendering);

        if (mIsRendering) {
            GenerateResultTask task = new GenerateResultTask();
            task.execute();
        } else {
            onComplete(mPreview);
        }
    }

    @Override
    protected void onComplete(final Bitmap bitmap, final EditToolResultVO editResult) {
        editResult.setToolAction(mToolAction);
        editResult.setActionList(mActions);
        super.onComplete(bitmap, editResult);
    }

    @Override
    public boolean onBackPressed() {
        killCurrentTask(true);
        return super.onBackPressed();
    }

    @Override
    public void onCancelled() {
        killCurrentTask(true);
        mIsRendering = false;
        super.onCancelled();
    }

    boolean killCurrentTask(boolean endProgress) {
        if (mCurrentTask != null) {
            if (mCurrentTask.cancel(true)) {
                mIsRendering = false;
                if (endProgress) {
                    onProgressEnd();
                }
                return true;
            }
        }
        return false;
    }

    protected void applyFilter(float value, boolean showProgress, boolean isPreview) {
        mLogger.info("applyFilter: " + value);

        if (value == 0) {
            killCurrentTask(!enableFastPreview);
            BitmapUtils.copy(mBitmap, mPreview);
            mPreviewSmallDrawable.updateBitmap(mPreview, mPreview.getWidth(), mPreview.getHeight());
            onPreviewUpdated();
            mIsRendering = false;
            setIsChanged(false);
        } else {
            if (!enableFastPreview) {
                killCurrentTask(!enableFastPreview);
            }
            mIsRendering = true;
            mCurrentTask = new ApplyFilterTask(value, showProgress, isPreview);
            mCurrentTask.execute(mBitmap);
            setIsChanged(true);
        }
    }

    private Bitmap acquireBitmap(final int ratio) {
        Bitmap bitmap;
        bitmap = Bitmap.createBitmap(mBitmap.getWidth() / ratio, mBitmap.getHeight() / ratio, mBitmap.getConfig());
        BitmapUtils.copy(mBitmap, bitmap);
        return bitmap;
    }

    class ApplyFilterTask extends AviaryAsyncTask<Bitmap, Void, Bitmap> {
        MoaResult mResult;
        boolean   mShowProgress;
        IFilter   filter;
        Bitmap    mCurrentBitmap;
        boolean   isPreview;

        public ApplyFilterTask(float value, boolean showProgress, boolean isPreview) {
            this.isPreview = isPreview;
            this.mShowProgress = showProgress;
            if (null != mFilter) {
                filter = ToolLoaderFactory.get(getName());
                ((INativeRangeFilter) filter).setValue(value);
            }
        }

        @Override
        protected void doPreExecute() {
            if (null != filter) {
                if (mShowProgress) {
                    onProgressStart();
                }
            }
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            if (mResult != null) {
                mResult.cancel();
            }

            if (null != mCurrentBitmap) {
                if (!mCurrentBitmap.isRecycled()) {
                    mCurrentBitmap.recycle();
                }
            }
        }

        @Override
        protected Bitmap doInBackground(Bitmap... arg0) {
            if (isCancelled() || null == filter) {
                return null;
            }

            try {
                if (isPreview && null != mPreviewSmallBitmap) {
                    mCurrentBitmap = Bitmap.createBitmap(mPreviewSmallBitmap.getWidth(),
                                                         mPreviewSmallBitmap.getHeight(),
                                                         mPreviewSmallBitmap.getConfig());
                    mResult = ((INativeRangeFilter) filter).prepare(mPreviewSmallBitmap, mCurrentBitmap, 1, 1);
                } else {
                    mResult = ((INativeRangeFilter) filter).prepare(mBitmap, mPreview, 1, 1);
                }
                mResult.execute();

                if (isCancelled()) {
                    mLogger.warn("isCancelled... return null");
                    return null;
                }

                mToolAction.setValue(((INativeRangeFilter) filter).getValue().getValue());
                mActions = ((INativeRangeFilter) filter).getActions();
            } catch (Throwable t) {
                t.printStackTrace();
                return null;
            }

            if (isCancelled()) {
                return null;
            }
            return mResult.outputBitmap;
        }

        @Override
        protected void doPostExecute(Bitmap result) {
            mLogger.info("onPostExecute, isPreview: %b, result: %s", isPreview, result);
            if (!isActive()) {
                return;
            }

            if (mShowProgress) {
                onProgressEnd();
            }

            if (result != null && !isCancelled()) {
                mLogger.log("result size: %dx%d", result.getWidth(), result.getHeight());

                mPreviewSmallDrawable.updateBitmap(result, mBitmap.getWidth(), mBitmap.getHeight());
                onPreviewUpdated();
                if (!isPreview) {
                    setIsChanged(true);
                }
            } else {
                mLogger.warn("result == null || isCancelled");
            }

            if (!isPreview) {
                mIsRendering = false;
            }
            mCurrentTask = null;
        }
    }

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
                // mLogger.log( "waiting...." );
            }
            return null;
        }

        @Override
        protected void doPostExecute(Void result) {
            mLogger.info("GenerateResultTask::doPostExecute");

            if (getContext().getBaseActivity().isFinishing()) {
                return;
            }
            if (mProgress.isShowing()) {
                mProgress.dismiss();
            }
            onComplete(mPreview);
        }
    }
}
