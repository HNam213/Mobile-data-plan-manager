package com.example.mobiledata.utils;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONObject;

/** Thông báo phản hồi hỗ trợ khi người dùng mở ứng dụng. Không dùng FCM/push nền. */
public final class SupportReplyNotifier {
    private static final String CHANNEL_ID = "mobiledata_support_replies";
    private static final long INTERVAL_MS = 55_000L;
    private static final String PREFS = "mobiledata_support_notifications_v1";
    private static boolean busy;
    private static long lastCheckAt;

    private SupportReplyNotifier() { }

    public static void check(Context context) { checkInternal(context, false); }
    public static void checkNow(Context context) { checkInternal(context, true); }

    private static synchronized void checkInternal(Context context, boolean force) {
        if (SupportRealtimeClient.isConnected()) return;
        Context app = context.getApplicationContext();
        SessionManager session = new SessionManager(app);
        String phone = session.getPhone();
        String token = session.getAccessToken();
        if (phone == null || phone.trim().isEmpty() || token == null || token.isEmpty()
                || session.isAdmin()) return;
        long now = SystemClock.elapsedRealtime();
        if (busy || (!force && now - lastCheckAt < INTERVAL_MS && lastCheckAt != 0)) return;
        busy = true;
        lastCheckAt = now;
        String key = "last_" + phone + "_" + session.getCarrier();
        SharedPreferences prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long lastId = prefs.getLong(key, -1L);
        // Lần đầu chỉ lưu mốc hiện tại, tránh thông báo dồn cho phản hồi cũ.
        long after = Math.max(0L, lastId);
        MobileDataBusinessClient.get("/api/me/support/notifications?after=" + after,
                token, new MobileDataBusinessClient.Callback() {
                    @Override public void onSuccess(JSONObject result) {
                        finish();
                        // Không ghi nhận thông báo của một tài khoản đã đăng xuất / chuyển tài khoản.
                        SessionManager current = new SessionManager(app);
                        if (!token.equals(current.getAccessToken()) ||
                                !phone.equals(current.getPhone())) return;
                        long latest = result.optLong("latestId", 0);
                        if (lastId < 0) {
                            prefs.edit().putLong(key, latest).apply();
                            return;
                        }
                        JSONArray messages = MobileDataBusinessClient.items(result);
                        if (messages.length() == 0) return;
                        // Không có quyền thông báo: giữ mốc cũ để có thể nhắc khi được cấp quyền.
                        if (!canNotify(app)) return;
                        long shownTo = lastId;
                        for (int i = 0; i < messages.length(); i++) {
                            JSONObject message = messages.optJSONObject(i);
                            if (message == null) continue;
                            long id = message.optLong("id", 0);
                            if (id <= shownTo) continue;
                                            if (sendNotification(app, message)) shownTo = id;
                            else break;
                        }
                        if (shownTo > lastId) prefs.edit().putLong(key, shownTo).apply();
                    }
                    @Override public void onError(String ignored) { finish(); }
                });
    }

    private static synchronized void finish() { busy = false; }

    private static boolean canNotify(Context context) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        return nm != null && (Build.VERSION.SDK_INT < 24 || nm.areNotificationsEnabled());
    }

    private static boolean sendNotification(Context context, JSONObject item) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return false;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                    "Phản hồi hỗ trợ", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Thông báo khi quản trị viên phản hồi yêu cầu hỗ trợ");
            nm.createNotificationChannel(ch);
        }
        String title = "MobileData • Có phản hồi hỗ trợ";
        String body = "#" + item.optLong("ticketId") + " • " + item.optString("title")
                + "\n" + item.optString("message");
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        PendingIntent pending = launch == null ? null : PendingIntent.getActivity(context, 1001,
                launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID) : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(title)
                .setContentText("ADMIN đã trả lời yêu cầu #" + item.optLong("ticketId"))
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setWhen(System.currentTimeMillis());
        if (pending != null) builder.setContentIntent(pending);
        try {
            nm.notify(520000 + (int) (item.optLong("id") % 99999L), builder.build());
            return true;
        } catch (SecurityException ex) {
            return false;
        }
    }
}
