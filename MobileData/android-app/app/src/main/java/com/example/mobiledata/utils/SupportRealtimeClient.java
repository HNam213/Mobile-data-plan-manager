package com.example.mobiledata.utils;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CopyOnWriteArrayList;

/** Ket noi SSE nghe su kien ho tro khi app dang o man hinh, do tre rat thap. */
public final class SupportRealtimeClient {
    private static final String EVENTS_URL = ApiConfig.BASE_URL + "/api/support/events";
    private static final String CHANNEL = "mobiledata_support_instant_v1";
    private static final AtomicInteger ACTIVE = new AtomicInteger(0);
    private static final CopyOnWriteArrayList<TicketObserver> OBSERVERS = new CopyOnWriteArrayList<>();
    private static final class TicketObserver {
        final long ticketId;
        final Runnable callback;
        TicketObserver(long ticketId, Runnable callback) {
            this.ticketId = ticketId;
            this.callback = callback;
        }
    }

    /** Dang ky nhan thay doi mot cuoc hoi thoai; goi Runnable tra ve khi dong dialog. */
    public static Runnable watchTicket(long ticketId, Runnable callback) {
        TicketObserver observer = new TicketObserver(ticketId, callback);
        OBSERVERS.add(observer);
        return () -> OBSERVERS.remove(observer);
    }
    private final Activity activity;
    private final Runnable onEvent;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private volatile HttpURLConnection connection;
    private Thread worker;

    public SupportRealtimeClient(Activity activity) { this(activity, null); }
    public SupportRealtimeClient(Activity activity, Runnable onEvent) {
        this.activity = activity;
        this.onEvent = onEvent;
    }
    public static boolean isConnected() { return ACTIVE.get() > 0; }

    public synchronized void start() {
        if (running) return;
        String token = new SessionManager(activity).getAccessToken();
        if (token == null || token.isEmpty()) return;
        requestPermission();
        running = true;
        worker = new Thread(() -> runLoop(token), "MobileData-Support-SSE");
        worker.setDaemon(true);
        worker.start();
    }
    public synchronized void stop() {
        running = false;
        HttpURLConnection old = connection;
        if (old != null) old.disconnect();
        if (worker != null) worker.interrupt();
    }
    private void runLoop(String token) {
        while (running) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(EVENTS_URL).openConnection();
                connection = conn;
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(0); // Stream lien tuc; stop() se disconnect.
                conn.setRequestProperty("Accept", "text/event-stream");
                conn.setRequestProperty("Authorization", "Bearer " + token);
                if (conn.getResponseCode() == 401) break;
                if (conn.getResponseCode() != 200) throw new java.io.IOException("SSE HTTP " + conn.getResponseCode());
                ACTIVE.incrementAndGet();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running && (line = reader.readLine()) != null) {
                        if (!line.startsWith("data: ")) continue;
                        try {
                            JSONObject data = new JSONObject(line.substring(6));
                            main.post(() -> {
                                if (!running || activity.isFinishing()) return;
                                SessionManager now = new SessionManager(activity);
                                if (!token.equals(now.getAccessToken())) return;
                                long changedTicket = data.optLong("ticketId", 0L);
                                if (changedTicket > 0) {
                                    for (TicketObserver listener : OBSERVERS) {
                                        if (listener.ticketId == changedTicket || listener.ticketId == 0L) {
                                            try { listener.callback.run(); }
                                            catch (Exception ignored) { }
                                        }
                                    }
                                }
                                notifyDevice(activity, data);
                                if (onEvent != null) onEvent.run();
                            });
                        } catch (Exception ignored) { }
                    }
                } finally { ACTIVE.decrementAndGet(); }
            } catch (Exception ignored) { /* server chua san sang, thu lai */ }
            finally {
                if (conn != null) conn.disconnect();
                if (connection == conn) connection = null;
            }
            if (running) {
                try { Thread.sleep(1700); } catch (InterruptedException ignored) { break; }
            }
        }
    }
    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 6111);
        }
    }
    private static void notifyDevice(Context context, JSONObject event) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL,
                    "Thông báo hỗ trợ trực tiếp", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Tin nhắn hỗ trợ và phản hồi mới");
            nm.createNotificationChannel(channel);
        }
        boolean admin = "admin".equals(event.optString("kind"));
        long ticket = event.optLong("ticketId");
        String title = admin ? "MobileData • Yêu cầu hỗ trợ mới" : "MobileData • Có phản hồi hỗ trợ";
        String detail = "Yêu cầu #" + ticket + " • " + (admin ?
                "Khách hàng vừa gửi tin nhắn" : "ADMIN đã trả lời");
        Intent open = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        PendingIntent action = open == null ? null : PendingIntent.getActivity(context,
                6110, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle(title).setContentText(detail)
                .setStyle(new Notification.BigTextStyle().bigText(detail))
                .setAutoCancel(true).setWhen(System.currentTimeMillis())
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPriority(Notification.PRIORITY_HIGH);
        if (action != null) builder.setContentIntent(action);
        nm.notify(610000 + (int)(ticket % 50000), builder.build());
    }
}
