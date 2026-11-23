package com.example.mobiledata.activities;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.mobiledata.utils.MobileDataBusinessClient;
import com.example.mobiledata.utils.MobileDataInputRules;
import com.example.mobiledata.utils.CustomerProfileRules;
import com.example.mobiledata.utils.SessionManager;
import org.json.JSONArray;
import org.json.JSONObject;

/** Quan ly ho so chu so huu CCCD, KHONG nham voi muc quan ly tung thue bao. */
public class CustomerManagementActivity extends AppCompatActivity {
    private static final int NAVY = Color.rgb(21, 51, 92), BLUE = Color.rgb(36, 99, 235);
    private static final int MUTED = Color.rgb(105, 122, 150);
    private String token;
    private LinearLayout list;
    private EditText search;
    private Spinner carrierFilter, statusFilter;
    private TextView count;
    private JSONArray data = new JSONArray();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        SessionManager session = new SessionManager(this);
        if (!session.isAdmin() || session.getAccessToken().isEmpty()) {
            Toast.makeText(this, "Chỉ ADMIN có quyền truy cập.", Toast.LENGTH_LONG).show();
            finish(); return;
        }
        token = session.getAccessToken();
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(245, 248, 253));
        LinearLayout page = vertical(); page.setPadding(dp(16), dp(20), dp(16), dp(30));
        scroll.addView(page); setContentView(scroll);
        TextView back = text("‹  Quản lý khách hàng", 23, NAVY, true);
        page.addView(back); back.setOnClickListener(v -> finish());
        page.addView(text("Hồ sơ khách hàng và thuê bao", 13, MUTED, false),
                params(0, dp(8)));
        Button add = button("＋  Thêm khách hàng");
        page.addView(add, params(0, dp(18)));
        add.setOnClickListener(v -> showForm(null));
        search = new EditText(this); MobileDataInputRules.text(search, 80);
        search.setHint("Tìm tên, CCCD hoặc số điện thoại...");
        search.setTextSize(15); search.setTextColor(NAVY);
        search.setPadding(dp(14), dp(10), dp(14), dp(10));
        search.setBackground(background(Color.WHITE, 14, 0));
        page.addView(search, params(0, dp(15)));
        LinearLayout filters = horizontal();
        page.addView(filters, params(0, dp(10)));
        carrierFilter = new Spinner(this);
        String[] carrierOptions = {"Tất cả nhà mạng", "Viettel", "VinaPhone", "MobiFone"};
        carrierFilter.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, carrierOptions));
        filters.addView(carrierFilter, new LinearLayout.LayoutParams(0, dp(48), 1));
        statusFilter = new Spinner(this);
        String[] statusOptions = {"Mọi trạng thái", "Đang hoạt động", "Đã khóa", "Chưa có thuê bao"};
        statusFilter.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, statusOptions));
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(0, dp(48), 1);
        statusLp.leftMargin = dp(8); filters.addView(statusFilter, statusLp);
        android.widget.AdapterView.OnItemSelectedListener listener = new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View v, int position, long id) { render(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        };
        carrierFilter.setOnItemSelectedListener(listener);
        statusFilter.setOnItemSelectedListener(listener);
        count = text("Đang tải danh sách...", 13, MUTED, false);
        page.addView(count, params(0, dp(15)));
        list = vertical(); page.addView(list);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) { render(); }
            @Override public void afterTextChanged(Editable e) { }
        });
        load();
    }
    private void load() {
        MobileDataBusinessClient.get("/api/admin/customers", token,
                new MobileDataBusinessClient.Callback() {
                    @Override public void onSuccess(JSONObject result) {
                        data = MobileDataBusinessClient.items(result); render();
                    }
                    @Override public void onError(String message) {
                        count.setText(message); list.removeAllViews();
                    }
                });
    }
    private void render() {
        if (list == null || search == null) return;
        list.removeAllViews(); int shown = 0;
        String q = search.getText().toString().trim().toLowerCase(java.util.Locale.ROOT);
        String carrier = carrierFilter == null ? "" : new String[]{"", "Viettel", "VinaPhone", "MobiFone"}
                [Math.max(0, Math.min(3, carrierFilter.getSelectedItemPosition()))];
        int selectedStatus = statusFilter == null ? 0 : statusFilter.getSelectedItemPosition();
        for (int i = 0; i < data.length(); i++) {
            JSONObject customer = data.optJSONObject(i);
            if (customer == null) continue;
            String fullName = customer.optString("fullName");
            String cccd = customer.optString("cccd");
            String phones = customer.optString("phones");
            String carriers = customer.optString("carriers");
            String statuses = customer.optString("statuses");
            String searchable = (fullName + " " + cccd + " " + phones + " " + carriers)
                    .toLowerCase(java.util.Locale.ROOT);
            if (!searchable.contains(q)) continue;
            if (!carrier.isEmpty() && !carriers.toLowerCase(java.util.Locale.ROOT)
                    .contains(carrier.toLowerCase(java.util.Locale.ROOT))) continue;
            int subscribers = customer.optInt("subscriberCount");
            if (selectedStatus == 1 && !statuses.contains("ACTIVE")) continue;
            if (selectedStatus == 2 && !statuses.contains("LOCKED")) continue;
            if (selectedStatus == 3 && subscribers != 0) continue;
            shown++;
            LinearLayout card = vertical(); card.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.setBackground(background(Color.WHITE, 17, 0));
            list.addView(card, params(0, dp(10)));
            card.addView(text(fullName, 17, NAVY, true));
            card.addView(text("CCCD: " + cccd, 14, BLUE, false), params(0, dp(5)));
            String phoneCount = customer.optInt("subscriberCount") + " thuê bao";
            card.addView(text(phoneCount + "   •   " + (customer.isNull("birthYear")
                    ? "Chưa có năm sinh" : customer.optString("birthYear")),
                    13, MUTED, false), params(0, dp(4)));
            if (!phones.isEmpty()) card.addView(text("Số điện thoại: " + phones, 13, MUTED, false), params(0, dp(4)));
            if (!carriers.isEmpty()) card.addView(text("Nhà mạng: " + carriers, 13, MUTED, false), params(0, dp(3)));
            if (!statuses.isEmpty()) card.addView(text("Trạng thái: "
                    + statuses.replace("ACTIVE", "Hoạt động").replace("LOCKED", "Đã khóa"),
                    13, MUTED, false), params(0, dp(3)));
            LinearLayout tools = horizontal(); card.addView(tools, params(0, dp(12)));
            Button edit = button("Sửa hồ sơ");
            Button delete = button("Xóa");
            delete.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(228, 78, 78)));
            tools.addView(edit, weight());
            LinearLayout.LayoutParams right = weight(); right.leftMargin = dp(9);
            tools.addView(delete, right);
            edit.setOnClickListener(v -> showForm(customer));
            delete.setOnClickListener(v -> confirmDelete(customer));
        }
        count.setText(shown + " khách hàng");
        if (shown == 0) list.addView(text("Không có hồ sơ phù hợp.", 14, MUTED, false));
    }
    private void confirmDelete(JSONObject customer) {
        int id = customer.optInt("id");
        int subscriberCount = customer.optInt("subscriberCount");
        if (subscriberCount > 0) {
            new AlertDialog.Builder(this).setTitle("Chưa thể xóa hồ sơ")
                    .setMessage("Khách hàng đang có " + subscriberCount + " thuê bao. "
                            + "Bạn có thể sửa CCCD tại đây hoặc khóa số điện thoại trong Quản lý thuê bao. "
                            + "Không xóa trực tiếp để bảo toàn giao dịch và lịch sử hỗ trợ.")
                    .setPositiveButton("Đã hiểu", null).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("Xóa hồ sơ khách hàng?")
                .setMessage(customer.optString("fullName") + "\nCCCD: " + customer.optString("cccd")
                        + "\n\nDữ liệu bị xóa không thể khôi phục bằng ứng dụng.")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa hồ sơ", (d, w) ->
                        MobileDataBusinessClient.delete("/api/admin/customers/" + id, token,
                                new MobileDataBusinessClient.Callback() {
                                    @Override public void onSuccess(JSONObject r) { toast("Đã xóa hồ sơ."); load(); }
                                    @Override public void onError(String e) { toast(e); }
                                })).show();
    }
    private void showForm(JSONObject existing) {
        final boolean editing = existing != null;
        LinearLayout fields = vertical(); fields.setPadding(dp(18), dp(10), dp(18), dp(12));
        EditText cccd = input(fields, "CCCD (12 chữ số)", editing ? existing.optString("cccd") : "", true);
        EditText name = input(fields, "Họ và tên", editing ? existing.optString("fullName") : "", false);
        TextView gtLabel = requiredTitle("Giới tính");
        fields.addView(gtLabel, params(0, dp(10)));
        Spinner gender = new Spinner(this);
        String[] options = {"Chọn giới tính", "Nam", "Nữ", "Khác"};
        gender.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options));
        fields.addView(gender);
        CustomerProfileRules.attachGender(fields, gender, null);
        if (editing) {
            String old = existing.optString("gender");
            for (int i = 1; i < options.length; i++) if (options[i].equals(old)) gender.setSelection(i);
        }
        EditText year = input(fields, "Năm sinh", editing && !existing.isNull("birthYear")
                ? existing.optString("birthYear") : "", true);
        EditText hometown = input(fields, "Quê quán", editing ? existing.optString("hometown") : "", false);
        EditText address = input(fields, "Địa chỉ", editing ? existing.optString("address") : "", false);
        EditText email = input(fields, "Email", editing ? existing.optString("email") : "", false);
        ScrollView scroll = new ScrollView(this); scroll.addView(fields);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editing ? "Chỉnh sửa khách hàng" : "Thêm khách hàng")
                .setView(scroll).setNegativeButton("Hủy", null)
                .setPositiveButton(editing ? "Lưu thay đổi" : "Thêm", null).create();
        dialog.setOnShowListener(v -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            if (!CustomerProfileRules.validateAll(cccd, name, gender,
                    year, hometown, address, email)) return;
            String idNumber = MobileDataInputRules.value(cccd);
            String fullName = MobileDataInputRules.value(name);
            String yr = year.getText().toString().trim();
            Integer birthYear = null;
            try {
                if (!yr.isEmpty()) birthYear = Integer.parseInt(yr);
                if (birthYear != null && (birthYear < 1900 || birthYear >
                        java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)))
                    throw new NumberFormatException();
            } catch (NumberFormatException e) { year.setError("Năm sinh không hợp lệ."); return; }
            JSONObject body = new JSONObject();
            try {
                body.put("cccd", idNumber); body.put("fullName", fullName);
                body.put("gender", gender.getSelectedItemPosition() == 0 ? "" :
                        options[gender.getSelectedItemPosition()]);
                body.put("birthYear", birthYear == null ? JSONObject.NULL : birthYear);
                body.put("hometown", hometown.getText().toString().trim());
                body.put("address", address.getText().toString().trim());
                body.put("email", email.getText().toString().trim());
            } catch (Exception e) { toast("Không thể đọc thông tin hồ sơ."); return; }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            MobileDataBusinessClient.Callback callback = new MobileDataBusinessClient.Callback() {
                @Override public void onSuccess(JSONObject response) {
                    toast(response.optString("message", "Đã lưu hồ sơ.")); dialog.dismiss(); load();
                }
                @Override public void onError(String error) {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); toast(error);
                }
            };
            if (editing) MobileDataBusinessClient.put("/api/admin/customers/" + existing.optInt("id"),
                    body, token, callback);
            else MobileDataBusinessClient.post("/api/admin/customers", body, token, callback);
        }));
        dialog.show();
    }
    private EditText input(LinearLayout parent, String label, String value, boolean numeric) {
        TextView title = requiredTitle(label);
        parent.addView(title, params(0, dp(10)));
        EditText edit = new EditText(this); edit.setSingleLine(true);
        edit.setTextSize(15); edit.setTextColor(NAVY); edit.setText(value);
        if (label.startsWith("CCCD")) MobileDataInputRules.digits(edit, 12);
        else if (label.equals("Năm sinh")) MobileDataInputRules.digits(edit, 4);
        else if (label.equals("Họ và tên")) MobileDataInputRules.text(edit, 100);
        else if (label.equals("Quê quán")) MobileDataInputRules.text(edit, 120);
        else if (label.equals("Địa chỉ")) MobileDataInputRules.text(edit, 200);
        else if (label.equals("Email")) MobileDataInputRules.email(edit);
        edit.setPadding(dp(10), dp(7), dp(10), dp(7));
        edit.setBackground(background(Color.rgb(245, 248, 253), 8, 0));
        parent.addView(edit, params(0, dp(5)));
        CustomerProfileRules.attach(parent, edit, null);
        return edit;
    }
    private TextView requiredTitle(String label) {
        TextView result = text(label + "  ●", 13, MUTED, true);
        android.text.SpannableString styled = new android.text.SpannableString(label + "  ●");
        styled.setSpan(new android.text.style.ForegroundColorSpan(Color.rgb(204, 45, 61)),
                styled.length() - 1, styled.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        result.setText(styled);
        return result;
    }
    private LinearLayout vertical() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l;
    }
    private LinearLayout horizontal() {
        LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL); return l;
    }
    private Button button(String value) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false);
        b.setTextColor(Color.WHITE); b.setBackgroundTintList(ColorStateList.valueOf(BLUE)); return b;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this); t.setText(value);
        t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD); return t;
    }
    private LinearLayout.LayoutParams params(int left, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.leftMargin = dp(left); p.topMargin = dp(top); return p;
    }
    private LinearLayout.LayoutParams weight() { return new LinearLayout.LayoutParams(0, dp(46), 1); }
    private GradientDrawable background(int color, int radius, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius));
        if (stroke != 0) d.setStroke(dp(1), stroke); return d;
    }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
