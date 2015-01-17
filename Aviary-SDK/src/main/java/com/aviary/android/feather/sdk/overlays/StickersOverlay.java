package com.aviary.android.feather.sdk.overlays;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Region;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.DynamicLayout;
import android.text.Layout;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import com.aviary.android.feather.common.tracking.AviaryTracker;
import com.aviary.android.feather.library.filters.ToolLoaderFactory;
import com.aviary.android.feather.sdk.R;

public class StickersOverlay extends AviaryOverlay {

	private final Rect viewRect;
	private View packView;
	private Drawable arrow;

	private Paint eraser;

	private DynamicLayout mTitleLayout;
	private DynamicLayout mDetailsLayout;

	private Rect mDetailsPosition;
	private Rect mTitlePosition;

	private CharSequence mTitleText;
	private CharSequence mDetailText;

	private float mTextWidthFraction;

	private CharSequence mTextRelativePosition;

	private Layout.Alignment mTextAlign;

	public StickersOverlay ( final Context context, int styleId, View view ) {
		this( context, styleId, view, ToolLoaderFactory.Tools.STICKERS, ID_STICKERS );
	}

	public StickersOverlay ( final Context context, int styleId, View view, ToolLoaderFactory.Tools toolName, int tutorial_id ) {
		super( context, ToolLoaderFactory.getToolName(toolName), styleId, tutorial_id );

		final Resources res = context.getResources();

		this.viewRect = new Rect();
		this.arrow = generateArrow();

		this.packView = view;

		mTitleText = getTitleText( res );
		mDetailText = getDetailText( res );

		mTextWidthFraction = getTextWidthFraction( res );
		mTextRelativePosition = getTextRelativePosition( res );

		final String textAlign = res.getString( R.string.aviary_overlay_default_text_align );

		if( Layout.Alignment.ALIGN_CENTER.name()
				.equals( textAlign ) ) {
			mTextAlign = Layout.Alignment.ALIGN_CENTER;
		} else if( Layout.Alignment.ALIGN_OPPOSITE.equals( textAlign ) ) {
			mTextAlign = Layout.Alignment.ALIGN_OPPOSITE;
		} else {
			mTextAlign = Layout.Alignment.ALIGN_NORMAL;
		}

		if( USE_CIRCLE ) {
			PorterDuffXfermode mBlender = new PorterDuffXfermode( PorterDuff.Mode.CLEAR );
			eraser = new Paint();
			eraser.setColor( 0xFFFFFF );
			eraser.setAlpha( 0 );
			eraser.setXfermode( mBlender );
			eraser.setAntiAlias( true );
		}

		addCloseButton( ALIGN_PARENT_BOTTOM, ALIGN_PARENT_LEFT );
	}

	protected CharSequence getTextRelativePosition ( final Resources res ) {
		return res.getString( R.string.aviary_overlay_stickers_text_position );
	}

	protected float getTextWidthFraction ( final Resources res ) {
		return res.getFraction( R.fraction.aviary_overlay_stickers_text_width, 100, 100 );
	}

	protected CharSequence getTitleText ( final Resources res ) {
		return res.getString( R.string.feather_stickers );
	}

	protected CharSequence getDetailText ( final Resources res ) {
		return res.getString( R.string.feather_overlay_stickers_text );
	}

	public void update ( View view ) {
		logger.info( "update" );
		this.packView = view;
		this.mDetailsLayout = null;
		calculatePositions();
		postInvalidate();
	}

	public void setTitle ( int resId ) {
		setTitle( getContext().getString( resId ) );
	}

	public void setTitle ( CharSequence title ) {
		mTitleText = title;
		mTitleLayout = null;
		postInvalidate();
	}

	public void setText ( int resId ) {
		setText( getContext().getString( resId ) );
	}

	public void setText ( CharSequence text ) {
		mDetailText = text;
		mDetailsLayout = null;
		postInvalidate();
	}

	@Override
	public void setAlpha ( final float alpha ) {
		if( null != mTitleLayout ) {
			mTitleLayout.getPaint()
					.setAlpha( (int) ( alpha * 255 ) );
		}

		if( null != mDetailsLayout ) {
			mDetailsLayout.getPaint()
					.setAlpha( (int) ( alpha * 255 ) );
		}

		this.arrow.setAlpha( (int) ( alpha * 255 ) );
		super.setAlpha( alpha );
	}

	@Override
	protected void dispatchDraw ( final Canvas canvas ) {
		if( getVisibility() != View.VISIBLE || ! isAttachedToParent() || null == packView ) {
			return;
		}

		if( USE_CIRCLE ) {
			if( Build.VERSION.SDK_INT <= Build.VERSION_CODES.HONEYCOMB ) {
				Path path = new Path();
				path.addCircle( viewRect.centerX(), viewRect.centerY(), viewRect.width() / 1.5f, Path.Direction.CW );
				canvas.clipPath( path, Region.Op.DIFFERENCE );
			}
		}

		canvas.drawColor( getBackgroundColor() );

		if( USE_CIRCLE ) {
			if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB ) {
				canvas.drawCircle( viewRect.centerX(), viewRect.centerY(), viewRect.width() / 1.5f, eraser );
			}
		}

		//arrow.setBounds( viewRect.centerX(), viewRect.top - arrow.getIntrinsicHeight(), viewRect.centerX() + arrow.getIntrinsicWidth(), viewRect.top );

		calculateTextLayouts();
        arrow.draw( canvas );

