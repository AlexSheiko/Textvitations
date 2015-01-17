package com.aviary.android.feather.sdk.panels;

import com.aviary.android.feather.common.log.LoggerFactory;
import com.aviary.android.feather.common.log.LoggerFactory.Logger;
import com.aviary.android.feather.common.log.LoggerFactory.LoggerType;
import com.aviary.android.feather.library.content.ToolEntry;
import com.aviary.android.feather.library.filters.ToolLoaderFactory;
import com.aviary.android.feather.library.services.BaseContextService;
import com.aviary.android.feather.library.services.IAviaryController;
import com.aviary.android.feather.sdk.R;

/**
 * This class is the delegate class for creating the appropriate tool panel
 * for the given tool name
 */
public class AbstractPanelLoaderService extends BaseContextService {
    public AbstractPanelLoaderService(IAviaryController context) {
        super(context);
    }

    /**
     * Passing a {@link ToolEntry} return an instance of {@link AbstractPanel} used to
     * create the requested tool.
     *
     * @param entry
     * @return
     */
    // CHECKSTYLE.OFF: CyclomaticComplexity
    public AbstractPanel createNew(ToolEntry entry) {

        AbstractPanel panel = null;
        final IAviaryController context = getContext();

        switch (entry.name) {
            case ORIENTATION:
                panel = new AdjustEffectPanel(context, entry, ToolLoaderFactory.Tools.ORIENTATION);
                break;

            case LIGHTING:
                panel = new ConsolidatedAdjustToolsPanel(context, entry);
                break;

            case COLOR:
                panel = new ConsolidatedAdjustToolsPanel(context, entry);
                break;

            case SHARPNESS:
                panel = new NativeEffectRangePanel(context, entry, ToolLoaderFactory.Tools.SHARPNESS, "sharpen");
                break;

            case ENHANCE:
                panel = new EnhanceEffectPanel(context, entry, ToolLoaderFactory.Tools.ENHANCE);
                break;

            case EFFECTS:
                panel = new EffectsPanel(context, entry);
                break;

            case FRAMES:
                panel = new BordersPanel(context, entry);
                break;

            case CROP:
                panel = new CropPanel(context, entry);
                break;

            case REDEYE:
                panel = new DelayedSpotDrawPanel(context, entry, ToolLoaderFactory.Tools.REDEYE);
                break;

            case WHITEN:
                panel = new DelayedSpotDrawPanel(context, entry, ToolLoaderFactory.Tools.WHITEN);
                break;

            case BLUR:
                panel = new DelayedSpotDrawPanel(context, entry, ToolLoaderFactory.Tools.BLUR);
                break;

            case BLEMISH:
                panel = new BlemishPanel(context, entry, ToolLoaderFactory.Tools.BLEMISH);
                break;

            case DRAW:
                panel = new DrawingPanel(context, entry);
                break;

            case STICKERS:
                panel = new StickersPanel(context, entry);
                break;

            case TEXT:
                panel = new TextPanel(context, entry);
                break;

            case MEME:
                panel = new MemePanel(context, entry);
                break;

            case SPLASH:
                panel = new ColorSplashPanel(context, entry);
                break;

            case FOCUS:
                panel = new TiltShiftPanel(context, entry);
                break;

            case VIGNETTE:
                panel = new VignettePanel(context, entry);
                break;

            case OVERLAYS:
                panel = new OverlaysPanel(context, entry);
                break;

            default:
                Logger logger = LoggerFactory.getLogger("EffectLoaderService", LoggerType.ConsoleLoggerType);
                logger.error("Effect with " + entry.name + " could not be found");
                break;
        }
        return panel;
    }
    // CHECKSTYLE.ON: CyclomaticComplexity

    /** The Constant ALL_ENTRIES. */
    static final ToolEntry[] ALL_ENTRIES;

