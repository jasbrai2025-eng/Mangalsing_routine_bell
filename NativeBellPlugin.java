package np.edu.mangalsingh.routine;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

@CapacitorPlugin(name = "NativeBell")
public class NativeBellPlugin extends Plugin {
    public static final String CHANNEL_ID = "school-bell-channel-v3";
    public static final String PREFS = "ms_routine_native_bell";
    public static final String KEY_ENABLED = "enabled";
    public static final String KEY_SCHEDULE = "schedule";
    public static final String KEY_DISMISSAL = "dismissal";
    public static final int REQ_BASE = 5000;
    public static final int REQ_DISMISSAL = 5999;
    public static final String ACTION_BELL = "np.edu.mangalsingh.routine.BELL";
    private static final int MAX_PERIODS = 50;
    private static final int MAX_STRIKES = 10;

    @PluginMethod
    public void enable(PluginCall call) {
        Context ctx = getContext();
        ensureChannel(ctx);

        JSONArray periods = call.getArray("periods", new JSArray()).toJSONArray();
        int dismissalHour = call.getInt("dismissalHour", 16);
        int dismissalMinute = call.getInt("dismissalMinute", 0);

        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_ENABLED, true)
                .putString(KEY_SCHEDULE, periods.toString())
                .putString(KEY_DISMISSAL, dismissalHour + ":" + dismissalMinute)
                .apply();

        requestNotificationPermissionIfNeeded();

        if (!canScheduleExactAlarms(ctx)) {
            requestExactAlarmAccessIfNeeded();
        } else {
            scheduleFromJson(ctx, periods, dismissalHour, dismissalMinute);
            requestBatteryOptimizationExemptionIfPossible(ctx);
        }

        JSObject ret = new JSObject();
        ret.put("enabled", true);
        ret.put("exactAlarmGranted", canScheduleExactAlarms(ctx));
        ret.put("notificationGranted", Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
        call.resolve(ret);
    }

