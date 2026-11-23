package com.example.mobiledata.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.example.mobiledata.MainActivity;
import com.example.mobiledata.R;
import com.example.mobiledata.activities.AdminActivity;
import com.example.mobiledata.activities.ChatActivity;
import com.example.mobiledata.activities.LoginActivity;
import com.example.mobiledata.activities.StatisticsActivity;
import com.example.mobiledata.activities.OwnerProfileActivity;
import com.example.mobiledata.activities.TopUpActivity;
import com.example.mobiledata.utils.MobileDataBusinessClient;
import com.example.mobiledata.utils.SupportDialogs;
import com.example.mobiledata.utils.SupportReplyNotifier;

import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.Locale;
import com.example.mobiledata.utils.SessionManager;

public class AccountFragment extends Fragment {

    private TextView txtBalance;

    public AccountFragment() {
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        // Nạp giao diện Tài khoản từ file XML.
        View view = inflater.inflate(
                R.layout.fragment_account,
                container,
                false
        );

        SessionManager session = new SessionManager(requireContext());

        // Hiển thị đúng thông tin của thuê bao đang đăng nhập.
        ((TextView) view.findViewById(R.id.txtAccountName))
                .setText(session.getName());
        ((TextView) view.findViewById(R.id.txtAccountHeaderPhone))
                .setText(session.getPhone());
        ((TextView) view.findViewById(R.id.txtAccountHeaderCarrier))
                .setText(session.getCarrier());
        ((TextView) view.findViewById(R.id.txtAccountPhone))
                .setText(session.getPhone());
        ((TextView) view.findViewById(R.id.txtAccountCarrier))
                .setText(session.getCarrier());

        txtBalance = view.findViewById(R.id.txtAccountBalance);

        // Nạp tiền mô phỏng.
        view.findViewById(R.id.btnAccountTopup)
                .setOnClickListener(v -> startActivity(new Intent(requireContext(), TopUpActivity.class)));

        // Mở hồ sơ đầy đủ bằng màn hình riêng, không dùng popup.
        view.findViewById(R.id.rowAccountProfile)
                .setOnClickListener(v -> startActivity(new Intent(requireContext(), OwnerProfileActivity.class)));

        // Chuyển đến các tab qua MainActivity.
        view.findViewById(R.id.rowAccountPackages)
                .setOnClickListener(v -> navigateToTab(R.id.nav_packages));
        view.findViewById(R.id.rowAccountHistory)
                .setOnClickListener(v -> navigateToTab(R.id.nav_history));
        view.findViewById(R.id.rowAccountChat)
                .setOnClickListener(v -> startActivity(new Intent(requireContext(), ChatActivity.class)));
        view.findViewById(R.id.rowAccountSupport)
                .setOnClickListener(v -> SupportDialogs.open(requireContext()));
        view.findViewById(R.id.rowAccountStatistics)
                .setOnClickListener(v -> startActivity(new Intent(requireContext(), StatisticsActivity.class)));

        View adminRow = view.findViewById(R.id.rowAccountAdmin);
        View adminDivider = view.findViewById(R.id.dividerAdmin);

        // USER: Ẩn hẳn mục quản trị và đường phân cách.
        // ADMIN: Hiển thị mục quản trị và cho phép nhấn vào.
        boolean isAdmin = session.isAdmin();
        view.findViewById(R.id.rowAccountSupport).setVisibility(isAdmin ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.rowAccountChat).setVisibility(isAdmin ? View.GONE : View.VISIBLE);
        view.findViewById(R.id.dividerChat).setVisibility(isAdmin ? View.GONE : View.VISIBLE);
        adminRow.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
        adminDivider.setVisibility(isAdmin ? View.VISIBLE : View.GONE);

        if (isAdmin) {
            adminRow.setOnClickListener(v -> openAdminPreview());
        }

        // Chỉ mở xác nhận; không xóa phiên ở đây.
        view.findViewById(R.id.btnAccountLogout)
                .setOnClickListener(v -> confirmLogout());

        updateBalance();
        return view;
    }

    private void openAdminPreview() {
        // Kiểm tra lại quyền trước khi mở AdminActivity.
        if (!new SessionManager(requireContext()).isAdmin()) {
            Toast.makeText(
                    requireContext(),
                    "Bạn không có quyền truy cập trang quản trị",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        Intent intent = new Intent(requireContext(), AdminActivity.class);
        startActivity(intent);
    }

    private void updateBalance() {
        if (txtBalance == null || !isAdded()) return;
        String token = new SessionManager(requireContext()).getAccessToken();
        txtBalance.setText("Đang tải...");
        MobileDataBusinessClient.get("/api/me/balance", token, new MobileDataBusinessClient.Callback() {
            @Override public void onSuccess(JSONObject result) {
                if (isAdded() && txtBalance != null)
                    txtBalance.setText(formatMoney(result.optDouble("balance")));
            }
            @Override public void onError(String message) {
                if (isAdded() && txtBalance != null) txtBalance.setText("Không tải được số dư");
            }
        });
    }

    private String formatMoney(double amount) {
        return NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(amount) + "đ";
    }

    private void confirmLogout() {
        new AlertDialog.Builder(requireContext(), R.style.MobileDataLightDialog)
                .setTitle("Đăng xuất")
                .setMessage("Bạn có chắc chắn muốn đăng xuất?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Đăng xuất", (dialog, which) -> {
                    // Chỉ xóa phiên khi người dùng xác nhận đăng xuất.
                    new SessionManager(requireContext()).logout();

                    Intent intent = new Intent(
                            requireContext(), LoginActivity.class
                    );

                    // Xóa ngăn xếp màn hình để Back không mở tài khoản cũ.
                    intent.addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK
                    );

                    startActivity(intent);
                    requireActivity().finish();
                })
                .show();
    }

    private void navigateToTab(int menuId) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateToTab(menuId);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Đọc lại số dư và kiểm tra phản hồi hỗ trợ khi quay về trang Tài khoản.
        if (isAdded()) SupportReplyNotifier.check(requireContext());
        if (txtBalance != null) {
            updateBalance();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        txtBalance = null;
    }
}
