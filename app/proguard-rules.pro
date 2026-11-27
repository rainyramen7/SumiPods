# Strip verbose/debug/info logs from release builds (eliminates BLE hot-path logging overhead)
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
