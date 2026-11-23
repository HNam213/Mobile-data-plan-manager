package com.example.mobiledata.utils;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.WindowManager;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.mobiledata.R;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Cua so hoi thoai ho tro dung chung cho USER va ADMIN.
 * Noi dung tu tai lai khi nhan su kien SSE cho dung ma yeu cau;
 * khong can dong va mo lai hop thoai.
 */
public final class SupportConversationDialog {
    private static final int NAVY = Color.rgb(21, 51, 92);
    private static final int BLUE = Color.rgb(36, 99, 235);
    private static final int SECONDARY = Color.rgb(100, 116, 139);
    private static final long FALLBACK_REFRESH_MS = 10_000L;

    private final Activity activity;
    private final String token;
    private final long ticketId;
    private final boolean admin;
    private final Runnable afterChange;
    private final Handler main = new Handler(Looper.getMainLooper());
    private AlertDialog dialog;
    private TextView heading;
    private TextView stateText;
    private LinearLayout messages;
    private ScrollView messagesScroll;
    private EditText input;
    private Button send;
    private Runnable stopListening;
    private boolean closed;
    private boolean fetching;
    private boolean fetchAgain;
    private boolean sending;
    // Nhan su kien lap lai nhung khong tao lai cac bong bong khi du lieu khong doi.
    private String renderedMessagesJson = null;

    private final Runnable periodicCheck = new Runnable() {
        @Override public void run() {
            if (closed) return;
            // SSE cap nhat ngay. Neu SSE tam mat ket noi thi thu lai theo chu ky.
            if (!SupportRealtimeClient.isConnected()) refresh();
            main.postDelayed(this, FALLBACK_REFRESH_MS);
        }
    };

    private SupportConversationDialog(Activity activity, String token, long ticketId,
                                      boolean admin, Runnable afterChange) {
        this.activity = activity;
        this.token = token;
        this.ticketId = ticketId;
        this.admin = admin;
        this.afterChange = afterChange;
    }

    public static void open(Activity activity, String token, long ticketId,
                            boolean admin, Runnable afterChange) {
        if (activity == null || activity.isFinishing()) return;
        if (token == null || token.trim().isEmpty() || ticketId <= 0) return;
        new SupportConversationDialog(activity, token, ticketId, admin, afterChange).show();
    }

