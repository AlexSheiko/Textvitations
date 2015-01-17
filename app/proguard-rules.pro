
# Specifies to exhaustively list classes and class members matched by the various -keep options.
# The list is printed to the standard output or to the given file. The list can be useful to verify if the intended class members
# are really found, especially if you're using wildcards. For example, you may want to list all the applications or all the
# applets that you are keeping.
-printseeds proguard_keep.txt

# Specifies to list dead code of the input class files. The list is printed to the standard output or to the given file.
# For example, you can list the unused code of an application. Only applicable when shrinking.
-printusage proguard_unused.txt

# Specifies to print the mapping from old names to new names for classes and class members that have been renamed.
# The mapping is printed to the standard output or to the given file. For example, it is required for subsequent incremental
# obfuscation, or if you ever want to make sense again of obfuscated stack traces. Only applicable when obfuscating.
-printmapping proguard_mapping.txt

# Specifies to write out the entire configuration that has been parsed, with included files and replaced variables.
# The structure is printed to the standard output or to the given file.
# This can sometimes be useful for debugging configurations, or for converting XML configurations into a more readable format.
-printconfiguration proguard_configuration.txt

# Specifies to write out the internal structure of the class files, after any processing.
# The structure is printed to the standard output or to the given file.
# For example, you may want to write out the contents of a given jar file, without processing it at all.
-dump proguard_dump.txt

-verbose
-dontobfuscate
-dontpreverify
-optimizationpasses 5
-optimizations !code/simplification/arithmetic
#-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*,!code/allocation/variable

-dontwarn com.squareup.okhttp.internal.**

# common library
-keep class com.aviary.android.feather.common.AviaryIntent
-keep class com.aviary.android.feather.common.tracking.AviaryTracker
-keep class com.aviary.android.feather.common.tracking.AbstractTracker
-keep class com.aviary.android.feather.common.log.LoggerFactory
-keep class com.aviary.android.feather.headless.gl.GLUtils
-keep class com.aviary.android.feather.library.services.BaseContextService
-keep class com.aviary.android.feather.library.external.tracking.TrackerFactory


# headless library
-keep interface com.aviary.android.feather.headless.filters.IFilter
-keep class com.aviary.android.feather.headless.AviaryEffect
-keep class com.aviary.android.feather.headless.moa.Moa
-keep class com.aviary.android.feather.headless.moa.MoaHD
-keep class com.aviary.android.feather.headless.moa.MoaParameter
-keep class com.aviary.android.feather.headless.utils.CameraUtils

-keep class com.aviary.android.feather.sdk.BuildConfig
-keep class com.aviary.android.feather.cds.BuildConfig
-keep class com.aviary.android.feather.headless.BuildConfig
-keep class com.aviary.android.feather.common.BuildConfig

-keep class * extends com.aviary.android.feather.headless.filters.IFilter
-keep class * extends com.aviary.android.feather.headless.moa.MoaParameter


-keep class * extends com.aviary.android.feather.library.services.BaseContextService
-keep class * extends com.aviary.android.feather.common.tracking.AbstractTracker
-keep class * extends android.app.Service
-keep class * extends android.os.AsyncTask
-keep class * extends android.app.Activity
-keep class * extends android.app.Application
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.content.ContentProvider
-keep class com.android.vending.licensing.ILicensingService
-keep public class com.android.vending.billing.IInAppBillingService


-keepclassmembers class com.aviary.android.feather.common.utils.SDKUtils {*;}
-keepclassmembers class com.aviary.android.feather.common.utils.SDKUtils$ApiKeyReader {*;}

# keep everything for native methods/fields
-keepclassmembers class com.aviary.android.feather.headless.moa.Moa {*;}
-keepclassmembers class com.aviary.android.feather.headless.moa.MoaHD {*;}
-keepclassmembers class com.aviary.android.feather.headless.utils.CameraUtils {*;}
-keepclassmembers class com.aviary.android.feather.headless.moa.MoaResult {*;}
-keepclassmembers class com.aviary.android.feather.sdk.opengl.AviaryGLSurfaceView {*;}

-keepclassmembers class com.aviary.android.feather.headless.filters.MoaJavaToolStrokeResult {
  <methods>;
}

-keepclassmembers class com.aviary.android.feather.headless.gl.GLUtils {
  <methods>;
}

-keepclassmembers class com.aviary.android.feather.headless.filters.NativeToolFilter {*;}

-keepclassmembers class com.aviary.android.feather.common.AviaryIntent {*;}
-keepclassmembers class com.aviary.android.feather.common.utils.os.AviaryIntentService {*;}
-keepclassmembers class com.aviary.android.feather.common.utils.os.AviaryAsyncTask {*;}

-keepclassmembers class com.aviary.android.feather.common.tracking.AbstractTracker {
    <fields>;
}
-keepclassmembers class com.aviary.android.feather.common.tracking.AviaryTracker {
    <fields>;
}

-keepclassmembers class com.aviary.android.feather.common.log.LoggerFactory {
    <fields>;
}

-keepclassmembers class com.aviary.android.feather.sdk.BuildConfig {*;}
-keepclassmembers class com.aviary.android.feather.cds.BuildConfig {*;}
-keepclassmembers class com.aviary.android.feather.headless.BuildConfig {*;}
-keepclassmembers class com.aviary.android.feather.common.BuildConfig {*;}


# keep class members
-keepclassmembers class com.aviary.android.feather.library.tracking.AbstractTracker { *; }
-keepclassmembers class com.aviary.android.feather.library.external.tracking.TrackerFactory { *; }
-keepclassmembers class com.aviary.android.feather.headless.gl.GLUtils { *; }
-keepclassmembers class com.aviary.android.feather.headless.moa.MoaResult { *; }
-keepclassmembers class com.aviary.android.feather.library.services.BaseContextService { *; }
-keepclassmembers class com.aviary.android.feather.utils.SettingsUtils { *; }

-keepclassmembers class * extends com.aviary.android.feather.library.services.BaseContextService {
   public <init>( com.aviary.android.feather.library.services.IAviaryController );
}

-keepclasseswithmembers class * {
    public <init>( com.aviary.android.feather.library.services.IAviaryController );
}

-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

# Keep all the native methods
-keepclassmembers class * {
   private native <methods>;
   public native <methods>;
   protected native <methods>;
   public static native <methods>;
   private static native <methods>;
   static native <methods>;
   native <methods>;
}

-keepclasseswithmembers class * {
    public <init>( com.aviary.android.feather.library.services.IAviaryController );
}
