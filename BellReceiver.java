package np.edu.mangalsingh.routine;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

public class BellReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!NativeBellPlugin.ACTION_BELL.equals(intent.getAction())) return;

        String title = intent.getStringExtra("title");
        if (title == null) title = "विद्यालय घण्टी";
        NativeBellPlugin.ensureChannel(context);

        // Notification optional; actual bell is played directly so notification permission
        // वा notification-channel mute भए पनि bell sound चल्न सक्छ।
        showNotification(context, "🔔 " + title, "MS Routine — विद्यालय घण्टी");
        playBellWithWakeLock(context, goAsync());

        NativeBellPlugin.rescheduleOne(context, intent);
    }

    public static void fireNow(Context context, String title, String body) {
        NativeBellPlugin.ensureChannel(context);
        showNotification(context, title, body);
        playBellWithWakeLock(context, null);
    }

    private static void showNotification(Context context, String title, String body) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        int id = (int) (System.currentTimeMillis() & 0x7fffffff);
        NotificationCompat.Builder b = new NotificationCompat.Builder(context, NativeBellPlugin.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(body)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setDefaults(0);
        if (android.os.Build.VERSION.SDK_INT < 33 ||
                androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            nm.notify(id, b.build());
        }
    }

    private static void playBellWithWakeLock(Context context, final PendingResult pending) {
        final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        final PowerManager.WakeLock lock = pm == null ? null : pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MSRoutine:Bell");
        if (lock != null) lock.acquire(15000);

        final int resId = context.getResources().getIdentifier("bell", "raw", context.getPackageName());
        if (resId == 0) {
            if (lock != null && lock.isHeld()) lock.release();
            if (pending != null) pending.finish();
            return;
        }

        final MediaPlayer player = new MediaPlayer();
        try {
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            Uri uri = Uri.parse("android.resource://" + context.getPackageName() + "/" + resId);
            player.setDataSource(context, uri);
            player.setOnCompletionListener(mp -> {
                try { mp.release(); } catch (Exception ignored) {}
                if (lock != null && lock.isHeld()) lock.release();
                if (pending != null) pending.finish();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                try { mp.release(); } catch (Exception ignored) {}
                if (lock != null && lock.isHeld()) lock.release();
                if (pending != null) pending.finish();
                return true;
            });
            player.prepare();
            player.start();
        } catch (Exception e) {
            try { player.release(); } catch (Exception ignored) {}
            if (lock != null && lock.isHeld()) lock.release();
            if (pending != null) pending.finish();
        }
    }
}
