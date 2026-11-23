package com.example.mobiledata.fragments;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.mobiledata.MainActivity;
import com.example.mobiledata.R;
import com.example.mobiledata.utils.MobileDataApiClient;
import com.example.mobiledata.utils.MobileDataBusinessClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import com.example.mobiledata.utils.SessionManager;
import com.example.mobiledata.utils.SupportReplyNotifier;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HomeFragment extends Fragment {

    private static final long MINUTE_MS = 60_000L;
    private static final long HOUR_MS = 60L * MINUTE_MS;
    private static final long DAY_MS = 24L * HOUR_MS;

    private final Handler handler = new Handler(Looper.getMainLooper());

    // Cập nhật lại thời gian và trạng thái gói khi đang ở Trang chủ.
    private final Runnable refreshTask = new Runnable() {
        @Override
        public void run() {
            View root = getView();
            if (root != null && isAdded()) {
                updateHomeInfo(root);
                SupportReplyNotifier.check(requireContext());
                handler.postDelayed(this, 60_000L);
            }
        }
    };

    public HomeFragment() {
        // Constructor rỗng để Android tạo lại Fragment khi cần.
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_home, container, false);
        setupActions(root);
        setupRecommendedPackages(root);
        updateHomeInfo(root);
        return root;
    }

    private void setupActions(View root) {
        root.findViewById(R.id.actionHomeBuy)
                .setOnClickListener(v -> navigateToTab(R.id.nav_buy));
        root.findViewById(R.id.actionHomeManage)
                .setOnClickListener(v -> navigateToTab(R.id.nav_packages));
        root.findViewById(R.id.actionHomeTopup)
                .setOnClickListener(v -> navigateToTab(R.id.nav_account));
        root.findViewById(R.id.actionHomeHistory)
                .setOnClickListener(v -> navigateToTab(R.id.nav_history));
        root.findViewById(R.id.btnHomeManagePackage)
                .setOnClickListener(v -> navigateToTab(R.id.nav_packages));
        root.findViewById(R.id.btnHomeBanner)
                .setOnClickListener(v -> navigateToTab(R.id.nav_buy));
        root.findViewById(R.id.btnHomeSeeAll)
                .setOnClickListener(v -> navigateToTab(R.id.nav_buy));
        root.findViewById(R.id.cardHomeEmptyPlan)
                .setOnClickListener(v -> navigateToTab(R.id.nav_buy));
    }

    private void updateHomeInfo(View root) {
        if (!isAdded()) return;
        SessionManager session = new SessionManager(requireContext());
        String name = session.getName().trim();
        ((TextView) root.findViewById(R.id.txtHomeName))
                .setText(name.isEmpty() ? "Thuê bao MobileData" : name);
        ((TextView) root.findViewById(R.id.txtHomePhone)).setText(formatPhone(session.getPhone()));
        ((TextView) root.findViewById(R.id.txtHomeCarrier)).setText(session.getCarrier());
        ((TextView) root.findViewById(R.id.txtHomeToday)).setText(
                new SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("vi-VN")).format(new Date()));

        String token = session.getAccessToken();
        MobileDataBusinessClient.get("/api/me/balance", token, new MobileDataBusinessClient.Callback() {
            @Override public void onSuccess(JSONObject result) {
                if (isAdded() && getView() == root)
                    ((TextView) root.findViewById(R.id.txtHomeBalance)).setText(
                            NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"))
                                    .format(result.optDouble("balance")) + "đ");
            }
            @Override public void onError(String msg) { }
        });

        MobileDataBusinessClient.get("/api/me/plans", token, new MobileDataBusinessClient.Callback() {
            @Override public void onSuccess(JSONObject result) {
                if (!isAdded() || getView() != root) return;
                JSONArray plans = MobileDataBusinessClient.items(result);
                JSONObject p = plans.optJSONObject(0);
                View activeCard = root.findViewById(R.id.cardHomeActivePlan);
                View emptyCard = root.findViewById(R.id.cardHomeEmptyPlan);
                activeCard.setVisibility(p == null ? View.GONE : View.VISIBLE);
                emptyCard.setVisibility(p == null ? View.VISIBLE : View.GONE);
                if (p == null) return;
                ((TextView) root.findViewById(R.id.txtHomePackageName)).setText(p.optString("code"));
                String unit = "DAILY".equals(p.optString("dataType")) ? "GB/ngày" : "GB/chu kỳ";
                ((TextView) root.findViewById(R.id.txtHomePackageInfo)).setText(
                        session.getCarrier() + " • " + p.optDouble("dataGb") + unit);
                long start = parseDate(p.optString("startedAt"));
                long end = parseDate(p.optString("expiresAt"));
                long now = System.currentTimeMillis();
                long remaining = Math.max(0L, end - now);
                ((TextView) root.findViewById(R.id.txtHomePackageRemaining)).setText(remainingText(remaining));
                int percent = end <= start ? 0 : (int)Math.max(0, Math.min(100,
                        100.0 * (now - start) / (end - start)));
                ((ProgressBar) root.findViewById(R.id.progressHomePackage)).setProgress(percent);
                ((TextView) root.findViewById(R.id.txtHomePackageProgress))
                        .setText("Đã qua " + percent + "% chu kỳ");
                TextView warning = root.findViewById(R.id.txtHomePackageWarning);
                warning.setText(remaining <= DAY_MS ? "Gói sắp hết hạn, không tự gia hạn."
                        : "Gói đang hoạt động. Xem chi tiết tại Gói dùng.");
            }
            @Override public void onError(String msg) { }
        });
    }

    private long parseDate(String value) {
        try {
            String iso = value.length() >= 19 ? value.substring(0,19) : value;
            java.text.SimpleDateFormat format = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
            return format.parse(iso).getTime();
        } catch (Exception ex) { return System.currentTimeMillis(); }
    }

    private String formatPhone(String phone) {
        if (phone != null && phone.matches("0\\d{9}")) {
            return phone.substring(0, 4) + " " + phone.substring(4, 7)
                    + " " + phone.substring(7);
        }
        return phone == null ? "" : phone;
    }

    private String remainingText(long milliseconds) {
        if (milliseconds < HOUR_MS) {
            return "Còn " + Math.max(1L,
                    (milliseconds + MINUTE_MS - 1L) / MINUTE_MS) + " phút";
        }
        if (milliseconds < DAY_MS) {
            return "Còn " + ((milliseconds + HOUR_MS - 1L) / HOUR_MS) + " giờ";
        }
        return "Còn " + ((milliseconds + DAY_MS - 1L) / DAY_MS) + " ngày";
    }

    private void setupRecommendedPackages(View root) {
        LinearLayout container = root.findViewById(R.id.recommendContainer);
        container.removeAllViews();
        String carrier = new SessionManager(requireContext()).getCarrierCode();
        MobileDataApiClient.getPlans(carrier, new MobileDataApiClient.PlansCallback() {
            @Override public void onSuccess(java.util.List<MobileDataApiClient.Plan> plans) {
                if (!isAdded() || getView() != root) return;
                container.removeAllViews();
                int count = 0;
                for (MobileDataApiClient.Plan p : plans) {
                    if (!p.popular && plans.size() > 3) continue;
                    addPackageCard(container, p.code, p.dataText,
                            NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"))
                                .format(p.price) + "đ", "/" + p.days + " ngày",
                            p.popular ? "Phổ biến" : "Gói cước", "Nổi bật");
                    if (++count >= 3) break;
                }
            }
            @Override public void onError(String message) { }
        });
    }

    private void addPackageCard(LinearLayout container, String name,
                                String data, String price, String period,
                                String badge, String rating) {
        View card = LayoutInflater.from(requireContext()).inflate(
                R.layout.item_home_plan, container, false);
        ((TextView) card.findViewById(R.id.txtPlanName)).setText(name);
        ((TextView) card.findViewById(R.id.txtPlanData)).setText(data);
        ((TextView) card.findViewById(R.id.txtPlanPrice)).setText(price);
        ((TextView) card.findViewById(R.id.txtPlanPeriod)).setText(period);
        ((TextView) card.findViewById(R.id.txtPlanBadge)).setText(badge);
        ((TextView) card.findViewById(R.id.txtPlanRating)).setText("★ " + rating);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                dp(154), dp(192));
        params.rightMargin = dp(12);
        container.addView(card, params);
        card.setOnClickListener(v -> navigateToTab(R.id.nav_buy));
    }

    private int dp(float size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }

    private void navigateToTab(int id) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateToTab(id);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        handler.removeCallbacks(refreshTask);
        if (getView() != null) {
            handler.post(refreshTask);
        }
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(refreshTask);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacks(refreshTask);
        super.onDestroyView();
    }
}
