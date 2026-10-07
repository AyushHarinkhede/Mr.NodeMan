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
    public static final String TYPE_LUNCH_REMINDER = "lunch_reminder";
    public static final String TYPE_SHIFT_END = "shift_end";
    public static final String TYPE_BIO_WELLNESS_BREAK = "bio_wellness_break";
    public static final String TYPE_ATTENDANCE_FOLLOWUP = "attendance_followup";
    public static final String TYPE_BIRTHDAY_GREETING = "birthday_greeting";
    public static final String TYPE_WORK_ANNIVERSARY = "work_anniversary";
    public static final String TYPE_SHIFT_STATS = "shift_stats";
    public static final String TYPE_NIGHT_STREAK = "night_streak";

    public static final int REQ_MORNING_SHIFT = 1001;
    public static final int REQ_EVENING_PENDING = 1002;
    public static final int REQ_ADVANCE_ROSTER = 1003;
    public static final int REQ_SALARY_DAY = 1004;
    public static final int REQ_CLIENT_DUES = 1005;
    public static final int REQ_LUNCH_REMINDER = 1006;
    public static final int REQ_SHIFT_END = 1007;
    public static final int REQ_BIO_WELLNESS_1 = 1008;
    public static final int REQ_BIO_WELLNESS_2 = 1009;
    public static final int REQ_ATTENDANCE_FOLLOWUP = 1010;
    public static final int REQ_BIRTHDAY_GREETING = 1011;
    public static final int REQ_WORK_ANNIVERSARY = 1012;
    public static final int REQ_SHIFT_STATS = 1013;
    public static final int REQ_NIGHT_STREAK = 1014;

    public static void scheduleAllAlarms(Context context) {
        if (context == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences("mrnodeman_native_store", Context.MODE_PRIVATE);
            String settingsJsonStr = prefs.getString("_mnm_notification_settings", null);
            String profileJsonStr = prefs.getString("_mnm_work_profile", null);

            boolean enabled = true;
            boolean morningShift = true;
            boolean eveningPending = true;
            boolean advanceRoster = true;
            boolean salaryAlerts = true;
            boolean clientDues = true;
            boolean lunchReminder = true;
            boolean shiftEndGreeting = true;
            boolean wellnessBreaks = true;
            boolean attendanceFollowup = true;
            boolean shiftStats = true;
            boolean nightStreak = true;

            if (settingsJsonStr != null && !settingsJsonStr.isEmpty()) {
                try {
                    JSONObject obj = new JSONObject(settingsJsonStr);
                    if (obj.has("enabled")) enabled = obj.optBoolean("enabled", true);
                    if (obj.has("morningShift")) morningShift = obj.optBoolean("morningShift", true);
                    if (obj.has("eveningPending")) eveningPending = obj.optBoolean("eveningPending", true);
                    if (obj.has("advanceRoster")) advanceRoster = obj.optBoolean("advanceRoster", true);
                    if (obj.has("salaryAlerts")) salaryAlerts = obj.optBoolean("salaryAlerts", true);
                    if (obj.has("clientDues")) clientDues = obj.optBoolean("clientDues", true);
                    if (obj.has("lunchReminder")) lunchReminder = obj.optBoolean("lunchReminder", true);
                    if (obj.has("shiftEndGreeting")) shiftEndGreeting = obj.optBoolean("shiftEndGreeting", true);
                    if (obj.has("wellnessBreaks")) wellnessBreaks = obj.optBoolean("wellnessBreaks", true);
                    if (obj.has("attendanceFollowup")) attendanceFollowup = obj.optBoolean("attendanceFollowup", true);
                    if (obj.has("shiftStats")) shiftStats = obj.optBoolean("shiftStats", true);
                    if (obj.has("nightStreak")) nightStreak = obj.optBoolean("nightStreak", true);
                } catch (Exception ignored) {}
            }

            if (!enabled) {
                cancelAllAlarms(context);
                return;
            }

            // 1. Morning Shift Check-in (09:00 AM) - 1-Tap Attendance Check-in
            if (morningShift) {
                scheduleDailyAlarm(context, REQ_MORNING_SHIFT, 9, 0, TYPE_MORNING_SHIFT);
            } else {
                cancelAlarm(context, REQ_MORNING_SHIFT);
            }

            // 2. Intelligent Attendance Follow-up (10:45 AM) - Only triggered ONCE if morning skipped
            if (morningShift && attendanceFollowup) {
                scheduleDailyAlarm(context, REQ_ATTENDANCE_FOLLOWUP, 10, 45, TYPE_ATTENDANCE_FOLLOWUP);
            } else {
                cancelAlarm(context, REQ_ATTENDANCE_FOLLOWUP);
            }

            // 3. Smart Self-Learning Lunch Reminder (Interactive Yes/No)
            // Reads learned lunch hour/minute from user's response history
            int learnedLunchHour = prefs.getInt("_mnm_learned_lunch_hour", 13);
            int learnedLunchMinute = prefs.getInt("_mnm_learned_lunch_minute", 0);
            if (learnedLunchHour < 11 || learnedLunchHour > 16) {
                learnedLunchHour = 13;
                learnedLunchMinute = 0;
            }
            if (lunchReminder) {
                scheduleDailyAlarm(context, REQ_LUNCH_REMINDER, learnedLunchHour, learnedLunchMinute, TYPE_LUNCH_REMINDER);
            } else {
                cancelAlarm(context, REQ_LUNCH_REMINDER);
            }

            // 4. Bio Break, Posture & Hydration Wellness Reminders (Removed: trivial recurring notifications)
            cancelAlarm(context, REQ_BIO_WELLNESS_1);
            cancelAlarm(context, REQ_BIO_WELLNESS_2);

            // 5. Shift End Motivation Greeting (Removed: trivial non-interactive notifications)
            cancelAlarm(context, REQ_SHIFT_END);

            // 6. Evening Pending Attendance & Streak Savior (07:30 PM = 19:30)
            if (eveningPending) {
                scheduleDailyAlarm(context, REQ_EVENING_PENDING, 19, 30, TYPE_EVENING_PENDING);
            } else {
                cancelAlarm(context, REQ_EVENING_PENDING);
            }

            // 7. 1-Day Advance Roster & Leave Alerts (08:00 PM = 20:00)
            if (advanceRoster) {
                scheduleDailyAlarm(context, REQ_ADVANCE_ROSTER, 20, 0, TYPE_ADVANCE_ROSTER);
            } else {
                cancelAlarm(context, REQ_ADVANCE_ROSTER);
            }

            // 8. Monthly Salary Credit Day Alerts (09:30 AM)
            if (salaryAlerts) {
                scheduleDailyAlarm(context, REQ_SALARY_DAY, 9, 30, TYPE_SALARY_DAY);
            } else {
                cancelAlarm(context, REQ_SALARY_DAY);
            }

            // 9. Client Outstanding Receivables Reminder (11:00 AM)
            if (clientDues) {
                scheduleDailyAlarm(context, REQ_CLIENT_DUES, 11, 0, TYPE_CLIENT_DUES);
            } else {
                cancelAlarm(context, REQ_CLIENT_DUES);
            }

            // 10. Birthday & Advance Birthday Wish Alarm (08:30 AM)
            scheduleDailyAlarm(context, REQ_BIRTHDAY_GREETING, 8, 30, TYPE_BIRTHDAY_GREETING);

            // 11. Work Tenure & Anniversary Greetings (09:15 AM - 1st of month & joining anniversary)
            scheduleDailyAlarm(context, REQ_WORK_ANNIVERSARY, 9, 15, TYPE_WORK_ANNIVERSARY);

            // 12. Specialized Shift & Stats Progress Notification (04:30 PM = 16:30)
            if (shiftStats) {
                scheduleDailyAlarm(context, REQ_SHIFT_STATS, 16, 30, TYPE_SHIFT_STATS);
            } else {
                cancelAlarm(context, REQ_SHIFT_STATS);
            }

            // 13. Night Streak Reminder & Motivation (09:45 PM = 21:45)
            if (nightStreak) {
                scheduleDailyAlarm(context, REQ_NIGHT_STREAK, 21, 45, TYPE_NIGHT_STREAK);
            } else {
                cancelAlarm(context, REQ_NIGHT_STREAK);
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

    public static void scheduleOneShotAlarm(Context context, int requestCode, long delayMillis, String alarmType) {
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
            long triggerAtMillis = System.currentTimeMillis() + delayMillis;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
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
        cancelAlarm(context, REQ_ATTENDANCE_FOLLOWUP);
        cancelAlarm(context, REQ_LUNCH_REMINDER);
        cancelAlarm(context, REQ_BIO_WELLNESS_1);
        cancelAlarm(context, REQ_BIO_WELLNESS_2);
        cancelAlarm(context, REQ_SHIFT_END);
        cancelAlarm(context, REQ_EVENING_PENDING);
        cancelAlarm(context, REQ_ADVANCE_ROSTER);
        cancelAlarm(context, REQ_SALARY_DAY);
        cancelAlarm(context, REQ_CLIENT_DUES);
        cancelAlarm(context, REQ_BIRTHDAY_GREETING);
        cancelAlarm(context, REQ_WORK_ANNIVERSARY);
        cancelAlarm(context, REQ_SHIFT_STATS);
        cancelAlarm(context, REQ_NIGHT_STREAK);
    }
}
