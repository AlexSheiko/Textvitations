package com.aviary.android.feather.sdk.utils;

import android.content.Context;

import com.aviary.android.feather.cds.AviaryCds;
import com.aviary.android.feather.sdk.R;

import java.util.HashMap;

public class CdsUIUtils {

	static HashMap<AviaryCds.PackType, String> sPackTypeStringMap = new HashMap<AviaryCds.PackType, String>();

	/**
	 * Returns the localized string for the pack type
	 * @param context
	 * @param packType
	 * @return
	 */
	public static String getPackTypeString ( Context context, AviaryCds.PackType packType ) {

		if( sPackTypeStringMap.containsKey( packType ) ) {
			return sPackTypeStringMap.get( packType );
		}

		int res = - 1;
		switch( packType ) {
			case FRAME:
				res = R.string.feather_borders;
				break;

			case EFFECT:
				res = R.string.feather_effects;
				break;

			case STICKER:
				res = R.string.feather_stickers;
				break;
		}

		if( res > 0 ) {
			String result = context.getString( res );
			sPackTypeStringMap.put( packType, result );
			return result;
		}
		return "";
	}
}
