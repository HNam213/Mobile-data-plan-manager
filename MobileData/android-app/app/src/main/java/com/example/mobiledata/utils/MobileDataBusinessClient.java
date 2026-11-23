package com.example.mobiledata.utils;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Các API yêu cầu token USER/ADMIN; không chạy mạng trên luồng giao diện. */
public final class MobileDataBusinessClient {
    private static final String HOST = ApiConfig.BASE_URL;
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback {
        void onSuccess(JSONObject result);
        void onError(String message);
    }

    private MobileDataBusinessClient() { }

    public static void get(String path, String token, Callback cb) {
        execute("GET", path, null, token, cb);
    }

    public static void post(String path, JSONObject body, String token, Callback cb) {
        execute("POST", path, body, token, cb);
    }

    public static void put(String path, JSONObject body, String token, Callback cb) {
        execute("PUT", path, body, token, cb);
    }

    public static void delete(String path, String token, Callback cb) {
        execute("DELETE", path, null, token, cb);
    }

    public static void patch(String path, JSONObject body, String token, Callback cb) {
        execute("PATCH", path, body, token, cb);
    }

    public static JSONArray items(JSONObject result) {
        return result.optJSONArray("items") == null ? new JSONArray() : result.optJSONArray("items");
    }

    private static void execute(String method, String path, JSONObject body,
                                String token, Callback cb) {
        IO.execute(() -> {
            HttpURLConnection connection = null;
            try {
                if (!path.startsWith("/api/")) throw new IllegalArgumentException("Đường dẫn API không hợp lệ");
                connection = (HttpURLConnection) new URL(HOST + path).openConnection();
                connection.setRequestMethod(method);
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("Accept", "application/json");
                if (token != null && !token.isEmpty())
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                if (body != null) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    try (OutputStream out = connection.getOutputStream()) {
                        out.write(body.toString().getBytes(StandardCharsets.UTF_8));
                    }
                }
                int status = connection.getResponseCode();
                InputStream source = status >= 200 && status < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                StringBuilder response = new StringBuilder();
                if (source != null) {
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(source, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) response.append(line);
                    }
                }
                JSONObject result;
                String raw = response.toString().trim();
                if (raw.startsWith("[")) {
                    result = new JSONObject();
                    result.put("items", new JSONArray(raw));
                } else if (raw.startsWith("{")) {
                    result = new JSONObject(raw);
                } else result = new JSONObject();

                if (status >= 200 && status < 300) {
                    MAIN.post(() -> cb.onSuccess(result));
                } else {
                    String message = result.optString("message", "HTTP " + status);
                    if (status == 401) message = "Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại.";
                    String finalMessage = message;
                    MAIN.post(() -> cb.onError(finalMessage));
                }
            } catch (Exception ex) {
                MAIN.post(() -> cb.onError("Không thể kết nối máy chủ. Vui lòng kiểm tra kết nối và thử lại."));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }
}
