package com.aviary.android.feather.sdk.widget;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.widget.Toast;

import com.aviary.android.feather.cds.CdsUtils;
import com.aviary.android.feather.cds.IAPInstance;
import com.aviary.android.feather.cds.billing.util.IabHelper;
import com.aviary.android.feather.cds.billing.util.IabResult;
import com.aviary.android.feather.cds.billing.util.Inventory;
import com.aviary.android.feather.cds.billing.util.Purchase;
import com.aviary.android.feather.cds.billing.util.SkuDetails;
import com.aviary.android.feather.common.log.LoggerFactory;
import com.aviary.android.feather.common.tracking.AviaryTracker;
import com.aviary.android.feather.common.utils.PackageManagerUtils;
import com.aviary.android.feather.library.services.IAPService;
import com.aviary.android.feather.receipt.Receipt;
import com.aviary.android.feather.receipt.ReceiptManager;
import com.aviary.android.feather.sdk.FeatherActivity;
import com.aviary.android.feather.sdk.R;

import junit.framework.Assert;

import java.io.IOException;

public class AviaryStoreWrapper {

	static final LoggerFactory.Logger logger = LoggerFactory.getLogger( "AviaryStoreWrapper", LoggerFactory.LoggerType.ConsoleLoggerType );

	public static interface Callback {

		/** download status of a pack has changed */
		void onDownloadStatusChanged ( long packid, String packType, int status );

		/** a pack has been installed/removed */
		void onPackInstalled ( long packId, String packType, int purchased );

		/** success purchased a pack */
		void onPurchaseSuccess ( long packId, String packType, Purchase purchase );

		/** subscription purchased */
		void onSubscriptionPurchased ( String identifier, int purchased );

		/** cds service completed */
		void onServiceFinished ();
	}

	AviaryTracker tracker;
	FeatherActivity context;
	private Callback callback;
	private IAPService wrapper;

