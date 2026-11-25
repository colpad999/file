package com.unrepost.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Riwayat repost yang pernah terdeteksi (FIFO, maksimal 300 entri). */
public final class RepostLog {

    private static final String KEY = "log_entries";
    private static final int MAX = 300;
    private static final char SEP = '\u001f';

    private RepostLog() {
    }

    private static SharedPreferences p(Context c) {
        return c.getApplicationContext().getSharedPreferences("unrepost_log", Context.MODE_PRIVATE);
    }

    /** @return false jika sidik jari sudah tercatat sebelumnya. */
    public static synchronized boolean add(Context c, String signature, String title) {
        if (signature == null || signature.isEmpty()) {
            return false;
        }
        List<String> entries = rawList(c);
        for (String e : entries) {
            if (e.startsWith(signature + SEP)) {
                return false;
            }
        }
        String clean = title == null ? "" : title.replace(SEP, ' ').replace('\n', ' ');
        entries.add(signature + SEP + System.currentTimeMillis() + SEP + clean);
        while (entries.size() > MAX) {
            entries.remove(0);
        }
        save(c, entries);
        return true;
    }

    /** Urutan terbaru lebih dulu. */
    public static synchronized List<Entry> list(Context c) {
        List<String> entries = rawList(c);
        List<Entry> out = new ArrayList<>();
        for (int i = entries.size() - 1; i >= 0; i--) {
            String[] parts = entries.get(i).split(String.valueOf(SEP), -1);
            if (parts.length >= 3) {
                Entry e = new Entry();
                e.signature = parts[0];
                try {
                    e.time = Long.parseLong(parts[1]);
                } catch (NumberFormatException ignored) {
                    e.time = 0L;
                }
                e.title = parts[2];
                out.add(e);
            }
        }
        return out;
    }

    public static synchronized void clear(Context c) {
        p(c).edit().remove(KEY).apply();
    }

    private static List<String> rawList(Context c) {
        String raw = p(c).getString(KEY, "");
        List<String> out = new ArrayList<>();
        if (raw != null && !raw.isEmpty()) {
            Collections.addAll(out, raw.split("\n"));
        }
        return out;
    }

    private static void save(Context c, List<String> entries) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(entries.get(i));
        }
        p(c).edit().putString(KEY, sb.toString()).apply();
    }

    public static final class Entry {
        public String signature;
        public long time;
        public String title;
    }
}
