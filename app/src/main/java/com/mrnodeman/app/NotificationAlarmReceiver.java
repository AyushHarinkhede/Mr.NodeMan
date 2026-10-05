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
                    "Today is your Week Off! 🌴",
                    "Good morning! Relax and recharge on your scheduled Week Off today. Have a peaceful day!",
                    R.drawable.ic_stat_attendance, Color.parseColor("#10B981"));
            } else if (isHoliday) {
                String holNote = todayRec.optString("note", "Official Holiday");
                showSimpleNotification(context, CHANNEL_ATTENDANCE, NOTIF_ID_MORNING,
                    "Happy Holiday! 🎉",
                    "Today is scheduled as a Holiday (" + holNote + "). Enjoy your special break!",
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
                        "Attendance Reminder (Follow-up) ⏰",
                        "Gentle reminder: You haven't checked in for today at " + companyName + " yet. Quick tap below to log Present!",
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
            String[] lunchMsg = getLunchReminder(daySeed);
            showLunchActionNotification(context, NOTIF_ID_LUNCH, lunchMsg[0], lunchMsg[1]);
        }

        // ══════════════════════════════════════════════════════
        // 4. BIO BREAK, POSTURE & HYDRATION WELLNESS REMINDERS
        // ══════════════════════════════════════════════════════
        else if (NotificationScheduler.TYPE_BIO_WELLNESS_BREAK.equals(alarmType) && wellnessBreaks) {
            boolean isAfternoon = nowCal.get(Calendar.HOUR_OF_DAY) >= 14;
            String[] wellnessMsg = getWellnessBreak(daySeed, isAfternoon);
            showWellnessNotification(context, NOTIF_ID_WELLNESS, wellnessMsg[0], wellnessMsg[1]);
        }

        // ══════════════════════════════════════════════════════
        // 5. SHIFT END WRAP-UP & DAILY MOTIVATION GREETINGS
        // Triggered at worker's shift end time (e.g. 06:00 PM)
        // ══════════════════════════════════════════════════════
        else if (NotificationScheduler.TYPE_SHIFT_END.equals(alarmType) && shiftEndGreeting) {
            String[] endMsg = getShiftEndMotivation(daySeed, workerName, companyName);
            showSimpleNotification(context, CHANNEL_SHIFT_GREETINGS, NOTIF_ID_SHIFT_END,
                endMsg[0], endMsg[1], R.drawable.ic_stat_notification, Color.parseColor("#7C6FED"));
        }

        // ══════════════════════════════════════════════════════
        // 6. EVENING PENDING ATTENDANCE & STREAK SAVIOR (07:30 PM)
        // Only triggered if still unlogged in evening
        // ══════════════════════════════════════════════════════
        else if (NotificationScheduler.TYPE_EVENING_PENDING.equals(alarmType) && eveningPending) {
            if (!hasMarkedAttendance) {
                showAttendanceActionNotification(context, NOTIF_ID_EVENING,
                    "Attendance Pending for Today ⚠️",
                    "Protect your daily streak! You have not logged your attendance for today at " + companyName + " yet.",
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
                    "Tomorrow is Week Off! 🌴",
                    "Enjoy your scheduled Week Off tomorrow from " + companyName + ". Have a restful evening!",
                    R.drawable.ic_stat_attendance, Color.parseColor("#10B981"));
            } else if (isTomHoliday) {
                String note = tomRec != null ? tomRec.optString("note", "Official Holiday") : "Official Holiday";
                showSimpleNotification(context, CHANNEL_ATTENDANCE, NOTIF_ID_ADVANCE,
                    "Tomorrow is Holiday! 🎉",
                    "Tomorrow is scheduled as a Holiday (" + note + "). Enjoy your break!",
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
                    "Salary Day Today! 💰",
                    "Today is your scheduled monthly salary credit date from " + companyName + ". Don't forget to review your payslip!",
                    R.drawable.ic_stat_salary, Color.parseColor("#7C6FED"));
            } else if (tomorrowDayOfMonth == salaryPayDay) {
                showSimpleNotification(context, CHANNEL_SALARY, NOTIF_ID_SALARY,
                    "Salary Tomorrow! 🔔",
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
                            "Client Receivables Pending 💼",
                            "You have ₹" + amtFormatted + " outstanding pending across " + pendingCount + " client sessions. Review your invoices to collect dues.",
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
                            "Happy Birthday, " + displayName + "! 🎂🎉",
                            "Wishing you a very Happy Birthday! May your day be filled with happiness, good health, and immense success. Keep shining!",
                            R.drawable.ic_stat_notification, Color.parseColor("#EC4899"));
                    }
                    // Birthday TOMORROW (Advance Birthday Wish)
                    else if (tomMonth == bMonth && tomDay == bDay) {
                        showSimpleNotification(context, CHANNEL_REMINDERS, NOTIF_ID_BIRTHDAY,
                            "Advance Birthday Wishes! 🎈✨",
                            "Kal aapka birthday hai, " + displayName + "! Mr.NodeMan team ki taraf se advance me bohot saari shubhkamnayein! Have a fantastic day ahead!",
                            R.drawable.ic_stat_notification, Color.parseColor("#8B5CF6"));
                    }
                } catch (Exception ignored) {}
            }
        }

        // ══════════════════════════════════════════════════════
        // 11. WORK TENURE & ANNIVERSARY GREETINGS (09:15 AM)
        // Checks joining date (DOJ): notifies on complete years and on 1st of every month
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

                    // Compute total full months elapsed
                    int totalMonths = (curYear - jYear) * 12 + (curMonth - jMonth);
                    if (curDay < jDay) {
                        totalMonths--;
                    }

                    if (totalMonths >= 1) {
                        int fullYears = totalMonths / 12;
                        int remMonths = totalMonths % 12;

                        String tenureStr = "";
                        if (fullYears > 0 && remMonths > 0) {
                            tenureStr = fullYears + " year" + (fullYears > 1 ? "s" : "") + " " + remMonths + " month" + (remMonths > 1 ? "s" : "");
                        } else if (fullYears > 0) {
                            tenureStr = fullYears + " year" + (fullYears > 1 ? "s" : "");
                        } else {
                            tenureStr = remMonths + " month" + (remMonths > 1 ? "s" : "");
                        }

                        // Case A: Work Anniversary (Completed Exact Year(s) Today)
                        if (curMonth == jMonth && curDay == jDay && fullYears >= 1) {
                            showSimpleNotification(context, CHANNEL_SHIFT_GREETINGS, NOTIF_ID_ANNIVERSARY,
                                "Happy Work Anniversary! 🏆🎊",
                                "Congratulations! Aaj aapko " + companyName + " me kaam karte hue pure " + fullYears + " saal ho gaye hain (" + fullYears + " Year" + (fullYears > 1 ? "s" : "") + " completed). Aapki mehnat aur dedication ko salute!",
                                R.drawable.ic_stat_notification, Color.parseColor("#10B981"));
                        }
                        // Case B: 1st Day of Month Milestone Update
                        else if (curDay == 1) {
                            showSimpleNotification(context, CHANNEL_SHIFT_GREETINGS, NOTIF_ID_ANNIVERSARY,
                                "Monthly Work Journey Milestone 💼✨",
                                "Naye mahine ki shubh shuruat! Aapko " + companyName + " me kaam karte hue kul " + tenureStr + " ho chuke hain. Keep achieving new milestones!",
                                R.drawable.ic_stat_notification, Color.parseColor("#7C6FED"));
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    // DYNAMIC DAILY UNIQUE NOTIFICATION MESSAGE POOLS
    // ══════════════════════════════════════════════════════════

    private String[] getMorningGreeting(int seed, String company) {
        String[][] pool = new String[][] {
            { "Shubh Prabhat! ☀️ Morning Check-in", "Naya din, nayi energy! Remember to mark your attendance for today at " + company + "." },
            { "Good Morning Champion! 🚀", "Make today count! Ek tap me apni attendance mark karein at " + company + "." },
            { "Rise & Shine! ☕ Daily Check-in", "Success starts with showing up! Record your attendance for today at " + company + "." },
            { "Morning Hustle! 💼 Ready for Today?", "Another opportunity to build your future at " + company + ". Tap below to log Present." },
            { "Shandaar Shuruat! 🌟 Check-in Active", "Focus on your goals today! Don't forget to mark your daily attendance at " + company + "." }
        };
        return pool[Math.abs(seed) % pool.length];
    }

    private String[] getLunchReminder(int seed) {
        String[][] pool = new String[][] {
            { "Lunch Break Time! 🍱", "Dopahar ho gayi! Kaam thodi der roko, lunch kiya kya? Tap below to record your lunch break." },
            { "Energy Refuel Time! 🥪", "Pet pooja pehle, kaam baad me! Did you have your lunch yet? Share your status below." },
            { "Lunch Kiya Kya? 🍛", "Working non-stop drains focus! Take a 20-minute relaxing lunch break. Kar liya kya?" },
            { "Healthy Meal Reminder 🥗", "Nutrition keeps your energy high! Have you eaten your lunch today? Tap Haan ya Abhi Nahi." },
            { "Bhookh Lagi Hogi! 🍲", "Mid-day battery low? Ek healthy meal lo aur energize ho jao. Did you finish lunch?" }
        };
        return pool[Math.abs(seed) % pool.length];
    }

    private String[] getWellnessBreak(int seed, boolean isAfternoon) {
        if (!isAfternoon) {
            String[][] morningPool = new String[][] {
                { "Pani Piya Kya? 🚰 Hydration Check", "Body dehydrate hone se tiredness aati hai. Abhi ek bada glass fresh pani pi lo to stay active!" },
                { "Posture Check! 🧘 Spine Straight", "Seedhe baitho! Roll your shoulders back, stretch your spine, and relieve neck tension." },
                { "Deep Breathing Reset! 🌬️", "Take 3 slow, deep belly breaths. Exhale all stress. A calm mind works 2x better!" }
            };
            return morningPool[Math.abs(seed) % morningPool.length];
        } else {
            String[][] afternoonPool = new String[][] {
                { "20-20-20 Eye Relaxation! 👀", "Screen ya machine se nazrein hatao: 20 feet door kisi cheez ko 20 seconds dekho. Eyes refresh ho jayengi!" },
                { "Movement & Walk Break! 🚶", "Ek 2-minute stroll lo, deep breath lo. Blood circulation badhega aur focus sharp hoga!" },
                { "Healthy Snack & Stretch! 🍎", "Quick break: Thoda stretch karo, aur chai ke sath healthy dry fruits ya water intake badhao." }
            };
            return afternoonPool[Math.abs(seed) % afternoonPool.length];
        }
    }

    private String[] getShiftEndMotivation(int seed, String workerName, String company) {
        String[][] pool = new String[][] {
            { "Shift Wrap-up! 🎉 Shandar Kaam", "Aaj ki shift successfully complete hui at " + company + "! Put your tools down, aapne khoob mehnat ki. Have a peaceful evening! ☕" },
            { "Work Done! 🌟 Take a Bow", "Great job today, " + workerName + "! Mehnat ka fal hamesha meetha hota hai. Relax karo aur family ke sath achha time spend karo. 🏡" },
            { "Shift Completed! 🚀 Great Energy", "Today's workday at " + company + " is done. Give yourself credit for showing up and giving your 100%. Rest well tonight!" },
            { "Mission Accomplished! 👏", "Duty khatam, chilling shuru! You crushed your shift targets today. Enjoy a delicious dinner and restful sleep." },
            { "Shift Over! 🌙 Time to Unwind", "Aaj ka kaam pura hua! Work-life balance is key — switch off from work mode and recharge for tomorrow." },
            { "Salute to Your Dedication! 💼", "Hard work builds the future! Aaj ka din productive raha at " + company + ". Have a warm, relaxing evening!" },
            { "End of Shift! ☕ Time to Relax", "A great day of honest work at " + company + ". Drop the stress, take a deep breath, and enjoy your evening!" }
        };
        return pool[Math.abs(seed) % pool.length];
    }

    // ══════════════════════════════════════════════════════════
    // NOTIFICATION BUILDERS
    // ══════════════════════════════════════════════════════════

    // Helper: Build and post rich notification with direct 1-Tap Attendance Action buttons
    private void showAttendanceActionNotification(Context context, int notifId, String title, String message, String dateISO, String company) {
        try {
            Intent mainIntent = new Intent(context, MainActivity.class);
            mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int pFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent contentIntent = PendingIntent.getActivity(context, notifId, mainIntent, pFlags);

            // Action 1: Mark Present
            Intent presentIntent = new Intent(context, NotificationActionReceiver.class);
            presentIntent.setAction(NotificationActionReceiver.ACTION_MARK_ATTENDANCE);
            presentIntent.putExtra(NotificationActionReceiver.EXTRA_STATUS, "P");
            presentIntent.putExtra(NotificationActionReceiver.EXTRA_DATE, dateISO);
            presentIntent.putExtra(NotificationActionReceiver.EXTRA_COMPANY, company);
            presentIntent.putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId);
            PendingIntent presentPi = PendingIntent.getBroadcast(context, notifId * 10 + 1, presentIntent, pFlags);

            // Action 2: Absent (Launches interactive reason question: SL, PL, Absent)
            Intent absentIntent = new Intent(context, NotificationActionReceiver.class);
            absentIntent.setAction(NotificationActionReceiver.ACTION_PROMPT_ABSENT);
            absentIntent.putExtra(NotificationActionReceiver.EXTRA_DATE, dateISO);
            absentIntent.putExtra(NotificationActionReceiver.EXTRA_COMPANY, company);
            absentIntent.putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId);
            PendingIntent absentPi = PendingIntent.getBroadcast(context, notifId * 10 + 2, absentIntent, pFlags);

            // Action 3: Mark Week Off (WO)
            Intent woIntent = new Intent(context, NotificationActionReceiver.class);
            woIntent.setAction(NotificationActionReceiver.ACTION_MARK_ATTENDANCE);
            woIntent.putExtra(NotificationActionReceiver.EXTRA_STATUS, "WO");
            woIntent.putExtra(NotificationActionReceiver.EXTRA_DATE, dateISO);
            woIntent.putExtra(NotificationActionReceiver.EXTRA_COMPANY, company);
            woIntent.putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId);
            PendingIntent woPi = PendingIntent.getBroadcast(context, notifId * 10 + 3, woIntent, pFlags);

            Uri soundUri = getCustomSoundUri(context);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ATTENDANCE)
                .setSmallIcon(R.drawable.ic_stat_attendance)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setColor(Color.parseColor("#36DFAF"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setSound(soundUri)
                .setVibrate(new long[]{0, 200, 100, 200})
                .addAction(R.drawable.ic_action_present, "Present 🎉", presentPi)
                .addAction(R.drawable.ic_action_absent, "Absent ❌", absentPi)
                .addAction(R.drawable.ic_action_halfday, "Week Off 🌴", woPi);

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

    // Helper: Build and post interactive lunch notification with [Haan, Kar Liya] and [Abhi Nahi]
    private void showLunchActionNotification(Context context, int notifId, String title, String message) {
        try {
            Intent mainIntent = new Intent(context, MainActivity.class);
            mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int pFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent contentIntent = PendingIntent.getActivity(context, notifId, mainIntent, pFlags);

            // Action 1: Haan, Kar Liya (Yes)
            Intent yesIntent = new Intent(context, NotificationActionReceiver.class);
            yesIntent.setAction(NotificationActionReceiver.ACTION_LUNCH_RESPONSE);
            yesIntent.putExtra(NotificationActionReceiver.EXTRA_LUNCH_STATUS, "YES");
            yesIntent.putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId);
            PendingIntent yesPi = PendingIntent.getBroadcast(context, notifId * 10 + 1, yesIntent, pFlags);

            // Action 2: Abhi Nahi (No)
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
                .addAction(R.drawable.ic_action_present, "Haan, Kar Liya 🍱", yesPi)
                .addAction(R.drawable.ic_action_absent, "Abhi Nahi ⏳", noPi);

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

    // Helper: Build and post bio break / health wellness tip notification
    private void showWellnessNotification(Context context, int notifId, String title, String message) {
        try {
            Intent mainIntent = new Intent(context, MainActivity.class);
            mainIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            int pFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent contentIntent = PendingIntent.getActivity(context, notifId, mainIntent, pFlags);

            Intent ackIntent = new Intent(context, NotificationActionReceiver.class);
            ackIntent.setAction(NotificationActionReceiver.ACTION_WELLNESS_ACK);
            ackIntent.putExtra(NotificationActionReceiver.EXTRA_NOTIF_ID, notifId);
            PendingIntent ackPi = PendingIntent.getBroadcast(context, notifId * 10 + 1, ackIntent, pFlags);

            Uri soundUri = getCustomSoundUri(context);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_WELLNESS)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setColor(Color.parseColor("#06B6D4"))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setSound(soundUri)
                .setVibrate(new long[]{0, 150, 100, 150})
                .addAction(R.drawable.ic_action_present, "Done! 💧", ackPi);

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
            NotificationScheduler.scheduleDailyAlarm(context, NotificationScheduler.REQ_MORNING_SHIFT, 9, 0, alarmType);
        } else if (NotificationScheduler.TYPE_ATTENDANCE_FOLLOWUP.equals(alarmType)) {
            NotificationScheduler.scheduleDailyAlarm(context, NotificationScheduler.REQ_ATTENDANCE_FOLLOWUP, 10, 45, alarmType);
        } else if (NotificationScheduler.TYPE_LUNCH_REMINDER.equals(alarmType)) {
            SharedPreferences p = context.getSharedPreferences("mrnodeman_native_store", Context.MODE_PRIVATE);
            int lh = p.getInt("_mnm_learned_lunch_hour", 13);
            int lm = p.getInt("_mnm_learned_lunch_minute", 0);
            NotificationScheduler.scheduleDailyAlarm(context, NotificationScheduler.REQ_LUNCH_REMINDER, lh, lm, alarmType);
        } else if (NotificationScheduler.TYPE_SHIFT_END.equals(alarmType)) {
            SharedPreferences p = context.getSharedPreferences("mrnodeman_native_store", Context.MODE_PRIVATE);
            String wpStr = p.getString("_mnm_work_profile", null);
            int seh = 18, sem = 0;
            if (wpStr != null) {
                try {
                    String se = new JSONObject(wpStr).optString("shiftEnd", "18:00");
                    if (se != null && se.contains(":")) {
                        seh = Integer.parseInt(se.split(":")[0].trim());
                        sem = Integer.parseInt(se.split(":")[1].trim());
                    }
                } catch (Exception ignored) {}
            }
            NotificationScheduler.scheduleDailyAlarm(context, NotificationScheduler.REQ_SHIFT_END, seh, sem, alarmType);
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
        }
    }

    private void ensureNotificationChannels(Context context) {
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
