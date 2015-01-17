package com.aviary.android.feather.sdk.panels;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.content.Loader.OnLoadCompleteListener;
import android.support.v4.util.SparseArrayCompat;
import android.support.v4.widget.CursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ViewSwitcher.ViewFactory;

import com.aviary.android.feather.cds.AviaryCds.PackType;
import com.aviary.android.feather.cds.PacksItemsColumns;
import com.aviary.android.feather.cds.TrayColumns;
import com.aviary.android.feather.common.utils.IOUtils;
import com.aviary.android.feather.common.utils.PackageManagerUtils;
import com.aviary.android.feather.common.utils.SystemUtils;
import com.aviary.android.feather.common.utils.os.AviaryAsyncTask;
import com.aviary.android.feather.headless.filters.INativeFilter;
import com.aviary.android.feather.headless.moa.Moa;
import com.aviary.android.feather.headless.moa.MoaActionList;
import com.aviary.android.feather.headless.moa.MoaResult;
import com.aviary.android.feather.library.Constants;
import com.aviary.android.feather.library.content.ToolEntry;
import com.aviary.android.feather.library.filters.BorderFilter;
import com.aviary.android.feather.library.filters.ToolLoaderFactory;
import com.aviary.android.feather.library.services.ConfigService;
import com.aviary.android.feather.library.services.IAviaryController;
import com.aviary.android.feather.library.services.LocalDataService;
import com.aviary.android.feather.library.services.PreferenceService;
import com.aviary.android.feather.library.utils.BitmapUtils;
import com.aviary.android.feather.library.vo.ToolActionVO;
import com.aviary.android.feather.sdk.AviaryMainController.FeatherContext;
import com.aviary.android.feather.sdk.R;
import com.aviary.android.feather.sdk.graphics.PluginDividerDrawable;
import com.aviary.android.feather.sdk.overlays.AviaryOverlay;
import com.aviary.android.feather.sdk.overlays.FramesOverlay;
import com.aviary.android.feather.sdk.utils.PackIconCallable;
import com.aviary.android.feather.sdk.widget.AviaryImageSwitcher;
import com.aviary.android.feather.sdk.widget.EffectThumbLayout;
import com.aviary.android.feather.sdk.widget.IAPDialogMain;
import com.aviary.android.feather.sdk.widget.IAPDialogMain.IAPUpdater;
import com.aviary.android.feather.sdk.widget.IAPDialogMain.OnCloseListener;
import it.sephiroth.android.library.picasso.Generator;
import it.sephiroth.android.library.picasso.LruCache;
import it.sephiroth.android.library.picasso.Picasso;
import it.sephiroth.android.library.picasso.RequestCreator;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import it.sephiroth.android.library.imagezoom.ImageViewTouch;
import it.sephiroth.android.library.imagezoom.ImageViewTouchBase.DisplayType;
import it.sephiroth.android.library.widget.AdapterView.OnItemClickListener;
import it.sephiroth.android.library.widget.AdapterView.OnItemSelectedListener;
import it.sephiroth.android.library.widget.HListView;

public class BordersPanel extends AbstractOptionPanel implements ViewFactory, OnItemSelectedListener, OnItemClickListener, OnLoadCompleteListener<Cursor> {

	private final PackType mPackType;

	protected HListView mHList;

	protected View mLoader;

	protected volatile Boolean mIsRendering = false;

	private volatile boolean mIsAnimating;

	private RenderTask mCurrentTask;

	protected ConfigService mConfigService;

	protected PreferenceService mPreferenceService;

	/** default width of each effect thumbnail */
	private int mCellWidth = 80;

	protected int mThumbSize;

	private Picasso mPicassoLibrary;

	private LruCache mCache;

	/** thumbnail for effects */
	protected Bitmap mThumbBitmap;

	/* the first valid position of the list */
	protected int mListFirstValidPosition = 0;

	private boolean mFirstTime = true;

	/** options used to decode cached images */
	private static BitmapFactory.Options mThumbnailOptions;

	protected boolean mEnableFastPreview = false;

	protected TrayColumns.TrayCursorWrapper mRenderedEffect;

	protected CursorAdapter mAdapter;
	protected CursorLoader mCursorLoader;
	protected ContentObserver mContentObserver;
	protected IAPDialogMain mIapDialog;

	// tutorial overlay
	FramesOverlay mOverlay;

	private final List<Long> mInstalledPacks = new ArrayList<Long>();

	private static final int MAX_MEM_CACHE_SIZE = 6 * IOUtils.MEGABYTE; // 6MB

