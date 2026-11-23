package com.example.mobiledata.activities;

import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.ViewGroup;
import android.text.InputFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.mobiledata.MainActivity;
import com.example.mobiledata.R;
import com.example.mobiledata.utils.MobileDataAuthClient;
import com.example.mobiledata.utils.MobileDataInputRules;
import com.example.mobiledata.utils.SessionManager;

import org.json.JSONObject;

/** Đăng nhập thuê bao bằng OTP mô phỏng; USER/ADMIN lấy từ SQL Server. */
public class LoginActivity extends AppCompatActivity {
    private EditText edtPhone;
    private Spinner spinnerCarrier;
    private Button btnRequestOtp;
    private ProgressBar progress;
    private boolean busy;

    private static final String[] CARRIERS = {
            "Chọn nhà mạng", "Viettel", "VinaPhone", "MobiFone"
    };
    private static final String[] CODES = {"", "VT", "VN", "MB"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_api);

        edtPhone = findViewById(R.id.edtApiLoginPhone);
        spinnerCarrier = findViewById(R.id.spApiLoginCarrier);
        btnRequestOtp = findViewById(R.id.btnApiRequestOtp);
        progress = findViewById(R.id.progressApiLogin);
        MobileDataInputRules.digits(edtPhone, 10);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, CARRIERS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCarrier.setAdapter(adapter);
        edtPhone.setTextColor(Color.parseColor("#15335C"));
        edtPhone.setHintTextColor(Color.parseColor("#64748B"));
        edtPhone.setTypeface(null, Typeface.BOLD);
        edtPhone.setTextSize(18);

