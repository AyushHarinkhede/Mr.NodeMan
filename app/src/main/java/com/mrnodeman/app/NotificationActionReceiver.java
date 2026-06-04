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
import java.util.Date;
import java.util.Locale;

public class NotificationActionReceiver extends BroadcastReceiver {

    public static final String ACTION_MARK_ATTENDANCE = "com.mrnodeman.app.ACTION_MARK_ATTENDANCE";
    public static final String ACTION_ATTENDANCE_BROADCAST = "com.mrnodeman.app.ATTENDANCE_UPDATED";

    public static final String EXTRA_STATUS = "extra_status";
    public static final String EXTRA_DATE = "extra_date";
    public static final String EXTRA_COMPANY = "extra_company";
    public static final String EXTRA_NOTIF_ID = "extra_notif_id";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        if (ACTION_MARK_ATTENDANCE.equals(intent.getAction())) {
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

            // 3. Update the notification to show confirmation and remove action buttons
            try {
                Intent mainIntent = new Intent(context, MainActivity.class);
                mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                int pFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
                PendingIntent contentIntent = PendingIntent.getActivity(context, notifId, mainIntent, pFlags);

                NotificationCompat.Builder confirmBuilder = new NotificationCompat.Builder(context, NotificationAlarmReceiver.CHANNEL_ATTENDANCE)
                    .setSmallIcon(R.drawable.ic_stat_attendance)
                    .setContentTitle("Attendance Marked: " + statusLabel)
                    .setContentText("Successfully recorded for " + targetDate + " at " + compName + ".")
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

                // Auto-dismiss confirmation after 10 seconds to keep notification shade clean
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        manager.cancel(notifId);
                    } catch (Exception ignored) {}
                }, 10000);

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
    }
}
