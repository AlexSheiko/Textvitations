package com.aviary.android.feather.sdk.panels;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.Toast;

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
import com.aviary.android.feather.sdk.graphics.PreviewSpotDrawable;
import com.aviary.android.feather.sdk.overlays.AviaryOverlay;
import com.aviary.android.feather.sdk.overlays.BlemishOutOverlay;
import com.aviary.android.feather.sdk.overlays.BlemishOverlay;
import com.aviary.android.feather.sdk.utils.UIUtils;
import com.aviary.android.feather.sdk.widget.AviaryAdapterView;
import com.aviary.android.feather.sdk.widget.AviaryGallery;
import com.aviary.android.feather.sdk.widget.AviaryGallery.OnItemsScrollListener;
import com.aviary.android.feather.sdk.widget.AviaryHighlightImageButton;
import com.aviary.android.feather.sdk.widget.ImageViewSpotSingleTap;
import com.aviary.android.feather.sdk.widget.ImageViewSpotSingleTap.TouchMode;

import it.sephiroth.android.library.imagezoom.ImageViewTouchBase;
import it.sephiroth.android.library.imagezoom.ImageViewTouchBase.DisplayType;

public class BlemishPanel extends AbstractContentPanel implements ImageViewSpotSingleTap.OnTapListener, OnClickListener, OnItemsScrollListener {

	static float BRUSH_MULTIPLIER = 1.5f;

	private BrushMode mBrushType = BrushMode.Free;
	private BackgroundDrawThread mBackgroundDrawThread;
	private NativeToolFilter mFilter;
	private AviaryOverlay mOverlay;
	private AviaryOverlay mCloseOverlay;

