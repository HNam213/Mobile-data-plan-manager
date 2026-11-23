package com.example.mobiledata.activities;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.mobiledata.R;
import com.example.mobiledata.utils.MobileDataBusinessClient;
import com.example.mobiledata.utils.MobileDataInputRules;
import com.example.mobiledata.utils.SessionManager;
import org.json.JSONObject;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;

/** Giao dien nap tien hien dai; he thong chi xu ly so du noi bo, khong thu tien that. */
public class TopUpActivity extends AppCompatActivity {
    private static final int NAVY = Color.rgb(21, 51, 92);
    private static final int BLUE = Color.rgb(36, 99, 235);
    private static final int GRAY = Color.rgb(108, 125, 150);
    private static final int LIGHT = Color.rgb(240, 245, 254);
    private final long[] amounts = {10000, 20000, 50000, 100000, 200000};
    private final String[] methodNames = {"Thẻ cào", "Chuyển khoản ngân hàng", "MoMo", "ZaloPay"};
    private final String[] methodDetails = {"Nhập mã thẻ nạp", "Thanh toán qua ngân hàng", "Ví điện tử MoMo", "Ví điện tử ZaloPay"};
    private final String[] methodCodes = {"CARD", "BANK", "MOMO", "ZALOPAY"};
    private final int[] methodIcons = {R.drawable.md_pay_card, R.drawable.md_pay_bank,
            R.drawable.md_pay_momo, R.drawable.md_pay_zalopay};
    private final ArrayList<TextView> amountButtons = new ArrayList<>();
    private final ArrayList<LinearLayout> methodRows = new ArrayList<>();
    private TextView balance;
    private Button continueButton;
    private String token;
    private int selectedAmount = 2;
    private int selectedMethod = 0;

