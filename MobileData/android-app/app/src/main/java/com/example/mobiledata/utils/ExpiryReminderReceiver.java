package com.example.mobiledata.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Hiển thị thông báo từ lịch nhắc do người dùng đã đồng bộ khi mở app. */
public class ExpiryReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        ExpiryReminderManager.receive(context, intent);
    }
}
