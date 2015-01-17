package com.aviary.android.feather.sdk.utils;

import android.content.res.AssetManager;
import android.graphics.Typeface;

import java.lang.ref.SoftReference;
import java.util.HashMap;

public final class TypefaceUtils {
    private static final HashMap<String, SoftReference<Typeface>> S_TYPE_CACHE = new HashMap<String, SoftReference<Typeface>>();

    private TypefaceUtils() { }

    public static Typeface createFromAsset(final AssetManager assets, final String fontname) {
        Typeface result = null;
        SoftReference<Typeface> cachedFont = getFromCache(fontname);

        if (null != cachedFont && cachedFont.get() != null) {
            result = cachedFont.get();
        } else {
            result = Typeface.createFromAsset(assets, fontname);
            putIntoCache(fontname, result);
        }

        return result;
    }

    private static SoftReference<Typeface> getFromCache(final String fontname) {
        synchronized (S_TYPE_CACHE) {
            return S_TYPE_CACHE.get(fontname);
        }
    }

    private static void putIntoCache(final String fontname, final Typeface font) {
        synchronized (S_TYPE_CACHE) {
            S_TYPE_CACHE.put(fontname, new SoftReference<Typeface>(font));
        }
    }
}
