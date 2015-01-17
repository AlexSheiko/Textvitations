package com.aviary.android.feather.sdk.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

import com.aviary.android.feather.library.graphics.Point2D;
import com.aviary.android.feather.sdk.R;
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.AnimatorSet;
import com.nineoldandroids.animation.ObjectAnimator;

import it.sephiroth.android.library.imagezoom.ImageViewTouch;
import it.sephiroth.android.library.imagezoom.graphics.IBitmapDrawable;

public class ImageViewSpotSingleTap extends ImageViewTouch implements Animator.AnimatorListener {

	public static enum TouchMode {
		// mode for pan and zoom
		IMAGE,
		// mode for drawing
		DRAW
	}

	public static interface OnTapListener {

		void onTap ( float points[], float radius );
	}

	AnimatorSet mAnimator;
	private float mBrushSize = 10;
	private float radius = 0;

	protected float mCurrentScale = 1;
	protected Matrix mInvertedMatrix = new Matrix();

	private Paint mShapePaint = new Paint();
	private Paint mTextPaint = new Paint();
	private Paint mTextRectPaint = new Paint();

	protected TouchMode mTouchMode = TouchMode.DRAW;

	protected float mX = 0, mY = 0;

	protected float mStartX, mStartY;

	private OnTapListener mTapListener;

	boolean mDrawFadeCircle = true;
	boolean mCanceled = false;

	RectF mTextRect = new RectF();
	Rect mTextBounds = new Rect();

	private String mToolTip = "";

	private float mTextSize = 50f;
	private float X_TEXT_OFFSET = 150;
	private float Y_TEXT_OFFSET = 150;
	private float TEXT_PADDING = 20;

	public ImageViewSpotSingleTap ( Context context, AttributeSet set ) {
		this( context, set, 0 );
	}

	public ImageViewSpotSingleTap ( Context context, AttributeSet attrs, int defStyle ) {
		super( context, attrs, defStyle );
		onCreate( context );
	}

	public void setRadius ( float value ) {
		this.radius = value;
		invalidate();
	}

	public float getRadius () {
		return radius;
	}

	@Override
	public void onAnimationStart ( final Animator animation ) {
		invalidate();
	}

	@Override
	public void onAnimationEnd ( final Animator animation ) {
		invalidate();
	}

	@Override
	public void onAnimationCancel ( final Animator animation ) {

	}

	@Override
	public void onAnimationRepeat ( final Animator animation ) {
//		Log.i( LOG_TAG, "onAnimationRepeat" );
		invalidate();
	}

	private void startAnimation () {

		radius = 0;
		mShapePaint.setAlpha( 255 );

		Animator set1 = ObjectAnimator.ofFloat( this, "radius", 0, mBrushSize );
		set1.setDuration( 200 );

		AnimatorSet set2 = new AnimatorSet();
		set2.setInterpolator( new DecelerateInterpolator( 1f ) );
		set2.setDuration( 200 );
		set2.playTogether( ObjectAnimator.ofFloat( this, "radius", mBrushSize, (int) ( mBrushSize * 1.3 ) ), ObjectAnimator.ofInt( mShapePaint, "alpha", 255, 0 ) );

		mAnimator.playSequentially( set1, set2 );

		mAnimator.setInterpolator( new AccelerateInterpolator( 1f ) );
		mAnimator.start();
	}

	private void onCreate ( Context context ) {
//		Log.i( LOG_TAG, "onCreate" );

		mToolTip = context.getString( R.string.feather_blemish_tool_tip );
		mTextSize = context.getResources()
				.getDimensionPixelSize( R.dimen.aviary_textSizeMedium );
		TEXT_PADDING = mTextSize / 2;
		X_TEXT_OFFSET = Y_TEXT_OFFSET = mTextSize * 3;

		mAnimator = new AnimatorSet();
		mAnimator.addListener( this );

		mShapePaint.setAntiAlias( true );
		mShapePaint.setStyle( Paint.Style.STROKE );
		mShapePaint.setColor( Color.WHITE );
		mShapePaint.setStrokeWidth( 6 );

		mTextPaint.setColor( Color.WHITE );
		mTextPaint.setTextSize( mTextSize );
		mTextPaint.getTextBounds( mToolTip, 0, mToolTip.length(), mTextBounds );

		mTextRectPaint.setARGB( 150, 0, 0, 0 );

		setLongClickable( false );
	}

