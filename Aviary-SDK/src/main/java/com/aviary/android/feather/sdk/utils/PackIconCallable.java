package com.aviary.android.feather.sdk.utils;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;

import com.aviary.android.feather.cds.AviaryCds.PackType;
import com.aviary.android.feather.library.utils.BitmapUtils;
import com.aviary.android.feather.sdk.R;
import it.sephiroth.android.library.picasso.Transformation;

import java.lang.ref.SoftReference;
import java.util.concurrent.Callable;

public class PackIconCallable implements Transformation, Callable<Bitmap> {

	public static class Builder {
		Resources resources;
		String path;
		PackType packType;
		boolean roundedCorners;
		boolean noBackground;
		int alpha = 255;
		private boolean isnew;

		public Builder() {}

		public Builder withResources( Resources resources ) {
			this.resources = resources;
			return this;
		}

		public Builder withPackType( PackType packType ) {
			this.packType = packType;
			return this;
		}

		public Builder withPath( String path ) {
			this.path = path;
			return this;
		}

		public Builder withAlpha( int alpha ) {
			this.alpha = alpha;
			return this;
		}

		public Builder roundedCorners() {
			this.roundedCorners = true;
			return this;
		}

		public Builder noBackground() {
			this.noBackground = true;
			return this;
		}

		public Builder isNew ( final boolean value ) {
			this.isnew = value;
			return this;
		}

		public PackIconCallable build() {
			PackIconCallable instance = new PackIconCallable();

			if( null == path ) throw new IllegalArgumentException( "path cannot be null" );
			if( null == packType ) throw new IllegalArgumentException( "packType cannot be null" );
			if( null == resources ) throw new IllegalArgumentException( "resources cannot be null" );

			instance.imagePath = path;
			instance.packType = packType;
			instance.resourcesRef = new SoftReference<Resources>( resources );
			instance.roundedCorners = roundedCorners;
			instance.noBackground = noBackground;
			instance.alpha = alpha;
			instance.isnew = isnew;
			return instance;
		}
	}

	SoftReference<Resources> resourcesRef;
	int fallbackResId = -1;
	int maxSize = -1;

	private String imagePath;
	private PackType packType;
	private boolean roundedCorners;
	private boolean noBackground;
	private int alpha;
	private boolean isnew;

	PackIconCallable() {}

	@Override
	public String key() {
		return imagePath + "_" + packType.name() + "_"  +roundedCorners + "_" + noBackground + "_" + alpha + "_" + isnew;
	}

	@Override
	public Bitmap call() throws Exception {

		Bitmap result = null;
		Bitmap bitmap = null;

		final Resources resources = resourcesRef.get();

		if ( null == resources ) {
			return null;
		}

		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inPreferredConfig = Config.ARGB_8888;

		if ( null != imagePath && imagePath.length() > 0 ) {
			result = BitmapFactory.decodeFile( imagePath, options );
		}

		if ( null == result ) {
			result = BitmapFactory.decodeResource( resources, fallbackResId );
		}
		
		result = transform( result );

		if ( maxSize > 0 && null != result ) {
			bitmap = BitmapUtils.resizeBitmap( result, maxSize, maxSize );
			if ( null != bitmap && result != bitmap ) {
				result.recycle();
				result = bitmap;
			}
		}

		return result;
	}

	Bitmap generate( Resources res, Bitmap icon, int maxSize ) {
		Bitmap result = generate( res, icon );
		Bitmap resized = BitmapUtils.resizeBitmap( result, maxSize, maxSize );
		
		if( resized != null && resized != result ) {
			if( result != icon ) {
				result.recycle();
			}
		}
		return resized;
	}
	
	Bitmap generate( Resources res, Bitmap icon ) {
		Bitmap background = null;
		Bitmap result = null;
		
		if( res == null ) return icon;
		
		if ( PackType.EFFECT.equals( packType ) ) {

			if( !noBackground ) {
				background = BitmapFactory.decodeResource( res, R.drawable.aviary_effects_pack_background );
			}

			if ( null != background ) {
				Bitmap newBitmap = BitmapUtils.roundedCorners( icon, 10, 10 );
				result = BitmapUtils.flattenDrawables( new BitmapDrawable( res, background ), new BitmapDrawable( res, newBitmap ), 0.76f, 0f );
				
				if( null != result && !newBitmap.equals( result ) ) {
					newBitmap.recycle();
				}

			} else {
				if( roundedCorners ) {
					result = BitmapUtils.roundedCorners( icon, 12, 12 );
				}
			}

		} else if ( PackType.STICKER.equals( packType ) ) {

			if( !noBackground ) {
				background = BitmapFactory.decodeResource( res, isnew ? R.drawable.aviary_sticker_pack_background_glow : R.drawable.aviary_sticker_pack_background );
			}

			if ( null != background ) {
				result = BitmapUtils.flattenDrawables( new BitmapDrawable( res, background ), new BitmapDrawable( res, icon ), 0.58f, 0.05f );
			}
		}

		if( null != result ) {
			if( alpha == 255 ) {
				return result;
			}

			Bitmap result2 = BitmapUtils.copy( result, alpha );
			if( !result.equals( result2 )) {
				result.recycle();
			}

			return result2;
		}

		return icon;
	}

	@Override
	public Bitmap transform( Bitmap bitmap ) {

		final Resources resources = resourcesRef.get();

		if ( null == resources ) {
			return null;
		}

		// packtype
		if ( null != bitmap ) {
			Bitmap result = generate( resources, bitmap );

			if ( null != result && result != bitmap ) {
				bitmap.recycle();
				bitmap = result;
			}
		}
		
		return bitmap;
	}
}
