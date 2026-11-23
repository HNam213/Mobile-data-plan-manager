package com.example.mobiledata.utils;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Gọi Web API OTP mô phỏng trên PC qua ADB reverse. Chỉ sử dụng trong bản demo. */
public final class MobileDataAuthClient {
    private static final String BASE_URL = ApiConfig.BASE_URL;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private MobileDataAuthClient() { }

    public interface ResultCallback {
        void onSuccess(JSONObject data);
        void onError(String message);
    }

    public static void requestOtp(String phone, String carrierCode, ResultCallback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("phone", phone);
            body.put("carrierCode", carrierCode);
        } catch (Exception ex) {
            MAIN.post(() -> callback.onError("Không thể tạo yêu cầu OTP."));
            return;
        }
        send("POST", "/api/auth/request-otp", body, null, callback);
    }

    public static void verifyOtp(long requestId, String otp, ResultCallback callback) {
        JSONObject body = new JSONObject();
        try {
            body.put("requestId", requestId);
            body.put("otp", otp);
        } catch (Exception ex) {
            MAIN.post(() -> callback.onError("Không thể tạo yêu cầu xác nhận OTP."));
            return;
        }
        send("POST", "/api/auth/verify-otp", body, null, callback);
    }

    public static void me(String token, ResultCallback callback) {
        send("GET", "/api/auth/me", null, token, callback);
    }

    public static void logout(String token) {
        if (token == null || token.isEmpty()) return;
        send("POST", "/api/auth/logout", new JSONObject(), token, new ResultCallback() {
            @Override public void onSuccess(JSONObject data) { }
            @Override public void onError(String message) { }
        });
    }

    private static void send(String method, String path, JSONObject body,
                             String bearerToken, ResultCallback callback) {
        IO.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
                connection.setRequestMethod(method);
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("Accept", "application/json");
                if (bearerToken != null && !bearerToken.isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
                }
                if (body != null) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    try (OutputStream out = connection.getOutputStream()) {
                        out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    }
                }

                int status = connection.getResponseCode();
                InputStream input = status >= 200 && status < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                String response = readAll(input);
                JSONObject data = response.trim().startsWith("{")
                        ? new JSONObject(response) : new JSONObject();
                if (status >= 200 && status < 300) {
                    MAIN.post(() -> callback.onSuccess(data));
                } else {
                    String detail = data.optString("message", "Yêu cầu thất bại (HTTP " + status + ").");
                    MAIN.post(() -> callback.onError(detail));
                }
            } catch (Exception error) {
                MAIN.post(() -> callback.onError(
                        "Không kết nối được Web API. Kiểm tra CHAY_API.bat, cáp USB và adb reverse tcp:5080 tcp:5080."));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private static String readAll(InputStream input) throws IOException {
        if (input == null) return "";
        StringBuilder text = new StringBuilder();
        try (InputStream stream = input;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
        }
        return text.toString();
    }
}
