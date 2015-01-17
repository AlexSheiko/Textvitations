package com.aviary.android.feather.sdk.utils;

/**
 * Created by aneem on 5/12/14.
 */
public class EmojiUtils {

    public static final int UPPER_EMOJI_CHAR = 57325;
    public static final int LOWER_EMOJI_CHAR = 56327;
    public static final int BASE_EMOJI_BYTE_1 = 55357;
    public static final int BASE_EMOJI_BYTE_2 = 55356;

    public static boolean isSingleByteEmoji( char c ) {
        if( ( c >= 0x2600 && c <= 0x26ff ) || ( c >= 0x2702 && c <= 0x27b0 ) || ( c >= 0x1f680 && c <= 0x1f6c0 ) || ( c >= 0x24c2 && c <= 0x1f251 )
                || ( c >= 0x1f600 && c <= 0x1f636 ) || ( c >= 0x1f30d && c <= 0x1f567 ) ) {
            return true;
        }
        return false;
    }

}
