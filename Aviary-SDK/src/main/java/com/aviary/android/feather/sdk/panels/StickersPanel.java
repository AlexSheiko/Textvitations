package com.aviary.android.feather.sdk.panels;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.content.Loader.OnLoadCompleteListener;
import android.support.v4.widget.CursorAdapter;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.aviary.android.feather.cds.AviaryCds;
import com.aviary.android.feather.cds.AviaryCds.PackType;
import com.aviary.android.feather.cds.AviaryCds.Size;
import com.aviary.android.feather.cds.CdsUtils;
import com.aviary.android.feather.cds.PacksItemsColumns;
import com.aviary.android.feather.cds.TrayColumns;
import com.aviary.android.feather.common.tracking.AviaryTracker;
import com.aviary.android.feather.common.utils.ApiHelper;
import com.aviary.android.feather.common.utils.IOUtils;
import com.aviary.android.feather.common.utils.PackageManagerUtils;
import com.aviary.android.feather.headless.moa.MoaActionFactory;
import com.aviary.android.feather.library.Constants;
import com.aviary.android.feather.library.content.ToolEntry;
import com.aviary.android.feather.library.filters.StickerFilter;
import com.aviary.android.feather.library.filters.ToolLoaderFactory;
import com.aviary.android.feather.library.graphics.drawable.FeatherDrawable;
import com.aviary.android.feather.library.graphics.drawable.StickerDrawable;
import com.aviary.android.feather.library.services.ConfigService;
import com.aviary.android.feather.library.services.DragController;
import com.aviary.android.feather.library.services.DragControllerService;
import com.aviary.android.feather.library.services.DragControllerService.DragListener;
import com.aviary.android.feather.library.services.DragControllerService.DragSource;
import com.aviary.android.feather.library.services.IAviaryController;
import com.aviary.android.feather.library.services.PreferenceService;
import com.aviary.android.feather.library.services.drag.DragView;
import com.aviary.android.feather.library.services.drag.DropTarget;
import com.aviary.android.feather.library.services.drag.DropTarget.DropTargetListener;
import com.aviary.android.feather.library.utils.BitmapUtils;
import com.aviary.android.feather.library.utils.MatrixUtils;
import com.aviary.android.feather.library.utils.UIConfiguration;
import com.aviary.android.feather.library.vo.EditToolResultVO;
import com.aviary.android.feather.library.vo.ToolActionVO;
import com.aviary.android.feather.sdk.AviaryMainController.FeatherContext;
import com.aviary.android.feather.sdk.R;
import com.aviary.android.feather.sdk.overlays.AviaryOverlay;
import com.aviary.android.feather.sdk.overlays.StickersOverlay;
import com.aviary.android.feather.sdk.panels.BordersPanel.ViewHolder;
import com.aviary.android.feather.sdk.panels.BordersPanel.ViewHolderExternal;
import com.aviary.android.feather.sdk.panels.SimpleStatusMachine.OnStatusChangeListener;
import com.aviary.android.feather.sdk.utils.PackIconCallable;
import com.aviary.android.feather.sdk.widget.DrawableHighlightView;
import com.aviary.android.feather.sdk.widget.DrawableHighlightView.OnDeleteClickListener;
import com.aviary.android.feather.sdk.widget.IAPDialogMain;
import com.aviary.android.feather.sdk.widget.IAPDialogMain.IAPUpdater;
import com.aviary.android.feather.sdk.widget.IAPDialogMain.OnCloseListener;
import com.aviary.android.feather.sdk.widget.ImageViewDrawableOverlay;
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.AnimatorSet;
import com.nineoldandroids.animation.ObjectAnimator;
import com.nineoldandroids.view.ViewHelper;

import junit.framework.Assert;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;

import it.sephiroth.android.library.imagezoom.ImageViewTouchBase.DisplayType;
import it.sephiroth.android.library.picasso.Callback;
import it.sephiroth.android.library.picasso.Picasso;
import it.sephiroth.android.library.widget.AdapterView.OnItemClickListener;
import it.sephiroth.android.library.widget.AdapterView.OnItemLongClickListener;
import it.sephiroth.android.library.widget.HListView;

