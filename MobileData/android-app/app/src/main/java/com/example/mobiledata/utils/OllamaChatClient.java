package com.example.mobiledata.utils;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Giao tiep qua ASP.NET API; khong ket noi truc tiep den Ollama hay SQL Server. */
public final class OllamaChatClient {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    public interface Callback {
        void onSuccess(JSONObject data);
        void onError(String message);
    }
    private OllamaChatClient() {}

    public static void send(String text, String token, Callback callback) {
        try {
            JSONObject body = new JSONObject(); body.put("message", text);
            post("/api/chat/message", body, token, callback);
        } catch (Exception e) { callback.onError("Không tạo được tin nhắn."); }
    }
    public static void prepare(String action, long targetId, String token, Callback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("action", action); body.put("targetId", targetId);
            post("/api/chat/prepare", body, token, callback);
        } catch (Exception e) { callback.onError("Không tạo được yêu cầu xác nhận."); }
    }
    public static void confirm(String confirmationId, String token, Callback callback) {
        try {
            JSONObject body = new JSONObject(); body.put("confirmationId", confirmationId);
            post("/api/chat/confirm", body, token, callback);
        } catch (Exception e) { callback.onError("Không thể xác nhận giao dịch."); }
    }
    private static void post(String path, JSONObject data, String token, Callback cb) {
        IO.execute(() -> {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(ApiConfig.BASE_URL + path).openConnection();
                conn.setRequestMethod("POST"); conn.setConnectTimeout(8000);
                conn.setReadTimeout(path.endsWith("/message") ? 100000 : 20000);
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(data.toString().getBytes(StandardCharsets.UTF_8));
                }
                int status = conn.getResponseCode();
                InputStream stream = status < 400 ? conn.getInputStream() : conn.getErrorStream();
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                if (stream != null) try (InputStream input = stream) {
                    byte[] b = new byte[4096]; int n;
                    while ((n = input.read(b)) != -1) buf.write(b, 0, n);
                }
                JSONObject result = new JSONObject(buf.toString("UTF-8"));
                if (status >= 200 && status < 300) MAIN.post(() -> cb.onSuccess(result));
                else {
                    String msg = status == 401 ? "Phiên đăng nhập đã hết hạn. Hãy đăng nhập lại."
                            : result.optString("message", "Lỗi máy chủ (" + status + ")");
                    MAIN.post(() -> cb.onError(msg));
                }
            } catch (Exception e) {
                MAIN.post(() -> cb.onError("Không kết nối được backend. Kiểm tra API và kết nối mạng."));
            } finally { if (conn != null) conn.disconnect(); }
        });
    }
}
