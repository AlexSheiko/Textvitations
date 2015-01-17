package com.aviary.android.feather.sdk.widget;

import android.annotation.SuppressLint;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.FloatMath;
import android.util.Log;

import java.util.ArrayList;

@SuppressLint ("FloatMath")
public class PointCloud {
    private static final String TAG                  = "PointCloud";
    private static final float  MIN_POINT_SIZE       = 2.0f;
    private static final float  MAX_POINT_SIZE       = 4.0f;
    private static final int    INNER_POINTS         = 8;
    private static final int    INNER_POINTS_ELLIPSE = 60;
    private static final float  PI                   = (float) Math.PI;
    private static float mEllipseOffsetX, mEllipseOffsetY;
    public WaveManager waveManager = new WaveManager();
    float mInnerMinor, mInnerMajor;
    boolean mIsHorizontalEllipse;
    float   mAxisRatio;
    private ArrayList<Point> mPointCloud1 = new ArrayList<Point>();
    private ArrayList<Point> mPointCloud2 = new ArrayList<Point>();
    private ArrayList<Point> mPointCloud3 = new ArrayList<Point>();
    private Drawable mDrawable;
    private float    mCenterX;
    private float mRotation = 0;
    private float mCenterY;
    private Paint mPaint;
    private float mScale = 1.0f;
    private float mOuterRadius;

    public PointCloud(Drawable drawable) {
        mPaint = new Paint();
        mPaint.setFilterBitmap(true);
        mPaint.setColor(Color.rgb(255, 255, 255)); // TODO: make configurable
        mPaint.setAntiAlias(true);
        mPaint.setDither(true);

        mDrawable = drawable;

        if (mDrawable != null) {
            drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        }
    }

    public void setCenter(float x, float y) {
        mCenterX = x;
        mCenterY = y;
    }

    public void setRotation(float angle) {
        mRotation = angle;
    }

    public void makeEllipseCloud(RectF innerBand, RectF outerBand) {

        float innerA = innerBand.width() / 2;
        float innerB = innerBand.height() / 2;
        double innerC = ellipseCircumference(innerA, innerB);

        if (innerA > innerB) {
            mInnerMinor = innerB;
            mInnerMajor = innerA;
            mIsHorizontalEllipse = true;
        } else {
            mInnerMinor = innerA;
            mInnerMajor = innerB;
            mIsHorizontalEllipse = false;
        }

        mAxisRatio = mInnerMinor / mInnerMajor;

        mOuterRadius = Math.max(outerBand.width() / 2, outerBand.height() / 2);
        mPointCloud3.clear();

        float distance = (float) innerC / INNER_POINTS_ELLIPSE;

        final float pointAreaRadius = outerBand.height() - innerBand.height();
        final int bands = Math.round(pointAreaRadius / distance);

        for (int band = 0; band < bands; band++) {

            float dr = band * distance;
            float a = innerA + dr;
            float b = innerB + dr;
            double c = ellipseCircumference(a, b);

            final int pointsIntBand = (int) (c / distance);
            float theta = (float) (Math.PI / 2.0f);
            float dTheta = (float) ((2.0f * Math.PI) / pointsIntBand);

            for (int i = 0; i < pointsIntBand; i++) {
                float x = a * FloatMath.cos(theta) + innerA;
                float y = b * FloatMath.sin(theta) + innerB;
                mPointCloud3.add(new Point(x, y, Math.max(a, b)));
                theta += dTheta;
            }

        }

    }

    public static double ellipseCircumference(double a, double b) {
        return Math.PI * (3 * (a + b) - Math.sqrt((3 * a + b) * (a + 3 * b)));
    }

    public void setEllipseOffset(float offX, float offY) {
        mEllipseOffsetX = offX;
        mEllipseOffsetY = offY;
    }

