package com.unrepost.app;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Status proses "Bersihkan Repost" yang bisa dibaca Activity walau layanan
 * berjalan di proses/layar lain: sedang jalan atau tidak, progres, dan log.
 */
public final class CleanerState {

    private static final String FILE = "unrepost_cleaner";
    public static final String KEY_RUNNING = "running";
    public static final String KEY_ABORTED = "aborted";
    public static final String KEY_PROGRESS = "progress";
    public static final String KEY_DELETED = "deleted";
    public static final String KEY_TARGET = "target";
    public static final String KEY_DRY = "dry";
    public static final String KEY_FINISHED_AT = "finished_at";
    public static final String KEY_LOG = "log";

    private CleanerState() {
    }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static void markStarting(Context c, int maxDeletes, int gapMs, boolean dryRun) {
        p(c).edit()
                .putBoolean(KEY_RUNNING, true)
                .putBoolean(KEY_ABORTED, false)
                .putInt(KEY_TARGET, maxDeletes)
                .putInt(KEY_DELETED, 0)
                .putBoolean(KEY_DRY, dryRun)
                .putString(KEY_PROGRESS, (dryRun ? "LATIHAN" : "HAPUS") + " 0/" + maxDeletes)
                .putString(KEY_LOG, "")
                .putLong(KEY_FINISHED_AT, 0L)
                .putString("gap", String.valueOf(gapMs))
                .apply();
    }

    public static void finish(Context c, CleanerEngine engine, boolean aborted) {
        SharedPreferences sp = p(c);
        SharedPreferences.Editor e = sp.edit()
                .putBoolean(KEY_RUNNING, false)
                .putBoolean(KEY_ABORTED, aborted)
                .putLong(KEY_FINISHED_AT, System.currentTimeMillis());
        if (engine != null) {
            e.putInt(KEY_DELETED, engine.deleted())
                    .putString(KEY_PROGRESS, engine.progressText());
            StringBuilder sb = new StringBuilder();
            for (String line : engine.logLines()) {
                sb.append(line).append('\n');
            }
            e.putString(KEY_LOG, sb.toString());
        }
        e.apply();
    }

    /** Dipanggil berkala oleh layanan agar Activity bisa menampilkan progres. */
    public static void updateProgress(Context c, int deleted, String progress) {
        p(c).edit()
                .putBoolean(KEY_RUNNING, true)
                .putInt(KEY_DELETED, deleted)
                .putString(KEY_PROGRESS, progress)
                .apply();
    }

    public static boolean isRunning(Context c) {
        return p(c).getBoolean(KEY_RUNNING, false);
    }

    public static boolean wasAborted(Context c) {
        return p(c).getBoolean(KEY_ABORTED, false);
    }

    public static int deleted(Context c) {
        return p(c).getInt(KEY_DELETED, 0);
    }

    public static int target(Context c) {
        return p(c).getInt(KEY_TARGET, 0);
    }

    public static boolean dryRun(Context c) {
        return p(c).getBoolean(KEY_DRY, false);
    }

    public static String progress(Context c) {
        return p(c).getString(KEY_PROGRESS, "");
    }

    public static String log(Context c) {
        return p(c).getString(KEY_LOG, "");
    }

    public static long finishedAt(Context c) {
        return p(c).getLong(KEY_FINISHED_AT, 0L);
    }

    public static int gap(Context c) {
        try {
            return Integer.parseInt(p(c).getString("gap", "8000"));
        } catch (Exception e) {
            return 8000;
        }
    }
}
