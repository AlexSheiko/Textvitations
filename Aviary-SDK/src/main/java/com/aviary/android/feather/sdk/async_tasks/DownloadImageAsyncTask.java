package com.aviary.android.feather.sdk.async_tasks;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.util.Log;

import com.aviary.android.feather.common.utils.SystemUtils;
import com.aviary.android.feather.common.utils.os.AviaryAsyncTask;
import com.aviary.android.feather.library.Constants;
import com.aviary.android.feather.library.utils.DecodeUtils;
import com.aviary.android.feather.library.utils.ImageInfo;

/**
 * Load an Image bitmap asynchronous.
 *
 * @author alessandro
 */
public class DownloadImageAsyncTask extends AviaryAsyncTask<Context, Void, Bitmap> {

	private static final String TAG = "DownloadImageAsyncTask";

	/**
	 * The listener interface for receiving onImageDownload events. The class that is
	 * interested in processing a onImageDownload
	 * event implements this interface, and the object created with that class is
	 * registered with a component using the component's
	 * <code>addOnImageDownloadListener<code> method. When
	 * the onImageDownload event occurs, that object's appropriate
	 * method is invoked.
	 */
	public static interface OnImageDownloadListener {

		/**
		 * On download start.
		 */
		void onDownloadStart();

		/**
		 * On download complete.
		 *
		 * @param result the result
		 */
		void onDownloadComplete(Bitmap result, ImageInfo sizes);

		/**
		 * On download error.
		 *
		 * @param error the error
		 */
		void onDownloadError(String error);
	}

	private OnImageDownloadListener mListener;
	private Uri mUri;
	private String error;
	private ImageInfo mImageOutputInfo;
	private int mMaxSize;

	/**
	 * Instantiates a new download image async task.
	 * @param uri the image uri
	 * @param maxSize the image max size (in px)
	 */
	public DownloadImageAsyncTask(Uri uri, int maxSize) {
		super();
		mUri = uri;
		mMaxSize = maxSize;
		mImageOutputInfo = new ImageInfo();
	}

	/**
	 * Sets the on load listener.
	 *
	 * @param listener the new on load listener
	 */
	public void setOnLoadListener(OnImageDownloadListener listener) {
		mListener = listener;
	}

	@Override
	protected void PreExecute() {
		Log.i(TAG, "PreExecute: current thread #" + Thread.currentThread().getId());
		if (mListener != null) mListener.onDownloadStart();
	}

	@Override
	protected Bitmap doInBackground(Context... params) {
		Log.i(TAG, "doInBackground: current thread #" + Thread.currentThread().getId());

		final Context context = params[0];
		int max_size = - 1;

		if (mMaxSize > 0) {
			max_size = mMaxSize;
		}

		if (max_size <= 0) {
			max_size = getManagedMaxImageSize(context);
		}

		try {
			return DecodeUtils.decode(context, mUri, max_size, max_size, mImageOutputInfo);
		} catch (Exception e) {
			Log.e(TAG, "decode error", e);
			error = e.getMessage();
		}

		return null;
	}

	@Override
	protected void PostExecute(Bitmap result) {
		Log.i(TAG, "PostExecute: current thread #" + Thread.currentThread().getId());
		if (mListener != null) {
			if (result != null) {
				mListener.onDownloadComplete(result, mImageOutputInfo);
			}
			else {
				mListener.onDownloadError(error);
			}
		}

		mListener = null;
		mUri = null;
		error = null;
	}

	/**
	 * Return the maximum image size allowed for this device. Be careful if you want to
	 * modify the return value because it's easy to
	 * throw an {@link OutOfMemoryError} in android expecially when dealing with
	 * {@link Bitmap}.<br />
	 * Part of the application available memory has been already taken by the host
	 * application.
	 *
	 * @return the managed max image size
	 */
	public static int getManagedMaxImageSize(Context context) {

		final DisplayMetrics metrics = context.getResources().getDisplayMetrics();
		final int screen_size = Math.max(metrics.widthPixels, metrics.heightPixels);
		double applicationMemory = SystemUtils.getApplicationTotalMemory();

		if (applicationMemory >= Constants.APP_MEMORY_MEDIUM) {
			return Math.min(screen_size, 1280);
		}
		else if (applicationMemory >= Constants.APP_MEMORY_SMALL) {
			return Math.min(screen_size, 900);
		}
		else {
			return Math.min(screen_size, 700);
		}
	}
}