    public void makePointCloud(float innerRadius, float outerRadius, RectF rect) {

        if (innerRadius == 0) {
            Log.w(TAG, "Must specify an inner radius");
            return;
        }

        mOuterRadius = outerRadius;

        // radial
        mPointCloud1.clear();

        final float pointAreaRadius = (outerRadius - innerRadius);
        final float ds = (2.0f * PI * innerRadius / INNER_POINTS);
        final int bands = (int) Math.round(pointAreaRadius / ds);
        final float dr = pointAreaRadius / bands;
        float r = innerRadius;
        for (int b = 0; b <= bands; b++, r += dr) {
            float circumference = 2.0f * PI * r;
            final int pointsInBand = (int) (circumference / ds);
            float eta = PI / 2.0f;
            float dEta = 2.0f * PI / pointsInBand;
            for (int i = 0; i < pointsInBand; i++) {
                float x = r * FloatMath.cos(eta);
                float y = r * FloatMath.sin(eta);
                eta += dEta;
                mPointCloud1.add(new Point(x, y, r));
            }
        }

        // linear
        mPointCloud2.clear();
        r = innerRadius;
        final float rectSide = Math.max(rect.width(), rect.height());
        float circumference = rectSide;

        for (int b = 0; b <= bands; b++, r += dr) {
            final float pointSize = interp(MAX_POINT_SIZE, MIN_POINT_SIZE, r / mOuterRadius);
            final int pointsInBand = (int) (circumference / (ds * (pointSize / MIN_POINT_SIZE)));

            for (int i = 0; i <= pointsInBand; i++) {
                float x = r;
                float y = -circumference / 2 + (circumference / pointsInBand * i);
                mPointCloud2.add(new Point(x, y, r));
                mPointCloud2.add(new Point(-x, y, r));
            }

        }

    }

    private float interp(float min, float max, float f) {
        return min + (max - min) * f;
    }

    public float getScale() {
        return mScale;
    }

    public void setScale(float scale) {
        mScale = scale;
    }

    public void draw(Canvas canvas) {

        if (!(waveManager.getAlpha() > 0.0f)) {
            return;
        }

        WaveType type = waveManager.getType();

        int saveCount = canvas.save(Canvas.MATRIX_SAVE_FLAG);
        canvas.scale(mScale, mScale, mCenterX, mCenterY);

        if (type == WaveType.Line) {
            canvas.rotate(mRotation, mCenterX, mCenterY);
            ArrayList<Point> points = mPointCloud2;
            for (int i = 0; i < points.size(); i++) {
                Point point = points.get(i);
                final float pointSize = interp(MAX_POINT_SIZE, MIN_POINT_SIZE, point.radius / mOuterRadius);
                final float px = point.x + mCenterX;
                final float py = point.y + mCenterY;
                // Compute contribution from Wave
                int alpha = getAlphaForPoint(point.radius);

                if (alpha == 0) {
                    continue;
                }

                if (mDrawable != null) {
                    int count = canvas.save(Canvas.MATRIX_SAVE_FLAG);
                    final float cx = mDrawable.getIntrinsicWidth() * 0.5f;
                    final float cy = mDrawable.getIntrinsicHeight() * 0.5f;
                    final float s = pointSize / MAX_POINT_SIZE;
                    canvas.scale(s, s, px, py);
                    canvas.translate(px - cx, py - cy);
                    mDrawable.setAlpha(alpha);
                    mDrawable.draw(canvas);
                    canvas.restoreToCount(count);
                } else {
                    mPaint.setAlpha(alpha);
                    canvas.drawCircle(px, py, pointSize, mPaint);
                }
            }

        } else if (type == WaveType.Ellipse) {

            for (Point p : mPointCloud3) {

                if (mDrawable != null) {
                    int count = canvas.save(Canvas.MATRIX_SAVE_FLAG);
                    final float pointSize = interp(MAX_POINT_SIZE, MIN_POINT_SIZE, p.radius / mOuterRadius);
                    final float cx = mDrawable.getIntrinsicWidth() * 0.5f;
                    final float cy = mDrawable.getIntrinsicHeight() * 0.5f;
                    final float s = pointSize / MAX_POINT_SIZE;

                    float px = p.x;
                    float py = p.y;

                    canvas.scale(s, s, px + mEllipseOffsetX, py + mEllipseOffsetY);
                    canvas.translate(px - cx + mEllipseOffsetX, py - cy + mEllipseOffsetY);
                    int alpha = getAlphaForPoint(p.radius);

                    mDrawable.setAlpha(alpha);
                    mDrawable.draw(canvas);
                    canvas.restoreToCount(count);

                }
            }

        } else {

            ArrayList<Point> points = mPointCloud1;
            for (int i = 0; i < points.size(); i++) {
                Point point = points.get(i);
                final float pointSize = interp(MAX_POINT_SIZE, MIN_POINT_SIZE, point.radius / mOuterRadius);
                final float px = point.x + mCenterX;
                final float py = point.y + mCenterY;

                int alpha = getAlphaForPoint(hypot(point.x, point.y));

                if (alpha == 0) {
                    continue;
                }

                if (mDrawable != null) {
                    int count = canvas.save(Canvas.MATRIX_SAVE_FLAG);
                    final float cx = mDrawable.getIntrinsicWidth() * 0.5f;
                    final float cy = mDrawable.getIntrinsicHeight() * 0.5f;
                    final float s = pointSize / MAX_POINT_SIZE;
                    canvas.scale(s, s, px, py);
                    canvas.translate(px - cx, py - cy);
                    mDrawable.setAlpha(alpha);
                    mDrawable.draw(canvas);
                    canvas.restoreToCount(count);
                } else {
                    mPaint.setAlpha(alpha);
                    canvas.drawCircle(px, py, pointSize, mPaint);
                }
            }
        }
        canvas.restoreToCount(saveCount);
    }