	/**
	 * Content resolver has loaded
	 */
	@Override
	public void onLoadComplete ( Loader<Cursor> loader, Cursor cursor ) {
		mLogger.info( "onLoadComplete" );

		long iapDialogFeaturedId = -1;
		int lastInstalledPackIndex = -1;
		int firstValidIndex = - 1;
		int index = 0;
		long options_pack_id = -1;
		long options_content_id = -1;
		boolean smooth_selection = false;
		boolean force_update = false;

		boolean checkFromIap = ( !mFirstTime && null != mIapDialog && mIapDialog.isValid() && null != mIapDialog.getParent() && null != mIapDialog.getData() );
		boolean checkFromOptions = false;
		boolean skipTutorial = false;
		boolean applySelected = false;

		// check if a pack has been installed from the IAP dialog
		if ( checkFromIap ) {
			IAPUpdater data = mIapDialog.getData();
			if( data.getFeaturedPackId() == data.getPackId() && data.getFeaturedPackId() > -1 ) {
				iapDialogFeaturedId = data.getFeaturedPackId();
			}
		}
		checkFromIap = iapDialogFeaturedId > -1;

		if( hasOptions() && mFirstTime && !checkFromIap ) {
			final Bundle options = getOptions();
			options_pack_id = options.getLong(Constants.QuickLaunch.CONTENT_PACK_ID, -1);
			options_content_id = options.getLong(Constants.QuickLaunch.CONTENT_ITEM_ID, -1);

			checkFromOptions = options_pack_id > -1 && options_content_id > -1;

			// remove the extra from the option bundle, since it's a one time shot
			options.remove(Constants.QuickLaunch.CONTENT_PACK_ID);
			options.remove(Constants.QuickLaunch.CONTENT_ITEM_ID);
		}

		List<Long> tmpList = new ArrayList<Long>();

		if( null != cursor ) {
			index = cursor.getPosition();
			while( cursor.moveToNext() ) {
				int type = cursor.getInt( TrayColumns.TYPE_COLUMN_INDEX );

				if( type == TrayColumns.TYPE_PACK_INTERNAL ) {
					long pack_id = cursor.getLong( TrayColumns.ID_COLUMN_INDEX );
					String identifier = cursor.getString( TrayColumns.IDENTIFIER_COLUMN_INDEX );
					mLogger.log( "%d = %s, is new: %b, first time: %b", pack_id, identifier, !mInstalledPacks.contains( pack_id ), mFirstTime );

					tmpList.add( pack_id );

					if( !mFirstTime ) {
						if (! mInstalledPacks.contains(pack_id)) {
							mLogger.log("adding %d (%s) to new packs", pack_id, identifier);
							mLogger.log("iapDialogFeaturedId: %d, pack_id: %d", iapDialogFeaturedId, pack_id);

							if (checkFromIap && iapDialogFeaturedId == pack_id) {
								mLogger.log("setting new position based on featured: %d", pack_id);
								lastInstalledPackIndex = cursor.getPosition();
								smooth_selection = true;
							}
						}
					}

					if( firstValidIndex == -1 ) {
						firstValidIndex = cursor.getPosition();
					}
					// break;

				}
				else if (type == TrayColumns.TYPE_CONTENT && checkFromOptions) {
					long item_id = cursor.getLong(TrayColumns.ID_COLUMN_INDEX);
					if (options_content_id == item_id) {
						lastInstalledPackIndex = cursor.getPosition();
						checkFromOptions = false;
						skipTutorial = true;
						applySelected = true;
						options_pack_id = - 1;
					}
				}
			}
			cursor.moveToPosition( index );
		}

		mInstalledPacks.clear();
		mInstalledPacks.addAll(tmpList);

		// update the adapter cursor
		mAdapter.changeCursor( cursor );

		mLogger.log( "lastInstalledPackIndex: %d", lastInstalledPackIndex );

		if( lastInstalledPackIndex >= 0 ) {
			force_update = true;
			firstValidIndex = lastInstalledPackIndex;
			removeIapDialog();
		}

		onEffectListUpdated( cursor, firstValidIndex, force_update, smooth_selection, applySelected );

		// check optional messaging
		if( openStorePanelIfRequired(options_pack_id) ) {
			return;
		}

		// skip tutorial if quick launch was enabled
		if (skipTutorial) return;

		if( PackType.FRAME == mPackType ) {
			createTutorialOverlayIfNecessary( firstValidIndex );
		}
	}

	public BordersPanel ( IAviaryController context, ToolEntry entry ) {
		this( context, entry, PackType.FRAME );
	}

	protected BordersPanel ( IAviaryController context, ToolEntry entry, PackType type ) {
		super( context, entry );
		mPackType = type;
	}


	private boolean openStorePanelIfRequired(long id) {
		mLogger.info("openStorePanelIfRequired: %d", id);
		// check optional messaging
		long iapPackageId = - 1;
		if (hasOption(Constants.QuickLaunch.CONTENT_PACK_ID) || id > - 1) {
			if (id > - 1) {
				iapPackageId = id;
			}
			else if (hasOption(Constants.QuickLaunch.CONTENT_PACK_ID)) {
				Bundle options = getOptions();
				iapPackageId = options.getLong(Constants.QuickLaunch.CONTENT_PACK_ID);
				options.remove(Constants.QuickLaunch.CONTENT_PACK_ID);
			}

			mLogger.log("iapPackageId: %d", id);

			// display the iap dialog
			if (iapPackageId > - 1) {
				//@formatter:off
				IAPUpdater iapData = new IAPUpdater.Builder().setPackId(iapPackageId)
				                                             .setFeaturedPackId(iapPackageId)
				                                             .setEvent("shop_details: opened")
				                                             .setPackType(mPackType)
				                                             .addEventAttributes("pack", String.valueOf(iapPackageId))
				                                             .addEventAttributes("from", "message")
				                                             .build();
				//@formatter:on

				displayIAPDialog(iapData);
				return true;
			}
		}
		return false;
	}

	@Override
	public void onCreate ( Bitmap bitmap, Bundle options ) {
		super.onCreate( bitmap, options );

		mPicassoLibrary = Picasso.with( getContext().getBaseContext() );
		mInstalledPacks.clear();

		double[] mem = new double[3];
		SystemUtils.getRuntimeMemoryInfo( mem );

		final double total = Math.max( mem[0], 2 ); // at least 2MB
		int max_size = (int) ( IOUtils.MEGABYTE * total );
		mLogger.log( "max size for cache: " + max_size );

		max_size = Math.min( max_size, MAX_MEM_CACHE_SIZE );
		mCache = new LruCache( max_size );

		mThumbnailOptions = new Options();
		mThumbnailOptions.inPreferredConfig = Config.RGB_565;

		mConfigService = getContext().getService( ConfigService.class );
		mPreferenceService = getContext().getService( PreferenceService.class );

		LocalDataService dataService = getContext().getService( LocalDataService.class );

		mEnableFastPreview = dataService.getFastPreviewEnabled();

		mHList = (HListView) getOptionView().findViewById( R.id.aviary_list );
		mLoader = getOptionView().findViewById( R.id.aviary_loader );

		mPreview = BitmapUtils.copy( mBitmap, Bitmap.Config.ARGB_8888 );
	}