	@Override
	protected void onDraw ( Canvas canvas ) {
		super.onDraw( canvas );

		if( mDrawFadeCircle ) {
			if( radius > 0 ) {
				canvas.drawCircle( mX, mY, radius, mShapePaint );
			}
		}

		if( mCanceled ) {
			mTextRect.set( mX - TEXT_PADDING - X_TEXT_OFFSET, mY - mTextBounds.height() * 1.25f - TEXT_PADDING - Y_TEXT_OFFSET, mX + mTextBounds.width() + TEXT_PADDING - X_TEXT_OFFSET,
			               mY + mTextBounds.height() * 0.5f + TEXT_PADDING - Y_TEXT_OFFSET );

			canvas.drawRoundRect( mTextRect, 10, 10, mTextRectPaint );
			canvas.drawText( mToolTip, mX - X_TEXT_OFFSET, mY - Y_TEXT_OFFSET, mTextPaint );
		}

	}

	public void setOnTapListener ( OnTapListener listener ) {
		mTapListener = listener;
	}

	@Override
	protected void init ( Context context, AttributeSet attrs, int defStyle ) {
		super.init( context, attrs, defStyle );
	}

	public TouchMode getDrawMode () {
		return mTouchMode;
	}

	public void setDrawMode ( TouchMode mode ) {
		if( mode != mTouchMode ) {
			mTouchMode = mode;
			onDrawModeChanged();
		}
	}

	@Override
	protected void onDrawableChanged ( Drawable drawable ) {
		super.onDrawableChanged( drawable );

		if( drawable != null && ( drawable instanceof IBitmapDrawable ) ) {
			onDrawModeChanged();
		}
	}

	@Override
	protected void onLayoutChanged ( int left, int top, int right, int bottom ) {
		super.onLayoutChanged( left, top, right, bottom );

		if( null != getDrawable() ) {
			onDrawModeChanged();
		}
	}

	protected void onDrawModeChanged () {
		if( mTouchMode == TouchMode.DRAW ) {
			Log.i( LOG_TAG, "onDrawModeChanged" );

			Matrix m1 = new Matrix( getImageMatrix() );
			mInvertedMatrix.reset();

			float[] v1 = getMatrixValues( m1 );
			m1.invert( m1 );
			float[] v2 = getMatrixValues( m1 );

			mInvertedMatrix.postTranslate( - v1[Matrix.MTRANS_X], - v1[Matrix.MTRANS_Y] );
			mInvertedMatrix.postScale( v2[Matrix.MSCALE_X], v2[Matrix.MSCALE_Y] );

			mCurrentScale = getScale() * getBaseScale();
		}

		setDoubleTapEnabled( mTouchMode == TouchMode.IMAGE );
		setScaleEnabled( mTouchMode == TouchMode.IMAGE );
	}

	public RectF getImageRect () {
		if( getDrawable() != null ) {
			return new RectF( 0, 0, getDrawable().getIntrinsicWidth(), getDrawable().getIntrinsicHeight() );
		} else {
			return null;
		}
	}

	public static float[] getMatrixValues ( Matrix m ) {
		float[] values = new float[9];
		m.getValues( values );
		return values;
	}

	public void setBrushSize ( float value ) {
		Log.i( LOG_TAG, "setBrushSize: " + value );
		mBrushSize = value;
	}

	@Override
	public boolean onDown ( final MotionEvent e ) {
//		Log.i( LOG_TAG, "onDown" );

		if( mTouchMode == TouchMode.DRAW ) {
			mStartX = mX = e.getX();
			mStartY = mY = e.getY();
			mDrawFadeCircle = false;
		}

		return super.onDown( e );
	}

	@Override
	public boolean onUp ( final MotionEvent e ) {
//		Log.i( LOG_TAG, "onUp" );
		mCanceled = false;
		postInvalidate();

		return super.onUp( e );
	}

