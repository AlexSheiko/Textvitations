package com.aviary.android.feather.sdk.panels;

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;

import com.aviary.android.feather.common.utils.ApiHelper;
import com.aviary.android.feather.common.utils.os.AviaryAsyncTask;
import com.aviary.android.feather.headless.filters.IFilter;
import com.aviary.android.feather.headless.filters.INativeFilter;
import com.aviary.android.feather.headless.filters.NativeFilter;
import com.aviary.android.feather.headless.filters.impl.AdjustColorFilter;
import com.aviary.android.feather.headless.filters.impl.AdjustExposureFilter;
import com.aviary.android.feather.headless.filters.impl.AdjustSliderFilter;
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
import com.aviary.android.feather.sdk.widget.AviaryTextView;

public class ConsolidatedAdjustToolsPanel extends AbstractOptionPanel
    implements SeekBar.OnSeekBarChangeListener, View.OnClickListener {
    ApplyFilterTask mCurrentTask;
    boolean enableFastPreview = ApiHelper.AT_LEAST_14;
    MoaActionList mActions;
    volatile boolean mIsRendering = false;
    final ToolActionVO<Float> mToolAction;
    boolean mFirstLaunch = true;
    static final int PREVIEW_FAKE_RATIO = 3;
    /** Consolidation Fields * */
    ViewPager        mViewPager;
    ViewPagerAdapter mAdapter;
    /** Positions of the tools within the pager * */
    private static final int POSITION_TOOL_1 = 0;
    private static final int POSITION_TOOL_2 = 1;
    private static final int POSITION_TOOL_3 = 2;
    private static final int POSITION_TOOL_4 = 3;
    private static final int TOOL_COUNT = 4;
    private              int mCurrentToolId      = 0;
    View tool1, tool2, tool3, tool4;
    SparseIntArray mAdjustToolValues = new SparseIntArray();
    // this will change depending on which page we are on
    SeekBar mCurrentSeekBar;

    public ConsolidatedAdjustToolsPanel(IAviaryController context, ToolEntry entry) {
        super(context, entry);
        mToolAction = new ToolActionVO<Float>();
    }

    /**
     * Selects a tool and deselects all others
     *
     * @param toolId
     */
    private void setToolSelected(int toolId) {

        mViewPager.setCurrentItem(toolId);

        switch (toolId) {
            case POSITION_TOOL_1:
                tool1.setSelected(true);
                break;
            case POSITION_TOOL_2:
                tool2.setSelected(true);
                break;
            case POSITION_TOOL_3:
                tool3.setSelected(true);
                break;
            case POSITION_TOOL_4:
                tool4.setSelected(true);
                break;
            default:
                break;
        }

        if (toolId != mCurrentToolId) {
            switch (mCurrentToolId) {
                case POSITION_TOOL_1:
                    tool1.setSelected(false);
                    break;
                case POSITION_TOOL_2:
                    tool2.setSelected(false);
                    break;
                case POSITION_TOOL_3:
                    tool3.setSelected(false);
                    break;
                case POSITION_TOOL_4:
                    tool4.setSelected(false);
                    break;
                default:
                    break;
            }
        }

        mCurrentToolId = toolId;

    }

    @Override
    public void onCreate(Bitmap bitmap, Bundle options) {
        super.onCreate(bitmap, options);

        mAdapter = new ViewPagerAdapter(getContext().getBaseContext());

        mViewPager = (ViewPager) getOptionView().findViewById(R.id.aviary_pager);
        mViewPager.setAdapter(mAdapter);
        mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mLogger.info("onPageSelected: %d", position);
                mCurrentSeekBar = (SeekBar) mViewPager.findViewWithTag(position);
                changeSeekBarToCurrent(position);
            }
        });

        tool1 = getOptionView().findViewById(R.id.tool1);
        tool2 = getOptionView().findViewById(R.id.tool2);
        tool3 = getOptionView().findViewById(R.id.tool3);
        tool4 = getOptionView().findViewById(R.id.tool4);

        setToolNames();

        tool1.setOnClickListener(this);
        tool2.setOnClickListener(this);
        tool3.setOnClickListener(this);
        tool4.setOnClickListener(this);

        mAdjustToolValues.put(POSITION_TOOL_1, 50);
        mAdjustToolValues.put(POSITION_TOOL_2, 50);
        mAdjustToolValues.put(POSITION_TOOL_4, 50);
        mAdjustToolValues.put(POSITION_TOOL_3, 50);

        setToolSelected(POSITION_TOOL_1);
        changeSeekBarToCurrent(POSITION_TOOL_1);
    }

    private void setToolNames() {
        if (getName() == ToolLoaderFactory.Tools.LIGHTING) {
            ((AviaryTextView) tool1).setText(R.string.feather_brightness);
            ((AviaryTextView) tool2).setText(R.string.feather_contrast);
            ((AviaryTextView) tool3).setText(R.string.feather_tool_highlight);
            ((AviaryTextView) tool4).setText(R.string.feather_tool_shadow);
        }

        if (getName() == ToolLoaderFactory.Tools.COLOR) {
            ((AviaryTextView) tool1).setText(R.string.feather_saturation);
            ((AviaryTextView) tool2).setText(R.string.feather_tool_temperature);
            ((AviaryTextView) tool3).setText(R.string.feather_tool_tint);
            ((AviaryTextView) tool4).setText(R.string.feather_tool_fade);
        }
    }

    private void updatedToolValue(int toolID, int value) {
        mLogger.log("new value tag, value: " + toolID + ", " + value);
        mAdjustToolValues.put(toolID, value);
    }

    private void changeSeekBarToCurrent(int position) {

        if (mCurrentSeekBar == null) {
            return;
        }

        switch (position) {
            case POSITION_TOOL_1:
                mCurrentSeekBar.setProgress(mAdjustToolValues.get(POSITION_TOOL_1));
                break;
            case POSITION_TOOL_3:
                mCurrentSeekBar.setProgress(mAdjustToolValues.get(POSITION_TOOL_3));
                break;
            case POSITION_TOOL_2:
                mCurrentSeekBar.setProgress(mAdjustToolValues.get(POSITION_TOOL_2));
                break;
            case POSITION_TOOL_4:
                mCurrentSeekBar.setProgress(mAdjustToolValues.get(POSITION_TOOL_4));
                break;
            default:
                break;
        }

    }

    protected void setValue(int value) {
        mCurrentSeekBar.setProgress(value);
    }

    @Override
    protected ViewGroup generateOptionView(final LayoutInflater inflater, final ViewGroup parent) {
        return (ViewGroup) inflater.inflate(R.layout.aviary_panel_adjustment_consolidation, parent, false);
    }

    @Override
    public void onBitmapReplaced(Bitmap bitmap) {
        super.onBitmapReplaced(bitmap);

        if (isActive()) {
            applyFilter(false, false);
            setValue(50);
        }
    }

    @Override
    public void onActivate() {
        super.onActivate();

        mPreview = BitmapUtils.copy(mBitmap, Bitmap.Config.ARGB_8888);
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
    public final void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        onSliderChanged(progress, fromUser);
    }

    @Override
    public final void onStartTrackingTouch(SeekBar seekBar) {
        onSliderStart(seekBar.getProgress());
    }

    @Override
    public final void onStopTrackingTouch(SeekBar seekBar) {
        onSliderEnd(seekBar.getProgress());
    }

    protected void onSliderStart(int value) {
        if (enableFastPreview) {
            onProgressStart();
        }
    }

    protected void onSliderEnd(int value) {
        mLogger.info("onSliderEnd: " + value);

        if (enableFastPreview) {
            killCurrentTask(false);
            onProgressEnd();
        }

        applyFilter(!enableFastPreview, false);
    }

    protected void onSliderChanged(int value, boolean fromUser) {
        mLogger.info("onSliderChanged: " + value + ", fromUser: " + fromUser);

        updatedToolValue(mViewPager.getCurrentItem(), value);

        if (enableFastPreview || !fromUser) {
            if (null == mCurrentTask) {
                applyFilter(!fromUser, true);
            }
        }
    }

    @Override
    public void onDeactivate() {
        onProgressEnd();
        super.onDeactivate();
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

        int itemsApplied = 0;
        for (int i = 0; i < TOOL_COUNT; i++) {
            if (convertValue(mAdjustToolValues.get(i)) != 0) {
                mTrackingAttributes.put(getToolName(i), "true");
                itemsApplied++;
            } else {
                mTrackingAttributes.put(getToolName(i), "false");
            }
        }

        mTrackingAttributes.put("item_count", String.valueOf(itemsApplied));

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

    protected void applyFilter(boolean showProgress, boolean isPreview) {
        mLogger.info("applyFilter(showProgress:%b, isPreview:%b)", showProgress, isPreview);
        if (!enableFastPreview) {
            killCurrentTask(!enableFastPreview);
        }
        mIsRendering = true;
        mCurrentTask = new ApplyFilterTask(showProgress, isPreview);
        mCurrentTask.execute(mBitmap);
        setIsChanged(true);
    }

    double convertValue(float value) {
        return (value - 50) * 2;
    }

    class ApplyFilterTask extends AviaryAsyncTask<Bitmap, Void, Bitmap> {
        MoaResult mResult;
        boolean   mShowProgress;
        IFilter   filter;
        Bitmap    mCurrentBitmap;
        boolean   isPreview;

        public ApplyFilterTask(boolean showProgress, boolean isPreview) {
            this.isPreview = isPreview;
            this.mShowProgress = showProgress;
            filter = ToolLoaderFactory.get(getName());

            if (filter != null) {
                ((AdjustSliderFilter) filter).setAdjustTool(0, convertValue(mAdjustToolValues.get(POSITION_TOOL_1)));
                ((AdjustSliderFilter) filter).setAdjustTool(1, convertValue(mAdjustToolValues.get(POSITION_TOOL_2)));
                ((AdjustSliderFilter) filter).setAdjustTool(2, convertValue(mAdjustToolValues.get(POSITION_TOOL_3)));
                ((AdjustSliderFilter) filter).setAdjustTool(3, convertValue(mAdjustToolValues.get(POSITION_TOOL_4)));
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

        private Bitmap acquireBitmap(boolean fake) {
            Bitmap bitmap;
            if (fake) {
                bitmap = Bitmap.createBitmap(mBitmap.getWidth() / PREVIEW_FAKE_RATIO,
                                             mBitmap.getHeight() / PREVIEW_FAKE_RATIO,
                                             mBitmap.getConfig());
                BitmapUtils.copy(mBitmap, bitmap);
            } else {
                bitmap = Bitmap.createBitmap(mBitmap.getWidth(), mBitmap.getHeight(), mBitmap.getConfig());
            }
            return bitmap;
        }

        @Override
        protected Bitmap doInBackground(Bitmap... arg0) {
            if (isCancelled() || null == filter) {
                return null;
            }

            mCurrentBitmap = acquireBitmap(isPreview);

            if (isCancelled()) {
                return null;
            }

            try {
                if (isPreview) {
                    mResult = ((INativeFilter) filter).prepare(mCurrentBitmap, mCurrentBitmap, 1, 1);
                } else {
                    mResult = ((INativeFilter) filter).prepare(mBitmap, mCurrentBitmap, 1, 1);
                }
                mResult.execute();

                if (isCancelled()) {
                    return null;
                }

                mActions = ((NativeFilter) filter).getActions();
            } catch (Throwable t) {
                t.printStackTrace();
                return null;
            }

            if (isCancelled()) {
                return null;
            }
            return mCurrentBitmap;
        }

        @Override
        protected void doPostExecute(Bitmap result) {
            mLogger.info("onPostExecute, isPreview: %b", isPreview);
            if (!isActive()) {
                return;
            }

            if (mShowProgress) {
                onProgressEnd();
            }

            if (result != null && !isCancelled()) {
                if (isPreview) {
                    FakeBitmapDrawable drawable = new FakeBitmapDrawable(result, mBitmap.getWidth(), mBitmap.getHeight());
                    onPreviewChanged(drawable, false, true);
                } else {
                    onPreviewChanged(result, false, true);
                }
                setIsChanged(true);
                mPreview = result;
            } else {
                BitmapUtils.copy(mBitmap, mPreview);
                onPreviewChanged(mPreview, false, true);
                setIsChanged(false);
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
            mLogger.info("GenerateResultTask::PostExecute");

            if (getContext().getBaseActivity().isFinishing()) {
                return;
            }
            if (mProgress.isShowing()) {
                mProgress.dismiss();
            }
            onComplete(mPreview);
        }
    }

    @Override
    public void onClick(final View view) {
        if (view == null) {
            return;
        }

        int id = view.getId();

        if (id == R.id.aviary_button_plus) {
            increaseValue();
        } else if (id == R.id.aviary_button_minus) {
            decreaseValue();
        } else if (id == R.id.tool1) {
            setToolSelected(POSITION_TOOL_1);
            getContext().getTracker().tagEvent("adjust: option_selected", "name", getToolName(POSITION_TOOL_1));
        } else if (id == R.id.tool2) {
            setToolSelected(POSITION_TOOL_2);
            getContext().getTracker().tagEvent("adjust: option_selected", "name", getToolName(POSITION_TOOL_2));
        } else if (id == R.id.tool3) {
            setToolSelected(POSITION_TOOL_3);
            getContext().getTracker().tagEvent("adjust: option_selected", "name", getToolName(POSITION_TOOL_3));
        } else if (id == R.id.tool4) {
            setToolSelected(POSITION_TOOL_4);
            getContext().getTracker().tagEvent("adjust: option_selected", "name", getToolName(POSITION_TOOL_4));
        }
    }

    protected void decreaseValue() {
        if (mCurrentSeekBar == null) {
            return;
        }
        updatedToolValue((Integer) mCurrentSeekBar.getTag(), mCurrentSeekBar.getProgress() - 1);
        mCurrentSeekBar.setProgress(mCurrentSeekBar.getProgress() - 1);
        onSliderEnd(mCurrentSeekBar.getProgress());
    }

    protected void increaseValue() {
        if (mCurrentSeekBar == null) {
            return;
        }
        updatedToolValue((Integer) mCurrentSeekBar.getTag(), mCurrentSeekBar.getProgress() + 1);
        mCurrentSeekBar.setProgress(mCurrentSeekBar.getProgress() + 1);
        onSliderEnd(mCurrentSeekBar.getProgress());
    }

    class ViewPagerAdapter extends PagerAdapter {
        final LayoutInflater inflater;

        ViewPagerAdapter(Context context) {
            this.inflater = LayoutInflater.from(context);
        }

        @Override
        public int getCount() {
            return 4;
        }

        @Override
        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public void destroyItem(ViewGroup collection, int position, Object view) {
            collection.removeView((View) view);
        }

        @Override
        public Object instantiateItem(final ViewGroup collection, final int position) {

            final View view = inflater.inflate(R.layout.aviary_adjust_seekbar, collection, false);

            final SeekBar seekBar = (SeekBar) view.findViewById(R.id.aviary_seekbar);

            View buttonPlus = view.findViewById(R.id.aviary_button_plus);
            View buttonMinus = view.findViewById(R.id.aviary_button_minus);

            buttonMinus.setOnClickListener(ConsolidatedAdjustToolsPanel.this);
            buttonPlus.setOnClickListener(ConsolidatedAdjustToolsPanel.this);

            switch (position) {
                case POSITION_TOOL_1:
                    seekBar.setProgress(mAdjustToolValues.get(POSITION_TOOL_1));
                    if (mFirstLaunch) {
                        mCurrentSeekBar = seekBar;
                        mFirstLaunch = false;
                    }
                    break;
                case POSITION_TOOL_3:
                    seekBar.setProgress(mAdjustToolValues.get(POSITION_TOOL_3));
                    break;
                case POSITION_TOOL_2:
                    seekBar.setProgress(mAdjustToolValues.get(POSITION_TOOL_2));
                    break;
                case POSITION_TOOL_4:
                    seekBar.setProgress(mAdjustToolValues.get(POSITION_TOOL_4));
                    break;
                default:
                    break;
            }

            seekBar.setOnSeekBarChangeListener(ConsolidatedAdjustToolsPanel.this);
            seekBar.setTag(position);

            collection.addView(view, 0);
            return view;
        }

    }

    private String getToolName(int position) {
        if (getName() == ToolLoaderFactory.Tools.LIGHTING) {
            return AdjustExposureFilter.getToolName(position);
        }

        if (getName() == ToolLoaderFactory.Tools.COLOR) {
            return AdjustColorFilter.getToolName(position);
        }

        return "invalid";
    }

}
