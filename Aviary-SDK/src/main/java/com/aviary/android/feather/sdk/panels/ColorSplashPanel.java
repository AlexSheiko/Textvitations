package com.aviary.android.feather.sdk.panels;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import com.aviary.android.feather.common.utils.os.AviaryAsyncTask;
import com.aviary.android.feather.headless.filters.NativeToolFilter;
import com.aviary.android.feather.headless.filters.NativeToolFilter.BrushMode;
import com.aviary.android.feather.headless.moa.MoaActionFactory;
import com.aviary.android.feather.headless.moa.MoaActionList;
import com.aviary.android.feather.library.content.ToolEntry;
import com.aviary.android.feather.library.filters.ToolLoaderFactory;
import com.aviary.android.feather.library.services.ConfigService;
import com.aviary.android.feather.library.services.IAviaryController;
import com.aviary.android.feather.library.utils.BitmapUtils;
import com.aviary.android.feather.library.utils.UIConfiguration;
import com.aviary.android.feather.library.vo.ToolActionVO;
import com.aviary.android.feather.sdk.R;
import com.aviary.android.feather.sdk.widget.AviaryHighlightImageButton;
import com.aviary.android.feather.sdk.widget.AviaryHighlightImageButton.OnCheckedChangeListener;
import com.aviary.android.feather.sdk.widget.ImageViewSpotDraw;
import com.aviary.android.feather.sdk.widget.ImageViewSpotDraw.OnDrawListener;
import com.aviary.android.feather.sdk.widget.ImageViewSpotDraw.TouchMode;

import java.util.Locale;

import it.sephiroth.android.library.imagezoom.ImageViewTouchBase;
import it.sephiroth.android.library.imagezoom.ImageViewTouchBase.DisplayType;

/**
 * The Class SpotDrawPanel.
 */
public class ColorSplashPanel extends AbstractContentPanel implements OnDrawListener, OnCheckedChangeListener, OnClickListener {

	private AviaryHighlightImageButton mLensButton;
	private BackgroundDrawThread mBackgroundDrawThread;
	private NativeToolFilter mFilter;
	private AviaryHighlightImageButton mSmart, mErase, mFree;
	private AviaryHighlightImageButton mCurrent;
	private View mDisabledStatusView;
	private BrushMode mBrushType = BrushMode.Free;
	private final ToolActionVO<String> mToolAction;

	static float BRUSH_MULTIPLIER = 1.5f;

	Handler mThreadHandler = new Handler( new Handler.Callback() {
		
		@Override
		public boolean handleMessage( Message msg ) {
			
			switch( msg.what ) {
				case PROGRESS_START:
					onProgressStart();
					break;
					
				case PROGRESS_END:
					onProgressEnd();
					break;
					
				case PREVIEW_BITMAP_UPDATED:
					if( isActive() && null != mImageView ) {
						mImageView.postInvalidate();
					}
					break;

				case BackgroundDrawThread.PREVIEW_INITIALIZED:
					if( isActive() ) {
						setIsChanged( true );
					}
					break;
			}
			return false;
		}
	});

	public ColorSplashPanel ( IAviaryController context, ToolEntry entry ) {
		super( context, entry );
		mToolAction = new ToolActionVO<String>(BrushMode.None.name());
	}

	@Override
	public void onCreate( Bitmap bitmap, Bundle options ) {
		super.onCreate( bitmap, options );
		ConfigService config = getContext().getService( ConfigService.class );

		int brushSize = config.getDimensionPixelSize( R.dimen.aviary_color_splash_brush_size );

		mLensButton = (AviaryHighlightImageButton) getContentView().findViewById( R.id.aviary_lens_button );
		mFree = (AviaryHighlightImageButton) getOptionView().findViewById( R.id.aviary_button1 );
		mSmart = (AviaryHighlightImageButton) getOptionView().findViewById( R.id.aviary_button2 );
		mErase = (AviaryHighlightImageButton) getOptionView().findViewById( R.id.aviary_button3 );

		mImageView = (ImageViewSpotDraw) getContentView().findViewById( R.id.image );
		( (ImageViewSpotDraw) mImageView ).setBrushSize( (int) ( brushSize * BRUSH_MULTIPLIER ) );
		( (ImageViewSpotDraw) mImageView ).setDrawLimit( 0.0 );
		( (ImageViewSpotDraw) mImageView ).setPaintEnabled( false );
		( (ImageViewSpotDraw) mImageView ).setDisplayType( DisplayType.FIT_IF_BIGGER );

		mDisabledStatusView = getOptionView().findViewById( R.id.aviary_disable_status );

		// initialize the filter
		mFilter = createFilter();
		// init the background thread
		mBackgroundDrawThread = new BackgroundDrawThread( "draw-thread", Thread.NORM_PRIORITY, mFilter, mThreadHandler, BRUSH_MULTIPLIER );
	}

