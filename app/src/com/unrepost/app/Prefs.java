package com.unrepost.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Setelan & statistik ringan (SharedPreferences). */
public final class Prefs {

    private static final String FILE = "unrepost";

    public static final String KEY_ENABLED = "filter_enabled";
    public static final String KEY_AUTO_SKIP = "auto_skip";
    public static final String KEY_THRESHOLD = "threshold";
    public static final String KEY_TOTAL_SEEN = "total_seen";
    public static final String KEY_TOTAL_SKIPPED = "total_skipped";
    public static final String KEY_TODAY_DATE = "today_date";
    public static final String KEY_TODAY_SEEN = "today_seen";
    public static final String KEY_TODAY_SKIPPED = "today_skipped";

    private Prefs() {
    }

    public static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static boolean filterEnabled(Context c) {
        return get(c).getBoolean(KEY_ENABLED, false);
    }

    public static boolean autoSkip(Context c) {
        return get(c).getBoolean(KEY_AUTO_SKIP, false);
    }

    public static int threshold(Context c) {
        return get(c).getInt(KEY_THRESHOLD, RepostDetector.THRESHOLD_NORMAL);
    }

    public static int sensitivityLevel(Context c) {
        int t = threshold(c);
        if (t <= RepostDetector.THRESHOLD_HIGH_SENSITIVITY) {
            return 2;
        }
        if (t >= RepostDetector.THRESHOLD_CONSERVATIVE) {
            return 0;
        }
        return 1;
    }

    public static int thresholdForLevel(int level) {
        if (level >= 2) {
            return RepostDetector.THRESHOLD_HIGH_SENSITIVITY;
        }
        if (level <= 0) {
            return RepostDetector.THRESHOLD_CONSERVATIVE;
        }
        return RepostDetector.THRESHOLD_NORMAL;
    }

    public static String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    public static int todaySeen(Context c) {
        SharedPreferences p = get(c);
        return todayKey().equals(p.getString(KEY_TODAY_DATE, "")) ? p.getInt(KEY_TODAY_SEEN, 0) : 0;
    }

    public static int todaySkipped(Context c) {
        SharedPreferences p = get(c);
        return todayKey().equals(p.getString(KEY_TODAY_DATE, "")) ? p.getInt(KEY_TODAY_SKIPPED, 0) : 0;
    }

    /** @return true jika ini penandaan pertama untuk sidik jari tsb. */
    public static synchronized boolean noteDetected(Context c, String signature, String title) {
        SharedPreferences p = get(c);
        boolean added = RepostLog.add(c, signature, title);
        if (added) {
            ensureToday(p);
            p.edit()
                    .putInt(KEY_TOTAL_SEEN, p.getInt(KEY_TOTAL_SEEN, 0) + 1)
                    .putInt(KEY_TODAY_SEEN, p.getInt(KEY_TODAY_SEEN, 0) + 1)
                    .apply();
        }
        return added;
    }

    public static synchronized void noteSkipped(Context c) {
        SharedPreferences p = get(c);
        ensureToday(p);
        p.edit()
                .putInt(KEY_TOTAL_SKIPPED, p.getInt(KEY_TOTAL_SKIPPED, 0) + 1)
                .putInt(KEY_TODAY_SKIPPED, p.getInt(KEY_TODAY_SKIPPED, 0) + 1)
                .apply();
    }

    private static void ensureToday(SharedPreferences p) {
        String key = todayKey();
        if (!key.equals(p.getString(KEY_TODAY_DATE, ""))) {
            p.edit()
                    .putString(KEY_TODAY_DATE, key)
                    .putInt(KEY_TODAY_SEEN, 0)
                    .putInt(KEY_TODAY_SKIPPED, 0)
                    .apply();
        }
    }

    public static void resetStats(Context c) {
        get(c).edit()
                .putInt(KEY_TOTAL_SEEN, 0)
                .putInt(KEY_TOTAL_SKIPPED, 0)
                .putString(KEY_TODAY_DATE, todayKey())
                .putInt(KEY_TODAY_SEEN, 0)
                .putInt(KEY_TODAY_SKIPPED, 0)
                .apply();
    }
}
