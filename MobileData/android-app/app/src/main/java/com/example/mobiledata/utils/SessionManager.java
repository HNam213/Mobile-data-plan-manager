package com.example.mobiledata.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.lang.reflect.Field;
import java.util.Locale;

/** Thông tin phiên đăng nhập của giao diện. Máy chủ vẫn là nơi xác thực quyền truy cập. */
public final class SessionManager {
    private static final String PREFS = "mobiledata_session_api";
    private static final String KEY_LOGGED_IN = "logged_in";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_CARRIER = "carrier";
    private static final String KEY_NAME = "name";
    private static final String KEY_ROLE = "role";
    private static final String KEY_TOKEN = "access_token";
    private static final String KEY_EXPIRES = "expires_at_ms";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Lưu hồ sơ đã được xác minh OTP bởi Web API. */
    public void saveApiLogin(String phone, String carrierName, String fullName,
                             String role, String accessToken) {
        // Web API hiện phát token 60 phút; máy khách tự giới hạn còn 55 phút.
        long expires = System.currentTimeMillis() + 55L * 60_000L;
        save(phone, carrierName, fullName, role, accessToken, expires);
    }

    /** Cập nhật hồ sơ từ /api/auth/me, không kéo dài hiệu lực của token máy chủ. */
    public void refreshProfile(String phone, String carrierName, String fullName, String role) {
        prefs.edit()
                .putString(KEY_PHONE, phone)
                .putString(KEY_CARRIER, carrierName)
                .putString(KEY_NAME, fullName)
                .putString(KEY_ROLE, role.toUpperCase(Locale.ROOT))
                .apply();
    }

    /** Phương thức tương thích dữ liệu demo đời cũ (không có token API). */
    public void saveLogin(String phone, String carrier, String name, String role) {
        save(phone, carrier, name, role, "", Long.MAX_VALUE);
    }

    /** Tương thích khi mã cũ gọi saveLogin(account). */
    public void saveLogin(Object account) {
        if (account == null) return;
        saveLogin(field(account, "phone", "phoneNumber", "soDienThoai"),
                field(account, "carrier", "carrierName", "nhaMang"),
                field(account, "name", "fullName", "hoTen"),
                field(account, "role", "vaiTro"));
    }

    private static String field(Object object, String... names) {
        for (String name : names) {
            try {
                Field f = object.getClass().getDeclaredField(name);
                f.setAccessible(true);
                Object value = f.get(object);
                if (value != null) return value.toString();
            } catch (Exception ignored) { }
        }
        return "";
    }

    private void save(String phone, String carrier, String name, String role,
                      String token, long expiry) {
        prefs.edit()
                .putBoolean(KEY_LOGGED_IN, true)
                .putString(KEY_PHONE, phone)
                .putString(KEY_CARRIER, carrier)
                .putString(KEY_NAME, name)
                .putString(KEY_ROLE, role.toUpperCase(Locale.ROOT))
                .putString(KEY_TOKEN, token)
                .putLong(KEY_EXPIRES, expiry)
                .apply();
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_LOGGED_IN, false)
                && System.currentTimeMillis() < prefs.getLong(KEY_EXPIRES, 0L);
    }

    public boolean isAdmin() {
        return isLoggedIn() && "ADMIN".equals(getRole());
    }

    public String getPhone() { return prefs.getString(KEY_PHONE, ""); }
    public String getName() { return prefs.getString(KEY_NAME, ""); }
    public String getCarrier() { return prefs.getString(KEY_CARRIER, ""); }
    public String getRole() { return prefs.getString(KEY_ROLE, "USER"); }
    public String getAccessToken() { return prefs.getString(KEY_TOKEN, ""); }

    public String getCarrierCode() {
        switch (getCarrier()) {
            case "VinaPhone": return "VN";
            case "Viettel": return "VT";
            case "MobiFone": return "MB";
            default: return "";
        }
    }

    public void logout() {
        String token = getAccessToken();
        prefs.edit().clear().apply();
        if (!token.isEmpty()) MobileDataAuthClient.logout(token);
    }

    public void clear() { logout(); }
}