        if( null != mDetailsLayout ) {
			canvas.save();
			canvas.translate( mDetailsPosition.left, mDetailsPosition.top );
			if( DEBUG_PAINT ) {
				canvas.drawRect( 0, 0, (float) ( mDetailsLayout.getWidth() ), (float) ( mDetailsLayout.getHeight() ), debugPaint );
			}
			mDetailsLayout.draw( canvas );
			canvas.restore();
		}

		if( null != mTitleLayout ) {
			canvas.save();
			canvas.translate( mTitlePosition.left, mTitlePosition.top );

			if( DEBUG_PAINT ) {
				canvas.drawRect( 0, 0, (float) ( mTitleLayout.getWidth() ), (float) ( mTitleLayout.getHeight() ), debugPaint );
			}

			mTitleLayout.draw( canvas );
			canvas.restore();
		}

		if( ! USE_CIRCLE ) {
			canvas.save();
			canvas.translate( viewRect.left, viewRect.top );
			packView.draw( canvas );
			canvas.restore();
		}

		super.dispatchDraw( canvas );
	}

	@Override
	protected void calculatePositions () {
		logger.info( "calculatePositions" );
		calculateTextLayouts();
	}

	private void calculateTextLayouts () {
		if( ! isAttachedToParent() ) {
			return;
		}

		packView.getGlobalVisibleRect( viewRect );

        int centerX = viewRect.centerX();
        int centerY = viewRect.centerY();
        int top = viewRect.top;
        int left = viewRect.left;

        arrow.setBounds( viewRect.centerX(), viewRect.top - arrow.getIntrinsicHeight(), viewRect.centerX() + arrow.getIntrinsicWidth(), viewRect.top );

        DisplayMetrics metrics = getDisplayMetrics();

        int textWidth = (int) ( ( metrics.widthPixels ) * ( mTextWidthFraction / 100f ) );

        mDetailsLayout = generateTextLayout( mDetailText, textWidth, mTextAlign );
        mDetailsPosition = new Rect();

        if( POSITION_LEFT.equals( mTextRelativePosition ) ) {
            mDetailsPosition.left = left - textWidth / 2;
        } else if( POSITION_CENTER.equals( mTextRelativePosition ) ) {
            mDetailsPosition.left = centerX - textWidth / 2;
        } else {
            mDetailsPosition.left = viewRect.right;
        }

        mDetailsPosition.right = mDetailsPosition.left + mDetailsLayout.getWidth();
        mDetailsPosition.top = ( top - arrow.getIntrinsicHeight() - mDetailsLayout.getHeight() - getTextMargins() );
        mDetailsPosition.bottom = mDetailsPosition.top + mDetailsLayout.getHeight();

        if( mDetailsPosition.right > metrics.widthPixels ) {
            mDetailsPosition.offset( ( metrics.widthPixels - mDetailsPosition.right ) - getTextMargins(), 0 );
        } else if( mDetailsPosition.left < 0 ) {
            mDetailsPosition.left = getTextMargins();
        }

        mTitleLayout = generateTitleLayout( mTitleText, textWidth, mTextAlign );

        mTitlePosition = new Rect();
        mTitlePosition.left = mDetailsPosition.left;
        mTitlePosition.right = mDetailsPosition.right;
        mTitlePosition.top = ( mDetailsPosition.top - mTitleLayout.getHeight() - getTitleMargins() );
        mTitlePosition.bottom = mTitlePosition.top + mTitleLayout.getHeight();

        // check close button position
        LayoutParams params = (LayoutParams) generateDefaultLayoutParams();
        params.addRule( ALIGN_PARENT_BOTTOM );

        if( viewRect.centerX() < metrics.widthPixels / 2 ) {
            params.addRule( ALIGN_PARENT_RIGHT );
        } else {
            params.addRule( ALIGN_PARENT_LEFT );
        }
        params.setMargins( mClosebuttonMargins, mClosebuttonMargins, mClosebuttonMargins, mClosebuttonMargins );
        getCloseButton().setLayoutParams( params );

	}

	@Override
	protected void inAnimationCompleted () {
		logger.info( "inAnimationCompleted" );

		if( null != getCloseButton() ) {
			getCloseButton().setVisibility( View.VISIBLE );
		}
	}

	@Override
	protected void doShow () {
		logger.info( "doShow" );
		if( ! isAttachedToParent() ) {
			return;
		}
		fadeIn();
	}

	private void trackPackClicked () {
		if( null != getContext() ) {
			AviaryTracker.getInstance( getContext() )
					.tagEvent( getToolName() + ": tutorial_pack_selected" );
		}
	}

	@Override
	public boolean onTouchEvent ( final MotionEvent event ) {
		float xDelta = Math.abs( event.getRawX() - viewRect.centerX() );
		float yDelta = Math.abs( event.getRawY() - viewRect.centerY() );
		double distanceFromFocus = Math.sqrt( Math.pow( xDelta, 2 ) + Math.pow( yDelta, 2 ) );
		int radius = (int) ( viewRect.width() / 1.5f );

		if( USE_CIRCLE ) {
			if( event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_UP ) {
				if( distanceFromFocus < radius ) {
					trackPackClicked();
					return false;
				}
			}
		} else {

			if( viewRect.contains( (int) event.getRawX(), (int) event.getRawY() ) ) {
				trackPackClicked();
				return false;
			}
		}

		if( event.getAction() == MotionEvent.ACTION_DOWN ) {
			hide( TAG_CLOSE_FROM_BACKGROUND );
			return true;
		}

		return true;
	}

}
