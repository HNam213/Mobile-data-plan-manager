package com.example.mobiledata.utils;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Chỉ gọi các API công khai để đọc danh sách gói cước.
 * Trong giai đoạn chạy thử qua USB, ADB reverse chuyển cổng 5080
 * của điện thoại về Web API đang chạy trên máy tính.
 */
public final class MobileDataApiClient {

    private static final String BASE_URL = ApiConfig.BASE_URL;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private MobileDataApiClient() { }

    public static class Plan {
        public final int id;
        public final String code;
        public final String name;
        public final String carrierName;
        public final String dataText;
        public final String description;
        public final double dataGb;
        public final int price;
        public final int days;
        public final boolean popular;

        private Plan(int id, String code, String name, String carrierName,
                     String dataText, String description, double dataGb,
                     int price, int days, boolean popular) {
            this.id = id;
            this.code = code;
            this.name = name;
            this.carrierName = carrierName;
            this.dataText = dataText;
            this.description = description;
            this.dataGb = dataGb;
            this.price = price;
            this.days = days;
            this.popular = popular;
        }
    }

    public interface PlansCallback {
        void onSuccess(List<Plan> plans);
        void onError(String message);
    }

    public static void getPlans(String carrierCode, PlansCallback callback) {
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                // Dùng URL do chúng ta kiểm soát, không nhận địa chỉ từ người dùng.
                String encoded = URLEncoder.encode(carrierCode, "UTF-8");
                URL url = new URL(BASE_URL + "/api/plans?carrier=" + encoded);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                connection.setRequestProperty("Accept", "application/json");

                int status = connection.getResponseCode();
                if (status != HttpURLConnection.HTTP_OK) {
                    throw new IllegalStateException("API trả về HTTP " + status);
                }

                StringBuilder jsonText = new StringBuilder();
                try (InputStream stream = connection.getInputStream();
                     BufferedReader reader = new BufferedReader(
                             new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        jsonText.append(line);
                    }
                }

                JSONArray array = new JSONArray(jsonText.toString());
                List<Plan> plans = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.getJSONObject(i);
                    int id = item.optInt("id", 0);
                    String code = item.optString("code", "");
                    String name = item.optString("name", code);
                    String carrierName = item.optString("carrierName", "");
                    String description = item.optString("description", "");
                    double dataGb = item.optDouble("dataGb", 0);
                    double priceValue = item.optDouble("price", 0);
                    int days = item.optInt("durationDays", 0);
                    boolean popular = item.optBoolean("isPopular", false);
                    String dataType = item.optString("dataType", "TOTAL");

                    // Bỏ qua bản ghi không hợp lệ thay vì làm ứng dụng bị crash.
                    if (id <= 0 || code.isEmpty() || days <= 0 || dataGb <= 0 ||
                            priceValue <= 0 || priceValue > Integer.MAX_VALUE) {
                        continue;
                    }

                    String formattedGb = dataGb == Math.floor(dataGb)
                            ? String.format(Locale.ROOT, "%.0f", dataGb)
                            : String.format(Locale.ROOT, "%.2f", dataGb)
                                    .replaceAll("0+$", "")
                                    .replaceAll("\\.$", "");
                    String dataText = "DAILY".equalsIgnoreCase(dataType)
                            ? formattedGb + "GB/ngày"
                            : formattedGb + "GB/" + days + " ngày";

                    plans.add(new Plan(id, code, name, carrierName, dataText,
                            description, dataGb, (int) priceValue, days, popular));
                }
                List<Plan> result = Collections.unmodifiableList(plans);
                MAIN.post(() -> callback.onSuccess(result));
            } catch (Exception error) {
                // Không hiển thị connection string hay chi tiết SQL cho người dùng.
                String message = "Không kết nối được Web API. "
                        + "Hãy kiểm tra dotnet run và adb reverse tcp:5080 tcp:5080.";
                MAIN.post(() -> callback.onError(message));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }
}
