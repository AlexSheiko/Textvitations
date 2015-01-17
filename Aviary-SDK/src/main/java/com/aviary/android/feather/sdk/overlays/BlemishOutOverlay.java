package com.aviary.android.feather.sdk.overlays;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;

import com.aviary.android.feather.library.filters.ToolLoaderFactory;
import com.aviary.android.feather.sdk.R;

public class BlemishOutOverlay extends AviaryOverlay {

	private Drawable mBlurDrawable;

	private Drawable mTitleDrawable1;
	private Drawable mTextDrawable1;

	private CharSequence mTitleText;
	private CharSequence mDetailText;

	private final Layout.Alignment mTextAlign;

	private final float mTitleWidthFraction;
	private final float mTextWidthFraction;

	public BlemishOutOverlay( Context context, int style ) {
		super( context, ToolLoaderFactory.getToolName(ToolLoaderFactory.Tools.BLEMISH) + "_out", style, ID_BLEMISH_CLOSE );

		final Resources res = context.getResources();
		mBlurDrawable = res.getDrawable( R.drawable.aviary_overlay_blur_blue );

		mTitleText = getTitleText( res );
		mDetailText = getDetailText( res );

		mTextWidthFraction = getTextWidthFraction( res );
		mTitleWidthFraction = getTitleWidthFraction( res );

		mTextAlign = Layout.Alignment.ALIGN_CENTER;
		addCloseButton( ALIGN_PARENT_LEFT, ALIGN_PARENT_BOTTOM );
	}

	protected float getTextWidthFraction( final Resources res ) {
		return res.getFraction( R.fraction.aviary_overlay_blemish_text4_width, 100, 100 );
	}

	protected float getTitleWidthFraction( final Resources res ) {
		return res.getFraction( R.fraction.aviary_overlay_blemish_text3_width, 100, 100 );
	}

	protected CharSequence getTitleText( final Resources res ) {
		return res.getString( R.string.feather_overlay_blemish_close_title );
	}

	protected CharSequence getDetailText( final Resources res ) {
		return res.getString( R.string.feather_overlay_blemish_close_text );
	}

	@Override
	public boolean onTouchEvent( final MotionEvent event ) {

		if( null != mCloseListener ) {
			trackTutorialClosed( TAG_CLOSE_FROM_BACKGROUND );
			mCloseListener.onClose( this );
			return true;
		}

		if( event.getAction() == MotionEvent.ACTION_DOWN ) {
			hide( TAG_CLOSE_FROM_BACKGROUND );
			return true;
		}
		return true;
	}

	@Override
	public void onClick( final View view ) {
		logger.info( "onClick: " + view );

		if( view == getCloseButton() && null != mCloseListener ) {
			trackTutorialClosed( TAG_CLOSE_FROM_BUTTON );
			mCloseListener.onClose( this );
			return;
		}

		super.onClick( view );
	}

	@Override
	protected void calculatePositions() {
		logger.info( "calculatePositions" );
		calculateTextLayouts();
	}


	private void calculateTextLayouts() {
		if( ! isAttachedToParent() ) {
			return;
		}

		final DisplayMetrics metrics = getDisplayMetrics();

		// blur
		int width = mBlurDrawable.getIntrinsicWidth();
		int height = mBlurDrawable.getIntrinsicHeight();
		final Rect bounds = new Rect( getWidth() / 2 - width / 2, getHeight() / 2 - height / 2, getWidth() / 2 + width / 2, getHeight() / 2 + height / 2 );
		mBlurDrawable.setBounds( bounds );


		// detail test
		int titleWidth = (int) ( ( metrics.widthPixels ) * ( mTitleWidthFraction / 100f ) );
		int textWidth = (int) ( ( metrics.widthPixels ) * ( mTextWidthFraction / 100f ) );

		mTitleDrawable1 = generateTitleDrawable( getContext(), mTitleText, titleWidth, mTextAlign );
		Rect textBounds = generateBounds( mTitleDrawable1, mBlurDrawable.getBounds(), getTextMargins(), "top" );
		mTitleDrawable1.setBounds( textBounds );
		mTitleDrawable1.setAlpha( 0 );

		mTextDrawable1 = generateHTMLTextDrawable( getContext(), mDetailText, textWidth, mTextAlign );
		textBounds = generateBounds( mTextDrawable1, mBlurDrawable.getBounds(), getTextMargins(), "bottom" );
		mTextDrawable1.setBounds( textBounds );
		mTextDrawable1.setAlpha( 0 );
	}

	private Rect generateBounds( Drawable drawable, Rect relativeTo, int margins, CharSequence relativePosition ) {
		final DisplayMetrics metrics = getDisplayMetrics();
		final int drawableWidth = drawable.getIntrinsicWidth();
		final int drawableHeight = drawable.getIntrinsicHeight();

		Rect textBounds = new Rect( 0, 0, drawableWidth, drawableHeight );

		if( "top".equals( relativePosition ) ) {
			textBounds.offsetTo( ( metrics.widthPixels - drawableWidth ) / 2, relativeTo.top - drawableHeight - margins );
		}
		else {
			textBounds.offsetTo( ( metrics.widthPixels - drawableWidth ) / 2, relativeTo.bottom + margins );
		}
		return textBounds;
	}

	@Override
	protected void doShow() {
		logger.info( "doShow" );
		if( ! isAttachedToParent() ) {
			return;
		}
		fadeIn();
	}

	@Override
	public void setAlpha( final float alpha ) {
		mBlurDrawable.setAlpha( (int) ( alpha * 255 ) );
		mTitleDrawable1.setAlpha( (int) ( alpha * 255 ) );
		mTextDrawable1.setAlpha( (int) ( alpha * 255 ) );
		super.setAlpha( alpha );
	}

	@Override
	protected void inAnimationCompleted() {
		if( null != getCloseButton() ) {
			getCloseButton().setVisibility( View.VISIBLE );
		}
	}

	@Override
	protected void dispatchDraw( final Canvas canvas ) {
		if( getVisibility() != View.VISIBLE || ! isAttachedToParent() ) {
			return;
		}

		canvas.drawColor( getBackgroundColor() );

		mBlurDrawable.draw( canvas );

		mTextDrawable1.draw( canvas );

		mTitleDrawable1.draw( canvas );

		super.dispatchDraw( canvas );
	}
}
