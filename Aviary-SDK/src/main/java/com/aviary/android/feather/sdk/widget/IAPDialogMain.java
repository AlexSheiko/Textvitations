package com.aviary.android.feather.sdk.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.ViewAnimator;

import com.aviary.android.feather.cds.AviaryCds.PackType;
import com.aviary.android.feather.cds.CdsUtils;
import com.aviary.android.feather.cds.billing.util.Inventory;
import com.aviary.android.feather.cds.billing.util.Purchase;
import com.aviary.android.feather.common.AviaryIntent;
import com.aviary.android.feather.common.log.LoggerFactory;
import com.aviary.android.feather.common.log.LoggerFactory.Logger;
import com.aviary.android.feather.common.log.LoggerFactory.LoggerType;
import com.aviary.android.feather.library.services.LocalDataService;
import com.aviary.android.feather.sdk.AviaryMainController;
import com.aviary.android.feather.sdk.AviaryMainController.FeatherContext;
import com.aviary.android.feather.sdk.FeatherActivity;
import com.aviary.android.feather.sdk.R;
import com.aviary.android.feather.sdk.widget.IAPDialogList.onPackSelectedListener;

import java.util.HashMap;

@SuppressLint("MissingSuperCall")
public class IAPDialogMain implements onPackSelectedListener, AviaryStoreWrapper.Callback {

	private LocalDataService mDataService;
	HashMap<Long, CdsUtils.PackOption> mDownloadMap = new HashMap<Long, CdsUtils.PackOption>();
	HashMap<Long, CdsUtils.PackOption> mCacheMap = new HashMap<Long, CdsUtils.PackOption>();
	Inventory mInventory;

	@Override
	public void onDownloadStatusChanged ( final long packid, final String packType, final int status ) {
		logger.info( "onDownloadStatusChanged: %d - %s - %d", packid, packType, status );

		if( !isValid() ) return;

		mDownloadMap.put( packid, CdsUtils.PackOption.fromDownloadManagerStatus( status ) );

		IAPDialogList listView = getListView();
		IAPDialogDetail detailView = getDetailView();

		if( null != listView && null != listView.getData() ) {
			listView.onDownloadStatusChanged( packid, packType, status );
		}

		if( null != detailView && null != detailView.getData() && mViewAnimator.getDisplayedChild() == 1 ) {
			detailView.onDownloadStatusChanged( packid, packType, status );
		}

	}

	@Override
	public void onPackInstalled ( final long packId, final String packType, final int purchased ) {
		logger.info( "onPackInstalled: %d - %s - %d", packId, packType, purchased );

		if( !isValid() ) return;

		mDownloadMap.remove( packId );

		if( purchased == 1 ) {
			mCacheMap.put( packId, CdsUtils.PackOption.OWNED );
		} else {
			mCacheMap.remove( packId );

			CdsUtils.PackOption option = CdsUtils.getPackOption( getContext(), CdsUtils.getPackFullInfoById( getContext(), packId ) );
			if( option != CdsUtils.PackOption.PACK_OPTION_BEING_DETERMINED ) {
				mCacheMap.put( packId, option );
			}
		}

		IAPDialogList listView = getListView();
		IAPDialogDetail detailView = getDetailView();

		if( null != listView && null != listView.getData() ) {
			listView.onPackInstalled( packId, packType, purchased );
		}

		if( null != detailView && null != detailView.getData() && mViewAnimator.getDisplayedChild() == 1 ) {
			detailView.onPackInstalled( packId, packType, purchased );
		}
	}

	@Override
	public void onPurchaseSuccess ( final long packId, final String packType, Purchase purchase ) {
		if( !isValid() ) return;

		logger.info( "onPurchaseSuccess(%d, %s)", packId, packType );

		if( null != mInventory ) {
			mInventory.addPurchase( purchase );
		}

		IAPDialogList listView = getListView();
		IAPDialogDetail detailView = getDetailView();

		if( null != listView && null != listView.getData() ) {
			listView.onPurchaseSuccess( packId, packType, purchase );
		}

		if( null != detailView && null != detailView.getData() && mViewAnimator.getDisplayedChild() == 1 ) {
			detailView.onPurchaseSuccess( packId, packType, purchase );
		}
	}

