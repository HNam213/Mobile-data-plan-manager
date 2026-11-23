package com.example.mobiledata.fragments;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.FrameLayout;
import android.view.ViewParent;

import androidx.appcompat.app.AlertDialog;

import org.json.JSONObject;

import com.example.mobiledata.utils.MobileDataBusinessClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.mobiledata.R;
import com.example.mobiledata.utils.MobileDataInputRules;
import com.example.mobiledata.utils.MobileDataApiClient;
import com.example.mobiledata.utils.SessionManager;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Trang Mua gói: dữ liệu được lấy từ SQL Server qua Web API. */
public class BuyPackageFragment extends Fragment {

    private EditText edtSearch;
    private Spinner spPrice, spData, spPeriod;
    private TextView txtCount, txtEmpty;
    private LinearLayout packageContainer, popularContainer;

    private final List<MobileDataApiClient.Plan> allPlans = new ArrayList<>();
    private final Locale vietnamese = Locale.forLanguageTag("vi-VN");
    private boolean loading;
    private boolean loadFailed;
    private int loadVersion;
    private boolean hasLoaded;

    public BuyPackageFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_buy_package, container, false);

        edtSearch = view.findViewById(R.id.edtSearchPackage);
        if (edtSearch != null) MobileDataInputRules.text(edtSearch, 80);
        edtSearch.setTextColor(Color.parseColor("#15335C"));
        edtSearch.setHintTextColor(Color.parseColor("#64748B"));
        edtSearch.setTypeface(null, Typeface.BOLD);
        edtSearch.setTextSize(16);
        spPrice = view.findViewById(R.id.spPrice);
        spData = view.findViewById(R.id.spData);
        spPeriod = view.findViewById(R.id.spPeriod);
        txtCount = view.findViewById(R.id.txtPackageCount);
        txtEmpty = view.findViewById(R.id.txtEmptyPackages);
        packageContainer = view.findViewById(R.id.packageListContainer);
        popularContainer = view.findViewById(R.id.popularContainer);

        updateHeaderIdentity(view);
        setupFilters();
        setupSearch();

        // Nếu người dùng chọn "Mua lại" ở tab Gói dùng, chỉ lọc tới mã gói đó.
        Bundle args = getArguments();
        if (args != null) {
            String requestedCode = args.getString("prefill_plan_code", "").trim();
            if (!requestedCode.isEmpty()) {
                edtSearch.setText(requestedCode);
                edtSearch.setSelection(edtSearch.length());
            }
        }

        txtEmpty.setOnClickListener(v -> {
            if (loadFailed && !loading) loadPlansFromApi();
        });

        // Không thêm gói cố định trong Java; tất cả sẽ đến từ API.
        loadPlansFromApi();
        return view;
    }

    /** Giao diện XML cũ còn ghi cứng số demo 0917 và VinaPhone. Đồng bộ từ phiên API. */
    private void updateHeaderIdentity(View root) {
        SessionManager session = new SessionManager(requireContext());
        String phone = session.getPhone();
        String carrier = session.getCarrier();
        if (phone == null || phone.trim().isEmpty()) return;
        fixStaticLabels(root, phone, carrier == null ? "" : carrier);
    }

    private void fixStaticLabels(View current, String phone, String carrier) {
        if (current instanceof TextView) {
            TextView label = (TextView) current;
            String text = label.getText().toString();
            // Header mua gói: thay số VinaPhone seed bằng số hiện đang đăng nhập.
            if (text.contains("0917 443 781") || text.contains("0917443781")) {
                label.setText(phone);
                label.setCompoundDrawablesWithIntrinsicBounds(0,0,0,0);
                label.setOnClickListener(null);
                label.setClickable(false);
                // Bỏ ký hiệu xổ xuống nếu là TextView nằm cùng thẻ số điện thoại.
                ViewParent parent = label.getParent();
                if (parent instanceof ViewGroup) {
                    ViewGroup group=(ViewGroup)parent;
                    for(int i=0;i<group.getChildCount();i++){
                        View sibling=group.getChildAt(i);
                        if(sibling instanceof TextView && sibling!=label){
                            String t=((TextView)sibling).getText().toString().trim();
                            if(t.equals("⌄")||t.equals("▼")||t.equals("▾")||t.equals("v"))sibling.setVisibility(View.GONE);
                        }
                    }
                }
            } else if (text.contains("Chỉ hiển thị gói cước")) {
                label.setText("Chỉ hiển thị gói cước " + carrier);
            } else if (text.contains("gói cước VinaPhone") && !carrier.isEmpty()) {
                label.setText(text.replace("VinaPhone", carrier));
            }
        }
        if(current instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)current;
            for(int i=0;i<group.getChildCount();i++) fixStaticLabels(group.getChildAt(i),phone,carrier);
        }
    }

    @Override public void onResume() {
        super.onResume();
        if (getView()!=null) updateHeaderIdentity(getView());
    }

    private String getCurrentCarrierCode() {
        // Người dùng chỉ thấy các gói thuộc nhà mạng của thuê bao đang đăng nhập.
        String carrier = new SessionManager(requireContext()).getCarrier();
        switch (carrier) {
            case "VinaPhone": return "VN";
            case "Viettel": return "VT";
            case "MobiFone": return "MB";
            default: return "";
        }
    }

    private void loadPlansFromApi() {
        if (loading) return;
        String carrier = getCurrentCarrierCode();
        if (carrier.isEmpty()) {
            loadFailed = true;
            txtEmpty.setText("Chưa xác định được nhà mạng của thuê bao.");
            refreshPackages();
            return;
        }

        loading = true;
        loadFailed = false;
        txtCount.setText("Đang tải...");
        txtEmpty.setText("Đang tải gói cước từ SQL Server...");
        txtEmpty.setVisibility(View.VISIBLE);
        packageContainer.removeAllViews();
        popularContainer.removeAllViews();
        int requestVersion = ++loadVersion;

        MobileDataApiClient.getPlans(carrier, new MobileDataApiClient.PlansCallback() {
            @Override
            public void onSuccess(List<MobileDataApiClient.Plan> plans) {
                if (!isAdded() || packageContainer == null || requestVersion != loadVersion) return;
                loading = false;
                hasLoaded = true;
                loadFailed = false;
                allPlans.clear();
                allPlans.addAll(plans);
                refreshPackages();
            }

            @Override
            public void onError(String message) {
                if (!isAdded() || packageContainer == null || requestVersion != loadVersion) return;
                loading = false;
                loadFailed = true;
                allPlans.clear();
                txtEmpty.setText(message + "\nChạm vào đây để thử lại.");
                txtEmpty.setVisibility(View.VISIBLE);
                txtCount.setText("Chưa tải được");
            }
        });
    }

    private void setupFilters() {
        setupSpinner(spPrice, new String[]{
                "Mức giá", "Dưới 50k", "50k - 100k", "Trên 100k"
        });
        setupSpinner(spData, new String[]{
                "Dung lượng", "Dưới 3GB", "3 - 5GB", "Từ 6GB"
        });
        setupSpinner(spPeriod, new String[]{
                "Chu kỳ", "1 ngày", "7 ngày", "30 ngày"
        });

        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (hasLoaded && !loading && !loadFailed) refreshPackages();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        };
        spPrice.setOnItemSelectedListener(listener);
        spData.setOnItemSelectedListener(listener);
        spPeriod.setOnItemSelectedListener(listener);
    }

    private void setupSpinner(Spinner spinner, String[] options) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(), R.layout.item_spinner_selected, android.R.id.text1, options);
        adapter.setDropDownViewResource(R.layout.item_spinner_light);
        spinner.setAdapter(adapter);
        spinner.setPopupBackgroundDrawable(new ColorDrawable(Color.WHITE));
    }

    private void setupSearch() {
        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (hasLoaded && !loading && !loadFailed) refreshPackages();
            }
            @Override
            public void afterTextChanged(Editable s) { }
        });
    }

    private void refreshPackages() {
        if (packageContainer == null || txtEmpty == null) return;
        if (loading || loadFailed) return;
        packageContainer.removeAllViews();
        List<MobileDataApiClient.Plan> matched = getFilteredPlans();
        for (MobileDataApiClient.Plan plan : matched) addPackageRow(plan);
        showPopularPackages(matched);
        txtCount.setText(matched.size() + " gói");
        txtEmpty.setText("Không có gói cước phù hợp.\nHãy thử từ khóa hoặc bộ lọc khác.");
        txtEmpty.setVisibility(matched.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private List<MobileDataApiClient.Plan> getFilteredPlans() {
        List<MobileDataApiClient.Plan> matched = new ArrayList<>();
        String keyword = edtSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        int priceFilter = spPrice.getSelectedItemPosition();
        int dataFilter = spData.getSelectedItemPosition();
        int periodFilter = spPeriod.getSelectedItemPosition();

        for (MobileDataApiClient.Plan plan : allPlans) {
            // Lọc theo mã, tên, giá, dung lượng, chu kỳ và nhà mạng.
            String searchable = (plan.code + " " + plan.name + " " + plan.carrierName
                    + " " + plan.dataText + " " + plan.price + " "
                    + formatPrice(plan.price) + " " + plan.days + " ngày")
                    .toLowerCase(Locale.ROOT);
            if (!searchable.contains(keyword)) continue;
            if (priceFilter == 1 && plan.price >= 50000) continue;
            if (priceFilter == 2 && (plan.price < 50000 || plan.price > 100000)) continue;
            if (priceFilter == 3 && plan.price <= 100000) continue;

            // Đây là dung lượng được cấp theo đơn vị hiển thị trên gói,
            // không phải dung lượng Internet thực tế đã sử dụng.
            if (dataFilter == 1 && plan.dataGb >= 3) continue;
            if (dataFilter == 2 && (plan.dataGb < 3 || plan.dataGb > 5)) continue;
            if (dataFilter == 3 && plan.dataGb < 6) continue;
            if (periodFilter == 1 && plan.days != 1) continue;
            if (periodFilter == 2 && plan.days != 7) continue;
            if (periodFilter == 3 && plan.days != 30) continue;
            matched.add(plan);
        }
        return matched;
    }

    private void addPackageRow(MobileDataApiClient.Plan plan) {
        View row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_buy_package, packageContainer, false);
        ((TextView) row.findViewById(R.id.txtPosterName)).setText(plan.code);
        ((TextView) row.findViewById(R.id.txtPosterData)).setText(plan.dataText);
        ((TextView) row.findViewById(R.id.txtPackageName)).setText(plan.code);
        ((TextView) row.findViewById(R.id.txtPackageData))
                .setText(plan.dataText + " • " + plan.days + " ngày");
        ((TextView) row.findViewById(R.id.txtPackagePrice)).setText(formatPrice(plan.price));
        ((TextView) row.findViewById(R.id.txtPackageBadge))
                .setText(plan.popular ? "Phổ biến" : "Gói cước");

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        packageContainer.addView(row, params);
        row.setOnClickListener(v -> showPackageDetail(plan));
    }

    private void showPopularPackages(List<MobileDataApiClient.Plan> matched) {
        popularContainer.removeAllViews();
        int shown = 0;
        for (MobileDataApiClient.Plan plan : matched) {
            if (!plan.popular || shown >= 3) continue;
            addPopularCard(plan);
            shown++;
        }
    }

    private void addPopularCard(MobileDataApiClient.Plan plan) {
        View card = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_home_plan, popularContainer, false);
        ((TextView) card.findViewById(R.id.txtPlanName)).setText(plan.code);
        ((TextView) card.findViewById(R.id.txtPlanData)).setText(plan.dataText);
        ((TextView) card.findViewById(R.id.txtPlanPrice)).setText(formatPrice(plan.price));
        ((TextView) card.findViewById(R.id.txtPlanPeriod)).setText("/" + plan.days + " ngày");
        ((TextView) card.findViewById(R.id.txtPlanBadge)).setText("Phổ biến");
        ((TextView) card.findViewById(R.id.txtPlanRating)).setText("★ Nổi bật");
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(154), dp(192));
        params.rightMargin = dp(12);
        popularContainer.addView(card, params);
        card.setOnClickListener(v -> showPackageDetail(plan));
    }

    private void showPackageDetail(MobileDataApiClient.Plan plan) {
        String summary = "Nhà mạng: " + plan.carrierName
                + "\nDung lượng: " + plan.dataText
                + "\nChu kỳ: " + plan.days + " ngày"
                + "\nGiá: " + formatPrice(plan.price)
                + "\n\nGiá gói sẽ được trừ vào số dư MobileData. Gói chỉ được kích hoạt bên trong ứng dụng."
                + "\nGói không tự gia hạn.";
        new AlertDialog.Builder(requireContext(), R.style.MobileDataLightDialog)
                .setTitle("Đăng ký " + plan.code + "?")
                .setMessage(summary)
                .setNegativeButton("Đóng", null)
                .setPositiveButton("Đăng ký", (dialog, which) -> buyPlan(plan))
                .show();
    }

    private void buyPlan(MobileDataApiClient.Plan plan) {
        String token = new SessionManager(requireContext()).getAccessToken();
        if (token.isEmpty()) {
            Toast.makeText(requireContext(), "Hãy đăng nhập bằng OTP API trước.", Toast.LENGTH_LONG).show();
            return;
        }
        JSONObject body = new JSONObject();
        try { body.put("planId", plan.id); } catch (Exception ignored) { }
        MobileDataBusinessClient.post("/api/me/plans", body, token,
                new MobileDataBusinessClient.Callback() {
            @Override public void onSuccess(JSONObject result) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), result.optString("message", "Đăng ký thành công"),
                        Toast.LENGTH_LONG).show();
            }
            @Override public void onError(String message) {
                if (!isAdded()) return;
                new AlertDialog.Builder(requireContext(), R.style.MobileDataLightDialog)
                        .setTitle("Chưa thể đăng ký gói")
                        .setMessage(message)
                        .setPositiveButton("Đóng", null).show();
            }
        });
    }

    private String formatPrice(int amount) {
        return NumberFormat.getNumberInstance(vietnamese).format(amount) + "đ";
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onDestroyView() {
        // Bỏ qua kết quả các yêu cầu mạng thuộc giao diện đã bị đóng.
        loadVersion++;
        loading = false;
        hasLoaded = false;
        edtSearch = null;
        spPrice = spData = spPeriod = null;
        txtCount = txtEmpty = null;
        packageContainer = popularContainer = null;
        super.onDestroyView();
    }
}
