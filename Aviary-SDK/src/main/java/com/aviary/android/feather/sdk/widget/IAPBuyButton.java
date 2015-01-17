package com.aviary.android.feather.sdk.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Pair;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.aviary.android.feather.sdk.R;
import com.aviary.android.feather.cds.CdsUtils;
import com.aviary.android.feather.cds.CdsUtils.PackOption;

public class IAPBuyButton extends RelativeLayout {

	TextView mTextView;
	View mProgress;
	CdsUtils.PackOptionWithPrice mOption;
	long mPackId;

	public IAPBuyButton( Context context, AttributeSet attrs ) {
		super( context, attrs );
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();
		mTextView = (TextView) findViewById( R.id.aviary_buy_button_text );
		mProgress = findViewById( R.id.aviary_buy_button_loader );
	}

	public CdsUtils.PackOptionWithPrice getPackOption() {
		return mOption;
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		getHandler().removeCallbacks( checkDownloadStatus );
	}

	Runnable checkDownloadStatus = new Runnable() {

		@Override
		public void run() {
			if( mPackId > - 1 && null != getContext() && null != mOption ) {
				Pair<PackOption, String> result = CdsUtils.getPackOptionDownloadStatus( getContext(), mPackId );
				if( null != result ) {
					if( null != getContext() ) {
						setPackOption( new CdsUtils.PackOptionWithPrice( result.first ), mPackId );
					}
				}
			}
		}
	};

	public long getPackId() {
		return mPackId;
	}

	public void setPackOption( CdsUtils.PackOptionWithPrice option, long packId ) {
		if( null != option && option.equals( mOption ) ) {
			// no need to update
			return;
		}

		mOption = option;
		mPackId = packId;

		if( null != getHandler() )
			getHandler().removeCallbacks( checkDownloadStatus );

		if( null == option ) {
			return;
		}

		boolean oldEnableStatus = isEnabled();
		boolean newEnableStatus = true;
		int oldProgressVisibility = mProgress.getVisibility();
		int oldTextVisibility = mTextView.getVisibility();
		int newProgressVisibility = View.INVISIBLE;
		int newTextVisibility = View.VISIBLE;

		switch( option.option ) {
			case RESTORE:
				mTextView.setText( R.string.feather_iap_restore );
				break;

			case PURCHASE:
				mTextView.setVisibility( View.VISIBLE );
				if( null != option.price ) {
					mTextView.setText( option.price );
				}
				else {
					mTextView.setText( R.string.feather_iap_unavailable );
				}
				break;

			case OWNED:
				mTextView.setText( R.string.feather_iap_owned );
				newEnableStatus = false;
				break;

            case UNINSTALL:
                mTextView.setText( R.string.feather_iap_uninstall );
                break;

			case ERROR:
				mTextView.setText( R.string.feather_iap_retry );
				break;

			case FREE:
				mTextView.setText( R.string.feather_iap_download );
				break;

            case INSTALL:
                mTextView.setText( R.string.feather_iap_install );
                break;

			case DOWNLOAD_COMPLETE:
				mTextView.setText( R.string.feather_iap_installing );
				newEnableStatus = false;
				break;

			case DOWNLOADING:
				newProgressVisibility = View.VISIBLE;
				newTextVisibility = View.INVISIBLE;
				newEnableStatus = false;

				if( null != getHandler() )
					getHandler().postDelayed( checkDownloadStatus, (long) ( ( Math.random() * 100 ) + 900 ) );
				break;

			case PACK_OPTION_BEING_DETERMINED:
				newProgressVisibility = View.VISIBLE;
				newTextVisibility = View.INVISIBLE;
				newEnableStatus = false;
				break;

			case DOWNLOAD_ERROR:
				mTextView.setText( R.string.feather_iap_retry );
				break;
		}

		if( oldEnableStatus != newEnableStatus ) {
			setEnabled( newEnableStatus );
		}

		if( oldProgressVisibility != newProgressVisibility ) {
			mProgress.setVisibility( newProgressVisibility );
		}

		if( oldTextVisibility != newTextVisibility ) {
			mTextView.setVisibility( newTextVisibility );
		}

	}

}
