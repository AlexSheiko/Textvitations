package com.aviary.android.feather.sdk.overlays;

import android.content.Context;
import android.content.res.Resources;
import android.view.View;

import com.aviary.android.feather.library.filters.ToolLoaderFactory;
import com.aviary.android.feather.sdk.R;

/**
 * Created by alessandro on 26/03/14.
 */
public class FramesOverlay extends StickersOverlay {

	public FramesOverlay ( final Context context, final int styleId, final View view ) {
		super( context, styleId, view, ToolLoaderFactory.Tools.FRAMES, ID_FRAMES );
	}

	@Override
	protected CharSequence getTextRelativePosition ( final Resources res ) {
		return res.getString( R.string.aviary_overlay_stickers_text_position );
	}

	@Override
	protected float getTextWidthFraction ( final Resources res ) {
		return res.getFraction( R.fraction.aviary_overlay_stickers_text_width, 100, 100 );
	}

	@Override
	protected CharSequence getTitleText ( final Resources res ) {
		return res.getString( R.string.feather_borders );
	}

	@Override
	protected CharSequence getDetailText ( final Resources res ) {
		return res.getString( R.string.feather_overlay_stickers_text );
	}
}