	@Override
	public void onBitmapReplaced ( Bitmap bitmap ) {
		super.onBitmapReplaced( bitmap );

		if( isActive() ) {
			mLogger.error( "TODO: BordersPanel check this" );
			mHList.setSelection( mListFirstValidPosition );
		}
	}

	@Override
	public void onActivate () {
		super.onActivate();

		mCellWidth = mConfigService.getDimensionPixelSize( R.dimen.aviary_frame_item_width );
		mThumbSize = mConfigService.getDimensionPixelSize( R.dimen.aviary_frame_item_image_width );

		mThumbBitmap = generateThumbnail( mBitmap, mThumbSize, mThumbSize );

		mHList.setOnItemClickListener( this );
		onPostActivate();
	}

	@Override
	public boolean isRendering () {
		return mIsRendering;
	}

	protected final PackType getPluginType () {
		return mPackType;
	}

	protected void onPostActivate () {
		updateInstalledPacks( true );
	}

	@Override
	public void onDestroy () {
		mConfigService = null;
//		mBadgeService = null;

		try {
			mCache.clear();
		} catch( Exception e ) {
		}

		super.onDestroy();
	}

	@Override
	public void onDeactivate () {
		onProgressEnd();
		mHList.setOnItemClickListener( null );
		mHList.setAdapter( null );

		removeIapDialog();

		if( null != mOverlay ) {
			mOverlay.dismiss();
			mOverlay = null;
		}

		Context context = getContext().getBaseContext();
		context.getContentResolver()
				.unregisterContentObserver( mContentObserver );

		if( null != mCursorLoader ) {
			mLogger.info( "disposing cursorloader..." );
			mCursorLoader.unregisterListener( this );
			mCursorLoader.stopLoading();
			mCursorLoader.abandon();
			mCursorLoader.reset();
		}

		if( null != mAdapter ) {
			Cursor cursor = mAdapter.getCursor();
			IOUtils.closeSilently( cursor );
		}

		mAdapter = null;
		mCursorLoader = null;

		super.onDeactivate();
	}

	@Override
	public void onConfigurationChanged ( Configuration newConfig, Configuration oldConfig ) {
		if( mIapDialog != null ) {
			mIapDialog.onConfigurationChanged( newConfig );
		}
		super.onConfigurationChanged( newConfig, oldConfig );
	}

	@Override
	protected void onDispose () {

		mHList.setAdapter( null );

		if( mThumbBitmap != null && ! mThumbBitmap.isRecycled() ) {
			mThumbBitmap.recycle();
		}
		mThumbBitmap = null;

		super.onDispose();
	}

	@Override
	protected void onGenerateResult () {
		mLogger.info( "onGenerateResult. isRendering: " + mIsRendering );
		if( mIsRendering ) {
			GenerateResultTask task = new GenerateResultTask();
			task.execute();
		} else {
			onComplete( mPreview );
		}
	}

	@Override
	public boolean onBackPressed () {
		if( backHandled() ) {
			return true;
		}
		return super.onBackPressed();
	}

	@Override
	public void onCancelled () {
		killCurrentTask();
		mIsRendering = false;
		super.onCancelled();
	}

	@Override
	public boolean getIsChanged () {
		return super.getIsChanged() || mIsRendering == true;
	}

	@Override
	public View makeView () {
		ImageViewTouch view = new ImageViewTouch( getContext().getBaseContext(), null );
		view.setBackgroundColor( 0x00000000 );
		view.setDoubleTapEnabled( false );
		view.setScaleEnabled( false );
		view.setScrollEnabled( false );
		view.setDisplayType( DisplayType.FIT_IF_BIGGER );
		view.setLayoutParams( new AviaryImageSwitcher.LayoutParams( LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT ) );
		return view;
	}

	@Override
	protected ViewGroup generateOptionView ( LayoutInflater inflater, ViewGroup parent ) {
		return (ViewGroup) inflater.inflate( R.layout.aviary_panel_frames, parent, false );
	}

	protected Bitmap generateThumbnail ( Bitmap input, final int width, final int height ) {
		return ThumbnailUtils.extractThumbnail( input, width, height );
	}

	/**
	 * Update the installed plugins
	 */
	protected void updateInstalledPacks ( boolean firstTime ) {

		mLoader.setVisibility( View.VISIBLE );
		mHList.setVisibility( View.INVISIBLE );

		mAdapter = createListAdapter( getContext().getBaseContext(), null );
		mHList.setAdapter( mAdapter );

		Context context = getContext().getBaseContext();

		if( null == mCursorLoader ) {

			final String uri = String.format( Locale.US, "packTray/%d/%d/%d/%s", 3, 0, 1, mPackType.toCdsString() );
			mLogger.log( "uri: %s", uri );

			Uri baseUri = PackageManagerUtils.getCDSProviderContentUri( context, uri );
			mCursorLoader = new CursorLoader( context, baseUri, null, null, null, null );
			mCursorLoader.registerListener( 1, this );

			mContentObserver = new ContentObserver( new Handler() ) {
				@Override
				public void onChange ( boolean selfChange ) {
					mLogger.info( "mContentObserver::onChange" );
					super.onChange( selfChange );

					if( isActive() && null != mCursorLoader && mCursorLoader.isStarted() ) {
						mCursorLoader.onContentChanged();
					}
				}
			};
			context.getContentResolver()
					.registerContentObserver( PackageManagerUtils.getCDSProviderContentUri( context, "packTray/" + mPackType.toCdsString() ), false, mContentObserver );
		}

		mCursorLoader.startLoading();
	}

