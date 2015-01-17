package com.aviary.android.feather.sdk.panels;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.os.Bundle;

import com.aviary.android.feather.common.utils.SystemUtils;
import com.aviary.android.feather.common.utils.os.AviaryAsyncTask;
import com.aviary.android.feather.headless.filters.INativeRangeFilter;
import com.aviary.android.feather.headless.moa.Moa;
import com.aviary.android.feather.headless.moa.MoaActionList;
import com.aviary.android.feather.headless.moa.MoaResult;
import com.aviary.android.feather.library.Constants;
import com.aviary.android.feather.library.content.ToolEntry;
import com.aviary.android.feather.library.filters.ToolLoaderFactory;
import com.aviary.android.feather.library.services.IAviaryController;
import com.aviary.android.feather.library.utils.BitmapUtils;
import com.aviary.android.feather.library.vo.EditToolResultVO;
import com.aviary.android.feather.library.vo.ToolActionVO;
import com.aviary.android.feather.sdk.R;

import org.json.JSONException;

public class NativeEffectRangePanel extends SliderEffectPanel {

	ApplyFilterTask mCurrentTask;
	volatile boolean mIsRendering = false;
	boolean enableFastPreview;

	final ToolActionVO<Float> mToolAction;
	MoaActionList mActions;

	public NativeEffectRangePanel ( IAviaryController context, ToolEntry entry, ToolLoaderFactory.Tools type, String resourcesBaseName ) {
		super( context, entry, type, resourcesBaseName );
		mFilter = ToolLoaderFactory.get(type);
		mToolAction = new ToolActionVO<Float>();
	}

	@Override
	public void onCreate( Bitmap bitmap, Bundle options ) {
		super.onCreate( bitmap, options );
	}

	@Override
	public void onBitmapReplaced( Bitmap bitmap ) {
		super.onBitmapReplaced( bitmap );

		if (isActive()) {
			applyFilter(0, false);
			setValue(50);
		}
	}

	@Override
	public void onActivate() {
		super.onActivate();
		mPreview = BitmapUtils.copy( mBitmap, Bitmap.Config.ARGB_8888 );
		onPreviewChanged(mPreview, true, true);
		setIsChanged(false);

		if( hasOptions() ) {
			final Bundle options = getOptions();
			if( options.containsKey(Constants.QuickLaunch.NUMERIC_VALUE)) {
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
	protected void onSliderStart( int value ) {
		if ( enableFastPreview ) {
			onProgressStart();
		}
	}

	@Override
	protected void onSliderEnd( int value ) {
		mLogger.info( "onProgressEnd: " + value );

		value = ( value - 50 ) * 2;
		applyFilter( value, !enableFastPreview );

		if ( enableFastPreview ) {
			onProgressEnd();
		}
	}

	@Override
	protected void onSliderChanged( int value, boolean fromUser ) {
		mLogger.info( "onProgressChanged: " + value + ", fromUser: " + fromUser );

		if ( enableFastPreview || !fromUser ) {
			value = ( value - 50 ) * 2;
			applyFilter( value, !fromUser );
		}
	}

	@Override
	public void onDeactivate() {
		onProgressEnd();
		super.onDeactivate();
	}

	@Override
	protected void onGenerateResult() {
		mLogger.info( "onGenerateResult: " + mIsRendering );

		if ( mIsRendering ) {
			GenerateResultTask task = new GenerateResultTask();
			task.execute();
		} else {
			onComplete( mPreview );
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
		mLogger.info( "onBackPressed" );
		killCurrentTask();
		return super.onBackPressed();
	}

	@Override
	public void onCancelled() {
		killCurrentTask();
		mIsRendering = false;
		super.onCancelled();
	}

	boolean killCurrentTask() {
		if ( mCurrentTask != null ) {
			if( mCurrentTask.cancel( true ) ) {
				mIsRendering = false;
				onProgressEnd();
				return true;
			}
		}
		return false;
	}

	protected void applyFilter( float value, boolean showProgress ) {
		mLogger.info( "applyFilter: " + value );

		killCurrentTask();

		if ( value == 0 ) {
			BitmapUtils.copy(mBitmap, mPreview);
			onPreviewChanged(mPreview, false, true);
			mIsRendering = false;
			setIsChanged(false);
		} else {
			mIsRendering = true;
			mCurrentTask = new ApplyFilterTask( value, showProgress );
			mCurrentTask.execute( mBitmap );
			setIsChanged(true);
		}
	}

	class ApplyFilterTask extends AviaryAsyncTask<Bitmap, Void, Bitmap> {

		MoaResult mResult;
		boolean mShowProgress;

		public ApplyFilterTask ( float value, boolean showProgress ) {
			mShowProgress = showProgress;
			if( null != mFilter ) {
				((INativeRangeFilter) mFilter).setValue(value);
			}
		}

		@Override
		protected void PreExecute() {
			mLogger.info( "PreExecute" );
			if( null != mFilter ) {
				try {
					mResult = ( (INativeRangeFilter) mFilter ).prepare( mBitmap, mPreview, 1, 1 );
				} catch( JSONException e ) {
					e.printStackTrace();
				}

				if( mShowProgress ) {
					onProgressStart();
				}
			}
		}

		@Override
		protected void onCancelled() {
			super.onCancelled();
			mLogger.info( "onCancelled" );
			if ( mResult != null ) {
				mResult.cancel();
			}
		}

		@Override
		protected Bitmap doInBackground( Bitmap... arg0 ) {
			if ( isCancelled() || null == mFilter ) return null;

			try {
				mResult.execute();
				mToolAction.setValue(((INativeRangeFilter) mFilter).getValue().getValue());
				mActions = ((INativeRangeFilter) mFilter).getActions();

			} catch ( Exception exception ) {
				exception.printStackTrace();
				return null;
			}

			if ( isCancelled() ) return null;
			return mPreview;
		}

		@Override
		protected void PostExecute( Bitmap result ) {

			if ( !isActive() ) return;

			mLogger.info( "PostExecute" );

			if ( mShowProgress ) {
				onProgressEnd();
			}

			if ( result != null ) {
				if ( SystemUtils.isHoneyComb() ) {
					Moa.notifyPixelsChanged( mPreview );
				}
				onPreviewUpdated();
				// onPreviewChanged( mPreview, true );
			} else {
				BitmapUtils.copy( mBitmap, mPreview );
				onPreviewChanged( mPreview, false, true );
				setIsChanged( false );
			}
			mIsRendering = false;
			mCurrentTask = null;
		}
	}

	class GenerateResultTask extends AviaryAsyncTask<Void, Void, Void> {

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