    @Override protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        token = new SessionManager(this).getAccessToken();
        if (token.isEmpty()) { finish(); return; }
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(245, 248, 253));
        LinearLayout page = vertical();
        page.setPadding(dp(16), 0, dp(16), dp(28));
        scroll.addView(page);
        setContentView(scroll);

        LinearLayout header = vertical();
        header.setPadding(dp(20), dp(32), dp(20), dp(25));
        header.setBackground(gradient());
        LinearLayout.LayoutParams hlp = params(-1, -2, 0, 0, 0, dp(16));
        page.addView(header, hlp);
        TextView back = text("‹  Nạp tiền", 24, Color.WHITE, true);
        header.addView(back);
        back.setOnClickListener(v -> finish());
        TextView sub = text("Chọn mệnh giá và phương thức thanh toán", 13,
                Color.rgb(218, 235, 255), false);
        header.addView(sub, params(-1, -2, 0, dp(8), 0, 0));
        LinearLayout balanceCard = vertical();
        balanceCard.setPadding(dp(18), dp(17), dp(18), dp(17));
        balanceCard.setBackground(rounded(Color.WHITE, 18, 0, 0));
        header.addView(balanceCard, params(-1, -2, 0, dp(20), 0, 0));
        balanceCard.addView(text("SỐ DƯ TÀI KHOẢN", 12, GRAY, true));
        balance = text("Đang tải...", 25, BLUE, true);
        balanceCard.addView(balance, params(-1, -2, 0, dp(7), 0, 0));

        page.addView(text("Chọn mệnh giá", 18, NAVY, true), params(-1, -2, dp(2), dp(7), 0, dp(13)));
        LinearLayout first = horizontal();
        page.addView(first);
        LinearLayout second = horizontal();
        page.addView(second, params(-1, -2, 0, dp(9), 0, 0));
        for (int i = 0; i < amounts.length; i++) {
            TextView chip = text(format(amounts[i]), 15, NAVY, true);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(56));
            final int idx = i;
            chip.setOnClickListener(v -> chooseAmount(idx));
            amountButtons.add(chip);
            LinearLayout holder = i < 3 ? first : second;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(58), 1);
            if ((i < 3 && i > 0) || i == 4) lp.leftMargin = dp(8);
            holder.addView(chip, lp);
        }
        // Giu hang thu hai can doi ve chieu rong so voi hang thu nhat.
        second.addView(new View(this), new LinearLayout.LayoutParams(0, dp(1), 1));
        chooseAmount(selectedAmount);

        page.addView(text("Phương thức thanh toán", 18, NAVY, true),
                params(-1, -2, dp(2), dp(25), 0, dp(12)));
        for (int i = 0; i < methodNames.length; i++) {
            final int index = i;
            LinearLayout row = horizontal();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(13), dp(13), dp(14), dp(13));
            ImageView icon = new ImageView(this);
            icon.setImageResource(methodIcons[i]);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            row.addView(icon, new LinearLayout.LayoutParams(dp(52), dp(52)));
            LinearLayout lines = vertical();
            row.addView(lines, new LinearLayout.LayoutParams(0, -2, 1));
            lines.setPadding(dp(13), 0, 0, 0);
            lines.addView(text(methodNames[i], 16, NAVY, true));
            lines.addView(text(methodDetails[i], 12, GRAY, false),
                    params(-1, -2, 0, dp(4), 0, 0));
            TextView indicator = text("○", 24, GRAY, false);
            indicator.setGravity(Gravity.CENTER);
            indicator.setTag("indicator");
            row.addView(indicator, new LinearLayout.LayoutParams(dp(29), dp(40)));
            row.setOnClickListener(v -> chooseMethod(index));
            page.addView(row, params(-1, -2, 0, 0, 0, dp(10)));
            methodRows.add(row);
        }
        chooseMethod(selectedMethod);

        continueButton = new Button(this);
        continueButton.setText("Tiếp tục  ›");
        continueButton.setAllCaps(false);
        continueButton.setTextSize(16);
        continueButton.setTypeface(null, Typeface.BOLD);
        continueButton.setTextColor(Color.WHITE);
        continueButton.setBackgroundTintList(ColorStateList.valueOf(BLUE));
        page.addView(continueButton, params(-1, dp(56), 0, dp(16), 0, 0));
        continueButton.setOnClickListener(v -> confirm());
        loadBalance();
    }

    private void chooseAmount(int selected) {
        selectedAmount = selected;
        for (int i = 0; i < amountButtons.size(); i++) {
            TextView b = amountButtons.get(i);
            boolean active = i == selected;
            b.setTextColor(active ? BLUE : NAVY);
            b.setBackground(rounded(active ? Color.rgb(232, 241, 255) : Color.WHITE,
                    15, active ? BLUE : Color.rgb(225, 233, 246), active ? 2 : 1));
        }
    }
    private void chooseMethod(int selected) {
        selectedMethod = selected;
        for (int i = 0; i < methodRows.size(); i++) {
            boolean active = i == selected;
            LinearLayout row = methodRows.get(i);
            row.setBackground(rounded(active ? Color.rgb(239, 246, 255) : Color.WHITE,
                    17, active ? BLUE : Color.rgb(232, 238, 247), active ? 2 : 1));
            TextView marker = (TextView) row.getChildAt(2);
            marker.setText(active ? "●" : "○");
            marker.setTextColor(active ? BLUE : GRAY);
        }
    }
    private void loadBalance() {
        MobileDataBusinessClient.get("/api/me/balance", token, new MobileDataBusinessClient.Callback() {
            @Override public void onSuccess(JSONObject o) {
                if (!isFinishing()) balance.setText(format(o.optDouble("balance")));
            }
            @Override public void onError(String e) { if (!isFinishing()) balance.setText("Chưa tải được số dư"); }
        });
    }
    private void confirm() {
        final long amount = amounts[selectedAmount];
        final String method = methodCodes[selectedMethod];
        if (selectedMethod == 0) {
            final EditText input = new EditText(this);
            MobileDataInputRules.code(input);
            input.setHint("Ví dụ: DEMO-123456");
            input.setPadding(dp(22), dp(14), dp(22), dp(14));
            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setTitle("Nạp tiền bằng thẻ cào")
                    .setMessage("Nhập mã thẻ theo định dạng được cấp.")
                    .setView(input)
                    .setNegativeButton("Quay lại", null)
                    .setPositiveButton("Tiếp tục", null).create();
            dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
                if (!MobileDataInputRules.value(input).toUpperCase(java.util.Locale.ROOT)
                        .matches("DEMO-[0-9]{6}")) {
                    MobileDataInputRules.error(input, "Mã thẻ không hợp lệ. Ví dụ: DEMO-123456."); return;
                }
                dialog.dismiss();
                finishTopup(amount, method);
            }));
            dialog.show();
        } else finishTopup(amount, method);
    }
    private void finishTopup(long amount, String method) {
        // Canh bao ro rang tai buoc xac nhan: khong ket noi voi nha cung cap thanh toan.
        new AlertDialog.Builder(this)
                .setTitle("Xác nhận nạp tiền")
                .setMessage("Phương thức: " + methodNames[selectedMethod]
                        + "\nSố tiền: " + format(amount)
                        + "\n\nChỉ cập nhật số dư trong MobileData; "
                        + "không thực hiện giao dịch tiền thật.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xác nhận", (d, w) -> {
                    JSONObject payload = new JSONObject();
                    try { payload.put("amount", amount); payload.put("method", method); }
                    catch (Exception ignored) { }
                    continueButton.setEnabled(false);
                    MobileDataBusinessClient.post("/api/me/topup", payload, token,
                            new MobileDataBusinessClient.Callback() {
                                @Override public void onSuccess(JSONObject r) {
                                    continueButton.setEnabled(true);
                                    loadBalance();
                                    Toast.makeText(TopUpActivity.this,
                                            "Đã cộng " + format(amount) + " vào số dư MobileData.",
                                            Toast.LENGTH_LONG).show();
                                }
                                @Override public void onError(String e) {
                                    continueButton.setEnabled(true);
                                    Toast.makeText(TopUpActivity.this, e, Toast.LENGTH_LONG).show();
                                }
                            });
                }).show();
    }
    private LinearLayout vertical() {
        LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v;
    }
    private LinearLayout horizontal() {
        LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL);
        v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD); return t;
    }
    private LinearLayout.LayoutParams params(int width, int height, int l, int top, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.setMargins(l, top, r, b); return p;
    }
    private GradientDrawable rounded(int color, int radius, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius));
        if (strokeWidth > 0) d.setStroke(dp(strokeWidth), stroke);
        return d;
    }
    private GradientDrawable gradient() {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.rgb(25, 80, 184), Color.rgb(65, 153, 248)});
        d.setCornerRadius(dp(22)); return d;
    }
    private String format(double n) {
        return NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(n) + "đ";
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
