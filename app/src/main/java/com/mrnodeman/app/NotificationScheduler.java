package com.mrnodeman.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONObject;
import java.util.Calendar;

public class NotificationScheduler {

    public static final String ACTION_ALARM_TRIGGER = "com.mrnodeman.app.ACTION_ALARM_TRIGGER";
    public static final String EXTRA_ALARM_TYPE = "alarm_type";

    public static final String TYPE_MORNING_SHIFT = "morning_shift";
    public static final String TYPE_EVENING_PENDING = "evening_pending";
    public static final String TYPE_ADVANCE_ROSTER = "advance_roster";
    public static final String TYPE_SALARY_DAY = "salary_day";
    public static final String TYPE_CLIENT_DUES = "client_dues";

    public static final int REQ_MORNING_SHIFT = 1001;
    public static final int REQ_EVENING_PENDING = 1002;
    public static final int REQ_ADVANCE_ROSTER = 1003;
    public static final int REQ_SALARY_DAY = 1004;
    public static final int REQ_CLIENT_DUES = 1005;

    public static void scheduleAllAlarms(Context context) {
        if (context == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences("mrnodeman_native_store", Context.MODE_PRIVATE);
            String settingsJsonStr = prefs.getString("_mnm_notification_settings", null);

            boolean enabled = true;
            boolean morningShift = true;
            boolean eveningPending = true;
            boolean advanceRoster = true;
            boolean salaryAlerts = true;
            boolean clientDues = true;

            if (settingsJsonStr != null && !settingsJsonStr.isEmpty()) {
                try {
                    JSONObject obj = new JSONObject(settingsJsonStr);
                    if (obj.has("enabled")) enabled = obj.optBoolean("enabled", true);
                    if (obj.has("morningShift")) morningShift = obj.optBoolean("morningShift", true);
                    if (obj.has("eveningPending")) eveningPending = obj.optBoolean("eveningPending", true);
                    if (obj.has("advanceRoster")) advanceRoster = obj.optBoolean("advanceRoster", true);
                    if (obj.has("salaryAlerts")) salaryAlerts = obj.optBoolean("salaryAlerts", true);
                    if (obj.has("clientDues")) clientDues = obj.optBoolean("clientDues", true);
                } catch (Exception ignored) {}
            }

            if (!enabled) {
                cancelAllAlarms(context);
                return;
            }

            // 1. Morning Shift Check-in (09:00 AM)
            if (morningShift) {
                scheduleDailyAlarm(context, REQ_MORNING_SHIFT, 9, 0, TYPE_MORNING_SHIFT);
            } else {
                cancelAlarm(context, REQ_MORNING_SHIFT);
            }

            // 2. Evening Pending Attendance & Streak Savior (07:30 PM = 19:30)
            if (eveningPending) {
                scheduleDailyAlarm(context, REQ_EVENING_PENDING, 19, 30, TYPE_EVENING_PENDING);
            } else {
                cancelAlarm(context, REQ_EVENING_PENDING);
            }

            // 3. 1-Day Advance Roster & Leave Alerts (08:00 PM = 20:00)
            if (advanceRoster) {
                scheduleDailyAlarm(context, REQ_ADVANCE_ROSTER, 20, 0, TYPE_ADVANCE_ROSTER);
            } else {
                cancelAlarm(context, REQ_ADVANCE_ROSTER);
            }

            // 4. Monthly Salary Credit Day Alerts (09:30 AM)
            if (salaryAlerts) {
                scheduleDailyAlarm(context, REQ_SALARY_DAY, 9, 30, TYPE_SALARY_DAY);
            } else {
                cancelAlarm(context, REQ_SALARY_DAY);
            }

            // 5. Client Outstanding Receivables Reminder (11:00 AM)
            if (clientDues) {
                scheduleDailyAlarm(context, REQ_CLIENT_DUES, 11, 0, TYPE_CLIENT_DUES);
            } else {
                cancelAlarm(context, REQ_CLIENT_DUES);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void scheduleDailyAlarm(Context context, int requestCode, int hourOfDay, int minute, String alarmType) {
        if (context == null) return;
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;

            Intent intent = new Intent(context, NotificationAlarmReceiver.class);
            intent.setAction(ACTION_ALARM_TRIGGER);
            intent.putExtra(EXTRA_ALARM_TYPE, alarmType);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags);

            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
            calendar.set(Calendar.MINUTE, minute);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);

            // If time already passed today, schedule for tomorrow
            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1);
            }

            long triggerAtMillis = calendar.getTimeInMillis();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                    } else {
                        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void cancelAlarm(Context context, int requestCode) {
        if (context == null) return;
        try {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager == null) return;

            Intent intent = new Intent(context, NotificationAlarmReceiver.class);
            intent.setAction(ACTION_ALARM_TRIGGER);

            int flags = PendingIntent.FLAG_NO_CREATE;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags);
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent);
                pendingIntent.cancel();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void cancelAllAlarms(Context context) {
        cancelAlarm(context, REQ_MORNING_SHIFT);
        cancelAlarm(context, REQ_EVENING_PENDING);
        cancelAlarm(context, REQ_ADVANCE_ROSTER);
        cancelAlarm(context, REQ_SALARY_DAY);
        cancelAlarm(context, REQ_CLIENT_DUES);
    }
}