	/** listen for download status change of any pack */
	final BroadcastReceiver downloadStatusReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive ( final Context context, final Intent intent ) {

			logger.info( "downloadStatusReceiver::onReceive: %s", intent );

			if( null != intent && isActive() ) {
				String packType = intent.getStringExtra( "packType" );
				long packid = intent.getLongExtra( "packId", - 1 );
				int status = intent.getIntExtra( "status", - 1 );
				callback.onDownloadStatusChanged( packid, packType, status );
			}
		}
	};

	final BroadcastReceiver subscriptionPurchasedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive ( final Context context, final Intent intent ) {
			logger.info( "subscriptionPurchasedReceiver::onReceive: %s", intent );

			if( null != intent && isActive() ) {
				int purchased = intent.getIntExtra( "purchased", 0 );
				String identifier = intent.getStringExtra( "identifier" );
				callback.onSubscriptionPurchased( identifier, purchased );
			}
		}
	};

	final BroadcastReceiver packPurchasedReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive ( final Context context, final Intent intent ) {
			logger.info( "packPurchasedReceiver::onReceive: %s", intent );

			if( null != intent && isActive() ) {
				String packType = intent.getStringExtra( "packType" );
				long packId = intent.getLongExtra( "packId", - 1 );
				Purchase purchase = intent.getParcelableExtra( "purchase" );
				callback.onPurchaseSuccess( packId, packType, purchase );
			}
		}
	};

	final BroadcastReceiver packInstalledReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive ( final Context context, final Intent intent ) {

			logger.info( "packInstalledReceiver::onReceive: %s", intent );

			if( null != intent && isActive() ) {
				String packType = intent.getStringExtra( "packType" );
				long packId = intent.getLongExtra( "packId", - 1 );
				int purchased = intent.getIntExtra( "purchased", 0 );
				callback.onPackInstalled( packId, packType, purchased );
			}
		}
	};

	// listen for service complete events
	final ContentObserver mServiceFinishedContentObserver = new ContentObserver( new Handler() ) {
		@Override
		public void onChange ( boolean selfChange ) {
			onChange( selfChange, null );
		}

		@Override
		public void onChange ( boolean selfChange, Uri uri ) {
			logger.info( "mServiceFinishedContentObserver::onChange" );
			if( isActive() ) {
				callback.onServiceFinished();
			}
		}
	};

	public AviaryStoreWrapper ( Callback callback ) {
		this.callback = callback;
	}

	public void onAttach ( FeatherActivity activity ) {
		this.context = activity;
		this.tracker = AviaryTracker.getInstance( context );
		this.wrapper = activity.getMainController().getService( IAPService.class );
	}

	public void registerReceivers () {
		if( null != context ) {
			context.registerReceiver( packPurchasedReceiver, new IntentFilter( context.getPackageName() + CdsUtils.BROADCAST_PACK_PURCHASED ) );
			context.registerReceiver( packInstalledReceiver, new IntentFilter( context.getPackageName() + CdsUtils.BROADCAST_PACK_INSTALLED ) );
			context.registerReceiver( downloadStatusReceiver, new IntentFilter( context.getPackageName() + CdsUtils.BROADCAST_DOWNLOAD_STATUS_CHANGED ) );
			context.registerReceiver( subscriptionPurchasedReceiver, new IntentFilter( context.getPackageName() + CdsUtils.BROADCAST_SUBSCRIPTION_PURCHASED ) );
			context.getContentResolver()
					.registerContentObserver( PackageManagerUtils.getCDSProviderContentUri( context, "service/finished" ), false, mServiceFinishedContentObserver );
		}
	}

	public void unregisterReceivers () {
		if( null != context ) {
			// this can throw an IllegalArgumentException is receiver is not yet registered
			context.unregisterReceiver( packPurchasedReceiver );
			context.unregisterReceiver( packInstalledReceiver );
			context.unregisterReceiver( downloadStatusReceiver );
			context.unregisterReceiver( subscriptionPurchasedReceiver );
			context.getContentResolver()
					.unregisterContentObserver( mServiceFinishedContentObserver );
		}
	}

	public void onDetach () {
		if( null != context ) {
			context = null;
			tracker = null;
			callback = null;
		}
	}

	public boolean isActive () {
		return callback != null && context != null;
	}

	public boolean isSetupDone () {
		return wrapper.isSetupDone();
	}

	public boolean isAvailable () {
		return wrapper.isAvailable();
	}

	public IAPInstance getIAPInstance () {
		return wrapper;
	}

	public void startSetup ( boolean force, IabHelper.OnIabSetupFinishedListener listener ) {
		if( ! isActive() ) {
			return;
		}
		logger.info( "startSetup: %b", force );

		if( ! wrapper.isSetupDone() || force ) {
			wrapper.startSetup( listener );
		}
	}

	private void onPurchaseSuccess ( final Purchase purchase, final long packId, final String identifier, final String packType, final String price ) {
		logger.info( "onPurchaseSuccess: %s - %s (%s)", identifier, packType, purchase );

		if( ! isActive() ) {
			return;
		}

		int newDownloadStatus = - 1;

		sendReceipt( purchase, price );

		try {
			if( requestPackDownload( packId, true ) ) {
				// do nothing...
			} else {
				newDownloadStatus = DownloadManager.STATUS_FAILED;
			}
		} catch( Throwable t ) {
			newDownloadStatus = DownloadManager.STATUS_FAILED;

			StringBuilder sb = new StringBuilder();
			sb.append( context.getString( R.string.feather_download_start_failed ) );
			sb.append( "." );
			sb.append( "\n" );
			sb.append( "Cause: " );
			sb.append( t.getLocalizedMessage() );

			new AlertDialog.Builder( context ).setTitle( R.string.feather_iap_download_failed )
					.setMessage( sb.toString() )
					.setPositiveButton( android.R.string.cancel, null )
					.create()
					.show();
		}

		// send broadcasr about the pack purchased
		CdsUtils.notifyPackPurchased( context, packId, packType, purchase );

		if( newDownloadStatus > - 1 ) {
			callback.onDownloadStatusChanged( packId, packType, newDownloadStatus );
		}
	}

	public void purchase ( final long packid, final String identifier, final String packType, final String whereFrom, final String price ) {
		logger.info( "purchase {%d, %s, %s, %s, %s}", packid, identifier, packType, whereFrom, price );

		if( ! isActive() ) {
			return;
		}


		if( ! wrapper.isSetupDone() ) {
			Toast.makeText( context, "There was a problem connecting to the billing service. Please try again.", Toast.LENGTH_SHORT )
					.show();
			wrapper.startSetup( null );
			return;
		}

		IabHelper.OnIabPurchaseFinishedListener purchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
			public void onIabPurchaseFinished ( IabResult result, Purchase purchase ) {
				logger.log( "onIabPurchaseFinished: { result: %s, purchase: %s }", result, purchase );

				if( ! isActive() ) {
					logger.error( "context is no more valid" );
					return;
				}

				if( result.isFailure() ) {
					final int response = result.getResponse();
					switch( response ) {
						case IabHelper.IABHELPER_USER_CANCELLED:
							// no need to display a message
							break;
						default:
							Toast.makeText( context, result.getMessage(), Toast.LENGTH_SHORT )
									.show();
							break;
					}
				} else {
					onPurchaseSuccess( purchase, packid, identifier, packType, price );
				}
				tracker.trackEndPurchaseFlow( identifier, whereFrom, result.isSuccess() );
			}
		};

		if( null != wrapper && wrapper.isAvailable() ) {
			wrapper.launchPurchaseFlow( identifier, purchaseFinishedListener, null );
			tracker.trackBeginPurchaseFlow( identifier, whereFrom );
		} else {
			logger.error( "wrapper disposed or null" );
			Toast.makeText( context, R.string.feather_store_connection_problem, Toast.LENGTH_SHORT ).show();
		}
	}

	public void restore (
			final long packid, final String identifier, final String packType, final String whereFrom, final boolean isRestore, final boolean isFree, final boolean isError,
			final boolean isSubscription ) {
		logger.info( "restore {%d, %s, %s, %s, restore: %b, free: %b, error: %b, subscription: %s}", packid, identifier, packType, whereFrom, isRestore, isFree, isError,
		             isSubscription );

		if( ! isActive() ) {
			return;
		}

		if( ! isError ) {
			if( isFree ) {
				tracker.trackContentInstalled( identifier, whereFrom );
			} else {
				tracker.trackContentRestored( identifier, whereFrom );
			}

			sendReceipt( identifier, isFree, isRestore, isSubscription );
		}

		int newDownloadOption = - 1;

		try {
			if( requestPackDownload( packid, true ) ) {
				// do nothing here...
			} else {
				newDownloadOption = DownloadManager.STATUS_FAILED;
			}
		} catch( Throwable t ) {
			t.printStackTrace();

			newDownloadOption = DownloadManager.STATUS_FAILED;

			StringBuilder sb = new StringBuilder();
			sb.append( context.getString( R.string.feather_download_start_failed ) );
			sb.append( "." );
			sb.append( "\n" );
			sb.append( "Cause: " );
			sb.append( t.getLocalizedMessage() );

			new AlertDialog.Builder( context ).setTitle( R.string.feather_iap_download_failed )
					.setMessage( sb.toString() )
					.setPositiveButton( android.R.string.cancel, null )
					.create()
					.show();
		}

		if( newDownloadOption > - 1 ) {
			// we don't need this
			callback.onDownloadStatusChanged( packid, packType, newDownloadOption );
		}
	}

	boolean requestPackDownload ( final long packId, boolean notify ) throws AssertionError, IOException {
		logger.info( "requestPackDownload { packId: %d, notify: %b }", packId, notify );

		if( ! isActive() ) {
			return false;
		}

		Uri uri = PackageManagerUtils.getCDSProviderContentUri( context, "pack/id/" + packId + "/requestDownload/1" );
		logger.log( "updating: " + uri );

		int result = context.getContentResolver()
				.update( uri, new ContentValues(), null, null );
		logger.log( "result: " + result );

		Assert.assertTrue( "Failed to update the database, please try again later", result != 0 );

		// finally request to download the item
		String requestResult = CdsUtils.requestPackDownload( context, packId, notify );
		logger.log( "requestResult: %s", requestResult );
		return true;
	}

	void sendReceipt ( final String identifier, boolean free, boolean isRestore, boolean isSubscription ) {
		if( null == context ) {
			return;
		}

		logger.info( "sendReceipt{ identifier: %s, isFree: %b, isRestore: %b, isSubscription: %b }", identifier, free, isRestore, isSubscription );

		Receipt.Builder builder = new Receipt.Builder( free ).withProductId( identifier )
				.isNewPurchase( ! isRestore )
				.withPurchaseTime( System.currentTimeMillis() );

		if( isSubscription ) {
			builder.withOrderId( "subscription" );
			builder.withToken( "" );
			builder.withPrice( "" );
		}

		try {
			Receipt receipt = builder.build();
			ReceiptManager.getInstance( context )
					.sendTicket( receipt );
		} catch( AssertionError e ) {
			e.printStackTrace();
		}
	}

	void sendReceipt ( final Purchase purchase, String price ) {
		if( null == context ) {
			return;
		}

		logger.info( "sendReceipt{ item: %s, price: %s }", purchase, price );

		Receipt.Builder builder = new Receipt.Builder( false ).withProductId( purchase.getSku() )
				.withPurchaseTime( purchase.getPurchaseTime() )
				.withOrderId( purchase.getOrderId() )
				.withPrice( price )
				.isNewPurchase( true )
				.withToken( purchase.getToken() );

		try {
			Receipt receipt = builder.build();
			ReceiptManager.getInstance( context )
					.sendTicket( receipt );
		} catch( AssertionError e ) {
			e.printStackTrace();
		}
	}


	public CdsUtils.PackOptionWithPrice getPackOptionFromInventory( final String sku, Inventory inventory ) {

		if( null != inventory ) {

			Purchase itemPurchase = inventory.getPurchase( sku );
			if( null != itemPurchase ) {
				return new CdsUtils.PackOptionWithPrice( CdsUtils.PackOption.RESTORE );
			} else {
				SkuDetails itemDetail = inventory.getSkuDetails( sku );
				if( null != itemDetail ) {
					return new CdsUtils.PackOptionWithPrice( CdsUtils.PackOption.PURCHASE, itemDetail.getPrice() );
				}
			}
		}

		// something went wrong!
		return new CdsUtils.PackOptionWithPrice( CdsUtils.PackOption.ERROR );
	}
}