	@Override
	public void onSubscriptionPurchased ( final String identifier, final int purchased ) {
		logger.info("onSubscriptionPurchased: %s - %d", identifier, purchased);

		if( !isValid() ) return;

		IAPDialogList listView = getListView();
		IAPDialogDetail detailView = getDetailView();

		if( null != listView && null != listView.getData() ) {
			listView.onSubscriptionPurchased( identifier, purchased );
		}

		if( null != detailView && null != detailView.getData() && mViewAnimator.getDisplayedChild() == 1 ) {
			detailView.onSubscriptionPurchased( identifier, purchased );
		}
	}

	@Override
	public void onServiceFinished () {
		logger.info( "onServiceFinished" );

		if( !isValid() ) return;

		IAPDialogList listView = getListView();
		IAPDialogDetail detailView = getDetailView();

		if( null != listView && null != listView.getData() ) {
			listView.onServiceFinished();
		}

		if( null != detailView && null != detailView.getData() && mViewAnimator.getDisplayedChild() == 1 ) {
			detailView.onServiceFinished();
		}
	}

	public interface OnCloseListener {

		void onClose ();
	}

	public static class IAPUpdater implements Cloneable {

		private long packId = - 1;
		private PackType packType = null;
		private String eventName = null;
		private long featuredPackId = -1;
		private HashMap<String, String> eventAttributes = new HashMap<String, String>();

		public long getPackId () {
			return packId;
		}

		public long getFeaturedPackId() {
			return featuredPackId;
		}

		public PackType getPackType () {
			return packType;
		}

		@Override
		public Object clone () {
			IAPUpdater cloned = new IAPUpdater();
			cloned.packId = packId;
			cloned.packType = packType;
			cloned.featuredPackId = featuredPackId;
			return cloned;
		}

		@Override
		public boolean equals ( Object o ) {
			if( o instanceof IAPUpdater ) {
				IAPUpdater other = (IAPUpdater) o;
				return other.packId == packId && other.packType == packType;
			}
			return super.equals( o );
		}

		@Override
		public String toString () {
			return "IAPUpdater{packType: " + packType + ", packId: " + packId + ", featuredPackId: " + featuredPackId + "}";
		}

		public static class Builder {

			IAPUpdater result;

			public Builder () {
				result = new IAPUpdater();
			}

			public Builder setPackId ( long id ) {
				result.packId = id;
				return this;
			}

			public Builder setPackType ( PackType type ) {
				result.packType = type;
				return this;
			}

			public Builder setEvent ( String name ) {
				result.eventName = name;
				return this;
			}

			public Builder setFeaturedPackId( long id ) {
				result.featuredPackId = id;
				return this;
			}

			public Builder addEventAttributes ( String key, String value ) {
				result.eventAttributes.put( key, value );
				return this;
			}

			public IAPUpdater build () {
				return result;
			}
		}
	}

	static Logger logger = LoggerFactory.getLogger( "IAPDialogMain", LoggerType.ConsoleLoggerType );

	OnCloseListener mCloseListener;
	ViewGroup mView;
	IAPUpdater mData;
	ViewAnimator mViewAnimator;
	AviaryMainController mController;
	AviaryStoreWrapper mStoreWrapper;

	public static IAPDialogMain create ( FeatherContext context, IAPUpdater data ) {
		logger.info( "create" );

		ViewGroup container = context.activatePopupContainer();
		ViewGroup dialog = (ViewGroup) container.findViewById( R.id.aviary_main_iap_dialog_container );
		IAPDialogMain instance = null;

		if( dialog == null ) {
			dialog = addToParent( container, - 1 );
			instance = new IAPDialogMain( dialog );
			instance.update( data, true );
		} else {
			instance = (IAPDialogMain) dialog.getTag();
			instance.update( data, false );
		}
		return instance;
	}

	public IAPDialogMain ( ViewGroup view ) {
		mView = view;
		mView.setTag( this );

		mStoreWrapper = new AviaryStoreWrapper( this );

		onAttachedToWindow();
	}