        btnRequestOtp.setOnClickListener(v -> requestOtp());
        checkExistingSession();
    }

    private void checkExistingSession() {
        SessionManager session = new SessionManager(this);
        if (!session.isLoggedIn()) {
            // Nếu ứng dụng tự hết hạn (55 phút), gửi đăng xuất để giải phóng phiên máy chủ.
            if (!session.getAccessToken().isEmpty()) session.logout();
            return;
        }
        String token = session.getAccessToken();
        if (token.isEmpty()) {
            // Phiên demo cũ không được dùng để mở khóa tài khoản SQL.
            session.logout();
            return;
        }
        setBusy(true);
        MobileDataAuthClient.me(token, new MobileDataAuthClient.ResultCallback() {
            @Override public void onSuccess(JSONObject data) {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                if (!data.optString("phone", "").equals(session.getPhone())) {
                    session.logout();
                    return;
                }
                String role = data.optString("role", "");
                if (!role.equals("ADMIN") && !role.equals("USER")) {
                    session.logout();
                    return;
                }
                session.refreshProfile(
                        data.optString("phone", ""),
                        data.optString("carrierName", ""),
                        data.optString("fullName", ""), role);
                openMain();
            }
            @Override public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                // Mất kết nối không phải là đăng xuất: giữ token để không bị khóa nhầm.
                if (message == null || !(message.contains("Phiên đăng nhập")
                        || message.contains("Tài khoản đã bị khóa"))) {
                    showMessage("Không kiểm tra được phiên hiện tại. Kiểm tra kết nối API rồi mở lại ứng dụng.");
                    return;
                }
                session.logout();
                showMessage("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
            }
        });
    }

    private void requestOtp() {
        if (busy) return;
        String phone = edtPhone.getText().toString().trim();
        if (!MobileDataInputRules.phone(edtPhone)) return;
        int index = spinnerCarrier.getSelectedItemPosition();
        if (index <= 0 || index >= CODES.length) {
            Toast.makeText(this, "Vui lòng chọn nhà mạng", Toast.LENGTH_SHORT).show();
            return;
        }
        String carrierCode = CODES[index];
        setBusy(true);
        MobileDataAuthClient.requestOtp(phone, carrierCode, new MobileDataAuthClient.ResultCallback() {
            @Override public void onSuccess(JSONObject data) {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                long requestId = data.optLong("requestId", -1);
                String devOtp = data.optString("devOtp", "");
                if (requestId <= 0) {
                    showMessage("API không trả về requestId hợp lệ.");
                    return;
                }
                showOtpDialog(requestId, devOtp);
            }
            @Override public void onError(String message) {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                showMessage(message);
            }
        });
    }

    private void showOtpDialog(long requestId, String devOtp) {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_otp_api, null, false);
        EditText edtOtp = content.findViewById(R.id.edtApiOtp);
        TextView txtDemo = content.findViewById(R.id.txtApiDevOtp);
        ProgressBar progressOtp = content.findViewById(R.id.progressApiOtp);
        Button btnResend = content.findViewById(R.id.btnApiResendOtp);
        MobileDataInputRules.digits(edtOtp, 6);

        if (devOtp.isEmpty()) {
            txtDemo.setText("Không nhận được mã OTP. Kiểm tra cấu hình máy chủ.");
        } else {
            txtDemo.setText("Mã OTP: " + devOtp);
        }
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.MobileDataLightDialog)
                .setTitle("Xác thực OTP")
                .setView(content)
                .setNegativeButton("Hủy", (d, which) -> { })
                .setPositiveButton("Xác nhận", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null)
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.white);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.parseColor("#2463EB"));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.parseColor("#64748B"));
            Button verifyButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            verifyButton.setOnClickListener(v -> {
                String otp = edtOtp.getText().toString().trim();
                if (!MobileDataInputRules.otp(edtOtp)) return;
                verifyButton.setEnabled(false);
                btnResend.setEnabled(false);
                progressOtp.setVisibility(View.VISIBLE);
                MobileDataAuthClient.verifyOtp(requestId, otp,
                        new MobileDataAuthClient.ResultCallback() {
                    @Override public void onSuccess(JSONObject response) {
                        if (isFinishing() || isDestroyed()) return;
                        JSONObject profile = response.optJSONObject("profile");
                        String token = response.optString("accessToken", "");
                        if (profile == null || token.isEmpty()) {
                            verifyButton.setEnabled(true);
                            btnResend.setEnabled(true);
                            progressOtp.setVisibility(View.GONE);
                            showMessage("API trả về thông tin đăng nhập chưa đầy đủ.");
                            return;
                        }
                        String role = profile.optString("role", "");
                        String name = profile.optString("fullName", "");
                        String phone = profile.optString("phone", "");
                        String carrierName = profile.optString("carrierName", "");
                        if (!(role.equals("USER") || role.equals("ADMIN")) || phone.isEmpty()
                                || carrierName.isEmpty()) {
                            verifyButton.setEnabled(true);
                            btnResend.setEnabled(true);
                            progressOtp.setVisibility(View.GONE);
                            showMessage("Hồ sơ trả về không hợp lệ.");
                            return;
                        }
                        new SessionManager(LoginActivity.this).saveApiLogin(
                                phone, carrierName, name, role, token);
                        dialog.dismiss();
                        openMain();
                    }
                    @Override public void onError(String message) {
                        if (isFinishing() || isDestroyed()) return;
                        verifyButton.setEnabled(true);
                        btnResend.setEnabled(true);
                        progressOtp.setVisibility(View.GONE);
                        showMessage(message);
                    }
                });
            });
            btnResend.setOnClickListener(v -> {
                dialog.dismiss();
                // Máy chủ tự giới hạn tối thiểu 30 giây giữa các yêu cầu OTP.
                requestOtp();
            });
        });
        dialog.show();
    }

    private void setBusy(boolean value) {
        busy = value;
        btnRequestOtp.setEnabled(!value);
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
    }

    private void showMessage(String text) {
        // Thông báo rõ ràng nếu tài khoản đang đăng nhập trên LDPlayer/thiết bị khác.
        if (text != null && text.contains("Tài khoản đang được đăng nhập")) {
            new AlertDialog.Builder(this, R.style.MobileDataLightDialog)
                    .setTitle("Tài khoản đang được sử dụng")
                    .setMessage(text)
                    .setPositiveButton("Đã hiểu", null)
                    .show();
            return;
        }
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
    }

    private void openMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
