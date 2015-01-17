package com.aviary.android.feather.sdk.widget;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.Checkable;
import android.widget.RelativeLayout;

import com.aviary.android.feather.sdk.R;
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.ObjectAnimator;

public class EffectThumbLayout extends RelativeLayout implements Checkable {

	public long id = - 1;

	private boolean mChecked;
	private boolean mOpened;

	private int mThumbAnimationDuration;
	private int mTranslationHeight = 0;
	private View mHiddenView;
	private View mImageView;
	private static final boolean IS_HONEYCOMB = Build.VERSION.SDK_INT > 10;
	private static final Interpolator INTERPOLATOR = new DecelerateInterpolator( 1.0f );
	private ObjectAnimator mAnimator;

	static final String LOG_TAG = "EffectThumbLayout";

	public EffectThumbLayout ( Context context, AttributeSet attrs ) {
		super( context, attrs );
		init( context, attrs, 0 );
	}

	private void init ( Context context, AttributeSet attrs, int defStyle ) {
		Resources.Theme theme = context.getTheme();
		TypedArray array = theme.obtainStyledAttributes( attrs, R.styleable.AviaryEffectThumbLayout, defStyle, 0 );
		mThumbAnimationDuration = array.getInteger( R.styleable.AviaryEffectThumbLayout_aviary_animationDuration, 200 );
	}

	private ObjectAnimator getAnimator () {
		if( null == mAnimator ) {
			mAnimator = ObjectAnimator.ofFloat( mImageView, "translationY", 0, 0 );
			mAnimator.setDuration( mThumbAnimationDuration );
			mAnimator.setInterpolator( INTERPOLATOR );
		}
		return mAnimator;
	}

	private void cancelAnimation () {
		ObjectAnimator animator = getAnimator();
		if( null != animator ) {
			animator.removeAllListeners();
			animator.cancel();
		}
	}

	@Override
	public boolean isChecked () {
		return mChecked;
	}

	@Override
	public void setChecked ( boolean checked ) {
		if( mChecked != checked ) {
			mChecked = checked;
		}
	}

	@Override
	public void toggle () {
	}

	@Override
	public void setSelected ( boolean selected ) {
		super.setSelected( selected );
	}

	public void open () {
		if( IS_HONEYCOMB ) {
			if( ! mOpened ) {
				mOpened = true;
				animateView( mThumbAnimationDuration, false );
			}
		} else {
			setIsOpened( true );
		}
	}

	public void close () {
		if( IS_HONEYCOMB ) {
			if( mOpened ) {
				mOpened = false;
				animateView( mThumbAnimationDuration, true );
			}
		} else {
			setIsOpened( false );
		}
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public void setIsOpened ( final boolean value ) {

		if( value != mOpened ) {

			Log.i( LOG_TAG, "setIsOpened(" + id + "): " + value );
			if( null == mHiddenView || null == mImageView || null == getHandler() ) {
				return;
			}

			mTranslationHeight = mHiddenView.getHeight() + mHiddenView.getPaddingBottom() + mHiddenView.getPaddingTop();

			if( ! ( mTranslationHeight > 0 ) ) {
				post( new Runnable() {
					@Override
					public void run () {
						setIsOpened( value );
					}
				} );
				return;
			}

			mOpened = value;
			mHiddenView.setVisibility( mOpened ? View.VISIBLE : View.INVISIBLE );

			if( IS_HONEYCOMB ) {
				cancelAnimation();
				mImageView.setTranslationY( value ? - mTranslationHeight : 0 );
			} else {
				LayoutParams params = (LayoutParams) mImageView.getLayoutParams();
				params.bottomMargin = value ? mTranslationHeight : 0;
				mImageView.setLayoutParams( params );
			}
		}
	}

	@Override
	protected void onDetachedFromWindow () {
		super.onDetachedFromWindow();
	}

	@Override
	protected void onAttachedToWindow () {
		super.onAttachedToWindow();
		mOpened = isChecked();
		mHiddenView = findViewById( R.id.aviary_hidden );
		mImageView = findViewById( R.id.aviary_image );
		setIsOpened( mOpened );
	}

	private Animator.AnimatorListener mCloseListener = new Animator.AnimatorListener() {
		@Override
		public void onAnimationStart ( final Animator animator ) {
		}

		@Override
		public void onAnimationEnd ( final Animator animator ) {
			if( null != mHiddenView ) {
				mHiddenView.setVisibility( View.INVISIBLE );
			}
		}

		@Override
		public void onAnimationCancel ( final Animator animator ) {
		}

		@Override
		public void onAnimationRepeat ( final Animator animator ) {
		}
	};

	private Animator.AnimatorListener mOpenListener = new Animator.AnimatorListener() {
		@Override
		public void onAnimationStart ( final Animator animator ) {
			if( null != mHiddenView ) {
				mHiddenView.setVisibility( View.VISIBLE );
			}
		}

		@Override
		public void onAnimationEnd ( final Animator animator ) {
		}

		@Override
		public void onAnimationCancel ( final Animator animator ) {
		}

		@Override
		public void onAnimationRepeat ( final Animator animator ) {
		}
	};

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	protected void animateView ( final int durationMs, final boolean isClosing ) {

		if( null == mHiddenView || null == mImageView || null == getHandler() ) {
			return;
		}

		mTranslationHeight = mHiddenView.getMeasuredHeight() + mHiddenView.getPaddingTop() + mHiddenView.getPaddingBottom();
		final float startY = isClosing ? - mTranslationHeight : 0;
		final float endY = isClosing ? 0 : - mTranslationHeight;

		if( ! ( mTranslationHeight > 0 ) ) {
			post( new Runnable() {
				@Override
				public void run () {
					animateView( durationMs, isClosing );
				}
			} );
			return;
		}

		cancelAnimation();

		ObjectAnimator animator = getAnimator();
		animator.setFloatValues( startY, endY );
		animator.addListener( isClosing ? mCloseListener : mOpenListener );
		animator.start();
	}

}

