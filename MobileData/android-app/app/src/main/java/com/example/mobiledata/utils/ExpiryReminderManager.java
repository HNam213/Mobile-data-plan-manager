package com.example.mobiledata.utils;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.mobiledata.MainActivity;
import com.example.mobiledata.R;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Nhắc hạn gói nhiều mốc; không gia hạn, trừ tiền hay sửa ngày hết hạn.
 * AlarmManager sử dụng lịch không chính xác để tránh yêu cầu quyền báo thức đặc biệt.
 * Cảnh báo dung lượng thật chỉ hoạt động nếu API trả remainingDataGb (xem syncUsage).
 */
public final class ExpiryReminderManager {
    private static final String PREF = "mobiledata_expiry_reminders_v1";
    private static final String CHANNEL = "mobiledata_plan_expiry";
    private static final String DEMO_CHANNEL = "mobiledata_demo_expiry";
    private static final String CURRENT = "current_key";
    private static final String CURRENT_CODE = "current_code";
    private static final String CURRENT_EXPIRY = "current_expiry";
    private static final String SENT = "sent_stages";
    private static final String SCHEDULED = "scheduled_stages";
    private static final String ASKED = "asked_notification_permission";
    private static final String ACTION = "com.example.mobiledata.ACTION_PLAN_EXPIRY";
    private static final String EXTRA_KEY = "reminder_key";
    private static final String EXTRA_PHONE = "subscriber_phone";
    private static final String EXTRA_CODE = "plan_code";
    private static final String EXTRA_EXPIRY = "expiry_ms";
    private static final String EXTRA_STAGE = "reminder_stage";

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60L * MINUTE;
    private static final long DAY = 24L * HOUR;
    private static final int LEGACY_REQUEST = 41723;
    private static final int BASE_REQUEST = 41800;
    private static final int BASE_NOTIFICATION = 41900;
    private static final int BASE_DEMO_NOTIFICATION = 42900;
    private static final int PERMISSION_REQUEST = 41723;

    private static final int THREE_DAYS = 0;
    private static final int ONE_DAY = 1;
    private static final int ONE_HOUR = 2;
    private static final int FIVE_MIN = 3;
    private static final int EXPIRED = 4;
    private static final int LOW_20 = 5;
    private static final int LOW_5 = 6;
    private static final int COUNT = 5;

    private ExpiryReminderManager() { }

