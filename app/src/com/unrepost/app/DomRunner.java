package com.unrepost.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebView;

import org.json.JSONObject;

import java.util.Locale;

/**
 * Menjalankan {@link DomEngine} di dalam WebView TikTok yang asli.
 *
 * <p>Runner ini menyuntikkan {@code domsnap.js} ke setiap halaman, menerima snapshot
 * DOM (lewat jembatan {@code UnrepostDom}), lalu menjalankan perintah yang diminta
 * mesin: buka halaman repost, ketuk elemen (klik biasa — seperti tangan pengguna),
 * buka menu, gulir, kembali.</p>
 *
 * <p>Semua eksekusi JS dilakukan di thread UI dan dibungkus try/catch, supaya
 * WebView yang dimusnahkan tidak mematikan aplikasi.</p>
 */
public final class DomRunner {

    private static final String TAG = "UnrepostDom";
    private static final String BRIDGE = "UnrepostDom";
    private static final long TICK_MS = 900L;

    /** Kontrak dengan Activity pemilik WebView. */
    public interface Host {
        /** Jalankan JS di thread UI; hasil dikirim ke callback (boleh null). */
        void runJs(String script, ValueCallback<String> callback);

        /** Buka URL di WebView TikTok. */
        void openUrl(String url);

        /** Apakah halaman sedang memuat. */
        boolean isLoading();

        /** Tampilkan progres di UI aplikasi. */
        void onProgress(String text, String note, int deleted, boolean finished, boolean aborted);

        /** Log satu baris untuk UI/log. */
        void onLog(String line);
    }

    private final Context context;
    private final Host host;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final DomEngine engine;
    private volatile String lastHtml = "";
    private volatile String lastUrl = "";
    private volatile long lastSnapAt;
    private volatile int snapCount;
    /** Mode kalibrasi: jangan menekan apa pun, hanya laporkan apa yang terlihat. */
    private volatile boolean calibrate;
    /** Label/aksi yang tidak ditemukan (untuk diagnosa). */
    private final java.util.List<String> missing = new java.util.ArrayList<>();
    private boolean ticking;
    private boolean stopped;
    private int killSwitch = 0;

    public DomRunner(Context context, Host host, int maxDeletes, long gapMs, boolean dryRun,
                     String username) {
        this.context = context.getApplicationContext();
        this.host = host;
        this.engine = new DomEngine(maxDeletes, gapMs, dryRun);
        this.engine.setUsername(username);
    }

    public DomEngine engine() {
        return engine;
    }

    public void setCalibrate(boolean on) {
        calibrate = on;
    }

    public int snapCount() {
        return snapCount;
    }

    public String missingReport() {
        synchronized (missing) {
            StringBuilder sb = new StringBuilder();
            for (String m : missing) {
                sb.append(m).append("\n");
            }
            return sb.toString();
        }
    }

    private void noteMissing(String what) {
        synchronized (missing) {
            if (missing.size() < 40 && !missing.contains(what)) {
                missing.add(what);
            }
        }
    }

    // --------------------------------------------------------------- jembatan

    /** Objek yang diekspos ke halaman TikTok sebagai {@code window.UnrepostDom}. */
    public final class Bridge {

        @JavascriptInterface
        public void snap(final String payload) {
            if (payload == null || payload.length() < 20) {
                return;
            }
            try {
                JSONObject o = new JSONObject(payload);
                lastHtml = o.optString("html", "");
                lastUrl = o.optString("url", "");
                lastSnapAt = System.currentTimeMillis();
                snapCount++;
            } catch (Exception e) {
                Log.w(TAG, "snap tidak valid", e);
            }
        }

        @JavascriptInterface
        public String stamp() {
            return "unrepost-1";
        }
    }

