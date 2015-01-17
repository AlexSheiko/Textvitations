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
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.AnimatorSet;
import com.nineoldandroids.animation.ObjectAnimator;
import com.nineoldandroids.animation.ValueAnimator;

public class BlemishOverlay extends AviaryOverlay {

	enum State {
		NONE,
		FIRST,
		SECOND,
		THIRD
	}

	private State mState = State.NONE;

	private View mView1;
	private View mView2;

	private final Rect viewRect1;
	private final Rect viewRect2;

	private Drawable ripple;
	private Drawable arrow;

	private Drawable mTitleDrawable1;
	private Drawable mTextDrawable1;
	private CharSequence mTitleText1;
	private CharSequence mDetailText1;
	private float mTextWidthFraction1;
	private CharSequence mTextRelativePosition1;

	private Drawable mTitleDrawable2;
	private Drawable mTextDrawable2;
	private CharSequence mTitleText2;
	private CharSequence mDetailText2;
	private float mTextWidthFraction2;
	private CharSequence mTextRelativePosition2;

    public boolean mConfigChange = false;

    private Layout.Alignment mTextAlign1;

	private int alpha2 = 0;
	private int alpha1 = 0;

	public BlemishOverlay ( final Context context, int style, View view1, View view2 ) {
		super( context, ToolLoaderFactory.getToolName(ToolLoaderFactory.Tools.BLEMISH), style, ID_BLEMISH );

		this.mView1 = view1;
		this.mView2 = view2;

		this.viewRect1 = new Rect();
		this.viewRect2 = new Rect();

		this.ripple = generateRipple();
		this.arrow = generateArrow();

		final Resources res = context.getResources();

		// image
		mTitleText1 = res.getString( R.string.feather_overlay_tap_title );
		mDetailText1 = res.getString( R.string.feather_overlay_blemish_tap_text );
		mTextWidthFraction1 = res.getFraction( R.fraction.aviary_overlay_blemish_text1_width, 100, 100 );
		mTextRelativePosition1 = res.getString( R.string.aviary_overlay_blemish_text1_position );

		mTitleText2 = res.getString( R.string.feather_overlay_zoom_title );
		mDetailText2 = res.getString( R.string.feather_overlay_zoom_text );
		mTextWidthFraction2 = res.getFraction( R.fraction.aviary_overlay_blemish_text2_width, 100, 100 );
		mTextRelativePosition2 = res.getString( R.string.aviary_overlay_blemish_text2_position );

		final String textAlign = res.getString( R.string.aviary_overlay_default_text_align );

		if( Layout.Alignment.ALIGN_CENTER.name()
				.equals( textAlign ) ) {
			mTextAlign1 = Layout.Alignment.ALIGN_CENTER;
		} else if( Layout.Alignment.ALIGN_OPPOSITE.equals( textAlign ) ) {
			mTextAlign1 = Layout.Alignment.ALIGN_OPPOSITE;
		} else {
			mTextAlign1 = Layout.Alignment.ALIGN_NORMAL;
		}
		addCloseButton( ALIGN_PARENT_LEFT, ALIGN_PARENT_BOTTOM );
	}