	private void initController () {
		if( null == mController ) {
			if( mView.getContext() instanceof FeatherActivity ) {
				mController = ( (FeatherActivity) mView.getContext() ).getMainController();
			}
		} else {
			logger.log( "controller: " + mController );
		}
	}

	public AviaryMainController getController () {
		return mController;
	}

	public boolean onBackPressed () {
		if( mViewAnimator.getDisplayedChild() == 0 ) {
			return false;
		} else {
			IAPDialogList view = (IAPDialogList) mViewAnimator.getChildAt( 0 );
			if( null != view && view.getData() != null ) {
				displayChild( 0, false );
				return true;
			}
		}
		return false;
	}

	public void update ( IAPUpdater updater ) {
		update( updater, false );
	}

	public void update ( IAPUpdater updater, boolean firstTime ) {
		logger.info( "update" );

		if( null == updater || ! isValid() ) {
			return;
		}
		mData = updater;

		initController();

		if( updater.packId < 0 && updater.packType == null ) {
			logger.error( "invalid updater instance" );
			return;
		}

		int currentChild = mViewAnimator.getDisplayedChild();
		int targetChild = updater.getPackId() < 0 ? 0 : 1;

		if( mData.eventName != null ) {
			if( null != mData.eventAttributes ) {
				getController().getTracker()
						.tagEventAttributes( mData.eventName, mData.eventAttributes );
			} else {
				getController().getTracker()
						.tagEvent( mData.eventName );
			}
		}

		displayChild( targetChild, firstTime );

		if( targetChild == 0 ) {
			IAPDialogList view = (IAPDialogList) mViewAnimator.getChildAt( targetChild );

			IAPUpdater viewData = view.getData();
			if( null != updater && ! updater.equals( viewData ) ) {
				view.update( updater, this );
			}
			view.setOnPackSelectedListener( this );
		} else {
			IAPDialogDetail view = (IAPDialogDetail) mViewAnimator.getChildAt( targetChild );
			IAPUpdater viewData = view.getData();
			if( null != updater && ! updater.equals( viewData ) ) {
				view.update( updater, this );
			}
		}
	}

	void displayChild ( int targetChild, boolean firstTime ) {
		int currentChild = mViewAnimator.getDisplayedChild();

		if( firstTime ) {
			if( targetChild == 0 ) {
				mViewAnimator.setAnimateFirstView( true );
			}
		}

		if( targetChild == 0 ) {
			mViewAnimator.setInAnimation( getContext(), R.anim.aviary_slide_in_left );
			mViewAnimator.setOutAnimation( getContext(), R.anim.aviary_slide_out_right );
		} else {
			mViewAnimator.setInAnimation( getContext(), R.anim.aviary_slide_in_right );
			mViewAnimator.setOutAnimation( getContext(), R.anim.aviary_slide_out_left );
		}

		if( currentChild != targetChild ) {
			mViewAnimator.setDisplayedChild( targetChild );
		}

		mViewAnimator.getInAnimation()
				.setAnimationListener( new AnimationListener() {

					@Override
					public void onAnimationStart ( Animation animation ) {
						logger.info( "onAnimationStart" );
					}

					@Override
					public void onAnimationRepeat ( Animation animation ) {
					}

					@Override
					public void onAnimationEnd ( Animation animation ) {
						logger.info( "onAnimationEnd" );
					}
				} );

	}

	IAPDialogList getListView () {
		return (IAPDialogList) mViewAnimator.getChildAt( 0 );
	}

	IAPDialogDetail getDetailView () {
		if( mViewAnimator.getChildCount() > 0 ) {
			return (IAPDialogDetail) mViewAnimator.getChildAt( 1 );
		}
		return null;
	}

	public IAPUpdater getData () {
		return mData;
	}

	private static ViewGroup addToParent ( ViewGroup parent, int index ) {
		ViewGroup view = (ViewGroup) LayoutInflater.from( parent.getContext() )
				.inflate( R.layout.aviary_iap_dialog_container, parent, false );
		view.setFocusable( true );
		if( index > - 1 ) {
			parent.addView( view, index );
		} else {
			parent.addView( view );
		}
		return view;
	}

	private Runnable mHideRunnable = new Runnable() {
		@Override
		public void run () {
			handleHide();
		}
	};