    @SuppressWarnings ("checkstyle:magicnumber")
    public int getAlphaForPoint(float radius) {
        float distanceToWaveRing = (radius - waveManager.radius);
        float waveAlpha = 0.0f;

        if (distanceToWaveRing > 0.0f) {
            // outside
            if (distanceToWaveRing < waveManager.width * 0.5f) {
                float cosf = FloatMath.cos(PI * 0.25f * distanceToWaveRing / (waveManager.width * 0.5f));
                waveAlpha = waveManager.alpha * max(0.0f, (float) Math.pow(cosf, 20.0f));
            }

        } else {
            // inside
            if (distanceToWaveRing > -(waveManager.width * 0.5f)) {
                float cosf = FloatMath.cos(PI * 0.25f * distanceToWaveRing / (waveManager.width * 0.5f));
                waveAlpha = waveManager.alpha * max(0.0f, (float) Math.pow(cosf, 20.0f));
            }
        }

        return (int) (waveAlpha * 255);
    }

    private static float hypot(float x, float y) {
        return FloatMath.sqrt(x * x + y * y);
    }

    private static float max(float a, float b) {
        return a > b ? a : b;
    }

    enum WaveType {
        Circle, Line, Ellipse
    }

    public static class WaveManager {
        public static final float    DEFAULT_WIDTH = 200.0f;
        private             float    radius        = 50;
        private             float    width         = DEFAULT_WIDTH; // TODO: Make configurable
        private             float    alpha         = 0.0f;
        private             WaveType type          = WaveType.Circle;

        public WaveType getType() {
            return type;
        }

        public void setType(WaveType t) {
            type = t;
        }

        public float getRadius() {
            return radius;
        }

        public void setRadius(float r) {
            radius = r;
        }

        public float getAlpha() {
            return alpha;
        }

        public void setAlpha(float a) {
            alpha = a;
        }

    }

    static class Point {
        float x;
        float y;
        float radius;

        public Point(float x2, float y2, float r) {
            x = (float) x2;
            y = (float) y2;
            radius = r;
        }

    }

}