	@Override
	public void onActivate() {
		super.onActivate();

		mFree.setOnCheckedChangeListener( this );
		if ( mFree.isChecked() ) mCurrent = mFree;

		mSmart.setOnCheckedChangeListener( this );
		if ( mSmart.isChecked() ) mCurrent = mSmart;

		mErase.setOnCheckedChangeListener( this );
		if ( mErase.isChecked() ) mCurrent = mErase;

		// create the preview bitmap
		mPreview = BitmapUtils.copy( mBitmap, Config.ARGB_8888 );
		
		// show the previewbitmap
		( (ImageViewSpotDraw) mImageView ).setOnDrawStartListener( this );
		mImageView.setDisplayType( DisplayType.FIT_IF_BIGGER );
		mImageView.setImageBitmap( mPreview, null, ImageViewTouchBase.ZOOM_INVALID, UIConfiguration.IMAGE_VIEW_MAX_ZOOM );		
		
		// start the background thread
		mBackgroundDrawThread.start( mBitmap, mPreview );

		mLensButton.setOnClickListener( this );

		getContentView().setVisibility( View.VISIBLE );
		contentReady();
	}

	@Override
	protected void onDispose() {
		mBackgroundDrawThread = null;
		mContentReadyListener = null;
		mThreadHandler = null;

		// dispose the filter
		mFilter.dispose();
		super.onDispose();
	}

	@Override
	public void onClick( View v ) {
		final int id = v.getId();

		if ( id == mLensButton.getId() ) {
			setSelectedTool( ( (ImageViewSpotDraw) mImageView ).getDrawMode() == TouchMode.DRAW ? TouchMode.IMAGE : TouchMode.DRAW );
		}
	}

	@Override
	public void onCheckedChanged( AviaryHighlightImageButton buttonView, boolean isChecked, boolean fromUser ) {
		if ( mCurrent != null && !buttonView.equals( mCurrent ) ) {
			mCurrent.setChecked( false );
		}
		mCurrent = buttonView;

		if ( fromUser && isChecked ) {
			final int id = buttonView.getId();

			if ( id == mFree.getId() ) {
				mBrushType = BrushMode.Free;
				getContext().getTracker().tagEvent( ToolLoaderFactory.Tools.SPLASH.name().toLowerCase( Locale.US ) + ": free_color_selected" );
			} else if ( id == mSmart.getId() ) {
				mBrushType = BrushMode.Smart;
				getContext().getTracker().tagEvent( ToolLoaderFactory.Tools.SPLASH.name().toLowerCase( Locale.US ) + ": smart_color_selected" );
			} else if ( id == mErase.getId() ) {
				mBrushType = BrushMode.Erase;
				getContext().getTracker().tagEvent( ToolLoaderFactory.Tools.SPLASH.name().toLowerCase( Locale.US ) + ": eraser_selected" );
			}

			if ( ( (ImageViewSpotDraw) mImageView ).getDrawMode() != TouchMode.DRAW ) {
				setSelectedTool( TouchMode.DRAW );
			}
		}
	}

	private void setSelectedTool( TouchMode which ) {
		( (ImageViewSpotDraw) mImageView ).setDrawMode( which );
		mLensButton.setSelected( which == TouchMode.IMAGE );
		setPanelEnabled( which != TouchMode.IMAGE );
	}