	public void onConfigurationChanged ( Configuration newConfig ) {
		logger.info( "onConfigurationChanged" );

		if( ! isValid() ) {
			return;
		}

		ViewGroup parent = (ViewGroup) mView.getParent();
		if( null != parent ) {
			int index = parent.indexOfChild( mView );
			removeFromParent();

			mView = addToParent( parent, index );

			ViewGroup animator = (ViewGroup) mView.findViewById( R.id.aviary_main_iap_dialog );
			if( null != animator ) {
				animator.setLayoutAnimation( null );
			}

			onAttachedToWindow();
			update( (IAPUpdater) mData.clone() );
		} else {
			logger.error( "parent is null" );
		}
	}

	protected void onAttachedToWindow () {
		logger.info( "onAttachedToWindow" );
		mViewAnimator = (ViewAnimator) mView.findViewById( R.id.aviary_view_animator );

		final FeatherActivity activity = (FeatherActivity) getContext();

		mDataService = activity.getMainController().getService( LocalDataService.class );

		mStoreWrapper.onAttach( (FeatherActivity) getContext() );
		mStoreWrapper.registerReceivers();
	}

	protected void onDetachedFromWindow () {
		logger.info( "onDetachedFromWindow" );
	}

	private boolean removeFromParent () {
		logger.info( "removeFromParent" );
		if( null != mView ) {
			ViewGroup parent = (ViewGroup) mView.getParent();
			if( null != parent ) {
				parent.removeView( mView );
				onDetachedFromWindow();
				return true;
			}
		}
		return false;
	}

	public void dismiss ( boolean animate ) {
		logger.info( "dismiss, animate: " + animate );

		getListView().setOnPackSelectedListener( null );

		mStoreWrapper.unregisterReceivers();
		mStoreWrapper.onDetach();
		setOnCloseListener( null );

		if( animate ) {
			hide();
		} else {
			removeFromParent();
		}
	}

	protected void hide () {
		logger.info( "hide" );
		if( ! isValid() ) {
			return;
		}
		mView.post( mHideRunnable );
	}

	public boolean isValid () {
		return mView != null && mView.getWindowToken() != null;
	}

	public ViewParent getParent() {
		if( null != mView ) {
			return mView.getParent();
		}
		return null;
	}

	public Context getContext () {
		if( null != mView ) {
			return mView.getContext();
		}
		return null;
	}

	private void handleHide () {
		logger.info( "handleHide" );

		if( ! isValid() ) {
			return;
		}

		Animation animation = AnimationUtils.loadAnimation( mView.getContext(), R.anim.aviary_iap_close_animation );
		AnimationListener listener = new AnimationListener() {

			@Override
			public void onAnimationStart ( Animation animation ) {}

			@Override
			public void onAnimationRepeat ( Animation animation ) {}

			@Override
			public void onAnimationEnd ( Animation animation ) {
				removeFromParent();
			}
		};
		animation.setAnimationListener( listener );
		mView.startAnimation( animation );
	}

	public void setOnCloseListener ( OnCloseListener listener ) {
		mCloseListener = listener;
	}

	@Override
	public void onPackSelected ( long packid, PackType packType, final String identifier ) {
		logger.info( "onPackSelected: " + packid );

		update( new IAPUpdater.Builder().setPackId( packid )
				        .setPackType( packType )
				        .setEvent( "shop_details: opened" )
				        .addEventAttributes( "pack", identifier )
				        .addEventAttributes( "from", "shop_list" )
				        .build() );
	}


	void launchSubscriptionActivity ( final String whereFrom ) {
		final String secret = mDataService.getIntentExtra(AviaryIntent.EXTRA_API_KEY_SECRET, "");
		final String billingPublicKey = mDataService.getIntentExtra(AviaryIntent.EXTRA_BILLING_PUBLIC_KEY, "");

		Intent intent2 = AviaryIntent.createSubscriptionIntent( getContext(), secret, billingPublicKey, whereFrom );
		getContext().startActivity( intent2 );
	}

	public AviaryStoreWrapper getStoreWrapper() {
		return mStoreWrapper;
	}
}