public class StickersPanel extends AbstractContentPanel
		implements OnStatusChangeListener, OnItemClickListener, DragListener, DragSource, DropTargetListener, OnLoadCompleteListener<Cursor> {

	private static final int STATUS_NULL = SimpleStatusMachine.INVALID_STATUS;
	private static final int STATUS_PACKS = 1;
	private static final int STATUS_STICKERS = 2;

	private final List<Long> mInstalledPacks = new ArrayList<Long>();
	private final List<Long> mNewPacks = new ArrayList<Long>( 0 );
	private long mLastInstalledPack;

	/** panel's status */
	private SimpleStatusMachine mStatus;

	/** horizontal listview for stickers packs */
	private HListView mListPacks;

	/** horizontal listview for stickers items */
	private HListView mListStickers;

	/** view flipper for switching between lists */
	private ViewFlipper mViewFlipper;

	private Picasso mPicassoLib;

	/** canvas used to draw stickers */
	private Canvas mCanvas;

	private int mPackCellWidth;
	private int mStickerCellWidth;

	/** required services */
	private ConfigService mConfigService;
	private DragControllerService mDragControllerService;
	private PreferenceService mPreferenceService;

	/** iap dialog for inline previews */
	private IAPDialogMain mIapDialog;

	private StickerFilter mCurrentFilter;

	private int mPackThumbSize;
	private int mStickerThumbSize;

	private boolean mFirstTime = true;

	protected CursorAdapter mAdapterPacks;
	protected CursorAdapter mAdapterStickers;
	protected CursorLoader mCursorLoaderPacks;
	protected ContentObserver mContentObserver;

	// for status_sticker
	private StickerPackInfo mPackInfo;

	int mItemCount = 0;
	int mStickersOnScreen = 0;

	// pack, sticker
	List<Pair<String, String>> mItemApplied = new ArrayList<Pair<String, String>>(0);

	// tutorial overlay
	StickersOverlay mOverlay;

	@Override
	public void onLoadComplete(Loader<Cursor> loader, Cursor cursor) {
		mLogger.info("onLoadComplete: " + cursor + ", currentStatus: " + mStatus.getCurrentStatus());

		long iapDialogFeaturedId = - 1;
		int lastInstalledPackIndex = - 1;
		int firstValidIndex = - 1;
		int newStatus = STATUS_PACKS;
		int index = 0;
		int cursorSize = 0;

		long options_pack_id = -1;
		long options_content_id = -1;
		boolean smooth_selection = false;
		boolean force_update = false;

		boolean checkFromIap = (!mFirstTime && null != mIapDialog && mIapDialog.isValid() && null != mIapDialog.getParent() && null != mIapDialog.getData());
		boolean checkFromOptions = false;
		boolean skipTutorial = false;
		boolean applySelected = false;

		mLogger.info("checkFromIap: %b", checkFromIap);

		if (checkFromIap) {
			IAPUpdater data = mIapDialog.getData();

			if (data.getFeaturedPackId() == data.getPackId() && data.getFeaturedPackId() > - 1) {
				iapDialogFeaturedId = data.getFeaturedPackId();
			}
		}

		checkFromIap = iapDialogFeaturedId > - 1;
		mLastInstalledPack = - 1;

		if (hasOptions() && mFirstTime && ! checkFromIap) {
			final Bundle options = getOptions();

			options_pack_id = options.getLong(Constants.QuickLaunch.CONTENT_PACK_ID, -1);
			options_content_id = options.getLong(Constants.QuickLaunch.CONTENT_ITEM_ID, -1);
			mLogger.verbose("options_pack_id: %s, options_content_id: %s", options_pack_id, options_content_id);
			options.remove(Constants.QuickLaunch.CONTENT_PACK_ID);

			// remove the extra from the option bundle, since it's a one time shot
			checkFromOptions = options_pack_id > - 1 && options_content_id > - 1;
		}

		List<Long> tmpList = new ArrayList<Long>();

		if (null != cursor) {
			index = cursor.getPosition();
			while (cursor.moveToNext()) {
				int type = cursor.getInt(TrayColumns.TYPE_COLUMN_INDEX);
				if (type == TrayColumns.TYPE_PACK_INTERNAL) {

					long pack_id = cursor.getLong(TrayColumns.ID_COLUMN_INDEX);
					String identifier = cursor.getString(TrayColumns.IDENTIFIER_COLUMN_INDEX);

					mLogger.log("%d = %s", pack_id, identifier);

					tmpList.add(pack_id);

					if (! mInstalledPacks.contains(pack_id) && ! mFirstTime) {
						mNewPacks.add(pack_id);
						mLogger.log("adding %d (%s) to new packs", pack_id, identifier);
						mLogger.verbose("iapDialogFeaturedId: %d, pack_id: %d", mLastInstalledPack, pack_id);

						if (checkFromIap) {
							if (iapDialogFeaturedId == pack_id && mLastInstalledPack == - 1) {
								mLastInstalledPack = pack_id;
								lastInstalledPackIndex = cursor.getPosition();
								checkFromIap = false;
								smooth_selection = true;
							}
						}
					}
					else if (checkFromOptions) {
						mLogger.verbose("checking options.. %s", identifier);

						if (options_pack_id == pack_id && mPackInfo == null ) {
							mLogger.log("found pack to auto-select: %s", identifier);

							lastInstalledPackIndex = -1;
							options_pack_id = -1;

							mPackInfo = new StickerPackInfo(cursor.getLong(TrayColumns.ID_COLUMN_INDEX), cursor.getString(TrayColumns.IDENTIFIER_COLUMN_INDEX));
							newStatus = STATUS_STICKERS;

							checkFromOptions = false;
							skipTutorial = true;
						}

					}

					if (firstValidIndex == - 1) {
						firstValidIndex = cursor.getPosition();
					}
					// break;
				}
			}
			cursorSize = cursor.getCount();
			cursor.moveToPosition(index);
		}

		mInstalledPacks.clear();
		mInstalledPacks.addAll(tmpList);

		if (firstValidIndex == 0 && cursorSize == 1 && null != cursor && mStatus.getCurrentStatus() != STATUS_STICKERS) {
			// we have only 1 installed pack and nothing else, so just
			// display its content
			index = cursor.getPosition();

			if (cursor.moveToFirst()) {
				int packType = cursor.getInt(TrayColumns.TYPE_COLUMN_INDEX);

				if (packType == StickerPacksAdapter.TYPE_DIVIDER) {
					mLogger.log("one pack only, show it");
					mPackInfo = new StickerPackInfo(cursor.getLong(TrayColumns.ID_COLUMN_INDEX), cursor.getString(TrayColumns.IDENTIFIER_COLUMN_INDEX));
					newStatus = STATUS_STICKERS;
				}
			}
			cursor.moveToPosition(index);

			skipTutorial = true;
			lastInstalledPackIndex = - 1;
		}

		int currentstatus = mStatus.getCurrentStatus();

		mLogger.log("mLastInstalledPack: %d", mLastInstalledPack);
		mLogger.log("lastInstalledPackIndex: %d", lastInstalledPackIndex);
		mLogger.log("currentStatus: " + currentstatus);
		mLogger.log("newStatus: " + newStatus);

		// Let's update the status only if we're not already
		// inside the "stickers" mode because we don't want to
		// break the user experience
		if (currentstatus != STATUS_STICKERS) {
			mStatus.setStatus(newStatus);
		}

		mAdapterPacks.changeCursor(cursor);

		if (lastInstalledPackIndex >= 0) {
			force_update = true;
			firstValidIndex = lastInstalledPackIndex;
			removeIapDialog();
		}

		onStickersPackListUpdated(cursor, firstValidIndex, force_update, smooth_selection);

		// check optional messaging
		if (openStorePanelIfRequired(options_pack_id)) {
			return;
		}

		// skip the tutorial overlay if requested
		if (skipTutorial) return;

		createTutorialOverlayIfNecessary(firstValidIndex, mStatus.getCurrentStatus());
	}

	private boolean openStorePanelIfRequired(long id) {
		// check optional messaging
		long iapPackageId = - 1;
		if( hasOption(Constants.QuickLaunch.CONTENT_PACK_ID) || id > -1) {
			if(id > -1){
				iapPackageId = id;
			} else {
				Bundle options = getOptions();
				iapPackageId = options.getLong(Constants.QuickLaunch.CONTENT_PACK_ID);
				options.remove(Constants.QuickLaunch.CONTENT_PACK_ID);
			}

			// display the iap dialog
			if( iapPackageId > - 1 ) {
				//@formatter:off
				IAPUpdater iapData = new IAPUpdater.Builder()
						.setPackId( iapPackageId )
						.setFeaturedPackId( iapPackageId )
						.setPackType( PackType.STICKER )
						.setEvent( "shop_details: opened" )
						.addEventAttributes( "pack", String.valueOf( iapPackageId ) )
						.addEventAttributes( "from", "message" )
						.build();
				//@formatter:on
				displayIAPDialog( iapData );
				return true;
			}
		}
		return false;
	}

	private void createTutorialOverlayIfNecessary ( final int firstValidIndex, int currentStatus ) {
		mLogger.info("createTutorialOverlayIfNecessary: %d, %d", firstValidIndex, currentStatus);

		if( currentStatus != STATUS_PACKS ) return;
		if( !isActive() ) return;
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

		if( mStatus.getCurrentStatus() != STATUS_PACKS ) {
			return false;
		}

		boolean shouldProceed = true;

		int count = mListPacks.getChildCount();
		int validIndex = - 1;
		View validView = null;
		boolean free = false;

		mLogger.log( "count: %d", count );

		for( int i = 0; i < count; i++ ) {
			View view = mListPacks.getChildAt( i );
			if( null != view ) {
				Object tag = view.getTag();
				if( null != tag && tag instanceof ViewHolder ) {
					ViewHolder holder = (ViewHolder) tag;

					if( holder.type == StickerPacksAdapter.TYPE_DIVIDER ) {
						shouldProceed = false;
						break;
					}

					if( holder.type == StickerPacksAdapter.TYPE_EXTERNAL ) {
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
			if( AviaryOverlay.shouldShow( getContext(), AviaryOverlay.ID_STICKERS )) {
				mOverlay = new StickersOverlay( getContext().getBaseActivity(), R.style.AviaryWidget_Overlay_Stickers, validView );
				return mOverlay.show();
			}
		} else {
			mOverlay.update( validView );
		}
		return false;
	}

	public StickersPanel ( IAviaryController context, ToolEntry entry ) {
		super(context, entry);
	}

	@Override
	public void onCreate ( Bitmap bitmap, Bundle options ) {
		super.onCreate( bitmap, options );

		mStatus = new SimpleStatusMachine();

		mInstalledPacks.clear();
		mNewPacks.clear();

		// init layout components
		mListPacks = (HListView) getOptionView().findViewById( R.id.aviary_list_packs );
		mListStickers = (HListView) getOptionView().findViewById( R.id.aviary_list_stickers );
		mViewFlipper = (ViewFlipper) getOptionView().findViewById( R.id.aviary_flipper );
		mImageView = (ImageViewDrawableOverlay) getContentView().findViewById( R.id.aviary_overlay );

		// init services
		mConfigService = getContext().getService( ConfigService.class );
		mPreferenceService = getContext().getService( PreferenceService.class );

		// setup the main imageview
		( (ImageViewDrawableOverlay) mImageView ).setDisplayType( DisplayType.FIT_IF_BIGGER );
		( (ImageViewDrawableOverlay) mImageView ).setForceSingleSelection( false );
		( (ImageViewDrawableOverlay) mImageView ).setDropTargetListener( this );
		( (ImageViewDrawableOverlay) mImageView ).setScaleWithContent( true );

		// create the default action list
		mEditResult.setActionList(MoaActionFactory.actionList());

		mPicassoLib = Picasso.with( getContext().getBaseContext() );

		// create the preview for the main imageview
		createAndConfigurePreview();

		DragControllerService dragger = getContext().getService( DragControllerService.class );
		dragger.addDropTarget( (DropTarget) mImageView );
		dragger.setMoveTarget( mImageView );
		dragger.setDragListener( this );

		mDragControllerService = dragger;
	}

	@Override
	public void onActivate () {
		super.onActivate();

		mImageView.setImageBitmap( mPreview, null, - 1, UIConfiguration.IMAGE_VIEW_MAX_ZOOM );

		mPackCellWidth = mConfigService.getDimensionPixelSize( R.dimen.aviary_sticker_pack_width );
		mPackThumbSize = mConfigService.getDimensionPixelSize( R.dimen.aviary_sticker_pack_image_width );
		mStickerCellWidth = mConfigService.getDimensionPixelSize( R.dimen.aviary_sticker_single_item_width );
		mStickerThumbSize = mConfigService.getDimensionPixelSize( R.dimen.aviary_sticker_single_item_image_width );

		// register to status change
		mStatus.setOnStatusChangeListener( this );
		updateInstalledPacks();

		getContentView().setVisibility( View.VISIBLE );
		contentReady();
	}

	@Override
	protected void onComplete(final Bitmap bitmap, final EditToolResultVO editResult) {
		mTrackingAttributes.put("item_count", String.valueOf(mItemCount));
		super.onComplete(bitmap, editResult);
	}

	@Override
	public boolean onBackPressed () {
		mLogger.info("onBackPressed");

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

		// we're in the packs status
		if( mStatus.getCurrentStatus() == STATUS_PACKS ) {
			if( stickersOnScreen() ) {
				askToLeaveWithoutApply();
				return true;
			}
			return false;
		}

		// we're in the stickers status
		if( mStatus.getCurrentStatus() == STATUS_STICKERS ) {

			int packsCount = 0;
			if( null != mAdapterPacks ) {
				packsCount = mAdapterPacks.getCount();
			}

			mLogger.log( "packsCount: %d", packsCount );

			if( packsCount > 1 ) {
				mStatus.setStatus( STATUS_PACKS );
				return true;
			} else {
				if( stickersOnScreen() ) {
					askToLeaveWithoutApply();
					return true;
				}
				return false;
			}
		}
		return super.onBackPressed();
	}

	@Override
	public boolean onCancel () {

		mLogger.info("onCancel");

		// if there's an active sticker on screen
		// then ask if we really want to exit this panel
		// and discard changes
		if( stickersOnScreen() ) {
			askToLeaveWithoutApply();
			return true;
		}

		return super.onCancel();
	}

	@Override
	public void onDeactivate () {
		super.onDeactivate();

		if( null != mOverlay ) {
			mOverlay.dismiss();
			mOverlay = null;
		}

		// disable the drag controller
		if( null != getDragController() ) {
			getDragController().deactivate();
			getDragController().removeDropTarget( (DropTarget) mImageView );
			getDragController().setDragListener( null );
		}
		setDragController( null );

		if( null != mAdapterPacks ) {
			mAdapterPacks.changeCursor( null );
		}

		if( null != mAdapterStickers ) {
			mAdapterStickers.changeCursor( null );
		}

		mListPacks.setAdapter( null );
		mListStickers.setAdapter( null );

		// mPluginService.removeOnUpdateListener( this );
		mStatus.setOnStatusChangeListener( null );

		mListPacks.setOnItemClickListener( null );
		mListStickers.setOnItemClickListener( null );
		mListStickers.setOnItemLongClickListener( null );

		removeIapDialog();

		Context context = getContext().getBaseContext();

		if( null != mContentObserver ) {
			context.getContentResolver()
					.unregisterContentObserver( mContentObserver );
		}

		if( null != mCursorLoaderPacks ) {
			mLogger.info( "stop load cursorloader..." );
			mCursorLoaderPacks.unregisterListener( this );
			mCursorLoaderPacks.stopLoading();
		}
	}

	@Override
	public void onDestroy () {
		super.onDestroy();

		mItemApplied.clear();

		( (ImageViewDrawableOverlay) mImageView ).clearOverlays();
		mCurrentFilter = null;

		if( null != mCursorLoaderPacks ) {
			mLogger.info("disposing cursorloader...");
			mCursorLoaderPacks.abandon();
			mCursorLoaderPacks.reset();
		}

		if( null != mAdapterPacks ) {
			IOUtils.closeSilently(mAdapterPacks.getCursor());
		}

		if( null != mAdapterStickers ) {
			IOUtils.closeSilently(mAdapterStickers.getCursor());
		}

		mAdapterPacks = null;
		mAdapterStickers = null;
		mCursorLoaderPacks = null;
	}

	@Override
	protected void onDispose () {
		super.onDispose();
		mCanvas = null;
	}

	@Override
	protected void onGenerateResult () {
		onApplyCurrent();
		onSendEvents();
		super.onGenerateResult( mEditResult );
	}

	private void onSendEvents () {
		mLogger.info("onSendEvents");
		if( null != getContext() ) {
			final AviaryTracker tracker = getContext().getTracker();
			Iterator<Pair<String, String>> iterator = mItemApplied.iterator();
			while( iterator.hasNext() ) {
				Pair<String, String> item = iterator.next();
				tracker.tagEvent( "stickers: item_saved", "pack", item.first, "item", item.second );
			}
		}
	}

	@Override
	public void onConfigurationChanged ( Configuration newConfig, Configuration oldConfig ) {
		mLogger.info("onConfigurationChanged: " + newConfig);

		if( mIapDialog != null ) {
			mIapDialog.onConfigurationChanged( newConfig );
		}
		super.onConfigurationChanged(newConfig, oldConfig);
	}

	@SuppressLint ("InflateParams")
	@Override
	protected View generateContentView ( LayoutInflater inflater ) {
		return inflater.inflate( R.layout.aviary_content_stickers, null );
	}

	@Override
	protected ViewGroup generateOptionView ( LayoutInflater inflater, ViewGroup parent ) {
		return (ViewGroup) inflater.inflate( R.layout.aviary_panel_stickers, parent, false );
	}

	// /////////////////////////
	// OnStatusChangeListener //
	// /////////////////////////
	@Override
	public void OnStatusChanged ( int oldStatus, int newStatus ) {
		mLogger.info( "OnStatusChange: " + oldStatus + " >> " + newStatus );

		switch( newStatus ) {
			case STATUS_PACKS:

				// deactivate listeners for the stickers list
				mListStickers.setOnItemClickListener( null );
				mListStickers.setOnItemLongClickListener( null );

				if( mViewFlipper.getDisplayedChild() != 1 ) {
					mViewFlipper.setDisplayedChild( 1 );
				}

				if( oldStatus == STATUS_NULL ) {
					// NULL
				} else if( oldStatus == STATUS_STICKERS ) {
					restoreToolbarTitle();

					if( getDragController() != null ) {
						getDragController().deactivate();
					}

					if( null != mAdapterStickers ) {
						mAdapterStickers.changeCursor( null );
					}
				}
				break;

			case STATUS_STICKERS:
				loadStickers();

				if( mViewFlipper.getDisplayedChild() != 2 ) {
					mViewFlipper.setDisplayedChild( 2 );
				}

				if( getDragController() != null ) {
					getDragController().activate();
				}
				break;

			default:
				mLogger.error( "unmanaged status change: " + oldStatus + " >> " + newStatus );
				break;
		}
	}

	@Override
	public void OnStatusUpdated ( int status ) {
	}

	// //////////////////////
	// OnItemClickListener //
	// //////////////////////

	@Override
	public void onItemClick ( it.sephiroth.android.library.widget.AdapterView<?> parent, View view, int position, long id ) {
		mLogger.info( "onItemClick: " + position );

		if( null != mOverlay ) {
			mOverlay.hide();
		}

		if( ! isActive() ) {
			return;
		}

		if( mStatus.getCurrentStatus() == STATUS_PACKS ) {

			ViewHolder holder = (ViewHolder) view.getTag();
			if( null != holder ) {

				// get more
				if( holder.type == StickerPacksAdapter.TYPE_LEFT_GETMORE || holder.type == StickerPacksAdapter.TYPE_RIGHT_GETMORE ) {

					HashMap<String, String> attrs = new HashMap<String, String>();
					attrs.put( "from", ToolLoaderFactory.Tools.STICKERS.name()
							.toLowerCase( Locale.US ) );

					if( holder.type == StickerPacksAdapter.TYPE_LEFT_GETMORE ) {
						attrs.put( "side", "left" );
					} else if( holder.type == StickerPacksAdapter.TYPE_RIGHT_GETMORE ) {
						attrs.put( "side", "right" );
					}
					getContext().getTracker()
							.tagEventAttributes( "shop_list: opened", attrs );

					IAPUpdater iapData = new IAPUpdater.Builder()
							.setPackType( PackType.STICKER )
							.setFeaturedPackId( -1 )
							.build();
					displayIAPDialog( iapData );

					// external
				} else if( holder.type == StickerPacksAdapter.TYPE_EXTERNAL ) {
					HashMap<String, String> attrs = new HashMap<String, String>();
					attrs.put( "pack", holder.identifier );
					attrs.put( "from", "featured" );
					getContext().getTracker()
							.tagEventAttributes( "shop_details: opened", attrs );

					IAPUpdater iapData = new IAPUpdater.Builder()
							.setPackType( PackType.STICKER )
							.setPackId( holder.id )
							.setFeaturedPackId( holder.id )
							.build();
					displayIAPDialog( iapData );

					if( position > 0 ) {

						if( mListPacks.getChildCount() > 0 ) {
							int left = view.getLeft();
							int right = view.getRight();
							int center = ( ( right - left ) / 2 + left );
							final int delta = mListPacks.getWidth() / 2 - center;

							mListPacks.postDelayed( new Runnable() {

								@Override
								public void run () {
									mListPacks.smoothScrollBy( - delta, 500 );
								}
							}, 300 );
						}
					}
				} else if( holder.type == StickerPacksAdapter.TYPE_DIVIDER ) {

					// click on a sticker pack icon
					onStickerPackSelected(holder.id, holder.identifier);

					if(holder.isNew) {
						holder.isNew = false;
						loadStickerPackIcon(holder, holder.id);
					}
				}
			}
		}
	}

	private void onStickerPackSelected(long packId, String packIdentifier) {
		mLogger.info("onStickerPackSelected: %d, %s", packId, packIdentifier);
		mNewPacks.remove(Long.valueOf(packId));
		removeIapDialog();
		mPackInfo = new StickerPackInfo(packId, packIdentifier);
		mStatus.setStatus(STATUS_STICKERS);
	}

	// ////////////////////////
	// Drag and Drop methods //
	// ////////////////////////

	/**
	 * Starts the drag and drop operation
	 *
	 * @param parent   - the parent list
	 * @param view     - the current view clicked
	 * @param position - the position in the list
	 * @param id       - the item id
	 * @return
	 */
	private boolean startDrag ( it.sephiroth.android.library.widget.AdapterView<?> parent, View view, int position, long id, boolean animate ) {

		mLogger.info( "startDrag" );

		if( android.os.Build.VERSION.SDK_INT < 9 ) {
			return false;
		}

		if( parent == null || view == null || parent.getAdapter() == null ) {
			return false;
		}

		if( mStatus.getCurrentStatus() != STATUS_STICKERS ) {
			return false;
		}

		if( null != view ) {
			View image = view.findViewById( R.id.image );
			if( null != image ) {

				if( null == parent.getAdapter() ) {
					return false;
				}
				StickersAdapter adapter = (StickersAdapter) parent.getAdapter();

				if( null == adapter ) {
					return false;
				}

				final String identifier = adapter.getItemIdentifier( position );
				final String contentPath = adapter.getContentPath();

				if( null == identifier || null == contentPath ) {
					return false;
				}

				getContext().getTracker()
						.tagEvent( "stickers: drag_began", "item", identifier );

				final String iconPath = contentPath + "/" + AviaryCds.getPackItemFilename( identifier, PackType.STICKER, Size.Small );

				Bitmap bitmap;
				try {
					bitmap = new StickerThumbnailCallable( iconPath, mStickerThumbSize ).call();
					int offsetx = Math.abs( image.getWidth() - bitmap.getWidth() ) / 2;
					int offsety = Math.abs( image.getHeight() - bitmap.getHeight() ) / 2;
					return getDragController().startDrag( image, bitmap, offsetx, offsety, StickersPanel.this, new StickerDragInfo( contentPath, identifier ),
					                                      DragControllerService.DRAG_ACTION_MOVE, animate );
				} catch( Exception e ) {
					e.printStackTrace();
				}
				return getDragController().startDrag( image, StickersPanel.this, new StickerDragInfo( contentPath, identifier ), DragControllerService.DRAG_ACTION_MOVE, animate );
			}
		}
		return false;
	}

	@Override
	public void setDragController ( DragController controller ) {
		//mDragControllerService = controller;
	}

	@Override
	public DragController getDragController () {
		return mDragControllerService.getInstance();
	}

	@Override
	public void onDropCompleted ( DropTarget arg0, boolean arg1 ) {}

	@Override
	public boolean onDragEnd () {
		return false;
	}

	@Override
	public void onDragStart ( DragSource arg0, Object arg1, int arg2 ) {
		mLogger.info( "onDragStart: %s - %s", arg0, arg1 );
	}

	@Override
	public boolean acceptDrop ( DragSource source, int x, int y, int xOffset, int yOffset, DragView dragView, Object dragInfo ) {
		return source == this;
	}

	@Override
	public void onDrop ( DragSource source, int x, int y, int xOffset, int yOffset, DragView dragView, Object dragInfo ) {

		mLogger.info( "onDrop. source=" + source + ", dragInfo=" + dragInfo );

		if( ! isActive() ) {
			return;
		}

		if( dragInfo != null && dragInfo instanceof StickerDragInfo ) {
			StickerDragInfo info = (StickerDragInfo) dragInfo;

			getContext().getTracker()
					.tagEvent( "stickers: drag_suceeded" );

			onApplyCurrent();

			float scaleFactor = dragView.getScaleFactor();

			float w = dragView.getWidth();
			float h = dragView.getHeight();

			int width = (int) ( w / scaleFactor );
			int height = (int) ( h / scaleFactor );

			int targetX = (int) ( x - xOffset );
			int targetY = (int) ( y - yOffset );

			RectF rect = new RectF( targetX, targetY, targetX + width, targetY + height );

			addSticker( info.contentPath, info.identifier, rect );
		}
	}

	// /////////////////////////
	// Stickers panel methods //
	// /////////////////////////

	/**
	 * Ask to leave without apply changes.
	 */
	void askToLeaveWithoutApply () {
		new AlertDialog.Builder( getContext().getBaseContext() ).setTitle( R.string.feather_attention )
				.setMessage( R.string.feather_tool_leave_question )
				.setPositiveButton( android.R.string.yes, new DialogInterface.OnClickListener() {

					@Override
					public void onClick ( DialogInterface dialog, int which ) {
						getContext().cancel();
					}
				} )
				.setNegativeButton( android.R.string.no, null )
				.show();
	}

	/**
	 * Initialize the preview bitmap and canvas.
	 */
	private void createAndConfigurePreview () {

		if( mPreview != null && ! mPreview.isRecycled() ) {
			mPreview.recycle();
			mPreview = null;
		}

		mPreview = BitmapUtils.copy( mBitmap, Bitmap.Config.ARGB_8888 );
		mCanvas = new Canvas( mPreview );
	}

	protected void updateInstalledPacks () {
		mLogger.info( "updateInstalledPacks" );

		// display the loader
		if( mViewFlipper.getDisplayedChild() != 0 ) {
			mViewFlipper.setDisplayedChild( 0 );
		}

		mAdapterPacks = createPacksAdapter( getContext().getBaseContext(), null );
		mListPacks.setAdapter( mAdapterPacks );
		Context context = getContext().getBaseContext();

		if( null == mCursorLoaderPacks ) {

			final String uri = String.format( Locale.US, "packTray/%d/%d/%d/%s", 3, 0, 0, AviaryCds.PACKTYPE_STICKER );

			Uri baseUri = PackageManagerUtils.getCDSProviderContentUri( context, uri );
			mCursorLoaderPacks = new CursorLoader( context, baseUri, null, null, null, null );
			mCursorLoaderPacks.registerListener( 1, this );

			mContentObserver = new ContentObserver( new Handler() ) {
				@Override
				public void onChange ( boolean selfChange ) {
					mLogger.info( "mContentObserver::onChange" );
					super.onChange( selfChange );

					if( isActive() && null != mCursorLoaderPacks && mCursorLoaderPacks.isStarted() ) {
						mCursorLoaderPacks.onContentChanged();
					}
				}
			};
			context.getContentResolver()
					.registerContentObserver( PackageManagerUtils.getCDSProviderContentUri( context, "packTray/" + AviaryCds.PACKTYPE_STICKER ), false, mContentObserver );
		}

		mCursorLoaderPacks.startLoading();
		mListPacks.setOnItemClickListener(this);
	}

	private StickerPacksAdapter createPacksAdapter ( Context context, Cursor cursor ) {
		return new StickerPacksAdapter( context, R.layout.aviary_sticker_item, R.layout.aviary_effect_item_external, R.layout.aviary_effect_item_more, cursor );
	}

	private final void displayIAPDialog ( IAPUpdater data ) {
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
			dialog.setOnCloseListener(new OnCloseListener() {
				@Override
				public void onClose() {
					removeIapDialog();
				}
			});
		}
		mIapDialog = dialog;
		setApplyEnabled(false);
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

	/**
	 * Loads the list of available stickers for the current selected pack
	 */
	protected void loadStickers () {
		mLogger.info("loadStickers");

		final Context context = getContext().getBaseContext();

		if( null == mPackInfo ) {
			return;
		}

		// retrieve the pack content path
		final String packContentPath = CdsUtils.getPackContentPath( context, mPackInfo.packId );

		// acquire the items cursor
		Cursor cursor = context.getContentResolver()
				.query( PackageManagerUtils.getCDSProviderContentUri( context, "pack/" + mPackInfo.packId + "/item/list" ),
				        new String[]{ PacksItemsColumns._ID + " as _id", PacksItemsColumns._ID, PacksItemsColumns.PACK_ID, PacksItemsColumns.IDENTIFIER,
						        PacksItemsColumns.DISPLAY_NAME }, null, null, null
				);

		if( null == mAdapterStickers ) {
			mAdapterStickers = new StickersAdapter( context, R.layout.aviary_sticker_item_single, cursor );
			( (StickersAdapter) mAdapterStickers ).setContentPath( packContentPath );
			mListStickers.setAdapter( mAdapterStickers );
		} else {
			( (StickersAdapter) mAdapterStickers ).setContentPath( packContentPath );
			mAdapterStickers.changeCursor( cursor );
		}

		if(ApiHelper.AT_LEAST_11) {
			mListStickers.setSelection(0);
		}

		mListStickers.setOnItemClickListener( new OnItemClickListener() {
			@Override
			public void onItemClick ( it.sephiroth.android.library.widget.AdapterView<?> parent, View view, int position, long id ) {
				mLogger.info( "onItemClick: " + position );
				StickersAdapter adapter = ( (StickersAdapter) parent.getAdapter() );

				final Cursor cursor = (Cursor) adapter.getItem( position );
				final String sticker = cursor.getString( cursor.getColumnIndex( PacksItemsColumns.IDENTIFIER ) );
				removeIapDialog();
				addSticker( adapter.getContentPath(), sticker, null );
			}
		} );

		mListStickers.setOnItemLongClickListener( new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick ( it.sephiroth.android.library.widget.AdapterView<?> parent, View view, int position, long id ) {
				return startDrag( parent, view, position, id, false );
			}
		} );


		// auto-select a stickers from options bundle
		if (hasOption(Constants.QuickLaunch.CONTENT_ITEM_ID)) {
			mLogger.info("hasOptions: Constants.QuickLaunch.CONTENT_VALUE");
			Bundle options = getOptions();
			long item_id = options.getLong(Constants.QuickLaunch.CONTENT_ITEM_ID, - 1);
			for (int i = 0; i < mAdapterStickers.getCount(); i++) {
				if (item_id == mAdapterStickers.getItemId(i)) {
					mLogger.log("performClick on : %d", i);
					mListStickers.performItemClick(null, i, item_id);
					break;
				}
			}
			options.remove(Constants.QuickLaunch.CONTENT_ITEM_ID);
		}

	}

	private void addSticker ( String contentPath, String identifier, RectF position ) {
		mLogger.info("addSticker: %s - %s", contentPath, identifier);

		onApplyCurrent();

		Assert.assertNotNull( mPackInfo );
		Assert.assertNotNull( contentPath );

		File file = new File( contentPath, AviaryCds.getPackItemFilename( identifier, PackType.STICKER, Size.Medium ) );
		mLogger.log( "file: " + file.getAbsolutePath() );

		if( file.exists() ) {
			StickerDrawable drawable = new StickerDrawable( getContext().getBaseContext()
					                                                .getResources(), file.getAbsolutePath(), identifier, mPackInfo.packIdentifier );
			drawable.setAntiAlias( true );

			mCurrentFilter = new StickerFilter( contentPath, identifier );
			mCurrentFilter.setSize( drawable.getBitmapWidth(), drawable.getBitmapHeight() );

			HashMap<String, String> attrs = new HashMap<String, String>();
			attrs.put( "item", identifier );
			attrs.put( "pack", mPackInfo.packIdentifier );

			getContext().getTracker().tagEventAttributes( "stickers: item_added", attrs );

			addSticker( drawable, position );
		} else {
			mLogger.warn("file does not exists");
			Toast.makeText(getContext().getBaseContext(), "Error loading the selected sticker", Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	public boolean getIsChanged() {
		mLogger.info("getIsChanged: " + (mStickersOnScreen > 0 || stickersOnScreen()));
		return mStickersOnScreen > 0 || stickersOnScreen();
	}

	private void addSticker ( FeatherDrawable drawable, RectF positionRect ) {
		mLogger.info("addSticker: " + drawable + ", position: " + positionRect);

		DrawableHighlightView hv = new DrawableHighlightView( mImageView, ( (ImageViewDrawableOverlay) mImageView ).getOverlayStyleId(), drawable );
		hv.setOnDeleteClickListener( new OnDeleteClickListener() {

			@Override
			public void onDeleteClick () {
				onClearCurrent( true );
			}
		} );

		Matrix mImageMatrix = mImageView.getImageViewMatrix();

		int cropWidth, cropHeight;
		int x, y;

		final int width = mImageView.getWidth();
		final int height = mImageView.getHeight();

		// width/height of the sticker
		if( positionRect != null ) {
			cropWidth = (int) positionRect.width();
			cropHeight = (int) positionRect.height();
		} else {
			cropWidth = (int) drawable.getCurrentWidth();
			cropHeight = (int) drawable.getCurrentHeight();
		}

		final int cropSize = Math.max( cropWidth, cropHeight );
		final int screenSize = Math.min( mImageView.getWidth(), mImageView.getHeight() );

		if( cropSize > screenSize ) {
			float ratio;
			float widthRatio = (float) mImageView.getWidth() / cropWidth;
			float heightRatio = (float) mImageView.getHeight() / cropHeight;

			if( widthRatio < heightRatio ) {
				ratio = widthRatio;
			} else {
				ratio = heightRatio;
			}

			cropWidth = (int) ( (float) cropWidth * ( ratio / 2 ) );
			cropHeight = (int) ( (float) cropHeight * ( ratio / 2 ) );

			if( positionRect == null ) {
				int w = mImageView.getWidth();
				int h = mImageView.getHeight();
				positionRect = new RectF( w / 2 - cropWidth / 2, h / 2 - cropHeight / 2, w / 2 + cropWidth / 2, h / 2 + cropHeight / 2 );
			}

			positionRect.inset( ( positionRect.width() - cropWidth ) / 2, ( positionRect.height() - cropHeight ) / 2 );
		}

		if( positionRect != null ) {
			x = (int) positionRect.left;
			y = (int) positionRect.top;
		} else {
			x = ( width - cropWidth ) / 2;
			y = ( height - cropHeight ) / 2;
		}

		Matrix matrix = new Matrix( mImageMatrix );
		matrix.invert( matrix );

		float[] pts = new float[]{ x, y, x + cropWidth, y + cropHeight };
		MatrixUtils.mapPoints( matrix, pts );

		RectF cropRect = new RectF( pts[0], pts[1], pts[2], pts[3] );
		Rect imageRect = new Rect( 0, 0, width, height );

		// hv.setRotateAndScale( rotateAndResize );
		hv.setup( getContext().getBaseContext(), mImageMatrix, imageRect, cropRect, false );

		( (ImageViewDrawableOverlay) mImageView ).addHighlightView( hv );
		( (ImageViewDrawableOverlay) mImageView ).setSelectedHighlightView( hv );

		mStickersOnScreen++;
	}

	private void onApplyCurrent () {
		mLogger.info( "onApplyCurrent" );

		if( ! stickersOnScreen() ) {
			return;
		}

		final DrawableHighlightView hv = ( (ImageViewDrawableOverlay) mImageView ).getHighlightViewAt( 0 );

		if( hv != null ) {

			final StickerDrawable stickerDrawable = ( (StickerDrawable) hv.getContent() );

			RectF cropRect = hv.getCropRectF();
			Rect rect = new Rect( (int) cropRect.left, (int) cropRect.top, (int) cropRect.right, (int) cropRect.bottom );

			Matrix rotateMatrix = hv.getCropRotationMatrix();
			Matrix matrix = new Matrix( mImageView.getImageMatrix() );
			if( ! matrix.invert( matrix ) ) {
			}

			int saveCount = mCanvas.save( Canvas.MATRIX_SAVE_FLAG );
			mCanvas.concat( rotateMatrix );

			stickerDrawable.setDropShadow( false );
			hv.getContent().setBounds(rect);
			hv.getContent().draw(mCanvas);
			mCanvas.restoreToCount( saveCount );
			mImageView.invalidate();

			if( mCurrentFilter != null ) {
				final int w = mBitmap.getWidth();
				final int h = mBitmap.getHeight();

				mCurrentFilter.setTopLeft( cropRect.left / w, cropRect.top / h );
				mCurrentFilter.setBottomRight( cropRect.right / w, cropRect.bottom / h );
				mCurrentFilter.setRotation( Math.toRadians( hv.getRotation() ) );

				int dw = stickerDrawable.getBitmapWidth();
				int dh = stickerDrawable.getBitmapHeight();
				float scalew = cropRect.width() / dw;
				float scaleh = cropRect.height() / dh;

				mCurrentFilter.setCenter( cropRect.centerX() / w, cropRect.centerY() / h );
				mCurrentFilter.setScale( scalew, scaleh );


				ToolActionVO<String> action = new ToolActionVO<String>();
				action.setPackIdentifier(stickerDrawable.getPackIdentifier());
				action.setContentIdentifier(stickerDrawable.getIdentifier());
				mEditResult.addToolAction(action);
				mEditResult.getActionList().add(mCurrentFilter.getActions().get(0));

				// tracking
				mItemCount++;
				mItemApplied.add( Pair.create( stickerDrawable.getPackIdentifier(), stickerDrawable.getIdentifier() ) );

				mCurrentFilter = null;
			}
		}

		onClearCurrent( false );
		onPreviewChanged( mPreview, false, false );
	}

	/**
	 * Remove the current sticker.
	 *
	 * @param removed - true if the current sticker is being removed, otherwise it was
	 *                flattened
	 */
	private void onClearCurrent ( boolean removed ) {
		mLogger.info( "onClearCurrent. removed=" + removed );

		if( stickersOnScreen() ) {
			final ImageViewDrawableOverlay image = (ImageViewDrawableOverlay) mImageView;
			final DrawableHighlightView hv = image.getHighlightViewAt( 0 );
			onClearCurrent( hv, removed );
		}
	}

	/**
	 * Removes the current active sticker.
	 *
	 * @param hv      - the {@link DrawableHighlightView} of the active sticker
	 * @param removed - current sticker is removed
	 */
	private void onClearCurrent ( DrawableHighlightView hv, boolean removed ) {
		mLogger.info( "onClearCurrent. hv=" + hv + ", removed=" + removed );

		if( mCurrentFilter != null ) {
			mCurrentFilter = null;
		}

		if( null != hv ) {
			FeatherDrawable content = hv.getContent();

			if( removed ) {
				if( content instanceof StickerDrawable ) {
					String name = ( (StickerDrawable) content ).getIdentifier();
					String packname = ( (StickerDrawable) content ).getPackIdentifier();

					HashMap<String, String> attrs = new HashMap<String, String>();
					attrs.put( "item", name );
					attrs.put( "pack", packname );
					getContext().getTracker().tagEventAttributes( "stickers: item_deleted", attrs );

					int removePosition = mItemApplied.lastIndexOf( Pair.create( packname, name ) );
					if( removePosition > -1 ) {
						mItemApplied.remove( removePosition );
					}

				}
			}
		}

		hv.setOnDeleteClickListener( null );
		( (ImageViewDrawableOverlay) mImageView ).removeHightlightView(hv);
		( (ImageViewDrawableOverlay) mImageView ).invalidate();

		if(removed){
			mStickersOnScreen--;
		}
	}

	/**
	 * Return true if there's at least one active sticker on screen.
	 *
	 * @return true, if successful
	 */
	private boolean stickersOnScreen () {
		final ImageViewDrawableOverlay image = (ImageViewDrawableOverlay) mImageView;
		return image.getHighlightCount() > 0;
	}

	private void onStickersPackListUpdated(Cursor cursor, int firstIndex, boolean forceSelection, final boolean smooth_selection) {
		mLogger.info( "onStickersPackListUpdated. firstIndex: %d, force: %b, smooth: %b", firstIndex, forceSelection, smooth_selection );

		int mListFirstValidPosition = firstIndex > 0 ? firstIndex : 0;

		if( mFirstTime || forceSelection ) {
			if( mListFirstValidPosition > 0 ) {
				if( smooth_selection ) {
					mListPacks.smoothScrollToPositionFromLeft( mListFirstValidPosition - 1, mPackCellWidth / 2, 500 );
				}
				mListPacks.setSelectionFromLeft( mListFirstValidPosition - 1, mPackCellWidth / 2 );
			}
		}
		mFirstTime = false;
	}

	/**
	 * Sticker pack listview adapter class
	 *
	 * @author alessandro
	 */
	class StickerPacksAdapter extends CursorAdapter {

		static final int TYPE_INVALID = - 1;
		static final int TYPE_LEFT_GETMORE = 5;
		static final int TYPE_RIGHT_GETMORE = 6;
		static final int TYPE_NORMAL = TrayColumns.TYPE_CONTENT;
		static final int TYPE_EXTERNAL = TrayColumns.TYPE_PACK_EXTERNAL;
		static final int TYPE_DIVIDER = TrayColumns.TYPE_PACK_INTERNAL;
		static final int TYPE_LEFT_DIVIDER = TrayColumns.TYPE_LEFT_DIVIDER;
		static final int TYPE_RIGHT_DIVIDER = TrayColumns.TYPE_RIGHT_DIVIDER;

		private int mLayoutResId;
		private int mExternalLayoutResId;
		private int mMoreResId;
		private LayoutInflater mInflater;

		int mPackageNameColumnIndex;
		int mDisplayNameColumnIndex;
		int mPathColumnIndex;
		int mIsFreeColumnIndex;

		public StickerPacksAdapter ( Context context, int mainResId, int externalResId, int moreResId, Cursor cursor ) {
			super( context, cursor, 0 );
			initColumns( cursor );
			mLayoutResId = mainResId;
			mExternalLayoutResId = externalResId;
			mMoreResId = moreResId;

			mInflater = LayoutInflater.from( context );
		}

		@Override
		public Cursor swapCursor ( Cursor newCursor ) {
			mLogger.info( "swapCursor" );
			initColumns( newCursor );
			return super.swapCursor( newCursor );
		}

		private void initColumns ( Cursor cursor ) {
			if( null != cursor ) {
				mPackageNameColumnIndex = cursor.getColumnIndex( TrayColumns.PACKAGE_NAME );
				mDisplayNameColumnIndex = cursor.getColumnIndex( TrayColumns.DISPLAY_NAME );
				mPathColumnIndex = cursor.getColumnIndex( TrayColumns.PATH );
				mIsFreeColumnIndex = cursor.getColumnIndex( TrayColumns.IS_FREE );
			}
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
				return cursor.getInt( TrayColumns.TYPE_COLUMN_INDEX );
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
			View view;
			ViewHolder holder;

			int layoutWidth = mPackCellWidth;
			final int type = getItemViewType( position );

			switch( type ) {
				case TYPE_LEFT_GETMORE:
					view = mInflater.inflate( mMoreResId, parent, false );
					( (ImageView) view.findViewById( R.id.aviary_image ) ).setImageResource( R.drawable.aviary_sticker_item_getmore );
					layoutWidth = mPackCellWidth;
					break;

				case TYPE_RIGHT_GETMORE:
					view = mInflater.inflate( mMoreResId, parent, false );
					( (ImageView) view.findViewById( R.id.aviary_image ) ).setImageResource( R.drawable.aviary_sticker_item_getmore );
					layoutWidth = mPackCellWidth;

					if( mPackCellWidth * cursor.getCount() < parent.getWidth() * 2 ) {
						view.setVisibility( View.INVISIBLE );
						layoutWidth = 1;
					} else {
						if( parent.getChildCount() > 0 && mListPacks.getFirstVisiblePosition() == 0 ) {
							View lastView = parent.getChildAt( parent.getChildCount() - 1 );

							if( lastView.getRight() < parent.getWidth() ) {
								view.setVisibility( View.INVISIBLE );
								layoutWidth = 1;
							}
						}
					}

					break;

				case TYPE_DIVIDER:
					view = mInflater.inflate( mLayoutResId, parent, false );
					layoutWidth = mPackCellWidth;
					break;

				case TYPE_EXTERNAL:
					view = mInflater.inflate( mExternalLayoutResId, parent, false );
					layoutWidth = mPackCellWidth;
					break;

				case TYPE_LEFT_DIVIDER:
					view = mInflater.inflate( R.layout.aviary_thumb_divider_right, parent, false );
					layoutWidth = LayoutParams.WRAP_CONTENT;
					break;

				case TYPE_RIGHT_DIVIDER:
					view = mInflater.inflate( R.layout.aviary_thumb_divider_left, parent, false );
					layoutWidth = LayoutParams.WRAP_CONTENT;

					if( mPackCellWidth * cursor.getCount() < parent.getWidth() * 2 ) {
						view.setVisibility( View.INVISIBLE );
						layoutWidth = 1;
					} else {

						if( parent.getChildCount() > 0 && mListPacks.getFirstVisiblePosition() == 0 ) {
							View lastView = parent.getChildAt( parent.getChildCount() - 1 );

							if( lastView.getRight() < parent.getWidth() ) {
								view.setVisibility( View.INVISIBLE );
								layoutWidth = 1;
							}
						}
					}
					break;

				case TYPE_NORMAL:
				default:
					mLogger.error( "TYPE_NORMAL" );
					view = null;
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

			if( holder.image != null ) {
				LayoutParams params = holder.image.getLayoutParams();
				params.height = mPackThumbSize;
				params.width = mPackThumbSize;
				holder.image.setLayoutParams( params );
			}

			view.setTag( holder );
			return view;
		}

		void bindView ( View view, Context context, Cursor cursor, int position ) {
			final ViewHolder holder = (ViewHolder) view.getTag();
			String displayName;
			String identifier;
			long id = - 1;

			if( ! cursor.isAfterLast() && ! cursor.isBeforeFirst() ) {
				id = cursor.getLong( TrayColumns.ID_COLUMN_INDEX );
			}

			if( holder.type == TYPE_NORMAL ) {

			} else if( holder.type == TYPE_EXTERNAL ) {
				ViewHolderExternal holderExternal = (ViewHolderExternal) holder;

				identifier = cursor.getString( TrayColumns.IDENTIFIER_COLUMN_INDEX );
				displayName = cursor.getString( mDisplayNameColumnIndex );
				String icon = cursor.getString( mPathColumnIndex );
				int free = cursor.getInt( mIsFreeColumnIndex );

				holder.text.setText( displayName );
				holder.identifier = identifier;
				holderExternal.free = free;

				if( holder.id != id ) {
					mPicassoLib.load( icon )
							.resize( mPackThumbSize, mPackThumbSize, true )
							.transform( new PackIconCallable.Builder()
									            .withResources( getContext().getBaseContext().getResources() )
									            .withPackType( PackType.STICKER )
												/*.withAlpha( 153 )*/
									            .withPath( icon )
									            .build()
							)
							.noFade()
							.error( R.drawable.aviary_ic_na )
							.into( holder.image );
				}

			} else if( holder.type == TYPE_DIVIDER ) {
				displayName = cursor.getString( mDisplayNameColumnIndex );
				identifier = cursor.getString( TrayColumns.IDENTIFIER_COLUMN_INDEX );
				final String icon = cursor.getString( mPathColumnIndex );

				holder.text.setText( displayName );
				holder.identifier = identifier;
				holder.obj = icon;
				holder.isNew = mNewPacks.contains(id);

				if( holder.id != id ) {
					loadStickerPackIcon( holder, id );
				} else {
					mLogger.warn( "icon already loaded..." );
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

	}

	private void loadStickerPackIcon( final ViewHolder holder, final long id ) {

		mLogger.info( "loadStickerPackIcon, %d (%s) = is new: %b", id, holder.identifier, mNewPacks.contains( id ) );

		if( null != holder && null != holder.obj ) {

			final boolean shouldShake = mLastInstalledPack == id;
			mLogger.log("shouldShake: %b, lastInstalledPack: %d, id: %d", shouldShake, mLastInstalledPack, id);

			if( shouldShake ) {
				ViewHelper.setAlpha( holder.image, 0 );
				ViewHelper.setScaleX( holder.image, 0 );
				ViewHelper.setScaleY( holder.image, 0 );
			}

			mPicassoLib.load(new File((String) holder.obj))
					.fit()
					.transform(
						new PackIconCallable.Builder().withResources(getContext().getBaseContext().getResources())
						                              .withPackType(PackType.STICKER)
						                              .withPath((String) holder.obj)
						                              .isNew(mNewPacks.contains(id))
						                              .build()
					)
					.noFade()
					.error(R.drawable.aviary_ic_na)
					.into(
						holder.image, new Callback() {
							@Override
							public void onSuccess() {

								mLogger.log("mLastInstalledPack: %d, holder.id: %d", mLastInstalledPack, holder.id);

								if (shouldShake) {
									shakePack(holder);
									mLastInstalledPack = - 1;
								}
							}

							@Override
							public void onError() {

							}
						}
					);

			if (shouldShake) {
				mLastInstalledPack = - 1;
			}
		}
	}

	private void shakePack( ViewHolder holder ) {
		if( null == holder || null == holder.image ) return;

		ViewHelper.setPivotX( holder.image, ( holder.image.getWidth() / 2 ) );
		ViewHelper.setPivotY( holder.image, ( holder.image.getHeight() / 2 ) );

		AnimatorSet scaleAnimation = new AnimatorSet();
		scaleAnimation.setDuration( 100 );
		scaleAnimation.setInterpolator( new DecelerateInterpolator( 1f ) );
		scaleAnimation.playTogether(
				ObjectAnimator.ofFloat( holder.image, "alpha", 0, 1 ),
				ObjectAnimator.ofFloat( holder.image, "scaleX", 0.3f, 1 ),
				ObjectAnimator.ofFloat( holder.image, "scaleY", 0.3f, 1 )
		);

		Animator shakeAnimation = ObjectAnimator.ofFloat( holder.image, "rotation", 0f, 3f );
		shakeAnimation.setDuration( 400 );
		shakeAnimation.setInterpolator( new android.view.animation.CycleInterpolator( 3 ) );

		AnimatorSet set = new AnimatorSet();
		set.playSequentially( scaleAnimation, shakeAnimation );
		set.setStartDelay( 100 );
		set.start();
	}

	//
	// Stickers list adapter
	//

	class StickersAdapter extends CursorAdapter {

		LayoutInflater mLayoutInflater;
		int mStickerResourceId;
		String mContentPath;
		int idColumnIndex, identifierColumnIndex, packIdColumnIndex;

		public StickersAdapter ( Context context, int resId, Cursor cursor ) {
			super( context, cursor, 0 );
			mStickerResourceId = resId;
			mLayoutInflater = LayoutInflater.from( context );
			initCursor( cursor );
		}

		public void setContentPath ( String path ) {
			mContentPath = path;
		}

		public String getContentPath () {
			return mContentPath;
		}

		@Override
		public boolean hasStableIds () {
			return true;
		}

		@Override
		public Cursor swapCursor ( Cursor newCursor ) {
			initCursor( newCursor );
			return super.swapCursor( newCursor );
		}

		private void initCursor ( Cursor cursor ) {
			if( null != cursor ) {
				idColumnIndex = cursor.getColumnIndex( PacksItemsColumns._ID );
				identifierColumnIndex = cursor.getColumnIndex( PacksItemsColumns.IDENTIFIER );
				packIdColumnIndex = cursor.getColumnIndex( PacksItemsColumns.PACK_ID );
			}
		}

		@Override
		public View newView ( Context context, Cursor cursor, ViewGroup parent ) {
			View view = mLayoutInflater.inflate( mStickerResourceId, null );
			LayoutParams params = new LayoutParams( mStickerCellWidth, LayoutParams.MATCH_PARENT );
			view.setLayoutParams( params );
			return view;
		}

		@Override
		public void bindView ( View view, Context context, Cursor cursor ) {
			ImageView image = (ImageView) view.findViewById( R.id.image );

			String identifier = cursor.getString( identifierColumnIndex );

			final String iconPath = mContentPath + "/" + AviaryCds.getPackItemFilename( identifier, PackType.STICKER, Size.Small );

			mPicassoLib.load( iconPath )
					.skipMemoryCache()
					.resize( mStickerThumbSize, mStickerThumbSize, true )
					.noFade()
					.into( image );

		}

		public String getItemIdentifier ( int position ) {
			Cursor cursor = (Cursor) getItem( position );
			return cursor.getString( identifierColumnIndex );
		}
	}

	/**
	 * Downloads and renders the sticker thumbnail
	 *
	 * @author alessandro
	 */
	static class StickerThumbnailCallable implements Callable<Bitmap> {

		int mFinalSize;
		String mUrl;

		public StickerThumbnailCallable ( String path, int maxSize ) {
			mUrl = path;
			mFinalSize = maxSize;
		}

		@Override
		public Bitmap call () throws Exception {
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inPreferredConfig = Config.ARGB_8888;
			Bitmap bitmap = BitmapFactory.decodeFile( mUrl, options );

			if( mFinalSize > 0 && null != bitmap ) {
				Bitmap result = BitmapUtils.resizeBitmap( bitmap, mFinalSize, mFinalSize );
				if( result != bitmap ) {
					bitmap.recycle();
					bitmap = result;
				}
			}
			return bitmap;
		}
	}

	static class StickerPackInfo {

		long packId;
		String packIdentifier;

		StickerPackInfo ( long packId, String packIdentifier ) {
			this.packId = packId;
			this.packIdentifier = packIdentifier;
		}
	}

	static class StickerDragInfo {

		String contentPath;
		String identifier;

		StickerDragInfo ( String contentPath, String identifier ) {
			this.contentPath = contentPath;
			this.identifier = identifier;
		}
	}

}