	/**
	 * Creates and returns the default adapter for the frames listview
	 *
	 * @param context
	 * @param
	 * @return
	 */
	protected CursorAdapter createListAdapter ( Context context, Cursor cursor ) {

		return new ListAdapter( context, R.layout.aviary_frame_item, R.layout.aviary_effect_item_more, R.layout.aviary_effect_item_external, R.layout.aviary_frame_item_divider, cursor );
	}

	private void onEffectListUpdated(
		Cursor cursor, int firstValidIndex, boolean forceSelection, boolean smoothSelection, boolean apply_selected) {
		mLogger.info("onEffectListUpdated: first valid index:" + firstValidIndex);

		int mListFirstValidPosition = firstValidIndex > 0 ? firstValidIndex : 0;

		if (mFirstTime) {
			mLoader.setVisibility(View.INVISIBLE);
			mHList.setVisibility(View.VISIBLE);
		}

		if( mFirstTime || forceSelection ) {
			if (mListFirstValidPosition > 0) {

				if(apply_selected){
					applyEffect(mListFirstValidPosition, 500);
				}

				if (smoothSelection) {
					mHList.smoothScrollToPositionFromLeft(mListFirstValidPosition - 1, mCellWidth / 2, 500);
				}
				else {
					mHList.setSelectionFromLeft(mListFirstValidPosition - 1, mCellWidth / 2);
				}
			}
		}

		if (mFirstTime) {
			Animation animation = new AlphaAnimation(0, 1);
			animation.setFillAfter(true);
			animation.setDuration(getContext().getBaseContext().getResources().getInteger(android.R.integer.config_longAnimTime));
			mHList.startAnimation(animation);
		}

		mFirstTime = false;
	}

	private void createTutorialOverlayIfNecessary ( final int firstValidIndex ) {
		mLogger.info("createTutorialOverlayIfNecessary: %d", firstValidIndex);

		if( !isActive() ) return;
		if( PackType.FRAME != mPackType ) return;
		if( null == getHandler() ) return;

		getHandler().postDelayed( new Runnable() {
			@Override
			public void run () {
				if( firstValidIndex < 0 ) {
					createTutorialOverlayIfNecessaryDelayed( firstValidIndex );
				}
			}
		}, 200 );
	}

	private boolean createTutorialOverlayIfNecessaryDelayed ( final int firstValidIndex ) {
		mLogger.info( "createTutorialOverlayIfNecessaryDelayed: %d", firstValidIndex );

		if( ! isActive() ) {
			return false;
		}

		boolean shouldProceed = true;

		int count = mHList.getChildCount();
		int validIndex = - 1;
		View validView = null;
		boolean free = false;

		mLogger.log( "count: %d", count );

		for( int i = 0; i < count; i++ ) {
			View view = mHList.getChildAt( i );
			if( null != view ) {
				Object tag = view.getTag();
				if( null != tag && tag instanceof ViewHolder ) {
					ViewHolder holder = (ViewHolder) tag;

					if( holder.type == ListAdapter.TYPE_NORMAL ) {
						shouldProceed = false;
						break;
					}

					if( holder.type == ListAdapter.TYPE_EXTERNAL ) {
						ViewHolderExternal holder_ext = (ViewHolderExternal) holder;
						free = holder_ext.free == 1;
						if( free ) {
							validIndex = i;
							validView = holder_ext.image;
						}
						mLogger.log( "%d = %s, free = %d", i, holder_ext.identifier, holder_ext.free );
					}
				}
			}
		}

		if( ! free || ! ( validIndex > - 1 ) || null == validView ) {
			shouldProceed = false;
		}

		mLogger.log( "shouldProceed: %b", shouldProceed );

		if( ! shouldProceed ) {
			if( null != mOverlay ) {
				mOverlay.hide();
			}
			return false;
		}

		mLogger.log( "free item index: %d", validIndex );

		if( null == mOverlay ) {
			if( AviaryOverlay.shouldShow( getContext(), AviaryOverlay.ID_FRAMES )) {
				mOverlay = new FramesOverlay( getContext().getBaseActivity(), R.style.AviaryWidget_Overlay_Frames, validView );
				return mOverlay.show();
			}
		} else {
			mOverlay.update( validView );
		}
		return false;
	}

	// ///////////////
	// IAP - Dialog //
	// ///////////////

	private final void displayIAPDialog ( IAPUpdater data ) {
		mLogger.info("displayIAPDialog: %s", data);
		if( null != mIapDialog ) {
			if( mIapDialog.isValid() ) {
				mIapDialog.update( data );
				setApplyEnabled( false );
				return;
			} else {
				mIapDialog.dismiss( false );
				mIapDialog = null;
			}
		}

		IAPDialogMain dialog = IAPDialogMain.create( (FeatherContext) getContext().getBaseContext(), data );
		if( dialog != null ) {
			dialog.setOnCloseListener( new OnCloseListener() {
				@Override
				public void onClose () {
					removeIapDialog();
				}
			} );
		}
		mIapDialog = dialog;
		setApplyEnabled( false );

		// TODO: add "Store: Opened" tracking event
	}

	private boolean removeIapDialog () {
		setApplyEnabled( true );
		if( null != mIapDialog ) {
			mIapDialog.dismiss( true );
			mIapDialog = null;
			return true;
		}
		return false;
	}

