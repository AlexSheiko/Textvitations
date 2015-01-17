package com.aviary.android.feather.sdk.panels;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.aviary.android.feather.common.utils.SystemUtils;
import com.aviary.android.feather.common.utils.os.AviaryAsyncTask;
import com.aviary.android.feather.headless.moa.Moa;
import com.aviary.android.feather.library.Constants;
import com.aviary.android.feather.library.content.ToolEntry;
import com.aviary.android.feather.library.filters.EnhanceFilter;
import com.aviary.android.feather.library.filters.EnhanceFilter.Types;
import com.aviary.android.feather.library.filters.ToolLoaderFactory;
import com.aviary.android.feather.library.services.IAviaryController;
import com.aviary.android.feather.library.services.LocalDataService;
import com.aviary.android.feather.library.utils.BitmapUtils;
import com.aviary.android.feather.library.vo.ToolActionVO;
import com.aviary.android.feather.sdk.R;
import com.aviary.android.feather.sdk.widget.AviaryHighlightImageButton;
import com.aviary.android.feather.sdk.widget.AviaryHighlightImageButton.OnCheckedChangeListener;

import org.json.JSONException;

public class EnhanceEffectPanel extends AbstractOptionPanel implements OnCheckedChangeListener {

	// current rendering task
	private RenderTask mCurrentTask;

	// panel is renderding
	volatile boolean mIsRendering = false;

	private ToolLoaderFactory.Tools mFilterType;
	boolean enableFastPreview = false;
	private AviaryHighlightImageButton mButton1, mButton2, mButton3;
	private AviaryHighlightImageButton mCurrent;

	public EnhanceEffectPanel ( IAviaryController context, ToolEntry entry, ToolLoaderFactory.Tools type ) {
		super( context, entry );
		mFilterType = type;
	}

	@Override
	public void onCreate( Bitmap bitmap, Bundle options ) {
		super.onCreate( bitmap, options );

		ViewGroup panel = getOptionView();

		mButton1 = (AviaryHighlightImageButton) panel.findViewById( R.id.button1 );
		mButton1.setOnCheckedChangeListener( this );
		if ( mButton1.isChecked() ) mCurrent = mButton1;

		mButton2 = (AviaryHighlightImageButton) panel.findViewById( R.id.button2 );
		mButton2.setOnCheckedChangeListener( this );
		if ( mButton2.isChecked() ) mCurrent = mButton2;

		mButton3 = (AviaryHighlightImageButton) panel.findViewById( R.id.button3 );
		mButton3.setOnCheckedChangeListener( this );
		if ( mButton3.isChecked() ) mCurrent = mButton3;
	}

	@Override
	public void onActivate() {
		super.onActivate();
		mPreview = BitmapUtils.copy( mBitmap, Config.ARGB_8888 );

		LocalDataService dataService = getContext().getService( LocalDataService.class );
		enableFastPreview = dataService.getFastPreviewEnabled();

		if (hasOptions()) {
			final Bundle options = getOptions();
			final String stringValue = options.getString(Constants.QuickLaunch.STRING_VALUE);
			mLogger.log("stringValue: %s", stringValue);
			if (null != stringValue) {
				AviaryHighlightImageButton button = (AviaryHighlightImageButton) getOptionView().findViewWithTag(stringValue);
				if (null != button) {
					mLogger.log("button found: %s", button);
					button.setChecked(true);
					buttonClick(stringValue, true);
				}
			}
		}
	}

	@Override
	public void onDeactivate() {
		super.onDeactivate();

		mButton1.setOnCheckedChangeListener( null );
		mButton2.setOnCheckedChangeListener( null );
		mButton3.setOnCheckedChangeListener( null );
	}

	@Override
	public boolean isRendering() {
		return mIsRendering;
	}

	@Override
	public void onBitmapReplaced( Bitmap bitmap ) {
		super.onBitmapReplaced( bitmap );

		if ( isActive() ) {
			mButton1.setChecked( false );
			mButton2.setChecked( false );
			mButton3.setChecked( false );
		}
	}

	@Override
	public void onCheckedChanged( AviaryHighlightImageButton buttonView, boolean isChecked, boolean fromUser ) {

		mLogger.info("onCheckedChanged: %b, fromUser: %b", isChecked, fromUser);

		if (mCurrent != null && ! buttonView.equals(mCurrent)) {
			mCurrent.setChecked(false);
		}

		mCurrent = buttonView;

		if (! isActive() || ! isEnabled() || ! fromUser) return;

		final String tag = (String) buttonView.getTag();
		buttonClick(tag, isChecked);
	}

