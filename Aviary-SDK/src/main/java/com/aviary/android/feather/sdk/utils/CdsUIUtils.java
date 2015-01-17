package com.aviary.android.feather.sdk.utils;

import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;

import com.aviary.android.feather.cds.AviaryCds;
import com.aviary.android.feather.cds.PacksColumns;
import com.aviary.android.feather.cds.PacksItemsColumns;
import com.aviary.android.feather.common.utils.PackageManagerUtils;
import com.aviary.android.feather.sdk.R;

import java.io.File;
import java.util.HashMap;

public final class CdsUIUtils {
    static HashMap<AviaryCds.PackType, String> sPackTypeStringMap = new HashMap<AviaryCds.PackType, String>();

    private CdsUIUtils() { }

    /**
     * Returns the localized string for the pack type
     *
     * @param context
     * @param packType
     * @return
     */
    public static String getPackTypeString(Context context, AviaryCds.PackType packType) {

        if (sPackTypeStringMap.containsKey(packType)) {
            return sPackTypeStringMap.get(packType);
        }

        int res = -1;
        switch (packType) {
            case FRAME:
                res = R.string.feather_borders;
                break;

            case EFFECT:
                res = R.string.feather_effects;
                break;

            case STICKER:
                res = R.string.feather_stickers;
                break;

            case OVERLAY:
                res = R.string.feather_overlays;
                break;

            default:
                /* invalid case */
                break;
        }

        if (res > 0) {
            String result = context.getString(res);
            sPackTypeStringMap.put(packType, result);
            return result;
        }
        return "";
    }

    public static Cursor computePreviewAspectRatio(
        Context context, long packId, String packType, String packPreviewPath, double[] outAspectRatio) {
        if (context == null) {
            return null;
        }
        if (null == outAspectRatio || outAspectRatio.length < 1) {
            return null;
        }

        double previewAspectRatio = 1;

        Cursor cursor = context.getContentResolver()
            .query(PackageManagerUtils.getCDSProviderContentUri(context, "pack/" + packId + "/item/list"), new String[]{
                PacksItemsColumns._ID + " as _id", PacksColumns.PACK_TYPE, PacksItemsColumns._ID, PacksItemsColumns.IDENTIFIER,
                PacksItemsColumns.DISPLAY_NAME
            }, null, null, null);

        if (cursor != null && cursor.moveToFirst()) {
            String identifier = cursor.getString(cursor.getColumnIndex(PacksItemsColumns.IDENTIFIER));
            File file = new File(packPreviewPath, identifier + (AviaryCds.getPreviewItemExt(packType)));
            final String path = file.getAbsolutePath();

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, options);

            if (options.outHeight == 0 || options.outWidth == 0) {
                previewAspectRatio = 1;
            } else {
                previewAspectRatio = (double) options.outWidth / (double) options.outHeight;
            }
        }

        outAspectRatio[0] = previewAspectRatio;
        return cursor;
    }
}
