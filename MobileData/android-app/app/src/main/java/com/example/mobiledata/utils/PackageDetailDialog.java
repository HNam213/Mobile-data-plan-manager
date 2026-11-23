
package com.example.mobiledata.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.example.mobiledata.R;

import java.text.NumberFormat;
import java.util.Locale;

public class PackageDetailDialog {

    // Hiển thị chi tiết một gói cước bằng giao diện riêng.
    public static void show(
            Context context,
            String packageName,
            String dataText,
            int price,
            int days) {

        // Nạp giao diện dialog_package_detail.xml.
        View view = LayoutInflater.from(context).inflate(
                R.layout.dialog_package_detail,
                null,
                false
        );

        TextView txtName =
                view.findViewById(R.id.txtDetailPlanName);

        TextView txtPrice =
                view.findViewById(R.id.txtDetailPlanPrice);

        TextView txtData =
                view.findViewById(R.id.txtDetailPlanData);

        TextView txtDays =
                view.findViewById(R.id.txtDetailPlanDays);

        // Cập nhật thông tin của gói được nhấn.
        txtName.setText(packageName);
        txtData.setText(dataText);

        NumberFormat formatter =
                NumberFormat.getNumberInstance(
                        Locale.forLanguageTag("vi-VN")
                );

        txtPrice.setText(formatter.format(price) + "đ");
        txtDays.setText(days + " ngày");

        // Dùng theme sáng đã tạo cho toàn ứng dụng.
        new AlertDialog.Builder(
                context,
                R.style.MobileDataLightDialog
        )
                .setView(view)
                .setPositiveButton("Đóng", null)
                .show();
    }
}
