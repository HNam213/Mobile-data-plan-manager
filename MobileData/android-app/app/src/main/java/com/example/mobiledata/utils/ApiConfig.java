package com.example.mobiledata.utils;

/** API MobileData khi demo trên máy tính bằng ADB reverse tcp:5080. */
public final class ApiConfig {
    private ApiConfig() { }
    // Cả hai LDPlayer dùng chung 127.0.0.1:5080 sau khi thiết lập reverse riêng.
    public static final String BASE_URL = "http://127.0.0.1:5080";
}
