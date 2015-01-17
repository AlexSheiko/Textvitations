package com.aviary.android.feather.sdk.overlays;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.text.DynamicLayout;
import android.text.Html;
import android.text.Layout;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.style.TextAppearanceSpan;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.aviary.android.feather.common.log.LoggerFactory;
import com.aviary.android.feather.common.tracking.AviaryTracker;
import com.aviary.android.feather.library.services.IAviaryController;
import com.aviary.android.feather.sdk.R;
import com.nineoldandroids.animation.Animator;
import com.nineoldandroids.animation.AnimatorSet;
import com.nineoldandroids.animation.ObjectAnimator;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AviaryOverlay extends RelativeLayout implements View.OnClickListener {
    public interface OnCloseListener {
        void onClose(AviaryOverlay overlay);
    }

    protected static final boolean      USE_CIRCLE                 = false;
    protected static final boolean      DEBUG_PAINT                = false;
    public static final    int          ID_NONE                    = -1;
    public static final    int          ID_STICKERS                = 1;
    public static final    int          ID_BLEMISH                 = 2;
    public static final    int          ID_FRAMES                  = 3;
    public static final    int          ID_BLEMISH_CLOSE           = 4;
    public static final    int          ID_UNDO_REDO               = 5;
    public static final    int          ID_CANT_UNDO_ANYMORE       = 6;
    public static final    int          ID_OVERLAYS                = 7;
    public static final    int          ID_OVERLAYS_PINCH          = 8;
    protected static final CharSequence POSITION_LEFT              = "left";
    protected static final CharSequence POSITION_CENTER            = "center";
    protected static final CharSequence POSITION_RIGHT             = "right";
    protected static final String       TAG_CLOSE_FROM_BUTTON      = "button";
    protected static final String       TAG_CLOSE_FROM_BACKGROUND  = "background";
    protected static final String       TAG_CLOSE_FROM_BACK_BUTTON = "back";
    private static final   String       PREFERENCE_NAME            = "aviary-overlays";
    private static final   String       PREFERENCE_KEY             = "overlay-";
    protected       Paint          debugPaint;
    private final   DisplayMetrics mMetrics;
    private final   int            mOriginalBackgroundColor;
    private final   int            mBackgroundAlpha;
    protected final int            mClosebuttonMargins;
    private         int            mBackgroundColor;
    private final   int            mTitleStyle;
    private final   int            mTextStyle;
    private final   int            mArrow;
    private final   int            mTitleMargins;
    private final   int            mTextMargins;
    private final   int            mRipple;
    private final   int            mAnimationDuration;
    private final   int            mActivationDelay;
    private final   String         mTagName;
    private float alpha = 0f;
    private         Animator             hideAnimation;
    private         Animator             showAnimation;
    private         boolean              mVisible;
    private         boolean              mActive;
    private         int                  overlayId;
    private         boolean              inLayout;
    private         Button               mCloseButton;
    protected final LoggerFactory.Logger logger;
    protected       OnCloseListener      mCloseListener;
    private static final Object M_LOCK = new Object();
    private static SharedPreferences mPreferences;
    protected       boolean       mOrientationChanged;

    static SharedPreferences getSharedPreferences(Context context) {
        synchronized (M_LOCK) {
            if (null == mPreferences) {
                mPreferences = context.getSharedPreferences(PREFERENCE_NAME, 0);
            }
        }
        return mPreferences;
    }

    public AviaryOverlay(@NotNull Context context, @NotNull String toolName, int styleId, int overlayId) {
        super(context);

        logger = LoggerFactory.getLogger(getClass().getSimpleName(), LoggerFactory.LoggerType.ConsoleLoggerType);

        final Resources res = context.getResources();

        this.overlayId = overlayId;
        this.mTagName = toolName;

        Resources.Theme theme = context.getTheme();
        TypedArray array = theme.obtainStyledAttributes(styleId, R.styleable.AviaryOverlay);

        this.mOriginalBackgroundColor = array.getColor(R.styleable.AviaryOverlay_android_background, Color.BLACK);
        this.mTitleStyle = array.getResourceId(R.styleable.AviaryOverlay_aviary_titleStyle, android.R.style.TextAppearance_Large);
        this.mTextStyle = array.getResourceId(R.styleable.AviaryOverlay_aviary_textStyle, android.R.style.TextAppearance_Medium);
        this.mArrow = array.getResourceId(R.styleable.AviaryOverlay_aviary_arrow, R.drawable.aviary_overlay_blemish_arrow);
        this.mRipple = array.getResourceId(R.styleable.AviaryOverlay_aviary_ripple, R.drawable.aviary_overlay_ripple);
        this.mTextMargins = array.getDimensionPixelSize(R.styleable.AviaryOverlay_aviary_textMargins, 0);
        this.mTitleMargins = array.getDimensionPixelSize(R.styleable.AviaryOverlay_aviary_titleMargins, 0);
        this.mAnimationDuration = array.getInt(R.styleable.AviaryOverlay_android_animationDuration, 250);
        this.mClosebuttonMargins = array.getDimensionPixelSize(R.styleable.AviaryOverlay_aviary_closeButtonMargins, 0);
        this.mActivationDelay = array.getInteger(R.styleable.AviaryOverlay_aviary_activationDelay, 0);

        array.recycle();

        this.mBackgroundColor = mOriginalBackgroundColor;
        this.mBackgroundAlpha = Color.alpha(mOriginalBackgroundColor);
        this.mMetrics = res.getDisplayMetrics();

        logger.log("background color: 0x%s", Integer.toHexString(mBackgroundColor));
        logger.log("background alpha: %d", mBackgroundAlpha);

        if (DEBUG_PAINT) {
            debugPaint = new Paint();
            debugPaint.setAntiAlias(true);
            debugPaint.setColor(Color.RED);
        }

        setTag(getClass().getName());
        setVisibility(INVISIBLE);
        setHardwareAccelerated(true);
    }

    public void setOnCloseListener(OnCloseListener listener) {
        mCloseListener = listener;
    }

    protected void addCloseButton(int... rules) {
        if (null == mCloseButton) {
            mCloseButton = (Button) LayoutInflater.from(getContext()).inflate(R.layout.aviary_overlay_close_button, this, false);
            mCloseButton.setOnClickListener(this);
            LayoutParams params = (LayoutParams) generateDefaultLayoutParams();
            for (int rule : rules) {
                params.addRule(rule);
            }
            params.setMargins(mClosebuttonMargins, mClosebuttonMargins, mClosebuttonMargins, mClosebuttonMargins);
            mCloseButton.setLayoutParams(params);
            mCloseButton.setVisibility(View.GONE);
            addView(mCloseButton);
        }
    }

    public Button getCloseButton() {
        return mCloseButton;
    }

    protected final String getToolName() {
        return mTagName;
    }

    protected final int getOverlayId() {
        return overlayId;
    }

    @Override
    public void setAlpha(final float alpha) {
        this.alpha = alpha;
        this.mBackgroundColor = Color.argb((int) (alpha * mBackgroundAlpha), 0, 0, 0);
        postInvalidate();
    }

    @Override
    public float getAlpha() {
        return alpha;
    }

    public int getAnimationDuration() {
        return mAnimationDuration;
    }

    public int getTitleMargins() {
        return mTitleMargins;
    }

    public int getTextMargins() {
        return mTextMargins;
    }

    Drawable generateTitleDrawable(Context context, CharSequence text, int width, Layout.Alignment align) {
        DynamicLayout layout = generateTitleLayout(text, width, align);
        Bitmap bitmap = generateBitmap(layout);
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    Drawable generateTextDrawable(Context context, CharSequence text, int width, Layout.Alignment align) {
        DynamicLayout layout = generateTextLayout(text, width, align);
        Bitmap bitmap = generateBitmap(layout);
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    Drawable generateHTMLTextDrawable(Context context, CharSequence text, int width, Layout.Alignment align) {
        DynamicLayout layout = generateHTMLTextLayout(text, width, align);
        Bitmap bitmap = generateBitmap(layout);
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    Bitmap generateBitmap(DynamicLayout layout) {

        Bitmap bitmap = Bitmap.createBitmap(layout.getWidth(), layout.getHeight(), Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(0);

        Canvas canvas = new Canvas(bitmap);
        layout.draw(canvas);
        return bitmap;
    }

    DynamicLayout generateTitleLayout(CharSequence text, int width, Layout.Alignment align) {
        return createTextLayout(text, width, align, getTitleStyle());
    }

    DynamicLayout generateTextLayout(CharSequence text, int width, Layout.Alignment align) {
        return createTextLayout(text, width, align, getTextStyle());
    }

    DynamicLayout generateHTMLTextLayout(CharSequence text, int width, Layout.Alignment align) {
        return createHTMLTextLayout(text, width, align, getTextStyle());
    }

    Drawable generateArrow() {
        return getContext().getResources().getDrawable(mArrow);
    }

    Drawable generateRipple() {
        return getContext().getResources().getDrawable(mRipple);
    }

    private DynamicLayout createTextLayout(CharSequence text, int width, Layout.Alignment align, int style) {
        TextPaint titlePaint = new TextPaint();
        titlePaint.setAntiAlias(true);
        titlePaint.setLinearText(true);
        titlePaint.setFilterBitmap(true);

        TextAppearanceSpan apperance = new TextAppearanceSpan(getContext(), style);
        SpannableString string = new SpannableString(text);
        string.setSpan(apperance, 0, string.length(), 0);

        return new DynamicLayout(string, titlePaint, width, align, 1.0f, 1.0f, true);
    }

    private DynamicLayout createHTMLTextLayout(CharSequence text, int width, Layout.Alignment align, int style) {
        TextPaint titlePaint = new TextPaint();
        titlePaint.setAntiAlias(true);
        titlePaint.setLinearText(true);
        titlePaint.setFilterBitmap(true);

        TextAppearanceSpan apperance = new TextAppearanceSpan(getContext(), style);
        SpannableStringBuilder spanned = (SpannableStringBuilder) Html.fromHtml((String) text);
        spanned.setSpan(apperance, 0, spanned.length(), 0);

        return new DynamicLayout(spanned, titlePaint, width, align, 1.0f, 1.0f, true);
    }

    DisplayMetrics getDisplayMetrics() {
        return mMetrics;
    }

    public int getBackgroundColor() {
        return mBackgroundColor;
    }

    public int getTitleStyle() {
        return mTitleStyle;
    }

    public int getTextStyle() {
        return mTextStyle;
    }

    int[] getViewLocation(int id) {
        FrameLayout decorView = (FrameLayout) ((Activity) (getContext())).getWindow().getDecorView();
        View view = decorView.findViewById(id);
        if (view == null) {
            return null;
        }
        int[] viewCoords = new int[2];
        view.getLocationInWindow(viewCoords);
        return viewCoords;
    }

    View getViewOfInterest(int id) {
        FrameLayout decorView = (FrameLayout) ((Activity) (getContext())).getWindow().getDecorView();
        return decorView.findViewById(id);
    }

    int[] getViewLocation(View view) {
        int[] viewCoords = new int[2];
        view.getLocationInWindow(viewCoords);
        return viewCoords;
    }

    @Override
    public abstract boolean onTouchEvent(MotionEvent event);

    public void setHardwareAccelerated(boolean accelerated) {
        if (accelerated) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                if (isHardwareAccelerated()) {
                    Paint hardwarePaint = new Paint();
                    hardwarePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.OVERLAY));
                    setLayerType(LAYER_TYPE_HARDWARE, hardwarePaint);
                } else {
                    setLayerType(LAYER_TYPE_SOFTWARE, null);
                }
            } else {
                setDrawingCacheEnabled(true);
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                setLayerType(LAYER_TYPE_SOFTWARE, null);
            } else {
                setDrawingCacheEnabled(true);
            }
        }
    }

    protected void trackTutorialOpened() {
        if (null != getContext()) {
            AviaryTracker.getInstance(getContext()).tagEvent(mTagName + ": tutorial_presented");
        }
    }

    protected void trackTutorialClosed(final String from) {
        if (null != getContext() && null != from) {
            final AviaryTracker tracker = AviaryTracker.getInstance(getContext());
            tracker.tagEvent(mTagName + ": tutorial_closed", "from", from);
        }
    }

    protected abstract void calculatePositions();

    protected abstract void doShow();

    protected abstract void inAnimationCompleted();

    protected void onActivate() {
        mActive = true;
        // empty
    }

    private void postActivate() {
        if (mActivationDelay > 0) {
            Handler handler = getHandler();
            if (null != handler) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        onActivate();
                    }
                }, mActivationDelay);
            }
        } else {
            onActivate();
        }
    }

    protected boolean isVisible() {
        return mVisible;
    }

    protected boolean isActive() {
        return mActive;
    }

    private boolean shouldShow() {
        if (null == getContext()) {
            return false;
        }
        final SharedPreferences prefs = getSharedPreferences(getContext());
        if (shouldShow(prefs, overlayId)) {
            markAsViewed(prefs, overlayId);
            return true;
        }
        return false;
    }

    /**
     * This will mark the registered overlayId as already viewed
     *
     * @param overlayId
     */
    public static void markAsViewed(final Context context, final int overlayId) {
        final SharedPreferences prefs = getSharedPreferences(context);
        markAsViewed(prefs, overlayId);
    }

    public static void markAsViewed(final IAviaryController context, final int overlayId) {
        if (null != context && null != context.getBaseContext()) {
            markAsViewed(context.getBaseContext(), overlayId);
        }
    }

    private static void markAsViewed(SharedPreferences preferences, final int overlayId) {
        if (overlayId < 0) {
            return;
        }

        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(PREFERENCE_KEY + overlayId, 1);
        editor.apply();
    }

    public static boolean shouldShow(@Nullable IAviaryController context, final int overlayId) {
        if (null != context && null != context.getBaseContext()) {
            return shouldShow(context.getBaseContext(), overlayId);
        }
        return false;
    }

    public static boolean shouldShow(@NotNull Context context, final int overlayId) {
        final SharedPreferences preferences = getSharedPreferences(context);
        return shouldShow(preferences, overlayId);
    }

    private static boolean shouldShow(@NotNull SharedPreferences preferences, final int overlayId) {
        if (overlayId > -1) {
            return !preferences.contains(PREFERENCE_KEY + overlayId);
        }
        return true;
    }

    public final boolean show() {
        return showDelayed(100);
    }

    public static AviaryOverlay findOverlay(@NotNull Context context, Class<? extends AviaryOverlay>... classes) {
        ViewGroup decor = (ViewGroup) ((Activity) context).getWindow().getDecorView();
        if (null != decor) {
            for (Class cls : classes) {
                AviaryOverlay overlay = (AviaryOverlay) decor.findViewWithTag(cls.getName());
                if (null != overlay) {
                    return overlay;
                }
            }
        }
        return null;
    }

    public boolean showDelayed(long delay) {
        logger.info("show");
        ViewGroup decor = (ViewGroup) ((Activity) getContext()).getWindow().getDecorView();

        if (null != decor && null != decor.getHandler()) {
            if (shouldShow()) {
                inLayout = true;
                decor.addView(this,
                              new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                decor.getHandler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        calculatePositions();
                        trackTutorialOpened();
                        doShow();
                    }
                }, delay);
                return true;
            } else {
                logger.log("don't show");
            }
        } else {
            logger.warn("handler is null");
        }
        return false;
    }

    public void hide() {
        hide(null);
    }

    public void hide(final String from) {
        if (!isAttachedToParent()) {
            return;
        }
        logger.info("hide");
        fadeOut(from);
    }

    public void dismiss() {
        inLayout = false;
        if (null != getParent()) {
            logger.info("dismiss");
            try {
                ((ViewGroup) getParent()).removeView(this);
            } catch (IndexOutOfBoundsException e) {
                e.printStackTrace();
            }
        }
    }

    protected boolean isAttachedToParent() {
        return inLayout && null != getParent();
    }

    protected Animator generateInAnimation() {
        Animator animator = new AnimatorSet();
        ((AnimatorSet) animator).playTogether(ObjectAnimator.ofFloat(this, "alpha", 0f, 1f));
        animator.setDuration(getAnimationDuration());
        return animator;
    }

    protected Animator generateHideAnimation() {
        Animator animator = ObjectAnimator.ofFloat(this, "alpha", 1f, 0f);
        animator.setDuration(getAnimationDuration());
        return animator;
    }

    protected void fadeOut(final String from) {
        if (null == hideAnimation) {
            logger.info("fadeOut");
            hideAnimation = generateHideAnimation();
            hideAnimation.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(final Animator animation) {
                }

                @Override
                public void onAnimationEnd(final Animator animation) {
                    if (null != from) {
                        trackTutorialClosed(from);
                    }
                    dismiss();
                }

                @Override
                public void onAnimationCancel(final Animator animation) {
                }

                @Override
                public void onAnimationRepeat(final Animator animation) {
                }
            });
            hideAnimation.start();
        }
    }

    protected void fadeIn() {
        if (null == showAnimation) {
            logger.info("fadeIn");
            showAnimation = generateInAnimation();
            showAnimation.addListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(final Animator animation) {
                    setVisibility(View.VISIBLE);
                }

                @Override
                public void onAnimationEnd(final Animator animation) {
                    mVisible = true;
                    inAnimationCompleted();
                    postActivate();
                }

                @Override
                public void onAnimationCancel(final Animator animation) {

                }

                @Override
                public void onAnimationRepeat(final Animator animation) {

                }
            });
            showAnimation.start();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setAlpha(1);
        mOrientationChanged = true;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (mOrientationChanged && changed) {
            calculatePositions();
            invalidate();
            mOrientationChanged = false;
        }
    }

    public static Bitmap getBitmapFromView(View view) {
        Bitmap returnedBitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(returnedBitmap);
        Drawable bgDrawable = view.getBackground();
        if (bgDrawable != null) {
            bgDrawable.draw(canvas);
        } else {
            canvas.drawColor(Color.WHITE);
        }
        view.draw(canvas);
        return returnedBitmap;
    }

    public boolean onBackPressed() {
        if (isAttachedToParent()) {
            logger.info("onBackPressed");
            hide(TAG_CLOSE_FROM_BACK_BUTTON);
            return true;
        }
        return false;
    }

    @Override
    public void onClick(final View view) {
        logger.info("onClick: " + view);

        if (view == getCloseButton()) {
            hide(TAG_CLOSE_FROM_BUTTON);
        }
    }
}