	private void applyEffect(final int position, long delay) {
		if (! isActive() || null == getHandler()) return;
		getHandler().postDelayed(new Runnable() {
			@Override
			public void run() {
				if (! isActive() || null == mAdapter || null == mHList) return;
				mHList.clearChoices();

				if (position >= mHList.getFirstVisiblePosition() && position < mHList.getLastVisiblePosition() &&
				    position < mAdapter.getCount()) {
					View view = mHList.getChildAt(position - mHList.getFirstVisiblePosition());
					if (null != view && view instanceof EffectThumbLayout) {
						mHList.performItemClick(view, position, mAdapter.getItemId(position));
					}
				}
			}
		}, delay);
	}

	private void renderEffect( int position ) {
		if( null == mAdapter ) return;
		if( position < 0 || position >= mAdapter.getCount() ) return;

		Cursor cursor = (Cursor) mAdapter.getItem( position );
		if( null != cursor ) {
			TrayColumns.TrayCursorWrapper item = TrayColumns.TrayCursorWrapper.create( cursor );
			if( null != item ) {
				renderEffect( item, position );
			}
		}
	}

	private void renderEffect ( TrayColumns.TrayCursorWrapper item, int position ) {
		mLogger.info( "renderEffect: " + position );

		killCurrentTask();
		mCurrentTask = createRenderTask( position );
		mCurrentTask.execute( item );
	}

	protected RenderTask createRenderTask ( int position ) {
		return new RenderTask( position );
	}

	boolean killCurrentTask () {
		if( mCurrentTask != null ) {
			onProgressEnd();
			return mCurrentTask.cancel( true );
		}
		return false;
	}

	protected INativeFilter loadNativeFilter ( final TrayColumns.TrayCursorWrapper item, int position, boolean hires ) throws JSONException {

		BorderFilter filter = (BorderFilter) ToolLoaderFactory.get(ToolLoaderFactory.Tools.FRAMES);
		if( null != item && position > - 1 ) {
			Cursor cursor = getContext().getBaseContext()
					.getContentResolver()
					.query( PackageManagerUtils.getCDSProviderContentUri( getContext().getBaseContext(), "pack/content/item/" + item.getId() ), null, null, null, null );
			double frameWidth = 0;
			try {
				if( null != cursor ) {
					if( cursor.moveToFirst() ) {
						byte[] options = cursor.getBlob( cursor.getColumnIndex( PacksItemsColumns.OPTIONS ) );
						JSONObject object = new JSONObject( new String( options ) );
						frameWidth = object.getDouble( "width" );
					}
				}
			} finally {
				IOUtils.closeSilently( cursor );
			}

			filter.setHiRes( hires );
			filter.setSize( frameWidth );
			filter.setIdentifier( item.getIdentifier() );
			filter.setSourceDir( item.getPath() );
		}

		return filter;
	}

	boolean backHandled () {
		if( mIsAnimating ) {
			return true;
		}
		if( null != mIapDialog ) {
			if( mIapDialog.onBackPressed() ) {
				return true;
			}
			removeIapDialog();
			return true;
		}

		if( null != mOverlay ) {
			if( mOverlay.onBackPressed() ) {
				return true;
			}
		}

		killCurrentTask();
		return false;
	}

	static class ViewHolder {

		protected TextView text;
		protected ImageView image;
		protected int type;
		protected long id;
		protected String identifier;
		protected Object obj;
		protected boolean isNew;
	}

	static class ViewHolderExternal extends ViewHolder {
		protected ImageView externalIcon;
		protected int free;
	}

	class ListAdapter extends CursorAdapter {

		static final int TYPE_INVALID = - 1;
		static final int TYPE_LEFT_GETMORE = TrayColumns.TYPE_LEFT_GETMORE;
		static final int TYPE_RIGHT_GETMORE = TrayColumns.TYPE_RIGHT_GETMORE;
		static final int TYPE_NORMAL = TrayColumns.TYPE_CONTENT;
		static final int TYPE_EXTERNAL = TrayColumns.TYPE_PACK_EXTERNAL;
		static final int TYPE_DIVIDER = TrayColumns.TYPE_PACK_INTERNAL;
		static final int TYPE_LEFT_DIVIDER = TrayColumns.TYPE_LEFT_DIVIDER;
		static final int TYPE_RIGHT_DIVIDER = TrayColumns.TYPE_RIGHT_DIVIDER;

		Object mLock = new Object();
		LayoutInflater mInflater;
		int mDefaultResId;
		int mMoreResId;
		int mExternalResId;
		int mDividerResId;
		int mCount = - 1;

		int mIdColumnIndex;
		int mPackageNameColumnIndex;
		int mIdentifierColumnIndex;
		int mTypeColumnIndex;
		int mDisplayNameColumnIndex;
		int mPathColumnIndex;
		int mIsFreeColumnIndex;

		public ListAdapter ( Context context, int defaultResId, int moreResId, int externalResId, int dividerResId, Cursor cursor ) {
			super( context, cursor, 0 );
			initColumns( cursor );

			mInflater = LayoutInflater.from( context );

			mDefaultResId = defaultResId;
			mMoreResId = moreResId;
			mExternalResId = externalResId;
			mDividerResId = dividerResId;
		}

		private void initColumns ( Cursor cursor ) {
			if( null != cursor ) {
				mIdColumnIndex = cursor.getColumnIndex( TrayColumns._ID );
				mPackageNameColumnIndex = cursor.getColumnIndex( TrayColumns.PACKAGE_NAME );
				mIdentifierColumnIndex = cursor.getColumnIndex( TrayColumns.IDENTIFIER );
				mTypeColumnIndex = cursor.getColumnIndex( TrayColumns.TYPE );
				mDisplayNameColumnIndex = cursor.getColumnIndex( TrayColumns.DISPLAY_NAME );
				mPathColumnIndex = cursor.getColumnIndex( TrayColumns.PATH );
				mIsFreeColumnIndex = cursor.getColumnIndex( TrayColumns.IS_FREE );

				mLogger.log( "mIdColumnIndex: " + mIdColumnIndex );
				mLogger.log( "mPackageNameColumnIndex: " + mPackageNameColumnIndex );
				mLogger.log( "mIdentifierColumnIndex: " + mIdentifierColumnIndex );
				mLogger.log( "mTypeColumnIndex: " + mTypeColumnIndex );
				mLogger.log( "mDisplayNameColumnIndex: " + mDisplayNameColumnIndex );
				mLogger.log( "mPathColumnIndex: " + mPathColumnIndex );
			}
		}