	Handler mThreadHandler = new Handler( new Handler.Callback() {

		@Override
		public boolean handleMessage ( Message msg ) {

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
			}
			return false;
		}
	} );

	protected int mBrushSize;
	protected ToolLoaderFactory.Tools mFilterType;
	protected AviaryGallery mGallery;
	protected int[] mBrushSizes;
	protected int mSelectedPosition = - 1;
	protected AviaryHighlightImageButton mLensButton;
	private View mDisabledStatusView;
	String mSizeContentDescription;

	protected Toast mToast;
	protected PreviewSpotDrawable mDrawable;

	float minRadiusSize;
	float maxRadiusSize;
	private int mBrushSizeIndex;
	private int minBrushSize;
	private int maxBrushSize;

	private void showSizePreview ( int size ) {
		if( ! isActive() ) {
			return;
		}
		updateSizePreview( size );
	}

	private void updateSizePreview ( int size ) {
		if( ! isActive() ) {
			return;
		}

		if( null != mToast ) {
			mDrawable.setFixedRadius( size );
			mToast.show();
		}
	}

	public BlemishPanel ( IAviaryController context, ToolEntry entry, ToolLoaderFactory.Tools filter_type ) {
		super( context, entry );
		mFilterType = filter_type;
	}

	@Override
	public void onCreate ( Bitmap bitmap, Bundle options ) {
		super.onCreate( bitmap, options );

		ConfigService config = getContext().getService( ConfigService.class );

		mBrushSizeIndex = config.getInteger( R.integer.aviary_spot_brush_index );
		mBrushSizes = config.getSizeArray( R.array.aviary_single_tap_spot_brush_sizes );
		mBrushSize = mBrushSizes[mBrushSizeIndex];

		minBrushSize = mBrushSizes[0];
		maxBrushSize = mBrushSizes[mBrushSizes.length - 1];

		minRadiusSize = config.getInteger( R.integer.aviary_spot_gallery_item_min_size ) / 100f;
		maxRadiusSize = config.getInteger( R.integer.aviary_spot_gallery_item_max_size ) / 100f;

		mLensButton = (AviaryHighlightImageButton) getContentView().findViewById( R.id.aviary_lens_button );
		mSizeContentDescription = config.getString( R.string.feather_acc_size );

		// setup the gallery view
		mGallery = (AviaryGallery) getOptionView().findViewById( R.id.aviary_gallery );
		mGallery.setDefaultPosition( mBrushSizeIndex );
		mGallery.setAutoSelectChild( true );
		mGallery.setCallbackDuringFling( false );
		mGallery.setAdapter( new GalleryAdapter( getContext().getBaseContext(), mBrushSizes ) );
		mSelectedPosition = mBrushSizeIndex;

		// setup the imageview
		mImageView = (ImageViewSpotSingleTap) getContentView().findViewById( R.id.image );
		mImageView.setDisplayType( DisplayType.FIT_IF_BIGGER );
		((ImageViewSpotSingleTap) mImageView).setBrushSize( mBrushSize );

		mDisabledStatusView = getOptionView().findViewById( R.id.aviary_disable_status );

		// create the filter
		mFilter = createFilter( mFilterType );

		// init the background thread
		mBackgroundDrawThread = new BackgroundDrawThread( "draw-thread", Thread.NORM_PRIORITY, mFilter, mThreadHandler, BRUSH_MULTIPLIER );
		mBackgroundDrawThread.setSingleTapAllowed( true );
		mBackgroundDrawThread.setRegisterStrokeInitParams( true );
	}

	@Override
	public void onActivate () {
		super.onActivate();

		mToast = makeToast();

		disableHapticIsNecessary( mGallery );

		mPreview = BitmapUtils.copy( mBitmap, Config.ARGB_8888 );

		mLensButton.setOnClickListener( this );
		mGallery.setOnItemsScrollListener( this );

		( (ImageViewSpotSingleTap) mImageView ).setOnTapListener( this );
		mImageView.setDisplayType( DisplayType.FIT_IF_BIGGER );
		mImageView.setImageBitmap( mPreview, null, ImageViewTouchBase.ZOOM_INVALID, UIConfiguration.IMAGE_VIEW_MAX_ZOOM );

		mBackgroundDrawThread.start( mPreview );

		getContentView().setVisibility( View.VISIBLE );
		contentReady();

		// create and show the blemish overlay tutorial
		if( AviaryOverlay.shouldShow( getContext(), AviaryOverlay.ID_BLEMISH )) {
			mOverlay = new BlemishOverlay( getContext().getBaseContext(), R.style.AviaryWidget_Overlay_Blemish, mImageView, mLensButton );
			mOverlay.showDelayed( 100 );
		}

	}

	@Override
	protected void onDispose () {
		mBackgroundDrawThread = null;
		mThreadHandler = null;
		mContentReadyListener = null;
		mFilter.dispose();
		super.onDispose();
	}

	@Override
	public void onClick ( View v ) {
		final int id = v.getId();

		if( id == mLensButton.getId() ) {
			setSelectedTool( ( (ImageViewSpotSingleTap) mImageView ).getDrawMode() == TouchMode.DRAW ? TouchMode.IMAGE : TouchMode.DRAW );
		}
	}

	private void setSelectedTool ( TouchMode which ) {
		( (ImageViewSpotSingleTap) mImageView ).setDrawMode( which );
		mLensButton.setSelected( which == TouchMode.IMAGE );
		setPanelEnabled( which != TouchMode.IMAGE );
	}

	@Override
	public void onDeactivate () {
		mLensButton.setOnClickListener( null );
		mGallery.setOnItemsScrollListener( null );

		( (ImageViewSpotSingleTap) mImageView ).setOnTapListener( null );

		if( mBackgroundDrawThread != null ) {

			mBackgroundDrawThread.clear();

			if( mBackgroundDrawThread.isAlive() ) {
				mBackgroundDrawThread.quit();
				while( mBackgroundDrawThread.isAlive() ) {
					// mLogger.log( "isAlive..." );
					// wait...
				}
			}
		}

		if( null != mOverlay ) {
			mOverlay.dismiss();
		}

		if( null != mCloseOverlay ) {
			mCloseOverlay.dismiss();
		}

		onProgressEnd();
		super.onDeactivate();
	}

	@Override
	public void onDestroy () {
		mImageView.clear();
		super.onDestroy();

		if( null != mToast ) {
			mToast.cancel();
		}
	}

	@Override
	public boolean onBackPressed () {
		mLogger.info("onBackPressed");

		if( null != mOverlay ) {
			if( mOverlay.onBackPressed() ) {
				return true;
			}
		}

		if( null != mCloseOverlay ) {
			mCloseOverlay.dismiss();
		} else {

			if( AviaryOverlay.shouldShow( getContext(), AviaryOverlay.ID_BLEMISH_CLOSE )) {
				mCloseOverlay = new BlemishOutOverlay( getContext().getBaseContext(), R.style.AviaryWidget_Overlay_Blemish_Close );
				if( mCloseOverlay.show() ) {
					mCloseOverlay.setOnCloseListener( new AviaryOverlay.OnCloseListener() {
						@Override
						public void onClose( final AviaryOverlay overlay ) {
							overlay.dismiss();

							try {
								if (null != BlemishPanel.this.getContext()) {
									BlemishPanel.this.getContext().cancel();
								}
							} catch( NullPointerException e ) {
								e.printStackTrace();
							}
						}
					} );
					return true;
				}
			}
		}

		return super.onBackPressed();
	}

	private Toast makeToast () {
		mDrawable = new PreviewSpotDrawable( this.getContext()
				                                     .getBaseContext() );
		Toast t = UIUtils.makeCustomToast( this.getContext()
				                                   .getBaseContext() );
		ImageView image = (ImageView) t.getView()
				.findViewById( R.id.image );
		image.setImageDrawable( mDrawable );
		return t;
	}

	@Override
	public void onTap ( final float[] points, float radius ) {
		mLogger.info( "onTap, radius: %.2f", radius );
		radius = Math.max( 1, radius );
		mBackgroundDrawThread.pathStart( radius * 2, points, mBrushType );
		mBackgroundDrawThread.pathEnd();
		setIsChanged( true );
	}

	@Override
	protected void onGenerateResult () {

		// mark blemish-close as read
		if( AviaryOverlay.shouldShow( getContext(), AviaryOverlay.ID_BLEMISH_CLOSE )) {
			AviaryOverlay.markAsViewed( getContext(), AviaryOverlay.ID_BLEMISH_CLOSE );
		}

		if( mBackgroundDrawThread.isAlive() && ! mBackgroundDrawThread.isCompleted() ) {
			mBackgroundDrawThread.finish();
			GenerateResultTask task = new GenerateResultTask();
			task.execute();
		} else {
			onComplete( mPreview, mFilter.getActions() );
		}
	}

	public void setPanelEnabled ( boolean value ) {

		if( mOptionView != null ) {
			if( value != mOptionView.isEnabled() ) {
				mOptionView.setEnabled( value );

				if( value ) {
					getContext().restoreToolbarTitle();
				} else {
					getContext().setToolbarTitle( R.string.feather_zoom_mode );
				}

				mDisabledStatusView.setVisibility( value ? View.INVISIBLE : View.VISIBLE );
			}
		}
	}

	public boolean getPanelEnabled () {
		if( mOptionView != null ) {
			return mOptionView.isEnabled();
		}
		return false;
	}

	protected NativeToolFilter createFilter ( ToolLoaderFactory.Tools type ) {
		return (NativeToolFilter) ToolLoaderFactory.get(type);
	}

	@SuppressLint ("InflateParams")
	@Override
	protected View generateContentView ( LayoutInflater inflater ) {
		return inflater.inflate( R.layout.aviary_content_blemish, null );
	}

	@Override
	protected ViewGroup generateOptionView ( LayoutInflater inflater, ViewGroup parent ) {
		return (ViewGroup) inflater.inflate( R.layout.aviary_panel_spot, parent, false );
	}

	class GalleryAdapter extends BaseAdapter {

		private final int VALID_POSITION = 0;
		private final int INVALID_POSITION = 1;

		private int[] sizes;
		LayoutInflater mLayoutInflater;
		Resources mRes;

		public GalleryAdapter ( Context context, int[] values ) {
			mLayoutInflater = LayoutInflater.from( context );
			sizes = values;
			mRes = context.getResources();
		}

		@Override
		public int getCount () {
			return sizes.length;
		}

		@Override
		public Object getItem ( int position ) {
			return sizes[position];
		}

		@Override
		public long getItemId ( int position ) {
			return 0;
		}

		@Override
		public int getViewTypeCount () {
			return 2;
		}

		@Override
		public int getItemViewType ( int position ) {
			final boolean valid = position >= 0 && position < getCount();
			return valid ? VALID_POSITION : INVALID_POSITION;
		}

		@Override
		public View getView ( int position, View convertView, ViewGroup parent ) {

			final int type = getItemViewType( position );

			PreviewSpotDrawable drawable = null;

			if( convertView == null ) {

				convertView = mLayoutInflater.inflate( R.layout.aviary_gallery_item_view, mGallery, false );

				if( type == VALID_POSITION ) {
					drawable = new PreviewSpotDrawable( getContext().getBaseContext() );
					ImageView image = (ImageView) convertView.findViewById( R.id.image );
					image.setImageDrawable( drawable );
					convertView.setTag( drawable );
				}
			} else {
				if( type == VALID_POSITION ) {
					drawable = (PreviewSpotDrawable) convertView.getTag();
				}
			}

			if( drawable != null && type == VALID_POSITION ) {
				int size = sizes[position];
				float value = minRadiusSize + ( ( ( (float) size - minBrushSize ) / ( maxBrushSize - minBrushSize ) * ( maxRadiusSize - minRadiusSize ) ) * 0.55f );

				drawable.setRadius( value );
				convertView.setContentDescription( mSizeContentDescription + " " + Float.toString( value ) );
			}

			convertView.setSelected( mSelectedPosition == position );
			convertView.setId( position );
			return convertView;
		}
	}

	/**
	 * GenerateResultTask is used when the background draw operation is still running.
	 * Just wait until the draw operation completed.
	 */
	class GenerateResultTask extends AviaryAsyncTask<Void, Void, MoaActionList> {

		ProgressDialog mProgress = new ProgressDialog( getContext().getBaseContext() );

		@Override
		protected void PreExecute () {
			mProgress.setTitle( getContext().getBaseContext()
					                    .getString( R.string.feather_loading_title ) );
			mProgress.setMessage( getContext().getBaseContext()
					                      .getString( R.string.feather_effect_loading_message ) );
			mProgress.setIndeterminate( true );
			mProgress.setCancelable( false );
			mProgress.show();
		}

		@Override
		protected MoaActionList doInBackground ( Void... params ) {

			MoaActionList actions = MoaActionFactory.actionList();

			if( mBackgroundDrawThread != null ) {

				while( mBackgroundDrawThread != null && ! mBackgroundDrawThread.isCompleted() ) {
					mLogger.log( "waiting.... " + mBackgroundDrawThread.getQueueSize() );
					try {
						Thread.sleep( 50 );
					} catch( InterruptedException e ) {
						e.printStackTrace();
					}
				}
			}

			actions.add(mFilter.getActions().get(0));
			return actions;
		}

		@Override
		protected void PostExecute(MoaActionList result) {

			if (getContext().getBaseActivity().isFinishing()) {
				return;
			}

			if (mProgress.isShowing()) {
				try {
					mProgress.dismiss();
				} catch (IllegalArgumentException e) {
				}
			}
			onComplete(mPreview, result);
		}
	}

	private void onComplete(final Bitmap bitmap, final MoaActionList actionList) {
		mEditResult.setActionList(actionList);
		mEditResult.setToolAction(new ToolActionVO<Integer>(mBrushSize));
		onComplete(bitmap);
	}

	@Override
	public void onScrollStarted ( AviaryAdapterView<?> parent, View view, int position, long id ) {
		showSizePreview( mBrushSizes[position] );
		setSelectedTool( TouchMode.DRAW );
	}

	@Override
	public void onScroll ( AviaryAdapterView<?> parent, View view, int position, long id ) {
		updateSizePreview( mBrushSizes[position] );
	}

	@Override
	public void onScrollFinished ( AviaryAdapterView<?> parent, View view, int position, long id ) {
		mBrushSize = mBrushSizes[position];
		( (ImageViewSpotSingleTap) mImageView ).setBrushSize( ( (float) mBrushSize ) );
		setSelectedTool( TouchMode.DRAW );
	}
}
