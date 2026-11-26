# Warden R8 Full-Mode Rules
#
# All persistence (SettingsJsonCodec) and network payloads (HttpGeminiGatekeeperClient)
# use platform org.json with zero reflection, so no model -keep rules are required.
# Manifest-declared entry points (Warden, FragmentActivity, WardenService,
# WardenDeviceAdminReceiver) are kept automatically by AAPT2's generated rules.

# Preserve line numbers for on-device CrashLogger stack traces while obfuscating file paths
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Strip verbose/debug/info/warning Log calls and printStackTrace() from release bytecode
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace();
}
