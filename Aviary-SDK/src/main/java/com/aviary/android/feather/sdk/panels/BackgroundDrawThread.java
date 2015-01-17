package com.aviary.android.feather.sdk.panels;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Handler;

import com.aviary.android.feather.common.log.LoggerFactory;
import com.aviary.android.feather.common.log.LoggerFactory.Logger;
import com.aviary.android.feather.common.log.LoggerFactory.LoggerType;
import com.aviary.android.feather.headless.filters.MoaJavaToolStrokeResult;
import com.aviary.android.feather.headless.filters.NativeToolFilter;
import com.aviary.android.feather.headless.filters.NativeToolFilter.BrushMode;
import com.aviary.android.feather.headless.moa.MoaStrokeParameter;
import com.aviary.android.feather.library.utils.ArrayUtils;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Thread used to draw using MoaJavaTools.cpp
 * @author alessandro
 *
 */
final class BackgroundDrawThread extends Thread {

	public static final int PREVIEW_INITIALIZED = AbstractPanel.LAST_VALID_MESSAGE + 1;

	/**
	 * Draw queue
	 * @author alessandro
	 *
	 */
	static class DrawQueue extends LinkedBlockingQueue<float[]> {

		private static final long serialVersionUID = 1L;

		private BrushMode mode;
		private float radius;
		private volatile boolean completed;
		private PointF startPoint;

		public DrawQueue ( BrushMode mode, float radius, float points[] ) {
			this.mode = mode;
			this.radius = radius;
			this.completed = false;
			this.startPoint = new PointF();

			if ( null != points && points.length >= 2 ) {
				this.startPoint.x = points[0];
				this.startPoint.y = points[1];
			}
			add( points );
		}

		public PointF getOriginalPoint() {
			return startPoint;
		}

		public BrushMode getMode() {
			return mode;
		}

		public float getRadius() {
			return radius;
		}

		public void end() {
			completed = true;
		}

		public boolean isCompleted() {
			return completed;
		}
	}

	static final Logger logger = LoggerFactory.getLogger( "BackgroundDrawThread", LoggerType.ConsoleLoggerType );

	private boolean singleTapAllowed;
	private boolean started;
	private volatile boolean running;
	private final Queue<DrawQueue> mQueue;
	private DrawQueue mCurrentQueue;
	private final PointF mLastPoint;
	private NativeToolFilter filter;
	private Handler handler;
	private double brushMultiplier;
	private boolean mRegisterStrokeInitParams;

	SoftReference<Bitmap> mSourceBitmap;
	SoftReference<Bitmap> mPreviewBitmap;

	public BackgroundDrawThread ( String name, int priority, NativeToolFilter filter, Handler handler, double brushMultiplier ) {
		super( name );
		this.mQueue = new LinkedBlockingQueue<DrawQueue>();
		this.mLastPoint = new PointF();
		this.filter = filter;
		this.handler = handler;
		this.brushMultiplier = brushMultiplier;
		setPriority( priority );
		init();
	}

	public void init() {}

	synchronized public void setSingleTapAllowed( boolean value ) {
		this.singleTapAllowed = value;
	}

	private void notifyPixelsChanged(Canvas canvas, Paint paint, final Bitmap current) {
		if(!isInterrupted()) {
			if (null != canvas && null != paint && null != current && ! current.isRecycled()) {
				paint.setColor(current.getPixel(0, 0));
				canvas.drawPoint(0, 0, paint);
			}
		}
	}

	/**
	 * Register the c++ results from the drawStart call and pass it back
	 * to the stroke params
	 * @param value
	 */
	public void setRegisterStrokeInitParams ( final boolean value ) {
		mRegisterStrokeInitParams = value;
	}

	synchronized public void start( Bitmap src, Bitmap preview ) {
		if (started) return;
		logger.info("start");
		mSourceBitmap = new SoftReference<Bitmap>(src);
		mPreviewBitmap = new SoftReference<Bitmap>(preview);
		started = true;
		running = true;
		super.start();
	}

	synchronized public void start( Bitmap bitmap ) {
		if (started) return;
		logger.info("start");
		mSourceBitmap = new SoftReference<Bitmap>(bitmap);
		started = true;
		running = true;
		super.start();
	}

	synchronized public void quit() {
		logger.info( "quit" );
		started = true;
		running = false;
		filter = null;
		interrupt();
	};

	synchronized public void singleTap( float radius, float points[], BrushMode brushType ) {
		if( !running ) return;

		logger.info( "singleTap" );

		if( singleTapAllowed ) {

		}
	}

	synchronized public void pathStart( float radius, float points[], BrushMode brushType ) {
		if ( !running ) return;
		logger.info( "pathStart" );

		if ( mCurrentQueue != null ) {
			mCurrentQueue.end();
			mCurrentQueue = null;
		}

		mLastPoint.set( points[0], points[1] );

		DrawQueue queue = new DrawQueue( brushType, radius, points );
		mQueue.add( queue );
		mCurrentQueue = queue;
	}

