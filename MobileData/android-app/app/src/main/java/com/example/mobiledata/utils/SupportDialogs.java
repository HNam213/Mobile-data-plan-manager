package com.example.mobiledata.utils;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.mobiledata.R;

import org.json.JSONArray;
import org.json.JSONObject;

/** Hộp thư hỗ trợ của USER. Không cần Activity/Manifest mới. */
public final class SupportDialogs {
    private static final int NAVY = Color.rgb(21, 51, 92);
    private static final int BLUE = Color.rgb(36, 99, 235);
    private SupportDialogs() { }

    public static void open(Context context) {
        String token = new SessionManager(context).getAccessToken();
        LinearLayout layout = vertical(context);
        layout.addView(label(context, "Đang tải yêu cầu...", 14, false));
        AlertDialog dialog = new AlertDialog.Builder(context, R.style.MobileDataLightDialog)
                .setTitle("Hỗ trợ khách hàng")
                .setView(scroll(context, layout))
                .setNegativeButton("Đóng", null)
                .setPositiveButton("Gửi yêu cầu", (d, w) -> create(context, token))
                .create();
        final Runnable[] unsubscribe = {null};
        final Runnable[] reload = {null};
        reload[0] = () -> {
            if (!dialog.isShowing()) return;
            MobileDataBusinessClient.get("/api/me/support", token, new MobileDataBusinessClient.Callback() {
                @Override public void onSuccess(JSONObject result) {
                    if (!dialog.isShowing()) return;
                    JSONArray requests = MobileDataBusinessClient.items(result);
                    layout.removeAllViews();
                    layout.addView(label(context, requests.length() + " yêu cầu của bạn", 14, false));
                    if (requests.length() == 0)
                        layout.addView(label(context, "Chưa có yêu cầu nào.", 14, false));
                    for (int i = 0; i < requests.length(); i++) {
                        JSONObject t = requests.optJSONObject(i);
                        if (t == null) continue;
                        TextView item = label(context, "#" + t.optLong("id") + " • " +
                                t.optString("title") + "\n" + state(t.optString("status")) +
                                " • " + t.optString("updatedAt", "").replace('T', ' ').split("\\.")[0],
                                15, true);
                        item.setBackground(panel());
                        item.setPadding(dp(context, 14), dp(context, 12), dp(context, 14), dp(context, 12));
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                        lp.topMargin = dp(context, 10);
                        layout.addView(item, lp);
                        item.setOnClickListener(v -> detail(context, token, t.optLong("id"), reload[0]));
                    }
                }
                @Override public void onError(String message) {
                    if (dialog.isShowing()) toast(context, message);
                }
            });
        };
        dialog.setOnShowListener(d -> {
            reload[0].run();
            unsubscribe[0] = SupportRealtimeClient.watchTicket(0L, reload[0]);
        });
        dialog.setOnDismissListener(d -> {
            if (unsubscribe[0] != null) unsubscribe[0].run();
        });
        dialog.show();
    }

    private static void create(Context context, String token) {
        LinearLayout form = vertical(context);
        EditText title = edit(context, "Tiêu đề yêu cầu", false);
        EditText message = edit(context, "Mô tả vấn đề cần hỗ trợ", true);
        form.addView(title); form.addView(message);
        AlertDialog dialog = new AlertDialog.Builder(context, R.style.MobileDataLightDialog)
                .setTitle("Gửi yêu cầu hỗ trợ")
                .setView(scroll(context, form))
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Gửi", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String t = title.getText().toString().trim();
            String m = message.getText().toString().trim();
            if (!MobileDataInputRules.supportTitle(title)
                    || !MobileDataInputRules.supportMessage(message)) return;
            try {
                JSONObject body = new JSONObject(); body.put("title", t); body.put("message", m);
                MobileDataBusinessClient.post("/api/me/support", body, token, new MobileDataBusinessClient.Callback() {
                    @Override public void onSuccess(JSONObject result) {
                        dialog.dismiss();
                        toast(context, "Đã gửi tới quản trị viên.");
                        long id = result.optLong("ticketId", 0);
                        if (id > 0) detail(context, token, id, null);
                        else open(context);
                    }
                    @Override public void onError(String error) { toast(context, error); }
                });
            } catch (Exception ignored) { toast(context, "Không thể tạo yêu cầu."); }
        }));
        dialog.show();
    }

    private static void detail(Context context, String token, long id, Runnable onChange) {
        if (!(context instanceof android.app.Activity)) {
            toast(context, "Không thể mở cuộc hội thoại.");
            return;
        }
        SupportConversationDialog.open((android.app.Activity) context, token, id, false, onChange);
    }

    public static String state(String value) {
        switch (value) {
            case "OPEN": return "Chờ xử lý";
            case "ANSWERED": return "Đã phản hồi";
            case "CLOSED": return "Đã hoàn tất";
            default: return value;
        }
    }
    private static LinearLayout vertical(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(context, 18), dp(context, 10), dp(context, 18), dp(context, 16));
        layout.setBackgroundColor(Color.WHITE);
        return layout;
    }
    private static ScrollView scroll(Context context, LinearLayout inner) {
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.addView(inner);
        return scroll;
    }
    private static TextView label(Context c, String text, int size, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(text); tv.setTextColor(NAVY); tv.setTextSize(size);
        if (bold) tv.setTypeface(null, Typeface.BOLD);
        tv.setLineSpacing(dp(c, 3), 1);
        return tv;
    }
    private static EditText edit(Context context, String hint, boolean multiline) {
        EditText edit = new EditText(context);
        edit.setHint(hint); edit.setTextColor(NAVY);
        edit.setHintTextColor(Color.rgb(102, 119, 145));
        edit.setTextSize(16); edit.setTypeface(null, Typeface.BOLD);
        edit.setInputType(multiline ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                : InputType.TYPE_CLASS_TEXT);
        edit.setMinHeight(dp(context, multiline ? 100 : 56));
        if (multiline) {
            MobileDataInputRules.multiline(edit, 2000);
            edit.setGravity(android.view.Gravity.TOP);
        } else MobileDataInputRules.text(edit, 160);
        return edit;
    }
    private static GradientDrawable panel() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.rgb(241, 246, 255)); g.setCornerRadius(18);
        return g;
    }
    private static int dp(Context context, int val) {
        return Math.round(val * context.getResources().getDisplayMetrics().density);
    }
    private static void toast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }
}
