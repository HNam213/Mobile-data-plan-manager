package com.example.mobiledata.activities;

import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.ImageView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import android.content.Intent;
import android.app.Activity;
import android.net.Uri;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.graphics.drawable.GradientDrawable;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import com.example.mobiledata.widgets.MonthlyColumnChartView;
import com.example.mobiledata.utils.SupportRealtimeClient;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.mobiledata.R;
import com.example.mobiledata.utils.MobileDataBusinessClient;
import com.example.mobiledata.utils.MobileDataInputRules;
import com.example.mobiledata.utils.CustomerProfileRules;
import com.example.mobiledata.utils.SessionManager;
import com.example.mobiledata.utils.SupportDialogs;
import com.example.mobiledata.utils.SupportConversationDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;

/** Quản trị viên thao tác với SQL Server qua Web API, không đọc dữ liệu demo. */
public class AdminActivity extends AppCompatActivity {
    private static final int CARRIERS = 0, PLANS = 1, SUBSCRIBERS = 2, SUPPORT = 3, REPORTS = 4;
    private int section = PLANS;
    private String token;
    private SupportRealtimeClient liveEvents;
    private EditText search;
    private Spinner carrierFilter;
    private TextView title, count, empty;
    private Button addButton;
    private LinearLayout rows;
    private TextView[] tabs;
    private JSONArray data = new JSONArray();
    private JSONObject report = new JSONObject();
    private int reportYear = Calendar.getInstance().get(Calendar.YEAR);
    private int reportCarrierIndex = 0;
    private int reloadGeneration = 0;
    private static final String[] REPORT_CARRIER_CODES = {"", "VT", "VN", "MB"};
    private static final String[] REPORT_CARRIER_NAMES = {"Tất cả nhà mạng", "Viettel", "VinaPhone", "MobiFone"};
    private String csvPending = "";
    private static final int CREATE_REPORT_CSV = 5077;
    private static final int PICK_OWNER_PHOTO = 7051;
    private String selectedOwnerPhotoBase64 = "";
    private ImageView ownerPhotoPreview;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        SessionManager session = new SessionManager(this);
        if (!session.isAdmin() || session.getAccessToken().isEmpty()) {
            Toast.makeText(this, "Bạn không có quyền truy cập mục này.", Toast.LENGTH_LONG).show();
            finish(); return;
        }
        token = session.getAccessToken();
        setContentView(R.layout.activity_admin);
        search = findViewById(R.id.edtAdminSearch);
        MobileDataInputRules.text(search, 80);
        carrierFilter = findViewById(R.id.spAdminCarrierFilter);
        ArrayAdapter<String> carrierAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Tất cả nhà mạng", "Viettel", "VinaPhone", "MobiFone"});
        carrierFilter.setAdapter(carrierAdapter);
        carrierFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) { draw(); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        title = findViewById(R.id.txtAdminSectionTitle);
        count = findViewById(R.id.txtAdminSectionCount);
        empty = findViewById(R.id.txtAdminEmpty);
        rows = findViewById(R.id.adminListContainer);
        addButton = findViewById(R.id.btnAdminAdd);
        tabs = new TextView[] { findViewById(R.id.tabAdminCarriers),
                findViewById(R.id.tabAdminPlans), findViewById(R.id.tabAdminSubscribers),
                findViewById(R.id.tabAdminSupport) };
        for (int i = 0; i < tabs.length; i++) {
            final int selected = i;
            tabs[i].setOnClickListener(v -> select(selected));
        }
        findViewById(R.id.btnAdminStatistics).setOnClickListener(v -> select(REPORTS));
        findViewById(R.id.btnAdminRegisterOwner).setOnClickListener(v -> registerOwner());
        findViewById(R.id.btnAdminManageOwners).setOnClickListener(v ->
                startActivity(new Intent(this, CustomerManagementActivity.class)));
        liveEvents = new SupportRealtimeClient(this, () -> {
            refreshSupportCount();
            if (section == SUPPORT) reload();
        });
        findViewById(R.id.txtAdminCountSupport).setOnClickListener(v -> select(SUPPORT));
        refreshSupportCount();
        findViewById(R.id.btnAdminBack).setOnClickListener(v -> finish());
        addButton.setOnClickListener(v -> { if (section == PLANS) planForm(null); else if (section == SUBSCRIBERS) subscriberForm(null); });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int co, int af) { }
            @Override public void onTextChanged(CharSequence s, int st, int be, int co) { draw(); }
            @Override public void afterTextChanged(Editable e) { }
        });
        select(PLANS);
    }

    private void select(int selected) {
        section = selected;
        search.setText("");
        String[] names = {"Danh sách nhà mạng", "Quản lý gói cước", "Quản lý thuê bao", "Yêu cầu hỗ trợ", "Báo cáo hệ thống"};
        title.setText(names[selected]);
        addButton.setVisibility(selected == PLANS || selected == SUBSCRIBERS ? View.VISIBLE : View.GONE);
        search.setVisibility(selected == REPORTS ? View.GONE : View.VISIBLE);
        carrierFilter.setVisibility(selected == PLANS || selected == SUBSCRIBERS || selected == SUPPORT
                ? View.VISIBLE : View.GONE);
        carrierFilter.setSelection(0);
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setBackgroundResource(i == selected ? R.drawable.bg_home_chip : R.drawable.bg_home_card);
            tabs[i].setTextColor(Color.parseColor(i == selected ? "#2463EB" : "#8192AD"));
            tabs[i].setTypeface(null, i == selected ? Typeface.BOLD : Typeface.NORMAL);
        }
        reload();
    }

    private void reload() {
        String[] urls = {"/api/carriers", "/api/admin/plans", "/api/admin/subscribers",
                "/api/admin/support", "/api/admin/stats?year=" + reportYear
                        + (reportCarrierIndex == 0 ? "" : "&carrier=" + REPORT_CARRIER_CODES[reportCarrierIndex])};
        final int requested = section;
        final int thisReload = ++reloadGeneration;
        rows.removeAllViews();
        empty.setText("Đang tải dữ liệu...");
        empty.setVisibility(View.VISIBLE);
        MobileDataBusinessClient.get(urls[section], token, new MobileDataBusinessClient.Callback() {
            @Override public void onSuccess(JSONObject response) {
                if (isFinishing() || requested != section || thisReload != reloadGeneration) return;
                if (section == REPORTS) report = response;
                else data = MobileDataBusinessClient.items(response);
                if (section == PLANS) ((TextView) findViewById(R.id.txtAdminCountPlans))
                        .setText(data.length() + "\nGói cước");
                if (section == SUBSCRIBERS) ((TextView) findViewById(R.id.txtAdminCountSubscribers))
                        .setText(data.length() + "\nThuê bao");
                if (section == SUPPORT) ((TextView) findViewById(R.id.txtAdminCountSupport))
                        .setText(data.length() + "\nHỗ trợ");
                if (section == CARRIERS) ((TextView) findViewById(R.id.txtAdminCountCarriers))
                        .setText(data.length() + "\nNhà mạng");
                draw();
            }
            @Override public void onError(String message) { if (!isFinishing() && requested == section && thisReload == reloadGeneration) {
                rows.removeAllViews(); empty.setText(message); empty.setVisibility(View.VISIBLE);
            } }
        });
    }

    private void draw() {
        if (rows == null) return;
        rows.removeAllViews();
        if (section == REPORTS) { drawReport(); return; }
        int shown = 0;
        String query = search.getText().toString().toLowerCase(Locale.ROOT).trim();
        int selectedCarrier = carrierFilter.getSelectedItemPosition();
        String filter = new String[]{"", "Viettel", "VinaPhone", "MobiFone"}[
                Math.max(0, Math.min(selectedCarrier, 3))];
        for (int i = 0; i < data.length(); i++) {
            JSONObject item = data.optJSONObject(i);
            if (item == null || !item.toString().toLowerCase(Locale.ROOT).contains(query)) continue;
            if (section != CARRIERS && !filter.isEmpty() &&
                    !filter.equalsIgnoreCase(item.optString("carrierName"))) continue;
            addRow(item); shown++;
        }
        count.setText(shown + " mục");
        empty.setText("Không có mục phù hợp.");
        empty.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
    }

    private void addRow(JSONObject item) {
        View view = LayoutInflater.from(this).inflate(R.layout.item_admin_row, rows, false);
        TextView name = view.findViewById(R.id.txtAdminRowName);
        TextView desc = view.findViewById(R.id.txtAdminRowSubtitle);
        TextView icon = view.findViewById(R.id.txtAdminRowIcon);
        if (section == CARRIERS) {
            name.setText(item.optString("name"));
            desc.setText("Mã nhà mạng: " + item.optString("code"));
            icon.setText("N");
        } else if (section == PLANS) {
            name.setText(item.optString("code") + " • " + item.optString("carrierName"));
            desc.setText(money(item.optDouble("price")) + " • " + item.optInt("durationDays")
                    + " ngày • " + (item.optBoolean("isForSale") ? "Đang bán" : "Ngừng bán"));
            icon.setText("G");
            view.setOnClickListener(v -> planActions(item));
        } else if (section == SUPPORT) {
            name.setText("#" + item.optLong("id") + " • " + item.optString("title"));
            desc.setText(item.optString("phone") + " • " + item.optString("carrierName")
                    + " • " + SupportDialogs.state(item.optString("status")));
            icon.setText("✉");
            view.setOnClickListener(v -> supportDetail(item.optLong("id")));
        } else {
            name.setText(item.optString("phone") + " • " + item.optString("role"));
            desc.setText(item.optString("fullName") + " • " + item.optString("carrierName")
                    + " • " + ("ACTIVE".equals(item.optString("status")) ? "Đang dùng" : "Đã khóa"));
            icon.setText("T");
            view.setOnClickListener(v -> subscriberForm(item));
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        rows.addView(view, params);
    }

    private void planActions(JSONObject plan) {
        new AlertDialog.Builder(this, R.style.MobileDataLightDialog)
                .setTitle(plan.optString("code"))
                .setMessage(plan.optString("name") + "\n" + money(plan.optDouble("price"))
                        + "\n" + plan.optDouble("dataGb") + " GB • " + plan.optString("dataType")
                        + "\n" + plan.optInt("durationDays") + " ngày")
                .setNeutralButton("Sửa gói", (d, w) -> planForm(plan))
                .setNegativeButton("Đóng", null)
                .setPositiveButton(plan.optBoolean("isForSale") ? "Ngừng bán" : "Mở bán",
                        (d, w) -> {
                            JSONObject body = json("isForSale", !plan.optBoolean("isForSale"));
                            MobileDataBusinessClient.patch("/api/admin/plans/" + plan.optInt("id")
                                    + "/availability", body, token, refreshCallback());
                        }).show();
    }

    private void planForm(JSONObject plan) {
        final boolean edit = plan != null;
        LinearLayout form = makeForm();
        Spinner carrier = spinner(form, new String[]{"VinaPhone (VN)", "Viettel (VT)", "MobiFone (MB)"});
        if (edit) carrier.setVisibility(View.GONE);
        EditText code = field(form, "Mã gói (VD: VT30)", edit ? plan.optString("code") : "", false);
        if (edit) code.setVisibility(View.GONE);
        EditText name = field(form, "Tên gói", edit ? plan.optString("name") : "", false);
        EditText price = field(form, "Giá (VND)", edit ? String.valueOf((long) plan.optDouble("price")) : "", true);
        EditText gb = field(form, "Dung lượng GB", edit ? plan.optString("dataGb") : "", true);
        Spinner type = spinner(form, new String[]{"DAILY (GB/ngày)", "TOTAL (GB/chu kỳ)"});
        if (edit && "TOTAL".equals(plan.optString("dataType"))) type.setSelection(1);
        EditText days = field(form, "Chu kỳ (ngày)", edit ? plan.optString("durationDays") : "", true);
        EditText description = field(form, "Mô tả", edit ? plan.optString("description", "") : "", false);
        CheckBox popular = check(form, "Gói phổ biến", edit && plan.optBoolean("isPopular"));
        CheckBox forSale = check(form, "Đang bán", !edit || plan.optBoolean("isForSale"));
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.MobileDataLightDialog)
                .setTitle(edit ? "Sửa " + plan.optString("code") : "Thêm gói cước")
                .setView(scroll(form))
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Lưu", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if ((!edit && !MobileDataInputRules.packageCode(code))
                    || !MobileDataInputRules.name(name)
                    || !MobileDataInputRules.planPrice(price)
                    || !MobileDataInputRules.dataGb(gb)
                    || !MobileDataInputRules.days(days)
                    || !MobileDataInputRules.optionalText(description, 500, "Mô tả")) return;
            try {
                JSONObject body = new JSONObject();
                if (!edit) {
                    body.put("carrierCode", new String[]{"VN", "VT", "MB"}[carrier.getSelectedItemPosition()]);
                    body.put("code", MobileDataInputRules.value(code).toUpperCase(Locale.ROOT));
                }
                body.put("name", MobileDataInputRules.value(name));
                body.put("price", Long.parseLong(MobileDataInputRules.value(price)));
                body.put("dataGb", MobileDataInputRules.dataGbValue(gb));
                body.put("dataType", type.getSelectedItemPosition() == 0 ? "DAILY" : "TOTAL");
                body.put("durationDays", Integer.parseInt(MobileDataInputRules.value(days)));
                body.put("description", MobileDataInputRules.value(description));
                body.put("isPopular", popular.isChecked());
                body.put("isForSale", forSale.isChecked());
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                MobileDataBusinessClient.Callback callback = new MobileDataBusinessClient.Callback() {
                    @Override public void onSuccess(JSONObject result) {
                        dialog.dismiss(); toast("Đã lưu gói cước."); reload();
                    }
                    @Override public void onError(String error) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); toast(error);
                    }
                };
                if (edit) MobileDataBusinessClient.put("/api/admin/plans/" + plan.optInt("id"), body, token, callback);
                else MobileDataBusinessClient.post("/api/admin/plans", body, token, callback);
            } catch (Exception ex) { toast("Không thể lưu thông tin gói cước."); }
        }));
        dialog.show();
    }

    /** Chỉ đăng ký chủ sở hữu CCCD: không tạo/gắn số điện thoại ở form này. */
    private void registerOwner() {
        selectedOwnerPhotoBase64 = "";
        LinearLayout form = makeForm();
        ownerPhotoPreview = new ImageView(this);
        ownerPhotoPreview.setImageResource(android.R.drawable.ic_menu_myplaces);
        ownerPhotoPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        LinearLayout.LayoutParams photoLp = new LinearLayout.LayoutParams(dp(90), dp(90));
        photoLp.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        form.addView(ownerPhotoPreview, photoLp);
        Button choosePhoto = new Button(this);
        choosePhoto.setText("Chọn ảnh đại diện (tùy chọn)");
        choosePhoto.setAllCaps(false);
        choosePhoto.setTextColor(Color.parseColor("#2463EB"));
        form.addView(choosePhoto);
        choosePhoto.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*"); intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "Chọn ảnh khách hàng"), PICK_OWNER_PHOTO);
        });
        EditText cccd = field(form, "CCCD (12 số)", "", true);
        EditText fullName = field(form, "Họ và tên", "", false);
        Spinner gender = spinner(form, new String[]{"Chưa xác định", "Nam", "Nữ", "Khác"});
        EditText birthYear = field(form, "Năm sinh", "", true);
        EditText hometown = field(form, "Quê quán", "", false);
        EditText address = field(form, "Địa chỉ", "", false);
        EditText email = field(form, "Email", "", false);
        CustomerProfileRules.attach(form, cccd, "CCCD (12 chữ số)");
        CustomerProfileRules.attach(form, fullName, "Họ và tên");
        CustomerProfileRules.attachGender(form, gender, "Giới tính");
        CustomerProfileRules.attach(form, birthYear, "Năm sinh");
        CustomerProfileRules.attach(form, hometown, "Quê quán");
        CustomerProfileRules.attach(form, address, "Địa chỉ");
        CustomerProfileRules.attach(form, email, "Email");
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.MobileDataLightDialog)
            .setTitle("Thêm khách hàng")
            .setView(scroll(form))
            .setNegativeButton("Hủy", null)
            .setPositiveButton("Lưu", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (!CustomerProfileRules.validateAll(cccd, fullName, gender,
                    birthYear, hometown, address, email)) return;
            JSONObject data = new JSONObject();
            try {
                data.put("cccd", MobileDataInputRules.value(cccd));
                data.put("fullName", MobileDataInputRules.value(fullName));
                data.put("gender", gender.getSelectedItemPosition() == 0 ? "" : gender.getSelectedItem().toString());
                String year = MobileDataInputRules.value(birthYear);
                data.put("birthYear", year.isEmpty() ? JSONObject.NULL : Integer.parseInt(year));
                data.put("hometown", MobileDataInputRules.value(hometown));
                data.put("address", MobileDataInputRules.value(address));
                data.put("email", MobileDataInputRules.value(email));
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                MobileDataBusinessClient.post("/api/admin/customers", data, token,
                    new MobileDataBusinessClient.Callback() {
                    @Override public void onSuccess(JSONObject r) {
                        dialog.dismiss(); toast("Đã thêm khách hàng.");
                        if (!selectedOwnerPhotoBase64.isEmpty()) {
                            JSONObject photoBody = new JSONObject();
                            try {
                                photoBody.put("cccd", MobileDataInputRules.value(cccd));
                                photoBody.put("photoBase64", selectedOwnerPhotoBase64);
                            } catch (Exception ignored) { }
                            MobileDataBusinessClient.post("/api/admin/customers/photo", photoBody, token,
                                new MobileDataBusinessClient.Callback() {
                                @Override public void onSuccess(JSONObject result) { toast("Đã lưu ảnh đại diện."); }
                                @Override public void onError(String error) { toast("Đã tạo hồ sơ nhưng chưa lưu được ảnh: " + error); }
                            });
                        }
                    }
                    @Override public void onError(String err) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        toast(err);
                    }
                });
            } catch (Exception ex) { toast("Không thể lưu hồ sơ khách hàng."); }
        }));
        dialog.show();
    }

    private void subscriberForm(JSONObject item) {
        final boolean edit = item != null;
        LinearLayout form = makeForm();
        EditText cccd = field(form, "CCCD (12 số)", edit ? item.optString("cccd") : "", true);
        if (edit) cccd.setVisibility(View.GONE);
        EditText name = field(form, "Họ tên khách hàng", edit ? item.optString("fullName") : "", false);
        EditText phone = field(form, "Số điện thoại (10 số)", edit ? item.optString("phone") : "", true);
        Spinner carrier = spinner(form, new String[]{"VinaPhone (VN)", "Viettel (VT)", "MobiFone (MB)"});
        if (edit) carrier.setVisibility(View.GONE);
        CheckBox locked = check(form, "Khóa thuê bao (không cho đăng nhập)",
                edit && !"ACTIVE".equals(item.optString("status")));
        if (!edit) locked.setVisibility(View.GONE);
        if (edit && !"USER".equals(item.optString("role"))) {
            toast("Chỉ có thể sửa thuê bao USER tại đây."); return;
        }
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.MobileDataLightDialog)
                .setTitle(edit ? "Sửa thuê bao" : "Thêm thuê bao")
                .setView(scroll(form))
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Lưu", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if ((!edit && !MobileDataInputRules.cccd(cccd))
                    || !validSubscriberName(name)
                    || !MobileDataInputRules.phone(phone)) return;
            try {
                JSONObject body = new JSONObject();
                body.put("fullName", MobileDataInputRules.value(name));
                body.put("phone", MobileDataInputRules.value(phone));
                if (!edit) {
                    body.put("cccd", MobileDataInputRules.value(cccd));
                    body.put("carrierCode", new String[]{"VN", "VT", "MB"}[carrier.getSelectedItemPosition()]);
                } else body.put("locked", locked.isChecked());
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                MobileDataBusinessClient.Callback callback = new MobileDataBusinessClient.Callback() {
                    @Override public void onSuccess(JSONObject result) {
                        dialog.dismiss(); toast("Đã lưu thuê bao."); reload();
                    }
                    @Override public void onError(String error) {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); toast(error);
                    }
                };
                if (edit) MobileDataBusinessClient.put("/api/admin/subscribers/" + item.optInt("id"), body, token, callback);
                else MobileDataBusinessClient.post("/api/admin/subscribers", body, token, callback);
            } catch (Exception ex) { toast("Không thể lưu thông tin thuê bao."); }
        }));
        dialog.show();
    }

    private boolean validSubscriberName(EditText field) {
        String error = CustomerProfileRules.validateFullName(field.getText().toString());
        if (error != null) { field.setError(error); field.requestFocus(); return false; }
        return true;
    }

    private void refreshSupportCount() {
        MobileDataBusinessClient.get("/api/admin/support", token, new MobileDataBusinessClient.Callback() {
            @Override public void onSuccess(JSONObject result) {
                if (!isFinishing()) ((TextView) findViewById(R.id.txtAdminCountSupport))
                        .setText(MobileDataBusinessClient.items(result).length() + "\nHỗ trợ");
            }
            @Override public void onError(String error) { }
        });
    }

    private void supportDetail(long id) {
        // Cua so chat dung chung: tin nhan moi cap nhat ngay khi nhan SSE.
        SupportConversationDialog.open(this, token, id, true, () -> {
            if (!isFinishing()) {
                reload();
                refreshSupportCount();
            }
        });
    }

    private void drawReport() {
        count.setText("Năm " + report.optInt("year", reportYear)
                + " • " + REPORT_CARRIER_NAMES[reportCarrierIndex]);
        empty.setVisibility(View.GONE);

        TextView filterLabel = info("LỌC BÁO CÁO THEO NHÀ MẠNG");
        filterLabel.setTypeface(null, Typeface.BOLD);
        filterLabel.setTextSize(13);
        rows.addView(filterLabel);
        Spinner reportFilter = spinner(rows, REPORT_CARRIER_NAMES);
        reportFilter.setSelection(reportCarrierIndex);
        reportFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != reportCarrierIndex) {
                    reportCarrierIndex = position;
                    reload();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        Button previous = reportButton("‹ Năm trước");
        Button next = reportButton("Năm sau ›");
        Button export = reportButton("Xuất CSV");
        next.setEnabled(reportYear < Calendar.getInstance().get(Calendar.YEAR));
        tools.addView(previous, new LinearLayout.LayoutParams(0, dp(48), 1));
        tools.addView(next, new LinearLayout.LayoutParams(0, dp(48), 1));
        tools.addView(export, new LinearLayout.LayoutParams(0, dp(48), 1));
        rows.addView(tools);
        previous.setOnClickListener(v -> { if (reportYear > 2000) { reportYear--; reload(); } });
        next.setOnClickListener(v -> {
            if (reportYear < Calendar.getInstance().get(Calendar.YEAR)) { reportYear++; reload(); }
        });
        export.setOnClickListener(v -> exportReport());


        reportCard("TỔNG NẠP", money(report.optDouble("totalTopup")), "#13976E");
        reportCard("CHI TIÊU MUA GÓI", money(report.optDouble("totalSpent")), "#2463EB");
        reportCard("LƯỢT ĐĂNG KÝ GÓI", String.valueOf(report.optInt("purchases")), "#15335C");
        reportCard("LƯỢT HỦY GÓI", String.valueOf(report.optInt("cancellations")), "#D78612");

        TextView carrierHeading = info("SO SÁNH 3 NHÀ MẠNG");
        carrierHeading.setTextSize(17);
        carrierHeading.setTypeface(null, Typeface.BOLD);
        rows.addView(carrierHeading);
        TextView carrierNote = info("Chạm vào một nhà mạng để lọc toàn bộ báo cáo. "
                + "Số thuê bao là số lượng hiện tại, còn giao dịch tính theo năm đã chọn.");
        carrierNote.setTextSize(12);
        rows.addView(carrierNote);
        JSONArray carrierStats = report.optJSONArray("carriers");
        if (carrierStats != null) for (int i = 0; i < carrierStats.length(); i++) {
            JSONObject network = carrierStats.optJSONObject(i);
            if (network == null) continue;
            drawCarrierReportRow(network);
        }

        TextView monthsHeading = info("BIỂU ĐỒ THU CHI THEO THÁNG");
        monthsHeading.setTextSize(17);
        monthsHeading.setTypeface(null, Typeface.BOLD);
        rows.addView(monthsHeading);
        LinearLayout chartPanel = new LinearLayout(this);
        chartPanel.setOrientation(LinearLayout.VERTICAL);
        chartPanel.setPadding(dp(8), dp(10), dp(8), dp(10));
        GradientDrawable chartBg = new GradientDrawable();
        chartBg.setColor(Color.WHITE); chartBg.setCornerRadius(dp(16));
        chartPanel.setBackground(chartBg);
        rows.addView(chartPanel, new LinearLayout.LayoutParams(-1, -2));
        HorizontalScrollView chartScroll = new HorizontalScrollView(this);
        chartScroll.setHorizontalScrollBarEnabled(false);
        chartPanel.addView(chartScroll);
        MonthlyColumnChartView chart = new MonthlyColumnChartView(this);
        chart.setMonths(report.optJSONArray("months"));
        chartScroll.addView(chart, new android.widget.FrameLayout.LayoutParams(dp(790), dp(300)));
        TextView chartTip = info("Vuốt ngang để xem biểu đồ 12 tháng.");
        chartTip.setTextSize(12); rows.addView(chartTip);
        TextView popularHeading = info("TOP GÓI CƯỚC PHỔ BIẾN");
        popularHeading.setTextSize(17);
        popularHeading.setTypeface(null, Typeface.BOLD);
        rows.addView(popularHeading);
        JSONArray popular = report.optJSONArray("popularPlans");
        if (popular == null || popular.length() == 0) {
            reportCard("CHƯA CÓ DỮ LIỆU", "Không có lượt mua trong năm này", "#64748B");
        } else for (int i = 0; i < popular.length(); i++) {
            JSONObject p = popular.optJSONObject(i);
            if (p != null) reportCard(p.optString("code") + " • " + p.optInt("purchases") + " lượt",
                    money(p.optDouble("revenue")), "#2463EB");
        }
        TextView note = info("Dữ liệu được tổng hợp từ giao dịch ghi nhận trong MobileData.");
        note.setTextSize(12); rows.addView(note);
    }


    private void drawCarrierReportRow(JSONObject carrier) {
        String code = carrier.optString("code", "");
        int targetIndex = 0;
        for (int i = 1; i < REPORT_CARRIER_CODES.length; i++) {
            if (REPORT_CARRIER_CODES[i].equalsIgnoreCase(code)) targetIndex = i;
        }
        final int selectedIndex = targetIndex;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(12), dp(16), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(reportCarrierIndex == selectedIndex ? Color.parseColor("#E8F0FF") : Color.WHITE);
        bg.setCornerRadius(dp(13));
        panel.setBackground(bg);
        TextView name = info(carrier.optString("name") + " • " + code);
        name.setTypeface(null, Typeface.BOLD);
        name.setTextSize(16);
        name.setPadding(0, 0, 0, dp(4));
        panel.addView(name);
        TextView desc = info("Thuê bao: " + carrier.optInt("subscribers")
                + "  •  Đang hoạt động: " + carrier.optInt("activeSubscribers")
                + "\nMua gói: " + money(carrier.optDouble("totalSpent"))
                + "  •  Nạp: " + money(carrier.optDouble("totalTopup"))
                + "\nLượt mua: " + carrier.optInt("purchases")
                + "  •  Lượt hủy: " + carrier.optInt("cancellations"));
        desc.setPadding(0, 0, 0, 0);
        panel.addView(desc);
        panel.setOnClickListener(v -> {
            if (reportCarrierIndex != selectedIndex) {
                reportCarrierIndex = selectedIndex;
                reload();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(8);
        rows.addView(panel, lp);
    }

    private void showReportDemoGuide() {
        new AlertDialog.Builder(this, R.style.MobileDataLightDialog)
                .setTitle("Hướng dẫn xem báo cáo")
                .setMessage("1. Chọn Tất cả nhà mạng để xem tổng hệ thống.\n\n"
                        + "2. Chạm Viettel, VinaPhone hoặc MobiFone để so sánh.\n\n"
                        + "3. Chuyển năm và xem biểu đồ mua gói theo tháng.\n\n"
                        + "4. Nhấn Xuất CSV, lưu file rồi mở bằng Excel.\n\n"
                        + "5. Đăng ký thêm gói và làm mới báo cáo để xem số liệu cập nhật.\n\n"
                        + "Dữ liệu được lấy từ các giao dịch ghi nhận trong hệ thống.")
                .setPositiveButton("Đóng", null)
                .show();
    }

    private Button reportButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(12);
        button.setAllCaps(false);
        if (text.contains("Xuất CSV")) {
            button.setTextColor(Color.WHITE);
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#13976E")));
        } else {
            button.setTextColor(Color.parseColor("#2463EB"));
            button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#E8F0FF")));
        }
        return button;
    }

    private static String csvCell(String raw) {
        String safe = raw == null ? "" : raw.replace("\"", "\"\"");
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        return "\"" + safe + "\"";
    }
    private void exportReport() {
        if (report.optInt("year", 0) == 0) {
            toast("Chưa có số liệu để xuất."); return;
        }
        StringBuilder csv = new StringBuilder();
        csv.append("MobileData - Bao cao quan tri nam ").append(reportYear).append("\r\n");
        csv.append("Nha mang,").append(csvCell(REPORT_CARRIER_NAMES[reportCarrierIndex])).append("\r\n");
        csv.append("Chi tieu mua goi,").append(report.optDouble("totalSpent")).append("\r\n");
        csv.append("Nap tien,").append(report.optDouble("totalTopup")).append("\r\n");
        csv.append("Luot mua,").append(report.optInt("purchases")).append("\r\n");
        csv.append("Luot huy,").append(report.optInt("cancellations")).append("\r\n\r\n");
        csv.append("Thang,Nap tien,Chi tieu,Luot mua,Luot huy\r\n");
        JSONArray months = report.optJSONArray("months");
        if (months != null) for (int i = 0; i < months.length(); i++) {
            JSONObject m = months.optJSONObject(i);
            if (m == null) continue;
            csv.append(m.optInt("month")).append(',')
                    .append(m.optDouble("topup")).append(',')
                    .append(m.optDouble("spent")).append(',')
                    .append(m.optInt("purchases")).append(',')
                    .append(m.optInt("cancellations")).append("\r\n");
        }
        csv.append("\r\nNha mang,Ma nha mang,Tong thue bao,Thue bao dang hoat dong,Nap tien,Mua goi,Luot mua,Luot huy\r\n");
        JSONArray carriers = report.optJSONArray("carriers");
        if (carriers != null) for (int i = 0; i < carriers.length(); i++) {
            JSONObject c = carriers.optJSONObject(i);
            if (c == null) continue;
            csv.append(csvCell(c.optString("name"))).append(',')
                    .append(csvCell(c.optString("code"))).append(',')
                    .append(c.optInt("subscribers")).append(',')
                    .append(c.optInt("activeSubscribers")).append(',')
                    .append(c.optDouble("totalTopup")).append(',')
                    .append(c.optDouble("totalSpent")).append(',')
                    .append(c.optInt("purchases")).append(',')
                    .append(c.optInt("cancellations")).append("\r\n");
        }
        csv.append("\r\nMa goi,Ten goi,Luot mua,Doanh thu mo phong\r\n");
        JSONArray popular = report.optJSONArray("popularPlans");
        if (popular != null) for (int i = 0; i < popular.length(); i++) {
            JSONObject p = popular.optJSONObject(i);
            if (p == null) continue;
            csv.append(csvCell(p.optString("code"))).append(',')
                    .append(csvCell(p.optString("name"))).append(',')
                    .append(p.optInt("purchases")).append(',')
                    .append(p.optDouble("revenue")).append("\r\n");
        }
        csvPending = csv.toString();
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("text/csv");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE, "MobileData_BaoCao_" + reportYear + "_" +
                (reportCarrierIndex == 0 ? "TatCa" : REPORT_CARRIER_CODES[reportCarrierIndex]) + ".csv");
        startActivityForResult(intent, CREATE_REPORT_CSV);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_OWNER_PHOTO) {
            if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
            try {
                android.net.Uri uri = data.getData();
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(in, null, options);
                }
                int sample = 1;
                while (options.outWidth / sample > 512 || options.outHeight / sample > 512) sample *= 2;
                options.inJustDecodeBounds = false; options.inSampleSize = sample;
                Bitmap bitmap;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    bitmap = BitmapFactory.decodeStream(in, null, options);
                }
                if (bitmap == null) { toast("Không đọc được ảnh."); return; }
                float scale = Math.min(1f, 320f / Math.max(bitmap.getWidth(), bitmap.getHeight()));
                Bitmap small = Bitmap.createScaledBitmap(bitmap,
                    Math.max(1, Math.round(bitmap.getWidth() * scale)),
                    Math.max(1, Math.round(bitmap.getHeight() * scale)), true);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                small.compress(Bitmap.CompressFormat.JPEG, 75, output);
                if (output.size() > 256 * 1024) { toast("Ảnh phải nhỏ hơn 256KB."); return; }
                selectedOwnerPhotoBase64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP);
                if (ownerPhotoPreview != null) ownerPhotoPreview.setImageBitmap(small);
                toast("Đã chọn ảnh. Nhấn Đăng ký để lưu.");
            } catch (Exception e) { toast("Không mở được ảnh đã chọn."); }
            return;
        }
        if (requestCode != CREATE_REPORT_CSV || resultCode != Activity.RESULT_OK || data == null)
            return;
        Uri uri = data.getData();
        if (uri == null) return;
        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new IllegalStateException("Không thể mở file");
            output.write(new byte[] {(byte)0xEF, (byte)0xBB, (byte)0xBF});
            output.write(csvPending.getBytes(StandardCharsets.UTF_8));
            toast("Đã lưu báo cáo CSV.");
        } catch (Exception ex) {
            toast("Không thể xuất file: " + ex.getMessage());
        }
    }

    private void reportCard(String heading, String value, String color) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(14), dp(18), dp(14));
        GradientDrawable back = new GradientDrawable();
        back.setColor(Color.WHITE); back.setCornerRadius(dp(16));
        box.setBackground(back);
        TextView label = info(heading); label.setTextSize(12);
        label.setTextColor(Color.parseColor("#64748B"));
        box.addView(label);
        TextView content = info(value); content.setTextSize(20);
        content.setTypeface(null, Typeface.BOLD);
        content.setTextColor(Color.parseColor(color));
        box.addView(content);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10); rows.addView(box, lp);
    }

    private MobileDataBusinessClient.Callback refreshCallback() {
        return new MobileDataBusinessClient.Callback() {
            @Override public void onSuccess(JSONObject obj) {
                if (!isFinishing()) { toast(obj.optString("message", "Đã lưu thành công")); reload(); }
            }
            @Override public void onError(String message) { if (!isFinishing()) toast(message); }
        };
    }

    private LinearLayout makeForm() {
        LinearLayout parent = new LinearLayout(this);
        parent.setOrientation(LinearLayout.VERTICAL);
        parent.setPadding(dp(20), dp(8), dp(20), dp(8));
        return parent;
    }
    private android.widget.ScrollView scroll(LinearLayout inner) {
        android.widget.ScrollView view = new android.widget.ScrollView(this);
        view.addView(inner); return view;
    }
    private EditText field(LinearLayout parent, String hint, String value, boolean numeric) {
        EditText text = new EditText(this);
        text.setSingleLine(true); text.setTextSize(16); text.setHint(hint);
        text.setTextColor(Color.parseColor("#15335C"));
        text.setHintTextColor(Color.parseColor("#64748B"));
        if (hint.startsWith("CCCD")) MobileDataInputRules.digits(text, 12);
        else if (hint.startsWith("Số điện thoại")) MobileDataInputRules.digits(text, 10);
        else if (hint.startsWith("Năm sinh")) MobileDataInputRules.digits(text, 4);
        else if (hint.startsWith("Giá ")) MobileDataInputRules.digits(text, 10);
        else if (hint.startsWith("Dung lượng")) MobileDataInputRules.decimal(text, 10);
        else if (hint.startsWith("Chu kỳ")) MobileDataInputRules.digits(text, 4);
        else if (hint.startsWith("Mã gói")) MobileDataInputRules.code(text);
        else if (hint.equals("Email")) MobileDataInputRules.email(text);
        else if (hint.startsWith("Họ") || hint.equals("Tên gói")) MobileDataInputRules.text(text, 100);
        else if (hint.equals("Quê quán")) MobileDataInputRules.text(text, 120);
        else if (hint.equals("Địa chỉ")) MobileDataInputRules.text(text, 200);
        else if (hint.equals("Mô tả")) MobileDataInputRules.text(text, 500);
        else if (hint.startsWith("Nhập phản hồi")) MobileDataInputRules.multiline(text, 2000);
        else MobileDataInputRules.text(text, 200);
        text.setText(value); // gán sau cấu hình để dữ liệu mẫu cũng theo đúng kiểu nhập
        if (hint.startsWith("Nhập phản hồi")) {
            text.setMinHeight(dp(105));
            text.setGravity(android.view.Gravity.TOP);
            parent.addView(text, new LinearLayout.LayoutParams(-1, -2));
        } else parent.addView(text, new LinearLayout.LayoutParams(-1, dp(55)));
        return text;
    }
    private Spinner spinner(LinearLayout parent, String[] choices) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, choices);
        spinner.setAdapter(adapter);
        parent.addView(spinner, new LinearLayout.LayoutParams(-1, dp(54)));
        return spinner;
    }
    private CheckBox check(LinearLayout parent, String title, boolean value) {
        CheckBox box = new CheckBox(this); box.setText(title); box.setChecked(value); parent.addView(box); return box;
    }
    private TextView info(String message) {
        TextView view = new TextView(this);
        view.setText(message); view.setTextColor(Color.parseColor("#15335C"));
        view.setTextSize(15); view.setPadding(dp(16), dp(13), dp(12), dp(13));
        return view;
    }
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private void toast(String msg) { Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); }
    private JSONObject json(String key, Object value) {
        JSONObject result = new JSONObject();
        try { result.put(key, value); } catch (Exception ignored) { }
        return result;
    }
    private String money(double v) {
        return NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(v) + "đ";
    }
    @Override protected void onStart() {
        super.onStart();
        if (liveEvents != null) liveEvents.start();
    }
    @Override protected void onStop() {
        if (liveEvents != null) liveEvents.stop();
        super.onStop();
    }

}