	@Override
	protected Animator generateInAnimation () {

		AnimatorSet animatorSet = new AnimatorSet();

		// first fade in animation
		Animator fadein = ObjectAnimator.ofFloat( this, "alpha", 0, 1 );
		Animator animation1 = ObjectAnimator.ofInt( this, "alpha1", 0, 255 );
		Animator animation2 = ObjectAnimator.ofInt( this, "alpha2", 0, 255 );
		Animator animation3 = ValueAnimator.ofInt( 0, 255 );

		animation1.setStartDelay( 200 );
		animation2.setStartDelay( 400 );
		animation3.setStartDelay( 400 );

		fadein.setDuration( getAnimationDuration() );
		animation1.setDuration( getAnimationDuration() );
		animation2.setDuration( getAnimationDuration() );
		animation3.setDuration( getAnimationDuration() );


		fadein.addListener( new Animator.AnimatorListener() {
			@Override
			public void onAnimationStart ( final Animator animation ) {}

			@Override
			public void onAnimationEnd ( final Animator animation ) {
				onAnimationComplete();
			}

			@Override
			public void onAnimationCancel ( final Animator animation ) {}

			@Override
			public void onAnimationRepeat ( final Animator animation ) {}
		} );

		animation1.addListener( new Animator.AnimatorListener() {
			@Override
			public void onAnimationStart ( final Animator animation ) {
			}

			@Override
			public void onAnimationEnd ( final Animator animation ) {
				onAnimationComplete();
			}

			@Override
			public void onAnimationCancel ( final Animator animation ) {

			}

			@Override
			public void onAnimationRepeat ( final Animator animation ) {

			}
		} );

		animation2.addListener( new Animator.AnimatorListener() {
			@Override
			public void onAnimationStart ( final Animator animation ) {
			}

			@Override
			public void onAnimationEnd ( final Animator animation ) {
				onAnimationComplete();
			}

			@Override
			public void onAnimationCancel ( final Animator animation ) {
			}

			@Override
			public void onAnimationRepeat ( final Animator animation ) {
			}
		} );

		animatorSet.playSequentially(
				fadein,
				animation1,
				animation2,
				animation3 );

		return animatorSet;
	}

	private void onAnimationComplete() {
		logger.info( "onAnimationComplete. state: %s", mState );

		switch( mState ) {
			case NONE:
				break;

			case FIRST:
				setState( State.SECOND );
				break;

			case SECOND:
				setState( State.THIRD );
				break;

			case THIRD:
				break;
		}
	}

	@SuppressWarnings( "unused" )
	public void setAlpha2( int alpha ) {
		this.alpha2 = alpha;
		arrow.setAlpha( alpha );
		mTitleDrawable2.setAlpha( alpha );
		mTextDrawable2.setAlpha( alpha );
		postInvalidate();
	}

	@SuppressWarnings( "unused" )
	public int getAlpha2() {
		return alpha2;
	}

	@SuppressWarnings( "unused" )
	public void setAlpha1( int alpha ) {
		this.alpha1 = alpha;
		ripple.setAlpha( alpha );
		mTitleDrawable1.setAlpha( alpha );
		mTextDrawable1.setAlpha( alpha );
		postInvalidate();
	}

	@SuppressWarnings( "unused" )
	public int getAlpha1() {
		return alpha1;
	}

	@Override
	public boolean onTouchEvent ( final MotionEvent event ) {
		if( event.getAction() == MotionEvent.ACTION_DOWN ) {
			hide( TAG_CLOSE_FROM_BACKGROUND );
			return true;
		}

		return true;
	}

	@Override
	public void calculatePositions () {
		calculateTextLayouts();
	}

    @Override
    public void forceInvalidate() {
        mConfigChange = true;
        forceCalculatePositions();
        invalidate();
    }

	@Override
	protected void doShow () {
		logger.info( "doShow" );
		if( ! isAttachedToParent() ) {
			return;
		}
		fadeIn();
	}

	@Override
	protected void inAnimationCompleted () {
		logger.info( "inAnimationCompleted" );
		if( null != getCloseButton() ) {
			getCloseButton().setVisibility( View.VISIBLE );
		}
	}

	@Override
	protected void dispatchDraw ( final Canvas canvas ) {
		if( getVisibility() != View.VISIBLE || ! isAttachedToParent() || null == mView1 ) {
			return;
		}

        if( mConfigChange ) {
            forceCalculatePositions();
        }
		else calculateTextLayouts();
		canvas.drawColor( getBackgroundColor() );

		if( mState == State.SECOND || mState == State.THIRD ) {
			ripple.draw( canvas );
			mTextDrawable1.draw( canvas );
			mTitleDrawable1.draw( canvas );
		}

		if( mState == State.THIRD ) {
			arrow.draw( canvas );
			mTextDrawable2.draw( canvas );
			mTitleDrawable2.draw( canvas );

			if( ! USE_CIRCLE ) {
				canvas.save();
				canvas.translate( viewRect2.left, viewRect2.top );

				if( alpha2 < 255 ) {
					int count = canvas.saveLayerAlpha( 0, 0, viewRect2.width(), viewRect2.height(), alpha2, Canvas.HAS_ALPHA_LAYER_SAVE_FLAG );
					mView2.draw( canvas );
					canvas.restoreToCount( count );
				} else {
					mView2.draw( canvas );
				}

				canvas.restore();
			}
		}

		super.dispatchDraw( canvas );
	}