	@Override
	public boolean onSingleTapUp ( final MotionEvent e ) {
//		Log.i( LOG_TAG, "onSingleTapUp" );
		if( mTouchMode == TouchMode.DRAW ) {
			return false;
		}

		return super.onSingleTapUp( e );
	}

	@Override
	public boolean onSingleTapConfirmed ( final MotionEvent e ) {
//		Log.i( LOG_TAG, "onSingleTapConfirmed" );
		if( mTouchMode == TouchMode.DRAW ) {
			mDrawFadeCircle = true;
			startAnimation();
			if( null != mTapListener ) {
				float mappedPoints[] = new float[2];
				mappedPoints[0] = e.getX();
				mappedPoints[1] = e.getY();
				mInvertedMatrix.mapPoints( mappedPoints );
				mTapListener.onTap( mappedPoints, mBrushSize / mCurrentScale );
			}
			return true;
		}
		return super.onSingleTapConfirmed( e );
	}

	@Override
	public boolean onScroll ( final MotionEvent e1, final MotionEvent e2, final float distanceX, final float distanceY ) {
//		Log.i( LOG_TAG, "onScroll" );
		if( mTouchMode == TouchMode.DRAW ) {
			mX = e2.getX();
			mY = e2.getY();
			mCanceled = true;
			postInvalidate();
			return false;
		}

		return super.onScroll( e1, e2, distanceX, distanceY );
	}

	@Override
	public boolean onFling ( final MotionEvent e1, final MotionEvent e2, final float velocityX, final float velocityY ) {
//		Log.i( LOG_TAG, "onFling" );
		if( mTouchMode == TouchMode.DRAW ) {
			return false;
		}

		return super.onFling( e1, e2, velocityX, velocityY );
	}

	@Override
	protected ScaleGestureDetector.OnScaleGestureListener getScaleListener () {
		return new TapScaleListener();
	}

	class TapScaleListener extends ScaleListener {

		@Override
		public boolean onScaleBegin ( final ScaleGestureDetector detector ) {
//			Log.i( LOG_TAG, "onScaleBegin" );
			if( mTouchMode == TouchMode.DRAW ) {
				mStartX = mX = detector.getFocusX();
				mStartY = mY = detector.getFocusY();
				mCanceled = true;
				postInvalidate();
				return true;
			}
			return super.onScaleBegin( detector );
		}

		@Override
		public boolean onScale ( final ScaleGestureDetector detector ) {
//			Log.i( LOG_TAG, "onScale" );
			if( mTouchMode == TouchMode.DRAW ) {
				mX = detector.getFocusX();
				mY = detector.getFocusY();
				postInvalidate();
				return true;
			}
			return super.onScale( detector );
		}

		@Override
		public void onScaleEnd ( final ScaleGestureDetector detector ) {
//			Log.i( LOG_TAG, "onScaleEnd" );
			mCanceled = false;
			super.onScaleEnd( detector );
		}
	}

	/*

	@Override
	public boolean onTouchEvent ( MotionEvent event ) {

		if( mTouchMode == TouchMode.DRAW && event.getPointerCount() == 1 ) {
			float x = event.getX();
			float y = event.getY();

			switch( event.getAction() ) {
				case MotionEvent.ACTION_DOWN:
					mX = x;
					mY = y;
					mStartX = x;
					mStartY = y;
					mDrawFadeCircle = false;
					break;
				case MotionEvent.ACTION_MOVE:
					mX = x;
					mY = y;
					if( Point2D.distance( mStartX, mStartY, x, y ) > 50 ) {
						mCanceled = true;
					}
					invalidate();
					break;
				case MotionEvent.ACTION_UP:
					invalidate();
					if( ! mCanceled ) {
						mDrawFadeCircle = true;
						startAnimation();
						if( null != mTapListener ) {
							float mappedPoints[] = new float[2];
							mappedPoints[0] = x;
							mappedPoints[1] = y;
							mInvertedMatrix.mapPoints( mappedPoints );
							mTapListener.onTap( mappedPoints, mBrushSize / mCurrentScale );
						}
					}
					mCanceled = false;
					break;
			}
			return true;
		} else {
			if( mTouchMode == TouchMode.IMAGE ) {
				return super.onTouchEvent( event );
			} else {
				return false;
			}
		}

	}
	*/

}
