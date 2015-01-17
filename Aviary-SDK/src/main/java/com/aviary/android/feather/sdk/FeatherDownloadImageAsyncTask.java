package com.aviary.android.feather.sdk;

import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import com.aviary.android.feather.cds.AviaryCds;
import com.aviary.android.feather.cds.CdsUtils;
import com.aviary.android.feather.common.utils.os.AviaryAsyncTask;
import com.aviary.android.feather.library.content.ToolEntry;
import com.aviary.android.feather.library.utils.DecodeUtils;
import com.aviary.android.feather.library.utils.ImageInfo;
import com.aviary.android.feather.sdk.async_tasks.DownloadImageAsyncTask;

import java.util.List;

/**
 * Load an Image bitmap asynchronous.
 *
 * @author alessandro
 */
class FeatherDownloadImageAsyncTask extends AviaryAsyncTask<FeatherActivity, FeatherDownloadImageAsyncTask.ToolLoadResult, Bitmap> {

	private static final String TAG = "FeatherDownloadImageAsyncTask";

	static class ToolLoadResult {
		public List<String> tools;
		public List<ToolEntry> entries;
		public boolean whiteLabel;
	}

	/**
	 * The listener interface for receiving onImageDownload events. The class that is
	 * interested in processing a onImageDownload
	 * event implements this interface, and the object created with that class is
	 * registered with a component using the component's
	 * <code>addOnImageDownloadListener<code> method. When
	 * the onImageDownload event occurs, that object's appropriate
	 * method is invoked.
	 */
	public static interface OnImageDownloadListener extends DownloadImageAsyncTask.OnImageDownloadListener {
		/**
		 * Finished loading the list of available tools, this method is called
		 * in the UI thread
		 *
		 * @param toolNames  list of available tools
		 * @param entries    list of corresponding entries
		 * @param whiteLabel white-label is enabled
		 */
		void onToolsLoaded(List<String> toolNames, List<ToolEntry> entries, boolean whiteLabel);
	}

	;

	private OnImageDownloadListener mListener;
	private Uri mUri;
	private String error;
	private ImageInfo mImageOutputInfo;
	private int mMaxSize;
	private final List<String> mToolList;
	private final boolean mLoadTools;

	/**
	 * Instantiates a new download image async task.
	 *
	 * @param uri       the image uri
	 * @param loadTools load/skip tool loading
	 * @param toolList
	 */
	public FeatherDownloadImageAsyncTask(Uri uri, int maxSize, final boolean loadTools, final List<String> toolList) {
		super();
		mUri = uri;
		mMaxSize = maxSize;
		mLoadTools = loadTools;
		mToolList = toolList;
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
		mImageOutputInfo = new ImageInfo();
	}

	@Override
	protected Bitmap doInBackground(FeatherActivity... params) {
		Log.i(TAG, "doInBackground: current thread #" + Thread.currentThread().getId());

		FeatherActivity activity = params[0];

		if (mLoadTools) {
			final Pair<List<String>, List<ToolEntry>> tools = activity.loadTools(mToolList);
			final List<String> permissions = CdsUtils.getPermissions(activity);

			ToolLoadResult result = new ToolLoadResult();
			result.tools = tools.first;
			result.entries = tools.second;
			result.whiteLabel = permissions != null && permissions.contains(AviaryCds.Permission.whitelabel.name());
			publishProgress(result);
		}
		else {
			Log.d(TAG, "skip tool load");
		}

		int max_size = - 1;

		if (mMaxSize > 0) {
			max_size = mMaxSize;
		}

		if (max_size <= 0) {
			max_size = DownloadImageAsyncTask.getManagedMaxImageSize(activity);
		}

		try {
			return DecodeUtils.decode(activity, mUri, max_size, max_size, mImageOutputInfo);
		} catch (Exception e) {
			Log.e(TAG, "decode error", e);
			error = e.getMessage();
		}

		return null;
	}

	@Override
	protected void ProgressUpdate(final ToolLoadResult... values) {
		if (null != values && values.length > 0 && null != mListener) {
			mListener.onToolsLoaded(values[0].tools, values[0].entries, values[0].whiteLabel);
		}
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
}