    public void forceCalculatePositions() {

        logger.warn( "forceCalculatePositions" );

        final DisplayMetrics metrics = getDisplayMetrics();
        mView1.getGlobalVisibleRect( viewRect1 );

        ripple.setBounds( viewRect1.left + viewRect1.width() / 3, viewRect1.top + viewRect1.height() / 3, viewRect1.left + viewRect1.width() / 3 + ripple.getIntrinsicWidth(),
                viewRect1.top + viewRect1.height() / 3 + ripple.getIntrinsicHeight() );

        int textWidth = (int) ( ( metrics.widthPixels ) * ( mTextWidthFraction1 / 100f ) );

        // TEXT
        mTextDrawable1 = generateTextDrawable( getContext(), mDetailText1, textWidth, mTextAlign1 );
        Rect textBounds = generateBounds( mTextDrawable1, ripple.getBounds(), getTextMargins(), mTextRelativePosition1 );
        mTextDrawable1.setBounds( textBounds );

        // TITLE
        mTitleDrawable1 = generateTitleDrawable( getContext(), mTitleText1, textWidth, mTextAlign1 );
        Rect titleBounds = new Rect( 0, 0, mTitleDrawable1.getIntrinsicWidth(), mTitleDrawable1.getIntrinsicHeight() );
        titleBounds.offsetTo( textBounds.left, textBounds.top - titleBounds.height() - getTitleMargins() );

        mTitleDrawable1.setBounds( titleBounds );

        mView2.getGlobalVisibleRect( viewRect2 );
        arrow.setBounds( viewRect2.centerX(), viewRect2.top - arrow.getIntrinsicHeight(), viewRect2.centerX() + arrow.getIntrinsicWidth(), viewRect2.top );

        textWidth = (int) ( ( metrics.widthPixels ) * ( mTextWidthFraction2 / 100f ) );

        // TEXT
        mTextDrawable2 = generateTextDrawable( getContext(), mDetailText2, textWidth, mTextAlign1 );
        textBounds = generateBounds( mTextDrawable2, arrow.getBounds(), getTextMargins(), mTextRelativePosition2 );
        mTextDrawable2.setBounds( textBounds );

        // TITLE
        mTitleDrawable2 = generateTitleDrawable( getContext(), mTitleText2, textWidth, mTextAlign1 );
        titleBounds = new Rect( 0, 0, mTitleDrawable2.getIntrinsicWidth(), mTitleDrawable2.getIntrinsicHeight() );
        titleBounds.offsetTo( textBounds.left, textBounds.top - titleBounds.height() - getTitleMargins() );
        mTitleDrawable2.setBounds( titleBounds );

    }

