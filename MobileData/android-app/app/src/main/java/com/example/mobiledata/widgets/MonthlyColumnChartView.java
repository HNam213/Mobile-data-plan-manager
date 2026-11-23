package com.example.mobiledata.widgets;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Biểu đồ nạp tiền và mua gói theo 12 tháng.
 *
 * Dùng các View Android thông thường thay cho Canvas / software layer,
 * giúp hiển thị ổn định hơn trên thiết bị thật và các máy giả lập LDPlayer.
 * API công khai giữ nguyên: new MonthlyColumnChartView(context), setMonths(json).
 */
public final class MonthlyColumnChartView extends LinearLayout {
    private static final int BLUE = Color.rgb(38, 100, 230);
    private static final int GREEN = Color.rgb(25, 168, 131);
    private static final int NAVY = Color.rgb(21, 51, 92);
    private static final int LABEL = Color.rgb(107, 124, 151);
    private static final int GRID = Color.rgb(226, 235, 248);
    private static final int MONTH_COUNT = 12;
    private static final int BAR_AREA_DP = 178;

    private final double[] topup = new double[MONTH_COUNT];
    private final double[] spent = new double[MONTH_COUNT];

    public MonthlyColumnChartView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setPadding(dp(8), dp(8), dp(8), dp(6));
        setBackgroundColor(Color.WHITE);
        setMinimumWidth(dp(790));
        setMinimumHeight(dp(300));
        buildChart();
    }

    public void setMonths(JSONArray array) {
        for (int i = 0; i < MONTH_COUNT; i++) {
            topup[i] = 0;
            spent[i] = 0;
        }
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                JSONObject month = array.optJSONObject(i);
                if (month == null) continue;
                int index = month.optInt("month", -1) - 1;
                if (index < 0 || index >= MONTH_COUNT) continue;
                topup[index] = safeAmount(month.optDouble("topup", 0));
                spent[index] = safeAmount(month.optDouble("spent", 0));
            }
        }
        buildChart();
    }

    private void buildChart() {
        removeAllViews();
        double highest = 0;
        for (int i = 0; i < MONTH_COUNT; i++) {
            highest = Math.max(highest, Math.max(topup[i], spent[i]));
        }
        final double axisMax = niceCeiling(highest);

        LinearLayout legend = new LinearLayout(getContext());
        legend.setGravity(Gravity.CENTER_VERTICAL);
        legend.setOrientation(HORIZONTAL);
        LayoutParams legendLp = new LayoutParams(LayoutParams.MATCH_PARENT, dp(28));
        legendLp.bottomMargin = dp(6);
        addView(legend, legendLp);
        addLegend(legend, GREEN, "Tiền nạp");
        addLegend(legend, BLUE, "Tiền mua gói");

        LinearLayout chartRow = new LinearLayout(getContext());
        chartRow.setOrientation(HORIZONTAL);
        chartRow.setGravity(Gravity.TOP);
        addView(chartRow, new LayoutParams(LayoutParams.MATCH_PARENT, dp(215)));

        // Trục giá trị: kể cả dữ liệu rỗng vẫn hiển thị để phân biệt với lỗi giao diện.
        FrameLayout axis = new FrameLayout(getContext());
        chartRow.addView(axis, new LayoutParams(dp(61), dp(BAR_AREA_DP)));
        for (int step = 0; step <= 4; step++) {
            double amount = axisMax * (4 - step) / 4d;
            TextView value = label(shortMoney(amount), 10, LABEL, false);
            value.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(56), dp(18));
            p.topMargin = dp(Math.round((BAR_AREA_DP - 18) * step / 4f));
            axis.addView(value, p);
        }

        FrameLayout plotting = new FrameLayout(getContext());
        chartRow.addView(plotting, new LayoutParams(dp(12 * 58), dp(215)));

        // Đường kẻ nằm SAU cột (không dùng Canvas tùy chỉnh).
        for (int step = 0; step <= 4; step++) {
            View line = new View(getContext());
            line.setBackgroundColor(GRID);
            FrameLayout.LayoutParams lineLp = new FrameLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, dp(1));
            lineLp.topMargin = dp(Math.round((BAR_AREA_DP - 1) * step / 4f));
            plotting.addView(line, lineLp);
        }

        LinearLayout bars = new LinearLayout(getContext());
        bars.setOrientation(HORIZONTAL);
        FrameLayout.LayoutParams barsLp = new FrameLayout.LayoutParams(dp(12 * 58), dp(214));
        plotting.addView(bars, barsLp);

        for (int month = 0; month < MONTH_COUNT; month++) {
            final int monthNumber = month + 1;
            LinearLayout column = new LinearLayout(getContext());
            column.setOrientation(VERTICAL);
            column.setGravity(Gravity.CENTER_HORIZONTAL);
            bars.addView(column, new LayoutParams(dp(58), LayoutParams.MATCH_PARENT));

            FrameLayout barSpace = new FrameLayout(getContext());
            column.addView(barSpace, new LayoutParams(dp(58), dp(BAR_AREA_DP)));

            addBar(barSpace, 10, topup[month], axisMax, GREEN);
            addBar(barSpace, 32, spent[month], axisMax, BLUE);

            TextView monthText = label("T" + monthNumber, 12, NAVY, true);
            monthText.setGravity(Gravity.CENTER);
            column.addView(monthText, new LayoutParams(LayoutParams.MATCH_PARENT, dp(29)));

            column.setOnClickListener(v -> Toast.makeText(getContext(),
                    "Tháng " + monthNumber + "  •  Nạp: " + money(topup[monthNumber - 1])
                            + "  •  Mua gói: " + money(spent[monthNumber - 1]),
                    Toast.LENGTH_SHORT).show());
        }

        if (highest <= 0) {
            TextView empty = label("Chưa có giao dịch thu chi trong năm này.", 12, LABEL, false);
            empty.setGravity(Gravity.CENTER);
            LayoutParams ep = new LayoutParams(LayoutParams.MATCH_PARENT, dp(24));
            ep.topMargin = dp(4);
            addView(empty, ep);
        }
    }

    private void addBar(FrameLayout parent, int leftDp, double amount,
                        double maximum, int color) {
        int heightPx = amount <= 0 ? 0 : Math.max(dp(3),
                Math.round((float) (amount / maximum) * dp(BAR_AREA_DP - 8)));
        View bar = new View(getContext());
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadii(new float[]{dp(4), dp(4), dp(4), dp(4), 0, 0, 0, 0});
        bar.setBackground(background);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(15), heightPx,
                Gravity.BOTTOM | Gravity.LEFT);
        p.leftMargin = dp(leftDp);
        parent.addView(bar, p);
    }

    private void addLegend(LinearLayout parent, int color, String text) {
        View dot = new View(getContext());
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(3));
        dot.setBackground(shape);
        LinearLayout.LayoutParams dp = new LayoutParams(dp(12), dp(12));
        dp.leftMargin = dp(12);
        dp.rightMargin = dp(6);
        parent.addView(dot, dp);
        TextView caption = label(text, 12, NAVY, false);
        parent.addView(caption);
    }

    private TextView label(String text, int sizeSp, int color, boolean bold) {
        TextView t = new TextView(getContext());
        t.setText(text);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private static double safeAmount(double amount) {
        return (Double.isNaN(amount) || Double.isInfinite(amount)) ? 0 : Math.max(0, amount);
    }

    private static double niceCeiling(double value) {
        if (value <= 0) return 100000;
        double mag = Math.pow(10, Math.floor(Math.log10(value)));
        double units = value / mag;
        return (units <= 1 ? 1 : units <= 2 ? 2 : units <= 5 ? 5 : 10) * mag;
    }

    private static String shortMoney(double value) {
        if (value >= 1_000_000) return String.format(Locale.US, "%.1ftr", value / 1_000_000);
        if (value >= 1_000) return String.format(Locale.US, "%.0fk", value / 1_000);
        return String.format(Locale.US, "%.0f", value);
    }

    private static String money(double amount) {
        return NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(amount) + "đ";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
