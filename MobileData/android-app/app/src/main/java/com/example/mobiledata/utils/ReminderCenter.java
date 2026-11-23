package com.example.mobiledata.utils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import com.example.mobiledata.R;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Cài đặt mốc nhắc và lịch sử thông báo của thuê bao hiện tại. */
public final class ReminderCenter {
    private ReminderCenter() { }

    private static final String[] OPTIONS = {
        "Bật thông báo nhắc hạn thật",
        "Nhắc trước 3 ngày (gói dài ngày)",
        "Nhắc trước 24 giờ (gói từ 2 ngày)",
        "Nhắc trước 1 giờ",
        "Nhắc trước 5 phút",
        "Thông báo khi đã hết hạn",
        "Còn dưới 20% dung lượng*",
        "Còn dưới 5% dung lượng*"
    };

    public static void showSettings(Context context, JSONObject activePlan) {
        boolean[] values = new boolean[OPTIONS.length];
        for (int i = 0; i < OPTIONS.length; i++) {
            values[i] = ExpiryReminderManager.isStageSelected(context, i - 1);
        }
        new AlertDialog.Builder(context, R.style.MobileDataLightDialog)
                .setTitle("Tùy chỉnh thông báo")
                .setMultiChoiceItems(OPTIONS, values, (dialog, index, value) -> values[index] = value)
                .setNegativeButton("Bỏ qua", null)
                .setPositiveButton("Lưu", (dialog, which) -> {
                    ExpiryReminderManager.saveSettings(context, values);
                    if (activePlan != null) ExpiryReminderManager.sync(context, activePlan);
                    Toast.makeText(context, "Đã cập nhật lịch nhắc", Toast.LENGTH_SHORT).show();
                }).show();
    }

    /** Tùy chỉnh đoạn chữ Demo 1 giờ ngay trên điện thoại để tiện bảo vệ đồ án. */
    public static void showDemoEditor(Context context) {
        EditText editor = new EditText(context);
        editor.setText(ExpiryReminderManager.getDemoMessage(context));
        editor.setTextColor(Color.rgb(21, 51, 92));
        editor.setTextSize(15);
        editor.setMinLines(4);
        editor.setMaxLines(8);
        editor.setHint("Nội dung thông báo Demo...");
        int px = dp(context, 12);
        editor.setPadding(px, px, px, px);
        new AlertDialog.Builder(context, R.style.MobileDataLightDialog)
                .setTitle("Sửa thông báo Demo 1 giờ")
                .setMessage("Dùng {GOI} để tự điền mã gói cước. Chỉ thay nội dung Demo, không đổi nhắc thật.")
                .setView(editor)
                .setNegativeButton("Đóng", null)
                .setPositiveButton("Lưu", (d, which) -> {
                    String value = editor.getText().toString().trim();
                    if (value.isEmpty() || value.length() > 600) {
                        Toast.makeText(context, "Nội dung cần 1–600 ký tự", Toast.LENGTH_LONG).show();
                        return;
                    }
                    ExpiryReminderManager.saveDemoMessage(context, value);
                    Toast.makeText(context, "Đã lưu nội dung thông báo Demo", Toast.LENGTH_SHORT).show();
                }).show();
    }

    public static void showHistory(Context context) {
        JSONArray events = ExpiryReminderManager.getHistory(context);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(context, 16);
        list.setPadding(padding, padding, padding, padding);
        if (events.length() == 0) {
            TextView empty = label(context, "Chưa có thông báo nào trên thiết bị này.", 14, false);
            list.addView(empty);
        }
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm", new Locale("vi", "VN"));
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null) continue;
            LinearLayout card = new LinearLayout(context);
            card.setPadding(padding, padding, padding, padding);
            card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE); bg.setCornerRadius(dp(context, 12));
            card.setBackground(bg);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.bottomMargin = dp(context, 10);
            list.addView(card, cp);
            TextView title = label(context, event.optString("title"), 15, true);
            card.addView(title);
            String time = format.format(new Date(event.optLong("time", 0)));
            TextView subtitle = label(context,
                    (event.optBoolean("demo") ? "DEMO • " : "Nhắc thực tế • ") + time, 12, false);
            subtitle.setTextColor(Color.rgb(95, 115, 143));
            subtitle.setPadding(0, dp(context, 4), 0, dp(context, 6));
            card.addView(subtitle);
            card.addView(label(context, event.optString("body"), 13, false));
        }
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(Color.rgb(244, 247, 252));
        scroll.addView(list);
        new AlertDialog.Builder(context, R.style.MobileDataLightDialog)
                .setTitle("Lịch sử nhắc nhở (" + events.length() + ")")
                .setView(scroll)
                .setNegativeButton("Đóng", null)
                .setNeutralButton("Xóa lịch sử", (d, w) -> new AlertDialog.Builder(
                        context, R.style.MobileDataLightDialog)
                        .setTitle("Xóa lịch sử trên máy?")
                        .setMessage("Việc này không xóa thông báo khỏi hệ thống hoặc thay đổi lịch nhắc thật.")
                        .setNegativeButton("Hủy", null)
                        .setPositiveButton("Xóa", (d2, w2) -> {
                            ExpiryReminderManager.clearHistory(context);
                            Toast.makeText(context, "Đã xóa lịch sử thông báo", Toast.LENGTH_SHORT).show();
                        }).show())
                .show();
    }

    private static TextView label(Context c, String value, int size, boolean bold) {
        TextView v = new TextView(c);
        v.setText(value); v.setTextSize(size); v.setTextColor(Color.rgb(21, 51, 92));
        if (bold) v.setTypeface(null, Typeface.BOLD);
        return v;
    }
    private static int dp(Context c, int n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
}