    @PluginMethod
    public void disable(PluginCall call) {
        Context ctx = getContext();
        cancelAll(ctx);
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, false).apply();
        call.resolve();
    }

    @PluginMethod
    public void test(PluginCall call) {
        Context ctx = getContext();
        ensureChannel(ctx);
        BellReceiver.fireNow(ctx, "🔔 घण्टी प्रणाली परीक्षण", "MS Routine — पृष्ठभूमि घण्टी परीक्षण");
        call.resolve();
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 7301);
        }
    }

    public static boolean canScheduleExactAlarms(Context ctx) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        return am != null && am.canScheduleExactAlarms();
    }

    private void requestExactAlarmAccessIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31 && !canScheduleExactAlarms(getContext())) {
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.parse("package:" + getContext().getPackageName()));
                getActivity().startActivity(intent);
            } catch (Exception ignored) {}
        }
    }

    private void requestBatteryOptimizationExemptionIfPossible(Context ctx) {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                android.os.PowerManager pm = (android.os.PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                if (pm != null && !pm.isIgnoringBatteryOptimizations(ctx.getPackageName())) {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + ctx.getPackageName()));
                    getActivity().startActivity(intent);
                }
            } catch (Exception ignored) {}
        }
    }

    public static void ensureChannel(Context ctx) {
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null || Build.VERSION.SDK_INT < 26) return;

        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            int resId = ctx.getResources().getIdentifier("bell", "raw", ctx.getPackageName());
            Uri sound = resId == 0 ? null : Uri.parse("android.resource://" + ctx.getPackageName() + "/" + resId);
            android.media.AudioAttributes attrs = new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "स्कुल घण्टी (School Bell)", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("MS Routine को ठ्याक्कै समयमा बज्ने विद्यालय घण्टी");
            if (sound != null) ch.setSound(sound, attrs);
            ch.enableVibration(true);
            ch.setVibrationPattern(new long[]{0, 350, 150, 350, 150, 500});
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(ch);
        }
    }

    private static int strikeCount(String bellCount) {
        if ("continuous".equalsIgnoreCase(bellCount)) return 5;
        try { return Math.max(1, Math.min(MAX_STRIKES, Integer.parseInt(bellCount))); }
        catch (Exception e) { return 1; }
    }

    private void scheduleFromJson(Context ctx, JSONArray periods, int dismissalHour, int dismissalMinute) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null || !canScheduleExactAlarms(ctx)) return;
        cancelAll(ctx);

        for (int i = 0; i < periods.length() && i < MAX_PERIODS; i++) {
            try {
                JSONObject p = periods.getJSONObject(i);
                int start = p.getInt("start");
                String title = p.optString("name", "कक्षा");
                String count = p.optString("bellCount", "1");
                scheduleDailyStrikes(ctx, am, REQ_BASE + i * 20, start / 60, start % 60, title, count);
            } catch (Exception ignored) {}
        }
        scheduleDailyStrikes(ctx, am, REQ_DISMISSAL, dismissalHour, dismissalMinute, "विद्यालय समय समाप्त भयो!", "continuous");
    }

    public static void rescheduleFromPrefs(Context ctx) {
        android.content.SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!sp.getBoolean(KEY_ENABLED, false) || !canScheduleExactAlarms(ctx)) return;
        ensureChannel(ctx);
        try {
            JSONArray periods = new JSONArray(sp.getString(KEY_SCHEDULE, "[]"));
            String[] dm = sp.getString(KEY_DISMISSAL, "16:0").split(":");
            int dh = Integer.parseInt(dm[0]), dmin = Integer.parseInt(dm[1]);
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            cancelAllStatic(ctx, am);
            for (int i = 0; i < periods.length() && i < MAX_PERIODS; i++) {
                JSONObject p = periods.getJSONObject(i);
                int start = p.getInt("start");
                scheduleDailyStrikes(ctx, am, REQ_BASE + i * 20, start / 60, start % 60,
                        p.optString("name", "कक्षा"), p.optString("bellCount", "1"));
            }
            scheduleDailyStrikes(ctx, am, REQ_DISMISSAL, dh, dmin, "विद्यालय समय समाप्त भयो!", "continuous");
        } catch (Exception ignored) {}
    }

    private static void scheduleDailyStrikes(Context ctx, AlarmManager am, int baseRequestCode, int hour, int minute, String title, String bellCount) {
        int count = strikeCount(bellCount);
        for (int strike = 0; strike < count; strike++) {
            Calendar next = Calendar.getInstance();
            next.set(Calendar.HOUR_OF_DAY, hour);
            next.set(Calendar.MINUTE, minute);
            next.set(Calendar.SECOND, 0);
            next.set(Calendar.MILLISECOND, 0);
            next.add(Calendar.SECOND, strike * 2);
            if (!next.after(Calendar.getInstance())) next.add(Calendar.DAY_OF_YEAR, 1);

            int requestCode = baseRequestCode + strike;
            Intent intent = new Intent(ctx, BellReceiver.class);
            intent.setAction(ACTION_BELL);
            intent.putExtra("title", title);
            intent.putExtra("requestCode", requestCode);
            intent.putExtra("baseRequestCode", baseRequestCode);
            intent.putExtra("hour", hour);
            intent.putExtra("minute", minute);
            intent.putExtra("strike", strike);
            intent.putExtra("strikeCount", count);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, next.getTimeInMillis(), pi);
            }
        }
    }

    public static void rescheduleOne(Context ctx, Intent received) {
        if (!canScheduleExactAlarms(ctx)) return;
        int base = received.getIntExtra("baseRequestCode", -1);
        if (base < 0) return;
        int hour = received.getIntExtra("hour", 16);
        int minute = received.getIntExtra("minute", 0);
        int count = received.getIntExtra("strikeCount", 1);
        String title = received.getStringExtra("title");
        if (title == null) title = "विद्यालय घण्टी";
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) scheduleDailyStrikes(ctx, am, base, hour, minute, title, String.valueOf(count));
    }

    private void cancelAll(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) cancelAllStatic(ctx, am);
    }

    private static void cancelAllStatic(Context ctx, AlarmManager am) {
        for (int i = 0; i < MAX_PERIODS; i++) {
            for (int strike = 0; strike < MAX_STRIKES; strike++) {
                cancelRequest(ctx, am, REQ_BASE + i * 20 + strike);
            }
        }
        for (int strike = 0; strike < MAX_STRIKES; strike++) cancelRequest(ctx, am, REQ_DISMISSAL + strike);
    }

    private static void cancelRequest(Context ctx, AlarmManager am, int requestCode) {
        Intent intent = new Intent(ctx, BellReceiver.class).setAction(ACTION_BELL);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
        pi.cancel();
    }
}