    /** Skrip yang disuntikkan setiap halaman selesai dimuat. */
    public static String bootstrapScript(Context ctx) {
        try {
            java.io.InputStream is = ctx.getAssets().open("web/domsnap.js");
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            is.close();
            return new String(bos.toByteArray(), "UTF-8")
                    + "\n;try{window.UnrepostSnap&&window.UnrepostSnap.snapNow();}catch(e){}";
        } catch (Exception e) {
            Log.e(TAG, "gagal membaca domsnap.js", e);
            return "/* domsnap tidak tersedia */";
        }
    }

    // ------------------------------------------------------------------ loop

    public void start() {
        stopped = false;
        log("mulai otomasi DOM");
        main.postDelayed(tickRunnable, 1200L);
    }

    public void stop() {
        stopped = true;
        engine.stop();
        main.removeCallbacks(tickRunnable);
        host.onProgress(engine.progressText(), "dihentikan", engine.deleted(), true, false);
    }

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (stopped) {
                return;
            }
            if (ticking) {
                main.postDelayed(this, TICK_MS);
                return;
            }
            ticking = true;
            try {
                tick();
            } catch (Exception e) {
                Log.e(TAG, "tick error", e);
                killSwitch++;
                if (killSwitch > 5) {
                    fail("kesalahan berulang: " + e.getClass().getSimpleName());
                    return;
                }
            } finally {
                ticking = false;
            }
            if (!stopped && !engine.done()) {
                main.postDelayed(this, TICK_MS);
            }
        }
    };

    private void tick() {
        long now = System.currentTimeMillis();
        if (calibrate) {
            // hanya melaporkan keadaan halaman, tidak menekan apa pun
            host.onProgress("KALIBRASI", "snapshot ke-" + snapCount + " · " + shortUrl(lastUrl),
                    engine.deleted(), false, false);
            if (snapCount == 0) {
                js("window.UnrepostSnap && window.UnrepostSnap.snapNow()");
            }
            return;
        }
        boolean fresh = now - lastSnapAt < 4000;
        DomEngine.Decision d = engine.onTick(fresh ? lastHtml : "", lastUrl, now, host.isLoading());

        host.onProgress(engine.progressText(), engine.note(), engine.deleted(), false, false);
        if (d.action == DomEngine.Action.WAIT && d.note != null) {
            noteMissing("menunggu: " + d.note);
        }
        if (d.action == DomEngine.Action.ABORT && d.note != null) {
            noteMissing("gagal: " + d.note);
        }

        switch (d.action) {
            case OPEN_REPOSTS:
                log("buka " + d.sel);
                host.openUrl(d.sel);
                break;
            case TAP_NODE:
                tapeNode(d.sel, "klik");
                break;
            case TAP_MENU:
                js("window.UnrepostSnap.menu()");
                break;
            case TAP_REMOVE:
            case TAP_CONFIRM:
                if (d.sel != null) {
                    tapeNode(d.sel, "klik");
                }
                break;
            case GO_BACK:
                js("window.UnrepostSnap.back()");
                break;
            case SCROLL:
                js("window.UnrepostSnap.scroll(" + (int) (hostScreenHeight() * 0.75) + ")");
                break;
            case FINISH:
                finish(false);
                return;
            case ABORT:
                finish(true);
                return;
            default:
                break;
        }
        if (engine.done()) {
            finish(engine.aborted());
        }
    }

    private int hostScreenHeight() {
        try {
            return context.getResources().getDisplayMetrics().heightPixels;
        } catch (Exception e) {
            return 800;
        }
    }

    private void tapeNode(String ur, String why) {
        if (ur == null) {
            return;
        }
        js("window.UnrepostSnap.click('" + ur.replace("'", "") + "')");
    }

    private void finish(boolean aborted) {
        stopped = true;
        main.removeCallbacks(tickRunnable);
        for (String line : engine.logLines()) {
            host.onLog(line);
        }
        host.onProgress(engine.progressText(), engine.note(), engine.deleted(), !aborted, aborted);
        log(aborted ? "berhenti: " + engine.note() : "selesai: " + engine.deleted() + " repost");
        String miss = missingReport();
        if (!miss.isEmpty()) {
            log("rincian macet:\n" + miss);
        }
        log("snapshot diterima: " + snapCount);
    }

    private void fail(String why) {
        stopped = true;
        host.onProgress(engine.progressText(), why, engine.deleted(), false, true);
        log("gagal: " + why);
    }

    private static String shortUrl(String url) {
        if (url == null) return "?";
        return url.length() > 60 ? url.substring(0, 57) + "…" : url;
    }

    /** Jalankan JS dengan pengaman (WebView bisa hilang kapan saja). */
    private void js(String script) {
        try {
            host.runJs("try{" + script + "}catch(e){}", null);
        } catch (Exception e) {
            Log.w(TAG, "js gagal", e);
        }
    }

    private void log(String line) {
        host.onLog(line);
    }

    /** Snapshot manual (tombol "periksa halaman"). */
    public void snapNow() {
        js("window.UnrepostSnap && window.UnrepostSnap.snapNow()");
    }

    /** Uji koneksi: apakah jembatan sudah terpasang di halaman aktif. */
    public void probe(ValueCallback<String> cb) {
        try {
            host.runJs("(function(){try{"
                    + "if(!window.UnrepostSnap) return 'tanpa-snap';"
                    + "window.UnrepostSnap.snapNow();"
                    + "return 'info=' + window.UnrepostSnap.info() + '\\n---\\n'"
                    + " + window.UnrepostSnap.summary();"
                    + "}catch(e){return 'err:'+e.message}})()", cb);
        } catch (Exception e) {
            if (cb != null) {
                cb.onReceiveValue("err:" + e.getClass().getSimpleName());
            }
        }
    }

    /** True bila sesi login TikTok sudah ada (cookie). */
    public static boolean hasSession(Context c) {
        try {
            String cookie = CookieManager.getInstance().getCookie("https://www.tiktok.com");
            if (cookie == null) {
                return false;
            }
            String l = cookie.toLowerCase(Locale.ROOT);
            return l.contains("sessionid") || l.contains("sid_tt") || l.contains("sid_guard");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Nama akun TikTok yang login. Urutan sumber (yang paling andal dulu):
     * 1. URL profil terakhir yang dibuka di WebView
     * 2. cookie TikTok yang memuat uniqueId (mis. "uniqueId=namaakun" / "tt_uid")
     * 3. kosong (pemanggil TIDAK boleh membuat URL "@" tanpa nama)
     */
    public static String readUsername(Context c) {
        try {
            String last = LastProfile.get();
            if (last != null && !last.isEmpty()) {
                return last;
            }
        } catch (Exception ignored) {
        }
        try {
            String cookie = CookieManager.getInstance().getCookie("https://www.tiktok.com");
            if (cookie != null && !cookie.isEmpty()) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("(?i)(?:uniqueid|unique_id|tt_uid_name)=([A-Za-z0-9._]+)")
                        .matcher(cookie);
                if (m.find()) {
                    LastProfile.set(m.group(1));
                    return m.group(1);
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    /** Menyimpan nama akun yang terakhir terlihat di URL profil TikTok. */
    public static final class LastProfile {
        private static volatile String value = "";

        private LastProfile() {
        }

        public static void set(String username) {
            if (username != null && !username.isEmpty()) {
                value = username.replace("@", "");
            }
        }

        public static String get() {
            return value;
        }
    }

    /** Catat nama akun bila URL berupa /@nama/... */
    public static void noteUrl(String url) {
        if (url == null) {
            return;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("tiktok\\.com/@([A-Za-z0-9._]+)").matcher(url);
        if (m.find()) {
            LastProfile.set(m.group(1));
        }
    }

    public static void clearSession() {
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.removeAllCookies(null);
        } catch (Exception ignored) {
        }
    }
}