    static {
        ALL_ENTRIES = new ToolEntry[]{
            new ToolEntry(ToolLoaderFactory.Tools.ENHANCE, R.drawable.aviary_tool_ic_enhance, R.string.feather_enhance),

            new ToolEntry(ToolLoaderFactory.Tools.FOCUS, R.drawable.aviary_tool_ic_focus, R.string.feather_tool_tiltshift),

            new ToolEntry(ToolLoaderFactory.Tools.EFFECTS, R.drawable.aviary_tool_ic_effects, R.string.feather_effects),

            new ToolEntry(ToolLoaderFactory.Tools.FRAMES, R.drawable.aviary_tool_ic_frames, R.string.feather_borders),

            new ToolEntry(ToolLoaderFactory.Tools.STICKERS, R.drawable.aviary_tool_ic_stickers, R.string.feather_stickers),

            new ToolEntry(ToolLoaderFactory.Tools.OVERLAYS, R.drawable.aviary_tool_ic_overlay, R.string.feather_overlays),

            new ToolEntry(ToolLoaderFactory.Tools.CROP, R.drawable.aviary_tool_ic_crop, R.string.feather_crop),

            new ToolEntry(ToolLoaderFactory.Tools.ORIENTATION, R.drawable.aviary_tool_ic_orientation, R.string.feather_adjust),

            new ToolEntry(ToolLoaderFactory.Tools.LIGHTING,
                          R.drawable.aviary_tool_ic_lighting,
                          R.string.feather_tool_lighting),

            new ToolEntry(ToolLoaderFactory.Tools.COLOR, R.drawable.aviary_tool_ic_color, R.string.feather_tool_color),

            new ToolEntry(ToolLoaderFactory.Tools.SHARPNESS, R.drawable.aviary_tool_ic_sharpen, R.string.feather_sharpen),

            new ToolEntry(ToolLoaderFactory.Tools.SPLASH, R.drawable.aviary_tool_ic_colorsplash, R.string.feather_tool_colorsplash),

            new ToolEntry(ToolLoaderFactory.Tools.DRAW, R.drawable.aviary_tool_ic_draw, R.string.feather_draw),

            new ToolEntry(ToolLoaderFactory.Tools.TEXT, R.drawable.aviary_tool_ic_text, R.string.feather_text),

            new ToolEntry(ToolLoaderFactory.Tools.REDEYE, R.drawable.aviary_tool_ic_redeye, R.string.feather_red_eye),

            new ToolEntry(ToolLoaderFactory.Tools.WHITEN, R.drawable.aviary_tool_ic_whiten, R.string.feather_whiten),

            new ToolEntry(ToolLoaderFactory.Tools.BLEMISH, R.drawable.aviary_tool_ic_blemish, R.string.feather_blemish),

            new ToolEntry(ToolLoaderFactory.Tools.MEME, R.drawable.aviary_tool_ic_meme, R.string.feather_meme),

            new ToolEntry(ToolLoaderFactory.Tools.BLUR, R.drawable.aviary_tool_ic_blur, R.string.feather_blur),

            new ToolEntry(ToolLoaderFactory.Tools.VIGNETTE, R.drawable.aviary_tool_ic_vignette, R.string.feather_vignette),
        };
    }

    /**
     * Return a list of available effects.
     *
     * @return the effects
     */
    public static ToolEntry[] getToolsEntries() {
        return ALL_ENTRIES;
    }

    public ToolEntry findEntry(ToolLoaderFactory.Tools name) {
        for (ToolEntry entry : ALL_ENTRIES) {
            if (entry.name.equals(name)) {
                return entry;
            }
        }
        return null;
    }

    public ToolEntry findEntry(String name) {
        for (ToolEntry entry : ALL_ENTRIES) {
            if (entry.name.name().equals(name)) {
                return entry;
            }
        }
        return null;
    }

    public static final ToolEntry[] getAllEntries() {
        return ALL_ENTRIES;
    }

    // CHECKSTYLE.OFF: CyclomaticComplexity
    public static int getToolDisplayName(ToolLoaderFactory.Tools tool) {
        switch (tool) {

            case SHARPNESS:
                return R.string.feather_sharpen;
            case EFFECTS:
                return R.string.feather_effects;
            case REDEYE:
                return R.string.feather_red_eye;
            case CROP:
                return R.string.feather_crop;
            case WHITEN:
                return R.string.feather_whiten;
            case DRAW:
                return R.string.feather_draw;
            case STICKERS:
                return R.string.feather_stickers;
            case TEXT:
                return R.string.feather_text;
            case BLEMISH:
                return R.string.feather_blemish;
            case MEME:
                return R.string.feather_meme;
            case ORIENTATION:
                return R.string.feather_adjust;
            case ENHANCE:
                return R.string.feather_enhance;
            case FRAMES:
                return R.string.feather_borders;
            case SPLASH:
                return R.string.feather_tool_colorsplash;
            case FOCUS:
                return R.string.feather_tool_tiltshift;
            case BLUR:
                return R.string.feather_blur;
            case VIGNETTE:
                return R.string.feather_vignette;
            case LIGHTING:
                return R.string.feather_tool_lighting;
            case COLOR:
                return R.string.feather_tool_color;
            case OVERLAYS:
                return R.string.feather_overlays;
            default:
                return 0;
        }
    }

    @Override
    public void dispose() {}
}
