package com.example.mobiledata.activities;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import com.example.mobiledata.widgets.MonthlyColumnChartView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.mobiledata.utils.MobileDataBusinessClient;
import com.example.mobiledata.utils.SessionManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Locale;

/** Thống kê rõ ràng theo năm và từng tháng; dữ liệu từ SQL Server. */
public class StatisticsActivity extends AppCompatActivity {
    private static final int NAVY = Color.rgb(21,51,92);
    private static final int BLUE = Color.rgb(36,99,235);
    private static final int MUTED = Color.rgb(98,116,139);
    private LinearLayout root, content;
    private int year = Calendar.getInstance().get(Calendar.YEAR);
    private int requestVersion;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(245,248,253));
        root = new LinearLayout(this);
        root.setPadding(dp(16), dp(26), dp(16), dp(24));
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root);
        setContentView(scroll);
        load();
    }

    private void load() {
        root.removeAllViews();
        title(root, "Thống kê chi tiêu", 24);
        text(root, "Biểu đồ giao dịch theo từng tháng", 13, MUTED, false);
        LinearLayout picker = horizontal();
        picker.setPadding(0, dp(16), 0, dp(12)); root.addView(picker);
        Button prev = button("‹  Năm trước");
        prev.setOnClickListener(v -> { year--; load(); });
        picker.addView(prev, new LinearLayout.LayoutParams(0, dp(48), 1));
        TextView yearText = text(picker, String.valueOf(year), 19, NAVY, true);
        yearText.setGravity(Gravity.CENTER);
        yearText.setLayoutParams(new LinearLayout.LayoutParams(0, dp(48), .8f));
        Button next = button("Năm sau  ›");
        next.setEnabled(year < Calendar.getInstance().get(Calendar.YEAR));
        next.setOnClickListener(v -> { year++; load(); });
        picker.addView(next, new LinearLayout.LayoutParams(0, dp(48), 1));
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content);
        text(content, "Đang tải thống kê...", 15, MUTED, false);
        final int currentRequest = ++requestVersion;
        String token = new SessionManager(this).getAccessToken();
        MobileDataBusinessClient.get("/api/me/stats?year=" + year, token, new MobileDataBusinessClient.Callback() {
            @Override public void onSuccess(JSONObject data) {
                if (isFinishing() || currentRequest != requestVersion) return;
                render(data);
            }
            @Override public void onError(String msg) {
                if (!isFinishing() && currentRequest == requestVersion) {
                    content.removeAllViews(); text(content, msg, 15, Color.RED, false);
                }
            }
        });
    }

    private void render(JSONObject data) {
        content.removeAllViews();
        LinearLayout first = horizontal();
        content.addView(first);
        statCard(first, "Tổng tiền đã nạp", money(data.optDouble("totalTopup")), Color.rgb(19,151,110), 0);
        statCard(first, "Đã chi mua gói", money(data.optDouble("totalSpent")), BLUE, 8);
        LinearLayout second = horizontal();
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(-1,-2);
        secondParams.topMargin = dp(10); content.addView(second, secondParams);
        statCard(second, "Lượt đăng ký", String.valueOf(data.optInt("purchases")), BLUE, 0);
        statCard(second, "Lượt hủy gói", String.valueOf(data.optInt("cancellations")), Color.rgb(209,136,28), 8);

        TextView monthly = title(content, "Biểu đồ thu chi theo tháng", 20);
        monthly.setPadding(0, dp(24), 0, dp(11));
        JSONArray months = data.optJSONArray("months");
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(10), dp(8), dp(10), dp(14));
        card.setBackground(rounded(Color.WHITE, 18));
        content.addView(card);
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        card.addView(horizontal);
        MonthlyColumnChartView chart = new MonthlyColumnChartView(this);
        chart.setMonths(months);
        horizontal.addView(chart, new android.widget.FrameLayout.LayoutParams(dp(790), dp(300)));
        text(content, "Vuốt biểu đồ sang ngang để xem đủ 12 tháng.", 12, MUTED, false)
                .setPadding(0, dp(10), 0, dp(12));
    }

    private void statCard(LinearLayout row, String label, String value, int tint, int leftMargin) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(12), dp(16));
        card.setBackground(rounded(Color.WHITE, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(104), 1);
        lp.leftMargin = dp(leftMargin);
        row.addView(card, lp);
        text(card, label, 12, MUTED, false);
        TextView metric = text(card, value, 18, tint, true);
        metric.setPadding(0, dp(9), 0, 0);
    }

    private void monthCard(JSONObject month, double max) {
        double spent = month.optDouble("spent");
        double topup = month.optDouble("topup");
        int purchases = month.optInt("purchases");
        int cancels = month.optInt("cancellations");
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(12), dp(15), dp(13));
        card.setBackground(rounded(Color.WHITE, 14));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1,-2);
        cp.bottomMargin = dp(9); content.addView(card, cp);
        LinearLayout header = horizontal(); card.addView(header);
        TextView name = text(header, "Tháng " + month.optInt("month"), 15, NAVY, true);
        name.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
        TextView sum = text(header, "Chi " + money(spent), 14, BLUE, true);
        sum.setGravity(Gravity.END);
        TextView detail = text(card, "Nạp: " + money(topup) + "   •   Mua: " + purchases + "   •   Hủy: " + cancels,
                13, MUTED, false);
        detail.setPadding(0, dp(7), 0, dp(10));
        LinearLayout track = horizontal();
        track.setBackground(rounded(Color.rgb(231,238,250), 5));
        card.addView(track, new LinearLayout.LayoutParams(-1, dp(7)));
        android.view.View fill = new android.view.View(this);
        fill.setBackground(rounded(BLUE, 5));
        float ratio = (float)Math.max(0, Math.min(1, spent / max));
        track.addView(fill, new LinearLayout.LayoutParams(0, dp(7), ratio));
        track.addView(new android.view.View(this), new LinearLayout.LayoutParams(0, dp(7), 1-ratio));
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s); b.setTextColor(BLUE); b.setAllCaps(false); b.setTextSize(12);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(230,238,252)));
        return b;
    }
    private TextView title(LinearLayout parent, String s, int size) { return text(parent, s, size, NAVY, true); }
    private TextView text(LinearLayout parent, String s, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        parent.addView(t); return t;
    }
    private LinearLayout horizontal() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private GradientDrawable rounded(int color, int radius) { GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(radius)); return g; }
    private String money(double n) { return NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(n) + "đ"; }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