    /** API hiện trả thời gian SQL Server dạng yyyy-MM-ddTHH:mm:ss theo giờ địa phương. */
    public static long parseServerDate(String iso) {
        try {
            if (iso == null || iso.length() < 19) return -1L;
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
            format.setLenient(false);
            return format.parse(iso.substring(0, 19)).getTime();
        } catch (Exception ex) { return -1L; }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static boolean permissionGranted(Context context) {
        return Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,
                "android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
    }

    public static void offerPermission(Fragment fragment) {
        if (!fragment.isAdded() || Build.VERSION.SDK_INT < 33) return;
        Context context = fragment.requireContext();
        if (permissionGranted(context) || prefs(context).getBoolean(ASKED, false)) return;
        prefs(context).edit().putBoolean(ASKED, true).apply();
        new AlertDialog.Builder(context, R.style.MobileDataLightDialog)
                .setTitle("Nhận nhắc nhở gói cước")
                .setMessage("MobileData có thể nhắc ở nhiều mốc trước khi gói hết hạn. Bạn có muốn bật thông báo không?")
                .setPositiveButton("Bật thông báo", (d, w) -> fragment.requestPermissions(
                        new String[]{"android.permission.POST_NOTIFICATIONS"}, PERMISSION_REQUEST))
                .setNegativeButton("Để sau", null)
                .show();
    }

    private static long offsetFor(int stage) {
        switch (stage) {
            case THREE_DAYS: return 3L * DAY;
            case ONE_DAY: return DAY;
            case ONE_HOUR: return HOUR;
            case FIVE_MIN: return 5L * MINUTE;
            default: return 0L;
        }
    }

    private static List<Integer> stagesFor(int durationDays) {
        if (durationDays <= 1) return Arrays.asList(ONE_HOUR, FIVE_MIN, EXPIRED);
        if (durationDays <= 7) return Arrays.asList(ONE_DAY, ONE_HOUR, FIVE_MIN, EXPIRED);
        return Arrays.asList(THREE_DAYS, ONE_DAY, ONE_HOUR, FIVE_MIN, EXPIRED);
    }

    private static Set<String> getSet(SharedPreferences p, String field) {
        return new HashSet<>(p.getStringSet(field, new HashSet<>()));
    }

    /** Gói hiện tại được đọc từ /api/me/plans, chỉ dùng gói đầu tiên như màn hình Gói đang dùng. */
    public static void sync(Context context, JSONObject plan) {
        if (plan == null) { notifyExpiredIfNeeded(context); clear(context); return; }
        String phone = new SessionManager(context).getPhone();
        String code = plan.optString("code", "").trim();
        long registrationId = plan.optLong("registrationId", 0L);
        long expiry = parseServerDate(plan.optString("expiresAt", ""));
        long now = System.currentTimeMillis();
        if (phone.isEmpty() || code.isEmpty() || registrationId <= 0L || expiry <= now) {
            if (expiry > 0 && expiry <= now && registrationId > 0 && !phone.isEmpty()) {
                // Nếu lần đồng bộ đầu tiên đã quá hạn: không suy đoán đây là gói còn hiệu lực.
                // Chỉ nhắc khi cùng gói đã được theo dõi trước đó.
                notifyExpiredIfNeeded(context);
            }
            clear(context);
            return;
        }
        int days = plan.optInt("durationDays", 30);
        String key = phone + "_" + registrationId + "_" + expiry;
        SharedPreferences p = prefs(context);
        if (!key.equals(p.getString(CURRENT, ""))) {
            cancelAll(context);
            p.edit().putString(CURRENT, key)
                    .remove(SENT).remove(SCHEDULED).apply();
        }
        p.edit().putString(CURRENT_CODE, code).putLong(CURRENT_EXPIRY, expiry).apply();
        if (!isEnabled(context, -1)) {
            cancelAll(context);
            p.edit().remove(SCHEDULED).apply();
            return;
        }
        List<Integer> stages = stagesFor(days);
        int dueStage = stageForNow(expiry, days, now);
        if (dueStage >= 0 && dueStage != EXPIRED && isEnabled(context, dueStage)
                && !isSent(context, dueStage)) {
            notifyReal(context, code, key, expiry, dueStage);
        }
        for (int stage : stages) {
            long at = expiry - offsetFor(stage);
            if (at > now && isEnabled(context, stage)) schedule(context, key, phone, code, expiry, stage, at);
        }
        // Không có dữ liệu dùng thực tế thì không được tự suy diễn số GB còn lại.
        syncUsage(context, plan, key, code);
    }

    /** Trả về mốc gần nhất đã đến; ngăn nhắc muộn của mốc cũ khi đã qua mốc tiếp theo. */
    private static int stageForNow(long expiry, int days, long now) {
        int current = -1;
        for (int stage : stagesFor(days)) {
            if (now >= expiry - offsetFor(stage)) current = stage;
        }
        return current;
    }

    private static boolean isSent(Context context, int stage) {
        return getSet(prefs(context), SENT).contains(String.valueOf(stage));
    }

    private static void markSent(Context context, int stage) {
        SharedPreferences p = prefs(context);
        Set<String> sent = getSet(p, SENT);
        sent.add(String.valueOf(stage));
        p.edit().putStringSet(SENT, sent).apply();
    }

    private static void schedule(Context context, String key, String phone,
                                 String code, long expiry, int stage, long when) {
        SharedPreferences p = prefs(context);
        if (getSet(p, SCHEDULED).contains(String.valueOf(stage)) || isSent(context, stage)) return;
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        Intent intent = new Intent(context, ExpiryReminderReceiver.class)
                .setAction(ACTION)
                .putExtra(EXTRA_KEY, key)
                .putExtra(EXTRA_PHONE, phone)
                .putExtra(EXTRA_CODE, code)
                .putExtra(EXTRA_EXPIRY, expiry)
                .putExtra(EXTRA_STAGE, stage);
        PendingIntent operation = PendingIntent.getBroadcast(context, BASE_REQUEST + stage, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        // Inexact: có thể bị Android/POCO trì hoãn khi tiết kiệm pin.
        if (Build.VERSION.SDK_INT >= 23) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, operation);
        } else {
            alarm.set(AlarmManager.RTC_WAKEUP, when, operation);
        }
        Set<String> scheduled = getSet(p, SCHEDULED);
        scheduled.add(String.valueOf(stage));
        p.edit().putStringSet(SCHEDULED, scheduled).apply();
    }

