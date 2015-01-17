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

	public AbstractPanelLoaderService ( IAviaryController context ) {
		super( context );
	}

	/**
	 * Passing a {@link ToolEntry} return an instance of {@link AbstractPanel} used to
	 * create the requested tool.
	 * 
	 * @param entry
	 * @return
	 */
	public AbstractPanel createNew( ToolEntry entry ) {

		AbstractPanel panel = null;
		final IAviaryController context = getContext();

		switch ( entry.name ) {
			case ORIENTATION:
				panel = new AdjustEffectPanel( context, entry, ToolLoaderFactory.Tools.ORIENTATION );
				break;

			case BRIGHTNESS:
				panel = new NativeEffectRangePanel( context, entry, ToolLoaderFactory.Tools.BRIGHTNESS, "brightness" );
				break;

			case SATURATION:
				panel = new NativeEffectRangePanel( context, entry, ToolLoaderFactory.Tools.SATURATION, "saturation" );
				break;

			case CONTRAST:
				panel = new NativeEffectRangePanel( context, entry, ToolLoaderFactory.Tools.CONTRAST, "contrast" );
				break;

			case SHARPNESS:
				panel = new NativeEffectRangePanel( context, entry, ToolLoaderFactory.Tools.SHARPNESS, "sharpen" );
				break;

			case WARMTH:
				panel = new NativeEffectRangePanel( context, entry, ToolLoaderFactory.Tools.WARMTH, "temperature" );
				break;

			case ENHANCE:
				panel = new EnhanceEffectPanel( context, entry, ToolLoaderFactory.Tools.ENHANCE );
				break;

			case EFFECTS:
				panel = new EffectsPanel( context, entry );
				break;

			case FRAMES:
				panel = new BordersPanel( context, entry );
				break;

			case CROP:
				panel = new CropPanel( context, entry );
				break;

			case REDEYE:
				panel = new DelayedSpotDrawPanel( context, entry, ToolLoaderFactory.Tools.REDEYE );
				break;

			case WHITEN:
				panel = new DelayedSpotDrawPanel( context, entry, ToolLoaderFactory.Tools.WHITEN );
				break;

			case BLUR:
				panel = new DelayedSpotDrawPanel( context, entry, ToolLoaderFactory.Tools.BLUR );
				break;

			case BLEMISH:
				panel = new BlemishPanel( context, entry, ToolLoaderFactory.Tools.BLEMISH );
				break;

			case DRAW:
				panel = new DrawingPanel( context, entry );
				break;

			case STICKERS:
				panel = new StickersPanel( context, entry );
				break;

			case TEXT:
				panel = new TextPanel( context, entry );
				break;

			case MEME:
				panel = new MemePanel( context, entry );
				break;

			case SPLASH:
				panel = new ColorSplashPanel( context, entry );
				break;

			case FOCUS:
				panel = new TiltShiftPanel( context, entry );
				break;

			default:
				Logger logger = LoggerFactory.getLogger( "EffectLoaderService", LoggerType.ConsoleLoggerType );
				logger.error( "Effect with " + entry.name + " could not be found" );
				break;
		}
		return panel;
	}

	/** The Constant mAllEntries. */
	static final ToolEntry[] mAllEntries;

	static {
		mAllEntries = new ToolEntry[] {
				new ToolEntry( ToolLoaderFactory.Tools.ENHANCE, R.drawable.aviary_tool_ic_enhance, R.string.feather_enhance ),

				new ToolEntry( ToolLoaderFactory.Tools.FOCUS, R.drawable.aviary_tool_ic_focus, R.string.feather_tool_tiltshift ),

				new ToolEntry( ToolLoaderFactory.Tools.EFFECTS, R.drawable.aviary_tool_ic_effects, R.string.feather_effects ),

				new ToolEntry( ToolLoaderFactory.Tools.FRAMES, R.drawable.aviary_tool_ic_frames, R.string.feather_borders ),

				new ToolEntry( ToolLoaderFactory.Tools.STICKERS, R.drawable.aviary_tool_ic_stickers, R.string.feather_stickers ),

				new ToolEntry( ToolLoaderFactory.Tools.CROP, R.drawable.aviary_tool_ic_crop, R.string.feather_crop ),

				new ToolEntry( ToolLoaderFactory.Tools.ORIENTATION, R.drawable.aviary_tool_ic_orientation, R.string.feather_adjust ),

				new ToolEntry( ToolLoaderFactory.Tools.BRIGHTNESS, R.drawable.aviary_tool_ic_brightness, R.string.feather_brightness ),

				new ToolEntry( ToolLoaderFactory.Tools.CONTRAST, R.drawable.aviary_tool_ic_contrast, R.string.feather_contrast ),

				new ToolEntry( ToolLoaderFactory.Tools.SATURATION, R.drawable.aviary_tool_ic_saturation, R.string.feather_saturation ),

				new ToolEntry( ToolLoaderFactory.Tools.WARMTH, R.drawable.aviary_tool_ic_warmth, R.string.feather_tool_temperature ),

				new ToolEntry( ToolLoaderFactory.Tools.SHARPNESS, R.drawable.aviary_tool_ic_sharpen, R.string.feather_sharpen ),

				new ToolEntry( ToolLoaderFactory.Tools.SPLASH, R.drawable.aviary_tool_ic_colorsplash, R.string.feather_tool_colorsplash ),

				new ToolEntry( ToolLoaderFactory.Tools.DRAW, R.drawable.aviary_tool_ic_draw, R.string.feather_draw ),

				new ToolEntry( ToolLoaderFactory.Tools.TEXT, R.drawable.aviary_tool_ic_text, R.string.feather_text ),

				new ToolEntry( ToolLoaderFactory.Tools.REDEYE, R.drawable.aviary_tool_ic_redeye, R.string.feather_red_eye ),

				new ToolEntry( ToolLoaderFactory.Tools.WHITEN, R.drawable.aviary_tool_ic_whiten, R.string.feather_whiten ),

				new ToolEntry( ToolLoaderFactory.Tools.BLEMISH, R.drawable.aviary_tool_ic_blemish, R.string.feather_blemish ),

				new ToolEntry( ToolLoaderFactory.Tools.MEME, R.drawable.aviary_tool_ic_meme, R.string.feather_meme ),

				new ToolEntry( ToolLoaderFactory.Tools.BLUR, R.drawable.aviary_tool_ic_blur, R.string.feather_blur ),
		};
	}

	/**
	 * Return a list of available effects.
	 * 
	 * @return the effects
	 */
	public static ToolEntry[] getToolsEntries() {
		return mAllEntries;
	}

	public ToolEntry findEntry( ToolLoaderFactory.Tools name ) {
		for ( ToolEntry entry : mAllEntries ) {
			if ( entry.name.equals( name ) ) {
				return entry;
			}
		}
		return null;
	}

	public ToolEntry findEntry( String name ) {
		for ( ToolEntry entry : mAllEntries ) {
			if ( entry.name.name().equals( name ) ) {
				return entry;
			}
		}
		return null;
	}

	public static final ToolEntry[] getAllEntries() {
		return mAllEntries;
	}

	public static int getToolDisplayName(ToolLoaderFactory.Tools tool) {
		switch (tool) {

			case SHARPNESS:
				return R.string.feather_sharpen;
			case BRIGHTNESS:
				return R.string.feather_brightness;
			case CONTRAST:
				return R.string.feather_contrast;
			case SATURATION:
				return R.string.feather_saturation;
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
			case WARMTH:
				return R.string.feather_tool_temperature;
			case FRAMES:
				return R.string.feather_borders;
			case SPLASH:
				return R.string.feather_tool_colorsplash;
			case FOCUS:
				return R.string.feather_tool_tiltshift;
			case BLUR:
				return R.string.feather_blur;
		}
		return 0;
	}

	@Override
	public void dispose() {}
}
