package com.example.mobiledata.utils;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Patterns;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import java.util.Calendar;

/** Kiem tra ho so KH, hien canh bao tai tung truong nhap. */
public final class CustomerProfileRules {
    private static final int RED = Color.rgb(204, 45, 61);
    private CustomerProfileRules() { }
    private static String v(String value) { return value == null ? "" : value.trim(); }
    private static String v(EditText field) { return v(field.getText().toString()); }

    public static String validateCccd(String value) {
        String s = v(value);
        if (s.isEmpty()) return "Vui lòng nhập CCCD.";
        return s.matches("[0-9]{12}") ? null : "CCCD phải có đúng 12 chữ số.";
    }
    public static String validateFullName(String value) {
        String s = v(value);
        if (s.isEmpty()) return "Vui lòng nhập họ và tên.";
        if (s.length() > 100) return "Họ tên không được vượt quá 100 ký tự.";
        // Ho va ten ca nhan: it nhat 2 tu, chi co chu cai va dau tieng Viet.
        return s.matches("[\\p{L}\\p{M}]+(?:[ '-]+[\\p{L}\\p{M}]+)+")
                ? null : "Nhập đầy đủ họ và tên (ít nhất 2 từ), không chứa số.";
    }
    public static String validateGender(int index) {
        return index > 0 ? null : "Vui lòng chọn giới tính.";
    }
    public static String validateBirthYear(String value) {
        String s = v(value);
        if (s.isEmpty()) return "Vui lòng nhập năm sinh.";
        if (!s.matches("[0-9]{4}")) return "Năm sinh phải có 4 chữ số.";
        int year = Integer.parseInt(s);
        return year >= 1900 && year <= Calendar.getInstance().get(Calendar.YEAR)
                ? null : "Năm sinh chưa hợp lệ.";
    }
    private static String validatePlace(String value, int limit, String label) {
        String s = v(value);
        if (s.isEmpty()) return "Vui lòng nhập " + label.toLowerCase(java.util.Locale.ROOT) + ".";
        if (s.length() < 3 || s.length() > limit)
            return label + " cần từ 3 đến " + limit + " ký tự.";
        if (!s.matches("(?s).*\\p{L}.*"))
            return label + " phải có chữ, không được chỉ nhập số hoặc ký hiệu.";
        if (s.matches("(?s).*\\p{Cntrl}.*"))
            return label + " chứa ký tự không hợp lệ.";
        return null;
    }
    public static String validateHometown(String s) { return validatePlace(s, 120, "Quê quán"); }
    public static String validateAddress(String s) { return validatePlace(s, 200, "Địa chỉ"); }
    public static String validateEmail(String value) {
        String s = v(value);
        if (s.isEmpty()) return "Vui lòng nhập email.";
        return s.length() <= 150 && Patterns.EMAIL_ADDRESS.matcher(s).matches()
                ? null : "Email chưa đúng định dạng (ví dụ: ten@gmail.com).";
    }
    private static int dp(Context c, int value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }
    private static TextView warning(Context context) {
        TextView result = new TextView(context);
        result.setTextSize(12);
        result.setTextColor(RED);
        result.setPadding(dp(context, 4), dp(context, 3), 0, dp(context, 3));
        result.setVisibility(View.GONE);
        return result;
    }
    private static void label(LinearLayout parent, int index, String name) {
        TextView title = new TextView(parent.getContext());
        String content = name + "  ●";
        SpannableString styled = new SpannableString(content);
        styled.setSpan(new ForegroundColorSpan(RED), content.length() - 1,
                content.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setText(styled);
        title.setTextSize(13);
        title.setTextColor(Color.rgb(64, 88, 124));
        title.setPadding(0, dp(parent.getContext(), 7), 0, dp(parent.getContext(), 3));
        parent.addView(title, index);
    }
    /** Goi ngay sau khi da them truong EditText vao LinearLayout. */
    public static void attach(LinearLayout parent, EditText field, String addLabel) {
        int index = parent.indexOfChild(field);
        if (index < 0) return;
        if (addLabel != null) { label(parent, index, addLabel); index++; }
        TextView message = warning(parent.getContext());
        parent.addView(message, index + 1);
        field.setTag(message);
        field.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                message.setVisibility(View.GONE);
                field.setError(null);
            }
            public void afterTextChanged(Editable s) { }
        });
    }
    public static void attachGender(LinearLayout parent, Spinner spinner, String addLabel) {
        int index = parent.indexOfChild(spinner);
        if (index < 0) return;
        if (addLabel != null) { label(parent, index, addLabel); index++; }
        TextView message = warning(parent.getContext());
        parent.addView(message, index + 1);
        spinner.setTag(message);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (pos > 0) message.setVisibility(View.GONE);
            }
            public void onNothingSelected(android.widget.AdapterView<?> p) { }
        });
    }
    private static boolean show(EditText field, String message) {
        TextView warning = field.getTag() instanceof TextView ? (TextView) field.getTag() : null;
        if (warning != null) {
            warning.setText(message == null ? "" : "● " + message);
            warning.setVisibility(message == null ? View.GONE : View.VISIBLE);
        }
        field.setError(message);
        return message == null;
    }
    private static boolean showGender(Spinner spinner, String message) {
        TextView warning = spinner.getTag() instanceof TextView ? (TextView) spinner.getTag() : null;
        if (warning != null) {
            warning.setText(message == null ? "" : "● " + message);
            warning.setVisibility(message == null ? View.GONE : View.VISIBLE);
        }
        return message == null;
    }
    /** Bao loi dong thoi o TAT CA truong sai, giu nguyen form khi chua hop le. */
    public static boolean validateAll(EditText cccd, EditText name, Spinner gender,
            EditText birthYear, EditText hometown, EditText address, EditText email) {
        View firstInvalid = null;
        boolean valid = true;
        if (cccd != null && !show(cccd, validateCccd(v(cccd)))) { valid = false; firstInvalid = cccd; }
        if (!show(name, validateFullName(v(name)))) { valid = false; if (firstInvalid == null) firstInvalid = name; }
        if (!showGender(gender, validateGender(gender.getSelectedItemPosition()))) {
            valid = false; if (firstInvalid == null) firstInvalid = gender;
        }
        if (!show(birthYear, validateBirthYear(v(birthYear)))) {
            valid = false; if (firstInvalid == null) firstInvalid = birthYear;
        }
        if (!show(hometown, validateHometown(v(hometown)))) {
            valid = false; if (firstInvalid == null) firstInvalid = hometown;
        }
        if (!show(address, validateAddress(v(address)))) {
            valid = false; if (firstInvalid == null) firstInvalid = address;
        }
        if (!show(email, validateEmail(v(email)))) {
            valid = false; if (firstInvalid == null) firstInvalid = email;
        }
        if (!valid && firstInvalid != null) firstInvalid.requestFocus();
        return valid;
    }
}
