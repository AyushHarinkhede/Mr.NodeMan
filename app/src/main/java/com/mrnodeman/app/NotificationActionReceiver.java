package com.mrnodeman.app;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class NotificationActionReceiver extends BroadcastReceiver {

    public static final String ACTION_MARK_ATTENDANCE = "com.mrnodeman.app.ACTION_MARK_ATTENDANCE";
    public static final String ACTION_ATTENDANCE_BROADCAST = "com.mrnodeman.app.ATTENDANCE_UPDATED";

    public static final String ACTION_LUNCH_RESPONSE = "com.mrnodeman.app.ACTION_LUNCH_RESPONSE";
    public static final String ACTION_LUNCH_BROADCAST = "com.mrnodeman.app.LUNCH_UPDATED";
    public static final String EXTRA_LUNCH_STATUS = "extra_lunch_status";

    public static final String ACTION_WELLNESS_ACK = "com.mrnodeman.app.ACTION_WELLNESS_ACK";

    public static final String EXTRA_STATUS = "extra_status";
    public static final String EXTRA_DATE = "extra_date";
    public static final String EXTRA_COMPANY = "extra_company";
    public static final String EXTRA_NOTIF_ID = "extra_notif_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        final String action = intent.getAction();
        if (action == null) return;

        // ══════════════════════════════════════════════════════
        // 1. ATTENDANCE 1-TAP CHECK-IN ACTION
        // ══════════════════════════════════════════════════════
        if (ACTION_MARK_ATTENDANCE.equals(action)) {
            final String status = intent.getStringExtra(EXTRA_STATUS);
            final String dateISO = intent.getStringExtra(EXTRA_DATE);
            final String company = intent.getStringExtra(EXTRA_COMPANY);
            final int notifId = intent.getIntExtra(EXTRA_NOTIF_ID, NotificationAlarmReceiver.NOTIF_ID_MORNING);

            if (status == null || dateISO == null) return;

            final String targetStatus = status.toUpperCase(Locale.US);
            final String targetDate = dateISO.trim();
            final String compName = (company != null && !company.trim().isEmpty()) ? company : "Workplace";

            // 1. Update native SharedPreferences and disk backup
            try {
                SharedPreferences prefs = context.getSharedPreferences("mrnodeman_native_store", Context.MODE_PRIVATE);
                String currentAttStr = prefs.getString("_mnm_attendance", "{}");
                JSONObject attObj;
                try {
                    attObj = new JSONObject(currentAttStr);
                } catch (Exception e) {
                    attObj = new JSONObject();
                }

                JSONObject rec = attObj.optJSONObject(targetDate);
                if (rec == null) {
                    rec = new JSONObject();
                }
                rec.put("date", targetDate);
                rec.put("status", targetStatus);
                rec.put("updatedAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
                rec.put("markedVia", "notification_action");

                attObj.put(targetDate, rec);
                String newAttJson = attObj.toString();

                // Save to SharedPreferences
                prefs.edit().putString("_mnm_attendance", newAttJson).commit();

                // Mirror to disk file
                try {
                    File storeDir = new File(context.getFilesDir(), "mrnodeman_data");
                    if (!storeDir.exists()) storeDir.mkdirs();
                    File file = new File(storeDir, "store_" + Math.abs("_mnm_attendance".hashCode()) + ".dat");
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        fos.write(newAttJson.getBytes(StandardCharsets.UTF_8));
                    }
                } catch (Exception ignored) {}

            } catch (Exception e) {
                e.printStackTrace();
            }

            // 2. Determine user-friendly status title & color
            String statusLabel = "Present";
            int statusColor = Color.parseColor("#10B981");
            if ("A".equals(targetStatus)) {
                statusLabel = "Absent";
                statusColor = Color.parseColor("#EF4444");
            } else if ("HD".equals(targetStatus)) {
                statusLabel = "Half Day";
                statusColor = Color.parseColor("#F59E0B");
            }

            // 3. Update the notification to show confirmation and cancel any other attendance alarms
            try {
                Intent mainIntent = new Intent(context, MainActivity.class);
                mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                int pFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
                PendingIntent contentIntent = PendingIntent.getActivity(context, notifId, mainIntent, pFlags);

                NotificationCompat.Builder confirmBuilder = new NotificationCompat.Builder(context, NotificationAlarmReceiver.CHANNEL_ATTENDANCE)
                    .setSmallIcon(R.drawable.ic_stat_attendance)
                    .setContentTitle("Attendance Marked: " + statusLabel)
                    .setContentText("Recorded for " + targetDate + " at " + compName + ".")
                    .setStyle(new NotificationCompat.BigTextStyle().bigText("Attendance for " + targetDate + " recorded as " + statusLabel + " at " + compName + ". Tap to view roster & earnings."))
                    .setColor(statusColor)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true);

                NotificationManagerCompat manager = NotificationManagerCompat.from(context);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                        manager.notify(notifId, confirmBuilder.build());
                    }
                } else {
                    manager.notify(notifId, confirmBuilder.build());
                }

                // Auto-dismiss confirmation after 8 seconds
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        manager.cancel(notifId);
                        manager.cancel(NotificationAlarmReceiver.NOTIF_ID_ATTENDANCE_FOLLOWUP);
                        manager.cancel(NotificationAlarmReceiver.NOTIF_ID_EVENING);
                    } catch (Exception ignored) {}
                }, 8000);

            } catch (Exception e) {
                e.printStackTrace();
            }

            // 4. Show confirmation Toast
            final String toastText = "Attendance Marked: " + statusLabel;
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {}
            });

            // 5. Send local broadcast to update running MainActivity in real time
            try {
                Intent bIntent = new Intent(ACTION_ATTENDANCE_BROADCAST);
                bIntent.putExtra(EXTRA_DATE, targetDate);
                bIntent.putExtra(EXTRA_STATUS, targetStatus);
                bIntent.setPackage(context.getPackageName());
                context.sendBroadcast(bIntent);
            } catch (Exception ignored) {}
        }

        // ══════════════════════════════════════════════════════
        // 2. INTERACTIVE LUNCH / BREAK RESPONSE & LEARNING ENGINE
        // ══════════════════════════════════════════════════════
        else if (ACTION_LUNCH_RESPONSE.equals(action)) {
            final String status = intent.getStringExtra(EXTRA_LUNCH_STATUS);
            final int notifId = intent.getIntExtra(EXTRA_NOTIF_ID, NotificationAlarmReceiver.NOTIF_ID_LUNCH);
            final boolean isYes = "YES".equalsIgnoreCase(status);

            SharedPreferences prefs = context.getSharedPreferences("mrnodeman_native_store", Context.MODE_PRIVATE);
            Calendar now = Calendar.getInstance();
            int curHour = now.get(Calendar.HOUR_OF_DAY);
            int curMin = now.get(Calendar.MINUTE);

            if (isYes) {
                // USER CONFIRMED LUNCH: LEARN TIME PATTERN
                try {
                    String historyStr = prefs.getString("_mnm_lunch_history", "[]");
                    org.json.JSONArray history;
                    try {
                        history = new org.json.JSONArray(historyStr);
                    } catch (Exception e) {
                        history = new org.json.JSONArray();
                    }

                    JSONObject entry = new JSONObject();
                    entry.put("date", new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.getTime()));
                    entry.put("hour", curHour);
                    entry.put("minute", curMin);
                    history.put(entry);

                    // Keep only last 10 records for recency
                    while (history.length() > 10) {
                        history.remove(0);
                    }

                    // Compute moving average of preferred lunch time
                    int totalMinutes = 0;
                    for (int i = 0; i < history.length(); i++) {
                        JSONObject item = history.getJSONObject(i);
                        totalMinutes += (item.getInt("hour") * 60 + item.getInt("minute"));
                    }
                    int avgMinOfDay = totalMinutes / history.length();
                    int learnedHour = avgMinOfDay / 60;
                    int learnedMin = avgMinOfDay % 60;

                    // Bound between 11:30 AM and 03:30 PM (reasonable workday lunch window)
                    if (learnedHour < 11) { learnedHour = 11; learnedMin = 30; }
                    if (learnedHour > 15) { learnedHour = 15; learnedMin = 30; }

                    prefs.edit()
                        .putString("_mnm_lunch_history", history.toString())
                        .putInt("_mnm_learned_lunch_hour", learnedHour)
                        .putInt("_mnm_learned_lunch_minute", learnedMin)
                        .commit();

                    // Re-schedule tomorrow's alarm to the newly learned time!
                    NotificationScheduler.scheduleDailyAlarm(context, NotificationScheduler.REQ_LUNCH_REMINDER, learnedHour, learnedMin, NotificationScheduler.TYPE_LUNCH_REMINDER);

                    // Broadcast learned lunch time to MainActivity & Web UI
                    Intent bIntent = new Intent(ACTION_LUNCH_BROADCAST);
                    bIntent.putExtra("learned_hour", learnedHour);
                    bIntent.putExtra("learned_min", learnedMin);
                    bIntent.setPackage(context.getPackageName());
                    context.sendBroadcast(bIntent);

                } catch (Exception e) {
                    e.printStackTrace();
                }

                // Update notification in shade to confirmation
                try {
                    NotificationCompat.Builder confirmBuilder = new NotificationCompat.Builder(context, NotificationAlarmReceiver.CHANNEL_WELLNESS)
                        .setSmallIcon(R.drawable.ic_stat_attendance)
                        .setContentTitle("Lunch Recorded! 🍱")
                        .setContentText("Bahut badhiya! Energy full, keep rocking your shift.")
                        .setStyle(new NotificationCompat.BigTextStyle().bigText("Bahut badhiya! Lunch recorded. Energy full, stay productive and hydrated for the rest of your shift. ⚡"))
                        .setColor(Color.parseColor("#10B981"))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true);

                    NotificationManagerCompat manager = NotificationManagerCompat.from(context);
                    manager.notify(notifId, confirmBuilder.build());

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try { manager.cancel(notifId); } catch (Exception ignored) {}
                    }, 8000);
                } catch (Exception ignored) {}

                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(context, "🍱 Lunch recorded! App learned your lunch time.", Toast.LENGTH_SHORT).show();
                });

            } else {
                // USER REPLIED "ABHI NAHI" (NO): GENTLE FOLLOW-UP IN 35 MINS
                try {
                    NotificationCompat.Builder laterBuilder = new NotificationCompat.Builder(context, NotificationAlarmReceiver.CHANNEL_WELLNESS)
                        .setSmallIcon(R.drawable.ic_stat_notification)
                        .setContentTitle("Thik hai! 🥪 Meal Skip Mat Karna")
                        .setContentText("Kaam ke chakkar me meal skip mat karna, thodi der me zaroor kha lena.")
                        .setStyle(new NotificationCompat.BigTextStyle().bigText("Koi baat nahi! Kaam ke chakkar me meal skip mat karna, health sabse pehle hai. 35 mins me dobara remind karunga."))
                        .setColor(Color.parseColor("#F59E0B"))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true);

                    NotificationManagerCompat manager = NotificationManagerCompat.from(context);
                    manager.notify(notifId, laterBuilder.build());

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try { manager.cancel(notifId); } catch (Exception ignored) {}
                    }, 8000);

                    // Schedule single follow-up reminder in 35 minutes
                    NotificationScheduler.scheduleOneShotAlarm(context, NotificationScheduler.REQ_LUNCH_REMINDER, 35 * 60 * 1000L, NotificationScheduler.TYPE_LUNCH_REMINDER);

                } catch (Exception ignored) {}

                new Handler(Looper.getMainLooper()).post(() -> {
                    Toast.makeText(context, "🥗 Take care! Will remind you in 35 mins.", Toast.LENGTH_SHORT).show();
                });
            }
        }

        // ══════════════════════════════════════════════════════
        // 3. BIO BREAK & HEALTH WELLNESS ACKNOWLEDGEMENT
        // ══════════════════════════════════════════════════════
        else if (ACTION_WELLNESS_ACK.equals(action)) {
            final int notifId = intent.getIntExtra(EXTRA_NOTIF_ID, NotificationAlarmReceiver.NOTIF_ID_WELLNESS);
            try {
                NotificationManagerCompat manager = NotificationManagerCompat.from(context);
                NotificationCompat.Builder ackBuilder = new NotificationCompat.Builder(context, NotificationAlarmReceiver.CHANNEL_WELLNESS)
                    .setSmallIcon(R.drawable.ic_stat_attendance)
                    .setContentTitle("Wellness Done! 🌿")
                    .setContentText("Great job staying active and hydrated!")
                    .setColor(Color.parseColor("#06B6D4"))
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setAutoCancel(true);
                manager.notify(notifId, ackBuilder.build());

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try { manager.cancel(notifId); } catch (Exception ignored) {}
                }, 5000);
            } catch (Exception ignored) {}

            new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(context, "💧 Hydrated & refreshed! Stay active.", Toast.LENGTH_SHORT).show();
            });
        }
    }
}