	private void buttonClick(final String tag, boolean isChecked) {
		mLogger.info("buttonClick: %s, %b", tag, isChecked);
		Types type = Types.HiDef;
		killCurrentTask();

		if (EnhanceFilter.ENHANCE_HIDEF.equals(tag)) {
			type = Types.HiDef;
		}
		else if (EnhanceFilter.ENHANCE_ILLUMINATE.equals(tag)) {
			type = Types.Illuminate;
		}
		else if (EnhanceFilter.ENHANCE_COLOR_FIX.equals(tag)) {
			type = Types.ColorFix;
		}

		if (! isChecked) {
			// restore the original image
			BitmapUtils.copy(mBitmap, mPreview);
			onPreviewChanged(mPreview, false, true);
			mEditResult.setActionList(null);
			mEditResult.setToolAction(null);
			mTrackingAttributes.clear();
			setIsChanged(false);
		}
		else {
			setIsChanged(true);

			if (type != null) {
				mIsRendering = true;
				mCurrentTask = new RenderTask();
				mCurrentTask.execute(type);

				mTrackingAttributes.put("name", type.name());
				getContext().getTracker().tagEvent("enhance: option_selected", "name", type.name());
			}
		}
	}

	@Override
	protected void onProgressStart() {
		if ( !enableFastPreview ) {
			onProgressModalStart();
			return;
		}
		super.onProgressStart();
	}

	@Override
	protected void onProgressEnd() {
		if ( !enableFastPreview ) {
			onProgressModalEnd();
			return;
		}
		super.onProgressEnd();
	}

	@Override
	protected ViewGroup generateOptionView( LayoutInflater inflater, ViewGroup parent ) {
		return (ViewGroup) inflater.inflate( R.layout.aviary_panel_enhance, parent, false );
	}

	@Override
	public boolean onBackPressed() {
		killCurrentTask();
		return super.onBackPressed();
	}

	@Override
	public void onCancelled() {
		killCurrentTask();
		mIsRendering = false;
		super.onCancelled();
	}

	@Override
	public boolean onCancel() {
		killCurrentTask();
		return super.onCancel();
	}

	private void killCurrentTask() {
		if ( mCurrentTask != null ) {
			synchronized ( mCurrentTask ) {
				mCurrentTask.cancel( true );
				mCurrentTask.renderFilter.stop();
				onProgressEnd();
			}
			mIsRendering = false;
			mCurrentTask = null;
		}
	}

	class RenderTask extends AviaryAsyncTask<Types, Void, Bitmap> {

		String mError;

		volatile EnhanceFilter renderFilter;

		public RenderTask () {
			renderFilter = (EnhanceFilter) ToolLoaderFactory.get(mFilterType);
			mError = null;
		}

		@Override
		protected void PreExecute() {
			onProgressStart();
		}

		@Override
		protected Bitmap doInBackground( Types... params ) {
			if ( isCancelled() ) return null;
			Bitmap result = null;
			Types type = params[0];
			renderFilter.setType( type );

			try {
				result = renderFilter.execute( mBitmap, mPreview, 1, 1 );
				mEditResult.setActionList(renderFilter.getActions());
				mEditResult.setToolAction(new ToolActionVO<String>(type.name()));

			} catch ( JSONException e ) {
				e.printStackTrace();
				mError = e.getMessage();
				return null;
			}

			if ( isCancelled() ) return null;
			return result;
		}

		@Override
		protected void PostExecute( Bitmap result ) {

			if ( !isActive() ) return;

			onProgressEnd();

			if ( isCancelled() ) return;

			if ( result != null ) {

				if ( SystemUtils.isHoneyComb() ) {
					Moa.notifyPixelsChanged( mPreview );
				}

				onPreviewChanged( mPreview, false, true );
			} else {
				if ( mError != null ) {
					onGenericError( mError, android.R.string.ok, null );
				}
			}

			mIsRendering = false;
			mCurrentTask = null;
		}

		@Override
		protected void onCancelled() {
			renderFilter.stop();
			super.onCancelled();
		}
	}

	@Override
	protected void onGenerateResult() {

		if ( mIsRendering ) {
			GenerateResultTask task = new GenerateResultTask();
			task.execute();
		} else {
			onComplete( mPreview );
		}
	}

	class GenerateResultTask extends AviaryAsyncTask<Void, Void, Void> {

		/** The m progress. */
		ProgressDialog mProgress = new ProgressDialog( getContext().getBaseContext() );

		@Override
		protected void PreExecute() {
			mProgress.setTitle( getContext().getBaseContext().getString( R.string.feather_loading_title ) );
			mProgress.setMessage( getContext().getBaseContext().getString( R.string.feather_effect_loading_message ) );
			mProgress.setIndeterminate( true );
			mProgress.setCancelable( false );
			mProgress.show();
		}

		@Override
		protected Void doInBackground( Void... params ) {

			mLogger.info( "GenerateResultTask::doInBackground", mIsRendering );

			while ( mIsRendering ) {
				// mLogger.log( "waiting...." );
			}

			return null;
		}

		@Override
		protected void PostExecute( Void result ) {

			mLogger.info( "GenerateResultTask::PostExecute" );

			if ( getContext().getBaseActivity().isFinishing() ) return;
			if ( mProgress.isShowing() ) mProgress.dismiss();
			onComplete( mPreview );
		}
	}
}
