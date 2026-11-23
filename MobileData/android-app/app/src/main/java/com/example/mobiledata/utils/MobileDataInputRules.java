package com.example.mobiledata.utils;

import android.text.InputFilter;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.util.Patterns;
import android.widget.EditText;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Locale;

/** Quy tắc nhập liệu dùng chung. Web API phải kiểm tra lại trước khi lưu. */
public final class MobileDataInputRules {
    private MobileDataInputRules() { }

    public static String value(EditText field) { return field.getText().toString().trim(); }
    private static void max(EditText field, int length) {
        field.setFilters(new InputFilter[]{new InputFilter.LengthFilter(length)});
    }
    public static void digits(EditText field, int length) {
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setKeyListener(DigitsKeyListener.getInstance("0123456789"));
        max(field, length);
    }
    public static void decimal(EditText field, int length) {
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        field.setKeyListener(DigitsKeyListener.getInstance("0123456789.,"));
        max(field, length);
    }
    public static void text(EditText field, int length) {
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        max(field, length);
    }
    public static void email(EditText field) {
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        max(field, 150);
    }
    public static void code(EditText field) {
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        max(field, 20);
    }
    public static void multiline(EditText field, int length) {
        field.setSingleLine(false);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        max(field, length);
    }
    public static boolean error(EditText field, String message) {
        field.setError(message);
        field.requestFocus();
        return false;
    }
    public static boolean phone(EditText field) {
        String s = value(field);
        return s.matches("0[35789][0-9]{8}") || error(field, "Số điện thoại phải có 10 chữ số, bắt đầu bằng 03, 05, 07, 08 hoặc 09.");
    }
    public static boolean cccd(EditText field) {
        return value(field).matches("[0-9]{12}") || error(field, "CCCD phải có đúng 12 chữ số.");
    }
    public static boolean otp(EditText field) {
        return value(field).matches("[0-9]{6}") || error(field, "Nhập đủ 6 chữ số OTP.");
    }
    public static boolean name(EditText field) {
        String s = value(field);
        return (s.length() >= 2 && s.length() <= 100 && s.matches("(?s).*\\p{L}.*")
                && !s.matches("(?s).*\\p{Cntrl}.*"))
                || error(field, "Họ tên cần từ 2 đến 100 ký tự.");
    }
    public static boolean birthYear(EditText field) {
        String s = value(field);
        if (s.isEmpty()) return true;
        if (!s.matches("[0-9]{4}")) return error(field, "Năm sinh phải có 4 chữ số.");
        int year = Integer.parseInt(s);
        return (year >= 1900 && year <= Calendar.getInstance().get(Calendar.YEAR))
                || error(field, "Năm sinh phải từ 1900 đến năm hiện tại.");
    }
    public static boolean optionalEmail(EditText field) {
        String s = value(field);
        return s.isEmpty() || (s.length() <= 150 && Patterns.EMAIL_ADDRESS.matcher(s).matches())
                || error(field, "Email chưa đúng định dạng. Ví dụ: ten@gmail.com.");
    }
    public static boolean optionalText(EditText field, int length, String label) {
        return value(field).length() <= length || error(field, label + " tối đa " + length + " ký tự.");
    }
    public static boolean packageCode(EditText field) {
        return value(field).toUpperCase(Locale.ROOT).matches("[A-Z0-9_-]{2,20}")
                || error(field, "Mã gói cần 2–20 ký tự, chỉ gồm chữ A–Z, số, _ hoặc -.");
    }
    public static boolean planPrice(EditText field) {
        String s = value(field);
        if (!s.matches("[0-9]{1,10}")) return error(field, "Giá phải là số nguyên, không có dấu chấm hoặc phẩy.");
        try { long amount = Long.parseLong(s); return (amount >= 1 && amount <= 1000000000L)
                || error(field, "Giá phải từ 1 đến 1.000.000.000đ."); }
        catch (NumberFormatException e) { return error(field, "Giá không hợp lệ."); }
    }
    public static boolean dataGb(EditText field) {
        String s = value(field).replace(',', '.');
        if (!s.matches("[0-9]+(\\.[0-9]{1,2})?"))
            return error(field, "Dung lượng chỉ được có tối đa 2 chữ số thập phân.");
        try { BigDecimal gb = new BigDecimal(s); return (gb.signum() > 0
                && gb.compareTo(new BigDecimal("100000")) <= 0)
                || error(field, "Dung lượng phải lớn hơn 0 và tối đa 100.000 GB."); }
        catch (NumberFormatException e) { return error(field, "Dung lượng không hợp lệ."); }
    }
    public static double dataGbValue(EditText field) {
        return Double.parseDouble(value(field).replace(',', '.'));
    }
    public static boolean days(EditText field) {
        String s = value(field);
        if (!s.matches("[0-9]{1,4}")) return error(field, "Chu kỳ phải là số ngày nguyên.");
        int count = Integer.parseInt(s);
        return (count >= 1 && count <= 3650) || error(field, "Chu kỳ phải từ 1 đến 3650 ngày.");
    }
    public static boolean supportTitle(EditText field) {
        int n = value(field).length();
        return (n >= 3 && n <= 160) || error(field, "Tiêu đề cần 3–160 ký tự.");
    }
    public static boolean supportMessage(EditText field) {
        int n = value(field).length();
        return (n >= 5 && n <= 2000) || error(field, "Nội dung cần 5–2000 ký tự.");
    }
}
