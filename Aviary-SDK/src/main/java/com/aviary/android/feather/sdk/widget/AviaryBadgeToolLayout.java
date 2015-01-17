package com.aviary.android.feather.sdk.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.aviary.android.feather.library.content.ToolEntry;
import com.aviary.android.feather.library.filters.ToolLoaderFactory;
import com.aviary.android.feather.library.services.BadgeService;
import com.aviary.android.feather.library.services.BadgeService.OnToolBadgesUpdateListener;
import com.aviary.android.feather.sdk.AviaryMainController;
import com.aviary.android.feather.sdk.FeatherActivity;
import com.aviary.android.feather.sdk.R;

/**
 * Special {@link LinearLayout} view which will manage the visibility
 * of the badge icon.<br />
 * This view will register itself as listener to the {@link BadgeService} service.<br />
 * When there is an update to the badges this view will manage the visibility change of
 * its badge-view.<br />
 * 
 * @author alessandro
 */
public class AviaryBadgeToolLayout extends LinearLayout implements OnToolBadgesUpdateListener {

	static final String LOG_TAG = "AviaryBadgeToolLayout";

	/** the badge-view contained in the xml layout */
	View mBadgeView;
	/** tool icon */
	ImageView mImageView;
	/** tool label */
	TextView mTextView;
	/** {@link BadgeService} reference */
	BadgeService mBadgeService;

	public AviaryBadgeToolLayout ( Context context ) {
		this( context, null );
	}

	public AviaryBadgeToolLayout ( Context context, AttributeSet attrs ) {
		this( context, attrs, 0 );
	}

	public AviaryBadgeToolLayout ( Context context, AttributeSet attrs, int defStyle ) {
		super( context, attrs );
	}

	@Override
	public void setTag( Object tag ) {
		super.setTag( tag );
		onTagChanged( tag );
	}

	protected void onTagChanged( Object tag ) {
		if ( null != tag ) {
			ToolEntry entry = (ToolEntry) tag;
			mImageView.setImageResource( entry.iconResourceId );
			mTextView.setText( entry.labelResourceId );

			if ( null != getContext() ) {
				setContentDescription( getContext().getString( entry.labelResourceId ) );
			}

			if( null != mBadgeService ) {
				onToolBadgesUpdate( mBadgeService );
			}
			postInvalidate();
		}
	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();
		mBadgeView = findViewById( R.id.aviary_badge );
		mTextView = (TextView) findViewById( R.id.aviary_text );
		mImageView = (ImageView) findViewById( R.id.aviary_image );

		registerToService();
	}

	protected void registerToService() {
		FeatherActivity activity = (FeatherActivity) getContext();
		if ( null != activity ) {
			AviaryMainController controller = activity.getMainController();
			if ( null != controller ) {
				mBadgeService = controller.getService( BadgeService.class );
				mBadgeService.registerOnToolBadgesUpdateListener( this );
			}
		}
	}

	protected void removeFromService() {
		if ( null != mBadgeService ) {
			mBadgeService.removeOnToolBadgesUpdateListener( this );
		}
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();

		if ( null != mBadgeService ) {
			onToolBadgesUpdate( mBadgeService );
		}
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
	}

	@Override
	protected void finalize() throws Throwable {
		removeFromService();
		super.finalize();
	}

	@Override
	public void onToolBadgesUpdate( BadgeService service ) {

		ToolEntry entry = (ToolEntry) getTag();

		if ( null != entry ) {
			if ( service.getIsActive( entry.name ) ) {
				showBadge();
			} else {
				hideBadge();
			}
		}
	}

	@Override
	public void onToolBadgeSingleUpdate( BadgeService service, ToolLoaderFactory.Tools type ) {

		ToolEntry entry = (ToolEntry) getTag();

		if ( null != entry && entry.name == type ) {

			Log.i( LOG_TAG, "onToolBadgeSingleUpdate: " + type );

			if ( service.getIsActive( entry.name ) ) {
				showBadge();
			} else {
				hideBadge();
			}
		}
	}

	protected void hideBadge() {
		if ( null != mBadgeView ) {
			mBadgeView.setVisibility( View.GONE );
		}
	}

	protected void showBadge() {
		if ( null != mBadgeView ) {
			mBadgeView.setVisibility( View.VISIBLE );
		}
	}

}
