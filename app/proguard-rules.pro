# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Preserve enum constant field names persisted as strings in DataStore.
-keepclassmembers enum com.example.ui.player.GestureSensitivity {
    public static final com.example.ui.player.GestureSensitivity LOW;
    public static final com.example.ui.player.GestureSensitivity MEDIUM;
    public static final com.example.ui.player.GestureSensitivity HIGH;
}

-keepclassmembers enum com.example.ui.player.DoubleTapSeekDuration {
    public static final com.example.ui.player.DoubleTapSeekDuration FIVE;
    public static final com.example.ui.player.DoubleTapSeekDuration TEN;
    public static final com.example.ui.player.DoubleTapSeekDuration FIFTEEN;
    public static final com.example.ui.player.DoubleTapSeekDuration THIRTY;
}