    private void show() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(8), dp(18), dp(8));
        root.setBackgroundColor(Color.WHITE);

        heading = label("Đang tải thông tin...", 14, false);
        heading.setTextColor(SECONDARY);
        root.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        stateText = label("", 13, true);
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(-1, -2);
        stateParams.topMargin = dp(6);
        root.addView(stateText, stateParams);

        messages = new LinearLayout(activity);
        messages.setOrientation(LinearLayout.VERTICAL);
        messages.setPadding(dp(4), dp(10), dp(4), dp(14));
        messagesScroll = new ScrollView(activity);
        messagesScroll.setFillViewport(true);
        // ScrollView khong duoc lay focus tu o nhap trong luc bo go dang ghep dau.
        messagesScroll.setFocusable(false);
        messagesScroll.setFocusableInTouchMode(false);
        messagesScroll.addView(messages);
        int displayHeight = activity.getResources().getDisplayMetrics().heightPixels;
        int height = Math.min(dp(315), Math.max(dp(185), displayHeight * 42 / 100));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, height);
        scrollParams.topMargin = dp(8);
        root.addView(messagesScroll, scrollParams);

        LinearLayout composer = new LinearLayout(activity);
        composer.setOrientation(LinearLayout.HORIZONTAL);
        composer.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams composerParams = new LinearLayout.LayoutParams(-1, -2);
        composerParams.topMargin = dp(6);
        root.addView(composer, composerParams);

        input = new EditText(activity);
        input.setHint("Nhập tin nhắn...");
        input.setTextSize(15);
        input.setTextColor(NAVY);
        input.setHintTextColor(SECONDARY);
        // Khong doi qua lai singleLine / inputType: tranh loi bo go tieng Viet.
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_FLAG_NO_FULLSCREEN);
        input.setMinLines(2);
        input.setMaxLines(4);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setFilters(new InputFilter[] { new InputFilter.LengthFilter(2000) });
        composer.addView(input, new LinearLayout.LayoutParams(0, -2, 1f));

        send = new Button(activity);
        send.setText("Gửi");
        send.setAllCaps(false);
        send.setTextColor(Color.WHITE);
        send.setTextSize(14);
        send.setBackgroundTintList(ColorStateList.valueOf(BLUE));
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(76), dp(52));
        sendParams.leftMargin = dp(8);
        composer.addView(send, sendParams);
        send.setOnClickListener(v -> sendMessage());

        AlertDialog.Builder builder = new AlertDialog.Builder(activity, R.style.MobileDataLightDialog)
                .setTitle("Yêu cầu hỗ trợ #" + ticketId)
                .setView(root)
                .setNegativeButton("Đóng", null);
        if (admin) builder.setNeutralButton("Hoàn tất", null);
        dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            }
            if (admin) {
                Button complete = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                if (complete != null) complete.setOnClickListener(v -> completeTicket());
            }
            stopListening = SupportRealtimeClient.watchTicket(ticketId, this::refresh);
            refresh();
            main.postDelayed(periodicCheck, FALLBACK_REFRESH_MS);
        });
        dialog.setOnDismissListener(ignored -> {
            closed = true;
            main.removeCallbacks(periodicCheck);
            if (stopListening != null) {
                stopListening.run();
                stopListening = null;
            }
        });
        dialog.show();
    }

    private void refresh() {
        if (closed || activity.isFinishing() || dialog == null || !dialog.isShowing()) return;
        if (fetching) { fetchAgain = true; return; }
        fetching = true;
        String endpoint = (admin ? "/api/admin/support/" : "/api/me/support/") + ticketId;
        MobileDataBusinessClient.get(endpoint, token, new MobileDataBusinessClient.Callback() {
            @Override public void onSuccess(JSONObject result) {
                fetching = false;
                if (!closed && !activity.isFinishing() && dialog.isShowing()) {
                    render(result);
                    if (fetchAgain) {
                        fetchAgain = false;
                        refresh();
                    }
                }
            }
            @Override public void onError(String error) {
                fetching = false;
                fetchAgain = false;
                if (!closed && dialog.isShowing())
                    Toast.makeText(activity, error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void render(JSONObject response) {
        JSONObject ticket = response.optJSONObject("ticket");
        if (ticket == null) {
            heading.setText("Không tìm thấy thông tin yêu cầu.");
            return;
        }
        String title = ticket.optString("title", "");
        dialog.setTitle("#" + ticketId + " • " + title);
        if (admin) {
            String customer = ticket.optString("fullName", "Khách hàng");
            heading.setText(customer + " • " + ticket.optString("phone") + " • " +
                    ticket.optString("carrierName"));
        } else {
            heading.setText("Trao đổi với bộ phận hỗ trợ");
        }
        String status = ticket.optString("status");
        stateText.setText("Trạng thái: " + SupportDialogs.state(status));
        if (admin) {
            Button complete = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (complete != null) complete.setEnabled(!"CLOSED".equals(status));
        }
        JSONArray list = response.optJSONArray("messages");
        String currentMessagesJson = list == null ? "[]" : list.toString();
        if (currentMessagesJson.equals(renderedMessagesJson)) return;
        renderedMessagesJson = currentMessagesJson;
        messages.removeAllViews();
        if (list == null || list.length() == 0) {
            TextView empty = label("Chưa có tin nhắn.", 14, false);
            empty.setTextColor(SECONDARY);
            messages.addView(empty);
        } else {
            for (int i = 0; i < list.length(); i++) {
                JSONObject msg = list.optJSONObject(i);
                if (msg == null) continue;
                boolean fromAdmin = "ADMIN".equalsIgnoreCase(msg.optString("senderRole"));
                boolean mine = fromAdmin == admin;
                TextView bubble = label((mine ? "Bạn" : (admin ? "Khách hàng" : "Quản trị viên"))
                        + "\n" + msg.optString("message"), 15, false);
                bubble.setTextColor(mine ? Color.WHITE : NAVY);
                bubble.setPadding(dp(13), dp(10), dp(13), dp(10));
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(mine ? BLUE : Color.rgb(239, 244, 252));
                bg.setCornerRadius(dp(15));
                bubble.setBackground(bg);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = dp(8);
                if (mine) lp.leftMargin = dp(47);
                else lp.rightMargin = dp(47);
                messages.addView(bubble, lp);
            }
        }
        messagesScroll.post(() -> {
            if (!closed) {
                // Chi cuon tin nhan. fullScroll(FOCUS_DOWN) co the chuyen focus,
                // khien LDPlayer/bo go tieng Viet mat trang thai dang ghep dau.
                messagesScroll.scrollTo(0, Math.max(0,
                        messages.getBottom() - messagesScroll.getHeight()));
            }
        });
    }

    private void sendMessage() {
        if (closed || sending) return;
        if (!MobileDataInputRules.supportMessage(input)) return;
        String text = input.getText().toString().trim();
        sending = true;
        send.setEnabled(false);
        String endpoint = (admin ? "/api/admin/support/" : "/api/me/support/")
                + ticketId + "/reply";
        try {
            JSONObject body = new JSONObject();
            body.put("message", text);
            MobileDataBusinessClient.post(endpoint, body, token, new MobileDataBusinessClient.Callback() {
                @Override public void onSuccess(JSONObject result) {
                    sending = false;
                    if (closed) return;
                    // Neu nguoi dung da go tiep khi dang gui, khong xoa ban nhap moi.
                    if (text.equals(input.getText().toString().trim())) input.setText("");
                    send.setEnabled(true);
                    refresh();
                    if (afterChange != null) afterChange.run();
                }
                @Override public void onError(String error) {
                    sending = false;
                    if (closed) return;
                    send.setEnabled(true);
                    Toast.makeText(activity, error, Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            sending = false;
            send.setEnabled(true);
            Toast.makeText(activity, "Không thể gửi tin nhắn.", Toast.LENGTH_SHORT).show();
        }
    }

    private void completeTicket() {
        if (closed || !admin) return;
        Button complete = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        if (complete != null) complete.setEnabled(false);
        try {
            JSONObject body = new JSONObject();
            body.put("status", "CLOSED");
            MobileDataBusinessClient.patch("/api/admin/support/" + ticketId + "/status",
                    body, token, new MobileDataBusinessClient.Callback() {
                        @Override public void onSuccess(JSONObject result) {
                            if (closed) return;
                            refresh();
                            if (afterChange != null) afterChange.run();
                        }
                        @Override public void onError(String error) {
                            if (closed) return;
                            if (complete != null) complete.setEnabled(true);
                            Toast.makeText(activity, error, Toast.LENGTH_SHORT).show();
                        }
                    });
        } catch (Exception e) {
            if (complete != null) complete.setEnabled(true);
        }
    }

    private TextView label(String value, int sp, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(NAVY);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
