package com.mrnodeman.app;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
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
    public static final String ACTION_PROMPT_ABSENT = "com.mrnodeman.app.ACTION_PROMPT_ABSENT";
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
        // 1. INTERACTIVE ABSENT SUB-FLOW PROMPT
        // When user taps "Absent", ask reason: SL, PL, or Direct Absent
        // ══════════════════════════════════════════════════════
        if (ACTION_PROMPT_ABSENT.equals(action)) {
            final String dateISO = intent.getStringExtra(EXTRA_DATE);
            final String company = intent.getStringExtra(EXTRA_COMPANY);
            final int notifId = intent.getIntExtra(EXTRA_NOTIF_ID, NotificationAlarmReceiver.NOTIF_ID_MORNING);

            if (dateISO == null) return;
            final String targetDate = dateISO.trim();
            final String compName = (company != null && !company.trim().isEmpty()) ? company : "Workplace";

            try {
                Intent mainIntent = new Intent(context, MainActivity.class);
                mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                int pFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
                PendingIntent contentIntent = PendingIntent.getActivity(context, notifId, mainIntent, pFlags);

                // Option 1: Sick Leave (SL)
                Intent slIntent = new Intent(context, NotificationActionReceiver.class);
                slIntent.setAction(ACTION_MARK_ATTENDANCE);
                slIntent.putExtra(EXTRA_STATUS, "SL");
                slIntent.putExtra(EXTRA_DATE, targetDate);
                slIntent.putExtra(EXTRA_COMPANY, compName);
                slIntent.putExtra(EXTRA_NOTIF_ID, notifId);
                PendingIntent slPi = PendingIntent.getBroadcast(context, notifId * 10 + 4, slIntent, pFlags);

                // Option 2: Paid Leave (PL)
                Intent plIntent = new Intent(context, NotificationActionReceiver.class);
                plIntent.setAction(ACTION_MARK_ATTENDANCE);
                plIntent.putExtra(EXTRA_STATUS, "PL");
                plIntent.putExtra(EXTRA_DATE, targetDate);
                plIntent.putExtra(EXTRA_COMPANY, compName);
                plIntent.putExtra(EXTRA_NOTIF_ID, notifId);
                PendingIntent plPi = PendingIntent.getBroadcast(context, notifId * 10 + 5, plIntent, pFlags);

                // Option 3: Half Day (HD)
                Intent hdIntent = new Intent(context, NotificationActionReceiver.class);
                hdIntent.setAction(ACTION_MARK_ATTENDANCE);
                hdIntent.putExtra(EXTRA_STATUS, "HD");
                hdIntent.putExtra(EXTRA_DATE, targetDate);
                hdIntent.putExtra(EXTRA_COMPANY, compName);
                hdIntent.putExtra(EXTRA_NOTIF_ID, notifId);
                PendingIntent hdPi = PendingIntent.getBroadcast(context, notifId * 10 + 6, hdIntent, pFlags);

                // Option 4: Direct Absent (A)
                Intent aIntent = new Intent(context, NotificationActionReceiver.class);
                aIntent.setAction(ACTION_MARK_ATTENDANCE);
                aIntent.putExtra(EXTRA_STATUS, "A");
                aIntent.putExtra(EXTRA_DATE, targetDate);
                aIntent.putExtra(EXTRA_COMPANY, compName);
                aIntent.putExtra(EXTRA_NOTIF_ID, notifId);
                PendingIntent aPi = PendingIntent.getBroadcast(context, notifId * 10 + 7, aIntent, pFlags);

                NotificationCompat.Builder promptBuilder = new NotificationCompat.Builder(context, NotificationAlarmReceiver.CHANNEL_ATTENDANCE)
                    .setSmallIcon(R.drawable.ic_stat_attendance)
                    .setContentTitle("Reason for Absence")
                    .setContentText("Select leave reason: Sick Leave, Paid Leave, Half Day, or Absent.")
                    .setStyle(new NotificationCompat.BigTextStyle().bigText("Select absence reason for " + compName + ": Sick Leave (SL), Paid Leave (PL), Half Day (HD), or Absent (A):"))
                    .setColor(Color.parseColor("#F59E0B"))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .setVibrate(new long[]{0, 150, 100, 150})
                    .addAction(R.drawable.ic_action_halfday, "Sick Leave (SL)", slPi)
                    .addAction(R.drawable.ic_action_halfday, "Paid Leave (PL)", plPi)
                    .addAction(R.drawable.ic_action_halfday, "Half Day (HD)", hdPi)
                    .addAction(R.drawable.ic_action_absent, "Absent (A)", aPi);

                NotificationManagerCompat manager = NotificationManagerCompat.from(context);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                        manager.notify(notifId, promptBuilder.build());
                    }
                } else {
                    manager.notify(notifId, promptBuilder.build());
                }

                // Show quick guidance toast
                new Handler(Looper.getMainLooper()).post(() -> {
                    try {
                        Toast.makeText(context, "Select: Sick Leave, Paid Leave, Half Day, or Absent", Toast.LENGTH_SHORT).show();
                    } catch (Exception ignored) {}
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }

        // ══════════════════════════════════════════════════════
        // 2. ATTENDANCE 1-TAP CHECK-IN ACTION
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

            // 2. Determine user-friendly status title, greeting & color
            String statusLabel = "Present";
            String confirmTitle = "Present Marked";
            String confirmBody = "Attendance recorded as Present for today at " + compName + ".";
            String toastText = "Present marked for today.";
            int statusColor = Color.parseColor("#10B981");

            if ("A".equals(targetStatus)) {
                statusLabel = "Absent";
                confirmTitle = "Absent Marked";
                confirmBody = "Attendance recorded as Absent for today at " + compName + ".";
                toastText = "Absent marked for today.";
                statusColor = Color.parseColor("#EF4444");
            } else if ("SL".equals(targetStatus)) {
                statusLabel = "Sick Leave";
                confirmTitle = "Sick Leave Logged";
                confirmBody = "Sick leave recorded for " + targetDate + " at " + compName + ".";
                toastText = "Sick Leave marked for today.";
                statusColor = Color.parseColor("#EC4899");
            } else if ("PL".equals(targetStatus)) {
                statusLabel = "Paid Leave";
                confirmTitle = "Paid Leave Logged";
                confirmBody = "Paid leave recorded for " + targetDate + " at " + compName + ".";
                toastText = "Paid Leave marked for today.";
                statusColor = Color.parseColor("#3B82F6");
            } else if ("WO".equals(targetStatus)) {
                statusLabel = "Week Off";
                confirmTitle = "Week Off Logged";
                confirmBody = "Today recorded as scheduled Week Off.";
                toastText = "Week Off marked for today.";
                statusColor = Color.parseColor("#6366F1");
            } else if ("HD".equals(targetStatus)) {
                statusLabel = "Half Day";
                confirmTitle = "Half Day Logged";
                confirmBody = "Half Day recorded for " + targetDate + " at " + compName + ".";
                toastText = "Half Day marked for today.";
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
                    .setContentTitle(confirmTitle)
                    .setContentText(confirmBody)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(confirmBody))
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
            final String finalToastText = toastText;
            new Handler(Looper.getMainLooper()).post(() -> {
                try {
                    Toast.makeText(context, finalToastText, Toast.LENGTH_SHORT).show();
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

            // 6. If Sick Leave (SL) was marked, send dedicated personalized Health Care Notification
            if ("SL".equals(targetStatus)) {
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try {
                        sendSickLeaveCareNotification(context, targetDate);
                    } catch (Exception ignored) {}
                }, 1500);
            }
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

                    String todayISO = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(now.getTime());
                    String timeStr = new SimpleDateFormat("hh:mm a", Locale.US).format(now.getTime());

                    JSONObject entry = new JSONObject();
                    entry.put("date", todayISO);
                    entry.put("hour", curHour);
                    entry.put("minute", curMin);
                    history.put(entry);

                    // Keep up to 14 records for multi-day habit learning
                    while (history.length() > 14) {
                        history.remove(0);
                    }

                    // Multi-day weighted moving average: recent days 3x weight, mid 2x, older 1x
                    int totalWeightedMinutes = 0;
                    int totalWeight = 0;
                    for (int i = 0; i < history.length(); i++) {
                        JSONObject item = history.getJSONObject(i);
                        int mins = item.getInt("hour") * 60 + item.getInt("minute");
                        int weight = 1;
                        if (i >= history.length() - 3) {
                            weight = 3;
                        } else if (i >= history.length() - 7) {
                            weight = 2;
                        }
                        totalWeightedMinutes += (mins * weight);
                        totalWeight += weight;
                    }
                    int avgMinOfDay = totalWeight > 0 ? (totalWeightedMinutes / totalWeight) : (curHour * 60 + curMin);
                    int learnedHour = avgMinOfDay / 60;
                    int learnedMin = avgMinOfDay % 60;

                    // Bound between 11:30 AM and 03:30 PM (reasonable workday lunch window)
                    if (learnedHour < 11 || (learnedHour == 11 && learnedMin < 30)) { learnedHour = 11; learnedMin = 30; }
                    if (learnedHour > 15 || (learnedHour == 15 && learnedMin > 30)) { learnedHour = 15; learnedMin = 30; }

                    prefs.edit()
                        .putString("_mnm_lunch_history", history.toString())
                        .putString("_mnm_today_lunch_date", todayISO)
                        .putBoolean("_mnm_today_lunch_done", true)
                        .putString("_mnm_today_lunch_time", timeStr)
                        .putInt("_mnm_learned_lunch_hour", learnedHour)
                        .putInt("_mnm_learned_lunch_minute", learnedMin)
                        .commit();

                    // Re-schedule tomorrow's alarm to the newly learned time!
                    NotificationScheduler.scheduleDailyAlarm(context, NotificationScheduler.REQ_LUNCH_REMINDER, learnedHour, learnedMin, NotificationScheduler.TYPE_LUNCH_REMINDER);

                    // Broadcast learned lunch time to MainActivity & Web UI
                    Intent bIntent = new Intent(ACTION_LUNCH_BROADCAST);
                    bIntent.putExtra("learned_hour", learnedHour);
                    bIntent.putExtra("learned_min", learnedMin);
                    bIntent.putExtra("today_done", true);
                    bIntent.putExtra("today_time", timeStr);
                    bIntent.setPackage(context.getPackageName());
                    context.sendBroadcast(bIntent);

                } catch (Exception e) {
                    e.printStackTrace();
                }

                // Update notification in shade to confirmation
                try {
                    NotificationCompat.Builder confirmBuilder = new NotificationCompat.Builder(context, NotificationAlarmReceiver.CHANNEL_WELLNESS)
                        .setSmallIcon(R.drawable.ic_stat_attendance)
                        .setContentTitle("Lunch Recorded")
                        .setContentText("Lunch break recorded successfully.")
                        .setStyle(new NotificationCompat.BigTextStyle().bigText("Lunch break recorded successfully. Stay productive for the rest of your shift."))
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
                    Toast.makeText(context, "Lunch recorded successfully.", Toast.LENGTH_SHORT).show();
                });

            } else {
                // USER REPLIED "NOT YET" (NO): GENTLE FOLLOW-UP IN 35 MINS
                try {
                    NotificationCompat.Builder laterBuilder = new NotificationCompat.Builder(context, NotificationAlarmReceiver.CHANNEL_WELLNESS)
                        .setSmallIcon(R.drawable.ic_stat_notification)
                        .setContentTitle("Lunch Reminder")
                        .setContentText("Reminder scheduled in 35 minutes.")
                        .setStyle(new NotificationCompat.BigTextStyle().bigText("Reminder scheduled in 35 minutes. Remember to take your meal break."))
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
                    Toast.makeText(context, "Reminder postponed. Will remind you in 35 minutes.", Toast.LENGTH_SHORT).show();
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
                    .setContentTitle("Wellness Update")
                    .setContentText("Status updated successfully.")
                    .setColor(Color.parseColor("#06B6D4"))
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setAutoCancel(true);
                manager.notify(notifId, ackBuilder.build());

                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try { manager.cancel(notifId); } catch (Exception ignored) {}
                }, 5000);
            } catch (Exception ignored) {}
        }
    }

    // Helper: Sends unique, caring, highly empathetic recovery notification with user's name
    public static void sendSickLeaveCareNotification(Context context, String targetDate) {
        if (context == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences("mrnodeman_native_store", Context.MODE_PRIVATE);
            String profileStr = prefs.getString("_mnm_work_profile", null);
            String workerName = "Champion";
            String companyName = "Workplace";
            if (profileStr != null) {
                try {
                    JSONObject wp = new JSONObject(profileStr);
                    String name = wp.optString("workerName", "");
                    if (name.trim().isEmpty()) name = wp.optString("name", "");
                    if (!name.trim().isEmpty()) {
                        workerName = name.trim();
                    }
                    String comp = wp.optString("company", "");
                    if (!comp.trim().isEmpty()) {
                        companyName = comp.trim();
                    }
                } catch (Exception ignored) {}
            }
            if ("Champion".equals(workerName)) {
                try {
                    String masterDb = prefs.getString("_mnm_master_db_backup", null);
                    if (masterDb != null) {
                        JSONObject db = new JSONObject(masterDb);
                        JSONObject user = db.optJSONObject("user");
                        if (user != null && !user.optString("name").trim().isEmpty()) {
                            workerName = user.optString("name").trim();
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Read rotational care counter & day of year so each notification is uniquely selected
            int careCount = prefs.getInt("_mnm_sick_care_count", 0);
            prefs.edit().putInt("_mnm_sick_care_count", careCount + 1).commit();
            int daySeed = Calendar.getInstance().get(Calendar.DAY_OF_YEAR);

            // ══════════════════════════════════════════════════════════════════════════
            // APOLOGY + SYMPATHY + TOTAL OWNERSHIP DYNAMIC CARE REPERTORY
            // 1. Apology: Genuine regret/remorse that user is facing illness/discomfort
            // 2. Sympathy: Deep empathy, recovery well-wishes, health prioritization
            // 3. Ownership: Complete responsibility for work/shift logs at workplace; zero guilt/stress
            // ══════════════════════════════════════════════════════════════════════════
            String[][] careMessages = new String[][] {
                {
                    "We are Truly Sorry You Are Unwell, " + workerName,
                    "We are genuinely sorry you are feeling unwell today. Please do not worry about your shift or duties at " + companyName + " — we have taken full ownership of your records. Your health is our highest priority. Take complete rest, stay hydrated, and get well soon."
                },
                {
                    "Health First, " + workerName + " — Work is Fully Covered",
                    "It pains us to see you under the weather today. Disconnect from work completely — everything at " + companyName + " is safely managed by us. Focus entirely on your healing, drink warm fluids, and take your medicines on time. Wishing you a speedy recovery."
                },
                {
                    "Get Well Soon, " + workerName + " — Zero Work Stress",
                    "We deeply regret that you are having a difficult health day. Zero stress about today's attendance or shift — your duty is fully protected. Please consult a doctor if required and give your body the rest it deserves. We stand with you."
                },
                {
                    "Rest Easy Today, " + workerName + " — We Have Your Back",
                    "So sorry you are sick today. Step away from all work anxiety — Mr.NodeMan and " + companyName + " have taken total responsibility for today's logs. Leave the desk to us and focus 100% on regaining your strength."
                },
                {
                    "Take It Slow Today, " + workerName + " — Your Shift is Protected",
                    "We are truly sorry to hear about your illness. Do not think twice about work targets or hours today — your sick leave is officially recorded and secured. Drink warm soup, take deep rest, and recover at your own pace."
                },
                {
                    "Your Well-Being Comes First, " + workerName,
                    "We are so sorry you are unwell today. Work can always wait, but your health cannot. We take full ownership of your shift today at " + companyName + ". Rest peacefully, stay comfortable, and feel better soon."
                },
                {
                    "Sending Healing Thoughts, " + workerName,
                    "Hate seeing you feel unwell today. Please give yourself complete permission to rest without any guilt. No work or pending tasks will bother you today at " + companyName + ". We are cheering for your fast, gentle recovery."
                },
                {
                    "You are in Safe Hands, " + workerName + " — Rest Completely",
                    "We apologize for the discomfort illness brings. Relax with total peace of mind — your shift records and tasks are safely taken care of. Take proper nourishment, rest well, and bounce back stronger whenever you are ready."
                }
            };

            int idx = Math.abs(careCount + daySeed) % careMessages.length;
            String title = careMessages[idx][0];
            String body = careMessages[idx][1];

            Intent mainIntent = new Intent(context, MainActivity.class);
            mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int pFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent contentIntent = PendingIntent.getActivity(context, NotificationAlarmReceiver.NOTIF_ID_SICK_CARE, mainIntent, pFlags);

            Uri soundUri = NotificationAlarmReceiver.getCustomSoundUri(context);

            NotificationCompat.Builder careBuilder = new NotificationCompat.Builder(context, NotificationAlarmReceiver.CHANNEL_WELLNESS)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setColor(Color.parseColor("#EC4899"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setSound(soundUri)
                .setVibrate(new long[]{0, 200, 100, 200});

            NotificationManagerCompat manager = NotificationManagerCompat.from(context);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    manager.notify(NotificationAlarmReceiver.NOTIF_ID_SICK_CARE, careBuilder.build());
                }
            } else {
                manager.notify(NotificationAlarmReceiver.NOTIF_ID_SICK_CARE, careBuilder.build());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
