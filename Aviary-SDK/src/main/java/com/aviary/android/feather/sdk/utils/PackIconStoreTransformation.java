package com.aviary.android.feather.sdk.utils;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.DisplayMetrics;

import com.aviary.android.feather.cds.AviaryCds;
import com.aviary.android.feather.library.utils.BitmapUtils;

import java.lang.ref.SoftReference;

import it.sephiroth.android.library.picasso.Transformation;

public class PackIconStoreTransformation implements Transformation {
    public static class Builder {
        Resources resources;
        String    identifier;
        String    packType;

        public Builder() { }

        public Builder withResources(Resources resources) {
            this.resources = resources;
            return this;
        }

        public Builder withPackType(String packType) {
            this.packType = packType;
            return this;
        }

        public Builder withIdentifier(String identifier) {
            this.identifier = identifier;
            return this;
        }

        public PackIconStoreTransformation build() throws IllegalArgumentException {
            PackIconStoreTransformation instance = new PackIconStoreTransformation();

            if (null == packType) {
                throw new IllegalArgumentException("packType cannot be null");
            }
            if (null == resources) {
                throw new IllegalArgumentException("resources cannot be null");
            }

            instance.identifier = identifier;
            instance.packType = packType;
            instance.resourcesRef = new SoftReference<Resources>(resources);
            return instance;
        }
    }

    SoftReference<Resources> resourcesRef;
    private String identifier;
    private String packType;
    private float  strokeSize;
    private float  shadowOffset;
    private int shadowColor = 0x55000000;
    private float ellipseSize;
    private int strokeColor     = Color.WHITE;
    private int backgroundColor = 0xff8d8f93;

    PackIconStoreTransformation() { }

    @Override
    public String key() {
        return this.getClass().getSimpleName() + "_" + identifier + "_" + packType + "_";
    }

    private float dp2px(DisplayMetrics metrics, int value) {
        return metrics.density * value;
    }

    Bitmap generate(Resources res, Bitmap icon) {
        Bitmap result = null;

        if (res == null) {
            return icon;
        }
        if (icon == null) {
            return icon;
        }

        DisplayMetrics metrics = res.getDisplayMetrics();
        strokeSize = dp2px(metrics, 4);
        shadowOffset = dp2px(metrics, 1);
        ellipseSize = dp2px(metrics, 3);

        if (AviaryCds.PACKTYPE_EFFECT.equals(packType)) {
            result = BitmapUtils.circleMask(icon, icon.getWidth() / 2);

            Bitmap bitmap2 = Bitmap.createBitmap(result.getWidth(), result.getHeight(), result.getConfig());
            Canvas canvas = new Canvas(bitmap2);

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(shadowColor);

            int radius = bitmap2.getWidth() / 2;

            canvas.drawCircle(bitmap2.getWidth() / 2, bitmap2.getHeight() / 2, radius, paint);

            paint.setColor(Color.WHITE);

            canvas.drawCircle(bitmap2.getWidth() / 2, bitmap2.getHeight() / 2, radius - shadowOffset / 2, paint);

            RectF dstRect = new RectF(strokeSize + shadowOffset,
                                      strokeSize + shadowOffset,
                                      bitmap2.getWidth() - (strokeSize + shadowOffset),
                                      bitmap2.getHeight() - (strokeSize + shadowOffset));
            canvas.drawBitmap(result, null, dstRect, paint);

            result.recycle();
            result = bitmap2;
        } else if (AviaryCds.PACKTYPE_STICKER.equals(packType)) {

            result = BitmapUtils.circleMask(icon, icon.getWidth() / 2);

            Bitmap bitmap2 = Bitmap.createBitmap(result.getWidth(), result.getHeight(), result.getConfig());
            Canvas canvas = new Canvas(bitmap2);

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(shadowColor);

            int radius = bitmap2.getWidth() / 2;

            canvas.drawCircle(bitmap2.getWidth() / 2, bitmap2.getHeight() / 2, radius, paint);

            paint.setColor(Color.WHITE);

            canvas.drawCircle(bitmap2.getWidth() / 2, bitmap2.getHeight() / 2, radius - shadowOffset / 2, paint);

            RectF dstRect = new RectF(strokeSize + shadowOffset,
                                      strokeSize + shadowOffset,
                                      bitmap2.getWidth() - (strokeSize + shadowOffset),
                                      bitmap2.getHeight() - (strokeSize + shadowOffset));

            paint.setColor(backgroundColor);
            canvas.drawCircle(bitmap2.getWidth() / 2, bitmap2.getHeight() / 2, radius - (shadowOffset + strokeSize) / 2, paint);

            canvas.drawBitmap(result, null, dstRect, paint);

            result.recycle();
            result = bitmap2;
        } else if (AviaryCds.PACKTYPE_FRAME.equals(packType) || AviaryCds.PACKTYPE_OVERLAY.equals(packType)) {

            result = Bitmap.createBitmap(icon.getWidth(), icon.getHeight(), icon.getConfig());
            Canvas canvas = new Canvas(result);

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(shadowColor);

            RectF roundRect = new RectF(0, 0, result.getWidth(), result.getHeight());
            canvas.drawRoundRect(roundRect, 12, 12, paint);

            paint.setColor(strokeColor);
            roundRect.set(1, 1, result.getWidth() - shadowOffset, result.getHeight() - shadowOffset);
            canvas.drawRoundRect(roundRect, ellipseSize, ellipseSize, paint);

            RectF dstRect = new RectF(1 + strokeSize,
                                      1 + strokeSize,
                                      result.getWidth() - (strokeSize + shadowOffset),
                                      result.getHeight() - (strokeSize + shadowOffset));
            canvas.drawBitmap(icon, null, dstRect, paint);
        }

        if (null != result && !result.equals(icon)) {
            icon.recycle();
        }

        return result;
    }

    @Override
    public Bitmap transform(Bitmap bitmap) {
        final Resources resources = resourcesRef.get();

        if (null == resources) {
            return null;
        }

        // packtype
        if (null != bitmap) {
            Bitmap result = generate(resources, bitmap);

            if (null != result && result != bitmap) {
                bitmap.recycle();
                bitmap = result;
            }
        }

        return bitmap;
    }
}
