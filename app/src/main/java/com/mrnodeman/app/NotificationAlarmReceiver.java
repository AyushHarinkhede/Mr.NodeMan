package com.mrnodeman.app;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class NotificationAlarmReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ATTENDANCE = "channel_attendance_shifts_v2";
    public static final String CHANNEL_SALARY = "channel_salary_payouts_v2";
    public static final String CHANNEL_REMINDERS = "channel_reminders_streaks_v2";
    public static final String CHANNEL_PAYMENTS = "channel_client_payments_v2";
    public static final String CHANNEL_WELLNESS = "channel_wellness_breaks_v2";
    public static final String CHANNEL_SHIFT_GREETINGS = "channel_shift_greetings_v2";

    public static Uri getCustomSoundUri(Context context) {
        return Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + context.getPackageName() + "/" + R.raw.nodeman_notification);
    }

    public static final int NOTIF_ID_MORNING = 1001;
    public static final int NOTIF_ID_EVENING = 1002;
    public static final int NOTIF_ID_ADVANCE = 1003;
    public static final int NOTIF_ID_SALARY = 1004;
    public static final int NOTIF_ID_PAYMENTS = 1005;
    public static final int NOTIF_ID_LUNCH = 1006;
    public static final int NOTIF_ID_SHIFT_END = 1007;
    public static final int NOTIF_ID_WELLNESS = 1008;
    public static final int NOTIF_ID_ATTENDANCE_FOLLOWUP = 1009;
    public static final int NOTIF_ID_BIRTHDAY = 1010;
    public static final int NOTIF_ID_ANNIVERSARY = 1011;
    public static final int NOTIF_ID_SICK_CARE = 1012;
    public static final int NOTIF_ID_SHIFT_STATS = 1013;
    public static final int NOTIF_ID_NIGHT_STREAK = 1014;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;

        String alarmType = intent.getStringExtra(NotificationScheduler.EXTRA_ALARM_TYPE);
        if (alarmType == null) return;

        // Ensure notification channels exist
        ensureNotificationChannels(context);

        // Reschedule next occurrence of this alarm for tomorrow
        rescheduleNextAlarm(context, alarmType);

        // Read stored user settings, profile, attendance, and entries
        SharedPreferences prefs = context.getSharedPreferences("mrnodeman_native_store", Context.MODE_PRIVATE);
        String settingsStr = prefs.getString("_mnm_notification_settings", null);
        String profileStr = prefs.getString("_mnm_work_profile", null);
        String attendanceStr = prefs.getString("_mnm_attendance", null);
        String entriesStr = prefs.getString("_mnm_entries", null);

        boolean masterEnabled = true;
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

        if (settingsStr != null) {
            try {
                JSONObject s = new JSONObject(settingsStr);
                masterEnabled = s.optBoolean("enabled", true);
                morningShift = s.optBoolean("morningShift", true);
                eveningPending = s.optBoolean("eveningPending", true);
                advanceRoster = s.optBoolean("advanceRoster", true);
                salaryAlerts = s.optBoolean("salaryAlerts", true);
                clientDues = s.optBoolean("clientDues", true);
                lunchReminder = s.optBoolean("lunchReminder", true);
                shiftEndGreeting = s.optBoolean("shiftEndGreeting", true);
                wellnessBreaks = s.optBoolean("wellnessBreaks", true);
                attendanceFollowup = s.optBoolean("attendanceFollowup", true);
                shiftStats = s.optBoolean("shiftStats", true);
                nightStreak = s.optBoolean("nightStreak", true);
            } catch (Exception ignored) {}
        }

        if (!masterEnabled) return;

        // Parse work profile
        String companyName = "Workplace";
        String workerName = "Worker";
        int salaryPayDay = 5;
        JSONArray weekOffDays = null;
        if (profileStr != null) {
            try {
                JSONObject wp = new JSONObject(profileStr);
                companyName = wp.optString("company", "Workplace");
                if (companyName.trim().isEmpty()) companyName = "Workplace";
                workerName = wp.optString("workerName", "Worker");
                if (workerName.trim().isEmpty()) workerName = "Worker";
                salaryPayDay = wp.optInt("salaryPayDay", 5);
                weekOffDays = wp.optJSONArray("weekOffDays");
            } catch (Exception ignored) {}
        }

        // Parse attendance records
        JSONObject attendanceRecords = null;
        if (attendanceStr != null) {
            try {
                attendanceRecords = new JSONObject(attendanceStr);
            } catch (Exception ignored) {}
        }

        SimpleDateFormat isoFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar nowCal = Calendar.getInstance();
        String todayISO = isoFmt.format(nowCal.getTime());

        Calendar tomCal = Calendar.getInstance();
        tomCal.add(Calendar.DAY_OF_YEAR, 1);
        String tomorrowISO = isoFmt.format(tomCal.getTime());

        JSONObject todayRec = attendanceRecords != null ? attendanceRecords.optJSONObject(todayISO) : null;
        JSONObject tomRec = attendanceRecords != null ? attendanceRecords.optJSONObject(tomorrowISO) : null;

        // Daily unique message rotation seed based on day-of-year + day-of-week
        int daySeed = nowCal.get(Calendar.DAY_OF_YEAR) + nowCal.get(Calendar.DAY_OF_WEEK);

        // ══════════════════════════════════════════════════════
        // STRICT ANTI-SPAM ATTENDANCE ALGORITHM:
        // If user already marked attendance for today (P, HD, PL, SL, CL, WO, HL, A),
        // completely suppress all attendance-related alarms! Zero duplicate spam!
        // ══════════════════════════════════════════════════════
        boolean hasMarkedAttendance = todayRec != null && todayRec.optString("status") != null && !todayRec.optString("status").trim().isEmpty();

        if (hasMarkedAttendance) {
            if (NotificationScheduler.TYPE_MORNING_SHIFT.equals(alarmType) ||
                NotificationScheduler.TYPE_ATTENDANCE_FOLLOWUP.equals(alarmType) ||
                NotificationScheduler.TYPE_EVENING_PENDING.equals(alarmType)) {
                try {
                    NotificationManagerCompat.from(context).cancel(NOTIF_ID_MORNING);
                    NotificationManagerCompat.from(context).cancel(NOTIF_ID_ATTENDANCE_FOLLOWUP);
                    NotificationManagerCompat.from(context).cancel(NOTIF_ID_EVENING);
                } catch (Exception ignored) {}
                return;
            }
        }

        // ══════════════════════════════════════════════════════
        // 1. MORNING SHIFT CHECK-IN (09:00 AM)
        // ══════════════════════════════════════════════════════
        if (NotificationScheduler.TYPE_MORNING_SHIFT.equals(alarmType) && morningShift) {
            boolean isWeekOff = isConfiguredWeekOff(nowCal, weekOffDays, todayRec);
            boolean isHoliday = todayRec != null && "HL".equalsIgnoreCase(todayRec.optString("status"));

            if (isWeekOff) {
                showSimpleNotification(context, CHANNEL_ATTENDANCE, NOTIF_ID_MORNING,
                    "Today is your Week Off",
                    "Good morning! You are scheduled for a Week Off today. Have a restful day.",
                    R.drawable.ic_stat_attendance, Color.parseColor("#10B981"));
            } else if (isHoliday) {
                String holNote = todayRec.optString("note", "Official Holiday");
                showSimpleNotification(context, CHANNEL_ATTENDANCE, NOTIF_ID_MORNING,
                    "Official Holiday",
                    "Today is scheduled as a holiday (" + holNote + "). Enjoy your day.",
                    R.drawable.ic_stat_attendance, Color.parseColor("#3B82F6"));
            } else if (todayRec == null || todayRec.optString("status").isEmpty()) {
                String[] msg = getMorningGreeting(daySeed, companyName);
                showAttendanceActionNotification(context, NOTIF_ID_MORNING, msg[0], msg[1], todayISO, companyName);
                prefs.edit().putInt("att_prompt_" + todayISO, 1).commit();
            }
        }

        // ══════════════════════════════════════════════════════
        // 2. INTELLIGENT ATTENDANCE FOLLOW-UP (10:45 AM)
        // Only sent ONCE if user skipped or missed the 9:00 AM notification without marking!
        // ══════════════════════════════════════════════════════
        else if (NotificationScheduler.TYPE_ATTENDANCE_FOLLOWUP.equals(alarmType) && morningShift && attendanceFollowup) {
            if (!hasMarkedAttendance) {
                int promptCount = prefs.getInt("att_prompt_" + todayISO, 0);
                if (promptCount == 1) {
                    showAttendanceActionNotification(context, NOTIF_ID_ATTENDANCE_FOLLOWUP,
                        "Attendance Reminder",
                        "Reminder: You have not checked in for today at " + companyName + " yet. Tap below to log your attendance.",
                        todayISO, companyName);
                    prefs.edit().putInt("att_prompt_" + todayISO, 2).commit();
                }
            }
        }

        // ══════════════════════════════════════════════════════
        // 3. SMART LUNCH & BREAK REMINDER (INTERACTIVE YES / NO)
        // Triggered at user's learned lunch time (or 1:00 PM default)
        // ══════════════════════════════════════════════════════
        else if (NotificationScheduler.TYPE_LUNCH_REMINDER.equals(alarmType) && lunchReminder) {
            boolean todayLunchDone = todayISO.equals(prefs.getString("_mnm_today_lunch_date", "")) && prefs.getBoolean("_mnm_today_lunch_done", false);
            if (todayLunchDone) {
                // Lunch already marked today! Strict anti-spam: do not bother user.
                try {
                    NotificationManagerCompat.from(context).cancel(NOTIF_ID_LUNCH);
                } catch (Exception ignored) {}
            } else {
                String[] lunchMsg = getLunchReminder(daySeed);
                showLunchActionNotification(context, NOTIF_ID_LUNCH, lunchMsg[0], lunchMsg[1]);
            }
        }

        // ══════════════════════════════════════════════════════
        // 5. SHIFT END WRAP-UP & STATS RECAP (Scheduled at user's Shift End Time)
        // ══════════════════════════════════════════════════════
        else if (NotificationScheduler.TYPE_SHIFT_END.equals(alarmType) && shiftEndGreeting) {
            showShiftEndStatsNotification(context, companyName, workerName, profileStr, attendanceRecords, entriesStr, prefs);
        }

        // ══════════════════════════════════════════════════════
        // 6. EVENING PENDING ATTENDANCE & STREAK SAVIOR (07:30 PM)
        // Only triggered if still unlogged in evening
        // ══════════════════════════════════════════════════════
        else if (NotificationScheduler.TYPE_EVENING_PENDING.equals(alarmType) && eveningPending) {
            if (!hasMarkedAttendance) {
                showAttendanceActionNotification(context, NOTIF_ID_EVENING,
                    "Attendance Pending for Today",
                    "You have not logged your attendance for today at " + companyName + " yet. Tap below to mark your attendance.",
                    todayISO, companyName);
            }
        }

        // ══════════════════════════════════════════════════════
        // 7. 1-DAY ADVANCE ROSTER & LEAVES (08:00 PM)
        // ══════════════════════════════════════════════════════
        else if (NotificationScheduler.TYPE_ADVANCE_ROSTER.equals(alarmType) && advanceRoster) {
            boolean isTomWeekOff = isConfiguredWeekOff(tomCal, weekOffDays, tomRec);
            boolean isTomHoliday = tomRec != null && "HL".equalsIgnoreCase(tomRec.optString("status"));
            String tomStatus = tomRec != null ? tomRec.optString("status").toUpperCase() : "";

            if (isTomWeekOff) {
                showSimpleNotification(context, CHANNEL_ATTENDANCE, NOTIF_ID_ADVANCE,
                    "Tomorrow is Week Off",
                    "Reminder: You are scheduled for a Week Off tomorrow from " + companyName + ".",
                    R.drawable.ic_stat_attendance, Color.parseColor("#10B981"));
            } else if (isTomHoliday) {
                String note = tomRec != null ? tomRec.optString("note", "Official Holiday") : "Official Holiday";
                showSimpleNotification(context, CHANNEL_ATTENDANCE, NOTIF_ID_ADVANCE,
                    "Tomorrow is a Holiday",
                    "Reminder: Tomorrow is scheduled as a holiday (" + note + ") at " + companyName + ".",
                    R.drawable.ic_stat_attendance, Color.parseColor("#3B82F6"));
            } else if ("PL".equals(tomStatus) || "SL".equals(tomStatus) || "CL".equals(tomStatus) || "HD".equals(tomStatus)) {
                String lName = "PL".equals(tomStatus) ? "Paid Leave (PL)" : "SL".equals(tomStatus) ? "Sick Leave (SL)" : "CL".equals(tomStatus) ? "Casual Leave (CL)" : "Half Day (HD)";
                showSimpleNotification(context, CHANNEL_ATTENDANCE, NOTIF_ID_ADVANCE,
                    "Advance Leave: " + lName,
                    "Reminder: You have scheduled " + lName + " for tomorrow at " + companyName + ".",
                    R.drawable.ic_stat_attendance, Color.parseColor("#F59E0B"));
            }
        }

        // ══════════════════════════════════════════════════════
        // 8. MONTHLY SALARY CREDIT DAY ALERTS (09:30 AM)
        // ══════════════════════════════════════════════════════
        else if (NotificationScheduler.TYPE_SALARY_DAY.equals(alarmType) && salaryAlerts) {
            int currentDayOfMonth = nowCal.get(Calendar.DAY_OF_MONTH);
            int tomorrowDayOfMonth = tomCal.get(Calendar.DAY_OF_MONTH);

            if (currentDayOfMonth == salaryPayDay) {
                showSimpleNotification(context, CHANNEL_SALARY, NOTIF_ID_SALARY,
                    "Salary Day Today",
                    "Today is your scheduled monthly salary credit date from " + companyName + ". Please review your payslip.",
                    R.drawable.ic_stat_salary, Color.parseColor("#7C6FED"));
            } else if (tomorrowDayOfMonth == salaryPayDay) {
                showSimpleNotification(context, CHANNEL_SALARY, NOTIF_ID_SALARY,
                    "Salary Credit Tomorrow",
                    "Reminder: Tomorrow is your monthly salary payout date (" + salaryPayDay + "th) from " + companyName + ".",
                    R.drawable.ic_stat_salary, Color.parseColor("#7C6FED"));
            }
        }

        // ══════════════════════════════════════════════════════
        // 9. CLIENT PENDING RECEIVABLES REMINDER (11:00 AM)
        // ══════════════════════════════════════════════════════
        else if (NotificationScheduler.TYPE_CLIENT_DUES.equals(alarmType) && clientDues) {
            if (entriesStr != null) {
                try {
                    JSONArray arr = new JSONArray(entriesStr);
                    double totalPending = 0;
                    int pendingCount = 0;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject e = arr.getJSONObject(i);
                        double p = e.optDouble("pending", 0);
                        String st = e.optString("status", e.optString("paymentStatus", ""));
                        if (p > 0 && !"paid".equalsIgnoreCase(st)) {
                            totalPending += p;
                            pendingCount++;
                        }
                    }

                    if (totalPending > 0 && pendingCount > 0) {
                        String amtFormatted = String.format(Locale.US, "%,.0f", totalPending);
                        showSimpleNotification(context, CHANNEL_PAYMENTS, NOTIF_ID_PAYMENTS,
                            "Pending Client Receivables",
                            "You have Rs " + amtFormatted + " outstanding pending across " + pendingCount + " client sessions. Please review your invoices.",
                            R.drawable.ic_stat_salary, Color.parseColor("#F59E0B"));
                    }
                } catch (Exception ignored) {}
            }
        }

        // ══════════════════════════════════════════════════════
        // 10. BIRTHDAY & ADVANCE BIRTHDAY WISHES (08:30 AM)
        // ══════════════════════════════════════════════════════
        else if (NotificationScheduler.TYPE_BIRTHDAY_GREETING.equals(alarmType)) {
            String dobStr = null;
            if (profileStr != null) {
                try {
                    JSONObject wp = new JSONObject(profileStr);
                    dobStr = wp.optString("dob", null);
                } catch (Exception ignored) {}
            }
            if (dobStr == null || dobStr.isEmpty()) {
                String userStr = prefs.getString("_mnm_current_user", null);
                if (userStr != null) {
                    try {
                        JSONObject u = new JSONObject(userStr);
                        dobStr = u.optString("dob", null);
                    } catch (Exception ignored) {}
                }
            }

            if (dobStr != null && dobStr.contains("-")) {
                try {
                    String[] parts = dobStr.split("-");
                    int bMonth = Integer.parseInt(parts[1].trim()); // 1-indexed
                    int bDay = Integer.parseInt(parts[2].trim());

                    int nowMonth = nowCal.get(Calendar.MONTH) + 1; // 1-indexed
                    int nowDay = nowCal.get(Calendar.DAY_OF_MONTH);

                    int tomMonth = tomCal.get(Calendar.MONTH) + 1;
                    int tomDay = tomCal.get(Calendar.DAY_OF_MONTH);

                    String displayName = (workerName != null && !workerName.equalsIgnoreCase("Worker")) ? workerName : "Champion";

                    // Birthday TODAY
                    if (nowMonth == bMonth && nowDay == bDay) {
                        showSimpleNotification(context, CHANNEL_REMINDERS, NOTIF_ID_BIRTHDAY,
                            "Happy Birthday, " + displayName,
                            "Wishing you a very Happy Birthday. May your year ahead be filled with happiness, good health, and success.",
                            R.drawable.ic_stat_notification, Color.parseColor("#EC4899"));
                    }
                    // Birthday TOMORROW (Advance Birthday Wish)
                    else if (tomMonth == bMonth && tomDay == bDay) {
                        showSimpleNotification(context, CHANNEL_REMINDERS, NOTIF_ID_BIRTHDAY,
                            "Advance Birthday Wishes, " + displayName,
                            "Wishing you an advanced Happy Birthday from Mr.NodeMan. Have a wonderful day ahead.",
                            R.drawable.ic_stat_notification, Color.parseColor("#8B5CF6"));
                    }
                } catch (Exception ignored) {}
            }
        }

        // ══════════════════════════════════════════════════════
        // 11. WORK TENURE & ANNIVERSARY GREETINGS (09:15 AM)
        // ══════════════════════════════════════════════════════
        else if (NotificationScheduler.TYPE_WORK_ANNIVERSARY.equals(alarmType)) {
            String dojStr = null;
            if (profileStr != null) {
                try {
                    JSONObject wp = new JSONObject(profileStr);
                    dojStr = wp.optString("doj", null);
                } catch (Exception ignored) {}
            }

            if (dojStr != null && dojStr.contains("-")) {
                try {
                    String[] parts = dojStr.split("-");
                    int jYear = Integer.parseInt(parts[0].trim());
                    int jMonth = Integer.parseInt(parts[1].trim()); // 1-indexed
                    int jDay = Integer.parseInt(parts[2].trim());

                    int curYear = nowCal.get(Calendar.YEAR);
                    int curMonth = nowCal.get(Calendar.MONTH) + 1; // 1-indexed
                    int curDay = nowCal.get(Calendar.DAY_OF_MONTH);

                    int totalMonths = (curYear - jYear) * 12 + (curMonth - jMonth);
                    if (curDay < jDay) {
                        totalMonths--;
                    }

                    if (totalMonths >= 1) {
                        int fullYears = totalMonths / 12;

                        // Work Anniversary (Completed Exact Year(s) Today)
                        if (curMonth == jMonth && curDay == jDay && fullYears >= 1) {
                            showSimpleNotification(context, CHANNEL_SHIFT_GREETINGS, NOTIF_ID_ANNIVERSARY,
                                "Happy Work Anniversary",
                                "Congratulations! You have completed " + fullYears + " year" + (fullYears > 1 ? "s" : "") + " of service at " + companyName + ". Thank you for your dedication.",
                                R.drawable.ic_stat_notification, Color.parseColor("#10B981"));
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        // ══════════════════════════════════════════════════════
        // 12. SPECIALIZED SHIFT & STATS PROGRESS NOTIFICATION (04:30 PM)
        // Daily rotating dynamic notification reading user's monthly stats
        // ══════════════════════════════════════════════════════
        else if (NotificationScheduler.TYPE_SHIFT_STATS.equals(alarmType) && shiftStats) {
            String monthPrefix = new SimpleDateFormat("yyyy-MM", Locale.US).format(nowCal.getTime());
            
            // Check pre-calculated stats summary from JS if available
            String statsSummaryStr = prefs.getString("_mnm_stats_summary", null);
            double monthlyEarned = 0;
            double monthlyHours = 0;
            int monthlyShifts = 0;
            double totalPendingDues = 0;
            int streak = prefs.getInt("_mnm_current_streak", 0);

            if (statsSummaryStr != null) {
                try {
                    JSONObject ss = new JSONObject(statsSummaryStr);
                    monthlyEarned = ss.optDouble("monthlyEarnings", 0);
                    monthlyHours = ss.optDouble("monthlyHours", 0);
                    monthlyShifts = ss.optInt("monthlyShifts", 0);
                    totalPendingDues = ss.optDouble("pendingDues", 0);
                    if (streak <= 0) streak = ss.optInt("streak", 0);
                } catch (Exception ignored) {}
            }

            // Fallback / dynamic native aggregation from attendance & entries
            if (monthlyShifts == 0 && attendanceRecords != null) {
                try {
                    java.util.Iterator<String> keys = attendanceRecords.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        if (k.startsWith(monthPrefix)) {
                            JSONObject obj = attendanceRecords.optJSONObject(k);
                            if (obj != null) {
                                String st = obj.optString("status", "").toUpperCase(Locale.US);
                                if ("P".equals(st) || "PRESENT".equals(st) || "HD".equals(st) || "HALF".equals(st)) {
                                    monthlyShifts++;
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (monthlyEarned == 0 && entriesStr != null) {
                try {
                    JSONArray arr = new JSONArray(entriesStr);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject e = arr.getJSONObject(i);
                        String eDate = e.optString("date", "");
                        if (eDate.startsWith(monthPrefix)) {
                            monthlyEarned += e.optDouble("amount", e.optDouble("total", 0));
                            monthlyHours += e.optDouble("hours", e.optDouble("duration", 0));
                        }
                        double p = e.optDouble("pending", 0);
                        String pSt = e.optString("status", e.optString("paymentStatus", ""));
                        if (p > 0 && !"paid".equalsIgnoreCase(pSt)) {
                            totalPendingDues += p;
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Select 1 of 4 specialized daily rotating messages based on daySeed
            int rot = Math.abs(daySeed) % 4;
            String statTitle;
            String statBody;

            if (rot == 0) {
                statTitle = "Monthly Shift Summary";
                if (monthlyEarned > 0) {
                    statBody = "You have earned Rs " + String.format(Locale.US, "%,.0f", monthlyEarned) + " across " + monthlyShifts + " shifts this month at " + companyName + ". Excellent work!";
                } else {
                    statBody = "You have completed " + monthlyShifts + " shifts this month at " + companyName + ". Keep up the steady progress.";
                }
            } else if (rot == 1) {
                statTitle = "Daily Work Recap";
                if (monthlyHours > 0) {
                    statBody = "You have completed " + String.format(Locale.US, "%.1f", monthlyHours) + " hours across " + monthlyShifts + " shifts this month. Consistency brings success.";
                } else {
                    statBody = "Dedication in action: " + monthlyShifts + " shifts completed this month at " + companyName + ". Great effort today.";
                }
            } else if (rot == 2) {
                statTitle = "Consistent Work Milestone";
                if (streak > 1) {
                    statBody = "You are on an active " + streak + "-day streak. Keep your momentum going strong at " + companyName + ".";
                } else {
                    statBody = "Every logged shift brings you closer to your financial goals at " + companyName + ". Keep your streak alive.";
                }
            } else {
                statTitle = "Shift Progress and Receivables";
                if (totalPendingDues > 0) {
                    statBody = "Rs " + String.format(Locale.US, "%,.0f", monthlyEarned) + " earned this month, with Rs " + String.format(Locale.US, "%,.0f", totalPendingDues) + " pending from clients. Review your ledger in Mr.NodeMan.";
                } else {
                    statBody = "Milestone update: " + monthlyShifts + " shifts logged this month. You are on track for your monthly targets.";
                }
            }

            showSimpleNotification(context, CHANNEL_SHIFT_GREETINGS, NOTIF_ID_SHIFT_STATS, statTitle, statBody, R.drawable.ic_stat_salary, Color.parseColor("#7C6FED"));
        }

        // ══════════════════════════════════════════════════════
        // 13. NIGHT STREAK GUARDIAN & MOTIVATION (09:45 PM)
        // ══════════════════════════════════════════════════════
        else if (NotificationScheduler.TYPE_NIGHT_STREAK.equals(alarmType) && nightStreak) {
            int streak = prefs.getInt("_mnm_current_streak", 0);
            if (streak <= 0) {
                String statsSummaryStr = prefs.getString("_mnm_stats_summary", null);
                if (statsSummaryStr != null) {
                    try {
                        streak = new JSONObject(statsSummaryStr).optInt("streak", 0);
                    } catch (Exception ignored) {}
                }
            }

            if (!hasMarkedAttendance) {
                // Streak is at risk before midnight! Provide direct 1-tap Present action
                String streakTitle = streak > 0 ? ("Keep Your " + streak + "-Day Streak Active") : "Log Today's Shift Before Midnight";
                String streakMsg = streak > 0
                    ? ("You have not logged your shift for today at " + companyName + ". Mark Present before midnight to maintain your " + streak + "-day streak.")
                    : ("You have not logged your attendance for today at " + companyName + " yet. Tap below to record your shift before midnight.");
                
                showAttendanceActionNotification(context, NOTIF_ID_NIGHT_STREAK, streakTitle, streakMsg, todayISO, companyName);
            } else {
                // Today is already marked! Send occasional evening streak celebration
                if (streak >= 3 && (Math.abs(daySeed) % 2 == 0)) {
                    String cheerTitle = "Streak Maintained: " + streak + " Days";
                    String cheerMsg = "Today's shift at " + companyName + " is safely recorded. Great dedication today. Rest well tonight and recharge for tomorrow.";
                    showSimpleNotification(context, CHANNEL_REMINDERS, NOTIF_ID_NIGHT_STREAK, cheerTitle, cheerMsg, R.drawable.ic_stat_attendance, Color.parseColor("#F59E0B"));
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    // DYNAMIC DAILY UNIQUE NOTIFICATION MESSAGE POOLS
    // ══════════════════════════════════════════════════════════

    private String[] getMorningGreeting(int seed, String company) {
        String[][] pool = new String[][] {
            { "Morning Check-in", "Good morning! Please mark your attendance for today at " + company + "." },
            { "Daily Check-in", "Start your day. Please log your attendance for today at " + company + "." },
            { "Morning Shift Check-in", "Ready for your shift? Record your attendance for today at " + company + "." },
            { "Attendance Check-in", "Please mark your attendance for today at " + company + "." },
            { "Daily Work Check-in", "Don't forget to mark your daily attendance at " + company + "." }
        };
        return pool[Math.abs(seed) % pool.length];
    }

    private String[] getLunchReminder(int seed) {
        String[][] pool = new String[][] {
            { "Lunch Break Reminder", "Have you taken your lunch break today? Tap below to record your status." },
            { "Lunch Reminder", "Time for a meal break. Have you finished your lunch? Please record below." },
            { "Midday Meal Reminder", "Did you have your lunch today? Tap below to log your break." },
            { "Lunch Break Check", "Please confirm if you have completed your lunch break today." },
            { "Meal Break Reminder", "Remember to take your scheduled lunch break. Have you eaten today?" }
        };
        return pool[Math.abs(seed) % pool.length];
    }

    // ══════════════════════════════════════════════════════════
    // NOTIFICATION BUILDERS
    // ══════════════════════════════════════════════════════════

    // Helper: Build and post rich notification with direct 1-Tap Attendance Action buttons
    public static void showAttendanceActionNotification(Context context, int notifId, String title, String message, String dateISO, String company) {
        if (context == null) return;
        try {
            ensureNotificationChannels(context);

            Intent mainIntent = new Intent(context, MainActivity.class);
            mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int pFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent contentIntent = PendingIntent.getActivity(context, notifId, mainIntent, pFlags);

            String targetDate = (dateISO != null && !dateISO.isEmpty()) ? dateISO : new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            String targetComp = (company != null && !company.isEmpty()) ? company : "Workplace";

            // Action 1: Mark Present
            Intent presentIntent = new Intent(context, NotificationActionReceiver.class);
            presentIntent.setAction(NotificationActionReceiver.ACTION_MARK_ATTENDANCE);
            presentIntent.putExtra(NotificationActionReceiver.EXTRA_STATUS, "P");
            presentIntent.putExtra(NotificationActionReceiver.EXTRA_DATE, targetDate);
            presentIntent.putExtra(NotificationActionReceiver.EXTRA_COMPANY, targetComp);
            presentIntent.putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId);
            PendingIntent presentPi = PendingIntent.getBroadcast(context, notifId * 10 + 1, presentIntent, pFlags);

            // Action 2: Absent (Launches interactive reason question: SL, PL, Absent)
            Intent absentIntent = new Intent(context, NotificationActionReceiver.class);
            absentIntent.setAction(NotificationActionReceiver.ACTION_PROMPT_ABSENT);
            absentIntent.putExtra(NotificationActionReceiver.EXTRA_DATE, targetDate);
            absentIntent.putExtra(NotificationActionReceiver.EXTRA_COMPANY, targetComp);
            absentIntent.putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId);
            PendingIntent absentPi = PendingIntent.getBroadcast(context, notifId * 10 + 2, absentIntent, pFlags);

            // Action 3: Mark Week Off (WO)
            Intent woIntent = new Intent(context, NotificationActionReceiver.class);
            woIntent.setAction(NotificationActionReceiver.ACTION_MARK_ATTENDANCE);
            woIntent.putExtra(NotificationActionReceiver.EXTRA_STATUS, "WO");
            woIntent.putExtra(NotificationActionReceiver.EXTRA_DATE, targetDate);
            woIntent.putExtra(NotificationActionReceiver.EXTRA_COMPANY, targetComp);
            woIntent.putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId);
            PendingIntent woPi = PendingIntent.getBroadcast(context, notifId * 10 + 3, woIntent, pFlags);

            Uri soundUri = getCustomSoundUri(context);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ATTENDANCE)
                .setSmallIcon(R.drawable.ic_stat_attendance)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setColor(Color.parseColor("#36DFAF"))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setSound(soundUri)
                .setVibrate(new long[]{0, 200, 100, 200})
                .addAction(R.drawable.ic_action_present, "Present", presentPi)
                .addAction(R.drawable.ic_action_absent, "Absent", absentPi)
                .addAction(R.drawable.ic_action_halfday, "Week Off", woPi);

            NotificationManagerCompat manager = NotificationManagerCompat.from(context);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    manager.notify(notifId, builder.build());
                }
            } else {
                manager.notify(notifId, builder.build());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Helper: Build and post interactive lunch notification with [Yes, Completed] and [Not Yet]
    private void showLunchActionNotification(Context context, int notifId, String title, String message) {
        try {
            Intent mainIntent = new Intent(context, MainActivity.class);
            mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int pFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent contentIntent = PendingIntent.getActivity(context, notifId, mainIntent, pFlags);

            // Action 1: Yes, Completed
            Intent yesIntent = new Intent(context, NotificationActionReceiver.class);
            yesIntent.setAction(NotificationActionReceiver.ACTION_LUNCH_RESPONSE);
            yesIntent.putExtra(NotificationActionReceiver.EXTRA_LUNCH_STATUS, "YES");
            yesIntent.putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId);
            PendingIntent yesPi = PendingIntent.getBroadcast(context, notifId * 10 + 1, yesIntent, pFlags);

            // Action 2: Not Yet
            Intent noIntent = new Intent(context, NotificationActionReceiver.class);
            noIntent.setAction(NotificationActionReceiver.ACTION_LUNCH_RESPONSE);
            noIntent.putExtra(NotificationActionReceiver.EXTRA_LUNCH_STATUS, "NO");
            noIntent.putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId);
            PendingIntent noPi = PendingIntent.getBroadcast(context, notifId * 10 + 2, noIntent, pFlags);

            Uri soundUri = getCustomSoundUri(context);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_WELLNESS)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setColor(Color.parseColor("#F59E0B"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setSound(soundUri)
                .setVibrate(new long[]{0, 180, 80, 180})
                .addAction(R.drawable.ic_action_present, "Yes, Completed", yesPi)
                .addAction(R.drawable.ic_action_absent, "Not Yet", noPi);

            NotificationManagerCompat manager = NotificationManagerCompat.from(context);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    manager.notify(notifId, builder.build());
                }
            } else {
                manager.notify(notifId, builder.build());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Helper: Standard clean notification with vector icon
    private void showSimpleNotification(Context context, String channelId, int notifId, String title, String message, int iconRes, int color) {
        try {
            Intent mainIntent = new Intent(context, MainActivity.class);
            mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int pFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent contentIntent = PendingIntent.getActivity(context, notifId, mainIntent, pFlags);

            Uri soundUri = getCustomSoundUri(context);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(iconRes)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setColor(color)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setSound(soundUri)
                .setVibrate(new long[]{0, 150, 100, 150});

            NotificationManagerCompat manager = NotificationManagerCompat.from(context);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    manager.notify(notifId, builder.build());
                }
            } else {
                manager.notify(notifId, builder.build());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isConfiguredWeekOff(Calendar cal, JSONArray weekOffDays, JSONObject record) {
        if (record != null && "WO".equalsIgnoreCase(record.optString("status"))) {
            return true;
        }
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1; // 0 = Sunday, 6 = Saturday
        if (weekOffDays != null) {
            for (int i = 0; i < weekOffDays.length(); i++) {
                if (weekOffDays.optInt(i, -1) == dayOfWeek) {
                    return true;
                }
            }
        } else {
            // Default Sunday (0)
            return dayOfWeek == 0;
        }
        return false;
    }

    private void rescheduleNextAlarm(Context context, String alarmType) {
        if (NotificationScheduler.TYPE_MORNING_SHIFT.equals(alarmType)) {
            int startH = 9;
            int startM = 0;
            try {
                String wpStr = context.getSharedPreferences("mrnodeman_native_store", Context.MODE_PRIVATE).getString("_mnm_work_profile", null);
                if (wpStr != null) {
                    String s = new JSONObject(wpStr).optString("shiftStart", "09:00");
                    if (s.contains(":")) {
                        startH = Integer.parseInt(s.split(":")[0].trim());
                        startM = Integer.parseInt(s.split(":")[1].trim());
                    }
                }
            } catch (Exception ignored) {}
            NotificationScheduler.scheduleDailyAlarm(context, NotificationScheduler.REQ_MORNING_SHIFT, startH, startM, alarmType);
        } else if (NotificationScheduler.TYPE_ATTENDANCE_FOLLOWUP.equals(alarmType)) {
            NotificationScheduler.scheduleDailyAlarm(context, NotificationScheduler.REQ_ATTENDANCE_FOLLOWUP, 10, 45, alarmType);
        } else if (NotificationScheduler.TYPE_LUNCH_REMINDER.equals(alarmType)) {
            SharedPreferences p = context.getSharedPreferences("mrnodeman_native_store", Context.MODE_PRIVATE);
            int lh = p.getInt("_mnm_learned_lunch_hour", 13);
            int lm = p.getInt("_mnm_learned_lunch_minute", 0);
            NotificationScheduler.scheduleDailyAlarm(context, NotificationScheduler.REQ_LUNCH_REMINDER, lh, lm, alarmType);
        } else if (NotificationScheduler.TYPE_SHIFT_END.equals(alarmType)) {
            int endH = 18;
            int endM = 0;
            try {
                String wpStr = context.getSharedPreferences("mrnodeman_native_store", Context.MODE_PRIVATE).getString("_mnm_work_profile", null);
                if (wpStr != null) {
                    String s = new JSONObject(wpStr).optString("shiftEnd", "18:00");
                    if (s.contains(":")) {
                        endH = Integer.parseInt(s.split(":")[0].trim());
                        endM = Integer.parseInt(s.split(":")[1].trim());
                    }
                }
            } catch (Exception ignored) {}
            NotificationScheduler.scheduleDailyAlarm(context, NotificationScheduler.REQ_SHIFT_END, endH, endM, alarmType);
        } else if (NotificationScheduler.TYPE_EVENING_PENDING.equals(alarmType)) {
            NotificationScheduler.scheduleDailyAlarm(context, NotificationScheduler.REQ_EVENING_PENDING, 19, 30, alarmType);
        } else if (NotificationScheduler.TYPE_ADVANCE_ROSTER.equals(alarmType)) {
            NotificationScheduler.scheduleDailyAlarm(context, NotificationScheduler.REQ_ADVANCE_ROSTER, 20, 0, alarmType);
        } else if (NotificationScheduler.TYPE_SALARY_DAY.equals(alarmType)) {
            NotificationScheduler.scheduleDailyAlarm(context, NotificationScheduler.REQ_SALARY_DAY, 9, 30, alarmType);
        } else if (NotificationScheduler.TYPE_CLIENT_DUES.equals(alarmType)) {
            NotificationScheduler.scheduleDailyAlarm(context, NotificationScheduler.REQ_CLIENT_DUES, 11, 0, alarmType);
        } else if (NotificationScheduler.TYPE_BIRTHDAY_GREETING.equals(alarmType)) {
            NotificationScheduler.scheduleDailyAlarm(context, NotificationScheduler.REQ_BIRTHDAY_GREETING, 8, 30, alarmType);
        } else if (NotificationScheduler.TYPE_WORK_ANNIVERSARY.equals(alarmType)) {
            NotificationScheduler.scheduleDailyAlarm(context, NotificationScheduler.REQ_WORK_ANNIVERSARY, 9, 15, alarmType);
        } else if (NotificationScheduler.TYPE_SHIFT_STATS.equals(alarmType)) {
            NotificationScheduler.scheduleDailyAlarm(context, NotificationScheduler.REQ_SHIFT_STATS, 16, 30, alarmType);
        } else if (NotificationScheduler.TYPE_NIGHT_STREAK.equals(alarmType)) {
            NotificationScheduler.scheduleDailyAlarm(context, NotificationScheduler.REQ_NIGHT_STREAK, 21, 45, alarmType);
        }
    }

    // Helper: Build and post Shift End Notification with user's shift & monthly stats
    public static void showShiftEndStatsNotification(Context context, String companyName, String workerName, String profileStr, JSONObject attendanceRecords, String entriesStr, SharedPreferences prefs) {
        if (context == null) return;
        try {
            ensureNotificationChannels(context);

            String displayName = (workerName != null && !workerName.trim().isEmpty() && !workerName.equalsIgnoreCase("Worker")) ? workerName : "Champion";
            String compName = (companyName != null && !companyName.trim().isEmpty()) ? companyName : "Workplace";

            Calendar nowCal = Calendar.getInstance();
            String monthPrefix = new SimpleDateFormat("yyyy-MM", Locale.US).format(nowCal.getTime());

            double monthlyEarned = 0;
            double monthlyHours = 0;
            int monthlyShifts = 0;
            double totalPendingDues = 0;
            int streak = prefs != null ? prefs.getInt("_mnm_current_streak", 0) : 0;
            double scheduledShiftHours = 8.0;

            if (profileStr != null) {
                try {
                    JSONObject wp = new JSONObject(profileStr);
                    scheduledShiftHours = wp.optDouble("shiftHours", 8.0);
                } catch (Exception ignored) {}
            }

            if (prefs != null) {
                String statsSummaryStr = prefs.getString("_mnm_stats_summary", null);
                if (statsSummaryStr != null) {
                    try {
                        JSONObject ss = new JSONObject(statsSummaryStr);
                        monthlyEarned = ss.optDouble("monthlyEarnings", 0);
                        monthlyHours = ss.optDouble("monthlyHours", 0);
                        monthlyShifts = ss.optInt("monthlyShifts", 0);
                        totalPendingDues = ss.optDouble("pendingDues", 0);
                        if (streak <= 0) streak = ss.optInt("streak", 0);
                    } catch (Exception ignored) {}
                }
            }

            // Fallback calculation for monthlyShifts from attendanceRecords
            if (monthlyShifts == 0 && attendanceRecords != null) {
                try {
                    java.util.Iterator<String> keys = attendanceRecords.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        if (k.startsWith(monthPrefix)) {
                            JSONObject obj = attendanceRecords.optJSONObject(k);
                            if (obj != null) {
                                String st = obj.optString("status", "").toUpperCase(Locale.US);
                                if ("P".equals(st) || "PRESENT".equals(st) || "HD".equals(st) || "HALF".equals(st)) {
                                    monthlyShifts++;
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }

            // Fallback for monthlyEarned from profile monthlySalary
            if (monthlyEarned == 0 && profileStr != null) {
                try {
                    JSONObject wp = new JSONObject(profileStr);
                    double mSal = wp.optDouble("monthlySalary", 0);
                    int workDays = wp.optInt("workDaysPerMonth", 26);
                    if (mSal > 0 && monthlyShifts > 0) {
                        monthlyEarned = (mSal / workDays) * monthlyShifts;
                    }
                } catch (Exception ignored) {}
            }

            String title = "Shift Complete, " + displayName + "! 🎉";
            String summaryText = "Shift completed at " + compName + " (" + monthlyShifts + " shifts logged this month).";

            StringBuilder sb = new StringBuilder();
            sb.append("Great work today! Shift is completed at ").append(compName).append(".\n\n");
            sb.append("⏱ Today's Shift: ").append(String.format(Locale.US, "%.1f", scheduledShiftHours)).append(" hrs logged\n");
            sb.append("📊 Monthly Shifts: ").append(monthlyShifts).append(" shifts completed\n");
            if (monthlyEarned > 0) {
                sb.append("💰 Monthly Earnings: Rs ").append(String.format(Locale.US, "%,.0f", monthlyEarned)).append("\n");
            }
            if (streak > 0) {
                sb.append("🔥 Active Streak: ").append(streak).append(" days on track\n");
            }
            if (totalPendingDues > 0) {
                sb.append("💳 Client Receivables: Rs ").append(String.format(Locale.US, "%,.0f", totalPendingDues)).append(" pending\n");
            }
            sb.append("\nRest well tonight and recharge for tomorrow!");

            String bigBody = sb.toString();

            Intent mainIntent = new Intent(context, MainActivity.class);
            mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int pFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent contentIntent = PendingIntent.getActivity(context, NOTIF_ID_SHIFT_END, mainIntent, pFlags);

            Uri soundUri = getCustomSoundUri(context);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_SHIFT_GREETINGS)
                .setSmallIcon(R.drawable.ic_stat_salary)
                .setContentTitle(title)
                .setContentText(summaryText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(bigBody))
                .setColor(Color.parseColor("#10B981"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setSound(soundUri)
                .setVibrate(new long[]{0, 200, 100, 200});

            NotificationManagerCompat manager = NotificationManagerCompat.from(context);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                    manager.notify(NOTIF_ID_SHIFT_END, builder.build());
                }
            } else {
                manager.notify(NOTIF_ID_SHIFT_END, builder.build());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void ensureNotificationChannels(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationManager manager = context.getSystemService(NotificationManager.class);
                if (manager != null) {
                    // Clean up legacy channels to force system to adopt new custom sound
                    try {
                        manager.deleteNotificationChannel("channel_attendance_shifts");
                        manager.deleteNotificationChannel("channel_salary_payouts");
                        manager.deleteNotificationChannel("channel_reminders_streaks");
                        manager.deleteNotificationChannel("channel_client_payments");
                        manager.deleteNotificationChannel("channel_wellness_breaks");
                        manager.deleteNotificationChannel("channel_shift_greetings");
                    } catch (Exception ignored) {}

                    Uri soundUri = getCustomSoundUri(context);
                    AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build();

                    NotificationChannel attChannel = new NotificationChannel(
                        CHANNEL_ATTENDANCE, "Attendance & Shifts", NotificationManager.IMPORTANCE_HIGH);
                    attChannel.setDescription("Shift reminders, 1-tap check-in buttons, and roster alerts");
                    attChannel.enableVibration(true);
                    attChannel.setSound(soundUri, audioAttributes);
                    manager.createNotificationChannel(attChannel);

                    NotificationChannel salChannel = new NotificationChannel(
                        CHANNEL_SALARY, "Salary & Payouts", NotificationManager.IMPORTANCE_HIGH);
                    salChannel.setDescription("Monthly salary credit day alerts and payslip notifications");
                    salChannel.enableVibration(true);
                    salChannel.setSound(soundUri, audioAttributes);
                    manager.createNotificationChannel(salChannel);

                    NotificationChannel remChannel = new NotificationChannel(
                        CHANNEL_REMINDERS, "Reminders & Streaks", NotificationManager.IMPORTANCE_HIGH);
                    remChannel.setDescription("Streak milestones and general activity reminders");
                    remChannel.enableVibration(true);
                    remChannel.setSound(soundUri, audioAttributes);
                    manager.createNotificationChannel(remChannel);

                    NotificationChannel payChannel = new NotificationChannel(
                        CHANNEL_PAYMENTS, "Client Invoices & Receivables", NotificationManager.IMPORTANCE_HIGH);
                    payChannel.setDescription("Outstanding balance and unpaid invoice reminders");
                    payChannel.enableVibration(true);
                    payChannel.setSound(soundUri, audioAttributes);
                    manager.createNotificationChannel(payChannel);

                    NotificationChannel welChannel = new NotificationChannel(
                        CHANNEL_WELLNESS, "Lunch, Breaks & Wellness", NotificationManager.IMPORTANCE_HIGH);
                    welChannel.setDescription("Lunch reminders with interactive Yes/No buttons, bio breaks, and hydration tips");
                    welChannel.enableVibration(true);
                    welChannel.setSound(soundUri, audioAttributes);
                    manager.createNotificationChannel(welChannel);

                    NotificationChannel endChannel = new NotificationChannel(
                        CHANNEL_SHIFT_GREETINGS, "Shift Wrap-up & Motivation", NotificationManager.IMPORTANCE_HIGH);
                    endChannel.setDescription("Warm greetings, congratulations, and motivational thoughts upon shift completion");
                    endChannel.enableVibration(true);
                    endChannel.setSound(soundUri, audioAttributes);
                    manager.createNotificationChannel(endChannel);
                }
            } catch (Exception ignored) {}
        }
    }
}