	@Override
	public void onDeactivate() {

		mFree.setOnCheckedChangeListener( this );
		mSmart.setOnCheckedChangeListener( this );
		mErase.setOnCheckedChangeListener( this );
		mLensButton.setOnClickListener( null );

		( (ImageViewSpotDraw) mImageView ).setOnDrawStartListener( null );

		if ( mBackgroundDrawThread != null ) {

			mBackgroundDrawThread.clear();

			if ( mBackgroundDrawThread.isAlive() ) {
				mBackgroundDrawThread.quit();
				while ( mBackgroundDrawThread.isAlive() ) {
					mLogger.log( "isAlive..." );
					// wait...
				}
			}
		}
		onProgressEnd();
		super.onDeactivate();
	}

	@Override
	public void onDestroy() {
		mImageView.clear();
		super.onDestroy();
	}

	@Override
	public void onDrawStart( float[] points, float radius ) {
		radius = Math.max( 1, radius );
		mBackgroundDrawThread.pathStart( ( radius / BRUSH_MULTIPLIER ), points, mBrushType );
		mToolAction.setValue(mBrushType.name());
		setIsChanged(true);
	}

	@Override
	public void onDrawing( float[] points, float radius ) {
		mBackgroundDrawThread.lineTo( points );
	}

	@Override
	public void onDrawEnd() {
		mBackgroundDrawThread.pathEnd();
	}

	@Override
	protected void onGenerateResult() {
		if ( mBackgroundDrawThread.isAlive() && !mBackgroundDrawThread.isCompleted() ) {
			mBackgroundDrawThread.finish();
			GenerateResultTask task = new GenerateResultTask();
			task.execute();
		} else {
			onComplete( mPreview, mFilter.getActions() );
		}
	}

	private void onComplete(final Bitmap bitmap, final MoaActionList actions) {
		mEditResult.setActionList(actions);
		mEditResult.setToolAction(mToolAction);
		onComplete(bitmap);
	}

	public void setPanelEnabled( boolean value ) {

		if ( mOptionView != null ) {
			if ( value != mOptionView.isEnabled() ) {
				mOptionView.setEnabled( value );

				if ( value ) {
					getContext().restoreToolbarTitle();
				} else {
					getContext().setToolbarTitle( R.string.feather_zoom_mode );
				}

				mDisabledStatusView.setVisibility( value ? View.INVISIBLE : View.VISIBLE );
			}
		}
	}

	public boolean getPanelEnabled() {
		if ( mOptionView != null ) {
			return mOptionView.isEnabled();
		}
		return false;
	}

	protected NativeToolFilter createFilter() {
		return (NativeToolFilter) ToolLoaderFactory.get(ToolLoaderFactory.Tools.SPLASH);
	}

	@SuppressLint ("InflateParams")
	@Override
	protected View generateContentView( LayoutInflater inflater ) {
		return inflater.inflate( R.layout.aviary_content_spot_draw, null );
	}

	@Override
	protected ViewGroup generateOptionView( LayoutInflater inflater, ViewGroup parent ) {
		return (ViewGroup) inflater.inflate( R.layout.aviary_panel_colorsplash, parent, false );
	}

	/**
	 * GenerateResultTask is used when the background draw operation is still running.
	 * Just wait until the draw operation completed.
	 */
	class GenerateResultTask extends AviaryAsyncTask<Void, Void, MoaActionList> {

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
		protected MoaActionList doInBackground(Void... params) {

			MoaActionList actionlist = MoaActionFactory.actionList();

			if (mBackgroundDrawThread != null) {

				while (mBackgroundDrawThread != null && ! mBackgroundDrawThread.isCompleted()) {
					mLogger.log("waiting.... " + mBackgroundDrawThread.getQueueSize());
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}

			actionlist.add(mFilter.getActions().get(0));
			return actionlist;
		}

		@Override
		protected void PostExecute(MoaActionList result) {

			if (getContext().getBaseActivity().isFinishing()) return;

			if (mProgress.isShowing()) {
				try {
					mProgress.dismiss();
				} catch (IllegalArgumentException e) {}
			}
			onComplete(mPreview, result);
		}
	}
}