	synchronized public void pathEnd() {
		if ( !running || mCurrentQueue == null ) return;
		
		logger.info( "pathEnd" );

		if( singleTapAllowed ) {
			mCurrentQueue.add( new float[]{ mLastPoint.x, mLastPoint.y } );
		} else {
			if( ! mLastPoint.equals( mCurrentQueue.getOriginalPoint() ) ) {
				logger.log( "adding tail" );
				mCurrentQueue.add( new float[]{ mLastPoint.x, mLastPoint.y } );
			} else {
				logger.log( "skipping tail" );
			}
		}

		mCurrentQueue.end();
		mCurrentQueue = null;
	}

	public void moveTo( float[] values ) {
		if ( !running || mCurrentQueue == null ) return;

		mLastPoint.set( values[0], values[1] );
		mCurrentQueue.add( values );
	}

	public void lineTo( float values[] ) {
		if ( !running || mCurrentQueue == null ) return;

		// logger.log( "lineTo: %.2fx%.2f", values[0], values[1] );

		float length = PointF.length( Math.abs( mLastPoint.x - values[0] ), Math.abs( mLastPoint.y - values[1] ) );

		if ( length > 10 ) {
			mLastPoint.set( values[0], values[1] );
			mCurrentQueue.add( values );
		} else {
			logger.warn( "skipping point, too close... " + length );
		}
	}

	public boolean isCompleted() {
		return mQueue.size() == 0;
	}

	public int getQueueSize() {
		return mQueue.size();
	}

	void getLerp( PointF pt1, PointF pt2, float t, PointF dstPoint ) {
		dstPoint.set( pt1.x + ( pt2.x - pt1.x ) * t, pt1.y + ( pt2.y - pt1.y ) * t );
	}

	public void clear() {
		logger.info( "clear" );
		if ( running && mQueue != null ) {
			synchronized ( mQueue ) {
				while ( mQueue.size() > 0 ) {
					DrawQueue element = mQueue.poll();
					if ( null != element ) {
						logger.log( "end element..." );
						element.end();
					}
				}
			}
		}
	}

	/**
	 * Ensure the drawing queue is completed
	 */
	public void finish() {
		logger.info( "finish" );
		if ( running && mQueue != null ) {
			synchronized ( mQueue ) {
				Iterator<DrawQueue> iterator = mQueue.iterator();
				while ( iterator.hasNext() ) {
					DrawQueue element = iterator.next();
					if ( null != element ) {
						logger.log( "end element..." );
						element.end();
					}
				}
			}
		}
	}

	@Override
	public boolean isInterrupted() {
		if ( !running ) return true;
		return super.isInterrupted();
	}