		@Override
		public Cursor swapCursor ( Cursor newCursor ) {
			mLogger.info( "swapCursor" );
			initColumns( newCursor );
			return super.swapCursor( newCursor );
		}

		@Override
		protected void onContentChanged () {
			super.onContentChanged();
			mLogger.error( "onContentChanged!!!!" );
		}

		@Override
		public boolean hasStableIds () {
			return true;
		}

		@Override
		public int getViewTypeCount () {
			return 7;
		}

		@Override
		public int getItemViewType ( int position ) {
			Cursor cursor = (Cursor) getItem( position );
			if( null != cursor ) {
				return cursor.getInt( mTypeColumnIndex );
			}
			return TYPE_INVALID;
		}

		@Override
		public View getView ( int position, View convertView, ViewGroup parent ) {
			if( ! mDataValid ) {
				throw new IllegalStateException( "this should only be called when the cursor is valid" );
			}

			View v;
			if( convertView == null ) {
				v = newView( mContext, mCursor, parent, position );
			} else {
				v = convertView;
			}
			bindView( v, mContext, mCursor, position );
			return v;
		}

		private View newView ( Context context, Cursor cursor, ViewGroup parent, int position ) {

			final int type = getItemViewType( position );

			View view;
			int layoutWidth;
			ViewHolder holder;

			switch( type ) {
				case TYPE_LEFT_GETMORE:
					view = mInflater.inflate( mMoreResId, parent, false );
					( (ImageView) view.findViewById( R.id.aviary_image ) ).setImageResource( mPackType == PackType.EFFECT ? R.drawable.aviary_effect_item_getmore : R.drawable.aviary_frame_item_getmore );
					layoutWidth = mCellWidth;
					break;

				case TYPE_RIGHT_GETMORE:
					view = mInflater.inflate( mMoreResId, parent, false );
					( (ImageView) view.findViewById( R.id.aviary_image ) ).setImageResource( mPackType == PackType.EFFECT ? R.drawable.aviary_effect_item_getmore : R.drawable.aviary_frame_item_getmore );
					layoutWidth = mCellWidth;

					if( parent.getChildCount() > 0 && mHList.getFirstVisiblePosition() == 0 ) {
						View lastView = parent.getChildAt( parent.getChildCount() - 1 );

						if( lastView.getRight() < parent.getWidth() ) {
							view.setVisibility( View.INVISIBLE );
							layoutWidth = 1;
						}
					}

					break;

				case TYPE_DIVIDER:
					view = mInflater.inflate( mDividerResId, parent, false );
					layoutWidth = LayoutParams.WRAP_CONTENT;
					break;

				case TYPE_EXTERNAL:
					view = mInflater.inflate( mExternalResId, parent, false );
					layoutWidth = mCellWidth;
					break;

				case TYPE_LEFT_DIVIDER:
					view = mInflater.inflate( R.layout.aviary_thumb_divider_right, parent, false );
					layoutWidth = LayoutParams.WRAP_CONTENT;
					break;

				case TYPE_RIGHT_DIVIDER:
					view = mInflater.inflate( R.layout.aviary_thumb_divider_left, parent, false );
					layoutWidth = LayoutParams.WRAP_CONTENT;

					if( parent.getChildCount() > 0 && mHList.getFirstVisiblePosition() == 0 ) {
						View lastView = parent.getChildAt( parent.getChildCount() - 1 );

						if( lastView.getRight() < parent.getWidth() ) {
							view.setVisibility( View.INVISIBLE );
							layoutWidth = 1;
						}
					}
					break;

				case TYPE_NORMAL:
				default:
					view = mInflater.inflate( mDefaultResId, parent, false );
					layoutWidth = mCellWidth;
					break;
			}

			view.setLayoutParams( new LayoutParams( layoutWidth, LayoutParams.MATCH_PARENT ) );

			if( type == TYPE_EXTERNAL ) {
				holder = new ViewHolderExternal();
//				( (ViewHolderExternal) holder ).externalIcon = (ImageView) view.findViewById( R.id.aviary_image2 );
			} else {
				holder = new ViewHolder();
			}

			holder.type = type;
			holder.image = (ImageView) view.findViewById( R.id.aviary_image );
			holder.text = (TextView) view.findViewById( R.id.aviary_text );

			if( type != TYPE_DIVIDER && holder.image != null ) {
				LayoutParams params = holder.image.getLayoutParams();
				params.height = mThumbSize;
				params.width = mThumbSize;
				holder.image.setLayoutParams( params );
			}

			view.setTag( holder );
			return view;
		}

