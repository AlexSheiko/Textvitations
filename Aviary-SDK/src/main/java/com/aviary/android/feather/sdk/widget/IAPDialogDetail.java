package com.aviary.android.feather.sdk.widget;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.support.v4.widget.CursorAdapter;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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
import com.aviary.android.feather.common.utils.PackageManagerUtils;
import com.aviary.android.feather.common.utils.SystemUtils;
import com.aviary.android.feather.common.utils.os.AviaryAsyncTask;
import com.aviary.android.feather.library.Constants;
import com.aviary.android.feather.library.services.BadgeService;
import com.aviary.android.feather.sdk.FeatherActivity;
import com.aviary.android.feather.sdk.R;
import com.aviary.android.feather.sdk.graphics.CdsPreviewTransformer;
import com.aviary.android.feather.sdk.widget.AviaryWorkspace.OnPageChangeListener;
import com.aviary.android.feather.sdk.widget.CellLayout.CellInfo;
import com.aviary.android.feather.sdk.widget.IAPDialogMain.IAPUpdater;
import it.sephiroth.android.library.picasso.Callback;
import it.sephiroth.android.library.picasso.Picasso;

import junit.framework.AssertionFailedError;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class IAPDialogDetail extends LinearLayout implements OnPageChangeListener, OnClickListener, IabHelper.OnIabSetupFinishedListener {

	private IAPUpdater mData;
	private PacksColumns.PackCursorWrapper mPack;
	private long mPackId;

	private Picasso mPicasso;

	private BadgeService mBadgeService;

	private IAPDialogMain mParent;

	private WorkspaceAdapter mWorkspaceAdapter;

	private int mMainLayoutResId = R.layout.aviary_iap_workspace_screen_stickers;
	private int mCellResId = R.layout.aviary_iap_cell_item_effects;

	private View mProgress;

	private View mErrorView;
	private TextView mErrorText;
	private TextView mRetryButton;

	private AviaryTextView mTitle, mDescription;
	private IAPBuyButton mBuyButton;
	private AviaryWorkspace mWorkspace;
	private AviaryWorkspaceIndicator mWorkspaceIndicator;
	private View mHeadView;
	private View mSubscriptionView;

	private boolean mDownloadOnDemand = true;
	private boolean mAttached;

	// workspace attributes
	int mRows = 1;
	int mCols = 1;
	int mItemsPerPage;

	private static Logger logger = LoggerFactory.getLogger( "IAPDialogDetail", LoggerType.ConsoleLoggerType );

	public IAPDialogDetail ( Context context, AttributeSet attrs ) {
		super( context, attrs );
		mDownloadOnDemand = SystemUtils.getApplicationTotalMemory() < Constants.APP_MEMORY_LARGE;
	}

	public IAPUpdater getData () {
		return mData;
	}

	@Override
	protected void onAttachedToWindow () {
		super.onAttachedToWindow();

		if( isInEditMode() ) return;

		logger.info( "onAttachedToWindow" );

		mAttached = true;
		final FeatherActivity activity = (FeatherActivity) getContext();

		mBadgeService = activity.getMainController().getService( BadgeService.class );

		mPicasso = Picasso.with( getContext() );

		mBuyButton = (IAPBuyButton) findViewById( R.id.aviary_buy_button );
		mHeadView = findViewById( R.id.aviary_head );
		mTitle = (AviaryTextView) findViewById( R.id.aviary_title );
		mDescription = (AviaryTextView) findViewById( R.id.aviary_description );
		mWorkspace = (AviaryWorkspace) findViewById( R.id.aviary_workspace );
		mWorkspaceIndicator = (AviaryWorkspaceIndicator) findViewById( R.id.aviary_workspace_indicator );
		mProgress = findViewById( R.id.aviary_progress );
		mSubscriptionView = findViewById( R.id.aviary_subscription_banner );

		mErrorView = findViewById( R.id.aviary_error_message );

		if( null != mErrorView ) {
			mErrorText = (TextView) mErrorView.findViewById( R.id.aviary_retry_text );
			mRetryButton = (TextView) mErrorView.findViewById( R.id.aviary_retry_button );

			if( null != mRetryButton ) {
				mRetryButton.setOnClickListener( this );
			}
		}

		mBuyButton.setOnClickListener( this );

		mWorkspaceAdapter = new WorkspaceAdapter( getContext(), null, - 1, null );
		mWorkspace.setAdapter( mWorkspaceAdapter );
		mWorkspace.setIndicator( mWorkspaceIndicator );
	}

	private void handleSubscriptionInUI () {
		if( CdsUtils.hasSubscriptionAvailable( getContext() ) ) {
			logger.log( "handleSubscriptionInUI: true" );

			if( null != mSubscriptionView ) {
				mSubscriptionView.setOnClickListener( this );
				mSubscriptionView.setVisibility( View.VISIBLE );
			}

		} else {
			logger.log( "handleSubscriptionInUI: false" );
			if( null != mSubscriptionView ) {
				mSubscriptionView.setOnClickListener( null );
				mSubscriptionView.setVisibility( View.GONE );
			}
		}
	}

	private void computeLayoutItems ( Resources res, String packType ) {
		if( AviaryCds.PACKTYPE_EFFECT.equals( packType ) || AviaryCds.PACKTYPE_FRAME.equals( packType ) ) {
			mMainLayoutResId = R.layout.aviary_iap_workspace_screen_effects;
			mCols = res.getInteger( R.integer.aviary_iap_dialog_cols_effects );
			mRows = res.getInteger( R.integer.aviary_iap_dialog_rows_effects );
			mCellResId = R.layout.aviary_iap_cell_item_effects;
		} else {
			mMainLayoutResId = R.layout.aviary_iap_workspace_screen_stickers;
			mCols = res.getInteger( R.integer.aviary_iap_dialog_cols_stickers );
			mRows = res.getInteger( R.integer.aviary_iap_dialog_rows_stickers );
			mCellResId = R.layout.aviary_iap_cell_item_stickers;
		}
		mItemsPerPage = mRows * mCols;
	}

	@Override
	protected void onDetachedFromWindow () {
		logger.info( "onDetachedFromWindow" );

		try {
			mParent.getController().getTracker().tagEvent( "shop_details: closed" );
		} catch( Throwable t ) {
		}

		mBuyButton.setOnClickListener( null );
		mSubscriptionView.setOnClickListener( null );
		mRetryButton.setOnClickListener( null );
		mWorkspace.setTag( null );
		mWorkspaceAdapter.changeCursor( null );
		mWorkspace.setAdapter( null );
		mWorkspace.setOnPageChangeListener( null );
		mAttached = false;

		super.onDetachedFromWindow();
	}

	@Override
	public void onClick ( View v ) {

		final int id = v.getId();

		if( id == mRetryButton.getId() ) {
			// RETRY
			update( getData(), mParent );

		} else if( id == mSubscriptionView.getId() ) {
			// SUBSCRIPTION
			mParent.launchSubscriptionActivity( "shop_details" );

		} else if( id == mBuyButton.getId() ) {

			IAPBuyButton button = (IAPBuyButton) v;

			CdsUtils.PackOptionWithPrice option = button.getPackOption();
			if( null == option ) {
				return;
			}

			switch( option.option ) {

				case PURCHASE:
					mParent.getStoreWrapper().purchase( mPack.getId(), mPack.getIdentifier(), mPack.getPackType(), "shop_detail", option.price );
					break;

				case INSTALL:
				case FREE:
				case RESTORE:
				case DOWNLOAD_ERROR:
					final boolean isFree = option.option == CdsUtils.PackOption.FREE;
					final boolean isRestore = option.option == CdsUtils.PackOption.RESTORE;
					final boolean isError = option.option == CdsUtils.PackOption.DOWNLOAD_ERROR;
					final boolean isSubscription = option.option == PackOption.INSTALL;

					mParent.getStoreWrapper()
							.restore( mPack.getId(), mPack.getIdentifier(), mPack.getPackType(), "shop_detail", isRestore, isFree, isError, isSubscription );
					break;

				case ERROR:
					setPackOption( new CdsUtils.PackOptionWithPrice( CdsUtils.PackOption.PACK_OPTION_BEING_DETERMINED ), mPackId );
					mParent.getStoreWrapper().startSetup( true, this );
					break;

				case OWNED:
				case PACK_OPTION_BEING_DETERMINED:
				case DOWNLOADING:
				case DOWNLOAD_COMPLETE:
					logger.log( "Do nothing here" );
					break;
			}
		}
	}

	// --------------------------
	// Store Wrapper methods
	// --------------------------

	@Override
	public void onIabSetupFinished ( final IabResult result ) {
		logger.info( "onIabSetupFinished: %s", result );

		if( isValidContext() && null != mPack && null != mData ) {
			new DeterminePackOptionAsyncTask( mPack.getId() ).execute( mParent.mInventory );
			new LoadPreviewsAsyncTask().execute( mPack.getId() );
		}
	}

	void onDownloadStatusChanged ( long packId, String packType, int status ) {
		if( isValidContext() && null != mPack && null != mData ) {
			logger.info( "onDownloadStatusChanged: %d, %s, %d", packId, packType, status );
			if( packId == mPack.getId() ) {
				new DeterminePackOptionAsyncTask( mPack.getId() ).execute( mParent.mInventory );
			}
		}
	}

	void onPackInstalled ( final long packId, final String packType, final int purchased ) {
		if( isValidContext() && null != mPack && packId == mPack.getId() ) {
			logger.info( "onPackInstalled: %d, %s, %d", packId, packType, purchased );
			new DeterminePackOptionAsyncTask( mPack.getId() ).execute( mParent.mInventory );
		}
	}

	void onPurchaseSuccess ( final long packId, final String packType, final Purchase purchase ) {
		if( isValidContext() && null != mPack && packId == mPack.getId() ) {
			logger.info( "onPurchaseSuccess: %d - %s", packId, packType );
			new DeterminePackOptionAsyncTask( mPack.getId() ).execute( mParent.mInventory );
		}
	}

	void onSubscriptionPurchased ( final String identifier, final int purchased ) {
		if( isValidContext() && null != mPack && null != mData ) {
			logger.info( "onSubscriptionPurchased: %s, %d", identifier, purchased );
			new DeterminePackOptionAsyncTask( mPack.getId() ).execute( mParent.mInventory );
		}
	}

	void onServiceFinished () {
		if( !isValidContext() || mPack == null || null == mData ) {
			return;
		}

		logger.info( "onServiceFinished" );
		handleSubscriptionInUI();
	}

	private void initWorkspace ( PacksColumns.PackCursorWrapper pack, final String previewPath ) {
		if( null != pack && isValidContext() ) {

			Long oldTag = (Long) mWorkspace.getTag();
			if( null != oldTag && oldTag == pack.getId() ) {
				logger.warn( "ok, don't reload the workspace, same tag found" );
				mProgress.setVisibility( View.GONE );
				return;
			}

			Cursor cursor = getContext().getContentResolver()
			                            .query( PackageManagerUtils.getCDSProviderContentUri( getContext(), "pack/" + pack.getId() + "/item/list" ),
			                                    new String[]{ PacksItemsColumns._ID + " as _id", PacksColumns.PACK_TYPE, PacksItemsColumns._ID, PacksItemsColumns.IDENTIFIER,
					                                    PacksItemsColumns.DISPLAY_NAME }, null, null, null );

			mWorkspaceAdapter.setBaseDir( previewPath );
			mWorkspaceAdapter.changeCursor( cursor );
			mWorkspace.setOnPageChangeListener( this );
			mWorkspace.setTag( Long.valueOf( pack.getId() ) );

			mProgress.setVisibility( View.GONE );

			if( null == cursor || cursor.getCount() <= mItemsPerPage ) {
				mWorkspaceIndicator.setVisibility( View.INVISIBLE );
			} else {
				mWorkspaceIndicator.setVisibility( View.VISIBLE );
			}
		} else {
			logger.error( "invalid plugin" );
			mWorkspaceAdapter.changeCursor( null );
			mWorkspace.setTag( null );
			mWorkspace.setOnPageChangeListener( null );
			mWorkspaceIndicator.setVisibility( View.INVISIBLE );
		}
	}

	public void update ( IAPUpdater updater, IAPDialogMain parent ) {
		logger.info( "update: %s", updater );
		logger.log( "isValidContext: %b", isValidContext() );

		if( null == updater || !isValidContext() ) {
			return;
		}

		if( parent.getStoreWrapper().isActive() ) {

			mParent = parent;
			mData = (IAPUpdater) updater.clone();
			mPackId = mData.getPackId();
			mPack = null;

			setPackOption( new CdsUtils.PackOptionWithPrice( CdsUtils.PackOption.PACK_OPTION_BEING_DETERMINED ), - 1 );
			mTitle.setText( "" );
			mDescription.setText( "" );

			mWorkspaceAdapter.changeCursor( null );
			mWorkspace.setTag( null );

			mErrorView.setVisibility( View.GONE );

			setPackContent( CdsUtils.getPackFullInfoById( getContext(), mPackId ) );
			handleSubscriptionInUI();
		}
	}

	void setPackOption ( CdsUtils.PackOptionWithPrice option, long packId ) {
		logger.info( "setPackOption: %s", option );
		if( null != mBuyButton ) {
			mBuyButton.setPackOption( option, packId );
		}
	}

	/**
	 * Error downloading plugin informations
	 */
	private void onDownloadError () {
		logger.info( "onDownloadError" );

		mErrorView.setVisibility( View.VISIBLE );

		mProgress.setVisibility( View.GONE );
		mBuyButton.setVisibility( View.GONE );
		mWorkspaceAdapter.changeCursor( null );
		mWorkspace.setTag( null );
		mTitle.setText( "" );

		if( null != mErrorText ) {
			mErrorText.setText( R.string.feather_item_not_found );
		}
	}

	private void onDownloadPreviewError () {
		logger.info( "onDownloadPreviewError" );
		mErrorView.setVisibility( View.VISIBLE );

		mProgress.setVisibility( View.GONE );
		mWorkspaceAdapter.changeCursor( null );
		mWorkspace.setTag( null );

		if( null != mErrorText ) {
			mErrorText.setText( R.string.feather_iap_failed_download_previews );
		}
	}

	private void setPackContent ( PacksColumns.PackCursorWrapper pack ) {
		logger.info( "setPackContent: %s", pack );

		if( !isValidContext() || mPackId < 0 || null == mData ) return;

		if( null == pack || null == pack.getContent() ) {
			logger.error( "pack or pack.content are null!" );
			onDownloadError();
			return;
		}

		mPack = pack;
		mPackId = pack.getId();
		mProgress.setVisibility( View.GONE );

		mTitle.setText( mPack.getContent().getDisplayName() );
		mTitle.setSelected( true );
		mDescription.setText( mPack.getContent().getDisplayDescription() != null ? mPack.getContent().getDisplayDescription() : "" );


		// mark pack as 'read'
		if( null != mBadgeService ) {
			mBadgeService.markAsRead( mPack.getIdentifier() );
		}

		// update workspace
		computeLayoutItems( getContext().getResources(), mPack.getPackType() );
		mWorkspaceAdapter.setContext( getContext() );
		mWorkspaceAdapter.setResourceId( mMainLayoutResId );
		mWorkspaceAdapter.setFileExt( AviaryCds.getPreviewItemExt( mPack.getPackType() ) );
		mWorkspaceAdapter.setBaseDir( null );
		mWorkspaceIndicator.setVisibility( View.INVISIBLE );

		if( null != mHeadView ) {
			mHeadView.requestFocus();
			mHeadView.requestFocusFromTouch();
		}

		if( null != mParent ) {
			if( mParent.getStoreWrapper().isSetupDone() ) {
				onIabSetupFinished( null );
			} else {
				mParent.getStoreWrapper().startSetup( true, this );
			}
		}
	}

	private void downloadPreviews ( PacksColumns.PackCursorWrapper pack ) {
		logger.info( "downloadPreviews" );

		if( !isValidContext() || null == pack || null == pack.getContent() ) {
			return;
		}

		new LoadPreviewsAsyncTask().execute( pack.getId() );

	}

	class WorkspaceAdapter extends CursorAdapter {

		LayoutInflater mLayoutInflater;
		int mResId;
		String mBaseDir;
		String mFileExt;
		int mTargetDensity;

		int columnIndexType;
		int columnIndexDisplayName;
		int columnIndexIdentifier;

		public WorkspaceAdapter ( Context context, String baseDir, int resource, Cursor cursor ) {
			super( context, cursor, false );
			mResId = resource;
			mLayoutInflater = LayoutInflater.from( getContext() );
			mBaseDir = baseDir;
			mTargetDensity = context.getResources().getDisplayMetrics().densityDpi;
			initCursor( cursor );
		}

		@Override
		public Cursor swapCursor ( Cursor newCursor ) {
			initCursor( newCursor );
			recycleBitmaps();
			return super.swapCursor( newCursor );
		}

		private void recycleBitmaps () {
			logger.info( "recycleBitmaps" );

			int count = mWorkspace.getChildCount();

			for( int i = 0; i < count; i++ ) {
				CellLayout view = (CellLayout) mWorkspace.getChildAt( i );
				int cells = view.getChildCount();
				for( int k = 0; k < cells; k++ ) {
					ImageView imageView = (ImageView) view.getChildAt( k );
					if( null != imageView ) {
						Drawable drawable = imageView.getDrawable();
						if( null != drawable && ( drawable instanceof BitmapDrawable ) ) {
							imageView.setImageBitmap( null );

							Bitmap bitmap = ( (BitmapDrawable) drawable ).getBitmap();

							if( null != bitmap && ! bitmap.isRecycled() ) {
								bitmap.recycle();
							}
						}
					}
				}
			}
		}

		private void initCursor ( Cursor cursor ) {
			if( null != cursor ) {
				columnIndexDisplayName = cursor.getColumnIndex( PacksItemsColumns.DISPLAY_NAME );
				columnIndexIdentifier = cursor.getColumnIndex( PacksItemsColumns.IDENTIFIER );
				columnIndexType = cursor.getColumnIndex( PacksColumns.PACK_TYPE );
			}
		}

		public void setContext ( Context context ) {
			mContext = context;
		}

		public void setResourceId ( int resid ) {
			mResId = resid;
		}

		public void setBaseDir ( String dir ) {
			mBaseDir = dir;
		}

		public String getBaseDir () {
			return mBaseDir;
		}

		public void setFileExt ( String file_ext ) {
			mFileExt = file_ext;
		}

		@Override
		public int getCount () {
			return (int) Math.ceil( (double) super.getCount() / mItemsPerPage );
		}

		/**
		 * Gets the real num of items.
		 *
		 * @return the real count
		 */
		public int getRealCount () {
			return super.getCount();
		}

		@Override
		public View newView ( Context context, Cursor cursor, ViewGroup parent ) {
			View view = mLayoutInflater.inflate( mResId, parent, false );
			return view;
		}

		@Override
		public void bindView ( View view, Context context, Cursor cursor ) {

			int page = cursor.getPosition();
			int position = page * mItemsPerPage;

			CellLayout cell = (CellLayout) view;
			cell.setNumCols( mCols );
			cell.setNumRows( mRows );

			for( int i = 0; i < mItemsPerPage; i++ ) {
				View toolView;
				CellInfo cellInfo = cell.findVacantCell();

				if( cellInfo == null ) {
					toolView = cell.getChildAt( i );
				} else {
					toolView = mLayoutInflater.inflate( mCellResId, mWorkspace, false );
					CellLayout.LayoutParams lp = new CellLayout.LayoutParams( cellInfo.cellX, cellInfo.cellY, cellInfo.spanH, cellInfo.spanV );
					cell.addView( toolView, - 1, lp );
				}

				final int index = position + i;
				final ImageView imageView = (ImageView) toolView.findViewById( R.id.aviary_image );

				int maxW = mWorkspace.getWidth() / mCols;
				int maxH = mWorkspace.getHeight() / mRows;

				if( index < getRealCount() ) {
					loadImage( i * 60, position + i, imageView, mDownloadOnDemand, maxW, maxH );
				} else {
					// else...
					// imageView.setTag( null );
					if( null != imageView ) {
						imageView.setImageBitmap( null );
					}
				}
			}
			view.requestLayout();
		}

		public void loadImage ( int delay, int position, final ImageView imageView, boolean onDemand, int maxW, int maxH ) {

			Cursor cursor = (Cursor) getItem( position );

			if( null != cursor && ! cursor.isAfterLast() && null != imageView ) {
				String identifier = cursor.getString( columnIndexIdentifier );
				String displayName = cursor.getString( columnIndexDisplayName );
				String type = cursor.getString( columnIndexType );

				File file = new File( getBaseDir(), identifier + ( mFileExt ) );
				final String path = file.getAbsolutePath();
				final int imageTag = path.hashCode();

				final Integer tag = (Integer) imageView.getTag();
				final boolean same = ( tag != null && tag.equals( imageTag ) );

				if( onDemand ) {
					if( ! same ) {
						imageView.setTag( null );
						imageView.setImageBitmap( null );
					}
				} else {
					if( ! same ) {

						mPicasso.load( path )
								.withDelay( delay )
								.skipMemoryCache()
								.error( R.drawable.aviary_ic_na )
								.transform( new CdsPreviewTransformer( path, displayName, type ) )
								.into( imageView, new Callback() {

									@Override
									public void onSuccess () {
										imageView.setTag( Integer.valueOf( imageTag ) );
									}

									public void onError () {}
								} );
					}
				}
			}
		}
	}

	@Override
	public void onPageChanged ( int which, int old ) {
		logger.info( "onPageChanged: " + old + " >> " + which );

		if( ! mDownloadOnDemand ) {
			return;
		}
		if( null == getContext() ) {
			return;
		}

		if( null != mWorkspace ) {
			WorkspaceAdapter adapter = (WorkspaceAdapter) mWorkspace.getAdapter();

			int index = which * mItemsPerPage;
			int endIndex = index + mItemsPerPage;
			int total = adapter.getRealCount();

			for( int i = index; i < endIndex; i++ ) {
				CellLayout cellLayout = (CellLayout) mWorkspace.getScreenAt( which );
				if( null == cellLayout ) {
					continue;
				}

				ImageView toolView = (ImageView) cellLayout.getChildAt( i - index );

				int maxW = mWorkspace.getWidth() / mCols;
				int maxH = mWorkspace.getHeight() / mRows;

				if( i < total ) {
					adapter.loadImage( i * 60, i, toolView, false, maxW, maxH );
				}
			}

			// if download on demand, then cleanup the old page bitmaps
			if( mDownloadOnDemand && old != which ) {
				CellLayout cellLayout = (CellLayout) mWorkspace.getScreenAt( old );
				if( null != cellLayout ) {
					for( int i = 0; i < cellLayout.getChildCount(); i++ ) {
						View toolView = cellLayout.getChildAt( i );
						ImageView imageView = (ImageView) toolView.findViewById( R.id.aviary_image );
						if( null != imageView ) {
							imageView.setImageBitmap( null );
							imageView.setTag( null );
						}
					}
				}
			}
		}
	}

	boolean isValidContext () {
		return mAttached && null != getContext();
	}

	class LoadPreviewsAsyncTask extends AviaryAsyncTask<Long, Void, String> {

		static final int POST_REMOTE_DOWNLOAD = 1;
		static final int POST_SHOW_ERROR = 2;

		int status;
		long packId;

		@Override
		protected void PostExecute ( final String previewPath ) {
			logger.info( "LoadPreviewsAsyncTask::PostExecute: %s, %d", previewPath, status );

			if( !isValidContext() || null == mPack || null == mData ) return;
			if( mPack.getId() != packId ) return;

			if( null != previewPath ) {
				initWorkspace( mPack, previewPath );
			} else {
				if( status == POST_SHOW_ERROR ) {
					onDownloadPreviewError();
				} else {
					new PreviewDownloadAsyncTask().execute( packId );
				}
			}
		}

		@Override
		protected void PreExecute () {
			mErrorView.setVisibility( View.GONE );
			mProgress.setVisibility( View.VISIBLE );
		}

		@Override
		protected String doInBackground ( final Long... params ) {
			logger.info( "LoadPreviewsAsyncTask::doInBackground" );

			if( !isValidContext() ) {
				return null;
			}

			packId = params[0];
			final Context context = getContext();
			int delayTime = getResources().getInteger( android.R.integer.config_mediumAnimTime ) + 300;

			SystemUtils.trySleep( delayTime + 300 );

			final PacksColumns.PackCursorWrapper pack = CdsUtils.getPackFullInfoById( context, packId );

			if( null == pack || null == pack.getContent() ) {
				status = POST_SHOW_ERROR;
				return null;
			}

			final String previewPath = pack.getContent().getPreviewPath();

			if( !TextUtils.isEmpty( previewPath ) ) {
				File file = new File( previewPath );
				Validator validator = AviaryCdsValidatorFactory.create( ContentType.PREVIEW, PackType.fromString( pack.getPackType() ) );

				try {
					validator.validate( getContext(), pack.getContent().getId(), file, false );
					return pack.getContent().getPreviewPath();
				} catch( AssertionFailedError e ) {
					// preview path exists, but is invalid
					status = POST_SHOW_ERROR;
				}
			} else {
				// previews must be downloaded
				status = POST_REMOTE_DOWNLOAD;
			}
			return null;
		}
	}


	/**
	 * Download the pack previews
	 */
	class PreviewDownloadAsyncTask extends AviaryAsyncTask<Long, Void, String> {

		private Throwable error;
		private long packId;

		@Override
		protected String doInBackground ( Long... params ) {

			logger.log( "PreviewDownloadAsyncTask::doInBackground" );

			if( !isValidContext() || null == mPack || null == mData ) return null;

			packId = params[0];
			final Context context = getContext();

			if( null == context ) {
				return null;
			}

			AviaryCdsDownloaderFactory.Downloader downloader = AviaryCdsDownloaderFactory.create( AviaryCds.ContentType.PREVIEW );
			try {
				return downloader.download( context, packId );
			} catch( Throwable e ) {
				error = e;
				return null;
			}
		}

		@Override
		protected void PostExecute ( String previewsPath ) {
			logger.log( "PreviewDownloadAsyncTask::PostExecute: %s", previewsPath );

			if( isCancelled() || !isValidContext() || null == mPack || null == mData ) return;

			if( mPack.getId() != packId ) {
				logger.warn( "different pack" );
				return;
			}

			if( null != previewsPath ) {
				mProgress.setVisibility( View.GONE );
				initWorkspace( mPack, previewsPath );
			}

			if( null != error ) {
				onDownloadPreviewError();
			}
		}

		@Override
		protected void PreExecute () {
			mProgress.setVisibility( View.VISIBLE );
			mErrorView.setVisibility( View.GONE );
		}
	}


	/**
	 * Determine the pack price/options
	 */
	class DeterminePackOptionAsyncTask extends AviaryAsyncTask<Inventory, Void, CdsUtils.PackOptionWithPrice> {

		long packId;
		Inventory inventory;

		DeterminePackOptionAsyncTask( long packId ) {
			this.packId = packId;
		}

		Inventory getInventory ( final String identifier, IAPInstance store ) throws IabException {
			List<String> array = Arrays.asList( new String[]{ identifier } );
			if( store.isAvailable() ) {
				return store.queryInventory( true, array, null );
			}
			return null;
		}

		@Override
		protected void PreExecute () {
		}

		@Override
		protected CdsUtils.PackOptionWithPrice doInBackground ( final Inventory... params ) {
			logger.info( "DeterminePackOptionAsyncTask.doInBackground" );

			if( !isValidContext() ) return null;
			if( null == mParent ) return null;

			final Context context = getContext();
			inventory = params[0];

			AviaryStoreWrapper wrapper = mParent.getStoreWrapper();
			if( null == wrapper ) return null;

			IAPInstance instance = wrapper.getIAPInstance();

			final PacksColumns.PackCursorWrapper pack = CdsUtils.getPackFullInfoById( context, packId );

			if( pack == null ) {
				return null;
			}

			CdsUtils.PackOptionWithPrice downloadStatus = getPackDownloadStatus( context, pack );
			CdsUtils.PackOptionWithPrice optionStatus = new CdsUtils.PackOptionWithPrice( CdsUtils.getPackOption( context, pack ), null );

			logger.log( "downloadStatus: %s", downloadStatus );
			logger.log( "optionsStatus: %s", optionStatus );

			if( null != downloadStatus ) {
				// special case, download completed and pack is owned ( this means it's installed )
				if( downloadStatus.option == CdsUtils.PackOption.DOWNLOAD_COMPLETE && PackOption.isInstalled( optionStatus.option ) ) {
					return optionStatus;
				}
				return downloadStatus;
			}

			if( PackOption.isOwned( optionStatus.option ) || PackOption.isFree( optionStatus.option )) {
				return optionStatus;
			}

			if( null != instance && instance.isSetupDone() ) {
				if( null == inventory ) {
					try {
						inventory = getInventory( pack.getIdentifier(), instance );
					} catch( Exception e ) {
						e.printStackTrace();
					}
				}
			}

			if( null != inventory ) {
				return wrapper.getPackOptionFromInventory( pack.getIdentifier(), inventory );
			}

			return null;
		}

		@Override
		protected void PostExecute ( CdsUtils.PackOptionWithPrice result ) {
			logger.log( "DeterminePackOptionAsyncTask::onPostExecute: %s", result );

			if( !isValidContext() || isCancelled() ) return;
			if( null == mPack || null == mData ) return;
			if( null == mParent || null == mParent.getStoreWrapper() ) return;
			if( !mParent.getStoreWrapper().isActive() ) return;
			if( mPack.getId() != packId ) return;

			if( null == result ) {
				result = new CdsUtils.PackOptionWithPrice( CdsUtils.PackOption.ERROR );
			}
			setPackOption( result, mPackId );
		}

		/**
		 * Returns the pack download status<br />
		 * Do not call this in the main thread!
		 *
		 * @param pack
		 */
		private CdsUtils.PackOptionWithPrice getPackDownloadStatus ( Context context, PacksColumns.PackCursorWrapper pack ) {

			if( null == context ) {
				return null;
			}

			CdsUtils.PackOptionWithPrice result = null;

			Pair<CdsUtils.PackOption, String> pair = CdsUtils.getPackOptionDownloadStatus( context, pack.getId() );
			if( null != pair ) {
				result = new CdsUtils.PackOptionWithPrice( pair.first, null );
			}
			return result;
		}
	}
}