    private static void cancelAll(Context context) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        for (int request = BASE_REQUEST; request < BASE_REQUEST + COUNT; request++) {
            PendingIntent op = PendingIntent.getBroadcast(context, request,
                    new Intent(context, ExpiryReminderReceiver.class).setAction(ACTION),
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (op != null) { manager.cancel(op); op.cancel(); }
        }
        // Huỷ báo thức 24 giờ từ phiên bản trước để không có thông báo thừa.
        PendingIntent old = PendingIntent.getBroadcast(context, LEGACY_REQUEST,
                new Intent(context, ExpiryReminderReceiver.class).setAction(ACTION),
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (old != null) { manager.cancel(old); old.cancel(); }
    }

    public static void clear(Context context) {
        cancelAll(context);
        prefs(context).edit().remove(CURRENT).remove(CURRENT_CODE).remove(CURRENT_EXPIRY)
                .remove(SENT).remove(SCHEDULED).apply();
    }

    /** Lưu cài đặt riêng cho từng số thuê bao, không ảnh hưởng báo Demo. */
    private static String settingKey(Context context, int stage) {
        String phone = new SessionManager(context).getPhone();
        return "setting_" + phone + "_" + stage;
    }

    /** Đọc lựa chọn lưu trong máy, không phụ thuộc công tắc chung. */
    public static boolean isStageSelected(Context context, int stage) {
        return prefs(context).getBoolean(settingKey(context, stage), true);
    }

    /** Nội dung mẫu có thể thay trực tiếp trong màn hình Debug. */
    public static String getDemoMessage(Context context) {
        String fallback = "Đây là thông báo mẫu. Gói {GOI} sẽ hết hạn sau 1 giờ nữa. "
                + "Vui lòng kiểm tra thời hạn và chủ động chọn gói mới nếu cần. "
                + "MobileData không tự động gia hạn.";
        String phone = new SessionManager(context).getPhone();
        return prefs(context).getString("demo_message_" + phone, fallback);
    }

    public static void saveDemoMessage(Context context, String text) {
        String phone = new SessionManager(context).getPhone();
        prefs(context).edit().putString("demo_message_" + phone, text).apply();
    }

    /** stage = -1 là bật/tắt toàn bộ thông báo thật. */
    public static boolean isEnabled(Context context, int stage) {
        SharedPreferences p = prefs(context);
        if (stage != -1 && !p.getBoolean(settingKey(context, -1), true)) return false;
        return p.getBoolean(settingKey(context, stage), true);
    }

    public static void saveSettings(Context context, boolean[] choices) {
        if (choices == null || choices.length != 8) return;
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.putBoolean(settingKey(context, -1), choices[0]);
        for (int i = 0; i < 7; i++) editor.putBoolean(settingKey(context, i), choices[i + 1]);
        // Hủy mọi lịch cũ để lập lại theo các mốc đã chọn sau sync().
        cancelAll(context);
        editor.remove(SCHEDULED).apply();
    }

    private static void notifyExpiredIfNeeded(Context context) {
        SharedPreferences p = prefs(context);
        String key = p.getString(CURRENT, "");
        String code = p.getString(CURRENT_CODE, "");
        long expiry = p.getLong(CURRENT_EXPIRY, 0L);
        String phone = new SessionManager(context).getPhone();
        if (key == null || code == null || key.isEmpty() || code.isEmpty()
                || phone.isEmpty() || !key.startsWith(phone + "_") || expiry <= 0
                || System.currentTimeMillis() < expiry || isSent(context, EXPIRED)) return;
        if (isEnabled(context, EXPIRED)) notifyReal(context, code, key, expiry, EXPIRED);
    }

    /** Lịch sử cục bộ tối đa 60 thông báo cho mỗi số thuê bao. */
    public static org.json.JSONArray getHistory(Context context) {
        String key = "history_" + new SessionManager(context).getPhone();
        try { return new org.json.JSONArray(prefs(context).getString(key, "[]")); }
        catch (Exception ignored) { return new org.json.JSONArray(); }
    }

    public static void clearHistory(Context context) {
        prefs(context).edit().remove("history_" + new SessionManager(context).getPhone()).apply();
    }

    private static void recordHistory(Context context, String title, String body, boolean demo) {
        String phone = new SessionManager(context).getPhone();
        if (phone == null || phone.isEmpty()) return;
        org.json.JSONArray old = getHistory(context);
        org.json.JSONArray next = new org.json.JSONArray();
        try {
            JSONObject event = new JSONObject();
            event.put("time", System.currentTimeMillis());
            event.put("title", title); event.put("body", body); event.put("demo", demo);
            next.put(event);
            for (int i = 0; i < old.length() && i < 59; i++) next.put(old.optJSONObject(i));
            prefs(context).edit().putString("history_" + phone, next.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** Nhận thông báo đã được lên lịch. Kiểm tra thuê bao, khóa đăng ký và mốc hiện tại. */
    static void receive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        String key = intent.getStringExtra(EXTRA_KEY);
        String phone = intent.getStringExtra(EXTRA_PHONE);
        String code = intent.getStringExtra(EXTRA_CODE);
        long expiry = intent.getLongExtra(EXTRA_EXPIRY, 0L);
        int stage = intent.getIntExtra(EXTRA_STAGE, -1);
        if (key == null || phone == null || stage < 0 || stage >= COUNT) return;
        if (!phone.equals(new SessionManager(context).getPhone())) return;
        SharedPreferences p = prefs(context);
        if (!key.equals(p.getString(CURRENT, "")) || isSent(context, stage)
                || !isEnabled(context, stage)) return;
        Set<String> scheduled = getSet(p, SCHEDULED);
        scheduled.remove(String.valueOf(stage));
        p.edit().putStringSet(SCHEDULED, scheduled).apply();
        long now = System.currentTimeMillis();
        if (expiry <= 0) return;
        // Chỉ gửi mốc tương ứng nếu chưa sang mốc cảnh báo tiếp theo.
        if (stage == EXPIRED && now < expiry) return;
        if (stage != EXPIRED) {
            if (now >= expiry || now < expiry - offsetFor(stage)) return;
            if (stage == THREE_DAYS && now >= expiry - DAY) return;
            if (stage == ONE_DAY && now >= expiry - HOUR) return;
            if (stage == ONE_HOUR && now >= expiry - 5L * MINUTE) return;
        }
        notifyReal(context, code, key, expiry, stage);
    }

    private static void ensureChannel(Context context, String id, String name, int importance) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(id, name, importance);
        channel.setDescription("Nhắc tình trạng gói cước MobileData");
        manager.createNotificationChannel(channel);
    }

    private static boolean canNotify(Context context) {
        if (!permissionGranted(context)) return false;
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        return manager != null && (Build.VERSION.SDK_INT < 24 || manager.areNotificationsEnabled());
    }

    private static String titleFor(String code, int stage) {
        switch (stage) {
            case THREE_DAYS: return "Gói " + code + " còn dưới 3 ngày";
            case ONE_DAY: return "Gói " + code + " còn dưới 24 giờ";
            case ONE_HOUR: return "Gói " + code + " còn dưới 1 giờ";
            case FIVE_MIN: return "Gói " + code + " còn khoảng 5 phút";
            case EXPIRED: return "Gói " + code + " đã hết hạn";
            case LOW_20: return "Dung lượng gói " + code + " sắp hết";
            case LOW_5: return "Dung lượng gói " + code + " còn rất ít";
            default: return "MobileData - Thông báo gói cước";
        }
    }

    private static String messageFor(int stage) {
        switch (stage) {
            case THREE_DAYS: return "Gói cước sẽ hết hạn trong khoảng 3 ngày. Bạn có thể chuẩn bị gói mới nếu cần.";
            case ONE_DAY: return "Gói cước còn dưới 24 giờ. Hãy kiểm tra thời hạn sử dụng.";
            case ONE_HOUR: return "Gói cước sẽ hết hạn trong khoảng 1 giờ. Hãy kiểm tra trước khi sử dụng tiếp.";
            case FIVE_MIN: return "Gói cước sắp hết hạn. Bạn có thể chủ động đăng ký gói mới.";
            case EXPIRED: return "Gói đã hết hạn. MobileData không tự gia hạn và không tự trừ tiền.";
            case LOW_20: return "Dung lượng còn dưới 20% định mức. Bạn nên kiểm tra nhu cầu sử dụng.";
            case LOW_5: return "Dung lượng chỉ còn dưới 5% định mức. Hãy sử dụng phù hợp hoặc chọn gói khác.";
            default: return "Mở MobileData để xem chi tiết gói cước.";
        }
    }

    private static Notification.Builder newBuilder(Context context, String channel) {
        if (Build.VERSION.SDK_INT >= 26) return new Notification.Builder(context, channel);
        return new Notification.Builder(context);
    }

    private static boolean post(Context context, int id, String channel,
                                String title, String body, boolean demo) {
        if (!canNotify(context)) return false;
        ensureChannel(context, channel, demo ? "Demo nhắc gói cước" : "Nhắc hạn gói cước",
                demo ? NotificationManager.IMPORTANCE_HIGH : NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return false;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel actual = manager.getNotificationChannel(channel);
            if (actual == null || actual.getImportance() == NotificationManager.IMPORTANCE_NONE) return false;
        }
        PendingIntent open = PendingIntent.getActivity(context, id,
                new Intent(context, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = newBuilder(context, channel)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(open)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setPriority(demo ? Notification.PRIORITY_HIGH : Notification.PRIORITY_DEFAULT);
        try {
            manager.notify(id, builder.build());
            recordHistory(context, title, body, demo);
            return true;
        } catch (SecurityException ex) { return false; }
    }

    private static void notifyReal(Context context, String code, String key, long expiry, int stage) {
        if (!key.equals(prefs(context).getString(CURRENT, ""))) return;
        if (isSent(context, stage) || !isEnabled(context, stage)) return;
        if (post(context, BASE_NOTIFICATION + stage, CHANNEL,
                titleFor(code, stage), messageFor(stage), false)) markSent(context, stage);
    }

    /** Demo có các kịch bản riêng; không tác động vào lịch/thời hạn gói thật. */
    public static boolean showDemoNotification(Context context, String code) {
        return showDemoNotification(context, code, "ONE_HOUR");
    }

    public static boolean showDemoNotification(Context context, String code, String scenario) {
        String displayed = (code == null || code.trim().isEmpty()) ? "GÓI MẪU" : code.trim();
        int stage;
        if ("THREE_DAYS".equals(scenario)) stage = THREE_DAYS;
        else if ("ONE_DAY".equals(scenario)) stage = ONE_DAY;
        else if ("FIVE_MIN".equals(scenario)) stage = FIVE_MIN;
        else if ("EXPIRED".equals(scenario)) stage = EXPIRED;
        else if ("LOW_DATA".equals(scenario)) stage = LOW_20;
        else if ("LOW_5".equals(scenario)) stage = LOW_5;
        else stage = ONE_HOUR;
        String text = "[DEMO] " + messageFor(stage);
        if (stage == ONE_HOUR) text = "[DEMO] " + getDemoMessage(context)
                .replace("{GOI}", displayed);
        if (stage == LOW_20 || stage == LOW_5) text =
                "[DEMO - giả lập] Dung lượng chỉ còn khoảng "
                + (stage == LOW_5 ? "5%" : "20%")
                + ". Đây không phải dữ liệu sử dụng thật từ nhà mạng.";
        return post(context, BASE_DEMO_NOTIFICATION + stage, DEMO_CHANNEL,
                "[DEMO] " + titleFor(displayed, stage), text, true);
    }

    /**
     * Chỉ cảnh báo dung lượng nếu API cung cấp remainingDataGb thực tế cho gói.
     * Không có trường này (tình trạng API hiện tại) thì bỏ qua, tuyệt đối không đoán.
     */
    private static void syncUsage(Context context, JSONObject plan, String key, String code) {
        if (!isEnabled(context, -1) || !plan.has("remainingDataGb")
                || plan.isNull("remainingDataGb")) return;
        double allowed = plan.optDouble("dataGb", -1);
        double remain = plan.optDouble("remainingDataGb", -1);
        if (allowed <= 0 || remain < 0 || remain > allowed) return;
        if (!canNotify(context)) return;
        boolean daily = "DAILY".equalsIgnoreCase(plan.optString("dataType", ""));
        // Nếu hạn mức được cấp theo ngày, ngưỡng và dấu đã thông báo cũng tính theo từng ngày.
        String datePart = new SimpleDateFormat("yyyyMMdd", Locale.ROOT)
                .format(new java.util.Date());
        String usageKey = "usage_" + key + (daily ? "_" + datePart : "");
        SharedPreferences p = prefs(context);
        if (remain <= allowed * 0.05 && isEnabled(context, LOW_5)) {
            String field = usageKey + "_5";
            if (!p.getBoolean(field, false) && post(context, BASE_NOTIFICATION + LOW_5,
                    CHANNEL, titleFor(code, LOW_5), messageFor(LOW_5), false)) {
                p.edit().putBoolean(field, true).apply();
            }
        } else if (remain <= allowed * 0.20 && isEnabled(context, LOW_20)) {
            String field = usageKey + "_20";
            if (!p.getBoolean(field, false) && post(context, BASE_NOTIFICATION + LOW_20,
                    CHANNEL, titleFor(code, LOW_20), messageFor(LOW_20), false)) {
                p.edit().putBoolean(field, true).apply();
            }
        }
    }
}