		void bindView ( View view, Context context, Cursor cursor, int position ) {
			final ViewHolder holder = (ViewHolder) view.getTag();
			String displayName;
			String identifier;
			String path;
			Generator executor;
			long id = - 1;

			if( ! cursor.isAfterLast() && ! cursor.isBeforeFirst() ) {
				id = cursor.getLong( mIdColumnIndex );
			}

			boolean is_checked = mHList.getCheckedItemPositions()
					.get( position, false );

			if( holder.type == TYPE_NORMAL ) {
				displayName = cursor.getString( mDisplayNameColumnIndex );
				identifier = cursor.getString( mIdentifierColumnIndex );
				path = cursor.getString( mPathColumnIndex );

				holder.text.setText( displayName );
				holder.identifier = identifier;

				if( holder.id != id ) {
					final String file;

					if( mPackType == PackType.EFFECT ) {
						file = Picasso.SCHEME_CUSTOM + "://" + path + "/" + identifier + ".json";
					} else {
						if( ! path.startsWith( ContentResolver.SCHEME_FILE + "://" ) ) {
							path = ContentResolver.SCHEME_FILE + "://" + path;
						}
						file = path + "/" + identifier + "-small.png";
					}

					executor = createContentCallable( position, position, identifier, path );

					RequestCreator request = mPicassoLibrary.load( Uri.parse( file ) )
							.fade( 200 )
							.error( R.drawable.aviary_ic_na )
							.withCache( mCache );

					if( null != executor ) {
						request.withGenerator( executor );
					}
					request.into( holder.image );
				}

				EffectThumbLayout effectThumbLayout = (EffectThumbLayout) view;
				effectThumbLayout.setIsOpened( is_checked );

			} else if( holder.type == TYPE_EXTERNAL ) {
				ViewHolderExternal holderExternal = (ViewHolderExternal) holder;

				identifier = cursor.getString( mIdentifierColumnIndex );
				displayName = cursor.getString( mDisplayNameColumnIndex );
				String icon = cursor.getString( mPathColumnIndex );
				int free = cursor.getInt( mIsFreeColumnIndex );

				holder.text.setText( displayName );
				holder.identifier = identifier;
				holderExternal.free = free;

//				if( free == 1 ) {
//					holderExternal.externalIcon.setVisibility( View.INVISIBLE );
//				} else {
//					holderExternal.externalIcon.setVisibility( View.VISIBLE );
//				}

				if( holder.id != id ) {
					mPicassoLibrary.load( icon )
							.transform( new PackIconCallable.Builder().withResources( getContext().getBaseContext()
									                                                          .getResources() )
									            .withPackType( mPackType )
									            .withPath( icon )
									            .build() )
							.error( R.drawable.aviary_ic_na )
							.into( holder.image );
				}

			} else if( holder.type == TYPE_DIVIDER ) {
				Drawable drawable = holder.image.getDrawable();
				displayName = cursor.getString( mDisplayNameColumnIndex );

				if( drawable instanceof PluginDividerDrawable ) {
					( (PluginDividerDrawable) drawable ).setTitle( displayName );
				} else {
					PluginDividerDrawable d = new PluginDividerDrawable( getContext().getBaseContext(), R.attr.aviaryEffectThumbDividerTextStyle, displayName );
					holder.image.setImageDrawable( d );
				}
			}

			holder.id = id;
		}

		@Override
		public View newView ( Context arg0, Cursor arg1, ViewGroup arg2 ) {
			return null;
		}

		@Override
		public void bindView ( View arg0, Context arg1, Cursor arg2 ) {}

		protected Generator createContentCallable ( long id, int position, String identifier, String path ) {
			return null;
		}
	}

	// ////////////////////////
	// OnItemClickedListener //
	// ////////////////////////

	@Override
	public void onItemClick ( it.sephiroth.android.library.widget.AdapterView<?> parent, View view, int position, long id ) {
		mLogger.info( "onItemClick: " + position );

		if( null != mOverlay ) {
			mOverlay.hide();
		}

		int checkedItemsCount = mHList.getCheckedItemCount();

		// get the current selection and remove the current position
		SparseArrayCompat<Boolean> checked = mHList.getCheckedItemPositions().clone();
		checked.remove( position );


		if( isActive() ) {
			ViewHolder holder = (ViewHolder) view.getTag();

			if( null != holder ) {

				final boolean valid_position = holder.type == ListAdapter.TYPE_NORMAL;

				if( holder.type == ListAdapter.TYPE_LEFT_GETMORE || holder.type == ListAdapter.TYPE_RIGHT_GETMORE ) {

					String side = holder.type == ListAdapter.TYPE_RIGHT_GETMORE ? "right" : "left";

					displayIAPDialog( new IAPUpdater.Builder().setPackType( mPackType )
							                  .setEvent( "shop_list: opened" )
							                  .setFeaturedPackId( - 1 )
							                  .addEventAttributes( "from", getName().name()
									                  .toLowerCase( Locale.US ) )
							                  .addEventAttributes( "side", side )
							                  .build() );

				} else if( holder.type == ListAdapter.TYPE_EXTERNAL ) {

					displayIAPDialog( new IAPUpdater.Builder().setPackId( holder.id )
							                  .setPackType( mPackType )
							                  .setFeaturedPackId( holder.id )
							                  .setEvent( "shop_details: opened" )
							                  .addEventAttributes( "pack", holder.identifier )
							                  .addEventAttributes( "from", "featured" )
							                  .build() );

				} else if( holder.type == ListAdapter.TYPE_NORMAL ) {
					removeIapDialog();

					if( checkedItemsCount > 0 ) {
						renderEffect(position);
					} else {
						renderEffect( null, - 1 );
					}
				}

				if( valid_position ) {
					EffectThumbLayout layout = (EffectThumbLayout) view;

					if( layout.isChecked() ) {
						layout.open();
					} else {
						layout.close();
					}
				} else {
					mHList.setItemChecked( position, false );
				}

				if( checked.size() > 0 && valid_position ) {
					mHList.setItemChecked( checked.keyAt( 0 ), false );
				}

			}
		}

		// mHList.setItemChecked( position, true );
	}

	// /////////////////////////
	// OnItemSelectedListener //
	// /////////////////////////

	@Override
	public void onItemSelected ( it.sephiroth.android.library.widget.AdapterView<?> arg0, View arg1, int arg2, long arg3 ) {
	}

