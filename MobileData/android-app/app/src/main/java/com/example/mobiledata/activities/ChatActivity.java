package com.example.mobiledata.activities;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputFilter;
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
import com.example.mobiledata.utils.MobileDataBusinessClient;
import com.example.mobiledata.utils.OllamaChatClient;
import com.example.mobiledata.utils.SessionManager;
import com.example.mobiledata.utils.SupportDialogs;
import org.json.JSONObject;
import org.json.JSONArray;

/** Chat AI chỉ hỗ trợ tư vấn; chuyển sang hộp thư ADMIN qua API hỗ trợ sẵn có. */
public class ChatActivity extends Activity {
    private EditText input;
    private Button sendButton, handoffButton;
    private TextView status, typing;
    private LinearLayout messages;
    private ScrollView scroll;
    private SessionManager session;
    private final StringBuilder conversation = new StringBuilder();
    private boolean busy = false;
    private boolean handedOff = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        session = new SessionManager(this);
        if (!session.isLoggedIn() || session.getAccessToken().isEmpty()) {
            Toast.makeText(this, "Vui lòng đăng nhập lại để sử dụng chatbot.", Toast.LENGTH_LONG).show();
            finish(); return;
        }
        setContentView(R.layout.activity_chat);
        input = findViewById(R.id.chatInput);
        sendButton = findViewById(R.id.btnChatSend);
        handoffButton = findViewById(R.id.btnChatHandoff);
        status = findViewById(R.id.chatStatus);
        typing = findViewById(R.id.chatTyping);
        messages = findViewById(R.id.chatMessages);
        scroll = findViewById(R.id.chatScroll);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});
        findViewById(R.id.chatTitle).setOnClickListener(v -> finish());
        sendButton.setOnClickListener(v -> send());
        handoffButton.setOnClickListener(v -> confirmHandoff());
        showMessage("Xin chào! Tôi là trợ lý AI MobileData. Tôi có thể hướng dẫn và tư vấn. " +
                "Nếu cần xử lý trực tiếp, bạn có thể chuyển yêu cầu đến quản trị viên.", false);
    }

    private void send() {
        if (busy) return;
        String question = input.getText().toString().trim();
        if (question.isEmpty()) { input.setError("Vui lòng nhập câu hỏi."); return; }
        input.setText("");
        showMessage(question, true);
        record("Người dùng", question);
        setBusy(true);
        status.setText("Đang phân tích yêu cầu và kiểm tra dữ liệu...");
        OllamaChatClient.send(question, session.getAccessToken(), new OllamaChatClient.Callback() {
            @Override public void onSuccess(JSONObject data) {
                if (isFinishing() || isDestroyed()) return;
                String answer = data.optString("reply", "Không có nội dung trả lời.");
                showMessage(answer, false);
                record("AI/Hệ thống", answer);
                JSONArray plans = data.optJSONArray("plans");
                if (plans != null && plans.length() > 0) showPlanCards(plans);
                else showOptions(data.optJSONArray("options"));
                if (data.optBoolean("handoffSuggested", false)) suggestHandoff();
                else if (!handedOff) {
                    status.setText("Các giao dịch chỉ thực hiện sau khi bạn xác nhận.");
                    handoffButton.setText("Chuyển yêu cầu đến quản trị viên");
                }
                setBusy(false);
            }
            @Override public void onError(String error) {
                if (isFinishing() || isDestroyed()) return;
                showMessage(error + " Bạn có thể chuyển sang quản trị viên.", false);
                suggestHandoff();
                setBusy(false);
            }
        });
    }

    // Hien thi danh sach goi duoi dang the, khong ghep moi thong tin vao mot doan chat dai.
    private void showPlanCards(JSONArray plans) {
        for (int i = 0; i < plans.length(); i++) {
            JSONObject p = plans.optJSONObject(i);
            if (p == null) continue;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE); bg.setCornerRadius(dp(15));
            bg.setStroke(dp(1), Color.rgb(219, 229, 243));
            card.setBackground(bg);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
            cp.setMargins(dp(20), dp(3), dp(4), dp(10));
            messages.addView(card, cp);
            TextView name = label(p.optString("code") + "  ·  " + p.optString("carrier"), 16, true);
            name.setTextColor(Color.rgb(28, 75, 158)); card.addView(name);
            String price = money(p.optDouble("price"));
            TextView info = label("Giá: " + price + "   •   " +
                number(p.optDouble("dataGb")) + " GB (" + p.optString("dataType") + ")", 14, false);
            info.setPadding(0, dp(9), 0, dp(5)); card.addView(info);
            card.addView(label("Thời hạn: " + p.optInt("days") + " ngày", 14, false));
            boolean canRegister = p.optBoolean("canRegister", false);
            if (canRegister) {
                Button buy = new Button(this);
                buy.setText("Đăng ký gói · " + price);
                buy.setAllCaps(false);
                buy.setTextSize(14);
                buy.setTextColor(Color.WHITE);
                buy.setBackground(tint(Color.rgb(36, 91, 222), 12));
                LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(44));
                bp.topMargin = dp(10); card.addView(buy, bp);
                long id = p.optLong("id", 0);
                buy.setOnClickListener(v -> { if (id > 0) prepareAction("buy", id); });
            } else {
                TextView notice = label("Chỉ tham khảo · Nhà mạng khác với thuê bao của bạn", 12, false);
                notice.setTextColor(Color.rgb(107, 116, 133));
                notice.setPadding(0, dp(9), 0, 0); card.addView(notice);
            }
        }
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private TextView label(String text, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text); view.setTextSize(size);
        view.setTextColor(Color.rgb(26, 53, 92));
        view.setLineSpacing(dp(2), 1f);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }
    private GradientDrawable tint(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color); bg.setCornerRadius(dp(radius));
        return bg;
    }
    private String number(double x) {
        if (x == Math.rint(x)) return String.format(java.util.Locale.US, "%.0f", x);
        return String.format(java.util.Locale.US, "%.1f", x);
    }
    private String money(double amount) {
        return String.format(new java.util.Locale("vi", "VN"), "%,.0fđ", amount);
    }

    private void showOptions(JSONArray options) {
        if (options == null) return;
        for (int i = 0; i < options.length(); i++) {
            JSONObject item = options.optJSONObject(i);
            if (item == null) continue;
            String label = item.optString("label");
            String action = item.optString("action");
            long target = item.optLong("targetId", -1);
            if (!(action.equals("buy") || action.equals("cancel")) || target <= 0) continue;
            Button button = new Button(this);
            button.setText(label); button.setAllCaps(false);
            button.setTextColor(Color.WHITE);
            button.setBackground(tint(Color.rgb(36, 91, 222), 12));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(45));
            lp.setMargins(dp(22), dp(3), dp(8), dp(7));
            messages.addView(button, lp);
            button.setOnClickListener(v -> prepareAction(action, target));
        }
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void prepareAction(String action, long target) {
        if (busy) return;
        setBusy(true);
        status.setText("Đang kiểm tra thông tin giao dịch...");
        OllamaChatClient.prepare(action, target, session.getAccessToken(), new OllamaChatClient.Callback() {
            @Override public void onSuccess(JSONObject data) {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                String id = data.optString("confirmationId");
                if (id.isEmpty()) { showMessage("Thiếu mã xác nhận.", false); return; }
                new AlertDialog.Builder(ChatActivity.this, R.style.MobileDataLightDialog)
                        .setTitle("Xác nhận thao tác")
                        .setMessage(data.optString("message", "Bạn có chắc muốn tiếp tục?"))
                        .setNegativeButton("Không", null)
                        .setPositiveButton("Tôi xác nhận", (dialog, which) -> confirmAction(id))
                        .show();
            }
            @Override public void onError(String error) {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false); showMessage(error, false);
            }
        });
    }

    private void confirmAction(String id) {
        setBusy(true);
        status.setText("Đang thực hiện giao dịch mô phỏng...");
        OllamaChatClient.confirm(id, session.getAccessToken(), new OllamaChatClient.Callback() {
            @Override public void onSuccess(JSONObject data) {
                if (isFinishing() || isDestroyed()) return;
                String message = data.optString("message", "Đã xử lý yêu cầu.");
                showMessage(message, false); record("Hệ thống", message);
                status.setText("Kết quả giao dịch đã được backend xác nhận.");
                setBusy(false);
            }
            @Override public void onError(String error) {
                if (isFinishing() || isDestroyed()) return;
                showMessage(error, false);
                status.setText("Giao dịch chưa được xác nhận thành công. Kiểm tra trạng thái gói trước khi thử lại.");
                setBusy(false);
            }
        });
    }

    private void suggestHandoff() {
        status.setText("Chatbot chưa giải quyết được. Bạn có thể chuyển quản trị viên.");
        handoffButton.setText("Chuyển quản trị viên (được đề xuất)");
    }

    private void confirmHandoff() {
        if (handedOff) { SupportDialogs.open(this); return; }
        if (busy) { Toast.makeText(this, "Hãy đợi câu trả lời hiện tại hoàn tất.", Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this, R.style.MobileDataLightDialog)
                .setTitle("Chuyển đến quản trị viên")
                .setMessage("Tạo yêu cầu hỗ trợ kèm nội dung cuộc trò chuyện? " +
                        "Không gửi OTP, CCCD hay mật khẩu trong tin nhắn.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xác nhận", (d,w) -> handoff())
                .show();
    }

    private void handoff() {
        String detail = conversation.length() == 0
                ? "Tôi cần được quản trị viên hỗ trợ." : conversation.toString();
        // Giới hạn nội dung theo giới hạn 2000 ký tự của hệ thống hỗ trợ hiện hữu.
        if (detail.length() > 1800) detail = detail.substring(detail.length() - 1800);
        // Bo thong tin nhay cam neu nguoi dung vo tinh nhap trong hoi thoai.
        detail = redactSensitive(detail);
        final String message = "Chuyển từ chatbot AI:\n" + detail;
        try {
            JSONObject body = new JSONObject();
            body.put("title", "Yêu cầu hỗ trợ từ chatbot AI");
            body.put("message", message);
            handoffButton.setEnabled(false);
            status.setText("Đang tạo yêu cầu hỗ trợ...");
            MobileDataBusinessClient.post("/api/me/support", body,
                    session.getAccessToken(), new MobileDataBusinessClient.Callback() {
                @Override public void onSuccess(JSONObject json) {
                    if (isFinishing() || isDestroyed()) return;
                    handedOff = true;
                    handoffButton.setText("Mở hộp thư hỗ trợ");
                    handoffButton.setEnabled(true);
                    status.setText("Đã chuyển cho quản trị viên. Mã yêu cầu: #" + json.optLong("ticketId"));
                    showMessage("Đã gửi yêu cầu hỗ trợ đến quản trị viên. Bạn có thể theo dõi tại Hộp thư hỗ trợ.", false);
                }
                @Override public void onError(String error) {
                    if (isFinishing() || isDestroyed()) return;
                    handoffButton.setEnabled(true);
                    status.setText(error);
                }
            });
        } catch (Exception e) {
            handoffButton.setEnabled(true);
            status.setText("Không thể tạo yêu cầu hỗ trợ.");
        }
    }

    private static String redactSensitive(String text) {
        // Che OTP gom 6 chu so va chuoi dai co the la token/CCCD.
        String safe = text.replaceAll("(?i)(otp|ma xac nhan)\\s*[:=]?\\s*\\d{4,8}", "$1: [DA AN]");
        safe = safe.replaceAll("\\b\\d{12}\\b", "[SO DINH DANH DA AN]");
        safe = safe.replaceAll("\\b[A-Za-z0-9_-]{35,}\\b", "[CHUOI NHAY CAM DA AN]");
        return safe;
    }

    private void setBusy(boolean value) {
        busy = value;
        typing.setVisibility(value ? View.VISIBLE : View.GONE);
        sendButton.setEnabled(!value);
        input.setEnabled(!value);
        handoffButton.setEnabled(!value);
    }

    private void record(String who, String text) {
        if (conversation.length() > 9000) conversation.delete(0, conversation.length() - 6000);
        conversation.append(who).append(": ").append(text).append("\n");
    }

    private void showMessage(String text, boolean mine) {
        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(15);
        bubble.setLineSpacing(dp(3), 1.08f);
        bubble.setTextColor(mine ? Color.WHITE : Color.rgb(21,51,92));
        bubble.setPadding(dp(14), dp(12), dp(14), dp(12));
        bubble.setMaxWidth(getResources().getDisplayMetrics().widthPixels * 85 / 100);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(mine ? Color.rgb(36,91,222) : Color.WHITE);
        bg.setCornerRadius(dp(16));
        bubble.setBackground(bg);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(mine ? Gravity.END : Gravity.START);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.bottomMargin = dp(10);
        row.addView(bubble);
        messages.addView(row, rowLp);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
}