	private void calculateTextLayouts () {
		if( ! isAttachedToParent() ) {
			return;
		}

		final DisplayMetrics metrics = getDisplayMetrics();

		if( mState == State.FIRST ) {
			// nothing to calculate...
		} else if( mState == State.SECOND ) {
			if( null == mTextDrawable1 || null == mTitleDrawable1 ) {
				mView1.getGlobalVisibleRect( viewRect1 );

				ripple.setBounds( viewRect1.left + viewRect1.width() / 3, viewRect1.top + viewRect1.height() / 3, viewRect1.left + viewRect1.width() / 3 + ripple.getIntrinsicWidth(),
				                  viewRect1.top + viewRect1.height() / 3 + ripple.getIntrinsicHeight() );
				ripple.setAlpha( 0 );

				int textWidth = (int) ( ( metrics.widthPixels ) * ( mTextWidthFraction1 / 100f ) );

				// TEXT
				mTextDrawable1 = generateTextDrawable( getContext(), mDetailText1, textWidth, mTextAlign1 );
				Rect textBounds = generateBounds( mTextDrawable1, ripple.getBounds(), getTextMargins(), mTextRelativePosition1 );
				mTextDrawable1.setBounds( textBounds );
				mTextDrawable1.setAlpha( 0 );

				// TITLE
				mTitleDrawable1 = generateTitleDrawable( getContext(), mTitleText1, textWidth, mTextAlign1 );
				Rect titleBounds = new Rect( 0, 0, mTitleDrawable1.getIntrinsicWidth(), mTitleDrawable1.getIntrinsicHeight() );
				titleBounds.offsetTo( textBounds.left, textBounds.top - titleBounds.height() - getTitleMargins() );

				mTitleDrawable1.setAlpha( 0 );
				mTitleDrawable1.setBounds( titleBounds );

			}
		} else if( mState == State.THIRD ) {
			if( null == mTextDrawable2 || null == mTitleDrawable2 ) {
				mView2.getGlobalVisibleRect( viewRect2 );
				arrow.setBounds( viewRect2.centerX(), viewRect2.top - arrow.getIntrinsicHeight(), viewRect2.centerX() + arrow.getIntrinsicWidth(), viewRect2.top );
				arrow.setAlpha( 0 );

				int textWidth = (int) ( ( metrics.widthPixels ) * ( mTextWidthFraction2 / 100f ) );

				// TEXT
				mTextDrawable2 = generateTextDrawable( getContext(), mDetailText2, textWidth, mTextAlign1 );
				Rect textBounds = generateBounds( mTextDrawable2, arrow.getBounds(), getTextMargins(), mTextRelativePosition2 );
				mTextDrawable2.setBounds( textBounds );
				mTextDrawable2.setAlpha( 0 );

				// TITLE
				mTitleDrawable2 = generateTitleDrawable( getContext(), mTitleText2, textWidth, mTextAlign1 );
				Rect titleBounds = new Rect( 0, 0, mTitleDrawable2.getIntrinsicWidth(), mTitleDrawable2.getIntrinsicHeight() );
				titleBounds.offsetTo( textBounds.left, textBounds.top - titleBounds.height() - getTitleMargins() );
				mTitleDrawable2.setBounds( titleBounds );
				mTitleDrawable2.setAlpha( 0 );
			}
		}
	}

	private Rect generateBounds ( Drawable drawable, Rect relativeTo, int margins, CharSequence relativePosition ) {
		final DisplayMetrics metrics = getDisplayMetrics();
		final int drawableWidth = drawable.getIntrinsicWidth();
		final int drawableHeight = drawable.getIntrinsicHeight();

		Rect textBounds = new Rect( 0, 0, drawableWidth, drawableHeight );

		int left;
		int top = relativeTo.top - textBounds.height() - margins;

		if( POSITION_LEFT.equals( relativePosition ) ) {
			left = relativeTo.left - drawableWidth;
		} else if( POSITION_CENTER.equals( relativePosition ) ) {
			left = relativeTo.centerX() - drawableWidth / 2;
		} else {
			left = relativeTo.right;
		}

		textBounds.offsetTo( left, top );

		if( textBounds.right > metrics.widthPixels ) {
			textBounds.offsetTo( metrics.widthPixels - textBounds.width() - margins, textBounds.top );
		} else if( textBounds.left < 0 ) {
			textBounds.offsetTo( margins, textBounds.top );
		}

		return textBounds;
	}

	private void setState( State state ) {
		logger.info( "setState: %s", state );
		this.mState = state;
		calculatePositions();
	}

	@Override
	public boolean showDelayed ( long delay ) {
		setState( State.FIRST );
		return super.showDelayed( delay );
	}

}