	@Override
	public void onNothingSelected ( it.sephiroth.android.library.widget.AdapterView<?> parent ) {
	}

	protected CharSequence[] getOptionalEffectsLabels () {
		if( null != mConfigService ) {
			return new CharSequence[]{ mConfigService.getString( R.string.feather_original ) };
		} else {
			return new CharSequence[]{ "Original" };
		}
	}

	/**
	 * Render the selected effect
	 */
	protected class RenderTask extends AviaryAsyncTask<TrayColumns.TrayCursorWrapper, Bitmap, Bitmap> implements OnCancelListener {

		int mPosition;
		String mError;
		MoaResult mMoaMainExecutor;
		TrayColumns.TrayCursorWrapper currentEffect;
		MoaActionList actionList;

		/**
		 * Instantiates a new render task.
		 *
		 * @param
		 */
		public RenderTask ( final int position ) {
			mPosition = position;
		}

		@Override
		protected void PreExecute () {
			onProgressStart();
		}

		private INativeFilter initFilter ( TrayColumns.TrayCursorWrapper item, int position ) {
			final INativeFilter filter;

			try {
				filter = loadNativeFilter( item, position, true );
			} catch( Throwable t ) {
				t.printStackTrace();
				return null;
			}

			actionList = (MoaActionList) filter.getActions().clone();

			if( filter instanceof BorderFilter ) {
				( (BorderFilter) filter ).setHiRes( false );
			}

			try {
				mMoaMainExecutor = filter.prepare( mBitmap, mPreview, 1, 1 );
			} catch( JSONException e ) {
				e.printStackTrace();
				mMoaMainExecutor = null;
				return null;
			}
			return filter;
		}

		protected MoaResult initPreview ( INativeFilter filter ) {
			return null;
		}

		public void doFullPreviewInBackground () {
			mMoaMainExecutor.execute();
		}

		@Override
		public Bitmap doInBackground ( final TrayColumns.TrayCursorWrapper... params ) {

			if( isCancelled() ) {
				return null;
			}

			final TrayColumns.TrayCursorWrapper item = params[0];
			currentEffect = item;

			initFilter( item, mPosition );

			mIsRendering = true;

			if( isCancelled() ) {
				return null;
			}

			// rendering the full preview
			try {
				doFullPreviewInBackground();
			} catch( Exception exception ) {
				mError = exception.getMessage();
				exception.printStackTrace();
				return null;
			}

			if( ! isCancelled() ) {
				return mMoaMainExecutor.outputBitmap;
			} else {
				return null;
			}
		}

		@Override
		public void PostExecute ( final Bitmap result ) {

			if( ! isActive() ) {
				return;
			}

			mPreview = result;
			mRenderedEffect = currentEffect;

			if( result == null || mMoaMainExecutor == null || mMoaMainExecutor.active == 0 ) {
				onRestoreOriginalBitmap();

				if( mError != null ) {
					onGenericError( mError, android.R.string.ok, null );
				}
				setIsChanged( false );
			} else {
				onApplyNewBitmap( result );

				if (null != mRenderedEffect) {
					HashMap<String, String> attrs = new HashMap<String, String>();
					attrs.put("pack", mRenderedEffect.getPackageName());
					attrs.put("item", mRenderedEffect.getIdentifier());
					getContext().getTracker().tagEventAttributes(getName().name().toLowerCase(Locale.US) + ": item_previewed", attrs);

					// filling the edit result
					ToolActionVO<String> toolAction = new ToolActionVO<String>();
					toolAction.setPackIdentifier(mRenderedEffect.getPackageName());
					toolAction.setContentIdentifier(mRenderedEffect.getIdentifier());

					mEditResult.setActionList(actionList);
					mEditResult.setToolAction(toolAction);

					mTrackingAttributes.put( "item", mRenderedEffect.getIdentifier() );
					mTrackingAttributes.put( "pack", mRenderedEffect.getPackageName() );
				} else {
					mEditResult.setToolAction(null);
					mEditResult.setActionList(null);

					mTrackingAttributes.remove("item");
					mTrackingAttributes.remove("pack");
				}
			}

			onProgressEnd();

			mIsRendering = false;
			mCurrentTask = null;
		}

		protected void onApplyNewBitmap ( final Bitmap result ) {
			if( SystemUtils.isHoneyComb() ) {
				Moa.notifyPixelsChanged( result );
			}
			onPreviewChanged( result, false, true );
			setIsChanged( mRenderedEffect != null );
		}

		protected void onRestoreOriginalBitmap () {
			// restore the original bitmap...

			onPreviewChanged( mBitmap, true, true );
			setIsChanged( false );
		}

		@Override
		public void onCancelled () {
			super.onCancelled();

			if( mMoaMainExecutor != null ) {
				mMoaMainExecutor.cancel();
			}
			mIsRendering = false;
		}

		@Override
		public void onCancel ( DialogInterface dialog ) {
			cancel( true );
		}
	}

	/**
	 * Used to generate the Bitmap result. If user clicks on the "Apply" button when an
	 * effect is still rendering, then starts this
	 * task.
	 */
	class GenerateResultTask extends AviaryAsyncTask<Void, Void, Void> {

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
		protected Void doInBackground ( Void... params ) {

			mLogger.info( "GenerateResultTask::doInBackground", mIsRendering );

			while( mIsRendering ) {
				mLogger.log( "waiting...." );
			}

			return null;
		}

		@Override
		protected void PostExecute ( Void result ) {
			if( getContext().getBaseActivity()
					.isFinishing() ) {
				return;
			}
			if( mProgress.isShowing() ) {
				mProgress.dismiss();
			}

			onComplete( mPreview );
		}
	}

}
