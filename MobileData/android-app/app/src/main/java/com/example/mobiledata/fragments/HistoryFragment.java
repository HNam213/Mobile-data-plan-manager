package com.example.mobiledata.fragments;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.example.mobiledata.utils.MobileDataBusinessClient;
import com.example.mobiledata.utils.SessionManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Lịch sử từ SQL Server, có lọc, tìm kiếm và xuất CSV qua Android Storage Access Framework. */
public class HistoryFragment extends Fragment {
    private static final int DARK = Color.rgb(21, 51, 92);
    private static final int MUTED = Color.rgb(100, 116, 139);
    private static final int BLUE = Color.rgb(36, 99, 235);
    private static final int EXPORT_REQUEST = 53571;
    private LinearLayout itemsContainer;
    private TextView resultText;
    private Button[] filterButtons;
    private EditText search;
    private JSONArray loaded = new JSONArray();
    private List<JSONObject> visibleRows = new ArrayList<>();
    private final String[] labels = {"Tất cả", "Nạp tiền", "Đăng ký", "Hủy gói"};
    private final String[] types = {"", "NAP_TIEN", "DANG_KY_GOI", "HUY_GOI"};
    private int selected = 0;
    private int loadId;

    @Nullable @Override public View onCreateView(@NonNull android.view.LayoutInflater inflater,
            @Nullable ViewGroup parent, @Nullable Bundle savedInstanceState) {
        ScrollView scroll = new ScrollView(requireContext());
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(245,248,253));
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(24), dp(16), dp(30));
        scroll.addView(content);
        text(content, "Lịch sử giao dịch", 24, true, DARK);
        resultText = text(content, "Đang tải lịch sử...", 13, false, MUTED);
        resultText.setPadding(0, dp(5), 0, dp(12));

        search = new EditText(requireContext());
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setTextColor(DARK);
        search.setHintTextColor(Color.rgb(103,124,151));
        search.setHint("Tìm gói cước hoặc ghi chú...");
        search.setPadding(dp(14), dp(9), dp(14), dp(9));
        search.setBackground(round(Color.WHITE, 12));
        content.addView(search, new LinearLayout.LayoutParams(-1, dp(49)));
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { refreshList(); }
            @Override public void afterTextChanged(Editable e) { }
        });

        // Dùng thanh ngang riêng cho bốn nút; không ép nhỏ chữ.
        android.widget.HorizontalScrollView tabs = new android.widget.HorizontalScrollView(requireContext());
        tabs.setHorizontalScrollBarEnabled(false);
        LinearLayout bar = new LinearLayout(requireContext());
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(0, dp(10), 0, dp(10));
        tabs.addView(bar);
        content.addView(tabs);
        filterButtons = new Button[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int ix = i;
            Button b = new Button(requireContext());
            b.setText(labels[i]); b.setAllCaps(false);
            b.setTextSize(13); b.setMinWidth(0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(43));
            lp.rightMargin = dp(6);
            bar.addView(b, lp);
            filterButtons[i] = b;
            b.setOnClickListener(v -> { selected = ix; updateTabs(); refreshList(); });
        }
        updateTabs();

        Button export = new Button(requireContext());
        export.setText("Lưu danh sách đang lọc thành CSV");
        export.setAllCaps(false);
        export.setTextSize(13);
        export.setTextColor(BLUE);
        export.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(227,237,255)));
        content.addView(export, new LinearLayout.LayoutParams(-1, dp(46)));
        export.setOnClickListener(v -> exportCsv());

        itemsContainer = new LinearLayout(requireContext());
        itemsContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.topMargin = dp(14);
        content.addView(itemsContainer, cp);
        return scroll;
    }

    @Override public void onResume() { super.onResume(); if (itemsContainer != null) reload(); }
    private void updateTabs() {
        if (filterButtons == null) return;
        for (int i = 0; i < filterButtons.length; i++) {
            Button b = filterButtons[i];
            b.setTextColor(i == selected ? Color.WHITE : DARK);
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    i == selected ? BLUE : Color.rgb(229,236,248)));
        }
    }

    private void reload() {
        final int request = ++loadId;
        resultText.setText("Đang tải lịch sử từ SQL Server...");
        itemsContainer.removeAllViews();
        String token = new SessionManager(requireContext()).getAccessToken();
        MobileDataBusinessClient.get("/api/me/history", token, new MobileDataBusinessClient.Callback() {
            @Override public void onSuccess(JSONObject result) {
                if (!isAdded() || request != loadId || itemsContainer == null) return;
                loaded = MobileDataBusinessClient.items(result);
                refreshList();
            }
            @Override public void onError(String err) {
                if (!isAdded() || request != loadId || itemsContainer == null) return;
                resultText.setText("Không tải được lịch sử");
                itemsContainer.removeAllViews();
                text(itemsContainer, err, 14, false, Color.RED);
            }
        });
    }

    private void refreshList() {
        if (itemsContainer == null || resultText == null) return;
        itemsContainer.removeAllViews();
        visibleRows = new ArrayList<>();
        String query = search == null ? "" : search.getText().toString().trim().toLowerCase(new Locale("vi", "VN"));
        for (int i = 0; i < loaded.length(); i++) {
            JSONObject e = loaded.optJSONObject(i);
            if (e == null) continue;
            String type = e.optString("type", "");
            if (!types[selected].isEmpty() && !types[selected].equals(type)) continue;
            String content = (e.optString("planCode", "") + " " + e.optString("note", "")
                    + " " + label(type)).toLowerCase(new Locale("vi", "VN"));
            if (!content.contains(query)) continue;
            visibleRows.add(e);
        }
        resultText.setText("Đang hiển thị " + visibleRows.size() + "/" + loaded.length() + " giao dịch");
        if (visibleRows.isEmpty()) text(itemsContainer, "Không có giao dịch phù hợp.", 14, false, MUTED);
        for (JSONObject item : visibleRows) drawRow(item);
    }

    private String label(String type) {
        if ("NAP_TIEN".equals(type)) return "Nạp tiền mô phỏng";
        if ("HUY_GOI".equals(type)) return "Hủy gói cước";
        if ("DANG_KY_GOI".equals(type)) return "Đăng ký gói cước";
        return "Giao dịch";
    }

    private void drawRow(JSONObject e) {
        String type = e.optString("type", "");
        boolean topup = "NAP_TIEN".equals(type);
        boolean cancel = "HUY_GOI".equals(type);
        String code = e.isNull("planCode") ? "" : e.optString("planCode", "");
        double amount = e.optDouble("amount", 0);
        double after = e.optDouble("balanceAfter", 0);
        double before = e.optDouble("balanceBefore", 0);
        int tint = topup ? Color.rgb(19,151,110) : cancel ? Color.rgb(209,136,28) : BLUE;

        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(15), dp(15), dp(15));
        card.setBackground(round(Color.WHITE, 15));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.bottomMargin = dp(11);
        itemsContainer.addView(card, lp);

        LinearLayout top = new LinearLayout(requireContext());
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top);
        TextView title = text(top, label(type) + (code.isEmpty() ? "" : " • " + code), 15, true, DARK);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        String change = topup ? "+" + money(amount) : cancel ? "0đ" : "−" + money(amount);
        TextView value = text(top, change, 15, true, tint);
        value.setGravity(Gravity.END);
        text(card, friendlyDate(e.optString("at")), 12, false, MUTED);
        String note = cancel ? "Hủy gói không hoàn tiền" : e.optString("note", "");
        if (!note.isEmpty()) { TextView n = text(card, note, 12, false, MUTED); n.setPadding(0,dp(5),0,dp(8)); }
        LinearLayout balances = new LinearLayout(requireContext());
        balances.setOrientation(LinearLayout.HORIZONTAL);
        balances.setPadding(dp(10), dp(10), dp(10), dp(10));
        balances.setBackground(round(Color.rgb(240,246,255), 10));
        card.addView(balances);
        TextView first = text(balances,"Trước\n" + money(before), 13, false, DARK);
        first.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        TextView second = text(balances,"Sau\n" + money(after), 13, true, DARK);
        second.setGravity(Gravity.END);
        second.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
    }

    private void exportCsv() {
        if (!isAdded() || visibleRows.isEmpty()) {
            if (isAdded()) Toast.makeText(requireContext(), "Không có giao dịch để xuất", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("text/csv");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE, "MobileData_LichSu_" +
                new SessionManager(requireContext()).getPhone() + ".csv");
        startActivityForResult(intent, EXPORT_REQUEST);
    }

    @Override @SuppressWarnings("deprecation") public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != EXPORT_REQUEST || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        try (OutputStream out = requireContext().getContentResolver().openOutputStream(data.getData())) {
            if (out == null) throw new IllegalStateException("Không mở được tệp");
            StringBuilder csv = new StringBuilder("Ngay,Loai giao dich,Ma goi,Bien dong VND,So du truoc VND,So du sau VND,Ghi chu\r\n");
            for (JSONObject e : visibleRows) {
                String type = e.optString("type", "");
                double amount = e.optDouble("amount", 0);
                double delta = "NAP_TIEN".equals(type) ? amount : "HUY_GOI".equals(type) ? 0 : -amount;
                csv.append(cell(friendlyDate(e.optString("at")))).append(',')
                        .append(cell(label(type))).append(',')
                        .append(cell(e.optString("planCode", ""))).append(',')
                        .append(delta).append(',')
                        .append(e.optDouble("balanceBefore")).append(',')
                        .append(e.optDouble("balanceAfter")).append(',')
                        .append(cell(e.optString("note", ""))).append("\r\n");
            }
            out.write(new byte[]{(byte)0xEF,(byte)0xBB,(byte)0xBF});
            out.write(csv.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            Toast.makeText(requireContext(), "Đã lưu tệp CSV", Toast.LENGTH_LONG).show();
        } catch (Exception ex) {
            if (isAdded()) Toast.makeText(requireContext(), "Không lưu được CSV: " + ex.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String cell(String raw) {
        String value = raw == null ? "" : raw.replace("\r", " ").replace("\n", " ");
        // Ngăn Excel tự diễn giải ghi chú nhập từ người dùng như một công thức.
        if (!value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    private String friendlyDate(String raw) {
        if (raw == null || raw.length() < 16) return raw == null ? "" : raw;
        return raw.substring(8,10) + "/" + raw.substring(5,7) + "/" + raw.substring(0,4) + " " + raw.substring(11,16);
    }
    private TextView text(LinearLayout list, String label, int size, boolean bold, int tint) {
        TextView t = new TextView(requireContext());
        t.setText(label); t.setTextSize(size); t.setTextColor(tint);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        list.addView(t); return t;
    }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private String money(double n) { return NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(n) + "đ"; }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    @Override public void onDestroyView() {
        loadId++;
        itemsContainer = null;
        search = null;
        resultText = null;
        filterButtons = null;
        super.onDestroyView();
    }
}
