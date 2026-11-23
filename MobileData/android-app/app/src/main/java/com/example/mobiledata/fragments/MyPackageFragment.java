package com.example.mobiledata.fragments;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.mobiledata.MainActivity;
import com.example.mobiledata.R;
import com.example.mobiledata.utils.MobileDataBusinessClient;
import com.example.mobiledata.utils.ExpiryReminderManager;
import com.example.mobiledata.utils.ReminderCenter;
import com.example.mobiledata.utils.SessionManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;

/** Gói đang sử dụng được đọc từ SQL Server, không còn tạo gói DATA12 demo. */
public class MyPackageFragment extends Fragment {
    private static final int DEMO_NOTIFICATION_PERMISSION_REQUEST = 42881;
    private View card, empty;
    private Button btnRenewLast;
    private String lastPurchasedCode = "";
    private TextView status, start, end, remaining, progressText, message, codeText, summaryText;
    private ProgressBar progress;
    private JSONObject currentPlan;
    private long requestVersion;
    private String pendingDemoScenario = "ONE_HOUR";
    private final Handler clock = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (isAdded() && getView() != null) {
                if (currentPlan != null) {
                    long expiration = ExpiryReminderManager.parseServerDate(
                            currentPlan.optString("expiresAt", ""));
                    if (expiration > 0 && System.currentTimeMillis() >= expiration) reload();
                    else {
                        // Kiểm tra mốc vừa đến nếu màn hình đang mở.
                        ExpiryReminderManager.sync(requireContext(), currentPlan);
                        update();
                    }
                }
                clock.postDelayed(this, 60_000L);
            }
        }
    };

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater,
        @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_my_package, container, false);
        card = v.findViewById(R.id.cardMyActive);
        empty = v.findViewById(R.id.emptyMyPackage);
        btnRenewLast = v.findViewById(R.id.btnMyRenewLast);
        btnRenewLast.setOnClickListener(x -> {
            if (!lastPurchasedCode.isEmpty() && getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).openBuyForPlan(lastPurchasedCode);
        });
        status = v.findViewById(R.id.txtMyPackageStatus);
        codeText = v.findViewById(R.id.txtMyPackageCode);
        summaryText = v.findViewById(R.id.txtMyPackageSummary);
        start = v.findViewById(R.id.txtMyPackageStart);
        end = v.findViewById(R.id.txtMyPackageEnd);
        remaining = v.findViewById(R.id.txtMyPackageRemaining);
        progressText = v.findViewById(R.id.txtMyPackageProgress);
        message = v.findViewById(R.id.txtMyPackageMessage);
        progress = v.findViewById(R.id.progressMyPackage);
        v.findViewById(R.id.btnMyPackageDetail).setOnClickListener(x -> showDetail());
        v.findViewById(R.id.btnMyPackageCancel).setOnClickListener(x -> cancel());
        v.findViewById(R.id.btnMyPackageBuy).setOnClickListener(x -> openBuy());
        v.findViewById(R.id.btnMyEmptyBuy).setOnClickListener(x -> openBuy());
        v.findViewById(R.id.btnReminderSettings).setOnClickListener(x ->
                ReminderCenter.showSettings(requireContext(), currentPlan));
        v.findViewById(R.id.btnReminderHistory).setOnClickListener(x ->
                ReminderCenter.showHistory(requireContext()));
        View demoPanel = v.findViewById(R.id.panelMyPackageExpiryDemo);
        boolean debugBuild = (requireContext().getApplicationInfo().flags
                & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        demoPanel.setVisibility(debugBuild ? View.VISIBLE : View.GONE);
        if (debugBuild) {
            v.findViewById(R.id.btnDemoPackageExpiry)
                    .setOnClickListener(x -> showExpiryDemo());
            v.findViewById(R.id.btnEditDemoMessage)
                    .setOnClickListener(x -> ReminderCenter.showDemoEditor(requireContext()));
        }
        return v;
    }

    @Override public void onResume() {
        super.onResume();
        clock.removeCallbacks(tick);
        if (getView() != null) { reload(); clock.postDelayed(tick, 60_000L); }
    }
    @Override public void onPause() {
        clock.removeCallbacks(tick);
        super.onPause();
    }

    private void reload() {
        long version = ++requestVersion;
        String token = new SessionManager(requireContext()).getAccessToken();
        card.setVisibility(View.GONE);
        empty.setVisibility(View.VISIBLE);
        lastPurchasedCode = "";
        if (btnRenewLast != null) btnRenewLast.setVisibility(View.GONE);
        MobileDataBusinessClient.get("/api/me/plans", token, new MobileDataBusinessClient.Callback() {
            @Override public void onSuccess(JSONObject data) {
                if (!isAdded() || version != requestVersion || card == null) return;
                JSONArray items = MobileDataBusinessClient.items(data);
                currentPlan = items.optJSONObject(0);
                ExpiryReminderManager.sync(requireContext(), currentPlan);
                if (currentPlan != null) ExpiryReminderManager.offerPermission(MyPackageFragment.this);
                update();
                // Khi không có gói đang dùng, tìm mã gói đã mua gần nhất từ SQL Server.
                if (currentPlan == null) loadLastPurchasedPlan(version, token);
            }
            @Override public void onError(String error) {
                if (isAdded()) Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadLastPurchasedPlan(long version, String token) {
        MobileDataBusinessClient.get("/api/me/history", token, new MobileDataBusinessClient.Callback() {
            @Override public void onSuccess(JSONObject response) {
                if (!isAdded() || version != requestVersion || btnRenewLast == null
                        || currentPlan != null) return;
                JSONArray rows = MobileDataBusinessClient.items(response);
                lastPurchasedCode = "";
                // API lịch sử trả về giao dịch mới nhất trước (tối đa 200 dòng).
                for (int i = 0; i < rows.length(); i++) {
                    JSONObject item = rows.optJSONObject(i);
                    if (item == null) continue;
                    String statusValue = item.optString("status", "");
                    if ("DANG_KY_GOI".equalsIgnoreCase(item.optString("type"))
                            && "SUCCESS".equalsIgnoreCase(statusValue)) {
                        String code = item.optString("planCode", "").trim();
                        if (!code.isEmpty() && !"null".equalsIgnoreCase(code)) {
                            lastPurchasedCode = code;
                            break;
                        }
                    }
                }
                if (!lastPurchasedCode.isEmpty()) {
                    btnRenewLast.setText("Đăng ký lại gói " + lastPurchasedCode);
                    btnRenewLast.setVisibility(View.VISIBLE);
                }
            }
            @Override public void onError(String ignored) {
                // Tính năng đăng ký lại là tùy chọn: lỗi lịch sử không làm mất màn hình gói.
            }
        });
    }

    private void update() {
        if (card == null || empty == null) return;
        if (currentPlan == null) {
            card.setVisibility(View.GONE); empty.setVisibility(View.VISIBLE); return;
        }
        card.setVisibility(View.VISIBLE); empty.setVisibility(View.GONE);
        String code = currentPlan.optString("code");
        String started = currentPlan.optString("startedAt");
        String expires = currentPlan.optString("expiresAt");
        codeText.setText(code);
        String unit = "DAILY".equals(currentPlan.optString("dataType")) ? "GB/ngày" : "GB/chu kỳ";
        summaryText.setText(currentPlan.optDouble("dataGb") + unit + " • "
                + NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"))
                .format(currentPlan.optDouble("price")) + "đ");
        start.setText("Ngày đăng ký: " + formatDate(started));
        end.setText("Hết hạn: " + formatDate(expires));
        long begin = parseDate(started), finish = parseDate(expires), now = System.currentTimeMillis();
        long left = Math.max(0L, finish - now);
        long days = (left + 86_399_999L) / 86_400_000L;
        if (left == 0L) remaining.setText("Gói đã hết hạn");
        else if (left < 3_600_000L) remaining.setText("Còn " + Math.max(1L, (left + 59_999L) / 60_000L) + " phút");
        else if (left < 86_400_000L) remaining.setText("Còn " + ((left + 3_599_999L) / 3_600_000L) + " giờ");
        else remaining.setText("Còn khoảng " + days + " ngày");
        int percent = finish <= begin ? 0 : (int)Math.max(0, Math.min(100,
                100.0 * (now - begin) / (finish - begin)));
        progress.setProgress(percent);
        progressText.setText("Đã qua " + percent + "% chu kỳ");
        boolean warning = left < 86_400_000L;
        status.setText(warning ? "Sắp hết hạn" : "Đang sử dụng");
        status.setTextColor(Color.parseColor(warning ? "#D78612" : "#13976E"));
        message.setText(warning
                ? "Gói " + code + " sắp hết hạn. Hãy xem xét mua gói mới khi cần; không tự gia hạn."
                : "Gói " + code + " còn hiệu lực. Không tự gia hạn hay trừ thêm tiền.");
    }

    private long parseDate(String iso) {
        try {
            String str = iso.length() >= 19 ? iso.substring(0, 19) : iso;
            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
            return format.parse(str).getTime();
        } catch (Exception e) { return System.currentTimeMillis(); }
    }

    private String formatDate(String date) {
        try {
            long time = parseDate(date);
            return new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("vi", "VN"))
                    .format(new java.util.Date(time));
        } catch (Exception ex) { return date; }
    }

    private void showDetail() {
        if (!isAdded() || currentPlan == null) return;
        JSONObject p = currentPlan;
        String unit = "DAILY".equals(p.optString("dataType")) ? "GB/ngày" : "GB/chu kỳ";
        new AlertDialog.Builder(requireContext(), R.style.MobileDataLightDialog)
                .setTitle(p.optString("code"))
                .setMessage("Dung lượng: " + p.optDouble("dataGb") + unit
                        + "\nGiá lúc đăng ký: " + NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"))
                        .format(p.optDouble("price")) + "đ"
                        + "\nChu kỳ: " + p.optInt("durationDays") + " ngày"
                        + "\nĐăng ký: " + formatDate(p.optString("startedAt"))
                        + "\nHết hạn: " + formatDate(p.optString("expiresAt")))
                .setPositiveButton("Đóng", null).show();
    }

    private void cancel() {
        if (!isAdded() || currentPlan == null) return;
        long id = currentPlan.optLong("registrationId");
        new AlertDialog.Builder(requireContext(), R.style.MobileDataLightDialog)
                .setTitle("Hủy gói " + currentPlan.optString("code") + "?")
                .setMessage("Gói sẽ ngừng hoạt động ngay, không tự gia hạn và không hoàn lại tiền.")
                .setNegativeButton("Giữ gói", null)
                .setPositiveButton("Hủy gói", (d,w) -> {
                    String token = new SessionManager(requireContext()).getAccessToken();
                    MobileDataBusinessClient.post("/api/me/plans/" + id + "/cancel",
                            new JSONObject(), token, new MobileDataBusinessClient.Callback() {
                        @Override public void onSuccess(JSONObject result) {
                            if (isAdded()) { ExpiryReminderManager.clear(requireContext());
                                currentPlan = null; update(); reload();
                                Toast.makeText(requireContext(), result.optString("message"), Toast.LENGTH_LONG).show(); }
                        }
                        @Override public void onError(String err) {
                            if (isAdded()) Toast.makeText(requireContext(), err, Toast.LENGTH_LONG).show();
                        }
                    });
                }).show();
    }

    /** Cho người trình diễn chọn kịch bản; không thay đổi dữ liệu thật. */
    private void showExpiryDemo() {
        if (!isAdded()) return;
        String[] options = {
                "Còn 3 ngày (mẫu)",
                "Còn 24 giờ (mẫu)",
                "Còn 1 giờ (mẫu)",
                "Còn 5 phút (mẫu)",
                "Dung lượng còn 20% (mô phỏng)",
                "Dung lượng còn 5% (mô phỏng)",
                "Đã hết hạn (mẫu)"
        };
        String[] types = {"THREE_DAYS", "ONE_DAY", "ONE_HOUR", "FIVE_MIN",
                "LOW_DATA", "LOW_5", "EXPIRED"};
        new AlertDialog.Builder(requireContext(), R.style.MobileDataLightDialog)
                .setTitle("Chọn tình huống thông báo Demo")
                .setItems(options, (dialog, index) -> sendExpiryDemo(types[index]))
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void sendExpiryDemo(String scenario) {
        if (!isAdded()) return;
        pendingDemoScenario = scenario;
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                requireContext(), "android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},
                    DEMO_NOTIFICATION_PERMISSION_REQUEST);
            return;
        }
        String code = currentPlan == null ? "DATA12 (mẫu)"
                : currentPlan.optString("code", "GÓI MẪU");
        boolean sent = ExpiryReminderManager.showDemoNotification(requireContext(),
                code, scenario);
        if (sent) {
            Toast.makeText(requireContext(),
                    "Đã tạo thông báo DEMO. Kéo thanh thông báo để xem.",
                    Toast.LENGTH_LONG).show();
        } else {
            openNotificationSettingsDialog();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRequestPermissionsResult(int requestCode,
            @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != DEMO_NOTIFICATION_PERMISSION_REQUEST || !isAdded()) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            sendExpiryDemo(pendingDemoScenario);
        } else {
            openNotificationSettingsDialog();
        }
    }

    private void openNotificationSettingsDialog() {
        if (!isAdded()) return;
        new AlertDialog.Builder(requireContext(), R.style.MobileDataLightDialog)
                .setTitle("Chưa bật thông báo")
                .setMessage("Hãy cho phép MobileData gửi thông báo trong Cài đặt của điện thoại, rồi nhấn Demo lại.")
                .setNegativeButton("Đóng", null)
                .setPositiveButton("Mở cài đặt", (dialog, which) -> {
                    Intent intent;
                    if (Build.VERSION.SDK_INT >= 26) {
                        intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE,
                                        requireContext().getPackageName());
                    } else {
                        intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + requireContext().getPackageName()));
                    }
                    startActivity(intent);
                }).show();
    }

    private void openBuy() {
        if (getActivity() instanceof MainActivity)
            ((MainActivity)getActivity()).navigateToTab(R.id.nav_buy);
    }

    @Override public void onDestroyView() {
        clock.removeCallbacks(tick);
        requestVersion++;
        currentPlan = null;
        card = empty = null; btnRenewLast = null;
        lastPurchasedCode = "";
        status = start = end = remaining = progressText = message = null;
        progress = null; codeText = summaryText = null;
        super.onDestroyView();
    }
}