	@Override
	public void run() {

		while ( !started ) {
			// wait until is not yet started
		}

		boolean s = false;
		boolean firstPoint = true;
		float points[];
		float prevPoints[] = null;
		Rect drawRect = new Rect();

		final MoaJavaToolStrokeResult strokeResult;
		if( mRegisterStrokeInitParams ) {
			strokeResult = new MoaJavaToolStrokeResult();
		} else {
			strokeResult = null;
		}

		logger.log( "thread.start!" );

		Bitmap current = null;
		Canvas canvasPreview = null;
		Paint paintPreview = new Paint();

		if( null != filter && !isInterrupted() ) {
			if( null != mSourceBitmap ) {
				final Bitmap src = mSourceBitmap.get();
				final Bitmap dst = mPreviewBitmap != null ? mPreviewBitmap.get() : null;
				
				if( null != src ) {
					if( !src.isRecycled() && ( dst != null ? !dst.isRecycled() : true ) ) {
						filter.init( src, dst );

						if (null != dst) {
							canvasPreview = new Canvas(dst);
							current = dst;
						}
						else if (null != src) {
							canvasPreview = new Canvas(src);
							current = src;
						}

                        if(null != filter && !isInterrupted()) filter.renderPreview();
						if ( null != handler && !isInterrupted() ) {
							notifyPixelsChanged(canvasPreview, paintPreview, current);
							handler.sendEmptyMessage( AbstractPanel.PREVIEW_BITMAP_UPDATED );
							handler.sendEmptyMessage( PREVIEW_INITIALIZED );
						}
					}
				}
			}
		}

		MoaStrokeParameter strokeData;

		while ( !isInterrupted() ) {

			if ( isInterrupted() ) {
				logger.log( "isInterrupted:true" );
				break;
			}

			if ( mQueue.size() > 0 && !isInterrupted() ) {

				logger.log( "queue.size: " + mQueue.size() );

				if ( !s ) {
					s = true;
					if ( null != handler ) handler.sendEmptyMessage( AbstractPanel.PROGRESS_START );
				}

				firstPoint = true;

				DrawQueue element = mQueue.element();

				if ( null == element ) {
					mQueue.poll();
					continue;
				}

				float radius = element.getRadius();
				logger.log( "radius: %.2f", radius );
				BrushMode mode = element.getMode();

				strokeData = new MoaStrokeParameter( mode, radius );

				List<Float> path = new ArrayList<Float>();
				int pathIndex = 0;

				while ( element.size() > 0 || !element.isCompleted() ) {

					if ( isInterrupted() ) {
						break;
					}

					if ( null == filter ) break;

					points = element.poll();
					if ( points == null ) continue;

					if ( firstPoint ) {
						firstPoint = false;
						drawRect.set( 
								(int) ( points[0] - radius * brushMultiplier ), 
								(int) ( points[1] - radius * brushMultiplier ), 
								(int) ( points[0] + radius * brushMultiplier ), 
								(int) ( points[1] + radius * brushMultiplier ) );

						strokeData.addPoint( points );

						path.add( points[0] );
						path.add( points[1] );
						pathIndex = 2;

						if ( null != filter && !isInterrupted() ) {
							filter.setBrushMode( mode );
							filter.drawStart( radius, 0, drawRect.centerX(), drawRect.centerY(), strokeResult );

							if( null != strokeResult ) {
								logger.log( "strokeResult: %s", strokeResult );
								strokeData.setInitParams( strokeResult );
							}

							if( null != filter )
								filter.renderPreview( drawRect );
						}

						if ( null != handler && !isInterrupted() ) {
							notifyPixelsChanged(canvasPreview, paintPreview, current);
							handler.sendEmptyMessage( AbstractPanel.PREVIEW_BITMAP_UPDATED );
						}

					} else {

						if ( prevPoints == null ) {
							continue;
						}

						logger.log( "path.size: %d", path.size() );
						logger.log( "element.iscompleted: %b", element.isCompleted() );
						logger.log( "element.size: %d", element.size() );

						int incrementIndex = 0;

						if( path.size() == 2 && element.isCompleted() && element.size() == 0 && singleTapAllowed ) {
							// single tap
							logger.warn( "single tap!" );

							float px = path.get( path.size() - 2 );
							float py = path.get( path.size() - 1 );

							if( px != points[0] || py != points[1] ) {
								path.add( points[0] );
								path.add( points[1] );
								strokeData.addPoint( points[0], points[1] );
								incrementIndex = 2;
							} else {
								logger.warn( "dont add more points.." );
							}
						} else {
							path.add( points[0] );
							path.add( points[1] );
							strokeData.addPoint( points[0], points[1] );
							incrementIndex = 2;
						}

						int left1 = (int) prevPoints[0];
						int left2 = (int) points[0];

						int top1 = (int) prevPoints[1];
						int top2 = (int) points[1];

						drawRect.set( 
								Math.min( left1, left2 ), 
								Math.min( top1, top2 ), 
								Math.max( left1, left2 ), 
								Math.max( top1, top2 ) );
						drawRect.sort();
						drawRect.inset( -(int) ( radius * brushMultiplier ), -(int) ( radius * brushMultiplier ) );

						if ( element.isCompleted() ) {
							logger.log( "size: " + element.size() + ", empty: " + element.isEmpty() );
						}

						if ( null != filter && !isInterrupted() ) {
							
							float[] invalidationPoints = filter.draw( 
									radius, 
									0, 
									pathIndex, 
									element.isCompleted() && element.isEmpty(),
									ArrayUtils.toPrimitive( path ) );
							
							if ( null != invalidationPoints && invalidationPoints.length == 4 ) {
								drawRect.set( 
										(int) invalidationPoints[0], 
										(int) invalidationPoints[1], 
										(int) invalidationPoints[2], 
										(int) invalidationPoints[3] );
							}
							if( null != filter )
								filter.renderPreview( drawRect );
						}

						pathIndex += incrementIndex;

						if ( null != handler && !isInterrupted() ) {
							notifyPixelsChanged(canvasPreview, paintPreview, current);
							handler.sendEmptyMessage( AbstractPanel.PREVIEW_BITMAP_UPDATED );
						}
					}
					prevPoints = points;
				}

				// now remove the element from the queue
				if ( null != filter && !isInterrupted() ) {
					filter.addStrokeData( strokeData );
				}
				mQueue.poll();

			} else {
				if ( s ) {
					if ( null != handler && !isInterrupted() ) handler.sendEmptyMessage( AbstractPanel.PROGRESS_END );
					s = false;
				}
			}
		}

		logger.log( "exiting while isInterrupted: " + isInterrupted() );

		if ( null != handler && !isInterrupted() ) handler.sendEmptyMessage( AbstractPanel.PROGRESS_END );
		
		handler = null;
		
		logger.log( "exiting thread..." );
	};
}
