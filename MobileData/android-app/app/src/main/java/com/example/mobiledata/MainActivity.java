package com.example.mobiledata;

import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.mobiledata.fragments.HomeFragment;
import com.example.mobiledata.fragments.BuyPackageFragment;
import com.example.mobiledata.fragments.MyPackageFragment;
import com.example.mobiledata.fragments.AccountFragment;
import com.example.mobiledata.fragments.HistoryFragment;

import android.content.Intent;
import com.example.mobiledata.activities.LoginActivity;
import com.example.mobiledata.utils.SessionManager;
import com.example.mobiledata.utils.MobileDataAuthClient;
import com.example.mobiledata.utils.SupportRealtimeClient;
import androidx.appcompat.app.AlertDialog;
import org.json.JSONObject;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigation;
    // Mã gói chuyển từ tab "Gói dùng" sang tab "Mua gói".
    private String pendingBuyPlanCode = "";
    private SupportRealtimeClient liveEvents;
    private boolean checkingSession = false;
    private boolean sessionWarningShown = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Không cho phép truy cập màn hình chính khi chưa đăng nhập.
        if (!new SessionManager(this).isLoggedIn()) {

            Intent intent = new Intent(
                    this,
                    LoginActivity.class
            );

            startActivity(intent);
            finish();
            return;
        }

        // Cho phép giao diện hoạt động với cơ chế Edge-to-Edge.
        EdgeToEdge.enable(this);

        setContentView(R.layout.activity_main);
        liveEvents = new SupportRealtimeClient(this);

        View main = findViewById(R.id.main);

        // Chỉ chừa khoảng trống cho thanh trạng thái phía trên.
        // BottomNavigationView của Material tự xử lý vùng điều hướng
        // hệ thống ở cạnh dưới (navigation bar inset).
        // Nếu cộng thêm bars.bottom vào main thì khoảng đệm bị nhân đôi,
        // khiến thanh 5 mục nằm cao hơn đáy màn hình.
        ViewCompat.setOnApplyWindowInsetsListener(
                main, (v, insets) -> {

                    Insets bars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );

                    v.setPadding(
                            bars.left,
                            bars.top,
                            bars.right,
                            0
                    );

                    // Giữ nguyên insets để BottomNavigationView nhận được
                    // khoảng đệm đáy do Android/Material xử lý.
                    return insets;
                }
        );

        bottomNavigation =
                findViewById(R.id.bottomNavigation);

        // Tắt nền chỉ báo hình viên thuốc của mục được chọn.
        bottomNavigation.setItemActiveIndicatorEnabled(false);

        // Xử lý khi người dùng chọn một mục ở thanh dưới.
        bottomNavigation.setOnItemSelectedListener(item -> {

            int id = item.getItemId();

            if (id == R.id.nav_home) {

                // Trang chủ sử dụng giao diện HomeFragment thật.
                showHome();

            } else if (id == R.id.nav_buy) {

                // Hiển thị giao diện mua gói cước thực tế.
                showBuyPackages();

            } else if (id == R.id.nav_packages) {

                // Mở màn hình quản lý gói cước có thời gian thực.
                showMyPackages();

            } else if (id == R.id.nav_account) {

                // Hiển thị màn hình Tài khoản có chức năng nạp tiền.
                showAccount();

            } else if (id == R.id.nav_history) {

                // Chuyển sang màn hình Lịch sử giao dịch.
                showHistory();

            } else {
                return false;
            }

            return true;
        });

        // Chỉ tạo trang chủ lần đầu.
        // Tránh ghi đè Fragment khi xoay màn hình hoặc tái tạo Activity.
        if (savedInstanceState == null) {
            showHome();
        }
    }


    private void showHistory() {

        // Thay Fragment đang hiển thị bằng HistoryFragment.
        getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                        R.id.fragment_container,
                        new HistoryFragment()
                )
                .commit();
    }

    private void showAccount() {

        // Thay Fragment hiện tại bằng trang Tài khoản.
        getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                        R.id.fragment_container,
                        new AccountFragment()
                )
                .commit();
    }

    private void showMyPackages() {

        // Thay nội dung hiện tại bằng màn hình Gói đang dùng.
        getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                        R.id.fragment_container,
                        new MyPackageFragment()
                )
                .commit();
    }
    private void showBuyPackages() {

        // Chuyển mã gói cần mua lại qua arguments của Fragment (nếu có).
        BuyPackageFragment fragment = new BuyPackageFragment();
        if (!pendingBuyPlanCode.isEmpty()) {
            Bundle args = new Bundle();
            args.putString("prefill_plan_code", pendingBuyPlanCode);
            fragment.setArguments(args);
            pendingBuyPlanCode = "";
        }

        getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.fragment_container, fragment)
                .commit();
    }

    /**
     * Được MyPackageFragment gọi khi nhấn "Mua lại" một gói đã đăng ký.
     * Chỉ chuyển trang và lọc theo mã gói, không tự trừ tiền/mua gói.
     */
    public void openBuyForPlan(String planCode) {
        pendingBuyPlanCode = planCode == null ? "" : planCode.trim();
        if (bottomNavigation == null) return;
        if (bottomNavigation.getSelectedItemId() == R.id.nav_buy) {
            // Trường hợp hiện đang ở tab Mua gói thì tạo mới Fragment để áp dụng mã gói.
            showBuyPackages();
        } else {
            // Listener của BottomNavigationView sẽ tự gọi showBuyPackages().
            bottomNavigation.setSelectedItemId(R.id.nav_buy);
        }
    }
    private void showHome() {

        // Thay vùng nội dung bằng HomeFragment.
        getSupportFragmentManager()
                .beginTransaction()
                .setReorderingAllowed(true)
                .replace(
                        R.id.fragment_container,
                        new HomeFragment()
                )
                .commit();
    }

    @Override protected void onResume() {
        super.onResume();
        verifyApiSession();
        if (liveEvents != null) liveEvents.start();
    }

    // Kiểm tra lại token mỗi khi quay về trang chính, ví dụ sau khi Web API khởi động lại.
    private void verifyApiSession() {
        if (checkingSession || sessionWarningShown || isFinishing()) return;
        SessionManager session = new SessionManager(this);
        if (!session.isLoggedIn() || session.getAccessToken().isEmpty()) {
            session.logout();
            redirectToLogin();
            return;
        }
        String token = session.getAccessToken();
        checkingSession = true;
        MobileDataAuthClient.me(token, new MobileDataAuthClient.ResultCallback() {
            @Override public void onSuccess(JSONObject data) {
                checkingSession = false;
                if (isFinishing() || isDestroyed()) return;
                // Phòng trường hợp đã chuyển tài khoản trong lúc chờ HTTP.
                if (!token.equals(new SessionManager(MainActivity.this).getAccessToken())) return;
                if (!session.getPhone().equals(data.optString("phone", ""))) {
                    showExpiredSession();
                }
            }
            @Override public void onError(String message) {
                checkingSession = false;
                if (isFinishing() || isDestroyed()) return;
                if (!token.equals(new SessionManager(MainActivity.this).getAccessToken())) return;
                // Mất mạng tạm thời không đồng nghĩa với việc tài khoản bị đăng xuất.
                if (message != null && (message.contains("Phiên đăng nhập")
                        || message.contains("Tài khoản đã bị khóa"))) {
                    showExpiredSession();
                }
            }
        });
    }

    private void showExpiredSession() {
        if (sessionWarningShown || isFinishing()) return;
        sessionWarningShown = true;
        if (liveEvents != null) liveEvents.stop();
        new AlertDialog.Builder(this, R.style.MobileDataLightDialog)
                .setTitle("Phiên đăng nhập đã kết thúc")
                .setMessage("Vui lòng đăng nhập lại để tiếp tục sử dụng MobileData.")
                .setCancelable(false)
                .setPositiveButton("Đăng nhập", (dialog, which) -> {
                    new SessionManager(this).logout();
                    redirectToLogin();
                })
                .show();
    }

    private void redirectToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override protected void onPause() {
        if (liveEvents != null) liveEvents.stop();
        super.onPause();
    }

    public void navigateToTab(int menuId) {

        // Đổi mục được chọn trên BottomNavigationView.
        // Listener ở trên sẽ tự hiển thị Fragment tương ứng.
        bottomNavigation.setSelectedItemId(menuId);
    }
}
